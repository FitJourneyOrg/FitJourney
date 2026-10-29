# -*- coding: utf-8 -*-
"""Gera `referencia_progressao.txt`: a saida do oraculo (`progressao_v3.py`) sobre GRADES de entradas,
no formato que o teste Kotlin `ProgressaoContraOOraculoTest` le linha a linha.

Fatia 10 (2026-10-28). Mesma ideia da `referencia_720.txt` aplicada ao nucleo de progressao:
referencia GERADA, nunca editada a mao, com teste de frescor (`test_progressao_v3.py`). Aqui nao ha
catalogo: o nucleo e funcao pura, entao a varredura e sobre ENTRADAS (cargas, RIRs, historicos,
dias de pausa), nao sobre perfis. O Kotlin reproduz cada linha e compara.

Uso (de server/src/test/oracle/):
    python3 gerar_referencia_progressao.py            # reescreve referencia_progressao.txt
    python3 gerar_referencia_progressao.py --verifica # nao escreve; sai 1 se estiver velho

Formato, uma linha por caso, campos separados por `|` (vazio = ausente; booleano 0/1; numero com 6
casas -- o Kotlin compara com tolerancia). O primeiro campo diz o que e:
    PI|x|incremento|resultado                                  para_incremento
    SF|excesso|barraLivre|salto                                salto_da_faixa
    PC|carga|rir|serieN|alvo|barraLivre|inc|cargaNova|terminou passo_calibracao
    RI|carga|rirPrimeira|alvo|inc|cargaNova|anulada            reducao_intrasessao
    RM|carga|reps|rir|papel|um_rm                              estimar_um_rm (1 serie)
    DE|objetivo|papel|inc|barra|c|exp|pos|sem|series|decisao|c'|exp'|pos'|umrm'|sem'     decidir
    SD|base|deload|series                                      series_do_dia
    DP|nivel|numero|semana|deload                              deload_programado
    IN|historico|diagnostico|situacao                          situacao_iniciante
    CS|numero|semana|cons|numero'|semana'|cons'                concluir_semana
    RE|dias|numero|semana|cons|idade|numero'|semana'|cons'|recalibra|fator    retorno_apos_pausa
    AR|c|exp|pos|umrm|sem|inc|dias|c'|exp'|pos'|umrm'|sem'     aplicar_retorno
    PD|historico|diagnostico                                   diagnostico_promocao_iniciante (Fatia 11)
    PP|diagnosticos|diagnostico                                diagnostico_promocao_do_perfil (Fatia 11)
    PA|umRmInicio|umRmFim|reducoes|limite|diagnostico           diagnostico_promocao_avancado (Fatia 11)
    PN|atual|recusa|pode                                        pode_sugerir_promocao_de_novo (Fatia 11)
`series` = `reps:rir;reps:rir;...`; RIR nulo (nao informado) = `-` (em series e nos campos `rir`). `historico` = letras S (SOBE) M (MANTEM) D (DESCE) L (DELOAD).
`diagnosticos` (PP) = letras N (None) P (PROMOVER) R (RECUPERACAO) S (RECUPERACAO_SEM_RETOMADA), uma por ancora, pode ser vazio.
"""
import itertools
import os
import sys

import progressao_v3 as P
import spec_v3 as S
from progressao_v3 import Estado, Mesociclo, Serie

AQUI = os.path.dirname(os.path.abspath(__file__))
CAMINHO = os.path.join(AQUI, "referencia_progressao.txt")

PAPEIS = ("COMPOSTO", "ACESSORIO", "ISOLAMENTO")
OBJETIVOS = tuple(S.PRESCRICAO)


def _f(x) -> str:
    return "" if x is None else "%.6f" % float(x)


def _o(x) -> str:
    """Optional[str] (os diagnosticos de promocao, que ja sao PROMOVER/RECUPERACAO/etc.)."""
    return "" if x is None else str(x)


def _b(x) -> str:
    return "1" if x else "0"


def _i(x) -> str:
    return "" if x is None else str(int(x))


def _r(x) -> str:
    return "-" if x is None else str(int(x))


def _series(ss) -> str:
    return ";".join("%d:%s" % (s.reps, _r(s.rir)) for s in ss)


def _hist(letras: str) -> list:
    return [{"S": P.SOBE, "M": P.MANTEM, "D": P.DESCE, "L": P.DELOAD}[c] for c in letras]


