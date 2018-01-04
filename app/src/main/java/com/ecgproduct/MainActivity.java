package com.ecgproduct;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

public class MainActivity extends AppCompatActivity
        implements OnClickListener, OnItemClickListener, BluetoothAdapter.LeScanCallback {
    private final static int REQUEST_PERMISSION_REQ_CODE = 34; // any 8-bit number
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int DOUBLE_BACK_PRESS_DURATION = 2000;
    String TAG = "tx_power";
    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothGatt mBluetoothGatt;

    public final UUID HR_MEASUREMENT = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");
    public final UUID HR_SERVICE = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb");
    public final UUID ECG_SERVICE = UUID.fromString("00002017-0000-1000-8000-00805f9b34fb");
    public final UUID ECG_MEASUREMENT = UUID.fromString("00001224-0000-1000-8000-00805f9b34fb");

    public static final UUID DESCRIPTOR_CCC = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private ListView deviceList;

    private Button btnScan;
    private Button btnSave;
    private Button btnPlay;
    private Button btnLoad;
    private Boolean isSaving = false;
    private String strFileName = "";

    public FrameLayout lyt_ecg, lyt_acc, lyt_hrm;

    private ECGChart mECGFlowChart;
    private ACCChart mXFlowChart;
    private ACCChart mYFlowChart;
    private ACCChart mZFlowChart;
    private HRChart mHRFlowChart;

    private TextView mHeartText;
    private TextView mHRM, mSPO, mTEMP1, mTEMP2;
    int currentPos = -1;
    BleDevice currentBle;

    int nPercent = -1;
    int nLoadIndex = 0;
    String[] loadData;
    Boolean isPlaying = false;
    private FileWriter fWriter = null;

    private LeDeviceListAdapter mLeDeviceListAdapter;
    boolean isFirst = true;
    int nGraphType = 3;

    final Handler handler = new Handler();
    final Runnable runnable = new Runnable() {
        public void run() {
            if (nLoadIndex < loadData.length) {
                mHRFlowChart.addEcgData(Integer.parseInt(loadData[nLoadIndex]));
                mHeartText.setText(Integer.parseInt(loadData[nLoadIndex]) + " BPM");
                nLoadIndex++;
                handler.postDelayed(this, 1000);
            }else{
                isPlaying = false;
                btnPlay.setText("Play");
                btnPlay.setBackgroundResource(R.drawable.radiusbuttongreen);
            }
        }
    };
    private void updateTemperature(final int nHRM, final int nSPO, final int nTemp1, final int nTemp2) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mHRM.setText(nHRM+ " BPM");
                mSPO.setText(nSPO+ " %");
                mTEMP1.setText(nTemp1+ " ℃");
                mTEMP2.setText(nTemp2+ " ℃");
            }
        });
    }
    private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                Log.d("ConnectionStatus", "Connected");
                mECGFlowChart.setConnection(true);
                mXFlowChart.setConnection(true);
                mYFlowChart.setConnection(true);
                mZFlowChart.setConnection(true);
                mBluetoothGatt.discoverServices();
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                Log.d("ConnectionStatus", "DisConnected");
                mECGFlowChart.setConnection(false);
                mXFlowChart.setConnection(false);
                mYFlowChart.setConnection(false);
                mZFlowChart.setConnection(false);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                for (BluetoothGattService gattService : gatt.getServices()) {
                    if (gattService.getUuid().equals(HR_SERVICE)) {
                        for (BluetoothGattCharacteristic characteristic : gattService.getCharacteristics()) {
                            if (characteristic.getUuid().equals(HR_MEASUREMENT)) {
                                BluetoothGattDescriptor descriptor = characteristic.getDescriptor(DESCRIPTOR_CCC);
                                gatt.setCharacteristicNotification(characteristic, true);
                                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                gatt.writeDescriptor(descriptor);
                            }
                        }
                    }
                }
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
        }
        int hrNumber = 0;
        boolean isSensorDetected = false;
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (characteristic.getUuid().equals(HR_MEASUREMENT)) {  //Get HeartRate Notify Default Profile
                byte[] data = characteristic.getValue();
                int hrFormat = data[0] & 0x01;
                int hrValue = (hrFormat == 1 ? data[1] + (data[2] << 8) : data[1]) & (hrFormat == 1 ? 0x0000FFFF : 0x000000FF);
                if (hrValue > 200) {
                    hrValue = 200;
                }
                if (isSaving) {
                    saveHR(hrValue);
                }
                mHRFlowChart.setGraphMax( 200 );
                if(nGraphType == 3){
                    mHRFlowChart.addEcgData(hrValue);
                }
                final int finalHrValue = hrValue;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mHeartText.setText(finalHrValue + " BPM");
                    }
                });
            }
            if(characteristic.getUuid().equals(ECG_MEASUREMENT)){   //Get ECG Notify Custom Profile
                int ecgVal;
                int hrsCount = 4;//characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 1);
                int nHRM = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                int nSPO = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 1);

                for (int i = 0; i < hrsCount; i++) {
                    ecgVal = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 2 + i * 2);
                    int val = (ecgVal & 0xf000) >> 12;
                    if (val != 8 && val != 0) ecgVal = ecgVal & 0xf000;
                    isSensorDetected = isSensorDetected(ecgVal);
                    ecgVal = ecgVal & 0x0fff;
                    ecgVal = 50 + ecgVal * 2400 / 4096;
                    mECGFlowChart.setGraphMax(2500);
                    if (isSensorDetected) {
                        if(nGraphType == 1)
                            mECGFlowChart.addEcgData(ecgVal);
                    }
                    hrNumber++;
                    Log.d("ECG", ecgVal + "");
                }

                int nTemperature1 = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 16);
                int nTemperature2 = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 17);
                int accX = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 3 + hrsCount * 2);
                int accY = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 3 + hrsCount * 2 + 2);
                int accZ = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 3 + hrsCount * 2 + 4);
                float accxx;
                if (accX > 2047)
                    accxx = ((accX- 2048) / 512f) - (float) 4;
                else
                    accxx = (accX) / 512f;

                float accyy;
                if (accY > 2047)
                    accyy = ((accY - 2048) / 512f) - (float) 4;
                else
                    accyy = (accY) / 512f;

                float acczz;
                if (accZ > 2047)
                    acczz = ((accZ- 2048) / 512f) - (float) 4;
                else
                    acczz = (accZ) / 512f;

                Log.d("Accx", ":" + accxx +";"+ accyy +";"+ acczz);
                mXFlowChart.setGraphMax(400);
                mYFlowChart.setGraphMax(400);
                mZFlowChart.setGraphMax(400);
                if(nGraphType == 2){
                    mXFlowChart.addEcgData((int) ((accxx + 4)* 50));
                    mYFlowChart.addEcgData((int) ((accyy + 4)* 50));
                    mZFlowChart.addEcgData((int) ((acczz + 4)* 50));
                }
                updateTemperature(nHRM, nSPO, nTemperature1, nTemperature2);
            }
        }
        private boolean isSensorDetected(final int value) {
            return ((value & 0x8000) != 0);
        }

        public void saveHR(int hrValue) {
            try {
                fWriter.append(hrValue + ",");
                BufferedWriter bw = new BufferedWriter(fWriter);
                bw.write(hrValue + ",");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorRead(gatt, descriptor, status);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if(isFirst) {
                for (BluetoothGattService gattService : gatt.getServices()) {
                    if (gattService.getUuid().equals(ECG_SERVICE)) {
                        for (BluetoothGattCharacteristic characteristic : gattService.getCharacteristics()) {
                            if (characteristic.getUuid().equals(ECG_MEASUREMENT)) {
                                BluetoothGattDescriptor new_descriptor = characteristic.getDescriptor(DESCRIPTOR_CCC);
                                gatt.setCharacteristicNotification(characteristic, true);
                                new_descriptor .setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                gatt.writeDescriptor(new_descriptor );
                            }
                        }
                    }
                }
                isFirst = false;
            }
            super.onDescriptorWrite(gatt, descriptor, status);
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // TODO Auto-generated method stub
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); // keep screen

        currentBle = null;

        initViews();
        _checkPermission();
    }


    private void initViews() {
        deviceList = (ListView) findViewById(R.id.device_list);
        mLeDeviceListAdapter = new LeDeviceListAdapter();
        deviceList.setAdapter(mLeDeviceListAdapter);
        deviceList.setOnItemClickListener(this);
        deviceList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

        btnScan = (Button) findViewById(R.id.btn_scan);
        btnScan.setOnClickListener(this);
        mHeartText = (TextView) findViewById(R.id.tx_heartrate);

        mHRM = (TextView) findViewById(R.id.tx_heart);
        mSPO = (TextView) findViewById(R.id.tx_spo);
        mTEMP1 = (TextView) findViewById(R.id.tx_temp1);
        mTEMP2 = (TextView) findViewById(R.id.tx_temp2);

        lyt_ecg = (FrameLayout) findViewById(R.id.lyt_ecg);
        lyt_acc = (FrameLayout) findViewById(R.id.lyt_acc);
        lyt_hrm = (FrameLayout) findViewById(R.id.lyt_hr);

        mECGFlowChart = (ECGChart) findViewById(R.id.ecg_flow_chart);
        mXFlowChart = (ACCChart) findViewById(R.id.x_flow_chart);
        mYFlowChart = (ACCChart) findViewById(R.id.y_flow_chart);
        mZFlowChart = (ACCChart) findViewById(R.id.z_flow_chart);
        mHRFlowChart = (HRChart) findViewById(R.id.hr_flow_chart);
        lyt_ecg.setVisibility(View.GONE);
        lyt_acc.setVisibility(View.GONE);
        lyt_hrm.setVisibility(View.VISIBLE);
        RadioButton rb1 = (RadioButton) findViewById(R.id.rd_ecg);
        RadioButton rb2 = (RadioButton) findViewById(R.id.rd_acc);
        RadioButton rb3 = (RadioButton) findViewById(R.id.rd_hrm);
        rb1.setChecked(false);
        rb2.setChecked(false);
        rb3.setChecked(true);
        nGraphType = 3;
        btnSave = (Button) findViewById(R.id.btn_save);
        btnSave.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Code here executes save_on main thread after user presses button
                if (isConnected) {
                    isSaving = !isSaving;
                    if (isSaving) {
                        strFileName = String.valueOf(System.currentTimeMillis());
                        strFileName += ".csv";
                        File folder = new File(Environment.getExternalStorageDirectory()
                                + "/HRM");
                        boolean var = false;
                        if (!folder.exists())
                            var = folder.mkdir();

                        final String filename = folder.toString() + "/" + strFileName;
                        try {
                            fWriter = new FileWriter(filename);
                            // fw.flush();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        Log.d("Filename", strFileName);
                        btnSave.setBackgroundResource(R.drawable.save_on);
                    } else {
                        btnSave.setBackgroundResource(R.drawable.save_off);
                        try {
                            fWriter.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        });
        btnLoad = (Button) findViewById(R.id.btn_load);
        btnLoad.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Code here executes save_on main thread after user presses button
                showFileChooser();
//                startActivityForResult(intent, YOUR_RESULT_CODE);
            }
        });
        btnPlay = (Button) findViewById(R.id.btn_play);
        btnPlay.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Code here executes save_on main thread after user presses button
