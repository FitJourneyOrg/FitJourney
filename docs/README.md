# FitJourney — Documentação

Índice da documentação técnica. Tudo vive aqui dentro; **a raiz do projeto tem só o `README.md`
do repositório.**

**Hierarquia de verdade:** código-fonte > `ESTADO.md` / `CONVENCOES.md` > regras > contrato de
API > documentação completa. Em divergência, **o código vence** — e a doc se corrige.

**Só um documento é de leitura obrigatória no início da sessão: `ESTADO.md`.**

---

## Regenerar os PDFs

```bash
python docs/geradores/gen_tudo.py              # todos, menos o catálogo
python docs/geradores/gen_tudo.py --catalogo   # inclui o catálogo (exige o Postgres de pé)
```

Dependência única: `pip install reportlab`.

> Os geradores individuais continuam existindo (`geradores/gen_handoff.py`, `gen_painel.py`, …)
> para quando só um documento mudou. O `gen_tudo.py` existe porque fechar uma fatia exigia onze
> comandos na mão, e a única forma de perceber um esquecido era reparar na data de um PDF — foi o
> que aconteceu com o `FitJourney_TESTES.pdf`, que ficou três dias para trás.

---

## Estrutura

```
docs/
├── README.md               ← este índice
├── PR-template.md          ← modelo de descrição de PR
│
├── ESTADO.md               ← "onde paramos". O ÚNICO de leitura obrigatória (~95 linhas)
├── CONVENCOES.md           ← o que vale em toda sessão e não muda por fatia
│
├── fontes/                 ← .md EDITADOS À MÃO. É aqui que se escreve
│   ├── contrato-api.md          ← rotas, DTOs, enums, códigos de erro
│   ├── documentacao-completa.md ← visão, produto, roadmap (o "o quê" e o "porquê")
│   ├── adendo-stack.md          ← tecnologias em uso, versão e papel
│   ├── mapa-de-testes.md        ← cobertura, lacunas e ONDE investir a próxima
│   └── debitos.md               ← débitos técnicos: aberto + pago, por fase
│
├── trabalho/               ← a fatia EM ANDAMENTO: ideias e passos antes de codar. DESCARTÁVEL —
│                             apagada no Fechamento de Ciclo, depois de promover o que sobreviveu.
│                             Modelo em trabalho/MODELO.md. Não é fonte da verdade
│
├── historico/              ← fatias FECHADAS, um arquivo por fatia (índice em historico/README.md).
│                             Leitura NÃO obrigatória — abre quando for mexer na área.
│                             Não gera PDF: o git já é o arquivo
│
├── arch/                   ← ADRs, um arquivo por decisão (índice em arch/README.md).
│                             Fase concluída que vale resumo de fechamento ganha um
│                             arquivo próprio ali dentro (ex.: arch/fase-4/fase-4-treino.md)
├── regras/                 ← [REGRA] e [INV] de comportamento, um arquivo por área
│                             (índice em regras/README.md). Tier 3 da hierarquia de verdade,
│                             logo abaixo de Handoff/Painel Mestre — leia antes de tocar numa
│                             área, não confie no que já sabe de sessão passada
├── propostas/              ← lacunas ainda não ratificadas [PROPOSTA]
├── migrations/             ← INDICE.md (uma linha por migration) + schema por versão
├── migration_verify/       ← SQL que prova o que a migration deixou no DADO (não no schema —
│                             isso o Flyway garante). Rodar depois de migration de dado, não de
│                             schema puro. Ver migration_verify/README.md
├── erros/                  ← erros que já aconteceram no projeto + solução, um por arquivo
│                             (índice em erros/README.md). Veio de referencia/diagnostico-
│                             ambiente.md em 2026-10-07 — referencia/ foi apagada
│
├── gerados/                ← .md gerados por script. NÃO editar à mão. Só isso mora aqui —
│   │                         arquivo de auditoria manual vai em tools/, não aqui
│   ├── testes.md                ← catálogo da suíte (gen_testes.py)
│   ├── catalogo.md              ← retrato do banco de exercícios (gen_catalogo.py)
│   ├── erros.md                 ← catálogo de mensagens de erro (gen_erros.py)
│   └── strings.md               ← catálogo pt-BR da interface (gen_strings.py)
│
├── pdf/                    ← TODOS os PDFs. Saída dos geradores
│
└── geradores/              ← os scripts Python
    ├── gen_tudo.py              ← roda todos os outros. É o único que você precisa lembrar
    ├── gen_estado.py            ← era gen_handoff.py (removido 2026-10-07)
    ├── gen_convencoes.py        ← era gen_painel.py (removido 2026-10-07)
    ├── gen_regras.py            ← era gen_especificacao.py (renomeado 2026-10-07)
    ├── gen_<doc>.py             ← um por documento, para quando só ele mudou
    ├── gen_testes.py            ← varre os source sets e conta a suíte
    ├── gen_catalogo.py          ← consulta o Postgres (precisa do container)
    └── genlib.py                ← o render de Markdown → PDF, compartilhado
```

**A regra que a estrutura tenta tornar óbvia:** `fontes/` é onde se escreve, `gerados/` e `pdf/`
são saída. Editar um arquivo de `gerados/` é trabalho perdido no próximo `gen_tudo.py`.

