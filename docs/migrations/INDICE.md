# Índice de migrations

> **Gerado a partir do código** (`server/src/main/resources/db/migration/`), que é a fonte da
> verdade. A descrição é o primeiro comentário de cada arquivo. Conferido em **2026-10-07**.
>
> Existe porque "o que a V53 fez?" exigia ler três documentos: o detalhe estava dissolvido na
> prosa do Painel e do Handoff, nunca numa lista. Schema das tabelas de exercício em
> `BANCO_EXERCICIOS.md`.

**Estado: V69 no código, local — não commitada** (ver `ESTADO.md`). ⚠️ Até 2026-10-07 os
documentos de estado paravam na **V60** — a V61 existia no código e não aparecia em nenhum deles.

## V38 → V63

| # | Arquivo | O que fez | Fatia |
|---|---|---|---|
| V38 | `create_check_ins` | Check-in | B · ARCH #33 |
| V39 | `thumbs_para_webp` | Miniaturas PNG → WebP (higiene de armazenamento) | — |
| V40 | `amizades` | O grafo do convite. PK do par canônico | #35.A · ARCH #35 |
| V41 | `device_tokens` | Tokens de aparelho para push. `ON CONFLICT (token) DO UPDATE` | F.1 |
| V42 | `notifications` | Central de notificações. `INSERT` em `JSONB` | F.1 · ARCH #36 |
| V43 | `checkin_emoji` | Emoji do dia no check-in | D · regra 5.1 |
| V44 | `comentarios_e_reacoes` | Comentários e reações. PK composta da reação | E.1 · decisões 8.1–8.4 |
| V45 | `denuncias_e_moderacao` | Denúncia e moderação. 2 `CHECK`, 2 índices únicos parciais, `CASCADE` | E.2 · ARCH #33 §6 |
| V46 | `avisos_diarios` | Avisos diários do grupo. PK tripla, `CHECK` do `kind` | F · 10.6 |
| V47 | `idioma_do_usuario` | `users.locale` | G.1 · ARCH #37 |
| V48 | `nome_do_programa_derivado` | Nome do programa deixa de ser prosa do servidor | G.5 · ARCH #37 |
| V49 | `catalogo_traduzido` | `exercise_translations` (ociosa até a V53) | H · ARCH #37 |
| V50 | `caminhos_das_midias` | **DADO.** Conserta os caminhos que serviam 404 em ~900 | H |
| V51 | `remove_duplicados` | **DADO.** Os 39 duplicados | H |
| V52 | `nomes_revisados` | **DADO.** 788 nomes revisados + 9 travessões | H sessão A |
| V53 | `carga_descricoes_e_traducoes` | **DADO.** `description` (923) + translations en (923). Casou por `name` — ⚠️ case-sensitive, não pegou 4 duplicatas | H sessão B |
| V54 | `limpeza_exercicios_legado` | **DADO.** Consolidou os 4 duplicados; apagou 3 em uso + 19 sem uso | H sessão B |
| V55 | `novos_exercicios_do_catalogo` | **DADO.** Os 23 do catálogo que nunca tiveram linha, todos com vídeo em disco | H sessão B |
| V56 | `programa_split_enum_e_foco_derivado` | `rationale` **DROPADA**; `split` persiste como chave do enum + `focusMuscles` | rationale derivado |
| V57 | `curadoria_tagueamento` | **DADO.** Famílias com músculo inconsistente + invisíveis | — |
| V58 | `prancha_isometrica` | **DADO.** Reinsere "Prancha" — ⚠️ pagou um débito que seguiu listado como aberto | — |
| V59 | `treino_ativo` | `users.active_workout_id`. **⚠️ Revertida pela V60** | treino ativo |
| V60 | `programa_ativo` | `users.active_program_id`; ativação vira de PROGRAMA | V60 |
| V61 | `wiki` | Acervo de conteúdo. Mesma modelagem de tradução da V49 | **Fase 8 "Aprender"** |
| V62 | `drop_workouts_origin` | Remove `workouts.origin` (V7), coluna que nada lia; a origem é `programs.origin`. Medido: 15 linhas, todas `MANUAL` | B10 |
| V63 | `dedup_stiff_terra_romeno` | Une terra romeno e stiff (o Stiff fica): apaga os 2 Terra Romeno, repõe FK defensivamente, `Stiff com Halteres` herda a taxonomia de hinge do `Stiff com Barra`. Medido: 0 uso nos que somem. Catálogo 924→922 | C4 |

## V64 → V69 (motor v3, não commitadas)

