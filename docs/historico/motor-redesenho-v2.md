# Motor v2 — redesenho completo (orçamento primeiro) — plano

_Aberta em 2026-10-11. Rascunho: não é fonte da verdade (ver README desta pasta)._

> **Escopo:** reescrita do `StructureEngine` + `SlotFiller`. Não é curadoria de catálogo e não
> depende da fatia da lista principal — mas a desbloqueia (ver §2.4).
>
> **Tudo que está medido aqui foi medido.** Simulação fiel do pipeline atual
> (`DeterministicWorkoutGenerator` → `StructureEngine` → `ExercisePreFilter` → `SlotFiller`,
> replicados linha a linha) e do pipeline proposto, rodados sobre a **matriz completa de perfis**:
> objetivo × nível × dias × idade × ambiente × foco × split. **14.256 gerações** no motor atual,
> **3.456 pares** atual↔v2 na comparação direta. Pool = as 79 da lista principal
> (`tools/cache/lista-principal-detalhada.xlsx`), pós-V64 e pós-regra da Rosca Inversa.
> Limitação da medição: `contraindications` não está em nenhum export, então **limitações
> corporais não entraram na simulação** — os números de slot vazio são piso, não teto.

---

## 0. Painel — decidido × aberto (atualizado 2026-10-12)

> Índice, não fonte: cada linha aponta para a seção onde a decisão e o motivo estão.
> Modelo lógico, relacional e fórmulas: **§12**.

**Decidido**

| tema | onde |
|---|---|
| Orçamento em minutos é o 1º cálculo; `sessionMinutes` 45/60/75/90 (default 60) | §4, Q1 |
| Indireto 0,5 no sinergista | Q2 |
| Descanso: pesado 120 s, isolamento 60 s | Q3 |
| Mesociclo de 4–6 semanas com progressão (o "sim"; o desenho não) | Q4 |
| Falha rígida só em ACADEMIA | Q5 |
| `n=2` = âncora + isolamento em alongamento | Q6 |
| Fator de objetivo: LOSE_FAT 0,9 · MAINTAIN 0,75 | Q7 |
| Escassez por prioridade; sem MEV, reduz escopo e avisa; papéis por composição; foco primeiro na sessão; âncora fixa na semana; dedup só no dia; penalidade de padrão só entre compostos; seed derivada; slot vazio nunca silencioso | §4 |
| Lista principal = único pool, filtro no `SlotFiller`; Rosca Inversa = antebraço; cobertura por alvo × papel; V64 | `motor-lista-principal.md` |
| `profile.level` segue mandando em volume, prescrição e foco | §4 + lista 10-10 |
| Check-in antes do treino: **não entra** | §11.12 |
| Série de abertura conservadora (RIR alvo +1) | §11.12 |
| Botão "senti dor" entra; [REGRA] dor persistente → troca de exercício, o app prioriza a saúde | §11.12 |
| Relato de dor só online, nunca no SQLDelight | §11.12 |
| Peso corporal e idade no laço: **fora por ora** (uso atual da idade continua) | §10 |
| Limitações articulares **removidas por completo** (onboarding, campo, filtro, dados de teste); PAR-Q fica; `contraindications` do catálogo só na troca por dor | §11.13 |
| Q19 — teto de séries por sessão: iniciante 16 · intermediário 18 · avançado 18 | §12.6 |
| Q20 — teto de 11 séries fracionadas por músculo por sessão (rede de segurança; constante a recalibrar) | §12.6 |
| Q24 — abdômen em séries × repetições (isometria em séries × segundos), volume pelo alvo semanal de CORE; minutos extras não viram mais abdômen | §12.6 |
| Q22 — teto de força não escala com os minutos; acima de 60 min, bloco extra de CARDIO ou ABDÔMEN à escolha do usuário | §12.6 |
| Q12 — quatro camadas + laço como arquitetura; Q13 — motor sugere carga após calibração; modelo de dados e fórmulas do §12 aprovados como estrutura (Rafael, 2026-10-12: "aprovado"). Itens do §12 marcados com outras Q seguem abertos | §11.10, §12 |

**Aberto (decisão do Rafael)**

| tema | onde |
|---|---|
| Escala de esforço: frases de referência por rótulo e se a coleta sai antes do M1 (substitui a Q14) | §11.11 |
| Q15 — mesociclo avança por conclusão, não por calendário | §11.10 |
| Q16 — STRENGTH no `Goal` | §11.10 |
| C1 exigência técnica × nível do catálogo · C2 `is_base` · C3 CORE sem candidato (Q17) | §3, §11.6 |
| Q18 — fundação de dados (F1–F8) antes do M1 | §11.10 |
| Q8 — foco: reserva / bloco de especialização | §3, §11.5 |
| Q10 — piso de 3 séries antes de sacrificar músculo | §11.2 |
| Q11 — contabilidade de volume única motor × análise | §11.2 |
| Exceção GLUTES→POSTERIOR 0,2 (medida, sem confirmação) | §3 |
| Q21 nenhum músculo grande 1×/semana (na prática: tirar PPL das opções de 3 dias) · Q23 abdômen só no bloco extra? | §12.6 |

**Pendência técnica (sem decisão de produto)**

- Constantes do §5 e limiares do dia ruim: calibrar no teste do atleta sintético (§11.8).
- Curadoria: abdômen, panturrilha, lista caseira (F8).
- Revisão dos textos do fluxo de dor.

---

## 1. O que é e por que agora

O traçado de 2026-10-11 achou cinco defeitos no `StructureEngine`. Medidos em escala, os cinco são
**sintomas de um só erro de arquitetura**, e o erro é o oposto do que a própria ARCH #26 decidiu.

A ARCH #26 diz, textualmente: *"o valor-mãe conceitual é a capacidade de recuperação — o orçamento
de estresse que o indivíduo transforma em adaptação por semana. Todo o resto é alocação dentro desse
orçamento."* **Esse orçamento nunca foi implementado.** O que existe no código são dois tetos
(`SESSION_MAX_EXERCISES = 6`, `SESSION_MAX_SETS_TOTAL = 20`) aplicados *depois* de a prescrição estar
pronta, por um laço que tira série de quem tem mais.

O resultado é um pipeline que anda numa direção só:

| passo | o que decide | o que ele **não** sabe |
|---|---|---|
| 1 | alvo semanal por músculo | quantos dias existem, quanto tempo a sessão tem |
| 2 | split → dias e frequência | que o alvo do passo 1 não cabe |
| 3 | séries, exercícios e **corte** | por que o alvo era aquele, quem era o foco |

Nenhuma informação volta para trás. Com o perfil do traçado (INTERMEDIATE · 4 dias · foco Costas), a
soma dos alvos é **117 séries/semana** e a capacidade nominal do programa é 4 × 20 = **80**. Trinta e
dois por cento da prescrição está inviável antes de qualquer exercício ser escolhido — e o motor
descobre isso série por série, cortando no escuro.

**Volume por músculo não é propriedade independente do músculo.** É alocação de um orçamento escasso
e compartilhado: tempo de sessão, dias disponíveis, recuperação sistêmica. Uma tabela de volume que
não vê `daysPerWeek` nem minutos de sessão está prescrevendo um programa que não existe.

---

## 2. Diagnóstico medido (motor atual)

### 2.1 Nenhum músculo chega perto do alvo, e a hierarquia está invertida

Atendimento = séries entregues/semana ÷ alvo do passo 1. Média das 14.256 gerações:

| | CHEST | BACK | SHOU | QUAD | POST | GLUT | BICE | TRIC | FORE | CALV | CORE |
|---|---|---|---|---|---|---|---|---|---|---|---|
| **média** | 69% | 60% | 62% | 50% | 50% | 29% | 65% | 62% | 36% | 25% | **0%** |
| **mediana** | 64% | 50% | 50% | 47% | 43% | 0% | 60% | 71% | 0% | 0% | 0% |
| **≥90% do alvo** | 29% | 20% | 18% | 12% | 15% | 10% | 31% | 30% | 21% | 6% | 0% |

Músculos grandes: **58%** do alvo. Pequenos: **36%** — mas quando se conta o **volume indireto
fracionado** (0,5 série por série de composto sinérgico, como a literatura conta), a hierarquia
inverte de vez:

| atendimento efetivo (direto + indireto) | grandes | pequenos |
|---|---|---|
| motor atual | **60%** | **82%** |

Tríceps recebe **139%** do alvo, antebraço **121%**, bíceps **116%** — enquanto costas recebe 60% e
quadríceps 51%. O motor gasta o recurso mais escasso do sistema nos músculos que menos precisam
dele. **CORE fica em 0% em 100% dos perfis**: o `StructureEngine` reserva 5–16 séries/semana para um
alvo que o catálogo não cobre, e essas séries não vão para lugar nenhum — são perdidas, não
redistribuídas.

### 2.2 O foco não funciona — e às vezes funciona ao contrário

11.664 gerações com foco declarado:

- atendimento do músculo focado: **65%** (efetivo)
- **ganho real de focar: +1,01 série/semana** — medido em pares foco-ON × foco-OFF do **mesmo
  perfil** (3.456 pares), com **mediana zero**: no caso típico, declarar foco **não muda nada**. O
  bônus nominal da tabela é +3.
- ganho ≥ 2 séries em apenas **20%** dos casos
- em **2% dos casos, declarar foco REDUZIU** as séries do músculo focado
- comparado aos outros músculos grandes: foco **51%** × não-foco **58%** — o músculo priorizado
  fica **abaixo** dos outros grandes em **66% dos perfis**

O bônus é somado no passo 1 e destruído no passo 3: o corte percorre a ordem canônica, que começa
nos grandes, então CHEST/BACK/SHOULDERS levam dois cortes cada e os pequenos levam um. **Isto viola
o [INV] do foco em `docs/regras/01-motor-de-geracao.md`.** E mesmo se sobrevivesse: +3 sobre uma base
de 16 é +19%, dentro do ruído da curva dose-resposta. Foco precisa agir em **três alavancas** —
séries protegidas do corte, **posição na sessão** (músculo-foco primeiro, quando fresco) e número de
ângulos. Hoje a ordenação é canônica e o foco não muda nada na ordem.

### 2.3 A sessão é mal composta e o teto está na moeda errada

| | medido |
|---|---|
| slots que são COMPOSTO_PESADO | **56%** |
| slots que são COMPOSTO_ACESSORIO | 11% |
| **dias Upper sem nenhum COMPOSTO_ACESSORIO** | **100%** (36/36) |
| séries por sessão (teto: 20) | **14,4** |
| minutos por sessão estimados | **43** (p10 29 · p90 58) |

O `allocateExercises` é inerte em Upper/Lower (6 músculos = o teto, o `while` nunca roda), então o
dia Upper é **seis compostos pesados a 5-8 reps, RIR 2, 150 s de descanso, e zero trabalho em
comprimento muscular longo**. Isso não é só um bug: é prescrição ruim para o objetivo selecionado.
Uma sessão inteiramente de compostos pesados tem razão estímulo/fadiga desfavorável — fadiga
sistêmica e articular escalam rápido, o estímulo por série satura, e perde-se justamente o
isolamento em alongamento (crucifixo, pulley, elevação lateral no cabo, flexora) que é onde a
evidência recente mais aponta para hipertrofia.

E o teto está na moeda errada: **o limite do usuário é minuto, não série.** 14,4 séries nessa
configuração dão ~43 min; o usuário típico tem 60–75. O motor deixa tempo na mesa **e** mata de fome
peito e costas ao mesmo tempo, porque trata uma série de agachamento (150 s de descanso) e uma de
rosca inversa (75 s) como a mesma unidade de orçamento.

### 2.4 Slot vazio é a regra, não a exceção

| | perfis com ≥1 slot vazio | vazios/slots |
|---|---|---|
| geral | **86%** | 26% |
| ACADEMIA | 70% | 7% |
| CASA | **100%** | 44% |
| BEGINNER | **100%** | — |

Como o `?: continue` descarta em silêncio, o usuário recebe um dia mais curto e ninguém é avisado.
**Isto é o que hoje bloqueia a decisão "slot sem principal → falha a geração"**: ligar a falha
rígida com 86% de incidência derruba quase todo mundo. O v2 resolve isso (§5.1).

### 2.5 Dois defeitos que o traçado não revelou — e são os mais graves

**A. A seed aleatória destrói a progressão.** `Random.nextLong()` por geração:

- 5 gerações do mesmo perfil → **5 programas diferentes**
- sobreposição média de exercícios entre duas gerações: **74%**
- em **76% dos pares**, pelo menos uma âncora (composto pesado) muda
- `alreadyUsed` é por programa, então **zero exercícios se repetem entre as sessões da semana**

