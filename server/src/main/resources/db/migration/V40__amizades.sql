-- Amizades: o grafo do convite (ARCH #35, fatia #35.A).
--
-- Modelo FACEBOOK e não Instagram: amizade é SIMÉTRICA e exige aceite (35.1). Não existe
-- "seguir". A decisão foi reconfirmada em 2026-08-27, depois de a emenda 9.3-A ter esvaziado o
-- argumento original — o ADR justificava a simetria pelo consentimento de ver o perfil, e o
-- perfil ficou público. Ela permanece por outro motivo: convidar para desafio é ato entre pares,
-- e o teto de 50 do grupo (2.2) pede uma lista curta e mútua, não uma audiência.
--
-- ============================================================================
-- POR QUE DUAS TABELAS, E NÃO UMA COM STATUS = 'BLOQUEADA'
-- ============================================================================
--
-- O ADR listava os quatro estados juntos: PENDENTE, ACEITA, RECUSADA, BLOQUEADA. Juntá-los numa
-- tabela só esconde que são coisas de NATUREZA diferente:
--
--   amizade  é SIMÉTRICA   — aceitou, os dois são amigos; não há "de quem para quem"
--   bloqueio é DIRECIONAL  — A bloquear B não é B bloquear A, e os dois podem coexistir
--
-- Numa tabela única, A pedir amizade a B e B bloquear A produziriam duas linhas com direções
-- opostas para o mesmo par, e "estes dois são o quê?" passaria a depender de qual linha foi lida
-- primeiro. Separar torna a pergunta impossível de responder errado.
--
-- [EMENDA 35.2] `BLOQUEADA` sai do vocabulário de `friendships`. Ver `blocks`.


-- ============================================================================
-- 1. CÓDIGO DO USUÁRIO — 8 caracteres, permanente até ser regenerado
-- ============================================================================
--
-- `display_name` NÃO serve como endereço: não é único (V35 dispensou o UNIQUE de propósito) e
-- buscar por nome seria superfície de enumeração sem barreira nenhuma.
--
-- OITO caracteres, não seis como o do grupo. A diferença não é preciosismo — é que os dois
-- códigos têm vidas diferentes:
--
--   código do grupo    efêmero, só vale em AGENDADO, a porta fecha sozinha
--   código do usuário  PERMANENTE e sempre resgatável
--
-- Com o alfabeto de 32 caracteres, 6 posições dão ~1 bilhão; 8 dão ~1 trilhão. Mil vezes mais
-- caro varrer. As outras duas defesas vivem no Kotlin: limite de tentativas por conta e
-- regeneração pelo dono — e é a terceira que importa de verdade, porque devolve controle a quem
-- está sendo importunado em vez de depender de nós detectarmos o abuso.

-- 1.1 Entra nullable para o backfill poder rodar (mesma sequência da V35).
ALTER TABLE users ADD COLUMN code CHAR(8);

-- 1.2 Backfill.
--
-- `md5(random()::text || id::text)` dá 32 caracteres hex; `translate` mapeia o alfabeto hex
-- (0-9a-f) para 16 dos 32 caracteres do nosso alfabeto sem ambiguidade. O código gerado aqui usa
-- metade do alfabeto e é, portanto, MAIS FRACO que o gerado pelo Kotlin — é aceitável porque:
--   1. o volume atual é de contas de teste;
--   2. 16^8 ainda são 4 bilhões, mais que os 6 caracteres do grupo;
--   3. quem quiser um código forte regenera, e a regeneração passa pelo Kotlin.
-- O laço trata colisão: com poucas linhas ela é improvável, mas "improvável" não é "impossível"
-- e o UNIQUE do passo 1.3 não perdoa.
DO $$
DECLARE
    linha RECORD;
    candidato CHAR(8);
    tentativas INT;
BEGIN
    FOR linha IN SELECT id FROM users WHERE code IS NULL LOOP
        tentativas := 0;
        LOOP
            candidato := upper(translate(
                left(md5(random()::text || linha.id::text || clock_timestamp()::text), 8),
                '0123456789abcdef',
                'ABCDEFGHJKLMNPQR'
            ));
            EXIT WHEN NOT EXISTS (SELECT 1 FROM users WHERE code = candidato);
            tentativas := tentativas + 1;
            IF tentativas > 20 THEN
                RAISE EXCEPTION 'V40: 20 colisões seguidas gerando código para %', linha.id;
            END IF;
        END LOOP;
        UPDATE users SET code = candidato WHERE id = linha.id;
    END LOOP;
END $$;

-- 1.3 Agora as garantias podem ser cravadas.
ALTER TABLE users ALTER COLUMN code SET NOT NULL;
ALTER TABLE users ADD CONSTRAINT users_code_unico UNIQUE (code);

-- O CHECK cobre o que o Kotlin não alcança: escrita direta por script ou psql. O alfabeto é o
-- mesmo do código do grupo (GroupPolicy.ALFABETO) — sem O/0 e sem I/1, porque estes códigos são
-- DITADOS por voz e digitados à mão.
ALTER TABLE users
    ADD CONSTRAINT users_code_alfabeto CHECK (code ~ '^[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{8}$');


-- ============================================================================
-- 2. FRIENDSHIPS — par CANÔNICO
-- ============================================================================
--
-- `user_a` é sempre o MENOR uuid do par e `user_b` o maior. Isso é o coração da tabela:
--
--   * o UNIQUE do par passa a impedir **pedido cruzado** — A→B e B→A escrevem a mesma chave, e
--     a segunda inserção falha no banco em vez de criar dois pedidos vivos para o mesmo par;
--   * "somos amigos?" vira uma consulta por chave, sem `OR` de duas direções.
--
-- É a mesma jogada do UNIQUE (group_id, user_id, local_date) do check-in: a regra mora na
-- CONSTRAINT, não numa validação que alguém pode esquecer de chamar num caminho novo.
--
-- O preço é que a direção do PEDIDO precisa ser guardada à parte, em `requested_by` — sem ela
-- não dá para saber quem convidou quem, e o botão "Aceitar" apareceria para os dois lados.
CREATE TABLE friendships (
    user_a       UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    user_b       UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    -- Quem MANDOU o pedido. Sempre user_a ou user_b — o CHECK garante.
    requested_by UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    status       VARCHAR(16) NOT NULL,

    -- TIMESTAMP e sem DEFAULT, como `check_ins` (V38): quem carimba o tempo é o Kotlin, com
    -- `kotlin.time.Clock`. Um `DEFAULT now()` criaria um segundo relógio no sistema — o do banco
    -- — e o dia em que os dois discordassem seria muito difícil de investigar.
    created_at   TIMESTAMP   NOT NULL,
    responded_at TIMESTAMP,

    PRIMARY KEY (user_a, user_b),

    -- A ordem canônica é INVARIANTE de tabela, não convenção de código. Sem isto, uma escrita
    -- fora de ordem criaria o par duplicado que a PK deveria ter impedido — e o bug apareceria
    -- como "somos amigos em uma tela e não em outra".
    CONSTRAINT friendships_ordem_canonica CHECK (user_a < user_b),

    CONSTRAINT friendships_pedinte_do_par CHECK (requested_by IN (user_a, user_b)),

    -- Vocabulário fechado no banco. BLOQUEADA não está aqui de propósito (emenda 35.2).
    CONSTRAINT friendships_status_valido CHECK (status IN ('PENDENTE', 'ACEITA', 'RECUSADA')),

    -- Data de resposta e estado não podem discordar: PENDENTE sem resposta, decidida com data.
    CONSTRAINT friendships_resposta_coerente CHECK (
        (status = 'PENDENTE' AND responded_at IS NULL) OR
        (status <> 'PENDENTE' AND responded_at IS NOT NULL)
    )
);

-- "Meus amigos" e "meus pedidos pendentes" são as duas leituras quentes, e cada uma pode entrar
-- pelo lado A ou pelo lado B do par — daí dois índices e não um.
CREATE INDEX friendships_user_a_idx ON friendships (user_a, status);
CREATE INDEX friendships_user_b_idx ON friendships (user_b, status);


-- ============================================================================
-- 3. BLOCKS — direcional
-- ============================================================================
--
-- Uma linha por SENTIDO: A bloquear B e B bloquear A são fatos independentes e podem coexistir.
--
-- O que bloquear faz, decidido em 2026-08-27 (emenda 35.6): quem foi bloqueado vê um PERFIL
-- INDISPONÍVEL — genérico, sem nome, nível ou conquistas — **idêntico ao de uma conta excluída**.
-- Isso é deliberado: se a tela dissesse "você foi bloqueado", o bloqueio viraria um recado, e
-- quem bloqueia normalmente quer sumir, não avisar.
--
-- É ASSIMÉTRICO: quem bloqueou continua vendo o perfil do outro. Sem isso a lista de bloqueados
-- viraria uma fileira de "Usuário" e desbloquear seria adivinhação.
--
-- O GRUPO não muda ([REGRA] #35: amizade e grupo são independentes). O nome de quem bloqueou
-- continua no feed e no ranking — esconder linhas faria a contagem do ranking não bater
-- ([REGRA] #18) e duas pessoas no mesmo desafio veriam placares diferentes.
CREATE TABLE blocks (
    blocker_id UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    blocked_id UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP   NOT NULL,

    PRIMARY KEY (blocker_id, blocked_id),

    CONSTRAINT blocks_nao_a_si_mesmo CHECK (blocker_id <> blocked_id)
);

-- "Fulano me bloqueou?" é a pergunta feita a CADA leitura de perfil público, e ela entra pelo
-- lado do BLOQUEADO — que não é o prefixo da PK. Sem este índice, todo perfil aberto viria de
-- uma varredura.
CREATE INDEX blocks_blocked_idx ON blocks (blocked_id);
