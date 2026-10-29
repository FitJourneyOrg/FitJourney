# Agenda e cronograma (ARCH #22)

> Índice: `docs/regras/README.md` · Marcadores: [REGRA] imutável · [INV] invariante · [PROPOSTA] lacuna não ratificada.

## Agenda e cronograma

- **[REGRA]** Descanso é implícito: dia sem treino. Não há entidade de descanso.
- Agenda (modo fixo): `day_of_week` (1=Seg..7=Dom) é a fonte da verdade. Sem dias off, o WeekSpread usa um calendário-âncora curado (ex.: 3d → Seg/Qua/Sex; Full Body no 4-dias nunca em dias consecutivos); com unavailableDays, cai no espaçamento uniforme.
- Cronograma (#22 estendido): o programa tem `duration_weeks` (mín 8 = 2 meses) e `started_at`; a semana atual (currentWeek) é derivada no servidor = `min(duração, ⌊(hoje − início)/7⌋ + 1)`. Ao concluir a janela, o cliente oferece gerar novo (IA ou manual). Ciclo rolante: [PROPOSTA] (não implementado).
