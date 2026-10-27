# -*- coding: utf-8 -*-
"""
Catálogo de MENSAGENS DE ERRO do servidor — GERADO A PARTIR DO CÓDIGO.

Uso:
    python3 docs/geradores/gen_erros.py            # escreve docs/gerados/erros.md
    python3 docs/geradores/gen_erros.py --pdf      # também gera o PDF

POR QUE GERADO E NÃO ESCRITO À MÃO
----------------------------------
Mesmo argumento do `gen_testes.py`, e aqui o custo do esquecimento é maior. Uma lista mantida a
mão faz alguém **traduzir frase que já foi removida** e **não receber a que entrou**. O primeiro
é desperdício; o segundo é buraco na tela, e nenhum dos dois aparece até o idioma novo estar em
produção.

O QUE ESTE DOCUMENTO SERVE (atualizado em 2026-09-11, depois da G.2 e da G.3)
----------------------------------------------------------------------------
**O propósito deste documento MUDOU, e o texto de abertura ficou para trás por uma fatia inteira.**

Ele nasceu na G.2 para validar o português das frases que o cliente exibia literalmente
(`ErrorUi` fazia `texto = message`). A G.2 acabou: deu código a cada frase e moveu o texto para o
`strings.xml`. Hoje **72 dos 88 `ErrorCodes` têm frase própria no cliente** e a mensagem do
servidor não chega mais à tela na maioria dos casos.

Então o que este catálogo é agora:

1. **O inventário do que o servidor AINDA diz em prosa** — as 9 frases do `SEM_TEXTO_PROPRIO`
   (carregam número que só o servidor conhece) e a frase de qualquer código que o cliente não
   conheça, que é o que faz um app antigo explicar bem um erro novo.
2. **A auditoria de que toda frase tem código.** Frase sem código aqui é frase que o cliente não
   consegue traduzir nunca.
3. **O par de contexto** (`erros_contexto.py`) que permite julgar se a frase está boa — o único
   lugar onde isso está escrito.

⚠️ LIÇÃO, e é o motivo deste bloco ser tão longo
------------------------------------------------
O preâmbulo de um documento gerado é **prosa escrita à mão dentro do gerador**. Tudo o mais aqui
sai do código e não pode mentir; ele pode, e mentiu por uma fatia inteira, com o gerador rodando
verde a cada `gen_tudo.py`. Nada avisou, porque não há o que avisar: nenhum teste sabe o que a
abertura deveria dizer.

> **A única parte de um documento gerado que pode mentir é a que foi escrita à mão.**
> Quem muda o sistema tem de reler a abertura; o resto se corrige sozinho.

POR QUE ELE CARREGA TANTO CONTEXTO
----------------------------------
A primeira versão listava a frase, o arquivo e a linha. Não dava para **validar** nada com isso:
ler *"Não encontramos este item."* fora de contexto não diz se a frase está boa, porque "boa"
depende de quem a lê e do que a pessoa acabou de tentar fazer.

Então cada erro traz:

- **em que operação** ele mora (a função) e por **qual rota** se chega até lá;
- **a guarda** que decide, em código, tirada do próprio arquivo;
- **o comentário** que o autor deixou ali, que neste projeto costuma explicar a regra;
- se a frase tem **valor interpolado**, se vira **erro de campo** de formulário, e se está
  **repetida** em outro lugar.

> **O tradutor não tem o app aberto, e quem valida o texto também não tem o código na cabeça.**
> Contexto que não está no documento não existe.
"""
import os
import re
import sys

DOCS = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ROOT = os.path.abspath(os.path.join(DOCS, ".."))
ALVO = os.path.join(ROOT, "server", "src", "main", "kotlin")
SAIDA = os.path.join(DOCS, "gerados", "erros.md")

# ---------------------------------------------------------------------------
# O QUE O USUÁRIO FEZ — escrito à mão, em `docs/fontes/erros_contexto.py`.
#
# É a única parte deste documento que não sai do código, e não sai porque não PODE sair:
# `if (!novidade)` é verdade sobre o código; *"denunciou de novo o que já tinha denunciado"* é
# verdade sobre a pessoa, e só a segunda permite julgar se a frase está boa.
#
# A chave é a frase exata. Mexer no texto de um erro **quebra o par de propósito**, e as duas
# metades aparecem no documento: frase sem descrição vira lacuna com aviso, descrição órfã vira
# lista no fim. Não impedimos o esquecimento; tornamos o esquecimento visível.
# ---------------------------------------------------------------------------
sys.path.insert(0, os.path.join(DOCS, "fontes"))
try:
    from erros_contexto import CONTEXTO, CAUSAS
except ImportError:
    CONTEXTO, CAUSAS = {}, {}


def causa_de(mensagem, classe_funcao, indice):
    """
    A causa de UMA ocorrência, quando a frase serve a mais de um lugar.

    O valor pode ser uma frase (vale para todas as ocorrências daquela função) ou uma lista, na
    ordem em que aparecem no arquivo.

    > **Duas causas diferentes debaixo da mesma frase é a definição de frase vaga.** É lendo estas
    > lado a lado que se decide desmembrar.
    """
    porta = CAUSAS.get(mensagem, {}).get(classe_funcao)
    if porta is None:
        return None
    if isinstance(porta, str):
        return porta
    return porta[indice] if indice < len(porta) else None

