package dev.rafael.features.profile.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.contract.profile.MuscleGroup
import dev.rafael.core.result.AppResult
import dev.rafael.features.profile.domain.model.Profile
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
            is QuizEvent.EnvironmentSelected -> _state.update { it.copy(environment = event.env, error = null) }
            is QuizEvent.HealthToggled -> toggleHealth(event.field)
            QuizEvent.AcknowledgedRiskToggled ->
                _state.update { it.copy(health = it.health.copy(acknowledgedRisk = !it.health.acknowledgedRisk)) }
            QuizEvent.Next -> next()
            QuizEvent.Back -> back()
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
        val steps = QuizStep.entries
        val idx = steps.indexOf(s.step)
        if (idx < steps.lastIndex) {
            _state.update { it.copy(step = steps[idx + 1]) }
        } else {
            submit()   // último passo (BODY) -> envia
        }
    }

    private fun back() {
        val steps = QuizStep.entries
        val idx = steps.indexOf(_state.value.step)
        if (idx > 0) _state.update { it.copy(step = steps[idx - 1]) }
    }

    private fun submit() {
        val s = _state.value
        // obrigatórios garantidos pelo canAdvance de cada passo; checagem defensiva:
        val goal = s.goal; val level = s.level; val days = s.daysPerWeek
        if (goal == null || level == null || days == null) {
            _state.update { it.copy(error = "Responda as perguntas obrigatórias.") }
            return
        }
        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            val profile = Profile(
                goal = goal,
                level = level,
                daysPerWeek = days,
                focusAreas = s.focusAreas,
                weightKg = s.weightKg,
                heightCm = s.heightCm,
                environment = s.environment,   // <- novo
                health = s.health,             // <- novo
                onboardingCompleted = false,   // server deriva
            )
            when (val result = repository.saveProfile(profile)) {
                is AppResult.Success ->
                    _state.update { it.copy(isSubmitting = false, completed = true) }
                is AppResult.Failure ->
                    _state.update { it.copy(isSubmitting = false, error = result.error.message) }
            }
        }
    }
}