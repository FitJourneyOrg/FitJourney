# <fatia> — plano

_Aberta em <data>. Rascunho: não é fonte da verdade (ver README desta pasta)._

## O que é e por que agora

<uma ou duas frases: o problema que a fatia resolve, e por que ela vem antes das outras>

## Perguntas que preciso responder antes de codar

- [ ] <pergunta cuja resposta muda o desenho — não "como faço X", mas "X ou Y, e por quê">

## Decisões tomadas

<uma linha por decisão, com o motivo. O motivo é o que vale: ele é o que vira [REGRA] ou ADR>

- **<decisão>** — <porquê>. Alternativa descartada: <qual, e o que ela custava>

## Desenho

### Servidor
### Contrato
### Cliente

> Ordem de implementação: Backend → Contrato → Cliente. Dependências apontam para o `Domain`.

## Migration

<número previsto, o que muda, e se apaga ou reescreve dado de usuário — se apagar, o número MEDIDO>

## Testes (C2)

| Tier | O que | Onde |
|---|---|---|
| 1 — lógica pura | | |
| 2 — serviço/ViewModel com fake | | |
| 3 — rota/repositório, Testcontainers | | |

Cada um inclui ao menos um caminho de falha.

## Fora de escopo, por decisão

<o que ficou de fora e por quê — isto vira item em debitos.md no fechamento, não se perde aqui>

## Ao fechar — promover antes de apagar

- [ ] `[REGRA]`/`[INV]` → `docs/regras/<área>.md`
- [ ] decisão de arquitetura → `docs/arch/`
- [ ] fora de escopo → `docs/fontes/debitos.md`
- [ ] narrativa e defeitos achados → `docs/historico/<fatia>.md`
- [ ] migration → `docs/migrations/INDICE.md`
- [ ] `ESTADO.md` atualizado, `gen_tudo.py` rodado
- [ ] **este arquivo apagado**
