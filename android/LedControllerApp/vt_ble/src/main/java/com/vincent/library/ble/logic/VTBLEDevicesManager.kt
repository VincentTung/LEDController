package com.vincent.library.ble.logic

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.provider.Settings.Global.DEVICE_NAME
import com.vincent.library.base.util.logd
import com.vincent.library.base.util.loge
import com.vincent.library.base.util.VTMMKVUtil
import com.vincent.library.ble.config.MAX_RETRY_COUNT

/**
 * 设备管理器 - 负责保存和获取已连接ble设备的信息，管理配对状态
 */
object VTBLEDevicesManager {
    private const val MMKV_ID = "device_manager"
    private const val KEY_DEVICE_NAME = "last_connected_device_name"
    private const val KEY_DEVICE_ADDRESS = "last_connected_device_address"
    private const val KEY_DEVICE_BOND_STATE = "last_connected_device_bond_state"
    private const val KEY_DEVICE_LAST_CONNECT_TIME = "last_connected_device_time"
    private const val KEY_DEVICE_CONNECT_COUNT = "last_connected_device_count"

    /**
     * 初始化MMKV
     */
    fun init(context: Context) {
        try {
            VTMMKVUtil.init(context)
            logd("=== DeviceManager: 初始化完成 ===")
            logd("使用MMKV实例ID: $MMKV_ID")
        } catch (e: Exception) {
            loge(message = "DeviceManager 初始化失败: ${e.message}")
        }
    }

    /**
     * 保存已连接的设备信息（包含配对状态）
     */
    fun saveConnectedDevice(device: BluetoothDevice) {
        try {
            val currentTime = System.currentTimeMillis()
            val currentCount = VTMMKVUtil.getInt(MMKV_ID, KEY_DEVICE_CONNECT_COUNT, 0)
            
            // 保存设备信息
            VTMMKVUtil.putString(MMKV_ID, KEY_DEVICE_NAME, device.name ?: "")
            VTMMKVUtil.putString(MMKV_ID, KEY_DEVICE_ADDRESS, device.address)
            VTMMKVUtil.putInt(MMKV_ID, KEY_DEVICE_BOND_STATE, device.bondState)
            VTMMKVUtil.putLong(MMKV_ID, KEY_DEVICE_LAST_CONNECT_TIME, currentTime)
            VTMMKVUtil.putInt(MMKV_ID, KEY_DEVICE_CONNECT_COUNT, currentCount + 1)

            logd("=== DeviceManager: 保存设备信息 ===")
            logd("设备名称: ${device.name ?: "未知"}")
            logd("设备地址: ${device.address}")
            logd("配对状态: ${getBondStateDescription(device.bondState)}")
            logd("连接次数: ${currentCount + 1}")
            logd("设备信息已保存到本地存储")
        } catch (e: Exception) {
            loge(message = "保存设备信息失败: ${e.message}")
        }
    }

    /**
     * 保存已连接的设备信息（兼容旧版本）
     */
    fun saveConnectedDevice(deviceName: String?, deviceAddress: String) {
        try {
            val currentTime = System.currentTimeMillis()
            val currentCount = VTMMKVUtil.getInt(MMKV_ID, KEY_DEVICE_CONNECT_COUNT, 0)
            
            // 保存设备信息
            VTMMKVUtil.putString(MMKV_ID, KEY_DEVICE_NAME, deviceName ?: "")
            VTMMKVUtil.putString(MMKV_ID, KEY_DEVICE_ADDRESS, deviceAddress)
            VTMMKVUtil.putLong(MMKV_ID, KEY_DEVICE_LAST_CONNECT_TIME, currentTime)
            VTMMKVUtil.putInt(MMKV_ID, KEY_DEVICE_CONNECT_COUNT, currentCount + 1)

            logd("=== DeviceManager: 保存设备信息 ===")
            logd("设备名称: ${deviceName ?: "未知"}")
            logd("设备地址: $deviceAddress")
            logd("连接次数: ${currentCount + 1}")
            logd("设备信息已保存到本地存储")
        } catch (e: Exception) {
            loge(message = "保存设备信息失败: ${e.message}")
        }
    }

    /**
     * 获取上次连接的设备地址
     */
    fun getLastConnectedDeviceAddress(): String? {
        val address = VTMMKVUtil.getString(MMKV_ID, KEY_DEVICE_ADDRESS)
        logd("=== DeviceManager: 获取保存的设备地址 ===")
        logd("设备地址: ${address ?: "无"}")
        return address
    }

    /**
     * 获取上次连接的设备名称
     */
    fun getLastConnectedDeviceName(): String? {
        val name = VTMMKVUtil.getString(MMKV_ID, KEY_DEVICE_NAME)
        logd("=== DeviceManager: 获取保存的设备名称 ===")
        logd("设备名称: ${name ?: "无"}")
        return name
    }

    /**
     * 获取上次连接的设备配对状态
     */
    fun getLastConnectedDeviceBondState(): Int {
        val bondState = VTMMKVUtil.getInt(MMKV_ID, KEY_DEVICE_BOND_STATE, BluetoothDevice.BOND_NONE)
        logd("=== DeviceManager: 获取保存的设备配对状态 ===")
        logd("配对状态: ${getBondStateDescription(bondState)}")
        return bondState
    }

