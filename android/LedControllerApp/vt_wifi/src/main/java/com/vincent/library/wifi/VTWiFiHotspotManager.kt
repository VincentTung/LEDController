package com.vincent.library.wifi

import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.vincent.library.base.util.VTCoroutineUtil
import kotlinx.coroutines.*

/**
 * WiFi热点管理器
 * 负责管理WiFi热点扫描、连接、配置等功能
 */
class VTWiFiHotspotManager private constructor() {
    companion object {
        private const val TAG = "WiFiHotspotManager"
        private const val SCOPE_NAME = "WiFiHotspotManager"
        
        @Volatile
        private var INSTANCE: VTWiFiHotspotManager? = null

        fun getInstance(): VTWiFiHotspotManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VTWiFiHotspotManager().also { INSTANCE = it }
            }
        }
    }

    private var context: Context? = null
    private var wifiManager: WifiManager? = null
    private var isInitialized = false

    /**
     * 初始化WiFi热点管理器
     */
    fun init(context: Context) {
        this.context = context.applicationContext
        wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        isInitialized = true
        Log.d(TAG, "WiFi热点管理器初始化完成")
    }

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
     * 扫描WiFi热点
     * @param callback 扫描结果回调
     */
    fun scanHotspots(callback: ((List<WiFiHotspot>) -> Unit)? = null) {
        if (!isInitialized) {
            Log.e(TAG, "WiFi热点管理器未初始化")
            callback?.invoke(emptyList())
            return
        }

        if (!hasLocationPermission()) {
            Log.e(TAG, "缺少位置权限，无法扫描WiFi热点")
            Log.e(TAG, "请授予应用位置权限以扫描WiFi热点")
            callback?.invoke(emptyList())
            return
        }

        VTCoroutineUtil.getScope(SCOPE_NAME).launch {
            try {
                Log.d(TAG, "开始扫描WiFi热点")
                
                // 开始扫描
                val scanSuccess = wifiManager?.startScan() ?: false
                if (!scanSuccess) {
                    Log.e(TAG, "WiFi扫描启动失败")
                    callback?.invoke(emptyList())
                    return@launch
                }
                
                // 等待扫描完成
                delay(5000)
                
                // 获取扫描结果
                val scanResults = wifiManager?.scanResults ?: emptyList()
                Log.d(TAG, "扫描到 ${scanResults.size} 个WiFi热点")
                
                // 转换为WiFiHotspot对象列表
                val hotspots = scanResults.map { result ->
                    WiFiHotspot(
                        ssid = result.SSID,
                        bssid = result.BSSID,
                        capabilities = result.capabilities,
                        frequency = result.frequency,
                        level = result.level,
                        timestamp = result.timestamp
                    )
                }
                
                Log.d(TAG, "WiFi热点扫描完成，找到 ${hotspots.size} 个热点")
                callback?.invoke(hotspots)
                
            } catch (e: Exception) {
                Log.e(TAG, "扫描WiFi热点失败", e)
                callback?.invoke(emptyList())
            }
        }
    }

    /**
     * 扫描ESP32热点
     * @param ssidPrefix ESP32热点名称前缀，默认为"ESP32_LED"
     * @param callback 扫描结果回调
     */
    fun scanESP32Hotspots(
        ssidPrefix: String = "ESP32_LED",
        callback: ((List<WiFiHotspot>) -> Unit)? = null
    ) {
        scanHotspots { hotspots ->
            val esp32Hotspots = hotspots.filter { it.ssid.startsWith(ssidPrefix) }
            Log.d(TAG, "找到 ${esp32Hotspots.size} 个ESP32热点")
            callback?.invoke(esp32Hotspots)
        }
    }

    /**
     * 连接到指定的WiFi热点
     * @param ssid WiFi名称
     * @param password WiFi密码
     * @param callback 连接结果回调
     */
    fun connectToHotspot(
        ssid: String, 
        password: String, 
        callback: ((Boolean, String?) -> Unit)? = null
    ) {
        if (!isInitialized) {
            callback?.invoke(false, "WiFi热点管理器未初始化")
            return
        }

        VTCoroutineUtil.getScope(SCOPE_NAME).launch {
            try {
                Log.d(TAG, "开始连接WiFi热点: $ssid")
                
                val connectSuccess = connectToWiFi(ssid, password)
                if (connectSuccess) {
                    Log.d(TAG, "WiFi热点连接成功: $ssid")
                    callback?.invoke(true, "WiFi热点连接成功")
                } else {
                    Log.e(TAG, "WiFi热点连接失败: $ssid")
                    callback?.invoke(false, "WiFi热点连接失败")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "连接WiFi热点时出错", e)
                callback?.invoke(false, "连接WiFi热点失败: ${e.message}")
            }
        }
    }

    /**
     * 断开当前WiFi连接
     */
    fun disconnect() {
        try {
            wifiManager?.let { wm ->
                val wifiInfo = wm.connectionInfo
                if (wifiInfo != null) {
                    wm.disableNetwork(wifiInfo.networkId)
                    wm.disconnect()
                    Log.d(TAG, "WiFi连接已断开")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "断开WiFi连接时出错", e)
        }
    }

    /**
     * 获取当前连接的WiFi信息
     */
    fun getCurrentConnection(): WiFiHotspot? {
        return try {
            val wifiInfo = wifiManager?.connectionInfo
            if (wifiInfo != null && wifiInfo.ssid.isNotEmpty()) {
                WiFiHotspot(
                    ssid = wifiInfo.ssid.removeSurrounding("\""),
                    bssid = wifiInfo.bssid ?: "",
                    capabilities = "",
                    frequency = 0,
                    level = wifiInfo.rssi,
                    timestamp = System.currentTimeMillis()
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取当前WiFi连接信息失败", e)
            null
        }
    }

    /**
     * 检查是否已连接到WiFi
     */
    fun isConnected(): Boolean {
        return try {
            val wifiInfo = wifiManager?.connectionInfo
            wifiInfo != null && wifiInfo.ssid.isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "检查WiFi连接状态失败", e)
            false
        }
    }

    /**
     * 检查是否连接到ESP32热点
     */
    fun isConnectedToESP32(ssidPrefix: String = "ESP32_LED"): Boolean {
        return try {
            val wifiInfo = wifiManager?.connectionInfo
            wifiInfo != null && wifiInfo.ssid.startsWith("\"$ssidPrefix")
        } catch (e: Exception) {
            Log.e(TAG, "检查ESP32连接状态失败", e)
            false
        }
    }

    /**
     * 获取WiFi信号强度
     */
    fun getSignalStrength(): Int {
        return try {
            val wifiInfo = wifiManager?.connectionInfo
            wifiInfo?.rssi ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "获取WiFi信号强度失败", e)
            0
        }
    }

    /**
     * 获取WiFi连接速度
     */
    fun getConnectionSpeed(): Int {
        return try {
            val wifiInfo = wifiManager?.connectionInfo
            wifiInfo?.linkSpeed ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "获取WiFi连接速度失败", e)
            0
        }
    }

    // ==================== 私有方法 ====================

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
}

