# Design System do FitJourney

> **Fonte**: extraído do código em 2026-11-03 (`Color.kt`, `Theme.kt`, `navigation/`, `ui/`, as 39
> telas de `app/androidApp/src/main/kotlin/dev/rafael/app/screens/`), mais `docs/CONVENCOES.md` e
> `docs/regras/06-gamificacao.md`. Onde o código não decide nada, o campo está marcado
> `[INFERIDO]` e vale como lacuna, não como convenção.
>
> **Para que serve**: referência completa para redesenhar telas do app (inclusive no Claude Design)
> sem quebrar as decisões que já custaram caro. Por isso cada item diz se é **lei** ou **livre**:
> a seção 13 reúne isso tela por tela.
>
> **Hierarquia de autoridade** (a mesma do projeto): código > este documento > PDF gerado. Se
> divergirem, o código vence e este arquivo é que está errado.
>
> **Versão navegável**: existe um artifact do tipo Design System, "FitJourney Volt Athletic"
> (privado), com os mesmos tokens em forma de tokens de verdade, oito componentes com preview ao
> vivo e **oito seções de especificação por área** (onboarding, treino, progresso, grupos, social,
> aprender, conta, home e abertura), cada uma com telas, estados, controles, textos literais,
> achados de layout medidos e a margem de manobra daquela área. É cópia para consumo, feita para o
> Claude Design ler; **a fonte de verdade é este arquivo.** Se divergirem, este vence.

---

## Índice

