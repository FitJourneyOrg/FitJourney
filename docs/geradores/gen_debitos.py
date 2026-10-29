# -*- coding: utf-8 -*-
"""
Gera o PDF dos débitos técnicos a partir de docs/fontes/debitos.md.
Uso: python3 docs/geradores/gen_debitos.py   (saída: docs/pdf/FitJourney_DEBITOS.pdf)
"""
import os
from genlib import build_pdf

DOCS = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
build_pdf(
    sources=[os.path.join(DOCS, "fontes", "debitos.md")],
    out=os.path.join(DOCS, "pdf", "FitJourney_DEBITOS.pdf"),
    title="FitJourney — Débitos técnicos",
)
