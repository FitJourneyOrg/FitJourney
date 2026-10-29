# -*- coding: utf-8 -*-
"""Testes da Fatia 10 (mesociclo e progressao). Rodar de server/src/test/oracle/:
    python3 -m unittest -v test_progressao_v3

Historico FALSO: cada teste monta o que a pessoa fez e confere o que o motor decide. Todo bloco tem
ao menos um caminho de falha. O espelho Kotlin le `referencia_progressao.txt` (gerada por
`gerar_referencia_progressao.py` a partir das funcoes daqui) e tem os mesmos casos escritos a mao.
"""
import os
import unittest
from collections import Counter

import motor_v3 as mv
import progressao_v3 as P
import spec_v3 as S
from progressao_v3 import (DESCE, MANTEM, SOBE, Estado, Mesociclo, Serie)


def sessao(reps, rir, n=3):
    return [Serie(reps, rir)] * n


class Arredondamento(unittest.TestCase):
    def test_vai_ao_multiplo_mais_proximo(self):
        self.assertEqual(95.0, P.para_incremento(96.0, 2.5))
        self.assertEqual(92.5, P.para_incremento(92.0, 2.5))
        self.assertEqual(8.0, P.para_incremento(7.68, 2.0))

    def test_empate_desce(self):
        # 56.25 esta exatamente entre 55 e 57.5; a carga menor e a segura
        self.assertEqual(55.0, P.para_incremento(56.25, 2.5))
        self.assertEqual(2.0, P.para_incremento(3.0, 2.0))

    def test_incremento_invalido_falha_alto(self):
        for inc in (0, -2.5):
            with self.assertRaises(ValueError):
                P.para_incremento(10.0, inc)

    def test_meio_pra_cima_das_series(self):
        self.assertEqual([1, 2, 2, 3], [P.arredondar(x) for x in (0.5, 1.5, 1.9, 2.5)])


class SaltoDaFaixa(unittest.TestCase):
    def test_as_tres_faixas_da_spec(self):
        self.assertEqual([0.10, 0.10, 0.25, 0.25, 0.50, 0.50],
                         [P.salto_da_faixa(x, False) for x in (1, 2, 3, 4, 5, 9)])

    def test_barra_livre_tem_teto_de_25(self):
        self.assertEqual([0.10, 0.25, 0.25], [P.salto_da_faixa(x, True) for x in (2, 4, 7)])

    def test_sem_excesso_nao_ha_salto(self):
        self.assertEqual([0.0, 0.0], [P.salto_da_faixa(x, False) for x in (0, -3)])


class Calibracao(unittest.TestCase):
    def test_20kg_vira_55kg_em_tres_series_e_60kg_na_terceira_exposicao(self):
        """⭐ O caso da spec (l.80, l.600): quem calibra com 20 kg e aguenta 60 kg levava ~16 sessoes
        subindo 2,5 kg. Capacidade REAL = 60 kg (10 repeticoes deixando 2). A pessoa informa o RIR
        que sente; o modelo de relato abaixo e deterministico: RIR = 2 + 10 x (1 - carga/60),
        arredondado meio pra cima, no maximo 10."""
        capacidade, alvo, inc = 60.0, 2, 2.5

        def rir_sentido(carga):
            return min(10, max(0, P.arredondar(alvo + 10 * (1 - carga / capacidade))))

        # exposicao 1: calibracao, ate 3 series (cada serie informa o RIR da carga em que foi feita)
        carga, historico = 20.0, []
        for n in (1, 2, 3):
            rir = rir_sentido(carga)
            historico.append((carga, rir))
            passo = P.passo_calibracao(carga, rir, n, alvo, False, inc)
            carga = passo.carga
            if passo.terminou:
                break
        self.assertEqual([(20.0, 9), (30.0, 7), (45.0, 5)], historico)
        self.assertEqual(55.0, carga, "20 -> 30 (+50%) -> 45 (+50%) -> 55 (+25%, empate desce)")
        self.assertLessEqual(carga, capacidade, "a calibracao nunca passa da capacidade real")
        self.assertGreaterEqual(carga / capacidade, 0.9)

        estado = Estado(carga=carga, exposicoes=1)
        # exposicao 2: a 55 kg a pessoa faz o topo da faixa com RIR 3 -> salto de 10% (1 acima do alvo)
        estado, d = P.decidir(estado, sessao(9, rir_sentido(55.0)), "COMPOSTO", "GAIN_MUSCLE", inc, False)
        self.assertEqual((SOBE, 60.0, 2), (d, estado.carga, estado.exposicoes))
        # exposicao 3 ja COMECA em 60 kg = a capacidade. Sao tres exposicoes, nao 16 sessoes.
        self.assertEqual(60.0, estado.carga)

    def test_o_caminho_antigo_levava_16_sessoes(self):
        # a contraprova do que a calibracao evita: subir 2,5 kg por sessao de 20 para 60
        self.assertEqual(16, int((60.0 - 20.0) / 2.5))

    def test_barra_livre_nao_dispara_o_salto_de_50(self):
        passo1 = P.passo_calibracao(20.0, 9, 1, 2, True, 2.5)
        self.assertEqual(25.0, passo1.carga, "teto de +25% em barra livre")
        carga = 20.0
        for n in (1, 2, 3):
            passo = P.passo_calibracao(carga, 9, n, 2, True, 2.5)
            carga = passo.carga
        self.assertLessEqual(carga, 20.0 * 1.25 ** 3 + 1e-9)
        self.assertTrue(passo.terminou)

    def test_rir_abaixo_de_4_termina_sem_mexer_na_carga(self):
        for rir in (0, 2, 3):
            passo = P.passo_calibracao(40.0, rir, 1, 2, False, 2.5)
            self.assertEqual((40.0, True), (passo.carga, passo.terminou), rir)

    def test_o_salto_nunca_arredonda_para_a_mesma_carga(self):
        # 10% de 10 kg = 1 kg, num incremento de 2,5 kg: sem o piso a calibracao travaria
        self.assertEqual(12.5, P.carga_apos_salto(10.0, 0.10, 2.5))

    def test_a_terceira_serie_sobe_e_encerra(self):
        passo = P.passo_calibracao(45.0, 9, 3, 2, False, 2.5)
        self.assertTrue(passo.terminou)
        self.assertGreater(passo.carga, 45.0)

    def test_calibrar_conveniencia_e_lista_vazia_falha_alto(self):
        e = P.calibrar(20.0, [9, 7, 5], 2, False, 2.5)
        self.assertEqual((55.0, 1, None), (e.carga, e.exposicoes, e.um_rm))
        with self.assertRaises(ValueError):
            P.calibrar(20.0, [], 2, False, 2.5)


