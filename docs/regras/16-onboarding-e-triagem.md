# Onboarding e triagem

> **Nasce em 2026-11-07** ("nível derivado e fim do gate de saúde" — primeiro recorte da
> Fatia 12 do motor, `trabalho/motor-v3-desenho-e-fatias.md`). Área sem regra própria até aqui;
> o questionário vivia espalhado em `ProfileDto`/`QuizSteps.kt` sem arquivo de comportamento.

## Nível deixou de ser autodeclarado

[REGRA] `Level` não é mais uma escolha livre do onboarding. É derivado por
`nivelDerivado(trainingConsistency, timeOffTraining)` (shared-contract, pura):

- `trainingConsistency == NEVER_TRAINED` OU `LESS_THAN_6_MONTHS`, OU
- `timeOffTraining == MORE_THAN_6_MONTHS`

→ `BEGINNER`. Qualquer outra combinação → `INTERMEDIATE`.

[INV] **Nunca `ADVANCED` pelo cadastro.** A entrada máxima é `INTERMEDIATE` — avançado só se
alcança pela promoção por histórico (`PromocaoDeNivel`, âncoras congeladas). Mesmo quem responde
"mais de 2 anos" + "não parei" entra intermediário.

[REGRA] **Autoridade e fallback aditivo.** `ProfileService.saveProfile` só usa o nível derivado
quando `trainingConsistency` E `timeOffTraining` vêm preenchidos no `ProfileDto`. Se qualquer um
vier `null` (cliente anterior a esta fatia, que ainda pergunta nível livremente), o servidor
**mantém o `level` que o cliente mandou** — sem isso, o onboarding em produção quebraria no dia
em que o backend subisse antes do app novo.

[REGRA] **Fechado no cliente (2026-11-07):** `LevelStep` saiu do onboarding Android. Em seu
lugar, `TrainingConsistencyStep`/`TimeOffTrainingStep` (`QuizSteps.kt`) perguntam as duas, e
`QuizState.levelDerivado` chama o MESMO `nivelDerivado()` do shared-contract para antecipar o
nível na UI -- o servidor é quem decide de verdade, recalculando sempre. Modo edição
(`QuizViewModel.startEditing`): quem já tinha perfil salvo ANTES desta fatia tem os dois campos
`null` e tem que responder de novo, uma vez -- esperado, não é bug. O fallback acima continua
existindo porque ele protege contra desencontro de deploy (backend novo × app antigo em
produção), não porque falte cliente.

## PAR-Q: autoavaliação sem armazenamento, não bloqueia

