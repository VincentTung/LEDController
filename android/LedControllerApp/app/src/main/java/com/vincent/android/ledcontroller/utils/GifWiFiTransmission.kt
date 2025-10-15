package com.vincent.android.ledcontroller.utils

import android.util.Log
import com.vincent.library.base.util.VTCoroutineUtil
import kotlinx.coroutines.*
import java.io.*
import java.net.Socket

/**
 * GIF WiFi传输管理器
 * 专门处理GIF文件通过WiFi的传输逻辑
 */
class GifWiFiTransmission private constructor() {
    companion object {
        private const val TAG = "GifWiFiTransmission"
        private const val SCOPE_NAME = "GifWiFiTransmission"
        private const val CHUNK_SIZE = 1024 // 1KB数据块
        private const val HEADER_PREFIX = "GIF:"
        private const val HEADER_SUFFIX = "\n"
        private const val CONFIRMATION_PREFIX = "OK"
        private const val TIMEOUT_MS = 30000L // 30秒超时
        
        @Volatile
        private var INSTANCE: GifWiFiTransmission? = null

        fun getInstance(): GifWiFiTransmission {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GifWiFiTransmission().also { INSTANCE = it }
            }
        }
    }

    /**
     * 发送GIF数据到ESP32
     * @param socket TCP Socket连接
     * @param gifData GIF文件字节数据
     * @param callback 发送结果回调
     * @param progressCallback 进度回调
     */
    fun sendGifToESP32(
        socket: Socket,
        gifData: ByteArray,
        callback: ((Boolean, String?) -> Unit)? = null,
        progressCallback: ((Int) -> Unit)? = null
    ) {
        VTCoroutineUtil.getScope(SCOPE_NAME).launch {
            try {
                Log.d(TAG, "开始发送GIF数据: ${gifData.size} 字节")
                
                val outputStream = socket.getOutputStream()
                val inputStream = socket.getInputStream()
                
                // 步骤1: 发送GIF数据头
                val header = "$HEADER_PREFIX${gifData.size}$HEADER_SUFFIX"
                outputStream.write(header.toByteArray(Charsets.UTF_8))
                outputStream.flush()
                
                Log.d(TAG, "发送GIF头信息: $header")
                
                // 步骤2: 等待ESP32确认
                val confirmation = waitForConfirmation(inputStream)
                if (!confirmation) {
                    callback?.invoke(false, "ESP32未确认GIF头信息")
                    return@launch
                }
                
                Log.d(TAG, "ESP32确认接收GIF头信息")
                
                // 步骤3: 分块发送GIF数据
                var sentBytes = 0
                val totalBytes = gifData.size
                val totalChunks = (totalBytes + CHUNK_SIZE - 1) / CHUNK_SIZE
                
                Log.d(TAG, "开始分块发送GIF数据: $totalChunks 个数据块")
                
                for (chunkIndex in 0 until totalChunks) {
                    val remainingBytes = totalBytes - sentBytes
                    val currentChunkSize = minOf(CHUNK_SIZE, remainingBytes)
                    
                    // 发送数据块
                    outputStream.write(gifData, sentBytes, currentChunkSize)
                    outputStream.flush()
                    
                    sentBytes += currentChunkSize
                    val progress = (sentBytes * 100 / totalBytes)
                    
                    Log.d(TAG, "发送数据块 $chunkIndex/$totalChunks: $currentChunkSize 字节, 进度: $progress%")
                    progressCallback?.invoke(progress)
                    
                    // 短暂延迟，避免发送过快
                    delay(10)
                }
                
                Log.d(TAG, "GIF数据发送完成: $sentBytes/$totalBytes 字节")
                callback?.invoke(true, "GIF发送成功")
                
            } catch (e: Exception) {
                Log.e(TAG, "发送GIF数据失败", e)
                callback?.invoke(false, "发送GIF数据失败: ${e.message}")
            }
        }
    }
    
    /**
     * 等待ESP32确认
     * @param inputStream 输入流
     * @return 是否收到确认
     */
    private suspend fun waitForConfirmation(inputStream: InputStream): Boolean = withContext(Dispatchers.IO) {
        try {
            val buffer = ByteArray(1024)
            val startTime = System.currentTimeMillis()
            
            while (System.currentTimeMillis() - startTime < TIMEOUT_MS) {
                if (inputStream.available() > 0) {
                    val bytesRead = inputStream.read(buffer)
                    val response = String(buffer, 0, bytesRead, Charsets.UTF_8).trim()
                    
                    Log.d(TAG, "收到ESP32响应: $response")
                    
                    if (response.startsWith(CONFIRMATION_PREFIX)) {
                        return@withContext true
                    }
                }
                delay(100) // 100ms检查一次
            }
            
            Log.w(TAG, "等待ESP32确认超时")
            return@withContext false
            
        } catch (e: Exception) {
            Log.e(TAG, "等待确认时出错", e)
            return@withContext false
        }
    }
    
    /**
     * 验证GIF数据
     * @param gifData GIF数据
     * @return 是否为有效的GIF数据
     */
    fun validateGifData(gifData: ByteArray): Boolean {
        if (gifData.size < 6) return false
        
        // 检查GIF文件头
        val gifHeader = byteArrayOf(0x47, 0x49, 0x46) // "GIF"
        for (i in 0..2) {
            if (gifData[i] != gifHeader[i]) {
                return false
            }
        }
        
        return true
    }
    
    /**
     * 计算传输时间估算
     * @param gifSize GIF文件大小（字节）
     * @param chunkSize 数据块大小（字节）
     * @param delayMs 每块之间的延迟（毫秒）
     * @return 估算的传输时间（毫秒）
     */
    fun estimateTransmissionTime(gifSize: Int, chunkSize: Int = CHUNK_SIZE, delayMs: Int = 10): Long {
        val totalChunks = (gifSize + chunkSize - 1) / chunkSize
        return (totalChunks * delayMs).toLong()
    }
    
    /**
     * 获取推荐的传输方式
     * @param gifSize GIF文件大小（字节）
     * @param wifiThreshold WiFi传输阈值（字节）
     * @return 推荐的传输方式
     */
    fun getRecommendedTransmissionMode(gifSize: Int, wifiThreshold: Int = 10 * 1024): TransmissionMode {
        return if (gifSize > wifiThreshold) {
            TransmissionMode.WIFI
        } else {
            TransmissionMode.BLUETOOTH
        }
    }
}

/**
 * 传输模式枚举
 */
enum class TransmissionMode {
    WIFI,       // WiFi传输
    BLUETOOTH   // 蓝牙传输
}