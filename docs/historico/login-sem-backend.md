# Login sem backend — fatia fechada (2026-11-05)

> Movido de `docs/trabalho/login-sem-backend.md` ao fechar o ciclo. Decisões e desenho completos
> ficaram no plano original (ver git log); aqui só a narrativa e os achados.

## O que entregou

Fecha o débito mais antigo ainda aberto no arquivo (`fontes/debitos.md`, vizinho de datas de
2026-08-16/20/21, nunca reclassificado desde então). `LoginViewModel.submit()` entra com a
sessão do Firebase direto, sem esperar `fetchMe()` (`GET /me`) — backend fora do ar deixava de
bloquear o login mesmo com credencial válida. Junto, achado e corrigido um segundo defeito, real
e reproduzido no aparelho pelo Rafael, do mesmo fluxo.

## Como a fatia nasceu, e o que a leitura do código derrubou

O débito original (escrito horas antes, na fatia do catálogo) trazia uma cautela própria: *"hoje
`fetchMe` é o que garante a linha em `users` antes de qualquer rota"*. Ler o código antes de
desenhar a correção achou isso **falso**: `UserService.findOrCreate()` já roda, idempotente e
decentralizado, em toda rota autenticada do servidor (`/me`, `/profile`, `/programs`,
`/workouts`, `/users/by-code/{code}`, `/me/subscribe`...). A linha em `users` nasce no primeiro
`findOrCreate` que rodar, de qualquer rota, sempre que o backend voltar — não precisa do login.

Segundo achado: a resiliência que a correção precisaria **já existia**, do lado errado.
`SplashViewModel.prepararAntesDaHome()` já chama `me.sincronizar()` com timeout, engolindo falha
de rede em silêncio ("mantém o último nome conhecido") — o próprio KDoc de lá já diz que prender
alguém num indicador de progresso é pior que uma Home incompleta. O `LoginViewModel` nunca
deixava a pessoa chegar lá: travava antes, numa segunda confirmação que a Splash já faz de novo.

Essa leitura (que o fix não precisava mexer em `users`/servidor, só no cliente) foi o que reduziu
o risco da mudança a ponto de ela não precisar de ADR.

## O [REGRA] que a mudança roçou, e por que não o violou

`00-regras-gerais.md:5`: "Firebase autentica; o backend autoriza." Risco de ler a mudança como
uma violação: o login deixa de esperar confirmação do backend antes de navegar. Confirmado com o
Rafael antes de codar: a autorização de verdade é o plugin `authenticate(FIREBASE_AUTH)`
validando o JWT em CADA request subsequente — isso não muda, continua rodando sempre, em toda
rota. O que a tela de login fazia era uma confirmação extra, antecipada e redundante — não era
ela que concedia acesso a nada.

## Segundo defeito, achado na mesma investigação — confirmado pelo Rafael no aparelho

Sequência reproduzida: offline, credencial CORRETA, `signIn()` falha (esperado — 1º login exige
internet). Depois, internet volta e o app é REABERTO — cai direto na Home, como se tivesse
logado, sem o usuário ter passado pelo fluxo de login de novo.

Mecanismo, cruzando com `docs/erros/02-403-login-mas-reabre-logado.md` (que já documentava a
metade de baixo disto): a Home é cache-first e o gate da Splash (`isLoggedIn()`) só olha
`auth.currentUser`, sem bater rede — [INV] intencional do ARCH #30, não o defeito. O defeito era
`auth.currentUser` ficar não-nulo depois de uma chamada que o PRÓPRIO app relatou como FALHA.
`signIn()`/`signUp()` faziam `runCatching { ... }` sem nenhum `signOut()` no caminho de erro — se
o SDK do Firebase deixa algum estado de sessão montado mesmo numa chamada que lança por rede, ele
ficava lá, e o próximo `isLoggedIn()` (minutos ou dias depois, já com internet) devolvia `true`.

Correção: `mapAuthError` (agora `suspend`, único ponto por onde as duas falhas passam) chama
`runCatching { auth.signOut() }` antes de devolver o erro — defensivo e idempotente, seguro
mesmo quando não havia sessão para limpar. A causa raiz exata no SDK do GitLive/Firebase ficou
fora de escopo por decisão: a defesa é correta independente da causa.

## O que ficou sem teste automatizado, e por quê

A linha do `signOut()` defensivo não tem teste. `FirebaseAuthRepository` já não tinha NENHUM
teste no arquivo inteiro antes desta fatia — toca `Firebase.auth` direto, sem seam nenhum.
Extrair uma interface só para esta linha seria refactor desproporcional ao tamanho do fix.
Registrado no plano antes de codar (é o que o C2 pede quando algo não é testável sem refactor),
não decidido em silêncio.

## O que a fatia criou, que não existia

`LoginViewModelTest.kt` — zero testes existiam para este ViewModel. Dois casos: sucesso do
Firebase entra e **nunca chama `fetchMe()`** (flag no fake, não só o estado final — um fix que
voltasse a chamar `fetchMe()` por engano, mas com sucesso, passaria pelas asserções de estado e
só essa flag denunciaria); e credencial inválida não entra e mostra o erro. Módulo
`auth/presentation` ganhou `commonTest.dependencies` no `build.gradle.kts` (não tinha nenhuma).

Verificado pelo Rafael: `:shared:features:auth:presentation:testAndroidHostTest` (2/2) e
`:app:androidApp:testDebugUnitTest` (204/204) — nenhum consumidor de `AuthRepository`/
`LoginViewModel` quebrou.
