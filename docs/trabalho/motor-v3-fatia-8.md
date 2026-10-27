# Motor v3 — Fatia 8 — corte por tempo — plano

_Aberta em 2026-10-24. Rascunho: não é fonte da verdade (ver README desta pasta)._

## O que é e por que agora

Etapa [8]: o laço de corte por tempo (estima a duração do programa gerado; se estourar, baixa o
teto em 1 série e refaz a alocação; o teto só desce, então termina). Vinha bloqueada desde a
Fatia 5 por uma decisão de prescrição pendente: os pisos de todos os músculos somam 61
séries/semana no iniciante e passam da capacidade em 100% dos perfis de 2–3 dias (288/288),
mesmo antes de qualquer corte por tempo entrar em jogo — a decisão sobre o que cede nesse caso
precisava vir primeiro, porque o laço de corte não resolve um déficit estrutural de piso.

## Andamento — 2026-10-24

A decisão de prescrição foi tomada e implementada nos dois lados: **(d) com corte proporcional
por `PESO_PRIORIDADE`** — quando sacrificar tudo de `ORDEM_SACRIFICIO` não basta, os 4 músculos
de fora dela (CHEST, BACK, QUADS, POSTERIOR) são reduzidos proporcionalmente ao inverso do peso,
em vez de ficarem intocados. Implementado como amendamento à Fatia 3, em `alocar()`, no oráculo
(`tools/motor_sim/motor_v3.py`) e no Kotlin (`PriorityAllocation.alocar()`, `Allocation.kt`).
`AllocationTest.kt` ganhou 2 testes novos; `:server:test` 658/658 verde (confirmado pelos XMLs,
não só pelo console). Narrativa completa das quatro opções medidas e da sensibilidade da rampa
em `docs/historico/motor-v3-fatia-8.md`.

Medido nos 720 perfis (não só nos 288 de 2–3 dias): `alocado > capacidade` caiu de 176/720 para
**6/720** (3 em 3 dias + 3 em 4 dias — ruído de arredondamento de ~0,2 série, não resolvido por
esta decisão). `entregue > capacidade` caiu de 297/720 para **130/720**. Confirmado que 4–6 dias
não mudam: `corte_proporcional` só dispara em 2–3 dias (120/144 e 52/144); os números de
`alocado > capacidade` por dias em 4, 5 e 6 dias são idênticos aos de antes da decisão.

Débito de produto registrado (`docs/fontes/debitos.md`): aviso no onboarding para 2 dias/≤60 min
("volume reduzido").

### Próximos passos (fatia continua aberta)

- [x] **Isolar a causa dos 130/720 `entregue > capacidade` restantes** — medido em 2026-10-24,
  NÃO é fallback do catálogo. Ver "Causa isolada" abaixo.
- [ ] **Mostrar o desenho do laço de corte por tempo antes de codar** (C1) — como a duração é
  estimada, onde exatamente ele entra no pipeline, e como preserva a ordem de prioridade entre a
  primeira e a última iteração (condição da especificação: "nunca uma poda no fim").

## Causa isolada: não é o catálogo (etapa [6]) — é o piso do isolamento (etapa [4])

Hipótese do passo 1 era fallback do catálogo na etapa [6] (`vagas_vazias`). **Descartada, por
medição** — instrumentado os 720 perfis (script descartável, não ficou no repo, roda em cima de
`motor_v3.py::gerar()`, nada mudou na lógica):

Nos 130/720 com `entregue > capacidade`, **100% (130/130) disparam `sobe_ao_piso_do_isolamento`**
em `montar_semana()` (etapa [4]), e os únicos músculos que entregam MAIS do que `alocar()` alocou
são BICEPS (58 casos), TRICEPS (74), GLUTES (38) e FOREARMS (6) — os músculos pequenos que sobram
com pouca sobra depois do corte proporcional.

Mecanismo: `alocar()` (etapa [3]) garante `soma(alocado) ≤ capacidade` — verdade nos 720,
inclusive nos 130 (só 6/130 têm `alocado > capacidade`, e são os mesmos 6/720 de ruído de
arredondamento já conhecidos, não um caso novo). O estouro nasce depois, em `montar_semana()`:

```python
if total < PISO_SERIES["ISOLAMENTO"]:   # 2
    total = PISO_SERIES["ISOLAMENTO"]
    disparos["sobe_ao_piso_do_isolamento"] += 1
```

Isto sobe o total SEMANAL de um músculo pra 2 séries sempre que `alocar()` deu menos — por fora
do orçamento, depois que `alocado` já esgotou a capacidade. `entregue` herda o estouro.

Confirmação numérica: comparando o excesso real (`entregue_total − capacidade`) com o excesso
teórico só do piso (`Σ max(0, 2 − alocado[m])` nos músculos que o disparam), os dois batem
exatamente em 73/130 (56%). Nos outros 57, o excesso real é MENOR que o teórico — a vaga do
próprio músculo bumped ficou vazia em parte (`vagas_vazias`, etapa [6] de novo, mas como
atenuante, nunca causa: o catálogo reduz o estouro, não aumenta). Em nenhum dos 130 o excesso
aparece sem o disparo do piso. 244/720 perfis disparam `sobe_ao_piso_do_isolamento` no total, mas
só 130 estouram — os outros 114 tinham sobra suficiente para absorver o piso sem passar do teto.

Os outros disparos que também aparecem em muitos dos 130 (`escassez` 118/130,
`modo_conservador_ativo` 86/130, `musculo_sem_relevancia_1` 82/130, `papel_trocado` 44/130...)
são concorrentes, não causais: o mesmo perfil de pouca capacidade aciona várias regras juntas,
mas nenhum correlaciona 100% com o estouro como `sobe_ao_piso_do_isolamento`.

**Implicação pro laço de corte por tempo (próximo passo):** o laço não pode só reagir a
`entregue > capacidade` baixando o teto de `alocar()` cegamente — se a causa é um piso que
`alocar()` não vê, cortar o teto de `alocar()` deixa `montar_semana()` livre pra subir o mesmo
músculo de volta a 2 e estourar de novo (risco de não convergir, ou só convergir quando o
músculo sai de `alocado` por completo). O desenho do laço precisa decidir entre: (i) o piso do
isolamento entra na conta de `capacidade` de `alocar()` como mínimo reservado, ou (ii) o laço
mede `entregue` (pós-piso), não `alocado`, pra saber quando parar.

## Opção (iii) medida: piso do isolamento condicional à capacidade restante

Pedido do Rafael antes do desenho do laço: em vez de sempre subir um músculo pra 2
séries/semana (`sobe_ao_piso_do_isolamento`), só subir SE couber na capacidade que resta; senão
zerar o músculo inteiro na semana (indireto continua contando — vem de `medir_entregue()` a
partir dos OUTROS músculos/compostos, não do próprio total zerado).

**Achado de implementação, antes dos números:** uma passada única em ordem de prioridade (sobe
quem cabe no que já foi "comprometido" até aquele ponto) não garante o invariante — um músculo
grande processado DEPOIS de um bump, sem checagem (porque sozinho ele "sempre cabe"), pode
empurrar o total pra cima da capacidade de qualquer jeito. A versão correta, usada na medição,
é em duas passadas: (1) soma fixa = total dos músculos que já têm `alocado ≥ piso` (sempre
cabem, não entram em disputa); (2) o que resta da capacidade é o orçamento pros candidatos a
bump (`alocado < piso`), distribuído em ordem de prioridade — cada um recebe o piso inteiro (2)
se o orçamento alcançar, senão é zerado por completo (não fica com o `alocado` antigo). Isto
importa pro desenho real, não só pro script de medição: a implementação em Kotlin/oráculo
precisaria do mesmo cálculo em duas passadas, não de uma passada ingênua.

