-- V57 — curadoria de tagueamento: famílias com músculo inconsistente + invisíveis.
--
-- Gerada por tools/curadoria/gen_v57.py a partir do dump do banco vivo
-- (tools/curadoria/estado_taxonomia.csv). NÃO editar à mão: regerar.
--
-- O DEFEITO. 32 exercícios chegavam ao cliente com primary_muscles VAZIO, e nenhum
-- deles falhava em lugar nenhum: o servidor converte TEXT[] -> MuscleGroup com
-- `mapNotNull { runCatching { MuscleGroup.valueOf(it) }.getOrNull() }`, que DESCARTA
-- valor desconhecido em silêncio. Efeito: some do filtro por músculo da Biblioteca e
-- não conta volume no motor. Degrada calado — não crasha, não loga, não testa falso.
--
-- 1. TRAPEZIUS -> BACK. MuscleGroup tem 9 valores e TRAPEZIUS não é um deles (ele é
--    ExerciseCategory, outra taxonomia). Não é escolha arbitrária: o catálogo JÁ usa
--    essa convenção — categoria LOWER_BACK mapeia para BACK (5/6) e CALVES para LEGS
--    (18/18). TRAPEZIUS era a única categoria que a quebrava (17 seguiam, 9 não).
--    Custo aceito: volume de trapézio fica indistinguível dentro de BACK — é o débito
--    P3 'Vocabulário de músculo incompleto', que segue aberto de propósito.
--
-- 2. Famílias uniformizadas pelo MOVIMENTO, não pela categoria. O mesmo exercício
--    estava tagueado de três formas (crucifixo inverso: SHOULDERS em SHOULDERS, BACK
--    em TRAPEZIUS, SHOULDERS em FUNCTIONAL_HIT). Critério anatômico:
--      crucifixo inverso / voador invertido -> SHOULDERS (deltoide posterior isolado)
--      face pull                            -> SHOULDERS + BACK (mesmo alvo + trap médio)
--      remada alta                          -> SHOULDERS + BACK (deltoide + trap sup.)
--      encolhimento / elevação T,Y / depressão escapular -> BACK
--
-- 3. ExerciseCategory NÃO muda. É vocabulário de UI; o motor e o filtro leem músculo.
--    Quem procura deltoide posterior filtra por SHOULDERS e acha os 11, em qualquer
--    categoria — o filtro por MuscleGroup da Biblioteca já resolve o 'espalhado'.
--
-- FORA DE ESCOPO (deliberado): os 76 com movement_pattern curado e modality NULA,
-- invisíveis ao motor pelo índice `WHERE modality IS NOT NULL`. Decidir se CROSSFIT e
-- CALISTHENICS entram no motor de força é produto, não curadoria — vai no SlotFiller v2.
--
-- SEGURANÇA: UPDATE por `name`, NÃO por id. O catálogo é semeado com
-- `gen_random_uuid()` (ver V55), então o id é DIFERENTE em cada banco — no do dev, no
-- do Testcontainers, no de produção. Um `WHERE id` gerado a partir de um dump viraria
-- no-op em todo banco que não fosse aquele. É por isso que as migrations de curadoria
-- anteriores (V29–V33) usam nome; o padrão tinha razão. Nome inexistente = no-op, a
-- migration não quebra — mas aí o UPDATE some em silêncio, e é o
-- CuradoriaTagueamentoIntegrationTest que percebe.

-- Agachamento Apoiado na Cadeira Abdutora  (sem primary_muscles (invisivel ao filtro e ao motor))
--   primary_muscles: {} -> {GLUTES}
UPDATE exercises SET primary_muscles = '{GLUTES}' WHERE name = 'Agachamento Apoiado na Cadeira Abdutora';

-- Bom Dia com Barra  (sem primary_muscles (invisivel ao filtro e ao motor))
--   primary_muscles: {} -> {LEGS,GLUTES}
UPDATE exercises SET primary_muscles = '{LEGS,GLUTES}' WHERE name = 'Bom Dia com Barra';

-- Coice de Glúteo na Máquina  (sem primary_muscles (invisivel ao filtro e ao motor))
--   primary_muscles: {} -> {GLUTES}
UPDATE exercises SET primary_muscles = '{GLUTES}' WHERE name = 'Coice de Glúteo na Máquina';

