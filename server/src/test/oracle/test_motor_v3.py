"""Testes da fatia 0 do motor v3. Rodar de server/src/test/oracle/:  python3 -m unittest -v test_motor_v3

Ordem: a especificação contra ela mesma (modelo à mão, cobertura) → o pipeline do simulador num
catálogo ideal (prova a lógica) → o simulador no catálogo do BANCO (prova o dado) → disparos.
Um vermelho aqui é informação: cada mensagem diz qual dos dois lados está errado.
"""
import os
import itertools
import dataclasses
import math
import unittest
from collections import Counter

import motor_v3 as mv
from modelo_a_mao import demanda_direta
from spec_v3 import (ALVOS, NIVEIS, ORDEM_SACRIFICIO, PESO_PRIORIDADE, PROTEGIDOS_ALOCACAO,
                     REFERENCIA_ALVO_TOTAL, REFERENCIA_COBERTURA, REFERENCIA_COMPOSTOS_SEMANA,
                     REFERENCIA_DEMANDA_DIRETA, SO_ISOLAMENTO)

TOLERANCIA_DIFERENCIAL = 0.5  # série por músculo (pedido do revisor)


def exercicio_ideal(nome, alvo, composto, k):
    """Um exercício sem nenhuma restrição: serve em qualquer ambiente, não pede acessório, vale até
    o avançado e tem família própria — assim o catálogo ideal continua tirando o catálogo da conta
    também depois dos atributos da V65."""
    return mv.Exercicio(nome, alvo, composto, "MACHINE", "BEGINNER", True, True,
                        prioridade=k + 1, relevancia=1, nivel_maximo="ADVANCED", ambiente="AMBOS",
                        familia=f"{alvo}_{'COMP' if composto else 'ISO'}_{k}",
                        acessorios=frozenset(),
                        # Fatia 7: catálogo ideal não participa do RIR mínimo -- isso é medido
                        # no catálogo do banco (DisparosNaMatriz), não aqui.
                        rir_minimo_seguranca=False)


def catalogo_ideal():
    """Três compostos e três isolamentos por alvo, todos alcançáveis: tira o catálogo da conta."""
    out = []
    for m in ALVOS:
        for k in range(3):
            out.append(exercicio_ideal(f"{m} iso {k}", m, False, k))
            if m not in SO_ISOLAMENTO:
                out.append(exercicio_ideal(f"{m} comp {k}", m, True, k))
    return out


def diferencas(simulado, mao):
    return {m: round(simulado.demanda_direta[m] - mao.direta[m], 2) for m in ALVOS
            if abs(simulado.demanda_direta[m] - mao.direta[m]) > TOLERANCIA_DIFERENCIAL}


class ModeloAMaoContraASpec(unittest.TestCase):
    def test_alvos_totais_sao_61_111_148(self):
        for nivel in NIVEIS:
            self.assertEqual(REFERENCIA_ALVO_TOTAL[nivel], sum(demanda_direta(nivel).alvo.values()), nivel)

    def test_demanda_direta_e_44_86_113(self):
        for nivel in NIVEIS:
            self.assertAlmostEqual(REFERENCIA_DEMANDA_DIRETA[nivel], demanda_direta(nivel).total, places=6, msg=nivel)

    def test_compostos_por_semana_batem_com_a_spec(self):
        for nivel, esperado in REFERENCIA_COMPOSTOS_SEMANA.items():
            obtido = demanda_direta(nivel).compostos_semana
            self.assertEqual(esperado, {m: obtido[m] for m in esperado}, nivel)

    def test_nenhum_coeficiente_sem_fonte_voltou_para_a_tabela(self):
        """Tripwire do `[INV]` de 2026-10-27: coeficiente sem fonte nao volta.

        Este teste ERA `test_gluteo_que_nao_treina_nao_credita_o_posterior`, e provava que o
        ponto fixo impedia o gluteo sem treino de creditar o posterior. O par
        `gluteo -> posterior` 0,2 foi REMOVIDO por falta de fonte, entao aquela assercao virou
        tautologia. Reaproveitado como guarda: se alguem devolver qualquer um dos tres pares
        removidos, isto falha. O posterior e o antebraco tem de receber ZERO.
        """
        for nivel in NIVEIS:
            d = demanda_direta(nivel)
            self.assertEqual(0, d.indireto["POSTERIOR"], "gluteo -> posterior voltou? " + nivel)
            self.assertEqual(0, d.indireto["FOREARMS"], "-> antebraco voltou? " + nivel)
            # e o alvo cheio tem de chegar como demanda direta nos dois
            self.assertEqual(d.alvo["FOREARMS"], d.direta["FOREARMS"], nivel)
            self.assertEqual(d.alvo["POSTERIOR"], d.direta["POSTERIOR"], nivel)

    def test_serie_fracionada_acima_do_piso_e_recusada(self):
        # caminho de falha: a composição não inventa arredondamento
        from modelo_a_mao import composto_da_sessao
        with self.assertRaises(ValueError):
            composto_da_sessao(5.5)


class OrdemDeSacrificioEhFonteUnica(unittest.TestCase):
    """`[INV]` da Fatia 8 (2026-10-24): ORDEM_SACRIFICIO e SEMPRE derivada de PESO_PRIORIDADE,
    nunca lista mantida a mao -- as duas nunca podem discordar, por construcao."""

    def test_ordem_bate_com_o_peso_crescente_e_desempate_por_alvos_index(self):
        pesos = [PESO_PRIORIDADE[m] for m in ORDEM_SACRIFICIO]
        self.assertEqual(sorted(pesos), pesos)
        self.assertEqual([], [m for m in ORDEM_SACRIFICIO if m in PROTEGIDOS_ALOCACAO])

    def test_ordem_de_hoje_tem_glutes_e_biceps_na_posicao_que_o_peso_manda(self):
        # Antes da Fatia 8 a lista era fixa e discordava do peso aqui: GLUTES (peso 0,7) vinha
        # ANTES de BICEPS/TRICEPS (peso 0,5/0,55) -- a derivacao corrige.
        self.assertEqual(
            ["FOREARMS", "CALVES", "BICEPS", "TRICEPS", "GLUTES", "SHOULDERS"],
            ORDEM_SACRIFICIO,
        )


class CoberturaContraASpec(unittest.TestCase):
    def test_tabela_de_cobertura_celula_a_celula(self):
        erradas = []
        for (nivel, minutos), linha in REFERENCIA_COBERTURA.items():
            demanda = REFERENCIA_DEMANDA_DIRETA[nivel]
            for dias, esperado in zip(range(2, 7), linha):
                obtido = round(100 * mv.cobertura(nivel, dias, minutos, demanda))
                if obtido != esperado:
                    erradas.append((nivel, minutos, dias, esperado, obtido))
        self.assertEqual([], erradas)

    def test_manter_corta_o_teto_e_sessenta_mais_iniciante_tambem(self):
        base = mv.teto_sessao(mv.Perfil(nivel="INTERMEDIATE", dias=4, minutos=75), "UPPER_LOWER")
        manter = mv.teto_sessao(mv.Perfil(objetivo="MAINTAIN", nivel="INTERMEDIATE", dias=4, minutos=75), "UPPER_LOWER")
        idoso = mv.teto_sessao(mv.Perfil(nivel="BEGINNER", idade=65, dias=4, minutos=75), "UPPER_LOWER")
        self.assertEqual((18, 18 * 0.6, 16 * 0.75), (base, manter, idoso))


