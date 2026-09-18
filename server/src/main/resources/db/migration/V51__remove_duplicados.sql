-- Remove os 39 exercícios duplicados (fatia H, migration de DADO).
--
-- ============================================================================
-- DE ONDE VEM A LISTA
-- ============================================================================
--
-- A revisão de nomenclatura de 2026-09-17 conferiu os 963 exercícios contra 3 quadros de cada
-- vídeo e achou 46 duplicados. Destes, 9 não eram duplicados de verdade e ganharam nome próprio;
-- 40 saíram do catálogo e tiveram a mídia MOVIDA para `gifs_exercicios/_removidos/` — nada foi
-- apagado em disco, e `_removidos/removidos.json` guarda, de cada um, o id, a chave, os arquivos
-- e a entrada inteira que estava no catálogo.
--
-- Aqui são **39**, não 40: o `Pull Up` (chave `pull_up_var_2`) nunca chegou ao banco. Ele existia
-- na pasta e não no seed — divergência antiga entre disco e catálogo, e não algo que esta
-- migration cause ou conserte.
--
--
-- ============================================================================
-- POR QUE `DELETE` E NÃO UMA COLUNA `ativo`
-- ============================================================================
--
-- Soft delete parece mais seguro e cobra um preço que não acaba: **toda consulta de exercício
-- passa a precisar de `WHERE ativo`**, inclusive o `ExercisePreFilter` do motor. Esquecer um
-- desses lugares faz o duplicado reaparecer só ali — o tipo de defeito que ninguém reproduz
-- porque só acontece numa tela.
--
-- > Coluna que toda consulta precisa lembrar de filtrar é uma regra que depende de memória, e
-- > memória não aparece no build.
--
-- O `DELETE` só é aceitável porque a condição foi MEDIDA, não suposta: **nenhum dos 39 está
-- dentro do treino de ninguém**. O bloco logo abaixo reconfere isso no banco real antes de
-- apagar, porque a medição foi feita num export e o banco pode ter andado desde então.
--
-- Reversão: `_removidos/removidos.json` tem a entrada completa de cada um e a mídia continua em
-- disco. Restaurar é um INSERT, não uma arqueologia.


-- ============================================================================
-- TRAVA: ninguém apaga exercício que está dentro de um treino
-- ============================================================================
--
-- Sem isto, um exercício que entrou num programa depois da minha medição sairia junto — e o
-- usuário perderia um item do treino dele, em silêncio, numa migration cujo assunto é "limpar
-- duplicado". A FK impediria o DELETE, mas com uma mensagem sobre restrição, não sobre o que
-- realmente aconteceu.
--
-- > Migration que apaga dado precisa falhar dizendo O QUE ela ia destruir, não qual constraint
-- > reclamou.
DO $$
DECLARE
    presos integer;
