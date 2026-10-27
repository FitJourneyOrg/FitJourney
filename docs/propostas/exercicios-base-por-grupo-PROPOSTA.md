# Exercícios-base por grupo muscular — tabela de prioridade (PROPOSTA)

> **Status:** `[PROPOSTA]` — curadoria a ratificar. Alimenta a Fatia B do ARCH #26 (motor de volume).
> **Objetivo:** os exercícios mais conhecidos/usados na academia, por grupo muscular — a lista de
> "campeões" que a Fatia B precisa mapear aos ids reais do catálogo.
>
> **Nota (2026-10-07):** a priorização "campeão vence variação" em si **já está implementada**,
> só que por outro caminho — `is_base` (coluna V16) + `SlotFiller.kt` (`pooled.filter { it.isBase }`),
> ratificada pela **ARCH #28**, não por este documento. O que falta é só a escala de
> `technical_demand` (demanda 1–5) e o gate `min(nível, idade)`, que ratificaram no apêndice do
> ARCH #26, mas que o `SlotFiller` ainda não aplica. A escala de demanda e o gate por teto estão
> descritos lá, não repetidos aqui — o que resta neste doc é a lista de campeões abaixo,
> genuinamente não mapeada ainda.

---

## CHEST (peito)

| Exercício | Padrão | Equipamento | Composto | Papel | Demanda |
|---|---|---|---|---|---|
| **Supino reto** (campeão) | HORIZONTAL_PUSH | BARBELL | sim | composto pesado | 3 |
| Supino máquina / Chest press | HORIZONTAL_PUSH | MACHINE | sim | composto pesado | 1 |
| Supino inclinado (halter/barra) | HORIZONTAL_PUSH | DUMBBELL/BARBELL | sim | composto acessório | 3 |
| Flexão de braço | HORIZONTAL_PUSH | BODYWEIGHT | sim | composto acessório | 2 |
| Crucifixo / Peck deck | HORIZONTAL_PUSH | MACHINE/DUMBBELL | não | isolamento | 2 |
| Crossover na polia | HORIZONTAL_PUSH | CABLE | não | isolamento | 2 |

## BACK (costas)

| Exercício | Padrão | Equipamento | Composto | Papel | Demanda |
|---|---|---|---|---|---|
| **Puxada frontal** (campeão iniciante) | VERTICAL_PULL | MACHINE/CABLE | sim | composto pesado | 1 |
| Barra fixa | VERTICAL_PULL | BODYWEIGHT | sim | composto pesado | 4 |
| Remada curvada | HORIZONTAL_PULL | BARBELL | sim | composto pesado | 3 |
| Remada sentada / máquina | HORIZONTAL_PULL | MACHINE/CABLE | sim | composto acessório | 1 |
| Remada unilateral (halter) | HORIZONTAL_PULL | DUMBBELL | sim | composto acessório | 2 |
| Levantamento terra | HINGE | BARBELL | sim | composto pesado | 5 |

## SHOULDERS (ombros)

| Exercício | Padrão | Equipamento | Composto | Papel | Demanda |
|---|---|---|---|---|---|
| **Desenvolvimento** (campeão) | VERTICAL_PUSH | BARBELL/DUMBBELL | sim | composto pesado | 3 |
| Desenvolvimento máquina | VERTICAL_PUSH | MACHINE | sim | composto pesado | 1 |
| Elevação lateral (deltoide lateral) | NONE | DUMBBELL/CABLE | não | isolamento | 2 |
| Elevação frontal | NONE | DUMBBELL | não | isolamento | 2 |
| Crucifixo inverso / Face pull (posterior) | HORIZONTAL_PULL | CABLE/MACHINE | não | isolamento | 2 |

## ARMS (braços)

| Exercício | Padrão | Equipamento | Composto | Papel | Demanda |
|---|---|---|---|---|---|
| **Rosca direta** (bíceps, campeão) | NONE | BARBELL | não | isolamento | 2 |
| Rosca alternada (halter) | NONE | DUMBBELL | não | isolamento | 2 |
| Rosca scott / máquina | NONE | MACHINE | não | isolamento | 1 |
| **Tríceps na polia** (campeão) | NONE | CABLE | não | isolamento | 1 |
| Tríceps testa | NONE | BARBELL/DUMBBELL | não | isolamento | 3 |
| Paralelas / mergulho (tríceps) | VERTICAL_PUSH | BODYWEIGHT | sim | composto acessório | 3 |
| Rosca martelo | NONE | DUMBBELL | não | isolamento | 2 |

