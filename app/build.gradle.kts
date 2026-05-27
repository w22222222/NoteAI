plugins {
    id("com.android.application")
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
}

dependencies {
    implementation(fileTree("libs") { include("*.jar") })
}
