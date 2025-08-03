package com.vincent.android.myled.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * 配置管理类
 * 统一管理应用配置和用户偏好设置
 */
object ConfigManager {
    
    private const val PREF_NAME = "led_controller_prefs"
    private const val KEY_LAST_BRIGHTNESS = "last_brightness"
    private const val KEY_LAST_STATIC_TEXT_SIZE = "last_static_text_size"
    private const val KEY_LAST_SCROLL_TEXT_SIZE = "last_scroll_text_size"
    private const val KEY_LAST_SCROLL_SPEED = "last_scroll_speed"
    private const val KEY_AUTO_CONNECT = "auto_connect"
    private const val KEY_REAL_TIME_UPDATE = "real_time_update"
    
    private lateinit var prefs: SharedPreferences
    
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }
    
    // 亮度设置
    var lastBrightness: Int
        get() = prefs.getInt(KEY_LAST_BRIGHTNESS, LED_DEFAULT_BRIGHTNESS)
        set(value) = prefs.edit().putInt(KEY_LAST_BRIGHTNESS, value).apply()
    
    // 静态文字大小
    var lastStaticTextSize: Int
        get() = prefs.getInt(KEY_LAST_STATIC_TEXT_SIZE, LED_DEFAULT_FONT_SIZE)
        set(value) = prefs.edit().putInt(KEY_LAST_STATIC_TEXT_SIZE, value).apply()
    
    // 滚动文字大小
    var lastScrollTextSize: Int
        get() = prefs.getInt(KEY_LAST_SCROLL_TEXT_SIZE, LED_DEFAULT_FONT_SIZE)
        set(value) = prefs.edit().putInt(KEY_LAST_SCROLL_TEXT_SIZE, value).apply()
    
    // 滚动速度
    var lastScrollSpeed: Int
        get() = prefs.getInt(KEY_LAST_SCROLL_SPEED, 2) // 默认中等速度
        set(value) = prefs.edit().putInt(KEY_LAST_SCROLL_SPEED, value).apply()
    
    // 自动连接
    var autoConnect: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CONNECT, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_CONNECT, value).apply()
    
    // 实时更新
    var realTimeUpdate: Boolean
        get() = prefs.getBoolean(KEY_REAL_TIME_UPDATE, false)
        set(value) = prefs.edit().putBoolean(KEY_REAL_TIME_UPDATE, value).apply()
    
    /**
     * 清除所有配置
     */
    fun clearAll() {
        prefs.edit().clear().apply()
    }
    
    /**
     * 重置为默认值
     */
    fun resetToDefaults() {
        lastBrightness = LED_DEFAULT_BRIGHTNESS
        lastStaticTextSize = LED_DEFAULT_FONT_SIZE
        lastScrollTextSize = LED_DEFAULT_FONT_SIZE
        lastScrollSpeed = 2
        autoConnect = true
        realTimeUpdate = false
    }
} 