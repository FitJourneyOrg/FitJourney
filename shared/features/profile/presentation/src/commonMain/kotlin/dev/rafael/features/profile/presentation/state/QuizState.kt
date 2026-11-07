package dev.rafael.features.profile.presentation.state

import dev.rafael.contract.profile.Accessory
import dev.rafael.contract.profile.BodyLimitation
import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.HealthScreening
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.MinutesAvailable
import dev.rafael.contract.profile.MuscleGroup
import dev.rafael.contract.profile.SplitType
import dev.rafael.contract.profile.TimeOffTraining
import dev.rafael.contract.profile.TrainingConsistency
import dev.rafael.contract.profile.TrainingEnvironment
import dev.rafael.contract.profile.nivelDerivado
import dev.rafael.core.result.AppError
import dev.rafael.features.profile.domain.model.Profile

/** Os passos do quiz, em ordem. REST_DAYS (Estágio 2) após DAYS; SPLIT (ARCH #29) após ENVIRONMENT. */
enum class QuizStep { GOAL, CONSISTENCY, TIME_OFF, LIGHT_MODE, AGE, DAYS, MINUTES, REST_DAYS, FOCUS, BODY, ENVIRONMENT, ACCESSORIES, SPLIT, HEALTH, LIMITATIONS }
data class QuizState(
    val step: QuizStep = QuizStep.GOAL,
    val goal: Goal? = null,
    val trainingConsistency: TrainingConsistency? = null,   // fatia 12 -- substitui a escolha livre de nível
    val timeOffTraining: TimeOffTraining? = null,             // fatia 12
    // fatia 12 (resto): "quer comecar no modo leve?" -- sempre respondida (default nao), nunca
    // nula no cliente; o passo ja chega com uma escolha marcada, igual ao padrao de accessories.
    val preferLightMode: Boolean = false,
    val daysPerWeek: Int? = null,
    val minutesAvailable: MinutesAvailable? = null,           // fatia 12 (resto)
    val splitPreference: SplitType? = null,   // ARCH #29: null = usa o recomendado
    val unavailableDays: List<Int> = emptyList(),   // dias (1=Seg..7=Dom) que NÃO quer treinar
    val focusAreas: List<MuscleGroup> = emptyList(),
    val weightKg: Double? = null,
    val heightCm: Double? = null,
    val age: Int? = null,                  // #24
    val minorSupervised: Boolean = false,  // #24
    val environment: TrainingEnvironment? = null,          // <- novo
    // V66: só pergunta quando environment == CASA. "Apoio firme" vem marcado por padrão --
    // quase todo mundo tem uma cadeira, e o rótulo explícito é o que torna o default honesto.
    val accessories: List<Accessory> = listOf(Accessory.ELEVATED_SURFACE),
    val limitations: List<BodyLimitation> = emptyList(),
    val health: HealthScreening = HealthScreening(),        // <- novo (não-null, começa tudo false)
    val isSubmitting: Boolean = false,
    val error: AppError? = null,
    val completed: Boolean = false,
    // Modo edição (pós-onboarding, ARCH #37-acessórios passo 4): `true` troca "Concluir" por
    // "Salvar" e mostra um jeito de sair sem terminar. `isLoadingExisting` cobre o intervalo
    // entre abrir a tela e o GET /me/profile voltar.
    val isEditing: Boolean = false,
    val isLoadingExisting: Boolean = false,
) {


    /**
     * Nível deixou de ser escolha livre (fatia 12): deriva por [nivelDerivado] das duas
     * perguntas novas -- nulo até ambas serem respondidas.
     */
    val levelDerivado: Level?
        get() {
            val c = trainingConsistency; val t = timeOffTraining
            return if (c != null && t != null) nivelDerivado(c, t) else null
        }

    /** Foco só faz sentido p/ INTER/ADVANCED e não p/ saúde geral — espelha o motor (#26/#24). */
    val focusEligible: Boolean
        get() = levelDerivado != null && levelDerivado != Level.BEGINNER && goal != Goal.GENERAL_HEALTH

    /** Passos visíveis: iniciante (ou saúde geral) não veem o passo de FOCO. */
    val visibleSteps: List<QuizStep>
        get() = QuizStep.entries.filter {
            (it != QuizStep.FOCUS || focusEligible) &&
                (it != QuizStep.ACCESSORIES || environment == TrainingEnvironment.CASA)
        }

    /** Sobra dia suficiente pra treinar o que o usuário pediu? (7 - dias off >= daysPerWeek) */
    val hasEnoughFreeDays: Boolean
        get() = 7 - unavailableDays.size >= (daysPerWeek ?: 0)

    val canAdvance: Boolean
        get() = when (step) {
            QuizStep.GOAL -> goal != null
            QuizStep.CONSISTENCY -> trainingConsistency != null
            QuizStep.TIME_OFF -> timeOffTraining != null
            QuizStep.LIGHT_MODE -> true   // pulável -- já chega com "não" marcado
            // #24: idade obrigatória e válida; menor de 18 só passa com o aceite de supervisão.
            QuizStep.AGE -> age != null && age in 5..120 && (age >= 18 || minorSupervised)
            QuizStep.DAYS -> daysPerWeek != null
            QuizStep.MINUTES -> minutesAvailable != null
            QuizStep.REST_DAYS -> hasEnoughFreeDays        // não pode marcar dias off demais
            QuizStep.FOCUS -> true
            QuizStep.BODY -> true
            QuizStep.ENVIRONMENT -> environment != null      // obrigatório escolher
            QuizStep.ACCESSORIES -> true                      // pulável -- quem não marcar nada segue com []
            QuizStep.SPLIT -> true                            // null = recomendado (pré-selecionado)
            QuizStep.LIMITATIONS -> true
            QuizStep.HEALTH -> !health.hasAnyRisk || health.acknowledgedRisk   // gate: sem risco OU reconhecido
        }

    /**
     * O [Profile] (domínio) que o `submit()` manda pro repositório, ou `null` se um obrigatório
     * ainda falta -- extraído do `QuizViewModel` pra ser testável sem `viewModelScope` (C2 tier 2,
     * plano "acessórios no onboarding" §6: "o que foi marcado chega ao ProfileDto").
     *
     * É aqui que mora a regra da V66: `accessories` só viaja quando `environment == CASA` --
     * fora disso vira `null` ("nunca perguntado"), nunca `[]` ("perguntado, nada declarado").
     */
    fun toProfileOrNull(): Profile? {
        val g = goal; val l = levelDerivado; val d = daysPerWeek
        if (g == null || l == null || d == null) return null
        return Profile(
            goal = g,
            level = l,
            trainingConsistency = trainingConsistency,   // fatia 12
            timeOffTraining = timeOffTraining,             // fatia 12
            preferLightMode = preferLightMode,             // fatia 12 (resto)
            minutesAvailable = minutesAvailable,           // fatia 12 (resto)
            daysPerWeek = d,
            splitPreference = splitPreference,   // ARCH #29 (null = recomendado)
            unavailableDays = unavailableDays,   // Estágio 2: dias que não quer treinar
            focusAreas = if (focusEligible) focusAreas else emptyList(),   // iniciante/saúde geral não focam
            weightKg = weightKg,
            heightCm = heightCm,
            age = age,                       // #24
            minorSupervised = minorSupervised,   // #24
            environment = environment,
            accessories = if (environment == TrainingEnvironment.CASA) accessories else null,
            limitations = limitations,
            health = health,
            onboardingCompleted = false,   // server deriva
        )
    }
}