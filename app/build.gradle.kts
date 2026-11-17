plugins {
    id("com.android.application")
}

android {
    namespace = "org.irface"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.irface"
        minSdk = 30
        targetSdk = 35
        versionCode = 8
        versionName = "0.8"
        ndk {
            // El Mi 8 es arm64; así el APK pesa mucho menos
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    androidResources {
        noCompress += "onnx"
    }

    lint {
        // PROVIDE_TRUST_AGENT es un permiso privilegiado: es intencionado (app de sistema)
        disable += "ProtectedPermissions"
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    // Stub de solo compilación de TrustAgentService (API de sistema); no se incluye en el APK
    compileOnly(files("libs/trustagent-stub.jar"))
    // OpenCV oficial en Maven Central (incluye FaceDetectorYN y FaceRecognizerSF)
    implementation("org.opencv:opencv:4.10.0")
}
