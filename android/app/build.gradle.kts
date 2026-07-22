plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.chaquo.python")
}

android {
    namespace = "com.lumo.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.lumo.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        ndk {
            abiFilters += "arm64-v8a"
        }
        val apiKey = System.getenv("OPENAI_API_KEY") ?: "sk-placeholder"
        val apiBase = System.getenv("OPENAI_API_BASE") ?: "http://api.hyper-op.com/"
        val modelName = System.getenv("OPENAI_MODEL") ?: "glm-5.2"
        buildConfigField("String", "OPENAI_API_KEY", "\"$apiKey\"")
        buildConfigField("String", "OPENAI_API_BASE", "\"$apiBase\"")
        buildConfigField("String", "OPENAI_MODEL", "\"$modelName\"")
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }


    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

chaquopy {
    defaultConfig {
        version = "3.12"
        pip {
            install("aiohttp")
            install("${rootProject.projectDir}/senza-wheel/senza_sdk-1.0.0-cp39-abi3-android_24_arm64_v8a.whl")
        }
    }
    sourceSets {
        getByName("main") {
            srcDir("${rootProject.projectDir}/../python")
            srcDir("src/main/python")
        }
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)

    // Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Lifecycle + ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")

    // Core
    implementation("androidx.core:core-ktx:1.13.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
}
