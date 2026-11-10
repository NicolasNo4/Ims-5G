plugins {
    id("com.android.application")
}

android {
    enableKotlin = false
    namespace = "io.github.vvb2060.ims"
    defaultConfig {
        versionCode = 6
        versionName = "3.1"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            vcsInfo.include = false
            proguardFiles("proguard-rules.pro")
            signingConfig = signingConfigs["debug"]
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "**"
        }
    }
    lint {
        checkReleaseBuilds = false
    }
    dependenciesInfo {
        includeInApk = false
    }
}

dependencies {
    implementation(libs.shizuku.provider)
    implementation(libs.shizuku.api)
    implementation(libs.hiddenapibypass)
    
    // 增加这行：提供 Android 隐藏 API (如 ITelephony) 的编译期依赖
    compileOnly("dev.rikka.hidden:stub:43.0.5") 
}
