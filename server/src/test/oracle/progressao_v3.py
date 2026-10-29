# -*- coding: utf-8 -*-
"""Motor v3, Fatia 10 -- MESOCICLO E PROGRESSAO no oraculo.

Nucleo puro, sem catalogo e sem relogio: tudo aqui e funcao de (estado, entrada) -> (estado novo,
decisao). A fonte dos numeros e `spec_v3.py` (secao "Fatia 10"); a fonte do COMPORTAMENTO e a
especificacao (`propostas/motor-v3-ESPECIFICACAO.md`, "Carga e progressao" e "Progressao e
mesociclo"). O Kotlin (`engine.v3.Progressao`, `Mesociclo`, `Calibracao`, `RetornoAposPausa`) se
prova contra `referencia_progressao.txt`, que `gerar_referencia_progressao.py` gera DAQUI.

Decisoes do Rafael (2026-10-28) incorporadas:
  - reducao de 4% FIXO por ponto de RIR abaixo do alvo (faixa 3-5% da literatura; contradicao
    interna da spec fechada por decisao -- nao reabrir);
  - retorno apos pausa em TRES faixas (uma regra, tres ramos);
  - iniciante: troca de ancora por estagnacao e EXCECAO NOMEADA a `[REGRA]` de exercicios fixos;
  - continuidade: `semana_do_programa` reaproveita a saida do `preencher()` -- nunca re-roda.

Marcadores `[PROPOSTA]` = a spec nao decide; escolhi o que menos inventa e o registrei.
"""
import math
from dataclasses import dataclass, replace
from typing import Optional

import spec_v3 as S

COMPOSTO_PAPEIS = ("COMPOSTO", "ACESSORIO")

# Decisoes entre sessoes (o que o motor faz com a carga da PROXIMA sessao).
SOBE, MANTEM, DESCE = "SOBE", "MANTEM", "DESCE"
# Marcador de deload no historico do iniciante (nao e decisao de carga: e o evento).
DELOAD = "DELOAD"

EPS = 1e-9


# ----------------------------------------------------------------------------- arredondamento

def para_incremento(x: float, incremento: float) -> float:
    """Multiplo de `incremento` mais proximo de `x`; EMPATE desce (a carga menor e a segura).

    Fixado aqui porque o Kotlin e o Python divergem em empates exatos de `round`. O `- EPS` faz o
    empate de ponto flutuante (2.5000000000000004) cair do lado certo.
    """
    if incremento <= 0:
        raise ValueError("incremento deve ser positivo")
    return math.ceil(x / incremento - 0.5 - EPS) * incremento


def arredondar(x: float) -> int:
    """Meio pra cima (mesmo criterio de `motor_v3.arredondar` e do `roundToInt` do Kotlin)."""
    return int(math.floor(x + 0.5))


# ----------------------------------------------------------------------------- estado

@dataclass(frozen=True)
class Serie:
    reps: int
    # RIR percebido/informado. None = NAO informado. [REGRA] (2026-10-28, D-3): RIR nulo aplica SO as
    # regras por repeticao -- sem a exigencia de RIR no topo, sem salto proporcional, sem tolerancia
    # pos-salto, sem a reducao de 4% e sem 1RM. Nada e inferido: a regra que precisa do RIR nao age.
    rir: Optional[int]


@dataclass(frozen=True)
class Estado:
    """Progressao de UM exercicio -- por exercicio, nao por vaga (checklist da spec): a vaga pode
    mudar de exercicio num programa novo, a historia da carga acompanha o exercicio."""
    carga: Optional[float] = None   # None = calibracao pendente (nunca fez, ou recalibra apos pausa)
    exposicoes: int = 0             # sessoes ja feitas com o exercicio (a calibracao conta como a 1a)
    pos_salto: bool = False         # regra 3: a tolerancia esta ativa
    um_rm: Optional[float] = None   # 1RM estimado da ultima sessao; None em isolamento ou sem dado
    sem_subir: int = 0              # sessoes consecutivas sem SOBE


# ----------------------------------------------------------------------------- prescricao

def prescricao(objetivo: str, papel: str) -> tuple:
    """(reps min, reps max, descanso s, RIR alvo) -- spec, "Prescricao de cada serie"."""
    return S.PRESCRICAO[objetivo][papel]


