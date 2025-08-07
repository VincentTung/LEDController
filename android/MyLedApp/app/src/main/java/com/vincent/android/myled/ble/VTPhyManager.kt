package com.vincent.android.myled.ble

import VTBLECallback
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import com.vincent.android.myled.utils.PHY_1M
import com.vincent.android.myled.utils.PHY_2M
import com.vincent.android.myled.utils.PHY_CODED
import com.vincent.android.myled.utils.PHY_NEGOTIATION_RETRY_COUNT
import com.vincent.android.myled.utils.PHY_NEGOTIATION_RETRY_DELAY
import com.vincent.android.myled.utils.PHY_NEGOTIATION_TIMEOUT
import com.vincent.android.myled.utils.logd
import com.vincent.android.myled.utils.CoroutineUtil
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

/**
 * PHY管理工具类
 * 负责处理BLE PHY协商、版本检查和设备兼容性检查
 */
class VTPhyManager(private val context: Context) {
    
    companion object {
        private const val TAG = "VTPhyManager"
    }
    
    // PHY协商状态管理
    private var phyNegotiationAttempted = false
    private var phyNegotiationFailed = false
    private var currentTxPhy = PHY_1M
    private var currentRxPhy = PHY_1M
    private var phyNegotiationRetryCount = 0
    
    // 协程Job管理
    private var phyNegotiationTimeoutJob: Job? = null
    private var phyRetryJob: Job? = null
    
    // 回调接口
    private var phyCallback: VTBLECallback? = null
    
