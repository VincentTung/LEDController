#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
ESP32 Arduino项目bin文件生成工具
支持从Arduino IDE编译输出中提取和生成bin文件
"""

import os
import sys
import shutil
import subprocess
import glob
from pathlib import Path

def find_arduino_build_path():
    """查找Arduino IDE编译输出路径"""
    possible_paths = [
        # Windows常见路径
        os.path.expanduser("~\\AppData\\Local\\Arduino15\\packages\\esp32\\hardware\\esp32\\*\\tools\\sdk\\esp32\\bin"),
        os.path.expanduser("~\\AppData\\Local\\Temp\\arduino_build_*"),
        # Linux常见路径
        os.path.expanduser("~/.arduino15/packages/esp32/hardware/esp32/*/tools/sdk/esp32/bin"),
        os.path.expanduser("~/.arduino15/packages/esp32/hardware/esp32/*/tools/sdk/esp32/bin"),
        # macOS常见路径
        os.path.expanduser("~/Library/Arduino15/packages/esp32/hardware/esp32/*/tools/sdk/esp32/bin"),
    ]
    
    for pattern in possible_paths:
        matches = glob.glob(pattern)
        for match in matches:
            if os.path.exists(match):
                return match
    
    return None

def find_latest_build():
    """查找最新的Arduino编译输出"""
    temp_dir = os.path.expanduser("~\\AppData\\Local\\Temp" if os.name == 'nt' else "/tmp")
    build_dirs = glob.glob(os.path.join(temp_dir, "arduino_build_*"))
    
    if not build_dirs:
        return None
    
    # 按修改时间排序，返回最新的
    latest_dir = max(build_dirs, key=os.path.getmtime)
    return latest_dir

def check_esptool():
    """检查esptool是否可用"""
    try:
        result = subprocess.run([sys.executable, "-m", "esptool", "--help"], 
                              capture_output=True, text=True)
        return result.returncode == 0
    except:
        return False

def generate_complete_bin(project_name, output_dir):
    """生成完整的烧录bin文件"""
    print("正在生成完整的烧录bin文件...")
    
    # 查找必要的bin文件
    bootloader_bin = None
    partitions_bin = None
    boot_app0_bin = None
    app_bin = None
    
    # 在输出目录中查找
    for file in os.listdir(output_dir):
        if file.endswith('.bin'):
            if 'bootloader' in file.lower():
                bootloader_bin = os.path.join(output_dir, file)
            elif 'partitions' in file.lower():
                partitions_bin = os.path.join(output_dir, file)
            elif 'boot_app0' in file.lower():
                boot_app0_bin = os.path.join(output_dir, file)
            elif project_name.lower() in file.lower():
                app_bin = os.path.join(output_dir, file)
    
    if not all([bootloader_bin, partitions_bin, boot_app0_bin, app_bin]):
        print("警告: 未找到所有必要的bin文件，尝试从Arduino核心目录查找...")
        
        # 从Arduino核心目录查找
        core_path = find_arduino_build_path()
        if core_path:
            for file in os.listdir(core_path):
                if file.endswith('.bin'):
                    if 'bootloader' in file.lower() and not bootloader_bin:
                        bootloader_bin = os.path.join(core_path, file)
                    elif 'partitions' in file.lower() and not partitions_bin:
                        partitions_bin = os.path.join(core_path, file)
                    elif 'boot_app0' in file.lower() and not boot_app0_bin:
                        boot_app0_bin = os.path.join(core_path, file)
    
    if not all([bootloader_bin, partitions_bin, boot_app0_bin, app_bin]):
        print("错误: 无法找到所有必要的bin文件")
        print("需要的文件:")
        print("- bootloader.bin")
        print("- partitions.bin") 
        print("- boot_app0.bin")
        print("- 应用程序.bin")
        return False
    
    # 生成完整的bin文件
    complete_bin = os.path.join(output_dir, f"{project_name}_complete.bin")
    
    try:
        cmd = [
            sys.executable, "-m", "esptool", 
            "--chip", "esp32",
            "merge_bin", 
            "-o", complete_bin,
            "--flash_mode", "dio",
            "--flash_freq", "80m", 
            "--flash_size", "4MB",
            "0x1000", bootloader_bin,
            "0x8000", partitions_bin,
            "0xe000", boot_app0_bin,
            "0x10000", app_bin
        ]
        
        result = subprocess.run(cmd, capture_output=True, text=True)
        
        if result.returncode == 0:
            print(f"成功生成完整bin文件: {complete_bin}")
            return True
        else:
            print(f"生成完整bin文件失败: {result.stderr}")
            return False
            
    except Exception as e:
        print(f"生成完整bin文件时出错: {e}")
        return False

def main():
    print("=" * 50)
    print("ESP32 Arduino项目bin文件生成工具")
    print("=" * 50)
    
    # 检查esptool
    if not check_esptool():
        print("错误: 未找到esptool，请先安装:")
        print("pip install esptool")
        return 1
    
    # 设置路径
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_dir = os.path.join(script_dir, "myled_hub75e")
    output_dir = os.path.join(script_dir, "bin_output")
    
    # 创建输出目录
    os.makedirs(output_dir, exist_ok=True)
    
    print(f"项目目录: {project_dir}")
    print(f"输出目录: {output_dir}")
    
    # 查找Arduino编译输出
    print("\n正在查找Arduino编译输出...")
    build_dir = find_latest_build()
    
    if not build_dir:
        print("未找到Arduino编译输出目录")
        print("请先在Arduino IDE中编译项目，然后重新运行此脚本")
        return 1
    
    print(f"找到编译输出目录: {build_dir}")
    
    # 复制bin文件到输出目录
    print("\n正在复制bin文件...")
    bin_files = glob.glob(os.path.join(build_dir, "*.bin"))
    
    if not bin_files:
        print("错误: 在编译输出目录中未找到bin文件")
        return 1
    
    copied_files = []
    for bin_file in bin_files:
        filename = os.path.basename(bin_file)
        dest_path = os.path.join(output_dir, filename)
        shutil.copy2(bin_file, dest_path)
        copied_files.append(filename)
        print(f"已复制: {filename}")
    
    # 生成完整的烧录bin文件
    print("\n正在生成完整的烧录bin文件...")
    if generate_complete_bin("myled_hub75e", output_dir):
        print("\n" + "=" * 50)
        print("bin文件生成完成！")
        print("=" * 50)
        print(f"输出目录: {output_dir}")
        print("\n生成的文件:")
        for file in os.listdir(output_dir):
            if file.endswith('.bin'):
                file_path = os.path.join(output_dir, file)
                file_size = os.path.getsize(file_path)
                print(f"- {file} ({file_size:,} 字节)")
        
        print(f"\n烧录命令示例:")
        print(f"python -m esptool --chip esp32 --port COM端口 --baud 921600 write_flash 0x0 \"{os.path.join(output_dir, 'myled_hub75e_complete.bin')}\"")
    else:
        print("生成完整bin文件失败，但应用程序bin文件已生成")
    
    return 0

if __name__ == "__main__":
    sys.exit(main())
