# Fatia J — progresso e análise de progressão (2026-10-01 a 10-04)

> **Histórico.** Fatia fechada — preservado literal do antigo `handoff-retomada.md`.
> Estado atual em `docs/ESTADO.md`; convenções em `docs/CONVENCOES.md`;
> regra de comportamento em `docs/regras/`; débitos em `docs/fontes/debitos.md`.

---

### O que a fatia J deixou pronto, em uma olhada

| recorte | eixo de tempo | quem escolhe |
|---|---|---|
| Todos / Avulsos | janela de calendário: 8 (grátis), 26 ou 52 semanas | chips, com cadeado no free |
| Um programa | faixa de semanas DELE: `de..ate`, teto = `max(duração, última semana com dado)` | slider |

**Grátis:** totais, "último treino vs o anterior" e carga por semana (8 semanas).
**Pago:** janela de 26/52, 1RM estimado por exercício, séries por grupo e a lista de exercícios.

⚠️ **Dois números do mesmo cartão têm de medir o mesmo recorte.** Foi o defeito do `b8b1ca2`
(tonelagem filtrada ao lado da contagem global) e a mesma classe do rótulo "esta semana" numa
faixa que terminou semanas atrás. Ao tocar nesta tela, conferir isso antes de qualquer coisa.

| | |
|---|---|
| Catálogo de textos do app | **648 chaves**, paridade pt-BR × en travada por teste |
| Frases de erro sem tradução | **0** — o `TextosDeErro.SEM_TEXTO_PROPRIO` está vazio |
| Sentinelas em português no código | **0** — o `value == "Sim"` do paywall foi o último |
| Prosa vinda do servidor | **1 ponto**, enumerado: `erroDoCampo` — `rationale` saiu (fatia "rationale derivado") |
| Troca de idioma | tela própria, três portas, `LocaleManager` |
| `users.locale` (V47) | sincronizado na abertura, numa direção só |
| Catálogo de exercícios | **923 exercícios**, `description` + tradução en carregadas (V53-V55), catálogo↔banco 1:1 |
| Exercícios sem mídia | **0** — os 26 legado foram consolidados (4) ou removidos (22) |
| Ativação de programa (V60, reverte a V59) | ponteiro `users.active_program_id`, autoridade do servidor; "Meus treinos" ganha botão "Ativar programa"; treino de hoje dentro do ativo é DERIVADO (schedule × dia da semana do servidor); Home sem match em nenhum programa pede pra completar a agenda, não sugere treino avulso |
| Análise de progressão (fatia J) | `GET /me/progress` — tonelagem, carga/semana, 1RM estimado (Epley), séries por grupo, lista de exercícios. Recorte por programa, faixa de semanas, janela de 8/26/52 e seleção de quais exercícios ganham linha. **Sem migration**: todo o dado já existia em `workout_sessions` + `session_set_logs` |
| Portão da análise | **grátis**: totais, último-vs-anterior e carga/semana (8 semanas). **pago**: janela 26/52, 1RM por exercício, séries por grupo, lista. O chip travado fica visível com cadeado |
| Conquistas | 9 + **5 de carga** (`CARGA_1T` a `CARGA_250T`), alvo em TONELADAS |
| Gráficos | Canvas puro, sem biblioteca (decisão testada e confirmada — ver Painel) |
| Migrations | até **V60** (a fatia J não acrescentou nenhuma) |

⚠️ **O que os testes NÃO cobrem, e só um aparelho responde:** o `LocaleManager`, o
`attachBaseContext`, o `recreate()` e o `PATCH /me` de verdade. Os testes provam compilação,
paridade dos catálogos e que a reconciliação é *chamada* — não que ela funciona.

### ✅ J.5 — detalhe por sessão de um exercício dentro do programa (2026-10-04)

Fecha a fatia J por completo. A lista de exercícios da J.4 ganhou "ver detalhado": abre
`ExercicioDetalheScreen`, com o histórico INTEIRO do exercício dentro daquele programa — do
início até a última sessão, sem faixa. Não herda o `de`/`ate` da tela de Progress: seria um
TERCEIRO conceito de janela concorrendo com os dois que já existem (calendário e faixa de
programa). "Ver tudo que registrei" é, ao pé da letra, tudo.

