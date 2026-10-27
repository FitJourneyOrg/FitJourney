# Fatia G — idioma (G.1 a G.5)

> **Histórico.** Fatia fechada — preservado literal do antigo `handoff-retomada.md`.
> Estado atual em `docs/ESTADO.md`; convenções em `docs/CONVENCOES.md`;
> regra de comportamento em `docs/regras/`; débitos em `docs/fontes/debitos.md`.

---

## 🔤 FATIA G — IDIOMA. G.1, G.2, G.3 e G.4 FECHADAS (2026-09-11)

Migrations até **V47**. A G.3 é só de cliente: nenhuma migration, nenhum arquivo de servidor.

**Suíte verde nas cinco tasks, medida em 2026-09-10 com `--rerun-tasks`: 739 testes em 88 arquivos,
zero falhas, zero pulados.** A G.3 acrescentou 12 testes em 3 arquivos ao `:app:androidApp`, que foi
de 63 para **75 em 14**.

| task | arquivos | testes |
|---|---:|---:|
| `:server:test` | 42 | 450 |
| `:server:integrationTest` | 13 | 132 |
| `:app:androidApp:testDebugUnitTest` | 14 | 75 |
| `testAndroidHostTest` (18 módulos KMP) | 18 | 76 |
| `:konsist:test` | 1 | 6 |

⚠️ **Rode o Konsist com `--rerun-tasks`.** Sem isso ele fica `up-to-date` e dá verde sem varrer.

> **Número de teste que ninguém mediu é pior que número nenhum: parece verificado.**

⚠️ **Rode o Konsist com `--rerun-tasks`.** Sem isso ele fica `up-to-date` e dá verde sem varrer.

**Objetivo declarado:** quando estiver 100%, manter **100% da UI traduzida**, com ressalva de ações
humanas (comentário de usuário não se traduz). Primeira leva: **pt-BR e en**. A estrutura é aberta
para N idiomas; a lista completa chega depois, já traduzida.

