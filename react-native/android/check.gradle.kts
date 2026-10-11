// Compile check only (used from vinta-sdk/android, not by apps): builds this
// module's Kotlin against the local SDK project and React Native 0.86.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "org.view2earn.vinta.rn"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    compileOnly("com.facebook.react:react-android:0.86.0")
    implementation(project(":vinta"))
}
