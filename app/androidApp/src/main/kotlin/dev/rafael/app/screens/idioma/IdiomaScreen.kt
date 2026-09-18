package dev.rafael.app.screens.idioma

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.rafael.app.R
import dev.rafael.app.idioma.IdiomaDoAparelho
import dev.rafael.app.ui.rotulo
import dev.rafael.contract.i18n.Idioma

/**
 * Escolha do idioma da interface (fatia G.4, ARCH #37).
 *
 * ## ⭐ Os nomes dos idiomas NÃO se traduzem
 *
 * Cada idioma aparece sempre na própria língua: *Português (Brasil)* e *English*, idênticos no
 * `values` e no `values-en`. Quem precisa desta tela é justamente quem abriu o app num idioma que
 * não lê, e essa pessoa reconhece *English*, não *Inglês*.
 *
 * > **Traduzir a lista de idiomas torna ilegível a única tela que existe para sair de um idioma**
 * > **ilegível.**
 *
 * É por isso que `idioma_pt_br` e `idioma_en` entram na lista de chaves fixadas como iguais nos
 * dois catálogos, no `CatalogoEmInglesTest`: ali a igualdade é uma decisão, não um descuido.
 *
 * ## A terceira linha existe para dar meia-volta
 *
 * *Seguir o idioma do aparelho* devolve a preferência ao estado de quem nunca escolheu, que é
 * diferente de "escolhi português". Sem ela o ajuste vira via de mão única — e ajuste sem volta é
 * ajuste que as pessoas têm medo de tocar.
 *
 * ## Quem recria a tela
 *
 * No Android 13+ o sistema recria a Activity sozinho ao gravar a escolha. Abaixo dele, o
 * `recreate()` é nosso: sem ele a escolha grava e a tela não muda, e a pessoa conclui que o app
 * não tem o idioma dela.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdiomaScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    // `remember` com a escolha atual: depois do recreate a tela nasce de novo e relê.
    var escolhido by remember { mutableStateOf(IdiomaDoAparelho.escolhido(context)) }

    fun escolher(novo: Idioma?) {
        if (novo == escolhido) return
        escolhido = novo
        IdiomaDoAparelho.aplicar(context, novo)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            (context as? Activity)?.recreate()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.comum_idioma)) },
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
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Idioma.TODOS.forEach { idioma ->
                LinhaDeIdioma(
                    // O nome na PRÓPRIA língua, e nunca traduzido. Ver o KDoc desta tela.
                    nome = stringResource(idioma.rotulo()),
                    selecionado = idioma == escolhido,
                    onClick = { escolher(idioma) },
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            LinhaDeIdioma(
                nome = stringResource(R.string.idioma_seguir_sistema),
                selecionado = escolhido == null,
                onClick = { escolher(null) },
            )

            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.idioma_ajuda),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LinhaDeIdioma(nome: String, selecionado: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(nome) },
        trailingContent = {
            if (selecionado) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = stringResource(R.string.idioma_selecionado),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}
