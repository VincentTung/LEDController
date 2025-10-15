package com.vincent.android.ledcontroller.constants

/**
 * LED控制相关常量
 */
object LedConstants {
    
    /**
     * LED亮度相关常量
     */
    // LED最小亮度为5，小于5就看不见了
    const val LED_MINIMUM_BRIGHTNESS = 5
    
    // LED默认亮度
    const val LED_DEFAULT_BRIGHTNESS = 20
    
    // LED默认显示文本
    const val LED_DEFAULT_DISPLAY_TEXT = "已连接"
    
    // LED默认显示字体大小
    const val LED_DEFAULT_FONT_SIZE = 1
    
    /**
     * LED控制命令常量
     */
    // 时钟控制命令
    const val LED_CMD_CLOCK_ENABLE = "C1"
    const val LED_CMD_CLOCK_DISABLE = "C0"
    
    // 文本显示命令
    const val LED_CMD_STATIC_TEXT = "T"
    const val LED_CMD_SCROLLING_TEXT = "S"
    
    // 亮度控制命令
    const val LED_CMD_BRIGHTNESS = "B"
    
    // 刷新频率控制命令
    const val LED_CMD_REFRESH_RATE = "R"
    
    // 像素绘制命令
    const val LED_CMD_PIXEL = "P"
    
    // 屏幕填充命令
    const val LED_CMD_FILL_SCREEN = "F"
    
    // 计时游戏命令
    const val LED_CMD_GAME_TIMER_TARGET = "GT"
    const val LED_CMD_GAME_CURRENT = "GC"
    const val LED_CMD_GAME_WIN = "GW"
    const val LED_CMD_GAME_LOSE = "GL"
    const val LED_CMD_GAME_START = "GS"
    const val LED_CMD_GAME_PAUSE = "GP"
    
    /**
     * 文本显示相关常量
     */
    const val DEFAULT_FONT_SIZE = 1
    const val DEFAULT_SCROLL_SPEED = 1
    
    /**
     * 亮度相关常量
     */
    const val BRIGHTNESS_MAX_VALUE = 255
    const val BRIGHTNESS_PERCENTAGE_DIVISOR = 100.0f
    
    /**
     * 刷新频率相关常量
     */
    const val REFRESH_RATE_MIN_HZ = 30
    const val REFRESH_RATE_MAX_HZ = 150
    
    /**
     * 屏幕填充常量
     */
    const val FILL_SCREEN_CLEAR = "1"
    const val FILL_SCREEN_WHITE = "0"
    
    /**
     * 进度计算常量
     */
    const val PROGRESS_PERCENTAGE = 100
}
