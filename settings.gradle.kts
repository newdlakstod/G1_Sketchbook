import java.util.Properties

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "G1 Sketchbook"
include(":app")
include(":pagecurl")
val localProperties = Properties().apply {
    file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
val pageCurlDirectory = localProperties.getProperty("pagecurl.dir") ?: "../pagecurl"
project(":pagecurl").projectDir = file(pageCurlDirectory)
