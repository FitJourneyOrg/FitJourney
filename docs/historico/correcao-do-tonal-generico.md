# Correção do tonal genérico (2026-11-03)

> O roxo de IA aparecia em treze arquivos onde não havia IA nenhuma, e o defeito já tinha sido
> achado uma vez e remendado na tela errada.

## Como apareceu

A sessão começou com um pedido de documentação: um README completo do design system para alimentar
o Claude Design. Ao montar a especificação, o Rafael mandou um screenshot da tela de execução de
treino e perguntou se já podia mandar para o desenho.

Três elementos da tela estavam roxos: os botões `-`/`+` de peso e reps, o chip da série selecionada
e a trilha da barra de progresso. Nenhum dos três tem relação com IA.

## A causa

O `FitJourneyTheme` mapeava `secondaryContainer = AiPurpleDark`. O Material 3 usa esse slot como a
**cor tonal genérica**: `FilledTonalIconButton`, `FilterChip` selecionado e a trilha do
`LinearProgressIndicator` vão todos buscar ali sem ninguém pedir. Na trilha M3 Expressive
(`material3 1.11.0-alpha07`, que é a do projeto) a trilha do progresso também passou a usar esse
slot.

**Treze arquivos do módulo app herdavam dele.**

## O que torna este caso instrutivo

O defeito **já tinha sido encontrado**, em 2026-09, e corrigido **numa tela só**. O
`AchievementsScreen` passou a declarar `trackColor` à mão, com este comentário:

> `trackColor` EXPLÍCITO. O default do Material3 é `secondaryContainer`, que neste tema é roxo vivo,
> mais chamativo que o próprio progresso, cinza. **O resultado lido de relance era o inverso do real:
> 11 de 30 parecia 90%.**

Alguém pagou o preço, consertou onde doeu e seguiu. O slot continuou roxo, e o mesmo defeito seguiu
vivo nas outras doze telas, incluindo a de execução de treino, que é onde o usuário passa mais tempo.

É a mesma família de lição que o `CONVENCOES.md` já registra duas vezes sobre ramificação por valor
único: **corrigir o sintoma deixa a origem intacta, e a origem produz o sintoma de novo noutro
lugar.**

## A regra que saiu disso

Um slot, três trabalhos com necessidades opostas:

| o que usa o slot | precisa ser |
|---|---|
| trilha de progresso | recessivo |
| chip selecionado com significado | legível como seleção |
| botão de ação já executada | lido como feito, não como desabilitado |

> **Cor que o Material pede emprestada para tudo tem de ser neutra. Quem quer significado pede a cor
> explicitamente.**

**Tonal genérico é neutro. Tonal com significado é o tom escuro da família da ação.**

Promovida para `regras/15-design-e-cor.md`.

## O que mudou no código

| arquivo | mudança |
|---|---|
| `Theme.kt` | `secondaryContainer = Ink3` (era `AiPurpleDark`), com a regra e a narrativa em KDoc |
| `WorkoutSessionScreen.kt` | `FilterChip` da série declara `selectedContainerColor = primaryContainer` |
| `WorkoutSessionScreen.kt` | `BotaoSerieFeita` marcado usa `primaryContainer` (era `secondaryContainer`) |

Suítes verdes confirmadas pelo Rafael: `:konsist:test`, `testAndroidHostTest`,
`:app:androidApp:testDebugUnitTest`.

## Verificado no aparelho

Screenshot antes e depois, mesma tela, mesmo estado. Os `-`/`+` e a trilha viraram neutros; o chip da
série e o "Série feita" marcado viraram azul escuro; o "Série feita" não marcado passou a ser o único
ponto aceso da tela.

**O tracinho azul no fim da trilha não é resíduo**: é o *stop indicator* que o M3 Expressive desenha
no fim da barra, na cor do indicador.

## O que a tela limpa revelou

Com o roxo fora, o problema de hierarquia mudou de dono: **a miniatura do exercício, que tem fundo
branco, virou o elemento mais aceso da tela** e passou a competir com o botão de ação. Não se resolve
no tema: é mídia. Registrado em `fontes/debitos.md`.

## Pontas soltas, deliberadas

1. `AiPurpleDark` ficou **sem uso no tema**. Continua exportado como token e documentado.
2. O remendo do `AchievementsScreen` virou redundante, e **o comentário dele virou falso** (diz que o
   slot "é roxo vivo"). Não foi mexido na mesma fatia, por ser outra tela.
3. Os outros doze arquivos que herdam o slot não foram inspecionados um a um. A mudança os corrige
   automaticamente, mas algum pode ter ficado quieto demais, em especial `FilterChip` selecionado
   fora da sessão.

## Também nesta sessão

- **`docs/DESIGN_SYSTEM.md`**: documento novo, a referência completa de cor, tipografia,
  espaçamento, movimento, componentes, ícones, os doze fluxos do usuário e a margem de manobra por
  tela.
- **Artifact "FitJourney Volt Athletic"** (tipo Design System, privado): a mesma referência em forma
  navegável, com tokens, oito componentes com preview ao vivo e oito seções de especificação por
  área (onboarding, treino, progresso, grupos, social, aprender, conta, home). É cópia para consumo;
  a fonte de verdade continua sendo o arquivo em `docs/`.
