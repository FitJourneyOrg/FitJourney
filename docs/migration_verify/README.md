# migration_verify/ — provar o que a migration deveria ter deixado

> SQL rodado à mão (`psql`) contra o Postgres vivo, para verificar o **estado real do dado**
> depois de uma migration — não o schema (isso o Flyway garante sozinho), o **conteúdo**.
>
> Separado de `tools/curadoria/` em 2026-10-07. Motivo registrado em `tools/README.md`: **uma
> migration mais recente não prova que uma anterior deixou o dado certo.** A V57 (set/2026) existiu
> porque a taxonomia continuou inconsistente depois da V30; a V53 deixou 4 duplicatas passarem sem
> nenhuma migration falhar. Migration de schema falha alto; migration de **dado** não — só um
> SELECT que conta o que sobrou prova isso. É função permanente, e por isso mora perto da doc da
> migration (`docs/migrations/INDICE.md`), não dentro do pipeline de curadoria que gera migration
> nova.

| Script | O que prova | Resultado esperado |
|---|---|---|
| `dup_geral.sql` | Duplicata real no catálogo: nomes iguais ignorando caixa/acento/pontuação | **0 linhas**. Citado em `docs/fontes/debitos.md` — a V30+curadoria deixou `965 exercícios · sem_taxonomia = 0 · 0 duplicata` |
| `dupcheck.sql` | Um par específico suspeito de duplicata é o MESMO exercício no banco, não coincidência de nome | Confirma ou descarta cada par listado no arquivo |
| `relatorio_catalogo.sql` | Retrato geral: total, completude (descrição/taxonomia/mídia), distribuição por categoria e por músculo | Números batem com o que o `ESTADO.md` e o `debitos.md` declaram |

## Quando rodar

Depois de qualquer migration de **dado** no catálogo (reclassificação de taxonomia, dedup, carga de
descrição/tradução) — não depois de migration de schema puro. Rode, cole o resultado, e se algo não
bater, é achado de verdade, não suspeita.

## Convenção

Scripts sem parâmetro, pensados para `psql -f` e leitura do resultado por você — nenhum grava nada.
Arquivo novo de verificação entra aqui, não em `tools/curadoria/`: lá é o que **gera** migration,
aqui é o que **confere** o que ela deixou.
