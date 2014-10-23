package com.emlab.cguee.evmbackup;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;


public class BackupActivity extends Activity implements View.OnClickListener {
    private Button unlock;
    private TextView soc;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice btd;
    private BluetoothSocket bts;
    private OutputStream outputStream;
    private InputStream inputStream;
    private Handler handler;
    private HandlerThread handlerThread;
    private boolean isCon = false;
    private Thread socThread;
    private byte[] input;
    private int socInt;
    private boolean shutDown = false;

    private static final int HEADER_SIGNAL = 111;

    private static final String TAG = "BackupActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        soc = (TextView) findViewById(R.id.soc);
        setContentView(R.layout.activity_backup);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        unlock = (Button)findViewById(R.id.unlock);
        unlock.setOnClickListener(this);
        handlerThread = new HandlerThread("");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.backup, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onClick(View view) {
        if(!isCon) {
            unlock.setClickable(false);
            handler.post(new Runnable() {
                @Override
                public void run() {
                    findBT();
                }
            });
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    openBT();
                }
            }, 500);
        }else{
            try {
                unlock.setText("Activate");
                outputStream.write(0);
                isCon = false;
                shutDown = true;
                bts.close();
            } catch (IOException e) {
                Log.w(TAG, e.toString());
            }
        }
    }

    void findBT() {
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter
                .getBondedDevices();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                if (device.getName().equals("HC-05")) {
                    btd = device;
                    break;
                }
            }
        }
    }

    void openBT() {
        UUID uuid = btd.getUuids()[0].getUuid();
        try {
            Log.w(TAG, "Trying to connect with standard method");
            bts = btd.createRfcommSocketToServiceRecord(uuid);
            if (!bts.isConnected()) {
                bts.connect();
                Log.w(TAG, "Device connected with standard method");
                outputStream = bts.getOutputStream();
                inputStream = bts.getInputStream();
                outputStream.write(1);
                isCon = true;
                shutDown = false;
                socThread = new Thread(socLis);
                socThread.start();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        unlock.setText("Deactivate");
                        unlock.setClickable(true);
                    }
                });
            }
        } catch (IOException e) {
            try {
                Log.w(TAG, "standard method failed, trying with reflect method......");
                if (!bts.isConnected()) {
                    Method m = btd.getClass().getMethod("createRfcommSocket", new Class[]{int.class});
                    bts = (BluetoothSocket) m.invoke(btd, 1);
                    bts.connect();
                    Log.w(TAG, "Device connected with reflect method");
                    outputStream = bts.getOutputStream();
                }
            } catch (NoSuchMethodException e1) {
                Log.w(TAG, e1.toString());
            } catch (InvocationTargetException e1) {
                Log.w(TAG, e1.toString());
            } catch (IllegalAccessException e1) {
                Log.w(TAG, e1.toString());
            } catch (IOException e1) {
                Log.w(TAG, "reflect method failed, shut down process");
//                runToastOnUIThread("Device not found");
            }
        }
    }

    private Runnable socLis = new Runnable() {
        @Override
        public void run() {
            Log.w(TAG,"Thread start");
            try {
                while (true) {
                    if(shutDown) break;
                    if (inputStream.available() >= 10) {
                        input = new byte[10];
                        inputStream.read(input);
                        if (input[0] == HEADER_SIGNAL) {
                            socInt = input[1];
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    soc = (TextView)findViewById(R.id.soc);
                                    if(soc != null) {
                                        Log.w(TAG,""+socInt);
                                        soc.setText("Device SOC: " + socInt + "%");
                                    }
                                }
                            });
                        }
                    }
                }
            } catch (IOException e) {
                Log.w(TAG,e.toString());
            }
            Log.w(TAG,"Thread shutdown");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    soc = (TextView)findViewById(R.id.soc);
                    soc.setText("Device not connected");
                }
            });
        }
    };
}
