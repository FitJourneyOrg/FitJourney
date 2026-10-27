# Débitos A a D — plano

_Aberta em 2026-10-07. Rascunho: não é fonte da verdade (ver README desta pasta)._

## O que é e por que agora

Triagem dos ~77 débitos abertos de `fontes/debitos.md` em quatro grupos, feita em 2026-10-07. O grupo A (limpeza de obsoletos) já foi executado. Este arquivo é o plano de trabalho dos outros três: **B** resolve em código, **C** espera decisão do Rafael, **D** é grande e já decidido.

Não é uma fatia só. Uma fatia por sessão: cada item de B vira sua própria fatia pequena, na ordem abaixo, **só quando o Rafael mandar o comando**. Nada aqui foi iniciado.

## Perguntas que preciso responder antes de codar

- [x] `gen_erros.py` e a interpolação Kotlin: **o gerador passa a resolver as constantes de `Limites`** (decidido em 2026-10-07, ver Decisões).
- [x] Ordem do grupo B: **a ordem proposta abaixo** (decidido em 2026-10-07).
- [ ] Quais itens do grupo C o Rafael quer decidir agora (cada um vira implementar, adiar ou excluir)?

## Grupo A: limpeza de obsoletos (FEITO em 2026-10-07)

| Item | Resultado |
|---|---|
| 9 exercícios de core sem description/mídia | ✅ Pago (Fatia H: V50, V53, V54, V55). Catálogo 923/923 completo |
| `SessionApi`/`SessionSync` no módulo app | ✅ Pago. Já vivem em `shared/features/session/data` |
| IP da LAN muda | ⚪ Obsoleto, decisão do Rafael |
| `tools/` sem decisão de versionamento | Sem mudança: segue como está (decisão do Rafael) |
| F.2 `PullToRefreshBox` | Segue aberto: só existe em Grupos, Histórico, Biblioteca e Aprender; faltam Amigos e Notificações |
| 23 novos sem taxonomia | Segue aberto (parcial): a V57 só deu `primary_muscles` |

Backup do `debitos.md` antes da limpeza: `fontes/debitos.md.bak-pre-limpeza-2026-10-07`.

## Grupo B: resolvíveis em código, sem decisão pendente

Ordem proposta. Cada item: investigar no código antes de estimar, C1 se introduzir padrão novo, testes C2.

