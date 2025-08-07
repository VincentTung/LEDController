package com.vincent.android.myled.ble

import VTBLECallback
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.vincent.android.myled.ble.VTBluetoothUtil.getBluetoothAdapter

import com.vincent.android.myled.utils.CONNECTION_MONITOR_INTERVAL
import com.vincent.android.myled.utils.CONNECTION_TIMEOUT
import com.vincent.android.myled.utils.DEVICE_NAME
import com.vincent.android.myled.utils.LED_SERVICE_UUID
import com.vincent.android.myled.utils.MAX_RETRY_COUNT
import com.vincent.android.myled.utils.MTU_ACCEPTABLE
import com.vincent.android.myled.utils.MTU_DEFAULT
import com.vincent.android.myled.utils.MTU_FALLBACK
import com.vincent.android.myled.utils.MTU_GOOD
import com.vincent.android.myled.utils.MTU_OPTIMAL
import com.vincent.android.myled.utils.RETRY_DELAY
import com.vincent.android.myled.utils.SCAN_TIMEOUT
import com.vincent.android.myled.utils.SERVICE_DISCOVERY_TIMEOUT
import com.vincent.android.myled.utils.logd
import com.vincent.android.myled.utils.CoroutineUtil
import com.vincent.android.myled.utils.PHY_1M
import com.vincent.android.myled.utils.DeviceManager
import com.vincent.android.myled.utils.LED_CHARACTERISTIC_BRIGHTNESS_UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.math.min

/**
 * BLE连接状态枚举
 */
enum class BLEConnectionState {
    DISCONNECTED,    // 断开连接
    CONNECTING,      // 连接中
    CONNECTED,       // 已连接
    DISCOVERING,     // 服务发现中
    READY,          // 准备就绪
    ERROR           // 错误状态
}

private const val SCOPE_NAME = "VTBLEController"

