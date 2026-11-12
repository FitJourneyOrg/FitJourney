-- V73 -- declaracao de custo do motor (debito (c)/(d) de "o motor sabe, o app nao mostra",
-- 2026-10-31). Duas colunas novas em `programs`, mesmo padrao de `focus_muscles` (V56):
-- snapshot JSON-em-TEXT, gravado na CRIACAO do programa, nunca recomputado na leitura --
-- e' o que faz o aviso sobreviver a releitura do mesmo programa ("ver a cada ciclo"), em
-- vez de existir so na resposta do POST /programs/generate e desaparecer no GET seguinte.
--
-- `avisos`: JSON de List<ProgramAvisoDto> (musculo + causa -- NAO_TREINADO/ABAIXO_DO_PISO/
-- PISO_DIRETO_NEGADO/MANUTENCAO). Ver `ProgramAudit.avisos()`.
--
-- `grupamentos_reduzidos_pelo_foco`: JSON de List<String> (TargetMuscle.name) -- musculos que
-- cairam pro piso por causa do foco ativo. Ver `VolumeTargets.custoDoFoco()`.
--
-- DORMENTE enquanto o motor v3 nao for o WorkoutGenerator de produção: o
-- DeterministicWorkoutGenerator (motor atual, StructureEngine) nunca calcula nada aqui -- as
-- duas colunas nascem com lista vazia ('[]', nao NULL -- `ProgramDto.avisos`/
-- `grupamentosReduzidosPeloFoco` default para emptyList(), nao para null) em todo programa
-- gerado hoje, e so passam a ter conteudo quando/se o v3 for ligado (decisao separada, "Troca
-- de producao do StructureEngine"). NULL fica reservado pra linha de ANTES desta migration
-- (coluna nem existia). Ligar isso e' so plumbing: NAO muda nenhuma serie prescrita, nao
-- reescreve programa nenhum.
--
-- Nullable, sem backfill (programas anteriores a esta migration nao tem o dado, mesma
-- situacao de focus_muscles nas linhas de antes da V56). Nao apaga nem reescreve dado de
-- usuario: duas colunas novas.

ALTER TABLE programs ADD COLUMN avisos TEXT;
ALTER TABLE programs ADD COLUMN grupamentos_reduzidos_pelo_foco TEXT;

COMMENT ON COLUMN programs.avisos IS
    'JSON de List<ProgramAvisoDto> (musculo + causa). NULL so em linha de antes da V73; '
    'motor que ainda nao calcula isto (StructureEngine, produção hoje) grava [] (lista '
    'vazia), nao NULL. Snapshot da geração, nunca recomputado na leitura.';
COMMENT ON COLUMN programs.grupamentos_reduzidos_pelo_foco IS
    'JSON de List<String> (TargetMuscle.name) -- musculos reduzidos ao piso pelo foco ativo. '
    'Mesma observação de NULL vs [] que programs.avisos.';