# O status vem do `ErrorMapper`. Repetido aqui porque quem valida o texto precisa saber se a
# frase é uma recusa que a pessoa pode corrigir (400) ou uma porta fechada (403).
STATUS = {
    "Validation": ("400", "o usuário pode corrigir e tentar de novo"),
    "Unauthorized": ("401", "sessão inválida"),
    "Forbidden": ("403", "não pode, e tentar de novo não resolve"),
    "NotFound": ("404", "não existe, ou não é visível para quem perguntou"),
    "Conflict": ("409", "o estado atual impede"),
    "Unexpected": ("500", "não chega ao usuário"),
    "Connection": ("500", "não chega ao usuário"),
    "Direto": ("—", "resposta montada direto, sem passar por AppError"),
}
INTERNOS = ("Unexpected", "Connection")

TIPOS = "|".join(k for k in STATUS if k != "Direto")
ABRE = re.compile(r"AppError\.(" + TIPOS + r")\s*\(")
RESPOSTA = re.compile(r"ErrorResponse\s*\(\s*([A-Za-z_][\w.]*)\s*,\s*\"([^\"]*)\"")
FUNCAO = re.compile(r"\bfun\s+(?:<[^>]+>\s*)?([A-Za-z_]\w*)\s*[(<]")
VERBO = re.compile(r'\b(get|post|put|patch|delete)\s*\(\s*"([^"]+)"\s*\)\s*\{')
TRAVESSAO = ("—", "–")


# ---------------------------------------------------------------------------
# Leitura
# ---------------------------------------------------------------------------

def sem_comentarios(texto):
    """
    Zera comentários **preservando o número de linhas**.

    Preservar a contagem é o que permite usar o mesmo número de linha nos dois textos: o limpo
    para achar as construções, o original para ler o comentário que o autor deixou ali.
    """
    limpo = re.sub(r"/\*.*?\*/", lambda m: "\n" * m.group(0).count("\n"), texto, flags=re.S)
    return re.sub(r"//[^\n]*", "", limpo)


def bloco(texto, inicio, abre="(", fecha=")"):
    """O miolo do bloco que abre em `inicio`, com delimitadores balanceados."""
    nivel = 0
    for i in range(inicio, len(texto)):
        c = texto[i]
        if c == abre:
            nivel += 1
        elif c == fecha:
            nivel -= 1
            if nivel == 0:
                return texto[inicio + 1:i], i
    return "", inicio


def literais(s):
    return re.findall(r'"((?:[^"\\]|\\.)*)"', s)


def primeiro_argumento(miolo):
    """Corta na primeira vírgula de NÍVEL ZERO — em `mapOf("a" to b, "c" to d)` a primeira não serve."""
    nivel = 0
    aspas = False
    escapado = False
    for i, c in enumerate(miolo):
        if escapado:
            escapado = False
            continue
        if c == "\\":
            escapado = True
        elif c == '"':
            aspas = not aspas
        elif not aspas:
            if c in "([{":
                nivel += 1
            elif c in ")]}":
                nivel -= 1
            elif c == "," and nivel == 0:
                return miolo[:i].strip()
    return miolo.strip()


PARAMS = re.compile(r"\bfun\s+(?:<[^>]+>\s*)?([A-Za-z_]\w*)\s*\(([^)]*)\)")


def helper_de(limpo, pos, primeiro):
    """
    Quando a frase é PARÂMETRO da função que a envolve, esta função é um auxiliar, e as frases
    de verdade estão nas chamadas dela.

    ```kotlin
    private fun recusa(mensagem: String) = AppError.Validation(mensagem, mapOf("foto" to mensagem))
    // ...
    if (bytes.isEmpty()) return recusa("Envie uma foto.")
    ```

    Sem tratar isso, o catálogo registrava uma "frase" chamada **`foto`** — o nome do campo — e
    **perdia as três frases reais**. O mesmo acontecia com `DisplayNamePolicy.erro`, e eram cinco
    frases invisíveis no total.

    > **Frase que o usuário lê pode não estar onde o erro é construído.**
    """
    anteriores = list(PARAMS.finditer(limpo[:pos]))
    if not anteriores:
        return None
    m = anteriores[-1]
    nomes = re.findall(r"([A-Za-z_]\w*)\s*:", m.group(2))
    return m.group(1) if primeiro in nomes else None


def mensagem_de(miolo, texto, ate):
    """
    A frase que o usuário lê.

    Normalmente é o primeiro literal do miolo. **Mas nem sempre**: quando a mesma frase vai para
    a `message` E para o `fieldErrors`, o código a guarda numa variável antes:

        val msg = "Use até 40 caracteres."
        return AppError.Validation(msg, mapOf("nomeDoLocal" to msg))

    Pegar "o primeiro literal" ali devolveria **`nomeDoLocal`**, que é nome de campo e não frase.
    A primeira versão deste script fez exatamente isso, e só apareceu porque um camelCase destoou
    no meio de um catálogo em português.

    > **Um gerador que lê o código precisa ler as formas que o código realmente usa.**
    """
    primeiro = primeiro_argumento(miolo)
    # Argumento NOMEADO: `AppError.Validation(message = msg, fieldErrors = ...)`.
    primeiro = re.sub(r"^[A-Za-z_]\w*\s*=\s*", "", primeiro).strip()

    if primeiro.startswith('"'):
        achados = literais(primeiro)
        if not achados:
            return ""
        # CONCATENAÇÃO: `"...pode apagar " + "este comentário."` é UMA frase quebrada em duas
        # linhas para caber na margem. Pegar só o primeiro literal devolveria meia frase, e meia
        # frase é pior que nenhuma: parece completa e não é.
        #
        # O teste é conservador: só junta se, tirando os literais, sobrar apenas espaço e `+`.
        # Com uma variável no meio (`"a " + nome + " b"`) prefiro devolver o primeiro pedaço a
        # inventar uma frase que o código não produz.
        resto = re.sub(r'"(?:[^"\\]|\\.)*"', "", primeiro)
        if re.fullmatch(r"[\s+]*", resto):
            return "".join(achados)
        return achados[0]
    if re.fullmatch(r"[A-Za-z_]\w*", primeiro):
        anterior = list(re.finditer(r'\bval\s+%s\s*=\s*"((?:[^"\\]|\\.)*)"' % re.escape(primeiro), texto[:ate]))
        if anterior:
            return anterior[-1].group(1)
        # Não é `val` local: pode ser PARÂMETRO de uma função auxiliar. Ver [helper_de].
        return ""
    achados = literais(miolo)
    return achados[0] if achados else ""


