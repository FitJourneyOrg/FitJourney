-- Existe MESMO um par duplicado no banco? (orfao x suposto original)
WITH pares(orfao, suposto) AS (VALUES
 ('Flexão diamante','Flexão diamante'),
 ('Supino declinada na Máquina','Supino declinada na máquina'),
 ('Extensão de Tríceps com Faixa Elástica','Extensão de Tríceps com Faixa Elástica'),
 ('Rosca bíceps com faixa elástica','Rosca bíceps com faixa elástica'),
 ('Glúteo Coice na Máquina','Glúteo coice na máquina'),
 ('Dips na cadeira','Dips na cadeira'),
 ('Alongamento de Punho','Alongamento de Punho'),
 ('Remada na Máquina','Remada Sentada na Máquina'),
 ('Rosca Scott na Máquina','Rosca Scott com Alavanca'),
 ('Crossover na Máquina','Crossover na Alavanca'),
 ('Supino com Anilha','Anilha Press'),
 ('Afundo com Cabo','Avanço com Cabo'),
 ('Remada Alta','Remada Alta (1)'),
 ('Cadeira / Mesa Flexora','Cadeira flexora'),
 ('Mergulho de tríceps na Máquina','Mergulho de tríceps com alavanca')
)
SELECT p.orfao,
       (SELECT count(*) FROM exercises e WHERE e.name = p.orfao)   AS orfao_existe,
       (SELECT count(*) FROM exercises e WHERE e.name = p.suposto) AS suposto_existe
FROM pares p ORDER BY 3 DESC, 1;
