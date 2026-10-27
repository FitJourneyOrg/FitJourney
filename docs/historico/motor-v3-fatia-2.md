# Motor v3 — Fatia 2: capacidade e teto da sessão (fechada em 2026-10-23)

Segunda fatia do motor v3, etapa [2]: `CapacityModel`, peça nova — hoje não existe teto por
sessão nenhum (`capacity_sets()` devolve 92,2 séries/semana, 23,1 por sessão, sem limite).

**Duas decisões de arquitetura, as duas resolvendo lacunas que a especificação não cobria:**

1. **Overhead deriva de `dias`, não de `SplitType`.** A especificação só define o overhead da
   rampa para FULL_BODY (16min) e "upper/lower e PPL" (11min) — o catálogo real tem também
   `UPPER_LOWER_FULL` e `ARNOLD`, que a especificação não menciona. O motor v3 nunca gera essas
   duas: pela especificação, a divisão sai do número de dias (2–3 → full body, 4 → upper/lower,
   5 → upper/lower+PPL, 6 → PPL), e as duas de fora são do motor antigo. Por isso
   `CapacityModel` recebe `dias` e deriva o overhead direto disso, falhando alto para qualquer
   valor fora de 2..6 em vez de herdar 11min em silêncio. Oferecer `UPPER_LOWER_FULL`/`ARNOLD`
   no v3 é decisão de produto pendente — registrada em `fontes/debitos.md`, não resolvida em
   código.

2. **Fator de idade decide a regra inteira, com mesociclo nulo contando como o primeiro.** O
   fator 0,75 vale para 60+ iniciante OU no 1º mesociclo, mas mesociclo só existe a partir da
   Fatia 10 — e a Fatia 3 (próxima) já vai chamar `CapacityModel` de verdade, então a decisão não
   podia mais ficar pendente como na Fatia 1. `fatorIdade(idade, nivel, mesocicloAtual)` mantém a
   regra da especificação inteira e testável num lugar só; `mesocicloAtual == null` conta como
   "1º mesociclo" porque o app ainda não acompanha isso, então hoje todo aluno de 60+ recebe
   0,75, independente do nível — lado seguro, e é esse o objetivo da regra. Quando a Fatia 10
   existir, só quem chama passa o valor real; a regra em si não muda.

**Achado ao implementar:** o teto precisa ficar em `Double` até a alocação (Fatia 3) — arredondar
o teto por tempo antes do `min()` quebra a tabela de cobertura da especificação em várias
células (confirmado batendo as 27 células, 3 níveis × 3 faixas de tempo × dias 2–6, contra o
documento). O arredondamento para séries inteiras é explicitamente da Fatia 3, na ordem de
prioridade — nunca nesta etapa.

**Teste (`CapacityModelTest`, 8 casos, verde):** as 27 células da tabela de cobertura
(tolerância de 1pp pelo arredondamento do documento), o caso "75min só rende mais que 60 a
partir de 4 dias", overhead por `dias`, falha alta fora de 2..6, e os fatores de objetivo/idade
isolados.

Nenhuma migration.
