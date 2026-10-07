plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.goimium.browser"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.goimium.browser"
        // 24 covers ~99% of active devices and is the floor for the modern WebView APIs
        // (DOCUMENT_START_SCRIPT) that Goimium's identity injection depends on.
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // androidx.webkit is required, not optional: WebViewCompat.addDocumentStartJavaScript()
    // is the only API that injects the identity script before page scripts run.
    implementation("androidx.webkit:webkit:1.12.1")
}
