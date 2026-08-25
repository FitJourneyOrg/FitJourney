-- Check-in (ARCH #33 emendado, fatia B).
--
-- SEM votação: o #17 foi emendado e o check-in NASCE VÁLIDO (4.9). Não há coluna de votos, nem
-- janela de apuração, nem job de fechamento — tudo isso saiu com a revogação.

CREATE TABLE check_ins (
    id          UUID PRIMARY KEY,
    group_id    UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users(id)  ON DELETE CASCADE,

    -- O DIA CIVIL no fuso do GRUPO (4.6), e não a data do servidor nem a do aparelho.
    --
    -- É derivado de (created_at, fuso do grupo), e normalmente derivado não se persiste — mas
    -- aqui ele É a chave do índice único, e índice não roda função de outra tabela. Persistir é
    -- seguro porque o fuso só é editável com o grupo AGENDADO (2-B.3) e check-in só existe com
    -- ele ATIVO (invariante): quando a primeira linha entra, o fuso já está congelado.
    local_date  DATE NOT NULL,

    -- Relógio do SERVIDOR (4.5). O do aparelho não entra em nada: [REGRA] autoridade do servidor.
    created_at  TIMESTAMP NOT NULL,

    -- VALIDO nasce assim (4.9). EM_ANALISE e INVALIDADO só passam a existir na fatia E, com a
    -- denúncia — mas o vocabulário nasce fechado para o estado não ser texto livre.
    status      TEXT NOT NULL DEFAULT 'VALIDO',

    -- Referência OPACA do ArmazenamentoDeMidia, não um caminho de arquivo. É o que permite trocar
    -- disco por R2 sem tocar nesta tabela. NULL quando o grupo não exige FOTO.
    photo_ref   TEXT,

    -- 4.8: a foto some em 90 dias, o CHECK-IN PERMANECE. Sem isto, "tem foto?" e "tinha foto?"
    -- seriam a mesma pergunta, e o feed não saberia distinguir "nunca teve" de "expirou".
    photo_purged_at TIMESTAMP,

    -- 5.2: o que o grupo vê é o NOME que a pessoa escreveu. NULL quando o grupo não exige.
    place_name  VARCHAR(60),

    -- Arredondada a 2 casas (~1 km) e NUNCA exibida (invariante). Existe só para viabilizar um
    -- mapa no futuro sem migration. Gravar a coordenada cheia seria PII que ninguém pediu.
    place_lat   NUMERIC(5, 2),
    place_lng   NUMERIC(5, 2),

    CONSTRAINT check_ins_status_valido
        CHECK (status IN ('VALIDO', 'EM_ANALISE', 'INVALIDADO')),

    -- 5.2: coordenada e nome andam juntos ou não andam. Meia localização é dado que a tela não
    -- sabe apresentar.
    CONSTRAINT check_ins_local_completo
        CHECK ((place_name IS NULL) = (place_lat IS NULL)
           AND (place_lat IS NULL) = (place_lng IS NULL)),

    -- Foto purgada exige foto. Sem isto, uma linha poderia dizer "expirou" sem nunca ter tido.
    CONSTRAINT check_ins_purga_exige_foto
        CHECK (photo_purged_at IS NULL OR photo_ref IS NOT NULL)
);

-- INVARIANTE: um por pessoa/dia/grupo (4.3), no fuso do grupo.
--
-- No banco e não no código: dois toques no botão com a rede lenta são duas requisições em voo, e
-- só o índice decide qual vence. Apagar o próprio check-in (4.11) libera o slot naturalmente,
-- porque a linha some de verdade.
CREATE UNIQUE INDEX check_ins_um_por_dia
    ON check_ins (group_id, user_id, local_date);

-- O FEED (8.0.4): mais recente primeiro, sempre dentro de um grupo.
CREATE INDEX check_ins_feed
    ON check_ins (group_id, created_at DESC);

-- A varredura dos 90 dias (4.8) percorre o que ainda tem foto viva, e não a tabela inteira.
CREATE INDEX check_ins_foto_a_purgar
    ON check_ins (created_at)
    WHERE photo_ref IS NOT NULL AND photo_purged_at IS NULL;
