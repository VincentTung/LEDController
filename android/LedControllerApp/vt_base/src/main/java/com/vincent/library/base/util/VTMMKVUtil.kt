package com.vincent.library.base.util

import android.content.Context
import com.tencent.mmkv.MMKV


/**
 * MMKV工具类 - 提供统一的MMKV操作接口
 * 支持多实例管理，每个模块可以使用独立的MMKV实例
 */
object VTMMKVUtil {

    private const val TAG = "MMKVUtil"
    private var isInitialized = false
    private val mmkvInstances = mutableMapOf<String, MMKV>()
    
    /**
     * 初始化MMKV
     * @param context 应用上下文
     */
    fun init(context: Context) {
        try {
            if (!isInitialized) {
                MMKV.initialize(context.applicationContext)
                isInitialized = true
                 logd(TAG, "=== MMKVUtil: 全局初始化完成 ===")
            }
        } catch (e: Exception) {
             loge(TAG, "MMKVUtil 全局初始化失败: ${e.message}")
        }
    }
    
    /**
     * 获取指定ID的MMKV实例
     * @param mmkvId MMKV实例ID
     * @return MMKV实例，如果初始化失败返回null
     */
    fun getMMKV(mmkvId: String): MMKV? {
        return try {
            if (!isInitialized) {
                 loge(TAG, "MMKVUtil 未初始化，请先调用init()方法")
                return null
            }
            
            mmkvInstances.getOrPut(mmkvId) {
                MMKV.mmkvWithID(mmkvId).also {
                     logd(TAG, "=== MMKVUtil: 创建MMKV实例 ===")
                     logd(TAG, "实例ID: $mmkvId")
                }
            }
        } catch (e: Exception) {
            loge(TAG, "MMKVUtil 获取实例失败: ${e.message}")
            null
        }
    }

    /**
     * 安全地获取Int值
     * @param mmkvId MMKV实例ID
     * @param key 键名
     * @param defaultValue 默认值
     * @return Int值
     */
    fun getInt(mmkvId: String, key: String, defaultValue: Int = 0): Int {
        return try {
            getMMKV(mmkvId)?.decodeInt(key, defaultValue) ?: defaultValue
        } catch (e: Exception) {
             loge(TAG, "MMKVUtil 获取Int值失败: key=$key, error=${e.message}")
            defaultValue
        }
    }
    
    /**
     * 安全地设置Int值
     * @param mmkvId MMKV实例ID
     * @param key 键名
     * @param value 值
     * @return 是否设置成功
     */
    fun putInt(mmkvId: String, key: String, value: Int): Boolean {
        return try {
            getMMKV(mmkvId)?.encode(key, value) ?: false
        } catch (e: Exception) {
             loge(TAG, "MMKVUtil 设置Int值失败: key=$key, value=$value, error=${e.message}")
            false
        }
    }
    
    /**
     * 安全地获取Long值
     * @param mmkvId MMKV实例ID
     * @param key 键名
     * @param defaultValue 默认值
     * @return Long值
     */
    fun getLong(mmkvId: String, key: String, defaultValue: Long = 0L): Long {
        return try {
            getMMKV(mmkvId)?.decodeLong(key, defaultValue) ?: defaultValue
        } catch (e: Exception) {
             loge(TAG, "MMKVUtil 获取Long值失败: key=$key, error=${e.message}")
            defaultValue
        }
    }
    
    /**
     * 安全地设置Long值
     * @param mmkvId MMKV实例ID
     * @param key 键名
     * @param value 值
     * @return 是否设置成功
     */
    fun putLong(mmkvId: String, key: String, value: Long): Boolean {
        return try {
            getMMKV(mmkvId)?.encode(key, value) ?: false
        } catch (e: Exception) {
             loge(TAG, "MMKVUtil 设置Long值失败: key=$key, value=$value, error=${e.message}")
            false
        }
    }
    
    /**
     * 安全地获取String值
     * @param mmkvId MMKV实例ID
     * @param key 键名
     * @param defaultValue 默认值
     * @return String值
     */
    fun getString(mmkvId: String, key: String, defaultValue: String? = null): String? {
        return try {
            getMMKV(mmkvId)?.decodeString(key, defaultValue)
        } catch (e: Exception) {
             loge(TAG, "MMKVUtil 获取String值失败: key=$key, error=${e.message}")
            defaultValue
        }
    }
    
