# progressao-por-repeticao — plano

_Aberta em 2026-11-05. Rascunho: não é fonte da verdade (ver README desta pasta)._

## O que é e por que agora

42/147 (28,6%) do pool do motor (`BODYWEIGHT` 26, `BAND` 15, `HAND_GRIP` 1) tem `incremento` NULL
por não ter carga numérica. `decidir()`/`calibrar()` exigem `Double` não-nulo — pra esses
exercícios o chamador simplesmente não invoca o motor (`[REGRA]`, `01-motor-de-geracao.md`).
Resultado: quem treina em casa sem equipamento de carga variável não tem progressão automática
nenhuma. Não é polimento — é feature ausente pra um público inteiro (débito P0, `debitos.md`).
Também é a causa raiz de 4/26 âncoras do avançado estruturalmente mortas (`estimarUmRm()` exige
`carga: Double`, sempre `null` em bodyweight puro) — resolver esta fatia é pré-requisito pra
atacar aquela, não a mesma fatia.

## Perguntas que preciso responder antes de codar

- [x] **Escopo: as três categorias juntas ou só BODYWEIGHT puro agora?** BAND não tem "incremento"
  físico claro — resistência é por nível de elástico, e o perfil não registra o kit do usuário
  (nenhum campo hoje). HAND_GRIP é 1 exercício só. Tratar os três com a mesma regra por repetição
  finge que são o mesmo problema; BAND pode precisar de modelagem de kit primeiro (fora desta
  fatia) e ficar fora por ora.
- [x] **Mecânica de "sobe": que parâmetro muda quando bate o topo da faixa com RIR no alvo?**
  Repetições puro (ex.: a cada SOBE, `repsMin`/`repsMax` da sessão sobem +1, teto alto) é o paralelo
  direto do incremento de carga e cabe no schema de progressão atual sem depender de nada novo no
  catálogo. A alternativa — sugerir trocar pra uma variação mais difícil do mesmo exercício
  (flexão → flexão com pés elevados) — exige grafo de progressão de exercícios no catálogo, que
  não existe hoje: fatia maior, decisão separada.
- [x] **Teto de repetições: existe, e o que acontece ao bater nele?** Sem teto, a faixa sobe pra
  sempre (50, 80, 100 reps) — fisiologicamente sem sentido pra treino de força/hipertrofia. Com
  teto, falta decidir o que o app faz quando bate: mantém no teto, ou fica sem recomendação (igual
  a "DADO INSUFICIENTE" do avançado)?
- [x] **Onde isso mora no código**: um `when` dentro de `decidir()` existente (exige mudar a
  assinatura — `incremento: Double` vira `Double?` — e me parece errado, já que a semântica inteira
  é outra), ou um objeto paralelo `ProgressaoPorRepeticao` com seu próprio `Estado`/`Decisao`, e o
  chamador escolhe qual invocar por `incremento == null`? A segunda parece a leitura certa da
  `[REGRA]` atual ("o chamador NÃO invoca o motor" — hoje não invoca nada; a decisão é invocar ESTE
  motor em vez do outro, não emendar o existente).

## Decisões tomadas

- **Escopo: só BODYWEIGHT (26 exercícios) nesta fatia** — BAND (resistência por nível de elástico,
  sem kit do usuário modelado no perfil) e HAND_GRIP (1 exercício) vão pro `debitos.md` como item
  separado. Tratar os três juntos fingiria que é o mesmo problema.
- **Mecânica do "sobe": reps puro.** Quando todas as séries batem `repsMax` com RIR no alvo
  (mesmo critério de `Progressao.decidir()`), a faixa de reps da sessão sobe +1 (`repsMin`/`repsMax`
  ambos). É o paralelo direto do incremento de carga. Alternativa descartada: sugerir variação mais
  difícil do exercício — exige grafo de progressão no catálogo, que não existe; fatia maior,
  decisão separada.
