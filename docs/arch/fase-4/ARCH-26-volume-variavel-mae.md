# ARCH #26 — Parametrização do motor: volume é a variável-mãe

**Fase:** 4 · **Status:** vigente ([PROPOSTA] até validar em uso)

## Decisão
Motor monta por goal×level×daysPerWeek; volume semanal por músculo é a variável-mãe (não a frequência). Tabelas de volume/séries/reps/descanso/split; split deriva dos dias (~2×/músculo).

### Enquadramento conceitual (a bússola do motor)
O **valor-mãe conceitual** é a **capacidade de recuperação** — o orçamento de estresse que o indivíduo transforma em adaptação por semana. Todo o resto (frequência, intensidade, complexidade) é alocação dentro desse orçamento. Ele é **operacionalizado** como volume semanal por músculo (séries efetivas, entre MEV e MRV) — a única variável com dose-resposta comprovada, computável, e que as outras só modulam. Por isso o motor tem um número central: séries/semana por músculo.

Corolário que rege os eixos do motor: o eixo primário é a **idade de treino** (meses/anos de treino consistente), não a idade cronológica — um iniciante de 22 e um de 55 estão mais próximos entre si do que o de 22 está de um avançado de 22. No nosso modelo, **`Level` (BEGINNER/INTERMEDIATE/ADVANCED) É o proxy de idade de treino e define a estrutura**; a **idade cronológica entra como modulador** (ARCH #24: calibra volume + RIR, não é eixo). [REGRA] Idade cronológica nunca vira eixo primário do motor.

**Refinos de 2026-07 (implementação, não decisão nova):** foco protegido (músculo de foco entra em todo dia do Full Body); dedup por movementPattern na sessão; isolamento fixado em 12-15 reps; tetos de 6 exercícios e 20 séries por sessão; landmarks de volume estilo RP. `kotlinx.datetime.Clock` migrado p/ `kotlin.time.Clock`.

---
_Detalhe histórico: Painel Mestre v24 (PDF). Este ADR é a fonte a partir de 2026-07-26._

---

## Apêndice — parâmetros ratificados (movido de `docs/propostas/ARCH-26-volume-PROPOSTA.md` em 2026-10-07)

### Fatia A — IMPLEMENTADA (`StructureEngine.kt`, `SlotFiller.kt`, `WorkoutExerciseDto.rir`)

**Volume — séries efetivas/semana, por músculo e nível** (perna fina via `movement_pattern`; ARMS combinado, split fica pra Fatia B):

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

**Reps / descanso / RIR por papel do exercício:**

| Papel | Reps | Descanso | RIR |
|---|---|---|---|
| Composto pesado (âncora) | 5–8 | 2–3 min | 2–3 |
| Composto acessório | 8–12 | 90–120 s | 1–2 |
| Isolamento | 12–20 | 60–90 s | 0–2 |

Modificadores: iniciante trava RIR 2–3 (cue "deixe 2 no tanque", nunca falha); GENERAL_HEALTH no volume-piso, reps 8–15, sem falha; idoso RIR-piso 3–4.

**Piso de 3 séries por exercício** (convenção fixa — volume baixo se resolve com menos exercícios/frequência, nunca abaixo de 3 séries). **Distribuição:** frequência 2x/semana por músculo grande (fixa); teto ~6–8 séries/músculo/sessão antes de junk volume; teto de 20 séries totais/sessão; ordem composto pesado → acessório → isolamento.

### Fatia B — decisão RATIFICADA, implementação PENDENTE (curadoria do catálogo)

**Escala de demanda técnica (1–5)** e o gate `teto_efetivo = min(teto_nível, teto_idade)`:

| Nota | Significado | Exemplos |
|---|---|---|
| 1 | Trivial (máquina guiada / bodyweight simples) | Leg press, chest press, extensora, prancha |
| 2 | Baixa (halter/cabo simples) | Rosca halter, crucifixo, elevação lateral |
| 3 | Média (barra estável, composto de halter) | Supino reto, desenvolvimento, stiff |
| 4 | Alta (coordenação/estabilização) | Agachamento livre, afundo com barra |
| 5 | Muito alta (técnica exigente/risco) | Levantamento terra, barra fixa controlada |

| Nível | Teto | | Idade | Teto |
|---|---|---|---|---|
| Iniciante | 2 | | Jovem (18–39) | 5 (sem cap) |
| Intermediário | 4 | | Adulto (40–59) | 4 |
| Avançado | 5 | | Idoso (60+) | 2 |

Elegibilidade cumulativa (teto 4 usa 1,2,3,4); **a idade prevalece** quando os eixos conflitam (avançado idoso = `min(5,2)=2`, sem liberar os de alta demanda que a experiência permitiria) — a idade também rebaixa o volume ao MEV e força RIR-piso 3–4. Seleção ≠ elegibilidade: a demanda só desempata qual variante vira a âncora; os de baixa demanda seguem no pool como acessório/isolamento.

**Pendente pra operacionalizar** (sem isso, a tabela acima é só papel): coluna `technical_demand SMALLINT` na taxonomia; script de curadoria mapeando os campeões aos ids reais do catálogo (ver `docs/propostas/exercicios-base-por-grupo-PROPOSTA.md`); pré-filtro no `SlotFiller` por `demanda ≤ teto_efetivo`. **Nota:** a priorização "campeão vence variação" já existe hoje por outro caminho — `is_base` (coluna V16) + `SlotFiller.kt` (`pooled.filter { it.isBase }`), ratificada pela ARCH #28 — mas sem a escala de demanda nem o gate por idade.

*Fontes: RP Strength (volume landmarks), Fitbod, Dr. Muscle/JuggernautAI. Princípio geral de treino, não prescrição individual; PAR-Q segue como gate de segurança.*

