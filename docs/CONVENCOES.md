# Convenções fixadas

> **Permanente.** O que vale em toda sessão e não muda por fatia. Estado atual em `ESTADO.md`;
> regra de comportamento de área em `regras/`; decisão em `arch/`.
>
> Veio do antigo `painel-mestre.md` (encerrado em 2026-10-07) — eram as duas seções dele que não
> duplicavam nenhum outro documento.

---

## Convenções fixadas

- Pacote raiz `dev.rafael.*`; core `dev.rafael.core.*`; features `dev.rafael.features..*`; server `dev.rafael.server`; contrato `dev.rafael.contract`.
- Ordem: Backend → Contrato → Cliente (Data → Domain → Presentation). Feature nunca depende de feature; domain é Kotlin puro.
- Autoridade do servidor (XP, validação, IA, entitlement); cliente só cópia otimista; nunca o relógio do cliente. Gate de edição/entitlement decide no server (`requireEditable`); UI só antecipa p/ não dar erro na cara.
- C1 explicar antes de construir · C2 testar ao fechar (fakes sem mock; kotlin.test; Testcontainers; coroutines-test).
- **Nome de teste em backticks não aceita `"` no Windows** (2026-09-08): o Kotlin gera um `.class` por lambda usando o nome do teste, e aspas duplas são caractere ilegal em caminho — o compilador quebra com `InvalidPathException` e um stack trace de 80 linhas que não menciona o teste culpado. Vale para `:` `*` `?` `<` `>` `|` também. Usar palavras em vez de citar o texto testado.
- **Nome de task nos módulos KMP:** o plugin novo de `androidLibrary` não gera `compileKotlinAndroid` nem `testDebugUnitTest`. Use `testAndroidHostTest` (compila main + test e roda). `testDebugUnitTest` só existe no `:app:androidApp`.
- **Suíte completa são CINCO tasks:** `:konsist:test` · `:server:test` · **`:server:integrationTest`** (Postgres real via Testcontainers — **precisa de Docker**) · `testAndroidHostTest` (módulos KMP) · `:app:androidApp:testDebugUnitTest` (ViewModels do app). Esta lista já mentiu **duas vezes**, e das duas o custo foi real:
  - dizia "duas" e esquecia a do `app`: entre a B.3 e a A.0 esse source set não compilava e ninguém percebeu. A CI passou a rodá-lo em 2026-08-20.
  - dizia "três" e esquecia a **de integração**, que não roda junto por exigir Docker. Fechei o #35 e a F.1 sem ela, e a V40 (`users.code` NOT NULL) quebrou **51 dos 62** testes de integração — descoberto só no GitHub Actions, depois do merge da fatia anterior.

  > **Migration que aperta uma coluna quebra todo INSERT direto que já existia** — e teste de integração, seed e script SQL são exatamente isso. Ao mexer em `NOT NULL`/`UNIQUE`/`CHECK`, procure `INSERT INTO <tabela>` fora do repositório.
