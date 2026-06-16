plugins {
    `kotlin-dsl`
}

dependencies {
    // Versões vêm do catálogo importado em settings.gradle.kts — sem duplicação.
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.android.gradle.plugin)
}