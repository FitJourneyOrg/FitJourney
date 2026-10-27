# Motor de prescrição de treino — revisão técnica

> **Especificação do motor v3. Pendente de ratificação por profissional com CREF.**
>
> Produzida em cinco rodadas de revisão técnica com um especialista, fechadas em 2026-10-13.
> Exportada do artifact `claude.ai/code/artifact/f664e0d0-68f3-4657-9036-71cc5fe5d6c2`, que é
> onde as threads de comentário vivem — **esta cópia é leitura, não fonte de edição**; editar aqui
> faz as duas divergirem.
>
> **O que é:** o *porquê* de cada regra e de cada número, com a medição que o sustenta.
> **O que não é:** o plano de implementação — esse é `trabalho/motor-v3-desenho-e-fatias.md`,
> e as fatias já carregam as regras ratificadas, então uma sessão de código não precisa abrir isto.
>
> **Destino:** as fatias promovem o conteúdo daqui para `regras/01-motor-de-geracao.md` como
> `[REGRA]`/`[INV]` e para `arch/` como ADR. Quando a última fechar, este arquivo sai.
>
> Histórico de como o desenho chegou aqui: `historico/motor-redesenho-v2.md`.

Oct 13, 2026 · @Rafael

Este documento descreve as regras de treinamento por trás de um aplicativo que monta programas de musculação automaticamente, e existe para ser criticado por quem entende de prescrição.

## Quinta rodada — o que pedimos agora

Você achou três bugs estruturais no motor e um erro nosso de leitura. Checamos os quatro no código antes de responder: **os quatro se confirmam.** Nenhum número de capacidade ou de cobertura que publicamos até aqui sobreviveu a esta rodada.

### O que conferimos no código

**1. O motor não aplica teto por sessão — confirmado.** `capacity_sets()` devolve 92,2 séries por semana para 4 dias × 60 min, ou **23,1 por sessão**, e não há teto de 16 nem de 18 em lugar nenhum do caminho. Na saída, Upper A e Upper B levam 23 séries cada. O teto por nível também não existe: 92,2 é idêntico para intermediário e avançado.

E sobre os 64: escrevemos que 92,2 estava "limitado por 4 × 16 = 64", o que é aritmeticamente impossível, como você apontou. **92,2 é a capacidade**, não um valor limitado por ela — o water-fill preenche o orçamento inteiro, e por isso o número não se mexe quando o alvo muda. A conclusão estava certa pelo motivo errado.

**2. A poda no fim voltou — confirmado, e com a ordem invertida.** Alocado contra entregue, avançado a 60 min:

| músculo | peso de prioridade | alocado | entregue | delta |
| --- | --- | --- | --- | --- |
| Costas | 1,10 | 18,9 | 14 | −4,9 |
| Peito | 1,00 | 16,1 | 13 | −3,1 |
| Quadríceps | 1,00 | 14,1 | 16 | +1,9 |
| Ombro | 0,90 | 13,3 | 12 | −1,3 |
| Posterior | 0,90 | 13,3 | 14 | +0,7 |
| Bíceps | 0,50 | 5,2 | 4 | −1,2 |
| Panturrilha | 0,40 | 8,3 | 10 | +1,7 |

Os três de maior prioridade perdem; panturrilha, segunda na ordem de sacrifício, ganha.

**3. O indireto sai de um programa que não é o entregue — confirmado.** No código, `ind = indirect_from(d1)`: a medição acontece sobre a estrutura provisória, antes da realocação que produz `d2` e antes do preenchimento. É por isso que o glúteo doa 1,6 ao posterior com 0 séries alocadas e 0 entregues.

**4. A regra de composição — você está certo, e nós dois estávamos errados.** O motor dá exatamente 3 séries a todo composto e joga o resto no isolamento. Medido no avançado sem restrição de capacidade: composto pesado com 3 séries em 10 posições e 5 séries em 2; acessório sempre 3; isolamento com 3, 4 e 5. As duas exceções de 5 vêm do passo que gasta o tempo que sobra no dia.

### A checagem de segurança passa, mas por acidente

A âncora de ombro do avançado **é** Desenvolvimento Militar com Barra. Mas o composto de peito no mesmo dia tem **3 séries, não 6** — porque o composto nunca passa de 3 —, então a regra não dispara e não há bug de segurança nesta configuração.

Dissemos aqui que o limiar de 6 era inalcançável, e estava errado: você mostrou pela nossa própria tabela que o avançado tem 14 compostos de peito por semana, ou 7 por sessão, porque com 9 séries de peito entram âncora e acessório. O limiar é alcançável no avançado, e o que vale agora é a sua regra, na seção do ombro. Texto anterior: **com o composto preso em 3 séries, o limiar de "6 ou mais séries de composto de peito no dia" é inalcançável.** Precisaria de dois compostos de peito na mesma sessão. A regra que escrevemos na terceira rodada é código morto — e continua sendo com a sua regra nova, em que o teto por exercício é 4. **O limiar precisa ser reescrito em algo que a composição produza**, por exemplo "quando o dia já tem composto de peito", sem número. É o único item de segurança aberto desta rodada.

### A regra de composição, escrita

Sua proposta, adotada:

- Exercícios por músculo por sessão: até 4 séries → 1 exercício; 5 a 8 → 2; 9 ou mais → 3.
- **De 2 a 4 séries por exercício.** O volume enche o primeiro até 4 e passa ao próximo, sem deixar nenhum abaixo do piso: 3 no composto, 2 no isolamento.

Medimos as duas regras lado a lado, com a mesma frequência de 2× e sem restrição de capacidade:

| nível | demanda direta, motor de hoje | demanda direta, regra nova |
| --- | --- | --- |
| iniciante | 40,8 | **42,8** |
| intermediário | 86,4 | **84,4** |
| avançado | 116,8 | **110,4** |

A coluna da esquerda é a réplica da regra que você identificou, e ela reproduz a simulação (86,4 contra 87,0; 116,8 contra 118,4), o que valida o modelo. A da direita é a que passa a valer, e é a que está nas tabelas deste documento.

### Onde corrigimos a nós mesmos, não a você

**A rampa estava contada duas vezes, e o erro era nosso.** Você perguntou o que entra nos 12 minutos de (minutos − 12) ÷ 3: entram 6 de aquecimento fixo e 5 de troca de exercício. Então a rampa **substitui** o aquecimento, não soma a ele. Refeito, com a sua exceção de 2 séries para barra livre de outra região: um full body com quatro compostos de barra gasta 3 + 2 + 2 + 2 = 9 séries de rampa, cerca de 11 minutos, mais 5 de troca — 16 de overhead. O teto de 45 min cai de 11 para **9,7**, não para 8,7, e o intermediário em 2 dias × 45 min vai de 25% para **23%**, não para 21%. Em upper/lower, onde há só dois compostos de barra, o custo é de cerca de um minuto.

Isso derruba o nosso próprio argumento contra oferecer 45 minutos. Somado ao seu — recusar não faz a pessoa treinar 60, faz treinar 45 com treino de rede social —, a faixa fica.

**E a calibração:** você está certo de que "sem teto" trocaria um problema por outro, porque salto proporcional a um RIR mal estimado é proporcional a ruído. Suas faixas — 1 a 2 acima do alvo → +10%, 3 a 4 → +25%, 5 ou mais → +50%, com teto de +25% em barra livre — ficam como estão, e levam o exemplo de 20 kg a perto de 60 na terceira exposição.

### O que precisamos de você

1. **O limiar do ombro reescrito.** É o único item de segurança aberto, e não temos critério próprio para substituí-lo.
2. **Os números novos**, antes de implementarmos: demanda direta de 42,8 · 84,4 · 110,4, e a cobertura refeita adiante.
3. **O glúteo**, que não fecha com regra nenhuma. Sua hipótese das etiquetas de perna no catálogo é a próxima coisa a medir, e ainda não medimos.

Nesta rodada não discordamos de nada: as quatro acusações eram verificáveis e as quatro se confirmaram. Os dois números que corrigimos acima são contra nós, não contra você.

## O que mudou na primeira rodada (seus itens 1 a 28)

### Segurança (seus itens 1 a 9)

| # | sua correção | o que fizemos |
| --- | --- | --- |
| 1 | RIR mínimo 2 em barra livre | aplicado, e corrigido nesta rodada: a regra vale por exercício, não por papel |
| 2 | progressão dupla com incremento mínimo | aplicado; 1RM estimado sai do isolamento |
| 3 | limitações articulares de volta, como substituição | aplicado; cinco regiões, janela de 12 meses, mapeadas para troca e não exclusão |
| **[VENCIDO, 2026-10-23]** | item 3 acima | descontinuado -- decisão de 21/10 (V65): limitação declarada (`profiles.limitations`) não entra mais em decisão do motor, vira aviso ao usuário no onboarding. Registrado ao abrir a Fatia 7; não reintroduzir como filtro nem como troca automática sem nova decisão do Rafael |
| 4 | botão de dor com escalonamento | aplicado; tipo, intensidade e contagem por região |
| 5 | nível derivado de perguntas objetivas | aplicado; tempo de treino consistente e tempo parado |
| 6 | aquecimento ausente | aplicado; rampa automática antes do primeiro pesado de cada padrão |
| 7 | regra de retorno após pausa | aplicado com os seus três patamares |
| 8 | âncora de costas com apoio | aplicado; resolve a ordem dos dias do PPL sem modelo de fadiga |
| 9 | ombro como alvo único | aplicado no mínimo que você indicou; a divisão em três porções virou `[PENDENTE]` |

