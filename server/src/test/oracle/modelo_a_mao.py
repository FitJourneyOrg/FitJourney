"""Modelo à mão da demanda direta — o método da especificação, escrito À PARTE do simulador.

Especificação, "Como a demanda direta foi recalculada" (frequência 2×, sem restrição de capacidade):
  1. séries de composto por semana de cada doador, pela regra de composição (2 a 4 séries por
     exercício, piso 3 no composto e 2 no isolamento) e pela regra do ombro;
  2. indireto recebido = séries de composto do doador × coeficiente da tabela;
  3. demanda direta = max(0, alvo − indireto).
O composto do doador sai do VOLUME PRÓPRIO dele (alvo − indireto que ele recebe), por ponto fixo.
Esse ponto fixo existia para impedir o glúteo que não treina de creditar o posterior; o par
`glúteo → posterior` saiu da tabela em 2026-10-27 (sem fonte), e com ele o CICLO do grafo de
coeficientes: doadores (peito, ombro, costas, posterior, quadríceps) e recebedores (tríceps,
bíceps, glúteo) são hoje conjuntos DISJUNTOS. O laço continua aqui porque ainda converge em uma
passada e porque o motor de verdade tem outro ciclo, pelo ORÇAMENTO — medido, `ponto_fixo_oscilou`
SUBIU de 26 para 44 nos 720 perfis depois da remoção, porque o antebraço passou a disputar
capacidade de verdade (pede 3/6/9 diretas onde pedia ~0).

Não importa nada de motor_v3: é o outro lado do teste diferencial. Compartilha só spec_v3, que é
constante, não lógica.
"""
from dataclasses import dataclass

from spec_v3 import ALVOS, INDIRETO, NIVEIS, OMBRO_LIMITE, OMBRO_MINIMO_COMPOSTO, VOLUME


@dataclass
class Demanda:
    alvo: dict
    indireto: dict
    direta: dict
    compostos_semana: dict

    @property
    def total(self) -> float:
        return sum(self.direta.values())


def composto_da_sessao(series: float, estrito: bool = True) -> float:
    """Séries de composto que a regra de composição tira de `series` de um músculo com composto.

    1 exercício (até 4): âncora; 2 (5 a 8): âncora + isolamento; 3 (9+): âncora + acessório + isolamento.
    Enche o primeiro até 4 sem deixar ninguém abaixo do piso (3 composto, 2 isolamento).
    """
    if estrito and series >= 3 and series != int(series):
        # Regra escrita contra valor que ninguém verificou: a partir de 3 séries a composição só
        # existe para séries inteiras (abaixo de 3 não há composto, e a fração não muda nada). Se a
        # referência um dia produzir fração aqui, o modelo precisa ser repensado, não arredondado
        # em silêncio.
        raise ValueError(f"série fracionada na sessão: {series}")
    # Fora do modo estrito (estados transitórios do ponto fixo) a mesma fórmula vale como extensão
    # contínua; o estado convergido é sempre conferido no modo estrito.
    if series < 3:
        return 0
    if series <= 4:
        return series
    if series <= 8:
        return min(4, series - 2)
    ancora = min(4, series - 5)
    acessorio = min(4, series - ancora - 2)
    return ancora + acessorio


def demanda_direta(nivel: str, frequencia: int = 2) -> Demanda:
    i = NIVEIS.index(nivel)
    alvo = {m: float(VOLUME[m][i]) for m in ALVOS}
    indireto = {m: 0.0 for m in ALVOS}
    for _ in range(50):
        proprio = {m: max(0.0, alvo[m] - indireto[m]) for m in ALVOS}
        por_sessao = {m: proprio[m] / frequencia for m in INDIRETO}
        comp = {m: composto_da_sessao(por_sessao[m], estrito=False) for m in INDIRETO}
        # Ombro: no máximo (10 − composto de peito do dia); abaixo de 3, não há desenvolvimento.
        teto_ombro = OMBRO_LIMITE - comp["CHEST"]
        comp["SHOULDERS"] = min(comp["SHOULDERS"], teto_ombro)
        if comp["SHOULDERS"] < OMBRO_MINIMO_COMPOSTO:
            comp["SHOULDERS"] = 0
        semana = {m: comp[m] * frequencia for m in INDIRETO}
        novo = {m: 0.0 for m in ALVOS}
        for doador, recebe in INDIRETO.items():
            for m, k in recebe.items():
                novo[m] += semana[doador] * k
        if novo == indireto:
            for m in INDIRETO:  # o estado convergido tem que ser composição de verdade
                composto_da_sessao(por_sessao[m])
            break
        indireto = novo
    else:
        raise RuntimeError("ponto fixo do indireto não convergiu")
    direta = {m: max(0.0, alvo[m] - indireto[m]) for m in ALVOS}
    return Demanda(alvo, indireto, direta, semana)