| # | Arquivo | O que fez | Fatia |
|---|---|---|---|
| V64 | `alvo_de_volume_do_motor` | **DADO, GERADA** (`server/src/test/oracle/gerar_v64.py`). Um exercício, um alvo de volume. **Regerada em 2026-10-21** (nunca tinha sido aplicada) com os 147 nomes da lista principal — a versão de 79 ficou em `server/src/test/oracle/historico/alvo_canonico_79.csv` | Motor v3 — fatia 0 |
| V65 | `curadoria_do_catalogo` | **DADO, GERADA** (`tools/curadoria_oficial/gerar_v65.py`). Taxonomia dos 893 exercícios e os atributos que escolhem o exercício: remove 29 (guarda contra uso em `workout_exercises`), cria `prioridade`/`relevancia`/`nivel_maximo`/`ambiente` (seleção) e `familia` + seis `requires_*` (execução), realinha `is_base` com `relevancia`, fecha num `CHECK` | Motor v3 — fatia 0 |
| V66 | `acessorios_do_usuario` | `profiles.accessories TEXT` (JSON), nullable — o outro lado do par dos seis `requires_*` da V65: o que o usuário *tem*, não só o que o exercício *exige*. `NULL` = nunca perguntado, `[]` = perguntado e nada declarado — distinção travada em `ProfileRepositoryImpl` e `AcessoriosPersistenciaIntegrationTest` | Acessórios no onboarding |
| V67 | `rir_minimo_de_seguranca` | `exercises.rir_minimo_seguranca BOOLEAN NOT NULL DEFAULT false`. RIR mínimo por propriedade (não derivável de `movement_pattern`/`equipment`) — aprovado por nome contra `tools/curadoria_oficial/curadoria_canonica.csv`, 8 exercícios `true` (peso livre com barra/landmine, carga na coluna ou sobre a cabeça). Autoconferência da contagem no fim (mesmo padrão da V64) | Motor v3 — fatia 7 |
| V68 | `core_sai_do_motor` | **SCHEMA (constraint).** Aperta `chk_exercises_volume_target` para recusar `'CORE'`: o alvo saiu do motor (`TargetMuscle`) e do contrato (`MuscleGroup`). Não reescreve dado — um bloco `DO` **falha alto** se alguma linha ainda tiver `volume_target = 'CORE'`. Medido antes: 0 exercícios com o alvo, 0 perfis com `CORE` em `focus_areas`, 0 programas com `CORE` em `focus_muscles`; e a remoção não mudou nenhum número da matriz dos 720 (só zerou os 720 `SEM_CATALOGO`, que eram todos CORE) | Motor v3 — fatia 8 |
| V69 | `progressao_por_exercicio` | **SCHEMA (aditiva).** 3 tabelas NOVAS — `exercise_progression` (PK usuário+exercício, D-1), `user_mesocycle` (por pessoa, com `conservador`, D-4) e `progression_event` (append-only; PK usuário+exercício+sessão = chave de idempotência, D-2; coluna `seq` IDENTITY dá a ordem do histórico, sem depender de relógio) — e `session_set_logs.rir SMALLINT NULL` com CHECK 0..10 (D-3, sem backfill; RIR nulo = só regras por repetição). **Não apaga nem reescreve dado**: nenhuma linha existente muda. Nada grava `rir` ainda (contrato/DTO é o passo seguinte) | Motor v3 — fatia 10b |
| V70 | `recusa_de_promocao` | **SCHEMA (aditiva).** 1 tabela NOVA — `promotion_decline` (PK `(user_id, nivel_proposto)`, D-1: guarda só a recusa MAIS RECENTE por par, upsert; `mesociclo_na_recusa` copiado de `user_mesocycle.numero` no momento da recusa, D-2, contagem exata de mesociclo — não heurística de calendário). A sugestão de promoção em si não é persistida, é derivada do histórico que a V69 já guarda; esta tabela só existe para o motor saber que já perguntou e quando pode perguntar de novo (`PromocaoDeNivel.podeSugerirDeNovo`). **Não apaga nem reescreve dado**: tabela nova. Desenho em `arch/motor-v3-progressao-schema.md` | Motor v3 — fatia 11 |

V1 → V37: fundação, catálogo, treino, programas, sessões, conquistas e grupos. Detalhe no arquivo
de cada uma; curadoria do catálogo em `BANCO_EXERCICIOS.md`.

## Convenções que as migrations desta faixa fixaram

- **Migration de DADO é marcada como tal.** V50–V55, V57 e V58 não mexem em schema: corrigem
  conteúdo. Misturar as duas coisas num arquivo é o que torna um rollback impossível de raciocinar.
- **Guarda que falha alto.** A V56 recusa valor fora do mapeamento `label` → enum conhecido. A
  V53/V54 não tinham essa guarda — achado de PR review.
- **Match por `name` sem `chave` persistida vai quebrar de novo.** `Stiff com halteres` ≠
  `Stiff com Halteres` para o Postgres, e são o MESMO exercício. A V33 já havia registrado a mesma
  família de risco por outro caminho. `chave` continua no catálogo sem uso — candidata a coluna
  persistida se isso quebrar uma terceira vez.
- **Reversão não apaga o histórico.** A V60 reverte a V59 com uma migration nova, não editando a
  anterior. As duas ficam na lista, e a V59 fica marcada.