class ProgressaoDupla(unittest.TestCase):
    def test_regra_1_sobe_so_quando_todas_as_series_batem_o_topo_no_rir_alvo(self):
        base = Estado(carga=100.0, exposicoes=10)
        # composto/ganhar massa: 6-9 reps, RIR 2
        e, d = P.decidir(base, sessao(9, 2), "COMPOSTO", "GAIN_MUSCLE", 2.5, True)
        self.assertEqual((SOBE, 102.5), (d, e.carga))
        # caminho de falha 1: UMA serie fora do topo e nada sobe
        e, d = P.decidir(base, [Serie(9, 2), Serie(9, 2), Serie(8, 2)], "COMPOSTO", "GAIN_MUSCLE", 2.5, True)
        self.assertEqual((MANTEM, 100.0), (d, e.carga))
        # caminho de falha 2: topo de repeticoes, mas mais perto da falha que o alvo (RIR 1 < 2)
        e, d = P.decidir(base, sessao(9, 1), "COMPOSTO", "GAIN_MUSCLE", 2.5, True)
        self.assertEqual((MANTEM, 100.0), (d, e.carga))

    def test_regra_2_o_aumento_e_o_menor_incremento_depois_das_tres_primeiras_exposicoes(self):
        # RIR 6 (4 acima do alvo) pediria +25% se fosse uma das tres primeiras
        e, d = P.decidir(Estado(carga=100.0, exposicoes=3), sessao(9, 6), "COMPOSTO", "GAIN_MUSCLE", 2.5, False)
        self.assertEqual((SOBE, 102.5), (d, e.carga), "a 4a exposicao ja nao tem salto grande")
        e, d = P.decidir(Estado(carga=100.0, exposicoes=2), sessao(9, 6), "COMPOSTO", "GAIN_MUSCLE", 2.5, False)
        self.assertEqual((SOBE, 125.0, 3), (d, e.carga, e.exposicoes), "a 3a exposicao ainda aceita +25%")

    def test_regra_2_o_salto_grande_usa_o_pior_rir_da_sessao(self):
        e, _ = P.decidir(Estado(carga=100.0, exposicoes=1), [Serie(9, 6), Serie(9, 3), Serie(9, 6)],
                         "COMPOSTO", "GAIN_MUSCLE", 2.5, False)
        self.assertEqual(110.0, e.carga, "min RIR = 3, 1 acima do alvo -> +10%")

    def test_regra_3_tolerancia_pos_salto_evita_o_loop_de_subir_e_devolver(self):
        """O halter que pula de 8 para 10 kg (+25%): na faixa 8-12 a pessoa faz 6 reps no RIR alvo.
        Sem a tolerancia a carga voltava para 8 e o ciclo recomecava."""
        e = Estado(carga=8.0, exposicoes=10)
        e, d = P.decidir(e, sessao(12, 1), "ACESSORIO", "GAIN_MUSCLE", 2.0, False)
        self.assertEqual((SOBE, 10.0, True), (d, e.carga, e.pos_salto))
        e, d = P.decidir(e, sessao(6, 1), "ACESSORIO", "GAIN_MUSCLE", 2.0, False)
        self.assertEqual((MANTEM, 10.0, True), (d, e.carga, e.pos_salto), "abaixo da faixa, RIR no alvo: aceita")
        e, d = P.decidir(e, sessao(7, 1), "ACESSORIO", "GAIN_MUSCLE", 2.0, False)
        self.assertEqual((MANTEM, 10.0), (d, e.carga), "segue aceitando enquanto o RIR esta no alvo")
        e, d = P.decidir(e, sessao(8, 1), "ACESSORIO", "GAIN_MUSCLE", 2.0, False)
        self.assertEqual((MANTEM, 10.0, False), (d, e.carga, e.pos_salto), "entrou na faixa: a tolerancia acaba")

    def test_regra_3_caminhos_de_falha(self):
        apos_salto = Estado(carga=10.0, exposicoes=10, pos_salto=True)
        # RIR mais dificil que o alvo: a tolerancia NAO vale, devolve um incremento
        e, d = P.decidir(apos_salto, sessao(6, 0), "ACESSORIO", "GAIN_MUSCLE", 2.0, False)
        self.assertEqual((DESCE, 8.0, False), (d, e.carga, e.pos_salto))
        # sem salto recente, abaixo da faixa devolve ([PROPOSTA]: o "devolver" implicito da spec)
        e, d = P.decidir(Estado(carga=10.0, exposicoes=10), sessao(6, 1), "ACESSORIO", "GAIN_MUSCLE", 2.0, False)
        self.assertEqual((DESCE, 8.0), (d, e.carga))
        # nunca desce abaixo de um incremento
        e, _ = P.decidir(Estado(carga=2.0, exposicoes=10), sessao(1, 0), "ACESSORIO", "GAIN_MUSCLE", 2.0, False)
        self.assertEqual(2.0, e.carga)

    def test_sem_subir_conta_ate_a_proxima_subida(self):
        e = Estado(carga=50.0, exposicoes=5)
        for esperado in (1, 2, 3):
            e, _ = P.decidir(e, sessao(7, 2), "COMPOSTO", "GAIN_MUSCLE", 2.5, False)
            self.assertEqual(esperado, e.sem_subir)
        e, d = P.decidir(e, sessao(9, 2), "COMPOSTO", "GAIN_MUSCLE", 2.5, False)
        self.assertEqual((SOBE, 0), (d, e.sem_subir))

    def test_entradas_invalidas_falham_alto(self):
        with self.assertRaises(ValueError):
            P.decidir(Estado(carga=None), sessao(9, 2), "COMPOSTO", "GAIN_MUSCLE", 2.5, False)
        with self.assertRaises(ValueError):
            P.decidir(Estado(carga=50.0), [], "COMPOSTO", "GAIN_MUSCLE", 2.5, False)
        with self.assertRaises(KeyError):
            P.decidir(Estado(carga=50.0), sessao(9, 2), "PESCOCO", "GAIN_MUSCLE", 2.5, False)

    def test_a_prescricao_vem_da_spec(self):
        self.assertEqual((6, 9, 120, 2), P.prescricao("GAIN_MUSCLE", "COMPOSTO"))
        self.assertEqual((12, 18, 75, 3), P.prescricao("GENERAL_HEALTH", "ISOLAMENTO"))
        self.assertEqual(P.prescricao("GAIN_MUSCLE", "ACESSORIO"), P.prescricao("LOSE_FAT", "ACESSORIO"),
                         "perder gordura e identico a ganhar massa (item 18)")


