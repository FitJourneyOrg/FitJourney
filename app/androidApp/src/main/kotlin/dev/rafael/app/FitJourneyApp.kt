package dev.rafael.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.ktor3.KtorNetworkFetcherFactory
import dev.rafael.app.data.sync.SyncScheduler
import dev.rafael.app.di.appModule
import dev.rafael.core.database.di.databaseModule
import dev.rafael.core.network.HttpClientFactory
import dev.rafael.core.network.di.networkModule
import dev.rafael.features.auth.data.di.authDataModule
import dev.rafael.features.auth.presentation.di.authPresentationModule
import dev.rafael.features.profile.data.di.profileDataModule
import dev.rafael.features.exercise.data.di.exerciseDataModule
import dev.rafael.features.exercise.presentation.di.exercisePresentationModule
import dev.rafael.features.profile.presentation.di.profilePresentationModule
import dev.rafael.features.workout.data.di.workoutDataModule
import dev.rafael.features.workout.presentation.di.workoutPresentationModule
import dev.rafael.features.program.data.di.programDataModule
import dev.rafael.features.program.presentation.di.programPresentationModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

/**
 * `SingletonImageLoader.Factory`: o Coil do app inteiro passa a usar o **mesmo cliente HTTP** do
 * resto do aplicativo.
 *
 * **Por que isso virou necessário na fatia B.** A foto de check-in é servida por uma rota
 * autenticada, que confere filiação ao grupo a cada leitura (foi a decisão desta fase). O loader
 * padrão do Coil não conhece a sessão: a requisição saía sem `Authorization`, voltava 401, e o
 * feed mostrava o retângulo de erro.
 *
 * **Por que reusar o cliente Ktor em vez de montar um OkHttp com interceptor.** A autenticação
 * passaria a existir em dois lugares — e o dia em que o cabeçalho mudar, um dos dois fica para
 * trás em silêncio, quebrando só as imagens. As imagens de exercício continuam funcionando porque
 * são públicas; um bug assim só aparece onde há permissão.
 */
class FitJourneyApp : Application(), SingletonImageLoader.Factory {

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory(httpClient = { GlobalContext.get().get() })) }
            .build()

    override fun onCreate() {
        super.onCreate()
        // ANTES do Koin: o cliente HTTP é criado na construção dos módulos, e a base precisa já
        // estar valendo. Escrita única, no boot — é o que torna aceitável o `var` global.
        HttpClientFactory.BASE_URL = BuildConfig.API_BASE_URL
        val koin = startKoin {
            androidLogger()
            androidContext(this@FitJourneyApp)
            modules(
                networkModule,
                authDataModule,
                databaseModule,
                authPresentationModule,
                profileDataModule,
                profilePresentationModule,
                exerciseDataModule,
                exercisePresentationModule,
                workoutDataModule,
                workoutPresentationModule,
                programDataModule,
                programPresentationModule,
                appModule
            )
        }.koin
        // rede de segurança: mesmo que o app fique fechado, o WorkManager tenta esvaziar
        // a outbox a cada 6h quando houver conexão.
        koin.get<SyncScheduler>().agendarPeriodico()
    }
}