plugins {
    `kotlin-dsl`
}

dependencies {
    // Versões vêm do catálogo importado em settings.gradle.kts — sem duplicação.
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.android.gradle.plugin)

    // Novos: necessários p/ os convention plugins de cliente aplicarem KMP-Android e Compose
    implementation(libs.androidKmp.gradle.plugin)
    implementation(libs.composeMultiplatform.gradle.plugin)
    implementation(libs.composeCompiler.gradle.plugin)
}