# ARCH #37 — Idioma: o servidor manda dados, o cliente escreve as frases

> Fatia **G** · escrito **antes** do código, 2026-09-09. Migration **V47** (`users.locale`).
> Primeira leva: **pt-BR** e **en**. A estrutura é aberta para N idiomas; a lista completa vem depois.
>
> ⚠️ **Esta fatia não está no plano de fases.** O RESUMO-FASES vai de 6 (grupos) para 7 (trial e
> assinatura), e idioma não aparece. Foi puxado a pedido.
>
> **Critério de aceitação declarado:** *"quando estiver 100%, manter 100% da UI traduzida, com
> ressalva de ações humanas como comentários de usuário"*. Este ADR é escrito para esse critério, e
> a primeira versão dele não era, porque o levantamento estava incompleto. Ver a seção 0.

---

## 0. O levantamento que faltou

A primeira versão cobria os literais Compose e o `Aviso`, e parava aí. Sob o critério de 100%, isso
deixava **duas categorias inteiras** de português de fora, e as duas são texto que o app **mostra**.

| | O quê | Onde vive | Volume |
|---|---|---|---|
| 1 | Literais Compose | app | ~500 |
| 2 | Avisos (push e central) | servidor | 7 avisos |
| 3 | **Mensagens de erro** | servidor; o `ErrorUi` faz `texto = message` | **104** |
| 4 | **`programs.rationale`, `SplitType.label`/`description`** | servidor e `shared-contract`, persistido | ~10 |
| 5 | Nomes de exercício | banco (`exercises.name`) | centenas |

O item 3 não é acessório. *"Você já denunciou este check-in."*, *"Só o admin do desafio julga
denúncias."*, *"Denúncias só valem até 7 dias."* são as frases que **melhor** explicam o erro hoje,
justamente porque o servidor sabe o que aconteceu e a tela não.

> **Um inventário que para no código do cliente não descreve o que o usuário lê.** O servidor manda
> prosa pronta em 111 lugares, e nenhum deles aparece num `grep` do módulo `app`.

---

## 1. [REGRA] Texto que o usuário lê vive em UM lugar: o `strings.xml` do cliente

O servidor manda **dados, códigos e chaves**. Nunca prosa.

Isso já era a intenção do ARCH #31: *"`code`: string estável p/ o cliente ramificar **sem parsear
`message`**"* e *"quem escolhe o texto é a camada que conhece a plataforma"*. O mecanismo foi
construído e nunca usado. O `code` viaja desde sempre, e o cliente o ignora para mostrar a `message`.

O ganho não é estético. Com um catálogo só:

- **uma** verificação cobre tudo, porque o Lint recusa build com `MissingTranslation`;
- adicionar o terceiro idioma é um arquivo, não três lugares;
- ninguém precisa lembrar de qual lado escrever a frase nova.

> **Duas fontes de tradução divergem na primeira que alguém esquecer de atualizar**, a mesma lição
> do `canDelete` na fatia E.2.

### A exceção é uma só, e é principiada

**A notificação.** O push tem que chegar com título e corpo prontos, porque mensagem só-dados **não
aparece com o app fechado ou dormindo**, que é exatamente quando a notificação importa. Já decidido
e testado no ARCH #36.

Então o servidor tem um catálogo, e só um: os ~14 textos dos 7 avisos, em `TextosDeAviso`. Não é
brecha para crescer. Qualquer texto novo que não seja notificação vai para o cliente.

---

## 1.1 [REGRA] O mecanismo é o `strings.xml` do Android, e isso foi reavaliado

`values/strings.xml` mais `values-en/strings.xml`, com `AppCompatDelegate.setApplicationLocales` e
`locales_config.xml`. Custa a dependência **appcompat** (já no `libs.versions.toml`, nenhum módulo
usava) e faz o `MainActivity` virar `AppCompatActivity`.

Duas razões concretas, e nenhuma é estética:

- **Plural.** *"1 pessoa entrou no seu desafio hoje"* / *"2 pessoas entraram"* já existe desde a
  fatia F, e inglês pluraliza por regras diferentes. `<plurals>` resolve; um `if (n == 1)` à mão é
  defeito esperando o idioma número três.
- **`MissingTranslation` é erro de build.** Com 721 literais, esquecer um não é risco, é certeza.
  Uma verificação que o Lint faz de graça não se troca por disciplina.

### A alternativa KMP foi levantada e RECUSADA por dado (2026-09-10)

A pergunta voltou no começo da G.3, e com razão: uma biblioteca multiplataforma pouparia refazer
isso no iOS? Duas descobertas responderam.

