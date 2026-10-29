"""Números da especificação do motor v3 — um lugar só.

Fonte: docs/propostas/motor-v3-ESPECIFICACAO.md (cópia do artifact f664e0d0). Mudou lá, muda aqui,
e os testes de test_motor_v3.py dizem o que quebrou. Só constantes: nenhuma lógica mora neste
arquivo, para que o modelo à mão e o simulador possam compartilhá-lo sem que o teste diferencial
vire tautologia.
"""

ALVOS = ["CHEST", "BACK", "SHOULDERS", "BICEPS", "TRICEPS", "FOREARMS",
         "QUADS", "POSTERIOR", "CALVES", "GLUTES"]  # = TargetMuscle (VolumeModel.kt)
NIVEIS = ["BEGINNER", "INTERMEDIATE", "ADVANCED"]

# "Volume semanal por músculo": iniciante, intermediário, avançado, teto. Braço a 12.
# A coluna do iniciante também é o PISO.
VOLUME = {
    "CHEST": (8, 14, 18, 22), "BACK": (10, 16, 20, 25), "SHOULDERS": (6, 12, 16, 22),
    "BICEPS": (6, 12, 15, 18), "TRICEPS": (7, 12, 16, 20), "FOREARMS": (3, 6, 9, 12),
    "QUADS": (6, 12, 16, 20), "POSTERIOR": (6, 10, 14, 18), "CALVES": (5, 9, 12, 16),
    # GLUTEO 4/8/12 -> 8/10/12 (2026-10-27). Mesma construcao do item 20 nos bracos: alvo =
    # indireto recebido + direto desejado. Medido, o gluteo recebe indireto 6/7/8 (quase plano,
    # porque depende de quanto composto de quadriceps/posterior entra, nao do nivel), entao
    # 8/10/12 entrega direto 2/3/4 e o ADVANCED fica INTOCADO em 12.
    #
    # NATUREZA DO MOTIVO: e DECISAO DE PRODUTO, nao inferencia da literatura. A isencao do
    # iniciante no hibrido (no BEGINNER o indireto conta para o MEV) continua valendo como REGRA
    # GERAL, sustentada por Iversen et al. 2021 e Gentil -- essas duas fontes embasam a regra
    # geral e NAO esta excecao. O gluteo e excecao DECLARADA neste app porque e prioridade de
    # mercado e zero exercicio direto le como programa incompleto.
    #
    # Descartado: 8/9/10 (o minimo aritmetico para cruzar o piso em cada nivel) -- derrubava o
    # ADVANCED de 12 para 10, contra a premissa de volume crescente com o nivel.
    "GLUTES": (8, 10, 12, 16),
}

# "Como as outras variáveis modulam". None = saúde geral usa o piso (coluna do iniciante).
FATOR_OBJETIVO_ALVO = {"GAIN_MUSCLE": 1.0, "LOSE_FAT": 1.0, "MAINTAIN": 0.6, "GENERAL_HEALTH": None}

# Volume de MANUTENCAO (MV): o minimo para NAO PERDER, que e menor que o MEV -- o minimo para
# CRESCER (a coluna do iniciante, VOLUME[m][0]). Sao grandezas diferentes na literatura (a RP
# publica as duas: biceps MV ~4 contra MEV ~8), e a nossa tabela so tinha uma. Sem esta
# distincao, um perfil MAINTAIN/BEGINNER era medido contra o MEV e reprovava nos 10 musculos
# por design -- 660 pares nos 720 perfis (ver docs/regras/01-motor-de-geracao.md, etapa [7]).
#
# [PROPOSTA] 2026-10-27: o FATOR e derivado, nao tabela ratificada. Usa o proprio fator de
# "manter" que ja passou por revisao, aplicado ao piso. A derivacao fecha com a decisao ja
# tomada: para o iniciante, o alvo de MAINTAIN (0,6 x coluna do iniciante) E exatamente o MV,
# entao "manter" passa a significar "treinar no volume de manutencao". Trocar por uma tabela
# revisada e trocar esta constante.
FATOR_MANUTENCAO = FATOR_OBJETIVO_ALVO["MAINTAIN"]
FATOR_OBJETIVO_TETO = {"GAIN_MUSCLE": 1.0, "LOSE_FAT": 1.0, "MAINTAIN": 0.6, "GENERAL_HEALTH": 1.0}
FATOR_IDADE_60 = 0.75  # 60+ iniciante ou no 1º mesociclo; só no teto da sessão

