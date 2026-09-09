-- Denúncia e moderação (fatia E.2, ARCH #33 seção 6).
--
-- ============================================================================
-- DUAS TABELAS COM POLÍTICAS DE INTEGRIDADE OPOSTAS — e a diferença é o ponto
-- ============================================================================
--
--   group_reports       -> o PEDIDO. Existe enquanto houver algo a julgar. CASCADE em tudo:
--                          alvo apagado, nada a moderar; a linha vai junto.
--   moderation_actions  -> a DECISÃO. É auditoria (6.6, append-only). SEM chave estrangeira no
--                          alvo, de propósito: o registro de que o admin invalidou um check-in
--                          precisa sobreviver ao check-in.
--
-- É a mesma tensão de sempre entre "dado operacional" e "registro histórico", e a maneira errada
-- de resolver é dar CASCADE aos dois: a auditoria evaporaria exatamente nos casos em que ela é
-- mais necessária.


-- ============================================================================
-- DENÚNCIAS
-- ============================================================================
--
-- ## O alvo é um ARCO EXCLUSIVO, não um `target_id` opaco
--
-- 6.4 diz que comentário também se denuncia. A forma tentadora é `target_type TEXT` +
-- `target_id UUID` sem FK — uma coluna que aponta para duas tabelas. Isso custa a integridade
-- referencial inteira: nada impediria uma denúncia apontando para um UUID que nunca existiu, e
-- apagar um comentário deixaria a fila do admin cheia de itens que não abrem.
--
-- Duas colunas anuláveis, uma FK em cada, e um CHECK garantindo que exatamente uma está
-- preenchida. O banco continua sabendo o que a linha aponta, e o CASCADE funciona.
--
-- O custo é real e vale citar: toda consulta tem de olhar duas colunas, e acrescentar um terceiro
-- tipo de alvo mexe no schema. É o preço de o banco poder verificar.

CREATE TABLE group_reports (
    id           UUID      NOT NULL PRIMARY KEY,

    -- Denúncia é sempre DENTRO de um grupo — é o admin dele que julga (6.2). Redundante com o
    -- alvo, e presente pelo mesmo motivo do `group_id` em `check_in_comments` (V44): a fila do
    -- admin é "as denúncias abertas DESTE grupo", e sem esta coluna ela passaria por duas tabelas.
    group_id     UUID      NOT NULL REFERENCES groups(id) ON DELETE CASCADE,

    -- O ARCO: exatamente um dos dois.
    check_in_id  UUID      REFERENCES check_ins(id)          ON DELETE CASCADE,
    comment_id   UUID      REFERENCES check_in_comments(id)  ON DELETE CASCADE,

    -- Quem denunciou. O admin NÃO vê este campo (decisão de 2026-09-07): num grupo de conhecidos,
    -- denúncia identificada é denúncia que ninguém faz. Fica gravado porque sustenta duas coisas
    -- que a tela não sustenta — o "uma por pessoa" abaixo, e a auditoria de quem abriu o pedido.
    reporter_id  UUID      NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    -- Por quê, em texto livre e OBRIGATÓRIO (decisão de 2026-09-07). Sem vocabulário fechado de
    -- motivos: numa v1, uma lista de categorias seria adivinhação, e o admin do grupo conhece as
    -- pessoas — ele lê a frase.
    --
    -- NOT NULL e não anulável-com-regra-no-Kotlin porque a direção importa: afrouxar depois é um
    -- `DROP NOT NULL` de uma linha; apertar depois exigiria decidir o que fazer com as linhas em
    -- branco que já existirem. Aperta-se cedo o que é barato apertar cedo.
    reason       VARCHAR(300) NOT NULL,

    created_at   TIMESTAMP NOT NULL,

    -- Quando o admin julgou. NULL = está na fila. Ver o índice parcial adiante.
    --
    -- Marcar em vez de apagar é o que impede a redenúncia infinita: apagada a linha, o índice
    -- único abaixo deixaria a mesma pessoa reabrir o mesmo caso quantas vezes quisesse.
    resolved_at  TIMESTAMP,

    CONSTRAINT group_reports_um_alvo
        CHECK ((check_in_id IS NULL) <> (comment_id IS NULL)),

    -- Motivo obrigatório é motivo com CONTEÚDO: sem o `btrim`, um espaço satisfaria o NOT NULL e a
    -- regra viraria decoração. Mesmo CHECK do `body` dos comentários (V44).
    --
    -- O prazo de 7 dias (6.9) NÃO está aqui: ele depende do fuso do grupo e da data do alvo, que
    -- moram em outras tabelas. Quem confere é o Kotlin.
    CONSTRAINT group_reports_motivo_nao_vazio
        CHECK (char_length(btrim(reason)) BETWEEN 1 AND 300)
);

-- 6.11: denúncias múltiplas do mesmo alvo viram UMA solicitação, com contador.
--
-- O contador é `COUNT(*)` das linhas abertas — não uma coluna que alguém incrementa. E "uma por
-- pessoa" é o índice, não um SELECT antes: dois toques no botão com a rede lenta são duas
-- requisições em voo, e só o banco resolve o empate. Mesma escolha do check-in do dia (V38).
--
-- Dois índices parciais porque o alvo mora em duas colunas — o custo do arco exclusivo, cobrado
-- aqui.
CREATE UNIQUE INDEX group_reports_um_por_pessoa_check_in
    ON group_reports (check_in_id, reporter_id)
    WHERE check_in_id IS NOT NULL;

CREATE UNIQUE INDEX group_reports_um_por_pessoa_comentario
    ON group_reports (comment_id, reporter_id)
    WHERE comment_id IS NOT NULL;

-- A FILA do admin: as abertas deste grupo, mais antigas primeiro.
--
-- Mais antigas primeiro, ao contrário do feed (8.0.4): fila se atende por ordem de chegada, e o
-- que está esperando há mais tempo é o mais urgente. 6.12 diz que o admin não tem prazo — o que
-- torna a ordem justa ainda mais importante, porque a fila pode crescer.
CREATE INDEX group_reports_fila
    ON group_reports (group_id, created_at)
    WHERE resolved_at IS NULL;


-- ============================================================================
-- DECISÕES DO ADMIN — append-only (6.6)
-- ============================================================================
--
-- [INV] "Decisões do admin são imutáveis — correção é registro novo."
--
-- Não há `UPDATE` nem `DELETE` nesta tabela em nenhum caminho do código. Um admin que mudou de
-- ideia grava outra linha; a anterior continua lá, e a leitura vale pela mais recente. É o mesmo
-- desenho de `user_achievements` (#32): o histórico é o dado.
--
-- ## Por que o alvo aqui NÃO tem chave estrangeira
--
-- Uma FK obrigaria a escolher entre CASCADE (a auditoria some com o alvo — pior caso possível,
-- porque é exatamente o alvo removido que alguém vai querer auditar) e RESTRICT (o alvo não pode
-- mais ser apagado, e a purga dos 90 dias trava). As duas são piores do que um UUID solto.
--
-- O que se perde: nada garante que `target_id` aponta para uma linha existente. É aceitável — esta
-- tabela nunca é lida para montar tela; ela é lida quando alguém pergunta "o que aconteceu aqui?".

CREATE TABLE moderation_actions (
    id          UUID      NOT NULL PRIMARY KEY,

    group_id    UUID      NOT NULL REFERENCES groups(id) ON DELETE CASCADE,

    -- Quem decidiu. SET NULL e não CASCADE: o admin apagar a conta não pode apagar o histórico das
    -- decisões dele — e não pode travar a exclusão da conta, que é direito do usuário.
    admin_id    UUID      REFERENCES users(id) ON DELETE SET NULL,

    -- Sem FK, ver acima. O `target_type` é obrigatório justamente porque o UUID sozinho não diz
    -- em que tabela procurar.
    target_type TEXT      NOT NULL,
    target_id   UUID      NOT NULL,

    -- Vocabulário fechado no banco, ao contrário do emoji da V44 — e a diferença é o motivo.
    -- Emoji é decisão de PRODUTO e muda; o conjunto de decisões possíveis de um moderador é
    -- estrutural, e uma linha com `action = 'banir'` seria uma decisão que o código não sabe
    -- executar, silenciosamente gravada.
    action      TEXT      NOT NULL,

    created_at  TIMESTAMP NOT NULL,

    CONSTRAINT moderation_actions_alvo_conhecido
        CHECK (target_type IN ('CHECK_IN', 'COMMENT')),

    CONSTRAINT moderation_actions_acao_conhecida
        CHECK (action IN ('INVALIDAR_CHECK_IN', 'MANTER_CHECK_IN', 'REMOVER_COMENTARIO', 'MANTER_COMENTARIO'))
);

-- "O que já se decidiu sobre este alvo", mais recente primeiro — a leitura de auditoria.
CREATE INDEX moderation_actions_alvo
    ON moderation_actions (target_id, created_at DESC);

-- "O que este admin decidiu neste grupo". Sustenta uma eventual tela de histórico sem varredura.
CREATE INDEX moderation_actions_grupo
    ON moderation_actions (group_id, created_at DESC);
