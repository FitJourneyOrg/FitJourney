-- V72 -- um_rm_kg do evento de progressao (service/rota do avancado, fatia nova pos-11/pos-iniciante).
--
-- ============================================================================
-- O QUE E (decisao do Rafael, 2026-10-30)
-- ============================================================================
-- A V71 resolveu a ANCORA (mesociclo) mas deixou passar o SNAPSHOT: a decisao registrada em
-- debitos.md dizia "1RM no inicio do mesociclo N = um_rm_kg do primeiro evento daquele exercicio
-- com progression_event.mesociclo = N" -- so que `progression_event` nunca guardou `um_rm_kg`,
-- so `mesociclo` (V71). Sem este valor por evento, "o 1RM que era no comeco do mesociclo N" nao
-- existe em lugar nenhum: `exercise_progression` so guarda o ATUAL, sobrescrito a cada evento.
--
-- Esta migration fecha o furo com a MESMA ideia da V71 -- coluna aditiva no log, log vira
-- autodescritivo -- em vez de abrir uma tabela de snapshot separada (duplicaria em outro lugar o
-- que o log deveria ter registrado na hora). Critério do Rafael: "um log de eventos que não
-- registra o valor que ele mudou serve para pouco".
--
-- Mesmo padrao do D-3 da V69 (`session_set_logs.rir`) e da V71 (`mesociclo`): coluna NULLABLE,
-- SEM BACKFILL. O historico anterior a esta migration nao tem o 1RM daquele momento registrado
-- em lugar nenhum recuperavel -- so o valor ATUAL sobrevive em `exercise_progression`, que nao e
-- a mesma coisa. Escrever o valor passa a ser obrigatorio em todo INSERT NOVO a partir de agora
-- (`ProgressaoRepository.registrar`); linha antiga com `um_rm_kg IS NULL` so significa "de antes
-- desta migration, sem snapshot recuperavel".
--
-- PERIODO CEGO conhecido e aceito: o diagnostico do avancado precisa comparar o 1RM de DOIS
-- mesociclos consecutivos (`PromocaoDeNivel.diagnosticoAvancado`). Sem backfill, isso significa
-- ~2 mesociclos sem dado suficiente para diagnosticar NINGUEM, a partir do deploy desta migration
-- -- nao e bug, e o mesmo trade-off ja aceito nas V69/V71, so que agora o periodo cego e mais
-- visivel porque bloqueia o service inteiro, nao so uma face dele. O service precisa DISTINGUIR
-- esse "dado insuficiente" de "ha dado, e o diagnostico nao e promover" -- ver docs/historico da
-- fatia desta migration para o motivo.
--
-- Nenhum dado existente e apagado ou reescrito: uma coluna NOVA, nullable.

ALTER TABLE progression_event
    ADD COLUMN um_rm_kg DOUBLE PRECISION;

COMMENT ON COLUMN progression_event.um_rm_kg IS
    'Snapshot do 1RM estimado (Epley) NO MOMENTO deste evento -- o estado pos-decisao que '
    'Progressao.decidir()/calibrar() ja calcula e so nao era persistido por evento. NULL = evento '
    'anterior a esta migration, sem snapshot recuperavel (so o ATUAL sobrevive em '
    'exercise_progression, que nao e o mesmo dado). Fonte do diagnostico de promocao do avancado '
    '(PromocaoDeNivel.diagnosticoAvancado): "1RM no inicio do mesociclo N" = um_rm_kg do PRIMEIRO '
    'evento do exercicio com mesociclo = N, sem tabela de snapshot separada.';
