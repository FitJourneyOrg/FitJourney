package dev.rafael.features.achievements.presentation.viewmodel

import dev.rafael.contract.stats.AchievementDto
import dev.rafael.core.result.AppError
import dev.rafael.features.achievements.domain.Achievements
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * `destaque` (diálogo de conquista desbloqueada — G.6, débito fechado em 2026-09-24).
 *
 * O resto do `AchievementsViewModel` (sync, catálogo) fica sem teste próprio por ora — mesmo
 * ponto cego que `AchievementService` tinha do lado do servidor antes deste débito. Este arquivo
 * cobre só o comportamento NOVO.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AchievementsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private fun conquista(id: String, desbloqueada: Boolean = true) = AchievementDto(
        id = id,
        unlockedAt = if (desbloqueada) "2026-09-24T12:00:00" else null,
        current = 1,
        target = 1,
    )

    private class FakeAchievements(
        private val lista: MutableStateFlow<List<AchievementDto>>,
    ) : Achievements {
        override fun observar(): Flow<List<AchievementDto>> = lista
        override suspend fun sincronizar(forcar: Boolean): AppError? = null
        override suspend fun jaSincronizou(): Boolean = true
    }

    @Test
    fun `destaque resolve quando a lista chega com o id`() = runTest(dispatcher) {
        val lista = MutableStateFlow(listOf(conquista("TREINOS_10")))
        val vm = AchievementsViewModel(FakeAchievements(lista), destaqueInicial = "TREINOS_10")
        advanceUntilIdle()

        assertEquals("TREINOS_10", vm.state.value.destaque?.id)
    }

    @Test
    fun `destaque continua nulo quando o id nao existe na lista`() = runTest(dispatcher) {
        // Caso do servidor mais novo que o app, ou sync que ainda não trouxe a conquista nova.
        val lista = MutableStateFlow(listOf(conquista("TREINOS_10")))
        val vm = AchievementsViewModel(FakeAchievements(lista), destaqueInicial = "ID_QUE_NAO_EXISTE")
        advanceUntilIdle()

        assertNull(vm.state.value.destaque)
    }

    @Test
    fun `sem destaqueInicial nunca abre dialogo`() = runTest(dispatcher) {
        val lista = MutableStateFlow(listOf(conquista("TREINOS_10")))
        val vm = AchievementsViewModel(FakeAchievements(lista), destaqueInicial = null)
        advanceUntilIdle()

        assertNull(vm.state.value.destaque)
    }

    @Test
    fun `dispensarDestaque zera e uma nova sincronizacao nao traz de volta`() = runTest(dispatcher) {
        val lista = MutableStateFlow(listOf(conquista("TREINOS_10")))
        val vm = AchievementsViewModel(FakeAchievements(lista), destaqueInicial = "TREINOS_10")
        advanceUntilIdle()
        assertEquals("TREINOS_10", vm.state.value.destaque?.id)

        vm.dispensarDestaque()
        assertNull(vm.state.value.destaque)

        // Simula o ON_RESUME chamando sincronizar(forcar = true) de novo -- o catálogo reemite.
        lista.value = listOf(conquista("TREINOS_10"), conquista("STREAK_7"))
        advanceUntilIdle()

        assertNull(vm.state.value.destaque, "diálogo já dispensado não pode reabrir sozinho")
    }
}