Hipertrofia é dirigida por **sobrecarga progressiva**, que exige exposição repetida à mesma curva de
resistência para que carga e reps sejam comparáveis semana a semana. Um programa que proíbe o
agachamento de aparecer nas duas sessões da semana impede o usuário de progredir no agachamento. E
sem modelo de progressão entre semanas, o que o usuário recebe é um **retrato de uma semana**, não um
programa. Novidade na seleção de exercício não é variável de treino que valha randomizar.

**B. O objetivo quase não existe.** `Goal` tem quatro valores e o motor produz **dois
comportamentos**: `GENERAL_HEALTH` usa a coluna iniciante, e `GAIN_MUSCLE`, `LOSE_FAT` e `MAINTAIN`
são **literalmente idênticos** — mesmo volume, mesmas reps, mesmo descanso, mesmo RIR. Quem marcou
"perder gordura" recebe exatamente o programa de quem marcou "ganhar massa".

### 2.6 Escassez estrutural: alvo somado ÷ capacidade nominal (dias × 20)

| nível | 2d | 3d | 4d | 5d | 6d |
|---|---|---|---|---|---|
| BEGINNER | 1,48 | 0,99 | 0,74 | 0,59 | 0,49 |
| INTERMEDIATE | 2,66 | 1,77 | **1,33** | 1,06 | 0,89 |
| ADVANCED | **3,52** | 2,35 | 1,76 | 1,41 | 1,17 |

Todo intermediário com ≤4 dias e todo avançado com ≤5 dias recebe uma prescrição que não cabe no
próprio programa. O motor nunca diz isso; ele amputa.

### 2.7 Outros achados menores, confirmados no código

- **`equipmentWeight` inverte o princípio** que o Rafael descreveu: `BARBELL = MACHINE = CABLE = 1.0`
  e `DUMBBELL = 0.8` → **máquina vence halter**, e barra não tem vantagem nenhuma sobre máquina. O
  peso mede "quanta carga externa cabe", não "quão guiado é o movimento".
- **BODYWEIGHT é deterministicamente morto na academia**: 1,0 − 0,2 = 0,8 e o jitter vai só a 0,35,
  então a diferença nunca fecha. Barra Fixa e Mergulho não ganham slot em nível nenhum.
- **A penalidade de padrão distorce o isolamento.** Depois do supino, Crucifixo/Crossover/Pec Deck
  levam −2,5 por serem HORIZONTAL_PUSH e o **Pullover ganha por ser VERTICAL_PULL** — justamente o
  exercício que talvez nem seja de peito, no lugar do que carrega o peitoral em comprimento longo.
  Redundância de padrão importa **entre compostos** (soma fadiga articular); entre isolamentos o que
  importa é o ponto da curva de força, não o padrão.
- **O papel vem da posição, não da propriedade.** `índice 0 = COMPOSTO_PESADO` faz o papel depender
  de quantos slots o músculo ganhou naquele dia, não da biomecânica do exercício.
- **A idade é aplicada na alavanca menos eficaz.** `ageVolumeFactor` encolhe volume; a idade afeta
  mais a recuperação *entre* sessões do que a tolerância *dentro* da sessão. Frequência, RIR nos
  compostos pesados e descanso importam mais que cortar séries.

---

## 3. Perguntas de desenho — RESPONDIDAS pelo Rafael em 2026-10-11

| # | pergunta | resposta | efeito no desenho |
|---|---|---|---|
| **Q1** | `minutosPorSessao` entra no `ProfileDto`? | **sim** | campo novo, default 60, opções 45/60/75/90 no quiz. A capacidade deixa de ser chute (§5 P0) |
| **Q2** | volume indireto é contado oficialmente? | **sim — o sinérgico recebe 1/2 série por série de composto** | os números de BICEPS/TRICEPS/FOREARMS da `VolumeTable` passam a ser lidos como **total (direto + indireto)**, não como alvo direto |
| **Q3** | descanso do pesado 150 s → 120 s e do isolamento 75 s → 60 s? | **sim** | +13% de capacidade semanal; 150 s era prescrição de força |
| **Q4** | o programa passa a ser mesociclo de 4–6 semanas com progressão? | **sim** | fatia M5; usa a janela `program_weeks_window` (V24) que já existe |
| **Q5** | a falha rígida liga só para ACADEMIA? | **sim** | com o v2, ACADEMIA mede **0 slot vazio em 54/54 configurações**; CASA fica fora até a lista caseira existir |
| **Q6** | `n=2` é âncora + isolamento em alongamento (não âncora + composto acessório)? | **sim** | regra de composição da §5 P3 ratificada |
| **Q7** | `LOSE_FAT` e `MAINTAIN` ganham fator de volume próprio (0,9 e 0,75)? | **ok** | `fatorObjetivo` da §5 P1 ratificado |

**Consequência da Q2 (ajuste feito depois da resposta):** o coeficiente foi **normalizado em 0,5**
em todos os pares sinérgicos, como o Rafael definiu. Sobra **uma exceção documentada**:
`GLUTES → POSTERIOR = 0,2`, porque elevação pélvica e abdução treinam isquiotibial muito pouco
(quadril já estendido, isquio em encurtamento) — tratar como 0,5 descontaria volume direto de
posterior que o usuário não recebeu. Se preferir uniformidade absoluta, é trocar uma constante; o
custo é posterior cair ~8 pontos de atendimento.

**Custo medido da exceção** (perguntado em 2026-10-11): tratar `GLUTES → POSTERIOR` como 0,5 em vez
de 0,2 derruba o atendimento de posterior de **86% para 76%** — 10 pontos. A exceção é
load-bearing, não cosmética.

### Q8 — ABERTA, e ela nasceu da auditoria dos coeficientes

**O bônus de foco deve ser uma RESERVA no orçamento, em vez de um peso no excedente?**

Medido: o v2 entrega **+1,90 série/semana** no músculo focado contra **+3 nominais**, e o foco ainda
fica 2 pontos abaixo dos outros músculos grandes (§6). Num programa limitado por capacidade, +3 para
o foco é −3 para alguém, e hoje o foco só ganha peso 1,8× na distribuição do excedente — o que
reparte a dor em vez de honrar o bônus.

- **(a) manter o peso 1,8×** — o foco é uma inclinação, não uma promessa. Custo: o usuário marca
  "foco em costas" no quiz e recebe +2 séries; a promessa implícita da tela não é cumprida.
- **(b) reserva dura** — o alvo do músculo focado (MEV + 3) entra no **piso** do water-filling,
  antes do excedente. Garante o +3 sempre que o piso couber na capacidade. Custo: os outros grandes
  perdem ~3 séries somadas, e isso fica visível no aviso de capacidade.
- **(c) reserva + mais tempo** — a reserva só é honrada se a capacidade permitir; se não, o motor
  avisa "seu foco pede 75 min/sessão" em vez de diluir.

**Minha recomendação é (c)**: é a única que não mente nem para cima nem para baixo. Mas é decisão de
produto — quanto a tela promete quando o usuário escolhe foco.

**O que ainda NÃO foi decidido** (não bloqueia M1–M4): o enum `Goal` **não tem força/performance**.
Hoje quem quer força recebe prescrição de hipertrofia. É lacuna de produto, fatia própria.

### Conflitos com `motor-lista-principal.md` — ABERTOS (achados em 2026-10-11)

Os dois planos foram escritos em dias diferentes e discordam em três pontos. Nenhum dos dois vence
sozinho; precisa de decisão explícita antes do M3.

| # | este plano (v2) | `motor-lista-principal.md` | por que importa |
|---|---|---|---|
| **C1** | §4: exigência técnica 1–4 **substitui** o `level` do catálogo na seleção ("aprovada em princípio") | 2026-10-10, 2ª rodada: **"Nível FICA"** (`levelsUpTo` e o `+2.0` do score permanecem); a exigência técnica segue [PROPOSTA] "aguardando o ok" | o "ACADEMIA: 0 falha" da §6 foi medido **com** a exigência técnica; sem ela, os bloqueadores B1/B3/B8 voltam |
| **C2** | P5: `is_base` vale **+0,5** no score; §13 manda a ARCH #28 dizer "peso de score" | 2026-10-09: `is_base` fica no banco **sem efeito na geração** (vira débito) | define se a coluna morre ou ganha papel novo |
| **C3** | P1: alvo sem candidato sai do plano (`trainable()`) e o volume é redistribuído | 2026-10-09, exceção CORE: slot CORE sem principal **cai no pool** (`isBase` → STRENGTH) e registra em `Falhas` | comportamentos diferentes para o mesmo caso (catálogo sem abdômen) |

---

## 4. Decisões

_Todas ratificadas pelo Rafael em 2026-10-11 (ver §3). O que vale aqui é o **motivo**: é ele que
vira [REGRA]/[INV] no fechamento._

- **A capacidade vira a primeira coisa calculada, em minutos** — e o alvo semanal é derivado dela,
  não o contrário. É o que a ARCH #26 já decidiu conceitualmente e o código nunca fez. Alternativa
  descartada: manter os tetos de 6 exercícios/20 séries. Custo dela: os 86% de slot vazio e os 60%
  de atendimento dos grandes continuam, e nenhum ajuste de constante resolve, porque o problema é a
  ordem dos passos.
- **Série deixa de ser a unidade de orçamento; minuto passa a ser.** Cada série custa
  `trabalho + descanso do papel`. Uma série de composto pesado custa 2,5 min e uma de isolamento
  1,67 min — hoje as duas custam "1". Alternativa descartada: teto de séries por papel. Custo: não
  resolve a sessão de 43 min com teto de 20.
- **Na escassez, alocação por prioridade (water-filling), nunca equalização para baixo.** Primeiro
  o piso MEV de todos; depois o excedente proporcional ao peso do músculo × multiplicador de foco,
  com teto no alvo e no MRV. Alternativa descartada: o laço "tira de quem tem mais" — é
  precisamente o que inverte a hierarquia.
- **Se nem o piso MEV cabe, o motor reduz o escopo explicitamente** (ordem de sacrifício declarada)
  e **informa**, em vez de entregar 32% do alvo em silêncio.
- **Volume indireto é contado e descontado do alvo direto dos sinérgicos.** Corrige na raiz o
  "antebraço 121%, costas 60%" e libera orçamento para quem precisa.
- **Papel do slot vira composição desejada da sessão, não posição no índice.** `n=1` → âncora;
  `n=2` → âncora + isolamento em alongamento; `n≥3` → âncora + acessórios + isolamento. Garante que
  todo músculo grande com volume suficiente receba trabalho em comprimento longo — hoje, em dia
  Upper, isso **nunca** acontece.
- **A sessão é ordenada por prioridade, não pela ordem canônica**: músculo de foco primeiro, quando
  o usuário está fresco. É a segunda das três alavancas do foco.
- **Exigência técnica (1–4, em código) substitui o `level` do catálogo na seleção** e substitui o
  `equipmentWeight`. É a proposta do Rafael de 2026-10-10, que ele já aprovou em princípio; o v2 só
  a usa. `profile.level` continua mandando em volume, prescrição e gate do foco.
- **`alreadyUsed` passa a ser duro só dentro do dia; entre dias, penalidade suave.** E a âncora de
  cada músculo é a **mesma nas duas sessões da semana** (core lift), para que carga e reps sejam
  comparáveis. Alternativa descartada: manter o dedup por programa. Custo: impede progressão e é a
  causa direta do "Lower B entrega 4 exercícios".
- **Penalidade de padrão só entre compostos.** Entre isolamentos, zero.
- **Seed derivada de `(perfil, split, minutos, mesociclo)`, não `Random.nextLong()`.** "Gerar de
  novo" dentro do mesmo mesociclo devolve o mesmo programa; a rotação acontece na virada do ciclo.
- **Slot sem candidato nunca é descartado em silêncio**: substituição guiada (mesmo alvo, papel
  vizinho) → se não houver, falha nomeada. Casa com a decisão já tomada na fatia da lista principal.
- ~~**A limitação corporal passa a preferir substituição guiada à exclusão em bloco**~~ **OBSOLETO
  (2026-10-12): limitações removidas por completo, ver §11.13.** Texto original: quando houver
  equivalente (terra → terra com trap bar → elevação pélvica). Exclusão em bloco é conservadora
  demais para a maioria dos casos não agudos e é o que esvazia o pool de posterior/costas.

---

## 5. Desenho

### Servidor — o pipeline em seis passos

