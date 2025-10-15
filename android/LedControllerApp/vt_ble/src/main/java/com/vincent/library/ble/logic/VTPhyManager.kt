package com.vincent.library.ble.logic

import VTBLECallback
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.content.Context
import android.os.Build
import com.vincent.library.base.util.VTCoroutineUtil
import com.vincent.library.base.util.logd
import com.vincent.library.ble.config.PHY_1M
import com.vincent.library.ble.config.PHY_2M
import com.vincent.library.ble.config.PHY_CODED
import com.vincent.library.ble.config.PHY_NEGOTIATION_TIMEOUT
import com.vincent.library.ble.util.VTBluetoothUtil
import kotlinx.coroutines.Job

/**
 * PHY管理工具类
 * 负责处理BLE PHY协商、版本检查和设备兼容性检查
 */
class VTPhyManager(private val context: Context) {

    companion object {
        private const val TAG = "VTPhyManager"
        private const val SCOPE_NAME = "VTPhyManager"
        
        // 错误消息常量
        private const val MSG_GATT_NULL = "GATT为空，无法执行操作"
        private const val MSG_PHY_NEGOTIATION_ATTEMPTED = "PHY协商已尝试过，跳过重复请求"
        private const val MSG_ANDROID_VERSION_NOT_SUPPORTED = "当前Android版本不支持PHY操作 (需要API 26+)"
        private const val MSG_PHY_READ_NOT_SUPPORTED = "当前Android版本不支持PHY读取 (需要API 26+)"
        private const val MSG_DEVICE_ONLY_SUPPORTS_1M = "设备只支持1M PHY，无需进行PHY协商"
        private const val MSG_TARGET_IS_1M_PHY = "目标PHY是1M PHY，无需进行PHY协商"
        private const val MSG_1M_PHY_DEFAULT = "1M PHY是默认PHY，直接标记为成功"
    }

    // PHY协商状态管理
    private var phyNegotiationAttempted = false
    private var phyNegotiationFailed = false
    private var currentTxPhy = PHY_1M
    private var currentRxPhy = PHY_1M
    private var phyNegotiationRetryCount = 0

    // 协程
    private var phyNegotiationTimeoutJob: Job? = null
    private var phyRetryJob: Job? = null

    // 回调接口
    private var phyCallback: VTBLECallback? = null
    
    // 缓存PHY支持状态
    private var cachedLe2MPhySupported: Boolean? = null
    private var cachedLeCodedPhySupported: Boolean? = null
    private var cachedSupportedPhys: List<Int>? = null
    
    // 缓存BluetoothAdapter
    private val bluetoothAdapter by lazy { VTBluetoothUtil.getBluetoothAdapter(context) }
    
