#ifndef UTILS_H
#define UTILS_H

#include "config.h"
#include <string>
#include <vector>

// ============================================================================
// 字符串处理工具类
// ============================================================================
class StringUtils {
public:
    /**
     * 解析逗号分隔的整数字符串
     * @param value 输入字符串
     * @param out 输出整数数组
     * @param maxCount 最大解析数量
     * @return 实际解析的数量
     */
    static int parseCommaSeparatedInts(const std::string& value, int* out, int maxCount);
    
    /**
     * 解析逗号分隔的字符串
     * @param value 输入字符串
     * @param delimiter 分隔符
     * @return 字符串数组
     */
    static std::vector<std::string> splitString(const std::string& value, char delimiter = ',');
    
    /**
     * 安全的字符串复制
     * @param dest 目标指针
     * @param src 源字符串
     * @param maxLen 最大长度
     * @return 是否成功
     */
    static bool safeStringCopy(char* dest, const char* src, size_t maxLen);
    
    /**
     * 检查字符串是否为空或只包含空白字符
     * @param str 输入字符串
     * @return 是否为空
     */
    static bool isEmpty(const std::string& str);
};

// ============================================================================
// 内存管理工具类
// ============================================================================
class MemoryUtils {
public:
    /**
     * 安全的内存分配
     * @param size 分配大小
     * @return 分配的内存指针，失败返回nullptr
     */
    static void* safeMalloc(size_t size);
    
    /**
     * 安全的内存释放
     * @param ptr 内存指针
     */
    static void safeFree(void* ptr);
    
    /**
     * 安全的字符串内存分配
     * @param str 源字符串
     * @return 新分配的字符串指针，失败返回nullptr
     */
    static char* safeStringDup(const char* str);
    
    /**
     * 获取可用内存大小
     * @return 可用内存字节数
     */
    static size_t getFreeMemory();
};

// ============================================================================
// 错误处理工具类
// ============================================================================
class ErrorHandler {
public:
    /**
     * 处理初始化错误
     * @param errorCode 错误码
     * @param errorMsg 错误信息
     */
    static void handleInitError(int errorCode, const char* errorMsg);
    
    /**
     * 处理内存分配错误
     * @param requestedSize 请求的大小
     */
    static void handleMemoryError(size_t requestedSize);
    
    /**
     * 处理文件操作错误
     * @param fileName 文件名
     * @param operation 操作类型
     */
    static void handleFileError(const char* fileName, const char* operation);
};

// ============================================================================
// 调试工具类
// ============================================================================
class DebugUtils {
public:
    /**
     * 打印内存使用情况
     */
    static void printMemoryInfo();
    
    /**
     * 打印BLE连接状态
     * @param isConnected 是否连接
     */
    static void printBLEStatus(bool isConnected);
    
    /**
     * 打印显示状态
     * @param state 显示状态
     */
    static void printDisplayState(DisplayState state);
    
    /**
     * 打印性能统计
     * @param operation 操作名称
     * @param startTime 开始时间
     */
    static void printPerformance(const char* operation, unsigned long startTime);
};

#endif // UTILS_H 