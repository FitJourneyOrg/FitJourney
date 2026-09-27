package dev.rafael.app.ui

import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
 *  - AGUARDANDO: discreto. É o caso normal do offline-first — some sozinho quando a rede
 *    volta, e alarmar o usuário sobre algo que se resolve sem ele seria ruído.
 *  - FALHA PERMANENTE: em `error`. O servidor recusou, ninguém vai tentar de novo, e sem
 *    destaque o usuário seguiria acreditando que salvou.
 *
 * [REGRA] Nada de `lime` aqui: a cor é exclusiva das recompensas do perfil individual (#16).
 */
@Composable
fun SeloDeSync(pendencia: PendenciaDeSync) {
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
            onClick = {},
            enabled = false,
            label = {
                Text(
                    stringResource(R.string.programa_lista_nao_sincronizou),
                    style = MaterialTheme.typography.labelSmall,
                )
            },
            colors = AssistChipDefaults.assistChipColors(
                disabledLabelColor = MaterialTheme.colorScheme.error,
            ),
        )
    }
}