Medido nos 720 perfis com essa versão (script descartável, não ficou no repo, roda em cima de
`alocar()`/`compor_sessao()`/`preencher()` sem alterar nenhum, só substitui `montar_semana()`):

1. **`entregue > capacidade`: 0/720** (era 130/720). Inclusive os 6/720 de ruído de
   arredondamento (`alocado > capacidade`, ~0,2 série) somem: nesses casos a soma fixa dos
   músculos grandes já esgota (e no caso excede por ~0,2) a capacidade, então o candidato a
   bump (sempre BICEPS nesses 6) é zerado — `entregue` cai de 29 pra 28 numa capacidade de
   28,8 (fica 0,8 série abaixo do teto, não exatamente nele, mas dentro).

2. **Músculos/perfis zerados**: 152/720 perfis zeram pelo menos um músculo —
   BICEPS (74), TRICEPS (56), GLUTES (20), FOREARMS (6). Nenhum outro músculo é candidato a
   bump na matriz de hoje (são os 4 que às vezes recebem alocação fracionária pequena depois
   do corte proporcional).

3. **Indireto que esses músculos ainda recebem**, nos perfis onde foram zerados (série/semana,
   min–max–média): BICEPS 3,00–7,50 (méd. 5,41); TRICEPS 5,00–9,50 (méd. 7,09); GLUTES
   6,50–11,00 (méd. 7,85); FOREARMS 7,50–7,50 (méd. 7,50). Nenhum caso com indireto zero — o
   músculo zerado direto continua recebendo crédito indireto dos compostos de outros músculos,
   como esperado (o cálculo não depende do total que `montar_semana` decidiu pra ele).

4. **4–6 dias mudam.** Diferente do corte proporcional (que só disparava em 2–3 dias), o
   zeramento condicional dispara em TODOS os tamanhos de divisão — perfis com pelo menos um
   músculo zerado, por dias: 2→12, 3→37, 4→35, 5→44, 6→24. Isto bate com o perfil dos 130
   antigos (que também apareciam em 4–6 dias: 26/37/20) — é o mesmo grupo de perfis, só que
   agora resolvido por zeramento em vez de estourar.

**Conclusão da medição:** a (iii) leva `entregue > capacidade` a 0/720 — nenhum resíduo de
arredondamento sobrevive, inclusive o que a `[REGRA]` do corte proporcional (Fatia 8, decisão
anterior) deixava passar. O preço é 152/720 perfis (21%) perderem BICEPS, TRICEPS, GLUTES ou
FOREARMS inteiros da programação direta daquela semana (ficam só com o indireto, sem exercício
dedicado) — não é grátis, é uma segunda política de sacrifício, mais severa que o corte
proporcional (que reduz, não zera) e que incide sobre músculos que a `ORDEM_SACRIFICIO`/corte
proporcional já tratam como baixa prioridade. **Decisão pendente do Rafael**: aceitar (iii) como
desenho final da Fatia 8 (regra condicional + invariante `entregue ≤ capacidade` + tratamento do
resíduo de arredondamento, sem laço de corte por tempo nenhum) ou tratar os 152/720 zerados como
problema a resolver com (i)/(ii) antes de fechar.

## Checagem antes de fechar (iii): volume real nos 5-6 dias e coerência com ORDEM_SACRIFICIO

Pedido do Rafael: antes de fechar (iii), medir se os 95 casos (diff ≥ 2) se concentram em
programas apertados, e se o zeramento é coerente com `ORDEM_SACRIFICIO` (etapa [3]).

### 1) Os 95 casos, por dias × duração, com a diferença mediana na célula

| dias | 45 min | 60 min | 75 min |
|---|---|---|---|
| 2 | n=0 | n=0 | n=8, mediana 8,50 (5,50–11,00... min 7,00–max 11,00) |
| 3 | n=0 | n=0 | n=8, mediana 6,75 (5,50–7,00) |
| 4 | n=1, mediana 2,20 | n=17, mediana 5,00 (2,20–8,50) | n=9, mediana 5,00 (2,20–7,00) |
| 5 | n=24, mediana 6,00 (2,20–9,00) | n=10, mediana 5,00 (2,20–9,00) | n=10, mediana 5,00 (2,20–9,00) |
| 6 | n=8, mediana 8,25 (7,50–9,00) | n=0 | n=0 |

**5–6 dias com 60–75 min: 20 casos, mediana 5,00.** Não é marginal — são programas com bastante
dias e tempo (os que historicamente tinham MENOS escassez: `corte_proporcional`, Fatia 8 anterior,
só disparava em 2–3 dias). Isto é **volume relevante em programa folgado**, o primeiro gatilho que
o Rafael definiu pra não fechar (iii) como está.

### 2) Coerência com `ORDEM_SACRIFICIO`

`ORDEM_SACRIFICIO = [FOREARMS, CALVES, CORE, GLUTES, BICEPS, TRICEPS, SHOULDERS]` — ordem de quem
perde volume primeiro em `alocar()` (etapa [3]); mais tarde na lista = mais protegido.

- **156/156 eventos**: algum músculo MAIS TARDE (mais protegido) tem `alocado > 0` enquanto o
  zerado foi a 0 — esperado, é o padrão da hierarquia (quem está mais protegido sobra volume).
- **126/156 eventos (81%)**: algum músculo MAIS CEDO na ordem (**menos** protegido, deveria
  perder volume primeiro) ainda tem `alocado > 0` enquanto o zerado — que é MAIS TARDE, deveria
  estar mais protegido — foi a 0. **Em TODOS os 126, o músculo "mais cedo" que segura volume é
  sempre `CALVES`** (posição 1, logo depois de `FOREARMS`), e os zerados são `GLUTES` (posição
  3), `BICEPS` (posição 4) ou `TRICEPS` (posição 5) — todos mais tarde que `CALVES` na ordem.

Dois exemplos (zerado=BICEPS, idx 4; CALVES, idx 1, com volume):
```
dias=3 min=60 CASA BEGINNER GAIN_MUSCLE 30a: alocado = BACK 10, CHEST 8, QUADS 6, SHOULDERS 6,
  POSTERIOR 6, TRICEPS 3, BICEPS 1 (zerado em [4]), CALVES 4
dias=3 min=75 ACADEMIA INTERMEDIATE GAIN_MUSCLE 30a: alocado = BACK 13, CHEST 10, QUADS 8,
  SHOULDERS 8, POSTERIOR 8, TRICEPS 1, BICEPS 1 (zerado em [4]), CALVES 5
```

Os 4 eventos de GLUTES com diferença ≥ 2 (da medição anterior) entram nos 126 também — em
todos, `CALVES` segura 5–10 séries enquanto `GLUTES` (mais protegido na ordem) fica com 1 série
em `alocado`, que o piso condicional zera.

**Causa, não só correlação:** `CALVES` tem `PESO_PRIORIDADE = 0,4` — menor que `BICEPS` (0,5),
`CORE` (0,5), `TRICEPS` (0,55) e `GLUTES` (0,7) — mas é sacrificado ANTES deles em
`ORDEM_SACRIFICIO`. As duas ordens (`ORDEM_SACRIFICIO`, discreta, usada no rebaixamento/
sacrifício de `alocar()`; `PESO_PRIORIDADE`, contínua, usada na redistribuição de sobra da MESMA
função) já discordavam entre si desde a Fatia 3/8 — isto não é novo. O que a medição de hoje
expõe é que essa discordância produz um resultado visível e sistemático: `CALVES` termina com
MAIS volume alocado que `GLUTES`/`BICEPS`/`TRICEPS` nestes perfis, mesmo sendo o músculo que a
`ORDEM_SACRIFICIO` manda sacrificar primeiro — e são justamente `GLUTES`/`BICEPS`/`TRICEPS`,
sub-alocados por essa discordância, que caem abaixo do piso do isolamento e acionam o zeramento
condicional da (iii).

