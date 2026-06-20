package dev.rafael.server.plugins

import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import io.ktor.server.application.install
import dev.rafael.server.di.appModule
import io.ktor.server.application.Application

fun Application.configureKoin() {
    install(Koin) {
        slf4jLogger()
        modules(appModule)
    }
}