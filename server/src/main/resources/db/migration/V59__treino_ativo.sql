-- V59: ponteiro de "treino ativo" (1 por usuario, autoridade do servidor).
-- ON DELETE SET NULL: excluir o treino ativo nao deixa ponteiro pendurado.
ALTER TABLE users ADD COLUMN active_workout_id UUID REFERENCES workouts(id) ON DELETE SET NULL;