### Conclusão, pelos dois critérios que o Rafael definiu

Os dois gatilhos de "não fechar (iii) como está" dispararam: **(a)** há volume relevante (mediana
5–8 séries) em programas de 5–6 dias com 60–75 min, que não são apertados; **(b)** há incoerência
sistemática com `ORDEM_SACRIFICIO` em 81% dos eventos, sempre pela mesma causa — `CALVES`
protegido por `PESO_PRIORIDADE` acima de músculos que a ordem de sacrifício diz que deveriam ser
mais protegidos. **Pela regra que o Rafael definiu antes de medir, o problema é de `alocar()`
(Fatia 3), não de `montar_semana()` (Fatia 8)** — a (iii) resolveria o SINTOMA (`entregue >
capacidade`) escondendo uma alocação de etapa [3] que já estava errada antes de chegar ao piso do
isolamento.

## Fonte única implementada: `ORDEM_SACRIFICIO` derivada de `PESO_PRIORIDADE`

Feito nos dois lados (`spec_v3.py`/`motor_v3.py` no oráculo, `PriorityAllocation.kt` no
servidor), fronteira A (CHEST/BACK/QUADS/POSTERIOR protegidos, como já era — fronteira B
descartada: zerava POSTERIOR em 12/720, contra a decisão (d)). `[INV]` nova: as duas nunca
discordam, por construção (desempate por `ALVOS.index`/`ordinal`).

**Efeito medido nos 720: NULO** para os 156 eventos de zeramento — são exatamente os mesmos
antes e depois, porque `alocar()` nunca alcança o laço de rebaixamento/sacrifício nesses perfis
(a soma dos pisos de base já cabe na capacidade). A `[INV]` vale por consistência interna, não
por ter corrigido o sintoma. Detalhe completo em `docs/historico/motor-v3-fatia-8.md`.

Testes atualizados: `AllocationTest.kt` (`ordem de sacrificio e derivada do peso, nao mais lista
fixa` + `invariante -- ORDEM_SACRIFICIO nunca discorda de PESO_PRIORIDADE`, novo), e
`test_motor_v3.py` (`OrdemDeSacrificioEhFonteUnica`, novo). **`:server:test` verde** (ver abaixo).

## Medição que decide a Fatia 8: 58/156 eventos ficam abaixo do mínimo efetivo

Métrica certa (não alvo−indireto): total entregue (direto+indireto) ÷ `VOLUME[m][0]` nos 156
eventos de zeramento. Mediana 1,00, mas **58/156 (37%) abaixo de 1,0** — concentrados em BICEPS
(mediana 0,83, pior caso 0,50) e TRICEPS (mediana 1,00, pior caso 0,71). GLUTES e FOREARMS
sempre ≥ 1,0 (bem cobertos pela via indireta — a (iii) está certa pra eles). Pela regra que o
Rafael definiu: há grupo relevante abaixo do mínimo, então o problema aponta pra fórmula de
sobra em `alocar()` (proporcional ao `PESO_PRIORIDADE` **puro**, sem ponderar pela demanda
restante). **Decisão pendente do Rafael** antes de fechar a Fatia 8 — ver
`docs/historico/motor-v3-fatia-8.md`.

## Auditoria completa dos 720 (2026-10-26) — o problema era 36× maior do que eu media

Até aqui eu só auditava os **músculos zerados pelo mecanismo da opção (iii)** — 58 eventos abaixo
do mínimo. Ao conferir se o plano cobria todas as recomendações do relatório, medi o que nunca
tinha medido: **todos os músculos de todos os 720 perfis**, com o motor como está hoje (sem a
(iii)).

**2.079 pares (perfil, músculo) entregam total (direto + indireto) abaixo de `VOLUME[m][0]`.**
Não 58. E atinge os protegidos também:

| músculo | abaixo do piso | % | pior razão | perfis com 0 série direta |
|---|---|---|---|---|
| CHEST | 191/720 | 26,5% | 0,38 | — |
| BACK | 204/720 | 28,3% | 0,60 | — |
| SHOULDERS | 279/720 | 38,8% | **0,00** | 204 (28%) |
| BICEPS | 322/720 | 44,7% | 0,50 | 269 (37%) |
| TRICEPS | 278/720 | 38,6% | 0,21 | 450 (62%) |
| FOREARMS | **0**/720 | 0,0% | — | **714 (99%)** |
| QUADS | 168/720 | 23,3% | 0,33 | — |
| POSTERIOR | 226/720 | 31,4% | 0,33 | — |
| CALVES | 327/720 | 45,4% | **0,00** | 234 (32%) |
| GLUTES | 84/720 | 11,7% | 0,00 | 628 (87%) |

FOREARMS é o retrato da inversão já registrada na proposta: **nunca** fica abaixo do piso e
**quase nunca** recebe trabalho direto (99%). O indireto sozinho o mantém sempre "coberto".

### As três camadas, separadas por medição

| camada | perfis | pares | o que é |
|---|---|---|---|
| sem problema | 302/720 (42%) | — | nenhum músculo abaixo do piso |
| **física** | 318/720 (44%) | **1.901** | o piso direto necessário NÃO cabe na capacidade. Não é bug: é o que a Fatia 8 já tinha medido (os pisos somam 61 séries/semana no iniciante). Precisa de **estado honesto**, não de algoritmo melhor |
| **catálogo** | — | **63** | vaga vazia: `alocar()` pediu certo e o `preencher()` não tinha exercício. **100% em CASA.** BACK 48, CHEST 15 |
| **alocação** | 100/720 (14%) | **115** | o piso cabia, não houve vaga vazia, e mesmo assim ficou abaixo. BICEPS 41, TRICEPS 20, e 9 em cada um dos outros 6. ACADEMIA 73, CASA 42 |

**Caso do buraco de catálogo, diagnosticado:** CASA/BEGINNER/4 dias, capacidade 64,
`alocado[BACK] = 10` (correto), `entregue[BACK] = 6`. Duas vagas `('Upper A'/'Upper B', BACK,
ISOLAMENTO)` ficaram vazias porque **o pool de CASA no nível iniciante tem 1 composto de costas e
ZERO isolamento de costas**. A alocação acertou; o catálogo não tinha o que colocar. Isto é
curadoria, não motor — **débito novo**, ver `fontes/debitos.md`.

### O que esta auditoria muda no plano

1. **O item que o relatório chama de "imediato" é o de maior alcance, e eu o tinha empurrado para
   a Fatia 9.** Parar de marcar como "coberto" quem está abaixo do mínimo cobre **2.079 pares**;
   o piso direto cobre 115. A honestidade não é o acabamento da correção — é a correção principal.
2. **O piso direto continua necessário**, mas é 5,5% do problema (115 de 2.079), não a solução
   inteira. Dimensionar expectativa antes de medir M-1.
3. **BICEPS/TRICEPS são 61 dos 115** — o problema que vínhamos perseguindo é real e é o maior
   bloco da falha de alocação, mas não é o bloco maior do problema.

## Cobertura das recomendações do relatório — auditoria item a item

