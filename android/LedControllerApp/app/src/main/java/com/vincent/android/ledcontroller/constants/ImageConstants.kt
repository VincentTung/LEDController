package com.vincent.android.ledcontroller.constants

/**
 * 图像处理相关常量
 */
object ImageConstants {
    
    /**
     * 图片类型常量
     */
    const val IMAGE_TYPE_ALL = "image/*"
    const val IMAGE_TYPE_GIF = "image/gif"
    
    /**
     * LED矩阵尺寸
     */
    const val LED_MATRIX_SIZE = 64
    
    /**
     * RGB565转换相关常量
     */
    const val RGB565_BYTES_PER_PIXEL = 2
    const val RGB_RED_SHIFT = 16
    const val RGB_GREEN_SHIFT = 8
    const val RGB_BLUE_SHIFT = 0
    const val RGB565_RED_MASK = 0xF8
    const val RGB565_GREEN_MASK = 0xFC
    const val RGB565_BLUE_MASK = 0x1F
    const val RGB565_RED_SHIFT_BITS = 8
    const val RGB565_GREEN_SHIFT_BITS = 3
    const val RGB565_BLUE_SHIFT_BITS = 3
}
