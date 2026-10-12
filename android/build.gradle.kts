plugins {
    id("com.android.application") version "8.12.0" apply false
    id("com.android.library") version "8.12.0" apply false
    id("org.jetbrains.kotlin.android") version "2.1.20" apply false
}

// One command to refresh VINTA's Maven repo on the console site:
//   gradlew publishToConsole   → apps/vinta/public/maven (served at <console>/maven after deploy)
tasks.register<Copy>("publishToConsole") {
    dependsOn(
        ":vinta:publishAllPublicationsToVintaRepository",
        ":vinta-admob:publishAllPublicationsToVintaRepository",
        ":vinta-applovin:publishAllPublicationsToVintaRepository",
    )
    from(layout.buildDirectory.dir("maven"))
    into(rootDir.resolve("../../apps/vinta/public/maven"))
}
