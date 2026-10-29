# -*- coding: utf-8 -*-
"""Gera docs/pdf/FitJourney_Convencoes.pdf a partir de docs/CONVENCOES.md + docs/migrations/INDICE.md.

Substitui o gen_painel.py (removido em 2026-10-07). O Painel Mestre se declarava documento-índice
mas era um log cronológico por fatia, com a mesma função do Handoff e conteúdo duplicado dele.
Sobraram as duas seções que eram só dele — convenções fixadas e o que atravessa a sessão.

Uso: python3 docs/geradores/gen_convencoes.py"""
import os
from genlib import build_pdf
DOCS = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
build_pdf([os.path.join(DOCS, "CONVENCOES.md"),
           os.path.join(DOCS, "migrations", "INDICE.md")],
          os.path.join(DOCS, "pdf", "FitJourney_Convencoes.pdf"),
          "FitJourney — Convenções")
