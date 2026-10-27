# Regras — índice

> Comportamento do sistema: máquinas de estado, invariantes e gates. **Tier 3** da hierarquia de
> verdade do projeto (abaixo de código-fonte e de Handoff/Painel Mestre; acima do Contrato da API
> e da Documentação Completa). Marcadores: **[REGRA]** imutável · **[INV]** invariante ·
> **[PROPOSTA]** lacuna não ratificada, não assumir como verdade.
>
> **Reestruturado em 2026-10-07** — era um arquivo único (`especificacao-logica.md`, ~1000
> linhas); virou esta pasta, um arquivo por área, pra abrir direto a regra de quem vai tocar em
> algo, sem `Ctrl+F` num arquivo gigante. Nenhuma regra mudou de conteúdo nessa reestruturação —
> só de endereço (checagem: contagem de `[REGRA]`/`[INV]`/`[PROPOSTA]` antes/depois bate).
>
> **Conteúdo não revisado desde 2026-09-09** (congelado em: Fase 6 CONCLUÍDA; migrations até V46;
> check-in do #17 emendado pelo ARCH #33; seção 6 — denúncia e moderação — implementada; os seis
> gatilhos de notificação no ar). Tudo que fechou DEPOIS disso (fatias G, H, J, a Dúvidas
> Frequentes) **não tem regra aqui ainda** — a fonte pra isso é o Handoff/Painel Mestre até alguém
> extrair pra um arquivo novo nesta pasta.

## Mapa — onde ler antes de tocar em cada área

| Arquivo | Área | ARCH / fatia |
|---|---|---|
| `00-regras-gerais.md` | Autoridade do servidor, fluxo de dependência | — (vale pra tudo) |
| `01-motor-de-geracao.md` | Motor de treino, gates de `/programs/generate` | #20, #24, #26, #27, #28 |
| `02-edicao-de-programa.md` | Edição de programa gerado por IA vs. manual | #25 |
| `03-agenda-e-cronograma.md` | Dias da semana, duração, semana atual | #22 |
| `04-execucao-de-treino.md` | Sessão de treino, idempotência | Fase 5 |
| `05-offline.md` | Leitura/escrita offline, outbox, login | #30 |
| `06-gamificacao.md` | XP, nível, conquistas, grupos (visão geral) | #16, #18, #32 |
| `07-checkin-e-moderacao.md` | Check-in, denúncia, máquina de estados | #33 |
| `08-notificacoes-gatilhos-grupo.md` | Quando avisar em grupo (comentário, denúncia, emoji do dia) | fatia F |
| `09-comentarios-e-reacoes.md` | Comentar e reagir a check-in | fatia E.1 |
| `10-amizades.md` | Pedido, aceite, bloqueio | #35 |
| `11-notificacoes-sistema.md` | FCM, token, central, badge, retenção | #36 |
| `12-analise-de-progressao.md` | Gráficos de progresso, 1RM, janelas, paywall | fatia J |
| `13-idioma.md` | pt-BR/en, fallback, o que traduz e o que não | fatia G, #37 |
| `14-privacidade-e-seguranca.md` | O que o perfil público expõe, PII | #34 |

## Convenção pra manter isto útil

- **Regra nova de uma área que já tem arquivo** → entra nesse arquivo, não em `06-gamificacao.md`
  por ser "genérico" nem direto no Handoff.
- **Área nova sem arquivo ainda** (ex.: quando a fatia J.5/análise por exercício ganhar regra
  própria, ou a Dúvidas Frequentes) → cria um arquivo novo aqui, numerado na sequência, e soma
  uma linha nesta tabela. Não empilha tudo em "regras gerais" por economia de arquivo.
- **Regra que vale pra TODO o app** (não só uma tela/feature) → `00-regras-gerais.md`, não
  duplicada em cada arquivo de área.
- **Fechamento de ciclo continua igual** (REGRA do Project): fatia que mexeu numa regra daqui
  atualiza o arquivo certo, não só Handoff/Painel Mestre — e regenera o PDF
  (`python3 docs/geradores/gen_especificacao.py`, ou `gen_tudo.py` pra todos).
