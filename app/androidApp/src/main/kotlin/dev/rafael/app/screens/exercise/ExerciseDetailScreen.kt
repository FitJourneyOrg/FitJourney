package dev.rafael.app.screens.exercise

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
// ⚠️ O import EXPLÍCITO do R é obrigatório neste arquivo, e não é preferência de estilo: os
// curingas do Compose (`material3.*`, `layout.*`) trazem os `R` das próprias bibliotecas para o
// escopo, e sem este import o `R.string` resolve para o `R` errado. O erro que isso dá não fala de
// import nenhum — diz "class R does not have a companion object", em cascata.
import dev.rafael.app.R
import dev.rafael.app.ui.ErroDeTela
import dev.rafael.app.ui.NetworkImage
import dev.rafael.app.ui.ShimmerLine
import dev.rafael.app.ui.rotulo
import dev.rafael.app.ui.rotuloDeEquipamento
import dev.rafael.app.ui.rotuloDePrescricao
import dev.rafael.app.ui.shimmer
import dev.rafael.core.network.MediaUrls
import dev.rafael.features.exercise.domain.model.Exercise
import dev.rafael.features.exercise.presentation.viewmodel.ExerciseDetailViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(
    exerciseId: String,
    onBack: () -> Unit,
    viewModel: ExerciseDetailViewModel = koinViewModel { parametersOf(exerciseId) },
) {
    val state by viewModel.state.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.exercise?.name ?: "Exercício") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading ->
                    ExerciseDetailSkeleton()
                // sem exercício E com erro: mostra a causa real (offline? servidor?).
                state.exercise == null && state.error != null ->
                    ErroDeTela(erro = state.error!!, modifier = Modifier.align(Alignment.Center))
                state.exercise == null ->
                    Text("Exercício não encontrado", Modifier.align(Alignment.Center))
                else ->
                    ExerciseDetailContent(state.exercise!!)
            }
        }
    }
}

