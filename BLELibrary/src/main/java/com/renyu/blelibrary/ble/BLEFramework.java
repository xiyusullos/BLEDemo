package com.renyu.blelibrary.ble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.renyu.blelibrary.bean.BLEDevice;
import com.renyu.blelibrary.impl.BLEConnectListener;
import com.renyu.blelibrary.impl.BLEResponseListener;
import com.renyu.blelibrary.impl.BLEStateChangeListener;
import com.renyu.blelibrary.utils.HexUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

/**
 * Created by renyu on 2017/1/12.
 */

public class BLEFramework {

    private static BLEFramework bleFramework;

    // 服务UUID
    private UUID UUID_SERVICE = null;
    private UUID UUID_Characteristic = null;
    private UUID UUID_DESCRIPTOR = null;
    // 设备连接断开
    public static final int STATE_DISCONNECTED = 0;
    // 设备正在扫描
    public static final int STATE_SCANNING = 1;
    // 设备扫描结束
    public static final int STATE_SCANNED = 2;
    // 设备正在连接
    public static final int STATE_CONNECTING = 3;
    // 设备连接成功
    public static final int STATE_CONNECTED = 4;
    // 设备配置服务成功
    public static final int STATE_SERVICES_DISCOVERED = 5;
    // 当前设备状态
    private int connectionState=STATE_DISCONNECTED;

    // 搜索到的设备
    private ArrayList<BLEDevice> devices;
    // 临时搜索设备
    private HashMap<String, BLEDevice> tempsDevices;
    // 搜索所需时间
    private int timeSeconds=5000;
    // 搜索Handler
    private Handler handlerScan;

    // 数据发送队列
    private static RequestQueue requestQueue;

    private Context context;
    private BluetoothManager manager;
    private BluetoothAdapter adapter;
    private BluetoothAdapter.LeScanCallback leScanCallback;
    private BluetoothGattCallback bleGattCallback;
    private BluetoothGatt gatt;

    private BLEConnectListener bleConnectListener;
    private BLEStateChangeListener bleStateChangeListener;
    private BLEResponseListener bleResponseListener;

    public static BLEFramework getBleFrameworkInstance(Context context,
                                                       UUID UUID_SERVICE, UUID UUID_Characteristic, UUID UUID_DESCRIPTOR) {
        if (bleFramework==null) {
            synchronized (BLEFramework.class) {
                if (bleFramework==null) {
                    bleFramework=new BLEFramework(context.getApplicationContext(), UUID_SERVICE, UUID_Characteristic, UUID_DESCRIPTOR);
                    requestQueue=RequestQueue.getQueueInstance(context.getApplicationContext(), bleFramework);
                }
            }
        }
        return bleFramework;
    }

    public BLEFramework(Context context,
                        UUID UUID_SERVICE, UUID UUID_Characteristic, UUID UUID_DESCRIPTOR) {
        this.context=context;
        this.UUID_SERVICE=UUID_SERVICE;
        this.UUID_Characteristic=UUID_Characteristic;
        this.UUID_DESCRIPTOR=UUID_DESCRIPTOR;
        manager= (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        adapter= manager.getAdapter();
        devices=new ArrayList<>();
        tempsDevices=new HashMap<>();
        handlerScan=new Handler(Looper.getMainLooper());
        leScanCallback=new BluetoothAdapter.LeScanCallback() {
            @Override
            public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
                if (device!=null) {
                    if (!tempsDevices.containsKey(device.getAddress())) {
                        Log.d("onLeScan", device.getAddress());
                        Log.d("onLeScan", HexUtil.encodeHexStr(scanRecord));
                        BLEDevice device1=new BLEDevice();
                        device1.setRssi(rssi);
                        device1.setDevice(device);
                        device1.setScanRecord(scanRecord);
                        tempsDevices.put(device.getAddress(), device1);
                        devices.add(device1);
                    }
                }
            }
        };
        bleGattCallback=new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                super.onConnectionStateChange(gatt, status, newState);
                switch (newState) {
                    case BluetoothProfile.STATE_CONNECTED:
                        setConnectionState(STATE_CONNECTED);
                        // 开始搜索服务
                        gatt.discoverServices();
                        break;
                    case BluetoothProfile.STATE_DISCONNECTED:
                        setConnectionState(STATE_DISCONNECTED);
                        gatt.close();
                        BLEFramework.this.gatt=null;
                        devices.clear();
                        tempsDevices.clear();
                        break;
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                super.onServicesDiscovered(gatt, status);
                BLEFramework.this.gatt=gatt;
                if (status==BluetoothGatt.GATT_SUCCESS) {
                    if (gatt.getService(BLEFramework.this.UUID_SERVICE)!=null) {
                        BluetoothGattCharacteristic characteristic = gatt.getService(BLEFramework.this.UUID_SERVICE).getCharacteristic(BLEFramework.this.UUID_Characteristic);
                        if (enableNotification(characteristic, gatt, BLEFramework.this.UUID_DESCRIPTOR)) {
                            setConnectionState(STATE_SERVICES_DISCOVERED);
                            return;
                        }
                    }
                }
                disConnect();
            }

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                super.onCharacteristicRead(gatt, characteristic, status);
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                super.onCharacteristicWrite(gatt, characteristic, status);
                if (status==BluetoothGatt.GATT_SUCCESS) {
                    requestQueue.release();
                }
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                super.onCharacteristicChanged(gatt, characteristic);
                if (bleResponseListener!=null) {
                    bleResponseListener.getResponseValues(characteristic.getValue());
                }
            }

