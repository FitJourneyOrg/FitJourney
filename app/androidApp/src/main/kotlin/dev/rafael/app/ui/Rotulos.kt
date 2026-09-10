package dev.rafael.app.ui

import androidx.annotation.StringRes
import dev.rafael.app.R
import dev.rafael.contract.exercise.ExerciseCategory
import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.MuscleGroup
import dev.rafael.contract.profile.TrainingEnvironment

/**
 * O rótulo de cada valor de enum, num lugar só (fatia G.3, ARCH #37).
 *
 * ## Por que centralizar, se cada tela já sabia escrever o seu
 *
 * Porque **não sabia**: `MuscleGroup.CHEST -> "Peito"` e `Level.BEGINNER -> "Iniciante"` estavam
 * duplicados em `ExerciseDetailScreen` e `QuizSteps`. Duas cópias da mesma tradução é uma cópia a
 * mais do que precisa divergir.
 *
 * Com um idioma, duplicar custa pouco: as duas cópias nasceram iguais e ninguém mexeu. Com dois,
 * o tradutor recebe *Peito* duas vezes, em telas diferentes do catálogo, e pode devolver *Chest*
 * numa e *Pecs* na outra. Ninguém compara.
 *
 * > **Duplicata de texto sobrevive num idioma e diverge no segundo.**
 *
 * São ~45 termos que se traduzem UMA vez e valem em toda tela que os usa.
 *
 * ## Devolve `@StringRes Int`, não `String`
 *
 * Assim estas funções não precisam ser `@Composable` nem receber `Context`, e continuam sendo
 * Kotlin puro chamável de qualquer lugar. Quem resolve é a tela, com `stringResource(...)`.
 *
 * De quebra ficam testáveis: um teste de unidade compara ids sem precisar de Android.
 */

@StringRes
fun MuscleGroup.rotulo(): Int = when (this) {
    MuscleGroup.CHEST -> R.string.enum_musculo_chest
    MuscleGroup.BACK -> R.string.enum_musculo_back
    MuscleGroup.BICEPS -> R.string.enum_musculo_biceps
    MuscleGroup.TRICEPS -> R.string.enum_musculo_triceps
    MuscleGroup.FOREARMS -> R.string.enum_musculo_forearms
    MuscleGroup.SHOULDERS -> R.string.enum_musculo_shoulders
    MuscleGroup.LEGS -> R.string.enum_musculo_legs
    MuscleGroup.GLUTES -> R.string.enum_musculo_glutes
    MuscleGroup.CORE -> R.string.enum_musculo_core
}

@StringRes
fun Level.rotulo(): Int = when (this) {
    Level.BEGINNER -> R.string.enum_nivel_beginner
    Level.INTERMEDIATE -> R.string.enum_nivel_intermediate
    Level.ADVANCED -> R.string.enum_nivel_advanced
}

@StringRes
fun Goal.rotulo(): Int = when (this) {
    Goal.GAIN_MUSCLE -> R.string.enum_objetivo_gain_muscle
    Goal.LOSE_FAT -> R.string.enum_objetivo_lose_fat
    Goal.MAINTAIN -> R.string.enum_objetivo_maintain
    Goal.GENERAL_HEALTH -> R.string.enum_objetivo_general_health
}

/** O nome curto do ambiente. A frase com exemplos de equipamento é [descricao]. */
@StringRes
fun TrainingEnvironment.rotulo(): Int = when (this) {
    TrainingEnvironment.ACADEMIA -> R.string.enum_ambiente_academia
    TrainingEnvironment.CASA -> R.string.enum_ambiente_casa
}

/**
 * A frase de escolha do quiz, com exemplos do que existe em cada ambiente.
 *
 * Separada do [rotulo] porque são coisas diferentes: o rótulo aparece em chip e resumo, a descrição
 * só no quiz. Uma string servindo aos dois lugares acabaria longa demais para um e curta demais
 * para o outro.
 */
@StringRes
fun TrainingEnvironment.descricao(): Int = when (this) {
    TrainingEnvironment.ACADEMIA -> R.string.enum_ambiente_academia_descricao
    TrainingEnvironment.CASA -> R.string.enum_ambiente_casa_descricao
}

