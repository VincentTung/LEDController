#include "utils.h"
#include <cstring>
#include <cstdlib>
#include <esp_heap_caps.h>

// ============================================================================
// StringUtils 实现
// ============================================================================
int StringUtils::parseCommaSeparatedInts(const std::string& value, int* out, int maxCount) {
    if (value.empty() || !out || maxCount <= 0) {
        return 0;
    }
    
    char* token;
    int count = 0;
    char* valueCopy = strdup(value.c_str());
    
    if (!valueCopy) {
        return 0;
    }
    
    token = strtok(valueCopy, ",");
    while (token != NULL && count < maxCount) {
        out[count] = atoi(token);
        count++;
        token = strtok(NULL, ",");
    }
    
    free(valueCopy);
    return count;
}

std::vector<std::string> StringUtils::splitString(const std::string& value, char delimiter) {
    std::vector<std::string> result;
    if (value.empty()) {
        return result;
    }
    
    size_t start = 0;
    size_t end = value.find(delimiter);
    
    while (end != std::string::npos) {
        result.push_back(value.substr(start, end - start));
        start = end + 1;
        end = value.find(delimiter, start);
    }
    
    result.push_back(value.substr(start));
    return result;
}

bool StringUtils::safeStringCopy(char* dest, const char* src, size_t maxLen) {
    if (!dest || !src || maxLen == 0) {
        return false;
    }
    
    size_t srcLen = strlen(src);
    if (srcLen >= maxLen) {
        return false;
    }
    
    strcpy(dest, src);
    return true;
}

bool StringUtils::isEmpty(const std::string& str) {
    return str.empty() || str.find_first_not_of(" \t\n\r") == std::string::npos;
}

// ============================================================================
// MemoryUtils 实现
// ============================================================================
void* MemoryUtils::safeMalloc(size_t size) {
    if (size == 0) {
        return nullptr;
    }
    
    void* ptr = malloc(size);
    if (!ptr) {
        DEBUG_PRINTF("Memory allocation failed for size: %zu\n", size);
        ErrorHandler::handleMemoryError(size);
    }
    
    return ptr;
}

void MemoryUtils::safeFree(void* ptr) {
    if (ptr) {
        free(ptr);
    }
}

char* MemoryUtils::safeStringDup(const char* str) {
    if (!str) {
        return nullptr;
    }
    
    size_t len = strlen(str) + 1;
    char* newStr = (char*)safeMalloc(len);
    if (newStr) {
        strcpy(newStr, str);
    }
    
    return newStr;
}

size_t MemoryUtils::getFreeMemory() {
    return heap_caps_get_free_size(MALLOC_CAP_8BIT);
}

// ============================================================================
// ErrorHandler 实现
// ============================================================================
void ErrorHandler::handleInitError(int errorCode, const char* errorMsg) {
    DEBUG_PRINTF("Initialization error %d: %s\n", errorCode, errorMsg);
    // 可以在这里添加LED指示或其他错误处理逻辑
}

void ErrorHandler::handleMemoryError(size_t requestedSize) {
    DEBUG_PRINTF("Memory allocation failed for size: %zu bytes\n", requestedSize);
    DEBUG_PRINTF("Available memory: %zu bytes\n", MemoryUtils::getFreeMemory());
    // 可以在这里添加内存清理或重启逻辑
}

void ErrorHandler::handleFileError(const char* fileName, const char* operation) {
    DEBUG_PRINTF("File operation failed: %s on %s\n", operation, fileName);
    // 可以在这里添加文件系统检查或重试逻辑
}

// ============================================================================
// DebugUtils 实现
// ============================================================================
void DebugUtils::printMemoryInfo() {
    size_t freeMem = MemoryUtils::getFreeMemory();
    size_t totalMem = heap_caps_get_total_size(MALLOC_CAP_8BIT);
    size_t usedMem = totalMem - freeMem;
    
    DEBUG_PRINTF("Memory Info - Total: %zu, Used: %zu, Free: %zu bytes\n", 
                 totalMem, usedMem, freeMem);
}

void DebugUtils::printBLEStatus(bool isConnected) {
    DEBUG_PRINTF("BLE Status: %s\n", isConnected ? "Connected" : "Disconnected");
}

void DebugUtils::printDisplayState(DisplayState state) {
    const char* stateNames[] = {
        "IDLE",
        "SHOWING_TEXT", 
        "SCROLLING_TEXT",
        "SHOWING_GIF",
        "DRAWING"
    };
    
    if (state >= 0 && state < ARRAY_SIZE(stateNames)) {
        DEBUG_PRINTF("Display State: %s\n", stateNames[state]);
    } else {
        DEBUG_PRINTF("Display State: UNKNOWN (%d)\n", state);
    }
}

void DebugUtils::printPerformance(const char* operation, unsigned long startTime) {
    unsigned long duration = millis() - startTime;
    DEBUG_PRINTF("Performance - %s: %lu ms\n", operation, duration);
} 