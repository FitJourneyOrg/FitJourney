# Minutos disponíveis → teto por tempo (contrato + persistência)

**Fechado em 2026-11-07.** Primeiro recorte do "resto da Fatia 12".

## O que era

A especificação trouxe de volta a pergunta "minutos disponíveis" (45/60/75/90+), que alimenta o
teto de séries da sessão. `CapacityModel.tetoPorTempo(minutos, dias)` já existia desde a Fatia 2
do motor v3 (`(minutos − overhead) ÷ 3`) -- o que faltava era de ONDE esse `minutos` vinha: não
havia campo nenhum no onboarding.

## O que mudou

`MinutesAvailable` (shared-contract, novo): enum com 4 faixas, cada uma carregando o minuto
representativo (`FORTY_FIVE(45)`, `SIXTY(60)`, `SEVENTY_FIVE(75)`, `NINETY_PLUS(90)`). Mesmo
padrão aditivo das duas perguntas da fatia anterior (`trainingConsistency`/`timeOffTraining`):
`ProfileDto`/`Profile` (server)/`ProfilesTable`/`ProfileRepositoryImpl`/`ProfileMapper` ganharam
o campo nullable, `ProfileService.saveProfile` só repassa (sem lógica -- não deriva nada, só
grava e lê).

V79, aditiva: 1 coluna nullable em `profiles` (`minutes_available`).

## Por que fica dormente

`CapacityModel.tetoPorTempo` não mudou -- continua sem nenhum caller fora de teste. Isto é só o
lado de ENTRADA (contrato + persistência); a fiação pro `MotorV3Pipeline` é de quando o v3 virar
o gerador de produção, não desta fatia.

## Testes

`MinutosDisponiveisPersistenciaIntegrationTest.kt` (Tier 3, mesmo padrão de
`NivelDerivadoPersistenciaIntegrationTest`): round-trip contra Postgres real, perfil sem a
pergunta grava/lê `null`, e JSON de cliente antigo (sem a chave) não quebra a desserialização.

`:server:integrationTest` -- a confirmar pelo Rafael.
