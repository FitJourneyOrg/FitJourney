# Nível deixa de ser escolha livre (cliente) — resto da Fatia 12

**Fechada em 2026-11-07.** Recorte do cliente que fecha o lado Android de "nível deixou de ser
autodeclarado" (ver `historico/nivel-derivado-e-fim-do-gate-de-saude.md`, que fechou o lado
servidor). Até aqui o `ProfileDto.trainingConsistency`/`timeOffTraining` só existiam no contrato
e no servidor — nada no app perguntava, então todo onboarding novo continuava mandando `level`
escolhido livremente, e o fallback aditivo do servidor é que aceitava.

## O que mudou

- **Domínio e dados**: `Profile` (domínio, cliente) ganhou `trainingConsistency`/`timeOffTraining`
  nullable, espelhando o `ProfileDto`. `ProfileMapper.toDomain()`/`toDto()` passam os dois campos
  nos dois sentidos (round-trip testado em `ProfileMapperTest`).
- **Estado do quiz**: `QuizStep.LEVEL` saiu; entraram `QuizStep.CONSISTENCY`/`TIME_OFF`.
  `QuizState` troca o campo `level: Level?` por `trainingConsistency`/`timeOffTraining`, e ganha
  `levelDerivado: Level?` — chama o MESMO `nivelDerivado()` puro do `shared-contract` que o
  servidor usa, `null` até as duas perguntas serem respondidas. `focusEligible` (gate do passo
  FOCUS) passou a depender de `levelDerivado` em vez do campo livre antigo.
- **UI**: `LevelStep` (FilterChip, escolha múltipla visual mas única na prática) saiu de
  `QuizSteps.kt`. Entraram `TrainingConsistencyStep`/`TimeOffTrainingStep`, radio-list — mesmo
  estilo do `EnvironmentStep`, porque são perguntas de única escolha, não `FilterChip`.
  `QuizScreen.kt` despacha os dois passos novos no lugar do antigo `QuizStep.LEVEL`.
- **Strings**: `quiz_nivel_titulo` saiu (órfã, só o `LevelStep` usava). Entraram
  `quiz_consistencia_titulo`/`quiz_parado_titulo` e 8 rótulos de opção
  (`enum_consistencia_*`/`enum_parado_*`), nos dois idiomas. `enum_nivel_*` (usado por
  `Level.rotulo()` em telas de perfil) não foi tocado — nível ainda é mostrado, só deixou de ser
  escolhido aqui.
- **Modo edição** (`QuizViewModel.startEditing`): quem já tem perfil salvo de ANTES desta fatia
  carrega `trainingConsistency`/`timeOffTraining` como `null` (o servidor nunca gravou isso pra
  eles) — ao editar o perfil, respondem as duas perguntas novas uma vez. É o comportamento
  esperado, o mesmo efeito que o fallback aditivo do servidor já cobria do lado dele.

## O que NÃO mudou

- O servidor continua recalculando sempre (`ProfileService.saveProfile`); o `levelDerivado` do
  cliente é só para a UI poder mostrar/usar o nível antes do round-trip, nunca autoridade.
- O fallback aditivo do servidor (mantém o `level` enviado quando os dois campos vêm `null`)
  continua existindo — agora protege só contra desencontro de deploy (backend novo, app ainda
  mais antigo em algum dispositivo), não porque falte cliente.

## Achado ao escrever

Comentário novo em `strings.xml` usou `--` dentro de `<!-- -->` de novo (mesmo defeito da fatia
"casa em breve", poucas horas antes) — `ExpatError` ao validar. Trocado por `;`. Dois achados
do mesmo tipo no mesmo dia: o padrão de escrever comentário com travessão duplo em prosa é um
hábito que vale checar antes de rodar `gen_tudo.py`, não só depois.

## Testes

`ProfileMapperTest` (round-trip dos dois campos novos), `QuizStateTest` (nível derivado em cada
combinação relevante, `canAdvance`/`toProfileOrNull` sem nível). `:shared:features:profile:data`,
`:shared:features:profile:presentation` e `:app:androidApp:testDebugUnitTest` — build verde
(confirmado pelo Rafael).

## Falta do resto da Fatia 12

Minutos disponíveis (passo novo pra `MinutesAvailable`), modo leve (toggle pra
`preferLightMode`), botão "não tenho este equipamento" também na ACADEMIA. `BodyLimitation`
em pausa (decisão do Rafael, ver `fontes/debitos.md`).
