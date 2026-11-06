-- V76 -- progressao por repeticao (fatia "progressao-por-repeticao", 2026-11-06).
--
-- Exercicios BODYWEIGHT puro (incremento IS NULL, equipment = 'BODYWEIGHT') nao tem carga pra
-- progredir -- o sinal de esforco vira a propria FAIXA de repeticoes (ProgressaoPorRepeticao,
-- docs/trabalho/progressao-por-repeticao-plano.md). Reaproveita a mesma linha/PK de
-- exercise_progression (user_id, exercise_id): e a mesma identidade -- progressao acompanha a
-- PESSOA, carga ou reps e so o eixo que muda. Um exercicio so usa um dos dois conjuntos de
-- colunas: carga_kg/um_rm_kg/pos_salto para o motor de carga, reps_min/reps_max para este -- nunca
-- os dois ao mesmo tempo, porque `incremento` do catalogo e fixo por exercicio.
--
-- Nenhum dado existente e apagado ou reescrito: duas colunas NOVAS, ambas NULLABLE.

ALTER TABLE exercise_progression
    ADD COLUMN reps_min SMALLINT,
    ADD COLUMN reps_max SMALLINT,
    ADD CONSTRAINT chk_exercise_progression_reps CHECK (
        (reps_min IS NULL AND reps_max IS NULL) OR
        (reps_min IS NOT NULL AND reps_max IS NOT NULL AND reps_min > 0 AND reps_max >= reps_min)
    );

COMMENT ON COLUMN exercise_progression.reps_min IS
    'Piso ATUAL da faixa de reps (ProgressaoPorRepeticao) -- NULL quando o exercicio progride por '
    'carga. Nao confundir com PrescricaoV3.Faixa.repsMin, que e fixa por papel/objetivo.';
COMMENT ON COLUMN exercise_progression.reps_max IS
    'Topo ATUAL da faixa de reps -- mesma ressalva de reps_min. Teto absoluto 20 '
    '(ProgressaoPorRepeticao.TETO_REPETICOES), nao garantido por CHECK (é constante de código, não '
    'de schema -- mudar o teto não deve exigir migration).';

-- `progression_event.tipo` (V69) só aceitava os três tipos do motor de carga + DELOAD. O motor por
-- repetição usa a MESMA tabela (decisão 2026-11-06, Rafael: "estagnação é ausência de progressão de
-- estímulo, independente da variável" -- AncoraDoIniciante.semSubirNoFim trata os dois eixos como
-- equivalentes pro contador de deload). Sufixo `_REP` distingue na leitura (Evento.Repeticao vs
-- Evento.Carga) sem precisar de coluna de eixo nova -- os nomes dos dois enums (SOBE/MANTEM/DESCE)
-- colidiriam sem ele.
ALTER TABLE progression_event DROP CONSTRAINT progression_event_tipo_check;
ALTER TABLE progression_event ADD CONSTRAINT progression_event_tipo_check CHECK (
    tipo IN ('SOBE', 'MANTEM', 'DESCE', 'DELOAD', 'SOBE_REP', 'MANTEM_REP', 'DESCE_REP')
);
