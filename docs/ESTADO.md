# Estado — onde paramos

> **O único documento de leitura obrigatória no início da sessão.** Só mora aqui o que muda a cada
> fatia. O resto se lê quando o assunto exigir: convenções em `CONVENCOES.md`, regra de
> comportamento em `regras/`, decisão em `arch/`, débito em `fontes/debitos.md`, migration em
> `migrations/INDICE.md`, fatia fechada em `historico/`.

_Atualizado em 2026-11-05 (**RIR chega do cliente e acorda a progressão**): fecha a face (a) de "o motor sabe, o app não mostra" (`debitos.md`, P0) — Backend → Contrato → Cliente, três etapas do mesmo dia. RIR por série chega até `session_set_logs.rir` (coluna existia desde a V69, nunca escrita) e `SessionService.record()` passa a chamar `ProgressaoAplicadaService` → `calibrar()`/`decidir()` → `ProgressaoRepository.registrar()` **em produção** (não o v3 dormente). Unificou `Papel`/`SlotRole` num enum só em `shared-contract` (V75, persiste `papel` em `workout_exercises`) e trocou o sinal de `cargaInvertida` em `decidir()` — item **bloqueante** do plano, sem o qual a Barra Fixa Assistida regrediria calada. Cliente: `SetEntry.rir`, `SessionEvent.RirChanged`, `finish()` carregando `rir` no DTO (em branco → `null`, não `0` — D-3), UI com stepper de RIR na execução. **Achado no fechamento**: `WorkoutSessionViewModelTest` não existia, e o primeiro caso travou o processo de teste — `contarOTempoDaSessao()` é um `while (true)` que só o `ViewModelStore` do Android cancelava; `onCleared()` virou `public` e passou a cancelar também esse job, não só o do descanso. `:shared:features:session:presentation:testAndroidHostTest` verde. Narrativa completa em `historico/rir-chega-e-registra.md`. Antes: login sem backend (2026-11-05)._

_Atualizado em 2026-11-05 (**login sem backend**): fecha o débito mais antigo ainda aberto no arquivo. `LoginViewModel.submit()` entrava com a sessão só depois de `fetchMe()` (`GET /me`) responder — backend fora bloqueava o login mesmo com credencial Firebase válida. A leitura do código derrubou a cautela do próprio débito (*"`fetchMe` garante a linha em `users`"* — falso, `findOrCreate()` já roda, idempotente, em toda rota autenticada) e achou que a Splash já tinha a resiliência que faltava (`prepararAntesDaHome` → `me.sincronizar()` com timeout). Fix: entra com o `uid` do Firebase direto no sucesso do `signIn`/`signUp`. **Segundo defeito achado na mesma investigação, reproduzido pelo Rafael no aparelho**: `signIn()`/`signUp()` podiam deixar `auth.currentUser` vivo numa chamada que o app relatou como FALHA (offline) — próxima abertura, já online, caía direto na Home sem o login ter acontecido de verdade. `mapAuthError` (agora `suspend`) chama `signOut()` defensivo antes de devolver o erro. `LoginViewModelTest` criado (não existia nenhum teste do ViewModel); `:shared:features:auth:presentation:testAndroidHostTest` 2/2 e `:app:androidApp:testDebugUnitTest` 204/204 verdes. Narrativa em `historico/login-sem-backend.md`. Antes: catálogo: incremento e semântica de carga (2026-11-05)._

_Atualizado em 2026-11-05 (**catálogo: incremento e semântica de carga**): nascem os três campos que `Progressao` exige e que **não existiam em lugar nenhum do código** — `incremento`, `barra_livre` e `carga_invertida` (V74, derivados de `equipment`, com autoconferência e três CHECKs). A fatia saiu de dentro do `rir-plano.md`, cuja pergunta 1 presumia que o dado existia. Dois achados pesados: a **Barra Fixa Assistida tem carga invertida** e o motor a facilitaria conforme a pessoa fica mais forte (flag criada aqui; a troca de sinal é item **bloqueante** da fatia do RIR), e **42 de 147 do pool (28,6%) não têm progressão nenhuma**, com 4 das 26 âncoras do avançado estruturalmente mortas — dois P0 separados. Duas Leis medidas viraram critério de aceite em `regras/01` (o loop quebra a ~20% da carga; o freio da Regra 4 só age acima de ~12,5× o incremento). `:server:integrationTest` 8/8 e `:server:test` 814/814 verdes. Narrativa em `historico/catalogo-incremento-e-semantica-de-carga.md`. Antes: design system + correção do tonal genérico (2026-11-03)._

