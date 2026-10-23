-- V64 -- alvo de volume do motor: um exercicio, um alvo (motor v3, fatia 0, 2026-10-14).
--
-- GERADO por tools/motor_sim/gerar_v64.py a partir de tools/motor_sim/alvo_canonico.csv.
-- Nao editar a mao; mudanca de alvo depois de aplicada e migration nova.
--
-- REGERADA em 2026-10-21, antes de ser aplicada em qualquer banco: a tabela canonica passou de 79
-- para 147 nomes, saida da curadoria do catalogo (tools/curadoria_oficial/, aprovada pelo Rafael
-- grupo a grupo). A versao de 79 esta em tools/motor_sim/historico/alvo_canonico_79.csv.
--
-- ============================================================================
-- O DEFEITO
-- ============================================================================
-- O SlotFiller casava musculo com exercicio por DOIS criterios que se sobrepoem: QUADS e
-- POSTERIOR por movement_pattern, GLUTES/CHEST/BACK/SHOULDERS por etiqueta em primary_muscles
-- (um ARRAY). Medido na lista principal de entao (79): 5 casavam em dois alvos -- Agachamento
-- Livre com Barra e os dois Afundos (QUADS+GLUTES), Levantamento Terra (POSTERIOR+GLUTES),
-- Pullover com Halter (CHEST+BACK). Quem ficava com eles dependia da ordem de preenchimento e do
-- score; o Afundo virava a ancora de gluteo com 10 series/semana no avancado. Nenhuma medicao do
-- motor valia enquanto isso fosse verdade.
--
-- ============================================================================
-- A DECISAO (ADR em docs/arch/, [REGRA] em docs/regras/01-motor-de-geracao.md)
-- ============================================================================
-- Coluna propria, de VALOR UNICO, so do servidor, no vocabulario de TargetMuscle (VolumeModel.kt).
-- "Exatamente um alvo" passa a ser propriedade do schema, nao da ordem dos `if`.
--   * Nao e MuscleGroup: aquele e o vocabulario do USUARIO (quiz de foco, filtro da biblioteca,
--     progresso), esta no contrato e em ~60 arquivos, e e um array -- [LEGS, GLUTES] continuaria
--     casando em dois.
--   * Nao e heuristica por padrao: a proposta "GLUTES por HIP_THRUST/ABDUCTION" usava padroes que
--     nao existem em MovementPattern; a Elevacao Pelvica e HINGE. O gluteo ficaria sem candidato.
--   * primary_muscles continua como esta e serve ao catalogo, a UI e ao bonus de foco. volume_target
--     serve a contabilidade de volume do motor. Sao papeis diferentes, de proposito.
--
-- Casos decididos (o resto e o unico alvo que o criterio antigo ja dava):
--   Agachamento Livre com Barra, Afundo com Halteres, Afundo com Barra -> QUADS
--   Levantamento Terra -> POSTERIOR
--   Pullover com Halter -> CHEST (segue a category curada)
--   Crucifixo Inverso Apoiado no Banco Inclinado -> SHOULDERS (deltoide posterior: a regra do
--     ombro do v3 tira dele o segundo isolamento; o banco ja dizia SHOULDERS, so a planilha dizia BACK)
--   Cadeira Adutora -> POSTERIOR (entrou na revisao de 21/10; nao existe alvo de adutor, e o
--     criterio anatomico mais proximo e o posterior de coxa)
--
-- ============================================================================
-- A REVISAO DE 21/10 (79 -> 147)
-- ============================================================================
-- A lista de 79 deixava perfis sem exercicio: quem treina em casa, quem e iniciante na academia e
-- os grupos pequenos. A revisao foi feita grupo a grupo, com exercicios ja curados no catalogo,
-- consolidados e de equipamento comum -- nenhum exercicio novo foi criado. Por alvo:
--   CHEST 23, BACK 21, SHOULDERS 17, BICEPS 13, TRICEPS 13, FOREARMS 8,
--   QUADS 15, POSTERIOR 11, CALVES 10, GLUTES 16.
-- Os tres ultimos (Agachamento Sissy, Agachamento Sissy Ajoelhado e Extensao de Ombro com Faixa
-- Elastica) entraram depois da simulacao do motor, que mostrou vaga de isolamento sem candidato
-- em casa: quadriceps e costas so tinham isolado de maquina e de cabo.
-- Os atributos que decidem QUAL dos 23 supinos entra na vaga (prioridade, relevancia, nivel
-- maximo, ambiente, familia e os acessorios) vem na V65, com a taxonomia do catalogo inteiro.
--
-- NULL = fora da contabilidade do motor. So a lista principal recebe alvo nesta migration.
-- Por NOME EXATO, conferido contra uma replica do banco (V1..V63 aplicadas em Postgres 16 limpo,
-- 922 exercicios, 147 nomes casando uma linha cada). Nenhum dos 147 esta entre os 29 que a V65
-- remove do catalogo.
-- Tabela inteira validada por profissional com CREF.

