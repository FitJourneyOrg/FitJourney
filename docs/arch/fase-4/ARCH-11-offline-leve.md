# ARCH #11 — Offline leve no cliente

**Fase:** 4 · **Status:** ⛔ SUBSTITUÍDO pelo [ARCH #30 — Offline-first](../fase-5/ARCH-30-offline-first.md) (2026-08-13)

## Decisão (histórica)
Cache de leitura local (catálogo+treinos); escrita online. Sync adiado pós-MVP. NÃO é offline-first.

## Por que foi substituído
A Documentação Completa já prometia "offline-first" (divergência registrada como débito de DOC), e
o uso real cobrou: treinar em academia com sinal ruim é o caso comum, não a exceção. O #30 muda o
alvo — banco local como fonte da verdade da UI, outbox para escrita e WorkManager para sincronizar.

O que este ADR acertou e o #30 preserva: **a escrita não é livre**. Continua valendo a autoridade
do servidor (XP, validação, geração). Offline-first mudou o *como* ler e enfileirar, não quem
decide.

---
_Detalhe histórico: Painel Mestre v24 (PDF). Este ADR é a fonte a partir de 2026-07-26._
