package com.vincent.android.ledcontroller.logic

import VTBLECallback
import android.bluetooth.BluetoothDevice
import android.content.Context
import com.vincent.android.ledcontroller.constants.BleConstants
import com.vincent.android.ledcontroller.constants.BleConstants.BLE_BATCH_DELAY_MS
import com.vincent.android.ledcontroller.constants.BleConstants.BLE_FILE_SIZE_BYTES
import com.vincent.android.ledcontroller.constants.BleConstants.BLE_GIF_BATCH_SIZE
import com.vincent.android.ledcontroller.constants.BleConstants.BLE_HEADER_DELAY_MS
import com.vincent.android.ledcontroller.constants.BleConstants.BLE_HEADER_PROCESS_DELAY_MS
import com.vincent.android.ledcontroller.constants.BleConstants.BLE_HEADER_SIZE
import com.vincent.android.ledcontroller.constants.BleConstants.BLE_IMAGE_BATCH_DELAY_MS
import com.vincent.android.ledcontroller.constants.BleConstants.BLE_IMAGE_BATCH_SIZE
import com.vincent.android.ledcontroller.constants.BleConstants.BLE_IMAGE_HEADER_DELAY_MS
import com.vincent.android.ledcontroller.constants.BleConstants.BLE_IMAGE_PACKET_DELAY_MS
import com.vincent.android.ledcontroller.constants.BleConstants.BLE_MTU_KB_MULTIPLIER
import com.vincent.android.ledcontroller.constants.BleConstants.BLE_PACKET_DELAY_MS
import com.vincent.android.ledcontroller.constants.BleConstants.CONNECTION_RETRY_DELAY_MS
import com.vincent.android.ledcontroller.constants.BleConstants.GIF_DATA_RETRY_COUNT
import com.vincent.android.ledcontroller.constants.BleConstants.GIF_HEADER_RETRY_COUNT
import com.vincent.android.ledcontroller.constants.BleConstants.IMAGE_DATA_RETRY_COUNT
import com.vincent.android.ledcontroller.constants.BleConstants.IMAGE_HEADER_RETRY_COUNT
import com.vincent.android.ledcontroller.constants.BleConstants.MAX_CONNECTION_RETRY_COUNT
import com.vincent.android.ledcontroller.constants.LedConstants
import com.vincent.android.ledcontroller.constants.LedConstants.DEFAULT_FONT_SIZE
import com.vincent.android.ledcontroller.constants.LedConstants.DEFAULT_SCROLL_SPEED
import com.vincent.android.ledcontroller.constants.LedConstants.LED_DEFAULT_DISPLAY_TEXT
import com.vincent.android.ledcontroller.constants.ProtocolConstants
import com.vincent.android.ledcontroller.model.LEDDevice
import com.vincent.android.ledcontroller.utils.LedConfigManager
import com.vincent.library.base.util.VTCoroutineUtil
import com.vincent.library.base.util.logd
import com.vincent.library.ble.logic.BLEConnectionState
import com.vincent.library.ble.logic.VTBLEController
import com.vincent.library.ble.logic.VTBLEDevicesManager
import com.vincent.library.ble.util.VTBluetoothUtil
import com.vincent.library.ble.model.VTBLEDevice
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * LED控制器
 *
 */