    /**
     * 检查设备是否支持2M PHY
     * 需要Android 8.0+ (API 26+)
     */
    fun isLe2MPhySupported(): Boolean {
        return cachedLe2MPhySupported ?: run {
            val supported = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    bluetoothAdapter?.isLe2MPhySupported() ?: false
                } catch (e: Exception) {
                    logd(TAG, "检查2M PHY支持时发生异常: ${e.message}")
                    false
                }
            } else {
                logd(TAG, "当前Android版本不支持2M PHY检查 (需要API 26+)")
                false
            }
            cachedLe2MPhySupported = supported
            supported
        }
    }

    /**
     * 检查设备是否支持Coded PHY
     * 需要Android 9.0+ (API 28+)
     */
    fun isLeCodedPhySupported(): Boolean {
        return cachedLeCodedPhySupported ?: run {
            val supported = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    bluetoothAdapter?.isLeCodedPhySupported() ?: false
                } catch (e: Exception) {
                    logd(TAG, "检查Coded PHY支持时发生异常: ${e.message}")
                    false
                }
            } else {
                logd(TAG, "当前Android版本不支持Coded PHY检查 (需要API 28+)")
                false
            }
            cachedLeCodedPhySupported = supported
            supported
        }
    }

    /**
     * 获取设备支持的PHY列表
     */
    fun getSupportedPhys(): List<Int> {
        return cachedSupportedPhys ?: run {
            val supportedPhys = mutableListOf<Int>()

            // 1M PHY总是支持的
            supportedPhys.add(PHY_1M)

            // 检查2M PHY支持
            if (isLe2MPhySupported()) {
                supportedPhys.add(PHY_2M)
                logd(TAG, "设备支持2M PHY")
            } else {
                logd(TAG, "设备不支持2M PHY")
            }

            // 检查Coded PHY支持
            if (isLeCodedPhySupported()) {
                supportedPhys.add(PHY_CODED)
                logd(TAG, "设备支持Coded PHY")
            } else {
                logd(TAG, "设备不支持Coded PHY")
            }

            logd(TAG, "设备支持的PHY列表: $supportedPhys")
            cachedSupportedPhys = supportedPhys
            supportedPhys
        }
    }

    /**
     * 获取最优PHY配置
     * 优先级：2M > Coded > 1M
     */
    fun getOptimalPhy(): Int {
        val supportedPhys = getSupportedPhys()

        return when {
            supportedPhys.contains(PHY_2M) -> {
                logd(TAG, "选择2M PHY作为最优配置")
                PHY_2M
            }
            supportedPhys.contains(PHY_CODED) -> {
                logd(TAG, "选择Coded PHY作为最优配置")
                PHY_CODED
            }
            else -> {
                logd(TAG, "使用1M PHY作为默认配置")
                PHY_1M
            }
        }
    }

    /**
     * 开始PHY协商
     */
    fun startPhyNegotiation(gatt: BluetoothGatt?, callback: VTBLECallback) {
        if (gatt == null) {
            logd(TAG, MSG_GATT_NULL)
            return
        }

        if (phyNegotiationAttempted) {
            logd(TAG, MSG_PHY_NEGOTIATION_ATTEMPTED)
            return
        }

        phyCallback = callback
        phyNegotiationAttempted = true
        phyNegotiationFailed = false
        phyNegotiationRetryCount = 0

        val optimalPhy = getOptimalPhy()
        val supportedPhys = getSupportedPhys()

        logd(TAG, "=== 开始PHY协商 ===")
        logd(TAG, "目标PHY: $optimalPhy")
        logd(TAG, "当前Android版本: ${Build.VERSION.SDK_INT}")
        logd(TAG, "设备支持的PHY: $supportedPhys")

        // 检查API版本要求
        if (!isPhyNegotiationSupported()) {
            logd(TAG, MSG_ANDROID_VERSION_NOT_SUPPORTED)
            handlePhyNegotiationFailure(optimalPhy, PHY_1M)
            return
        }

        // 如果设备只支持1M PHY或目标就是1M PHY，直接成功
        if (shouldSkipNegotiation(supportedPhys, optimalPhy)) {
            handleDirectSuccess(callback)
            return
        }

        // 设置PHY协商超时
        setupPhyNegotiationTimeout(optimalPhy)

        // 开始PHY协商
        requestPhyUpdate(gatt, optimalPhy)
    }
    
    /**
     * 检查是否应该跳过PHY协商
     */
    private fun shouldSkipNegotiation(supportedPhys: List<Int>, optimalPhy: Int): Boolean {
        return when {
            supportedPhys.size == 1 && supportedPhys.contains(PHY_1M) -> {
                logd(TAG, MSG_DEVICE_ONLY_SUPPORTS_1M)
                true
            }
            optimalPhy == PHY_1M -> {
                logd(TAG, MSG_TARGET_IS_1M_PHY)
                true
            }
            else -> false
        }
    }
    
    /**
     * 处理直接成功的情况
     */
    private fun handleDirectSuccess(callback: VTBLECallback) {
        logd(TAG, MSG_1M_PHY_DEFAULT)
        phyNegotiationFailed = false
        currentTxPhy = PHY_1M
        currentRxPhy = PHY_1M
        callback.onPhyNegotiationSuccess(PHY_1M, PHY_1M)
    }
    
    /**
     * 设置PHY协商超时
     */
    private fun setupPhyNegotiationTimeout(optimalPhy: Int) {
        phyNegotiationTimeoutJob = VTCoroutineUtil.delayInScope(SCOPE_NAME, PHY_NEGOTIATION_TIMEOUT) {
            logd(TAG, "=== PHY协商超时 ===")
            logd(TAG, "PHY协商时间超过 ${PHY_NEGOTIATION_TIMEOUT}ms")
            handlePhyNegotiationFailure(optimalPhy, currentTxPhy)
        }
    }

    /**
     * 重试PHY协商（用于降级PHY）
     */
    fun retryPhyNegotiation(gatt: BluetoothGatt?, fallbackPhy: Int) {
        if (gatt == null) {
            logd(TAG, MSG_GATT_NULL)
            return
        }

        logd(TAG, "=== 重试PHY协商 ===")
        logd(TAG, "降级到PHY: $fallbackPhy")

        // 设置PHY协商超时
        phyNegotiationTimeoutJob = VTCoroutineUtil.delayInScope(SCOPE_NAME, PHY_NEGOTIATION_TIMEOUT) {
            logd(TAG, "=== PHY协商重试超时 ===")
            logd(TAG, "PHY协商重试时间超过 ${PHY_NEGOTIATION_TIMEOUT}ms")
            handlePhyNegotiationFailure(fallbackPhy, currentTxPhy)
        }

        // 重试PHY协商
        requestPhyUpdate(gatt, fallbackPhy)
    }

    /**
     * 请求PHY更新
     */
    @SuppressLint("MissingPermission")
    private fun requestPhyUpdate(gatt: BluetoothGatt, phy: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                logd(TAG, "请求PHY更新: $phy")
                gatt.setPreferredPhy(phy, phy, BluetoothDevice.PHY_OPTION_NO_PREFERRED)
                logd(TAG, "PHY更新请求已发送")
            } catch (e: Exception) {
                logd(TAG, "PHY更新请求异常: ${e.message}")
                handlePhyNegotiationFailure(phy, currentTxPhy)
            }
        } else {
            logd(TAG, MSG_ANDROID_VERSION_NOT_SUPPORTED)
            handlePhyNegotiationFailure(phy, PHY_1M)
        }
    }

    /**
     * 读取当前PHY
     */
    fun readPhy(gatt: BluetoothGatt?, callback: VTBLECallback) {
        if (gatt == null) {
            logd(TAG, MSG_GATT_NULL)
            callback.onPhyReadFailed()
            return
        }

        if (!isPhyReadSupported()) {
            logd(TAG, MSG_PHY_READ_NOT_SUPPORTED)
            callback.onPhyReadFailed()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                logd(TAG, "读取当前PHY...")
                gatt.readPhy()
                logd(TAG, "PHY读取请求已发送")
            } catch (e: Exception) {
                logd(TAG, "PHY读取异常: ${e.message}")
                callback.onPhyReadFailed()
            }
        }
    }

    /**
     * 处理PHY读取结果
     */
    fun handlePhyReadResult(txPhy: Int, rxPhy: Int, status: Int) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            logd(TAG, "=== PHY读取成功 ===")
            logd(TAG, "TX PHY: $txPhy, RX PHY: $rxPhy")
            currentTxPhy = txPhy
            currentRxPhy = rxPhy
            phyCallback?.onPhyReadSuccess(txPhy, rxPhy)
        } else {
            logd(TAG, "=== PHY读取失败 ===")
            logd(TAG, "状态码: $status")
            phyCallback?.onPhyReadFailed()
        }
    }

    /**
     * 处理PHY更新结果
     */
    fun handlePhyUpdateResult(txPhy: Int, rxPhy: Int, status: Int) {
        phyNegotiationTimeoutJob?.cancel()

        if (status == BluetoothGatt.GATT_SUCCESS) {
            logd(TAG, "=== PHY更新成功 ===")
            logd(TAG, "新的TX PHY: $txPhy, 新的RX PHY: $rxPhy")
            currentTxPhy = txPhy
            currentRxPhy = rxPhy
            phyNegotiationFailed = false
            phyCallback?.onPhyUpdateSuccess(txPhy, rxPhy)
            phyCallback?.onPhyNegotiationSuccess(txPhy, rxPhy)
        } else {
            logd(TAG, "=== PHY更新失败 ===")
            logd(TAG, "状态码: $status")
            handlePhyNegotiationFailure(getOptimalPhy(), currentTxPhy)
        }
    }

    /**
     * 处理PHY协商失败
     */
    private fun handlePhyNegotiationFailure(requestedPhy: Int, actualPhy: Int) {
        logd(TAG, "=== PHY协商失败 ===")
        logd(TAG, "请求的PHY: $requestedPhy")
        logd(TAG, "实际的PHY: $actualPhy")
        logd(TAG, "PHY协商失败，使用默认1M PHY")

        // 直接使用默认的1M PHY，不进行重试
        phyNegotiationFailed = false  // 标记为成功，因为使用默认PHY
        currentTxPhy = PHY_1M
        currentRxPhy = PHY_1M

        // 通知回调PHY协商成功（使用默认PHY）
        phyCallback?.onPhyNegotiationSuccess(PHY_1M, PHY_1M)
    }

    /**
     * 检查是否支持PHY协商
     */
    private fun isPhyNegotiationSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    }

    /**
     * 检查是否支持PHY读取
     */
    private fun isPhyReadSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    }

    /**
     * 获取当前TX PHY
     */
    fun getCurrentTxPhy(): Int {
        return currentTxPhy
    }

    /**
     * 获取当前RX PHY
     */
    fun getCurrentRxPhy(): Int {
        return currentRxPhy
    }

    /**
     * 检查PHY协商是否成功
     */
    fun isPhyNegotiationSuccessful(): Boolean {
        return phyNegotiationAttempted && !phyNegotiationFailed
    }

    /**
     * 重置PHY协商状态
     */
    fun resetPhyNegotiationState() {
        phyNegotiationAttempted = false
        phyNegotiationFailed = false
        phyNegotiationRetryCount = 0
        currentTxPhy = PHY_1M
        currentRxPhy = PHY_1M
        phyNegotiationTimeoutJob?.cancel()
        phyRetryJob?.cancel()
        phyCallback = null
        
        // 清除缓存
        cachedLe2MPhySupported = null
        cachedLeCodedPhySupported = null
        cachedSupportedPhys = null
        
        logd(TAG, "PHY协商状态已重置")
    }

    /**
     * 获取PHY描述信息
     */
    fun getPhyDescription(phy: Int): String {
        return when (phy) {
            PHY_1M -> "1M PHY"
            PHY_2M -> "2M PHY"
            PHY_CODED -> "Coded PHY"
            else -> "Unknown PHY ($phy)"
        }
    }

    /**
     * 获取PHY性能描述
     */
    fun getPhyPerformanceDescription(phy: Int): String {
        return when (phy) {
            PHY_1M -> "标准性能，兼容性最好"
            PHY_2M -> "高性能，数据传输速度翻倍"
            PHY_CODED -> "长距离传输，抗干扰能力强"
            else -> "未知性能"
        }
    }
} 