-- Crucifixo Inverso Apoiado no Banco Inclinado  (deltoide posterior isolado)
--   primary_muscles: {BACK} -> {SHOULDERS}
UPDATE exercises SET primary_muscles = '{SHOULDERS}' WHERE name = 'Crucifixo Inverso Apoiado no Banco Inclinado';

-- Crucifixo Inverso Curvado no Cabo  (deltoide posterior isolado)
--   primary_muscles: {BACK} -> {SHOULDERS}
UPDATE exercises SET primary_muscles = '{SHOULDERS}' WHERE name = 'Crucifixo Inverso Curvado no Cabo';

-- Crucifixo Unilateral no Solo com Landmine  (sem primary_muscles (invisivel ao filtro e ao motor))
--   primary_muscles: {} -> {CHEST}
UPDATE exercises SET primary_muscles = '{CHEST}' WHERE name = 'Crucifixo Unilateral no Solo com Landmine';

-- Cruz de Ferro com Halteres  (sem primary_muscles (invisivel ao filtro e ao motor))
--   primary_muscles: {} -> {CHEST}
UPDATE exercises SET primary_muscles = '{CHEST}' WHERE name = 'Cruz de Ferro com Halteres';

-- Depressão Escapular no Banco  (trapezio medio/inferior -> BACK (convencao do catalogo))
--   primary_muscles: {TRAPEZIUS} -> {BACK}
UPDATE exercises SET primary_muscles = '{BACK}' WHERE name = 'Depressão Escapular no Banco';

-- Elevação Lateral Sentado com Tronco Inclinado  (sem primary_muscles (invisivel ao filtro e ao motor))
--   primary_muscles: {} -> {SHOULDERS}
UPDATE exercises SET primary_muscles = '{SHOULDERS}' WHERE name = 'Elevação Lateral Sentado com Tronco Inclinado';

-- Elevação Lateral na Máquina Sentado  (sem primary_muscles (invisivel ao filtro e ao motor))
--   primary_muscles: {} -> {SHOULDERS}
UPDATE exercises SET primary_muscles = '{SHOULDERS}' WHERE name = 'Elevação Lateral na Máquina Sentado';

-- Elevação Pélvica com Peso Corporal  (sem primary_muscles (invisivel ao filtro e ao motor))
--   primary_muscles: {} -> {GLUTES}
UPDATE exercises SET primary_muscles = '{GLUTES}' WHERE name = 'Elevação Pélvica com Peso Corporal';

-- Elevação em T Apoiado no Banco Inclinado  (trapezio medio/inferior -> BACK (convencao do catalogo))
--   primary_muscles: {TRAPEZIUS} -> {BACK}
UPDATE exercises SET primary_muscles = '{BACK}' WHERE name = 'Elevação em T Apoiado no Banco Inclinado';

-- Elevação em Y Apoiado no Banco Inclinado  (trapezio medio/inferior -> BACK (convencao do catalogo))
--   primary_muscles: {TRAPEZIUS} -> {BACK}
UPDATE exercises SET primary_muscles = '{BACK}' WHERE name = 'Elevação em Y Apoiado no Banco Inclinado';

-- Encolhimento acima da Cabeça  (trapezio superior -> BACK (convencao do catalogo))
--   primary_muscles: {TRAPEZIUS} -> {BACK}
UPDATE exercises SET primary_muscles = '{BACK}' WHERE name = 'Encolhimento acima da Cabeça';

-- Encolhimento com Barra  (trapezio superior -> BACK (convencao do catalogo))
--   primary_muscles: {TRAPEZIUS} -> {BACK}
UPDATE exercises SET primary_muscles = '{BACK}' WHERE name = 'Encolhimento com Barra';

-- Encolhimento com Halteres  (trapezio superior -> BACK (convencao do catalogo))
--   primary_muscles: {TRAPEZIUS} -> {BACK}
UPDATE exercises SET primary_muscles = '{BACK}' WHERE name = 'Encolhimento com Halteres';

-- Encolhimento na Máquina  (trapezio superior -> BACK (convencao do catalogo))
--   primary_muscles: {TRAPEZIUS} -> {BACK}
UPDATE exercises SET primary_muscles = '{BACK}' WHERE name = 'Encolhimento na Máquina';