_Atualizado em 2026-11-03 (**design system + correção do tonal genérico**): nasce o `DESIGN_SYSTEM.md`, a referência de cor, tipografia, componentes, ícones e os fluxos do usuário, com o que é lei e o que é livre tela a tela. Dele saiu um defeito real: `secondaryContainer` era o roxo de IA, e o Material 3 usa esse slot como cor tonal GENÉRICA, então **13 arquivos herdavam dele** (os `-`/`+` da sessão, o chip da série, a trilha de progresso). Já tinha sido achado uma vez e remendado numa tela só. Agora é neutro, e quem quer significado pede `primaryContainer` explicitamente ([REGRA] nova em `regras/15-design-e-cor.md`, narrativa em `historico/correcao-do-tonal-generico.md`). Três suítes verdes, verificado no aparelho. 20 achados de layout entraram em `debitos.md`. Antes: declaração de custo do motor (2026-10-31)._

_Atualizado em 2026-10-31 (**declaração de custo do motor**): fecha (c)/(d) de "o motor sabe, o app não mostra" (`debitos.md`, P0). `ProgramAudit.Causa` ganha `MANUTENCAO`; `VolumeTargets.custoDoFoco()` declara os grupos reduzidos ao piso pela fase de ênfase. Chegam ao `ProgramDto` (`avisos`, `grupamentosReduzidosPeloFoco`), persistidos como snapshot nos dois lados (servidor: V73; cliente: `10.sqm`) e aparecem num card em `ProgramDetailScreen` que some sozinho quando vazio. **Fica dormente**: 100% aditivo, `WorkoutGenerator` de produção não mudou — ninguém vê nada até o v3 virar o gerador de produção (decisão separada). As cinco suítes voltaram a verde depois de três rodadas de correção (arquivo de teste corrompido entre sessões, apóstrofo sem escapar no catálogo em inglês, `stringResource` dentro de `joinToString` não-inline, valor de string novo colidindo com um existente) — narrativa completa em `historico/declaracao-de-custo-do-motor.md`. Antes: service/rota da promoção do avançado (2026-10-31, mesmo dia), V72._

## Agora

