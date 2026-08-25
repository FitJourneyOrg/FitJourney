-- Grupos (ARCH #33, Fase 6, fatia A.1).
--
-- NÃO EXISTE COLUNA `status`. O estado (AGENDADO/ATIVO/ENCERRADO) é função pura de
-- (data_inicio, data_fim, agora, fuso) e é derivado a cada leitura — mesma técnica do
-- `currentWeek` do programa (#22) e do XP (#16). Um job que "virasse a chave" na data de
-- início seria mais uma peça falhando em silêncio, e o SyncWorker já nos custou uma noite
-- exatamente assim. Derivar é sempre correto; persistir só é correto enquanto o job roda.

CREATE TABLE groups (
    id                UUID         PRIMARY KEY,

    -- Código de entrada: 6 caracteres, alfabeto sem O/0 e I/1 (ver GroupPolicy.ALFABETO).
    -- UNIQUE porque é por ele que se entra — colisão mandaria a pessoa para o grupo errado.
    code              VARCHAR(6)   NOT NULL UNIQUE,

    -- TEXT e não ENUM do Postgres, mesma escolha do `achievement_id` (#32): hoje só existe
    -- 'DESAFIO' e 'CONTAGEM_CHECKINS', e o segundo valor não pode custar migration. Um CHECK
    -- listando os valores teria o mesmo problema, então a validação é do Kotlin.
    type              TEXT         NOT NULL,
    scoring_model     TEXT         NOT NULL,

    title             VARCHAR(60)  NOT NULL,
    description       VARCHAR(300),

    -- DATE, não TIMESTAMP: um desafio dura DIAS, e o dia é resolvido no fuso do grupo (4.6).
    -- Com timestamp, criar um grupo exigiria escolher hora — pergunta que ninguém quer.
    start_date        DATE         NOT NULL,
    end_date          DATE         NOT NULL,

    -- Identificador IANA ('America/Sao_Paulo'), nunca offset ('-03:00'): offset quebra no
    -- horário de verão, e o grupo atravessa meses.
    timezone          TEXT         NOT NULL,

    -- Banner é opcional e chega na fatia A.3. A coluna nasce agora para a A.3 não precisar
    -- de migration só para uma URL.
    banner_url        TEXT,

    created_by        UUID         NOT NULL REFERENCES users(id),
    created_at        TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP    NOT NULL DEFAULT now(),

    -- Fim POSTERIOR ao início: desafio de duração zero ou negativa não é um desafio.
    -- É invariante de sistema, então mora no banco e não só no Kotlin.
    CONSTRAINT groups_periodo_valido CHECK (end_date > start_date)
);

-- Membros. O criador entra como ADMIN na MESMA transação da criação — grupo sem admin não
-- existe, nem por um instante.
CREATE TABLE group_members (
    group_id   UUID      NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    user_id    UUID      NOT NULL REFERENCES users(id)  ON DELETE CASCADE,
    role       TEXT      NOT NULL,   -- 'ADMIN' | 'MEMBRO' (TEXT pelo mesmo motivo acima)
    joined_at  TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (group_id, user_id)
);

-- A PK cobre "quem está neste grupo". Este índice cobre a pergunta oposta, que é a da tela
-- inicial de Grupos: "de quais grupos EU faço parte?".
CREATE INDEX idx_group_members_user ON group_members (user_id);

-- Regras obrigatórias do grupo (FOTO, LOCALIZACAO, EMOJI_DO_DIA; GYM_PASS declarada e
-- indisponível). Tabela filha em vez de coluna array: acrescentar um tipo de regra não custa
-- migration de estrutura, e a fatia D vai ler isto a cada check-in.
CREATE TABLE group_rules (
    group_id  UUID  NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    rule      TEXT  NOT NULL,
    PRIMARY KEY (group_id, rule)
);

-- Sem backfill e sem ALTER: as três tabelas são novas e não tocam dado existente.
