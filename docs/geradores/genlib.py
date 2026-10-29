# -*- coding: utf-8 -*-
"""
Motor de render compartilhado dos geradores de PDF do FitJourney.
Suporta o subconjunto de Markdown dos fragmentos: #/##/### títulos, **negrito**,
`code`, listas '- ', tabelas GFM, blockquote '>', regra '---'.

Use `build_pdf(sources, out, title)` a partir de um gerador fino (ex.: gen_debitos.py).
"""
import os, re, html
from reportlab.lib.pagesizes import A4
from reportlab.lib.units import mm
from reportlab.lib import colors
from reportlab.platypus import (SimpleDocTemplate, Paragraph, Spacer, Table,
                                TableStyle, HRFlowable, PageBreak)
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.enums import TA_LEFT

NAVY = colors.HexColor("#1F2A44"); LIGHT = colors.HexColor("#EEF1F6"); GREY = colors.HexColor("#666666")
S = getSampleStyleSheet()
H1 = ParagraphStyle("h1", parent=S["Title"], textColor=NAVY, fontSize=20, spaceBefore=6, spaceAfter=6, alignment=TA_LEFT)
H2 = ParagraphStyle("h2", parent=S["Heading2"], textColor=NAVY, fontSize=13, spaceBefore=10, spaceAfter=4)
H3 = ParagraphStyle("h3", parent=S["Heading3"], textColor=NAVY, fontSize=11, spaceBefore=8, spaceAfter=3)
BODY = ParagraphStyle("body", parent=S["Normal"], fontSize=9.3, leading=13, spaceAfter=4)
QUOTE = ParagraphStyle("quote", parent=BODY, textColor=GREY, fontSize=8.4, leftIndent=6, spaceAfter=6)
BULLET = ParagraphStyle("bul", parent=BODY, leftIndent=10, bulletIndent=0)
CELL = ParagraphStyle("cell", parent=S["Normal"], fontSize=8.0, leading=10)
CELLH = ParagraphStyle("cellh", parent=CELL, textColor=colors.white, fontName="Helvetica-Bold")


def inline(t):
    original = t          # guardado em markdown: é isto que a pessoa procura com Ctrl+F
    t = html.escape(t)

    # Trecho em `crase` sai de cena ANTES de negrito/itálico e volta no fim. Sem isto, um
    # identificador legítimo como `dev.rafael.*` vira abertura de itálico e o parser do
    # ReportLab estoura com tags cruzadas — foi exatamente o que aconteceu em 2026-08-19.
    codigos = []

    def guardar(m):
        codigos.append(m.group(1))
        return "\x00%d\x00" % (len(codigos) - 1)

    t = re.sub(r"`(.+?)`", guardar, t)

    # `***assim***` ANTES de `**` e `*`, senão o negrito come dois asteriscos, sobra um solto, e o
    # itálico fecha depois do </b> — tags cruzadas de novo, a mesma família de 2026-08-19.
    t = re.sub(r"\*\*\*(.+?)\*\*\*", r"<b><i>\1</i></b>", t)
    t = re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", t)
    t = re.sub(r"(?<!\*)\*([^*\s][^*]*?)\*(?!\*)", r"<i>\1</i>", t)   # itálico DEPOIS do negrito
    t = re.sub(r"\[(.+?)\]\((.+?)\)", r"\1", t)  # link -> texto

    conferir_aninhamento(t, original)

    t = re.sub(r"\x00(\d+)\x00", lambda m: '<font face="Courier">%s</font>' % codigos[int(m.group(1))], t)
    return t


def conferir_aninhamento(marcado, original):
    """
    Falha AQUI, e não lá dentro do ReportLab.

    Ênfase aninhada que o conversor não sabe montar (`**a *b* c**` com o fechamento na ordem
    errada) vira `<b>...<i>...</b></i>`. O ReportLab recusa, e o que chega ao console são 60
    linhas de traceback terminando num `Parse error: saw </b> instead of expected </i>` — que está
    certo e não diz NEM o arquivo NEM o trecho.

    Esta checagem custa uma varredura e devolve o texto original em markdown, que é o que a pessoa
    vai procurar com Ctrl+F.

    > **Mensagem de erro que não diz onde procurar transfere o diagnóstico inteiro para quem lê.**
    """
    pilha = []
    for barra, tag in re.findall(r"<(/?)(b|i)>", marcado):
        if not barra:
            pilha.append(tag)
        elif not pilha or pilha.pop() != tag:
            raise ValueError(
                "ênfase aninhada que o gerador não sabe montar.\n"
                "  trecho: %s\n"
                "  saída:  %s\n"
                "  Escreva `**negrito**` e `*itálico*` em trechos SEPARADOS, ou `***os dois***` "
                "no mesmo par." % (original.strip()[:160], marcado.strip()[:160])
            )
    if pilha:
        raise ValueError(
            "ênfase aberta e não fechada.\n  trecho: %s" % original.strip()[:160]
        )