class ReducaoNoDia(unittest.TestCase):
    def test_quatro_por_cento_por_ponto_de_rir_abaixo_do_alvo(self):
        self.assertEqual((95.0, False), P.reducao_intrasessao(100.0, 1, 2, 2.5))   # 96 -> 95
        self.assertEqual((92.5, False), P.reducao_intrasessao(100.0, 0, 2, 2.5))   # 92 -> 92,5

    def test_no_alvo_ou_mais_facil_nada_muda(self):
        self.assertEqual((100.0, False), P.reducao_intrasessao(100.0, 2, 2, 2.5))
        self.assertEqual((100.0, False), P.reducao_intrasessao(100.0, 5, 2, 2.5))

    def test_quando_o_incremento_nao_existe_a_reducao_e_anulada_e_avisa(self):
        """Checklist: "arredondamento da reducao de 4% quando o incremento nao existe". 4% de 8 kg
        e 0,32 kg, num halter de 2 kg: nao ha carga menor. A carga fica e a regra DIZ que nao agiu."""
        self.assertEqual((8.0, True), P.reducao_intrasessao(8.0, 1, 2, 2.0))

    def test_nunca_abaixo_de_um_incremento(self):
        nova, _ = P.reducao_intrasessao(2.5, 0, 3, 2.5)
        self.assertGreaterEqual(nova, 2.5)


class UmRm(unittest.TestCase):
    def test_epley_com_rir(self):
        self.assertAlmostEqual(100.0 * (1 + 10 / 30), P.um_rm_epley(100.0, 8, 2), places=9)

    def test_composto_usa_a_melhor_serie_e_isolamento_nao_usa(self):
        series = [Serie(8, 2), Serie(6, 2), Serie(8, 3)]
        self.assertAlmostEqual(100.0 * (1 + 11 / 30), P.estimar_um_rm(100.0, series, "COMPOSTO"), places=9)
        self.assertIsNone(P.estimar_um_rm(20.0, series, "ISOLAMENTO"), "regra 5: isolamento so tem progressao dupla")
        self.assertIsNone(P.estimar_um_rm(100.0, [], "COMPOSTO"))

    def test_o_1rm_acompanha_o_exercicio_e_entra_no_estado(self):
        e, _ = P.decidir(Estado(carga=100.0, exposicoes=9), sessao(8, 2), "COMPOSTO", "GAIN_MUSCLE", 2.5, True)
        self.assertAlmostEqual(100.0 * (1 + 10 / 30), e.um_rm, places=9)
        e2, _ = P.decidir(Estado(carga=20.0, exposicoes=9), sessao(14, 1), "ISOLAMENTO", "GAIN_MUSCLE", 1.0, False)
        self.assertIsNone(e2.um_rm)


class MesocicloDeQuatroSemanas(unittest.TestCase):
    def test_a_semana_4_fecha_e_abre_o_proximo(self):
        m, vistos = Mesociclo(), []
        for _ in range(9):
            vistos.append((m.numero, m.semana))
            m = P.concluir_semana(m)
        self.assertEqual([(1, 1), (1, 2), (1, 3), (1, 4), (2, 1), (2, 2), (2, 3), (2, 4), (3, 1)], vistos)

    def test_deload_na_semana_4_so_para_intermediario_e_avancado(self):
        for nivel in ("INTERMEDIATE", "ADVANCED"):
            self.assertEqual([False, False, False, True],
                             [P.deload_programado(nivel, Mesociclo(1, s)) for s in (1, 2, 3, 4)], nivel)
        # o iniciante NAO tem deload por calendario: a semana 4 dele e semana normal
        self.assertEqual([False] * 4, [P.deload_programado("BEGINNER", Mesociclo(1, s)) for s in (1, 2, 3, 4)])

    def test_deload_e_metade_do_volume_meio_pra_cima_e_no_minimo_uma(self):
        self.assertEqual([2, 1, 2, 3, 1], [P.series_do_dia(s, True) for s in (3, 2, 4, 5, 1)])
        self.assertEqual([3, 2, 4, 1], [P.series_do_dia(s, False) for s in (3, 2, 4, 1)],
                         "fora do deload as series nao mudam: nao sobem dentro do ciclo")

    def test_o_mesociclo_do_perfil_e_o_1_quando_nao_ha_estado(self):
        self.assertEqual(1, P.mesociclo_para_perfil(None))
        self.assertEqual(3, P.mesociclo_para_perfil(Mesociclo(3, 2)))


