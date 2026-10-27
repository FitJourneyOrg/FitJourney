# Gamificação e grupos (ARCH #16, #18, #32)

> Índice: `docs/regras/README.md` · Marcadores: [REGRA] imutável · [INV] invariante · [PROPOSTA] lacuna não ratificada.

## Gamificação e social (próximas fases)

- **[REGRA]** Gamificação (#16): XP, Nível e Conquistas são do perfil individual; a cor `lime` é exclusiva dessas recompensas. Derivados das sessões.
- **[REGRA]** Conquista CONCEDIDA nunca é retirada (#32). A regra que decide é derivada e pura, mas o desbloqueio é PERSISTIDO com data: se um limiar mudar, a medalha de quem já a viu não pode desaparecer. Consequência: em conquista desbloqueada o progresso atual pode estar ABAIXO do alvo (streak quebra, medalha fica) — a UI lê a data de desbloqueio, nunca compara progresso com alvo.
- **[REGRA]** Grupos (#18): baseados em contagem de check-ins (não XP); recompensam badges de grupo. Ranking global antecipa o Redis.
