# -*- coding: utf-8 -*-
"""
Catálogo de testes do FitJourney — GERADO A PARTIR DO CÓDIGO.

Uso:
    python3 docs/geradores/gen_testes.py            # escreve FitJourney_TESTES.md na raiz
    python3 docs/geradores/gen_testes.py --pdf      # também gera o PDF

POR QUE GERADO E NÃO ESCRITO À MÃO
----------------------------------
São ~250 testes em 44 arquivos. Um catálogo mantido a mão exige lembrar de editá-lo a
cada teste novo — e no primeiro esquecimento ele passa a descrever um sistema que não
existe, o que é pior do que não ter catálogo.

Aqui a fonte é o próprio código: o script varre os source sets de teste, lê os nomes das
funções `@Test` (que no projeto são frases em crase, legíveis como especificação) e
agrupa por CATEGORIA e por módulo.

Consequência prática: renomear um teste atualiza o documento. Apagar um teste o remove.
O catálogo não tem como mentir.
"""
import os
import re
import sys

DOCS = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ROOT = os.path.abspath(os.path.join(DOCS, ".."))

# ---------------------------------------------------------------------------
# As CATEGORIAS são derivadas do caminho, e não de convenção de nome de arquivo.
#
# O source set já diz o que o teste é: `integrationTest` sobe Postgres real,
# `commonTest` roda em KMP, `konsist` afirma arquitetura. Inferir pelo diretório
# significa que criar um teste no lugar certo o classifica sozinho.
# ---------------------------------------------------------------------------
CATEGORIAS = [
    (
        "Arquitetura",
        lambda p: p.startswith("konsist/"),
        "Konsist. Roda PRIMEIRO no CI: violar arquitetura é o defeito mais caro de "
        "desfazer, porque tudo que vem depois se apoia nele.",
    ),
    (
        "Integração — Postgres real",
        lambda p: "/integrationTest/" in p,
        "Testcontainers com Postgres 16. Cobre o que NENHUM dublê prova: índice único, "
        "CHECK, ON DELETE CASCADE e atomicidade de instrução SQL.",
    ),
    (
        "Unidade — servidor",
        lambda p: p.startswith("server/src/test/"),
        "Políticas puras (sem banco, sem HTTP, sem relógio próprio) e serviços contra "
        "dublês que IMPLEMENTAM a regra em vez de devolver constante.",
    ),
    (
        "Unidade — compartilhado (KMP)",
        lambda p: p.startswith("shared/"),
        "Mappers, ViewModels de feature, tradução de erro HTTP e o outbox — que é o "
        "coração do offline (#30).",
    ),
    (
        "Unidade — app Android",
        lambda p: p.startswith("app/"),
        "ViewModels do módulo app. Este source set não compilou entre duas fatias sem "
        "ninguém notar, porque o CI não o executava; hoje executa.",
    ),
]

# `@Test` seguido (com o que houver no meio) de uma função com nome em crase.
TESTE = re.compile(r"@Test[^\n]*\n\s*(?:@\w+[^\n]*\n\s*)*fun\s+`([^`]+)`")
# KDoc no topo do arquivo: a primeira linha de conteúdo serve de resumo da suíte.
RESUMO = re.compile(r"/\*\*(.*?)\*/", re.S)


# Varre só onde teste PODE existir.
#
# Percorrer a raiz inteira custa caro e é frágil: `gifs_exercicios/` tem ~2 mil arquivos de
# mídia e cada `build/` tem dezenas de milhares de intermediários. Listar as raízes é também
# uma decisão explícita — módulo novo com teste precisa aparecer aqui, e isso é bom: o
# catálogo não silencia um módulo por acidente.
RAIZES = [
    "konsist/src/test",
    "server/src/test",
    "server/src/integrationTest",
    "shared",
    "app/androidApp/src/test",
]

PODAR = {"build", ".git", ".gradle", ".idea", "node_modules", "__pycache__"}


