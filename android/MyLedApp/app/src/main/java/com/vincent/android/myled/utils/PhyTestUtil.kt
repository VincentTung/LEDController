package com.vincent.android.myled.utils

import android.content.Context
import android.os.Build
import com.vincent.android.myled.ble.VTPhyManager
import com.vincent.android.myled.ble.VTBluetoothUtil

/**
 * PHY测试工具类
 * 用于测试和验证PHY相关功能
 */
object PhyTestUtil {
    
    /**
     * 测试设备PHY支持情况
     */
    fun testPhySupport(context: Context): String {
        val phyManager = VTPhyManager(context)
        val bluetoothAdapter = VTBluetoothUtil.getBluetoothAdapter(context)
        
        val result = StringBuilder()
        result.append("=== PHY支持测试 ===\n")
        result.append("Android版本: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})\n")
        result.append("蓝牙适配器: ${if (bluetoothAdapter != null) "可用" else "不可用"}\n\n")
        
        // 测试2M PHY支持
        val supports2M = phyManager.isLe2MPhySupported()
        result.append("2M PHY支持: ${if (supports2M) "✓" else "✗"}\n")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            result.append("  (需要Android 8.0+)\n")
        }
        
        // 测试Coded PHY支持
        val supportsCoded = phyManager.isLeCodedPhySupported()
        result.append("Coded PHY支持: ${if (supportsCoded) "✓" else "✗"}\n")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            result.append("  (需要Android 9.0+)\n")
        }
        
        // 获取支持的PHY列表
        val supportedPhys = phyManager.getSupportedPhys()
        result.append("支持的PHY列表: $supportedPhys\n")
        
        // 获取最优PHY
        val optimalPhy = phyManager.getOptimalPhy()
        result.append("最优PHY: ${phyManager.getPhyDescription(optimalPhy)}\n")
        result.append("性能描述: ${phyManager.getPhyPerformanceDescription(optimalPhy)}\n")
        
        return result.toString()
    }
    
    /**
     * 测试PHY协商功能
     */
    fun testPhyNegotiation(context: Context): String {
        val phyManager = VTPhyManager(context)
        
        val result = StringBuilder()
        result.append("=== PHY协商测试 ===\n")
        
        // 检查API版本支持
        val supportsNegotiation = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        result.append("PHY协商支持: ${if (supportsNegotiation) "✓" else "✗"}\n")
        if (!supportsNegotiation) {
            result.append("  (需要Android 8.0+)\n")
        }
        
        // 检查PHY读取支持
        val supportsRead = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        result.append("PHY读取支持: ${if (supportsRead) "✓" else "✗"}\n")
        if (!supportsRead) {
            result.append("  (需要Android 8.0+)\n")
        }
        
        // 测试设备支持情况
        val supports2M = phyManager.isLe2MPhySupported()
        val supportsCoded = phyManager.isLeCodedPhySupported()
        
        result.append("\n设备能力评估:\n")
        result.append("- 2M PHY: ${if (supports2M) "支持" else "不支持"}\n")
        result.append("- Coded PHY: ${if (supportsCoded) "支持" else "不支持"}\n")
        
        if (supports2M || supportsCoded) {
            result.append("✓ 设备支持高级PHY，可以进行PHY协商优化\n")
        } else {
            result.append("✗ 设备仅支持1M PHY，无法进行PHY协商优化\n")
        }
        
        return result.toString()
    }
    
    /**
     * 生成PHY优化建议
     */
    fun generatePhyOptimizationAdvice(context: Context): String {
        val phyManager = VTPhyManager(context)
        
        val result = StringBuilder()
        result.append("=== PHY优化建议 ===\n")
        
        // 检查Android版本
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> {
                result.append("✓ Android 9.0+，支持所有PHY功能\n")
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                result.append("✓ Android 8.0+，支持2M PHY\n")
                result.append("⚠ Android 9.0+ 可支持Coded PHY\n")
            }
            else -> {
                result.append("✗ Android版本过低，不支持PHY协商\n")
                result.append("建议升级到Android 8.0+\n")
            }
        }
        
        // 检查设备支持
        val supports2M = phyManager.isLe2MPhySupported()
        val supportsCoded = phyManager.isLeCodedPhySupported()
        
        result.append("\n设备优化建议:\n")
        when {
            supportsCoded -> {
                result.append("✓ 设备支持Coded PHY\n")
                result.append("  建议: 使用Coded PHY获得最佳长距离传输性能\n")
            }
            supports2M -> {
                result.append("✓ 设备支持2M PHY\n")
                result.append("  建议: 使用2M PHY获得最佳数据传输速度\n")
            }
            else -> {
                result.append("⚠ 设备仅支持1M PHY\n")
                result.append("  建议: 确保设备固件支持高级PHY\n")
            }
        }
        
        // 连接质量建议
        result.append("\n连接质量建议:\n")
        result.append("- 保持设备距离在1-3米范围内\n")
        result.append("- 避免金属障碍物和强电磁干扰\n")
        result.append("- 定期检查PHY协商状态\n")
        result.append("- 监控连接质量和数据传输速度\n")
        
        return result.toString()
    }
    
    /**
     * 获取详细的PHY信息报告
     */
    fun getDetailedPhyReport(context: Context): String {
        val phyManager = VTPhyManager(context)
        
        val result = StringBuilder()
        result.append("=== 详细PHY信息报告 ===\n\n")
        
        // 系统信息
        result.append("系统信息:\n")
        result.append("- Android版本: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})\n")
        result.append("- 设备型号: ${Build.MODEL}\n")
        result.append("- 制造商: ${Build.MANUFACTURER}\n\n")
        
        // PHY支持测试
        result.append(testPhySupport(context))
        result.append("\n")
        
        // PHY协商测试
        result.append(testPhyNegotiation(context))
        result.append("\n")
        
        // 优化建议
        result.append(generatePhyOptimizationAdvice(context))
        
        return result.toString()
    }
} 