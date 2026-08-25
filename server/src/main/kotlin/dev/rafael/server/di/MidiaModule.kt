package dev.rafael.server.di

import dev.rafael.server.media.ArmazenamentoDeMidia
import dev.rafael.server.media.ArmazenamentoEmDisco
import org.koin.dsl.module
import java.io.File

/**
 * Módulo próprio, e não uma linha no [appModule], porque este precisa de um argumento: a pasta
 * só é conhecida depois de ler a configuração do Ktor. É também o ponto único a trocar no dia
 * em que a foto for para R2 — uma linha, um implementador novo (decisão 10.5).
 */
fun midiaModule(pastaDeUploads: File) = module {
    single<ArmazenamentoDeMidia> { ArmazenamentoEmDisco(pastaDeUploads) }
}
