package com.vincent.android.ledcontroller.logic

import android.app.AlertDialog
import android.content.Context
import com.vincent.android.ledcontroller.R
import com.vincent.android.ledcontroller.logic.LEDController
import com.vincent.library.ble.config.PHY_1M
import com.vincent.library.ble.config.PHY_2M
import com.vincent.library.ble.config.PHY_CODED
import com.vincent.library.base.util.logd

/**
 * 对话框管理器
 * 负责管理各种对话框的显示和交互
 */
class DialogManager(
    private val context: Context,
    private val isConnected: () -> Boolean,
    private val showToast: (String) -> Unit,
    private val onClearDevice: (() -> Unit)? = null
) {
    companion object {
        private const val TAG = "DialogManager"
    }

    private val ledController: LEDController by lazy { LEDController.getInstance() }
    private var progressDialog: AlertDialog? = null

    /**
     * 显示PHY信息对话框
     */
    fun showPhyInfoDialog() {
        logd(TAG, "=== 显示PHY信息 ===")

        val phyInfo = buildPhyInfoContent()
        
        createPhyInfoDialog(phyInfo).show()
    }
    
    /**
     * 构建PHY信息内容
     */
    private fun buildPhyInfoContent(): String {
        val supports2M = ledController.isLe2MPhySupported()
        val supportsCoded = ledController.isLeCodedPhySupported()
        val supportedPhys = ledController.getSupportedPhys()
        
        val supportStatus = if (isConnected()) {
            buildConnectionStatusInfo()
        } else {
            context.getString(R.string.device_not_connected_phy)
        }
        
        return context.getString(
            R.string.phy_info_content,
            if (supports2M) context.getString(R.string.phy_supported) else context.getString(R.string.phy_not_supported),
            if (supportsCoded) context.getString(R.string.phy_supported) else context.getString(R.string.phy_not_supported),
            supportedPhys,
            supportStatus
        )
    }
    
    /**
     * 构建连接状态信息
     */
    private fun buildConnectionStatusInfo(): String {
        val currentTxPhy = ledController.getCurrentTxPhy()
        val currentRxPhy = ledController.getCurrentRxPhy()
        val phyNegotiationSuccess = ledController.isPhyNegotiationSuccessful()
        val qualityInfo = ledController.getConnectionQualityInfo()
        
        return buildString {
            appendLine("当前连接状态:")
            appendLine("- TX PHY: ${getPhyDescription(currentTxPhy)}")
            appendLine("- RX PHY: ${getPhyDescription(currentRxPhy)}")
            appendLine("- PHY协商: ${if (phyNegotiationSuccess) context.getString(R.string.phy_negotiation_success) else context.getString(R.string.phy_negotiation_status_failed)}")
            appendLine()
            appendLine("连接质量:")
            append(qualityInfo)
        }
    }
    
    /**
     * 创建PHY信息对话框
     */
    private fun createPhyInfoDialog(message: String): AlertDialog {
        return AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.phy_info_title))
            .setMessage(message)
            .setPositiveButton(context.getString(R.string.ok), null)
            .setNegativeButton(context.getString(R.string.test_phy)) { _, _ ->
                handlePhyTestAction()
            }
            .create()
    }
    
    /**
     *
     */
    private fun handlePhyTestAction() {
        if (isConnected()) {
            ledController.readCurrentPhy()
            showToast(context.getString(R.string.reading_phy_info))
        } else {
            showToast(context.getString(R.string.device_not_connected_short))
        }
    }

    /**
     * 显示配对信息对话框
     */
    fun showBondingDialog() {
        val bondingInfo = buildBondingInfoContent()
        createBondingDialog(bondingInfo).show()
    }
    
    /**
     * 配对信息内容
     */
    private fun buildBondingInfoContent(): String {
        val deviceInfo = ledController.getDeviceInfoSummary()
        val statusInfo = if (isConnected()) {
            val bondingStatus = ledController.getBondingStatusInfo()
            val qualityInfo = ledController.getConnectionQualityInfo()
            "$bondingStatus\n\n$qualityInfo"
        } else {
            context.getString(R.string.device_not_connected_bonding)
        }
        
        return context.getString(R.string.bonding_info_content, deviceInfo, statusInfo)
    }
    
    /**
     * 配对信息对话框
     */
    private fun createBondingDialog(message: String): AlertDialog {
        return AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.bonding_info_title))
            .setMessage(message)
            .setPositiveButton(context.getString(R.string.ok), null)
            .setNegativeButton(context.getString(R.string.request_bonding)) { _, _ ->
                handleBondingRequest()
            }
            .setNeutralButton(context.getString(R.string.clear_device)) { _, _ ->
                onClearDevice?.invoke()
            }
            .create()
    }
    
    /**
     * 处理配对请求
     */
    private fun handleBondingRequest() {
        if (isConnected()) {
            ledController.requestBonding()
            showToast(context.getString(R.string.requesting_bonding))
        } else {
            showToast(context.getString(R.string.device_not_connected_short))
        }
    }

    /**
     * 显示进度对话框
     */
    fun showProgressDialog() {
        progressDialog = createProgressDialog().apply { show() }
    }

    /**
     * 创建进度对话框
     */
    private fun createProgressDialog(): AlertDialog {
        return AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.progress_dialog_title))
            .setMessage(context.getString(R.string.progress_dialog_message, 0))
            .setCancelable(false)
            .setNegativeButton(context.getString(R.string.stop_sending)) { _, _ ->
                handleStopSending()
            }
            .create()
    }
    
    /**
     * 处理停止发送
     */
    private fun handleStopSending() {
        ledController.stopSendImageBytes()
        hideProgressDialog()
        showToast(context.getString(R.string.sending_stopped))
    }

    /**
     * 更新进度对话框
     */
    fun updateProgressDialog(progress: Int) {
        progressDialog?.setMessage(context.getString(R.string.progress_dialog_message, progress))
    }

    /**
     * 隐藏进度对话框
     */
    fun hideProgressDialog() {
        progressDialog?.takeIf { it.isShowing }?.dismiss()
        progressDialog = null
    }

    /**
     * 显示设置对话框
     */
    fun showSettingsDialog() {
        val options = arrayOf(
            context.getString(R.string.settings_clear_device),
            context.getString(R.string.settings_phy_info),
            context.getString(R.string.settings_about)
        )
        
        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.settings_title))
            .setItems(options) { _, which ->
                handleSettingsSelection(which)
            }
            .setNegativeButton(context.getString(R.string.cancel), null)
            .show()
    }
    
    /**
     * 处理设置选择
     */
    private fun handleSettingsSelection(which: Int) {
        when (which) {
            0 -> {
                onClearDevice?.invoke()
                showToast(context.getString(R.string.device_cleared))
            }
            1 -> showPhyInfoDialog()
            2 -> showAboutDialog()
        }
    }

    /**
     * 显示关于对话框
     */
    private fun showAboutDialog() {
        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.about_title))
            .setMessage(context.getString(R.string.about_content))
            .setPositiveButton(context.getString(R.string.ok), null)
            .show()
    }

    /**
     * 获取PHY描述信息
     */
    private fun getPhyDescription(phy: Int): String {
        return when (phy) {
            PHY_1M -> context.getString(R.string.phy_1m)
            PHY_2M -> context.getString(R.string.phy_2m)
            PHY_CODED -> context.getString(R.string.phy_coded)
            else -> context.getString(R.string.phy_unknown, phy)
        }
    }
}