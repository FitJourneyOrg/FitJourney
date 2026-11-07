# Mostrar sugestão de nível (BAND/HAND_GRIP) na tela de execução

**Fechado em 2026-11-07.**

## O que era

Débito aberto na fatia "progressão por nível" (2026-11-06): o eixo de carga e o eixo de
repetição (BODYWEIGHT) já mostravam a sugestão de progressão na tela de execução
("Meta: X kg" / "Meta: X–Y reps"); o 3º eixo, nível (BAND/HAND_GRIP), não.

## O que mudou

`SugestaoDeProgressao.calcular` ganhou um novo ramo:

```kotlin
equipment == "BAND" || equipment == "HAND_GRIP" -> {
    val faixa = PrescricaoV3.de(goal, papel)
    Sugestao(repsMin = faixa.repsMin, repsMax = faixa.repsMax)
}
```

Sem `Estado` nenhum envolvido -- nesse eixo a faixa de reps **nunca muda**; o que sobe/desce é o
`nivelAtual` (abstrato, nunca exposto como número). A faixa certa já está em `PrescricaoV3.de`.

## Achado ao investigar

O débito, como estava escrito, pedia estender `SugestaoDeProgressao`/`WorkoutExerciseDto`/
`WorkoutSessionViewModel`/`CartaoDoExercicio` com um 3º caso. Lendo a cadeia completa antes de
tocar em qualquer coisa:

`WorkoutExerciseDto` → `WorkoutMapper.comSugestao()` → `WorkoutSessionViewModel`/`State` →
`WorkoutSessionScreen.kt`

...descobri que o lado do cliente já é genérico: a tela decide o que mostrar pelo **campo que
não é nulo** (`cargaSugeridaKg` vs. `repsMinSugerido`/`repsMaxSugerido`), não por qual eixo
gerou o dado. Não precisou mudar nada lá -- só um comentário que dizia "BAND/HAND_GRIP sempre
fica sem sugestão" estava desatualizado e foi corrigido.

O débito real era bem menor que o texto registrado: uma função pura, 4 testes Tier 1 novos, mais
um teste Tier 2 provando que o serviço nunca consulta `ProgressaoRepository` nesse eixo (não
precisa -- a faixa não depende de estado).

## Por que não tem migration

Nenhuma coluna nova. `cargaSugeridaKg`/`repsMinSugerido`/`repsMaxSugerido` já existiam no DTO
desde a fatia "sugestão de progressão" (2026-11-06); o cache local já serializa o DTO inteiro em
JSON, então o campo novo atravessa sem mudança de schema.

## Testes

- `SugestaoDeProgressaoTest.kt`: 4 novos (BAND e HAND_GRIP corretos; `papel == null` → vazia;
  `equipment` desconhecido → vazia, cobrindo o `else` que o BAND antes exercitava).
- `SugestaoDeProgressaoServiceTest.kt`: 1 novo, end-to-end, com
  `FakeProgressaoRepository().apply { falhar = true }` provando que o eixo nível nunca toca o
  repositório.

`:server:test` verde (confirmado pelo Rafael).