    /**
     * 检查设备是否支持2M PHY
     * 需要Android 8.0+ (API 26+)
     */
    fun isLe2MPhySupported(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val bluetoothAdapter = VTBluetoothUtil.getBluetoothAdapter(context)
                bluetoothAdapter?.isLe2MPhySupported() ?: false
            } catch (e: Exception) {
                logd("检查2M PHY支持时发生异常: ${e.message}")
                false
            }
        } else {
            logd("当前Android版本不支持2M PHY检查 (需要API 26+)")
            false
        }
    }
    
    /**
     * 检查设备是否支持Coded PHY
     * 需要Android 9.0+ (API 28+)
     */
    fun isLeCodedPhySupported(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val bluetoothAdapter = VTBluetoothUtil.getBluetoothAdapter(context)
                bluetoothAdapter?.isLeCodedPhySupported() ?: false
            } catch (e: Exception) {
                logd("检查Coded PHY支持时发生异常: ${e.message}")
                false
            }
        } else {
            logd("当前Android版本不支持Coded PHY检查 (需要API 28+)")
            false
        }
    }
    
    /**
     * 获取设备支持的PHY列表
     */
    fun getSupportedPhys(): List<Int> {
        val supportedPhys = mutableListOf<Int>()
        
        // 1M PHY总是支持的
        supportedPhys.add(PHY_1M)
        
        // 检查2M PHY支持
        if (isLe2MPhySupported()) {
            supportedPhys.add(PHY_2M)
            logd("设备支持2M PHY")
        } else {
            logd("设备不支持2M PHY")
        }
        
        // 检查Coded PHY支持
        if (isLeCodedPhySupported()) {
            supportedPhys.add(PHY_CODED)
            logd("设备支持Coded PHY")
        } else {
            logd("设备不支持Coded PHY")
        }
        
        logd("设备支持的PHY列表: $supportedPhys")
        return supportedPhys
    }
    
    /**
     * 获取最优PHY配置
     * 优先级：2M > Coded > 1M
     */
    fun getOptimalPhy(): Int {
        val supportedPhys = getSupportedPhys()
        
        return when {
            supportedPhys.contains(PHY_2M) -> {
                logd("选择2M PHY作为最优配置")
                PHY_2M
            }
            supportedPhys.contains(PHY_CODED) -> {
                logd("选择Coded PHY作为最优配置")
                PHY_CODED
            }
            else -> {
                logd("使用1M PHY作为默认配置")
                PHY_1M
            }
        }
    }
    
    /**
     * 开始PHY协商
     */
    fun startPhyNegotiation(gatt: BluetoothGatt?, callback: VTBLECallback) {
        if (gatt == null) {
            logd("GATT为空，无法开始PHY协商")
            return
        }
        
        if (phyNegotiationAttempted) {
            logd("PHY协商已尝试过，跳过重复请求")
            return
        }
        
        phyCallback = callback
        phyNegotiationAttempted = true
        phyNegotiationFailed = false
        phyNegotiationRetryCount = 0
        
        val optimalPhy = getOptimalPhy()
        
        logd("=== 开始PHY协商 ===")
        logd("目标PHY: $optimalPhy")
        logd("当前Android版本: ${Build.VERSION.SDK_INT}")
        
        // 检查API版本要求
        if (!isPhyNegotiationSupported()) {
            logd("当前Android版本不支持PHY协商 (需要API 26+)")
            handlePhyNegotiationFailure(optimalPhy, PHY_1M)
            return
        }
        
        // 设置PHY协商超时
        phyNegotiationTimeoutJob = CoroutineUtil.delayInScope("VTPhyManager", PHY_NEGOTIATION_TIMEOUT) {
            logd("=== PHY协商超时 ===")
            logd("PHY协商时间超过 ${PHY_NEGOTIATION_TIMEOUT}ms")
            handlePhyNegotiationFailure(optimalPhy, currentTxPhy)
        }
        
        // 开始PHY协商
        requestPhyUpdate(gatt, optimalPhy)
    }
    
    /**
     * 重试PHY协商（用于降级PHY）
     */
    fun retryPhyNegotiation(gatt: BluetoothGatt?, fallbackPhy: Int) {
        if (gatt == null) {
            logd("GATT为空，无法重试PHY协商")
            return
        }
        
        logd("=== 重试PHY协商 ===")
        logd("降级到PHY: $fallbackPhy")
        
        // 设置PHY协商超时
        phyNegotiationTimeoutJob = CoroutineUtil.delayInScope("VTPhyManager", PHY_NEGOTIATION_TIMEOUT) {
            logd("=== PHY协商重试超时 ===")
            logd("PHY协商重试时间超过 ${PHY_NEGOTIATION_TIMEOUT}ms")
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
                logd("请求PHY更新: $phy")
                gatt.setPreferredPhy(phy, phy, BluetoothDevice.PHY_OPTION_NO_PREFERRED)
                logd("PHY更新请求已发送")
            } catch (e: Exception) {
                logd("PHY更新请求异常: ${e.message}")
                handlePhyNegotiationFailure(phy, currentTxPhy)
            }
        } else {
            logd("当前Android版本不支持PHY更新 (需要API 26+)")
            handlePhyNegotiationFailure(phy, PHY_1M)
        }
    }
    
    /**
     * 读取当前PHY
     */
    fun readPhy(gatt: BluetoothGatt?, callback: VTBLECallback) {
        if (gatt == null) {
            logd("GATT为空，无法读取PHY")
            callback.onPhyReadFailed()
            return
        }
        
        if (!isPhyReadSupported()) {
            logd("当前Android版本不支持PHY读取 (需要API 26+)")
            callback.onPhyReadFailed()
            return
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                logd("读取当前PHY...")
                gatt.readPhy()
                logd("PHY读取请求已发送")
            } catch (e: Exception) {
                logd("PHY读取异常: ${e.message}")
                callback.onPhyReadFailed()
            }
        }
    }
    
    /**
     * 处理PHY读取结果
     */
    fun handlePhyReadResult(txPhy: Int, rxPhy: Int, status: Int) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            logd("=== PHY读取成功 ===")
            logd("TX PHY: $txPhy, RX PHY: $rxPhy")
            currentTxPhy = txPhy
            currentRxPhy = rxPhy
            phyCallback?.onPhyReadSuccess(txPhy, rxPhy)
        } else {
            logd("=== PHY读取失败 ===")
            logd("状态码: $status")
            phyCallback?.onPhyReadFailed()
        }
    }
    
    /**
     * 处理PHY更新结果
     */
    fun handlePhyUpdateResult(txPhy: Int, rxPhy: Int, status: Int) {
        phyNegotiationTimeoutJob?.cancel()
        
        if (status == BluetoothGatt.GATT_SUCCESS) {
            logd("=== PHY更新成功 ===")
            logd("新的TX PHY: $txPhy, 新的RX PHY: $rxPhy")
            currentTxPhy = txPhy
            currentRxPhy = rxPhy
            phyNegotiationFailed = false
            phyCallback?.onPhyUpdateSuccess(txPhy, rxPhy)
            phyCallback?.onPhyNegotiationSuccess(txPhy, rxPhy)
        } else {
            logd("=== PHY更新失败 ===")
            logd("状态码: $status")
            handlePhyNegotiationFailure(getOptimalPhy(), currentTxPhy)
        }
    }
    
    /**
     * 处理PHY协商失败
     */
    private fun handlePhyNegotiationFailure(requestedPhy: Int, actualPhy: Int) {
        logd("=== PHY协商失败 ===")
        logd("请求的PHY: $requestedPhy")
        logd("实际的PHY: $actualPhy")
        
        if (phyNegotiationRetryCount < PHY_NEGOTIATION_RETRY_COUNT) {
            phyNegotiationRetryCount++
            logd("准备第 $phyNegotiationRetryCount 次PHY协商重试...")
            
            phyRetryJob = CoroutineUtil.delayInScope("VTPhyManager", PHY_NEGOTIATION_RETRY_DELAY) {
                logd("开始PHY协商重试...")
                // 重试时使用降级的PHY
                val fallbackPhy = when (requestedPhy) {
                    PHY_2M -> PHY_1M
                    PHY_CODED -> PHY_2M
                    else -> PHY_1M
                }
                
                if (fallbackPhy != requestedPhy) {
                    logd("降级到PHY: $fallbackPhy")
                    // 通知回调，让外部决定是否重试
                    phyCallback?.onPhyNegotiationFailed(requestedPhy, actualPhy)
                } else {
                    logd("无法进一步降级PHY")
                    phyNegotiationFailed = true
                    phyCallback?.onPhyNegotiationFailed(requestedPhy, actualPhy)
                }
            }
        } else {
            logd("PHY协商重试次数已达上限")
            phyNegotiationFailed = true
            phyCallback?.onPhyNegotiationFailed(requestedPhy, actualPhy)
        }
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
        logd("PHY协商状态已重置")
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