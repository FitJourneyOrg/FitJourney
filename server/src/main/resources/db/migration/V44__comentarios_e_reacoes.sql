-- Comentários e reações em check-in (fatia E.1, ARCH #33 decisões 8.1 a 8.4).
--
-- ============================================================================
-- DUAS TABELAS, PORQUE SÃO DUAS COISAS DIFERENTES
-- ============================================================================
--
-- Parecem irmãs — as duas penduram algo num check-in — e têm formatos opostos:
--
--   comentário  ->  MUITOS por pessoa, texto livre, ordem cronológica importa
--   reação      ->  UMA por pessoa (8.2), vocabulário fechado, o que importa é a CONTAGEM
--
-- Uma tabela só, com `tipo` e um texto anulável, teria de carregar o índice único da reação sem
-- aplicá-lo ao comentário — e regra que vale para metade das linhas é regra que o banco não
-- consegue garantir.

CREATE TABLE check_in_comments (
    id          UUID         NOT NULL PRIMARY KEY,

    -- CASCADE nos dois: apagar o check-in leva os comentários, e apagar o grupo leva tudo (2.9).
    -- Sem o `group_id` aqui, listar "comentários do grupo" para moderação (fatia E.2) exigiria
    -- passar por `check_ins` a cada consulta.
    check_in_id UUID         NOT NULL REFERENCES check_ins(id) ON DELETE CASCADE,
    group_id    UUID         NOT NULL REFERENCES groups(id)    ON DELETE CASCADE,

    -- Quem escreveu. CASCADE: conta apagada leva os comentários dela junto.
    user_id     UUID         NOT NULL REFERENCES users(id)     ON DELETE CASCADE,

    -- 8.1: máximo 500, SEM EDIÇÃO.
    --
    -- O CHECK cobre o que o Kotlin não alcança — escrita por script ou psql. E o limite inferior
    -- existe porque comentário vazio é ruído no feed de 49 pessoas: se não há o que dizer, não há
    -- o que publicar.
    body        VARCHAR(500) NOT NULL,

    created_at  TIMESTAMP    NOT NULL,

    -- Não existe `updated_at`, e a ausência é a regra: **comentário não se edita** (8.1). Uma
    -- coluna de edição convidaria alguém a implementá-la, e aí o que 49 pessoas leram deixaria de
    -- ser o que está escrito.

    CONSTRAINT check_in_comments_body_len CHECK (char_length(btrim(body)) BETWEEN 1 AND 500)
);

-- A leitura é sempre "os comentários DESTE check-in, do mais antigo para o mais novo" — conversa
-- se lê na ordem em que aconteceu, ao contrário do feed.
CREATE INDEX check_in_comments_checkin_idx ON check_in_comments (check_in_id, created_at);

-- Para a fila de moderação da E.2: "comentários deste grupo". Sem ele, a tela do admin varreria a
-- tabela inteira.
CREATE INDEX check_in_comments_group_idx ON check_in_comments (group_id, created_at DESC);


-- ============================================================================
-- REAÇÕES — a PK É a regra
-- ============================================================================
--
-- [INV] "Uma reação por pessoa por check-in" (8.2). A chave primária composta
-- `(check_in_id, user_id)` faz disso um fato do BANCO, não uma checagem que alguém pode esquecer.
--
-- Trocar de reação é `INSERT ... ON CONFLICT DO UPDATE`, e tirar é `DELETE`. Nenhum dos dois
-- precisa consultar antes — é a mesma escolha do par canônico das amizades (V40): quando a regra
-- cabe numa chave, ela para de depender de código correto.

CREATE TABLE check_in_reactions (
    check_in_id UUID        NOT NULL REFERENCES check_ins(id) ON DELETE CASCADE,
    group_id    UUID        NOT NULL REFERENCES groups(id)    ON DELETE CASCADE,
    user_id     UUID        NOT NULL REFERENCES users(id)     ON DELETE CASCADE,

    -- O emoji da reação. VARCHAR(16) e não CHAR(2) pela mesma razão da V43: emoji não é um
    -- caractere. `👍` são 2 unidades UTF-16 e `❤️` leva seletor de variação.
    --
    -- Sem CHECK de vocabulário no banco, de propósito. O conjunto de 6 é decisão de PRODUTO e vai
    -- mudar; um CHECK obrigaria uma migration a cada ajuste de UI, e o custo de uma linha com
    -- emoji fora da lista é uma reação estranha no feed, não uma corrupção. Mesma técnica do
    -- `achievement_id` (#32) e do `type` das notificações (V42): vocabulário aberto no banco,
    -- fechado no Kotlin.
    emoji       VARCHAR(16) NOT NULL,

    created_at  TIMESTAMP   NOT NULL,

    PRIMARY KEY (check_in_id, user_id)
);

-- "As reações deste check-in", que é como o feed as agrupa para contar. A PK já cobre buscas por
-- `check_in_id`, mas o índice explícito ajuda a contagem por emoji sem tocar na tabela.
CREATE INDEX check_in_reactions_checkin_idx ON check_in_reactions (check_in_id, emoji);