class SimuladorNoCatalogoIdeal(unittest.TestCase):
    """Prova a LÓGICA do pipeline: com o catálogo fora da conta, ele tem que chegar à mão."""

    def setUp(self):
        self._real = mv.CATALOGO
        mv.CATALOGO = catalogo_ideal()

    def tearDown(self):
        mv.CATALOGO = self._real

    def test_diferencial_contra_o_modelo_a_mao(self):
        for nivel in NIVEIS:
            with self.subTest(nivel=nivel):
                self.assertEqual({}, diferencas(mv.referencia(nivel), demanda_direta(nivel)))

    def test_nenhuma_vaga_vazia_nem_papel_trocado(self):
        for nivel in NIVEIS:
            r = mv.referencia(nivel)
            self.assertEqual(([], 0), (r.vagas_vazias, r.disparos["papel_trocado"]), nivel)

    def test_indireto_sai_do_entregue_nao_do_alocado(self):
        # caminho de falha do bug do indirect_from(d1): tirar o composto do programa tem que
        # tirar o crédito, mesmo com o alocado intacto
        r = mv.referencia("INTERMEDIATE")
        for _, vagas in r.sessoes:
            for v in vagas:
                if v.alvo == "BACK":
                    v.exercicio = None
        _, compostos, indireto = mv.medir_entregue(r.sessoes)
        self.assertNotIn("BACK", compostos)
        self.assertEqual(0, indireto["BICEPS"])

    def test_alocacao_sob_escassez_nao_poda_no_fim(self):
        # alocado (inteiro) e entregue têm que bater músculo a músculo — nada sai depois do
        # arredondamento, exceto o que sobe ao piso do isolamento
        r = mv.gerar(mv.Perfil(nivel="ADVANCED", dias=4, minutos=60))
        self.assertGreater(r.disparos["escassez"], 0)
        for m, s in r.alocado.items():
            self.assertGreaterEqual(r.entregue.get(m, 0), s, m)
        self.assertLessEqual(sum(r.alocado.values()), r.capacidade)


class SimuladorNoCatalogoDoBanco(unittest.TestCase):
    """Prova o DADO: o mesmo diferencial, sobre catalogo_banco.csv (export do banco com a V64)."""

    def test_diferencial_contra_o_modelo_a_mao(self):
        for nivel in NIVEIS:
            with self.subTest(nivel=nivel):
                r = mv.referencia(nivel)
                self.assertEqual({}, diferencas(r, demanda_direta(nivel)),
                                 f"compostos {dict(r.compostos_semana)} trocas {r.disparos['papel_trocado']}")

    def test_toda_vaga_da_referencia_tem_exercicio_do_papel_certo(self):
        """Zero vaga vazia, e papel trocado só no buraco enumerado.

        Até a V65 este teste acusava BACK ISOLAMENTO: o Pulldown com Braços Estendidos estava
        marcado como composto, e as costas ficavam sem isolamento nenhum. A curadoria corrigiu, e
        a simulação expôs um segundo buraco — POSTERIOR sem composto BEGINNER na academia — que o
        Stiff com Halteres em BEGINNER fechou no mesmo dia.
        """
        for nivel in NIVEIS:
            with self.subTest(nivel=nivel):
                r = mv.referencia(nivel)
                trocas = [(rot, v.alvo, v.papel) for rot, vagas in r.sessoes for v in vagas
                          if v.exercicio and v.exercicio.composto != (v.papel in mv.COMPOSTOS)]
                self.assertEqual(([], []), (r.vagas_vazias, trocas))

    def test_cada_exercicio_do_pool_tem_um_alvo_do_vocabulario(self):
        self.assertTrue(mv.CATALOGO)
        self.assertEqual([], [e.nome for e in mv.CATALOGO if e.alvo not in ALVOS])


PERFIS = [mv.Perfil(objetivo=o, nivel=n, dias=d, minutos=t, idade=i, ambiente=a)
          for o, n, d, t, i, a in itertools.product(
              ["GAIN_MUSCLE", "LOSE_FAT", "MAINTAIN", "GENERAL_HEALTH"], NIVEIS, range(2, 7),
              [45, 60, 75], [30, 65], ["ACADEMIA", "CASA"])]
# Fatia 10: a matriz do FOCO. Eixo limitado a nenhum (a matriz de cima) / peito / ombro -- e o que o
# tripwire de `ombro_vira_isolamento` exige (a regra le o composto de PEITO do dia); os oito grupos
# do quiz custariam tempo e tamanho sem ganho. Fora INICIANTE: o foco nao vale para ele
# (`mv.foco_efetivo`), seria o mesmo perfil da matriz de cima outra vez. 2 x 2 x 240 = 960 perfis.
PERFIS_FOCO = [mv.Perfil(objetivo=o, nivel=n, dias=d, minutos=t, idade=i, ambiente=a, foco=(f,))
               for f in ("CHEST", "SHOULDERS")
               for o, n, d, t, i, a in itertools.product(
                   ["GAIN_MUSCLE", "LOSE_FAT", "MAINTAIN", "GENERAL_HEALTH"], NIVEIS[1:], range(2, 7),
                   [45, 60, 75], [30, 65], ["ACADEMIA", "CASA"])]
# `ombro_vira_isolamento` voltou para esta lista na Fatia 10 (2026-10-28): era inalcancavel nos 720
# perfis sem foco (peito_composto_do_dia maximo 7, a regra pede 8) e o foco em PEITO a alcanca --
# ver test_ombro_vira_isolamento_so_dispara_com_foco_em_peito. Ela e medida na UNIAO das duas
# matrizes (`disparos_na_matriz_com_foco`), porque nos 720 ela continua zero, de proposito.
REGRAS_DE_SEGURANCA = ["ombro_teto_do_desenvolvimento", "um_empurrar_com_barra_por_sessao",
                       "ombro_vira_isolamento"]


def disparos_na_matriz():
    total = Counter()
    for p in PERFIS:
        total.update(mv.gerar(p).disparos)
    return total


def disparos_na_matriz_com_foco():
    """Disparos nos 960 perfis COM foco (a matriz de cima fica de fora)."""
    total = Counter()
    for p in PERFIS_FOCO:
        total.update(mv.gerar(p).disparos)
    return total