class LEDController private constructor() {
    companion object {
        private const val TAG = "LEDController"
        private const val SCOPE_NAME = "LEDController"
        
        @Volatile
        private var INSTANCE: LEDController? = null

        fun getInstance(): LEDController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LEDController().also { INSTANCE = it }
            }
        }
    }

    private lateinit var context: Context
    private lateinit var bleController: VTBLEController

    private var connectionState = BLEConnectionState.DISCONNECTED
    private var isInitialized = false
    private var lastConnectionCheck = 0L
    private var connectionRetryCount = 0
    private val maxRetryCount = MAX_CONNECTION_RETRY_COUNT

    private var stateFlowObservationJob: kotlinx.coroutines.Job? = null
    private var deviceMirrorObservationJob: kotlinx.coroutines.Job? = null
    
    // 发送任务管理
    private var currentSendingJob: kotlinx.coroutines.Job? = null
    private var isSending = false
    
    // LED设备状态管理
    private val _ledDevice = MutableStateFlow(LEDDevice.createDefault())
    val ledDevice: StateFlow<LEDDevice> = _ledDevice.asStateFlow()
    
    // 当前连接的设备信息
    private var currentDeviceName: String = ""
    private var currentDeviceAddress: String = ""

    fun init(context: Context) {
        if (!isInitialized) {
            this@LEDController.context = context.applicationContext // 使用applicationContext避免内存泄漏

            // 初始化DeviceManager
            VTBLEDevicesManager.init(this@LEDController.context)

            //Led屏幕的配置类
            LedConfigManager.init( this@LEDController.context )


            initBleController()
            // 如果有保存的设备地址，设置到控制器中用于快速重连
            val savedDeviceAddress = VTBLEDevicesManager.getLastConnectedDeviceAddress()
            if (savedDeviceAddress != null) {
                bleController.setDeviceAddress(savedDeviceAddress)
                logd(TAG,"LEDController initialized with saved device address: $savedDeviceAddress")
            } else {
                logd(TAG,"LEDController initialized without saved device address")
            }
            
            isInitialized = true
        }
    }

    fun getConnectionState(): BLEConnectionState = connectionState
    
    /**
     * 获取当前LED设备状态
     */
    fun getCurrentLEDDevice(): LEDDevice = _ledDevice.value
    
    /**
     * 获取协程作用域
     */
    fun getScope() = VTCoroutineUtil.getScope(SCOPE_NAME)
    
    /**
     * 更新设备连接状态
     */
    private fun updateDeviceConnectionState(newState: BLEConnectionState) {
        connectionState = newState
        val currentDevice = _ledDevice.value
        val updatedBleDevice = currentDevice.bleDevice.copyWith(
            connectionState = newState,
            lastConnectedTime = if (newState == BLEConnectionState.CONNECTED || newState == BLEConnectionState.READY) System.currentTimeMillis() else currentDevice.lastConnectedTime
        )
        _ledDevice.value = currentDevice.copyWith(bleDevice = updatedBleDevice)
        logd(TAG, "设备连接状态更新: $newState")
    }
    
    /**
     * 更新设备基本信息
     */
    private fun updateDeviceInfo(name: String?, address: String?) {
        currentDeviceName = name ?: ""
        currentDeviceAddress = address ?: ""
        val currentDevice = _ledDevice.value
        val updatedBleDevice = currentDevice.bleDevice.copyWith(
            name = currentDeviceName,
            macAddress = currentDeviceAddress
        )
        _ledDevice.value = currentDevice.copyWith(bleDevice = updatedBleDevice)
        logd(TAG, "设备信息更新: name=$currentDeviceName, address=$currentDeviceAddress")
    }
    
    /**
     * 更新设备分辨率
     */
    fun updateDeviceResolution(width: Int, height: Int) {
        val newResolution = LEDDevice.Resolution(width, height)
        _ledDevice.value = _ledDevice.value.copyWith(resolution = newResolution)
        logd(TAG, "设备分辨率更新: $width x $height")
    }
    
    /**
     * 更新设备信息（分辨率、亮度、固件版本）
     */
    fun updateDeviceInfo(
        width: Int? = null,
        height: Int? = null,
        brightness: Int? = null,
        firmwareVersion: String? = null
    ) {
        val currentDevice = _ledDevice.value
        var newResolution = currentDevice.resolution
        var newBrightness = currentDevice.brightness
        var newFirmwareVersion = currentDevice.firmwareVersion
        
        // 更新分辨率
        if (width != null && height != null) {
            newResolution = LEDDevice.Resolution(width, height)
        }
        
        // 更新亮度
        if (brightness != null) {
            newBrightness = brightness
        }
        
        // 更新固件版本
        if (firmwareVersion != null) {
            newFirmwareVersion = firmwareVersion
        }
        
        _ledDevice.value = currentDevice.copyWith(
            resolution = newResolution,
            brightness = newBrightness,
            firmwareVersion = newFirmwareVersion
        )
        
        logd(TAG, "设备信息更新: 分辨率=${newResolution}, 亮度=${newBrightness}%, 固件版本=${newFirmwareVersion}")
    }
    
    /**
     * 更新设备配对状态
     */
    private fun updateDeviceBondingState(isBonded: Boolean) {
        val currentDevice = _ledDevice.value
        val updatedBleDevice = currentDevice.bleDevice.copyWith(isBonded = isBonded)
        _ledDevice.value = currentDevice.copyWith(bleDevice = updatedBleDevice)
        logd(TAG, "设备配对状态更新: $isBonded")
    }
    
    /**
     * 更新设备MTU信息
     */
    private fun updateDeviceMtu(mtu: Int) {
        val currentDevice = _ledDevice.value
        val updatedBleDevice = currentDevice.bleDevice.copyWith(mtu = mtu)
        _ledDevice.value = currentDevice.copyWith(bleDevice = updatedBleDevice)
        logd(TAG, "设备MTU更新: $mtu")
    }
    
    /**
     * 更新设备PHY信息
     */
    private fun updateDevicePhy(txPhy: Int, rxPhy: Int) {
        val currentDevice = _ledDevice.value
        val updatedBleDevice = currentDevice.bleDevice.copyWith(txPhy = txPhy, rxPhy = rxPhy)
        _ledDevice.value = currentDevice.copyWith(bleDevice = updatedBleDevice)
        logd(TAG, "设备PHY更新: TX=$txPhy, RX=$rxPhy")
    }
    fun reconnect() {
        logd(TAG, "重新连接")
        // 检查当前连接状态，避免重复连接
        if (connectionState == BLEConnectionState.CONNECTING || connectionState == BLEConnectionState.CONNECTED || connectionState == BLEConnectionState.READY) {
            logd(TAG, "设备正在连接或已连接，跳过重连请求")
            return
        }
        disconnect()
        // 添加延迟避免立即重连
        VTCoroutineUtil.delayInScope("LEDController", 500) {
            connect()
        }
    }

    private fun disconnect() {
        bleController.disconnect()
    }

    /**
     * 检查连接状态并尝试重连
     * 注意：由于现在使用StateFlow自动同步状态，这个方法主要用于重连逻辑
     */
    fun checkConnectionAndReconnect() {
        val currentTime = System.currentTimeMillis()
        
        // 每5秒检查一次连接状态
        if (currentTime - lastConnectionCheck < BleConstants.CONNECTION_CHECK_INTERVAL_MS) {
            return
        }
        
        lastConnectionCheck = currentTime
        
        // 如果状态显示已连接，重置重试计数
        if (connectionState == BLEConnectionState.CONNECTED || connectionState == BLEConnectionState.READY) {
            connectionRetryCount = 0
        } else if (connectionState == BLEConnectionState.DISCONNECTED && connectionRetryCount < maxRetryCount) {
            // 如果断开连接且未超过重试次数，可以尝试重连
            connectionRetryCount++
            logd(TAG,"检测到连接断开，尝试重连 ($connectionRetryCount/$maxRetryCount)")
        } else if (connectionRetryCount >= maxRetryCount) {
            logd(TAG,"重连次数超限，停止自动重连")
            updateDeviceConnectionState(BLEConnectionState.ERROR)
        }
    }



    /**
     * LED屏幕连接
     */
    fun connect() {
        if (!isInitialized) {
            logd(TAG,"LEDController not initialized")
            return
        }
        
        // 检查当前连接状态，避免重复连接
        if (connectionState == BLEConnectionState.CONNECTING || connectionState == BLEConnectionState.CONNECTED || connectionState == BLEConnectionState.READY) {
            logd(TAG, "设备正在连接或已连接，跳过连接请求")
            return
        }
        
        updateDeviceConnectionState(BLEConnectionState.CONNECTING)

        bleController.connect(object : VTBLECallback {
            override fun onCheckCharacteristicSuccess() {
                logd(TAG, "特征值检查成功")
                // 确保连接状态为已连接
                if (connectionState != BLEConnectionState.CONNECTED && connectionState != BLEConnectionState.READY) {
                    updateDeviceConnectionState(BLEConnectionState.READY)
                }
                // 连接成功后发送默认文本
                drawDefaultText()
            }

            override fun onConnecting() {
                logd(TAG, "设备连接中")
            }

            override fun onConnected(name: String?, address: String?) {
                updateDeviceConnectionState(BLEConnectionState.CONNECTED)
                updateDeviceInfo(name, address)
                logd(TAG,"=== 连接成功 ===")
                logd(TAG,"设备名称: ${name ?: "未知"}")
                logd(TAG,"设备地址: ${address ?: "未知"}")
                if (address != null) {
                    logd(TAG,"设备连接成功，配对状态将由VTBLEController管理")
                }

            }

            override fun onDisConnected() {
                updateDeviceConnectionState(BLEConnectionState.DISCONNECTED)
                logd(TAG,"=== 连接断开 ===")
            }

            override fun onScanFailed() {
                updateDeviceConnectionState(BLEConnectionState.ERROR)
                logd(TAG,"=== 连接失败 ===")
                logd(TAG,"所有连接方式都已尝试失败")
            }

            override fun writeDataCallback(isSuccess: Boolean) {
                logd(TAG, "数据写入${if (isSuccess) "成功" else "失败"}")
            }
            
            // MTU协商相关回调
            override fun onMtuNegotiationSuccess(mtu: Int) {
                updateDeviceMtu(mtu)
                logd(TAG,"=== LEDController: MTU协商成功 ===")
                logd(TAG,"协商后的MTU大小: $mtu 字节")
            }
            
            override fun onMtuNegotiationFailed(requestedMtu: Int, actualMtu: Int) {
                updateDeviceMtu(actualMtu)
                logd(TAG,"=== LEDController: MTU协商失败 ===")
                logd(TAG,"请求的MTU大小: $requestedMtu 字节")
                logd(TAG,"实际使用的MTU大小: $actualMtu 字节")
            }
            
            // PHY协商相关回调
            override fun onPhyNegotiationSuccess(txPhy: Int, rxPhy: Int) {
                updateDevicePhy(txPhy, rxPhy)
                logd(TAG,"=== LEDController: PHY协商成功 ===")
                logd(TAG,"TX PHY: $txPhy, RX PHY: $rxPhy")
            }
            
            override fun onPhyNegotiationFailed(requestedPhy: Int, actualPhy: Int) {
                updateDevicePhy(actualPhy, actualPhy)
                logd(TAG,"=== LEDController: PHY协商失败 ===")
                logd(TAG,"请求的PHY: $requestedPhy")
                logd(TAG,"实际的PHY: $actualPhy")
            }
            
            override fun onPhyReadSuccess(txPhy: Int, rxPhy: Int) {
                updateDevicePhy(txPhy, rxPhy)
                logd(TAG,"=== LEDController: PHY读取成功 ===")
                logd(TAG,"TX PHY: $txPhy, RX PHY: $rxPhy")
            }
            
            override fun onPhyReadFailed() {
                logd(TAG,"=== LEDController: PHY读取失败 ===")
            }
            
            override fun onPhyUpdateSuccess(txPhy: Int, rxPhy: Int) {
                updateDevicePhy(txPhy, rxPhy)
                logd(TAG,"=== LEDController: PHY更新成功 ===")
                logd(TAG,"新的TX PHY: $txPhy, 新的RX PHY: $rxPhy")
            }
            
            override fun onPhyUpdateFailed() {
                logd(TAG,"=== LEDController: PHY更新失败 ===")
            }
            
            // 配对相关回调
            override fun onBondingSuccess(name: String?, address: String?) {
                updateDeviceBondingState(true)
                logd(TAG,"=== LEDController: 配对成功 ===")
                logd(TAG,"设备名称: ${name ?: "未知"}")
                logd(TAG,"设备地址: ${address ?: "未知"}")
                logd(TAG,"设备配对成功，连接稳定性将得到提升")
                // 配对成功，  
            }
            
            //
            override fun onNotifyValueReceived(notiValue: Int) {
                logd(TAG,"=== LEDController: onNotifyValueReceived ===")
                logd(TAG,"onNotifyValueReceived: $notiValue")
                
                // 将ESP32的亮度值转换为百分比并更新到LEDDevice
                val percent = (notiValue * 100 / LedConstants.BRIGHTNESS_MAX_VALUE).coerceIn(0, 100)
                updateDeviceInfo(brightness = percent)
                logd(TAG, "亮度通知已处理，更新到LEDDevice: $percent%")
            }
            
            override fun onCharacteristicReadValue(value: String) {
                logd(TAG, "=== LEDController: onCharacteristicReadValue ===")
                logd(TAG, "raw: $value")

                val parts = value.split(',').map { it.trim() }
                var fw: String? = null
                var width: Int? = null
                var height: Int? = null
                var br: Int? = null

                parts.forEach { part ->
                    when {
                        part.startsWith("FW:") -> {
                            fw = part.substringAfter("FW:").trim()
                        }
                        part.startsWith("RES:") -> {
                            val resStr = part.substringAfter("RES:").trim()
                            val wh = resStr.split('x', 'X')
                            if (wh.size >= 2) {
                                width = wh[0].toIntOrNull()
                                height = wh[1].toIntOrNull()
                            }
                        }
                        part.startsWith("BR:") -> {
                            br = part.substringAfter("BR:").trim().toIntOrNull()
                        }
                    }
                }

                // 更新设备信息到LEDDevice
                // 将原始亮度值转换为百分比（0-100）
                val brightnessPercent = br?.let { brightnessValue ->
                    (brightnessValue * 100 / LedConstants.BRIGHTNESS_MAX_VALUE).coerceIn(0, 100)
                }
                
                updateDeviceInfo(
                    width = width,
                    height = height,
                    brightness = brightnessPercent,
                    firmwareVersion = fw
                )

                // 通知上层亮度变化
                if (br != null) {
                    // 亮度值已更新到LEDDevice StateFlow
                    logd(TAG, "解析亮度成功: 原始值=$br, 百分比=${brightnessPercent}%")
                }

                if (width != null && height != null) {
                    logd(TAG, "解析分辨率成功: ${width}x${height}")
                }

                if (fw != null) {
                    logd(TAG, "解析固件版本: $fw")
                }
            }
        })
    }
    
    /**
     * 重新初始化BLE控制器（当设备地址发生变化时）
     */
    fun reinitializeBLE() {
        logd(TAG,"=== 重新初始化BLE控制器 ===")
        
        // 断开当前连接
        if (connectionState == BLEConnectionState.CONNECTED || connectionState == BLEConnectionState.READY) {
            bleController.disconnect()
        }

        // 创建BLE控制器（使用新的通用控制特征值）
        initBleController()
        // 清除之前保存的设备地址
        bleController.clearDeviceAddress()
        
        // 重置连接状态
        updateDeviceConnectionState(BLEConnectionState.DISCONNECTED)
        
        logd(TAG,"BLE控制器重新初始化完成")
    }

    private fun initBleController() {
        bleController = VTBLEController(
            context,
            BleConstants.DEVICE_NAME,
            BleConstants.LED_SERVICE_UUID,
            BleConstants.LED_CHARACTERISTIC_CONTROL_UUID,
            BleConstants.LED_CHARACTERISTIC_BRIGHTNESS_UUID
        )
        
        // 开始观察BLE连接状态变化
        startObservingBLEState()
        // 开始镜像BLE层设备基础信息
        startObservingBleDevice()
        
        logd(TAG,"BLE控制器初始化完成，将自动进行MTU协商和PHY优化")
    }

    /**
     * 开始观察BLE连接状态变化
     */
    private fun startObservingBLEState() {
        // 取消之前的观察
        stopObservingBLEState()
        
        stateFlowObservationJob = VTCoroutineUtil.getScope(SCOPE_NAME).launch {
            bleController.connectionState.collect { bleState ->
                handleBLEStateChange(bleState)
            }
        }
    }
    
    /**
     * 停止观察BLE连接状态变化
     */
    private fun stopObservingBLEState() {
        stateFlowObservationJob?.cancel()
        stateFlowObservationJob = null
    }
    
    /**
     * 观察BLE层设备基础信息，并合并到LEDDevice
     */
    private fun startObservingBleDevice() {
        deviceMirrorObservationJob?.cancel()
        deviceMirrorObservationJob = VTCoroutineUtil.getScope(SCOPE_NAME).launch {
            bleController.device.collect { base: VTBLEDevice ->
                val cur = _ledDevice.value
                _ledDevice.value = cur.copyWith(bleDevice = base)
            }
        }
    }
    
    /**
     * 处理BLE状态变化
     */
    private fun handleBLEStateChange(bleState: BLEConnectionState) {
        if (bleState != connectionState) {
            logd(TAG, "BLE状态变化: ${connectionState} -> ${bleState}")
            updateDeviceConnectionState(bleState)
        }
    }

    /**
     * 检查设备连接状态
     */
    private fun checkConnection(): Boolean {
        return if (connectionState == BLEConnectionState.CONNECTED || connectionState == BLEConnectionState.READY) {
            true
        } else {
            logd(TAG, "Device not connected, current state: $connectionState")
            false
        }
    }
    
    /**
     * 检查文本是否有效
     */
    private fun isValidText(text: String): Boolean {
        return if (text.isNotBlank()) {
            true
        } else {
            logd(TAG, "Text is empty")
            false
        }
    }
    
    /**
     * 显示静态文字
     */
    fun drawStaticText(text: String, fontSize: Int = DEFAULT_FONT_SIZE) {
        if (!checkConnection() || !isValidText(text)) return

        val command = buildString {
            append(LedConstants.LED_CMD_STATIC_TEXT)
            append(fontSize)
            append(',')
            append(text)
        }
        
        logd(TAG, "发送静态文本命令: $command")
        
        bleController.sendText(
            BleConstants.LED_SERVICE_UUID,
            BleConstants.LED_CHARACTERISTIC_CONTROL_UUID,
            command
        )
    }

    /**
     * 显示滚动文字
     */
    fun drawScrollingText(text: String, fontSize: Int = DEFAULT_FONT_SIZE, speed: Int = DEFAULT_SCROLL_SPEED) {
        if (!checkConnection() || !isValidText(text)) return

        val command = buildString {
            append(LedConstants.LED_CMD_SCROLLING_TEXT)
            append(fontSize)
            append(',')
            append(speed)
            append(',')
            append(text)
        }
        
        bleController.sendText(
            BleConstants.LED_SERVICE_UUID,
            BleConstants.LED_CHARACTERISTIC_CONTROL_UUID,
            command
        )
    }

    /**
     * 发送控制命令
     */
    private fun sendControlCommand(command: String) {
        if (!checkConnection()) return
        
        bleController.sendText(
            BleConstants.LED_SERVICE_UUID,
            BleConstants.LED_CHARACTERISTIC_CONTROL_UUID,
            command
        )
    }
    
    /**
     * 启用时钟显示
     */
    fun enableClock() {
        if (!checkConnection()) return

        val currentTimestamp = System.currentTimeMillis() / 1000
        val command = buildString {
            append(LedConstants.LED_CMD_CLOCK_ENABLE)
            append(',')
            append(currentTimestamp)
        }
        
        sendControlCommand(command)
        logd(TAG, "Clock enabled with timestamp: $currentTimestamp")
    }

    /**
     * 禁用时钟显示
     */
    fun disableClock() {
        if (!checkConnection()) return
        
        sendControlCommand(LedConstants.LED_CMD_CLOCK_DISABLE)
        logd(TAG, "Clock disabled")
    }

    /**
     * 检查字节数组是否有效
     */
    private fun isValidByteArray(byteArray: ByteArray, dataType: String): Boolean {
        return if (byteArray.isNotEmpty()) {
            true
        } else {
            logd(TAG, "$dataType data is empty")
            false
        }
    }
    
    /**
     * 显示单色图绘制图
     */
    fun drawNormalCanvas(byteArray: ByteArray) {
        if (!checkConnection() || !isValidByteArray(byteArray, "Canvas")) return

        // 取消之前的发送任务，避免重复发送
        currentSendingJob?.cancel()
        currentSendingJob = VTCoroutineUtil.getScope(SCOPE_NAME).launch {
            try {
                // 等待之前的发送完成
                while (isSending) {
                    delay(50)
                }
                
                isSending = true
                
                // 发送数据头信息：总大小,分块数量
                val totalSize = byteArray.size
                val chunkSize = BleConstants.BLE_CHUNK_SIZE // BLE MTU限制
                val chunkCount = (totalSize + chunkSize - 1) / chunkSize // 向上取整
                
                val header = "$totalSize,$chunkCount"
                logd(TAG,"发送图像数据头: $header, 总大小: $totalSize, 分块数: $chunkCount")
                
                // 先发送头信息
                val headerSent = bleController.sendBytesSync(
                    BleConstants.LED_SERVICE_UUID,
                    BleConstants.LED_CHARACTERISTIC_CONTROL_UUID,
                    header.toByteArray()
                )
                
                if (!headerSent) {
                    logd(TAG,"图像数据头发送失败")
                    isSending = false
                    return@launch
                }
                
                // 增加延迟时间，确保头信息被ESP32处理
                delay(BLE_HEADER_PROCESS_DELAY_MS)
                
                // 然后发送图像数据
                val dataSent = bleController.sendBytesSync(
                    BleConstants.LED_SERVICE_UUID,
                    BleConstants.LED_CHARACTERISTIC_CONTROL_UUID,
                    byteArray
                )
                
                if (dataSent) {
                    logd(TAG,"图像数据发送完成")
                } else {
                    logd(TAG,"图像数据发送失败")
                }
                
                isSending = false
            } catch (e: Exception) {
                logd(TAG,"发送图像数据时发生错误: ${e.message}")
                isSending = false
            }
        }
    }

    /**
     * 设置亮度值
     */
    fun setNotiValue(value: Int) {
        if (!checkConnection()) return
        
        logd(TAG, "setNotiValue: $value")
        val notiValue = (LedConstants.BRIGHTNESS_MAX_VALUE / LedConstants.BRIGHTNESS_PERCENTAGE_DIVISOR * value).toInt()
        val finalBrightness = if (notiValue == 0) LedConstants.LED_MINIMUM_BRIGHTNESS else notiValue
        
        val command = buildString {
            append(LedConstants.LED_CMD_BRIGHTNESS)
            append(finalBrightness)
        }
        
        sendControlCommand(command)
    }

    /**
     * 设置刷新频率
     */
    fun setRefreshRate(refreshRate: Int) {
        if (!checkConnection()) return
        
        val finalRefreshRate = refreshRate.coerceIn(
            LedConstants.REFRESH_RATE_MIN_HZ,
            LedConstants.REFRESH_RATE_MAX_HZ
        )
        
        logd(TAG, "设置刷新频率: $finalRefreshRate Hz")
        
        val command = buildString {
            append(LedConstants.LED_CMD_REFRESH_RATE)
            append(finalRefreshRate)
        }
        
        sendControlCommand(command)
    }

    /**
     * 绘制一个像素
     */
    fun draw1Pixel(x: Int, y: Int, color: Int) {
        if (!checkConnection()) return
        
        val command = buildString {
            append(LedConstants.LED_CMD_PIXEL)
            append(x)
            append(',')
            append(y)
            append(',')
            append(color)
        }
        
        sendControlCommand(command)
    }

    /**
     * 填充屏幕
     * @param isClear true为黑屏，false为白屏
     */
    fun fillScreen(isClear: Boolean) {
        if (!checkConnection()) return
        
        val fillValue = if (isClear) LedConstants.FILL_SCREEN_CLEAR else LedConstants.FILL_SCREEN_WHITE
        val command = buildString {
            append(LedConstants.LED_CMD_FILL_SCREEN)
            append(fillValue)
        }
        
        sendControlCommand(command)
    }

    /**
     * 绘制图片
     */
    fun drawImage(imagePath: String) {
        // TODO: 待实现
        logd(TAG, "drawImage not implemented yet")
    }
    
    /**
     * 绘制Gif动画 (通过字节数据)
     */
    fun drawGifBytes(gifBytes: ByteArray, callback: ((Boolean, String?) -> Unit)? = null, progressCallback: ((Int) -> Unit)? = null) {
        // 检查连接状态
        checkConnectionAndReconnect()
        
        if (!checkConnection()) {
            callback?.invoke(false, "设备未连接")
            return
        }
        
        if (!isValidByteArray(gifBytes, "GIF")) {
            callback?.invoke(false, "GIF数据为空")
            return
        }
        
        // 检查文件大小限制
        val maxSize = bleController.getCurrentMtu() * BLE_MTU_KB_MULTIPLIER  // 使用动态MTU大小作为KB单位
        if (gifBytes.size > maxSize) {
            logd(TAG,"GIF文件过大: ${gifBytes.size} 字节，最大支持: $maxSize 字节")
            callback?.invoke(false, "GIF文件过大")
            return
        }
        
        logd(TAG,"开始发送GIF字节数据: ${gifBytes.size} 字节")
        
        currentSendingJob?.cancel()
        currentSendingJob = VTCoroutineUtil.getScope(SCOPE_NAME).launch {
            isSending = true
            try {
                // 发送头信息包 (包类型: 0x01, 块索引: 0x00, 数据: 4字节文件大小)
                // 使用动态MTU大小
                val currentMtu = bleController.getCurrentMtu()
                val headerData = ByteArray(currentMtu)
                headerData[0] = ProtocolConstants.PACKET_TYPE_HEADER  // 包类型：头信息
                headerData[1] = ProtocolConstants.PACKET_INDEX_HEADER  // 块索引
                headerData[2] = (gifBytes.size shr 24).toByte()  // 文件大小高字节
                headerData[3] = (gifBytes.size shr 16).toByte()
                headerData[4] = (gifBytes.size shr 8).toByte()
                headerData[5] = gifBytes.size.toByte()  // 文件大小低字节
                // 其余字节填充为0
                for (i in BLE_FILE_SIZE_BYTES + 2 until currentMtu) {
                    headerData[i] = 0x00
                }
                
                logd(TAG,"发送GIF头信息包: 文件大小=${gifBytes.size} 字节")
                
                // 发送头信息包，增加重试机制和确认机制
                var headerSent = false
                for (retry in 0..GIF_HEADER_RETRY_COUNT) {  // 增加重试次数到5次
                    logd(TAG,"发送GIF头信息包 (重试 $retry/$GIF_HEADER_RETRY_COUNT)")
                    logd(TAG,"头信息包内容: ${headerData.joinToString(", ") { "0x%02X".format(it) }}")
                    
                    // 使用同步发送，等待确认
                    val sendResult = bleController.sendBytesSync(
                        BleConstants.LED_SERVICE_UUID,
                        BleConstants.LED_CHARACTERISTIC_GIF_UUID,
                        headerData
                    )
                    
                    if (sendResult) {
                        headerSent = true
                        logd(TAG,"头信息包发送成功 (重试 $retry/$GIF_HEADER_RETRY_COUNT)")
                        break
                    } else {
                        logd(TAG,"头信息包发送失败 (重试 $retry/$GIF_HEADER_RETRY_COUNT)")
                        // 使用协程delay替代Thread.sleep，增加重试延迟
                        delay(CONNECTION_RETRY_DELAY_MS * (retry + 1))  // 增加延迟时间
                    }
                }

                if (!headerSent) {
                    logd(TAG,"头信息包发送失败，所有重试都失败了")
                    callback?.invoke(false, "头信息包发送失败")
                    return@launch
                }

                logd(TAG,"头信息包发送成功，等待ESP32处理...")
                delay(BLE_HEADER_PROCESS_DELAY_MS)  // 增加等待时间，确保ESP32有足够时间处理头信息
                
                // 分块发送GIF数据
                // 使用动态MTU大小，减去2字节包头
                val chunkSize = currentMtu - BLE_HEADER_SIZE  // BLE数据块大小
                logd(TAG,"使用数据块大小: $chunkSize 字节 (总块大小: $currentMtu - 2字节包头)")
                var chunkIndex = 0
                var successCount = 0
                var failCount = 0
                val totalChunks = (gifBytes.size + chunkSize - 1) / chunkSize

                for (i in gifBytes.indices step chunkSize) {
                    // 检查BLE连接状态，如果断开则立即停止发送
                    if (connectionState != BLEConnectionState.CONNECTED && connectionState != BLEConnectionState.READY) {
                        logd(TAG,"检测到BLE连接断开，停止GIF发送")
                        callback?.invoke(false, "BLE连接断开，发送已停止")
                        return@launch
                    }
                    
                    val remainingBytes = gifBytes.size - i
                    val currentChunkSize = minOf(chunkSize, remainingBytes)

                    val chunkData = ByteArray(currentChunkSize + BLE_HEADER_SIZE)
                    chunkData[0] = ProtocolConstants.PACKET_TYPE_DATA  // 包类型：数据
                    chunkData[1] = chunkIndex.toByte()  // 块索引

                    // 复制GIF数据
                    System.arraycopy(gifBytes, i, chunkData, BLE_HEADER_SIZE, currentChunkSize)

                    // 发送数据包，增加重试机制
                    var chunkSent = false
                    for (retry in 0..GIF_DATA_RETRY_COUNT) {  // 增加重试次数到3次
                        // 每次重试前都检查连接状态
                        if (connectionState != BLEConnectionState.CONNECTED && connectionState != BLEConnectionState.READY) {
                            logd(TAG,"重试过程中检测到BLE连接断开，停止GIF发送")
                            callback?.invoke(false, "BLE连接断开，发送已停止")
                            return@launch
                        }
                        
                        if (retry > 0) {
                            logd(TAG,"数据包 $chunkIndex 发送失败，重试 $retry/$GIF_DATA_RETRY_COUNT")
                        }
                        
                        val sendResult = bleController.sendBytesSync(
                            BleConstants.LED_SERVICE_UUID,
                            BleConstants.LED_CHARACTERISTIC_GIF_UUID,
                            chunkData
                        )

                        if (sendResult) {
                            chunkSent = true
                            break
                        } else {
                            // 使用协程delay替代Thread.sleep，增加延迟时间
                            delay(BLE_HEADER_DELAY_MS * (retry + 1))  // 递增延迟：100ms, 200ms, 300ms
                        }
                    }

                    if (chunkSent) {
                        successCount++
                    } else {
                        failCount++
                        logd(TAG,"数据包 $chunkIndex 发送失败")
                    }

                    chunkIndex++

                    // 计算并发送进度
                    val progress = ((chunkIndex * LedConstants.PROGRESS_PERCENTAGE) / totalChunks).coerceAtMost(LedConstants.PROGRESS_PERCENTAGE)
                    logd(TAG,"GIF发送进度: $progress% ($chunkIndex/$totalChunks)")
                    progressCallback?.invoke(progress)

                    delay(BLE_PACKET_DELAY_MS)  // 增加包间延迟

                    // 每发送10个包，额外等待一下，减少频率但增加等待时间
                    if (chunkIndex % BLE_GIF_BATCH_SIZE == 0) {
                        delay(BLE_BATCH_DELAY_MS)  // 增加等待时间
                        logd(TAG,"已发送 $chunkIndex 个数据包，成功: $successCount，失败: $failCount")
                    }
                }
                
                logd(TAG,"GIF字节数据发送完成: $chunkIndex 个数据块")
                
                // 发送完成后等待一段时间，确保ESP32处理完成
                delay(BLE_HEADER_PROCESS_DELAY_MS)
                
                // 发送成功回调
                if (failCount == 0) {
                    logd(TAG,"GIF发送完全成功: 成功 $successCount 个包，失败 $failCount 个包")
                    callback?.invoke(true, "GIF发送成功")
                } else {
                    logd(TAG,"GIF发送部分失败: 成功 $successCount 个包，失败 $failCount 个包")
                    callback?.invoke(false, "部分数据包发送失败 ($failCount 个包失败)")
                }
                
            } catch (e: Exception) {
                logd(TAG,"发送GIF数据时发生错误: ${e.message}")
                callback?.invoke(false, "发送失败: ${e.message}")
            } finally {
                isSending = false
                currentSendingJob = null
            }
        }
    }

    /**
     * 显示彩色绘制图
     */
    fun drawColorfulCanvas(data: IntArray) {
        // TODO: 待实现
        logd(TAG,"drawColorfulCanvas not implemented yet")
    }
    
    /**
     * 发送图片字节数据
     */
    fun drawImageBytes(imageBytes: ByteArray, callback: ((Boolean, String?) -> Unit)? = null, progressCallback: ((Int) -> Unit)? = null) {
        if (!checkConnection()) {
            callback?.invoke(false, "设备未连接")
            return
        }
        
        if (!isValidByteArray(imageBytes, "Image")) {
            callback?.invoke(false, "图片数据为空")
            return
        }
        
        // 检查文件大小限制
        val maxSize = bleController.getCurrentMtu() * BLE_MTU_KB_MULTIPLIER
        if (imageBytes.size > maxSize) {
            logd(TAG,"Image file过大: ${imageBytes.size} 字节，最大支持: $maxSize 字节")
            callback?.invoke(false, "图片文件过大")
            return
        }
        
        logd(TAG,"开始发送图片字节数据: ${imageBytes.size} 字节")
        
        // 取消之前的发送任务
        currentSendingJob?.cancel()
        
        // 使用协程异步发送
        currentSendingJob = VTCoroutineUtil.getScope(SCOPE_NAME).launch {
            isSending = true
            try {
                // 发送头信息包
                val currentMtu = bleController.getCurrentMtu()
                val headerData = ByteArray(currentMtu)
                headerData[0] = ProtocolConstants.PACKET_TYPE_HEADER  // 包类型：头信息
                headerData[1] = ProtocolConstants.PACKET_INDEX_HEADER  // 块索引
                headerData[2] = (imageBytes.size shr 24).toByte()
                headerData[3] = (imageBytes.size shr 16).toByte()
                headerData[4] = (imageBytes.size shr 8).toByte()
                headerData[5] = imageBytes.size.toByte()
                
                for (i in BLE_FILE_SIZE_BYTES + 2 until currentMtu) {
                    headerData[i] = 0x00
                }
                
                logd(TAG,"发送图片头信息包: 文件大小=${imageBytes.size} 字节")
                
                // 发送头信息包
                var headerSent = false
                for (retry in 0..IMAGE_HEADER_RETRY_COUNT) {
                    val sendResult = bleController.sendBytesSync(
                        BleConstants.LED_SERVICE_UUID,
                        BleConstants.LED_CHARACTERISTIC_GIF_UUID, // 复用GIF特征值
                        headerData
                    )
                    
                    if (sendResult) {
                        headerSent = true
                        break
                    } else {
                        delay(BLE_HEADER_DELAY_MS * (retry + 1))
                    }
                }
                
                if (!headerSent) {
                    callback?.invoke(false, "头信息包发送失败")
                    return@launch
                }
                
                delay(BLE_IMAGE_HEADER_DELAY_MS)
                
                // 分块发送图片数据
                val chunkSize = currentMtu - BLE_HEADER_SIZE
                var chunkIndex = 0
                var successCount = 0
                var failCount = 0
                val totalChunks = (imageBytes.size + chunkSize - 1) / chunkSize
                
                logd(TAG,"图片发送参数: MTU=$currentMtu, chunkSize=$chunkSize, totalChunks=$totalChunks, imageSize=${imageBytes.size}")
                
                for (i in imageBytes.indices step chunkSize) {
                    // 检查BLE连接状态，如果断开则立即停止发送
                    if (connectionState != BLEConnectionState.CONNECTED && connectionState != BLEConnectionState.READY) {
                        logd(TAG,"检测到BLE连接断开，停止图片发送")
                        callback?.invoke(false, "BLE连接断开，发送已停止")
                        return@launch
                    }
                    
                    val remainingBytes = imageBytes.size - i
                    val currentChunkSize = minOf(chunkSize, remainingBytes)
                    
                    val chunkData = ByteArray(currentChunkSize + BLE_HEADER_SIZE)
                    chunkData[0] = ProtocolConstants.PACKET_TYPE_DATA  // 包类型：数据
                    chunkData[1] = chunkIndex.toByte()
                    
                    System.arraycopy(imageBytes, i, chunkData, BLE_HEADER_SIZE, currentChunkSize)
                    
                    var chunkSent = false
                    for (retry in 0..IMAGE_DATA_RETRY_COUNT) {
                        // 每次重试前都检查连接状态
                        if (connectionState != BLEConnectionState.CONNECTED && connectionState != BLEConnectionState.READY) {
                            logd(TAG,"重试过程中检测到BLE连接断开，停止图片发送")
                            callback?.invoke(false, "BLE连接断开，发送已停止")
                            return@launch
                        }
                        
                        val sendResult = bleController.sendBytesSync(
                            BleConstants.LED_SERVICE_UUID,
                            BleConstants.LED_CHARACTERISTIC_GIF_UUID,
                            chunkData
                        )
                        
                        if (sendResult) {
                            chunkSent = true
                            break
                        } else {
                            delay(BLE_IMAGE_PACKET_DELAY_MS)
                        }
                    }
                    
                    if (chunkSent) {
                        successCount++
                    } else {
                        failCount++
                    }
                    
                    chunkIndex++
                    
                    // 计算并发送进度
                    val progress = ((chunkIndex * LedConstants.PROGRESS_PERCENTAGE) / totalChunks).coerceAtMost(LedConstants.PROGRESS_PERCENTAGE)
                    logd(TAG,"图片发送进度: $progress% ($chunkIndex/$totalChunks)")
                    progressCallback?.invoke(progress)
                    
                    delay(BLE_IMAGE_PACKET_DELAY_MS)
                    
                    if (chunkIndex % BLE_IMAGE_BATCH_SIZE == 0) {
                        delay(BLE_IMAGE_BATCH_DELAY_MS)
                    }
                }
                
                logd(TAG,"图片字节数据发送完成: $chunkIndex 个数据块")
                
                if (failCount == 0) {
                    callback?.invoke(true, "图片发送成功")
                } else {
                    callback?.invoke(false, "部分数据包发送失败")
                }
                
            } catch (e: Exception) {
                logd(TAG,"发送图片数据时发生错误: ${e.message}")
                callback?.invoke(false, "发送失败: ${e.message}")
            } finally {
                isSending = false
                currentSendingJob = null
            }
        }
    }

    fun drawDefaultText() {
        drawStaticText(LED_DEFAULT_DISPLAY_TEXT)
    }

    /**
     * 停止发送任务
     */
    private fun stopSendingTask(taskName: String) {
        logd(TAG, "停止发送$taskName")
        currentSendingJob?.cancel()
        isSending = false
    }
    
    /**
     * 停止发送GIF字节数据
     */
    fun stopSendGifBytes() {
        stopSendingTask("GIF字节数据")
    }
    
    /**
     * 停止发送图片字节数据
     */
    fun stopSendImageBytes() {
        stopSendingTask("图片字节数据")
    }
    
    /**
     * 停止所有发送任务
     */
    fun stopAllSending() {
        stopSendingTask("所有发送任务")
    }
    
    /**
     * 检查是否正在发送
     */
    fun isSending(): Boolean {
        return isSending
    }
    
    /**
     * 读取当前PHY
     */
    fun readCurrentPhy() {
        if (checkConnection()) {
            bleController.readCurrentPhy()
        }
    }
    
    /**
     * 获取当前TX PHY
     */
    fun getCurrentTxPhy(): Int {
        return bleController.getCurrentTxPhy()
    }
    
    /**
     * 获取当前RX PHY
     */
    fun getCurrentRxPhy(): Int {
        return bleController.getCurrentRxPhy()
    }
    
    /**
     * 检查PHY协商是否成功
     */
    fun isPhyNegotiationSuccessful(): Boolean {
        return bleController.isPhyNegotiationSuccessful()
    }
    
    /**
     * 检查设备是否支持2M PHY
     */
    fun isLe2MPhySupported(): Boolean {
        return bleController.isLe2MPhySupported()
    }
    
    /**
     * 检查设备是否支持Coded PHY
     */
    fun isLeCodedPhySupported(): Boolean {
        return bleController.isLeCodedPhySupported()
    }
    
    /**
     * 获取设备支持的PHY列表
     */
    fun getSupportedPhys(): List<Int> {
        return bleController.getSupportedPhys()
    }
    
    /**
     * 获取PHY描述信息
     */
    fun getPhyDescription(phy: Int): String {
        return bleController.getPhyDescription(phy)
    }
    
    /**
     * 获取PHY性能描述
     */
    fun getPhyPerformanceDescription(phy: Int): String {
        return bleController.getPhyPerformanceDescription(phy)
    }
    
    /**
     * 获取连接质量信息
     */
    fun getConnectionQualityInfo(): String {
        return bleController.getConnectionQualityInfo()
    }
    
    // ==================== 配对管理相关方法 ====================
    
    /**
     * 检查当前设备是否已配对
     */
    fun isCurrentDeviceBonded(): Boolean {
        return bleController.isCurrentDeviceBonded()
    }
    
    /**
     * 获取配对状态信息
     */
    fun getBondingStatusInfo(): String {
        return bleController.getBondingStatusInfo()
    }
    
    /**
     * 获取设备信息摘要
     */
    fun getDeviceInfoSummary(): String {
        return VTBLEDevicesManager.getDeviceInfoSummary()
    }

    data class DeviceInfo(val firmwareVersion: String, val width: Int, val height: Int)

    /**
     * 读取设备信息（固件版本与分辨率），以及当前亮度（如固件返回）。
     * 现在从亮度特征读取，固件可能返回:
     * - 纯亮度数字: "94"
     * - 组合: "FW:1.0.0,RES:128x64,BR:94"
     */
    fun readDeviceInfo(callback: (DeviceInfo?) -> Unit) {
        if (!checkConnection()) { callback(null); return }

        try {
            val ok = bleController.readCharacteristic(
                BleConstants.LED_SERVICE_UUID,
                BleConstants.LED_CHARACTERISTIC_BRIGHTNESS_UUID
            ) { raw ->
                // 兼容两种返回
                val parsed = parseDeviceInfo(raw)
                if (parsed != null) {
                    updateDeviceResolution(parsed.width, parsed.height)
                    callback(parsed)
                } else {
                    // 若只返回了数字亮度，则无法解析设备信息
                    callback(null)
                }
            }
            if (!ok) callback(null)
        } catch (e: Exception) {
            logd(TAG, "读取设备信息失败: ${e.message}")
            callback(null)
        }
    }

    private fun parseDeviceInfo(text: String?): DeviceInfo? {
        if (text.isNullOrBlank()) return null
        // 期望格式: FW:x.x.x,RES:WxH
        // 简单解析，容错处理
        return try {
            val parts = text.split(",")
            val fw = parts.firstOrNull { it.startsWith("FW:") }?.substringAfter("FW:") ?: return null
            val res = parts.firstOrNull { it.startsWith("RES:") }?.substringAfter("RES:") ?: return null
            val wh = res.split("x")
            val w = wh.getOrNull(0)?.toIntOrNull() ?: return null
            val h = wh.getOrNull(1)?.toIntOrNull() ?: return null
            DeviceInfo(fw, w, h)
        } catch (_: Exception) { null }
    }
    
    /**
     * 获取当前设备名称
     */
    fun getCurrentDeviceName(): String = currentDeviceName
    
    /**
     * 获取当前设备地址
     */
    fun getCurrentDeviceAddress(): String = currentDeviceAddress
    
    /**
     * 检查设备是否已连接
     */
    fun isDeviceConnected(): Boolean = _ledDevice.value.isConnected
    
    /**
     * 检查设备是否正在连接
     */
    fun isDeviceConnecting(): Boolean = _ledDevice.value.isConnecting
    
    /**
     * 检查设备是否有错误
     */
    fun hasDeviceError(): Boolean = _ledDevice.value.hasError
    
    /**
     * 获取设备连接质量信息
     */
    fun getDeviceConnectionQualityInfo(): String {
        val device = _ledDevice.value
        return buildString {
            append("设备状态: ${device.connectionStateDescription}\n")
            append("设备名称: ${if (device.name.isNotEmpty()) device.name else "未知"}\n")
            append("MAC地址: ${if (device.macAddress.isNotEmpty()) device.macAddress else "未知"}\n")
            append("分辨率: ${device.resolution}\n")
            append("配对状态: ${if (device.isBonded) "已配对" else "未配对"}\n")
            if (device.mtu > 0) append("MTU: ${device.mtu}\n")
            if (device.txPhy > 0 || device.rxPhy > 0) append("PHY: TX=${device.txPhy}, RX=${device.rxPhy}\n")
            if (device.lastConnectedTime > 0) {
                val timeStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                    .format(java.util.Date(device.lastConnectedTime))
                append("最后连接时间: $timeStr\n")
            }
        }
    }
    
    /**
     * 手动请求配对（如果需要）
     */
    fun requestBonding() {
        if (!checkConnection()) return
        
        val deviceAddress = bleController.getDeviceAddress()
        if (deviceAddress.isEmpty()) return
        
        val bluetoothAdapter = VTBluetoothUtil.getBluetoothAdapter(context)
        val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
        
        when (device?.bondState) {
            BluetoothDevice.BOND_BONDED -> {
                logd(TAG, "设备已配对，无需再次配对")
            }
            else -> {
                logd(TAG, "=== 手动请求配对 ===")
                logd(TAG, "设备名称: ${device?.name ?: "未知"}")
                logd(TAG, "设备地址: $deviceAddress")
                
                val bondResult = device?.createBond()
                logd(TAG, "配对请求结果: $bondResult")
                
                if (bondResult == true) {
                    logd(TAG, "配对请求已发送，等待用户确认")
                } else {
                    logd(TAG, "配对请求失败")
                }
            }
        }
    }
    
    // ==================== 计时游戏相关方法 ====================
    
    /**
     * 开始计时游戏（请求ESP32生成随机时间）
     */
    fun startTimerGame() {
        if (!checkConnection()) return

        sendControlCommand(LedConstants.LED_CMD_GAME_START)
        logd(TAG, "Start timer game - request random time generation")
    }
    
    /**
     * 开始计时倒计时
     */
    fun startTimerCountdown() {
        if (!checkConnection()) return

        sendControlCommand(LedConstants.LED_CMD_GAME_TIMER_TARGET)
        logd(TAG, "Start timer countdown")
    }
    
    /**
     * 停止计时倒计时
     */
    fun stopTimerCountdown() {
        if (!checkConnection()) return

        sendControlCommand(LedConstants.LED_CMD_GAME_PAUSE)
        logd(TAG, "Stop timer countdown")
    }

    fun cancelJob(){
        VTCoroutineUtil.getScope(SCOPE_NAME).cancel()
        stopObservingBLEState()
        
        logd(TAG, "所有协程任务已取消")
    }
}