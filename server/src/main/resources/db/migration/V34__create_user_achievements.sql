-- Conquistas desbloqueadas (ARCH #16).
--
-- Por que PERSISTIR, se XP e nível são derivados das sessões a cada consulta:
-- a conquista precisa sobreviver a uma mudança de regra. Se o limiar de um streak subir de 7
-- para 10, o cálculo puro faria a medalha SUMIR do perfil de quem já a tinha visto. Aqui o
-- desbloqueio é fato histórico — concedido uma vez, nunca retirado.
--
-- `achievement_id` é TEXT e não enum do Postgres de propósito: conquista nova não deve exigir
-- migration. O significado do id é contrato (ver AchievementPolicy.Conquista) e nunca muda.
CREATE TABLE user_achievements (
    user_id        UUID      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    achievement_id TEXT      NOT NULL,
    unlocked_at    TIMESTAMP NOT NULL DEFAULT now(),   -- relógio do SERVIDOR ([REGRA]: nunca o do cliente)
    PRIMARY KEY (user_id, achievement_id)
);

-- A PK composta é a garantia de idempotência: a concessão roda a cada sessão registrada e o
-- INSERT ... ON CONFLICT DO NOTHING não pode duplicar medalha nem sobrescrever a data original.

-- A leitura é sempre "as conquistas DESTE usuário, mais recentes primeiro" (a tela mostra em
-- ordem de desbloqueio); a PK cobre o filtro, este índice cobre a ordenação.
CREATE INDEX idx_user_achievements_unlocked ON user_achievements (user_id, unlocked_at DESC);

-- Sem backfill aqui de propósito. Quem já treinou recebe na PRÓXIMA avaliação, com
-- unlocked_at = agora. A data histórica real exigiria reprocessar sessão a sessão para
-- descobrir em que dia cada marco caiu — custo que só se paga se a data for exibida.