@SuppressLint("MissingPermission")
class VTBLEController(
    private val mContext: Context,
    private val mDeviceServiceID: String,
    private val mDeviceCharacteristicID: String
) {

    companion object{
        private const val TAG = SCOPE_NAME
    }
    private var mGatt: BluetoothGatt? = null
    private var mCharacteristic: BluetoothGattCharacteristic? = null
    private var mBLEVTBLECallback: VTBLECallback = DefaultBLECallback()
    private var mScanCallback: ScanCallback? = null
    
    // 协程Job管理
    private var scanTimeoutJob: Job? = null
    private var connectionTimeoutJob: Job? = null
    private var serviceDiscoveryTimeoutJob: Job? = null
    private var connectionMonitorJob: Job? = null
    private var retryJob: Job? = null
    
    // 设备地址管理 - 用于快速重连
    private var mDeviceAddress: String = ""
    
    // 连接状态管理
    private var connectionState = BLEConnectionState.DISCONNECTED
    private var isConnecting = false
    private var isScanning = false
    
    // 重连机制
    private var retryCount = 0
    
    // MTU协商相关
    private var currentMtu = MTU_DEFAULT // 默认MTU大小
    private var mtuNegotiationAttempted = false
    private var mtuNegotiationFailed = false
    
    // PHY管理
    private val phyManager = VTPhyManager(mContext)
    
    // 配对管理
    private var isBonding = false
    private var bondingDevice: BluetoothDevice? = null
    
    // 配对状态广播接收器
    private val bondStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                    handleBondStateChange(device, bondState)
                }
            }
        }
    }

    /**
     * 处理连接失败
     */
    private fun handleConnectionFailure(reason: String) {
        logd("连接失败原因: $reason")
        connectionState = BLEConnectionState.ERROR
        isConnecting = false
        
        // 注销配对状态广播接收器
        try {
            mContext.unregisterReceiver(bondStateReceiver)
        } catch (e: Exception) {
            logd("注销广播接收器异常: ${e.message}")
        }
        
        if (retryCount < MAX_RETRY_COUNT) {
            retryCount++
            logd("准备第 $retryCount 次重试连接...")
            retryJob = CoroutineUtil.delayInScope(SCOPE_NAME, RETRY_DELAY) {
                if (connectionState == BLEConnectionState.ERROR) {
                    logd("开始重试连接...")
                    retryConnection()
                }
            }
        } else {
            logd("重试次数已达上限，连接失败")
            retryCount = 0
            mBLEVTBLECallback.onScanFailed()
        }
    }
    
    /**
     * 重试连接
     */
    private fun retryConnection() {
        if (connectionState == BLEConnectionState.ERROR) {
            logd("=== 开始重试连接 ===")
            connectionState = BLEConnectionState.DISCONNECTED
            isConnecting = false
            // 使用扫描模式重试连接
            scan(mBLEVTBLECallback)
        }
    }
    
    /**
     * 开始连接监控
     */
    private fun startConnectionMonitor() {
        stopConnectionMonitor()
        connectionMonitorJob = CoroutineUtil.timerInScope(SCOPE_NAME, CONNECTION_MONITOR_INTERVAL) {
            if (connectionState == BLEConnectionState.CONNECTED || 
                connectionState == BLEConnectionState.READY) {
                // 检查GATT连接状态
                val gatt = mGatt
                if (gatt != null) {
                    try {
                        val bluetoothManager = mContext.getSystemService(android.bluetooth.BluetoothManager::class.java)
                        val device = gatt.device // 使用已连接的设备
                        val connectionState = bluetoothManager.getConnectionState(device, BluetoothProfile.GATT)
                        
                        if (connectionState == BluetoothProfile.STATE_DISCONNECTED) {
                            logd("=== 连接监控检测到连接断开 ===")
                            handleUnexpectedDisconnection()
                        }
                    } catch (e: Exception) {
                        logd("连接监控检查异常: ${e.message}")
                    }
                }
            }
        }
    }
    
    /**
     * 停止连接监控
     */
    private fun stopConnectionMonitor() {
        connectionMonitorJob?.cancel()
        connectionMonitorJob = null
    }
    
    /**
     * 处理意外断开连接
     */
    private fun handleUnexpectedDisconnection() {
        logd("=== 处理意外断开连接 ===")
        connectionState = BLEConnectionState.DISCONNECTED
        stopConnectionMonitor()
        
        // 注销配对状态广播接收器
        try {
            mContext.unregisterReceiver(bondStateReceiver)
        } catch (e: Exception) {
            logd("注销广播接收器异常: ${e.message}")
        }
        
        mBLEVTBLECallback.onDisConnected()
    }
    
    /**
     * 更新连接状态
     */
    private fun updateConnectionState(newState: BLEConnectionState) {
        val oldState = connectionState
        connectionState = newState
        logd("连接状态变化: $oldState -> $newState")
    }

    /**
     * 扫描设备
     */
    fun scan(callback: VTBLECallback) {
        if (isScanning) {
            logd("扫描已在进行中，忽略重复请求")
            return
        }
        
        logd("=== VTBLEController: 扫描模式 ===")
        mBLEVTBLECallback = callback
        retryCount = 0
        
        val bluetoothAdapter = VTBluetoothUtil.getBluetoothAdapter(mContext)
        if (bluetoothAdapter == null) {
            logd("错误: 设备不支持蓝牙")
            callback.onScanFailed()
            return
        }
        
        if (!bluetoothAdapter.isEnabled) {
            logd("错误: 蓝牙未启用")
            callback.onScanFailed()
            return
        }

        val bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
        if (bluetoothLeScanner == null) {
            logd("错误: 蓝牙LE扫描器不可用")
            callback.onScanFailed()
            return
        }

        logd("开始扫描周围的BLE设备...")
        logd("目标设备名称: $DEVICE_NAME")
        logd("目标服务UUID: $LED_SERVICE_UUID")
        logd("扫描超时时间: ${SCAN_TIMEOUT}ms")

        // 创建扫描过滤器，优先使用Service UUID过滤
        val scanFilters = mutableListOf<android.bluetooth.le.ScanFilter>()
        
        // 添加Service UUID过滤器 - 这是最可靠的方法
        try {
            val serviceUuid = java.util.UUID.fromString(LED_SERVICE_UUID)
            val serviceFilter = android.bluetooth.le.ScanFilter.Builder()
                .setServiceUuid(android.os.ParcelUuid(serviceUuid))
                .build()
            scanFilters.add(serviceFilter)
            logd("添加服务UUID过滤器: $LED_SERVICE_UUID")
        } catch (e: IllegalArgumentException) {
            logd("服务UUID格式无效，跳过UUID过滤器: $LED_SERVICE_UUID")
        }
        
        // 如果知道设备名称，添加名称过滤器作为备选
        if (DEVICE_NAME.isNotEmpty()) {
            val nameFilter = android.bluetooth.le.ScanFilter.Builder()
                .setDeviceName(DEVICE_NAME)
                .build()
            scanFilters.add(nameFilter)
            logd("添加设备名称过滤器: $DEVICE_NAME")
        }
        
        // 创建扫描设置，优化扫描参数
        val scanSettings = android.bluetooth.le.ScanSettings.Builder()
            .setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY) // 低延迟模式
            .setReportDelay(0) // 立即报告结果
            .build()
        
        logd("扫描过滤器数量: ${scanFilters.size}")
        logd("扫描模式: 低延迟模式")

        mScanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                super.onScanResult(callbackType, result)
                result?.let { scanResult ->
                    val deviceName = scanResult.device.name ?: "Unknown"
                    val deviceAddress = scanResult.device.address
                    val rssi = scanResult.rssi
                    val scanRecord = scanResult.scanRecord
                    
                    logd("扫描发现设备: $deviceName ($deviceAddress) RSSI: ${rssi}dBm")
                    
                    // 检查设备是否为目标设备
                    if (isTargetDevice(deviceName, scanRecord)) {
                        logd("找到目标设备，停止扫描")
                        logd("匹配的设备名称: $deviceName")
                        logd("匹配的设备地址: $deviceAddress")
                        logd("信号强度: ${rssi}dBm")
                        
                        // 记录信号强度信息用于连接质量评估
                        if (rssi > -70) {
                            logd("信号强度良好，适合连接")
                        } else if (rssi > -80) {
                            logd("信号强度一般，可能影响连接稳定性")
                        } else {
                            logd("信号强度较弱，建议靠近设备")
                        }
                        
                        stopScan()
                        // 找到目标设备后立即连接
                        connectDevice(scanResult.device)
                    } else {
                        // 记录其他发现的设备（用于调试）
                        if (deviceName.isNotEmpty() && deviceName != "Unknown") {
                            logd("发现其他设备: $deviceName ($deviceAddress) RSSI: ${rssi}dBm")
                        }
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                logd("扫描失败，错误代码: $errorCode")
                stopScan()
                handleConnectionFailure("扫描失败，错误代码: $errorCode")
            }
        }

        // 开始扫描并设置超时
        isScanning = true
        bluetoothLeScanner.startScan(scanFilters, scanSettings, mScanCallback)
        
        // 使用协程设置扫描超时
        scanTimeoutJob = CoroutineUtil.delayInScope(SCOPE_NAME, SCAN_TIMEOUT) {
            logd("=== 扫描超时 ===")
            logd("扫描时间超过 ${SCAN_TIMEOUT}ms，停止扫描")
            stopScan()
            handleConnectionFailure("扫描超时")
        }
        
        logd("BLE扫描已启动，等待设备发现...")
    }
    
    /**
     * 智能连接设备
     * 优先使用保存的设备地址进行快速连接，失败则回退到扫描模式
     */
    fun connect(callback: VTBLECallback) {
        if (isConnecting) {
            logd("连接已在进行中，忽略重复请求")
            return
        }
        
        mBLEVTBLECallback = callback
        retryCount = 0
        
        // 检查是否有保存的设备地址
        if (hasDeviceAddress()) {
            logd("=== 尝试快速重连模式 ===")
            logd("使用保存的设备地址: $mDeviceAddress")
            connectToDeviceByAddress(mDeviceAddress, callback)
        } else {
            logd("=== 使用扫描模式连接 ===")
            logd("未找到保存的设备地址，开始扫描")
            scan(callback)
        }
    }
    
    /**
     * 直接连接指定设备（通过设备地址）
     */
    fun connectToDeviceByAddress(deviceAddress: String, callback: VTBLECallback) {
        if (isConnecting) {
            logd("连接已在进行中，忽略重复请求")
            return
        }
        
        logd("=== VTBLEController: 直接连接模式 ===")
        mBLEVTBLECallback = callback
        retryCount = 0
        
        val bluetoothAdapter = getBluetoothAdapter(mContext)
        if (bluetoothAdapter == null) {
            logd("错误: 设备不支持蓝牙")
            callback.onScanFailed()
            return
        }
        
        if (!bluetoothAdapter.isEnabled) {
            logd("错误: 蓝牙未启用")
            callback.onScanFailed()
            return
        }
        
        // 如果提供了设备地址，尝试直接连接
        if (deviceAddress.isNotEmpty()) {
            try {
                val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
                logd("获取到远程设备对象")
                logd("目标设备地址: ${device.address}")
                logd("开始直接连接流程...")
                connectDevice(device)
            } catch (e: IllegalArgumentException) {
                logd("错误: 无效的设备地址: $deviceAddress")
                logd("将使用扫描模式查找设备...")
                // 如果地址无效，回退到扫描模式
                scan(callback)
            } catch (e: Exception) {
                logd("错误: 连接设备时发生异常: ${e.message}")
                logd("将使用扫描模式查找设备...")
                // 发生异常时，回退到扫描模式
                scan(callback)
            }
        } else {
            logd("未提供设备地址，使用扫描模式查找设备...")
            // 没有地址时，使用扫描模式
            scan(callback)
        }
    }
    
    private fun stopScan() {
        logd("=== 停止扫描 ===")
        scanTimeoutJob?.cancel()
        mScanCallback?.let { callback ->
            val bluetoothAdapter = getBluetoothAdapter(mContext)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(callback)
            }
            mScanCallback = null
        }
        isScanning = false
        logd("BLE扫描已停止")
    }

    /**
     * 连接设备
     */
    private fun connectDevice(device: BluetoothDevice) {
        if (isConnecting) {
            logd("连接已在进行中，忽略重复请求")
            return
        }
        
        logd("=== 开始连接设备 ===")
        logd("设备名称: ${device.name ?: "未知"}")
        logd("设备地址: ${device.address}")
        logd("连接超时时间: ${CONNECTION_TIMEOUT}ms")
        
        // 重置MTU协商状态
        resetMtuNegotiationState()
        
        // 重置PHY协商状态
        phyManager.resetPhyNegotiationState()
        
        isConnecting = true
        updateConnectionState(BLEConnectionState.CONNECTING)
        
        // 注册配对状态广播接收器
        val bondFilter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        mContext.registerReceiver(bondStateReceiver, bondFilter)
        
        // 使用协程设置连接超时
        connectionTimeoutJob = CoroutineUtil.delayInScope(SCOPE_NAME, CONNECTION_TIMEOUT) {
            logd("=== 连接超时 ===")
            logd("连接时间超过 ${CONNECTION_TIMEOUT}ms，断开连接")
            disconnect()
            handleConnectionFailure("连接超时")
        }
        
        // 使用协程设置服务发现超时
        serviceDiscoveryTimeoutJob = CoroutineUtil.delayInScope(SCOPE_NAME, SERVICE_DISCOVERY_TIMEOUT) {
            logd("=== 服务发现超时 ===")
            logd("服务发现时间超过 ${SERVICE_DISCOVERY_TIMEOUT}ms")
            disconnect()
            handleConnectionFailure("服务发现超时")
        }
        
        logd("正在连接设备...")
        
        // 使用优化的连接参数
        val connectOptions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Android 8.0+ 支持连接参数优化
            BluetoothDevice.TRANSPORT_LE
        } else {
            BluetoothDevice.TRANSPORT_AUTO
        }
        
        mGatt = device.connectGatt(mContext, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                super.onConnectionStateChange(gatt, status, newState)
                
                logd("连接状态变化: $newState, 状态码: $status")
                
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        logd("=== 设备连接成功 ===")
                        connectionTimeoutJob?.cancel()
                        mGatt = gatt
                        
                        // 保存设备地址用于快速重连
                        if (device.address.isNotEmpty()) {
                            setDeviceAddress(device.address)
                            logd("已保存设备地址用于快速重连: ${device.address}")
                        }
                        
                        // 检查是否需要配对
                        checkAndRequestBonding(device)
                        
                        updateConnectionState(BLEConnectionState.CONNECTED)
                        mBLEVTBLECallback.onConnected(device.name, device.address)
                        
                        // 请求MTU大小 - 使用智能协商策略
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            requestMtuWithFallback(gatt)
                        }
                        
                        // 开始PHY协商 - 在MTU协商之后进行
                        startPhyNegotiation(gatt)
                        
                        logd("开始发现服务...")
                        updateConnectionState(BLEConnectionState.DISCOVERING)
                        gatt?.discoverServices()
                        startConnectionMonitor() // 连接成功后启动监控
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        logd("=== 设备连接断开 ===")
                        connectionTimeoutJob?.cancel()
                        serviceDiscoveryTimeoutJob?.cancel()
                        mGatt = null
                        mCharacteristic = null
                        updateConnectionState(BLEConnectionState.DISCONNECTED)
                        isConnecting = false
                        mBLEVTBLECallback.onDisConnected()
                        handleUnexpectedDisconnection() // 断开连接时也处理意外断开
                    }
                    BluetoothProfile.STATE_CONNECTING -> {
                        logd("正在连接设备...")
                        updateConnectionState(BLEConnectionState.CONNECTING)
                        mBLEVTBLECallback.onConnecting()
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                super.onServicesDiscovered(gatt, status)
                
                serviceDiscoveryTimeoutJob?.cancel()
                
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    logd("=== 服务发现成功 ===")
                    gatt?.services?.forEach { service ->
                        logd("发现服务: ${service.uuid}")
                        if (service.uuid.toString() == mDeviceServiceID) {
                            logd("找到目标服务: ${service.uuid}")
                            checkCharacteristic(gatt, service)
                            return@forEach
                        }
                    }
                } else {
                    logd("服务发现失败，状态码: $status")
                    handleConnectionFailure("服务发现失败，状态码: $status")
                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?
            ) {
                super.onCharacteristicChanged(gatt, characteristic)
                characteristic?.value?.let { value ->
                    val stringValue = String(value, Charsets.UTF_8)
                    val characteristicUUID = characteristic.uuid.toString()
                    logd("Characteristic changed: $characteristicUUID = $stringValue")
                    
                    // 处理亮度特征的通知
                    if (characteristicUUID == LED_CHARACTERISTIC_BRIGHTNESS_UUID) {
                        try {
                            val brightness = stringValue.toInt()
                            logd("收到亮度通知: $brightness")
                            // 通知回调处理亮度值
                            mBLEVTBLECallback.onBrightnessReceived(brightness)
                        } catch (e: NumberFormatException) {
                            logd("亮度值格式错误: $stringValue")
                        }
                    }
                }
            }
            
            override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
                super.onMtuChanged(gatt, mtu, status)
                handleMtuNegotiationResult(gatt, mtu, status)
            }
            
            override fun onPhyRead(gatt: BluetoothGatt?, txPhy: Int, rxPhy: Int, status: Int) {
                super.onPhyRead(gatt, txPhy, rxPhy, status)
                phyManager.handlePhyReadResult(txPhy, rxPhy, status)
            }
            
            override fun onPhyUpdate(gatt: BluetoothGatt?, txPhy: Int, rxPhy: Int, status: Int) {
                super.onPhyUpdate(gatt, txPhy, rxPhy, status)
                phyManager.handlePhyUpdateResult(txPhy, rxPhy, status)
            }
            
            override fun onCharacteristicWrite(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int
            ) {
                super.onCharacteristicWrite(gatt, characteristic, status)
                logd("Characteristic write completed with status: $status")
                
                // 处理同步写入状态
                if (syncWritePending) {
                    syncWriteResult = (status == BluetoothGatt.GATT_SUCCESS)
                    syncWritePending = false
                    logd("同步写入状态更新: $syncWriteResult")
                }
                
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    // 如果是分块发送，调用成功回调继续发送下一个数据块
                    onCharacteristicWriteSuccess()
                }
                
                mBLEVTBLECallback.writeDataCallback(status == BluetoothGatt.GATT_SUCCESS)
            }
            
            // 配对状态变化回调 - 使用广播接收器处理
            // onBondStateChanged 不是 BluetoothGattCallback 的方法
            // 配对状态变化通过广播接收器处理
        }, connectOptions)
    }

    /**
     *  检查特征值是否存在
     */
    private fun checkCharacteristic(gatt: BluetoothGatt, service: BluetoothGattService?) {
        logd("=== 检查特征值 ===")
        var foundTargetCharacteristic = false
        var foundBrightnessCharacteristic = false
        
        service?.characteristics?.forEach { characteristic ->
            val characUUID = characteristic.uuid.toString()
            logd("检查特征值: $characUUID")
            
            if (characUUID == mDeviceCharacteristicID) {
                logd("=== 找到目标特征值 ===")
                logd("特征值UUID: $characUUID")
                mCharacteristic = characteristic
                foundTargetCharacteristic = true
                
                // 启用通知
                val success = gatt.setCharacteristicNotification(characteristic, true)
                logd("特征值通知启用状态: $success")
                
                if (success) {
                    logd("=== 设备连接流程完成 ===")
                    updateConnectionState(BLEConnectionState.READY)
                    isConnecting = false
                    mBLEVTBLECallback.onCheckCharacteristicSuccess()
                } else {
                    logd("错误: 启用特征值通知失败")
                    handleConnectionFailure("启用特征值通知失败")
                }
                return@forEach
            }
            
            // 检查亮度特征
            if (characUUID == LED_CHARACTERISTIC_BRIGHTNESS_UUID) {
                logd("=== 找到亮度特征值 ===")
                logd("亮度特征值UUID: $characUUID")
                foundBrightnessCharacteristic = true
                
                // 启用亮度特征的通知
                val success = gatt.setCharacteristicNotification(characteristic, true)
                logd("亮度特征值通知启用状态: $success")
                
                if (!success) {
                    logd("警告: 启用亮度特征值通知失败")
                }
            }
        }
        
        // 如果没有找到目标特征值
        if (!foundTargetCharacteristic) {
            logd("错误: 未找到目标特征值: $mDeviceCharacteristicID")
            handleConnectionFailure("未找到目标特征值")
        }
        
        // 记录亮度特征查找结果
        if (foundBrightnessCharacteristic) {
            logd("亮度特征值查找成功")
        } else {
            logd("警告: 未找到亮度特征值")
        }
    }

    /**
     * 发送文本数据
     */
    fun sendText(serviceUUID: String, characteristicUUID: String, text: String) {
        if (connectionState != BLEConnectionState.READY) {
            logd("设备未准备就绪，当前状态: $connectionState，无法发送文本")
            mBLEVTBLECallback.writeDataCallback(false)
            return
        }
        
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
        if (connectionState != BLEConnectionState.READY) {
            logd("设备未准备就绪，当前状态: $connectionState，无法发送字节数据")
            mBLEVTBLECallback.writeDataCallback(false)
            return
        }
        
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

    // 同步写入状态管理
    private var syncWritePending = false
    private var syncWriteResult = false
    private var syncWriteCompleted = false

    /**
     * 同步发送字节数据（等待确认）
     * 注意：此方法仍保持同步接口以兼容现有代码，但内部使用协程优化
     */
    fun sendBytesSync(serviceUUID: String, characteristicUUID: String, bytes: ByteArray): Boolean {
        if (connectionState != BLEConnectionState.READY) {
            logd("设备未准备就绪，当前状态: $connectionState，无法发送字节数据")
            return false
        }
        
        if (mGatt == null) {
            logd("GATT not connected, cannot send bytes")
            return false
        }
        
        if (bytes.isEmpty()) {
            logd("Bytes array is empty, cannot send")
            return false
        }
        
        // 重置同步写入状态
        syncWritePending = true
        syncWriteResult = false
        syncWriteCompleted = false
        
        val result = writeDataToCharacteristicBytesSync(mGatt!!, serviceUUID, characteristicUUID, bytes)
        
        if (!result) {
            logd("同步写入请求失败")
            syncWritePending = false
            return false
        }
        
        // 使用协程等待写入完成，最多等待2秒
        return runBlocking {
            var waitCount = 0
            while (syncWritePending && waitCount < 200) { // 200 * 10ms = 2秒
                delay(10) // 使用协程delay替代Thread.sleep
                waitCount++
            }
            
            if (syncWritePending) {
                logd("同步写入超时")
                syncWritePending = false
                false
            } else {
                logd("同步写入完成，结果: $syncWriteResult")
                syncWriteResult
            }
        }
    }

    /**
     * 写入特征值的辅助方法，兼容新旧API
     */
    private fun writeCharacteristicCompat(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        data: ByteArray,
        writeType: Int = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
    ): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val result = gatt.writeCharacteristic(characteristic, data, writeType)
            result == BluetoothGatt.GATT_SUCCESS
        } else {
            try {
                characteristic.setValue(data)
                gatt.writeCharacteristic(characteristic)
            } catch (e: Exception) {
                logd("兼容性写入失败: ${e.message}")
                false
            }
        }
    }

    /**
     * 发送大块数据（分块传输）
     */
    private fun sendLargeData(
        bluetoothGatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        data: ByteArray
    ) {
        val chunkSize = currentMtu // 使用动态MTU大小
        var offset = 0
        
        // 创建发送队列
        val sendQueue = mutableListOf<ByteArray>()
        while (offset < data.size) {
            val remainingBytes = data.size - offset
            val currentChunkSize = min(chunkSize, remainingBytes)
            val chunk = ByteArray(currentChunkSize)
            System.arraycopy(data, offset, chunk, 0, currentChunkSize)
            sendQueue.add(chunk)
            offset += currentChunkSize
        }
        
        logd("准备发送 ${sendQueue.size} 个数据块，总大小: ${data.size} 字节，块大小: $chunkSize 字节")
        
        // 开始发送第一个数据块
        sendNextChunk(bluetoothGatt, characteristic, sendQueue, 0)
    }
    
    private fun sendNextChunk(
        bluetoothGatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        sendQueue: List<ByteArray>,
        currentIndex: Int
    ) {
        if (currentIndex >= sendQueue.size) {
            logd("所有数据块发送完成，总大小: ${sendQueue.size * currentMtu} 字节")
            // 清理发送状态
            currentSendQueue = null
            currentSendIndex = 0
            currentSendCharacteristic = null
            currentSendGatt = null
            mBLEVTBLECallback.writeDataCallback(true)
            return
        }
        
        val chunk = sendQueue[currentIndex]
        val success = writeCharacteristicCompat(bluetoothGatt, characteristic, chunk)
        
        if (!success) {
            logd("发送数据块失败，索引: $currentIndex")
            // 清理发送状态
            currentSendQueue = null
            currentSendIndex = 0
            currentSendCharacteristic = null
            currentSendGatt = null
            mBLEVTBLECallback.writeDataCallback(false)
            return
        }
        
        logd("发送数据块 ${currentIndex + 1}/${sendQueue.size}，等待确认...")
        
        // 存储当前发送状态，等待onCharacteristicWrite回调
        currentSendQueue = sendQueue
        currentSendIndex = currentIndex
        currentSendCharacteristic = characteristic
        currentSendGatt = bluetoothGatt
    }
    
    // 在onCharacteristicWrite回调中调用
    fun onCharacteristicWriteSuccess() {
        val queue = currentSendQueue
        val index = currentSendIndex
        val characteristic = currentSendCharacteristic
        val gatt = currentSendGatt
        
        if (queue != null && characteristic != null && gatt != null) {
            logd("数据块 ${index + 1} 发送确认成功")
            
            // 使用协程处理延迟，避免阻塞主线程
            CoroutineUtil.delayInScope(SCOPE_NAME, 20) {
                // 发送下一个数据块
                sendNextChunk(gatt, characteristic, queue, index + 1)
            }
        }
    }
    
    // 成员变量用于跟踪发送状态
    private var currentSendQueue: List<ByteArray>? = null
    private var currentSendIndex: Int = 0
    private var currentSendCharacteristic: BluetoothGattCharacteristic? = null
    private var currentSendGatt: BluetoothGatt? = null

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
            
            // 检查特征值是否支持写操作
            if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) == 0 &&
                (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) == 0) {
                logd("Characteristic does not support write operations: $characteristicUUID")
                mBLEVTBLECallback.writeDataCallback(false)
                return
            }
            
            // 根据数据大小选择发送方式
            if (data.size > currentMtu) {
                logd("数据大小超过MTU大小，使用分块传输: ${data.size} 字节")
                sendLargeData(gatt, characteristic, data)
            } else {
                val isSuccess = writeCharacteristicCompat(gatt, characteristic, data)
                logd("Write characteristic result: $isSuccess")
                
                if (!isSuccess) {
                    mBLEVTBLECallback.writeDataCallback(false)
                }
            }
            
        } catch (e: Exception) {
            logd("Error writing characteristic: ${e.message}")
            mBLEVTBLECallback.writeDataCallback(false)
        }
    }

    private fun writeDataToCharacteristicBytesSync(
        gatt: BluetoothGatt,
        serviceUUID: String,
        characteristicUUID: String,
        data: ByteArray
    ): Boolean {
        try {
            val service = gatt.services.find { it.uuid.toString() == serviceUUID }
            if (service == null) {
                logd("Service not found: $serviceUUID")
                return false
            }
            
            val characteristic = service.characteristics.find { it.uuid.toString() == characteristicUUID }
            if (characteristic == null) {
                logd("Characteristic not found: $characteristicUUID")
                return false
            }
            
            // 检查特征值是否支持写操作
            if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) == 0 &&
                (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) == 0) {
                logd("Characteristic does not support write operations: $characteristicUUID")
                return false
            }
            
            // 对于同步发送，只处理小数据包
            if (data.size > currentMtu) {
                logd("同步发送不支持大数据包: ${data.size} 字节")
                return false
            }
            
            val isSuccess = writeCharacteristicCompat(gatt, characteristic, data)
            logd("Sync write characteristic request result: $isSuccess")
            
            return isSuccess
            
        } catch (e: Exception) {
            logd("Error in sync write characteristic: ${e.message}")
            return false
        }
    }
    
    /**
     * 断开连接
     */
    fun disconnect() {
        logd("=== 断开BLE连接 ===")
        
        // 取消所有协程任务
        scanTimeoutJob?.cancel()
        connectionTimeoutJob?.cancel()
        serviceDiscoveryTimeoutJob?.cancel()
        connectionMonitorJob?.cancel()
        retryJob?.cancel()
        
        // 停止扫描
        stopScan()
        stopConnectionMonitor()
        
        // 断开GATT连接
        mGatt?.let { gatt ->
            try {
                gatt.disconnect()
                gatt.close()
            } catch (e: Exception) {
                logd("断开GATT连接异常: ${e.message}")
            }
        }
        
        // 重置状态
        mGatt = null
        mCharacteristic = null
        isConnecting = false
        isScanning = false
        updateConnectionState(BLEConnectionState.DISCONNECTED)
        
        // 重置MTU协商状态
        resetMtuNegotiationState()
        
        // 重置PHY协商状态
        phyManager.resetPhyNegotiationState()
        
        // 注销配对状态广播接收器
        try {
            mContext.unregisterReceiver(bondStateReceiver)
        } catch (e: Exception) {
            logd("注销广播接收器异常: ${e.message}")
        }
        
        logd("BLE连接已断开")
    }
    
    /**
     * 获取当前连接状态
     */
    fun getConnectionState(): BLEConnectionState {
        return connectionState
    }
    
    /**
     * 检查是否已连接
     */
    fun isConnected(): Boolean {
        return connectionState == BLEConnectionState.READY
    }
    
    /**
     * 检查是否正在连接
     */
    fun isConnecting(): Boolean {
        return isConnecting
    }
    
    /**
     * 强制重置连接状态（用于异常恢复）
     */
    fun forceReset() {
        logd("=== 强制重置BLE控制器状态 ===")
        disconnect()
        connectionState = BLEConnectionState.DISCONNECTED
        isConnecting = false
        isScanning = false
        retryCount = 0
        
        // 确保注销广播接收器
        try {
            mContext.unregisterReceiver(bondStateReceiver)
        } catch (e: Exception) {
            logd("强制重置时注销广播接收器异常: ${e.message}")
        }
        
        logd("BLE控制器状态已重置")
    }

    /**
     * 判断设备是否为目标设备
     * 优先检查Service UUID，其次检查设备名称
     */
    private fun isTargetDevice(deviceName: String?, scanRecord: android.bluetooth.le.ScanRecord?): Boolean {
        // 1. 检查设备名称匹配
        val nameMatch = deviceName == DEVICE_NAME
        if (nameMatch) {
            logd("设备名称匹配: $deviceName")
        }
        
        // 2. 检查Service UUID匹配（更可靠）
        val uuidMatch = scanRecord?.serviceUuids?.any { uuid ->
            uuid.uuid.toString().equals(LED_SERVICE_UUID, ignoreCase = true)
        } ?: false
        
        if (uuidMatch) {
            logd("服务UUID匹配: ${scanRecord?.serviceUuids}")
        }
        
        // 3. 检查广播数据中的Service UUID
        val advDataUuidMatch = scanRecord?.serviceData?.keys?.any { uuid ->
            uuid.toString().equals(LED_SERVICE_UUID, ignoreCase = true)
        } ?: false
        
        if (advDataUuidMatch) {
            logd("广播数据中的服务UUID匹配")
        }
        
        // 4. 检查制造商数据（如果有特定的制造商数据）
        val manufacturerData = scanRecord?.manufacturerSpecificData
        if (manufacturerData != null) {
            logd("发现制造商数据")
            // 这里可以添加特定的制造商数据检查逻辑
        }
        
        // 返回匹配结果：名称匹配 或 UUID匹配
        val isTarget = nameMatch || uuidMatch || advDataUuidMatch
        
        if (isTarget) {
            logd("确认为目标设备 - 名称匹配: $nameMatch, UUID匹配: $uuidMatch, 广播数据UUID匹配: $advDataUuidMatch")
        } else {
            logd("非目标设备 - 名称匹配: $nameMatch, UUID匹配: $uuidMatch, 广播数据UUID匹配: $advDataUuidMatch")
        }
        
        return isTarget
    }

    /**
     * 获取扫描记录中的服务UUID列表（用于调试）
     */
    private fun getServiceUuidsFromScanRecord(scanRecord: android.bluetooth.le.ScanRecord?): List<String> {
        return scanRecord?.serviceUuids?.map { it.uuid.toString() } ?: emptyList()
    }

    /**
     * 设置设备地址（用于快速重连）
     */
    fun setDeviceAddress(address: String) {
        mDeviceAddress = address
        logd("设置设备地址: $address")
    }
    
    /**
     * 获取当前设备地址
     */
    fun getDeviceAddress(): String {
        return mDeviceAddress
    }
    
    /**
     * 清除设备地址
     */
    fun clearDeviceAddress() {
        mDeviceAddress = ""
        logd("清除设备地址")
    }
    
    /**
     * 检查是否有保存的设备地址
     */
    fun hasDeviceAddress(): Boolean {
        return mDeviceAddress.isNotEmpty()
    }

    /**
     * 智能MTU协商策略
     * 优先尝试512字节，失败后降级到256字节，最后使用默认23字节
     */
    private fun requestMtuWithFallback(gatt: BluetoothGatt?) {
        if (gatt == null) {
            logd("GATT为空，无法请求MTU")
            return
        }
        
        if (mtuNegotiationAttempted) {
            logd("MTU协商已尝试过，跳过重复请求")
            return
        }
        
        mtuNegotiationAttempted = true
        mtuNegotiationFailed = false
        
        // 尝试请求理想MTU大小
        logd("=== 开始MTU协商 ===")
        logd("第一阶段：请求${MTU_OPTIMAL}字节MTU")
        val mtuRequested = gatt.requestMtu(MTU_OPTIMAL)
        logd("MTU请求状态: $mtuRequested, 请求大小: $MTU_OPTIMAL")
        
        if (!mtuRequested) {
            logd("MTU请求失败，将使用默认MTU大小")
            handleMtuNegotiationFailure(MTU_OPTIMAL, MTU_DEFAULT)
        }
    }
    
    /**
     * 处理MTU协商结果
     */
    private fun handleMtuNegotiationResult(gatt: BluetoothGatt?, mtu: Int, status: Int) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            logd("=== MTU协商成功 ===")
            logd("协商后的MTU大小: $mtu 字节")
            currentMtu = mtu
            
            if (mtu >= MTU_OPTIMAL) {
                logd("获得理想的MTU大小，数据传输效率最佳")
            } else if (mtu >= MTU_GOOD) {
                logd("获得较好的MTU大小，数据传输效率良好")
            } else if (mtu >= MTU_ACCEPTABLE) {
                logd("获得一般的MTU大小，数据传输效率一般")
            } else {
                logd("MTU大小较小，数据传输可能较慢")
            }
            
            // 通知回调
            mBLEVTBLECallback.onMtuNegotiationSuccess(mtu)
            
        } else {
            logd("=== MTU协商失败 ===")
            logd("状态码: $status")
            logd("尝试降级到${MTU_FALLBACK}字节MTU")
            
            // 尝试降级到MTU_FALLBACK字节
            if (!mtuNegotiationFailed) {
                mtuNegotiationFailed = true
                val fallbackRequested = gatt?.requestMtu(MTU_FALLBACK)
                logd("降级MTU请求状态: $fallbackRequested, 请求大小: $MTU_FALLBACK")
                
                if (fallbackRequested == false) {
                    logd("降级MTU请求也失败，使用默认MTU")
                    handleMtuNegotiationFailure(MTU_FALLBACK, MTU_DEFAULT)
                }
            } else {
                // 降级也失败了，使用默认MTU
                handleMtuNegotiationFailure(MTU_FALLBACK, MTU_DEFAULT)
            }
        }
    }
    
    /**
     * 处理MTU协商失败
     */
    private fun handleMtuNegotiationFailure(requestedMtu: Int, actualMtu: Int) {
        logd("=== MTU协商最终失败 ===")
        logd("请求的MTU大小: $requestedMtu 字节")
        logd("将使用默认MTU大小: $actualMtu 字节")
        logd("数据传输可能会较慢，但功能不受影响")
        
        currentMtu = actualMtu
        mtuNegotiationFailed = true
        
        // 通知回调
        mBLEVTBLECallback.onMtuNegotiationFailed(requestedMtu, actualMtu)
    }
    
    /**
     * 获取当前MTU大小
     */
    fun getCurrentMtu(): Int {
        return currentMtu
    }
    
    /**
     * 检查MTU协商是否成功
     */
    fun isMtuNegotiationSuccessful(): Boolean {
        return mtuNegotiationAttempted && !mtuNegotiationFailed
    }
    
    /**
     * 重置MTU协商状态（用于重新连接时）
     */
    private fun resetMtuNegotiationState() {
        mtuNegotiationAttempted = false
        mtuNegotiationFailed = false
        currentMtu = MTU_DEFAULT
        logd("MTU协商状态已重置")
    }

    /**
     * 开始PHY协商
     */
    private fun startPhyNegotiation(gatt: BluetoothGatt?) {
        logd("=== 开始PHY协商流程 ===")
        
        // 检查设备PHY支持情况
        val supportedPhys = phyManager.getSupportedPhys()
        logd("设备支持的PHY: $supportedPhys")
        
        // 获取最优PHY配置
        val optimalPhy = phyManager.getOptimalPhy()
        logd("选择的最优PHY: ${phyManager.getPhyDescription(optimalPhy)}")
        logd("PHY性能描述: ${phyManager.getPhyPerformanceDescription(optimalPhy)}")
        
        // 开始PHY协商，VTPhyManager内部处理所有重试和降级逻辑
        phyManager.startPhyNegotiation(gatt, mBLEVTBLECallback)
    }
    

    
    /**
     * 读取当前PHY
     */
    fun readCurrentPhy() {
        if (mGatt != null) {
            logd("=== 读取当前PHY ===")
            phyManager.readPhy(mGatt, mBLEVTBLECallback)
        } else {
            logd("GATT未连接，无法读取PHY")
        }
    }
    
    /**
     * 获取当前TX PHY
     */
    fun getCurrentTxPhy(): Int {
        return phyManager.getCurrentTxPhy()
    }
    
    /**
     * 获取当前RX PHY
     */
    fun getCurrentRxPhy(): Int {
        return phyManager.getCurrentRxPhy()
    }
    
    /**
     * 检查PHY协商是否成功
     */
    fun isPhyNegotiationSuccessful(): Boolean {
        return phyManager.isPhyNegotiationSuccessful()
    }
    
    /**
     * 检查设备是否支持2M PHY
     */
    fun isLe2MPhySupported(): Boolean {
        return phyManager.isLe2MPhySupported()
    }
    
    /**
     * 检查设备是否支持Coded PHY
     */
    fun isLeCodedPhySupported(): Boolean {
        return phyManager.isLeCodedPhySupported()
    }
    
    /**
     * 获取设备支持的PHY列表
     */
    fun getSupportedPhys(): List<Int> {
        return phyManager.getSupportedPhys()
    }
    
    /**
     * 获取PHY描述信息
     */
    fun getPhyDescription(phy: Int): String {
        return phyManager.getPhyDescription(phy)
    }
    
    /**
     * 获取PHY性能描述
     */
    fun getPhyPerformanceDescription(phy: Int): String {
        return phyManager.getPhyPerformanceDescription(phy)
    }
    
    /**
     * 获取连接质量信息
     */
    fun getConnectionQualityInfo(): String {
        val mtuInfo = "MTU: ${getCurrentMtu()}字节"
        val txPhyInfo = "TX PHY: ${getPhyDescription(getCurrentTxPhy())}"
        val rxPhyInfo = "RX PHY: ${getPhyDescription(getCurrentRxPhy())}"
        val mtuStatus = if (isMtuNegotiationSuccessful()) "✓" else "✗"
        val phyStatus = if (isPhyNegotiationSuccessful()) "✓" else "✗"
        
        return "连接质量信息:\n" +
               "  $mtuInfo $mtuStatus\n" +
               "  $txPhyInfo $phyStatus\n" +
               "  $rxPhyInfo $phyStatus"
    }
    
    // ==================== 配对管理相关方法 ====================
    
    /**
     * 检查并请求配对
     */
    private fun checkAndRequestBonding(device: BluetoothDevice) {
        logd("=== 检查设备配对需求 ===")
        logd("设备名称: ${device.name ?: "未知"}")
        logd("设备地址: ${device.address}")
        logd("当前配对状态: ${getBondStateDescription(device.bondState)}")
        
        // 使用DeviceManager检查是否需要配对
        if (DeviceManager.shouldBondDevice(device)) {
            logd("设备需要配对，开始配对流程")
            requestBonding(device)
        } else {
            logd("设备暂不需要配对")
        }
    }
    
    /**
     * 请求设备配对
     */
    private fun requestBonding(device: BluetoothDevice) {
        if (isBonding) {
            logd("配对已在进行中，忽略重复请求")
            return
        }
        
        if (device.bondState == BluetoothDevice.BOND_BONDED) {
            logd("设备已配对，无需再次配对")
            return
        }
        
        logd("=== 开始设备配对 ===")
        logd("设备名称: ${device.name ?: "未知"}")
        logd("设备地址: ${device.address}")
        
        isBonding = true
        bondingDevice = device
        
        // 创建配对
        val bondResult = device.createBond()
        logd("配对请求结果: $bondResult")
        
        if (!bondResult) {
            logd("配对请求失败")
            isBonding = false
            bondingDevice = null
        }
    }
    
    /**
     * 处理配对状态变化
     */
    private fun handleBondStateChange(device: BluetoothDevice?, bondState: Int) {
        if (device == null) {
            logd("配对状态变化：设备为空")
            return
        }
        
        logd("=== 配对状态变化 ===")
        logd("设备名称: ${device.name ?: "未知"}")
        logd("设备地址: ${device.address}")
        logd("新配对状态: ${getBondStateDescription(bondState)}")
        
        when (bondState) {
            BluetoothDevice.BOND_NONE -> {
                logd("设备配对状态：未配对")
                if (isBonding) {
                    logd("配对失败或取消")
                    isBonding = false
                    bondingDevice = null
                }
            }
            
            BluetoothDevice.BOND_BONDING -> {
                logd("设备配对状态：配对中")
                isBonding = true
                bondingDevice = device
            }
            
            BluetoothDevice.BOND_BONDED -> {
                logd("设备配对状态：已配对")
                logd("=== 配对成功 ===")
                isBonding = false
                bondingDevice = null
                
                // 保存配对成功的设备信息
                DeviceManager.saveConnectedDevice(device)
                
                // 通知回调
                mBLEVTBLECallback.onBondingSuccess(device.name, device.address)
            }
        }
    }
    
    /**
     * 获取配对状态描述
     */
    private fun getBondStateDescription(bondState: Int): String {
        return when (bondState) {
            BluetoothDevice.BOND_NONE -> "未配对"
            BluetoothDevice.BOND_BONDING -> "配对中"
            BluetoothDevice.BOND_BONDED -> "已配对"
            else -> "未知状态"
        }
    }
    
    /**
     * 检查设备是否已配对
     */
    fun isDeviceBonded(device: BluetoothDevice): Boolean {
        return device.bondState == BluetoothDevice.BOND_BONDED
    }
    
    /**
     * 检查当前设备是否已配对
     */
    fun isCurrentDeviceBonded(): Boolean {
        val deviceAddress = getDeviceAddress()
        if (deviceAddress.isNotEmpty()) {
            val bluetoothAdapter = VTBluetoothUtil.getBluetoothAdapter(mContext)
            bluetoothAdapter?.let { adapter ->
                val device = adapter.getRemoteDevice(deviceAddress)
                return isDeviceBonded(device)
            }
        }
        return false
    }
    
    /**
     * 获取配对状态信息
     */
    fun getBondingStatusInfo(): String {
        val deviceAddress = getDeviceAddress()
        if (deviceAddress.isNotEmpty()) {
            val bluetoothAdapter = VTBluetoothUtil.getBluetoothAdapter(mContext)
            bluetoothAdapter?.let { adapter ->
                val device = adapter.getRemoteDevice(deviceAddress)
                val bondState = device.bondState
                val bondDescription = getBondStateDescription(bondState)
                val isCurrentlyBonding = isBonding && bondingDevice?.address == deviceAddress
                
                return "配对状态信息:\n" +
                       "  设备: ${device.name ?: "未知"}\n" +
                       "  地址: $deviceAddress\n" +
                       "  状态: $bondDescription\n" +
                       "  配对中: ${if (isCurrentlyBonding) "是" else "否"}"
            }
        }
        return "配对状态信息: 无设备信息"
    }
}