| item do relatório | status no plano | onde |
|---|---|---|
| Rec 1a — parar de marcar "coberto" com E < MEV | ✅ **promovido a prioridade 1** (era Fatia 9) | `[INV]` 2 e 3, critério de pronto |
| Rec 1b — auditar se a tabela de MEV é direct-only | ✅ feito, virou decisão aberta | PROPOSTA, D-1 |
| Rec 2 — contabilidade fracionada 0,5 + zeros biarticulares | ✅ já estamos alinhados; só `→ antebraço` sem fonte | PROPOSTA, D-2 |
| Rec 3 — piso direto + water-filling + estado "manutenção" | ✅ piso (D-3) · water-filling condicional (M-3) · estado (D-4) | este doc + PROPOSTA |
| Rec 3b — MILP só se houver restrições combinatórias | ❌ **faltava** — e nós TEMOS (ambiente, acessório, família, dias). Registrado abaixo | este doc |
| Rec 4 — piso direto em bíceps, tríceps, **panturrilha e isquiotibiais** | ⚠️ **faltava isquiotibiais**: meu desenho restringia o piso a `SO_ISOLAMENTO`, que inclui CALVES mas **exclui POSTERIOR**. A auditoria mostra POSTERIOR abaixo do piso em 226/720 | corrigido abaixo |
| Rec 5 — expor o trade-off ao usuário | ✅ Fatia 9 (já prevê texto acionável) | PROPOSTA, D-4 |
| Passo 0 — contabilidade única, nunca misturar | ✅ | PROPOSTA, D-1 |
| Passo 1 — matriz de coeficientes com fonte | ✅ | PROPOSTA, D-2 |
| Passo 1b — **regra da cabeça longa do tríceps** (≥1 extensão de cotovelo/semana) | ❌ **faltava inteiro** — é etapa [6]/curadoria, não alocação | registrado abaixo |
| Passo 2.1 — orçamento = dias × min ÷ tempo por série **com descanso** | ⚠️ **a confirmar**: nosso `MINUTOS_POR_SERIE = 3` e `OVERHEAD_MIN` cobrem isso? | registrado abaixo |
| Passo 2.2 — piso direto (2–3 iniciante, 4 int./avç.) | ✅ M-1 mede 2 vs 4 | este doc |
| Passo 2.3 — water-filling recalculando o efetivo a cada série | ✅ condicional | M-3 |
| Passo 2.4 — **excedente por utilidade côncava** `w·log(1+E)` | ❌ **faltava** — nem para adotar, nem para rejeitar | registrado abaixo |
| Passo 2.5 — estado honesto | ✅ | D-4 |
| Passo 2.6 — teste de regressão como asserção | ✅ | `[INV]` 2 e 3 |

### Os cinco itens que faltavam, agora registrados

1. **Piso direto inclui POSTERIOR** (isquiotibiais), não só `SO_ISOLAMENTO`. Ele tem composto
   próprio (stiff, terra romeno), mas recebe só 0,2 de indireto do glúteo e fica abaixo do piso em
   226/720. M-1 passa a medir o piso com e sem POSTERIOR incluído.
2. **Regra da cabeça longa do tríceps** — `[PROPOSTA]`: exigir ≥1 exercício de extensão de
   cotovelo por semana sempre que TRICEPS receber trabalho direto. Base: Brandão et al. 2020 —
   cabeça longa cresce **+2,1% só com supino contra +17,5% com extensão**. É etapa [6]
   (`preencher()`) e curadoria (`familia`/`relevancia`), **não é da Fatia 8**. Vai para
   `debitos.md` e para a Fatia 9/curadoria.
3. **Excedente por utilidade côncava** — hoje o excedente acima do piso é proporcional ao peso. O
   relatório sugere `w[m]·log(1+efetivo[m])`, que dá à primeira série de um músculo zerado o maior
   ganho marginal. **Avaliação:** se o piso direto (D-3) já garante que ninguém fica em zero, a
   utilidade côncava vira refinamento, não correção. **Decisão: não entra na Fatia 8**; medir
   depois do piso, com os 720, e só adotar se sobrar caso. Registrado para não sumir.
4. **MILP** — o relatório diz que só compensa com muitas restrições combinatórias, e nós temos
   (ambiente, acessório, família por sessão, divisão por dias, teto por exercício). **Rejeitado
   agora, com motivo:** o motor é determinístico e auditável linha a linha contra o oráculo
   Python, e isso é um requisito nosso (Fatia 9 compara os dois lados). Um solver quebra essa
   auditoria. Reavaliar só se o piso + water-filling não fecharem.
5. **Orçamento com descanso** — ✅ **confirmado na especificação**, passo 2.1 do relatório já
   satisfeito. `teto por tempo = (minutos − overhead) ÷ 3`, com o overhead cobrindo rampa de
   aquecimento **e** troca de exercício (16 min no full body de 2–3 dias, que carrega quatro
   compostos de barra; 11 no upper/lower e PPL, que carregam dois). Os 3 min/série são execução
   mais descanso entre séries. Nada a mudar, e o laço de corte por tempo segue desnecessário.

> **Achado ao conferir o item 5:** a própria especificação já dizia, sobre o intermediário em
> 2 dias × 45 min (cobertura 26%): *"é aí que a ordem de sacrifício **e o aviso de honestidade**
> decidem o programa"*. O item que o relatório externo lista como correção imediata não é
> novidade de fora — **é uma dívida nossa, prevista na spec desde o começo e nunca construída.**
> A pesquisa a reencontrou por outro caminho.

## Desenho proposto para fechar a Fatia 8 (2026-10-26, depois da pesquisa de literatura)

A pesquisa encomendada (resumo e fontes em `docs/propostas/volume-efetivo-PROPOSTA.md`) responde
a pergunta que estava travando: **não adianta ajustar a fórmula de sobra.** O veredito dela é que
a alocação proporcional por peso fixo é cega ao déficit — dá série a quem já está acima do mínimo
e arredonda para zero quem está abaixo —, e que a correção é estrutural: **piso direto como
restrição rígida antes de qualquer distribuição proporcional.**

Isso confirma, com fonte, a opção 2 da lista que levantei em 24/10, e descarta as opções 1
(peso × falta) e 6 (piso reduzido de 1 série) como remendos no lugar errado.

**O que NÃO entra nesta fatia** (está na proposta, espera decisão separada): a convenção do alvo
(`alvo = total` vs `alvo = direto`, item D-1), os coeficientes de indireto por par (D-2) e o
estado "manutenção" no contrato/cliente (D-4, cai na Fatia 9, que já prevê o aviso de
honestidade). Nenhum deles bloqueia fechar a Fatia 8.

### Etapa [3] — piso direto em `alocar()`

Fase nova, **antes** da redistribuição de sobra (não depois): reservar `piso_direto[m]` séries
diretas para cada músculo treinável, por nível. Dimensionamento **a medir, não a assumir**:

| nível | piso direto proposto | base |
|---|---|---|
| BEGINNER | **0** | Iversen et al. 2021 e Gentil 2013/2015: para não treinados, compostos bastam; monoarticulares não trazem benefício adicional substancial. É também o que a nossa referência já entrega (tríceps 0 direto no iniciante, total 7,00 = alvo). |
| INTERMEDIATE / ADVANCED | **2 ou 4** (medir as duas) em músculos de `SO_ISOLAMENTO` | RP: MV de bíceps ≈ 4 séries diretas/semana; Iversen: ≥4 séries semanais por grupo. O 2 é o nosso próprio `PISO_SERIES["ISOLAMENTO"]`, mais barato em capacidade. |

Se nem o piso direto couber, ele entra na **ordem de sacrifício derivada** que acabou de virar
`[INV]` — não cria exceção nova nem uma segunda hierarquia.

### Etapa [4] — o zeramento condicional (iii) provavelmente para de disparar sozinho

Hipótese a medir, e é a razão de medir o piso ANTES de mexer em mais qualquer coisa: o gatilho do
zeramento é `alocado[m] < PISO_SERIES["ISOLAMENTO"]`. Com piso direto de 2 na etapa [3], esse
gatilho **deixa de existir** para exatamente os músculos que hoje caem nele (BICEPS 74,
TRICEPS 56, GLUTES 20, FOREARMS 6). Se a medição confirmar, a (iii) continua no código como rede
de segurança mas não dispara mais nos 720 — e os 58/156 abaixo do mínimo somem pela origem, não
por compensação.

### Medições, nesta ordem — cada uma decide se a próxima é necessária

