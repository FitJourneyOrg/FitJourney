plugins {
    id("fitjourney.kmp-library")
}
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared.core.result)
        }
    }
}