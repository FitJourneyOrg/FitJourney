# Botão de equipamento também na ACADEMIA (cliente) — fechada 2026-11-07

Último item do "Resto da Fatia 12". Fecha o débito que o servidor já tinha deixado pronto: `Accessory.paraAmbiente` passou a respeitar lista explícita também na ACADEMIA numa fatia anterior desta mesma sessão (`historico/nao-tenho-este-equipamento.md`), mas o cliente nunca mandava essa lista -- o passo `ACCESSORIES` só aparecia pra CASA.

## O que mudou

- `QuizState.academyMissingEquipment: Boolean` (default `false`) -- novo campo, não vai pro `Profile`/contrato: é só controle de UI.
- `QuizState.mostraAcessorios` (privado): `CASA || (ACADEMIA && academyMissingEquipment)`. `visibleSteps` e `toProfileOrNull()` passaram a usar essa regra em vez do `environment == CASA` direto.
- `QuizEvent.AcademyMissingEquipmentToggled` -- liga/desliga. Ligar preenche `accessories` com os seis (mesmo default do onboarding de CASA); desligar mantém o que tiver (`toProfileOrNull()` já ignora e manda `null` quando a condição não vale).
- `EnvironmentStep` (Compose): checkbox inline, só aparece sob a opção ACADEMIA quando ela está selecionada -- mesmo padrão do checkbox de supervisão em `AgeStep`.
- Modo edição (`startEditing()`): reabre com o botão marcado quando `p.environment == ACADEMIA && p.accessories != null` -- quem já tinha declarado lista parcial não perde a resposta ao editar.
- String nova: `quiz_ambiente_falta_equipamento` (pt-BR + en).

## O que não mudou

- Nenhum campo novo no contrato/servidor -- reusa `ProfileDto.accessories`, que já aceita lista explícita pra qualquer ambiente desde a fatia servidor desta sessão.
- `AccessoriesStep` (a tela em si) não mudou -- é a mesma Composable, só passou a ser alcançável também pela ACADEMIA.

## Testes

Tier 2 (`QuizStateTest`), três casos novos:
- marcar o botão revela `ACCESSORIES` em `visibleSteps` pra ACADEMIA;
- marcado, a lista declarada vai pro `Profile` (não `null`);
- marcado e tudo desmarcado manda lista vazia, não `null` -- mesma regra de CASA (é resposta real, não "não perguntado").

## Achados

Nenhum achado novo nesta fatia -- as armadilhas já conhecidas (comentário XML com `--`, apóstrofo em string EN) foram evitadas desta vez por checagem proativa antes de cada edit.

## Fecha

Com este item, **"Resto da Fatia 12" fecha inteiro**. Só resta `BodyLimitation` remapeado pras 5 regiões, que está em pausa por decisão separada do Rafael (não é débito desta fatia, é decisão de produto maior, ainda não tomada).
