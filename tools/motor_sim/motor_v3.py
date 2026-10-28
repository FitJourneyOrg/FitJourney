"""Simulador do motor v3 — o oráculo contra o qual o motor Kotlin vai ser testado (fatia 9).

Pipeline da especificação, determinístico (nenhum sorteio):
  [1] alvos → [2] teto da sessão e capacidade → [3] alocação → [4] semana → [5] composição
  → [6] preenchimento pelo catálogo → [7] auditoria sobre o programa ENTREGUE,
com o ponto fixo do indireto em volta de [3]–[6]: o indireto da iteração seguinte sai dos compostos
que o preenchimento de fato colocou no programa, nunca de uma estrutura provisória.

Catálogo: catalogo_banco.csv, exportado do banco por exportar_catalogo.sql. Casamento
músculo↔exercício = `volume_target` (V64): um exercício, um alvo. Sem padrão de movimento, sem
etiqueta, sem remendo no carregamento.

PROVISÓRIO, até a fatia dona decidir (cada um conta disparos em `Programa.disparos`):
  - alocação sob escassez (fatia 3, com adição da fatia 8): piso = coluna do iniciante menos o
    indireto; rebaixamento ao piso do papel na ordem de sacrifício; sacrifício; CORTE
    PROPORCIONAL ao inverso do peso entre o que sobrar de `ORDEM_SACRIFICIO` (decisão da Fatia
    8, 2026-10-24 -- `[REGRA]`, não provisório: ver `docs/regras/01-motor-de-geracao.md`);
    excedente proporcional ao peso; arredondamento para séries inteiras em ordem de prioridade;
  - teto por sessão (fatia 8): a capacidade é dias × teto; sessão acima do teto é MEDIDA
    (`sessoes_acima_do_teto`), não cortada — o laço que corta é a fatia 8;
  - escolha do exercício (fatia 6): critério fixo e explícito, sem âncora de mesociclo.
  - ciclo do ponto fixo sob escassez (fatia 3): fica o estado de menor indireto total, e o
    disparo `ponto_fixo_oscilou` é contado (26 de 720 perfis em 2026-10-14).
Fora daqui por enquanto: ênfase, rampa por propriedade e porção do deltoide. RIR mínimo e
modo conservador entraram na Fatia 7 (2026-10-24) -- ver `rir_minimo()` e
`modo_conservador_automatico()`.
"""
import csv
import math
import os
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from typing import Optional

from spec_v3 import (ALVOS, DIVISOES, EQUIPAMENTO_CASA, FATOR_IDADE_60, FATOR_MANUTENCAO,
                     FATOR_OBJETIVO_ALVO, PISO_DIRETO, PISO_DIRETO_MUSCULOS, PISO_DIRETO_NIVEIS,
                     FATOR_OBJETIVO_TETO, INDIRETO, MAX_EXERCICIOS_MUSCULO,
                     MAX_SERIES_EXERCICIO, MAX_SERIES_MUSCULO_SESSAO, MINUTOS_POR_SERIE, NIVEIS,
                     OMBRO_LIMITE, OMBRO_MINIMO_COMPOSTO, ORDEM_SACRIFICIO, OVERHEAD_MIN,
                     PESO_PRIORIDADE, PISO_SERIES, SO_ISOLAMENTO, TETO_NIVEL,
                     TRICEPS_SEM_EXTENSAO, VOLUME)

AQUI = os.path.dirname(os.path.abspath(__file__))
COMPOSTOS = ("COMPOSTO", "ACESSORIO")
EMPURRAR = ("CHEST", "SHOULDERS")


# ----------------------------------------------------------------------------- catálogo

ACESSORIOS = ("requires_bench", "requires_elevated_surface", "requires_pullup_bar",
              "requires_dip_station", "requires_step", "requires_anchor_point")
TODOS_ACESSORIOS = frozenset(ACESSORIOS)


@dataclass(frozen=True)
class Exercicio:
    nome: str
    alvo: str
    composto: bool
    equipamento: str
    nivel: str
    visivel: bool  # modality = STRENGTH: o ExercisePreFilter do Kotlin só enxerga esses
    base: bool
    # --- V65: é com isto que o preenchimento escolhe (antes era base + ordem alfabética)
    prioridade: int
    relevancia: int
    nivel_maximo: str
    ambiente: str
    familia: str
    acessorios: frozenset
    # --- V67 (fatia 7): RIR minimo por propriedade -- aprovado por nome contra
    # tools/curadoria_oficial/curadoria_canonica.csv, nao derivavel de movement_pattern nem de
    # equipment (ver docs/trabalho/motor-v3-fatia-7.md). O motor nao olha contraindications.
    rir_minimo_seguranca: bool


def carregar_catalogo(caminho: str = os.path.join(AQUI, "catalogo_banco.csv")) -> list:
    out = []
    with open(caminho, encoding="utf-8") as f:
        for r in csv.DictReader(f):
            out.append(Exercicio(
                nome=r["name"], alvo=r["volume_target"], composto=r["is_compound"] == "t",
                equipamento=r["equipment"], nivel=r["level"], visivel=r["modality"] == "STRENGTH",
                base=r["is_base"] == "t",
                prioridade=int(r["prioridade"]), relevancia=int(r["relevancia"]),
                nivel_maximo=r["nivel_maximo"], ambiente=r["ambiente"], familia=r["familia"],
                acessorios=frozenset(a for a in ACESSORIOS if r[a] == "t"),
                rir_minimo_seguranca=r["rir_minimo_seguranca"] == "t",
            ))
    return out