### Contas e contradições (seus itens 10 a 16)

| # | sua correção | o que fizemos |
| --- | --- | --- |
| 10 | gráfico compara grandezas diferentes | **confirmado medindo**; gráfico refeito sobre a demanda direta |
| 11 | objetivo e idade não agem no teto | aplicado; os fatores passam a multiplicar o teto da sessão |
| 12 | "manter" gera a sessão mais longa | aplicado; teto próprio e descansos iguais aos de ganhar massa |
| 13 | exemplo de 2 dias contradiz as regras | **confirmado como bug**; suas três condições viraram teste |
| 14 | PPL em 3 dias | removido do catálogo de divisões |
| 15 | mesociclo soma séries em sessão no teto | aplicado; séries fixas no ciclo, progressão por carga |
| 16 | inconsistências menores | corrigidas: "força" saiu, mesociclo fixo em 4 semanas, trecho da disponibilidade reescrito |

### Prescrição (seus itens 17 a 28)

| # | sua correção | o que fizemos |
| --- | --- | --- |
| 17 | voltar a perguntar os minutos | aplicado em faixas, com calibração pela duração real — **reverte decisão nossa de ontem** |
| 18 | descanso igual para perder gordura | aplicado |
| 19 | corte de volume aos 40 e RIR por idade | aplicado; fator 40-59 removido |
| 20 | alvo de braço baixo demais | aplicado; intermediário sobe para 12 |
| 21 | piso de 2 séries no isolamento | aplicado — **reverte regra ratificada nossa** |
| 22 | músculo pequeno abrindo a sessão | aplicado com a sua condição |
| 23 | ênfase permanente e sem limite | aplicada como fase de um mesociclo, com +3 e +6 |
| 24 | iniciante com deload e âncora rodando | aplicado; âncoras fixas e deload por estagnação |
| 25 | texto do limiar de honestidade | reescrito; gatilho passa a ser músculo abaixo do piso |
| 26 | lista de equipamentos no lugar de casa/academia | aceito; virou `[PENDENTE]` de escopo |
| 27 | carga inicial perguntada | aplicado; série de calibração leve |
| 28 | abdômen sem exercício | aplicado; alvo zero até o catálogo ter |

## O que é o motor

O motor monta um **programa semanal completo de musculação** a partir de um questionário curto de cadastro. Ele decide a divisão de treino, quais músculos entram em cada dia, quantas séries cada um recebe, quais exercícios, em que ordem, com quantas repetições, quanto descanso e qual RIR alvo.

**Não há inteligência artificial generativa no caminho.** É um conjunto de regras determinísticas: o mesmo perfil produz o mesmo programa. Isso é proposital — queremos poder auditar por que cada série foi prescrita, e queremos que a revisão de um especialista mude o comportamento de todos os usuários de uma vez, não de forma imponderável.

### Para quem

Pessoas que treinam em academia, do iniciante ao avançado, **sem acompanhamento profissional presencial**. É o público que hoje copia treino de rede social ou repete a mesma ficha por dois anos. O treino em casa fica para depois do lançamento, já com a lista de equipamentos.

Essa população é o que mais distancia nossas decisões da prática de consultório. Várias regras que fazem sentido com um atleta ao lado — "ajuste pelo feeling", "duas séries válidas levadas próximo à falha" — não funcionam quando ninguém confere a execução nem a proximidade da falha.

### O que ele faz

- Distribui o volume semanal por músculo dentro do que a semana da pessoa comporta
- Escolhe a divisão de treino compatível com o número de dias
- Seleciona os exercícios de um catálogo curado, respeitando equipamento disponível
- Define séries, faixa de repetições, descanso e RIR de cada exercício
- Mantém os mesmos exercícios por algumas semanas para permitir progressão comparável

### O que ele não faz, de propósito

- **Não prescreve carga no primeiro contato.** A primeira exposição a cada exercício é uma série de calibração leve, em que a pessoa informa o RIR percebido; daí em diante o sistema sugere a carga.
- **Não substitui avaliação física nem diagnóstico.** Antes de liberar a geração há uma autoavaliação de prontidão (PAR-Q) cujas respostas não são armazenadas: quem se identifica em qualquer item é orientado a procurar um médico antes de começar.
- **Não prescreve dieta.**
- **Não trata lesão.** A dor relatada entra num escalonamento — tipo, intensidade e contagem por região — que troca o exercício e, se persistir, orienta procurar avaliação. O app não tenta reabilitar.

## O que perguntamos

| pergunta | opções | onde pesa |
| --- | --- | --- |
| Objetivo | ganhar massa · perder gordura · manter · saúde geral | volume semanal, teto da sessão, faixas de repetição, RIR |
| **Tempo de treino consistente** | nunca treinou · menos de 6 meses · 6 meses a 2 anos · mais de 2 anos | **deriva o nível; o máximo de entrada é intermediário** |
| **Há quanto tempo parou** | não parei · até 1 mês · 1 a 6 meses · mais de 6 meses | rebaixa o nível e aciona a regra de retorno |
| Dias por semana | 2 a 6 | divisão de treino e capacidade total |
| **Minutos disponíveis** | 45 · 60 · 75 · 90+ | **teto de séries da sessão** |
| Divisão preferida | opcional, lista curada por número de dias | se válida, vale a escolha |
| Ênfase | até 2 grupos, só intermediário e avançado | fase de priorização de dois mesociclos |
| Idade | número | restrições a partir dos 60 |
| **Dor ou lesão nos últimos 12 meses** | ombro · joelho · lombar · quadril · cotovelo/punho | **substituição de exercício**, nunca exclusão de bloco |
| Ambiente | academia · casa (em breve) | filtro do catálogo; "casa" sai marcada "em breve" na primeira tela |
| Autoavaliação de prontidão | PAR-Q, sem registrar resposta | não bloqueia e não entra no motor; orienta procurar um médico |

As quatro linhas em negrito são **novas nesta versão**, todas vindas da sua revisão.

### Nível deixou de ser autodeclarado (seu item 5)

O nível é derivado, não escolhido. Ponto de partida: menos de \~6 meses consistentes, **ou** parado há mais de \~6 meses, é iniciante. Depois do primeiro mesociclo, o histórico de cargas reavalia. E **ninguém entra como avançado pelo cadastro** (sua correção S2): o máximo de entrada é intermediário, e avançado só se alcança pela promoção por histórico. Tempo de academia não é nível — muita gente treina cinco anos sem sair do intermediário.

O motivo é o seu: era a variável que decidia volume, teto e exercícios liberados, e a mais superestimada pelo próprio usuário.

### Os minutos voltaram (seu item 17)

Nós havíamos **removido** essa pergunta um dia antes da sua revisão, por entender que a duração é consequência de como a pessoa treina. Seu argumento nos convenceu: a pessoa não sabe quanto o treino leva, mas sabe quanto tempo **tem**.

A pergunta é em faixas, e o teto de séries passa a sair dela:

| faixa | teto de séries da sessão |
| --- | --- |
| 45 min | \~11 |
| 60 min | 16 |
| 75 min ou mais | \~18 |

Do tempo disponível descontamos aquecimento e trocas de exercício; o restante é dividido pelo tempo médio por série (execução mais descanso prescrito, com folga para o descanso real). Depois do primeiro mesociclo, a duração registrada no app recalibra a faixa.

### Limitações articulares voltaram, mas como substituição (seu item 3)

A pergunta é **dor ou lesão nos últimos 12 meses** (sua correção S4), não "histórico de dor": uma lesão curada há dez anos não deve trocar exercício para sempre. São cinco regiões, e cada uma mapeia para **trocas**, não para exclusão de bloco:

| região | exemplo de substituição |
| --- | --- |
| ombro | desenvolvimento com halter em pegada neutra no lugar do militar com barra |
| joelho | leg press ou agachamento goblet no lugar do agachamento com barra |
| lombar | âncora de costas apoiada; hinge com apoio no lugar do terra; leg press ou hack no lugar do agachamento com barra |
| quadril | máquina com amplitude controlada: leg press raso, elevação pélvica, extensora e flexora. Afundo e búlgaro saem — o padrão unilateral exige mais do quadril, não menos |
| cotovelo / punho | pegada neutra e barra W no lugar da barra reta |

**O PAR-Q virou autoavaliação** (nossa discordância 3): as perguntas aparecem, nenhuma resposta é armazenada, e quem se identifica em qualquer item é orientado a procurar um médico antes de começar. Bloquear fazia a pessoa mentir; ignorar desperdiçava a informação. O custo declarado de não armazenar é que o "sim" deixa de poder acionar o **modo conservador** — esse modo passa a ser disparado só por iniciante, saúde geral e 60+ no primeiro mesociclo.

### O que continua fora

**Peso corporal e medidas** — não entram em nenhuma decisão. **Estresse, sono e nutrição** — fora do escopo.

> **Respondido nesta rodada:** lançar só com academia; a lista de equipamentos é condição de lançamento apenas para o treino em casa. Quando casa abrir, abre já com a lista, sem passar pelo "casa" genérico. Para academia, um botão "não tenho este equipamento" resolve as academias de condomínio.

## O princípio central: a capacidade é escassa

Esta é a decisão mais estruturante do motor, e **foi recalibrada depois da sua correção 10** — o número que apresentávamos estava inflado.

A semana tem capacidade finita, mas a comparação certa não é contra o alvo da tabela: o alvo **inclui** o trabalho indireto, e a capacidade conta **séries executadas**. A comparação válida é contra a demanda **direta**.

