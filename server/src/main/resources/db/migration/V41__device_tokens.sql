-- Tokens de aparelho para push (fatia F.1, ARCH #35 + F).
--
-- Puxada para junto da #35 em 2026-08-27: o ADR já dizia que "convidar só funciona se a pessoa
-- ficar sabendo", e a tela de pedidos era paliativo justamente porque a notificação viria depois.
-- A F.1 traz a INFRAESTRUTURA inteira com UM caso de uso real (pedido de amizade); os outros
-- tipos entram depois, só plugando. Infra nascida de um caso concreto erra menos que infra
-- genérica feita no vácuo — foi assim que o outbox (#30) ficou bom.
--
-- ============================================================================
-- POR QUE UMA TABELA, E NÃO UMA COLUNA EM `users`
-- ============================================================================
--
-- Uma pessoa tem VÁRIOS aparelhos — celular, tablet, o celular velho que ficou na gaveta. Uma
-- coluna `fcm_token` em `users` guardaria só o último, e quem instalasse o app num segundo
-- aparelho silenciosamente pararia de receber no primeiro.
--
-- O token também NÃO é estável: o FCM o reemite quando o app é reinstalado, quando os dados são
-- limpos, ou por decisão dele. Ele é identidade de INSTALAÇÃO, não de usuário.

CREATE TABLE device_tokens (
    -- O próprio token é a chave. Ele já é único por instalação, e usar um id sintético exigiria
    -- um UNIQUE no token de qualquer forma — a chave natural existe, então é ela.
    token       TEXT        NOT NULL PRIMARY KEY,

    -- ON DELETE CASCADE: conta apagada leva os tokens junto. Sem isso, o servidor continuaria
    -- tentando notificar aparelhos de uma conta que não existe mais.
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    -- Quando o cliente registrou. Serve para diagnóstico e para uma futura limpeza de tokens
    -- muito antigos, que o FCM às vezes aceita mas nunca entrega.
    created_at  TIMESTAMP   NOT NULL,

    -- Atualizado a cada registro do MESMO token. É o que distingue "aparelho ativo" de "token que
    -- ficou para trás numa reinstalação".
    updated_at  TIMESTAMP   NOT NULL
);

-- ============================================================================
-- POR QUE O TOKEN PODE TROCAR DE DONO — e por que isso é ESPERADO
-- ============================================================================
--
-- O mesmo aparelho pode ser usado por duas contas: alguém empresta o celular, ou o próprio dono
-- tem conta de teste e conta real. O FCM devolve o MESMO token para a instalação, então o
-- registro precisa **sobrescrever o user_id** em vez de falhar.
--
-- Isso é `INSERT ... ON CONFLICT (token) DO UPDATE SET user_id = ...`, feito no Kotlin.
--
-- **E é por isso que o logout PRECISA apagar o token.** Sem isso, quem saiu continuaria
-- recebendo notificações no aparelho até a próxima pessoa fazer login — e nesse intervalo o
-- push chegaria para quem não deveria ver. É o item da F.1 mais fácil de esquecer e o de pior
-- consequência: só aparece quando alguém empresta o celular.

-- "Todos os aparelhos deste usuário" é a leitura de TODO envio — a única, na verdade.
CREATE INDEX device_tokens_user_idx ON device_tokens (user_id);
