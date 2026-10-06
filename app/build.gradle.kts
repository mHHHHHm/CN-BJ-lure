import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// ---------------------------------------------------------------------------
// 签名信息从 local.properties 读，**不要**写在这个文件里。
//
// 原来这里硬编码着 storePassword / keyPassword 的明文 —— 那等于把签名私钥
// 的钥匙一起提交上去了，别人能拿它签个恶意包冒充你发布。
// local.properties 是本地文件，进 .gitignore，永远不进版本库。
//
// 要出 release 包的人，在自己机器的 local.properties 里补上这四行：
//   RELEASE_STORE_FILE=../keystore/your.p12
//   RELEASE_STORE_PASSWORD=***
//   RELEASE_KEY_ALIAS=***
//   RELEASE_KEY_PASSWORD=***
//
// 没配的话也能编 —— 只是 release 不做签名，方便别人 clone 下来直接跑通。
// ---------------------------------------------------------------------------
val keystoreProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) {
        f.inputStream().use { stream -> load(stream) }
    }
}

val releaseStoreFile = keystoreProps.getProperty("RELEASE_STORE_FILE")
val hasReleaseSigning = !releaseStoreFile.isNullOrBlank() &&
    rootProject.file(releaseStoreFile).exists()

android {
    namespace = "com.bjlure.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.fatfish.lure"
        minSdk = 24
        targetSdk = 34
        // 版本号规矩：数据或功能有实质性更新就 +1，别让用户拿着旧包以为是最新的。
        // 1.0 初版 36 个点 / 1.1 淡蓝主题+高德地图+筛选折叠 / 1.2 102 个点+假饵页+深蓝选中态
        versionCode = 3
        versionName = "1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = keystoreProps.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = keystoreProps.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = keystoreProps.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // 配了签名才挂上；没配就出未签名包，不影响别人编译
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    androidResources {
        // 地图瓦片是 png，本身已经压过了，别再让 aapt 压一遍，免得读取时还得解压
        noCompress += listOf("png")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.coroutines.android)

    // 注意：这里刻意没有引入 play-services-location。
    // 国内 Android 设备普遍不带 Google Play 服务，用 FusedLocationProvider 会直接拿不到定位。
    // 定位改用系统原生 LocationManager，见 ui/location/LocationProvider.kt。

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.ui.tooling)
}
