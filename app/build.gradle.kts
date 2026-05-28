import java.util.Properties

plugins {
    id("com.android.application")
}

val aiProperties = Properties().apply {
    val configFile = rootProject.file("ai.properties")
    if (configFile.exists()) {
        configFile.inputStream().use { load(it) }
    }
}

fun aiProperty(key: String, defaultValue: String = ""): String {
    return aiProperties.getProperty(key, defaultValue).trim()
}

fun quotedBuildConfig(value: String): String {
    return "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
}

android {
    namespace = "com.noteai.noteai"
    compileSdk = 35

    ndkVersion = "23.2.8568313"

    defaultConfig {
        applicationId = "com.noteai.noteai"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "AI_MODE", quotedBuildConfig(aiProperty("ai.mode", "proxy")))
        buildConfigField("String", "AI_PROXY_BASE_URL", quotedBuildConfig(aiProperty("ai.proxyBaseUrl")))
        buildConfigField("String", "AI_DIRECT_BASE_URL", quotedBuildConfig(aiProperty("ai.directBaseUrl")))
        buildConfigField("String", "AI_DIRECT_API_KEY", quotedBuildConfig(aiProperty("ai.directApiKey")))
        buildConfigField("String", "AI_DIRECT_MODEL", quotedBuildConfig(aiProperty("ai.directModel")))
        buildConfigField("int", "AI_TIMEOUT_MS", ((aiProperty("ai.timeoutSeconds", "30").toIntOrNull() ?: 30).coerceAtLeast(1) * 1000).toString())

        ndk {
            abiFilters += "arm64-v8a"
        }

        externalNativeBuild {
            ndkBuild {
                arguments += "APP_PLATFORM=android-31"
                arguments += "APP_STL=c++_shared"
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
        }
        release {
            isMinifyEnabled = false
        }
    }

    externalNativeBuild {
        ndkBuild {
            path = file("src/main/cpp/Android.mk")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(fileTree("libs") { include("*.jar") })
}