class AuditoriaDoEfetivo(unittest.TestCase):
    """Etapa [7] (Fatia 9 antecipada, 2026-10-27): `efetivo = direto + indireto` e o status.

    A peca existe porque a grandeza que responde "este programa serve?" nao existia no modelo --
    o motor media direto e indireto em separado e nunca somava os dois. Os numeros abaixo sao a
    medicao da matriz no dia em que ela foi construida: se mudarem, o motor mudou de prescricao.
    """

    def test_todo_musculo_sai_com_status_e_os_invariantes_valem(self):
        violacoes = []
        for p in PERFIS:
            r = mv.gerar(p)
            if len(r.auditoria) != len(ALVOS):
                violacoes.append(("faltou musculo", p))
            for m, a in r.auditoria.items():
                if a.status == mv.ATENDIDO and a.alvo > 0 and a.efetivo < a.alvo - 1e-9:
                    violacoes.append(("ATENDIDO abaixo do alvo", p, m))
                if a.status == mv.MANUTENCAO and a.efetivo < a.minimo - 1e-9:
                    violacoes.append(("MANUTENCAO abaixo do piso", p, m))
                if a.atingiu_minimo != (a.efetivo >= a.minimo - 1e-9):
                    violacoes.append(("atingiu_minimo inconsistente", p, m))
        self.assertEqual([], violacoes)

    def test_a_auditoria_nao_muda_o_programa(self):
        # caminho de falha: se auditar() tivesse efeito colateral, a segunda chamada divergiria
        r = mv.gerar(mv.Perfil(nivel="INTERMEDIATE", dias=4, minutos=60))
        antes = dict(r.entregue)
        de_novo = mv.auditar(r.alvo, r.entregue, r.indireto, list(r.alocado), "GAIN_MUSCLE")
        self.assertEqual(antes, r.entregue)
        self.assertEqual({m: a.efetivo for m, a in r.auditoria.items()},
                         {m: a.efetivo for m, a in de_novo.items()})

    def test_quem_mantem_e_medido_contra_o_MV_e_por_isso_aprova(self):
        """O limiar depende do OBJETIVO (decisao de 2026-10-27).

        MAINTAIN/BEGINNER tem alvo 4,8 no peito contra uma coluna de iniciante de 8. Medido
        contra o MEV, reprovava por design -- 660 pares nos 720. Medido contra o MV (4,8),
        aprova: prescrever abaixo do MEV nao e erro em manutencao, e o que manutencao significa.
        """
        r = mv.gerar(mv.Perfil(objetivo="MAINTAIN", nivel="BEGINNER", dias=4, minutos=60))
        peito = r.auditoria["CHEST"]
        self.assertAlmostEqual(4.8, peito.minimo, places=9)
        self.assertEqual(mv.ATENDIDO, peito.status)
        self.assertTrue(peito.atingiu_minimo)
        self.assertNotIn("CHEST", [a.musculo for a in r.abaixo_do_minimo])

    def test_o_mesmo_peito_pedido_como_ganho_de_massa_reprova(self):
        """Caminho de falha do teste acima: o limiar e a unica coisa que muda."""
        self.assertAlmostEqual(4.8, mv.minimo_efetivo("CHEST", "MAINTAIN"), places=9)
        self.assertEqual(8.0, mv.minimo_efetivo("CHEST", "GAIN_MUSCLE"))
        # saude geral NAO e manutencao: usa o MEV cheio
        self.assertEqual(8.0, mv.minimo_efetivo("CHEST", "GENERAL_HEALTH"))

    def test_a_matriz_de_hoje_tem_estes_numeros(self):
        status = Counter()
        perfis_reprovados = 0
        for p in PERFIS:
            r = mv.gerar(p)
            status.update(a.status for a in r.auditoria.values())
            if r.abaixo_do_minimo:
                perfis_reprovados += 1
        # MEDIDO EM 2026-10-27, com a Fatia 8 fechada (piso direto, piso prescritivel e a
        # restricao de extensao do triceps ja dentro). Sai de PROVISORIO.
        #
        # Historico do que cada rodada mudou aqui:
        #   * CORE removido do motor: SEM_CATALOGO saiu da matriz (os 720 eram todos dele, um por
        #     perfil). O status continua existindo -- e a defesa para um alvo que PERCA cobertura
        #     -- mas nao dispara mais. Nenhum outro numero mudou nessa remocao.
        #   * contabilidade honesta (3 pares sem fonte removidos, gluteo 8/10/12): ATENDIDO
        #     3613 -> 2982, ABAIXO_DO_MINIMO 1040 -> 1396, NAO_TREINADO 447 -> 692, reprovados
        #     367 -> 417. O +245 em NAO_TREINADO e EXATAMENTE o antebraco, que deixou de ser
        #     falsamente coberto pelo credito sem fonte: identidade contabil, nao coincidencia.
        #   * piso direto + piso prescritivel + extensao de cotovelo (D-1 fechado): ATENDIDO
        #     2982 -> 2905, MANUTENCAO 2130 -> 2107, ABAIXO_DO_MINIMO 1396 -> 1429, NAO_TREINADO
        #     692 -> 759, reprovados 417 -> 451. Os numeros PIORAM de proposito: o piso
        #     prescritivel ZERA quem nao cabe no orcamento em vez de entregar 1 serie
        #     improprescritivel, e o piso direto gasta orcamento em serie direta onde antes o
        #     credito indireto fechava a conta sozinho. O que melhorou foi a honestidade do
        #     rotulo, nao o programa -- e `entregue > capacidade` saiu de 167 para 0.
        self.assertEqual(
            {"ATENDIDO": 2905, "MANUTENCAO": 2107, "ABAIXO_DO_MINIMO": 1429,
             "NAO_TREINADO": 759},
            dict(status))
        self.assertEqual(451, perfis_reprovados)

    def test_o_caso_realista_de_casa_tem_estes_outros_numeros(self):
        """O numero de PRODUTO, ao lado do numero de PIOR CASO do teste acima.

        A matriz dos 720 declara CASA sem acessorio nenhum -- o pior caso, nao o tipico (desde
        2026-10-22 o onboarding pergunta, V66). Isto mede o mesmo com um banco declarado, que e
        o acessorio mais comum, e e este par de numeros que deve ser citado:

          * pior caso  : 2188 pares abaixo do piso, 451 perfis, 219 vagas vazias
          * com banco  : 2066 pares abaixo do piso, 382 perfis,   0 vagas vazias

        MEDIDO EM 2026-10-27, com a Fatia 8 fechada.

        O que o par prova: o deficit de VOLUME e robusto a suposicao de acessorio (-6%), e por
        isso as medicoes anteriores sobre os 720 continuam valendo; as VAGAS VAZIAS, nao -- sao
        100% artefato da suposicao, e um banco sozinho as zera. Citar 239 vagas vazias como
        problema de produto e erro de leitura, nao de motor.
        """
        pares = perfis = vazias = 0
        for p in PERFIS:
            q = p if p.ambiente != "CASA" else dataclasses.replace(
                p, acessorios=frozenset({"requires_bench"}))
            r = mv.gerar(q)
            n = len(r.abaixo_do_minimo)
            pares += n
            if n:
                perfis += 1
            vazias += len(r.vagas_vazias)

        self.assertEqual(2066, pares)
        self.assertEqual(382, perfis)
        self.assertEqual(0, vazias, "um banco declarado zera toda vaga vazia de CASA")


