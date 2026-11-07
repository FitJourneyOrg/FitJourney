# Modo leve (cliente) — resto da Fatia 12

**Fechada em 2026-11-07.** Fecha o lado Android de `preferLightMode` (ver `historico/modo-leve.md`,
que fechou contrato + servidor nesta mesma sessão, antes). Até aqui o campo só existia no
`ProfileDto`/`Profile` (servidor) — nada no app perguntava, e ninguém mandava o valor.

## O que mudou

- **Domínio e dados**: `Profile` (domínio, cliente) ganhou `preferLightMode: Boolean?` nullable,
  espelhando o `ProfileDto`. `ProfileMapper.toDomain()`/`toDto()` passam o campo nos dois sentidos
  (round-trip testado em `ProfileMapperTest`).
- **Estado do quiz**: `QuizStep.LIGHT_MODE` novo, logo depois de `TIME_OFF` — agrupa com as
  perguntas que avaliam o ponto de partida da pessoa. Diferente das demais perguntas desta fatia,
  `preferLightMode` no `QuizState` é `Boolean` não-nulo, default `false`: é preferência, não
  condição, e a especificação (3ª rodada) descreve como pergunta que "guarda a escolha, não a
  condição" — por isso o passo nunca bloqueia o avanço (`canAdvance` sempre `true` nele), quem não
  toca segue com "não" marcado.
- **UI**: `LightModeStep` (`QuizSteps.kt`) — radio de duas opções (sim/não), com texto de ajuda
  explicando que vale por um mesociclo. `QuizScreen.kt` despacha o passo novo.
- **Strings**: `quiz_modo_leve_titulo`/`_ajuda`/`_sim`/`_nao`, nos dois idiomas.
- **Modo edição** (`QuizViewModel.startEditing`): carrega `p.preferLightMode ?: false` — quem já
  tinha perfil salvo de antes desta fatia simplesmente herda "não" (preferência, não exige
  resposta explícita como as perguntas de nível fizeram).

## O que NÃO mudou

`SafetyRules.modoConservadorAutomatico` (servidor, já fechado) continua **dormente** —
`MotorV3Pipeline.Entrada` não tem caller fora de teste. O campo grava e lê de ponta a ponta, mas
nada em produção lê `preferLightMode` hoje.

## Testes

`ProfileMapperTest` (round-trip), `QuizStateTest` (passo não bloqueia sem resposta, default
`false`, `toProfileOrNull` carregando o valor marcado). `:shared:features:profile:data`/
`:presentation` e `:app:androidApp:testDebugUnitTest` — build verde (confirmado pelo Rafael).

## Falta do resto da Fatia 12

Botão "não tenho este equipamento" também na ACADEMIA (hoje o passo ACCESSORIES só aparece pra
CASA). `BodyLimitation` em pausa (decisão do Rafael, ver `fontes/debitos.md`).