-- Encolhimento na Máquina (Alavanca)  (trapezio superior -> BACK (convencao do catalogo))
--   primary_muscles: {} -> {BACK}
UPDATE exercises SET primary_muscles = '{BACK}' WHERE name = 'Encolhimento na Máquina (Alavanca)';

-- Encolhimento no Cabo  (trapezio superior -> BACK (convencao do catalogo))
--   primary_muscles: {TRAPEZIUS} -> {BACK}
UPDATE exercises SET primary_muscles = '{BACK}' WHERE name = 'Encolhimento no Cabo';

-- Face Pull (Puxada para o Rosto)  (deltoide posterior + trapezio medio; mesmo alvo do crucifixo inverso)
--   primary_muscles: {TRAPEZIUS} -> {SHOULDERS}
UPDATE exercises SET primary_muscles = '{SHOULDERS}' WHERE name = 'Face Pull (Puxada para o Rosto)';

-- Face Pull Ajoelhado  (deltoide posterior + trapezio medio; mesmo alvo do crucifixo inverso)
--   primary_muscles: {BACK} -> {SHOULDERS}
--   secondary_muscles: {} -> {BACK}
UPDATE exercises SET primary_muscles = '{SHOULDERS}', secondary_muscles = '{BACK}' WHERE name = 'Face Pull Ajoelhado';

-- Face Pull Semiajoelhado no Cabo  (deltoide posterior + trapezio medio; mesmo alvo do crucifixo inverso)
--   primary_muscles: {BACK} -> {SHOULDERS}
--   secondary_muscles: {} -> {BACK}
UPDATE exercises SET primary_muscles = '{SHOULDERS}', secondary_muscles = '{BACK}' WHERE name = 'Face Pull Semiajoelhado no Cabo';

-- Face Pull com Cabos Cruzados  (deltoide posterior + trapezio medio; mesmo alvo do crucifixo inverso)
--   primary_muscles: {BACK} -> {SHOULDERS}
--   secondary_muscles: {} -> {BACK}
UPDATE exercises SET primary_muscles = '{SHOULDERS}', secondary_muscles = '{BACK}' WHERE name = 'Face Pull com Cabos Cruzados';

-- Flexão Nórdica no Banco  (sem primary_muscles (invisivel ao filtro e ao motor))
--   primary_muscles: {} -> {LEGS}
UPDATE exercises SET primary_muscles = '{LEGS}' WHERE name = 'Flexão Nórdica no Banco';

-- Flexão com Mãos Invertidas  (sem primary_muscles (invisivel ao filtro e ao motor))
--   primary_muscles: {} -> {CHEST}
UPDATE exercises SET primary_muscles = '{CHEST}' WHERE name = 'Flexão com Mãos Invertidas';

-- Flexão com Transição para Cobra  (sem primary_muscles (invisivel ao filtro e ao motor))
--   primary_muscles: {} -> {CHEST,CORE}
UPDATE exercises SET primary_muscles = '{CHEST,CORE}' WHERE name = 'Flexão com Transição para Cobra';

-- Hiperextensão Lombar no Banco Reto  (sem primary_muscles (invisivel ao filtro e ao motor))
--   primary_muscles: {} -> {BACK}
UPDATE exercises SET primary_muscles = '{BACK}' WHERE name = 'Hiperextensão Lombar no Banco Reto';

-- Meio Desenvolvimento Arnold Sentado  (sem primary_muscles (invisivel ao filtro e ao motor))
--   primary_muscles: {} -> {SHOULDERS}
UPDATE exercises SET primary_muscles = '{SHOULDERS}' WHERE name = 'Meio Desenvolvimento Arnold Sentado';

-- Mergulho entre Bancos  (sem primary_muscles (invisivel ao filtro e ao motor))
--   primary_muscles: {} -> {TRICEPS}
UPDATE exercises SET primary_muscles = '{TRICEPS}' WHERE name = 'Mergulho entre Bancos';

-- Mesa Flexora Ajoelhado (Alavanca)  (sem primary_muscles (invisivel ao filtro e ao motor))
--   primary_muscles: {} -> {LEGS}
UPDATE exercises SET primary_muscles = '{LEGS}' WHERE name = 'Mesa Flexora Ajoelhado (Alavanca)';