CATALOGO = carregar_catalogo()


# ----------------------------------------------------------------------------- perfil e programa

@dataclass(frozen=True)
class Perfil:
    objetivo: str = "GAIN_MUSCLE"
    nivel: str = "INTERMEDIATE"
    dias: int = 4
    minutos: int = 60
    idade: int = 30
    ambiente: str = "ACADEMIA"
    # O que o usuário tem além do equipamento de carga. None = academia tem tudo, casa não tem
    # nada declarado.
    #
    # ATENCAO (2026-10-27): o cadastro JA pergunta -- a V66 deu ao perfil onde guardar e a fatia
    # "acessorios no onboarding" fechou em 22/10. Entao `None` em CASA e o PIOR CASO, nao o caso
    # tipico. A matriz dos 720 perfis usa `None` em toda CASA: medido, um banco sozinho leva as
    # 239 vagas vazias de CASA para 0. Todo numero medido sobre os 720 carrega esse vies.
    acessorios: frozenset = None
    # Fatia 2 [REGRA]: nulo = 1º mesociclo -- o oráculo não rastreava mesociclo até
    # agora (Fatia 7, 2026-10-23) e usava `primeiro_mesociclo: bool = False`, convenção
    # OPOSTA à do Kotlin (`CapacityModel.fatorIdade`, nulo = 1º). Nenhuma instanciação em
    # `test_motor_v3.py::PERFIS` definia o campo, então todo perfil 60+ não-iniciante da
    # matriz vinha recebendo fator de idade 1,0 (sem redução) no oráculo -- o Kotlin daria
    # 0,75. Corrigido para alinhar com a `[REGRA]` da Fatia 2.
    mesociclo_atual: Optional[int] = None
    sem_teto: bool = False         # "sem restrição de capacidade": a configuração da referência
    so_visiveis: bool = True       # False = catálogo pretendido (como se modality estivesse curada)


@dataclass
class Vaga:
    alvo: str
    papel: str
    series: int
    exercicio: Exercicio = None
    # --- Fatia 7: RIR aplicado a esta vaga, so depois de preencher() escolher o exercicio.
    # None = preenchimento nao rodou (vaga vazia) ou RIR normal do objetivo (fora do escopo
    # desta fatia -- so o PISO de seguranca é calculado aqui, nao a tabela de RIR por semana).
    rir: Optional[int] = None


@dataclass
class Programa:
    perfil: Perfil
    divisao: str
    teto_sessao: float
    capacidade: float
    alvo: dict
    alocado: dict
    sessoes: list                  # [(rótulo, [Vaga])]
    indireto: dict                 # do ENTREGUE
    demanda_direta: dict           # max(0, alvo − indireto do entregue)
    entregue: dict                 # séries diretas entregues por músculo
    compostos_semana: dict         # séries de exercício composto entregues, por músculo
    disparos: Counter = field(default_factory=Counter)
    vagas_vazias: list = field(default_factory=list)
    sessoes_acima_do_teto: list = field(default_factory=list)
    iteracoes: int = 0
    auditoria: dict = field(default_factory=dict)   # [7]: musculo -> AuditoriaMusculo

    @property
    def abaixo_do_minimo(self) -> list:
        """Gatilho do aviso de honestidade: quem nao alcancou o PISO, qualquer que seja o status.

        Usa `atingiu_minimo`, nao o status: um perfil MAINTAIN/BEGINNER sai ATENDIDO (bateu o
        alvo) com efetivo abaixo do piso, e e justamente esse caso que nao pode passar calado.
        """
        return [a for a in self.auditoria.values()
                if a.status != SEM_CATALOGO and a.minimo > 0 and not a.atingiu_minimo]

    @property
    def demanda_total(self) -> float:
        return sum(self.demanda_direta.values())


# ----------------------------------------------------------------------------- [1] e [2]

def primeiro_mesociclo(mesociclo_atual: Optional[int]) -> bool:
    """Nulo = 1º mesociclo (Fatia 2 `[REGRA]`, espelha `CapacityModel.fatorIdade` no
    Kotlin) -- compartilhada pelo fator de idade e pelo modo conservador (Fatia 7)."""
    return (mesociclo_atual if mesociclo_atual is not None else 1) == 1


def alvos(p: Perfil) -> dict:
    fator = FATOR_OBJETIVO_ALVO[p.objetivo]
    i = 0 if fator is None else NIVEIS.index(p.nivel)
    return {m: min(VOLUME[m][i] * (1.0 if fator is None else fator), VOLUME[m][3]) for m in ALVOS}


def teto_sessao(p: Perfil, divisao: str) -> float:
    if p.sem_teto:
        return math.inf
    overhead = OVERHEAD_MIN["FULL_BODY" if divisao == "FULL_BODY" else "DEMAIS"]
    por_tempo = (p.minutos - overhead) / MINUTOS_POR_SERIE
    idade = FATOR_IDADE_60 if p.idade >= 60 and (p.nivel == "BEGINNER" or primeiro_mesociclo(p.mesociclo_atual)) else 1.0
    por_nivel = TETO_NIVEL[p.nivel] * FATOR_OBJETIVO_TETO[p.objetivo] * idade
    return min(por_tempo, por_nivel)


