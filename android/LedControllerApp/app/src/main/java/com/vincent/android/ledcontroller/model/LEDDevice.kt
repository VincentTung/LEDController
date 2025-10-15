package com.vincent.android.ledcontroller.model

import com.vincent.library.ble.model.VTBLEDevice
import com.vincent.library.ble.logic.BLEConnectionState

/**
 * LED设备数据类
 * 包含通用蓝牙设备信息和LED设备特有的属性
 */
data class LEDDevice(
    val bleDevice: VTBLEDevice = VTBLEDevice(),
    val resolution: Resolution = Resolution(),
    val brightness: Int = 50, // 亮度百分比 0-100
    val firmwareVersion: String = "" // 固件版本
) {
    // 代理VTBLEDevice的属性
    val name: String get() = bleDevice.name
    val macAddress: String get() = bleDevice.macAddress
    val connectionState: BLEConnectionState get() = bleDevice.connectionState
    val isBonded: Boolean get() = bleDevice.isBonded
    val mtu: Int get() = bleDevice.mtu
    val txPhy: Int get() = bleDevice.txPhy
    val rxPhy: Int get() = bleDevice.rxPhy
    val lastConnectedTime: Long get() = bleDevice.lastConnectedTime
    
    /**
     * 设备分辨率信息
     */
    data class Resolution(
        val width: Int = 64,
        val height: Int = 32
    ) {
        val totalPixels: Int get() = width * height
        
        override fun toString(): String = "${width}x${height}"
    }
    
    /**
     * 检查设备是否已连接
     */
    val isConnected: Boolean 
        get() = connectionState == BLEConnectionState.CONNECTED || 
                connectionState == BLEConnectionState.DISCOVERING || 
                connectionState == BLEConnectionState.READY
    
    /**
     * 检查设备是否正在连接
     */
    val isConnecting: Boolean 
        get() = connectionState == BLEConnectionState.CONNECTING
    
    /**
     * 检查设备是否有错误
     */
    val hasError: Boolean 
        get() = connectionState == BLEConnectionState.ERROR
    
    /**
     * 获取连接状态描述
     */
    val connectionStateDescription: String
        get() = when (connectionState) {
            BLEConnectionState.DISCONNECTED -> "未连接"
            BLEConnectionState.CONNECTING -> "连接中"
            BLEConnectionState.CONNECTED -> "已连接"
            BLEConnectionState.DISCOVERING -> "服务发现中"
            BLEConnectionState.READY -> "就绪"
            BLEConnectionState.ERROR -> "连接错误"
        }
    
    /**
     * 获取设备信息摘要
     */
    val deviceSummary: String
        get() = buildString {
            append("设备名称: ${if (name.isNotEmpty()) name else "未知"}\n")
            append("MAC地址: ${if (macAddress.isNotEmpty()) macAddress else "未知"}\n")
            append("分辨率: $resolution\n")
            append("亮度: ${brightness}%\n")
            if (firmwareVersion.isNotEmpty()) append("固件版本: $firmwareVersion\n")
            append("连接状态: $connectionStateDescription\n")
            append("配对状态: ${if (isBonded) "已配对" else "未配对"}\n")
            if (mtu > 0) append("MTU: $mtu\n")
            if (txPhy > 0 || rxPhy > 0) append("PHY: TX=$txPhy, RX=$rxPhy\n")
            if (lastConnectedTime > 0) {
                val timeStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                    .format(java.util.Date(lastConnectedTime))
                append("最后连接时间: $timeStr\n")
            }
        }
    
    /**
     * 创建设备副本并更新指定属性
     */
    fun copyWith(
        bleDevice: VTBLEDevice? = null,
        resolution: Resolution? = null,
        brightness: Int? = null,
        firmwareVersion: String? = null
    ): LEDDevice {
        return copy(
            bleDevice = bleDevice ?: this.bleDevice,
            resolution = resolution ?: this.resolution,
            brightness = brightness ?: this.brightness,
            firmwareVersion = firmwareVersion ?: this.firmwareVersion
        )
    }
    
    companion object {
        /**
         * 默认LED设备实例
         */
        fun createDefault(): LEDDevice = LEDDevice()

    }
}