| | |
|---|---|
| Servidor | `GET /me/progress/exercicio?programId=&exercicioId=` — `ProgressService.detalheDoExercicio`, já existia de uma sessão anterior. `ProgressPolicy.evolucaoPorSessaoNoPrograma` agrupa por `sessaoId`, não por data: duas sessões no mesmo dia (raro, mas possível) não podem virar um ponto só, ou `volumeKg` somaria dois treinos como se fosse um |
| Portão | **403 `ENTITLEMENT_REQUIRED`, não campo nulo** — ao contrário do `/me/progress` (J.2). Aqui não há bloco grátis pra misturar numa resposta só: é um drill-down de dentro de uma lista que já é paga, a tela inteira é profundidade paga |
| Contrato | `ExercicioDetalheDto` (`exerciseId`, `name`, `points: List<PontoDeSessaoDto>`). Cada ponto é **uma sessão**, não uma semana — `estimated1rm` (Epley, melhor série) e `volumeKg` (soma de todas as séries do exercício na sessão) viajam juntos de propósito: força subindo com volume caindo é treino de pico, não erro |
| Cliente | `ExercicioDetalheViewModel` — online-only, sem cache, modelado no `ProgramRepository.activateProgram`. Dois gráficos em Canvas reaproveitados do Progress (`CalhaDoEixo`, `CartaoDeGrafico`, `GraficoDeLinhas`, `GraficoDeBarras`): 1RM estimado em linha, volume por sessão em barra com toque |
| PR na tela | Texto estático ACIMA do gráfico, não marcador dentro do Canvas — risco e raio de impacto menores do que tocar no `GraficoDeLinhas` compartilhado por uma fatia pequena |
| Eixo X | Por ÍNDICE de sessão, não alinhado à régua de semanas do Progress — a J.5 não tem janela de calendário, é o histórico inteiro, um ponto por sessão, igualmente espaçado |
| Teste (C2) | `ExercicioDetalheViewModelTest`, 4 casos incluindo o caminho de falha: estado inicial, sucesso popula e tira o `carregando`, falha (`AppError.Forbidden`/`NotFound`) popula `erro`, recarregar depois de um erro limpa o erro antigo |
| Verificação | `:app:androidApp:testDebugUnitTest` **150** (146 + os 4 novos), `testAndroidHostTest` e `assembleDebug` — verdes, confirmados por você no XML, não no "BUILD SUCCESSFUL" |

⚠️ **`diaMes` virou `internal`** (estava `private` em `ProgressScreen.kt`) pra reaproveitar o
formatador `dd/MM` na tela nova — mesmo padrão já usado para `CalhaDoEixo`/`CartaoDeGrafico`.

### ✅ Dúvidas Frequentes — tela estática, sem servidor (2026-10-04)

Fora da fatia J. `AppRoute.Duvidas` existia como placeholder desde uma fase anterior (abria
`EmBreveScreen`) — a fase 8 (Aprender/Wiki) já tinha confirmado por escrito que FAQ ficava fora
do escopo dela. Virou tela de verdade.

Duas decisões ratificadas com você antes de codar (C1):

1. **Conteúdo fixo no app** (`strings.xml`, pt/en), não banco — ao contrário da Wiki, que é
   migration + cache + offline-first. Para 8 perguntas curtas e estáveis, aquela máquina inteira
   seria peso morto.
2. **Accordion** (expande no lugar), não navegação por pergunta — a Wiki navega porque o
   conteúdo é longo; aqui a resposta é duas frases, navegação seria demais.