```
P0  orçamento        dias × minutosPorSessão → capacidade em séries
P1  alvo semanal     tabela × idade × objetivo + foco, condicionado à capacidade (water-filling)
P2  split            SplitCatalog (inalterado) → dias, frequência bruta
P3  estrutura do dia piso de presença → excedente por prioridade → sobra de tempo
P4  volume indireto  mede os compostos de P3, desconta dos sinérgicos, reinveste nos prioritários
P5  seleção          exigência técnica + foco + base, âncora fixa na semana, dedup intra-dia
```

#### P0 — orçamento

```
custoSérie(papel)  = (trabalho(papel) + descanso(papel, objetivo)) / 60     [min]
trabalho           = PESADO 30 s · ACESSÓRIO 35 s · ISOLAMENTO 40 s
AQUECIMENTO        = 6 min/sessão        OVERHEAD_EXERCÍCIO = 1 min
mixTípico          = 0,45·PESADO + 0,20·ACESSÓRIO + 0,35·ISOLAMENTO
capacidadeSéries   = dias × (minutos − AQUECIMENTO − 5×OVERHEAD) / mixTípico
```

**Curva de capacidade medida** (séries/semana viáveis, GAIN_MUSCLE, com os descansos propostos):

| min/sessão | 2d | 3d | 4d | 5d | 6d |
|---|---|---|---|---|---|
| 45 | 32 | 48 | 64 | 80 | 96 |
| 60 | 46 | 69 | **92** | 115 | 138 |
| 75 | 60 | 90 | 120 | **151** | 181 |
| 90 | 74 | 112 | 149 | 186 | 223 |

Alvo somado: BEGINNER **64** · INTERMEDIATE **114** · ADVANCED **153**. Lê-se direto da tabela: o
intermediário cabe em 4 dias × 75 min ou 5 dias × 60 min; o avançado precisa de 5 × 75 ou 6 × 75.
**Este número é o que o motor hoje não tem** — e é o que permite dizer ao usuário "4 dias de 60 min
suportam 80% do seu alvo; com 75 min ele fecha", em vez de entregar 60% calado.

#### P1 — alvo semanal condicionado à capacidade

```
alvoBruto[m] = min( base(nível, objetivo) × fatorObjetivo × fatorIdade + (3 se foco), MRV[m] )
fatorObjetivo: GAIN_MUSCLE 1,0 · LOSE_FAT 0,9 · MAINTAIN 0,75 · GENERAL_HEALTH = piso MEV
piso[m]      = min(MEV[m], alvoBruto[m])                       // MEV = coluna iniciante da tabela

enquanto Σ piso > capacidade:  remove o próximo da ORDEM_DE_SACRIFÍCIO (nunca um músculo de foco)
plano[m] = piso[m]
sobra    = capacidade − Σ plano
repete:  plano[m] += sobra × peso[m]·(1,8 se foco) / Σ pesos,  teto em alvoBruto[m]
```

| peso[m] | | ORDEM_DE_SACRIFÍCIO |
|---|---|---|
| BACK 1,1 · CHEST 1,0 · QUADS 1,0 · SHOULDERS 0,9 · POSTERIOR 0,9 | | FOREARMS → CALVES → CORE |
| GLUTES 0,7 · TRICEPS 0,55 · BICEPS 0,5 · CORE 0,5 · CALVES 0,4 · FOREARMS 0,2 | | → GLUTES → BICEPS → TRICEPS → SHOULDERS |

`FOCUS_MULT = 1,8`. **Alvos cujo par (alvo, papel) não tem nenhum candidato no pool saem do plano
antes do water-filling** (`trainable()`) — é assim que as 8 séries/semana de CORE deixam de ser
jogadas no lixo e passam a ser redistribuídas.

#### P3 — estrutura do dia, em duas etapas

A etapa de piso é o que garante que braço não desapareça quando peito e costas comem o orçamento:

```
frequênciaEfetiva[m] = max(1, min(freqSplit[m], plano[m] div 3))      // respeita o piso de 3 séries/exercício
diasDoMúsculo        = espalhados com offset = índiceCanônico(m) mod nDias   // desencontra os músculos

ETAPA 1 (piso de presença)  : 1 exercício × 3 séries por músculo do dia, em ordem de prioridade
ETAPA 2 (excedente)         : sobe séries (até 5/exercício) e abre exercícios, na mesma ordem
ETAPA 3 (sobra de tempo)    : engorda o slot mais barato de quem ainda está abaixo do alvo

nºExercícios(m) = clamp(sériesSessão div 3, 1, 3)      (2 para alvos só-isolamento)
papéis(n)       = n=1 → [PESADO] · n=2 → [PESADO, ISOLAMENTO] · n≥3 → [PESADO, ACESSÓRIO×(n−2), ISOLAMENTO]
ordem da sessão = foco primeiro → papel → ordem canônica
```

#### P4 — volume indireto

Coeficientes (série sinérgica por série de composto). Só entram músculos em que o trabalho indireto
é **substituto real**: braço, preensão e glúteo. Ombro e posterior ficam fora de propósito — o
alvo `SHOULDERS` é o deltoide inteiro e o supino só cobre a porção anterior; contar como substituto
subserviria lateral e posterior.

| composto de… | dá a… |
|---|---|
| CHEST, SHOULDERS | TRICEPS 0,5 |
| BACK | BICEPS 0,5 · FOREARMS 0,5 |
| POSTERIOR | GLUTES 0,5 · FOREARMS 0,5 |
| QUADS | GLUTES 0,5 |
| GLUTES | POSTERIOR **0,2** — única exceção ao 0,5 (ver §3) |

```
alvoDireto[m] = max(3, alvoBruto[m] − indireto[m])       para m ∈ {BICEPS, TRICEPS, FOREARMS, GLUTES}
                0  se  alvoBruto[m] − indireto[m] < 2,4  (exceto piso direto de 3 em BICEPS/TRICEPS)
orçamento liberado → reinvestido nos prioritários, com teto no alvo
```

#### P5 — seleção

```
exigência(e) = base por equipamento + override
  1 GUIADO    MACHINE, CABLE
  2 ASSISTIDO DUMBBELL, BODYWEIGHT, BAND
  3 LIVRE     BARBELL
  4 TÉCNICO   override: Barra Fixa, Chin-Up, Mergulho, Agachamento Livre, Terra
teto por experiência: BEGINNER ≤2 · INTERMEDIATE ≤3 · ADVANCED ≤4     (substitui levelsUpTo)

score = foco(+4)
      + papel×exigência:  PESADO +0,6·(exig−1) · ACESSÓRIO 0 · ISOLAMENTO +0,6·(4−exig)
      + is_base(+0,5)
      − 1,0  se já usado em OUTRO dia            (suave, não proíbe)
      − 2,5  se é composto E o padrão já saiu neste dia   (só entre compostos)
      + jitter(0..0,35)

âncora: o COMPOSTO_PESADO escolhido para um músculo é REUSADO em todas as sessões da semana
dedup duro: só dentro do dia
seed = hash(objetivo, nível, dias, foco, ambiente, idade, split, minutos, mesociclo)
```

### Contrato

- `ProfileDto.sessionMinutes: Int?` (Q1) — default 60 no server.
- Erro novo de geração, já previsto na fatia da lista principal (`LISTA_PRINCIPAL_INSUFICIENTE`),
  agora com um irmão: **aviso** (não erro) de "alvo acima da capacidade", com o número de séries que
  cabe e a sugestão (mais dias ou mais minutos). É aviso porque o programa sai; só sai menor.
- Mesociclo (Q4): `ProgramDto.mesocycleWeek` / `mesocycleIndex` para a progressão semanal.

### Cliente

- Passo novo no quiz: minutos por sessão (45/60/75/90).
- Tela de geração mostra o aviso de capacidade quando houver, e o erro novo quando a geração falhar.
- Nada muda na troca de exercício (`alternatives` continua fora da geração).

> Ordem de implementação: Backend → Contrato → Cliente.

---

## 6. Validação medida — atual × v2, mesma matriz, mesmo pool

Atendimento **efetivo** (direto + indireto, mesma contabilidade nos dois motores), 3.456 pares:

| | ATUAL | V2 |
|---|---|---|
| média geral | 73% | **86%** |
| músculos **grandes** | 60% | **86%** |
| músculos pequenos | 84% | 87% |
| músculos abaixo de 70% do alvo, por programa | 6,1 | **3,2** |

| por músculo | CHEST | BACK | SHOU | QUAD | POST | GLUT | BICE | TRIC | FORE | CALV | CORE |
|---|---|---|---|---|---|---|---|---|---|---|---|
| ATUAL | 70% | 60% | 63% | 51% | 54% | 95% | 116% | 139% | 130% | 26% | 0% |
| **V2** | **89%** | **90%** | **87%** | **79%** | **85%** | 104% | 102% | 127% | 148% | 37% | 0% |

Tríceps 127% e antebraço 148% no v2 **não são erro de alocação** — são a leitura correta da Q2: com
o indireto contado a 0,5, o trabalho de puxada e de hinge já satisfaz o antebraço sozinho, e o
trabalho de empurrada já satisfaz o tríceps. O que a tabela está dizendo é que **os números de braço
e antebraço da `VolumeTable` precisam ser reescritos como alvo direto** (hoje são alvo total mal
rotulado). Item para a M1.

| foco — medido em pares ON × OFF do mesmo perfil (3.456) | ATUAL | V2 |
|---|---|---|
| ganho real de declarar foco (nominal: +3 séries/semana) | +1,01 | **+1,90** |
| mediana do ganho | **0,0** | **+2,0** |
| ganho ≥ 2 séries | 20% | **58%** |
| casos em que focar REDUZIU séries | 2% | 3% |
| atendimento do foco × outros músculos grandes | 51% × 58% | **82% × 84%** |
| foco abaixo dos outros grandes | 66% dos perfis | **48%** |

> ⚠️ **Dois avisos de honestidade sobre esta tabela, que achei auditando os coeficientes:**
>
> **1. A métrica "foco abaixo da média do programa" que estava aqui antes era contaminada.** Com o
> indireto a 0,5, antebraço bate 152% e glúteo 119% do alvo; qualquer músculo grande a 90% fica
> "abaixo da média do programa" por aritmética, não por defeito. A comparação válida é **foco ×
> outros músculos grandes**, que é a que ficou na tabela.
>
> **2. O foco do v2 melhorou mas NÃO está resolvido.** +1,90 contra +3 nominal, e o músculo focado
> ainda fica 2 pontos **abaixo** dos outros grandes. A causa é estrutural: num programa limitado por
> capacidade, dar +3 ao foco significa tirar 3 de alguém, e o water-filling atual só dá ao foco um
> **peso maior no excedente** (1,8×), não uma reserva. Os 3% de casos negativos são artefato de
> arredondamento na atribuição de dias. **Ver Q8.**

| slots vazios — perfis com ≥1 | ATUAL | V2 |
|---|---|---|
| ACADEMIA | 70% | **0%** |
| CASA | 100% | 36% |
| BEGINNER | 100% | **1%** |
| INTERMEDIATE | 84% | 15% |
| ADVANCED | 84% | 25% |

| determinismo | ATUAL | V2 |
|---|---|---|
| 5 gerações do mesmo perfil | 5 programas diferentes | **1 programa** |
| âncora estável nas sessões da semana | impossível (dedup por programa) | **sim** |
| mesociclos consecutivos | não existe | distintos, 78% de sobreposição |

### O que o v2 **não** resolve — e é catálogo, não motor

Rodando só o v2, em 54 configurações por ambiente:

- **ACADEMIA: 0 falha.** Em nível nenhum, com split nenhum.
- **CASA: 16 de 54 programas com slot vazio**, e **toda** falha é um destes pares:
  `QUADS·ISOLAMENTO` (24 ocorrências) · `POSTERIOR·ISOLAMENTO` (14) · `GLUTES·COMPOSTO_PESADO` (6) ·
  `QUADS/POSTERIOR·ACESSÓRIO` (4) · `GLUTES·ISOLAMENTO` (1).
- **CALVES 37% e CORE 0%** continuam: o pool tem **2** exercícios de panturrilha e **nenhum** de
  abdômen. Teto aritmético da panturrilha = 2 exercícios × 5 séries = 10 séries/semana.

**Requisitos de catálogo que saem disso, em ordem de retorno:**

1. **Abdômen com mídia** (o catálogo não tem nenhum) — destrava 5–16 séries/semana hoje perdidas.
2. **3º e 4º exercício de panturrilha.**
3. **Lista principal caseira**: isolamento de quadríceps e de posterior com halter/elástico/corporal,
   e um composto de glúteo caseiro. Sem isso CASA não fecha em motor nenhum.
