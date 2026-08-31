-- Central de notificações (fatia F.1, decidido em 2026-08-27).
--
-- ============================================================================
-- POR QUE ISTO EXISTE: PUSH É EFÊMERO
-- ============================================================================
--
-- O FCM não guarda nada. A mensagem chega, aparece na bandeja, e se a pessoa dispensar — ou
-- reiniciar o celular, ou negar a permissão — sumiu. Não existe "buscar o que recebi" no FCM.
--
-- Esta tabela inverte a relação: **a notificação gravada é a VERDADE, o push é só o aviso dela**.
--
--   grava  ->  a notificação existe, e o sininho vai mostrá-la
--   push   ->  tentativa de avisar AGORA; se falhar, não se perdeu nada
--
-- É por isso que a gravação acontece ANTES do despacho, e é ela que não pode falhar em silêncio.
-- Sem esta tabela, quem negasse a permissão de notificação nunca saberia de um pedido de amizade
-- até abrir a tela certa por acaso.

CREATE TABLE notifications (
    id           UUID        NOT NULL PRIMARY KEY,

    -- Quem RECEBE. CASCADE: conta apagada leva as notificações junto.
    user_id      UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    -- Vocabulário aberto de propósito, como `achievement_id` (#32): tipo novo não exige
    -- migration. O CHECK vive no Kotlin, onde o enum é a fonte da verdade — e um tipo removido
    -- numa versão futura vira linha órfã que a tela ignora em silêncio, em vez de derrubá-la.
    type         VARCHAR(32) NOT NULL,

    -- O texto JÁ RENDERIZADO, e não os ingredientes para montá-lo.
    --
    -- "Rafael quer ser seu amigo" é gravado assim, com o nome dentro. A alternativa — guardar só
    -- o id e montar a frase na leitura — parece mais limpa e é pior: se o Rafael trocar de nome
    -- amanhã, a notificação de ontem passaria a dizer o nome novo, contando uma história que não
    -- aconteceu. **Notificação é registro do que houve, não uma consulta ao presente.**
    title        VARCHAR(120) NOT NULL,
    body         VARCHAR(300) NOT NULL,

    -- Dados para a navegação (o mesmo `data` do push): tipo, fromUserId. JSONB e não colunas
    -- porque cada tipo de notificação leva chaves diferentes, e uma coluna por chave viraria uma
    -- tabela larga e cheia de NULL na primeira notificação nova.
    data         JSONB       NOT NULL DEFAULT '{}'::jsonb,

    -- NULL = não lida. É o que o contador do sininho conta.
    read_at      TIMESTAMP,

    created_at   TIMESTAMP   NOT NULL
);

-- A leitura da central: "minhas notificações, mais recente primeiro". O índice cobre também a
-- contagem de não-lidas, que filtra por `read_at IS NULL` dentro do mesmo conjunto.
CREATE INDEX notifications_user_idx ON notifications (user_id, created_at DESC);

-- ============================================================================
-- RETENÇÃO: 6 MESES (decidido em 2026-08-27)
-- ============================================================================
--
-- Notificação de seis meses atrás não serve a ninguém, e a tabela cresce por usuário para sempre
-- sem um corte. O número é do Rafael; o índice é o que torna a purga barata.
--
-- **Índice parcial**, como o da purga de mídia (V38): ele só indexa o que a purga procura —
-- linhas VELHAS. Um índice completo em `created_at` seria mantido a cada inserção para servir
-- uma consulta que roda uma vez por dia.
--
-- O predicado usa data fixa porque índice parcial não aceita `now()` (não é IMMUTABLE). A data
-- é folgada de propósito: serve como corte grosseiro, e o `WHERE` da purga faz o corte fino.
CREATE INDEX notifications_purga_idx
    ON notifications (created_at)
    WHERE created_at < '2027-01-01';
