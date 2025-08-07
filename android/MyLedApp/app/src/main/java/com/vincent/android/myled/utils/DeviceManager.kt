package com.vincent.android.myled.utils

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.util.Log
import com.tencent.mmkv.MMKV

/**
 * 设备管理器 - 负责保存和获取已连接设备的信息，管理配对状态
 */
object DeviceManager {
    private const val MMKV_ID = "device_manager"
    private const val KEY_DEVICE_NAME = "last_connected_device_name"
    private const val KEY_DEVICE_ADDRESS = "last_connected_device_address"
    private const val KEY_DEVICE_BOND_STATE = "last_connected_device_bond_state"
    private const val KEY_DEVICE_LAST_CONNECT_TIME = "last_connected_device_time"
    private const val KEY_DEVICE_CONNECT_COUNT = "last_connected_device_count"
    
    private var mmkv: MMKV? = null
    
    /**
     * 初始化MMKV
     */
    fun init(context: Context) {
        try {
            mmkv = MMKV.mmkvWithID(MMKV_ID)
            logd(LOG_TAG, "=== DeviceManager: 初始化MMKV ===")
            logd(LOG_TAG, "MMKV初始化完成，ID: $MMKV_ID")
        } catch (e: Exception) {
            loge(LOG_TAG, "MMKV初始化失败: ${e.message}")
        }
    }
    
    /**
     * 保存已连接的设备信息（包含配对状态）
     */
    fun saveConnectedDevice(device: BluetoothDevice) {
        try {
            mmkv?.encode(KEY_DEVICE_NAME, device.name ?: "")
            mmkv?.encode(KEY_DEVICE_ADDRESS, device.address)
            mmkv?.encode(KEY_DEVICE_BOND_STATE, device.bondState)
            mmkv?.encode(KEY_DEVICE_LAST_CONNECT_TIME, System.currentTimeMillis())
            
            // 增加连接次数
            val currentCount = mmkv?.decodeInt(KEY_DEVICE_CONNECT_COUNT, 0) ?: 0
            mmkv?.encode(KEY_DEVICE_CONNECT_COUNT, currentCount + 1)
            
            logd(LOG_TAG, "=== DeviceManager: 保存设备信息 ===")
            logd(LOG_TAG, "设备名称: ${device.name ?: "未知"}")
            logd(LOG_TAG, "设备地址: ${device.address}")
            logd(LOG_TAG, "配对状态: ${getBondStateDescription(device.bondState)}")
            logd(LOG_TAG, "连接次数: ${currentCount + 1}")
            logd(LOG_TAG, "设备信息已保存到本地存储")
        } catch (e: Exception) {
            loge(LOG_TAG, "保存设备信息失败: ${e.message}")
        }
    }
    
    /**
     * 保存已连接的设备信息（兼容旧版本）
     */
    fun saveConnectedDevice(deviceName: String?, deviceAddress: String) {
        try {
            mmkv?.encode(KEY_DEVICE_NAME, deviceName ?: "")
            mmkv?.encode(KEY_DEVICE_ADDRESS, deviceAddress)
            mmkv?.encode(KEY_DEVICE_LAST_CONNECT_TIME, System.currentTimeMillis())
            
            // 增加连接次数
            val currentCount = mmkv?.decodeInt(KEY_DEVICE_CONNECT_COUNT, 0) ?: 0
            mmkv?.encode(KEY_DEVICE_CONNECT_COUNT, currentCount + 1)
            
            logd(LOG_TAG, "=== DeviceManager: 保存设备信息 ===")
            logd(LOG_TAG, "设备名称: ${deviceName ?: "未知"}")
            logd(LOG_TAG, "设备地址: $deviceAddress")
            logd(LOG_TAG, "连接次数: ${currentCount + 1}")
            logd(LOG_TAG, "设备信息已保存到本地存储")
        } catch (e: Exception) {
            loge(LOG_TAG, "保存设备信息失败: ${e.message}")
        }
    }
    
    /**
     * 获取上次连接的设备地址
     */
    fun getLastConnectedDeviceAddress(): String? {
        try {
            val address = mmkv?.decodeString(KEY_DEVICE_ADDRESS)
            logd(LOG_TAG, "=== DeviceManager: 获取保存的设备地址 ===")
            logd(LOG_TAG, "设备地址: ${address ?: "无"}")
            return address
        } catch (e: Exception) {
            loge(LOG_TAG, "获取设备地址失败: ${e.message}")
            return null
        }
    }
    
    /**
     * 获取上次连接的设备名称
     */
    fun getLastConnectedDeviceName(): String? {
        try {
            val name = mmkv?.decodeString(KEY_DEVICE_NAME)
            logd(LOG_TAG, "=== DeviceManager: 获取保存的设备名称 ===")
            logd(LOG_TAG, "设备名称: ${name ?: "无"}")
            return name
        } catch (e: Exception) {
            loge(LOG_TAG, "获取设备名称失败: ${e.message}")
            return null
        }
    }
    
