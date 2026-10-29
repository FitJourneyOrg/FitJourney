# -*- coding: utf-8 -*-
"""
Regenera TODOS os PDFs de uma vez.

    python docs/geradores/gen_tudo.py            # tudo, menos o catálogo
    python docs/geradores/gen_tudo.py --catalogo # inclui o catálogo (exige o Postgres de pé)

Existe porque fechar uma fatia significava rodar onze comandos na mão, e a única forma de
descobrir que um foi esquecido era reparar na data de um PDF. Foi o que aconteceu com o
`FitJourney_TESTES.pdf`: o `gen_testes.py` só o gera com `--pdf`, e ele ficou três dias para trás.

O catálogo fica de fora por padrão: ele consulta o Postgres direto e falha sem o container,
o que derrubaria a regeneração inteira por causa do único documento que quase nunca muda.
"""
import os
import subprocess
import sys

AQUI = os.path.dirname(os.path.abspath(__file__))   # docs/geradores

# A ordem importa só no fim: o consolidado varre os ADRs e os débitos, então roda por último.
GERADORES = [
    # gen_handoff.py e gen_painel.py removidos em 2026-10-07 (reestruturação): o Handoff foi
    # desmembrado em docs/historico/ e o Painel Mestre encerrado. O que sobrou de leitura
    # obrigatória é o ESTADO.md; o que sobrou de permanente é o CONVENCOES.md. O histórico
    # não gera PDF de propósito — ver o docstring do gen_estado.py.
    ("gen_estado.py", []),
    ("gen_convencoes.py", []),
    ("gen_contrato.py", []),
    ("gen_regras.py", []),   # era gen_especificacao.py; a fonte é docs/regras/ desde 2026-10-07
    ("gen_documentacao.py", []),
    ("gen_adendo.py", []),
    # gen_fase6.py e gen_relatorio.py removidos em 2026-10-07: as fontes (fase-6-decisoes.md,
    # relatorio-testes.md) foram apagadas — zero pendência, conteúdo já promovido pra regras/ e
    # debitos.md. Manter a chamada aqui geraria PDF de fonte inexistente em silêncio
    # (build_pdf pula arquivo que não existe sem avisar — é o mesmo defeito que o gen_docs.py já
    # teve com fases/ e baterias/).
    ("gen_testes.py", ["--pdf"]),   # sem a flag ele gera só o .md — o defeito que motivou este script
    # Catálogo de mensagens de erro (fatia G, ARCH #37). Varre `server/src/main` e é o documento
    # que vai para quem traduz. Entra aqui pelo mesmo motivo dos outros: um catálogo que só é
    # regenerado quando alguém lembra passa a descrever um sistema que não existe.
    ("gen_erros.py", ["--pdf"]),
    # Catálogo pt-BR da INTERFACE (fatia G.3, ARCH #37). Irmão do de cima: aquele é o texto que o
    # servidor manda, este é o texto que o cliente tem. Varre `res/values/strings.xml` e o Kotlin
    # das telas, e por isso ENVELHECE A CADA TELA NOVA — é o documento desta lista com a maior
    # chance de sair de sincronia sem ninguém notar, porque ninguém abre 605 frases para conferir.
    ("gen_strings.py", ["--pdf"]),
    ("gen_debitos.py", []),
    ("gen_docs.py", []),
]

if "--catalogo" in sys.argv:
    GERADORES.insert(-1, ("gen_catalogo.py", []))

falhas = []
for script, args in GERADORES:
    print(f"\n>>> {script} {' '.join(args)}")
    r = subprocess.run([sys.executable, os.path.join(AQUI, script), *args], cwd=AQUI)
    if r.returncode != 0:
        falhas.append(script)

print("\n" + "=" * 60)
if falhas:
    print("FALHARAM: " + ", ".join(falhas))
    sys.exit(1)
print(f"OK — {len(GERADORES)} documentos regenerados em docs/pdf/")
