# Declaração de custo do motor — fatia fechada (2026-10-31)

> Movido de `docs/trabalho/declaracao-de-custo-do-motor.md` ao fechar o ciclo. Decisões e
> desenho completos ficaram no plano original (ver git log); aqui só a narrativa e os achados.

## O que entregou

Fecha (c)/(d) de "o motor sabe, o app não mostra" (`fontes/debitos.md`, P0) — os quatro sinais
da camada de honestidade (custo da fase de ênfase, rótulo de manutenção, rótulo de não treinado,
`pisoDiretoNegado`) ganharam superfície de produto, numa tela só:

- **Servidor**: `ProgramAudit.Causa` ganhou `MANUTENCAO` (a mais branda, depois de
  `PISO_DIRETO_NEGADO`); `VolumeTargets.custoDoFoco()` (nova) declara os `TargetMuscle` reduzidos
  ao piso pela fase de ênfase, reusando a isenção BACK/EMPURRAR extraída pra
  `isentoDoPisoPorFoco()` — mesma condição que `weeklySets()` já aplicava, sem duplicar.
  `MotorV3Pipeline.Resultado` expõe os dois (`avisos`, `custoDoFoco`).
- **Contrato**: `ProgramDto.avisos: List<ProgramAvisoDto>` e
  `grupamentosReduzidosPeloFoco: List<String>`, ambos com default `emptyList()` — mudança 100%
  aditiva, o `WorkoutGenerator` de produção (`DeterministicWorkoutGenerator`/`StructureEngine`)
  não precisou mudar.
- **Persistência, dos dois lados** (achado durante o desenho, não previsto no plano original —
  ver "Achados" abaixo): V73 no servidor, `10.sqm` no cliente, mesmo padrão JSON-em-TEXT de
  `focusMuscles`/V56/4.sqm.
- **Cliente**: `Program` (domínio) e `ProgramMapper` propagam; card novo em
  `ProgramDetailScreen` (`AvisosDoPrograma`), que some sozinho quando as duas listas estão
  vazias — hoje sempre, motor dormente. `Rotulos.kt` ganhou `rotuloDoMusculoAlvo()` (reusa
  `MuscleGroup`/`ExerciseCategory` pros 8 nomes que já tinham rótulo; só QUADS/POSTERIOR são
  string nova) e `rotuloDaCausaDeAviso()`.
- **Fica dormente**: nenhum aviso aparece até o v3 virar o gerador de produção — decisão
  separada, registrada em "Troca de produção do `StructureEngine`" (`debitos.md`). A plumbing
  inteira (schema dos dois lados, contrato, UI) está pronta e testada; falta só o motor que a
  alimenta estar no caminho de produção.

## Achado durante o desenho: a plumbing crescia a cada autoauditoria

O plano original (primeiro rascunho) dizia "sem migration" e "sem teste de integração novo".
Pedido do Rafael pra reler o próprio plano contra o código antes de codar achou os dois errados:
`focusMuscles` já prova que o padrão do projeto é persistir o snapshot da geração, não recomputar
— sem migration, `avisos` sumiria no próximo GET do mesmo programa; e não havia nenhum teste de
integração tocando `Program`/`ProgramRepository`, então C2 não permite pular o Tier 3 numa fatia
que toca schema. Os dois furos foram fechados antes de codar (V73 + `ProgramIntegrationTest`).

**O mesmo buraco existia no cliente e não estava no plano revisado**: o cache local
(`ProgramLocalDataSource`, tabelas reais desde a fatia "programa sai do kv_cache") grava
`focusMuscles` com o mesmo padrão JSON-em-TEXT — e `avisos`/`grupamentosReduzidosPeloFoco`
teriam o mesmo destino (sobrevivem no `ProgramDto` em memória, somem do cache a cada reabertura
do app) se a `10.sqm` não tivesse sido escrita durante a implementação da camada Cliente. Lição:
"mesmo padrão de `focusMuscles`" precisa ser conferido nos DOIS lados que persistem o DTO —
servidor E cliente —, não só no que o plano cita primeiro.