| nível | alvo total | demanda direta | cobertura em 4 dias × 60 min |
| --- | --- | --- | --- |
| iniciante | 61 | 44,0 | 145% |
| intermediário | 111 | 86,0 | 76% |
| avançado | 148 | 113,0 | 58% |

Os três níveis pelo mesmo método: regra de composição de 2 a 4 séries por exercício, composto do doador saindo do volume próprio dele e não do alvo, e a regra nova do ombro. Três versões anteriores desta coluna estavam erradas, cada uma por um motivo diferente, e as três foram derrubadas pela revisão: a primeira misturava demanda com o volume **alocado**, que era a capacidade de 92,2; a segunda media com a composição antiga do motor; a terceira creditava indireto de um glúteo que não treina.

Os três níveis pelo mesmo método, com a **regra de composição nova** (2 a 4 séries por exercício) e sem restrição de capacidade. Duas versões anteriores desta coluna estavam erradas: a primeira misturava uma demanda com o volume **alocado** do avançado, que era na verdade a capacidade de 92,2; a segunda media com a composição antiga do motor, em que todo composto ficava preso em 3 séries.

A demanda direta foi **remedida com a capacidade removida** (mesma simulação, sessão de 150 min, 4 dias em upper/lower), porque a coluna publicada antes misturava duas grandezas: o valor do avançado, 92, era o volume **alocado depois do corte por capacidade**, não uma demanda. A prova é que ele dava 92,2 tanto com a tabela antiga (alvo 153) quanto com a nova (alvo 148) — o alvo caiu 5 séries e o número não se mexeu. Compará-lo contra a capacidade era comparar a capacidade consigo mesma.

A coluna da demanda direta foi **recalculada da especificação** nesta rodada (sua conta 2), não medida na simulação. Para o avançado os dois métodos discordam em 19 séries, e a reconciliação está entre os pontos abertos.

Os totais mudaram pela tabela nova — braço a 12, abdômen a zero. A escassez some para o iniciante e aperta bem mais do que dissemos para o avançado, cuja demanda direta estava subestimada em 20%. **O alvo do nível é máximo, não meta a ser enchida** (sua contradição 10): a sobra do iniciante vira sessão mais curta, não volume extra até o teto da tabela.

&#91;embedded content: capacidade = dias × teto da sessão na faixa de 60 min, contra a demanda direta (alvo da tabela menos o trabalho indireto) · medido na simulação do motor\]

Montar um programa continua não sendo somar volumes-alvo: é distribuir um recurso escasso, e a diferença entre um programa bom e um ruim está em **quem leva o corte**.

### Cobertura por faixa de tempo

Teto da sessão = min(teto por tempo, teto por nível), com teto por tempo = (minutos − overhead) ÷ 3. O overhead depende da divisão, por causa da rampa: 16 minutos no full body de 2 e 3 dias, que carrega quatro compostos de barra, e 11 no upper/lower e no PPL, que carregam dois. O teto por nível é 16 para o iniciante e 18 para os demais, e é ele que faz 75 minutos render o mesmo que 60 para o iniciante — mas só a partir de 4 dias: em 2 e 3 dias o overhead maior deixa 75 acima de 60.

| nível e faixa | 2 dias | 3 dias | 4 dias | 5 dias | 6 dias |
| --- | --- | --- | --- | --- | --- |
| iniciante, 45 min | 44% | 66% | 103% | 129% | 155% |
| iniciante, 60 min | 67% | 100% | 145% | 182% | 218% |
| iniciante, 75 min | 73% | 109% | 145% | 182% | 218% |
| intermediário, 45 min | 22% | 34% | 53% | 66% | 79% |
| intermediário, 60 min | 34% | 51% | 76% | 95% | 114% |
| intermediário, 75 min | 42% | 63% | 84% | 105% | 126% |
| avançado, 45 min | 17% | 26% | 40% | 50% | 60% |
| avançado, 60 min | 26% | 39% | 58% | 72% | 87% |
| avançado, 75 min | 32% | 48% | 64% | 80% | 96% |

O overhead agora depende da divisão, por causa da rampa: full body de 2 e 3 dias carrega quatro compostos de barra por sessão (3 + 2 + 2 + 2 séries de rampa, cerca de 16 min de overhead); upper/lower e PPL carregam dois, cerca de 11 min. É isso que faz a coluna de 3 dias cair mais que a de 4.

O que isso muda na narrativa: **a escassez é muito maior do que dissemos**, e o avançado não fecha em configuração nenhuma — nem com 6 dias de 75 minutos. O intermediário em 2 dias × 45 min cobre 26% — é aí que a ordem de sacrifício e o aviso de honestidade decidem o programa, e é por isso que a coluna não devia ter saído. E a rampa de 3 séries por composto de barra, se valer para todos, derruba o teto de 45 min de 11 para 8,7 séries, levando esse 26% a 21%.

### Como a demanda direta foi recalculada

Para você poder arbitrar os 92 contra os 110,8, o método — que é o da especificação, não o da simulação. Frequência 2× por semana, que é o caso de 4 dias em upper/lower.

1. **Séries de composto por semana de cada doador**, pelas regras de composição: 1 exercício = âncora; 2 = âncora + isolamento de 3 séries; 3 = âncora + acessório + isolamento de 3.
2. **Indireto recebido** = séries de composto do doador × o coeficiente da tabela de volume indireto.
3. **Demanda direta** = max(0, alvo − indireto).

Séries de composto por semana, passo 1, pela regra nova — intermediário: peito 8, costas 8, ombro 8, quadríceps 8, posterior 6, glúteo 0. Avançado: peito 14, costas 16, ombro 6, quadríceps 8, posterior 8, glúteo 0. O ombro do avançado cai de 8 para 6 pelo teto do desenvolvimento, e o glúteo fica em zero porque o volume próprio dele não chega ao piso de um composto.

| músculo | alvo int | indireto int | direto int | alvo avç | indireto avç | direto avç |
| --- | --- | --- | --- | --- | --- | --- |
| Peito | 14 | 0 | 14,0 | 18 | 0 | 18,0 |
| Costas | 16 | 0 | 16,0 | 20 | 0 | 20,0 |
| Ombro | 12 | 0 | 12,0 | 16 | 0 | 16,0 |
| Bíceps | 12 | 4,0 | 8,0 | 15 | 8,0 | 7,0 |
| Tríceps | 12 | 8,0 | 4,0 | 16 | 10,0 | 6,0 |
| Antebraço | 6 | 7,0 | 0 | 9 | 12,0 | 0 |
| Quadríceps | 12 | 0 | 12,0 | 16 | 0 | 16,0 |
| Posterior | 10 | 0 | 10,0 | 14 | 0 | 14,0 |
| Panturrilha | 9 | 0 | 9,0 | 12 | 0 | 12,0 |
| Glúteo | 8 | 7,0 | 1,0 | 12 | 8,0 | 4,0 |
| Abdômen | 0 | 0 | 0 | 0 | 0 | 0 |
| **total** | **111** | **26,0** | **86,0** | **148** | **38,0** | **113,0** |

**Correção do passo 1, sua.** O composto de cada doador saa do **alvo** dele, e o glúteo tem alvo 8 e 12 mas demanda direta de 1 e 4 — ou seja, ele mal ganha exercício próprio, e mesmo assim creditava 1,6 ao posterior. Era o bug do `indirect_from(d1)` reproduzido no modelo, dois parágrafos depois de nós o diagnosticarmos. Agora o composto sai do **volume próprio** do doador, por ponto fixo, e o posterior deixa de receber crédito de um glúteo que não treina.

**A arbitragem fechou na quinta rodada.** Os 92 eram o volume alocado, que é a própria capacidade de 92,2 séries por semana; e a diferença que sobrava vinha da **regra de composição**, não da tabela de indireto. O motor dava exatamente 3 séries a todo composto, o que produzia menos trabalho de composto do que as regras deste documento implicavam, e portanto menos indireto. Com a regra nova — 2 a 4 séries por exercício — os números acima são os que valem, e os três níveis saem do mesmo método.

**O glúteo: medido, e a sua hipótese estava certa.** A demanda direta dele é 1 e 4, o que você considerou correto — agachamento, afundo e stiff já treinam glúteo em comprimento longo —, então ele fica dispensado da exigência de isolamento em alongamento: o segundo exercício dele pode ser elevação pélvica ou abdução.

O diagnóstico do catálogo achou o defeito. **Afundo com Barra está etiquetado como glúteo**, não como quadríceps, e por isso vira a âncora de glúteo com 5 séries por sessão, 10 por semana — era essa a contradição que você viu entre 10 compostos e 1,2 de crédito. Rodando o avançado com alvo de glúteo zero, o composto de quadríceps **não sobe**, fica em 6: o afundo simplesmente desaparece do programa, em vez de voltar como quadríceps.

| exercício | etiqueta hoje | deveria ser |
| --- | --- | --- |
| Agachamento Livre com Barra | LEGS, GLUTES | quadríceps, creditando glúteo 0,5 |
| Afundo com Barra e com Halteres | LEGS, GLUTES | quadríceps, creditando glúteo 0,5 |
| Levantamento Terra | GLUTES, LEGS | posterior |
| Elevação Pélvica, barra e máquina | GLUTES | glúteo, correto |
| Cadeira Abdutora | GLUTES | glúteo, correto |
| Cadeira Adutora | GLUTES | adutores — e hoje credita 0,2 ao posterior |

O agachamento e o terra como "glúteo" explicam também por que o quadríceps e o posterior saem com pouco composto. A última linha é um achado à parte: cadeira adutora etiquetada como glúteo, creditando isquiotibial.