1. [Como ler este documento: lei × livre](#1-como-ler-este-documento-lei--livre)
2. [Fundação: plataforma, tema, Material 3](#2-fundação-plataforma-tema-material-3)
3. [Cor](#3-cor)
4. [Tipografia](#4-tipografia)
5. [Espaçamento, raio e elevação](#5-espaçamento-raio-e-elevação)
6. [Movimento](#6-movimento)
7. [Estrutura de navegação e chrome](#7-estrutura-de-navegação-e-chrome)
8. [Componentes](#8-componentes)
9. [Ícones](#9-ícones)
10. [Estados: carregando, vazio, erro, offline](#10-estados-carregando-vazio-erro-offline)
11. [Fluxos do usuário](#11-fluxos-do-usuário)
12. [Restrições de texto que limitam o desenho](#12-restrições-de-texto-que-limitam-o-desenho)
13. [Margem para modificações no layout](#13-margem-para-modificações-no-layout)
14. [Lacunas e decisões abertas](#14-lacunas-e-decisões-abertas)

---

## 1. Como ler este documento: lei × livre

Três níveis, usados em todo o arquivo:

| marcador | significado | pode mudar no redesenho? |
|---|---|---|
| **[LEI]** | `[REGRA]`/`[INV]` do projeto, ou decisão com cicatriz registrada. Violar quebra teste, acessibilidade, tradução ou o significado da cor. | Não, sem decisão explícita do Rafael. |
| **[CONVENÇÃO]** | Padrão real e consistente no código, mas sem regra escrita por trás. | Sim, desde que a troca seja global (todas as telas juntas). |
| **[LIVRE]** | Layout, densidade, composição, hierarquia visual, ilustração, ordem dentro da tela. | Sim, tela por tela. |

A regra geral: **o significado é lei, a aparência é livre.** O lime querer dizer "recompensa" é lei;
o lime aparecer como barra, anel, selo ou número grande é livre. A ordem "exercício → números →
séries → registrar → descanso" na sessão é lei (é a ordem da ação); como esses cinco blocos são
desenhados é livre.

---

## 2. Fundação: plataforma, tema, Material 3

| item | valor |
|---|---|
| Stack de UI | Kotlin Multiplatform + Compose Multiplatform; Android é o target vivo, iOS declarado |
| Design system base | **Material 3** (`androidx.compose.material3`), tema customizado apenas no `colorScheme` |
| Tema | **Dark-first, e dark-only.** `FitJourneyTheme` instala só `darkColorScheme` |
| Tipografia | `MaterialTheme.typography` **default do M3**, sem customização |
| Shapes | Não há `Shapes` customizado no tema; os raios são literais por componente |
| Idiomas | `values` (pt-BR) e `values-en`, 785 strings |
| Módulo do DS | `shared/core/designsystem` (122 linhas: só `Color.kt` e `Theme.kt`) |
| Componentes compartilhados | `app/androidApp/.../ui/` (não estão no módulo de design system) |

**[LEI]** O app não tem tema claro. Não existe `lightColorScheme` em lugar nenhum, e nenhuma tela
tem variante clara. Qualquer proposta de design precisa funcionar em fundo escuro; propor um claro
é propor uma fatia de trabalho, não um ajuste visual.

**[INFERIDO]** O design system está fisicamente dividido: os tokens de cor estão num módulo KMP
compartilhado, mas os componentes (`shimmer`, `NetworkImage`, `ErroDeTela`, `AvatarInicial`,
`SeloDeSync`) estão no módulo Android do app. Isso não está documentado como decisão em lugar
nenhum: é o estado de fato. Consequência prática: nenhum componente de UI do app é reusável no
target iOS hoje.

---

## 3. Cor

### 3.1 A hierarquia de cor é o coração do sistema

**[LEI]** (ARCH #16, escrita no KDoc do `Color.kt`) Cada papel tem a sua família, e misturá-las
apaga o significado:

| família | papel | onde aparece |
|---|---|---|
| **lime** | **recompensa do perfil individual** | XP, nível, streak, conquista, sucesso |
| **azul** | **ação primária do usuário** | iniciar treino, salvar, tentar de novo, links |
| **roxo** | **geração por IA** ("premium inteligente") | gerar com IA, oferta do programa |
| **turquesa / laranja / violeta** | **dado** (medida, não ação nem prêmio) | séries dos gráficos |
| **neutro (Ink/Text)** | tudo o mais | superfícies, texto, bordas |
| **vermelho / amarelo** | estado de falha e aviso | erro, pendência que não sincronizou |

**[LEI]** O lime é **exclusivo** de recompensa individual. Está proibido em:
ação comum, navegação, grupo, avatar, gráfico, selo de sync. Três arquivos citam essa proibição
explicitamente (`Avatar.kt`, `SeloDeSync.kt`, `Color.kt`), o que indica que ela já foi violada e
custou correção.

### 3.2 Tokens

#### Recompensa
| token | hex | uso |
|---|---|---|
| `Volt` | `#CCFF00` | XP conquistado, streak aceso, nível, sucesso |
| `VoltDim` | `#7A8729` | XP **pendente** (aguardando validação do servidor, #17) |
| `VoltOn` | `#14180A` | texto sobre lime |

#### Ação
| token | hex | uso |
|---|---|---|
| `ActionBlue` | `#2E6BE6` | botão primário, link, destaque de seção |
| `ActionBlueDark` | `#1E4FB0` | container da ação |

#### IA
| token | hex | uso |
|---|---|---|
| `AiPurple` | `#8B5CF6` | gerar com IA |
| `AiPurpleDark` | `#6D28D9` | container de IA |

#### Superfícies (dark-first, 4 degraus)
| token | hex | uso |
|---|---|---|
| `Ink0` | `#0B0B0D` | fundo da tela (`background`) |
| `Ink1` | `#141518` | card (`surface`) |
| `Ink2` | `#1D1F24` | card elevado, input, shimmer (`surfaceVariant`) |
| `Ink3` | `#2A2D34` | borda e divisória (`outline`) |

#### Texto
| token | hex | uso |
|---|---|---|
| `TextHigh` | `#F5F6F7` | texto principal |
| `TextMid` | `#9BA1AC` | texto secundário (`onSurfaceVariant`) |
| `TextLow` | `#6B7280` | texto terciário, hoje praticamente não usado via tema |

#### Dados (gráficos, fatia J.2)
| token | hex | uso |
|---|---|---|
| `Chart1` | `#1FA8A0` | série 1, e cor única de gráfico de uma série só |
| `Chart2` | `#D95926` | série 2 |
| `Chart3` | `#9A66D0` | série 3 |
| `ChartGrid` | `#23262C` | grade e eixo, recessivos de propósito |

**[LEI]** Os três `Chart*` foram escolhidos **em conjunto** e validados contra `Ink1` para
daltonismo: o pior par tem ΔE 12,1 em deuteranopia e 23,7 em visão normal, todos com contraste
≥ 3:1. **Trocar um deles sozinho quebra a garantia.** É um conjunto, não três escolhas.

⚠️ `Chart3` (`#9A66D0`) é próximo de `AiPurple` (`#8B5CF6`). Convivem só porque nenhuma linha de
gráfico depende apenas da cor: cada série carrega o nome do exercício na ponta. **Se um redesenho
usar cor sem rótulo num gráfico, esse par precisa ser revisto antes.**

#### Estado
| token | hex | uso |
|---|---|---|
| `Danger` | `#E24B4A` | erro (`error`) |
| `Warning` | `#EF9F27` | aviso |
| `Success` | = `Volt` | sucesso é recompensa, então usa o lime |

### 3.3 Mapeamento para o `colorScheme` do Material 3

Este mapeamento é o que faz a hierarquia valer de graça em todo componente M3:

```
primary               = ActionBlue       onPrimary            = TextHigh
primaryContainer      = ActionBlueDark   onPrimaryContainer   = TextHigh
secondary             = AiPurple         onSecondary          = TextHigh
secondaryContainer    = Ink3             onSecondaryContainer = TextHigh   <- NEUTRO (2026-11-03)
tertiary              = Volt (LIME)      onTertiary           = VoltOn
tertiaryContainer     = VoltDim          onTertiaryContainer  = TextHigh
background            = Ink0             onBackground         = TextHigh
surface               = Ink1             onSurface            = TextHigh
surfaceVariant        = Ink2             onSurfaceVariant     = TextMid
surfaceContainerHighest = Ink2
outline / outlineVariant = Ink3
error                 = Danger           onError              = TextHigh
```

**[LEI]** `MaterialTheme.colorScheme.tertiary` **é o lime**. Logo: usar `tertiary` em qualquer
lugar que não seja recompensa individual viola a ARCH #16 mesmo que o código pareça genérico.
Para ação, `primary`.

### [LEI] (2026-11-03) `secondaryContainer` é neutro, e inverter isto já custou caro

O Material 3 usa esse slot como a **cor tonal genérica**: `FilledTonalIconButton`, `FilterChip`
selecionado e a trilha do `LinearProgressIndicator` vão todos buscar ali sem ninguém pedir.
Enquanto ele valia `AiPurpleDark`, o roxo de IA aparecia nos `-`/`+` de peso e reps, no chip da
série selecionada e na trilha de progresso da sessão, onde não há IA nenhuma, e **13 arquivos
herdavam dele**.

O defeito já tinha sido achado **uma vez** e remendado na tela errada: o `AchievementsScreen`
passou a declarar `trackColor` à mão porque *"o resultado lido de relance era o inverso do real:
11 de 30 parecia 90%"*. O slot continuou roxo, e o mesmo defeito seguiu vivo nas outras doze.

> **Cor que o Material pede emprestada para tudo tem de ser neutra. Quem quer significado pede a
> cor explicitamente.**

A regra que sai daí:

| o que usa o slot | precisa ser | valor |
|---|---|---|
| trilha de progresso | recessivo | `secondaryContainer` (neutro), herdado |
| chip selecionado com significado | legível como seleção | `primaryContainer`, declarado no componente |
| botão de ação já executada | lido como feito, não como desabilitado | `primaryContainer`, declarado no componente |

**Tonal genérico é neutro. Tonal com significado é o tom escuro da família da ação.**

Narrativa completa em `historico/correcao-do-tonal-generico.md`; regra em
`regras/15-design-e-cor.md`.

### Outras duas leis de cor (2026-11-04)

**[LEI] Estado não tem família de cor, e isso é decisão, não lacuna.** "Ligado", "ativo",
"correndo", "há algo aqui" se dizem com **neutro mais movimento ou forma**, nunca com um tom novo.
As famílias já têm papel, e os três `Chart*` foram validados juntos contra daltonismo: abrir um tom
para estado exigiria rever a paleta **em conjunto**. O preço está assumido: **"ativo" nunca salta
aos olhos**, e quando saltar for o requisito a resposta é hierarquia, tamanho ou posição.

**[LEI] Selo colorido é contorno, não preenchimento.** Um selo pequeno que carrega o significado de
uma família usa a cor no **traço**, com o rótulo em `TextHigh`. O motivo é medido: em texto de 10 a
11px sobre `Ink1`, `ActionBlue` dá 3,43:1 e `AiPurple` dá 4,38:1, e os dois reprovam o 4,5. No
contorno, onde bastam 3:1, os dois passam.

**[INFERIDO]** O esquema ainda é incompleto em relação ao M3: não define `surfaceContainer`,
`surfaceContainerLow/High`, `inverseSurface`, `scrim`, `errorContainer`, `onErrorContainer`,
`surfaceTint`. Componentes que caem nesses sete slots usam o default do `darkColorScheme`, que não
pertence à paleta Volt Athletic. É a lacuna de cor mais relevante do app hoje, e é **a mesma classe
de defeito** que o `secondaryContainer` acabou de custar.

### 3.4 Paleta do avatar (caso especial)

`AvatarInicial` pinta um círculo com a inicial do nome, e a cor sai do **hash do id** (determinística:
a mesma pessoa tem sempre a mesma cor em qualquer aparelho, sem guardar nada).

```
#2E6BE6 azul · #8B5CF6 roxo · #0E9F6E verde-mar
#DB5AA0 rosa · #0EA5B7 ciano · #7C6AF0 índigo
```

**[LEI]** Sem lime, sem vermelho e sem amarelo nessa lista: **cor de avatar não pode significar
estado.** Foto de perfil é v2 (exigiria upload, moderação e storage).

### 3.5 Uso de cor, medido no código

- Destaque de rótulo de seção: `primary` (ex.: "TREINO DE HOJE", "COMECE AQUI").
- Texto secundário: sempre `onSurfaceVariant`, nunca um cinza literal.
- Ícone de erro: `onSurfaceVariant` (não vermelho) no `ErroDeTela`; o vermelho fica no `ErroInline`.
- Ícone de atalho na Home: `primary`.
- Streak apagado: `onSurfaceVariant`; aceso: lime.
- XP pendente: `tertiaryContainer` (o lime esmaecido, `VoltDim`) para dizer "ainda não é seu".
- Card de descanso correndo: `primaryContainer`; parado: `surfaceVariant`.
- Ação já executada (botão "série feita" marcado, chip da série atual): `primaryContainer`, o tom
  escuro da **mesma** família. O que mudou foi o tempo verbal, não o assunto.
- Badge de play sobre miniatura de vídeo: `Color.Black.copy(alpha = 0.55f)` + ícone branco (cores
  literais, fora do tema, porque ficam sobre imagem arbitrária).

---

## 4. Tipografia

**A fonte está decidida; a escala, não.** Decidido em 2026-11-03, **ainda não no código: a fonte do
app é a Inter**, escolhida contra Archivo e Barlow por legibilidade nos 11px do `labelSmall` (56
usos), por figuras tabulares (o peso é o protagonista da sessão e muda enquanto a pessoa aperta o
`±`; sem largura fixa o número dança) e por não exigir reaprendizado, já que a métrica é próxima do
default atual. O que ela sacrifica é personalidade. No Android entra como TTF em `res/font` ou por
downloadable fonts; o Compose não lê woff2. **Peso, tracking e escala continuam lacuna**, e agora
são a decisão de maior alavanca que resta.

A marca é a palavra, em Inter 800 com tracking fechado, sempre em `TextHigh`. **Nunca em lime**:
lime na marca passaria a significar "FitJourney" além de "recompensa" e esvaziaria a ARCH #16 por
dentro, sem ninguém violar regra nenhuma.

**[INFERIDO]** O app ainda usa a **escala default do Material 3**, sem ajuste de peso,
tracking ou tamanho no tema. A identidade tipográfica do FitJourney hoje é a identidade do Roboto do
M3. É a maior oportunidade de diferenciação visual disponível, e a mais barata: um `Typography`
customizado no `FitJourneyTheme` muda o app inteiro sem tocar numa tela.

### 4.1 Escala, por frequência real de uso

| estilo | ocorrências | papel observado |
|---|---|---|
| `bodyMedium` | 61 | corpo de texto, descrição de card |
| `labelSmall` | 56 | rótulo de seção, selo, legenda, caixa alta de desenho |
| `bodySmall` | 53 | metadado, linha de detalhe sob o título |
| `titleMedium` | 29 | título de card, título de AppBar |
| `headlineSmall` | 23 | título de tela, nome do exercício, nome do treino |
| `titleLarge` | 17 | título de card de estado (concluído, descanso, sem programa) |
| `titleSmall` | 13 | nome no cabeçalho do drawer |
| `bodyLarge` | 13 | corpo em leitura longa (artigo, dúvidas) |
| `labelLarge` | 11 | cronômetro da sessão, texto de botão |
| `labelMedium` | 9 | rótulo de atalho, fração de passo do quiz |
| `headlineMedium` | 4 | saudação da Home, marca na Splash |
| `headlineLarge` | 1 | uso isolado |
| `displaySmall` | 1 | uso isolado |

### 4.2 Pesos e tamanhos cravados (fora da escala)

**[CONVENÇÃO]** `fontWeight = FontWeight.Bold` é aplicado manualmente em quase todo título. Não há
estilo "título" que já venha bold: o negrito é decisão repetida tela por tela.

**[CONVENÇÃO]** Três números são grandes de propósito, com `sp` literal, porque são o protagonista
da tela e precisam ser lidos de relance:

| onde | tamanho |
|---|---|
| peso e repetições na sessão de treino | `44.sp` Bold |
| contador do descanso | `34.sp` Bold |
| inicial do avatar | `tamanho * 0.4f` (proporcional ao círculo, não constante) |

### 4.3 Caixa alta

**[LEI]** (#37, fatia G.3) **Caixa alta é decisão de desenho e vive no código, nunca no
`strings.xml`.** O Compose não tem `text-transform`, e `.uppercase()` no catálogo quebraria em
alemão (substantivos capitalizados por gramática), turco (duas letras i) e escritas sem caixa.
Existe teste que recusa texto que já chega gritando no catálogo.

Logo: num redesenho, aplicar caixa alta é `.uppercase()` no composable (padrão já usado em
`PESO`/`REPS` na sessão, `DESCANSO`, `COMECE AQUI` na wiki). **Nunca** pedir a string em maiúsculas.

Custo já pago: 12 cabeçalhos perderam a caixa alta em 2026-09-10 por causa dessa regra.

---

## 5. Espaçamento, raio e elevação

**[INFERIDO]** Não existe arquivo de tokens de espaçamento. Todos os valores são literais `.dp`
espalhados pelas telas. O que segue é a escala **medida**, não declarada: é convenção emergente, e
é o melhor candidato a virar token de verdade num redesenho.

### 5.1 Escala de espaçamento (frequência)

| valor | ocorrências | papel dominante |
|---|---|---|
| `8.dp` | 146 | espaço entre elementos próximos, gap de chips |
| `16.dp` | 128 | padding interno de card, gap entre blocos |
| `12.dp` | 116 | gap entre cards, espaço após ícone |
| `10.dp` | 48 | gap médio |
| `6.dp` | 43 | gap mínimo (ícone e rótulo) |
| `20.dp` | 42 | padding de tela (Home), gap generoso |
| `4.dp` | 40 | gap atômico |
| `24.dp` | 37 | padding de card de estado, padding de tela (quiz, erro) |
| `32.dp` | 20 | padding de tela centrada (splash, oferta, reveal) |

**[CONVENÇÃO]** Padding de tela por tipo:

| tipo de tela | padding |
|---|---|
| Home (hub rolável) | `20.dp` |
| Quiz / onboarding | `24.dp` |
| Splash, Oferta, Revelação, erro de tela | `32.dp` (centrada) / `24.dp` (`ErroDeTela`) |
| Sessão de treino | `16.dp` horizontal, `12.dp` entre blocos |
| Card de estado (vazio, concluído, descanso) | `24.dp` interno |
| Card informativo | `16.dp` interno |

### 5.2 Raio

| valor | ocorrências | onde |
|---|---|---|
| `12.dp` | 21 | **o raio padrão do app** (blocos, placeholders, cards customizados) |
| `10.dp` | 6 | variação próxima |
| `4.dp` | 5 | linha de shimmer (texto esqueleto) |
| `8.dp` | 4 | pequeno |
| `16.dp` | 3 | painel de técnica (vídeo) |
| `14.dp` | 2 | miniatura do exercício na sessão |
| `20.dp` / `24.dp` | 2 cada | botão esqueleto, bloco grande |
| `CircleShape` | n/a | avatar, cronômetro, badge de play, ponto de "gravando" |

**[CONVENÇÃO]** `12.dp` é o raio padrão. `Card` do M3 usa o default do tema (não customizado), e a
diferença entre o raio do `Card` e o `12.dp` dos blocos manuais é visível: **unificar isso num
`Shapes` do tema é uma melhoria de baixo risco e alto ganho.**

### 5.3 Elevação

**[CONVENÇÃO]** O app praticamente não usa sombra. Separação é por **cor de superfície** (os quatro
degraus de `Ink`), que é o idioma correto em tema escuro. Duas exceções, as duas com
`tonalElevation = 3.dp`:

- barra inferior da sessão de treino (`Anterior`/`Próximo`/`Encerrar`)
- barra inferior do paywall (botão de assinar)

Ou seja: **elevação tonal marca "barra de ação fixa", nada mais.**

### 5.4 Dimensões notáveis

| elemento | medida |
|---|---|
| gaveta (drawer) | `288.dp` de largura |
| avatar | `42.dp` (drawer), `44.dp` (default, listas), `58.dp` (topo do perfil) |
| botão "série feita" | `fillMaxWidth` × `56.dp` de altura |
| miniatura do exercício na sessão | `84.dp` quadrada, raio `14.dp` |
| painel de técnica (vídeo) | `fillMaxWidth` × `300.dp`, raio `16.dp` |
| barra de progresso da sessão | `3.dp` de altura, colada sob a AppBar |
| ponto de "gravando" no cronômetro | `6.dp` circular, cor `primary` |
| ícone do `ErroDeTela` | `36.dp` |
| ícone de card de estado | `40.dp` a `44.dp` |
| badge de play sobre miniatura | `30.dp` circular, ícone `20.dp` |

---

## 6. Movimento

Todo o movimento entre telas está em `navigation/Movimento.kt`, e a decisão é tomada **lendo as
rotas**, não anotada destino a destino.

**[LEI]** *"Regra que se repete por destino não é regra: são 36 oportunidades de divergir."* A
classificação fica num lugar só. Rota nova herda o movimento certo automaticamente.

### 6.1 Os três gestos

**[LEI]** A direção do movimento é uma **promessa sobre o botão voltar**:

| gesto | o que a pessoa fez | movimento |
|---|---|---|
| **empilhar** | abriu um detalhe, e existe um "voltar" | entra da direita; ao voltar, sai para a direita |
| **trocar de aba** | pulou de seção, sem "de onde" | esmaece com escala (fade through), sem direção |
| **entrar numa tarefa** | execução, check-in, paywall | sobe de baixo, como painel |
| **sem pilha** | Splash, Preparando, Login | sem animação |

Por que aba não desliza: a navegação entre as 4 raízes usa `popUpTo`, então a pilha não guarda de
onde você veio. Um deslize horizontal acertaria metade das vezes, e "tocar num ícone à direita e ver
a tela entrar pela esquerda" é o tipo de detalhe que ninguém sabe nomear e todo mundo sente.

Por que tarefa sobe: essas telas **escondem a barra de abas**. *"Tela que esconde a navegação
deveria chegar por um caminho diferente dela."*

### 6.2 Números

| constante | valor | razão |
|---|---|---|
| entrada (`RAPIDO`) | `280ms` | longo o bastante para ser lido como movimento, curto para não virar espera |
| saída (`MAIS_RAPIDO`) | `200ms` | **quem sai não precisa ser acompanhado** |
| `EMPURRA` | `cubic-bezier(0.05, 0.7, 0.1, 1)` | "emphasized decelerate": começa rápido, freia no fim |
| `SOME` | `cubic-bezier(0.3, 0, 0.8, 0.15)` | saída do fade de aba |
| `RECUO` | largura / `4` | a tela que sai anda 1/4, não a tela inteira: ela está **esperando o voltar**, não foi descartada |
| escala do fade de aba | `0.92` → `1.0` | sutil de propósito: evita que o fade pareça piscada, sem virar zoom |

**[LEI]** A assimetria entrada/saída é intencional: **a tela que chega merece mais tempo que a que
sai.**

### 6.3 Animações dentro das telas

| onde | spec | razão |
|---|---|---|
| shimmer | gradiente varrendo, `1200ms` linear, `Restart` | placeholder padrão de carregamento |
| XP e barra de XP (Home) | `tween 900ms` | o salto 0 → 250 ao sincronizar vira recompensa, não troca seca de número |
| streak (Home) | `tween 600ms` | idem, mais curto |
| painel de técnica, abrir | altura em `spring(DampingRatioLowBouncy, StiffnessMediumLow)` + `fadeIn(180ms, delay 60ms)` | você vê o espaço abrir e **depois** o vídeo ocupá-lo |
| painel de técnica, fechar | `fadeOut(90ms)` + `shrinkVertically(spring(StiffnessMedium))` | conteúdo sai primeiro; **entrar pode chamar atenção, sair não deve** |
| badge de play | `rotationZ` 0° → 90° | o mesmo selo vira a seta que fecha |
| passo do preparo (Splash) | `AnimatedContent` | troca de frase sem salto |
| descanso | `animateFloatAsState` na barra | contagem visível |

### 6.4 Feedback físico

**[CONVENÇÃO]** Vibração de `400ms` ao **fim do descanso**, e só ali. A razão está escrita: o
usuário não fica olhando a tela entre séries. É o único háptico do app.

---

## 7. Estrutura de navegação e chrome

### 7.1 As quatro abas

**[LEI]** Quatro abas, não cinco. Perfil saiu (ARCH #34) e virou item do menu lateral.

| aba | ícone | rota raiz |
|---|---|---|
| Início | `Icons.Filled.Home` | `Home` |
| Treino | `Icons.Outlined.FitnessCenter` | `Programs` |
| Grupos | `Icons.Outlined.Group` | `Grupos` |
| Progresso | `Icons.Outlined.BarChart` | `Progresso` |

**[LEI]** Abas = tarefa frequente. Gaveta = destino secundário. Os dois conjuntos são **disjuntos**:
nenhum destino aparece nos dois. É isso que permite bottom bar e drawer conviverem, contra a
recomendação genérica do Material.

**[LEI]** Trocar de aba usa `trocarDeAba()` (`popUpTo` + `saveState` + `restoreState`), nunca
`navigate` cru. Atalho da Home que leve a uma raiz de aba **precisa** passar por lá: com `navigate`
cru a pilha fica `[Home, Programs]` em vez de `[Programs]`, e a bottom bar trava sem responder a
toque até alguém apertar voltar.

**[LEI]** A barra de abas e a barra do topo **só aparecem nas 4 raízes**. Detalhe, execução, quiz,
onboarding e paywall ocupam a tela inteira.

### 7.2 Barra do topo (só nas raízes)

- `navigationIcon`: hamburger (`Icons.Filled.Menu`) que abre a gaveta
- `title`: **vazio**. As raízes não têm título na AppBar
- `actions`: ícone de notificações (`Icons.Outlined.Notifications`) com `Badge` numérico de não
  lidas

**[LEI]** O ícone de notificações fica na barra que já existe nas quatro raízes: quem está em Grupos
vê o badge sem voltar para a Home. É o **ponto único de aviso do app**.

### 7.3 Gaveta (menu lateral)

Largura `288.dp`, rolável. Estrutura:

```
CABEÇALHO (não é item): avatar + nome + "Nível N" em lime  → leva ao Perfil
──────────────────────────────────────────────────────────
Meu perfil        Person
Exercícios        FitnessCenter
Conquistas        EmojiEvents
Histórico         History
Aprender          MenuBook
Dúvidas           HelpOutline
──────────────────────────────────────────────────────────
Conta             Settings
Idioma            Language   ← globo, e isso é funcional, não enfeite
──────────────────────────────────────────────────────────
Sair              Logout     ← e ele SAI (abre diálogo de confirmação)
```

**[LEI]** *"Item de menu NAVEGA."* Interruptor (tema, notificação) não entra na gaveta: quebra a
expectativa do componente e não entra no back stack. Vai em Conta.

**[LEI]** O ícone de globo no item de idioma é o rótulo, não decoração: *"num app que a pessoa não
consegue ler, o ícone não é decoração: é o rótulo."*

**[LEI]** "Sair" sai. A primeira versão navegava para Configurações, e *"um item chamado 'Sair' que
leva a outra tela mente sobre o que faz."*

**[LEI]** `gesturesEnabled` acompanha `mostrarAbas`: em execução de treino ou formulário, arrasto
lateral que abre menu é acidente.

#### Estados do cabeçalho da gaveta (três, e confundi-los já custou quatro defeitos)

| estado | o que mostra |
|---|---|
| sem sessão | `Spacer(44.dp)`, nada. Não há dado e não vai haver: esqueleto seria carregamento infinito |
| nada conhecido ainda | **esqueleto shimmer** (círculo 44dp + duas linhas). Não afirma nada, que é a verdade naquele instante |
| com dado | avatar + nome (ou e-mail local como fallback) + nível em lime |

**[LEI]** A versão antiga mostrava `"?"` e `"Você"`, que *parecem dado*: por um segundo o app
afirmava que o usuário se chama "Você". **Esqueleto não afirma nada.**

### 7.4 Insets: a regra mais frágil do app

**[LEI]** **Quem desenha a barra do topo pinta a status bar.**

O arranjo: o `Scaffold` do `AppNavHost` usa `contentWindowInsets = WindowInsets(0)` (não reserva
inset para si), entrega como `padding` só a altura das barras que ele realmente desenhou, e o
`NavHost` faz `.padding(padding).consumeWindowInsets(padding)`.

| | `mostrarAbas = false` (detalhes) | `mostrarAbas = true` (as 4 raízes) |
|---|---|---|
| barras do host | nenhuma | TopAppBar + NavigationBar |
| `padding` entregue | zero | altura das duas, inset já embutido |
| o que a tela enxerga | os insets **inteiros** | zero |
| quem pinta a status bar | o `Scaffold` **da tela** | o `Scaffold` do host |

**[LEI]** **Toda tela fora das 4 raízes precisa do próprio `Scaffold`**, mesmo sem barra nenhuma,
só para aplicar o inset. Splash, Oferta, Revelação e Quiz têm `Scaffold` vazio exatamente por isso.

**[LEI]** Nenhuma tela chama `statusBarsPadding`, `navigationBarsPadding` ou `systemBarsPadding`.
Travado no `ArchitectureKonsistTest`. `imePadding` continua sendo de quem tem campo de texto.

⚠️ *"Defeito de inset só é visível onde algum pixel encosta na borda. Nas outras telas ele está lá,
esperando o dia em que alguém mover um elemento para o topo."* O defeito existia em 7 telas e só
aparecia na barra de progresso do quiz. **Num redesenho que encoste conteúdo no topo, verificar isso
antes de qualquer coisa.**

### 7.5 Mapa de rotas (36)

```
Splash · Preparando · Login · Nome · Quiz
Home · Library · ExerciseDetail(id)
Programs · ProgramDetail(id) · ProgramGenerate · ProgramOffer · ProgramReveal
WorkoutDetail(id, editLocked) · WorkoutCreate(programId, takenDays) · WorkoutEdit(id) · WorkoutSession(id)
Grupos · GrupoNovo · GrupoDetalhe(id, aba) · GrupoEntrar(inviteToken?) · CheckIn(groupId)
  Comentarios(groupId, checkInId) · Moderacao(groupId)
Progresso · ExercicioDetalhe(programId, exercicioId) · Historico · Conquistas(destaque?)
Perfil(userId?) · Amigos · Bloqueados · Conta · EditarPerfil · IdiomaDaInterface
Notificacoes · Wiki · WikiArticle(slug) · Duvidas · Paywall(voltarParaHome)
```

**[LEI]** `Perfil(userId?)` é **uma rota e duas telas**: `null` é o meu perfil (cache-first, com
números que só eu vejo); um id é o perfil público de outra pessoa (online, `PublicProfileDto`). A
rota é uma só porque o destino, para quem navega, é o mesmo: "abrir o perfil de alguém".

---

## 8. Componentes

### 8.1 Compartilhados (`ui/`)

| componente | o que é | notas de desenho |
|---|---|---|
| `Modifier.shimmer(shape)` | gradiente animado varrendo o componente | **placeholder padrão de carregamento**, texto e imagem. Base `surfaceVariant`, highlight `surface`, 1200ms |
| `ShimmerLine(width, height=16dp)` | linha de texto esqueleto | raio `4.dp` |
| `ShimmerList(rows=6)` | esqueleto de lista | por linha: 200dp/16dp + 120dp/12dp, gap 6dp, padding vertical 10dp |
| `ShimmerContent()` | esqueleto de detalhe/form | bloco 120dp (raio 12) + linhas 220/160/260dp, gap 12dp, padding 16dp |
| `NetworkImage(url, ...)` | imagem remota padrão (Coil) | shimmer enquanto carrega, `surfaceVariant` em erro ou ref vazio. **Nunca usar `AsyncImage` cru** |
| `AvatarInicial(nome, id, tamanho=44dp)` | inicial branca sobre cor derivada do id | `FontWeight.Medium`, fonte = 0.4 × tamanho |
| `SeloDeSync(pendencia, onDescartar?)` | `AssistChip` de pendência do outbox | dois estados, pesos diferentes (abaixo) |
| `DescartarPendenciaDialog` | convite para descartar falha permanente | mostra a mensagem que o **servidor** devolveu |
| `ErroDeTela` / `ErroInline` / `ErroEmSnackbar` | os três níveis de erro | seção 10 |
| `DialogoDeSaida` / `DialogoDeDenuncia` | confirmações | `AlertDialog` do M3 |
| `Frase` | tipo selado: texto do catálogo **ou** do servidor | seção 12 |
| `Rotulos.kt` | rótulo de enum → `@StringRes Int` | seção 12 |
| `NomeDePrograma` / `RationaleDePrograma` | nome e justificativa derivados no cliente | o servidor manda dados, não prosa |

#### `SeloDeSync`: dois estados com pesos diferentes, de propósito

| estado | aparência | clicável? |
|---|---|---|
| **aguardando** | `AssistChip` discreto, `labelSmall`, desabilitado | **não**: é o caso normal do offline-first, some sozinho quando a rede volta. Dar ação sugeriria que há algo a fazer |
| **falha permanente** | `AssistChip` com label em `error` | **sim**: abre o diálogo de descarte. Sem ação aqui a falha travava para sempre |

**[LEI]** Nada de lime no selo de sync.

### 8.2 Componentes M3 em uso, e para quê

| componente | papel no app |
|---|---|
| `Card` | **a unidade de conteúdo do app**. 11 variações só na Home |
| `Scaffold` | 19 telas. Obrigatório fora das raízes (insets) |
| `TopAppBar` | 14 telas de detalhe, com `ArrowBack` |
| `Button` | ação primária, geralmente `fillMaxWidth` |
| `OutlinedButton` | ação secundária, e a ação do `ErroDeTela` |
| `TextButton` | ação terciária, "pular", "encerrar", botões de diálogo |
| `FilledTonalIconButton` | `-`/`+` de peso e reps na sessão |
| `FilledIconButton` | play/pause do descanso |
| `OutlinedTextField` | **o único campo de texto do app** (9 usos) |
| `AlertDialog` | 9 confirmações |
| `FilterChip` | seleção: séries (S1 S2 S3), filtro de programa no Progresso, regras do check-in |
| `AssistChip` | informativo e acionável: selo de sync, "ver técnica" |
| `LinearProgressIndicator` | XP, passo do quiz, progresso da sessão (3dp), descanso |
| `CircularProgressIndicator` | 11 usos: splash, geração, submissão |
| `TabRow` + `Tab` | **só no detalhe do grupo** (4 abas) |
| `NavigationBar` / `NavigationBarItem` | as 4 abas |
| `ModalNavigationDrawer` / `ModalDrawerSheet` / `NavigationDrawerItem` | a gaveta |
| `BadgedBox` + `Badge` | contador de não lidas, fila de moderação |
| `HorizontalDivider` / `VerticalDivider` | separação na gaveta e entre peso/reps |
| `Surface(tonalElevation = 3.dp)` | barra de ação fixa (sessão, paywall) |
| `SnackbarHost` | **feedback de ação**, padrão do app |
| `DatePicker` / `DatePickerDialog` | período do desafio |
| `ExtendedFloatingActionButton` / `FloatingActionButton` | 1 uso cada |
| `ListItem` | lista de treinos no detalhe do programa |
| `DropdownMenu` | menu de card no feed do grupo |
| `pulltorefresh` | 6 telas |

**[LEI]** (convenção de UI, registrada em memória do projeto) **Feedback de ação rápida e mensagem
curta são sempre snackbar.** Erro, sucesso e aviso ficam padronizados nesse componente.

### 8.3 Componentes compostos notáveis, por tela

A lista completa de composables privados por tela está no código; os que carregam decisão de desenho:

**Home**: `Saudacao`, `FaixaDeProgresso`, `CardTreinoDeHoje`, `CardTreinoConcluido`,
`CardDiaDeDescanso`, `CardAtivarPrograma`, `CardCompletarAgenda`, `CardNaoSincronizado`,
`CardSemPrograma`, `CardErro`, `CardEsqueleto`, `Atalho`.

**Sessão**: `CabecalhoDaSessao`, `CronometroDaSessao`, `CartaoDoExercicio`, `PainelDeTecnica`,
`CartaoDeNumeros`, `FileiraDeSeries`, `BotaoSerieFeita`, `CartaoDeDescanso`, `BarraDeNavegacao`.

**Grupo (detalhe)**: `AbaSobre`, `ConviteDoDesafio`, `AbaDoRanking`, `LinhaDoRanking`, `AbaDePosts`,
`ItemDoFeed`, `SeloDeStatus`, `MenuDoCard`, `BarraSocial`, `AbaDeMembros`, `Membro`, 5 diálogos.

**Progresso**: `FiltroDeProgramas`, `FaixaDeSemanas`, `CartaoDeTotais`, `CartaoDeComparacao`,
`CartaoTrancado`, `CartaoSemCarga`, `BlocosDaAnalise`, `SeletorDeJanela`, `BlocoDeCargaSemanal`,
`BlocoDeEvolucao`, `ListaDeExercicios`, `CartaoDeGrafico`, `CalhaDoEixo`.

**Gráficos** (`GraficosDeProgresso.kt`): `GraficoDeBarras`, `GraficoDeLinhas`, `BarrasHorizontais`.
Três tipos, desenhados à mão, sem biblioteca de chart.

**Paywall**: `ComparisonHeader`, `GroupTitle`, `FeatureRow`, `Cell`. Tabela comparativa Free ×
Premium em 3 grupos (IA, Manuais, Geral), com células `Sim` (✓), `Nao` (✗) ou `Texto` qualificado
("1 programa", "Até 2", "Só o Dia 1").

---

## 9. Ícones

**[CONVENÇÃO]** Material Icons, **variante `Outlined` como padrão**; `Filled` reservado para
estado ativo ou gesto de alta frequência. `AutoMirrored` para tudo que tem direção (RTL).

### 9.1 Navegação e chrome

| ícone | uso | contagem |
|---|---|---|
| `AutoMirrored.Filled.ArrowBack` | voltar, em toda tela de detalhe | **23** |
| `Filled.Menu` | abrir gaveta | 1 |
| `Filled.Home` | aba Início (**único `Filled` nas abas**, por ser a raiz) | 1 |
| `Outlined.FitnessCenter` | aba Treino, item Exercícios | 2 |
| `Outlined.Group` / `Outlined.People` | aba Grupos, membros | 3 / 1 |
| `Outlined.BarChart` | aba Progresso, atalho da Home | 2 |
| `Outlined.Notifications` / `NotificationsNone` | central de notificações | 1 / 1 |
| `AutoMirrored.Filled.KeyboardArrowRight` / `Left` | avançar/voltar na sessão, "abrir" em listas | 3 / 1 |
| `Filled.KeyboardArrowDown` | expandir bloco | 1 |

### 9.2 Gaveta

`Outlined.Person` (perfil) · `Outlined.EmojiEvents` (conquistas) · `Outlined.History` (histórico) ·
`AutoMirrored.Outlined.MenuBook` (Aprender) · `AutoMirrored.Outlined.HelpOutline` (dúvidas) ·
`Outlined.Settings` (conta) · `Outlined.Language` (idioma) · `AutoMirrored.Outlined.Logout` (sair)

### 9.3 Estado e erro (mapeamento fixo, seção 10)

| ícone | erro |
|---|---|
| `Outlined.WifiOff` | sem conexão (culpa do aparelho) |
| `Outlined.SyncProblem` | servidor mudo (tem rede, servidor não responde) |
| `Outlined.Lock` | sessão expirada, credenciais, sem permissão, recurso premium |
| `Outlined.SearchOff` | não encontrado (404) |
| `Outlined.ErrorOutline` | conflito, dados inválidos, falha nossa (500) |

**[LEI]** Esse mapeamento é política de apresentação de erro, não escolha estética de tela. Vive em
`ErrorUi.kt` e vale para o app inteiro.

### 9.4 Ação e conteúdo

`Filled.Add` / `Default.Add` (criar, +1) · `Default.Remove` (−1) · `Default.Check` /
`Filled.Check` (feito) · `Default.Close` / `Filled.Close` (fechar, sair da tarefa) ·
`Default.Edit` / `Filled.Edit` · `Outlined.Delete` / `Default.Delete` · `Default.Search` ·
`Default.PlayArrow` / `Default.Pause` / `Outlined.PlayCircleOutline` · `Outlined.Refresh` ·
`Outlined.MoreVert` · `Outlined.ContentCopy` (copiar código de convite) ·
`AutoMirrored.Filled.Send` (enviar comentário) · `Outlined.AddReaction` (reagir)

### 9.5 Domínio (os que carregam significado do produto)

| ícone | significado |
|---|---|
| `Outlined.LocalFireDepartment` | **streak**. Lime quando aceso, `onSurfaceVariant` quando apagado |
| `Outlined.EmojiEvents` | conquista |
| `Default.Star` | medalha recém-desbloqueada (diálogo), tint lime |
| `Outlined.CheckCircle` | treino do dia concluído, tint lime, 44dp |
| `Outlined.Bedtime` | dia de descanso, `onSurfaceVariant`, 40dp |
| `Outlined.AutoAwesome` | **geração por IA**, tint `secondary` (roxo) |
| `Outlined.Shield` | moderação / status de check-in |
| `Outlined.Flag` | denunciar |
| `Outlined.AddAPhoto` / `Filled.Cameraswitch` | check-in com foto |
| `Outlined.LocationOn` / `Outlined.MyLocation` | check-in com local (aproximado × exato) |
| `Outlined.PersonAdd` | pedido de amizade |
| `Outlined.CloudQueue` | sincronização |
| `Outlined.Construction` | "em breve" |
| `Outlined.Info` | informação |

**[INFERIDO]** Não há **nenhum ícone próprio** no app: tudo é Material Icons. Também não há asset de
logo: a marca na Splash é o nome do app tipografado (`headlineMedium` Bold), com um slot
`MarcaDoApp()` já preparado para receber um `Image(painterResource(R.drawable.logo))` sem mexer em
mais nada. **Isso é um buraco de identidade visual, e é o item mais barato de resolver.**

---

## 10. Estados: carregando, vazio, erro, offline

### 10.1 A política de erro tem três níveis (ARCH #30)

**[LEI]** `ErrorUi.kt` é o **ponto único onde `AppError` vira pixel**. Nenhum ViewModel decide
isso sozinho. Antes de existir, o resultado era: banner vermelho de rede numa tela que tinha dado
local, "sem conexão" quando o servidor estava morto, e 401 sem caminho de volta para o login.

| nível | quando | componente |
|---|---|---|
| **1. silencioso** | sync falhou **e há dado local** | nenhum. É a ausência de componente |
| **2. estado de tela** | falhou e **não há nada local** | `ErroDeTela`: ícone 36dp + título + texto + 1 botão de saída |
| **3. erro de ação** | o usuário tocou algo e falhou | `ErroEmSnackbar` (efêmero, com retry) ou `ErroInline` (uma linha em `error`, ancorada ao que falhou) |

**[LEI]** Nível 1 é regra, não descuido: exibir erro por cima de uma lista que funciona é assustar o
usuário à toa.

**[LEI]** `ErroInline` mostra o **texto**, não o título: *"o título é a CATEGORIA, o texto é o que
aconteceu"*. Ao lado do botão que falhou, a categoria não acrescenta nada; quem tocou já sabe que
falhou.

**[LEI]** Validação **não** vira snackbar nem estado de tela: vai embaixo do campo
(`erroDoCampo`). Dizer o mesmo erro duas vezes, uma delas longe de onde aconteceu, foi defeito real.

**[LEI]** `erroGeral(camposVisiveis)` existe porque formulário que só usa `erroDoCampo` **engole** a
recusa cujo campo ele não desenha. Aconteceu no form de grupo: o servidor recusou por `timezone`,
que ali é texto e não campo, e o usuário ficou com um botão que não fazia nada. *"O pior tipo de
erro é o que não aparece."*

### 10.2 As cinco ações oferecidas

`TENTAR_DE_NOVO` · `IR_PRO_LOGIN` · `VER_PLANOS` · `VOLTAR` · `NENHUMA`

**[LEI]** `ErrorUi` **não navega**: devolve a ação recomendada e a tela executa. A rota de
login/paywall é assunto do NavHost.

**[LEI]** `VOLTAR` não vira botão de snackbar: a pessoa já está onde queria estar, e "Voltar" ao lado
de "Você atingiu o limite de 4 programas" parecia uma ação que não faz nada.

### 10.3 Os eixos que mudam a mensagem

Três eixos **ortogonais**, e cada um existe porque a sua ausência fez o app mentir:

| eixo | valores | o que decide |
|---|---|---|
| `ErroContexto` | `LOGADO` / `AUTENTICANDO` | 401 logado = "sessão expirada, entre de novo". 401 **na tela de login** = "e-mail ou senha incorretos". Aplicar a primeira na segunda deslogaria quem só errou a senha |
| `ContextoDeCache` | `TEM_CACHE` / `SEM_CACHE` | só promete "o que já foi baixado continua funcionando" **quando é verdade**. Fila de moderação, comentários e denúncia são online-only |
| `temRede` | lido do sistema, rechecado a cada erro | "sem conexão" (o usuário resolve) × "servidor mudo" (não resolve) |

### 10.4 Carregando

**[LEI]** `shimmer` é o placeholder padrão. *"Use em qualquer estado de loading em vez de deixar
vazio/preto."*

**[LEI]** **Carregar e falhar não podem ser o mesmo pixel.** O cabeçalho da gaveta girou para sempre
porque "carregando" e "falhou" compartilhavam estado; o esqueleto só aparece quando nem o dado do
servidor nem o fallback local existem.

**[LEI]** **Carregamento sem teto não é carregamento: é uma tela sem saída.** O preparo pós-login
tem teto de 20s para o conjunto; estourando, a Home abre incompleta.

**[CONVENÇÃO]** Onde cada forma é usada:

| forma | onde |
|---|---|
| `ShimmerContent` | sessão de treino, detalhe, formulário |
| `ShimmerList` | listas |
| `CardEsqueleto` dedicado | Home (imita a forma do card real: linha 110/12, 200/26, 160/14, botão 44dp raio 20) |
| shimmer circular + 2 linhas | cabeçalho da gaveta |
| `CircularProgressIndicator` | Splash, geração de programa, submissão de formulário, dentro de botão (20dp) |
| `LinearProgressIndicator` | progresso determinado (passo do quiz, séries feitas, descanso) |

**[CONVENÇÃO]** Altura reservada antes do texto aparecer: na Splash o passo do preparo vive num
`Box(heightIn(min = 24.dp))`, porque *"um salto no primeiro segundo do app é a pior hora para ele"*.

### 10.5 Vazio × não sincronizado

**[LEI]** A distinção mais importante de estado vazio no app: **"não baixei ainda" não é "você não
tem".**

| condição | card |
|---|---|
| sem programa, sync falhou, **nunca sincronizou neste aparelho** | `CardNaoSincronizado` (usa `ErroDeTela` dentro de um Card) |
| sem programa, de verdade | `CardSemPrograma` com **duas** saídas (IA e manual) |
| sincronizou ontem, abre offline hoje com zero programas | **não** mostra "sem conexão": seria mentira |

**[LEI]** `CardSemPrograma` tem duas saídas porque *"quem recusou a oferta no onboarding não pode
cair num card que só oferece a mesma coisa que acabou de recusar"*.

### 10.6 Offline (transversal)

**[LEI]** Leitura é **cache-first, sempre** (#30): a tela lê do banco local, a rede é
sincronizador. **Erro de rede não vira erro de tela quando há dado local.**

**[LEI]** Escrita é **um caminho só**: grava local → enfileira no outbox → agenda o envio, com ou
sem rede.

**[LEI]** **Escrita otimista exige selo na UI**: sem ele o usuário não distingue "está no servidor"
de "está só neste aparelho".

**[LEI]** XP de treino feito offline só entra depois que a sessão sobe (autoridade do servidor). A
Home avisa com `tertiaryContainer` (lime esmaecido) para a pessoa não achar que o treino não contou.

**[CONVENÇÃO]** Não há listener de conectividade (aceito em 2026-10-08): a tela aberta não reage à
rede voltando, só ao entrar, ao puxar (`pulltorefresh`) ou ao "Tentar de novo". **Num redesenho, não
prometer reatividade de rede que o app não tem.**

---

## 11. Fluxos do usuário

Cada fluxo abaixo traz: **gatilho → telas → estados → saídas**, com as decisões de desenho que o
sustentam. É a seção para ler antes de redesenhar qualquer tela.

---

### F1. Abertura do app

```
                    ┌─ nunca logou aqui ──────────────→ Login
Splash ─ decide ────┼─ logado + onboarding concluído ──→ Home
                    └─ logado + onboarding pendente ──→ Nome
```

**Splash** (sem animação de saída, de propósito: *"o Splash decide para onde ir antes de qualquer
pixel importar"*). Conteúdo: marca tipografada + `CircularProgressIndicator` + linha de passo
(altura reservada). `Scaffold` vazio só para o inset.

A decisão de rota, em ordem:

1. **Gate pela sessão persistida**, não por token fresco: quem já logou entra mesmo offline. Só vai
   para o Login quem nunca logou neste aparelho.
2. **Cache-first com assimetria deliberada**: `onboardingCompleted` é **monotônico** (uma vez
   concluído, nunca volta atrás), então um `true` cacheado é confiável e dispensa a rede, e o
   usuário recorrente abre o app instantaneamente. `false` ou `null` **não** podem ser confiados:
   *"pular o onboarding por engano é muito pior que esperar 2 segundos"*.
3. Timeout de **5s** para o perfil (o primeiro request após o boot do servidor é lento por JIT/pool,
   e 1,5s estourava e caía em cache velho → Home errada para cadastro novo).
4. Aquecimento de fundo (catálogo, histórico) **só depois** da decisão de rota, no `appScope`: antes,
   o `GET /exercises` competia com o `getProfile` no mesmo HttpClient.

**[LEI]** `Preparando` é **a mesma tela, outro momento**. Duas rotas para um composable:

| | cold start (`Splash`) | pós-login (`Preparando`) |
|---|---|---|
| banco local | já tem dado | **vazio** |
| comportamento | decide e sincroniza **de fundo** | decide e **espera** o essencial |
| razão | *"esperar dado que já está no aparelho é atraso"* | *"esperar dado que não existe é carregamento"* |

Passos visíveis do preparo, nesta ordem (o catálogo por último, por ser o mais demorado): **Perfil →
Programas → Progresso → Exercícios**. Teto de **20s** no conjunto.

**[LEI]** Sem o `Preparando`, a Home aparecia e **se montava na frente da pessoa**: esqueleto, XP
zerado, "sem programa".

#### Login
Tela própria, sem barra. Erro usa `ErroContexto.AUTENTICANDO` (credenciais, ícone `Lock`, ação
`NENHUMA`: a pessoa já está onde precisa estar). Saída: `Preparando`, com `popUpTo(Login,
inclusive)`.

**[LEI]** A permissão de notificação **não** é pedida no Login. É pedida no `AppNavHost`, atrelada a
"existe usuário logado agora". Pedir na tela de login disparava o diálogo **antes da pessoa digitar
a senha**, no momento de maior taxa de recusa, e no Android recusar é quase definitivo. Pior: quem
abrisse com sessão restaurada pularia o Login e nunca veria o pedido.

#### Sessão expirada (transversal)
401 que sobreviveu à renovação do token, de qualquer request (inclusive do `SyncWorker` em
background) → `signOut()` + `navigate(Login) { popUpTo(0) }`. Sem isso o usuário ficava preso em
"Sessão expirada" com um "Tentar de novo" que só repetia o 401.

**[LEI]** Logout e sessão expirada usam `popUpTo(0)` e **não animam**: *"não existe relação espacial
entre a tela de onde se saiu e a de login"*.

---

### F2. Onboarding

```
Nome → Quiz (até 12 passos) → [Home como raiz] + ProgramOffer (por cima)
                                    │
                        ┌───────────┴───────────┐
                     Gerar                    Pular
                        │                        └──→ Home (nada criado no servidor)
                 ProgramReveal
                        │
            ┌───────────┴────────────┐
      Concluir                 Ver Premium
            │                        │
          Home                   Paywall(voltarParaHome = true)
                                     │
                            ┌────────┴────────┐
                        Assinou           Agora não
                            │                 │
                       Home (cache          Home
                       invalidado)
```

#### Nome
**[LEI]** Primeiro passo, **antes** do quiz e não dentro dele: o quiz escreve em `profiles`, o nome
mora em `users` (via `PATCH /me`). Um passo dentro do quiz faria `profile` depender de `auth`, e
*"feature nunca depende de feature"*. Sem volta (`popUpTo(Nome, inclusive)`).

#### Quiz
`Scaffold` sem barra (só inset) no onboarding; **com** barra de fechar no modo edição, porque dali
dá para sair no meio.

Layout: barra de progresso no topo → fração "3/10" (`labelMedium`) → **miolo com peso 1** (a
pergunta) → `ErroInline` se houver → linha de navegação (`Voltar` outlined + `Continuar` filled com
peso 1).

Passos (12 definidos, **nem todos visíveis**: `state.visibleSteps` esconde FOCO para iniciante e
para quem declarou saúde geral, #26/#24):

| passo | pergunta | controle |
|---|---|---|
| `GOAL` | objetivo | seleção única |
| `LEVEL` | nível | seleção única |
| `AGE` | idade + menor supervisionado | campo + switch |
| `DAYS` | dias por semana | seleção única |
| `REST_DAYS` | dias indisponíveis | chips múltiplos |
| `FOCUS` | foco muscular | chips múltiplos (condicional) |
| `ENVIRONMENT` | academia / casa | seleção única |
| `ACCESSORIES` | acessórios disponíveis | chips múltiplos |
| `SPLIT` | divisão de treino | seleção única, dependente de `daysPerWeek` |
| `HEALTH` | condições + reconhecimento de risco | switches |
| `LIMITATIONS` | limitações corporais | chips múltiplos |
| `BODY` | peso e altura | dois campos |

**[CONVENÇÃO]** Títulos de passo usam `headlineSmall` (os 8 primeiros) ou `titleLarge` (ambiente,
acessórios, saúde, limitações). **Inconsistência real**, boa candidata a unificar.

**[LEI]** O quiz e o Nome **não** usam a animação de "entrar numa tarefa" apesar de esconderem as
abas: *"eles são uma sequência de passos, e passo que sobe de baixo a cada pergunta vira um
elevador."*

**[LEI]** O botão final muda de rótulo por contexto: `Continuar` / `Concluir` (onboarding) /
`Salvar` (edição). Habilitado só com `canAdvance`.

Saída: `navigate(Home) { popUpTo(Quiz, inclusive) }` **e depois** `navigate(ProgramOffer)` por cima.
Logo: qualquer saída da oferta desemboca na Home, que já é a raiz.

#### ProgramOffer
Tela centrada (padding 32dp): ícone `AutoAwesome` 48dp em **roxo** (`secondary`, porque é IA) →
título `headlineSmall` Bold → texto `bodyMedium` em `onSurfaceVariant` → `Button` "gerar"
fillMaxWidth → `TextButton` "pular" fillMaxWidth.

**[LEI]** Esta tela existe porque antes o `ProgramRevealViewModel` chamava `generate()` no `init`:
**o programa era criado no servidor no instante em que a tela abria, sem ninguém pedir.** Quem não
queria ficava com um plano pendurado na conta.

**[LEI]** A oferta fica **depois** do quiz, não antes: o quiz é sobre o **perfil** (ambiente, idade,
limitações), que a Home e a biblioteca usam. *"Programa é consequência opcional do perfil; perfil não
é opcional."*

#### ProgramReveal
Três estados num `Box`:

| estado | conteúdo |
|---|---|
| gerando | `CircularProgressIndicator` + "montando" (`titleMedium`) + texto de apoio, centrado |
| erro | título + `ErroInline` + `Button` "tentar de novo", centrado |
| pronto | `RevealContent`: título trancado/liberado, nome derivado, resumo, rationale derivado, lista de treinos, ação |

**[LEI]** O nome do programa e o rationale são **derivados no cliente**, no idioma da tela, a partir
de `split` + `daysPerWeek` + `focusMuscles`. O servidor manda o programa com nome em branco, porque
*"o `StructureEngine` não sabe em que idioma a tela está"*.

`ON_RESUME` recarrega: ao voltar do paywall o programa já aparece desbloqueado.

Saída da oferta ao gerar: `popUpTo(ProgramOffer, inclusive)`. **[LEI]** *"o Reveal não deve poder
voltar para a oferta: o programa já foi criado, e reperguntar 'quer um programa?' não faz sentido."*

#### Paywall
Barra do topo com título "Premium" e `Close`. Corpo: tabela comparativa (header Free × Premium, 3
grupos, 13 linhas). Barra inferior `Surface(tonalElevation = 3.dp)` com `ErroInline` + botão de
assinar fillMaxWidth.

**[LEI]** `Paywall(voltarParaHome)` distingue os dois contextos: vindo do Reveal, recusar leva à
**Home**, porque voltar para o Reveal (que é a própria tela de oferta) *"dá a sensação de gaiola: o
usuário recusou e caiu de volta na oferta"*. Dentro do app, recusar volta de onde veio.

**[CONVENÇÃO]** `invalidate()` do cache de programas **só se assinou**: antes invalidava em todo
fechamento, e até o "Agora não" custava um refetch.

---

### F3. O laço diário: Home → sessão de treino

A Home é o hub e tem **nove estados mutuamente exclusivos**, decididos nesta ordem de precedência:

```
1. isLoading                                            → CardEsqueleto
2. error != null                                        → CardErro (ErroDeTela em Card)
3. semPrograma && erroSync != null && !jaSincronizou    → CardNaoSincronizado
4. semPrograma                                          → CardSemPrograma (IA | manual)
5. treinouHoje                                          → CardTreinoConcluido
6. today != null                                        → CardTreinoDeHoje (ou "desbloquear" se locked)
7. precisaAtivarPrograma                                → CardAtivarPrograma
8. precisaCompletarAgenda                               → CardCompletarAgenda
9. else                                                 → CardDiaDeDescanso
```

**[LEI]** A ordem **é** a lógica: 3 antes de 4 ("não baixei" antes de "não tem"), 5 antes de 6
(*"evita refazer o mesmo treino em loop"*), 7 e 8 antes de 9 (*"sem agenda no ativo não é descanso, é
ninguém ter montado a semana ainda"*, achado do Rafael navegando no app, quando a Home dizia "Dia de
descanso" sem a semana existir).

Estrutura fixa da tela (padding 20dp, scroll vertical):

```
Saudação (bom dia / boa tarde / boa noite) + data compacta
   ↓ 16dp
FaixaDeProgresso  ← streak + nível + barra de XP, sempre visível, vem do cache local
   ↓ 12dp
[um dos nove cards]
   ↓ 24dp
"ATALHOS" (labelSmall, onSurfaceVariant)
   ↓ 10dp
[Criar treino]  [Exercícios]  [Grupos]   ← três cards com peso 1, ícone primary + labelMedium
```

**[LEI]** A `FaixaDeProgresso` aparece **sempre**, inclusive offline e antes do primeiro sync da
vida (zerada, *"o que é verdade: o usuário ainda não tem XP"*).

Composição da faixa: ícone de fogo 22dp (lime se streak > 0, senão `onSurfaceVariant`) + número do
streak (`titleLarge` Bold, lime se aceso) + plural "dias seguidos" à esquerda; "Nível N"
(`titleMedium` Bold) + "x / y XP" (`bodySmall`) à direita; `LinearProgressIndicator` lime com track
`surfaceVariant`; e, se houver treino pendente de sync, uma linha `labelSmall` em
`tertiaryContainer`.

**[LEI]** A saudação é **só** saudação. O "Sair" saiu dela (ARCH #34) e virou o rodapé da gaveta.

**[LEI]** `LifecycleEventEffect(ON_RESUME)` recarrega a Home: o treino pode ter sido executado ou
alterado em outra tela.

#### Sessão de treino (`WorkoutSession`)

Chega **subindo de baixo** (é tarefa). Esconde a barra de abas e a gaveta. Redesenhada em 2026-10-01
para **um exercício por vez**.

**[LEI]** A ordem vertical **é a ordem da ação**:

```
┌─ TopAppBar: nome do treino · 3/8 · cronômetro (ponto verde) · X ──┐
│  LinearProgressIndicator 3dp  ← progresso por SÉRIE feita          │
├────────────────────────────────────────────────────────────────────┤
│  1. O QUE você vai fazer                                           │
│     miniatura 84dp (raio 14) + nome (headlineSmall Bold)           │
│     + "Série 2 de 4" + AssistChip "ver técnica"                    │
│  ╰─ PainelDeTecnica: vídeo 300dp, abre/fecha DENTRO da sessão      │
│                                                                     │
│  2. COM QUANTO                                                      │
│     PESO [44sp Bold]  │  REPS [44sp Bold]    + FilledTonal −/+     │
│                                                                     │
│  3. ONDE você está                                                  │
│     [S1✓] [S2✓] [S3] [S4]   ← LazyRow de FilterChip                │
│                                                                     │
│  4. REGISTRAR                                                       │
│     [ SÉRIE FEITA ]  ← 56dp, fillMaxWidth, ÚNICO botão de destaque │
│                                                                     │
│  5. O QUE VEM DEPOIS                                                │
│     CartaoDeDescanso: 3 estados                                     │
├────────────────────────────────────────────────────────────────────┤
│  Surface(tonalElevation 3dp):  ‹ Anterior   Próximo ›    Encerrar  │
└────────────────────────────────────────────────────────────────────┘
```

**[LEI]** *"O gesto que grava o dado fica ao lado do dado, não do que acontece depois dele."*

**[LEI]** A versão anterior era a planilha inteira numa `LazyColumn`. Era boa para **conferir** e
péssima para **executar**: *"na academia, entre uma série e outra, com o celular na mão suada, a
pessoa precisa de um número grande e um botão grande, não de uma tabela."*

**[LEI]** A técnica abre **dentro** da sessão, não em tela nova: navegar tirava a pessoa da execução,
o cronômetro saía de vista e o descanso corria fora do campo de visão. *"Sair da tela para consultar
uma coisa interrompe a coisa que se estava fazendo. Expandir, não."*

**[LEI]** O player de vídeo (`ExoPlayer`) só existe **enquanto o painel está aberto**: mantê-lo vivo
o treino inteiro é pagar bateria por um vídeo que ninguém está olhando.

**[LEI]** Sem vídeo, **não há chip e não há toque na miniatura**: *"oferecer 'ver técnica' e abrir um
painel vazio é prometer o que não se entrega."*

**[LEI]** O painel de técnica mostra **só o vídeo**, sem a descrição do catálogo (o texto foi cortado
pelo Rafael na bateria manual): *"consulta no meio de uma tarefa tem que caber num olhar. O que
exige leitura já é outra tarefa."*

**[LEI]** "Encerrar" é **discreto de propósito** (`TextButton` no canto): é a ação que menos se quer
no meio do treino, e estava com a cor mais chamativa da tela no desenho original. O destaque é do
"Série feita".

**[LEI]** `FileiraDeSeries` é `LazyRow` e não `Row`: o motor prescreve de 2 a 5 séries e pode
prescrever mais. Fileira fixa quebraria calada no dia em que isso acontecesse.

Os três estados do descanso, no mesmo lugar da tela:

| estado | rótulo | número | botão | fundo |
|---|---|---|---|---|
| ocioso | "DESCANSO · APÓS A SÉRIE" | tempo **prescrito** | ▶ inicia | `surfaceVariant` |
| correndo | "DESCANSO" | tempo restante | ⏸ pausa (+ "mais 15s") | `primaryContainer` |
| pausado | "DESCANSO" | tempo restante | ▶ retoma | `primaryContainer` |

**[LEI]** O rótulo "após a série" é o que impede o card de **mentir** quando está parado: ele não
finge contagem, anuncia o que vem.

**[LEI]** Sair no meio **só pergunta quando há o que perder** (alguma série marcada e não salva).
Vale para o X e para o botão do sistema. *"Confirmação que aparece quando não há o que perder treina
o usuário a ignorá-la."* No diálogo, **ficar é a opção segura e tem o verbo do que se está fazendo**
("Continuar treino"), não um "Cancelar" que não diz o que cancela.

**[CONVENÇÃO]** O que a tela **não** tem, de propósito: campo de anotação. Não existe coluna, DTO
nem rota para isso. *"Botão que não faz nada é pior que botão ausente."*

**[LACUNA ATIVA]** Não há campo de **RIR** (reps in reserve). O motor usa `session_set_logs.rir` na
Regra 4 (proteção intrassessão, −4%/ponto) e na calibração, e o cliente **nunca escreve esse
campo**, então as duas nunca disparam em produção. É a próxima fatia planejada
(`docs/trabalho/rir-plano.md`). **Quem redesenhar a sessão deve reservar lugar para o RIR ao lado de
peso e reps.**

---

### F4. Programas e treinos

```
aba Treino → ProgramListScreen
                ├─ "+" → CreateProgramDialog (manual)
                ├─ gerar com IA → ProgramGenerate → ProgramDetail(novo)
                ├─ trancado → Paywall()
                └─ abrir → ProgramDetail
                              ├─ WeekdayPicker / RenameProgramDialog / ativar
                              ├─ AvisosDoPrograma (card que some sozinho quando vazio)
                              ├─ "+" treino → WorkoutCreate(programId, takenDays)
                              └─ treino → WorkoutDetail
                                            ├─ editar → WorkoutEdit  (ou Paywall se editLocked)
                                            └─ iniciar → WorkoutSession
```

**[LEI]** (ARCH #27) A hierarquia é **programa → treinos aninhados**, restaurada em 2026-09-26
(revertendo o "deck achatado por treino"). `onOpenProgram` é a ação primária da lista.

**[LEI]** (ARCH #23) **Gate de plano**: programa de origem IA para usuário free vem com os dias
depois do Dia 1 **borrados** (`locked`). O servidor decide (`ProgramBlur`), o cliente só reflete. A
Home **não fura o blur**: dia trancado mostra `OutlinedButton` com cadeado "desbloquear" em vez do
`Button` "iniciar treino".

**[LEI]** `editLocked` barra na hora, com paywall, **sem entrar na tela de edição**: a UI antecipa o
gate para não dar erro na cara do usuário, mas quem decide é o servidor (`requireEditable`).

**[CONVENÇÃO]** `invalidate()` do cache de programas só quando **algo mudou de fato** (trocou ou
removeu exercício, excluiu treino, assinou). Antes invalidava em toda volta, e só entrar e sair de um
treino gerava um `GET /programs`.

**[CONVENÇÃO]** `WorkoutCreate` recebe `takenDays` (CSV dos dias já ocupados) e **desabilita** esses
dias no form, em vez de deixar o usuário escolher e tomar erro.

**[CONVENÇÃO]** `ProgramGenerate` tem dois diálogos de gate: `PremiumDialog` e `HealthGateDialog`.

**[CONVENÇÃO]** A lista mostra `SeloDeSync` quando há pendência (rename/create do **programa**); o
detalhe mostra quando a pendência é do **treino** (ativação). Mesmo componente, dois níveis.

---

### F5. Progresso, histórico e conquistas

```
aba Progresso → ProgressScreen
                   ├─ trancado → CartaoTrancado → Paywall()
                   ├─ sem carga → CartaoSemCarga (explicação, NÃO paywall)
                   └─ com dado → filtro + faixa de semanas + totais + comparação + 3 gráficos
                                    └─ "ver detalhado" → ExercicioDetalhe(programId, exercicioId)

gaveta → Historico     (consulta: lista de sessões)
gaveta → Conquistas    (grade do catálogo inteiro, bloqueadas em cinza)
```

**[LEI]** Três estados, e confundi-los é o defeito caro:

| estado | quando | o que aparece |
|---|---|---|
| trancado | `analysisLocked` **do servidor** | cartão de assinatura |
| sem carga | `sinceDate` nulo | explicação, **não** paywall |
| com dado | o resto | os três gráficos |

**[LEI]** ⚠️ **Nulo não decide nada aqui.** Os blocos pagos vêm nulos nos dois primeiros casos, e
usar o nulo como critério mostraria paywall a quem treina só peso corporal, *"cobrando por algo que
ela não conseguiria usar nem pagando"*. Quem responde "é pago" é o servidor.

**[LEI]** O desmembramento de 2026-10-01: a tela acumulava métricas, a porta das conquistas e o
histórico inteiro. *"Três coisas numa tela não é uma tela de três coisas: é uma tela sem assunto."*
Conquistas e Histórico saíram para a gaveta, porque os dois são **consulta**.

**[LEI]** O filtro de programas **some quando não há o que escolher**: com um programa só e nenhuma
sessão avulsa, "Todos" e o programa mostram o mesmo gráfico, e *"um filtro que não filtra é ruído que
ensina a pessoa a ignorar a fileira"*. O critério é **duas opções de verdade**, não "existe
programa".

**[LEI]** Id de programa que o cache local ainda não conhece é **pulado**, não vira chip genérico:
*"dois chips escritos 'Programa' são piores que um chip a menos."*

**[CONVENÇÃO]** Detalhe de layout que vale copiar: a fileira de chips tem padding **só no `start`**.
O começo alinha com o título; o fim não ganha margem de propósito, porque *"chip encostando na borda
direita é o que diz 'tem mais, role'. Fechar dos dois lados faria a fileira parecer completa mesmo
quando não está."*

**[LEI]** (ARCH #32) **Recompensa concedida nunca é retirada.** XP e nível podem ser 100% derivados
porque são números; conquista não pode, porque mudar um limiar faria a medalha sumir do perfil de
quem já a viu. Consequência de UI: **em conquista desbloqueada o progresso atual pode estar ABAIXO do
alvo** (o streak quebra, a medalha fica). A UI lê a **data de desbloqueio**, nunca compara progresso
com alvo.

**[LEI]** Conquistas mostra o **catálogo inteiro**, bloqueadas incluídas e em cinza: *"uma tela só
com o que já foi ganho não diz o que fazer a seguir, e o valor da gamificação está justamente no
próximo marco visível."*

**[CONVENÇÃO]** `Conquistas(destaque)` abre um `AlertDialog` de celebração quando chega por push de
"conquista desbloqueada" (ícone `Star` em lime). O diálogo usa a versão **sem** barra de progresso:
*"é celebração de algo já feito, não status de algo em andamento."*

**[CONVENÇÃO]** Os gráficos são três tipos desenhados à mão (`GraficoDeBarras`, `GraficoDeLinhas`,
`BarrasHorizontais`), sem biblioteca. Usam `Chart1/2/3` + `ChartGrid`, e **cada série carrega o nome
na ponta** (ver a restrição de `Chart3` × `AiPurple`, seção 3.2).

---

### F6. Grupos (desafios)

```
aba Grupos → GruposScreen  [CartaoDeGrupo | Esqueleto | Vazio]
                ├─ criar → GrupoFormScreen → (popBackStack ao criar)
                ├─ entrar por código → EntrarScreen(inviteToken?)
                └─ abrir → GrupoDetalheScreen(id, abaInicial)
                              ├─ aba SOBRE    → check-in, convite, especificações, sair
                              ├─ aba RANKING  → LinhaDoRanking → Perfil(userId)
                              ├─ aba POSTS    → ItemDoFeed → Comentarios | denúncia | reações
                              ├─ aba MEMBROS  → Membro → Perfil(userId)
                              ├─ fazer check-in → CheckIn(groupId)   [sobe de baixo: é tarefa]
                              └─ [só admin] badge → Moderacao(groupId)
```

**[LEI]** Cabeçalho fixo + **quatro abas**. Era um scroll único com tudo empilhado; com o ranking
entrando, o único jeito de ver o terceiro conteúdo era rolar por cima dos outros dois.

**[LEI]** A separação em abas **resolve um débito de graça**: cada aba ganha a própria `LazyColumn`, e
o feed deixa de compor todos os itens carregados para sempre, que era o motivo de a paginação ter
virado botão em vez de rolagem infinita.

**[LEI]** O cabeçalho é fixo e **enxuto**: colapsá-lo com `nestedScroll` seria o ideal, mas um
cabeçalho alto e fixo comeria metade da tela. O meio-termo: essencial sempre visível (banner, selo,
ação) e as especificações (período, fuso, regras, código) num bloco que **abre quando alguém quer**,
fechado por padrão, porque é consulta rara.

**[LEI]** A ordem das abas **lê como uma pergunta de cada vez**: *"o que é isto → quem está ganhando
→ o que aconteceu → quem está aqui."* Mas "Sobre" não é a de abertura no caso comum: **com o desafio
`ATIVO`, a tela abre em Ranking**.

**[LEI]** A aba Sobre é a de abertura nos outros casos **e por isso reúne as ações**: *"uma aba de
abertura que só informa poria a consulta na frente da ação, e a ação aqui é o laço diário inteiro do
produto."* Ordem interna: **do agora para o raro**: primeiro o que fazer hoje (check-in ou convite),
depois o que o desafio é, e por último sair, *"que é decisão, não gesto"*.

**[LEI]** `GrupoDetalhe(id, aba)` existe para o deep link: *"'seu check-in foi denunciado' abrindo em
Sobre faz a pessoa trocar de aba para ver o que motivou o aviso."* **Levar ao grupo certo na aba
errada é meio caminho.** Os índices são constantes nomeadas (`AbasDoGrupo.POSTS`), nunca números
soltos: reordenar as abas quebraria o destino **em silêncio**.

**[LEI]** `aba` é `Int`, não o enum da tela: a rota é serializada pela navegação e não deve depender
de um tipo de tela.

#### Check-in
Tela própria, **não** diálogo: pode envolver câmera em tela cheia, permissão de localização e edição
de texto, *"três coisas que um `AlertDialog` faz mal, e que juntas precisam sobreviver a rotação e a
ida às configurações do sistema."*

**[LEI]** A tela é **guiada pelas regras do grupo**: exige foto → mostra a câmera (`BlocoDaFoto`);
exige local → mostra o campo (`BlocoDoLocal`); grupo sem regra nenhuma **vira um botão só**. As
regras foram aceitas na entrada, *"e é isso que torna a exigência aqui uma escolha informada e não
uma imposição descoberta agora."*

**[LEI]** **Um pedido de permissão só**, com todas as que o grupo exige
(`RequestMultiplePermissions`). Dois `launch()` seguidos não funcionam: o Android não mostra dois
diálogos ao mesmo tempo e o segundo é engolido, e foi o que fez a localização nunca ser pedida.

**[LEI]** E **só** as que o grupo exige: *"pedir câmera 'por via das dúvidas' num desafio sem foto
gasta a boa vontade que a gente vai precisar depois."*

**[LEI]** A permissão é **sempre relida** (`ON_RESUME`), nunca lembrada num `var`: quem ia às
Configurações, concedia e voltava encontrava o app dizendo que não tinha permissão.

**[LEI]** A **intenção** da pessoa ("quero local exato") é estado **da tela**, separado da permissão
que ela tem. São coisas diferentes: pode querer exatidão e não ter concedido, ou ter concedido e
voltar para o bairro.

Saída: `popBackStack()` para o **detalhe** (onde o feed acabou de ganhar um item), não para a lista:
*"quem faz check-in quer ver o próprio check-in aparecer."*

#### Comentários
Tela própria, **não** expansão no card: o feed viraria *"uma lista de alturas imprevisíveis que salta
a cada polling de 10s, e o teclado taparia metade do que se está lendo."*

**[LEI]** Leva o **grupo** junto (`Comentarios(groupId, checkInId)`) porque toda rota social é do
grupo: ele é a fronteira de acesso (#33).

#### Moderação
Tela própria, **não** modal nem quinta aba. Uma aba estaria visível para os 49 membros que não podem
abri-la, *"e escondê-la só para o admin faria a barra de abas mudar de tamanho conforme quem olha."*
Alcançada só pelo atalho na barra do detalhe, que também só existe para o admin, *"quando o badge
sobe."*

#### Criar / entrar
**[LEI]** `GrupoNovo` sai da pilha ao criar: *"voltar para o formulário depois do grupo criado
convidaria a criar o mesmo desafio duas vezes."*

`GrupoEntrar(inviteToken?)`: `null` = vai digitar o código; preenchido = chegou por link.

**[CONVENÇÃO]** Atualização de dado de terceiros ignora TTL e vai à rede **ao entrar na tela**: a
contagem de membros muda porque **outra pessoa entrou**, e *"esperar cinco minutos ali não é
economia, é atraso."*

---

### F7. Perfil e grafo social

```
gaveta/cabeçalho → Perfil()           [meu, cache-first]
                      ├─ editar → Conta
                      ├─ conquistas → Conquistas()
                      └─ amigos → Amigos
                                    ├─ aba amigos: LinhaDePessoa, MeuCodigo
                                    └─ aba pedidos
                                          └─ buscar por código → Perfil(userId)  [NUNCA pedido direto]

ranking/posts/membros → Perfil(userId)  [público, online]
                           └─ "ver meu perfil" → popBackStack + Perfil()
```

**[LEI]** (ARCH #34) Perfil **público** e **conta** são telas diferentes. *"Fronteira de privacidade é
o DTO, nunca uma condicional"*: dado privado **não existe** no contrato público.

**[LEI]** `Perfil(userId)` nasceu com o parâmetro mesmo quando só o próprio perfil era alcançável:
*"uma tela nascida 'só minha' acumula suposições que depois viram vazamento quando alguém a aponta
para outro usuário."*

**[LEI]** (#35) Buscar por código abre o **perfil**, nunca manda pedido direto: *"com o perfil
público, a confirmação que o ADR previa é o próprio perfil."*

**[LEI]** (#35) Ranking, posts e membros **levam todos ao mesmo lugar**: o perfil público.

**[LEI]** Amigos fica dentro do **Perfil**, não na gaveta: *"amizade é extensão da identidade, não
uma seção do app, e assim o contador de pedidos não exige estado no ícone global do menu."*

**[LEI]** `PerfilPublicoScreen` faz `popBackStack()` **antes** de navegar para o meu perfil, senão a
pilha vira `ranking → meu perfil público → meu perfil` e o voltar passa duas vezes pela mesma pessoa.

**[LEI]** (#33) Identidade em contexto social é `display_name`, **nunca e-mail**. O e-mail não
atravessa a fronteira do grupo.

---

### F8. Aprender (wiki)

```
gaveta → WikiScreen  [CardDeDestaque "COMECE AQUI" + chips de categoria + CardDeArtigo]
            └─ WikiArticleScreen(slug)
```

**[LEI]** Copia o esqueleto do `ExerciseLibraryScreen` de propósito (`Scaffold` + `TopAppBar` +
`LazyRow` de chips + `LazyColumn`): *"é o padrão de catálogo já estabelecido no app, e quem usou a
Biblioteca não precisa reaprender nada aqui."*

**[LEI]** O que **não** tem, e é decisão:

- **Busca.** A Biblioteca, com 893 exercícios, não tem (só chip). Com ~12 artigos, uma busca
  falharia na maioria das tentativas: *"seria uma máquina de decepção numa tela cujo trabalho é
  acolher quem está começando."*
- **Foto no card.** Seriam 10 a 14 imagens a produzir ou licenciar, e imagem é o segundo gargalo de
  conteúdo depois do texto. **O selo de categoria carrega a informação que a miniatura carregaria.**

**[LEI]** Os chips vêm do que **existe**: categoria sem artigo não vira chip que leva a uma tela
vazia.

**[CONVENÇÃO]** O "COMECE AQUI" (`CardDeDestaque`, fundo `surfaceVariant`, padding 20dp, rótulo
`labelSmall` Bold em `primary` e `.uppercase()`) *"resolve o problema de quem não sabe por onde
começar melhor que chip ou busca."*

**[CONVENÇÃO]** Artigo usa `bodyLarge` (leitura longa), diferente do resto do app.

---

### F9. Notificações e deep links

```
ícone da barra (com Badge) → NotificacoesScreen
                                 └─ tocar numa notificação → onde se AGE sobre ela
push (app aberto/fechado/morto) → pilhaDoPush(tipo, grupoAberto) → navega
```

**[LEI]** A central é o **ponto único de aviso**. Tudo que for notificação aparece ali, e é para lá
que o deep link vai quando o tipo não tem destino próprio.

**[LEI]** Tocar leva **onde se age**, não na lista: pedido de amizade abre **Amigos**, porque *"a
central é a lista, e quem tocou já sabe o que quer fazer"*. Tipo sem destino próprio **não navega**:
*"melhor não fazer nada do que levar a pessoa para um lugar aleatório."*

**[LEI]** O contador acompanha a **navegação entre abas**, não polling: *"trocar de tela-raiz é o
momento em que a pessoa olha para a barra, e pedido de amizade não é feed para justificar polling."*
Mais o evento de push, *"o outro momento em que o número muda sem a pessoa fazer nada"*.

**[LEI]** O deep link espera a navegação inicial terminar. Com o app **morto**, o `AppNavHost` compõe
já com o destino na mão, navega, e logo depois o Splash chama `popUpTo(Splash, inclusive)`, **apagando
a tela que o push acabou de abrir**. Sintoma observado: tocar na notificação com o app fechado abria a
Home.

**[LEI]** O gate bloqueia **só** Splash, Preparando, Login, Nome e Quiz. A primeira versão usava
`mostrarAbas` (só raízes) e tocar na notificação estando em Conquistas não fazia nada. **Tocar na
notificação é ação explícita e tem que levar de onde quer que a pessoa esteja.**

**[LEI]** (#36) **Notificação gravada nunca muda de texto.** Corrigir uma frase não altera as que já
estão na bandeja ou na central, só as novas. É o invariante funcionando, e engana quem está testando.

**[LEI]** (#36) A notificação é a **única exceção** à regra de que todo texto do usuário mora no
`strings.xml` do cliente: push só-dados não aparece com o app fechado, então o texto precisa sair
pronto do servidor.

**[LACUNA ATIVA]** Não há notificação de desbloqueio de conquista: hoje a avaliação só roda na
leitura, e o usuário descobre a medalha ao abrir a tela.

---

### F10. Conta, idioma, dúvidas, sair

```
gaveta → Conta
            ├─ bloqueados → BloqueadosScreen
            ├─ idioma → IdiomaScreen
            ├─ editar perfil de treino → EditarPerfil (= QuizScreen em modo edição)
            └─ sair → DialogoDeSaida → Login (popUpTo(0))
gaveta → IdiomaScreen   (mesmo destino, dois caminhos, de propósito)
gaveta → DuvidasScreen  (lista estática, sem servidor)
```

**[LEI]** `EditarPerfil` **reaproveita o `QuizScreen`** em modo edição, em vez de tela própria: *"até
ontem o único jeito de mudar ACADEMIA → CASA era desinstalar o app."*

**[LEI]** Idioma é alcançado pela gaveta **e** por Conta, de propósito: *"quem não lê o idioma atual
vai procurar um ícone, não uma palavra, e a gaveta é onde os ícones ficam."*

**[LEI]** Sair com o menu aberto **fecha o menu junto**: *"sair com o drawer aberto deixava um painel
sem dono por cima da tela de login."*

**[LACUNA ATIVA]** Preço/plano, cancelamento, contato de suporte e política de privacidade dependem
de decisão ou dado que só o Rafael tem. As telas correspondentes não existem.

---

### F11. Fluxo transversal: offline e sincronização

```
AÇÃO DO USUÁRIO
   ↓
grava LOCAL  →  outbox  →  agenda envio (WorkManager)
   ↓                            ↓
tela mostra o dado         falha temporária (sem rede / 5xx / 401) → continua tentando
+ SELO DE SYNC             falha permanente (4xx de recusa)       → PARA, selo em `error`,
                                                                     clicável → diálogo de descarte
```

**[LEI]** **Falha temporária × permanente** é a regra de maior consequência da fila. Na primeira
falha temporária o processamento **para** (a ordem entre alvos é dependência real).

**[LEI]** O outbox cobre o que o usuário **possui**: conteúdo `MANUAL` é otimista; conteúdo `AI` é
travado por entitlement e continua **online-only**, porque *"a recusa tem de chegar no momento da
ação, não pela fila minutos depois."*

**[LEI]** `DescartarPendenciaDialog` mostra a mensagem que o **servidor** devolveu. Antes ela existia
no domínio e não era lida em lugar nenhum, *"então o usuário via 'Didn't sync' sem nenhuma pista do
porquê."*

**[LEI]** Sem uma ação na falha permanente, ela travava **para sempre**: o outbox já parava de tentar
(é o que "permanente" quer dizer), mas nada na tela convidava o usuário a reconhecer isso e seguir em
frente.

**[LEI]** **Nunca usar o relógio do cliente para lógica.** `current_date` do Postgres é UTC: depois
das 21h em São Paulo, `- 1 day` ainda é *hoje* para um grupo em `America/Sao_Paulo`.

---

### F12. Fluxo transversal: gamificação

**[LEI]** (#16) XP, Nível e Conquistas são do **perfil individual**, e a cor lime é exclusiva dessas
recompensas. Derivados das sessões.

**[LEI]** (#18) **Grupos são baseados em contagem de check-ins, não em XP**, e recompensam badges de
grupo. Consequência de desenho: **nunca pintar nada de grupo com lime**, e nunca mostrar XP em
contexto de grupo.

**[LEI]** (#32) Recompensa concedida nunca é retirada (ver F5).

**[LEI]** Autoridade do servidor: XP, validação, IA e entitlement são do backend. O cliente tem
**cópia otimista**. Daí o aviso de XP pendente em `tertiaryContainer` na Home.

Onde a gamificação aparece hoje:

| superfície | o que mostra |
|---|---|
| Home, `FaixaDeProgresso` | streak (fogo), nível, barra de XP, XP pendente |
| gaveta, cabeçalho | "Nível N" em lime |
| Home, `CardTreinoConcluido` | check lime 44dp, "+N XP" em lime `headlineSmall`, sequência |
| Perfil | `CartaoDeProgresso`, `FileiraDeConquistas` |
| Perfil público | `CartaoDeNivel`, `Conquistas` |
| Conquistas | grade do catálogo + diálogo de celebração |

---

## 12. Restrições de texto que limitam o desenho

Estas não são regras de conteúdo: são restrições que **mudam o que se pode desenhar**.

**[LEI]** (#37) **Texto do usuário vive em um lugar só: o `strings.xml` do cliente.** O servidor
manda dados, códigos e chaves, nunca prosa. Exceção única: a notificação.

**[LEI]** **Sem travessão** (`—` nem `–`) em texto de usuário, em nenhum idioma, nem na documentação.
Verificado por teste.

**[LEI]** **Sem caixa alta cravada no texto** (ver 4.3). Caixa é desenho, mora no composable.

**[LEI]** **Rótulo de enum mora em `ui/Rotulos.kt`** e devolve `@StringRes Int`, nunca `String`.
Aconteceu duas vezes mostrar o identificador ao usuário (`ExerciseCategory` dizendo "Chest" a
brasileiro, `GroupRule` dizendo `localizacao` sem cedilha).

**[LEI]** `BottomTab.rotulo` é `@StringRes Int` **por cicatriz**: era `val label: String` com as
quatro palavras em português cravadas no enum, e **a barra de baixo, o componente mais visível do
app, atravessou uma fatia inteira em português** e apareceu assim no primeiro teste em inglês.
*"Texto guardado como propriedade de enum é invisível para quem procura texto em posição de UI: o
literal não está na tela, e a tela não tem literal."* Quarta ocorrência da família; existe
`EnumsSemTextoTest` para não haver uma quinta.

**[LEI]** **Plural é `<plurals>`, não `if (n == 1)`**: 12 plurais, com teste exigindo `one` e
`other`. Russo tem três formas, árabe seis, e o inglês trata zero como plural. Logo: **qualquer
contador novo no desenho precisa de plural, não de concatenação.**

**[LEI]** **Texto que descreve posição envelhece a cada mudança de layout.** "Apague o check-in
abaixo" quebrou quatro vezes. **Num redesenho, nenhuma string pode se referir a onde algo está na
tela.**

**[LEI]** **19 pares de texto idênticos** são mantidos de propósito, e **cinco são falsos
duplicados** com aviso cruzado nas duas pontas: `Sair` (do desafio × da conta), `Descanso` (dia ×
cronômetro), `Fazer check-in` (abrir × enviar), `Treino de hoje` (rótulo × nome), `FitJourney` (marca
× título). *"Unificá-los quebra uma tela num idioma que ninguém desta equipe lê."*

**[LEI]** `Frase` é um tipo selado (`Recurso(@StringRes)` × `DoServidor(String)`) porque parte das
frases de erro **não pode virar recurso**: código desconhecido ou família que usa a `message` do
servidor. *"Um app antigo falando com um servidor novo continua explicando melhor do que qualquer
genérico que a gente escrevesse aqui."* Nove códigos listados em `TextosDeErro.SEM_TEXTO_PROPRIO`
não são traduzidos, e isso é sabido e enumerado.

**[LEI]** A mesma chave no ícone e no rótulo da aba, de propósito: *"o leitor de tela lê os dois em
sequência, e duas frases diferentes ali viram repetição confusa."*

**[CONVENÇÃO]** `contentDescription = null` em ícone decorativo (dentro de card que já tem texto), e
descrição real em ícone que é a única pista (`IconButton`). O badge de notificações muda a descrição
conforme haja ou não não lidas.

---

## 13. Margem para modificações no layout

Esta é a seção para consultar **antes** de propor um redesenho. Por área: o que pode mudar
livremente, o que exige cuidado, e o que não se toca.

### 13.1 Regra geral

| dimensão | status |
|---|---|
| **Paleta (os hex)** | pode mudar, **em conjunto**. Trocar um `Chart*` sozinho quebra a garantia de daltonismo |
| **Papel de cada cor** | **[LEI]**. Lime = recompensa individual, azul = ação, roxo = IA, Chart* = dado |
| **Tipografia (fonte, escala, pesos)** | **[LIVRE] e recomendado**: hoje é o default do M3, sem identidade |
| **Espaçamento e raio** | **[LIVRE]**, de preferência virando tokens de verdade |
| **Elevação** | **[LIVRE]**, lembrando que em tema escuro a separação por cor de superfície funciona melhor que sombra |
| **Dark-only** | **[LEI] por ora**. Tema claro é fatia de trabalho, não ajuste |
| **Material 3 como base** | **[LEI] de fato**: sair dele significa reescrever as 39 telas |
| **Layout de cada tela** | **[LIVRE]** |
| **Hierarquia visual dentro da tela** | **[LIVRE]**, respeitando as ordens marcadas [LEI] abaixo |
| **Quantidade e nome das abas** | **[LEI]**: quatro, e disjuntas da gaveta |
| **Movimento entre telas** | **[LEI]** nos três gestos; **[LIVRE]** nos números |
| **Padrão de erro em 3 níveis** | **[LEI]**: a aparência de cada nível é livre, a política não |
| **Shimmer como placeholder** | **[LEI]** como princípio ("nunca vazio/preto"); a forma do esqueleto é livre |
| **Snackbar para feedback de ação** | **[LEI]** (convenção de UI do projeto) |

### 13.2 Por tela

| tela | **[LIVRE]** | **cuidado** | **[LEI], não mexa** |
|---|---|---|---|
| **Splash / Preparando** | composição, slot de marca (está esperando um logo), ilustração | nada pode encostar no topo sem `Scaffold` | sem animação de saída; teto de 20s; passo visível só no pós-login |
| **Login** | layout inteiro | o pedido de permissão **não** vem para cá | `ErroContexto.AUTENTICANDO`; saída para `Preparando` |
| **Nome** | layout inteiro | | antes do Quiz, sem volta |
| **Quiz** | aparência dos passos, controles, ilustração por passo, unificar `headlineSmall`/`titleLarge` | a barra de progresso **encosta no topo**: é onde o defeito de inset aparece | 1 pergunta por tela; progresso + fração; passos condicionais; sem movimento de "tarefa" |
| **ProgramOffer** | layout, ilustração | | existe para nada ser criado sem pedido; fica **depois** do quiz; ícone/cor de IA |
| **ProgramReveal** | layout dos três estados, revelação mais cênica | | nome e rationale derivados no cliente; `popUpTo` da oferta |
| **Paywall** | a tabela comparativa inteira (forma, ordem, agrupamento) | manter as 13 capacidades e os 3 grupos, ou mudar o conteúdo junto com o produto | `voltarParaHome`; `invalidate()` só se assinou; `Celula` é tipo fechado, não string |
| **Home** | composição da faixa de XP, dos nove cards, dos atalhos, densidade, ordem visual dos atalhos | os nove estados precisam continuar **distinguíveis**; não unificar "não sincronizou" com "sem programa" | a **ordem de precedência** dos nove; faixa sempre visível; lime só na recompensa; atalhos via `trocarDeAba` |
| **Sessão de treino** | aparência dos cinco blocos, forma do cronômetro, do seletor de séries, do descanso | **reservar lugar para o RIR** (próxima fatia); manter "Série feita" como único destaque | a **ordem** exercício→números→séries→registrar→descanso; técnica expande, não navega; player só aberto; sem chip sem vídeo; "Encerrar" discreto; confirmação só com algo a perder; `LazyRow` nas séries |
| **Programas (lista/detalhe)** | layout de card, de lista, do picker de dia | o blur dos dias trancados é do servidor: a UI não fura | hierarquia programa→treinos; gate #23; `editLocked` barra antes de entrar |
| **Progresso** | tipo e aparência dos gráficos, dos cartões, do seletor de janela | **cor sem rótulo quebra o par `Chart3`/`AiPurple`**; nulo não decide estado | os três estados; filtro some com <2 opções; chips com padding só no `start` |
| **Conquistas** | grade, forma da medalha, celebração | | catálogo inteiro visível; lê data de desbloqueio, não progresso × alvo; lime |
| **Grupos (detalhe)** | aparência das 4 abas, do cabeçalho, do feed, do ranking | colapsar o cabeçalho com `nestedScroll` é melhoria **desejada**, não proibida | 4 abas, nessa ordem; abertura em Ranking se ativo; ações na aba Sobre; índices nomeados |
| **Check-in** | layout dos blocos, câmera, mapa | | tela própria; 1 pedido de permissão só, e só as exigidas; permissão relida no resume; intenção ≠ permissão |
| **Comentários / Moderação** | layout inteiro | são **online-only**: usar `ContextoDeCache.SEM_CACHE` | telas próprias, pelas razões escritas |
| **Perfil / Perfil público** | layout inteiro | o público **não** pode exibir dado privado: a fronteira é o DTO | uma rota, duas telas; `popBackStack` antes de ir ao meu |
| **Amigos** | layout, abas | | busca por código abre perfil, nunca manda pedido |
| **Aprender** | layout dos cards, do destaque, dos chips | adicionar foto significa produzir 10 a 14 imagens | sem busca; chips só do que existe; segue o padrão da Biblioteca |
| **Gaveta** | aparência do cabeçalho, agrupamento, largura | | só navegação, nada de interruptor; globo no idioma; "Sair" sai; 3 estados do cabeçalho |
| **Barra de abas / topo** | aparência | título vazio nas raízes é oportunidade, não regra | 4 abas; só nas raízes; `trocarDeAba`; badge de notificações |
| **Erros** | aparência dos 3 níveis | | a política, o mapa ícone→erro, validação no campo, `VOLTAR` sem snackbar |

### 13.3 Mudanças de alto valor e baixo risco

Em ordem de ganho por esforço, se o objetivo é melhorar o visual do app:

1. **`Typography` customizado no `FitJourneyTheme`.** Muda o app inteiro num arquivo. Hoje não há
   identidade tipográfica nenhuma.
2. **`Shapes` customizado no tema.** Unifica o raio do `Card` (default do M3) com o `12.dp` dos
   blocos manuais, que hoje divergem visivelmente.
3. **Completar o `colorScheme`**: `surfaceContainer*`, `errorContainer`, `scrim`, `inverseSurface`.
   Hoje caem no default do `darkColorScheme`, fora da paleta Volt Athletic.
4. **Asset de marca.** O slot `MarcaDoApp()` já existe e espera um `Image`.
5. **Tokens de espaçamento** (`Spacing.kt` no módulo de design system), substituindo os literais.
6. **Dar fundo transparente ou tratamento às ilustrações do catálogo.** Com o roxo resolvido, a
   miniatura de fundo branco virou o elemento mais aceso da tela de execução, competindo com o botão
   de ação. É problema de mídia, não de tema: entra junto dos 963 MP4 a re-encodar.
7. **Cabeçalho colapsável no detalhe do grupo** (`nestedScroll`): está escrito no código como o ideal
   que não foi feito.
8. **Mover `ui/` para `shared/core/designsystem`**, para os componentes valerem no target iOS.

---

## 14. Lacunas e decisões abertas

### 14.1 `[INFERIDO]`: onde o código não decide nada

| item | situação |
|---|---|
| Tipografia | default do M3. Nenhuma decisão de fonte, peso, tracking ou escala |
| Shapes | nenhum `Shapes` no tema; raios literais por componente |
| Espaçamento | nenhum token; escala emergente (8/12/16/24) |
| Slots do `colorScheme` | 7 slots do M3 sem valor próprio (`secondaryContainer` resolvido em 2026-11-03) |
| Marca | nenhum asset de logo; só `ic_launcher` |
| Ícones próprios | nenhum; 100% Material Icons |
| Tema claro | não existe |
| Densidade / tablet / landscape | nenhuma decisão registrada; nenhum breakpoint |
| Acessibilidade | `contentDescription` é cuidado caso a caso; não há auditoria de contraste além dos `Chart*` |
| `TextLow` (`#6B7280`) | definido e praticamente não usado via tema |
| Localização de `ui/` | componentes no módulo Android, tokens no módulo KMP. Sem decisão escrita |
| Ilustrações de fundo branco | acendem no tema escuro; hoje são o elemento mais claro da sessão |
| Três azuis na sessão | chip da série, "Série feita" e play do descanso competem |
| Peso abre em 0 | o número protagonista da tela abre no valor menos informativo |

### 14.2 Lacunas de produto que afetam o desenho

| lacuna | consequência |
|---|---|
| **RIR não é escrito pelo cliente** | a Regra 4 do motor e a calibração nunca disparam. Próxima fatia (`trabalho/rir-plano.md`). **Reservar lugar na sessão** |
| **Sem notificação de conquista** | o usuário descobre a medalha ao abrir a tela |
| **Sem campo de anotação** | não existe coluna, DTO nem rota. Não desenhar o botão |
| **Sem listener de conectividade** | a tela aberta não reage à rede voltando |
| **Preço, cancelamento, suporte, privacidade** | telas inexistentes; dependem de decisão do Rafael |
| **Premium não edita conteúdo de IA offline** | consequência aceita da fronteira #30 × #25 |
| **`stats` + `session` + `achievements` no módulo app** | deviam ser features próprias |
| **Motor v3 dormente** | `ProgramDto.avisos` e `grupamentosReduzidosPeloFoco` já chegam ao cliente e aparecem no card `AvisosDoPrograma`, mas **ninguém vê nada até o v3 virar o gerador de produção** |
| **24 imagens órfãs, 963 MP4 a re-encodar (197 MB)** | 95% da mídia do app. Afeta qualquer desenho que dependa de imagem |

### 14.3 O que o Claude Design precisa decidir

1. **A fonte.** É a decisão de maior impacto visual disponível, e o app não tem nenhuma.
2. **A marca.** Logo, ou a tipografia da palavra "FitJourney" como marca.
3. **Os 7 slots de cor faltantes**, dentro da paleta Volt Athletic.
4. **A escala de espaçamento e de raio**, como token e não como literal.
5. **Se o lime ganha mais expressões** (gradiente, glow, textura) sem sair do papel de recompensa.
6. **Como os estados vazios ganham ilustração** sem exigir produção de imagem por tela (os nove cards
   da Home hoje usam só ícone do Material).
7. **Se vale um tema claro**, sabendo que é fatia de trabalho e não ajuste.
8. **Como o RIR entra na sessão** ao lado de peso e reps, sem roubar o destaque do "Série feita".

---

## Apêndice: onde ler o quê, no código

| assunto | arquivo |
|---|---|
| tokens de cor, hierarquia, regra do lime | `shared/core/designsystem/.../Color.kt` |
| tema e mapeamento M3 | `shared/core/designsystem/.../Theme.kt` |
| as 36 rotas, com a razão de cada uma | `app/androidApp/.../navigation/AppRoutes.kt` |
| abas, `trocarDeAba`, bottom bar | `.../navigation/BottomNav.kt` |
| movimento entre telas | `.../navigation/Movimento.kt` |
| insets, gaveta, top bar, deep link, sessão expirada | `.../navigation/AppNavHost.kt` |
| política de erro (3 níveis) | `.../ui/ErrorUi.kt` |
| shimmer, esqueletos, imagem de rede | `.../ui/Loading.kt` |
| avatar e sua paleta | `.../ui/Avatar.kt` |
| selo de sync e descarte de pendência | `.../ui/SeloDeSync.kt` |
| `Frase` (catálogo × servidor) | `.../ui/Frase.kt` |
| rótulos de enum | `.../ui/Rotulos.kt` |
| gaveta | `.../screens/menu/MenuLateral.kt` |
| decisão de rota na abertura | `.../screens/splash/SplashViewModel.kt` |
| os nove estados da Home | `.../screens/home/HomeScreen.kt` |
| sessão de treino | `.../screens/session/WorkoutSessionScreen.kt` |
| abas e cabeçalho do grupo | `.../screens/grupos/GrupoDetalheScreen.kt` |
| três estados do progresso, gráficos | `.../screens/progress/ProgressScreen.kt`, `GraficosDeProgresso.kt` |
| convenções permanentes do projeto | `docs/CONVENCOES.md` |
| gamificação (#16, #18, #32) | `docs/regras/06-gamificacao.md` |
| idioma (#37) | `docs/regras/13-idioma.md` |
| offline (#30) | `docs/regras/05-offline.md` |
