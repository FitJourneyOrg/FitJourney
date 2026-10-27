# -*- coding: utf-8 -*-
"""
Catálogo pt-BR da INTERFACE — GERADO A PARTIR DO `strings.xml` E DO CÓDIGO.

Uso:
    python3 docs/geradores/gen_strings.py            # escreve docs/gerados/strings.md
    python3 docs/geradores/gen_strings.py --pdf      # também gera o PDF

POR QUE GERADO E NÃO ESCRITO À MÃO
----------------------------------
Mesmo argumento do `gen_erros.py`: uma lista mantida a mão faz alguém **traduzir frase que já foi
removida** e **não receber a que entrou**. Com 605 chaves isso deixa de ser risco e vira certeza.

POR QUE ELE EXISTE, SE O `strings.xml` JÁ TEM TUDO
--------------------------------------------------
Porque o `strings.xml` é ordenado pelo PROGRAMADOR e lido pelo TRADUTOR, e as duas leituras querem
coisas diferentes. Três coisas que só existem aqui:

1. **ONDE cada frase aparece**, tirada do código e não do comentário. O comentário diz "botão da
   tela de conta" porque alguém escreveu isso; `conta/ContaScreen.kt:130` é verdade verificável, e
   quando as duas divergem é o comentário que está velho.
2. **Conferência de parâmetros entre o XML e a chamada.** `%1$s` no catálogo e
   `stringResource(R.string.x, a, b)` na tela é um defeito que **o compilador não pega e o Lint
   não pega entre idiomas diferentes** — estoura em runtime, no idioma de quem traduziu.
3. **Os avisos cruzados em destaque.** São cinco pares de texto idêntico que NÃO podem ser
   unificados, e são a única coisa neste catálogo que um tradutor cuidadoso erraria **justamente
   por ser cuidadoso**: ele vê `Sair` duas vezes, deduz duplicata e usa a mesma palavra nas duas.
   Uma é *Leave*, a outra é *Log out*.

REGRA DE PROJETO DESTE SCRIPT: ERRAR PARA MAIS
----------------------------------------------
Herdada do `inventario_strings.py`, e pelo mesmo motivo: **os três defeitos do `gen_erros.py`
foram todos de SUBnotificação**, e subnotificação passa despercebida porque o relatório parece
limpo. Aqui, na dúvida o alerta entra. Um falso positivo custa dez segundos de leitura; um falso
negativo custa uma frase errada em produção, descoberta por um usuário.

> **O tradutor não tem o app aberto. Contexto que não está no documento não existe.**
"""
import io
import os
import re
import sys
from collections import defaultdict
from xml.dom import minidom

DOCS = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ROOT = os.path.abspath(os.path.join(DOCS, ".."))
APP = os.path.join(ROOT, "app", "androidApp", "src", "main")
XML = os.path.join(APP, "res", "values", "strings.xml")
KOTLIN = os.path.join(APP, "kotlin")
SAIDA = os.path.join(DOCS, "gerados", "strings.md")

# ---------------------------------------------------------------------------
# ⚠️ OS AVISOS CRUZADOS — escrito à mão, e é a única parte que não sai do código.
#
# Não sai porque não PODE sair: que `Sair` do desafio e `Sair` da conta sejam palavras diferentes
# em inglês é verdade sobre o PRODUTO, não sobre o código. O script confere que os pares ainda
# existem e que os textos ainda são idênticos — se um deles mudar, o aviso vira ruído e aparece
# como órfão no fim do documento.
# ---------------------------------------------------------------------------
AVISOS_CRUZADOS = [
    (("grupo_detalhe_sair_confirmar", "menu_sair"),
     "Sair do DESAFIO (o grupo continua existindo) × encerrar a SESSÃO da conta.",
     "Leave × Log out"),
    (("programa_detalhe_descanso", "sessao_descanso"),
     "Dia sem treino agendado × o cronômetro entre séries.",
     "Rest day × Rest"),
    (("grupo_detalhe_fazer_checkin", "checkin_enviar"),
     "ABRIR a tela de check-in × ENVIAR o check-in preenchido.",
     "Check in × Submit"),
    (("home_secao_treino_de_hoje", "home_treino_de_hoje"),
     "O RÓTULO do card × o NOME de um treino que o programa não nomeou. Rótulo e dado.",
     "Today × Today's workout"),
    (("nav_aba_treino", "treino_detalhe_titulo_padrao"),
     "A ABA da barra de baixo × o nome de um treino que ninguém nomeou. Rótulo de navegação e dado.",
     "Training × Workout"),
    (("app_name", "login_titulo"),
     "A MARCA (não se traduz, nunca) × o título da tela de entrada.",
     "FitJourney × FitJourney"),
]

