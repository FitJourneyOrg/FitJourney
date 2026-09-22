-- Fatia H, sessao B: limpeza dos 26 exercicios legado (seed antigo, video_ref vazio) que a
-- conferencia reversa da V53 revelou nao ter correspondencia exata (case-sensitive) no
-- catalogo.json atual.
--
-- Descoberta em 3 grupos, decisao tomada com o Rafael via AskUserQuestion:
--
-- 1) DUPLICATA POR CAPITALIZACAO/SUFIXO: 4 exercicios legado (sem video) sao o MESMO exercicio
--    que ja existe no catalogo atual sob outra capitalizacao/sufixo (com video). Nao sao
--    renomeacoes que a V53 deveria ter pego -- sao duas linhas para o mesmo conceito, uma
--    delas relíquia de um seed anterior ao padrao de nomes atual. Consolida: migra as
--    referencias de workout_exercises para a linha moderna, depois apaga o legado.
--    Achados: "Stiff com halteres" == "Stiff com Halteres"; "Levantamento terra romeno com
--    barra" == "Levantamento Terra Romeno"; "Elevação de panturrilha em pé com halteres" ==
--    "Elevação de Panturrilha em Pé com Halteres"; "Flexora em pé unilateral" ==
--    "Flexora em Pé Unilateral". Confirmado por consulta direta que a versao moderna de cada
--    par tem video_ref preenchido.
--
-- 2) SEM EQUIVALENTE MODERNO, EM USO: "Abdominal bicicleta", "Abdominal na maquina" e
--    "Abdominal na polia" nao tem substituto no catalogo atual (nem por nome nem por
--    case-insensitive), mas tem workout_exercises apontando para eles. Rafael confirmou
--    apagar mesmo assim -- ambiente de desenvolvimento, usuarios de teste, sem necessidade
--    de preservar historico.
--
-- 3) SEM USO: os 19 restantes nunca foram referenciados em workout_exercises e nao tem
--    equivalente no catalogo (checado case-insensitive). Delete direto, sem risco de violar FK.
--
-- workout_sets tem ON DELETE CASCADE em workout_exercise_id (V5), entao apagar workout_exercises
-- no grupo 2 ja arrasta os sets associados automaticamente.

-- ============================================================
-- Grupo 1: consolidacao (duplicata por capitalizacao/sufixo)
-- ============================================================
UPDATE workout_exercises SET exercise_id = (SELECT id FROM exercises WHERE name = 'Stiff com Halteres')
    WHERE exercise_id = (SELECT id FROM exercises WHERE name = 'Stiff com halteres');
DELETE FROM exercises WHERE name = 'Stiff com halteres';

UPDATE workout_exercises SET exercise_id = (SELECT id FROM exercises WHERE name = 'Levantamento Terra Romeno')
    WHERE exercise_id = (SELECT id FROM exercises WHERE name = 'Levantamento terra romeno com barra');
DELETE FROM exercises WHERE name = 'Levantamento terra romeno com barra';

UPDATE workout_exercises SET exercise_id = (SELECT id FROM exercises WHERE name = 'Elevação de Panturrilha em Pé com Halteres')
    WHERE exercise_id = (SELECT id FROM exercises WHERE name = 'Elevação de panturrilha em pé com halteres');
DELETE FROM exercises WHERE name = 'Elevação de panturrilha em pé com halteres';

UPDATE workout_exercises SET exercise_id = (SELECT id FROM exercises WHERE name = 'Flexora em Pé Unilateral')
    WHERE exercise_id = (SELECT id FROM exercises WHERE name = 'Flexora em pé unilateral');
DELETE FROM exercises WHERE name = 'Flexora em pé unilateral';

-- ============================================================
-- Grupo 2: sem equivalente, em uso -- apaga com o historico (dev/teste)
-- ============================================================
DELETE FROM workout_exercises WHERE exercise_id IN (
    SELECT id FROM exercises WHERE name IN (
    'Abdominal bicicleta',
    'Abdominal na máquina',
    'Abdominal na polia'
    )
);
DELETE FROM exercises WHERE name IN (
    'Abdominal bicicleta',
    'Abdominal na máquina',
    'Abdominal na polia'
);

-- ============================================================
-- Grupo 3: sem uso -- delete direto
-- ============================================================
DELETE FROM exercises WHERE name IN (
    'Abdominal',
    'Abdominal infra',
    'Abdominal na Polia (Cable Crunch)',
    'Dead Bug',
    'Deslizamento de calcanhar',
    'Elevação de Pernas Suspenso na Barra',
    'Elevação de panturrilha no degrau com haltere',
    'Elevação de panturrilha sentado com halteres',
    'Elevação de panturrilha unilateral em pé',
    'Elevação de pernas na cadeira romana',
    'Hollow Hold',
    'Pallof Press',
    'Pallof Press com Elástico',
    'Prancha',
    'Prancha Frontal',
    'Prancha com toque no ombro',
    'Prancha lateral',
    'Rotação no cabo (Pallof)',
    'Russian Twist com Anilha'
);
