package com.vincent.android.ledcontroller.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.vincent.android.ledcontroller.R
import com.vincent.android.ledcontroller.logic.LEDController
import com.vincent.android.ledcontroller.constants.ImageConstants
import java.io.InputStream

/**
 * 图片处理器
 * 负责处理图片和GIF文件的加载、转换和发送
 */
class ImageProcessor(
    private val context: Context,
    private val isConnected: () -> Boolean,
    private val showToast: (String) -> Unit,
    private val showProgressDialog: () -> Unit,
    private val updateProgressDialog: (Int) -> Unit,
    private val hideProgressDialog: () -> Unit
) {
    companion object {
        private const val TAG = "ImageProcessor"
    }

    private val ledController: LEDController = LEDController.getInstance()

    fun processSelectedImage(uri: Uri) {
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap != null) {
                // 检查文件类型
                val mimeType = context.contentResolver.getType(uri)
                if (mimeType == context.getString(R.string.gif_mime_type)) {
                    // 处理GIF文件
                    processGifFile(uri)
                } else {
                    // 处理普通图片
                    processNormalImage(bitmap)
                }
            } else {
                showToast(context.getString(R.string.image_read_failed))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            showToast(context.getString(R.string.image_process_error, e.message ?: ""))
        }
    }

    private fun processGifFile(uri: Uri) {
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val gifBytes = inputStream?.readBytes()
            inputStream?.close()

            if (gifBytes != null && gifBytes.isNotEmpty()) {
                // 显示进度对话框
                showProgressDialog()

                // 发送GIF数据
                ledController.drawGifBytes(
                    gifBytes,
                    callback = { success, message ->
                        hideProgressDialog()
                        if (success) {
                            showToast(message ?: "GIF发送成功")
                        } else {
                            showToast(message ?: "GIF发送失败")
                        }
                    },
                    progressCallback = { progress ->
                        updateProgressDialog(progress)
                    }
                )
            } else {
                showToast(context.getString(R.string.gif_read_failed))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            showToast(context.getString(R.string.gif_process_error, e.message ?: ""))
        }
    }

    private fun processNormalImage(bitmap: Bitmap) {
        try {
            // 调整图片大小到LED矩阵尺寸
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap,  ImageConstants.LED_MATRIX_SIZE,  ImageConstants.LED_MATRIX_SIZE, true)

            // 转换为字节数组
            val byteArray = bitmapToByteArray(scaledBitmap)

            // 显示进度对话框
            showProgressDialog()

            // 发送图片数据
            ledController.drawImageBytes(
                byteArray,
                callback = { success, message ->
                    hideProgressDialog()
                    if (success) {
                        showToast(message ?: context.getString(R.string.image_send_success))
                    } else {
                        showToast(message ?: context.getString(R.string.image_send_failed))
                    }
                },
                progressCallback = { progress ->
                    updateProgressDialog(progress)
                }
            )
        } catch (e: Exception) {
            e.printStackTrace()
            showToast(context.getString(R.string.image_process_error, e.message ?: ""))
        }
    }

    private fun bitmapToByteArray(bitmap: Bitmap): ByteArray {
        // 将Bitmap转换为RGB565格式的字节数组
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val rgb565Data = ByteArray(width * height *  ImageConstants.RGB565_BYTES_PER_PIXEL)
        var index = 0

        for (pixel in pixels) {
            // 提取RGB分量
            val r = (pixel shr  ImageConstants.RGB_RED_SHIFT) and 0xFF
            val g = (pixel shr  ImageConstants.RGB_GREEN_SHIFT) and 0xFF
            val b = (pixel shr  ImageConstants.RGB_BLUE_SHIFT) and 0xFF

            // 转换为RGB565格式
            val rgb565 = ((r and  ImageConstants.RGB565_RED_MASK) shl  ImageConstants.RGB565_RED_SHIFT_BITS) or
                        ((g and  ImageConstants.RGB565_GREEN_MASK) shl  ImageConstants.RGB565_GREEN_SHIFT_BITS) or
                        (b shr  ImageConstants.RGB565_BLUE_SHIFT_BITS)

            // 存储为字节数组（大端序）
            rgb565Data[index++] = (rgb565 shr 8).toByte()
            rgb565Data[index++] = rgb565.toByte()
        }

        return rgb565Data
    }
}