### Como alocamos

1. **Piso para todos.** Cada músculo treinado recebe primeiro um mínimo.
2. **O excedente vai por prioridade**, proporcional à importância do músculo no objetivo, com peso extra para o grupo em ênfase.
3. **Se nem o piso cabe**, o músculo é rebaixado; se nem isso, sai do programa, na ordem: antebraço, panturrilha, abdômen, glúteo, bíceps, tríceps, ombro. Músculo em ênfase nunca sai.

> **[AMENDADO PELA FATIA 8, `[REGRA]`, 2026-10-24]:** esta ordem de sacrifício deixa de fora
> peito, costas, quadríceps e posterior — e os pisos de todos os músculos somados (61
> séries/semana no iniciante) passam da capacidade em 100% dos perfis de 2–3 dias, mesmo depois
> de sacrificar tudo acima. Decisão do Rafael, medida nos 288 perfis de 2–3 dias antes de
> decidir entre quatro saídas (`docs/historico/motor-v3-fatia-8.md`): **quando a capacidade não
> comporta os pisos, depois de sacrificar na ordem acima, os grupos restantes (hoje: peito,
> costas, quadríceps e posterior) são reduzidos proporcionalmente ao peso de prioridade** — quem
> pesa mais perde menos — em vez de sair do programa inteiro de uma vez. Com isso,
> `soma(alocado) ≤ capacidade` passa a valer sempre, inclusive nesses quatro. Implementado em
> `alocar()` (oráculo e Kotlin, Fatia 3 amendada pela Fatia 8); não muda a rampa de carga.

### Os tetos (corrigidos pelos seus itens 11, 12 e 17)

```latex
\text{teto da sessão} = \min\big(\text{teto por tempo},\ \text{teto por nível} \times \text{fator de objetivo} \times \text{fator de idade}\big)
```

| teto | valor | origem |
| --- | --- | --- |
| por tempo | estimativa inicial de 11 a 18 séries conforme a faixa | seu item 17 |
| por nível | 16 iniciante · 18 demais | mantido |
| por músculo por sessão | 11 séries fracionadas | **limite fisiológico**, você aprovou |
| fator de objetivo | manter 0,6 · ganhar massa, perder gordura e saúde geral 1,0 | seu item 12 |
| fator de idade | 0,75 para 60+ iniciante ou no primeiro mesociclo | seu item 19 |

Os limites são independentes, então o teto é o menor deles (sua contradição 2). A forma anterior cortava duas vezes quem tem pouco tempo: min(11, 18) × 0,6 dava 7 séries por sessão para quem escolheu "manter" com 45 minutos.

A mudança de fundo é a sua: **o teto por nível era um substituto do tempo**, e agora o tempo tem a sua própria entrada. O único teto que defendemos como fisiológico é o de 11 por músculo. A tabela de faixas acima é apenas a estimativa inicial, e é a que usamos na simulação de cobertura; o teto que vale na geração sai da duração calculada do treino gerado.

### As três invariantes que viraram teste (seu item 13)

Suas condições viraram teste automatizado, porque nenhuma das nossas métricas pegou o bug — elas mediam médias de atendimento, não violação de regra:

1. Nunca sobra tempo suficiente para o menor exercício de um músculo removido. Estava escrita em séries; como o limite passou a ser tempo, você pediu a reescrita. E o indireto que estas invariantes verificam passa a sair do programa entregue, não da estrutura provisória: hoje o motor mede antes do corte, e por isso conta séries que não existem
2. Nenhum músculo ativo fica abaixo do piso sem ter sido rebaixado
3. Nenhuma justificativa contradiz a tabela de volume indireto

## Volume semanal por músculo

Séries efetivas por semana. **Braço subiu** pelo seu item 20; **abdômen foi a zero** pelo seu item 28.

| músculo | iniciante | intermediário | avançado | teto |
| --- | --- | --- | --- | --- |
| Peito | 8 | 14 | 18 | 22 |
| Costas | 10 | 16 | 20 | 25 |
| Ombro | 6 | 12 | 16 | 22 |
| Bíceps | 6 | **12** | 15 | 18 |
| Tríceps | 7 | **12** | 16 | 20 |
| Antebraço | 3 | 6 | 9 | 12 |
| Quadríceps | 6 | 12 | 16 | 20 |
| Posterior de coxa | 6 | 10 | 14 | 18 |
| Panturrilha | 5 | 9 | 12 | 16 |
| Glúteo | 4 | 8 | 12 | 16 |
| Abdômen | **0** | **0** | **0** | — |

A coluna do iniciante também é o **piso**. Abdômen fica em zero até o catálogo ter exercícios — reservar capacidade que não dá para preencher era o próprio defeito.

**Efeito do item 20:** com o alvo de 12, bíceps e tríceps passam de \~3 para **\~6 séries diretas por semana** no intermediário. Antebraço segue sem trabalho direto, exceto em ênfase.

### Como as outras variáveis modulam

**Objetivo.** Ganhar massa 1,0 · perder gordura 1,0 · manter 0,6 · saúde geral usa o piso. Os dois valores divergiam entre as seções (sua contradição 1). Pelo seu item 11, esses fatores agora multiplicam também o **teto da sessão**, não só o alvo semanal — antes um intermediário de 60 anos continuava com sessões de 18 séries.

**"Manter" foi corrigido** (seu item 12): teto de sessão próprio, bem menor, e descansos e faixas iguais aos de ganhar massa. Antes era a sessão mais longa do app, o oposto do que essa pessoa quer.

**Idade** (seu item 19). O fator de 40 a 59 anos foi **removido** — 10% era ruído e contradizia "idade de treino, não cronológica". Para 60+, as restrições valem para quem é iniciante ou está no primeiro mesociclo, e as alavancas são teto de sessão menor — fator 0,75, que leva 16 séries a 12 (sua contradição 3) —, máquinas no lugar de exercícios que carregam a coluna, e progressão de carga mais lenta. O avançado de 62 anos não fica mais preso em RIR 3 para sempre.

> **Respondido nesta rodada:** manter os dois em 12 e iguais. Seu argumento: o tríceps já recebe mais indireto — de peito e de ombro —, então o mesmo alvo dá a ele menos trabalho direto.

## Volume indireto

### `[REGRA]` A tabela de volume está em convenção TOTAL, para todos os músculos (2026-10-27)

**O alvo de cada músculo na tabela é volume EFETIVO = trabalho direto + indireto fracionado.**
Uma contabilidade só. O piso de trabalho direto (D-1, híbrido) é **restrição adicional**, nunca
uma segunda forma de contar.

Essa convenção não nasceu assim — ela foi alcançada em quatro passos, e é por isso que a tabela
esteve inconsistente consigo mesma por três fatias:

1. **`ARCH #26` (Fatia A, ratificada em 2026-10-07) nasceu em convenção DIRETA.** A fonte
   declarada é *"RP Strength (volume landmarks), Fitbod, Dr. Muscle/JuggernautAI"*, e o cabeçalho
   das linhas de lá nomeia o sinal de casamento — "CHEST (primary CHEST)",
   "GLUTES (primary GLUTES/HINGE)" —, ou seja, contados sobre exercícios em que o músculo é
   **motor primário**. Naquele momento não existia modelo de indireto nenhum.
2. **O motor v3 redefiniu a tabela como TOTAL, subtraindo o indireto dela** — e sem subir os
   números. A seção abaixo diz isso com estas palavras: *"Sem essa conta, o motor tratava a
   tabela de volume como alvo de trabalho direto."*
3. **O *item 20* corrigiu os BRAÇOS.** Bíceps foi para 6/**12**/15/18 e tríceps para
   7/**12**/16/20 (vinham do ARMS combinado 6/10/14/20), com o efeito registrado: *"com o alvo de
   12, bíceps e tríceps passam de ~3 para ~6 séries diretas por semana"*. Eles passaram a ser
   total **por intenção**.
4. **Esta rodada (2026-10-27) corrigiu ANTEBRAÇO, GLÚTEO e POSTERIOR**, os três que continuavam
   herdados em convenção direta enquanto o motor subtraía indireto deles:
   - **antebraço** e **posterior**: os pares que os consumiam (`costas → antebraço` 0,5,
     `posterior → antebraço` 0,5, `glúteo → posterior` 0,2) saíram da tabela de indireto **por
     falta de fonte**. O alvo deles passou a chegar inteiro como demanda direta, **sem subir
     número nenhum**. Medido: o antebraço saiu de **0 direto em todo nível** para 3/6/9.
   - **glúteo**: recebeu o tratamento do item 20 — alvo 4/8/12 → **8/10/12**, construído como
     indireto recebido (medido: 6/7/8) + direto desejado (2/3/4), com o avançado **intocado** em
     12. **É decisão de produto, não inferência da literatura**: a isenção do iniciante no híbrido
     continua valendo como regra geral (Iversen et al. 2021, Gentil — essas fontes embasam a
     **regra geral**, não esta exceção), e o glúteo é **exceção declarada** neste app porque é
     prioridade de mercado e zero exercício direto lê como programa incompleto. Descartado:
     8/9/10, o mínimo aritmético para cruzar o piso, que derrubava o avançado de 12 para 10 —
     contra a premissa de volume crescente com o nível.

Consequência nas referências: **alvo total 61/111/148 → 65/113/148** e **demanda direta
44/86/113 → 49/94/122**.

### `[REGRA]` O piso de trabalho direto nunca passa por cima da capacidade (2026-10-27)