# "Volume indireto": cada serie de COMPOSTO do doador credita o sinergista. Ombro nao recebe
# desconto (o alvo "ombro" e o deltoide inteiro; descontar subserviria lateral/posterior).
#
# [INV] 2026-10-27: NENHUM coeficiente aqui sem fonte registrada. Tres pares foram REMOVIDOS
# nesta rodada por nao ter nenhuma -- `costas -> antebraco` 0,5, `posterior -> antebraco` 0,5 e
# `gluteo -> posterior` 0,2. Eram eles que causavam o double-count do antebraco (recebia 6/7/12
# contra alvo 3/6/9, ficando com ZERO direto em todo nivel) e o do posterior. Ver D-1/D-2 em
# docs/propostas/volume-efetivo-PROPOSTA.md.
#
# Fonte de cada par sobrevivente:
#   peito/ombro -> triceps ... Pelland et al. 2025 (Sports Medicine, 67 estudos): a quantificacao
#                              FRACIONADA (0,5) foi a de evidencia mais forte entre 0 / 0,5 / 1.
#                              Confianca ALTA.
#   costas -> biceps ......... Pelland et al. 2025, idem; Mannarino et al. 2021 mediu rosca
#                              +11,06% contra remada +5,16% na espessura dos flexores do cotovelo,
#                              razao coerente com 0,5. Confianca ALTA.
#   quadriceps -> gluteo ..... Kubo et al. 2019: gluteo +6,7% so com agachamento. Confianca ALTA.
#   posterior -> gluteo ...... EXTENSAO POR ANALOGIA do par irmao acima -- a dobradica tambem e
#                              extensao de quadril (Kubo 2019). Confianca MEDIA. Zera-lo
#                              contradiria a biomecanica que sustenta o par de agachamento.
INDIRETO = {
    "CHEST": {"TRICEPS": 0.5},
    "SHOULDERS": {"TRICEPS": 0.5},
    "BACK": {"BICEPS": 0.5},
    "POSTERIOR": {"GLUTES": 0.5},
    "QUADS": {"GLUTES": 0.5},
}

# Músculos sem composto próprio no catálogo: toda vaga deles é isolamento.
SO_ISOLAMENTO = {"BICEPS", "TRICEPS", "FOREARMS", "CALVES"}

# "Composição da sessão" (regra da 5ª rodada).
PISO_SERIES = {"COMPOSTO": 3, "ACESSORIO": 3, "ISOLAMENTO": 2}
MAX_SERIES_EXERCICIO = 4
MAX_EXERCICIOS_MUSCULO = 3
MAX_SERIES_MUSCULO_SESSAO = 11  # limite fisiológico, aprovado pelo revisor

# "Ombro": desenvolvimento com no máximo (10 − composto de peito do dia); abaixo de 3, vira isolamento.
OMBRO_LIMITE = 10
OMBRO_MINIMO_COMPOSTO = 3

# "Os tetos": min(teto por tempo, teto por nível × fator de objetivo × fator de idade).
TETO_NIVEL = {"BEGINNER": 16, "INTERMEDIATE": 18, "ADVANCED": 18}
OVERHEAD_MIN = {"FULL_BODY": 16, "DEMAIS": 11}  # a rampa substitui o aquecimento fixo
MINUTOS_POR_SERIE = 3

# "Como alocamos".
PESO_PRIORIDADE = {"CHEST": 1.0, "BACK": 1.1, "SHOULDERS": 0.9, "QUADS": 1.0, "POSTERIOR": 0.9,
                   "GLUTES": 0.7, "BICEPS": 0.5, "TRICEPS": 0.55, "FOREARMS": 0.2, "CALVES": 0.4}

