# Estado — onde paramos

> **O único documento de leitura obrigatória no início da sessão.** Só mora aqui o que muda a cada
> fatia. O resto se lê quando o assunto exigir: convenções em `CONVENCOES.md`, regra de
> comportamento em `regras/`, decisão em `arch/`, débito em `fontes/debitos.md`, migration em
> `migrations/INDICE.md`, fatia fechada em `historico/`.

_Atualizado em 2026-10-27 (**motor v3 — fatia 8 EM ANDAMENTO, mudou de escopo**: a causa do `entregue>capacidade` foi isolada — é o piso do isolamento na etapa [4], **não** o fallback do catálogo — e o **laço de corte por tempo deixou de ser necessário**. Uma auditoria completa dos 720 (todos os músculos, não só os zerados) mostrou que o problema era 36× maior do que se media: **2.079 pares (perfil, músculo) abaixo do piso**, dos quais 91% é capacidade física, 3% buraco de catálogo em CASA e 6% falha de alocação. Construída a **etapa [7], `ProgramAudit`** (`efetivo = direto + indireto` e status por músculo), que é o que cobre os 91%. Três defeitos achados e resolvidos no caminho: ordem de sacrifício derivada do peso (`[INV]` fonte única), limiar de mínimo por objetivo (MV × MEV) e **o Kotlin não aplicava o fator 0,6 de `MAINTAIN` ao alvo semanal** (180/720 perfis divergindo do oráculo). `:server:test` **661 verdes**. **D-1 DECIDIDO** (híbrido): a tabela passa a estar em **convenção total** para todos os músculos, uma contabilidade só, e o piso direto é restrição adicional — `[REGRA]` na especificação com a história (ARCH #26 nasceu em convenção direta → v3 redefiniu como total → item 20 corrigiu os braços → esta rodada corrigiu antebraço, glúteo e posterior). Três coeficientes **sem fonte** saíram da tabela de indireto (`costas→antebraço`, `posterior→antebraço`, `glúteo→posterior`), com `[INV]` e tripwire nos dois lados; glúteo 4/8/12 → **8/10/12** (decisão de produto, registrada como tal). Referências: alvo total **65/113/148**, demanda direta **49/94/122**. **Dois defeitos antigos fecharam junto**: as 2 vermelhas do oráculo (`POSTERIOR: -0,8`) eram o coeficiente sem fonte — oráculo **32/32 verde**. **CORE saiu do motor e do contrato** (V68): era o único alvo com pool vazio nas 15 combinações de ambiente × nível × acessório e os 720 `SEM_CATALOGO` da matriz eram todos dele; medido, a remoção não mudou nenhum outro número (0 perfis/programas com CORE salvo, logo sem migration de dado). **Falta**: a decisão de prescrição D-1 (o alvo da tabela inclui o indireto?) e o piso de trabalho direto na etapa [3] — ver `trabalho/motor-v3-fatia-8.md` e `propostas/volume-efetivo-PROPOSTA.md`. `AllocationModel.kt`/`AllocationModelTest.kt` (código morto, sem uso em produção) apagados nesta sessão. Antes dela, **fatia 7 (regras de segurança) fechada**: `SafetyRules.kt` (`modoConservadorAutomatico`, `rirMinimo`), `rirMinimoSeguranca` (V67, 8 exercícios aprovados por nome) em `Exercise`/`CatalogFiller`/`MotorV3Pipeline`, espelhado no oráculo `motor_v3.py`; troca de barra pro iniciante e substituições articulares NÃO construídas — satisfeitas pela curadoria/descontinuadas, ver `historico/motor-v3-fatia-7.md`; `ombro_vira_isolamento` convertido para padrão enumerado em `test_motor_v3.py`). Antes dela: motor v3 fatia 6, motor v3 fatia 5, motor v3 fatia 4, motor v3 fatia 3, motor v3 fatia 2, motor v3 fatia 1, notificações offline (cache-first), motor v3 fatia 0, acessórios no onboarding fechadas._

## Agora

| | |
|---|---|
| Última fatia fechada | **Motor v3 — fatia 7 (regras de segurança)**, 2026-10-24. `SafetyRules.modoConservadorAutomatico()`/`rirMinimo()` (espelhados em `motor_v3.py`); `rirMinimoSeguranca` (V67) em `Exercise`, lido por `CatalogFiller`/`MotorV3Pipeline`. **Decisões do Rafael:** troca de barra pro iniciante NÃO construída (especificação manda `BEGINNER` literal; os 8 exercícios aprovados são INTERMEDIATE/ADVANCED — já satisfeita pela curadoria, `[INV]` com 0 violações nos 720); substituições articulares descontinuadas (dependiam de limitação declarada, fora de decisão do motor desde 21/10); rampa era descrição errada no doc, não lacuna de código (overhead já existia desde a Fatia 2). `ombro_vira_isolamento` convertido pra padrão enumerado em `test_motor_v3.py` (tripwire pro foco, Fatia 10). Disparos nos 720 perfis do oráculo: modo conservador 540/720 (iniciante 240, saúde geral 180, 60+/1º mesociclo 360); RIR 2 em 123 perfis, RIR 3 em 540. Achado: convenção de mesociclo divergia entre oráculo e Kotlin desde a Fatia 2 (oráculo não alinhava `mesocicloAtual == null` = 1º) — corrigido, números recalculados em `historico/motor-v3-fatia-4.md`/`fatia-5.md`/`fatia-6.md`. Varredura dos 720 contra catálogo real em Kotlin fica pra Fatia 9. Antes dela: **Motor v3 — fatia 6**, 2026-10-23; **fatia 5**, 2026-10-23; **fatia 4**, 2026-10-23; **fatia 3**, 2026-10-23; **fatia 2**, 2026-10-23; **fatia 1**, 2026-10-23; **Notificações offline (cache-first)**, 2026-10-22; **Acessórios no onboarding**, 2026-10-22; **Motor v3 — fatia 0**, 2026-10-21 |
| Migration no código | **V68** (`V68__core_sai_do_motor.sql`) — aperta `chk_exercises_volume_target` para recusar `'CORE'`, com bloco `DO` que falha alto se alguma linha ainda usar o alvo. Antes dela, **V67** (`V67__rir_minimo_de_seguranca.sql`) — `exercises.rir_minimo_seguranca`, 8 exercícios `true`, autoconferência no fim. V64/V65/V66 **commitadas e mergeadas em `main`** (PR #58, 2026-10-23); V67 local, não commitada |
| Schema local | `9.sqm` (`8.sqm` = acervo do Aprender/Fase 8, `9.sqm` = tabela `notificacao`) |
| Catálogo | **893 exercícios** depois da V65 (922 antes: 29 saíram, duplicados e um aparelho passivo). **147 na lista principal**, com `volume_target`, `prioridade`, `relevancia`, `nivel_maximo` e `ambiente`; família e os seis `requires_*` nos 893 |
| Não commitado | **Mergeado em `main` em 2026-10-23** (PR #58, develop→main): a fatia de notificações offline inteira, os dois achados da bateria (`UserService.kt`/`UserServiceTest.kt` — corrida de login; `AppNavHost.kt` — chave do badge), a curadoria inteira (V64/V65/V66) e toda a fatia de acessórios, em 4 commits separados por fatia. Resta ver `git status` só pra fatia J.5 + Dúvidas Frequentes, de antes, e `tools/` (fora do git por decisão) |
| Achado ao fechar | **2026-10-27, quatro do motor v3:** (0) **o "buraco de catálogo em CASA" que eu mesmo tinha registrado como débito em 26/10 estava mal diagnosticado**: os exercícios existem, o que os tira do pool é o **acessório** — um banco sozinho leva as 239 vagas vazias de CASA a 0 —, e a V66 (22/10) já tinha resolvido isso no produto. Consequência maior, aberta: **a matriz dos 720 testa CASA sempre no pior caso** (zero acessório declarado), então todo número medido sobre ela carrega esse viés, inclusive os 2.079 pares. Decisão de escopo pendente. Na mesma varredura, **CORE** apareceu como o único alvo com pool vazio em todas as combinações — removido do motor e do contrato (V68). Depois dele: (1) o Kotlin nunca aplicou o fator 0,6 de `MAINTAIN` ao **alvo semanal** — só ao teto da sessão —, dando alvo 67% maior que o oráculo em 180/720 perfis; passou porque nenhum teste exercitava `MAINTAIN` no alvo e a varredura dos 720 em Kotlin não existe. (2) `FATOR_OBJETIVO_ALVO` fazia o alvo nascer abaixo do próprio piso em `MAINTAIN` (660 pares) — não era erro de prescrição, era comparar contra o limiar errado: faltava o MV ao lado do MEV. (3) `papel_trocado` (312/720) faz o catálogo **injetar indireto que a prescrição não planejou** — BICEPS +398 séries, FOREARMS +526 —, então um buraco de curadoria vira decisão de prescrição sem passar por ninguém. Os dois primeiros corrigidos; o terceiro é débito. Antes deles: `regras/05-offline.md` dizia que o fallback de cache era em `AppError.Unexpected` — o código (`ProgramRepositoryImpl`, e agora `NotificacoesRepositoryImpl`) sempre checou `AppError.Connection`. Doc corrigida. **Na bateria manual (22/10), dois defeitos tangenciais, não relacionados ao cache-first:** (1) corrida de primeiro login — `GET /me`/`POST /me/devices`/`GET /me/notifications` quase juntos na conta nova bateriam no UNIQUE de `firebase_uid` e caíam em 500; corrigido relendo no conflito em `UserService.findOrCreate`. (2) badge de notificações recarregando em toda navegação, não só troca de aba — `LaunchedEffect` usava a rota exata como chave em vez da aba-raiz; corrigido em `AppNavHost.kt`. Narrativa completa em `historico/notificacoes-offline.md`. Achado anterior (acessórios): `Accessory.kt` prometia no KDoc que `BENCH` satisfaz `ELEVATED_SURFACE` — **nunca foi implementado** no filtro (`containsAll` puro, sem expansão). Resolvido corrigindo o KDoc (decisão do Rafael: não implementar agora), não o filtro. Ver `ARCH #38` |
| Em andamento | **Motor v3 em Kotlin — fatias 1 a 7 fechadas; fatia 8 EM ANDAMENTO**, não fechada. O laço de corte por tempo **foi descartado** — o piso condicional leva `entregue>capacidade` a 0/720 sozinho. Etapa [7] (`ProgramAudit`: `efetivo = direto + indireto` e status por músculo) construída e verde nos dois lados. CORE removido do motor e do contrato (V68). **D-1 decidido** (híbrido, convenção total, coeficientes com fonte, glúteo 8/10/12) e viés da matriz **resolvido por medição** (fica como está; número de produto congelado ao lado). **Falta só dimensionar o piso de trabalho direto**: medido, ele tem de agir sobre **134 casos** (`ATENDIDO` com direto 0: tríceps 88 + glúteo 46) dos 480 perfis INTER/ADV — os outros 172 já são honestos (`MANUTENCAO`). `[REGRA]` do piso já escrita: nunca passa por cima da capacidade. Paralelo, sem relação: **varredura de débitos A–D** (`trabalho/debitos-A-D.md`), A/B/C fechados em 2026-10-09, **D aguardando o Rafael** |
| Motor de geração | **Especificação pronta, fatias 0–3 fechadas, fatias 4–12 por fazer.** A spec vive em `propostas/motor-v3-ESPECIFICACAO.md`; o desenho e as 13 fatias em `trabalho/motor-v3-desenho-e-fatias.md` (corrigido em 23/10: troca de produção pós-Fatia-9, ponto fixo é Fatia 5). O que roda hoje em produção ainda é a geração antiga (`StructureEngine` + `SlotFiller`, `Random(seed)`, casamento por padrão de movimento) — `engine.v3` (`VolumeTargets`, `VolumeIndirectTable`, `CapacityModel`, `PriorityAllocation`) existe em paralelo, sem fiação nenhuma no motor antigo. `tools/motor_sim/` é o oráculo (2 testes vermelhos conhecidos, ambos diferencial GLUTES→POSTERIOR em ADVANCED — pergunta aberta da Fatia 9; `ombro_vira_isolamento` convertido pra padrão enumerado na Fatia 7, verde hoje) e desde 21/10 escolhe pela curadoria (ambiente, acessório, relevância, família, prioridade). `regras/01-motor-de-geracao.md` tem cinco `[INV]` marcados `[VENCIDO]` |
| Próxima fatia | **Motor v3 em Kotlin**, fechar a fatia 8 — **dimensionar o piso de trabalho direto** sobre os 134 casos medidos, dentro do orçamento (`[REGRA]` já escrita). O laço do ponto fixo **continua necessário**: medido, o ciclo não é o grafo de coeficientes (que ficou acíclico) e sim o **orçamento** — `ponto_fixo_oscilou` subiu de 26 para 44/720 depois da contabilidade honesta, porque o antebraço passou a disputar capacidade de verdade. Ver `trabalho/motor-v3-fatia-8.md` e `propostas/volume-efetivo-PROPOSTA.md` |

> ⚠️ **Dois achados da reestruturação de 2026-10-07, ambos esperando sua confirmação:**
>
> 1. **A V61 não estava em documento de estado nenhum.** Painel, Handoff e README paravam na V60;
>    `V61__wiki.sql` está em `server/src/main/resources/db/migration/` desde antes desta sessão, e
>    só aparecia no `adendo-stack.md` e no `contrato-api.md`. O código vence a doc — por isso a
>    tabela acima diz V61.
> 2. **A V58 pagou um débito que continuou listado como aberto.** `V58__prancha_isometrica.sql`
>    existe, e "prancha isométrica ausente" seguia nas pendências do catálogo.

## Fase corrente

A tabela **"Por fase"** do `README.md` é a fonte única — não duplicar aqui.

> ⚠️ **Divergência corrigida em 2026-10-07, confirme:** três documentos discordavam sobre a Fase 6.
> O `README.md` dizia "em curso, faltam D, E e o resto da F"; o Handoff tinha ao mesmo tempo
> "FASE 6 CONCLUÍDA (2026-09-09), todas as fatias entregues (A.0·A·B·C·C.1·D·E.1·E.2·F)" **e** um
> "Próximo passo" listando `#35 → D → E → F` como restantes. Adotei a versão concluída: é a mais
> recente, a mais específica, e as fatias G/H/J fecharam depois dela. O "Próximo passo" antigo foi
> descartado por ser resíduo — está no `historico/` se precisar conferir.

## Suíte — são CINCO tasks

`:konsist:test` · `:server:test` · `:server:integrationTest` (Postgres real, **exige Docker**) ·
`testAndroidHostTest` (módulos KMP) · `:app:androidApp:testDebugUnitTest` (ViewModels do app).

`gradlew` não roda no ambiente do Claude — ele pede, você roda e cola. Contagem atual de testes:
`gerados/testes.md` (gerado), nunca somada à mão.

Em 2026-10-21 rodaram duas, verdes: `:server:test` (575) e `:server:integrationTest` (194). **As outras três
não foram rodadas desde a curadoria** — `:konsist:test`, `testAndroidHostTest` e
`:app:androidApp:testDebugUnitTest`.

> **Suíte que não roda não cobre nada.** Vale para a que exige Docker e para a que o Gradle acha
> que já rodou (`:konsist:test` fica `up-to-date` sem varrer — use `--rerun-tasks` para provar).

## Fechamento de ciclo

Testes verdes → atualizar **este arquivo**, `fontes/debitos.md` e `migrations/INDICE.md` →
`python3 docs/geradores/gen_tudo.py`. O que só você faz: confirmar build verde e o `git push`/merge.

---

## Pendências ativas (demais em `docs/fontes/debitos.md`)

- **`motor-fatia-0-catalogo-e-medicao.md` fechado e apagado em 22/10.** §7 inteiro promovido:
  narrativa e Hand Grip → `historico/`; V64/V65/V66 → `migrations/INDICE.md`; critério de
  casamento (`volume_target`, decidido mas **não implementado** no `SlotFiller`) →
  `[REGRA]`/`[INV]` em `regras/01-motor-de-geracao.md` + `ARCH #39`; fora de escopo (CREF, LGPD,
  Lei 9.696/98, referência do avançado, `ombro_vira_isolamento`, refinamentos futuros) →
  `fontes/debitos.md`. Os 5 achados do andamento de 2026-10-14 (não eram "fora de escopo", eram
  bug/lacuna no caminho) **ainda não triados** — ficaram em `trabalho/debitos-§7.md`, a decidir
  qual é débito real e em qual fatia do motor v3 cada um cai.
  `acessorios-no-onboarding.md` já tinha fechado e sido apagado em 22/10.
  `motor-v3-desenho-e-fatias.md` é o roteiro das fatias 1–12 e fica, é o próximo vivo;
  `motor-lista-principal.md` e `notificacoes-offline.md` são planos antigos, a decidir

- **Dúvidas sem preço/plano, cancelamento, contato de suporte e política de privacidade** — as
  8 perguntas (2026-10-04) cobrem só regra já implementada; os quatro de cima dependem de
  decisão ou dado que só o Rafael tem, não foram esquecidos

- **Notificação de desbloqueio de conquista** — hoje a avaliação só roda na leitura, então o
  usuário descobre a medalha ao abrir a tela. Quando houver notificação, o `POST /sessions`
  precisa avaliar também
- **`stats` + `session` + `achievements` no módulo app** — os três deviam ser features próprias;
  conquistas seguiu o padrão dos irmãos de propósito, para não repartir a gamificação em dois

- **Outbox sem teste de integração** — compactador e processador têm cobertura pura, mas a
  interação outbox × `save()` (preservar pendentes) é exatamente onde estavam os bugs, e só
  tem verificação manual
- **Premium não edita conteúdo de IA offline** — consequência aceita da fronteira #30 × #25
- **Sem listener de conectividade** (⚪ aceito em 2026-10-08) — a fila sobe pelo WorkManager; a tela aberta não reage à rede voltando, só ao entrar, ao puxar ou ao "Tentar de novo"

- ~~`tools/` sem decisão de versionamento~~ — **DECIDIDO (2026-08-23): fora do git**, junto com `exercises.csv` e `gifs_exercicios/`
- `SessionApi`/`SessionSync` no módulo app (atalho) — extrair p/ `session:data`
- `fieldErrors` do nome de programa inalcançável pela UI (`enabled = name.isNotBlank()` barra
  antes) — rede de segurança, não caminho normal
- `/me/profile` sem TTL — decisão consciente (é o gate da Splash), roda de fundo
- Catálogo: nomenclatura de placas, Afundo × Avanço, `MuscleGroup` sem CALVES/TRAPEZIUS/
  LOWER_BACK, prancha isométrica ausente, 74% da base em INTERMEDIATE
- Dedup FUNCIONAL (stiff ↔ terra romeno) — precisa de decisão de produto


## Débitos ⚪ abertos (lista completa em `fontes/debitos.md`)

Re-encodar os 963 MP4 (197 MB, hoje 95% da mídia) · deep link do convite · vocabulário do 409
duplicado entre servidor e `EntrarScreen` · `LocalClipboardManager` deprecado · `integrationTest`
em 3m51s (um container por classe) · ~24 imagens órfãs sem exercício · base da API como `var`
global · copiar `gifs_exercicios/banners/default.jpg` para produção quando houver (dev já resolvido
e verificado em 2026-09-06).

> **Débito só fecha quando alguém verifica, não quando alguém entrega.** O banner ficou duas
> semanas aberto depois de resolvido. A V58 é o mesmo caso pelo outro lado: pagou e ninguém riscou.