- **Falha engolida de propósito passa pela porta `Falhas`** (C9, 2026-10-09; server): `Falhas.registrar(contexto, erro)` em `dev.rafael.server.error`, impl `FalhasSlf4j` no Koin. Serviço que engole exceção/erro (push, aviso de conquista, notificação) NUNCA chama `log.warn` direto: recebe `Falhas` sem default e o teste afirma `FalhasFake.registradas` no caminho de falha. Erro que sobe como `AppResult.Failure` não usa isto. Cliente fora por ora.
- **Todos os módulos em JVM 17**, o `app` inclusive (corrigido em 2026-08-20; estava em 11, o que impedia inlinar `AppResult.map`/`flatMap` do core lá dentro). Ao criar módulo novo, alinhe — divergência de `jvmTarget` aparece como erro de bytecode incompreensível, não como erro de configuração.
- **Leitura é cache-first, sempre** (#30): a tela lê do banco local; a rede é sincronizador. Erro de rede não vira erro de tela quando há dado local.
- **Frescor:** TTL para o que você NÃO controla; `invalidate()` para o que você controla. Invalidar cache é consequência de mutação, nunca de navegação.
- **Emenda (2026-08-21, fatia A.3):** dado que muda por ação de **TERCEIROS** vai à rede **ao entrar na tela**, ignorando o TTL. O TTL protege bem o que muda pela ação do próprio usuário (o XP da Home), porque aí a invalidação na mutação cobre tudo. A contagem de membros de um grupo muda porque **outra pessoa entrou** — esperar cinco minutos ali não é economia, é atraso, e o usuário não tem o que fazer além de esperar. O custo é uma requisição pequena por entrada na aba, não as seis que motivaram o TTL.
- **Todo dado e todo carimbo local são chaveados por uid** — o isolamento por conta deve ser consequência da CHAVE, não de um `if` que alguém precisa lembrar de escrever.
- **Escrita é um caminho só** (#30): grava local → enfileira no outbox → agenda o envio, com ou sem rede. Dois caminhos ("online manda direto") deixam o offline mal testado.
- **O outbox cobre o que o usuário POSSUI**: conteúdo `MANUAL` é otimista; conteúdo `AI` é travado por entitlement (#25) e continua online-only — a recusa tem de chegar no momento da ação, não pela fila minutos depois.
- **Falha temporária × permanente** é a regra de maior consequência da fila: 4xx de recusa para de tentar; sem rede / 5xx / 401 continua. Na primeira falha temporária o processamento PARA (a ordem entre alvos é dependência real).
- **Recompensa concedida NUNCA é retirada** (#32): XP e nível podem ser 100% derivados porque são números; conquista não pode, porque mudar um limiar faria a medalha sumir do perfil de quem já a viu. Regra derivada e pura, desbloqueio persistido com data.
- **Escrita otimista exige selo na UI** — sem ele o usuário não distingue "está no servidor" de "está só neste aparelho".
- **Fronteira de privacidade é o DTO, nunca uma condicional** (#34): dado privado não existe no contrato público. Isolamento que depende de alguém lembrar de escrever um `if` falha — já falhou com o uid no cache local.
- **Identidade em contexto social é `display_name`, nunca e-mail** (#33): a coluna é NOT NULL com backfill, para não espalhar `?: "Usuário"` por toda tela que mostra gente. O e-mail não atravessa a fronteira do grupo.
- **Estado que é função de datas é DERIVADO, não persistido** (#22, #16, #33): sem coluna `status`, sem job de virada — job é mais uma peça falhando em silêncio.
- **Erro é `AppError` até a UI** (#31): nunca `String` no state. Quem escolhe o texto é a camada que conhece a plataforma.
- DTO nunca vaza p/ o domínio (mapper na data). NavHost puro. Datas no server: `kotlin.time.Clock`.
- Trabalho fire-and-forget que não pode morrer com a tela vai no appScope (singleton em appModule), não no viewModelScope.
- Ritual de fechar fatia: main → commit → atualizar HANDOFF + fragmentos + ADR novo → regenerar PDF. **As FONTES são atualizadas antes do gerador** (2026-09-07): `gen_tudo.py` lê `docs/fontes/`, então rodar sem editar produz PDF com data de hoje e conteúdo de anteontem — pior que PDF velho, porque parece confiável. > **Regenerar não é atualizar.**
- **Fake sem o índice do banco prova o oposto do que se quer** (E.2): os dublès reproduzem os índices únicos reais — `(grupo, usuário, dia)` no check-in, `(checkIn, user)` na reação, `(alvo, denunciante)` na denúncia. Um fake que guardasse em lista deixaria "uma por pessoa" passar contando 2, e o defeito só apareceria em produção.
- **Ramificar por um código só e depois multiplicar os códigos quebra a ramificação sem quebrar o build** (#37, G.2): o `ErrorUi` abria o paywall com `if (code == ENTITLEMENT_REQUIRED)`, e dar código próprio a cada portão de plano quase o apagou em silêncio. O conjunto `ErrorCodes.PORTOES_DE_PLANO` mora no CONTRATO, e um teste percorre ele inteiro. Vale para toda ramificação por valor único que um dia pode virar vários.
- **Guarda que barra por dois motivos precisa de duas frases** (#37, G.2): `podeResponder` é uma condição só e recusa por "já foi respondido" **ou** "é o seu próprio pedido". Dar a mesma frase aos dois fez o app afirmar algo falso, e foi o erro que a fatia cometeu enquanto o corrigia. Quem apontou foi um teste escrito muito antes.
- **Desmembrar por caminho de código, e não por situação do usuário, multiplica trabalho sem mudar uma tela** (#37, G.2): as 104 ocorrências de erro eram 87 frases distintas; *"Programa não encontrado"* aparecia em 7 lugares que são a MESMA coisa para quem usa. O critério é a situação de quem lê, nunca a linha que percebeu.
- **Débito que ninguém consegue enumerar não é débito, é surpresa** (#37, G.2): as 9 frases de erro que carregam número do servidor não serão traduzidas, e por isso estão LISTADAS em `TextosDeErro.SEM_TEXTO_PROPRIO`, com teste exigindo que todo código tenha texto ou apareça ali.
- **Texto que o usuário lê vive em UM lugar: o `strings.xml` do cliente** (#37, 2026-09-09). O servidor manda dados, códigos e chaves, nunca prosa. Duas fontes de tradução divergem na primeira que alguém esquecer de atualizar, e um catálogo só significa uma verificação só (o `MissingTranslation` do Lint). **Exceção única: a notificação**, porque push só-dados não aparece com o app fechado (#36) e o texto precisa sair pronto do servidor.
- **Sem travessão em texto de usuário** (#37): nem `—` nem `–`, em nenhum idioma, nem na documentação. **Verificado por teste**, não combinado, porque quem traduzir o sétimo idioma não vai ler o ADR. Já havia uma violação anterior à regra, no `StructureEngine.rationale`, achada por acaso na saída de um teste que não é sobre texto. Desde a G.3 a varredura alcança o `strings.xml` inteiro, e os 15 que existiam no cliente foram reescritos.
- **Sem caixa alta cravada no texto** (#37, G.3): caixa é decisão de DESENHO, e desenho morando no dado quebra em quem traduz — alemão capitaliza substantivos por gramática, turco tem duas letras i, e há escritas sem caixa nenhuma. O Compose **não tem `text-transform`**, e `.uppercase()` imporia a caixa a todo idioma; a única saída que não mente custou a aparência de 12 cabeçalhos, mudada de propósito em 2026-09-10.
- **Débito enumerado só tem dois finais decentes** (#37, G.3): ou é pago e o teste ENDURECE (a lista vira proibição), ou a lista vira mobília. Travessão e caixa alta foram enumerados durante a extração e proibidos no portão; a única lista que sobrou é a dos textos repetidos, porque lá a decisão foi MANTER.
- **Duplicata de texto sobrevive num idioma e diverge no segundo** (#37, G.3): é o argumento da `Rotulos.kt`, e a fatia o viu se cumprir — `MuscleGroup.FOREARMS` era "Antebraço" numa tela e "Antebraços" noutra **antes de qualquer tradutor entrar**. Um teste enumera os 19 pares idênticos que restam, cada um com o motivo. **Cinco são falsos duplicados** (`Sair` do desafio × da conta, `Descanso` dia × cronômetro, `Fazer check-in` abrir × enviar, `Treino de hoje` rótulo × nome, `FitJourney` marca × título) e têm aviso cruzado nas duas pontas: unificá-los quebra uma tela num idioma que ninguém desta equipe lê.
- **Rótulo derivado do nome do enum não é rótulo, é o identificador com maiúscula** (#37, G.3): aconteceu duas vezes — `ExerciseCategory` mostrando "Chest" ao usuário brasileiro, e `GroupRule` mostrando `localizacao` sem cedilha. Rótulo de enum mora em `ui/Rotulos.kt` e devolve `@StringRes Int`, nunca `String`: assim não precisa de `Context` nem de `@Composable`, e o teste compara ids sem Android.
- **Campo que o servidor compara não é rótulo, mesmo escrito em português** (#37, G.3): `SplitType.label` está documentado como "texto de UI" e o `WeekSpread` faz `split == SplitType.FULL_BODY.label`. O cliente passou a ler o catálogo e o contrato ficou intacto. Mesma família do sentinela `value == "Sim"` do paywall, adiado para a G.4. > **Sentinela escrito em português é código ramificando em idioma.**
- **Extrair a palavra e deixar a concordância no código traduz o vocabulário, não o idioma** (#37, G.3): `if (n == 1) "dia" else "dias"` é gramática do português cravada no cliente. Doze `<plurals>`, e um teste exige `one` e `other` em cada — russo tem três formas, árabe seis, e o inglês trata zero como plural.
- **⚠️ Curinga de import + `R` = 80 erros que não mencionam import nenhum** (#37, G.3): `material3.*` e `layout.*` trazem os `R` das bibliotecas para o escopo, e sem `import dev.rafael.app.R` explícito o compilador diz *"class R does not have a companion object"*. Mordeu em três arquivos. **O import do `R` entra ANTES de qualquer substituição.**
- **`joinToString { stringResource(...) }` não compila** (#37, G.3): `joinToString` não é inline e a lambda não herda o escopo `@Composable`; `map` é inline e herda. Resolver, depois juntar. Da mesma família: `stringResource` dentro de `try/catch` é terreno em que o compilador do Compose já foi restritivo — resolva o `runCatching` primeiro e formate depois.
- **Idioma no fio é `String`, nunca enum** (#37): mesma razão já escrita no `ErrorResponse` sobre o `code`. Enum serializado faria um idioma novo derrubar a desserialização do `/me` INTEIRO num cliente antigo, e a pessoa perderia a tela por causa de um idioma que não escolheu.
- **A cadeia de fallback de idioma existe ANTES da lista** (#37): exato, depois só a língua ignorando a região, depois o piso `pt-BR`. Escrita e testada com dois idiomas justamente porque adicionar o terceiro só funciona se ela já existir. > **Idioma que não existe precisa cair em algum lugar decidido, ou cai onde der.**
- **Entrada de sistema tolera, entrada de usuário recusa** (#37): `IdiomaPolicy.de` nunca falha (coluna do banco, locale do aparelho); `IdiomaPolicy.valida` devolve `null` (corpo de `PATCH`). Quem pede `es` e recebe `200 OK` acredita que escolheu espanhol, e atribui a falta de tradução a um defeito do app.
- **Requisição que falha não pode ter mudado metade** (#37): o `PATCH /me` valida os dois campos ANTES de qualquer escrita. Com um campo só o caso não existia; com dois, nome válido mais idioma inválido gravaria o nome e devolveria erro.
- **Verificação que o compilador faz não precisa de teste, e não pode ser esquecida** (#37): `ChaveDeAviso` é sealed e os `when` por idioma são exaustivos. Aviso novo quebra o build em todo idioma não traduzido; idioma novo quebra o `render`. É o `MissingTranslation` do lado do servidor, que lá não vem de graça.
- **⚠️ `:konsist:test` fica `up-to-date` sem rodar** (2026-09-09): o Konsist lê os fontes em tempo de execução e o Gradle não os conhece como entrada da task. Um verde de 7s com "12 up-to-date" **não varreu nada**. Use `--rerun-tasks` ao fechar fatia. Mesma lição da V40 com outra roupa: **suíte que não roda não cobre nada.**
- **A suíte de integração compartilha UM Postgres** (`BancoDeTeste`, 2026-09-09), limpo por `TRUNCATE` no `@BeforeAll` — o isolamento é por **classe**, nunca por teste. Duas consequências que valem como regra:
  - **Asserção sobre a tabela inteira assume um isolamento que esta suíte não dá.** Afirme sobre o cenário (o par, o grupo, o usuário), nunca sobre um `count()` global. Já cobrou: um `blocks.count() == 0` falhou por bloqueios de testes anteriores, sem relação com a cascata afirmada.
  - **Estado global de conexão não convive com duas estratégias de container.** `Database.connect` do Exposed define o default de todo `transaction { }` do processo; uma classe com container próprio que o chame deixa a seguinte com um datasource morto — 39 falhas com `SQLException`, e a primeira da ordem passando, o que fez o erro parecer aleatório. `MigrationIntegrationTest` e `DisplayNameBackfillIntegrationTest` mantêm container próprio de propósito (testam o schema **nascendo**) e falam JDBC cru justamente por isso.

  > **Suíte lenta é suíte que ninguém roda.** O critério da mudança não foi elegância: esta suíte atravessou duas fatias sem ser executada, e quando rodou a V40 tinha derrubado 51 de 62.
- **Model que não representa o que a tabela permite empurra alguém a contornar o model** (2026-09-09): `AcaoDeModeracao.adminId` era `Uuid` não-anulável enquanto a coluna aceita `NULL`, e o `encerrarCasosPendentes` inseria `null` **direto na tabela**. Duas verdades sobre a mesma coluna, e a do Kotlin era a errada. Achado pelo compilador ao escrever o teste de integração — ele recusou um caso que o banco aceita todo dia.
- **Teste que passa por dois motivos não prova nenhum dos dois** (E.2): o cenário em que o admin também era dono do check-in deixava o teste da autoridade dele verde por acidente — continuaria passando se a regra do admin sumisse.
- **Reiniciar o Ktor faz parte de cada correção de servidor** — três investigações entre a E.2 e a F custaram isso. `installDebug` atualiza o app; o processo Ktor precisa ser derrubado à mão, e o sintoma de esquecer é o mais enganoso possível: o comportamento antigo continua e parece que a correção não funcionou.
- **Notificação gravada nunca muda de texto** (#36). Corrigir uma frase não altera as que já estão na bandeja ou na central — só as novas. É o invariante funcionando, e engana quem está testando.
- **`current_date` do Postgres é UTC** (F): depois das 21h em São Paulo, `- 1 day` ainda é *hoje* para um grupo em `America/Sao_Paulo`. Comando de teste que depende disso acusa o código sem motivo.
- **Teste de comportamento negativo precisa provar que chegou lá** (F): um `isFailure` sozinho é satisfeito por qualquer falha, inclusive a que acontece **antes** do trecho em teste. Foi o que fez um teste passar sem exercitar nada.
- **Não aplique correção em massa sem medir antes** (bancada, E.2): um `UPDATE` de teste com `where status = 'VALIDO'` em vez de `where id = ...` atingiu 42.784 linhas do `seed_volume`. Reverter às cegas teria criado o erro na direção oposta, sem um "antes" para comparar.
- **Backup de cache restaura mentira** (2026-08-23): `allowBackup="true"` devolveu ao app um catálogo com caminhos `.png` que já não existiam, sobrevivendo a `pm clear` e a reinstalar. Cache local do app fica **fora do backup** — e mudar dado que o cliente cacheia **não é uma migration, são duas**: uma no banco e uma no tempo.
- **Instrumento vence adivinhação**: o N+1 de `meusGrupos` apareceu porque o seed criou volume para LER no log, não porque alguém suspeitou dele ou rodou teste de carga.
- **O fake tem que implementar a REGRA, não devolver constante** — um teste de integração passou a mentir porque todos os check-ins compartilhavam o mesmo `photoRef`.
- **Par de asserções duvidoso mora no MESMO teste**, senão "consertar" uma delas quebra a outra em silêncio.
- **Texto que descreve posição envelhece a cada mudança de layout** ("apague o check-in abaixo" quebrou quatro vezes).
- **`MutableStateFlow` de ViewModel só se escreve com `update`** — read-modify-write através de uma suspensão perdeu a lista de grupos inteira.
- **Reiniciar o Ktor faz parte de rodar a bateria manual.**
- **Todo defeito que escapou vira teste** — e o caminho que a MÃO não alcança também: modo avião não testa a tela B quando a tela A, que vem antes, já trata o offline (achado da bateria da C.1).
- **Migration no TÍTULO do PR quando ela toca dado que já existe** (`ALTER`, backfill, `DELETE`, mudança de constraint). `CREATE TABLE` puro fica só no checklist — não há o que dar errado com dado de ninguém. O critério é "o que eu ia querer ler primeiro se algo quebrasse em produção": tabela nova não é suspeita; coluna `NOT NULL` acrescentada a `users` é a primeira. O título é o que sobra no `git log` depois do squash.

## O que ATRAVESSA a sessão — a classe de defeito mais cara do cliente

Componentes criados junto com o `AppNavHost` vivem enquanto a Activity viver e **atravessam logout
e login sem serem recriados**. O `MenuViewModel` já acumulou **quatro** defeitos por isso, todos
com o mesmo formato: um valor resolvido uma vez, num instante em que a resposta ainda era outra.

| Defeito | O que ficava preso | Corrigido com |
|---|---|---|
| "?" eterno no cabeçalho (A.0) | a chave do cache, resolvida no início da coleta | `TokenProvider.uidFlow()` |
| Nome da conta anterior sobrando | idem | idem |
| Segunda saída não disparava | `saiu = true` para sempre — evento modelado como estado | `consumirSaida()` |
| Esqueleto girando para sempre (D) | "carregando" e "falhou" eram o mesmo estado | fallback para o e-mail local do Firebase |

A F.1 repetiu o padrão em outro lugar: o registro de push num `LaunchedEffect(Unit)`, que não
redispara no login, e o `filterNotNull()` antes do `distinctUntilChanged()`, que engolia o segundo
login da mesma conta.

> **Antes de resolver um valor, pergunte se ele sobrevive a um logout.** Se o componente nasce com
> o `AppNavHost`, a resposta é quase sempre não — e o que resolve é observar a sessão, não ler uma
> vez.

**Coberto desde 2026-09-06.** A regra saiu do `AppNavHost` para `ReagirASessao`, e o
`ReagirASessaoTest` atravessa a fronteira que nenhum teste atravessava: `"u1" → null → "u1"`.
Duas lições do caminho:

- **Regra escondida dentro de UI é regra sem teste.** Enquanto o código vivia num `@Composable`,
  exercitá-lo exigiria compor a árvore inteira — e foi ali que os dois defeitos invisíveis moraram.
- **O dispatcher de teste decide o que o teste consegue observar.** Com `StandardTestDispatcher` os
  quatro testes que esperavam registro viam zero; para quem **observa** um `Flow`, o certo é
  `UnconfinedTestDispatcher`.