# "Extensao de cotovelo para o triceps" (Fatia 8, 2026-10-27) -- `[REGRA]` SEPARADA do volume.
#
# Mesma fonte do piso direto, outra decisao: Brandao et al. 2020 mediu a cabeca longa do triceps
# crescendo +2,1% so com supino contra +17,5% com EXTENSAO. Dai a parte que o MOTOR afirma: a
# vaga direta de triceps tem de ser extensao de cotovelo. Isso e fato mecanico (o que o movimento
# faz), nao curadoria (qual exercicio e o melhor) -- expresso pela coluna `familia`, que ja existe
# e ja separa isto; nenhuma coluna nova.
#
# A outra metade da fonte -- "de preferencia ACIMA DA CABECA", onde a cabeca longa fica alongada
# porque cruza o ombro -- NAO esta no motor, por decisao do Rafael (2026-10-27, opcao 4). Tentada
# como filtro, ela passava por cima da `[REGRA]` da relevancia 1 ("a primeira vaga do musculo sai
# da relevancia 1"): rodava ANTES dela e esvaziava o pool, concentrando 471/471 vagas de
# isolamento em 2 exercicios de TRICEPS_FRANCES e tornando TRICEPS_PULLEY (relevancia 1,
# prioridade 1 -- a escolha primaria da curadoria) inalcancavel em TODOS os 720 perfis, com 0
# disparos de fallback. O motor decide QUANTO, a curadoria decide QUAL: se a ciencia manda acima
# da cabeca primeiro, o errado e a relevancia do catalogo, nao o motor. Virou `[PROPOSTA]` de
# curadoria em docs/fontes/debitos.md, para revisao de quem tem CREF.
#
# Taxonomia medida das familias de triceps -- e a evidencia daquela proposta:
#   TRICEPS_FRANCES .. extensao de cotovelo ACIMA DA CABECA (umero vertical). 4 exercicios, com
#                      opcao em CASA (faixa elastica) e em AMBOS (halter sentado).
#   TRICEPS_TESTA .... extensao de cotovelo, umero ~perpendicular ao tronco. Alonga a cabeca
#                      longa parcialmente; NAO conta como acima da cabeca (decisao do Rafael,
#                      2026-10-27).
#   TRICEPS_PULLEY ... extensao de cotovelo, ombro neutro (cabeca longa encurtada).
#   TRICEPS_COICE .... extensao de cotovelo, nao acima da cabeca.
#   MERGULHO ......... NAO e extensao de cotovelo: e empurrar composto. Fora da vaga direta.
TRICEPS_SEM_EXTENSAO = frozenset({"MERGULHO"})

# "Piso de trabalho direto" (Fatia 8, D-1 hibrido, 2026-10-27).
#
# Garante N series DIRETAS por semana nos musculos que recebem credito indireto SANCIONADO, para
# INTERMEDIATE e ADVANCED. Nao e para completar volume -- esses casos ja estao em ATENDIDO, com o
# alvo coberto pelo indireto. E para garantir ESTIMULO DIRETO onde o composto nao alcanca:
# Brandao et al. 2020 (J Strength Cond Res 34(5)) mediu a cabeca longa do triceps crescendo
# +2,1% so com supino contra +17,5% com extensao -- ela cruza o ombro, e no supino a acao e
# conflitante. Medido: 134 pares (perfil, musculo) INTER/ADV saem ATENDIDO com ZERO trabalho
# direto (triceps 88, gluteo 46).
#
# INICIANTE SEGUE ISENTO, pela regra geral do hibrido: para nao treinado o composto basta
# (Iversen et al. 2021, Gentil). A isencao e a regra, nao a excecao.
#
# [REGRA] O piso NUNCA passa por cima da capacidade -- e financiado DENTRO do orcamento, em ordem
# decrescente de peso, e musculo que nao couber sai como MANUTENCAO ou NAO_TREINADO. O motivo
# esta em docs/regras/01-motor-de-geracao.md: o `sobe_ao_piso_do_isolamento` agia por fora do
# orcamento e foi o que produziu `entregue > capacidade` em 130/720.
PISO_DIRETO = 2
PISO_DIRETO_MUSCULOS = frozenset({"TRICEPS", "BICEPS", "GLUTES"})  # os que recebem indireto com fonte
PISO_DIRETO_NIVEIS = frozenset({"INTERMEDIATE", "ADVANCED"})

# Fronteira do conjunto protegido (Fatia 8, decisao "fronteira A", 2026-10-24, medida contra a
# "fronteira B" = +SHOULDERS: B zera POSTERIOR em 12/720 perfis, contra a decisao (d) da mesma
# fatia -- descartada). Estes 4 nunca sao rebaixados nem sacrificados -- so o corte proporcional
# os alcanca, quando sacrificar todo o resto (ORDEM_SACRIFICIO) nao basta.
PROTEGIDOS_ALOCACAO = frozenset({"CHEST", "BACK", "QUADS", "POSTERIOR"})

