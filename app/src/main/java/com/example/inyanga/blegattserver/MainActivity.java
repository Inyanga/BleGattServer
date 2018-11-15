package com.example.inyanga.blegattserver;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Handler;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;

import com.example.inyanga.blegattserver.ble.BleEventCallback;
import com.example.inyanga.blegattserver.ble.GattServer;
import com.example.inyanga.blegattserver.ble.GattServerProfile;
import com.example.inyanga.blegattserver.logger.Logger;
import com.example.inyanga.blegattserver.logger.LoggerCallback;

import butterknife.Bind;
import butterknife.ButterKnife;

public class MainActivity extends AppCompatActivity implements BleEventCallback, LoggerCallback {

    private static final int BT_REQUEST_CODE = 13;

    private GattServer gattServer;
    private Logger logger;
    private String status = GattServerProfile.STATUS_SERVER_STOPPED;

    @Bind(R.id.log)
    TextView logView;
    @Bind(R.id.status)
    TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        logger = new Logger(this);
        startServer();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.restart_server:
                startServer();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == BT_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                gattServer.initServer();
            }
        }
    }

    private void startServer() {
        if (gattServer != null)
            gattServer.stopServer();
        gattServer = new GattServer(getApplicationContext(), this, logger);

            if (gattServer.initBluetooth())
                gattServer.initServer();


    }


    /***********************************************************************************************
     Gatt Server UI callback methods
     **********************************************************************************************/

    @Override
    public void onBleMsg(String msg) {
        Snackbar.make(findViewById(R.id.main_constraint), msg, Snackbar.LENGTH_SHORT);
    }

    @Override
    public void onBluetoothEnable() {
        Intent btEnableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(btEnableIntent, BT_REQUEST_CODE);
    }

    /***********************************************************************************************
     Logger callback
     **********************************************************************************************/

    @Override
    public void onLog(final String msg, final boolean newLine) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String logMsg = (newLine) ? msg + "\n" : msg;
                logView.append(logMsg);
            }
        });
    }

    @Override
    public void onStatus(String status) {
        this.status = status;
    }

    @Override
    public void statusUpdate() {

        final int delay = 140; //milliseconds

       new Thread(new Runnable() {
            @Override
            public void run() {
//                String[] statChar = {"/", "-", "\\", "|","/", "-", "\\", "|"};
                String[] statChar = {".", ".. ", "...  ", "....   ", "....  ", ".....  ", "......  ", "...  ", ".. ", ".."};

                while (!status.equals(GattServerProfile.STATUS_SERVER_STOPPED)) {
                    for (final String s : statChar) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                statusView.setText(String.format("%s   %s", s, status));
                            }
                        });

                        try {
                            Thread.sleep(delay);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }).start();


    }
}
