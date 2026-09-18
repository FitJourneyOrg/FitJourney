package dev.rafael.app.ui

import androidx.annotation.StringRes
import dev.rafael.app.R
import dev.rafael.contract.checkin.CheckInStatus
import dev.rafael.contract.exercise.ExerciseCategory
import dev.rafael.contract.i18n.Idioma
import dev.rafael.contract.group.GroupRule
import dev.rafael.contract.group.GroupState
import dev.rafael.contract.group.JoinBlock
import dev.rafael.contract.profile.BodyLimitation
import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.MuscleGroup
import dev.rafael.contract.profile.SplitType
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

// ---------------------------------------------------------------------------
// GRUPOS E DESAFIOS (fatia G.3, área 2)
// ---------------------------------------------------------------------------

/**
 * ⚠️ **Corrige o SEGUNDO defeito de rótulo-derivado-do-`name` desta fatia.**
 *
 * `GroupRule` estava escrito de três jeitos, em três telas:
 *
 * | Tela | Antes |
 * |---|---|
 * | Entrar (preview do convite) | `"Uma foto, tirada na hora pelo app"` |
 * | Criar desafio (chip) | `"Foto"` |
 * | Detalhe ("Check-in exige") | `name.lowercase().replace('_',' ')` → **`foto, localizacao`** |
 *
 * O terceiro não era tradução ruim, era **texto quebrado**: sem cedilha em "localizacao", tudo em
 * minúscula, e `GYM_PASS` sairia como `gym pass`. O mesmo erro que o `ExerciseCategory` tinha, pela
 * mesma causa — derivar rótulo do identificador **parece** que resolve enquanto ninguém lê.
 *
 * > **Rótulo derivado do nome do enum não é rótulo, é o identificador com maiúscula.**
 *
 * [rotulo] é o curto (chip, linha de resumo); [descricao] é a frase do preview, onde o opt-in de
 * localização acontece (#17) e a pessoa precisa saber o que vai ser pedido. Mesmo par do
 * [TrainingEnvironment].
 */
@StringRes
fun GroupRule.rotulo(): Int = when (this) {
    GroupRule.FOTO -> R.string.enum_regra_foto
    GroupRule.LOCALIZACAO -> R.string.enum_regra_localizacao
    GroupRule.EMOJI_DO_DIA -> R.string.enum_regra_emoji_do_dia
    GroupRule.GYM_PASS -> R.string.enum_regra_gym_pass
}

/** A mesma exigência por extenso, para o preview do convite. Ver [rotulo]. */
@StringRes
fun GroupRule.descricao(): Int = when (this) {
    GroupRule.FOTO -> R.string.enum_regra_foto_descricao
    GroupRule.LOCALIZACAO -> R.string.enum_regra_localizacao_descricao
    GroupRule.EMOJI_DO_DIA -> R.string.enum_regra_emoji_do_dia_descricao
    GroupRule.GYM_PASS -> R.string.enum_regra_gym_pass_descricao
}

/**
 * Em que fase o desafio está. **Vem RESOLVIDO do servidor** — o cliente não recalcula com o
 * próprio relógio, e por isso isto é só rótulo.
 *
 * O curto é o selo do cartão da lista; [rotuloDetalhado] é o do topo do desafio, onde cabe dizer
 * o que a fase significa para quem quer entrar. Duas chaves e não um sufixo concatenado: "entrada
 * aberta" não é um pedaço que se cole em qualquer idioma.
 */
@StringRes
fun GroupState.rotulo(): Int = when (this) {
    GroupState.AGENDADO -> R.string.enum_estado_agendado
    GroupState.ATIVO -> R.string.enum_estado_ativo
    GroupState.ENCERRADO -> R.string.enum_estado_encerrado
}