class MesocicloAtualRealTiraO60MaisDoConservadorPermanente(unittest.TestCase):
    """Fecha o debito registrado desde a Fatia 7: ate aqui `mesociclo_atual = None` contava como 1o
    mesociclo e TODO 60+ ficava em modo conservador (fator de idade 0,75, RIR >= 3) para sempre."""

    def perfil(self, **kw):
        return mv.Perfil(nivel="INTERMEDIATE", dias=4, minutos=60, idade=65, **kw)

    def test_sem_estado_o_60_mais_continua_no_lado_seguro(self):
        p = self.perfil(mesociclo_atual=P.mesociclo_para_perfil(None))
        self.assertTrue(mv.modo_conservador_automatico(p))
        self.assertAlmostEqual(0.75 * 18, mv.teto_sessao(p, "UPPER_LOWER"), places=9)
        self.assertLess(mv.teto_sessao(p, "UPPER_LOWER"),
                        mv.teto_sessao(self.perfil(mesociclo_atual=2), "UPPER_LOWER"))

    def test_depois_de_um_mesociclo_concluido_ele_sai_do_conservador(self):
        m = Mesociclo()
        for _ in range(4):
            m = P.concluir_semana(m)
        p = self.perfil(mesociclo_atual=P.mesociclo_para_perfil(m))
        self.assertEqual(2, p.mesociclo_atual)
        self.assertFalse(mv.modo_conservador_automatico(p))
        self.assertEqual(mv.teto_sessao(mv.Perfil(nivel="INTERMEDIATE", dias=4, minutos=60, idade=30), "UPPER_LOWER"),
                         mv.teto_sessao(p, "UPPER_LOWER"), "fator de idade 1,0 fora do 1o mesociclo")

    def test_no_meio_do_primeiro_mesociclo_ele_segue_conservador(self):
        m = P.concluir_semana(P.concluir_semana(Mesociclo()))
        self.assertTrue(mv.modo_conservador_automatico(self.perfil(mesociclo_atual=P.mesociclo_para_perfil(m))))

    def test_iniciante_60_mais_segue_conservador_em_qualquer_mesociclo(self):
        p = mv.Perfil(nivel="BEGINNER", dias=4, minutos=60, idade=65, mesociclo_atual=5)
        self.assertTrue(mv.modo_conservador_automatico(p))
        self.assertAlmostEqual(0.75 * 16, mv.teto_sessao(p, "UPPER_LOWER"), places=9)

    def test_quem_tem_menos_de_60_nao_muda_com_o_mesociclo(self):
        a = mv.teto_sessao(mv.Perfil(idade=59, mesociclo_atual=1), "UPPER_LOWER")
        b = mv.teto_sessao(mv.Perfil(idade=59, mesociclo_atual=2), "UPPER_LOWER")
        self.assertEqual(a, b)


class ContinuidadeDoPrograma(unittest.TestCase):
    """Registro da Fatia 6 (escopo da Fatia 10): o `preencher()` roda UMA vez por programa."""

    def programa(self, **kw):
        return mv.gerar(mv.Perfil(nivel="INTERMEDIATE", dias=4, minutos=60, **kw))

    def test_o_mesmo_exercicio_em_cada_posicao_nas_oito_semanas_de_dois_mesociclos(self):
        prog = self.programa()
        m, semanas = Mesociclo(), []
        for _ in range(8):
            semanas.append(P.semana_do_programa(prog, m))
            m = P.concluir_semana(m)
        nomes = [[(rot, [(v.alvo, v.papel, v.exercicio) for v in vagas]) for rot, vagas in s] for s in semanas]
        for s in nomes[1:]:
            self.assertEqual(nomes[0], s, "o exercicio de cada posicao nao pode mudar dentro do programa")
        self.assertTrue(nomes[0] and all(vagas for _, vagas in nomes[0]))

    def test_o_preencher_nao_e_chamado_de_novo_a_cada_semana(self):
        chamadas = Counter()
        original = mv.preencher

        def contando(*a, **k):
            chamadas["preencher"] += 1
            return original(*a, **k)

        mv.preencher = contando
        try:
            prog = self.programa()
            apos_gerar = chamadas["preencher"]
            m = Mesociclo()
            for _ in range(8):
                P.semana_do_programa(prog, m)
                m = P.concluir_semana(m)
        finally:
            mv.preencher = original
        self.assertGreaterEqual(apos_gerar, 1)
        self.assertEqual(apos_gerar, chamadas["preencher"], "ler o programa nao pode re-rodar o preenchimento")

    def test_as_series_nao_sobem_dentro_do_ciclo_e_a_semana_4_e_metade(self):
        prog = self.programa()
        s = {sem: P.semana_do_programa(prog, Mesociclo(1, sem)) for sem in (1, 2, 3, 4)}
        self.assertEqual(s[1], s[2])
        self.assertEqual(s[2], s[3])
        total = lambda sem: sum(v.series for _, vagas in s[sem] for v in vagas)
        base = sum(v.series for _, vagas in prog.sessoes for v in vagas if v.exercicio)
        self.assertEqual(base, total(1))
        self.assertLess(total(4), base)
        self.assertGreaterEqual(total(4), base / 2.0, "meio pra cima e minimo 1: nunca menos que a metade")

    def test_iniciante_nao_tem_semana_4_de_deload_mas_pode_pedir_o_deload_por_estagnacao(self):
        prog = mv.gerar(mv.Perfil(nivel="BEGINNER", dias=3, minutos=60))
        normal, s4 = P.semana_do_programa(prog, Mesociclo(1, 1)), P.semana_do_programa(prog, Mesociclo(1, 4))
        self.assertEqual(normal, s4)
        deload = P.semana_do_programa(prog, Mesociclo(1, 2), deload_extra=True)
        self.assertLess(sum(v.series for _, vs in deload for v in vs), sum(v.series for _, vs in normal for v in vs))

    def test_vaga_vazia_nao_e_prescrita(self):
        prog = self.programa()
        prog.sessoes[0][1][0].exercicio = None
        n_semana = sum(len(vagas) for _, vagas in P.semana_do_programa(prog, Mesociclo()))
        self.assertEqual(sum(1 for _, vagas in prog.sessoes for v in vagas if v.exercicio), n_semana)


