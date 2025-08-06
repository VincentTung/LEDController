package com.vincent.android.myled.ui

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.loading.dialog.IOSLoadingDialog
import com.vincent.android.myled.R
import com.vincent.android.myled.utils.logd


/**
 * UI基类
 */
open class VTBaseActivity : FragmentActivity() {
    var iosLoadingDialog: IOSLoadingDialog? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    /**
     * 初始化标题内容和返回键
     */
    fun initTitle(stringId: Int, isBackVisible: Boolean = true) {
        findViewById<TextView>(R.id.tv_title).text = getString(stringId)
        findViewById<ImageView>(R.id.iv_back).run {

            if (isBackVisible) {
                visibility = View.VISIBLE
                setOnClickListener {
                    finish()
                }
            } else {
                visibility = View.GONE
            }
        }
    }

    fun startLoading(text: String) {
        logd("startLoading: ")
        stopLoading()
        iosLoadingDialog = IOSLoadingDialog().setHintMsg(text).setOnTouchOutside(false)
        iosLoadingDialog!!.show(fragmentManager, text)
    }

    fun stopLoading() {
        logd("stopLoading: ")
        try {
            // 检查Activity状态，避免在Activity销毁时操作Fragment
            if (!isFinishing && !isDestroyed && !supportFragmentManager.isStateSaved) {
                iosLoadingDialog?.dismiss()
            }
        } catch (e: Exception) {
            logd("stopLoading异常: ${e.message}")
        } finally {
            iosLoadingDialog = null
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 确保在Activity销毁时清理对话框
        try {
            if (!supportFragmentManager.isStateSaved) {
                iosLoadingDialog?.dismiss()
            }
        } catch (e: Exception) {
            logd("onDestroy中stopLoading异常: ${e.message}")
        } finally {
            iosLoadingDialog = null
        }
    }
}