ALTER TABLE exercises ADD COLUMN volume_target VARCHAR(16);
ALTER TABLE exercises ADD CONSTRAINT chk_exercises_volume_target CHECK (
    volume_target IS NULL OR volume_target IN ('CHEST', 'BACK', 'SHOULDERS', 'BICEPS', 'TRICEPS', 'FOREARMS', 'QUADS', 'POSTERIOR', 'CALVES', 'GLUTES', 'CORE'));

-- CHEST (23)
UPDATE exercises SET volume_target = 'CHEST' WHERE name IN (
    'Supino Reto com Barra',
    'Supino Inclinado com Barra',
    'Supino Reto com Halteres',
    'Supino Inclinado com Halteres',
    'Supino Sentado na Máquina',
    'Flexão',
    'Supino Inclinado na Máquina Articulada',
    'Crucifixo Inclinado com Halteres',
    'Crossover na Polia Alta',
    'Voador no Pec Deck',
    'Mergulho nas Paralelas com Ênfase no Peitoral',
    'Supino Reto no Smith',
    'Crossover na Polia Média',
    'Voador na Máquina (Pec Deck com Apoio de Antebraço)',
    'Flexão Declinada',
    'Supino em Pé com Faixa Elástica',
    'Flexão com Um Braço',
    'Flexão com Joelhos Apoiados',
    'Supino Declinado com Barra',
    'Supino Declinado com Halteres',
    'Supino Reto Pegada Fechada com Barra',
    'Pullover com Halter',
    'Flexão na Parede');

-- BACK (21)
UPDATE exercises SET volume_target = 'BACK' WHERE name IN (
    'Barra Fixa',
    'Puxada Alta',
    'Remada Curvada com Barra',
    'Remada Serrote com Halter',
    'Remada Sentada no Cabo',
    'Remada Sentada na Máquina',
    'Barra Fixa Pegada Supinada (Chin-Up)',
    'Puxada Alta com Triângulo',
    'Remada Curvada com Halteres',
    'Remada Apoiada no Banco Inclinado Pegada Neutra com Halteres',
    'Remada Cavalinho na Máquina (Alavanca)',
    'Barra Fixa com Peso',
    'Pulldown com Braços Estendidos na Corda',
    'Remada Invertida na Mesa',
    'Barra Fixa Assistida na Máquina',
    'Puxada Ajoelhado com Faixa Elástica',
    'Remada Sentada com Faixa Elástica',
    'Extensão de Ombro com Faixa Elástica',
    'Remada Cavalinho no Landmine',
    'Remada Sentada no Cabo Pegada Fechada',
    'Remada Sentada na Polia com Pegador Reto');

-- SHOULDERS (17)
UPDATE exercises SET volume_target = 'SHOULDERS' WHERE name IN (
    'Desenvolvimento Militar com Barra',
    'Desenvolvimento Sentado com Halteres',
    'Desenvolvimento na Máquina Articulada',
    'Elevação Lateral com Halteres',
    'Crucifixo Inverso Apoiado no Banco Inclinado',
    'Elevação Lateral na Máquina Sentado',
    'Elevação Lateral Unilateral Inclinado no Cabo',
    'Face Pull (Puxada para o Rosto)',
    'Crucifixo Inverso na Máquina (Voador Invertido)',
    'Desenvolvimento Arnold',
    'Flexão em Parada de Mão na Parede',
    'Flexão Pike',
    'Desenvolvimento Sentado com Faixa Elástica',
    'Abertura com Faixa Elástica (Pull-Apart)',
    'Elevação Frontal com Halteres',
    'Remada Alta no Cabo',
    'Remada Alta com Barra');

-- BICEPS (13)
UPDATE exercises SET volume_target = 'BICEPS' WHERE name IN (
    'Rosca Direta com Barra W',
    'Rosca Alternada com Halteres',
    'Rosca Martelo com Halteres',
    'Rosca Scott com Barra W',
    'Rosca Alternada no Banco Inclinado com Halteres',
    'Rosca Direta no Cabo com Barra',
    'Rosca Direta com Barra',
    'Rosca Bíceps na Máquina',
    'Rosca Scott com Halteres',
    'Rosca Bíceps com Faixa Elástica',
    'Rosca Martelo com Faixa Elástica',
    'Rosca Concentrada com Halter',
    'Rosca Spider Unilateral Deitado com Halter');

