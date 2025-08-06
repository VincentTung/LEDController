package com.vincent.android.myled.utils

const val LOG_TAG = "LEDController"

/**
 * 打开蓝牙 request code
 */
const val REQUEST_ENABLE_BLUETOOTH = 0x16


/**
 * BLE相关常量
 */
const val MAX_RETRY_COUNT = 3 // 最大重试次数
const val RETRY_DELAY = 2000L // 2秒后重试
const val CONNECTION_MONITOR_INTERVAL = 5000L // 5秒检查一次连接状态
const val SCAN_TIMEOUT = 8000L // 8秒扫描超时
const val CONNECTION_TIMEOUT = 12000L // 12秒连接超时
const val SERVICE_DISCOVERY_TIMEOUT = 8000L // 8秒服务发现超时
const val BLE_CHUNK_SIZE = 512  // 使用512字节，兼容性更好 // BLE数据块大小
const val BLE_MTU_SIZE = 512    // BLE MTU大小，与ESP32端保持一致

/**
 * 蓝牙设备名称
 */
const val DEVICE_NAME = "MyLED"
/**
 * LED 服务UUID
 */
const val LED_SERVICE_UUID = "4fafc201-1fb5-459e-8fcc-c5c9c331914b"

/**
 * 特征UUID
 */

//文本
const val LED_CHARACTERISTIC_TEXT_UUID = "beb5483e-36e1-4688-b7f5-ea07361b26a8"
const val LED_CHARACTERISTIC_TEXT_SCROLL_UUID = "beb5483e-36e1-4688-b7f5-ea07361b26a3"

//GIF
const val LED_CHARACTERISTIC_GIF_UUID = "beb5483e-36e1-4688-b7f5-ea07361b26b1"

//画图(黑白)
const val LED_CHARACTERISTIC_DRAW_NORMAL_UUID = "beb5483e-36e1-4688-b7f5-ea07361b26a7"

//画图(彩色)
const val LED_CHARACTERISTIC_DRAW_COLORFUL_UUID = "beb5483e-36e1-4688-b7f5-ea07361b26a6"

//亮度
const val LED_CHARACTERISTIC_BRIGHTNESS_UUID = "beb5483e-36e1-4688-b7f5-ea07361b26a9"

//单像素
const val LED_CHARACTERISTIC_FILL_PIXEL_UUID = "beb5483e-36e1-4688-b7f5-ea07361b26a5"

//全屏
const val LED_CHARACTERISTIC_FILL_SCREEN_UUID = "beb5483e-36e1-4688-b7f5-ea07361b26a4"

//刷新频率
const val LED_CHARACTERISTIC_REFRESH_RATE_UUID = "beb5483e-36e1-4688-b7f5-ea07361b26b2"

//LED最小亮度为5，小于5就看不见了
const val LED_MINIMUM_BRIGHTNESS = 0

//LED默认亮度
const val LED_DEFAULT_BRIGHTNESS = 20

//LED默认显示文本
const val LED_DEFAULT_DISPLAY_TEXT = "已连接"

//LED默认显示字体大小
const val LED_DEFAULT_FONT_SIZE = 1

