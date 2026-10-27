# Motor v3 — Fatia 4: estrutura da semana

_Fechada em 2026-10-23._

## O que foi feito

`engine.v3.WeekStructure` — etapa [4] do motor v3: divisão a partir do número de dias e
frequência efetiva (quantos treinos por semana cada músculo recebe, e quais séries em qual dia).

- `divisao(dias)` espelha `spec_v3.py::DIVISOES` (2–3 = FULL_BODY, 4 = UPPER_LOWER, 5 = UL_PPL,
  6 = PPL); fora de 2..6 lança erro explícito, mesmo padrão da Fatia 2.
- `distribuirPelosDias(alocado, dias)` espelha `montar_semana()` termo a termo: sobe ao piso de
  isolamento (2) quando o total é menor; frequência efetiva = `max(1, min(dias que treinam,
  total ÷ piso do papel))`, subindo se `total/freq` passar do teto de 11 séries/sessão;
  distribui pelos dias escolhidos (`espalhar`, desempate por `ordinal` do músculo).
- Validado rodando `tools/motor_sim/motor_v3.py::montar_semana()` com as 3 saídas reais de
  `alocar()` da Fatia 3 (sem escassez, capacidade 40, capacidade 65,33) — saída exata, sem
  tolerância. `WeekStructureTest.kt`, 8 testes, todos verdes.

## Correção de escopo na abertura

Eu tinha escrito, ao fechar a Fatia 3, que a Fatia 4 "depende só da Fatia 2" — errado.
`distribuirPelosDias()` recebe `alocado: Map<TargetMuscle, Int>`, que só existe depois de
`PriorityAllocation.alocar()` (Fatia 3). Corrigido no plano e nos testes antes de codar.

## Auditoria pedida ao fechar a Fatia 3

Rodei `mv.gerar(p)` nos 720 perfis da matriz e contei `soma(alocado) > capacidade`: **154/720
(21,4%) excedem — perfil comum, não extremo.** Concentrado em 2–3 dias (108 + 43 = 151 dos 154);
zero em 5 e 6 dias. Quase igual entre níveis. Excesso máximo 10,67 séries. Registrado como
contexto da Fatia 8 (o laço de corte por tempo) em `trabalho/motor-v3-desenho-e-fatias.md` — esta
fatia e a 5 processam `alocado` como ele vem, inclusive excedendo a capacidade, porque é assim
que o oráculo funciona hoje.

> **Recalculado após alinhar convenção do mesociclo (Fatia 7, 2026-10-23):** o oráculo tratava
> `primeiro_mesociclo` como `bool = False` por padrão -- nenhum perfil da matriz o setava --
> enquanto o Kotlin (`CapacityModel.fatorIdade`, Fatia 2 `[REGRA]`) trata `mesocicloAtual == null`
> como 1º mesociclo. Corrigido (`mesociclo_atual: Optional[int] = None`, nulo = 1º, igual ao
> Kotlin). Com a correção: **176/720 (24,4%)** excedem -- 120 em 2 dias, 53 em 3, 3 em 4, zero em
> 5 e 6. Por nível: BEGINNER 54 (sem mudança), INTERMEDIATE 60, ADVANCED 62. Excesso máximo
> **13,8 séries** (era 10,67). O número antigo (154/720) veio do fator de idade não reduzindo o
> teto de nenhum 65 anos intermediário/avançado -- bug, não característica.
>
> **Por que a validação "idêntica" das Fatias 2 e 3 não pegou isso:** nenhuma das duas cruzava
> Kotlin contra Python num perfil 60+ com mesociclo não setado, pelo desenho do próprio teste.
> `CapacityModelTest` (Fatia 2) testava `fatorIdade` do Kotlin contra as 27 células da tabela da
> especificação (documento, não Python) e os fatores "isolados" -- nunca chamou
> `teto_sessao()` do oráculo lado a lado para comparar. `AllocationTest` (Fatia 3) comparava
> `alocar()` do Kotlin contra `alocar()` do Python "com saída EXATA" -- mas `alocar()` recebe a
> capacidade **já calculada** como parâmetro; não chama `teto_sessao()`/`primeiro_mesociclo`
> internamente, então o teste passava por fora do trecho com o bug, não através dele. A auditoria
> dos 720 perfis acima também não cruzava os dois lados -- rodava só `mv.gerar(p)` (Python) e
> contava direto, sem gerar o mesmo perfil no Kotlin para comparar. O primeiro lugar em que os
> dois pipelines completos (perfil → saída) foram comparados ponto a ponto foi o teste de 12
> perfis da Fatia 6 (`MotorV3FidelidadeCatalogoRealIntegrationTest`) -- mas seus 12 perfis usam
> idade=30 fixo, então nem esse teste cruzava um 60+. O bug só apareceu quando a Fatia 7 construiu
> o gatilho do modo conservador e precisou da mesma convenção "nulo = 1º mesociclo" nos dois
> lados -- foi aí que a divergência ficou visível.

## Caso GLUTES — conformidade, não decisão

GLUTES com 4 séries e piso 3 treina 1 vez por semana (todas as 4 séries juntas em um só dia), não
2 sessões de 2. É o mesmo caso que causa os 2 vermelhos do diferencial contra `modelo_a_mao.py`
(Fatia 9) — este fatia só prova que o Kotlin faz o que o oráculo faz; a pergunta de qual lado está
certo continua aberta na Fatia 9.

## Fora de escopo

- **Calendário** (dia da semana por posição do ciclo) — concern de agenda/apresentação, não de
  motor. `divisao()` já decide quais músculos em cada posição; rotular a posição com "segunda"/
  "quinta" não muda nenhuma decisão de volume ou frequência. `regras/03-agenda-e-cronograma.md`
  existe e não trata disso — fica para quem wireia agenda, sem reabrir esta fatia.
- Corte por escassez extrema (os 24,4%, recalculado na Fatia 7) — Fatia 8.
- GLUTES × modelo à mão — Fatia 9.

## Testes

`WeekStructureTest.kt`, 8 testes, 0 falhas (`:server:test`, 2026-10-23).