-- TRICEPS (13)
UPDATE exercises SET volume_target = 'TRICEPS' WHERE name IN (
    'Tríceps Pulley com Barra',
    'Tríceps Testa com Barra',
    'Tríceps Pulley com Corda',
    'Tríceps Francês Sentado com Halter (Duas Mãos)',
    'Tríceps Francês Sentado com Barra W',
    'Tríceps Testa Pegada Neutra com Halteres',
    'Tríceps Francês em Pé na Polia Alta',
    'Mergulho Sentado na Máquina',
    'Extensão de Tríceps na Máquina',
    'Mergulho no Banco',
    'Tríceps Pulley com Faixa Elástica',
    'Tríceps Francês com Faixa Elástica',
    'Tríceps Coice com Halter');

-- FOREARMS (8)
UPDATE exercises SET volume_target = 'FOREARMS' WHERE name IN (
    'Rosca Inversa com Barra W',
    'Rosca de Punho com Barra sobre o Banco',
    'Rosca Inversa com Halteres',
    'Rosca de Punho com Halteres',
    'Rosca de Punho Neutra Sentado com Halter',
    'Rosca de Dedos com Halteres',
    'Rosca Inversa com Barra',
    'Hand Grip (Flexor de Mão)');

-- QUADS (15)
UPDATE exercises SET volume_target = 'QUADS' WHERE name IN (
    'Agachamento Livre com Barra',
    'Leg Press 45°',
    'Agachamento no Hack',
    'Agachamento Goblet com Halter',
    'Agachamento Livre com Peso Corporal',
    'Agachamento Búlgaro com Halteres',
    'Afundo com Halteres',
    'Cadeira Extensora',
    'Subida no Banco (Step-Up)',
    'Afundo Reverso',
    'Agachamento Pistola',
    'Afundo com Barra',
    'Agachamento Sumô com Halter',
    'Agachamento Sissy Ajoelhado',
    'Agachamento Sissy');

-- POSTERIOR (11)
UPDATE exercises SET volume_target = 'POSTERIOR' WHERE name IN (
    'Stiff com Barra',
    'Levantamento Terra',
    'Mesa Flexora',
    'Cadeira Flexora',
    'Stiff com Halteres',
    'Stiff Unilateral com Halteres',
    'Flexora em Pé Unilateral',
    'Flexão Nórdica',
    'Flexão de Pernas Deitado com Faixa Elástica',
    'Levantamento Terra Unilateral com Peso Corporal',
    'Cadeira Adutora');

-- CALVES (10)
UPDATE exercises SET volume_target = 'CALVES' WHERE name IN (
    'Panturrilha em Pé na Máquina',
    'Panturrilha Sentado na Máquina (Alavanca)',
    'Elevação de Panturrilha no Leg Press',
    'Elevação de Panturrilha em Pé com Halteres',
    'Elevação de Panturrilha Unilateral',
    'Elevação de Panturrilha no Step com Halteres',
    'Elevação de Panturrilha com Faixa Elástica',
    'Elevação de Panturrilha em Pé no Degrau',
    'Elevação de Panturrilha no Smith',
    'Panturrilha Burrinho Apoiado no Banco');

-- GLUTES (16)
UPDATE exercises SET volume_target = 'GLUTES' WHERE name IN (
    'Elevação Pélvica com Barra',
    'Elevação Pélvica na Máquina',
    'Ponte de Glúteos',
    'Elevação Pélvica no Smith',
    'Elevação Pélvica com Barra e Pés no Banco',
    'Ponte de Glúteos com Halter',
    'Elevação Pélvica Unilateral no Banco',
    'Cadeira Abdutora',
    'Pull-Through Ajoelhado no Cabo',
    'Abdução de Quadril em Pé na Polia',
    'Coice de Glúteo Unilateral na Polia Baixa',
    'Pull-Through com Faixa Elástica',
    'Elevação Pélvica com Faixa Elástica',
    'Abdução de Quadril Sentado com Faixa Elástica',
    'Ponte de Glúteos com Barra',
    'Coice de Burro (Quatro Apoios)');

-- Fora da contabilidade do motor, de proposito (continuam no catalogo para troca e treino manual):

-- Autoconferencia: nome que nao casar (renomeado, digitado errado, duplicado) derruba a migration
-- aqui, em vez de deixar um exercicio da lista principal invisivel ao motor em silencio.
DO $$
DECLARE
    esperado CONSTANT jsonb := '{"CHEST": 23, "BACK": 21, "SHOULDERS": 17, "BICEPS": 13, "TRICEPS": 13, "FOREARMS": 8, "QUADS": 15, "POSTERIOR": 11, "CALVES": 10, "GLUTES": 16}';
    achado   jsonb;
BEGIN
    SELECT jsonb_object_agg(volume_target, n) INTO achado
      FROM (SELECT volume_target, count(*) AS n FROM exercises
             WHERE volume_target IS NOT NULL GROUP BY volume_target) t;
    IF achado IS DISTINCT FROM esperado THEN
        RAISE EXCEPTION 'V64: contagem por alvo diverge. esperado %, achado %', esperado, achado;
    END IF;
END $$;
-- total com alvo: 147