class FechamentoDaFatia8(unittest.TestCase):
    """Os tres fechos da fatia: orcamento respeitado, piso prescritivel, piso direto honesto."""

    def test_nenhum_perfil_entrega_mais_series_do_que_a_capacidade(self):
        """⭐ CRITERIO DE PRONTO DA FATIA 8. Era 167/720; tem de ser 0.

        O estouro nunca foi excesso de volume: era aumento de volume acontecendo FORA do
        orcamento. Tres lugares subiam series depois da etapa [3] ter fechado a conta (o piso do
        isolamento no `montar_semana`, a panturrilha e o antebraco pelo mesmo caminho); a correcao
        foi a MESMA nos tres -- mover a decisao para dentro do orcamento, na etapa [3].
        """
        estouraram = [(p.nivel, p.dias, p.minutos, p.ambiente,
                       sum(r.entregue.values()), r.capacidade)
                      for p in PERFIS for r in [mv.gerar(p)]
                      if sum(r.entregue.values()) > math.floor(r.capacidade + 1e-9)]
        self.assertEqual([], estouraram)

    def test_todo_musculo_entregue_sai_com_zero_ou_com_o_piso_do_papel(self):
        """Serie fracionada nao e prescritivel: nenhum exercicio do catalogo tem 1 serie."""
        minimo = mv.PISO_SERIES["ISOLAMENTO"]
        quebrados = [(p.nivel, p.ambiente, m, n)
                     for p in PERFIS for r in [mv.gerar(p)]
                     for m, n in r.alocado.items() if 0 < n < minimo]
        self.assertEqual([], quebrados)

    def test_piso_prescritivel_zera_quem_nao_cabe_no_orcamento(self):
        """Caminho de FALHA do piso: quando nao cabe, o musculo sai -- nao estoura a capacidade.

        Orcamento de 6 series com tres musculos pedindo 4+1+1: o primeiro ja consome 4, o segundo
        sobe a 2 (total 6, couber exato) e o terceiro nao cabe e e ZERADO.
        """
        disparos = Counter()
        saida = mv.piso_prescritivel({"CHEST": 4, "BICEPS": 1, "FOREARMS": 1},
                                     ["CHEST", "BICEPS", "FOREARMS"], 6.0, disparos)
        self.assertEqual({"CHEST": 4, "BICEPS": 2}, saida)
        self.assertEqual(1, disparos["sobe_ao_piso_dentro_do_orcamento"])
        self.assertEqual(1, disparos["zerado_por_nao_caber_no_piso"])

    def test_piso_prescritivel_nao_inverte_a_prioridade(self):
        """⭐ Regressao medida (2026-10-27): o gluteo era ZERADO e o triceps subia no lugar dele.

        Orcamento de 8 com quadriceps em 6 e dois pendentes de 1. Medindo o orcamento contra a
        fracao dos pendentes (o jeito errado), a serie do triceps ocupava a vaga na vez do
        gluteo: 6+1 = 7, mais 1 do gluteo = 8... o gluteo nao cabia, era zerado, e o triceps
        subia com o que ele liberou -- 18 vezes nos 720, contra a decisao de produto que poe o
        gluteo acima dele. Medindo contra quem JA esta decidido, o gluteo cabe e o triceps sai.
        """
        disparos = Counter()
        saida = mv.piso_prescritivel({"QUADS": 6, "GLUTES": 1, "TRICEPS": 1},
                                     ["QUADS", "GLUTES", "TRICEPS"], 8.0, disparos)
        self.assertEqual({"QUADS": 6, "GLUTES": 2}, saida)
        self.assertEqual(8, sum(saida.values()), "nunca por fora do orcamento")

    def test_piso_prescritivel_sem_teto_sobe_todo_mundo_e_nao_estoura(self):
        """O perfil de REFERENCIA chega com capacidade infinita -- `math.floor(inf)` estourava."""
        disparos = Counter()
        saida = mv.piso_prescritivel({"CHEST": 1, "FOREARMS": 1}, ["CHEST", "FOREARMS"],
                                     math.inf, disparos)
        self.assertEqual({"CHEST": 2, "FOREARMS": 2}, saida)
        self.assertEqual(0, disparos["zerado_por_nao_caber_no_piso"])

    def test_piso_direto_so_existe_nos_tres_musculos_e_fora_do_iniciante(self):
        self.assertEqual(2, mv.piso_direto("TRICEPS", "INTERMEDIATE"))
        self.assertEqual(2, mv.piso_direto("GLUTES", "ADVANCED"))
        self.assertEqual(0, mv.piso_direto("TRICEPS", "BEGINNER"), "isencao do iniciante")
        self.assertEqual(0, mv.piso_direto("CHEST", "ADVANCED"), "nao recebe credito indireto")

    def test_nenhum_atendido_sem_serie_direta_passa_sem_a_marca(self):
        """⭐ Asserção (b) do D-1: ATENDIDO com direto = 0 só passa marcado `piso_direto_negado`.

        Escopo INTERMEDIATE/ADVANCED, que e onde o piso direto existe. O iniciante fica FORA por
        isencao declarada (regra geral do hibrido, Iversen 2021/Gentil) -- e a isencao tem preco
        medido: 57 pares BEGINNER/TRICEPS saem ATENDIDO com zero serie direta, cobertos so pelo
        credito das pressoes. Nao e defeito; e a regra. Esta no debito de mensagem ao usuario,
        junto com os casos de gluteo.
        """
        mudos = [(p.nivel, p.ambiente, m)
                 for p in PERFIS if p.nivel != "BEGINNER" for r in [mv.gerar(p)]
                 for m, a in r.auditoria.items()
                 if a.status == "ATENDIDO" and a.direto == 0 and not a.piso_direto_negado]
        self.assertEqual([], mudos)

    def test_a_vaga_de_isolamento_do_triceps_e_sempre_extensao_de_cotovelo(self):
        """`[REGRA]` do motor: MERGULHO (empurrar composto) nunca ocupa a vaga direta."""
        errados = [(p.nivel, p.ambiente, v.exercicio.nome)
                   for p in PERFIS for r in [mv.gerar(p)]
                   for _, vagas in r.sessoes for v in vagas
                   if v.alvo == "TRICEPS" and v.papel == "ISOLAMENTO" and v.exercicio
                   and v.exercicio.familia in mv.TRICEPS_SEM_EXTENSAO]
        self.assertEqual([], errados)

    def test_a_exigencia_do_cotovelo_ganha_da_relevancia_1_do_mergulho(self):
        """A `[REGRA]` roda ANTES da relevancia 1, de proposito: o MERGULHO podia ser a escolha
        primaria da curadoria e ainda assim nao serve ao papel. Equivalente Kotlin:
        `CatalogFillerTest`. So compostos, para a vaga cair na inversao de papel -- e por ela que
        o MERGULHO chega perto da vaga direta na vida real.
        """
        catalogo = [mv.Exercicio("Mergulho Sentado na Maquina", "TRICEPS", True, "MACHINE", "BEGINNER", True, True,
                         prioridade=1, relevancia=1, nivel_maximo="ADVANCED",
                         ambiente="AMBOS", familia="MERGULHO", acessorios=frozenset(),
                         rir_minimo_seguranca=False),
                    mv.Exercicio("Supino Fechado com Barra", "TRICEPS", True, "BARBELL", "BEGINNER", True, True,
                         prioridade=9, relevancia=2, nivel_maximo="ADVANCED",
                         ambiente="AMBOS", familia="SUPINO_FECHADO", acessorios=frozenset(),
                         rir_minimo_seguranca=False)]
        vagas = [mv.Vaga("TRICEPS", "ISOLAMENTO", 2)]
        disparos, vazias = Counter(), []
        mv.preencher([("Dia", vagas)], catalogo, mv.Perfil(), disparos, vazias)

        self.assertEqual("Supino Fechado com Barra", vagas[0].exercicio.nome)
        self.assertEqual(1, disparos["papel_trocado"])
        self.assertEqual(1, disparos["musculo_sem_relevancia_1"], "o relevancia 1 era o MERGULHO")
        self.assertEqual(0, disparos["triceps_sem_extensao_de_cotovelo"], "havia alternativa")

    def test_mergulho_volta_para_a_vaga_do_triceps_quando_e_o_unico_candidato(self):
        """Caminho de FALHA da `[REGRA]`: a exigencia que esvaziaria o pool cede -- melhor
        mergulho que vaga vazia -- e o disparo registra para a lacuna de curadoria aparecer na
        medicao em vez de desaparecer num pool vazio. Equivalente Kotlin: `CatalogFillerTest`.
        """
        catalogo = [mv.Exercicio("Mergulho no Banco", "TRICEPS", True, "BODYWEIGHT", "BEGINNER", True, True,
                         prioridade=10, relevancia=2, nivel_maximo="ADVANCED",
                         ambiente="AMBOS", familia="MERGULHO", acessorios=frozenset(),
                         rir_minimo_seguranca=False)]
        vagas = [mv.Vaga("TRICEPS", "ISOLAMENTO", 2)]
        disparos, vazias = Counter(), []
        mv.preencher([("Dia", vagas)], catalogo, mv.Perfil(), disparos, vazias)

        self.assertEqual("Mergulho no Banco", vagas[0].exercicio.nome)
        self.assertEqual(1, disparos["triceps_sem_extensao_de_cotovelo"])
        self.assertEqual([], vazias, "a exigencia cede, nao deixa vaga vazia")

    def test_o_motor_nao_escolhe_a_familia_do_triceps_pela_ciencia(self):
        """⭐ Tripwire da decisao do Rafael (2026-10-27, opcao 4): acima-da-cabeca NAO e do motor.

        Implementada como filtro no `preencher()`, a preferencia por TRICEPS_FRANCES rodava ANTES
        da `[REGRA]` da relevancia 1 e esvaziava o pool: 471/471 vagas em 2 exercicios, com
        TRICEPS_PULLEY (relevancia 1, prioridade 1 -- a escolha primaria da curadoria) inalcancavel
        em TODOS os 720 perfis. Motor decide QUANTO; curadoria decide QUAL.

        Este teste fixa a distribuicao que a CURADORIA produz. Se alguem reintroduzir a preferencia
        no motor, a academia colapsa em TRICEPS_FRANCES e isto fica vermelho. Mudar a curadoria
        (a `[PROPOSTA]` em debitos.md) tambem derruba este teste -- de proposito: a mudanca passa
        a ser visivel e deliberada, com o numero novo medido.
        """
        familias = Counter(v.exercicio.familia
                           for p in PERFIS for r in [mv.gerar(p)]
                           for _, vagas in r.sessoes for v in vagas
                           if v.alvo == "TRICEPS" and v.papel == "ISOLAMENTO" and v.exercicio)
        self.assertEqual({"TRICEPS_FRANCES": 237, "TRICEPS_PULLEY": 180,
                          "TRICEPS_TESTA": 24, "TRICEPS_COICE": 24}, dict(familias))


