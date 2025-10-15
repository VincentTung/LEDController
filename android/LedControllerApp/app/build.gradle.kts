plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

apply(from = "../versions.gradle.kts")

android {
    namespace = "com.vincent.android.ledcontroller"
    compileSdk = rootProject.extra["compileSdkVersion"] as Int
    // buildToolsVersion = rootProject.extra["buildToolsVersion"] as String

    defaultConfig {
        applicationId = "com.vincent.android.ledcontroller"
        minSdk = rootProject.extra["minSdkVersion"] as Int
        targetSdk = rootProject.extra["targetSdkVersion"] as Int
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    
    signingConfigs {
        getByName("debug") {
            // debug配置
        }
        create("release") {
            keyAlias = "ble"
            keyPassword = "11111111"
            storeFile = file("../key/key.jks")
            storePassword = "11111111"
            enableV2Signing = true
            enableV1Signing = true
        }
    }
    
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // 添加Java编译选项
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // 添加Kotlin编译选项来解决版本冲突
    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs += listOf("-Xskip-metadata-version-check")
    }

    // 启用ViewBinding
    buildFeatures {
        viewBinding = true
    }
}

// 添加依赖解析策略来解决Kotlin版本冲突
configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-stdlib:${rootProject.extra["kotlinVersion"]}")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:${rootProject.extra["kotlinVersion"]}")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:${rootProject.extra["kotlinVersion"]}")
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    //自定义蓝牙库
    api(project(":vt_ble"))
    
    implementation("androidx.appcompat:appcompat:${rootProject.extra["androidxAppcompatVersion"]}")
    implementation("androidx.core:core-ktx:${rootProject.extra["androidxCoreVersion"]}")
    implementation("androidx.constraintlayout:constraintlayout:${rootProject.extra["androidxConstraintLayoutVersion"]}")
    implementation("androidx.recyclerview:recyclerview:${rootProject.extra["androidxRecyclerViewVersion"]}")
    
    // Kotlin协程依赖
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${rootProject.extra["kotlinCoroutinesVersion"]}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:${rootProject.extra["kotlinCoroutinesVersion"]}")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:${rootProject.extra["androidxLifecycleVersion"]}")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:${rootProject.extra["androidxLifecycleVersion"]}")
    
    // Activity Result API
    implementation("androidx.activity:activity-ktx:1.7.2")

    implementation("com.github.tbruyelle:rxpermissions:${rootProject.extra["rxPermissionsVersion"]}")
    implementation("io.reactivex.rxjava2:rxjava:${rootProject.extra["rxJavaVersion"]}")
    implementation("com.github.bumptech.glide:glide:${rootProject.extra["glideVersion"]}")
    annotationProcessor("com.github.bumptech.glide:compiler:${rootProject.extra["glideVersion"]}")
}