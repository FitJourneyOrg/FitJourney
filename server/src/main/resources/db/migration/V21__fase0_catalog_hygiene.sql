-- V21: Fase 0 — higiene de catálogo (SÓ DADO; não toca o motor).
-- Motivação (auditoria Fase 0): o motor enxergava apenas 1 isolamento de QUADRÍCEPS
-- (uma extensora com elástico). Como o gerador não repete exercício no programa, os slots
-- de isolamento de quadríceps seguintes eram pulados (silent skip no SlotFiller) → dia de
-- perna saía só com composto (agachamentos/afundos). As extensoras existiam no catálogo,
-- mas a V9 nunca as taggeou → ficaram modality=NULL → invisíveis ao motor.
--
-- Esta migration:
--   1) Reativa 5 extensoras de quadríceps (KNEE_EXTENSION, is_compound=false) → visíveis.
--   2) Corrige is_compound da "Flexão nórdica" (isolamento de isquios, não composto).
-- Dedup de duplicatas (merge + repontamento de FK) fica FORA daqui — precisa de decisão do
-- Rafael sobre qual ID sobrevive (ver bloco PENDENTE no fim).

-- ============================================================================
-- 1) QUADRÍCEPS — reativar isolamento (KNEE_EXTENSION). Academia: MACHINE. Casa: BAND.
--    Só as que são de fato quadríceps; extensoras "de glúteo" (Elevação Pélvica na Máquina
--    de Extensão de Pernas, Glúteo Coice, Smith Reversa) foram deliberadamente EXCLUÍDAS.
-- ============================================================================

-- Cadeira extensora (base de academia)
UPDATE exercises SET modality='STRENGTH', movement_pattern='KNEE_EXTENSION', secondary_pattern=NULL,
  is_compound=false, equipment='MACHINE', primary_muscles=ARRAY['LEGS']::TEXT[],
  secondary_muscles=ARRAY[]::TEXT[], unilateral=false, prescription_type='REPS', level='BEGINNER',
  contraindications=ARRAY['KNEE']::TEXT[], is_base=true
  WHERE id='48194d9f-998f-5782-86e9-dc6b1e8afcc2';

-- Extensão de Perna Unilateral (máquina, unilateral)
UPDATE exercises SET modality='STRENGTH', movement_pattern='KNEE_EXTENSION', secondary_pattern=NULL,
  is_compound=false, equipment='MACHINE', primary_muscles=ARRAY['LEGS']::TEXT[],
  secondary_muscles=ARRAY[]::TEXT[], unilateral=true, prescription_type='REPS', level='BEGINNER',
  contraindications=ARRAY['KNEE']::TEXT[], is_base=false
  WHERE id='4db6256f-f8be-5bfb-a172-e5ad75f430af';

-- Extensão De Perna Reta (máquina)
UPDATE exercises SET modality='STRENGTH', movement_pattern='KNEE_EXTENSION', secondary_pattern=NULL,
  is_compound=false, equipment='MACHINE', primary_muscles=ARRAY['LEGS']::TEXT[],
  secondary_muscles=ARRAY[]::TEXT[], unilateral=false, prescription_type='REPS', level='BEGINNER',
  contraindications=ARRAY['KNEE']::TEXT[], is_base=false
  WHERE id='1a7f25a9-6953-5257-85d9-c298be0bf5a1';

-- Extensão de Pernas com Faixa Elástica Sentado (casa/elástico, base de casa)
UPDATE exercises SET modality='STRENGTH', movement_pattern='KNEE_EXTENSION', secondary_pattern=NULL,
  is_compound=false, equipment='BAND', primary_muscles=ARRAY['LEGS']::TEXT[],
  secondary_muscles=ARRAY[]::TEXT[], unilateral=false, prescription_type='REPS', level='BEGINNER',
  contraindications=ARRAY['KNEE']::TEXT[], is_base=true
  WHERE id='e0310bfd-88f9-5a44-82e5-5d1144b60e84';

-- Extensão de Perna em Pé com Faixa de Resistência (casa/elástico, unilateral típico)
UPDATE exercises SET modality='STRENGTH', movement_pattern='KNEE_EXTENSION', secondary_pattern=NULL,
  is_compound=false, equipment='BAND', primary_muscles=ARRAY['LEGS']::TEXT[],
  secondary_muscles=ARRAY[]::TEXT[], unilateral=true, prescription_type='REPS', level='BEGINNER',
  contraindications=ARRAY['KNEE']::TEXT[], is_base=false
  WHERE id='3321cdb7-8d97-58ce-bafa-3b3c94d02aa6';

-- Resultado: QUADS isolamento visível passa de 1 → 6 (academia); casa 1 → 3.

-- ============================================================================
-- 2) is_compound — Flexão nórdica é isolamento de isquiotibiais (só flexão de joelho),
--    não composto. Estava enchendo slot de COMPOSTO de posterior.
-- ============================================================================
UPDATE exercises SET is_compound=false
  WHERE id='db17b9b6-6425-5929-babe-ba61b3c40eca';

-- ============================================================================
-- PENDENTE (NÃO nesta migration) — DEDUP, precisa da decisão do Rafael sobre o sobrevivente.
-- Cada merge = UPDATE workout_exercises/session_set_logs SET exercise_id=<sobrevivente>
--             WHERE exercise_id=<duplicata>;  +  DELETE FROM exercises WHERE id=<duplicata>;
-- (mesmo padrão da V15). Candidatos:
--
-- Nome idêntico, AMBOS visíveis (co-ocorrem no motor — prioridade):
--   "Elevação de panturrilha em pé":  3dce0442  vs  e63cb82e (inserido na V16)
--   "Prancha lateral":                524e4b5c  vs  250ddb2b (inserido na V16)
--
-- Funcional (posterior chain, todos base+visíveis — mesma mecânica HINGE):
--   RDL (fb3814e9, halter)  ~=  "Levantamento terra romeno com barra" (dde5714a)
--   "Stiff com barra" (30a50289)  ~=  "Levantamento terra romeno com barra" (dde5714a)
--   "Stiff com halteres" (4a073e03)  ~=  RDL (fb3814e9)
--   -> decidir quais manter (ex.: 1 stiff/RDL por implemento) e quais mesclar.
-- ============================================================================
