# -*- coding: utf-8 -*-
"""(Era gen_especificacao.py, renomeado em 2026-10-07 — a fonte virou docs/regras/.)

Gera docs/pdf/FitJourney_Regras.pdf a partir de docs/regras/ (um arquivo por área, desde a
reestruturação de 2026-10-07 — era docs/fontes/especificacao-logica.md um arquivo único, depois
docs/fontes/especificacao/, renomeado pra docs/regras/ no mesmo dia; o PDF se chamava
FitJourney_Especificacao_Logica.pdf até 2026-10-07, renomeado junto pra bater com o nome novo).
Uso: python3 docs/geradores/gen_regras.py"""
import os
from genlib import build_pdf
DOCS = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PASTA = os.path.join(DOCS, "regras")

# Ordem fixa (não é alfabética pra não depender de zero-padding): README primeiro como índice,
# depois as áreas na mesma ordem em que estavam no arquivo único original.
ARQUIVOS = [
    "README.md",
    "00-regras-gerais.md",
    "01-motor-de-geracao.md",
    "02-edicao-de-programa.md",
    "03-agenda-e-cronograma.md",
    "04-execucao-de-treino.md",
    "05-offline.md",
    "06-gamificacao.md",
    "07-checkin-e-moderacao.md",
    "08-notificacoes-gatilhos-grupo.md",
    "09-comentarios-e-reacoes.md",
    "10-amizades.md",
    "11-notificacoes-sistema.md",
    "12-analise-de-progressao.md",
    "13-idioma.md",
    "14-privacidade-e-seguranca.md",
]

build_pdf([os.path.join(PASTA, nome) for nome in ARQUIVOS],
          os.path.join(DOCS, "pdf", "FitJourney_Regras.pdf"),
          "FitJourney — Regras")
