-- V74 -- incremento, barra livre e carga invertida: a semantica de carga que o motor de
-- progressao exige e que nao existia em lugar nenhum (fatia do catalogo, 2026-11-05).
--
-- ============================================================================
-- O DEFEITO
-- ============================================================================
-- Progressao.decidir() e Progressao.calibrar() exigem, por exercicio, tres coisas que o schema
-- nao tinha: o incremento de carga do equipamento, se e barra livre, e -- achado nesta fatia --
-- se a carga e INVERTIDA. Nenhuma delas e derivavel do que existia:
--   * `equipment` e texto solto: serve para derivar barra livre, nao o passo de carga.
--   * o unico "incremento" no codigo era o passo fixo de 2,5 kg dos botoes -/+ da UI
--     (WorkoutSessionViewModel.ajustarPeso), igual para todo mundo e sem relacao com o exercicio.
-- Sem as tres, o fio do RIR (rir-plano.md) nao tem o que passar para o motor.
--
-- ============================================================================
-- A DECISAO
-- ============================================================================
-- `incremento` = GRANULARIDADE FISICA (menor carga alcancavel) em kg, curada por EQUIPAMENTO.
-- DOUBLE PRECISION, nao NUMERIC: e o padrao de todo valor em kg aqui (carga_kg e um_rm_kg da
-- V69/V72, weight_kg do perfil) e e o tipo com que Progressao compara, sem conversao no meio.
-- Paliativo declarado: coloca leg press e pec deck no mesmo numero. O destino e calibracao
-- derivada do historico do usuario -- por isso esta coluna NAO ganha CHECK amarrando a
-- `equipment`: ela nasce derivada e o destino dela e deixar de ser.
--   BARBELL/SMITH/TRAP_BAR/LANDMINE/WEIGHT_PLATE/CABLE/MACHINE = 2,50  (anilha ou pino)
--   DUMBBELL = 1,00, carga POR HALTER (o rack brasileiro anda de 1 em 1 ate 10 kg, onde o
--     iniciante vive; 2 kg faz 6->8 = +33% e, medido, NUNCA converge)
--   todo o resto = NULL (sem carga externa em kg, ou resistencia nao-numerica)
-- `MACHINE` = 2,50 e nao 5,00, decidido por medicao (tools/motor_sim/sim_incremento.py):
--   no Hack a 80 kg, 2,5 e 5 chegam ao MESMO lugar (90 kg em 20 sessoes); na Cadeira Abdutora
--   a 20 kg, o 2,5 estabiliza em 22,5 e o 5 fica preso em 20->25->20 para sempre. E o 5
--   desliga a Regra 4 (freio intrassessao) abaixo de 65 kg, porque o freio so age acima de
--   ~12,5x o incremento.
--
-- `barra_livre` = funcao pura de `equipment` HOJE (BARBELL|TRAP_BAR). A coluna existe porque
-- tem futuro proprio, mas enquanto for derivavel o CHECK no fim garante que nunca discorde da
-- regra -- mesmo arranjo que a V65 usou para amarrar is_base a relevancia enquanto as duas
-- conviviam. SMITH fica false: tem barra, mas o trilho e guiado (o enum ja separa, o que nao
-- aconteceria se equipment fosse texto livre). LANDMINE fica false: o movimento e travado num
-- arco pela ponta fixa.
--
-- `carga_invertida` = mais peso significa exercicio MAIS FACIL (maquina assistida: o peso e
-- contrapeso). Entra nesta fatia e nao como debito porque e DANO ATIVO, nao magnitude errada:
-- a Barra Fixa Assistida na Maquina e o caminho do iniciante ate a barra fixa, e `decidir()`
-- faz `carga + incremento` no SOBE -- ou seja, FACILITA o exercicio conforme a pessoa fica mais
-- forte. Hoje e inofensivo porque nada chama decidir() em producao; no dia em que o fio ligar
-- sem a troca de sinal, a assistida regride calada. A troca de sinal em si NAO esta nesta
-- migration nem nesta fatia (mexer em Progressao.kt obriga a atualizar o oraculo Python e
-- regerar referencia_progressao.txt) -- esta registrada como item que BLOQUEIA o fechamento da
-- fatia do RIR, com caso de teste nomeado.
--
-- Os 4 invertidos sao marcados por NOME EXPLICITO, nao por LIKE '%Assistid%': os outros cinco
-- "assistidos" do catalogo sao assistencia de banco, parceiro ou faixa elastica, sem carga
-- selecionavel -- incremento NULL ja os cobre, e um LIKE solto marcaria todos.
--
-- NAO toca dado de usuario: `exercises` e catalogo. Nenhuma coluna existente e alterada.

ALTER TABLE exercises ADD COLUMN incremento      DOUBLE PRECISION;
ALTER TABLE exercises ADD COLUMN barra_livre     BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE exercises ADD COLUMN carga_invertida BOOLEAN NOT NULL DEFAULT false;

COMMENT ON COLUMN exercises.incremento IS
    'Granularidade fisica de carga em kg: a menor carga alcancavel naquele equipamento '
    '(Progressao.paraIncremento arredonda para multiplos dela; decidir() a usa tambem como '
    'passo aditivo no SOBE/DESCE -- os dois usos estao colados, ver debito do passo '
    'proporcional). DUMBBELL e POR HALTER, nao a soma dos dois. NULL = nao se aplica (sem '
    'carga externa em kg) e o chamador NAO invoca o motor de progressao: decidir()/calibrar() '
    'exigem Double nao-nulo e nao existe fallback por repeticao.';

