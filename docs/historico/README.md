# Histórico — fatias fechadas

> **Nada aqui é de leitura obrigatória.** Esta pasta existe para não apagar o "porquê" de decisões
> antigas, não para ser lida no início da sessão. Abra um arquivo quando for **mexer na área dele**.
>
> Estado atual → `../ESTADO.md` · convenções → `../CONVENCOES.md` · regra de comportamento →
> `../regras/` · decisão → `../arch/` · débito → `../fontes/debitos.md` · migration →
> `../migrations/INDICE.md`.

Criada em **2026-10-07**. Antes disso o histórico morava dentro do `handoff-retomada.md`
(1983 linhas) e do `painel-mestre.md` (466 linhas), os dois de leitura obrigatória e com a mesma
função — log cronológico por fatia, com as mesmas fatias escritas duas vezes.

| Arquivo | Período | Conteúdo |
|---|---|---|
| `fatia-j-progresso.md` | 2026-10-01 a 10-04 | J.1 a J.5: desmembramento do Progresso, análise de progressão, filtro por programa, janela de calendário, detalhe por exercício. Mais a tela de Dúvidas Frequentes |
| `v59-v60-ativacao.md` | 2026-09-25 a 09-28 | Treino ativo (V59) e a reversão para programa ativo (V60), com o fix de Home no meio |
| `rationale-derivado.md` | 2026-09-22 | V56: a frase sai do banco, o cliente monta no idioma da tela |
| `debitos-2026-09-22.md` | 2026-09-22 | P0.1–P0.4 + 2 do P1 |
| `fatia-h-catalogo.md` | 2026-09-15 a 09-21 | H.1, H.2, sessão A e sessão B. V50–V55, catálogo 1:1 em 923 |
| `ui-insets-movimento.md` | 2026-09-15 | Insets, `Movimento.kt` e o `Scaffold` da biblioteca |
| `fatia-g-idioma.md` | 2026-09-10 a 09-15 | G.1 a G.5: extração dos literais, o inglês, tela de idioma, conquistas |
| `fase-6-e-integracao.md` | 2026-09-09 | Fechamento da Fase 6 e o débito de integração V40–V46 pago |
| `anterior-handoff-14.md` | até 2026-08-15 | ARCH #23, #30, #31, #32; outbox; onboarding; Home testável |
| `handoff-32-snapshot.md` | — | O `handoff-retomada.md` integral, antes do desmembramento |
| `painel-mestre-v42-snapshot.md` | — | O `painel-mestre.md` integral, antes do encerramento |

## Convenção

Fatia nova fechada **não** entra aqui de imediato: ela nasce no `ESTADO.md`, em "Agora". Quando a
fatia seguinte fechar e empurrar a anterior para fora do "Agora", aí ela ganha um arquivo nesta
pasta — é o momento em que ela deixou de ser estado e passou a ser história.

**Esta pasta não gera PDF**, de propósito: o git já é o arquivo, e PDF de log cronológico ninguém
abre. Os dois snapshots nunca se editam.
