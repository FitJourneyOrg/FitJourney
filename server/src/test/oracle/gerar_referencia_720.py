# -*- coding: utf-8 -*-
"""Gera `referencia_720.txt`: a saida do oraculo (`motor_v3.gerar`) para os 720 perfis, no formato
que o teste Kotlin `MotorV3Varredura720Test` le linha a linha.

Fatia 9 (2026-10-27). Por que isto existe: `MotorV3FidelidadeCatalogoRealIntegrationTest` guardava
12 perfis escritos a mao no proprio .kt, gerados em 23/10. Veio a Fatia 8, o oraculo mudou, e os 12
divergiam todos -- sem que nenhum teste visse, porque o `integrationTest` nao rodou. Referencia
embutida em teste envelhece em silencio. Este arquivo e GERADO, e `test_motor_v3.py` falha se ele
nao bater com o oraculo de hoje (`ReferenciaDos720`).

Uso (de server/src/test/oracle/):
    python3 gerar_referencia_720.py            # reescreve referencia_720.txt
    python3 gerar_referencia_720.py --verifica # nao escreve; sai 1 se estiver velho

Formato, uma linha por registro, campos separados por `|`:
    # ...                                    comentario (cabecalho)
    P|<id>|<divisao>|<teto>|<capacidade>|<iteracoes>
    V|<dia>|<alvo>|<papel>|<series>|<exercicio>|<rir>     (exercicio vazio = vaga vazia)
    M|<musculo>|<alvo>|<alocado>|<entregue>|<compostos>|<indireto>|<status>|<direto>|<efetivo>|
      <minimo>|<razao>|<atingiu_minimo>|<piso_direto_negado>      (uma linha por musculo)
    D|<chave>=<n>;<chave>=<n>                               (disparos nao zerados, ordenados)
O `<id>` e `OBJETIVO-NIVEL-<dias>d-<minutos>m-<idade>a-AMBIENTE`. Numeros com 6 casas: o Kotlin
compara com tolerancia, porque Python e Java divergem no arredondamento de empates exatos.
"""
import hashlib
import os
import sys

import motor_v3 as mv
from spec_v3 import ALVOS

AQUI = os.path.dirname(os.path.abspath(__file__))
CAMINHO = os.path.join(AQUI, "referencia_720.txt")
CATALOGO = os.path.join(AQUI, "catalogo_banco.csv")


def perfil_id(p) -> str:
    return "%s-%s-%dd-%dm-%da-%s" % (p.objetivo, p.nivel, p.dias, p.minutos, p.idade, p.ambiente)


def _f(x) -> str:
    return "%.6f" % float(x)


def _b(x) -> str:
    return "1" if x else "0"


def perfis() -> list:
    # A matriz tem UMA definicao, no teste -- aqui so se importa, para nao divergir.
    from test_motor_v3 import PERFIS
    return list(PERFIS)


def gerar_texto() -> str:
    sha = hashlib.sha256(open(CATALOGO, "rb").read().replace(b"\r\n", b"\n")).hexdigest()[:16]
    ps = perfis()
    out = [
        "# GERADO por gerar_referencia_720.py -- NAO EDITAR A MAO. Veja o cabecalho do script.",
        "# catalogo_banco.csv sha256[:16]=%s | perfis=%d | alvos=%s" % (sha, len(ps), ",".join(ALVOS)),
    ]
    for p in ps:
        r = mv.gerar(p)
        out.append("P|%s|%s|%s|%s|%d" % (perfil_id(p), r.divisao, _f(r.teto_sessao),
                                         _f(r.capacidade), r.iteracoes))
        for dia, vagas in r.sessoes:
            for v in vagas:
                out.append("V|%s|%s|%s|%d|%s|%s" % (
                    dia, v.alvo, v.papel, v.series,
                    v.exercicio.nome if v.exercicio else "",
                    "" if v.rir is None else v.rir))
        for m in ALVOS:
            a = r.auditoria[m]
            out.append("M|%s|%s|%d|%d|%d|%s|%s|%d|%s|%s|%s|%s|%s" % (
                m, _f(a.alvo), r.alocado.get(m, 0), r.entregue.get(m, 0),
                r.compostos_semana.get(m, 0), _f(r.indireto.get(m, 0.0)), a.status, a.direto,
                _f(a.efetivo), _f(a.minimo), "" if a.razao is None else _f(a.razao),
                _b(a.atingiu_minimo), _b(a.piso_direto_negado)))
        out.append("D|" + ";".join("%s=%d" % (k, n) for k, n in sorted(r.disparos.items()) if n))
    return "\n".join(out) + "\n"


if __name__ == "__main__":
    texto = gerar_texto()
    if "--verifica" in sys.argv:
        atual = open(CAMINHO, encoding="utf-8").read() if os.path.exists(CAMINHO) else ""
        if atual != texto:
            print("referencia_720.txt esta VELHA: rode `python3 gerar_referencia_720.py`")
            sys.exit(1)
        print("referencia_720.txt em dia")
    else:
        with open(CAMINHO, "w", encoding="utf-8", newline="\n") as f:
            f.write(texto)
        print("escrito: %s (%d linhas, %d KB)" % (CAMINHO, texto.count("\n"), len(texto) // 1024))
