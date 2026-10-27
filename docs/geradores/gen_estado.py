# -*- coding: utf-8 -*-
"""Gera docs/pdf/FitJourney_Estado.pdf a partir de docs/ESTADO.md.

Substitui o gen_handoff.py (removido em 2026-10-07). O Handoff tinha 1983 linhas e virava um PDF
de 30+ páginas das quais ~80% eram fatias já fechadas — histórico no caminho crítico de leitura.
O histórico agora é .md em docs/historico/ e NÃO gera PDF: o git já é o arquivo, e PDF de log
cronológico ninguém abre.

Uso: python3 docs/geradores/gen_estado.py"""
import os
from genlib import build_pdf
DOCS = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
build_pdf([os.path.join(DOCS, "ESTADO.md")],
          os.path.join(DOCS, "pdf", "FitJourney_Estado.pdf"),
          "FitJourney — Estado")
