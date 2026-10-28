# Motor v3 — Fatia 8: corte proporcional na alocação (em andamento, decisão de 2026-10-24)

Oitava fatia do motor v3, etapa [8]: corte por tempo (laço "gera com um teto, estima a duração
e, se passar, reduz o teto em 1 série e refaz a alocação" — `motor-v3-ESPECIFICACAO.md` linha
598). A fatia estava bloqueada desde a Fatia 5 por uma decisão de prescrição pendente: **os
pisos de todos os músculos somam 61 séries/semana no iniciante e passam da capacidade em 100%
dos perfis de 2 e 3 dias (288/288)**, mesmo antes de qualquer corte por tempo entrar em jogo.
Este documento registra a decisão tomada (corte proporcional) e o que ainda falta dentro da
própria Fatia 8. **O laço de corte por tempo em si ainda não foi construído** — esta fatia, até
aqui, só resolveu o bloqueio de entrada.

## O bloqueio

Critério de pronto da fatia: 0/720 perfis com `entregue > capacidade` depois do corte. Isso
exige que a especificação diga o que cede quando nem os pisos cabem — hoje a `ORDEM_SACRIFICIO`
(antebraço, panturrilha, abdômen, glúteo, bíceps, tríceps, ombro) nunca inclui peito, costas,
quadríceps e posterior, e são esses quatro que sobram sem lugar quando o resto já foi
sacrificado por completo.

Baseline medido nos 288 perfis de 2–3 dias, por dias × duração:

| dias | duração | `alocado > capacidade` | `entregue > capacidade` |
|---|---|---|---|
| 2 | 45 min | 46/48 | 46/48 |
| 2 | 60 min | 46/48 | 43/48 |
| 2 | 75 min | 28/48 | 38/48 |
| 3 | 45 min | 43/48 | 44/48 |
| 3 | 60 min | 5/48 | 19/48 |
| 3 | 75 min | 5/48 | 24/48 |
| **total** | | **173/288** | **214/288** |

## As quatro saídas medidas

Rafael propôs quatro saídas de prescrição e pediu números antes de decidir, em vez de escolher
"a olho":

- **(a) Piso só para os grupos grandes**, pequenos fora de `treinaveis`. Efeito mínimo: os
  pequenos já estavam perto de zero no baseline (170/288 alocado, 175/288 entregue).
- **(b) Alternar entre semanas** (grupo grande num mesociclo, outro no seguinte). Não medido —
  exige estado de mesociclo (Fatia 10) que não existe ainda no oráculo desta fatia; descartado
  por dependência, não por prescrição.
- **(c) Baixar o piso de composto de 3 para 2 séries em 2–3 dias**. Efeito pequeno (173/288
  alocado — sem mudança —, 203/288 entregue).
- **(d) Estender a `ORDEM_SACRIFICIO` com os 4 grandes**, sequencial (um músculo inteiro por
  vez) ou proporcional (reduz todos simultaneamente, proporcional ao inverso do
  `PESO_PRIORIDADE`).

A opção (d) foi a única que resolveu `alocado > capacidade` de verdade (3/288, puro ruído de
arredondamento — 3 perfis MAINTAIN/BEGINNER/3 dias/CASA, excedente de exatamente 0,20 série
cada). A escolha ficou entre sequencial e proporcional:

| cenário | `alocado>cap` | `entregue>cap` | CHEST | BACK | QUADS | POSTERIOR |
|---|---|---|---|---|---|---|
| baseline | 173/288 | 214/288 | 7,92 | 9,56 | 5,92 | 5,84 |
| (d) sequencial | 3/288 | 160/288 | 5,06 | 7,36 | 4,27 | 3,81 |
| (d) proporcional | 3/288 | **47/288** | 7,08 | 9,22 | 4,99 | 4,70 |

O sequencial resolve `alocado` mas pouco de `entregue` (a inflação do preenchimento pelo
catálogo continua), e faz os 4 grandes perderem 25–36% do volume de baseline. O proporcional
resolve muito mais de `entregue` (47/288, contra 160/288) e os 4 grandes ficam muito mais perto
do baseline.

### Sensibilidade da rampa (rejeitada como ideia, mantida como medição)

Terceira medição pedida: e se a rampa de aquecimento contasse só uma vez por sessão, em vez de
uma vez por padrão pesado — liberando mais tempo? Testado com (d) proporcional e
`OVERHEAD_MIN` estimado em 9/9 em vez de 16/11 (estimativa derivada para o teste, não número
oficial da especificação):

