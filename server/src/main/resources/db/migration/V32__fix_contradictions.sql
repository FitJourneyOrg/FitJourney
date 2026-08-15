-- V32 (CANDIDATA) — resolve as 13 contradições + resíduos de duplicata + typos de nome.
-- Base: veredito_13_contradicoes.md (auditoria 3). APLICAR DEPOIS DA V31.
--
-- Padrões de segurança:
--   * dedup com sobrevivente: repoint de FK (workout_exercises + session_set_logs) e só então DELETE.
--     Os UPDATEs usam FROM exercises s, exercises d -> se um dos nomes não existir, vira no-op.
--   * remoção sem sobrevivente: DELETE guardado por NOT EXISTS nas FKs (não quebra a migration
--     se o exercício estiver em algum treino/sessão; nesse caso simplesmente não remove).
--   * renomes: só onde o nome novo NÃO existe (verificado na geração) -> sem colisão.

-- =====================================================================
-- 1. DUPLICATAS DISFARÇADAS — remover, repontando pro registro correto
-- =====================================================================

-- Flexão Invertida: a descrição é de remada invertida; já existe "Remada Invertida".
UPDATE workout_exercises we SET exercise_id = s.id FROM exercises s, exercises d
 WHERE s.name = 'Remada Invertida' AND d.name = 'Flexão Invertida' AND we.exercise_id = d.id;
UPDATE session_set_logs sl SET exercise_id = s.id FROM exercises s, exercises d
 WHERE s.name = 'Remada Invertida' AND d.name = 'Flexão Invertida' AND sl.exercise_id = d.id;
DELETE FROM exercises WHERE name = 'Flexão Invertida';

-- Cruz de ferro com halteres: não existe versão com halteres; a descrição é de crucifixo.
UPDATE workout_exercises we SET exercise_id = s.id FROM exercises s, exercises d
 WHERE s.name = 'Crucifixo com halteres' AND d.name = 'Cruz de ferro com halteres' AND we.exercise_id = d.id;
UPDATE session_set_logs sl SET exercise_id = s.id FROM exercises s, exercises d
 WHERE s.name = 'Crucifixo com halteres' AND d.name = 'Cruz de ferro com halteres' AND sl.exercise_id = d.id;
DELETE FROM exercises WHERE name = 'Cruz de ferro com halteres';

-- Agachamento na Máquina Abdutora: a máquina não permite agachar; é abdução pura.
UPDATE workout_exercises we SET exercise_id = s.id FROM exercises s, exercises d
 WHERE s.name = 'Máquina de Abdução de Quadril' AND d.name = 'Agachamento na Máquina Abdutora' AND we.exercise_id = d.id;
UPDATE session_set_logs sl SET exercise_id = s.id FROM exercises s, exercises d
 WHERE s.name = 'Máquina de Abdução de Quadril' AND d.name = 'Agachamento na Máquina Abdutora' AND sl.exercise_id = d.id;
DELETE FROM exercises WHERE name = 'Agachamento na Máquina Abdutora';

-- Máquina de flexão de tríceps: nome anatomicamente invertido (tríceps ESTENDE) + duplicata.
UPDATE workout_exercises we SET exercise_id = s.id FROM exercises s, exercises d
 WHERE s.name = 'Extensão de tríceps na máquina' AND d.name = 'Máquina de flexão de tríceps' AND we.exercise_id = d.id;
UPDATE session_set_logs sl SET exercise_id = s.id FROM exercises s, exercises d
 WHERE s.name = 'Extensão de tríceps na máquina' AND d.name = 'Máquina de flexão de tríceps' AND sl.exercise_id = d.id;
DELETE FROM exercises WHERE name = 'Máquina de flexão de tríceps';

-- Resíduos de duplicação "(1)/(2)" — o original existe.
UPDATE workout_exercises we SET exercise_id = s.id FROM exercises s, exercises d
 WHERE s.name = 'Flexão Nórdica' AND d.name = 'Flexão Nórdica (2)' AND we.exercise_id = d.id;
UPDATE session_set_logs sl SET exercise_id = s.id FROM exercises s, exercises d
 WHERE s.name = 'Flexão Nórdica' AND d.name = 'Flexão Nórdica (2)' AND sl.exercise_id = d.id;
DELETE FROM exercises WHERE name = 'Flexão Nórdica (2)';

