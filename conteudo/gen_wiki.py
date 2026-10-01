# -*- coding: utf-8 -*-
"""
Gera server/src/main/resources/db/migration/R__wiki_conteudo.sql a partir de conteudo/aprender/.

Uso: python3 conteudo/gen_wiki.py

## Por que uma migration REPETÍVEL (R__) e não uma versionada

Conteúdo se reescreve. Uma migration versionada por edição transformaria o histórico de schema num
depósito de correção de vírgula (V62, V63, V64...). O Flyway reaplica uma `R__` sozinho sempre que o
checksum dela muda, que é exatamente o ciclo de quem edita texto.

A alternativa era uma rota de admin, recusada: exigiria inventar um admin de PLATAFORMA (o que o app
tem é admin de desafio) e, sem uma tela de edição, publicar artigo de 800 palavras via Postman.

## O que este gerador recusa

Ele falha em vez de emitir SQL torto. As regras estão em `validar()` e cada uma nasceu de um defeito
real desta base: travessão em texto (a fatia G proibiu e a H ainda achou três), vocabulário de enum
divergindo do CHECK, e slug que não casa com o arquivo (o seed casa POR slug -- slug errado escreve
no artigo errado).
"""
import datetime
import os
import re
import sys

RAIZ = os.path.dirname(os.path.abspath(__file__))
ARTIGOS = os.path.join(RAIZ, "aprender")
SAIDA = os.path.join(
    os.path.dirname(RAIZ), "server", "src", "main", "resources", "db", "migration", "R__wiki_conteudo.sql"
)

# Espelha o enum WikiCategory do shared-contract e o CHECK da V61. Os três têm de concordar.
CATEGORIAS = ("TRAINING", "TECHNIQUE", "NUTRITION", "RECOVERY", "MINDSET")
PISO = "pt-BR"
TRADUZIDOS = ("en",)
SLUG_VALIDO = re.compile(r"^[a-z0-9]+(-[a-z0-9]+)*$")


def ler(caminho):
    """Frontmatter YAML simples (chave: valor) + corpo. Sem dependência externa de propósito."""
    texto = io_ler(caminho)
    if not texto.startswith("---"):
        erro(caminho, "falta o frontmatter (o arquivo tem de comecar com ---)")
    _, frente, corpo = texto.split("---", 2)
    campos = {}
    for linha in frente.strip().splitlines():
        if not linha.strip():
            continue
        if ":" not in linha:
            erro(caminho, "linha de frontmatter sem ':' -> %s" % linha)
        chave, valor = linha.split(":", 1)
        campos[chave.strip()] = valor.strip()
    return campos, corpo.strip()


def io_ler(caminho):
    with open(caminho, encoding="utf-8") as f:
        return f.read()


def erro(caminho, mensagem):
    print("ERRO em %s: %s" % (os.path.basename(caminho), mensagem), file=sys.stderr)
    sys.exit(1)


