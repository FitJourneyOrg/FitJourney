# Estado — onde paramos

> **O único documento de leitura obrigatória no início da sessão.** Só mora aqui o que muda a cada
> fatia. O resto se lê quando o assunto exigir: convenções em `CONVENCOES.md`, regra de
> comportamento em `regras/`, decisão em `arch/`, débito em `fontes/debitos.md`, migration em
> `migrations/INDICE.md`, fatia fechada em `historico/`.

_Atualizado em 2026-11-07 (**modo leve -- resto da Fatia 12, cliente**): sétimo recorte,
terceiro do lado cliente. `LightModeStep` (`QuizSteps.kt`, sim/não) entra em
`QuizStep.LIGHT_MODE`, logo depois de TIME_OFF. Diferente das perguntas de nível/minutos,
`preferLightMode` no `QuizState` é `Boolean` não-nulo (default `false`) -- é preferência, não
condição (especificação, 3ª rodada: "guarda a escolha, não a condição"), e o passo nunca
bloqueia o avanço. `Profile`/`ProfileMapper`/`QuizViewModel` ganham o campo, mesmo padrão
aditivo. Continua dormente -- `MotorV3Pipeline.Entrada` sem caller. Strings novas
(`quiz_modo_leve_*`, 4 chaves) nos dois idiomas. `ProfileMapperTest`/`QuizStateTest` novos;
`:shared:features:profile:data`/`:presentation` e `:app:androidApp:testDebugUnitTest` verdes
(confirmado pelo Rafael). Narrativa em `historico/modo-leve-no-cliente.md`. Falta do resto da
Fatia 12: só o botão de equipamento, no cliente. Antes: minutos disponíveis -- resto da Fatia
12, cliente (2026-11-07, mesmo dia)._

_Atualizado em 2026-11-07 (**minutos disponíveis -- resto da Fatia 12, cliente**): sexto
recorte, segundo do lado cliente. `MinutesAvailableStep` (`QuizSteps.kt`, radio-list, mesmo
estilo das perguntas de nível fechadas horas antes) entra em `QuizStep.MINUTES`, logo depois
de DAYS. `Profile` (domínio)/`ProfileMapper`/`QuizState`/`QuizViewModel` ganham o campo, mesmo
padrão aditivo de sempre. Continua dormente -- nenhuma rota chama `CapacityModel.tetoPorTempo`
ainda. Strings novas (`quiz_minutos_titulo` + 4 `enum_minutos_*`) nos dois idiomas.
`ProfileMapperTest`/`QuizStateTest` novos; `:shared:features:profile:data`/`:presentation` e
`:app:androidApp:testDebugUnitTest` verdes (confirmado pelo Rafael, depois de reiniciar o
daemon do Kotlin -- `Could not delete .../build/classes/kotlin/android/main`, lock de arquivo
do Windows sem relação com o código). Narrativa em
`historico/minutos-disponiveis-no-cliente.md`. Falta do resto da Fatia 12: modo leve e botão
de equipamento, no cliente. Antes: nível deixa de ser escolha livre -- resto da Fatia 12,
cliente (2026-11-07, mesmo dia)._

_Atualizado em 2026-11-07 (**nível deixa de ser escolha livre -- resto da Fatia 12, cliente**):
quinto recorte, primeiro do lado cliente. `LevelStep` saiu do onboarding Android;
`TrainingConsistencyStep`/`TimeOffTrainingStep` (`QuizSteps.kt`, radio-list como o
`EnvironmentStep`) perguntam as duas, e `QuizState.levelDerivado` chama o MESMO `nivelDerivado()`
puro do `shared-contract` que o servidor usa (fallback de UI -- servidor continua recalculando
sempre). `Profile` (domínio, cliente) e `ProfileMapper` ganharam os dois campos novos; modo
edição (`QuizViewModel.startEditing`) carrega os dois como `null` pra quem já tinha perfil salvo
antes desta fatia -- responde de novo, uma vez, esperado. Strings novas: `quiz_consistencia_*`,
`quiz_parado_*`, 8 rótulos de opção, nos dois idiomas; `quiz_nivel_titulo` saiu (órfã).
**Achado de novo**: comentário com `--` dentro de `<!-- -->` no `strings.xml` -- MESMO defeito
da fatia "casa em breve", poucas horas antes; trocado por `;`. `ProfileMapperTest`/
`QuizStateTest` novos; `:shared:features:profile:data`/`:presentation` e
`:app:androidApp:testDebugUnitTest` verdes (confirmado pelo Rafael). Narrativa em
`historico/nivel-no-cliente.md`. Falta do resto da Fatia 12: minutos disponíveis, modo leve,
botão de equipamento, todos no cliente -- `BodyLimitation` em pausa. Antes: "casa (em breve)"
na tela -- resto da Fatia 12, recorte 4 (2026-11-07, mesmo dia)._

_Atualizado em 2026-11-07 (**"casa (em breve)" na tela -- resto da Fatia 12, recorte 4**):
quarto recorte. `EnvironmentStep` (QuizSteps.kt) restringe as opções visíveis a
`AMBIENTES_DISPONIVEIS = {ACADEMIA}` -- CASA some como opção clicável, fica esmaecida com
sufixo "(em breve)"/"(coming soon)" (`quiz_ambiente_em_breve`, string nova nos dois idiomas). Só
UI -- `TrainingEnvironment.CASA` continua existindo sem gate nenhum, não afeta o 1 usuário real
medido. **Achado**: comentário novo no `strings.xml` usou `--` dentro de `<!-- -->` -- XML não
aceita, `gen_strings.py` falhou com `ExpatError` até eu trocar por `;`. Sem teste novo (Compose
não é testado neste projeto); `CatalogoDeStringsTest`/`CatalogoEmInglesTest` existentes cobrem a
string. `:app:androidApp:testDebugUnitTest` -- a confirmar pelo Rafael. Narrativa em
`historico/casa-em-breve.md`. Falta do resto da Fatia 12: só o cliente inteiro (as 4 perguntas
novas + botão de equipamento). Antes: botão "não tenho este equipamento" -- resto da Fatia 12,
recorte 3 (2026-11-07, mesmo dia)._