    /**
     * 获取设备连接次数
     */
    fun getDeviceConnectCount(): Int {
        val count = VTMMKVUtil.getInt(MMKV_ID, KEY_DEVICE_CONNECT_COUNT, 0)
        logd("=== DeviceManager: 获取设备连接次数 ===")
        logd("连接次数: $count")
        return count
    }

    /**
     * 获取设备最后连接时间
     */
    fun getDeviceLastConnectTime(): Long {
        val time = VTMMKVUtil.getLong(MMKV_ID, KEY_DEVICE_LAST_CONNECT_TIME, 0L)
        logd("=== DeviceManager: 获取设备最后连接时间 ===")
        logd("最后连接时间: $time")
        return time
    }

    /**
     * 检查设备是否需要配对
     */
    fun shouldBondDevice(device: BluetoothDevice): Boolean {
        val bondState = device.bondState
        val connectCount = getDeviceConnectCount()

        // 判断逻辑：
        // 1. 如果设备未配对且连接次数超过3次，建议配对
        // 2. 如果设备未配对且是LED相关设备，建议配对
        // 3. 如果设备已配对，不需要再次配对

        val shouldBond = when {
            bondState == BluetoothDevice.BOND_BONDED -> {
                logd("设备已配对，无需再次配对")
                false
            }

            bondState == BluetoothDevice.BOND_BONDING -> {
                logd("设备正在配对中")
                false
            }

            connectCount >= MAX_RETRY_COUNT-> {
                logd("设备连接次数较多(${connectCount}次)，建议配对以提高连接稳定性")
                true
            }

            isLEDDevice(device) -> {
                logd("检测到LED设备，建议配对以提高数据传输稳定性")
                true
            }

            else -> {
                logd("设备连接次数较少，暂不需要配对")
                false
            }
        }

        logd("=== DeviceManager: 检查设备配对需求 ===")
        logd("设备名称: ${device.name ?: "未知"}")
        logd("配对状态: ${getBondStateDescription(bondState)}")
        logd("连接次数: $connectCount")
        logd("是否需要配对: $shouldBond")

        return shouldBond
    }

    /**
     * 检查是否为LED设备
     */
    private fun isLEDDevice(device: BluetoothDevice): Boolean {
        val deviceName = device.name ?: ""
        return deviceName.contains(DEVICE_NAME, ignoreCase = true)
    }

    /**
     * 获取配对状态描述
     */
    fun getBondStateDescription(bondState: Int): String {
        return when (bondState) {
            BluetoothDevice.BOND_NONE -> "未配对"
            BluetoothDevice.BOND_BONDING -> "配对中"
            BluetoothDevice.BOND_BONDED -> "已配对"
            else -> "未知状态"
        }
    }

    /**
     * 清除保存的设备信息
     */
    fun clearSavedDevice() {
        try {
            VTMMKVUtil.removeKey(MMKV_ID, KEY_DEVICE_NAME)
            VTMMKVUtil.removeKey(MMKV_ID, KEY_DEVICE_ADDRESS)
            VTMMKVUtil.removeKey(MMKV_ID, KEY_DEVICE_BOND_STATE)
            VTMMKVUtil.removeKey(MMKV_ID, KEY_DEVICE_LAST_CONNECT_TIME)
            VTMMKVUtil.removeKey(MMKV_ID, KEY_DEVICE_CONNECT_COUNT)
            logd("=== DeviceManager: 清除保存的设备信息 ===")
            logd("设备信息已清除")
        } catch (e: Exception) {
            loge(message = "清除设备信息失败: ${e.message}")
        }
    }

    /**
     * 检查是否有保存的设备信息
     */
    fun hasSavedDevice(): Boolean {
        val hasAddress = VTMMKVUtil.containsKey(MMKV_ID, KEY_DEVICE_ADDRESS)
        logd("=== DeviceManager: 检查是否有保存的设备信息 ===")
        logd("是否有保存的设备: $hasAddress")
        return hasAddress
    }

    /**
     * 获取设备信息摘要
     */
    fun getDeviceInfoSummary(): String {
        try {
            val name = getLastConnectedDeviceName() ?: "未知"
            val address = getLastConnectedDeviceAddress() ?: "无"
            val bondState = getBondStateDescription(getLastConnectedDeviceBondState())
            val connectCount = getDeviceConnectCount()
            val lastConnectTime = getDeviceLastConnectTime()

            val timeAgo = if (lastConnectTime > 0) {
                val diff = System.currentTimeMillis() - lastConnectTime
                when {
                    diff < 60000 -> "刚刚"
                    diff < 3600000 -> "${diff / 60000}分钟前"
                    diff < 86400000 -> "${diff / 3600000}小时前"
                    else -> "${diff / 86400000}天前"
                }
            } else {
                "从未连接"
            }

            return "设备: $name\n地址: $address\n配对状态: $bondState\n连接次数: $connectCount\n最后连接: $timeAgo"
        } catch (e: Exception) {
            loge(message = "获取设备信息摘要失败: ${e.message}")
            return "获取设备信息失败"
        }
    }
}