**A primeira: a biblioteca não tira o trabalho que dói.** Nenhuma delas extrai literal de dentro do
código. Os 721 literais saem à mão, nos mesmos 87 arquivos, com qualquer mecanismo. O que muda é
**onde a string mora**, e isso é uma fração do esforço.

**A segunda, decisiva: o Moko Resources não suporta o Kotlin deste projeto.** O último release é
**0.26.4, de 06/05/2026**; o **Kotlin 2.4.0 saiu em junho de 2026**. E não é azar de uma vez: cada
minor do Kotlin exigiu um release de correção (`2.2.20` → 0.25.1, `2.3.20` → 0.26.2). Com Kotlin
2.4, AGP 9.0.1 e Gradle 9.1, adotá-la seria amarrar o upgrade do projeto à cadência dela.

> **Dependência que gera código contra a API interna do compilador amarra você à cadência dela, não
> à sua.**

O **Compose Multiplatform Resources** ficou como alternativa viável (é da JetBrains e acompanha o
Kotlin), e o projeto já tem Compose MP 1.11.1 no catálogo com `iosArm64` e `iosSimulatorArm64`
declarados no convention plugin. Recusado por escopo: **nenhum módulo aplica esse plugin hoje**,
então adotá-lo é pôr o Compose MP em uso de verdade e mexer na estrutura de módulos — decisão maior
que escolher onde guardar string.

Quando o iOS chegar, migrar um `strings.xml` **completo e revisado** é trabalho pequeno perto de
extrair 721 literais de novo, e aí a escolha do mecanismo será feita sabendo como o iOS vai ser.

---

## 2. [REGRA] Idioma são três problemas, com três donos

| Camada | Onde o texto nasce | Quem decide o idioma |
|---|---|---|
| UI, erros, rationale | `strings.xml` | **Android**, pela locale ativa |
| Notificação | `TextosDeAviso`, no Ktor | **servidor**, lendo `users.locale` |
| Conteúdo de usuário | comentário, título de desafio, motivo de denúncia | **ninguém** |

A terceira linha é decisão, não omissão. É a ressalva feita ao definir o critério. Traduzir
automaticamente o que uma pessoa escreveu seria **inventar palavras na boca dela**, e num grupo de
conhecidos atribuir a alguém uma frase que ela não disse é pior que a barreira de idioma.

---

## 3. [REGRA] A estrutura é para N idiomas; a lista é curta de propósito

Três decisões fazem o terceiro idioma custar um arquivo em vez de uma refatoração. Elas valem desde
já, com dois, porque só se testa o que existe.

### 3.1 O idioma viaja como `String`, nunca como enum

O projeto já tem essa lição escrita, no `ErrorResponse`: *"String (não enum) no fio: código novo no
server não quebra cliente antigo"*. Vale idêntico aqui.

Se `UserDto.locale` fosse enum e o servidor mandasse um idioma que o app antigo não conhece, a
desserialização quebraria e a pessoa perderia a tela inteira por causa de um idioma que nem escolheu.
String no fio, enum interno, tradução feita pelo `IdiomaPolicy`.

### 3.2 [INV] A cadeia de fallback existe antes da lista

Com dois idiomas dá para não pensar nisso. Com dez, é a única coisa que importa: aparelho em
`fr-CA`, não temos francês, o que aparece?

```
exato        pt-BR -> pt-BR
só a língua  pt-PT -> pt-BR      (ignora a região)
piso         fr-CA -> pt-BR
```

> **Idioma que não existe precisa cair em algum lugar decidido, ou cai onde der.**

A regra fica no `IdiomaPolicy`, pura e testada com dois idiomas. Adicionar o terceiro não mexe nela.

### 3.3 `TextosDeAviso` é organizado por IDIOMA, e quem cobra é o COMPILADOR

> Esta seção foi **reescrita durante a implementação**. A versão anterior propunha um mapa por
> idioma mais um teste afirmando que toda chave existe em todos. Ao escrever, apareceu uma forma
> melhor, e o ADR passa a descrever o que existe.

`ChaveDeAviso` é uma `sealed interface` com uma `data class` por aviso, e o `TextosDeAviso` tem uma
função por idioma. Os dois `when` são **exaustivos**:

- **aviso novo** quebra o build em todos os idiomas que ainda não o traduziram;
- **idioma novo** quebra o `when` de `render`.

> **Verificação que o compilador faz não precisa de teste, e não pode ser esquecida.**

No cliente, o `MissingTranslation` do Lint faz esse papel de graça. No servidor não existe nada
equivalente, e a exaustividade do `when` é o que ocupa o lugar dele.

