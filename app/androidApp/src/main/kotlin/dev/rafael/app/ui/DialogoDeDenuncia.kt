package dev.rafael.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.rafael.app.R
import dev.rafael.core.result.AppError

/** Espelha o `ModeracaoPolicy.MAX_MOTIVO`. Divergir faria o servidor cortar o texto em silêncio. */
const val MAX_MOTIVO_DA_DENUNCIA = 300

/**
 * O diálogo de denúncia (fatia E.2, 6.1 + 6.4).
 *
 * ## O erro aparece AQUI, e não na tela de trás
 *
 * A primeira versão escrevia a falha no estado da tela, que só a renderizava na aba "Sobre" — e
 * quem denuncia está na aba "Posts", com este diálogo por cima. O resultado foi "Você já denunciou
 * este check-in" existindo e nunca sendo lido: o botão simplesmente não fazia nada.
 *
 * **Erro precisa aparecer onde o gesto aconteceu.** Achado nos passos 8 e 17 da bateria E.2.
 *
 * **Compartilhado entre o card do feed e o comentário** de propósito. São dois alvos com regras
 * diferentes no servidor, mas o gesto é o mesmo — e duas cópias divergiriam: uma ganharia o aviso
 * de anonimato, a outra não, e a pessoa aprenderia regras diferentes conforme a tela.
 *
 * ## O motivo é obrigatório, e o botão desabilitado é o aviso
 *
 * Escrever é o atrito que separa a denúncia pensada do toque irritado. Um campo opcional custaria
 * nada e traria à fila casos sem contexto — e o admin julga **sem saber quem reclamou**, então a
 * frase é tudo o que ele tem.
 *
 * ## Não fecha ao falhar
 *
 * O texto continua no campo. Fechar apagaria o esforço de quem redigiu, e ela desistiria da segunda
 * tentativa. É a mesma escolha do rascunho do comentário.
 */
@Composable
fun DialogoDeDenuncia(
    /**
     * O nome de quem publicou o conteúdo. `null` = diálogo fechado.
     *
     * ⚠️ **É o NOME, e não a frase pronta.** Até a G.3 este parâmetro recebia `"o check-in de Ana"`
     * já montado pela tela, e o título saía de `"Denunciar " + alvo + "?"` — duas concatenações
     * encadeadas. Em inglês a frase inteira se reorganiza ("Report Ana's check-in?") e nenhuma das
     * metades pode ser reordenada depois de colada.
     *
     * > **Frase montada por pedaços não se traduz: o tradutor recebe os pedaços, não a frase.**
     */
    nomeDoAutor: String?,
    /** Decide QUAL frase inteira usar. O servidor já distingue os dois alvos; a tela não adivinha. */
    ehComentario: Boolean,
    ocupado: Boolean,
    /** A falha da última tentativa. Mostrada DENTRO do diálogo — ver o KDoc acima. */
    erro: AppError?,
    aoFechar: () -> Unit,
    aoEnviar: (String) -> Unit,
) {
    val nome = nomeDoAutor ?: return

    // `key(nome)` zera o texto ao trocar de alvo: sem isso, abrir a denúncia de outro item
    // traria o motivo escrito para o anterior — e a pessoa enviaria sem reparar.
    key(nome) {
        var motivo by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { if (!ocupado) aoFechar() },
            title = {
                Text(
                    stringResource(
                        if (ehComentario) R.string.denuncia_titulo_comentario
                        else R.string.denuncia_titulo_checkin,
                        nome,
                    ),
                )
            },
            text = {
                Column {
                    Text(
                        // Diz o que ACONTECE, não o que a pessoa deve sentir. E diz que é anônimo
                        // porque é a informação que decide se ela denuncia num grupo de conhecidos.
                        stringResource(R.string.denuncia_explicacao),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = motivo,
                        // Corta no limite em vez de recusar no envio — a lição do comentário (8.1).
                        onValueChange = { if (it.length <= MAX_MOTIVO_DA_DENUNCIA) motivo = it },
                        label = { Text(stringResource(R.string.denuncia_campo_motivo)) },
                        placeholder = { Text(stringResource(R.string.denuncia_campo_exemplo)) },
                        minLines = 2,
                        enabled = !ocupado,
                        isError = erro != null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // Abaixo do campo, onde o olho já está. "Você já denunciou este check-in" é a
                    // recusa mais provável aqui, e sem ela o botão parece quebrado.
                    erro?.let {
                        Spacer(Modifier.height(8.dp))
                        ErroInline(it)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    // Desabilitado até haver texto: a regra aparece ANTES do envio, em vez de virar
                    // erro vermelho depois de a pessoa tocar.
                    enabled = motivo.isNotBlank() && !ocupado,
                    onClick = { aoEnviar(motivo.trim()) },
                ) {
                    Text(stringResource(R.string.comum_denunciar), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = aoFechar, enabled = !ocupado) {
                    Text(stringResource(R.string.comum_cancelar))
                }
            },
        )
    }
}
