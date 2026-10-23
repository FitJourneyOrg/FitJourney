package dev.rafael.features.profile.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.contract.profile.Accessory
import dev.rafael.contract.profile.HealthScreening
import dev.rafael.contract.profile.MuscleGroup
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.features.profile.domain.repository.ProfileRepository
import dev.rafael.features.profile.presentation.state.QuizEvent
import dev.rafael.features.profile.presentation.state.QuizState
import dev.rafael.features.profile.presentation.state.QuizStep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class QuizViewModel(
    private val repository: ProfileRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(QuizState())
    val state: StateFlow<QuizState> = _state.asStateFlow()

    fun onEvent(event: QuizEvent) {
        when (event) {
            is QuizEvent.GoalSelected -> _state.update { it.copy(goal = event.goal, error = null) }
            is QuizEvent.LevelSelected -> _state.update { it.copy(level = event.level, error = null) }
            is QuizEvent.DaysSelected -> _state.update { it.copy(daysPerWeek = event.days, error = null) }
            is QuizEvent.FocusToggled -> toggleFocus(event.muscle)
            is QuizEvent.WeightChanged -> _state.update { it.copy(weightKg = event.value) }
            is QuizEvent.HeightChanged -> _state.update { it.copy(heightCm = event.value) }
            is QuizEvent.AgeChanged -> _state.update { it.copy(age = event.value, error = null) }
            QuizEvent.SupervisedToggled -> _state.update { it.copy(minorSupervised = !it.minorSupervised) }
            is QuizEvent.EnvironmentSelected -> _state.update { it.copy(environment = event.env, error = null) }
            is QuizEvent.SplitSelected -> _state.update { it.copy(splitPreference = event.split, error = null) }
            is QuizEvent.RestDayToggled -> _state.update { s ->
                val cur = s.unavailableDays
                s.copy(unavailableDays = if (event.dayOfWeek in cur) cur - event.dayOfWeek else cur + event.dayOfWeek, error = null)
            }
            is QuizEvent.HealthToggled -> toggleHealth(event.field)
            is QuizEvent.LimitationToggled -> _state.update { s ->
                val cur = s.limitations
                s.copy(limitations = if (event.limitation in cur) cur - event.limitation else cur + event.limitation)
            }
            is QuizEvent.AccessoryToggled -> _state.update { s ->
                val cur = s.accessories
                s.copy(accessories = if (event.accessory in cur) cur - event.accessory else cur + event.accessory)
            }
            QuizEvent.AcknowledgedRiskToggled ->
                _state.update { it.copy(health = it.health.copy(acknowledgedRisk = !it.health.acknowledgedRisk)) }
            QuizEvent.StartEditing -> startEditing()
            QuizEvent.Next -> next()
            QuizEvent.Back -> back()
        }
    }
    /**
     * Modo edição: busca o perfil já salvo e substitui o estado inteiro por ele, começando do
     * primeiro passo. `accessories` nulo (ambiente != CASA, nunca perguntado) vira o mesmo
     * default do onboarding -- se o usuário trocar pra CASA durante a edição, a tela de
     * acessórios precisa abrir com algo marcado, não em branco.
     */
    private fun startEditing() {
        _state.update { it.copy(isEditing = true, isLoadingExisting = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.getProfile()) {
                is AppResult.Success -> {
                    val p = result.value
                    _state.update {
                        QuizState(
                            step = QuizStep.GOAL,
                            goal = p.goal,
                            level = p.level,
                            daysPerWeek = p.daysPerWeek,
                            splitPreference = p.splitPreference,
                            unavailableDays = p.unavailableDays,
                            focusAreas = p.focusAreas,
                            weightKg = p.weightKg,
                            heightCm = p.heightCm,
                            age = p.age,
                            minorSupervised = p.minorSupervised,
                            environment = p.environment,
                            accessories = p.accessories ?: listOf(Accessory.ELEVATED_SURFACE),
                            limitations = p.limitations,
                            health = p.health ?: HealthScreening(),
                            isEditing = true,
                            isLoadingExisting = false,
                        )
                    }
                }
                is AppResult.Failure ->
                    _state.update { it.copy(isLoadingExisting = false, error = result.error) }
            }
        }
    }

    private fun toggleHealth(field: QuizEvent.HealthField) {
        _state.update { s ->
            val h = s.health
            val next = when (field) {
                QuizEvent.HealthField.CARDIAC -> h.copy(hasCardiacCondition = !h.hasCardiacCondition)
                QuizEvent.HealthField.CHEST_PAIN -> h.copy(hasChestPainDuringActivity = !h.hasChestPainDuringActivity)
                QuizEvent.HealthField.JOINT -> h.copy(hasJointOrBoneIssue = !h.hasJointOrBoneIssue)
                QuizEvent.HealthField.MEDICATION -> h.copy(takesContinuousMedication = !h.takesContinuousMedication)
            }
            s.copy(health = if (!next.hasAnyRisk) next.copy(acknowledgedRisk = false) else next)
        }
    }

    /** Toggle manual com trava em 2: contém -> remove; senão se <2 -> adiciona; senão ignora. */
    private fun toggleFocus(muscle: MuscleGroup) {
        _state.update { s ->
            val current = s.focusAreas
            val next = when {
                muscle in current -> current - muscle
                current.size < 2 -> current + muscle
                else -> current   // cheio: ignora o 3º
            }
            s.copy(focusAreas = next)
        }
    }

    private fun next() {
        val s = _state.value
        if (!s.canAdvance) return
        val steps = s.visibleSteps          // iniciante/saúde geral pulam o FOCO (#26/#24)
        val idx = steps.indexOf(s.step)
        if (idx < steps.lastIndex) {
            _state.update { it.copy(step = steps[idx + 1]) }
        } else {
            submit()   // último passo -> envia
        }
    }

    private fun back() {
        val steps = _state.value.visibleSteps
        val idx = steps.indexOf(_state.value.step)
        if (idx > 0) _state.update { it.copy(step = steps[idx - 1]) }
    }

    private fun submit() {
        val s = _state.value
        // obrigatórios garantidos pelo canAdvance de cada passo; `toProfileOrNull` é a checagem
        // defensiva, e é pura de propósito -- ver KDoc dela no QuizState (C2 tier 2).
        val profile = s.toProfileOrNull()
        if (profile == null) {
            _state.update { it.copy(error = AppError.Validation("Responda as perguntas obrigatórias.")) }
            return
        }
        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.saveProfile(profile)) {
                is AppResult.Success ->
                    _state.update { it.copy(isSubmitting = false, completed = true) }
                is AppResult.Failure ->
                    _state.update { it.copy(isSubmitting = false, error = result.error) }
            }
        }
    }
}