**Decisão que estrutura tudo (ARCH #37):**

> **Texto que o usuário lê vive em UM lugar: o `strings.xml` do cliente.** O servidor manda dados,
> códigos e chaves, nunca prosa.

A exceção é uma só e é principiada: **a notificação**, porque push só-dados não aparece com o app
fechado (já decidido no #36). Por isso o servidor tem um catálogo, e só um: os ~14 textos dos 7
avisos, em `TextosDeAviso`.

### O que a G.1 entregou

- **V47** `users.locale`, NOT NULL, default `pt-BR`, `CHECK` de vocabulário fechado.
- **`Idioma` e `IdiomaPolicy`** no `shared-contract`, usados pelos dois lados. Cadeia de fallback:
  exato, depois só a língua ignorando a região (`pt-PT` cai em `pt-BR`), depois o piso.
- **`Aviso` virou chave mais parâmetros.** Quem renderiza é o `NotificacaoService`, que já lia o
  usuário para pegar os tokens.
- **`PATCH /me`** com `displayName` e `locale` anuláveis. `null` significa não mexer.

### Três coisas para lembrar antes de mexer nisto

1. **`locale` viaja como `String`, nunca como enum.** Precedente do `ErrorResponse`: *"código novo
   no server não quebra cliente antigo"*. Enum no fio faria um idioma novo derrubar a
   desserialização do `/me` INTEIRO num app antigo.
2. **O compilador é o `MissingTranslation` do servidor.** `ChaveDeAviso` é sealed e os `when` de
   cada idioma são exaustivos: aviso novo quebra o build em todo idioma não traduzido, idioma novo
   quebra o `render`. Trocar isso por enum mais mapa perderia a verificação sem avisar.
3. **Ninguém tem o idioma trocado por uma atualização.** Quem já usa continua em pt-BR mesmo com o
   celular em inglês. O inglês passa a existir na tela de Conta, por escolha explícita.

### Sem travessão, sem caixa alta

Nem `—` nem `–`, em nenhum idioma, nem na documentação. Verificado por teste no `TextosDeAviso` **e,
desde a G.3, no `strings.xml` inteiro** (`CatalogoDeStringsTest`). Os 15 que existiam no cliente
foram reescritos no portão do pt-BR, em 2026-09-10.

Caixa alta também não, e essa é decisão nova da mesma data: 12 cabeçalhos vinham em maiúsculas
cravadas no texto e passaram a caixa normal. **As telas mudaram de aparência de propósito** — o
Compose não tem `text-transform`, e deixar a caixa no dado obrigaria todo idioma a herdar uma
escolha tipográfica feita olhando só para o português.

### O que a G.2 entregou (2026-09-10)

As 103 frases de erro do servidor saíram de lá. **67 códigos, 87 frases distintas no cliente.**

- **Não eram 104, eram 87.** *"Programa não encontrado"* estava em 7 lugares, *"Grupo não
  encontrado"* em 5. Consolidar onde a situação é a mesma tirou 17 traduções do caminho.
- **Seis frases cobriam situações opostas** e foram desmembradas. A pior: *"Usuário não
  encontrado"* servia a "a outra pessoa sumiu" **e** a "a sua própria conta sumiu".
- **19 frases reescritas**, tirando jargão (`id`, `cursor`, `workoutId`) e o único travessão do
  catálogo.
- **`docs/gerados/erros.md`**, gerado, com o contexto de cada erro. É o documento de validação.

**Três coisas para não desfazer sem pensar:**

1. **`ErrorCodes.PORTOES_DE_PLANO`** é um CONJUNTO, e o `ErrorUi` compara com ele. Era
   `if (code == ENTITLEMENT_REQUIRED)`, e dar código próprio a cada portão **quase apagou o paywall
   sem quebrar build nem teste**.
2. **`TextosDeErro.SEM_TEXTO_PROPRIO`** lista as 9 frases que carregam número do servidor e por
   isso **não serão traduzidas**. Um teste exige que todo código tenha texto ou esteja na lista.
3. **Código desconhecido cai na `message` do servidor.** É a lição do #31 sobrevivendo: servidor
   novo com app antigo continua explicando melhor.

## ✅ G.3 CONCLUÍDA — extração dos literais (2026-09-10)

**634 literais extraídos. 605 chaves no catálogo (593 `string` + 12 `plurals`). Zero literais em
posição forte** — não sobrou um `Text("…")`, `label=` ou `title=` em português no app inteiro.

O inventário caiu de **673 candidatos em 49 arquivos** para **39 em 8**, e os 39 restantes estão
todos declarados:

| Onde | Quantos | Por quê |
|---|---:|---|
| `PaywallScreen` | 23 | dívida adiada para a **G.4** por decisão de 2026-09-10 (ver abaixo) |
| `ExerciseDetailScreen` | 5 | os dois regex e o aviso de segurança — **fatia H** |
| repositórios, `Localizador`, push | 11 | falsos positivos: chaves de cache, `@SuppressLint`, logs |

Rode `python docs/geradores/inventario_strings.py` para conferir; o número tem de continuar 39.

### A convenção (documentada no topo do `strings.xml`)

**Chave = `<area>_<elemento>`**, e a área vem do **pacote da tela**, não do que a frase diz.

> Chave que descreve o texto envelhece quando o texto muda; chave que descreve o lugar sobrevive.

Quatro prefixos especiais: `comum_` (usado em mais de uma tela), `enum_<familia>_<valor>`,
`erro_<codigo>` e `data_` (dia/mês abreviados). O resto por área (`login_`, `grupo_detalhe_`,
`checkin_`). **Todo string tem comentário de contexto, e um teste exige isso.**

### Os três mecanismos que a fatia acrescentou

**1. `ui/Rotulos.kt` — rótulo de enum num lugar só.** Cresceu de ~45 para ~90 termos:
`MuscleGroup`, `Level`, `Goal`, `TrainingEnvironment`, `ExerciseCategory`, equipamento, prescrição,
**`GroupRule`, `GroupState`, `JoinBlock`, `CheckInStatus`, `SplitType`, `BodyLimitation`** e os dias
da semana. Devolvem `@StringRes Int`, nunca `String`: continuam Kotlin puro e testáveis sem Android.

**2. `ui/Frase.kt` — a fronteira "quem escreve a frase" virou TIPO.** `Frase.Recurso(@StringRes)` ou
`Frase.DoServidor(String)`. Existe porque o `ErroVisual.texto` é bimodal: catálogo quando o código é
conhecido, `message` do servidor quando não é (a lição do #31). Guardar isso como `Int?` + `String?`
compila igual e deixa o ramo esquecido passar em silêncio.

**3. `<plurals>` — a concordância saiu do Kotlin.** Doze plurais. Havia `if (n == 1) "dia" else
"dias"` espalhado: isso é **gramática do português cravada no cliente**, e traduzir a palavra não
conserta a regra — russo tem três formas, árabe seis, e o inglês trata zero como plural.

> **Extrair a palavra e deixar a concordância no código traduz o vocabulário, não o idioma.**

### ⚠️ AS TRÊS ARMADILHAS. Custaram build quebrado, e voltaram em toda área.

**1. O import do `R` entra ANTES de qualquer substituição.** Os curingas do Compose
(`material3.*`, `layout.*`) trazem os `R` das bibliotecas para o escopo. Sem
`import dev.rafael.app.R`, você recebe ~80 erros em cascata que **não mencionam import nenhum** —
dizem *"class R does not have a companion object"*. Aconteceu no `QuizScreen`, `QuizSteps` e
`PaywallScreen`, os três arquivos com curinga.

**2. Interpolação vira placeholder posicional, nunca concatenação.** `"Também: " + lista` →
`stringResource(R.string.x, lista)` com `%1$s`. O índice vem **antes** das flags (`%1$02d`). Um
teste varre o catálogo inteiro atrás de `%s` sem posição.

**3. `map` antes de `joinToString`.** `joinToString { stringResource(...) }` **não compila**:
`joinToString` não é inline e a lambda não herda o escopo `@Composable`. `map` é inline e herda.

**Quarta, descoberta nesta rodada:** `stringResource` dentro de `try/catch` é terreno em que o
compilador do Compose já foi restritivo. O `dataCurta` do Progresso resolve o `runCatching`
primeiro e formata depois.

### As três listas de débito, e o que aconteceu com elas

A extração produziu débito enumerado por teste. **No portão do pt-BR, em 2026-09-10, duas foram
pagas e viraram proibição:**

| | Durante a G.3 | Agora |
|---|---|---|
| **Travessões** | 15 enumerados | **proibidos** — reescritos, teste falha se voltar |
| **Caixa alta** | 12 enumeradas | **proibida** — 12 cabeçalhos em caixa normal, **as telas mudaram de aparência de propósito** |
| **Textos repetidos** | 19 pares | **mantidos**, cada um com o motivo escrito |

> **Débito enumerado só tem dois finais decentes: ou é pago e o teste endurece, ou a lista vira
> mobília.**

**Travessão:** onde ele introduzia a consequência virou `:` ou `.`; onde juntava orações
coordenadas, virou `,`. O décimo quinto era o travessão **sozinho**, marcando "não preenchido" na
Conta — virou `Não informado`. Marcador tipográfico não é palavra: leitor de tela lê um traço.

**Caixa alta:** o Compose **não tem `text-transform`**. Ou a caixa está na string, ou o código chama
`.uppercase()` — e `.uppercase()` impõe a caixa a todo idioma e erra o i turco. A terceira saída é
a única que não mente, e custa a aparência de 12 cabeçalhos.

**Repetidos:** três famílias. Nove pares são enums distintos que coincidem em pt-BR
(`MuscleGroup` × `ExerciseCategory`); unificar acoplaria dois enums do contrato. **Cinco são falsos
duplicados, e são os perigosos** — cada um tem aviso cruzado nas duas pontas:

| par | não é a mesma coisa |
|---|---|
| `grupo_detalhe_sair_confirmar` × `menu_sair` | sair do DESAFIO × encerrar a SESSÃO (*Leave* × *Log out*) |
| `programa_detalhe_descanso` × `sessao_descanso` | dia sem treino × cronômetro entre séries (*Rest day* × *Rest*) |
| `grupo_detalhe_fazer_checkin` × `checkin_enviar` | ABRIR a tela × ENVIAR o check-in (*Check in* × *Submit*) |
| `home_secao_treino_de_hoje` × `home_treino_de_hoje` | o RÓTULO do card × o NOME de um treino sem nome |
| `app_name` × `login_titulo` | a marca (não se traduz) × o título da tela |

Os quatro últimos são "mesma ação, telas de contexto diferente" e ficaram separados por decisão:
`erro_acao_voltar` × `comum_voltar`, `erro_acao_tentar_de_novo` × `programa_reveal_tentar_de_novo`,
`checkin_invalidar_confirmar` × `moderacao_invalidar`, `login_email` × `conta_email`.

> **Duplicata que alguém "limpa" sem ler o comentário quebra uma tela num idioma que ninguém desta
> equipe lê.**

### Os 5 arquivos de teste que a fatia deixa (26 testes, em `app/.../ui/`)

| | O que impede |
|---|---|
| `CatalogoDeStringsTest` (10) | forma do catálogo: comentário obrigatório, placeholder posicional, plural completo, sem travessão, sem caixa alta, sem identificador vazado, e a lista dos repetidos |
| `TextosDeErroTest` (8) | todo `ErrorCode` tem frase **e a chave é derivada do código** (`PESSOA_NAO_EXISTE` → `erro_pessoa_nao_existe`) |
| `ErrorUiTest` (6) | a política do #31 sobrevive: código conhecido → cliente, desconhecido → servidor |
| `TextoDoServidorTest` (1) | **conta** os 9 pontos onde texto do servidor chega à tela, por arquivo |
| `LiteraisAdiadosTest` (1) | a dívida do paywall não cresce |

O `CatalogoDeStrings` lê o `res/values/strings.xml` **como arquivo**, num teste JVM comum: `R` num
teste devolve um `Int` sem o texto, e resolver id para frase exigiria Robolectric.

> **Invariante que só vale para a área que você está extraindo não é invariante, é revisão.**

### ⚠️ Cinco defeitos que a extração desenterrou

1. **`GroupRule` mostrava `localizacao` sem cedilha.** O detalhe do desafio derivava o rótulo de
   `it.name.lowercase().replace('_',' ')`. **Segundo caso** do mesmo padrão — o primeiro foi o
   `ExerciseCategory` mostrando "Chest" ao usuário brasileiro.
   > **Rótulo derivado do nome do enum não é rótulo, é o identificador com maiúscula.**
2. **`MuscleGroup.FOREARMS` já tinha divergido:** "Antebraço" no `QuizSteps`, "Antebraços" no
   `ExerciseDetailScreen`. Um idioma, duas cópias, e já não combinavam.
3. **Duas `AppError.Unexpected("frase")` que ninguém lia** (`WorkoutSessionViewModel`,
   `PaywallViewModel`). `Unexpected` não carrega código, o `ErrorUi` escreve o texto da família e
   **descarta a `message`**. Removidas.
4. **Quatro cópias dos nomes dos dias da semana**, em três grafias. Hoje há duas famílias, de
   propósito: `quiz_dia_*` (etiqueta de chip, capitalizada) e `data_dia_*` (linha de data compacta,
   minúscula). A ordem é ISO e é **contrato**, não apresentação — o índice vira o número enviado.
5. **`SplitType.label` está documentado como "texto de UI" e não é:** o `StructureEngine` o grava no
   `ProgramSkeleton` e o `WeekSpread` faz `split == SplitType.FULL_BODY.label`. **O servidor compara
   aquele campo.** O cliente passou a ler `SplitType.rotulo()` do catálogo e o contrato ficou
   intacto — mas o KDoc do enum precisa ser corrigido.
   > **Campo que o servidor compara não é rótulo, mesmo escrito em português.**

### ✂️ FORA DO ESCOPO (decidido, não esquecido)

**`PaywallScreen`, para a G.4.** A tabela comparativa guarda o valor de cada célula como `String?` e
faz `value == "Sim"` para escolher entre ✓, ✗ e texto. Aquele `"Sim"` **é sentinela, não texto de
tela**: mandá-lo ao catálogo quebraria a comparação em silêncio no dia em que alguém o traduzisse.
A saída é trocar o `String?` por um tipo fechado (`Sim` / `Nao` / `Texto(@StringRes)`); enquanto
isso, as 6 palavras (17 ocorrências de `"Sim"`) estão fixadas no `LiteraisAdiadosTest`.

> **Sentinela escrito em português é código ramificando em idioma, não texto de tela.**

**Terceira ocorrência do padrão nesta base.** As outras duas ficaram: o `"Não encontrado"` do
`AppError` virou sentinela **derivada** (`AppError.NotFound().message`, sem literal duplicado), e o
`SplitType.label` é dado do servidor.

**`ExerciseDetailScreen`, para a fatia H:** os dois regex que detectam aviso em português e o texto
de segurança. É lá que a descrição do exercício muda.

**`erroDoCampo`, para a G.4:** devolve a frase do servidor para um `fieldErrors` — mapa aberto, sem
código. Mesma classe do `SEM_TEXTO_PROPRIO`; a saída para os dois é o servidor mandar chave.

### ✅ `gen_strings.py` (2026-09-10)

`docs/geradores/gen_strings.py` → `docs/gerados/strings.md` → `FitJourney_STRINGS.pdf`, e já
registrado no `gen_tudo.py`. **605 chaves em 44 seções, 6 alertas.** É o documento que vai para
quem traduz, e o único momento em que alguém lê as 605 frases seguidas, fora da tela que as
justifica — que é exatamente como o tradutor vai encontrá-las.

Três coisas existem só nele, e nenhuma sai do `strings.xml`:

1. **ONDE cada frase aparece**, tirado do código e não do comentário. `conta/ContaScreen.kt:130` é
   verdade verificável; quando o comentário discorda dele, é o comentário que está velho.
2. **Conferência de parâmetros entre o XML e a chamada.** `%1$s` no catálogo com
   `stringResource(R.string.x, a, b)` na tela **não é pego pelo compilador nem pelo Lint** entre
   idiomas — estoura em runtime, no idioma de quem traduziu.
3. **Os 5 avisos cruzados em destaque**, escritos à mão porque não podem sair do código: que
   `Sair` do desafio e `Sair` da conta sejam palavras diferentes em inglês é verdade sobre o
   PRODUTO. O script confere a cada rodada que os pares ainda existem e ainda são idênticos.

⚠️ **A primeira rodada emitiu 324 alertas e três quartos eram defeito do medidor, não do
catálogo** — vale mais como aviso do que o script:

| Alerta | Era | Fim |
|---|---|---|
| Parâmetros do código × do catálogo | **51**, todos `declara N e passa N-1` | **0**. A contagem de vírgulas já excluía o id, e o script descontava de novo. **Viés constante não é ruído: é bug do medidor, e parece que está funcionando** |
| Rótulo curto sem limite | **243** de 605 | **removido**. 40% do catálogo não é dívida, é o formato de um app: botão, aba e título são curtos. Virou REGRA em "Como ler cada linha", que se lê uma vez, em vez de 243 linhas que se leem nunca |
| Sem comentário de contexto | **24** | **0**. Eram as primeiras chaves de cada seção, com só a faixa `==== Nome ====` acima. O teste aceita a faixa; **o tradutor lê a linha, não o arquivo** — os 24 comentários foram escritos |

Os **6 alertas restantes são informação, não defeito**: 3 frases longas (corpo de diálogo),
`app_name` (usada no manifest, que o script não varre) e 2 itens prefixados com `· %1$s`, que são
pedaço de frase montada pelo código.

> **"Errar para mais" tem um limite, e o número é o diagnóstico.** Alerta que acende em quatro de
> cada dez linhas ensina a pular a seção inteira — inclusive os 51 de verdade logo acima dela.

### O que ficou fora da G.3 (histórico)

- **`rationale` derivado no cliente** (débito registrado, ver abaixo).

---

## 🌐 G.4 CONCLUÍDA — o inglês (2026-09-11)

> As subseções abaixo estão em ordem cronológica, como aconteceram. O resumo do que a fatia
> entregou está em **Estado atual**, no topo; aqui ficam os PORQUÊS, que é o que não cabe numa
> tabela.

| entrega | |
|---|---|
| `values-en` com as 620 chaves | paridade travada por `CatalogoEmInglesTest` (10 testes) |
| Tela de idioma | gaveta + Conta + ajustes do sistema, um mecanismo |
| Barra de abas no catálogo | `BottomTab.label` era `String`; virou `@StringRes` |
| Paywall | sentinela `"Sim"` virou tipo fechado `Celula` |
| 9 frases com número do servidor | entraram no catálogo; `SEM_TEXTO_PROPRIO` vazio |
| `users.locale` | `PATCH /me` reconciliado na abertura |
| Grupos e Conquistas | pararam de esconder falha de rede |


**`values-en/strings.xml` está escrito: 593 `string` + 12 `plurals`, as 605 chaves, paridade
verde.** O portão do pt-BR passou, e as decisões que eram do Rafael (os quatro pares que têm de
divergir, a ordem da data, etc.) foram tomadas e aplicadas — `docs/revisao-ingles.md` (documento
de passagem) foi apagado em 2026-10-06 depois da decisão, como o próprio arquivo pedia.

### ⭐ O invariante que sustenta a fatia

> **Chave que falta no `values-en` NÃO quebra: cai no pt-BR, em silêncio.**

O Android resolve o que não achou subindo até a pasta padrão. O app roda, nada é logado, e quem
escolheu inglês lê português. É o pior modo de falha que existe — o que falha alto se conserta
hoje; o que falha calado fica até um usuário reclamar. Por isso a tradução **nasceu como arquivo
que o build confere**, e não como planilha para alguém transportar à mão depois.

`CatalogoEmInglesTest` (10 testes) tranca: conjunto de chaves nos dois sentidos, conjunto de
parâmetros posicionais por chave e por plural, `one`+`other` em todo plural, travessão e caixa
alta, frase vazia, os **4 pares cruzados obrigatoriamente divergentes**, a marca idêntica de
propósito, e as **36 chaves iguais nos dois idiomas** fixadas em lista.

O `CatalogoDeStrings` virou `open class Catalogo(caminho)` com dois objetos: `CatalogoDeStrings`
(pt-BR) e `CatalogoEmIngles`. Mesma leitura, perguntas diferentes.

### O que o inglês NÃO repete

Os comentários de contexto ficam só no pt-BR. **Duas cópias da mesma explicação são dois lugares
para atualizar e um para esquecer** — a falha que o #37 existe para impedir. No `values-en` só
sobraram ~10 comentários, todos registrando uma decisão específica do inglês que alguém
"consertaria" de volta para defeito: os pares cruzados, a inversão dia/mês, a capitalização.

### As três coisas que o inglês faz diferente, e não é engano

| | pt-BR | inglês |
|---|---|---|
| **ordem da data** | 13/09 | 09/13 — 3 chaves invertem a FRASE, não os índices |
| **caixa de dia e mês** | `seg`, `set` minúsculos | `Mon`, `Sep` — as famílias `data_dia_*` e `quiz_dia_*` ficam idênticas |
| **zero** | singular | plural, coberto pelo `other` |

### ⚠️ Achado: o teste de caixa alta tinha um buraco

`grupo_detalhe_participantes` (`PARTICIPANTES · %1$d`) e `amigos_secao_lista` (`MEUS AMIGOS · %1$d`)
atravessaram a faxina de 2026-09-10 **com o teste verde**: o invariante comparava
`v == v.uppercase()`, e `%1$d` tem um `d` minúsculo que vira `D` em caixa alta — a string nunca era
igual a si mesma. Os dois foram para caixa normal e o teste passou a **remover o placeholder antes
de comparar**. Duas telas a mais mudam de aparência, pelo mesmo motivo que as outras doze.

> **Invariante com buraco é pior que invariante ausente: o ausente você sabe que não tem.**

### ✅ Débitos fechados em 2026-09-11

Cinco linhas do `debitos.md`, e **duas delas já estavam pagas pelo código sem ninguém fechar a
linha** — o defeito espelhado do que eu fiz ontem ao subnotificar o que estava pronto.

| débito | o que era |
|---|---|
| ✅ `:konsist:test` ficava `UP-TO-DATE` sem varrer nada | O teste lê os `.kt` de todos os módulos pelo filesystem e o Gradle só conhecia `konsist/src`: dava para quebrar uma regra em qualquer módulo e a task passava sem abrir o arquivo. Os fontes viraram `inputs.files`, e a guarda de árvore vazia vive no `doFirst` (na configuração, ela fazia todo `.kt` virar entrada do build script e custava o cache de configuração de TODO build). **Provado:** `Reusing configuration cache` + `1 executed` + `varrendo 479 arquivos .kt` na mesma rodada. Custou 3 tentativas e 2 leituras erradas minhas — ver `debitos.md` |
| Reticências divergiam (`...` × `…`) | Três frases usavam `…`, uma usava três pontos. Atravessou o portão do pt-BR porque **ninguém enxerga a diferença lendo** — quem enxerga é o leitor de tela. Unificado e proibido nos dois catálogos |
| Preâmbulo velho do `erros.md` | Descrevia o cliente de ANTES da G.2 (*"o app mostra a frase do servidor literalmente"*), com o gerador rodando verde a cada `gen_tudo.py` |
| DOC: `videoRef`/`thumbRef` nullable | **Já não era verdade.** O `ExerciseDto` é non-null dos dois lados |
| DOC: "offline-first" × "offline leve" | **Já não era verdade.** O #30 foi ratificado e a escrita offline fechou em 2026-08-16 |

### ⚠️ E um que não era obsoleto: `enum.name` na tela

`ExerciseLibraryScreen` mostrava `cat.name` no chip de filtro e `ex.category.name` na linha da
lista — `FUNCTIONAL_HIT` e `LOWER_BACK`, **em inglês e em caixa alta, para o usuário brasileiro**,
numa das telas mais visitadas do app. Corrigido para `stringResource(cat.rotulo())`.

**A G.3 não tinha como pegar.** O inventário varreu LITERAIS, e `enum.name` nunca foi literal: a
varredura que declarou "zero literais em posição forte" estava certa e mesmo assim havia texto não
traduzido na tela. É a terceira ocorrência deste padrão nesta base (`MuscleGroup` e `Environment`
foram as outras duas, achadas na G.3).

> **Invariante que mede a forma errada do defeito não mede nada.**

Débito novo aberto para a guarda que falta: regex não serve aqui — ela não enxerga o TIPO do
receptor, e um teste por regex daria confiança sem base. A forma provável é uma regra de Konsist
sobre a AST. Varri o resto das telas: os outros 17 `.name` são dado legítimo.

### ✅ Tela de idioma (2026-09-11)

Alcançada por **duas portas** — item na gaveta, ao lado de Configurações da conta, e linha dentro
de Conta — e por uma terceira que não é nossa: **os ajustes do sistema**. `AppRoute.IdiomaDaInterface`.

#### [REGRA C1] `LocaleManager`, e não `AppCompatDelegate`

O caminho que a documentação do Android recomenda foi **recusado com motivo**. Ele exige
`AppCompatActivity` (a `MainActivity` é `ComponentActivity`) e, pior, um tema descendente de
`Theme.AppCompat` — este app usa `@android:style/Theme.Material.Light.NoActionBar`, um tema de
PLATAFORMA, e não tem `themes.xml`. Adotá-lo custaria dependência nova, troca da Activity raiz e um
tema escrito do zero, com risco visual em toda tela. Para dois idiomas, caro demais.

O `LocaleManager` é a API da própria plataforma (33+) e entrega o mesmo, **inclusive a porta nos
ajustes do sistema**, sem nada disso. `minSdk` é 24, então abaixo do 33 a preferência vive numa
`SharedPreferences` aplicada no `attachBaseContext` — mesmo estado, guardado onde o sistema ainda
não guardava, e ali a porta dos ajustes não existia de qualquer forma.

> **A recomendação da documentação vale para o app médio. O preço dela é do seu app.**

#### Duas portas não são dois mecanismos

Gaveta, Conta e ajustes do sistema escrevem no mesmo `IdiomaDoAparelho`. **Dois mecanismos gravando
a mesma preferência seriam duas fontes de verdade** — o defeito que a fatia G existe para remover.
A duplicação que custaria caro seria duas telas ou duas gravações, não duas entradas.

#### Conflito: o APARELHO vence (decisão de 2026-09-11)

Idioma é preferência POR APARELHO: dá para ter o app em inglês no celular do trabalho e em
português no pessoal. A escolha do aparelho manda na interface e é empurrada para `users.locale`.
Consequência aceita: trocar no celular A não troca no B, e o idioma do push segue o último aparelho
que abriu o app.

#### ⭐ Nome de idioma não se traduz

`idioma_pt_br` é *Português (Brasil)* e `idioma_en` é *English* nos DOIS catálogos, e as duas
entraram na lista fixada de chaves iguais (36 → 38). Quem precisa desta tela é quem abriu o app numa
língua que não lê, e reconhece *English*, não *Inglês*.

> **Traduzir a lista de idiomas torna ilegível a única tela que existe para sair de um idioma**
> **ilegível.**

Pelo mesmo motivo o item da gaveta leva **ícone de globo**: para quem não lê o idioma atual, a
palavra "Idioma" é tão opaca quanto o resto da gaveta. *Num app que a pessoa não consegue ler, o
ícone não é decoração: é o rótulo.* E a terceira linha, *Seguir o idioma do aparelho*, existe para
dar meia-volta — ajuste sem volta é ajuste que as pessoas têm medo de tocar.

⚠️ **O que o build verde NÃO prova.** Os testes de unidade confirmam compilação e paridade dos
catálogos. Nada exercita `LocaleManager`, `attachBaseContext` ou o `recreate()` — isso só se vê com
o app num aparelho, e nos dois lados do Android 13.

### ⚠️ A barra de abas estava em português, e o teste na mão foi quem achou

Primeiro uso do app em inglês: **tudo traduzido, menos a barra de baixo** — `Início`, `Treino`,
`Grupos`, `Progresso`. O componente mais visível do app, em português, depois de uma fatia que
declarou *zero literais em posição forte*.

A declaração estava **tecnicamente correta**. O `BottomTab` era um enum com `val label: String`, e
nenhuma das duas metades parecia texto de UI:

| onde | o que a varredura via |
|---|---|
| `INICIO("Início", ...)` | literal em argumento de construtor, longe de qualquer `Text(` |
| `Text(tab.label)` | chamada sem literal nenhum |

> **Texto guardado como propriedade de enum é invisível para quem procura texto em posição de UI:**
> **o literal não está na tela, e a tela não tem literal.**

**Quarta ocorrência da família** — antes `MuscleGroup`, `Environment` e `ExerciseCategory`. Fechada
com `@StringRes val rotulo: Int` (padrão do `ui/Rotulos.kt`) e com a guarda que faltava:

**`EnumsSemTextoTest`** — nenhum enum do módulo app pode ter propriedade `String`. A regra é grossa
de propósito. A fina ("String que chega a um `Text(`") exigiria o TIPO do receptor, que regex não
enxerga, e um teste fingindo esse alcance daria confiança sem base.

> **Invariante grosseiro e honesto vale mais que invariante fino e fingido.**

Falso duplicado novo no caminho: `nav_aba_treino` × `treino_detalhe_titulo_padrao`, ambos *Treino*
em pt-BR e **Training × Workout** em inglês. Avisos cruzados 5 → 6; duplicados fixados 19 → 22.
Catálogo em **615 chaves**.

### ✅ Paywall: o sentinela virou tipo (2026-09-11)

`value == "Sim"` **não existe mais**. O `String?` de cada célula virou:

```kotlin
private sealed interface Celula {
    data object Sim : Celula                          // ✓
    data object Nao : Celula                          // ✗  (era o `null`)
    data class Texto(@StringRes val id: Int) : Celula // isto sim é texto
}
```

O `when` do `Cell` ficou **exaustivo, sem `else`**: um quarto estado de célula quebra o build, em
vez de cair calado no ramo de texto — que era precisamente o que o `String?` permitia.

As 6 palavras adiadas foram para o catálogo (*1 programa, Vários, Só o Dia 1, Todos, Até 2*) e o
`LiteraisAdiadosTest` foi **apagado**: não sobrou literal para ele fixar. Catálogo em **620 chaves**.

**Terceira e última ocorrência do padrão nesta base.** As outras duas já estavam resolvidas: o
`"Não encontrado"` do `AppError` virou sentinela DERIVADA, e o `SplitType.label` ficou porque é
chave interna do motor, não texto.

> **A `String` é o tipo que aceita qualquer coisa, inclusive a coisa errada. Quando o código**
> **ramifica no valor dela, o tipo certo já existe e só não foi escrito.**

### ⚠️ Grupos e Conquistas escondiam a falha (2026-09-11)

Achado por teste na mão: a aba Grupos ficava em *"Carregando seus desafios"* **para sempre** e a de
Conquistas afirmava `0 de 0` numa tela em branco. Nenhum teste desta base teria pego, e a causa
estava mais fundo que a renderização.

**Grupos.** `Groups.sincronizar` devolvia `Unit`, com `is AppResult.Failure -> Unit  // mantém a
última lista conhecida`. A política de cache estava certa; jogar fora a INFORMAÇÃO de que falhou é
que não. O `GruposState` já declarava um `erroSync` que **nunca teve como ser preenchido**.

> **Campo de erro que ninguém preenche é pior que nenhum: quem lê o estado conclui que o erro**
> **está tratado.**

Agora a interface devolve `AppError?`, o ViewModel preenche e a tela ganhou um ramo — antes de
`vazio`, depois de `carregando` — com o `ErroDeTela`, o componente cujo próprio KDoc já dizia a
regra que faltava aplicar: *"use SÓ quando não há dado local"*.

**Conquistas.** O KDoc justificava não ter erro assim: *"a grade vem do cache local, então a rede
falhar não tem consequência visível"*. A conclusão vale **quando a premissa vale**, e a premissa é
*há cache local*. Na primeira execução não há — e a tela não ficava vazia, ela AFIRMAVA `0 de 0`,
em lima e negrito, dizendo que existem zero conquistas no catálogo.

> **Tela que não distingue "não tem" de "não chegou" não fica incompleta: ela mente com confiança.**

Ganhou `jaSincronizou()` e `erroSync`, e a tela passou a separar três coisas que eram uma: falhou,
ainda não chegou, e realmente não tem.

**Segunda e terceira ocorrências da família.** A primeira está no `MenuLateral.kt`, consertada em
2026-09-06: *"com o `/me` inalcançável e o cache vazio, carregando e falhou eram o mesmo pixel"*.

**Dois testes novos** no `GruposViewModelTest`, e o desenho deles importa:

- `sync que falha sem cache vira erro de tela` — o fake tem grupos NO SERVIDOR de propósito: é o que
  prova que a tela vazia não é "não tenho desafios", e sim "não consegui perguntar".
- `sync que falha COM cache nao esvazia a lista` — garante o INSUMO da decisão de silêncio (nível 1
  do #31), sem afirmar que a tela silenciou: quem decide é o ramo da tela, e um teste de ViewModel
  não alcança isso. Afirmar mais seria dar confiança sem base.

⚠️ **Lição de processo:** mudei duas interfaces e não procurei os implementadores. O pré-voo léxico
é cego para tipos — eu sabia, e ainda assim confiei nele. Antes de mudar assinatura: `grep` pelos
`override` em **main E test**.

### 🟡 Servidor em loopback: resolvido, CAUSA NÃO IDENTIFICADA (2026-09-11)

O `:server:run` subia em `Responding at http://127.0.0.1:8080` e nem o emulador nem o celular
alcançavam a API. O `application.yaml` declara `host: "$HOST:0.0.0.0"` — *"use a variável de
ambiente `HOST`, e na falta dela use `0.0.0.0`"* — e já trazia um comentário descrevendo exatamente
este sintoma, de uma passagem anterior por ele.

**Ficou funcionando. Não sabemos por quê.** Nenhuma hipótese foi confirmada: não se checou se havia
um `HOST` no ambiente do Windows, e o contorno (`--args="-host=0.0.0.0"`) não chegou a ser usado
como diagnóstico.

⚠️ **Isto fica registrado como pendência, não como fechado.** Correção sem causa identificada não é
correção: é o defeito fora de vista. Se voltar, a ordem de investigação é:

1. `echo $env:HOST` — se devolver algo, o yaml está obedecendo e o ambiente é que manda;
2. `./gradlew :server:run --args="-host=0.0.0.0"` — se isto resolve, confirma que o problema é a
   resolução do host, e não rota, porta ou firewall;
3. conferir se o IP da LAN mudou: o `API_BASE_URL` vem do `local.properties` e é cravado no build.
   Nesta ocasião ele mudou de `192.168.15.4` para `.6` no meio da investigação, o que por si só
   produz o mesmo sintoma e confunde o diagnóstico.

> **Defeito que some sem explicação não foi consertado: foi perdido de vista.**

O consolo é que o custo da reincidência caiu: as telas de Grupos e Conquistas agora **dizem** que
não falaram com o servidor, em vez de girar. Foi assim que este bug apareceu.

### ✅ As 9 frases com número do servidor entraram no catálogo (2026-09-11)

`TextosDeErro.SEM_TEXTO_PROPRIO` está **vazio**. Nenhuma frase de erro do app fica sem tradução.

**O `ErrorResponse` não mudou** — e essa é a parte que vale contar. A proposta em cima da mesa era
o envelope carregar `params: Map<String,String>` para o cliente montar a frase. O Rafael recusou por
verbosidade, e a verificação deu razão a ele por um motivo mais forte do que o alegado: **este
catálogo já cravava número de servidor em quatro strings**, todas comentadas como regra de produto
(`erro_dias_por_semana_invalido`, `erro_idade_invalida`, `erro_foco_alem_do_limite`,
`enum_bloqueio_lotado`). Mandar parâmetros no envelope teria criado **duas maneiras de fazer a mesma
coisa** no mesmo app.

> **Antes de inventar o mecanismo, olhe se a base já resolveu isso quatro vezes.**

Oito dos nove receberam o número dentro da frase, com o nome da constante do servidor no comentário
(`DisplayNamePolicy.MIN` = 2, `ProgramLimits.FREE_AI_LIMIT` = 1, `ModeracaoPolicy.PRAZO_EM_DIAS` = 7…).

O nono, `DIAS_LIVRES_INSUFICIENTES`, **não tem número para cravar**: a frase do servidor é *"precisa
de N dias livres, tem M"*, e os dois vêm das respostas da pessoa. Ele é backstop — o quiz já calcula
isso no cliente, com plural e com os dois números (`quiz_descanso_insuficiente`) — então virou frase
sem número: *"Você marcou dias de descanso demais para treinar nessa frequência."*

**O preço está registrado:** 12 frases do catálogo agora dependem de constantes do servidor que elas
não enxergam. Débito novo no `debitos.md`, com a saída conhecida (mover os números para um `Limites`
no `shared-contract`) e o motivo do adiamento.

---

## 🏅 G.5 CONCLUÍDA — conquistas e nome do programa (2026-09-15)

Migrations até **V48**. Fatia de servidor + contrato + cliente, na ordem.

| entrega | |
|---|---|
| `ConquistaIds` no `shared-contract` | 9 ids, padrão do `ErrorCodes` |
| 18 chaves novas de conquista | pt-BR e `values-en`, paridade verde |
| `programa_nome_derivado` | o nome do programa gerado passa a nascer no cliente |
| **V48** | zera o nome dos programas que a máquina já tinha nomeado |
| `AchievementDto` e `PublicAchievementDto` | perderam `title` e `description` |

### Como o defeito atravessou três invariantes verdes

A tela de Conquistas, o perfil público e a fileira de medalhas do perfil próprio estavam **inteiras
em português** para quem escolheu inglês. Nenhuma das três guardas da fatia G viu:

| guarda | por que não viu |
|---|---|
| inventário de literais (G.3) | varre literais **do cliente**, e a frase estava no servidor |
| `EnumsSemTextoTest` (G.4) | proíbe `String` em enum **do módulo app**; o enum era do `:server` |
| `TextoDoServidorTest` (G.4) | fixa os pontos que passam por `Frase.DoServidor`, e a tela fazia `Text(conquista.title)` direto |

> **Três invariantes verdes e o texto em português na tela: cada um media uma forma do defeito, e o
> defeito tinha uma quarta.**

É a **quinta** ocorrência do padrão "texto de UI onde a varredura não procura" (`ExerciseCategory`,
`MuscleGroup`, `Environment`, `BottomTab`, agora `AchievementPolicy`). As quatro anteriores foram
achadas por alguém olhando a tela; esta foi achada lendo os DTOs do contrato — que é o método que
entrou no "Próximo passo" acima.

### A decisão que a G.5 INVERTE, e o que ela custa

O KDoc do `AchievementDto` dizia, e dizia com razão para a época: *"título e descrição ficam no
servidor, então ajustar um texto ou um limiar não exige publicar uma versão nova do app"*.

Metade disso continua de pé, e é a metade que importa: **o LIMIAR não se mexeu**. Mudar `TREINOS_50`
de 50 para 40 segue sendo uma linha no `AchievementPolicy`. O que passou a exigir release é corrigir
uma PALAVRA.

> **Texto no servidor economiza um release e custa um idioma.**

### A corrente tem três elos, e cada teste guarda um

Sem os três, a falha é **silenciosa do pior jeito**: conquista sem texto **não vira cartão errado,
some da grade**. Não há fallback para cair, diferente do `TextosDeErro`.

| elo | quem guarda |
|---|---|
| enum do `:server` ↔ `ConquistaIds` | `AchievementPolicyTest` (teste novo) |
| `ConquistaIds` ↔ `TextosDeConquista` | `TextosDeConquistaTest` |
| `TextosDeConquista` ↔ `strings.xml` | `TextosDeConquistaTest`, chave derivada nos dois sentidos |

> **Medalha que o servidor concede e a tela não mostra é pior que medalha que não existe.**

### O nome do programa: `autoName` era pior que o `rationale`

`"Programa 4x — Push/Pull/Legs"` era montado no `ProgramService` e **GRAVADO**. Português,
travessão (proibido pelo #37 desde 2026-09-10), e persistido — nenhuma troca de idioma alcança dado
já escrito.

Agora o servidor grava `""`, que significa *"ninguém escolheu, derive"*, e o cliente monta o rótulo
de `daysPerWeek` + `split` no idioma da tela (`NomeDePrograma.kt`, usado nos 4 pontos que mostravam
o nome: Home, lista, detalhe e revelação).

**A coluna FICA**, diferente do que vai acontecer com o `rationale`: o usuário renomeia o programa
(ARCH #27), e o que ela guarda a partir daqui é escolha dele. Mesmo desenho do `display_name`.

⚠️ **O filtro da V48 é conservador de propósito.** Três camadas, todas obrigatórias: `origin = 'AI'`,
o formato exato `LIKE 'Programa %x — %'`, e o travessão — que é o que torna o predicado quase
infalsificável, porque ninguém o digita no teclado do celular e o projeto inteiro o proíbe.

> **Migration que apaga dado do usuário tem de errar para o lado de não apagar.**

### ❌ Notificações: NÃO foram movidas, e isso é decisão

Pedido original era "mover tudo para strings". As notificações ficaram, por um motivo que só
apareceu ao reler o contrato:

> *"`title` e `body` vêm já renderizados do servidor. Assim a notificação de ontem continua dizendo
> o que dizia ontem, mesmo que a pessoa citada troque de nome depois."*

Remontar a frase no cliente a partir do `data` faria a notificação passar a mostrar o nome **de
agora**. Ela deixaria de ser registro do que houve e viraria uma frase que se reescreve sozinha —
pior que ler o histórico no idioma antigo.

> **Registro do que aconteceu não se traduz retroativamente, porque traduzir é remontar, e remontar
> usa o dado de hoje.**

É a exceção única que o #37 já previa (o push só-dados não aparece com o app fechado, #36), agora
com uma segunda razão independente. Ratificada no `debitos.md`.

---

### ✅ `users.locale` sincronizado (2026-09-11) — G.4 fechada

`PATCH /me` com **só o `locale`**: o `displayName` fica `null`, que no `UpdateMeRequest` significa
"não mexer". Um PATCH que toca o que não foi pedido é um PUT com nome errado.

#### Onde a escrita mora, e por que não é na tela

A reconciliação vive no **`ReagirASessao`**, como quarta porta estreita ao lado de registrar o
aparelho e reler o badge. Não está na tela de idioma de propósito: **trocar o idioma destrói a
Activity** — no Android 13+ quem recria é o sistema, abaixo dele é o nosso `recreate()` — e um PATCH
disparado dali correria contra a própria recriação.

Reconciliar na abertura cobre os dois caminhos com UM ponto de escrita, e cobre também o que a tela
nunca veria: **trocar o idioma pelos ajustes do sistema com o app fechado**.

> **Quando a ação derruba quem a disparou, o lugar de gravar é no que nasce depois.**

#### Uma direção só

`ReconciliarIdiomaDaConta` empurra e nunca puxa — é a decisão "o aparelho vence", de 2026-09-11. O
contrário faria alguém que trocou pelos ajustes ver o app **desfazer a escolha sozinho** na abertura
seguinte, e o KDoc do `Idioma.PADRAO` já registrava o princípio: *idioma trocado por uma atualização
que ninguém pediu parece defeito, não recurso*.

Escreve **só quando diverge** (reescrever o mesmo valor a cada sessão é custo sem efeito), e ignora
a falha de propósito: sem rede o PATCH não sai, nada se perde, e a próxima abertura tenta de novo.
Diferente do `renomear`, cuja recusa a pessoa precisa ver.

⚠️ **Testado até onde teste de unidade alcança.** O `ReagirASessaoTest` ganhou a quarta porta e
prova que a reconciliação é chamada. **Nada exercita o PATCH de verdade, nem o `LocaleManager`, nem
o `recreate()`** — isso só num aparelho, e nos dois lados do Android 13.

### Ainda falta — lista única, revisada em 2026-09-11

⚠️ Esta lista foi reescrita do zero. A anterior tinha **itens já fechados ainda listados como
pendentes** e três cópias do mesmo item, porque cresceu por acréscimo durante a sessão. É o mesmo
defeito de livro-razão que esta sessão passou o dia caçando, cometido no documento que deveria
denunciá-lo.

> **Lista de pendências que ninguém reescreve vira lista de leitura opcional.**

| o quê | onde | tamanho |
|---|---|---|
| **12 frases cravam constantes do servidor** | `debitos.md`, aberto hoje | médio; saída conhecida (`Limites` no shared-contract) |
| **`erroDoCampo`** | `fieldErrors` é campo → FRASE em português | médio; precisa decidir se o valor vira código |
| **Varrer tela por tela com o app em inglês** | manual | contínuo, e o de maior retorno até agora |
| **Servidor em loopback, causa não identificada** | `debitos.md`, 🟡 | investigar só se voltar |

O `rationale` continua sendo o maior débito de arquitetura da área: frase em português montada pelo
`StructureEngine` e **gravada no banco**, exibida no `ProgramDetail` e no `ProgramReveal`. Carrega
também o **último travessão do produto**. Enumerado no `TextoDoServidorTest`; sai quando o servidor
mandar código mais parâmetros.


### ✅ G.5 verificada no aparelho (2026-09-15)

As três coisas que teste nenhum respondia:

| | resultado |
|---|---|
| Conquistas, Perfil e Perfil público **em inglês** | ✅ os 18 textos vêm do catálogo |
| `SELECT ... FROM programs` no banco de dev | ✅ os 2 `AI` zerados, os 3 `MANUAL` intactos, inclusive um chamado *"renomeado"* |
| `:konsist:test --rerun-tasks` com a regra de insets | ✅ 487 arquivos varridos |

⚠️ **Ressalva sobre a V48, e ela é o tipo de coisa que some se não for escrita:** os três sobreviventes são `MANUAL`, então o `origin = 'AI'` sozinho já bastaria nesta amostra. As camadas do FORMATO e do TRAVESSÃO **não foram exercitadas** — nenhum programa de IA tinha sido renomeado à mão.

> **Migration que acertou porque o caso difícil não existia no banco foi testada pela sorte da amostra.**

---

