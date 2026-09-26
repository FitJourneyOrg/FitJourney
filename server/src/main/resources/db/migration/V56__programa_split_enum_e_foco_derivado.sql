-- Fatia "rationale derivado" (backend -> contrato -> cliente, decisao 2026-09-22).
-- O StructureEngine para de gravar a frase pronta em portugues; o cliente monta a
-- partir de split (agora chave do enum SplitType, nao mais o rotulo renderizado) e
-- focus_muscles (snapshot dos musculos priorizados NO MOMENTO da geracao).
--
-- Decisao do Rafael: programas legados (gerados antes desta fatia) NAO recebem
-- focus_muscles retroativo -- nascem NULL, e o cliente omite so a clausula de foco
-- pra eles. A frase base (split + dias) continua integra, porque essas duas colunas
-- sempre existiram.

ALTER TABLE programs ADD COLUMN focus_muscles TEXT;

-- 'Manual' (shell sem motor) vira split = NULL -- a coluna original e NOT NULL
-- (V11) e precisa soltar a constraint ANTES do UPDATE abaixo, senao o proprio
-- UPDATE que grava NULL quebra.
ALTER TABLE programs ALTER COLUMN split DROP NOT NULL;

-- Reescreve split de ROTULO (label, o que o motor gravava ate a V55) para CHAVE do
-- enum (name). 'Manual' (shell sem motor, ProgramService.createManual) vira NULL.
UPDATE programs SET split = CASE split
    WHEN 'Full Body' THEN 'FULL_BODY'
    WHEN 'Upper/Lower' THEN 'UPPER_LOWER'
    WHEN 'Upper/Lower/Full' THEN 'UPPER_LOWER_FULL'
    WHEN 'Push/Pull/Legs' THEN 'PUSH_PULL_LEGS'
    WHEN 'Upper/Lower + PPL' THEN 'UL_PPL'
    WHEN 'Arnold' THEN 'ARNOLD'
    WHEN 'Manual' THEN NULL
    ELSE split
END;

-- Guarda automatica: a V53/V54 desta mesma fatia H nao tinham nenhuma (achado do PR
-- review). Esta tem -- se sobrar valor fora do mapeamento acima, a migration falha
-- alto em vez de deixar uma linha com split que o enum nao consegue ler.
DO $$
DECLARE
    invalidos INT;
BEGIN
    SELECT count(*) INTO invalidos FROM programs
        WHERE split IS NOT NULL
        AND split NOT IN ('FULL_BODY','UPPER_LOWER','UPPER_LOWER_FULL','PUSH_PULL_LEGS','UL_PPL','ARNOLD');
    IF invalidos > 0 THEN
        RAISE EXCEPTION 'V56: % linha(s) em programs.split fora do mapeamento label->enum conhecido', invalidos;
    END IF;
END $$;

ALTER TABLE programs DROP COLUMN rationale;