## Achado durante o fechamento: arquivo de teste corrompido entre sessões

`ProgramAuditTest.kt` chegou ao fechamento com os dois testes novos de `MANUTENCAO` duplicados
três vezes e um teste mais antigo (`avisos de manutencao usa o MV...`) com o corpo cortado no
meio — resultado de uma edição de sessão anterior que não fechou corretamente, só percebido
porque o Rafael tentou compilar e o erro (`Return type mismatch: expected 'Boolean', actual
'Unit'`) não bateu com nenhum teste que devesse existir. Lição: depois de uma retomada de sessão
(principalmente com resumo de contexto), reler o arquivo inteiro antes de assumir que o estado
descrito no resumo bate com o disco — o resumo registra INTENÇÃO, não necessariamente o byte
final gravado.

## Achado durante o fechamento: dois testes novos "contaminados" por músculo não blindado

Os dois testes novos de `MANUTENCAO`/`avisos` só davam `alvo`/`entregue` ao músculo que
exercitavam. `ProgramAudit.avisos()` reage a `efetivo == 0` (não ao `alvo`) pra decidir
`NAO_TREINADO` — então todo `TargetMuscle` fora do mapa também saía `NAO_TREINADO` por conta
própria, contaminando `avisos.first()`/`.last()`/`.single()`. Corrigido blindando os demais
músculos com `alvo`/`entregue` bem acima do mínimo, mesmo padrão que o teste `tudoBem` já usava.
Lição: qualquer teste de `avisos()` que não cubra os 10 `TargetMuscle` precisa blindar
explicitamente os que não são o alvo do teste — "não mencionei esse músculo" não é "esse músculo
está bem".

## Achado durante o fechamento: duas armadilhas de catálogo/Compose já documentadas, redescobertas

- **Apóstrofo sem escapar em `values-en/strings.xml`** (`level's target`) quebra o AAPT
  ("Invalid unicode escape sequence"). O catálogo já tinha o padrão certo (`\'`, ver
  `programa_detalhe_excluir_texto`), só não segui.
- **`stringResource` dentro do lambda de `joinToString`** não compila (`@Composable
  invocations can only happen from the context of a @Composable function`) — `joinToString` não
  é inline, ao contrário de `forEach`/`map`. Já documentado em `RationaleDePrograma.kt` e
  `GrupoDetalheScreen.kt` ("`map` ANTES de `joinToString`"); bastava ter grepado por
  `joinToString` antes de escrever o card.
- **Valor de string novo colidindo com um que já existia** (`enum_musculo_alvo_calves` =
  "Panturrilhas" = `enum_categoria_calves`) derrubou `CatalogoDeStringsTest > os textos
  repetidos sao exatamente os declarados` — o teste existe exatamente pra isso. Corrigido
  reaproveitando `enum_categoria_calves` em vez de duplicar, mesmo padrão que os outros 7
  `enum_musculo_*` já seguiam com `MuscleGroup`.

Nenhum dos três é bug de produto; os três são erro de quem escreveu (eu), pegos pelos testes e
pelo build real que o Rafael roda — exatamente o papel que C2 e os catálogos têm.

## Fora de escopo (ficou aberto, outra camada)

- RIR não chega do cliente — débito (a) de "o motor sabe, o app não mostra", ainda P0, não
  tocado nesta fatia.
- Troca de produção do `StructureEngine` pelo v3 — decisão separada do Rafael, registrada em
  `debitos.md`. Esta fatia deixou a plumbing pronta (schema + contrato + UI), não decidiu ligar.
- Vocabulário de músculo incompleto (`MuscleGroup` sem granularidade de perna) — é por isso que
  a tela expõe `TargetMuscle`, não `MuscleGroup`; o débito em si continua aberto.
- Plano do RIR para a sessão seguinte — escrito separado, em `docs/trabalho/rir-plano.md`
  (pedido do Rafael, feito no fechamento desta fatia).