/** O estado com a consequência junto, para o detalhe do desafio. Ver [rotulo]. */
@StringRes
fun GroupState.rotuloDetalhado(): Int = when (this) {
    GroupState.AGENDADO -> R.string.enum_estado_agendado_detalhe
    GroupState.ATIVO -> R.string.enum_estado_ativo_detalhe
    // Encerrado não ganha segunda metade: não há entrada para explicar, o desafio acabou.
    GroupState.ENCERRADO -> R.string.enum_estado_encerrado
}

/**
 * Por que o botão de entrar está desabilitado.
 *
 * O motivo vem do servidor como enum e a frase é escolhida aqui: **botão desabilitado sem
 * explicação é um mistério**, e quem conhece a plataforma escreve o texto (#31).
 */
@StringRes
fun JoinBlock.frase(): Int = when (this) {
    JoinBlock.JA_COMECOU -> R.string.enum_bloqueio_ja_comecou
    JoinBlock.ENCERRADO -> R.string.enum_bloqueio_encerrado
    JoinBlock.LOTADO -> R.string.enum_bloqueio_lotado
    JoinBlock.JA_E_MEMBRO -> R.string.enum_bloqueio_ja_e_membro
    JoinBlock.CONVITE_INVALIDO -> R.string.enum_bloqueio_convite_invalido
}

/**
 * A faixa de moderação sobre o check-in no feed.
 *
 * `VALIDO` devolve `null` porque **a ausência da faixa é o estado normal** — dar-lhe um rótulo
 * criaria uma faixa "tudo certo" em cima de todo card do feed.
 */
@StringRes
fun CheckInStatus.rotulo(): Int? = when (this) {
    CheckInStatus.VALIDO -> null
    CheckInStatus.EM_ANALISE -> R.string.enum_checkin_em_analise
    CheckInStatus.INVALIDADO -> R.string.enum_checkin_invalidado
}

// ---------------------------------------------------------------------------
// ONBOARDING (fatia G.3, área 3)
// ---------------------------------------------------------------------------

/**
 * O modelo de divisão de treino (ARCH #29).
 *
 * ## ⚠️ Por que isto NÃO reaproveita `SplitType.label`
 *
 * O enum do contrato tem `label` e `description`, e o KDoc dele ainda diz "texto de UI". Não são
 * mais: o **servidor** os usa como dado — `StructureEngine` grava `split.label` no
 * `ProgramSkeleton` e o costura dentro do `rationale`, e `WeekSpread` compara
 * `split == SplitType.FULL_BODY.label`, ou seja, **usa o rótulo como identificador**.
 *
 * Mexer neles para traduzir a tela quebraria a geração de programa. Então o cliente passa a ler o
 * catálogo, e o contrato guarda o que é do servidor. As duas frases coincidem hoje em pt-BR e vão
 * divergir quando o inglês entrar — o que é correto: uma é texto para o usuário, a outra é chave
 * interna do motor.
 *
 * > **Campo que o servidor compara não é rótulo, mesmo que esteja escrito em português.**
 *
 * A saída de verdade é o `rationale` deixar de ser frase pronta do servidor e virar código +
 * parâmetros, como a G.2 fez com os erros. Está registrado como débito no handoff; é decisão do
 * Rafael, e ela é maior do que esta fatia.
 *
 * Os NOMES não se traduzem — "Push/Pull/Legs" e "Arnold" são o jargão de academia em qualquer
 * idioma. A [descricao] traduz.
 */
@StringRes
fun SplitType.rotulo(): Int = when (this) {
    SplitType.FULL_BODY -> R.string.enum_split_full_body
    SplitType.UPPER_LOWER -> R.string.enum_split_upper_lower
    SplitType.UPPER_LOWER_FULL -> R.string.enum_split_upper_lower_full
    SplitType.PUSH_PULL_LEGS -> R.string.enum_split_push_pull_legs
    SplitType.UL_PPL -> R.string.enum_split_ul_ppl
    SplitType.ARNOLD -> R.string.enum_split_arnold
}