O sealed também resolve um problema que o mapa tinha e que só apareceria depois: **os parâmetros
ganham tipo**. `quantas` é `Int`, e o plural precisa do número. Com `Map<String, String>` o catálogo
teria que reconverter, e um erro de digitação na chave viraria push sem o número, em produção, em
silêncio.

**De quebra, uma duplicação sumiu.** O `TIPO_*` que o cliente lê para o deep link era escrito à mão
dentro de cada factory, ao lado da chave: dois lugares dizendo a mesma coisa, sem nada impedindo que
discordassem. Agora é propriedade da chave.

O que sobra para teste é o que o compilador não vê: plural, possessivo e a convenção de escrita da
seção 8.

### 3.4 O `CHECK` do banco é de vocabulário FECHADO

`CHECK (locale IN ('pt-BR','en'))`. Cada idioma novo é uma migration, mas de uma linha, e que só
**afrouxa** a restrição. É o mesmo movimento que a V46 já fez em `moderation_actions`.

Aceito o custo porque é o padrão do projeto: emoji, papel de membro e ação de moderação são todos
fechados no banco, e pela mesma razão. O `CHECK` vale para **escrita direta**, por script ou
correção manual, que é onde o Kotlin não alcança.

---

## 4. [REGRA] O idioma mora no servidor

`users.locale`, NOT NULL, default `pt-BR`, com o `CHECK` da seção 3.4 (V47).

Guardar só no aparelho seria mais barato e está errado por um motivo decisivo: **o texto da
notificação nasce no servidor**. Quem escolhesse inglês teria a interface em inglês e todo push em
português.

> **Metade do app traduzida é pior que nada.** A pessoa não conclui "falta traduzir", conclui que
> está quebrado.

### O idioma pode mudar em DOIS lugares

Com idioma-por-app do Android, a pessoa pode trocá-lo pelas **configurações do sistema**, sem abrir
o app. É o comportamento certo, e é onde muita gente procura.

A consequência: a sincronização lê `AppCompatDelegate.getApplicationLocales()`, e **não** o que a
nossa tela gravou. Uma tela que fosse a única fonte da verdade dessincronizaria no primeiro usuário
que usasse o caminho do sistema, e o sintoma seria só o push chegando no idioma antigo, que ninguém
liga ao ajuste feito três dias antes.

### [REGRA] Ninguém tem o idioma trocado por uma atualização

Quem já usa o app **continua em pt-BR** depois desta fatia, mesmo com o celular configurado em
inglês. O inglês passa a existir na tela de Conta, por escolha explícita.

Seguir o aparelho na primeira abertura seria o comportamento de um app internacional, e é
provavelmente o certo quando houver dez idiomas. Hoje seria um brasileiro abrindo o app amanhã em
inglês sem ter pedido, e isso não parece recurso novo, parece defeito.

> **Recurso que muda a experiência de quem não pediu nada precisa de um gesto da pessoa.** Virar
> essa chave é decisão de produto para quando a lista crescer, e está registrada como [PROPOSTA].

---

## 5. [REGRA] O `Aviso` carrega chave e parâmetros, nunca texto pronto

```kotlin
// antes
Aviso(titulo = "Novo pedido de amizade", corpo = "$de quer ser seu amigo", dados = ...)
// depois
Aviso(chave = PEDIDO_DE_AMIZADE, params = mapOf("nome" to de), dados = ...)
```

### Por que não passar o idioma para as factories

Seria a mudança menor: `Aviso.pedidoDeAmizade(de, deId, idioma)`. O custo aparece uma linha adiante,
porque **todo serviço que notifica precisaria descobrir o idioma do destinatário**: hoje
`SocialService`, `ModeracaoService`, `FriendshipService` e `AvisosDoDia`, mais o próximo que nascer.

> **Concern que aparece em quatro lugares está no lugar errado nos quatro.**

O `NotificacaoService` já lê o usuário para pegar os tokens de aparelho. É a borda natural: uma
leitura, um lugar que sabe de idioma, features intocadas. É o mesmo raciocínio das portas estreitas
da fatia F.

### [INV] Push e linha gravada são renderizados na MESMA chamada

Hoje os dois usam o mesmo `Aviso` por disciplina, e quem escrevesse um caminho que renderizasse duas
vezes não quebraria teste nenhum. Com a renderização na borda isso passa a valer por construção, e
reforça o `[INV]` do #36 (gravado já renderizado) em vez de competir com ele.

---

## 6. [REGRA] Erro se ramifica por `code`; `message` vira diagnóstico

> ✅ **Implementado na G.2 (2026-09-10).** 67 códigos, 87 frases distintas, 6 desmembramentos.