class DisparosNaMatriz(unittest.TestCase):
    """Regra de segurança que nunca dispara é código morto com cara de proteção."""

    def test_toda_regra_de_seguranca_dispara(self):
        total = disparos_na_matriz() + disparos_na_matriz_com_foco()
        mortas = [r for r in REGRAS_DE_SEGURANCA if total[r] == 0]
        self.assertEqual([], mortas, f"disparos em {len(PERFIS)}+{len(PERFIS_FOCO)} perfis: {dict(total)}")

    def test_ombro_vira_isolamento_so_dispara_com_foco_em_peito(self):
        """⭐ REAUDITORIA da Fatia 10 (2026-10-28) -- o tripwire da Fatia 7 virou vermelho, como
        previsto, e foi trocado por esta afirmacao em dois lados. **2a REAUDITORIA na Fatia 11
        (2026-10-29)**: o piso dos demais grupos (que a Fatia 10 deixou para depois, exatamente
        porque "o bonus sozinho e quase inerte") libera capacidade de sessao para o peito --
        8 perfis -> 72, so com foco em PEITO, nunca com OMBRO. Numero maior NAO e regressao: e a
        propria protecao funcionando (o foco finalmente move algo, que era o ponto da Fatia 11).

        Sem foco, `peito_composto_do_dia` maximo e 7 e a regra pede 8 (limite = 10 - peito < 3):
        ZERO disparos nos 720 (nao mudou com o piso -- sem foco, o piso nunca entra em jogo).
        Com foco em PEITO e o piso dos demais ativo, o peito ganha espaco de sessao que antes ia
        para ombro/bracos e a regra passa a disparar em mais perfis e mais niveis (INTERMEDIATE e
        ADVANCED agora, nao so INTERMEDIATE) e mais divisoes (4/5/6 dias). Foco em OMBRO continua
        sem alcancar (nao mexe no peito).

        Os dois lados importam: se o zero dos 720 virar positivo, alguem fez a regra disparar sem
        foco (a Fatia 7 registrou isto como impossivel); se o lado do foco virar zero, a regra
        voltou a ser morta e a reauditoria precisa ser refeita."""
        self.assertEqual(0, disparos_na_matriz()["ombro_vira_isolamento"])
        disparou = [p for p in PERFIS_FOCO if mv.gerar(p).disparos["ombro_vira_isolamento"]]
        self.assertEqual(72, len(disparou))
        self.assertEqual({("CHEST",)}, {p.foco for p in disparou})
        self.assertEqual({"INTERMEDIATE", "ADVANCED"}, {p.nivel for p in disparou})
        self.assertEqual({4, 5, 6}, {p.dias for p in disparou})
        self.assertEqual(136, disparos_na_matriz_com_foco()["ombro_vira_isolamento"])

    def test_ponto_fixo_termina_e_e_deterministico_em_todo_perfil(self):
        # Oscilar é permitido e CONTADO (ver gerar(): ciclo sob escassez, pendência da fatia 3);
        # o que não pode é estourar as iterações nem depender da ordem de execução.
        total = disparos_na_matriz()
        self.assertEqual(0, total["ponto_fixo_nao_convergiu"])
        for p in PERFIS[::37]:
            a, b = mv.gerar(p), mv.gerar(p)
            self.assertEqual((a.alocado, a.entregue, a.indireto), (b.alocado, b.entregue, b.indireto), p)


