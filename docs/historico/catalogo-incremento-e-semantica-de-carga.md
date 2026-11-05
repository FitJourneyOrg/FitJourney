# Catálogo: incremento e semântica de carga — fatia fechada (2026-11-05)

> Movida de `docs/trabalho/catalogo-incremento-e-semantica-de-carga.md` ao fechar o ciclo. Decisões
> e desenho completos ficaram no plano original (ver git log); aqui só a narrativa e os achados.
> Regras e as duas Leis → `regras/01-motor-de-geracao.md` · ADR → `arch/motor-v3-semantica-de-carga.md`
> · débitos → `fontes/debitos.md` · V74 → `migrations/INDICE.md`.

## O que entregou

Os três campos que `Progressao.decidir()`/`calibrar()` exigem e que **não existiam em lugar nenhum
do código** — verificado por grep: os nomes só apareciam como parâmetro da própria `Progressao`, sem
produtor. V74 (3 colunas + 3 CHECKs + autoconferência), `ExercisesTable`, `Exercise`,
`ExerciseRepositoryImpl.toExercise()`, `SemanticaDeCargaIntegrationTest` (8 testes, 5 caminhos de
falha). Contrato e cliente: **nada**, por desenho — é dado do motor, que roda no servidor.

Verde confirmado pelo Rafael em 2026-11-05: `:server:integrationTest` 8/8 na classe nova,
`:server:test` **814 testes, 0 falhas**. As outras três suítes não tocam código afetado.

## Como a fatia nasceu: o plano anterior presumia que o dado existia

O `rir-plano.md` (handoff escrito em 31/10) descrevia dois buracos conectados — cliente não manda
RIR, servidor não chama o motor — e a pergunta 1 dele era "de onde vêm `Papel`/`incremento`/
`barraLivre` hoje, catálogo ou sessão?". A resposta, medida em 05/11: **de lugar nenhum.** O único
"incremento" do código era o passo fixo de 2,5 kg dos botões `-`/`+` da UI, igual para todo mundo.
Daí a quebra em duas fatias: esta modela o dado, a do RIR liga o fio.

> **Pergunta de handoff que presume a existência do dado produz plano que subestima a fatia.**

## O que a simulação decidiu (e por que ela existiu)

`tools/motor_sim/sim_incremento.py` — port fiel do `Progressao.kt`. Nasceu de um pedido do Rafael
("um exemplo com cada equipamento, mostre os cálculos") depois de ele rejeitar a primeira tabela de
incrementos com quatro perguntas que a desmontaram: granularidade física × passo prescritivo, carga
de halter por peça × somada, o que `decidir()` faz com NULL, e se o app impõe a escada. **O ficheiro
ficou** porque as duas Leis que saíram dele são critério de aceite da fatia do passo proporcional.

- **`MACHINE` = 2,5 e não 5** foi decidido por medição, contra a minha própria proposta inicial de 5:
  no Hack a 80 kg os dois chegam ao **mesmo** lugar (90 kg em 20 sessões); na Cadeira Abdutora a
  20 kg o 2,5 estabiliza em 22,5 e **o 5 fica preso em 20→25→20 para sempre** (10 SOBE / 10 DESCE,
  carga final = inicial). O 5 não compra nada no topo e perde tudo embaixo.
- **`DUMBBELL` = 1,0 e não 2,0**: com 2 kg o loop **nunca converge** (14 sessões, 7 subidas, carga
  final igual à inicial).
- As duas Leis (converge até ~20% da carga; freio da Regra 4 só acima de ~12,5× o incremento) estão
  em `regras/01`, com a aritmética.

## Achados — o que a fatia descobriu que não era o assunto dela

1. **`Barra Fixa Assistida na Máquina` tem carga INVERTIDA, e o motor a facilitaria.** Mais peso =
   mais ajuda; `SOBE` faz `carga + incremento`. É o caminho do iniciante até a barra fixa, e
   regrediria **calada** conforme a pessoa ficasse mais forte. Decisão do Rafael: **não é débito, é
   dano ativo** — a flag entrou nesta fatia, e a troca de sinal virou item **bloqueante** da fatia do
   RIR, com caso de teste nomeado lá. "Direção errada é pior que magnitude errada."
2. **42 de 147 (28,6%) do pool sem progressão nenhuma** (`BODYWEIGHT` 26, `BAND` 15, `HAND_GRIP` 1) e
   **4 de 26 âncoras do avançado estruturalmente mortas** — registrados como **dois P0 separados**,
   por decisão: o segundo é bug de funcionalidade existente e seria escondido dentro do primeiro.
3. **A Regra 3 não protege no caso para que foi escrita** e **MANTEM em RIR 0 deixa a pessoa treinando
   à falha** (medido: halter 13 kg, 8 sessões seguidas a 8–9 reps com RIR 0, freio anulado porque
   13 < 32,5). Gravado como aviso no `rir-plano.md`: **a fatia do RIR não conserta a qualidade da
   progressão** — hoje é motor imóvel, com o RIR vira oscilação.
4. **Curadoria por nome não serve para a família aditiva:** os 11 `BODYWEIGHT` chamados
   "... com Peso Corporal" significam *sem carga*, o oposto. Os aditivos reais são 2, ambos
   `WEIGHT_PLATE`.
5. **`docs/` está no git**, ao contrário do que a regra de limpeza presume (`git ls-files docs/` lista
   `ESTADO.md`, `CONVENCOES.md`, `arch/README.md`…). "Apagar em `docs/` é definitivo" vale para
   `docs/trabalho/` e para o não rastreado, não para arquivo já commitado.

## Defeitos meus, corrigidos no caminho

- **8 testes falhando na mesma linha, e a causa engolida por mim.** O helper fazia
  `findAll(...) as AppResult.Success` e recebeu `Failure`; o `ClassCastException` não dizia nada, e
  um problema de porta virou oito falhas idênticas. Causa real: **`runBlocking` dentro de helper
  chamado por `@Test` não-suspenso** — o padrão do vizinho é `@Test fun x() = runBlocking { }`.
  Virou convenção em `CONVENCOES.md`. Lição mais geral: teste de migration que depende da camada
  inteira para afirmar que uma coluna existe mistura dois defeitos numa falha só — agora **um** teste
  passa pelo repositório (o que prova o `toExercise()`) e sete leem a coluna direto.
- **`NUMERIC(5,2)` trocado por `DOUBLE PRECISION`** depois de olhar o vizinho: todo valor em kg aqui
  é `double` (`carga_kg`, `um_rm_kg`, `weight_kg`), e é o tipo com que `Progressao` compara. `NUMERIC`
  obrigaria `BigDecimal → Double` no mapeamento.
- **O plano dizia que o `ExerciseMapper` mapearia os três campos. Errado:** aquele é
  `Exercise → ExerciseDto` e **não mudou**; a conversão linha→modelo mora no
  `ExerciseRepositoryImpl.toExercise()`.
- **O Tier 1 de lógica pura foi retirado do plano por decisão**, não esquecido: a derivação mora em
  SQL, e reescrevê-la em Kotlin só para ter um Tier 1 criaria uma segunda fonte da verdade para a
  mesma regra. As fábricas de teste derivam `barraLivre` do `equipamento` que o próprio teste declara
  — não default silencioso, que é a armadilha do achado da Fatia 11.