def modo_conservador_automatico(p: Perfil) -> bool:
    """Especificação, "Modo conservador": iniciante, saúde geral ou 60+ no 1º mesociclo. O
    gatilho por preferência declarada ("quer começar no modo leve?") fica fora -- o campo não
    existe em `ProfileDto`/nenhuma migration (Fatia 7, `docs/trabalho/motor-v3-fatia-7.md`)."""
    return (
        p.nivel == "BEGINNER"
        or p.objetivo == "GENERAL_HEALTH"
        or (p.idade >= 60 and primeiro_mesociclo(p.mesociclo_atual))
    )


def rir_minimo(exercicio: Optional[Exercicio], modo_conservador: bool) -> Optional[int]:
    """Piso de RIR de segurança (Fatia 7). Não é a tabela de RIR por semana/objetivo -- isto é
    só o piso que pode apertar ela. 3 se o modo conservador estiver ativo (vale em TODO
    exercício, não só nos de `rir_minimo_seguranca`); senão 2 se o exercício carrega a coluna ou
    passa sobre a cabeça com peso livre (`rir_minimo_seguranca`, V67); senão nenhum piso (None).
    `[INV]`: nenhum perfil BEGINNER recebe exercício com `rir_minimo_seguranca=True` -- a troca
    de barra "para o iniciante" da especificação já está satisfeita pela curadoria (os 8
    exercícios aprovados são todos INTERMEDIATE/ADVANCED), então não há regra de troca aqui;
    isto é medido, não codado (ver `docs/regras/01-motor-de-geracao.md`)."""
    if modo_conservador:
        return 3
    if exercicio is not None and exercicio.rir_minimo_seguranca:
        return 2
    return None


def acessorios_de(p: Perfil) -> frozenset:
    if p.acessorios is not None:
        return p.acessorios
    return TODOS_ACESSORIOS if p.ambiente == "ACADEMIA" else frozenset()


def pool(p: Perfil, disparos: Counter = None) -> list:
    """O pré-filtro: nível mínimo, ambiente, acessórios e nível máximo.

    `nivel_maximo` é o único que tem válvula de escape: se tirar o último candidato de um alvo, ele
    é ignorado NAQUELE alvo -- é melhor dar ao avançado em casa a flexão com joelhos do que deixar
    o peito sem exercício. Ambiente e acessório não têm escape: quem não tem banco não tem banco.
    """
    ate = NIVEIS[: NIVEIS.index(p.nivel) + 1]
    tem = acessorios_de(p)
    ambientes = {"ACADEMIA", "AMBOS"} if p.ambiente == "ACADEMIA" else {"CASA", "AMBOS"}
    viavel = [e for e in CATALOGO
              if e.nivel in ate
              and (e.visivel or not p.so_visiveis)
              and e.ambiente in ambientes
              and (p.ambiente == "ACADEMIA" or e.equipamento in EQUIPAMENTO_CASA)
              and e.acessorios <= tem]
    i = NIVEIS.index(p.nivel)
    no_teto = [e for e in viavel if i <= NIVEIS.index(e.nivel_maximo)]
    sobrou = {e.alvo for e in no_teto}
    for alvo in {e.alvo for e in viavel} - sobrou:
        if disparos is not None:
            disparos["nivel_maximo_ignorado"] += 1
        no_teto += [e for e in viavel if e.alvo == alvo]
    return no_teto


# ----------------------------------------------------------------------------- [3] alocação

def piso_do_papel(m: str) -> int:
    return PISO_SERIES["ISOLAMENTO"] if m in SO_ISOLAMENTO else PISO_SERIES["COMPOSTO"]


def piso_direto(m: str, nivel: str) -> int:
    """Series DIRETAS garantidas por semana (Fatia 8, D-1 hibrido). 0 = sem piso.

    So vale para quem recebe indireto com fonte (triceps, biceps, gluteo) e so em
    INTERMEDIATE/ADVANCED -- o iniciante e isento pela regra geral do hibrido. Ver spec_v3.
    """
    if nivel not in PISO_DIRETO_NIVEIS or m not in PISO_DIRETO_MUSCULOS:
        return 0
    return PISO_DIRETO


def arredondar(x: float) -> int:  # Kotlin roundToInt: meio pra cima
    return math.floor(x + 0.5)


def piso_prescritivel(inteiro: dict, prioridade: list, capacidade: float,
                      disparos: Counter) -> dict:
    """`[REGRA]` (Fatia 8, 2026-10-27): total semanal de cada musculo e ZERO ou >= 2.

    1 serie nao e prescritivel -- nenhum exercicio do catalogo tem 1 serie. Isto e a opcao (iii)
    aplicada na ETAPA [3], onde o orcamento existe. Antes vivia no `montar_semana()` (etapa [4]),
    que subia o total DEPOIS do orcamento fechado, e era essa a causa de `entregue > capacidade`:
    o estouro nunca foi excesso de volume, era aumento de volume acontecendo FORA do orcamento.

    Financiado DENTRO do orcamento, em ordem decrescente de peso: quem couber sobe, quem nao
    couber vai a ZERO, e o que ele libera financia o proximo da fila -- que por construcao pesa
    menos.

    O orcamento e medido contra quem JA esta decidido (os que ficaram no piso ou acima), nunca
    contra a fracao dos pendentes. Medir contra ela invertia a prioridade: a fracao do tricepes
    ocupava o orcamento na vez do gluteo, o gluteo nao cabia e era ZERADO, e o tricepes subia com
    o que o gluteo liberou -- 18 vezes nos 720, medido em 2026-10-27, contra a decisao de produto
    que poe o gluteo acima dele. Cada pendente vai acabar em `minimo` ou em 0; enquanto nao
    decidido, nao reserva nada. Teto garantido por inducao: so somamos `minimo` depois de testar.

    Guloso de uma passada, nao otimo: um musculo zerado nao e reconsiderado se um pendente
    posterior sair e liberar espaco -- mas esse caso favorece sempre o de MENOR peso, que e a
    direcao certa de perder.
    """
    minimo = PISO_SERIES["ISOLAMENTO"]
    # `sem_teto` (o perfil de REFERENCIA) chega aqui com capacidade infinita, e math.floor(inf)
    # estoura. Orcamento infinito = todo mundo sobe ao piso e ninguem zera, que e o certo ali.
    teto = capacidade if math.isinf(capacidade) else math.floor(capacidade + 1e-9)
    decidido = sum(n for n in inteiro.values() if n >= minimo)
    for m in [m for m in prioridade if m in inteiro and inteiro[m] < minimo]:
        if decidido + minimo <= teto:
            inteiro[m] = minimo
            decidido += minimo
            disparos["sobe_ao_piso_dentro_do_orcamento"] += 1
        else:
            del inteiro[m]
            disparos["zerado_por_nao_caber_no_piso"] += 1
    return inteiro


