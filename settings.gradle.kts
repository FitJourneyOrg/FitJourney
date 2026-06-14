rootProject.name = "FitJourney"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

include(":app:androidApp")
include(":server")
include(":shared-contract")

include(":shared:core:network")
include(":shared:core:database")
include(":shared:core:designsystem")
include(":shared:core:domain")
include(":shared:core:result")
include(":shared:core:util")

include(":shared:features:auth:domain")
include(":shared:features:auth:data")
include(":shared:features:auth:presentation")
