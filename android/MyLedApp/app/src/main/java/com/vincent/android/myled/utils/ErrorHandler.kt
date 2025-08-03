package com.vincent.android.myled.utils

import android.content.Context
import android.widget.Toast
import com.vincent.android.myled.ble.logd

/**
 * 错误处理工具类
 * 统一处理应用中的错误和异常
 */
object ErrorHandler {
    
    /**
     * 处理BLE相关错误
     */
    fun handleBLEError(context: Context, errorCode: Int) {
        val message = when (errorCode) {
            1 -> "蓝牙未开启"
            2 -> "设备未找到"
            3 -> "连接超时"
            4 -> "连接失败"
            5 -> "写入数据失败"
            6 -> "读取数据失败"
            7 -> "服务发现失败"
            8 -> "特征值未找到"
            else -> "未知错误 (错误码: $errorCode)"
        }
        
        showError(context, message)
        logd("BLE Error: $message")
    }
    
    /**
     * 处理权限相关错误
     */
    fun handlePermissionError(context: Context, permission: String) {
        val message = when (permission) {
            android.Manifest.permission.ACCESS_FINE_LOCATION -> "需要位置权限才能扫描蓝牙设备"
            android.Manifest.permission.BLUETOOTH -> "需要蓝牙权限"
            android.Manifest.permission.BLUETOOTH_ADMIN -> "需要蓝牙管理权限"
            else -> "需要权限: $permission"
        }
        
        showError(context, message)
        logd("Permission Error: $message")
    }
    
    /**
     * 处理网络相关错误
     */
    fun handleNetworkError(context: Context, error: String) {
        val message = "网络错误: $error"
        showError(context, message)
        logd("Network Error: $error")
    }
    
    /**
     * 处理数据相关错误
     */
    fun handleDataError(context: Context, error: String) {
        val message = "数据错误: $error"
        showError(context, message)
        logd("Data Error: $error")
    }
    
    /**
     * 处理通用错误
     */
    fun handleGeneralError(context: Context, error: String, throwable: Throwable? = null) {
        showError(context, error)
        logd("General Error: $error")
        throwable?.let {
            logd("Exception: ${it.message}")
            it.printStackTrace()
        }
    }
    
    /**
     * 显示错误信息
     */
    private fun showError(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
    
    /**
     * 安全执行代码块
     */
    inline fun <T> safeExecute(
        context: Context,
        operation: String,
        defaultValue: T,
        block: () -> T
    ): T {
        return try {
            block()
        } catch (e: Exception) {
            handleGeneralError(context, "$operation 执行失败", e)
            defaultValue
        }
    }
    
    /**
     * 安全执行异步代码块
     */
    inline fun safeExecuteAsync(
        context: Context,
        operation: String,
        block: () -> Unit
    ) {
        try {
            block()
        } catch (e: Exception) {
            handleGeneralError(context, "$operation 执行失败", e)
        }
    }
} 