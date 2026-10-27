# ARCH #21 — Array nativo para dado filtrado em SQL

**Fase:** 4 · **Status:** vigente

## Decisão
Taxonomia (primary_muscles/contraindications) é TEXT[] nativo (filtrado com && e GIN), não JSON. Regra: dado lido pela app → JSON; filtrado em SQL → array.

---
_Detalhe histórico: Painel Mestre v24 (PDF). Este ADR é a fonte a partir de 2026-07-26._
