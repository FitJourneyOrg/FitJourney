# Regras gerais

> Índice: `docs/regras/README.md` · Marcadores: [REGRA] imutável · [INV] invariante · [PROPOSTA] lacuna não ratificada.

- **[REGRA]** Autoridade do servidor: XP, validação, IA, entitlement e gates decidem no backend (Ktor). O cliente tem só cópia otimista e nunca usa o próprio relógio para lógica. Firebase autentica; o backend autoriza.
- **[REGRA]** Fluxo: Backend → Contrato → Cliente (Data → Domain → Presentation). Feature nunca depende de feature; domain é Kotlin puro.
