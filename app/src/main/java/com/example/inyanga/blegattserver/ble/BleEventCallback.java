package com.example.inyanga.blegattserver.ble;

public interface BleEventCallback {
    void onBleMsg(String msg);
    void onBluetoothEnable();
}

