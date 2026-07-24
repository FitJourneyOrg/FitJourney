rootProject.name = "FitJourney"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")
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
include(":konsist")

include(":shared:core:network")
include(":shared:core:database")
include(":shared:core:designsystem")
include(":shared:core:domain")
include(":shared:core:result")
include(":shared:core:util")

include(":shared:features:auth:domain")
include(":shared:features:auth:data")
include(":shared:features:auth:presentation")

include(":shared:features:profile:domain")
include(":shared:features:profile:data")
include(":shared:features:profile:presentation")


include(":shared:features:exercise:data")
include(":shared:features:exercise:domain")
include(":shared:features:exercise:presentation")



include(":shared:features:workout:data")
include(":shared:features:workout:domain")
include(":shared:features:workout:presentation")
include(":shared:core:catalog")

include(":shared:features:program:data")
include(":shared:features:program:domain")
include(":shared:features:program:presentation")
