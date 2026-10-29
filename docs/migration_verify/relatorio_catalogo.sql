\echo '===== 1. TOTAL E COMPLETUDE ====='
SELECT count(*) AS total,
       count(*) FILTER (WHERE description IS NOT NULL AND description <> '') AS com_descricao,
       count(*) FILTER (WHERE cardinality(primary_muscles) > 0)              AS com_taxonomia,
       count(*) FILTER (WHERE video_ref <> '' AND thumb_ref <> '')           AS com_midia,
       count(*) FILTER (WHERE is_base)                                        AS is_base
FROM exercises;

\echo ''
\echo '===== 2. POR CATEGORIA ====='
SELECT category, count(*) AS qtd FROM exercises GROUP BY category ORDER BY qtd DESC;

\echo ''
\echo '===== 3. POR MÚSCULO PRIMÁRIO ====='
SELECT unnest(primary_muscles) AS musculo, count(*) AS qtd
FROM exercises GROUP BY 1 ORDER BY qtd DESC;

\echo ''
\echo '===== 4. POR EQUIPAMENTO ====='
SELECT coalesce(equipment,'(nulo)') AS equipamento, count(*) AS qtd
FROM exercises GROUP BY 1 ORDER BY qtd DESC;

\echo ''
\echo '===== 5. POR NÍVEL ====='
SELECT coalesce(level,'(nulo)') AS nivel, count(*) AS qtd,
       round(100.0*count(*)/sum(count(*)) OVER (), 1) AS pct
FROM exercises GROUP BY 1 ORDER BY qtd DESC;

\echo ''
\echo '===== 6. O QUE O MOTOR ENXERGA (modality=STRENGTH) ====='
SELECT coalesce(modality,'(nulo)') AS modality, count(*) AS qtd
FROM exercises GROUP BY 1 ORDER BY qtd DESC;

\echo ''
\echo '===== 7. DISPONÍVEL EM CASA (peso corporal/halter/elástico) ====='
SELECT count(*) AS treino_em_casa FROM exercises
WHERE equipment IN ('BODYWEIGHT','DUMBBELL','BAND','KETTLEBELL','SUSPENSION');

\echo ''
\echo '===== 8. COMPOSTO x ISOLAMENTO / UNILATERAL ====='
SELECT count(*) FILTER (WHERE is_compound) AS compostos,
       count(*) FILTER (WHERE NOT is_compound) AS isolamentos,
       count(*) FILTER (WHERE unilateral) AS unilaterais
FROM exercises;