def codigo_de(miolo):
    """
    O `code` do erro, em qualquer das formas que o código usa.

    Todas estas aparecem na base, e a primeira versão só reconhecia a primeira:

    ```kotlin
    AppError.Conflict("...", CODE_JA_DENUNCIEI)                  // constante local, posicional
    AppError.Forbidden("...", ErrorCodes.HEALTH_GATE_REQUIRED)   // qualificada, posicional
    AppError.Validation("...", code = ErrorCodes.CURSOR_INVALIDO) // nomeada
    ```

    Reconhecer só a primeira fez o catálogo reportar **80 frases sem código** logo depois de a G.2
    ter posto código em 61 delas. Um relatório que erra para menos é pior que nenhum: ele manda
    trabalhar de novo no que já está pronto.
    """
    resto = miolo[len(primeiro_argumento(miolo)):]
    padroes = (
        r"\bcode\s*=\s*(?:ErrorCodes\.)?([A-Z][A-Za-z0-9_]*)",   # nomeada
        r",\s*(?:ErrorCodes\.)?([A-Z][A-Z0-9_]{2,})\b",          # posicional
        r",\s*\"([A-Z][A-Z0-9_]{2,})\"",                         # literal
    )
    for p in padroes:
        m = re.search(p, resto)
        if m:
            return m.group(1)
    return ""


# ---------------------------------------------------------------------------
# Contexto: onde mora e como se chega
# ---------------------------------------------------------------------------

def funcao_de(limpo, pos):
    """A função que contém a posição. A última declarada antes dela."""
    anteriores = list(FUNCAO.finditer(limpo[:pos]))
    return anteriores[-1].group(1) if anteriores else ""


CONTROLE = re.compile(r"^\s*(if\s*\(|when\s*[({]|\}?\s*else\b|.*\?:|.*->|require\(|return\s+if)")


def guarda_de(linhas, i):
    """
    A linha de código que DECIDE por este erro.

    Olha para trás no máximo oito linhas procurando controle de fluxo. Oito é o suficiente para
    um `if` multilinha e curto o bastante para não capturar a guarda de outro erro.

    Devolve a linha crua, do próprio arquivo. É deliberado mostrar código em vez de uma descrição
    minha: **descrição minha é uma segunda fonte da verdade** e envelhece sem avisar; a linha
    envelhece junto com o arquivo, porque é o arquivo.
    """
    for j in range(i, max(-1, i - 8), -1):
        linha = linhas[j]
        if not linha.strip():
            continue
        if CONTROLE.match(linha):
            return linha.strip()
    return ""


def comentario_de(linhas_originais, i):
    """
    O comentário imediatamente acima, se houver. Neste projeto ele costuma explicar a REGRA.

    Sobe enquanto encontrar `//` ou linha de KDoc (` * `), e para na primeira linha de código.
    """
    coletadas = []
    j = i - 1
    while j >= 0:
        crua = linhas_originais[j].strip()
        if crua.startswith("//"):
            coletadas.append(crua.lstrip("/").strip())
        elif crua.startswith("*") and not crua.startswith("*/"):
            coletadas.append(crua.lstrip("*").strip())
        elif crua in ("/**", "*/", ""):
            if crua == "" and coletadas:
                break
        else:
            break
        j -= 1
    coletadas = [c for c in reversed(coletadas) if c]
    texto = " ".join(coletadas).strip()
    return texto[:400] + ("…" if len(texto) > 400 else "")


def indexar_rotas():
    """
    Índice `(verbo, caminho) -> corpo`, lido dos `*Routes.kt`.

    Serve para responder **por onde o usuário chega até este erro**, que é a pergunta que valida
    o texto: a mesma frase pode estar certa numa tela e errada em outra.
    """
    indice = []
    for raiz, _, arquivos in os.walk(ALVO):
        for nome in arquivos:
            if not nome.endswith("Routes.kt"):
                continue
            caminho = os.path.join(raiz, nome)
            with open(caminho, encoding="utf-8") as f:
                limpo = sem_comentarios(f.read())
            for m in VERBO.finditer(limpo):
                corpo, _ = bloco(limpo, limpo.index("{", m.end() - 1), "{", "}")
                indice.append({
                    "verbo": m.group(1).upper(),
                    "caminho": m.group(2),
                    "corpo": corpo,
                    "arquivo": nome,
                    "inicio": m.start(),
                    "feature": feature_de(caminho),
                })
    return indice


