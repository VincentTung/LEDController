package com.vincent.android.ledcontroller.ui

import android.app.AlertDialog
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.vincent.android.ledcontroller.R
import com.vincent.android.ledcontroller.adapter.GIFListAdapter
import com.vincent.android.ledcontroller.databinding.ActivityGifBinding
import com.vincent.android.ledcontroller.logic.LEDController
import com.vincent.android.ledcontroller.utils.ResourceScanner
import com.vincent.library.base.ui.VTBaseActivity
import com.vincent.library.base.util.VTToastUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException


/**
 *  App内置Gif列表
 */
class GIFListActivity : VTBaseActivity() {

    companion object {
        private const val GRID_COLUMNS = 3
        private const val INVALID_POSITION = -1
    }

    private lateinit var binding: ActivityGifBinding
    private var gifFiles: List<String> = emptyList()
    private lateinit var gifAdapter: GIFListAdapter
    private var progressDialog: AlertDialog? = null
    private val ledController: LEDController = LEDController.getInstance()
    private var sendJob: Job? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityGifBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initViews()
        loadGIF()
    }

    override fun onDestroy() {
        super.onDestroy()
        hideProgressDialog()
    }

    override fun getStatusBarColor(): Int {
        return R.color.yellow
    }

    private fun loadGIF() {
        gifFiles = ResourceScanner.scanGifResources(this)
        if (gifFiles.isEmpty()) {
            VTToastUtil.show(this, getString(R.string.gif_no_files_found))
            return
        }

        gifAdapter = GIFListAdapter(this, gifFiles) { selectedPosition ->
            // 当选择状态改变时更新按钮图标
            updateSendButtonState(selectedPosition)
        }
        binding.rclView.layoutManager = GridLayoutManager(this, GRID_COLUMNS)
        binding.rclView.adapter = gifAdapter
    }

    private fun initViews() {
        initTitle(R.string.gif, R.color.yellow)

        binding.btnSendBytes.setOnClickListener {
            val selectedPosition = gifAdapter.getSelect()
            if (isValidPosition(selectedPosition)) {
                if(ledController.isDeviceConnected()) {
                    sendGifFile(gifFiles[selectedPosition])
                }else{
                    VTToastUtil.show(this,getString(R.string.device_not_connected))
                }
            } else {
                VTToastUtil.show(this, getString(R.string.gif_please_select_first))
            }
        }

        updateSendButtonState(INVALID_POSITION)
    }


    /**
     * 读取GIF文件并发送字节数据
     */
    private fun sendGifFile(gifFilePath: String) {

        showProgressDialog()
        sendJob?.cancel()
        sendJob = lifecycleScope.launch {
            try {
                val gifBytes = withContext(Dispatchers.IO) {
                    assets.open(gifFilePath).use { inputStream ->
                        inputStream.readBytes()
                    }
                }


                if (!isFinishing && !isDestroyed) {

                    ledController.drawGifBytes(
                        gifBytes,
                        callback = { success, message ->

                            hideProgressDialog()
                            val toastMessage = message ?: if (success) {
                                getString(R.string.gif_send_success)
                            } else {
                                getString(R.string.gif_send_failed)
                            }
                            VTToastUtil.show(this@GIFListActivity, toastMessage)
                        },
                        progressCallback = { progress ->
                            if (!isFinishing && !isDestroyed) {
                                updateProgressDialog(progress)
                            }
                        }
                    )
                }
            } catch (e: IOException) {
                e.printStackTrace()
                hideProgressDialog()
                if (!isFinishing && !isDestroyed) {
                    VTToastUtil.show(
                        this@GIFListActivity,
                        "${getString(R.string.gif_file_read_failed)}: ${e.message}"
                    )
                }
            }
        }
    }

    private fun showProgressDialog() {
        if (progressDialog?.isShowing == true) return

        // 暂停GIF列表中的播放
        gifAdapter.pauseGifPlayback()

        progressDialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.gif_sending_title))
            .setMessage(getString(R.string.gif_sending_progress, 0))
            .setCancelable(false)
            .setNegativeButton(getString(R.string.gif_stop_sending)) { _, _ ->
                // 停止发送GIF
                ledController.stopSendGifBytes()
                hideProgressDialog()
                VTToastUtil.show(this, getString(R.string.gif_sending_stopped))
            }
            .create()
        progressDialog?.show()
    }

    private fun updateProgressDialog(progress: Int) {
        progressDialog?.setMessage(getString(R.string.gif_sending_progress, progress))
    }

    private fun hideProgressDialog() {
        progressDialog?.takeIf { it.isShowing }?.dismiss()
        progressDialog = null
        
        // 恢复GIF列表中的播放
        gifAdapter.resumeGifPlayback()
    }

    /**
     * 检查位置是否有效
     */
    private fun isValidPosition(position: Int): Boolean {
        return position >= 0 && position < gifFiles.size
    }

    /**
     * 更新发送按钮的状态和图标
     */
    private fun updateSendButtonState(selectedPosition: Int) {
        val isValid = isValidPosition(selectedPosition)
        binding.btnSendBytes.apply {
            setImageResource(if (isValid) R.drawable.ic_send_enable else R.drawable.ic_send_disabled)
            isEnabled = isValid
        }
    }
}

