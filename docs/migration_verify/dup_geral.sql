-- Duplicatas REAIS no banco: nomes que só diferem por caixa/acento/pontuação.
WITH norm AS (
  SELECT id, name, category,
         regexp_replace(
           translate(lower(name),
             'áàâãäéèêëíìîïóòôõöúùûüçñ',
             'aaaaaeeeeiiiiooooouuuucn'),
           '[^a-z0-9]', '', 'g') AS chave
  FROM exercises
)
SELECT chave, count(*) AS qtd, string_agg(name, '  |  ' ORDER BY name) AS nomes
FROM norm GROUP BY chave HAVING count(*) > 1 ORDER BY qtd DESC, chave;