def padroes(lo: int, hi: int, alvo: int) -> list:
    """Nove sessoes-tipo em torno da faixa e do RIR alvo: topo, topo mais facil, topo muito mais
    facil, uma serie fora do topo, abaixo da faixa (no alvo / mais dificil), meio da faixa, uma
    serie mais dificil que o alvo, acima do topo, e a mistura."""
    return [
        [Serie(hi, alvo)] * 3,
        [Serie(hi, alvo + 1)] * 3,
        [Serie(hi, alvo + 4)] * 3,
        [Serie(hi, alvo), Serie(hi, alvo), Serie(hi - 1, alvo)],
        [Serie(lo - 2, alvo)] * 3,
        [Serie(lo - 2, max(0, alvo - 1))] * 3,
        [Serie(lo + 1, alvo)] * 3,
        [Serie(hi, alvo), Serie(hi, max(0, alvo - 1)), Serie(hi, alvo)],
        [Serie(hi + 3, alvo + 2)] * 3,
        # RIR nulo (D-3): so as regras por repeticao
        [Serie(hi, None)] * 3,
        [Serie(lo - 2, None)] * 3,
        [Serie(hi, alvo + 4), Serie(hi, None), Serie(hi, alvo + 4)],
    ]


def linhas() -> list:
    out = []
    # --- arredondamento
    for inc in (0.5, 1.0, 2.0, 2.5, 5.0):
        for k in range(1, 241):
            x = k * 0.25
            out.append("PI|%s|%s|%s" % (_f(x), _f(inc), _f(P.para_incremento(x, inc))))
    # --- salto da faixa
    for ex, barra in itertools.product(range(-2, 9), (False, True)):
        out.append("SF|%d|%s|%s" % (ex, _b(barra), _f(P.salto_da_faixa(ex, barra))))
    # --- calibracao
    for carga, rir, n, alvo, barra, inc in itertools.product(
            (10.0, 20.0), list(range(0, 11)) + [None], (1, 2, 3), (1, 2, 3), (False, True), (1.0, 2.5)):
        p = P.passo_calibracao(carga, rir, n, alvo, barra, inc)
        out.append("PC|%s|%s|%d|%d|%s|%s|%s|%s" % (_f(carga), _r(rir), n, alvo, _b(barra), _f(inc), _f(p.carga), _b(p.terminou)))
    # --- reducao no dia
    for carga, rir, alvo, inc in itertools.product((8.0, 10.0, 20.0, 60.0, 100.0, 102.5), list(range(0, 6)) + [None], (1, 2, 3), (1.0, 2.0, 2.5, 5.0)):
        nova, anulada = P.reducao_intrasessao(carga, rir, alvo, inc)
        out.append("RI|%s|%s|%d|%s|%s|%s" % (_f(carga), _r(rir), alvo, _f(inc), _f(nova), _b(anulada)))
    # --- 1RM
    for carga, reps, rir, papel in itertools.product((20.0, 100.0), (5, 8, 12), (0, 2, 4, None), PAPEIS):
        out.append("RM|%s|%d|%s|%s|%s" % (_f(carga), reps, _r(rir), papel,
                                          _f(P.estimar_um_rm(carga, [Serie(reps, rir)], papel))))
    # --- decisao entre sessoes
    for objetivo, papel, barra in itertools.product(OBJETIVOS, PAPEIS, (False, True)):
        lo, hi, _, alvo = P.prescricao(objetivo, papel)
        for (carga, inc), exp, pos, sem in itertools.product(((8.0, 2.0), (60.0, 2.5)), (1, 3, 4), (False, True), (0, 2)):
            for ss in padroes(lo, hi, alvo):
                e = Estado(carga, exp, pos, None, sem)
                n, d = P.decidir(e, ss, papel, objetivo, inc, barra)
                out.append("DE|%s|%s|%s|%s|%s|%d|%s|%d|%s|%s|%s|%d|%s|%s|%d" % (
                    objetivo, papel, _f(inc), _b(barra), _f(carga), exp, _b(pos), sem, _series(ss), d,
                    _f(n.carga), n.exposicoes, _b(n.pos_salto), _f(n.um_rm), n.sem_subir))
    # --- mesociclo
    for base, deload in itertools.product(range(1, 9), (False, True)):
        out.append("SD|%d|%s|%d" % (base, _b(deload), P.series_do_dia(base, deload)))
    for numero, semana, cons in itertools.product((1, 2, 3), (1, 2, 3, 4), (False, True)):
        m = P.concluir_semana(Mesociclo(numero, semana, cons))
        out.append("CS|%d|%d|%s|%d|%d|%s" % (numero, semana, _b(cons), m.numero, m.semana, _b(m.conservador)))
    for nivel, numero, semana in itertools.product(S.NIVEIS, (1, 2), (1, 2, 3, 4)):
        out.append("DP|%s|%d|%d|%s" % (nivel, numero, semana, _b(P.deload_programado(nivel, Mesociclo(numero, semana)))))
    # --- iniciante: TODOS os historicos de ate 5 sessoes/eventos
    for tam in range(0, 6):
        for letras in itertools.product("SMDL", repeat=tam):
            h = "".join(letras)
            for diag in (False, True):
                out.append("IN|%s|%s|%s" % (h, _b(diag), P.situacao_iniciante(_hist(h), diag)))
    # --- retorno apos pausa
    for dias, (numero, semana), cons, idade in itertools.product(
            (0, 1, 13, 14, 15, 20, 27, 28, 29, 60, 200), ((1, 1), (2, 3), (3, 4)), (False, True), (30, 59, 60, 65)):
        r = P.retorno_apos_pausa(dias, Mesociclo(numero, semana, cons), idade)
        out.append("RE|%d|%d|%d|%s|%d|%d|%d|%s|%s|%s" % (
            dias, numero, semana, _b(cons), idade, r.mesociclo.numero, r.mesociclo.semana,
            _b(r.mesociclo.conservador), _b(r.recalibra), _f(r.fator_carga)))
    for (carga, inc), exp, pos, um, dias in itertools.product(
            ((None, 2.5), (8.0, 2.0), (60.0, 2.5), (100.0, 2.5)), (0, 9), (False, True), (None, 140.0), (5, 20, 40)):
        e = Estado(carga, exp, pos, um, 2)
        r = P.retorno_apos_pausa(dias, Mesociclo())
        n = P.aplicar_retorno(e, r, inc)
        out.append("AR|%s|%d|%s|%s|%d|%s|%d|%s|%d|%s|%s|%d" % (
            _f(carga), exp, _b(pos), _f(um), 2, _f(inc), dias, _f(n.carga), n.exposicoes, _b(n.pos_salto), _f(n.um_rm), n.sem_subir))
    # --- diagnostico de promocao do iniciante (Fatia 11): mesma grade de historicos do IN
    for tam in range(0, 6):
        for letras in itertools.product("SMDL", repeat=tam):
            h = "".join(letras)
            out.append("PD|%s|%s" % (h, _o(P.diagnostico_promocao_iniciante(_hist(h)))))
    # --- diagnostico de promocao do perfil (Fatia 11): agregacao de 0 a 4 ancoras
    VALORES_DIAG = {"N": None, "P": P.PROMOVER, "R": P.RECUPERACAO, "S": P.RECUPERACAO_SEM_RETOMADA}
    for n in range(0, 5):
        for combo in itertools.product("NPRS", repeat=n):
            diagnosticos = [VALORES_DIAG[c] for c in combo]
            out.append("PP|%s|%s" % ("".join(combo), _o(P.diagnostico_promocao_do_perfil(diagnosticos))))
    # --- diagnostico de promocao ao avancado (Fatia 11)
    for um_inicio, um_fim, reducoes, limite in itertools.product(
            (None, 50.0, 100.0), (None, 48.0, 49.999999, 50.0, 50.000001, 52.0, 100.0, 101.0),
            range(0, 4), (0, 1, 2)):
        r = P.diagnostico_promocao_avancado(um_inicio, um_fim, reducoes, limite)
        out.append("PA|%s|%s|%d|%d|%s" % (_f(um_inicio), _f(um_fim), reducoes, limite, _o(r)))
    # --- pode sugerir promocao de novo (Fatia 11)
    for atual, recusa in itertools.product(range(1, 6), (None,) + tuple(range(0, 6))):
        out.append("PN|%d|%s|%s" % (atual, _i(recusa), _b(P.pode_sugerir_promocao_de_novo(atual, recusa))))
    return out


def gerar_texto() -> str:
    corpo = linhas()
    cab = ["# GERADO por gerar_referencia_progressao.py -- NAO EDITAR A MAO. Veja o cabecalho do script.",
           "# casos=%d" % len(corpo)]
    return "\n".join(cab + corpo) + "\n"


if __name__ == "__main__":
    texto = gerar_texto()
    if "--verifica" in sys.argv:
        atual = open(CAMINHO, encoding="utf-8").read() if os.path.exists(CAMINHO) else ""
        if atual != texto:
            print("referencia_progressao.txt esta VELHA: rode `python3 gerar_referencia_progressao.py`")
            sys.exit(1)
        print("referencia_progressao.txt em dia")
    else:
        with open(CAMINHO, "w", encoding="utf-8", newline="\n") as f:
            f.write(texto)
        print("escrito: %s (%d linhas, %d KB)" % (CAMINHO, texto.count("\n"), len(texto) // 1024))
