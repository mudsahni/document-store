pluginManagement {
    repositories {
        // Using RepositoryHandler.exclusiveContent for more control
        exclusiveContent {
            forRepository {
                gradlePluginPortal()
            }
            filter {
                // Configure what this repository will contain
                includeGroup("org.gradle.plugin")
            }
        }
        mavenCentral()
        google()
        // Add Kotlin plugin repository
        maven("https://plugins.gradle.org/m2/")
    }
}
rootProject.name = "documentstore"
