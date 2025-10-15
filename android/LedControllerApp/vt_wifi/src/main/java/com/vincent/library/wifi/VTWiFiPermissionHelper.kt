package com.vincent.library.wifi

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.util.Log

/**
 * WiFi权限帮助类
 * 用于检查和请求WiFi相关的权限
 */
object VTWiFiPermissionHelper {
    private const val TAG = "WiFiPermissionHelper"
    const val LOCATION_PERMISSION_REQUEST_CODE = 1001

    /**
     * 检查所有必需的权限是否已授予
     * @param context 上下文
     * @return 是否所有权限都已授予
     */
    fun hasAllPermissions(context: Context): Boolean {
        return hasInternetPermission(context) &&
                hasNetworkStatePermission(context) &&
                hasWifiStatePermission(context) &&
                hasChangeWifiStatePermission(context) &&
                hasChangeNetworkStatePermission(context) &&
                hasLocationPermission(context)
    }

    /**
     * 检查网络权限
     */
    fun hasInternetPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 检查网络状态权限
     */
    fun hasNetworkStatePermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_NETWORK_STATE) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 检查WiFi状态权限
     */
    fun hasWifiStatePermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_WIFI_STATE) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 检查修改WiFi状态权限
     */
    fun hasChangeWifiStatePermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CHANGE_WIFI_STATE) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 检查修改网络状态权限
     */
    fun hasChangeNetworkStatePermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CHANGE_NETWORK_STATE) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 检查位置权限（WiFi扫描需要）
     */
    fun hasLocationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Android 6.0以下不需要运行时权限
        }
    }

    /**
     * 获取缺失的权限列表
     * @param context 上下文
     * @return 缺失的权限列表
     */
    fun getMissingPermissions(context: Context): List<String> {
        val missingPermissions = mutableListOf<String>()

        if (!hasInternetPermission(context)) {
            missingPermissions.add(Manifest.permission.INTERNET)
        }
        if (!hasNetworkStatePermission(context)) {
            missingPermissions.add(Manifest.permission.ACCESS_NETWORK_STATE)
        }
        if (!hasWifiStatePermission(context)) {
            missingPermissions.add(Manifest.permission.ACCESS_WIFI_STATE)
        }
        if (!hasChangeWifiStatePermission(context)) {
            missingPermissions.add(Manifest.permission.CHANGE_WIFI_STATE)
        }
        if (!hasChangeNetworkStatePermission(context)) {
            missingPermissions.add(Manifest.permission.CHANGE_NETWORK_STATE)
        }
        if (!hasLocationPermission(context)) {
            missingPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        return missingPermissions
    }

    /**
     * 请求位置权限（WiFi扫描需要）
     * @param activity Activity实例
     * @return 是否已授予权限
     */
    fun requestLocationPermission(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true // Android 6.0以下不需要运行时权限
        }

        if (hasLocationPermission(activity)) {
            return true
        }

        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            LOCATION_PERMISSION_REQUEST_CODE
        )
        return false
    }

    /**
     * 请求所有缺失的权限
     * @param activity Activity实例
     * @param requestCode 请求码
     * @return 是否已授予所有权限
     */
    fun requestAllPermissions(activity: Activity, requestCode: Int = LOCATION_PERMISSION_REQUEST_CODE): Boolean {
        val missingPermissions = getMissingPermissions(activity)
        
        if (missingPermissions.isEmpty()) {
            return true
        }

        ActivityCompat.requestPermissions(activity, missingPermissions.toTypedArray(), requestCode)
        return false
    }

    /**
     * 检查权限请求结果
     * @param requestCode 请求码
     * @param permissions 权限数组
     * @param grantResults 授权结果数组
     * @return 是否所有权限都已授予
     */
    fun checkPermissionResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray): Boolean {
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            return grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        }
        return false
    }

    /**
     * 获取权限说明文本
     * @param context 上下文
     * @return 权限说明文本
     */
    fun getPermissionExplanation(context: Context): String {
        val missingPermissions = getMissingPermissions(context)
        if (missingPermissions.isEmpty()) {
            return "所有权限都已授予"
        }

        val explanations = mutableListOf<String>()
        
        if (missingPermissions.contains(Manifest.permission.INTERNET)) {
            explanations.add("网络访问权限：用于连接ESP32设备")
        }
        if (missingPermissions.contains(Manifest.permission.ACCESS_NETWORK_STATE)) {
            explanations.add("网络状态权限：用于检查网络连接状态")
        }
        if (missingPermissions.contains(Manifest.permission.ACCESS_WIFI_STATE)) {
            explanations.add("WiFi状态权限：用于读取WiFi连接信息")
        }
        if (missingPermissions.contains(Manifest.permission.CHANGE_WIFI_STATE)) {
            explanations.add("WiFi控制权限：用于连接和断开WiFi")
        }
        if (missingPermissions.contains(Manifest.permission.CHANGE_NETWORK_STATE)) {
            explanations.add("网络控制权限：用于修改网络配置")
        }
        if (missingPermissions.contains(Manifest.permission.ACCESS_FINE_LOCATION)) {
            explanations.add("位置权限：用于扫描WiFi热点（Android 6.0+要求）")
        }

        return explanations.joinToString("\n")
    }

    /**
     * 记录权限状态到日志
     * @param context 上下文
     */
    fun logPermissionStatus(context: Context) {
        Log.d(TAG, "=== WiFi权限状态 ===")
        Log.d(TAG, "网络权限: ${if (hasInternetPermission(context)) "✅" else "❌"}")
        Log.d(TAG, "网络状态权限: ${if (hasNetworkStatePermission(context)) "✅" else "❌"}")
        Log.d(TAG, "WiFi状态权限: ${if (hasWifiStatePermission(context)) "✅" else "❌"}")
        Log.d(TAG, "WiFi控制权限: ${if (hasChangeWifiStatePermission(context)) "✅" else "❌"}")
        Log.d(TAG, "网络控制权限: ${if (hasChangeNetworkStatePermission(context)) "✅" else "❌"}")
        Log.d(TAG, "位置权限: ${if (hasLocationPermission(context)) "✅" else "❌"}")
        
        val missingPermissions = getMissingPermissions(context)
        if (missingPermissions.isNotEmpty()) {
            Log.w(TAG, "缺失权限: ${missingPermissions.joinToString(", ")}")
        } else {
            Log.d(TAG, "所有权限都已授予 ✅")
        }
        Log.d(TAG, "==================")
    }
}