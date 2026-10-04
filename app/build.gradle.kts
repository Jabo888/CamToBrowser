plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    // Namespace = Paket der Quelldateien (kann vom Application-Id abweichen)
    namespace = "de.r7s.camtobrowser"
    compileSdk = 36
    defaultConfig {
        applicationId = "de.r7s.camtobrowser"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "1.0.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.0")
}
