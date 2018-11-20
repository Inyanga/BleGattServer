package com.example.inyanga.blegattserver.ble;

import android.bluetooth.BluetoothGattDescriptor;

import java.util.UUID;

public class GattServerProfile {

    public static final UUID DATA_SERVICE = UUID.fromString("F000C0E0-0451-4000-B000-000000000000");
    public static final UUID DATA_CHAR = UUID.fromString("F000C0E1-0451-4000-B000-000000000000");
    public static final UUID CCC_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    public static final String STATUS_ADVERTISING = "Advertising";
    public static final String STATUS_CONNECTED = "Connected";
    public static final String STATUS_DISCONNECTED = "Disconnected";
    public static final String STATUS_SERVER_STOPPED = "Server stopped";
    public static final String STATUS_WRITING_CHAR = "Receiving data";
    public static final String STATUS_WRITING_CCCD = "Enabling notifications";
    public static final String STATUS_SENDING_DATA = "Sending data";
}