class FocoDoQuiz(unittest.TestCase):
    """Fatia 10: o foco e SO um bonus de alvo (+3 pequeno, +6 grande). A fase de dois mesociclos, o
    piso nos demais grupos e a protecao na alocacao sao da Fatia 11."""

    def test_sem_foco_o_alvo_e_o_de_antes(self):
        a = mv.alvos(mv.Perfil(nivel="INTERMEDIATE"))
        self.assertEqual((14.0, 12.0, 12.0), (a["CHEST"], a["SHOULDERS"], a["BICEPS"]))

    def test_grande_ganha_6_e_pequeno_ganha_3(self):
        """O bonus continua so dos alvos em foco. Fatia 11: os DEMAIS vao ao piso (VOLUME[m][0]),
        exceto BACK -- isento porque o foco inclui CHEST (empurrar); ver
        `test_back_fica_no_normal_quando_a_enfase_e_em_empurrar` para o isolado dessa isencao."""
        sem, com = mv.alvos(mv.Perfil()), mv.alvos(mv.Perfil(foco=("CHEST", "BICEPS")))
        self.assertEqual(6.0, com["CHEST"] - sem["CHEST"])
        self.assertEqual(3.0, com["BICEPS"] - sem["BICEPS"])
        self.assertEqual(sem["BACK"], com["BACK"], "BACK isento -- foco inclui CHEST")
        for m in ALVOS:
            if m not in ("CHEST", "BICEPS", "BACK"):
                from spec_v3 import VOLUME
                self.assertEqual(float(VOLUME[m][0]), com[m], m)

    def test_back_fica_no_normal_quando_a_enfase_e_em_empurrar(self):
        """[REGRA] Fatia 11: "puxar nunca cai abaixo de empurrar" e a RAZAO de uma ISENCAO, nao
        uma desigualdade imposta -- BACK fica no volume NORMAL (nao vai ao piso) quando a enfase e
        em CHEST ou SHOULDERS. Com enfase em LEGS (nenhum dos dois), BACK vai ao piso como
        qualquer outro musculo de fora do foco."""
        sem = mv.alvos(mv.Perfil())
        for grupo_empurrar in ("CHEST", "SHOULDERS"):
            com = mv.alvos(mv.Perfil(foco=(grupo_empurrar,)))
            self.assertEqual(sem["BACK"], com["BACK"], grupo_empurrar)
        com_pernas = mv.alvos(mv.Perfil(foco=("LEGS",)))
        from spec_v3 import VOLUME
        self.assertEqual(float(VOLUME["BACK"][0]), com_pernas["BACK"],
                         "sem enfase em empurrar, BACK vai ao piso como os outros")

    def test_grande_e_derivado_do_peso_com_a_fronteira_declarada(self):
        """[REGRA] 2026-10-28: grande = PESO_PRIORIDADE >= FOCO_FRONTEIRA_GRANDE, nao lista a mao."""
        from spec_v3 import FOCO_FRONTEIRA_GRANDE, GRANDES, PESO_PRIORIDADE
        self.assertEqual({"CHEST", "BACK", "SHOULDERS", "QUADS", "POSTERIOR", "GLUTES"}, set(GRANDES))
        self.assertEqual(GRANDES, {m for m in ALVOS if PESO_PRIORIDADE[m] >= FOCO_FRONTEIRA_GRANDE})
        # a fronteira fica entre TRICEPS (0,55) e GLUTES (0,7): nenhum musculo na borda
        self.assertTrue(0.55 < FOCO_FRONTEIRA_GRANDE < 0.7)
        self.assertTrue(all(abs(w - FOCO_FRONTEIRA_GRANDE) >= 0.049 for w in PESO_PRIORIDADE.values()))
        # ombro e gluteo sao grandes: o bonus (antes do MRV) e 6, nao 3
        bonus = mv.bonus_de_foco(mv.Perfil(foco=("SHOULDERS", "GLUTES")))
        self.assertEqual({"SHOULDERS": 6, "GLUTES": 6}, bonus)
        self.assertEqual({"BICEPS": 3}, mv.bonus_de_foco(mv.Perfil(foco=("BICEPS",))))

    def test_o_bonus_respeita_o_mrv(self):
        # avancado: peito 18 + 6 = 24 > 22; costas 20 + 6 = 26 > 25; antebraco 9 + 3 = 12 = MRV
        a = mv.alvos(mv.Perfil(nivel="ADVANCED", foco=("CHEST", "BACK")))
        self.assertEqual((22.0, 25.0), (a["CHEST"], a["BACK"]))
        self.assertEqual(12.0, mv.alvos(mv.Perfil(nivel="ADVANCED", foco=("FOREARMS",)))["FOREARMS"])

    def test_iniciante_e_saude_geral_ignoram_o_foco(self):
        # mesma elegibilidade do motor antigo (`effectiveFocus`) e da spec ("so intermediario e avancado")
        for p in (mv.Perfil(nivel="BEGINNER"), mv.Perfil(objetivo="GENERAL_HEALTH")):
            com = dataclasses.replace(p, foco=("CHEST",))
            self.assertEqual(mv.alvos(p), mv.alvos(com), p)
            self.assertEqual((), mv.foco_efetivo(com))

    def test_no_maximo_dois_grupos(self):
        """So os 2 primeiros do tuplo valem -- SHOULDERS fica de fora do foco (so CHEST+BACK
        contam) e, Fatia 11, vai ao PISO (6.0) por estar fora do foco -- nao fica em 12 (normal)
        porque a isencao de puxar/empurrar e so para BACK, nao para quem SOBRA do proprio foco."""
        a = mv.alvos(mv.Perfil(foco=("CHEST", "BACK", "SHOULDERS")))
        self.assertEqual((20.0, 22.0, 6.0), (a["CHEST"], a["BACK"], a["SHOULDERS"]))  # BACK 16+6; ombro no piso

    def test_pernas_cobre_quadriceps_posterior_e_panturrilha(self):
        sem, com = mv.alvos(mv.Perfil()), mv.alvos(mv.Perfil(foco=("LEGS",)))
        # UM bonus por escolha (+6: LEGS cobre alvo grande), repartido pelo peso 1,0 / 0,9 / 0,4
        self.assertEqual({"QUADS": 3.0, "POSTERIOR": 2.0, "CALVES": 1.0},
                         {m: com[m] - sem[m] for m in ("QUADS", "POSTERIOR", "CALVES")})
        self.assertEqual(6.0, sum(com[m] - sem[m] for m in ("QUADS", "POSTERIOR", "CALVES")),
                         "o bonus nao pode ser somado cheio em cada alvo")
        # Fatia 11: GLUTES e foco PROPRIO, nao parte de LEGS -- fica de fora do foco escolhido e
        # vai ao piso como qualquer outro musculo nao focado (LEGS nao e empurrar, BACK tambem
        # vai ao piso aqui -- sem isencao).
        from spec_v3 import VOLUME
        self.assertEqual(float(VOLUME["GLUTES"][0]), com["GLUTES"], "gluteo fora do foco, vai ao piso")
        self.assertEqual(float(VOLUME["BACK"][0]), com["BACK"], "sem enfase em empurrar, BACK vai ao piso")

    def test_distribuicao_do_bonus_soma_exata_e_e_deterministica(self):
        for total in (3, 6):
            for alvos in (("QUADS", "POSTERIOR", "CALVES"), ("CHEST",), ("BICEPS", "TRICEPS")):
                d = mv.distribuir_bonus(total, alvos)
                self.assertEqual(total, sum(d.values()), (total, alvos))
                self.assertTrue(all(v >= 0 for v in d.values()))
        self.assertEqual({"CHEST": 6}, mv.distribuir_bonus(6, ("CHEST",)))

    def test_alvo_coberto_por_dois_grupos_nao_soma_duas_vezes(self):
        self.assertEqual(mv.alvos(mv.Perfil(foco=("CHEST",))), mv.alvos(mv.Perfil(foco=("CHEST", "CHEST"))))

    def test_grupo_desconhecido_falha_alto(self):
        with self.assertRaises(KeyError):
            mv.alvos(mv.Perfil(foco=("PESCOCO",)))

    def test_o_foco_chega_ao_programa(self):
        # sem teto de capacidade o alvo maior vira mais series entregues -- o bonus nao fica no papel
        base = mv.gerar(mv.Perfil(sem_teto=True))
        foco = mv.gerar(mv.Perfil(sem_teto=True, foco=("CHEST",)))
        self.assertGreater(foco.entregue["CHEST"], base.entregue["CHEST"])


