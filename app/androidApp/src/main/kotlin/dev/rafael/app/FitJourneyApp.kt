package dev.rafael.app

import android.app.Application
import dev.rafael.core.network.di.networkModule
import dev.rafael.features.auth.data.di.authDataModule
import dev.rafael.features.auth.presentation.di.authPresentationModule
import dev.rafael.features.profile.data.di.profileDataModule
import dev.rafael.features.profile.presentation.di.profilePresentationModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class FitJourneyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@FitJourneyApp)
            modules(
                networkModule,
                authDataModule,
                authPresentationModule,
                profileDataModule,           // <- novo
                profilePresentationModule,   // <- novo
            )
        }
    }
}