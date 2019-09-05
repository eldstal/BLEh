package org.snutt.bleh;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;

import static org.snutt.bleh.VibeaseUtils.*;

public class VibeaseController extends BluetoothGattCallback {
    private final VibeaseActivity ui;

    private final BluetoothGatt gattServer;

    private String KEY_HS = "";

    // The characteristic used for commands and responses
    private BluetoothGattCharacteristic cmd_write;
    private BluetoothGattCharacteristic cmd_read;

    // We can only have one command/response active at a time.
    // This is the response while we're receiving it.
    private VibeaseUtils.Msg current_rx = new Msg();
    private VibeaseUtils.Msg current_tx = null;

    public enum STATE {
        PAIRING,
        IDENTIFY,
        NOTIFICATIONS,
        KEY_EXCHANGE,
        READY,
        FAILED

    };

    private STATE state;

    //
    // The pairing and set-up follows these methods
    // from top to bottom. Each callback triggers the next one.
    //
    // |
    // |
    // V
    //

    public VibeaseController(BluetoothDevice dev, VibeaseActivity ui) {
        state = STATE.PAIRING;

        this.ui = ui;

        ui.Write("Connecting...");
        this.gattServer = dev.connectGatt(this.ui, false, this);
        dev.createBond();

    }


    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        super.onConnectionStateChange(gatt, status, newState);
        if (newState == BluetoothGatt.STATE_CONNECTED) {
            if (state == STATE.PAIRING) {
                ui.Write("Identifying...");
                state = STATE.IDENTIFY;
                gatt.discoverServices();
            }
        }
    }


    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        super.onServicesDiscovered(gatt, status);
        if (state == STATE.IDENTIFY) {
            ui.Write("Starting handshake...");
            state = STATE.NOTIFICATIONS;
            start_handshake();
        }
    }

    private void start_handshake() {

        BluetoothGattService service = gattServer.getService(VibeaseUtils.UUID_SRVC_VIBE);
        if (service == null) {
            ui.Write("Service not found...?");
            state = STATE.FAILED;
            return;
        }

        for (BluetoothGattCharacteristic c : service.getCharacteristics()) {
            if (c.getUuid().equals(UUID_CHAR_CMD) ||
                c.getUuid().equals(UUID_CHAR_CMD_ALT)) {
                if ((c.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                    cmd_write = c;
                    ui.Write("Device uses characteristic " + c.getUuid().toString());
                }
                if ((c.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                    cmd_read = c;
                }

            }
        }

        if (cmd_read == null) {
            ui.Write("READ Characteristic not found...?");
            state = STATE.FAILED;
            return;
        }

        if (cmd_write == null) {
            ui.Write("WRITE Characteristic not found...?");
            state = STATE.FAILED;
            return;
        }


        ui.Write("Enabling notifications...");
        gattServer.setCharacteristicNotification(cmd_read, true);
        BluetoothGattDescriptor descriptor = cmd_read.getDescriptor(VibeaseUtils.UUID_DESC_NOTIF);
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        gattServer.writeDescriptor(descriptor);
    }


    @Override
    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        super.onDescriptorWrite(gatt, descriptor, status);

        if (state == STATE.NOTIFICATIONS) {
            state = STATE.KEY_EXCHANGE;

            ui.Write("Performing key exchange...");
/*
            // If done properly, this should scramble and encode to "$aGK=!"
            VibeaseUtils.Msg getkey = new VibeaseUtils.Msg(CMD_KEY_EXCHANGE, PFX_KEY_EXCHANGE, KEY2);
            for (String p : getkey.packets) {
                ui.Write("Scrambled keyex command as " + p);
            }
            Send(getkey);
 */

            // Hardcoded key exchange command that has worked fine...
            cmd_write.setValue("$aGk=!".getBytes());
            if (!gattServer.writeCharacteristic(cmd_write)) {
                ui.Write("Failed write when attempting key exchange.");
                state = STATE.FAILED;
            }

        }
    }


    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        super.onCharacteristicWrite(gatt, characteristic, status);
        ui.Write("Write to " + characteristic.getUuid().toString() + " status: " + status);
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        super.onCharacteristicChanged(gatt, characteristic);
        // This is how the vibrator responds to our writes.

        if (!current_rx.AddPacket(characteristic.getValue(), KEY2)) {
            ui.Write("Incomplete message...");
            return;
        }

        Msg msg = current_rx;
        current_rx = new Msg();

        if (state == STATE.KEY_EXCHANGE) {
            String payload = new String(msg.descrambled);
            if (payload.startsWith("HS=")) {
                KEY_HS = payload.substring(3);
                ui.Write("HS Key: " + KEY_HS);
                ui.Write("Setup completed!");
                state = STATE.READY;
            }
        } else if (state == STATE.READY) {

            // TODO: Handle msg
            ui.Write("RX message: " + new String(msg.descrambled));
        }
    }




    @Override
    public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
        super.onReadRemoteRssi(gatt, rssi, status);
    }




    public boolean Send(Msg msg) {

        for (String p : msg.packets) {
            ui.Write("TX: " + p);
            cmd_write.setValue(p.getBytes());
            if (!gattServer.writeCharacteristic(cmd_write)) {
                return false;
            }
        }

        return true;
    }


    public void ToggleVibration() {

    }
}
