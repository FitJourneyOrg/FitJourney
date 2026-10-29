# -*- coding: utf-8 -*-
"""
Gera o PDF CONSOLIDADO a partir dos fragmentos .md de docs/.
Uso: python3 docs/geradores/gen_docs.py   (saída: docs/pdf/FitJourney_Docs.pdf)
Render compartilhado em genlib.py.
"""
import os, glob
from genlib import build_pdf

DOCS = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# ordem de montagem
# docs/fases/ e docs/baterias/ foram apagadas em 2026-10-07 (conteúdo obsoleto ou duplicado em
# outro lugar — ver CONVENCOES.md e historico/painel-mestre-v42-snapshot.md). docs/referencia/
# apagada no mesmo dia, substituída por docs/erros/ (gerador próprio, gen_erros.py).
order = [os.path.join(DOCS, "README.md"),
         os.path.join(DOCS, "arch", "README.md")]
order += sorted(glob.glob(os.path.join(DOCS, "arch", "**", "ARCH-*.md"), recursive=True))
order += sorted(glob.glob(os.path.join(DOCS, "arch", "**", "fase-*.md"), recursive=True))
order += sorted(glob.glob(os.path.join(DOCS, "propostas", "*.md")))
order += sorted(glob.glob(os.path.join(DOCS, "erros", "*.md")))
order += [os.path.join(DOCS, "fontes", "debitos.md")]           # débitos consolidados

build_pdf(order, os.path.join(DOCS, "pdf", "FitJourney_Docs.pdf"), "FitJourney — Docs consolidados")