def validar(caminho, campos, corpo, slugs_vistos, ordens_vistas, destaques):
    slug = campos.get("slug")
    arquivo = os.path.splitext(os.path.basename(caminho))[0]
    if not slug:
        erro(caminho, "frontmatter sem 'slug'")
    if slug != arquivo:
        erro(caminho, "o slug ('%s') tem de ser igual ao nome do arquivo ('%s')" % (slug, arquivo))
    if not SLUG_VALIDO.match(slug):
        erro(caminho, "slug so aceita minusculas, numeros e hifen: '%s'" % slug)
    if slug in slugs_vistos:
        erro(caminho, "slug repetido: '%s'" % slug)
    slugs_vistos.add(slug)

    categoria = campos.get("categoria")
    if categoria not in CATEGORIAS:
        erro(caminho, "categoria '%s' fora do vocabulario %s" % (categoria, list(CATEGORIAS)))

    if not campos.get("titulo"):
        erro(caminho, "frontmatter sem 'titulo'")
    if len(campos["titulo"]) > 200:
        erro(caminho, "titulo passa de 200 caracteres (limite da coluna)")
    if not corpo:
        erro(caminho, "corpo vazio")

    # $wiki$ delimita o bloco DO do SQL gerado (ver `main`). Texto que o contenha fecharia o
    # bloco no meio e o resto do artigo viraria comando solto.
    for onde, texto in (("titulo", campos["titulo"]), ("corpo", corpo)):
        if "$wiki$" in texto:
            erro(caminho, "o %s contem '$wiki$', que e o delimitador do bloco SQL gerado" % onde)

    # A fatia G proibiu travessao em texto do app, e a H ainda achou tres que tinham escapado.
    for onde, texto in (("titulo", campos["titulo"]), ("corpo", corpo)):
        if "\u2014" in texto or "\u2013" in texto:
            erro(caminho, "travessao encontrado no %s -- use virgula ou ponto" % onde)

    try:
        ordem = int(campos.get("ordem", ""))
    except ValueError:
        erro(caminho, "'ordem' tem de ser um inteiro")
    if ordem in ordens_vistas:
        erro(caminho, "ordem %d repetida -- a ordem editorial precisa ser deterministica" % ordem)
    ordens_vistas.add(ordem)

    try:
        datetime.date.fromisoformat(campos.get("atualizado", ""))
    except ValueError:
        erro(caminho, "'atualizado' tem de ser uma data yyyy-MM-dd")

    if campos.get("destaque", "false").lower() == "true":
        destaques.append(slug)

    return slug, categoria, ordem


def sql(valor):
    """Escaping de aspas igual ao da V28/V53 -- e a mesma razao: a carga entra por script."""
    return valor.replace("'", "''")


