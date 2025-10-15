package com.vincent.library.ble.config

/**
 * PHY相关常量
 */
const val PHY_1M = 1 // 1M PHY
const val PHY_2M = 2 // 2M PHY (Android 8.0+)
const val PHY_CODED = 3 // Coded PHY (Android 9.0+)

/**
 * PHY协商相关常量
 */
const val PHY_NEGOTIATION_TIMEOUT = 5000L // 5秒PHY协商超时
const val PHY_NEGOTIATION_RETRY_COUNT = 2 // PHY协商重试次数
const val PHY_NEGOTIATION_RETRY_DELAY = 1000L // 1秒PHY协商重试延迟

/**
 * 连接参数相关常量
 */
const val CONNECTION_INTERVAL_MIN = 6 // 最小连接间隔 (7.5ms)
const val CONNECTION_INTERVAL_MAX = 6 // 最大连接间隔 (7.5ms)
const val SLAVE_LATENCY = 0 // 从机延迟
const val SUPERVISION_TIMEOUT = 500 // 监督超时 (5秒)

/**
 * BLE相关常量
 */
const val MAX_RETRY_COUNT = 3 // 最大重试次数
const val RETRY_DELAY = 2000L // 2秒后重试
const val CONNECTION_MONITOR_INTERVAL = 5000L // 5秒检查一次连接状态
const val SCAN_TIMEOUT = 8000L // 8秒扫描超时
const val CONNECTION_TIMEOUT = 12000L // 12秒连接超时
const val SERVICE_DISCOVERY_TIMEOUT = 15000L // 15秒服务发现超时，增加稳定性

/**
 * MTU相关常量
 */
const val MTU_DEFAULT = 23 // 默认MTU大小
const val MTU_OPTIMAL = 512 // 理想MTU大小
const val MTU_FALLBACK = 256 // 降级MTU大小
const val MTU_GOOD = 256 // 较好的MTU大小阈值
const val MTU_ACCEPTABLE = 128 // 可接受的MTU大小阈值