def rotas_de(indice, funcao, arquivo, pos, feature):
    """
    As rotas que levam a este erro, em três níveis de confiança.

    1. **exata** — o erro mora num `*Routes.kt`, e a rota é a que o contém.
    2. **provável** — o nome da função aparece no corpo de uma rota **da mesma área**.
    3. **fraca** — o nome aparece em rota de outra área.

    O filtro por área não é preciosismo. Sem ele, `apagar` casava com
    `DELETE /groups/{id}/checkins/{id}` **e** `DELETE /me/devices/{token}`, porque os dois corpos
    chamam algo chamado `apagar`. Uma rota errada no documento é pior que nenhuma: quem valida o
    texto leria a frase pensando numa tela que não é a dela.

    > **Inferência que não distingue confiança vira certeza inventada.**
    """
    if arquivo.endswith("Routes.kt"):
        candidatas = [r for r in indice if r["arquivo"] == arquivo and r["inicio"] <= pos]
        if candidatas:
            r = max(candidatas, key=lambda x: x["inicio"])
            return ["`%s %s`" % (r["verbo"], r["caminho"])], "exata"
    if not funcao:
        return [], "nenhuma"

    padrao = re.compile(r"\.%s\s*\(" % re.escape(funcao))
    mesma_area = sorted(set(
        "`%s %s`" % (r["verbo"], r["caminho"])
        for r in indice if r["feature"] == feature and padrao.search(r["corpo"])
    ))
    if mesma_area:
        return mesma_area, "provavel"

    qualquer = sorted(set(
        "`%s %s`" % (r["verbo"], r["caminho"])
        for r in indice if padrao.search(r["corpo"])
    ))
    return qualquer, ("fraca" if qualquer else "nenhuma")


KDOC = re.compile(
    r"/\*\*(.*?)\*/\s*(?:@\w+\s*)*"
    r"(?:(?:private|internal|public|suspend|override|inline|operator)\s+)*"
    r"fun\s+(?:<[^>]+>\s*)?([A-Za-z_]\w*)",
    re.S,
)


def kdoc_da_funcao(texto, pos, funcao):
    """
    O KDoc **da função [funcao]**, resumido na primeira frase.

    Existe como plano B do comentário adjacente: neste projeto a regra costuma estar no cabeçalho
    da função, não ao lado do `return`.

    ⚠️ **Exige que o nome bata.** A primeira versão pegava simplesmente o último KDoc antes da
    posição, e quando a função do erro não tinha KDoc próprio ela herdava o da função ANTERIOR.
    O resultado apareceu no documento: o erro *"Transfira o cargo de admin antes de sair"* vinha
    com a explicação de *"Entrar pelo LINK"*, que é outra operação inteira.

    > **Contexto errado é pior que contexto ausente**, porque quem lê não tem como desconfiar.
    """
    anteriores = [m for m in KDOC.finditer(texto[:pos + 300]) if m.group(2) == funcao]
    if not anteriores:
        return ""
    corpo = anteriores[-1].group(1)
    linhas = []
    for linha in corpo.split("\n"):
        limpa = linha.strip().lstrip("*").strip()
        if limpa.startswith("@"):
            break
        if not limpa and linhas:
            break
        if limpa:
            linhas.append(limpa)
    texto_kdoc = " ".join(linhas).strip()
    return texto_kdoc[:300] + ("…" if len(texto_kdoc) > 300 else "")


def feature_de(caminho):
    rel = caminho.replace("\\", "/")
    m = re.search(r"/features/([^/]+)/", rel)
    if m:
        return m.group(1)
    for pasta in ("media", "auth", "avisos", "error", "plugins"):
        if "/%s/" % pasta in rel:
            return pasta
    return "core"


AREAS = {
    "user": "Conta e usuário",
    "profile": "Perfil e onboarding",
    "program": "Programas",
    "workout": "Treinos",
    "session": "Execução de treino",
    "exercise": "Catálogo de exercícios",
    "group": "Grupos e desafios",
    "checkin": "Check-in, social e moderação",
    "friendship": "Amizades",
    "notificacao": "Notificações",
    "stats": "Conquistas e progresso",
    "media": "Fotos",
    "auth": "Autenticação",
    "error": "Genéricos do servidor",
    "core": "Genéricos do servidor",
    "avisos": "Avisos diários",
    "plugins": "Genéricos do servidor",
}


# ---------------------------------------------------------------------------
# Coleta
# ---------------------------------------------------------------------------

