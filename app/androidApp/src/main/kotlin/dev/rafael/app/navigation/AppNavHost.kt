package dev.rafael.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Text
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.material.icons.outlined.Notifications
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.rafael.app.data.notificacoes.ContadorDeNaoLidas
import dev.rafael.app.push.AvisosDePush
import dev.rafael.app.push.PedirPermissaoDeNotificacao
import dev.rafael.app.push.RegistroDePush
import dev.rafael.core.network.TokenProvider
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import dev.rafael.app.screens.notificacoes.NotificacoesScreen
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.rafael.app.screens.placeholder.EmBreveScreen
import dev.rafael.app.screens.achievements.AchievementsScreen
import dev.rafael.app.screens.progress.ProgressScreen
import dev.rafael.core.network.SessionExpiryBus
import dev.rafael.features.auth.domain.repository.AuthRepository
import dev.rafael.features.program.domain.repository.ProgramRepository
import org.koin.compose.koinInject
import dev.rafael.app.screens.authentication.LoginScreen
import dev.rafael.app.screens.conta.ContaScreen
import dev.rafael.app.screens.exercise.ExerciseDetailScreen
import dev.rafael.app.screens.exercise.ExerciseLibraryScreen
import dev.rafael.app.screens.grupos.EntrarScreen
import dev.rafael.app.screens.checkin.CheckInScreen
import dev.rafael.app.screens.grupos.GrupoDetalheScreen
import dev.rafael.app.screens.grupos.GrupoFormScreen
import dev.rafael.app.screens.grupos.GruposScreen
import dev.rafael.app.screens.home.HomeScreen
import dev.rafael.app.screens.menu.MenuLateral
import dev.rafael.app.screens.onboarding.NomeScreen
import dev.rafael.app.screens.onboarding.QuizScreen
import dev.rafael.app.screens.amigos.AmigosScreen
import dev.rafael.app.screens.amigos.BloqueadosScreen
import dev.rafael.app.screens.perfil.PerfilPublicoScreen
import dev.rafael.app.screens.perfil.PerfilScreen
import dev.rafael.app.screens.program.ProgramDetailScreen
import dev.rafael.app.screens.program.ProgramGenerateScreen
import dev.rafael.app.screens.program.ProgramListScreen
import dev.rafael.app.screens.paywall.PaywallScreen
import dev.rafael.app.screens.reveal.ProgramOfferScreen
import dev.rafael.app.screens.reveal.ProgramRevealScreen
import dev.rafael.app.screens.session.WorkoutSessionScreen
import dev.rafael.app.screens.splash.SplashScreen
import dev.rafael.app.screens.workout.WorkoutDetailScreen
import dev.rafael.app.screens.workout.WorkoutFormScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavHost(destinoDoPush: StateFlow<String?> = MutableStateFlow(null)) {
    val nav = rememberNavController()

    // A barra de abas só aparece nas telas-raiz. Detalhe, execução, quiz e paywall
    // ocupam a tela inteira (o usuário está numa tarefa, não navegando).
    val entry by nav.currentBackStackEntryAsState()
    val mostrarAbas = BottomTab.entries.any { tab ->
        entry?.destination?.hasRoute(tab.routeClass) == true
    }

    // A lista de programas é cache-first. Mudanças feitas FORA da feature de programa
    // (criar/editar/excluir treino, virar premium) precisam sujar esse cache — quem faz a
    // ponte é a camada do app, porque feature nunca depende de feature (Konsist).
    val programas: ProgramRepository = koinInject()

    // SESSÃO EXPIRADA (401 que sobreviveu à renovação do token). Fica aqui, e não numa tela,
    // porque o 401 pode vir de qualquer request — inclusive do SyncWorker em background. Sem
    // isto o usuário ficava preso: "Sessão expirada" + um "Tentar de novo" que só repetia o 401.
    val sessionExpiry: SessionExpiryBus = koinInject()
    val auth: AuthRepository = koinInject()

    // NOTIFICAÇÕES (F.1). O contador vive aqui, acima das telas, porque o ícone está na barra —
    // e a central, que o zera, é outra tela. Ver `ContadorDeNaoLidas`.
    val contador: ContadorDeNaoLidas = koinInject()
    val registroDePush: RegistroDePush = koinInject()
    val naoLidas by contador.quantidade.collectAsStateWithLifecycle()

    /*
     * REGISTRA O APARELHO A CADA SESSÃO, e não uma vez por composição.
     *
     * A primeira versão era `LaunchedEffect(Unit)`, que dispara UMA vez — e a bateria mostrou o
     * buraco: sair da conta e entrar de novo **sem matar o app** deixava o aparelho fora do
     * `device_tokens`. A pessoa ficava sem push até reabrir, e nada na tela dizia isso.
     *
     * O defeito era de simetria: a BAIXA é chamada explicitamente pelo `SairDaConta`, o registro
     * dependia de um evento de UI que o login não produz. Uma ponta explícita e a outra implícita
     * nunca ficam sincronizadas por muito tempo.
     *
     * `uidFlow()` foi criado no #30 para o mesmo tipo de problema (cache chaveado por uid que não
     * re-chaveava no login), e resolve os três momentos de uma vez: boot com sessão, login novo, e
     * troca de conta no mesmo aparelho.
     *
     * `filterNotNull`: logout emite `null` e não há o que registrar — a baixa já foi feita, com o
     * token do Firebase ainda válido, que é a única janela em que ela funciona.
     *
     * ## A ORDEM DOS DOIS OPERADORES É O COMPORTAMENTO
     *
     * `distinctUntilChanged()` vem **antes** do `filterNotNull()`, e isso não é estilo.
     *
     * A sequência real de uma sessão que reinicia é `"u1"` → `null` → `"u1"`. Filtrando primeiro,
     * o `null` do logout desaparece e sobra `"u1"` seguido de `"u1"` — iguais consecutivos, que o
     * `distinctUntilChanged` descarta. Resultado: **sair e entrar na MESMA conta não re-registrava
     * o aparelho**, e a pessoa ficava sem push. Entrar em outra conta funcionava, o que torna o
     * defeito ainda mais fácil de não notar.
     *
     * O `null` é a única coisa que separa as duas sessões. Comparar antes de descartá-lo preserva
     * essa fronteira.
     */
    val sessao: TokenProvider = koinInject()

    /*
     * A PERMISSÃO DE NOTIFICAÇÃO SÓ FAZ SENTIDO COM SESSÃO.
     *
     * A primeira versão chamava isto dentro da `LoginScreen`, e a bateria mostrou que estava
     * errado por dois motivos: o `LaunchedEffect` dispara quando a TELA COMPÕE, então o diálogo
     * aparecia **antes de a pessoa digitar a senha** — quando ela ainda não viu nada do app, que é
     * o momento de maior taxa de recusa, e no Android recusar é quase definitivo (o sistema ignora
     * pedidos posteriores). E quem abrisse o app com sessão restaurada pularia a `LoginScreen`
     * inteira, e **nunca** veria o pedido.
     *
     * Aqui, atrelado ao `uidFlow`, o gatilho é "existe usuário logado agora" — que é exatamente a
     * condição em que a permissão passa a valer alguma coisa. Mesmo lugar do registro do aparelho,
     * pela mesma razão: as duas coisas dependem de haver sessão, e separá-las foi o que fez uma
     * delas ficar para trás.
     */
    val uidAtual by sessao.uidFlow().collectAsStateWithLifecycle(initialValue = null)
    if (uidAtual != null) PedirPermissaoDeNotificacao()

    LaunchedEffect(Unit) {
        sessao.uidFlow().distinctUntilChanged().filterNotNull().collect {
            // O FCM reemite o token sozinho, e o `onNewToken` roda num Service sem sessão: ele
            // guarda em `TokenPendente` e é aqui que o ciclo se completa.
            registroDePush.registrar()
            contador.atualizar()
        }
    }

    // O contador acompanha a NAVEGAÇÃO em vez de fazer polling: trocar de tela-raiz é o momento
    // em que a pessoa olha para a barra, e pedido de amizade não é feed para justificar polling.
    LaunchedEffect(entry?.destination?.route) { contador.atualizar() }

    // ...e o push, que é o outro momento em que o número muda sem a pessoa fazer nada. Sem isto,
    // a notificação chega na bandeja e o badge da barra continua no número velho até a próxima
    // navegação — foi o que a bateria da F.1 pegou.
    val avisos: AvisosDePush = koinInject()
    LaunchedEffect(Unit) { avisos.eventos.collect { contador.atualizar() } }

    /*
     * DEEP LINK do push (F.1).
     *
     * Tocar na notificação leva onde se AGE sobre ela — pedido de amizade abre Amigos, e não a
     * central: a central é a lista, e quem tocou já sabe o que quer fazer.
     *
     * Consome o valor depois de navegar. Sem isso, voltar da tela reexecutaria o efeito na
     * próxima recomposição e a pessoa ficaria presa em Amigos.
     */
    val destino by destinoDoPush.collectAsStateWithLifecycle()

    /*
     * ESPERA A NAVEGAÇÃO INICIAL TERMINAR — e este gate é a fatia inteira funcionando ou não.
     *
     * Com o app MORTO, o `AppNavHost` compõe já com o destino do push na mão, navega para Amigos,
     * e logo depois o Splash decide para onde ir e chama
     * `navigate(Home) { popUpTo(Splash) { inclusive = true } }` — que apaga tudo que foi empilhado
     * sobre o Splash, inclusive a tela que o push acabou de abrir. Resultado observado na bateria:
     * tocar na notificação com o app fechado abria a **Home**.
     *
     * ## A primeira versão deste gate estava larga demais
     *
     * Ela usava `mostrarAbas` (verdadeiro só nas telas-raiz), o que parecia elegante por reusar
     * um cálculo existente. Mas tocar na notificação estando em **Conquistas** — uma tela de
     * detalhe — não fazia nada: o destino ficava pendurado esperando uma raiz que a pessoa não
     * tinha por que visitar.
     *
     * **Tocar na notificação é ação explícita e tem que levar de onde quer que a pessoa esteja.**
     * O que precisa ser bloqueado não é "não-raiz", é o punhado de telas em que navegar seria
     * desfeito (Splash) ou não faria sentido (não há sessão, ou o cadastro está em curso).
     */
    val naEntrada = entry?.destination?.let { atual ->
        atual.hasRoute(AppRoute.Splash::class) ||
            atual.hasRoute(AppRoute.Login::class) ||
            atual.hasRoute(AppRoute.Nome::class) ||
            atual.hasRoute(AppRoute.Quiz::class)
    } ?: true   // `entry` nulo é o instante anterior à primeira navegação: também é entrada

    LaunchedEffect(destino, naEntrada) {
        if (destino == null || naEntrada) return@LaunchedEffect

        when (destino) {
            "PEDIDO_DE_AMIZADE" -> nav.navigate(AppRoute.Amigos)
            // Tipo que este app não conhece — vindo de uma versão mais nova do servidor. Abre a
            // central, que sabe mostrar qualquer notificação. Melhor um destino genérico que
            // funciona do que nenhum.
            else -> nav.navigate(AppRoute.Notificacoes)
        }
        // Consome DEPOIS de navegar. Sem isto, voltar da tela reexecutaria o efeito e a pessoa
        // ficaria presa em Amigos.
        (destinoDoPush as? MutableStateFlow)?.value = null
    }
    LaunchedEffect(Unit) {
        sessionExpiry.eventos.collect {
            auth.signOut()   // limpa a sessão local e o token cacheado do Ktor
            nav.navigate(AppRoute.Login) {
                popUpTo(0) { inclusive = true }   // não dá pra "voltar" pra uma sessão morta
                launchSingleTop = true
            }
        }
    }

    // MENU LATERAL (ARCH #34). Fica AQUI, e não dentro de uma tela, por dois motivos:
    // é global — abre em qualquer tela-raiz, sem obrigar a voltar para a Home — e precisa do
    // NavController, que é desta camada.
    //
    // `gesturesEnabled` acompanha `mostrarAbas`: em execução de treino ou formulário o usuário
    // está numa TAREFA, e um arrasto lateral que abre menu no meio dela é acidente.
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val escopo = rememberCoroutineScope()
    fun navegarDoMenu(rota: AppRoute) {
        escopo.launch { drawer.close() }
        nav.navigate(rota) { launchSingleTop = true }
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = mostrarAbas,
        drawerContent = {
            MenuLateral(
                aberto = drawer.isOpen,
                onSaiu = {
                    escopo.launch { drawer.close() }
                    nav.navigate(AppRoute.Login) {
                        popUpTo(0) { inclusive = true }   // sessão encerrada não tem "voltar"
                        launchSingleTop = true
                    }
                },
                onPerfil = { navegarDoMenu(AppRoute.Perfil()) },
                onExercicios = { navegarDoMenu(AppRoute.Library) },
                onWiki = { navegarDoMenu(AppRoute.Wiki) },
                onDuvidas = { navegarDoMenu(AppRoute.Duvidas) },
                onConta = { navegarDoMenu(AppRoute.Conta) },
            )
        },
    ) {
    Scaffold(
        topBar = {
            if (mostrarAbas) {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = { escopo.launch { drawer.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Abrir menu")
                        }
                    },
                    actions = {
                        // O ícone fica na barra que JÁ existe nas quatro telas-raiz: quem está em
                        // Grupos vê o badge sem voltar para a Home.
                        IconButton(onClick = { nav.navigate(AppRoute.Notificacoes) }) {
                            BadgedBox(
                                badge = {
                                    if (naoLidas > 0) Badge { Text("$naoLidas") }
                                },
                            ) {
                                Icon(
                                    Icons.Outlined.Notifications,
                                    contentDescription = if (naoLidas > 0) {
                                        "Notificações, $naoLidas não lidas"
                                    } else {
                                        "Notificações"
                                    },
                                )
                            }
                        }
                    },
                )
            }
        },
        bottomBar = { if (mostrarAbas) FitJourneyBottomBar(nav) },
    ) { padding ->
    NavHost(
        navController = nav,
        startDestination = AppRoute.Splash,
        modifier = Modifier.padding(padding),
    ) {

        composable<AppRoute.Splash> {
            SplashScreen(
                onDecided = { dest ->
                    nav.navigate(dest) {
                        popUpTo(AppRoute.Splash) { inclusive = true }  // splash sai do back stack
                    }
                }
            )
        }

        composable<AppRoute.Login> {
            LoginScreen(onLoggedIn = {
                nav.navigate(AppRoute.Splash) {
                    popUpTo(AppRoute.Login) { inclusive = true }
                }
            })
        }

        composable<AppRoute.Nome> {
            NomeScreen(onPronto = {
                nav.navigate(AppRoute.Quiz) {
                    popUpTo(AppRoute.Nome) { inclusive = true }   // não dá pra "voltar" pro nome
                }
            })
        }

        composable<AppRoute.Quiz> {
            QuizScreen(onCompleted = {
                // Home vira a raiz e, por cima, abre a OFERTA do 1º programa (Fase 7).
                // Qualquer saída dali (gerar ou pular) desemboca na Home.
                nav.navigate(AppRoute.Home) {
                    popUpTo(AppRoute.Quiz) { inclusive = true }
                }
                nav.navigate(AppRoute.ProgramOffer)
            })
        }

        composable<AppRoute.ProgramOffer> {
            ProgramOfferScreen(
                // Sai da pilha ao gerar: o Reveal não deve poder voltar pra oferta (o
                // programa já foi criado — reperguntar "quer um programa?" não faz sentido).
                onGerar = {
                    nav.navigate(AppRoute.ProgramReveal) {
                        popUpTo(AppRoute.ProgramOffer) { inclusive = true }
                    }
                },
                onPular = { nav.popBackStack() },   // → Home, sem nada criado no servidor
            )
        }

        composable<AppRoute.ProgramReveal> {
            ProgramRevealScreen(
                onDone = { nav.popBackStack() },                     // conclui → Home (raiz do back stack)
                // voltarParaHome: recusar o premium aqui não pode devolver pro Reveal, que é
                // a própria tela de oferta — seria recusar e cair de volta na oferta.
                onOpenPaywall = { nav.navigate(AppRoute.Paywall(voltarParaHome = true)) },
            )
        }

        composable<AppRoute.Paywall> { entry ->
            val rota: AppRoute.Paywall = entry.toRoute()
            // virar premium muda o blur dos programas (#23) → cache de programas fica sujo
            PaywallScreen(onClose = { assinou ->
                // Só invalida se ASSINOU: virar premium destrava o blur (#23) e muda a lista.
                // Antes invalidava em todo fechamento, então até o "Agora não" custava um refetch.
                if (assinou) programas.invalidate()
                if (rota.voltarParaHome) {
                    nav.navigate(AppRoute.Home) { popUpTo(AppRoute.Home) { inclusive = true } }
                } else {
                    nav.popBackStack()
                }
            })
        }

        composable<AppRoute.Home> {
            HomeScreen(
                onOpenLibrary = { nav.navigate(AppRoute.Library) },
                onOpenWorkouts = { nav.navigate(AppRoute.Programs) },
                onGenerateWithAI = { nav.navigate(AppRoute.ProgramGenerate) },
                onStartWorkout = { id -> nav.navigate(AppRoute.WorkoutSession(id)) },
                onOpenGroups = { nav.navigate(AppRoute.Grupos) },
                onOpenProgress = { nav.navigate(AppRoute.Progresso) },
            )
        }

        composable<AppRoute.Library> {
            ExerciseLibraryScreen(
                onOpenExercise = { id -> nav.navigate(AppRoute.ExerciseDetail(id)) },
            )
        }
        composable<AppRoute.ExerciseDetail> { entry ->
            val route: AppRoute.ExerciseDetail = entry.toRoute()
            ExerciseDetailScreen(exerciseId = route.id, onBack = { nav.popBackStack() })
        }

        // ---- Programas (ARCH #27 — substitui a antiga AppRoute.Workout flat) ----

        composable<AppRoute.Programs> {
            ProgramListScreen(
                onOpenProgram = { id -> nav.navigate(AppRoute.ProgramDetail(id)) },
                onGenerateWithAI = { nav.navigate(AppRoute.ProgramGenerate) },
            )
        }
        composable<AppRoute.ProgramDetail> { entry ->
            val route: AppRoute.ProgramDetail = entry.toRoute()
            ProgramDetailScreen(
                programId = route.id,
                onBack = { nav.popBackStack() },
                onOpenWorkout = { id, editLocked -> nav.navigate(AppRoute.WorkoutDetail(id, editLocked)) },
                onAddWorkout = { programId, taken -> nav.navigate(AppRoute.WorkoutCreate(programId, taken)) },
                onOpenPaywall = { nav.navigate(AppRoute.Paywall()) },
                onGenerateNew = { nav.navigate(AppRoute.ProgramGenerate) },
                onCreateManual = { nav.popBackStack() },   // volta à lista, onde o "+" cria manual
            )
        }
        composable<AppRoute.ProgramGenerate> {
            ProgramGenerateScreen(
                onBack = { nav.popBackStack() },
                onGenerated = { id ->
                    // volta e abre o detalhe do programa gerado
                    nav.popBackStack()
                    nav.navigate(AppRoute.ProgramDetail(id))
                },
            )
        }

        // ---- Treino individual (dentro de um programa) ----

        composable<AppRoute.WorkoutDetail> { entry ->
            val route: AppRoute.WorkoutDetail = entry.toRoute()
            WorkoutDetailScreen(
                workoutId = route.id,
                editLocked = route.editLocked,
                // Invalida só se ALGO mudou de fato (trocou/removeu exercício, excluiu o
                // treino). Antes invalidava em toda volta, então só entrar e sair de um treino
                // já gerava um GET /programs — era o ruído que sobrava no log do servidor.
                onBack = { alterou ->
                    if (alterou) programas.invalidate()
                    nav.popBackStack()
                },
                onEdit = { nav.navigate(AppRoute.WorkoutEdit(route.id)) },
                onStartSession = { nav.navigate(AppRoute.WorkoutSession(route.id)) },
            )
        }
        composable<AppRoute.WorkoutCreate> { entry ->
            val route: AppRoute.WorkoutCreate = entry.toRoute()
            WorkoutFormScreen(
                workoutId = null,
                programId = route.programId,
                takenDays = route.takenDays,
                onBack = { nav.popBackStack() },
                // treino novo muda a contagem/agenda do programa → invalida o cache
                onSaved = { programas.invalidate(); nav.popBackStack() },
            )
        }
        composable<AppRoute.WorkoutEdit> { entry ->
            val route: AppRoute.WorkoutEdit = entry.toRoute()
            WorkoutFormScreen(
                workoutId = route.id,
                programId = null,
                onBack = { nav.popBackStack() },
                onSaved = { programas.invalidate(); nav.popBackStack() },
            )
        }
        composable<AppRoute.WorkoutSession> { entry ->
            val route: AppRoute.WorkoutSession = entry.toRoute()
            WorkoutSessionScreen(workoutId = route.id, onDone = { nav.popBackStack() })
        }

        // ---- Grupos (Fase 6, ARCH #33) ----

        composable<AppRoute.Grupos> {
            GruposScreen(
                onCriar = { nav.navigate(AppRoute.GrupoNovo) },
                onEntrarPorCodigo = { nav.navigate(AppRoute.GrupoEntrar()) },
                onAbrirGrupo = { id -> nav.navigate(AppRoute.GrupoDetalhe(id)) },
            )
        }
        composable<AppRoute.GrupoDetalhe> { entry ->
            val rota: AppRoute.GrupoDetalhe = entry.toRoute()
            GrupoDetalheScreen(
                groupId = rota.id,
                onBack = { nav.popBackStack() },
                onCheckIn = { nav.navigate(AppRoute.CheckIn(rota.id)) },
                // Ranking, posts e membros — os três levam ao mesmo lugar ([REGRA] #35).
                onAbrirPerfil = { userId -> nav.navigate(AppRoute.Perfil(userId)) },
            )
        }
        composable<AppRoute.CheckIn> { entry ->
            val rota: AppRoute.CheckIn = entry.toRoute()
            CheckInScreen(
                groupId = rota.groupId,
                onBack = { nav.popBackStack() },
                // Volta para o detalhe, onde o feed acabou de ganhar um item — e não para a lista.
                // Quem faz check-in quer ver o próprio check-in aparecer.
                onPronto = { nav.popBackStack() },
            )
        }
        composable<AppRoute.GrupoNovo> {
            GrupoFormScreen(
                onBack = { nav.popBackStack() },
                // Sai da pilha ao criar: voltar para o formulário depois do grupo criado
                // convidaria a criar o mesmo desafio duas vezes.
                onCriado = { nav.popBackStack() },
            )
        }
        composable<AppRoute.GrupoEntrar> { entry ->
            val rota: AppRoute.GrupoEntrar = entry.toRoute()
            EntrarScreen(
                inviteToken = rota.inviteToken,
                onBack = { nav.popBackStack() },
                onEntrou = { nav.popBackStack() },
            )
        }

        // ---- Abas ainda não implementadas ----
        composable<AppRoute.Progresso> {
            ProgressScreen(onOpenConquistas = { nav.navigate(AppRoute.Conquistas) })
        }
        composable<AppRoute.Conquistas> { AchievementsScreen(onBack = { nav.popBackStack() }) }

        // ---- Grafo social (#35) ----

        composable<AppRoute.Amigos> {
            AmigosScreen(
                onBack = { nav.popBackStack() },
                // Buscar por código abre o PERFIL ([REGRA] #35), nunca manda pedido direto: com
                // o perfil público, a confirmação que o ADR previa é o próprio perfil.
                onAbrirPerfil = { userId -> nav.navigate(AppRoute.Perfil(userId)) },
            )
        }

        composable<AppRoute.Bloqueados> {
            BloqueadosScreen(onBack = { nav.popBackStack() })
        }

        composable<AppRoute.Notificacoes> {
            NotificacoesScreen(
                onBack = { nav.popBackStack() },
                // Tocar numa notificação leva onde se AGE sobre ela. Pedido de amizade vai para
                // Amigos; tipo sem destino próprio não navega — melhor não fazer nada do que
                // levar a pessoa para um lugar aleatório.
                onAbrir = { n ->
                    if (n.type == "PEDIDO_DE_AMIZADE") nav.navigate(AppRoute.Amigos)
                },
            )
        }

        // ---- Perfil e conta (ARCH #34) ----

        /**
         * Uma rota, DUAS telas (C.1).
         *
         * O `userId` decide qual: `null` é o meu perfil, cache-first, com os números de treino
         * que só eu vejo; um id é o perfil de outra pessoa, online, alimentado pelo
         * `PublicProfileDto`. Telas separadas porque os TIPOS de dado são diferentes — ver o
         * KDoc de `PerfilPublicoScreen`.
         *
         * A rota continua uma só porque o destino, do ponto de vista de quem navega, é o mesmo:
         * "abrir o perfil de alguém". Quem toca no próprio nome não deveria precisar saber que
         * cai noutro lugar.
         */
        composable<AppRoute.Perfil> { entry ->
            val rota: AppRoute.Perfil = entry.toRoute()
            val id = rota.userId
            if (id == null) {
                PerfilScreen(
                    onBack = { nav.popBackStack() },
                    onEditar = { nav.navigate(AppRoute.Conta) },
                    onVerConquistas = { nav.navigate(AppRoute.Conquistas) },
                    onVerAmigos = { nav.navigate(AppRoute.Amigos) },
                    souEu = true,
                )
            } else {
                PerfilPublicoScreen(
                    userId = id,
                    onBack = { nav.popBackStack() },
                    // `popBackStack` antes de navegar: sem isso a pilha vira
                    // ranking → meu perfil público → meu perfil, e o "voltar" passa duas vezes
                    // pela mesma pessoa.
                    onVerMeuPerfil = {
                        nav.popBackStack()
                        nav.navigate(AppRoute.Perfil())
                    },
                )
            }
        }
        composable<AppRoute.Conta> {
            ContaScreen(
                onBack = { nav.popBackStack() },
                onVerBloqueados = { nav.navigate(AppRoute.Bloqueados) },
                onSaiu = {
                    // Fecha o menu junto: sair com o drawer aberto deixava um painel sem dono
                    // por cima da tela de login.
                    escopo.launch { drawer.close() }
                    nav.navigate(AppRoute.Login) {
                        popUpTo(0) { inclusive = true }   // não dá pra "voltar" pra sessão encerrada
                        launchSingleTop = true
                    }
                },
            )
        }

        composable<AppRoute.Wiki> {
            EmBreveScreen("Wiki fitness", "Conteúdo sobre treino, técnica e recuperação. Chega na Fase 8.")
        }
        composable<AppRoute.Duvidas> {
            EmBreveScreen("Dúvidas frequentes", "As perguntas mais comuns sobre o app e os treinos.")
        }
    }
    }
    }
}
