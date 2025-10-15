// Top-level build file where you can add configuration options common to all sub-projects/modules.

buildscript {
    val kotlinVersion = "1.9.20"
    repositories {
        maven {
            url = uri("https://maven.aliyun.com/repository/public")
        }
        maven {
            url = uri("https://maven.aliyun.com/repository/google")
        }
        google()
        mavenCentral()
        mavenLocal()
        maven {
            url = uri("https://jitpack.io")
        }
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.5.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")

        // NOTE: Do not place your application dependencies here; they belong
        // in the individual module build.gradle files
    }
}

allprojects {
    repositories {
        maven {
            url = uri("https://maven.aliyun.com/repository/public")
        }
        maven {
            url = uri("https://maven.aliyun.com/repository/google")
        }
        google()
        mavenCentral()
        mavenLocal()
        maven {
            url = uri("https://jitpack.io")
        }
    }
    
//           // 禁用 JDK 图像转换以避免 jlink.exe 问题
//           tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
//               kotlinOptions {
//                   jvmTarget = "11"
//               }
//           }
//
//           // 强制禁用所有 JDK 图像转换任务
//           tasks.whenTaskAdded {
//               if (name.contains("JdkImageTransform") || name.contains("jlink")) {
//                   enabled = false
//               }
//           }
}

// 应用版本管理
apply(from = "versions.gradle.kts")

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}