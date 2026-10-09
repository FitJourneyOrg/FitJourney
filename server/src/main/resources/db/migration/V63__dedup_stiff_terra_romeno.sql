-- V63 -- dedup FUNCIONAL stiff x terra romeno (debito C4, 2026-10-08).
--
-- DECISAO DO RAFAEL, depois de ver os 4 videos lado a lado: terra romeno e stiff sao o MESMO
-- exercicio para o app, e o STIFF FICA. Somem os dois Terra Romeno:
--   'Levantamento Terra Romeno'            (romanian_deadlift.mp4, de BARRA, mas marcado DUMBBELL
--                                           por engano na taxonomia)  -> 'Stiff com Barra'
--   'Levantamento Terra Romeno com Halteres' (dumbbell_romanian_deadlift.mp4) -> 'Stiff com Halteres'
--
-- MEDIDO antes (2026-10-08, banco de dev): os dois que somem tem 0 linhas em workout_exercises e
-- 0 em session_set_logs; 'Stiff com Halteres' tem 2 treinos de teste (nao e afetado: e o que
-- FICA). O repoint abaixo e defensivo, para o caso de alguem ter usado um deles entre a medicao e
-- a aplicacao. exercise_translations cai por ON DELETE CASCADE (V49).
--
-- Por NOME EXATO, conferido contra o banco (a licao da V32: DELETE por nome pode furar por
-- capitalizacao, entao os quatro nomes foram lidos da saida de um SELECT, nao digitados de cabeca).
--
-- ============================================================================
-- O QUE A UNIAO QUEBRARIA SE FICASSE SO NA PRIMEIRA METADE
-- ============================================================================
-- 'Levantamento Terra Romeno' era o UNICO exercicio de HINGE alcancavel com halteres: o motor
-- (SlotFiller, alvo POSTERIOR) exige movement_pattern em HINGE e LEGS entre os musculos
-- primarios, e 'Stiff com Halteres' esta com padrao NULL e is_base = false. Apagar o Terra
-- Romeno sem tocar nele deixaria o usuario de casa (so halteres) sem posterior no programa.
-- Por isso a taxonomia do 'Stiff com Barra' e copiada para o 'Stiff com Halteres' (equipamento
-- continua DUMBBELL), SO se o padrao dele ainda for NULL.

UPDATE workout_exercises we SET exercise_id = s.id FROM exercises s, exercises d
 WHERE s.name = 'Stiff com Barra' AND d.name = 'Levantamento Terra Romeno' AND we.exercise_id = d.id;
UPDATE session_set_logs sl SET exercise_id = s.id FROM exercises s, exercises d
 WHERE s.name = 'Stiff com Barra' AND d.name = 'Levantamento Terra Romeno' AND sl.exercise_id = d.id;

UPDATE workout_exercises we SET exercise_id = s.id FROM exercises s, exercises d
 WHERE s.name = 'Stiff com Halteres' AND d.name = 'Levantamento Terra Romeno com Halteres' AND we.exercise_id = d.id;
UPDATE session_set_logs sl SET exercise_id = s.id FROM exercises s, exercises d
 WHERE s.name = 'Stiff com Halteres' AND d.name = 'Levantamento Terra Romeno com Halteres' AND sl.exercise_id = d.id;

DELETE FROM exercises
 WHERE name IN ('Levantamento Terra Romeno', 'Levantamento Terra Romeno com Halteres');

UPDATE exercises d SET
    modality          = s.modality,
    movement_pattern  = s.movement_pattern,
    secondary_pattern = s.secondary_pattern,
    is_compound       = s.is_compound,
    primary_muscles   = s.primary_muscles,
    secondary_muscles = s.secondary_muscles,
    unilateral        = s.unilateral,
    prescription_type = s.prescription_type,
    level             = s.level,
    contraindications = s.contraindications,
    is_base           = s.is_base
  FROM exercises s
 WHERE s.name = 'Stiff com Barra'
   AND d.name = 'Stiff com Halteres'
   AND d.movement_pattern IS NULL;