def render_md(path, story):
    """
    Renderiza um fragmento .md.

    JUNTA LINHAS ANTES DE RENDERIZAR. Os fontes são quebrados em ~100 colunas para o diff do git
    ficar legível, mas a quebra é do arquivo, não do texto. Renderizar linha a linha (como era até
    2026-08-19) tinha dois defeitos: `**negrito que atravessava a quebra**` saía com os asteriscos
    literais no PDF, e cada linha virava um parágrafo com `spaceAfter` próprio, o que espaçava a
    prosa como se fosse lista.
    """
    lines = open(path, encoding="utf-8").read().splitlines()
    buf, modo = [], None   # modo: "body" | "bullet" | "quote"

    def flush():
        nonlocal buf, modo
        if not buf:
            return
        texto = inline(" ".join(s.strip() for s in buf))
        if modo == "bullet":
            story.append(Paragraph(texto, BULLET, bulletText="•"))
        elif modo == "quote":
            story.append(Paragraph(texto, QUOTE))
        else:
            story.append(Paragraph(texto, BODY))
        buf, modo = [], None

    i = 0
    while i < len(lines):
        ln = lines[i]
        if ln.strip() == "":
            flush(); i += 1; continue
        if ln.startswith("| "):  # bloco de tabela
            flush()
            block = []
            while i < len(lines) and lines[i].startswith("|"):
                block.append(lines[i]); i += 1
            rows = []
            for r, raw in enumerate(block):
                cells = [c.strip() for c in raw.strip().strip("|").split("|")]
                if r == 1 and set("".join(cells)) <= set("-: "):  # separador
                    continue
                sty = CELLH if r == 0 else CELL
                rows.append([Paragraph(inline(c), sty) for c in cells])
            ncol = max(len(r) for r in rows)
            width = 178*mm
            tb = Table(rows, colWidths=[width/ncol]*ncol)
            tb.setStyle(TableStyle([
                ("BACKGROUND",(0,0),(-1,0),NAVY),
                ("GRID",(0,0),(-1,-1),0.3,colors.HexColor("#CBD2DE")),
                ("ROWBACKGROUNDS",(0,1),(-1,-1),[colors.white,LIGHT]),
                ("VALIGN",(0,0),(-1,-1),"TOP"),
                ("TOPPADDING",(0,0),(-1,-1),3),("BOTTOMPADDING",(0,0),(-1,-1),3),
                ("LEFTPADDING",(0,0),(-1,-1),4),("RIGHTPADDING",(0,0),(-1,-1),4),
            ]))
            story += [tb, Spacer(1,6)]; continue
        if ln.startswith("### "): flush(); story.append(Paragraph(inline(ln[4:]), H3))
        elif ln.startswith("## "): flush(); story.append(Paragraph(inline(ln[3:]), H2))
        elif ln.startswith("# "): flush(); story.append(Paragraph(inline(ln[2:]), H1))
        elif ln.startswith(">"):
            if modo != "quote": flush()
            modo = "quote"; buf.append(ln.lstrip("> ").strip())
        elif ln.strip() == "---":
            flush()
            story.append(HRFlowable(width="100%", thickness=0.5, color=colors.HexColor("#CBD2DE"), spaceBefore=3, spaceAfter=3))
        elif ln.lstrip().startswith("- ") or ln.lstrip().startswith("* "):
            flush(); modo = "bullet"; buf.append(ln.lstrip()[2:])
        elif modo in ("bullet", "quote") and not ln[:1].isspace():
            # Linha colada num item/citação SEM indentação é parágrafo novo, não continuação.
            flush(); modo = "body"; buf.append(ln)
        else:
            if modo is None: modo = "body"
            buf.append(ln)
        i += 1
    flush()


def build_pdf(sources, out, title):
    """sources: lista de caminhos .md (em ordem). out: caminho do PDF. title: metadados."""
    story = []
    for idx, p in enumerate(sources):
        if not os.path.exists(p):
            continue
        if idx > 0:
            story.append(PageBreak())
        render_md(p, story)
    SimpleDocTemplate(out, pagesize=A4, topMargin=15*mm, bottomMargin=15*mm,
                      leftMargin=15*mm, rightMargin=15*mm, title=title).build(story)
    print("PDF gerado:", out)
    return out