COMMENT ON COLUMN exercises.barra_livre IS
    'Peso livre sobre barra (BARBELL|TRAP_BAR): limita o salto proporcional da calibracao ao '
    'teto de 25% (Progressao.CALIBRACAO_TETO_BARRA_LIVRE), porque pular 25%+ numa barra '
    'olimpica e salto fisico grande. SMITH e false (trilho guiado), LANDMINE e false (arco '
    'travado pela ponta fixa). [INV] enquanto for derivavel, nunca discorda de '
    'equipment IN (BARBELL, TRAP_BAR) -- garantido pelo chk_exercises_barra_livre.';

COMMENT ON COLUMN exercises.carga_invertida IS
    'Mais peso = exercicio mais FACIL (maquina assistida: a carga e contrapeso). O motor tem de '
    'TROCAR O SINAL da decisao: SOBE significa DIMINUIR a carga. false = carga normal. A troca '
    'de sinal no decidir() e item bloqueante da fatia do RIR, nao desta.';

-- ============================================================================
-- 1. incremento, derivado de equipment
-- ============================================================================
UPDATE exercises SET incremento = 2.50
 WHERE equipment IN ('BARBELL', 'SMITH', 'TRAP_BAR', 'LANDMINE', 'WEIGHT_PLATE', 'CABLE', 'MACHINE');

UPDATE exercises SET incremento = 1.00
 WHERE equipment = 'DUMBBELL';

-- ============================================================================
-- 2. barra_livre, derivado de equipment
-- ============================================================================
UPDATE exercises SET barra_livre = true
 WHERE equipment IN ('BARBELL', 'TRAP_BAR');

-- ============================================================================
-- 3. carga_invertida, por nome explicito (4 maquinas assistidas)
-- ============================================================================
UPDATE exercises SET carga_invertida = true WHERE name IN (
    'Barra Fixa Assistida na Máquina',
    'Mergulho Assistido na Máquina',
    'Mergulho Assistido para Tríceps',
    'Subida Unilateral Assistida na Máquina');

-- ============================================================================
-- 4. OS CHECKS -- depois dos dados, nao antes
-- ============================================================================
-- [INV] barra_livre nunca discorda da regra enquanto for derivavel de equipment. O
-- `equipment IS NOT NULL AND` deixa a condicao TOTAL: com equipment NULL o lado direito e
-- false, entao barra_livre tem de ser false -- sem isso o CHECK seria vacuo (NULL nao e false)
-- justamente nas linhas sem taxonomia.
ALTER TABLE exercises ADD CONSTRAINT chk_exercises_barra_livre
    CHECK (barra_livre = (equipment IS NOT NULL AND equipment IN ('BARBELL', 'TRAP_BAR')));

-- Carga invertida sem unidade de carga e sem sentido: nao ha sinal para trocar.
ALTER TABLE exercises ADD CONSTRAINT chk_exercises_carga_invertida
    CHECK (NOT carga_invertida OR incremento IS NOT NULL);

-- Incremento zero ou negativo travaria a progressao: Progressao.paraIncremento() tem
-- require(incremento > 0), e cargaAposSalto() garante "nunca menos que UM incremento acima".
-- Mesmo CHECK que a V69 usa para carga_kg.
ALTER TABLE exercises ADD CONSTRAINT chk_exercises_incremento
    CHECK (incremento IS NULL OR incremento > 0);

-- ============================================================================
-- 5. AUTOCONFERENCIA
-- ============================================================================
-- Nome ou enum que nao casa e no-op silencioso: o UPDATE some e o exercicio chega torto ao
-- motor. Aqui ha quatro nomes explicitos para errar. Estes numeros derrubam a migration em vez
-- de deixar passar (mesmo padrao da V64/V65/V67). Esperados medidos em
-- tools/curadoria_oficial/curadoria_canonica.csv sobre os 893 ativos.
DO $$
DECLARE
    esperado CONSTANT jsonb := '{"catalogo": 893, "inc_250": 322, "inc_100": 139, "inc_null": 432, "barra_livre": 97, "invertida": 4}';
    achado   jsonb;
BEGIN
    SELECT jsonb_build_object(
        'catalogo',    (SELECT count(*) FROM exercises),
        'inc_250',     (SELECT count(*) FROM exercises WHERE incremento = 2.50),
        'inc_100',     (SELECT count(*) FROM exercises WHERE incremento = 1.00),
        'inc_null',    (SELECT count(*) FROM exercises WHERE incremento IS NULL),
        'barra_livre', (SELECT count(*) FROM exercises WHERE barra_livre),
        'invertida',   (SELECT count(*) FROM exercises WHERE carga_invertida)
    ) INTO achado;
    IF achado IS DISTINCT FROM esperado THEN
        RAISE EXCEPTION 'V74: contagem diverge. esperado %, achado %', esperado, achado;
    END IF;
END $$;
-- incremento 2,50: 322 (BARBELL 95, CABLE 99, MACHINE 89, SMITH 17, LANDMINE 11, WEIGHT_PLATE 9, TRAP_BAR 2)
-- incremento 1,00: 139 (DUMBBELL, por halter)
-- incremento NULL: 432 (BODYWEIGHT 289, BAND 53, KETTLEBELL 25, e a cauda longa)
-- barra_livre:      97 (BARBELL 95 + TRAP_BAR 2)
-- carga_invertida:   4 (as quatro maquinas assistidas, por nome)
