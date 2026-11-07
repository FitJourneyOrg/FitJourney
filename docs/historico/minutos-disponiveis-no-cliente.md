# Minutos disponíveis (cliente) — resto da Fatia 12

**Fechada em 2026-11-07.** Fecha o lado Android de `MinutesAvailable` (ver
`historico/minutos-disponiveis.md`, que fechou contrato + servidor nesta mesma sessão, antes). Até
aqui o campo só existia no `ProfileDto`/`Profile` (servidor) — nada no app perguntava, e ninguém
mandava o valor.

## O que mudou

- **Domínio e dados**: `Profile` (domínio, cliente) ganhou `minutesAvailable: MinutesAvailable?`
  nullable, espelhando o `ProfileDto`. `ProfileMapper.toDomain()`/`toDto()` passam o campo nos dois
  sentidos (round-trip testado em `ProfileMapperTest`).
- **Estado do quiz**: `QuizStep.MINUTES` novo, logo depois de `DAYS` (dias por semana e minutos
  por sessão são a mesma pergunta de "quanto tempo tenho", em dois passos). `QuizState` ganha o
  campo e o `canAdvance`/`toProfileOrNull` correspondentes.
- **UI**: `MinutesAvailableStep` (`QuizSteps.kt`), radio-list — mesmo estilo das perguntas de
  nível fechadas horas antes (`TrainingConsistencyStep`/`TimeOffTrainingStep`/`EnvironmentStep`).
  `QuizScreen.kt` despacha o passo novo.
- **Strings**: `quiz_minutos_titulo` + 4 rótulos de opção (`enum_minutos_*`), nos dois idiomas.
- **Modo edição** (`QuizViewModel.startEditing`): carrega `p.minutesAvailable`; quem já tinha
  perfil salvo de antes desta fatia tem o campo `null` e responde ao editar, mesmo padrão já usado
  para `trainingConsistency`/`timeOffTraining`.

## O que NÃO mudou

`CapacityModel.tetoPorTempo` (motor v3, Fatia 2) continua **dormente** — nenhuma rota chama o
pipeline v3 ainda. O campo grava e lê de ponta a ponta, mas nada em produção o consome.

## Testes

`ProfileMapperTest` (round-trip), `QuizStateTest` (`canAdvance` do passo MINUTES,
`toProfileOrNull` carregando o valor marcado). `:shared:features:profile:data`/`:presentation` e
`:app:androidApp:testDebugUnitTest` — build verde (confirmado pelo Rafael, depois de reiniciar o
daemon do Kotlin: `Could not delete .../build/classes/kotlin/android/main`, lock de arquivo do
Windows sem relação com o código).

## Falta do resto da Fatia 12

Modo leve (toggle pra `preferLightMode`) e o botão "não tenho este equipamento" também na
ACADEMIA, ambos no cliente. `BodyLimitation` em pausa (decisão do Rafael, ver `fontes/debitos.md`).
