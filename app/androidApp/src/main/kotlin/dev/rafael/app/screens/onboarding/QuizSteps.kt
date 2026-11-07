package dev.rafael.app.screens.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.rafael.app.R
import dev.rafael.app.ui.descricao
import dev.rafael.app.ui.rotulo
import dev.rafael.app.ui.rotuloDoDiaDaSemana
import dev.rafael.contract.profile.Accessory
import dev.rafael.contract.profile.BodyLimitation
import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.HealthScreening
import dev.rafael.contract.profile.MinutesAvailable
import dev.rafael.contract.profile.MuscleGroup
import dev.rafael.contract.profile.TimeOffTraining
import dev.rafael.contract.profile.TrainingConsistency
import dev.rafael.contract.profile.TrainingEnvironment
import dev.rafael.contract.profile.SplitCatalog
import dev.rafael.contract.profile.SplitType
import dev.rafael.features.profile.presentation.state.QuizEvent

/**
 * O teto de grupos de foco (#26). Espelha o `FOCO_ALEM_DO_LIMITE` do servidor, que é quem recusa.
 *
 * Existe como constante porque o número aparece no contador da tela: cravá-lo dentro da frase
 * faria a tradução carregar uma regra de negócio, e mudá-la exigiria mexer em todo idioma.
 */
private const val MAX_GRUPOS_DE_FOCO = 2

/** Sete. Nomeado porque `7 - selected.size` não diz de que sete se trata. */
private const val DIAS_DA_SEMANA = 7