**A regra que a reestruturação de 2026-10-07 acrescentou:** o corte é por **tempo de vida**, não
por assunto. O que muda a cada fatia vive no `ESTADO.md` e é curto porque é lido sempre; o que é
permanente vive em `CONVENCOES.md`, `regras/` e `arch/`; o que é passado vive em `historico/` e
quase nunca se lê. O Painel Mestre morreu porque era as três coisas ao mesmo tempo, e o Handoff
chegou a 1983 linhas pelo mesmo motivo — com as mesmas fatias escritas nos dois.

**O ciclo de vida de uma fatia tem três estágios, e cada um tem um lugar só:**

```
docs/trabalho/<fatia>.md   →   ESTADO.md "Agora"   →   docs/historico/<fatia>.md
    planejando                    codando                     fechada
```

A informação **muda de lugar**, nunca é copiada. Copiar é o que produziu o Painel e o Handoff
descrevendo a mesma fatia sem nenhum dos dois saber qual era o atual.

**`tools/` não entra nesse ciclo** e não se limpa ao fechar fatia: a raiz é bancada de script, o
`curadoria/` é pipeline do catálogo, e só o `tools/cache/` é descartável (dado derivado, regenera).
Ver `tools/README.md`.

---

## Por fase

| Fase | Título | Marco | Status |
|---|---|---|---|
| 0–3 | Fundação, backend, auth, onboarding | — | concluídas |
| 4 | Treino (núcleo) | M3 | concluída |
| 5 | Progresso & Gamificação | — | concluída (2026-08-17) |
| 6 | Grupos, check-in, social | M4 | concluída (2026-09-09) — A.0, A, B, C, C.1, D, E.1, E.2, F e #35 |
| G · H · J | Idioma · catálogo · progresso | — | concluídas (2026-09-11 a 10-04) |
| 8 | "Aprender" (acervo de conteúdo) | — | **iniciada** — V61 aplicada, plano escrito |
| 7 · 9–11 | Assinatura → iOS | M5–M7 | a fazer |

Esta tabela é a fonte do estado por fase — não duplicar em outro arquivo. ⚠️ A linha da Fase 6 foi
corrigida em 2026-10-07: dizia "em curso, faltam D, E e o resto da F" enquanto o Handoff declarava
a fase concluída desde 2026-09-09 e três fatias fecharam depois dela. Backlog transversal em `fontes/debitos.md`.

---

## Decisões de arquitetura (ADRs)

Um arquivo por decisão em `arch/`, índice em `arch/README.md`. Âncoras recentes:

- **#30** — offline-first (leitura e escrita, com outbox).
- **#31** — política única de apresentação de erro.
- **#33** — grupos e check-in: **confiança, não verificação** (revoga a votação do #17).
- **#34** — perfil público e drawer, emendado pela 9.3-A e pela C.1 (duas telas, dois tipos).
- **#35** — amizades: o grafo do convite.
- **#36** — notificações: **a gravada é a verdade, o push é o aviso**.

## Baterias manuais

Pasta `baterias/` apagada em 2026-10-07 — todas as fatias fecharam com saldo zero (defeitos
achados já corrigidos) e o conteúdo relevante (débitos abertos, lições) já estava duplicado em
`handoff-retomada.md`, `painel-mestre.md`, `debitos.md` e `mapa-de-testes.md`. Próxima fatia que
precisar de bateria manual começa um arquivo novo aqui.

## Documentos de trabalho encerrados

- **`fase-6-decisoes.md`** apagado em 2026-10-07 — doc de CONFERÊNCIA da Fase 6 (grupos/check-in + fatias D/E.1/E.2/F/#35 que entraram no mesmo arco). Zero itens `[ABERTO]` restantes; as decisões ratificadas já viraram `[REGRA]`/`[INV]` em `regras/07-checkin-e-moderacao.md` e nas demais áreas, e os débitos que o documento apontava já estão em `debitos.md` (✅ Pagos). Detalhe narrativo do "porquê" de cada decisão fica só no git history a partir de agora.
- **`relatorio-testes.md`** apagado em 2026-10-07 — o próprio arquivo já se declarava "histórico, parou em 2026-08-17": contagem/catálogo atual é o `gen_testes.py` (`gerados/testes.md`), cobertura/lacunas é o `mapa-de-testes.md`. O que só ele tinha (lições de metodologia de teste, tipo "teste que passa por dois motivos não prova nenhum") não tem lar canônico — fica no git history.

## Referência

- `migrations/BANCO_EXERCICIOS.md` — schema e relacionamento das tabelas de exercícios, por versão de migration.

## Erros que já aconteceram

Pasta `erros/`, um arquivo por erro + índice. **Consultar antes de investigar o app**: metade
do tempo da bateria da F.1 foi gasto em IP, rede e configuração do aparelho, não em código.
Cresce conforme acontece um novo (ver convenção no `erros/README.md`). Não confundir com
`gerados/erros.md` — aquele é o catálogo de MENSAGENS de erro do servidor (gen_erros.py),
coisa bem diferente de bug que já aconteceu aqui.

---
_Toda esta pasta é local e está no `.gitignore`._
