# -*- coding: utf-8 -*-
"""Gera docs/pdf/FitJourney_Documentacao_Completa.pdf a partir de docs/fontes/documentacao-completa.md. Uso: python3 docs/geradores/gen_documentacao.py"""
import os
from genlib import build_pdf
DOCS = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
build_pdf([os.path.join(DOCS, "fontes", "documentacao-completa.md")],
          os.path.join(DOCS, "pdf", "FitJourney_Documentacao_Completa.pdf"),
          "FitJourney — Documentação Completa")
