// VINTA Flutter plugin (Android). The VINTA SDK comes from VINTA's Maven repo.
group = "org.view2earn.vinta.flutter"
version = "0.1.0"

buildscript {
    val kotlinVersion = "2.4.0"
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:9.1.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    }
}

// Override for testing with a local copy, in the app's android/gradle.properties:
//   vintaMavenUrl=file:///C:/path/to/vinta/apps/vinta/public/maven
val vintaMaven = (rootProject.findProperty("vintaMavenUrl") as String?) ?: "https://vinta-console.pages.dev/maven"

// The app resolves this plugin's dependencies with ITS repositories, so the
// VINTA repo is added to every project in the build, not just this one.
rootProject.allprojects {
    repositories {
        maven { url = uri(vintaMaven) }
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

plugins {
    id("com.android.library")
}

android {
    namespace = "org.view2earn.vinta.flutter"
    compileSdk = 36

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
    }
    defaultConfig {
        minSdk = 24
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    implementation("org.view2earn.vinta:vinta:0.5.0")
}
