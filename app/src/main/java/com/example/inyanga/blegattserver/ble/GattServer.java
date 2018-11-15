package com.example.inyanga.blegattserver.ble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.pm.PackageManager;

import com.example.inyanga.blegattserver.logger.Logger;
import com.example.inyanga.blegattserver.logger.LoggerCallback;

public class GattServer {
    private Context context;
    private BleEventCallback bleEventCallback;
    private BluetoothManager bluetoothManager;
    private BluetoothGattServer gattServer;
    private BluetoothLeAdvertiser advertiser;
    private Logger logger;

    public GattServer(Context context, BleEventCallback bleEventCallback, Logger logger) {
        this.context = context;
        this.bleEventCallback = bleEventCallback;
        this.logger = logger;
    }

    public boolean initBluetooth() {

        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            bleEventCallback.onBleMsg("BLE not supported");
            return false;
        }

        bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = null;
        if (bluetoothManager != null)
            bluetoothAdapter = bluetoothManager.getAdapter();

        if (bluetoothAdapter == null) {
            bleEventCallback.onBleMsg("Cannot get Bluetooth Service");
            return false;
        }

        advertiser = bluetoothAdapter.getBluetoothLeAdvertiser();

        if (!bluetoothAdapter.isEnabled()) {
            bleEventCallback.onBluetoothEnable();
            return false;
        } else {
            return true;
        }
    }

    public void initServer() {
        BluetoothGattService dataService = new BluetoothGattService(GattServerProfile.DATA_SERVICE,
                BluetoothGattService.SERVICE_TYPE_PRIMARY);

        BluetoothGattCharacteristic dataChar =
                new BluetoothGattCharacteristic(GattServerProfile.DATA_CHAR,
                        BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                        BluetoothGattCharacteristic.PERMISSION_WRITE);

        //Descriptor for read notifications
        BluetoothGattDescriptor cccDescriptor = new BluetoothGattDescriptor(GattServerProfile.CCC_DESCRIPTOR,
                BluetoothGattDescriptor.PERMISSION_WRITE);
        dataChar.addDescriptor(cccDescriptor);

        dataService.addCharacteristic(dataChar);
        gattServer = bluetoothManager.openGattServer(context, gattServerCallback);
        gattServer.addService(dataService);
        logger.log("Server started", true, 0);
        startAdvertising();
    }

    private void startAdvertising() {
        if (advertiser == null) return;

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .build();

        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .setIncludeTxPowerLevel(true)
                .build();

        advertiser.startAdvertising(settings, data, advertiseCallback);
        logger.setStatus(GattServerProfile.STATUS_ADVERTISING);
        logger.startUpdatingStatus();
    }

    public void stopServer() {
        advertiser.stopAdvertising(advertiseCallback);
        gattServer.close();
        logger.setStatus(GattServerProfile.STATUS_SERVER_STOPPED);
        logger.log("Server stopped", true, 1);
        logger.log("", true, 2);
    }


    /***********************************************************************************************
     AdvertiseCallback implementation
     **********************************************************************************************/

    private AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            logger.log("Start advertising...", true, 0);
        }

        @Override
        public void onStartFailure(int errorCode) {
            logger.log("Advertising failure: " + errorCode, true, 0);
        }
    };

    /***********************************************************************************************
     BluetoothGattServerCallback implementation
     **********************************************************************************************/

    private BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED &&
                    status == BluetoothGatt.GATT_SUCCESS) {
                logger.log("Device connected:", true, 0);
                String name = (device.getName()==null) ? "N/A" : device.getName();
                logger.log("    Name: " + name, true, 0);
                logger.log("    MAC: " + device.getAddress(), true, 0);
                logger.setStatus(GattServerProfile.STATUS_CONNECTED);
            }
        }



        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            logger.setStatus(GattServerProfile.STATUS_WRITING_CCCD);
            logger.log("Notifications enabled", true, 0);

            if(responseNeeded)
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
            logger.setStatus(GattServerProfile.STATUS_CONNECTED);
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device,
                                                 int requestId, BluetoothGattCharacteristic characteristic,
                                                 boolean preparedWrite, boolean responseNeeded,
                                                 int offset, byte[] value) {
            if (GattServerProfile.DATA_CHAR.equals(characteristic.getUuid())) {
                logger.log("Data characteristic write request", true, 0);
                logger.setStatus(GattServerProfile.STATUS_WRITING_CHAR);
            }
            logger.setStatus(GattServerProfile.STATUS_CONNECTED);

        }


    };
}