TRAVESSAO = ("\u2014", "\u2013")
POSICIONAL = re.compile(r"%(\d+)\$[-#+ 0,(]*\d*(?:\.\d+)?([sdfe])")
QUALQUER_PC = re.compile(r"%[^%]")
LIMITE = re.compile(r"~\s*(\d+)\s*caracteres")


# ---------------------------------------------------------------------------
# Leitura do catálogo
# ---------------------------------------------------------------------------

def texto_de(no):
    return "".join(c.data for c in no.childNodes
                   if c.nodeType in (c.TEXT_NODE, c.CDATA_SECTION_NODE))


def ler_catalogo():
    """
    Devolve (entradas, plurais) na ORDEM DO DOCUMENTO, cada uma com a seção e o comentário.

    A seção sai do comentário-faixa (`==== Nome ====`), que é como o arquivo já se organiza. Sem
    ela o documento vira uma lista de 605 linhas em que ninguém acha nada — e o tradutor precisa
    ler por TELA, porque é assim que o texto vai ser visto.
    """
    raiz = minidom.parse(XML).documentElement
    entradas, plurais = [], []
    secao, comentario, distancia = "Geral", None, 0

    for no in raiz.childNodes:
        if no.nodeType == no.COMMENT_NODE:
            bruto = no.data.strip()
            faixa = re.search(r"={6,}\s*\n\s*(.+?)\s*\n\s*={6,}", bruto)
            if faixa:
                secao = faixa.group(1).strip()
                comentario, distancia = None, 0
                continue
            faixa2 = re.match(r"^=+\s*(.+?)\s*=+$", bruto)
            if faixa2:
                secao = faixa2.group(1).strip()
                comentario, distancia = None, 0
                continue
            comentario, distancia = bruto, 0
            continue

        if no.nodeType != no.ELEMENT_NODE:
            continue

        if no.tagName == "string":
            entradas.append({
                "chave": no.getAttribute("name"),
                "valor": texto_de(no),
                "secao": secao,
                "comentario": comentario,
                "distancia": distancia,
                "plural": False,
            })
            distancia += 1
        elif no.tagName == "plurals":
            formas = {}
            for item in no.getElementsByTagName("item"):
                formas[item.getAttribute("quantity")] = texto_de(item)
            plurais.append({
                "chave": no.getAttribute("name"),
                "formas": formas,
                "secao": secao,
                "comentario": comentario,
                "distancia": distancia,
                "plural": True,
            })
            distancia += 1
        else:
            comentario = None

    return entradas, plurais


# ---------------------------------------------------------------------------
# Leitura do código — ONDE cada chave aparece, e com quantos argumentos
# ---------------------------------------------------------------------------

def sem_comentarios(t):
    t = re.sub(r"/\*.*?\*/", lambda m: "\n" * m.group(0).count("\n"), t, flags=re.S)
    return re.sub(r"//[^\n]*", "", t)


def argumentos_da_chamada(linha_toda, pos_id):
    """
    Conta os argumentos passados DEPOIS do id, varrendo parênteses balanceados.

    Devolve `None` quando a chamada não fecha na mesma varredura (quebrada em várias linhas de um
    jeito que este script não segue) — e `None` significa **não sei**, nunca "zero". Chutar zero
    aqui produziria alerta falso em massa, e alerta falso em massa é como um relatório aprende a
    ser ignorado.
    """
    i = pos_id
    prof = 0
    while i > 0:
        if linha_toda[i] == "(":
            prof += 1
            if prof == 1:
                break
        elif linha_toda[i] == ")":
            prof -= 1
        i -= 1
    if prof != 1:
        return None

    j, nivel, args, viu = i + 1, 0, 0, False
    while j < len(linha_toda):
        c = linha_toda[j]
        if c in "([{":
            nivel += 1
        elif c in ")]}":
            if nivel == 0:
                return args if viu else 0
            nivel -= 1
        elif c == "," and nivel == 0:
            args += 1
        elif not c.isspace():
            viu = True
        j += 1
    return None


