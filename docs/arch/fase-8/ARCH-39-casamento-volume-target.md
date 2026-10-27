# ARCH #39 — Casamento músculo↔exercício: coluna dedicada (`volume_target`), não heurística

> Motor v3 · fatia 0 · decidido em 2026-10-14 ("seguir o plano", Rafael). Migration **V64**
> (`alvo_de_volume_do_motor`). **Decisão tomada, motor Kotlin ainda não implementa** — fica para
> uma fatia do motor v3 (1–12, `trabalho/motor-v3-desenho-e-fatias.md`).

## O problema

`SlotFiller.matches()` hoje casa músculo com exercício por dois critérios que se sobrepõem:
`movement_pattern` decide QUADS/POSTERIOR, etiqueta `primary_muscles` decide GLUTES. Um exercício
pode ser candidato legítimo de dois alvos ao mesmo tempo — Afundo com Barra é QUADS pelo padrão
**e** GLUTES pela etiqueta — e quem fica com ele depende da ordem de preenchimento e do scorer, não
de uma decisão. Na prática ele vira a âncora de glúteo com 10 séries/semana no avançado.

Agravante: o vocabulário de `primary_muscles` (`MuscleGroup`, contrato) tem 8 baldes e o modelo de
volume do motor usa 11 (`TargetMuscle`). O casamento virou heurística para coser essa diferença, e
é por isso que ele é ambíguo.

## Três opções levantadas

| | descrição | custo | resolve a ambiguidade? |
|---|---|---|---|
| **A** | Estender `MuscleGroup` com QUADS/POSTERIOR/CALVES/BICEPS/TRICEPS/FOREARMS e reseed dos exercícios curados | migration + ~60 arquivos que leem `MuscleGroup` (quiz, biblioteca, progresso, `focus_areas`, catálogo de strings) | Parcial — `primary_muscles` é array; `[LEGS, GLUTES]` continua casando em dois alvos |
| **B** | Manter os 8 baldes, tornar `matches()` determinístico (padrão decide o composto, etiqueta só decide o isolamento) | mais barato, nenhuma migration | Não tira a ambiguidade de fundo, só ordena quem ganha |
| **C** (escolhida) | `exercises.volume_target VARCHAR(16)`, um valor por exercício, só do servidor, `CHECK` no vocabulário de `TargetMuscle` | segundo campo de músculo ao lado de `primary_muscles`, com papel distinto | Sim — "exatamente um alvo" vira propriedade do schema, não da ordem dos `if` |

## [REGRA] Decisão: opção C

Casamento passa a ser `exercise.volumeTarget == alvo`. A tabela canônica para validação por CREF **é
a própria coluna**, exportada em `tools/motor_sim/alvo_canonico.csv` — não um documento separado
para manter sincronizado à mão.

Contrato e cliente intocados: `volume_target` é interno ao motor, não é `MuscleGroup` nem aparece
em nenhum DTO.

`primary_muscles` continua existindo, com papel diferente — catálogo, UI, bônus de foco
(`focusAreas`). **`[REGRA]` separando os papéis: `primary_muscles` é vocabulário de produto;
`volume_target` é contabilidade do motor.** Um não substitui o outro, e não há plano de fundir os
dois.

## Estado: decidido, não implementado

A V64 populou a coluna para os 147 exercícios da lista principal. O `SlotFiller` do motor Kotlin
**continua usando o critério antigo** (`movement_pattern`/categoria) — esta fatia não tocou o motor
Kotlin, de propósito (nota de abertura do plano: *"Esta fatia não mexe no motor Kotlin. Ela
conserta o catálogo e devolve a capacidade de medir."*). O simulador Python
(`tools/motor_sim/motor_v3.py`) já usa `volume_target` como oráculo.

Trocar o `SlotFiller` para ler a coluna é trabalho de uma fatia do motor v3 — até lá, o `[INV]`
`[VENCIDO]` em `regras/01-motor-de-geracao.md` sobre casamento por padrão continua sendo o que roda
de verdade. **Não implemente código novo contra o critério antigo.**

## Pendente

Validação profissional (CREF) da tabela — ver débito em `fontes/debitos.md`.
