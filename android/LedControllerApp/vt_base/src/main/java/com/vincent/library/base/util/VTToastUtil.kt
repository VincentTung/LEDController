package com.vincent.library.base.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/**
 * Toast工具类
 *
 */
object VTToastUtil {

    private var currentToast: Toast? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 显示Toast（字符串）
     * @param context 上下文
     * @param message 提示消息
     * @param duration 显示时长，默认短时间
     */
    fun show(context: Context?, message: String?, duration: Int = Toast.LENGTH_SHORT) {
        if (context == null || message.isNullOrEmpty()) {
            return
        }

        mainHandler.post {
            // 取消之前的Toast
            currentToast?.cancel()

            // 创建新的Toast
            currentToast = Toast.makeText(context.applicationContext, message, duration)
            currentToast?.show()
        }
    }

    /**
     * 显示Toast（资源ID）
     * @param context 上下文
     * @param stringResId 字符串资源ID
     * @param duration 显示时长，默认短时间
     */
    fun show(context: Context?, stringResId: Int, duration: Int = Toast.LENGTH_SHORT) {
        if (context == null) {
            return
        }

        mainHandler.post {
            currentToast?.cancel()

            currentToast = Toast.makeText(context.applicationContext, stringResId, duration)
            currentToast?.show()
        }
    }

    /**
     * 显示长时间Toast（字符串）
     * @param context 上下文
     * @param message 提示消息
     */
    fun showLong(context: Context?, message: String?) {
        show(context, message, Toast.LENGTH_LONG)
    }

    /**
     * 显示长时间Toast（资源ID）
     * @param context 上下文
     * @param stringResId 字符串资源ID
     */
    fun showLong(context: Context?, stringResId: Int) {
        show(context, stringResId, Toast.LENGTH_LONG)
    }

    /**
     * 取消当前显示的Toast
     */
    fun cancel() {
        mainHandler.post {
            currentToast?.cancel()
            currentToast = null
        }
    }

    /**
     * 清除所有Toast（用于Activity销毁时）
     */
    fun clear() {
        mainHandler.post {
            currentToast?.cancel()
            currentToast = null
        }
    }
}