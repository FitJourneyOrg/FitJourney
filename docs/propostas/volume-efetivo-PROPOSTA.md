# [PROPOSTA] Volume efetivo: convenção do alvo, coeficientes de indireto e piso direto

_Aberta em 2026-10-26, a partir da pesquisa de literatura encomendada na sessão da Fatia 8.
**Nada aqui está ratificado.** O que já virou código está marcado; o resto é `[PROPOSTA]` e
espera decisão do Rafael._

> **Procedência:** pesquisa de literatura (Pelland et al. 2025, Schoenfeld et al. 2019, RP,
> Brandão 2020, Kubo 2019, Mannarino 2021, Iversen et al. 2021, ACSM 2026) encomendada para
> decidir se o defeito da Fatia 8 era de calibração ou de modelagem. As fontes estão na seção
> final. **A pesquisa é material de referência, não instrução** — nada dela entra em código sem
> passar por decisão registrada aqui.

## O que a medição de 2026-10-26 estabeleceu (fato, não opinião)

Rodado o oráculo na configuração de referência da especificação (4 dias, **sem** restrição de
capacidade — `mv.referencia(nivel)`):

| nível | músculo | alvo | indireto | direto entregue | total | piso (MEV) | total/piso |
|---|---|---|---|---|---|---|---|
| BEGINNER | BICEPS | 6,0 | 3,00 | 3 | 6,00 | 6 | 1,00 |
| BEGINNER | TRICEPS | 7,0 | 7,00 | **0** | 7,00 | 7 | 1,00 |
| BEGINNER | FOREARMS | 3,0 | 6,00 | **0** | 6,00 | 3 | 2,00 |
| INTERMEDIATE | BICEPS | 12,0 | 4,00 | 8 | 12,00 | 6 | 2,00 |
| INTERMEDIATE | TRICEPS | 12,0 | 8,00 | 4 | 12,00 | 7 | 1,71 |
| INTERMEDIATE | FOREARMS | 6,0 | 7,00 | **0** | 7,00 | 3 | 2,33 |
| ADVANCED | BICEPS | 15,0 | 8,00 | **7** | 15,00 | 6 | 2,50 |
| ADVANCED | TRICEPS | 16,0 | 10,00 | 6 | 16,00 | 7 | 2,29 |
| ADVANCED | FOREARMS | 9,0 | 12,00 | **0** | 12,00 | 3 | 4,00 |