4. Os dois furos já conhecidos: **`is_compound` do Face Pull** (está `true`, é acessório) e
   **FOREARMS do iniciante**, que a escala de exigência zera (as duas Roscas Inversas são BARBELL) —
   entra a Rosca de Punho com Halteres, que já é `is_base` nos 270.

---

## 7. Fatias de implementação

| # | fatia | entrega | depende de |
|---|---|---|---|
| **M1** | orçamento + water-filling | `CapacityModel`, `VolumePlanner`; `StructureEngine` passa a derivar o alvo da capacidade; `sessionMinutes` no contrato; **`VolumeTable` de braço reescrita como alvo direto** | — (Q1–Q3 e Q7 respondidas) |
| **M2** | estrutura do dia em duas etapas | `allocateExercises` morre; piso de presença, papéis por composição (`n=2` = âncora + isolamento), ordem por foco | M1 |
| **M3** | seleção v2 | exigência técnica (código), âncora da semana, dedup intra-dia, seed determinística, penalidade só entre compostos | M2 |
| **M4** | falha explícita + aviso de capacidade | erro novo + aviso; **liga a falha rígida em ACADEMIA**, CASA fora | M3, fatia da lista principal |
| **M5** | mesociclo e progressão | 4–6 semanas, progressão de série/carga, deload, rotação na virada | M3 |

M1+M2+M3 já entregam todos os números da §6. M4 e M5 são produto novo.

> **OBSOLETO (2026-10-12, §11.13: limitações removidas).** ~~Pré-requisito de M4 que ainda não foi medido:~~ a simulação rodou **sem** `contraindications`
> (o dado não está em nenhum export). Antes de ligar a falha rígida, rodar a matriz de cobertura
> com as limitações reais — ombro, joelho, lombar, punho, impacto — contra o banco.

---

## 8. Migration

**Nenhuma migration de motor.** A exigência técnica mora em código, pelo mesmo motivo já ratificado
no `EquipmentEnvironmentMap` ("onde esse equipamento roda é julgamento nosso e muda sem migration"),
e a coluna `level` fica intacta — só deixa de ser lida na seleção (vira vitrine + débito de limpeza).

Migrations que continuam valendo, da fatia da lista principal: **V64** (taxonomia dos 12/14 + mídia
da Visão 2 + Adutora `primary→GLUTES` + Face Pull `primary/category→BACK`, **somando agora
`is_compound=false` no Face Pull**) e **V65** (`is_principal`).

Novas, de dado, fora desta fatia: inserção dos abdominais e das panturrilhas (requisito 1 e 2 da
§6), e a lista principal caseira (requisito 3).

Treinos já gerados estão persistidos — **nada reescreve histórico**; só gerações novas mudam.

---

## 9. Testes (C2)

| Tier | O que | Onde |
|---|---|---|
| **1 — lógica pura** | `capacidadeSéries` monótona em dias e em minutos; water-filling conserva o orçamento (Σ plano ≤ capacidade) e **nunca** deixa um músculo do plano abaixo do MEV; **[INV] foco**: (i) declarar foco **nunca reduz** as séries do músculo focado — os 3% de casos negativos medidos são caminho de teste, não aceitável; (ii) atendimento do foco ≥ atendimento dos outros músculos **grandes** (nunca "≥ média do programa": métrica contaminada pelo indireto, ver §6); (iii) se a Q8 fechar em (b)/(c), ganho = +3 sempre que a capacidade permitir; ordem de sacrifício nunca remove músculo de foco; `alvoDireto + indireto ≤ MRV`; piso de 3 séries/exercício respeitado; papéis por composição (`n=2` ⇒ existe ISOLAMENTO); ordem da sessão põe foco primeiro | `CapacityModelTest`, `VolumePlannerTest`, `StructureEngineTest` |
| **1 — seleção** | âncora idêntica em todas as sessões do mesmo músculo na semana; dedup duro intra-dia e **permitido** entre dias; penalidade de padrão **não** se aplica a isolamento; teto de exigência por nível; mesma seed+mesociclo ⇒ mesmo programa; mesociclo+1 ⇒ programa diferente; **slot sem candidato devolve falha, nunca `continue`** | `SlotFillerTest` |
| **2 — serviço/fake** | serviço propaga o erro novo e o aviso de capacidade; `alternatives` continua devolvendo exercício fora da lista principal (trava o filtro no lugar errado) | `ProgramServiceTest`, `ExerciseServiceTest` |
| **3 — Testcontainers, lista real** | **matriz de cobertura**: para todo (objetivo × nível × dias × split × ambiente × limitação comum), nenhum slot vazio em ACADEMIA; CASA listada como exceção conhecida; contagem de `is_principal` bate | `ExerciseEngineIntegrationTest` |

Caminho de falha em cada tier. Comandos: `:server:test` · `:server:integrationTest` (Docker) ·
`:konsist:test` · testes do cliente.

**Teste de regressão que eu recomendo nascer junto:** uma *golden matrix* — o atendimento por músculo
da §6 gravado como baseline, com tolerância. É o único jeito de um ajuste futuro de constante não
reintroduzir silenciosamente o que a §2 mediu.

---

## 10. Fora de escopo, por decisão

- Objetivo força/performance (o enum `Goal` não tem) — é lacuna de produto, fatia própria.
- Deltoide por cabeça (anterior/lateral/posterior) no catálogo — hoje `SHOULDERS` é um alvo só, e é
  por isso que o indireto do supino não pode ser descontado do ombro.
- `MuscleGroup` sem CALVES/TRAPEZIUS/LOWER_BACK (débito do ESTADO.md) — o v2 conviva com ele;
  Face Pull e Remada Alta continuam dependendo da V64.
- Lista principal por usuário; remoção do `is_base` (o v2 usa como +0,5 no score, não como filtro
  duro — o filtro base-first é o que hoje esvazia o pool do 2º dia).
- Carga: o motor continua sem prescrever peso. A dificuldade real é carga e ela é do usuário e do
  log de progressão (fatia J). O que o motor julga é exigência de execução.
- Simulação com `contraindications` — o dado não está em nenhum export; medir antes de M4.
- **Peso corporal e idade no laço** (Rafael, 2026-10-12: "não vamos colocar no motor por hora"). Fica de fora: carga de exercício com peso do corpo (fração do peso por exercício), força relativa (e1RM ÷ peso) para o nível efetivo, histórico de peso para LOSE_FAT, e idade ajustando o ritmo do laço (deload mais frequente, teto de subida menor). **Continua** o uso atual da idade: gate de menor, `ageVolumeFactor` e RIR conservador 60+. Vai para `debitos.md` no fechamento.

---

## 11. Reanálise — o motor como sistema de plano completo (2026-10-11) — [PROPOSTA]

> Substitui a primeira leitura do PDF "Cálculos científicos no treinamento muscular" (mesma data),
> que tinha viés de custo de implementação. Critério agora, dado pelo Rafael: **a estrutura certa
> para o propósito do app** (plano completo para qualquer usuário, com progressão de carga e
> estratégia de crescimento), independentemente do trabalho. Nada codado, nada ratificado.

### 11.1 Diagnóstico com essa lente

O motor, o atual e o v2 M1–M4, é **malha aberta**: gera uma semana e nada do que o usuário faz
volta para a prescrição. A fatia J mede progresso (e1RM), mas só para leitura. Para haver
progressão de carga e crescimento, o centro do sistema não é o gerador, é o **laço**:
prescrever → executar → medir → ajustar. O PDF é fraco como fonte (11.9), mas as peças úteis dele
são justamente as do laço (e1RM, RIR, autorregulação, dose-resposta). O v2 cobre bem a parte
estrutural e continua valendo.

### 11.2 Estrutura-alvo: quatro camadas e um laço

| camada | decide | horizonte | o que o PDF traz |
|---|---|---|---|
| **1 Estrutura** (v2 M1–M3) | split, volume por músculo, exercícios, papéis | o plano | contagem fracionada 0,5; curva √; piso MED; frequência neutra |
| **2 Periodização** | rampa de volume e de RIR semana a semana, deload | mesociclo (4–6 sem.) | √ (rampa até o alvo, não até o MRV); força satura em 4–5 séries/padrão |
| **3 Prescrição de sessão** | carga e reps de cada série | a sessão | e1RM com `r_efetiva = reps + RIR`; a inversa prescreve a carga; APRE vira ajuste entre séries |
| **4 Crescimento** | volume do próximo ciclo, foco, rotação, graduação de exigência | meses | √ (onde a próxima série rende mais); estagnação de e1RM |
| **Laço** | estado por (usuário, exercício): e1RM, confiança, última carga, contador de estagnação | contínuo | — |

Regras estruturais:

- **[INV proposto] Uma função de e1RM só**, usada pela prescrição, pelo laço e pela análise (fatia
  J). Com `r_efetiva`, onda de RIR e deload deixam de distorcer o gráfico de 1RM. Por isso o
  "[INV] RIR fixo no mesociclo" da primeira leitura deixa de ser necessário.
- **[INV proposto] Uma contabilidade de volume só** (fracionada), usada pelo motor, pelo laço e pela
  análise. Sem ela, depois do M1 a tela de progresso mostra tríceps "abaixo" do que o motor
  considera atendido.
- **Três modos de progressão**, escolhidos pela propriedade do exercício e não pelo usuário:
  - *carga calculada* (composto com carga externa, `r_efetiva ≤ 12`): carga = e1RM ÷ fator(reps
    alvo + RIR alvo), arredondada ao passo do equipamento;
  - *progressão dupla* (isolamento, máquina de passo grosso, `r_efetiva > 12`): faixa [mín, máx] no
    RIR alvo. Bateu o máximo em todas as séries → sobe um passo; abaixo do mínimo → mantém ou desce;
  - *peso corporal*: reps → variação mais difícil (grafo, F5) → carga adicional.
- **O APRE vira consequência, não tabela.** A cada série o e1RM do dia é recalculado e a carga da
  próxima sai da mesma inversa, com amortecimento (teto de subida por série e por sessão). As
  tabelas em kg do PDF são uma discretização disso e quebram em halter leve (+2,5 kg num halter de
  8 kg = +31%). Isso também cobre a "autorregulação diária" que o PDF atribui ao VBT, sem sensor.
- **Na extrapolação entre faixas, vale a projeção conservadora** (regra do próprio PDF para
  dispersão > 5%). Prescrever 5 reps a partir de uma série de 12: Epley dá 1,20× a carga, Brzycki
  1,28× (diferença de 6,7%) → usa a menor. Dado próximo da faixa alvo pesa mais.
- **O e1RM fica atrás de uma interface**, com Epley como implementação padrão. Escolher a fórmula
  por população (KLW para idoso, por exemplo) passa a ser trocar uma implementação, não reescrever
  o laço.
- **Calibração:** a primeira exposição a um exercício não tem carga prescrita. O app pede "a carga
  em que você faria X reps deixando N no tanque" e o laço aprende dali.
- **Onde roda:** Kotlin puro em commonMain, porque precisa rodar offline entre séries. O servidor
  recalcula o estado ao sincronizar e é a autoridade (mesmo modelo do XP: cópia otimista no cliente).
