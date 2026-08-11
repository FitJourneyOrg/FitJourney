package dev.rafael.app.screens.session

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.rafael.app.ui.ShimmerContent
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutSessionScreen(
    workoutId: String,
    onDone: () -> Unit,
    viewModel: WorkoutSessionViewModel = koinViewModel { parametersOf(workoutId) },
) {
    val state by viewModel.state.collectAsState()

    // salvou (localmente ao menos) → volta
    LaunchedEffect(state.saved) { if (state.saved) onDone() }

    // descanso acabou → vibra (o usuário não fica olhando a tela entre séries)
    val context = LocalContext.current
    LaunchedEffect(state.restDoneTick) {
        if (state.restDoneTick > 0) vibrar(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.workoutName.ifBlank { "Treino" }) },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
            )
        },
        bottomBar = {
            if (!state.isLoading && state.error == null) {
                Surface(tonalElevation = 3.dp) {
                    Column {
                        state.restRemaining?.let { restante ->
                            RestTimerBar(
                                remaining = restante,
                                total = state.restTotal,
                                onSkip = { viewModel.onEvent(SessionEvent.SkipRest) },
                                onAdd30 = { viewModel.onEvent(SessionEvent.AddRest(30)) },
                            )
                        }
                        Button(
                            onClick = { viewModel.onEvent(SessionEvent.Finish) },
                            enabled = state.canFinish,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        ) {
                            if (state.isSaving) CircularProgressIndicator(Modifier.size(20.dp))
                            else Text("Finalizar treino")
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.isLoading -> ShimmerContent()
                state.error != null -> Text(state.error!!, Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.error)
                else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    itemsIndexed(state.entries) { i, e ->
                        val firstOfExercise = i == 0 || state.entries[i - 1].orderIndex != e.orderIndex
                        if (firstOfExercise) {
                            Spacer(Modifier.height(12.dp))
                            Text(e.exerciseName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                        SetRow(
                            entry = e,
                            onReps = { viewModel.onEvent(SessionEvent.RepsChanged(i, it)) },
                            onWeight = { viewModel.onEvent(SessionEvent.WeightChanged(i, it)) },
                            onToggle = { viewModel.onEvent(SessionEvent.ToggleDone(i)) },
                        )
                        HorizontalDivider()
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

/** Vibração curta ao fim do descanso. No-op se o aparelho não tiver vibrador. */
private fun vibrar(context: Context) {
    val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    runCatching {
        vibrator?.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}

/**
 * Barra de descanso — aparece ao marcar uma série e conta o `restSeconds` que o motor
 * prescreveu (150s composto pesado · 105s acessório · 75s isolamento, ARCH #26 §3.2).
 */
@Composable
private fun RestTimerBar(
    remaining: Int,
    total: Int,
    onSkip: () -> Unit,
    onAdd30: () -> Unit,
) {
    val progresso by animateFloatAsState(
        targetValue = if (total > 0) remaining / total.toFloat() else 0f,
        label = "rest-progress",
    )
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Timer, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("Descanso", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            Text(
                "%d:%02d".format(remaining / 60, remaining % 60),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(progress = { progresso }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onAdd30, modifier = Modifier.weight(1f)) { Text("+30s") }
            OutlinedButton(onClick = onSkip, modifier = Modifier.weight(1f)) { Text("Pular") }
        }
    }
}

@Composable
private fun SetRow(
    entry: SetEntry,
    onReps: (String) -> Unit,
    onWeight: (String) -> Unit,
    onToggle: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Série ${entry.setIndex + 1}", Modifier.width(64.dp), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = entry.repsDone,
            onValueChange = onReps,
            label = { Text("reps") },
            supportingText = { Text("alvo ${entry.targetReps}") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = entry.weight,
            onValueChange = onWeight,
            label = { Text("kg") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        Checkbox(checked = entry.done, onCheckedChange = { onToggle() })
    }
}
