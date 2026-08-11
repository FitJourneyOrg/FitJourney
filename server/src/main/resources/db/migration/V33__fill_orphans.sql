-- V33 (CANDIDATA) — fecha o gap dos 33 exercícios sem taxonomia.
-- CAUSA: a V14 renomeou 49 exercícios POR ID, então os nomes do catalogo.json não batiam
-- mais com o banco; as V29/V31 (que casavam por nome) nunca alcançaram estas linhas.
-- Fonte: orfaos_auditados.csv (auditoria 4) — inferência + correções do avaliador.
--
-- NÃO são duplicatas: a checagem no banco (dup_geral.sql) não achou nenhuma duplicata
-- normalizada; e os supostos originais apontados na avaliação (ex. "Rosca Scott com Alavanca")
-- não existem no banco — eram nomes pré-V14 da lista do JSON.

-- 1. Taxonomia dos 33
UPDATE exercises SET primary_muscles=ARRAY['BACK']::TEXT[], equipment='MACHINE', is_compound=true, unilateral=false, prescription_type='REPS', level='BEGINNER' WHERE name = 'Remada na Máquina';
UPDATE exercises SET primary_muscles=ARRAY['BACK']::TEXT[], equipment='MACHINE', is_compound=true, unilateral=false, prescription_type='REPS', level='BEGINNER' WHERE name = 'remada T invertida na máquina';
UPDATE exercises SET primary_muscles=ARRAY['BICEPS']::TEXT[], equipment='MACHINE', is_compound=false, unilateral=false, prescription_type='REPS', level='BEGINNER' WHERE name = 'Rosca Scott na Máquina';
UPDATE exercises SET primary_muscles=ARRAY['BICEPS']::TEXT[], equipment='MACHINE', is_compound=false, unilateral=false, prescription_type='REPS', level='BEGINNER' WHERE name = 'Rosca de bíceps na Máquina';
UPDATE exercises SET primary_muscles=ARRAY['TRICEPS']::TEXT[], equipment='BODYWEIGHT', is_compound=true, unilateral=false, prescription_type='REPS', level='INTERMEDIATE' WHERE name = 'Dips na cadeira';
UPDATE exercises SET primary_muscles=ARRAY['TRICEPS']::TEXT[], equipment='BODYWEIGHT', is_compound=true, unilateral=false, prescription_type='REPS', level='INTERMEDIATE' WHERE name = 'Flexão diamante';
UPDATE exercises SET primary_muscles=ARRAY['LEGS']::TEXT[], equipment='MACHINE', is_compound=false, unilateral=false, prescription_type='REPS', level='BEGINNER' WHERE name = 'Levantamento de panturrilha na Máquina';
UPDATE exercises SET primary_muscles=ARRAY['CHEST']::TEXT[], equipment='CABLE', is_compound=false, unilateral=false, prescription_type='REPS', level='INTERMEDIATE' WHERE name = 'Crossover na Máquina';
UPDATE exercises SET primary_muscles=ARRAY['CHEST']::TEXT[], equipment='CABLE', is_compound=false, unilateral=false, prescription_type='REPS', level='INTERMEDIATE' WHERE name = 'Crucifixo Inclinado no Cross';
UPDATE exercises SET primary_muscles=ARRAY['CHEST']::TEXT[], equipment='MACHINE', is_compound=true, unilateral=false, prescription_type='REPS', level='BEGINNER' WHERE name = 'Supino Declinado na Máquina';
UPDATE exercises SET primary_muscles=ARRAY['CHEST']::TEXT[], equipment='PLATE', is_compound=false, unilateral=false, prescription_type='REPS', level='INTERMEDIATE' WHERE name = 'Supino com Anilha';
UPDATE exercises SET primary_muscles=ARRAY['CHEST']::TEXT[], equipment='MACHINE', is_compound=true, unilateral=false, prescription_type='REPS', level='BEGINNER' WHERE name = 'Supino declinada na Máquina';
UPDATE exercises SET primary_muscles=ARRAY['CHEST']::TEXT[], equipment='MACHINE', is_compound=true, unilateral=true, prescription_type='REPS', level='BEGINNER' WHERE name = 'Supino unilateral na Máquina';
UPDATE exercises SET primary_muscles=ARRAY['CHEST']::TEXT[], equipment='MACHINE', is_compound=false, unilateral=false, prescription_type='REPS', level='BEGINNER' WHERE name = 'Voador Inclinado na Máquina';
UPDATE exercises SET primary_muscles=ARRAY['TRICEPS']::TEXT[], equipment='BAND', is_compound=false, unilateral=false, prescription_type='REPS', level='BEGINNER' WHERE name = 'Extensão de Tríceps com Faixa Elástica';
UPDATE exercises SET primary_muscles=ARRAY['BICEPS']::TEXT[], equipment='BAND', is_compound=false, unilateral=false, prescription_type='REPS', level='BEGINNER' WHERE name = 'Rosca bíceps com faixa elástica';
UPDATE exercises SET primary_muscles=ARRAY['BICEPS']::TEXT[], equipment='BAND', is_compound=false, unilateral=false, prescription_type='REPS', level='BEGINNER' WHERE name = 'Rosca martelo com faixa de resistência';
UPDATE exercises SET primary_muscles=ARRAY['BICEPS']::TEXT[], equipment='BODYWEIGHT', is_compound=false, unilateral=false, prescription_type='REPS', level='INTERMEDIATE' WHERE name = 'Rosca martelo com garrafa de água';
UPDATE exercises SET primary_muscles=ARRAY['TRICEPS']::TEXT[], equipment='BAND', is_compound=false, unilateral=false, prescription_type='REPS', level='BEGINNER' WHERE name = 'Tríceps Francês com Faixa Elástica Acima da Cabeça';
UPDATE exercises SET primary_muscles=ARRAY['TRICEPS']::TEXT[], equipment='BAND', is_compound=false, unilateral=false, prescription_type='REPS', level='BEGINNER' WHERE name = 'Tríceps Testa com Faixa Elástica';
UPDATE exercises SET primary_muscles=ARRAY['GLUTES']::TEXT[], equipment='MACHINE', is_compound=false, unilateral=true, prescription_type='REPS', level='BEGINNER' WHERE name = 'Glúteo Coice na Máquina';
UPDATE exercises SET primary_muscles=ARRAY['LEGS']::TEXT[], equipment='MACHINE', is_compound=false, unilateral=false, prescription_type='REPS', level='BEGINNER' WHERE name = 'Adução de Quadril na Máquina';
UPDATE exercises SET primary_muscles=ARRAY['LEGS']::TEXT[], equipment='CABLE', is_compound=true, unilateral=true, prescription_type='REPS', level='INTERMEDIATE' WHERE name = 'Afundo com Cabo';
UPDATE exercises SET primary_muscles=ARRAY['LEGS']::TEXT[], equipment='MACHINE', is_compound=false, unilateral=false, prescription_type='REPS', level='BEGINNER' WHERE name = 'Cadeira / Mesa Flexora';
UPDATE exercises SET primary_muscles=ARRAY['LEGS']::TEXT[], equipment='MACHINE', is_compound=false, unilateral=true, prescription_type='REPS', level='BEGINNER' WHERE name = 'Elevação de Perna em Pé na Máquina';
UPDATE exercises SET primary_muscles=ARRAY['LEGS']::TEXT[], equipment='MACHINE', is_compound=false, unilateral=true, prescription_type='REPS', level='BEGINNER' WHERE name = 'Flexão de Perna Unilateral na Máquina';
UPDATE exercises SET primary_muscles=ARRAY['LEGS']::TEXT[], equipment='MACHINE', is_compound=true, unilateral=false, prescription_type='REPS', level='BEGINNER' WHERE name = 'levantamento terra na máquina';
UPDATE exercises SET primary_muscles=ARRAY['FOREARMS']::TEXT[], equipment='BODYWEIGHT', is_compound=false, unilateral=false, prescription_type='TIME', level='BEGINNER' WHERE name = 'Alongamento Reverso de Pulso';
UPDATE exercises SET primary_muscles=ARRAY['FOREARMS']::TEXT[], equipment='BODYWEIGHT', is_compound=false, unilateral=false, prescription_type='TIME', level='BEGINNER' WHERE name = 'Alongamento de Punho';
UPDATE exercises SET primary_muscles=ARRAY['TRICEPS']::TEXT[], equipment='BODYWEIGHT', is_compound=false, unilateral=false, prescription_type='TIME', level='BEGINNER' WHERE name = 'Alongamento de tríceps em pé';
UPDATE exercises SET primary_muscles=ARRAY['SHOULDERS']::TEXT[], equipment='BARBELL', is_compound=true, unilateral=false, prescription_type='REPS', level='INTERMEDIATE' WHERE name = 'Remada Alta';
UPDATE exercises SET primary_muscles=ARRAY['BACK']::TEXT[], equipment='DUMBBELL', is_compound=false, unilateral=false, prescription_type='REPS', level='INTERMEDIATE' WHERE name = 'Elevação com Halteres';
UPDATE exercises SET primary_muscles=ARRAY['TRICEPS']::TEXT[], equipment='MACHINE', is_compound=true, unilateral=false, prescription_type='REPS', level='BEGINNER' WHERE name = 'Mergulho de tríceps na Máquina';