UPDATE workout_exercises we SET exercise_id = s.id FROM exercises s, exercises d
 WHERE s.name = 'Bom dia' AND d.name = 'Bom dia (1)' AND we.exercise_id = d.id;
UPDATE session_set_logs sl SET exercise_id = s.id FROM exercises s, exercises d
 WHERE s.name = 'Bom dia' AND d.name = 'Bom dia (1)' AND sl.exercise_id = d.id;
DELETE FROM exercises WHERE name = 'Bom dia (1)';

UPDATE workout_exercises we SET exercise_id = s.id FROM exercises s, exercises d
 WHERE s.name = 'Tríceps no Banco' AND d.name = 'Tríceps no Banco(1)' AND we.exercise_id = d.id;
UPDATE session_set_logs sl SET exercise_id = s.id FROM exercises s, exercises d
 WHERE s.name = 'Tríceps no Banco' AND d.name = 'Tríceps no Banco(1)' AND sl.exercise_id = d.id;
DELETE FROM exercises WHERE name = 'Tríceps no Banco(1)';

-- Duplicata exata restante: "Remada inclinada a 45 graus" (minúsculo) x "Remada Inclinada a 45 Graus".
-- Mantém o registro capitalizado e corrige a categoria (não é TRAPEZIUS nem SHOULDERS: é dorsal).
UPDATE workout_exercises we SET exercise_id = s.id FROM exercises s, exercises d
 WHERE s.name = 'Remada Inclinada a 45 Graus' AND d.name = 'Remada inclinada a 45 graus' AND we.exercise_id = d.id;
UPDATE session_set_logs sl SET exercise_id = s.id FROM exercises s, exercises d
 WHERE s.name = 'Remada Inclinada a 45 Graus' AND d.name = 'Remada inclinada a 45 graus' AND sl.exercise_id = d.id;
DELETE FROM exercises WHERE name = 'Remada inclinada a 45 graus';
UPDATE exercises SET category = 'BACK' WHERE name = 'Remada Inclinada a 45 Graus';

-- =====================================================================
-- 2. REMOÇÕES SEM SOBREVIVENTE (registro sem correspondente real)
--    Guardadas: não removem se estiverem em uso.
-- =====================================================================

-- "voador com barra" é mecanicamente impossível; nome, equipamento e descrição divergem entre si.
DELETE FROM exercises e WHERE e.name = 'Voador unilateral no Solo com Barra'
  AND NOT EXISTS (SELECT 1 FROM workout_exercises w WHERE w.exercise_id = e.id)
  AND NOT EXISTS (SELECT 1 FROM session_set_logs l WHERE l.exercise_id = e.id);

-- Não corresponde a movimento reconhecido; descrição genérica. Variações plausíveis já existem.
DELETE FROM exercises e WHERE e.name = 'Remada lateral com halteres sentado'
  AND NOT EXISTS (SELECT 1 FROM workout_exercises w WHERE w.exercise_id = e.id)
  AND NOT EXISTS (SELECT 1 FROM session_set_logs l WHERE l.exercise_id = e.id);

-- Natação em piscina não cabe numa base de exercícios de academia.
DELETE FROM exercises e WHERE e.name = 'Swimming'
  AND NOT EXISTS (SELECT 1 FROM workout_exercises w WHERE w.exercise_id = e.id)
  AND NOT EXISTS (SELECT 1 FROM session_set_logs l WHERE l.exercise_id = e.id);

-- =====================================================================
-- 3. RECLASSIFICAÇÕES (descrição estava certa; a classificação é que errou)
-- =====================================================================

-- Máquina de remo = ergômetro (a descrição diz "treino cardiovascular"). A versão de força
-- já existe como "Remada Sentada na Máquina". Preenche a lacuna de remo no CARDIO.
UPDATE exercises SET
    name = 'Remo Ergômetro',
    category = 'CARDIO',
    primary_muscles = ARRAY['LEGS']::TEXT[],
    equipment = 'MACHINE',
    prescription_type = 'TIME',
    is_compound = true
 WHERE name = 'Máquina de remo';