_Atualizado em 2026-11-07 (**botão "não tenho este equipamento" -- resto da Fatia 12, recorte
3**): terceiro recorte. `Accessory.paraAmbiente` tinha um bug de produto: ACADEMIA sempre
devolvia os seis, **ignorando** qualquer lista declarada (a decisão anterior "`accessories !=
null` já basta" estava errada -- os dois chamadores de produção normalizavam `?: emptyList()`
antes de chamar `paraAmbiente`, que por sua vez nem olhava o parâmetro pra ACADEMIA). Corrigido:
`null` continua assumindo os seis (fallback aditivo, ninguém manda outra coisa hoje); lista
explícita (mesmo vazia) agora é respeitada nos dois ambientes -- é o que o botão declara. **Não
dormente**: os dois chamadores de produção (`DeterministicWorkoutGenerator`,
`ExerciseRoutes.alternatives`) já valem. Sem campo novo, sem migration. **Achado ao arrumar os
testes**: `MotorV3Varredura720Test` chamava `paraAmbiente(ambiente, emptyList())` pra ACADEMIA
também -- sob a semântica nova isso teria zerado o pool da academia na varredura; trocado para
`null`, preserva o número de hoje. `:server:test`/`:server:integrationTest` -- a confirmar pelo
Rafael. Narrativa em `historico/nao-tenho-este-equipamento.md`. **`BodyLimitation`: decisão do
Rafael -- NÃO remover** (perguntei, ele cogitou remover e depois recuou: "esqueça não é pra
remover"); fica em pausa, sem a lógica de substituição (que não existe em código nenhum). Falta
do resto da Fatia 12: só "casa (em breve)" + cliente inteiro. Antes: modo leve -- resto da Fatia
12, recorte 2 (2026-11-07, mesmo dia)._

_Atualizado em 2026-11-07 (**modo leve -- resto da Fatia 12, recorte 2**): segundo recorte do
"resto da Fatia 12". `ProfileDto.preferLightMode: Boolean?` (V80, aditiva) -- mesmo padrão das
fatias anteriores. `SafetyRules.modoConservadorAutomatico` ganha `preferiuModoLeve: Boolean =
false` (default preserva todo caller existente) e um 4º gatilho: dispara só no 1º mesociclo
(`CapacityModel.primeiroMesociclo`), igual ao gatilho de idade 60+ -- é a regra "modo leve dura 1
mesociclo" da especificação. **Dormente**: `MotorV3Pipeline.Entrada` continua sem caller.
`:server:test`/`:server:integrationTest` -- a confirmar pelo Rafael. Narrativa em
`historico/modo-leve.md`. Falta do resto da Fatia 12: `BodyLimitation` remapeado, botão de
equipamento, cliente inteiro. Antes: minutos disponíveis -- resto da Fatia 12, recorte 1
(2026-11-07, mesmo dia)._

_Atualizado em 2026-11-07 (**minutos disponíveis -- resto da Fatia 12, recorte 1**): primeira
fatia do "resto da Fatia 12" (`fontes/debitos.md`). `MinutesAvailable` (shared-contract, V79,
aditiva) -- 4 faixas (45/60/75/90+), cada uma com o minuto representativo. `ProfileDto`/`Profile`/
tabela/mapper ganharam o campo, mesmo padrão aditivo de `trainingConsistency`/`timeOffTraining`.
`CapacityModel.tetoPorTempo` (Fatia 2 do motor v3) já existia e não mudou -- só ganhou de onde
vir o `minutos`; continua **dormente**, nenhuma rota chama o pipeline v3. `:server:integrationTest`
cobre o round-trip real -- a confirmar pelo Rafael. Narrativa em
`historico/minutos-disponiveis.md`. Falta do resto da Fatia 12: modo leve, `BodyLimitation`
remapeado, botão de equipamento, cliente inteiro. Antes: mostrar sugestão de nível na tela de
execução (2026-11-07, mesmo dia)._

_Atualizado em 2026-11-07 (**mostrar sugestão de nível na tela de execução**): fecha o débito
pequeno "mostrar sugestão de nível (BAND/HAND_GRIP)" aberto na fatia "progressão por nível"
(2026-11-06), mesmo padrão já fechado pra carga/reps. `SugestaoDeProgressao.calcular` ganha o 3º
eixo: `equipment in {BAND, HAND_GRIP}` devolve a faixa original de `PrescricaoV3.de(goal, papel)`
direto, sem Estado (a faixa nunca muda nesse eixo -- só `nivelAtual`, abstrato, sobe/desce).
**Achado ao investigar**: a cadeia cliente (`WorkoutExerciseDto` -> `WorkoutMapper.comSugestao()`
-> `WorkoutSessionViewModel`/`State` -> `WorkoutSessionScreen`) já era 100% genérica -- a UI
decide pelo campo não-nulo (`cargaSugeridaKg` XOR `repsMin/MaxSugerido`), não pelo eixo -- então o
débito era muito menor do que o texto registrado sugeria; zero mudança de cliente, só um
comentário desatualizado corrigido. Sem migration. `:server:test` verde (confirmado pelo Rafael).
Narrativa em `historico/sugestao-de-nivel.md`. Antes: nível derivado e fim do gate de saúde
(2026-11-07, mesmo dia)._

_Atualizado em 2026-11-07 (**nível derivado e fim do gate de saúde**): primeiro recorte da
"Fatia 12 — questionário e triagem" (`trabalho/motor-v3-desenho-e-fatias.md`) -- as duas
mudanças que afetam a geração ANTIGA, em produção, hoje; o resto (minutos→teto, modo leve,
`BodyLimitation` remapeado, cliente) fica para fatias seguintes, alimenta só o motor v3 (sem
fiação nenhuma em rota ainda). `Level` deixa de ser autodeclarado: `nivelDerivado(trainingConsistency,
timeOffTraining)` (shared-contract, pura) -- iniciante se nunca treinou/treina há <6 meses OU
parou há >6 meses, senão intermediário (**nunca avançado pelo cadastro**). **Fallback aditivo**:
`ProfileService.saveProfile` só deriva quando os dois campos novos vêm preenchidos; cliente de
produção hoje (que ainda não pergunta as duas) cai no `level` que ele mesmo mandou -- sem
regressão. **Gate de saúde (PAR-Q) removido** de `/programs/generate` -- decisão já ratificada
na spec (3ª rodada): autoavaliação sem armazenar, nunca bloqueia. Extraído pra
`ProgramGerarGate.avaliar()` (pura), só resta o gate de idade (#24). `ProfileDto.health`/coluna
`health_screening` ficam mortos, não apagados (débito próprio). V78, aditiva. Medido no banco
real antes de decidir: `CASA` tem 1 usuário (não desliga), `limitations` tem 0 linhas em
qualquer região (remapear `BodyLimitation` sem risco, fatia futura). **Dois achados de
compilação**: smart-cast cross-módulo não funciona em propriedade de outro módulo mesmo com
null-check (corrigido com `val` local); 4 fixtures de teste que montam `Profile(...)` direto
precisaram que os 2 campos novos tivessem default `null` também no model do server, não só no
DTO. `:konsist:test`/`:server:test`/`:server:integrationTest` verdes (confirmado pelo Rafael).
Narrativa em `historico/nivel-derivado-e-fim-do-gate-de-saude.md`. Antes: progressão por nível,
BAND/HAND_GRIP (2026-11-06)._

_Atualizado em 2026-11-06 (**progressão por nível, BAND/HAND_GRIP**): fecha "progressão inexistente para BAND/HAND_GRIP" (`debitos.md`, P0) -- reabre e descarta a premissa de que precisava modelar o kit do usuário primeiro. Decisão do Rafael: **nível RELATIVO, sem modelar o kit** (mesmo truque do eixo de reps pra `BODYWEIGHT`). `ProgressaoPorNivel` (novo): a faixa de reps (`PrescricaoV3.Faixa`) fica FIXA -- é o `nivelAtual` que sobe/desce, nunca abaixo de 1, sem significado absoluto (app nunca sabe qual elástico é). V77, aditiva (`exercise_progression.nivel_atual`, `progression_event.tipo` ganha `_NIVEL`). `AncoraDoIniciante.Evento.Nivel` -- terceiro eixo contando igual aos outros dois pro contador de estagnação. **Achado não relacionado, corrigido**: `FakeProgressaoRepository.registrarPorRepeticao` zerava `sinalForca` incondicionalmente (desatualizado desde a fatia anterior, sem efeito até agora). Sem contrato/cliente -- "mostrar sugestão de nível na tela" vira débito próprio, mesmo padrão dos outros dois eixos. `:server:test`/`:server:integrationTest` verdes (confirmado pelo Rafael). Narrativa em `historico/progressao-por-nivel.md`. Antes: âncoras do avançado bodyweight (2026-11-06, mesmo dia)._

_Atualizado em 2026-11-06 (**âncoras do avançado bodyweight**): fecha "4 de 26 âncoras do avançado estruturalmente mortas" (`debitos.md`, P0, medido 2026-11-05) -- bug de funcionalidade existente, não feature ausente, afetava quem treina em academia também. Barra Fixa/Flexão/Mergulho são `BODYWEIGHT` sem `carga`, então `estimarUmRm()` nunca rodava pra elas e caíam em DADO INSUFICIENTE pra sempre no `diagnosticoAvancadoPorAncora`. `ProgressaoPorRepeticao.estimarSinalDeForca()` (reps+RIR da melhor série -- não é 1RM em kg, Epley não se aplica sem carga) dá o sinal comparável entre mesociclos. **Decisão de implementação**: grava na MESMA coluna `um_rm_kg` que o eixo de carga usa (não coluna nova) -- `ProgressaoRepositoryImpl.buscar()`/`primeiroUmRmNoMesociclo()` já liam essa coluna de qualquer linha e `PromotionService` já consumia `.umRm` sem saber de eixo, então zero mudança em `PromotionService`/`PromocaoDeNivel`/contrato/cliente. Zero migration (colunas já existiam, nullable, V69/V72). Exceção documentada em `regras/01-motor-de-geracao.md` ao comentário da V76 ("nunca os dois conjuntos de colunas ao mesmo tempo"). `:server:test`/`:server:integrationTest` verdes (confirmado pelo Rafael). Narrativa em `historico/ancoras-avancado-bodyweight.md`. Antes: sugestão de progressão na tela de execução (2026-11-06, mesmo dia)._

_Atualizado em 2026-11-06 (**sugestão de progressão na tela de execução**): fecha o débito "mostrar sugestão na tela" aberto na fatia anterior (afetava os dois eixos igual). `GET /workouts/{id}` ganha um pós-processamento igual ao `comOrigem()` — `SugestaoDeProgressao` (núcleo puro) decide `cargaSugeridaKg`/`repsMinSugerido`/`repsMaxSugerido` em cada `WorkoutExerciseDto`, via `SugestaoDeProgressaoService`. **Decisão de propósito**: falha aqui NUNCA quebra o `GET` — sem `Profile`/papel/catálogo/estado, o exercício fica com os três campos `null`, diferente de `comOrigem()` que propaga falha. Eixo de carga sem calibração → vazio; eixo de repetição na 1ª exposição → sugere a faixa original da prescrição (não existe calibração por reps). Cliente: `WorkoutExercise` (domínio) → `SetEntry`/`ExercicioEmExecucao` → texto "Meta: X kg"/"Meta: X-Y reps" no `CartaoDoExercicio`. Sem migration — cache local serializa o DTO inteiro em JSON, os três campos atravessam sem mudança de schema. **Achados ao fechar, sem relação com a fatia, ambos corrigidos**: `Semear.kt` semeava `category = "STRENGTH"` (não existe mais no enum — trocado por `"CORE"`; causa raiz: `exercises` nunca é truncada entre testes, então a linha ruim sobrevivia e quebrava qualquer teste que lesse a tabela inteira via `findAll()`; `:server:integrationTest` verde depois); `sessao_rir_rotulo` faltava na allowlist de "chaves idênticas" do catálogo em inglês (corrigido). Narrativa em `historico/sugestao-de-progressao.md`. Antes: progressão por repetição (2026-11-06, mesmo dia)._

_Atualizado em 2026-11-06 (**progressão por repetição**): fecha a face BODYWEIGHT de "progressão inexistente para quem treina em casa" (`debitos.md`, P0, medido na fatia do catálogo). `ProgressaoPorRepeticao` (novo, paralelo a `Progressao`) assume quando `incremento == null` e `equipment == BODYWEIGHT`: o sinal de esforço vira a faixa de reps (±1 nos dois limites, piso na prescrição original, teto 20). Sem calibração — a faixa original já é estado válido da 1ª exposição. **Achado no meio do caminho**: `AncoraDoIniciante.semSubirNoFim`/`PromocaoDeNivel.diagnosticoIniciante` só reconheciam `Evento.Carga` pro streak de SOBE — sem ajuste, bodyweight cairia em DELOAD falso a cada 3 sessões. Decisão do Rafael: unificar (`Evento.Repeticao` + `.subiu`, os dois eixos contam igual — "estagnação é ausência de progressão de estímulo, independente da variável"). **Segundo achado, ao planejar o Contrato**: nenhum dos dois eixos (nem o de carga, que já existe desde 2026-10-28) expõe a sugestão de progressão pro cliente — a fatia fecha no servidor, em paridade real, e "mostrar sugestão na tela" virou débito próprio que afeta os dois eixos. BAND/HAND_GRIP saem desta fatia (débito separado — falta modelar o kit do usuário). `V76`, aditiva. `:server:test`/`:server:integrationTest` verdes. Narrativa completa em `historico/progressao-por-repeticao.md`. Antes: RIR chega do cliente e acorda a progressão (2026-11-05)._

_Atualizado em 2026-11-05 (**RIR chega do cliente e acorda a progressão**): fecha a face (a) de "o motor sabe, o app não mostra" (`debitos.md`, P0) — Backend → Contrato → Cliente, três etapas do mesmo dia. RIR por série chega até `session_set_logs.rir` (coluna existia desde a V69, nunca escrita) e `SessionService.record()` passa a chamar `ProgressaoAplicadaService` → `calibrar()`/`decidir()` → `ProgressaoRepository.registrar()` **em produção** (não o v3 dormente). Unificou `Papel`/`SlotRole` num enum só em `shared-contract` (V75, persiste `papel` em `workout_exercises`) e trocou o sinal de `cargaInvertida` em `decidir()` — item **bloqueante** do plano, sem o qual a Barra Fixa Assistida regrediria calada. Cliente: `SetEntry.rir`, `SessionEvent.RirChanged`, `finish()` carregando `rir` no DTO (em branco → `null`, não `0` — D-3), UI com stepper de RIR na execução. **Achado no fechamento**: `WorkoutSessionViewModelTest` não existia, e o primeiro caso travou o processo de teste — `contarOTempoDaSessao()` é um `while (true)` que só o `ViewModelStore` do Android cancelava; `onCleared()` virou `public` e passou a cancelar também esse job, não só o do descanso. `:shared:features:session:presentation:testAndroidHostTest` verde. Narrativa completa em `historico/rir-chega-e-registra.md`. Antes: login sem backend (2026-11-05)._

_Atualizado em 2026-11-05 (**login sem backend**): fecha o débito mais antigo ainda aberto no arquivo. `LoginViewModel.submit()` entrava com a sessão só depois de `fetchMe()` (`GET /me`) responder — backend fora bloqueava o login mesmo com credencial Firebase válida. A leitura do código derrubou a cautela do próprio débito (*"`fetchMe` garante a linha em `users`"* — falso, `findOrCreate()` já roda, idempotente, em toda rota autenticada) e achou que a Splash já tinha a resiliência que faltava (`prepararAntesDaHome` → `me.sincronizar()` com timeout). Fix: entra com o `uid` do Firebase direto no sucesso do `signIn`/`signUp`. **Segundo defeito achado na mesma investigação, reproduzido pelo Rafael no aparelho**: `signIn()`/`signUp()` podiam deixar `auth.currentUser` vivo numa chamada que o app relatou como FALHA (offline) — próxima abertura, já online, caía direto na Home sem o login ter acontecido de verdade. `mapAuthError` (agora `suspend`) chama `signOut()` defensivo antes de devolver o erro. `LoginViewModelTest` criado (não existia nenhum teste do ViewModel); `:shared:features:auth:presentation:testAndroidHostTest` 2/2 e `:app:androidApp:testDebugUnitTest` 204/204 verdes. Narrativa em `historico/login-sem-backend.md`. Antes: catálogo: incremento e semântica de carga (2026-11-05)._

_Atualizado em 2026-11-05 (**catálogo: incremento e semântica de carga**): nascem os três campos que `Progressao` exige e que **não existiam em lugar nenhum do código** — `incremento`, `barra_livre` e `carga_invertida` (V74, derivados de `equipment`, com autoconferência e três CHECKs). A fatia saiu de dentro do `rir-plano.md`, cuja pergunta 1 presumia que o dado existia. Dois achados pesados: a **Barra Fixa Assistida tem carga invertida** e o motor a facilitaria conforme a pessoa fica mais forte (flag criada aqui; a troca de sinal é item **bloqueante** da fatia do RIR), e **42 de 147 do pool (28,6%) não têm progressão nenhuma**, com 4 das 26 âncoras do avançado estruturalmente mortas — dois P0 separados. Duas Leis medidas viraram critério de aceite em `regras/01` (o loop quebra a ~20% da carga; o freio da Regra 4 só age acima de ~12,5× o incremento). `:server:integrationTest` 8/8 e `:server:test` 814/814 verdes. Narrativa em `historico/catalogo-incremento-e-semantica-de-carga.md`. Antes: design system + correção do tonal genérico (2026-11-03)._

_Atualizado em 2026-11-03 (**design system + correção do tonal genérico**): nasce o `DESIGN_SYSTEM.md`, a referência de cor, tipografia, componentes, ícones e os fluxos do usuário, com o que é lei e o que é livre tela a tela. Dele saiu um defeito real: `secondaryContainer` era o roxo de IA, e o Material 3 usa esse slot como cor tonal GENÉRICA, então **13 arquivos herdavam dele** (os `-`/`+` da sessão, o chip da série, a trilha de progresso). Já tinha sido achado uma vez e remendado numa tela só. Agora é neutro, e quem quer significado pede `primaryContainer` explicitamente ([REGRA] nova em `regras/15-design-e-cor.md`, narrativa em `historico/correcao-do-tonal-generico.md`). Três suítes verdes, verificado no aparelho. 20 achados de layout entraram em `debitos.md`. Antes: declaração de custo do motor (2026-10-31)._

_Atualizado em 2026-10-31 (**declaração de custo do motor**): fecha (c)/(d) de "o motor sabe, o app não mostra" (`debitos.md`, P0). `ProgramAudit.Causa` ganha `MANUTENCAO`; `VolumeTargets.custoDoFoco()` declara os grupos reduzidos ao piso pela fase de ênfase. Chegam ao `ProgramDto` (`avisos`, `grupamentosReduzidosPeloFoco`), persistidos como snapshot nos dois lados (servidor: V73; cliente: `10.sqm`) e aparecem num card em `ProgramDetailScreen` que some sozinho quando vazio. **Fica dormente**: 100% aditivo, `WorkoutGenerator` de produção não mudou — ninguém vê nada até o v3 virar o gerador de produção (decisão separada). As cinco suítes voltaram a verde depois de três rodadas de correção (arquivo de teste corrompido entre sessões, apóstrofo sem escapar no catálogo em inglês, `stringResource` dentro de `joinToString` não-inline, valor de string novo colidindo com um existente) — narrativa completa em `historico/declaracao-de-custo-do-motor.md`. Antes: service/rota da promoção do avançado (2026-10-31, mesmo dia), V72._

## Agora

| | |
|---|---|
| Última fatia fechada | **Modo leve -- resto da Fatia 12, cliente**, 2026-11-07. `LightModeStep` (sim/não) no passo `QuizStep.LIGHT_MODE`, logo depois de TIME_OFF. Preferência, não condição -- passo nunca bloqueia, default `false`. Continua dormente. Detalhe em `historico/modo-leve-no-cliente.md`. Antes: **Minutos disponíveis -- resto da Fatia 12, cliente**, 2026-11-07. `MinutesAvailableStep` (radio-list) no passo `QuizStep.MINUTES`, logo depois de DAYS. Continua dormente -- nenhuma rota chama `CapacityModel.tetoPorTempo`. Achado de infra, não de código: lock de arquivo do Windows no daemon do Kotlin, resolvido reiniciando o daemon. Detalhe em `historico/minutos-disponiveis-no-cliente.md`. Antes: Antes: **Nível deixa de ser escolha livre -- resto da Fatia 12, cliente**, 2026-11-07. `LevelStep` saiu, `TrainingConsistencyStep`/`TimeOffTrainingStep` entraram; `QuizState.levelDerivado` chama o `nivelDerivado()` compartilhado. Modo edição pede a resposta de novo pra quem já tinha perfil salvo (campos vêm `null`). Achado: `--` em comentário XML de novo (mesmo defeito de horas antes), trocado por `;`. Detalhe em `historico/nivel-no-cliente.md`. Antes: Antes: **"Casa (em breve)" na tela -- resto da Fatia 12, recorte 4**, 2026-11-07. `EnvironmentStep` restringe a opções = {ACADEMIA}, CASA esmaecida e não clicável, sufixo "(em breve)". Só UI, não desliga o usuário real. Detalhe em `historico/casa-em-breve.md`. Antes: **Botão "não tenho este equipamento" -- resto da Fatia 12, recorte 3**, 2026-11-07. `Accessory.paraAmbiente` corrigido -- ACADEMIA ignorava qualquer lista declarada; agora respeita (null continua assumindo os seis). Não dormente, vale hoje nos dois chamadores de produção. Detalhe em `historico/nao-tenho-este-equipamento.md`. Antes: **Modo leve -- resto da Fatia 12, recorte 2**, 2026-11-07. `ProfileDto.preferLightMode` (V80) + `SafetyRules.modoConservadorAutomatico` ganha 4º gatilho (`preferiuModoLeve`, default `false`), só no 1º mesociclo. Dormente. `:server:test`/`:server:integrationTest` -- a confirmar. Detalhe em `historico/modo-leve.md`. Antes: **Minutos disponíveis -- resto da Fatia 12, recorte 1**, 2026-11-07. `MinutesAvailable` (V79, aditiva) alimenta `CapacityModel.tetoPorTempo` (dormente, motor v3). `:server:integrationTest` cobre round-trip real -- a confirmar. Detalhe em `historico/minutos-disponiveis.md`. Antes: **Mostrar sugestão de nível na tela de execução**, 2026-11-07. Fecha o débito pequeno da fatia anterior -- `SugestaoDeProgressao.calcular` ganha o 3º eixo (BAND/HAND_GRIP: faixa original de `PrescricaoV3.de`, sem Estado). Cadeia do cliente já era genérica -- zero mudança lá, só comentário. Sem migration. `:server:test` verde. Detalhe em `historico/sugestao-de-nivel.md`. Antes: **Nível derivado e fim do gate de saúde**, 2026-11-07. Primeiro recorte da Fatia 12 -- `Level` deixa de ser autodeclarado (`nivelDerivado`, fallback aditivo pro cliente que ainda não pergunta as duas perguntas novas); gate de saúde (PAR-Q) removido de `/programs/generate` (`ProgramGerarGate`, só resta gate de idade). V78, aditiva. Medido no banco antes de decidir: 0 linhas em `limitations`, 1 usuário em `CASA`. `:konsist:test`/`:server:test`/`:server:integrationTest` verdes. Detalhe em `historico/nivel-derivado-e-fim-do-gate-de-saude.md`. Antes: **Progressão por nível (BAND/HAND_GRIP)**, 2026-11-06. Fecha "progressão inexistente para BAND/HAND_GRIP" (`debitos.md`, P0) -- descarta a premissa de que precisava modelar o kit do usuário. `ProgressaoPorNivel` (novo): nível RELATIVO, faixa de reps FIXA (diferente do eixo de reps, onde a faixa é que muda), nunca abaixo do nível 1. V77, aditiva. `AncoraDoIniciante.Evento.Nivel` -- terceiro eixo. Achado não relacionado (fake desatualizado da fatia anterior) corrigido. `:server:test`/`:server:integrationTest` verdes. Detalhe em `historico/progressao-por-nivel.md`. Antes: **Âncoras do avançado bodyweight**, 2026-11-06 (mesmo dia). Fecha "4 âncoras estruturalmente mortas" (`debitos.md`, P0) -- bug existente, não feature ausente. `ProgressaoPorRepeticao.estimarSinalDeForca()` (reps+RIR, não é 1RM em kg) grava na MESMA coluna `um_rm_kg` que o eixo de carga -- zero mudança em `PromotionService`/contrato/cliente, zero migration. Exceção documentada em `regras/01` ao comentário da V76. `:server:test`/`:server:integrationTest` verdes. Detalhe em `historico/ancoras-avancado-bodyweight.md`. Antes: **Sugestão de progressão na tela de execução**, 2026-11-06 (mesmo dia). `GET /workouts/{id}` enriquece o DTO (carga OU reps, falha sempre silenciosa); cliente mostra "Meta: ..." no card do exercício. Sem migration. Dois achados sem relação, ambos corrigidos e fechados em `debitos.md` (`Semear.kt` STRENGTH → `CORE`; allowlist de chave idêntica). Detalhe em `historico/sugestao-de-progressao.md`. Antes: **Progressão por repetição**, 2026-11-06 (mesmo dia). `ProgressaoPorRepeticao` assume BODYWEIGHT sem `incremento` (reps em vez de carga, sem calibração). Achado no meio: `AncoraDoIniciante`/`PromocaoDeNivel` só reconheciam `Evento.Carga` pro streak de SOBE -- unificado via `Evento.Repeticao`/`.subiu` (decisão do Rafael). Achado ao planejar o Contrato: nenhum dos dois eixos expõe sugestão pro cliente -- fecha no servidor, em paridade real; BAND/HAND_GRIP e "mostrar sugestão na tela" viraram débitos próprios. V76, aditiva. `:server:test`/`:server:integrationTest` verdes. Detalhe em `historico/progressao-por-repeticao.md`. Antes: **RIR chega do cliente e acorda a progressão**, 2026-11-05. Backend → Contrato → Cliente: RIR por série até `session_set_logs.rir`, `SessionService.record()` ligado a `ProgressaoAplicadaService` em produção, unificação `Papel`/`SlotRole` (V75), troca de sinal de `cargaInvertida` (bloqueante), e no cliente `SetEntry.rir`/`RirChanged`/UI. Achado no fechamento: `onCleared()` virou `public` (era o único jeito de o teste novo cancelar o `while(true)` do cronômetro, que travava o processo de teste). `:shared:features:session:presentation:testAndroidHostTest` verde. Detalhe em `historico/rir-chega-e-registra.md`. Antes: **Login sem backend**, 2026-11-05. `LoginViewModel.submit()` entrava só depois de `fetchMe()` responder — backend fora bloqueava login com credencial válida; a leitura do código achou que `findOrCreate()` já roda em toda rota (não só `/me`) e que a Splash já sincroniza `/me` com timeout depois. Fix: entra com o `uid` do Firebase direto. **Segundo defeito, reproduzido pelo Rafael no aparelho**: `signIn`/`signUp` podiam deixar `auth.currentUser` vivo numa chamada relatada como falha (offline) — `mapAuthError` agora `suspend`, chama `signOut()` defensivo. `LoginViewModelTest` novo (não existia nenhum); `testAndroidHostTest` 2/2, `:app:androidApp:testDebugUnitTest` 204/204. Detalhe em `historico/login-sem-backend.md`. Antes: **Catálogo: incremento e semântica de carga**, 2026-11-05. Cria `exercises.incremento`/`barra_livre`/`carga_invertida` (V74) — os três que `decidir()`/`calibrar()` exigem e que não existiam. Entregou: 3 colunas derivadas de `equipment` (322 com 2,5 kg, 139 com 1,0 **por halter**, 432 NULL, 97 barra livre, 4 carga invertida), 3 CHECKs (o `chk_exercises_barra_livre` é `[INV]` no banco), autoconferência com `RAISE EXCEPTION`, e `SemanticaDeCargaIntegrationTest` (8 testes, 5 caminhos de falha). Contrato e cliente: nada, por desenho. Detalhe em `historico/catalogo-incremento-e-semantica-de-carga.md`. Antes: **Declaração de custo do motor**, 2026-10-31. Fecha (c)/(d) de "o motor sabe, o app não mostra" (P0). Entregou: `ProgramAudit.Causa.MANUTENCAO`, `VolumeTargets.custoDoFoco()` (reusa a isenção BACK/EMPURRAR extraída pra `isentoDoPisoPorFoco()`), `ProgramDto.avisos`/`grupamentosReduzidosPeloFoco` (default `emptyList()`, 100% aditivo), persistência nos dois lados (servidor V73, cliente `10.sqm` — achado durante o desenho, não previsto no plano original), card `AvisosDoPrograma` em `ProgramDetailScreen`, `Rotulos.kt` (`rotuloDoMusculoAlvo`/`rotuloDaCausaDeAviso`). Fica dormente até o v3 virar o gerador de produção. Três achados de fechamento (arquivo de teste corrompido entre sessões, apóstrofo sem escapar, `stringResource` em `joinToString` não-inline, string nova colidindo com uma existente) corrigidos antes do build verde. Antes: service/rota do avançado (2026-10-31, mesmo dia). Detalhe em `historico/declaracao-de-custo-do-motor.md` |
| Migration no código | **V78** (`V78__nivel_derivado.sql`) -- SCHEMA aditiva, 2 colunas nullable em `profiles` (`training_consistency`, `time_off_training`). Não espelhada no cliente ainda. Antes dela, **V77** (`V77__progressao_por_nivel.sql`) — SCHEMA aditiva, 1 coluna nullable em `exercise_progression` (`nivel_atual`) + CHECK, recria o CHECK de `progression_event.tipo` com `SOBE_NIVEL`/`MANTEM_NIVEL`/`DESCE_NIVEL`. Não espelhada no cliente. Antes dela, **V76** (`V76__progressao_por_repeticao.sql`) — SCHEMA aditiva, 2 colunas nullable em `exercise_progression` (`reps_min`/`reps_max`) + CHECK, e recria o CHECK de `progression_event.tipo` com `SOBE_REP`/`MANTEM_REP`/`DESCE_REP`. Não espelhada no cliente. Antes dela, **V75** — persiste `papel` (enum unificado `Papel`/`SlotRole`) em `workout_exercises`, nullable. Antes dela, **V74** (`V74__incremento_e_semantica_de_carga.sql`) — SCHEMA + DADO, 3 colunas em `exercises`, não espelhada no cliente (nada no cliente consome). É migration de DADO, então pede `docs/migration_verify/` quando rodar contra o banco de desenvolvimento real. Antes dela, **V73** (`V73__declaracao_de_custo_do_programa.sql`) — 2 colunas em `programs` (`avisos`, `grupamentos_reduzidos_pelo_foco`, JSON-em-TEXT, nullable, mesmo padrão de `focus_muscles`/V56). Espelhada no cliente pela **`10.sqm`** (SQLDelight, mesmo par de colunas). Antes dela, **V72** (`um_rm_kg`), **V71** (`mesociclo`) e **V70** (`promotion_decline`). V64–V73 **já commitadas** — conferido em 2026-11-05 com `git ls-files`, acha as 10; este campo dizia o contrário desde 31/10. Só a **V74** está fora do git |
| Schema local | `10.sqm` (`8.sqm` = acervo do Aprender/Fase 8, `9.sqm` = tabela `notificacao`, `10.sqm` = `program.avisos`/`grupamentosReduzidosPeloFoco`) |
| Catálogo | **893 exercícios** depois da V65 (922 antes: 29 saíram, duplicados e um aparelho passivo). **147 na lista principal**, com `volume_target`, `prioridade`, `relevancia`, `nivel_maximo` e `ambiente`; família e os seis `requires_*` nos 893. **V74**: `incremento`, `barra_livre` e `carga_invertida` nos 893, derivados de `equipment` |
| Não commitado | **"Modo leve -- resto da Fatia 12, cliente"**: `Profile.kt` (domínio), `ProfileMapper.kt`/`ProfileMapperTest.kt`, `QuizEvent.kt`, `QuizState.kt`/`QuizStateTest.kt`, `QuizViewModel.kt`, `QuizSteps.kt`, `QuizScreen.kt`, `strings.xml`/`values-en/strings.xml` (4 strings novas), `debitos.md`/`regras/16`/`historico/modo-leve-no-cliente.md`/`ESTADO.md`. Sem migration. Aguardando commit. Antes dela, **"minutos disponíveis (cliente)"** (`2839b8d`), **"nível deixa de ser escolha livre (cliente)"** (`b0dd226`), **""casa (em breve)" na tela"** (commit a confirmar), **"botão de equipamento"** (`addd558`), **"modo leve"** (`558d99a`), **"minutos disponíveis"** (`d8535ef`), **"mostrar sugestão de nível"** (`8efd7c6`) e **"nível derivado e fim do gate de saúde"** (`a902c25`) já commitadas. Antes: **"progressao-por-nivel"** (`c28ce4b`), **"ancoras-avancado-bodyweight"** (`e8515ec`), **"sugestao-de-progressao"** e **"correção do tonal genérico"** (`5a9db01`, `ccbf91c`) já commitadas em `develop` nesta sessão -- falta só o `git push`, que é do Rafael. Antes delas, **"progressao-por-repeticao"** (`43c3152`, `41b3a7e`, `f8c010e`, `0ada929`, `72c65c9`) e **"rir-chega-e-registra"** (`6ad9b2a`, `07c02bf`, `95fcdc6`) já estavam em `develop`. Lembrete: `docs/` NÃO está fora do git (só `docs/pdf/` é ignorado, `.gitignore:77`).
| Achado ao fechar | **2026-10-29, Fatia 11:** (1) `Oraculo.catalogo()` (teste Kotlin) nunca lia `movement_pattern` do CSV — fábrica de teste com default silencioso, invisível até `WeeklyBackSelection` precisar do campo; causa dos 1680/1680 perfis divergindo na varredura. (2) dois lados concordando não é prova, de novo: as outras 6 falhas eram expectativa congelada antes do piso de ênfase existir, não bug do motor. (3) a fase move `ombro_vira_isolamento` de 16 para 136 — efeito esperado, não regressão. (4) restrição de volume puxar×empurrar foi medida e rejeitada — o eixo certo era seleção, não volume. (5) scripts de medição ad hoc em `tools/cache/` violavam a própria regra do `tools/README.md` — movidos para `tools/motor_sim/` |
| Em andamento | **Nenhuma fatia do motor** — "modo leve (resto da Fatia 12, cliente)" fechada (ver linha acima); "minutos disponíveis (resto da Fatia 12, cliente)", "nível deixa de ser escolha livre (resto da Fatia 12, cliente)", ""casa (em breve)" (resto da Fatia 12, recorte 4)", "botão de equipamento (recorte 3)", "modo leve (recorte 2)", "minutos disponíveis (recorte 1)", "mostrar sugestão de nível na tela", "nível derivado e fim do gate de saúde", "progressão por nível", "âncoras do avançado bodyweight", "sugestão de progressão" e "progressão por repetição" fechadas antes dela. Sem relação: **varredura de débitos A–D** (`trabalho/debitos-A-D.md`), A/B/C fechados em 2026-10-09, **D aguardando o Rafael** |
| Motor de geração | **Especificação pronta, fatias 0–11 fechadas, 12 por fazer.** A spec vive em `propostas/motor-v3-ESPECIFICACAO.md`; o desenho e as fatias em `trabalho/motor-v3-desenho-e-fatias.md`. O que roda hoje em produção ainda é a geração antiga (`StructureEngine` + `SlotFiller`, `Random(seed)`) — `engine.v3` existe em paralelo, sem fiação. `server/src/test/oracle/` é o oráculo (94 testes verdes) e escolhe pela curadoria. **Condição da troca de produção: FECHADA nesta fatia** — a proteção contra sacrifício existe agora, o risco de foco permanente sem proteção não se aplica mais; LIGAR continua decisão sua. **Aberto, mas não bloqueante:** a "fase de 2 mesociclos" da ênfase estava no plano original da Fatia 11 e não foi implementada — o foco continua permanente (`fontes/debitos.md`) |
| Próxima fatia | **Resto da Fatia 12, continuação** (só o botão de equipamento, no cliente -- ver `fontes/debitos.md` §"Motor v3 — fatia 12"). `BodyLimitation` em PAUSA (decisão do Rafael, sem lógica de substituição). Nível deixou de ser escolha livre (servidor e cliente), "casa (em breve)", botão de equipamento (servidor), modo leve (servidor), minutos→teto (servidor), gate de saúde, BAND/HAND_GRIP sem progressão (e sua sugestão na tela), as 4 âncoras do avançado mortas e o `Semear.kt`/`STRENGTH` já foram corrigidos e fechados |

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
