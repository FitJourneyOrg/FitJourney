package dev.rafael.server.features.group.services

import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

/**
 * O emoji que o grupo tem de reproduzir HOJE (regra `EMOJI_DO_DIA`, fatia D).
 *
 * ## Derivado, nunca guardado
 *
 * O emoji é função pura de `(groupId, dia)`. Não há tabela, não há job, não há migration para o
 * sorteio — é a mesma decisão do estado do grupo (#33) e do `currentWeek` (#22): **o que dá para
 * calcular não se guarda**.
 *
 * Um sorteio de verdade (`Random.nextInt()` a cada requisição) pareceria certo e quebraria a regra
 * inteira: duas pessoas do mesmo grupo veriam emojis diferentes no mesmo dia, e a própria tela
 * mudaria a cada recarga. O emoji do dia deixaria de existir.
 *
 * Aqui o resultado é **imprevisível para quem olha** — não há padrão visível, não dá para deduzir
 * o de amanhã — e **estável para quem calcula**. É o que "aleatório" precisa significar neste caso.
 *
 * ## O dia entra pronto
 *
 * Quem resolve o dia é quem chama, sempre no **fuso do grupo** (4.6). Se esta função recebesse um
 * `Instant`, teria de conhecer o fuso, e existiriam duas respostas para "que dia é hoje" no mesmo
 * código — o defeito que o fuso do grupo já custou uma vez.
 *
 * ## Por que mora em `group` e não em `checkin`
 *
 * O emoji é propriedade da **regra do grupo** (`GroupRule.EMOJI_DO_DIA`); o check-in apenas grava
 * o que valia no dia. Colocá-lo em `checkin` faria o `GroupMapper` importar de `checkin` para
 * preencher o `emojiDeHoje` — e feature dependendo de feature é o ciclo que o `UserCodePolicy` já
 * custou uma vez no #35. Cada import parecia certo sozinho.
 *
 * ## O emoji GRAVADO no check-in é que vale
 *
 * Esta função diz o emoji de hoje; o `check_ins.emoji` (V43) guarda o emoji **daquele** dia. Se a
 * [LISTA] mudar, o feed antigo continua mostrando o que as pessoas de fato imitaram. Mesma razão do
 * `title`/`body` renderizado da F.1: **registro do que houve, não consulta ao presente**.
 */
object EmojiDoDia {

    /**
     * A lista curada (revisada e ratificada por Rafael em 2026-09-05).
     *
     * ## Cinco critérios, e cada um excluiu candidatos reais
     *
     * **1. Reproduzível com o corpo.** Sortear de todo o Unicode entregaria 🏦 ou uma bandeira,
     * que ninguém imita. Só rosto e gesto entram.
     *
     * **2. Uma mão só.** Selfie se tira segurando o celular. 🙌 👏 🙏 🤝 saíram por isso — eram
     * da primeira versão desta lista, e o critério só apareceu quando ela foi revisada.
     *
     * **3. Existe no Android mais antigo que suportamos.** Emojis de Unicode 14 e 15
     * (🫱 🫲 🫳 🫸 🫰 🫵 🫡 🫢 🫣) aparecem como **caixa vazia** em aparelhos anteriores ao
     * Android 14 — o moto g50 da bancada é Android 12. Um emoji que a pessoa não enxerga é uma
     * regra impossível de cumprir, e o app não teria como saber que falhou.
     *
     * **4. Distinguível NA FOTO.** 🤚 🖐️ ✋ são o mesmo gesto com desenhos diferentes; ficou ✋.
     * E 👈 👉 saíram porque a câmera frontal **espelha**: apontar para a direita vira esquerda na
     * foto, e num app onde ninguém confere mas todo mundo compara, ambiguidade é atrito.
     *
     * **5. Não constrange.** 🖕 foi proposto e recusado: o feed é visto por até 49 pessoas, e
     * sortear um gesto ofensivo é fabricar denúncia (fatia E) a partir da própria regra.
     *
     * ## Forma NEUTRA, sempre
     *
     * Sem modificador de tom de pele. `✌️` e `🤞` variam por teclado, e duas versões do mesmo
     * gesto no feed pareceriam emojis diferentes. O neutro mantém o histórico comparável — há
     * teste garantindo que nenhum item carrega modificador.
     *
     * Crescer a lista é seguro: quem já fez check-in guarda o emoji antigo na coluna (V43).
     */
    val LISTA: List<String> = listOf(
        // gesto — uma mão só
        "✋", "🖖", "👌", "🤌", "🤏", "✌️", "🤞", "🤟", "🤘", "🤙",
        "☝️", "👍", "👎", "✊", "👊", "💪",
        // rosto
        "😀", "😂", "😚", "😛", "😜", "😝", "😔", "😬", "😒", "🤔", "🤫", "😤", "😠",
    )

    /**
     * O emoji de [dia] para [grupoId].
     *
     * `hashCode` do par, e não `Random(seed)`: o `Random` do Kotlin **não garante a mesma sequência
     * entre versões da linguagem**, e um emoji que muda depois de um upgrade do Kotlin seria um
     * defeito impossível de reproduzir. O hash de `String` é especificado e estável.
     *
     * `absoluteValue` não serve sozinho — `Int.MIN_VALUE.absoluteValue` continua negativo, e o
     * índice negativo estouraria a lista uma vez a cada dois bilhões. `Math.floorMod` cobre o caso.
     */
    fun de(grupoId: Uuid, dia: LocalDate): String =
        LISTA[Math.floorMod("$grupoId:$dia".hashCode(), LISTA.size)]
}