@Composable
fun SplitStep(daysPerWeek: Int, selected: SplitType?, onSelect: (SplitType) -> Unit) {
    val options = SplitCatalog.optionsFor(daysPerWeek)
    // recomendado pré-selecionado; se o usuário não tocar, fica null e o server usa o recomendado.
    val effective = selected ?: SplitCatalog.recommendedFor(daysPerWeek)
    Column {
        Text(stringResource(R.string.quiz_split_titulo), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.quiz_split_ajuda, daysPerWeek),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        options.forEach { opt ->
            Row(
                Modifier.fillMaxWidth().clickable { onSelect(opt.type) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = effective == opt.type, onClick = { onSelect(opt.type) })
                Spacer(Modifier.width(8.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // NÃO `opt.type.name`: aquela é a CHAVE que o `StructureEngine`
                        // compara e persiste, não rótulo de UI. Ver `SplitType.rotulo()` em
                        // ui/Rotulos.kt.
                        Text(stringResource(opt.type.rotulo()), style = MaterialTheme.typography.titleMedium)
                        if (opt.recommended) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.quiz_split_recomendado),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Text(stringResource(opt.type.descricao()), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun GoalStep(selected: Goal?, onSelect: (Goal) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.quiz_objetivo_titulo), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Goal.entries.forEach { g ->
            FilterChip(
                selected = selected == g,
                onClick = { onSelect(g) },
                label = { Text(stringResource(g.rotulo())) },
            )
        }
    }
}

/**
 * As duas perguntas novas que substituem a escolha livre de nível (fatia 12 -- resto, cliente):
 * o `QuizState.levelDerivado` combina as duas pelo `nivelDerivado()` compartilhado. Radio-list,
 * mesmo estilo do [EnvironmentStep] -- é pergunta de única escolha, não `FilterChip` múltiplo.
 */
@Composable
fun TrainingConsistencyStep(selected: TrainingConsistency?, onSelect: (TrainingConsistency) -> Unit) {
    Column {
        Text(stringResource(R.string.quiz_consistencia_titulo), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        TrainingConsistency.entries.forEach { c ->
            Row(
                Modifier.fillMaxWidth().clickable { onSelect(c) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected == c, onClick = { onSelect(c) })
                Spacer(Modifier.width(8.dp))
                Text(stringResource(c.rotulo()))
            }
        }
    }
}

@Composable
fun TimeOffTrainingStep(selected: TimeOffTraining?, onSelect: (TimeOffTraining) -> Unit) {
    Column {
        Text(stringResource(R.string.quiz_parado_titulo), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        TimeOffTraining.entries.forEach { t ->
            Row(
                Modifier.fillMaxWidth().clickable { onSelect(t) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected == t, onClick = { onSelect(t) })
                Spacer(Modifier.width(8.dp))
                Text(stringResource(t.rotulo()))
            }
        }
    }
}

@Composable
fun AgeStep(
    age: Int?,
    minorSupervised: Boolean,
    onAge: (Int?) -> Unit,
    onToggleSupervised: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.quiz_idade_titulo), style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = age?.toString() ?: "",
            onValueChange = { onAge(it.filter(Char::isDigit).take(3).toIntOrNull()) },
            label = { Text(stringResource(R.string.quiz_idade_campo)) },
            singleLine = true,
            isError = age != null && age !in 5..120,
        )
        if (age != null && age !in 5..120) {
            Text(
                stringResource(R.string.quiz_idade_invalida),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        if (age != null && age in 5..17) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = minorSupervised, onCheckedChange = { onToggleSupervised() })
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.quiz_idade_supervisao),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (age != null && age in 69..120) {
            Text(
                stringResource(R.string.quiz_idade_aviso_idoso),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun DaysStep(selected: Int?, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.quiz_dias_titulo), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (2..6).forEach { d ->
                FilterChip(selected = selected == d, onClick = { onSelect(d) }, label = { Text("$d") })
            }
        }
    }
}

/**
 * "Quer começar no modo leve?" (fatia 12 -- resto, especificação 3ª rodada). Pergunta de
 * PREFERÊNCIA, não condição -- por isso sim/não em vez de checklist, e por isso o passo nunca
 * trava o avanço (`canAdvance` sempre `true`): quem não toca segue com "não" marcado.
 */
@Composable
fun LightModeStep(selected: Boolean, onSelect: (Boolean) -> Unit) {
    Column {
        Text(stringResource(R.string.quiz_modo_leve_titulo), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.quiz_modo_leve_ajuda),
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(16.dp))
        listOf(true to R.string.quiz_modo_leve_sim, false to R.string.quiz_modo_leve_nao).forEach { (valor, rotulo) ->
            Row(
                Modifier.fillMaxWidth().clickable { onSelect(valor) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected == valor, onClick = { onSelect(valor) })
                Spacer(Modifier.width(8.dp))
                Text(stringResource(rotulo))
            }
        }
    }
}

/** Minutos disponíveis por sessão (fatia 12 -- resto). Radio-list, mesmo estilo das demais
 * perguntas de única escolha desta fatia. */
@Composable
fun MinutesAvailableStep(selected: MinutesAvailable?, onSelect: (MinutesAvailable) -> Unit) {
    Column {
        Text(stringResource(R.string.quiz_minutos_titulo), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        MinutesAvailable.entries.forEach { m ->
            Row(
                Modifier.fillMaxWidth().clickable { onSelect(m) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected == m, onClick = { onSelect(m) })
                Spacer(Modifier.width(8.dp))
                Text(stringResource(m.rotulo()))
            }
        }
    }
}

@Composable
fun FocusStep(selected: List<MuscleGroup>, onToggle: (MuscleGroup) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.quiz_foco_titulo), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.quiz_foco_ajuda), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        // ⚠️ Estes rótulos estavam DUPLICADOS aqui e no `ExerciseDetailScreen`, e já tinham
        // divergido: "Antebraço" aqui, "Antebraços" lá. É exatamente o que a `Rotulos.kt` existe
        // para impedir, e a prova de que uma cópia sobrevive num idioma e diverge no segundo.
        // CORE fica de fora: não tem alvo de volume, então escolher abdômen como foco não faria nada.
        MuscleGroup.entries.filter { it.temAlvoDeVolume }.forEach { m ->
            FilterChip(
                selected = m in selected,
                onClick = { onToggle(m) },
                label = { Text(stringResource(m.rotulo())) },
            )
        }
        Text(
            pluralStringResource(
                R.plurals.quiz_foco_contador,
                selected.size,
                selected.size,
                MAX_GRUPOS_DE_FOCO,
            ),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/** Estágio 2: dias que o usuário NÃO quer treinar (descanso). A IA evita esses na distribuição. */
@Composable
fun RestDaysStep(selected: List<Int>, daysPerWeek: Int, onToggle: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.quiz_descanso_titulo), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.quiz_descanso_ajuda), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        // O índice ISO (segunda = 1) é o que vai para o servidor, e por isso ele — e não uma
        // lista de rótulos — é a fonte da iteração. Ver `rotuloDoDiaDaSemana`.
        (1..7).forEach { d ->
            FilterChip(
                selected = d in selected,
                onClick = { onToggle(d) },
                label = { Text(stringResource(rotuloDoDiaDaSemana(d))) },
            )
        }
        val free = DIAS_DA_SEMANA - selected.size
        if (free < daysPerWeek) {
            Text(
                // A concordância segue o PRIMEIRO número, que é o que a frase exige.
                pluralStringResource(R.plurals.quiz_descanso_insuficiente, daysPerWeek, daysPerWeek, free),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
            )
        } else {
            Text(
                pluralStringResource(R.plurals.quiz_descanso_livres, free, free),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
fun BodyStep(
    weight: Double?,
    height: Double?,
    onWeight: (Double?) -> Unit,
    onHeight: (Double?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.quiz_corpo_titulo), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.quiz_corpo_ajuda), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = weight?.toString() ?: "",
            onValueChange = { onWeight(it.toDoubleOrNull()) },
            label = { Text(stringResource(R.string.quiz_corpo_peso)) },
            singleLine = true,
        )
        OutlinedTextField(
            value = height?.toString() ?: "",
            onValueChange = { onHeight(it.toDoubleOrNull()) },
            label = { Text(stringResource(R.string.quiz_corpo_altura)) },
            singleLine = true,
        )
    }
}

/**
 * [REGRA] (resto da Fatia 12): CASA sai desabilitada aqui -- "lançar só com academia; a lista
 * de equipamentos é condição de lançamento apenas para o treino em casa" (especificação). É
 * só de UI: não desliga quem já tem `environment = CASA` salvo (1 usuário real, medido em
 * 2026-11-07) -- o campo continua existindo no contrato/servidor sem gate nenhum, só esta tela
 * de onboarding não oferece a opção pra quem ainda não escolheu.
 */
private val AMBIENTES_DISPONIVEIS = setOf(TrainingEnvironment.ACADEMIA)

@Composable
fun EnvironmentStep(
    selected: TrainingEnvironment?,
    onSelect: (TrainingEnvironment) -> Unit,
) {
    Column {
        Text(stringResource(R.string.quiz_ambiente_titulo), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        TrainingEnvironment.entries.forEach { env ->
            val disponivel = env in AMBIENTES_DISPONIVEIS
            val cor = if (disponivel) {
                Color.Unspecified
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            }
            Row(
                Modifier.fillMaxWidth()
                    .clickable(enabled = disponivel) { onSelect(env) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected == env, onClick = { onSelect(env) }, enabled = disponivel)
                Spacer(Modifier.width(8.dp))
                // `env.name` como fallback era rótulo derivado do identificador — o defeito que
                // esta fatia já achou duas vezes. Agora o catálogo é exaustivo e não há fallback.
                Text(stringResource(env.descricao()), color = cor)
                if (!disponivel) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.quiz_ambiente_em_breve),
                        style = MaterialTheme.typography.labelSmall,
                        color = cor,
                    )
                }
            }
        }
    }
}