- **[PROPOSTA] O mesociclo avança por conclusão, não por calendário.** Quem perde uma semana não
  pula etapa da progressão. A semana de calendário (ARCH #22, `started_at`) continua para agenda e
  análise: são dois relógios com papéis diferentes, e o de progressão não usa o relógio do cliente.

### 11.3 Fundação de dados que a estrutura exige

| # | o quê | por quê |
|---|---|---|
| **F1** | prescrição persistida: faixa [mín, máx], RIR alvo e carga prescrita, no contrato e em `session_set_logs` | hoje `midReps("6-10") = 8` destrói a faixa e o log só tem `target_reps`; sem isso não existe laço |
| **F2** | RIR real opcional por série (ou só na última) e marca de deload na sessão | `r_efetiva` precisa do RIR; sessão de deload não pode aparecer como queda |
| **F3** | `user_exercise_state` no servidor, com cópia otimista no cliente | o estado do laço |
| **F4** | exigência técnica 1–4 por exercício (C1) | separa "difícil de executar" de "nível do usuário"; é a base da graduação (11.5) |
| **F5** | grafo de exercícios com arestas mais fácil / mais difícil / equivalente | uma estrutura atende quatro usos (a limitação corporal saiu, §11.13): troca por dor, slot sem candidato, progressão caseira de peso corporal e graduação do iniciante |
| **F6** | sinergistas dinâmicos por exercício (0,5), em vez de por músculo-alvo | supino fechado e crucifixo não dão o mesmo tríceps; o mapa CHEST→TRICEPS do P4 é aproximação |
| **F7** | taxonomia completa: deltoide por cabeça, trapézio, lombar | sem isso a contagem de Pelland não fecha no ombro (supino → deltoide anterior 0,5) e os desvios da Q2 continuam |
| **F8** | catálogo: abdômen com mídia, 3º e 4º de panturrilha, lista caseira | sem isso CORE fica em 0% e CASA não fecha em motor nenhum |

### 11.4 Objetivo como estratégia, não como `if`

`Goal` vira um perfil de estratégia em dado: fator de volume, faixas e RIR por papel, esquema de
mesociclo e modo de progressão preferido.

- **STRENGTH entra** (a lacuna do §10 vira escopo): 4–5 séries/semana por padrão nos levantamentos
  principais (onde o PDF diz que a força satura), faixas de 3–6 e RIR 1–3. Parar longe da falha dá a
  mesma força com menos fadiga, que é a conclusão do PDF sobre perda de velocidade. O resto do
  orçamento vai para hipertrofia.
- GAIN_MUSCLE, LOSE_FAT, MAINTAIN e GENERAL_HEALTH mantêm a Q7, agora como dados do perfil de
  estratégia.

### 11.5 Crescimento pessoal (camada 4)

- **Volume do próximo ciclo pela resposta:** o músculo cujos exercícios subiram de e1RM no alvo de
  RIR ganha +1–2 séries (até o alvo ou o MRV); estagnado com reps caindo, mantém ou reduz. A curva √
  diz onde a série extra rende mais.
- **Foco vira bloco de especialização:** 1–2 músculos acima do alvo num mesociclo, os outros perto
  da manutenção, com rotação entre ciclos. É o jeito de crescer um músculo além do que o orçamento
  uniforme permite. A Q8 vira regra de ciclo, e o custo medido (−1 a −2,5% por músculo) é o que a
  justifica.
- **Rotação guiada por dado:** a âncora fica enquanto progride; estagnou um ciclo inteiro → troca
  pela equivalente do grafo. Acessórios rodam a cada ciclo.
- **Nível efetivo:** o nível declarado no quiz mais a evidência do histórico (exposições, aderência,
  carga relativa ao `weightKg`). Gradua a exigência permitida (guiado → livre) com o tempo; é o
  crescimento que o usuário enxerga.

### 11.6 Os conflitos C1–C3 sob essa lente

- **C1 → exigência técnica.** O "nível do exercício" que o Rafael quis manter (2026-10-10) passa a
  medir exigência técnica (1–4): o conceito fica e o eixo é corrigido. `profile.level` segue
  mandando em volume, prescrição e foco, e vira o ponto de partida do nível efetivo.
- **C2 → `is_base` sem efeito na geração.** A lista principal é a curadoria; `is_base` fica
  redundante. Vira débito de remoção.
- **C3 → regra geral, sem exceção.** Alvo sem candidato sai do plano, o volume é realocado e o motivo
  é informado. O abdômen entra pelo catálogo (F8), não por exceção no `SlotFiller`.

### 11.7 O que muda em relação ao que foi dito antes, no mesmo dia

| item | antes (com viés de custo) | agora |
|---|---|---|
| M5 | cortar do v2 | núcleo do v2 (camadas 2 e 3) |
| Q9 — sugerir carga | aberta | sim, depois da calibração (inverte a §10) |
| Q10 — piso MED | cortar | entra (P1) |
| Q8 — foco | opção (b) | regra de ciclo (bloco de especialização) + aviso de capacidade |
| substituição guiada e limitação | adiar | entra pelo grafo (F5) |
| "RIR fixo no mesociclo" | [INV] | desnecessário, com `r_efetiva` e a marca de deload |
| deltoide por cabeça, sinergista por exercício | fora de escopo | fundação (F6, F7) |
| fórmula de e1RM | manter Epley | Epley atrás de interface, com `r_efetiva` e projeção conservadora entre faixas |

### 11.8 Ordem proposta

**F** (fundação F1–F8; parte é curadoria do Rafael) → **M1–M3** (com piso MED, exigência técnica e
regra geral de alvo vazio) → **M4** (falha nomeada, aviso de capacidade, substituição pelo grafo) →
**P1** prescrição persistida, RIR e deload (F1, F2) → **P2** motor de progressão em commonMain (F3,
os três modos, calibração, ajuste entre séries) → **P3** mesociclo por objetivo e STRENGTH → **P4**
crescimento entre ciclos → **J2** análise migra para `r_efetiva` e contabilidade única.

**Teste estrutural do laço:** um **atleta sintético** cujo 1RM real cresce pela curva √ do PDF, com
ruído diário, simulado por 12–24 semanas. O teste afirma que a prescrição acompanha o 1RM real
(erro limitado), que nenhuma subida passa do teto, que o deload dispara e que o laço não diverge. É
o PDF usado como simulador, não como regra.

### 11.9 Números medidos que continuam valendo

- Epley sem RIR subestima o 1RM em **5% (RIR 2) e 7% (RIR 3)**.
- Epley × Brzycki × Lander diferem **≤3,9% de 1 a 12 reps**, 9% em 15 e 27% em 20.
- Incremento fixo: **+2,5 kg = +31%** num halter de 8 kg.
- Q8 (toy, só o P1, foco BACK, capacidade 69): a reserva dá **+8% no foco e −1 a −2,5%** em cada
  outro músculo.
- A curva √ reproduz a tabela de eficiência do próprio PDF (14→18 séries rende 70% do que 6→10
  rende; 26→30, 53%).
- Fonte: texto gerado, cinco números em branco, contradição VBT × APRE, Pelland via blog. **Nada
  vira [REGRA] citando o PDF**; cita-se o artigo primário.

### 11.10 Decisões pedidas ao Rafael

Substituem as Q9–Q11 da primeira leitura e fecham C1–C3 se aprovadas.

- **Q12** — quatro camadas mais o laço como arquitetura do motor (vira a ARCH #38, ampliando
  "orçamento de tempo").
- **Q13** — o motor prescreve carga depois da calibração (inverte a §10).
- **Q14** — RIR real opcional no log (atrito de UI: um toque na última série).
- **Q15** — o mesociclo avança por conclusão, não por calendário.
- **Q16** — STRENGTH entra no `Goal`.
- **Q17** — C1, C2 e C3 como em 11.6.
- **Q18** — fundação (F1–F8) antes do M1, ou M1–M3 primeiro com o mapa por músculo-alvo e migração
  depois. Recomendação: F1, F4 e F7 antes, porque mudam o formato do dado que o motor lê; F5 e F6
  podem chegar junto com o M4 e o P2.
- Q10 (piso MED) e Q11 (contabilidade única) passam a ser recomendação firme.

### 11.11 Escala de esforço por série — ideia do Rafael (2026-10-11) — [PROPOSTA]

Depois de cada série o app pergunta como foi o esforço: **Leve · Moderado leve · Moderado · Moderado
alto · Difícil**. É a interface do **F2** (RIR real) e responde à Q14. É também a escala RPE-RIR
(Zourdos) que o PDF descreve, em linguagem de usuário.

**Ajuste necessário: cada rótulo precisa de uma âncora em repetições.** Sem âncora, "moderado" é RIR
4 para um usuário e RIR 1 para outro, e a resposta não converte em carga. Com âncora, a resposta vira
estimativa:

| rótulo | o que a tela diz | RIR usado | RPE |
|---|---|---|---|
| Leve | "daria para fazer mais 5 ou mais" | 5 | ≤5 |
| Moderado leve | "mais 3 ou 4" | 3,5 | 6–7 |
| Moderado | "mais 2" | 2 | 8 |
| Moderado alto | "mais 1" | 1 | 9 |
| Difícil | "nenhuma, foi no limite" | 0 | 10 |

A resolução fica onde a autopercepção é confiável (RIR 0–3, segundo o PDF) e tudo acima de 4
cai num balde só.

**Onde entra:**
- **Camada 3, entre séries:** recalcula o e1RM do dia e sugere a carga da próxima série. Exemplo:
  supino 3×8 a 60 kg, RIR alvo 2 (e1RM implícito 80). Série 1 com 8 reps e "Leve" → `r_efetiva` 13
  → e1RM 86 → a próxima sairia 64,5 kg; com o teto de +5%, sugere **62,5 kg**. Mesma série com
  "Difícil" → e1RM 76 → sugere **57,5 kg**. É sempre sugestão; quem decide é o usuário.
- **Laço (F3):** atualiza o estado do exercício. A próxima sessão já nasce com a carga ajustada.
- **Camadas 2 e 4:** a mesma carga ficando mais difícil em 2–3 sessões seguidas é sinal de fadiga
  (antecipa o deload); ficando mais leve, é sinal para progredir.
- **Análise (J2):** o gráfico de 1RM passa a usar `r_efetiva`, o que remove o viés de 5–7%.
- **Isolamento e reps altas:** a percepção é pior nessa faixa, então a resposta alimenta a
  progressão dupla (Difícil no topo da faixa → mantém; Leve no topo → sobe um passo), não o e1RM.

**Regras de dado:**
- **Guardar a resposta crua** (o enum), não o RIR derivado. O mapa rótulo→RIR pode ser recalibrado
  depois sem perder histórico.
- **Sem resposta:** reps feitas ≥ alvo → assume o RIR alvo (conservador); reps < alvo → RIR 0.
  Marca confiança menor.
- **Calibração da percepção [PROPOSTA]:** uma vez por mesociclo, a última série de um composto vira
  série máxima com boa técnica. A diferença entre o previsto e o feito dá um ajuste de percepção por
  usuário.

**UX:** chips de um toque na tela do cronômetro de descanso (o usuário já está parado ali),
**opcional e sem pré-seleção**, porque pré-selecionar o esforço planejado ancora a resposta. Rótulos
em pt/en (regras/13).

**Contrato e migration:** `effort` nullable no DTO de log de série e coluna em `session_set_logs`
(V66+, depois das V64/V65 da lista principal). Ordem: Backend → Contrato → Cliente.

**Ordem:** entra no P1, que **pode sair antes do M1**. Histórico de esforço que não for coletado
agora não existe depois, e o J2 já mostra benefício (1RM corrigido) antes do motor de progressão.

### 11.12 Dia ruim e dia corrido — o laço não pode ser uma escada que só sobe (2026-10-11) — [PROPOSTA]

Pergunta do Rafael: na semana passada o usuário fez 100 → 110 → 120 kg; nesta semana o plano sugere
a mesma faixa, mas ele está num dia difícil e corrido e não sustenta nem os 100 kg. O que acontece?

**Princípio:** o app prescreve **esforço** (reps + RIR) e **sugere** carga. A carga sugerida é ponto
de partida, não meta. Num dia ruim a mesma prescrição de esforço cabe numa carga menor, e isso é
treino certo, não fracasso.

**Duas forças no estado (refina o F3):**
- **Força de base:** o e1RM suavizado do exercício, que define a sugestão de abertura da sessão.
- **Força do dia:** recalculada a cada série (reps + esforço), que define a sugestão da próxima série.

**Durante a sessão (camada 3):** a primeira série de trabalho é a sonda do dia. Exemplo, supondo a
série de topo da semana passada em 120 kg × 8 "Moderado" (e1RM base 160): hoje, 100 kg × 6
"Difícil" → força do dia 120 (−25%). A próxima série sai em **90 kg** (o que dá 8 reps deixando 2),
dentro do teto de queda por série (−10%), arredondada ao passo do equipamento. A sessão continua; o
plano não quebra.

**Ao fechar a sessão (laço), a atualização é assimétrica:**
- desvio > −5% → atualização normal da base;
- desvio ≤ −5% → **dia ruim**: a base mexe pouco (exemplo: 160 → ~156) e um contador sobe. Na
  semana seguinte a sugestão volta perto do normal, não em 90 kg;
- **2–3 sessões ruins seguidas**, ou a mesma carga ficando mais difícil em sequência → não é dia,
  é tendência: a base desce de verdade e o deload é antecipado (camada 2).
- **Queda em quase todos os exercícios da sessão** → causa sistêmica (sono, estresse): a sessão é
  marcada e nenhum exercício é penalizado individualmente. **Queda num exercício só** → causa
  local (técnica, desconforto): o contador é daquele exercício.

Limiares (−5%, teto de −10% por série, peso da suavização) são parâmetros a calibrar no teste do
atleta sintético (11.8), não constantes ratificadas.

**Check-in antes do treino — DECIDIDO: não entra** (Rafael, 2026-10-11). Consequências:
- O dia é detectado **só pela primeira série de trabalho** (a sonda acima), que já funciona sem
  pergunta nenhuma.
- A **sessão expressa** caiu junto, porque dependia de "quanto tempo você tem hoje?". Num dia
  corrido o usuário pula séries ou exercícios; o volume que faltou não é empurrado para a próxima
  sessão e não há penalidade.
- **Não proposto:** inferir os minutos reais pela duração da sessão. `started_at`/`finished_at` vêm
  do relógio do cliente, e a regra do projeto proíbe usá-lo para lógica.
- **Série de abertura conservadora (DECIDIDO, ver abaixo):** sem check-in, a primeira série de trabalho de cada
  exercício abre com RIR alvo +1 (uma rep a mais no tanque) e as seguintes sobem pela força do dia.
  Num dia bom a carga sobe na série 2; num dia ruim ninguém falha logo de cara com a carga cheia. É
  o que o usuário já faz por conta própria ao rampar 100 → 110 → 120. Custo: uma série por exercício
  um pouco abaixo do esforço alvo.

**"Senti dor" — DECIDIDO: entra** (Rafael, 2026-10-11). É um botão separado da escala de esforço,
porque dor não é dia ruim. O objetivo, nas palavras do Rafael, é **auxiliar o usuário**: orientar a
procurar um instrutor ou profissional, ou trocar o exercício, porque a dor pode vir de execução
errada.

Fluxo [PROPOSTA]:
1. O botão fica na tela do exercício. Ao tocar, o exercício é interrompido (as séries restantes
   deixam de ser cobradas) e a série **não entra como dado de desempenho** (nem e1RM, nem estado do
   laço).
2. Três saídas:
   - **Rever a execução:** abre a mídia e a descrição do exercício, já que a causa pode ser técnica.
   - **Trocar o exercício:** sugere o equivalente **mais guiado** pelo grafo (F5), por exemplo livre
     → máquina, porque menos exigência técnica reduz o risco de erro de execução. Até o F5 existir,
     usa a troca manual que já existe (`alternatives`).
   - **Orientação, sempre visível:** "Se a dor continuar, fale com o instrutor da academia ou com um
     profissional de saúde." Em CASA, onde não há instrutor: "profissional de educação física ou de
     saúde".
3. **Recorrência — [REGRA] do Rafael (2026-10-12): "se a dor persistir mesmo fazendo o exercício
   correto, o melhor é trocar de exercício. O app prioriza a saúde."** Na 2ª ocorrência no mesmo
   exercício, a troca deixa de ser uma opção entre três e vira a **ação principal**:
   - o exercício sai do programa (com confirmação do usuário) e entra numa lista de **exercícios
     evitados por usuário**, que o motor respeita nas próximas gerações. A lista guarda só o exercício, sem o motivo;
   - o substituto vem do grafo (F5) com preferência por **outro padrão de carga** (livre → máquina,
     ou outro ângulo): se a execução já estava correta, um equivalente idêntico tende a doer igual;
   - o substituto **evita a mesma articulação**: as `contraindications` do exercício que doeu
     (ombro, joelho, lombar, punho, impacto) excluem candidatos com a mesma contraindicação. É o
     que substitui o filtro por limitação declarada (removido, §11.13): sem isso, trocar o
     agachamento livre pode cair no leg press e doer no mesmo joelho;
   - a orientação para procurar um profissional de saúde fica mais firme;
   - dor também no substituto → mesma orientação firme, sem nova rodada de trocas automáticas.
4. **Nunca** diagnostica e **nunca** sugere continuar com dor. O texto passa por revisão.

**Dado de saúde:**
- No fechamento, o relato de dor entra na lista de "nunca exposto" do [INV] de
  `regras/14-privacidade-e-seguranca.md`, ao lado do PAR-Q (limitações saem da lista, §11.13).
- **Armazenamento — DECIDIDO: opção (b), só online** (Rafael, 2026-10-12). A marca de dor nunca
  vai para o SQLDelight. Desenho [PROPOSTA]:
  - **Online:** o relato vai direto ao servidor por um endpoint próprio, **fora da outbox**. Ele
    referencia o id da sessão gerado no cliente e o exercício, mas não depende de a sessão já ter
    chegado (ela pode estar parada na outbox). Data do relato = relógio do servidor.
  - **Offline:** orientação, revisão da execução e troca de exercício funcionam normalmente. O
    relato fica **só em memória** durante a sessão e é enviado se a conexão voltar antes de o app
    fechar; se o app fechar, perde-se. Consequência aceita: dor relatada offline pode não contar
    para a recorrência.
  - **O que vai na outbox é neutro:** as séries não feitas saem como `done = false`, sem motivo. É
    por isso que elas não entram no desempenho, sem que a palavra "dor" toque o banco local.
  - A contagem de recorrência é do servidor.

**Série de abertura conservadora — DECIDIDO** (Rafael, 2026-10-11): a primeira série de trabalho
abre com RIR alvo +1 e as seguintes seguem a força do dia.

**Produto:** sessão ajustada conta igual para sequência e XP. Mensagem do tipo "Dia pesado acontece.
Ajustamos a carga de hoje; seu plano continua." A análise (J2) mostra o ponto real, e a linha de
tendência é a força de base, para um dia ruim não parecer regressão.

**Dados novos:** marca de sessão ajustada e o motivo, e a marca de dor por exercício (forma de guardar: ver o conflito acima).
Somam-se ao F2.

### 11.13 Limitações articulares — REMOVIDAS por completo (Rafael, 2026-10-12)

Decisão: opção (c). Sai a pergunta do onboarding, o campo do perfil, o filtro do motor e da troca,
e **os dados já declarados** ("ainda estamos em testes"). O PAR-Q (`health_screening`, gate de
saúde) **fica**: a decisão foi sobre articulações.

**O que sai:**
- `ProfileDto.limitations` e o enum `BodyLimitation` do contrato; tela do onboarding, strings pt/en.
- `profiles.limitations` (coluna). **Migration destrutiva:** antes de rodar, medir no banco quantos
  perfis têm limitação declarada e mostrar o número (regra do projeto). Consulta:
  `SELECT count(*) FROM profiles WHERE limitations IS NOT NULL AND limitations NOT IN ('', '[]');`
  **Medido em 2026-10-12 no banco de dev: 0 perfis** com limitação declarada (a coluna sai vazia).
  O onboarding continua perguntando até a tela sair, então **medir de novo logo antes de aplicar**.
- Parâmetro `limitations` de `ExercisePreFilter.poolFor`, `ExerciseService.alternatives` e da rota
  de alternativas.
- Na documentação, no fechamento: `regras/01` (pipeline "ambiente + nível + limitações"),
  `regras/14` ([INV] "PAR-Q, limitações" → só PAR-Q), §4 e §7 deste plano (já marcados OBSOLETO).

**O que fica, e por quê:**
- `exercises.contraindications` (dado de **catálogo**, não do usuário). Passa a ter um uso só: na
  troca por dor, evitar um substituto que force a mesma articulação (§11.12). É o que cobre o risco
  de "trocar resolve um exercício, não a articulação".

**Efeitos no motor:** pool maior e geração mais simples; a matriz de cobertura com
`contraindications` antes do M4 deixa de ser necessária; o F5 (grafo) perde o uso "limitação
corporal" e mantém dor, slot sem candidato, progressão caseira e graduação.

**Risco aceito:** quem já sabe que tem um problema articular não tem como avisar o app antes de
sentir dor. A proteção passa a ser reativa (botão de dor → troca que evita a mesma articulação →
orientação profissional).

**Quando:** junto com o M1, que já mexe em `ProfileDto` e `profiles` (`session_minutes`): uma
migration de perfil só, um ajuste de contrato só.

---

## 12. Modelo lógico, modelo relacional e fórmulas (2026-10-12) — [PROPOSTA]

> Legenda de status: **D** = decidido · **P(Qx)** = proposta que depende da decisão Qx do §0 ·
> **C** = constante a calibrar no atleta sintético. Nada aqui foi migrado.

### 12.1 Modelo lógico

**Entidades que já existem e mudam**

| entidade | relação | muda | status |
|---|---|---|---|
| Perfil | 1:1 Usuário | + minutos por sessão; objetivo ganha FORÇA | D (Q1) · P(Q16) |
| Exercício | catálogo | + `is_principal` (lista principal); exigência técnica 1–4 **derivada em código** (equipamento + exceção), não coluna | D · P(C1) |
| Programa | 1:N Treino · **1:N Mesociclo** (novo) | passa a ter ciclos | D (Q4) |
| Exercício do treino | N:1 Treino | + faixa de reps (mín, máx) no lugar do `midReps` | P(F1) |
| Sessão | N:1 Usuário | + ciclo e semana do ciclo (snapshot), semana leve, marca de dia ajustado | P(F2) |
| Série executada | N:1 Sessão | + faixa e RIR alvo, carga sugerida, esforço respondido, tipo (trabalho/aquecimento) | P(F1, F2) |

**Entidades novas**

| entidade | relação | atributos | para quê | status |
|---|---|---|---|---|
| **Mesociclo** | N:1 Programa | ordem, semanas planejadas, semana atual, semana de deload, músculos em especialização, seed, situação | camada 2: rampa, deload, rotação | D (Q4) · P(Q15, Q8) |
| **Estado do exercício** | N:1 Usuário, N:1 Exercício (chave = os dois) | força de base (e1RM), confiança, última carga/reps/esforço, sequência de sessões ruins, ciclos sem progresso, exposições | a memória do laço | P(Q12, Q13) |
| **Estado de treino do usuário** | 1:1 Usuário | nível efetivo, viés de percepção do esforço | camada 4 e calibração | P(Q12) |
| **Contribuição muscular** | N:1 Exercício | músculo, fator (1,0 primário · 0,5 sinergista) | contagem fracionada por exercício | P(F6) |
| **Relação entre exercícios** | Exercício N:N Exercício | tipo: PROGRESSÃO (de → mais difícil) ou EQUIVALENTE | grafo: dor, falta de candidato, progressão caseira | P(F5) |
| **Relato de dor** | N:1 Usuário | sessão (id do cliente, sem FK), exercício, data do servidor | recorrência → troca. **Dado de saúde** | D (§11.12) |
| **Exercício evitado** | Usuário N:N Exercício | data; **sem motivo** | o motor não escolhe mais | D (§11.12) |

"Mais fácil" não vira tipo próprio: é a aresta PROGRESSÃO lida ao contrário. EQUIVALENTE é simétrica
e é gravada uma vez só.

**Enums em código** (não são tabela): esforço (LEVE, MODERADO_LEVE, MODERADO, MODERADO_ALTO,
DIFICIL); tipo de série (TRABALHO, AQUECIMENTO); marca do dia (NORMAL, AJUSTADA); músculo-alvo
completo [P(F7)]: CHEST, BACK, DELT_FRONT, DELT_SIDE, DELT_REAR, BICEPS, TRICEPS, FOREARMS, QUADS,
POSTERIOR, GLUTES, CALVES, CORE, TRAPEZIUS, LOWER_BACK; passo de carga por equipamento [C].

### 12.2 Modelo relacional (Postgres, servidor)

Números V66+ (V64/V65 já são da lista principal); a ordem real sai na fatia. Enums em
`VARCHAR`, como o resto do schema.

**Alterações**

```sql
-- profiles                                                     D (Q1)
ALTER TABLE profiles ADD COLUMN session_minutes SMALLINT;            -- nulo = 60 no servidor

-- workout_exercises                                            P(F1)
ALTER TABLE workout_exercises ADD COLUMN rep_min SMALLINT;           -- nulo em treino MANUAL
ALTER TABLE workout_exercises ADD COLUMN rep_max SMALLINT;           -- (rir e rest_seconds já existem)

-- workout_sessions                                             P(F2, Q4)
ALTER TABLE workout_sessions ADD COLUMN mesocycle_id   UUID;         -- snapshot, sem FK (como program_id)
ALTER TABLE workout_sessions ADD COLUMN mesocycle_week SMALLINT;
ALTER TABLE workout_sessions ADD COLUMN is_deload      BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE workout_sessions ADD COLUMN day_flag       VARCHAR(16);  -- calculado pelo servidor

-- session_set_logs                                             P(F1, F2)
ALTER TABLE session_set_logs ADD COLUMN target_rep_min       SMALLINT;
ALTER TABLE session_set_logs ADD COLUMN target_rep_max       SMALLINT;
ALTER TABLE session_set_logs ADD COLUMN target_rir           SMALLINT;   -- já com o +1 da série de abertura
ALTER TABLE session_set_logs ADD COLUMN prescribed_weight_kg DOUBLE PRECISION;
ALTER TABLE session_set_logs ADD COLUMN effort               VARCHAR(16); -- resposta crua; nulo = não respondeu
ALTER TABLE session_set_logs ADD COLUMN set_type             VARCHAR(12) NOT NULL DEFAULT 'TRABALHO';

-- exercises: is_principal vem da V65 (lista principal). Sem coluna de exigência técnica (código).
```

**Tabelas novas**

```sql
CREATE TABLE mesocycles (                                     -- D (Q4) · P(Q15)
    id              UUID PRIMARY KEY,
    program_id      UUID NOT NULL REFERENCES programs(id) ON DELETE CASCADE,
    ordinal         SMALLINT NOT NULL,                        -- 1º, 2º... ciclo do programa
    weeks_planned   SMALLINT NOT NULL,                        -- 4–6
    current_week    SMALLINT NOT NULL DEFAULT 1,              -- avança por conclusão (Q15)
    deload_week     SMALLINT,
    specialization  TEXT,                                     -- músculos em especialização (Q8)
    seed            BIGINT NOT NULL,
    status          VARCHAR(12) NOT NULL DEFAULT 'ATIVO',     -- ATIVO | CONCLUIDO
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (program_id, ordinal)
);

CREATE TABLE user_exercise_state (                            -- P(Q12, Q13)
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    exercise_id         UUID NOT NULL REFERENCES exercises(id),
    e1rm_base           DOUBLE PRECISION,                     -- nulo até calibrar
    confidence          REAL NOT NULL DEFAULT 0,              -- 0..1
    last_weight_kg      DOUBLE PRECISION,
    last_reps           SMALLINT,
    last_effort         VARCHAR(16),
    bad_session_streak  SMALLINT NOT NULL DEFAULT 0,
    stalled_cycles      SMALLINT NOT NULL DEFAULT 0,
    exposures           INT NOT NULL DEFAULT 0,
    version             INT NOT NULL DEFAULT 0,               -- concorrência otimista
    updated_at          TIMESTAMP NOT NULL DEFAULT now(),     -- relógio do servidor
    PRIMARY KEY (user_id, exercise_id)
);

CREATE TABLE user_training_state (                            -- P(Q12)
    user_id          UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    effective_level  VARCHAR(16),
    effort_bias      REAL NOT NULL DEFAULT 0,                 -- correção de percepção (reps)
    updated_at       TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE exercise_muscle_contributions (                  -- P(F6)
    exercise_id  UUID NOT NULL REFERENCES exercises(id) ON DELETE CASCADE,
    muscle       VARCHAR(16) NOT NULL,
    factor       REAL NOT NULL CHECK (factor > 0 AND factor <= 1),
    PRIMARY KEY (exercise_id, muscle)
);

CREATE TABLE exercise_relations (                             -- P(F5)
    from_id  UUID NOT NULL REFERENCES exercises(id) ON DELETE CASCADE,
    to_id    UUID NOT NULL REFERENCES exercises(id) ON DELETE CASCADE,
    kind     VARCHAR(12) NOT NULL,                            -- PROGRESSAO | EQUIVALENTE
    PRIMARY KEY (from_id, to_id, kind),
    CHECK (from_id <> to_id),
    CHECK (kind <> 'EQUIVALENTE' OR from_id < to_id)          -- simétrica gravada uma vez
);

CREATE TABLE pain_reports (                                   -- D (§11.12) — dado de saúde
    id           UUID PRIMARY KEY,
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    session_id   UUID NOT NULL,                               -- id do cliente, SEM FK: a sessão pode estar na outbox
    exercise_id  UUID NOT NULL REFERENCES exercises(id),
    created_at   TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_pain_user_exercise ON pain_reports (user_id, exercise_id);

CREATE TABLE user_avoided_exercises (                         -- D (§11.12)
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    exercise_id  UUID NOT NULL REFERENCES exercises(id),
    created_at   TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, exercise_id)
);
```

Notas:
- **Dedup de catálogo** (como V25/V63) passa a ter de mesclar `user_exercise_state`, `pain_reports`,
  `user_avoided_exercises` e as duas tabelas de grafo/contribuição antes de apagar o duplicado.
- **Cliente (SQLDelight):** recebe `user_exercise_state` como cópia otimista (cache) e as colunas
  novas do log entram no payload da sessão. `pain_reports` **nunca** vai para o banco local (§11.12).
- `pain_reports` entra na lista de "nunca exposto" de `regras/14`.

### 12.3 Fórmulas — o que calcula, para quê, onde

**Estrutura do plano (camada 1)**

| # | fórmula | para quê | status |
|---|---|---|---|
| E1 | `custo(papel) = (t_trabalho + t_descanso) / 60` (min por série) | quanto tempo cada série consome | D · C |
| E2 | `capacidade = dias × (minutos − aquecimento − 5 × overhead) / Σ mix(papel) × custo(papel)` | quantas séries por semana cabem (ex.: 4 dias × 60 min ≈ 92) | D · C |
| E3 | `alvo[m] = min(base[m, nível] × f_objetivo × f_idade + 3·foco[m], MRV[m])` | séries semanais desejadas por músculo | D (Q7) |
| E4 | `piso[m] = min(MEV[m], alvo[m])`; com a Q10, antes um degrau de 3 séries | mínimo por músculo antes de dividir a sobra | D · P(Q10) |
| E5 | `plano[m] += sobra × w[m] · (1,8 se foco) / Σ w`, teto em `alvo[m]` | dividir a sobra por prioridade | D · C · P(Q8) |
| E6 | `Vfrac[m] = Σ séries × fator(exercício, m)`; fator 1,0 primário, 0,5 sinergista (0,2 glúteo→posterior) | volume real por músculo (Pelland) — motor, análise e crescimento usam a mesma conta | D (Q2) · P(Q11, F6) |
| E7 | `alvoDireto[m] = max(3, alvo[m] − indireto[m])` | quanto treinar direto depois de descontar o indireto | D (Q2) |
| E8 | `score = foco + papel × exigência + base − já usado em outro dia − padrão repetido (só compostos) + jitter` | escolher o exercício do espaço | D · P(C1, C2) |

**Esforço e força (camadas 3 e laço)**

| # | fórmula | para quê | status |
|---|---|---|---|
| F1 | esforço → RIR: Leve 5 · Moderado leve 3,5 · Moderado 2 · Moderado alto 1 · Difícil 0 (− viés do usuário); `RPE = 10 − RIR` | transformar a resposta em número | P (frases pendentes) |
| F2 | `r_ef = reps_feitas + RIR` | reps que a pessoa aguentaria até a falha | P(Q13) |
| F3 | `e1RM = carga × (1 + r_ef / 30)` (Epley); `r_ef = 1` → `e1RM = carga` | força estimada — **uma função só** para prescrição, laço e gráfico | P(Q13) |
| F4 | `carga = e1RM / (1 + (reps_alvo + RIR_alvo) / 30)`, arredondada ao passo do equipamento; série de abertura usa `RIR_alvo + 1` | carga sugerida | P(Q13) · D (abertura) |
| F5 | entre faixas distantes: `carga = min(F4, e1RM_brz × (37 − reps_alvo) / 36)`, com `e1RM_brz = carga × 36 / (37 − r)` | projeção conservadora (ex.: 60 kg × 12 → 5 reps: Epley 72, Brzycki 76,8 → **72**) | P(Q13) |
| F6 | `próxima = clamp(F4(força_do_dia), carga × 0,90, carga × 1,05)` | ajuste entre séries (o APRE em forma contínua) | P(Q13) · C |
| F7 | `d = (força_do_dia − base) / base`; `α = 0,5` se `d > −5%`, senão `α = 0,1` e contador +1; `base ← base + α × (força_do_dia − base)` | um dia ruim não reescreve o histórico (160 → ~156) | P · C |
| F8 | ≥ 70% dos exercícios da sessão com `d ≤ −5%` → sessão AJUSTADA, nenhum contador individual sobe | separar causa geral de causa local | P · C |
| F9 | todas as séries com `reps ≥ rep_max` no esforço alvo → `+1 passo`; alguma `< rep_min` → mantém (2ª vez: −1 passo) | progressão dupla (isolamento, máquina de passo grosso, `r_ef > 12`) | P(Q13) |
| F10 | `prescription_type = TIME`: +5 s por sessão até o teto, depois a PROGRESSÃO do grafo | progressão de isometria (prancha) | P |
| F11 | `viés = reps_AMRAP − 30 × (e1RM / carga − 1)`, suavizado | corrigir a percepção de esforço do usuário (1×/ciclo) | P |

**Ciclo e crescimento (camadas 2 e 4)**

| # | fórmula | para quê | status |
|---|---|---|---|
| G1 | semana de deload: `séries × 0,5`, carga mantida, `RIR_alvo + 1` | recuperar sem perder adaptação | P(Q12) · C |
| G2 | mesma carga com esforço subindo em 2–3 sessões, ou `bad_session_streak ≥ 2` → deload antecipado | fadiga acumulada | P · C |
| G3 | `Δciclo = (base_fim − base_início) / base_início < 1%` → `stalled_cycles + 1` → troca pela EQUIVALENTE | rotação guiada por dado | P(Q12) · C |
| G4 | músculo com `Δciclo > 0` no esforço alvo → `+1 a 2 séries` no próximo ciclo (teto alvo/MRV); estagnado com esforço subindo → mantém ou −1 | volume cresce onde responde | P(Q12) |
| G5 | `estímulo[m] = √(Vfrac[m] / alvo[m])`; ganho marginal ∝ `1 / (2√V)` | onde a série extra rende mais; métrica da golden matrix | P |

**Análise (J2):** gráfico de 1RM = F3 com `r_ef`; volume por grupo = E6; linha de tendência = base
de F7. Uma conta só em todo o app.

**Do PDF que NÃO entra, e por quê:** Lander, Lombardi, Mayhew, Wathen, O'Connor e KLW (≤4% de
diferença até 12 reps; o laço corrige o viés; Brzycki fica só como freio em F5) · perda de
velocidade e carga-velocidade (exigem sensor) · tabelas de kg do APRE (substituídas por F6) ·
volume-carga para comparar músculos (a fatia J já proíbe).

