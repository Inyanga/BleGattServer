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
import android.util.Log;

import com.example.inyanga.blegattserver.logger.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

public class GattServer {

    private static final byte PROTOCOL_VERSION = 1;
    private static final int PACKET_SIZE = 20;

    private Context context;
    private BleEventCallback bleEventCallback;
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGattServer gattServer;
    private BluetoothLeAdvertiser advertiser;
    private BluetoothDevice connectedDevice;
    private BluetoothGattCharacteristic dataChar;
    private Queue<byte[]> byteQueue = new ConcurrentLinkedQueue<>();
    private Queue<byte[]> dataQueue;
    private int notifyReceiveCount;
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
        bluetoothAdapter = null;
        if (bluetoothManager != null)
            bluetoothAdapter = bluetoothManager.getAdapter();

        if (bluetoothAdapter == null) {
            bleEventCallback.onBleMsg("Cannot get Bluetooth Service");
            return false;
        }
        bluetoothAdapter.setName("GTS000001");


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


        dataChar =
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
        advertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
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

    private void readFile() {
        InputStream in = null;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
//            buffer.write(new byte[]{protocolVersion, majByte, minByte});
            in = context.getAssets().open("lorem");

            int nRead;
            byte[] data = new byte[16384];

            while ((nRead = in.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        int dataLength = buffer.toByteArray().length;
        byte[] length = new byte[2];
        length[0] = (byte) (dataLength & 0xFF);
        length[1] = (byte) ((dataLength >> 8) & 0xFF);
        ByteArrayOutputStream message = new ByteArrayOutputStream();
        try {
            message.write(new byte[]{PROTOCOL_VERSION, length[1], length[0]});
            message.write(buffer.toByteArray());
            notifyReceiveCount = 0;
            prepareData(message.toByteArray(), PACKET_SIZE);
//            prepareCtlEmulatedData(message.toByteArray());
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.i("DATA_SIZE", "SIZE: " + buffer.toByteArray().length);
    }

    private void prepareCtlEmulatedData(byte[] array) {
        int max = 20;
        int min = 1;

        byteQueue = new ConcurrentLinkedQueue<>();
        int counter = 0;
        while (counter < array.length) {
            Random random = new Random();
            int randomSize = random.nextInt(max - min) + min;
            int packetSize = Math.min(randomSize, array.length - counter);
            byte[] packet = new byte[packetSize];
            for (int i = 0; i < packetSize; i++) {
                packet[i] = array[counter++];
            }
            byteQueue.add(packet);
        }
        sendData();
    }

    private void prepareData(byte[] array, int packetSize) {
        int numOfChunks = (int) Math.ceil((double) array.length / packetSize);

        byteQueue = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < numOfChunks; ++i) {
            int start = i * packetSize;
            int length = Math.min(array.length - start, packetSize);

            byte[] temp = new byte[length];
            System.arraycopy(array, start, temp, 0, length);
            byteQueue.add(temp);
        }

        sendData();
    }

    private void sendData() {
        if (dataChar != null) {
            byte[] byteValue = byteQueue.poll();
            if (byteValue != null) {
                notifyReceiveCount += byteValue.length;
                dataChar.setValue(byteValue);
                gattServer.notifyCharacteristicChanged(connectedDevice, dataChar, false);
            } else {
                logger.log("Transfer complete", true, 1);
                logger.log("Sent bytes: " + notifyReceiveCount, true, 0);
                logger.setStatus(GattServerProfile.STATUS_CONNECTED);
                notifyReceiveCount = 0;
//                byteQueue = dataQueue;
            }
        }
    }

    private void setConnectedDevice(BluetoothDevice device) {
        this.connectedDevice = device;
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

        private static final int MAJOR_BYTE = 1;
        private static final int MINOR_BYTE = 2;

        private int receiveCounter;
        private boolean firstPacket = true;
        private int dataLength;
        private byte majByte;
        private byte minByte;


        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED &&
                    status == BluetoothGatt.GATT_SUCCESS) {
//                advertiser.stopAdvertising(advertiseCallback);
                setConnectedDevice(device);
                firstPacket = true;
                receiveCounter = 0;
                logger.log("Device connected:", true, 0);
                String name = (device.getName() == null) ? "N/A" : device.getName();
                logger.log("    Name: " + name, true, 0);
                logger.log("    MAC: " + device.getAddress(), true, 0);
                logger.setStatus(GattServerProfile.STATUS_CONNECTED);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {

                setConnectedDevice(null);
                logger.log("Device disconnected", true, 1);
                logger.setStatus(GattServerProfile.STATUS_DISCONNECTED);
                stopServer();
                initServer();

            }
        }


        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            logger.setStatus(GattServerProfile.STATUS_WRITING_CCCD);
            logger.log("Notifications enabled", true, 0);

            if (responseNeeded)
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
            logger.setStatus(GattServerProfile.STATUS_CONNECTED);
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device,
                                                 int requestId, BluetoothGattCharacteristic characteristic,
                                                 boolean preparedWrite, boolean responseNeeded,
                                                 int offset, byte[] value) {

            if (GattServerProfile.DATA_CHAR.equals(characteristic.getUuid())) {

                if (value != null && value.length > 0) {
                    if (firstPacket) {
                        majByte = value[MAJOR_BYTE];
                        minByte = value[MINOR_BYTE];
                        dataLength = ((majByte & 0xff) << 8) | (minByte & 0xff);
                        logger.setStatus(GattServerProfile.STATUS_WRITING_CHAR);
                        logger.log("Data characteristic write request", true, 0);
                        logger.log("Message length: " + dataLength, true, 0);
                        logger.log("Receiving data: ", true, 1);
                        firstPacket = false;
                    }
                    logger.log("*", false, 0);
                    receiveCounter += value.length;
                    if(responseNeeded) {
                        gattServer.sendResponse(device,
                                requestId,
                                BluetoothGatt.GATT_SUCCESS,
                                0,
                                value);
                    }

                    checkEndOfData();
                }
            }
        }



        private void checkEndOfData() {
            if (receiveCounter >= dataLength) {
                firstPacket = true;
                receiveCounter = 0;
                readFile();
                logger.log("Client requesting data transfer", true, 1);
                logger.log("", true, 0);
                logger.log("Sending data:", true, 0);
                logger.setStatus(GattServerProfile.STATUS_SENDING_DATA);
            }
        }

        @Override
        public void onNotificationSent(BluetoothDevice device, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (notifyReceiveCount % 8 == 0)
                logger.log("*", false, 0);
                logger.setStatus(GattServerProfile.STATUS_SENDING_DATA + ": " + notifyReceiveCount + " bytes");
                sendData();
            } else {
                logger.log("Notification failure", false, 0);
            }
        }
    };
}
