-- V70 -- recusa de promocao de nivel (motor v3, Fatia 11).
--
-- ============================================================================
-- O QUE E (desenho aprovado em 2026-10-29: docs/trabalho/motor-v3-fatia-11.md)
-- ============================================================================
-- D-1  PK (user_id, nivel_proposto), upsert a cada recusa -- guarda so a MAIS RECENTE por par.
--      Historico completo de recusas omitido DE PROPOSITO: o unico consumo desenhado aqui e "posso
--      perguntar de novo?", que depende so da ultima. Append-only (padrao progression_event) e
--      migration aditiva se um consumidor de historico aparecer depois.
-- D-2  `mesociclo_na_recusa` copiado de `user_mesocycle.numero` no momento da recusa -- NAO e
--      heuristica de calendario (a primeira proposta, 8 semanas, foi descartada: erra com pausa e
--      recalibracao). [REGRA] pode sugerir de novo quando `mesociclo_atual > mesociclo_na_recusa + 1`
--      (passou do mesociclo SEGUINTE ao da recusa). Sem numero calibravel aqui -- e contagem exata,
--      nao aproximacao.
--
-- A SUGESTAO em si nao e persistida -- e derivada do historico que a V69 ja guarda
-- (exercise_progression, progression_event). Isto so existe para o motor saber que ja perguntou e
-- quando pode perguntar de novo.
--
-- `recusado_em` e TIMESTAMP (sem fuso), nao TIMESTAMPTZ: conferido contra as 51 colunas de timestamp
-- de TODAS as migrations do projeto (grep -rho TIMESTAMPTZ\|TIMESTAMP) -- nenhuma usa TIMESTAMPTZ,
-- V69 incluida. Manter TIMESTAMP e consistencia com o projeto inteiro, nao so com a V69.
--
-- Nenhum dado existente e apagado ou reescrito: uma tabela NOVA.

CREATE TABLE promotion_decline (
    user_id             UUID      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    nivel_proposto      TEXT      NOT NULL CHECK (nivel_proposto IN ('INTERMEDIATE', 'ADVANCED')),
    mesociclo_na_recusa INT       NOT NULL CHECK (mesociclo_na_recusa >= 1),
    recusado_em         TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, nivel_proposto)
);

COMMENT ON TABLE promotion_decline IS
    'Recusa de sugestao de promocao de nivel. So a mais recente por (user_id, nivel_proposto) -- '
    'historico completo omitido de proposito, ver cabecalho da migration.';