### 12.4 Exemplo traçado — serve de fixture para os testes de lógica pura

Números calculados (script), não estimados. Ana · INTERMEDIATE · GAIN_MUSCLE · academia.

**Prescrição** (camada 1): Supino reto com barra, 3 × 5–8, RIR 2, passo 2,5 kg (carga calculada) ·
Rosca direta com halteres, 3 × 12–15, RIR 1, passo 2 kg (progressão dupla).

**Detalhes de desenho que o exemplo fixou:**
- na carga calculada, `reps_alvo = rep_max` (aqui, 8);
- a série seguinte usa o e1RM da **última** série (que já carrega a fadiga da sessão); a **força
  do dia** que atualiza a base é o **maior** e1RM das séries de trabalho;
- arredondamento ao passo mais próximo, depois do limite de −10%/+5%.

**Supino**

| sessão | série | carga × reps | esforço | r_ef | e1RM | próxima sugerida |
|---|---|---|---|---|---|---|
| 1 calibração | 1 | 50 × 8 (escolhida pela Ana) | Moderado leve | 11,5 | 69,2 | 52,5 |
| | 2 | 52,5 × 8 | Moderado | 10 | 70,0 | 52,5 |
| | 3 | 52,5 × 8 | Moderado alto | 9 | 68,3 | — |
| | | **base = 70,0** (1ª exposição) | | | | |
| 2 normal | 1 abertura (RIR 3) | 50 × 8 | Leve | 13 | 71,7 | 52,5 (limitado a +5%) |
| | 2 | 52,5 × 8 | Moderado leve | 11,5 | 72,6 | 55 |
| | 3 | 55 × 8 | Moderado | 10 | 73,3 | — |
| | | força do dia 73,3 · d = +4,8% → α 0,5 → **base 71,7** | | | | |
| 3 dia ruim | 1 abertura | 52,5 × 5 | Difícil | 5 | 61,3 | 47,5 (limitado a −10%) |
| | 2 | 47,5 × 7 | Moderado alto | 8 | 60,2 | 45 |
| | 3 | 45 × 8 | Moderado | 10 | 60,0 | — |
| | | força do dia 61,3 · d = −14,5% → α 0,1 → **base 70,6** | | | | |
| 4 | abertura | **52,5** (sem o amortecimento seria 45) | | | | |

