package dev.rafael.app.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * A confirmação de saída, num lugar só.
 *
 * O `Sair` tem duas portas (menu lateral e tela de conta). Duplicar o diálogo duplicaria o
 * texto, e texto duplicado é o próximo item a divergir — uma porta acabaria avisando de uma
 * consequência que a outra não menciona.
 */
@Composable
fun DialogoDeSaida(onConfirmar: () -> Unit, onCancelar: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("Sair da conta?") },
        text = { Text("Você precisará entrar de novo para acessar seus programas.") },
        confirmButton = { TextButton(onClick = onConfirmar) { Text("Sair") } },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } },
    )
}