def alocar(direta: dict, indireto: dict, treinaveis: list, capacidade: float, disparos: Counter,
           piso_direto_de: dict = None) -> dict:
    """`piso_direto_de`: musculo -> series diretas garantidas (Fatia 8). Entra como PRIORIDADE NA
    FILA do orcamento, nunca por fora dele: sob escassez ele eleva o piso protegido do musculo,
    e os lacos de rebaixamento e sacrificio continuam podendo alcanca-lo -- quem nao couber sai.
    """
    piso_direto_de = piso_direto_de or {}
    prioridade = sorted(treinaveis, key=lambda m: (-PESO_PRIORIDADE[m], ALVOS.index(m)))
    if sum(direta[m] for m in treinaveis) <= capacidade:
        folgado = {m: arredondar(direta[m]) for m in prioridade if arredondar(direta[m]) > 0}
        return piso_prescritivel(folgado, prioridade, capacidade, disparos)

    # --- escassez (PROVISÓRIO, fatia 3)
    disparos["escassez"] += 1
    ativos = [m for m in prioridade if direta[m] > 0]
    # O piso protegido de cada ativo e o maior entre (a) o que falta para o MEV depois do
    # indireto e (b) o PISO DIRETO da Fatia 8 -- nunca mais que a propria demanda.
    piso = {m: min(direta[m], max(float(piso_direto_de.get(m, 0)),
                                  max(0.0, VOLUME[m][0] - indireto[m])))
            for m in ativos}
    for m in ORDEM_SACRIFICIO:
        if sum(piso.values()) <= capacidade:
            break
        if m in piso and piso[m] > piso_do_papel(m):
            piso[m] = piso_do_papel(m)
            disparos["rebaixamento"] += 1
    for m in ORDEM_SACRIFICIO:
        if sum(piso.values()) <= capacidade:
            break
        if m in piso:
            del piso[m]
            disparos["sacrificio"] += 1

    # --- corte proporcional (Fatia 8, `[REGRA]`, 2026-10-24): quando sacrificar tudo que está em
    # ORDEM_SACRIFICIO ainda não basta (hoje: só CHEST/BACK/QUADS/POSTERIOR sobram), o resto é
    # reduzido PROPORCIONALMENTE ao inverso do `PESO_PRIORIDADE` -- quem pesa mais perde menos --
    # em vez de continuar sacrificando um músculo inteiro por vez. Decisão da Fatia 8, medida nos
    # 720 perfis antes de decidir (ver `historico/motor-v3-fatia-8.md`): o corte sequencial
    # (estender ORDEM_SACRIFICIO) deixava os quatro perderem 25-36% de volume; o proporcional
    # erra bem menos (ex.: BACK 9,56 -> 9,22 série/semana nos 288 perfis de 2-3 dias, contra
    # 7,36 do sequencial).
    restantes = [m for m in piso]
    if sum(piso.values()) > capacidade and restantes:
        deficit = sum(piso.values()) - capacidade
        ativos_restantes = set(restantes)
        for _ in range(100):
            if deficit <= 1e-9 or not ativos_restantes:
                break
            inv = {m: 1.0 / PESO_PRIORIDADE[m] for m in ativos_restantes}
            soma_inv = sum(inv.values())
            cortes = {m: deficit * inv[m] / soma_inv for m in ativos_restantes}
            estourou = [m for m in ativos_restantes if cortes[m] >= piso[m] - 1e-9]
            if not estourou:
                for m in ativos_restantes:
                    piso[m] -= cortes[m]
                disparos["corte_proporcional"] += 1
                deficit = 0.0
                break
            for m in estourou:
                deficit -= piso[m]
                del piso[m]
                ativos_restantes.discard(m)
                disparos["sacrificio_proporcional"] += 1

    alocado = dict(piso)
    sobra = capacidade - sum(alocado.values())
    for _ in range(100):
        famintos = [m for m in alocado if alocado[m] < direta[m] - 1e-9]
        if sobra <= 1e-9 or not famintos:
            break
        peso = sum(PESO_PRIORIDADE[m] for m in famintos)
        movido = 0.0
        for m in famintos:
            add = min(sobra * PESO_PRIORIDADE[m] / peso, direta[m] - alocado[m])
            alocado[m] += add
            movido += add
        sobra -= movido
        if movido < 1e-9:
            break
    # Arredondamento alocado → inteiro em ORDEM DE PRIORIDADE: chão para todos, e a série que
    # sobrar vai para o mais prioritário que tinha fração. Nada é podado depois disto.
    inteiro = {m: math.floor(alocado[m] + 1e-9) for m in alocado}
    livre = math.floor(capacidade + 1e-9) - sum(inteiro.values())
    for m in [m for m in prioridade if m in alocado]:
        if livre <= 0:
            break
        if alocado[m] - inteiro[m] > 1e-9:
            inteiro[m] += 1
            livre -= 1
    inteiro = {m: s for m, s in inteiro.items() if s > 0}
    return piso_prescritivel(inteiro, prioridade, capacidade, disparos)