8 perguntas, todas fundamentadas em regra já implementada — nada inventado: check-in (#17,
votação 24h + ≥50% aprova + empate aprova), contestação (24h, só check-in de terceiro, via admin
do grupo), localização opt-in com PII separada, lista de recursos Premium (saída dos
`paywall_recurso_*`/`paywall_valor_*` reais), XP/nível/conquistas do perfil individual (#16),
ranking de grupo por CONTAGEM de check-in, não XP, múltiplos programas coexistindo (#27) e
autoridade do servidor (nunca o relógio do cliente).

⚠️ **Gaps deliberadamente deixados de fora, não esquecidos**: preço/plano, fluxo de
cancelamento, contato de suporte, link da política de privacidade formal — eu não tinha como
confirmar nenhum dos quatro sem inventar. Ver "Pendências ativas" abaixo.

Polimento de layout pedido depois, só visual: seta que gira (`animateFloatAsState` +
`graphicsLayer.rotationZ` — primeira vez nesse idiom específico, rotação de ícone; o
`animateFloatAsState` em si já era usado em outras telas), tingimento do card por estado
aberto/fechado, divisor entre pergunta e resposta, peso de fonte mais leve. `Card(onClick=...)`,
não `Modifier.clickable` — segue o precedente do `OutlinedCard(onClick=...)` em
`ProgramListScreen`.

Sem teste — tela 100% estática, sem ViewModel e sem lógica de negócio (nada no Tier 1/2 pra
testar; o mesmo padrão já usado para outras telas puramente de apresentação).

### ✅ J.3 e J.4 — recorte, janela e seleção (2026-10-02 e 03)

Fecham a fatia J. **Nenhuma migration**, de novo.

**J.3 — filtro por programa e faixa de semanas.** Programas COEXISTEM (V60/#27), então o gráfico
somava dois sem avisar: é correção, não conveniência. O nome do chip sai do cache LOCAL — nome de
programa é DERIVADO (V48/#37), então `availablePrograms` traz só ids e id desconhecido é PULADO em
vez de virar um chip genérico. A semana do programa conta por **dias corridos** do `started_at`
(`Math.floorDiv`, porque a divisão de `Int` trunca em direção a zero). O teto da faixa é
`max(duração declarada, última semana com dado)`: `duration_weeks` é um PLANO.

**J.4 — janela de calendário, lista de exercícios e seleção.**

| | |
|---|---|
| Janela | `?semanas=8\|26\|52`, em `JanelasDeProgresso` no **contrato** — servidor e cliente leem a mesma lista. `weeksWindow` devolve a **aplicada**, não a pedida |
| Um recorte | `naJanelaDeCalendario` espelha o `naFaixaDoPrograma`. **Duas janelas declaradas**: totais/“desde”/comparação usam todo o histórico; os três gráficos usam a janela |
| Portão | `weeklyLoad` saiu do pago. Era 100% pago, então ninguém free via 8 semanas — e “janela longa” não vendia nada |
| Scroll | acima de 12 barras o gráfico rola, com a **calha do Y fora do scroll** e começando no fim |
| Lista | `exerciseSummary` com TODOS os exercícios do recorte; a lista substituiu a legenda e virou o controle das linhas (`?exercicios=`, máximo 3 no servidor também) |
| Verificação | `:server:test` **552**, `:server:integrationTest` **174**, `:app:androidApp` **146**, `testAndroidHostTest` **29** |

⚠️ **Quatro defeitos achados olhando a TELA, não o verde — e três deles eram mentira de gráfico:**

1. **Média de séries dividindo o histórico inteiro por 8.** Volume semanal inflado ~3× em quem
   treina há meses. Passou na validação original porque o seed tem exatamente 8 semanas: o número
   certo por coincidência. A proporção entre grupos continuava honesta; só o absoluto mentia.
2. **A linha de 1RM plotava todo o histórico** ao lado de barras de 8 semanas, na mesma tela, sem
   nada dizendo isso. Inofensivo até a janela de 52 existir.
3. **O rótulo `"esta semana"` era cravado** no fim do eixo. Com faixa 10–14 em quem está na 16, a
   última barra não é esta semana.
4. **A contagem de treinos ignorava o recorte** (`stats` global ao lado de tonelagem filtrada), e
   no servidor ignorava a faixa.

> **Dois números vizinhos medindo períodos diferentes é o defeito mais barato de cometer e o mais
> caro de notar: ninguém desconfia de "30 treinos", só do gráfico.**

⚠️ **E um de processo:** o `:server:integrationTest` passou quatro commits **sem compilar** — o
construtor do `ProgressService` ganhou `programs` e ninguém rodou aquele source set. `src/test` e
`src/integrationTest` são dois source sets, e o CI roda os dois. Virou item do template de PR.

### ✅ Fatia J — Progresso desmembrado e análise de progressão (2026-10-01 e 02)

Começou com um pedido de layout e virou fatia inteira. **Nenhuma migration**: o dado que a análise
precisa já estava em `workout_sessions` + `session_set_logs` desde a V20 — faltava alguém somá-lo.

**J.1 — desmembramento (2026-10-01).** A tela de Progresso acumulava métricas, a porta das
Conquistas e o histórico inteiro. Conquistas e Histórico ganharam tela própria, alcançadas pelo
**drawer**: os dois são CONSULTA, e quem abre o app para treinar não passa por eles.

**J.2 — a análise (2026-10-02).** Quatro passos, na ordem Backend → Contrato → Cliente.

| | |
|---|---|
| Política | `ProgressPolicy` (Kotlin puro, 20 testes): tonelagem, 1RM por Epley, séries por grupo, último treino contra o anterior do MESMO treino |
| Servidor | `ProgressService` + `GET /me/progress`. O portão é **ausência**, não 403: responde 200 com os blocos pagos nulos. Para free eles nem são calculados — não há número a vazar |
| Catálogo | `ExerciseRepository.paraAnalise(ids)`: leitura crua (nome de piso + músculos primários), **sem idioma**. A tradução segue na borda |
| Conquistas | `Metrica.CARGA` com 1/10/50/100/250 **toneladas**. Atravessa servidor + contrato + `strings.xml` numa fatia só — os testes de amarração não deixam fatiar |
| Cliente | Porta `Progresso` separada de `Stats` (a Home lê stats a cada `onResume`; a análise varre o histórico e só interessa a uma tela). Cache offline-first com o **idioma na chave** |
| Gráficos | Canvas puro: barras com toque por semana, linha com área e escala compartilhada, barras horizontais ranqueadas. Cor própria (`Chart1/2/3`), validada para daltonismo |
| Tier 3 | `ProgressIntegrationTest` (9, Postgres real) + `ProgressDtoSerializacaoTest` (4, `DefaultJson` do Ktor) |
| Verificação | `:server:test` **515**, `:server:integrationTest` **173**, `:app:androidApp` **125** — todos conferidos no XML, não no "BUILD SUCCESSFUL" |

⚠️ **Quatro defeitos que só apareceram porque alguém olhou o número, não o verde:**

1. **"Mesma fórmula" era meia verdade.** `StatsService` e `ProgressService` chamavam `cargaDaSerie`,
   mas cada um repetia a condição de elegibilidade por conta própria — duas cópias de uma condição
   é como a conquista de carga e a tela de progresso discordam um dia sobre uma série de 0 kg.
   Virou `ProgressPolicy.cargaDeSerieCrua`, que faz elegibilidade E fórmula numa chamada.
2. **Um teste meu afirmava algo falso.** `e1rm(65,10)=86,7` contra `e1rm(70,8)=88,7` — eu tinha
   escrito o contrário, e a mesma conta errada estava no KDoc, a caminho de virar documentação
   mentirosa. O teste pegou o erro de raciocínio, que é para isso que ele serve.
3. **Série plana afundava na base.** Com um ponto só, `min == max`, a amplitude caía no fallback
   `1.0` e o `y` dava 0 — o ponto ia para a linha de base como se a carga fosse zero.
4. **`RAISE NOTICE` do plpgsql não tem `printf`.** O `%.1f` do script de seed imprimia o número
   com `.1f` colado.

**Verificação ponta a ponta, e esta é a parte que vale guardar:** um script em `tools/` semeou 8
semanas de treino falso numa conta de teste e o `psql` imprimiu **181,8 t em 30 sessões**. A tela
mostrou os mesmos 181,8 t e 30 treinos. Séries no Postgres → política → DTO → JSON → cache local →
Canvas, tudo conferido por um número único. ⚠️ O script **não é migration e não pode virar uma** —
mora em `tools/` (gitignored) e escolhe exercícios por GRUPO MUSCULAR, não por nome, porque nome
muda em curadoria (a V54 apagou vários).

**Aberto, aguardando você:** o filtro por PROGRAMA + faixa de semanas (J.3) está desenhado e
registrado no `debitos.md` (P4) — falta decidir se o balde de "avulsos" é selecionável.

