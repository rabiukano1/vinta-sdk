plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    `maven-publish`
}

group = "org.view2earn.vinta"
version = property("VINTA_VERSION") as String

// Blueprint 3.5: tiny AAR, no third-party dependencies (only the Kotlin
// stdlib, which every Kotlin app already has), nothing that clashes with
// Google Play Services. Networking = HttpURLConnection, JSON = org.json.
android {
    namespace = "org.view2earn.vinta"
    compileSdk = 36
    defaultConfig {
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("String", "VINTA_VERSION", "\"${property("VINTA_VERSION")}\"")
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    publishing { singleVariant("release") { withSourcesJar() } }
    kotlinOptions {
        jvmTarget = "1.8"
        // Interface default methods visible to Java callers of the listeners.
        freeCompilerArgs += "-Xjvm-default=all"
    }
}

// Maven repo (blueprint 2.2 distribution): `gradlew publishAllPublicationsToVintaRepository`
// writes build/maven/ at the project root: static files any web host can serve.
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                artifactId = "vinta"
                pom {
                    name.set("vinta")
                    description.set("VINTA halal ad network Android SDK")
                    url.set("https://view2earn.org")
                }
            }
        }
        repositories { maven { name = "vinta"; url = uri(rootProject.layout.buildDirectory.dir("maven")) } }
    }
}