    /**
     * 获取上次连接的设备配对状态
     */
    fun getLastConnectedDeviceBondState(): Int {
        try {
            val bondState = mmkv?.decodeInt(KEY_DEVICE_BOND_STATE, BluetoothDevice.BOND_NONE) ?: BluetoothDevice.BOND_NONE
            logd(LOG_TAG, "=== DeviceManager: 获取保存的设备配对状态 ===")
            logd(LOG_TAG, "配对状态: ${getBondStateDescription(bondState)}")
            return bondState
        } catch (e: Exception) {
            loge(LOG_TAG, "获取设备配对状态失败: ${e.message}")
            return BluetoothDevice.BOND_NONE
        }
    }
    
    /**
     * 获取设备连接次数
     */
    fun getDeviceConnectCount(): Int {
        try {
            val count = mmkv?.decodeInt(KEY_DEVICE_CONNECT_COUNT, 0) ?: 0
            logd(LOG_TAG, "=== DeviceManager: 获取设备连接次数 ===")
            logd(LOG_TAG, "连接次数: $count")
            return count
        } catch (e: Exception) {
            loge(LOG_TAG, "获取设备连接次数失败: ${e.message}")
            return 0
        }
    }
    
    /**
     * 获取设备最后连接时间
     */
    fun getDeviceLastConnectTime(): Long {
        try {
            val time = mmkv?.decodeLong(KEY_DEVICE_LAST_CONNECT_TIME, 0L) ?: 0L
            logd(LOG_TAG, "=== DeviceManager: 获取设备最后连接时间 ===")
            logd(LOG_TAG, "最后连接时间: $time")
            return time
        } catch (e: Exception) {
            loge(LOG_TAG, "获取设备最后连接时间失败: ${e.message}")
            return 0L
        }
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
                logd(LOG_TAG, "设备已配对，无需再次配对")
                false
            }
            bondState == BluetoothDevice.BOND_BONDING -> {
                logd(LOG_TAG, "设备正在配对中")
                false
            }
            connectCount >= 3 -> {
                logd(LOG_TAG, "设备连接次数较多(${connectCount}次)，建议配对以提高连接稳定性")
                true
            }
            isLEDDevice(device) -> {
                logd(LOG_TAG, "检测到LED设备，建议配对以提高数据传输稳定性")
                true
            }
            else -> {
                logd(LOG_TAG, "设备连接次数较少，暂不需要配对")
                false
            }
        }
        
        logd(LOG_TAG, "=== DeviceManager: 检查设备配对需求 ===")
        logd(LOG_TAG, "设备名称: ${device.name ?: "未知"}")
        logd(LOG_TAG, "配对状态: ${getBondStateDescription(bondState)}")
        logd(LOG_TAG, "连接次数: $connectCount")
        logd(LOG_TAG, "是否需要配对: $shouldBond")
        
        return shouldBond
    }
    
    /**
     * 检查是否为LED设备
     */
    private fun isLEDDevice(device: BluetoothDevice): Boolean {
        val deviceName = device.name ?: ""
        return deviceName.contains("LED", ignoreCase = true) ||
               deviceName.contains("MyLED", ignoreCase = true) ||
               deviceName.contains("Matrix", ignoreCase = true) ||
               deviceName.contains("Display", ignoreCase = true)
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
            mmkv?.removeValueForKey(KEY_DEVICE_NAME)
            mmkv?.removeValueForKey(KEY_DEVICE_ADDRESS)
            mmkv?.removeValueForKey(KEY_DEVICE_BOND_STATE)
            mmkv?.removeValueForKey(KEY_DEVICE_LAST_CONNECT_TIME)
            mmkv?.removeValueForKey(KEY_DEVICE_CONNECT_COUNT)
            logd(LOG_TAG, "=== DeviceManager: 清除保存的设备信息 ===")
            logd(LOG_TAG, "设备信息已清除")
        } catch (e: Exception) {
            loge(LOG_TAG, "清除设备信息失败: ${e.message}")
        }
    }
    
    /**
     * 检查是否有保存的设备信息
     */
    fun hasSavedDevice(): Boolean {
        try {
            val hasAddress = mmkv?.containsKey(KEY_DEVICE_ADDRESS) == true
            logd(LOG_TAG, "=== DeviceManager: 检查是否有保存的设备信息 ===")
            logd(LOG_TAG, "是否有保存的设备: $hasAddress")
            return hasAddress
        } catch (e: Exception) {
            loge(LOG_TAG, "检查设备信息失败: ${e.message}")
            return false
        }
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
            loge(LOG_TAG, "获取设备信息摘要失败: ${e.message}")
            return "获取设备信息失败"
        }
    }
} 