def ler_usos():
    """Onde cada chave é referenciada, e quantos argumentos a chamada passa."""
    usos = defaultdict(list)
    for pasta, _, arquivos in os.walk(KOTLIN):
        for nome in sorted(arquivos):
            if not nome.endswith(".kt"):
                continue
            caminho = os.path.join(pasta, nome)
            curto = os.path.relpath(caminho, KOTLIN).replace(os.sep, "/")
            curto = curto.replace("dev/rafael/app/", "")
            bruto = io.open(caminho, encoding="utf-8").read()
            limpo = sem_comentarios(bruto)
            for n, linha in enumerate(limpo.split("\n"), 1):
                for m in re.finditer(r"R\.(string|plurals)\.([A-Za-z0-9_]+)", linha):
                    tipo, chave = m.group(1), m.group(2)
                    args = None
                    if "stringResource(" in linha or "pluralStringResource(" in linha or "getString(" in linha:
                        args = argumentos_da_chamada(linha, m.start())
                        if args is not None:
                            # `argumentos_da_chamada` conta VÍRGULAS de primeiro nível, e o id é
                            # sempre o primeiro argumento — a contagem JÁ é "quantos vêm depois do
                            # id". Descontar o id de novo aqui foi o defeito da primeira rodada:
                            # 51 alertas, todos com a mesma cara `declara N e passa N-1`, todos
                            # falsos. Um alerta que erra sempre para o mesmo lado não é ruído: é
                            # bug do medidor, e um medidor com viés constante é pior que nenhum,
                            # porque parece que está funcionando.
                            # O plural, esse sim, gasta um argumento a mais: o `count`, que escolhe
                            # a forma e não preenche `%1$d`.
                            if tipo == "plurals":
                                args -= 1
                            args = max(args, 0)
                    usos[chave].append({"onde": "%s:%d" % (curto, n), "args": args})
    return usos


# ---------------------------------------------------------------------------
# Análise — aqui é onde se erra para mais
# ---------------------------------------------------------------------------

def parametros(valor):
    """Os parâmetros declarados, na ordem do índice: [(1, 's'), (2, 'd')]."""
    vistos = {}
    for m in POSICIONAL.finditer(valor):
        vistos[int(m.group(1))] = m.group(2)
    return sorted(vistos.items())


