package com.vincent.android.ledcontroller.constants

/**
 * BLE蓝牙相关常量
 */
object BleConstants {
    
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
    // 通用控制特征值
    const val LED_CHARACTERISTIC_CONTROL_UUID = "beb5483e-36e1-4688-b7f5-ea07361b26c0"
    
    // GIF显示特征值
    const val LED_CHARACTERISTIC_GIF_UUID = "beb5483e-36e1-4688-b7f5-ea07361b26b1"
    
    // 亮度特征值 - 亮度通知
    const val LED_CHARACTERISTIC_BRIGHTNESS_UUID = "beb5483e-36e1-4688-b7f5-ea07361b26a9"
    
    /**
     * 连接和重试相关常量
     */
    const val CONNECTION_CHECK_INTERVAL_MS = 5000L
    const val MAX_CONNECTION_RETRY_COUNT = 3
    const val CONNECTION_RETRY_DELAY_MS = 200L
    
    /**
     * BLE数据传输相关常量
     */
    const val BLE_CHUNK_SIZE = 20
    const val BLE_HEADER_DELAY_MS = 100L  // 增加头信息延迟
    const val BLE_PACKET_DELAY_MS = 50L  // 增加包间延迟
    const val BLE_BATCH_DELAY_MS = 200L   // 增加批次延迟
    const val BLE_HEADER_PROCESS_DELAY_MS = 800L  // 增加头信息处理延迟
    const val BLE_IMAGE_HEADER_DELAY_MS = 500L     // 增加图像头信息延迟
    const val BLE_IMAGE_PACKET_DELAY_MS = 20L      // 增加图像包延迟
    const val BLE_IMAGE_BATCH_DELAY_MS = 100L      // 增加图像批次延迟
    const val BLE_IMAGE_BATCH_SIZE = 20            // 减少批次大小
    const val BLE_GIF_BATCH_SIZE = 10               // 减少GIF批次大小
    const val BLE_HEADER_SIZE = 2
    const val BLE_FILE_SIZE_BYTES = 4
    const val BLE_MTU_KB_MULTIPLIER = 1024
    
    /**
     * 重试相关常量
     */
    const val GIF_HEADER_RETRY_COUNT = 5
    const val GIF_DATA_RETRY_COUNT = 3
    const val IMAGE_HEADER_RETRY_COUNT = 3
    const val IMAGE_DATA_RETRY_COUNT = 2
}
