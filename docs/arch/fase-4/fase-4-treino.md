# Fase 4 · M3 — Treino (núcleo) · **CONCLUÍDA (2026-08-04)**

> Fonte: Painel Mestre v25 + Handoff Retomada 14, atualizado 2026-08-04. ARCHs: #26=volume; #27=multi-programa; #28=ambientes/is_base; #29=split guiado.

## Fatias concluídas
- **A** Catálogo; **B** CRUD treino; **C.1/2/3** cliente (biblioteca, navegação, workout, detalhe, criar/editar).
- **D** Geração por IA stub-first + PAR-Q + entitlement (#17/#18/#19).
- **F.0/F.1** Curadoria (430) + taxonomia (V8/V9).
- **F.2/F.3/F.4** Motor determinístico (#26): StructureEngine + pré-filtro + SlotFiller.
- **G.1** Motor LIGADO (V11 + ProgramService + /programs/generate). Primeira geração real 200 OK.

## Depois da G.1 (2026-07)
- **#27 Multi-programa:** nome, N programas, sem substituição, tetos via `ProgramLimits`. V12/V13.
- **#26 (refinos):** foco protegido, dedup por padrão, isolamento 12-15, tetos por sessão.
- **#28 Dois ambientes + is_base:** ACADEMIA/CASA, base-first, esconder exóticos, catálogo refinado. V14–V17.
- **G.2 (cliente do programa) — parcial:** ProgramList/Detail/Generate + telas + paywall + swap/delete exercício.
- **Testes:** suíte de 96 (relatório de testes).

## Fechamento (2026-08-04)
Tudo que estava "a fazer" foi entregue. G.2 completa (revelação, blur, agendamento por dia real, edição premium). Motor **destravado (Fase 0)** e **desmembrado (Fatia B/B.2)**. **Cronograma de semanas (V24)** e **calendário-âncora**. **Idade (#24)** entregue: `age`/`minor_supervised`, gate no `/generate`, passo AGE no quiz. **Fase 4 = 100%.**

## Fora da Fase 4 (próximas)
- **Override de volume premium** → v2.0 (depende de ratificar landmarks MEV/MAV/MRV, `docs/proposals/`).
- **Fatia E / Fase 5** (execução ao vivo + XP/gamificação) — o **log de sessão** já foi entregue; falta timer, histórico/progresso e a gamificação (#16).
