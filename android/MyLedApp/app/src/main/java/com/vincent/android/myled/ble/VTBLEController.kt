package com.vincent.android.myled.ble

import VTBLECallback
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.vincent.android.myled.utils.DEVICE_NAME
import com.vincent.android.myled.utils.LED_CHARACTERISTIC_BRIGHTNESS_UUID
import com.vincent.android.myled.utils.LED_CHARACTERISTIC_DRAW_COLORFUL_UUID
import com.vincent.android.myled.utils.LED_CHARACTERISTIC_DRAW_NORMAL_UUID
import com.vincent.android.myled.utils.LED_CHARACTERISTIC_FILL_PIXEL_UUID
import com.vincent.android.myled.utils.LED_CHARACTERISTIC_FILL_SCREEN_UUID
import com.vincent.android.myled.utils.LED_CHARACTERISTIC_TEXT_UUID
import com.vincent.android.myled.utils.LED_MINIMUM_BRIGHTNESS
import com.vincent.android.myled.utils.LED_SERVICE_UUID
import com.vincent.android.myled.utils.LOG_TAG
import java.nio.ByteBuffer
import kotlin.math.min

@SuppressLint("MissingPermission")
class VTBLEController(
    private val mContext: Context,
    private val mDeviceAddress: String,
    private val mDeviceServiceID: String,
    private val mDeviceCharacteristicID: String
) {
    private var mGatt: BluetoothGatt? = null
    private var mCharacteristic: BluetoothGattCharacteristic? = null
    private var mBLEVTBLECallback: VTBLECallback = DefaultBLECallback()
    private var mScanCallback: ScanCallback? = null
    private var mHandler = Handler(Looper.getMainLooper())
    
    // 超时处理
    private val SCAN_TIMEOUT = 10000L // 10秒扫描超时
    private val CONNECTION_TIMEOUT = 15000L // 15秒连接超时
    
    private val scanTimeoutRunnable = Runnable {
        stopScan()
        mBLEVTBLECallback.onScanFailed()
    }
    
    private val connectionTimeoutRunnable = Runnable {
        disconnect()
        mBLEVTBLECallback.onScanFailed()
    }

    /**
     * 扫描设备
     */
    fun scan(callback: VTBLECallback) {
        mBLEVTBLECallback = callback
        
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            logd("Bluetooth not supported")
            callback.onScanFailed()
            return
        }
        
        if (!bluetoothAdapter.isEnabled) {
            logd("Bluetooth not enabled")
            callback.onScanFailed()
            return
        }

        val bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
        if (bluetoothLeScanner == null) {
            logd("Bluetooth LE scanner not available")
            callback.onScanFailed()
            return
        }

        mScanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                super.onScanResult(callbackType, result)
                result?.let { scanResult ->
                    logd("Found device: ${scanResult.device.name ?: "Unknown"} (${scanResult.device.address})")
                    
                    // 检查设备名称或地址匹配
                    if (scanResult.device.name == DEVICE_NAME ||
                        scanResult.device.address == mDeviceAddress) {
                        stopScan()
                        connectDevice(scanResult.device)
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                logd("Scan failed with error code: $errorCode")
                stopScan()
                callback.onScanFailed()
            }
        }

        // 开始扫描并设置超时
        bluetoothLeScanner.startScan(mScanCallback)
        mHandler.postDelayed(scanTimeoutRunnable, SCAN_TIMEOUT)
        logd("Started BLE scan with timeout: ${SCAN_TIMEOUT}ms")
    }
    
    private fun stopScan() {
        mHandler.removeCallbacks(scanTimeoutRunnable)
        mScanCallback?.let { callback ->
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(callback)
            }
            mScanCallback = null
        }
        logd("BLE scan stopped")
    }

    /**
     * 连接设备
     */
    private fun connectDevice(device: BluetoothDevice) {
        logd("Connecting to device: ${device.name} (${device.address})")
        mBLEVTBLECallback.onConnecting()
        
        // 设置连接超时
        mHandler.postDelayed(connectionTimeoutRunnable, CONNECTION_TIMEOUT)

        device.connectGatt(mContext, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                super.onConnectionStateChange(gatt, status, newState)
                logd("Connection state changed: $newState, status: $status")
                
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        mHandler.removeCallbacks(connectionTimeoutRunnable)
                        mGatt = gatt
                        mBLEVTBLECallback.onConnected(device.name, device.address)
                        gatt?.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        mHandler.removeCallbacks(connectionTimeoutRunnable)
                        mGatt = null
                        mCharacteristic = null
                        mBLEVTBLECallback.onDisConnected()
                    }
                    BluetoothProfile.STATE_CONNECTING -> {
                        mBLEVTBLECallback.onConnecting()
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                super.onServicesDiscovered(gatt, status)
                
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    logd("Services discovered successfully")
                    gatt?.services?.forEach { service ->
                        logd("Found service: ${service.uuid}")
                        if (service.uuid.toString() == mDeviceServiceID) {
                            checkCharacteristic(gatt, service)
                            return@forEach
                        }
                    }
                } else {
                    logd("Service discovery failed with status: $status")
                    mBLEVTBLECallback.onScanFailed()
                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?
            ) {
                super.onCharacteristicChanged(gatt, characteristic)
                characteristic?.value?.let { value ->
                    val stringValue = String(value, Charsets.UTF_8)
                    logd("Characteristic changed: $stringValue")
                }
            }
            
            override fun onCharacteristicWrite(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int
            ) {
                super.onCharacteristicWrite(gatt, characteristic, status)
                logd("Characteristic write completed with status: $status")
                mBLEVTBLECallback.writeDataCallback(status == BluetoothGatt.GATT_SUCCESS)
            }
        }, BluetoothDevice.TRANSPORT_LE)
    }

    /**
     *  检查特征值是否存在
     */
    private fun checkCharacteristic(gatt: BluetoothGatt, service: BluetoothGattService?) {
        service?.characteristics?.forEach { characteristic ->
            val characUUID = characteristic.uuid.toString()
            logd("Checking characteristic: $characUUID")
            
            if (characUUID == mDeviceCharacteristicID) {
                logd("Target characteristic found: $characUUID")
                mCharacteristic = characteristic
                
                // 启用通知
                val success = gatt.setCharacteristicNotification(characteristic, true)
                logd("Characteristic notification enabled: $success")
                
                mBLEVTBLECallback.onCheckCharacteristicSuccess()
                return@forEach
            }
        }
        
        // 如果没有找到目标特征值
        if (mCharacteristic == null) {
            logd("Target characteristic not found: $mDeviceCharacteristicID")
            mBLEVTBLECallback.onScanFailed()
        }
    }

    /**
     * 发送文本数据
     */
    fun sendText(serviceUUID: String, characteristicUUID: String, text: String) {
        if (mGatt == null) {
            logd("GATT not connected, cannot send text")
            mBLEVTBLECallback.writeDataCallback(false)
            return
        }
        
        if (text.isBlank()) {
            logd("Text is empty, cannot send")
            mBLEVTBLECallback.writeDataCallback(false)
            return
        }
        
        writeDataToCharacteristicText(mGatt!!, serviceUUID, characteristicUUID, text)
    }

    /**
     * 发送字节数据
     */
    fun sendBytes(serviceUUID: String, characteristicUUID: String, bytes: ByteArray) {
        if (mGatt == null) {
            logd("GATT not connected, cannot send bytes")
            mBLEVTBLECallback.writeDataCallback(false)
            return
        }
        
        if (bytes.isEmpty()) {
            logd("Bytes array is empty, cannot send")
            mBLEVTBLECallback.writeDataCallback(false)
            return
        }
        
        writeDataToCharacteristicBytes(mGatt!!, serviceUUID, characteristicUUID, bytes)
    }

    private fun sendLargeData(
        bluetoothGatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        data: ByteArray
    ) {
        val chunkSize = 20 // 默认情况下，每块20字节
        var offset = 0
        val length = min(chunkSize.toDouble(), (data.size - offset).toDouble()).toInt()
        val chunk = ByteArray(length)
        System.arraycopy(data, offset, chunk, 0, length)
        characteristic.setValue(chunk)
        bluetoothGatt.writeCharacteristic(characteristic)
        offset += length
        logd("sendLargeData: $offset")
    }

    private fun writeDataToCharacteristicText(
        gatt: BluetoothGatt,
        serviceUUID: String,
        characteristicUUID: String,
        text: String
    ) {
        writeDataToCharacteristicBytes(gatt, serviceUUID, characteristicUUID, text.toByteArray())
    }

    private fun writeDataToCharacteristicBytes(
        gatt: BluetoothGatt,
        serviceUUID: String,
        characteristicUUID: String,
        data: ByteArray
    ) {
        try {
            val service = gatt.services.find { it.uuid.toString() == serviceUUID }
            if (service == null) {
                logd("Service not found: $serviceUUID")
                mBLEVTBLECallback.writeDataCallback(false)
                return
            }
            
            val characteristic = service.characteristics.find { it.uuid.toString() == characteristicUUID }
            if (characteristic == null) {
                logd("Characteristic not found: $characteristicUUID")
                mBLEVTBLECallback.writeDataCallback(false)
                return
            }
            
            characteristic.setValue(data)
            val isSuccess = gatt.writeCharacteristic(characteristic)
            logd("Write characteristic result: $isSuccess")
            
            if (!isSuccess) {
                mBLEVTBLECallback.writeDataCallback(false)
            }
            // 成功的情况会在onCharacteristicWrite回调中处理
            
        } catch (e: Exception) {
            logd("Error writing characteristic: ${e.message}")
            mBLEVTBLECallback.writeDataCallback(false)
        }
    }
    
    /**
     * 断开连接
     */
    fun disconnect() {
        stopScan()
        mHandler.removeCallbacks(connectionTimeoutRunnable)
        mGatt?.disconnect()
        mGatt = null
        mCharacteristic = null
        logd("BLE connection disconnected")
    }
}

fun logd(text: String) {
    Log.d(LOG_TAG, text)
}