/**
 * WiFi热点数据类
 */
data class WiFiHotspot(
    val ssid: String,
    val bssid: String,
    val capabilities: String,
    val frequency: Int,
    val level: Int,
    val timestamp: Long
) {
    /**
     * 获取信号强度百分比 (0-100)
     */
    fun getSignalStrengthPercent(): Int {
        return when {
            level >= -30 -> 100
            level >= -50 -> 80
            level >= -60 -> 60
            level >= -70 -> 40
            level >= -80 -> 20
            else -> 0
        }
    }

    /**
     * 获取信号强度描述
     */
    fun getSignalStrengthDescription(): String {
        return when {
            level >= -30 -> "极强"
            level >= -50 -> "很强"
            level >= -60 -> "强"
            level >= -70 -> "中等"
            level >= -80 -> "弱"
            else -> "极弱"
        }
    }

    /**
     * 检查是否为开放网络
     */
    fun isOpenNetwork(): Boolean {
        return !capabilities.contains("WPA") && !capabilities.contains("WEP")
    }

    /**
     * 检查是否为WPA2网络
     */
    fun isWPA2Network(): Boolean {
        return capabilities.contains("WPA2")
    }

    /**
     * 检查是否为WPA3网络
     */
    fun isWPA3Network(): Boolean {
        return capabilities.contains("WPA3")
    }
}