**Rosca** (progressão dupla, F9): S1 a 10 kg faz 15/15/14 → mantém · S2 faz 15/15/15 "Moderado
alto" → **12 kg** · S3 (dia ruim) a 12 kg faz 12/11/10 → abaixo do mínimo, mas a sessão é AJUSTADA,
então não conta → mantém 12 kg.

**Dia ruim geral (F8):** na S3 os dois exercícios caíram mais de 5% → `day_flag = AJUSTADA`, e
nenhum `bad_session_streak` sobe.

**Linhas resultantes** (S3, supino):

```
session_set_logs  set  weight  reps  target_rep_min/max  target_rir  prescribed_weight  effort         set_type
                  1    52.5    5     5 / 8               3           52.5               DIFICIL        TRABALHO
                  2    47.5    7     5 / 8               2           47.5               MODERADO_ALTO  TRABALHO
                  3    45.0    8     5 / 8               2           45.0               MODERADO       TRABALHO
workout_sessions  is_deload=false  day_flag=AJUSTADA
user_exercise_state (Ana, Supino)  e1rm_base 71.7 → 70.6 · last 45×8 MODERADO · bad_session_streak 0 · exposures 3
```


### 12.5 Exemplo de 1 mês (mesociclo de 4 semanas) — achados para o desenho (2026-10-12)

