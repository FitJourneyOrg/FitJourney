# Design e cor (ARCH #16, #37)

> Índice: `docs/regras/README.md` · Marcadores: [REGRA] imutável · [INV] invariante · [PROPOSTA] lacuna não ratificada.
>
> Referência completa (tokens, componentes, fluxos tela a tela, margem de manobra por tela):
> `docs/DESIGN_SYSTEM.md`. Aqui ficam só as regras que alguém pode violar escrevendo código.

## Hierarquia de cor

- **[REGRA]** (#16) Cada família de cor tem UM papel: `Volt`/lime = recompensa do perfil individual ·
  `ActionBlue` = ação primária do usuário · `AiPurple` = geração por IA · `Chart1/2/3` = dado ·
  `Ink*`/`Text*` = neutro · `Danger`/`Warning` = falha e aviso. Misturar apaga o significado.
- **[REGRA]** (#16) `colorScheme.tertiary` **é** o lime. Usá-lo fora de recompensa individual viola a
  regra mesmo quando o código parece genérico. Para ação, `primary`.
- **[INV]** Os três `Chart*` foram escolhidos **em conjunto** e validados contra `Ink1` para
  daltonismo (pior par: ΔE 12,1 em deuteranopia, 23,7 em visão normal, todos ≥ 3:1). Trocar um
  sozinho quebra a garantia.
- **[INV]** `Chart3` é próximo de `AiPurple`, e os dois só convivem porque **nenhuma linha de gráfico
  depende apenas da cor**: cada uma leva o nome do exercício na ponta. Gráfico que use cor sem rótulo
  obriga a rever este par antes.
- **[REGRA]** Cor de avatar não pode significar estado: a paleta do `AvatarInicial` não tem lime,
  vermelho nem amarelo.

## O slot tonal do Material 3

- **[REGRA]** (2026-11-03) **`secondaryContainer` é NEUTRO (`Ink3`), não o roxo de IA.** O Material 3
  usa esse slot como a cor tonal genérica: `FilledTonalIconButton`, `FilterChip` selecionado e a
  trilha do `LinearProgressIndicator` buscam ali sem ninguém pedir.
- **[REGRA]** Componente que precisa de tonal **com significado** (seleção, ação já executada) pede
  `primaryContainer` **explicitamente**, no próprio componente.

  > **Cor que o Material pede emprestada para tudo tem de ser neutra. Quem quer significado pede a
  > cor explicitamente.**

  Narrativa e o custo que isso já teve: `docs/historico/correcao-do-tonal-generico.md`.

## Caixa alta e tipografia

- **[REGRA]** (#37) Caixa alta é decisão de DESENHO e mora no composable (`.uppercase()`), nunca no
  `strings.xml`. O Compose não tem `text-transform`, e caixa no catálogo quebra em alemão, turco e
  escritas sem caixa. Há teste que recusa texto que chega gritando no catálogo.
- **[REGRA]** (#37) Símbolo é desenho, pela mesma razão: **emoji não entra no `strings.xml`.**
  (Débito aberto: duas strings do `WorkoutDetailScreen` ainda violam isto. Ver `fontes/debitos.md`.)

## Insets

- **[REGRA]** Quem desenha a barra do topo pinta a status bar. O `Scaffold` do `AppNavHost` não
  reserva inset para si; **toda tela fora das quatro raízes precisa do próprio `Scaffold`**, mesmo
  sem barra nenhuma.
- **[INV]** Nenhuma tela chama `statusBarsPadding`, `navigationBarsPadding` ou `systemBarsPadding`.
  Travado no `ArchitectureKonsistTest`. `imePadding` continua sendo de quem tem campo de texto.

## Estado e feedback

- **[REGRA]** A política de apresentação de erro é única e vive em `ui/ErrorUi.kt`: nível 1
  silencioso (há dado local), nível 2 estado de tela (não há), nível 3 erro de ação (snackbar ou
  linha inline). Validação vai embaixo do campo, nunca em snackbar.
- **[REGRA]** Shimmer é o placeholder padrão de carregamento: nunca deixar vazio nem preto.
- **[REGRA]** Esqueleto não afirma nada. Carregar e falhar não podem ser o mesmo pixel.
- **[REGRA]** Escrita otimista exige selo na UI (#30): sem ele o usuário não distingue "está no
  servidor" de "está só neste aparelho".
- **[REGRA]** Convenção de UI: feedback de ação rápida e mensagem curta são sempre **snackbar**.

## Tema

- **[REGRA]** O app é dark-only por ora. Não existe `lightColorScheme`, e nenhuma tela tem variante
  clara. Tema claro é fatia de trabalho, não ajuste.
- **[PROPOSTA]** A fonte do app é a **Inter** (decidida em 2026-11-03, ainda não no código). Peso,
  tracking e escala continuam lacuna. A marca é a palavra, em Inter 800, sempre em `TextHigh`,
  **nunca em lime**: lime na marca passaria a significar "FitJourney" além de "recompensa" e
  esvaziaria a #16 por dentro.
- **[PROPOSTA]** (2026-11-04) Estado ("ligado", "ativo", "correndo") se diz com **neutro mais
  movimento ou forma**, nunca com um tom novo. Abrir um tom para estado exigiria rever a paleta em
  conjunto.
- **[PROPOSTA]** (2026-11-04) Selo pequeno que carrega a cor de uma família usa a cor no **traço**,
  com rótulo em `TextHigh`: em 10 a 11px sobre `Ink1`, `ActionBlue` dá 3,43:1 e `AiPurple` 4,38:1, e
  os dois reprovam o 4,5. No contorno, onde bastam 3:1, passam.
