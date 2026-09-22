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
import dev.rafael.contract.i18n.Idioma
import dev.rafael.core.network.MediaUrls
import dev.rafael.features.exercise.domain.model.Exercise
import dev.rafael.features.exercise.presentation.descricao.paragrafosDaDescricao
import dev.rafael.features.exercise.presentation.viewmodel.ExerciseDetailViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
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
                title = {
                    Text(state.exercise?.name ?: stringResource(R.string.comum_exercicio))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.comum_voltar),
                        )
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
                    Text(stringResource(R.string.exercicio_nao_encontrado), Modifier.align(Alignment.Center))
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

/**
 * Renderiza a descrição em parágrafos (split \n\n). O parsing (o que é boilerplate a esconder,
 * o que é aviso a destacar) é lógica pura por idioma — mora em `paragrafosDaDescricao`
 * (shared/features/exercise/presentation), testada em commonTest. Este Composable só decide COMO
 * desenhar, nunca decide o QUE é aviso.
 *
 * O idioma vem do mesmo provedor que o `ExerciseRepositoryImpl` já usa pra pedir o catálogo
 * (`single<() -> Idioma>` no `AppModule`) — é o idioma em que o `desc` chegou do servidor, então
 * não corre o risco de aplicar o regex errado sobre o texto certo.
 *
 * A nota de segurança fixa (SEMPRE anexada, em TODOS os exercícios) é texto de app, não dado do
 * catálogo — por isso vive no `strings.xml` (values/values-en), como todo o resto da tela.
 */
@Composable
private fun ColumnScope.DescriptionBody(desc: String) {
    val idiomaAtual: () -> Idioma = koinInject()
    val paragrafos = paragrafosDaDescricao(desc, idiomaAtual())
    paragrafos.forEachIndexed { i, p ->
        if (p.destaque) NoteBox(p.texto) else Text(p.texto, style = MaterialTheme.typography.bodyMedium)
        if (i < paragrafos.lastIndex) Spacer(Modifier.height(10.dp))
    }
    // nota de segurança padrão — em TODOS os exercícios
    Spacer(Modifier.height(12.dp))
    NoteBox(stringResource(R.string.exercicio_aviso_seguranca))
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