## LEGS (pernas) — cobertura reforçada

| Exercício | Padrão | Equipamento | Composto | Papel | Demanda |
|---|---|---|---|---|---|
| **Agachamento livre** (campeão) | SQUAT | BARBELL | sim | composto pesado | 4 |
| **Leg press** (campeão iniciante) | SQUAT | MACHINE | sim | composto pesado | 1 |
| Hack squat / agach. máquina | SQUAT | MACHINE | sim | composto pesado | 2 |
| Cadeira extensora (quadríceps) | KNEE_EXTENSION | MACHINE | não | isolamento | 1 |
| Mesa/cadeira flexora (posterior) | KNEE_FLEXION | MACHINE | não | isolamento | 1 |
| Stiff / Terra romeno (posterior) | HINGE | BARBELL/DUMBBELL | sim | composto acessório | 3 |
| Afundo / Passada | LUNGE | DUMBBELL/BODYWEIGHT | sim | composto acessório | 3 |
| Panturrilha em pé / máquina | NONE | MACHINE | não | isolamento | 1 |

## GLUTES (glúteos)

| Exercício | Padrão | Equipamento | Composto | Papel | Demanda |
|---|---|---|---|---|---|
| **Elevação pélvica / Hip thrust** (campeão) | HINGE | BARBELL | sim | composto pesado | 3 |
| Terra romeno / Stiff | HINGE | BARBELL | sim | composto acessório | 3 |
| Cadeira abdutora | NONE | MACHINE | não | isolamento | 1 |
| Coice na polia (glute kickback) | NONE | CABLE | não | isolamento | 2 |
| Agachamento (transferência de SQUAT) | SQUAT | — | sim | composto pesado | 4 |

## CORE

| Exercício | Padrão | Equipamento | Composto | Papel | Demanda |
|---|---|---|---|---|---|
| **Prancha** (campeão, TIME) | NONE | BODYWEIGHT | não | isolamento | 1 |
| Abdominal / crunch | NONE | BODYWEIGHT | não | isolamento | 1 |
| Elevação de pernas | NONE | BODYWEIGHT | não | isolamento | 2 |
| Abdominal na polia | NONE | CABLE | não | isolamento | 2 |
| Rotação / Russian twist | ROTATION | BODYWEIGHT/MEDICINE_BALL | não | isolamento | 2 |

---

## Passo de implementação (débito a resolver)

Esta lista é **conceitual** (nomes canônicos). Pra virar prioridade real no motor, falta:

1. ~~Coluna `is_base BOOLEAN`~~ — já existe (migration V16, ARCH #28), já usada pelo `SlotFiller`. Falta só `technical_demand SMALLINT` (1–5).
2. Script de curadoria que casa esta lista com os `id` reais do catálogo (fuzzy match dos nomes traduzidos + revisão humana), corrigindo tag errada quando achar. Liga ao débito do Handoff (catálogo mal tagueado).
3. `SlotFiller`: pré-filtro por `demanda ≤ min(teto_nível, teto_idade)` — o bônus por `is_base` já existe; falta só o filtro por demanda.

---

## Checklist de ratificação

- [ ] Aprovar a lista de campeões por grupo (adicionar/remover).
- [x] Escala de demanda 1–5 e tetos por nível/idade — ratificados (ver apêndice ARCH #26).
- [ ] Script de curadoria: mapear esta lista aos ids reais do catálogo.
- [ ] `SlotFiller`: pré-filtro por `technical_demand` (o bônus por `is_base` já existe).
- [ ] Priorizar quando o mapeamento pro catálogo entra (agora vs. depois do MVP).

*Base fundamental para todos os níveis; demanda técnica gateia por `min(nível, idade)`. Princípio geral de treino, não prescrição individual.*