Escrita **antes** de implementar o piso, de propósito. O piso direto é **financiado dentro do
orçamento**, em ordem decrescente de peso (`PESO_PRIORIDADE`); músculo que não couber **sai como
`MANUTENCAO` ou `NAO_TREINADO`**, honestamente, nunca com volume que o orçamento não comporta.

**O motivo é um defeito que já cometemos:** o `sobe_ao_piso_do_isolamento` da etapa [4] agia
**por fora do orçamento** — subia o volume ao piso do isolamento sem perguntar se cabia —, e foi
exatamente isso que produziu `entregue > capacidade` em 130/720 perfis, o sintoma que abriu a
Fatia 8. Um piso direto que repetisse esse desenho recriaria o mesmo defeito numa etapa mais
cedo, onde é mais difícil de ver. Piso é prioridade na fila do orçamento, não licença para
estourá-lo.

Esta decisão mudou mais o resultado dos programas do que qualquer outra.

**O motor conta meia série para o sinergista a cada série de composto.** Uma remada creditando 0,5 ao bíceps, um supino creditando 0,5 ao tríceps. O alvo **direto** do músculo pequeno passa a ser o alvo da tabela menos o que ele já recebeu de indireto.

| composto de… | credita a… |
| --- | --- |
| peito, ombro | tríceps 0,5 |
| costas | bíceps 0,5 · antebraço 0,5 |
| posterior de coxa | glúteo 0,5 · antebraço 0,5 |
| quadríceps | glúteo 0,5 |
| glúteo | posterior de coxa 0,2 |

### Por que isso importa tanto

Sem essa conta, o motor tratava a tabela de volume como alvo de trabalho **direto**. O resultado medido na versão anterior: o tríceps recebia **139%** do alvo e o antebraço **121%**, enquanto as costas ficavam em **60%** e o quadríceps em **51%**.

O motor estava gastando o recurso mais escasso — espaço na sessão — nos músculos que menos precisavam dele, porque não enxergava que toda remada já é trabalho de bíceps e todo supino já é trabalho de tríceps.

Na prática, para um intermediário treinando quatro dias, isso significa que **bíceps e tríceps recebem cerca de 6 séries diretas por semana** — dois exercícios para cada, não um — e o restante do alvo deles vem das puxadas e empurradas. O antebraço costuma não receber nenhum trabalho direto.

### As duas exceções, e o motivo delas

**Ombro não recebe desconto.** O supino dá trabalho real ao deltoide anterior, mas nosso alvo "ombro" é o deltoide inteiro. Descontar o indireto do supino subserviria lateral e posterior, que são justamente as porções que precisam de trabalho isolado.

**Glúteo → posterior fica em 0,2**, não 0,5. Elevação pélvica e abdução treinam isquiotibial muito pouco: o quadril já está estendido e o isquio em encurtamento. Medimos o efeito dessa exceção — tratá-la como 0,5 derruba o atendimento do posterior de 86% para 76%.

> A pergunta do coeficiente único de 0,5 saiu daqui — você já a respondeu na primeira rodada (seu trecho antigo 2). O 0,5 único segue como primeira versão. O coeficiente por exercício, que seria o certo — supino com pegada fechada e crucifixo não entregam o mesmo tríceps —, fica para quando a curadoria do catálogo permitir.

## Frequência, divisão e ordem dos dias

### A divisão sai do número de dias

| dias | opções (★ = recomendada) |
| --- | --- |
| 2 | Full Body ★ |
| 3 | Full Body ★ · Upper/Lower/Full |
| 4 | Upper/Lower ★ · Full Body |
| 5 | Upper/Lower + Push/Pull/Legs ★ |
| 6 | Push/Pull/Legs 2× ★ · Arnold |

**Push/Pull/Legs saiu das opções de 3 dias** (seu item 14). Você estava certo e o argumento era nosso: dá frequência de 1× por músculo na semana, que é exatamente a razão pela qual excluímos o bro split. Era contradição interna.

### Frequência efetiva

Não fatiamos **composto** abaixo de três séries por exercício. Um músculo com 4 séries de composto na semana é treinado em um dia, não em dois.

Pelo seu item 21, **o isolamento agora tem piso de duas séries**, não três. Isso reverte uma regra que tínhamos ratificado internamente. Seu argumento: isolamento em 2 séries é prescrição comum, não precisa de aquecimento, e o piso de 3 fazia ombro e panturrilha sumirem nos cenários apertados.

### Dias da semana

| dias | calendário |
| --- | --- |
| 2 | segunda, quinta |
| 3 | segunda, quarta, sexta |
| 4 | segunda, terça, quinta, sexta |
| 5 | segunda, terça, quinta, sexta, sábado |
| 6 | segunda a sábado |

Você aprovou os seis dias seguidos, então o calendário fica. E a ordem dos dias do Push/Pull/Legs — dia de puxar na véspera do dia de pernas — **resolveu-se sozinha pelo seu item 8**: com a âncora de costas passando a ser apoiada, o acúmulo lombar entre os dois dias deixa de existir, sem precisarmos de modelo de fadiga entre sessões.

## Composição da sessão

### Quantos exercícios por músculo

Sai do volume do músculo na sessão, com piso de 3 séries no composto e **2 no isolamento** (seu item 21). Máximo de 3 exercícios por músculo por sessão.

### Quais papéis

| exercícios | composição | raciocínio |
| --- | --- | --- |
| 1 | âncora (composto pesado) | com pouco volume, o composto entrega mais estímulo por série |
| 2 | âncora + isolamento em alongamento | cobre a parte da curva que o composto não alcança, com fadiga baixa |
| 3 | âncora + acessório + isolamento | aí cabe um segundo composto, em outra faixa e outro ângulo |

Você aprovou essa composição, então ela fica como está. Em compensação, **o perfil de composição por objetivo saiu**: existia um perfil chamado "força" sem que houvesse objetivo de força, que era a incoerência do seu item 16.

### A âncora de costas mudou (seu item 8)

**A âncora padrão de costas passa a ter apoio** — peito apoiado, máquina ou cabo sentado. Remada curvada com barra fica restrita ao avançado, e nunca em dia vizinho a terra ou stiff pesado.

Essa foi a correção de melhor custo-benefício da sua lista: resolve técnica difícil sem supervisão, carga lombar e a ordem dos dias do Push/Pull/Legs, tudo numa troca de exercício padrão.

### Ombro (seu item 9)

Aplicamos os dois mínimos que você indicou:

- **O isolamento de ombro é sempre elevação lateral.** Não há escolha aleatória nessa posição.
- **O desenvolvimento entra com no máximo (10 − séries de composto de peito do dia) séries. Se der menos de 3, a vaga vira elevação lateral, e o volume de ombro que sobrar vai para um segundo isolamento, de deltoide posterior. E no máximo um empurrar com barra livre por sessão: se o supino é com barra, o desenvolvimento é com halter ou máquina. O 10 é ponto de partida a validar. Duas versões anteriores desta regra estavam erradas: o limiar de 6 séries da terceira rodada, e a formulação sem número da quinta, que tiraria o desenvolvimento de todo dia de upper e de full body.**

O ideal que você descreveu — dividir o deltoide em três alvos, com a remada creditando o posterior — virou `[PENDENTE]`: exige recurar o catálogo inteiro por porção do deltoide.

### Ordem de execução (seu item 22)

| situação | onde o músculo em ênfase entra |
| --- | --- |
| músculo grande | abre a sessão |
| músculo pequeno, dia **sem** composto que dependa dele | abre a sessão |
| músculo pequeno, dia **com** composto que dependa dele | primeiro isolamento, depois dos compostos |

Antes, bíceps em ênfase abria qualquer sessão, inclusive a de puxada. Seus dois argumentos — bíceps cansado piora a remada, tríceps cansado sob uma barra de supino é risco — fecharam a questão.

## Priorização: agora é uma fase

Mudança grande, vinda do seu item 23. **A ênfase deixou de ser permanente e virou uma fase de dois mesociclos.**

### Como funciona

|  | antes | agora |
| --- | --- | --- |
| duração | permanente até o usuário mudar | **dois mesociclos, com saída opcional depois do primeiro**; no fim o app pergunta se quer renovar |
| demais grupos | volume normal | **no piso**, durante a fase |
| limite | +6 séries para qualquer músculo | **+3 para pequeno, +6 para grande; no máximo um grupo grande por fase** |
| grupos | até 2 | até 2, só intermediário e avançado; com dois grupos o orçamento se divide |

Seu argumento resolveu o problema que nós tínhamos medido e não sabíamos corrigir: a ênfase não entregava nada em **37% dos casos** por falta de espaço na sessão. Baixar os outros grupos ao piso durante a fase **abre exatamente o espaço que faltava** — e, por ser temporário, não condena os demais a meses de manutenção.

O limite por tamanho corrige a desproporcionalidade: +6 séries sobre antebraço (base 6) eram +100% e batiam no teto; sobre costas (base 16) eram +38%.

Três ajustes desta rodada. **O limite é total, não por grupo** (sua contradição 5): por grupo, dois grandes somariam +12 séries, mais agressivo que a versão que você reprovou — então a fase admite no máximo um grupo grande, e com dois grupos o orçamento se divide. **A fase passou a dois mesociclos**, pelo seu veredito: com o deload na semana 4, um ciclo deixava só três semanas de trabalho. E **puxar nunca cai abaixo de empurrar**, pela sua resposta: numa ênfase de peito ou de ombro, costas continua no volume normal em vez de ir ao piso.

### As outras duas alavancas

