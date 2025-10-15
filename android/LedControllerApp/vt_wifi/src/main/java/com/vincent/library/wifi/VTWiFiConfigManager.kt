package com.vincent.library.wifi

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * WiFi配置管理器
 * 负责管理WiFi相关的配置信息，包括连接参数、传输设置等
 */
class VTWiFiConfigManager private constructor() {
    companion object {
        private const val TAG = "WiFiConfigManager"
        private const val PREF_NAME = "wifi_config"
        
        // 配置键名
        private const val KEY_WIFI_ENABLED = "wifi_enabled"
        private const val KEY_IP_ADDRESS = "ip_address"
        private const val KEY_PORT = "port"
        private const val KEY_SSID_PREFIX = "ssid_prefix"
        private const val KEY_DEFAULT_PASSWORD = "default_password"
        private const val KEY_WIFI_THRESHOLD = "wifi_threshold"
        private const val KEY_TRANSMISSION_MODE = "transmission_mode"
        private const val KEY_AUTO_CONNECT = "auto_connect"
        private const val KEY_CONNECTION_TIMEOUT = "connection_timeout"
        private const val KEY_SOCKET_TIMEOUT = "socket_timeout"
        private const val KEY_CHUNK_SIZE = "chunk_size"
        private const val KEY_HEARTBEAT_INTERVAL = "heartbeat_interval"
        
        // 默认值
        private const val DEFAULT_IP_ADDRESS = "192.168.4.1"
        private const val DEFAULT_PORT = 8888
        private const val DEFAULT_SSID_PREFIX = "ESP32_LED"
        private const val DEFAULT_PASSWORD = "12345678"
        private const val DEFAULT_WIFI_THRESHOLD = 10 * 1024 // 10KB
        private const val DEFAULT_TRANSMISSION_MODE = "AUTO"
        private const val DEFAULT_AUTO_CONNECT = false
        private const val DEFAULT_CONNECTION_TIMEOUT = 10000 // 10秒
        private const val DEFAULT_SOCKET_TIMEOUT = 5000 // 5秒
        private const val DEFAULT_CHUNK_SIZE = 1024 // 1KB
        private const val DEFAULT_HEARTBEAT_INTERVAL = 30000 // 30秒
        
        @Volatile
        private var INSTANCE: VTWiFiConfigManager? = null

        fun getInstance(): VTWiFiConfigManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VTWiFiConfigManager().also { INSTANCE = it }
            }
        }
    }

    private var context: Context? = null
    private var sharedPreferences: SharedPreferences? = null
    private var isInitialized = false

    /**
     * 初始化WiFi配置管理器
     */
    fun init(context: Context) {
        this.context = context.applicationContext
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        isInitialized = true
        Log.d(TAG, "WiFi配置管理器初始化完成")
    }

    // ==================== WiFi连接配置 ====================

    /**
     * 设置WiFi是否启用
     */
    fun setWiFiEnabled(enabled: Boolean) {
        if (!isInitialized) return
        sharedPreferences?.edit()?.putBoolean(KEY_WIFI_ENABLED, enabled)?.apply()
        Log.d(TAG, "WiFi启用状态设置为: $enabled")
    }

    /**
     * 获取WiFi是否启用
     */
    fun isWiFiEnabled(): Boolean {
        return sharedPreferences?.getBoolean(KEY_WIFI_ENABLED, true) ?: true
    }

    /**
     * 设置IP地址
     */
    fun setIpAddress(ipAddress: String) {
        if (!isInitialized) return
        sharedPreferences?.edit()?.putString(KEY_IP_ADDRESS, ipAddress)?.apply()
        Log.d(TAG, "IP地址设置为: $ipAddress")
    }

    /**
     * 获取IP地址
     */
    fun getIpAddress(): String {
        return sharedPreferences?.getString(KEY_IP_ADDRESS, DEFAULT_IP_ADDRESS) ?: DEFAULT_IP_ADDRESS
    }

    /**
     * 设置端口号
     */
    fun setPort(port: Int) {
        if (!isInitialized) return
        sharedPreferences?.edit()?.putInt(KEY_PORT, port)?.apply()
        Log.d(TAG, "端口号设置为: $port")
    }

    /**
     * 获取端口号
     */
    fun getPort(): Int {
        return sharedPreferences?.getInt(KEY_PORT, DEFAULT_PORT) ?: DEFAULT_PORT
    }

    // ==================== ESP32热点配置 ====================

    /**
     * 设置ESP32热点SSID前缀
     */
    fun setSsidPrefix(prefix: String) {
        if (!isInitialized) return
        sharedPreferences?.edit()?.putString(KEY_SSID_PREFIX, prefix)?.apply()
        Log.d(TAG, "SSID前缀设置为: $prefix")
    }

    /**
     * 获取ESP32热点SSID前缀
     */
    fun getSsidPrefix(): String {
        return sharedPreferences?.getString(KEY_SSID_PREFIX, DEFAULT_SSID_PREFIX) ?: DEFAULT_SSID_PREFIX
    }

    /**
     * 设置默认密码
     */
    fun setDefaultPassword(password: String) {
        if (!isInitialized) return
        sharedPreferences?.edit()?.putString(KEY_DEFAULT_PASSWORD, password)?.apply()
        Log.d(TAG, "默认密码设置为: $password")
    }

    /**
     * 获取默认密码
     */
    fun getDefaultPassword(): String {
        return sharedPreferences?.getString(KEY_DEFAULT_PASSWORD, DEFAULT_PASSWORD) ?: DEFAULT_PASSWORD
    }

    // ==================== 传输配置 ====================

    /**
     * 设置WiFi传输阈值
     */
    fun setWiFiThreshold(threshold: Int) {
        if (!isInitialized) return
        sharedPreferences?.edit()?.putInt(KEY_WIFI_THRESHOLD, threshold)?.apply()
        Log.d(TAG, "WiFi传输阈值设置为: $threshold 字节")
    }

    /**
     * 获取WiFi传输阈值
     */
    fun getWiFiThreshold(): Int {
        return sharedPreferences?.getInt(KEY_WIFI_THRESHOLD, DEFAULT_WIFI_THRESHOLD) ?: DEFAULT_WIFI_THRESHOLD
    }

    /**
     * 设置传输模式
     */
    fun setTransmissionMode(mode: String) {
        if (!isInitialized) return
        sharedPreferences?.edit()?.putString(KEY_TRANSMISSION_MODE, mode)?.apply()
        Log.d(TAG, "传输模式设置为: $mode")
    }

    /**
     * 获取传输模式
     */
    fun getTransmissionMode(): String {
        return sharedPreferences?.getString(KEY_TRANSMISSION_MODE, DEFAULT_TRANSMISSION_MODE) ?: DEFAULT_TRANSMISSION_MODE
    }

    /**
     * 设置是否自动连接
     */
    fun setAutoConnect(autoConnect: Boolean) {
        if (!isInitialized) return
        sharedPreferences?.edit()?.putBoolean(KEY_AUTO_CONNECT, autoConnect)?.apply()
        Log.d(TAG, "自动连接设置为: $autoConnect")
    }

    /**
     * 获取是否自动连接
     */
    fun isAutoConnect(): Boolean {
        return sharedPreferences?.getBoolean(KEY_AUTO_CONNECT, DEFAULT_AUTO_CONNECT) ?: DEFAULT_AUTO_CONNECT
    }

    // ==================== 超时配置 ====================

    /**
     * 设置连接超时时间
     */
    fun setConnectionTimeout(timeout: Int) {
        if (!isInitialized) return
        sharedPreferences?.edit()?.putInt(KEY_CONNECTION_TIMEOUT, timeout)?.apply()
        Log.d(TAG, "连接超时时间设置为: $timeout 毫秒")
    }

    /**
     * 获取连接超时时间
     */
    fun getConnectionTimeout(): Int {
        return sharedPreferences?.getInt(KEY_CONNECTION_TIMEOUT, DEFAULT_CONNECTION_TIMEOUT) ?: DEFAULT_CONNECTION_TIMEOUT
    }

    /**
     * 设置Socket超时时间
     */
    fun setSocketTimeout(timeout: Int) {
        if (!isInitialized) return
        sharedPreferences?.edit()?.putInt(KEY_SOCKET_TIMEOUT, timeout)?.apply()
        Log.d(TAG, "Socket超时时间设置为: $timeout 毫秒")
    }

    /**
     * 获取Socket超时时间
     */
    fun getSocketTimeout(): Int {
        return sharedPreferences?.getInt(KEY_SOCKET_TIMEOUT, DEFAULT_SOCKET_TIMEOUT) ?: DEFAULT_SOCKET_TIMEOUT
    }

    // ==================== 传输参数配置 ====================

    /**
     * 设置数据块大小
     */
    fun setChunkSize(size: Int) {
        if (!isInitialized) return
        sharedPreferences?.edit()?.putInt(KEY_CHUNK_SIZE, size)?.apply()
        Log.d(TAG, "数据块大小设置为: $size 字节")
    }

    /**
     * 获取数据块大小
     */
    fun getChunkSize(): Int {
        return sharedPreferences?.getInt(KEY_CHUNK_SIZE, DEFAULT_CHUNK_SIZE) ?: DEFAULT_CHUNK_SIZE
    }

    /**
     * 设置心跳间隔
     */
    fun setHeartbeatInterval(interval: Int) {
        if (!isInitialized) return
        sharedPreferences?.edit()?.putInt(KEY_HEARTBEAT_INTERVAL, interval)?.apply()
        Log.d(TAG, "心跳间隔设置为: $interval 毫秒")
    }

    /**
     * 获取心跳间隔
     */
    fun getHeartbeatInterval(): Int {
        return sharedPreferences?.getInt(KEY_HEARTBEAT_INTERVAL, DEFAULT_HEARTBEAT_INTERVAL) ?: DEFAULT_HEARTBEAT_INTERVAL
    }

    // ==================== 配置管理 ====================

    /**
     * 重置所有配置为默认值
     */
    fun resetToDefaults() {
        if (!isInitialized) return
        
        sharedPreferences?.edit()?.apply {
            putBoolean(KEY_WIFI_ENABLED, true)
            putString(KEY_IP_ADDRESS, DEFAULT_IP_ADDRESS)
            putInt(KEY_PORT, DEFAULT_PORT)
            putString(KEY_SSID_PREFIX, DEFAULT_SSID_PREFIX)
            putString(KEY_DEFAULT_PASSWORD, DEFAULT_PASSWORD)
            putInt(KEY_WIFI_THRESHOLD, DEFAULT_WIFI_THRESHOLD)
            putString(KEY_TRANSMISSION_MODE, DEFAULT_TRANSMISSION_MODE)
            putBoolean(KEY_AUTO_CONNECT, DEFAULT_AUTO_CONNECT)
            putInt(KEY_CONNECTION_TIMEOUT, DEFAULT_CONNECTION_TIMEOUT)
            putInt(KEY_SOCKET_TIMEOUT, DEFAULT_SOCKET_TIMEOUT)
            putInt(KEY_CHUNK_SIZE, DEFAULT_CHUNK_SIZE)
            putInt(KEY_HEARTBEAT_INTERVAL, DEFAULT_HEARTBEAT_INTERVAL)
            apply()
        }
        
        Log.d(TAG, "配置已重置为默认值")
    }

    /**
     * 获取所有配置信息
     */
    fun getAllConfig(): Map<String, Any> {
        return mapOf(
            "wifiEnabled" to isWiFiEnabled(),
            "ipAddress" to getIpAddress(),
            "port" to getPort(),
            "ssidPrefix" to getSsidPrefix(),
            "defaultPassword" to getDefaultPassword(),
            "wifiThreshold" to getWiFiThreshold(),
            "transmissionMode" to getTransmissionMode(),
            "autoConnect" to isAutoConnect(),
            "connectionTimeout" to getConnectionTimeout(),
            "socketTimeout" to getSocketTimeout(),
            "chunkSize" to getChunkSize(),
            "heartbeatInterval" to getHeartbeatInterval()
        )
    }

    /**
     * 清除所有配置
     */
    fun clearAllConfig() {
        if (!isInitialized) return
        sharedPreferences?.edit()?.clear()?.apply()
        Log.d(TAG, "所有配置已清除")
    }

    /**
     * 导出配置到JSON字符串
     */
    fun exportConfig(): String {
        val config = getAllConfig()
        return org.json.JSONObject(config).toString()
    }

    /**
     * 从JSON字符串导入配置
     */
    fun importConfig(jsonString: String): Boolean {
        return try {
            val jsonObject = org.json.JSONObject(jsonString)
            
            sharedPreferences?.edit()?.apply {
                putBoolean(KEY_WIFI_ENABLED, jsonObject.optBoolean("wifiEnabled", true))
                putString(KEY_IP_ADDRESS, jsonObject.optString("ipAddress", DEFAULT_IP_ADDRESS))
                putInt(KEY_PORT, jsonObject.optInt("port", DEFAULT_PORT))
                putString(KEY_SSID_PREFIX, jsonObject.optString("ssidPrefix", DEFAULT_SSID_PREFIX))
                putString(KEY_DEFAULT_PASSWORD, jsonObject.optString("defaultPassword", DEFAULT_PASSWORD))
                putInt(KEY_WIFI_THRESHOLD, jsonObject.optInt("wifiThreshold", DEFAULT_WIFI_THRESHOLD))
                putString(KEY_TRANSMISSION_MODE, jsonObject.optString("transmissionMode", DEFAULT_TRANSMISSION_MODE))
                putBoolean(KEY_AUTO_CONNECT, jsonObject.optBoolean("autoConnect", DEFAULT_AUTO_CONNECT))
                putInt(KEY_CONNECTION_TIMEOUT, jsonObject.optInt("connectionTimeout", DEFAULT_CONNECTION_TIMEOUT))
                putInt(KEY_SOCKET_TIMEOUT, jsonObject.optInt("socketTimeout", DEFAULT_SOCKET_TIMEOUT))
                putInt(KEY_CHUNK_SIZE, jsonObject.optInt("chunkSize", DEFAULT_CHUNK_SIZE))
                putInt(KEY_HEARTBEAT_INTERVAL, jsonObject.optInt("heartbeatInterval", DEFAULT_HEARTBEAT_INTERVAL))
                apply()
            }
            
            Log.d(TAG, "配置导入成功")
            true
        } catch (e: Exception) {
            Log.e(TAG, "配置导入失败", e)
            false
        }
    }
}