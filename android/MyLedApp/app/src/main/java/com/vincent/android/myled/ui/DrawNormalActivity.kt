package com.vincent.android.myled.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.widget.Button
import android.widget.ImageView
import android.widget.Switch
import com.vincent.android.myled.R
import com.vincent.android.myled.led.LEDController
import com.vincent.android.myled.utils.logd
import com.vincent.android.myled.view.PixelView
import com.vincent.android.myled.view.PixelViewListener

/**
 * 单色图
 */
class DrawNormalActivity : VTBaseActivity(), PixelViewListener {

    private val MESSAGE_CODE = 1
    private var isRealTime = false
    private lateinit var pixelView: PixelView

    private lateinit var ivDrag: ImageView
    private lateinit var ivFill: ImageView
    private val ledController: LEDController = LEDController.getInstance()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_draw_normal)
        initViews()
        ledController.fillScreen(true)
    }

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            ledController.draw1Pixel(msg.what, msg.arg1, msg.arg2)
        }
    }

    private fun initViews() {
        initTitle(R.string.draw_a_picture)
        pixelView = findViewById(R.id.pv)
        pixelView.setListener(this)
        //是否是实时更新
        findViewById<Switch>(R.id.sw).run {
            isChecked = isRealTime
            setOnCheckedChangeListener { _, isChecked ->
                isRealTime = isChecked
            }
        }
        findViewById<Button>(R.id.btn_update).setOnClickListener {
            ledDraw()
        }
        ivDrag = findViewById<ImageView>(R.id.btn_drag).apply {
            setOnClickListener {
                setMode(true)
            }
        }
        ivFill = findViewById<ImageView>(R.id.btn_fill).apply {
            setOnClickListener {
                setMode(false)

            }
        }

        findViewById<ImageView>(R.id.btn_all_black).setOnClickListener {
            pixelView.fillAllBlack()
            if (isRealTime) {
                ledDraw()
            }
        }
        findViewById<ImageView>(R.id.btn_all_white).setOnClickListener {
            pixelView.fillAllWhite()
            if (isRealTime) {
                ledDraw()
            }
        }
        setMode(pixelView.isDragMode)
    }

    private fun setMode(isDrawMode: Boolean) {
        pixelView.setDragMode(isDrawMode)
        if (isDrawMode) {
            ivDrag.setImageResource(R.mipmap.drag_enable)
            ivFill.setImageResource(R.mipmap.fill_disable)
        } else {
            ivDrag.setImageResource(R.mipmap.drag_disable)
            ivFill.setImageResource(R.mipmap.fill_enable)
        }

    }

    /**
     * 画到LED屏幕上去
     */
    private fun ledDraw() {
        if (pixelView.isAllBlack) {
            ledController.fillScreen(true)
        } else if (pixelView.isAllWhite) {
            ledController.fillScreen(false)
        } else {
            ledController.drawNormalCanvas(pixelView.c51ModData)
        }
    }

    override fun pixelOnTouch(x: Int, y: Int, color: Int) {
        if (isRealTime) {
            logd("actionUpCallback:$x,$y,$color ")
            val msg = handler.obtainMessage(x, y, color)
            handler.sendMessageDelayed(msg, 1000);
        }
    }
}

