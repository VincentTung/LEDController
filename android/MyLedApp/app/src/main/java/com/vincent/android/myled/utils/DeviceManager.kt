package com.vincent.android.myled.utils

import android.content.Context
import android.util.Log
import com.tencent.mmkv.MMKV

/**
 * 设备管理器 - 负责保存和获取已连接设备的信息
 */
object DeviceManager {
    private const val MMKV_ID = "device_manager"
    private const val KEY_DEVICE_NAME = "last_connected_device_name"
    private const val KEY_DEVICE_ADDRESS = "last_connected_device_address"
    
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
     * 保存已连接的设备信息
     */
    fun saveConnectedDevice(deviceName: String?, deviceAddress: String) {
        try {
            mmkv?.encode(KEY_DEVICE_NAME, deviceName ?: "")
            mmkv?.encode(KEY_DEVICE_ADDRESS, deviceAddress)
            logd(LOG_TAG, "=== DeviceManager: 保存设备信息 ===")
            logd(LOG_TAG, "设备名称: ${deviceName ?: "未知"}")
            logd(LOG_TAG, "设备地址: $deviceAddress")
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
     * 清除保存的设备信息
     */
    fun clearSavedDevice() {
        try {
            mmkv?.removeValueForKey(KEY_DEVICE_NAME)
            mmkv?.removeValueForKey(KEY_DEVICE_ADDRESS)
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
} 