class ReferenciaDos720(unittest.TestCase):
    """⭐ Fatia 9: a referência que o Kotlin lê (`referencia_720.txt`) tem de ser a saída do
    oráculo DE HOJE.

    Nasceu de um defeito real: o `MotorV3FidelidadeCatalogoRealIntegrationTest` guardava 12 perfis
    escritos no próprio .kt, gerados em 23/10. A Fatia 8 mudou o oráculo (piso direto, glúteo
    8/10/12, coeficientes removidos, CORE fora) e os 12 passaram a divergir — 12 de 12 — sem que
    nenhum teste visse, porque o `integrationTest` não rodou. Referência embutida em teste
    envelhece em silêncio.

    Aqui a regra é a inversa: o arquivo é GERADO, e este teste fica vermelho no mesmo instante em
    que o oráculo (ou o `catalogo_banco.csv`) muda sem a referência acompanhar. A mensagem diz
    o comando. Quem muda o oráculo regenera e REVISA o diff — é o diff que mostra o que a mudança
    fez nos 720 perfis, e é contra ele que o Kotlin passa a ser cobrado.
    """

    def test_referencia_dos_720_esta_em_dia(self):
        import gerar_referencia_720 as g
        esperado = g.gerar_texto()
        atual = ""
        if os.path.exists(g.CAMINHO):
            with open(g.CAMINHO, encoding="utf-8") as f:
                atual = f.read()
        if atual != esperado:
            velhos = sum(1 for a, b in zip(atual.split("\nP|"), esperado.split("\nP|")) if a != b)
            self.fail("referencia_720.txt esta VELHA (%d bloco(s) de perfil divergem). Rode, de "
                      "server/src/test/oracle/:  python3 gerar_referencia_720.py  -- e revise o "
                      "diff: ele e o efeito da sua mudanca nos 720 perfis." % velhos)

    def test_referencia_do_foco_esta_em_dia(self):
        import gerar_referencia_720 as g
        esperado = g.gerar_texto_foco()
        atual = ""
        if os.path.exists(g.CAMINHO_FOCO):
            with open(g.CAMINHO_FOCO, encoding="utf-8") as f:
                atual = f.read()
        if atual != esperado:
            self.fail("referencia_foco.txt esta VELHA. Rode, de server/src/test/oracle/:  "
                      "python3 gerar_referencia_720.py  -- e revise o diff.")

    def test_a_referencia_do_foco_cobre_exatamente_a_matriz_do_foco(self):
        import gerar_referencia_720 as g
        ids = [l.split("|")[1] for l in g.gerar_texto_foco().split("\n") if l.startswith("P|")]
        self.assertEqual(960, len(ids))
        self.assertEqual(960, len(set(ids)), "perfil duplicado na matriz do foco")
        self.assertEqual([g.perfil_id(p) for p in PERFIS_FOCO], ids)
        self.assertTrue(all("-f" in i for i in ids))
        self.assertTrue(all("-f" not in l.split("|")[1] for l in g.gerar_texto().split("\n") if l.startswith("P|")),
                        "a matriz sem foco nao pode ter id de foco")

    def test_a_referencia_cobre_exatamente_a_matriz(self):
        """Caminho de falha do gerador: perfil faltando/sobrando seria varredura que nao varre."""
        import gerar_referencia_720 as g
        ids = [l.split("|")[1] for l in g.gerar_texto().split("\n") if l.startswith("P|")]
        self.assertEqual(720, len(ids))
        self.assertEqual(720, len(set(ids)), "perfil duplicado na matriz")
        self.assertEqual([g.perfil_id(p) for p in PERFIS], ids)


if __name__ == "__main__":
    unittest.main()


# ----------------------------------------------------------------------------- V65: a escolha

MATRIZ_DIAS = [2, 3, 4, 5, 6]
MATRIZ_MINUTOS = [45, 60, 75]


def programas(ambiente="ACADEMIA", acessorios=None):
    for nivel, dias, minutos in itertools.product(NIVEIS, MATRIZ_DIAS, MATRIZ_MINUTOS):
        yield nivel, mv.gerar(mv.Perfil(nivel=nivel, dias=dias, minutos=minutos,
                                        ambiente=ambiente, acessorios=acessorios))


def escolhidos(programa):
    return [(rot, v) for rot, vagas in programa.sessoes for v in vagas if v.exercicio]


class EscolhaPelaCuradoria(unittest.TestCase):
    """A V65 trocou `is_base` + ordem alfabética por prioridade, relevância, família e ambiente.
    Estes testes provam que a troca pegou, e enumeram o único buraco que ela expôs."""

    def test_academia_nao_deixa_vaga_vazia(self):
        vazias = [(n, p.vagas_vazias) for n, p in programas() if p.vagas_vazias]
        self.assertEqual([], vazias)

    def test_academia_nao_repete_familia_na_sessao(self):
        repetidas = []
        for nivel, p in programas():
            for rot, vagas in p.sessoes:
                fams = [v.exercicio.familia for v in vagas if v.exercicio]
                dup = [f for f, q in Counter(fams).items() if q > 1]
                if dup:
                    repetidas.append((nivel, rot, dup))
        self.assertEqual([], repetidas)

    def test_ambiente_nao_se_mistura(self):
        """Quem treina na academia não recebe exercício de casa, e vice-versa."""
        for ambiente, proibido in (("ACADEMIA", "CASA"), ("CASA", "ACADEMIA")):
            errados = [(n, v.exercicio.nome) for n, p in programas(ambiente, mv.TODOS_ACESSORIOS)
                       for _, v in escolhidos(p) if v.exercicio.ambiente == proibido]
            self.assertEqual([], errados, ambiente)

    def test_acessorio_que_o_usuario_nao_tem_nao_aparece(self):
        """Casa sem nada declarado: nenhum exercício escolhido pode exigir banco, barra ou step."""
        errados = [(n, v.exercicio.nome, sorted(v.exercicio.acessorios))
                   for n, p in programas("CASA", frozenset())
                   for _, v in escolhidos(p) if v.exercicio.acessorios]
        self.assertEqual([], errados)

    def test_a_primeira_vaga_do_musculo_sai_da_relevancia_1(self):
        """Quando existe candidato de relevância 1 para o papel, a abertura do músculo é dele."""
        errados = []
        for nivel, p in programas():
            candidatos = mv.pool(p.perfil)
            for rot, vagas in p.sessoes:
                vistos = set()
                for v in vagas:
                    if not v.exercicio or v.alvo in vistos:
                        continue
                    vistos.add(v.alvo)
                    quer_composto = v.papel in mv.COMPOSTOS
                    tem_base = any(e.alvo == v.alvo and e.composto == quer_composto and e.relevancia == 1
                                   for e in candidatos)
                    if tem_base and v.exercicio.relevancia != 1:
                        errados.append((nivel, rot, v.alvo, v.exercicio.nome))
        self.assertEqual([], errados)

    def test_academia_abre_peito_no_prioridade_1(self):
        """Caminho feliz, concreto: o intermediário na academia abre o peito pelo Supino Reto com
        Barra, que é a prioridade 1 do grupo — não por ordem alfabética, não por sorteio."""
        p = mv.gerar(mv.Perfil(nivel="INTERMEDIATE", dias=4, minutos=60))
        primeiro = next(v.exercicio for _, vagas in p.sessoes for v in vagas
                        if v.alvo == "CHEST" and v.exercicio)
        self.assertEqual("Supino Reto com Barra", primeiro.nome)
        self.assertEqual(1, primeiro.prioridade)

    def test_academia_nao_troca_o_papel_de_nenhuma_vaga(self):
        """⭐ Nenhuma vaga de composto recebe isolamento na academia, em nenhum dos 45 perfis.

        Nasceu enumerando um buraco: POSTERIOR não tinha composto de nível BEGINNER na academia
        (Stiff e Terra eram INTERMEDIATE, e o único hinge BEGINNER da lista estava marcado CASA
        por ser peso do corpo), então o iniciante recebia mesa flexora na vaga de composto e nunca
        treinava extensão de quadril — 105 dos 360 perfis. O Stiff com Halteres passou a BEGINNER
        e o buraco fechou; a exceção saiu daqui no mesmo dia em que entrou.
        """
        trocados = {(n, v.alvo, v.papel) for n, p in programas()
                    for _, v in escolhidos(p) if (v.papel in mv.COMPOSTOS) != v.exercicio.composto}
        self.assertEqual(set(), trocados)