- **Posição** — pela regra nova do item 22 (acima)
- **Proteção** — o músculo em ênfase nunca é sacrificado nem rebaixado

### Quando ainda assim não couber

O motor avisa, e o aviso é acionável: *"para priorizar costas, cinco dias dariam a elas uma terceira sessão"*.

> **Respondido nesta rodada:** dois mesociclos, com saída opcional depois do primeiro. E nenhum grupo precisa, pela fisiologia, ser poupado do piso — mas o volume de puxar fica pelo menos igual ao de empurrar, então numa ênfase de peito ou de ombro, costas continua no volume normal. Você respondeu a nossa dúvida das oito semanas com números: o piso do peito é 57% do volume do intermediário, e a massa se mantém com um terço ou menos desde que a carga se mantenha. Os dois ciclos ficam com os demais grupos no piso.

## Prescrição de cada série

| objetivo | composto pesado | composto acessório | isolamento |
| --- | --- | --- | --- |
| ganhar massa | 6-9 · 120 s · RIR 2 | 8-12 · 90 s · RIR 1 | 12-16 · 60 s · RIR 1 |
| **perder gordura** | 6-9 · **120 s** · RIR 2 | 8-12 · **90 s** · RIR 1 | 12-16 · **60 s** · RIR 1 |
| manter | 6-9 · 120 s · RIR 2 | 8-12 · 90 s · RIR 2 | 12-16 · 60 s · RIR 2 |
| saúde geral | 8-12 · 120 s · RIR 3 | 10-14 · 90 s · RIR 3 | 12-18 · 75 s · RIR 3 |

**Perder gordura passou a ser idêntico a ganhar massa** (seu item 18). Havíamos encurtado o descanso por "densidade"; seu argumento encerrou: em déficit o objetivo é manter carga e repetições, descanso curto derruba as duas, e o gasto extra é irrelevante perto da dieta.

### Teto de segurança na barra livre (seu item 1)

**RIR mínimo de 2 em todo exercício com barra livre que carregue a coluna ou passe sobre a cabeça**, em qualquer semana do mesociclo. A regra vale **por propriedade, não por lista**, e isso é correção da quinta rodada: a enumeração anterior — supino, agachamento, desenvolvimento, terra, stiff — deixava de fora 12 dos 20 exercícios com barra do catálogo, e três deles são justamente de risco: **Afundo com Barra**, **Remada Curvada com Barra** e **Remada Cavalinho**. Os três carregam a coluna ou dependem de técnica perto da falha, e os três passavam sem piso de RIR. A mesma propriedade decide a troca por halter ou máquina para o iniciante. O mesociclo também não aperta mais o RIR em exercício nenhum: com progressão dupla, o RIR fica fixo dentro do ciclo.

**[SATISFEITA PELA CURADORIA, 2026-10-24]** Troca de barra por halter/máquina para o iniciante: não há regra no motor. Os 8 exercícios aprovados com a propriedade (V67) são todos `level` INTERMEDIATE/ADVANCED -- o filtro de nível do catálogo já garante que nenhum iniciante os recebe, então a troca nunca teria candidato para disparar. Ver `docs/regras/01-motor-de-geracao.md`, Fatia 7.

Seu argumento: o acidente mais grave de academia é ficar preso sob a barra, quem treina sozinho estima RIR mal, e a semana 3 ainda apertava.

**Modo conservador** — RIR nunca abaixo de 3 — para iniciante, saúde geral e 60+ no primeiro mesociclo. O "sim" do PAR-Q deixou de ser gatilho, porque a resposta não é mais armazenada — é o custo declarado de não armazenar. No lugar dele entra uma pergunta de preferência no cadastro, "quer começar no modo leve?", que guarda a escolha e não a condição. Ela vale por um mesociclo, e no fim o app pergunta se a pessoa quer continuar — sem prazo, uma preferência deixaria alguém em RIR 3 para sempre.

### Aquecimento (seu item 6)

Essa ausência passou por todas as nossas análises sem ser notada, e era item de segurança.

**Rampa automática antes do primeiro exercício de cada padrão pesado:** \~50% × 8, 70% × 4, 85% × 2 da carga de trabalho. A rampa **não conta no volume**, mas **conta no tempo da sessão** — e isso agora importa, porque o tempo voltou a ser entrada.

### Carga e progressão (seus itens 2 e 27)

**A primeira exposição virou uma série de calibração com carga leve**, em que o usuário informa o RIR percebido. A pergunta direta — *"com quanto peso você faria 10 deixando 2?"* — ficou só para quem marca "já faço esse exercício" e não vem de uma pausa. Acima de 1 mês parado, a série de calibração passa a ser obrigatória (seu risco 1 desta rodada): a carga informada seria a de antes da pausa, e a pergunta direta pularia a redução da regra de retorno. Seu argumento: quem nunca fez não sabe responder. O ajuste desta rodada (sua correção P1): se o RIR informado na calibração for 4 ou mais, a carga sobe na hora e a pessoa faz outra série, até 2 ou 3 séries. Sem isso, quem calibra com 20 kg e aguenta 60 kg leva umas 16 sessões subindo 2,5 kg por vez.

**A progressão passou a ser dupla, com limite:**

1. A carga sobe apenas quando todas as séries batem o topo da faixa no RIR alvo
2. Cada aumento é o **menor incremento disponível** no equipamento — exceto nas primeiras exposições ao exercício, em que saltos maiores são permitidos (sua correção P1)
3. Logo depois de um salto de carga, repetições abaixo da faixa são aceitas enquanto o RIR estiver no alvo (sua correção P2) — sem isso, o halter que pula de 8 para 10 kg, +25%, entra em loop de subir e devolver
4. Se a primeira série do dia sair mais difícil que o alvo, as seguintes reduzem a carga em **3 a 5% por ponto de RIR abaixo do alvo** (sua correção P3)
5. No isolamento não se usa 1RM estimado — só progressão dupla

Antes não havia regra nem limite: um 1RM estimado a partir do RIR relatado podia gerar um salto grande de carga a partir de uma única série boa ou de um RIR mal informado.

## Progressão e mesociclo

### O problema que isto resolve

A versão anterior do motor sorteava os exercícios a cada geração. Medimos: **cinco gerações do mesmo perfil produziam cinco programas diferentes**, e em 76% dos pares pelo menos um exercício principal mudava. Pior, uma regra impedia o mesmo exercício de aparecer duas vezes na semana.

O efeito prático: **o usuário nunca agachava duas vezes na mesma semana com o mesmo agachamento**, e nunca via a mesma barra na semana seguinte. Sem exposição repetida ao mesmo exercício, carga e repetições não são comparáveis, e sem comparação não existe sobrecarga progressiva. O que a pessoa recebia era o retrato de uma semana, não um programa.

### Como fica

**Cada posição de cada sessão tem o seu exercício, e ele se repete semana após semana** dentro do mesociclo. A sessão A tem a mesma remada apoiada toda semana; a sessão B pode ter uma puxada, também toda semana. São duas linhas de progressão independentes, e as duas são rastreáveis.

Isso permite variedade de ângulo dentro da semana sem perder a comparação entre semanas.

### O mesociclo (seus itens 15, 16, 24 e 7)

**Fixo em 4 semanas**, e **as séries não sobem mais dentro do ciclo**:

| semana | o que muda |
| --- | --- |
| 1 a 3 | séries fixas; progride **carga e repetições** |
| 4 | deload: metade do volume, carga mantida |

Seu item 15 apontou a contradição: a série extra das semanas 2 e 3 ou estourava o teto — "a única defesa contra lixo de volume" — ou simplesmente não acontecia. Escolhemos a sua primeira saída: séries fixas, progressão por carga.

**O iniciante tem regra própria** (seu item 24): nos primeiros 2 a 3 meses, âncoras fixas e **deload só quando a carga estagnar ou cair em sessões seguidas**. A ordem importa: estagnou → deload com a mesma âncora → diagnóstico → só então a troca. Trocar antes do diagnóstico compararia exercícios diferentes, e era conflito com a própria regra de promoção. O iniciante progride quase toda sessão; deload programado desperdiçava 25% do tempo dele, e trocar a âncora atrapalhava o aprendizado motor.

### Retorno após pausa (seu item 7)

Não existia, e era furo real: como o mesociclo avança por conclusão, quem sumia três semanas voltava direto no pico de volume.

| pausa | o que o motor faz |
| --- | --- |
| até 2 semanas | segue normalmente |
| 2 a 4 semanas | volta à semana 1 com carga um pouco menor |
| mais de 4 semanas | novo mesociclo, carga recalibrada |

E a pergunta de "há quanto tempo parou" no cadastro faz o mesmo para quem chega depois de uma pausa longa — ela rebaixa o nível (item 5). As faixas novas (sua correção S3) mapeiam direto nesta tabela: até 1 mês volta à semana 1 com carga menor; 1 a 6 meses começa um mesociclo novo, recalibrado; mais de 6 meses rebaixa a iniciante.

## Quando não cabe

A cascata, em ordem:

1. Capacidade semanal = dias × teto da sessão (que agora sai do tempo informado)
2. O alvo é reescalado por prioridade
3. Quem não cabe é rebaixado: um único exercício no piso de séries do papel — 3 no composto, 2 no isolamento (sua contradição 6, que estava sem definição)
4. Se nem o piso cabe, o músculo sai, na ordem declarada
5. Dentro do dia, cada músculo recebe pelo menos um exercício antes de qualquer um receber o segundo

