package com.vincent.android.myled.led

import VTBLECallback
import android.bluetooth.BluetoothDevice
import android.content.Context
import com.vincent.android.myled.ble.VTBLEController
import com.vincent.android.myled.ble.VTBluetoothUtil
import com.vincent.android.myled.utils.LED_CHARACTERISTIC_BRIGHTNESS_UUID
import com.vincent.android.myled.utils.LED_CHARACTERISTIC_DRAW_NORMAL_UUID
import com.vincent.android.myled.utils.LED_CHARACTERISTIC_FILL_PIXEL_UUID
import com.vincent.android.myled.utils.LED_CHARACTERISTIC_FILL_SCREEN_UUID
import com.vincent.android.myled.utils.LED_CHARACTERISTIC_GIF_UUID
import com.vincent.android.myled.utils.LED_CHARACTERISTIC_TEXT_SCROLL_UUID
import com.vincent.android.myled.utils.LED_CHARACTERISTIC_TEXT_UUID
import com.vincent.android.myled.utils.LED_DEFAULT_BRIGHTNESS
import com.vincent.android.myled.utils.LED_DEFAULT_DISPLAY_TEXT
import com.vincent.android.myled.utils.LED_MINIMUM_BRIGHTNESS
import com.vincent.android.myled.utils.LED_SERVICE_UUID
import com.vincent.android.myled.utils.logd
import com.vincent.android.myled.utils.DeviceManager
import com.vincent.android.myled.utils.LED_CHARACTERISTIC_REFRESH_RATE_UUID
import com.vincent.android.myled.utils.CoroutineUtil
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * LED控制器
 *
 */