-- 2. Duplicata real: linha combinada; "Cadeira flexora" e "Mesa flexora" já existem separadas.
UPDATE workout_exercises we SET exercise_id = s.id FROM exercises s, exercises d
 WHERE s.name = 'Cadeira flexora' AND d.name = 'Cadeira / Mesa Flexora' AND we.exercise_id = d.id;
UPDATE session_set_logs sl SET exercise_id = s.id FROM exercises s, exercises d
 WHERE s.name = 'Cadeira flexora' AND d.name = 'Cadeira / Mesa Flexora' AND sl.exercise_id = d.id;
DELETE FROM exercises WHERE name = 'Cadeira / Mesa Flexora';

-- 3. Variante de concordância do mesmo aparelho (escapou da normalização por diferir em letra).
UPDATE workout_exercises we SET exercise_id = s.id FROM exercises s, exercises d
 WHERE s.name = 'Supino Declinado na Máquina' AND d.name = 'Supino declinada na Máquina' AND we.exercise_id = d.id;
UPDATE session_set_logs sl SET exercise_id = s.id FROM exercises s, exercises d
 WHERE s.name = 'Supino Declinado na Máquina' AND d.name = 'Supino declinada na Máquina' AND sl.exercise_id = d.id;
DELETE FROM exercises WHERE name = 'Supino declinada na Máquina';

-- 4. Caso 5 do veredito, agora pelo nome real: id 72d6a804 era "Máquina de remo" (V4) e a V14
--    renomeou p/ "Remada na Máquina". A descrição é de ergômetro; a versão de força já existe
--    como "Remada Sentada na Máquina". Preenche a lacuna de remo no CARDIO.
UPDATE exercises SET name='Remo Ergômetro', category='CARDIO', primary_muscles=ARRAY['LEGS']::TEXT[],
       equipment='MACHINE', prescription_type='TIME', is_compound=true
 WHERE id = '72d6a804-e21e-5f50-a5e7-27839b5edcfe';