- [ ] **M-1: piso direto 2 vs 4** (0 no iniciante), nos 720. Medir: `entregue > capacidade`,
  eventos de zeramento restantes, quantos ficam abaixo do mínimo efetivo, e **quanto volume
  CHEST/BACK/QUADS/POSTERIOR perdem** — é o preço do piso, e foi o critério que derrubou o corte
  sequencial na decisão (d).
- [ ] **M-2: o piso sozinho basta?** Se M-1 zerar os eventos abaixo do mínimo, o water-filling
  por déficit **não precisa ser construído**. Economia real: é a reforma mais cara da lista.
- [ ] **M-3 (só se M-2 disser que falta):** substituir a sobra proporcional por peso por
  water-filling ponderado pelo déficit `w[m] × (MEV[m] − efetivo[m]) / MEV[m]`, recalculando o
  efetivo a cada série (porque uma série de remada também reduz o déficit do bíceps).

### Critério de pronto da Fatia 8 (atualizado)

Três invariantes, medidas nos 720 nos dois lados (oráculo e Kotlin):

1. `[INV]` `entregue ≤ capacidade` — **0 violações**. (Já atingido pela (iii): 0/720.)
2. `[INV]` nenhum músculo sai com total (direto + indireto) `< VOLUME[m][0]` sem estar
   explicitamente marcado como abaixo do mínimo. Hoje: **58/156 eventos violam isto em silêncio.**
3. `[INV]` nenhum perfil INTERMEDIATE/ADVANCED sai com `direto = 0` em músculo de `SO_ISOLAMENTO`
   sem a mesma marcação.

A marcação em si (o vocabulário "manutenção") é da Fatia 9; a Fatia 8 precisa apenas que o caso
não aconteça em silêncio — ou não aconteça.

## P0 resolvido (2026-10-27): etapa [7] construída, e dois achados

### I1 — confirmado: o catálogo injeta indireto que a prescrição não planejou

Medido nos 720. `papel_trocado` dispara em **312/720 perfis**, e o sentido dominante é o
**inverso** do que eu supunha: não é perder crédito, é ganhar crédito não previsto.

| troca | vagas | efeito |
|---|---|---|
| ISOLAMENTO de costas → preenchido com COMPOSTO | 324 | credita 0,5 ao bíceps **e** 0,5 ao antebraço, por série |
| ISOLAMENTO de peito → COMPOSTO | 134 | credita tríceps |
| ISOLAMENTO de posterior → COMPOSTO | 124 | credita glúteo e antebraço |
| COMPOSTO de ombro → preenchido com ISOLAMENTO | 150 | **deixa** de creditar tríceps |

Delta de indireto somado nos 720 (entregue menos o que o papel planejava): **BICEPS +398 séries**
(média +2,37/perfil em 168 perfis), **FOREARMS +526** (+2,55 em 206), GLUTES +131, TRICEPS −87,5.

A cadeia passa por fora de qualquer decisão: **o catálogo não tem isolamento de costas → o motor
põe um composto na vaga → o composto credita bíceps → o bíceps perde demanda direta → o bíceps é
zerado.** Uma lacuna de curadoria vira decisão de prescrição.

Creditar pelo exercício real continua certo (decisão da Fatia 5, mantida). O que falta é o motor
**medir o que a troca custou**: hoje ele conta que trocou, nunca o efeito. Dois efeitos colaterais
medidos: a oscilação do ponto fixo **não** piora (1,9% com troca contra 4,7% sem), e a medição que
sustenta o D-1 continua limpa (a inversão bíceps 8→7 foi medida na referência, onde não há troca).

### E9 — achado novo: o alvo pode nascer abaixo do próprio piso

`FATOR_OBJETIVO_ALVO` multiplica o alvo **sem respeitar o piso**. Em `MAINTAIN`/`BEGINNER` (fator
0,6 sobre a coluna do iniciante, que É o piso), **os 10 músculos com piso > 0 nascem abaixo dele**:
peito alvo 4,8 contra piso 8, costas 6,0 contra 10. `MAINTAIN`/`INTERMEDIATE` tem o mesmo em BACK
(9,6 contra 10). **660 pares nos 720 perfis.**

Não tem relação com capacidade: acontece na etapa [1], antes de qualquer escassez. Não é bug de
implementação — o código faz o que a especificação manda. É **lacuna da especificação**: ela
declara que a coluna do iniciante é o piso, declara um multiplicador de objetivo, e nunca disse
qual dos dois vence. **Decisão de prescrição pendente**, só o Rafael.

A auditoria **não arbitra**: expõe os dois fatos (`status` mede contra o alvo, `atingiu_minimo`
mede contra o piso) e um perfil desses sai `ATENDIDO` com `atingiu_minimo = False`. Arbitrar aqui
seria repetir o E3 — enterrar uma decisão de prescrição dentro de uma peça de código.

### E1 + E8 — a etapa [7] existe

`auditar()` no oráculo, `ProgramAudit.kt` no Kotlin. Recebe alvo/entregue/indireto/treináveis e
devolve, por músculo: `direto`, `indireto`, `efetivo = direto + indireto`, `minimo`, `alvo`,
`razao`, `status` e `atingiu_minimo`. **Não muda nenhuma série prescrita** — roda depois do
pipeline, lê o resultado e o classifica.

Cinco status: `ATENDIDO` (efetivo ≥ alvo, ou alvo zero) · `MANUTENCAO` (passou do piso, não do
alvo) · `ABAIXO_DO_MINIMO` · `NAO_TREINADO` (nada, nem direto nem indireto) · `SEM_CATALOGO` (não
havia exercício no pool — não é escolha do motor).

O gatilho do aviso de honestidade (`abaixo_do_minimo`) usa **`atingiu_minimo`, não o status** —
senão os 42 perfis `MAINTAIN` do E9 passariam calados, marcados `ATENDIDO`.

A matriz de hoje, travada em teste:

| status | pares (de 7.920) |
|---|---|
| ATENDIDO | 3.613 |
| MANUTENCAO | 1.898 |
| ABAIXO_DO_MINIMO | 1.242 |
| SEM_CATALOGO | 720 (CORE, sempre) |
| NAO_TREINADO | 447 |

**418/720 perfis (58%)** tinham pelo menos um músculo abaixo do piso. _(Números da construção,
antes do limiar por objetivo — ver o E9 abaixo para os de hoje: 367/720.)_

**Testes (rodados em 2026-10-27, `:server:test` 661 verdes, 0 falhas, confirmado nos XMLs, não só
no console):**

| suíte | testes | nota |
|---|---|---|
| `:server:test` | **661**, 0 falhas | 64 XMLs, todos da mesma rodada, nenhum órfão de classe removida |
| `ProgramAuditTest` (novo) | 7 | cinco status · MV contra MEV · caminho de falha do gatilho · sem efeito colateral |
| `VolumeTargetsTest` | 13 (+2) | a regressão do E10 e o limiar do E9 |
| `AllocationTest` | 10 (+1) | inclui o `[INV]` de fonte única, que afirma a propriedade e não só o valor de hoje |
| oráculo (`test_motor_v3`) | 31, 2 vermelhos | os 2 conhecidos do diferencial GLUTES→POSTERIOR em ADVANCED, pergunta aberta da Fatia 9 |

> **Ressalva de contagem:** o doc registrava 658 em 24/10 e a rodada de 27/10 deu 661, mas foram
> 10 testes adicionados (7 + 2, mais 1 ao trocar um teste do `AllocationTest` por dois). A conta
> não fecha nos dois sentidos e **não foi investigada** — o número que vale é o medido, 661
> verdes. Vale regenerar `gerados/testes.md` para a contagem sair de uma fonte só, em vez de
> ficar anotada à mão aqui.

