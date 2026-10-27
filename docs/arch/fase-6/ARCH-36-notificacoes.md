# ARCH #36 — Notificações: a gravada é a verdade, o push é o aviso

> Fase 6 · fatia **F.1** · escrito **depois** da implementação e da bateria manual, 2026-08-30.
> Migrations **V41** (`device_tokens`) e **V42** (`notifications`).
> Este ADR nasce de uma fatia executada, não de um desenho prévio: a decisão central estava clara
> antes do código, mas **seis das nove correções vieram da bateria manual** e várias delas mudaram
> o desenho. Elas estão marcadas ao longo do texto.

## Contexto

O ADR #35 dizia, sobre amizades: *"a fatia precisa entregar a tela de convites pendentes — sem ela
o recurso apenas parece funcionar, porque notificação é a fatia F"*. A F.1 foi **puxada para junto
do #35** por isso: pedido de amizade que ninguém fica sabendo é um recurso que existe no banco e
não no mundo.

A fatia traz a **infraestrutura inteira** com **um** caso de uso real. Tipos novos (comentário,
reação, denúncia da fatia E) entram depois, só plugando. Infra nascida de um caso concreto erra
menos que infra genérica feita no vácuo — foi assim que o outbox (#30) ficou bom.

---

## 1. [REGRA] Grava primeiro, despacha depois

O FCM **não guarda nada**. A mensagem chega, aparece na bandeja, e se a pessoa dispensar — ou
reiniciar o celular, ou negar a permissão — sumiu. Não existe "buscar o que recebi" no FCM.

A tabela `notifications` (V42) inverte a relação:

```
grava  ->  a notificação EXISTE, e a central vai mostrá-la
push   ->  tentativa de avisar AGORA; se falhar, não se perdeu nada
```

**A ordem é a decisão.** A ordem inversa perderia o aviso de quem mais precisa dele: justamente
quem não recebe push.

Se a **gravação** falha, o push sai mesmo assim — uma notificação efêmera é melhor que nenhuma.
Se o **push** falha, não se faz nada: a central mostra na próxima abertura.

> A bateria validou isto de forma acidental e depois deliberada. Acidental: o defeito do `JSONB`
> fez a gravação falhar por horas, e o log mostrou `Tentando só o push` — o sistema degradou em vez
> de cair. Deliberada: o **bloco 7** desligou as notificações no sistema e a central continuou
> mostrando tudo.

### [INV] `title` e `body` são gravados JÁ RENDERIZADOS

"u2 quer ser seu amigo" é gravado assim, com o nome dentro. Guardar só o id e montar a frase na
leitura parece mais limpo e é pior: se a pessoa trocar de nome amanhã, a notificação de ontem
passaria a dizer o nome novo, **contando uma história que não aconteceu**.

> **Notificação é registro do que houve, não consulta ao presente.**

Verificado no passo 16: u2 virou "miguel" e a notificação antiga continua dizendo "u2".

### Nunca falha para o chamador

`NotificacaoService.avisar` não propaga erro. O chamador é sempre uma ação de usuário que **já
aconteceu** — um pedido de amizade criado. Derrubá-la porque a notificação falhou seria trocar um
problema pequeno por um grande.

---

## 2. [REGRA] O token é identidade de INSTALAÇÃO, não de usuário

`device_tokens` (V41) usa o **próprio token como PK**. Três consequências que decidem o desenho:

**Uma pessoa tem vários aparelhos.** Uma coluna `fcm_token` em `users` guardaria só o último, e
quem instalasse o app num segundo aparelho silenciosamente pararia de receber no primeiro.

**O token não é estável.** O FCM o reemite em reinstalação, limpeza de dados, ou por decisão
própria. O `onNewToken` roda num `Service` sem sessão: ele guarda em `TokenPendente`, e o registro
se completa na próxima abertura com usuário logado.

**O mesmo aparelho serve a várias contas.** Alguém empresta o celular; o FCM devolve o **mesmo**
token. Por isso `INSERT ... ON CONFLICT (token) DO UPDATE SET user_id`.

### [INV] O logout apaga o token DESTE aparelho

Sem isso, quem sai continua recebendo notificação no aparelho até a próxima pessoa entrar — e
nesse intervalo o push chega para quem não deveria ver.

A baixa é o **primeiro** dos três passos do `SairDaConta`, e a ordem não é preferência:

```kotlin
push.darBaixa()                  // 1. precisa do token do Firebase AINDA VÁLIDO
profile.clearOnboardingCache()   // 2. antes do signOut (defeito da Fase 5)
auth.signOut()                   // 3. limpa a sessão + o token cacheado do Ktor
```

Depois do `signOut` a requisição sairia sem autenticação. É o item mais fácil de esquecer da fatia
e o de pior consequência: só aparece quando alguém empresta o celular.

### Emenda 36.1 — o registro tem que ser tão explícito quanto a baixa

**Descoberto na bateria, passo 20.** A baixa era chamada explicitamente pelo `SairDaConta`; o
registro dependia de um `LaunchedEffect(Unit)` no `AppNavHost`, que dispara **uma vez por
composição**. Login não recompõe o `AppNavHost`.

Resultado: **sair e entrar de novo sem matar o app deixava o aparelho fora da tabela**, e a pessoa
ficava sem push sem nenhum sintoma na tela.

> **Uma ponta explícita e a outra implícita não ficam sincronizadas por muito tempo.**

O registro passou a observar `uidFlow()` — a mesma peça que o #30 criou para caches que não
re-chaveavam no login. Cobre os três momentos: boot com sessão, login novo, troca de conta.

### Emenda 36.2 — a ordem dos operadores de `Flow` É o comportamento

A primeira versão da 36.1 escreveu `uidFlow().filterNotNull().distinctUntilChanged()`, e continuou
falhando.

A sequência real é `"u1"` → `null` (logout) → `"u1"` (login). O `filterNotNull` remove o `null` do
meio, e o `distinctUntilChanged` vê dois `"u1"` consecutivos e descarta o segundo. **O `null` era a
única coisa que separava as duas sessões.**

O detalhe que torna isto perigoso: **entrar em OUTRA conta funcionava**. Só sair e voltar na
própria conta falhava — o caso comum, e o menos provável de alguém testar por curiosidade.

Correção: `distinctUntilChanged()` **antes** de `filterNotNull()`.

### O FCM diz quando o token morreu, e a limpeza acontece ali

`UNREGISTERED` e `INVALID_ARGUMENT` na resposta do envio significam que aquele aparelho não existe
mais para nós. Sem apagar, a tabela cresce com lixo e cada envio fica mais caro.

A limpeza é **na resposta do envio**, não num job agendado: é exatamente ali que a informação
existe, e um job seria mais uma peça falhando em silêncio.

Falha que **não** é token morto (rede, quota, indisponibilidade) fica só no log — apagar por
instabilidade passageira perderia o aparelho de alguém.

---

## 3. A central: como a pessoa vê o que chegou

### [REGRA] Abrir a central marca tudo como lido

Abrir é o gesto de "vi tudo isto". Exigir um toque por item deixaria o badge aceso sobre coisas
que a pessoa acabou de ler, e ninguém marca notificação uma a uma.

### [INV] A lista exibida preserva o `readAt` do snapshot

Marcar acontece **depois** de ter a lista na mão, e a tela desenha o snapshot anterior. Se os itens
perdessem o destaque na hora, a pessoa não saberia o que era novo — **e o motivo de ela ter aberto
a tela era exatamente esse**.

Os passos 14 e 15 da bateria são um par: um sozinho não distingue "preservou o snapshot" de
"recarregou e apagou tudo".

### O badge conta a MESMA lista, não uma rota `/count`

`count { readAt == null }`. Duas fontes da mesma verdade divergem no dia em que uma ganhar cache e
a outra não — é a terceira vez que esta decisão aparece no projeto (badge de pedidos, contador de
membros, agora este).

**Falhar não zera o badge.** Zerar diria "você não tem notificações" quando a verdade é "não
consegui perguntar" — o mesmo erro que a C.1 evitou ao não apagar o perfil já exibido.

### [REGRA] Aceitar um pedido não marca a notificação como lida

Decisão de 2026-08-27. A notificação é o registro de que **o pedido chegou**; aceitar é outro fato.
Apagar o primeiro ao fazer o segundo perderia a história.

### Sem paginação, teto de 100

Quem rola 100 notificações para trás está procurando uma coisa específica, e para isso a central é
a ferramenta errada. Paginar seria construir navegação para um uso que não existe.

### Retenção: 6 meses

Notificação de seis meses atrás não serve a ninguém, e a tabela cresce por usuário para sempre sem
um corte. Purga agendada no boot, como a de mídia (fatia B) — **duas coisas que falham por razões
diferentes falham separadas**, então são dois laços, não um.

Índice **parcial** (`WHERE created_at < '2027-01-01'`): indexa só o que a purga procura. Um índice
completo em `created_at` seria mantido a cada inserção para servir uma consulta diária.

---

## 4. `AvisosDePush` — o push atualiza quem está olhando

**Nasceu da bateria.** A tela de Amigos recarregava no `ON_START` e o badge da barra ao trocar de
tela-raiz: os dois assumem que a pessoa **saiu e voltou**. Mas o push existe para avisar quem
**não** saiu — e o badge de Pedidos só subia depois de sair da tela e voltar.

Polling resolveria e foi descartado no `ContadorDeNaoLidas` com razão: **pedido de amizade não é
feed**. O evento já existe; faltava entregá-lo.

`AvisosDePush` é um `SharedFlow` no molde do `SessionExpiryBus`, publicado pelo
`FitJourneyMessagingService` e observado pelo `AppNavHost` e pela tela de Amigos.

**Por que um bus e não o Service buscando dados:** o Service roda fora de qualquer tela, às vezes
com o app morto. Se ele buscasse, faria requisição para uma tela que não existe. Sem coletor o
evento se perde — e não se perde nada, porque o app recarrega ao abrir.

> ⚪ **Dívida.** Com o bus, um pedido chegando enquanto a pessoa está na aba Pedidos move os itens
> debaixo do dedo. Hoje é pequeno (lista curta, botões explícitos); com os pushes da fatia E vira
> real. Saída conhecida: **"1 pedido novo — toque para ver"** no topo, sem recarregar a lista.

---

## 5. Deep link: tocar na notificação leva onde se AGE

Pedido de amizade abre **Amigos**, não a central: a central é a lista, e quem tocou já sabe o que
quer fazer. Tipo desconhecido — vindo de uma versão mais nova do servidor — cai na central, que
sabe mostrar qualquer notificação. Melhor um destino genérico que funciona do que nenhum.

O destino viaja num `MutableStateFlow` da `MainActivity`, e não como parâmetro do Composable: a
Activity é `singleTop`, então tocar na notificação com o app aberto chama `onNewIntent` e a tela já
composta não seria recriada. Um fluxo faz os dois caminhos chegarem ao mesmo lugar.

### Emenda 36.3 — o deep link espera a navegação inicial terminar

**Descoberto na bateria, passo 10.** Com o app morto, o `AppNavHost` compõe já com o destino na
mão, navega para Amigos, e **então** o Splash decide e chama
`navigate(Home) { popUpTo(Splash) { inclusive = true } }` — apagando tudo que foi empilhado sobre o
Splash. Tocar na notificação com o app fechado abria a **Home**.

> **Dois caminhos que terminam no mesmo lugar não são o mesmo caminho.** "App morto" e "app aberto"
> chamam o mesmo `lerDestino` e navegam igual, mas só um concorre com o Splash.

A primeira correção usou `mostrarAbas` (verdadeiro só nas telas-raiz) como gate, e **estava larga
demais**: tocar na notificação estando em Conquistas não fazia nada. O gate correto bloqueia apenas
**Splash, Login, Nome e Quiz** — as telas onde navegar seria desfeito ou não faria sentido.

> **Tocar na notificação é ação explícita e tem que levar de onde quer que a pessoa esteja.**

---

## 6. A permissão

Android 13+ exige `POST_NOTIFICATIONS`. Abaixo disso a notificação é permitida por padrão.

**Negar não quebra nada**: a notificação continua gravada e a central a mostra. Por isso o
componente **não insiste, não explica e não mostra tela de justificativa** — negar é escolha
legítima com consequência pequena.

### Emenda 36.4 — o gatilho é "existe sessão", não "abri a tela de login"

**Descoberto na bateria, passo 27.** O pedido morava na `LoginScreen`, e o `LaunchedEffect` dispara
quando a tela **compõe**: o diálogo aparecia **antes de a pessoa digitar a senha** — quando ela
ainda não viu nada do app, que é o momento de maior taxa de recusa, e no Android recusar é quase
definitivo. Pior: quem abrisse com sessão restaurada pularia a `LoginScreen` e **nunca** seria
perguntado.

Passou para o `AppNavHost`, atrelado ao `uidFlow` — o mesmo lugar do registro do aparelho, pela
mesma razão: as duas coisas dependem de haver sessão, e separá-las foi o que fez uma ficar para
trás.

---

## 7. [ARCH] O driver Postgres saiu de `runtimeOnly`

`server/build.gradle.kts` tinha `runtimeOnly(libs.postgres)` com a nota *"driver JDBC: só runtime,
código não referencia"*.

A F.1 precisou de `PGobject` para gravar `JSONB` (o Postgres não faz cast implícito de `varchar`
para `jsonb` em parâmetro preparado), e a restrição caiu.

**Ela descrevia uma independência de banco que o projeto já não tem** há muitas fatias: as
migrations usam `JSONB`, índice parcial, `ON CONFLICT`, `INTERVAL`, `UUID` — nada disso é portável.

> **Restrição que mente é pior que dependência declarada.**

O `PGobject` fica encapsulado em `server.db.JsonbText`, e nenhuma feature o referencia.

> ⚪ **Dívida:** regra de Konsist mantendo o import do driver restrito a `server.db`.

---

## 8. O que a bateria manual ensinou

Nove defeitos: **seis de comportamento, três de observabilidade**. Os três de observabilidade não
quebravam nada — e foram eles que fizeram os outros custarem horas.

> **Engolir exceção é decisão de fluxo, não de log.** `runCatching { notify() }` e
> `runCatching { registrar() }` mantinham o processo vivo, corretamente, e não deixavam rastro. O
> processo deve seguir; alguém precisa poder saber por quê.

> **Ação destrutiva sem motivo registrado não é diagnosticável.** `apagando 1 token(s) morto(s)`
> não distinguia `UNREGISTERED` (aparelho fora) de `INVALID_ARGUMENT` (credencial de outro
> projeto) — dois problemas com correções opostas e o mesmo sintoma.

**Dois defeitos não davam sintoma nenhum** (36.1 e 36.2): o app funcionava, a tela não reclamava, e
a pessoa parava de receber notificação. Nenhum teste automatizado deste projeto os pegaria — a 36.2
exige duas sessões reais da mesma conta.

E metade do tempo da bateria foi gasto em **problemas de ambiente que imitavam defeitos de
código**: IP da LAN reatribuído pelo DHCP a outro aparelho que respondia 403, conexão do FCM caída
no celular, modo Não Perturbe ligado. Nenhum deixa rastro no nosso log.

> **Antes de suspeitar do código, elimine o ambiente.** Registrado em
> `docs/erros/README.md`, com verificação de 60 segundos.

O build agora imprime `FitJourney: API_BASE_URL = <url>` — a linha que teria encerrado os três
episódios de IP em segundos.

---

## Emenda 36.3 — a aposta da infraestrutura, verificada (fatia F, 2026-09-09)

Este ADR abriu dizendo que a F.1 traria *"a infraestrutura inteira com **um** caso de uso real"* e
que *"tipos novos entram depois, só plugando"*. A fatia F acrescentou **seis** tipos. O resultado:

| Prometido | Aconteceu |
|---|---|
| Nenhuma migration na `notifications` | ✅ V42 intocada |
| Nenhuma mudança no `NotificadorFcm` | ✅ |
| Tipo novo não exige migration | ✅ o `type` é `TEXT`, vocabulário aberto |
| A gravada é a verdade | ✅ verificado com o app desinstalado e com o usuário deslogado |

**Os três defeitos da bateria F foram no gatilho novo ou no TEXTO dos avisos — nenhum na
infraestrutura.** É a evidência mais forte a favor de "infra nascida de um caso concreto erra menos
que infra genérica feita no vácuo".

### O que a F precisou acrescentar

**Portas estreitas, uma por chamador.** `checkin` não importa `notificacao`: o `SocialService`
recebe um `fun interface AvisarComentario`, o `ModeracaoService` um `data class` com três lambdas.
Todas com **default que não faz nada** — comentar e denunciar funcionam sem FCM, e um serviço que
exigisse o notificador amarraria a ação à disponibilidade do Google.

> **A porta NÃO protege ninguém.** Se o push estourar, a exceção atravessa e derruba a ação. Quem
> protege é o `NotificacaoService`, que engole — e o wiring real passa por ele. Há um teste que
> afirma isso explicitamente, para que quem ligar uma porta nova direto no `Notificador` não ache
> que está seguro "porque as outras estão".

**O `data` do push cresceu.** A F.1 mandava `tipo` e `fromUserId`; agora vão `groupId` e
`checkInId` conforme o tipo. O `FitJourneyMessagingService` já repassava **todos** os campos como
extras desde a F.1 — o que faltava era o lado da Activity ler mais de um. O `destinoDoPush` deixou
de ser `String` e virou `DestinoDePush(tipo, groupId?, checkInId?)`.

**Cada tipo abre onde se AGE**, incluindo a **aba**: levar ao grupo certo na aba errada é meio
caminho. Toda rota que depende de id cai na central se ele faltar — `Map<String,String>` não tem
contrato de compilação, e **cair no genérico é sempre melhor que abrir um grupo que não existe**.

### O sexto gatilho não cabia neste ADR

"Entradas do dia" e "fila parada" **não nascem de requisição nenhuma**, e por isso viraram o
`AvisosDoDia`, em `server.avisos` — fora de `features/`, como o `PurgaDeMidia`. O que ele tem de
diferente das duas purgas está documentado lá; o que interessa aqui é que **ele usa a mesma porta
estreita e o mesmo `NotificacaoService`**, sem nenhum privilégio.

### Um texto que este ADR não previu

A F.1 estabeleceu que `title`/`body` são **gravados já renderizados**, para a notificação de ontem
não passar a dizer o nome novo de quem trocou de nome hoje. A F encontrou o outro lado disso:

> **Mensagem compartilhada só pode afirmar o que é verdade em todos os caminhos que a usam.**

O aviso de invalidação dizia *"o admin avaliou **a denúncia**"* e era reusado na invalidação direta
(6.10), onde não há denúncia. A decisão de os dois caminhos mandarem o mesmo aviso continua certa —
quem perde o ponto não precisa saber por qual rota o admin passou. O erro era o texto afirmar um
fato que só valia num deles.

**Nenhum teste pegaria isso**: eles afirmam *que* avisou, não *o que* a frase diz.

---

## Emenda 36.5 — a leitura virou cache-first (fatia "notificações offline", 2026-10-22)

Até aqui `Notificacoes.listar()` era só-rede no cliente: offline, a central esvaziava — violação
da convenção "leitura é cache-first" (#30) que só foi notada quando Rafael testou em modo avião.

A correção estende o #30 sem reabrir nenhuma das decisões deste ADR: `title`/`body` continuam
gravados já renderizados (agora também numa tabela local, `notificacao`, espelho do que o servidor
tem), o badge continua contando a MESMA lista (`count { readAt == null }`, agora local), e o
**[INV] a lista exibida preserva o `readAt` do snapshot** continua valendo — é por isso que
`NotificacoesRepositoryImpl.marcarComoLidas()` grava local e enfileira o `POST`, mas não existe
caminho onde a tela em pé perde o destaque dos itens que acabou de mostrar.

`Notificacoes` (a interface que `ContadorDeNaoLidas`/`NotificacoesViewModel` consomem) não mudou:
continua `listar()`/`marcarComoLidas()` one-shot. Cache-first é implementação por trás dela
(`NotificacoesRepositoryImpl`), não um novo contrato reativo — decisão deliberada para não
arriscar o `[INV]` acima com um `Flow` que refletisse a escrita local na hora.

A marcação "todas como lidas" é a mutação GLOBAL do servidor (POST sem id) — o outbox a trata com
um alvo fixo por conta (ver `regras/05-offline.md`), e isso é o que faz duas marcações offline
seguidas colapsarem numa só sem tocar no `CompactadorDeOutbox`.