def analisar(entradas, plurais, usos):
    alertas = defaultdict(list)
    todos = entradas + plurais

    for e in todos:
        chave = e["chave"]
        valores = [e["valor"]] if not e["plural"] else list(e["formas"].values())
        refs = usos.get(chave, [])

        if not e["comentario"]:
            alertas["Só o nome da seção como contexto"].append(
                (chave, "primeira chave da seção **%s**, sem comentário próprio — o teste "
                        "`toda string esta sob um comentario de contexto` aceita a faixa da seção, "
                        "este documento não: o tradutor lê a linha, não o arquivo" % e["secao"]))

        if not refs:
            alertas["Nunca referenciada no código"].append(
                (chave, "ou é lixo, ou é usada de um jeito que este script não vê (manifest, etc.)"))

        for v in valores:
            if any(t in v for t in TRAVESSAO):
                alertas["Travessão (proibido pelo #37, seção 8)"].append((chave, v))
            if any(c.isalpha() for c in v) and v == v.upper() and sum(c.isalpha() for c in v) > 3:
                alertas["Caixa alta cravada no texto"].append((chave, v))
            achados = len(QUALQUER_PC.findall(v))
            if achados and achados != len(POSICIONAL.findall(v)):
                alertas["Placeholder sem posição (`%s` em vez de `%1$s`)"].append((chave, v))
            if len(v) > 120:
                alertas["Frase longa (confira se cabe na tela)"].append(
                    (chave, "%d caracteres" % len(v)))
            se_pedaco = v.strip()
            if se_pedaco and (se_pedaco[0] in "·•,;:" or se_pedaco[-1] in "·•"):
                alertas["Parece PEDAÇO de frase, não frase"].append(
                    (chave, "`%s` — se o código monta a frase juntando isto com outra coisa, a ordem não é traduzível" % v))

        # O plural precisa das duas formas que o pt-BR usa.
        if e["plural"]:
            faltando = [f for f in ("one", "other") if f not in e["formas"]]
            if faltando:
                alertas["Plural incompleto"].append((chave, "falta " + ", ".join(faltando)))

        # ⭐ A conferência que nem o compilador nem o Lint fazem entre idiomas.
        # No plural, a união de TODAS as formas: `one` costuma escrever "1 dia" por extenso e não
        # declarar `%1$d`, e medir só a primeira forma diria que o plural não tem parâmetro.
        vistos = {}
        for v in valores:
            vistos.update(dict(parametros(v)))
        declarados = sorted(vistos.items())
        indices = [i for i, _ in declarados]
        if indices and indices != list(range(1, len(indices) + 1)):
            alertas["Índices de parâmetro fora de sequência"].append(
                (chave, "declara %s" % ", ".join("%%%d$" % i for i in indices)))
        for r in refs:
            if r["args"] is None:
                continue
            if r["args"] != len(declarados):
                alertas["Parâmetros do código × do catálogo"].append(
                    (chave, "o catálogo declara %d e `%s` passa %d" % (len(declarados), r["onde"], r["args"])))

        # NÃO existe mais alerta de "rótulo curto sem limite declarado".
        #
        # Existiu na primeira rodada e disparou em 243 das 605 chaves. O número é o diagnóstico:
        # 40% do catálogo não é uma dívida, é o formato de um app — botão, aba, chip e título são
        # curtos por natureza. Um alerta que acende em quatro de cada dez linhas não mede defeito,
        # mede que a tela tem botões, e ensina a pular a seção inteira de alertas — inclusive os 51
        # de verdade que estão logo acima dela.
        #
        # A informação continua no documento, mas como REGRA em "Como ler cada linha", que se lê
        # uma vez, em vez de 243 linhas que se leem nunca. As 145 chaves cujo espaço é realmente
        # apertado declaram `~N caracteres` no próprio comentário, e isso aparece na coluna
        # `contexto`, ao lado do texto — que é onde a informação é usada.

    # Textos idênticos.
    por_valor = defaultdict(list)
    for e in entradas:
        por_valor[e["valor"]].append(e["chave"])
    repetidos = {v: ks for v, ks in por_valor.items() if len(ks) > 1}

    return alertas, repetidos


def conferir_avisos(entradas):
    """Os avisos cruzados ainda descrevem a realidade?"""
    mapa = {e["chave"]: e["valor"] for e in entradas}
    validos, quebrados = [], []
    for (a, b), motivo, ingles in AVISOS_CRUZADOS:
        if a not in mapa or b not in mapa:
            quebrados.append(((a, b), "chave não existe mais"))
        elif mapa[a] != mapa[b]:
            quebrados.append(((a, b), "os textos já não são idênticos: `%s` × `%s`" % (mapa[a], mapa[b])))
        else:
            validos.append(((a, b), mapa[a], motivo, ingles))
    return validos, quebrados


# ---------------------------------------------------------------------------
# Documento
# ---------------------------------------------------------------------------

def esc(s):
    return s.replace("|", "\\|").replace("\n", " ")


def resumo_do_comentario(c):
    """A primeira frase útil do comentário, sem as faixas e sem os avisos internos."""
    if not c:
        return ""
    linhas = [l.strip() for l in c.split("\n")]
    linhas = [l for l in linhas if l and not re.match(r"^[=~-]{4,}$", l)]
    if not linhas:
        return ""
    junto = " ".join(linhas)
    junto = re.sub(r"\s+", " ", junto)
    return junto


