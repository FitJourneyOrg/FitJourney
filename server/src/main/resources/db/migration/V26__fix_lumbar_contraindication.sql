-- V26: corrige a contraindicação lombar (segurança / PAR-Q).
-- Várias bases inseridas na V16 gravaram 'LOWER_BACK' em `contraindications` (Stiff com barra,
-- Levantamento terra romeno, Stiff com halteres, Abdominal infra, Pallof, Elevação de pernas na
-- cadeira romana), mas o enum BodyLimitation usa 'LUMBAR'. O mapper (runCatching valueOf) descarta
-- 'LOWER_BACK' em silêncio → esses exercícios NÃO eram excluídos para quem declarou limitação
-- lombar no quiz. O motor podia prescrever um stiff/terra pra um usuário com problema de lombar.
--
-- Correção: normaliza 'LOWER_BACK' -> 'LUMBAR' apenas na coluna `contraindications`.
-- (A category 'LOWER_BACK' é um valor legítimo de ExerciseCategory e fica INTACTA — esta migration
--  não toca na coluna category.)
UPDATE exercises
   SET contraindications = array_replace(contraindications, 'LOWER_BACK', 'LUMBAR')
 WHERE 'LOWER_BACK' = ANY(contraindications);
