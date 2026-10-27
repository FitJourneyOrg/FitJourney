# Contrato da API

> Fonte única: `shared-contract` (código). Atualizado em **2026-10-07** (migrations até **V61**; fatia J fechada — análise de progressão, filtro por programa, janela de calendário, seleção de exercícios e detalhe por exercício [J.5]). Auth via Firebase (header `Authorization: Bearer <idToken>`); o backend (Ktor) autoriza.

## Endpoints

Todos exigem auth, exceto `/health`.

| Método | Rota | O que faz |
|---|---|---|
| GET | `/health` | Ping (público). |
| GET | `/me` | Usuário atual (uid, email, is_premium, displayName, code, **locale**). |
| PATCH | `/me` | Edita nome (V35, #33/#34) e **idioma** (V47, #37). **Os dois campos são anuláveis: `null` = não mexer**, que é o que um PATCH significa; corpo com os dois nulos é requisição válida que não faz nada. Devolve o `UserDto` inteiro, não só o campo mexido: o cliente cacheia `/me` e a resposta substitui a cópia local sem 2ª requisição. **Valida os dois ANTES de escrever** — com dois campos aparece "nome válido e idioma inválido", e requisição que falha não pode ter mudado metade. `locale` fora do vocabulário → **400** `VALIDATION` com `fieldErrors["locale"]`; não é fallback silencioso. Sem gate de premium: nome é identidade e idioma é acessibilidade. |
| GET | `/users/{id}/profile` | **Perfil PÚBLICO de terceiro (C.1, #34 + emenda 9.3-A).** Nome, nível, XP e conquistas **desbloqueadas** — e nada além. Autenticada: a 9.3-A abriu o perfil a qualquer usuário AUTENTICADO, não à internet. **404 para id inexistente E malformado**, nunca 403 — "sem permissão" confirmaria a existência da conta. **Não concede conquista** (diferente de `/me/achievements`): ler perfil alheio é caminho de leitura. |
| POST | `/me/subscribe` | Ativa premium (DevBilling; Play Billing pluga atrás da interface). |
| GET | `/me/profile` | Perfil do onboarding. 404 se ainda não fez. |
| PUT | `/me/profile` | Cria/atualiza o perfil (onboarding). Validação autoritativa no server. |
| GET | `/me/stats` | XP, nível, streak — DERIVADOS das sessões (#16). Só leitura: o cliente nunca envia XP. |
| GET | `/me/achievements` | Catálogo COMPLETO de conquistas (#32): desbloqueadas com data, bloqueadas com progresso. **A leitura também AVALIA e concede** o que faltar — é o que dá retroativo sem backfill. Idempotente. |
| GET | `/me/progress` | **Análise de progressão (fatia J).** Tonelagem, carga por semana, 1RM estimado por exercício, séries por grupo muscular e a lista de todos os exercícios do recorte — tudo derivado das sessões (#16). `?locale=` porque o nome do exercício volta traduzido. **Query:** `?programId=<uuid\|avulsos>` (recorte) · `?de=&ate=` (faixa de semanas DO programa) · `?semanas=8\|26\|52` (janela de calendário) · `?exercicios=<uuid,uuid,uuid>` (quais ganham linha; ausente = os três de maior volume). ⚠️ **Responde 200 SEMPRE, com os blocos pagos NULOS para quem é free — não 403.** O portão aqui não barra uma AÇÃO: a tela mistura blocos grátis e pagos, e um erro que não é erro custaria uma segunda chamada e um estado de falha falso. Por isso **a análise não tem código em `PORTOES_DE_PLANO`**; quem diz "é pago" é o campo `analysisLocked`. **⚠️ Uma ÚNICA entrada vira 400: `programId` malformado** (`ID_DE_PROGRAMA_INVALIDO`) — ele muda QUAL dado responde. Janela fora da lista, faixa fora do teto e id de exercício inválido **encaixam em silêncio**, porque são ajuste de VISUALIZAÇÃO e recusar a requisição inteira deixaria a tela sem gráfico. |
| GET | `/me/progress/exercicio` | **Detalhe de UM exercício dentro de UM programa (fatia J.5).** Série a série, sessão a sessão — o "zoom" que o `/me/progress` não oferece; é o que a tela "ver detalhado" abre a partir da lista de exercícios do recorte. `?locale=` (nome traduzido). **Query, as DUAS obrigatórias:** `?programId=<uuid>` · `?exercicioId=<uuid>` — aqui `programId` **não aceita `avulsos`**, é sempre um programa real. Ausente ou malformado → **400**, cada um com código próprio (`ID_DE_PROGRAMA_INVALIDO` / `ID_DE_EXERCICIO_INVALIDO`): ao contrário do `/me/progress`, aqui os DOIS ids mudam QUAL dado responde, não há "ajuste de visualização" pra encaixar em silêncio. ⚠️ **Gate por 403 `ENTITLEMENT_REQUIRED`, não por campo nulo** — padrão diferente do `/me/progress`: aqui não há bloco grátis pra misturar numa resposta só, a tela inteira é profundidade paga. **Não herda `de`/`ate` do `/me/progress`** — devolve o PROGRAMA INTEIRO, sem faixa de semanas: um terceiro conceito de janela, deliberado, pra não competir com os outros dois (janela de calendário, faixa de programa). |
| GET | `/exercises` | Catálogo de exercícios. |
| GET | `/exercises/{id}/alternatives` | Alternativas slot-safe (mesmo padrão/músculo/composto). |
| POST | `/programs/generate` | Gera programa por IA. Gates: saúde (PAR-Q), idade (#24), teto por plano. Aplica blur (#23). |
| GET | `/programs` | Lista programas do usuário (com blur por entitlement). |
| POST | `/programs` | Cria programa manual (shell vazio p/ treino avulso). Aceita `id` do cliente (idempotência do outbox, #30). |
| PUT | `/programs/{id}` | Renomeia (gate premium se origin=AI). |
| PUT | `/programs/{id}/schedule` | Define o dia (1..7) de cada treino. |
| DELETE | `/programs/{id}` | Exclui o programa (CASCADE nos treinos). |
| POST | `/workouts` | Cria treino num programa (escolhe o dia). Aceita `id` do cliente: reenvio devolve o existente em vez de duplicar (#30). Id de OUTRO usuário → 409. |
| GET | `/workouts` · `/workouts/{id}` | Lista / detalhe. |
| PUT | `/workouts/{id}` | Edita treino (gate premium se IA). |
| DELETE | `/workouts/{id}` | Exclui treino. |
| POST | `/sessions` | Registra uma sessão executada. Idempotente por id (gerado no cliente). |
| GET | `/sessions` | Histórico de sessões. |

### Fase 6 — grupos e check-in (#33)

| Método | Rota | O que faz |
|---|---|---|
| POST | `/groups` | Cria o desafio. Início ≥ amanhã (senão a janela de entrada tem duração zero); fuso em IANA; criador entra como `ADMIN` e conta no teto de 50. |
| GET | `/groups` | Meus grupos. Traz `myCheckInToday` — resolvido no servidor, com o fuso do GRUPO. Papéis lidos em lote (`rolesOf`): o N+1 aqui foi achado lendo o log com o seed. |
| GET | `/groups/{id}` | Detalhe. **Estado é DERIVADO** de datas + fuso; não existe coluna `status` nem job de virada. |
| GET | `/groups/preview` | Preview por código — a **única** leitura da fase que responde a quem não é membro. É onde mora o opt-in do #17. |
| POST | `/groups/join` · `/invites/{token}/join` | Entrar por código (não expira, fica no grupo) ou por link (vence no menor entre 7 dias e o início). |
| GET | `/groups/{id}/members` | Participantes com papel. |
| POST | `/groups/{id}/leave` | Sair. Admin transfere antes (409 com a frase pronta); **admin sozinho apaga o desafio** (regra 2.5-A). |
| DELETE | `/groups/{id}/members/{userId}` | Expulsar (admin). |
| POST | `/groups/{id}/admin/{userId}` | Transferir o cargo. |
| POST · DELETE | `/groups/{id}/invite` | Gerar / revogar link. **Um ativo por grupo**: gerar revoga o anterior na mesma transação. |
| POST | `/groups/{id}/checkins` | Check-in (**multipart**, não JSON+base64). Nasce **VÁLIDO** (#33). Um por dia garantido por UNIQUE `(group_id, user_id, local_date)`. Foto re-encodada no servidor; coordenadas arredondadas a 2 casas na ESCRITA; local é opt-in e exige nome + coordenadas juntos. |
| GET | `/groups/{id}/checkins` | Feed, mais recente primeiro, cursor por `createdAt`. `mine` e `canDelete` vêm resolvidos — a tela não recalcula data nem compara ids. |
| DELETE | `/groups/{id}/checkins/{checkInId}` | Apaga o meu, só hoje. Libera o slot do dia. |
| GET | `/groups/{id}/ranking` | **Contagem de check-ins, nunca XP** ([REGRA] #18). Posição atribuída sobre a ordem do banco e não persistida. Desempate: `total DESC`, `ultimo ASC NULLS LAST`, `joined_at`, `user_id`. Sem paginação — o teto de 50 membros é o teto da lista. |
| GET | `/checkins/{id}/foto` | A foto. **Autenticada** e na base da API, não na base de mídia dos exercícios — foi o que exigiu um `ImageLoader` do Coil com o cliente Ktor do app.

### Fase 6 — perfil público e amizades (#34 C.1, #35)

| Método | Rota | O que faz |
|---|---|---|
| GET | `/users/{id}/profile` | Perfil de terceiro (9.3-A). **Autenticada** — sem isso seria raspador de perfis sem custo. **404 para id inexistente E malformado, nunca 403**: "sem permissão" confirmaria a existência da conta. Traz `available` (falso para quem me bloqueou ou conta apagada) e `friendStatus`. |
| GET | `/users/by-code/{code}` | Resgate por código de 8 caracteres. Abre o **perfil**, nunca manda pedido — erro de digitação viraria pedido a um desconhecido. Limite de **10 tentativas/hora**; punir por IP puniria o usuário de dedo grosso. 404 com frase do servidor: "Nenhum usuário com esse código." |
| POST | `/me/code/regenerate` | Gera código novo. O antigo morre na hora — é a defesa de quem está sendo importunado, em vez de depender de nós detectarmos o abuso. |
| GET | `/friends` | Meus amigos, alfabético. |
| GET | `/friends/requests` | Pedidos que EU recebi. **Só os recebidos** — os enviados aparecem no perfil da pessoa; caixa de saída é tela que ninguém abre duas vezes e mais um lugar onde o contador diverge. |
| POST | `/friends/{id}` | Manda o pedido. **Pedido cruzado vira amizade na hora**: os dois manifestaram a mesma intenção, e isso também resolve a corrida de dois toques simultâneos sem erro. Teto de 500. |
| POST | `/friends/{id}/accept` · `/decline` | Responde. `WHERE status = PENDENTE` no update: responder relação já decidida não muda nada. |
| DELETE | `/friends/{id}` | Cancela o pedido que mandei **ou** desfaz a amizade. Uma rota porque, para quem toca, é o mesmo gesto; **quem decide é o ESTADO**, no serviço. *(A primeira versão tentava `cancelar` e, no erro, `desfazer` — **erro não é canal de decisão**.)* |
| GET | `/blocks` | Lista de bloqueados (Configurações → Conta → Bloqueados). |
| POST · DELETE | `/blocks/{id}` | Bloquear / desbloquear. Bloquear **apaga a amizade na mesma transação**; quem foi bloqueado vê perfil indisponível, e desbloquear devolve o perfil. |

### Fase 6 — social: comentários e reações (E.1, decisões 8.1 a 8.4)

| Método | Rota | O que faz |
|---|---|---|
| GET | `/groups/{id}/checkins/{checkInId}/comments` | A conversa, do mais **antigo** para o mais novo — ao contrário do feed: conversa se lê na ordem em que aconteceu. |
| POST | `/groups/{id}/checkins/{checkInId}/comments` | Comenta (máx. 500). **Não existe `PUT`**, e a ausência é a regra (8.1): comentário não se edita, senão o que 49 pessoas leram deixaria de ser o que está escrito. |
| DELETE | `/groups/{id}/comments/{commentId}` | Apaga. **Autor, dono do check-in ou admin** (emenda de 07/09 — é a foto dele). Sem prazo, ao contrário do check-in: comentário não vale ponto, então apagar não move o ranking. |
| POST | `/groups/{id}/checkins/{checkInId}/reactions` | Põe ou **troca** a reação (8.2). `POST` e não `PUT`: a operação é "reagir", e quem chama não precisa saber se já existia uma. |
| DELETE | `/groups/{id}/checkins/{checkInId}/reactions` | Tira a minha. **Sem corpo** — só existe uma minha, não há o que identificar. Idempotente. |

**Duas guardas em toda operação:** (1) sou membro do grupo? senão **404, nunca 403**; (2) o check-in é DESTE grupo? Sem a segunda, conhecer um `Uuid` de outro grupo daria acesso a comentar nele — e **segurança que depende de o atacante não descobrir um identificador é segurança emprestada**.

### Fase 6 — denúncia e moderação (E.2, ARCH #33 seção 6)

| Método | Rota | O que faz |
|---|---|---|
| POST | `/groups/{id}/checkins/{checkInId}/reports` | Denuncia um check-in (6.1). **Motivo obrigatório**, até 300. Prazo de **7 dias** (6.9). Não dá para denunciar o próprio: quem quer desfazer tem a 4.11, e um segundo caminho para o mesmo efeito é como uma regra de prazo deixa de valer. A **primeira** denúncia move `VALIDO → EM_ANALISE`; as seguintes só engrossam o contador. |
| POST | `/groups/{id}/comments/{commentId}/reports` | Denuncia um comentário (6.4). Mesmo prazo, contado **do comentário**. |
| GET | `/groups/{id}/reports` | A fila do admin (6.2), casos abertos **mais antigos primeiro** — fila se atende por chegada, ao contrário do feed. Conteúdo já **hidratado**, para a tela não fazer uma busca por item. **Membro comum leva 403, não 404**: única exceção da fase — ele está dentro do grupo e olhando para ele. |
| GET | `/groups/{id}/reports/count` | Só o número, para o badge. Rota separada porque baixar a fila hidratada para exibir um "3" seria pagar a página por um inteiro. |
| POST | `/groups/{id}/reports/{targetId}/judgment` | Julga. O id é o do **ALVO**, não o da denúncia — é o que faz várias denúncias do mesmo conteúdo fecharem juntas (6.11). `acatar=true` invalida o check-in ou **remove** o comentário. |
| POST | `/groups/{id}/checkins/{checkInId}/invalidate` | 6.10: o admin invalida direto, sem denúncia prévia. Rota própria porque não há caso a julgar — passar pelo `judgment` exigiria fabricar uma denúncia falsa. A auditoria gravada é a mesma. |

**Nenhum `DELETE` nesta seção, e a ausência é o invariante.** Julgar é `POST` mesmo quando o efeito é remover um comentário: a decisão do admin não apaga o pedido, ela **acrescenta um registro** (6.6). O verbo acompanha a semântica de negócio, não o efeito colateral.

### Fase 6 — notificações e push (F.1, ARCH #36)

| Método | Rota | O que faz |
|---|---|---|
| GET | `/me/notifications` | A central, mais recente primeiro. Teto de **100, sem paginação**: quem rola 100 para trás procura algo específico, e a central é a ferramenta errada para isso. |
| POST | `/me/notifications/read` | Marca todas como lidas. **Abrir a central é o gesto de "vi tudo isto"** — ninguém marca uma a uma. `WHERE read_at IS NULL` no update, senão "quando li isto" viraria "quando abri a tela pela última vez". |
| POST | `/me/devices` | Registra o token FCM deste aparelho. `ON CONFLICT (token) DO UPDATE SET user_id` — o token é identidade de **instalação**, e o mesmo aparelho serve a várias contas. |
| DELETE | `/me/devices/{token}` | Baixa no logout, **antes** do `signOut` (o token do Firebase precisa estar válido). Sem isso, quem sai continua recebendo notificação no aparelho. |

## Gates de geração (`/programs/generate`)

Ordem: 1) **Saúde** (`HealthScreening.gateSatisfied` — sem risco OU risco reconhecido; senão Forbidden `HEALTH_GATE_REQUIRED`) → 2) **Idade (#24)** (`ageGateSatisfied` — age ≥ 18 OU minorSupervised; senão Forbidden `AGE_GATE_REQUIRED`) → 3) **Teto por plano** (grátis: 1 IA + 2 manuais; premium: 10 — senão Forbidden `ENTITLEMENT_REQUIRED`).

## DTOs principais

- **UserDto.locale** (V47, #37) — a tag do idioma, como `pt-BR`. **`String` e não enum, de propósito**, pela mesma razão já escrita no `ErrorResponse` sobre o `code`: idioma novo no servidor não pode quebrar cliente antigo, e um enum serializado faria a desserialização do `/me` **INTEIRO** falhar, tirando a tela de conta de quem nem escolheu aquele idioma. Default `pt-BR` no DTO, pelo mesmo motivo. **Não é o idioma da INTERFACE**: a UI segue a locale ativa do aparelho, e este campo é o que o servidor usa para montar o push. As duas podem divergir por um instante enquanto o `PATCH` está em voo, e isso é a cópia otimista de sempre.
- **UpdateMeRequest** (#37) — `displayName: String?` e `locale: String?`, **os dois anuláveis**. O KDoc da versão de um campo só já previa: *"quando houver mais coisa editável, cada campo vira anulável com null = não mexer"*. O `displayName` deixou de ser obrigatório porque a tela que só troca o idioma não deveria reenviar o nome atual, e reenviar o nome atual é a forma mais comum de sobrescrever sem querer o que outra tela acabou de mudar.
- **GroupDto.emojiDeHoje** (D) — o emoji que ESTE grupo tem de reproduzir hoje, ou `null` se não exige a regra. **Derivado no servidor** (fuso do grupo + relógio do servidor), como `state` e `myCheckInToday`. Existe para a tela mostrar a exigência **antes** da foto — depois seria oferecer e desmentir.
- **CheckInDto.emoji** (D) — o emoji que valia **naquele** dia, vindo do banco. Nunca recalculado: a lista curada cresce.
- **PersonDto** (#35) — userId, displayName, code?. · **FriendRequestDto** — userId, displayName, requestedAt.
- **NotificacaoDto** (F.1) — id, type, title, body, data (Map<String,String>), readAt?, createdAt. **`title` e `body` vêm JÁ RENDERIZADOS do servidor** — o cliente não monta frase, senão a notificação de ontem passaria a dizer o nome novo de quem trocou de nome hoje. `data` carrega `tipo` e, conforme o tipo, `fromUserId`, **`groupId`** e **`checkInId`** (fatia F) para o deep link.
- **`data` do push — o que cada tipo carrega (F).** Os ids viajam porque o destino precisa deles e buscá-los depois seria uma requisição a partir de uma tela que ainda não abriu. **Nenhum é obrigatório no cliente**: um tipo que chegue sem o `groupId` cai na central em vez de abrir tela vazia — `Map<String,String>` não tem contrato de compilação, e cair no genérico é sempre melhor que abrir um grupo que não existe.
- **RegistrarDispositivoRequest** (F.1) — token.
- **PublicProfileDto** (C.1) — userId, displayName, level, xp, achievements[], me, **available** (#35), **friendStatus** (#35). **DTO próprio, não o `UserStatsDto`**: aquele traz `totalSessions`, `sessionsThisWeek`, `streakDays` e `trainedToday`, que são histórico de treino e a 9.3-A manteve privados. Campo que **não existe** no DTO não vaza por descuido.
- **PublicAchievementDto** — id, unlockedAt (nunca nulo: bloqueada não entra na lista). **Sem `current`/`target`** — progresso é histórico de treino; no perfil público existe a medalha, não o caminho até ela. **Sem `title`/`description` desde a G.5**: o texto é do cliente, e esta tela é a que mais precisava disso — é onde uma pessoa lê a medalha de OUTRA, possivelmente em outro idioma.
- **CheckInDto** — id, groupId, userId, displayName, createdAt, localDate, **status**, photoUrl? (relativo à base da API), placeName?, emoji?, **commentCount**, **reactions[]**, mine, canDelete, **canReport**. Os quatro últimos **resolvidos no servidor**: a tela não compara ids, não consulta papel e não recalcula data. `canReport` **não** consulta se EU já denunciei — seria uma quarta consulta em lote no feed para evitar uma mensagem barata; o custo é o servidor responder "você já denunciou este check-in".
- **CommentDto** (E.1) — id, checkInId, userId, displayName, body, createdAt, canDelete, **canReport**. **Não existe campo de edição**, e a ausência é a regra: um `editedAt` convidaria alguém a implementar a edição. `canDelete` e `canReport` podem ser verdadeiros ao mesmo tempo e não é contradição — quem pode apagar resolve na hora, quem não pode encaminha ao admin.
- **ReactionSummaryDto** (E.1) — emoji, count, mine. **Agrupado pelo servidor**: mandar as reações cruas faria cada aparelho baixar até 50 linhas por card para exibir "12 👍", e o feed já é a tela mais pesada do app.
- **ReportItemDto** (E.2) — targetId, target, count, reasons[], firstReportedAt, checkIn?, comment?. **Não existe campo de denunciante** — o banco guarda `reporter_id` (sustenta "uma por pessoa" e a auditoria), mas o nome não atravessa esta fronteira. `reasons` é **lista** e não texto concatenado: três pessoas dizendo a mesma coisa é informação diferente de uma dizendo três. Exatamente um entre `checkIn` e `comment` vem preenchido, espelhando o arco exclusivo da V45.
- **DenunciarRequest** (E.2) — reason (**não-anulável**, 1..300). Sem valor padrão de propósito: escrever é o atrito que separa a denúncia pensada do toque irritado, e o tipo faz o compilador cobrar. · **JulgarRequest** — acatar (booleano; quem traduz para o efeito certo é o servidor, que sabe o que está julgando). · **ModerationBadgeDto** — pending.
- **RankingEntryDto** — position, userId, displayName, checkIns, mine.
- **ProfileDto** — goal, level, daysPerWeek (2..6), splitPreference?, unavailableDays[], focusAreas[] (0-2), weightKg?, heightCm?, age?, minorSupervised, environment?, health?, limitations[], onboardingCompleted.
- **ProgramDto** — id?, name, origin, workouts[], daysPerWeek, split, rationale, locked, schedule[], durationWeeks (mín 8), startedAt?, currentWeek, createdAt?, updatedAt?. ⚠️ **`name` vazio significa "ninguém escolheu, derive"** (G.5, V48): programa gerado nasce sem nome e a tela monta o rótulo de `daysPerWeek` + `split` no idioma dela. Preenchido = o usuário renomeou (ARCH #27), e aí é dado dele. Até 2026-09-15 o servidor gravava `"Programa 4x — Push/Pull/Legs"`, em português e com travessão.
- **WorkoutDto** — id?, name, origin, programId?, exercises[], dayOfWeek?, lockedExerciseCount (blur #23).
- **WorkoutExerciseDto** — exerciseId, orderIndex, restSeconds, rir, sets[]. · **WorkoutSetDto** — reps, orderIndex.
- **WorkoutSessionDto** — id, programId?, workoutId?, workoutName, startedAt, finishedAt, sets[].
- **SetLogDto** — exerciseId, orderIndex, setIndex, targetReps, repsDone, weightKg?, done.
- **ExerciseDto** — id, name, category, description?, videoRef, thumbRef.
- **UserStatsDto** — xp, level, xpInLevel, xpForNextLevel, streakDays, totalSessions, sessionsThisWeek, trainedToday, xpToday, **totalKg** (J.2). Tudo derivado; não há campo que o cliente possa enviar. O `totalKg` vive AQUI, e não só no `ProgressDto`, porque é por esta porta (`ProgressoDeStats`) que o `AchievementService` lê o progresso — a conquista de carga precisa do número, e calculá-lo num segundo lugar seria a divergência que o KDoc daquele serviço já proíbe. As duas pontas chamam `ProgressPolicy.cargaDeSerieCrua`.
- **ProgressDto** (fatia J) — totalKg, totalSessions, sinceDate?, lastVsPrevious?, **weeklyLoad?**, **strengthTrend?**, **setsByMuscle?**, **exerciseSummary?**, **analysisLocked**, availablePrograms[], hasUnassigned, programWeeks?, fromWeek?, toWeek?, weeksWindow?. ⚠️ **O que é pago mudou na J.4.1: a PROFUNDIDADE, não o gráfico.** `weeklyLoad` saiu do portão — era 100% pago, o que significava que ninguém free via oito semanas, e por isso "janela longa" não vendia nada. Pagos hoje: `strengthTrend`, `setsByMuscle` e `exerciseSummary`. ⚠️ **[INV] Resposta de free não carrega o número pago em campo nenhum** — é o buraco que o `ProgramAccess` nasceu para fechar (a listagem trancava, e o `GET /workouts/{id}` entregava). Garantido por construção: para free eles nem são CALCULADOS. **`analysisLocked` existe porque nulo tem duas causas** — "você é free" e "você ainda não treinou com carga" —, e sem distingui-las um premium recém-chegado veria o paywall do que acabou de pagar.
- **WeeklyLoadDto** — weekStart (segunda-feira, ISO), kg, **weekNumber?** (semana DO programa, 1-based). A lista tem exatamente o tamanho da janela — `semanas` no calendário, `ate - de + 1` no programa — e **semana sem treino vem com `0.0` em vez de sumir**, porque o buraco é informação e omiti-lo encostaria duas semanas que não são vizinhas. O `weekNumber` vem só no recorte de programa, e é o que deixa o eixo dizer "Semana 10" em vez de uma data: programas COEXISTEM (#27), então a mesma data é semana 3 de um e 11 de outro. **A data da segunda viaja junto mesmo assim** — o toque na barra mostra "semana 5" *e* quando ela foi.
- **ExerciseSummaryDto** (J.4.2) — exerciseId, name, current1rm, changePercent, sets, weeks. **Todos** os exercícios do recorte, em ordem de tonelagem — não só os três desenhados. ⚠️ **`current1rm` é da ÚLTIMA semana em que o exercício apareceu, não a média do período**: a pergunta é "onde estou hoje", e média de dez semanas responde outra — e responde para baixo justamente para quem progride. A ordem é a MESMA do gráfico, então a cabeça da lista é o que está desenhado. **Por que lista em vez de mais linhas:** as três linhas dividem a mesma escala, e é isso que quebra com mais — leg press a 200 kg e rosca a 20 kg no mesmo eixo achatam a rosca numa reta, e ela pode ter subido 30%.
- **ExercicioDetalheDto** (J.5) — exerciseId, name, points: List<PontoDeSessaoDto>. O drill-down de dentro de `ExerciseSummaryDto`: mesmo exercício, granularidade de SESSÃO em vez de semana. **Programa inteiro, sem `de`/`ate`** — ver a linha de `/me/progress/exercicio` acima. · **PontoDeSessaoDto** — date, weekNumber, estimated1rm, **volumeKg**, kg, reps, sets. Um ponto = UMA sessão, não uma semana. `estimated1rm` e `volumeKg` são a MESMA sessão por dois ângulos (intensidade máxima vs. quantidade de trabalho) e **podem divergir de propósito** — força subindo com volume caindo é treino de pico, não erro. `kg`/`reps`/`sets` são só para a legenda ao tocar no ponto; o eixo nunca é peso bruto, pela mesma razão do `TrendPointDto`.
- **ExerciseTrendDto** — exerciseId, **name** (já traduzido: é nome de catálogo, não enum, e o cliente não tem como resolver por id), points[], changePercent. · **TrendPointDto** — weekStart, estimated1rm (Epley, `w × (1 + reps/30)`), **weekNumber?**. **Máximo 3 séries**, cortado no SERVIDOR também — cliente não é autoridade (#16). Semana sem o exercício é PULADA, nunca zerada: zero diria que a pessoa enfraqueceu quando ela só treinou outra coisa.
- **MuscleVolumeDto** — byMuscle (Map<MuscleGroup, Double>), **unclassified**. Média semanal de **SÉRIES**, não de quilos. O `unclassified` é o volume de exercício sem `primary_muscles` no catálogo (a coluna é nullable): aparece para não sumir em silêncio.
- **WorkoutComparisonDto** — workoutName, currentDate, previousDate, currentKg, previousKg, exercises[]. · **ExerciseDeltaDto** — exerciseId, name, currentKg, deltaKg. Compara com a sessão anterior **do mesmo treino**; sem par, vem `null` e a tela não mostra o bloco.
- **ConquistaIds** ganhou 5 ids na J.2 — `CARGA_1T`, `CARGA_10T`, `CARGA_50T`, `CARGA_100T`, `CARGA_250T`. ⚠️ **O `target` dessas vem em TONELADAS, não em quilos**, e não é detalhe de exibição: a frase da medalha recebe o `target` no `%1$d`, então um alvo em quilos imprimiria "250000" no cartão. A barra de progresso também só fecha assim (175 de 250). Só conta **carga externa** — quem treina peso corporal não alcança, e as frases pt/en dizem isso.
- **AchievementDto** (#32) — id (contrato estável, vocabulário em `ConquistaIds`), unlockedAt? (ISO; `null` = bloqueada), current, target. ⚠️ **`title` e `description` SAÍRAM na G.5** (2026-09-15, ARCH #37): o texto vive no `strings.xml` do cliente, com a chave derivada do id (`PRIMEIRO_TREINO` → `conquista_primeiro_treino_titulo`). O contrato dizia o contrário — *"para ajustar texto ou limiar sem publicar versão nova"* — e a metade do limiar continua valendo; a do texto custava um idioma. **Id desconhecido some da tela** em vez de derrubá-la (servidor novo, app antigo). **`current` pode ser MENOR que `target` numa conquista desbloqueada** (streak quebra, medalha fica): a UI lê `unlockedAt`, nunca compara current com target.
- **Requests** — RenameProgramRequest(name), CreateManualProgramRequest(name, **id?** — opcional e retrocompatível: ausente = servidor gera), SetScheduleRequest(entries: [ScheduleEntry(workoutId, dayOfWeek)]).
- **Erro** — ErrorResponse(message, code?).

## Constantes de contrato

- **`JanelasDeProgresso`** (J.4.1) — `PADRAO = 8` · `FREE = 8` · `OFERECIDAS = [8, 26, 52]`. Mora no contrato, e não num `companion` de cada lado: o servidor encaixa o que vem fora da lista e o cliente desenha um chip por item. Escrita duas vezes, a lista divergiria na primeira janela nova, e o sintoma seria **um chip que não filtra**. O limite do free é aplicado no SERVIDOR mesmo com a tela já não deixando escolher, e o `weeksWindow` devolve a janela **aplicada**, não a pedida — seletor marcando 52 com o eixo desenhando 8 é a mesma mentira do rótulo "esta semana" numa faixa que terminou semanas atrás.

## Enums

| Enum | Valores |
|---|---|
| Goal | GAIN_MUSCLE, LOSE_FAT, MAINTAIN, GENERAL_HEALTH |
| Level | BEGINNER, INTERMEDIATE, ADVANCED |
| MuscleGroup | CHEST, BACK, BICEPS, TRICEPS, FOREARMS, SHOULDERS, LEGS, GLUTES, CORE *(ARMS aposentado — Fatia B.2)* |
| SplitType | FULL_BODY, UPPER_LOWER, UPPER_LOWER_FULL, PUSH_PULL_LEGS, UL_PPL, ARNOLD |
| TrainingEnvironment | ACADEMIA, CASA |
| BodyLimitation | SHOULDER, KNEE, LUMBAR, WRIST, IMPACT |
| WorkoutOrigin | AI, MANUAL |
| ExerciseCategory | CORE, FUNCTIONAL_HIT, MOBILITY, CALISTHENICS, LEGS, SHOULDERS, CHEST, BACK, CROSSFIT, BICEPS, TRICEPS, TRAPEZIUS, GLUTES, CALVES, FOREARMS, CARDIO, LOWER_BACK |
| HealthScreening | hasCardiacCondition, hasChestPainDuringActivity, hasJointOrBoneIssue, takesContinuousMedication, acknowledgedRisk → gateSatisfied |
| FriendStatus (#35) | NENHUMA, PEDIDO_ENVIADO, PEDIDO_RECEBIDO, AMIGOS, BLOQUEADO_POR_MIM *(o servidor manda o ESTADO; a tela só escolhe o rótulo do botão)* |
| Tipo de notificação (F.1 + F) | `PEDIDO_DE_AMIZADE` · `COMENTARIO_NO_CHECKIN` · `DENUNCIA_CONTRA_MIM` · `DENUNCIA_NO_GRUPO` · `CHECK_IN_INVALIDADO` · `ENTRADAS_DO_DIA` · `FILA_PARADA`. **Vocabulário aberto**, como `achievement_id` (#32): tipo novo não exige migration, e o cliente abre a central quando não reconhece — melhor um destino genérico que funciona do que nenhum. **Os seis da fatia F entraram sem tocar na V42**, que é a prova da aposta da F.1. |
| CheckInStatus (#33) | VALIDO, EM_ANALISE, INVALIDADO. Nasce fechado desde a V38, **mas só a E.2 lhe deu quem o movesse**. `INVALIDADO` é terminal. `EM_ANALISE` **continua contando ponto** (6.8) — se o ponto sumisse durante a análise, a denúncia viraria arma. |
| ReportTarget (E.2) | CHECK_IN, COMMENT. **Fechado no contrato**, ao contrário do emoji: acrescentar um alvo é mexer no schema e nas duas telas ao mesmo tempo, e um `String` livre esconderia esse trabalho até alguém receber `"perfil"` numa fila que não sabe abrir perfil. |
| Idioma (V47, #37) | `pt-BR` · `en`. **Fechado nos dois lados**: enum `Idioma` no `shared-contract` e `CHECK` na V47. Idioma novo é uma migration de uma linha, que só afrouxa o `CHECK` (mesmo movimento da V46 em `moderation_actions`). Viaja como **`String`**, nunca como enum — ver `UserDto.locale`. Um teste de integração compara o `CHECK` com o enum, porque são duas fontes da mesma verdade e adicionar de um lado só faria o `PATCH` passar na validação do Kotlin e estourar no `UPDATE`. |
| Reações (E.1) | 👍 💪 🔥 👏 😮 ❤️ — seis, emendando a 8.2 que dizia "qualquer emoji". **Fechado no Kotlin, aberto no banco**: a lista é decisão de produto e vai mudar, e um `CHECK` exigiria migration a cada ajuste de UI. |

## Códigos de erro (ErrorCodes)

**67 códigos desde a G.2** (ARCH #37). O catálogo completo, com a frase, a operação, a rota e o que a pessoa fez para chegar em cada um, é **gerado** em `docs/gerados/erros.md`.

- **Genéricos, que continuam existindo como FALLBACK**: `VALIDATION` · `UNAUTHORIZED` · `FORBIDDEN` · `NOT_FOUND` · `CONFLICT` · `INTERNAL`. Não são preguiça: o cliente cai neles ao receber um código que não conhece, e sem eles um código novo viraria tela sem texto.
- **`code` chegou a TODAS as famílias.** Antes só `Forbidden` e `Conflict` o tinham; `Validation`, `NotFound` e `Unauthorized` ganharam na G.2, porque o cliente passou a escolher o texto por ele.
- **`ErrorCodes.PORTOES_DE_PLANO`** é o CONJUNTO dos códigos que abrem o paywall: `ENTITLEMENT_REQUIRED` · `LIMITE_DE_PROGRAMAS_GRATIS` · `EDICAO_DE_IA_E_PREMIUM` · `TREINO_BLOQUEADO_PREMIUM`. Mora no contrato, e não num `if` do cliente. **`LIMITE_DE_PROGRAMAS_PREMIUM` fica de fora**: quem bate nele já é premium, e oferecer o plano a quem acabou de pagar é pior que não oferecer nada.
- **Vocabulário aberto no fio, fechado no código.** `String` e não enum, pela razão já escrita no `ErrorResponse`: código novo no servidor não pode quebrar cliente antigo.
- **Código é contrato, e não se renomeia.** Trocar o valor de um código quebra o texto em todo app já instalado, e a pessoa passa a ver o fallback sem que nada avise.

### A assimetria do `/me/progress` (fatia J)

Dois códigos novos, e só um deles existe:

- **`ID_DE_PROGRAMA_INVALIDO`** (400) e **`PROGRAMA_NAO_EXISTE`** (404) — o segundo não distingue "não existe" de "não é seu", porque `findAllByUser` já filtrou por dono e análise vazia seria uma afirmação sobre o programa de outra pessoa.
- **Janela, faixa e seleção de exercícios NÃO têm código**, de propósito. Valor fora da lista encaixa em silêncio. A regra que separa os dois casos: **`programId` muda QUAL dado responde; os outros três só mudam o que é desenhado.** Recusar a requisição inteira por uma janela de 12 ou por um id de exercício que a pessoa não treina deixaria a tela sem gráfico nenhum — e o `strengthTrend` da resposta já diz quais entraram, então ela se autodescreve.

### J.5 — `/me/progress/exercicio`

Padrão deliberadamente diferente do `/me/progress` acima: aqui **os dois** query params são obrigatórios e cada ausência/malformação tem código próprio — `ID_DE_PROGRAMA_INVALIDO` (400) e **`ID_DE_EXERCICIO_INVALIDO`** (400, novo na J.5). Não há "encaixa em silêncio": sem os dois ids não existe resposta possível, diferente de `?exercicios=` em `/me/progress`, que descarta id malformado em vez de recusar a requisição. O gate de plano aqui **é** `ENTITLEMENT_REQUIRED` (403) — ao contrário do `/me/progress`, que não usa código de `PORTOES_DE_PLANO` nenhum.

### ⚠️ Dois códigos são DELIBERADAMENTE vagos

`PERFIL_DE_TERCEIRO_INDISPONIVEL` cobre "não existe", "id malformado" e "essa pessoa te bloqueou" (9.3-A). `ALVO_INDISPONIVEL` cobre "não existe" e "é de outro grupo". **Precisá-los vazaria exatamente o que o 404 protege.** Não desmembrar.

O contraste é o `NAO_SOU_MEMBRO`, desmembrado na G.2 **confirmando que o desafio existe**: aceito porque o id do grupo é UUID e ninguém o adivinha, diferente do id de perfil, que circula em notificação e link.

## Convenções

- `shared-contract` é a fonte única — DTO nunca vaza pro domínio (mapper na data).
- Autoridade do servidor: XP, validação, IA, entitlement e gates decidem no backend; cliente só antecipa.
- Datas de execução: relógio do cliente (startedAt/finishedAt); created_at/currentWeek no relógio do servidor.
- Mapeamento de erro HTTP único (`httpResult`/`mapHttpError`, core:network): 401/403/400/404/409 → AppError tipado; falha de rede → Unexpected.
- **Texto que o usuário lê vive no cliente** (#37): o servidor manda dados, códigos e chaves, nunca prosa. **Exceção única: a notificação**, porque push só-dados não aparece com o app fechado (#36), e por isso `title`/`body` continuam vindo renderizados do servidor, no idioma de `users.locale`.
- ✅ **`ErrorResponse.message` deixou de ser texto de tela** (G.2, 2026-09-10). O cliente escolhe pelo `code`, que é o que o KDoc do `ErrorResponse` já mandava desde sempre: *"String estável p/ o cliente ramificar **sem parsear** `message`"*. A `message` continua no fio como **diagnóstico** e como **fallback para código desconhecido** — é ela que faz um servidor novo falando com um app antigo continuar explicando melhor que um texto genérico.

---
_Reconstruído do PDF canônico + verificado no `shared-contract`, 2026-09-07. Gerado por `docs/geradores/gen_contrato.py`._
