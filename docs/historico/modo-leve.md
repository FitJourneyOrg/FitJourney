# Modo leve (preferência explícita) → gatilho do modo conservador

**Fechado em 2026-11-07.** Segundo recorte do "resto da Fatia 12".

## O que era

A especificação troca o PAR-Q (removido como gate na fatia "nível derivado e fim do gate de
saúde") por uma pergunta de preferência: "quer começar no modo leve?". O custo declarado de não
armazenar o PAR-Q é que o "sim" deixa de poder acionar o modo conservador -- esse modo passa a
ser disparado só por iniciante, saúde geral e 60+ no 1º mesociclo (regra já existente). A
preferência explícita reintroduz um 4º gatilho, mas **só vale no 1º mesociclo**, mesma regra do
gatilho de idade.

`SafetyRules.modoConservadorAutomatico` já documentava a lacuna desde a Fatia 7: "o campo não
existe em `ProfileDto`/nenhuma migration ainda".

## O que mudou

`ProfileDto.preferLightMode: Boolean?` (novo, V80) -- mesmo padrão aditivo das fatias anteriores
(`ProfileDto`/`Profile`/tabela/mapper/service, só repassa, sem lógica).

`SafetyRules.modoConservadorAutomatico` ganha o parâmetro `preferiuModoLeve: Boolean = false`
(default preserva todo caller existente) e o novo branch:

```kotlin
(preferiuModoLeve && CapacityModel.primeiroMesociclo(mesocicloAtual))
```

## Por que fica dormente

`ProfileDto.preferLightMode` grava e lê, mas nada em produção lê esse campo: nenhuma rota
constrói `MotorV3Pipeline.Entrada`/chama `modoConservadorAutomatico` com ele. Mesma situação de
`minutesAvailable` -- é só o lado de entrada, a fiação é de quando o v3 virar o gerador de
produção.

## Testes

`SafetyRulesTest`: +2 (dispara no 1º mesociclo, inclusive nulo; não dispara fora do 1º
mesociclo -- caminho de falha). `PreferLightModePersistenciaIntegrationTest.kt` (Tier 3, mesmo
padrão das fatias anteriores): round-trip real, perfil sem a pergunta grava/lê `null`, JSON de
cliente antigo não quebra.

`:server:test`/`:server:integrationTest` -- a confirmar pelo Rafael.