            @Override
            public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
                super.onReadRemoteRssi(gatt, rssi, status);
            }
        };
    }

    private void disConnect() {
        if (gatt!=null)
        gatt.disconnect();
    }

    private boolean enableNotification(BluetoothGattCharacteristic characteristic, BluetoothGatt gatt, UUID uuid) {
        boolean success = gatt.setCharacteristicNotification(characteristic, true);
        if(!success) {
            return false;
        }
        if (characteristic.getDescriptors().size()>0) {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(uuid);
            if(descriptor != null) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                gatt.writeDescriptor(descriptor);
                return true;
            }
        }
        return false;
    }

    public void setBleConnectListener(BLEConnectListener bleConnectListener) {
        this.bleConnectListener = bleConnectListener;
    }

    public void setBleStateChangeListener(BLEStateChangeListener bleStateChangeListener) {
        this.bleStateChangeListener = bleStateChangeListener;
    }

    public void setBleResponseListener(BLEResponseListener bleResponseListener) {
        this.bleResponseListener = bleResponseListener;
    }

    public void setTimeSeconds(int timeSeconds) {
        this.timeSeconds=timeSeconds;
    }

    /**
     * 开始扫描
     * @return
     */
    public void startScan() {
        boolean success=adapter.startLeScan(leScanCallback);
        if (success) {
            // 开始搜索
            setConnectionState(STATE_SCANNING);
            handlerScan.postDelayed(new Runnable() {
                @Override
                public void run() {
                    stopScan();
                }
            }, timeSeconds);
        }
        else {
            setConnectionState(STATE_DISCONNECTED);
        }
    }

    /**
     * 结束扫描
     */
    public void stopScan() {
        adapter.stopLeScan(leScanCallback);
        handlerScan.removeCallbacksAndMessages(null);
        // 搜索完毕
        setConnectionState(STATE_SCANNED);
        if (bleConnectListener!=null) {
            bleConnectListener.getAllScanDevice(devices);
        }
    }

    /**
     * 开始连接
     * @param device
     */
    public void startConn(BluetoothDevice device) {
        // 开始连接
        setConnectionState(STATE_CONNECTING);
        device.connectGatt(context, false, bleGattCallback);
    }

    /**
     * 获取当前状态
     * @return
     */
    public int getConnectionState() {
        return connectionState;
    }

    /**
     * 设置当前状态
     * @param state
     */
    private void setConnectionState(int state) {
        connectionState=state;
        if (bleStateChangeListener!=null) {
            bleStateChangeListener.getCurrentState(state);
        }
    }

    /**
     * 获取所有扫描后的设备
     * @return
     */
    public ArrayList<BLEDevice> getAllScanDevice() {
        return devices;
    }

    /**
     * 发送数据
     * @param value
     */
    protected void writeCharacteristic(byte[] value) {
        writeCharacteristic(UUID_Characteristic, value);
    }

    /**
     * 发送数据
     * @param uuid
     * @param value
     */
    protected void writeCharacteristic(UUID uuid, byte[] value) {
        if (gatt!=null) {
            BluetoothGattCharacteristic characteristic = gatt.getService(UUID_SERVICE).getCharacteristic(uuid);
            if (characteristic==null) {
                Log.d("BLEFramework", "writeCharacteristic中uuid不存在");
                return;
            }
            characteristic.setValue(value);
            if (!gatt.writeCharacteristic(characteristic)) {
                Log.d("BLEFramework", "writeCharacteristic失败");
            }
            else {
                Log.d("BLEFramework", "writeCharacteristic成功");
            }
        }
    }

    /**
     * 主动读数据
     * @param serviceUUID
     * @param CharacUUID
     */
    protected void readCharacteristic(UUID serviceUUID, UUID CharacUUID) {
        if (gatt!=null) {
            BluetoothGattCharacteristic characteristic = gatt.getService(serviceUUID).getCharacteristic(CharacUUID);
            if (characteristic==null) {
                Log.d("BLEFramework", "readCharacteristic中uuid不存在");
                return;
            }
            if (!gatt.readCharacteristic(characteristic)) {
                Log.d("BLEFramework", "readCharacteristic失败");
            }
            else {
                Log.d("BLEFramework", "readCharacteristic成功");
            }
        }
    }

    /**
     * 发送指令
     * @param sendValue
     */
    public void addCommand(byte[] sendValue) {
        requestQueue.add(sendValue);
    }
}