# ----------------------------------------------------------------------------- [4] semana

def montar_semana(alocado: dict, dias: list, disparos: Counter) -> list:
    """Distribui as séries semanais de cada músculo pelos dias em que a divisão o treina.

    Frequência efetiva: não fatiar abaixo do piso do primeiro papel (3 no composto, 2 no
    isolamento). Abaixo do piso do isolamento, sobe para ele (um exercício de 2 séries).
    """
    series_dia = [defaultdict(int) for _ in dias]
    for m, total in alocado.items():
        dias_m = [i for i, (_, mus) in enumerate(dias) if m in mus]
        if not dias_m:
            continue
        # [INV] (2026-10-27): aqui NAO se sobe mais nada. O piso prescritivel passou para a
        # etapa [3] (`piso_prescritivel()`), onde o orcamento existe -- este bloco subia o total
        # DEPOIS do orcamento fechado e era a causa de `entregue > capacidade` (130/720 quando a
        # Fatia 8 abriu). Medido: com a correcao na etapa [3], `sobe_ao_piso_do_isolamento`
        # passou a disparar 0 vez nos 720, ou seja virou protecao morta. Agora e afirmacao: se um
        # total abaixo do piso chegar aqui, a etapa [3] tem defeito e isto falha alto em vez de
        # remendar em silencio.
        if total < PISO_SERIES["ISOLAMENTO"]:
            raise AssertionError(
                "etapa [4] recebeu %s com %d serie(s), abaixo do piso prescritivel: a etapa [3] "
                "deveria ter zerado ou subido isso DENTRO do orcamento" % (m, total))
        freq = max(1, min(len(dias_m), total // piso_do_papel(m)))
        while freq < len(dias_m) and math.ceil(total / freq) > MAX_SERIES_MUSCULO_SESSAO:
            freq += 1
        escolhidos = espalhar(dias_m, freq, ALVOS.index(m))
        base, resto = divmod(total, freq)
        for k, d in enumerate(escolhidos):
            s = base + (1 if k < resto else 0)
            if s > MAX_SERIES_MUSCULO_SESSAO:
                disparos["limite_11_por_sessao"] += 1
                s = MAX_SERIES_MUSCULO_SESSAO
            series_dia[d][m] = s
    return series_dia


def espalhar(dias_m: list, freq: int, desloc: int) -> list:
    if freq >= len(dias_m):
        return list(dias_m)
    passo = len(dias_m) / freq
    return sorted({dias_m[int(i * passo + desloc) % len(dias_m)] for i in range(freq)})


# ----------------------------------------------------------------------------- [5] composição

def distribuir(total: int, papeis: list) -> list:
    """Piso de cada papel, depois enche o primeiro até 4 e passa ao próximo."""
    x = [PISO_SERIES[p] for p in papeis]
    resto = total - sum(x)
    assert resto >= 0, (total, papeis)
    for i in range(len(x)):
        add = min(MAX_SERIES_EXERCICIO - x[i], resto)
        x[i] += add
        resto -= add
    assert resto == 0, (total, papeis)
    return x


def papeis_para(m: str, s: int) -> list:
    n = 1 if s <= 4 else 2 if s <= 8 else 3
    if m in SO_ISOLAMENTO:
        return ["ISOLAMENTO"] * n
    if n == 1 and s < PISO_SERIES["COMPOSTO"]:
        return ["ISOLAMENTO"]
    return {1: ["COMPOSTO"], 2: ["COMPOSTO", "ISOLAMENTO"], 3: ["COMPOSTO", "ACESSORIO", "ISOLAMENTO"]}[n]


def isolamentos(m: str, s: int, maximo: int) -> list:
    n = min(maximo, 1 if s <= 4 else 2 if s <= 8 else 3)
    s = min(s, n * MAX_SERIES_EXERCICIO)
    while n > 1 and s < n * PISO_SERIES["ISOLAMENTO"]:
        n -= 1
    return [Vaga(m, "ISOLAMENTO", x) for x in distribuir(s, ["ISOLAMENTO"] * n)]


def compor(m: str, s: int, peito_composto_do_dia: int, disparos: Counter) -> list:
    papeis = papeis_para(m, s)
    vagas = [Vaga(m, p, x) for p, x in zip(papeis, distribuir(s, papeis))]
    if m != "SHOULDERS":
        return vagas
    # Regra do ombro: desenvolvimento com no máximo (10 − composto de peito do dia) séries;
    # abaixo de 3, a vaga vira elevação lateral e a sobra vai para um segundo isolamento.
    comp = sum(v.series for v in vagas if v.papel in COMPOSTOS)
    limite = OMBRO_LIMITE - peito_composto_do_dia
    if comp <= limite:
        return vagas
    if limite < OMBRO_MINIMO_COMPOSTO:
        disparos["ombro_vira_isolamento"] += 1
        return isolamentos(m, s, MAX_EXERCICIOS_MUSCULO)
    disparos["ombro_teto_do_desenvolvimento"] += 1
    ancora = Vaga(m, "COMPOSTO", min(MAX_SERIES_EXERCICIO, limite))
    return [ancora] + isolamentos(m, s - ancora.series, MAX_EXERCICIOS_MUSCULO - 1)


def compor_sessao(series: dict, disparos: Counter) -> list:
    vagas = []
    peito = 0
    for m in ALVOS:  # CHEST vem antes de SHOULDERS: a regra do ombro lê o peito do dia
        if series.get(m, 0) <= 0:
            continue
        vs = compor(m, series[m], peito, disparos)
        if m == "CHEST":
            peito = sum(v.series for v in vs if v.papel in COMPOSTOS)
        vagas += vs
    return vagas


# ----------------------------------------------------------------------------- [6] preenchimento

def preencher(sessoes: list, candidatos: list, p: Perfil, disparos: Counter, vazias: list) -> None:
    """Escolha determinística pela curadoria (V65), no lugar de is_base + ordem alfabética.

    Em ordem:
      1. papel certo (composto x isolamento) e não repetir exercício no dia;
      2. no máximo um por FAMÍLIA na sessão -- duas remadas no mesmo dia gastam o tempo do aluno
         repetindo o mesmo vetor em vez de cobrir o outro;
      3. a PRIMEIRA vaga do músculo sai da RELEVÂNCIA 1, se alguma sobreviveu aos filtros;
      4. entre os que restam: o menos usado na semana (é o que faz a sessão B diferir da A) e,
         empatado, a menor PRIORIDADE -- a ordem que a curadoria deu ao grupo.
    """
    usados_semana = Counter()
    modo_conservador = modo_conservador_automatico(p)
    if modo_conservador:
        disparos["modo_conservador_ativo"] += 1
    for rotulo, vagas in sessoes:
        usados_dia = set()
        familias_do_dia = set()
        musculos_abertos = set()
        empurrar_com_barra = False
        for v in vagas:
            def serve(e, composto):
                return e.alvo == v.alvo and e.composto == composto and e.nome not in usados_dia
            quer_composto = v.papel in COMPOSTOS
            cands = [e for e in candidatos if serve(e, quer_composto)]
            if not cands:
                cands = [e for e in candidatos if serve(e, not quer_composto)]
                if cands:
                    disparos["papel_trocado"] += 1
            if v.alvo in EMPURRAR and empurrar_com_barra:
                sem_barra = [e for e in cands if not (e.composto and e.equipamento == "BARBELL")]
                if len(sem_barra) < len(cands):
                    disparos["um_empurrar_com_barra_por_sessao"] += 1
                cands = sem_barra
            inediots = [e for e in cands if e.familia not in familias_do_dia]
            if inediots:
                cands = inediots
            elif cands:
                disparos["familia_repetida_na_sessao"] += 1
            # [REGRA] triceps: a vaga de ISOLAMENTO tem de ser extensao de cotovelo -- fora o
            # MERGULHO, que e empurrar composto e so chega aqui pela inversao de papel.
            # Brandao 2020: cabeca longa +2,1% so com supino contra +17,5% com extensao.
            # Exigencia, nao preferencia: se excluir o MERGULHO esvaziar o pool, ele volta e o
            # disparo registra -- melhor mergulho que vaga vazia. A preferencia por ACIMA DA
            # CABECA nao mora aqui: ela atropelava a relevancia 1 (ver spec_v3.TRICEPS_SEM_EXTENSAO
            # e o `[PROPOSTA]` em debitos.md). Motor decide QUANTO; curadoria decide QUAL.
            if v.alvo == "TRICEPS" and v.papel == "ISOLAMENTO":
                com_extensao = [e for e in cands if e.familia not in TRICEPS_SEM_EXTENSAO]
                if com_extensao:
                    cands = com_extensao
                elif cands:
                    disparos["triceps_sem_extensao_de_cotovelo"] += 1
            if v.alvo not in musculos_abertos:
                base = [e for e in cands if e.relevancia == 1]
                if base:
                    cands = base
                elif cands:
                    disparos["musculo_sem_relevancia_1"] += 1
            if not cands:
                vazias.append((rotulo, v.alvo, v.papel))
                continue
            e = min(cands, key=lambda e: (usados_semana[e.nome], e.prioridade))
            v.exercicio = e
            v.rir = rir_minimo(e, modo_conservador)
            if v.rir == 3:
                disparos["rir_3_modo_conservador"] += 1
            elif v.rir == 2:
                disparos["rir_2_por_propriedade"] += 1
            if p.nivel == "BEGINNER" and e.rir_minimo_seguranca:
                disparos["inv_beginner_com_rir_minimo_seguranca"] += 1
            usados_dia.add(e.nome)
            familias_do_dia.add(e.familia)
            musculos_abertos.add(v.alvo)
            usados_semana[e.nome] += 1
            if v.alvo in EMPURRAR and e.composto and e.equipamento == "BARBELL":
                empurrar_com_barra = True

        # Ordem final (Fatia 6, [REGRA] do Rafael): composto antes de isolado, sort ESTÁVEL --
        # preserva a ordem de preenchimento dentro de cada grupo. Espelha
        # `CatalogFiller.preencher()` no Kotlin (`sortedByDescending { isCompound }`), que já
        # tinha isto; aqui entrou depois, ao achar a divergência de ordem nos 12 perfis do
        # catálogo real (2026-10-23).
        vagas.sort(key=lambda v: not (v.exercicio.composto if v.exercicio else False))


# ----------------------------------------------------------------------------- [7] auditoria

def medir_entregue(sessoes: list):
    entregue, compostos = Counter(), Counter()
    for _, vagas in sessoes:
        for v in vagas:
            if v.exercicio is None:
                continue
            entregue[v.alvo] += v.series
            if v.exercicio.composto:
                compostos[v.alvo] += v.series
    indireto = {m: 0.0 for m in ALVOS}
    for doador, recebe in INDIRETO.items():
        for m, k in recebe.items():
            indireto[m] += compostos[doador] * k
    return dict(entregue), dict(compostos), indireto


# ----------------------------------------------------------------------------- [7] auditoria do efetivo

# Status por musculo (Fatia 9 antecipada, 2026-10-27). O motor nao tinha vocabulario para dizer
# "este musculo ficou abaixo do minimo": media `entregue` (direto) e `indireto` separados e nunca
# somava os dois, entao a grandeza que responde "este programa serve?" -- o ESTIMULO TOTAL --
# nao existia no modelo. Medido antes de construir: 2079 pares (perfil, musculo) dos 720 perfis
# entregam efetivo abaixo do piso, e nenhum era sinalizado. Ver docs/trabalho/motor-v3-fatia-8.md.
ATENDIDO = "ATENDIDO"
MANUTENCAO = "MANUTENCAO"
ABAIXO_DO_MINIMO = "ABAIXO_DO_MINIMO"
NAO_TREINADO = "NAO_TREINADO"
SEM_CATALOGO = "SEM_CATALOGO"


def minimo_efetivo(m: str, objetivo: str) -> float:
    """O limiar contra o qual o volume efetivo e medido -- e ele depende do OBJETIVO.

    MEV (minimo para crescer) = `VOLUME[m][0]`. MV (minimo para nao perder) = `FATOR_MANUTENCAO`
    vezes isso. Quem pediu manutencao e medido contra o MV: prescrever abaixo do MEV nao e erro
    nesse caso, e o que manutencao significa. Medir todo mundo contra o MEV era comparar contra
    o limiar errado -- 660 pares reprovavam por design.
    """
    piso = float(VOLUME[m][0])
    return piso * FATOR_MANUTENCAO if objetivo == "MAINTAIN" else piso


@dataclass(frozen=True)
class AuditoriaMusculo:
    """O que UM musculo recebeu, e se isso basta.

    `efetivo` e a soma do trabalho direto entregue com o credito indireto que os compostos de
    outros musculos deram a ele -- a grandeza contra a qual o minimo (`VOLUME[m][0]`, a coluna do
    iniciante) faz sentido. `razao` e None para quem nao tem minimo (abdomen, hoje em zero).
    """
    musculo: str
    direto: int
    indireto: float
    efetivo: float
    minimo: float   # o limiar APLICAVEL: MEV, ou MV quando o objetivo e manutencao
    alvo: float
    razao: Optional[float]
    status: str
    # O status mede contra o ALVO (a prescricao do perfil). Este campo mede contra o PISO (o
    # minimo fisiologico), que e outra pergunta: `FATOR_OBJETIVO_ALVO` multiplica o alvo sem
    # respeitar o piso, entao MAINTAIN/BEGINNER nasce com alvo ABAIXO do proprio piso em todos
    # os 10 musculos (peito 4,8 contra piso 8) -- 660 pares nos 720 perfis. Qual dos dois vence
    # e decisao de prescricao NAO TOMADA (ver docs/trabalho/motor-v3-fatia-8.md), entao a
    # auditoria expoe os dois fatos e nao arbitra: um perfil assim sai ATENDIDO com
    # `atingiu_minimo=False`.
    atingiu_minimo: bool
    # EIXO SEPARADO do status (Fatia 8, 2026-10-27). O status mede VOLUME contra alvo; isto mede
    # ESTIMULO DIRETO. True = o musculo era elegivel ao piso de trabalho direto e o orcamento
    # RECUSOU pagar (`direto < piso`). Precisa ser eixo proprio porque os dois sao ortogonais: um
    # musculo cujo indireto ja cobre o alvo sai ATENDIDO com ZERO trabalho direto, e sem esta
    # marca a auditoria nao teria onde dizer "alvo batido pelo indireto, nao coube exercicio
    # direto". A `[REGRA]` do piso manda financiar DENTRO do orcamento, entao esses casos existem
    # por desenho -- o que nao pode e passarem calados.
    piso_direto_negado: bool = False


def auditar(alvo: dict, entregue: dict, indireto: dict, treinaveis: list, objetivo: str,
            piso_direto_de: dict = None) -> dict:
    """Etapa [7]: classifica cada musculo pelo que de fato recebeu.

    NAO muda o programa -- so diz o que o programa e. Rodar esta funcao duas vezes sobre o mesmo
    `Programa` da o mesmo resultado, e remove-la nao altera nenhuma serie prescrita.
    """
    out = {}
    for m in ALVOS:
        direto = entregue.get(m, 0)
        ind = indireto.get(m, 0.0)
        efetivo = direto + ind
        minimo = minimo_efetivo(m, objetivo)
        alvo_m = alvo.get(m, 0.0)
        if m not in treinaveis:
            status = SEM_CATALOGO
        elif alvo_m <= 0 or efetivo >= alvo_m - 1e-9:
            status = ATENDIDO
        elif efetivo >= minimo - 1e-9:
            status = MANUTENCAO
        elif efetivo > 1e-9:
            status = ABAIXO_DO_MINIMO
        else:
            status = NAO_TREINADO
        piso_m = (piso_direto_de or {}).get(m, 0)
        out[m] = AuditoriaMusculo(
            musculo=m, direto=direto, indireto=ind, efetivo=efetivo, minimo=minimo,
            alvo=alvo_m, razao=(efetivo / minimo if minimo > 0 else None), status=status,
            atingiu_minimo=(efetivo >= minimo - 1e-9),
            piso_direto_negado=(piso_m > 0 and direto < piso_m),
        )
    return out


# ----------------------------------------------------------------------------- orquestração

def gerar(p: Perfil) -> Programa:
    divisao, dias = DIVISOES[p.dias]
    alvo = alvos(p)
    teto = teto_sessao(p, divisao)
    capacidade = teto * len(dias)
    disparos_pool = Counter()
    candidatos = pool(p, disparos_pool)
    na_divisao = {m for _, mus in dias for m in mus}
    treinaveis = [m for m in ALVOS if m in na_divisao and any(e.alvo == m for e in candidatos)]

    indireto = {m: 0.0 for m in ALVOS}
    historico = []  # (indireto de entrada, estado gerado)
    for it in range(1, 31):
        disparos, vazias = Counter(disparos_pool), []
        pisos = {m: piso_direto(m, p.nivel) for m in ALVOS}
        # A demanda direta nasce do alvo menos o indireto; o PISO DIRETO a eleva onde se aplica,
        # senao o musculo cujo indireto ja cobre o alvo pediria ZERO e nunca receberia vaga.
        direta = {m: max(float(pisos[m]), max(0.0, alvo[m] - indireto[m])) for m in ALVOS}
        alocado = alocar(direta, indireto, treinaveis, capacidade, disparos, pisos)
        semana = montar_semana(alocado, dias, disparos)
        sessoes = [(rot, compor_sessao(series, disparos)) for (rot, _), series in zip(dias, semana)]
        preencher(sessoes, candidatos, p, disparos, vazias)
        entregue, compostos, novo = medir_entregue(sessoes)
        estado = (alocado, sessoes, entregue, compostos, novo, disparos, vazias)
        if novo == indireto:
            break
        entradas = [ind for ind, _ in historico]
        if novo in entradas:
            # Ciclo: sob escassez e com séries inteiras, indireto → alocação → compostos → indireto
            # pode alternar entre estados. A spec não decide (é da fatia 3). O oráculo fica com o
            # estado do ciclo de MENOR indireto total — o que menos supõe trabalho não feito — e
            # conta o disparo para que ninguém trate isto como convergência.
            ciclo = [e for _, e in historico[entradas.index(novo):]] + [estado]
            estado = min(ciclo, key=lambda e: (sum(e[4].values()), sorted(e[4].items())))
            estado[5]["ponto_fixo_oscilou"] += 1
            break
        historico.append((indireto, estado))
        indireto = novo
    else:
        disparos["ponto_fixo_nao_convergiu"] += 1
    alocado, sessoes, entregue, compostos, novo, disparos, vazias = estado

    acima = [(rot, sum(v.series for v in vagas if v.exercicio))
             for rot, vagas in sessoes
             if sum(v.series for v in vagas if v.exercicio) > teto + 1e-9]
    return Programa(
        perfil=p, divisao=divisao, teto_sessao=teto, capacidade=capacidade, alvo=alvo,
        alocado=alocado, sessoes=sessoes, indireto=novo,
        demanda_direta={m: max(0.0, alvo[m] - novo[m]) for m in ALVOS},
        entregue=entregue, compostos_semana=compostos, disparos=disparos, vagas_vazias=vazias,
        sessoes_acima_do_teto=acima, iteracoes=it,
        auditoria=auditar(alvo, entregue, novo, treinaveis, p.objetivo,
                          {m: piso_direto(m, p.nivel) for m in ALVOS}),
    )


def referencia(nivel: str, so_visiveis: bool = True) -> Programa:
    """A configuração da tabela da spec: 4 dias upper/lower (frequência 2×), sem restrição de capacidade."""
    return gerar(Perfil(nivel=nivel, dias=4, minutos=150, sem_teto=True, so_visiveis=so_visiveis))


def cobertura(nivel: str, dias: int, minutos: int, demanda_total: float) -> float:
    """Spec, "Cobertura por faixa de tempo": dias × teto da sessão ÷ demanda direta."""
    divisao, _ = DIVISOES[dias]
    return dias * teto_sessao(Perfil(nivel=nivel, dias=dias, minutos=minutos), divisao) / demanda_total


if __name__ == "__main__":
    for nivel in NIVEIS:
        for vis in (True, False):
            r = referencia(nivel, so_visiveis=vis)
            print(f"{nivel:12} {'banco' if vis else 'pretendido':10} demanda direta {r.demanda_total:6.1f}"
                  f"  compostos {dict(sorted(r.compostos_semana.items()))}"
                  f"  vazias {len(r.vagas_vazias)}  iter {r.iteracoes}  {dict(r.disparos)}")
