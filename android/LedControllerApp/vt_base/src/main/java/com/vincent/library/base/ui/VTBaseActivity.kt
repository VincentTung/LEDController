package com.vincent.library.base.ui

import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.loading.dialog.IOSLoadingDialog
import com.vincent.library.base.R
import com.vincent.library.base.util.VTToastUtil
import com.vincent.library.base.util.logd
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope

/**
 * UI基类
 */
open class VTBaseActivity : FragmentActivity() {
    
    companion object {
        private const val TAG = "VTBaseActivity"
        private const val DEFAULT_LOADING_TEXT = "加载中..."
        private const val GIF_SENDING_TEXT = "发送GIF中... %d%%"
    }
    private var iosLoadingDialog: IOSLoadingDialog? = null
        set(value) {
            // 安全地关闭旧的对话框
            field?.takeIf { !isFinishing && !isDestroyed }?.dismiss()
            field = value
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupStatusBar()
    }

    fun initTitle(stringId: Int, bgColor: Int = android.R.color.white, isBackVisible: Boolean = true) {
        findViewById<View>(R.id.fl_title)?.setBackgroundColor(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                getColor(bgColor)
            } else {
                @Suppress("DEPRECATION")
                resources.getColor(bgColor)
            }
        )
        
        // 设置标题文本
        findViewById<TextView>(R.id.tv_title)?.text = getString(stringId)
        
        // 设置返回按钮
        findViewById<ImageView>(R.id.iv_back)?.apply {
            visibility = if (isBackVisible) View.VISIBLE else View.GONE
            if (isBackVisible) {
                setOnClickListener { finish() }
            }
        }
    }


    /**
     * 开始显示Loading对话框
     */
    fun startLoading(text: String = DEFAULT_LOADING_TEXT) {
        logd(TAG, "startLoading: $text")
        stopLoading()
        
        if (isFinishing || isDestroyed) return
        
        // 检查 fragmentManager 是否可用
        val fm = fragmentManager
        if (fm == null) {
            logd(TAG, "fragmentManager is null, cannot show loading dialog")
            return
        }
        
        iosLoadingDialog = IOSLoadingDialog()
            .setHintMsg(text)
            .setOnTouchOutside(false)
            .apply {
                show(fm, text)
            }
    }

    /**
     * 更新Loading进度
     */
    fun updateLoadingProgress(progress: Int) {
        val progressText = GIF_SENDING_TEXT.format(progress)
        logd(TAG, "updateLoadingProgress: $progressText")
        iosLoadingDialog?.setHintMsg(progressText)
    }

    /**
     * 停止Loading对话框
     */
    fun stopLoading() {
        logd(TAG, "stopLoading")
        iosLoadingDialog = null
    }
    
    /**
     * 安全地关闭对话框
     */
    private fun safeDismissDialog() {
        try {
            val fm = fragmentManager
            if (!isFinishing && !isDestroyed && fm != null && !fm.isStateSaved) {
                iosLoadingDialog?.dismiss()
            }
        } catch (e: Exception) {
            logd(TAG, "safeDismissDialog异常: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        safeDismissDialog()
        iosLoadingDialog = null
        VTToastUtil.clear()
    }

    /**
     * 设置状态栏
     */
    private fun setupStatusBar() {
        val statusBarColor = getStatusBarColor()
        
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                // Android 6.0及以上版本
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                window.statusBarColor = getColor(statusBarColor)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP -> {
                // Android 5.0-5.1版本
                window.statusBarColor = getColor(statusBarColor)
            }
        }
    }

    /**
     * 获取状态栏颜色
     */
    open fun getStatusBarColor(): Int = android.R.color.white
    
    /**
     * 获取协程作用域
     */
    fun getActivityScope(): CoroutineScope = lifecycleScope
}