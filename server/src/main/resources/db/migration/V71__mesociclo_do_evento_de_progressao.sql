-- V71 -- mesociclo do evento de progressao (service/rota da promocao de nivel, fatia nova pos-11).
--
-- ============================================================================
-- O QUE E (desenho aprovado em 2026-10-29, sessao da promocao iniciante->intermediario)
-- ============================================================================
-- Resolve DOIS problemas com UMA coluna aditiva, decisao do Rafael:
--
-- (1) AS ANCORAS DO SERVICE DE PROMOCAO. `PromotionService` precisa saber quais exercicios
--     contam como ancora da pessoa AGORA, pra alimentar `PromocaoDeNivel.diagnosticoDoPerfil`.
--     A primeira ideia foi `users.active_program_id` (V60) -- descartada: o KDoc da V60 e
--     explicito, "nao mexe em sessao/historico, e so destaque de UI resolvido no servidor", e dar
--     um segundo papel (decisao de dominio) a um campo documentado como UI-only e o mesmo padrao
--     que ja custou caro tres vezes nesta fatia. Pior: com `active_program_id` nulo (programa
--     nunca "ativado"), a pessoa treina e nunca recebe diagnostico, SEM NADA REPORTAR -- falha
--     calada. A fonte certa e comportamento, nao ponteiro de UI: ancora = exercicio com evento de
--     progressao no mesociclo ATUAL, lido de `progression_event`. Precisa saber a QUE mesociclo
--     cada evento pertence -- e e exatamente o que faltava na V69.
--
-- (2) O SNAPSHOT DO 1RM NO INICIO DO MESOCICLO, pro diagnostico avancado (`diagnosticoAvancado`,
--     ainda SEM service/rota -- debito aberto, ver `debitos.md`). Antes de abrir tabela nova pra
--     isso, a pergunta certa era "a V69 ja guarda o suficiente?" -- e guarda quase: falta so o
--     numero do mesociclo em `progression_event` pra "1RM no inicio do mesociclo N" ser
--     "o `um_rm_kg` do PRIMEIRO evento daquele exercicio com esse numero", sem tabela nem
--     snapshot novo.
--
-- Mesmo padrao do D-3 da V69 (`session_set_logs.rir`): coluna NULLABLE, SEM BACKFILL. O historico
-- anterior a esta migration nao tem como saber a que mesociclo pertenceu -- inventar um numero
-- seria pior que nao ter. Escrever o numero passa a ser obrigatorio em todo INSERT NOVO a partir
-- de agora (`ProgressaoRepository.registrar`); linha antiga com `mesociclo IS NULL` so significa
-- "de antes desta migration", e fica fora da janela de ambos os consumidores ((1) e (2)) ate ser
-- naturalmente substituida por eventos novos.
--
-- Nenhum dado existente e apagado ou reescrito: uma coluna NOVA, nullable.

ALTER TABLE progression_event
    ADD COLUMN mesociclo INT CHECK (mesociclo IS NULL OR mesociclo >= 1);

COMMENT ON COLUMN progression_event.mesociclo IS
    'Numero do mesociclo da pessoa no momento do evento (user_mesocycle.numero). NULL = evento '
    'anterior a esta migration, sem backfill. Fonte das ancoras do PromotionService (iniciante) e, '
    'quando o avancado tiver service/rota, do snapshot de 1RM por mesociclo -- sem tabela extra.';

CREATE INDEX idx_progression_event_mesociclo ON progression_event (user_id, mesociclo);