| cenário | `alocado>cap` | `entregue>cap` | CHEST | BACK | QUADS | POSTERIOR |
|---|---|---|---|---|---|---|
| (d) proporcional | 3/288 | 47/288 | 7,08 | 9,22 | 4,99 | 4,70 |
| rampa 1×/sessão | 3/288 | **84/288** | 7,49 | 9,43 | 5,16 | 5,08 |

Contraintuitivo: liberar mais tempo PIOROU `entregue > capacidade` (47 → 84). Hipótese
verificada e rejeitada: não é a escassez parando de disparar menos vezes (265/288 vs. 262/288,
diferença pequena — não explica 37 perfis). A causa exata não foi isolada; fica registrada como
inferência não confirmada, não como fato. **Decisão do Rafael: a rampa não muda** — os 9 minutos
desta estimativa ficam só como medição aqui, nunca como regra.

## Decisão

**(d) com corte proporcional por `PESO_PRIORIDADE`.** Implementado como fase nova em `alocar()`
— entre o sacrifício da `ORDEM_SACRIFICIO` e a redistribuição de excedente —, no oráculo
(`tools/motor_sim/motor_v3.py`) e no Kotlin (`PriorityAllocation.alocar()`,
`Allocation.kt`), ambos espelhando o mesmo algoritmo de "água enchendo": reduz todos os
restantes proporcionalmente ao inverso do peso; se o corte calculado para algum excede o que ele
ainda tem, remove-o por completo (`sacrificio_proporcional`) e recalcula o déficit entre os que
sobram, até convergir (`corte_proporcional`) ou esvaziar tudo.