# ----------------------------------------------------------------------------- 1RM

def um_rm_epley(carga: float, reps: int, rir: int) -> float:
    """Epley (1985) com o RIR somado as repeticoes: 1RM = carga x (1 + (reps + RIR) / 30).

    [PROPOSTA] a spec manda estimar "a partir do RIR relatado" e nao escolhe a formula. Epley e a
    mais usada; so e confiavel perto de 10 repeticoes -- por isso o 1RM nunca ESCOLHE a carga
    (quem escolhe e a progressao dupla), so serve de sinal para a promocao (Fatia 11).
    """
    return carga * (1.0 + (reps + rir) / 30.0)


def estimar_um_rm(carga: float, series: list, papel: str) -> Optional[float]:
    """Melhor serie da sessao. Isolamento NAO usa 1RM estimado (spec, regra 5): so progressao dupla."""
    if papel not in COMPOSTO_PAPEIS or not series:
        return None
    com_rir = [s for s in series if s.rir is not None]
    if not com_rir:
        return None
    return max(um_rm_epley(carga, s.reps, s.rir) for s in com_rir)


# ----------------------------------------------------------------------------- calibracao

def salto_da_faixa(excesso_rir: int, barra_livre: bool) -> float:
    """Salto proporcional pelo quanto o RIR informado passa do alvo (spec l.80):
    1-2 acima -> +10%, 3-4 -> +25%, 5 ou mais -> +50%; teto de +25% em barra livre. 0 = sem salto."""
    for de, ate, salto in S.CALIBRACAO_SALTOS:
        if de <= excesso_rir <= ate:
            return min(salto, S.CALIBRACAO_TETO_BARRA_LIVRE) if barra_livre else salto
    return 0.0


def carga_apos_salto(carga: float, pct: float, incremento: float) -> float:
    """Nunca menos que UM incremento acima: um salto pedido que arredonda para a mesma carga
    travaria a calibracao (10% de 10 kg num incremento de 2,5 kg)."""
    return max(para_incremento(carga * (1.0 + pct), incremento), carga + incremento)


@dataclass(frozen=True)
class PassoCalibracao:
    carga: float        # carga da PROXIMA serie (ou a final, se terminou)
    terminou: bool


def passo_calibracao(carga: float, rir_informado: Optional[int], serie_n: int, rir_alvo: int,
                     barra_livre: bool, incremento: float) -> PassoCalibracao:
    """Uma serie de calibracao. `serie_n` e 1-based. RIR informado >= 4 sobe NA HORA e a pessoa faz
    outra serie, ate 3 series (spec l.600). Abaixo de 4 a carga e a certa: termina sem mexer.
    Na 3a serie a carga ainda sobe (e a sugestao para a proxima exposicao) e termina."""
    if rir_informado is None or rir_informado < S.CALIBRACAO_RIR_MINIMO:
        return PassoCalibracao(carga, True)   # RIR nulo: a calibracao e feita de RIR, nao age
    pct = salto_da_faixa(rir_informado - rir_alvo, barra_livre)
    if pct == 0.0:
        return PassoCalibracao(carga, True)
    nova = carga_apos_salto(carga, pct, incremento)
    return PassoCalibracao(nova, serie_n >= S.CALIBRACAO_MAX_SERIES)


def calibrar(carga_inicial: float, rirs_informados: list, rir_alvo: int, barra_livre: bool,
             incremento: float) -> Estado:
    """Conveniencia: roda os passos sobre uma lista de RIRs informados e devolve o Estado depois da
    1a exposicao. Lista curta demais para fechar nao e erro -- devolve a ultima carga sugerida."""
    carga, usadas = carga_inicial, 0
    for n, rir in enumerate(rirs_informados, start=1):
        usadas = n
        passo = passo_calibracao(carga, rir, n, rir_alvo, barra_livre, incremento)
        carga = passo.carga
        if passo.terminou:
            break
    if usadas == 0:
        raise ValueError("calibracao sem nenhum RIR informado")
    return Estado(carga=carga, exposicoes=1)


# ----------------------------------------------------------------------------- progressao dupla

