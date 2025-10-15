package com.vincent.library.base.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

/**
 * 协程工具类
 *
 */
object VTCoroutineUtil {

    // 协程作用域管理
    private val coroutineScopes = ConcurrentHashMap<String, CoroutineScope>()

    /**
     * 获取或创建协程作用域
     */
    fun getScope(scopeName: String): CoroutineScope {
        return coroutineScopes.getOrPut(scopeName) {
            CoroutineScope(Dispatchers.Main + SupervisorJob())
        }
    }

    /**
     * 延时执行
     * @param delayMillis 延时毫秒数
     * @param block 要执行的代码块
     */
    fun delay(delayMillis: Long, block: suspend () -> Unit): Job {
        return CoroutineScope(Dispatchers.Main).launch {
            kotlinx.coroutines.delay(delayMillis)
            block()
        }
    }

    /**
     * 在指定作用域中延时执行
     * @param scopeName 作用域名称
     * @param delayMillis 延时毫秒数
     * @param block 要执行的代码块
     */
    fun delayInScope(scopeName: String, delayMillis: Long, block: suspend () -> Unit): Job {
        return getScope(scopeName).launch {
            kotlinx.coroutines.delay(delayMillis)
            block()
        }
    }

    /**
     * 定时器
     * @param intervalMillis 间隔毫秒数
     * @param block 要执行的代码块
     */
    fun timer(intervalMillis: Long, block: suspend () -> Unit): Job {
        return CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                block()
                kotlinx.coroutines.delay(intervalMillis)
            }
        }
    }

    /**
     * 在指定作用域中定时器
     * @param scopeName 作用域名称
     * @param intervalMillis 间隔毫秒数
     * @param block 要执行的代码块
     */
    fun timerInScope(scopeName: String, intervalMillis: Long, block: suspend () -> Unit): Job {
        return getScope(scopeName).launch {
            while (isActive) {
                block()
                kotlinx.coroutines.delay(intervalMillis)
            }
        }
    }

    /**
     * 带超时的定时器
     * @param intervalMillis 间隔毫秒数
     * @param timeoutMillis 超时毫秒数
     * @param block 要执行的代码块
     */
    fun timerWithTimeout(
        intervalMillis: Long,
        timeoutMillis: Long,
        block: suspend () -> Unit
    ): Job {
        return CoroutineScope(Dispatchers.Main).launch {
            val startTime = System.currentTimeMillis()
            while (isActive && (System.currentTimeMillis() - startTime) < timeoutMillis) {
                block()
                kotlinx.coroutines.delay(intervalMillis)
            }
        }
    }

    /**
     * 在指定作用域中带超时的定时器
     * @param scopeName 作用域名称
     * @param intervalMillis 间隔毫秒数
     * @param timeoutMillis 超时毫秒数
     * @param block 要执行的代码块
     */
    fun timerWithTimeoutInScope(
        scopeName: String,
        intervalMillis: Long,
        timeoutMillis: Long,
        block: suspend () -> Unit
    ): Job {
        return getScope(scopeName).launch {
            val startTime = System.currentTimeMillis()
            while (isActive && (System.currentTimeMillis() - startTime) < timeoutMillis) {
                block()
                kotlinx.coroutines.delay(intervalMillis)
            }
        }
    }

    /**
     * 取消指定作用域的所有协程
     * @param scopeName 作用域名称
     */
    fun cancelScope(scopeName: String) {
        coroutineScopes[scopeName]?.cancel()
        coroutineScopes.remove(scopeName)
    }

    /**
     * 取消所有协程作用域
     */
    fun cancelAllScopes() {
        coroutineScopes.values.forEach { it.cancel() }
        coroutineScopes.clear()
    }

    /**
     * 在IO线程执行
     * @param block 要执行的代码块
     */
    suspend fun <T> withIO(block: suspend () -> T): T {
        return withContext(Dispatchers.IO) {
            block()
        }
    }

    /**
     * 在主线程执行
     * @param block 要执行的代码块
     */
    suspend fun <T> withMain(block: suspend () -> T): T {
        return withContext(Dispatchers.Main) {
            block()
        }
    }

    /**
     * 创建Flow定时器
     * @param intervalMillis 间隔毫秒数
     * @param count 执行次数，-1表示无限循环
     */
    fun createTimerFlow(intervalMillis: Long, count: Int = -1): Flow<Int> = flow {
        var currentCount = 0
        while (count == -1 || currentCount < count) {
            emit(currentCount)
            kotlinx.coroutines.delay(intervalMillis)
            currentCount++
        }
    }.flowOn(Dispatchers.Main)

    /**
     * 带取消功能的延时执行
     * @param delayMillis 延时毫秒数
     * @param block 要执行的代码块
     * @return 可取消的Job
     */
    fun cancellableDelay(delayMillis: Long, block: suspend () -> Unit): Job {
        return CoroutineScope(Dispatchers.Main).launch {
            try {
                kotlinx.coroutines.delay(delayMillis)
                block()
            } catch (e: CancellationException) {
                // 协程被取消，不执行block
            }
        }
    }
}