| | |
|---|---|
| Última fatia fechada | **RIR chega do cliente e acorda a progressão**, 2026-11-05. Backend → Contrato → Cliente: RIR por série até `session_set_logs.rir`, `SessionService.record()` ligado a `ProgressaoAplicadaService` em produção, unificação `Papel`/`SlotRole` (V75), troca de sinal de `cargaInvertida` (bloqueante), e no cliente `SetEntry.rir`/`RirChanged`/UI. Achado no fechamento: `onCleared()` virou `public` (era o único jeito de o teste novo cancelar o `while(true)` do cronômetro, que travava o processo de teste). `:shared:features:session:presentation:testAndroidHostTest` verde. Detalhe em `historico/rir-chega-e-registra.md`. Antes: **Login sem backend**, 2026-11-05. `LoginViewModel.submit()` entrava só depois de `fetchMe()` responder — backend fora bloqueava login com credencial válida; a leitura do código achou que `findOrCreate()` já roda em toda rota (não só `/me`) e que a Splash já sincroniza `/me` com timeout depois. Fix: entra com o `uid` do Firebase direto. **Segundo defeito, reproduzido pelo Rafael no aparelho**: `signIn`/`signUp` podiam deixar `auth.currentUser` vivo numa chamada relatada como falha (offline) — `mapAuthError` agora `suspend`, chama `signOut()` defensivo. `LoginViewModelTest` novo (não existia nenhum); `testAndroidHostTest` 2/2, `:app:androidApp:testDebugUnitTest` 204/204. Detalhe em `historico/login-sem-backend.md`. Antes: **Catálogo: incremento e semântica de carga**, 2026-11-05. Cria `exercises.incremento`/`barra_livre`/`carga_invertida` (V74) — os três que `decidir()`/`calibrar()` exigem e que não existiam. Entregou: 3 colunas derivadas de `equipment` (322 com 2,5 kg, 139 com 1,0 **por halter**, 432 NULL, 97 barra livre, 4 carga invertida), 3 CHECKs (o `chk_exercises_barra_livre` é `[INV]` no banco), autoconferência com `RAISE EXCEPTION`, e `SemanticaDeCargaIntegrationTest` (8 testes, 5 caminhos de falha). Contrato e cliente: nada, por desenho. Detalhe em `historico/catalogo-incremento-e-semantica-de-carga.md`. Antes: **Declaração de custo do motor**, 2026-10-31. Fecha (c)/(d) de "o motor sabe, o app não mostra" (P0). Entregou: `ProgramAudit.Causa.MANUTENCAO`, `VolumeTargets.custoDoFoco()` (reusa a isenção BACK/EMPURRAR extraída pra `isentoDoPisoPorFoco()`), `ProgramDto.avisos`/`grupamentosReduzidosPeloFoco` (default `emptyList()`, 100% aditivo), persistência nos dois lados (servidor V73, cliente `10.sqm` — achado durante o desenho, não previsto no plano original), card `AvisosDoPrograma` em `ProgramDetailScreen`, `Rotulos.kt` (`rotuloDoMusculoAlvo`/`rotuloDaCausaDeAviso`). Fica dormente até o v3 virar o gerador de produção. Três achados de fechamento (arquivo de teste corrompido entre sessões, apóstrofo sem escapar, `stringResource` em `joinToString` não-inline, string nova colidindo com uma existente) corrigidos antes do build verde. Antes: service/rota do avançado (2026-10-31, mesmo dia). Detalhe em `historico/declaracao-de-custo-do-motor.md` |
| Migration no código | **V75** — persiste `papel` (enum unificado `Papel`/`SlotRole`) em `workout_exercises`, nullable. Antes dela, **V74** (`V74__incremento_e_semantica_de_carga.sql`) — SCHEMA + DADO, 3 colunas em `exercises`, não espelhada no cliente (nada no cliente consome). É migration de DADO, então pede `docs/migration_verify/` quando rodar contra o banco de desenvolvimento real. Antes dela, **V73** (`V73__declaracao_de_custo_do_programa.sql`) — 2 colunas em `programs` (`avisos`, `grupamentos_reduzidos_pelo_foco`, JSON-em-TEXT, nullable, mesmo padrão de `focus_muscles`/V56). Espelhada no cliente pela **`10.sqm`** (SQLDelight, mesmo par de colunas). Antes dela, **V72** (`um_rm_kg`), **V71** (`mesociclo`) e **V70** (`promotion_decline`). V64–V73 **já commitadas** — conferido em 2026-11-05 com `git ls-files`, acha as 10; este campo dizia o contrário desde 31/10. Só a **V74** está fora do git |
| Schema local | `10.sqm` (`8.sqm` = acervo do Aprender/Fase 8, `9.sqm` = tabela `notificacao`, `10.sqm` = `program.avisos`/`grupamentosReduzidosPeloFoco`) |
| Catálogo | **893 exercícios** depois da V65 (922 antes: 29 saíram, duplicados e um aparelho passivo). **147 na lista principal**, com `volume_target`, `prioridade`, `relevancia`, `nivel_maximo` e `ambiente`; família e os seis `requires_*` nos 893. **V74**: `incremento`, `barra_livre` e `carga_invertida` nos 893, derivados de `equipment` |
| Não commitado | **Nada** da fatia "rir-chega-e-registra": `6ad9b2a` (RIR até o banco), `07c02bf` (progressão ligada a `record()`) e `95fcdc6` (cliente: `SetEntry.rir`/`RirChanged`/UI/teste) já estão em `develop`. Antes: "Login sem backend" (`LoginViewModel`, `FirebaseAuthRepository`, `LoginViewModelTest` novo, `build.gradle.kts` do módulo, e os docs do fechamento) vai commitada em `develop` ao fechar esta fatia. A **fatia do catálogo** (V74 e o resto, `9ae3a57`) e a correção do `gen_debitos.py`/`gen_docs.py` (`28ab426`, achada ao regenerar os PDFs desta sessão) já estão commitadas. Falta só o `git push`, que é do Rafael — `No such device or address` ao autenticar no GitHub por este shell, não é bloqueio de permissão. Antes: **a correção do tonal genérico** (`Theme.kt`, `WorkoutSessionScreen.kt`), o `docs/DESIGN_SYSTEM.md` novo, `regras/15-design-e-cor.md`, `historico/correcao-do-tonal-generico.md` e os 20 achados em `debitos.md` — três suítes verdes em 2026-11-03. A fatia **"declaração de custo do motor"** NÃO está mais aqui: foi commitada em 31/10 (`173157a`, `9f85aac`, `f4e7823`) — este campo a listava como pendente por engano. E **`docs/` NÃO está fora do git** (medido em 2026-11-05): só `docs/pdf/` é ignorado (`.gitignore:77`); todo o resto é rastreado, **inclusive `trabalho/`**, que tem 7 arquivos commitados (`MODELO`, `README`, `debitos-A-D`, `debitos-§7`, `motor-lista-principal`, `motor-v3-desenho-e-fatias`, `rir-plano`). O que aparece como `??` é novo, não ignorado. Consequência para a limpeza: apagar arquivo **nunca commitado** é definitivo; apagar um já commitado é recuperável com `git checkout` |
| Achado ao fechar | **2026-10-29, Fatia 11:** (1) `Oraculo.catalogo()` (teste Kotlin) nunca lia `movement_pattern` do CSV — fábrica de teste com default silencioso, invisível até `WeeklyBackSelection` precisar do campo; causa dos 1680/1680 perfis divergindo na varredura. (2) dois lados concordando não é prova, de novo: as outras 6 falhas eram expectativa congelada antes do piso de ênfase existir, não bug do motor. (3) a fase move `ombro_vira_isolamento` de 16 para 136 — efeito esperado, não regressão. (4) restrição de volume puxar×empurrar foi medida e rejeitada — o eixo certo era seleção, não volume. (5) scripts de medição ad hoc em `tools/cache/` violavam a própria regra do `tools/README.md` — movidos para `tools/motor_sim/` |
| Em andamento | **Nenhuma fatia do motor/RIR** — fechada (ver linha acima e `historico/rir-chega-e-registra.md`). Sem relação: **varredura de débitos A–D** (`trabalho/debitos-A-D.md`), A/B/C fechados em 2026-10-09, **D aguardando o Rafael** |
| Motor de geração | **Especificação pronta, fatias 0–11 fechadas, 12 por fazer.** A spec vive em `propostas/motor-v3-ESPECIFICACAO.md`; o desenho e as fatias em `trabalho/motor-v3-desenho-e-fatias.md`. O que roda hoje em produção ainda é a geração antiga (`StructureEngine` + `SlotFiller`, `Random(seed)`) — `engine.v3` existe em paralelo, sem fiação. `server/src/test/oracle/` é o oráculo (94 testes verdes) e escolhe pela curadoria. **Condição da troca de produção: FECHADA nesta fatia** — a proteção contra sacrifício existe agora, o risco de foco permanente sem proteção não se aplica mais; LIGAR continua decisão sua. **Aberto, mas não bloqueante:** a "fase de 2 mesociclos" da ênfase estava no plano original da Fatia 11 e não foi implementada — o foco continua permanente (`fontes/debitos.md`) |
| Próxima fatia | **Fatia 12 — questionário e triagem** (`trabalho/motor-v3-desenho-e-fatias.md`). A fatia "rir-chega-e-registra" fechou de ponta a ponta (Backend → Contrato → Cliente) nesta sessão; o que ficou de fora dela (UI mostrando o resultado da progressão, medir o disparo real em produção, (b) avançado sem service/rota) está nomeado em `historico/rir-chega-e-registra.md` e em `fontes/debitos.md`, não é trabalho pendente desta fatia |

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

Em 2026-10-31 rodaram as cinco, verdes (confirmado pelo Rafael), duas vezes no mesmo dia:
primeiro pra "service/rota da promoção do avançado" (`:konsist:test`, `:server:test`,
`testAndroidHostTest`, `:app:androidApp:testDebugUnitTest`, `:server:integrationTest` com **213**,
depois de corrigir Docker fora do ar e a fixture de data absoluta do `CheckInIntegrationTest` —
ver `historico/promocao-avancado.md`); depois pra "declaração de custo do motor", em três rodadas
de correção (`:server:test` e `:app:androidApp:testDebugUnitTest` só, já que as outras três não
tocavam código afetado — ver `historico/declaracao-de-custo-do-motor.md`).

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