@Composable
private fun ExerciseDetailSkeleton() {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).shimmer(RoundedCornerShape(16.dp)))
        Spacer(Modifier.height(16.dp))
        ShimmerLine(width = 220.dp, height = 26.dp)
        Spacer(Modifier.height(8.dp))
        ShimmerLine(width = 110.dp, height = 18.dp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseDetailContent(ex: Exercise) {
    val videoUrl = MediaUrls.url(ex.videoRef)
    val thumbUrl = MediaUrls.url(ex.thumbRef)
    val media = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp))

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        // Demonstração: mp4 em loop se houver; senão o PNG grande; senão placeholder.
        if (videoUrl != null) {
            ExerciseVideoLoop(videoUrl, media)
        } else {
            NetworkImage(url = thumbUrl, contentDescription = ex.name, modifier = media)
        }

        Spacer(Modifier.height(16.dp))
        Text(ex.name, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        AssistChip(onClick = {}, label = { Text(stringResource(ex.category.rotulo())) })

        // Seção: Sobre o exercício (parágrafos; "Aviso/Atenção/Importante" viram nota).
        ex.description?.takeIf { it.isNotBlank() }?.let { desc ->
            Section(stringResource(R.string.exercicio_secao_sobre)) {
                DescriptionBody(desc)
            }
        }

        // Seção: Músculos trabalhados (primários em chip; secundários em texto).
        if (ex.primaryMuscles.isNotEmpty()) {
            Section(stringResource(R.string.exercicio_secao_musculos)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ex.primaryMuscles.forEach { m ->
                        AssistChip(onClick = {}, label = { Text(stringResource(m.rotulo())) })
                    }
                }
                if (ex.secondaryMuscles.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    // Resolve os rótulos ANTES de juntar: `joinToString` não é inline, então a
                    // lambda de transformação dele não herda o escopo @Composable. `map` é inline
                    // e herda, por isso a ordem é map-e-depois-join, e não join-com-transform.
                    val secundarios = ex.secondaryMuscles
                        .map { stringResource(it.rotulo()) }
                        .joinToString()
                    Text(
                        stringResource(R.string.exercicio_musculos_secundarios, secundarios),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Seção: Equipamento.
        ex.equipment?.takeIf { it.isNotBlank() }?.let { bruto ->
            Section(stringResource(R.string.exercicio_secao_equipamento)) {
                // Valor fora do vocabulário conhecido cai no bruto prettificado: mostrar o que o
                // catálogo trouxe é melhor que mostrar vazio.
                val texto = rotuloDeEquipamento(bruto)?.let { stringResource(it) }
                    ?: bruto.lowercase().replaceFirstChar { it.uppercase() }
                Text(texto, style = MaterialTheme.typography.bodyMedium)
            }
        }

        // Seção: Como treinar (nível, prescrição, tipo, execução).
        val comoTreinar = buildList {
            ex.level?.let {
                add(stringResource(R.string.exercicio_info_nivel) to stringResource(it.rotulo()))
            }
            ex.prescriptionType?.let { bruto ->
                val v = rotuloDePrescricao(bruto)?.let { stringResource(it) } ?: bruto
                add(stringResource(R.string.exercicio_info_prescricao) to v)
            }
            ex.isCompound?.let {
                add(
                    stringResource(R.string.exercicio_info_tipo) to stringResource(
                        if (it) R.string.exercicio_tipo_composto else R.string.exercicio_tipo_isolamento,
                    ),
                )
            }
            ex.unilateral?.let {
                add(
                    stringResource(R.string.exercicio_info_execucao) to stringResource(
                        if (it) R.string.exercicio_execucao_unilateral else R.string.exercicio_execucao_bilateral,
                    ),
                )
            }
        }
        if (comoTreinar.isNotEmpty()) {
            Section(stringResource(R.string.exercicio_secao_como_treinar)) {
                comoTreinar.forEach { (k, v) -> InfoRow(k, v) }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Spacer(Modifier.height(20.dp))
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(8.dp))
    Column(content = content)
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

private val warnRegex = Regex("^\\s*(aviso|aten[cç][aã]o|importante)\\b", RegexOption.IGNORE_CASE)

// Boilerplate de "procure um profissional" que muitas descrições repetem — removido do texto
// pra não duplicar a nota padrão (SAFETY_NOTE) que mostramos em TODOS os exercícios.
private val safetyRegex = Regex(
    "profissional (de educa[çc][aã]o f[íi]sica|qualificado|de sa[úu]de)|" +
        "orienta[çc][aã]o de um profissional|antes de iniciar qualquer|acompanhamento profissional",
    RegexOption.IGNORE_CASE,
)
private const val SAFETY_NOTE =
    "Antes de iniciar qualquer programa de treino, procure a orientação de um profissional de " +
        "educação física para garantir a execução correta e evitar lesões."

/**
 * Renderiza a descrição em parágrafos (split \n\n). Tira o disclaimer de "profissional" que já
 * vem no texto (pra não duplicar) e SEMPRE anexa a nota de segurança padrão ao final.
 */
@Composable
private fun ColumnScope.DescriptionBody(desc: String) {
    val paras = desc.split("\n\n").map { it.trim() }
        .filter { it.isNotBlank() && !safetyRegex.containsMatchIn(it) }
    paras.forEachIndexed { i, para ->
        if (warnRegex.containsMatchIn(para)) NoteBox(para) else Text(para, style = MaterialTheme.typography.bodyMedium)
        if (i < paras.lastIndex) Spacer(Modifier.height(10.dp))
    }
    // nota de segurança padrão — em TODOS os exercícios
    Spacer(Modifier.height(12.dp))
    NoteBox(SAFETY_NOTE)
}

@Composable
private fun NoteBox(text: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Outlined.Info, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// Os rótulos de enum saíram daqui na G.3 e vivem em `ui/Rotulos.kt`, um lugar só.
//
// Estavam duplicados com o `QuizSteps` ("Peito", "Iniciante"), e duplicata de texto sobrevive num
// idioma e diverge no segundo: o tradutor recebe a mesma palavra duas vezes, em telas diferentes
// do catálogo, e não tem como saber que precisam combinar.
//
// O `categoryLabel` não foi movido, foi CORRIGIDO: ele derivava o rótulo de `enum.name` e mostrava
// "Chest" e "Lower back" para o usuário brasileiro.
