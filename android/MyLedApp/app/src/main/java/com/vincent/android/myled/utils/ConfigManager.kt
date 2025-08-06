package com.vincent.android.myled.utils

import android.content.Context
import com.tencent.mmkv.MMKV

/**
 * 配置管理类
 * 统一管理应用配置和用户偏好设置
 * 使用MMKV替代SharedPreferences，提供更好的性能
 */
object ConfigManager {
    
    private const val MMKV_ID = "led_controller_config"
    private const val KEY_LAST_BRIGHTNESS = "last_brightness"
    private const val KEY_LAST_STATIC_TEXT_SIZE = "last_static_text_size"
    private const val KEY_LAST_SCROLL_TEXT_SIZE = "last_scroll_text_size"
    private const val KEY_LAST_SCROLL_SPEED = "last_scroll_speed"
    private const val KEY_AUTO_CONNECT = "auto_connect"
    private const val KEY_REAL_TIME_UPDATE = "real_time_update"
    
    private var mmkv: MMKV? = null
    
    fun init(context: Context) {
        try {
            mmkv = MMKV.mmkvWithID(MMKV_ID)
           logd(LOG_TAG, "=== ConfigManager: 初始化MMKV ===")
           logd(LOG_TAG, "MMKV初始化完成，ID: $MMKV_ID")
        } catch (e: Exception) {
            loge(LOG_TAG, "ConfigManager MMKV初始化失败: ${e.message}")
        }
    }
    
    // 亮度设置
    var lastBrightness: Int
        get() {
            return try {
                mmkv?.decodeInt(KEY_LAST_BRIGHTNESS, LED_DEFAULT_BRIGHTNESS) ?: LED_DEFAULT_BRIGHTNESS
            } catch (e: Exception) {
                loge(LOG_TAG, "获取亮度设置失败: ${e.message}")
                LED_DEFAULT_BRIGHTNESS
            }
        }
        set(value) {
            try {
                mmkv?.encode(KEY_LAST_BRIGHTNESS, value)
               logd(LOG_TAG, "保存亮度设置: $value")
            } catch (e: Exception) {
                loge(LOG_TAG, "保存亮度设置失败: ${e.message}")
            }
        }
    
    // 静态文字大小
    var lastStaticTextSize: Int
        get() {
            return try {
                mmkv?.decodeInt(KEY_LAST_STATIC_TEXT_SIZE, LED_DEFAULT_FONT_SIZE) ?: LED_DEFAULT_FONT_SIZE
            } catch (e: Exception) {
                loge(LOG_TAG, "获取静态文字大小失败: ${e.message}")
                LED_DEFAULT_FONT_SIZE
            }
        }
        set(value) {
            try {
                mmkv?.encode(KEY_LAST_STATIC_TEXT_SIZE, value)
               logd(LOG_TAG, "保存静态文字大小: $value")
            } catch (e: Exception) {
                loge(LOG_TAG, "保存静态文字大小失败: ${e.message}")
            }
        }
    
    // 滚动文字大小
    var lastScrollTextSize: Int
        get() {
            return try {
                mmkv?.decodeInt(KEY_LAST_SCROLL_TEXT_SIZE, LED_DEFAULT_FONT_SIZE) ?: LED_DEFAULT_FONT_SIZE
            } catch (e: Exception) {
                loge(LOG_TAG, "获取滚动文字大小失败: ${e.message}")
                LED_DEFAULT_FONT_SIZE
            }
        }
        set(value) {
            try {
                mmkv?.encode(KEY_LAST_SCROLL_TEXT_SIZE, value)
               logd(LOG_TAG, "保存滚动文字大小: $value")
            } catch (e: Exception) {
                loge(LOG_TAG, "保存滚动文字大小失败: ${e.message}")
            }
        }
    
    // 滚动速度
    var lastScrollSpeed: Int
        get() {
            return try {
                mmkv?.decodeInt(KEY_LAST_SCROLL_SPEED, 2) ?: 2 // 默认中等速度
            } catch (e: Exception) {
                loge(LOG_TAG, "获取滚动速度失败: ${e.message}")
                2
            }
        }
        set(value) {
            try {
                mmkv?.encode(KEY_LAST_SCROLL_SPEED, value)
               logd(LOG_TAG, "保存滚动速度: $value")
            } catch (e: Exception) {
                loge(LOG_TAG, "保存滚动速度失败: ${e.message}")
            }
        }
    
    // 自动连接
    var autoConnect: Boolean
        get() {
            return try {
                mmkv?.decodeBool(KEY_AUTO_CONNECT, true) ?: true
            } catch (e: Exception) {
                loge(LOG_TAG, "获取自动连接设置失败: ${e.message}")
                true
            }
        }
        set(value) {
            try {
                mmkv?.encode(KEY_AUTO_CONNECT, value)
               logd(LOG_TAG, "保存自动连接设置: $value")
            } catch (e: Exception) {
                loge(LOG_TAG, "保存自动连接设置失败: ${e.message}")
            }
        }
    
    // 实时更新
    var realTimeUpdate: Boolean
        get() {
            return try {
                mmkv?.decodeBool(KEY_REAL_TIME_UPDATE, false) ?: false
            } catch (e: Exception) {
                loge(LOG_TAG, "获取实时更新设置失败: ${e.message}")
                false
            }
        }
        set(value) {
            try {
                mmkv?.encode(KEY_REAL_TIME_UPDATE, value)
               logd(LOG_TAG, "保存实时更新设置: $value")
            } catch (e: Exception) {
                loge(LOG_TAG, "保存实时更新设置失败: ${e.message}")
            }
        }
    
    /**
     * 清除所有配置
     */
    fun clearAll() {
        try {
            mmkv?.clearAll()
           logd(LOG_TAG, "=== ConfigManager: 清除所有配置 ===")
           logd(LOG_TAG, "所有配置已清除")
        } catch (e: Exception) {
            loge(LOG_TAG, "清除配置失败: ${e.message}")
        }
    }
    
    /**
     * 重置为默认值
     */
    fun resetToDefaults() {
        try {
           logd(LOG_TAG, "=== ConfigManager: 重置为默认值 ===")
            lastBrightness = LED_DEFAULT_BRIGHTNESS
            lastStaticTextSize = LED_DEFAULT_FONT_SIZE
            lastScrollTextSize = LED_DEFAULT_FONT_SIZE
            lastScrollSpeed = 2
            autoConnect = true
            realTimeUpdate = false
           logd(LOG_TAG, "配置已重置为默认值")
        } catch (e: Exception) {
            loge(LOG_TAG, "重置配置失败: ${e.message}")
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
        return mmkv != null
    }
} 