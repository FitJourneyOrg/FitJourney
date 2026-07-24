# Exercícios-base por grupo muscular — tabela de prioridade (PROPOSTA)

> **Status:** `[PROPOSTA]` — curadoria a ratificar. Alimenta o ARCH #27 (motor de volume).
> **Objetivo:** os exercícios mais conhecidos/usados na academia, por grupo muscular. São a **base fundamental para todos os níveis** e recebem **peso de prioridade** na seleção do `SlotFiller` — o motor os escolhe primeiro, antes de variações obscuras.

---

## Modelo de demanda técnica (1–5) e o gate por teto

Cada exercício tem uma **demanda técnica** de 1 a 5 (habilidade/coordenação exigida pra executar com segurança; um 5 também tende a ser mais fatigante sistemicamente):

| Nota | Significado | Exemplos |
|---|---|---|
| **1** | Trivial — máquina guiada / bodyweight simples | Leg press, chest press, cadeira extensora, prancha |
| **2** | Baixa — halteres/cabos em padrão simples | Rosca halter, crucifixo, elevação lateral, remada máquina |
| **3** | Média — barra estável, composto de halter | Supino reto, desenvolvimento, remada curvada, stiff |
| **4** | Alta — muita coordenação/estabilização | Agachamento livre, afundo com barra |
| **5** | Muito alta — técnica exigente / risco se malfeito | Levantamento terra, barra fixa controlada |

### O gate: teto efetivo = `min(teto_nível, teto_idade)`

Dois eixos, cada um dá um teto; o exercício entra no pool se `demanda ≤ min(dos dois)`. **Elegibilidade cumulativa** (quem tem teto 4 usa também os de 1, 2, 3 — máquina não é "coisa de iniciante"), **mas a idade prevalece** e pode derrubar o teto mesmo de um experiente.

| Nível | Teto | | Idade | Teto de segurança |
|---|---|---|---|---|
| Iniciante | 2 | | Jovem (18–39) | 5 (sem cap) |
| Intermediário | 4 | | Adulto (40–59) | 4 |
| Avançado | 5 | | Idoso (60+) | 2 |

Exemplos do `min()`:
- Avançado jovem → `min(5,5)=5` → tudo.
- **Avançado idoso → `min(5,2)=2`** → só máquina/baixa demanda. A idade **não libera** os de alta demanda que a experiência permitiria.
- Iniciante jovem → `min(2,5)=2`.
- Intermediário adulto → `min(4,4)=4`.

### Seleção ≠ elegibilidade
Estar no pool não é ser escolhido. A demanda só desempata *qual variante vira a âncora* (o slot composto pesado prefere a de maior demanda elegível — ex.: agachamento livre pro avançado jovem). Os de demanda baixa seguem no pool como acessório/isolamento pra todos (extensora, flexora, crucifixo). O `Papel` casa com a tabela 3.3 do ARCH #27 (define reps/descanso/RIR).

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

1. Colunas novas na taxonomia (migration): `is_base BOOLEAN` (marca os campeões) + `technical_demand SMALLINT` (1–5).
2. Script de curadoria que casa esta lista com os `id` reais do catálogo (fuzzy match dos nomes traduzidos + revisão humana), corrigindo tag errada quando achar. Liga ao débito do Handoff (catálogo mal tagueado).
3. `SlotFiller`: pré-filtro por `demanda ≤ min(teto_nível, teto_idade)`; e bônus no `score()` quando `is_base = true` (gateado pela demanda vs teto efetivo).

---

## Checklist de ratificação

- [ ] Aprovar a lista de campeões por grupo (adicionar/remover).
- [ ] Aprovar a escala de demanda 1–5 e as notas por exercício.
- [ ] Aprovar os tetos por nível e por idade + a regra `min()` (idade prevalece).
- [ ] Aprovar o mecanismo (`is_base` + `technical_demand` + pré-filtro/bônus no score).
- [ ] Priorizar quando o mapeamento pro catálogo entra (agora vs junto da Fase 1 do ARCH #27).

*Base fundamental para todos os níveis; demanda técnica gateia por `min(nível, idade)`. Princípio geral de treino, não prescrição individual.*