class AncoraDoIniciante(unittest.TestCase):
    """EXCECAO NOMEADA a `[REGRA]` de exercicios fixos dentro do programa: a unica troca de ancora
    no meio do programa e a do iniciante por estagnacao PERSISTENTE depois de um deload com a mesma
    ancora, mais o diagnostico. Ver docs/regras/01-motor-de-geracao.md (Fatia 10)."""

    def test_progredindo_enquanto_a_carga_sobe(self):
        self.assertEqual(P.ESTAGNACAO_PROGRESSANDO, P.situacao_iniciante([SOBE, SOBE, MANTEM, SOBE, MANTEM, MANTEM], False))

    def test_tres_sessoes_sem_subir_pedem_deload_com_a_mesma_ancora(self):
        self.assertEqual(P.ESTAGNACAO_DELOAD, P.situacao_iniciante([SOBE, MANTEM, MANTEM, MANTEM], False))
        self.assertEqual(P.ESTAGNACAO_DELOAD, P.situacao_iniciante([SOBE, DESCE, MANTEM, DESCE], False),
                         "cair tambem conta como nao subir")

    def test_duas_sessoes_ainda_nao_sao_estagnacao(self):
        self.assertEqual(P.ESTAGNACAO_PROGRESSANDO, P.situacao_iniciante([SOBE, MANTEM, MANTEM], False))

    def test_depois_do_deload_espera_tres_sessoes_antes_de_julgar(self):
        h = [SOBE, MANTEM, MANTEM, MANTEM, P.DELOAD]
        self.assertEqual(P.ESTAGNACAO_AGUARDANDO, P.situacao_iniciante(h, True))
        self.assertEqual(P.ESTAGNACAO_AGUARDANDO, P.situacao_iniciante(h + [MANTEM, MANTEM], True))

    def test_se_a_carga_volta_a_subir_e_retomada_e_nao_ha_troca(self):
        h = [MANTEM, MANTEM, MANTEM, P.DELOAD, MANTEM, SOBE]
        self.assertEqual(P.ESTAGNACAO_RETOMOU, P.situacao_iniciante(h, True))

    def test_troca_exige_deload_mais_estagnacao_persistente_mais_diagnostico(self):
        persistente = [MANTEM, MANTEM, MANTEM, P.DELOAD, MANTEM, MANTEM, MANTEM]
        self.assertEqual(P.ESTAGNACAO_TROCA, P.situacao_iniciante(persistente, True))
        # caminho de falha 1: sem diagnostico, NAO troca (trocar antes compararia exercicios diferentes)
        self.assertEqual(P.ESTAGNACAO_DIAGNOSTICO, P.situacao_iniciante(persistente, False))
        # caminho de falha 2: sem deload anterior, nunca ha troca -- mesmo com diagnostico
        self.assertEqual(P.ESTAGNACAO_DELOAD, P.situacao_iniciante([MANTEM] * 8, True))
        # caminho de falha 3: deload com retomada no meio nao e persistente
        self.assertNotEqual(P.ESTAGNACAO_TROCA, P.situacao_iniciante([MANTEM] * 3 + [P.DELOAD, SOBE, MANTEM, MANTEM], True))

    def test_so_o_ultimo_deload_conta(self):
        h = [MANTEM] * 3 + [P.DELOAD, MANTEM, MANTEM, MANTEM, SOBE, SOBE] + [MANTEM] * 3
        self.assertEqual(P.ESTAGNACAO_DELOAD, P.situacao_iniciante(h, True),
                         "retomou e estagnou de novo: nao e troca (sinal de promocao, Fatia 11), pede novo deload")


class DiagnosticoDePromocaoDoIniciante(unittest.TestCase):
    """Fatia 11, `[REGRA]`: deload-como-diagnostico (ESPECIFICACAO, "[PENDENTE 3]"). Opera sobre o
    historico de UMA ancora -- mesma entrada de `situacao_iniciante`."""

    def test_sem_deload_nenhum_nao_ha_o_que_diagnosticar(self):
        self.assertIsNone(P.diagnostico_promocao_iniciante([SOBE, SOBE, MANTEM]))
        self.assertIsNone(P.diagnostico_promocao_iniciante([SOBE, MANTEM, MANTEM]),
                           "estagnou mas ainda nao deloadou -- isto dispara o 1o deload, nao diagnostica nada")

    def test_dentro_da_janela_pos_deload_ainda_nao_diagnostica(self):
        h = [MANTEM] * 3 + [P.DELOAD, MANTEM, MANTEM]
        self.assertIsNone(P.diagnostico_promocao_iniciante(h))

    def test_subiu_e_reestagnou_promove(self):
        h = [MANTEM] * 3 + [P.DELOAD, SOBE, MANTEM, MANTEM, MANTEM]
        self.assertEqual(P.PROMOVER, P.diagnostico_promocao_iniciante(h))

    def test_ainda_subindo_depois_do_deload_nao_diagnostica(self):
        h = [MANTEM] * 3 + [P.DELOAD, SOBE, SOBE]
        self.assertIsNone(P.diagnostico_promocao_iniciante(h),
                           "RETOMOU, nao reestagnou ainda: nada a diagnosticar")

    def test_nunca_subiu_depois_do_1o_deload_e_recuperacao_sem_aviso_extra(self):
        h = [MANTEM] * 3 + [P.DELOAD, MANTEM, MANTEM, MANTEM]
        self.assertEqual(P.RECUPERACAO, P.diagnostico_promocao_iniciante(h))

    def test_dois_deloads_consecutivos_sem_retomada_vira_recuperacao_com_aviso(self):
        h = [MANTEM] * 3 + [P.DELOAD, MANTEM, MANTEM, MANTEM, P.DELOAD, MANTEM, MANTEM, MANTEM]
        self.assertEqual(P.RECUPERACAO_SEM_RETOMADA, P.diagnostico_promocao_iniciante(h))

    def test_retomada_entre_dois_deloads_nao_conta_como_sem_retomada(self):
        # subiu entre o 1o e o 2o deload (retomou, estagnou de novo -> ja seria PROMOVER antes do
        # 2o deload acontecer de verdade; aqui simulamos so para garantir que o "antes" do ultimo
        # deload com SOBE nao e tratado como "sem retomada")
        h = [MANTEM] * 3 + [P.DELOAD, SOBE, MANTEM, MANTEM, MANTEM, P.DELOAD, MANTEM, MANTEM, MANTEM]
        self.assertEqual(P.RECUPERACAO, P.diagnostico_promocao_iniciante(h),
                          "houve retomada antes do ultimo deload -- nao e 'dois deloads CONSECUTIVOS sem retomada'")