O teto da sessão deixou de sair de uma tabela fixa de faixas. O motor calcula a duração estimada do treino que acabou de gerar — descanso prescrito, rampas de aquecimento e trocas de exercício incluídos — e corta até caber, como você indicou. **Com uma condição nossa, que você não pediu:** o corte percorre esta mesma cascata, na ordem declarada, e nunca é uma poda no fim. Aparar às cegas no fim era o defeito original do motor — ele voltaria inteiro se "cortar até caber" não tivesse ordem. Você aceitou a condição e apontou a dependência circular que ela cria: a duração depende dos exercícios escolhidos, e os exercícios dependem do teto. Fica com o laço que você indicou — gera com um teto, estima a duração e, se passar do tempo, reduz o teto em 1 série e refaz a alocação inteira. Como o teto só desce, o laço termina. E o arredondamento de séries alocadas para séries de exercício segue a mesma ordem de prioridade. Hoje não segue, e é a poda no fim de novo: no avançado a 60 min, costas perde 4,9 séries e panturrilha ganha 1,7, invertendo a hierarquia inteira na última etapa.

### O exemplo que você derrubou

O documento anterior trazia um programa de 2 dias como demonstração de degradação correta. **Você mostrou que ele violava as nossas próprias regras, e a medição confirmou** — com três violações simultâneas, não uma:

- sobravam 3 séries por sessão enquanto quatro músculos eram removidos por "falta de capacidade"
- peito, costas e ombro ficavam **abaixo do próprio piso** sem terem sido rebaixados
- a justificativa de que ombro e glúteo estavam "cobertos pelo indireto" contradizia a nossa própria tabela, em que ombro não recebe indireto e glúteo recebe

O exemplo saiu do documento até o bug estar corrigido. Suas três condições viraram teste automatizado, que roda a cada alteração do motor.

A lição para nós: as nossas métricas mediam **médias de atendimento**, e um bug de invariante não aparece numa média. Você achou lendo e fazendo conta.

### O aviso ao usuário (seu item 25)

O texto anterior dizia que com dois dias *"não dá para ganhar massa"*. Era falso — dá, mais devagar — e o gatilho usava a porcentagem sobre o alvo inflado do item 10.

|  | antes | agora |
| --- | --- | --- |
| texto | "não dá para ganhar massa" | "dá para ganhar, mais devagar; 3 dias aceleraria" |
| gatilho | menos de 40% do alvo | **algum músculo grande abaixo do piso** |

O gatilho novo é melhor porque aponta para o que de fato está faltando, em vez de um percentual abstrato.

## Proporção composto × isolado

Medimos o que o motor produz hoje, porque essa proporção é um critério comum de avaliação de programa. Percentual de **séries compostas** sobre o total da semana:

|  | 3 dias | 4 dias | 5 dias | 6 dias |
| --- | --- | --- | --- | --- |
| iniciante | 69% | 74% | 74% | 74% |
| intermediário | 68% | 70% | 51% | 50% |
| avançado | 69% | 64% | 40% | 40% |

**Estes números estão pendentes de remedição** (seu trecho antigo 4). São da simulação anterior às suas correções, e a coluna de 2 dias saiu porque vinha do motor com o bug do seu item 13. Só voltam a valer como evidência depois de o motor ser implementado com as quatro rodadas aplicadas, inclusive a regra de composição nova, que é justamente o que decide esta proporção. Guardamos a tabela porque o argumento que vem a seguir não depende dela.

A referência que costuma circular é de 70 a 80% composto para hipertrofia, 85% para iniciante. Nós ficamos dentro dessa faixa em algumas configurações e bem abaixo em outras — e **não tratamos a proporção como alvo**, por dois motivos medidos.

**Primeiro: a tabela de volume torna 70-80% difícil por aritmética.** Bíceps, tríceps, antebraço e panturrilha não têm composto-âncora, e juntos representam **35% do alvo semanal** em qualquer nível. Publicamos 37%: o abdômen entrava na conta e agora está em zero (seu trecho antigo 4). E um ajuste contra nós mesmos: a conta é sobre o **alvo**, não sobre a demanda direta — contado o indireto, a fatia sem âncora cai para cerca de 25% das séries diretas. Ou seja, 75% composto é um limite de fronteira, não impossível como dissemos.

**Segundo: a faixa é melhor atendida pelos piores programas.** A coluna de 2 dias, que tinha o melhor número da tabela — 77 a 87% composto —, saiu daqui porque vinha do motor com bug. Mas o efeito é real e reaparece em toda faixa de tempo curta: a proporção fica ótima justamente porque não sobrou espaço para isolamento. Era o programa em que seis músculos eram sacrificados por falta de capacidade — e essa frase também descreve o exemplo com bug, então só volta quando houver um caso medido no motor corrigido.

Nossa conclusão é que a proporção é **sintoma, não alavanca**: ela mede quanto espaço sobrou depois dos compostos. Persegui-la levaria o motor a cortar braço e panturrilha de propósito para melhorar a estatística.

Pretendemos usá-la como **faixa de validação** — um alarme quando o programa sai muito fora do esperado para aquele nível — e não como entrada do planejamento.

> A pergunta dos 40% de compostos para o avançado saiu daqui: você já a respondeu na primeira rodada (seu trecho antigo 4). O que fica no lugar dela é a remedição da tabela, que só acontece depois de o motor existir com as duas rodadas aplicadas.

## Pendentes — onde ficamos em dúvida

Três correções da sua primeira revisão nós aceitamos sem conseguir fechar como implementar. Você respondeu as três, e as respostas estão registradas abaixo. Discordamos de um critério do terceiro.

### `[PENDENTE 1]` Lista de equipamentos no lugar de casa/academia (seu item 26)

Concordamos que "casa" indo de "não tenho nada" a "garagem com rack" é informação perdida. É a maior mudança de escopo da sua lista: mexe no questionário, no filtro do catálogo e na curadoria inteira.

**Sua resposta:** lançar só com academia. A lista de equipamentos é condição de lançamento apenas para o treino em casa, e quando casa abrir, abre já com a lista, sem construir o "casa" genérico. Para academia, você indicou um botão "não tenho este equipamento", que resolve as academias de condomínio. Aplicado: "casa" sai marcada "em breve" na primeira tela — não no fim do cadastro, como você apontou na contradição 11 — e o botão entrou no escopo de lançamento.

### `[PENDENTE 2]` Deltoide em três alvos (seu item 9)

Aplicamos os dois mínimos. O ideal exige recurar o catálogo por porção do deltoide e mudar a tabela de volume indireto para a remada creditar o posterior.

**Sua resposta:** os dois mínimos bastam para lançar, desde que o limite de supino do dia tenha número — e agora tem: 6 ou mais séries de composto de peito. Dividir o deltoide em três é questão de precisão, não de segurança, e pode esperar.

### `[PENDENTE 3]` Reavaliação do nível pelo histórico (seu item 5)

**Resolvido na terceira rodada, contra nós.** A nossa contraproposta — promover por progressão demonstrada — estava errada, pelo motivo que você deu: progressão demonstrada é justamente o que define um iniciante. Quem ainda sobe de sessão para sessão é exatamente quem não deve ser promovido, e com 6 meses e 80% de adesão quase todo mundo demonstra progressão — o critério viraria promoção por tempo, que é o que acabávamos de proibir na entrada.

**A regra que fica é a sua: o deload como diagnóstico**, usando um mecanismo que a regra do iniciante já tem.

1. A carga estagna e o motor aplica o deload.
2. Depois do deload a carga volta a subir e estagna de novo em valores parecidos: os ganhos fáceis acabaram, e o iniciante é promovido.
3. Depois do deload a carga não volta a subir: é recuperação, não nível. Não há promoção, e o problema está fora do programa.

Somam-se o tempo mínimo, a adesão e a trava do escalonamento de dor, que já estavam aqui.

**Nossa adição ao caso 3:** o app precisa dizer alguma coisa. Depois de dois deloads consecutivos sem retomada — retomada é a carga subir em pelo menos uma âncora nas três sessões seguintes ao deload —, ele aponta sono, alimentação e estresse e sugere procurar um profissional. Sem isso a pessoa deloada para sempre, em silêncio, e nunca entende por quê.

**Promoção a avançado — furo nosso, achado por você.** A correção S2 fechou a entrada pelo cadastro e não abriu saída nenhuma: o nível avançado não era alcançável. Fica a mesma lógica medida por mesociclo — 1RM estimado das âncoras comparado entre mesociclos consecutivos; dois ciclos sem ganho, com adesão mantida e sem dor em escalonamento, promovem. Com uma precisão: aí o mecanismo não é o mesmo, porque para o intermediário o deload é programado na semana 4 e não disparado por estagnação — não existe "deloadar e ver se sobe". Mais dois ajustes seus: as âncoras ficam congeladas durante os dois ciclos de comparação, senão o 1RM estimado compara exercícios diferentes; e "sem ganho" tem a mesma ambiguidade que achamos no iniciante, resolvida pelo sinal que o motor já tem — poucas reduções de carga no dia com estagnação promovem, muitas indicam recuperação e geram aviso sem promoção.

---

## As novas decisões que ninguém revisou

Suas correções nos obrigaram a escolher números e textos que não existiam antes, nas três rodadas. Você escreveu que os valores marcados como "seu item" são pontos de partida e não números validados, e que esta tabela continua verdadeira. Ela está atualizada:

