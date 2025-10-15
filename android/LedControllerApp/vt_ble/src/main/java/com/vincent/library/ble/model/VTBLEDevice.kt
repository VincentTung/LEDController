package com.vincent.library.ble.model

import com.vincent.library.ble.logic.BLEConnectionState

/**
 * 通用蓝牙设备基类
 * 包含所有蓝牙设备共有的属性和状态
 */
open class VTBLEDevice(
    val name: String = "",
    val macAddress: String = "",
    val connectionState: BLEConnectionState = BLEConnectionState.DISCONNECTED,
    val isBonded: Boolean = false,
    val mtu: Int = 0,
    val txPhy: Int = 0,
    val rxPhy: Int = 0,
    val lastConnectedTime: Long = 0L
) {
    /**
     * 连接状态描述
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
     * 是否已连接
     */
    val isConnected: Boolean
        get() = connectionState == BLEConnectionState.CONNECTED || 
                connectionState == BLEConnectionState.DISCOVERING || 
                connectionState == BLEConnectionState.READY

    /**
     * 是否正在连接
     */
    val isConnecting: Boolean
        get() = connectionState == BLEConnectionState.CONNECTING

    /**
     * 是否有错误
     */
    val hasError: Boolean
        get() = connectionState == BLEConnectionState.ERROR

    /**
     * 设备摘要信息
     */
    val deviceSummary: String
        get() = buildString {
            append("设备名称: ${if (name.isNotEmpty()) name else "未知"}\n")
            append("MAC地址: ${if (macAddress.isNotEmpty()) macAddress else "未知"}\n")
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
     * 创建默认设备
     */
    companion object {
        fun createDefault(): VTBLEDevice {
            return VTBLEDevice()
        }
    }

    /**
     * 复制并更新属性
     */
    fun copyWith(
        name: String? = null,
        macAddress: String? = null,
        connectionState: BLEConnectionState? = null,
        isBonded: Boolean? = null,
        mtu: Int? = null,
        txPhy: Int? = null,
        rxPhy: Int? = null,
        lastConnectedTime: Long? = null
    ): VTBLEDevice {
        return VTBLEDevice(
            name = name ?: this.name,
            macAddress = macAddress ?: this.macAddress,
            connectionState = connectionState ?: this.connectionState,
            isBonded = isBonded ?: this.isBonded,
            mtu = mtu ?: this.mtu,
            txPhy = txPhy ?: this.txPhy,
            rxPhy = rxPhy ?: this.rxPhy,
            lastConnectedTime = lastConnectedTime ?: this.lastConnectedTime
        )
    }
}