    /**
     * 安全地设置String值
     * @param mmkvId MMKV实例ID
     * @param key 键名
     * @param value 值
     * @return 是否设置成功
     */
    fun putString(mmkvId: String, key: String, value: String?): Boolean {
        return try {
            getMMKV(mmkvId)?.encode(key, value) ?: false
        } catch (e: Exception) {
             loge(TAG, "MMKVUtil 设置String值失败: key=$key, value=$value, error=${e.message}")
            false
        }
    }
    
    /**
     * 安全地获取Boolean值
     * @param mmkvId MMKV实例ID
     * @param key 键名
     * @param defaultValue 默认值
     * @return Boolean值
     */
    fun getBoolean(mmkvId: String, key: String, defaultValue: Boolean = false): Boolean {
        return try {
            getMMKV(mmkvId)?.decodeBool(key, defaultValue) ?: defaultValue
        } catch (e: Exception) {
             loge(TAG, "MMKVUtil 获取Boolean值失败: key=$key, error=${e.message}")
            defaultValue
        }
    }
    
    /**
     * 安全地设置Boolean值
     * @param mmkvId MMKV实例ID
     * @param key 键名
     * @param value 值
     * @return 是否设置成功
     */
    fun putBoolean(mmkvId: String, key: String, value: Boolean): Boolean {
        return try {
            getMMKV(mmkvId)?.encode(key, value) ?: false
        } catch (e: Exception) {
             loge(TAG, "MMKVUtil 设置Boolean值失败: key=$key, value=$value, error=${e.message}")
            false
        }
    }
    
    /**
     * 检查键是否存在
     * @param mmkvId MMKV实例ID
     * @param key 键名
     * @return 是否存在
     */
    fun containsKey(mmkvId: String, key: String): Boolean {
        return try {
            getMMKV(mmkvId)?.containsKey(key) ?: false
        } catch (e: Exception) {
             loge(TAG, "MMKVUtil 检查键是否存在失败: key=$key, error=${e.message}")
            false
        }
    }
    
    /**
     * 删除指定键
     * @param mmkvId MMKV实例ID
     * @param key 键名
     * @return 是否删除成功
     */
    fun removeKey(mmkvId: String, key: String): Boolean {
        return try {
            val result = getMMKV(mmkvId)?.removeValueForKey(key)
            result != null
        } catch (e: Exception) {
             loge(TAG, "MMKVUtil 删除键失败: key=$key, error=${e.message}")
            false
        }
    }
    
    /**
     * 清除指定实例的所有数据
     * @param mmkvId MMKV实例ID
     * @return 是否清除成功
     */
    fun clearAll(mmkvId: String): Boolean {
        return try {
            val result = getMMKV(mmkvId)?.clearAll()
            result != null
        } catch (e: Exception) {
             loge(TAG, "MMKVUtil 清除所有数据失败: mmkvId=$mmkvId, error=${e.message}")
            false
        }
    }
    
    /**
     * 检查MMKV是否已初始化
     * @return 是否已初始化
     */
    fun isInitialized(): Boolean {
        return isInitialized
    }
    
    /**
     * 获取所有MMKV实例ID
     * @return 实例ID列表
     */
    fun getAllInstanceIds(): List<String> {
        return mmkvInstances.keys.toList()
    }
    
    /**
     * 移除指定实例（释放内存）
     * @param mmkvId MMKV实例ID
     */
    fun removeInstance(mmkvId: String) {
        try {
            mmkvInstances.remove(mmkvId)
             logd(TAG, "MMKVUtil 移除实例: $mmkvId")
        } catch (e: Exception) {
            loge(TAG, "MMKVUtil 移除实例失败: mmkvId=$mmkvId, error=${e.message}")
        }
    }
    
    /**
     * 获取实例信息摘要（用于调试）
     * @return 实例信息
     */
    fun getInstanceInfo(): String {
        return try {
            val instanceCount = mmkvInstances.size
            val instanceIds = mmkvInstances.keys.joinToString(", ")
            """
            === MMKVUtil 实例信息 ===
            全局初始化状态: $isInitialized
            实例数量: $instanceCount
            实例ID列表: $instanceIds
            """.trimIndent()
        } catch (e: Exception) {
            "获取实例信息失败: ${e.message}"
        }
    }
}