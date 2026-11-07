# Botão "não tenho este equipamento" (academia)

**Fechado em 2026-11-07.** Terceiro recorte do "resto da Fatia 12".

## O que era

A especificação resolve as academias de condomínio (que não têm todo o equipamento de uma
academia completa) com um botão "não tenho este equipamento" na tela da academia. Decisão já
tomada antes desta fatia: **sem campo novo no contrato** -- reusa `ProfileDto.accessories`
(V66), o mesmo campo que hoje só é perguntado em CASA.

## O achado

A decisão "`accessories != null` já basta" pressupunha que bastava o cliente mandar uma lista
não-nula pra mudar o comportamento. Falso: `Accessory.paraAmbiente` tinha

```kotlin
if (ambiente == TrainingEnvironment.ACADEMIA) entries.toSet() else declarados.toSet()
```

-- ACADEMIA sempre devolvia os seis, **ignorando o parâmetro `declarados` por completo**. Os
dois chamadores de produção (`DeterministicWorkoutGenerator`, `ExerciseRoutes.alternatives`)
ainda normalizavam `profile.accessories ?: emptyList()` *antes* de chamar `paraAmbiente`,
perdendo a distinção null/lista no caminho. Essa é lógica de produção, não dormente -- toda
geração de treino em academia passa por aqui.

## O que mudou

`paraAmbiente` ganhou o parâmetro nullable e a condição mudou:

```kotlin
fun paraAmbiente(ambiente: TrainingEnvironment?, declarados: List<Accessory>?): Set<Accessory> =
    if (ambiente == TrainingEnvironment.ACADEMIA && declarados == null) {
        entries.toSet()
    } else {
        declarados.orEmpty().toSet()
    }
```

`null` continua assumindo os seis (nenhum perfil de academia manda outra coisa hoje -- fallback
aditivo, zero mudança de comportamento até o cliente existir). Lista explícita, mesmo vazia,
agora é respeitada nos dois ambientes. Os dois chamadores de produção só deixaram de normalizar
`?: emptyList()` -- a diferença passa direto pra `paraAmbiente`.

## Achado ao arrumar os testes

`MotorV3Varredura720Test.entradaDe()` chamava `paraAmbiente(ambiente, emptyList())`
incondicionalmente (para ACADEMIA e CASA). Sob a semântica antiga isso não importava (ACADEMIA
ignorava o parâmetro); sob a nova, `emptyList()` explícito pra ACADEMIA teria virado conjunto
vazio, quebrando a varredura dos 720 perfis. Troquei para `paraAmbiente(ambiente, null)` --
preserva o número de hoje nos dois ambientes (CASA: `null` e `emptyList()` davam o mesmo
resultado antes e continuam dando).

## Testes

`AccessoryTest.kt`: os 2 casos de ACADEMIA foram reescritos (o antigo "ignora o declarado" virou
o caso de falha coberto pelo novo comportamento). `AcessoriosIntegrationTest.kt`: +1 teste Tier 3
provando contra o catálogo real que a academia com declaração parcial filtra igual casa.

`:server:test`/`:server:integrationTest` -- a confirmar pelo Rafael.