def main():
    pasta_piso = os.path.join(ARTIGOS, PISO)
    if not os.path.isdir(pasta_piso):
        print("ERRO: %s nao existe" % pasta_piso, file=sys.stderr)
        sys.exit(1)

    slugs_vistos, ordens_vistas, destaques = set(), set(), []
    artigos = []
    for nome in sorted(os.listdir(pasta_piso)):
        # `_modelo.md` e qualquer rascunho com `_` na frente ficam de fora.
        if not nome.endswith(".md") or nome.startswith("_"):
            continue
        caminho = os.path.join(pasta_piso, nome)
        campos, corpo = ler(caminho)
        slug, categoria, ordem = validar(caminho, campos, corpo, slugs_vistos, ordens_vistas, destaques)
        artigos.append(
            {
                "slug": slug, "categoria": categoria, "ordem": ordem,
                "titulo": campos["titulo"], "corpo": corpo,
                "atualizado": campos["atualizado"],
                "destaque": campos.get("destaque", "false").lower() == "true",
                "traducoes": {},
            }
        )

    if len(destaques) > 1:
        print("ERRO: mais de um destaque (%s). So um artigo pode ser o COMECE AQUI." % destaques, file=sys.stderr)
        sys.exit(1)

    por_slug = {a["slug"]: a for a in artigos}
    for locale in TRADUZIDOS:
        pasta = os.path.join(ARTIGOS, locale)
        if not os.path.isdir(pasta):
            continue
        for nome in sorted(os.listdir(pasta)):
            if not nome.endswith(".md") or nome.startswith("_"):
                continue
            caminho = os.path.join(pasta, nome)
            campos, corpo = ler(caminho)
            slug = os.path.splitext(nome)[0]
            if slug not in por_slug:
                erro(caminho, "traducao sem artigo no piso (%s/%s.md nao existe)" % (PISO, slug))
            if not campos.get("titulo") or not corpo:
                # Os dois NOT NULL na mesma linha (V61): prosa traduzida pela metade vira titulo em
                # ingles com corpo em portugues na MESMA tela.
                erro(caminho, "traducao precisa de titulo E corpo -- meia traducao nao entra")
            por_slug[slug]["traducoes"][locale] = (campos["titulo"], corpo)

    linhas = [
        "-- GERADO por conteudo/gen_wiki.py. NAO EDITE A MAO -- edite os .md e rode o gerador.",
        "-- Migration REPETIVEL: o Flyway reaplica sozinho quando este arquivo muda.",
        "--",
        "-- ====================================================================",
        "-- POR QUE O BLOCO `DO` COM GUARDA DE EXISTENCIA",
        "-- ====================================================================",
        "--",
        "-- Migration repetivel roda em TODO migrate -- inclusive num migrate PARCIAL, com `target`",
        "-- anterior a V61, que e quando a tabela deste seed ainda nem existe. O",
        "-- `DisplayNameBackfillIntegrationTest` faz exatamente isso de proposito: migra ate a V34,",
        "-- insere usuarios como eram antes da coluna, e so entao roda a V35 pra exercitar o backfill.",
        "-- Sem a guarda, o seed estourava ali com `relation \"wiki_articles\" does not exist` e",
        "-- derrubava a suite inteira (achado em 2026-09-30, no primeiro artigo real).",
        "--",
        "-- > Migration repetivel nao pode assumir que o schema dela ja nasceu.",
        "--",
        "-- O PL/pgSQL planeja cada comando na PRIMEIRA execucao, entao o RETURN antecipado impede",
        "-- que os comandos abaixo sejam sequer planejados quando a tabela nao existe.",
        "",
        "DO $wiki$",
        "BEGIN",
        "IF to_regclass('public.wiki_articles') IS NULL THEN",
        "    RAISE NOTICE 'wiki_articles ainda nao existe (migrate parcial, anterior a V61): seed do acervo pulado';",
        "    RETURN;",
        "END IF;",
        "",
        "-- Artigo apagado do conteudo tem de sumir do banco, senao vira fantasma no acervo.",
        "DELETE FROM wiki_articles WHERE slug NOT IN (%s);"
        % (", ".join("'%s'" % sql(a["slug"]) for a in artigos) or "''"),
        "",
        "-- Limpa o destaque antes de reatribuir: o indice unico parcial da V61 recusaria dois",
        "-- durante a troca de um artigo para outro.",
        "UPDATE wiki_articles SET featured = FALSE;",
        "",
    ]

    for a in sorted(artigos, key=lambda x: x["ordem"]):
        linhas.append(
            "INSERT INTO wiki_articles (id, slug, category, title, body, featured, order_index, updated_at)\n"
            "VALUES (gen_random_uuid(), '%s', '%s', '%s', '%s', %s, %d, TIMESTAMP '%s 00:00:00')\n"
            "ON CONFLICT (slug) DO UPDATE SET\n"
            "    category = EXCLUDED.category, title = EXCLUDED.title, body = EXCLUDED.body,\n"
            "    featured = EXCLUDED.featured, order_index = EXCLUDED.order_index,\n"
            "    updated_at = EXCLUDED.updated_at;"
            % (
                sql(a["slug"]), a["categoria"], sql(a["titulo"]), sql(a["corpo"]),
                "TRUE" if a["destaque"] else "FALSE", a["ordem"], a["atualizado"],
            )
        )
        linhas.append("")

    linhas.append("-- Traducao que sumiu do conteudo tambem tem de sumir do banco.")
    for locale in TRADUZIDOS:
        com_traducao = [a["slug"] for a in artigos if locale in a["traducoes"]]
        linhas.append(
            "DELETE FROM wiki_article_translations WHERE locale = '%s' AND article_id IN\n"
            "    (SELECT id FROM wiki_articles WHERE slug NOT IN (%s));"
            % (locale, ", ".join("'%s'" % sql(s) for s in com_traducao) or "''")
        )
    linhas.append("")

    for a in sorted(artigos, key=lambda x: x["ordem"]):
        for locale, (titulo, corpo) in sorted(a["traducoes"].items()):
            linhas.append(
                "INSERT INTO wiki_article_translations (article_id, locale, title, body)\n"
                "SELECT id, '%s', '%s', '%s' FROM wiki_articles WHERE slug = '%s'\n"
                "ON CONFLICT (article_id, locale) DO UPDATE SET\n"
                "    title = EXCLUDED.title, body = EXCLUDED.body;"
                % (locale, sql(titulo), sql(corpo), sql(a["slug"]))
            )
            linhas.append("")

    linhas += ["END", "$wiki$;", ""]

    with open(SAIDA, "w", encoding="utf-8") as f:
        f.write("\n".join(linhas))
    print("SQL gerado: %s (%d artigos)" % (SAIDA, len(artigos)))


if __name__ == "__main__":
    main()
