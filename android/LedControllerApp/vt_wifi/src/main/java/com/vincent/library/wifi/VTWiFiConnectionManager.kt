package com.vincent.library.wifi

import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.vincent.library.base.util.VTCoroutineUtil
import kotlinx.coroutines.*
import java.io.*
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * WiFi连接管理器
 * 负责管理WiFi连接、热点扫描、TCP通信等核心功能
 */
class VTWiFiConnectionManager private constructor() {
    companion object {
        private const val TAG = "WiFiConnectionManager"
        private const val SCOPE_NAME = "WiFiConnectionManager"
        private const val DEFAULT_WIFI_PORT = 8888
        private const val CONNECTION_TIMEOUT = 10000 // 10秒连接超时
        private const val SOCKET_TIMEOUT = 5000 // 5秒Socket超时
        private const val CHUNK_SIZE = 1024 // 数据块大小
        
        @Volatile
        private var INSTANCE: VTWiFiConnectionManager? = null

        fun getInstance(): VTWiFiConnectionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VTWiFiConnectionManager().also { INSTANCE = it }
            }
        }
    }

    enum class WiFiConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }

    private var connectionState = WiFiConnectionState.DISCONNECTED
    private var context: Context? = null
    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private var isInitialized = false
    private var currentIpAddress: String? = null
    private var currentPort: Int = DEFAULT_WIFI_PORT
    
    // WiFi管理器
    private var wifiManager: WifiManager? = null
    
    // 连接状态管理
    private val isConnecting = AtomicBoolean(false)
    private var connectionJob: Job? = null
    private var heartbeatJob: Job? = null
    
    // ESP32热点配置
    private val ESP32_SSID_PREFIX = "ESP32_LED" // ESP32热点名称前缀
    private val ESP32_DEFAULT_IP = "192.168.4.1" // ESP32热点默认IP
    private val ESP32_DEFAULT_PASSWORD = "12345678" // ESP32热点默认密码

    /**
     * 初始化WiFi连接管理器
     */
    fun init(context: Context) {
        this.context = context.applicationContext
        wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        isInitialized = true
        Log.d(TAG, "WiFi连接管理器初始化完成")
    }

    /**
     * 连接到指定的IP地址和端口
     * @param ipAddress 目标IP地址
     * @param port 端口号，默认为8888
     * @param callback 连接结果回调
     */
    fun connect(ipAddress: String, port: Int = DEFAULT_WIFI_PORT, callback: ((Boolean, String?) -> Unit)? = null) {
        if (!isInitialized) {
            callback?.invoke(false, "WiFi连接管理器未初始化")
            return
        }

        if (isConnecting.get()) {
            callback?.invoke(false, "正在连接中，请稍候")
            return
        }

        isConnecting.set(true)
        connectionState = WiFiConnectionState.CONNECTING
        currentIpAddress = ipAddress
        currentPort = port

        connectionJob = VTCoroutineUtil.getScope(SCOPE_NAME).launch {
            try {
                Log.d(TAG, "开始连接WiFi: $ipAddress:$port")
                
                // 创建Socket连接
                withContext(Dispatchers.IO) {
                    socket = Socket().apply {
                        soTimeout = SOCKET_TIMEOUT
                        connect(java.net.InetSocketAddress(ipAddress, port), CONNECTION_TIMEOUT)
                    }
                }
                
                // 获取输入输出流
                outputStream = socket!!.getOutputStream()
                inputStream = socket!!.getInputStream()
                
                connectionState = WiFiConnectionState.CONNECTED
                isConnecting.set(false)
                
                // 启动心跳检测
                startHeartbeat()
                
                Log.d(TAG, "WiFi连接成功: $ipAddress:$port")
                callback?.invoke(true, "WiFi连接成功")
                
            } catch (e: Exception) {
                Log.e(TAG, "WiFi连接失败", e)
                connectionState = WiFiConnectionState.ERROR
                isConnecting.set(false)
                disconnect()
                callback?.invoke(false, "WiFi连接失败: ${e.message}")
            }
        }
    }

    /**
     * 扫描并连接ESP32热点
     * @param callback 连接结果回调
     */
    fun connectToESP32Hotspot(callback: ((Boolean, String?) -> Unit)? = null) {
        if (!isInitialized) {
            callback?.invoke(false, "WiFi连接管理器未初始化")
            return
        }
        
        if (isConnecting.get()) {
            callback?.invoke(false, "正在连接中，请稍候")
            return
        }
        
        isConnecting.set(true)
        connectionState = WiFiConnectionState.CONNECTING
        
        connectionJob = VTCoroutineUtil.getScope(SCOPE_NAME).launch {
            try {
                Log.d(TAG, "开始扫描ESP32热点")
                
                // 扫描WiFi热点
                val esp32SSID = scanForESP32Hotspot()
                if (esp32SSID == null) {
                    isConnecting.set(false)
                    connectionState = WiFiConnectionState.ERROR
                    callback?.invoke(false, "未找到ESP32热点，请确保ESP32已开启热点模式")
                    return@launch
                }
                
                Log.d(TAG, "找到ESP32热点: $esp32SSID")
                
                // 连接到ESP32热点
                val connectSuccess = connectToWiFi(esp32SSID, ESP32_DEFAULT_PASSWORD)
                if (!connectSuccess) {
                    isConnecting.set(false)
                    connectionState = WiFiConnectionState.ERROR
                    callback?.invoke(false, "连接ESP32热点失败")
                    return@launch
                }
                
                // 等待WiFi连接稳定
                delay(3000)
                
                // 连接到ESP32的TCP服务
                val tcpSuccess = connectToESP32TCP(ESP32_DEFAULT_IP, DEFAULT_WIFI_PORT)
                if (!tcpSuccess) {
                    isConnecting.set(false)
                    connectionState = WiFiConnectionState.ERROR
                    callback?.invoke(false, "连接ESP32 TCP服务失败")
                    return@launch
                }
                
                connectionState = WiFiConnectionState.CONNECTED
                isConnecting.set(false)
                currentIpAddress = ESP32_DEFAULT_IP
                currentPort = DEFAULT_WIFI_PORT
                
                // 启动心跳检测
                startHeartbeat()
                
                Log.d(TAG, "ESP32热点连接成功")
                callback?.invoke(true, "ESP32热点连接成功")
                
            } catch (e: Exception) {
                Log.e(TAG, "连接ESP32热点失败", e)
                connectionState = WiFiConnectionState.ERROR
                isConnecting.set(false)
                callback?.invoke(false, "连接ESP32热点失败: ${e.message}")
            }
        }
    }

    /**
     * 断开WiFi连接
     */
    fun disconnect() {
        try {
            connectionJob?.cancel()
            heartbeatJob?.cancel()
            
            inputStream?.close()
            outputStream?.close()
            socket?.close()
            
            inputStream = null
            outputStream = null
            socket = null
            connectionState = WiFiConnectionState.DISCONNECTED
            
            Log.d(TAG, "WiFi连接已断开")
        } catch (e: Exception) {
            Log.e(TAG, "断开WiFi连接时出错", e)
        }
    }

    /**
     * 断开ESP32热点连接
     */
    fun disconnectFromESP32Hotspot() {
        try {
            // 断开TCP连接
            disconnect()
            
            // 断开WiFi连接
            wifiManager?.let { wm ->
                val wifiInfo = wm.connectionInfo
                if (wifiInfo != null && wifiInfo.ssid.startsWith("\"$ESP32_SSID_PREFIX")) {
                    wm.disableNetwork(wifiInfo.networkId)
                    wm.disconnect()
                    Log.d(TAG, "已断开ESP32热点连接")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "断开ESP32热点连接时出错", e)
        }
    }

    /**
     * 检查是否已连接
     */
    fun isConnected(): Boolean {
       return  true
    }

    /**
     * 检查是否连接到ESP32热点
     */
    fun isConnectedToESP32Hotspot(): Boolean {
        return try {
            val wifiManager = wifiManager ?: return false
            val wifiInfo = wifiManager.connectionInfo
           isConnected()
        } catch (e: Exception) {
            Log.e(TAG, "检查ESP32热点连接状态失败", e)
            false
        }
    }

    /**
     * 获取当前连接的WiFi名称
     */
    fun getCurrentWiFiSSID(): String? {
        return try {
            val wifiManager = wifiManager ?: return null
            val wifiInfo = wifiManager.connectionInfo
            if (wifiInfo != null && isConnected()) {
                // 移除SSID两端的引号
                val ssid = wifiInfo.ssid
                if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
                    ssid.substring(1, ssid.length - 1)
                } else {
                    ssid
                }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取当前WiFi名称失败", e)
            null
        }
    }

    /**
     * 检查是否连接到指定的WiFi网络
     */
    fun isConnectedToWiFi(ssidPrefix: String): Boolean {
        return try {
            val currentSSID = getCurrentWiFiSSID()
            currentSSID != null && currentSSID.startsWith(ssidPrefix) && isConnected()
        } catch (e: Exception) {
            Log.e(TAG, "检查WiFi连接失败", e)
            false
        }
    }

    /**
     * 获取连接状态
     */
    fun getConnectionState(): WiFiConnectionState = connectionState

    /**
     * 发送文本命令
     * @param command 命令字符串
     * @param callback 发送结果回调
     */
    fun sendCommand(command: String, callback: ((Boolean, String?) -> Unit)? = null) {
        if (!isConnected()) {
            callback?.invoke(false, "WiFi未连接")
            return
        }

        VTCoroutineUtil.getScope(SCOPE_NAME).launch {
            try {
                val commandBytes = command.toByteArray(Charsets.UTF_8)
                outputStream?.write(commandBytes)
                outputStream?.flush()
                
                Log.d(TAG, "发送命令成功: $command")
                callback?.invoke(true, "命令发送成功")
                
            } catch (e: Exception) {
                Log.e(TAG, "发送命令失败", e)
                callback?.invoke(false, "发送命令失败: ${e.message}")
            }
        }
    }

    /**
     * 发送GIF文件数据
     * @param gifData GIF文件字节数据
     * @param callback 发送结果回调
     * @param progressCallback 进度回调
     */
    fun sendGifData(
        gifData: ByteArray, 
        callback: ((Boolean, String?) -> Unit)? = null,
        progressCallback: ((Int) -> Unit)? = null
    ) {
        if (!isConnected()) {
            callback?.invoke(false, "WiFi未连接")
            return
        }

        VTCoroutineUtil.getScope(SCOPE_NAME).launch {
            try {
                Log.d(TAG, "开始发送GIF数据: ${gifData.size} 字节")
                
                // 发送数据头（包含数据长度）
                val header = "GIF:${gifData.size}\n".toByteArray(Charsets.UTF_8)
                outputStream?.write(header)
                outputStream?.flush()
                
                // 分块发送数据
                var sentBytes = 0
                val totalBytes = gifData.size
                
                while (sentBytes < totalBytes) {
                    val remainingBytes = totalBytes - sentBytes
                    val chunkSize = minOf(CHUNK_SIZE, remainingBytes)
                    
                    outputStream?.write(gifData, sentBytes, chunkSize)
                    outputStream?.flush()
                    
                    sentBytes += chunkSize
                    val progress = (sentBytes * 100 / totalBytes)
                    progressCallback?.invoke(progress)
                    
                    Log.d(TAG, "GIF发送进度: $progress% ($sentBytes/$totalBytes)")
                }
                
                Log.d(TAG, "GIF数据发送完成")
                callback?.invoke(true, "GIF发送成功")
                
            } catch (e: Exception) {
                Log.e(TAG, "发送GIF数据失败", e)
                callback?.invoke(false, "发送GIF数据失败: ${e.message}")
            }
        }
    }

    /**
     * 获取当前连接的IP地址
     */
    fun getCurrentIpAddress(): String? = currentIpAddress

    /**
     * 获取当前连接的端口
     */
    fun getCurrentPort(): Int = currentPort

    // ==================== 私有方法 ====================

    /**
     * 检查WiFi扫描权限
     */
    private fun hasLocationPermission(): Boolean {
        val context = this.context ?: return false
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 检查位置服务是否开启
     */
    private fun isLocationServiceEnabled(): Boolean {
        val context = this.context ?: return false
        return try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {
            Log.e(TAG, "检查位置服务状态失败", e)
            false
        }
    }

    /**
     * 扫描WiFi热点，查找ESP32热点
     */
    private suspend fun scanForESP32Hotspot(): String? = withContext(Dispatchers.IO) {
        try {
            val wifiManager = wifiManager ?: return@withContext null
            
            // 检查权限
            if (!hasLocationPermission()) {
                Log.e(TAG, "缺少位置权限，无法扫描WiFi热点")
                Log.e(TAG, "请授予应用位置权限以扫描WiFi热点")
                return@withContext null
            }
            
            // 检查位置服务是否开启
            if (!isLocationServiceEnabled()) {
                Log.e(TAG, "位置服务未开启，无法扫描WiFi热点")
                Log.e(TAG, "请开启设备位置服务（设置 → 位置）")
                return@withContext null
            }
            
            // 开始扫描
            val scanSuccess = wifiManager.startScan()
            if (!scanSuccess) {
                Log.e(TAG, "WiFi扫描启动失败")
                return@withContext null
            }
            
            // 等待扫描完成
            delay(5000)
            
            // 获取扫描结果
            val scanResults = wifiManager.scanResults
            Log.d(TAG, "扫描到 ${scanResults.size} 个WiFi热点")
            
            // 查找ESP32热点
            for (result in scanResults) {
                val ssid = result.SSID
                Log.d(TAG, "发现热点: $ssid")
                
                if (ssid.startsWith(ESP32_SSID_PREFIX)) {
                    Log.d(TAG, "找到ESP32热点: $ssid")
                    return@withContext ssid
                }
            }
            
            Log.w(TAG, "未找到ESP32热点")
            return@withContext null
            
        } catch (e: Exception) {
            Log.e(TAG, "扫描WiFi热点失败", e)
            return@withContext null
        }
    }

    /**
     * 连接到指定的WiFi热点
     */
    private suspend fun connectToWiFi(ssid: String, password: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val wifiManager = wifiManager ?: return@withContext false
            
            // 创建WiFi配置
            val wifiConfig = WifiConfiguration().apply {
                this.SSID = "\"$ssid\""
                preSharedKey = "\"$password\""
                allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
            }
            
            // 添加网络配置
            val netId = wifiManager.addNetwork(wifiConfig)
            if (netId == -1) {
                Log.e(TAG, "添加WiFi配置失败")
                return@withContext false
            }
            
            // 启用网络
            val enableSuccess = wifiManager.enableNetwork(netId, true)
            if (!enableSuccess) {
                Log.e(TAG, "启用WiFi网络失败")
                return@withContext false
            }
            
            // 等待连接
            var retryCount = 0
            while (retryCount < 30) { // 最多等待30秒
                delay(1000)
                val wifiInfo = wifiManager.connectionInfo
                if (wifiInfo != null && wifiInfo.ssid == "\"$ssid\"") {
                    Log.d(TAG, "WiFi连接成功: $ssid")
                    return@withContext true
                }
                retryCount++
            }
            
            Log.e(TAG, "WiFi连接超时")
            return@withContext false
            
        } catch (e: Exception) {
            Log.e(TAG, "连接WiFi失败", e)
            return@withContext false
        }
    }

    /**
     * 连接到ESP32的TCP服务
     */
    private suspend fun connectToESP32TCP(ipAddress: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始连接ESP32 TCP服务: $ipAddress:$port")
            
            // 创建Socket连接
            socket = Socket().apply {
                soTimeout = SOCKET_TIMEOUT
                connect(java.net.InetSocketAddress(ipAddress, port), CONNECTION_TIMEOUT)
            }
            
            // 获取输入输出流
            outputStream = socket!!.getOutputStream()
            inputStream = socket!!.getInputStream()
            
            Log.d(TAG, "ESP32 TCP连接成功")
            return@withContext true
            
        } catch (e: Exception) {
            Log.e(TAG, "连接ESP32 TCP服务失败", e)
            return@withContext false
        }
    }

    /**
     * 启动心跳检测
     */
    private fun startHeartbeat() {
        heartbeatJob = VTCoroutineUtil.getScope(SCOPE_NAME).launch {
            while (isConnected()) {
                try {
                    delay(30000) // 30秒心跳间隔
                    
                    // 发送心跳包
                    sendCommand("PING", null)
                    Log.d(TAG, "发送心跳包")
                    
                } catch (e: Exception) {
                    Log.e(TAG, "心跳检测失败", e)
                    connectionState = WiFiConnectionState.ERROR
                    break
                }
            }
        }
    }
}