package com.vincent.android.ledcontroller.utils

import android.content.Context
import android.util.Log
import com.vincent.library.base.util.logd

/**
 * 资源扫描工具类
 * 用于动态扫描assets文件夹中的图像文件
 */
object ResourceScanner {
    
    // 常量定义
    private const val TAG = "ResourceScanner"
    private const val DEFAULT_ASSETS_FOLDER = "gifs"
    private const val PATH_SEPARATOR = "/"
    
    // 支持的图像文件扩展名
    private val SUPPORTED_IMAGE_EXTENSIONS = setOf(
        "gif", "png", "jpg", "jpeg", "bmp", "webp", "svg", "ico"
    )
    
    // 排序方式枚举
    enum class SortType {
        NAME_ASC,    // 按名称升序
        NAME_DESC,   // 按名称降序
        SIZE_ASC,    // 按大小升序
        SIZE_DESC    // 按大小降序
    }
    
    /**
     * 扫描指定文件夹中的所有图像文件
     * @param context 上下文
     * @param folderPath assets文件夹路径，默认为"gifs"
     * @param sortType 排序方式，默认为按名称升序
     * @return 图像文件的路径列表
     */
    fun scanImageResources(
        context: Context,
        folderPath: String = DEFAULT_ASSETS_FOLDER,
        sortType: SortType = SortType.NAME_ASC
    ): List<String> {
        val imageFiles = mutableListOf<String>()
        
        try {
            val assetManager = context.assets
            val fileList = assetManager.list(folderPath)
            
            logd(TAG, "AssetManager扫描到 ${fileList?.size ?: 0} 个文件在文件夹: $folderPath")
            
            fileList?.forEach { fileName ->
                logd(TAG, "检查文件: $fileName")
                
                if (isImageFile(fileName)) {
                    val fullPath = "$folderPath$PATH_SEPARATOR$fileName"
                    imageFiles.add(fullPath)
                    logd(TAG, "找到图像文件: $fileName")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "AssetManager扫描失败，文件夹: $folderPath", e)
        }
        
        // 根据指定方式排序
        sortImageFiles(imageFiles, sortType)
        
        // 调试输出
        logd(TAG, "扫描到 ${imageFiles.size} 个图像文件:")
        imageFiles.forEach { filePath ->
            val fileName = filePath.substringAfterLast(PATH_SEPARATOR)
            logd(TAG, "  - $fileName ($filePath)")
        }
        
        return imageFiles
    }
    
    /**
     * 扫描GIF文件（向后兼容）
     * @param context 上下文
     * @return GIF文件的路径列表
     */
    fun scanGifResources(context: Context): List<String> {
        return scanImageResources(context, DEFAULT_ASSETS_FOLDER, SortType.NAME_ASC)
            .filter { it.endsWith(".gif", ignoreCase = true) }
    }
    
    /**
     * 检查文件是否为支持的图像格式
     * @param fileName 文件名
     * @return 是否为图像文件
     */
    private fun isImageFile(fileName: String): Boolean {
        val extension = fileName.substringAfterLast(".", "").lowercase()
        return SUPPORTED_IMAGE_EXTENSIONS.contains(extension)
    }
    
    /**
     * 对图像文件列表进行排序
     * @param imageFiles 图像文件列表
     * @param sortType 排序方式
     */
    private fun sortImageFiles(imageFiles: MutableList<String>, sortType: SortType) {
        when (sortType) {
            SortType.NAME_ASC -> {
                imageFiles.sortWith { a, b ->
                    val nameA = a.substringAfterLast(PATH_SEPARATOR)
                    val nameB = b.substringAfterLast(PATH_SEPARATOR)
                    nameA.compareTo(nameB, ignoreCase = true)
                }
            }
            SortType.NAME_DESC -> {
                imageFiles.sortWith { a, b ->
                    val nameA = a.substringAfterLast(PATH_SEPARATOR)
                    val nameB = b.substringAfterLast(PATH_SEPARATOR)
                    nameB.compareTo(nameA, ignoreCase = true)
                }
            }
            SortType.SIZE_ASC -> {
                //这里按名称排序作为fallback
                Log.w(TAG, "按大小排序在AssetManager中不可用，使用名称排序")
                imageFiles.sortWith { a, b ->
                    val nameA = a.substringAfterLast(PATH_SEPARATOR)
                    val nameB = b.substringAfterLast(PATH_SEPARATOR)
                    nameA.compareTo(nameB, ignoreCase = true)
                }
            }
            SortType.SIZE_DESC -> {
                Log.w(TAG, "按大小排序在AssetManager中不可用，使用名称排序")
                imageFiles.sortWith { a, b ->
                    val nameA = a.substringAfterLast(PATH_SEPARATOR)
                    val nameB = b.substringAfterLast(PATH_SEPARATOR)
                    nameB.compareTo(nameA, ignoreCase = true)
                }
            }
        }
    }
    
    /**
     * 获取图像文件的显示名称（去掉扩展名）
     * @param imageFilePath 图像文件路径
     * @return 显示名称
     */
    fun getImageDisplayName(imageFilePath: String): String {
        val fileName = imageFilePath.substringAfterLast(PATH_SEPARATOR)
        val lastDotIndex = fileName.lastIndexOf(".")
        return if (lastDotIndex > 0) {
            fileName.substring(0, lastDotIndex)
        } else {
            fileName
        }
    }
    
    /**
     * 获取GIF文件的显示名称（向后兼容）
     * @param gifFilePath GIF文件路径
     * @return 显示名称
     */
    fun getGifDisplayName(gifFilePath: String): String {
        return getImageDisplayName(gifFilePath)
    }
    
    /**
     * 获取文件扩展名
     * @param filePath 文件路径
     * @return 文件扩展名（小写）
     */
    fun getFileExtension(filePath: String): String {
        val fileName = filePath.substringAfterLast(PATH_SEPARATOR)
        return fileName.substringAfterLast(".", "").lowercase()
    }
    
    /**
     * 检查文件是否为GIF格式
     * @param filePath 文件路径
     * @return 是否为GIF文件
     */
    fun isGifFile(filePath: String): Boolean {
        return getFileExtension(filePath) == "gif"
    }
}