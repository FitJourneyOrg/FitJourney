plugins {
    id("fitjourney.kmp-library")
}
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared.core.result)
            // NÃO depende de workout:domain — o Konsist trata domain de TODAS as
            // features como uma camada única (domain.dependsOnNothing()), então até
            // dependência de mão única entre domains de features quebra a regra.
            // Program tem seu próprio ProgramWorkout (versão enxuta, sem exercises/sets).
        }
    }
}
