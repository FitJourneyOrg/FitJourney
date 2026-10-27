# ARCH #29 — Split guiado na onboarding

**Fase:** 4 · **Status:** implementado (2026-07)

> ADR criado em 2026-07-28 (posterior ao Painel v24).

## Decisão
O usuário escolhe o **split** (Full Body, Upper/Lower, PPL, U/L+Full, U/L+PPL, Arnold) na onboarding, **livre**, mas com um **recomendado por número de dias** pré-selecionado. Fonte única no contrato: `SplitCatalog` (`byDays`: 2→FB★; 3→FB★/U-L+Full/PPL; 4→UL★/FB; 5→UL+PPL★; 6→PPL★/Arnold). `SplitType` carrega label/descrição. Dias por semana seguem em **2–6**.

- `SplitCatalog.resolve(days, escolhido)` → usa o escolhido se válido p/ os dias, senão cai no recomendado. É a autoridade também no motor.
- **Motor:** `StructureEngine.buildSkeleton(..., splitPreference)` resolve o split e monta o esqueleto (helper `cycle()` p/ sufixar A/B). Sem preferência = recomendado.
- **Persistência:** migration **V18** (`split_preference VARCHAR(24)` nullable). Fluxo Backend → Contrato → Cliente completo (DTO, model server/cliente, mappers, quiz).
- **Onboarding:** passo `SPLIT` após `ENVIRONMENT`. Iniciante (`BEGINNER`) ou saúde geral (`GENERAL_HEALTH`) **pulam o passo de FOCO** (`QuizState.visibleSteps`), espelhando a regra `effectiveFocus` do motor.

Provado em runtime (persiste e gera). Testes em `SplitSelectionTest` (server) e round-trip de mapper (cliente).

---
_Detalhe histórico: Painel Mestre v24 (PDF). Este ADR é a fonte a partir de 2026-07-28._
