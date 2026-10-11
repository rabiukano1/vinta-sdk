plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    `maven-publish`
}

group = "org.view2earn.vinta"
version = property("VINTA_ADMOB_ADAPTER_VERSION") as String

// AdMob custom-event adapter (blueprint 2.2 step 1). The publisher's app
// already ships Google Mobile Ads, so it is compileOnly: we never force a GMA
// version on them (blueprint 2.6 compatibility matrix lives in README.md).
android {
    namespace = "org.view2earn.vinta.admob"
    compileSdk = 36
    defaultConfig {
        minSdk = 23 // Google Mobile Ads 24.x minimum
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("String", "ADAPTER_VERSION", "\"${property("VINTA_ADMOB_ADAPTER_VERSION")}\"")
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
    compileOnly("com.google.android.gms:play-services-ads:24.9.0")
}

// Maven repo (blueprint 2.2 distribution): `gradlew publishAllPublicationsToVintaRepository`
// writes build/maven/ at the project root: static files any web host can serve.
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                artifactId = "vinta-admob"
                pom {
                    name.set("vinta-admob")
                    description.set("VINTA AdMob mediation adapter")
                    url.set("https://view2earn.org")
                }
            }
        }
        repositories { maven { name = "vinta"; url = uri(rootProject.layout.buildDirectory.dir("maven")) } }
    }
}
