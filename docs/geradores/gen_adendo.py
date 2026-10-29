# -*- coding: utf-8 -*-
"""Gera docs/pdf/FitJourney_Adendo_Stack.pdf a partir de docs/fontes/adendo-stack.md. Uso: python3 docs/geradores/gen_adendo.py"""
import os
from genlib import build_pdf
DOCS = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
build_pdf([os.path.join(DOCS, "fontes", "adendo-stack.md")],
          os.path.join(DOCS, "pdf", "FitJourney_Adendo_Stack.pdf"),
          "FitJourney — Adendo Stack (em uso)")