def varrer():
    achados = []
    for raiz in RAIZES:
        inicio = os.path.join(ROOT, raiz)
        if not os.path.isdir(inicio):
            continue
        for base, dirs, arquivos in os.walk(inicio):
            dirs[:] = [d for d in dirs if d not in PODAR]   # poda IN PLACE, antes de descer
            rel = os.path.relpath(base, ROOT).replace(os.sep, "/")
            # Em `shared/` há main e test lado a lado; só o segundo interessa.
            if raiz == "shared" and "Test/" not in rel + "/":
                continue
            for arq in arquivos:
                if not arq.endswith("Test.kt"):
                    continue
                caminho = f"{rel}/{arq}"
                with open(os.path.join(base, arq), encoding="utf-8") as f:
                    texto = f.read()
                nomes = TESTE.findall(texto)
                if not nomes:
                    continue
                achados.append((caminho, arq[:-3], resumo_de(texto), nomes))
    return sorted(achados)


def resumo_de(texto):
    """Primeira frase do KDoc da classe — o 'por que esta suíte existe'."""
    for bloco in RESUMO.findall(texto):
        linhas = [
            re.sub(r"^\s*\*ered?\s?", "", l).strip().lstrip("*").strip()
            for l in bloco.strip().splitlines()
        ]
        linhas = [l for l in linhas if l]
        if linhas and not linhas[0].startswith("@"):
            frase = linhas[0]
            if len(frase) > 20:
                return frase
    return ""


def categoria_de(caminho):
    for nome, teste, _ in CATEGORIAS:
        if teste(caminho):
            return nome
    return "Outros"


def gerar():
    achados = varrer()
    total = sum(len(n) for _, _, _, n in achados)

    out = []
    w = out.append
    w("# Catálogo de testes do FitJourney\n")
    w(
        "_Gerado por `docs/gen_testes.py` a partir do código. **Não edite à mão** — "
        "renomear um teste atualiza este documento; apagar um teste o remove._\n"
    )
    w(f"\n**{total} testes em {len(achados)} arquivos.**\n")
    w(
        "\nA estratégia por trás desta suíte — o que está coberto, o que não está e onde "
        "colocar o próximo teste — está em `docs/fontes/mapa-de-testes.md`.\n"
    )

    # Resumo por categoria, antes do detalhe: quem abre o documento quer primeiro saber
    # a forma da suíte, não os 250 nomes.
    w("\n## Resumo\n")
    w("| Categoria | Arquivos | Testes |")
    w("|---|---:|---:|")
    for nome, _, _ in CATEGORIAS + [("Outros", None, None)]:
        arquivos = [a for a in achados if categoria_de(a[0]) == nome]
        if not arquivos:
            continue
        w(f"| {nome} | {len(arquivos)} | {sum(len(a[3]) for a in arquivos)} |")

    for nome, _, descricao in CATEGORIAS + [("Outros", None, "")]:
        arquivos = [a for a in achados if categoria_de(a[0]) == nome]
        if not arquivos:
            continue
        w(f"\n---\n\n## {nome}\n")
        if descricao:
            w(f"{descricao}\n")
        for caminho, classe, resumo, nomes in arquivos:
            w(f"\n### `{classe}` — {len(nomes)} testes\n")
            w(f"`{caminho}`\n")
            if resumo:
                w(f"\n> {resumo}\n")
            w("")
            for n in nomes:
                w(f"- {n}")

    w("\n---\n")
    w(
        "\n_Os nomes acima são os das funções `@Test`. No FitJourney eles são frases em "
        "português, e por isso o catálogo se lê como especificação: cada linha afirma um "
        "comportamento que o sistema garante hoje._\n"
    )

    destino = os.path.join(DOCS, "gerados", "testes.md")
    with open(destino, "w", encoding="utf-8") as f:
        f.write("\n".join(out))
    print(f"OK: {destino} ({total} testes, {len(achados)} arquivos)")
    return destino


if __name__ == "__main__":
    md = gerar()
    if "--pdf" in sys.argv:
        sys.path.insert(0, DOCS)
        from genlib import build_pdf

        build_pdf(
            sources=[md],
            out=os.path.join(DOCS, "pdf", "FitJourney_TESTES.pdf"),
            title="FitJourney — Catálogo de testes",
        )
        print("OK: FitJourney_TESTES.pdf")