# [INV] fonte unica (2026-10-24): ORDEM_SACRIFICIO e SEMPRE derivada do PESO_PRIORIDADE (peso
# crescente -- quem pesa menos sacrifica primeiro), nunca mantida a mao -- as duas nunca podem
# discordar, por construcao. Desempate: ALVOS.index ascendente, mesmo criterio do resto do
# codigo (ver `prioridade` em motor_v3.py::alocar() e Allocation.kt). Ate hoje era lista fixa
# e discordava do peso em GLUTES (medio demais, sacrificado cedo demais) e BICEPS (leve demais,
# sacrificado tarde demais) -- achado da bateria de coerencia da Fatia 8. Medido nos 720 perfis
# (script descartavel, nao ficou no repo): a correcao NAO muda nenhum dos 156 eventos de
# zeramento da Fatia 8 -- nesses perfis o laco de sacrificio nunca e alcancado (a soma dos
# pisos de base, antes de qualquer sacrificio, ja cabe na capacidade). A [INV] vale pela
# consistencia interna, nao por ter corrigido esse sintoma -- ver docs/historico/motor-v3-fatia-8.md.
ORDEM_SACRIFICIO = sorted(
    (m for m in ALVOS if m not in PROTEGIDOS_ALOCACAO),
    key=lambda m: (PESO_PRIORIDADE[m], ALVOS.index(m)),
)

# "A divisão sai do número de dias" — só as recomendadas (★).
UPPER = ("CHEST", "BACK", "SHOULDERS", "BICEPS", "TRICEPS", "FOREARMS")
LOWER = ("QUADS", "POSTERIOR", "CALVES", "GLUTES")
PUSH = ("CHEST", "SHOULDERS", "TRICEPS")
PULL = ("BACK", "BICEPS", "FOREARMS")
FULL = tuple(ALVOS)
DIVISOES = {
    2: ("FULL_BODY", [("Full Body A", FULL), ("Full Body B", FULL)]),
    3: ("FULL_BODY", [("Full Body A", FULL), ("Full Body B", FULL), ("Full Body C", FULL)]),
    4: ("UPPER_LOWER", [("Upper A", UPPER), ("Lower A", LOWER), ("Upper B", UPPER), ("Lower B", LOWER)]),
    5: ("UL_PPL", [("Upper", UPPER), ("Lower", LOWER), ("Push", PUSH), ("Pull", PULL), ("Legs", LOWER)]),
    6: ("PPL", [("Push A", PUSH), ("Pull A", PULL), ("Legs A", LOWER),
                ("Push B", PUSH), ("Pull B", PULL), ("Legs B", LOWER)]),
}

# Espelha EquipmentEnvironmentMap.HOME_EQUIPMENT do servidor. HAND_GRIP entrou em 21/10: o
# Hand Grip e curado como ambiente CASA e sem ele nao era alcancavel por perfil nenhum.
EQUIPAMENTO_CASA = {"DUMBBELL", "BODYWEIGHT", "BAND", "HAND_GRIP"}

# "Cobertura por faixa de tempo" e "O princípio central" — o que os testes conferem.
#
# ESTAVEL (2026-10-27): os dois numeros abaixo mudaram nesta rodada e sao definitivos -- saem da
# propria VOLUME e da INDIRETO, nao de medicao de programa. ALVO_TOTAL e a soma das colunas da
# VOLUME (subiu com o gluteo 4/8/12 -> 8/10/12); DEMANDA_DIRETA e essa soma menos o indireto
# creditado (subiu porque tres pares sem fonte sairam da INDIRETO, devolvendo ao antebraco e ao
# posterior o alvo que o credito fantasma consumia).
#   ALVO_TOTAL ..... 61/111/148 -> 65/113/148
#   DEMANDA_DIRETA . 44/86/113  -> 49/94/122
# Ver o [REGRA] da convencao total em docs/propostas/motor-v3-ESPECIFICACAO.md.
REFERENCIA_ALVO_TOTAL = {"BEGINNER": 65, "INTERMEDIATE": 113, "ADVANCED": 148}
REFERENCIA_DEMANDA_DIRETA = {"BEGINNER": 49.0, "INTERMEDIATE": 94.0, "ADVANCED": 122.0}
# GLUTES saiu desta tabela em 2026-10-27: deixou de ser DOADOR (o par `gluteo -> posterior` 0,2
# foi removido por falta de fonte), e o modelo a mao so computa composto de doador -- e o composto
# do doador que credita alguem. O gluteo passou a ENTREGAR composto de verdade (3 no
# intermediario, 4 no avancado, com o alvo 8/10/12), mas esse composto nao credita ninguem.
REFERENCIA_COMPOSTOS_SEMANA = {
    "INTERMEDIATE": {"CHEST": 8, "BACK": 8, "SHOULDERS": 8, "QUADS": 8, "POSTERIOR": 6},
    "ADVANCED": {"CHEST": 14, "BACK": 16, "SHOULDERS": 6, "QUADS": 8, "POSTERIOR": 8},
}
# PROVISORIO -- MEDIDO EM 2026-10-27, ANTES DO PISO DIRETO. Esta tabela e funcao da
# DEMANDA_DIRETA, entao ela se move de novo quando o piso direto do hibrido (D-1) entrar: o piso
# muda quanto trabalho direto o motor pede. Os valores de antes desta rodada (44/66/103... na
# linha do iniciante) correspondiam a demanda 44/86/113 e ficam no historico da Fatia 8.
REFERENCIA_COBERTURA = {  # (nível, minutos) -> % para 2..6 dias
    ("BEGINNER", 45): (39, 59, 93, 116, 139),
    ("BEGINNER", 60): (60, 90, 131, 163, 196),
    ("BEGINNER", 75): (65, 98, 131, 163, 196),
    ("INTERMEDIATE", 45): (21, 31, 48, 60, 72),
    ("INTERMEDIATE", 60): (31, 47, 70, 87, 104),
    ("INTERMEDIATE", 75): (38, 57, 77, 96, 115),
    ("ADVANCED", 45): (16, 24, 37, 46, 56),
    ("ADVANCED", 60): (24, 36, 54, 67, 80),
    ("ADVANCED", 75): (30, 44, 59, 74, 89),
}