def coletar(indice):
    achados = []
    for raiz, _, arquivos in os.walk(ALVO):
        for nome in arquivos:
            if not nome.endswith(".kt"):
                continue
            caminho = os.path.join(raiz, nome)
            with open(caminho, encoding="utf-8") as f:
                texto = f.read()
            limpo = sem_comentarios(texto)
            linhas_limpas = limpo.split("\n")
            linhas_originais = texto.split("\n")
            rel = os.path.relpath(caminho, ROOT).replace("\\", "/")

            def registrar(tipo, mensagem, codigo, campos, pos):
                de_limites = bool(INTERPOLA_LIMITE.search(mensagem))
                mensagem = resolver_limites(mensagem)
                i = limpo[:pos].count("\n")
                funcao = funcao_de(limpo, pos)
                feat = feature_de(caminho)
                rotas, confianca = rotas_de(indice, funcao, nome, pos, feat)
                achados.append({
                    "tipo": tipo,
                    "mensagem": mensagem,
                    "de_limites": de_limites,
                    "codigo": codigo,
                    "campos": campos,
                    "arquivo": rel,
                    "curto": nome,
                    "linha": i + 1,
                    "funcao": funcao,
                    "classe": nome[:-3],
                    "guarda": guarda_de(linhas_limpas, i),
                    "comentario": comentario_de(linhas_originais, i),
                    "kdoc": kdoc_da_funcao(texto, pos, funcao),
                    "rotas": rotas,
                    "confianca": confianca,
                    "feature": feat,
                })

            for m in ABRE.finditer(limpo):
                abre = limpo.index("(", m.start())
                miolo, _ = bloco(limpo, abre)
                campos = re.findall(r'"([A-Za-z_]\w*)"\s+to\b', miolo) if "mapOf(" in miolo else []
                codigo = codigo_de(miolo)
                mensagem = mensagem_de(miolo, limpo, m.start())

                if mensagem:
                    registrar(m.group(1), mensagem, codigo, campos, m.start())
                    continue

                # A frase veio por PARÂMETRO: é função auxiliar. As frases reais estão nas
                # chamadas dela, e cada uma vira uma entrada com o contexto do SEU call site.
                cru = re.sub(r"^[A-Za-z_]\w*\s*=\s*", "", primeiro_argumento(miolo)).strip()
                auxiliar = helper_de(limpo, m.start(), cru)
                if not auxiliar:
                    continue
                # O CÓDIGO também vem do call site, não do auxiliar.
                #
                # Na G.2 esses helpers passaram a receber `code` por parâmetro, justamente para que
                # as três recusas de foto pudessem ter três textos diferentes no cliente. Lendo o
                # código de dentro do auxiliar, o catálogo reportava as cinco frases como "sem
                # código" DEPOIS de elas terem ganhado um.
                #
                # > **Relatório que erra para menos manda trabalhar de novo no que já está pronto.**
                chamada = re.compile(r"(?<![.\w])%s\s*\(" % re.escape(auxiliar))
                for c in chamada.finditer(limpo):
                    abre_c = limpo.index("(", c.start())
                    miolo_c, _ = bloco(limpo, abre_c)
                    frases = literais(miolo_c)
                    if not frases:
                        continue
                    registrar(m.group(1), frases[0], codigo_de(miolo_c) or codigo, campos, c.start())

            for m in RESPOSTA.finditer(limpo):
                registrar("Direto", m.group(2), m.group(1).split(".")[-1], [], m.start())
    return achados


def interpolado(s):
    return "${" in s or re.search(r"\$[A-Za-z_]", s) is not None


# ---------------------------------------------------------------------------
# CONSTANTES DE `Limites` (B3, 2026-10-07)
#
# O servidor escreve `"Digite uma idade entre ${Limites.Profile.AGE_MIN} e ..."`, e o arquivo de
# contexto é indexado pelo texto que o usuário LÊ ("entre 5 e 120 anos"). Sem resolver a constante
# o par nunca casava: a frase aparecia "sem descrição" e a descrição, "órfã", o mesmo defeito
# contado dos dois lados.
#
# Resolver aqui, e não indexar o contexto pela forma interpolada, mantém `Limites` como a ÚNICA
# fonte do número. Mudar o limite muda a frase, e o par quebra DE PROPÓSITO (a descrição vira órfã,
# visível no fim do documento) -- é o aviso de que a frase mudou e precisa ser revista.
#
# Só resolve o que consegue ler em `Limites.kt`. O que não conseguir fica como está (e continua
# marcado "tem valor interpolado"), e o gerador avisa no final.
# ---------------------------------------------------------------------------
LIMITES_KT = os.path.join(ROOT, "shared-contract", "src", "commonMain", "kotlin",
                          "dev", "rafael", "contract", "limites", "Limites.kt")
INTERPOLA_LIMITE = re.compile(r"\$\{\s*(Limites(?:\.\w+)+)\s*\}")


def carregar_limites():
    """`{"Limites.Profile.AGE_MIN": "5", ...}`, lido dos `object` aninhados e dos `const val`."""
    if not os.path.isfile(LIMITES_KT):
        return {}
    texto = sem_comentarios(open(LIMITES_KT, encoding="utf-8").read())
    valores, pilha = {}, []
    for linha in texto.split("\n"):
        o = re.match(r"\s*object\s+(\w+)\s*\{", linha)
        if o:
            pilha.append(o.group(1))
            continue
        c = re.match(r"\s*const\s+val\s+(\w+)\s*(?::\s*\w+)?\s*=\s*(-?\d+)\s*$", linha)
        if c and pilha:
            valores[".".join(pilha + [c.group(1)])] = c.group(2)
            continue
        if linha.strip() == "}" and pilha:
            pilha.pop()
    return valores


LIMITES = carregar_limites()


def resolver_limites(mensagem):
    """Troca `${Limites.X.Y}` pelo número. O que não achar em `Limites.kt` fica intacto."""
    return INTERPOLA_LIMITE.sub(lambda m: LIMITES.get(m.group(1), m.group(0)), mensagem)


def esc(s):
    return s.replace("|", "\\|")


# ---------------------------------------------------------------------------
# Documento
# ---------------------------------------------------------------------------

