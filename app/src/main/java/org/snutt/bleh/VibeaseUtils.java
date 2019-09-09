package org.snutt.bleh;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;

import android.util.Base64;

import java.util.ArrayList;
import java.util.UUID;

public class VibeaseUtils {



    public static final String KEY1 = "2iYNPjW9ptZj6L7snPfPWIH5onzQ0V1p";
    public static final String KEY2 = "4sRewsha3G54ZqEcjr9Iadexd1sKB8vr";
    public static String KEY_HS = "";

    public static final UUID UUID_SRVC_VIBE = UUID.fromString("DE3A0001-7100-57EF-9190-F1BE84232730");
    public static final UUID UUID_CHAR_CMD  = UUID.fromString("803C3B1F-D300-1120-0530-33A62B7838C9");
    public static final UUID UUID_CHAR_CMD_ALT = UUID.fromString("0002a4d-0000-1000-8000-00805f9b34fb");
    public static final UUID UUID_DESC_NOTIF  = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");


    // Some known static commands and their expected prefixes
    public static final String PFX_KEY_EXCHANGE = "$";
    public static final byte[] CMD_KEY_EXCHANGE = { 'S', 0x1b };

    public static final String PFX_STATUS_QUERY = "$";
    public static final byte[] CMD_STATUS_QUERY = { 0x20, 0x45 };

    public static final String PFX_CMD = "*";
    public static final byte[] CMD_STOP_VIBE = "0500,0500".getBytes();

    // Usually applied with KEY2.
    public static byte[] Descramble(byte[] cryptext) { return Descramble(cryptext, KEY2); }
    public static byte[] Descramble(byte[] cryptext, String key) {
        byte[] keyb = key.getBytes();
        int length = keyb.length;

        for (int i=0; i< cryptext.length; i++) {
            cryptext[i] = (byte) ((cryptext[i] - 1) ^ keyb[i % length]);
        }
        return cryptext;
    }

    // Always(?) applied with KEY_HS
    public static byte[] Scramble(byte[] plaintext) { return Scramble(plaintext, KEY_HS); }
    public static byte[] Scramble(byte[] plaintext, String key) {
        byte[] keyb = key.getBytes();
        byte[] cryptext = plaintext.clone();
        int length = keyb.length;

        for (int i=0; i< plaintext.length; i++) {
            cryptext[i] = (byte) ((cryptext[i] ^ keyb[i % length]) + 1);
        }
        return cryptext;
    }



    public static String VibeCommand(int intensity, int duration) {

        intensity = StrictMath.min(intensity, 9);
        intensity= StrictMath.max(intensity, 0);

        duration = StrictMath.min(duration, 999);
        duration = StrictMath.max(duration, 0);


        StringBuilder sb = new StringBuilder();

        sb.append(intensity);
        if (duration < 100) sb.append("0");
        if (duration < 10) sb.append("0");
        sb.append(duration);

        return sb.toString();
    }


    public static class Msg {
        ArrayList<String> packets = new ArrayList<String>();

        String prefix = "";
        String joined = "";
        byte[] scrambled = new byte[0];
        byte[] descrambled = new byte[0];

        //
        // Receipt of messages from the vibrator
        //
        public Msg() {
            /* Add packets to parse automatically */
        }

        // Pass in packet payloads such as "<abcd4433J>"
        // in the order they arrive.
        // Returns true if the packet completes the message.
        // If so, the fields of the message have been parsed.
        public boolean AddPacket(byte[] packet, String key) {
            String payload = new String(packet);

            if (payload.isEmpty()) return false;

            // Message has already been decoded before.
            if (!prefix.isEmpty()) return true;

            packets.add(payload);
            if (payload.endsWith("!")) {
                defragment();

                if (prefix.equals("%")) {
                    // These messages are only fragmented, nothing else.
                    this.descrambled = this.joined.getBytes();
                } else {
                    decode();
                    descramble(key);
                }

                return true;
            }

            return false;
        }

        private void defragment() {
            joined = "";
            prefix = "";

            if (packets.size() == 0) {
                // Something is badly wrong.
                return;
            }

            String last = packets.get(packets.size()-1);
            if (!last.endsWith("!")) {
                // This breaks with the fragmentation techniques we've seen so far.
                return;
            }


            for (String f : packets) {
                if (f.length() == 0) {
                    // Someone submitted an empty packet. Boo.
                    return;
                }

                // The character that starts the message is of interest.
                if (prefix.isEmpty()) prefix = f.substring(0,1);

                String contents = f.substring(1, f.length()-1);
                joined += contents;
            }
        }

        private void decode() {
            scrambled = Base64.decode(joined, Base64.DEFAULT | Base64.NO_WRAP);
        }

        private void descramble(String key) {
            descrambled = VibeaseUtils.Descramble(this.scrambled, key);
        }




        //
        // Transmission of messages
        //

        // After using this constructor, the packets to send are
        // available in packets[].
        // prefix must be a single character.
        // It's been known to be $ and * under different circumstances.
        public Msg(String payload, String prefix, String scramble_key) {
            this(payload.getBytes(), prefix, scramble_key);
        }

        public Msg(byte[] payload, String prefix, String scramble_key) {
            this.descrambled = payload;
            this.prefix = prefix;

            if (prefix == null || prefix.isEmpty()) prefix = "*";

            scramble(scramble_key);
            encode();
            fragment();
        }

        private void scramble(String key) {
            scrambled = VibeaseUtils.Scramble(descrambled, key);
        }

        private void encode() {
            joined = Base64.encodeToString(scrambled, Base64.DEFAULT | Base64.NO_WRAP);
        }

        private void fragment() {
            packets.clear();

            int len = joined.length();
            int n_chunks = len / 16;
            if (len % 16 != 0) n_chunks += 1;

            if (n_chunks == 1) {
                packets.add(prefix + joined + "!");
                return;
            }

            if (n_chunks > 1) {
                for (int c=0; c<n_chunks; c++) {

                    int start = c * 16;
                    int end = start + 16;
                    if (end > len-1) end = len-1;

                    String chunk = joined.substring(start, end);

                    if (c == 0) {
                        // First chunk has the prefix provided from the user
                        packets.add(prefix + chunk + ">");

                    } else if (c < n_chunks - 1){
                        // Middle chunks are all similar
                        packets.add("<" + chunk + ">");

                    } else {
                        // Last chunk is special
                        packets.add("<" + chunk + "!");

                    }
                }


            }
        }


    }
}