def decidir(estado: Estado, series: list, papel: str, objetivo: str, incremento: float,
            barra_livre: bool) -> tuple:
    """Decisao ENTRE sessoes. Devolve (Estado novo, SOBE|MANTEM|DESCE).

    Regra 1: sobe so quando TODAS as series batem o topo da faixa sem ficar mais perto da falha que o
             alvo (reps >= topo e RIR >= alvo).
    Regra 2: o aumento e o MENOR incremento -- exceto nas tres primeiras exposicoes, em que o salto
             proporcional da calibracao tambem vale (pelo pior RIR da sessao).
    Regra 3: logo depois de uma subida, repeticoes abaixo da faixa sao aceitas enquanto o RIR estiver
             no alvo -- a carga fica. Sem isso o halter que vai de 8 para 10 kg (+25%) entra em loop
             de subir e devolver.
    Implicita ("subir e devolver" so existe se devolver existe) -- [PROPOSTA]: repeticoes abaixo da
             faixa FORA da tolerancia devolvem UM incremento.
    A regra 4 (reduzir dentro do dia) e `reducao_intrasessao`.
    """
    if estado.carga is None:
        raise ValueError("sem carga: a calibracao vem antes da progressao")
    if not series:
        raise ValueError("sessao sem series")
    lo, hi, _, rir_alvo = prescricao(objetivo, papel)
    topo = all(s.reps >= hi and (s.rir is None or s.rir >= rir_alvo) for s in series)
    abaixo = any(s.reps < lo for s in series)
    todos_com_rir = all(s.rir is not None for s in series)
    rir_no_alvo = todos_com_rir and all(s.rir >= rir_alvo for s in series)  # nulo: tolerancia NAO vale
    exposicoes = estado.exposicoes + 1
    um_rm = estimar_um_rm(estado.carga, series, papel)
    if um_rm is None and not any(s.rir is not None for s in series):
        um_rm = estado.um_rm   # sessao sem nenhum RIR nao apaga o 1RM historico

    if topo:
        pct = 0.0
        if exposicoes <= S.EXPOSICOES_COM_SALTO_MAIOR and todos_com_rir:
            pct = salto_da_faixa(min(s.rir for s in series) - rir_alvo, barra_livre)
        nova = carga_apos_salto(estado.carga, pct, incremento) if pct else estado.carga + incremento
        return Estado(nova, exposicoes, True, um_rm, 0), SOBE
    if abaixo and not (estado.pos_salto and rir_no_alvo):
        nova = max(estado.carga - incremento, incremento)
        return Estado(nova, exposicoes, False, um_rm, estado.sem_subir + 1), DESCE
    # Fica. A tolerancia so continua se foi ela que segurou a carga; entrar na faixa a encerra.
    return (Estado(estado.carga, exposicoes, bool(abaixo and estado.pos_salto), um_rm,
                   estado.sem_subir + 1), MANTEM)


def reducao_intrasessao(carga: float, rir_primeira_serie: Optional[int], rir_alvo: int,
                        incremento: float) -> tuple:
    """Regra 4: se a 1a serie do dia saiu mais dificil que o alvo, as seguintes reduzem 4% POR PONTO
    de RIR abaixo do alvo (linear sobre a carga do dia). Devolve (carga das proximas series,
    anulada). `anulada` = a reducao arredondou para a mesma carga (4% de 8 kg num incremento de
    2 kg): a carga fica e o chamador sabe que a regra nao agiu -- nao existe carga menor carregavel.
    """
    if rir_primeira_serie is None:
        return carga, False   # RIR nulo: a regra 4 nao age (e nao e "anulada": nao havia o que medir)
    falta = rir_alvo - rir_primeira_serie
    if falta <= 0:
        return carga, False
    nova = para_incremento(carga * (1.0 - S.REDUCAO_POR_PONTO_RIR * falta), incremento)
    nova = max(nova, incremento)
    if nova >= carga - EPS:
        return carga, True
    return nova, False


# ----------------------------------------------------------------------------- mesociclo

