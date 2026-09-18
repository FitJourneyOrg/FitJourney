package dev.rafael.app.idioma

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import dev.rafael.contract.i18n.Idioma
import dev.rafael.contract.i18n.IdiomaPolicy
import java.util.Locale

/**
 * A preferência de idioma DESTE APARELHO (fatia G.4, ARCH #37).
 *
 * ## Um mecanismo, três portas
 *
 * A pessoa troca o idioma na gaveta, em Configurações da conta ou nos **ajustes do sistema**. As
 * três escrevem aqui, e este objeto escreve num lugar só. Dois mecanismos gravando a mesma
 * preferência seriam duas fontes de verdade, que é o defeito que a fatia G inteira existe para
 * remover — a diferença entre "duas portas" e "dois mecanismos" é toda a diferença.
 *
 * ## Por que `LocaleManager` e não `AppCompatDelegate`
 *
 * O caminho que a documentação do Android recomenda é o `AppCompatDelegate.setApplicationLocales`,
 * e ele foi **recusado com motivo**: exige `AppCompatActivity`, e a `MainActivity` daqui é
 * `ComponentActivity`; pior, exige um tema descendente de `Theme.AppCompat`, e este app usa
 * `@android:style/Theme.Material.Light.NoActionBar`, um tema de PLATAFORMA, sem `themes.xml` no
 * projeto. Adotá-lo custaria dependência nova, troca da Activity raiz e um tema escrito do zero,
 * com risco visual em toda tela. Para dois idiomas, é caro demais.
 *
 * O [LocaleManager] é a API da própria plataforma (33+) e entrega o mesmo resultado, incluindo a
 * porta nos ajustes do sistema, sem nada disso.
 *
 * > **A recomendação da documentação vale para o app médio. O preço dela é do seu app.**
 *
 * ## Abaixo do Android 13
 *
 * O `minSdk` é 24 e o [LocaleManager] nasceu no 33. Abaixo disso a preferência vive numa
 * `SharedPreferences` e é aplicada em [envolver], chamado do `attachBaseContext` da Activity.
 * **Não é um mecanismo concorrente:** é o mesmo estado guardado onde o sistema ainda não guardava.
 * A porta dos ajustes não existe ali porque o Android não a tinha, não porque abrimos mão dela.
 *
 * ## O que "não escolheu" significa
 *
 * [escolhido] devolve `null` enquanto a pessoa nunca escolheu, e isso é diferente de "escolheu
 * português". Sem essa distinção não existe caminho de volta para *seguir o aparelho*, e ajuste
 * sem volta é ajuste que as pessoas têm medo de tocar.
 */
object IdiomaDoAparelho {

    private const val ARQUIVO = "idioma_do_aparelho"
    private const val CHAVE = "tag"

    /** O que a PESSOA escolheu, ou `null` se ela nunca escolheu. */
    fun escolhido(context: Context): Idioma? {
        val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)
                ?.applicationLocales
                ?.takeUnless { it.isEmpty }
                ?.get(0)
                ?.toLanguageTag()
        } else {
            prefs(context).getString(CHAVE, null)
        }
        return IdiomaPolicy.valida(tag)
    }

    /**
     * O idioma que o app REALMENTE renderiza agora.
     *
     * Sem escolha explícita, cai na cadeia do [IdiomaPolicy.preferido] sobre as preferências do
     * aparelho, que respeita a ordem em que a pessoa as declarou e não só a primeira.
     */
    fun efetivo(context: Context): Idioma =
        escolhido(context) ?: IdiomaPolicy.preferido(tagsDoSistema(context))

    /**
     * Grava a escolha. `null` volta a seguir o aparelho.
     *
     * No 33+ quem recria a Activity é o sistema. Abaixo dele, quem chama precisa recriar — está
     * dito aqui porque esquecer disso produz o pior sintoma possível: a escolha grava, a tela não
     * muda, e a pessoa conclui que o app não tem o idioma dela.
     */
    fun aplicar(context: Context, idioma: Idioma?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales =
                if (idioma == null) LocaleList.getEmptyLocaleList()
                else LocaleList.forLanguageTags(idioma.tag)
        } else {
            val editor = prefs(context).edit()
            if (idioma == null) editor.remove(CHAVE) else editor.putString(CHAVE, idioma.tag)
            editor.apply()
        }
    }

    /**
     * Aplica a preferência ao contexto base da Activity. Só faz efeito abaixo do Android 13: no
     * 33+ o sistema já entrega o contexto no idioma certo, e mexer de novo seria disputar com ele.
     */
    fun envolver(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val escolha = IdiomaPolicy.valida(prefs(base).getString(CHAVE, null)) ?: return base
        val locale = Locale.forLanguageTag(escolha.tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)

    private fun tagsDoSistema(context: Context): List<String> {
        val lista: LocaleList = context.resources.configuration.locales
        return (0 until lista.size()).map { lista[it].toLanguageTag() }
    }
}
