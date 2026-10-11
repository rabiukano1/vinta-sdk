plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Test app for the SDK: paste an app key from the VINTA publisher portal.
android {
    namespace = "org.view2earn.vinta.sample"
    compileSdk = 36
    defaultConfig {
        applicationId = "org.view2earn.vinta.sample"
        minSdk = 21
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions { jvmTarget = "1.8" }
}

dependencies {
    implementation(project(":vinta"))
}
