package com.vincent.android.myled.ui

import android.os.Bundle
import android.widget.Button
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vincent.android.myled.R
import com.vincent.android.myled.adapter.GIFAdapter
import com.vincent.android.myled.led.LEDController
import com.vincent.android.myled.utils.ToastUtil
import java.io.IOException


/**
 * GIF
 */
class GIFActivity : VTBaseActivity() {
    val images = arrayOf<Int>(
        R.raw.a,
        R.raw.b,
        R.raw.c,
        R.raw.d,
        R.raw.e,
        R.raw.f,
        R.raw.g,
        R.raw.h,
        R.raw.i,
        R.raw.j,
        R.raw.k,
        R.raw.l,

        )

    val imagesName = arrayOf<String>(
        "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l",

        )
    private lateinit var rclView: RecyclerView
    private lateinit var gifAdapter: GIFAdapter
    private val ledController: LEDController = LEDController.getInstance()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_gif)
        initViews()
        loadGIF()
    }

    private fun loadGIF() {

        gifAdapter = GIFAdapter(this, images)
        rclView.setLayoutManager(GridLayoutManager(this, 3))
        rclView.setAdapter(gifAdapter)

    }

    private fun initViews() {
        initTitle(R.string.gif)

        findViewById<Button>(R.id.btn_send_bytes).setOnClickListener {
            val po = gifAdapter.getSelect()
            if (po >= 0) {
                sendGifBytes(images[po])
            }
        }

        rclView = findViewById(R.id.rclView);
    }
    

    
    /**
     * 读取GIF文件并发送字节数据
     */
    private fun sendGifBytes(gifResourceId: Int) {
        try {
            val inputStream = resources.openRawResource(gifResourceId)
            val gifBytes = inputStream.readBytes()
            inputStream.close()
            
            // 显示loading对话框
            startLoading(getString(R.string.sending_gif))
            
            // 发送GIF数据，带回调
            ledController.drawGifBytes(gifBytes) { success, message ->
                // 隐藏loading对话框
                stopLoading()
                
                // 根据发送结果显示提示
                if (success) {
                    ToastUtil.show(this, message ?: getString(R.string.gif_send_success))
                } else {
                    ToastUtil.show(this, message ?: getString(R.string.gif_send_failed))
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            stopLoading()
            ToastUtil.show(this, "${getString(R.string.gif_file_read_failed)}: ${e.message}")
        }
    }
}