| o que | valor que escolhemos | de onde veio |
| --- | --- | --- |
| bíceps e tríceps, intermediário | 12, igualados | seu item 20; aprovado na 2ª rodada |
| fator de objetivo "manter" | 0,6 | escolha nossa; número aprovado, lugar na fórmula corrigido |
| duração da fase de ênfase | 2 mesociclos, demais grupos no piso nos dois | seu veredito da 2ª rodada; a nossa dúvida foi respondida na 3ª |
| limite de ênfase | +3 pequeno, +6 grande, um grande por fase | seu item 23 + sua contradição 5 |
| ênfase com dois grupos | +3 séries para cada | seu ponto de partida na 3ª rodada |
| teto da sessão | duração estimada do treino gerado, cortada pela cascata, com laço de teto decrescente | seu veredito da 2ª rodada + seu laço da 3ª; a ordem do corte é nossa |
| fator de idade 60+ | 0,75 sobre o teto por nível | seu ponto de partida na 2ª rodada; segue sem validação profissional |
| limite de supino para a âncora de ombro | 6 séries de composto de peito no dia | seu ponto de partida na 2ª rodada; segue sem validação profissional |
| piso do rebaixamento | 3 séries no composto, 2 no isolamento | seu ponto de partida na 2ª rodada; segue sem validação profissional |
| redução de carga no dia | 4%, arredondado para o incremento mais próximo; se der zero, reduz repetições | seu ponto de partida na 3ª rodada |
| salto dentro da calibração | RIR 4 ou mais sobe na hora, até 2 ou 3 séries, **sem teto de 10%** | seu ponto de partida na 3ª rodada, com a nossa discordância sobre o teto |
| saltos entre sessões, primeiras exposições | até 10% por sessão nas 3 primeiras, enquanto o RIR ficar 2 acima do alvo | seu ponto de partida na 3ª rodada |
| tolerância depois de um salto | até as repetições voltarem à faixa; se não voltarem em 3 sessões, a carga volta | seu ponto de partida na 3ª rodada |
| rampa de aquecimento | 3 séries no primeiro composto de barra da sessão, 1 nos seguintes | **contraproposta nossa** ao seu "3 em toda barra livre" |
| mesociclo | fixo em 4 semanas | seu item 16 deu as duas opções |
| âncora fixa do iniciante | 2 a 3 meses; troca por estagnação ou dor, não por data | seu item 24 + seu veredito da 2ª rodada |
| regiões de dor no cadastro | 5, janela de 12 meses | seu item 3 + sua correção S4 |
| regiões de dor no cadastro | **[VENCIDO, 2026-10-23]** -- ver nota na tabela de Segurança acima (decisão de 21/10, V65) | registrado ao abrir a Fatia 7 |
| promoção a intermediário | tempo + adesão + deload como diagnóstico | seu pendente 3, corrigido por você na 3ª rodada |
| promoção a avançado | 1RM estimado sem ganho em dois mesociclos | sua lógica da 3ª rodada, medida por mesociclo |
| PAR-Q | autoavaliação, sem armazenar resposta | sua sugestão, confirmada na 3ª rodada |
| gatilho do modo conservador | pergunta de preferência: "quer começar no modo leve?" | seu ponto de partida na 3ª rodada |
| demanda direta | recalculada da especificação: 41 · 83 · 111 | conta nossa desta rodada; **conflita com a simulação no avançado** |

## O que o motor ainda não faz

Lista honesta do que ficou de fora depois das suas quatro revisões.

**Fadiga acumulada entre sessões.** Continua sem modelo. Mas o seu item 8 — âncora de costas com apoio — resolveu o caso concreto que mais nos preocupava, sem precisar do modelo.

**Autorregulação no dia.** Não existe "hoje não estou bem, reduza".

**Objetivo de força.** Continua fora. Pelo seu item 16, tiramos também o perfil de composição chamado "força" que existia sem objetivo correspondente.

**Deltoide por porção, trapézio e eretores.** Ver `[PENDENTE 2]`.

**Abdômen.** Alvo zerado até o catálogo ter exercícios, como você indicou.

**Panturrilha com dois exercícios.** Limita o volume semanal possível.

**Treino em casa.** Com o `[PENDENTE 1]` resolvido, o lançamento sai só com academia: "casa" aparece marcada "em breve" na primeira tela, e a geração em casa continua bloqueada em vez de entregar programa com posição vazia.

**Individualização por resposta ao treino.** Duas pessoas com o mesmo cadastro recebem o mesmo primeiro programa. A camada que aprende com o histórico está desenhada; a progressão dupla do seu item 2 é o primeiro pedaço dela a existir de verdade.

## O que ficou em aberto

Consolidado das cinco rodadas, na estrutura que você propôs. Fechados na quinta: a regra do ombro, a demanda direta de 44,0 · 86,0 · 113,0 com o avançado em 58% em 4 × 60, o diagnóstico do glúteo, a rampa sem dupla contagem e a faixa de 45 minutos mantida.

### Antes de lançar

- [ ] **Etiquetas do catálogo:** Agachamento e Afundo (barra e halteres) como quadríceps, creditando glúteo 0,5; Terra como posterior; Cadeira Adutora como adutores, sem vaga e sem crédito. Equipe.
- [ ] **Auditoria das regras de segurança contra o catálogo:** listar onde cada uma dispara — RIR mínimo 2 em barra livre, troca de barra para iniciante, rampa de padrão pesado, substituições por dor. **Já rodada para a primeira: 12 dos 20 exercícios com barra ficavam fora da enumeração, entre eles Afundo com Barra, Remada Curvada e Remada Cavalinho.** Equipe.
- [ ] **Tabela canônica do catálogo:** padrão, equipamento, músculo primário e créditos de indireto de cada exercício. Profissional.
- [ ] **Promoção a avançado:** validar o critério escrito. Equipe.
- [ ] **LGPD:** consentimento específico e destacado para as regiões de dor e o registro do botão de dor. Advogado.
- [ ] **Regulamentação:** verificar se o app precisa de responsável técnico com CREF (Lei 9.696/98). Advogado.
- [ ] **Revisão profissional:** validar o motor e os números da tabela de decisões, incluindo o 10 da regra do ombro. Profissional.
- [ ] **Versão limpa do documento** para esse profissional, com o histórico das rodadas num apêndice. Equipe.

### Durante a implementação

**Fonte única da verdade**

- [ ] Indireto, invariantes, piso, aviso, cobertura e checagens de segurança saem do **programa entregue**; nada lê `d1` depois que `d2` existe.
- [ ] Recalcular tudo isso depois de cada troca pelos botões "senti dor" e "não tenho este equipamento".
- [ ] Progressão dupla e 1RM estimado guardados **por exercício**, não por vaga.
- [ ] Proteções verificadas no entregue: ênfase nunca sacrificada, puxar ≥ empurrar, piso dos músculos grandes e gatilho do aviso.
- [ ] Cobertura medida no programa entregue, não por capacidade ÷ demanda.

**Testes**

- [ ] Contagem de disparos por regra na simulação; **regra de segurança com zero disparos é investigada.**
- [ ] Teste diferencial automático: modelo à mão e motor no mesmo catálogo, falhando com diferença acima de 0,5 série por músculo.
- [ ] Exemplo de 2 dias reconstruído como primeiro teste de regressão.
- [ ] Remedição da proporção composto × isolado com o motor corrigido.

**Regras já decididas, a implementar**

- [ ] De 2 a 4 séries por exercício, com os limiares de 1, 2 e 3 exercícios.
- [ ] Regra do ombro: teto de (10 − composto de peito do dia), elevação lateral abaixo de 3, sobra para deltoide posterior, e um empurrar com barra por sessão.
- [x] RIR mínimo 2 **por propriedade** -- `rirMinimoSeguranca` (V67), coluna aprovada por nome. Troca de barra do iniciante: **satisfeita pela curadoria, sem regra no motor** -- ver nota acima e `docs/regras/01-motor-de-geracao.md` (2026-10-24).
- [ ] Teto por sessão aplicado no motor.
- [ ] Arredondamento de alocado para entregue na ordem de prioridade.
- [ ] Laço do corte por tempo, com o teto só descendo.
- [ ] Invariantes verificadas em tempo, não em séries.
- [ ] Glúteo dispensado do isolamento em alongamento.
- [ ] Aviso depois de dois deloads consecutivos sem retomada.
- [ ] Âncoras congeladas durante os dois ciclos de comparação da promoção.
- [ ] Ênfase com dois grupos: +3 séries para cada.
- [ ] Definição de "padrão pesado" e rampa de uma série para máquina e cabo.
- [ ] Arredondamento da redução de 4% quando o incremento não existe.
- [ ] Botão "não tenho este equipamento".
- [ ] Pergunta de preferência do modo leve, valendo por um mesociclo.

### Depois do lançamento

- [ ] Treino em casa, já com a lista de equipamentos.
- [ ] Deltoide em três porções, com a remada creditando o posterior.
- [ ] Exercícios de abdômen no catálogo (alvo hoje em zero).
- [ ] Mais exercícios de panturrilha.
- [ ] Coeficiente de volume indireto por exercício.
- [ ] Autorregulação no dia.
- [ ] Fadiga acumulada entre sessões, com trapézio e eretores como alvos.
- [ ] Objetivo de força.
- [ ] Individualização pela resposta ao treino.

---

### Como responder

Comente direto nos trechos. Discordância direta continua valendo mais que concordância educada — inclusive discordância sobre como aplicamos as suas próprias correções.

E a sua observação final, registrada aqui porque é a que mais mudou o nosso método: nada disto substitui a revisão de um profissional de Educação Física com CREF antes do lançamento, e aceitar todas as correções não é sinal de que o documento melhorou. Esta rodada achou falhas nas próprias correções da rodada anterior; é provável que as desta rodada também tenham.
