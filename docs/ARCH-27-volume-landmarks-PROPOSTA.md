# ARCH #27 — Motor de volume por músculo + nível×idade (+ mesociclo)

> **Status:**
> - **Fatia A (motor de volume) — RATIFICADA**, pronta pra implementar.
> - **Fatia B (curadoria + gate de demanda)** — desenhada, pendente da curadoria do catálogo.
> - **Fatia C (mesociclo)** — pós-MVP.
> **Substitui/estende:** a parametrização do F.2 (`StructureEngine`). Preserva o ARCH #20 (determinístico, sem LLM).
> **Docs companheiros:** `exercicios-base-por-grupo-PROPOSTA.md` (prioridade + demanda), `ARCH-27-exemplos-plano.md` (2 exemplos gerados).

---

## 1. Diagnóstico: o motor conta a variável errada

Hoje o `StructureEngine` raciocina em **slots por padrão de movimento por dia**. Cada dia = 1 slot por padrão + isolamentos só dos grupos focados. Resultado: `legs`/`lower` têm só 2 padrões (SQUAT+HINGE) → saem com 2 exercícios. Dois problemas observados: (a) perna magra; (b) gerar de novo devolve treino idêntico (seed puro do perfil).

A ciência de hipertrofia (Schoenfeld; volume landmarks de Israetel/RP) mede **séries semanais efetivas por músculo** — não exercícios por dia. **Decisão central:** inverter o eixo — primeiro decidir quantas séries/semana cada músculo recebe, depois distribuir em dias/exercícios.

## 2. Por que RP (prescritivo), não Fitbod (reativo)

| Modelo | Precisa de histórico? | Cabe hoje? |
|---|---|---|
| **RP landmarks** (MEV/MAV/MRV) | Não | **Sim** — determinístico, ARCH #20 intacto |
| Fitbod (recuperação %) | Sim (logs) | Não — sem Fatia E |
| Dr. Muscle / Juggernaut (RPE) | Sim (feedback) | Não — sem Fatia E |

Autorregulação reativa fica para a **Fatia E** (quando existir o loop de registro de séries).

---

## 3. Parâmetros RATIFICADOS (Fatia A)

### 3.1 Volume — séries efetivas/semana, por músculo e nível

Perna **fina**, derivada de dado confiável (`movement_pattern`): quadríceps ← SQUAT/LUNGE/KNEE_EXTENSION; posterior ← HINGE/KNEE_FLEXION; glúteo ← HINGE + primary GLUTES. **ARMS fica combinado** (bíceps/tríceps não se separam por padrão de movimento — split fica pra Fatia B).

| Músculo (sinal usado) | Iniciante | Intermediário | Avançado | Teto (MRV) |
|---|---|---|---|---|
| CHEST (primary CHEST) | 8 | 14 | 18 | 22 |
| BACK (primary BACK) | 10 | 16 | 20 | 25 |
| SHOULDERS (primary SHOULDERS) | 6 | 12 | 16 | 22 |
| ARMS (primary ARMS — bi+tri) | 6 | 10 | 14 | 20 |
| Quadríceps (SQUAT/LUNGE/KNEE_EXT) | 6 | 12 | 16 | 20 |
| Posterior (HINGE/KNEE_FLEX) | 6 | 10 | 14 | 18 |
| Panturrilha (category CALVES) | 5 | 9 | 12 | 16 |
| GLUTES (primary GLUTES/HINGE) | 4 | 8 | 12 | 16 |
| CORE (primary CORE) | 5 | 8 | 10 | 16 |

Foco (INTER/ADV, exceto GENERAL_HEALTH): +2 a +4 séries/semana no(s) grupo(s) priorizado(s), respeitando o teto.
Caveat: panturrilha depende de `ExerciseCategory.CALVES` (coluna geralmente ruim, mas "panturrilha" é inequívoca → aceitável).

### 3.2 Reps / descanso / RIR — por PAPEL do exercício

| Papel | Reps | Descanso | RIR |
|---|---|---|---|
| Composto pesado (âncora) | 5–8 | 2–3 min | 2–3 |
| Composto acessório | 8–12 | 90–120 s | 1–2 |
| Isolamento | 12–20 | 60–90 s | 0–2 |

Modificadores: **iniciante** trava RIR 2–3 (mostrado como cue "deixe 2 no tanque", nunca falha) e reps na parte baixa; **GENERAL_HEALTH** no volume-piso, reps 8–15, sem falha; **idoso** RIR-piso 3–4 (camada de idade).
Muda o eixo atual (descanso era por nível 75/105/150s; reps fixo 8-12). RIR é campo NOVO no output.

### 3.3 Piso de 3 séries por exercício (convenção)

**Série-por-exercício é fixa: mínimo 3.** O volume semanal baixo (iniciante/idoso) é atingido com **menos exercícios e menos frequência**, nunca descendo abaixo de 3 séries. Ex.: um músculo com cota de 6 séries/semana = 1 exercício × 3 séries em 2 dias — não "3 exercícios × 2 séries". Casa a convenção de mercado (3×10) com o modelo de volume.

### 3.4 Distribuição

- Frequência **2x/semana** por músculo grande (fixa). Panturrilha e core toleram 2–3x (baixa fadiga).
- Teto de ~6–8 séries/músculo por sessão antes de junk volume; excedente vai pra 2ª sessão.
- Ordem: composto pesado → acessório → isolamento (`orderIndex`).

---

## 4. Camada Nível × Idade (gate de demanda)

