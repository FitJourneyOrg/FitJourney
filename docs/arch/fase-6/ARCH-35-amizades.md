# ARCH #35 — Amizades: o grafo do convite

> Fase 6 · 2026-08-26 · **antes** da primeira linha de código.
> **Revisado no mesmo dia**, depois da emenda **9.3-A**. Ver a seção "O que mudou na revisão".
> **IMPLEMENTADO em 2026-08-27** (V40). A implementação produziu **quatro emendas** — ver a
> seção final "O que a implementação mudou". As quatro estão em produção; o texto acima delas
> descreve o desenho ORIGINAL e é mantido para leitura histórica.

## Contexto

**O convite é o gargalo do produto.** A 2-B.0 é explícita: grupo que nasce vazio, nasce morto.
Hoje convidar significa copiar um código de 6 caracteres e mandar por fora do app — funciona, mas
é atrito num ponto onde atrito custa o desafio inteiro.

Amizade resolve isso: convidar passa a ser escolher da lista.

## 1. Uma porta só: o perfil é público

A emenda **9.3-A** decidiu que **nome, nível, XP e conquistas são públicos** a qualquer usuário
autenticado. Amizade **não é permissão** — é atalho.

| | |
|---|---|
| **Ver perfil** | Qualquer um. Toca no nome em qualquer lugar e abre |
| **Amizade** | Serve para **convidar sem digitar código** |
| **Grupo** | Continua sendo a fronteira da ATIVIDADE: check-ins, feed, ranking |

### [REGRA] Tocar no nome abre o perfil, em qualquer superfície

Feed, ranking, lista de membros. E de lá, **Adicionar** se ainda não forem amigos — que é como se
conhece alguém que treina com você sem precisar pedir o código por fora.

### [REGRA] XP e nível ficam fora das superfícies do grupo

