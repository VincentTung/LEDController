package com.vincent.android.ledcontroller.utils

import android.content.Context
import com.vincent.library.base.util.logd
import com.vincent.library.base.util.loge
import com.vincent.library.base.util.VTMMKVUtil
import com.vincent.android.ledcontroller.constants.LedConstants

object LedConfigManager {

    private const val TAG = "ConfigManager"
    private const val MMKV_ID = "led_controller_config"
    private const val KEY_LAST_BRIGHTNESS = "last_brightness"
    private const val KEY_LAST_STATIC_TEXT_SIZE = "last_static_text_size"
    private const val KEY_LAST_SCROLL_TEXT_SIZE = "last_scroll_text_size"
    private const val KEY_LAST_SCROLL_SPEED = "last_scroll_speed"
    private const val KEY_AUTO_CONNECT = "auto_connect"
    private const val KEY_REAL_TIME_UPDATE = "real_time_update"

    fun init(context: Context) {
        try {
            VTMMKVUtil.init(context)
           logd(TAG, "=== ConfigManager: 初始化完成 ===")
           logd(TAG, "使用MMKV实例ID: $MMKV_ID")
        } catch (e: Exception) {
            loge(TAG, "ConfigManager 初始化失败: ${e.message}")
        }
    }

    // 亮度设置
    var lastBrightness: Int
        get() {
            val value = VTMMKVUtil.getInt(MMKV_ID, KEY_LAST_BRIGHTNESS, LedConstants.LED_DEFAULT_BRIGHTNESS)
            logd(TAG, "获取亮度设置: $value")
            return value
        }
        set(value) {
            if (VTMMKVUtil.putInt(MMKV_ID, KEY_LAST_BRIGHTNESS, value)) {
                logd(TAG, "保存亮度设置: $value")
            } else {
                loge(TAG, "保存亮度设置失败: $value")
            }
        }

    // 静态文字大小
    var lastStaticTextSize: Int
        get() {
            val value = VTMMKVUtil.getInt(MMKV_ID, KEY_LAST_STATIC_TEXT_SIZE, LedConstants.LED_DEFAULT_FONT_SIZE)
            logd(TAG, "获取静态文字大小: $value")
            return value
        }
        set(value) {
            if (VTMMKVUtil.putInt(MMKV_ID, KEY_LAST_STATIC_TEXT_SIZE, value)) {
                logd(TAG, "保存静态文字大小: $value")
            } else {
                loge(TAG, "保存静态文字大小失败: $value")
            }
        }

    // 滚动文字大小
    var lastScrollTextSize: Int
        get() {
            val value = VTMMKVUtil.getInt(MMKV_ID, KEY_LAST_SCROLL_TEXT_SIZE, LedConstants.LED_DEFAULT_FONT_SIZE)
            logd(TAG, "获取滚动文字大小: $value")
            return value
        }
        set(value) {
            if (VTMMKVUtil.putInt(MMKV_ID, KEY_LAST_SCROLL_TEXT_SIZE, value)) {
                logd(TAG, "保存滚动文字大小: $value")
            } else {
                loge(TAG, "保存滚动文字大小失败: $value")
            }
        }

    // 滚动速度
    var lastScrollSpeed: Int
        get() {
            val value = VTMMKVUtil.getInt(MMKV_ID, KEY_LAST_SCROLL_SPEED, 2) // 默认中等速度
            logd(TAG, "获取滚动速度: $value")
            return value
        }
        set(value) {
            if (VTMMKVUtil.putInt(MMKV_ID, KEY_LAST_SCROLL_SPEED, value)) {
                logd(TAG, "保存滚动速度: $value")
            } else {
                loge(TAG, "保存滚动速度失败: $value")
            }
        }

    // 自动连接
    var autoConnect: Boolean
        get() {
            val value = VTMMKVUtil.getBoolean(MMKV_ID, KEY_AUTO_CONNECT, true)
            logd(TAG, "获取自动连接设置: $value")
            return value
        }
        set(value) {
            if (VTMMKVUtil.putBoolean(MMKV_ID, KEY_AUTO_CONNECT, value)) {
                logd(TAG, "保存自动连接设置: $value")
            } else {
                loge(TAG, "保存自动连接设置失败: $value")
            }
        }

    // 实时更新
    var realTimeUpdate: Boolean
        get() {
            val value = VTMMKVUtil.getBoolean(MMKV_ID, KEY_REAL_TIME_UPDATE, false)
            logd(TAG, "获取实时更新设置: $value")
            return value
        }
        set(value) {
            if (VTMMKVUtil.putBoolean(MMKV_ID, KEY_REAL_TIME_UPDATE, value)) {
                logd(TAG, "保存实时更新设置: $value")
            } else {
                loge(TAG, "保存实时更新设置失败: $value")
            }
        }

    /**
     * 清除所有配置
     */
    fun clearAll() {
        if (VTMMKVUtil.clearAll(MMKV_ID)) {
           logd(TAG, "=== ConfigManager: 清除所有配置 ===")
           logd(TAG, "所有配置已清除")
        } else {
            loge(TAG, "清除配置失败")
        }
    }

    /**
     * 重置为默认值
     */
    fun resetToDefaults() {
        try {
           logd(TAG, "=== ConfigManager: 重置为默认值 ===")
            lastBrightness = LedConstants.LED_DEFAULT_BRIGHTNESS
            lastStaticTextSize = LedConstants.LED_DEFAULT_FONT_SIZE
            lastScrollTextSize = LedConstants.LED_DEFAULT_FONT_SIZE
            lastScrollSpeed = 2
            autoConnect = true
            realTimeUpdate = false
           logd(TAG, "配置已重置为默认值")
        } catch (e: Exception) {
            loge(TAG, "重置配置失败: ${e.message}")
        }
    }

    /**
     * 获取所有配置信息（用于调试）
     */
    fun getAllConfigInfo(): String {
        return try {
            """
            === ConfigManager 配置信息 ===
            亮度设置: $lastBrightness
            静态文字大小: $lastStaticTextSize
            滚动文字大小: $lastScrollTextSize
            滚动速度: $lastScrollSpeed
            自动连接: $autoConnect
            实时更新: $realTimeUpdate
            """.trimIndent()
        } catch (e: Exception) {
            "获取配置信息失败: ${e.message}"
        }
    }

    /**
     * 检查MMKV是否已初始化
     */
    fun isInitialized(): Boolean {
        return VTMMKVUtil.isInitialized()
    }
} 