# ARCH #31 — Política única de apresentação de erro

**Fase:** 5 · **Status:** implementado (2026-08)

> Complementa o **ARCH #4** (erro como fluxo funcional, não exceção) e o **ARCH #30**
> (offline-first). O #4 definiu como o erro *viaja*; este define como ele *aparece*.

## O problema

Não havia política. Cada ViewModel decidia sozinho como mostrar erro, e o resultado foi um
conjunto de comportamentos incoerentes:

- banner vermelho de rede numa tela que tinha dado local e funcionava perfeitamente;
- "Sem conexão" com o wifi ligado, quando o servidor estava parado;
- 401 exibindo "Sessão expirada" com um botão "Tentar de novo" que só repetia o 401 — sem
  caminho de volta para o login;
- `fieldErrors` trafegando do servidor ao cliente sem nenhuma tela consumir.

O sintoma que expôs isso: com wifi ativo e o Ktor parado, o app dizia "Sem conexão".

## Decisão 1 — `AppError.Connection` separa transporte de erro do servidor

Antes, qualquer falha não-HTTP virava `AppError.Unexpected("Falha de rede")`. Como `Unexpected`
**também** é o 500, os dois eram indistinguíveis. Isso causou um defeito silencioso:
`is AppError.Unexpected` era o gatilho do fallback de cache nos repositórios, então uma exceção
interna do servidor fazia o app servir dado velho e esconder a falha.

```
AppError.Connection   → sem rede, servidor fora do ar, timeout, DNS, 502/503/504
AppError.Unexpected   → 500 e desconhecidos
```

**[REGRA] Só `Connection` autoriza servir cache.** Resposta do servidor (401/403/404/500) é
informação real e propaga.

**[REGRA] `AppError` não diz se o aparelho está offline.** Isso é fato de plataforma
(`ConnectivityManager`), resolvido na UI. `core:result` segue Kotlin puro — colocar a pergunta
ali arrastaria Android para o domínio ou exigiria injetar conectividade em 25 call sites.

**[REGRA] Rede que volta NÃO recarrega tela aberta (decidido em 2026-10-08).** Não há listener de conectividade. A escrita offline sobe sozinha pelo WorkManager (`NetworkType.CONNECTED`); a leitura se atualiza ao entrar na tela, ao puxar (F.2) ou em "Tentar de novo". O aviso de offline pode ficar na tela até uma dessas três. Reabrir se isso virar reclamação real.

## Decisão 2 — quatro níveis de apresentação

O mesmo erro merece tratamentos diferentes conforme o que a tela tem em mãos:

| nível | quando | componente |
|---|---|---|
| **1. Silencioso** | sync falhou **mas há dado local** | nenhum — é a ausência dele |
| **2. Estado de tela** | falhou e **não há nada** para mostrar | `ErroDeTela` |
| **3. Inline** | erro ancorado num campo/formulário | `ErroInline` |
| **3. Snackbar** | ação avulsa que falhou | `ErroEmSnackbar` |

O nível 1 é o mais importante e o mais fácil de esquecer: **offline com dados na mão não é
erro**. Era o banner vermelho aparecendo por cima de uma lista que funcionava.

Snackbar não serve em formulário — ele desaparece e o usuário perde a referência do que falhou.
Por isso o `ErroInline` existe como forma separada.

**[REGRA] Validação não vira snackbar.** O lugar dela é embaixo do campo, via `fieldErrors`.

## Decisão 3 — o componente não navega

`app/ui/ErrorUi.kt` traduz `AppError` em `ErroVisual` e devolve a **`ErroAcao` recomendada**
(`TENTAR_DE_NOVO`, `IR_PRO_LOGIN`, `VER_PLANOS`, `VOLTAR`, `NENHUMA`). Quem executa é a tela.
Rota é assunto do NavHost, não de um componente de erro.

Consequência prática: o paywall dedicado que já existia (`showPaywall` no `WorkoutDetail`)
**não** foi substituído por `VER_PLANOS`. Um diálogo de assinatura é melhor que um card de erro
genérico, e já tinha teste. `VER_PLANOS` é fallback para telas sem tratamento próprio.

## Decisão 4 — o contexto muda o significado do status

**[REGRA] 401 na tela de login ≠ 401 numa tela logada.**

```
ErroContexto.LOGADO       → "Sessão expirada" + IR_PRO_LOGIN
ErroContexto.AUTENTICANDO → "E-mail ou senha incorretos" + NENHUMA
```

Sem essa distinção, o handler global de sessão expirada deslogaria quem apenas errou a senha.

## Decisão 5 — sessão expirada é evento global

`SessionExpiryBus` (em `core:network`) emite quando um 401 **sobrevive à renovação do token**.
O NavHost escuta, desloga e vai ao Login limpando a pilha.

Fica no core, e não numa tela, porque o 401 pode nascer em qualquer request — inclusive no
`SyncWorker` com o app fechado, longe de qualquer UI.

**FIX que veio com isso:** o `install(Auth)` tinha `loadTokens` e **não tinha** `refreshTokens`.
O Ktor cacheia o token e nunca mais o recarrega; token do Firebase dura ~1h. Passada essa hora,
**todo** request virava 401 permanente, com o usuário logado e sem saída. Agora o 401 tem dois
caminhos: renova e repete (invisível), ou sinaliza sessão morta.

## Decisão 6 — `fieldErrors` só onde o usuário pode agir

O servidor tinha ~30 pontos de validação e **nenhum** preenchia `fieldErrors`. Marcamos os 11
que correspondem a um campo corrigível (perfil, treino, programa). Os ~19 restantes são
validações de rota/id — bug de cliente, não erro de preenchimento; marcá-los sugeriria que
existe um campo para consertar.

`ErrorFields` (em `shared-contract`) é o contrato das chaves, como `ErrorCodes`.

## Alternativas descartadas

**Dois tipos (`Offline` + `ServerUnreachable`) no `AppError`.** Arrastaria plataforma para o
domínio, ou exigiria injetar um `NetworkStatus` em 25 call sites. A distinção é de mensagem, e
mensagem é da UI.

**Blur no `GET /workouts/{id}` em vez de 403.** Criaria um segundo idioma para "você não pode":
escrita responde 403 (`requireEditable`, ARCH #25), leitura responderia objeto-mentira. Ver
ARCH #23.

**String formatada no state.** Era o que existia. Achata a decisão de texto no ViewModel, que
não sabe se o problema é o wifi do usuário ou o servidor.

## Onde estamos

- `AppError.Connection` + `mapHttpError`: **feito**, com testes
- `ErrorUi` (4 formas) e 11 states migrados de `String` para `AppError`: **feito**
- `refreshTokens` + `SessionExpiryBus`: **feito**
- `fieldErrors` nos 11 pontos: **feito** no servidor; consumido em WorkoutForm e no diálogo de
  criar programa
- `ErrorMessage.kt` (o `toMessage()` antigo): **removido**

## Débito aberto

O `fieldErrors` do nome de programa é rede de segurança, não caminho normal: o botão "Criar"
usa `enabled = name.isNotBlank()`, então a requisição inválida nem sai. Correto — mas significa
que aquele caminho não é exercitável pela UI.
