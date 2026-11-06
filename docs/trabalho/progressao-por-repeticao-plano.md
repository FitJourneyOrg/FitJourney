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

### Servidor — FEITO (Tier 1/2/3 verdes; fatia fecha aqui, sem Contrato/Cliente -- ver abaixo)

- `ProgressaoPorRepeticao` (novo, `engine/v3/`) — Tier 1 puro. `Estado(repsMin, repsMax,
  exposicoes, semSubir)`; SOBE/DESCE ±1 nos dois limites; piso na `PrescricaoV3.Faixa` original;
  teto 20 reps.
- `ExercicioParaProgressao.equipment` (novo campo, cru do catálogo) — é o que decide, com
  `incremento == null`, se o exercício é `BODYWEIGHT` (motor de repetição) ou BAND/HAND_GRIP
  (continua sem progressão, débito separado).
- `ProgressaoAplicadaService.aplicarExercicio()` — branch em `incremento == null`; novo método
  `aplicarExercicioPorRepeticao()`. **Sem calibração**: a faixa original já é o estado válido da
  1ª exposição (diferente do eixo de carga, que precisa descobrir um peso que não existe).
- **Decisão do Rafael (2026-11-06), achada ao implementar**: `AncoraDoIniciante.Evento` ganhou um
  3º caso, `Repeticao(decisao: ProgressaoPorRepeticao.Decisao)`. Sem isso, `semSubirNoFim`
  (contador de estagnação/DELOAD) e `PromocaoDeNivel.diagnosticoIniciante` (promoção de nível)
  só reconheciam `Evento.Carga` — quem treina bodyweight cairia em DELOAD falso a cada 3 sessões
  mesmo progredindo. Resolvido com `Evento.subiu` (true pra SOBE de qualquer um dos dois eixos);
  os dois lugares que contavam `decisao == Decisao.SOBE` na mão agora usam `.subiu`.
- `ProgressaoRepository`: `buscarPorRepeticao`/`registrarPorRepeticao`, mesma linha/PK de
  `exercise_progression` (não é dado duplicado — um exercício usa um eixo OU outro, nunca os
  dois, porque `incremento` do catálogo é fixo). `progression_event.tipo` ganhou
  `SOBE_REP`/`MANTEM_REP`/`DESCE_REP` (sufixo pra não colidir com os nomes do outro enum).
- Migration `V76__progressao_por_repeticao.sql` — aditiva: 2 colunas nullable
  (`reps_min`/`reps_max`) + CHECK, e o CHECK de `progression_event.tipo` recriado com os 3 tipos
  novos. Nenhum dado existente apagado ou reescrito.

### Contrato / Cliente -- FORA DE ESCOPO (achado ao revisar, não é débito de repetição)

- Checado antes de tocar: o eixo de CARGA também não expõe nada ao cliente hoje -- não há rota
  `GET` nem campo em `WorkoutExerciseDto`/`SetLogDto` mostrando carga ou faixa sugerida. O motor
  roda só como registro invisível (SOBE/MANTEM/DESCE em background, alimenta deload/promoção);
  quem treina com carga também não vê alvo na tela.
- "Expor a faixa de reps pro cliente" não é paridade -- é feature NOVA, que afeta os dois eixos
  igualmente, maior que esta fatia. Fazer só pra bodyweight seria inconsistente. Fica pra uma
  fatia futura ("mostrar sugestão de progressão na execução"), registrada em `debitos.md`, não
  como algo específico de repetição.
- **A fatia fecha no SERVIDOR**, em paridade real com o que já existe pro eixo de carga.

> Ordem de implementação: Backend → Contrato → Cliente. Dependências apontam para o `Domain`.

## Migration

<depende do desenho — se "sobe" grava um alvo de reps por (user, exercise), é tabela/coluna nova,
sem dado de usuário existente para apagar>

## Testes (C2)

| Tier | O que | Onde |
|---|---|---|
| 1 — lógica pura | `decidir()` por repetição, espelhando os mesmos critérios de aceite da progressão por carga | `ProgressaoPorRepeticaoTest` |
| 2 — serviço com fake | BODYWEIGHT usa `ProgressaoPorRepeticao` sem calibração; BAND continua pulando (caminho de falha) | `ProgressaoAplicadaServiceTest` |
| 3 — rota/repositório, Testcontainers | `buscarPorRepeticao`/`registrarPorRepeticao` contra Postgres real, idempotência (D-2) no eixo de reps | **não escrito ainda** |

Cada um inclui ao menos um caminho de falha.

## Fora de escopo, por decisão

- **BAND/HAND_GRIP** — sem kit do usuário modelado no perfil; débito separado em `debitos.md`.
- **Grafo de variação de exercício** ao bater o teto de 20 reps — fica só `MANTEM`, sem sugestão.
- **Calibração derivada do histórico** — mesmo teto do ADR da carga (`motor-v3-semantica-de-carga.md`).
- **`ProgressaoRepository.todos()`** devolve `Map<Uuid, Progressao.Estado>` pra TODOS os exercícios
  do usuário, inclusive os que progridem por repetição (viriam com `carga = null`, indistinguível
  de "calibração pendente"). Função sem chamador em produção hoje — achado ao revisar o
  repositório, não corrigido por não ter uso real que quebre. Registrar em `debitos.md` no
  fechamento.
- **Tier 3 (Testcontainers)** desta fatia — feito, verde (`:server:integrationTest`).
- **Mostrar sugestão de progressão (carga OU reps) na tela de execução** — não existe pra NENHUM
  dos dois eixos hoje; fatia futura própria, não nasce aqui.

## Ao fechar — promover antes de apagar

- [ ] `[REGRA]`/`[INV]` → `docs/regras/01-motor-de-geracao.md`
- [ ] decisão de arquitetura → `docs/arch/`
- [ ] fora de escopo → `docs/fontes/debitos.md`
- [ ] narrativa e defeitos achados → `docs/historico/<fatia>.md`
- [ ] migration → `docs/migrations/INDICE.md`
- [ ] `ESTADO.md` atualizado, `gen_tudo.py` rodado
- [ ] **este arquivo apagado**