/** A frase que explica o split, abaixo do nome. Ver [rotulo]. */
@StringRes
fun SplitType.descricao(): Int = when (this) {
    SplitType.FULL_BODY -> R.string.enum_split_full_body_descricao
    SplitType.UPPER_LOWER -> R.string.enum_split_upper_lower_descricao
    SplitType.UPPER_LOWER_FULL -> R.string.enum_split_upper_lower_full_descricao
    SplitType.PUSH_PULL_LEGS -> R.string.enum_split_push_pull_legs_descricao
    SplitType.UL_PPL -> R.string.enum_split_ul_ppl_descricao
    SplitType.ARNOLD -> R.string.enum_split_arnold_descricao
}

/**
 * A região que o gerador deve poupar.
 *
 * `IMPACT` não é uma parte do corpo e sim um tipo de movimento, e por isso o rótulo dele é uma
 * frase inteira em vez de uma palavra. Enum cujo último valor foge do padrão dos outros é comum, e
 * forçar "Impacto" só para os cinco ficarem simétricos diria menos do que a frase.
 */
@StringRes
fun BodyLimitation.rotulo(): Int = when (this) {
    BodyLimitation.SHOULDER -> R.string.enum_limitacao_shoulder
    BodyLimitation.KNEE -> R.string.enum_limitacao_knee
    BodyLimitation.LUMBAR -> R.string.enum_limitacao_lumbar
    BodyLimitation.WRIST -> R.string.enum_limitacao_wrist
    BodyLimitation.IMPACT -> R.string.enum_limitacao_impact
}

/**
 * Os sete dias da semana, abreviados para caber no chip do passo de descanso.
 *
 * ⚠️ Recebe o número **ISO 8601** (segunda = 1), que é o que vai para o servidor. A ordem em que
 * a tela desenha os chips é a mesma sequência 1..7, e isso é **contrato, não apresentação** — em
 * países onde a semana começa no domingo a ordem ficaria estranha, e isso é débito conhecido, não
 * descuido. Trocar a ordem sem desacoplar o índice do número quebraria o que é enviado.
 */
@StringRes
fun rotuloDoDiaDaSemana(isoDay: Int): Int = ROTULOS_DOS_DIAS[isoDay - 1]

/**
 * Segunda a domingo, na ordem ISO. Lista e não `when`: o índice É o número do dia, e um `when`
 * com sete ramos precisaria de um `else` que só poderia lançar — mais código para dizer o mesmo,
 * e uma frase de erro a mais no arquivo. Fora da faixa, o próprio índice lança.
 */
private val ROTULOS_DOS_DIAS = intArrayOf(
    R.string.quiz_dia_seg,
    R.string.quiz_dia_ter,
    R.string.quiz_dia_qua,
    R.string.quiz_dia_qui,
    R.string.quiz_dia_sex,
    R.string.quiz_dia_sab,
    R.string.quiz_dia_dom,
)

/**
 * O nome de um idioma, na PRÓPRIA língua (fatia G.4, ARCH #37).
 *
 * ⚠️ **Estes dois rótulos são os únicos do arquivo que NÃO se traduzem.** `idioma_pt_br` é
 * *Português (Brasil)* e `idioma_en` é *English* nos dois catálogos, idênticos, e o
 * `CatalogoEmInglesTest` fixa os dois na lista de chaves iguais para que ninguém os "conserte".
 *
 * Quem precisa da tela de idioma é justamente quem abriu o app numa língua que não lê, e essa
 * pessoa reconhece *English*, não *Inglês*.
 *
 * > **Traduzir a lista de idiomas torna ilegível a única tela que existe para sair de um idioma**
 * > **ilegível.**
 */
@StringRes
fun Idioma.rotulo(): Int = when (this) {
    Idioma.PT_BR -> R.string.idioma_pt_br
    Idioma.EN -> R.string.idioma_en
}