[REGRA] **O gate de saúde foi removido de `/programs/generate`.** Decisão já ratificada na
especificação do motor v3 (`propostas/motor-v3-ESPECIFICACAO.md`, 3ª rodada de revisão):
bloquear fazia a pessoa mentir, e a resposta deixa de ser armazenada — então não pode mais
acionar nada no servidor. `ProgramGerarGate.avaliar()` (pura, `program/services/`) só resta o
gate de idade (#24); é a ÚNICA pré-condição de saúde/idade que recusa a geração hoje.

[INV] `ProfileDto.health`/coluna `profiles.health_screening` ficam **mortos** (sem leitura, sem
gate) — não apagados nesta fatia (débito próprio, `fontes/debitos.md`). Não reintroduzir
nenhuma lógica que leia `.health` ou `.gateSatisfied` fora de código legado/teste.

[REGRA] **"Modo leve" (preferência explícita) agora é gatilho do modo conservador.**
`ProfileDto.preferLightMode: Boolean?` (V80) -- quando `true`, soma-se aos gatilhos de
`SafetyRules.modoConservadorAutomatico` (`preferiuModoLeve`), mas só vale no 1º mesociclo
(`CapacityModel.primeiroMesociclo`), igual ao gatilho de idade 60+ -- é a mesma regra "modo leve
dura 1 mesociclo" da especificação. Default `false`: nenhum caller existente muda de resultado.
**Ainda dormente** -- `MotorV3Pipeline.Entrada` não tem caller fora de teste; o campo grava e lê,
mas nada em produção lê `preferLightMode` hoje.

[REGRA] **Fechado no cliente (2026-11-07):** `LightModeStep` (`QuizSteps.kt`) pergunta no
passo `QuizStep.LIGHT_MODE`, logo depois de TIME_OFF -- sim/não, com texto de ajuda sobre o
mesociclo. É preferência, não condição: diferente das perguntas de nível, o passo nunca
bloqueia o avanço (default "não"). Continua dormente do mesmo jeito do servidor.

## Minutos disponíveis → teto de séries

[REGRA] `ProfileDto.minutesAvailable: MinutesAvailable?` (shared-contract, V79) -- faixas
`FORTY_FIVE`/`SIXTY`/`SEVENTY_FIVE`/`NINETY_PLUS`, cada uma carregando o minuto representativo
(`.minutos`) que `CapacityModel.tetoPorTempo(minutos, dias)` consome. **Dormente** -- a coluna
grava e lê, mas nenhuma rota chama `MotorV3Pipeline.Entrada`/`tetoPorTempo` ainda; fica parada
até o v3 virar o gerador de produção, igual ao resto do motor v3.

[REGRA] **Fechado no cliente (2026-11-07):** `MinutesAvailableStep` (`QuizSteps.kt`,
radio-list) pergunta no passo `QuizStep.MINUTES`, logo depois de DAYS. Continua dormente do
mesmo jeito -- o campo grava e lê de ponta a ponta, só não tem rota que leia pra calcular o
teto ainda.

## "Não tenho este equipamento" (academia)

[REGRA] **`Accessory.paraAmbiente` agora respeita declaração explícita também na ACADEMIA.**
Antes: `ambiente == ACADEMIA` sempre devolvia os seis, ignorando qualquer lista declarada. Agora:
`null` continua assumindo os seis (nunca perguntado -- nenhum perfil de academia de hoje manda
outra coisa, fallback aditivo); uma lista explícita, mesmo vazia, é respeitada nos dois
ambientes -- é o que o botão "não tenho este equipamento" (especificação -- resolve as academias
de condomínio) declara. **Sem campo novo no contrato** -- reusa `ProfileDto.accessories` (V66),
decisão tomada antes de codar. `DeterministicWorkoutGenerator`/`ExerciseRoutes.alternatives` (os
dois chamadores de produção) só deixaram de forçar `?: emptyList()` antes de chamar
`paraAmbiente` -- a distinção null/lista agora atravessa até lá.

[INV] `Rafael decidiu NÃO remover `BodyLimitation` do código (2026-11-07) -- mesmo sem
armazenar nada de novo sobre limitação do usuário, o enum continua existindo (perfil +
`Exercise.contraindications`, dado curado). Não propor remoção de novo sem pedir.

## "Casa (em breve)" na tela de onboarding

[REGRA] `EnvironmentStep` (`QuizSteps.kt`) restringe as opções visíveis a
`AMBIENTES_DISPONIVEIS = {ACADEMIA}` -- especificação: "lançar só com academia; a lista de
equipamentos é condição de lançamento apenas para o treino em casa". CASA aparece esmaecida, com
sufixo "(em breve)"/"(coming soon)" (`quiz_ambiente_em_breve`), rádio e row desabilitados. **Só
UI** -- `TrainingEnvironment.CASA` continua existindo de ponta a ponta sem gate nenhum; o 1
usuário real medido não é afetado, porque isto só governa a ESCOLHA no onboarding, não nenhuma
rota.

## O que ainda falta da Fatia 12

**Fatia 12 fechou inteira no cliente em 2026-11-07** -- nível derivado, minutos disponíveis,
modo leve e o botão "não tenho este equipamento" também na ACADEMIA (`QuizState.
academyMissingEquipment` revela o passo `ACCESSORIES`, reusando o mesmo campo/passo de CASA;
detalhe em `historico/botao-de-equipamento-no-cliente.md`). Só resta `BodyLimitation`
remapeado pras 5 regiões da spec -- **em pausa**: a lógica de SUBSTITUIÇÃO de exercício por
região (a parte que daria utilidade real ao remap) não existe em nenhum lugar do código, e é
decisão de produto nova, maior que esta fatia; decisão tomada de não remover o enum (ver INV
acima). Ver `trabalho/motor-v3-desenho-e-fatias.md` §Fatia 12 e `ESTADO.md`.
