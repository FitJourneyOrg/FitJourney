-- V24: janela de semanas no programa (cronograma). ARCH #22 estendido: o programa passa a ter
-- DURAÇÃO (X semanas) e DATA DE INÍCIO. A "semana atual" NÃO é armazenada — é derivada no
-- servidor (autoridade) a partir de started_at. [INV] mínimo/default = 8 semanas (2 meses).
ALTER TABLE programs ADD COLUMN duration_weeks INT NOT NULL DEFAULT 8;
ALTER TABLE programs ADD COLUMN started_at TIMESTAMP;

-- retrocompat: programas existentes começam na data de criação.
UPDATE programs SET started_at = created_at;

ALTER TABLE programs ALTER COLUMN started_at SET NOT NULL;
ALTER TABLE programs ALTER COLUMN started_at SET DEFAULT now();