//                File dir = Environment.getExternalStorageDirectory();
                if (!isPlaying) {
                    btnPlay.setText("Stop");
                    btnPlay.setBackgroundResource(R.drawable.radiusbuttonred);
                    File file = new File(strFileName);
                    disconnect();
                    nLoadIndex = 0;
                    mHRFlowChart.clearGraph();
                    if (file.exists()) {
                        BufferedReader br = null;
                        try {
                            br = new BufferedReader(new FileReader(file));
                        } catch (FileNotFoundException e) {
                            e.printStackTrace();
                        }
                        try {
                            String csvLine;
                            while ((csvLine = br.readLine()) != null) {
                                loadData = csvLine.split(",");
                            }
                            br.close();
                        } catch (IOException ex) {
                            throw new RuntimeException("Error in reading CSV file: " + ex);
                        }
                    } else {
                        Toast.makeText(getApplicationContext(), "file not exists", Toast.LENGTH_SHORT).show();
                    }
                    handler.postDelayed(runnable,100);

                } else {
                    btnPlay.setText("Play");
                    btnPlay.setBackgroundResource(R.drawable.radiusbuttongreen);
                    mHRFlowChart.clearGraph();
                    handler.removeCallbacks(runnable);
                }
                isPlaying = !isPlaying;
            }
        });
    }

    private static final int FILE_SELECT_CODE = 0;

    private void showFileChooser() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        try {
            startActivityForResult(
                    Intent.createChooser(intent, "Select a File to Upload"),
                    FILE_SELECT_CODE);
        } catch (android.content.ActivityNotFoundException ex) {
            // Potentially direct the user to the Market with a Dialog
            Toast.makeText(this, "Please install a File Manager.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        // TODO Auto-generated method stub
        super.onResume();
        initBLE();
    }

    private void initBLE() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
        }

        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        if (mBluetoothAdapter == null) {
            finish();
            return;
        }

        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    private void startScanBLE() {
        if (mLeDeviceListAdapter.mLeDevices.size() > 0) {
            currentPos = -1;
            mLeDeviceListAdapter.mLeDevices.clear();
            unCheckall();
        }

        btnScan.setText("Stop");
        if (mBluetoothAdapter.isEnabled()) {
            mBluetoothAdapter.startLeScan(this);
        } else {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    private void stopScanBLE() {
        btnScan.setText("Scan");
        if (mBluetoothAdapter != null) {
            mBluetoothAdapter.stopLeScan(this);
            mBluetoothAdapter.cancelDiscovery();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        } else if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_OK) {
            // initBLE();
        }
        switch (requestCode) {
            case FILE_SELECT_CODE:
                if (resultCode == RESULT_OK) {
                    // Get the Uri of the selected file
                    Uri uri = data.getData();
                    Log.d(TAG, "File Uri: " + uri.toString());
                    // Get the path
                    String path = null;
                    try {
                        path = FileUtils.getPath(this, uri);
                    } catch (URISyntaxException e) {
                        e.printStackTrace();
                    }
                    btnPlay.setEnabled(true);
                    btnPlay.setBackgroundResource(R.drawable.radiusbuttongreen);
                    Log.d(TAG, "File Path: " + path);
                    strFileName = path;
                }
                break;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onClick(View v) {
        if (isConnected) {
            Toast.makeText(this, "Please disconnect and try again.", Toast.LENGTH_SHORT).show();
            return;
        }
        // TODO Auto-generated method stub
        if (btnScan.isSelected()) {
            btnScan.setText("Scan");
            stopScanBLE();
            btnScan.setSelected(false);
        } else {
            btnScan.setText("Stop");
            startScanBLE();
            btnScan.setSelected(true);
        }
    }
    public void onRadioButtonClicked(View  v){
        RadioButton rb1 = (RadioButton) findViewById(R.id.rd_ecg);
        RadioButton rb2 = (RadioButton) findViewById(R.id.rd_acc);
        RadioButton rb3 = (RadioButton) findViewById(R.id.rd_hrm);
        boolean  checked = ((RadioButton) v).isChecked();
        switch(v.getId()){
            case R.id.rd_ecg:
                if(checked) {
                    nGraphType = 1;
                    lyt_ecg.setVisibility(View.VISIBLE);
                    lyt_acc.setVisibility(View.GONE);
                    lyt_hrm.setVisibility(View.GONE);
                    rb1.setTextColor(getResources().getColor(R.color.colorAccent));
                }
                rb3.setTextColor(Color.BLACK);
                rb2.setTextColor(Color.BLACK);
                break;
            case R.id.rd_acc:
                if(checked) {
                    nGraphType = 2;
                    lyt_ecg.setVisibility(View.GONE);
                    lyt_acc.setVisibility(View.VISIBLE);
                    lyt_hrm.setVisibility(View.GONE);
                    rb2.setTextColor(getResources().getColor(R.color.colorAccent));
                }
                rb3.setTextColor(Color.BLACK);
                rb1.setTextColor(Color.BLACK);
                break;
            case R.id.rd_hrm:
                if(checked) {
                    nGraphType = 3;
                    lyt_ecg.setVisibility(View.GONE);
                    lyt_acc.setVisibility(View.GONE);
                    lyt_hrm.setVisibility(View.VISIBLE);
                    rb3.setTextColor(getResources().getColor(R.color.colorAccent));
                }
                rb1.setTextColor(Color.BLACK);
                rb2.setTextColor(Color.BLACK);
                break;
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Checks the orientation of the screen for landscape and portrait and set portrait mode always
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        // TODO Auto-generated method stub
        unCheckall();
        BleDevice bleDevice = mLeDeviceListAdapter.getDevice(position);
        if (bleDevice == null)
            return;
        disconnect();
        nPercent = -1;
        if (currentPos != position) {
            currentPos = position;
            connectBle(bleDevice);
            currentBle = bleDevice;
            CheckBox chb = (CheckBox) view.findViewById(R.id.rd_select);
            chb.setChecked(true);
        } else if (currentPos == position) {
            currentPos = -1;
            CheckBox chb = (CheckBox) view.findViewById(R.id.rd_select);
            chb.setChecked(false);
        }
        Log.i("Check", ":" + position + ":" + bleDevice.device.getName());
    }

    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            return;
        }
        Log.d(TAG, "disconnect");

        mBluetoothGatt.close();
        mBluetoothGatt = null;
        currentBle = null;
        isConnected = false;
        isFirst = true;
        mHRM.setText("- BPM");
        mSPO.setText("- %");
        mTEMP1.setText("- ℃");
        mTEMP2.setText("- ℃");
        mHeartText.setText("- BPM");
        mHRFlowChart.clearGraph();
        mXFlowChart.clearGraph();
        mYFlowChart.clearGraph();
        mZFlowChart.clearGraph();
        mECGFlowChart.clearGraph();

    }

    boolean isConnected = false;

    public void connectBle(BleDevice _bleDevice) {
        Log.d(TAG, "connectBle");
        if (mBluetoothAdapter == null || _bleDevice.device.getAddress() == null) {

        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(_bleDevice.device.getAddress());
        if (device == null) {
        }

        if (device != null) {
            mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        }
        btnScan.setText("Scan");
        isConnected = true;
        stopScanBLE();
        btnScan.setSelected(false);
        Log.i("connect", "Trying to create a new connection.");
    }

    public void unCheckall() {
        LeDeviceListAdapter adapter = ((LeDeviceListAdapter) deviceList.getAdapter());
        for (int i = 0; i < deviceList.getChildCount(); i++) {
            RelativeLayout itemLayout = (RelativeLayout) deviceList.getChildAt(i);
            CheckBox cb = (CheckBox) itemLayout.findViewById(R.id.rd_select);
            cb.setChecked(false);
            adapter.mCheckStates[i] = false;
        }
    }

    @Override
    public void onLeScan(final BluetoothDevice device, final int rssi, byte[] scanRecord) {
        // TODO Auto-generated method stub
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                {
                    BleDevice leDevice = new BleDevice();
                    leDevice.device = device;
                    leDevice.isChecked = false;
                    leDevice.signal = rssi;
                    mLeDeviceListAdapter.addDevice(leDevice);
                    mLeDeviceListAdapter.notifyDataSetChanged();
                }
            }
        });
    }

    boolean doubleBackToExitPressedOnce = false;

    @Override
    public void onBackPressed() {
        if (doubleBackToExitPressedOnce) {
            stopScanBLE();
            disconnect();
            super.onBackPressed();
            return;
        }
        this.doubleBackToExitPressedOnce = true;
        Toast.makeText(this, "Please click BACK again to exit", Toast.LENGTH_SHORT).show();

        new Handler().postDelayed(new Runnable() {

            @Override
            public void run() {
                doubleBackToExitPressedOnce = false;
            }
        }, DOUBLE_BACK_PRESS_DURATION);
    }

    @Override
    protected void onDestroy() {
        // TODO Auto-generated method stub
        super.onDestroy();
        stopScanBLE();
        disconnect();
    }

    // Adapter for holding devices found through
    // scanning.//adapter///////////////////////////////////////////////////////////
    private class LeDeviceListAdapter extends BaseAdapter {
        private ArrayList<BleDevice> mLeDevices;
        private LayoutInflater mInflator;
        boolean mCheckStates[];

        LeDeviceListAdapter() {
            super();
            mLeDevices = new ArrayList<>();
            mInflator = MainActivity.this.getLayoutInflater();
            mCheckStates = new boolean[0];
        }

        void addDevice(BleDevice dev) {
            if ((dev.device.getName() != null)) {
                int i;
                int listSize = mLeDevices.size();
                for (i = 0; i < listSize; i++) {
                    if (mLeDevices.get(i).device.equals(dev.device)) {
                        break;
                    }
                }
                if (i >= listSize) {
                    mLeDevices.add(dev);
                    mCheckStates = Arrays.copyOf(mCheckStates, mCheckStates.length + 1);
                }
            }
        }

        BleDevice getDevice(int position) {
            return mLeDevices.get(position);
        }

        @Override
        public int getCount() {
            return mLeDevices.size();
        }

        @Override
        public Object getItem(int i) {
            return mLeDevices.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(final int i, View view, ViewGroup viewGroup) {
            final ViewHolder viewHolder;
            // General ListView optimization code.
            if (view == null) {
                view = mInflator.inflate(R.layout.item_device, null);
                viewHolder = new ViewHolder();
                viewHolder.deviceName = (TextView) view.findViewById(R.id.device_name);
                viewHolder.macName = (TextView) view.findViewById(R.id.mac_address);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }
            BleDevice bleDevice = mLeDevices.get(i);
            final String deviceName = bleDevice.device.getName() + "";
            final String mac = bleDevice.device.getAddress() + "";

            if (deviceName != null && deviceName.length() > 0) {
                viewHolder.deviceName.setText(deviceName);
                viewHolder.macName.setText(mac);
            } else {
                viewHolder.deviceName.setText("Unknown device");
                viewHolder.macName.setText(mac);
            }
            return view;
        }
    }

    private static class ViewHolder {
        TextView deviceName;
        TextView macName;

    }

    private class BleDevice {
        BluetoothDevice device;
        int signal = 0;
        boolean isChecked = false;
    }

    private boolean _checkPermission() {
        int nLog = ContextCompat.checkSelfPermission(this.getApplicationContext(), Manifest.permission.BLUETOOTH);
        Log.d("permisiontest", "nLog: " + nLog);
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(), Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
            // When user pressed Deny and still wants to use this functionality, show the rationale
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.BLUETOOTH)) {
                Log.d("permisiontest", "1 BLUETOOTH if");
            } else {
                Log.d("permisiontest", "1 BLUETOOTH else");
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH}, REQUEST_PERMISSION_REQ_CODE);
            }
        }
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(), Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
            // When user pressed Deny and still wants to use this functionality, show the rationale
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.BLUETOOTH_ADMIN)) {
                Log.d("permisiontest", "2 BLUETOOTH_ADMIN if");
            } else {
                Log.d("permisiontest", "2 BLUETOOTH_ADMIN else");
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH_ADMIN}, REQUEST_PERMISSION_REQ_CODE);
            }
        }
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            // When user pressed Deny and still wants to use this functionality, show the rationale
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_PERMISSION_REQ_CODE);
            }

        }
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(), Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            // When user pressed Deny and still wants to use this functionality, show the rationale
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_PERMISSION_REQ_CODE);
            }
        }
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(), Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
            // When user pressed Deny and still wants to use this functionality, show the rationale
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.BLUETOOTH)) {
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH}, REQUEST_PERMISSION_REQ_CODE);
            }
        }
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // When user pressed Deny and still wants to use this functionality, show the rationale
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION)) {
                Log.d("permisiontest", "3 ACCESS_COARSE_LOCATION if");
            } else {
                Log.d("permisiontest", "3 ACCESS_COARSE_LOCATION else");
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_PERMISSION_REQ_CODE);
            }
        }
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(), Manifest.permission.NFC) != PackageManager.PERMISSION_GRANTED) {
            // When user pressed Deny and still wants to use this functionality, show the rationale
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.NFC)) {
                Log.d("permisiontest", "4 NFC if");
            } else {
                Log.d("permisiontest", "4 NFC else");
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{Manifest.permission.NFC}, REQUEST_PERMISSION_REQ_CODE);
            }
        }
        return false;
    }

}