BEGIN
    SELECT count(DISTINCT we.exercise_id) INTO presos
      FROM workout_exercises we
     WHERE we.exercise_id IN (
        'e2dc37fa-c8a0-594b-93d4-4496f60b23f1',
        'c524afad-4945-5bcd-8de4-fc22a1ea3cc1',
        '5aa73385-cee1-5107-bf14-f97a9cc32257',
        'c0fe2436-adb9-5f53-8383-aa4262b02401',
        'b3a2e5a5-a6b4-5b87-b002-c7f35d9f0549',
        '6ad75e20-1382-557b-afca-ce5fdc7754ca',
        'f8f2965e-8801-5633-82f8-12db94bd2f8b',
        '02e0bf74-3eb7-52cb-a732-4aa477b9e696',
        'ecb1f9b6-f79c-52b4-aa90-77d4e74dcb9d',
        '4da011a6-af1d-5681-84b6-e8dc15d12752',
        'e4312278-9939-515e-a549-0b21e6f0d33b',
        'a3b2db90-4e4f-5526-b65d-3d5aa3eccfcf',
        '38a781a0-e503-5a33-9938-f8464a042d37',
        '1549c2f5-5e2d-5340-9f85-d314fd615954',
        'd2e5d522-b367-5495-bd5a-0cb3db84d67c',
        '45f07ca7-2c94-5481-9b93-4675644736c2',
        'df942cd6-28b2-54a8-9b54-7a32db854beb',
        'eef53508-115a-5a3a-bde1-4d18616abc10',
        '6d3d0618-1578-5f00-9dde-6541eccbdcd0',
        'e2f2f7b4-b14a-579b-baa8-6b942fb3124b',
        '99f8272a-b7cb-592d-816a-9fd93eab09b6',
        'cfb54ff8-e05b-5c3c-9d1c-f81b96072681',
        '0356e287-d921-500c-a0d0-a299fefdddc4',
        '2f74a3af-db43-5f98-bf5a-6123a3e6559c',
        'd9641557-3833-53fa-bcce-66e4ab1f6998',
        '5d546738-080b-51cc-9af7-2dd1828f5e64',
        '2032318c-501f-51f5-aeca-df54c1aaf252',
        'bf9f7cc7-a8da-5d07-bb15-70675b1cf636',
        '093cb755-48cb-5c0b-89e9-802c3ed73596',
        '9f833ee9-1a11-5b3f-a07d-34c840a0288f',
        '02b6292c-f3e6-5b29-9f20-e39a1a520441',
        '0b41e78d-c8b9-5365-9518-8fe05aef372d',
        '5da14baa-bbf6-5678-851f-f1cd0b68ae09',
        '0daaab0d-1454-5fa1-8155-784349b2c90e',
        '53531c33-a762-5f5d-bf1c-a708319837c4',
        '443066da-6b7b-5bcd-bea9-007afb995c46',
        '91f1fc1a-c10a-5266-9fd8-50b86a1d396a',
        '2e315f33-8cdc-5473-990b-2ac10a65cdd2',
        '17152d29-2592-5862-807a-465ab82db8d9'
     );

    IF presos > 0 THEN
        RAISE EXCEPTION 'V51: % dos duplicados estão dentro de treinos e não podem ser apagados', presos;
    END IF;
END $$;