O cliente parou de exibir `ErrorResponse.message` e passou a escolher o texto pelo `code`.

**`message` continua no fio**, como fallback para código desconhecido e como diagnóstico no log. Ela
não some: deixa de ser o que a pessoa lê. É por ela que **um servidor novo falando com um app antigo
continua explicando melhor** do que qualquer texto genérico.

> **Código estável é contrato; frase é apresentação.** O #31 já dizia isso, faltava alguém usar.

### O que a G.2 encontrou, e que o desenho não previa

**Não eram 104 frases, eram 87 distintas.** *"Programa não encontrado"* aparecia em 7 lugares,
*"Grupo não encontrado"* em 5. Consolidar onde a situação do usuário é a mesma tirou 17 traduções do
caminho.

> **Desmembrar por caminho de código, e não por situação do usuário, multiplica trabalho sem mudar
> uma tela.**

**Seis frases cobriam situações opostas**, e essas foram desmembradas:

| Antes | Virou |
|---|---|
| "Usuário não encontrado" | a OUTRA pessoa · a PRÓPRIA conta (estado impossível) |
| "Perfil não encontrado" | perfil de terceiro (vago por privacidade) · meu questionário incompleto |
| "Complete a avaliação de saúde" | falta a saúde · falta o questionário inteiro |
| "Pedido não encontrado" | não existe · já respondido · **é meu** |
| "Grupo não encontrado" | não existe · **não sou membro** |

O último desmembramento da lista aconteceu **durante a própria fatia**, e o motivo está na seção 6.1.

### 6.1 O erro que a fatia cometeu enquanto o corrigia

O `FriendshipPolicy.podeResponder` é **uma condição que barra por dois motivos**: o pedido já foi
respondido, ou quem tenta responder foi quem o enviou. A primeira versão da G.2 deu a mesma frase
aos dois, e para o segundo ela é **falsa**: ninguém respondeu nada.

Quem apontou foi um teste escrito muito antes da fatia, `quem mandou nao consegue aceitar o proprio
pedido`.

> **Guarda que barra por dois motivos precisa de duas frases, mesmo quando a condição é uma só no
> código.**

### 6.2 [INV] O conjunto de portões de plano mora no CONTRATO

Até a G.2 os quatro portões compartilhavam `ENTITLEMENT_REQUIRED`, e o `ErrorUi` abria o paywall com
`if (code == ENTITLEMENT_REQUIRED)`. Dar texto próprio a cada um exigiu código próprio, e isso
**quase apagou o paywall em silêncio**: os códigos novos não casariam com o `if`, nada quebraria, e
a tela ofereceria "Voltar" no lugar da assinatura.

> **Ramificar por um código só e depois multiplicar os códigos quebra a ramificação sem quebrar o
> build.**

`ErrorCodes.PORTOES_DE_PLANO` é o conjunto, e um teste percorre ele inteiro. `LIMITE_DE_PROGRAMAS_PREMIUM`
fica **de fora**: quem bate nele já é premium, e oferecer o plano a quem acabou de pagar é pior que
não oferecer nada.

### 6.3 Nove frases NÃO serão traduzidas, e estão listadas

Nove mensagens carregam um número que só o servidor conhece (o prazo da denúncia, o limite de
caracteres, os tetos de plano). Havia três saídas: mandar o valor no `ErrorResponse`, mover as
constantes para o contrato, ou deixar essas nove com o texto do servidor. **Foi escolhida a
terceira** (decisão de 2026-09-10).

A consequência é que elas aparecerão em português num app em inglês. Por isso existe
`TextosDeErro.SEM_TEXTO_PROPRIO`: os nove códigos, cada um com o parâmetro que carrega, e um teste
que exige que todo código do `ErrorCodes` tenha texto **ou** esteja nessa lista.

> **Débito que ninguém consegue enumerar não é débito, é surpresa.**

### 6.4 O catálogo `docs/gerados/erros.md`

Gerado por `gen_erros.py` a partir de `server/src/main`. Traz, por frase: o código, o status, a
operação, a rota, a guarda no código, o comentário do autor, e **o que a pessoa fez** para chegar
ali — este último escrito à mão em `docs/fontes/erros_contexto.py`, porque não pode ser extraído.

O cruzamento entre os dois lados relata frase sem descrição e descrição órfã. Não é teoria: ao
reescrever 19 mensagens, o gerador acusou **30 lacunas e 23 órfãs** na hora.

---

## 7. [REGRA] `rationale` é derivado, não persistido

