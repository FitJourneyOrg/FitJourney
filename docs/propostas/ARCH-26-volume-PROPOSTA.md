# ARCH #26 — Motor de volume: Fatia B (gate de demanda) e Fatia C (mesociclo) [PROPOSTA]

> **Fatia A (motor de volume por músculo, reps/descanso/RIR, piso de 3 séries) foi RATIFICADA e
> está implementada** (`StructureEngine.kt`, `SlotFiller.kt`, campo `rir` no contrato). Os
> parâmetros completos viraram apêndice de `docs/arch/fase-4/ARCH-26-volume-variavel-mae.md` em
> 2026-10-07 — não duplicados aqui. Este arquivo ficou só com o que ainda falta decidir ou construir.
>
> **Status:**
> - **Fatia B (curadoria + gate de demanda)** — decisão RATIFICADA (escala 1–5 + `min(nível,idade)`,
>   ver apêndice do ARCH #26), **implementação PENDENTE** (depende da curadoria do catálogo).
> - **Fatia C (mesociclo)** — pós-MVP, nem desenhada em detalhe.
> **Docs companheiros:** `exercicios-base-por-grupo-PROPOSTA.md` (lista de campeões por grupo,
> ainda não mapeada aos ids reais do catálogo).

---

## 1. Por que este arquivo ainda existe

A Fatia A resolveu a "perna magra" (motor contava exercícios/dia, não séries/semana por músculo).
Duas coisas continuam abertas:

1. **Fatia B** — o gate de demanda técnica × idade está ratificado em princípio, mas sem a coluna
   `technical_demand` na taxonomia e sem o script de curadoria que mapeia os "campeões" aos ids
   reais, é só papel — o `SlotFiller` não tem como aplicá-lo.
2. **Fatia C (mesociclo)** — hoje o motor é determinístico e **regenera o treino idêntico** (mesma
   seed + perfil = mesmo resultado). Sem blocos de semana que progridem (MEV→MAV→deload), não há
   "programa de verdade" no sentido de periodização — só uma foto fixa.

## 2. Fatia C — mesociclo (pós-MVP, rascunho)

Bloco de **4 semanas** (3 de progressão MEV→MAV + 1 deload). Cada semana difere → mata o
"regenerar idêntico" sem tocar o seed. Toca contrato (`weekIndex`) + schema (migration nova).
Resolve o determinismo e vira programa de verdade — mas é trabalho de schema + motor, não
cabe empurrar pro MVP.

## 3. Impacto por camada (Fatia B + C, quando entrarem)

| Camada | Fatia B | Fatia C |
|---|---|---|
| Taxonomia/migration | `technical_demand SMALLINT`, `is_base` já existe (ARCH #28) | `weekIndex` ou similar |
| Script de curadoria | mapear campeões aos ids reais + corrigir tag errada | — |
| `SlotFiller` | pré-filtro `demanda ≤ teto_efetivo`; bônus de prioridade | consumir semana do mesociclo |
| Contrato | — | `weekIndex` no `WorkoutDto` |
| Cliente | — | mostrar progressão da semana |

## 4. Fora de escopo (Fatia E)

Autorregulação reativa (RPE/performance real ajustando volume; recuperação % do Fitbod) — exige o
loop de registro de séries, que só existe com a sessão ao vivo (Fatia E). O mesociclo prescritivo
(Fatia C) é a base sobre a qual isso pluga depois.

## 5. Checklist de ratificação — só o que falta

- [ ] Fatia B: aprovar formalmente `technical_demand`/mecanismo de pré-filtro (a escala e o `min()`
  já estão ratificados no apêndice do ARCH #26; falta aprovar o *mecanismo* de implementação).
- [ ] Priorizar quando a curadoria do catálogo entra (agora vs. depois do MVP).
- [ ] Fatia C: desenhar o mesociclo em detalhe (hoje é só rascunho na seção 2).
- [ ] Ao mergear cada fatia: atualizar Painel Mestre + Handoff + Regras (fechamento de ciclo).

*Fontes: RP Strength (volume landmarks), Fitbod, Dr. Muscle/JuggernautAI. Princípio geral de
treino, não prescrição individual; PAR-Q segue como gate de segurança.*
