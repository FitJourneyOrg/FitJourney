# -*- coding: utf-8 -*-
"""Gera docs/pdf/FitJourney_Contrato_API.pdf a partir de docs/fontes/contrato-api.md. Uso: python3 docs/geradores/gen_contrato.py"""
import os
from genlib import build_pdf
DOCS = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
build_pdf([os.path.join(DOCS, "fontes", "contrato-api.md")],
          os.path.join(DOCS, "pdf", "FitJourney_Contrato_API.pdf"),
          "FitJourney — Contrato da API")