-- Nadador (Swimming)  (sem primary_muscles (invisivel ao filtro e ao motor))
--   primary_muscles: {} -> {BACK,CORE}
UPDATE exercises SET primary_muscles = '{BACK,CORE}' WHERE name = 'Nadador (Swimming)';

-- Remada Alta Unilateral com Halter  (deltoide lateral/posterior + trapezio superior)
--   primary_muscles: {BACK} -> {SHOULDERS}
--   secondary_muscles: {} -> {BACK}
UPDATE exercises SET primary_muscles = '{SHOULDERS}', secondary_muscles = '{BACK}' WHERE name = 'Remada Alta Unilateral com Halter';

-- Remada Alta com Barra  (deltoide lateral/posterior + trapezio superior)
--   primary_muscles: {BACK} -> {SHOULDERS}
--   secondary_muscles: {} -> {BACK}
UPDATE exercises SET primary_muscles = '{SHOULDERS}', secondary_muscles = '{BACK}' WHERE name = 'Remada Alta com Barra';

-- Remada Alta com Barra W  (deltoide lateral/posterior + trapezio superior)
--   primary_muscles: {BACK} -> {SHOULDERS}
--   secondary_muscles: {} -> {BACK}
UPDATE exercises SET primary_muscles = '{SHOULDERS}', secondary_muscles = '{BACK}' WHERE name = 'Remada Alta com Barra W';

-- Remada Alta com Halter Único  (deltoide lateral/posterior + trapezio superior)
--   primary_muscles: {BACK} -> {SHOULDERS}
--   secondary_muscles: {} -> {BACK}
UPDATE exercises SET primary_muscles = '{SHOULDERS}', secondary_muscles = '{BACK}' WHERE name = 'Remada Alta com Halter Único';

-- Remada Alta com Halteres  (deltoide lateral/posterior + trapezio superior)
--   secondary_muscles: {} -> {BACK}
UPDATE exercises SET secondary_muscles = '{BACK}' WHERE name = 'Remada Alta com Halteres';

-- Remada Alta no Cabo  (deltoide lateral/posterior + trapezio superior)
--   primary_muscles: {BACK} -> {SHOULDERS}
--   secondary_muscles: {} -> {BACK}
UPDATE exercises SET primary_muscles = '{SHOULDERS}', secondary_muscles = '{BACK}' WHERE name = 'Remada Alta no Cabo';

-- Remada Apoiada no Banco a 45° com Halteres  (sem primary_muscles (invisivel ao filtro e ao motor))
--   primary_muscles: {} -> {SHOULDERS}
UPDATE exercises SET primary_muscles = '{SHOULDERS}' WHERE name = 'Remada Apoiada no Banco a 45° com Halteres';

-- Remada Deitado no Banco com Halteres  (sem primary_muscles (invisivel ao filtro e ao motor))
--   primary_muscles: {} -> {BACK}
UPDATE exercises SET primary_muscles = '{BACK}' WHERE name = 'Remada Deitado no Banco com Halteres';

-- Supino Declinado na Máquina (Alavanca)  (sem primary_muscles (invisivel ao filtro e ao motor))
--   primary_muscles: {} -> {CHEST}
UPDATE exercises SET primary_muscles = '{CHEST}' WHERE name = 'Supino Declinado na Máquina (Alavanca)';

-- Supino Inclinado na Máquina (Alavanca)  (sem primary_muscles (invisivel ao filtro e ao motor))
--   primary_muscles: {} -> {CHEST}
UPDATE exercises SET primary_muscles = '{CHEST}' WHERE name = 'Supino Inclinado na Máquina (Alavanca)';

-- Supino Sentado na Máquina (Alavanca)  (sem primary_muscles (invisivel ao filtro e ao motor))
--   primary_muscles: {} -> {CHEST}
UPDATE exercises SET primary_muscles = '{CHEST}' WHERE name = 'Supino Sentado na Máquina (Alavanca)';

-- Supino Sentado na Máquina Convergente  (sem primary_muscles (invisivel ao filtro e ao motor))
--   primary_muscles: {} -> {CHEST}
UPDATE exercises SET primary_muscles = '{CHEST}' WHERE name = 'Supino Sentado na Máquina Convergente';
