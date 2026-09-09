-- Avisos diários do grupo (fatia F, decisões 10.6 e as duas emendas de 2026-09-07).
--
-- ============================================================================
-- POR QUE UMA TABELA SÓ AQUI, SE A V44 INSISTIU EM DUAS
-- ============================================================================
--
-- Na V44 o argumento foi que comentário e reação **têm formatos opostos** — muitos por pessoa
-- contra um por pessoa, texto livre contra vocabulário fechado. Uma tabela com `tipo` teria de
-- carregar o índice único de um sem aplicá-lo ao outro.
--
-- Aqui é o contrário, e por isso a conclusão inverte: "entradas do dia" e "fila parada" têm o
-- MESMO formato exato — um aviso, por grupo, por dia, no máximo um. O índice único é idêntico
-- para os dois. Separá-las criaria duas tabelas com o mesmo schema e duas consultas iguais.
--
-- > O critério nunca foi "uma tabela" nem "duas": é **se a regra do banco cabe em todas as
-- > linhas**. Aqui cabe.


-- ============================================================================
-- O CONTROLE DE "JÁ AVISEI ESTE GRUPO HOJE"
-- ============================================================================
--
-- ## Por que existe
--
-- 10.6: "no máximo **uma por dia**, para todos os membros". O teto precisa sobreviver a reinício do
-- servidor — o laço é uma corrotina no boot (como as duas purgas), e sem registro persistido
-- reiniciar duas vezes num dia mandaria o mesmo aviso duas vezes.
--
-- ## A PK composta É a regra
--
-- `(group_id, day, kind)`: o banco recusa o segundo aviso do mesmo tipo, no mesmo grupo, no mesmo
-- dia. Não há checagem no Kotlin porque não precisa — mesma escolha do check-in do dia (V38), da
-- reação (V44) e da denúncia (V45). **Quando a regra cabe numa chave, ela para de depender de
-- código correto.**

CREATE TABLE group_daily_notices (
    group_id UUID      NOT NULL REFERENCES groups(id) ON DELETE CASCADE,

    -- O DIA CIVIL no fuso do GRUPO, como `check_ins.local_date` (V38) — e pelo mesmo motivo: é a
    -- chave do índice, e índice não roda função de outra tabela. Um grupo em Tóquio "vira o dia"
    -- nove horas antes de um em São Paulo, e o aviso das entradas de hoje tem de seguir o dia
    -- daquele desafio, não o do servidor.
    day      DATE      NOT NULL,

    -- Qual aviso. Vocabulário fechado no banco, como o `action` da V45 e ao contrário do emoji:
    -- o conjunto de avisos diários é estrutural, e uma linha com um `kind` que o código não
    -- conhece seria um aviso registrado que nunca foi enviado.
    kind     TEXT      NOT NULL,

    sent_at  TIMESTAMP NOT NULL,

    PRIMARY KEY (group_id, day, kind),

    CONSTRAINT group_daily_notices_kind_conhecido
        CHECK (kind IN ('ENTRADAS_DO_DIA', 'FILA_PARADA'))
);

-- A varredura pergunta "o que já mandei HOJE?", não "o que mandei para este grupo". O índice segue
-- a consulta, e não a chave.
CREATE INDEX group_daily_notices_dia ON group_daily_notices (day);


-- ============================================================================
-- DENÚNCIA EM DESAFIO ENCERRADO FECHA SOZINHA
-- ============================================================================
--
-- Decisão de 2026-09-07, preenchendo uma lacuna que nenhuma das decisões da seção 6 cobria.
--
-- ## Por que aqui resolver automaticamente É honesto
--
-- A expiração por tempo foi **recusada** no mesmo dia, e o motivo é quem ganha com ela: se a
-- denúncia sumisse sozinha em 24h, a inação do admin viraria absolvição — bastaria não abrir o app.
-- A 6.12 já diz que ele não tem prazo, e isso funciona porque o `EM_ANALISE` continua contando
-- ponto (6.8): o caso parado não prejudica ninguém.
--
-- Com o desafio ENCERRADO é outra coisa. O ranking congelou e as recompensas foram concedidas;
-- invalidar depois mudaria um resultado que as pessoas já comemoraram, contra a 6.5 ("recompensa
-- concedida não é retirada"). Não há o que corrigir — e um caso que não pode produzir efeito não
-- deveria continuar pedindo decisão.
--
-- A ação entra no vocabulário fechado do `action` porque **precisa aparecer na auditoria**: quem
-- perguntar "por que esta denúncia sumiu?" seis meses depois merece uma resposta diferente de
-- "alguém julgou". O `admin_id` fica NULL — não foi decisão de ninguém.

ALTER TABLE moderation_actions
    DROP CONSTRAINT moderation_actions_acao_conhecida;

ALTER TABLE moderation_actions
    ADD CONSTRAINT moderation_actions_acao_conhecida
        CHECK (action IN (
            'INVALIDAR_CHECK_IN',
            'MANTER_CHECK_IN',
            'REMOVER_COMENTARIO',
            'MANTER_COMENTARIO',
            -- Sem julgamento: o desafio acabou antes de o admin decidir.
            'ENCERRADO_SEM_JULGAMENTO'
        ));
