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
em que o backend subisse antes do app novo. O fallback só desaparece quando o cliente parar de
mandar `level` como escolha livre (fatia de cliente, ainda não feita).

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

## Minutos disponíveis → teto de séries

[REGRA] `ProfileDto.minutesAvailable: MinutesAvailable?` (shared-contract, V79) -- faixas
`FORTY_FIVE`/`SIXTY`/`SEVENTY_FIVE`/`NINETY_PLUS`, cada uma carregando o minuto representativo
(`.minutos`) que `CapacityModel.tetoPorTempo(minutos, dias)` consome. **Dormente** -- a coluna
grava e lê, mas nenhuma rota chama `MotorV3Pipeline.Entrada`/`tetoPorTempo` ainda; fica parada
até o v3 virar o gerador de produção, igual ao resto do motor v3.

## O que ainda falta da Fatia 12

`BodyLimitation` remapeado pras 5 regiões da spec (0 dado real medido em 2026-11-07, migration
livre de risco quando vier), "modo leve" (preferência explícita, ver `[PROPOSTA]` acima), botão
"não tenho este equipamento" na academia (`accessories != null` já basta como gatilho, decisão
tomada), "casa (em breve)" na tela — tudo cliente, nenhuma linha ainda. Ver
`trabalho/motor-v3-desenho-e-fatias.md` §Fatia 12 e `ESTADO.md`.
