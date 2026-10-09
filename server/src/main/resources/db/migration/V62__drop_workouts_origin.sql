-- B10: workouts.origin (V7) nunca foi lida nem escrita pelo servidor; a origem vive em
-- programs.origin (V12). Medido antes: 15 linhas, todas 'MANUAL' (o DEFAULT), logo nenhum
-- dado informativo se perde.
ALTER TABLE workouts DROP COLUMN origin;