@dataclass(frozen=True)
class Mesociclo:
    numero: int = 1
    semana: int = 1
    # [REGRA] 2026-10-28: aberto por retorno de 60+ depois de >28 dias. Vale ate o fim DESTE
    # mesociclo; abrir o seguinte (concluir_semana na semana 4) o apaga.
    conservador: bool = False


def concluir_semana(m: Mesociclo) -> Mesociclo:
    """O mesociclo avanca por CONCLUSAO, nao por calendario (spec): fechar a semana 4 abre o proximo."""
    if m.semana >= S.MESOCICLO_SEMANAS:
        return Mesociclo(m.numero + 1, 1)
    return Mesociclo(m.numero, m.semana + 1, m.conservador)


def deload_programado(nivel: str, m: Mesociclo) -> bool:
    """Semana 4 para intermediario e avancado. O INICIANTE nao tem deload por calendario: so por
    estagnacao (`situacao_iniciante`) -- 25% do tempo dele seria desperdicio (spec, item 24)."""
    return nivel != "BEGINNER" and m.semana == S.SEMANA_DELOAD


def series_do_dia(series_base: int, deload: bool) -> int:
    """Series nao sobem dentro do ciclo; no deload, metade (meio pra cima), no minimo 1."""
    if not deload:
        return series_base
    return max(1, arredondar(series_base * S.DELOAD_FATOR_SERIES))


def mesociclo_para_perfil(m: Optional[Mesociclo]) -> int:
    """O `mesociclo_atual` que `Perfil` recebe. Quem ainda nao treinou nao tem estado: e o 1o."""
    return 1 if m is None else m.numero


def retorno_conservador_para_perfil(m: Optional[Mesociclo]) -> bool:
    """O `retorno_conservador` que `Perfil` recebe (so produz efeito com idade >= 60)."""
    return m is not None and m.conservador


# ----------------------------------------------------------------------------- continuidade

@dataclass(frozen=True)
class VagaDaSemana:
    alvo: str
    papel: str
    series: int
    exercicio: str


def semana_do_programa(programa, m: Mesociclo, deload_extra: bool = False) -> list:
    """A semana `m.semana` de um programa JA gerado: [(rotulo, [VagaDaSemana])].

    CONTINUIDADE (registro da Fatia 6, escopo da Fatia 10): o `preencher()` roda UMA vez, em
    `motor_v3.gerar()`; esta funcao so le o resultado. O exercicio de cada posicao e o mesmo em
    todas as semanas do mesociclo e dos mesociclos seguintes do MESMO programa -- sem isso nao ha
    exposicao repetida, e sem ela nao ha sobrecarga progressiva. Trocar exercicio e gerar um NOVO
    programa. Vaga vazia (sem exercicio) nao e prescrita.

    Unica excecao nomeada: a troca de ancora por estagnacao do iniciante (`situacao_iniciante`).
    """
    deload = deload_programado(programa.perfil.nivel, m) or deload_extra
    return [(rotulo, [VagaDaSemana(v.alvo, v.papel, series_do_dia(v.series, deload), v.exercicio.nome)
                      for v in vagas if v.exercicio])
            for rotulo, vagas in programa.sessoes]


# ----------------------------------------------------------------------------- iniciante

ESTAGNACAO_PROGRESSANDO = "PROGREDINDO"
ESTAGNACAO_DELOAD = "DELOAD"                      # aplique o deload agora, com A MESMA ancora
ESTAGNACAO_AGUARDANDO = "AGUARDANDO_RETOMADA"     # deload feito; ainda nao deu para julgar
ESTAGNACAO_RETOMOU = "RETOMOU"                    # a carga voltou a subir depois do deload
ESTAGNACAO_DIAGNOSTICO = "DIAGNOSTICO_PENDENTE"   # estagnacao persiste; falta o diagnostico
ESTAGNACAO_TROCA = "TROCA_AUTORIZADA"


def _sem_subir_no_fim(decisoes: list) -> int:
    n = 0
    for d in reversed(decisoes):
        if d == SOBE:
            break
        n += 1
    return n