- **Teto: 20 repetições, absoluto, por cima de qualquer papel.** Isolamento (12–16) e
  composto/acessório (6–9/8–12) cabem embaixo dele sem tabela nova. Ao bater o teto: `MANTÉM`
  (sem alarme, sem "dado insuficiente"). Sugestão de trocar de variação ao bater o teto é débito
  registrado, não resolvido aqui.
- **Onde mora: `ProgressaoPorRepeticao`, objeto novo e paralelo a `Progressao`.** Não mexe na
  assinatura de `decidir()` (colar as duas semânticas em `incremento: Double?` repetiria o erro já
  apontado no ADR da carga pro passo proporcional). O chamador escolhe o motor por
  `incremento == null`.

## Desenho

### Servidor

- `ProgressaoPorRepeticao` (novo, `engine/v3/`) — Tier 1 puro, espelha `Progressao.decidir()` no
  eixo de reps. `Estado(repsMin, repsMax, exposicoes, semSubir)`; SOBE/DESCE andam ±1 nos dois
  limites; piso é a `PrescricaoV3.Faixa` original do papel/objetivo; teto fixo em 20 reps
  (`TETO_REPETICOES`). **Feito, teste Tier 1 escrito — falta rodar (`:server:test`).**
- Falta (próximo passo): `ProgressaoAplicadaService.aplicarExercicio()` hoje tem
  `val incremento = cat.incremento ?: return` — pula o exercício inteiro. Precisa de um branch:
  `incremento != null` → `Progressao` (como já é); `incremento == null` e exercício é
  `BODYWEIGHT` → `ProgressaoPorRepeticao`; `incremento == null` e não é `BODYWEIGHT` (BAND/
  HAND_GRIP) → continua pulando, sem mudança.
- Falta: persistência do novo `Estado` — `ProgressaoRepository` hoje serializa
  `Progressao.Estado` (tem `carga`). Precisa de uma tabela/coluna nova pro estado por repetição
  (schema a desenhar), já que não é o mesmo formato.

### Contrato

- A decidir junto da persistência: o cliente precisa saber a faixa de reps ATUAL (pra mostrar
  "sua meta hoje é 13–17", não só a prescrição fixa do papel). Hoje `SetLogDto`/`WorkoutExerciseDto`
  não carregam isso — provavelmente um campo novo no mesmo lugar que expõe a prescrição.

### Cliente

- Não iniciado.

> Ordem de implementação: Backend → Contrato → Cliente. Dependências apontam para o `Domain`.

## Migration

<depende do desenho — se "sobe" grava um alvo de reps por (user, exercise), é tabela/coluna nova,
sem dado de usuário existente para apagar>

## Testes (C2)

| Tier | O que | Onde |
|---|---|---|
| 1 — lógica pura | `decidir()` por repetição, espelhando os mesmos critérios de aceite da progressão por carga | `ProgressaoPorRepeticaoTest` (ou nome equivalente) |
| 2 — serviço/ViewModel com fake | | |
| 3 — rota/repositório, Testcontainers | | |

Cada um inclui ao menos um caminho de falha.

## Fora de escopo, por decisão

<a confirmar junto das perguntas: BAND/HAND_GRIP se o escopo desta fatia for só BODYWEIGHT; grafo
de variação de exercício; calibração "derivada do histórico" (mesmo teto do ADR da carga)>

## Ao fechar — promover antes de apagar

- [ ] `[REGRA]`/`[INV]` → `docs/regras/01-motor-de-geracao.md`
- [ ] decisão de arquitetura → `docs/arch/`
- [ ] fora de escopo → `docs/fontes/debitos.md`
- [ ] narrativa e defeitos achados → `docs/historico/<fatia>.md`
- [ ] migration → `docs/migrations/INDICE.md`
- [ ] `ESTADO.md` atualizado, `gen_tudo.py` rodado
- [ ] **este arquivo apagado**