`programs.rationale` nasce em português no `StructureEngine` e é **gravado**. Diferente da
notificação, isso **não** é registro do que houve: é a descrição de um programa que a pessoa está
usando hoje, e é função pura de `split` mais `daysPerWeek` mais `focusAreas`.

> **Estado que é função de outra coisa é DERIVADO, não persistido**, a mesma regra que evita coluna
> `status` em grupo (#22, #33). Congelar a frase é o mesmo erro, em texto.

O cliente monta a frase a partir dos campos que o `ProgramDto` já carrega. Consequências:

- sai no idioma atual, sem migration de dado;
- **programas antigos passam a ficar certos também**, inclusive os gerados antes desta fatia;
- a coluna `rationale` fica vestigial. Não é apagada agora, ninguém apaga coluna com pressa, mas
  para de ser lida, e isso vai para o `debitos.md`.

`SplitType.label` e `description` saem do `shared-contract` pelo mesmo motivo. O KDoc deles diz
literalmente *"texto de UI"*, e texto de UI não mora no módulo de contrato.

### 7.1 `programs.name` seguiu o mesmo caminho, e primeiro (G.5, V48)

Enquanto o `rationale` estava enumerado e esperando, o **`name` não estava**: o `ProgramService`
montava `"Programa 4x — Push/Pull/Legs"` e gravava. Português, travessão, persistido.

A diferença de desenho entre os dois importa, e é o que mantém a coluna viva:

| | `rationale` | `name` |
|---|---|---|
| quem escreve | só a máquina | máquina **ou o usuário** (ARCH #27) |
| destino da coluna | vestigial, remoção futura | **fica**, e passa a guardar só a escolha da pessoa |
| o que significa o vazio | não se aplica | *"ninguém escolheu, derive"* |

> **Nem toda coluna de texto derivado morre: a que o usuário também escreve sobrevive com o
> significado do vazio redefinido.**

É o mesmo desenho do `display_name`. A **V48** zera só o que a máquina tinha nomeado, com filtro de
três camadas — `origin = 'AI'`, o formato exato, e o travessão, que ninguém digita no celular e que
esta mesma ARCH proíbe na seção 8.

### 7.2 O mesmo raciocínio NÃO vale para a notificação (ratificado na G.5)

O pedido de 2026-09-15 era mover todo o texto do servidor. As notificações ficaram, e o motivo não
é escopo: `NotificacaoDto.title`/`body` chegam **renderizados** justamente para que *"a notificação
de ontem continue dizendo o que dizia ontem, mesmo que a pessoa citada troque de nome depois"*.

Derivar exige remontar, e remontar usa o dado de **hoje**. A notificação deixaria de ser registro do
que houve.

> **Registro do que aconteceu não se traduz retroativamente.**

Isto **refina a seção 1**: a exceção continua sendo uma só, e agora tem duas razões independentes —
push só-dados não aparece com o app fechado (#36), e a linha gravada é histórico.

---

## 8. [REGRA] Texto de usuário não usa travessão

Nem `—` nem `–`. Vale para o `strings.xml` de todos os idiomas, para o `TextosDeAviso` e para a
documentação, porque um documento que usa uma pontuação que o app não usa cria duas convenções.

**E é verificado, não combinado.** O `TextosDeAvisoTest` varre todas as chaves em todos os idiomas e
quebra o build se achar travessão. Vale para sempre, inclusive para quem traduzir o sétimo idioma sem
nunca ler este documento.

> **Regra que depende de alguém lembrar já falhou.** É o argumento do Konsist aplicado a texto.

⚠️ **A regra já estava sendo violada antes de existir.** O `StructureEngine.rationale` escreve
*"calibrado pelo seu nível — a base do modelo de hipertrofia"*, com travessão. Apareceu por acaso na
saída do `WorkoutGenerationIntegrationTest`, num teste que não é sobre texto.

Não foi corrigido na G.1 porque a frase inteira sai do servidor na G.3 (seção 7). Registrado ali
para não se perder, e é um bom lembrete de que a varredura precisa alcançar o `strings.xml` também,
não só o catálogo de avisos.

---

## 9. [REGRA] O catálogo de strings é GERADO, e carrega contexto

O documento com todas as strings em pt-BR é a base que vai para quem traduz os demais idiomas. Ele
**não é escrito à mão**, pelo mesmo motivo que o catálogo de testes não é: um catálogo mantido a mão
exige lembrar de editá-lo a cada string nova, e no primeiro esquecimento passa a descrever um app
que não existe.

Aqui o custo do esquecimento é maior que no catálogo de testes. Uma lista defasada faz alguém
**traduzir string que já foi removida** e **não receber a que entrou**. O primeiro é desperdício, o
segundo é buraco na tela, e nenhum dos dois aparece até o idioma novo estar em produção.

`docs/geradores/gen_strings.py` lê o `values/strings.xml` e o `TextosDeAviso.kt`, e escreve
`docs/gerados/strings.md`. Entra no `gen_tudo.py` junto com os outros onze.

### [INV] Chave sem contexto é tradução errada esperando acontecer

A parte que decide a qualidade não é a lista, é o que vai ao lado de cada linha. `"Denunciar"`
sozinho pode virar *Report*, *Flag* ou *Denounce*, e as três estão certas em algum contexto e erradas
neste. Quem traduz não tem o app aberto.

O gerador emite, por string:

| Coluna | De onde sai | Por que o tradutor precisa |
|---|---|---|
| chave | `name` do `<string>` | é o identificador estável |
| texto pt-BR | o valor | é o que se traduz |
| onde aparece | comentário `<!-- -->` obrigatório | resolve o `"Denunciar"` acima |
| parâmetros | `%1$s`, `%2$d` no valor | diz o que **não** pode ser traduzido nem reordenado sem cuidado |
| plural | é `<plurals>` ou não | avisa que o idioma alvo pode ter mais formas que português |
| limite | comentário, quando houver | botão e chip têm espaço; frase longa quebra o layout |

**Isso impõe uma regra à G.3:** toda string extraída nasce com comentário de contexto. Extrair sem
comentar seria mais rápido e entregaria um catálogo que só serve para quem já conhece o app, ou
seja, para ninguém que vá traduzi-lo.

> **O tradutor não tem o app aberto.** Contexto que não está no documento não existe.

### [REGRA] O pt-BR é validado pelo Rafael ANTES de qualquer tradução

Nenhum idioma novo começa antes de o catálogo em português estar lido e aprovado. Isso é um portão
entre a G.3 e a G.4, não uma etapa opcional de revisão.

**Por que é regra e não boa vontade:** o pt-BR é o texto de ORIGEM. Toda tradução herda o que ele
tem de errado. Uma frase confusa em português vira uma frase confusa em inglês, e depois em mais
oito idiomas, todas fiéis ao original e todas ruins.

> **Corrigir a origem custa um. Corrigir depois de traduzido custa N**, e o N cresce a cada idioma
> que entra.

Há um segundo motivo, que só existe neste momento: a extração é a **primeira vez** que as ~500
frases do app aparecem juntas, fora do contexto da tela que as criou. É quando ficam visíveis coisas
que ninguém enxerga lendo um arquivo por vez:

- o mesmo conceito com nomes diferentes (`desafio` em uma tela, `grupo` em outra);
- textos idênticos com chaves diferentes, que deveriam ser uma só;
- frases escritas há meses que já não descrevem o que a tela faz.

Depois da G.4 essa revisão fica cara, porque cada ajuste vira retrabalho em todos os idiomas. Antes,
é só editar uma linha.

Para tornar a validação rastreável e não uma leitura vaga, o `gen_strings.py` emite o catálogo
agrupado **por tela**, na ordem em que a pessoa encontra os textos usando o app. Validar frase solta
em ordem alfabética é o jeito de não perceber inconsistência entre duas frases da mesma tela.

---

## 10. [INV] Nome de exercício é dado, não interface

`exercises.name` vem dos seeds V4, V9 e V28. Traduzir é **migração de dado com curadoria**, natureza
diferente de extrair string, e por isso é fatia própria (H).

**Mas é pré-requisito do critério de 100%.** Enquanto ela não acontece, não podemos dizer que o app
está traduzido: um usuário em inglês veria interface inglesa e "Supino reto". Fica registrado como
fatia com dono, não como débito solto.

### 10.1 [REGRA] Dado traduzido mora em TABELA, e o piso fica na coluna original (H.1, 2026-09-15)

A alternativa era `name_en`, `name_es`, duas colunas por idioma em `exercises`. Ela tinha uma
vantagem que esta modelagem **não tem**: o `when (idioma)` da leitura ficaria exaustivo, e idioma
novo no enum `Idioma` quebraria o build até alguém traduzir — o mesmo mecanismo da seção 3.3.

Recusada por um número: **o app vai ter mais de quatro idiomas**. Com dez, aquela vantagem vira
vinte colunas e um build vermelho que impede lançar QUALQUER idioma enquanto o último não estiver
pronto.

> **A escolha entre coluna e tabela não é de modelagem, é de quantos.**

A proteção mudou de dono: saiu do compilador e virou o `CatalogoTraduzidoIntegrationTest`, que
percorre `Idioma.TODOS` e acusa idioma **traduzido pela metade** — o estado perigoso, porque zero
traduzido é honesto (tudo no piso) e metade é o que ninguém percebe.

**O pt-BR não entra na tabela.** `exercises.name` assume o papel que o `Idioma.PADRAO` tem na cadeia
de fallback da seção 3.2, e a consequência prática é que **a leitura em português não faz join**.

⚠️ **O `LEFT` é o que decide quem aparece na lista.** Com `INNER`, trocar de idioma faria o catálogo
encolher para o número de traduzidos, sem erro nenhum.

> **Join que decide quem aparece na lista não é detalhe de consulta: é regra de produto escrita em
> SQL.**

### 10.2 [INV] Cache de texto traduzido tem de saber EM QUE IDIOMA está (H.2)

O cliente guarda o catálogo num banco local que tem um nome por exercício, não um por idioma. Duas
perguntas diferentes, e a decisão de ir à rede precisa das duas:

| pergunta | quem responde |
|---|---|
| está velho? | o carimbo do `SyncStamps`, agora com o idioma na chave |
| é o idioma certo? | o idioma gravado **na mesma transação** das linhas |

Carimbar por idioma sozinho resolve a IDA e não a VOLTA: voltando ao português, o carimbo está
fresco de ontem e a tabela cheia — de inglês.

> **Carimbo diz quando foi baixado. Ele não diz o que está guardado.**

E o idioma é gravado junto das linhas porque, fora da transação, uma falha no meio deixaria o banco
afirmando português com inglês dentro — e o app confiaria nisso até o TTL virar.

### 10.3 Quem PEDE o idioma é o cliente, não o servidor que já o tem

`GET /exercises?locale=en`, e não o servidor lendo `users.locale` da V47. Aquela coluna é cópia
reconciliada na abertura, **numa direção só** (seção 4): se o `PATCH /me` falhou sem rede, ela está
atrasada, e o servidor devolveria o idioma antigo enquanto o cliente carimbaria o cache com o novo.

> **Quem cacheia a resposta tem de ser quem escolhe a pergunta.**

A tag passa pelo `IdiomaPolicy.de`, nunca pelo `valida`: isto é entrada de SISTEMA (seção 3.2), e um
app antigo pedindo um idioma desconhecido recebe o piso, não um 400.

---

## 11. O que a bateria manual PRECISA verificar

Comportamentos corretos que **vão parecer bug** para quem testar sem saber:

| Passo | Resultado esperado |
|---|---|
| Trocar pt para en e abrir a central | Notificações **antigas continuam em português**. `[INV]` #36 |
| Trocar pt para en e abrir um programa antigo | O `rationale` **está em inglês**, porque é derivado (seção 7) |
| Trocar o idioma pelas configurações do Android | O app acompanha, **e o servidor também** |
| Provocar um erro do servidor em inglês | Frase em inglês e específica, não o fallback genérico |
| Atualizar o app com o celular em inglês | **Continua em português** até a pessoa escolher (seção 4) |

O primeiro e o segundo são opostos de propósito, e o contraste é o ponto: **notificação é registro,
rationale é descrição.** Sem os dois passos lado a lado, alguém uniformiza os dois e quebra um.

---

## 12. Fatiamento

| Fatia | Entrega | Critério de aceitação |
|---|---|---|
| **G.1** ✅ | V47, `Idioma` e `IdiomaPolicy`, `TextosDeAviso`, `Aviso` chave e params, `PATCH /me` | Push sai no idioma do destinatário |
| **G.2** ✅ | 67 `ErrorCodes`; cliente ramifica por código; 6 desmembramentos; 19 frases reescritas | Toda frase de erro vinda do cliente, com 9 exceções listadas |
| **G.3** | **721 literais** (não ~500) saem para `strings.xml` **com comentário de contexto**, ainda em português; rótulos de enum com chave derivada; `gen_strings.py` e o `docs/gerados/strings.md` | **Nenhuma tela mudou**, e o catálogo gerado é legível por quem não conhece o app |
| 🚪 | **PORTÃO: o Rafael valida o catálogo pt-BR.** A G.4 não começa antes | Aprovação explícita, seção 9 |
| **G.4** ✅ | `values-en`, tela de idioma em Conta, sincronização nos dois sentidos | Bateria manual |
| **G.5** ✅ | `ConquistaIds` no contrato e 18 frases de conquista no catálogo; `programs.name` derivado no cliente (**V48**) | Nenhum campo `String` de DTO carrega prosa, fora os 2 enumerados |
| **H.1** ✅ | **V49** `exercise_translations`, leitura com `LEFT JOIN`, `?locale=` | Exercício sem tradução continua na lista, no piso |
| **H.2** ✅ | O cache do cliente passa a saber em que idioma está | Trocar de idioma rebaixa o catálogo, **nas duas direções** |
| **H.3** | Carga dos ~960 nomes em inglês | Parada esperando a tradução. Pré-requisito para declarar "100%" |
| **H.4** | `description` e os dois regex do `ExerciseDetailScreen` | O aviso deixa de ser detectado por palavra e vira dado |

**A G.5 não estava neste fatiamento, e essa é a informação.** Ela nasceu da pergunta *"quais telas
faltam?"*, feita depois da G.4 com todos os documentos atualizados. O inventário respondeu
**nenhuma**, e respondeu certo dentro do que mede — literais do cliente. As três telas de conquistas
estavam inteiras em português, vindas de um enum do `:server`.

> **Perguntar "que tela falta" devolve a resposta do medidor, não a da tela.**

Fica como procedimento para a fatia H, em três passos e nesta ordem: varredura de literais, `.name`
de enum nas telas, e **leitura dos DTOs do contrato campo a campo procurando `String` que é prosa**.
O terceiro passo é o que achou a G.5 inteira, e não existia.

**A G.3 é separada de propósito.** Extrair e traduzir são trabalhos diferentes: um é mecânico e
volumoso, o outro é de julgamento. Numa fatia só, a bateria não distinguiria "extraiu errado" de
"traduziu errado", e com 500 strings essa distinção é a diferença entre uma hora e um dia de
investigação.

> **Trabalho mecânico em massa merece uma fatia onde o resultado esperado é "nada mudou".**

**O portão entre G.3 e G.4 é o que dá sentido a essa separação.** A G.3 termina com o catálogo
pronto e nenhuma tradução escrita, exatamente para que a validação do pt-BR aconteça enquanto
corrigir ainda é editar uma linha. Emendar as duas fatias faria a revisão da origem competir com a
pressa de ver o inglês na tela, e a origem perderia.

**A G.2 vem antes da G.3** porque cria códigos, não texto. Depois dela, as frases de erro entram no
`strings.xml` junto com todas as outras, numa extração só, em vez de virarem um segundo mutirão.

---

### ⚠️ Três lugares onde o CÓDIGO ramifica em português (achado na G.3)

Não são strings para traduzir: é lógica que depende do idioma, e **nenhum quebra o build**.

| Onde | O que faz | Ao traduzir |
|---|---|---|
| `ExerciseDetailScreen.warnRegex` | detecta parágrafo iniciado por *aviso/atenção/importante* | avisos perdem o destaque |
| `ExerciseDetailScreen.safetyRegex` | **remove** o boilerplate *"procure um profissional"* da descrição | o disclaimer aparece **duas vezes** |
| `PaywallScreen:165` | `value == "Sim"` decide se desenha o ✓ | o ✓ some e a palavra aparece crua |

> **Traduzir dado quebra o código que lê o dado.** O catálogo não é só texto: existe lógica
> interpretando o português dele.

**E o `warnRegex` está morto.** Dos 171 parágrafos de aviso do catálogo, **169 são o próprio
boilerplate** que o `safetyRegex` remove antes; os 2 restantes também falam de "profissionais
qualificados". Na prática o `NoteBox` de aviso nunca desenha nada. É código que não falha, só não
faz nada, e ninguém percebeu.

O `safetyRegex` remove **866 ocorrências** de um disclaimer que o app já anexa por conta própria a
todos os exercícios: o dado carrega, em toda linha, um texto que a tela sempre descarta.

**Saíram da G.3 de propósito.** Os dois primeiros dependem da descrição do exercício, que só muda na
fatia **H** — consertá-los antes seria decidir contra um dado que ainda não mudou. O terceiro entra
na **G.4**, junto com a tela do paywall. A G.3 volta a ter um critério verificável: *nenhuma tela
mudou*.

---

## Fica registrado como não resolvido

- **[PROPOSTA] Seguir o idioma do aparelho na primeira abertura.** Recusado agora (seção 4);
  reavaliar quando a lista de idiomas crescer.
- **[PROPOSTA] Ficha da Play Store e preço** continuam em pt-BR. É Fase 10, e é o que decide se
  "temos inglês" é verdade fora do app.
- **Unidades (kg/lb, cm/ft) estão FORA por decisão.** Mexem em dado persistido
  (`profiles.weight_kg`, `height_cm`) e no motor, então é decisão de produto e não localização.
- **A coluna `programs.rationale` fica vestigial** após a G.3. Remoção em migration futura.