### E10 — DEFEITO: o Kotlin não aplica o fator de objetivo ao alvo semanal

Achado em 2026-10-27, ao implementar o MV no Kotlin. **Divergência de resultado entre os dois
lados, em 180 dos 720 perfis.**

`VolumeTargets.weeklySets(muscle, level, goal)` recebe o objetivo e trata **só**
`GENERAL_HEALTH` (usa o piso). O fator 0,6 de `MAINTAIN` **nunca é aplicado ao alvo semanal** —
está implementado apenas no teto da sessão (`CapacityModel.fatorObjetivo`). O oráculo aplica nos
dois, como a especificação manda: *"esses fatores agora multiplicam **também** o teto da sessão,
não só o alvo semanal"*.

| nível | alvo total MAINTAIN (oráculo) | alvo total MAINTAIN (Kotlin) | |
|---|---|---|---|
| BEGINNER | 36,6 | 61,0 | **+67%** |
| INTERMEDIATE | 66,6 | 111,0 | **+67%** |
| ADVANCED | 88,8 | 148,0 | **+67%** |

**Por que ninguém viu:** nenhum teste do Kotlin exercita `MAINTAIN` no alvo semanal —
`VolumeTargetsTest` usa `GAIN_MUSCLE` e `GENERAL_HEALTH` em todos os casos. O teste diferencial
que pegaria isso é a varredura dos 720 em Kotlin, que **não existe** (foi substituída pelo
substituto de 12 perfis ao fechar a Fatia 6). É o I4/I7 do mapa do motor, confirmado com
resultado divergente, não só estrutura divergente.

**Efeito prático é menor que os 67%**, porque o teto já corta a capacidade de qualquer forma: num
MAINTAIN/INTERMEDIATE/4 dias/60 min o entregue muda pouco (BACK 10→11, BICEPS 3→2). O alvo cheio
muda sobretudo **quem ganha a sobra**, e o efeito cresce com a capacidade (mais dias, mais
minutos).

**CORRIGIDO em 2026-10-27**, decisão do Rafael. `VolumeTargets.fatorObjetivoAlvo()` aplica o
fator ao alvo semanal; `weeklySets()` passou a devolver `Double` e a espelhar `alvos()` termo a
termo, limite pelo MRV incluído. Dois testes de regressão no `VolumeTargetsTest` — um para o
fator, um para o limiar — justamente onde não havia cobertura nenhuma de `MAINTAIN`.

### E9 — RESOLVIDO: o limiar depende do objetivo

Decisão do Rafael (opção **i**): a tabela ganha o **MV** (volume de manutenção, o mínimo para não
perder) ao lado do **MEV** (o mínimo para crescer), e a auditoria compara contra o limiar do
objetivo. Quem pediu `MAINTAIN` é medido contra o MV.

O defeito não estava na prescrição — estava em **comparar contra o limiar errado**. Prescrever
abaixo do MEV em manutenção não é erro: é o que manutenção significa. MEV e MV são grandezas
distintas na literatura e a nossa tabela só tinha uma.

`FATOR_MANUTENCAO` / `minimoEfetivo()` nos dois lados. O fator é **derivado** (`[PROPOSTA]`):
reaproveita o 0,6 de "manter", já revisado. Trocar por tabela revisada é trocar uma função.

Efeito na matriz (os 660 pares que reprovavam por design somem):

| status | antes do MV | depois |
|---|---|---|
| ATENDIDO | 3.613 | 3.613 |
| MANUTENCAO | 1.898 | **2.100** |
| ABAIXO_DO_MINIMO | 1.242 | **1.040** |
| NAO_TREINADO | 447 | 447 |
| SEM_CATALOGO | 720 | 720 |
| **perfis com músculo abaixo do limiar** | 418 | **367** |

Alternativas descartadas: clampar o alvo no piso tornaria `MAINTAIN` e `GAIN_MUSCLE` idênticos
para o iniciante; deixar como estava faria o aviso de honestidade dar alarme falso em 9% dos
pares.

### O que falta no P0

**D-1** — a convenção do alvo. Continua sendo a decisão que governa a etapa [3] e que decide se
o laço do ponto fixo precisa existir.

## Item 8 (buraco de catálogo) — mal diagnosticado, fechado em 2026-10-27

Fui executar o item mais barato do plano e descobri que o diagnóstico estava errado.

**O que eu disse em 26/10:** o pool de CASA/BEGINNER tem 1 composto de costas e nenhum
isolamento, logo faltam exercícios no catálogo. Débito de curadoria, 63 pares.