def main():
    indice = indexar_rotas()
    achados = coletar(indice)
    achados.sort(key=lambda a: (AREAS.get(a["feature"], a["feature"]), a["arquivo"], a["linha"]))

    visiveis = [a for a in achados if a["tipo"] not in INTERNOS]
    internos = [a for a in achados if a["tipo"] in INTERNOS]

    contagem = {}
    for a in visiveis:
        contagem.setdefault(a["mensagem"], []).append(a)
    repetidas = {k: v for k, v in contagem.items() if len(v) > 1}
    com_travessao = [a for a in achados if any(t in a["mensagem"] for t in TRAVESSAO)]
    sem_codigo = [a for a in visiveis if not a["codigo"]]
    sem_rota = [a for a in visiveis if not a["rotas"]]

    # As duas metades da sincronia entre o código e o `erros_contexto.py`.
    sem_contexto = {a["mensagem"] for a in visiveis if a["mensagem"] not in CONTEXTO}
    orfas = set(CONTEXTO) - set(contagem)

    L = []
    a = L.append

    a("# Mensagens de erro do servidor")
    a("")
    a("> **GERADO. Não edite este arquivo.** Edite as frases no código e rode")
    a("> `python docs/geradores/gen_erros.py`. Uma lista mantida a mão faz alguém traduzir frase")
    a("> que já foi removida e não receber a que entrou.")
    a("")
    a("## Para que serve")
    a("")
    a("⚠️ **O cliente já NÃO mostra estas frases, na maioria dos casos.** A fatia G.2 deu um código")
    a("distinto a cada uma e moveu o texto para o `strings.xml`: hoje **72 dos 88 `ErrorCodes` têm**")
    a("**frase própria no cliente**, traduzida em pt-BR e em inglês, e é ela que aparece na tela.")
    a("")
    a("O que o servidor ainda diz em prosa, e que continua chegando ao usuário:")
    a("")
    a("- **as 9 frases do `TextosDeErro.SEM_TEXTO_PROPRIO`**, que carregam um número que só o")
    a("  servidor conhece (prazo da denúncia, limite de caracteres, tetos de plano);")
    a("- **a frase de qualquer código que o cliente não conheça** — de propósito: é o que faz um")
    a("  app antigo continuar explicando bem um erro que o servidor só inventou depois.")
    a("")
    a("Para essas, este documento continua sendo onde se valida o português. Para as outras 72, ele")
    a("é **auditoria**: frase sem código aqui é frase que o cliente não consegue traduzir nunca.")
    a("")
    a("> **Corrigir a origem custa um. Corrigir depois de traduzido custa N.**")
    a("")
    a("## Como ler cada erro")
    a("")
    a("| Campo | O que é, e por que está aqui |")
    a("|---|---|")
    a("| **causa** *(nas frases repetidas)* | O que leva a **aquela ocorrência** especificamente. Ler as causas de um mesmo número lado a lado é o que decide se a frase precisa ser desmembrada: **duas causas diferentes debaixo da mesma frase é a definição de frase vaga**. |")
    a("| **O que a pessoa fez** | O gesto que leva até o erro, em português. **É a única parte que não sai do código**, e não sai porque não pode: `if (!novidade)` é verdade sobre o código, *\"denunciou de novo o que já tinha denunciado\"* é verdade sobre a pessoa, e só a segunda permite julgar se a frase está boa. Vive em `docs/fontes/erros_contexto.py`, chaveada pela frase exata: mexer no texto do erro quebra o par de propósito e obriga a revisar a descrição. |")
    a("| **Guarda no código** | A linha que decide, tirada do arquivo. Fica aqui para conferir a descrição acima contra o que o código realmente faz. |")
    a("| **Nota ao lado do erro** | O comentário que o autor deixou naquela linha. |")
    a("| **O que a operação faz** | A primeira frase do KDoc da função. Neste projeto a REGRA costuma estar no cabeçalho da função, não ao lado do `return`, e é a regra que decide se a frase de erro está boa. |")
    a("| **Rota** | Por onde o usuário chega, em três níveis: **exata** (o erro mora no arquivo de rotas), **provável** (o nome da função aparece numa rota da mesma área) e **fraca** (casou por nome em outra área, pode estar errada). Sem o filtro por área, `apagar` casava com a rota de apagar check-in **e** com a de apagar token de aparelho. |")
    a("| **Operação** | Classe e função. Responde \"o que a pessoa estava tentando fazer\". |")
    a("| **HTTP** | 400 é recusa que a pessoa corrige; 403 é porta fechada; 404 pode ser \"não existe\" ou \"não é para você\"; 409 é o estado atual impedindo. **Muda o tom certo da frase.** |")
    a("")
    a("## Resumo")
    a("")
    a("| | |")
    a("|---|---:|")
    a("| **Frases que o usuário LÊ** | **%d** |" % len(visiveis))
    a("| Frases distintas (depois de juntar as repetidas) | %d |" % len(contagem))
    a("| Frases internas (viram \"Erro interno\") | %d |" % len(internos))
    a("| Sem código próprio (usam o genérico) | %d |" % len(sem_codigo))
    a("| Repetidas em mais de um lugar | %d grupos |" % len(repetidas))
    a("| Sem rota identificada | %d |" % len(sem_rota))
    a("| Com travessão (viola a convenção do #37) | %d |" % len(com_travessao))
    a("| **Sem descrição de \"o que a pessoa fez\"** | **%d** |" % len(sem_contexto))
    a("| Descrições órfãs (a frase mudou ou sumiu) | %d |" % len(orfas))
    a("")

    if sem_contexto:
        a("### ⚠️ Faltando descrever: %d" % len(sem_contexto))
        a("")
        a("Estas frases entraram no código depois da última revisão deste catálogo. Acrescente cada")
        a("uma em `docs/fontes/erros_contexto.py` e rode o gerador de novo.")
        a("")
        for frase in sorted(sem_contexto):
            a("- `%s`" % esc(frase))
        a("")

    if orfas:
        a("### ⚠️ Descrições órfãs: %d" % len(orfas))
        a("")
        a("Há descrição em `erros_contexto.py` para frases que **não existem mais no código**. Ou o")
        a("texto do erro foi editado, e a descrição precisa ser reapontada, ou o erro sumiu e a")
        a("descrição pode ser apagada.")
        a("")
        a("**A segunda hipótese é a perigosa:** se o texto mudou, a descrição antiga continuaria")
        a("parecendo válida se este bloco não existisse.")
        a("")
        for frase in sorted(orfas):
            a("- `%s`" % esc(frase))
        a("")

    if repetidas:
        a("### ⚠️ Frases repetidas: %d grupos, %d ocorrências" % (
            len(repetidas), sum(len(v) for v in repetidas.values())))
        a("")
        a("A mesma frase em mais de um lugar. **Cada grupo é candidato a um código só**, e")
        a("descobrir isso agora é mais barato que traduzir a mesma coisa sete vezes e receber sete")
        a("traduções que podem divergir.")
        a("")
        a("Mas repare antes de unificar: às vezes a mesma frase serve a **operações diferentes**, e")
        a("aí ela pode estar certa numa e vaga na outra. A coluna de operações existe para isso.")
        a("")
        a("| Frase | Vezes | Operações |")
        a("|---|---:|---|")
        for frase, ocs in sorted(repetidas.items(), key=lambda kv: -len(kv[1])):
            ops = " · ".join(sorted(set("`%s.%s`" % (o["classe"], o["funcao"]) for o in ocs)))
            a("| %s | %d | %s |" % (esc(frase), len(ocs), ops))
        a("")
        a("Cada uma aparece **uma vez só** na listagem abaixo, com todos os lugares listados dentro.")
        a("Validar a mesma frase sete vezes seria trabalho repetido para o mesmo julgamento.")
        a("")

    if com_travessao:
        a("### ⚠️ Travessão")
        a("")
        a("Viola a convenção do ARCH #37, seção 8. Corrigir **antes** de traduzir, senão o")
        a("travessão entra em todos os idiomas de uma vez.")
        a("")
        for x in com_travessao:
            a("- `%s:%d` — %s" % (x["arquivo"], x["linha"], esc(x["mensagem"])))
        a("")

    # ---- uma entrada por FRASE DISTINTA, numerada ----
    #
    # A versão anterior emitia um bloco por OCORRÊNCIA, e "Programa não encontrado" aparecia sete
    # vezes idêntico. Para validar TEXTO isso é puro desperdício: a frase é a mesma, o julgamento é
    # o mesmo. As ocorrências viram uma lista dentro do bloco, porque o LUGAR ainda importa para
    # decidir se a frase serve a todos eles.
    distintas = []
    for x in visiveis:
        if not any(d["mensagem"] == x["mensagem"] for d in distintas):
            distintas.append(x)
    distintas.sort(key=lambda x: (AREAS.get(x["feature"], x["feature"]), x["arquivo"], x["linha"]))
    numero = {d["mensagem"]: i + 1 for i, d in enumerate(distintas)}

    a("---")
    a("")
    a("## Índice")
    a("")
    a("**%d frases** para validar. Use o NÚMERO para se referir a uma delas." % len(distintas))
    a("")
    area_atual = None
    for d in distintas:
        area = AREAS.get(d["feature"], d["feature"])
        if area != area_atual:
            area_atual = area
            a("")
            a("**%s**" % area)
            a("")
        vezes = len(contagem[d["mensagem"]])
        n = numero[d["mensagem"]]
        marca = "  ·  **%d lugares** (%d.1 a %d.%d)" % (vezes, n, n, vezes) if vezes > 1 else ""
        falta = "  ⚠️" if d["mensagem"] not in CONTEXTO else ""
        a("%d. %s%s%s" % (n, esc(d["mensagem"]), marca, falta))
    a("")
    a("Frases que aparecem em mais de um lugar têm **subnúmero por ocorrência**. Para pedir que uma")
    a("delas vire frase própria, cite o subnúmero: *\"desmembra o 4.3\"*.")
    a("")

    a("---")
    a("")
    a("## As frases")
    a("")
    a("Agrupadas por área e na ordem do arquivo, **não em ordem alfabética**: frase solta em ordem")
    a("alfabética é o jeito mais eficiente de não perceber que duas frases da mesma tela se")
    a("contradizem.")
    a("")

    atual = None
    for d in distintas:
        area = AREAS.get(d["feature"], d["feature"])
        if area != atual:
            atual = area
            a("")
            a("## %s" % area)

        ocorrencias = contagem[d["mensagem"]]
        a("")
        a("### %d. %s" % (numero[d["mensagem"]], esc(d["mensagem"])))
        a("")

        descricao = CONTEXTO.get(d["mensagem"])
        if descricao:
            a("**O que a pessoa fez:** %s" % esc(descricao))
        else:
            a("**O que a pessoa fez:** ⚠️ **FALTA DESCREVER.** Acrescente esta frase em"
              " `docs/fontes/erros_contexto.py` e rode o gerador de novo.")
        a("")

        tipos = sorted(set(o["tipo"] for o in ocorrencias))
        for t in tipos:
            status, nota_status = STATUS.get(t, ("—", ""))
            a("- **HTTP** %s · `%s` — %s" % (status, t, nota_status))
        codigos = sorted(set(o["codigo"] for o in ocorrencias if o["codigo"]))
        a("- **Código hoje:** %s" % (" · ".join("`%s`" % c for c in codigos) if codigos
                                     else "**nenhum**, usa o genérico"))

        if interpolado(d["mensagem"]):
            a("- ⚠️ **tem valor interpolado** — o `${...}` não se traduz nem se reordena sem cuidado")
        if any(o.get("de_limites") for o in ocorrencias):
            a("- o número vem de `Limites` (o gerador resolveu a constante): mudar o limite muda a"
              " frase, e a descrição de contexto passa a aparecer como órfã até ser relida")
        campos = sorted(set(c for o in ocorrencias for c in o["campos"]))
        if campos:
            a("- vira **erro de campo** do formulário: %s" % ", ".join("`%s`" % c for c in campos))

        a("")
        n = numero[d["mensagem"]]
        varios = len(ocorrencias) > 1
        if varios:
            a("**Acontece em %d lugares, numerados de %d.1 a %d.%d.** Confira se a mesma frase serve"
              " a todos: às vezes ela está certa numa operação e vaga em outra. Para pedir mudança"
              " em um lugar só, cite o subnúmero." % (len(ocorrencias), n, n, len(ocorrencias)))
            a("")

        # Índice da ocorrência DENTRO da mesma função, para casar com a lista do `CAUSAS`.
        contador = {}
        for k, o in enumerate(ocorrencias, 1):
            chave = "%s.%s" % (o["classe"], o["funcao"] or "?")
            i_na_funcao = contador.get(chave, 0)
            contador[chave] = i_na_funcao + 1

            selo = {
                "exata": "",
                "provavel": " *(rota provável)*",
                "fraca": " *(⚠️ rota inferida por nome em outra área, pode estar errada)*",
            }.get(o["confianca"], "")
            rota = (" · ".join(o["rotas"]) + selo) if o["rotas"] else "sem rota direta (função interna)"
            rotulo = "**%d.%d** " % (n, k) if varios else ""
            a("- %s`%s` → %s" % (rotulo, chave, rota))

            if varios:
                causa = causa_de(d["mensagem"], chave, i_na_funcao)
                if causa:
                    a("  - **causa:** %s" % esc(causa))
                else:
                    a("  - **causa:** ⚠️ **FALTA DESCREVER.** Acrescente em `CAUSAS` no"
                      " `docs/fontes/erros_contexto.py`, sob `\"%s\"` → `\"%s\"`."
                      % (esc(d["mensagem"]), chave))
            if o["guarda"]:
                a("  - guarda: `%s`" % o["guarda"].replace("`", ""))
            if o["comentario"]:
                a("  - nota ao lado: %s" % esc(o["comentario"]))
            if o["kdoc"]:
                a("  - a operação: %s" % esc(o["kdoc"]))
            a("  - <sub>`%s:%d`</sub>" % (o["arquivo"], o["linha"]))

    a("")
    a("---")
    a("")
    a("## Frases internas")
    a("")
    a("**Não precisam de tradução.** O `ErrorMapper` as substitui por \"Erro interno\" antes de")
    a("responder: detalhe de falha interna fica no log, não no fio.")
    a("")
    a("| Frase | Operação | Onde |")
    a("|---|---|---|")
    for x in internos:
        a("| %s | `%s.%s` | `%s:%d` |" % (esc(x["mensagem"]), x["classe"], x["funcao"] or "?", x["curto"], x["linha"]))

    a("")
    a("---")
    a("")
    a("_Gerado por `docs/geradores/gen_erros.py` a partir de `server/src/main`._")

    os.makedirs(os.path.dirname(SAIDA), exist_ok=True)
    with open(SAIDA, "w", encoding="utf-8") as f:
        f.write("\n".join(L) + "\n")

    print("OK: %s" % SAIDA)
    print("   %d frases visíveis (%d distintas), %d internas, %d sem rota" % (
        len(visiveis), len(contagem), len(internos), len(sem_rota)))
    if sem_contexto:
        print("   ⚠️  %d SEM descrição de contexto: %s" % (
            len(sem_contexto), " · ".join(sorted(sem_contexto)[:3]) + ("…" if len(sem_contexto) > 3 else "")))
    if orfas:
        print("   ⚠️  %d descrição(ões) órfã(s): %s" % (
            len(orfas), " · ".join(sorted(orfas)[:3]) + ("…" if len(orfas) > 3 else "")))
    if not sem_contexto and not orfas:
        print("   ✅ contexto sincronizado com o código")

    if "--pdf" in sys.argv:
        sys.path.insert(0, DOCS)
        from genlib import build_pdf
        build_pdf(
            sources=[SAIDA],
            out=os.path.join(DOCS, "pdf", "FitJourney_ERROS.pdf"),
            title="FitJourney — Mensagens de erro do servidor",
        )
        print("OK: FitJourney_ERROS.pdf")


if __name__ == "__main__":
    main()
