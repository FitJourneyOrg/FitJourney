-- V68 -- CORE sai do motor: um alvo sem catalogo nao e um alvo (motor v3, fatia 8, 2026-10-27).
--
-- ============================================================================
-- O QUE ERA
-- ============================================================================
-- CORE era alvo de volume desde o inicio do motor v3, com alvo semanal ZERO em todo nivel
-- (VolumeTargets.kt, spec_v3.py) e NENHUM exercicio no catalogo apontando para ele
-- (volume_target = 'CORE' em 0 linhas). A V64 abriu o CHECK para o valor junto com os outros
-- dez; a V58 chegou a cadastrar a Prancha, e a curadoria posterior a tirou da contabilidade do
-- motor. O alvo ficou existindo sem nada para preencher.
--
-- Medido nos 720 perfis da matriz do oraculo, antes de remover:
--   * cobertura do pool: CORE era o UNICO alvo com zero candidato em todo ambiente, nivel e
--     combinacao de acessorio -- 15 de 15 combinacoes vazias nos dois papeis (composto e
--     isolamento), sem valvula de escape possivel.
--   * auditoria (etapa [7]): os 720 status SEM_CATALOGO da matriz eram TODOS de CORE -- um por
--     perfil, exatamente.
--   * o alvo zerado nunca abria vaga, entao o buraco de catalogo era dormente: nao quebrava
--     nada hoje, e viraria vaga vazia de verdade no instante em que alguem desse alvo > 0.
--
-- ============================================================================
-- A DECISAO
-- ============================================================================
-- CORE sai do motor (TargetMuscle) E do contrato (MuscleGroup, o vocabulario do usuario: quiz de
-- foco, filtro da biblioteca, progresso). Conferido no banco ANTES de cortar o enum:
--   SELECT count(*) FROM profiles WHERE focus_areas LIKE '%"CORE"%';   -> 0
--   SELECT count(*) FROM programs WHERE focus_muscles LIKE '%CORE%';   -> 0
-- Nenhum perfil escolheu abdomen como foco e nenhum programa guardou CORE no snapshot, por isso
-- a remocao nao precisa de migration de DADO -- nada e reescrito aqui, so a constraint aperta.
--
-- Remover o alvo NAO muda prescricao nenhuma: medido nos 720 perfis depois da remocao, ATENDIDO
-- (3613), MANUTENCAO (2100), ABAIXO_DO_MINIMO (1040), NAO_TREINADO (447) e os 367 perfis
-- reprovados ficaram identicos; so os 720 SEM_CATALOGO desapareceram. O status SEM_CATALOGO
-- continua no codigo -- e a defesa para um alvo que PERCA cobertura -- mas deixa de disparar.
--
-- Fora de escopo, de proposito: ExerciseCategory.CORE (taxonomia de categoria do catalogo) e
-- CORE em primary_muscles/secondary_muscles (tag grossa de estimulo, que a UI mostra). Sao
-- papeis diferentes de volume_target, que e a unica coluna que o motor le para contabilizar
-- volume -- ver o cabecalho da V64.
--
-- ============================================================================
-- O EFEITO
-- ============================================================================
-- Falha ALTO se alguma linha ainda tiver volume_target = 'CORE': e o comportamento desejado --
-- nada e apagado por conta propria, a migration para e mostra o problema.

DO $$
DECLARE
    n integer;
BEGIN
    SELECT count(*) INTO n FROM exercises WHERE volume_target = 'CORE';
    IF n > 0 THEN
        RAISE EXCEPTION 'V68: % exercicio(s) ainda com volume_target = CORE. Reclassifique ou '
                        'zere o alvo desses antes de apertar a constraint -- esta migration nao '
                        'reescreve dado de catalogo por conta propria.', n;
    END IF;
END $$;

ALTER TABLE exercises DROP CONSTRAINT chk_exercises_volume_target;
ALTER TABLE exercises ADD CONSTRAINT chk_exercises_volume_target CHECK (
    volume_target IS NULL OR volume_target IN ('CHEST', 'BACK', 'SHOULDERS', 'BICEPS', 'TRICEPS', 'FOREARMS', 'QUADS', 'POSTERIOR', 'CALVES', 'GLUTES'));
