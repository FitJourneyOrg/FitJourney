# ARCH #24 — Idade: gate de segurança + variável do motor

**Fase:** 4 · **Status:** vigente (implementado — V27, motor)

> Revisão 2026-08-06: a decisão original ("idade é GATE, não variável do motor") foi **ampliada**.
> Idade continua sendo gate, **e** passou a calibrar o motor determinístico. Substitui o texto antigo
> (<18 bloqueava cadastro; ≥69 disparava PAR-Q) — nada disso vale mais; ver abaixo.

## Decisão

Idade (`ProfileDto.age: Int?`, nulável — opcional no quiz) tem **dois papéis**:

### 1. Gate de segurança (revalidado no `/programs/generate`)
- **[REGRA]** `<18`: geração exige aceite de responsável (`minorSupervised = true`). Sem aceite →
  `403 AGE_GATE_REQUIRED`. Não bloqueia o cadastro/perfil, só a geração de programa.
- **[REGRA]** `≥69`: **apenas informativo** (aviso no quiz). Não bloqueia, não dispara PAR-Q extra.
- **[INV]** `ageGateSatisfied(age, minorSupervised) = age == null || age >= 18 || minorSupervised`.
  Idade nula nunca bloqueia. Validação de faixa no perfil: `age in 5..120`.

### 2. Variável do motor (StructureEngine / ARCH #26)
A capacidade de recuperação cai com a idade → idade **calibra** o programa (não bloqueia). Faixas:

| Faixa | Banda | Volume-base | Modo |
|---|---|---|---|
| `<40` ou nula | YOUNG | ×1.0 | normal |
| `40–59` | ADULT | ×0.9 (−10%) | normal |
| `60+` | SENIOR | ×0.75 (−25%) | conservador (RIR-piso 3) |

- **(a) Modo conservador:** `60+` entra no mesmo mecanismo de `RoleParams.paramsFor` já usado por
  iniciante/GENERAL_HEALTH → RIR-piso 3 (treina mais longe da falha, cue "deixe no tanque").
- **(b) Fator de volume:** `ageVolumeFactor(age)` multiplica o volume-base de `VolumeTable.weeklySets`
  **antes** de somar o bônus de foco (a faixa reduz carga sistêmica sem apagar a prioridade escolhida).
- Threading: `DeterministicWorkoutGenerator.generate` → `StructureEngine.buildSkeleton(age)` →
  `VolumeTable`/`RoleParams`. `age` default `null` (YOUNG, sem penalidade) → retrocompatível.

## Trade-offs e limites conhecidos
- **[REGRA]** Os fatores (0.9 / 0.75) e os cortes de faixa (40 / 60) foram **ratificados pelo Rafael em 2026-10-08** (C3). Mudar exige decisão nova e ajuste do `StructureEngineTest`. Alinhado ao ARCH #26 (volume é a variável-mãe).
- No **ADVANCED**, os tetos por sessão (`SESSION_MAX_SETS=8`, total 20) saturam e absorvem quase toda
  a redução de volume (idoso ≈ jovem em séries). O **RIR conservador** é o lever de segurança dominante
  para idosos e vale em todos os níveis. A redução de volume morde de fato em INI/INTER.
- Descartado: gate `min(teto_nível, teto_idade)` por `technical_demand` (proposta dos exercícios-base) —
  conflita com "≥69 informativo" e depende de curadoria não feita. Fica fora do escopo.

---
_Detalhe histórico: Painel Mestre v24 (PDF) trazia "idade é gate, não variável". Ampliado em 2026-08-06._
