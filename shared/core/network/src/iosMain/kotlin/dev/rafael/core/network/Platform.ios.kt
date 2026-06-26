package dev.rafael.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin

actual fun httpEngine(): HttpClientEngine = Darwin.create()