Não por privacidade — **por foco**. O ranking é por contagem de check-ins ([REGRA] #18), e dois
placares na mesma tela confundem qual é o jogo. O número está a um toque, para quem quiser.

### [REGRA] Amizade e grupo são independentes

Desfazer amizade não toca no grupo, e sair de um desafio não desfaz amizade. O que a amizade
perde é o atalho de convite, não acesso a nada.

## 1-A. O que mudou na revisão

A primeira versão deste ADR foi escrita quando o perfil ainda seria **privado**, e amizade era a
permissão para vê-lo. Caiu no mesmo dia, com a 9.3-A.

| Caiu | Por quê |
|---|---|
| "Duas portas que não se cruzam" | Passou a haver uma porta; a outra virou atalho |
| "O nome só é clicável para amigo" | O perfil é público — o nome é sempre clicável |
| "Preview mínimo antes de adicionar" | **O perfil completo É o preview** |

A terceira é a mais interessante: eu tinha desenhado um cartão reduzido — nome e inicial — para
alguém confirmar que digitou o código certo antes de mandar o pedido. Com o perfil público, esse
cartão é o próprio perfil. **A decisão de produto simplificou o desenho em vez de complicá-lo**, e
isso costuma ser sinal de que ela está certa.

## 2. Como se adiciona alguém

### [DECISÃO] Código de usuário, regenerável

Cada pessoa tem um código próprio. `display_name` **não serve** como endereço: ele não é único
(2–30 caracteres, sem restrição de unicidade), e buscar por nome seria superfície de enumeração
sem nenhuma barreira.

**8 caracteres**, não 6 como o do grupo. A diferença não é preciosismo:

| | Código do grupo | Código do usuário |
|---|---|---|
| Vida | Efêmero — o desafio acaba | **Permanente** |
| Janela de uso | Só em `AGENDADO` | Sempre |
| O que dá | Entrar num grupo | Chegar a uma **pessoa** |

O do grupo aguenta 6 caracteres porque é descartável e a porta fecha sozinha. O do usuário é
permanente e sempre resgatável — com 32⁶ ≈ 1 bilhão e sem limite de tentativas, dá para varrer e
descobrir gente. Com 8 caracteres são ~1 trilhão, mil vezes mais caro.

**Três defesas, e as três juntas:**

1. **8 caracteres** — encarece a varredura
2. **Limite de tentativas por conta** — encarece ainda mais e deixa rastro
3. **Regenerável** — quem receber importunação gera outro e o antigo morre na hora

A terceira é a que importa de verdade: é a única que **devolve controle à pessoa** em vez de
depender de nós detectarmos o abuso.

### [REGRA] Digitar o código abre o PERFIL, não um pedido

O código leva à mesma tela de perfil que se abre tocando num nome — com o botão **Adicionar**. A
pessoa vê quem é antes de mandar o pedido.

Sem isso, um erro de digitação viraria um pedido de amizade a um desconhecido. Com o perfil
público, essa garantia sai de graça: não precisa de tela intermediária nenhuma.

## 3. A relação

| # | Decisão |
|---|---|
| 35.1 | **Simétrica, com aceite.** Não existe "seguir" |
| 35.2 | Estados: `PENDENTE` → `ACEITA` · `RECUSADA` · `BLOQUEADA` |
| 35.3 | **Bloquear existe** e impede novo pedido |
| 35.4 | Teto de **500** amizades |
| 35.5 | O código é **regenerável** pelo dono |

### 35.1 — Por que simétrica

Assimétrico significa que qualquer um passa a ver seu perfil **sem você saber**. Num app com foto
de gente na academia, consentimento não pode ser implícito.

É a mesma escolha do opt-in do #17: a pessoa decide **antes**, sabendo o que está aceitando.

### 35.3 — Por que bloquear não é enfeite

Sem bloqueio, remover um amigo não impede ele de adicionar de novo no minuto seguinte. **Remover
vira gesto vazio — e quem precisa dele é exatamente quem está sendo importunado.**

Um "remover" que não remove é pior que não ter botão: promete proteção e não entrega.

### 35.4 — Por que um teto que ninguém alcança

500 não limita ninguém de verdade. Existe para o dia em que alguém escrever um script que adiciona
todo mundo. **Teto que ninguém alcança ainda é teto** — e o custo dele é uma constante.

## 4. Convidar para desafio pela lista

O ganho de produto que motivou tudo isto. Escolher amigos em vez de copiar código.

### [REGRA] Convidar ≠ entrar

O convite **não coloca ninguém no grupo**. Ele cria um pedido que a pessoa aceita ou ignora. As
regras de entrada da 2-B continuam valendo integralmente: só em `AGENDADO`, respeitando o teto de
50.

Um convite que colocasse a pessoa direto seria a mesma coisa que ser adicionado a grupo de
WhatsApp sem pedir — exatamente o que a 2-B.0 evitou ao exigir preview antes de entrar.

### ⚠️ Dependência: sem notificação, o convite cai num buraco

Convidar só funciona se a pessoa **ficar sabendo**. As notificações são a fatia **F**.

Enquanto ela não chega, esta fatia **precisa entregar uma tela de convites pendentes** —
alcançável pelo drawer, com contador. Não é refino: sem isso o recurso não funciona, ele apenas
parece funcionar.

## 5. Onde isso entra no plano

**Depois da fatia C.** A C é o primeiro momento em que o app é jogável — sem ranking, não há jogo
para convidar ninguém a jogar.

Construir grafo social antes do laço principal funcionar é decorar uma casa sem telhado.

Ordem proposta: **C** (ranking) → **C.1** (perfil de terceiro, que já nasce sabendo quem pode ver)
→ **#35** (amizades) → **E** (comentários e reações) → **F** (notificação).

Amizade antes da E de propósito: comentário e reação fazem mais sentido quando existe alguém do
outro lado que você escolheu ter por perto.

## 6. O que este ADR **não** decide

- **Sugestão de amizade** ("pessoas que você talvez conheça") — exige grafo e é v2
- **Importar contatos** — pede permissão de agenda, que é outra conversa de privacidade
- **Comunidade** (grupo sem fim) — adiada para a v2 em 2026-08-26
- **Feed de amigos** fora do grupo — não existe e não está previsto

## Consequências

**Bom:** ataca o gargalo da 2-B.0 · destrava a C.1 · e, depois da 9.3-A, **não acrescenta
fronteira de privacidade nenhuma** — o que era o maior custo da versão anterior.

**Custo:** um grafo social para manter — pedidos, bloqueios, teto.

**Risco aceito, e ele CRESCEU com a 9.3-A:** o código permanente é superfície de enumeração, e
agora o que ela expõe é o perfil completo — nome, nível e conquistas — em vez de um cartão
reduzido. As três defesas continuam sendo as mesmas (8 caracteres, limite de tentativas,
regeneração), mas o que está do outro lado vale mais.

**Vale registrar:** este ADR foi escrito e revisado no mesmo dia, porque uma decisão de produto
tomada em seguida derrubou metade dele. Foi barato justamente por ter sido escrito **antes** do
código — a revisão custou meia hora de texto, e não uma refatoração.

---

_Ratificado por Rafael em 2026-08-26. Fonte: `docs/arch/fase-6/ARCH-35-amizades.md`._

---

# O que a implementação mudou (2026-08-27)

Quatro emendas, nenhuma de conveniência: cada uma nasceu de um problema que só apareceu ao
escrever o código ou ao rodar a bateria.

## 35.2-A — duas tabelas, e `BLOQUEADA` sai do vocabulário

**O ADR dizia:** estados `PENDENTE` → `ACEITA` · `RECUSADA` · `BLOQUEADA`, os quatro na mesma
relação.

**O problema:** amizade é **simétrica** e bloqueio é **direcional**. Numa tabela só, A pedir
amizade a B e B bloquear A produzem duas linhas com direções opostas para o mesmo par — e
"estes dois são o quê?" passa a depender de qual linha foi lida primeiro.

**Agora:** `friendships` (par canônico, simétrica) + `blocks` (direcional). `BLOQUEADA` não
existe mais como status; bloqueio é uma linha em `blocks`.

**O par canônico** — `user_a` é sempre o menor uuid — é o que faz a chave primária impedir
**pedido cruzado**: A→B e B→A escrevem a mesma chave. É a mesma jogada do
`UNIQUE (group_id, user_id, local_date)` do check-in: a regra mora na CONSTRAINT, não numa
validação que alguém pode esquecer de chamar num caminho novo.

## 35.6 — perfil INDISPONÍVEL para quem foi bloqueado

**Decisão de produto tomada durante a implementação**, e ela é melhor que as três opções que eu
tinha proposto (só pedido / 404 / sumir do grupo).

Quem foi bloqueado vê um **perfil indisponível** — sem nome, sem nível, sem conquistas — e ele é
**idêntico ao de uma conta excluída**. Isso é o ponto: quem foi bloqueado **não consegue
distinguir os dois casos**. Se a tela dissesse "você foi bloqueado", o bloqueio viraria um
recado, e quem bloqueia normalmente quer sumir, não avisar.

É a mesma lógica do "404 e nunca 403" da C.1: **respostas diferentes vazam informação tanto
quanto o conteúdo delas**.

**Assimétrico:** quem BLOQUEOU continua vendo o perfil. Sem isso, a lista de Configurações →
Bloqueados seria uma fileira de "Usuário" e desbloquear viraria adivinhação.

**O grupo não muda** ([REGRA] amizade e grupo são independentes): o nome de quem bloqueou
continua no feed e no ranking. Esconder linhas faria a contagem do ranking não bater
([REGRA] #18), e duas pessoas no mesmo desafio veriam placares diferentes.

**Zerado no SERVIDOR**, não escondido na tela: `displayName` sai vazio, o nome verdadeiro não
atravessa o fio. Uma tela que recebesse o nome e decidisse não mostrá-lo seria a fronteira na UI
de novo — o erro que a 9.3-A já custou reconhecer.

## 35.7 — pedido cruzado vira AMIZADE

Pedir amizade a quem **já te pediu** não é conflito: os dois manifestaram a mesma intenção, e
exigir que um deles refaça o gesto com outro nome é burocracia. O servidor transforma o segundo
pedido em ACEITE.

**De quebra resolve a corrida** de dois toques simultâneos sem erro para ninguém.

**Como esta regra apareceu:** escrevi um teste assumindo que o segundo pedido daria sucesso. O
código respondia `JA_EXISTE_PEDIDO` — leitura literal e defensável. A falha não apontou um bug;
apontou uma **decisão de produto que ninguém tinha tomado**, resolvida por omissão dentro de um
`when`.

> `PENDENTE` era **dois estados usando o mesmo nome**: pendente que EU criei (conflito de
> verdade) e pendente que o OUTRO criou (intenção esperando confirmação). O parâmetro `euPedi`
> existe só para separá-los.

> **Teste que falha porque você esperava outro comportamento não é teste errado até você decidir
> qual comportamento quer.**

## 35.8 — Amigos mora em **Perfil → Amigos**, não no drawer

**O ADR previa** o drawer com contador de pedidos.

**O problema:** o contador só tem função se aparecer no ÍCONE do menu — senão você só descobre o
pedido depois de já ter ido olhar. E isso exigiria estado no ícone global do `AppNavHost`.

**Agora:** amizade é extensão da **identidade**, não seção do app, e mora dentro do perfil. O
contador fica no badge da aba **Pedidos**. Quem avisa de verdade é o **push** (fatia F.1, puxada
para junto desta por decisão de 2026-08-27); esta tela é o caminho para AGIR depois do aviso.

## O que se confirmou como estava

- **35.1 simétrica com aceite** (modelo Facebook, não Instagram). A 9.3-A esvaziou o argumento
  original — consentimento para ver o perfil —, mas a simetria fica por outro: convidar para
  desafio é ato entre pares, e o teto de 50 do grupo pede lista curta e mútua, não audiência.
- **35.3 bloquear existe e impede novo pedido**, agora com o efeito reforçado pela 35.6.
- **35.4 teto de 500**, verificado no **aceite** e do lado de quem age — barrar no pedido porque
  o ALVO está cheio revelaria quantos amigos ele tem.
- **35.5 código de 8 caracteres, regenerável**, com as três defesas: tamanho, 10 tentativas por
  hora por conta (em memória) e regeneração.
- **[REGRA] digitar o código abre o PERFIL**, nunca manda pedido.

## Uma coisa que o ADR não previu e a bateria achou

`NotFound` mostrava "Isto não existe mais. Pode ter sido removido em outro aparelho." ao buscar
um código regenerado — frase escrita para conteúdo do próprio usuário. **Terceira ocorrência**
do mesmo padrão nesta base (Conflict na A.2, ErroInline na A.4). Virou teste que vale para todos
os erros, não só para este.

---

_Emendas ratificadas por Rafael em 2026-08-27, durante a implementação._