def _exercicio_minimo(nome, alvo, composto, movement_pattern, familia="FAM", relevancia=1, prioridade=1):
    return mv.Exercicio(nome=nome, alvo=alvo, composto=composto, equipamento="MACHINE",
                        nivel="BEGINNER", visivel=True, base=True, prioridade=prioridade,
                        relevancia=relevancia, nivel_maximo="ADVANCED", ambiente="ACADEMIA",
                        familia=familia, acessorios=frozenset(), rir_minimo_seguranca=False,
                        movement_pattern=movement_pattern)


class SelecaoDePuxadaHorizontalNoBack(unittest.TestCase):
    """Fatia 11, [REGRA]: com 2+ vagas de BACK na semana, ao menos uma e HORIZONTAL_PULL. Mesma
    forma da extensao de cotovelo do triceps (Fatia 8) -- selecao, nao volume: a vaga ja existe,
    so troca QUAL exercicio a ocupa."""

    def test_academia_sem_foco_nunca_fica_sem_puxada_horizontal(self):
        """Caminho feliz, no catalogo real: dos 720, 186 precisavam de correcao (medido antes desta
        regra existir) -- apos a correcao, nenhum perfil com 2+ vagas de BACK fica sem horizontal."""
        sem_horizontal = []
        for nivel, p in programas():
            vagas_back = [v for _, v in escolhidos(p) if v.alvo == "BACK"]
            if len(vagas_back) >= 2 and not any(
                    v.exercicio.movement_pattern == "HORIZONTAL_PULL" for v in vagas_back):
                sem_horizontal.append((nivel, p.perfil.ambiente))
        self.assertEqual([], sem_horizontal)

    def test_com_uma_vaga_so_a_regra_nao_se_aplica(self):
        """Caminho de nao-aplicacao, explicito: 1 vaga de BACK na semana nao e balanceada contra
        nada -- a correcao nem olha o exercicio, so conta o disparo."""
        vertical = _exercicio_minimo("Barra Fixa", "BACK", True, "VERTICAL_PULL")
        v = mv.Vaga(alvo="BACK", papel="COMPOSTO", series=4, exercicio=vertical)
        sessoes = [("Dia 1", [v])]
        disparos = Counter()
        mv.aplicar_puxada_horizontal_semanal(sessoes, [vertical], disparos)
        self.assertEqual(1, disparos["puxada_horizontal_regra_nao_aplicavel"])
        self.assertEqual("Barra Fixa", v.exercicio.nome)

    def test_duas_vagas_verticais_trocam_uma_para_horizontal(self):
        """Caminho feliz, de unidade: 2 vagas verticais, 1 horizontal disponivel -- uma delas
        troca, a outra fica, a serie de cada vaga nao muda."""
        vertical = _exercicio_minimo("Barra Fixa", "BACK", True, "VERTICAL_PULL", familia="VERTICAL")
        horizontal = _exercicio_minimo("Remada Curvada com Barra", "BACK", True, "HORIZONTAL_PULL",
                                       familia="HORIZONTAL", prioridade=3)
        v1 = mv.Vaga(alvo="BACK", papel="COMPOSTO", series=4, exercicio=vertical)
        v2 = mv.Vaga(alvo="BACK", papel="COMPOSTO", series=4, exercicio=vertical)
        sessoes = [("Dia 1", [v1]), ("Dia 2", [v2])]
        disparos = Counter()
        mv.aplicar_puxada_horizontal_semanal(sessoes, [vertical, horizontal], disparos)
        self.assertEqual(1, disparos["puxada_horizontal_semanal_corrigida"])
        padroes = {v1.exercicio.movement_pattern, v2.exercicio.movement_pattern}
        self.assertEqual({"VERTICAL_PULL", "HORIZONTAL_PULL"}, padroes)
        self.assertEqual(4, v1.series)
        self.assertEqual(4, v2.series)

    def test_sem_candidato_horizontal_nao_quebra_e_conta_disparo(self):
        """Caminho de falha: pool sem HORIZONTAL_PULL nenhum para BACK -- nada e trocado, o
        disparo registra que a correcao nao pode ser aplicada (nao deveria ocorrer no catalogo
        real: `test_academia_sem_foco_nunca_fica_sem_puxada_horizontal` prova que nao ocorre)."""
        v1 = _exercicio_minimo("Barra Fixa", "BACK", True, "VERTICAL_PULL")
        v2 = _exercicio_minimo("Puxada Alta", "BACK", True, "VERTICAL_PULL", prioridade=2)
        vaga1 = mv.Vaga(alvo="BACK", papel="COMPOSTO", series=4, exercicio=v1)
        vaga2 = mv.Vaga(alvo="BACK", papel="COMPOSTO", series=4, exercicio=v2)
        sessoes = [("Dia 1", [vaga1]), ("Dia 2", [vaga2])]
        disparos = Counter()
        mv.aplicar_puxada_horizontal_semanal(sessoes, [v1, v2], disparos)
        self.assertEqual(1, disparos["puxada_horizontal_sem_candidato"])
        self.assertEqual("Barra Fixa", vaga1.exercicio.nome)
        self.assertEqual("Puxada Alta", vaga2.exercicio.nome)

    def test_familia_repetida_no_dia_nao_impede_a_correcao_quando_e_a_unica_saida(self):
        """Mesma regra de `preencher()`: familia inedita no dia e preferencia, nao exigencia -- se
        so sobrar uma familia ja usada no dia, a correcao ainda troca (nao deixa vertical parado
        por excesso de zelo com familia)."""
        vertical = _exercicio_minimo("Barra Fixa", "BACK", True, "VERTICAL_PULL", familia="REMADA")
        horizontal = _exercicio_minimo("Remada Curvada com Barra", "BACK", True, "HORIZONTAL_PULL",
                                       familia="REMADA", prioridade=3)
        # as duas vagas no MESMO dia, mesma familia disponivel nos dois candidatos -- nao ha
        # alternativa de familia inedita, e a correcao deve trocar mesmo assim.
        v1 = mv.Vaga(alvo="BACK", papel="COMPOSTO", series=4, exercicio=vertical)
        v2 = mv.Vaga(alvo="BACK", papel="COMPOSTO", series=3, exercicio=vertical)
        sessoes = [("Dia 1", [v1, v2])]
        disparos = Counter()
        mv.aplicar_puxada_horizontal_semanal(sessoes, [vertical, horizontal], disparos)
        self.assertEqual(1, disparos["puxada_horizontal_semanal_corrigida"])
        padroes = {v1.exercicio.movement_pattern, v2.exercicio.movement_pattern}
        self.assertEqual({"VERTICAL_PULL", "HORIZONTAL_PULL"}, padroes)