Isso **amenda a Fatia 3**: o `[INV]` registrado em `docs/regras/01-motor-de-geracao.md` ("CHEST,
BACK, QUADS e POSTERIOR nunca são rebaixados nem sacrificados") deixa de valer como estava —
agora esses 4 também podem ser reduzidos (e, no limite, removidos) quando o resto não bastar.
Em compensação, `soma(resultado) ≤ capacidade` passa a ser garantido sempre em `alocar()`, o que
antes não era o caso.

`[REGRA]` adicionada em `docs/regras/01-motor-de-geracao.md` (seção "etapa [3] amendada pela
etapa [8]") e anotação datada em `docs/propostas/motor-v3-ESPECIFICACAO.md` (linha 315, "Como
alocamos").

### Testes

Oráculo (`test_motor_v3.py`): suíte inteira roda sem regressão — ainda exatamente os 2 vermelhos
pré-existentes do diferencial GLUTES (24 testes, 2 falhas, 0 erros), antes e depois da edição
permanente de `alocar()`.

Kotlin (`AllocationTest.kt`, Fatia 3): o teste `escassez extrema nao garante caber na
capacidade` (capacidade 29,33, que antes esperava soma 30 > capacidade) foi reescrito para
`escassez extrema agora cabe na capacidade, com corte proporcional` — soma 29, disparo
`corte_proporcional` novo. Teste novo `corte proporcional pode remover um dos 4 intocaveis, se o
deficit exigir` (capacidade 5,0) prova o caminho `sacrificio_proporcional` (QUADS e POSTERIOR
removidos por completo antes de BACK/CHEST caberem). Ambos os resultados vieram de rodar
`motor_v3.py::alocar()` com os mesmos `direta`/`indireto`/`capacidade` do fixture — não são
números inventados. O teste de bateria `nenhuma regra de escassez fica sem disparo` passou a
exigir `corte_proporcional` e `sacrificio_proporcional` também. **Pendente de confirmação do
Rafael**: rodar `:server:test` (este agente não executa `./gradlew`).

**Achado à parte, não desta fatia, resolvido em 2026-10-24**: existia um segundo arquivo,
`AllocationModel.kt` (`object AllocationModel`), com o MESMO algoritmo de `PriorityAllocation`
(Fatia 3). Confirmado por busca em `app/`, `server/`, `shared/` e `shared-contract/` (excluindo
`build/`): nenhum uso fora do próprio arquivo e do próprio teste `AllocationModelTest.kt` —
código morto/duplicado de uma iteração anterior da Fatia 3, não usado por `MotorV3Pipeline.kt`
nem por nenhuma outra peça de produção. Apagado por decisão do Rafael, junto com
`AllocationModelTest.kt`. Como todo `engine.v3` ainda estava fora do git nesta sessão (ver
`git status` ao fechar), não há commit separado para esta remoção — ela simplesmente não entra
no commit das Fatias 1–8.

## Medição completa nos 720 perfis (não só os 288 de 2–3 dias)

Pedido do Rafael: confirmar que 4–6 dias não mudam com esta decisão.

| dias | n | `alocado>cap` | `entregue>cap` | perfis com `corte_proporcional` |
|---|---|---|---|---|
| 2 | 144 | 0/144 | 10/144 | 120/144 |
| 3 | 144 | 3/144 | 37/144 | 52/144 |
| 4 | 144 | 3/144 | 26/144 | 0/144 |
| 5 | 144 | 0/144 | 37/144 | 0/144 |
| 6 | 144 | 0/144 | 20/144 | 0/144 |
| **total** | **720** | **6/720** | **130/720** | |

`corte_proporcional` dispara SÓ em 2–3 dias — confirmando que 4–6 dias não mudam com esta
decisão: os números de `alocado>cap` por dias (3/144 em 4 dias, 0/144 em 5 e 6 dias) são
idênticos aos já registrados antes desta fatia. Dos 288 perfis de 2–3 dias: `alocado>cap`
3/288, `entregue>cap` 47/288 — igual à medição da seção acima.

## O que fica pendente, dentro da própria Fatia 8

1. **Os 6/720 casos residuais de ruído de arredondamento** (3 em 3 dias + 3 em 4 dias, ~0,2
   série de excesso cada) não são corrigidos aqui — é o laço de corte por tempo (ainda não
   construído) que precisa cobrir esse resíduo. Critério de pronto da fatia continua sendo
   0/720 `entregue > capacidade` depois do corte.
2. **A causa dos 47/288 (ou 130/720) perfis com `entregue > capacidade`** (fallback do catálogo
   na etapa [6] ou outra causa) **ainda não foi isolada**. Isolar com medição é trabalho desta
   mesma fatia, antes de desenhar o laço de corte por tempo propriamente — não foi feito ainda.
3. O laço de corte por tempo em si (estimar duração, reduzir o teto em 1 série, refazer a
   alocação, repetir até caber) não foi desenhado nem implementado nesta sessão.

## Débito de produto registrado

`docs/fontes/debitos.md`, seção "Motor v3 — fatia 8": aviso no onboarding para quem escolhe 2
dias e até 60 minutos ("volume reduzido") — mesmo com o corte proporcional, essa combinação
continua entregando menos do que os pisos pedem, e é físico, não bug. Decisão de produto/UX, não
do motor.

## Fonte única: `ORDEM_SACRIFICIO` derivada de `PESO_PRIORIDADE` (2026-10-24)

Decisão de princípio do Rafael: uma fonte só. `ORDEM_SACRIFICIO` deixa de ser lista mantida à
mão e passa a ser derivada de `PESO_PRIORIDADE` (ordem crescente de peso = ordem de sacrifício;
desempate por `ALVOS.index`/`ordinal` ascendente, mesmo critério já usado no resto do código).
`[INV]` nova: as duas nunca discordam, por construção.

**Fronteira do conjunto protegido: A (CHEST/BACK/QUADS/POSTERIOR, como já era) contra B
(+SHOULDERS, peso ≥ 0,90).** Medido nos 720 perfis, com o piso do isolamento condicional já
aplicado (opção iii):

| fronteira | `entregue>cap` | zerados totais | por músculo |
|---|---|---|---|
| A (4) | 0/720 | 156 | BICEPS 74, TRICEPS 56, GLUTES 20, FOREARMS 6 |
| B (+SHOULDERS) | 0/720 | 141 | BICEPS 60, TRICEPS 43, GLUTES 20, FOREARMS 6, **POSTERIOR 12 (novo)** |

B reduz o total (156→141) mas introduz 12 casos de POSTERIOR zerado — contra a decisão (d) desta
mesma fatia, que queria CHEST/BACK/QUADS/POSTERIOR sempre protegidos. **B descartada.** Fica A.

**Efeito medido da correção: NULO.** Comparando evento a evento (não só a contagem agregada), os
156 eventos de zeramento com a `ORDEM_SACRIFICIO` antiga (lista fixa) e com a nova (derivada,
fronteira A) são EXATAMENTE os mesmos — mesmo perfil, mesmo músculo, nos 156/156. Conferido
também na alocação completa (não só no zeramento) de dois perfis concretos, série a série:
idêntica.

**Por quê:** nesses 156 perfis, `alocar()` nunca chega a executar o laço de rebaixamento/
sacrifício — nem com a ordem antiga, nem com a nova. A soma dos pisos de TODOS os músculos, já
na base (`min(direta, VOLUME[m][0] − indireto[m])`), fica abaixo da capacidade antes de qualquer
sacrifício entrar em jogo. A ordem do laço — certa ou errada — é irrelevante quando o laço não
roda.

A `[INV]` fonte única continua valendo — é consistência interna, não depende de ter corrigido um
sintoma. A causa dos 156 zeramentos está em outro lugar: o piso-base de BICEPS/TRICEPS/GLUTES
desaba perto de zero porque o crédito indireto que recebem (de BACK; CHEST+SHOULDERS; QUADS,
respectivamente) já é grande o bastante pra zerar `VOLUME[m][0] − indireto[m]`; CALVES/FOREARMS
não recebem (FOREARMS recebe de BACK e POSTERIOR, então também pode zerar) ou recebem pouco
crédito indireto, então o piso-base deles fica mais alto. Depois, a redistribuição de sobra
(`alocar()`, proporcional a `PESO_PRIORIDADE` **puro** — `add = min(sobra × peso[m] / Σpeso dos
famintos, direta[m] − alocado[m])`, sem ponderar pela demanda restante) dá pouco aos de peso
baixo (BICEPS 0,5, TRICEPS 0,55, GLUTES 0,7) e isso não é suficiente pra tirá-los do zero.

## Medição que decide a Fatia 8: total entregue (direto + indireto) contra o mínimo efetivo

Métrica certa, não "alvo − indireto" (que mede demanda, não cobertura): para o músculo zerado em
cada um dos 156 eventos, `VOLUME[m][0]` (mínimo efetivo), indireto recebido, total entregue
(direto 0 + indireto) e a razão total ÷ mínimo.

- Distribuição da razão (156 eventos): mínimo 0,50, máximo 2,75, **mediana 1,00**, média 1,14.
- **58/156 (37%) ficam ABAIXO do mínimo efetivo** (razão < 1,0) — não é desprezível.
- Por músculo: GLUTES (n=20, razão 1,62–2,75, mediana 1,75) e FOREARMS (n=6, sempre 2,50) ficam
  SEMPRE acima do mínimo — cobertos de verdade pela via indireta, a (iii) está certa pra eles.
  **BICEPS (n=74, razão 0,50–1,25, mediana 0,83) e TRICEPS (n=56, razão 0,71–1,36, mediana
  1,00) são o grupo que fica abaixo do mínimo** — na pior ponta, BICEPS entrega metade do
  mínimo efetivo (razão 0,50: indireto 3,0 de um mínimo de 6).

**Conclusão, pela regra que o Rafael definiu**: há um grupo relevante abaixo do mínimo
(BICEPS/TRICEPS, 130/156 dos eventos). O problema não é só "o músculo já é coberto pela via
indireta" — a (iii) não está certa como está pra estes dois. A causa apontada é a fórmula de
sobra em `alocar()`: proporcional ao `PESO_PRIORIDADE` puro, sem levar em conta a demanda
restante (`direta[m] − alocado[m]`) de cada músculo além de usá-la só como teto (`minOf`).
Decisão pendente do Rafael sobre essa fórmula antes de fechar a Fatia 8.

## Três medições que mudaram o tamanho do problema

### A auditoria completa: o problema era 36× maior do que eu media (2026-10-26)

Até aqui eu auditava apenas os músculos **zerados pelo mecanismo novo** (a opção (iii)) — 58
eventos abaixo do mínimo. Ao conferir se o plano cobria as recomendações do relatório, medi o que
nunca tinha medido: **todos os músculos de todos os 720 perfis**, com o motor como estava.

**2.079 pares (perfil, músculo)** entregavam total (direto + indireto) abaixo de `VOLUME[m][0]`.
Não 58. E atingia os protegidos: `SHOULDERS` em 279/720, pior razão **0,00**, com **204 perfis sem
nenhuma série direta** de ombro.

> **O escopo da medição definia o tamanho do problema.** Medir só o que o mecanismo novo tocava
> fazia o defeito parecer ser do mecanismo — e sugeria consertar o mecanismo. O defeito era do
> motor, e maior.

### Item 8: o débito que eu mesmo escrevi estava mal diagnosticado

**26/10, por mim:** o pool de CASA/BEGINNER tem 1 composto de costas e nenhum isolamento, logo
**faltam exercícios no catálogo** — débito de curadoria, 63 pares.

**27/10, medido:** os exercícios **existem**. O que os tira do pool é o **acessório** (ancoragem,
banco, superfície elevada), e a matriz dos 720 simula CASA com **zero acessório declarado**.

| cenário (360 perfis de CASA) | vagas vazias |
|---|---|
| sem acessório (a matriz) | **239** |
| com **um banco** | **0** |

E o conserto de produto já existia: a **V66 (22/10)** deu ao perfil onde guardar acessórios, e a
fatia de onboarding fechou no mesmo dia.

> **Débito escrito a partir da lista, sem medir a causa, aponta para a área errada.** Este mandava
> curar catálogo; a causa era fixture de teste, e a solução estava em produção havia cinco dias.

### Os dois achados do P0 (2026-10-27)

**I1 — o catálogo injeta indireto que a prescrição não planejou.** `papel_trocado` dispara em
**312/720** perfis, e no sentido **inverso** do que eu supunha: não é perder crédito, é **ganhar**
crédito não previsto — `BICEPS` **+398 séries**, `FOREARMS` **+526** no somatório dos 720. A
cadeia inteira passa por fora de qualquer decisão: *o catálogo não tem isolamento de costas → o
motor põe um composto na vaga → o composto credita bíceps → o bíceps perde demanda direta → o
bíceps é zerado.* Uma **lacuna de curadoria vira decisão de prescrição** sem passar por ninguém.
Creditar pelo exercício real segue certo (Fatia 5); o que falta é o motor medir **o que a troca
custou** — hoje ele conta que trocou, nunca o efeito. Débito aberto.

**E9 — o alvo podia nascer abaixo do próprio piso.** `FATOR_OBJETIVO_ALVO` multiplicava o alvo
**sem respeitar o piso**: em `MAINTAIN`/`BEGINNER` (fator 0,6 sobre a coluna do iniciante, que
**é** o piso) os 10 músculos nasciam abaixo dele — peito 4,8 contra piso 8 —, **660 pares**.
Não era bug de implementação: o código fazia o que a especificação mandava. Era **lacuna da
especificação**, que declarava a coluna do iniciante como piso, declarava um multiplicador de
objetivo, e nunca disse qual dos dois vence. Resolvido comparando contra o **MV ao lado do MEV**,
em vez de arbitrar na etapa [1].

> **A auditoria não arbitra.** Ela expõe os dois fatos (o status mede contra o alvo,
> `atingiu_minimo` mede contra o piso) e deixa a decisão de prescrição fora do código. Arbitrar
> ali seria enterrar uma decisão de produto dentro de uma peça de motor.

## As duas lições da fatia (2026-10-27)

### 1. O estouro de capacidade nunca foi excesso de volume

Era **aumento de volume acontecendo fora do orçamento**. É a mesma frase para os três lugares onde
o defeito morava, e é por isso que os números caíram em degraus em vez de de uma vez:

| | `entregue > capacidade` | o que foi corrigido |
|---|---|---|
| Antes | **167/720** | o piso condicional subia o músculo depois do orçamento fechado |
| Depois da etapa [3] | **27/720** | `piso_prescritivel()` passou a financiar o piso **dentro** do orçamento |
| Depois da generalização | **0/720** | panturrilha e antebraço ainda subiam por fora na etapa [4] |

Os **167 → 27 → 0 são a mesma correção aplicada em três lugares**, não três correções
diferentes. O `sobe_ao_piso_do_isolamento` da etapa [4], que era o mecanismo original, hoje é um
`[INV]` que falha alto e dispara **0 vez** nos 720 — a lógica migrou inteira para a etapa [3], que
é onde o orçamento existe. Quando um número cai em degraus assim, vale perguntar se os degraus são
problemas distintos ou o mesmo problema visto de três ângulos; aqui eram o mesmo, e tratá-los como
distintos teria produzido três remendos em vez de uma mudança de lugar.

### 2. Piso honrado em menos da metade dos casos não é garantia, é aspiração

A regra de decisão entre o piso de 2 e o de 4 foi **fixada antes da medição**, de propósito: 4 é o
número da literatura (MV da RP, Iversen 2021) e seria adotado se não aumentasse
`entregue > capacidade` nem levasse músculo protegido abaixo do MEV. A medição deu
**324/480 contra 228/480** de pagabilidade nos perfis INTER/ADV.

**Piso honrado em menos da metade dos casos elegíveis não é garantia, é aspiração** — e declará-lo
seria **repor exatamente a desonestidade que esta rodada removeu**. A rodada inteira foi sobre
isso: tirar da tabela coeficientes sem fonte que fabricavam cobertura, trocar o zeramento cego
pelo rótulo `MANUTENCAO`, marcar `pisoDiretoNegado` em vez de criar um status que escondesse a
pergunta. Adotar um piso de 4 que a capacidade não paga em 52% dos casos seria escrever no
contrato uma promessa que o motor não cumpre — a mesma classe de defeito, num campo novo.

O corolário prático, que vale para a próxima fatia: **número de regra se escolhe pela
pagabilidade medida, não pelo prestígio da fonte.** A literatura diz qual é o alvo desejável; o
orçamento diz qual é declarável.