class LEDController private constructor() {

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }

    private var connectionState = ConnectionState.DISCONNECTED
    private lateinit var mContext: Context
    private lateinit var mBLEController: VTBLEController
    private var isInitialized = false

    fun initBLE(context: Context) {
        if (!isInitialized) {
            mContext = context.applicationContext // 使用applicationContext避免内存泄漏
            // 初始化DeviceManager
            DeviceManager.init(mContext)
            
            // 创建BLE控制器（不再需要传入设备地址）
            mBLEController = VTBLEController(
                mContext, LED_SERVICE_UUID, LED_CHARACTERISTIC_TEXT_UUID
            )
            
            // 如果有保存的设备地址，设置到控制器中用于快速重连
            val savedDeviceAddress = DeviceManager.getLastConnectedDeviceAddress()
            if (savedDeviceAddress != null) {
                mBLEController.setDeviceAddress(savedDeviceAddress)
                logd("LEDController initialized with saved device address: $savedDeviceAddress")
            } else {
                logd("LEDController initialized without saved device address")
            }
            
            isInitialized = true
        }
    }

    fun getConnectionState(): ConnectionState = connectionState

    companion object {
        @Volatile
        private var INSTANCE: LEDController? = null

        fun getInstance(): LEDController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LEDController().also { INSTANCE = it }
            }
        }
    }

    /**
     * LED屏幕连接
     */
    fun connect(callback: VTBLECallback) {
        if (!isInitialized) {
            logd("LEDController not initialized")
            return
        }
        
        connectionState = ConnectionState.CONNECTING

        mBLEController.connect(object : VTBLECallback by callback {
            override fun onConnected(name: String?, address: String?) {
                connectionState = ConnectionState.CONNECTED
                logd("=== 连接成功 ===")
                logd("设备名称: ${name ?: "未知"}")
                logd("设备地址: ${address ?: "未知"}")
                
                                        // 保存连接成功的设备信息到DeviceManager
                        if (address != null) {
                            // 注意：这里不再直接保存，因为VTBLEController会处理配对和保存
                            logd("设备连接成功，配对状态将由VTBLEController管理")
                        }
                
                callback.onConnected(name, address)
            }

            override fun onDisConnected() {
                connectionState = ConnectionState.DISCONNECTED
                logd("=== 连接断开 ===")
                callback.onDisConnected()
            }

            override fun onScanFailed() {
                connectionState = ConnectionState.ERROR
                logd("=== 连接失败 ===")
                logd("所有连接方式都已尝试失败")
                callback.onScanFailed()
            }
            
            // MTU协商相关回调
            override fun onMtuNegotiationSuccess(mtu: Int) {
                logd("=== LEDController: MTU协商成功 ===")
                logd("协商后的MTU大小: $mtu 字节")
                callback.onMtuNegotiationSuccess(mtu)
            }
            
            override fun onMtuNegotiationFailed(requestedMtu: Int, actualMtu: Int) {
                logd("=== LEDController: MTU协商失败 ===")
                logd("请求的MTU大小: $requestedMtu 字节")
                logd("实际使用的MTU大小: $actualMtu 字节")
                callback.onMtuNegotiationFailed(requestedMtu, actualMtu)
            }
            
            // PHY协商相关回调
            override fun onPhyNegotiationSuccess(txPhy: Int, rxPhy: Int) {
                logd("=== LEDController: PHY协商成功 ===")
                logd("TX PHY: $txPhy, RX PHY: $rxPhy")
                callback.onPhyNegotiationSuccess(txPhy, rxPhy)
            }
            
            override fun onPhyNegotiationFailed(requestedPhy: Int, actualPhy: Int) {
                logd("=== LEDController: PHY协商失败 ===")
                logd("请求的PHY: $requestedPhy")
                logd("实际的PHY: $actualPhy")
                callback.onPhyNegotiationFailed(requestedPhy, actualPhy)
            }
            
            override fun onPhyReadSuccess(txPhy: Int, rxPhy: Int) {
                logd("=== LEDController: PHY读取成功 ===")
                logd("TX PHY: $txPhy, RX PHY: $rxPhy")
                callback.onPhyReadSuccess(txPhy, rxPhy)
            }
            
            override fun onPhyReadFailed() {
                logd("=== LEDController: PHY读取失败 ===")
                callback.onPhyReadFailed()
            }
            
            override fun onPhyUpdateSuccess(txPhy: Int, rxPhy: Int) {
                logd("=== LEDController: PHY更新成功 ===")
                logd("新的TX PHY: $txPhy, 新的RX PHY: $rxPhy")
                callback.onPhyUpdateSuccess(txPhy, rxPhy)
            }
            
            override fun onPhyUpdateFailed() {
                logd("=== LEDController: PHY更新失败 ===")
                callback.onPhyUpdateFailed()
            }
            
            // 配对相关回调
            override fun onBondingSuccess(name: String?, address: String?) {
                logd("=== LEDController: 配对成功 ===")
                logd("设备名称: ${name ?: "未知"}")
                logd("设备地址: ${address ?: "未知"}")
                logd("设备配对成功，连接稳定性将得到提升")
                callback.onBondingSuccess(name, address)
            }
            
            // 亮度通知回调
            override fun onBrightnessReceived(brightness: Int) {
                logd("=== LEDController: 收到亮度通知 ===")
                logd("当前亮度值: $brightness")
                callback.onBrightnessReceived(brightness)
            }
        })
    }
    
    /**
     * 重新初始化BLE控制器（当设备地址发生变化时）
     */
    fun reinitializeBLE() {
        logd("=== 重新初始化BLE控制器 ===")
        
        // 断开当前连接
        if (connectionState == ConnectionState.CONNECTED) {
            mBLEController.disconnect()
        }
        
        // 重新创建BLE控制器
        mBLEController = VTBLEController(
            mContext, LED_SERVICE_UUID, LED_CHARACTERISTIC_TEXT_UUID
        )
        
        // 清除之前保存的设备地址
        mBLEController.clearDeviceAddress()
        
        // 重置连接状态
        connectionState = ConnectionState.DISCONNECTED
        
        logd("BLE控制器重新初始化完成")
    }

    /**
     * 显示静态文字
     */
    fun drawStaticText(text: String, fontSize: Int = 1) {
        if (!mBLEController.isConnected()) {
            logd("Device not connected, cannot send text")
            return
        }
        
        if (text.isBlank()) {
            logd("Text is empty")
            return
        }

        mBLEController.sendText(
            LED_SERVICE_UUID,
            LED_CHARACTERISTIC_TEXT_UUID,
            "$fontSize,$text"
        )
    }

    /**
     * 显示滚动文字
     */
    fun drawScrollingText(text: String, fontSize: Int = 1, speed: Int = 1) {
        if (!mBLEController.isConnected()) {
            logd("Device not connected, cannot send scrolling text")
            return
        }
        
        if (text.isBlank()) {
            logd("Text is empty")
            return
        }

        mBLEController.sendText(
            LED_SERVICE_UUID,
            LED_CHARACTERISTIC_TEXT_SCROLL_UUID,
            "$fontSize,$speed,$text"
        )
    }

    /**
     * 显示单色图绘制图
     */
    fun drawNormalCanvas(byteArray: ByteArray) {
        if (!mBLEController.isConnected()) {
            logd("Device not connected, cannot send canvas data")
            return
        }
        
        if (byteArray.isEmpty()) {
            logd("Canvas data is empty")
            return
        }

        // 使用协程异步发送，避免阻塞主线程
        CoroutineUtil.getScope("LEDController").launch {
            try {
                // 发送数据头信息：总大小,分块数量
                val totalSize = byteArray.size
                val chunkSize = 20 // BLE MTU限制
                val chunkCount = (totalSize + chunkSize - 1) / chunkSize // 向上取整
                
                val header = "$totalSize,$chunkCount"
                logd("发送图像数据头: $header, 总大小: $totalSize, 分块数: $chunkCount")
                
                // 先发送头信息
                mBLEController.sendText(
                    LED_SERVICE_UUID,
                    LED_CHARACTERISTIC_DRAW_NORMAL_UUID,
                    header
                )
                
                // 使用协程delay替代Thread.sleep，确保头信息被处理
                delay(100)
                
                // 然后发送图像数据
                mBLEController.sendBytes(
                    LED_SERVICE_UUID,
                    LED_CHARACTERISTIC_DRAW_NORMAL_UUID,
                    byteArray
                )
                
                logd("图像数据发送完成")
            } catch (e: Exception) {
                logd("发送图像数据时发生错误: ${e.message}")
            }
        }
    }

    /**
     *  设置亮度
     */
    fun setBrightness(value: Int) {
        if (!mBLEController.isConnected()) {
            logd("Device not connected, cannot set brightness")
            return
        }
        logd("setBrightness : $value")
        val brightness: Int = (255 / 100.0f * value).toInt()
        val finalBrightness = if (brightness == 0) LED_MINIMUM_BRIGHTNESS else brightness
        
        mBLEController.sendText(
            LED_SERVICE_UUID,
            LED_CHARACTERISTIC_BRIGHTNESS_UUID,
            finalBrightness.toString()
        )
    }

    /**
     *  设置刷新频率
     */
    fun setRefreshRate(refreshRate: Int) {
        if (!mBLEController.isConnected()) {
            logd("Device not connected, cannot set refresh rate")
            return
        }
        
        // 限制刷新频率范围 (10Hz - 200Hz)
        val finalRefreshRate = when {
            refreshRate < 10 -> 10
            refreshRate > 200 -> 200
            else -> refreshRate
        }
        
        logd("设置刷新频率: $finalRefreshRate Hz")
        mBLEController.sendText(
            LED_SERVICE_UUID,
            LED_CHARACTERISTIC_REFRESH_RATE_UUID,
            finalRefreshRate.toString()
        )
    }

    /**
     * 绘制一个像素
     */
    fun draw1Pixel(x: Int, y: Int, color: Int) {
        if (!mBLEController.isConnected()) {
            logd("Device not connected, cannot draw pixel")
            return
        }
        
        val text = "$x,$y,$color"
        mBLEController.sendText(
            LED_SERVICE_UUID,
            LED_CHARACTERISTIC_FILL_PIXEL_UUID,
            text
        )
    }

    /**
     *  填充屏幕
     *   isClear为true的时候黑屏，反之白屏
     */
    fun fillScreen(isClear: Boolean) {
        if (!mBLEController.isConnected()) {
            logd("Device not connected, cannot fill screen")
            return
        }
        
        mBLEController.sendText(
            LED_SERVICE_UUID,
            LED_CHARACTERISTIC_FILL_SCREEN_UUID,
            if (isClear) "1" else "0"
        )
    }

    /**
     *  绘制图片
     */
    fun drawImage(imagePath: String) {
        // TODO: 待实现
        logd("drawImage not implemented yet")
    }
    

    
    /**
     *  绘制Gif动画 (通过字节数据)
     */
    fun drawGifBytes(gifBytes: ByteArray, callback: ((Boolean, String?) -> Unit)? = null) {
        if (!mBLEController.isConnected()) {
            logd("Device not connected, cannot draw GIF")
            callback?.invoke(false, "设备未连接")
            return
        }
        
        if (gifBytes.isEmpty()) {
            logd("GIF bytes is empty")
            callback?.invoke(false, "GIF数据为空")
            return
        }
        
        // 检查文件大小限制
        val maxSize = mBLEController.getCurrentMtu() * 1024  // 使用动态MTU大小作为KB单位
        if (gifBytes.size > maxSize) {
            logd("GIF文件过大: ${gifBytes.size} 字节，最大支持: $maxSize 字节")
            callback?.invoke(false, "GIF文件过大")
            return
        }
        
        logd("开始发送GIF字节数据: ${gifBytes.size} 字节")
        
        // 使用协程异步发送，避免阻塞主线程
        CoroutineUtil.getScope("LEDController").launch {
            try {
                // 发送头信息包 (包类型: 0x01, 块索引: 0x00, 数据: 4字节文件大小)
                // 使用动态MTU大小
                val currentMtu = mBLEController.getCurrentMtu()
                val headerData = ByteArray(currentMtu)
                headerData[0] = 0x01  // 包类型：头信息
                headerData[1] = 0x00  // 块索引
                headerData[2] = (gifBytes.size shr 24).toByte()  // 文件大小高字节
                headerData[3] = (gifBytes.size shr 16).toByte()
                headerData[4] = (gifBytes.size shr 8).toByte()
                headerData[5] = gifBytes.size.toByte()  // 文件大小低字节
                // 其余字节填充为0
                for (i in 6 until currentMtu) {
                    headerData[i] = 0x00
                }
                
                logd("发送GIF头信息包: 文件大小=${gifBytes.size} 字节")
                
                // 发送头信息包，增加重试机制和确认机制
                var headerSent = false
                for (retry in 0..3) {  // 增加重试次数
                    logd("发送GIF头信息包 (重试 $retry/3)")
                    logd("头信息包内容: ${headerData.joinToString(", ") { "0x%02X".format(it) }}")
                    
                    // 使用同步发送，等待确认
                    val sendResult = mBLEController.sendBytesSync(
                        LED_SERVICE_UUID,
                        LED_CHARACTERISTIC_GIF_UUID,
                        headerData
                    )
                    
                    if (sendResult) {
                        headerSent = true
                        logd("头信息包发送成功 (重试 $retry/3)")
                        break
                    } else {
                        logd("头信息包发送失败 (重试 $retry/3)")
                        // 使用协程delay替代Thread.sleep，增加重试延迟
                        delay(100L * (retry + 1))
                    }
                }

                if (!headerSent) {
                    logd("头信息包发送失败，所有重试都失败了")
                    callback?.invoke(false, "头信息包发送失败")
                    return@launch
                }

                logd("头信息包发送成功，等待ESP32处理...")
                delay(500)
                
                // 分块发送GIF数据
                // 使用动态MTU大小，减去2字节包头
                val chunkSize = currentMtu - 2  // BLE数据块大小
                logd("使用数据块大小: $chunkSize 字节 (总块大小: $currentMtu - 2字节包头)")
                var chunkIndex = 0
                var successCount = 0
                var failCount = 0
                val totalChunks = (gifBytes.size + chunkSize - 1) / chunkSize

                for (i in gifBytes.indices step chunkSize) {
                    val remainingBytes = gifBytes.size - i
                    val currentChunkSize = minOf(chunkSize, remainingBytes)

                    val chunkData = ByteArray(currentChunkSize + 2)
                    chunkData[0] = 0x02  // 包类型：数据
                    chunkData[1] = chunkIndex.toByte()  // 块索引

                    // 复制GIF数据
                    System.arraycopy(gifBytes, i, chunkData, 2, currentChunkSize)

                    // 发送数据包，增加重试机制
                    var chunkSent = false
                    for (retry in 0..2) {  // 增加重试次数
                        if (retry > 0) {
                            logd("数据包 $chunkIndex 发送失败，重试 $retry/2")
                        }
                        
                        val sendResult = mBLEController.sendBytesSync(
                            LED_SERVICE_UUID,
                            LED_CHARACTERISTIC_GIF_UUID,
                            chunkData
                        )

                        if (sendResult) {
                            chunkSent = true
                            break
                        } else {
                            // 使用协程delay替代Thread.sleep，短暂延迟后重试
                            delay(50)
                        }
                    }

                    if (chunkSent) {
                        successCount++
                    } else {
                        failCount++
                        logd("数据包 $chunkIndex 发送失败")
                    }

                    chunkIndex++

                    delay(30)

                    // 每发送20个包，额外等待一下
                    if (chunkIndex % 20 == 0) {
                        delay(100)
                        logd("已发送 $chunkIndex 个数据包，成功: $successCount，失败: $failCount")
                    }
                }
                
                logd("GIF字节数据发送完成: $chunkIndex 个数据块")
                
                // 发送成功回调
                if (failCount == 0) {
                    callback?.invoke(true, "GIF发送成功")
                } else {
                    callback?.invoke(false, "部分数据包发送失败")
                }
                
            } catch (e: Exception) {
                logd("发送GIF数据时发生错误: ${e.message}")
                callback?.invoke(false, "发送失败: ${e.message}")
            }
        }
    }

    /**
     * 显示彩色绘制图
     */
    fun drawColorfulCanvas(data: IntArray) {
        // TODO: 待实现
        logd("drawColorfulCanvas not implemented yet")
    }

    fun drawDefault() {
        drawStaticText(LED_DEFAULT_DISPLAY_TEXT)

    }

    fun stopSendGifBytes() {
        CoroutineUtil.getScope("LEDController").cancel()
    }
    
    /**
     * 读取当前PHY
     */
    fun readCurrentPhy() {
        if (mBLEController.isConnected()) {
            mBLEController.readCurrentPhy()
        } else {
            logd("设备未连接，无法读取PHY")
        }
    }
    
    /**
     * 获取当前TX PHY
     */
    fun getCurrentTxPhy(): Int {
        return mBLEController.getCurrentTxPhy()
    }
    
    /**
     * 获取当前RX PHY
     */
    fun getCurrentRxPhy(): Int {
        return mBLEController.getCurrentRxPhy()
    }
    
    /**
     * 检查PHY协商是否成功
     */
    fun isPhyNegotiationSuccessful(): Boolean {
        return mBLEController.isPhyNegotiationSuccessful()
    }
    
    /**
     * 检查设备是否支持2M PHY
     */
    fun isLe2MPhySupported(): Boolean {
        return mBLEController.isLe2MPhySupported()
    }
    
    /**
     * 检查设备是否支持Coded PHY
     */
    fun isLeCodedPhySupported(): Boolean {
        return mBLEController.isLeCodedPhySupported()
    }
    
    /**
     * 获取设备支持的PHY列表
     */
    fun getSupportedPhys(): List<Int> {
        return mBLEController.getSupportedPhys()
    }
    
    /**
     * 获取PHY描述信息
     */
    fun getPhyDescription(phy: Int): String {
        return mBLEController.getPhyDescription(phy)
    }
    
    /**
     * 获取PHY性能描述
     */
    fun getPhyPerformanceDescription(phy: Int): String {
        return mBLEController.getPhyPerformanceDescription(phy)
    }
    
    /**
     * 获取连接质量信息
     */
    fun getConnectionQualityInfo(): String {
        return mBLEController.getConnectionQualityInfo()
    }
    
    // ==================== 配对管理相关方法 ====================
    
    /**
     * 检查当前设备是否已配对
     */
    fun isCurrentDeviceBonded(): Boolean {
        return mBLEController.isCurrentDeviceBonded()
    }
    
    /**
     * 获取配对状态信息
     */
    fun getBondingStatusInfo(): String {
        return mBLEController.getBondingStatusInfo()
    }
    
    /**
     * 获取设备信息摘要
     */
    fun getDeviceInfoSummary(): String {
        return DeviceManager.getDeviceInfoSummary()
    }
    
    /**
     * 手动请求配对（如果需要）
     */
    fun requestBonding() {
        if (mBLEController.isConnected()) {
            val deviceAddress = mBLEController.getDeviceAddress()
            if (deviceAddress.isNotEmpty()) {
                val bluetoothAdapter = VTBluetoothUtil.getBluetoothAdapter(mContext)
                val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
                
                if (device?.bondState != BluetoothDevice.BOND_BONDED) {
                    logd("=== 手动请求配对 ===")
                    logd("设备名称: ${device?.name ?: "未知"}")
                    logd("设备地址: $deviceAddress")
                    
                    val bondResult = device?.createBond()
                    logd("配对请求结果: $bondResult")
                    
                    if (bondResult == true) {
                        logd("配对请求已发送，等待用户确认")
                    } else {
                        logd("配对请求失败")
                    }
                } else {
                    logd("设备已配对，无需再次配对")
                }
            }
        } else {
            logd("设备未连接，无法请求配对")
        }
    }
}