def situacao_iniciante(decisoes: list, diagnostico_feito: bool) -> str:
    """Maquina da ancora do INICIANTE. `decisoes` e o historico, em ordem, de SOBE/MANTEM/DESCE e do
    marcador DELOAD. A ORDEM da spec: estagnou -> deload com a mesma ancora -> diagnostico -> so
    entao a troca. Trocar antes compararia exercicios diferentes.

    TROCA_AUTORIZADA exige as TRES coisas, nesta ordem:
      (1) um deload JA aplicado com a mesma ancora;
      (2) estagnacao PERSISTENTE depois dele: `RETOMADA_SESSOES` (3) sessoes seguintes sem SOBE
          ("retomada e a carga subir em pelo menos uma ancora nas tres sessoes seguintes", spec);
      (3) diagnostico registrado (`diagnostico_feito`), que vem de fora -- do mecanismo de deload
          como diagnostico (Fatia 11) ou da resposta do usuario.
    Subir depois do deload (em qualquer sessao) afasta a troca: RETOMOU. Estagnar DE NOVO depois de
    retomar nao e troca -- e o sinal de promocao (Fatia 11); aqui devolve DELOAD, o novo deload.
    """
    if DELOAD not in decisoes:
        if _sem_subir_no_fim(decisoes) >= S.ESTAGNACAO_SESSOES:
            return ESTAGNACAO_DELOAD
        return ESTAGNACAO_PROGRESSANDO
    depois = decisoes[len(decisoes) - decisoes[::-1].index(DELOAD):]
    if SOBE in depois:
        # A carga voltou a subir depois do deload (mesmo que tarde): nao e estagnacao persistente.
        return ESTAGNACAO_RETOMOU if _sem_subir_no_fim(depois) < S.ESTAGNACAO_SESSOES else ESTAGNACAO_DELOAD
    if len(depois) < S.RETOMADA_SESSOES:
        return ESTAGNACAO_AGUARDANDO
    return ESTAGNACAO_TROCA if diagnostico_feito else ESTAGNACAO_DIAGNOSTICO


# ----------------------------------------------------------------------------- retorno apos pausa

@dataclass(frozen=True)
class Retorno:
    mesociclo: Mesociclo
    recalibra: bool         # True = todas as cargas voltam a pendentes: calibracao OBRIGATORIA
    fator_carga: float      # sobre a carga de antes da pausa (1.0 = intacta)


def retorno_apos_pausa(dias_parado: int, m: Mesociclo, idade: Optional[int] = None) -> Retorno:
    """UMA regra, tres ramos (spec, "Retorno apos pausa"). O relogio e o do servidor.

      ate 14 dias ........ segue como estava
      15 a 28 dias ....... volta a semana 1 do MESMO mesociclo, com a carga menor (`REDUCAO_RETORNO`)
      mais de 28 dias .... mesociclo NOVO e carga recalibrada (calibracao obrigatoria: a carga
                           informada seria a de antes da pausa e pularia a reducao)

    [REGRA] extensao declarada alem da lista literal da spec: com idade >= 60 o mesociclo NOVO nasce
    com `conservador=True` (modo conservador explicito, independente do numero). Motivo: quem
    volta depois de um mes fora e quem mais precisa do lado seguro.
    """
    if dias_parado < 0:
        raise ValueError("dias parado negativo")
    if dias_parado <= S.PAUSA_SEGUE_DIAS:
        return Retorno(m, False, 1.0)
    if dias_parado <= S.PAUSA_RECALIBRA_DIAS:
        return Retorno(Mesociclo(m.numero, 1, m.conservador), False, 1.0 - S.REDUCAO_RETORNO)
    conservador = idade is not None and idade >= S.IDADE_RETORNO_CONSERVADOR
    return Retorno(Mesociclo(m.numero + 1, 1, conservador), True, 1.0)


def aplicar_retorno(estado: Estado, r: Retorno, incremento: float) -> Estado:
    """O Estado de UM exercicio depois do retorno. Recalibrar zera a carga e as exposicoes (a
    calibracao e a 1a de novo) e preserva o 1RM historico; a reducao arredonda ao incremento."""
    if r.recalibra:
        return Estado(None, 0, False, estado.um_rm, 0)
    if estado.carga is None or r.fator_carga == 1.0:
        return estado
    nova = max(para_incremento(estado.carga * r.fator_carga, incremento), incremento)
    return replace(estado, carga=min(nova, estado.carga), pos_salto=False)
