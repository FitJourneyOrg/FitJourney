package dev.rafael.app

import android.app.Application
import dev.rafael.core.network.di.networkModule
import dev.rafael.features.auth.data.di.authDataModule
import dev.rafael.features.auth.presentation.di.authPresentationModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class FitJourneyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@FitJourneyApp)
            modules(networkModule, authDataModule, authPresentationModule)
        }
    }
}