package com.vincent.library.ble.logic

import VTBLECallback
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattDescriptor
import android.os.Handler
import android.os.Looper
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.ParcelUuid
import com.vincent.library.base.util.VTCoroutineUtil
import com.vincent.library.base.util.logd
import com.vincent.library.ble.util.VTBluetoothUtil.getBluetoothAdapter
import com.vincent.library.ble.config.CONNECTION_MONITOR_INTERVAL
import com.vincent.library.ble.config.CONNECTION_TIMEOUT
import com.vincent.library.ble.config.MAX_RETRY_COUNT
import com.vincent.library.ble.config.MTU_ACCEPTABLE
import com.vincent.library.ble.config.MTU_DEFAULT
import com.vincent.library.ble.config.MTU_FALLBACK
import com.vincent.library.ble.config.MTU_GOOD
import com.vincent.library.ble.config.MTU_OPTIMAL
import com.vincent.library.ble.config.RETRY_DELAY
import com.vincent.library.ble.config.SCAN_TIMEOUT
import com.vincent.library.ble.config.SERVICE_DISCOVERY_TIMEOUT
import kotlinx.coroutines.Job
import com.vincent.library.ble.model.VTBLEDevice
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

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



@SuppressLint("MissingPermission")
class VTBLEController(
    private val context: Context,
    private val bleDeviceName:String,
    private val bleDeviceServiceID: String,
    private val bleDeviceCharacteristicID: String,
    private val bleNotificationCharacteristicID:String,
    private val deviceInfoCharacteristicID: String? = null
) {
    // 通用蓝牙设备信息（供上层读取）
    private val _device: MutableStateFlow<VTBLEDevice> = MutableStateFlow(VTBLEDevice.createDefault())
    val device: StateFlow<VTBLEDevice> = _device.asStateFlow()

    private fun updateDeviceBasic(name: String? = null, address: String? = null) {
        val cur = _device.value
        _device.value = cur.copyWith(
            name = name ?: cur.name,
            macAddress = address ?: cur.macAddress,
            connectionState = _connectionState.value,
            isBonded = isCurrentDeviceBonded(),
            mtu = currentMtu,
            txPhy = getCurrentTxPhy(),
            rxPhy = getCurrentRxPhy(),
            lastConnectedTime = if (_connectionState.value == BLEConnectionState.CONNECTED || _connectionState.value == BLEConnectionState.READY) System.currentTimeMillis() else cur.lastConnectedTime
        )
    }

    companion object {
        private const val SCOPE_NAME = "VTBLEController"
        private const val TAG = "VTBLEController"
    }

    private val bluetoothAdapter by lazy { getBluetoothAdapter(context) }
    
    // 连接状态StateFlow
    private val _connectionState = MutableStateFlow(BLEConnectionState.DISCONNECTED)
    val connectionState: StateFlow<BLEConnectionState> = _connectionState.asStateFlow()
    
    private var bleGatt: BluetoothGatt? = null
    private var bleCharacteristic: BluetoothGattCharacteristic? = null
    private var brightnessCharacteristic: BluetoothGattCharacteristic? = null
    private var deviceInfoCharacteristic: BluetoothGattCharacteristic? = null
    private var pendingReadCallback: ((String) -> Unit)? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var bleVTBLECallback: VTBLECallback = VTDefaultBLECallback()
    private var bleScanCallback: ScanCallback? = null

    // 协程
    private var scanTimeoutJob: Job? = null
    private var connectionTimeoutJob: Job? = null
    private var serviceDiscoveryTimeoutJob: Job? = null
    private var connectionMonitorJob: Job? = null
    private var retryJob: Job? = null

    // 设备地址管理 - 用于快速重连
    private var bleDeviceAddress: String = ""

    // 连接状态管理
    private var isConnecting = false
    private var isScanning = false

    // 重连机制
    private var retryCount = 0

    // MTU协商相关
    private var currentMtu = MTU_DEFAULT // 默认MTU大小
    private var mtuNegotiationAttempted = false
    private var mtuNegotiationFailed = false

    // PHY管理
    private val phyManager = VTPhyManager(context)

    // 配对管理
    private var isBonding = false
    private var bondingDevice: BluetoothDevice? = null

    // 配对状态广播接收器注册状态
    private var isBondStateReceiverRegistered = false

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
        logd(TAG, "连接失败原因: $reason")
        updateConnectionState(BLEConnectionState.ERROR)
        isConnecting = false

        // 安全注销配对状态广播接收器
        unregisterBondStateReceiver()

        if (retryCount < MAX_RETRY_COUNT) {
            retryCount++
            logd(TAG, "准备第 $retryCount 次重试连接...")
            retryJob = VTCoroutineUtil.delayInScope(SCOPE_NAME, RETRY_DELAY) {
                if (_connectionState.value == BLEConnectionState.ERROR) {
                    logd(TAG, "开始重试连接...")
                    retryConnection()
                }
            }
        } else {
            logd(TAG, "重试次数已达上限，连接失败")
            retryCount = 0
            bleVTBLECallback.onScanFailed()
        }
    }

    /**
     * 安全注销配对状态广播接收器
     */
    private fun unregisterBondStateReceiver() {
        try {
            if (isBondStateReceiverRegistered) {
                context.unregisterReceiver(bondStateReceiver)
                isBondStateReceiverRegistered = false
                logd(TAG, "成功注销配对状态广播接收器")
            }
        } catch (e: Exception) {
            logd(TAG, "注销广播接收器异常: ${e.message}")
        }
    }

    /**
     * 重试连接
     */
    private fun retryConnection() {
        if (_connectionState.value == BLEConnectionState.ERROR) {
            logd(TAG, "=== 开始重试连接 ===")
            // 重置连接状态
            updateConnectionState(BLEConnectionState.DISCONNECTED)
            isConnecting = false
            // 清理之前的连接资源
            cleanupConnection()
            // 使用扫描模式重试连接
            scan(bleVTBLECallback)
        }
    }

    /**
     * 清理连接资源
     */
    private fun cleanupConnection() {
        connectionTimeoutJob?.cancel()
        serviceDiscoveryTimeoutJob?.cancel()
        scanTimeoutJob?.cancel()
        retryJob?.cancel()
        phyManager.resetPhyNegotiationState()
        unregisterBondStateReceiver()
        logd(TAG, "连接资源已清理")
    }

    /**
     * 开始连接监控
     */
    private fun startConnectionMonitor() {
        stopConnectionMonitor()
        connectionMonitorJob = VTCoroutineUtil.timerInScope(SCOPE_NAME, CONNECTION_MONITOR_INTERVAL) {
            val currentState = _connectionState.value
            if (currentState == BLEConnectionState.CONNECTED ||
                currentState == BLEConnectionState.READY) {
                // 检查GATT连接状态
                val gatt = bleGatt
                if (gatt != null) {
                    try {
                        val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
                        val device = gatt.device // 使用已连接的设备
                        val connectionState = bluetoothManager.getConnectionState(device, BluetoothProfile.GATT)

                        if (connectionState == BluetoothProfile.STATE_DISCONNECTED) {
                            logd(TAG, "=== 连接监控检测到连接断开 ===")
                            handleUnexpectedDisconnection()
                        }
                    } catch (e: Exception) {
                        logd(TAG, "连接监控检查异常: ${e.message}")
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
        logd(TAG, "=== 处理意外断开连接 ===")
        updateConnectionState(BLEConnectionState.DISCONNECTED)
        stopConnectionMonitor()

        // 注销配对状态广播接收器
        try {
            context.unregisterReceiver(bondStateReceiver)
        } catch (e: Exception) {
            logd(TAG, "注销广播接收器异常: ${e.message}")
        }

        bleVTBLECallback.onDisConnected()
    }

    /**
     * 更新连接状态
     */
    private fun updateConnectionState(newState: BLEConnectionState) {
        val oldState = _connectionState.value
        _connectionState.value = newState
        logd(TAG, "连接状态变化: $oldState -> $newState")
    // 同步到通用设备镜像
    updateDeviceBasic()
    }

    /**
     * 扫描设备
     */
    fun scan(callback: VTBLECallback) {
        if (isScanning) {
            logd(TAG, "扫描已在进行中，忽略重复请求")
            return
        }

        logd(TAG, "=== VTBLEController: 扫描模式 ===")
        bleVTBLECallback = callback
        retryCount = 0

        val adapter = bluetoothAdapter
        if (adapter == null) {
            logd(TAG, "错误: 设备不支持蓝牙")
            callback.onScanFailed()
            return
        }

        if (!adapter.isEnabled) {
            logd(TAG, "错误: 蓝牙未启用")
            callback.onScanFailed()
            return
        }

        val bluetoothLeScanner = adapter.bluetoothLeScanner
        if (bluetoothLeScanner == null) {
            logd(TAG, "错误: 蓝牙LE扫描器不可用")
            callback.onScanFailed()
            return
        }

        logd(TAG, "开始扫描周围的BLE设备...")
        logd(TAG, "目标设备名称: $bleDeviceName")
        logd(TAG, "目标服务UUID: $bleDeviceServiceID")
        logd(TAG, "扫描超时时间: ${SCAN_TIMEOUT}ms")

        // 创建扫描过滤器，优先使用Service UUID过滤
        val scanFilters = mutableListOf<ScanFilter>()

        // 添加Service UUID过滤器
        try {
            val serviceUuid = UUID.fromString(bleDeviceServiceID)
            val serviceFilter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(serviceUuid))
                .build()
            scanFilters.add(serviceFilter)
            logd(TAG, "添加服务UUID过滤器: $bleDeviceServiceID")
        } catch (e: IllegalArgumentException) {
            logd(TAG, "服务UUID格式无效，跳过UUID过滤器: $bleDeviceServiceID")
        }

        // 如果知道设备名称，添加名称过滤器作为备选
        if (bleDeviceName.isNotEmpty()) {
            val nameFilter = ScanFilter.Builder()
                .setDeviceName(bleDeviceName)
                .build()
            scanFilters.add(nameFilter)
            logd(TAG, "添加设备名称过滤器: $bleDeviceName")
        }

        // 创建扫描设置，优化扫描参数
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // 低延迟模式
            .setReportDelay(0) // 立即报告结果
            .build()

        logd(TAG, "扫描过滤器数量: ${scanFilters.size}")
        logd(TAG, "扫描模式: 低延迟模式")

        bleScanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                super.onScanResult(callbackType, result)
                result?.let { scanResult ->
                    val deviceName = scanResult.device.name ?: "Unknown"
                    val deviceAddress = scanResult.device.address
                    val rssi = scanResult.rssi
                    val scanRecord = scanResult.scanRecord

                    logd(TAG, "扫描发现设备: $deviceName ($deviceAddress) RSSI: ${rssi}dBm")

                    // 检查设备是否为目标设备
                    if (isTargetDevice(deviceName, scanRecord)) {
                        logd(TAG, "找到目标设备，停止扫描")
                        logd(TAG, "匹配的设备名称: $deviceName")
                        logd(TAG, "匹配的设备地址: $deviceAddress")
                        logd(TAG, "信号强度: ${rssi}dBm")

                        // 记录信号强度信息用于连接质量评估
                        if (rssi > -70) {
                            logd(TAG, "信号强度良好，适合连接")
                        } else if (rssi > -80) {
                            logd(TAG, "信号强度一般，可能影响连接稳定性")
                        } else {
                            logd(TAG, "信号强度较弱，建议靠近设备")
                        }

                        stopScan()
                        // 找到目标设备后立即连接
                        connectDevice(scanResult.device)
                    } else {
                        // 记录其他发现的设备（用于调试）
                        if (deviceName.isNotEmpty() && deviceName != "Unknown") {
                            logd(TAG, "发现其他设备: $deviceName ($deviceAddress) RSSI: ${rssi}dBm")
                        }
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                logd(TAG, "扫描失败，错误代码: $errorCode")
                stopScan()
                handleConnectionFailure("扫描失败，错误代码: $errorCode")
            }
        }

        // 开始扫描并设置超时
        isScanning = true
        bluetoothLeScanner.startScan(scanFilters, scanSettings, bleScanCallback)

        // 使用协程设置扫描超时
        scanTimeoutJob = VTCoroutineUtil.delayInScope(SCOPE_NAME, SCAN_TIMEOUT) {
            logd(TAG, "=== 扫描超时 ===")
            logd(TAG, "扫描时间超过 ${SCAN_TIMEOUT}ms，停止扫描")
            stopScan()
            handleConnectionFailure("扫描超时")
        }

        logd(TAG, "BLE扫描已启动，等待设备发现...")
    }

    /**
     * 智能连接设备
     * 优先使用保存的设备地址进行快速连接，失败则回退到扫描模式
     */
    fun connect(callback: VTBLECallback) {
        if (isConnecting) {
            logd(TAG, "连接已在进行中，忽略重复请求")
            return
        }

        // 检查当前连接状态，避免重复连接
        val currentState = _connectionState.value
        if (currentState == BLEConnectionState.CONNECTED || currentState == BLEConnectionState.READY) {
            logd(TAG, "设备已连接，忽略连接请求")
            return
        }

        bleVTBLECallback = callback
        retryCount = 0

        // 检查是否有保存的设备地址
        if (hasDeviceAddress()) {
            logd(TAG, "=== 尝试快速重连模式 ===")
            logd(TAG, "使用保存的设备地址: $bleDeviceAddress")
            connectToDeviceByAddress(bleDeviceAddress, callback)
        } else {
            logd(TAG, "=== 使用扫描模式连接 ===")
            logd(TAG, "未找到保存的设备地址，开始扫描")
            scan(callback)
        }
    }

    /**
     * 直接连接指定设备（通过设备地址）
     */
    fun connectToDeviceByAddress(deviceAddress: String, callback: VTBLECallback) {
        if (isConnecting) {
            logd(TAG, "连接已在进行中，忽略重复请求")
            return
        }

        logd(TAG, "=== VTBLEController: 直接连接模式 ===")
        bleVTBLECallback = callback
        retryCount = 0

        val adapter = bluetoothAdapter
        if (adapter == null) {
            logd(TAG, "错误: 设备不支持蓝牙")
            callback.onScanFailed()
            return
        }

        if (!adapter.isEnabled) {
            logd(TAG, "错误: 蓝牙未启用")
            callback.onScanFailed()
            return
        }

        // 如果提供了设备地址，尝试直接连接
        if (deviceAddress.isNotEmpty()) {
            try {
                val device = adapter.getRemoteDevice(deviceAddress)
                logd(TAG, "获取到远程设备对象")
                logd(TAG, "目标设备地址: ${device.address}")
                logd(TAG, "开始直接连接流程...")
                connectDevice(device)
            } catch (e: IllegalArgumentException) {
                logd(TAG, "错误: 无效的设备地址: $deviceAddress")
                logd(TAG, "将使用扫描模式查找设备...")
                // 如果地址无效，回退到扫描模式
                scan(callback)
            } catch (e: Exception) {
                logd(TAG, "错误: 连接设备时发生异常: ${e.message}")
                logd(TAG, "将使用扫描模式查找设备...")
                // 发生异常时，回退到扫描模式
                scan(callback)
            }
        } else {
            logd(TAG, "未提供设备地址，使用扫描模式查找设备...")
            // 没有地址时，使用扫描模式
            scan(callback)
        }
    }

    private fun stopScan() {
        logd(TAG, "=== 停止扫描 ===")
        scanTimeoutJob?.cancel()
        bleScanCallback?.let { callback ->
            val bluetoothAdapter = getBluetoothAdapter(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(callback)
            }
            bleScanCallback = null
        }
        isScanning = false
        logd(TAG, "BLE扫描已停止")
    }

    /**
     * 连接设备
     */
    private fun connectDevice(device: BluetoothDevice) {
        if (isConnecting) {
            logd(TAG, "连接已在进行中，忽略重复请求")
            return
        }

        logd(TAG, "=== 开始连接设备 ===")
        logd(TAG, "设备名称: ${device.name ?: "未知"}")
        logd(TAG, "设备地址: ${device.address}")
        logd(TAG, "连接超时时间: ${CONNECTION_TIMEOUT}ms")

        // 重置MTU协商状态
        resetMtuNegotiationState()

        // 重置PHY协商状态
        phyManager.resetPhyNegotiationState()

        isConnecting = true
        updateConnectionState(BLEConnectionState.CONNECTING)

        // 注册配对状态广播接收器
        if (!isBondStateReceiverRegistered) {
            val bondFilter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            context.registerReceiver(bondStateReceiver, bondFilter)
            isBondStateReceiverRegistered = true
            logd(TAG, "成功注册配对状态广播接收器")
        }

        connectionTimeoutJob = VTCoroutineUtil.delayInScope(SCOPE_NAME, CONNECTION_TIMEOUT) {
            logd(TAG, "=== 连接超时 ===")
            logd(TAG, "连接时间超过 ${CONNECTION_TIMEOUT}ms，断开连接")
            disconnect()
            handleConnectionFailure("连接超时")
        }


        logd(TAG, "正在连接设备...")
        // 使用优化的连接参数
        val connectOptions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Android 8.0+ 支持连接参数优化
            BluetoothDevice.TRANSPORT_LE
        } else {
            BluetoothDevice.TRANSPORT_AUTO
        }

        bleGatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                super.onConnectionStateChange(gatt, status, newState)

                logd(TAG, "连接状态变化: $newState, 状态码: $status")

                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        logd(TAG, "=== 设备连接成功 ===")
                        connectionTimeoutJob?.cancel()
                        bleGatt = gatt

                        // 保存设备地址用于快速重连
                        if (device.address.isNotEmpty()) {
                            setDeviceAddress(device.address)
                            logd(TAG, "已保存设备地址用于快速重连: ${device.address}")
                        }

                        // 检查是否需要配对
                        checkAndRequestBonding(device)

                        // 更新设备基本信息
                        updateDeviceBasic(device.name, device.address)
                        updateConnectionState(BLEConnectionState.CONNECTED)
                        bleVTBLECallback.onConnected(device.name, device.address)

                        // 请求MTU大小 - 使用智能协商策略，PHY协商将在MTU协商完成后自动进行
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            requestMtuWithFallback(gatt)
                        } else {
                            // Android 5.0以下版本不支持MTU协商，直接开始PHY协商
                            startPhyNegotiation(gatt)
                        }

                        startConnectionMonitor() // 连接成功后启动监控
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        logd(TAG, "=== 设备连接断开 ===")
                        connectionTimeoutJob?.cancel()
                        serviceDiscoveryTimeoutJob?.cancel()
                        bleGatt = null
                        bleCharacteristic = null
                        updateConnectionState(BLEConnectionState.DISCONNECTED)
                        isConnecting = false
                        unregisterBondStateReceiver() // 断开连接时注销广播接收器
                        bleVTBLECallback.onDisConnected()
                        handleUnexpectedDisconnection() // 断开连接时也处理意外断开
                    }
                    BluetoothProfile.STATE_CONNECTING -> {
                        logd(TAG, "正在连接设备...")
                        updateConnectionState(BLEConnectionState.CONNECTING)
                        bleVTBLECallback.onConnecting()
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
                        if (service.uuid.toString() == bleDeviceServiceID) {
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

                    if (characteristicUUID == bleNotificationCharacteristicID) {
                        // 通知仅负责转发亮度数值给上层
                        val notiValue = stringValue.toIntOrNull()
                        if (notiValue != null) {
                            bleVTBLECallback.onNotifyValueReceived(notiValue)
                        } else {
                            logd("通知值格式错误: $stringValue")
                        }
                    }
                }
            }
            override fun onCharacteristicRead(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int
            ) {
                super.onCharacteristicRead(gatt, characteristic, status)
                if (gatt == null || characteristic == null) return
                if (status != BluetoothGatt.GATT_SUCCESS) return

                val valueBytes = characteristic.value ?: return
                val str = try { String(valueBytes) } catch (_: Exception) { "" }
                // 读取仅把原始字符串交给上层：优先走主动回调，其次兼容一次性回调
                bleVTBLECallback.onCharacteristicReadValue(str)
                pendingReadCallback?.invoke(str)
                pendingReadCallback = null
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

                bleVTBLECallback.writeDataCallback(status == BluetoothGatt.GATT_SUCCESS)
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
        var foundNotifyCharacteristic = false

        service?.characteristics?.forEach { characteristic ->
            val characUUID = characteristic.uuid.toString()
            logd("检查特征值: $characUUID")

            if (characUUID == bleDeviceCharacteristicID) {
                logd("=== 找到目标特征值 ===")
                logd("特征值UUID: $characUUID")
                bleCharacteristic = characteristic
                foundTargetCharacteristic = true

                // 启用通知
                val success = gatt.setCharacteristicNotification(characteristic, true)
                logd("特征值通知启用状态: $success")

                if (success) {
                    logd("=== 设备连接流程完成 ===")
                    updateConnectionState(BLEConnectionState.READY)
                    isConnecting = false
                    bleVTBLECallback.onCheckCharacteristicSuccess()
                } else {
                    logd("错误: 启用特征值通知失败")
                    handleConnectionFailure("启用特征值通知失败")
                }
                return@forEach
            }


            if (characUUID == bleNotificationCharacteristicID) {
                logd("=== 找到通知特征值 ===")
                logd("通知特征值UUID: $characUUID")
                foundNotifyCharacteristic = true

                val success = gatt.setCharacteristicNotification(characteristic, true)
                logd("通知特征值通知启用状态: $success")

                if (!success) {
                    logd("警告: 启用通知特征值通知失败")
                }

                // 写入CCCD以真正启用通知
                try {
                    val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                    val cccd = characteristic.getDescriptor(cccdUuid)
                    if (cccd != null) {
                        cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        val writeCccd = gatt.writeDescriptor(cccd)
                        logd("写入CCCD以启用通知: $writeCccd")
                    } else {
                        logd("未找到CCCD描述符，跳过写入")
                    }
                } catch (e: Exception) {
                    logd("写入CCCD失败: ${e.message}")
                }

                // 记录特征以便稍后读取
                brightnessCharacteristic = characteristic
                // 避免与并发的写操作冲突，延时再读一次当前值
                mainHandler.postDelayed({
                    try {
                        val target = brightnessCharacteristic
                        val readStarted = if (target != null) gatt.readCharacteristic(target) else false
                        logd("延时发起亮度特征读取: $readStarted")
                    } catch (e: Exception) {
                        logd("延时读取亮度特征失败: ${e.message}")
                    }
                }, 300)
            }

            // 记录设备信息特征，便于上层查询
            if (deviceInfoCharacteristicID != null && characUUID == deviceInfoCharacteristicID) {
                deviceInfoCharacteristic = characteristic
                logd("发现设备信息特征值: $characUUID")
            }
        }

        // 如果没有找到目标特征值
        if (!foundTargetCharacteristic) {
            logd("错误: 未找到目标特征值: $bleDeviceCharacteristicID")
            handleConnectionFailure("未找到目标特征值")
        }


        if (foundNotifyCharacteristic) {
            logd("通知特征值查找成功")
        } else {
            logd("警告: 未找到通知特征值")
        }
    }

    /**
     * 发送文本数据
     */
    fun sendText(serviceUUID: String, characteristicUUID: String, text: String) {
        if (_connectionState.value != BLEConnectionState.READY) {
            logd(TAG, "设备未准备就绪，当前状态: ${_connectionState.value}，无法发送文本")
            bleVTBLECallback.writeDataCallback(false)
            return
        }

        if (bleGatt == null) {
            logd("GATT not connected, cannot send text")
            bleVTBLECallback.writeDataCallback(false)
            return
        }

        if (text.isBlank()) {
            logd("Text is empty, cannot send")
            bleVTBLECallback.writeDataCallback(false)
            return
        }

        logd("发送文本:$text")
        writeDataToCharacteristicText(bleGatt!!, serviceUUID, characteristicUUID, text)
    }

    /**
     * 读取指定服务/特征的字符串值（一次性回调）。
     */
    fun readCharacteristic(serviceUUID: String, characteristicUUID: String, callback: (String?) -> Unit): Boolean {
        if (_connectionState.value != BLEConnectionState.READY) {
            logd(TAG, "设备未准备就绪，无法读取特征值")
            callback(null)
            return false
        }
        val gatt = bleGatt ?: run {
            callback(null)
            return false
        }

        val service = gatt.getService(java.util.UUID.fromString(serviceUUID))
        val characteristic = service?.getCharacteristic(java.util.UUID.fromString(characteristicUUID))
        return if (characteristic != null) {
            pendingReadCallback = callback
            val started = gatt.readCharacteristic(characteristic)
            logd("发起特征读取($characteristicUUID): $started")
            started
        } else {
            callback(null)
            false
        }
    }

    /**
     * 发送字节数据
     */
    fun sendBytes(serviceUUID: String, characteristicUUID: String, bytes: ByteArray) {
        if (_connectionState.value != BLEConnectionState.READY) {
            logd(TAG, "设备未准备就绪，当前状态: ${_connectionState.value}，无法发送字节数据")
            bleVTBLECallback.writeDataCallback(false)
            return
        }

        if (bleGatt == null) {
            logd("GATT not connected, cannot send bytes")
            bleVTBLECallback.writeDataCallback(false)
            return
        }

        if (bytes.isEmpty()) {
            logd("Bytes array is empty, cannot send")
            bleVTBLECallback.writeDataCallback(false)
            return
        }

        writeDataToCharacteristicBytes(bleGatt!!, serviceUUID, characteristicUUID, bytes)
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
        if (_connectionState.value != BLEConnectionState.READY) {
            logd(TAG, "设备未准备就绪，当前状态: ${_connectionState.value}，无法发送字节数据")
            return false
        }

        if (bleGatt == null) {
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

        val result = writeDataToCharacteristicBytesSync(bleGatt!!, serviceUUID, characteristicUUID, bytes)

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
            bleVTBLECallback.writeDataCallback(true)
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
            bleVTBLECallback.writeDataCallback(false)
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
            VTCoroutineUtil.delayInScope(SCOPE_NAME, 20) {
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
        logd("写入文本:$text")

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
                bleVTBLECallback.writeDataCallback(false)
                return
            }

            val characteristic = service.characteristics.find { it.uuid.toString() == characteristicUUID }
            if (characteristic == null) {
                logd("Characteristic not found: $characteristicUUID")
                bleVTBLECallback.writeDataCallback(false)
                return
            }

            // 检查特征值是否支持写操作
            if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) == 0 &&
                (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) == 0) {
                logd("Characteristic does not support write operations: $characteristicUUID")
                bleVTBLECallback.writeDataCallback(false)
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
                    bleVTBLECallback.writeDataCallback(false)
                }
            }

        } catch (e: Exception) {
            logd("Error writing characteristic: ${e.message}")
            bleVTBLECallback.writeDataCallback(false)
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
        bleGatt?.let { gatt ->
            try {
                gatt.disconnect()
                gatt.close()
            } catch (e: Exception) {
                logd("断开GATT连接异常: ${e.message}")
            }
        }

        // 重置状态
        bleGatt = null
        bleCharacteristic = null
        isConnecting = false
        isScanning = false
        updateConnectionState(BLEConnectionState.DISCONNECTED)

        // 重置MTU协商状态
        resetMtuNegotiationState()

        // 重置PHY协商状态
        phyManager.resetPhyNegotiationState()

        // 注销配对状态广播接收器
        unregisterBondStateReceiver()

        logd("BLE连接已断开")
    }

    /**
     * 获取当前连接状态
     */
    fun getConnectionState(): BLEConnectionState {
        return _connectionState.value
    }

    /**
     * 检查是否已连接
     */
    fun isConnected(): Boolean {
        return _connectionState.value == BLEConnectionState.READY
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
        logd(TAG, "=== 强制重置BLE控制器状态 ===")
        disconnect()
        updateConnectionState(BLEConnectionState.DISCONNECTED)
        isConnecting = false
        isScanning = false
        retryCount = 0

        // 确保注销广播接收器
        try {
            context.unregisterReceiver(bondStateReceiver)
        } catch (e: Exception) {
            logd("强制重置时注销广播接收器异常: ${e.message}")
        }

        logd("BLE控制器状态已重置")
    }

    /**
     * 判断设备是否为目标设备
     * 优先检查Service UUID，其次检查设备名称
     */
    private fun isTargetDevice(deviceName: String?, scanRecord: ScanRecord?): Boolean {
        // 1. 检查设备名称匹配
        val nameMatch = deviceName == bleDeviceName
        if (nameMatch) {
            logd("设备名称匹配: $deviceName")
        }

        // 2. 检查Service UUID匹配
        val uuidMatch = scanRecord?.serviceUuids?.any { uuid ->
            uuid.uuid.toString().equals(bleDeviceServiceID, ignoreCase = true)
        } ?: false

        if (uuidMatch) {
            logd("服务UUID匹配: ${scanRecord?.serviceUuids}")
        }

        // 3. 检查广播数据中的Service UUID
        val advDataUuidMatch = scanRecord?.serviceData?.keys?.any { uuid ->
            uuid.toString().equals(bleDeviceServiceID, ignoreCase = true)
        } ?: false

        if (advDataUuidMatch) {
            logd("广播数据中的服务UUID匹配")
        }

        // 4. 检查制造商数据（如果有特定的制造商数据）
        val manufacturerData = scanRecord?.manufacturerSpecificData
        if (manufacturerData != null) {
            logd("发现制造商数据")
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
    private fun getServiceUuidsFromScanRecord(scanRecord: ScanRecord?): List<String> {
        return scanRecord?.serviceUuids?.map { it.uuid.toString() } ?: emptyList()
    }

    /**
     * 设置设备地址（用于快速重连）
     */
    fun setDeviceAddress(address: String) {
        bleDeviceAddress = address
        logd("设置设备地址: $address")
    }

    /**
     * 获取当前设备地址
     */
    fun getDeviceAddress(): String {
        return bleDeviceAddress
    }

    /**
     * 清除设备地址
     */
    fun clearDeviceAddress() {
        bleDeviceAddress = ""
        logd("清除设备地址")
    }

    /**
     * 检查是否有保存的设备地址
     */
    fun hasDeviceAddress(): Boolean {
        return bleDeviceAddress.isNotEmpty()
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
        logd("MTU请求状态: $mtuRequested, 请求大小: ${MTU_OPTIMAL}")

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
            bleVTBLECallback.onMtuNegotiationSuccess(mtu)

            // MTU协商完成后，开始PHY协商
            logd("MTU协商完成，开始PHY协商")
            startPhyNegotiation(gatt)

        } else {
            logd("=== MTU协商失败 ===")
            logd("状态码: $status")
            logd("尝试降级到${MTU_FALLBACK}字节MTU")

            // 尝试降级到MTU_FALLBACK字节
            if (!mtuNegotiationFailed) {
                mtuNegotiationFailed = true
                val fallbackRequested = gatt?.requestMtu(MTU_FALLBACK)
                logd("降级MTU请求状态: $fallbackRequested, 请求大小: ${MTU_FALLBACK}")

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
        bleVTBLECallback.onMtuNegotiationFailed(requestedMtu, actualMtu)

        // MTU协商失败后，继续PHY协商
        logd("MTU协商失败，继续PHY协商")
        startPhyNegotiation(bleGatt)
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
        phyManager.startPhyNegotiation(gatt, object : VTBLECallback by bleVTBLECallback {
            override fun onPhyNegotiationSuccess(txPhy: Int, rxPhy: Int) {
                logd("=== VTBLEController: PHY协商成功 ===")
                logd("TX PHY: $txPhy, RX PHY: $rxPhy")
                // 继续服务发现流程
                startServiceDiscovery()
                bleVTBLECallback.onPhyNegotiationSuccess(txPhy, rxPhy)
            }

            override fun onPhyNegotiationFailed(requestedPhy: Int, actualPhy: Int) {
                logd("=== VTBLEController: PHY协商失败 ===")
                logd("请求的PHY: $requestedPhy")
                logd("实际的PHY: $actualPhy")
                logd("PHY协商失败，使用默认1M PHY，继续服务发现流程")
                // PHY协商失败不影响连接，继续服务发现
                startServiceDiscovery()
                bleVTBLECallback.onPhyNegotiationFailed(requestedPhy, actualPhy)
            }
        })
    }

    /**
     * 开始服务发现
     */
    private fun startServiceDiscovery() {
        if (bleGatt != null) {
            logd("开始发现服务...")
            updateConnectionState(BLEConnectionState.DISCOVERING)

            // 设置服务发现超时
            serviceDiscoveryTimeoutJob = VTCoroutineUtil.delayInScope(SCOPE_NAME,
                SERVICE_DISCOVERY_TIMEOUT
            ) {
                logd("=== 服务发现超时 ===")
                logd("服务发现时间超过 ${SERVICE_DISCOVERY_TIMEOUT}ms")
                disconnect()
                handleConnectionFailure("服务发现超时")
            }

            bleGatt?.discoverServices()
        } else {
            logd("GATT未连接，无法开始服务发现")
        }
    }



    /**
     * 读取当前PHY
     */
    fun readCurrentPhy() {
        if (bleGatt != null) {
            logd("=== 读取当前PHY ===")
            phyManager.readPhy(bleGatt, bleVTBLECallback)
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
        if (VTBLEDevicesManager.shouldBondDevice(device)) {
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
                VTBLEDevicesManager.saveConnectedDevice(device)

                // 通知回调
                bleVTBLECallback.onBondingSuccess(device.name, device.address)
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
            val bluetoothAdapter = getBluetoothAdapter(context)
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
            val bluetoothAdapter = getBluetoothAdapter(context)
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