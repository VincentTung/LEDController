plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

apply(from = "../versions.gradle.kts")

android {
    namespace = "com.vincent.library.base"
    compileSdk = rootProject.extra["compileSdkVersion"] as Int

    defaultConfig {
        minSdk = rootProject.extra["minSdkVersion"] as Int

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
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

    // 添加Kotlin编译选项
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    api("org.jetbrains.kotlin:kotlin-stdlib:${rootProject.extra["kotlinVersion"]}")
    api("org.jetbrains.kotlin:kotlin-stdlib-jdk7:${rootProject.extra["kotlinVersion"]}")
    api("org.jetbrains.kotlin:kotlin-stdlib-jdk8:${rootProject.extra["kotlinVersion"]}")
    api("androidx.core:core-ktx:${rootProject.extra["androidxCoreVersion"]}")
    api("androidx.appcompat:appcompat:${rootProject.extra["androidxAppcompatVersion"]}")
    // Kotlin协程依赖
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:${rootProject.extra["kotlinCoroutinesVersion"]}")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:${rootProject.extra["kotlinCoroutinesVersion"]}")
    api("androidx.lifecycle:lifecycle-runtime-ktx:${rootProject.extra["androidxLifecycleVersion"]}")
    api("androidx.lifecycle:lifecycle-viewmodel-ktx:${rootProject.extra["androidxLifecycleVersion"]}")
    api("com.tencent:mmkv:${rootProject.extra["mmkvVersion"]}")
    api("com.github.ydstar:loadingdialog:${rootProject.extra["loadingDialogVersion"]}")
}