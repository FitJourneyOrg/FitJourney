# sugestao-de-progressao — histórico

_Fechada em 2026-11-06._

## O que é

Nenhum dos dois eixos do motor v3 (carga, repetição) expunha a sugestão de progressão pro
cliente — o motor rodava só como registro invisível, alimentando deload/promoção, mas quem
treina não via meta nenhuma na tela. Achado ao planejar o Contrato da fatia
"progressao-por-repeticao" (2026-11-06) e priorizado por ser o maior retorno sobre o que já
existia construído (RIR por série, calibração/progressão de carga, progressão por repetição).

## Decisões

- **Falha vira sugestão vazia, nunca erro no `GET`** — diferente do `comOrigem()` (que propaga
  falha do programa pro `AppResult` da rota). Sem `Profile`/`Goal`, sem papel, sem linha no
  catálogo, ou erro de leitura em qualquer repositório: o exercício fica com os três campos
  `null`, o `GET` continua 200. É enriquecimento, não dado essencial.
- **Eixo carga sem `Progressao.Estado` (calibração pendente) → vazio**, não um palpite — mostrar
  uma carga antes da calibração seria inventar um número que o motor não calculou. Eixo de
  repetição sem `Estado` (1ª exposição) → sugere a `PrescricaoV3.Faixa` ORIGINAL, porque não
  existe calibração por reps (decisão já tomada na fatia anterior).
- Contrato: campos flat e nullable em `WorkoutExerciseDto` (`cargaSugeridaKg`,
  `repsMinSugerido`, `repsMaxSugerido`), mesmo estilo de `rir`/`papel`.
- Só `GET /workouts/{id}` ganha o enriquecimento — é o único ponto que serve o DTO completo pra
  quem vai executar. `POST`/`PUT` não.
- A sugestão nunca viaja do cliente pro servidor: `WorkoutMapper.toDto()` (nos dois lados,
  servidor e cliente) não escreve esses três campos de volta — o servidor recalcula a cada
  `GET`, não lê o que o cliente manda.

## Desenho

- `SugestaoDeProgressao` (núcleo puro, `engine/v3/`): `calcular(incremento, equipment, papel,
  goal, estadoCarga, estadoRepeticao): Sugestao`. Mesmo piso de eixo do
  `ProgressaoAplicadaService`.
- `SugestaoDeProgressaoService` (novo): busca `Profile.goal`, `ExerciseRepository.paraProgressao`
  e **só o estado do eixo que o catálogo decide** (evita leitura inútil e log duplicado numa
  falha) — chama o núcleo puro, devolve o `WorkoutDto` enriquecido ou intacto.
- `WorkoutRoutes.kt`: mais um `.flatMap` depois de `completarOrigem`.
- Cliente: `WorkoutExercise` (domínio) → `WorkoutMapper.toDomain()` (só leitura) → `SetEntry`/
  `ExercicioEmExecucao` (repetido por série, mesmo padrão de `exerciseName`/`thumbRef`) →
  `WorkoutSessionViewModel.load()` → texto "Meta: X kg" / "Meta: X–Y reps" no
  `CartaoDoExercicio`, usando `formatarPeso` (promovido de `internal` pra público).

## Migration

Nenhuma. Confirmado também no cliente: `WorkoutLocalDataSource` guarda `exercises` como JSON
serializado (`WorkoutExerciseDto.serializer()`) — os três campos novos atravessam o cache local
sem mudança de schema.

## Defeitos achados e corrigidos durante a fatia

- **Serviço buscava os dois eixos de estado sempre**, mesmo quando o catálogo já decidia que só
  um se aplica — além de desperdiçar uma leitura, uma falha de banco gerava DOIS logs pro mesmo
  exercício (pego pelo teste de Tier 2, `erro de banco no estado de carga`). Corrigido: busca só
  o eixo decidido pelo `incremento`/`equipment`.
- **Strings novas (`sessao_meta_reps`) usaram en-dash (`–`)** — proibido pelo #37 seção 8 em
  qualquer idioma. Trocado por hífen ASCII.
- **Inserção sem comentário próprio empurrou a "distância do comentário" de duas strings não
  relacionadas** (`sessao_retomar_descanso`, `sessao_vazia`) além do limite — `CatalogoDeStrings`
  conta strings desde o último `<!-- comentário -->` XML, não linhas. Corrigido com um comentário
  dedicado pras duas strings novas, que também devolveu as strings seguintes à contagem original.

## Achados sem relação com esta fatia, registrados em `debitos.md`

- `SemanticaDeCargaIntegrationTest` falhando por `Semear.kt` semear `category = "STRENGTH"`
  (valor que não existe mais em `ExerciseCategory`) — pré-existente, não investigado.
- `sessao_rir_rotulo` faltava na allowlist de "chaves idênticas pt-BR/en" do
  `CatalogoEmInglesTest` — corrigido (uma linha, mesma natureza das entradas já na lista).

## Testes

Tier 1 (motor puro `SugestaoDeProgressaoTest`, agrupamento `AgrupamentoDaSessaoTest`, mapper
`WorkoutMapperTest`), Tier 2 (`SugestaoDeProgressaoServiceTest`, `WorkoutSessionViewModelTest`).
Tier 3 conscientemente não escrito — sem migration/coluna nova, a composição da rota não tem
lógica própria além do que o Tier 2 já cobre com fakes reais das mesmas interfaces.
