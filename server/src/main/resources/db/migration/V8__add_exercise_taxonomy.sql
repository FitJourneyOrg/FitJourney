-- Taxonomia curada dos exercícios (Fatia F.1, ARCH #20).
-- Colunas nullable: só os curados são preenchidos; os demais ficam NULL e
-- ficam invisíveis ao motor de geração (mas continuam no catálogo p/ treino manual).
-- Listas como TEXT[] nativo (não JSON) porque SÃO FILTRADAS EM SQL:
--   WHERE NOT (contraindications && ARRAY['KNEE'])  <- exclusão dura do PAR-Q (§3.2)

ALTER TABLE exercises ADD COLUMN modality           VARCHAR(16);
ALTER TABLE exercises ADD COLUMN movement_pattern   VARCHAR(24);
ALTER TABLE exercises ADD COLUMN secondary_pattern  VARCHAR(24);
ALTER TABLE exercises ADD COLUMN is_compound        BOOLEAN;
ALTER TABLE exercises ADD COLUMN equipment          VARCHAR(24);
ALTER TABLE exercises ADD COLUMN primary_muscles    TEXT[];
ALTER TABLE exercises ADD COLUMN secondary_muscles  TEXT[];
ALTER TABLE exercises ADD COLUMN unilateral         BOOLEAN;
ALTER TABLE exercises ADD COLUMN prescription_type  VARCHAR(12);
ALTER TABLE exercises ADD COLUMN level              VARCHAR(16);
ALTER TABLE exercises ADD COLUMN contraindications  TEXT[];

-- Perfil: limitações do usuário (multi-select do quiz). JSON em TEXT segue o
-- padrão de focus_areas — é lido pela app, não filtrado em SQL.
ALTER TABLE profiles ADD COLUMN limitations TEXT;

-- Índices para o pré-filtro do motor (F.3).
CREATE INDEX idx_exercises_engine ON exercises (modality, movement_pattern, level)
    WHERE modality IS NOT NULL;
CREATE INDEX idx_exercises_contraind ON exercises USING GIN (contraindications);
CREATE INDEX idx_exercises_primary ON exercises USING GIN (primary_muscles);