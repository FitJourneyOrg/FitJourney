-- V77 -- progressao por nivel (fatia "progressao-por-nivel", 2026-11-06).
--
-- BAND/HAND_GRIP (incremento IS NULL, equipment nao e BODYWEIGHT) nao tem carga numerica nem faz
-- sentido numa faixa de reps que sobe/desce -- quem muda e a resistencia do elastico/aparelho, em
-- passos discretos. Decisao do Rafael: nivel RELATIVO, sem modelar o kit do usuario -- nivel_atual
-- nao tem significado absoluto (nao e "elastico vermelho"), so conta trocas de resistencia desde
-- que a pessoa comecou o exercicio. Reaproveita a mesma linha/PK de exercise_progression -- e a
-- mesma identidade, carga/reps/nivel e so o eixo que muda (nunca mais de um ao mesmo tempo pras
-- colunas de FAIXA do proprio eixo; ver docs/regras/01-motor-de-geracao.md pela excecao do
-- um_rm_kg, que NAO se aplica a este eixo).
--
-- Nenhum dado existente e apagado ou reescrito: uma coluna NOVA, nullable.

ALTER TABLE exercise_progression
    ADD COLUMN nivel_atual SMALLINT,
    ADD CONSTRAINT chk_exercise_progression_nivel CHECK (nivel_atual IS NULL OR nivel_atual > 0);

COMMENT ON COLUMN exercise_progression.nivel_atual IS
    'Nivel ATUAL de resistencia (ProgressaoPorNivel) -- RELATIVO, sem significado absoluto. NULL '
    'quando o exercicio progride por carga ou por faixa de reps.';

-- progression_event.tipo (V69, emendado em V76 com SOBE_REP/MANTEM_REP/DESCE_REP) ganha o
-- terceiro eixo. Sufixo _NIVEL distingue na leitura (Evento.Nivel) sem precisar de coluna de eixo
-- nova -- mesmo motivo do _REP.
ALTER TABLE progression_event DROP CONSTRAINT progression_event_tipo_check;
ALTER TABLE progression_event ADD CONSTRAINT progression_event_tipo_check CHECK (
    tipo IN (
        'SOBE', 'MANTEM', 'DESCE', 'DELOAD',
        'SOBE_REP', 'MANTEM_REP', 'DESCE_REP',
        'SOBE_NIVEL', 'MANTEM_NIVEL', 'DESCE_NIVEL'
    )
);
