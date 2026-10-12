plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    `maven-publish`
}

group = "org.view2earn.vinta"
version = property("VINTA_APPLOVIN_ADAPTER_VERSION") as String

// AppLovin MAX custom SDK network adapter (blueprint phase 3). The publisher's
// app already ships the AppLovin SDK, so it is compileOnly: we never force a
// MAX version on them.
android {
    namespace = "org.view2earn.vinta.applovin"
    compileSdk = 36
    defaultConfig {
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("String", "ADAPTER_VERSION", "\"${property("VINTA_APPLOVIN_ADAPTER_VERSION")}\"")
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
    compileOnly("com.applovin:applovin-sdk:13.6.4")
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                artifactId = "vinta-applovin"
                pom {
                    name.set("vinta-applovin")
                    description.set("VINTA AppLovin MAX mediation adapter")
                    url.set("https://view2earn.org")
                }
            }
        }
        repositories { maven { name = "vinta"; url = uri(rootProject.layout.buildDirectory.dir("maven")) } }
    }
}
