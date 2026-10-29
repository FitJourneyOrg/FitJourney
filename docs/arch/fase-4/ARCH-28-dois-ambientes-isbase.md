# ARCH #28 — Dois ambientes + is_base + refino do catálogo

**Fase:** 4 · **Status:** implementado (2026-07)

> ADR criado em 2026-07-26 (posterior ao Painel v24).

## Decisão
TrainingEnvironment reduzido a ACADEMIA | CASA (Casa = halter+corporal+elástico). Coluna is_base: o motor escolhe exercício BASE primeiro; variação só em fallback (base-first no SlotFiller). Exóticos (CROSSFIT/olímpicos/calistenia extrema) escondidos do motor via modality=NULL (seguem no catálogo p/ treino manual). Ambiente do exercício DERIVADO do equipment (EquipmentEnvironmentMap), não coluna. Migrations V14–V17 (curadoria de nomes, dedup, is_base+inserts, remap de ambiente).

---
_Detalhe histórico: Painel Mestre v24 (PDF). Este ADR é a fonte a partir de 2026-07-26._
