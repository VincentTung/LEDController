plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

apply(from = "../versions.gradle.kts")

android {
    namespace = "com.vincent.library.ble"
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
    implementation("androidx.core:core-ktx:${rootProject.extra["androidxCoreVersion"]}")
    api(project(":vt_base"))
}