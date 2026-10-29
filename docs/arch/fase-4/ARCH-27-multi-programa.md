# ARCH #27 — Multi-programa

**Fase:** 4 · **Status:** implementado (2026-07) · revisa #22

> ADR criado em 2026-07-26 (posterior ao Painel v24).

## Decisão
Programa ganha NOME (editável), usuário tem N programas, treino manual vive DENTRO de um programa; acaba o modelo '1 ativo que substitui' do #22 (que fica superseded nessa parte). Tetos por plano (revisto em 2026-10-08, B8/limites): grátis 1 programa no total e premium 3, de qualquer origem, números em `Limites.Program` (shared-contract); antes eram grátis 1 IA + 2 manuais contados separadamente e premium 10. Programas acima do teto, de antes da mudança, são mantidos (só bloqueiam criar). O que fazer com o excedente quando o premium expira segue em aberto (débito). A decisão do teto é uma FUNÇÃO PURA testada: ProgramLimits.gate(counts, isPremium). Migrations V12 (name/origin) e V13 (rir).

---
_Detalhe histórico: Painel Mestre v24 (PDF). Este ADR é a fonte a partir de 2026-07-26._
