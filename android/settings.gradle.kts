// VINTA Android: the SDK, the AdMob custom-event adapter, and a sample app.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "vinta-android"
include(":vinta", ":vinta-admob", ":sample")

// Compile check for the React Native wrapper (../react-native).
include(":rn-check")
project(":rn-check").projectDir = file("../react-native/android")
project(":rn-check").buildFileName = "check.gradle.kts"