Ana · INTERMEDIATE · GAIN_MUSCLE · 4 dias × 60 min · academia · 28 anos · sem foco. Exercícios da
lista principal; split Upper/Lower; tempos pelo P0 (pesado 2,5 min/série, acessório 2,08,
isolamento 1,67, aquecimento 6, 1 min por exercício). Carga do supino simulada com um atleta
sintético (1RM real 72 → +0,75% por sessão, fadiga de 3% por série): a base foi de 71,7 a 75,0
enquanto o 1RM real foi de 72,0 a 75,9.

**Rampa do mês [PROPOSTA — o desenho do ciclo não foi decidido; Q4 só disse "sim"]:**
sem. 1 introdução (−1 série por exercício, mín. 2; RIR alvo +1; calibração) · sem. 2 acumulação
(plano cheio, RIR alvo) · sem. 3 pico (plano cheio; acessório e isolamento a RIR alvo −1, pesados no
alvo) · sem. 4 deload (G1: séries × 0,5 arredondadas para cima, carga da sem. 3, RIR alvo +1).
Séries/semana: 56 → 76 → 76 → 47. Com o orçamento de tempo fixo, **a rampa é de esforço e carga,
não de séries**: a semana cheia já é a capacidade.

**Achados:**
- **Upper/Lower 4 × 60 aperta os dias de superiores** (59,4 min) e sobra nos inferiores (41–46
  min). Costas fecha em 88%, ombro 92%, bíceps 83%; com 75 min nos dias de superiores tudo fecha.
  É o aviso de capacidade do M4 na prática. A sobra dos inferiores é onde o abdômen entra quando
  o catálogo tiver (F8).
- **Glúteo:** pelo P4 sozinho teria 0 série direta (6 de 8 vêm do indireto, sobra < 2,4); a
  Elevação Pélvica entra pela ETAPA 3 (sobra de tempo). Ordem coerente: zera na escassez,
  preenche quando sobra tempo.
- **C1 aparece aqui:** com a exigência técnica, intermediário (teto 3) não recebe Agachamento
  Livre (exigência 4) e a âncora de quadríceps vira Leg Press 45°.
- **Q6 em costas:** com `n = 2` (âncora + isolamento) o dia de costas pode sair sem puxada
  vertical; no exemplo ela só aparece porque o Upper A tem `n = 3`. Vale um teste: todo programa
  com BACK ≥ 2 sessões tem ao menos uma puxada vertical.
- **Face Pull como BACK** depende da V64.

### 12.6 Volume semanal e por sessão — conferido contra fontes primárias (2026-10-12) — [PROPOSTA]

O Rafael apontou que "iniciante com 26 séries num dia" está errado. Está: aquele número era o
**alvo completo dividido pelos dias** (2 dias), não o que o motor prescreveria. Mas mesmo o que o
motor prescreveria (até ~23 séries em 60 min) é demais para quem está começando.

**Fontes primárias consultadas:**
- **ACSM 2026**, *Resistance Training Prescription for Muscle Function, Hypertrophy, and Physical
  Performance in Healthy Adults: An Overview of Reviews*, MSSE (abr/2026; 137 revisões, >30 mil
  participantes): **~10 séries por grupo muscular por semana** para hipertrofia; **todos os grandes
  grupos ≥ 2×/semana**; força 2–3 séries por exercício a ~80% de 1RM; ir à falha não mudou o
  resultado de forma consistente.
- **ACSM 2009** (Progression Models): iniciante **1–3 séries por exercício** "inicialmente", corpo
  inteiro 2–3×/semana; intermediário 1–3 séries, 3× corpo inteiro ou 4× dividido; avançado 3–6
  séries por exercício, 4–6×/semana.
- **Pelland et al. 2024** (preprint SportRxiv; 67 estudos, 2.058 participantes, 28 com destreinados):
  hipertrofia com dose mínima de **4 séries fracionadas/semana**, faixa mais eficiente 5–10,
  retorno decrescente 11–18 e 19–29, poucos estudos acima de ~25; força sem ganho detectável
  além de **~4 séries/semana**.
- **Remmert et al. 2025** (preprint SportRxiv): por sessão, hipertrofia sem superioridade
  detectável acima de **~11 séries fracionadas por músculo**; força, ~2 séries diretas do exercício.
- **Baz-Valle et al. 2022** (J Hum Kinet): **12–20 séries/semana** por grupo para treinados.

**Veredito sobre a `VolumeTable`:** os números **por músculo** batem com as fontes (iniciante 3–10 ≈
dose mínima até a faixa eficiente; intermediário 10–16 ≈ ACSM 2026; avançado 14–20 ≈ Baz-Valle;
MRV 16–25 ≈ onde Pelland tem poucos dados). O erro está na **sessão**, não na semana: nada no
desenho limita o total de séries de uma sessão de iniciante além dos minutos.

**Propostas:**
- ~~Q19 (proposta original: só o iniciante, 16)~~ — substituída pela decisão abaixo. Texto original: **teto de séries por sessão do iniciante: 16 (~45 min)**, e a semana 1 do ciclo com −1 série
  por exercício (o "inicialmente" da ACSM 2009). Intermediário e avançado continuam limitados pelos
  minutos. Com 2 dias, o iniciante recebe 32 séries/semana (68% do alvo, todos os grandes acima da
  dose mínima de 4); com 3+ dias, 100%.
- **Q19 — DECIDIDO (Rafael, 2026-10-12): teto de séries por sessão = INICIANTE 16 · INTERMEDIÁRIO 18 ·
  AVANÇADO 18** ("não precisa ser exatamente no limite [dos 23 que cabem em 60 min]; deixe em torno de
  18"). 18 séries ≈ 50 min no modelo do P0, que ignora espera por aparelho e montagem de carga: a
  folga de ~10 min é o que faz a sessão real caber em 60. Consequência medida: com 4 dias o
  intermediário recebe 90% do alvo semanal e o avançado 69%; o avançado só fecha 100% com 6 dias.
  Pela curva √, 69% das séries ≈ 83% do estímulo.
- **Q22 — DECIDIDO (Rafael, 2026-10-12): o teto de força NÃO escala com os minutos.** "Passou de 60
  minutos, dê uma opção do usuário inserir cardio ou abdômen." Regras derivadas [PROPOSTA]:
  - abaixo de 60 min manda o tempo (45 min → ~13 séries de força); a partir de 60, o teto de força
    fica 16/18/18 e os minutos a mais (75 → +15, 90 → +30) viram um **bloco extra no fim da
    sessão**, escolhido pelo usuário no quiz: **CARDIO** ou **ABDÔMEN**;
  - cardio: o catálogo já tem **25 exercícios `modality = CARDIO`** (mídia a conferir); prescrição por
    tempo (`prescription_type` TIME/DISTANCE — conferir como TIME é gravado em `workout_sets` antes
    de codar); sem progressão de cardio nesta fatia;
  - abdômen: depende do F8 (o catálogo não tem abdômen com mídia). Até lá, só CARDIO é oferecido;
  - sempre no fim, depois da força, para não roubar desempenho dos exercícios principais;
  - dado novo: `profiles.extra_block VARCHAR(12)` (CARDIO | CORE | nulo) e o passo no quiz.
- **Q23 — ABERTA: o abdômen sai da distribuição de força e passa a viver só no bloco extra?** Resolve
  o C3 (CORE sem candidato) e libera orçamento para os músculos principais; custo: quem treina 60
  min ou menos não recebe abdômen.
- **Q24 — DECIDIDO (Rafael, 2026-10-12): abdômen é prescrito em séries × repetições, não em tempo.**
  Consequências [PROPOSTA]: o volume do bloco vem do alvo semanal de CORE (5/8/10 por nível)
  dividido pelas sessões (~2–4 séries por treino), e **os minutos extras não são preenchidos com mais
  abdômen**; o que sobrar de tempo, o treino termina antes. Isometria (prancha, `prescription_type =
  TIME`) continua em séries × segundos. Abdômen entra na progressão dupla (F9) como qualquer
  isolamento.
- **Q20 — DECIDIDO (Rafael, 2026-10-12: "sim"), como rede de segurança e constante a recalibrar — teto por músculo por sessão: 11 séries fracionadas** (Remmert 2025), no lugar dos 15
  implícitos (3 exercícios × 5 séries). Acima disso o motor aumenta a frequência em vez de engordar a
  sessão.
  *Recomendação de 2026-10-12:* aprovar como **rede de segurança**, na mesma conta fracionada do E6.
  Com o teto de 18 séries por sessão (Q19) e a Q21, ela só atua num caso: avançado com foco num
  dia que concentra o músculo (ex.: dia Pull do PPL com foco em costas, 23 ÷ 2 = 11,5 → 11). Em
  iniciante e intermediário a 2×/semana o máximo é 5 e 9,5 por sessão. Custa uma linha e um teste,
  e protege se o teto de 18 mudar ou se entrar o objetivo força.
- **Q21 — nenhum músculo grande com frequência 1×/semana** (ACSM 2026). **Correção (2026-10-12):** o
  `SplitCatalog` só oferece ARNOLD com 6 dias (2×/semana), então o único caso real é **PUSH_PULL_LEGS
  com 3 dias** (alternativa, não recomendada). Efeito prático: tirar PPL das opções de 3 dias (ficam
  FULL_BODY e UPPER_LOWER_FULL). Programas já gerados não mudam. Motivos: com 1×/semana o volume do
  músculo tem de caber numa sessão só, o que bate no teto de 11 (Q20) e no de 18 por sessão (Q19);
  faltar ao dia zera o músculo na semana; e em força a frequência teve efeito positivo (Pelland 2024).
- No fechamento, a ARCH #26 troca a citação "RP/Israetel" (não revisado por pares) pelas fontes
  acima.

---

## 13. Ao fechar — promover antes de apagar

- [ ] `[INV]` novos → `docs/regras/01-motor-de-geracao.md`: (a) capacidade em minutos é a
      variável de alocação e o alvo é derivado dela; (b) na escassez, prioridade — nunca
      equalização; (c) atendimento do foco ≥ média do programa; (d) âncora estável na semana;
      (e) slot vazio nunca é silencioso. **Substituir** o [INV] dos tetos "≤6 exercícios, ≤20
      séries" e o de determinismo ("estrutura fixa, seleção varia por seed").
- [ ] ADR novo (**ARCH #38 — orçamento de tempo é a unidade de alocação do motor**), e ARCH #26
      ganha nota: o valor-mãe conceitual dela passa a estar implementado; a Fatia B (exigência
      técnica) sai de [PROPOSTA] para vigente.
- [ ] ARCH #28: `is_base` deixa de ser filtro e vira peso de score.
- [ ] `docs/fontes/debitos.md`: deltoide por cabeça, `Goal` sem força, `level` sem leitura no motor,
      `MuscleGroup` incompleto, buracos de catálogo da §6.
- [ ] `docs/historico/motor-v2.md`: a narrativa e os números da §2 (o diagnóstico vale mais que o
      código; é o que impede o próximo ajuste de constante de ser feito no escuro).
- [ ] `docs/migrations/INDICE.md` (V64/V65 + as de catálogo), `ESTADO.md`, `gen_tudo.py`.
- [ ] **este arquivo apagado.**
