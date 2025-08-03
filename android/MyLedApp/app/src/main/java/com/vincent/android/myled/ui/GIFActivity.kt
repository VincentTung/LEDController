package com.vincent.android.myled.ui

import android.os.Bundle
import android.widget.Button
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vincent.android.myled.R
import com.vincent.android.myled.adapter.GIFAdapter
import com.vincent.android.myled.led.LEDController


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
        findViewById<Button>(R.id.btn_update).setOnClickListener {
            val po = gifAdapter.getSelect()
            if (po >= 0) {
                ledController.drawGif("/gifs/${imagesName[po]}.gif")
            }
        }

        rclView = findViewById(R.id.rclView);
    }
}