**O que de fato acontece:** os exercícios existem. O que os tira do pool é o **acessório** —
"Extensão de Ombro com Faixa Elástica" pede ancoragem, "Supino Reto com Halteres" pede banco,
"Flexão Nórdica" pede superfície elevada. A matriz dos 720 simula CASA com **zero acessório
declarado**, e a regra do pré-filtro não tem válvula de escape para acessório ("quem não tem
banco não tem banco").

Medido nos 360 perfis de CASA:

| cenário | vagas vazias | pares abaixo do limiar |
|---|---|---|
| sem acessório (**a matriz de hoje**) | **239** | 768 |
| com banco | **0** | 678 |
| com banco + superfície elevada | 0 | 684 |
| com os 6 | 0 | 719 |

**Um banco sozinho zera as vagas vazias.** E a V66 já tinha medido o mesmo em 22/10 — "sem
acessório 131 vagas · com cadeira 45 · com cadeira e banco 0" — e entregou a solução: o perfil
ganhou onde guardar os acessórios e o onboarding passou a perguntar. **Não há curadoria a
fazer**; o débito que eu abri em 26/10 foi fechado como mal diagnosticado.

### O que isso revela, e é maior que o item 8

**A matriz dos 720 testa CASA sempre no pior caso.** Metade dos perfis é CASA, todos sem
acessório nenhum — o que deixou de ser o caso típico em 22/10. **Todo número medido sobre os 720
carrega esse viés**, incluindo os 2.079 pares da auditoria de 26/10. O comentário do oráculo que
dizia "o cadastro ainda não pergunta isto" estava desatualizado e foi corrigido.

Mudar a matriz muda todos os números históricos do projeto, então **é decisão de escopo, não
conserto**: ou os 720 passam a declarar acessório (e os números de todas as fatias são refeitos),
ou ficam como estão e todo relatório diz explicitamente que CASA ali é o pior caso. Registrado em
`fontes/debitos.md`. **Decisão do Rafael.**

**Achado colateral, não investigado:** com os 6 acessórios os pares abaixo do limiar ficam
levemente PIORES do que só com banco (719 contra 678). Mais candidatos mudam a seleção e, com
ela, o indireto entregue. Mesma família do I1.

## Perguntas que preciso responder antes de codar

- [x] O que cede quando nem os pisos cabem? → corte proporcional por `PESO_PRIORIDADE` (decidido
  2026-10-24).
- [x] Qual é a causa real dos 130/720 `entregue > capacidade` que sobrevivem ao corte
  proporcional — fallback do catálogo, ponto fixo do indireto, ou outra coisa? → piso do
  isolamento em `montar_semana()` (etapa [4]), não o catálogo (ver "Causa isolada" acima).
- [ ] Como estimar a duração do programa gerado (execução + descanso prescrito + rampas + trocas
  de exercício) de forma auditável, antes de decidir baixar o teto?

## Decisões tomadas

- **Corte proporcional por `PESO_PRIORIDADE`, não sequencial** — medido nos 288 perfis de 2–3
  dias antes de decidir: o sequencial resolve `alocado > capacidade` mas faz os 4 grandes
  perderem 25–36% de volume; o proporcional erra bem menos e resolve mais `entregue >
  capacidade` de tabela (47/288 contra 160/288). Alternativa descartada: estender
  `ORDEM_SACRIFICIO` sequencialmente — custava volume demais nos músculos grandes.
- **A rampa não muda** — sensibilidade medida (rampa 1×/sessão em vez de 1×/padrão pesado)
  piorou `entregue > capacidade` (47→84/288), não melhorou. Fica só como medição no histórico.
- **Alternar entre semanas (opção b) não medido** — exige estado de mesociclo (Fatia 10) que não
  existe ainda no oráculo desta fatia. Descartado por dependência, não por prescrição.

## Desenho

### Servidor

_Ainda não desenhado — é o próximo passo desta fatia, antes de codar o laço (C1)._

### Contrato
### Cliente

> Ordem de implementação: Backend → Contrato → Cliente. Dependências apontam para o `Domain`.

## Migration

Nenhuma prevista até aqui — o corte proporcional (já implementado) não mudou schema. O laço de
corte por tempo em si também não deve precisar, mas confirmar ao desenhar.

## Testes (C2)

| Tier | O que | Onde |
|---|---|---|
| 1 — lógica pura | corte proporcional em `alocar()` (`escassez extrema agora cabe...`, `corte proporcional pode remover um dos 4 intocaveis...`) | `AllocationTest.kt` — ✅ feito |
| 1 — lógica pura | laço de corte por tempo (estimar duração, baixar teto, refazer) | a desenhar |
| 2 — serviço/ViewModel com fake | — | — |
| 3 — rota/repositório, Testcontainers | — | — |

Cada um inclui ao menos um caminho de falha.

## Fora de escopo, por decisão

- Alternar entre semanas (opção b) — depende de estado de mesociclo, Fatia 10.
- `AllocationModel.kt`/`AllocationModelTest.kt` — confirmado sem uso em produção (busca em
  `app/`, `server/`, `shared/`, `shared-contract/`), apagados em 2026-10-24 por decisão do
  Rafael. Ver `historico/motor-v3-fatia-8.md`.

## Ao fechar — promover antes de apagar

- [ ] `[REGRA]`/`[INV]` → `docs/regras/01-motor-de-geracao.md` (parte já promovida: corte
  proporcional; falta o laço de corte por tempo quando existir)
- [ ] decisão de arquitetura → `docs/arch/`
- [ ] fora de escopo → `docs/fontes/debitos.md` (parte já promovida: aviso de onboarding)
- [ ] narrativa e defeitos achados → `docs/historico/motor-v3-fatia-8.md` (em andamento, não
  fechado)
- [ ] migration → `docs/migrations/INDICE.md`
- [ ] `ESTADO.md` atualizado, `gen_tudo.py` rodado
- [ ] **este arquivo apagado**

## CORE removido do motor e do contrato — 2026-10-27 (V68)

Saiu da varredura de cobertura do pool (ambiente × nível × músculo × papel, nos 4 cenários de
acessório de CASA; ACADEMIA tem tudo, é cenário único — 176 células).

**Achado A — vazio real** (nenhum exercício em papel nenhum, a vaga fica sem exercício):

| ambiente | músculo | combinações vazias |
|---|---|---|
| ACADEMIA | CORE | 3/3 (todos os níveis) |
| CASA | CORE | 12/12 (níveis × cenários) |

Só CORE, em 15 de 15. E era **dormente**: `VOLUME["CORE"] = (0,0,0,0)`, então `montar_semana()`
nunca abria vaga — não quebrava nada hoje, e viraria vaga vazia de verdade no instante em que
alguém desse alvo > 0. Na auditoria da etapa [7], os **720 `SEM_CATALOGO` da matriz eram todos
dele**, um por perfil.

**Achado B — papel trocado sistemático** (um papel vazio, o outro cobre; `preencher()` troca e
dispara `papel_trocado`). Só em CASA, ACADEMIA nunca falta:

| músculo | falta isolamento | falta composto |
|---|---|---|
| BACK | 9/12 | 0/12 |
| CHEST | 6/12 | 0/12 |
| POSTERIOR | 8/12 | 0/12 |
| QUADS | 4/12 | 0/12 |
| SHOULDERS | 0/12 | 1/12 |

Isto **quantifica o I1**: em CASA o catálogo de isolamento dos 4 protegidos é raso. BACK e
POSTERIOR ficam sem isolamento em quase todo cenário, **inclusive com os 6 acessórios** — ali o
que falta não é acessório, é exercício curado para casa nesse papel. QUADS só tem isolamento em
CASA no nível ADVANCED (gate de nível, não de acessório). Consequência: todo perfil CASA que pede
isolamento desses músculos recebe na prática mais um composto, empurrando indireto não planejado
para os sinergistas. **Não resolvido — é curadoria, não motor.**

### O que foi feito com o achado A

CORE removido do motor **e do contrato**, decisão do Rafael (o catálogo já havia sido limpo por
ele antes desta sessão).

Conferido no banco **antes** de cortar o enum, porque `focus_areas` é dado de usuário:

```
SELECT count(*) FROM profiles WHERE focus_areas LIKE '%"CORE"%';   -> 0
SELECT count(*) FROM programs WHERE focus_muscles LIKE '%CORE%';   -> 0
```

Zero nos dois ⇒ corte limpo, sem migration de dado.

Tocado: `spec_v3.py` (ALVOS, VOLUME, SO_ISOLAMENTO, PESO_PRIORIDADE, LOWER), `test_motor_v3.py`
(ordem de sacrifício esperada, snapshot da auditoria), `VolumeModel.kt` (o enum `TargetMuscle`
em si, `VolumeTable` do v2 e `targetsForFocus`), `SlotFiller.kt`, `StructureEngine.kt` (4
lugares), `Allocation.kt` (`PESO_PRIORIDADE`, `SO_ISOLAMENTO`), `SessionComposition.kt`,
`WeekStructure.kt`, `VolumeTargets.kt`, `MuscleGroup.kt` (contrato), `Rotulos.kt` (cliente) e os
5 testes Kotlin que citavam CORE. Mais a **V68**, que aperta o `CHECK` de `volume_target` e falha
alto se alguma linha ainda usar o valor.

**Medição depois de remover, nos 720:** `ATENDIDO` 3613, `MANUTENCAO` 2100, `ABAIXO_DO_MINIMO`
1040, `NAO_TREINADO` 447, 367 perfis reprovados — **idênticos** aos de antes. Só os 720
`SEM_CATALOGO` desapareceram. Oráculo: 31 testes, as 2 vermelhas conhecidas (diferencial
GLUTES→POSTERIOR em ADVANCED, pergunta aberta da Fatia 9), nenhuma nova.

`gerar_v64.py` ficou **com** CORE de propósito: a V64 já foi aplicada e o gerador tem de
continuar reproduzindo o arquivo que gerou. Nota no próprio arquivo apontando para
`spec_v3.ALVOS` como lista viva.

### Dois efeitos colaterais que pedem decisão (não consertados)

1. **`SEM_CATALOGO` não dispara mais** em nenhum dos 720. Continua sendo a defesa correta para um
   alvo que *perca* cobertura, e o teste agora simula isso tirando um alvo arbitrário de
   `treinaveis` em vez de depender do acidente CORE — mas, pela convenção do projeto, defesa que
   nunca dispara pede decisão explícita.
2. **`VolumeTargets.V.mrv` é `Int?`** e o ramo `if (v.mrv != null)` em `weeklySets()` existia
   **só** por causa de CORE, o único alvo sem teto. Hoje todos os 10 têm MRV. Manter nullable
   como defesa declarada ou simplificar para `Int` é decisão sua.

Mais um resíduo menor: o recurso Android `enum_musculo_core` ficou órfão (não quebra build, é
aviso de recurso não usado). Não removi para não mexer em arquivo de tradução por locale.

## Viés da matriz dos 720 — resolvido por medição, 2026-10-27

Eu havia posto esta decisão **antes** do D-1, com o argumento de que os números do D-1 sairiam
dela. **Estava errado** — medido:

| cenário | pares < piso | perfis afetados | vagas vazias |
|---|---|---|---|
| hoje (CASA sem acessório) | 1.487 | 367 | 239 |
| CASA com banco | 1.397 (−6%) | 302 (−18%) | 0 |
| CASA banco+elevada | 1.403 | 302 | 0 |
| CASA com os 6 | 1.438 | 310 | 0 |

O viés move duas grandezas de formas opostas:

- **Déficit de volume: robusto** (−6%). A conclusão "91% é capacidade física" sobrevive, e **toda
  medição anterior sobre os 720 continua valendo**. O D-1 não depende desta decisão.
- **Vagas vazias: 100% artefato** (239 → 0 com um banco). Esse número nunca deve ser citado como
  problema de produto.

**Decisão: a matriz fica como está.** Ela é a fixture do teste diferencial, onde pior caso é
vantagem e estabilidade histórica vale mais que representatividade. O número de produto passa a
existir ao lado, congelado em `test_motor_v3.py::test_o_caso_realista_de_casa_tem_estes_outros_numeros`
(1397 / 302 / 0). Oráculo: 32 testes, as 2 vermelhas conhecidas.

**Achado novo, não investigado:** com os 6 acessórios o resultado é **pior** que só com banco
(1438 contra 1397 pares; 310 contra 302 perfis). Ter mais equipamento piora o programa — a
seleção de `preencher()` não é monótona no tamanho do pool. `[INV]` que daria para afirmar e hoje
não vale. Em `fontes/debitos.md`.

## D-1 decidido e contabilidade honesta — 2026-10-27

**Decisão do Rafael: híbrido.** Uma contabilidade só (volume efetivo = direto + indireto
fracionado); o piso direto é restrição adicional para INTER/ADV, não segunda forma de contar.
Opção B descartada. `[REGRA]` na especificação, com a história em quatro passos.

### Procedência auditada (condição 1)

A primária é a **`VOLUME` por músculo** — `REFERENCIA_ALVO_TOTAL` é exatamente a soma das colunas
dela, e `REFERENCIA_DEMANDA_DIRETA` é essa soma menos o indireto. Nenhuma das duas é fonte.
A `VOLUME` veio de `ARCH #26`, cuja fonte declarada é RP/Fitbod/Dr. Muscle e cujas linhas nomeiam
"primary X" como sinal — **convenção direta**. O v3 redefiniu como total subtraindo indireto, sem
subir números; o item 20 subiu só os braços. **Veredito: tabela mista** — braços eram total por
intenção; antebraço, glúteo e posterior continuavam herdados em convenção direta.

### O que mudou (condições 1, 2 e 3)

| | antes | depois |
|---|---|---|
| `costas → antebraço` | 0,5 | **removido** (sem fonte) |
| `posterior → antebraço` | 0,5 | **removido** (sem fonte) |
| `glúteo → posterior` | 0,2 | **removido** (sem fonte) |
| `posterior → glúteo` | 0,5 | mantido, analogia de Kubo 2019, confiança **média** |
| alvo do glúteo | 4/8/12 | **8/10/12** (decisão de produto) |
| alvo total | 61/111/148 | **65/113/148** |
| demanda direta | 44/86/113 | **49/94/122** |

Antebraço: de **0 direto em todo nível** para 3/6/9, **sem subir alvo**. Posterior: alvo direto =
alvo total, consistente por tabela.

### Os 720 remedidos — PROVISÓRIO, antes do piso direto

| medida | antes | agora |
|---|---|---|
| pares abaixo do MEV | 1.487 | **2.088** |
| perfis reprovados | 367 | **417** |
| ATENDIDO | 3.613 | 2.982 |
| NAO_TREINADO | 447 | **692** |
| entregue > capacidade | 130/720 | **167/720** |

O `+245` em `NAO_TREINADO` é **exatamente** o antebraço — identidade contábil. O número piorou
40% porque o crédito sem fonte maquiava déficit, não porque o motor piorou.

### As duas asserções, medidas

- **(a) nenhum "coberto" abaixo do MEV → 0 violações.** Já satisfeita.
- **(b) nenhum músculo pequeno de INTER/ADV coberto com direto = 0 → 306**, dos quais:
  - **134 são `ATENDIDO`** (tríceps 88 + glúteo 46) — **violação real, é sobre estes que o piso
    direto age**;
  - 172 são `MANUTENCAO` (bíceps 76 + tríceps 72 + glúteo 24) — **já honestos**, o piso não
    precisa tocá-los.

### Achados colaterais

1. **As 2 vermelhas antigas do oráculo fecharam.** O diferencial `POSTERIOR: -0,8` no avançado,
   atribuído a "pergunta aberta da Fatia 9", era o `glúteo → posterior` 0,2 (× 4 séries = 0,8).
   Oráculo: **32 testes, 0 vermelha**.
2. **O grafo de coeficientes ficou acíclico** (doadores e recebedores disjuntos), mas
   `ponto_fixo_oscilou` **subiu de 26 para 44/720**. O ciclo real do ponto fixo é o **orçamento**,
   não o grafo — então **o laço continua necessário**, o que responde a pergunta que o D-1
   deixava aberta sobre ele.
3. Dois testes viraram tripwire do `[INV]` em vez de serem apagados (o do glúteo→posterior nos
   dois lados), porque a asserção original virou tautologia depois da remoção.

## Oscilação do ponto fixo — medida em 2026-10-27

**A escolha do estado é determinística e documentada**, não "o último calculado":
`min(ciclo, key=(soma do indireto, itens ordenados))` — menor indireto total, desempate pela
lista ordenada. O motivo está no comentário do código: é o estado que **menos supõe trabalho não
feito**.

Nos 44/720 que oscilam (24 com ciclo de 2 estados, 20 com 3), a diferença entre estados é de
**1 a 3 séries** por músculo. Mas **39 dos 44 trocam rótulo**:

| músculo | troca | perfis |
|---|---|---|
| BICEPS | `ABAIXO_DO_MINIMO` ↔ `MANUTENCAO` | 16 |
| FOREARMS | `ABAIXO_DO_MINIMO` ↔ `NAO_TREINADO` | 16 |
| TRICEPS | `ABAIXO_DO_MINIMO` ↔ `ATENDIDO` | 10 |
| CALVES | `ABAIXO_DO_MINIMO` ↔ `MANUTENCAO` | 8 |
| CHEST | `ABAIXO_DO_MINIMO` ↔ `MANUTENCAO` | 4 |
| TRICEPS | `ATENDIDO` ↔ `MANUTENCAO` | 3 |
| POSTERIOR | `ATENDIDO` ↔ `MANUTENCAO` | 2 |
| GLUTES | `ATENDIDO` ↔ `MANUTENCAO` | 2 |

### Remedição escolhendo cada extremo do ciclo

| métrica | atual | melhor | pior | faixa |
|---|---|---|---|---|
| **134** — `ATENDIDO` com direto 0 (INT/ADV) | 134 | 134 | 134 | **+0 / +0** |
| 245 — antebraço com direto 0 | 245 | 245 | 261 | +0 / +16 |
| 167 — `entregue > capacidade` | 167 | 149 | 170 | −18 / +3 |

**Os 134 são invariantes** — a conjunção que os define (`direto == 0` **e** `ATENDIDO` **e**
nível ≠ iniciante) cai fora de todas as trocas: as 10 do tríceps são em perfis `BEGINNER`, e as
`ATENDIDO ↔ MANUTENCAO` ocorrem em estados com direto > 0. **O piso direto pode ser dimensionado
sobre eles sem risco de estar medindo ruído de desempate.**

Os outros dois passam a ser declarados como faixa. Corrigido em `fontes/debitos.md`.