@Composable
fun AccessoriesStep(selected: List<Accessory>, onToggle: (Accessory) -> Unit) {
    Column {
        Text(stringResource(R.string.quiz_acessorios_titulo), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.quiz_acessorios_ajuda),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        Accessory.entries.forEach { acc ->
            Row(
                Modifier.fillMaxWidth().clickable { onToggle(acc) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = acc in selected, onCheckedChange = { onToggle(acc) })
                Spacer(Modifier.width(8.dp))
                Text(stringResource(acc.rotulo()))
            }
        }
    }
}

@Composable
fun HealthStep(
    health: HealthScreening,
    onToggle: (QuizEvent.HealthField) -> Unit,
    onAck: () -> Unit,
) {
    Column {
        Text(stringResource(R.string.quiz_saude_titulo), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.quiz_saude_ajuda),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))

        HealthSwitch(R.string.quiz_saude_cardiaca, health.hasCardiacCondition) {
            onToggle(QuizEvent.HealthField.CARDIAC)
        }
        HealthSwitch(R.string.quiz_saude_dor_no_peito, health.hasChestPainDuringActivity) {
            onToggle(QuizEvent.HealthField.CHEST_PAIN)
        }
        HealthSwitch(R.string.quiz_saude_articulacao, health.hasJointOrBoneIssue) {
            onToggle(QuizEvent.HealthField.JOINT)
        }
        HealthSwitch(R.string.quiz_saude_medicacao, health.takesContinuousMedication) {
            onToggle(QuizEvent.HealthField.MEDICATION)
        }

        if (health.hasAnyRisk) {
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = health.acknowledgedRisk, onCheckedChange = { onAck() })
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.quiz_saude_ciente),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
fun LimitationsStep(selected: List<BodyLimitation>, onToggle: (BodyLimitation) -> Unit) {
    Column {
        Text(stringResource(R.string.quiz_limitacoes_titulo), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.quiz_limitacoes_ajuda),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        BodyLimitation.entries.forEach { lim ->
            Row(
                Modifier.fillMaxWidth().clickable { onToggle(lim) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = lim in selected, onCheckedChange = { onToggle(lim) })
                Spacer(Modifier.width(8.dp))
                Text(stringResource(lim.rotulo()))
            }
        }
    }
}


@Composable
private fun HealthSwitch(
    @androidx.annotation.StringRes label: Int,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(label), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}