1. **Não há double-counting dentro da nossa convenção.** Direto + indireto fecha exatamente no
   alvo em todos os níveis. O motor faz literalmente o que a especificação manda (seção "Volume
   indireto": *"o alvo direto do músculo pequeno passa a ser o alvo da tabela menos o que ele já
   recebeu de indireto"*). O alarme de double-counting levantado pela pesquisa **não se aplica à
   execução** — se aplicar, aplica-se à convenção, que é o item D-1 abaixo.

2. **Achado novo: a convenção produz inversão por nível.** O bíceps recebe **8 séries diretas no
   intermediário e 7 no avançado** — o trabalho direto DIMINUI quando o nível sobe, porque o
   indireto cresce mais rápido que o alvo. O antebraço fica com **0 direto em todos os níveis**,
   inclusive avançado com alvo 9. Isto aparece **sem nenhuma restrição de capacidade**: não é o
   aperto de tempo, é a convenção. A RP afirma o oposto para esses músculos (≥8 séries **diretas**
   de bíceps por semana para intermediário-avançado, subindo com o nível).

3. **O defeito da Fatia 8 (58/156 eventos de zeramento abaixo do mínimo efetivo) é agravado pelo
   aperto de capacidade, mas a raiz está na modelagem**, não na fórmula de sobra. Com capacidade
   folgada o motor acerta o alvo; é só quando aperta que a ausência de um piso direto aparece.

## D-1 — Decisão de prescrição que trava o resto: o que a tabela de volume significa

**Só o Rafael responde: de onde saíram os números 6/12/15/18 (bíceps) e 7/12/16/20 (tríceps)?**

- Se foram **derivados da RP**, eles já são alvos de trabalho **direto** (a RP é explícita: conta
  só séries em que o músculo é motor primário, e diz ter *"already factored in the indirect volume
  from pressing movements"*). Nesse caso, subtrair indireto deles é descontar duas vezes, e o
  motor sub-prescreve direto para todo sinergista.
- Se foram **construídos como alvo total** (direto + indireto), a convenção atual está correta por
  construção e o item 2 acima é uma consequência aceita, não um defeito.

Saídas possíveis, nenhuma escolhida:

| | **Opção A — manter "alvo = total"** (status quo) | **Opção B — virar para "alvo = direto"** (convenção RP) |
|---|---|---|
| Custo | Nenhum na tabela. Só adiciona piso direto e honestidade. | Refaz `REFERENCIA_ALVO_TOTAL` (61/111/148), `REFERENCIA_DEMANDA_DIRETA` (44/86/113) e a tabela de cobertura inteira — números de referência das Fatias 0 a 7. |
| Risco | Mantém a inversão por nível (item 2) e o antebraço em 0 direto. | Reabre fatias fechadas; a capacidade já não comporta os alvos atuais, e alvos "só direto" são maiores. |
| O que a literatura sustenta | Defensável para **iniciante** (Iversen 2021, Gentil 2013/2015: compostos bastam para não treinados). | Defensável para **intermediário/avançado** (RP; Brandão 2020: tríceps não cresce só com supino, +2,1% vs +17,5% na cabeça longa). |
| Saída mista | — | **Híbrido:** indireto conta para o MEV no iniciante; piso direto obrigatório no intermediário/avançado. É o que a pesquisa recomenda explicitamente. |

**Recomendação (minha, não decisão):** o híbrido. Ele resolve o item 2 sem refazer a tabela, e
é exatamente o que a Fatia 8 precisa para fechar. **D-1 continua aberta** e só é bloqueante se
a escolha for a Opção B.

## D-1 — DECIDIDO: híbrido, com três condições (Rafael, 2026-10-27)

**Decisão:** opção híbrida. Uma contabilidade só — **volume efetivo = direto + indireto
fracionado**. O piso direto é **restrição adicional** para INTERMEDIATE/ADVANCED, nunca uma
segunda forma de contar. Opção B (virar para alvo = direto) **descartada**: a literatura
recomenda a contabilidade fracionada, que é a que já temos, e mudar reabriria as Fatias 0–7 para
adotar a convenção que ela considera inferior.

Mais: **estado "manutenção" adotado** — nenhum músculo pode ser marcado como coberto com volume
efetivo abaixo do MEV. Substitui o zeramento cego e fecha a Fatia 8 sem mentir sobre cobertura.
Vira asserção de teste: (a) nenhum "coberto" abaixo do MEV; (b) nenhum músculo pequeno de usuário
intermediário/avançado coberto com direto = 0.

### Condição 1 — auditoria de procedência (feita, 2026-10-27)

**Qual tabela é a primária:** nenhuma das duas referências. A primária é a **`VOLUME` por
músculo**. Medido: `REFERENCIA_ALVO_TOTAL` (61/111/148) é **exatamente a soma das colunas da
`VOLUME`** nos três níveis, e `REFERENCIA_DEMANDA_DIRETA` (44/86/113) é essa soma **menos** o
indireto creditado. As duas são conferências da `VOLUME`, não fontes.

**De onde vieram os números da `VOLUME`:** de `ARCH #26` (Fatia A, ratificada em 2026-10-07),
cuja fonte declarada é *"RP Strength (volume landmarks), Fitbod, Dr. Muscle/JuggernautAI"* —
"landmarks de volume estilo RP". E o cabeçalho das linhas de lá nomeia o **sinal de casamento**:
"CHEST (primary CHEST)", "ARMS (primary ARMS — bi+tri)", "GLUTES (primary GLUTES/HINGE)". Ou
seja: contados sobre exercícios em que o músculo é **motor primário = trabalho direto**. Naquele
momento **não existia modelo de indireto nenhum**.

O motor v3 introduziu o indireto e **diz isso na própria especificação**: *"Sem essa conta, o
motor tratava a tabela de volume como alvo de trabalho **direto**."* A tabela foi redefinida como
**total** subtraindo o indireto dela, **sem subir os números** — com uma exceção.

**A exceção são os braços.** O *item 20* subiu BÍCEPS para 6/**12**/15/18 e TRÍCEPS para
7/**12**/16/20 (vinham do ARMS combinado 6/10/14/20), e a especificação registra a intenção:
*"com o alvo de 12, bíceps e tríceps passam de ~3 para ~6 séries diretas por semana"*. Eles foram
subidos **justamente para compensar a subtração** — são "total" por intenção.

**Veredito: a tabela é MISTA, e é aí que está o defeito.**

| músculo | recebe indireto de | procedência do número | convenção de fato | double-count? |
|---|---|---|---|---|
| TRICEPS | CHEST 0,5 · SHOULDERS 0,5 | subido no item 20, com o efeito direto declarado | **total por intenção** | **não** |
| BICEPS | BACK 0,5 | subido no item 20, idem | **total por intenção** | **não** |
| FOREARMS | BACK 0,5 · POSTERIOR 0,5 | herdado, nunca revisto | direto (RP) | **SIM** |
| GLUTES | POSTERIOR 0,5 · QUADS 0,5 | herdado do `ARCH #26` ("primary GLUTES/HINGE") | direto (RP) | **SIM** |
| POSTERIOR | GLUTES 0,2 | herdado, nunca revisto | direto (RP) | **sim, nominal** |

**O dano medido**, na configuração de referência (4 dias, **sem** aperto de capacidade — então
não é falta de tempo, é a convenção):

| nível | músculo | alvo | indireto | direto | alvo − indireto |
|---|---|---|---|---|---|
| BEGINNER | FOREARMS | 3,0 | 6,00 | **0** | **−3,00** |
| BEGINNER | GLUTES | 4,0 | 6,00 | **0** | **−2,00** |
| INTERMEDIATE | FOREARMS | 6,0 | 7,00 | **0** | **−1,00** |
| INTERMEDIATE | GLUTES | 8,0 | 7,00 | 2 | 1,00 |
| ADVANCED | FOREARMS | 9,0 | 12,00 | **0** | **−3,00** |
| ADVANCED | GLUTES | 12,0 | 8,00 | 4 | 4,00 |
| ADVANCED | POSTERIOR | 14,0 | 0,80 | 13 | 13,20 |

FOREARMS recebe **mais indireto que o próprio alvo em todo nível** — por isso fica com 0 direto
sempre, inclusive avançado com alvo 9. GLUTES é consumido por inteiro no iniciante. POSTERIOR é
afetado só no papel: recebe 0,2 apenas de composto de glúteo, e na referência o glúteo entrega 0
composto, então na prática recebe ~0.

### Condição 3 — matriz INDIRETO atual contra a lista sancionada

| par (doador → recebedor) | hoje | o artigo sanciona? | situação |
|---|---|---|---|
| peito → tríceps | 0,5 | **sim** ("tríceps em pressões") | ✅ com fonte |
| ombro → tríceps | 0,5 | **sim** (idem) | ✅ com fonte |
| costas → bíceps | 0,5 | **sim** ("bíceps em puxadas/remadas") | ✅ com fonte |
| quadríceps → glúteo | 0,5 | **sim** ("glúteo em agachamento") | ✅ com fonte |
| posterior → glúteo | 0,5 | **não está na lista** | ⚠️ sem fonte |
| costas → antebraço | 0,5 | **não está na lista** | ⚠️ sem fonte (já era débito) |
| posterior → antebraço | 0,5 | **não está na lista** | ⚠️ sem fonte (já era débito) |
| glúteo → posterior | 0,2 | **não está na lista** | ⚠️ sem fonte |

E os zeros que o artigo manda, conferidos: **isquiotibiais em agachamento/leg press** →
`QUADS → POSTERIOR` **não existe na matriz, já é 0** ✅; **gastrocnêmio em agachamento** →
`QUADS → CALVES` **não existe, já é 0** ✅.

**Uma recomendação do artigo não é representável no nosso modelo:** "0 para **reto femoral** em
agachamento" não é crédito indireto — é afirmação sobre o quanto o agachamento conta como
trabalho **direto** de quadríceps, e nós temos um alvo QUADS único, sem porções. Registrar como
limite conhecido do modelo, não como coeficiente.

### O achado que muda o conserto

As condições 1 e 3 **colidem a favor**: os pares sem fonte são **exatamente** os que causam o
double-count. Se `→ antebraço` (0,5 de costas e 0,5 de posterior) vai a 0 por falta de fonte, o
antebraço deixa de receber 6/7/12 e o problema dele desaparece **sem subir alvo nenhum**. O mesmo
vale em parte para `posterior → glúteo`.

Ou seja: **corrigir coeficiente pode resolver o double-count de graça**, sem tocar na `VOLUME` e
sem reabrir número de fatia fechada. Medir isso antes de propor subir qualquer total.

## D-2 — Coeficientes de indireto por par (hoje: 0,5 único, sem fonte)

A especificação já registrou isto como débito consciente: *"o coeficiente por exercício, que seria
o certo — supino com pegada fechada e crucifixo não entregam o mesmo tríceps — fica para quando a
curadoria do catálogo permitir."* A pesquisa fornece a fonte que faltava.

**O 0,5 que já usamos é o valor mais bem sustentado pela literatura.** Pelland et al. 2025
(*Sports Medicine*, 67 estudos, 2.058 participantes) testou contar a série indireta como 0, 0,5
ou 1 e concluiu que *"the relative evidence for the 'fractional' quantification method was
strongest"*. Mannarino 2021 mediu rosca +11,06% vs remada +5,16% na espessura dos flexores do
cotovelo — razão coerente com 0,5. **Nenhuma mudança proposta nesses pares.**

O que a pesquisa sustenta mudar — `[PROPOSTA]`, não ratificado:

| par | hoje | proposto | base |
|---|---|---|---|
| costas → bíceps | 0,5 | **0,5** (manter) | Pelland 2025; Mannarino 2021 |
| peito, ombro → tríceps | 0,5 | **0,5** (manter) | Pelland 2025 |
| quadríceps → glúteo | 0,5 | **0,5** (manter) | Kubo 2019 (glúteo +6,7% só com agachamento) |
| posterior → glúteo | 0,5 | **0,5** (manter) | — |
| glúteo → posterior | 0,2 | **0,2** (manter) | Nossa medição própria já derrubou 0,5 (atendimento do posterior 86%→76%); Kubo 2019 mediu isquiotibiais **sem mudança** com agachamento, o que sustenta o valor baixo |
| costas → antebraço | 0,5 | **a rever** | Sem estudo direto. É o par que mantém o antebraço em 0 direto em todos os níveis (item 2) |
| posterior → antebraço | 0,5 | **a rever** | idem |

**Observação importante:** os pares que a pesquisa manda zerar (isquiotibiais e reto femoral no
agachamento, gastrocnêmio no agachamento) **já são zero na nossa tabela** — não creditamos nada
disso. Nossa tabela de indireto já está, nesse ponto, mais alinhada com a literatura do que o
default que a pesquisa descreve.

O único par sem fonte e com efeito visível é **→ antebraço**. Débito, não urgência.

## D-3 — Piso de séries diretas (é o que fecha a Fatia 8)

A pesquisa é direta: *"a alocação proporcional por peso fixo é cega ao déficit"* e a correção é
estrutural — piso direto como restrição rígida, depois distribuição do resto por déficit.

Isto **não depende de D-1 nem de D-2** e cabe dentro da Fatia 8. Desenho e medições em
`docs/trabalho/motor-v3-fatia-8.md`.

Referência externa para dimensionar o piso (a medir, não a assumir):
- RP: volume de manutenção (MV) de bíceps ≈ 4 séries diretas/semana.
- Iversen et al. 2021 (*Sports Medicine*, "No Time to Lift?"): ≥4 séries semanais por grupo, e
  monoarticulares **não** trazem benefício adicional substancial para iniciantes.
- O mesmo trabalho relata que superséries e rest-pause *"roughly halves training time"* mantendo
  o volume — relevante porque o custo em minutos do piso é menor do que a conta linear sugere.
  **Fora de escopo agora** (nosso motor não compõe superséries), mas registrado.

## D-4 — Estado honesto: "manutenção" em vez de "coberto"

Hoje o motor não tem vocabulário para "este músculo ficou abaixo do mínimo". Ele entrega o
programa e o usuário não sabe. A pesquisa aponta que o padrão de mercado (RP: `Emphasize` /
`Grow` / `Maintain`; Alpha Progression: duração e prioridade como entradas explícitas) é declarar
o trade-off, não escondê-lo.

Encaixa na **Fatia 9**, que já prevê *"aviso de honestidade: gatilho é músculo grande abaixo do
piso, com texto acionável"* — a proposta é estender o gatilho para músculo **pequeno** abaixo do
piso e dar status explícito por músculo no `ProgramAudit`. Não é fatia nova.

## O que NÃO adotar da pesquisa

- **Converter a tabela para "séries fracionadas"** (opção A do relatório): só faz sentido se D-1
  fechar na Opção B, e mesmo aí refaz as Fatias 0-7.
- **Coeficiente de "eficiência contrátil" de 0,4–0,5 por cima do crédito já fracionado:** conta o
  indireto duas vezes. Já rejeitado na sessão de 2026-10-24, quando apareceu numa proposta
  externa.
- **Estudos de Barbalho et al.** sobre ausência de benefício de monoarticulares: **retratados**
  (JSCR 2020 e Eur J Sport Sci 2020, ambos em 2022, por irregularidades de dados). Não citar em
  documentação de produto.
- **ACSM 2026 ("≥10 séries por grupo por semana")** como meta fracionada: a pesquisa não conseguiu
  confirmar no texto original como o position stand conta séries indiretas. Não usar como âncora.

## Caveats da própria pesquisa (registrados para não virarem fato por repetição)

- O coeficiente 0,5 é **o melhor de três valores discretos testados** (0 / 0,5 / 1), não uma
  estimativa contínua. Os próprios autores o chamam de heurística.
- Os estudos comparativos (Mannarino, Gentil, Brandão, Kubo) são majoritariamente de **não
  treinados**, amostras de 10 a 43 sujeitos, 8 a 10 semanas. Transpor para treinados é inferência.
- Os números de MEV/MV da RP são, nas palavras da própria RP, *"starting points, not gospel"* —
  médias de experiência com clientes, não ensaios controlados.
- Nenhum app grande (RP, Juggernaut, Fitbod, Alpha Progression) publica seu algoritmo de alocação.
  O que a pesquisa extraiu é material de marketing e central de ajuda.

## Fontes

- Pelland, Remmert, Zourdos et al. (2025). *The Resistance Training Dose-Response:
  Meta-Regressions…* — Sports Medicine, doi 10.1007/s40279-025-02344-w.
- Schoenfeld, Grgic, Haun, Itagaki & Helms (2019). *Calculating Set-Volume for the Limb Muscles…*
  — Sports 7(7):177.
- Brandão et al. (2020). J Strength Cond Res 34(5):1254–1263 (tríceps: supino vs extensão, RM).
- Mannarino et al. (2021). J Strength Cond Res 35(10):2677–2681 (rosca vs remada, intra-sujeito).
- Kubo et al. (2019). Eur J Appl Physiol 119:1933–1942 (agachamento: glúteo, isquios, reto femoral).
- Gentil, Soares & Bottaro (2015). Asian J Sports Med 6(2):e24057; Gentil et al. (2013) APNM.
- Iversen, Norum, Schoenfeld & Fimland (2021). *No Time to Lift?* — Sports Medicine.
- Rosa et al. (2023). Strength Cond J 45(1):49–57, doi 10.1519/SSC.0000000000000720.
- Renaissance Periodization — volume landmarks e artigo de bíceps (rpstrength.com).
- Retratações de Barbalho et al.: retractionwatch.com; strongerbyscience.com/barbalho.