class DiagnosticoDePromocaoDoPerfil(unittest.TestCase):
    """Agregacao entre ancoras -- `[REGRA]` (decisao do Rafael, 2026-10-29): 2 ancoras em
    PROMOVER, ou 1 se o perfil so tiver uma ancora. Motivo: nivel barra acesso a barra livre, e
    uma unica ancora estagnando nao representa a maturidade da pessoa inteira."""

    def test_sem_nenhum_diagnostico_nao_ha_nada_a_reportar(self):
        self.assertIsNone(P.diagnostico_promocao_do_perfil([None, None, None]))

    def test_uma_ancora_promovendo_nao_basta_com_mais_de_uma_ancora_no_perfil(self):
        # 1 PROMOVER nao fecha o limiar de 2 (perfil com 3 ancoras) -- cai para o pior caso de
        # recuperacao entre as outras, que e exatamente o que deve acontecer (nao fica mudo)
        self.assertEqual(P.RECUPERACAO, P.diagnostico_promocao_do_perfil([None, P.RECUPERACAO, P.PROMOVER]))

    def test_duas_ancoras_promovendo_promove(self):
        self.assertEqual(P.PROMOVER,
                          P.diagnostico_promocao_do_perfil([P.PROMOVER, P.RECUPERACAO, P.PROMOVER]))

    def test_com_uma_unica_ancora_no_perfil_ela_sozinha_basta(self):
        self.assertEqual(P.PROMOVER, P.diagnostico_promocao_do_perfil([P.PROMOVER]))
        self.assertIsNone(P.diagnostico_promocao_do_perfil([P.RECUPERACAO]) and None)
        self.assertEqual(P.RECUPERACAO, P.diagnostico_promocao_do_perfil([P.RECUPERACAO]))

    def test_sem_duas_promovendo_o_pior_caso_de_recuperacao_vence(self):
        self.assertEqual(P.RECUPERACAO_SEM_RETOMADA,
                          P.diagnostico_promocao_do_perfil(
                              [None, P.PROMOVER, P.RECUPERACAO, P.RECUPERACAO_SEM_RETOMADA]),
                          "1 PROMOVER nao basta (perfil com 4 ancoras); entre as recuperacoes, o pior caso vence")

    def test_so_recuperacao_simples(self):
        self.assertEqual(P.RECUPERACAO, P.diagnostico_promocao_do_perfil([None, P.RECUPERACAO]))