-- "Flexão de joelhos": as leituras de leg curl e agachamento já existem na base. A única
-- variação ausente — e a mais prescrita como progressão de entrada — é a flexão de braço
-- apoiada nos joelhos. Repropõe o registro e reescreve a descrição.
UPDATE exercises SET
    name = 'Flexão de Braço de Joelhos',
    category = 'CHEST',
    primary_muscles = ARRAY['CHEST']::TEXT[],
    equipment = 'BODYWEIGHT',
    level = 'BEGINNER',
    is_compound = true,
    prescription_type = 'REPS',
    description = 'A flexão de braço de joelhos é a progressão de entrada para quem ainda não '
        || 'executa a flexão completa. Apoie as mãos no chão na largura dos ombros e os joelhos '
        || 'no solo, mantendo tronco e quadril alinhados. Desça o peito em direção ao chão '
        || 'controlando o movimento e empurre de volta até estender os cotovelos.'
        || E'\n\n'
        || 'Trabalha peitoral, tríceps e ombros com uma fração da carga da flexão tradicional, '
        || 'o que permite treinar o padrão de empurrar com técnica correta desde o início.'
 WHERE name = 'Flexão de joelhos';

-- Nome se contradiz: elevação lateral trabalha deltoide MEDIAL, mas o exercício (e a descrição)
-- é de deltoide posterior. Só renomeia — a versão bilateral não existia na base.
UPDATE exercises SET name = 'Elevação Posterior com Halteres em Decúbito Ventral'
 WHERE name = 'Elevação lateral com halteres para deltoides posteriores deitado';

-- Descrição divergente (falava de quadríceps) num registro coerente de tríceps: corrige o texto.
UPDATE exercises SET description =
    'A extensão concentrada com cabo no joelho é um exercício de isolamento para o tríceps. '
    || 'Apoiado com o braço fixo junto ao joelho, estenda o cotovelo contra a resistência do cabo '
    || 'até a contração completa e retorne controlando a fase excêntrica.'
    || E'\n\n'
    || 'Por manter tensão constante ao longo de toda a amplitude, o cabo é uma opção eficiente '
    || 'para trabalhar o tríceps com carga leve e baixo estresse articular.'
 WHERE name = 'Extensão Concentrada com Cabo no Joelho';

-- =====================================================================
-- 4. NOMES — typos, tradução e resíduos (sem colisão: nomes novos não existiam)
-- =====================================================================
UPDATE exercises SET name = 'Corrida Lateral'                         WHERE name = 'Corrida Latera';
UPDATE exercises SET name = 'Navy Seal Burpee'                        WHERE name = 'Nave Seal Burpee';
UPDATE exercises SET name = 'Glúteos Coice Unilateral na Polia Baixa' WHERE name = 'Gluteos Coice nilateral Polia Baixa';
UPDATE exercises SET name = 'Levantamento Terra Pernas Rígidas com Barra' WHERE name = 'Peso muerto piernas rígidas con barra';
UPDATE exercises SET name = 'Remada Alta'                             WHERE name = 'Remada Alta (1)';
UPDATE exercises SET name = 'Abdominal de Rã com Bola de Exercícios'  WHERE name = 'Cópia de Abdominal de Rã com Bola de Exercícios';
UPDATE exercises SET name = 'Extensão de Tríceps Deitado com Barra'   WHERE name = 'Extensão de Tríceps com deitado com Barra';
UPDATE exercises SET name = 'Extensão de Tríceps Invertida Unilateral' WHERE name = 'Extensão de Tríceps Invertida com unilateral';
UPDATE exercises SET name = 'Remada Invertida com Cabo Inclinado'     WHERE name = 'Remada invertida com cable inclinado';
UPDATE exercises SET name = 'Supino Declinado com Alavanca'           WHERE name = 'Supino declinada com alavanca';
UPDATE exercises SET name = 'Supino Declinado na Máquina'             WHERE name = 'Supino declinada na máquina';
-- "colete scott" -> banco Scott (a própria descrição já escreve "banco Scott")
UPDATE exercises SET name = 'Rosca Direta com Barra no Banco Scott'   WHERE name = 'Rosca Direta com Barra no colete scott';
UPDATE exercises SET name = 'Rosca com Halteres no Banco Scott'       WHERE name = 'Rosca com halteres no colete scott';
UPDATE exercises SET name = 'Rosca Martelo com Halter no Banco Scott' WHERE name = 'Rosca martelo com halter no colete scott';

-- =====================================================================
-- 5. PRESCRIÇÃO — ergômetros não são prescritos por repetição
-- =====================================================================
UPDATE exercises SET prescription_type = 'TIME'
 WHERE name IN ('Airbike', 'Bike') AND prescription_type = 'REPS';
