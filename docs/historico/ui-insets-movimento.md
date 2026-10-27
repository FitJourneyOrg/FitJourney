# UI — insets, movimento e a tela que faltava (2026-09-15)

> **Histórico.** Fatia fechada — preservado literal do antigo `handoff-retomada.md`.
> Estado atual em `docs/ESTADO.md`; convenções em `docs/CONVENCOES.md`;
> regra de comportamento em `docs/regras/`; débitos em `docs/fontes/debitos.md`.

---

## 🎨 UI: insets, movimento e a tela que faltava (2026-09-15)

Três correções que nasceram do teste no aparelho, **depois** de a G.5 fechar. Só cliente.

### O defeito dos insets teve duas metades, e a segunda foi criada pela correção da primeira

O app tem `Scaffold` aninhado — um no `AppNavHost` (menu e abas) e um em cada tela — e **ninguém tinha declarado de quem eram os insets**.

1. **Faixa preta em cima e embaixo de toda tela.** `Modifier.padding(padding)` empurra o conteúdo e não consome inset nenhum, então o `Scaffold` de dentro descontava a status bar e a navigation bar outra vez.
2. **Status bar preta acima do header.** Consumir tudo no `AppNavHost` matou a faixa dupla e tirou da `TopAppBar` de cada tela a chance de alcançar a status bar.

O arranjo final é `contentWindowInsets = WindowInsets(0)` no externo — ele deixa de reservar inset para si e passa a entregar só a **altura das barras que desenhou** — mais o `consumeWindowInsets(padding)`.

| | `mostrarAbas` = false | `mostrarAbas` = true |
|---|---|---|
| `padding` do externo | zero | altura das barras, inset embutido |
| a tela enxerga | os insets inteiros | zero |
| quem pinta a status bar | a `TopAppBar` da tela | a do `AppNavHost` |

Funciona porque `TopAppBar` e `NavigationBar` do Material 3 **já aplicam o próprio inset**. O erro era o `Scaffold` externo tirar delas essa chance.

> **[REGRA] Quem desenha a barra do topo pinta a status bar.**

Travado por regra nova no `ArchitectureKonsistTest`, com **três exceções declaradas**: `AppNavHost` (o dono), `MainActivity` (o `enableEdgeToEdge`) e **`MenuLateral`** — a gaveta fica fora do `Scaffold`, dentro do `ModalNavigationDrawer`, e é o único lugar onde esses modificadores ainda somam pixel.

### `Movimento.kt` — a escolha mora nas rotas, não nos 36 destinos

| gesto | movimento |
|---|---|
| empilhar | entra da direita; a de trás recua 1/4 e escurece |
| trocar de aba | esmaece com escala de 92%, **sem direção** |
| entrar numa tarefa (execução, check-in, paywall) | sobe de baixo |

As abas não deslizam porque a navegação entre raízes usa `popUpTo` e a pilha não guarda de onde se veio: um deslize teria de chutar um lado e acertaria metade das vezes.

Anotar os 36 `composable` seria o caminho óbvio e é o errado — **a rota 37 nasceria sem movimento**, o defeito numa tela só. Mesma lição dos insets, no mesmo dia.

> **Regra que se repete por destino não é regra: é 36 oportunidades de divergir.**

### `ExerciseLibraryScreen` ganhou `Scaffold`

Era a **única tela navegável sem um**. O `Text` de título não pintava a barra, não reservava a própria altura e não tinha botão voltar; com o edge-to-edge, passou a dividir a faixa com o relógio. Varri as outras 31 — as 7 que seguem sem `Scaffold` estão certas assim (Splash, Login, os dois passos de onboarding, as duas telas de conversão, e as duas que vivem sob as abas).

⚠️ **Não conferidos ainda:** `ProgressScreen` e `EmBreveScreen`, que vivem sob as abas.

---

---

