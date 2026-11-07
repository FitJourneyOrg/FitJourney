# Progressão por nível (BAND/HAND_GRIP) — 2026-11-06

## O problema

BAND (15) e HAND_GRIP (1) — 16/147 do catálogo — continuavam sem progressão nenhuma desde a fatia
"progressao-por-repeticao", registrado como débito P0 à parte: "resistência de elástico é por
NÍVEL (discreto), não por reps, e o perfil não modela o kit de elásticos do usuário — resolver
exige essa modelagem primeiro, não é só trocar o eixo."

## Decisão (Rafael)

Essa premissa foi reaberta e descartada: **nível RELATIVO, sem modelar o kit do usuário** — o
mesmo truque que já funcionou pra `BODYWEIGHT` (progressão sem saber a carga real, só a direção
subiu/desceu). `nivelAtual` não tem significado absoluto (não é "elástico vermelho" nem newton
nenhum) — só conta quantas trocas de resistência a pessoa já fez desde que começou o exercício. O
app nunca pergunta nem sabe qual elástico é, só fala "sobe"/"desce um nível". Isso elimina o
pré-requisito de modelagem por completo e encolhe a fatia pro tamanho da "progressao-por-repeticao"
(só servidor).

## Desenho

`ProgressaoPorNivel` (novo, paralelo a `ProgressaoPorRepeticao` e a `Progressao`, não emenda de
nenhuma): `Estado(nivelAtual: Int = 1, exposicoes, semSubir)`. Diferença-chave pro eixo de
repetição: lá a FAIXA de reps sobe/desce; aqui a faixa (`PrescricaoV3.Faixa`) fica **FIXA** a vida
toda — é o `nivelAtual` que muda. Bate o topo da faixa fixa com RIR no alvo → sobe de nível; cai
abaixo do piso → desce, nunca abaixo do nível 1. Mais fiel à prática real: a pessoa continua
treinando no mesmo range de reps, só troca de elástico quando o atual fica fácil ou difícil demais.

`AncoraDoIniciante.Evento` ganhou `Nivel(decisao)` — terceiro eixo contando igual aos outros dois
pro contador de estagnação/DELOAD (mesma decisão do Rafael da fatia anterior, agora estendida).

`ProgressaoRepository`/`ProgressaoRepositoryImpl` ganharam `buscarPorNivel`/`registrarPorNivel` —
mesma linha/PK de `exercise_progression`, coluna nova `nivel_atual` (V77). `progression_event.tipo`
ganhou `SOBE_NIVEL`/`MANTEM_NIVEL`/`DESCE_NIVEL`.

`ProgressaoAplicadaService.aplicarExercicio`: `incremento == null` e `equipment` em
`{BAND, HAND_GRIP}` → `aplicarExercicioPorNivel` (novo ramo, ao lado do `BODYWEIGHT`).

## Achado não relacionado, corrigido

`FakeProgressaoRepository.registrarPorRepeticao` zerava `umRms[chave] = null` incondicionalmente,
ignorando `estado.sinalForca` — ficou desatualizado desde a fatia "ancoras-avancado-bodyweight"
(sem efeito até agora: nenhum teste exercitava `primeiroUmRmNoMesociclo` com âncora bodyweight via
fake). Corrigido ao tocar na mesma função por este motivo.

## Testes

- **Tier 1** (`ProgressaoPorNivelTest`): topo sobe nível, abaixo desce, nunca abaixo de 1, RIR nulo
  não bloqueia o topo, 1ª exposição já começa no nível 1, sessão vazia falha.
- **Tier 2** (`ProgressaoAplicadaServiceTest`): BAND e HAND_GRIP caem no ramo de nível; 1ª
  exposição sem calibração.
- **Tier 3** (`ProgressaoIntegrationTest`, Postgres real): idempotência (D-2), histórico decodifica
  `_NIVEL` sem colidir com os outros dois eixos, estado por pessoa/exercício, banco recusa
  `nivel_atual <= 0`.

`:server:test` e `:server:integrationTest` verdes (confirmado pelo Rafael).

## Fora de escopo

- Modelar o kit real de elásticos do usuário — descartado como pré-requisito, não como ideia; pode
  voltar como feature própria se algum dia for pedido.
- Mostrar a sugestão de nível na tela de execução — débito próprio, pequeno, mesmo padrão do
  "mostrar sugestão" dos outros dois eixos (já fechado pra carga/reps).
- Sinal de força (1RM-like) pro eixo de nível — nenhum BAND é âncora do avançado hoje.
