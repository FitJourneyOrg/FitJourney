# ARCH #16 — core:catalog — lookup de exercício no core

**Fase:** 4 · **Status:** vigente

## Decisão
Módulo puro (ExerciseRef + ExerciseLookup.byIds); impl no core:database. Resolve o exerciseId-FK sem ferir feature→feature; mantém o VM em commonMain.

## Duplicação com `Exercise` é intencional (2026-10-08, B12)
`ExerciseRef` (4 campos) repete parte de `Exercise` (14) de propósito: subir o `Exercise` para o core alargaria a porta e acoplaria Session/Workout à taxonomia. O que protege a divergência é o `ParidadeExercicioRefTest` (feature exercise, `androidHostTest`), que monta os dois a partir da mesma linha SQLite. Reabrir se um 4º consumidor pedir campo novo.

---
_Detalhe histórico: Painel Mestre v24 (PDF). Este ADR é a fonte a partir de 2026-07-26._
