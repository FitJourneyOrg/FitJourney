# Execução de treino (Fase 5)

> Índice: `docs/regras/README.md` · Marcadores: [REGRA] imutável · [INV] invariante · [PROPOSTA] lacuna não ratificada.

## Execução de treino (Fase 5)

- Sessão = snapshot auto-contido de um treino executado (sobrevive à exclusão do template).
- **[INV]** Idempotência: `POST /sessions` usa o id gerado no cliente (ON CONFLICT DO NOTHING) → reenvio não duplica.
- Offline-first: `record()` enfileira na outbox local e sincroniza; `flush()` reenvia pendentes no boot. startedAt/finishedAt do relógio do cliente; created_at do servidor.

## Sugestão de progressão na tela de execução (fatia "sugestao-de-progressao", 2026-11-06)

- **[REGRA]** `GET /workouts/{id}` preenche `cargaSugeridaKg`/`repsMinSugerido`/`repsMaxSugerido`
  em cada `WorkoutExerciseDto` — nunca os dois eixos juntos (o `incremento` do catálogo decide
  carga XOR reps). **Falha aqui NUNCA quebra o `GET`**: sem `Profile`/`Goal`, sem papel, sem
  catálogo, ou erro de leitura em qualquer repositório, o exercício fica com os três campos
  `null` — diferente de `comOrigem()`, que propaga falha. É enriquecimento, não dado essencial.
- **[REGRA]** Eixo de carga sem `Progressao.Estado` (calibração pendente) → sem sugestão
  (`null`). Eixo de repetição sem `ProgressaoPorRepeticao.Estado` (1ª exposição) → sugere a
  `PrescricaoV3.Faixa` ORIGINAL — não precisa esperar a 1ª sessão terminar, porque não existe
  calibração por reps (fatia "progressao-por-repeticao").
- Só o `GET /workouts/{id}` ganha o enriquecimento (é o único ponto que serve o DTO completo pra
  quem vai executar); `POST`/`PUT` não. O campo NUNCA volta do cliente pro servidor
  (`WorkoutMapper.toDto()`, nos dois lados, não escreve esses três campos de volta).
- Offline: a sugestão viaja no mesmo JSON serializado que o resto do `WorkoutExerciseDto`
  (`WorkoutLocalDataSource`) — sem migration de schema local, tão fresca quanto o último sync.