-- ============================================================================
-- A REMOÇÃO
-- ============================================================================
--
-- Por ID, e nunca por nome: o nome é justamente o que a revisão mudou, e casar por ele faria a
-- migration depender do estado que ela mesma está corrigindo.
DELETE FROM exercises
 WHERE id IN (
    'e2dc37fa-c8a0-594b-93d4-4496f60b23f1',   -- Agachamento Búlgaro Calistenia
    'c524afad-4945-5bcd-8de4-fc22a1ea3cc1',   -- Agachamento com Elevação dos Joelhos
    '5aa73385-cee1-5107-bf14-f97a9cc32257',   -- Agachamento Dividido Profundo
    'c0fe2436-adb9-5f53-8383-aa4262b02401',   -- Agachamento Frontal com Polia
    'b3a2e5a5-a6b4-5b87-b002-c7f35d9f0549',   -- Agachamento Funcional
    '6ad75e20-1382-557b-afca-ce5fdc7754ca',   -- Alongamento de panturrilha com uma perna esticada
    'f8f2965e-8801-5633-82f8-12db94bd2f8b',   -- Alongamento do Gastrocnêmio com Joelho Flexionado
    '02e0bf74-3eb7-52cb-a732-4aa477b9e696',   -- Alongamento dos adutores sentado
    'ecb1f9b6-f79c-52b4-aa90-77d4e74dcb9d',   -- Alongamento dos flexores do quadril em posição de joelho
    '4da011a6-af1d-5681-84b6-e8dc15d12752',   -- Alongamento dos isquiotibiais em pé
    'e4312278-9939-515e-a549-0b21e6f0d33b',   -- Alongamento reverso assistido (peito e ombro)
    'a3b2db90-4e4f-5526-b65d-3d5aa3eccfcf',   -- Avanço com Barra
    '38a781a0-e503-5a33-9938-f8464a042d37',   -- Avanço com Halteres
    '1549c2f5-5e2d-5340-9f85-d314fd615954',   -- Avanço com Halteres para Trás
    'd2e5d522-b367-5495-bd5a-0cb3db84d67c',   -- Balanços com Kettlebell
    '45f07ca7-2c94-5481-9b93-4675644736c2',   -- Barra fixa pegada invertida
    'df942cd6-28b2-54a8-9b54-7a32db854beb',   -- Bom dia
    'eef53508-115a-5a3a-bde1-4d18616abc10',   -- Desenvolvimento de ombros na máquina
    '6d3d0618-1578-5f00-9dde-6541eccbdcd0',   -- Elevação com Halteres
    'e2f2f7b4-b14a-579b-baa8-6b942fb3124b',   -- Elevação lateral tronco inclinado
    '99f8272a-b7cb-592d-816a-9fd93eab09b6',   -- Extensão de tríceps com pegada invertida
    'cfb54ff8-e05b-5c3c-9d1c-f81b96072681',   -- Flexão de Braço com Bola Medicinal em Um Braço
    '0356e287-d921-500c-a0d0-a299fefdddc4',   -- Flexão de Parede
    '2f74a3af-db43-5f98-bf5a-6123a3e6559c',   -- Glúteos na Polia Baixa
    'd9641557-3833-53fa-bcce-66e4ab1f6998',   -- Levantamento Terra Pernas Rígidas com Barra
    '5d546738-080b-51cc-9af7-2dd1828f5e64',   -- Panturrilha em Pé no Smith
    '2032318c-501f-51f5-aeca-df54c1aaf252',   -- Paralelas na Barra
    'bf9f7cc7-a8da-5d07-bb15-70675b1cf636',   -- Puxada com Faixa Elástica
    '093cb755-48cb-5c0b-89e9-802c3ed73596',   -- Quatro Apoios
    '9f833ee9-1a11-5b3f-a07d-34c840a0288f',   -- Remada Inclinada a 45 Graus
    '02b6292c-f3e6-5b29-9f20-e39a1a520441',   -- remada invertida com halteres inclinado
    '0b41e78d-c8b9-5365-9518-8fe05aef372d',   -- Remada Unilateral com Barra
    '5da14baa-bbf6-5678-851f-f1cd0b68ae09',   -- Rosca bíceps inclinada com halteres sentado
    '0daaab0d-1454-5fa1-8155-784349b2c90e',   -- Rosca com barra
    '53531c33-a762-5f5d-bf1c-a708319837c4',   -- Rosca com cabo de um braço
    '443066da-6b7b-5bcd-bea9-007afb995c46',   -- Saltos com Joelhos Altos
    '91f1fc1a-c10a-5266-9fd8-50b86a1d396a',   -- Toques de Dedos em Pé
    '2e315f33-8cdc-5473-990b-2ac10a65cdd2',   -- Voador de Deltoides Posterior com Cabo
    '17152d29-2592-5862-807a-465ab82db8d9'  -- Voador na Máquina para Deltoides Posteriores
);


-- ============================================================================
-- GUARDA
-- ============================================================================
--
-- Contagem exata nos dois lados. 965 - 39 = 926, e os 900 com mídia continuam 900 — a remoção não
-- pode ter levado ninguém do grupo bom junto.
DO $$
DECLARE
    total integer;
    com_midia integer;
BEGIN
    SELECT count(*) INTO total FROM exercises;
    IF total <> 926 THEN
        RAISE EXCEPTION 'V51: esperava 926 exercícios depois da remoção, encontrei %', total;
    END IF;

    SELECT count(*) INTO com_midia
      FROM exercises
     WHERE video_ref ~ '^[^/]+/[a-z0-9_]+\.mp4$';

    IF com_midia <> 900 THEN
        RAISE EXCEPTION 'V51: a remoção levou exercício com mídia junto; sobraram %', com_midia;
    END IF;
END $$;
