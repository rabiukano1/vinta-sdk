plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    `maven-publish`
}

group = "org.view2earn.vinta"
version = property("VINTA_LEVELPLAY_ADAPTER_VERSION") as String

// Unity LevelPlay custom adapter (blueprint phase 3). The publisher's
// app already ships the LevelPlay SDK, so it is compileOnly: we never force a
// LevelPlay version on them.
android {
    namespace = "org.view2earn.vinta.levelplay"
    compileSdk = 36
    defaultConfig {
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("String", "ADAPTER_VERSION", "\"${property("VINTA_LEVELPLAY_ADAPTER_VERSION")}\"")
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    publishing { singleVariant("release") { withSourcesJar() } }
    kotlinOptions { jvmTarget = "1.8" }
}

dependencies {
    api(project(":vinta"))
    compileOnly("com.unity3d.ads-mediation:mediation-sdk:9.6.1")
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                artifactId = "vinta-levelplay"
                pom {
                    name.set("vinta-levelplay")
                    description.set("VINTA Unity LevelPlay mediation adapter")
                    url.set("https://view2earn.org")
                }
            }
        }
        repositories { maven { name = "vinta"; url = uri(rootProject.layout.buildDirectory.dir("maven")) } }
    }
}
