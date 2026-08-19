pluginManagement {
    includeBuild("build-logic")
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

rootProject.name = "NetInspector"

include(":app")
include(":core:model")
include(":core:common")
include(":core:designsystem")
include(":data:wifi")
include(":data:lan")
include(":data:diagnostics")
include(":data:persistence")
