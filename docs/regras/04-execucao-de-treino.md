# Execução de treino (Fase 5)

> Índice: `docs/regras/README.md` · Marcadores: [REGRA] imutável · [INV] invariante · [PROPOSTA] lacuna não ratificada.

## Execução de treino (Fase 5)

- Sessão = snapshot auto-contido de um treino executado (sobrevive à exclusão do template).
- **[INV]** Idempotência: `POST /sessions` usa o id gerado no cliente (ON CONFLICT DO NOTHING) → reenvio não duplica.
- Offline-first: `record()` enfileira na outbox local e sincroniza; `flush()` reenvia pendentes no boot. startedAt/finishedAt do relógio do cliente; created_at do servidor.
