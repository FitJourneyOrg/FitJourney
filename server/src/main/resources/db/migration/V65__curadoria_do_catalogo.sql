-- V65 -- curadoria do catalogo: taxonomia dos 893 e os atributos que escolhem o exercicio.
--
-- GERADA por tools/curadoria_oficial/gerar_v65.py a partir de
-- tools/curadoria_oficial/curadoria_canonica.csv (estado final) e de estado_banco.csv (o de hoje).
-- Nao editar a mao: regerar.
--
-- ============================================================================
-- O DEFEITO
-- ============================================================================
-- 548 dos 922 exercicios chegaram ate aqui com `modality` NULA. O indice do pre-filtro e
-- `WHERE modality IS NOT NULL`, entao esses exercicios estao no catalogo, aparecem na
-- biblioteca e sao invisiveis ao motor: nunca entram num programa gerado. Outros 203 tinham
-- taxonomia, mas nao tinham sido conferidos contra a execucao real do movimento -- havia
-- composto marcado como isolado, musculo primario errado, equipamento trocado e contraindicacao
-- faltando.
--
-- E, mesmo entre os visiveis, o motor nao tinha como escolher BEM: o SlotFiller desempatava por
-- `is_base` (dois valores) e por ordem alfabetica. Com 23 supinos no catalogo, "qual supino" era
-- sorte.
--
-- ============================================================================
-- O QUE FOI FEITO
-- ============================================================================
-- Curadoria exercicio a exercicio das 17 categorias (855 linhas), conferida contra os videos do
-- proprio catalogo e, quando o movimento nao era obvio, contra fonte externa (ExRx, NSCA, ACSM,
-- EMG em PubMed) -- a fonte de cada decisao esta na planilha, com o nivel de confianca:
-- 543 ALTA, 284 MEDIA, 0 pendente. Validada por profissional com CREF.
--
-- A planilha e a fonte: tools/curadoria_oficial/ (01_peito.xlsx .. 17_cardio.xlsx,
-- _referencia_curados.xlsx, lista_principal.xlsx), com LEIA-ME descrevendo o estado e o que falta.
--
-- ============================================================================
-- O QUE ESTA MIGRATION MUDA
-- ============================================================================
--   1. Remove 29 exercicios: duplicados do mesmo movimento e um aparelho passivo. So depois de
--      PROVAR que nenhum esta em uso (a V51 pode usar DELETE porque mediu; aqui a migration mede
--      sozinha e para se achar referencia).
--   2. Cria as colunas de selecao: prioridade, relevancia, nivel_maximo, ambiente, familia e os
--      seis acessorios.
--   3. Aplica a taxonomia curada em 840 exercicios (so o que muda, campo a campo).
--   4. Preenche prioridade, relevancia, nivel_maximo e ambiente nos 147 da lista principal.
--   5. Preenche familia e acessorios no catalogo inteiro (893).
--   6. Realinha is_base com relevancia enquanto as duas convivem.
--   7. Amarra, no schema, "estar na lista principal" a "ter os quatro atributos".
--
-- ============================================================================
-- O QUE MUDA DE COMPORTAMENTO, E POR QUE NAO QUEBRA
-- ============================================================================
-- Preencher `modality` leva o pool do pre-filtro de 270 para 700 exercicios STRENGTH -- hoje 560
-- do catalogo estao com modality NULA e o indice e `WHERE modality IS NOT NULL`. Isso NAO inunda
-- os programas: o SlotFiller tenta `pooled.filter { it.isBase }` antes de usar o pool inteiro, e
-- a secao 6 deixa `is_base` verdadeiro so para os 43 de relevancia 1. O dia em que o SlotFiller
-- for reescrito (fatia 6), quem limita o pool passa a ser `volume_target IS NOT NULL`, que e a
-- lista principal de verdade.
--
-- CONFERIDA de verdade, nao no olho: V1..V64 foram reaplicadas num Postgres 16 limpo (922
-- exercicios, 147 com alvo) e esta migration rodou em cima, sem erro.
--
-- ============================================================================
-- O QUE ESTA MIGRATION NAO FAZ, DE PROPOSITO
-- ============================================================================
--   * Nao derruba `is_base`. Ela e lida em ExercisesTable.kt, Exercise.kt,
--     ExerciseRepositoryImpl.kt, SlotFiller.kt e tres testes; o DROP vai com a fatia 6.
--   * Nao mexe em `contraindications`. O dado curado continua na tabela e serve a biblioteca,
--     mas o motor deixa de filtrar por ele (decisao do Rafael, 21/10): como filtro duro ele
--     esvaziava grupo inteiro -- dor no ombro deixava 1 exercicio de peito, dor no joelho
--     deixava 0 de quadriceps. Quem tira o filtro e o ExercisePreFilter.kt, nao esta migration.
--   * Nao cria exercicio novo. Os 68 que entraram na lista principal ja estavam no catalogo.
--     Os tres ultimos entraram depois da simulacao do motor (ver o cabecalho da V64).
--   * Nao traduz nem renomeia nada. Nome e chave aqui, como em todas as migrations de curadoria.
--
-- ============================================================================
-- SEGURANCA
-- ============================================================================
-- UPDATE e DELETE por `name`, NUNCA por id: o catalogo e semeado com gen_random_uuid() (V55),
-- entao o id difere entre o banco do dev, o do Testcontainers e o de producao. Nome inexistente
-- vira no-op silencioso -- e por isso a secao 8 confere as contagens e derruba a migration se
-- divergirem, em vez de deixar um exercicio chegar torto ao motor.

-- ============================================================================
-- 1. SAEM DO CATALOGO (29)
-- ============================================================================
-- Duplicados e um aparelho passivo, decididos um a um com o Rafael. O motivo de cada um
-- esta na aba 'Remover na migration' da planilha da categoria.
--
-- GUARDA: a V51 so pode usar DELETE porque mediu que ninguem referenciava. Aqui a medicao
-- nao existe (a curadoria rodou sobre um dump, nao sobre o banco vivo), entao a migration
-- verifica na hora e PARA se achar referencia, em vez de estourar violacao de FK no meio
-- do deploy ou apagar treino de alguem.
DO $$
DECLARE
    presos text;
BEGIN
    SELECT string_agg(DISTINCT e.name, ', ') INTO presos
      FROM exercises e JOIN workout_exercises we ON we.exercise_id = e.id
     WHERE e.name IN (
        'Afundo com Peso Corporal',
        'Agachamento com Salto Grupado',
        'Alongamento Lateral de Tronco em Pé',
        'Alongamento Reverso de Peitoral no Banco',
        'Caminhada Rápida',
        'Coice de Glúteo em Pé na Polia',
        'Coice de Glúteo na Cadeira Extensora',
        'Crucifixo Declinado com Halteres (Visão 2)',
        'Elevação Lateral Deitado de Lado com Halter (Deltoide Posterior)',
        'Elevação Lateral Sentado com Tronco Inclinado',
        'Extensão Lombar com Peso',
        'Face Pull Ajoelhado',
        'Flexão Nórdica no Banco',
        'Flexão com Transição para Cobra',
        'Inclinação Lateral em Pé com Braço Estendido',
        'Mergulho na Cadeira',
        'Mergulho nas Paralelas',
        'Panturrilha em Pé na Máquina com Apoio nos Ombros',
        'Plataforma Vibratória',
        'Puxada Alta Unilateral Ajoelhado',
        'Remada Apoiada no Banco a 45° com Halteres',
        'Remada Cavalinho (T-Bar) com Barra',
        'Remada Unilateral em Pé no Cabo',
        'Rosca Bíceps no Banco Inclinado com Cabo',
        'Rosca Spider Unilateral em Pé com Halter',
        'Salto na Caixa Unilateral',
        'Supino Inclinado Pegada Supinada com Halteres',
        'Tríceps Francês na Polia com Corda',
        'Tríceps Testa Deitado no Cabo');
    IF presos IS NOT NULL THEN
        RAISE EXCEPTION 'V65: exercicio a remover esta em uso em workout_exercises: %', presos;
    END IF;
END $$;

DELETE FROM exercises WHERE name IN (
    'Afundo com Peso Corporal',
    'Agachamento com Salto Grupado',
    'Alongamento Lateral de Tronco em Pé',
    'Alongamento Reverso de Peitoral no Banco',
    'Caminhada Rápida',
    'Coice de Glúteo em Pé na Polia',
    'Coice de Glúteo na Cadeira Extensora',
    'Crucifixo Declinado com Halteres (Visão 2)',
    'Elevação Lateral Deitado de Lado com Halter (Deltoide Posterior)',
    'Elevação Lateral Sentado com Tronco Inclinado',
    'Extensão Lombar com Peso',
    'Face Pull Ajoelhado',
    'Flexão Nórdica no Banco',
    'Flexão com Transição para Cobra',
    'Inclinação Lateral em Pé com Braço Estendido',
    'Mergulho na Cadeira',
    'Mergulho nas Paralelas',
    'Panturrilha em Pé na Máquina com Apoio nos Ombros',
    'Plataforma Vibratória',
    'Puxada Alta Unilateral Ajoelhado',
    'Remada Apoiada no Banco a 45° com Halteres',
    'Remada Cavalinho (T-Bar) com Barra',
    'Remada Unilateral em Pé no Cabo',
    'Rosca Bíceps no Banco Inclinado com Cabo',
    'Rosca Spider Unilateral em Pé com Halter',
    'Salto na Caixa Unilateral',
    'Supino Inclinado Pegada Supinada com Halteres',
    'Tríceps Francês na Polia com Corda',
    'Tríceps Testa Deitado no Cabo');

-- ============================================================================
-- 2. COLUNAS NOVAS: como o motor escolhe QUAL exercicio entra na vaga
-- ============================================================================
-- A V64 diz quantas series de cada musculo o programa tem. Estas colunas dizem qual dos 23
-- supinos preenche a vaga de peito. Sem elas, o SlotFiller desempata por is_base e ordem
-- alfabetica, e o avancado na academia recebe flexao de braco.
ALTER TABLE exercises ADD COLUMN prioridade    SMALLINT;
ALTER TABLE exercises ADD COLUMN relevancia    SMALLINT;
ALTER TABLE exercises ADD COLUMN nivel_maximo  VARCHAR(16);
ALTER TABLE exercises ADD COLUMN ambiente      VARCHAR(16);
ALTER TABLE exercises ADD COLUMN familia       VARCHAR(32);

-- Acessorio e propriedade do exercicio, nao do usuario: NOT NULL DEFAULT false, porque
-- 'nao marcado' significa 'nao exige', nunca 'nao sei'.
ALTER TABLE exercises ADD COLUMN requires_bench             BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE exercises ADD COLUMN requires_elevated_surface  BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE exercises ADD COLUMN requires_pullup_bar        BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE exercises ADD COLUMN requires_dip_station       BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE exercises ADD COLUMN requires_step              BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE exercises ADD COLUMN requires_anchor_point      BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE exercises ADD CONSTRAINT chk_exercises_relevancia
    CHECK (relevancia IS NULL OR relevancia BETWEEN 1 AND 3);
ALTER TABLE exercises ADD CONSTRAINT chk_exercises_prioridade
    CHECK (prioridade IS NULL OR prioridade > 0);
ALTER TABLE exercises ADD CONSTRAINT chk_exercises_nivel_maximo
    CHECK (nivel_maximo IS NULL OR nivel_maximo IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED'));
ALTER TABLE exercises ADD CONSTRAINT chk_exercises_ambiente
    CHECK (ambiente IS NULL OR ambiente IN ('ACADEMIA', 'CASA', 'AMBOS'));
-- O CHECK que amarra volume_target a relevancia entra no fim, depois dos dados: criado
-- aqui, ele quebraria na hora, porque a V64 ja deixou volume_target preenchido e
-- relevancia ainda esta NULL.

CREATE INDEX idx_exercises_selecao ON exercises (volume_target, relevancia, prioridade)
    WHERE volume_target IS NOT NULL;

-- ============================================================================
-- 3. TAXONOMIA CURADA (893 exercicios no catalogo)
-- ============================================================================
-- So o que MUDA em relacao ao banco. Cada bloco traz o antes -> depois do campo, como na
-- V57: quem revisa le o diff, nao o estado inteiro.

-- Abdominal Grupado com Bola Suíça
--   secondary_muscles: LEGS, GLUTES -> LEGS
UPDATE exercises SET secondary_muscles = ARRAY['LEGS']::TEXT[] WHERE name = 'Abdominal Grupado com Bola Suíça';

-- Abdominal com Arremesso de Bola Medicinal na Parede
--   modality: (vazio) -> PLYOMETRIC
--   movement_pattern: (vazio) -> NONE
--   secondary_muscles: (vazio) -> LEGS, BACK
--   contraindications: (vazio) -> LUMBAR
UPDATE exercises SET modality = 'PLYOMETRIC', movement_pattern = 'NONE', secondary_muscles = ARRAY['LEGS', 'BACK']::TEXT[], contraindications = ARRAY['LUMBAR']::TEXT[] WHERE name = 'Abdominal com Arremesso de Bola Medicinal na Parede';

-- Abdução Horizontal Unilateral com Elástico
--   movement_pattern: HORIZONTAL_PULL -> NONE
--   is_compound: sim -> não
--   primary_muscles: BACK -> SHOULDERS
--   secondary_muscles: SHOULDERS -> BACK
UPDATE exercises SET movement_pattern = 'NONE', is_compound = false, primary_muscles = ARRAY['SHOULDERS']::TEXT[], secondary_muscles = ARRAY['BACK']::TEXT[] WHERE name = 'Abdução Horizontal Unilateral com Elástico';

-- Abdução de Quadril Deitado de Lado com Miniband
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   secondary_muscles: (vazio) -> LEGS
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', secondary_muscles = ARRAY['LEGS']::TEXT[] WHERE name = 'Abdução de Quadril Deitado de Lado com Miniband';

-- Abdução de Quadril em Prancha Lateral
--   secondary_muscles: CORE -> LEGS
--   level: BEGINNER -> INTERMEDIATE
--   contraindications: (vazio) -> SHOULDER, WRIST
UPDATE exercises SET secondary_muscles = ARRAY['LEGS']::TEXT[], level = 'INTERMEDIATE', contraindications = ARRAY['SHOULDER', 'WRIST']::TEXT[] WHERE name = 'Abdução de Quadril em Prancha Lateral';

-- Abdução de Quadril em Pé
--   secondary_muscles: CORE -> LEGS
UPDATE exercises SET secondary_muscles = ARRAY['LEGS']::TEXT[] WHERE name = 'Abdução de Quadril em Pé';

-- Abdução em Quatro Apoios (Fire Hydrant)
--   secondary_muscles: LEGS, CORE -> LEGS
UPDATE exercises SET secondary_muscles = ARRAY['LEGS']::TEXT[] WHERE name = 'Abdução em Quatro Apoios (Fire Hydrant)';

-- Abertura com Faixa Elástica (Pull-Apart)
--   movement_pattern: HORIZONTAL_PULL -> NONE
--   is_compound: sim -> não
--   primary_muscles: BACK -> SHOULDERS
--   secondary_muscles: SHOULDERS, CORE -> BACK
UPDATE exercises SET movement_pattern = 'NONE', is_compound = false, primary_muscles = ARRAY['SHOULDERS']::TEXT[], secondary_muscles = ARRAY['BACK']::TEXT[] WHERE name = 'Abertura com Faixa Elástica (Pull-Apart)';

-- Abertura de Pernas Deitado (Alongamento de Adutores)
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> STRETCH
--   primary_muscles: GLUTES -> LEGS
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'STRETCH', primary_muscles = ARRAY['LEGS']::TEXT[] WHERE name = 'Abertura de Pernas Deitado (Alongamento de Adutores)';

-- Adução de Ombro com Faixa Elástica
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> VERTICAL_PULL
--   primary_muscles: SHOULDERS -> BACK
--   secondary_muscles: (vazio) -> SHOULDERS
--   unilateral: não -> sim
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'VERTICAL_PULL', primary_muscles = ARRAY['BACK']::TEXT[], secondary_muscles = ARRAY['SHOULDERS']::TEXT[], unilateral = true WHERE name = 'Adução de Ombro com Faixa Elástica';

-- Adução de Quadril Deitado de Lado
--   secondary_muscles: CORE -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Adução de Quadril Deitado de Lado';

-- Adução de Quadril em Pé na Máquina (Alavanca)
--   secondary_muscles: CORE -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Adução de Quadril em Pé na Máquina (Alavanca)';

-- Adução de Quadril na Polia
--   secondary_muscles: CORE -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Adução de Quadril na Polia';

-- Afundo Alternado com Salto
--   secondary_muscles: CORE -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Afundo Alternado com Salto';

-- Afundo Caminhando com Elevação de Joelho
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> LUNGE
--   primary_muscles: LEGS -> LEGS, GLUTES
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> KNEE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'LUNGE', primary_muscles = ARRAY['LEGS', 'GLUTES']::TEXT[], level = 'BEGINNER', contraindications = ARRAY['KNEE']::TEXT[] WHERE name = 'Afundo Caminhando com Elevação de Joelho';

-- Afundo Cruzado (Curtsy Lunge)
--   secondary_muscles: CORE -> (vazio)
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[], level = 'BEGINNER' WHERE name = 'Afundo Cruzado (Curtsy Lunge)';

-- Afundo Cruzado com Barra
--   secondary_muscles: CORE -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Afundo Cruzado com Barra';

-- Afundo Cruzado com Halter
--   secondary_muscles: CORE -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Afundo Cruzado com Halter';

-- Afundo Lateral
--   secondary_muscles: CORE -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Afundo Lateral';

-- Afundo Lateral com Barra
--   secondary_muscles: CORE -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Afundo Lateral com Barra';

-- Afundo Profundo
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> LUNGE
--   primary_muscles: GLUTES -> LEGS, GLUTES
--   contraindications: (vazio) -> KNEE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'LUNGE', primary_muscles = ARRAY['LEGS', 'GLUTES']::TEXT[], contraindications = ARRAY['KNEE']::TEXT[] WHERE name = 'Afundo Profundo';

-- Afundo Reverso
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> LUNGE
--   primary_muscles: LEGS -> LEGS, GLUTES
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> KNEE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'LUNGE', primary_muscles = ARRAY['LEGS', 'GLUTES']::TEXT[], level = 'BEGINNER', contraindications = ARRAY['KNEE']::TEXT[] WHERE name = 'Afundo Reverso';

-- Afundo Reverso com Elevação de Joelho
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> LUNGE
--   primary_muscles: LEGS -> LEGS, GLUTES
--   unilateral: não -> sim
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> KNEE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'LUNGE', primary_muscles = ARRAY['LEGS', 'GLUTES']::TEXT[], unilateral = true, level = 'BEGINNER', contraindications = ARRAY['KNEE']::TEXT[] WHERE name = 'Afundo Reverso com Elevação de Joelho';

-- Afundo Reverso com Halteres
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> LUNGE
--   primary_muscles: LEGS -> LEGS, GLUTES
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> KNEE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'LUNGE', primary_muscles = ARRAY['LEGS', 'GLUTES']::TEXT[], level = 'BEGINNER', contraindications = ARRAY['KNEE']::TEXT[] WHERE name = 'Afundo Reverso com Halteres';

-- Afundo com Barra
--   secondary_muscles: CORE -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Afundo com Barra';

-- Afundo com Faixa Elástica
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> LUNGE
--   primary_muscles: GLUTES -> LEGS, GLUTES
--   contraindications: (vazio) -> KNEE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'LUNGE', primary_muscles = ARRAY['LEGS', 'GLUTES']::TEXT[], contraindications = ARRAY['KNEE']::TEXT[] WHERE name = 'Afundo com Faixa Elástica';

-- Afundo com Gymstick
--   modality: (vazio) -> STRENGTH
--   secondary_muscles: CORE -> (vazio)
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', secondary_muscles = ARRAY[]::TEXT[], level = 'BEGINNER' WHERE name = 'Afundo com Gymstick';

-- Afundo com Halteres
--   secondary_muscles: CORE -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Afundo com Halteres';

-- Afundo com Salto e Halteres
--   modality: (vazio) -> PLYOMETRIC
--   movement_pattern: (vazio) -> LUNGE
--   primary_muscles: GLUTES -> LEGS, GLUTES
--   contraindications: (vazio) -> IMPACT, KNEE
UPDATE exercises SET modality = 'PLYOMETRIC', movement_pattern = 'LUNGE', primary_muscles = ARRAY['LEGS', 'GLUTES']::TEXT[], contraindications = ARRAY['IMPACT', 'KNEE']::TEXT[] WHERE name = 'Afundo com Salto e Halteres';

-- Afundo no Bosu com Elevação de Joelho
--   modality: (vazio) -> STRENGTH
--   secondary_muscles: CORE -> (vazio)
UPDATE exercises SET modality = 'STRENGTH', secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Afundo no Bosu com Elevação de Joelho';

-- Afundo no Cabo
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> LUNGE
--   primary_muscles: LEGS -> LEGS, GLUTES
--   contraindications: (vazio) -> KNEE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'LUNGE', primary_muscles = ARRAY['LEGS', 'GLUTES']::TEXT[], contraindications = ARRAY['KNEE']::TEXT[] WHERE name = 'Afundo no Cabo';

-- Afundo no Cabo com Braços Estendidos
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> LUNGE
--   primary_muscles: LEGS -> LEGS, GLUTES
--   contraindications: (vazio) -> KNEE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'LUNGE', primary_muscles = ARRAY['LEGS', 'GLUTES']::TEXT[], contraindications = ARRAY['KNEE']::TEXT[] WHERE name = 'Afundo no Cabo com Braços Estendidos';

-- Afundo no Landmine
--   secondary_muscles: CORE -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Afundo no Landmine';

-- Afundo no Smith
--   equipment: MACHINE -> SMITH
--   secondary_muscles: CORE -> (vazio)
--   contraindications: KNEE -> KNEE, LUMBAR
UPDATE exercises SET equipment = 'SMITH', secondary_muscles = ARRAY[]::TEXT[], contraindications = ARRAY['KNEE', 'LUMBAR']::TEXT[] WHERE name = 'Afundo no Smith';

-- Agachamento Apoiado na Cadeira Abdutora
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> SQUAT
--   is_compound: (vazio) -> sim
--   equipment: (vazio) -> MACHINE
--   primary_muscles: GLUTES -> GLUTES, LEGS
--   unilateral: (vazio) -> não
--   prescription_type: (vazio) -> REPS
--   level: (vazio) -> BEGINNER
--   contraindications: (vazio) -> KNEE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'SQUAT', is_compound = true, equipment = 'MACHINE', primary_muscles = ARRAY['GLUTES', 'LEGS']::TEXT[], unilateral = false, prescription_type = 'REPS', level = 'BEGINNER', contraindications = ARRAY['KNEE']::TEXT[] WHERE name = 'Agachamento Apoiado na Cadeira Abdutora';

-- Agachamento Búlgaro com Barra
--   secondary_muscles: CORE -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Agachamento Búlgaro com Barra';

-- Agachamento Búlgaro com Halteres
--   secondary_muscles: CORE -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Agachamento Búlgaro com Halteres';

-- Agachamento Búlgaro com Peso Corporal
--   secondary_muscles: CORE -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Agachamento Búlgaro com Peso Corporal';

-- Agachamento Búlgaro com Salto
--   secondary_muscles: CORE -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Agachamento Búlgaro com Salto';

-- Agachamento Camarão
--   modality: (vazio) -> STRENGTH
--   secondary_muscles: CORE -> (vazio)
UPDATE exercises SET modality = 'STRENGTH', secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Agachamento Camarão';

-- Agachamento Cossaco
--   modality: (vazio) -> STRENGTH
--   movement_pattern: SQUAT -> LUNGE
--   secondary_muscles: CORE -> (vazio)
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'LUNGE', secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Agachamento Cossaco';

-- Agachamento Frontal com Barra
--   secondary_muscles: CORE -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Agachamento Frontal com Barra';

-- Agachamento Frontal com Barra no Banco (Box Squat)
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> SQUAT
--   primary_muscles: LEGS -> LEGS, GLUTES
--   contraindications: (vazio) -> LUMBAR, KNEE, WRIST
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'SQUAT', primary_muscles = ARRAY['LEGS', 'GLUTES']::TEXT[], contraindications = ARRAY['LUMBAR', 'KNEE', 'WRIST']::TEXT[] WHERE name = 'Agachamento Frontal com Barra no Banco (Box Squat)';

-- Agachamento Frontal com Barra no Rack
--   secondary_muscles: CORE -> (vazio)
--   contraindications: KNEE -> LUMBAR, KNEE, WRIST
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[], contraindications = ARRAY['LUMBAR', 'KNEE', 'WRIST']::TEXT[] WHERE name = 'Agachamento Frontal com Barra no Rack';

-- Agachamento Frontal com Kettlebells
--   modality: (vazio) -> STRENGTH
--   secondary_muscles: CORE, BACK -> (vazio)
UPDATE exercises SET modality = 'STRENGTH', secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Agachamento Frontal com Kettlebells';

-- Agachamento Frontal na Polia
--   secondary_muscles: CORE -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Agachamento Frontal na Polia';

-- Agachamento Goblet com Halter
--   secondary_muscles: CORE -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Agachamento Goblet com Halter';

-- Agachamento Goblet com Kettlebell
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> SQUAT
--   primary_muscles: LEGS -> LEGS, GLUTES
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> KNEE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'SQUAT', primary_muscles = ARRAY['LEGS', 'GLUTES']::TEXT[], level = 'BEGINNER', contraindications = ARRAY['KNEE']::TEXT[] WHERE name = 'Agachamento Goblet com Kettlebell';

-- Agachamento Goblet com Kettlebell e Miniband
--   modality: (vazio) -> STRENGTH
--   secondary_muscles: CORE -> (vazio)
UPDATE exercises SET modality = 'STRENGTH', secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Agachamento Goblet com Kettlebell e Miniband';

-- Agachamento Hack Invertido
--   secondary_muscles: CORE -> (vazio)
--   contraindications: KNEE -> KNEE, LUMBAR
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[], contraindications = ARRAY['KNEE', 'LUMBAR']::TEXT[] WHERE name = 'Agachamento Hack Invertido';

-- Agachamento Hack com Barra
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> SQUAT
--   primary_muscles: LEGS -> LEGS, GLUTES
--   level: BEGINNER -> INTERMEDIATE
--   contraindications: (vazio) -> LUMBAR, KNEE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'SQUAT', primary_muscles = ARRAY['LEGS', 'GLUTES']::TEXT[], level = 'INTERMEDIATE', contraindications = ARRAY['LUMBAR', 'KNEE']::TEXT[] WHERE name = 'Agachamento Hack com Barra';

-- Agachamento Havaiano
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> SQUAT
--   primary_muscles: LEGS -> LEGS, GLUTES
--   contraindications: (vazio) -> KNEE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'SQUAT', primary_muscles = ARRAY['LEGS', 'GLUTES']::TEXT[], contraindications = ARRAY['KNEE']::TEXT[] WHERE name = 'Agachamento Havaiano';

-- Agachamento Isométrico com Elevação de Panturrilhas
--   secondary_muscles: CORE -> (vazio)
--   contraindications: (vazio) -> KNEE
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[], contraindications = ARRAY['KNEE']::TEXT[] WHERE name = 'Agachamento Isométrico com Elevação de Panturrilhas';

-- Agachamento Isométrico na Parede (Wall Sit)
--   modality: (vazio) -> STRENGTH
--   secondary_muscles: CORE -> (vazio)
UPDATE exercises SET modality = 'STRENGTH', secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Agachamento Isométrico na Parede (Wall Sit)';

-- Agachamento Isométrico na Parede com Inclinação de Tronco
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> SQUAT
--   primary_muscles: LEGS -> LEGS, GLUTES
--   secondary_muscles: (vazio) -> CORE
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> KNEE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'SQUAT', primary_muscles = ARRAY['LEGS', 'GLUTES']::TEXT[], secondary_muscles = ARRAY['CORE']::TEXT[], level = 'BEGINNER', contraindications = ARRAY['KNEE']::TEXT[] WHERE name = 'Agachamento Isométrico na Parede com Inclinação de Tronco';

-- Agachamento Jefferson
--   modality: (vazio) -> STRENGTH
--   secondary_pattern: HINGE -> (vazio)
--   secondary_muscles: CORE, BACK -> (vazio)
UPDATE exercises SET modality = 'STRENGTH', secondary_pattern = NULL, secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Agachamento Jefferson';

-- Agachamento Livre com Barra
--   secondary_muscles: CORE -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Agachamento Livre com Barra';

-- Agachamento Livre com Peso Corporal
--   secondary_muscles: CORE -> (vazio)
--   contraindications: (vazio) -> KNEE
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[], contraindications = ARRAY['KNEE']::TEXT[] WHERE name = 'Agachamento Livre com Peso Corporal';

-- Agachamento Patinador (Skater Squat)
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> SQUAT
--   primary_muscles: LEGS -> LEGS, GLUTES
--   contraindications: (vazio) -> KNEE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'SQUAT', primary_muscles = ARRAY['LEGS', 'GLUTES']::TEXT[], contraindications = ARRAY['KNEE']::TEXT[] WHERE name = 'Agachamento Patinador (Skater Squat)';

-- Agachamento Pistola
--   modality: (vazio) -> STRENGTH
--   secondary_muscles: CORE -> (vazio)
UPDATE exercises SET modality = 'STRENGTH', secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Agachamento Pistola';

-- Agachamento Pistola Assistido
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> SQUAT
--   primary_muscles: LEGS -> LEGS, GLUTES
--   level: ADVANCED -> INTERMEDIATE
--   contraindications: (vazio) -> KNEE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'SQUAT', primary_muscles = ARRAY['LEGS', 'GLUTES']::TEXT[], level = 'INTERMEDIATE', contraindications = ARRAY['KNEE']::TEXT[] WHERE name = 'Agachamento Pistola Assistido';

-- Agachamento Pistola com Halteres
--   modality: (vazio) -> STRENGTH
--   secondary_muscles: CORE -> (vazio)
UPDATE exercises SET modality = 'STRENGTH', secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Agachamento Pistola com Halteres';

-- Agachamento Pistola com Kettlebell
--   modality: (vazio) -> STRENGTH
--   secondary_muscles: CORE -> (vazio)
UPDATE exercises SET modality = 'STRENGTH', secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Agachamento Pistola com Kettlebell';

-- Agachamento Pistola com TRX
--   modality: (vazio) -> STRENGTH
--   secondary_muscles: CORE -> (vazio)
UPDATE exercises SET modality = 'STRENGTH', secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Agachamento Pistola com TRX';

-- Agachamento Pistola em Cima da Caixa
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> SQUAT
--   primary_muscles: LEGS -> LEGS, GLUTES
--   contraindications: (vazio) -> KNEE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'SQUAT', primary_muscles = ARRAY['LEGS', 'GLUTES']::TEXT[], contraindications = ARRAY['KNEE']::TEXT[] WHERE name = 'Agachamento Pistola em Cima da Caixa';

-- Agachamento Pistola na Caixa
--   modality: (vazio) -> STRENGTH
--   secondary_muscles: CORE -> (vazio)
UPDATE exercises SET modality = 'STRENGTH', secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Agachamento Pistola na Caixa';

-- Agachamento Plié com Halter
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> SQUAT
--   primary_muscles: LEGS -> LEGS, GLUTES
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> KNEE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'SQUAT', primary_muscles = ARRAY['LEGS', 'GLUTES']::TEXT[], level = 'BEGINNER', contraindications = ARRAY['KNEE']::TEXT[] WHERE name = 'Agachamento Plié com Halter';

-- Agachamento Sissy
--   modality: (vazio) -> STRENGTH
--   movement_pattern: SQUAT -> KNEE_EXTENSION
--   is_compound: sim -> não
--   secondary_muscles: CORE -> (vazio)
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'KNEE_EXTENSION', is_compound = false, secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Agachamento Sissy';

-- Agachamento Sissy Ajoelhado
--   modality: (vazio) -> STRENGTH
--   movement_pattern: SQUAT -> KNEE_EXTENSION
--   is_compound: sim -> não
--   secondary_muscles: CORE -> (vazio)
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'KNEE_EXTENSION', is_compound = false, secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Agachamento Sissy Ajoelhado';

-- Agachamento Sumô com Halter
--   secondary_muscles: CORE -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Agachamento Sumô com Halter';

-- Agachamento Sumô com Peso Corporal
--   secondary_muscles: CORE -> (vazio)
--   contraindications: (vazio) -> KNEE
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[], contraindications = ARRAY['KNEE']::TEXT[] WHERE name = 'Agachamento Sumô com Peso Corporal';

-- Agachamento Zercher
--   modality: (vazio) -> STRENGTH
--   secondary_muscles: CORE, BACK -> (vazio)
UPDATE exercises SET modality = 'STRENGTH', secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Agachamento Zercher';

-- Agachamento com Barra acima da Cabeça
--   modality: (vazio) -> STRENGTH
--   secondary_muscles: BACK, SHOULDERS, CORE -> (vazio)
UPDATE exercises SET modality = 'STRENGTH', secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Agachamento com Barra acima da Cabeça';

-- Agachamento com Chute Lateral e Toque no Calcanhar
--   movement_pattern: SQUAT -> LUNGE
--   secondary_muscles: CORE -> (vazio)
--   contraindications: (vazio) -> KNEE
UPDATE exercises SET movement_pattern = 'LUNGE', secondary_muscles = ARRAY[]::TEXT[], contraindications = ARRAY['KNEE']::TEXT[] WHERE name = 'Agachamento com Chute Lateral e Toque no Calcanhar';

-- Agachamento com Desenvolvimento com Kettlebells
--   modality: (vazio) -> STRENGTH
--   secondary_muscles: GLUTES, CORE -> GLUTES, TRICEPS
--   contraindications: SHOULDER, KNEE -> SHOULDER, LUMBAR, KNEE
UPDATE exercises SET modality = 'STRENGTH', secondary_muscles = ARRAY['GLUTES', 'TRICEPS']::TEXT[], contraindications = ARRAY['SHOULDER', 'LUMBAR', 'KNEE']::TEXT[] WHERE name = 'Agachamento com Desenvolvimento com Kettlebells';

-- Agachamento com Desenvolvimento no Landmine
--   modality: (vazio) -> STRENGTH
--   equipment: BARBELL -> LANDMINE
--   secondary_muscles: GLUTES, CORE, BACK -> GLUTES, TRICEPS
--   level: ADVANCED -> INTERMEDIATE
--   contraindications: LUMBAR, SHOULDER -> SHOULDER, KNEE
UPDATE exercises SET modality = 'STRENGTH', equipment = 'LANDMINE', secondary_muscles = ARRAY['GLUTES', 'TRICEPS']::TEXT[], level = 'INTERMEDIATE', contraindications = ARRAY['SHOULDER', 'KNEE']::TEXT[] WHERE name = 'Agachamento com Desenvolvimento no Landmine';

-- Agachamento com Elevação de Joelho
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> SQUAT
--   primary_muscles: LEGS -> LEGS, GLUTES
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> KNEE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'SQUAT', primary_muscles = ARRAY['LEGS', 'GLUTES']::TEXT[], level = 'BEGINNER', contraindications = ARRAY['KNEE']::TEXT[] WHERE name = 'Agachamento com Elevação de Joelho';

-- Agachamento com Faixa Elástica acima da Cabeça
--   secondary_muscles: CORE, SHOULDERS -> (vazio)
--   contraindications: SHOULDER -> SHOULDER, KNEE
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[], contraindications = ARRAY['SHOULDER', 'KNEE']::TEXT[] WHERE name = 'Agachamento com Faixa Elástica acima da Cabeça';

-- Agachamento com Gymstick
--   modality: (vazio) -> STRENGTH
--   secondary_muscles: CORE -> (vazio)
--   contraindications: (vazio) -> KNEE
UPDATE exercises SET modality = 'STRENGTH', secondary_muscles = ARRAY[]::TEXT[], contraindications = ARRAY['KNEE']::TEXT[] WHERE name = 'Agachamento com Gymstick';

-- Agachamento com Salto
--   secondary_muscles: CORE -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Agachamento com Salto';

-- Agachamento com Salto e Barra
--   secondary_muscles: CORE -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Agachamento com Salto e Barra';

-- Agachamento com Salto e Halteres
--   secondary_muscles: CORE, SHOULDERS -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Agachamento com Salto e Halteres';

-- Agachamento com Salto na Barra Hexagonal
--   secondary_muscles: CORE -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Agachamento com Salto na Barra Hexagonal';

-- Agachamento na Parede com Bola Suíça
--   modality: (vazio) -> STRENGTH
--   secondary_muscles: CORE -> (vazio)
--   contraindications: (vazio) -> KNEE
UPDATE exercises SET modality = 'STRENGTH', secondary_muscles = ARRAY[]::TEXT[], contraindications = ARRAY['KNEE']::TEXT[] WHERE name = 'Agachamento na Parede com Bola Suíça';

-- Agachamento no Banco com Halteres
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> SQUAT
--   primary_muscles: LEGS -> LEGS, GLUTES
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> KNEE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'SQUAT', primary_muscles = ARRAY['LEGS', 'GLUTES']::TEXT[], level = 'BEGINNER', contraindications = ARRAY['KNEE']::TEXT[] WHERE name = 'Agachamento no Banco com Halteres';

-- Agachamento no Banco com Peso Corporal
--   secondary_muscles: CORE -> (vazio)
--   contraindications: (vazio) -> KNEE
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[], contraindications = ARRAY['KNEE']::TEXT[] WHERE name = 'Agachamento no Banco com Peso Corporal';

-- Agachamento no Belt Squat
--   secondary_muscles: CORE -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Agachamento no Belt Squat';

-- Agachamento no Hack
--   secondary_muscles: CORE -> (vazio)
--   contraindications: KNEE -> KNEE, LUMBAR
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[], contraindications = ARRAY['KNEE', 'LUMBAR']::TEXT[] WHERE name = 'Agachamento no Hack';

-- Agachamento no Landmine
--   secondary_muscles: CORE -> (vazio)
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[], level = 'BEGINNER' WHERE name = 'Agachamento no Landmine';

-- Agachamento no Rack com Pinos (Pin Squat)
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> SQUAT
--   primary_muscles: LEGS -> LEGS, GLUTES
--   contraindications: (vazio) -> LUMBAR, KNEE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'SQUAT', primary_muscles = ARRAY['LEGS', 'GLUTES']::TEXT[], contraindications = ARRAY['LUMBAR', 'KNEE']::TEXT[] WHERE name = 'Agachamento no Rack com Pinos (Pin Squat)';

-- Agachamento no Smith
--   equipment: MACHINE -> SMITH
--   secondary_muscles: CORE -> (vazio)
--   contraindications: KNEE -> KNEE, LUMBAR
UPDATE exercises SET equipment = 'SMITH', secondary_muscles = ARRAY[]::TEXT[], contraindications = ARRAY['KNEE', 'LUMBAR']::TEXT[] WHERE name = 'Agachamento no Smith';

-- Air Bike
--   secondary_muscles: CORE, BACK, SHOULDERS -> SHOULDERS, CHEST, BACK
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS', 'CHEST', 'BACK']::TEXT[] WHERE name = 'Air Bike';

-- Alongamento Abraço com Tapinhas nas Costas
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> NONE
--   secondary_muscles: (vazio) -> CHEST
--   prescription_type: TIME -> REPS
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'NONE', secondary_muscles = ARRAY['CHEST']::TEXT[], prescription_type = 'REPS' WHERE name = 'Alongamento Abraço com Tapinhas nas Costas';

-- Alongamento Borboleta
--   secondary_muscles: GLUTES -> (vazio)
--   contraindications: (vazio) -> LUMBAR
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[], contraindications = ARRAY['LUMBAR']::TEXT[] WHERE name = 'Alongamento Borboleta';

-- Alongamento Dinâmico de Ombro com Braço Cruzado
--   movement_pattern: STRETCH -> NONE
--   primary_muscles: TRICEPS -> SHOULDERS
--   secondary_muscles: SHOULDERS -> (vazio)
--   prescription_type: TIME -> REPS
--   contraindications: SHOULDER -> (vazio)
UPDATE exercises SET movement_pattern = 'NONE', primary_muscles = ARRAY['SHOULDERS']::TEXT[], secondary_muscles = ARRAY[]::TEXT[], prescription_type = 'REPS', contraindications = ARRAY[]::TEXT[] WHERE name = 'Alongamento Dinâmico de Ombro com Braço Cruzado';

-- Alongamento Dinâmico de Peitoral
--   movement_pattern: STRETCH -> NONE
--   contraindications: SHOULDER -> (vazio)
UPDATE exercises SET movement_pattern = 'NONE', contraindications = ARRAY[]::TEXT[] WHERE name = 'Alongamento Dinâmico de Peitoral';

-- Alongamento Duplo de Pernas (Pilates)
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   is_compound: não -> sim
--   primary_muscles: LEGS -> CORE
--   secondary_muscles: (vazio) -> LEGS
--   prescription_type: TIME -> REPS
--   level: BEGINNER -> INTERMEDIATE
--   contraindications: (vazio) -> LUMBAR
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', is_compound = true, primary_muscles = ARRAY['CORE']::TEXT[], secondary_muscles = ARRAY['LEGS']::TEXT[], prescription_type = 'REPS', level = 'INTERMEDIATE', contraindications = ARRAY['LUMBAR']::TEXT[] WHERE name = 'Alongamento Duplo de Pernas (Pilates)';

-- Alongamento Reverso Assistido (Peitoral e Ombro)
--   equipment: BAND -> BODYWEIGHT
--   secondary_muscles: BACK -> (vazio)
--   contraindications: SHOULDER -> SHOULDER, KNEE
UPDATE exercises SET equipment = 'BODYWEIGHT', secondary_muscles = ARRAY[]::TEXT[], contraindications = ARRAY['SHOULDER', 'KNEE']::TEXT[] WHERE name = 'Alongamento Reverso Assistido (Peitoral e Ombro)';

-- Alongamento Reverso de Ombro em Pé
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> STRETCH
--   secondary_muscles: (vazio) -> CHEST
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'STRETCH', secondary_muscles = ARRAY['CHEST']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Alongamento Reverso de Ombro em Pé';

-- Alongamento Reverso de Punho
--   contraindications: WRIST -> (vazio)
UPDATE exercises SET contraindications = ARRAY[]::TEXT[] WHERE name = 'Alongamento Reverso de Punho';

-- Alongamento com Bastão na Posição de Front Rack
--   equipment: PVC_PIPE -> BODYWEIGHT
--   primary_muscles: SHOULDERS -> BACK, TRICEPS
--   secondary_muscles: CHEST, BACK -> FOREARMS
--   unilateral: não -> sim
UPDATE exercises SET equipment = 'BODYWEIGHT', primary_muscles = ARRAY['BACK', 'TRICEPS']::TEXT[], secondary_muscles = ARRAY['FOREARMS']::TEXT[], unilateral = true WHERE name = 'Alongamento com Bastão na Posição de Front Rack';

-- Alongamento da Esfinge
--   secondary_muscles: CHEST, BACK -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Alongamento da Esfinge';

-- Alongamento da Parte Superior das Costas
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> STRETCH
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'STRETCH' WHERE name = 'Alongamento da Parte Superior das Costas';

-- Alongamento de Adutores Ajoelhado com Perna Estendida
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> STRETCH
--   unilateral: não -> sim
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'STRETCH', unilateral = true WHERE name = 'Alongamento de Adutores Ajoelhado com Perna Estendida';

-- Alongamento de Adutores Sentado com Pernas Afastadas
--   secondary_muscles: GLUTES -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Alongamento de Adutores Sentado com Pernas Afastadas';

-- Alongamento de Adutores em Afundo Lateral
--   contraindications: (vazio) -> KNEE
UPDATE exercises SET contraindications = ARRAY['KNEE']::TEXT[] WHERE name = 'Alongamento de Adutores em Afundo Lateral';

-- Alongamento de Adutores em Pé com Flexão de Tronco
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> STRETCH
--   contraindications: (vazio) -> LUMBAR
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'STRETCH', contraindications = ARRAY['LUMBAR']::TEXT[] WHERE name = 'Alongamento de Adutores em Pé com Flexão de Tronco';

-- Alongamento de Adutores em Pé com Pernas Afastadas
--   secondary_muscles: GLUTES -> (vazio)
--   unilateral: sim -> não
--   contraindications: (vazio) -> KNEE
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[], unilateral = false, contraindications = ARRAY['KNEE']::TEXT[] WHERE name = 'Alongamento de Adutores em Pé com Pernas Afastadas';

-- Alongamento de Costas no Rolo de Espuma
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> NONE
--   equipment: BODYWEIGHT -> FOAM_ROLLER
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'NONE', equipment = 'FOAM_ROLLER' WHERE name = 'Alongamento de Costas no Rolo de Espuma';

-- Alongamento de Dorsal e Peitoral Apoiado no Banco
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> STRETCH
--   primary_muscles: CHEST -> BACK, CHEST
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'STRETCH', primary_muscles = ARRAY['BACK', 'CHEST']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Alongamento de Dorsal e Peitoral Apoiado no Banco';

-- Alongamento de Flexores de Punho em Quatro Apoios
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> STRETCH
--   contraindications: (vazio) -> WRIST
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'STRETCH', contraindications = ARRAY['WRIST']::TEXT[] WHERE name = 'Alongamento de Flexores de Punho em Quatro Apoios';

-- Alongamento de Flexores do Quadril Ajoelhado
--   secondary_muscles: CORE, GLUTES -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Alongamento de Flexores do Quadril Ajoelhado';

-- Alongamento de Glúteos Deitado
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> STRETCH
--   unilateral: não -> sim
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'STRETCH', unilateral = true WHERE name = 'Alongamento de Glúteos Deitado';

-- Alongamento de Isquiotibiais Deitado
--   equipment: BAND -> BODYWEIGHT
UPDATE exercises SET equipment = 'BODYWEIGHT' WHERE name = 'Alongamento de Isquiotibiais Deitado';

-- Alongamento de Isquiotibiais Sentado com Pernas Afastadas
--   unilateral: não -> sim
UPDATE exercises SET unilateral = true WHERE name = 'Alongamento de Isquiotibiais Sentado com Pernas Afastadas';

-- Alongamento de Isquiotibiais em Pé
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> STRETCH
--   primary_muscles: CORE -> LEGS
--   contraindications: (vazio) -> LUMBAR
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'STRETCH', primary_muscles = ARRAY['LEGS']::TEXT[], contraindications = ARRAY['LUMBAR']::TEXT[] WHERE name = 'Alongamento de Isquiotibiais em Pé';

-- Alongamento de Isquiotibiais em Pé com Pernas Cruzadas
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> STRETCH
--   unilateral: não -> sim
--   contraindications: (vazio) -> LUMBAR
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'STRETCH', unilateral = true, contraindications = ARRAY['LUMBAR']::TEXT[] WHERE name = 'Alongamento de Isquiotibiais em Pé com Pernas Cruzadas';

-- Alongamento de Ombro com Toalha
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> STRETCH
--   secondary_muscles: (vazio) -> TRICEPS, BACK
--   unilateral: não -> sim
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'STRETCH', secondary_muscles = ARRAY['TRICEPS', 'BACK']::TEXT[], unilateral = true, contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Alongamento de Ombro com Toalha';

-- Alongamento de Ombros por trás das Costas
--   primary_muscles: SHOULDERS -> TRICEPS
--   secondary_muscles: CHEST -> BACK
UPDATE exercises SET primary_muscles = ARRAY['TRICEPS']::TEXT[], secondary_muscles = ARRAY['BACK']::TEXT[] WHERE name = 'Alongamento de Ombros por trás das Costas';

-- Alongamento de Panturrilha Deitado com Faixa
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> STRETCH
--   equipment: ROPE -> BODYWEIGHT
--   unilateral: não -> sim
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'STRETCH', equipment = 'BODYWEIGHT', unilateral = true WHERE name = 'Alongamento de Panturrilha Deitado com Faixa';

-- Alongamento de Panturrilha Sentado com Perna Estendida
--   contraindications: (vazio) -> LUMBAR
UPDATE exercises SET contraindications = ARRAY['LUMBAR']::TEXT[] WHERE name = 'Alongamento de Panturrilha Sentado com Perna Estendida';

-- Alongamento de Panturrilha Unilateral no Degrau
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> STRETCH
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'STRETCH' WHERE name = 'Alongamento de Panturrilha Unilateral no Degrau';

-- Alongamento de Panturrilha com Antebraços na Parede
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> STRETCH
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'STRETCH' WHERE name = 'Alongamento de Panturrilha com Antebraços na Parede';

-- Alongamento de Panturrilha em Passada Larga
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> STRETCH
--   unilateral: não -> sim
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'STRETCH', unilateral = true WHERE name = 'Alongamento de Panturrilha em Passada Larga';

-- Alongamento de Panturrilha no Degrau
--   equipment: BODYWEIGHT -> STEP
--   unilateral: não -> sim
UPDATE exercises SET equipment = 'STEP', unilateral = true WHERE name = 'Alongamento de Panturrilha no Degrau';

-- Alongamento de Peitoral Unilateral na Parede
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> STRETCH
--   secondary_muscles: (vazio) -> SHOULDERS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'STRETCH', secondary_muscles = ARRAY['SHOULDERS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Alongamento de Peitoral Unilateral na Parede';

-- Alongamento de Peitoral acima da Cabeça
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> STRETCH
--   unilateral: não -> sim
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'STRETCH', unilateral = true, contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Alongamento de Peitoral acima da Cabeça';

-- Alongamento de Peitoral e Ombro Anterior com Bastão
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> STRETCH
--   primary_muscles: SHOULDERS -> CHEST, SHOULDERS
--   unilateral: não -> sim
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'STRETCH', primary_muscles = ARRAY['CHEST', 'SHOULDERS']::TEXT[], unilateral = true, contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Alongamento de Peitoral e Ombro Anterior com Bastão';

-- Alongamento de Peitoral na Quina da Parede
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> STRETCH
--   primary_muscles: CORE -> CHEST
--   secondary_muscles: (vazio) -> SHOULDERS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'STRETCH', primary_muscles = ARRAY['CHEST']::TEXT[], secondary_muscles = ARRAY['SHOULDERS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Alongamento de Peitoral na Quina da Parede';

-- Alongamento de Peitoral no Rolo de Espuma
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> NONE
--   equipment: BODYWEIGHT -> FOAM_ROLLER
--   unilateral: não -> sim
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'NONE', equipment = 'FOAM_ROLLER', unilateral = true WHERE name = 'Alongamento de Peitoral no Rolo de Espuma';

-- Alongamento de Quadril 90-90
--   movement_pattern: STRETCH -> NONE
--   unilateral: sim -> não
--   prescription_type: TIME -> REPS
UPDATE exercises SET movement_pattern = 'NONE', unilateral = false, prescription_type = 'REPS' WHERE name = 'Alongamento de Quadril 90-90';

-- Alongamento de Quadríceps Ajoelhado
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> STRETCH
--   primary_muscles: CORE -> LEGS
--   unilateral: não -> sim
--   contraindications: (vazio) -> KNEE
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'STRETCH', primary_muscles = ARRAY['LEGS']::TEXT[], unilateral = true, contraindications = ARRAY['KNEE']::TEXT[] WHERE name = 'Alongamento de Quadríceps Ajoelhado';

-- Alongamento de Quadríceps em Quatro Apoios
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> STRETCH
--   primary_muscles: CORE -> LEGS
--   unilateral: não -> sim
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'STRETCH', primary_muscles = ARRAY['LEGS']::TEXT[], unilateral = true WHERE name = 'Alongamento de Quadríceps em Quatro Apoios';

-- Alongamento de Rotação da Coluna em Pé
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> STRETCH
--   secondary_muscles: (vazio) -> BACK
--   unilateral: não -> sim
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'STRETCH', secondary_muscles = ARRAY['BACK']::TEXT[], unilateral = true WHERE name = 'Alongamento de Rotação da Coluna em Pé';

-- Alongamento do Manguito Rotador
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> STRETCH
--   unilateral: não -> sim
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'STRETCH', unilateral = true, contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Alongamento do Manguito Rotador';

-- Alongamento do Piriforme Sentado
--   contraindications: (vazio) -> LUMBAR
UPDATE exercises SET contraindications = ARRAY['LUMBAR']::TEXT[] WHERE name = 'Alongamento do Piriforme Sentado';

-- Alongamento do Piriforme Sentado no Banco
--   contraindications: (vazio) -> LUMBAR
UPDATE exercises SET contraindications = ARRAY['LUMBAR']::TEXT[] WHERE name = 'Alongamento do Piriforme Sentado no Banco';

-- Alongamento do Tendão de Aquiles em Pé
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> STRETCH
--   primary_muscles: CORE -> LEGS
--   unilateral: não -> sim
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'STRETCH', primary_muscles = ARRAY['LEGS']::TEXT[], unilateral = true WHERE name = 'Alongamento do Tendão de Aquiles em Pé';

-- Alongamento do Tibial Posterior com Faixa
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> STRETCH
--   unilateral: não -> sim
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'STRETCH', unilateral = true WHERE name = 'Alongamento do Tibial Posterior com Faixa';

-- Alongamento dos Extensores dos Dedos dos Pés
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> STRETCH
--   primary_muscles: CORE -> LEGS
--   unilateral: não -> sim
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'STRETCH', primary_muscles = ARRAY['LEGS']::TEXT[], unilateral = true WHERE name = 'Alongamento dos Extensores dos Dedos dos Pés';

-- Alongamento dos Flexores dos Dedos dos Pés em Pé
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> STRETCH
--   primary_muscles: CORE -> LEGS
--   unilateral: não -> sim
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'STRETCH', primary_muscles = ARRAY['LEGS']::TEXT[], unilateral = true WHERE name = 'Alongamento dos Flexores dos Dedos dos Pés em Pé';

-- Andar de Pato
--   modality: (vazio) -> STRENGTH
--   secondary_muscles: CORE -> (vazio)
UPDATE exercises SET modality = 'STRENGTH', secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Andar de Pato';

-- Arranco Unilateral com Halter
--   modality: (vazio) -> STRENGTH
--   primary_muscles: LEGS, SHOULDERS -> GLUTES, LEGS
--   secondary_muscles: BACK, CHEST, CORE, GLUTES -> SHOULDERS, BACK
--   contraindications: LUMBAR, SHOULDER -> LUMBAR, SHOULDER, KNEE
UPDATE exercises SET modality = 'STRENGTH', primary_muscles = ARRAY['GLUTES', 'LEGS']::TEXT[], secondary_muscles = ARRAY['SHOULDERS', 'BACK']::TEXT[], contraindications = ARRAY['LUMBAR', 'SHOULDER', 'KNEE']::TEXT[] WHERE name = 'Arranco Unilateral com Halter';

-- Arranco Unilateral com Kettlebell
--   modality: (vazio) -> STRENGTH
--   secondary_muscles: BACK, SHOULDERS, CORE -> SHOULDERS, BACK
--   level: ADVANCED -> INTERMEDIATE
UPDATE exercises SET modality = 'STRENGTH', secondary_muscles = ARRAY['SHOULDERS', 'BACK']::TEXT[], level = 'INTERMEDIATE' WHERE name = 'Arranco Unilateral com Kettlebell';

-- Arranco com Barra
--   modality: (vazio) -> STRENGTH
--   primary_muscles: LEGS, BACK -> GLUTES, LEGS
--   secondary_muscles: GLUTES, SHOULDERS, CORE -> SHOULDERS, BACK
UPDATE exercises SET modality = 'STRENGTH', primary_muscles = ARRAY['GLUTES', 'LEGS']::TEXT[], secondary_muscles = ARRAY['SHOULDERS', 'BACK']::TEXT[] WHERE name = 'Arranco com Barra';

-- Arranco com Kettlebell em Afundo
--   modality: (vazio) -> STRENGTH
--   primary_muscles: LEGS, SHOULDERS -> GLUTES, LEGS
--   secondary_muscles: GLUTES, BACK, CORE -> SHOULDERS, BACK
UPDATE exercises SET modality = 'STRENGTH', primary_muscles = ARRAY['GLUTES', 'LEGS']::TEXT[], secondary_muscles = ARRAY['SHOULDERS', 'BACK']::TEXT[] WHERE name = 'Arranco com Kettlebell em Afundo';

-- Arranco de Potência
--   modality: (vazio) -> STRENGTH
--   primary_muscles: LEGS, BACK -> GLUTES, LEGS
--   secondary_muscles: GLUTES, SHOULDERS, CORE -> SHOULDERS, BACK
UPDATE exercises SET modality = 'STRENGTH', primary_muscles = ARRAY['GLUTES', 'LEGS']::TEXT[], secondary_muscles = ARRAY['SHOULDERS', 'BACK']::TEXT[] WHERE name = 'Arranco de Potência';

-- Arremesso Rotacional de Bola Medicinal
--   modality: (vazio) -> PLYOMETRIC
--   movement_pattern: (vazio) -> ROTATION
--   secondary_muscles: (vazio) -> GLUTES, SHOULDERS
--   unilateral: não -> sim
--   contraindications: (vazio) -> LUMBAR
UPDATE exercises SET modality = 'PLYOMETRIC', movement_pattern = 'ROTATION', secondary_muscles = ARRAY['GLUTES', 'SHOULDERS']::TEXT[], unilateral = true, contraindications = ARRAY['LUMBAR']::TEXT[] WHERE name = 'Arremesso Rotacional de Bola Medicinal';

-- Arremesso com Barra (Jerk)
--   modality: (vazio) -> STRENGTH
--   movement_pattern: HINGE -> VERTICAL_PUSH
--   secondary_pattern: VERTICAL_PUSH -> SQUAT
--   secondary_muscles: BACK, GLUTES, CORE -> TRICEPS, GLUTES
--   contraindications: LUMBAR, SHOULDER, KNEE -> LUMBAR, SHOULDER, WRIST
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'VERTICAL_PUSH', secondary_pattern = 'SQUAT', secondary_muscles = ARRAY['TRICEPS', 'GLUTES']::TEXT[], contraindications = ARRAY['LUMBAR', 'SHOULDER', 'WRIST']::TEXT[] WHERE name = 'Arremesso com Barra (Jerk)';

-- Arremesso de Bola Medicinal Deitado
--   modality: (vazio) -> PLYOMETRIC
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   primary_muscles: CORE -> CHEST
--   secondary_muscles: (vazio) -> SHOULDERS, TRICEPS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'PLYOMETRIC', movement_pattern = 'HORIZONTAL_PUSH', primary_muscles = ARRAY['CHEST']::TEXT[], secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Arremesso de Bola Medicinal Deitado';

-- Arremesso de Bola Medicinal acima da Cabeça
--   movement_pattern: HORIZONTAL_PUSH -> VERTICAL_PULL
--   primary_muscles: CHEST, SHOULDERS -> BACK, CHEST
--   secondary_muscles: CORE, LEGS -> TRICEPS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET movement_pattern = 'VERTICAL_PULL', primary_muscles = ARRAY['BACK', 'CHEST']::TEXT[], secondary_muscles = ARRAY['TRICEPS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Arremesso de Bola Medicinal acima da Cabeça';

-- Arremesso de Bola de Reação
--   modality: (vazio) -> SKILL
--   movement_pattern: (vazio) -> NONE
--   equipment: MEDICINE_BALL -> BODYWEIGHT
--   primary_muscles: CORE -> SHOULDERS
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'SKILL', movement_pattern = 'NONE', equipment = 'BODYWEIGHT', primary_muscles = ARRAY['SHOULDERS']::TEXT[], level = 'BEGINNER' WHERE name = 'Arremesso de Bola de Reação';

-- Ativação Abdominal Deitado
--   contraindications: LUMBAR -> (vazio)
UPDATE exercises SET contraindications = ARRAY[]::TEXT[] WHERE name = 'Ativação Abdominal Deitado';

-- Avanço com Peso Corporal
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> LUNGE
--   primary_muscles: LEGS -> LEGS, GLUTES
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> KNEE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'LUNGE', primary_muscles = ARRAY['LEGS', 'GLUTES']::TEXT[], level = 'BEGINNER', contraindications = ARRAY['KNEE']::TEXT[] WHERE name = 'Avanço com Peso Corporal';

-- Back Lever
--   modality: (vazio) -> SKILL
--   primary_muscles: BACK, CORE -> BACK
--   secondary_muscles: SHOULDERS -> SHOULDERS, BICEPS
UPDATE exercises SET modality = 'SKILL', primary_muscles = ARRAY['BACK']::TEXT[], secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[] WHERE name = 'Back Lever';

-- Bandeira Humana
--   modality: (vazio) -> SKILL
--   movement_pattern: ROTATION -> NONE
--   primary_muscles: CORE, SHOULDERS -> SHOULDERS, BACK
--   secondary_muscles: BACK, GLUTES -> (vazio)
UPDATE exercises SET modality = 'SKILL', movement_pattern = 'NONE', primary_muscles = ARRAY['SHOULDERS', 'BACK']::TEXT[], secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Bandeira Humana';

-- Barra Fixa
--   secondary_muscles: SHOULDERS -> SHOULDERS, BICEPS
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[] WHERE name = 'Barra Fixa';

-- Barra Fixa Assistida com Faixa Elástica
--   secondary_muscles: SHOULDERS, CORE -> SHOULDERS, BICEPS
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[] WHERE name = 'Barra Fixa Assistida com Faixa Elástica';

-- Barra Fixa Assistida na Máquina
--   secondary_muscles: SHOULDERS -> SHOULDERS, BICEPS
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[] WHERE name = 'Barra Fixa Assistida na Máquina';

-- Barra Fixa Isométrica
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> VERTICAL_PULL
--   secondary_muscles: (vazio) -> SHOULDERS, BICEPS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'VERTICAL_PULL', secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Barra Fixa Isométrica';

-- Barra Fixa Pegada Aberta
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> VERTICAL_PULL
--   secondary_muscles: (vazio) -> SHOULDERS, BICEPS
--   level: ADVANCED -> INTERMEDIATE
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'VERTICAL_PULL', secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[], level = 'INTERMEDIATE', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Barra Fixa Pegada Aberta';

-- Barra Fixa Pegada Fechada
--   secondary_muscles: SHOULDERS, CORE -> SHOULDERS, BICEPS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Barra Fixa Pegada Fechada';

-- Barra Fixa Pegada Neutra
--   secondary_muscles: SHOULDERS, CORE -> SHOULDERS, BICEPS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Barra Fixa Pegada Neutra';

-- Barra Fixa Pegada Supinada (Chin-Up)
--   secondary_muscles: SHOULDERS, CORE -> SHOULDERS, BICEPS
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[] WHERE name = 'Barra Fixa Pegada Supinada (Chin-Up)';

-- Barra Fixa Supinada Assistida
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> VERTICAL_PULL
--   secondary_muscles: (vazio) -> SHOULDERS, BICEPS
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'VERTICAL_PULL', secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[], level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Barra Fixa Supinada Assistida';

-- Barra Fixa atrás da Nuca
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> VERTICAL_PULL
--   secondary_muscles: (vazio) -> SHOULDERS, BICEPS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'VERTICAL_PULL', secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Barra Fixa atrás da Nuca';

-- Barra Fixa com Braços Alternados
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> VERTICAL_PULL
--   secondary_muscles: (vazio) -> SHOULDERS, BICEPS
--   unilateral: sim -> não
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'VERTICAL_PULL', secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[], unilateral = false, contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Barra Fixa com Braços Alternados';

-- Barra Fixa com Halter entre os Pés
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> VERTICAL_PULL
--   equipment: CABLE -> DUMBBELL
--   secondary_muscles: (vazio) -> SHOULDERS, BICEPS
--   unilateral: sim -> não
--   level: INTERMEDIATE -> ADVANCED
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'VERTICAL_PULL', equipment = 'DUMBBELL', secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[], unilateral = false, level = 'ADVANCED', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Barra Fixa com Halter entre os Pés';

-- Barra Fixa com L-Sit
--   modality: (vazio) -> STRENGTH
--   primary_muscles: BACK, CORE -> BACK
--   secondary_muscles: SHOULDERS -> SHOULDERS, BICEPS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', primary_muscles = ARRAY['BACK']::TEXT[], secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Barra Fixa com L-Sit';

-- Barra Fixa com Peso
--   modality: (vazio) -> STRENGTH
--   secondary_muscles: SHOULDERS -> SHOULDERS, BICEPS
UPDATE exercises SET modality = 'STRENGTH', secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[] WHERE name = 'Barra Fixa com Peso';

-- Barra Fixa com Rotação
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> VERTICAL_PULL
--   secondary_pattern: (vazio) -> ROTATION
--   secondary_muscles: (vazio) -> SHOULDERS, BICEPS, CORE
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'VERTICAL_PULL', secondary_pattern = 'ROTATION', secondary_muscles = ARRAY['SHOULDERS', 'BICEPS', 'CORE']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Barra Fixa com Rotação';

-- Barra Fixa com Salto
--   modality: (vazio) -> STRENGTH
--   secondary_muscles: SHOULDERS -> SHOULDERS, BICEPS
--   contraindications: (vazio) -> SHOULDER, IMPACT
UPDATE exercises SET modality = 'STRENGTH', secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[], contraindications = ARRAY['SHOULDER', 'IMPACT']::TEXT[] WHERE name = 'Barra Fixa com Salto';

-- Barra Fixa com Ênfase no Braquial
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> VERTICAL_PULL
--   secondary_muscles: (vazio) -> SHOULDERS, BICEPS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'VERTICAL_PULL', secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Barra Fixa com Ênfase no Braquial';

-- Barra Fixa de Cabeça para Baixo
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> VERTICAL_PULL
--   secondary_muscles: (vazio) -> SHOULDERS, BICEPS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'VERTICAL_PULL', secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Barra Fixa de Cabeça para Baixo';

-- Bicicleta Ergométrica (Spinning)
--   secondary_muscles: GLUTES, CORE -> GLUTES
--   contraindications: KNEE -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY['GLUTES']::TEXT[], contraindications = ARRAY[]::TEXT[] WHERE name = 'Bicicleta Ergométrica (Spinning)';

-- Bicicleta Ergométrica Reclinada
--   secondary_muscles: GLUTES, CORE -> GLUTES
UPDATE exercises SET secondary_muscles = ARRAY['GLUTES']::TEXT[] WHERE name = 'Bicicleta Ergométrica Reclinada';

-- Bom Dia com Barra
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HINGE
--   is_compound: (vazio) -> sim
--   equipment: (vazio) -> BARBELL
--   primary_muscles: LEGS, GLUTES -> LEGS
--   secondary_muscles: (vazio) -> GLUTES, BACK
--   unilateral: (vazio) -> não
--   prescription_type: (vazio) -> REPS
--   level: (vazio) -> INTERMEDIATE
--   contraindications: (vazio) -> LUMBAR
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HINGE', is_compound = true, equipment = 'BARBELL', primary_muscles = ARRAY['LEGS']::TEXT[], secondary_muscles = ARRAY['GLUTES', 'BACK']::TEXT[], unilateral = false, prescription_type = 'REPS', level = 'INTERMEDIATE', contraindications = ARRAY['LUMBAR']::TEXT[] WHERE name = 'Bom Dia com Barra';

-- Bom Dia com Faixa Elástica
--   primary_muscles: GLUTES, BACK -> LEGS
--   secondary_muscles: LEGS, CORE -> GLUTES, BACK
UPDATE exercises SET primary_muscles = ARRAY['LEGS']::TEXT[], secondary_muscles = ARRAY['GLUTES', 'BACK']::TEXT[] WHERE name = 'Bom Dia com Faixa Elástica';

-- Bom Dia no Smith
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HINGE
--   secondary_muscles: (vazio) -> GLUTES, BACK
--   contraindications: (vazio) -> LUMBAR
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HINGE', secondary_muscles = ARRAY['GLUTES', 'BACK']::TEXT[], contraindications = ARRAY['LUMBAR']::TEXT[] WHERE name = 'Bom Dia no Smith';

-- Boxe Sombra
--   primary_muscles: SHOULDERS, CORE -> SHOULDERS
--   secondary_muscles: LEGS, BACK -> CHEST, TRICEPS, CORE
UPDATE exercises SET primary_muscles = ARRAY['SHOULDERS']::TEXT[], secondary_muscles = ARRAY['CHEST', 'TRICEPS', 'CORE']::TEXT[] WHERE name = 'Boxe Sombra';

-- Burpee
--   secondary_muscles: GLUTES, SHOULDERS, CORE -> SHOULDERS, TRICEPS, CORE
--   prescription_type: REPS -> TIME
--   contraindications: IMPACT, KNEE -> SHOULDER, WRIST, IMPACT
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS', 'CORE']::TEXT[], prescription_type = 'TIME', contraindications = ARRAY['SHOULDER', 'WRIST', 'IMPACT']::TEXT[] WHERE name = 'Burpee';

-- Burpee Navy SEAL
--   modality: (vazio) -> CARDIO
--   movement_pattern: (vazio) -> SQUAT
--   secondary_pattern: (vazio) -> HORIZONTAL_PUSH
--   primary_muscles: LEGS -> LEGS, CHEST
--   secondary_muscles: (vazio) -> SHOULDERS, TRICEPS, CORE
--   prescription_type: REPS -> TIME
--   contraindications: (vazio) -> SHOULDER, WRIST, IMPACT
UPDATE exercises SET modality = 'CARDIO', movement_pattern = 'SQUAT', secondary_pattern = 'HORIZONTAL_PUSH', primary_muscles = ARRAY['LEGS', 'CHEST']::TEXT[], secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS', 'CORE']::TEXT[], prescription_type = 'TIME', contraindications = ARRAY['SHOULDER', 'WRIST', 'IMPACT']::TEXT[] WHERE name = 'Burpee Navy SEAL';

-- Burpee com Polichinelo
--   modality: (vazio) -> CARDIO
--   movement_pattern: (vazio) -> SQUAT
--   secondary_muscles: (vazio) -> SHOULDERS, CORE
--   prescription_type: REPS -> TIME
--   contraindications: (vazio) -> SHOULDER, WRIST, IMPACT
UPDATE exercises SET modality = 'CARDIO', movement_pattern = 'SQUAT', secondary_muscles = ARRAY['SHOULDERS', 'CORE']::TEXT[], prescription_type = 'TIME', contraindications = ARRAY['SHOULDER', 'WRIST', 'IMPACT']::TEXT[] WHERE name = 'Burpee com Polichinelo';

-- Cadeira Adutora
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE' WHERE name = 'Cadeira Adutora';

-- Cadeira Extensora com Elástico Tubular
--   unilateral: não -> sim
UPDATE exercises SET unilateral = true WHERE name = 'Cadeira Extensora com Elástico Tubular';

-- Cadeira Extensora com Miniband
--   secondary_muscles: CORE -> (vazio)
--   unilateral: não -> sim
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[], unilateral = true WHERE name = 'Cadeira Extensora com Miniband';

-- Caminhada
--   categoria: FUNCTIONAL_HIT -> CARDIO
--   secondary_muscles: GLUTES, CORE -> GLUTES
UPDATE exercises SET category = 'CARDIO', secondary_muscles = ARRAY['GLUTES']::TEXT[] WHERE name = 'Caminhada';

-- Caminhada Lateral com Miniband
--   movement_pattern: GAIT -> NONE
--   is_compound: sim -> não
--   secondary_muscles: LEGS, CORE -> LEGS
UPDATE exercises SET movement_pattern = 'NONE', is_compound = false, secondary_muscles = ARRAY['LEGS']::TEXT[] WHERE name = 'Caminhada Lateral com Miniband';

-- Caminhada do Fazendeiro com Halteres
--   modality: (vazio) -> STRENGTH
--   primary_muscles: CORE, BACK -> FOREARMS
--   secondary_muscles: LEGS, GLUTES, SHOULDERS -> BACK, LEGS
UPDATE exercises SET modality = 'STRENGTH', primary_muscles = ARRAY['FOREARMS']::TEXT[], secondary_muscles = ARRAY['BACK', 'LEGS']::TEXT[] WHERE name = 'Caminhada do Fazendeiro com Halteres';

-- Caminhada em Parada de Mão
--   modality: (vazio) -> SKILL
--   primary_muscles: SHOULDERS, CORE -> SHOULDERS
--   secondary_muscles: BACK -> TRICEPS
UPDATE exercises SET modality = 'SKILL', primary_muscles = ARRAY['SHOULDERS']::TEXT[], secondary_muscles = ARRAY['TRICEPS']::TEXT[] WHERE name = 'Caminhada em Parada de Mão';

-- Caminhada na Esteira Inclinada
--   secondary_muscles: CORE -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Caminhada na Esteira Inclinada';

-- Caminhada na Parede
--   modality: (vazio) -> STRENGTH
--   primary_muscles: SHOULDERS, CORE -> SHOULDERS
--   secondary_muscles: CHEST, BACK -> (vazio)
UPDATE exercises SET modality = 'STRENGTH', primary_muscles = ARRAY['SHOULDERS']::TEXT[], secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Caminhada na Parede';

-- Carregamento Zercher
--   modality: (vazio) -> STRENGTH
--   primary_muscles: CORE, LEGS -> BACK
--   secondary_muscles: BACK, GLUTES -> BICEPS, LEGS
UPDATE exercises SET modality = 'STRENGTH', primary_muscles = ARRAY['BACK']::TEXT[], secondary_muscles = ARRAY['BICEPS', 'LEGS']::TEXT[] WHERE name = 'Carregamento Zercher';

-- Chute em Gancho no Saco
--   primary_muscles: LEGS, CORE -> LEGS
--   secondary_muscles: GLUTES -> GLUTES, CORE
--   contraindications: (vazio) -> KNEE
UPDATE exercises SET primary_muscles = ARRAY['LEGS']::TEXT[], secondary_muscles = ARRAY['GLUTES', 'CORE']::TEXT[], contraindications = ARRAY['KNEE']::TEXT[] WHERE name = 'Chute em Gancho no Saco';

-- Chute no Glúteo (Butt Kicks)
--   modality: (vazio) -> CARDIO
--   movement_pattern: (vazio) -> GAIT
--   primary_muscles: GLUTES -> LEGS
--   unilateral: sim -> não
--   prescription_type: REPS -> TIME
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> IMPACT
UPDATE exercises SET modality = 'CARDIO', movement_pattern = 'GAIT', primary_muscles = ARRAY['LEGS']::TEXT[], unilateral = false, prescription_type = 'TIME', level = 'BEGINNER', contraindications = ARRAY['IMPACT']::TEXT[] WHERE name = 'Chute no Glúteo (Butt Kicks)';

-- Ciclismo ao Ar Livre
--   secondary_muscles: GLUTES, CORE -> GLUTES
UPDATE exercises SET secondary_muscles = ARRAY['GLUTES']::TEXT[] WHERE name = 'Ciclismo ao Ar Livre';

-- Clean and Jerk com Kettlebell
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HINGE
--   secondary_pattern: (vazio) -> VERTICAL_PUSH
--   primary_muscles: SHOULDERS -> GLUTES, SHOULDERS
--   secondary_muscles: (vazio) -> LEGS, TRICEPS
--   unilateral: não -> sim
--   level: ADVANCED -> INTERMEDIATE
--   contraindications: (vazio) -> LUMBAR, SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HINGE', secondary_pattern = 'VERTICAL_PUSH', primary_muscles = ARRAY['GLUTES', 'SHOULDERS']::TEXT[], secondary_muscles = ARRAY['LEGS', 'TRICEPS']::TEXT[], unilateral = true, level = 'INTERMEDIATE', contraindications = ARRAY['LUMBAR', 'SHOULDER']::TEXT[] WHERE name = 'Clean and Jerk com Kettlebell';

-- Clean and Press com Barra
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HINGE
--   secondary_pattern: (vazio) -> VERTICAL_PUSH
--   primary_muscles: SHOULDERS -> GLUTES, SHOULDERS
--   secondary_muscles: (vazio) -> LEGS, TRICEPS, BACK
--   contraindications: (vazio) -> LUMBAR, SHOULDER, KNEE, WRIST
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HINGE', secondary_pattern = 'VERTICAL_PUSH', primary_muscles = ARRAY['GLUTES', 'SHOULDERS']::TEXT[], secondary_muscles = ARRAY['LEGS', 'TRICEPS', 'BACK']::TEXT[], contraindications = ARRAY['LUMBAR', 'SHOULDER', 'KNEE', 'WRIST']::TEXT[] WHERE name = 'Clean and Press com Barra';

-- Clean e Desenvolvimento com Kettlebell
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HINGE
--   secondary_pattern: (vazio) -> VERTICAL_PUSH
--   primary_muscles: SHOULDERS -> GLUTES, SHOULDERS
--   secondary_muscles: (vazio) -> LEGS, TRICEPS
--   unilateral: não -> sim
--   level: ADVANCED -> INTERMEDIATE
--   contraindications: (vazio) -> LUMBAR, SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HINGE', secondary_pattern = 'VERTICAL_PUSH', primary_muscles = ARRAY['GLUTES', 'SHOULDERS']::TEXT[], secondary_muscles = ARRAY['LEGS', 'TRICEPS']::TEXT[], unilateral = true, level = 'INTERMEDIATE', contraindications = ARRAY['LUMBAR', 'SHOULDER']::TEXT[] WHERE name = 'Clean e Desenvolvimento com Kettlebell';

-- Coice de Burro (Quatro Apoios)
--   movement_pattern: HINGE -> NONE
--   is_compound: sim -> não
--   secondary_muscles: LEGS, CORE -> (vazio)
UPDATE exercises SET movement_pattern = 'NONE', is_compound = false, secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Coice de Burro (Quatro Apoios)';

-- Coice de Glúteo Unilateral na Polia Baixa
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   secondary_muscles: (vazio) -> LEGS
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', secondary_muscles = ARRAY['LEGS']::TEXT[], level = 'BEGINNER' WHERE name = 'Coice de Glúteo Unilateral na Polia Baixa';

-- Coice de Glúteo com Gymstick
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   equipment: BAND -> GYMSTICK
--   secondary_muscles: (vazio) -> LEGS
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', equipment = 'GYMSTICK', secondary_muscles = ARRAY['LEGS']::TEXT[], level = 'BEGINNER' WHERE name = 'Coice de Glúteo com Gymstick';

-- Coice de Glúteo com Perna Flexionada
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   secondary_muscles: (vazio) -> LEGS
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', secondary_muscles = ARRAY['LEGS']::TEXT[], level = 'BEGINNER' WHERE name = 'Coice de Glúteo com Perna Flexionada';

-- Coice de Glúteo com Perna Flexionada e Miniband
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE' WHERE name = 'Coice de Glúteo com Perna Flexionada e Miniband';

-- Coice de Glúteo em Pé
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', level = 'BEGINNER' WHERE name = 'Coice de Glúteo em Pé';

-- Coice de Glúteo em Pé com Faixa Elástica
--   movement_pattern: HINGE -> NONE
--   is_compound: sim -> não
--   secondary_muscles: LEGS, CORE -> LEGS
UPDATE exercises SET movement_pattern = 'NONE', is_compound = false, secondary_muscles = ARRAY['LEGS']::TEXT[] WHERE name = 'Coice de Glúteo em Pé com Faixa Elástica';

-- Coice de Glúteo em Quatro Apoios com Faixa Elástica
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   secondary_muscles: (vazio) -> LEGS
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', secondary_muscles = ARRAY['LEGS']::TEXT[] WHERE name = 'Coice de Glúteo em Quatro Apoios com Faixa Elástica';

-- Coice de Glúteo na Máquina
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   is_compound: (vazio) -> sim
--   equipment: (vazio) -> MACHINE
--   secondary_muscles: (vazio) -> LEGS
--   unilateral: (vazio) -> sim
--   prescription_type: (vazio) -> REPS
--   level: (vazio) -> BEGINNER
--   contraindications: (vazio) -> KNEE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', is_compound = true, equipment = 'MACHINE', secondary_muscles = ARRAY['LEGS']::TEXT[], unilateral = true, prescription_type = 'REPS', level = 'BEGINNER', contraindications = ARRAY['KNEE']::TEXT[] WHERE name = 'Coice de Glúteo na Máquina';

-- Coice de Glúteo na Máquina Multi-Hip
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   secondary_muscles: (vazio) -> LEGS
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', secondary_muscles = ARRAY['LEGS']::TEXT[] WHERE name = 'Coice de Glúteo na Máquina Multi-Hip';

-- Coice de Glúteo no Smith
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   secondary_muscles: (vazio) -> LEGS
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', secondary_muscles = ARRAY['LEGS']::TEXT[] WHERE name = 'Coice de Glúteo no Smith';

-- Concha em Decúbito Lateral (Clamshell)
--   secondary_muscles: CORE -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Concha em Decúbito Lateral (Clamshell)';

-- Corda Naval
--   primary_muscles: SHOULDERS, CORE -> SHOULDERS
--   secondary_muscles: BACK, LEGS -> BACK
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET primary_muscles = ARRAY['SHOULDERS']::TEXT[], secondary_muscles = ARRAY['BACK']::TEXT[], level = 'BEGINNER' WHERE name = 'Corda Naval';

-- Corrida
--   secondary_muscles: GLUTES, CORE -> GLUTES
UPDATE exercises SET secondary_muscles = ARRAY['GLUTES']::TEXT[] WHERE name = 'Corrida';

-- Corrida Estacionária
--   secondary_muscles: GLUTES, CORE -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Corrida Estacionária';

-- Corrida com Joelhos Altos (Skipping)
--   modality: (vazio) -> CARDIO
--   movement_pattern: (vazio) -> GAIT
--   primary_muscles: CORE -> LEGS
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> IMPACT
UPDATE exercises SET modality = 'CARDIO', movement_pattern = 'GAIT', primary_muscles = ARRAY['LEGS']::TEXT[], level = 'BEGINNER', contraindications = ARRAY['IMPACT']::TEXT[] WHERE name = 'Corrida com Joelhos Altos (Skipping)';

-- Corrida com Passadas Rápidas
--   modality: (vazio) -> CARDIO
--   movement_pattern: (vazio) -> GAIT
--   primary_muscles: CORE -> LEGS
--   secondary_muscles: (vazio) -> GLUTES
--   contraindications: (vazio) -> IMPACT
UPDATE exercises SET modality = 'CARDIO', movement_pattern = 'GAIT', primary_muscles = ARRAY['LEGS']::TEXT[], secondary_muscles = ARRAY['GLUTES']::TEXT[], contraindications = ARRAY['IMPACT']::TEXT[] WHERE name = 'Corrida com Passadas Rápidas';

-- Corrida com Passadas Saltadas (Bounding)
--   modality: (vazio) -> PLYOMETRIC
--   movement_pattern: (vazio) -> GAIT
--   primary_muscles: CORE -> LEGS
--   secondary_muscles: (vazio) -> GLUTES
--   prescription_type: TIME -> DISTANCE
--   contraindications: (vazio) -> IMPACT, KNEE
UPDATE exercises SET modality = 'PLYOMETRIC', movement_pattern = 'GAIT', primary_muscles = ARRAY['LEGS']::TEXT[], secondary_muscles = ARRAY['GLUTES']::TEXT[], prescription_type = 'DISTANCE', contraindications = ARRAY['IMPACT', 'KNEE']::TEXT[] WHERE name = 'Corrida com Passadas Saltadas (Bounding)';

-- Corrida com Passos Curtos
--   modality: (vazio) -> CARDIO
--   movement_pattern: (vazio) -> GAIT
--   primary_muscles: CORE -> LEGS
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> IMPACT
UPDATE exercises SET modality = 'CARDIO', movement_pattern = 'GAIT', primary_muscles = ARRAY['LEGS']::TEXT[], level = 'BEGINNER', contraindications = ARRAY['IMPACT']::TEXT[] WHERE name = 'Corrida com Passos Curtos';

-- Corrida de Costas
--   modality: (vazio) -> CARDIO
--   movement_pattern: (vazio) -> GAIT
--   contraindications: (vazio) -> IMPACT
UPDATE exercises SET modality = 'CARDIO', movement_pattern = 'GAIT', contraindications = ARRAY['IMPACT']::TEXT[] WHERE name = 'Corrida de Costas';

-- Corrida na Esteira
--   secondary_muscles: GLUTES, CORE -> GLUTES
UPDATE exercises SET secondary_muscles = ARRAY['GLUTES']::TEXT[] WHERE name = 'Corrida na Esteira';

-- Crossover Unilateral no Cabo
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   secondary_muscles: (vazio) -> SHOULDERS
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', secondary_muscles = ARRAY['SHOULDERS']::TEXT[], level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Crossover Unilateral no Cabo';

-- Crossover na Polia Média
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   secondary_muscles: (vazio) -> SHOULDERS
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', secondary_muscles = ARRAY['SHOULDERS']::TEXT[], level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Crossover na Polia Média';

-- Crossover para Peitoral Superior
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   primary_muscles: CHEST -> CHEST, SHOULDERS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', primary_muscles = ARRAY['CHEST', 'SHOULDERS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Crossover para Peitoral Superior';

-- Crucifixo Declinado Unilateral no Cabo
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   secondary_muscles: (vazio) -> SHOULDERS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', secondary_muscles = ARRAY['SHOULDERS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Crucifixo Declinado Unilateral no Cabo';

-- Crucifixo Declinado com Halteres
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   secondary_muscles: (vazio) -> SHOULDERS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', secondary_muscles = ARRAY['SHOULDERS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Crucifixo Declinado com Halteres';

-- Crucifixo Declinado no Cabo
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   secondary_muscles: (vazio) -> SHOULDERS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', secondary_muscles = ARRAY['SHOULDERS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Crucifixo Declinado no Cabo';

-- Crucifixo Inclinado no Cabo
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   secondary_muscles: (vazio) -> SHOULDERS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', secondary_muscles = ARRAY['SHOULDERS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Crucifixo Inclinado no Cabo';

-- Crucifixo Inverso Apoiado no Banco Inclinado
--   categoria: TRAPEZIUS -> SHOULDERS
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   secondary_muscles: (vazio) -> BACK
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET category = 'SHOULDERS', modality = 'STRENGTH', movement_pattern = 'NONE', secondary_muscles = ARRAY['BACK']::TEXT[], level = 'BEGINNER' WHERE name = 'Crucifixo Inverso Apoiado no Banco Inclinado';

-- Crucifixo Inverso Curvado com Faixa Elástica
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   is_compound: sim -> não
--   secondary_muscles: (vazio) -> BACK
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', is_compound = false, secondary_muscles = ARRAY['BACK']::TEXT[], level = 'BEGINNER' WHERE name = 'Crucifixo Inverso Curvado com Faixa Elástica';

-- Crucifixo Inverso Curvado com Halteres
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   secondary_muscles: (vazio) -> BACK
--   contraindications: (vazio) -> LUMBAR
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', secondary_muscles = ARRAY['BACK']::TEXT[], contraindications = ARRAY['LUMBAR']::TEXT[] WHERE name = 'Crucifixo Inverso Curvado com Halteres';

-- Crucifixo Inverso Curvado no Cabo
--   categoria: TRAPEZIUS -> SHOULDERS
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   is_compound: sim -> não
--   secondary_muscles: (vazio) -> BACK
--   contraindications: (vazio) -> LUMBAR
UPDATE exercises SET category = 'SHOULDERS', modality = 'STRENGTH', movement_pattern = 'NONE', is_compound = false, secondary_muscles = ARRAY['BACK']::TEXT[], contraindications = ARRAY['LUMBAR']::TEXT[] WHERE name = 'Crucifixo Inverso Curvado no Cabo';

-- Crucifixo Inverso Deitado no Banco
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   secondary_muscles: (vazio) -> BACK
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', secondary_muscles = ARRAY['BACK']::TEXT[], level = 'BEGINNER' WHERE name = 'Crucifixo Inverso Deitado no Banco';

-- Crucifixo Inverso Deitado nos Cabos
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   is_compound: sim -> não
--   secondary_muscles: (vazio) -> BACK
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', is_compound = false, secondary_muscles = ARRAY['BACK']::TEXT[] WHERE name = 'Crucifixo Inverso Deitado nos Cabos';

-- Crucifixo Inverso Unilateral no Cabo
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   secondary_muscles: (vazio) -> BACK
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', secondary_muscles = ARRAY['BACK']::TEXT[], level = 'BEGINNER' WHERE name = 'Crucifixo Inverso Unilateral no Cabo';

-- Crucifixo Inverso com Gymstick
--   equipment: BAND -> GYMSTICK
--   level: BEGINNER -> INTERMEDIATE
--   contraindications: (vazio) -> LUMBAR
UPDATE exercises SET equipment = 'GYMSTICK', level = 'INTERMEDIATE', contraindications = ARRAY['LUMBAR']::TEXT[] WHERE name = 'Crucifixo Inverso com Gymstick';

-- Crucifixo Inverso na Máquina (Voador Invertido)
--   equipment: DUMBBELL -> MACHINE
UPDATE exercises SET equipment = 'MACHINE' WHERE name = 'Crucifixo Inverso na Máquina (Voador Invertido)';

-- Crucifixo Reto no Cabo
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   secondary_muscles: (vazio) -> SHOULDERS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', secondary_muscles = ARRAY['SHOULDERS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Crucifixo Reto no Cabo';

-- Crucifixo Unilateral no Solo com Landmine
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   is_compound: (vazio) -> não
--   equipment: (vazio) -> LANDMINE
--   secondary_muscles: (vazio) -> SHOULDERS
--   unilateral: (vazio) -> sim
--   prescription_type: (vazio) -> REPS
--   level: (vazio) -> INTERMEDIATE
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', is_compound = false, equipment = 'LANDMINE', secondary_muscles = ARRAY['SHOULDERS']::TEXT[], unilateral = true, prescription_type = 'REPS', level = 'INTERMEDIATE', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Crucifixo Unilateral no Solo com Landmine';

-- Crucifixo no TRX
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   secondary_muscles: (vazio) -> SHOULDERS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', secondary_muscles = ARRAY['SHOULDERS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Crucifixo no TRX';

-- Cruz de Ferro com Halteres
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> SQUAT
--   is_compound: (vazio) -> sim
--   equipment: (vazio) -> DUMBBELL
--   primary_muscles: CHEST -> SHOULDERS, LEGS
--   secondary_muscles: (vazio) -> GLUTES, BACK
--   unilateral: (vazio) -> não
--   prescription_type: (vazio) -> REPS
--   level: (vazio) -> INTERMEDIATE
--   contraindications: (vazio) -> SHOULDER, KNEE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'SQUAT', is_compound = true, equipment = 'DUMBBELL', primary_muscles = ARRAY['SHOULDERS', 'LEGS']::TEXT[], secondary_muscles = ARRAY['GLUTES', 'BACK']::TEXT[], unilateral = false, prescription_type = 'REPS', level = 'INTERMEDIATE', contraindications = ARRAY['SHOULDER', 'KNEE']::TEXT[] WHERE name = 'Cruz de Ferro com Halteres';

-- Cruzado de Direita (Boxe)
--   primary_muscles: SHOULDERS, CORE -> SHOULDERS
--   secondary_muscles: LEGS, BACK -> CHEST, TRICEPS, CORE
UPDATE exercises SET primary_muscles = ARRAY['SHOULDERS']::TEXT[], secondary_muscles = ARRAY['CHEST', 'TRICEPS', 'CORE']::TEXT[] WHERE name = 'Cruzado de Direita (Boxe)';

-- Círculos com Anilha
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   equipment: DUMBBELL -> WEIGHT_PLATE
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', equipment = 'WEIGHT_PLATE', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Círculos com Anilha';

-- Círculos com Um Braço
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> NONE
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'NONE', level = 'BEGINNER' WHERE name = 'Círculos com Um Braço';

-- Círculos com os Braços
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> NONE
--   prescription_type: TIME -> REPS
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'NONE', prescription_type = 'REPS', level = 'BEGINNER' WHERE name = 'Círculos com os Braços';

-- Depressão Escapular no Banco
--   secondary_muscles: SHOULDERS, BACK -> (vazio)
--   contraindications: SHOULDER -> SHOULDER, WRIST
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[], contraindications = ARRAY['SHOULDER', 'WRIST']::TEXT[] WHERE name = 'Depressão Escapular no Banco';

-- Descida Unilateral do Step
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> LUNGE
--   primary_muscles: LEGS -> LEGS, GLUTES
--   unilateral: não -> sim
--   contraindications: (vazio) -> KNEE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'LUNGE', primary_muscles = ARRAY['LEGS', 'GLUTES']::TEXT[], unilateral = true, contraindications = ARRAY['KNEE']::TEXT[] WHERE name = 'Descida Unilateral do Step';

-- Desenvolvimento Ajoelhado no Cabo
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> VERTICAL_PUSH
--   secondary_muscles: (vazio) -> TRICEPS, CHEST
--   contraindications: (vazio) -> SHOULDER, LUMBAR
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'VERTICAL_PUSH', secondary_muscles = ARRAY['TRICEPS', 'CHEST']::TEXT[], contraindications = ARRAY['SHOULDER', 'LUMBAR']::TEXT[] WHERE name = 'Desenvolvimento Ajoelhado no Cabo';

-- Desenvolvimento Ajoelhado no Landmine
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> VERTICAL_PUSH
--   equipment: BARBELL -> LANDMINE
--   secondary_muscles: (vazio) -> TRICEPS, CHEST
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'VERTICAL_PUSH', equipment = 'LANDMINE', secondary_muscles = ARRAY['TRICEPS', 'CHEST']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Desenvolvimento Ajoelhado no Landmine';

-- Desenvolvimento Alternado com Rotação com Halteres
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> VERTICAL_PUSH
--   secondary_pattern: (vazio) -> ROTATION
--   secondary_muscles: (vazio) -> TRICEPS, CHEST, CORE
--   contraindications: (vazio) -> SHOULDER, LUMBAR
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'VERTICAL_PUSH', secondary_pattern = 'ROTATION', secondary_muscles = ARRAY['TRICEPS', 'CHEST', 'CORE']::TEXT[], contraindications = ARRAY['SHOULDER', 'LUMBAR']::TEXT[] WHERE name = 'Desenvolvimento Alternado com Rotação com Halteres';

-- Desenvolvimento Alternado em Pé com Halteres
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> VERTICAL_PUSH
--   secondary_muscles: (vazio) -> TRICEPS, CHEST
--   contraindications: (vazio) -> SHOULDER, LUMBAR
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'VERTICAL_PUSH', secondary_muscles = ARRAY['TRICEPS', 'CHEST']::TEXT[], contraindications = ARRAY['SHOULDER', 'LUMBAR']::TEXT[] WHERE name = 'Desenvolvimento Alternado em Pé com Halteres';

-- Desenvolvimento Arnold
--   secondary_muscles: CORE -> TRICEPS, CHEST
UPDATE exercises SET secondary_muscles = ARRAY['TRICEPS', 'CHEST']::TEXT[] WHERE name = 'Desenvolvimento Arnold';

-- Desenvolvimento Arnold Unilateral
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> VERTICAL_PUSH
--   secondary_muscles: (vazio) -> TRICEPS, CHEST
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'VERTICAL_PUSH', secondary_muscles = ARRAY['TRICEPS', 'CHEST']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Desenvolvimento Arnold Unilateral';

-- Desenvolvimento Arnold Unilateral com Kettlebell
--   modality: (vazio) -> STRENGTH
--   secondary_muscles: CORE -> TRICEPS, CHEST
--   unilateral: não -> sim
--   contraindications: SHOULDER -> SHOULDER, LUMBAR
UPDATE exercises SET modality = 'STRENGTH', secondary_muscles = ARRAY['TRICEPS', 'CHEST']::TEXT[], unilateral = true, contraindications = ARRAY['SHOULDER', 'LUMBAR']::TEXT[] WHERE name = 'Desenvolvimento Arnold Unilateral com Kettlebell';

-- Desenvolvimento Cubano Sentado com Halteres
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> VERTICAL_PUSH
--   secondary_muscles: (vazio) -> TRICEPS, BACK
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'VERTICAL_PUSH', secondary_muscles = ARRAY['TRICEPS', 'BACK']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Desenvolvimento Cubano Sentado com Halteres';

-- Desenvolvimento Cubano com Halteres
--   secondary_muscles: BACK -> TRICEPS, BACK
UPDATE exercises SET secondary_muscles = ARRAY['TRICEPS', 'BACK']::TEXT[] WHERE name = 'Desenvolvimento Cubano com Halteres';

-- Desenvolvimento Deitado de Bruços com Halteres
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> VERTICAL_PUSH
--   secondary_muscles: (vazio) -> TRICEPS, BACK
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'VERTICAL_PUSH', secondary_muscles = ARRAY['TRICEPS', 'BACK']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Desenvolvimento Deitado de Bruços com Halteres';

-- Desenvolvimento Militar Pegada Fechada
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> VERTICAL_PUSH
--   secondary_muscles: (vazio) -> TRICEPS, CHEST
--   contraindications: (vazio) -> SHOULDER, LUMBAR, WRIST
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'VERTICAL_PUSH', secondary_muscles = ARRAY['TRICEPS', 'CHEST']::TEXT[], contraindications = ARRAY['SHOULDER', 'LUMBAR', 'WRIST']::TEXT[] WHERE name = 'Desenvolvimento Militar Pegada Fechada';

-- Desenvolvimento Militar com Barra
--   secondary_muscles: CHEST, CORE -> TRICEPS, CHEST
UPDATE exercises SET secondary_muscles = ARRAY['TRICEPS', 'CHEST']::TEXT[] WHERE name = 'Desenvolvimento Militar com Barra';

-- Desenvolvimento Militar em Pé no Smith
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> VERTICAL_PUSH
--   secondary_muscles: (vazio) -> TRICEPS, CHEST
--   contraindications: (vazio) -> SHOULDER, LUMBAR
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'VERTICAL_PUSH', secondary_muscles = ARRAY['TRICEPS', 'CHEST']::TEXT[], contraindications = ARRAY['SHOULDER', 'LUMBAR']::TEXT[] WHERE name = 'Desenvolvimento Militar em Pé no Smith';

-- Desenvolvimento Sentado com Barra
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> VERTICAL_PUSH
--   secondary_muscles: (vazio) -> TRICEPS, CHEST
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'VERTICAL_PUSH', secondary_muscles = ARRAY['TRICEPS', 'CHEST']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Desenvolvimento Sentado com Barra';

-- Desenvolvimento Sentado com Faixa Elástica
--   secondary_muscles: CORE -> TRICEPS, CHEST
UPDATE exercises SET secondary_muscles = ARRAY['TRICEPS', 'CHEST']::TEXT[] WHERE name = 'Desenvolvimento Sentado com Faixa Elástica';

-- Desenvolvimento Sentado com Halteres
--   secondary_muscles: CHEST -> TRICEPS, CHEST
UPDATE exercises SET secondary_muscles = ARRAY['TRICEPS', 'CHEST']::TEXT[] WHERE name = 'Desenvolvimento Sentado com Halteres';

-- Desenvolvimento Unilateral Ajoelhado com Kettlebell
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> VERTICAL_PUSH
--   secondary_muscles: (vazio) -> TRICEPS, CHEST
--   contraindications: (vazio) -> SHOULDER, LUMBAR
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'VERTICAL_PUSH', secondary_muscles = ARRAY['TRICEPS', 'CHEST']::TEXT[], contraindications = ARRAY['SHOULDER', 'LUMBAR']::TEXT[] WHERE name = 'Desenvolvimento Unilateral Ajoelhado com Kettlebell';

-- Desenvolvimento Unilateral com Faixa Elástica
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> VERTICAL_PUSH
--   secondary_muscles: (vazio) -> TRICEPS, CHEST
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'VERTICAL_PUSH', secondary_muscles = ARRAY['TRICEPS', 'CHEST']::TEXT[], level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Desenvolvimento Unilateral com Faixa Elástica';

-- Desenvolvimento Unilateral com Gymstick
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> VERTICAL_PUSH
--   equipment: BAND -> GYMSTICK
--   secondary_muscles: (vazio) -> TRICEPS, CHEST
--   unilateral: não -> sim
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'VERTICAL_PUSH', equipment = 'GYMSTICK', secondary_muscles = ARRAY['TRICEPS', 'CHEST']::TEXT[], unilateral = true, level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Desenvolvimento Unilateral com Gymstick';

-- Desenvolvimento Unilateral com Halter
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> VERTICAL_PUSH
--   secondary_muscles: (vazio) -> TRICEPS, CHEST
--   contraindications: (vazio) -> SHOULDER, LUMBAR
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'VERTICAL_PUSH', secondary_muscles = ARRAY['TRICEPS', 'CHEST']::TEXT[], contraindications = ARRAY['SHOULDER', 'LUMBAR']::TEXT[] WHERE name = 'Desenvolvimento Unilateral com Halter';

-- Desenvolvimento Unilateral com Kettlebell
--   modality: (vazio) -> STRENGTH
--   secondary_muscles: CORE -> TRICEPS, CHEST
--   contraindications: SHOULDER -> SHOULDER, LUMBAR
UPDATE exercises SET modality = 'STRENGTH', secondary_muscles = ARRAY['TRICEPS', 'CHEST']::TEXT[], contraindications = ARRAY['SHOULDER', 'LUMBAR']::TEXT[] WHERE name = 'Desenvolvimento Unilateral com Kettlebell';

-- Desenvolvimento W com Halteres
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> VERTICAL_PUSH
--   secondary_muscles: (vazio) -> TRICEPS, BACK
--   contraindications: (vazio) -> SHOULDER, LUMBAR
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'VERTICAL_PUSH', secondary_muscles = ARRAY['TRICEPS', 'BACK']::TEXT[], contraindications = ARRAY['SHOULDER', 'LUMBAR']::TEXT[] WHERE name = 'Desenvolvimento W com Halteres';

-- Desenvolvimento Z com Halteres
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> VERTICAL_PUSH
--   secondary_muscles: (vazio) -> TRICEPS, CHEST
--   contraindications: (vazio) -> SHOULDER, LUMBAR
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'VERTICAL_PUSH', secondary_muscles = ARRAY['TRICEPS', 'CHEST']::TEXT[], contraindications = ARRAY['SHOULDER', 'LUMBAR']::TEXT[] WHERE name = 'Desenvolvimento Z com Halteres';

-- Desenvolvimento atrás da Nuca Sentado com Barra
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> VERTICAL_PUSH
--   secondary_muscles: (vazio) -> TRICEPS, CHEST
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'VERTICAL_PUSH', secondary_muscles = ARRAY['TRICEPS', 'CHEST']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Desenvolvimento atrás da Nuca Sentado com Barra';

-- Desenvolvimento atrás da Nuca com Gymstick
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> VERTICAL_PUSH
--   equipment: BAND -> GYMSTICK
--   secondary_muscles: (vazio) -> TRICEPS, CHEST
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'VERTICAL_PUSH', equipment = 'GYMSTICK', secondary_muscles = ARRAY['TRICEPS', 'CHEST']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Desenvolvimento atrás da Nuca com Gymstick';

-- Desenvolvimento atrás da Nuca no Smith
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> VERTICAL_PUSH
--   secondary_muscles: (vazio) -> TRICEPS, CHEST
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'VERTICAL_PUSH', secondary_muscles = ARRAY['TRICEPS', 'CHEST']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Desenvolvimento atrás da Nuca no Smith';

-- Desenvolvimento com Barra W Pegada Supinada
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> VERTICAL_PUSH
--   secondary_muscles: (vazio) -> TRICEPS, CHEST
--   contraindications: (vazio) -> SHOULDER, LUMBAR
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'VERTICAL_PUSH', secondary_muscles = ARRAY['TRICEPS', 'CHEST']::TEXT[], contraindications = ARRAY['SHOULDER', 'LUMBAR']::TEXT[] WHERE name = 'Desenvolvimento com Barra W Pegada Supinada';

-- Desenvolvimento com Kettlebells
--   modality: (vazio) -> STRENGTH
--   secondary_muscles: CORE -> TRICEPS, CHEST
--   unilateral: sim -> não
--   contraindications: SHOULDER -> SHOULDER, LUMBAR
UPDATE exercises SET modality = 'STRENGTH', secondary_muscles = ARRAY['TRICEPS', 'CHEST']::TEXT[], unilateral = false, contraindications = ARRAY['SHOULDER', 'LUMBAR']::TEXT[] WHERE name = 'Desenvolvimento com Kettlebells';

-- Desenvolvimento com Peso Corporal (sem Carga)
--   modality: STRENGTH -> MOBILITY
--   secondary_muscles: CHEST, CORE -> TRICEPS
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'MOBILITY', secondary_muscles = ARRAY['TRICEPS']::TEXT[], level = 'BEGINNER' WHERE name = 'Desenvolvimento com Peso Corporal (sem Carga)';

-- Desenvolvimento em Pé com Halteres Pegada Neutra
--   secondary_muscles: CORE -> TRICEPS, CHEST
--   contraindications: SHOULDER -> SHOULDER, LUMBAR
UPDATE exercises SET secondary_muscles = ARRAY['TRICEPS', 'CHEST']::TEXT[], contraindications = ARRAY['SHOULDER', 'LUMBAR']::TEXT[] WHERE name = 'Desenvolvimento em Pé com Halteres Pegada Neutra';

-- Desenvolvimento em Pé no Cabo
--   secondary_muscles: CHEST -> TRICEPS, CHEST
--   level: BEGINNER -> INTERMEDIATE
--   contraindications: SHOULDER -> SHOULDER, LUMBAR
UPDATE exercises SET secondary_muscles = ARRAY['TRICEPS', 'CHEST']::TEXT[], level = 'INTERMEDIATE', contraindications = ARRAY['SHOULDER', 'LUMBAR']::TEXT[] WHERE name = 'Desenvolvimento em Pé no Cabo';

-- Desenvolvimento em Pé no Landmine
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> VERTICAL_PUSH
--   equipment: BARBELL -> LANDMINE
--   secondary_muscles: (vazio) -> TRICEPS, CHEST
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'VERTICAL_PUSH', equipment = 'LANDMINE', secondary_muscles = ARRAY['TRICEPS', 'CHEST']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Desenvolvimento em Pé no Landmine';

-- Desenvolvimento na Máquina Articulada
--   secondary_muscles: CHEST -> TRICEPS, CHEST
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET secondary_muscles = ARRAY['TRICEPS', 'CHEST']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Desenvolvimento na Máquina Articulada';

-- Desenvolvimento na Máquina Inclinada (Invertido)
--   movement_pattern: NONE -> VERTICAL_PUSH
--   is_compound: não -> sim
--   secondary_muscles: BACK -> TRICEPS, CHEST
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET movement_pattern = 'VERTICAL_PUSH', is_compound = true, secondary_muscles = ARRAY['TRICEPS', 'CHEST']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Desenvolvimento na Máquina Inclinada (Invertido)';

-- Desenvolvimento na Máquina Pegada Neutra
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> VERTICAL_PUSH
--   secondary_muscles: (vazio) -> TRICEPS, CHEST
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'VERTICAL_PUSH', secondary_muscles = ARRAY['TRICEPS', 'CHEST']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Desenvolvimento na Máquina Pegada Neutra';

-- Desenvolvimento no Smith
--   equipment: MACHINE -> SMITH
--   secondary_muscles: CHEST, CORE -> TRICEPS, CHEST
--   level: BEGINNER -> INTERMEDIATE
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET equipment = 'SMITH', secondary_muscles = ARRAY['TRICEPS', 'CHEST']::TEXT[], level = 'INTERMEDIATE', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Desenvolvimento no Smith';

-- Deslizamento de Braços na Parede (Wall Slide)
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> NONE
--   primary_muscles: CORE -> SHOULDERS
--   secondary_muscles: (vazio) -> BACK
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'NONE', primary_muscles = ARRAY['SHOULDERS']::TEXT[], secondary_muscles = ARRAY['BACK']::TEXT[], level = 'BEGINNER' WHERE name = 'Deslizamento de Braços na Parede (Wall Slide)';

-- Deslizamento na Parede com Rolo (Serrátil)
--   movement_pattern: STRETCH -> NONE
--   secondary_muscles: BACK -> CHEST, BACK
--   prescription_type: TIME -> REPS
UPDATE exercises SET movement_pattern = 'NONE', secondary_muscles = ARRAY['CHEST', 'BACK']::TEXT[], prescription_type = 'REPS' WHERE name = 'Deslizamento na Parede com Rolo (Serrátil)';

-- Deslocamento Lateral (Shuffle)
--   modality: (vazio) -> CARDIO
--   movement_pattern: (vazio) -> GAIT
--   secondary_muscles: (vazio) -> GLUTES
--   contraindications: (vazio) -> IMPACT
UPDATE exercises SET modality = 'CARDIO', movement_pattern = 'GAIT', secondary_muscles = ARRAY['GLUTES']::TEXT[], contraindications = ARRAY['IMPACT']::TEXT[] WHERE name = 'Deslocamento Lateral (Shuffle)';

-- Devil Press com Halteres
--   modality: (vazio) -> STRENGTH
--   primary_muscles: LEGS, SHOULDERS -> GLUTES, LEGS
--   secondary_muscles: CHEST, CORE, GLUTES -> SHOULDERS, BACK, CHEST
--   contraindications: LUMBAR, SHOULDER, IMPACT -> LUMBAR, SHOULDER, KNEE, IMPACT
UPDATE exercises SET modality = 'STRENGTH', primary_muscles = ARRAY['GLUTES', 'LEGS']::TEXT[], secondary_muscles = ARRAY['SHOULDERS', 'BACK', 'CHEST']::TEXT[], contraindications = ARRAY['LUMBAR', 'SHOULDER', 'KNEE', 'IMPACT']::TEXT[] WHERE name = 'Devil Press com Halteres';

-- Direto de Direita no Saco
--   primary_muscles: SHOULDERS, CORE -> SHOULDERS
--   secondary_muscles: LEGS, BACK -> CHEST, TRICEPS, CORE
--   contraindications: (vazio) -> WRIST
UPDATE exercises SET primary_muscles = ARRAY['SHOULDERS']::TEXT[], secondary_muscles = ARRAY['CHEST', 'TRICEPS', 'CORE']::TEXT[], contraindications = ARRAY['WRIST']::TEXT[] WHERE name = 'Direto de Direita no Saco';

-- Drill dos 5 Pontos
--   modality: (vazio) -> PLYOMETRIC
--   movement_pattern: (vazio) -> SQUAT
--   primary_muscles: CORE -> LEGS
--   contraindications: (vazio) -> IMPACT
UPDATE exercises SET modality = 'PLYOMETRIC', movement_pattern = 'SQUAT', primary_muscles = ARRAY['LEGS']::TEXT[], contraindications = ARRAY['IMPACT']::TEXT[] WHERE name = 'Drill dos 5 Pontos';

-- Elevação Frontal Alternada Sentado com Halteres
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   secondary_muscles: (vazio) -> CHEST
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', secondary_muscles = ARRAY['CHEST']::TEXT[], level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Elevação Frontal Alternada Sentado com Halteres';

-- Elevação Frontal Alternada com Halteres
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   secondary_muscles: (vazio) -> CHEST
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', secondary_muscles = ARRAY['CHEST']::TEXT[], level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Elevação Frontal Alternada com Halteres';

-- Elevação Frontal Apoiado no Banco Inclinado
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   secondary_muscles: (vazio) -> CHEST
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', secondary_muscles = ARRAY['CHEST']::TEXT[], level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Elevação Frontal Apoiado no Banco Inclinado';

-- Elevação Frontal Bilateral com Halteres
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   secondary_muscles: (vazio) -> CHEST
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', secondary_muscles = ARRAY['CHEST']::TEXT[], level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Elevação Frontal Bilateral com Halteres';

-- Elevação Frontal Bilateral no Cabo
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   secondary_muscles: (vazio) -> CHEST
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', secondary_muscles = ARRAY['CHEST']::TEXT[], level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Elevação Frontal Bilateral no Cabo';

-- Elevação Frontal Sentado com Halteres
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   secondary_muscles: (vazio) -> CHEST
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', secondary_muscles = ARRAY['CHEST']::TEXT[], level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Elevação Frontal Sentado com Halteres';

-- Elevação Frontal Unilateral no Cabo
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   secondary_muscles: (vazio) -> CHEST
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', secondary_muscles = ARRAY['CHEST']::TEXT[], level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Elevação Frontal Unilateral no Cabo';

-- Elevação Frontal com Anilha
--   modality: (vazio) -> STRENGTH
--   secondary_muscles: CORE -> CHEST
UPDATE exercises SET modality = 'STRENGTH', secondary_muscles = ARRAY['CHEST']::TEXT[] WHERE name = 'Elevação Frontal com Anilha';

-- Elevação Frontal com Barra W e Rotação
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   secondary_muscles: (vazio) -> CHEST
--   contraindications: (vazio) -> SHOULDER, WRIST
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', secondary_muscles = ARRAY['CHEST']::TEXT[], contraindications = ARRAY['SHOULDER', 'WRIST']::TEXT[] WHERE name = 'Elevação Frontal com Barra W e Rotação';

-- Elevação Frontal com Barra W no Banco Inclinado
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   secondary_muscles: (vazio) -> CHEST
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', secondary_muscles = ARRAY['CHEST']::TEXT[], level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Elevação Frontal com Barra W no Banco Inclinado';

-- Elevação Frontal com Dois Cabos no Crossover
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   secondary_muscles: (vazio) -> CHEST
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', secondary_muscles = ARRAY['CHEST']::TEXT[], level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Elevação Frontal com Dois Cabos no Crossover';

-- Elevação Frontal com Faixa Elástica
--   secondary_muscles: CORE -> CHEST
--   unilateral: não -> sim
UPDATE exercises SET secondary_muscles = ARRAY['CHEST']::TEXT[], unilateral = true WHERE name = 'Elevação Frontal com Faixa Elástica';

-- Elevação Frontal com Halteres
--   secondary_muscles: CORE -> CHEST
UPDATE exercises SET secondary_muscles = ARRAY['CHEST']::TEXT[] WHERE name = 'Elevação Frontal com Halteres';

-- Elevação Frontal com Halteres até acima da Cabeça
--   categoria: CHEST -> SHOULDERS
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   primary_muscles: CHEST -> SHOULDERS
--   secondary_muscles: (vazio) -> CHEST
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET category = 'SHOULDERS', modality = 'STRENGTH', movement_pattern = 'NONE', primary_muscles = ARRAY['SHOULDERS']::TEXT[], secondary_muscles = ARRAY['CHEST']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Elevação Frontal com Halteres até acima da Cabeça';

-- Elevação Frontal no Landmine
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   equipment: BARBELL -> LANDMINE
--   secondary_muscles: (vazio) -> CHEST
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', equipment = 'LANDMINE', secondary_muscles = ARRAY['CHEST']::TEXT[], level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Elevação Frontal no Landmine';

-- Elevação Lateral Alternada com Halteres
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Elevação Lateral Alternada com Halteres';

-- Elevação Lateral Apoiado no Banco Inclinado
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Elevação Lateral Apoiado no Banco Inclinado';

-- Elevação Lateral Cruzada no Crossover
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Elevação Lateral Cruzada no Crossover';

-- Elevação Lateral Deitado de Lado no Banco (Deltoide Posterior)
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   secondary_muscles: (vazio) -> BACK
--   unilateral: não -> sim
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', secondary_muscles = ARRAY['BACK']::TEXT[], unilateral = true, level = 'BEGINNER' WHERE name = 'Elevação Lateral Deitado de Lado no Banco (Deltoide Posterior)';

-- Elevação Lateral Deitado de Lado no Banco Inclinado
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   unilateral: não -> sim
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', unilateral = true, level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Elevação Lateral Deitado de Lado no Banco Inclinado';

-- Elevação Lateral Isométrica com Toalha na Parede
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   equipment: DUMBBELL -> BODYWEIGHT
--   prescription_type: REPS -> TIME
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', equipment = 'BODYWEIGHT', prescription_type = 'TIME', level = 'BEGINNER' WHERE name = 'Elevação Lateral Isométrica com Toalha na Parede';

-- Elevação Lateral Sentado com Halteres
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Elevação Lateral Sentado com Halteres';

-- Elevação Lateral Unilateral Inclinado com Halter
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Elevação Lateral Unilateral Inclinado com Halter';

-- Elevação Lateral Unilateral Inclinado no Cabo
--   secondary_muscles: CORE -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Elevação Lateral Unilateral Inclinado no Cabo';

-- Elevação Lateral Unilateral com Halter
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Elevação Lateral Unilateral com Halter';

-- Elevação Lateral Unilateral no Cabo
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   unilateral: não -> sim
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', unilateral = true, level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Elevação Lateral Unilateral no Cabo';

-- Elevação Lateral com Cotovelos Flexionados
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Elevação Lateral com Cotovelos Flexionados';

-- Elevação Lateral com Halteres
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Elevação Lateral com Halteres';

-- Elevação Lateral com Kettlebells
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   is_compound: sim -> não
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', is_compound = false, level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Elevação Lateral com Kettlebells';

-- Elevação Lateral de Perna Deitado com Miniband
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   secondary_muscles: (vazio) -> LEGS
--   unilateral: não -> sim
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', secondary_muscles = ARRAY['LEGS']::TEXT[], unilateral = true WHERE name = 'Elevação Lateral de Perna Deitado com Miniband';

-- Elevação Lateral e Frontal com Halteres
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   secondary_muscles: (vazio) -> CHEST
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', secondary_muscles = ARRAY['CHEST']::TEXT[], level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Elevação Lateral e Frontal com Halteres';

-- Elevação Lateral na Máquina Sentado
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   is_compound: (vazio) -> não
--   equipment: (vazio) -> MACHINE
--   unilateral: (vazio) -> não
--   prescription_type: (vazio) -> REPS
--   level: (vazio) -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', is_compound = false, equipment = 'MACHINE', unilateral = false, prescription_type = 'REPS', level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Elevação Lateral na Máquina Sentado';

-- Elevação Lateral na Máquina de Frente para o Aparelho
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Elevação Lateral na Máquina de Frente para o Aparelho';

-- Elevação Lateral no Landmine
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   equipment: BARBELL -> LANDMINE
--   unilateral: não -> sim
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', equipment = 'LANDMINE', unilateral = true, contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Elevação Lateral no Landmine';

-- Elevação Lateral sem Carga
--   modality: STRENGTH -> MOBILITY
--   equipment: DUMBBELL -> BODYWEIGHT
--   secondary_muscles: CORE -> (vazio)
UPDATE exercises SET modality = 'MOBILITY', equipment = 'BODYWEIGHT', secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Elevação Lateral sem Carga';

-- Elevação Pélvica Unilateral com Barra
--   secondary_muscles: LEGS, CORE -> LEGS
UPDATE exercises SET secondary_muscles = ARRAY['LEGS']::TEXT[] WHERE name = 'Elevação Pélvica Unilateral com Barra';

-- Elevação Pélvica Unilateral no Banco
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HINGE
--   secondary_muscles: (vazio) -> LEGS
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HINGE', secondary_muscles = ARRAY['LEGS']::TEXT[], level = 'BEGINNER' WHERE name = 'Elevação Pélvica Unilateral no Banco';

-- Elevação Pélvica com Barra
--   secondary_muscles: LEGS, CORE -> LEGS
UPDATE exercises SET secondary_muscles = ARRAY['LEGS']::TEXT[] WHERE name = 'Elevação Pélvica com Barra';

-- Elevação Pélvica com Barra e Pés no Banco
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HINGE
--   is_compound: não -> sim
--   secondary_muscles: (vazio) -> LEGS
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HINGE', is_compound = true, secondary_muscles = ARRAY['LEGS']::TEXT[] WHERE name = 'Elevação Pélvica com Barra e Pés no Banco';

-- Elevação Pélvica com Faixa Elástica
--   secondary_muscles: LEGS, CORE -> LEGS
UPDATE exercises SET secondary_muscles = ARRAY['LEGS']::TEXT[] WHERE name = 'Elevação Pélvica com Faixa Elástica';

-- Elevação Pélvica com Peso Corporal
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HINGE
--   is_compound: (vazio) -> sim
--   equipment: (vazio) -> BODYWEIGHT
--   secondary_muscles: (vazio) -> LEGS
--   unilateral: (vazio) -> não
--   prescription_type: (vazio) -> REPS
--   level: (vazio) -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HINGE', is_compound = true, equipment = 'BODYWEIGHT', secondary_muscles = ARRAY['LEGS']::TEXT[], unilateral = false, prescription_type = 'REPS', level = 'BEGINNER' WHERE name = 'Elevação Pélvica com Peso Corporal';

-- Elevação Pélvica com Pés no Banco
--   secondary_muscles: CORE, LEGS -> LEGS
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET secondary_muscles = ARRAY['LEGS']::TEXT[], level = 'BEGINNER' WHERE name = 'Elevação Pélvica com Pés no Banco';

-- Elevação Pélvica na Cadeira Extensora
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HINGE
--   is_compound: não -> sim
--   secondary_muscles: (vazio) -> LEGS
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HINGE', is_compound = true, secondary_muscles = ARRAY['LEGS']::TEXT[] WHERE name = 'Elevação Pélvica na Cadeira Extensora';

-- Elevação Pélvica na Máquina
--   secondary_muscles: LEGS, CORE -> LEGS
UPDATE exercises SET secondary_muscles = ARRAY['LEGS']::TEXT[] WHERE name = 'Elevação Pélvica na Máquina';

-- Elevação Pélvica no Smith
--   equipment: MACHINE -> SMITH
--   secondary_muscles: LEGS, CORE -> LEGS
UPDATE exercises SET equipment = 'SMITH', secondary_muscles = ARRAY['LEGS']::TEXT[] WHERE name = 'Elevação Pélvica no Smith';

-- Elevação de Panturrilha Sentado com Anilha
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   equipment: DUMBBELL -> WEIGHT_PLATE
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', equipment = 'WEIGHT_PLATE', level = 'BEGINNER' WHERE name = 'Elevação de Panturrilha Sentado com Anilha';

-- Elevação de Panturrilha Sentado com Barra
--   secondary_muscles: CORE -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Elevação de Panturrilha Sentado com Barra';

-- Elevação de Panturrilha Unilateral no Hack
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   contraindications: (vazio) -> LUMBAR
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', contraindications = ARRAY['LUMBAR']::TEXT[] WHERE name = 'Elevação de Panturrilha Unilateral no Hack';

-- Elevação de Panturrilha Unilateral no Leg Press
--   secondary_muscles: CORE -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Elevação de Panturrilha Unilateral no Leg Press';

-- Elevação de Panturrilha com Faixa Elástica
--   secondary_muscles: CORE -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Elevação de Panturrilha com Faixa Elástica';

-- Elevação de Panturrilha em Pé com Barra
--   secondary_muscles: CORE -> (vazio)
--   level: BEGINNER -> INTERMEDIATE
--   contraindications: (vazio) -> LUMBAR
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[], level = 'INTERMEDIATE', contraindications = ARRAY['LUMBAR']::TEXT[] WHERE name = 'Elevação de Panturrilha em Pé com Barra';

-- Elevação de Panturrilha em Pé com Halteres
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   equipment: BODYWEIGHT -> DUMBBELL
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', equipment = 'DUMBBELL' WHERE name = 'Elevação de Panturrilha em Pé com Halteres';

-- Elevação de Panturrilha no Hack
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   contraindications: (vazio) -> LUMBAR
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', contraindications = ARRAY['LUMBAR']::TEXT[] WHERE name = 'Elevação de Panturrilha no Hack';

-- Elevação de Panturrilha no Leg Press
--   secondary_muscles: CORE -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Elevação de Panturrilha no Leg Press';

-- Elevação de Panturrilha no Leg Press Horizontal
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE' WHERE name = 'Elevação de Panturrilha no Leg Press Horizontal';

-- Elevação de Panturrilha no Smith
--   equipment: MACHINE -> SMITH
--   secondary_muscles: CORE -> (vazio)
--   contraindications: (vazio) -> LUMBAR
UPDATE exercises SET equipment = 'SMITH', secondary_muscles = ARRAY[]::TEXT[], contraindications = ARRAY['LUMBAR']::TEXT[] WHERE name = 'Elevação de Panturrilha no Smith';

-- Elevação de Panturrilha no Step com Halteres
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   equipment: BODYWEIGHT -> DUMBBELL
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', equipment = 'DUMBBELL' WHERE name = 'Elevação de Panturrilha no Step com Halteres';

-- Elevação de Perna Estendida em Pé com Faixa Elástica
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE' WHERE name = 'Elevação de Perna Estendida em Pé com Faixa Elástica';

-- Elevação em 3 Direções com Halteres
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> VERTICAL_PUSH
--   is_compound: não -> sim
--   secondary_muscles: (vazio) -> TRICEPS, CHEST
--   contraindications: (vazio) -> SHOULDER, LUMBAR
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'VERTICAL_PUSH', is_compound = true, secondary_muscles = ARRAY['TRICEPS', 'CHEST']::TEXT[], contraindications = ARRAY['SHOULDER', 'LUMBAR']::TEXT[] WHERE name = 'Elevação em 3 Direções com Halteres';

-- Elevação em 4 Direções com Halteres
--   secondary_muscles: BACK, CORE -> CHEST, BACK
--   level: BEGINNER -> INTERMEDIATE
--   contraindications: (vazio) -> SHOULDER, LUMBAR
UPDATE exercises SET secondary_muscles = ARRAY['CHEST', 'BACK']::TEXT[], level = 'INTERMEDIATE', contraindications = ARRAY['SHOULDER', 'LUMBAR']::TEXT[] WHERE name = 'Elevação em 4 Direções com Halteres';

-- Elevação em T Apoiado no Banco Inclinado
--   secondary_muscles: SHOULDERS, BACK -> SHOULDERS
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS']::TEXT[], level = 'BEGINNER' WHERE name = 'Elevação em T Apoiado no Banco Inclinado';

-- Elevação em Y Apoiado no Banco Inclinado
--   secondary_muscles: SHOULDERS, BACK -> SHOULDERS
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS']::TEXT[], level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Elevação em Y Apoiado no Banco Inclinado';

-- Elevação em Y no Cabo
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   is_compound: sim -> não
--   secondary_muscles: (vazio) -> SHOULDERS
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', is_compound = false, secondary_muscles = ARRAY['SHOULDERS']::TEXT[], level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Elevação em Y no Cabo';

-- Elíptico
--   secondary_muscles: GLUTES, CORE, BACK -> GLUTES, BACK, SHOULDERS
UPDATE exercises SET secondary_muscles = ARRAY['GLUTES', 'BACK', 'SHOULDERS']::TEXT[] WHERE name = 'Elíptico';

-- Encolhimento Apoiado no Banco Inclinado com Halteres
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', level = 'BEGINNER' WHERE name = 'Encolhimento Apoiado no Banco Inclinado com Halteres';

-- Encolhimento Sentado de Gittleson com Halteres
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', level = 'BEGINNER' WHERE name = 'Encolhimento Sentado de Gittleson com Halteres';

-- Encolhimento Sentado no Banco Inclinado
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', level = 'BEGINNER' WHERE name = 'Encolhimento Sentado no Banco Inclinado';

-- Encolhimento acima da Cabeça
--   secondary_muscles: SHOULDERS, CORE -> (vazio)
--   contraindications: SHOULDER -> SHOULDER, LUMBAR
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[], contraindications = ARRAY['SHOULDER', 'LUMBAR']::TEXT[] WHERE name = 'Encolhimento acima da Cabeça';

-- Encolhimento com Barra
--   secondary_muscles: BACK -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Encolhimento com Barra';

-- Encolhimento com Barra atrás do Corpo
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', level = 'BEGINNER' WHERE name = 'Encolhimento com Barra atrás do Corpo';

-- Encolhimento com Barra atrás do Corpo no Rack
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   is_compound: não -> sim
--   secondary_muscles: (vazio) -> SHOULDERS, BICEPS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', is_compound = true, secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Encolhimento com Barra atrás do Corpo no Rack';

-- Encolhimento com Halteres
--   secondary_muscles: BACK -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Encolhimento com Halteres';

-- Encolhimento na Máquina
--   secondary_muscles: BACK -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Encolhimento na Máquina';

-- Encolhimento na Máquina (Alavanca)
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   is_compound: (vazio) -> não
--   equipment: (vazio) -> MACHINE
--   unilateral: (vazio) -> não
--   prescription_type: (vazio) -> REPS
--   level: (vazio) -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', is_compound = false, equipment = 'MACHINE', unilateral = false, prescription_type = 'REPS', level = 'BEGINNER' WHERE name = 'Encolhimento na Máquina (Alavanca)';

-- Encolhimento no Cabo
--   secondary_muscles: BACK -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Encolhimento no Cabo';

-- Encolhimento no Smith
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', level = 'BEGINNER' WHERE name = 'Encolhimento no Smith';

-- Engatinhar do Urso (Bear Crawl)
--   modality: (vazio) -> CARDIO
--   primary_muscles: CORE, SHOULDERS -> SHOULDERS
--   secondary_muscles: LEGS, GLUTES -> LEGS, TRICEPS
--   contraindications: (vazio) -> SHOULDER, WRIST
UPDATE exercises SET modality = 'CARDIO', primary_muscles = ARRAY['SHOULDERS']::TEXT[], secondary_muscles = ARRAY['LEGS', 'TRICEPS']::TEXT[], contraindications = ARRAY['SHOULDER', 'WRIST']::TEXT[] WHERE name = 'Engatinhar do Urso (Bear Crawl)';

-- Ergômetro de Braço
--   secondary_muscles: CHEST, CORE -> CHEST
UPDATE exercises SET secondary_muscles = ARRAY['CHEST']::TEXT[] WHERE name = 'Ergômetro de Braço';

-- Escada de Agilidade
--   secondary_muscles: CORE -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Escada de Agilidade';

-- Escada de Agilidade Lateral
--   modality: (vazio) -> CARDIO
--   movement_pattern: (vazio) -> GAIT
--   equipment: BODYWEIGHT -> AGILITY_LADDER
--   contraindications: (vazio) -> IMPACT
UPDATE exercises SET modality = 'CARDIO', movement_pattern = 'GAIT', equipment = 'AGILITY_LADDER', contraindications = ARRAY['IMPACT']::TEXT[] WHERE name = 'Escada de Agilidade Lateral';

-- Escalador em Pé
--   movement_pattern: HORIZONTAL_PUSH -> GAIT
--   primary_muscles: CORE -> LEGS
--   secondary_muscles: CHEST, SHOULDERS, LEGS -> SHOULDERS
UPDATE exercises SET movement_pattern = 'GAIT', primary_muscles = ARRAY['LEGS']::TEXT[], secondary_muscles = ARRAY['SHOULDERS']::TEXT[] WHERE name = 'Escalador em Pé';

-- Esquiador com Gymstick
--   modality: (vazio) -> STRENGTH
--   movement_pattern: SQUAT -> VERTICAL_PULL
--   is_compound: sim -> não
--   primary_muscles: LEGS, CORE -> BACK
--   secondary_muscles: BACK, SHOULDERS -> SHOULDERS, TRICEPS
--   unilateral: sim -> não
--   contraindications: (vazio) -> LUMBAR
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'VERTICAL_PULL', is_compound = false, primary_muscles = ARRAY['BACK']::TEXT[], secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], unilateral = false, contraindications = ARRAY['LUMBAR']::TEXT[] WHERE name = 'Esquiador com Gymstick';

-- Exercício Pliométrico em X
--   modality: (vazio) -> CARDIO
--   movement_pattern: (vazio) -> GAIT
--   contraindications: (vazio) -> IMPACT
UPDATE exercises SET modality = 'CARDIO', movement_pattern = 'GAIT', contraindications = ARRAY['IMPACT']::TEXT[] WHERE name = 'Exercício Pliométrico em X';

-- Exercício da Bailarina Sentado
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> NONE
--   primary_muscles: CORE -> SHOULDERS
--   secondary_muscles: (vazio) -> BACK
--   prescription_type: TIME -> REPS
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'NONE', primary_muscles = ARRAY['SHOULDERS']::TEXT[], secondary_muscles = ARRAY['BACK']::TEXT[], prescription_type = 'REPS', level = 'BEGINNER' WHERE name = 'Exercício da Bailarina Sentado';

-- Extensão Lombar Sentado na Máquina
--   categoria: CORE -> LOWER_BACK
--   primary_muscles: CORE -> BACK
--   secondary_muscles: BACK, GLUTES -> GLUTES
UPDATE exercises SET category = 'LOWER_BACK', primary_muscles = ARRAY['BACK']::TEXT[], secondary_muscles = ARRAY['GLUTES']::TEXT[] WHERE name = 'Extensão Lombar Sentado na Máquina';

-- Extensão de Ombro com Faixa Elástica
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> VERTICAL_PULL
--   primary_muscles: SHOULDERS -> BACK
--   secondary_muscles: (vazio) -> SHOULDERS
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'VERTICAL_PULL', primary_muscles = ARRAY['BACK']::TEXT[], secondary_muscles = ARRAY['SHOULDERS']::TEXT[] WHERE name = 'Extensão de Ombro com Faixa Elástica';

-- Extensão de Quadril Ajoelhado com Faixa Elástica
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HINGE
--   secondary_muscles: (vazio) -> LEGS
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> KNEE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HINGE', secondary_muscles = ARRAY['LEGS']::TEXT[], level = 'BEGINNER', contraindications = ARRAY['KNEE']::TEXT[] WHERE name = 'Extensão de Quadril Ajoelhado com Faixa Elástica';

-- Extensão de Quadril Alternada Deitado no Banco
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   is_compound: sim -> não
--   secondary_muscles: (vazio) -> LEGS
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', is_compound = false, secondary_muscles = ARRAY['LEGS']::TEXT[], level = 'BEGINNER' WHERE name = 'Extensão de Quadril Alternada Deitado no Banco';

-- Extensão de Quadril Apoiado no Banco
--   movement_pattern: HINGE -> NONE
--   is_compound: sim -> não
--   secondary_muscles: LEGS, CORE -> LEGS
--   unilateral: não -> sim
--   contraindications: LUMBAR -> (vazio)
UPDATE exercises SET movement_pattern = 'NONE', is_compound = false, secondary_muscles = ARRAY['LEGS']::TEXT[], unilateral = true, contraindications = ARRAY[]::TEXT[] WHERE name = 'Extensão de Quadril Apoiado no Banco';

-- Extensão de Quadril Deitado no Smith
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   primary_muscles: LEGS -> GLUTES
--   secondary_muscles: (vazio) -> LEGS
--   contraindications: (vazio) -> LUMBAR
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', primary_muscles = ARRAY['GLUTES']::TEXT[], secondary_muscles = ARRAY['LEGS']::TEXT[], contraindications = ARRAY['LUMBAR']::TEXT[] WHERE name = 'Extensão de Quadril Deitado no Smith';

-- Extensão de Quadril com Perna Estendida em Quatro Apoios
--   movement_pattern: KNEE_EXTENSION -> NONE
--   equipment: MACHINE -> BODYWEIGHT
--   primary_muscles: LEGS -> GLUTES
--   secondary_muscles: (vazio) -> LEGS
--   unilateral: não -> sim
--   contraindications: KNEE -> (vazio)
UPDATE exercises SET movement_pattern = 'NONE', equipment = 'BODYWEIGHT', primary_muscles = ARRAY['GLUTES']::TEXT[], secondary_muscles = ARRAY['LEGS']::TEXT[], unilateral = true, contraindications = ARRAY[]::TEXT[] WHERE name = 'Extensão de Quadril com Perna Estendida em Quatro Apoios';

-- Extensão de Quadril em Pé na Máquina (Alavanca)
--   secondary_muscles: LEGS, CORE -> LEGS
UPDATE exercises SET secondary_muscles = ARRAY['LEGS']::TEXT[] WHERE name = 'Extensão de Quadril em Pé na Máquina (Alavanca)';

-- Extensão de Tríceps Ajoelhado no Cabo Apoiado no Banco
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Extensão de Tríceps Ajoelhado no Cabo Apoiado no Banco';

-- Extensão de Tríceps Horizontal com Faixa Elástica
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   unilateral: não -> sim
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', unilateral = true WHERE name = 'Extensão de Tríceps Horizontal com Faixa Elástica';

-- Extensão de Tríceps Horizontal no Cabo
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Extensão de Tríceps Horizontal no Cabo';

-- Extensão de Tríceps Lateral no Cabo
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   unilateral: não -> sim
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', unilateral = true, level = 'BEGINNER' WHERE name = 'Extensão de Tríceps Lateral no Cabo';

-- Extensão de Tríceps Unilateral Ajoelhado no Cabo
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   unilateral: não -> sim
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', unilateral = true, level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Extensão de Tríceps Unilateral Ajoelhado no Cabo';

-- Extensão de Tríceps com Cabos Cruzados
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', level = 'BEGINNER' WHERE name = 'Extensão de Tríceps com Cabos Cruzados';

-- Extensão de Tríceps com Peso Corporal na Barra
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   is_compound: sim -> não
--   primary_muscles: BACK -> TRICEPS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', is_compound = false, primary_muscles = ARRAY['TRICEPS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Extensão de Tríceps com Peso Corporal na Barra';

-- Extensão de Tríceps com Peso Corporal no Solo
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   equipment: DUMBBELL -> BODYWEIGHT
--   contraindications: (vazio) -> WRIST
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', equipment = 'BODYWEIGHT', contraindications = ARRAY['WRIST']::TEXT[] WHERE name = 'Extensão de Tríceps com Peso Corporal no Solo';

-- Extensão de Tríceps na Máquina
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE' WHERE name = 'Extensão de Tríceps na Máquina';

-- Extensão de Tríceps na Máquina Pegada Neutra
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE' WHERE name = 'Extensão de Tríceps na Máquina Pegada Neutra';

-- Face Pull (Puxada para o Rosto)
--   secondary_muscles: SHOULDERS, BACK -> BACK, BICEPS
UPDATE exercises SET secondary_muscles = ARRAY['BACK', 'BICEPS']::TEXT[] WHERE name = 'Face Pull (Puxada para o Rosto)';

-- Face Pull Semiajoelhado no Cabo
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PULL
--   secondary_muscles: BACK -> BACK, BICEPS
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PULL', secondary_muscles = ARRAY['BACK', 'BICEPS']::TEXT[], level = 'BEGINNER' WHERE name = 'Face Pull Semiajoelhado no Cabo';

-- Face Pull com Cabos Cruzados
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PULL
--   secondary_muscles: BACK -> BACK, BICEPS
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PULL', secondary_muscles = ARRAY['BACK', 'BICEPS']::TEXT[], level = 'BEGINNER' WHERE name = 'Face Pull com Cabos Cruzados';

-- Flexora em Pé Unilateral
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> KNEE_FLEXION
--   contraindications: (vazio) -> KNEE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'KNEE_FLEXION', contraindications = ARRAY['KNEE']::TEXT[] WHERE name = 'Flexora em Pé Unilateral';

-- Flexão
--   secondary_muscles: SHOULDERS, CORE -> SHOULDERS, TRICEPS
--   contraindications: (vazio) -> SHOULDER, WRIST
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], contraindications = ARRAY['SHOULDER', 'WRIST']::TEXT[] WHERE name = 'Flexão';

-- Flexão Declinada
--   secondary_muscles: SHOULDERS, CORE -> SHOULDERS, TRICEPS
--   contraindications: (vazio) -> SHOULDER, WRIST
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], contraindications = ARRAY['SHOULDER', 'WRIST']::TEXT[] WHERE name = 'Flexão Declinada';

-- Flexão Declinada na Bola Suíça
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   secondary_muscles: (vazio) -> SHOULDERS, TRICEPS
--   contraindications: (vazio) -> SHOULDER, WRIST
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], contraindications = ARRAY['SHOULDER', 'WRIST']::TEXT[] WHERE name = 'Flexão Declinada na Bola Suíça';

-- Flexão Diamante
--   primary_muscles: TRICEPS -> CHEST
--   secondary_muscles: CHEST, SHOULDERS -> TRICEPS, SHOULDERS
--   contraindications: WRIST -> SHOULDER, WRIST
UPDATE exercises SET primary_muscles = ARRAY['CHEST']::TEXT[], secondary_muscles = ARRAY['TRICEPS', 'SHOULDERS']::TEXT[], contraindications = ARRAY['SHOULDER', 'WRIST']::TEXT[] WHERE name = 'Flexão Diamante';

-- Flexão Diamante com Joelhos Apoiados
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   secondary_muscles: (vazio) -> TRICEPS, SHOULDERS
--   contraindications: (vazio) -> SHOULDER, WRIST
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', secondary_muscles = ARRAY['TRICEPS', 'SHOULDERS']::TEXT[], contraindications = ARRAY['SHOULDER', 'WRIST']::TEXT[] WHERE name = 'Flexão Diamante com Joelhos Apoiados';

-- Flexão Dive Bomber
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   secondary_pattern: (vazio) -> VERTICAL_PUSH
--   primary_muscles: CHEST -> CHEST, SHOULDERS
--   secondary_muscles: (vazio) -> TRICEPS
--   contraindications: (vazio) -> SHOULDER, WRIST, LUMBAR
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', secondary_pattern = 'VERTICAL_PUSH', primary_muscles = ARRAY['CHEST', 'SHOULDERS']::TEXT[], secondary_muscles = ARRAY['TRICEPS']::TEXT[], contraindications = ARRAY['SHOULDER', 'WRIST', 'LUMBAR']::TEXT[] WHERE name = 'Flexão Dive Bomber';

-- Flexão Escapular
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   primary_muscles: CHEST -> SHOULDERS
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> WRIST
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', primary_muscles = ARRAY['SHOULDERS']::TEXT[], level = 'BEGINNER', contraindications = ARRAY['WRIST']::TEXT[] WHERE name = 'Flexão Escapular';

-- Flexão Fechada com Joelhos Apoiados
--   secondary_muscles: SHOULDERS, CORE -> TRICEPS, SHOULDERS
--   contraindications: (vazio) -> SHOULDER, WRIST
UPDATE exercises SET secondary_muscles = ARRAY['TRICEPS', 'SHOULDERS']::TEXT[], contraindications = ARRAY['SHOULDER', 'WRIST']::TEXT[] WHERE name = 'Flexão Fechada com Joelhos Apoiados';

-- Flexão Fechada na Bola Medicinal
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   secondary_muscles: (vazio) -> TRICEPS, SHOULDERS
--   contraindications: (vazio) -> SHOULDER, WRIST
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', secondary_muscles = ARRAY['TRICEPS', 'SHOULDERS']::TEXT[], contraindications = ARRAY['SHOULDER', 'WRIST']::TEXT[] WHERE name = 'Flexão Fechada na Bola Medicinal';

-- Flexão Hindu
--   modality: (vazio) -> STRENGTH
--   secondary_pattern: (vazio) -> VERTICAL_PUSH
--   secondary_muscles: CORE -> TRICEPS
--   contraindications: LUMBAR, SHOULDER -> SHOULDER, WRIST, LUMBAR
UPDATE exercises SET modality = 'STRENGTH', secondary_pattern = 'VERTICAL_PUSH', secondary_muscles = ARRAY['TRICEPS']::TEXT[], contraindications = ARRAY['SHOULDER', 'WRIST', 'LUMBAR']::TEXT[] WHERE name = 'Flexão Hindu';

-- Flexão Hindu Modificada
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   secondary_pattern: (vazio) -> VERTICAL_PUSH
--   primary_muscles: CHEST -> CHEST, SHOULDERS
--   secondary_muscles: (vazio) -> TRICEPS
--   contraindications: (vazio) -> SHOULDER, WRIST, LUMBAR
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', secondary_pattern = 'VERTICAL_PUSH', primary_muscles = ARRAY['CHEST', 'SHOULDERS']::TEXT[], secondary_muscles = ARRAY['TRICEPS']::TEXT[], contraindications = ARRAY['SHOULDER', 'WRIST', 'LUMBAR']::TEXT[] WHERE name = 'Flexão Hindu Modificada';

-- Flexão Inclinada
--   secondary_muscles: SHOULDERS, CORE -> SHOULDERS, TRICEPS
--   contraindications: (vazio) -> SHOULDER, WRIST
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], contraindications = ARRAY['SHOULDER', 'WRIST']::TEXT[] WHERE name = 'Flexão Inclinada';

-- Flexão Pike
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> VERTICAL_PUSH
--   primary_muscles: CHEST -> SHOULDERS
--   secondary_muscles: (vazio) -> TRICEPS
--   contraindications: (vazio) -> SHOULDER, WRIST
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'VERTICAL_PUSH', primary_muscles = ARRAY['SHOULDERS']::TEXT[], secondary_muscles = ARRAY['TRICEPS']::TEXT[], contraindications = ARRAY['SHOULDER', 'WRIST']::TEXT[] WHERE name = 'Flexão Pike';

-- Flexão Pike Profunda entre Cadeiras
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> VERTICAL_PUSH
--   primary_muscles: LEGS -> SHOULDERS
--   secondary_muscles: (vazio) -> TRICEPS
--   level: BEGINNER -> ADVANCED
--   contraindications: (vazio) -> SHOULDER, WRIST
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'VERTICAL_PUSH', primary_muscles = ARRAY['SHOULDERS']::TEXT[], secondary_muscles = ARRAY['TRICEPS']::TEXT[], level = 'ADVANCED', contraindications = ARRAY['SHOULDER', 'WRIST']::TEXT[] WHERE name = 'Flexão Pike Profunda entre Cadeiras';

-- Flexão Pike com Mãos no Banco
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> VERTICAL_PUSH
--   primary_muscles: CHEST -> SHOULDERS
--   secondary_muscles: (vazio) -> TRICEPS
--   contraindications: (vazio) -> SHOULDER, WRIST
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'VERTICAL_PUSH', primary_muscles = ARRAY['SHOULDERS']::TEXT[], secondary_muscles = ARRAY['TRICEPS']::TEXT[], contraindications = ARRAY['SHOULDER', 'WRIST']::TEXT[] WHERE name = 'Flexão Pike com Mãos no Banco';

-- Flexão Plantar em Pé
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> NONE
--   prescription_type: TIME -> REPS
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'NONE', prescription_type = 'REPS', level = 'BEGINNER' WHERE name = 'Flexão Plantar em Pé';

-- Flexão Plus
--   primary_muscles: CHEST, SHOULDERS -> CHEST
--   secondary_muscles: CORE -> SHOULDERS, TRICEPS
--   contraindications: (vazio) -> SHOULDER, WRIST
UPDATE exercises SET primary_muscles = ARRAY['CHEST']::TEXT[], secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], contraindications = ARRAY['SHOULDER', 'WRIST']::TEXT[] WHERE name = 'Flexão Plus';

-- Flexão Profunda com Kettlebells
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   secondary_muscles: (vazio) -> SHOULDERS, TRICEPS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Flexão Profunda com Kettlebells';

-- Flexão Reversa Apoiada nos Cotovelos
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PULL
--   is_compound: sim -> não
--   primary_muscles: TRICEPS -> BACK
--   secondary_muscles: (vazio) -> SHOULDERS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PULL', is_compound = false, primary_muscles = ARRAY['BACK']::TEXT[], secondary_muscles = ARRAY['SHOULDERS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Flexão Reversa Apoiada nos Cotovelos';

-- Flexão Unilateral na Bola Medicinal
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   secondary_muscles: (vazio) -> SHOULDERS, TRICEPS
--   contraindications: (vazio) -> SHOULDER, WRIST
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], contraindications = ARRAY['SHOULDER', 'WRIST']::TEXT[] WHERE name = 'Flexão Unilateral na Bola Medicinal';

-- Flexão com Apoios (Push-Up Bars)
--   modality: (vazio) -> STRENGTH
--   secondary_muscles: SHOULDERS, CORE -> SHOULDERS, TRICEPS
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Flexão com Apoios (Push-Up Bars)';

-- Flexão com Cobra
--   modality: MOBILITY -> STRENGTH
--   movement_pattern: STRETCH -> HORIZONTAL_PUSH
--   primary_muscles: CORE -> CHEST
--   secondary_muscles: BACK, CHEST -> TRICEPS, SHOULDERS
--   prescription_type: TIME -> REPS
--   contraindications: LUMBAR -> SHOULDER, WRIST, LUMBAR
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', primary_muscles = ARRAY['CHEST']::TEXT[], secondary_muscles = ARRAY['TRICEPS', 'SHOULDERS']::TEXT[], prescription_type = 'REPS', contraindications = ARRAY['SHOULDER', 'WRIST', 'LUMBAR']::TEXT[] WHERE name = 'Flexão com Cobra';

-- Flexão com Elevação de Braço
--   categoria: TRICEPS -> CALISTHENICS
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   is_compound: não -> sim
--   secondary_muscles: (vazio) -> SHOULDERS, TRICEPS
--   contraindications: (vazio) -> SHOULDER, WRIST
UPDATE exercises SET category = 'CALISTHENICS', modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', is_compound = true, secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], contraindications = ARRAY['SHOULDER', 'WRIST']::TEXT[] WHERE name = 'Flexão com Elevação de Braço';

-- Flexão com Joelhos Apoiados
--   categoria: CHEST -> CALISTHENICS
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   secondary_muscles: (vazio) -> SHOULDERS, TRICEPS
--   contraindications: (vazio) -> SHOULDER, WRIST
UPDATE exercises SET category = 'CALISTHENICS', modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], contraindications = ARRAY['SHOULDER', 'WRIST']::TEXT[] WHERE name = 'Flexão com Joelhos Apoiados';

-- Flexão com Mãos Cruzadas
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   secondary_muscles: (vazio) -> TRICEPS, SHOULDERS
--   contraindications: (vazio) -> SHOULDER, WRIST
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', secondary_muscles = ARRAY['TRICEPS', 'SHOULDERS']::TEXT[], contraindications = ARRAY['SHOULDER', 'WRIST']::TEXT[] WHERE name = 'Flexão com Mãos Cruzadas';

-- Flexão com Mãos Invertidas
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   is_compound: (vazio) -> sim
--   equipment: (vazio) -> BODYWEIGHT
--   secondary_muscles: (vazio) -> SHOULDERS, TRICEPS
--   unilateral: (vazio) -> não
--   prescription_type: (vazio) -> REPS
--   level: (vazio) -> INTERMEDIATE
--   contraindications: (vazio) -> SHOULDER, WRIST
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', is_compound = true, equipment = 'BODYWEIGHT', secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], unilateral = false, prescription_type = 'REPS', level = 'INTERMEDIATE', contraindications = ARRAY['SHOULDER', 'WRIST']::TEXT[] WHERE name = 'Flexão com Mãos Invertidas';

-- Flexão com Mãos na Bola Suíça
--   modality: (vazio) -> STRENGTH
--   secondary_muscles: SHOULDERS, CORE -> SHOULDERS, TRICEPS
--   contraindications: (vazio) -> SHOULDER, WRIST
UPDATE exercises SET modality = 'STRENGTH', secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], contraindications = ARRAY['SHOULDER', 'WRIST']::TEXT[] WHERE name = 'Flexão com Mãos na Bola Suíça';

-- Flexão com Palma
--   secondary_muscles: SHOULDERS, CORE -> SHOULDERS, TRICEPS
--   contraindications: IMPACT, WRIST -> SHOULDER, WRIST, IMPACT
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], contraindications = ARRAY['SHOULDER', 'WRIST', 'IMPACT']::TEXT[] WHERE name = 'Flexão com Palma';

-- Flexão com Peso
--   modality: (vazio) -> STRENGTH
--   secondary_muscles: SHOULDERS -> SHOULDERS, TRICEPS
--   contraindications: (vazio) -> SHOULDER, WRIST, LUMBAR
UPDATE exercises SET modality = 'STRENGTH', secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], contraindications = ARRAY['SHOULDER', 'WRIST', 'LUMBAR']::TEXT[] WHERE name = 'Flexão com Peso';

-- Flexão com Punhos Fechados
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   is_compound: não -> sim
--   primary_muscles: FOREARMS -> CHEST
--   secondary_muscles: (vazio) -> SHOULDERS, TRICEPS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', is_compound = true, primary_muscles = ARRAY['CHEST']::TEXT[], secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Flexão com Punhos Fechados';

-- Flexão com Queda entre Bancos
--   modality: (vazio) -> PLYOMETRIC
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   secondary_muscles: (vazio) -> SHOULDERS, TRICEPS
--   level: INTERMEDIATE -> ADVANCED
--   contraindications: (vazio) -> SHOULDER, WRIST, IMPACT
UPDATE exercises SET modality = 'PLYOMETRIC', movement_pattern = 'HORIZONTAL_PUSH', secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], level = 'ADVANCED', contraindications = ARRAY['SHOULDER', 'WRIST', 'IMPACT']::TEXT[] WHERE name = 'Flexão com Queda entre Bancos';

-- Flexão com Rotação (Flexão em T)
--   secondary_pattern: (vazio) -> ROTATION
--   secondary_muscles: CORE, SHOULDERS -> SHOULDERS, TRICEPS, CORE
--   unilateral: sim -> não
--   contraindications: (vazio) -> SHOULDER, WRIST
UPDATE exercises SET secondary_pattern = 'ROTATION', secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS', 'CORE']::TEXT[], unilateral = false, contraindications = ARRAY['SHOULDER', 'WRIST']::TEXT[] WHERE name = 'Flexão com Rotação (Flexão em T)';

-- Flexão com Toque no Ombro
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   secondary_muscles: (vazio) -> SHOULDERS, TRICEPS
--   contraindications: (vazio) -> SHOULDER, WRIST
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], contraindications = ARRAY['SHOULDER', 'WRIST']::TEXT[] WHERE name = 'Flexão com Toque no Ombro';

-- Flexão com Toque no Peito
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   secondary_muscles: (vazio) -> SHOULDERS, TRICEPS
--   contraindications: (vazio) -> SHOULDER, WRIST
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], contraindications = ARRAY['SHOULDER', 'WRIST']::TEXT[] WHERE name = 'Flexão com Toque no Peito';

-- Flexão com Toque nos Pés
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   secondary_muscles: (vazio) -> SHOULDERS, TRICEPS
--   contraindications: (vazio) -> SHOULDER, WRIST
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], contraindications = ARRAY['SHOULDER', 'WRIST']::TEXT[] WHERE name = 'Flexão com Toque nos Pés';

-- Flexão com Um Braço
--   secondary_muscles: SHOULDERS, CORE -> SHOULDERS, TRICEPS
--   contraindications: SHOULDER -> SHOULDER, WRIST
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], contraindications = ARRAY['SHOULDER', 'WRIST']::TEXT[] WHERE name = 'Flexão com Um Braço';

-- Flexão com Um Braço Assistida no Banco
--   secondary_muscles: SHOULDERS, CORE -> SHOULDERS, TRICEPS
--   level: ADVANCED -> INTERMEDIATE
--   contraindications: SHOULDER -> SHOULDER, WRIST
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], level = 'INTERMEDIATE', contraindications = ARRAY['SHOULDER', 'WRIST']::TEXT[] WHERE name = 'Flexão com Um Braço Assistida no Banco';

-- Flexão com Um Braço na Bola Medicinal
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   secondary_muscles: (vazio) -> SHOULDERS, TRICEPS
--   level: INTERMEDIATE -> ADVANCED
--   contraindications: (vazio) -> SHOULDER, WRIST
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], level = 'ADVANCED', contraindications = ARRAY['SHOULDER', 'WRIST']::TEXT[] WHERE name = 'Flexão com Um Braço na Bola Medicinal';

-- Flexão com Uma Perna Elevada
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   secondary_muscles: (vazio) -> SHOULDERS, TRICEPS
--   unilateral: sim -> não
--   level: ADVANCED -> INTERMEDIATE
--   contraindications: (vazio) -> SHOULDER, WRIST
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], unilateral = false, level = 'INTERMEDIATE', contraindications = ARRAY['SHOULDER', 'WRIST']::TEXT[] WHERE name = 'Flexão com Uma Perna Elevada';

-- Flexão de Ombro Alternada na Parede
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> NONE
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'NONE', level = 'BEGINNER' WHERE name = 'Flexão de Ombro Alternada na Parede';

-- Flexão de Ombro com Faixa Elástica
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   is_compound: sim -> não
--   secondary_muscles: (vazio) -> CHEST
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', is_compound = false, secondary_muscles = ARRAY['CHEST']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Flexão de Ombro com Faixa Elástica';

-- Flexão de Perna em Pé com Faixa Elástica
--   secondary_muscles: GLUTES, CORE -> (vazio)
--   unilateral: não -> sim
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[], unilateral = true WHERE name = 'Flexão de Perna em Pé com Faixa Elástica';

-- Flexão de Pernas Declinado com Halter
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> KNEE_FLEXION
--   contraindications: (vazio) -> KNEE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'KNEE_FLEXION', contraindications = ARRAY['KNEE']::TEXT[] WHERE name = 'Flexão de Pernas Declinado com Halter';

-- Flexão de Pernas Deitado com Faixa Elástica
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> KNEE_FLEXION
--   primary_muscles: GLUTES -> LEGS
--   contraindications: (vazio) -> KNEE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'KNEE_FLEXION', primary_muscles = ARRAY['LEGS']::TEXT[], contraindications = ARRAY['KNEE']::TEXT[] WHERE name = 'Flexão de Pernas Deitado com Faixa Elástica';

-- Flexão de Pernas Deitado com Halter
--   categoria: CALISTHENICS -> LEGS
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> KNEE_FLEXION
--   contraindications: (vazio) -> KNEE
UPDATE exercises SET category = 'LEGS', modality = 'STRENGTH', movement_pattern = 'KNEE_FLEXION', contraindications = ARRAY['KNEE']::TEXT[] WHERE name = 'Flexão de Pernas Deitado com Halter';

-- Flexão de Pernas Deslizante com Toalha
--   secondary_muscles: GLUTES, CORE -> GLUTES
--   level: ADVANCED -> INTERMEDIATE
UPDATE exercises SET secondary_muscles = ARRAY['GLUTES']::TEXT[], level = 'INTERMEDIATE' WHERE name = 'Flexão de Pernas Deslizante com Toalha';

-- Flexão de Pernas na Bola Suíça
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> KNEE_FLEXION
--   secondary_muscles: (vazio) -> GLUTES
--   contraindications: (vazio) -> KNEE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'KNEE_FLEXION', secondary_muscles = ARRAY['GLUTES']::TEXT[], contraindications = ARRAY['KNEE']::TEXT[] WHERE name = 'Flexão de Pernas na Bola Suíça';

-- Flexão de Quadril em Pé com Faixa Elástica
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE' WHERE name = 'Flexão de Quadril em Pé com Faixa Elástica';

-- Flexão de Quadril em Pé na Máquina (Alavanca)
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE' WHERE name = 'Flexão de Quadril em Pé na Máquina (Alavanca)';

-- Flexão em Parada de Mão
--   modality: (vazio) -> STRENGTH
--   secondary_muscles: CORE, CHEST -> TRICEPS
UPDATE exercises SET modality = 'STRENGTH', secondary_muscles = ARRAY['TRICEPS']::TEXT[] WHERE name = 'Flexão em Parada de Mão';

-- Flexão em Parada de Mão com Déficit entre Bancos
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> VERTICAL_PUSH
--   primary_muscles: CHEST -> SHOULDERS
--   secondary_muscles: (vazio) -> TRICEPS
--   level: INTERMEDIATE -> ADVANCED
--   contraindications: (vazio) -> SHOULDER, WRIST
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'VERTICAL_PUSH', primary_muscles = ARRAY['SHOULDERS']::TEXT[], secondary_muscles = ARRAY['TRICEPS']::TEXT[], level = 'ADVANCED', contraindications = ARRAY['SHOULDER', 'WRIST']::TEXT[] WHERE name = 'Flexão em Parada de Mão com Déficit entre Bancos';

-- Flexão em Parada de Mão com Kipping
--   modality: (vazio) -> STRENGTH
--   secondary_muscles: CHEST, CORE -> TRICEPS, GLUTES
UPDATE exercises SET modality = 'STRENGTH', secondary_muscles = ARRAY['TRICEPS', 'GLUTES']::TEXT[] WHERE name = 'Flexão em Parada de Mão com Kipping';

-- Flexão em Parada de Mão na Parede
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> VERTICAL_PUSH
--   primary_muscles: CHEST -> SHOULDERS
--   secondary_muscles: (vazio) -> TRICEPS
--   level: BEGINNER -> ADVANCED
--   contraindications: (vazio) -> SHOULDER, WRIST
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'VERTICAL_PUSH', primary_muscles = ARRAY['SHOULDERS']::TEXT[], secondary_muscles = ARRAY['TRICEPS']::TEXT[], level = 'ADVANCED', contraindications = ARRAY['SHOULDER', 'WRIST']::TEXT[] WHERE name = 'Flexão em Parada de Mão na Parede';

-- Flexão em Planche
--   modality: (vazio) -> STRENGTH
--   secondary_muscles: CORE -> TRICEPS
UPDATE exercises SET modality = 'STRENGTH', secondary_muscles = ARRAY['TRICEPS']::TEXT[] WHERE name = 'Flexão em Planche';

-- Flexão na Parede
--   secondary_muscles: SHOULDERS -> SHOULDERS, TRICEPS
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[] WHERE name = 'Flexão na Parede';

-- Flexão na Parede Pegada Fechada
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   secondary_muscles: (vazio) -> TRICEPS, SHOULDERS
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', secondary_muscles = ARRAY['TRICEPS', 'SHOULDERS']::TEXT[] WHERE name = 'Flexão na Parede Pegada Fechada';

-- Flexão na Ponta dos Dedos
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   is_compound: não -> sim
--   primary_muscles: FOREARMS -> CHEST
--   secondary_muscles: (vazio) -> SHOULDERS, TRICEPS, FOREARMS
--   contraindications: (vazio) -> SHOULDER, WRIST
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', is_compound = true, primary_muscles = ARRAY['CHEST']::TEXT[], secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS', 'FOREARMS']::TEXT[], contraindications = ARRAY['SHOULDER', 'WRIST']::TEXT[] WHERE name = 'Flexão na Ponta dos Dedos';

-- Flexão no Bosu
--   modality: (vazio) -> STRENGTH
--   secondary_muscles: SHOULDERS, CORE -> SHOULDERS, TRICEPS
--   contraindications: (vazio) -> SHOULDER, WRIST
UPDATE exercises SET modality = 'STRENGTH', secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], contraindications = ARRAY['SHOULDER', 'WRIST']::TEXT[] WHERE name = 'Flexão no Bosu';

-- Flexão no TRX
--   modality: (vazio) -> STRENGTH
--   secondary_muscles: SHOULDERS, CORE -> SHOULDERS, TRICEPS
UPDATE exercises SET modality = 'STRENGTH', secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[] WHERE name = 'Flexão no TRX';

-- Gancho de Direita (Boxe)
--   primary_muscles: SHOULDERS, CORE -> SHOULDERS
--   secondary_muscles: LEGS, BACK -> CHEST, CORE
UPDATE exercises SET primary_muscles = ARRAY['SHOULDERS']::TEXT[], secondary_muscles = ARRAY['CHEST', 'CORE']::TEXT[] WHERE name = 'Gancho de Direita (Boxe)';

-- Hand Grip (Flexor de Mão)
--   equipment: BODYWEIGHT -> HAND_GRIP
UPDATE exercises SET equipment = 'HAND_GRIP' WHERE name = 'Hand Grip (Flexor de Mão)';

-- Hang Clean com Barra
--   modality: (vazio) -> STRENGTH
--   secondary_pattern: (vazio) -> SQUAT
--   secondary_muscles: BACK, SHOULDERS, CORE -> SHOULDERS, BACK
--   contraindications: LUMBAR, SHOULDER -> LUMBAR, SHOULDER, KNEE, WRIST
UPDATE exercises SET modality = 'STRENGTH', secondary_pattern = 'SQUAT', secondary_muscles = ARRAY['SHOULDERS', 'BACK']::TEXT[], contraindications = ARRAY['LUMBAR', 'SHOULDER', 'KNEE', 'WRIST']::TEXT[] WHERE name = 'Hang Clean com Barra';

-- Hang Clean com Kettlebell
--   modality: (vazio) -> STRENGTH
--   secondary_muscles: BACK, SHOULDERS, CORE -> SHOULDERS, BACK
--   unilateral: não -> sim
UPDATE exercises SET modality = 'STRENGTH', secondary_muscles = ARRAY['SHOULDERS', 'BACK']::TEXT[], unilateral = true WHERE name = 'Hang Clean com Kettlebell';

-- Heaving Snatch Balance
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> VERTICAL_PUSH
--   secondary_pattern: (vazio) -> SQUAT
--   equipment: BODYWEIGHT -> BARBELL
--   primary_muscles: CORE -> LEGS, SHOULDERS
--   secondary_muscles: (vazio) -> TRICEPS, GLUTES
--   contraindications: (vazio) -> SHOULDER, LUMBAR, KNEE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'VERTICAL_PUSH', secondary_pattern = 'SQUAT', equipment = 'BARBELL', primary_muscles = ARRAY['LEGS', 'SHOULDERS']::TEXT[], secondary_muscles = ARRAY['TRICEPS', 'GLUTES']::TEXT[], contraindications = ARRAY['SHOULDER', 'LUMBAR', 'KNEE']::TEXT[] WHERE name = 'Heaving Snatch Balance';

-- Hiperextensão Lombar
--   secondary_muscles: GLUTES, LEGS, CORE -> GLUTES, LEGS
UPDATE exercises SET secondary_muscles = ARRAY['GLUTES', 'LEGS']::TEXT[] WHERE name = 'Hiperextensão Lombar';

-- Hiperextensão Lombar no Banco Reto
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HINGE
--   is_compound: (vazio) -> sim
--   equipment: (vazio) -> BODYWEIGHT
--   secondary_muscles: (vazio) -> GLUTES, LEGS
--   unilateral: (vazio) -> não
--   prescription_type: (vazio) -> REPS
--   level: (vazio) -> INTERMEDIATE
--   contraindications: (vazio) -> LUMBAR
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HINGE', is_compound = true, equipment = 'BODYWEIGHT', secondary_muscles = ARRAY['GLUTES', 'LEGS']::TEXT[], unilateral = false, prescription_type = 'REPS', level = 'INTERMEDIATE', contraindications = ARRAY['LUMBAR']::TEXT[] WHERE name = 'Hiperextensão Lombar no Banco Reto';

-- Hiperextensão Reversa Sapo no Banco
--   categoria: LOWER_BACK -> GLUTES
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   primary_muscles: BACK -> GLUTES
--   secondary_muscles: (vazio) -> BACK
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> LUMBAR
UPDATE exercises SET category = 'GLUTES', modality = 'STRENGTH', movement_pattern = 'NONE', primary_muscles = ARRAY['GLUTES']::TEXT[], secondary_muscles = ARRAY['BACK']::TEXT[], level = 'BEGINNER', contraindications = ARRAY['LUMBAR']::TEXT[] WHERE name = 'Hiperextensão Reversa Sapo no Banco';

-- Hiperextensão Reversa com Faixa Elástica
--   movement_pattern: HINGE -> NONE
--   is_compound: sim -> não
--   primary_muscles: GLUTES, LEGS -> GLUTES
--   secondary_muscles: BACK, CORE -> LEGS, BACK
UPDATE exercises SET movement_pattern = 'NONE', is_compound = false, primary_muscles = ARRAY['GLUTES']::TEXT[], secondary_muscles = ARRAY['LEGS', 'BACK']::TEXT[] WHERE name = 'Hiperextensão Reversa com Faixa Elástica';

-- Hiperextensão com Rotação
--   movement_pattern: ROTATION -> HINGE
--   secondary_pattern: (vazio) -> ROTATION
--   equipment: BODYWEIGHT -> MACHINE
--   primary_muscles: CORE -> BACK
--   secondary_muscles: BACK, GLUTES -> CORE, GLUTES
UPDATE exercises SET movement_pattern = 'HINGE', secondary_pattern = 'ROTATION', equipment = 'MACHINE', primary_muscles = ARRAY['BACK']::TEXT[], secondary_muscles = ARRAY['CORE', 'GLUTES']::TEXT[] WHERE name = 'Hiperextensão com Rotação';

-- Hiperextensão no Solo
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> LUMBAR
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', level = 'BEGINNER', contraindications = ARRAY['LUMBAR']::TEXT[] WHERE name = 'Hiperextensão no Solo';

-- Jab (Boxe)
--   primary_muscles: SHOULDERS, CORE -> SHOULDERS
--   secondary_muscles: LEGS, BACK -> CHEST, TRICEPS
UPDATE exercises SET primary_muscles = ARRAY['SHOULDERS']::TEXT[], secondary_muscles = ARRAY['CHEST', 'TRICEPS']::TEXT[] WHERE name = 'Jab (Boxe)';

-- Joelho ao Peito Alternado Deitado
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> NONE
--   primary_muscles: CHEST -> GLUTES
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'NONE', primary_muscles = ARRAY['GLUTES']::TEXT[], level = 'BEGINNER' WHERE name = 'Joelho ao Peito Alternado Deitado';

-- Joelhos Altos Apoiado na Parede
--   modality: (vazio) -> CARDIO
--   movement_pattern: (vazio) -> GAIT
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> IMPACT
UPDATE exercises SET modality = 'CARDIO', movement_pattern = 'GAIT', level = 'BEGINNER', contraindications = ARRAY['IMPACT']::TEXT[] WHERE name = 'Joelhos Altos Apoiado na Parede';

-- Leg Press 45°
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> SQUAT
--   primary_muscles: LEGS -> LEGS, GLUTES
--   contraindications: (vazio) -> KNEE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'SQUAT', primary_muscles = ARRAY['LEGS', 'GLUTES']::TEXT[], contraindications = ARRAY['KNEE']::TEXT[] WHERE name = 'Leg Press 45°';

-- Leg Press 90° no Smith
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> SQUAT
--   primary_muscles: LEGS -> LEGS, GLUTES
--   contraindications: (vazio) -> KNEE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'SQUAT', primary_muscles = ARRAY['LEGS', 'GLUTES']::TEXT[], contraindications = ARRAY['KNEE']::TEXT[] WHERE name = 'Leg Press 90° no Smith';

-- Leg Press Alternado Deitado com Gymstick
--   modality: (vazio) -> STRENGTH
--   secondary_muscles: CORE -> (vazio)
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', secondary_muscles = ARRAY[]::TEXT[], level = 'BEGINNER' WHERE name = 'Leg Press Alternado Deitado com Gymstick';

-- Leg Press Horizontal
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> SQUAT
--   primary_muscles: LEGS -> LEGS, GLUTES
--   contraindications: (vazio) -> KNEE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'SQUAT', primary_muscles = ARRAY['LEGS', 'GLUTES']::TEXT[], contraindications = ARRAY['KNEE']::TEXT[] WHERE name = 'Leg Press Horizontal';

-- Leg Press Unilateral
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> SQUAT
--   primary_muscles: LEGS -> LEGS, GLUTES
--   contraindications: (vazio) -> KNEE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'SQUAT', primary_muscles = ARRAY['LEGS', 'GLUTES']::TEXT[], contraindications = ARRAY['KNEE']::TEXT[] WHERE name = 'Leg Press Unilateral';

-- Levantamento Terra
--   secondary_muscles: BACK, CORE -> BACK
UPDATE exercises SET secondary_muscles = ARRAY['BACK']::TEXT[] WHERE name = 'Levantamento Terra';

-- Levantamento Terra Parcial no Rack (Rack Pull)
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HINGE
--   primary_muscles: LEGS -> GLUTES, LEGS
--   secondary_muscles: (vazio) -> BACK
--   contraindications: (vazio) -> LUMBAR
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HINGE', primary_muscles = ARRAY['GLUTES', 'LEGS']::TEXT[], secondary_muscles = ARRAY['BACK']::TEXT[], contraindications = ARRAY['LUMBAR']::TEXT[] WHERE name = 'Levantamento Terra Parcial no Rack (Rack Pull)';

-- Levantamento Terra Sumô com Barra
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HINGE
--   primary_muscles: LEGS -> GLUTES, LEGS
--   secondary_muscles: (vazio) -> BACK
--   contraindications: (vazio) -> LUMBAR, KNEE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HINGE', primary_muscles = ARRAY['GLUTES', 'LEGS']::TEXT[], secondary_muscles = ARRAY['BACK']::TEXT[], contraindications = ARRAY['LUMBAR', 'KNEE']::TEXT[] WHERE name = 'Levantamento Terra Sumô com Barra';

-- Levantamento Terra Sumô com Halteres
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HINGE
--   primary_muscles: LEGS -> GLUTES, LEGS
--   secondary_muscles: (vazio) -> BACK
--   contraindications: (vazio) -> LUMBAR, KNEE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HINGE', primary_muscles = ARRAY['GLUTES', 'LEGS']::TEXT[], secondary_muscles = ARRAY['BACK']::TEXT[], contraindications = ARRAY['LUMBAR', 'KNEE']::TEXT[] WHERE name = 'Levantamento Terra Sumô com Halteres';

-- Levantamento Terra Unilateral com Peso Corporal
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HINGE
--   primary_muscles: GLUTES -> LEGS
--   secondary_muscles: (vazio) -> GLUTES
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HINGE', primary_muscles = ARRAY['LEGS']::TEXT[], secondary_muscles = ARRAY['GLUTES']::TEXT[], level = 'BEGINNER' WHERE name = 'Levantamento Terra Unilateral com Peso Corporal';

-- Levantamento Terra Zercher
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HINGE
--   primary_muscles: LEGS -> GLUTES, LEGS
--   secondary_muscles: (vazio) -> BACK
--   contraindications: (vazio) -> LUMBAR, KNEE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HINGE', primary_muscles = ARRAY['GLUTES', 'LEGS']::TEXT[], secondary_muscles = ARRAY['BACK']::TEXT[], contraindications = ARRAY['LUMBAR', 'KNEE']::TEXT[] WHERE name = 'Levantamento Terra Zercher';

-- Levantamento Terra com Barra Hexagonal
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HINGE
--   equipment: BARBELL -> TRAP_BAR
--   primary_muscles: LEGS -> GLUTES, LEGS
--   secondary_muscles: (vazio) -> BACK
--   contraindications: (vazio) -> LUMBAR, KNEE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HINGE', equipment = 'TRAP_BAR', primary_muscles = ARRAY['GLUTES', 'LEGS']::TEXT[], secondary_muscles = ARRAY['BACK']::TEXT[], contraindications = ARRAY['LUMBAR', 'KNEE']::TEXT[] WHERE name = 'Levantamento Terra com Barra Hexagonal';

-- Levantamento Terra com Halter entre as Pernas
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HINGE
--   primary_muscles: BACK -> GLUTES, LEGS
--   secondary_muscles: (vazio) -> BACK
--   contraindications: (vazio) -> LUMBAR, KNEE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HINGE', primary_muscles = ARRAY['GLUTES', 'LEGS']::TEXT[], secondary_muscles = ARRAY['BACK']::TEXT[], contraindications = ARRAY['LUMBAR', 'KNEE']::TEXT[] WHERE name = 'Levantamento Terra com Halter entre as Pernas';

-- Levantamento Terra com Halteres
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HINGE
--   primary_muscles: LEGS -> GLUTES, LEGS
--   secondary_muscles: (vazio) -> BACK
--   contraindications: (vazio) -> LUMBAR, KNEE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HINGE', primary_muscles = ARRAY['GLUTES', 'LEGS']::TEXT[], secondary_muscles = ARRAY['BACK']::TEXT[], contraindications = ARRAY['LUMBAR', 'KNEE']::TEXT[] WHERE name = 'Levantamento Terra com Halteres';

-- Levantamento Terra com Kettlebell
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HINGE
--   primary_muscles: LEGS -> GLUTES, LEGS
--   secondary_muscles: (vazio) -> BACK
--   contraindications: (vazio) -> LUMBAR, KNEE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HINGE', primary_muscles = ARRAY['GLUTES', 'LEGS']::TEXT[], secondary_muscles = ARRAY['BACK']::TEXT[], contraindications = ARRAY['LUMBAR', 'KNEE']::TEXT[] WHERE name = 'Levantamento Terra com Kettlebell';

-- Levantamento Terra na Máquina (Alavanca)
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HINGE
--   primary_muscles: LEGS -> GLUTES, LEGS
--   secondary_muscles: (vazio) -> BACK
--   level: BEGINNER -> INTERMEDIATE
--   contraindications: (vazio) -> LUMBAR, KNEE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HINGE', primary_muscles = ARRAY['GLUTES', 'LEGS']::TEXT[], secondary_muscles = ARRAY['BACK']::TEXT[], level = 'INTERMEDIATE', contraindications = ARRAY['LUMBAR', 'KNEE']::TEXT[] WHERE name = 'Levantamento Terra na Máquina (Alavanca)';

-- Levantamento Terra no Landmine
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HINGE
--   equipment: BARBELL -> LANDMINE
--   primary_muscles: LEGS -> GLUTES, LEGS
--   secondary_muscles: (vazio) -> BACK
--   contraindications: (vazio) -> LUMBAR, KNEE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HINGE', equipment = 'LANDMINE', primary_muscles = ARRAY['GLUTES', 'LEGS']::TEXT[], secondary_muscles = ARRAY['BACK']::TEXT[], contraindications = ARRAY['LUMBAR', 'KNEE']::TEXT[] WHERE name = 'Levantamento Terra no Landmine';

-- Levantamento Turco (Turkish Get-Up)
--   modality: (vazio) -> STRENGTH
--   primary_muscles: SHOULDERS, CORE -> CORE
--   secondary_muscles: LEGS, GLUTES, BACK -> GLUTES, LEGS
--   contraindications: SHOULDER, LUMBAR -> SHOULDER, LUMBAR, KNEE
UPDATE exercises SET modality = 'STRENGTH', primary_muscles = ARRAY['CORE']::TEXT[], secondary_muscles = ARRAY['GLUTES', 'LEGS']::TEXT[], contraindications = ARRAY['SHOULDER', 'LUMBAR', 'KNEE']::TEXT[] WHERE name = 'Levantamento Turco (Turkish Get-Up)';

-- Levantamento de Bola Medicinal do Chão acima da Cabeça
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HINGE
--   primary_muscles: CORE -> GLUTES, SHOULDERS
--   secondary_muscles: (vazio) -> LEGS, CHEST
--   contraindications: (vazio) -> LUMBAR, SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HINGE', primary_muscles = ARRAY['GLUTES', 'SHOULDERS']::TEXT[], secondary_muscles = ARRAY['LEGS', 'CHEST']::TEXT[], contraindications = ARRAY['LUMBAR', 'SHOULDER']::TEXT[] WHERE name = 'Levantamento de Bola Medicinal do Chão acima da Cabeça';

-- Liberação Miofascial com Rolo: Costas
--   movement_pattern: STRETCH -> NONE
--   secondary_muscles: SHOULDERS -> (vazio)
--   contraindications: LUMBAR -> (vazio)
UPDATE exercises SET movement_pattern = 'NONE', secondary_muscles = ARRAY[]::TEXT[], contraindications = ARRAY[]::TEXT[] WHERE name = 'Liberação Miofascial com Rolo: Costas';

-- Liberação Miofascial com Rolo: Glúteos
--   movement_pattern: STRETCH -> NONE
--   unilateral: não -> sim
UPDATE exercises SET movement_pattern = 'NONE', unilateral = true WHERE name = 'Liberação Miofascial com Rolo: Glúteos';

-- Liberação Miofascial com Rolo: Isquiotibiais
--   movement_pattern: STRETCH -> NONE
--   unilateral: não -> sim
UPDATE exercises SET movement_pattern = 'NONE', unilateral = true WHERE name = 'Liberação Miofascial com Rolo: Isquiotibiais';

-- Liberação Miofascial com Rolo: Ombro Posterior
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> NONE
--   equipment: BODYWEIGHT -> FOAM_ROLLER
--   unilateral: não -> sim
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'NONE', equipment = 'FOAM_ROLLER', unilateral = true, level = 'BEGINNER' WHERE name = 'Liberação Miofascial com Rolo: Ombro Posterior';

-- Liberação Miofascial com Rolo: Panturrilhas
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> NONE
--   equipment: BODYWEIGHT -> FOAM_ROLLER
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'NONE', equipment = 'FOAM_ROLLER', level = 'BEGINNER' WHERE name = 'Liberação Miofascial com Rolo: Panturrilhas';

-- Liberação Miofascial com Rolo: Peitoral e Ombro Anterior
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> NONE
--   equipment: BODYWEIGHT -> FOAM_ROLLER
--   primary_muscles: SHOULDERS -> CHEST, SHOULDERS
--   unilateral: não -> sim
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'NONE', equipment = 'FOAM_ROLLER', primary_muscles = ARRAY['CHEST', 'SHOULDERS']::TEXT[], unilateral = true, level = 'BEGINNER' WHERE name = 'Liberação Miofascial com Rolo: Peitoral e Ombro Anterior';

-- Liberação Miofascial com Rolo: Planta do Pé
--   movement_pattern: STRETCH -> NONE
--   equipment: MASSAGE_BALL -> FOAM_ROLLER
UPDATE exercises SET movement_pattern = 'NONE', equipment = 'FOAM_ROLLER' WHERE name = 'Liberação Miofascial com Rolo: Planta do Pé';

-- Liberação Miofascial com Rolo: Quadríceps
--   movement_pattern: STRETCH -> NONE
UPDATE exercises SET movement_pattern = 'NONE' WHERE name = 'Liberação Miofascial com Rolo: Quadríceps';

-- Liberação Miofascial com Rolo: Romboides
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> NONE
--   equipment: BODYWEIGHT -> FOAM_ROLLER
--   primary_muscles: CORE -> BACK
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'NONE', equipment = 'FOAM_ROLLER', primary_muscles = ARRAY['BACK']::TEXT[], level = 'BEGINNER' WHERE name = 'Liberação Miofascial com Rolo: Romboides';

-- Liberação Miofascial de Adutores com Rolo
--   movement_pattern: STRETCH -> NONE
UPDATE exercises SET movement_pattern = 'NONE' WHERE name = 'Liberação Miofascial de Adutores com Rolo';

-- Liberação Miofascial do Dorsal com Rolo
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> NONE
--   equipment: BODYWEIGHT -> FOAM_ROLLER
--   primary_muscles: CORE -> BACK
--   unilateral: não -> sim
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'NONE', equipment = 'FOAM_ROLLER', primary_muscles = ARRAY['BACK']::TEXT[], unilateral = true WHERE name = 'Liberação Miofascial do Dorsal com Rolo';

-- Liberação Miofascial do Trato Iliotibial
--   movement_pattern: STRETCH -> NONE
UPDATE exercises SET movement_pattern = 'NONE' WHERE name = 'Liberação Miofascial do Trato Iliotibial';

-- Meio Desenvolvimento Arnold Sentado
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> VERTICAL_PUSH
--   is_compound: (vazio) -> sim
--   equipment: (vazio) -> DUMBBELL
--   secondary_muscles: (vazio) -> TRICEPS, CHEST
--   unilateral: (vazio) -> não
--   prescription_type: (vazio) -> REPS
--   level: (vazio) -> INTERMEDIATE
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'VERTICAL_PUSH', is_compound = true, equipment = 'DUMBBELL', secondary_muscles = ARRAY['TRICEPS', 'CHEST']::TEXT[], unilateral = false, prescription_type = 'REPS', level = 'INTERMEDIATE', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Meio Desenvolvimento Arnold Sentado';

-- Mergulho Assistido na Máquina
--   secondary_muscles: SHOULDERS -> SHOULDERS, TRICEPS
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[] WHERE name = 'Mergulho Assistido na Máquina';

-- Mergulho Assistido para Tríceps
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> VERTICAL_PUSH
--   equipment: BODYWEIGHT -> MACHINE
--   secondary_muscles: (vazio) -> CHEST, SHOULDERS
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'VERTICAL_PUSH', equipment = 'MACHINE', secondary_muscles = ARRAY['CHEST', 'SHOULDERS']::TEXT[], level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Mergulho Assistido para Tríceps';

-- Mergulho Coreano
--   modality: (vazio) -> STRENGTH
--   secondary_muscles: CORE -> TRICEPS
UPDATE exercises SET modality = 'STRENGTH', secondary_muscles = ARRAY['TRICEPS']::TEXT[] WHERE name = 'Mergulho Coreano';

-- Mergulho Escapular na Paralela
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   is_compound: sim -> não
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', is_compound = false, contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Mergulho Escapular na Paralela';

-- Mergulho Impossível (Impossible Dips)
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> VERTICAL_PUSH
--   primary_muscles: CHEST -> TRICEPS
--   secondary_muscles: (vazio) -> CHEST, SHOULDERS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'VERTICAL_PUSH', primary_muscles = ARRAY['TRICEPS']::TEXT[], secondary_muscles = ARRAY['CHEST', 'SHOULDERS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Mergulho Impossível (Impossible Dips)';

-- Mergulho Sentado na Máquina
--   movement_pattern: NONE -> VERTICAL_PUSH
--   is_compound: não -> sim
--   secondary_muscles: (vazio) -> CHEST, SHOULDERS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET movement_pattern = 'VERTICAL_PUSH', is_compound = true, secondary_muscles = ARRAY['CHEST', 'SHOULDERS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Mergulho Sentado na Máquina';

-- Mergulho de Tríceps na Máquina (Alavanca)
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> VERTICAL_PUSH
--   secondary_muscles: (vazio) -> CHEST, SHOULDERS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'VERTICAL_PUSH', secondary_muscles = ARRAY['CHEST', 'SHOULDERS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Mergulho de Tríceps na Máquina (Alavanca)';

-- Mergulho de Tríceps nas Paralelas
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> VERTICAL_PUSH
--   secondary_muscles: (vazio) -> CHEST, SHOULDERS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'VERTICAL_PUSH', secondary_muscles = ARRAY['CHEST', 'SHOULDERS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Mergulho de Tríceps nas Paralelas';

-- Mergulho de Tríceps no Solo
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> VERTICAL_PUSH
--   secondary_muscles: (vazio) -> CHEST, SHOULDERS
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER, WRIST
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'VERTICAL_PUSH', secondary_muscles = ARRAY['CHEST', 'SHOULDERS']::TEXT[], level = 'BEGINNER', contraindications = ARRAY['SHOULDER', 'WRIST']::TEXT[] WHERE name = 'Mergulho de Tríceps no Solo';

-- Mergulho entre Bancos
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> VERTICAL_PUSH
--   is_compound: (vazio) -> sim
--   equipment: (vazio) -> BODYWEIGHT
--   secondary_muscles: (vazio) -> CHEST, SHOULDERS
--   unilateral: (vazio) -> não
--   prescription_type: (vazio) -> REPS
--   level: (vazio) -> INTERMEDIATE
--   contraindications: (vazio) -> SHOULDER, WRIST
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'VERTICAL_PUSH', is_compound = true, equipment = 'BODYWEIGHT', secondary_muscles = ARRAY['CHEST', 'SHOULDERS']::TEXT[], unilateral = false, prescription_type = 'REPS', level = 'INTERMEDIATE', contraindications = ARRAY['SHOULDER', 'WRIST']::TEXT[] WHERE name = 'Mergulho entre Bancos';

-- Mergulho entre Cadeiras
--   secondary_muscles: SHOULDERS, CORE -> SHOULDERS, TRICEPS
--   level: BEGINNER -> INTERMEDIATE
--   contraindications: SHOULDER -> SHOULDER, WRIST
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], level = 'INTERMEDIATE', contraindications = ARRAY['SHOULDER', 'WRIST']::TEXT[] WHERE name = 'Mergulho entre Cadeiras';

-- Mergulho nas Argolas
--   modality: (vazio) -> STRENGTH
--   equipment: BODYWEIGHT -> SUSPENSION
--   secondary_muscles: SHOULDERS, CORE -> SHOULDERS, TRICEPS
UPDATE exercises SET modality = 'STRENGTH', equipment = 'SUSPENSION', secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[] WHERE name = 'Mergulho nas Argolas';

-- Mergulho nas Paralelas com Ênfase no Peitoral
--   secondary_muscles: SHOULDERS, CORE -> SHOULDERS, TRICEPS
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[] WHERE name = 'Mergulho nas Paralelas com Ênfase no Peitoral';

-- Mergulho no Banco
--   movement_pattern: NONE -> VERTICAL_PUSH
--   contraindications: SHOULDER -> SHOULDER, WRIST
UPDATE exercises SET movement_pattern = 'VERTICAL_PUSH', contraindications = ARRAY['SHOULDER', 'WRIST']::TEXT[] WHERE name = 'Mergulho no Banco';

-- Mesa Flexora Ajoelhado (Alavanca)
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> KNEE_FLEXION
--   is_compound: (vazio) -> não
--   equipment: (vazio) -> MACHINE
--   unilateral: (vazio) -> sim
--   prescription_type: (vazio) -> REPS
--   level: (vazio) -> BEGINNER
--   contraindications: (vazio) -> KNEE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'KNEE_FLEXION', is_compound = false, equipment = 'MACHINE', unilateral = true, prescription_type = 'REPS', level = 'BEGINNER', contraindications = ARRAY['KNEE']::TEXT[] WHERE name = 'Mesa Flexora Ajoelhado (Alavanca)';

-- Mesa Flexora Unilateral
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> KNEE_FLEXION
--   contraindications: (vazio) -> KNEE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'KNEE_FLEXION', contraindications = ARRAY['KNEE']::TEXT[] WHERE name = 'Mesa Flexora Unilateral';

-- Minhoca (Inchworm)
--   primary_muscles: CORE -> LEGS
--   secondary_muscles: SHOULDERS, LEGS, CHEST -> SHOULDERS
--   contraindications: (vazio) -> SHOULDER, WRIST
UPDATE exercises SET primary_muscles = ARRAY['LEGS']::TEXT[], secondary_muscles = ARRAY['SHOULDERS']::TEXT[], contraindications = ARRAY['SHOULDER', 'WRIST']::TEXT[] WHERE name = 'Minhoca (Inchworm)';

-- Mobilidade de Pés e Tornozelos Sentado
--   movement_pattern: STRETCH -> NONE
--   unilateral: sim -> não
--   prescription_type: TIME -> REPS
UPDATE exercises SET movement_pattern = 'NONE', unilateral = false, prescription_type = 'REPS' WHERE name = 'Mobilidade de Pés e Tornozelos Sentado';

-- Moinho de Vento com Halter
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HINGE
--   secondary_pattern: (vazio) -> ROTATION
--   primary_muscles: LEGS -> CORE
--   secondary_muscles: (vazio) -> GLUTES, LEGS
--   unilateral: não -> sim
--   level: INTERMEDIATE -> ADVANCED
--   contraindications: (vazio) -> LUMBAR, SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HINGE', secondary_pattern = 'ROTATION', primary_muscles = ARRAY['CORE']::TEXT[], secondary_muscles = ARRAY['GLUTES', 'LEGS']::TEXT[], unilateral = true, level = 'ADVANCED', contraindications = ARRAY['LUMBAR', 'SHOULDER']::TEXT[] WHERE name = 'Moinho de Vento com Halter';

-- Moinho de Vento com Kettlebell
--   movement_pattern: ROTATION -> HINGE
--   secondary_pattern: (vazio) -> ROTATION
--   secondary_muscles: SHOULDERS, LEGS -> GLUTES, LEGS
UPDATE exercises SET movement_pattern = 'HINGE', secondary_pattern = 'ROTATION', secondary_muscles = ARRAY['GLUTES', 'LEGS']::TEXT[] WHERE name = 'Moinho de Vento com Kettlebell';

-- Moinho de Vento com Toque Cruzado no Pé
--   secondary_muscles: SHOULDERS, LEGS -> LEGS
--   contraindications: (vazio) -> LUMBAR
UPDATE exercises SET secondary_muscles = ARRAY['LEGS']::TEXT[], contraindications = ARRAY['LUMBAR']::TEXT[] WHERE name = 'Moinho de Vento com Toque Cruzado no Pé';

-- Muscle Snatch com Barra
--   modality: (vazio) -> STRENGTH
--   primary_muscles: LEGS, BACK -> GLUTES, LEGS
--   secondary_muscles: SHOULDERS, CORE -> SHOULDERS, BACK
--   contraindications: LUMBAR, SHOULDER -> LUMBAR, SHOULDER, KNEE
UPDATE exercises SET modality = 'STRENGTH', primary_muscles = ARRAY['GLUTES', 'LEGS']::TEXT[], secondary_muscles = ARRAY['SHOULDERS', 'BACK']::TEXT[], contraindications = ARRAY['LUMBAR', 'SHOULDER', 'KNEE']::TEXT[] WHERE name = 'Muscle Snatch com Barra';

-- Muscle-Up
--   modality: (vazio) -> STRENGTH
--   secondary_muscles: CHEST, SHOULDERS -> SHOULDERS, BICEPS, TRICEPS
UPDATE exercises SET modality = 'STRENGTH', secondary_muscles = ARRAY['SHOULDERS', 'BICEPS', 'TRICEPS']::TEXT[] WHERE name = 'Muscle-Up';

-- Nadador (Swimming)
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   is_compound: (vazio) -> sim
--   equipment: (vazio) -> BODYWEIGHT
--   primary_muscles: BACK, CORE -> BACK
--   secondary_muscles: (vazio) -> GLUTES, SHOULDERS
--   unilateral: (vazio) -> não
--   prescription_type: (vazio) -> TIME
--   level: (vazio) -> BEGINNER
--   contraindications: (vazio) -> LUMBAR
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', is_compound = true, equipment = 'BODYWEIGHT', primary_muscles = ARRAY['BACK']::TEXT[], secondary_muscles = ARRAY['GLUTES', 'SHOULDERS']::TEXT[], unilateral = false, prescription_type = 'TIME', level = 'BEGINNER', contraindications = ARRAY['LUMBAR']::TEXT[] WHERE name = 'Nadador (Swimming)';

-- Oito com Kettlebell (Figure 8)
--   secondary_muscles: GLUTES, LEGS, BACK, SHOULDERS -> GLUTES, LEGS, BACK
UPDATE exercises SET secondary_muscles = ARRAY['GLUTES', 'LEGS', 'BACK']::TEXT[] WHERE name = 'Oito com Kettlebell (Figure 8)';

-- Panturrilha Burrinho Apoiado no Banco
--   secondary_muscles: CORE -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Panturrilha Burrinho Apoiado no Banco';

-- Panturrilha Burrinho Unilateral Apoiado no Banco
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', level = 'BEGINNER' WHERE name = 'Panturrilha Burrinho Unilateral Apoiado no Banco';

-- Panturrilha Burrinho com Parceiro
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   equipment: DUMBBELL -> BODYWEIGHT
--   contraindications: (vazio) -> LUMBAR
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', equipment = 'BODYWEIGHT', contraindications = ARRAY['LUMBAR']::TEXT[] WHERE name = 'Panturrilha Burrinho com Parceiro';

-- Panturrilha Burrinho na Máquina
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   contraindications: (vazio) -> LUMBAR
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', contraindications = ARRAY['LUMBAR']::TEXT[] WHERE name = 'Panturrilha Burrinho na Máquina';

-- Panturrilha Sentado na Máquina (Alavanca)
--   secondary_muscles: CORE -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Panturrilha Sentado na Máquina (Alavanca)';

-- Panturrilha em Pé na Máquina
--   secondary_muscles: CORE -> (vazio)
--   contraindications: (vazio) -> LUMBAR
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[], contraindications = ARRAY['LUMBAR']::TEXT[] WHERE name = 'Panturrilha em Pé na Máquina';

-- Passada de Boxeador
--   modality: (vazio) -> CARDIO
--   movement_pattern: (vazio) -> GAIT
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> IMPACT
UPDATE exercises SET modality = 'CARDIO', movement_pattern = 'GAIT', level = 'BEGINNER', contraindications = ARRAY['IMPACT']::TEXT[] WHERE name = 'Passada de Boxeador';

-- Passe de Peito com Bola Medicinal na Parede
--   modality: (vazio) -> PLYOMETRIC
--   secondary_muscles: SHOULDERS, CORE, LEGS -> SHOULDERS, TRICEPS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'PLYOMETRIC', secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Passe de Peito com Bola Medicinal na Parede';

-- Passo Lateral Rápido
--   modality: (vazio) -> CARDIO
--   movement_pattern: (vazio) -> GAIT
--   prescription_type: REPS -> TIME
--   contraindications: (vazio) -> IMPACT
UPDATE exercises SET modality = 'CARDIO', movement_pattern = 'GAIT', prescription_type = 'TIME', contraindications = ARRAY['IMPACT']::TEXT[] WHERE name = 'Passo Lateral Rápido';

-- Passo de Esqui
--   primary_muscles: LEGS, GLUTES -> LEGS
--   secondary_muscles: CORE, SHOULDERS -> (vazio)
UPDATE exercises SET primary_muscles = ARRAY['LEGS']::TEXT[], secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Passo de Esqui';

-- Patinador (Skater Jump)
--   secondary_muscles: CORE -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Patinador (Skater Jump)';

-- Pedalada em Pé na Bicicleta Ergométrica
--   modality: (vazio) -> CARDIO
--   movement_pattern: (vazio) -> NONE
--   secondary_muscles: (vazio) -> GLUTES
--   contraindications: (vazio) -> KNEE
UPDATE exercises SET modality = 'CARDIO', movement_pattern = 'NONE', secondary_muscles = ARRAY['GLUTES']::TEXT[], contraindications = ARRAY['KNEE']::TEXT[] WHERE name = 'Pedalada em Pé na Bicicleta Ergométrica';

-- Perdigueiro (Bird Dog)
--   secondary_muscles: BACK, SHOULDERS, GLUTES -> GLUTES, SHOULDERS
UPDATE exercises SET secondary_muscles = ARRAY['GLUTES', 'SHOULDERS']::TEXT[] WHERE name = 'Perdigueiro (Bird Dog)';

-- Planche
--   modality: (vazio) -> SKILL
--   secondary_muscles: CORE -> BICEPS
UPDATE exercises SET modality = 'SKILL', secondary_muscles = ARRAY['BICEPS']::TEXT[] WHERE name = 'Planche';

-- Polichinelo
--   movement_pattern: GAIT -> NONE
--   secondary_muscles: GLUTES, SHOULDERS, CORE -> SHOULDERS
UPDATE exercises SET movement_pattern = 'NONE', secondary_muscles = ARRAY['SHOULDERS']::TEXT[] WHERE name = 'Polichinelo';

-- Polichinelo com Abertura Horizontal (Seal Jack)
--   modality: (vazio) -> CARDIO
--   movement_pattern: (vazio) -> NONE
--   secondary_muscles: (vazio) -> SHOULDERS
--   prescription_type: REPS -> TIME
--   contraindications: (vazio) -> IMPACT
UPDATE exercises SET modality = 'CARDIO', movement_pattern = 'NONE', secondary_muscles = ARRAY['SHOULDERS']::TEXT[], prescription_type = 'TIME', contraindications = ARRAY['IMPACT']::TEXT[] WHERE name = 'Polichinelo com Abertura Horizontal (Seal Jack)';

-- Polichinelo com Agachamento Sumô
--   modality: (vazio) -> PLYOMETRIC
--   movement_pattern: (vazio) -> SQUAT
--   primary_muscles: CORE -> LEGS, GLUTES
--   level: BEGINNER -> INTERMEDIATE
--   contraindications: (vazio) -> IMPACT, KNEE
UPDATE exercises SET modality = 'PLYOMETRIC', movement_pattern = 'SQUAT', primary_muscles = ARRAY['LEGS', 'GLUTES']::TEXT[], level = 'INTERMEDIATE', contraindications = ARRAY['IMPACT', 'KNEE']::TEXT[] WHERE name = 'Polichinelo com Agachamento Sumô';

-- Ponte de Glúteos
--   secondary_muscles: LEGS, CORE -> LEGS
UPDATE exercises SET secondary_muscles = ARRAY['LEGS']::TEXT[] WHERE name = 'Ponte de Glúteos';

-- Ponte de Glúteos Sapo (Frog Pump)
--   categoria: CORE -> GLUTES
--   movement_pattern: NONE -> HINGE
--   is_compound: não -> sim
--   primary_muscles: CORE -> GLUTES
--   contraindications: LUMBAR -> (vazio)
UPDATE exercises SET category = 'GLUTES', movement_pattern = 'HINGE', is_compound = true, primary_muscles = ARRAY['GLUTES']::TEXT[], contraindications = ARRAY[]::TEXT[] WHERE name = 'Ponte de Glúteos Sapo (Frog Pump)';

-- Ponte de Glúteos Unilateral com Perna Elevada
--   secondary_muscles: LEGS, CORE -> LEGS
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET secondary_muscles = ARRAY['LEGS']::TEXT[], level = 'BEGINNER' WHERE name = 'Ponte de Glúteos Unilateral com Perna Elevada';

-- Ponte de Glúteos Unilateral com Perna Estendida
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HINGE
--   secondary_muscles: (vazio) -> LEGS
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HINGE', secondary_muscles = ARRAY['LEGS']::TEXT[], level = 'BEGINNER' WHERE name = 'Ponte de Glúteos Unilateral com Perna Estendida';

-- Ponte de Glúteos Unilateral com Pé no Banco
--   secondary_muscles: LEGS, BACK, CORE -> LEGS
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET secondary_muscles = ARRAY['LEGS']::TEXT[], level = 'BEGINNER' WHERE name = 'Ponte de Glúteos Unilateral com Pé no Banco';

-- Ponte de Glúteos com Abdução de Quadril
--   secondary_muscles: CORE, LEGS -> LEGS
UPDATE exercises SET secondary_muscles = ARRAY['LEGS']::TEXT[] WHERE name = 'Ponte de Glúteos com Abdução de Quadril';

-- Ponte de Glúteos com Barra
--   categoria: LEGS -> GLUTES
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HINGE
--   secondary_muscles: (vazio) -> LEGS
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET category = 'GLUTES', modality = 'STRENGTH', movement_pattern = 'HINGE', secondary_muscles = ARRAY['LEGS']::TEXT[], level = 'BEGINNER' WHERE name = 'Ponte de Glúteos com Barra';

-- Ponte de Glúteos com Halter
--   secondary_muscles: LEGS, CORE -> LEGS
UPDATE exercises SET secondary_muscles = ARRAY['LEGS']::TEXT[] WHERE name = 'Ponte de Glúteos com Halter';

-- Ponte de Glúteos com Miniband
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HINGE
--   secondary_muscles: (vazio) -> LEGS
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HINGE', secondary_muscles = ARRAY['LEGS']::TEXT[] WHERE name = 'Ponte de Glúteos com Miniband';

-- Postura da Cobra
--   secondary_muscles: CHEST, BACK -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Postura da Cobra';

-- Postura de Meio Sapo
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> STRETCH
--   primary_muscles: CORE -> LEGS
--   unilateral: não -> sim
--   contraindications: (vazio) -> LUMBAR
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'STRETCH', primary_muscles = ARRAY['LEGS']::TEXT[], unilateral = true, contraindications = ARRAY['LUMBAR']::TEXT[] WHERE name = 'Postura de Meio Sapo';

-- Postura de Virilha Sentado
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> STRETCH
--   primary_muscles: CORE -> LEGS
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> LUMBAR
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'STRETCH', primary_muscles = ARRAY['LEGS']::TEXT[], level = 'BEGINNER', contraindications = ARRAY['LUMBAR']::TEXT[] WHERE name = 'Postura de Virilha Sentado';

-- Postura do Arco
--   secondary_muscles: CHEST, SHOULDERS -> CHEST, LEGS
--   contraindications: LUMBAR -> LUMBAR, SHOULDER
UPDATE exercises SET secondary_muscles = ARRAY['CHEST', 'LEGS']::TEXT[], contraindications = ARRAY['LUMBAR', 'SHOULDER']::TEXT[] WHERE name = 'Postura do Arco';

-- Postura do Arco com Balanço
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> NONE
--   primary_muscles: CORE -> BACK
--   secondary_muscles: (vazio) -> CHEST, LEGS
--   prescription_type: TIME -> REPS
--   contraindications: (vazio) -> LUMBAR, SHOULDER
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'NONE', primary_muscles = ARRAY['BACK']::TEXT[], secondary_muscles = ARRAY['CHEST', 'LEGS']::TEXT[], prescription_type = 'REPS', contraindications = ARRAY['LUMBAR', 'SHOULDER']::TEXT[] WHERE name = 'Postura do Arco com Balanço';

-- Postura do Bebê Feliz
--   secondary_muscles: LEGS, CORE -> LEGS
UPDATE exercises SET secondary_muscles = ARRAY['LEGS']::TEXT[] WHERE name = 'Postura do Bebê Feliz';

-- Postura do Peixe
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> STRETCH
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'STRETCH' WHERE name = 'Postura do Peixe';

-- Postura do Sapo
--   secondary_muscles: GLUTES -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Postura do Sapo';

-- Power Clean com Barra
--   modality: (vazio) -> STRENGTH
--   primary_muscles: LEGS, BACK -> GLUTES, LEGS
--   secondary_muscles: GLUTES, SHOULDERS, CORE -> SHOULDERS, BACK
--   contraindications: LUMBAR, SHOULDER, KNEE -> LUMBAR, SHOULDER, KNEE, WRIST
UPDATE exercises SET modality = 'STRENGTH', primary_muscles = ARRAY['GLUTES', 'LEGS']::TEXT[], secondary_muscles = ARRAY['SHOULDERS', 'BACK']::TEXT[], contraindications = ARRAY['LUMBAR', 'SHOULDER', 'KNEE', 'WRIST']::TEXT[] WHERE name = 'Power Clean com Barra';

-- Power Clean com Halteres
--   modality: (vazio) -> STRENGTH
--   primary_muscles: LEGS, BACK -> GLUTES, LEGS
--   secondary_muscles: GLUTES, SHOULDERS, CORE -> SHOULDERS, BACK
--   level: ADVANCED -> INTERMEDIATE
UPDATE exercises SET modality = 'STRENGTH', primary_muscles = ARRAY['GLUTES', 'LEGS']::TEXT[], secondary_muscles = ARRAY['SHOULDERS', 'BACK']::TEXT[], level = 'INTERMEDIATE' WHERE name = 'Power Clean com Halteres';

-- Prancha
--   secondary_muscles: SHOULDERS -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Prancha';

-- Protração e Retração Escapular
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> NONE
--   primary_muscles: CORE -> BACK
--   secondary_muscles: (vazio) -> CHEST
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'NONE', primary_muscles = ARRAY['BACK']::TEXT[], secondary_muscles = ARRAY['CHEST']::TEXT[], level = 'BEGINNER' WHERE name = 'Protração e Retração Escapular';

-- Pular Corda
--   movement_pattern: GAIT -> NONE
--   secondary_muscles: SHOULDERS, CORE -> (vazio)
UPDATE exercises SET movement_pattern = 'NONE', secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Pular Corda';

-- Pull-Through Ajoelhado no Cabo
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HINGE
--   primary_muscles: BACK -> GLUTES
--   secondary_muscles: (vazio) -> LEGS
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HINGE', primary_muscles = ARRAY['GLUTES']::TEXT[], secondary_muscles = ARRAY['LEGS']::TEXT[], level = 'BEGINNER' WHERE name = 'Pull-Through Ajoelhado no Cabo';

-- Pull-Through com Faixa Elástica
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HINGE
--   primary_muscles: SHOULDERS -> GLUTES
--   secondary_muscles: (vazio) -> LEGS
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HINGE', primary_muscles = ARRAY['GLUTES']::TEXT[], secondary_muscles = ARRAY['LEGS']::TEXT[] WHERE name = 'Pull-Through com Faixa Elástica';

-- Pulldown Inclinado com Corda
--   is_compound: sim -> não
UPDATE exercises SET is_compound = false WHERE name = 'Pulldown Inclinado com Corda';

-- Pulldown Unilateral com Braço Estendido no Cabo
--   is_compound: sim -> não
--   contraindications: SHOULDER -> (vazio)
UPDATE exercises SET is_compound = false, contraindications = ARRAY[]::TEXT[] WHERE name = 'Pulldown Unilateral com Braço Estendido no Cabo';

-- Pulldown com Braços Estendidos na Corda
--   is_compound: sim -> não
UPDATE exercises SET is_compound = false WHERE name = 'Pulldown com Braços Estendidos na Corda';

-- Pullover Deitado no Cabo
--   secondary_muscles: CORE -> SHOULDERS
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS']::TEXT[] WHERE name = 'Pullover Deitado no Cabo';

-- Pullover Sentado no Cabo
--   secondary_muscles: SHOULDERS, CORE -> SHOULDERS
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS']::TEXT[] WHERE name = 'Pullover Sentado no Cabo';

-- Pullover com Barra
--   secondary_muscles: CORE -> SHOULDERS
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS']::TEXT[] WHERE name = 'Pullover com Barra';

-- Pullover com Barra no Banco Declinado
--   secondary_muscles: CORE -> SHOULDERS
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS']::TEXT[] WHERE name = 'Pullover com Barra no Banco Declinado';

-- Pullover com Halter
--   secondary_muscles: CORE -> SHOULDERS
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS']::TEXT[] WHERE name = 'Pullover com Halter';

-- Pullover com Halter na Bola Suíça
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> VERTICAL_PULL
--   primary_muscles: CHEST -> CHEST, BACK
--   secondary_muscles: (vazio) -> SHOULDERS
--   contraindications: (vazio) -> SHOULDER, LUMBAR
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'VERTICAL_PULL', primary_muscles = ARRAY['CHEST', 'BACK']::TEXT[], secondary_muscles = ARRAY['SHOULDERS']::TEXT[], contraindications = ARRAY['SHOULDER', 'LUMBAR']::TEXT[] WHERE name = 'Pullover com Halter na Bola Suíça';

-- Pullover com Halteres e Pernas Elevadas
--   primary_muscles: BACK, CHEST -> CHEST, BACK
--   secondary_muscles: SHOULDERS, CORE -> SHOULDERS
UPDATE exercises SET primary_muscles = ARRAY['CHEST', 'BACK']::TEXT[], secondary_muscles = ARRAY['SHOULDERS']::TEXT[] WHERE name = 'Pullover com Halteres e Pernas Elevadas';

-- Pullover na Máquina (Alavanca)
--   is_compound: sim -> não
UPDATE exercises SET is_compound = false WHERE name = 'Pullover na Máquina (Alavanca)';

-- Puxada Ajoelhado com Faixa Elástica
--   secondary_muscles: CORE -> SHOULDERS, BICEPS
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[] WHERE name = 'Puxada Ajoelhado com Faixa Elástica';

-- Puxada Alta
--   secondary_muscles: SHOULDERS -> SHOULDERS, BICEPS
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[] WHERE name = 'Puxada Alta';

-- Puxada Alta Neutra Ajoelhado com Dois Cabos
--   secondary_muscles: SHOULDERS -> SHOULDERS, BICEPS
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[] WHERE name = 'Puxada Alta Neutra Ajoelhado com Dois Cabos';

-- Puxada Alta Pegada Fechada
--   secondary_muscles: SHOULDERS -> SHOULDERS, BICEPS
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[] WHERE name = 'Puxada Alta Pegada Fechada';

-- Puxada Alta Pegada Supinada
--   secondary_muscles: SHOULDERS -> SHOULDERS, BICEPS
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[] WHERE name = 'Puxada Alta Pegada Supinada';

-- Puxada Alta com Triângulo
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> VERTICAL_PULL
--   equipment: MACHINE -> CABLE
--   secondary_muscles: (vazio) -> SHOULDERS, BICEPS
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'VERTICAL_PULL', equipment = 'CABLE', secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[] WHERE name = 'Puxada Alta com Triângulo';

-- Puxada Alta na Máquina (Alavanca)
--   secondary_muscles: SHOULDERS -> SHOULDERS, BICEPS
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[] WHERE name = 'Puxada Alta na Máquina (Alavanca)';

-- Puxada Cruzada no Crossover
--   movement_pattern: HORIZONTAL_PULL -> VERTICAL_PULL
--   secondary_muscles: SHOULDERS, CORE -> SHOULDERS, BICEPS
--   unilateral: sim -> não
UPDATE exercises SET movement_pattern = 'VERTICAL_PULL', secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[], unilateral = false WHERE name = 'Puxada Cruzada no Crossover';

-- Puxada Escapular na Barra Fixa
--   secondary_muscles: SHOULDERS -> (vazio)
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Puxada Escapular na Barra Fixa';

-- Puxada Frontal na Máquina (Alavanca)
--   movement_pattern: HORIZONTAL_PULL -> VERTICAL_PULL
--   secondary_muscles: SHOULDERS, CORE -> SHOULDERS, BICEPS
UPDATE exercises SET movement_pattern = 'VERTICAL_PULL', secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[] WHERE name = 'Puxada Frontal na Máquina (Alavanca)';

-- Puxada Unilateral Semiajoelhado no Cabo
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> VERTICAL_PULL
--   secondary_muscles: (vazio) -> SHOULDERS, BICEPS
--   unilateral: não -> sim
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'VERTICAL_PULL', secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[], unilateral = true WHERE name = 'Puxada Unilateral Semiajoelhado no Cabo';

-- Puxada Unilateral Sentado no Cabo
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> VERTICAL_PULL
--   secondary_muscles: (vazio) -> SHOULDERS, BICEPS
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'VERTICAL_PULL', secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[], level = 'BEGINNER' WHERE name = 'Puxada Unilateral Sentado no Cabo';

-- Puxada Unilateral em Pé com Rotação no Cabo
--   secondary_pattern: (vazio) -> HORIZONTAL_PULL
--   secondary_muscles: BACK, SHOULDERS -> BACK, SHOULDERS, BICEPS
--   unilateral: não -> sim
UPDATE exercises SET secondary_pattern = 'HORIZONTAL_PULL', secondary_muscles = ARRAY['BACK', 'SHOULDERS', 'BICEPS']::TEXT[], unilateral = true WHERE name = 'Puxada Unilateral em Pé com Rotação no Cabo';

-- Puxada atrás da Nuca na Máquina
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> VERTICAL_PULL
--   secondary_muscles: (vazio) -> SHOULDERS, BICEPS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'VERTICAL_PULL', secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Puxada atrás da Nuca na Máquina';

-- Puxada atrás da Nuca na Polia
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> VERTICAL_PULL
--   secondary_muscles: (vazio) -> SHOULDERS, BICEPS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'VERTICAL_PULL', secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Puxada atrás da Nuca na Polia';

-- Pêndulo de Ombro (Codman)
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> NONE
--   unilateral: não -> sim
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'NONE', unilateral = true, level = 'BEGINNER' WHERE name = 'Pêndulo de Ombro (Codman)';

-- Remada Alta Unilateral com Halter
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   secondary_muscles: BACK -> BACK, BICEPS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', secondary_muscles = ARRAY['BACK', 'BICEPS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Remada Alta Unilateral com Halter';

-- Remada Alta com Barra
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   secondary_muscles: BACK -> BACK, BICEPS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', secondary_muscles = ARRAY['BACK', 'BICEPS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Remada Alta com Barra';

-- Remada Alta com Barra W
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   secondary_muscles: BACK -> BACK, BICEPS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', secondary_muscles = ARRAY['BACK', 'BICEPS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Remada Alta com Barra W';

-- Remada Alta com Halter Único
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   secondary_muscles: BACK -> BACK, BICEPS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', secondary_muscles = ARRAY['BACK', 'BICEPS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Remada Alta com Halter Único';

-- Remada Alta com Halteres
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   equipment: BARBELL -> DUMBBELL
--   secondary_muscles: BACK -> BACK, BICEPS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', equipment = 'DUMBBELL', secondary_muscles = ARRAY['BACK', 'BICEPS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Remada Alta com Halteres';

-- Remada Alta no Cabo
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   secondary_muscles: BACK -> BACK, BICEPS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', secondary_muscles = ARRAY['BACK', 'BICEPS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Remada Alta no Cabo';

-- Remada Apoiada no Banco Inclinado Pegada Neutra com Halteres
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PULL
--   secondary_muscles: (vazio) -> SHOULDERS, BICEPS
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PULL', secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[], level = 'BEGINNER' WHERE name = 'Remada Apoiada no Banco Inclinado Pegada Neutra com Halteres';

-- Remada Apoiada no Banco Inclinado Pegada Supinada com Halteres
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PULL
--   secondary_muscles: (vazio) -> SHOULDERS, BICEPS
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PULL', secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[], level = 'BEGINNER' WHERE name = 'Remada Apoiada no Banco Inclinado Pegada Supinada com Halteres';

-- Remada Apoiada no Banco no Cabo
--   secondary_muscles: SHOULDERS -> SHOULDERS, BICEPS
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[], level = 'BEGINNER' WHERE name = 'Remada Apoiada no Banco no Cabo';

-- Remada Cavalinho Apoiada na Máquina
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PULL
--   secondary_muscles: (vazio) -> SHOULDERS, BICEPS
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PULL', secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[] WHERE name = 'Remada Cavalinho Apoiada na Máquina';

-- Remada Cavalinho na Máquina (Alavanca)
--   secondary_muscles: SHOULDERS -> SHOULDERS, BICEPS
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[] WHERE name = 'Remada Cavalinho na Máquina (Alavanca)';

-- Remada Cavalinho no Landmine
--   secondary_muscles: SHOULDERS, CORE -> SHOULDERS, BICEPS
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[] WHERE name = 'Remada Cavalinho no Landmine';

-- Remada Curvada Pegada Supinada com Barra
--   secondary_muscles: SHOULDERS -> SHOULDERS, BICEPS
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[] WHERE name = 'Remada Curvada Pegada Supinada com Barra';

-- Remada Curvada Pegada Supinada com Halteres
--   secondary_muscles: SHOULDERS -> SHOULDERS, BICEPS
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[] WHERE name = 'Remada Curvada Pegada Supinada com Halteres';

-- Remada Curvada com Barra
--   secondary_muscles: CORE -> SHOULDERS, BICEPS
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[] WHERE name = 'Remada Curvada com Barra';

-- Remada Curvada com Barra Pegada Mista
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PULL
--   secondary_muscles: (vazio) -> SHOULDERS, BICEPS
--   unilateral: sim -> não
--   contraindications: (vazio) -> LUMBAR
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PULL', secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[], unilateral = false, contraindications = ARRAY['LUMBAR']::TEXT[] WHERE name = 'Remada Curvada com Barra Pegada Mista';

-- Remada Curvada com Halteres
--   secondary_muscles: SHOULDERS -> SHOULDERS, BICEPS
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[] WHERE name = 'Remada Curvada com Halteres';

-- Remada Curvada com Kettlebells
--   modality: (vazio) -> STRENGTH
--   secondary_muscles: SHOULDERS, CORE -> SHOULDERS, BICEPS
--   unilateral: sim -> não
UPDATE exercises SET modality = 'STRENGTH', secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[], unilateral = false WHERE name = 'Remada Curvada com Kettlebells';

-- Remada Curvada no Cabo
--   secondary_muscles: SHOULDERS -> SHOULDERS, BICEPS
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[] WHERE name = 'Remada Curvada no Cabo';

-- Remada Curvada no Smith
--   equipment: MACHINE -> SMITH
--   secondary_muscles: CORE -> SHOULDERS, BICEPS
--   level: BEGINNER -> INTERMEDIATE
UPDATE exercises SET equipment = 'SMITH', secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[], level = 'INTERMEDIATE' WHERE name = 'Remada Curvada no Smith';

-- Remada Deitado no Banco com Halteres
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PULL
--   is_compound: (vazio) -> sim
--   equipment: (vazio) -> DUMBBELL
--   secondary_muscles: (vazio) -> SHOULDERS, BICEPS
--   unilateral: (vazio) -> não
--   prescription_type: (vazio) -> REPS
--   level: (vazio) -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PULL', is_compound = true, equipment = 'DUMBBELL', secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[], unilateral = false, prescription_type = 'REPS', level = 'BEGINNER' WHERE name = 'Remada Deitado no Banco com Halteres';

-- Remada Espingarda no Cabo
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PULL
--   secondary_muscles: (vazio) -> SHOULDERS, BICEPS
--   unilateral: não -> sim
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PULL', secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[], unilateral = true, level = 'BEGINNER' WHERE name = 'Remada Espingarda no Cabo';

-- Remada Invertida na Mesa
--   secondary_muscles: SHOULDERS, CORE -> SHOULDERS, BICEPS
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[] WHERE name = 'Remada Invertida na Mesa';

-- Remada Invertida nas Argolas
--   equipment: BODYWEIGHT -> SUSPENSION
--   secondary_muscles: SHOULDERS, CORE -> SHOULDERS, BICEPS
UPDATE exercises SET equipment = 'SUSPENSION', secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[] WHERE name = 'Remada Invertida nas Argolas';

-- Remada Invertida no Smith
--   secondary_muscles: CORE -> SHOULDERS, BICEPS
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[] WHERE name = 'Remada Invertida no Smith';

-- Remada Renegada com Halteres
--   primary_muscles: BACK, CORE -> BACK
--   secondary_muscles: SHOULDERS -> SHOULDERS, BICEPS
--   contraindications: LUMBAR -> SHOULDER, LUMBAR
UPDATE exercises SET primary_muscles = ARRAY['BACK']::TEXT[], secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[], contraindications = ARRAY['SHOULDER', 'LUMBAR']::TEXT[] WHERE name = 'Remada Renegada com Halteres';

-- Remada Sentada Unilateral com Rotação no Cabo
--   secondary_muscles: SHOULDERS, CORE -> SHOULDERS, BICEPS, CORE
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS', 'BICEPS', 'CORE']::TEXT[] WHERE name = 'Remada Sentada Unilateral com Rotação no Cabo';

-- Remada Sentada com Faixa Elástica
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PULL
--   secondary_muscles: (vazio) -> SHOULDERS, BICEPS
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PULL', secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[] WHERE name = 'Remada Sentada com Faixa Elástica';

-- Remada Sentada na Máquina
--   secondary_muscles: CORE -> SHOULDERS, BICEPS
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[] WHERE name = 'Remada Sentada na Máquina';

-- Remada Sentada na Máquina com Anilhas
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PULL
--   equipment: PLATE -> MACHINE
--   secondary_muscles: (vazio) -> SHOULDERS, BICEPS
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PULL', equipment = 'MACHINE', secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[], level = 'BEGINNER' WHERE name = 'Remada Sentada na Máquina com Anilhas';

-- Remada Sentada na Polia com Pegador Reto
--   secondary_muscles: CORE -> SHOULDERS, BICEPS
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[] WHERE name = 'Remada Sentada na Polia com Pegador Reto';

-- Remada Sentada no Cabo
--   secondary_muscles: SHOULDERS -> SHOULDERS, BICEPS
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[] WHERE name = 'Remada Sentada no Cabo';

-- Remada Sentada no Cabo Pegada Fechada
--   secondary_muscles: SHOULDERS -> SHOULDERS, BICEPS
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[] WHERE name = 'Remada Sentada no Cabo Pegada Fechada';

-- Remada Serrote com Halter
--   secondary_muscles: SHOULDERS -> SHOULDERS, BICEPS
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[] WHERE name = 'Remada Serrote com Halter';

-- Remada Unilateral Aberta com Halter (Deltoide Posterior)
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PULL
--   secondary_muscles: (vazio) -> BACK, BICEPS
--   unilateral: não -> sim
--   contraindications: (vazio) -> LUMBAR
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PULL', secondary_muscles = ARRAY['BACK', 'BICEPS']::TEXT[], unilateral = true, contraindications = ARRAY['LUMBAR']::TEXT[] WHERE name = 'Remada Unilateral Aberta com Halter (Deltoide Posterior)';

-- Remada Unilateral com Gymstick
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   equipment: BODYWEIGHT -> GYMSTICK
--   primary_muscles: BACK -> SHOULDERS
--   secondary_muscles: (vazio) -> BACK, BICEPS
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', equipment = 'GYMSTICK', primary_muscles = ARRAY['SHOULDERS']::TEXT[], secondary_muscles = ARRAY['BACK', 'BICEPS']::TEXT[], level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Remada Unilateral com Gymstick';

-- Remada Unilateral no Landmine
--   secondary_muscles: SHOULDERS, CORE -> SHOULDERS, BICEPS
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[] WHERE name = 'Remada Unilateral no Landmine';

-- Remada com Barra Apoiada no Banco Inclinado
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PULL
--   secondary_muscles: (vazio) -> SHOULDERS, BICEPS
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PULL', secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[], level = 'BEGINNER' WHERE name = 'Remada com Barra Apoiada no Banco Inclinado';

-- Remada com Barra atrás do Corpo
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PULL
--   secondary_muscles: (vazio) -> SHOULDERS, BICEPS
--   contraindications: (vazio) -> LUMBAR, SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PULL', secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[], contraindications = ARRAY['LUMBAR', 'SHOULDER']::TEXT[] WHERE name = 'Remada com Barra atrás do Corpo';

-- Remada com Peso Corporal na Porta
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PULL
--   secondary_muscles: (vazio) -> SHOULDERS, BICEPS
--   unilateral: não -> sim
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PULL', secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[], unilateral = true WHERE name = 'Remada com Peso Corporal na Porta';

-- Remada em Front Lever
--   modality: (vazio) -> STRENGTH
--   movement_pattern: VERTICAL_PULL -> HORIZONTAL_PULL
--   equipment: BODYWEIGHT -> SUSPENSION
--   secondary_muscles: SHOULDERS, CORE -> SHOULDERS, BICEPS
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PULL', equipment = 'SUSPENSION', secondary_muscles = ARRAY['SHOULDERS', 'BICEPS']::TEXT[] WHERE name = 'Remada em Front Lever';

-- Remada para Deltoide Posterior Sentado com Halteres
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PULL
--   secondary_muscles: (vazio) -> BACK, BICEPS
--   contraindications: (vazio) -> LUMBAR
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PULL', secondary_muscles = ARRAY['BACK', 'BICEPS']::TEXT[], contraindications = ARRAY['LUMBAR']::TEXT[] WHERE name = 'Remada para Deltoide Posterior Sentado com Halteres';

-- Remo Ergômetro
--   modality: (vazio) -> CARDIO
--   movement_pattern: (vazio) -> SQUAT
--   secondary_pattern: (vazio) -> HORIZONTAL_PULL
--   primary_muscles: LEGS -> LEGS, BACK
--   secondary_muscles: (vazio) -> GLUTES, BICEPS
--   contraindications: (vazio) -> LUMBAR
UPDATE exercises SET modality = 'CARDIO', movement_pattern = 'SQUAT', secondary_pattern = 'HORIZONTAL_PULL', primary_muscles = ARRAY['LEGS', 'BACK']::TEXT[], secondary_muscles = ARRAY['GLUTES', 'BICEPS']::TEXT[], contraindications = ARRAY['LUMBAR']::TEXT[] WHERE name = 'Remo Ergômetro';

-- Retração Escapular Sentado
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> NONE
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'NONE', level = 'BEGINNER' WHERE name = 'Retração Escapular Sentado';

-- Retroversão Pélvica
--   movement_pattern: STRETCH -> NONE
--   prescription_type: TIME -> REPS
UPDATE exercises SET movement_pattern = 'NONE', prescription_type = 'REPS' WHERE name = 'Retroversão Pélvica';

-- Rolando como uma Bola (Pilates)
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   is_compound: não -> sim
--   equipment: MEDICINE_BALL -> BODYWEIGHT
--   prescription_type: TIME -> REPS
--   contraindications: (vazio) -> LUMBAR
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', is_compound = true, equipment = 'BODYWEIGHT', prescription_type = 'REPS', contraindications = ARRAY['LUMBAR']::TEXT[] WHERE name = 'Rolando como uma Bola (Pilates)';

-- Rollout na Bola Suíça
--   secondary_muscles: SHOULDERS -> BACK
UPDATE exercises SET secondary_muscles = ARRAY['BACK']::TEXT[] WHERE name = 'Rollout na Bola Suíça';

-- Rolo de Punho (Rolinho de Antebraço)
--   equipment: BODYWEIGHT -> WEIGHT_PLATE
--   contraindications: (vazio) -> WRIST, SHOULDER
UPDATE exercises SET equipment = 'WEIGHT_PLATE', contraindications = ARRAY['WRIST', 'SHOULDER']::TEXT[] WHERE name = 'Rolo de Punho (Rolinho de Antebraço)';

-- Rosca Alternada com Halteres
--   unilateral: não -> sim
UPDATE exercises SET unilateral = true WHERE name = 'Rosca Alternada com Halteres';

-- Rosca Alternada no Banco Inclinado com Halteres
--   secondary_muscles: SHOULDERS -> (vazio)
--   unilateral: não -> sim
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[], unilateral = true WHERE name = 'Rosca Alternada no Banco Inclinado com Halteres';

-- Rosca Bíceps Agachado no Cabo
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   contraindications: (vazio) -> WRIST, KNEE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', contraindications = ARRAY['WRIST', 'KNEE']::TEXT[] WHERE name = 'Rosca Bíceps Agachado no Cabo';

-- Rosca Bíceps Alta com Halteres
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Rosca Bíceps Alta com Halteres';

-- Rosca Bíceps Bilateral na Polia Alta
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Rosca Bíceps Bilateral na Polia Alta';

-- Rosca Bíceps Bilateral no Cabo em Banco Inclinado
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Rosca Bíceps Bilateral no Cabo em Banco Inclinado';

-- Rosca Bíceps Deitado no Cabo
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> WRIST
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', level = 'BEGINNER', contraindications = ARRAY['WRIST']::TEXT[] WHERE name = 'Rosca Bíceps Deitado no Cabo';

-- Rosca Bíceps Pegada Fechada com Barra W
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', level = 'BEGINNER' WHERE name = 'Rosca Bíceps Pegada Fechada com Barra W';

-- Rosca Bíceps Sentado com Halteres
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', level = 'BEGINNER' WHERE name = 'Rosca Bíceps Sentado com Halteres';

-- Rosca Bíceps Unilateral Pegada Invertida no Cabo
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   primary_muscles: BICEPS -> FOREARMS
--   secondary_muscles: (vazio) -> BICEPS
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> WRIST
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', primary_muscles = ARRAY['FOREARMS']::TEXT[], secondary_muscles = ARRAY['BICEPS']::TEXT[], level = 'BEGINNER', contraindications = ARRAY['WRIST']::TEXT[] WHERE name = 'Rosca Bíceps Unilateral Pegada Invertida no Cabo';

-- Rosca Bíceps Unilateral com Faixa Elástica
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE' WHERE name = 'Rosca Bíceps Unilateral com Faixa Elástica';

-- Rosca Bíceps Unilateral com Halter
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', level = 'BEGINNER' WHERE name = 'Rosca Bíceps Unilateral com Halter';

-- Rosca Bíceps Unilateral na Polia Alta
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Rosca Bíceps Unilateral na Polia Alta';

-- Rosca Bíceps Unilateral no Cabo
--   secondary_muscles: CORE -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Rosca Bíceps Unilateral no Cabo';

-- Rosca Bíceps com Halteres
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', level = 'BEGINNER' WHERE name = 'Rosca Bíceps com Halteres';

-- Rosca Bíceps com Halteres e Arm Blaster
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', level = 'BEGINNER' WHERE name = 'Rosca Bíceps com Halteres e Arm Blaster';

-- Rosca Bíceps na Máquina (Alavanca)
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE' WHERE name = 'Rosca Bíceps na Máquina (Alavanca)';

-- Rosca Bíceps na Polia Alta Sentado
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Rosca Bíceps na Polia Alta Sentado';

-- Rosca Concentrada com Barra Sentado
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   equipment: DUMBBELL -> BARBELL
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> WRIST
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', equipment = 'BARBELL', level = 'BEGINNER', contraindications = ARRAY['WRIST']::TEXT[] WHERE name = 'Rosca Concentrada com Barra Sentado';

-- Rosca Concentrada com Resistência da Perna
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   unilateral: não -> sim
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', unilateral = true, level = 'BEGINNER' WHERE name = 'Rosca Concentrada com Resistência da Perna';

-- Rosca Concentrada no Cabo
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   unilateral: não -> sim
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', unilateral = true, level = 'BEGINNER' WHERE name = 'Rosca Concentrada no Cabo';

-- Rosca Direta com Barra Deitado em Banco Alto
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> WRIST
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', level = 'BEGINNER', contraindications = ARRAY['WRIST']::TEXT[] WHERE name = 'Rosca Direta com Barra Deitado em Banco Alto';

-- Rosca Direta com Barra Deitado no Banco Inclinado
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   equipment: DUMBBELL -> BARBELL
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> WRIST
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', equipment = 'BARBELL', level = 'BEGINNER', contraindications = ARRAY['WRIST']::TEXT[] WHERE name = 'Rosca Direta com Barra Deitado no Banco Inclinado';

-- Rosca Direta com Barra Pegada Fechada
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> WRIST
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', level = 'BEGINNER', contraindications = ARRAY['WRIST']::TEXT[] WHERE name = 'Rosca Direta com Barra Pegada Fechada';

-- Rosca Direta com Barra e Arm Blaster
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> WRIST
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', level = 'BEGINNER', contraindications = ARRAY['WRIST']::TEXT[] WHERE name = 'Rosca Direta com Barra e Arm Blaster';

-- Rosca Direta no Cabo com Barra
--   contraindications: (vazio) -> WRIST
UPDATE exercises SET contraindications = ARRAY['WRIST']::TEXT[] WHERE name = 'Rosca Direta no Cabo com Barra';

-- Rosca Inversa com Barra
--   secondary_muscles: (vazio) -> BICEPS
--   contraindications: (vazio) -> WRIST
UPDATE exercises SET secondary_muscles = ARRAY['BICEPS']::TEXT[], contraindications = ARRAY['WRIST']::TEXT[] WHERE name = 'Rosca Inversa com Barra';

-- Rosca Inversa com Barra W
--   primary_muscles: BICEPS -> FOREARMS
--   secondary_muscles: SHOULDERS -> BICEPS
UPDATE exercises SET primary_muscles = ARRAY['FOREARMS']::TEXT[], secondary_muscles = ARRAY['BICEPS']::TEXT[] WHERE name = 'Rosca Inversa com Barra W';

-- Rosca Inversa com Halteres
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   primary_muscles: BICEPS -> FOREARMS
--   secondary_muscles: (vazio) -> BICEPS
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> WRIST
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', primary_muscles = ARRAY['FOREARMS']::TEXT[], secondary_muscles = ARRAY['BICEPS']::TEXT[], level = 'BEGINNER', contraindications = ARRAY['WRIST']::TEXT[] WHERE name = 'Rosca Inversa com Halteres';

-- Rosca Martelo Sentado com Halteres
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   secondary_muscles: (vazio) -> FOREARMS
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', secondary_muscles = ARRAY['FOREARMS']::TEXT[], level = 'BEGINNER' WHERE name = 'Rosca Martelo Sentado com Halteres';

-- Rosca Martelo com Faixa Elástica
--   secondary_muscles: (vazio) -> FOREARMS
UPDATE exercises SET secondary_muscles = ARRAY['FOREARMS']::TEXT[] WHERE name = 'Rosca Martelo com Faixa Elástica';

-- Rosca Martelo com Garrafa de Água
--   modality: (vazio) -> STRENGTH
--   secondary_muscles: (vazio) -> FOREARMS
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', secondary_muscles = ARRAY['FOREARMS']::TEXT[], level = 'BEGINNER' WHERE name = 'Rosca Martelo com Garrafa de Água';

-- Rosca Martelo com Halteres
--   secondary_muscles: (vazio) -> FOREARMS
UPDATE exercises SET secondary_muscles = ARRAY['FOREARMS']::TEXT[] WHERE name = 'Rosca Martelo com Halteres';

-- Rosca Martelo com Halteres e Arm Blaster
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   secondary_muscles: (vazio) -> FOREARMS
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', secondary_muscles = ARRAY['FOREARMS']::TEXT[], level = 'BEGINNER' WHERE name = 'Rosca Martelo com Halteres e Arm Blaster';

-- Rosca Martelo no Cabo com Corda
--   secondary_muscles: (vazio) -> FOREARMS
UPDATE exercises SET secondary_muscles = ARRAY['FOREARMS']::TEXT[] WHERE name = 'Rosca Martelo no Cabo com Corda';

-- Rosca Scott Alternada com Halteres
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', level = 'BEGINNER' WHERE name = 'Rosca Scott Alternada com Halteres';

-- Rosca Scott Martelo Unilateral com Halter
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   secondary_muscles: (vazio) -> FOREARMS
--   unilateral: não -> sim
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', secondary_muscles = ARRAY['FOREARMS']::TEXT[], unilateral = true, level = 'BEGINNER' WHERE name = 'Rosca Scott Martelo Unilateral com Halter';

-- Rosca Scott Martelo com Halteres
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   secondary_muscles: (vazio) -> FOREARMS
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', secondary_muscles = ARRAY['FOREARMS']::TEXT[], level = 'BEGINNER' WHERE name = 'Rosca Scott Martelo com Halteres';

-- Rosca Scott Unilateral em Pé com Halter
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   unilateral: não -> sim
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', unilateral = true, level = 'BEGINNER' WHERE name = 'Rosca Scott Unilateral em Pé com Halter';

-- Rosca Scott Unilateral em Pé com Halter (Banco Inclinado)
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', level = 'BEGINNER' WHERE name = 'Rosca Scott Unilateral em Pé com Halter (Banco Inclinado)';

-- Rosca Scott Unilateral no Cabo
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', level = 'BEGINNER' WHERE name = 'Rosca Scott Unilateral no Cabo';

-- Rosca Scott na Máquina (Alavanca)
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE' WHERE name = 'Rosca Scott na Máquina (Alavanca)';

-- Rosca Spider Unilateral Deitado com Halter
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', level = 'BEGINNER' WHERE name = 'Rosca Spider Unilateral Deitado com Halter';

-- Rosca Unilateral com Barra
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   contraindications: (vazio) -> WRIST
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', contraindications = ARRAY['WRIST']::TEXT[] WHERE name = 'Rosca Unilateral com Barra';

-- Rosca Zottman
--   secondary_muscles: (vazio) -> FOREARMS
UPDATE exercises SET secondary_muscles = ARRAY['FOREARMS']::TEXT[] WHERE name = 'Rosca Zottman';

-- Rosca de Punho Neutra com Anilhas
--   equipment: BARBELL -> WEIGHT_PLATE
UPDATE exercises SET equipment = 'WEIGHT_PLATE' WHERE name = 'Rosca de Punho Neutra com Anilhas';

-- Rosca de Punho Reversa com Anilha
--   equipment: BARBELL -> WEIGHT_PLATE
UPDATE exercises SET equipment = 'WEIGHT_PLATE' WHERE name = 'Rosca de Punho Reversa com Anilha';

-- Rotação Cotovelo ao Joelho em Pé
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> ROTATION
--   secondary_muscles: (vazio) -> LEGS
--   unilateral: não -> sim
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'ROTATION', secondary_muscles = ARRAY['LEGS']::TEXT[], unilateral = true, level = 'BEGINNER' WHERE name = 'Rotação Cotovelo ao Joelho em Pé';

-- Rotação Externa de Ombro Ajoelhado no Cabo
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   primary_muscles: CORE -> SHOULDERS
--   unilateral: não -> sim
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', primary_muscles = ARRAY['SHOULDERS']::TEXT[], unilateral = true, contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Rotação Externa de Ombro Ajoelhado no Cabo';

-- Rotação Externa de Ombro Deitado de Lado com Halter
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   unilateral: não -> sim
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', unilateral = true, level = 'BEGINNER' WHERE name = 'Rotação Externa de Ombro Deitado de Lado com Halter';

-- Rotação Externa de Ombro a 90° no Cabo
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   primary_muscles: CORE -> SHOULDERS
--   unilateral: não -> sim
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', primary_muscles = ARRAY['SHOULDERS']::TEXT[], unilateral = true, contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Rotação Externa de Ombro a 90° no Cabo';

-- Rotação Externa de Ombro com Faixa Elástica
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   secondary_muscles: (vazio) -> BACK
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', secondary_muscles = ARRAY['BACK']::TEXT[] WHERE name = 'Rotação Externa de Ombro com Faixa Elástica';

-- Rotação Externa de Ombro com Halter Apoiado no Banco
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   unilateral: não -> sim
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', unilateral = true, contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Rotação Externa de Ombro com Halter Apoiado no Banco';

-- Rotação Externa de Ombro no Cabo
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   unilateral: não -> sim
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', unilateral = true, level = 'BEGINNER' WHERE name = 'Rotação Externa de Ombro no Cabo';

-- Rotação Externa de Ombro sem Carga
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> NONE
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'NONE', level = 'BEGINNER' WHERE name = 'Rotação Externa de Ombro sem Carga';

-- Rotação Externa de Quadril Deitado com Faixa Elástica
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   unilateral: não -> sim
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', unilateral = true WHERE name = 'Rotação Externa de Quadril Deitado com Faixa Elástica';

-- Rotação Externa de Quadril Sentado com Faixa Elástica
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   unilateral: não -> sim
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', unilateral = true WHERE name = 'Rotação Externa de Quadril Sentado com Faixa Elástica';

-- Rotação Externa dos Pés com Faixa Elástica
--   modality: MOBILITY -> STRENGTH
--   movement_pattern: ROTATION -> NONE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE' WHERE name = 'Rotação Externa dos Pés com Faixa Elástica';

-- Rotação Interna de Ombro Sentado no Cabo
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   secondary_muscles: (vazio) -> CHEST
--   unilateral: não -> sim
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', secondary_muscles = ARRAY['CHEST']::TEXT[], unilateral = true, level = 'BEGINNER' WHERE name = 'Rotação Interna de Ombro Sentado no Cabo';

-- Rotação Interna de Ombro a 90° no Cabo
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   primary_muscles: CORE -> SHOULDERS
--   secondary_muscles: (vazio) -> CHEST
--   unilateral: não -> sim
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', primary_muscles = ARRAY['SHOULDERS']::TEXT[], secondary_muscles = ARRAY['CHEST']::TEXT[], unilateral = true, contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Rotação Interna de Ombro a 90° no Cabo';

-- Rotação Interna de Ombro no Cabo
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   secondary_muscles: (vazio) -> CHEST
--   unilateral: não -> sim
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', secondary_muscles = ARRAY['CHEST']::TEXT[], unilateral = true, level = 'BEGINNER' WHERE name = 'Rotação Interna de Ombro no Cabo';

-- Rotação Interna de Ombro sem Carga
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> NONE
--   prescription_type: TIME -> REPS
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'NONE', prescription_type = 'REPS', level = 'BEGINNER' WHERE name = 'Rotação Interna de Ombro sem Carga';

-- Rotação Interna de Quadril Sentado com Faixa Elástica
--   modality: MOBILITY -> STRENGTH
--   movement_pattern: ROTATION -> NONE
--   secondary_muscles: LEGS -> (vazio)
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Rotação Interna de Quadril Sentado com Faixa Elástica';

-- Rotação Torácica Deitado (Open Book)
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> ROTATION
--   secondary_muscles: (vazio) -> CHEST
--   unilateral: não -> sim
--   prescription_type: TIME -> REPS
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'ROTATION', secondary_muscles = ARRAY['CHEST']::TEXT[], unilateral = true, prescription_type = 'REPS', level = 'BEGINNER' WHERE name = 'Rotação Torácica Deitado (Open Book)';

-- Rotação Torácica em Quatro Apoios
--   movement_pattern: STRETCH -> ROTATION
--   prescription_type: TIME -> REPS
UPDATE exercises SET movement_pattern = 'ROTATION', prescription_type = 'REPS' WHERE name = 'Rotação Torácica em Quatro Apoios';

-- Rotação Torácica em Quatro Apoios com Braço Estendido
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> ROTATION
--   secondary_muscles: (vazio) -> BACK
--   unilateral: não -> sim
--   prescription_type: TIME -> REPS
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'ROTATION', secondary_muscles = ARRAY['BACK']::TEXT[], unilateral = true, prescription_type = 'REPS', level = 'BEGINNER' WHERE name = 'Rotação Torácica em Quatro Apoios com Braço Estendido';

-- Rotação da Coluna Deitado
--   unilateral: não -> sim
UPDATE exercises SET unilateral = true WHERE name = 'Rotação da Coluna Deitado';

-- Rotação de Punhos
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> NONE
--   prescription_type: TIME -> REPS
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'NONE', prescription_type = 'REPS' WHERE name = 'Rotação de Punhos';

-- Rotação de Pés e Tornozelos Sentado
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> NONE
--   primary_muscles: CORE -> LEGS
--   prescription_type: TIME -> REPS
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'NONE', primary_muscles = ARRAY['LEGS']::TEXT[], prescription_type = 'REPS', level = 'BEGINNER' WHERE name = 'Rotação de Pés e Tornozelos Sentado';

-- Rotação de Tronco em Pé
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> ROTATION
--   prescription_type: TIME -> REPS
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'ROTATION', prescription_type = 'REPS', level = 'BEGINNER' WHERE name = 'Rotação de Tronco em Pé';

-- Salto Grupado (Tuck Jump)
--   secondary_muscles: CORE -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Salto Grupado (Tuck Jump)';

-- Salto Unilateral para a Frente
--   secondary_muscles: CORE -> (vazio)
--   level: INTERMEDIATE -> ADVANCED
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[], level = 'ADVANCED' WHERE name = 'Salto Unilateral para a Frente';

-- Salto com Abertura em Agachamento Sumô
--   modality: (vazio) -> PLYOMETRIC
--   movement_pattern: (vazio) -> SQUAT
--   primary_muscles: LEGS -> LEGS, GLUTES
--   level: BEGINNER -> INTERMEDIATE
--   contraindications: (vazio) -> IMPACT, KNEE
UPDATE exercises SET modality = 'PLYOMETRIC', movement_pattern = 'SQUAT', primary_muscles = ARRAY['LEGS', 'GLUTES']::TEXT[], level = 'INTERMEDIATE', contraindications = ARRAY['IMPACT', 'KNEE']::TEXT[] WHERE name = 'Salto com Abertura em Agachamento Sumô';

-- Salto com Elevação de Joelho
--   modality: (vazio) -> CARDIO
--   movement_pattern: (vazio) -> GAIT
--   prescription_type: REPS -> TIME
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> IMPACT
UPDATE exercises SET modality = 'CARDIO', movement_pattern = 'GAIT', prescription_type = 'TIME', level = 'BEGINNER', contraindications = ARRAY['IMPACT']::TEXT[] WHERE name = 'Salto com Elevação de Joelho';

-- Salto do Ajoelhado para Agachamento com Barra
--   equipment: BODYWEIGHT -> BARBELL
--   secondary_muscles: CORE -> (vazio)
--   contraindications: IMPACT, KNEE -> IMPACT, KNEE, LUMBAR
UPDATE exercises SET equipment = 'BARBELL', secondary_muscles = ARRAY[]::TEXT[], contraindications = ARRAY['IMPACT', 'KNEE', 'LUMBAR']::TEXT[] WHERE name = 'Salto do Ajoelhado para Agachamento com Barra';

-- Salto em Distância
--   secondary_muscles: CORE -> (vazio)
--   level: ADVANCED -> INTERMEDIATE
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[], level = 'INTERMEDIATE' WHERE name = 'Salto em Distância';

-- Salto na Caixa
--   secondary_muscles: CORE -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Salto na Caixa';

-- Salto na Caixa 2 para 1
--   modality: (vazio) -> PLYOMETRIC
--   movement_pattern: (vazio) -> SQUAT
--   equipment: BODYWEIGHT -> BOX
--   primary_muscles: LEGS -> LEGS, GLUTES
--   unilateral: não -> sim
--   contraindications: (vazio) -> IMPACT, KNEE
UPDATE exercises SET modality = 'PLYOMETRIC', movement_pattern = 'SQUAT', equipment = 'BOX', primary_muscles = ARRAY['LEGS', 'GLUTES']::TEXT[], unilateral = true, contraindications = ARRAY['IMPACT', 'KNEE']::TEXT[] WHERE name = 'Salto na Caixa 2 para 1';

-- Salto na Caixa com Aterrissagem em Pistola
--   modality: (vazio) -> PLYOMETRIC
--   movement_pattern: (vazio) -> SQUAT
--   equipment: BODYWEIGHT -> BOX
--   primary_muscles: LEGS -> LEGS, GLUTES
--   contraindications: (vazio) -> IMPACT, KNEE
UPDATE exercises SET modality = 'PLYOMETRIC', movement_pattern = 'SQUAT', equipment = 'BOX', primary_muscles = ARRAY['LEGS', 'GLUTES']::TEXT[], contraindications = ARRAY['IMPACT', 'KNEE']::TEXT[] WHERE name = 'Salto na Caixa com Aterrissagem em Pistola';

-- Salto para Trás
--   modality: (vazio) -> PLYOMETRIC
--   movement_pattern: (vazio) -> SQUAT
--   primary_muscles: LEGS -> LEGS, GLUTES
--   contraindications: (vazio) -> IMPACT, KNEE
UPDATE exercises SET modality = 'PLYOMETRIC', movement_pattern = 'SQUAT', primary_muscles = ARRAY['LEGS', 'GLUTES']::TEXT[], contraindications = ARRAY['IMPACT', 'KNEE']::TEXT[] WHERE name = 'Salto para Trás';

-- Saltos Pliométricos em Zigue-Zague
--   modality: (vazio) -> PLYOMETRIC
--   movement_pattern: (vazio) -> LUNGE
--   primary_muscles: LEGS -> LEGS, GLUTES
--   unilateral: não -> sim
--   contraindications: (vazio) -> IMPACT, KNEE
UPDATE exercises SET modality = 'PLYOMETRIC', movement_pattern = 'LUNGE', primary_muscles = ARRAY['LEGS', 'GLUTES']::TEXT[], unilateral = true, contraindications = ARRAY['IMPACT', 'KNEE']::TEXT[] WHERE name = 'Saltos Pliométricos em Zigue-Zague';

-- Saltos em Tesoura
--   secondary_muscles: CORE -> SHOULDERS
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS']::TEXT[] WHERE name = 'Saltos em Tesoura';

-- Simulador de Caminhada (Air Walker)
--   modality: (vazio) -> CARDIO
--   movement_pattern: (vazio) -> GAIT
--   secondary_muscles: (vazio) -> GLUTES
--   prescription_type: DISTANCE -> TIME
UPDATE exercises SET modality = 'CARDIO', movement_pattern = 'GAIT', secondary_muscles = ARRAY['GLUTES']::TEXT[], prescription_type = 'TIME' WHERE name = 'Simulador de Caminhada (Air Walker)';

-- Simulador de Escada
--   secondary_muscles: GLUTES, CORE -> GLUTES
UPDATE exercises SET secondary_muscles = ARRAY['GLUTES']::TEXT[] WHERE name = 'Simulador de Escada';

-- Skipping Alto em Deslocamento
--   modality: (vazio) -> CARDIO
--   movement_pattern: (vazio) -> GAIT
--   is_compound: não -> sim
--   primary_muscles: CORE -> LEGS
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> IMPACT
UPDATE exercises SET modality = 'CARDIO', movement_pattern = 'GAIT', is_compound = true, primary_muscles = ARRAY['LEGS']::TEXT[], level = 'BEGINNER', contraindications = ARRAY['IMPACT']::TEXT[] WHERE name = 'Skipping Alto em Deslocamento';

-- Skipping com Impulsão (Power Skip)
--   modality: (vazio) -> PLYOMETRIC
--   movement_pattern: (vazio) -> GAIT
--   primary_muscles: LEGS -> LEGS, GLUTES
--   contraindications: (vazio) -> IMPACT, KNEE
UPDATE exercises SET modality = 'PLYOMETRIC', movement_pattern = 'GAIT', primary_muscles = ARRAY['LEGS', 'GLUTES']::TEXT[], contraindications = ARRAY['IMPACT', 'KNEE']::TEXT[] WHERE name = 'Skipping com Impulsão (Power Skip)';

-- Snap Jump (Prancha para Agachamento)
--   modality: (vazio) -> CARDIO
--   movement_pattern: (vazio) -> SQUAT
--   secondary_muscles: (vazio) -> SHOULDERS, CORE
--   prescription_type: REPS -> TIME
--   contraindications: (vazio) -> SHOULDER, WRIST, IMPACT
UPDATE exercises SET modality = 'CARDIO', movement_pattern = 'SQUAT', secondary_muscles = ARRAY['SHOULDERS', 'CORE']::TEXT[], prescription_type = 'TIME', contraindications = ARRAY['SHOULDER', 'WRIST', 'IMPACT']::TEXT[] WHERE name = 'Snap Jump (Prancha para Agachamento)';

-- Socos Alternados (Boxe)
--   modality: (vazio) -> CARDIO
--   movement_pattern: (vazio) -> STRIKE
--   secondary_muscles: (vazio) -> CHEST, TRICEPS
UPDATE exercises SET modality = 'CARDIO', movement_pattern = 'STRIKE', secondary_muscles = ARRAY['CHEST', 'TRICEPS']::TEXT[] WHERE name = 'Socos Alternados (Boxe)';

-- Sprint com Resistência de Faixa Elástica
--   modality: CARDIO -> PLYOMETRIC
--   secondary_muscles: CORE -> (vazio)
--   level: ADVANCED -> INTERMEDIATE
UPDATE exercises SET modality = 'PLYOMETRIC', secondary_muscles = ARRAY[]::TEXT[], level = 'INTERMEDIATE' WHERE name = 'Sprint com Resistência de Faixa Elástica';

-- Stiff Unilateral com Barra
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HINGE
--   secondary_muscles: (vazio) -> GLUTES
--   level: INTERMEDIATE -> ADVANCED
--   contraindications: (vazio) -> LUMBAR
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HINGE', secondary_muscles = ARRAY['GLUTES']::TEXT[], level = 'ADVANCED', contraindications = ARRAY['LUMBAR']::TEXT[] WHERE name = 'Stiff Unilateral com Barra';

-- Stiff Unilateral com Faixa Elástica
--   primary_muscles: GLUTES, BACK -> LEGS
--   secondary_muscles: LEGS, CORE -> GLUTES
--   unilateral: não -> sim
UPDATE exercises SET primary_muscles = ARRAY['LEGS']::TEXT[], secondary_muscles = ARRAY['GLUTES']::TEXT[], unilateral = true WHERE name = 'Stiff Unilateral com Faixa Elástica';

-- Stiff Unilateral com Halteres
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HINGE
--   secondary_muscles: (vazio) -> GLUTES
--   contraindications: (vazio) -> LUMBAR
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HINGE', secondary_muscles = ARRAY['GLUTES']::TEXT[], contraindications = ARRAY['LUMBAR']::TEXT[] WHERE name = 'Stiff Unilateral com Halteres';

-- Stiff com Barra
--   secondary_muscles: (vazio) -> GLUTES
UPDATE exercises SET secondary_muscles = ARRAY['GLUTES']::TEXT[] WHERE name = 'Stiff com Barra';

-- Stiff com Halteres
--   secondary_muscles: (vazio) -> GLUTES
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET secondary_muscles = ARRAY['GLUTES']::TEXT[], level = 'BEGINNER' WHERE name = 'Stiff com Halteres';

-- Subida Unilateral Assistida na Máquina
--   movement_pattern: HORIZONTAL_PUSH -> LUNGE
--   primary_muscles: CHEST, SHOULDERS -> LEGS, GLUTES
--   secondary_muscles: CORE -> (vazio)
--   contraindications: (vazio) -> KNEE
UPDATE exercises SET movement_pattern = 'LUNGE', primary_muscles = ARRAY['LEGS', 'GLUTES']::TEXT[], secondary_muscles = ARRAY[]::TEXT[], contraindications = ARRAY['KNEE']::TEXT[] WHERE name = 'Subida Unilateral Assistida na Máquina';

-- Subida na Corda sem Pernas
--   modality: (vazio) -> STRENGTH
--   secondary_muscles: SHOULDERS, CORE -> SHOULDERS, BICEPS, FOREARMS
UPDATE exercises SET modality = 'STRENGTH', secondary_muscles = ARRAY['SHOULDERS', 'BICEPS', 'FOREARMS']::TEXT[] WHERE name = 'Subida na Corda sem Pernas';

-- Subida no Banco (Step-Up)
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> LUNGE
--   primary_muscles: GLUTES -> LEGS, GLUTES
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> KNEE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'LUNGE', primary_muscles = ARRAY['LEGS', 'GLUTES']::TEXT[], level = 'BEGINNER', contraindications = ARRAY['KNEE']::TEXT[] WHERE name = 'Subida no Banco (Step-Up)';

-- Subida no Banco com Barra
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> LUNGE
--   is_compound: não -> sim
--   primary_muscles: LEGS -> LEGS, GLUTES
--   contraindications: (vazio) -> KNEE, LUMBAR
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'LUNGE', is_compound = true, primary_muscles = ARRAY['LEGS', 'GLUTES']::TEXT[], contraindications = ARRAY['KNEE', 'LUMBAR']::TEXT[] WHERE name = 'Subida no Banco com Barra';

-- Subida no Banco com Elevação de Joelho
--   modality: (vazio) -> STRENGTH
--   equipment: STEP -> BODYWEIGHT
--   secondary_muscles: CORE -> (vazio)
UPDATE exercises SET modality = 'STRENGTH', equipment = 'BODYWEIGHT', secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Subida no Banco com Elevação de Joelho';

-- Subida no Banco com Elevação de Joelho e Rosca Bíceps
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> LUNGE
--   is_compound: não -> sim
--   equipment: BODYWEIGHT -> DUMBBELL
--   primary_muscles: LEGS -> LEGS, BICEPS
--   secondary_muscles: (vazio) -> GLUTES
--   unilateral: não -> sim
--   contraindications: (vazio) -> KNEE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'LUNGE', is_compound = true, equipment = 'DUMBBELL', primary_muscles = ARRAY['LEGS', 'BICEPS']::TEXT[], secondary_muscles = ARRAY['GLUTES']::TEXT[], unilateral = true, contraindications = ARRAY['KNEE']::TEXT[] WHERE name = 'Subida no Banco com Elevação de Joelho e Rosca Bíceps';

-- Subida no Banco com Halteres
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> LUNGE
--   primary_muscles: LEGS -> LEGS, GLUTES
--   contraindications: (vazio) -> KNEE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'LUNGE', primary_muscles = ARRAY['LEGS', 'GLUTES']::TEXT[], contraindications = ARRAY['KNEE']::TEXT[] WHERE name = 'Subida no Banco com Halteres';

-- Subida no Banco com Toque Cotovelo-Joelho
--   movement_pattern: ROTATION -> LUNGE
--   secondary_pattern: (vazio) -> ROTATION
--   primary_muscles: CORE -> LEGS, CORE
--   secondary_muscles: SHOULDERS, LEGS -> GLUTES
--   contraindications: (vazio) -> KNEE
UPDATE exercises SET movement_pattern = 'LUNGE', secondary_pattern = 'ROTATION', primary_muscles = ARRAY['LEGS', 'CORE']::TEXT[], secondary_muscles = ARRAY['GLUTES']::TEXT[], contraindications = ARRAY['KNEE']::TEXT[] WHERE name = 'Subida no Banco com Toque Cotovelo-Joelho';

-- Subida no Step com Faixa Elástica
--   secondary_muscles: CORE -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Subida no Step com Faixa Elástica';

-- Subida no Step com Halteres
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> LUNGE
--   is_compound: não -> sim
--   primary_muscles: LEGS -> LEGS, GLUTES
--   contraindications: (vazio) -> KNEE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'LUNGE', is_compound = true, primary_muscles = ARRAY['LEGS', 'GLUTES']::TEXT[], contraindications = ARRAY['KNEE']::TEXT[] WHERE name = 'Subida no Step com Halteres';

-- Super-Homem (Superman)
--   secondary_muscles: GLUTES, CORE -> GLUTES
UPDATE exercises SET secondary_muscles = ARRAY['GLUTES']::TEXT[] WHERE name = 'Super-Homem (Superman)';

-- Supino Alternado com Halteres
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   secondary_muscles: (vazio) -> SHOULDERS, TRICEPS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Supino Alternado com Halteres';

-- Supino Declinado Pegada Aberta com Barra
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   secondary_muscles: (vazio) -> SHOULDERS, TRICEPS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Supino Declinado Pegada Aberta com Barra';

-- Supino Declinado Pegada Neutra com Halteres
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   equipment: BARBELL -> DUMBBELL
--   secondary_muscles: (vazio) -> SHOULDERS, TRICEPS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', equipment = 'DUMBBELL', secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Supino Declinado Pegada Neutra com Halteres';

-- Supino Declinado Unilateral Pegada Neutra com Halter
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   secondary_muscles: (vazio) -> SHOULDERS, TRICEPS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Supino Declinado Unilateral Pegada Neutra com Halter';

-- Supino Declinado com Barra
--   secondary_muscles: SHOULDERS -> SHOULDERS, TRICEPS
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[] WHERE name = 'Supino Declinado com Barra';

-- Supino Declinado com Halteres
--   secondary_muscles: SHOULDERS -> SHOULDERS, TRICEPS
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[] WHERE name = 'Supino Declinado com Halteres';

-- Supino Declinado na Máquina (Alavanca)
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   is_compound: (vazio) -> sim
--   equipment: (vazio) -> MACHINE
--   secondary_muscles: (vazio) -> SHOULDERS, TRICEPS
--   unilateral: (vazio) -> não
--   prescription_type: (vazio) -> REPS
--   level: (vazio) -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', is_compound = true, equipment = 'MACHINE', secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], unilateral = false, prescription_type = 'REPS', level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Supino Declinado na Máquina (Alavanca)';

-- Supino Declinado na Máquina Articulada
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   secondary_muscles: (vazio) -> SHOULDERS, TRICEPS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Supino Declinado na Máquina Articulada';

-- Supino Declinado no Smith
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   secondary_muscles: (vazio) -> SHOULDERS, TRICEPS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Supino Declinado no Smith';

-- Supino Fechado
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   primary_muscles: TRICEPS -> CHEST
--   secondary_muscles: (vazio) -> TRICEPS, SHOULDERS
--   contraindications: (vazio) -> SHOULDER, WRIST
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', primary_muscles = ARRAY['CHEST']::TEXT[], secondary_muscles = ARRAY['TRICEPS', 'SHOULDERS']::TEXT[], contraindications = ARRAY['SHOULDER', 'WRIST']::TEXT[] WHERE name = 'Supino Fechado';

-- Supino Fechado Pegada Supinada
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   primary_muscles: TRICEPS -> CHEST
--   secondary_muscles: (vazio) -> TRICEPS, SHOULDERS
--   level: INTERMEDIATE -> ADVANCED
--   contraindications: (vazio) -> SHOULDER, WRIST
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', primary_muscles = ARRAY['CHEST']::TEXT[], secondary_muscles = ARRAY['TRICEPS', 'SHOULDERS']::TEXT[], level = 'ADVANCED', contraindications = ARRAY['SHOULDER', 'WRIST']::TEXT[] WHERE name = 'Supino Fechado Pegada Supinada';

-- Supino Fechado com Halter Único
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   secondary_muscles: (vazio) -> TRICEPS, SHOULDERS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', secondary_muscles = ARRAY['TRICEPS', 'SHOULDERS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Supino Fechado com Halter Único';

-- Supino Fechado no Smith
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   secondary_muscles: (vazio) -> TRICEPS, SHOULDERS
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER, WRIST
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', secondary_muscles = ARRAY['TRICEPS', 'SHOULDERS']::TEXT[], level = 'BEGINNER', contraindications = ARRAY['SHOULDER', 'WRIST']::TEXT[] WHERE name = 'Supino Fechado no Smith';

-- Supino Inclinado 30° Pegada Supinada com Halteres
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   equipment: BARBELL -> DUMBBELL
--   secondary_muscles: (vazio) -> SHOULDERS, TRICEPS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', equipment = 'DUMBBELL', secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Supino Inclinado 30° Pegada Supinada com Halteres';

-- Supino Inclinado Pegada Fechada com Barra
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   secondary_muscles: (vazio) -> TRICEPS, SHOULDERS
--   contraindications: (vazio) -> SHOULDER, WRIST
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', secondary_muscles = ARRAY['TRICEPS', 'SHOULDERS']::TEXT[], contraindications = ARRAY['SHOULDER', 'WRIST']::TEXT[] WHERE name = 'Supino Inclinado Pegada Fechada com Barra';

-- Supino Inclinado Pegada Fechada com Halteres
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   secondary_muscles: (vazio) -> TRICEPS, SHOULDERS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', secondary_muscles = ARRAY['TRICEPS', 'SHOULDERS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Supino Inclinado Pegada Fechada com Halteres';

-- Supino Inclinado Pegada Neutra com Halteres
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   secondary_muscles: (vazio) -> SHOULDERS, TRICEPS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Supino Inclinado Pegada Neutra com Halteres';

-- Supino Inclinado com Barra
--   secondary_muscles: SHOULDERS -> SHOULDERS, TRICEPS
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[] WHERE name = 'Supino Inclinado com Barra';

-- Supino Inclinado com Halteres
--   secondary_muscles: SHOULDERS -> SHOULDERS, TRICEPS
--   level: BEGINNER -> INTERMEDIATE
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], level = 'INTERMEDIATE' WHERE name = 'Supino Inclinado com Halteres';

-- Supino Inclinado na Máquina (Alavanca)
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   is_compound: (vazio) -> sim
--   equipment: (vazio) -> MACHINE
--   secondary_muscles: (vazio) -> SHOULDERS, TRICEPS
--   unilateral: (vazio) -> não
--   prescription_type: (vazio) -> REPS
--   level: (vazio) -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', is_compound = true, equipment = 'MACHINE', secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], unilateral = false, prescription_type = 'REPS', level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Supino Inclinado na Máquina (Alavanca)';

-- Supino Inclinado na Máquina Articulada
--   secondary_muscles: SHOULDERS -> SHOULDERS, TRICEPS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Supino Inclinado na Máquina Articulada';

-- Supino Inclinado na Máquina Pegada Neutra
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   secondary_muscles: (vazio) -> SHOULDERS, TRICEPS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Supino Inclinado na Máquina Pegada Neutra';

-- Supino Inclinado no Cabo
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   secondary_muscles: (vazio) -> SHOULDERS, TRICEPS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Supino Inclinado no Cabo';

-- Supino Inclinado no Smith
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   secondary_muscles: (vazio) -> SHOULDERS, TRICEPS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Supino Inclinado no Smith';

-- Supino Pegada Supinada com Halteres
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   secondary_muscles: (vazio) -> SHOULDERS, TRICEPS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Supino Pegada Supinada com Halteres';

-- Supino Reto Pegada Aberta com Barra
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   secondary_muscles: (vazio) -> SHOULDERS, TRICEPS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Supino Reto Pegada Aberta com Barra';

-- Supino Reto Pegada Fechada com Barra
--   secondary_muscles: SHOULDERS -> TRICEPS, SHOULDERS
UPDATE exercises SET secondary_muscles = ARRAY['TRICEPS', 'SHOULDERS']::TEXT[] WHERE name = 'Supino Reto Pegada Fechada com Barra';

-- Supino Reto Pegada Neutra com Halteres
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   equipment: BARBELL -> DUMBBELL
--   secondary_muscles: (vazio) -> SHOULDERS, TRICEPS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', equipment = 'DUMBBELL', secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Supino Reto Pegada Neutra com Halteres';

-- Supino Reto com Barra
--   secondary_muscles: SHOULDERS -> SHOULDERS, TRICEPS
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[] WHERE name = 'Supino Reto com Barra';

-- Supino Reto com Halteres
--   secondary_muscles: SHOULDERS -> SHOULDERS, TRICEPS
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[] WHERE name = 'Supino Reto com Halteres';

-- Supino Reto na Máquina Articulada
--   secondary_muscles: SHOULDERS -> SHOULDERS, TRICEPS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Supino Reto na Máquina Articulada';

-- Supino Reto no Smith
--   equipment: MACHINE -> SMITH
--   secondary_muscles: SHOULDERS -> SHOULDERS, TRICEPS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET equipment = 'SMITH', secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Supino Reto no Smith';

-- Supino Sentado Pegada Fechada no Cabo
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   secondary_muscles: (vazio) -> TRICEPS, SHOULDERS
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', secondary_muscles = ARRAY['TRICEPS', 'SHOULDERS']::TEXT[], level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Supino Sentado Pegada Fechada no Cabo';

-- Supino Sentado na Máquina
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   secondary_muscles: (vazio) -> SHOULDERS, TRICEPS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Supino Sentado na Máquina';

-- Supino Sentado na Máquina (Alavanca)
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   is_compound: (vazio) -> sim
--   equipment: (vazio) -> MACHINE
--   secondary_muscles: (vazio) -> SHOULDERS, TRICEPS
--   unilateral: (vazio) -> não
--   prescription_type: (vazio) -> REPS
--   level: (vazio) -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', is_compound = true, equipment = 'MACHINE', secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], unilateral = false, prescription_type = 'REPS', level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Supino Sentado na Máquina (Alavanca)';

-- Supino Sentado na Máquina Convergente
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   is_compound: (vazio) -> sim
--   equipment: (vazio) -> MACHINE
--   secondary_muscles: (vazio) -> SHOULDERS, TRICEPS
--   unilateral: (vazio) -> não
--   prescription_type: (vazio) -> REPS
--   level: (vazio) -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', is_compound = true, equipment = 'MACHINE', secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], unilateral = false, prescription_type = 'REPS', level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Supino Sentado na Máquina Convergente';

-- Supino Sentado no Cabo
--   secondary_muscles: SHOULDERS -> SHOULDERS, TRICEPS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Supino Sentado no Cabo';

-- Supino Squeeze com Halteres
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   secondary_muscles: (vazio) -> TRICEPS, SHOULDERS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', secondary_muscles = ARRAY['TRICEPS', 'SHOULDERS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Supino Squeeze com Halteres';

-- Supino Unilateral Pegada Supinada com Halter
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   secondary_muscles: (vazio) -> SHOULDERS, TRICEPS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Supino Unilateral Pegada Supinada com Halter';

-- Supino Unilateral com Kettlebell
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   secondary_muscles: (vazio) -> SHOULDERS, TRICEPS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Supino Unilateral com Kettlebell';

-- Supino Unilateral em Pé no Cabo
--   secondary_muscles: CORE -> SHOULDERS, TRICEPS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Supino Unilateral em Pé no Cabo';

-- Supino Unilateral na Máquina (Alavanca)
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   secondary_muscles: (vazio) -> SHOULDERS, TRICEPS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Supino Unilateral na Máquina (Alavanca)';

-- Supino em Pé com Faixa Elástica
--   primary_muscles: CHEST, SHOULDERS -> CHEST
--   secondary_muscles: CORE -> SHOULDERS, TRICEPS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET primary_muscles = ARRAY['CHEST']::TEXT[], secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Supino em Pé com Faixa Elástica';

-- Supino em Pé no Crossover
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   is_compound: não -> sim
--   secondary_muscles: (vazio) -> SHOULDERS, TRICEPS
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', is_compound = true, secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Supino em Pé no Crossover';

-- Supino no Chão com Barra (Floor Press)
--   secondary_muscles: SHOULDERS -> SHOULDERS, TRICEPS
--   level: BEGINNER -> INTERMEDIATE
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], level = 'INTERMEDIATE', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Supino no Chão com Barra (Floor Press)';

-- Supino no Chão com Kettlebells
--   modality: (vazio) -> STRENGTH
--   secondary_muscles: SHOULDERS, CORE -> SHOULDERS, TRICEPS
--   unilateral: sim -> não
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', secondary_muscles = ARRAY['SHOULDERS', 'TRICEPS']::TEXT[], unilateral = false, contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Supino no Chão com Kettlebells';

-- Suspensão Passiva na Barra
--   modality: STRENGTH -> MOBILITY
--   movement_pattern: VERTICAL_PULL -> STRETCH
--   secondary_muscles: SHOULDERS -> FOREARMS
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'STRETCH', secondary_muscles = ARRAY['FOREARMS']::TEXT[] WHERE name = 'Suspensão Passiva na Barra';

-- Svend Press com Anilha
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   is_compound: não -> sim
--   equipment: PLATE -> WEIGHT_PLATE
--   secondary_muscles: (vazio) -> SHOULDERS
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', is_compound = true, equipment = 'WEIGHT_PLATE', secondary_muscles = ARRAY['SHOULDERS']::TEXT[], level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Svend Press com Anilha';

-- Swing 360 na Barra
--   modality: (vazio) -> SKILL
--   movement_pattern: (vazio) -> VERTICAL_PULL
--   equipment: KETTLEBELL -> BODYWEIGHT
--   primary_muscles: GLUTES -> BACK
--   secondary_muscles: (vazio) -> CORE, SHOULDERS
--   level: INTERMEDIATE -> ADVANCED
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'SKILL', movement_pattern = 'VERTICAL_PULL', equipment = 'BODYWEIGHT', primary_muscles = ARRAY['BACK']::TEXT[], secondary_muscles = ARRAY['CORE', 'SHOULDERS']::TEXT[], level = 'ADVANCED', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Swing 360 na Barra';

-- Swing Unilateral com Kettlebell
--   modality: (vazio) -> STRENGTH
--   secondary_muscles: BACK, SHOULDERS, CORE -> BACK
UPDATE exercises SET modality = 'STRENGTH', secondary_muscles = ARRAY['BACK']::TEXT[] WHERE name = 'Swing Unilateral com Kettlebell';

-- Swing com Gymstick
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HINGE
--   equipment: BAND -> GYMSTICK
--   primary_muscles: CORE -> GLUTES, SHOULDERS
--   secondary_muscles: (vazio) -> LEGS, CHEST
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HINGE', equipment = 'GYMSTICK', primary_muscles = ARRAY['GLUTES', 'SHOULDERS']::TEXT[], secondary_muscles = ARRAY['LEGS', 'CHEST']::TEXT[], level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Swing com Gymstick';

-- Swing com Kettlebell
--   modality: (vazio) -> STRENGTH
--   primary_muscles: GLUTES, BACK -> GLUTES, LEGS
--   secondary_muscles: LEGS, CORE, SHOULDERS -> BACK
UPDATE exercises SET modality = 'STRENGTH', primary_muscles = ARRAY['GLUTES', 'LEGS']::TEXT[], secondary_muscles = ARRAY['BACK']::TEXT[] WHERE name = 'Swing com Kettlebell';

-- Thruster com Barra
--   modality: (vazio) -> STRENGTH
--   movement_pattern: VERTICAL_PUSH -> SQUAT
--   secondary_pattern: (vazio) -> VERTICAL_PUSH
--   primary_muscles: SHOULDERS -> LEGS, SHOULDERS
--   secondary_muscles: CORE, LEGS -> GLUTES, TRICEPS
--   contraindications: SHOULDER, LUMBAR -> SHOULDER, LUMBAR, KNEE, WRIST
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'SQUAT', secondary_pattern = 'VERTICAL_PUSH', primary_muscles = ARRAY['LEGS', 'SHOULDERS']::TEXT[], secondary_muscles = ARRAY['GLUTES', 'TRICEPS']::TEXT[], contraindications = ARRAY['SHOULDER', 'LUMBAR', 'KNEE', 'WRIST']::TEXT[] WHERE name = 'Thruster com Barra';

-- Toque Lateral nos Pés em Pé
--   movement_pattern: STRETCH -> NONE
--   primary_muscles: CORE -> LEGS
--   secondary_muscles: LEGS -> (vazio)
--   contraindications: (vazio) -> LUMBAR
UPDATE exercises SET movement_pattern = 'NONE', primary_muscles = ARRAY['LEGS']::TEXT[], secondary_muscles = ARRAY[]::TEXT[], contraindications = ARRAY['LUMBAR']::TEXT[] WHERE name = 'Toque Lateral nos Pés em Pé';

-- Toque no Balão (Balloon Drill)
--   modality: (vazio) -> CARDIO
--   movement_pattern: (vazio) -> SQUAT
--   primary_muscles: CORE -> LEGS, GLUTES
--   secondary_muscles: (vazio) -> SHOULDERS
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> KNEE
UPDATE exercises SET modality = 'CARDIO', movement_pattern = 'SQUAT', primary_muscles = ARRAY['LEGS', 'GLUTES']::TEXT[], secondary_muscles = ARRAY['SHOULDERS']::TEXT[], level = 'BEGINNER', contraindications = ARRAY['KNEE']::TEXT[] WHERE name = 'Toque no Balão (Balloon Drill)';

-- Toque nos Pés Sentado
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> NONE
--   primary_muscles: CORE -> LEGS, BACK
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> LUMBAR
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'NONE', primary_muscles = ARRAY['LEGS', 'BACK']::TEXT[], level = 'BEGINNER', contraindications = ARRAY['LUMBAR']::TEXT[] WHERE name = 'Toque nos Pés Sentado';

-- Toque nos Pés em Pé
--   modality: (vazio) -> MOBILITY
--   movement_pattern: (vazio) -> NONE
--   primary_muscles: CORE -> LEGS
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> LUMBAR
UPDATE exercises SET modality = 'MOBILITY', movement_pattern = 'NONE', primary_muscles = ARRAY['LEGS']::TEXT[], level = 'BEGINNER', contraindications = ARRAY['LUMBAR']::TEXT[] WHERE name = 'Toque nos Pés em Pé';

-- Tríceps Coice no Cabo
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   unilateral: não -> sim
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', unilateral = true, level = 'BEGINNER' WHERE name = 'Tríceps Coice no Cabo';

-- Tríceps Francês Ajoelhado no Cabo
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Tríceps Francês Ajoelhado no Cabo';

-- Tríceps Francês Sentado com Barra W
--   secondary_muscles: SHOULDERS, CORE -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Tríceps Francês Sentado com Barra W';

-- Tríceps Francês Sentado com Halter (Duas Mãos)
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Tríceps Francês Sentado com Halter (Duas Mãos)';

-- Tríceps Francês Unilateral Sentado com Halter
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Tríceps Francês Unilateral Sentado com Halter';

-- Tríceps Francês Unilateral na Polia Alta
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Tríceps Francês Unilateral na Polia Alta';

-- Tríceps Francês Unilateral no Cabo
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Tríceps Francês Unilateral no Cabo';

-- Tríceps Francês com Faixa Elástica
--   secondary_muscles: SHOULDERS -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Tríceps Francês com Faixa Elástica';

-- Tríceps Francês com Gymstick
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   equipment: BAND -> GYMSTICK
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', equipment = 'GYMSTICK', level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Tríceps Francês com Gymstick';

-- Tríceps Francês em Pé com Barra
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   contraindications: (vazio) -> SHOULDER, WRIST
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', contraindications = ARRAY['SHOULDER', 'WRIST']::TEXT[] WHERE name = 'Tríceps Francês em Pé com Barra';

-- Tríceps Francês em Pé com Gymstick
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   equipment: BAND -> GYMSTICK
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', equipment = 'GYMSTICK', level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Tríceps Francês em Pé com Gymstick';

-- Tríceps Francês em Pé com Halter
--   secondary_muscles: SHOULDERS -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Tríceps Francês em Pé com Halter';

-- Tríceps Francês em Pé na Polia Alta
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Tríceps Francês em Pé na Polia Alta';

-- Tríceps Francês no Banco Inclinado com Halter
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Tríceps Francês no Banco Inclinado com Halter';

-- Tríceps Francês no Banco Inclinado no Cabo
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Tríceps Francês no Banco Inclinado no Cabo';

-- Tríceps Pulley Pegada Supinada
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> WRIST
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', level = 'BEGINNER', contraindications = ARRAY['WRIST']::TEXT[] WHERE name = 'Tríceps Pulley Pegada Supinada';

-- Tríceps Pulley Unilateral
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   equipment: DUMBBELL -> CABLE
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', equipment = 'CABLE', level = 'BEGINNER' WHERE name = 'Tríceps Pulley Unilateral';

-- Tríceps Pulley Unilateral Pegada Supinada
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   equipment: DUMBBELL -> CABLE
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', equipment = 'CABLE', level = 'BEGINNER' WHERE name = 'Tríceps Pulley Unilateral Pegada Supinada';

-- Tríceps Pulley com Barra V
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   level: INTERMEDIATE -> BEGINNER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', level = 'BEGINNER' WHERE name = 'Tríceps Pulley com Barra V';

-- Tríceps Pulley com Faixa Elástica na Estrutura
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE' WHERE name = 'Tríceps Pulley com Faixa Elástica na Estrutura';

-- Tríceps Testa Alternado no Banco Inclinado com Halteres
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Tríceps Testa Alternado no Banco Inclinado com Halteres';

-- Tríceps Testa Declinado Pegada Fechada com Barra
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   equipment: DUMBBELL -> BARBELL
--   contraindications: (vazio) -> SHOULDER, WRIST
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', equipment = 'BARBELL', contraindications = ARRAY['SHOULDER', 'WRIST']::TEXT[] WHERE name = 'Tríceps Testa Declinado Pegada Fechada com Barra';

-- Tríceps Testa Declinado com Halteres
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Tríceps Testa Declinado com Halteres';

-- Tríceps Testa Deitado no Cabo com Barra
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   equipment: BARBELL -> CABLE
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER, WRIST
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', equipment = 'CABLE', level = 'BEGINNER', contraindications = ARRAY['SHOULDER', 'WRIST']::TEXT[] WHERE name = 'Tríceps Testa Deitado no Cabo com Barra';

-- Tríceps Testa Deitado no Cabo com Corda
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   equipment: ROPE -> CABLE
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', equipment = 'CABLE', level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Tríceps Testa Deitado no Cabo com Corda';

-- Tríceps Testa Inclinado com Barra W
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Tríceps Testa Inclinado com Barra W';

-- Tríceps Testa Pegada Neutra com Halteres
--   secondary_muscles: SHOULDERS -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Tríceps Testa Pegada Neutra com Halteres';

-- Tríceps Testa Unilateral Pegada Pronada com Halter
--   unilateral: não -> sim
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET unilateral = true, contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Tríceps Testa Unilateral Pegada Pronada com Halter';

-- Tríceps Testa com Barra
--   secondary_muscles: SHOULDERS, CORE -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Tríceps Testa com Barra';

-- Tríceps Testa com Barra Pegada Supinada
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   contraindications: (vazio) -> SHOULDER, WRIST
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', contraindications = ARRAY['SHOULDER', 'WRIST']::TEXT[] WHERE name = 'Tríceps Testa com Barra Pegada Supinada';

-- Tríceps Testa com Barra W Pegada Fechada atrás da Cabeça
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Tríceps Testa com Barra W Pegada Fechada atrás da Cabeça';

-- Tríceps Testa com Barra atrás da Cabeça
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> NONE
--   contraindications: (vazio) -> SHOULDER, WRIST
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'NONE', contraindications = ARRAY['SHOULDER', 'WRIST']::TEXT[] WHERE name = 'Tríceps Testa com Barra atrás da Cabeça';

-- Tríceps Testa com Faixa Elástica
--   secondary_muscles: SHOULDERS -> (vazio)
UPDATE exercises SET secondary_muscles = ARRAY[]::TEXT[] WHERE name = 'Tríceps Testa com Faixa Elástica';

-- Virada de Pneu
--   modality: (vazio) -> STRENGTH
--   secondary_pattern: (vazio) -> HORIZONTAL_PUSH
--   secondary_muscles: BACK, SHOULDERS, CORE -> BACK, SHOULDERS, CHEST
--   contraindications: LUMBAR, KNEE -> LUMBAR, SHOULDER, KNEE
UPDATE exercises SET modality = 'STRENGTH', secondary_pattern = 'HORIZONTAL_PUSH', secondary_muscles = ARRAY['BACK', 'SHOULDERS', 'CHEST']::TEXT[], contraindications = ARRAY['LUMBAR', 'SHOULDER', 'KNEE']::TEXT[] WHERE name = 'Virada de Pneu';

-- Voador Inclinado na Máquina
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   secondary_muscles: (vazio) -> SHOULDERS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', secondary_muscles = ARRAY['SHOULDERS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Voador Inclinado na Máquina';

-- Voador na Máquina (Alavanca)
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   equipment: CABLE -> MACHINE
--   secondary_muscles: (vazio) -> SHOULDERS
--   level: INTERMEDIATE -> BEGINNER
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', equipment = 'MACHINE', secondary_muscles = ARRAY['SHOULDERS']::TEXT[], level = 'BEGINNER', contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Voador na Máquina (Alavanca)';

-- Voador no Pec Deck
--   modality: (vazio) -> STRENGTH
--   movement_pattern: (vazio) -> HORIZONTAL_PUSH
--   secondary_muscles: (vazio) -> SHOULDERS
--   contraindications: (vazio) -> SHOULDER
UPDATE exercises SET modality = 'STRENGTH', movement_pattern = 'HORIZONTAL_PUSH', secondary_muscles = ARRAY['SHOULDERS']::TEXT[], contraindications = ARRAY['SHOULDER']::TEXT[] WHERE name = 'Voador no Pec Deck';

-- Wall Ball
--   modality: (vazio) -> PLYOMETRIC
--   secondary_muscles: GLUTES, CHEST, CORE -> GLUTES, TRICEPS
UPDATE exercises SET modality = 'PLYOMETRIC', secondary_muscles = ARRAY['GLUTES', 'TRICEPS']::TEXT[] WHERE name = 'Wall Ball';

-- ============================================================================
-- 4. ATRIBUTOS DE SELECAO (147 da lista principal)
-- ============================================================================

-- prioridade: a ordem de escolha dentro do grupo (1 = primeiro).
UPDATE exercises e SET prioridade = v.p FROM (VALUES
    ('Abdução de Quadril Sentado com Faixa Elástica', 14),
    ('Abdução de Quadril em Pé na Polia', 10),
    ('Abertura com Faixa Elástica (Pull-Apart)', 14),
    ('Afundo Reverso', 10),
    ('Afundo com Barra', 12),
    ('Afundo com Halteres', 7),
    ('Agachamento Búlgaro com Halteres', 6),
    ('Agachamento Goblet com Halter', 4),
    ('Agachamento Livre com Barra', 1),
    ('Agachamento Livre com Peso Corporal', 5),
    ('Agachamento Pistola', 11),
    ('Agachamento Sissy', 15),
    ('Agachamento Sissy Ajoelhado', 14),
    ('Agachamento Sumô com Halter', 13),
    ('Agachamento no Hack', 3),
    ('Barra Fixa', 1),
    ('Barra Fixa Assistida na Máquina', 15),
    ('Barra Fixa Pegada Supinada (Chin-Up)', 7),
    ('Barra Fixa com Peso', 12),
    ('Cadeira Abdutora', 8),
    ('Cadeira Adutora', 11),
    ('Cadeira Extensora', 8),
    ('Cadeira Flexora', 4),
    ('Coice de Burro (Quatro Apoios)', 16),
    ('Coice de Glúteo Unilateral na Polia Baixa', 11),
    ('Crossover na Polia Alta', 9),
    ('Crossover na Polia Média', 13),
    ('Crucifixo Inclinado com Halteres', 8),
    ('Crucifixo Inverso Apoiado no Banco Inclinado', 5),
    ('Crucifixo Inverso na Máquina (Voador Invertido)', 9),
    ('Desenvolvimento Arnold', 10),
    ('Desenvolvimento Militar com Barra', 1),
    ('Desenvolvimento Sentado com Faixa Elástica', 13),
    ('Desenvolvimento Sentado com Halteres', 2),
    ('Desenvolvimento na Máquina Articulada', 3),
    ('Elevação Frontal com Halteres', 15),
    ('Elevação Lateral Unilateral Inclinado no Cabo', 7),
    ('Elevação Lateral com Halteres', 4),
    ('Elevação Lateral na Máquina Sentado', 6),
    ('Elevação Pélvica Unilateral no Banco', 7),
    ('Elevação Pélvica com Barra', 1),
    ('Elevação Pélvica com Barra e Pés no Banco', 5),
    ('Elevação Pélvica com Faixa Elástica', 13),
    ('Elevação Pélvica na Máquina', 2),
    ('Elevação Pélvica no Smith', 4),
    ('Elevação de Panturrilha Unilateral', 5),
    ('Elevação de Panturrilha com Faixa Elástica', 7),
    ('Elevação de Panturrilha em Pé com Halteres', 4),
    ('Elevação de Panturrilha em Pé no Degrau', 8),
    ('Elevação de Panturrilha no Leg Press', 3),
    ('Elevação de Panturrilha no Smith', 9),
    ('Elevação de Panturrilha no Step com Halteres', 6),
    ('Extensão de Ombro com Faixa Elástica', 18),
    ('Extensão de Tríceps na Máquina', 9),
    ('Face Pull (Puxada para o Rosto)', 8),
    ('Flexora em Pé Unilateral', 7),
    ('Flexão', 6),
    ('Flexão Declinada', 15),
    ('Flexão Nórdica', 8),
    ('Flexão Pike', 12),
    ('Flexão com Joelhos Apoiados', 18),
    ('Flexão com Um Braço', 17),
    ('Flexão de Pernas Deitado com Faixa Elástica', 9),
    ('Flexão em Parada de Mão na Parede', 11),
    ('Flexão na Parede', 23),
    ('Hand Grip (Flexor de Mão)', 8),
    ('Leg Press 45°', 2),
    ('Levantamento Terra', 2),
    ('Levantamento Terra Unilateral com Peso Corporal', 10),
    ('Mergulho Sentado na Máquina', 8),
    ('Mergulho nas Paralelas com Ênfase no Peitoral', 11),
    ('Mergulho no Banco', 10),
    ('Mesa Flexora', 3),
    ('Panturrilha Burrinho Apoiado no Banco', 10),
    ('Panturrilha Sentado na Máquina (Alavanca)', 2),
    ('Panturrilha em Pé na Máquina', 1),
    ('Ponte de Glúteos', 3),
    ('Ponte de Glúteos com Barra', 15),
    ('Ponte de Glúteos com Halter', 6),
    ('Pull-Through Ajoelhado no Cabo', 9),
    ('Pull-Through com Faixa Elástica', 12),
    ('Pulldown com Braços Estendidos na Corda', 13),
    ('Pullover com Halter', 22),
    ('Puxada Ajoelhado com Faixa Elástica', 16),
    ('Puxada Alta', 2),
    ('Puxada Alta com Triângulo', 8),
    ('Remada Alta com Barra', 17),
    ('Remada Alta no Cabo', 16),
    ('Remada Apoiada no Banco Inclinado Pegada Neutra com Halteres', 10),
    ('Remada Cavalinho na Máquina (Alavanca)', 11),
    ('Remada Cavalinho no Landmine', 19),
    ('Remada Curvada com Barra', 3),
    ('Remada Curvada com Halteres', 9),
    ('Remada Invertida na Mesa', 14),
    ('Remada Sentada com Faixa Elástica', 17),
    ('Remada Sentada na Máquina', 6),
    ('Remada Sentada na Polia com Pegador Reto', 21),
    ('Remada Sentada no Cabo', 5),
    ('Remada Sentada no Cabo Pegada Fechada', 20),
    ('Remada Serrote com Halter', 4),
    ('Rosca Alternada com Halteres', 2),
    ('Rosca Alternada no Banco Inclinado com Halteres', 5),
    ('Rosca Bíceps com Faixa Elástica', 10),
    ('Rosca Bíceps na Máquina', 8),
    ('Rosca Concentrada com Halter', 12),
    ('Rosca Direta com Barra', 7),
    ('Rosca Direta com Barra W', 1),
    ('Rosca Direta no Cabo com Barra', 6),
    ('Rosca Inversa com Barra', 7),
    ('Rosca Inversa com Barra W', 1),
    ('Rosca Inversa com Halteres', 3),
    ('Rosca Martelo com Faixa Elástica', 11),
    ('Rosca Martelo com Halteres', 3),
    ('Rosca Scott com Barra W', 4),
    ('Rosca Scott com Halteres', 9),
    ('Rosca Spider Unilateral Deitado com Halter', 13),
    ('Rosca de Dedos com Halteres', 6),
    ('Rosca de Punho Neutra Sentado com Halter', 5),
    ('Rosca de Punho com Barra sobre o Banco', 2),
    ('Rosca de Punho com Halteres', 4),
    ('Stiff Unilateral com Halteres', 6),
    ('Stiff com Barra', 1),
    ('Stiff com Halteres', 5),
    ('Subida no Banco (Step-Up)', 9),
    ('Supino Declinado com Barra', 19),
    ('Supino Declinado com Halteres', 20),
    ('Supino Inclinado com Barra', 2),
    ('Supino Inclinado com Halteres', 4),
    ('Supino Inclinado na Máquina Articulada', 7),
    ('Supino Reto Pegada Fechada com Barra', 21),
    ('Supino Reto com Barra', 1),
    ('Supino Reto com Halteres', 3),
    ('Supino Reto no Smith', 12),
    ('Supino Sentado na Máquina', 5),
    ('Supino em Pé com Faixa Elástica', 16),
    ('Tríceps Coice com Halter', 13),
    ('Tríceps Francês Sentado com Barra W', 5),
    ('Tríceps Francês Sentado com Halter (Duas Mãos)', 4),
    ('Tríceps Francês com Faixa Elástica', 12),
    ('Tríceps Francês em Pé na Polia Alta', 7),
    ('Tríceps Pulley com Barra', 1),
    ('Tríceps Pulley com Corda', 3),
    ('Tríceps Pulley com Faixa Elástica', 11),
    ('Tríceps Testa Pegada Neutra com Halteres', 6),
    ('Tríceps Testa com Barra', 2),
    ('Voador na Máquina (Pec Deck com Apoio de Antebraço)', 14),
    ('Voador no Pec Deck', 10)
) AS v(name, p) WHERE e.name = v.name;

-- relevancia: 1 = base, 2 = complementar, 3 = variacao
UPDATE exercises SET relevancia = 1 WHERE name IN (  -- 43
    'Agachamento Goblet com Halter',
    'Agachamento Livre com Barra',
    'Agachamento Livre com Peso Corporal',
    'Agachamento no Hack',
    'Barra Fixa',
    'Cadeira Flexora',
    'Crucifixo Inverso Apoiado no Banco Inclinado',
    'Desenvolvimento Militar com Barra',
    'Desenvolvimento Sentado com Halteres',
    'Desenvolvimento na Máquina Articulada',
    'Elevação Lateral com Halteres',
    'Elevação Pélvica com Barra',
    'Elevação Pélvica na Máquina',
    'Elevação de Panturrilha em Pé com Halteres',
    'Elevação de Panturrilha no Leg Press',
    'Flexão',
    'Leg Press 45°',
    'Levantamento Terra',
    'Mesa Flexora',
    'Panturrilha Sentado na Máquina (Alavanca)',
    'Panturrilha em Pé na Máquina',
    'Ponte de Glúteos',
    'Puxada Alta',
    'Remada Curvada com Barra',
    'Remada Sentada na Máquina',
    'Remada Sentada no Cabo',
    'Remada Serrote com Halter',
    'Rosca Alternada com Halteres',
    'Rosca Direta com Barra W',
    'Rosca Inversa com Barra W',
    'Rosca Martelo com Halteres',
    'Rosca de Punho com Barra sobre o Banco',
    'Stiff com Barra',
    'Stiff com Halteres',
    'Supino Inclinado com Barra',
    'Supino Inclinado com Halteres',
    'Supino Reto com Barra',
    'Supino Reto com Halteres',
    'Supino Sentado na Máquina',
    'Tríceps Francês Sentado com Halter (Duas Mãos)',
    'Tríceps Pulley com Barra',
    'Tríceps Pulley com Corda',
    'Tríceps Testa com Barra');
UPDATE exercises SET relevancia = 2 WHERE name IN (  -- 79
    'Abdução de Quadril Sentado com Faixa Elástica',
    'Abdução de Quadril em Pé na Polia',
    'Abertura com Faixa Elástica (Pull-Apart)',
    'Afundo Reverso',
    'Afundo com Halteres',
    'Agachamento Búlgaro com Halteres',
    'Agachamento Pistola',
    'Barra Fixa Assistida na Máquina',
    'Barra Fixa Pegada Supinada (Chin-Up)',
    'Barra Fixa com Peso',
    'Cadeira Abdutora',
    'Cadeira Extensora',
    'Coice de Glúteo Unilateral na Polia Baixa',
    'Crossover na Polia Alta',
    'Crossover na Polia Média',
    'Crucifixo Inclinado com Halteres',
    'Crucifixo Inverso na Máquina (Voador Invertido)',
    'Desenvolvimento Arnold',
    'Desenvolvimento Sentado com Faixa Elástica',
    'Elevação Lateral Unilateral Inclinado no Cabo',
    'Elevação Lateral na Máquina Sentado',
    'Elevação Pélvica Unilateral no Banco',
    'Elevação Pélvica com Barra e Pés no Banco',
    'Elevação Pélvica com Faixa Elástica',
    'Elevação Pélvica no Smith',
    'Elevação de Panturrilha Unilateral',
    'Elevação de Panturrilha com Faixa Elástica',
    'Elevação de Panturrilha em Pé no Degrau',
    'Elevação de Panturrilha no Step com Halteres',
    'Extensão de Ombro com Faixa Elástica',
    'Extensão de Tríceps na Máquina',
    'Face Pull (Puxada para o Rosto)',
    'Flexora em Pé Unilateral',
    'Flexão Declinada',
    'Flexão Nórdica',
    'Flexão Pike',
    'Flexão com Joelhos Apoiados',
    'Flexão com Um Braço',
    'Flexão de Pernas Deitado com Faixa Elástica',
    'Flexão em Parada de Mão na Parede',
    'Levantamento Terra Unilateral com Peso Corporal',
    'Mergulho Sentado na Máquina',
    'Mergulho nas Paralelas com Ênfase no Peitoral',
    'Mergulho no Banco',
    'Ponte de Glúteos com Halter',
    'Pull-Through Ajoelhado no Cabo',
    'Pull-Through com Faixa Elástica',
    'Pulldown com Braços Estendidos na Corda',
    'Puxada Ajoelhado com Faixa Elástica',
    'Puxada Alta com Triângulo',
    'Remada Apoiada no Banco Inclinado Pegada Neutra com Halteres',
    'Remada Cavalinho na Máquina (Alavanca)',
    'Remada Curvada com Halteres',
    'Remada Invertida na Mesa',
    'Remada Sentada com Faixa Elástica',
    'Rosca Alternada no Banco Inclinado com Halteres',
    'Rosca Bíceps com Faixa Elástica',
    'Rosca Bíceps na Máquina',
    'Rosca Direta com Barra',
    'Rosca Direta no Cabo com Barra',
    'Rosca Inversa com Halteres',
    'Rosca Martelo com Faixa Elástica',
    'Rosca Scott com Barra W',
    'Rosca Scott com Halteres',
    'Rosca de Dedos com Halteres',
    'Rosca de Punho Neutra Sentado com Halter',
    'Rosca de Punho com Halteres',
    'Stiff Unilateral com Halteres',
    'Subida no Banco (Step-Up)',
    'Supino Inclinado na Máquina Articulada',
    'Supino Reto no Smith',
    'Supino em Pé com Faixa Elástica',
    'Tríceps Francês Sentado com Barra W',
    'Tríceps Francês com Faixa Elástica',
    'Tríceps Francês em Pé na Polia Alta',
    'Tríceps Pulley com Faixa Elástica',
    'Tríceps Testa Pegada Neutra com Halteres',
    'Voador na Máquina (Pec Deck com Apoio de Antebraço)',
    'Voador no Pec Deck');
UPDATE exercises SET relevancia = 3 WHERE name IN (  -- 25
    'Afundo com Barra',
    'Agachamento Sissy',
    'Agachamento Sissy Ajoelhado',
    'Agachamento Sumô com Halter',
    'Cadeira Adutora',
    'Coice de Burro (Quatro Apoios)',
    'Elevação Frontal com Halteres',
    'Elevação de Panturrilha no Smith',
    'Flexão na Parede',
    'Hand Grip (Flexor de Mão)',
    'Panturrilha Burrinho Apoiado no Banco',
    'Ponte de Glúteos com Barra',
    'Pullover com Halter',
    'Remada Alta com Barra',
    'Remada Alta no Cabo',
    'Remada Cavalinho no Landmine',
    'Remada Sentada na Polia com Pegador Reto',
    'Remada Sentada no Cabo Pegada Fechada',
    'Rosca Concentrada com Halter',
    'Rosca Inversa com Barra',
    'Rosca Spider Unilateral Deitado com Halter',
    'Supino Declinado com Barra',
    'Supino Declinado com Halteres',
    'Supino Reto Pegada Fechada com Barra',
    'Tríceps Coice com Halter');

-- nivel_maximo: ate que nivel o exercicio ainda e boa escolha
UPDATE exercises SET nivel_maximo = 'ADVANCED' WHERE name IN (  -- 144
    'Abdução de Quadril Sentado com Faixa Elástica',
    'Abdução de Quadril em Pé na Polia',
    'Abertura com Faixa Elástica (Pull-Apart)',
    'Afundo Reverso',
    'Afundo com Barra',
    'Afundo com Halteres',
    'Agachamento Búlgaro com Halteres',
    'Agachamento Goblet com Halter',
    'Agachamento Livre com Barra',
    'Agachamento Livre com Peso Corporal',
    'Agachamento Pistola',
    'Agachamento Sissy',
    'Agachamento Sissy Ajoelhado',
    'Agachamento Sumô com Halter',
    'Agachamento no Hack',
    'Barra Fixa',
    'Barra Fixa Pegada Supinada (Chin-Up)',
    'Barra Fixa com Peso',
    'Cadeira Abdutora',
    'Cadeira Adutora',
    'Cadeira Extensora',
    'Cadeira Flexora',
    'Coice de Burro (Quatro Apoios)',
    'Coice de Glúteo Unilateral na Polia Baixa',
    'Crossover na Polia Alta',
    'Crossover na Polia Média',
    'Crucifixo Inclinado com Halteres',
    'Crucifixo Inverso Apoiado no Banco Inclinado',
    'Crucifixo Inverso na Máquina (Voador Invertido)',
    'Desenvolvimento Arnold',
    'Desenvolvimento Militar com Barra',
    'Desenvolvimento Sentado com Faixa Elástica',
    'Desenvolvimento Sentado com Halteres',
    'Desenvolvimento na Máquina Articulada',
    'Elevação Frontal com Halteres',
    'Elevação Lateral Unilateral Inclinado no Cabo',
    'Elevação Lateral com Halteres',
    'Elevação Lateral na Máquina Sentado',
    'Elevação Pélvica Unilateral no Banco',
    'Elevação Pélvica com Barra',
    'Elevação Pélvica com Barra e Pés no Banco',
    'Elevação Pélvica com Faixa Elástica',
    'Elevação Pélvica na Máquina',
    'Elevação Pélvica no Smith',
    'Elevação de Panturrilha Unilateral',
    'Elevação de Panturrilha com Faixa Elástica',
    'Elevação de Panturrilha em Pé com Halteres',
    'Elevação de Panturrilha em Pé no Degrau',
    'Elevação de Panturrilha no Leg Press',
    'Elevação de Panturrilha no Smith',
    'Elevação de Panturrilha no Step com Halteres',
    'Extensão de Ombro com Faixa Elástica',
    'Extensão de Tríceps na Máquina',
    'Face Pull (Puxada para o Rosto)',
    'Flexora em Pé Unilateral',
    'Flexão',
    'Flexão Declinada',
    'Flexão Nórdica',
    'Flexão Pike',
    'Flexão com Um Braço',
    'Flexão de Pernas Deitado com Faixa Elástica',
    'Flexão em Parada de Mão na Parede',
    'Hand Grip (Flexor de Mão)',
    'Leg Press 45°',
    'Levantamento Terra',
    'Levantamento Terra Unilateral com Peso Corporal',
    'Mergulho Sentado na Máquina',
    'Mergulho nas Paralelas com Ênfase no Peitoral',
    'Mergulho no Banco',
    'Mesa Flexora',
    'Panturrilha Burrinho Apoiado no Banco',
    'Panturrilha Sentado na Máquina (Alavanca)',
    'Panturrilha em Pé na Máquina',
    'Ponte de Glúteos',
    'Ponte de Glúteos com Barra',
    'Ponte de Glúteos com Halter',
    'Pull-Through Ajoelhado no Cabo',
    'Pull-Through com Faixa Elástica',
    'Pulldown com Braços Estendidos na Corda',
    'Pullover com Halter',
    'Puxada Ajoelhado com Faixa Elástica',
    'Puxada Alta',
    'Puxada Alta com Triângulo',
    'Remada Alta com Barra',
    'Remada Alta no Cabo',
    'Remada Apoiada no Banco Inclinado Pegada Neutra com Halteres',
    'Remada Cavalinho na Máquina (Alavanca)',
    'Remada Cavalinho no Landmine',
    'Remada Curvada com Barra',
    'Remada Curvada com Halteres',
    'Remada Invertida na Mesa',
    'Remada Sentada com Faixa Elástica',
    'Remada Sentada na Máquina',
    'Remada Sentada na Polia com Pegador Reto',
    'Remada Sentada no Cabo',
    'Remada Sentada no Cabo Pegada Fechada',
    'Remada Serrote com Halter',
    'Rosca Alternada com Halteres',
    'Rosca Alternada no Banco Inclinado com Halteres',
    'Rosca Bíceps com Faixa Elástica',
    'Rosca Bíceps na Máquina',
    'Rosca Concentrada com Halter',
    'Rosca Direta com Barra',
    'Rosca Direta com Barra W',
    'Rosca Direta no Cabo com Barra',
    'Rosca Inversa com Barra',
    'Rosca Inversa com Barra W',
    'Rosca Inversa com Halteres',
    'Rosca Martelo com Faixa Elástica',
    'Rosca Martelo com Halteres',
    'Rosca Scott com Barra W',
    'Rosca Scott com Halteres',
    'Rosca Spider Unilateral Deitado com Halter',
    'Rosca de Dedos com Halteres',
    'Rosca de Punho Neutra Sentado com Halter',
    'Rosca de Punho com Barra sobre o Banco',
    'Rosca de Punho com Halteres',
    'Stiff Unilateral com Halteres',
    'Stiff com Barra',
    'Stiff com Halteres',
    'Subida no Banco (Step-Up)',
    'Supino Declinado com Barra',
    'Supino Declinado com Halteres',
    'Supino Inclinado com Barra',
    'Supino Inclinado com Halteres',
    'Supino Inclinado na Máquina Articulada',
    'Supino Reto Pegada Fechada com Barra',
    'Supino Reto com Barra',
    'Supino Reto com Halteres',
    'Supino Reto no Smith',
    'Supino Sentado na Máquina',
    'Supino em Pé com Faixa Elástica',
    'Tríceps Coice com Halter',
    'Tríceps Francês Sentado com Barra W',
    'Tríceps Francês Sentado com Halter (Duas Mãos)',
    'Tríceps Francês com Faixa Elástica',
    'Tríceps Francês em Pé na Polia Alta',
    'Tríceps Pulley com Barra',
    'Tríceps Pulley com Corda',
    'Tríceps Pulley com Faixa Elástica',
    'Tríceps Testa Pegada Neutra com Halteres',
    'Tríceps Testa com Barra',
    'Voador na Máquina (Pec Deck com Apoio de Antebraço)',
    'Voador no Pec Deck');
UPDATE exercises SET nivel_maximo = 'BEGINNER' WHERE name IN (  -- 2
    'Flexão com Joelhos Apoiados',
    'Flexão na Parede');
UPDATE exercises SET nivel_maximo = 'INTERMEDIATE' WHERE name IN (  -- 1
    'Barra Fixa Assistida na Máquina');

-- ambiente: ACADEMIA + AMBOS para academia; CASA + AMBOS para casa
UPDATE exercises SET ambiente = 'ACADEMIA' WHERE name IN (  -- 70
    'Abdução de Quadril em Pé na Polia',
    'Afundo com Barra',
    'Agachamento Livre com Barra',
    'Agachamento no Hack',
    'Barra Fixa Assistida na Máquina',
    'Barra Fixa com Peso',
    'Cadeira Abdutora',
    'Cadeira Adutora',
    'Cadeira Extensora',
    'Cadeira Flexora',
    'Coice de Glúteo Unilateral na Polia Baixa',
    'Crossover na Polia Alta',
    'Crossover na Polia Média',
    'Crucifixo Inverso na Máquina (Voador Invertido)',
    'Desenvolvimento Militar com Barra',
    'Desenvolvimento na Máquina Articulada',
    'Elevação Lateral Unilateral Inclinado no Cabo',
    'Elevação Lateral na Máquina Sentado',
    'Elevação Pélvica com Barra',
    'Elevação Pélvica com Barra e Pés no Banco',
    'Elevação Pélvica na Máquina',
    'Elevação Pélvica no Smith',
    'Elevação de Panturrilha no Leg Press',
    'Elevação de Panturrilha no Smith',
    'Extensão de Tríceps na Máquina',
    'Face Pull (Puxada para o Rosto)',
    'Flexora em Pé Unilateral',
    'Leg Press 45°',
    'Levantamento Terra',
    'Mergulho Sentado na Máquina',
    'Mesa Flexora',
    'Panturrilha Sentado na Máquina (Alavanca)',
    'Panturrilha em Pé na Máquina',
    'Ponte de Glúteos com Barra',
    'Pull-Through Ajoelhado no Cabo',
    'Pulldown com Braços Estendidos na Corda',
    'Puxada Alta',
    'Puxada Alta com Triângulo',
    'Remada Alta com Barra',
    'Remada Alta no Cabo',
    'Remada Cavalinho na Máquina (Alavanca)',
    'Remada Cavalinho no Landmine',
    'Remada Curvada com Barra',
    'Remada Sentada na Máquina',
    'Remada Sentada na Polia com Pegador Reto',
    'Remada Sentada no Cabo',
    'Remada Sentada no Cabo Pegada Fechada',
    'Rosca Bíceps na Máquina',
    'Rosca Direta com Barra',
    'Rosca Direta com Barra W',
    'Rosca Direta no Cabo com Barra',
    'Rosca Inversa com Barra',
    'Rosca Inversa com Barra W',
    'Rosca Scott com Barra W',
    'Rosca de Punho com Barra sobre o Banco',
    'Stiff com Barra',
    'Supino Declinado com Barra',
    'Supino Inclinado com Barra',
    'Supino Inclinado na Máquina Articulada',
    'Supino Reto Pegada Fechada com Barra',
    'Supino Reto com Barra',
    'Supino Reto no Smith',
    'Supino Sentado na Máquina',
    'Tríceps Francês Sentado com Barra W',
    'Tríceps Francês em Pé na Polia Alta',
    'Tríceps Pulley com Barra',
    'Tríceps Pulley com Corda',
    'Tríceps Testa com Barra',
    'Voador na Máquina (Pec Deck com Apoio de Antebraço)',
    'Voador no Pec Deck');
UPDATE exercises SET ambiente = 'AMBOS' WHERE name IN (  -- 39
    'Afundo com Halteres',
    'Agachamento Búlgaro com Halteres',
    'Agachamento Goblet com Halter',
    'Agachamento Sumô com Halter',
    'Barra Fixa',
    'Barra Fixa Pegada Supinada (Chin-Up)',
    'Crucifixo Inclinado com Halteres',
    'Crucifixo Inverso Apoiado no Banco Inclinado',
    'Desenvolvimento Arnold',
    'Desenvolvimento Sentado com Halteres',
    'Elevação Frontal com Halteres',
    'Elevação Lateral com Halteres',
    'Elevação de Panturrilha em Pé com Halteres',
    'Elevação de Panturrilha no Step com Halteres',
    'Flexão Nórdica',
    'Mergulho nas Paralelas com Ênfase no Peitoral',
    'Ponte de Glúteos com Halter',
    'Pullover com Halter',
    'Remada Apoiada no Banco Inclinado Pegada Neutra com Halteres',
    'Remada Curvada com Halteres',
    'Remada Serrote com Halter',
    'Rosca Alternada com Halteres',
    'Rosca Alternada no Banco Inclinado com Halteres',
    'Rosca Concentrada com Halter',
    'Rosca Inversa com Halteres',
    'Rosca Martelo com Halteres',
    'Rosca Scott com Halteres',
    'Rosca Spider Unilateral Deitado com Halter',
    'Rosca de Dedos com Halteres',
    'Rosca de Punho Neutra Sentado com Halter',
    'Rosca de Punho com Halteres',
    'Stiff Unilateral com Halteres',
    'Stiff com Halteres',
    'Supino Declinado com Halteres',
    'Supino Inclinado com Halteres',
    'Supino Reto com Halteres',
    'Tríceps Coice com Halter',
    'Tríceps Francês Sentado com Halter (Duas Mãos)',
    'Tríceps Testa Pegada Neutra com Halteres');
UPDATE exercises SET ambiente = 'CASA' WHERE name IN (  -- 38
    'Abdução de Quadril Sentado com Faixa Elástica',
    'Abertura com Faixa Elástica (Pull-Apart)',
    'Afundo Reverso',
    'Agachamento Livre com Peso Corporal',
    'Agachamento Pistola',
    'Agachamento Sissy',
    'Agachamento Sissy Ajoelhado',
    'Coice de Burro (Quatro Apoios)',
    'Desenvolvimento Sentado com Faixa Elástica',
    'Elevação Pélvica Unilateral no Banco',
    'Elevação Pélvica com Faixa Elástica',
    'Elevação de Panturrilha Unilateral',
    'Elevação de Panturrilha com Faixa Elástica',
    'Elevação de Panturrilha em Pé no Degrau',
    'Extensão de Ombro com Faixa Elástica',
    'Flexão',
    'Flexão Declinada',
    'Flexão Pike',
    'Flexão com Joelhos Apoiados',
    'Flexão com Um Braço',
    'Flexão de Pernas Deitado com Faixa Elástica',
    'Flexão em Parada de Mão na Parede',
    'Flexão na Parede',
    'Hand Grip (Flexor de Mão)',
    'Levantamento Terra Unilateral com Peso Corporal',
    'Mergulho no Banco',
    'Panturrilha Burrinho Apoiado no Banco',
    'Ponte de Glúteos',
    'Pull-Through com Faixa Elástica',
    'Puxada Ajoelhado com Faixa Elástica',
    'Remada Invertida na Mesa',
    'Remada Sentada com Faixa Elástica',
    'Rosca Bíceps com Faixa Elástica',
    'Rosca Martelo com Faixa Elástica',
    'Subida no Banco (Step-Up)',
    'Supino em Pé com Faixa Elástica',
    'Tríceps Francês com Faixa Elástica',
    'Tríceps Pulley com Faixa Elástica');

-- ============================================================================
-- 5. FAMILIA E ACESSORIOS (893 exercicios, catalogo inteiro)
-- ============================================================================
-- familia: o padrao mecanico. Regra do motor: no maximo um por familia na mesma sessao.
UPDATE exercises SET familia = 'ABDOMINAL' WHERE name IN (  -- 6
    'Abdominal Grupado com Bola Suíça',
    'Ativação Abdominal Deitado',
    'Elevação Lateral das Duas Pernas Deitado',
    'Retroversão Pélvica',
    'Rollout na Bola Suíça',
    'V-Up com Bola Suíça');
UPDATE exercises SET familia = 'ABDUCAO' WHERE name IN (  -- 12
    'Abdução de Quadril Deitado de Lado com Miniband',
    'Abdução de Quadril Sentado com Faixa Elástica',
    'Abdução de Quadril em Prancha Lateral',
    'Abdução de Quadril em Pé',
    'Abdução de Quadril em Pé na Máquina (Alavanca)',
    'Abdução de Quadril em Pé na Polia',
    'Abdução em Quatro Apoios (Fire Hydrant)',
    'Abdução em Quatro Apoios com Miniband (Fire Hydrant)',
    'Cadeira Abdutora',
    'Caminhada Lateral com Miniband',
    'Concha em Decúbito Lateral (Clamshell)',
    'Elevação Lateral de Perna Deitado com Miniband');
UPDATE exercises SET familia = 'ADUCAO' WHERE name IN (  -- 3
    'Adução de Quadril em Pé na Máquina (Alavanca)',
    'Adução de Quadril na Polia',
    'Cadeira Adutora');
UPDATE exercises SET familia = 'AFUNDO' WHERE name IN (  -- 38
    'Afundo Caminhando com Elevação de Joelho',
    'Afundo Cruzado (Curtsy Lunge)',
    'Afundo Cruzado com Barra',
    'Afundo Cruzado com Halter',
    'Afundo Lateral',
    'Afundo Lateral com Barra',
    'Afundo Profundo',
    'Afundo Reverso',
    'Afundo Reverso com Elevação de Joelho',
    'Afundo Reverso com Halteres',
    'Afundo com Barra',
    'Afundo com Faixa Elástica',
    'Afundo com Gymstick',
    'Afundo com Halteres',
    'Afundo com Salto e Halteres',
    'Afundo no Bosu com Elevação de Joelho',
    'Afundo no Cabo',
    'Afundo no Cabo com Braços Estendidos',
    'Afundo no Landmine',
    'Afundo no Smith',
    'Agachamento Búlgaro com Barra',
    'Agachamento Búlgaro com Halteres',
    'Agachamento Búlgaro com Peso Corporal',
    'Agachamento Búlgaro com Salto',
    'Agachamento Cossaco',
    'Agachamento Pistola',
    'Agachamento com Chute Lateral e Toque no Calcanhar',
    'Avanço com Peso Corporal',
    'Descida Unilateral do Step',
    'Subida Unilateral Assistida na Máquina',
    'Subida no Banco (Step-Up)',
    'Subida no Banco com Barra',
    'Subida no Banco com Elevação de Joelho',
    'Subida no Banco com Elevação de Joelho e Rosca Bíceps',
    'Subida no Banco com Halteres',
    'Subida no Banco com Toque Cotovelo-Joelho',
    'Subida no Step com Faixa Elástica',
    'Subida no Step com Halteres');
UPDATE exercises SET familia = 'AGACHAMENTO_HACK' WHERE name IN (  -- 2
    'Agachamento Hack Invertido',
    'Agachamento no Hack');
UPDATE exercises SET familia = 'AGACHAMENTO_LIVRE' WHERE name IN (  -- 41
    'Agachamento Apoiado na Cadeira Abdutora',
    'Agachamento Camarão',
    'Agachamento Frontal com Barra',
    'Agachamento Frontal com Barra no Banco (Box Squat)',
    'Agachamento Frontal com Barra no Rack',
    'Agachamento Frontal com Kettlebells',
    'Agachamento Frontal na Polia',
    'Agachamento Goblet com Halter',
    'Agachamento Goblet com Kettlebell',
    'Agachamento Goblet com Kettlebell e Miniband',
    'Agachamento Hack com Barra',
    'Agachamento Havaiano',
    'Agachamento Isométrico com Elevação de Panturrilhas',
    'Agachamento Isométrico na Parede (Wall Sit)',
    'Agachamento Isométrico na Parede com Inclinação de Tronco',
    'Agachamento Jefferson',
    'Agachamento Livre com Barra',
    'Agachamento Livre com Peso Corporal',
    'Agachamento Patinador (Skater Squat)',
    'Agachamento Pistola Assistido',
    'Agachamento Pistola com Halteres',
    'Agachamento Pistola com Kettlebell',
    'Agachamento Pistola com TRX',
    'Agachamento Pistola em Cima da Caixa',
    'Agachamento Pistola na Caixa',
    'Agachamento Plié com Halter',
    'Agachamento Sumô com Halter',
    'Agachamento Sumô com Peso Corporal',
    'Agachamento Zercher',
    'Agachamento com Barra acima da Cabeça',
    'Agachamento com Elevação de Joelho',
    'Agachamento com Faixa Elástica acima da Cabeça',
    'Agachamento com Gymstick',
    'Agachamento com Salto e Halteres',
    'Agachamento na Parede com Bola Suíça',
    'Agachamento no Banco com Halteres',
    'Agachamento no Banco com Peso Corporal',
    'Agachamento no Belt Squat',
    'Agachamento no Landmine',
    'Agachamento no Rack com Pinos (Pin Squat)',
    'Agachamento no Smith');
UPDATE exercises SET familia = 'ALONGAMENTO_ADUTOR' WHERE name IN (  -- 10
    'Abertura de Pernas Deitado (Alongamento de Adutores)',
    'Adução de Quadril Deitado de Lado',
    'Alongamento Borboleta',
    'Alongamento de Adutores Ajoelhado com Perna Estendida',
    'Alongamento de Adutores Sentado com Pernas Afastadas',
    'Alongamento de Adutores em Afundo Lateral',
    'Alongamento de Adutores em Pé com Flexão de Tronco',
    'Alongamento de Adutores em Pé com Pernas Afastadas',
    'Postura de Virilha Sentado',
    'Postura do Sapo');
UPDATE exercises SET familia = 'ALONGAMENTO_COLUNA' WHERE name IN (  -- 13
    'Alongamento Duplo de Pernas (Pilates)',
    'Alongamento da Esfinge',
    'Alongamento de Rotação da Coluna em Pé',
    'Moinho de Vento com Toque Cruzado no Pé',
    'Postura da Cobra',
    'Postura do Arco',
    'Postura do Arco com Balanço',
    'Rolando como uma Bola (Pilates)',
    'Rotação Torácica Deitado (Open Book)',
    'Rotação Torácica em Quatro Apoios',
    'Rotação Torácica em Quatro Apoios com Braço Estendido',
    'Rotação da Coluna Deitado',
    'Rotação de Tronco em Pé');
UPDATE exercises SET familia = 'ALONGAMENTO_COSTAS' WHERE name IN (  -- 3
    'Alongamento Abraço com Tapinhas nas Costas',
    'Alongamento da Parte Superior das Costas',
    'Alongamento de Dorsal e Peitoral Apoiado no Banco');
UPDATE exercises SET familia = 'ALONGAMENTO_OMBRO' WHERE name IN (  -- 8
    'Alongamento Dinâmico de Ombro com Braço Cruzado',
    'Alongamento Reverso de Ombro em Pé',
    'Alongamento com Bastão na Posição de Front Rack',
    'Alongamento de Ombro com Braço Cruzado',
    'Alongamento de Ombro com Toalha',
    'Alongamento de Ombros por trás das Costas',
    'Alongamento do Manguito Rotador',
    'Pêndulo de Ombro (Codman)');
UPDATE exercises SET familia = 'ALONGAMENTO_PANTURRILHA' WHERE name IN (  -- 9
    'Alongamento de Panturrilha Agachado',
    'Alongamento de Panturrilha Deitado com Faixa',
    'Alongamento de Panturrilha Sentado com Perna Estendida',
    'Alongamento de Panturrilha Unilateral no Degrau',
    'Alongamento de Panturrilha com Antebraços na Parede',
    'Alongamento de Panturrilha com Ponta do Pé na Parede',
    'Alongamento de Panturrilha em Passada Larga',
    'Alongamento de Panturrilha no Degrau',
    'Alongamento do Tendão de Aquiles em Pé');
UPDATE exercises SET familia = 'ALONGAMENTO_PEITORAL' WHERE name IN (  -- 8
    'Alongamento Dinâmico de Peitoral',
    'Alongamento Reverso Assistido (Peitoral e Ombro)',
    'Alongamento de Peitoral Unilateral na Parede',
    'Alongamento de Peitoral acima da Cabeça',
    'Alongamento de Peitoral e Ombro Anterior com Bastão',
    'Alongamento de Peitoral e Ombro na Porta',
    'Alongamento de Peitoral na Quina da Parede',
    'Postura do Peixe');
UPDATE exercises SET familia = 'ALONGAMENTO_POSTERIOR' WHERE name IN (  -- 7
    'Alongamento de Isquiotibiais Deitado',
    'Alongamento de Isquiotibiais Sentado com Pernas Afastadas',
    'Alongamento de Isquiotibiais em Pé',
    'Alongamento de Isquiotibiais em Pé com Pernas Cruzadas',
    'Toque Lateral nos Pés em Pé',
    'Toque nos Pés Sentado',
    'Toque nos Pés em Pé');
UPDATE exercises SET familia = 'ALONGAMENTO_PUNHO' WHERE name IN (  -- 4
    'Alongamento Reverso de Punho',
    'Alongamento de Flexores de Punho em Quatro Apoios',
    'Alongamento de Punho',
    'Rotação de Punhos');
UPDATE exercises SET familia = 'ALONGAMENTO_QUADRICEPS' WHERE name IN (  -- 3
    'Alongamento de Quadríceps Ajoelhado',
    'Alongamento de Quadríceps em Pé',
    'Alongamento de Quadríceps em Quatro Apoios');
UPDATE exercises SET familia = 'ALONGAMENTO_QUADRIL' WHERE name IN (  -- 9
    'Abraço nos Joelhos em Pé',
    'Alongamento de Flexores do Quadril Ajoelhado',
    'Alongamento de Glúteos Deitado',
    'Alongamento de Quadril 90-90',
    'Alongamento do Piriforme Sentado',
    'Alongamento do Piriforme Sentado no Banco',
    'Joelho ao Peito Alternado Deitado',
    'Postura de Meio Sapo',
    'Postura do Bebê Feliz');
UPDATE exercises SET familia = 'ALONGAMENTO_TORNOZELO' WHERE name IN (  -- 7
    'Alongamento do Tibial Posterior com Faixa',
    'Alongamento dos Extensores dos Dedos dos Pés',
    'Alongamento dos Flexores dos Dedos dos Pés em Pé',
    'Flexão Plantar em Pé',
    'Mobilidade de Pés e Tornozelos Sentado',
    'Rotação Externa dos Pés com Faixa Elástica',
    'Rotação de Pés e Tornozelos Sentado');
UPDATE exercises SET familia = 'ARREMESSO' WHERE name IN (  -- 7
    'Abdominal com Arremesso de Bola Medicinal na Parede',
    'Arremesso Rotacional de Bola Medicinal',
    'Arremesso de Bola Medicinal Deitado',
    'Arremesso de Bola Medicinal acima da Cabeça',
    'Arremesso de Bola de Reação',
    'Levantamento de Bola Medicinal do Chão acima da Cabeça',
    'Passe de Peito com Bola Medicinal na Parede');
UPDATE exercises SET familia = 'BURPEE' WHERE name IN (  -- 6
    'Burpee',
    'Burpee Navy SEAL',
    'Burpee com Polichinelo',
    'Devil Press com Halteres',
    'Minhoca (Inchworm)',
    'Snap Jump (Prancha para Agachamento)');
UPDATE exercises SET familia = 'CARREGAMENTO' WHERE name IN (  -- 2
    'Caminhada do Fazendeiro com Halteres',
    'Carregamento Zercher');
UPDATE exercises SET familia = 'COICE' WHERE name IN (  -- 16
    'Coice de Burro (Quatro Apoios)',
    'Coice de Glúteo Unilateral na Polia Baixa',
    'Coice de Glúteo com Gymstick',
    'Coice de Glúteo com Perna Flexionada',
    'Coice de Glúteo com Perna Flexionada e Miniband',
    'Coice de Glúteo em Pé',
    'Coice de Glúteo em Pé com Faixa Elástica',
    'Coice de Glúteo em Quatro Apoios com Faixa Elástica',
    'Coice de Glúteo na Máquina',
    'Coice de Glúteo na Máquina Multi-Hip',
    'Coice de Glúteo no Smith',
    'Extensão de Quadril Alternada Deitado no Banco',
    'Extensão de Quadril Apoiado no Banco',
    'Extensão de Quadril Deitado no Smith',
    'Extensão de Quadril com Perna Estendida em Quatro Apoios',
    'Extensão de Quadril em Pé na Máquina (Alavanca)');
UPDATE exercises SET familia = 'CORDA_NAVAL' WHERE name IN (  -- 1
    'Corda Naval');
UPDATE exercises SET familia = 'CORRIDA' WHERE name IN (  -- 26
    'Caminhada',
    'Caminhada na Esteira Inclinada',
    'Chute no Glúteo (Butt Kicks)',
    'Corrida',
    'Corrida Estacionária',
    'Corrida com Joelhos Altos (Skipping)',
    'Corrida com Passadas Rápidas',
    'Corrida com Passadas Saltadas (Bounding)',
    'Corrida com Passos Curtos',
    'Corrida de Costas',
    'Corrida na Esteira',
    'Deslocamento Lateral (Shuffle)',
    'Drill dos 5 Pontos',
    'Elíptico',
    'Escada de Agilidade',
    'Escada de Agilidade Lateral',
    'Escalador em Pé',
    'Joelhos Altos Apoiado na Parede',
    'Passada de Boxeador',
    'Passo Lateral Rápido',
    'Passo de Esqui',
    'Simulador de Caminhada (Air Walker)',
    'Simulador de Escada',
    'Skipping Alto em Deslocamento',
    'Skipping com Impulsão (Power Skip)',
    'Sprint com Resistência de Faixa Elástica');
UPDATE exercises SET familia = 'CRUCIFIXO' WHERE name IN (  -- 17
    'Crossover Unilateral no Cabo',
    'Crossover na Polia Alta',
    'Crossover na Polia Baixa',
    'Crossover na Polia Média',
    'Crossover para Peitoral Superior',
    'Crucifixo Declinado Unilateral no Cabo',
    'Crucifixo Declinado com Halteres',
    'Crucifixo Declinado no Cabo',
    'Crucifixo Inclinado com Halteres',
    'Crucifixo Inclinado no Cabo',
    'Crucifixo Reto no Cabo',
    'Crucifixo Unilateral no Solo com Landmine',
    'Crucifixo no TRX',
    'Voador Inclinado na Máquina',
    'Voador na Máquina (Alavanca)',
    'Voador na Máquina (Pec Deck com Apoio de Antebraço)',
    'Voador no Pec Deck');
UPDATE exercises SET familia = 'CRUCIFIXO_INVERSO' WHERE name IN (  -- 19
    'Abdução Horizontal Unilateral com Elástico',
    'Abertura com Faixa Elástica (Pull-Apart)',
    'Crucifixo Inverso Apoiado no Banco Inclinado',
    'Crucifixo Inverso Curvado com Faixa Elástica',
    'Crucifixo Inverso Curvado com Halteres',
    'Crucifixo Inverso Curvado no Cabo',
    'Crucifixo Inverso Deitado no Banco',
    'Crucifixo Inverso Deitado nos Cabos',
    'Crucifixo Inverso Unilateral Deitado com Halter',
    'Crucifixo Inverso Unilateral no Cabo',
    'Crucifixo Inverso com Gymstick',
    'Crucifixo Inverso em Pé no Cabo',
    'Crucifixo Inverso na Máquina (Voador Invertido)',
    'Elevação Lateral Deitado de Lado no Banco (Deltoide Posterior)',
    'Face Pull (Puxada para o Rosto)',
    'Face Pull Semiajoelhado no Cabo',
    'Face Pull com Cabos Cruzados',
    'Remada Unilateral Aberta com Halter (Deltoide Posterior)',
    'Remada para Deltoide Posterior Sentado com Halteres');
UPDATE exercises SET familia = 'DESENVOLVIMENTO' WHERE name IN (  -- 45
    'Desenvolvimento Ajoelhado no Cabo',
    'Desenvolvimento Ajoelhado no Landmine',
    'Desenvolvimento Alternado com Rotação com Halteres',
    'Desenvolvimento Alternado em Pé com Halteres',
    'Desenvolvimento Arnold',
    'Desenvolvimento Arnold Unilateral',
    'Desenvolvimento Arnold Unilateral com Kettlebell',
    'Desenvolvimento Cubano Sentado com Halteres',
    'Desenvolvimento Cubano com Halteres',
    'Desenvolvimento Deitado de Bruços com Halteres',
    'Desenvolvimento Militar Pegada Fechada',
    'Desenvolvimento Militar com Barra',
    'Desenvolvimento Militar em Pé no Smith',
    'Desenvolvimento Sentado com Barra',
    'Desenvolvimento Sentado com Faixa Elástica',
    'Desenvolvimento Sentado com Halteres',
    'Desenvolvimento Unilateral Ajoelhado com Kettlebell',
    'Desenvolvimento Unilateral com Faixa Elástica',
    'Desenvolvimento Unilateral com Gymstick',
    'Desenvolvimento Unilateral com Halter',
    'Desenvolvimento Unilateral com Kettlebell',
    'Desenvolvimento W com Halteres',
    'Desenvolvimento Z com Halteres',
    'Desenvolvimento atrás da Nuca Sentado com Barra',
    'Desenvolvimento atrás da Nuca com Gymstick',
    'Desenvolvimento atrás da Nuca no Smith',
    'Desenvolvimento com Barra W Pegada Supinada',
    'Desenvolvimento com Kettlebells',
    'Desenvolvimento com Peso Corporal (sem Carga)',
    'Desenvolvimento em Pé com Halteres Pegada Neutra',
    'Desenvolvimento em Pé no Cabo',
    'Desenvolvimento em Pé no Landmine',
    'Desenvolvimento na Máquina Articulada',
    'Desenvolvimento na Máquina Inclinada (Invertido)',
    'Desenvolvimento na Máquina Pegada Neutra',
    'Desenvolvimento no Smith',
    'Elevação em 3 Direções com Halteres',
    'Flexão Pike',
    'Flexão Pike Profunda entre Cadeiras',
    'Flexão Pike com Mãos no Banco',
    'Flexão em Parada de Mão',
    'Flexão em Parada de Mão com Déficit entre Bancos',
    'Flexão em Parada de Mão com Kipping',
    'Flexão em Parada de Mão na Parede',
    'Meio Desenvolvimento Arnold Sentado');
UPDATE exercises SET familia = 'DESLOCAMENTO' WHERE name IN (  -- 4
    'Andar de Pato',
    'Caminhada em Parada de Mão',
    'Caminhada na Parede',
    'Engatinhar do Urso (Bear Crawl)');
UPDATE exercises SET familia = 'ELEVACAO_FRONTAL' WHERE name IN (  -- 18
    'Círculos com Anilha',
    'Elevação Frontal Alternada Sentado com Halteres',
    'Elevação Frontal Alternada com Halteres',
    'Elevação Frontal Apoiado no Banco Inclinado',
    'Elevação Frontal Bilateral com Halteres',
    'Elevação Frontal Bilateral no Cabo',
    'Elevação Frontal Sentado com Halteres',
    'Elevação Frontal Unilateral no Cabo',
    'Elevação Frontal com Anilha',
    'Elevação Frontal com Barra W e Rotação',
    'Elevação Frontal com Barra W no Banco Inclinado',
    'Elevação Frontal com Dois Cabos no Crossover',
    'Elevação Frontal com Faixa Elástica',
    'Elevação Frontal com Halteres',
    'Elevação Frontal com Halteres até acima da Cabeça',
    'Elevação Frontal no Landmine',
    'Flexão de Ombro com Faixa Elástica',
    'Tesoura de Braços');
UPDATE exercises SET familia = 'ELEVACAO_LATERAL' WHERE name IN (  -- 20
    'Cruz de Ferro com Halteres',
    'Elevação Lateral Alternada com Halteres',
    'Elevação Lateral Apoiado no Banco Inclinado',
    'Elevação Lateral Cruzada no Crossover',
    'Elevação Lateral Deitado de Lado no Banco Inclinado',
    'Elevação Lateral Isométrica com Toalha na Parede',
    'Elevação Lateral Sentado com Halteres',
    'Elevação Lateral Unilateral Inclinado com Halter',
    'Elevação Lateral Unilateral Inclinado no Cabo',
    'Elevação Lateral Unilateral com Halter',
    'Elevação Lateral Unilateral no Cabo',
    'Elevação Lateral com Cotovelos Flexionados',
    'Elevação Lateral com Halteres',
    'Elevação Lateral com Kettlebells',
    'Elevação Lateral e Frontal com Halteres',
    'Elevação Lateral na Máquina Sentado',
    'Elevação Lateral na Máquina de Frente para o Aparelho',
    'Elevação Lateral no Landmine',
    'Elevação Lateral sem Carga',
    'Elevação em 4 Direções com Halteres');
UPDATE exercises SET familia = 'ELEVACAO_PELVICA' WHERE name IN (  -- 19
    'Elevação Pélvica Unilateral com Barra',
    'Elevação Pélvica Unilateral no Banco',
    'Elevação Pélvica com Barra',
    'Elevação Pélvica com Barra e Pés no Banco',
    'Elevação Pélvica com Faixa Elástica',
    'Elevação Pélvica com Peso Corporal',
    'Elevação Pélvica com Pés no Banco',
    'Elevação Pélvica na Cadeira Extensora',
    'Elevação Pélvica na Máquina',
    'Elevação Pélvica no Smith',
    'Ponte de Glúteos',
    'Ponte de Glúteos Sapo (Frog Pump)',
    'Ponte de Glúteos Unilateral com Perna Elevada',
    'Ponte de Glúteos Unilateral com Perna Estendida',
    'Ponte de Glúteos Unilateral com Pé no Banco',
    'Ponte de Glúteos com Abdução de Quadril',
    'Ponte de Glúteos com Barra',
    'Ponte de Glúteos com Halter',
    'Ponte de Glúteos com Miniband');
UPDATE exercises SET familia = 'ENCOLHIMENTO' WHERE name IN (  -- 12
    'Encolhimento Apoiado no Banco Inclinado com Halteres',
    'Encolhimento Sentado de Gittleson com Halteres',
    'Encolhimento Sentado no Banco Inclinado',
    'Encolhimento acima da Cabeça',
    'Encolhimento com Barra',
    'Encolhimento com Barra atrás do Corpo',
    'Encolhimento com Barra atrás do Corpo no Rack',
    'Encolhimento com Halteres',
    'Encolhimento na Máquina',
    'Encolhimento na Máquina (Alavanca)',
    'Encolhimento no Cabo',
    'Encolhimento no Smith');
UPDATE exercises SET familia = 'ERGOMETRO' WHERE name IN (  -- 2
    'Ergômetro de Braço',
    'Remo Ergômetro');
UPDATE exercises SET familia = 'ESCAPULAR' WHERE name IN (  -- 7
    'Depressão Escapular no Banco',
    'Elevação em T Apoiado no Banco Inclinado',
    'Elevação em Y Apoiado no Banco Inclinado',
    'Elevação em Y no Cabo',
    'Flexão Escapular',
    'Mergulho Escapular na Paralela',
    'Puxada Escapular na Barra Fixa');
UPDATE exercises SET familia = 'ESTATICO' WHERE name IN (  -- 4
    'Back Lever',
    'Bandeira Humana',
    'Planche',
    'Suspensão Passiva na Barra');
UPDATE exercises SET familia = 'EXTENSAO_LOMBAR' WHERE name IN (  -- 9
    'Extensão Lombar Sentado na Máquina',
    'Hiperextensão Lombar',
    'Hiperextensão Lombar no Banco Reto',
    'Hiperextensão Reversa Sapo no Banco',
    'Hiperextensão Reversa com Faixa Elástica',
    'Hiperextensão com Rotação',
    'Hiperextensão no Solo',
    'Nadador (Swimming)',
    'Super-Homem (Superman)');
UPDATE exercises SET familia = 'EXTENSORA' WHERE name IN (  -- 7
    'Agachamento Sissy',
    'Agachamento Sissy Ajoelhado',
    'Cadeira Extensora',
    'Cadeira Extensora Unilateral',
    'Cadeira Extensora com Elástico Tubular',
    'Cadeira Extensora com Miniband',
    'Extensão de Quadril em Pé com Faixa Elástica');
UPDATE exercises SET familia = 'FLEXAO' WHERE name IN (  -- 39
    'Flexão',
    'Flexão Declinada',
    'Flexão Declinada na Bola Suíça',
    'Flexão Diamante',
    'Flexão Diamante com Joelhos Apoiados',
    'Flexão Dive Bomber',
    'Flexão Fechada com Joelhos Apoiados',
    'Flexão Fechada na Bola Medicinal',
    'Flexão Hindu',
    'Flexão Hindu Modificada',
    'Flexão Inclinada',
    'Flexão Plus',
    'Flexão Profunda com Kettlebells',
    'Flexão Unilateral na Bola Medicinal',
    'Flexão com Apoios (Push-Up Bars)',
    'Flexão com Cobra',
    'Flexão com Elevação de Braço',
    'Flexão com Joelhos Apoiados',
    'Flexão com Mãos Cruzadas',
    'Flexão com Mãos Invertidas',
    'Flexão com Mãos na Bola Suíça',
    'Flexão com Palma',
    'Flexão com Peso',
    'Flexão com Punhos Fechados',
    'Flexão com Queda entre Bancos',
    'Flexão com Rotação (Flexão em T)',
    'Flexão com Toque no Ombro',
    'Flexão com Toque no Peito',
    'Flexão com Toque nos Pés',
    'Flexão com Um Braço',
    'Flexão com Um Braço Assistida no Banco',
    'Flexão com Um Braço na Bola Medicinal',
    'Flexão com Uma Perna Elevada',
    'Flexão em Planche',
    'Flexão na Parede',
    'Flexão na Parede Pegada Fechada',
    'Flexão na Ponta dos Dedos',
    'Flexão no Bosu',
    'Flexão no TRX');
UPDATE exercises SET familia = 'FLEXAO_QUADRIL' WHERE name IN (  -- 3
    'Elevação de Perna Estendida em Pé com Faixa Elástica',
    'Flexão de Quadril em Pé com Faixa Elástica',
    'Flexão de Quadril em Pé na Máquina (Alavanca)');
UPDATE exercises SET familia = 'FLEXORA' WHERE name IN (  -- 12
    'Cadeira Flexora',
    'Flexora em Pé Unilateral',
    'Flexão Nórdica',
    'Flexão de Perna em Pé com Faixa Elástica',
    'Flexão de Pernas Declinado com Halter',
    'Flexão de Pernas Deitado com Faixa Elástica',
    'Flexão de Pernas Deitado com Halter',
    'Flexão de Pernas Deslizante com Toalha',
    'Flexão de Pernas na Bola Suíça',
    'Mesa Flexora',
    'Mesa Flexora Ajoelhado (Alavanca)',
    'Mesa Flexora Unilateral');
UPDATE exercises SET familia = 'GOLPE' WHERE name IN (  -- 7
    'Boxe Sombra',
    'Chute em Gancho no Saco',
    'Cruzado de Direita (Boxe)',
    'Direto de Direita no Saco',
    'Gancho de Direita (Boxe)',
    'Jab (Boxe)',
    'Socos Alternados (Boxe)');
UPDATE exercises SET familia = 'INCLINACAO_LATERAL' WHERE name IN (  -- 3
    'Inclinação Lateral de Tronco',
    'Moinho de Vento com Halter',
    'Moinho de Vento com Kettlebell');
UPDATE exercises SET familia = 'LEG_PRESS' WHERE name IN (  -- 5
    'Leg Press 45°',
    'Leg Press 90° no Smith',
    'Leg Press Alternado Deitado com Gymstick',
    'Leg Press Horizontal',
    'Leg Press Unilateral');
UPDATE exercises SET familia = 'LEVANTAMENTO_OLIMPICO' WHERE name IN (  -- 15
    'Arranco Unilateral com Halter',
    'Arranco Unilateral com Kettlebell',
    'Arranco com Barra',
    'Arranco com Kettlebell em Afundo',
    'Arranco de Potência',
    'Arremesso com Barra (Jerk)',
    'Clean and Jerk com Kettlebell',
    'Clean and Press com Barra',
    'Clean e Desenvolvimento com Kettlebell',
    'Hang Clean com Barra',
    'Hang Clean com Kettlebell',
    'Heaving Snatch Balance',
    'Muscle Snatch com Barra',
    'Power Clean com Barra',
    'Power Clean com Halteres');
UPDATE exercises SET familia = 'LEVANTAMENTO_TURCO' WHERE name IN (  -- 1
    'Levantamento Turco (Turkish Get-Up)');
UPDATE exercises SET familia = 'LIBERACAO_MIOFASCIAL' WHERE name IN (  -- 15
    'Alongamento de Costas no Rolo de Espuma',
    'Alongamento de Peitoral no Rolo de Espuma',
    'Deslizamento na Parede com Rolo (Serrátil)',
    'Liberação Miofascial com Rolo: Costas',
    'Liberação Miofascial com Rolo: Glúteos',
    'Liberação Miofascial com Rolo: Isquiotibiais',
    'Liberação Miofascial com Rolo: Ombro Posterior',
    'Liberação Miofascial com Rolo: Panturrilhas',
    'Liberação Miofascial com Rolo: Peitoral e Ombro Anterior',
    'Liberação Miofascial com Rolo: Planta do Pé',
    'Liberação Miofascial com Rolo: Quadríceps',
    'Liberação Miofascial com Rolo: Romboides',
    'Liberação Miofascial de Adutores com Rolo',
    'Liberação Miofascial do Dorsal com Rolo',
    'Liberação Miofascial do Trato Iliotibial');
UPDATE exercises SET familia = 'MERGULHO' WHERE name IN (  -- 5
    'Mergulho Assistido para Tríceps',
    'Mergulho Sentado na Máquina',
    'Mergulho de Tríceps na Máquina (Alavanca)',
    'Mergulho entre Bancos',
    'Mergulho no Banco');
UPDATE exercises SET familia = 'MOBILIDADE_ESCAPULAR' WHERE name IN (  -- 7
    'Círculos com Um Braço',
    'Círculos com os Braços',
    'Deslizamento de Braços na Parede (Wall Slide)',
    'Exercício da Bailarina Sentado',
    'Flexão de Ombro Alternada na Parede',
    'Protração e Retração Escapular',
    'Retração Escapular Sentado');
UPDATE exercises SET familia = 'PANTURRILHA_EM_PE' WHERE name IN (  -- 17
    'Elevação de Panturrilha Unilateral',
    'Elevação de Panturrilha Unilateral no Hack',
    'Elevação de Panturrilha Unilateral no Leg Press',
    'Elevação de Panturrilha com Faixa Elástica',
    'Elevação de Panturrilha em Pé com Barra',
    'Elevação de Panturrilha em Pé com Halteres',
    'Elevação de Panturrilha em Pé no Degrau',
    'Elevação de Panturrilha no Hack',
    'Elevação de Panturrilha no Leg Press',
    'Elevação de Panturrilha no Leg Press Horizontal',
    'Elevação de Panturrilha no Smith',
    'Elevação de Panturrilha no Step com Halteres',
    'Panturrilha Burrinho Apoiado no Banco',
    'Panturrilha Burrinho Unilateral Apoiado no Banco',
    'Panturrilha Burrinho com Parceiro',
    'Panturrilha Burrinho na Máquina',
    'Panturrilha em Pé na Máquina');
UPDATE exercises SET familia = 'PANTURRILHA_SENTADO' WHERE name IN (  -- 3
    'Elevação de Panturrilha Sentado com Anilha',
    'Elevação de Panturrilha Sentado com Barra',
    'Panturrilha Sentado na Máquina (Alavanca)');
UPDATE exercises SET familia = 'PARALELAS' WHERE name IN (  -- 8
    'Mergulho Assistido na Máquina',
    'Mergulho Coreano',
    'Mergulho Impossível (Impossible Dips)',
    'Mergulho de Tríceps nas Paralelas',
    'Mergulho de Tríceps no Solo',
    'Mergulho entre Cadeiras',
    'Mergulho nas Argolas',
    'Mergulho nas Paralelas com Ênfase no Peitoral');
UPDATE exercises SET familia = 'PEDALADA' WHERE name IN (  -- 5
    'Air Bike',
    'Bicicleta Ergométrica (Spinning)',
    'Bicicleta Ergométrica Reclinada',
    'Ciclismo ao Ar Livre',
    'Pedalada em Pé na Bicicleta Ergométrica');
UPDATE exercises SET familia = 'PEGADA' WHERE name IN (  -- 4
    'Hand Grip (Flexor de Mão)',
    'Rolo de Punho (Rolinho de Antebraço)',
    'Rosca de Dedos com Barra atrás das Costas',
    'Rosca de Dedos com Halteres');
UPDATE exercises SET familia = 'PRANCHA' WHERE name IN (  -- 1
    'Prancha');
UPDATE exercises SET familia = 'PULLOVER' WHERE name IN (  -- 13
    'Extensão de Ombro com Faixa Elástica',
    'Pulldown Inclinado com Corda',
    'Pulldown Unilateral com Braço Estendido no Cabo',
    'Pulldown com Braços Estendidos na Corda',
    'Pullover Deitado no Cabo',
    'Pullover Sentado no Cabo',
    'Pullover com Barra',
    'Pullover com Barra W Pegada Invertida',
    'Pullover com Barra no Banco Declinado',
    'Pullover com Halter',
    'Pullover com Halter na Bola Suíça',
    'Pullover com Halteres e Pernas Elevadas',
    'Pullover na Máquina (Alavanca)');
UPDATE exercises SET familia = 'PULL_THROUGH' WHERE name IN (  -- 3
    'Extensão de Quadril Ajoelhado com Faixa Elástica',
    'Pull-Through Ajoelhado no Cabo',
    'Pull-Through com Faixa Elástica');
UPDATE exercises SET familia = 'PUXADA_VERTICAL' WHERE name IN (  -- 36
    'Adução de Ombro com Faixa Elástica',
    'Barra Fixa',
    'Barra Fixa Assistida com Faixa Elástica',
    'Barra Fixa Assistida na Máquina',
    'Barra Fixa Isométrica',
    'Barra Fixa Pegada Aberta',
    'Barra Fixa Pegada Fechada',
    'Barra Fixa Pegada Neutra',
    'Barra Fixa Pegada Supinada (Chin-Up)',
    'Barra Fixa Supinada Assistida',
    'Barra Fixa atrás da Nuca',
    'Barra Fixa com Braços Alternados',
    'Barra Fixa com Halter entre os Pés',
    'Barra Fixa com L-Sit',
    'Barra Fixa com Peso',
    'Barra Fixa com Rotação',
    'Barra Fixa com Salto',
    'Barra Fixa com Ênfase no Braquial',
    'Barra Fixa de Cabeça para Baixo',
    'Esquiador com Gymstick',
    'Muscle-Up',
    'Puxada Ajoelhado com Faixa Elástica',
    'Puxada Alta',
    'Puxada Alta Neutra Ajoelhado com Dois Cabos',
    'Puxada Alta Pegada Fechada',
    'Puxada Alta Pegada Supinada',
    'Puxada Alta com Triângulo',
    'Puxada Alta na Máquina (Alavanca)',
    'Puxada Cruzada no Crossover',
    'Puxada Frontal na Máquina (Alavanca)',
    'Puxada Unilateral Semiajoelhado no Cabo',
    'Puxada Unilateral Sentado no Cabo',
    'Puxada atrás da Nuca na Máquina',
    'Puxada atrás da Nuca na Polia',
    'Subida na Corda sem Pernas',
    'Swing 360 na Barra');
UPDATE exercises SET familia = 'REMADA_ALTA' WHERE name IN (  -- 6
    'Remada Alta Unilateral com Halter',
    'Remada Alta com Barra',
    'Remada Alta com Barra W',
    'Remada Alta com Halter Único',
    'Remada Alta com Halteres',
    'Remada Alta no Cabo');
UPDATE exercises SET familia = 'REMADA_HORIZONTAL' WHERE name IN (  -- 35
    'Flexão Reversa Apoiada nos Cotovelos',
    'Remada Apoiada no Banco Inclinado Pegada Neutra com Halteres',
    'Remada Apoiada no Banco Inclinado Pegada Supinada com Halteres',
    'Remada Apoiada no Banco no Cabo',
    'Remada Cavalinho Apoiada na Máquina',
    'Remada Cavalinho na Máquina (Alavanca)',
    'Remada Cavalinho no Landmine',
    'Remada Curvada Pegada Supinada com Barra',
    'Remada Curvada Pegada Supinada com Halteres',
    'Remada Curvada com Barra',
    'Remada Curvada com Barra Pegada Mista',
    'Remada Curvada com Halteres',
    'Remada Curvada com Kettlebells',
    'Remada Curvada no Cabo',
    'Remada Curvada no Smith',
    'Remada Deitado no Banco com Halteres',
    'Remada Espingarda no Cabo',
    'Remada Invertida na Mesa',
    'Remada Invertida nas Argolas',
    'Remada Invertida no Smith',
    'Remada Renegada com Halteres',
    'Remada Sentada Unilateral com Rotação no Cabo',
    'Remada Sentada com Faixa Elástica',
    'Remada Sentada na Máquina',
    'Remada Sentada na Máquina com Anilhas',
    'Remada Sentada na Polia com Pegador Reto',
    'Remada Sentada no Cabo',
    'Remada Sentada no Cabo Pegada Fechada',
    'Remada Serrote com Halter',
    'Remada Unilateral com Gymstick',
    'Remada Unilateral no Landmine',
    'Remada com Barra Apoiada no Banco Inclinado',
    'Remada com Barra atrás do Corpo',
    'Remada com Peso Corporal na Porta',
    'Remada em Front Lever');
UPDATE exercises SET familia = 'ROSCA_CONCENTRADA' WHERE name IN (  -- 5
    'Rosca Concentrada com Barra Sentado',
    'Rosca Concentrada com Halter',
    'Rosca Concentrada com Resistência da Perna',
    'Rosca Concentrada no Cabo',
    'Rosca Spider Unilateral Deitado com Halter');
UPDATE exercises SET familia = 'ROSCA_DIRETA' WHERE name IN (  -- 26
    'Rosca Alternada Sentado com Halteres',
    'Rosca Alternada com Halteres',
    'Rosca Bíceps Agachado no Cabo',
    'Rosca Bíceps Alta com Halteres',
    'Rosca Bíceps Bilateral na Polia Alta',
    'Rosca Bíceps Deitado no Cabo',
    'Rosca Bíceps Pegada Fechada com Barra W',
    'Rosca Bíceps Sentado com Halteres',
    'Rosca Bíceps Unilateral com Faixa Elástica',
    'Rosca Bíceps Unilateral com Halter',
    'Rosca Bíceps Unilateral na Polia Alta',
    'Rosca Bíceps Unilateral no Cabo',
    'Rosca Bíceps com Faixa Elástica',
    'Rosca Bíceps com Halteres',
    'Rosca Bíceps com Halteres e Arm Blaster',
    'Rosca Bíceps na Máquina',
    'Rosca Bíceps na Máquina (Alavanca)',
    'Rosca Bíceps na Polia Alta Sentado',
    'Rosca Direta com Barra',
    'Rosca Direta com Barra Deitado em Banco Alto',
    'Rosca Direta com Barra Pegada Fechada',
    'Rosca Direta com Barra W',
    'Rosca Direta com Barra e Arm Blaster',
    'Rosca Direta no Cabo com Barra',
    'Rosca Unilateral com Barra',
    'Rosca Zottman');
UPDATE exercises SET familia = 'ROSCA_INCLINADA' WHERE name IN (  -- 3
    'Rosca Alternada no Banco Inclinado com Halteres',
    'Rosca Bíceps Bilateral no Cabo em Banco Inclinado',
    'Rosca Direta com Barra Deitado no Banco Inclinado');
UPDATE exercises SET familia = 'ROSCA_INVERSA' WHERE name IN (  -- 4
    'Rosca Bíceps Unilateral Pegada Invertida no Cabo',
    'Rosca Inversa com Barra',
    'Rosca Inversa com Barra W',
    'Rosca Inversa com Halteres');
UPDATE exercises SET familia = 'ROSCA_MARTELO' WHERE name IN (  -- 6
    'Rosca Martelo Sentado com Halteres',
    'Rosca Martelo com Faixa Elástica',
    'Rosca Martelo com Garrafa de Água',
    'Rosca Martelo com Halteres',
    'Rosca Martelo com Halteres e Arm Blaster',
    'Rosca Martelo no Cabo com Corda');
UPDATE exercises SET familia = 'ROSCA_PUNHO' WHERE name IN (  -- 9
    'Rosca de Punho Neutra Sentado com Halter',
    'Rosca de Punho Neutra com Anilhas',
    'Rosca de Punho Reversa Sentado com Barra',
    'Rosca de Punho Reversa com Anilha',
    'Rosca de Punho Reversa com Barra sobre o Banco',
    'Rosca de Punho Unilateral no Cabo Ajoelhado',
    'Rosca de Punho com Barra atrás das Costas',
    'Rosca de Punho com Barra sobre o Banco',
    'Rosca de Punho com Halteres');
UPDATE exercises SET familia = 'ROSCA_SCOTT' WHERE name IN (  -- 9
    'Rosca Scott Alternada com Halteres',
    'Rosca Scott Martelo Unilateral com Halter',
    'Rosca Scott Martelo com Halteres',
    'Rosca Scott Unilateral em Pé com Halter',
    'Rosca Scott Unilateral em Pé com Halter (Banco Inclinado)',
    'Rosca Scott Unilateral no Cabo',
    'Rosca Scott com Barra W',
    'Rosca Scott com Halteres',
    'Rosca Scott na Máquina (Alavanca)');
UPDATE exercises SET familia = 'ROTACAO' WHERE name IN (  -- 5
    'Oito com Kettlebell (Figure 8)',
    'Perdigueiro (Bird Dog)',
    'Puxada Unilateral em Pé com Rotação no Cabo',
    'Rotação Cotovelo ao Joelho em Pé',
    'Rotação Oblíqua Sentado');
UPDATE exercises SET familia = 'ROTACAO_OMBRO' WHERE name IN (  -- 11
    'Rotação Externa de Ombro Ajoelhado no Cabo',
    'Rotação Externa de Ombro Deitado de Lado com Halter',
    'Rotação Externa de Ombro a 90° no Cabo',
    'Rotação Externa de Ombro com Faixa Elástica',
    'Rotação Externa de Ombro com Halter Apoiado no Banco',
    'Rotação Externa de Ombro no Cabo',
    'Rotação Externa de Ombro sem Carga',
    'Rotação Interna de Ombro Sentado no Cabo',
    'Rotação Interna de Ombro a 90° no Cabo',
    'Rotação Interna de Ombro no Cabo',
    'Rotação Interna de Ombro sem Carga');
UPDATE exercises SET familia = 'ROTACAO_QUADRIL' WHERE name IN (  -- 3
    'Rotação Externa de Quadril Deitado com Faixa Elástica',
    'Rotação Externa de Quadril Sentado com Faixa Elástica',
    'Rotação Interna de Quadril Sentado com Faixa Elástica');
UPDATE exercises SET familia = 'SALTO' WHERE name IN (  -- 23
    'Afundo Alternado com Salto',
    'Agachamento com Salto',
    'Agachamento com Salto e Barra',
    'Agachamento com Salto na Barra Hexagonal',
    'Exercício Pliométrico em X',
    'Patinador (Skater Jump)',
    'Polichinelo',
    'Polichinelo com Abertura Horizontal (Seal Jack)',
    'Polichinelo com Agachamento Sumô',
    'Pular Corda',
    'Salto Grupado (Tuck Jump)',
    'Salto Unilateral para a Frente',
    'Salto com Abertura em Agachamento Sumô',
    'Salto com Elevação de Joelho',
    'Salto do Ajoelhado para Agachamento com Barra',
    'Salto em Distância',
    'Salto na Caixa',
    'Salto na Caixa 2 para 1',
    'Salto na Caixa com Aterrissagem em Pistola',
    'Salto para Trás',
    'Saltos Pliométricos em Zigue-Zague',
    'Saltos em Tesoura',
    'Toque no Balão (Balloon Drill)');
UPDATE exercises SET familia = 'STIFF_TERRA' WHERE name IN (  -- 21
    'Bom Dia com Barra',
    'Bom Dia com Faixa Elástica',
    'Bom Dia no Smith',
    'Levantamento Terra',
    'Levantamento Terra Parcial no Rack (Rack Pull)',
    'Levantamento Terra Sumô com Barra',
    'Levantamento Terra Sumô com Halteres',
    'Levantamento Terra Unilateral com Peso Corporal',
    'Levantamento Terra Zercher',
    'Levantamento Terra com Barra Hexagonal',
    'Levantamento Terra com Halter entre as Pernas',
    'Levantamento Terra com Halteres',
    'Levantamento Terra com Kettlebell',
    'Levantamento Terra na Máquina (Alavanca)',
    'Levantamento Terra no Landmine',
    'Stiff Unilateral com Barra',
    'Stiff Unilateral com Faixa Elástica',
    'Stiff Unilateral com Halteres',
    'Stiff com Barra',
    'Stiff com Halteres',
    'Virada de Pneu');
UPDATE exercises SET familia = 'SUPINO_DECLINADO' WHERE name IN (  -- 8
    'Supino Declinado Pegada Aberta com Barra',
    'Supino Declinado Pegada Neutra com Halteres',
    'Supino Declinado Unilateral Pegada Neutra com Halter',
    'Supino Declinado com Barra',
    'Supino Declinado com Halteres',
    'Supino Declinado na Máquina (Alavanca)',
    'Supino Declinado na Máquina Articulada',
    'Supino Declinado no Smith');
UPDATE exercises SET familia = 'SUPINO_INCLINADO' WHERE name IN (  -- 11
    'Supino Inclinado 30° Pegada Supinada com Halteres',
    'Supino Inclinado Pegada Fechada com Barra',
    'Supino Inclinado Pegada Fechada com Halteres',
    'Supino Inclinado Pegada Neutra com Halteres',
    'Supino Inclinado com Barra',
    'Supino Inclinado com Halteres',
    'Supino Inclinado na Máquina (Alavanca)',
    'Supino Inclinado na Máquina Articulada',
    'Supino Inclinado na Máquina Pegada Neutra',
    'Supino Inclinado no Cabo',
    'Supino Inclinado no Smith');
UPDATE exercises SET familia = 'SUPINO_RETO' WHERE name IN (  -- 28
    'Supino Alternado com Halteres',
    'Supino Fechado',
    'Supino Fechado Pegada Supinada',
    'Supino Fechado com Halter Único',
    'Supino Fechado no Smith',
    'Supino Pegada Supinada com Halteres',
    'Supino Reto Pegada Aberta com Barra',
    'Supino Reto Pegada Fechada com Barra',
    'Supino Reto Pegada Neutra com Halteres',
    'Supino Reto com Barra',
    'Supino Reto com Halteres',
    'Supino Reto na Máquina Articulada',
    'Supino Reto no Smith',
    'Supino Sentado Pegada Fechada no Cabo',
    'Supino Sentado na Máquina',
    'Supino Sentado na Máquina (Alavanca)',
    'Supino Sentado na Máquina Convergente',
    'Supino Sentado no Cabo',
    'Supino Squeeze com Halteres',
    'Supino Unilateral Pegada Supinada com Halter',
    'Supino Unilateral com Kettlebell',
    'Supino Unilateral em Pé no Cabo',
    'Supino Unilateral na Máquina (Alavanca)',
    'Supino em Pé com Faixa Elástica',
    'Supino em Pé no Crossover',
    'Supino no Chão com Barra (Floor Press)',
    'Supino no Chão com Kettlebells',
    'Svend Press com Anilha');
UPDATE exercises SET familia = 'SWING' WHERE name IN (  -- 3
    'Swing Unilateral com Kettlebell',
    'Swing com Gymstick',
    'Swing com Kettlebell');
UPDATE exercises SET familia = 'THRUSTER' WHERE name IN (  -- 4
    'Agachamento com Desenvolvimento com Kettlebells',
    'Agachamento com Desenvolvimento no Landmine',
    'Thruster com Barra',
    'Wall Ball');
UPDATE exercises SET familia = 'TRICEPS_COICE' WHERE name IN (  -- 2
    'Tríceps Coice com Halter',
    'Tríceps Coice no Cabo');
UPDATE exercises SET familia = 'TRICEPS_FRANCES' WHERE name IN (  -- 14
    'Tríceps Francês Ajoelhado no Cabo',
    'Tríceps Francês Sentado com Barra W',
    'Tríceps Francês Sentado com Halter (Duas Mãos)',
    'Tríceps Francês Unilateral Sentado com Halter',
    'Tríceps Francês Unilateral na Polia Alta',
    'Tríceps Francês Unilateral no Cabo',
    'Tríceps Francês com Faixa Elástica',
    'Tríceps Francês com Gymstick',
    'Tríceps Francês em Pé com Barra',
    'Tríceps Francês em Pé com Gymstick',
    'Tríceps Francês em Pé com Halter',
    'Tríceps Francês em Pé na Polia Alta',
    'Tríceps Francês no Banco Inclinado com Halter',
    'Tríceps Francês no Banco Inclinado no Cabo');
UPDATE exercises SET familia = 'TRICEPS_PULLEY' WHERE name IN (  -- 16
    'Extensão de Tríceps Ajoelhado no Cabo Apoiado no Banco',
    'Extensão de Tríceps Horizontal com Faixa Elástica',
    'Extensão de Tríceps Horizontal no Cabo',
    'Extensão de Tríceps Lateral no Cabo',
    'Extensão de Tríceps Unilateral Ajoelhado no Cabo',
    'Extensão de Tríceps com Cabos Cruzados',
    'Extensão de Tríceps na Máquina',
    'Extensão de Tríceps na Máquina Pegada Neutra',
    'Tríceps Pulley Pegada Supinada',
    'Tríceps Pulley Unilateral',
    'Tríceps Pulley Unilateral Pegada Supinada',
    'Tríceps Pulley com Barra',
    'Tríceps Pulley com Barra V',
    'Tríceps Pulley com Corda',
    'Tríceps Pulley com Faixa Elástica',
    'Tríceps Pulley com Faixa Elástica na Estrutura');
UPDATE exercises SET familia = 'TRICEPS_TESTA' WHERE name IN (  -- 15
    'Extensão de Tríceps com Peso Corporal na Barra',
    'Extensão de Tríceps com Peso Corporal no Solo',
    'Tríceps Testa Alternado no Banco Inclinado com Halteres',
    'Tríceps Testa Declinado Pegada Fechada com Barra',
    'Tríceps Testa Declinado com Halteres',
    'Tríceps Testa Deitado no Cabo com Barra',
    'Tríceps Testa Deitado no Cabo com Corda',
    'Tríceps Testa Inclinado com Barra W',
    'Tríceps Testa Pegada Neutra com Halteres',
    'Tríceps Testa Unilateral Pegada Pronada com Halter',
    'Tríceps Testa com Barra',
    'Tríceps Testa com Barra Pegada Supinada',
    'Tríceps Testa com Barra W Pegada Fechada atrás da Cabeça',
    'Tríceps Testa com Barra atrás da Cabeça',
    'Tríceps Testa com Faixa Elástica');

-- acessorios: o que o exercicio exige alem do equipamento. O resto fica no default false.
UPDATE exercises SET requires_bench = true WHERE name IN (  -- 114
    'Crucifixo Declinado Unilateral no Cabo',
    'Crucifixo Declinado com Halteres',
    'Crucifixo Declinado no Cabo',
    'Crucifixo Inclinado com Halteres',
    'Crucifixo Inclinado no Cabo',
    'Crucifixo Inverso Apoiado no Banco Inclinado',
    'Crucifixo Inverso Deitado no Banco',
    'Crucifixo Inverso Deitado nos Cabos',
    'Crucifixo Inverso Unilateral Deitado com Halter',
    'Crucifixo Reto no Cabo',
    'Depressão Escapular no Banco',
    'Desenvolvimento Arnold',
    'Desenvolvimento Cubano Sentado com Halteres',
    'Desenvolvimento Deitado de Bruços com Halteres',
    'Desenvolvimento Sentado com Barra',
    'Desenvolvimento Sentado com Halteres',
    'Desenvolvimento atrás da Nuca Sentado com Barra',
    'Desenvolvimento no Smith',
    'Elevação Frontal Apoiado no Banco Inclinado',
    'Elevação Frontal com Barra W no Banco Inclinado',
    'Elevação Lateral Apoiado no Banco Inclinado',
    'Elevação Lateral Deitado de Lado no Banco (Deltoide Posterior)',
    'Elevação Lateral Deitado de Lado no Banco Inclinado',
    'Elevação Pélvica Unilateral com Barra',
    'Elevação Pélvica com Barra',
    'Elevação Pélvica com Barra e Pés no Banco',
    'Elevação Pélvica no Smith',
    'Elevação de Panturrilha Sentado com Anilha',
    'Elevação de Panturrilha Sentado com Barra',
    'Elevação em T Apoiado no Banco Inclinado',
    'Elevação em Y Apoiado no Banco Inclinado',
    'Encolhimento Apoiado no Banco Inclinado com Halteres',
    'Encolhimento Sentado no Banco Inclinado',
    'Extensão de Tríceps Ajoelhado no Cabo Apoiado no Banco',
    'Flexão de Pernas Declinado com Halter',
    'Flexão de Pernas Deitado com Halter',
    'Hiperextensão Lombar no Banco Reto',
    'Hiperextensão Reversa Sapo no Banco',
    'Hiperextensão Reversa com Faixa Elástica',
    'Meio Desenvolvimento Arnold Sentado',
    'Pullover Deitado no Cabo',
    'Pullover com Barra',
    'Pullover com Barra W Pegada Invertida',
    'Pullover com Barra no Banco Declinado',
    'Pullover com Halter',
    'Pullover com Halteres e Pernas Elevadas',
    'Remada Apoiada no Banco Inclinado Pegada Neutra com Halteres',
    'Remada Apoiada no Banco Inclinado Pegada Supinada com Halteres',
    'Remada Apoiada no Banco no Cabo',
    'Remada Deitado no Banco com Halteres',
    'Remada Serrote com Halter',
    'Remada Unilateral Aberta com Halter (Deltoide Posterior)',
    'Remada com Barra Apoiada no Banco Inclinado',
    'Rosca Alternada no Banco Inclinado com Halteres',
    'Rosca Bíceps Bilateral no Cabo em Banco Inclinado',
    'Rosca Direta com Barra Deitado em Banco Alto',
    'Rosca Direta com Barra Deitado no Banco Inclinado',
    'Rosca Scott Alternada com Halteres',
    'Rosca Scott Martelo Unilateral com Halter',
    'Rosca Scott Martelo com Halteres',
    'Rosca Scott Unilateral em Pé com Halter',
    'Rosca Scott Unilateral em Pé com Halter (Banco Inclinado)',
    'Rosca Scott Unilateral no Cabo',
    'Rosca Scott com Barra W',
    'Rosca Scott com Halteres',
    'Rosca Spider Unilateral Deitado com Halter',
    'Rosca de Punho Reversa com Barra sobre o Banco',
    'Rosca de Punho com Barra sobre o Banco',
    'Rotação Externa de Ombro com Halter Apoiado no Banco',
    'Supino Alternado com Halteres',
    'Supino Declinado Pegada Aberta com Barra',
    'Supino Declinado Pegada Neutra com Halteres',
    'Supino Declinado Unilateral Pegada Neutra com Halter',
    'Supino Declinado com Barra',
    'Supino Declinado com Halteres',
    'Supino Declinado no Smith',
    'Supino Fechado',
    'Supino Fechado Pegada Supinada',
    'Supino Fechado com Halter Único',
    'Supino Fechado no Smith',
    'Supino Inclinado 30° Pegada Supinada com Halteres',
    'Supino Inclinado Pegada Fechada com Barra',
    'Supino Inclinado Pegada Fechada com Halteres',
    'Supino Inclinado Pegada Neutra com Halteres',
    'Supino Inclinado com Barra',
    'Supino Inclinado com Halteres',
    'Supino Inclinado no Cabo',
    'Supino Inclinado no Smith',
    'Supino Pegada Supinada com Halteres',
    'Supino Reto Pegada Aberta com Barra',
    'Supino Reto Pegada Fechada com Barra',
    'Supino Reto Pegada Neutra com Halteres',
    'Supino Reto com Barra',
    'Supino Reto com Halteres',
    'Supino Reto no Smith',
    'Supino Squeeze com Halteres',
    'Supino Unilateral Pegada Supinada com Halter',
    'Supino Unilateral com Kettlebell',
    'Tríceps Francês Sentado com Barra W',
    'Tríceps Francês no Banco Inclinado com Halter',
    'Tríceps Francês no Banco Inclinado no Cabo',
    'Tríceps Testa Alternado no Banco Inclinado com Halteres',
    'Tríceps Testa Declinado Pegada Fechada com Barra',
    'Tríceps Testa Declinado com Halteres',
    'Tríceps Testa Deitado no Cabo com Barra',
    'Tríceps Testa Deitado no Cabo com Corda',
    'Tríceps Testa Inclinado com Barra W',
    'Tríceps Testa Pegada Neutra com Halteres',
    'Tríceps Testa Unilateral Pegada Pronada com Halter',
    'Tríceps Testa com Barra',
    'Tríceps Testa com Barra Pegada Supinada',
    'Tríceps Testa com Barra W Pegada Fechada atrás da Cabeça',
    'Tríceps Testa com Barra atrás da Cabeça',
    'Tríceps Testa com Faixa Elástica');

UPDATE exercises SET requires_elevated_surface = true WHERE name IN (  -- 57
    'Agachamento Búlgaro com Barra',
    'Agachamento Búlgaro com Halteres',
    'Agachamento Búlgaro com Peso Corporal',
    'Agachamento Búlgaro com Salto',
    'Agachamento no Banco com Peso Corporal',
    'Alongamento Reverso Assistido (Peitoral e Ombro)',
    'Alongamento de Dorsal e Peitoral Apoiado no Banco',
    'Alongamento do Piriforme Sentado no Banco',
    'Cadeira Extensora com Elástico Tubular',
    'Cadeira Extensora com Miniband',
    'Desenvolvimento Sentado com Faixa Elástica',
    'Elevação Frontal Alternada Sentado com Halteres',
    'Elevação Frontal Sentado com Halteres',
    'Elevação Lateral Sentado com Halteres',
    'Elevação Pélvica Unilateral no Banco',
    'Elevação Pélvica com Faixa Elástica',
    'Elevação Pélvica com Peso Corporal',
    'Elevação Pélvica com Pés no Banco',
    'Encolhimento Sentado de Gittleson com Halteres',
    'Extensão de Quadril Alternada Deitado no Banco',
    'Extensão de Quadril Apoiado no Banco',
    'Flexão Inclinada',
    'Flexão Nórdica',
    'Flexão Pike Profunda entre Cadeiras',
    'Flexão Pike com Mãos no Banco',
    'Flexão Reversa Apoiada nos Cotovelos',
    'Flexão com Queda entre Bancos',
    'Flexão com Um Braço Assistida no Banco',
    'Flexão em Parada de Mão com Déficit entre Bancos',
    'Mergulho entre Bancos',
    'Mergulho entre Cadeiras',
    'Mergulho no Banco',
    'Panturrilha Burrinho Apoiado no Banco',
    'Panturrilha Burrinho Unilateral Apoiado no Banco',
    'Panturrilha Burrinho com Parceiro',
    'Ponte de Glúteos Unilateral com Pé no Banco',
    'Pêndulo de Ombro (Codman)',
    'Remada Invertida na Mesa',
    'Remada Invertida no Smith',
    'Remada para Deltoide Posterior Sentado com Halteres',
    'Rosca Alternada Sentado com Halteres',
    'Rosca Bíceps Sentado com Halteres',
    'Rosca Bíceps na Polia Alta Sentado',
    'Rosca Concentrada com Barra Sentado',
    'Rosca Concentrada com Halter',
    'Rosca Concentrada com Resistência da Perna',
    'Rosca Concentrada no Cabo',
    'Rosca Martelo Sentado com Halteres',
    'Rosca de Dedos com Halteres',
    'Rosca de Punho Neutra Sentado com Halter',
    'Rosca de Punho Reversa Sentado com Barra',
    'Rosca de Punho com Halteres',
    'Rotação Externa de Quadril Sentado com Faixa Elástica',
    'Rotação Externa dos Pés com Faixa Elástica',
    'Rotação Interna de Quadril Sentado com Faixa Elástica',
    'Tríceps Francês Sentado com Halter (Duas Mãos)',
    'Tríceps Francês Unilateral Sentado com Halter');

UPDATE exercises SET requires_pullup_bar = true WHERE name IN (  -- 23
    'Back Lever',
    'Barra Fixa',
    'Barra Fixa Assistida com Faixa Elástica',
    'Barra Fixa Isométrica',
    'Barra Fixa Pegada Aberta',
    'Barra Fixa Pegada Fechada',
    'Barra Fixa Pegada Neutra',
    'Barra Fixa Pegada Supinada (Chin-Up)',
    'Barra Fixa Supinada Assistida',
    'Barra Fixa atrás da Nuca',
    'Barra Fixa com Braços Alternados',
    'Barra Fixa com Halter entre os Pés',
    'Barra Fixa com L-Sit',
    'Barra Fixa com Peso',
    'Barra Fixa com Rotação',
    'Barra Fixa com Salto',
    'Barra Fixa com Ênfase no Braquial',
    'Barra Fixa de Cabeça para Baixo',
    'Muscle-Up',
    'Puxada Escapular na Barra Fixa',
    'Remada em Front Lever',
    'Suspensão Passiva na Barra',
    'Swing 360 na Barra');

UPDATE exercises SET requires_dip_station = true WHERE name IN (  -- 5
    'Mergulho Coreano',
    'Mergulho Escapular na Paralela',
    'Mergulho Impossível (Impossible Dips)',
    'Mergulho de Tríceps nas Paralelas',
    'Mergulho nas Paralelas com Ênfase no Peitoral');

UPDATE exercises SET requires_step = true WHERE name IN (  -- 26
    'Agachamento Frontal com Barra no Banco (Box Squat)',
    'Agachamento Pistola em Cima da Caixa',
    'Agachamento Pistola na Caixa',
    'Agachamento no Banco com Halteres',
    'Alongamento de Panturrilha Unilateral no Degrau',
    'Alongamento de Panturrilha no Degrau',
    'Alongamento do Tendão de Aquiles em Pé',
    'Descida Unilateral do Step',
    'Elevação Pélvica com Barra e Pés no Banco',
    'Elevação de Panturrilha Sentado com Anilha',
    'Elevação de Panturrilha em Pé no Degrau',
    'Elevação de Panturrilha no Step com Halteres',
    'Flexão Declinada',
    'Panturrilha Burrinho Apoiado no Banco',
    'Panturrilha Burrinho Unilateral Apoiado no Banco',
    'Salto na Caixa',
    'Salto na Caixa 2 para 1',
    'Salto na Caixa com Aterrissagem em Pistola',
    'Subida no Banco (Step-Up)',
    'Subida no Banco com Barra',
    'Subida no Banco com Elevação de Joelho',
    'Subida no Banco com Elevação de Joelho e Rosca Bíceps',
    'Subida no Banco com Halteres',
    'Subida no Banco com Toque Cotovelo-Joelho',
    'Subida no Step com Faixa Elástica',
    'Subida no Step com Halteres');

UPDATE exercises SET requires_anchor_point = true WHERE name IN (  -- 17
    'Adução de Ombro com Faixa Elástica',
    'Extensão de Ombro com Faixa Elástica',
    'Extensão de Quadril Ajoelhado com Faixa Elástica',
    'Extensão de Tríceps Horizontal com Faixa Elástica',
    'Flexão de Ombro com Faixa Elástica',
    'Flexão de Pernas Deitado com Faixa Elástica',
    'Pull-Through com Faixa Elástica',
    'Puxada Ajoelhado com Faixa Elástica',
    'Rosca Bíceps Unilateral com Faixa Elástica',
    'Rotação Externa de Quadril Deitado com Faixa Elástica',
    'Rotação Externa de Quadril Sentado com Faixa Elástica',
    'Rotação Interna de Quadril Sentado com Faixa Elástica',
    'Stiff Unilateral com Faixa Elástica',
    'Supino em Pé com Faixa Elástica',
    'Tríceps Pulley com Faixa Elástica',
    'Tríceps Pulley com Faixa Elástica na Estrutura',
    'Tríceps Testa com Faixa Elástica');

-- ============================================================================
-- 6. is_base REALINHADO COM relevancia
-- ============================================================================
-- relevancia substitui is_base: tres niveis no lugar de dois. O DROP da coluna vai junto
-- com a reescrita do SlotFiller (fatia 6) -- ela e lida em ExercisesTable.kt, Exercise.kt,
-- ExerciseRepositoryImpl.kt, SlotFiller.kt e tres testes, e derrubar aqui quebra o build.
-- Ate la os dois convivem, e esta linha garante que nunca discordem.
UPDATE exercises SET is_base = (relevancia = 1) WHERE volume_target IS NOT NULL;
UPDATE exercises SET is_base = false WHERE volume_target IS NULL AND is_base;

-- ============================================================================
-- 7. O CHECK QUE AMARRA A LISTA PRINCIPAL AOS ATRIBUTOS DE SELECAO
-- ============================================================================
-- Depois dos dados, nao antes. Estar na lista principal e ter os quatro atributos passam a
-- ser a mesma coisa, garantida pelo schema: exercicio na lista sem relevancia nao existe.
ALTER TABLE exercises ADD CONSTRAINT chk_exercises_selecao
    CHECK ((volume_target IS NULL) = (relevancia IS NULL)
       AND (relevancia IS NULL) = (prioridade IS NULL)
       AND (relevancia IS NULL) = (nivel_maximo IS NULL)
       AND (relevancia IS NULL) = (ambiente IS NULL));

-- ============================================================================
-- 8. AUTOCONFERENCIA
-- ============================================================================
-- Nome que nao casa e no-op silencioso: o UPDATE some e o exercicio chega torto ao motor.
-- Estes numeros derrubam a migration em vez de deixar passar.
DO $$
DECLARE
    esperado CONSTANT jsonb := '{"catalogo": 893, "principal": 147, "familia": 893, "rel1": 43, "rel2": 79, "rel3": 25, "academia": 70, "casa": 38, "ambos": 39}';
    achado   jsonb;
BEGIN
    SELECT jsonb_build_object(
        'catalogo',  (SELECT count(*) FROM exercises),
        'principal', (SELECT count(*) FROM exercises WHERE volume_target IS NOT NULL),
        'familia',   (SELECT count(*) FROM exercises WHERE familia IS NOT NULL),
        'rel1',      (SELECT count(*) FROM exercises WHERE relevancia = 1),
        'rel2',      (SELECT count(*) FROM exercises WHERE relevancia = 2),
        'rel3',      (SELECT count(*) FROM exercises WHERE relevancia = 3),
        'academia',  (SELECT count(*) FROM exercises WHERE ambiente = 'ACADEMIA'),
        'casa',      (SELECT count(*) FROM exercises WHERE ambiente = 'CASA'),
        'ambos',     (SELECT count(*) FROM exercises WHERE ambiente = 'AMBOS')
    ) INTO achado;
    IF achado IS DISTINCT FROM esperado THEN
        RAISE EXCEPTION 'V65: contagem diverge. esperado %, achado %', esperado, achado;
    END IF;
END $$;