# ============================================================================= Fatia 10
# Foco, mesociclo e progressao. Fonte: motor-v3-ESPECIFICACAO.md, "Prescricao de cada serie",
# "Carga e progressao", "Progressao e mesociclo" e "Priorizacao: agora e uma fase".

# --- Foco (so o BONUS DE ALVO; a fase de dois mesociclos, o piso nos demais grupos e a protecao na
# alocacao sao da Fatia 11). Registrado em docs/trabalho/motor-v3-desenho-e-fatias.md.
#
# `ProfileDto.focusAreas` e `MuscleGroup` (grosso); o alvo e `TargetMuscle` (fino). Mapa copiado do
# motor antigo (`VolumeModel.targetsForFocus`).
FOCO_PARA_ALVOS = {
    "CHEST": ("CHEST",), "BACK": ("BACK",), "SHOULDERS": ("SHOULDERS",), "BICEPS": ("BICEPS",),
    "TRICEPS": ("TRICEPS",), "FOREARMS": ("FOREARMS",), "GLUTES": ("GLUTES",),
    "LEGS": ("QUADS", "POSTERIOR", "CALVES"),
}
FOCO_BONUS_PEQUENO = 3  # spec, tabela "Priorizacao": +3 para pequeno
FOCO_BONUS_GRANDE = 6   # +6 para grande
FOCO_MAX_GRUPOS = 2
# Elegibilidade: mesma do motor antigo (`DeterministicWorkoutGenerator.effectiveFocus`) e da spec
# ("so intermediario e avancado"). Saude geral usa o piso por definicao -- bonus contradiria isso.
FOCO_NIVEIS = frozenset({"INTERMEDIATE", "ADVANCED"})
FOCO_OBJETIVOS_SEM_FOCO = frozenset({"GENERAL_HEALTH"})
# [REGRA] 2026-10-28: "grande" e DERIVADO do PESO_PRIORIDADE (fonte unica, o mesmo principio da
# ORDEM_SACRIFICIO) por uma fronteira DECLARADA: peso >= FOCO_FRONTEIRA_GRANDE. A fronteira 0,6 fica
# entre TRICEPS (0,55) e GLUTES (0,7), a 0,05 e 0,10 deles: ninguem esta na borda. Nao e o maior vao
# da tabela (0,7 -> 0,9 e 0,2 de largura), mas e o que deixa o gluteo -- que o Rafael decidiu ser
# grande -- do lado de cima, e os bracos do lado de baixo.
# Resultado: CHEST, BACK, SHOULDERS, QUADS, POSTERIOR, GLUTES grandes; BICEPS, TRICEPS, CALVES,
# FOREARMS pequenos. A spec so da dois exemplos (antebraco pequeno, costas grande). NAO e o
# conjunto PROTEGIDOS_ALOCACAO: protecao na alocacao (fronteira A, Fatia 8) e outra pergunta.
FOCO_FRONTEIRA_GRANDE = 0.6
GRANDES = frozenset(m for m in ALVOS if PESO_PRIORIDADE[m] >= FOCO_FRONTEIRA_GRANDE)