/**
 * ⚠️ **Corrige um defeito que existia antes desta fatia.**
 *
 * A versão anterior era `c.name.lowercase().replaceFirstChar { it.uppercase() }`, ou seja, mostrava
 * o nome do enum prettificado: o chip da tela de exercício exibia **"Chest"**, **"Back"** e
 * **"Lower back"** para o usuário brasileiro.
 *
 * Não era problema de tradução, era texto em inglês na tela em português. Apareceu porque a
 * extração obriga a olhar cada string, e derivar rótulo do `name` **parece** que resolve enquanto
 * ninguém lê.
 *
 * > **Rótulo derivado do nome do enum não é rótulo, é o identificador com maiúscula.**
 */
@StringRes
fun ExerciseCategory.rotulo(): Int = when (this) {
    ExerciseCategory.CORE -> R.string.enum_categoria_core
    ExerciseCategory.FUNCTIONAL_HIT -> R.string.enum_categoria_functional_hit
    ExerciseCategory.MOBILITY -> R.string.enum_categoria_mobility
    ExerciseCategory.CALISTHENICS -> R.string.enum_categoria_calisthenics
    ExerciseCategory.LEGS -> R.string.enum_categoria_legs
    ExerciseCategory.SHOULDERS -> R.string.enum_categoria_shoulders
    ExerciseCategory.CHEST -> R.string.enum_categoria_chest
    ExerciseCategory.BACK -> R.string.enum_categoria_back
    ExerciseCategory.CROSSFIT -> R.string.enum_categoria_crossfit
    ExerciseCategory.BICEPS -> R.string.enum_categoria_biceps
    ExerciseCategory.TRICEPS -> R.string.enum_categoria_triceps
    ExerciseCategory.TRAPEZIUS -> R.string.enum_categoria_trapezius
    ExerciseCategory.GLUTES -> R.string.enum_categoria_glutes
    ExerciseCategory.CALVES -> R.string.enum_categoria_calves
    ExerciseCategory.FOREARMS -> R.string.enum_categoria_forearms
    ExerciseCategory.CARDIO -> R.string.enum_categoria_cardio
    ExerciseCategory.LOWER_BACK -> R.string.enum_categoria_lower_back
}

/**
 * Equipamento e prescrição vêm do banco como **`String`**, não como enum.
 *
 * Por isso estas duas não são exaustivas e precisam de fallback: o catálogo pode conter um valor
 * que o app não conhece, e nesse caso mostrar o valor cru é melhor que mostrar vazio.
 *
 * `null` significa "não tenho rótulo para isto", e quem chama decide o que fazer. Devolver um
 * `@StringRes` genérico esconderia o caso e faria todo equipamento desconhecido virar a mesma
 * palavra.
 */
@StringRes
fun rotuloDeEquipamento(bruto: String): Int? = when (bruto.uppercase()) {
    "BARBELL" -> R.string.enum_equipamento_barbell
    "DUMBBELL" -> R.string.enum_equipamento_dumbbell
    "MACHINE" -> R.string.enum_equipamento_machine
    "CABLE" -> R.string.enum_equipamento_cable
    "BODYWEIGHT" -> R.string.enum_equipamento_bodyweight
    "KETTLEBELL" -> R.string.enum_equipamento_kettlebell
    "BAND", "RESISTANCE_BAND", "ELASTIC" -> R.string.enum_equipamento_band
    "SMITH" -> R.string.enum_equipamento_smith
    "EZ_BAR" -> R.string.enum_equipamento_ez_bar
    "PLATE" -> R.string.enum_equipamento_plate
    "MEDICINE_BALL" -> R.string.enum_equipamento_medicine_ball
    "STABILITY_BALL" -> R.string.enum_equipamento_stability_ball
    "BOSU" -> R.string.enum_equipamento_bosu
    "SUSPENSION" -> R.string.enum_equipamento_suspension
    "ROPE" -> R.string.enum_equipamento_rope
    "AGILITY_LADDER" -> R.string.enum_equipamento_agility_ladder
    "NONE", "" -> R.string.enum_equipamento_nenhum
    else -> null
}

@StringRes
fun rotuloDePrescricao(bruto: String): Int? = when (bruto.uppercase()) {
    "REPS" -> R.string.enum_prescricao_reps
    "TIME" -> R.string.enum_prescricao_time
    else -> null
}
