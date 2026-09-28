-- V60: ativacao vira conceito de PROGRAMA, nao de treino (reverte parte da V59).
-- POR QUE: a Home ja deriva "treino de hoje" pelo schedule (ARCH #22), cruzando TODOS os
-- programas -- o ponteiro por treino so servia de fallback pra programa sem agenda, e
-- fazia "ativar" parecer uma escolha entre treinos soltos quando o usuario pensa em termos
-- de PROGRAMA (ver ARCH #27: varios programas coexistem, nao ha mais "1 ativo que substitui" na
-- CRIACAO -- isto aqui e so um PONTEIRO de destaque, nao um substituto, mesmo espirito da V59).
--
-- ON DELETE SET NULL: excluir o programa ativo nao deixa o ponteiro pendurado.
ALTER TABLE users DROP COLUMN active_workout_id;
ALTER TABLE users ADD COLUMN active_program_id UUID REFERENCES programs(id) ON DELETE SET NULL;