# [REGRA] 2026-10-28 (extensao declarada ALEM da lista literal da spec): 60+ que volta depois de mais
# de PAUSA_RECALIBRA_DIAS entra no mesociclo N+1 em MODO CONSERVADOR explicito, independente do
# numero do mesociclo (a regra da spec so olha o 1o mesociclo). Motivo: quem volta depois de um mes
# fora e quem mais precisa do lado seguro. Mesma idade de corte do fator 0,75 (FATOR_IDADE_60).
IDADE_RETORNO_CONSERVADOR = 60

# --- Mesociclo: fixo em 4 semanas; as series NAO sobem dentro do ciclo.
MESOCICLO_SEMANAS = 4
SEMANA_DELOAD = 4            # intermediario e avancado; o iniciante deloada por estagnacao
DELOAD_FATOR_SERIES = 0.5    # "metade do volume, carga mantida"

# --- Prescricao por papel (spec, "Prescricao de cada serie"): (reps min, reps max, descanso s, RIR).
# COMPOSTO = "composto pesado", ACESSORIO = "composto acessorio".
PRESCRICAO = {
    "GAIN_MUSCLE": {"COMPOSTO": (6, 9, 120, 2), "ACESSORIO": (8, 12, 90, 1), "ISOLAMENTO": (12, 16, 60, 1)},
    "LOSE_FAT": {"COMPOSTO": (6, 9, 120, 2), "ACESSORIO": (8, 12, 90, 1), "ISOLAMENTO": (12, 16, 60, 1)},
    "MAINTAIN": {"COMPOSTO": (6, 9, 120, 2), "ACESSORIO": (8, 12, 90, 2), "ISOLAMENTO": (12, 16, 60, 2)},
    "GENERAL_HEALTH": {"COMPOSTO": (8, 12, 120, 3), "ACESSORIO": (10, 14, 90, 3), "ISOLAMENTO": (12, 18, 75, 3)},
}

# --- Progressao dupla (spec, "Carga e progressao").
# Regra 4: "3 a 5% por ponto de RIR abaixo do alvo" no corpo (l.608), "4%" no checklist. DECISAO do
# Rafael (2026-10-28): 4% FIXO, dentro da faixa 3-5%, contradicao interna fechada -- nao reabrir.
REDUCAO_POR_PONTO_RIR = 0.04
# Regra 3 (tolerancia pos-salto) e a de "abaixo da faixa devolve" nao tem numero: sao estado.
EXPOSICOES_COM_SALTO_MAIOR = 3   # "as tres primeiras exposicoes" aceitam saltos maiores
# --- Serie de calibracao (spec l.80 e l.600): RIR informado >= 4 sobe na hora; ate 3 series.
CALIBRACAO_RIR_MINIMO = 4
CALIBRACAO_MAX_SERIES = 3
# (excesso de RIR sobre o alvo: de, ate, salto). Spec l.80: 1-2 -> +10%, 3-4 -> +25%, 5+ -> +50%.
CALIBRACAO_SALTOS = ((1, 2, 0.10), (3, 4, 0.25), (5, 10**6, 0.50))
CALIBRACAO_TETO_BARRA_LIVRE = 0.25  # "teto de +25% em barra livre"

# --- Retorno apos pausa (spec, tabela): <=2 semanas segue; 2-4 semana 1 com carga menor; >4
# novo mesociclo recalibrado. Em DIAS (o relogio e do servidor): 14 e 28.
PAUSA_SEGUE_DIAS = 14
PAUSA_RECALIBRA_DIAS = 28
# [PROPOSTA] 2026-10-28: a spec diz "carga um pouco menor" sem numero. 10% e heuristica SEM FONTE;
# trocar e trocar esta constante. Pendente de decisao do Rafael.
REDUCAO_RETORNO = 0.10

# --- Iniciante: ancora fixa; deload so por estagnacao (spec, item 24).
# "Retomada e a carga subir em pelo menos uma ancora nas tres sessoes seguintes ao deload" (spec,
# "Reavaliacao do nivel"). O mesmo 3 e usado como janela de estagnacao ANTES do deload --
# [PROPOSTA]: a spec nao da numero para "estagnar".
RETOMADA_SESSOES = 3
ESTAGNACAO_SESSOES = 3
