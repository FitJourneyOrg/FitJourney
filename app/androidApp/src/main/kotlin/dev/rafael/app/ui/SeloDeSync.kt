package dev.rafael.app.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.rafael.app.R
import dev.rafael.features.program.domain.model.PendenciaDeSync

/**
 * Selo de sincronização (ARCH #30, B.4). Compartilhado entre ProgramListScreen (pendência do
 * PROGRAMA — rename/create) e ProgramDetailScreen (pendência do TREINO — ativação), já que
 * `PendenciaDeSync.alvoId` é "id do programa ou treino" (mesmo conceito, dois níveis).
 *
 * Dois estados, com pesos diferentes de propósito:
 *  - AGUARDANDO: discreto e NÃO clicável — é o caso normal do offline-first, some sozinho
 *    quando a rede volta, e dar uma ação aqui sugeriria que há algo a fazer quando não há.
 *  - FALHA PERMANENTE: em `error`, e clicável quando `onDescartar` é passado — abre o convite
 *    pra descartar (ver [DescartarPendenciaDialog]). Sem uma ação aqui, essa falha travava pra
 *    sempre: o outbox já parava de tentar (é o que "permanente" quer dizer), mas nada na tela
 *    convidava o usuário a reconhecer isso e seguir em frente (débito registrado 2026-09-27).
 *
 * [REGRA] Nada de `lime` aqui: a cor é exclusiva das recompensas do perfil individual (#16).
 */
@Composable
fun SeloDeSync(pendencia: PendenciaDeSync, onDescartar: (() -> Unit)? = null) {
    if (pendencia.aguardando) {
        AssistChip(
            onClick = {},
            enabled = false,
            label = {
                Text(stringResource(R.string.programa_lista_pendente), style = MaterialTheme.typography.labelSmall)
            },
        )
    } else {
        AssistChip(
            onClick = { onDescartar?.invoke() },
            enabled = onDescartar != null,
            label = {
                Text(
                    stringResource(R.string.programa_lista_nao_sincronizou),
                    style = MaterialTheme.typography.labelSmall,
                )
            },
            colors = AssistChipDefaults.assistChipColors(
                labelColor = MaterialTheme.colorScheme.error,
                disabledLabelColor = MaterialTheme.colorScheme.error,
            ),
        )
    }
}

/**
 * Convite pra descartar uma falha permanente do outbox (companheiro do [SeloDeSync]).
 *
 * Mostra a mensagem que o SERVIDOR devolveu (`erroPermanente`) — antes ela existia no domínio e
 * não era lida em lugar nenhum, então o usuário via "Didn't sync" sem nenhuma pista do porquê.
 */
@Composable
fun DescartarPendenciaDialog(mensagem: String?, onConfirmar: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pendencia_descartar_titulo)) },
        text = {
            Text(mensagem ?: stringResource(R.string.pendencia_descartar_corpo_sem_mensagem))
        },
        confirmButton = {
            TextButton(onClick = onConfirmar) { Text(stringResource(R.string.pendencia_descartar_confirmar)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.comum_cancelar)) }
        },
    )
}