class InvariantesDoDiagnosticoDePromocao(unittest.TestCase):
    """`[INV]` trancado -- mesmo padrao do `ombro_vira_isolamento` (Fatia 10): numero medido na
    matriz de hoje, nao suposto. Se o catalogo mudar e isto quebrar, e sinal de que o ramo de
    1-ancora em `diagnostico_promocao_do_perfil` passou a ter caso vivo -- investigar, nao so
    atualizar o numero."""

    def test_nenhum_beginner_tem_menos_de_5_ancoras(self):
        from test_motor_v3 import PERFIS
        contagens = []
        for p in PERFIS:
            if p.nivel != "BEGINNER":
                continue
            r = mv.gerar(p)
            contagens.append(sum(1 for _, vagas in r.sessoes for v in vagas if v.exercicio is not None))
        self.assertEqual(240, len(contagens))
        self.assertGreaterEqual(min(contagens), 5,
                                 "se isto cair abaixo de 5, o ramo de 1 ancora em "
                                 "diagnostico_promocao_do_perfil ganhou caso vivo -- nao e so numero novo")
        self.assertEqual(14, sorted(contagens)[len(contagens) // 2], "mediana registrada na Fatia 11")


class DiagnosticoDePromocaoAoAvancado(unittest.TestCase):
    """Fatia 11, `[PROPOSTA]` (numero de "poucas reducoes" calibravel -- a spec da a direcao, nao
    o valor): 1RM congelado sem ganho entre dois mesociclos consecutivos."""

    def test_1rm_subiu_nao_diagnostica(self):
        self.assertIsNone(P.diagnostico_promocao_avancado(100.0, 105.0, reducoes_no_periodo=0))

    def test_sem_1rm_nao_diagnostica(self):
        self.assertIsNone(P.diagnostico_promocao_avancado(None, 100.0, reducoes_no_periodo=0))
        self.assertIsNone(P.diagnostico_promocao_avancado(100.0, None, reducoes_no_periodo=0))

    def test_sem_ganho_e_poucas_reducoes_promove(self):
        self.assertEqual(P.PROMOVER, P.diagnostico_promocao_avancado(100.0, 100.0, reducoes_no_periodo=0))
        self.assertEqual(P.PROMOVER, P.diagnostico_promocao_avancado(100.0, 98.0, reducoes_no_periodo=1))

    def test_sem_ganho_e_muitas_reducoes_e_recuperacao(self):
        self.assertEqual(P.RECUPERACAO, P.diagnostico_promocao_avancado(100.0, 95.0, reducoes_no_periodo=2))

    def test_limite_de_poucas_reducoes_e_configuravel(self):
        self.assertEqual(P.RECUPERACAO,
                          P.diagnostico_promocao_avancado(100.0, 100.0, reducoes_no_periodo=2, limite_poucas_reducoes=1))
        self.assertEqual(P.PROMOVER,
                          P.diagnostico_promocao_avancado(100.0, 100.0, reducoes_no_periodo=2, limite_poucas_reducoes=2))


class PodeSugerirPromocaoDeNovo(unittest.TestCase):
    """Fatia 11, `[REGRA]` aprovado: contador de mesociclo, nao calendario."""

    def test_nunca_recusou_sempre_pode(self):
        self.assertTrue(P.pode_sugerir_promocao_de_novo(mesociclo_atual=1, mesociclo_na_recusa=None))

    def test_mesociclo_seguinte_ao_da_recusa_ainda_nao_pode(self):
        self.assertFalse(P.pode_sugerir_promocao_de_novo(mesociclo_atual=5, mesociclo_na_recusa=4))

    def test_dois_mesociclos_depois_da_recusa_pode(self):
        self.assertTrue(P.pode_sugerir_promocao_de_novo(mesociclo_atual=6, mesociclo_na_recusa=4))

    def test_mesmo_mesociclo_da_recusa_nao_pode(self):
        self.assertFalse(P.pode_sugerir_promocao_de_novo(mesociclo_atual=4, mesociclo_na_recusa=4))


class RetornoAposPausa(unittest.TestCase):
    def test_tres_faixas(self):
        m = Mesociclo(2, 3)
        r = P.retorno_apos_pausa(14, m)
        self.assertEqual((m, False, 1.0), (r.mesociclo, r.recalibra, r.fator_carga), "ate 2 semanas segue")
        r = P.retorno_apos_pausa(15, m)
        self.assertEqual((Mesociclo(2, 1), False, 0.9), (r.mesociclo, r.recalibra, r.fator_carga),
                         "2 a 4 semanas: semana 1 do mesmo mesociclo, carga menor")
        r = P.retorno_apos_pausa(28, m)
        self.assertEqual((Mesociclo(2, 1), False), (r.mesociclo, r.recalibra))
        r = P.retorno_apos_pausa(29, m)
        self.assertEqual((Mesociclo(3, 1), True, 1.0), (r.mesociclo, r.recalibra, r.fator_carga),
                         "mais de 4 semanas: mesociclo novo, recalibrado")

    def test_sem_pausa_nada_muda(self):
        r = P.retorno_apos_pausa(0, Mesociclo(1, 4))
        self.assertEqual((Mesociclo(1, 4), False, 1.0), (r.mesociclo, r.recalibra, r.fator_carga))

    def test_dias_negativos_falham_alto(self):
        with self.assertRaises(ValueError):
            P.retorno_apos_pausa(-1, Mesociclo())

    def test_recalibrar_zera_a_carga_e_a_calibracao_volta_a_ser_obrigatoria(self):
        antes = Estado(carga=100.0, exposicoes=30, pos_salto=True, um_rm=140.0, sem_subir=2)
        depois = P.aplicar_retorno(antes, P.retorno_apos_pausa(40, Mesociclo()), 2.5)
        self.assertEqual(Estado(None, 0, False, 140.0, 0), depois, "carga pendente; o 1RM historico fica")
        with self.assertRaises(ValueError):
            P.decidir(depois, sessao(9, 2), "COMPOSTO", "GAIN_MUSCLE", 2.5, True)

    def test_carga_menor_arredonda_ao_incremento_e_nunca_sobe(self):
        e = Estado(carga=100.0, exposicoes=30, pos_salto=True)
        r = P.aplicar_retorno(e, P.retorno_apos_pausa(20, Mesociclo()), 2.5)
        self.assertEqual((90.0, False), (r.carga, r.pos_salto))
        # halter pequeno: 10% de 8 kg nao tem carga menor num incremento de 2 kg -> fica
        r = P.aplicar_retorno(Estado(carga=8.0, exposicoes=9), P.retorno_apos_pausa(20, Mesociclo()), 2.0)
        self.assertEqual(8.0, r.carga)
        self.assertEqual(e, P.aplicar_retorno(e, P.retorno_apos_pausa(5, Mesociclo()), 2.5), "ate 2 semanas: intacto")

    def test_a_pergunta_do_cadastro_e_o_mesmo_mapa_em_dias(self):
        # "ate 1 mes volta a semana 1 com carga menor; 1 a 6 meses mesociclo novo recalibrado" (S3)
        self.assertEqual(S.PAUSA_SEGUE_DIAS * 2, S.PAUSA_RECALIBRA_DIAS)


class ReferenciaDaProgressao(unittest.TestCase):
    """A referencia que o Kotlin le (`referencia_progressao.txt`) tem de ser a saida do oraculo DE
    HOJE -- a mesma licao da Fatia 9 (referencia embutida envelhece em silencio)."""

    def test_esta_em_dia(self):
        import gerar_referencia_progressao as g
        atual = open(g.CAMINHO, encoding="utf-8").read() if os.path.exists(g.CAMINHO) else ""
        if atual != g.gerar_texto():
            self.fail("referencia_progressao.txt esta VELHA. Rode, de server/src/test/oracle/:  "
                      "python3 gerar_referencia_progressao.py  -- e revise o diff.")

    def test_a_grade_cobre_todo_tipo_de_caso(self):
        """Caminho de falha do gerador: um tipo que sumisse da grade deixaria o Kotlin sem comparar."""
        import gerar_referencia_progressao as g
        tipos = Counter(l.split("|")[0] for l in g.linhas())
        self.assertEqual({"PI", "SF", "PC", "RI", "RM", "DE", "SD", "CS", "DP", "IN", "RE", "AR"}, set(tipos))
        self.assertEqual(6912, tipos["DE"])
        self.assertEqual(2730, tipos["IN"])
        self.assertTrue(all(n > 0 for n in tipos.values()))

    def test_toda_decisao_da_grade_e_uma_das_tres(self):
        import gerar_referencia_progressao as g
        vistas = Counter(l.split("|")[10] for l in g.linhas() if l.startswith("DE|"))
        self.assertEqual({SOBE, MANTEM, DESCE}, set(vistas), "a grade tem de exercitar as tres decisoes")

class RirNuloAplicaSoRegrasPorRepeticao(unittest.TestCase):
    """[REGRA] (D-3, 2026-10-28): RIR nulo aplica SO as regras por repeticao -- sem exigencia de RIR
    no topo, sem salto proporcional, sem tolerancia pos-salto, sem o -4% e sem 1RM. Nada e inferido."""

    def test_topo_de_repeticoes_sobe_um_incremento_sem_salto(self):
        # composto/ganhar massa: 6-9. Nas 3 primeiras exposicoes COM RIR alto o salto seria +25%.
        e, d = P.decidir(Estado(carga=100.0, exposicoes=1), [Serie(9, None)] * 3, "COMPOSTO", "GAIN_MUSCLE", 2.5, False)
        self.assertEqual((SOBE, 102.5), (d, e.carga))

    def test_uma_serie_sem_rir_nao_impede_o_topo_nem_libera_o_salto(self):
        mista = [Serie(9, 6), Serie(9, None), Serie(9, 6)]
        e, d = P.decidir(Estado(carga=100.0, exposicoes=1), mista, "COMPOSTO", "GAIN_MUSCLE", 2.5, False)
        self.assertEqual((SOBE, 102.5), (d, e.carga), "RIR faltando em UMA serie: nao ha pior RIR para saltar")

    def test_abaixo_da_faixa_devolve_porque_a_tolerancia_precisa_do_rir(self):
        pos = Estado(carga=10.0, exposicoes=10, pos_salto=True)
        e, d = P.decidir(pos, [Serie(6, None)] * 3, "ACESSORIO", "GAIN_MUSCLE", 2.0, False)
        self.assertEqual((DESCE, 8.0, False), (d, e.carga, e.pos_salto))
        # contraprova: com RIR no alvo a mesma sessao e tolerada
        e2, d2 = P.decidir(pos, [Serie(6, 1)] * 3, "ACESSORIO", "GAIN_MUSCLE", 2.0, False)
        self.assertEqual((MANTEM, 10.0), (d2, e2.carga))

    def test_a_reducao_de_4_por_cento_nao_age_e_nao_diz_que_foi_anulada(self):
        self.assertEqual((100.0, False), P.reducao_intrasessao(100.0, None, 2, 2.5))

    def test_o_um_rm_so_vem_das_series_com_rir_e_sessao_sem_rir_preserva_o_historico(self):
        e, _ = P.decidir(Estado(carga=100.0, exposicoes=9, um_rm=140.0), [Serie(8, None)] * 3,
                         "COMPOSTO", "GAIN_MUSCLE", 2.5, True)
        self.assertEqual(140.0, e.um_rm)
        e2, _ = P.decidir(Estado(carga=100.0, exposicoes=9), [Serie(8, None), Serie(8, 2), Serie(8, None)],
                          "COMPOSTO", "GAIN_MUSCLE", 2.5, True)
        self.assertAlmostEqual(100.0 * (1 + 10 / 30), e2.um_rm)

    def test_calibracao_sem_rir_nao_mexe_na_carga(self):
        p = P.passo_calibracao(20.0, None, 1, 2, False, 2.5)
        self.assertEqual((20.0, True), (p.carga, p.terminou))


class ModoConservadorDoRetornoDoSessentaMais(unittest.TestCase):
    """Extensao declarada alem da lista literal da spec (2026-10-28): 60+ que volta depois de >28 dias
    entra no mesociclo N+1 em modo conservador explicito, independente do numero do mesociclo."""

    def test_so_o_60_mais_com_pausa_longa_abre_o_mesociclo_conservador(self):
        self.assertTrue(P.retorno_apos_pausa(29, Mesociclo(3, 2), idade=65).mesociclo.conservador)
        self.assertFalse(P.retorno_apos_pausa(29, Mesociclo(3, 2), idade=59).mesociclo.conservador)
        self.assertFalse(P.retorno_apos_pausa(29, Mesociclo(3, 2)).mesociclo.conservador, "sem idade: nao presume")
        for dias in (0, 14, 15, 28):
            self.assertFalse(P.retorno_apos_pausa(dias, Mesociclo(3, 2), idade=65).mesociclo.conservador, dias)

    def test_o_perfil_do_60_mais_fica_conservador_mesmo_no_mesociclo_4(self):
        m = P.retorno_apos_pausa(40, Mesociclo(3, 2), idade=65).mesociclo
        self.assertEqual(4, P.mesociclo_para_perfil(m))
        p = mv.Perfil(nivel="INTERMEDIATE", dias=4, minutos=60, idade=65,
                      mesociclo_atual=P.mesociclo_para_perfil(m),
                      retorno_conservador=P.retorno_conservador_para_perfil(m))
        self.assertTrue(mv.modo_conservador_automatico(p))
        self.assertAlmostEqual(0.75 * 18, mv.teto_sessao(p, "UPPER_LOWER"))
        # contraprova: sem o retorno, o mesmo 60+ no mesociclo 4 NAO seria conservador
        self.assertFalse(mv.modo_conservador_automatico(mv.Perfil(nivel="INTERMEDIATE", idade=65, mesociclo_atual=4)))

    def test_a_flag_vale_so_com_idade_60_mais(self):
        p = mv.Perfil(nivel="INTERMEDIATE", idade=40, mesociclo_atual=4, retorno_conservador=True)
        self.assertFalse(mv.modo_conservador_automatico(p))

    def test_a_flag_dura_o_mesociclo_e_o_seguinte_a_apaga(self):
        m = P.retorno_apos_pausa(40, Mesociclo(3, 2), idade=65).mesociclo
        for _ in range(3):
            m = P.concluir_semana(m)
            self.assertTrue(m.conservador)
        m = P.concluir_semana(m)          # fecha a semana 4 e abre o mesociclo seguinte
        self.assertEqual((5, 1, False), (m.numero, m.semana, m.conservador))

    def test_pausa_media_preserva_a_flag_do_mesociclo_em_curso(self):
        m = Mesociclo(4, 3, conservador=True)
        self.assertTrue(P.retorno_apos_pausa(20, m, idade=65).mesociclo.conservador)

    def test_sem_estado_nao_ha_flag(self):
        self.assertFalse(P.retorno_conservador_para_perfil(None))


if __name__ == "__main__":
    unittest.main()