Nível define a **ambição** (complexidade + volume); idade aplica **limites de segurança**. Pipeline de 2 passadas: nível monta o esqueleto → idade aplica os tetos.

### 4.1 Escala de demanda técnica (1–5) por exercício

| Nota | Significado | Exemplos |
|---|---|---|
| 1 | Trivial (máquina guiada / bodyweight simples) | Leg press, chest press, extensora, prancha |
| 2 | Baixa (halter/cabo simples) | Rosca halter, crucifixo, elevação lateral, remada máquina |
| 3 | Média (barra estável, composto de halter) | Supino reto, desenvolvimento, remada curvada, stiff |
| 4 | Alta (coordenação/estabilização) | Agachamento livre, afundo com barra |
| 5 | Muito alta (técnica exigente/risco) | Levantamento terra, barra fixa controlada |

### 4.2 O gate: teto efetivo = `min(teto_nível, teto_idade)`

| Nível | Teto | | Idade | Teto |
|---|---|---|---|---|
| Iniciante | 2 | | Jovem (18–39) | 5 (sem cap) |
| Intermediário | 4 | | Adulto (40–59) | 4 |
| Avançado | 5 | | Idoso (60+) | 2 |

Exercício entra no pool se `demanda ≤ min(dos dois)`. Elegibilidade **cumulativa** (teto 4 usa 1,2,3,4 — máquina não é "coisa de iniciante").

### 4.3 A idade prevalece

Quando os eixos conflitam, **a idade vence**. Avançado idoso = `min(5,2)=2` → só máquina/baixa demanda, **sem** liberar os de alta demanda que a experiência permitiria. A idade também **rebaixa o volume ao MEV** e força RIR-piso 3–4, mesmo para experiente. É a regra ratificada.

**Seleção ≠ elegibilidade:** estar no pool não é ser escolhido. A demanda só desempata *qual variante vira a âncora* (composto pesado prefere a de maior demanda elegível); os de baixa demanda seguem no pool como acessório/isolamento.

**Presentation gating por nível:** iniciante não vê RIR-número (vê cue) nem mesociclo nem jargão; intermediário vê RIR + semanas; avançado vê tudo (landmarks). A idade é dado que o quiz já coleta (idade, PAR-Q ≥69 = gate médico da ARCH #24).

---

## 5. Exercícios-base (prioridade)

Os "campeões" por grupo (ver doc companheiro) recebem **bônus de prioridade** (`is_base`) no `score()` do `SlotFiller` → escolhidos primeiro, gateados pela demanda vs teto efetivo. Precisa de curadoria do catálogo (mapear os campeões nos nomes traduzidos + preencher `technical_demand`).

---

## 6. Fatiamento (rollout)

**Fatia A — motor de volume (RATIFICADA, próxima a implementar).**
Volume-por-músculo (perna fina via `movement_pattern`) + reps/descanso/RIR por papel + piso de 3 séries. Segurança do idoso via **viés de equipamento** (MACHINE, dado que já existe) + volume-MEV + RIR-piso. Toca `StructureEngine` + campo `rir` no contrato. **Sem curadoria nova, sem schema pesado.** Conserta a perna magra.

**Fatia B — curadoria + gate de demanda fino.**
Colunas novas na taxonomia (`technical_demand` 1–5, `is_base`) + script de curadoria (mapear campeões, corrigir tag errada) + pré-filtro `demanda ≤ min(teto_nível, teto_idade)` + bônus de prioridade. Depende da curadoria (nomes traduzidos ruins, taxonomia suja — débito do Handoff).

**Fatia C — mesociclo (pós-MVP).**
Bloco de **4 semanas** (3 progressão MEV→MAV + 1 deload). Cada semana difere → mata o "regenerar idêntico" sem tocar o seed. Toca contrato (`weekIndex`) + schema (migration V13). Resolve o determinismo e vira programa de verdade.

---

## 7. Impacto por camada (Fatia A)

| Camada | Mudança |
|---|---|
| `StructureEngine` (F.2) | reescrito: pattern-slots → alocação de séries/músculo (perna fina) → distribuição em dias com piso de 3 séries |
| `SlotFiller` (F.4) | preencher a cota de cada músculo; viés de equipamento pro idoso |
| Contrato | `WorkoutExerciseDto`/`WorkoutSetDto`: campo `rir` |
| Schema | nenhum na Fatia A (schema só na Fatia C) |
| Cliente | mostrar RIR/cue conforme nível |

## 8. Fora de escopo (Fatia E)

Autorregulação reativa (RPE/performance real ajustando volume; recuperação % do Fitbod) — exige o loop de registro de séries, que só existe com a sessão ao vivo (Fatia E). O mesociclo prescritivo é a base sobre a qual isso pluga depois.

---

## 9. Status de ratificação

- [x] Volume por músculo com perna fina (3.1).
- [x] Reps/descanso/RIR por papel (3.2) + piso de 3 séries (3.3).
- [x] Frequência 2x fixa; salto iniciante→intermediário mantido.
- [x] Escala de demanda 1–5 + tetos + regra `min()` (idade prevalece).
- [x] Rollout: Fatia A primeiro; mesociclo pós-MVP.
- [ ] Fatia B: aprovar `technical_demand`/`is_base` + quando a curadoria entra.
- [ ] Ao mergear cada fatia: atualizar Painel Mestre + Handoff + Especificação (fechamento de ciclo).

*Fontes: RP Strength (volume landmarks), Fitbod, Dr. Muscle/JuggernautAI. Princípio geral de treino, não prescrição individual; PAR-Q segue como gate de segurança.*