| # | Débito | Nota |
|---|---|---|
| B1 | Teste do gate da splash (sem perfil → onboarding) | **✅ FEITO em 2026-10-07: `SplashViewModelTest` 11/0, confirmado pelo Rafael e conferido no XML. Débito marcado ✅ em `debitos.md`.** 6 casos novos no `SplashViewModelTest` (Quiz = `AppRoute.Nome`): sem cadastro, onboarding incompleto, concluído sem cache, cache `true` sem esperar rede, rede caída com `false` cacheado, rede travada com teto de 5 s. Ao ficar verde: marcar o débito ✅ em `debitos.md` |
| B2 | **✅ FEITO em 2026-10-07 (wiki 6/0, app 23 suítes 0 falhas, KoinModulesVerify 2/0, Konsist 8/0, confirmado no XML).** Extrair interface do `SyncStamps` (`interface SyncStamps` com `Escopo` e chaves; `SyncStampsImpl` com SQLDelight; `AppModule` registra `single<SyncStamps>`; os ~19 consumidores não mudaram). Prova do ganho: `WikiRepositoryImplTest` (6 casos, 1 de falha) + `FakeSyncStamps`. Ao ficar verde: ✅ em `debitos.md` e registrar o padrão em `CONVENCOES.md` se o Rafael quiser | Débito novo da Fase 8. `SyncStamps` é classe concreta e exige `FitJourneyDatabase`; a interface destrava teste de unidade em todo repository impl do cliente. Maior retorno do grupo |
| B3 | `gen_erros.py` não resolve interpolação Kotlin | **✅ FEITO em 2026-10-07: o gerador resolve `${Limites.X.Y}`; 0 lacunas, 0 órfãs.** Decidido: o gerador resolve as constantes de `Limites`. "5 sem descrição" e "3 órfãs" são o mesmo problema contado dos dois lados |
| B4 | Quatro `GET /me/notifications` no boot | **Investigado em 2026-10-07 (por leitura do código, sem rodar o app).** Três origens chamam `ContadorDeNaoLidas.atualizar()`: `AppNavHost:152` (a cada troca de rota, inclusive Splash antes de haver sessão, quando o GET só pode falhar), `AppNavHost:158` (evento de push) e `AppModule:200` (`ReagirASessao`, a cada login/sessão restaurada). No boot com sessão: rota nula → Splash → Home + `ReagirASessao` = os 4. **✅ FEITO em 2026-10-07 (`ContadorDeNaoLidasTest` 8/0; app 23 suítes sem falha, conferido no XML).** single-flight no `ContadorDeNaoLidas` (chamadas sobrepostas viram 1 requisição, sem relógio) + não perguntar sem sessão. Janela de tempo descartada por ora: engoliria o push |
| B5 | Lista que recarrega debaixo do dedo | **Investigado em 2026-10-07 (leitura do código).** A condição do débito ("com os pushes de comentário e reação") JÁ ocorre: o servidor envia 8 tipos de push (`ChaveDeAviso.kt`). `AmigosScreen:105` recarrega tudo (`carregar()`, que liga `carregando` e chama `/me` + 2 listas) a CADA push, de qualquer tipo. As linhas têm `key = userId`, então só um pedido de amizade NOVO (tipo `PEDIDO_DE_AMIZADE`) desloca os botões. **Decidido pelo Rafael: filtro + aviso.** **✅ FEITO em 2026-10-07 (`AmigosViewModelTest` 9/0; app 24 suítes sem falha, conferido no XML; falta conferir o visual do aviso no aparelho).** `AmigosViewModel.aoChegarPush` (só `PEDIDO_DE_AMIZADE`; consulta só os pedidos; lista visível não muda, o que chegou vai para `aguardando`), `mostrarPedidosNovos`, aviso sobreposto no rodapé (não desloca as linhas), plural `amigos_pedidos_novos` pt/en, `AmigosViewModelTest` (9 casos, 2 de falha) |
| B6 | Voltar do deep link cai na Home | **✅ FEITO em 2026-10-07** (`pilhaDoPush` + `PilhaDoPushTest` 8/0; testado no aparelho, app morto e aberto). Navegação; conferir contra `trocarDeAba` da Fase 8 |
| B7 | Erro de refresh offline agressivo | **✅ FEITO em 2026-10-07** (build verde e aparelho OK). Wiki e Biblioteca: `ErroInline` → `ErroEmSnackbar`, `consumeError()`, 3 testes), aguardando build verde. Decisão do Rafael: snackbar sempre para mensagens rápidas. **Fora do B7:** padronizar sucesso/alerta (ver débito novo) |
| B8 | `fieldErrors` do nome de programa não alcançável | **✅ FEITO em 2026-10-07** (`ProgramServiceTest` 28/0). Mantida a UI; servidor passou a mandar código no `fieldErrors`.  |
| B9 | Read do aggregate N+1 | **⚪ ACEITO em 2026-10-08** (decisão do Rafael; pior caso ~100 consultas com os tetos 1/3; reabrir se houver lentidão).  Medir antes de mexer |
| B10 | `workouts.origin` provisionada e não lida | ✅ 2026-10-08, build verde. Removida (V62); rota completa `origin` do programa (`comOrigem`). Medido: 15 linhas, todas MANUAL |
| B11 | Models internos sem default | ✅ 2026-10-08, build verde. Servidor feito (exceção: `User.idioma`); cliente não examinado |
| B12 | `ExerciseRef` duplica `Exercise` | ✅ 2026-10-08, build verde. Aceita a estrutura (ARCH #16) + `ParidadeExercicioRefTest`. Rafael escolheu 1+2 depois de ter dito "3" (subir para o core); mantive a recomendação e ele confirmou |

## Grupo C: dependem de decisão do Rafael (guard rail: não codo sem definição)

| Débito | Decisão que falta |
|---|---|
| F.2 `PullToRefreshBox` em Amigos e Notificações | ✅ 2026-10-09 (decidido 2026-10-08), **decidido: fazer nas duas, e só nelas** (mais telas, o Rafael pede caso a caso). `atualizando` separado de `carregando`; vazio também puxa (`verticalScroll`); 6 testes de VM. Pull offline avisa por snackbar (`erroDoPull`). ✅ 2026-10-09, build verde e conferido no aparelho |
| Sem listener de conectividade | ⚪ 2026-10-08, **decidido: corrigir a expectativa**, sem código (promovido ao ARCH #31 como [REGRA]) |
| Idade como variável do motor (ARCH #24 ampliado) | ✅ 2026-10-08, **já implementado e testado**; Rafael ratificou os fatores (×1,0/×0,9/×0,75, RIR-piso 3 aos 60+). [PROPOSTA]→[REGRA] no ARCH #24 |
| Dedup funcional (stiff↔terra romeno, RDL↔stiff halteres) | ✅ 2026-10-08, **decidido: unir, Stiff fica** (V63), build verde e conferido no banco. Sobra: 3 `Stiff Unilateral` sem padrão |
| Autorregulação, Tier 3 `[PROPOSTA]` (RIR/RPE, fadiga, progressão) | ⚪ 2026-10-08, **decidido: não agora, reabrir com dado real** (gatilho e primeiro passo registrados no débito) |
| Exclusão granular de peso/altura | ⚪ 2026-10-08, **fundido com HealthGate**: um item, tela de perfil de treino na Fase 10 |
| HealthGate sem tela de ação | ⚪ 2026-10-08, **fundido com o anterior**; texto do diálogo corrigido agora (pt/en), ação real na Fase 10; `testDebugUnitTest` verde em 2026-10-09 |
| Logging de plano servido e engajamento | ⚪ 2026-10-08, **decidido: adiar até o LLM ao vivo** |
| Sem teste de observabilidade | ✅ 2026-10-09 porta `Falhas` + fake, só onde se engole exceção; `:server:test` verde |
| Link de convite ponta a ponta | ⚪ 2026-10-09, **decidido: adiar até haver domínio** (gatilho: domínio https definido + `assetlinks.json` hospedado; o código por ACTION_SEND cobre o uso hoje) |
| Banner padrão no servidor de produção | ⚪ 2026-10-09, **decidido: item do checklist de primeiro deploy** (copiar `gifs_exercicios/banners/default.jpg`, junto com a credencial Firebase por env) |
| Ficha da Play Store e preço em pt-BR | ⚪ 2026-10-09, **decidido: manter na Fase 10** (gatilho: preparar a publicação na Play Console) |

## Grupo D: grandes, decisão já tomada

| Débito | Observação |
|---|---|
| Play Billing real no lugar do DevBilling | |
| Credencial Firebase via env em CI/prod | ⚪ 2026-10-09, **adiado até escolher o provedor de deploy**; bloqueador só no dia do deploy, vai ao checklist |
| Login exige o backend de pé | Aparece como "app não abre" em incidente de backend |
| Vocabulário de músculo incompleto | `MuscleGroup` ainda tem 9 valores (falta CALVES, TRAPEZIUS, LOWER_BACK). Volume desses grupos fica invisível ao motor |
| Home é placeholder | Parte já mudou na Fase 8 (programa ativo); reavaliar o que falta do hub |
| ~~23 novos sem taxonomia (resto)~~ | **Saiu daqui em 2026-10-23** — decisão do Rafael: entra no plano do motor v3, trava a Fatia 3 (alocação). Ver `trabalho/motor-v3-desenho-e-fatias.md` e `fontes/debitos.md` |

## Decisões tomadas

- **2026-10-08 · Tetos de programa (decisão do Rafael, fora da lista A–D, surgiu no B9):** grátis 2 no total (qualquer tipo, substitui 1 IA + 2 manuais), premium 4 (era 10). Quem já tem mais mantém e só não cria. Sem usuários reais, então sem compat de código antigo: `LIMITE_DE_IA_GRATIS`/`LIMITE_DE_MANUAIS_GRATIS` viraram `LIMITE_DE_PROGRAMAS_GRATIS`; `ProgramLimits.Kind` removido. Implementado em 2026-10-08; build verde (`ProgramLimitsTest` 8/0, `TextosDeErroTest` 11/0, server 49 suítes, app 25, konsist, 0 falhas). Falta só conferir no aparelho. Efeito no B9: pior caso do N+1 cai de ~330 para ~130 consultas; B9 segue em pausa.

- **`tools/` fica como está** (2026-10-07). Alternativa descartada: registrar "não versionar" e mover para P6.
- **IP da LAN é obsoleto** (2026-10-07).
- **Nada do grupo B começa sem comando do Rafael** (2026-10-07).
- **B3: o `gen_erros.py` resolve as constantes de `Limites`** (2026-10-07, "seguir a mais consistente"). Motivo: a constante continua sendo a única fonte do número, então a descrição não quebra quando ela é renomeada ou muda de valor. Alternativa descartada: indexar as descrições pelo literal com a interpolação; mais barata, mas é um segundo lugar que precisa lembrar do nome da constante.
- **Ordem do grupo B: a proposta, B1 a B12** (2026-10-07, "seguir a mais recomendada").

## Desenho / Migration / Testes

Por item, quando cada fatia abrir. Regra C2 vale em cada uma: kotlin.test, fakes à mão, ao menos um caminho de falha. Comandos de build são rodados pelo Rafael.

## Fora de escopo, por decisão

Grupos C e D inteiros nesta rodada, até o Rafael decidir.

## Ao fechar cada item, promover antes de apagar

- [ ] `[REGRA]`/`[INV]` → `docs/regras/<área>.md`
- [ ] decisão de arquitetura → `docs/arch/`
- [ ] fora de escopo → `docs/fontes/debitos.md` (e marcar o débito fechado nele)
- [ ] narrativa e defeitos achados → `docs/historico/<fatia>.md`
- [ ] migration → `docs/migrations/INDICE.md`
- [ ] `ESTADO.md` atualizado, `gen_tudo.py` rodado
- [ ] **este arquivo apagado, só quando A a D estiverem resolvidos ou decididos, com ok do Rafael**

- **2026-10-08 · Criação manual acima do teto ficava "pendente" (achado do Rafael no teste do aparelho):** o `createManual` é otimista e o 403 do servidor só chegava pela fila. Opção 1 aplicada: `TetoDeProgramas` (cópia otimista, números de `Limites`) recusa ANTES de gravar, com snackbar de motivo (`ErroEmSnackbar` mostra o texto nos 403). Plano via `PlanoDoUsuario` (domínio) implementado por `PlanoDoUsuarioDoMe` (app); cache "grátis" no teto confirma no `/me` antes de recusar; plano desconhecido não bloqueia. Fantasmas com falha permanente não contam. Aguardando build.
- **Débito novo (opção 2, recomendada pelo Claude, não implementada):** recusa ASSÍNCRONA do outbox sem aviso. Falha permanente só guarda a mensagem em PT do servidor (sem código) e aparece no diálogo de descartar; avisar via snackbar no idioma certo exige guardar o código na tabela `outbox` (migration local). Registrar em `debitos.md` se o Rafael quiser seguir.

- **2026-10-08 (tarde) · Tetos revistos pelo Rafael: grátis 1, premium 3** (eram 2/4 horas antes, e 1 IA + 2 manuais / 10 antes disso). Só as constantes de `Limites` mudaram; frase passou a "Limite de programas do plano grátis: N" para não depender de plural ("até 1 programas"). Testes ajustados (`ProgramLimitsTest`, `TetoDeProgramasTest`). Aguardando build. Efeito no B9: pior caso do N+1 cai para ~100 consultas (3 programas), o grátis com 1 programa faz ~35.

- **2026-10-08 · Excedente após o premium expirar:** registrado em `debitos.md` como decisão de produto em aberto (A somente leitura / B trancar / C só documentar). Critério já decidido: ativo e mais recentes, calculado na leitura. Não codar até o Rafael escolher. Lembrete de fechamento: o ARCH #27 ainda cita os tetos antigos (1 IA + 2 manuais, premium 10); atualizar para grátis 1 / premium 3.