def main():
    entradas, plurais = ler_catalogo()
    usos = ler_usos()
    alertas, repetidos = analisar(entradas, plurais, usos)
    avisos_ok, avisos_quebrados = conferir_avisos(entradas)

    L = []
    a = L.append

    a("# Catálogo pt-BR da interface")
    a("")
    a("> **GERADO. Não edite este arquivo.** Edite `app/androidApp/src/main/res/values/strings.xml`")
    a("> e rode `python docs/geradores/gen_strings.py`. Uma lista mantida a mão faz alguém traduzir")
    a("> frase que já foi removida e não receber a que entrou.")
    a("")
    a("**%d chaves** — %d `string` e %d `plurals` — em %d seções."
      % (len(entradas) + len(plurais), len(entradas), len(plurais),
         len({e["secao"] for e in entradas + plurais})))
    a("")
    a("## Para que serve")
    a("")
    a("Este é **todo o texto que o usuário lê no app**, fora as três exceções declaradas: os nomes")
    a("de exercício (vêm do banco, fatia H), as nove frases de erro que carregam número do servidor")
    a("(`TextosDeErro.SEM_TEXTO_PROPRIO`) e os textos das notificações (nascem no servidor, #36).")
    a("")
    a("Valide o **português** aqui, enquanto corrigir ainda é editar uma linha.")
    a("")
    a("> **Corrigir a origem custa um. Corrigir depois de traduzido custa N.**")
    a("")
    a("## Como ler cada linha")
    a("")
    a("| Coluna | O que é |")
    a("|---|---|")
    a("| **chave** | O identificador. `<area>_<elemento>`, e a área vem do PACOTE da tela, não do que a frase diz. |")
    a("| **texto** | A frase em pt-BR. É isto que se traduz. |")
    a("| **contexto** | O comentário do `strings.xml`. Diz ONDE aparece e, quando o espaço aperta, qual é o limite. |")
    a("| **onde** | O arquivo e a linha que usam a chave, **tirados do código**. Quando isto discorda do contexto acima, é o comentário que está velho. |")
    a("| **params** | Os parâmetros interpolados. `%1$s` é texto, `%1$d` é número. **A ordem pode mudar na tradução** — é para isso que eles são numerados. |")
    a("")
    a("### A regra do espaço")
    a("")
    a("O Compose não corta texto que não cabe: ele **quebra o botão**. E quem traduz não vê a tela.")
    a("")
    a("- Quando o **contexto** declara `~N caracteres`, N é teto, não sugestão. São %d chaves."
      % sum(1 for e in entradas if e["comentario"] and LIMITE.search(e["comentario"])))
    a("- Nas demais, **o comprimento do pt-BR é a referência**: até uns 30% a mais costuma caber,")
    a("  o dobro não cabe. Frase curta aqui quase sempre é botão, aba, chip ou título de tela.")
    a("- Precisou de mais espaço do que isso? Não invente abreviação: **avise**. Rótulo abreviado")
    a("  errado atravessa o app inteiro e ninguém mais o revisa.")
    a("")

    # -----------------------------------------------------------------
    # O bloco mais importante do documento
    # -----------------------------------------------------------------
    a("## ⚠️ LEIA ISTO ANTES DE TRADUZIR — os pares que parecem duplicata e não são")
    a("")
    a("São %d pares de texto **idêntico em português** que precisam virar palavras **diferentes**"
      % len(avisos_ok))
    a("em outros idiomas. É a única coisa deste catálogo que alguém cuidadoso erraria **por ser**")
    a("**cuidadoso**: vê a mesma palavra duas vezes, conclui que é duplicata e usa uma tradução só.")
    a("")
    a("| As duas chaves | Texto | Não é a mesma coisa | Em inglês |")
    a("|---|---|---|---|")
    for (x, y), valor, motivo, ingles in avisos_ok:
        a("| `%s`<br>`%s` | %s | %s | %s |" % (x, y, esc(valor), esc(motivo), esc(ingles)))
    a("")
    if avisos_quebrados:
        a("### ⚠️ Avisos cruzados que já não descrevem o código")
        a("")
        a("Alguém mexeu num dos lados. **Revise o par e atualize `AVISOS_CRUZADOS` no gerador** —")
        a("um aviso que virou ruído ensina a ignorar os outros.")
        a("")
        for (x, y), motivo in avisos_quebrados:
            a("- `%s` × `%s` — %s" % (x, y, motivo))
        a("")

    # -----------------------------------------------------------------
    # Alertas
    # -----------------------------------------------------------------
    total_alertas = sum(len(v) for v in alertas.values())
    a("## Alertas (%d)" % total_alertas)
    a("")
    a("Este gerador **erra para mais**: na dúvida o alerta entra. Falso positivo custa dez segundos")
    a("de leitura; falso negativo custa uma frase errada em produção, achada por um usuário.")
    a("")
    if not alertas:
        a("Nenhum. As verificações de forma (travessão, caixa alta, placeholder, plural) também são")
        a("invariantes do `CatalogoDeStringsTest` — aqui elas rodam de novo porque **este documento**")
        a("**sai do repositório**, e quem o lê não roda o build.")
        a("")
    for titulo in sorted(alertas):
        itens = alertas[titulo]
        a("### %s (%d)" % (titulo, len(itens)))
        a("")
        for chave, nota in itens:
            a("- `%s` — %s" % (chave, esc(nota)))
        a("")

    # -----------------------------------------------------------------
    # Textos repetidos
    # -----------------------------------------------------------------
    a("## Textos idênticos em mais de uma chave (%d)" % len(repetidos))
    a("")
    a("Duplicata de texto **sobrevive num idioma e diverge no segundo**: o tradutor recebe a mesma")
    a("palavra em pontos diferentes do catálogo e não tem como saber que precisam combinar.")
    a("")
    a("Cada par abaixo é uma decisão registrada, não um descuido — os cinco do topo deste documento")
    a("são os que **não podem** ser unificados. Os demais são enums distintos que coincidem em")
    a("pt-BR, ou a mesma ação em telas de contexto diferente.")
    a("")
    a("| Texto | Chaves |")
    a("|---|---|")
    marcados = {c for par, _, _ in AVISOS_CRUZADOS for c in par}
    for valor in sorted(repetidos):
        chaves = repetidos[valor]
        selo = " ⚠️" if any(c in marcados for c in chaves) else ""
        a("| %s%s | %s |" % (esc(valor), selo, " · ".join("`%s`" % c for c in chaves)))
    a("")

    # -----------------------------------------------------------------
    # O catálogo
    # -----------------------------------------------------------------
    a("---")
    a("")
    a("# O catálogo")
    a("")
    ordem, por_secao = [], defaultdict(list)
    for e in entradas + plurais:
        if e["secao"] not in por_secao:
            ordem.append(e["secao"])
        por_secao[e["secao"]].append(e)

    for secao in ordem:
        itens = por_secao[secao]
        a("## %s (%d)" % (secao, len(itens)))
        a("")
        a("| chave | texto | contexto | onde | params |")
        a("|---|---|---|---|---|")
        for e in itens:
            refs = usos.get(e["chave"], [])
            onde = " · ".join(sorted({r["onde"] for r in refs})[:3]) or "—"
            if len({r["onde"] for r in refs}) > 3:
                onde += " *(+%d)*" % (len({r["onde"] for r in refs}) - 3)
            if e["plural"]:
                texto = "<br>".join("**%s:** %s" % (q, esc(v)) for q, v in sorted(e["formas"].items()))
                ps = parametros(list(e["formas"].values())[0]) if e["formas"] else []
            else:
                texto = esc(e["valor"])
                ps = parametros(e["valor"])
            params = " ".join("`%%%d$%s`" % (i, t) for i, t in ps) or "—"
            a("| `%s`%s | %s | %s | %s | %s |" % (
                e["chave"], " *(plural)*" if e["plural"] else "",
                texto, esc(resumo_do_comentario(e["comentario"]))[:220], onde, params))
        a("")

    a("---")
    a("")
    a("_Gerado por `docs/geradores/gen_strings.py` a partir de `res/values/strings.xml` e de")
    a("`app/androidApp/src/main/kotlin`._")

    os.makedirs(os.path.dirname(SAIDA), exist_ok=True)
    io.open(SAIDA, "w", encoding="utf-8", newline="\n").write("\n".join(L) + "\n")

    print("OK: %s" % SAIDA)
    print("   %d chaves (%d string, %d plurals) em %d seções, %d alertas"
          % (len(entradas) + len(plurais), len(entradas), len(plurais), len(ordem), total_alertas))
    if avisos_quebrados:
        print("   ⚠️  %d aviso(s) cruzado(s) desatualizado(s) — veja o documento" % len(avisos_quebrados))
    else:
        print("   ✅ os %d avisos cruzados ainda batem com o código" % len(avisos_ok))
    for titulo in sorted(alertas):
        print("   • %-52s %d" % (titulo, len(alertas[titulo])))

    if "--pdf" in sys.argv:
        sys.path.insert(0, DOCS)
        from genlib import build_pdf
        build_pdf(
            sources=[SAIDA],
            out=os.path.join(DOCS, "pdf", "FitJourney_STRINGS.pdf"),
            title="FitJourney — Catálogo pt-BR da interface",
        )
        print("OK: FitJourney_STRINGS.pdf")


if __name__ == "__main__":
    main()
