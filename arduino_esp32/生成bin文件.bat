@echo off
chcp 65001 >nul
echo ========================================
echo ESP32 Arduino bin文件生成工具
echo ========================================

:: 设置路径
set SCRIPT_DIR=%~dp0
set PYTHON_SCRIPT=%SCRIPT_DIR%merge_bin_final.py

echo 正在运行Python脚本...
python "%PYTHON_SCRIPT%"

if %errorlevel% equ 0 (
    echo.
    echo ========================================
    echo bin文件生成成功！
    echo ========================================
    echo 生成的文件位于 archives 目录中
    echo.
    echo 生成的文件:
    echo - myled_hub75e_时间戳.bin (完整烧录文件)
    echo.
    echo 只生成一个带时间戳的bin文件，其他文件不会复制到archives目录
    echo 您可以使用这个文件烧录到ESP32开发板
) else (
    echo.
    echo ========================================
    echo bin文件生成失败！
    echo ========================================
    echo 请检查错误信息并重试
)

echo.
pause
