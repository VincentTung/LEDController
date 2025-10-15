package com.vincent.android.ledcontroller.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

class PixelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val GRID_SIZE = 64
    }

    private val paint = Paint().apply {
        style = Paint.Style.FILL_AND_STROKE
        strokeWidth = 1f
    }
    
    private val pixels = Array(GRID_SIZE) { BooleanArray(GRID_SIZE) }
    private var isDragMode = false
    private var scaleFactor = 1.0f
    private var translateX = 0f
    private var translateY = 0f
    
    private val scaleDetector: ScaleGestureDetector
    private val gestureDetector: GestureDetector
    
    private var pixelViewListener: PixelViewListener? = null

    init {
        scaleDetector = ScaleGestureDetector(context, ScaleListener())
        gestureDetector = GestureDetector(context, GestureListener())
    }

    fun setListener(pixelViewListener: PixelViewListener?) {
        this.pixelViewListener = pixelViewListener
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.save()
        canvas.scale(scaleFactor, scaleFactor)
        canvas.translate(translateX / scaleFactor, translateY / scaleFactor)

        val cellSize = width / GRID_SIZE

        for (i in 0 until GRID_SIZE) {
            for (j in 0 until GRID_SIZE) {
                paint.color = if (pixels[i][j]) Color.WHITE else Color.BLACK
                canvas.drawRect(
                    (i * cellSize).toFloat(),
                    (j * cellSize).toFloat(),
                    ((i + 1) * cellSize).toFloat(),
                    ((j + 1) * cellSize).toFloat(),
                    paint
                )
            }
        }

        paint.color = Color.WHITE
        for (i in 0..GRID_SIZE) {
            canvas.drawLine(
                (i * cellSize).toFloat(),
                0f,
                (i * cellSize).toFloat(),
                height.toFloat(),
                paint
            )
            canvas.drawLine(
                0f,
                (i * cellSize).toFloat(),
                width.toFloat(),
                (i * cellSize).toFloat(),
                paint
            )
        }

        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isDragMode) {
            scaleDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
        } else {
            val cellSize = width / GRID_SIZE
            val x = ((event.x - translateX) / (cellSize * scaleFactor)).toInt()
            val y = ((event.y - translateY) / (cellSize * scaleFactor)).toInt()

            if (x in 0 until GRID_SIZE && y in 0 until GRID_SIZE) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                        pixels[x][y] = !pixels[x][y]
                        invalidate()

                        pixelViewListener?.pixelOnTouch(x, y, if (pixels[x][y]) 1 else 0)
                    }
                    MotionEvent.ACTION_UP -> {
                        // 处理抬起事件
                    }
                }
            }
        }

        return true
    }


    var dragMode: Boolean
        get() = isDragMode
        set(value) { isDragMode = value }

    fun isDragMode(): Boolean = dragMode

    fun fillAllBlack() {
        for (i in 0 until GRID_SIZE) {
            for (j in 0 until GRID_SIZE) {
                pixels[i][j] = false
            }
        }
        invalidate()
    }

    val isAllBlack: Boolean
        get() {
            for (i in 0 until GRID_SIZE) {
                for (j in 0 until GRID_SIZE) {
                    if (pixels[i][j]) {
                        return false
                    }
                }
            }
            return true
        }
        
    val isAllWhite: Boolean
        get() {
            for (i in 0 until GRID_SIZE) {
                for (j in 0 until GRID_SIZE) {
                    if (!pixels[i][j]) {
                        return false
                    }
                }
            }
            return true
        }

    fun fillAllWhite() {
        for (i in 0 until GRID_SIZE) {
            for (j in 0 until GRID_SIZE) {
                pixels[i][j] = true
            }
        }
        invalidate()
    }

    fun resetView() {
        scaleFactor = 1.0f
        translateX = 0f
        translateY = 0f
        invalidate()
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            scaleFactor = scaleFactor.coerceIn(1.0f, 10.0f)
            invalidate()
            return true
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (isDragMode) {
                resetView()
            }
            return true
        }

        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            if (isDragMode) {
                translateX -= distanceX
                translateY -= distanceY
                invalidate()
            }
            return true
        }
    }

    val c51ModData: ByteArray
        get() {
            val modData = ByteArray((GRID_SIZE * GRID_SIZE) / 8)
            var index = 0
            for (y in 0 until GRID_SIZE) {
                for (x in 0 until GRID_SIZE step 8) {
                    var byteData: Byte = 0
                    for (bit in 0 until 8) {
                        if ((x + bit) < GRID_SIZE && pixels[x + bit][y]) {
                            byteData = (byteData.toInt() or (1 shl (7 - bit))).toByte()
                        }
                    }
                    modData[index++] = byteData
                }
            }
            return modData
        }
}
