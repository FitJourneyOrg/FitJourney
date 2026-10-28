-- Exporta o pool do motor (volume_target NOT NULL) para o simulador. Rodar contra o banco de dev:
--   docker exec -i <container-postgres> psql -U <usuario> -d <banco> -f - < exportar_catalogo.sql > catalogo_banco.csv
-- O simulador NAO le planilha: le isto. Se o banco mudar (migration de dado), regenerar.
--
-- Depois da V65 o export carrega tambem os atributos de selecao (prioridade, relevancia,
-- nivel_maximo, ambiente), a familia e os seis acessorios: e com eles que o preenchimento
-- escolhe o exercicio, no lugar de is_base + ordem alfabetica.
--
-- V67 (fatia 7, 2026-10-24): rir_minimo_seguranca entra tambem -- o oraculo precisa da mesma
-- coluna que o Kotlin le, para o RIR minimo e a troca de barra nao divergirem entre os dois
-- lados (licao da Fatia 6, onde a ordem composto-antes-de-isolado so entrou no Kotlin e so
-- apareceu no teste com o catalogo real).
COPY (
  SELECT name, volume_target, category, equipment, movement_pattern, is_compound, level, modality,
         is_base, array_to_string(primary_muscles, ',') AS primary_muscles,
         prioridade, relevancia, nivel_maximo, ambiente, familia,
         requires_bench, requires_elevated_surface, requires_pullup_bar,
         requires_dip_station, requires_step, requires_anchor_point,
         rir_minimo_seguranca
    FROM exercises
   WHERE volume_target IS NOT NULL
   ORDER BY volume_target, prioridade
) TO STDOUT WITH (FORMAT csv, HEADER);
