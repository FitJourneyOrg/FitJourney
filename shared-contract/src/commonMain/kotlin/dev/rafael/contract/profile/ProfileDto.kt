package dev.rafael.contract.profile

import kotlinx.serialization.Serializable

/**
 * Perfil de onboarding. Obrigatórios: goal, level, daysPerWeek.
 * Opcionais (puláveis): focusAreas (0-2), weightKg, heightCm.
 * onboardingCompleted é derivado no server (true quando os obrigatórios estão preenchidos).
 *
 * **`level` deixou de ser a fonte (fatia 12, `docs/regras/16-onboarding-e-triagem.md`).** Quando
 * `trainingConsistency` E `timeOffTraining` vêm preenchidos, o servidor IGNORA o `level` enviado
 * e recalcula com [nivelDerivado] — `level` aqui passa a ser só o valor de volta (ou o fallback
 * de quem ainda não respondeu as duas perguntas novas: cliente antigo, onboarding pré-fatia-12).
 */
@Serializable
data class ProfileDto(
    val goal: Goal,
    val level: Level,
    val daysPerWeek: Int,
    val trainingConsistency: TrainingConsistency? = null,   // fatia 12
    val timeOffTraining: TimeOffTraining? = null,            // fatia 12
    val splitPreference: SplitType? = null,   // ARCH #29: escolha do quiz; null = usa o recomendado
    val unavailableDays: List<Int> = emptyList(),   // dias (1=Seg..7=Dom) que NÃO quer treinar; motor evita
    val focusAreas: List<MuscleGroup> = emptyList(),
    val weightKg: Double? = null,
    val heightCm: Double? = null,
    val age: Int? = null,                  // #24: idade (gate de menor)
    val minorSupervised: Boolean = false,  // #24: <18 declarou supervisão de responsável
    val environment: TrainingEnvironment? = null,
    // fatia 12: PAR-Q virou autoavaliação sem armazenamento -- o gate de
    // /programs/generate foi removido (ProgramGerarGate). Campo morto, mantido só pra
    // não quebrar cliente que ainda manda; ver débito "coluna health_screening morta".
    val health: HealthScreening? = null,
    val limitations: List<BodyLimitation> = emptyList(),
    // V66: o que o usuário tem além da carga. Só perguntado quando environment = CASA;
    // na academia o server assume os seis. null = nunca perguntado; lista vazia = perguntado e
    // nada declarado. Distinção vive de ponta a ponta (DTO -> Profile -> coluna nullable).
    val accessories: List<Accessory>? = null,
    val onboardingCompleted: Boolean = false,
)