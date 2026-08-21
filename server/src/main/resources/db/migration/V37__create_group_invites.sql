-- Convites de grupo (ARCH #33, fatia A.2).
--
-- Existem DUAS portas de entrada, de propósito (2.1): o CÓDIGO, que fica no próprio grupo e é
-- ditado/digitado, e o LINK, que é compartilhado e tem prazo. O código não expira porque ele é
-- identidade do grupo; o link expira porque é um convite.

CREATE TABLE group_invites (
    token       UUID       PRIMARY KEY,
    group_id    UUID       NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    created_by  UUID       NOT NULL REFERENCES users(id),
    created_at  TIMESTAMP  NOT NULL DEFAULT now(),

    -- MENOR entre 7 dias e o início do grupo (2.13 + 2-B.0). Sete dias é o prazo do convite;
    -- o início é o teto real, porque depois dele a entrada fecha de qualquer jeito e um link
    -- que "funciona" mas leva a uma recusa é pior que um link vencido, que ao menos explica.
    expires_at  TIMESTAMP  NOT NULL,

    -- Revogação é ANULAÇÃO, não exclusão: a linha fica para responder "por que aquele link
    -- parou de funcionar?" — mesma escolha append-only das decisões do admin (#33).
    revoked_at  TIMESTAMP
);

-- UM convite ativo por grupo. Gerar outro revoga o anterior, então esta consulta ("qual é o
-- link atual deste grupo?") é a única que a tela faz.
CREATE INDEX idx_group_invites_group ON group_invites (group_id, created_at DESC);

-- Sem backfill e sem ALTER: tabela nova.
