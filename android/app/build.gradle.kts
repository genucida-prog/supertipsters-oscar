import java.util.Properties

// The signing material is never committed. CI builds only run `assembleDebug`,
// which does not need it, so the release signing config is registered only when
// keystore.properties is present (local release builds).
val keystoreFile = File(rootProject.projectDir, "keystore.properties")
val hasKeystore = keystoreFile.exists()
val keystoreProperties = Properties().apply {
    if (hasKeystore) {
        keystoreFile.inputStream().use { load(it) }
    }
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.supertipsters.oscar"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.supertipsters.oscar"
        minSdk = 24
        targetSdk = 34
        versionCode = 7
        versionName = "1.0.6"
    }

    signingConfigs {
        if (hasKeystore) {
            create("release") {
                storeFile = File(rootProject.projectDir, keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Debuggable, sideloadable build used while iterating.
            isMinifyEnabled = false
        }
        release {
            // Shrinks the Kotlin/Java code and unused resources. The WebView
            // payload under assets/ is not touched by R8, so the keep rules in
            // proguard-rules.pro protect the @JavascriptInterface bridges.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // The www payload lives in assets/wwww and is loaded through
    // WebViewAssetLoader, so nothing here needs to be exposed or compressed
    // in a special way. Keep the raw files byte-identical.
    androidResources {
        noCompress += listOf("png")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.webkit:webkit:1.11.0")
}