# ARCH #30 — Offline-first (substitui o #11 "offline leve")

**Fase:** 5 · **Status:** vigente (implementado parcialmente — ver "Onde estamos")

> **Substitui o ARCH #11.** O #11 dizia "offline leve": cache com fallback, leitura network-first.
> A partir daqui o alvo é offline-first de verdade. Isso também resolve a divergência registrada
> como débito de DOC (a Documentação Completa já dizia "offline-first"; a arquitetura, não).

## Decisão

**O banco local é a fonte da verdade da UI.** A tela nunca espera a rede: ela lê do SQLDelight e
renderiza. O sync roda por fora e, quando grava, o `Flow` re-emite e a tela se atualiza sozinha.

Três consequências que valem como regra:

- **[REGRA] Tela lê do banco, nunca da rede.** Repositório expõe `Flow` do local; a rede é um
  sincronizador separado. Erro de rede não vira erro de tela — o usuário segue vendo seus dados.
- **[REGRA] Sync é responsabilidade de fundo.** Nenhuma tela pode ser a única a popular o banco.
  (Violamos isso: o histórico só descia se o usuário abrisse o Progresso online. Corrigido.)
- **[REGRA] Todo dado local é chaveado por `uid`.** Um aparelho, várias contas. Sem isso o
  histórico vaza — e pior, o flush enviaria a sessão de um usuário com o token de outro.

## Autoridade do servidor continua valendo

Offline-first **não** move a verdade para o cliente. XP, validação, entitlement e geração seguem
decididos no backend. O cliente mostra a **última verdade conhecida** e sinaliza o que está
pendente ("N treinos aguardando sincronizar"). Ele nunca calcula XP localmente: haveria dois
números divergindo na hora do sync.

Como o XP é **derivado das sessões** (não há saldo persistido), sincronizar a sessão basta — o XP
se acerta sozinho no servidor. Foi o que tornou o offline simples.

## O padrão da fila (outbox)

Não existe tabela de fila. A fila é uma **consulta** sobre a tabela de dados:

```
pending = 1  ->  fila de envio
pending = 0  ->  histórico
```

Confirmar **não apaga a linha** — só desliga o marcador. A `session_outbox` antiga apagava, e com
isso o histórico local se auto-destruía.

O `id` é **gerado no cliente** (UUID), o que torna o `POST` idempotente: reenviar nunca duplica.

## WorkManager: o despertador

Outbox = *o que* está pendente. WorkManager = *quando* tentar de novo. Ele roda com o app fechado,
só com rede, com backoff exponencial, e é único por nome (treinar 3x offline não cria 3
agendamentos). Sem ele, o treino só subia se o usuário reabrisse o app.

## Estrutura do banco local

| Tabela | Papel | Nível |
|---|---|---|
| `exercise` | catálogo (consultável, indexado por categoria) | offline-first pleno |
| `workout_session` | histórico **e** fila (`pending`), por `uid` | offline-first pleno |
| `onboarding_cache` | flag do quiz (linha única, por `uid`) | ok |
| `kv_cache` | blob chave→JSON: programas, treinos, stats | só cache |

**O `kv_cache` é o ponto fraco**: blob não é consultável, ordenável nem observável. É por isso que
programa e treino ainda não são reativos. Tirá-los de lá é o próximo passo.

**Migrations reais** (`deriveSchemaFromMigrations`): o schema vive nos `.sqm`, os `.sq` só têm
queries. O banco tem nome estável (`fitjourney.db`) — bumpar o nome apagaria treino não
sincronizado. Detalhes em `shared/core/database/MIGRATIONS.md`.

## Política de sincronização

| Momento | Sobe | Baixa |
|---|---|---|
| App abre (Splash, `appScope`) | pendências | histórico |
| Termina treino | a sessão | — (já está no local) |
| Tela de Progresso | ✓ | ✓ (janela de 5 min) |
| Home | ✓ | só stats |
| WorkManager (app fechado) | ✓ | ✓ |

**Janela de frescor (TTL 5 min)** em programas e histórico: trocar de aba não refaz a requisição.
Sincronizar demais não deixa o app lento (a tela lê do banco), mas gasta dados e bateria.
Mutação local invalida a janela na hora.

## Onde estamos

```
[x] Migrations reais + banco com nome estável
[x] workout_session (histórico + fila) por uid
[x] Histórico e XP offline (Progresso, Home) + WorkManager
[x] Tabelas reais para programa e treino (sair do kv_cache)
[x] Carimbos de sync persistidos (SyncStamps) — TTL sobrevive ao fechar o app
[ ] Outbox genérico: criar/editar treino offline
[ ] Delta sync + paginação (quando o histórico crescer)
```

## Emenda 2026-08 — política de frescor (SyncStamps)

Cache-first sem carimbo persistido é meia solução: todo TTL vivia em memória e valia **zero**
entre aberturas. O caso mais irônico era o catálogo de exercícios — TTL de 24h porque só muda em
deploy, expirando a cada vez que o app fechava, rebaixando 965 exercícios por boot.

`SyncStamps` grava o instante do último sync no `kv_cache`, com o **uid na chave**
(`sync:programs:{uid}`). Isso substituiu quatro pares `sincronizadoEm` + `donoDoCache` escritos
à mão.

**[REGRA] Isolamento por conta é consequência da chave, não de um `if`.** A conta B simplesmente
não encontra o carimbo da conta A. Foi esquecendo esse `if` num lugar que dado de uma conta
apareceu em outra.

**[REGRA] TTL para o que você não controla; invalidação explícita para o que você controla.**
Mutação local não é aposta — invalida na hora. Mudança remota é imperceptível ao cliente, e é aí
que o TTL entra.

Janelas escolhidas (não uniformes de propósito — cada uma reflete quão rápido o dado muda):

| dado | TTL | escopo |
|---|---|---|
| catálogo de exercícios | 24h | GLOBAL (não tem uid) |
| programas, detalhe de treino, histórico | 5 min | usuário |
| XP / stats | 2 min | usuário |
| perfil | nenhum | — gate da Splash, sempre atualiza de fundo |

Medido: cold start caiu de 6 requisições para 2.

**FIX que veio com isso:** `ProgramLocalDataSource.read()` devolvia `null` tanto para "não tenho
programas" quanto para "nunca sincronizei". Com isso **toda conta sem programa furava o
cache-first** — o carimbo dizia fresco, o read devolvia null, e o repositório ia à rede em toda
abertura, para sempre. Hoje `read()` devolve lista (vazia é resposta válida) e quem responde
"nunca sincronizei" é `stamps.jaSincronizou()`.

**Consequência na UI:** o card "Sem conexão" passou a exigir que o aparelho REALMENTE nunca
tenha baixado nada naquela conta. Antes o marcador era campo de ViewModel e voltava a `false` em
todo cold start, então quem sincronizou ontem e abria offline hoje via "Sem conexão".

**Splash cache-first:** `onboardingCompleted` é MONOTÔNICO (concluído nunca volta atrás), então
um `true` cacheado dispensa esperar a rede. `false`/`null` continuam esperando o servidor —
pular o onboarding por engano é pior que esperar 2s.

**Fora de escopo por natureza:** gerar programa por IA exige o motor do servidor (ARCH #20).
Primeiro login também (Firebase).

## Alternativas descartadas

| Opção | Por que não |
|---|---|
| Firestore | trocaria Postgres+Ktor; perderíamos o motor no servidor |
| PowerSync / ElectricSQL | infra nova; empurra a verdade para o cliente |
| CRDT (Linear/Figma) | desnecessário: dado de um usuário só, sem edição concorrente |

Todas colidem com a autoridade do servidor. O padrão adotado (SSOT + outbox + WorkManager) é o
mainstream do Android e preserva o motor determinístico como diferencial.

---
_Escrito em 2026-08-13, depois de implementar histórico e XP offline. Substitui o ARCH #11._


---

## Emenda 2026-08-16 — ESCRITA offline (outbox genérico, fatias B.1–B.4)

Fecha o último item aberto do #30. A leitura já era cache-first; a escrita ainda ia direto à
rede e falhava sem rede.

### O caminho é UM só, com ou sem rede

Grava local → enfileira no outbox → pede o envio. A alternativa ("se tem rede manda direto,
senão enfileira") produz dois comportamentos, dois conjuntos de bug, e deixa o caminho offline
exercitado apenas quando algo já deu errado — ou seja, mal testado.

Efeito visível: salvar é instantâneo e nunca falha por rede. O erro, quando existe, é do
servidor e chega depois — pela fila, não pelo botão.

### B.1 — id gerado no CLIENTE (idempotência)

`POST /workouts` e `POST /programs` aceitam `id` no corpo. Servidor usa `insertIgnore` + releitura:
reenviar depois de uma resposta perdida devolve o recurso que já existe em vez de duplicar. Id de
OUTRO usuário → 409. Sem isto, toda queda de rede no meio de um POST viraria treino duplicado.

### B.2 — a fila (tabela `outbox`, migration 3.sqm)

Guarda **comandos**, não "estado + flag de sujo". Um flag não sabe dizer que o usuário criou,
editou duas vezes e excluiu — perde a intenção. `CompactadorDeOutbox` (lógica pura, 7 testes)
reduz a sequência ao menor conjunto de requisições:

| sequência | vira | por quê |
|---|---|---|
| criar + editar + editar | criar (payload final) | o servidor nunca viu o recurso |
| criar + … + excluir | **nada** | recurso que o servidor nunca conheceu |
| editar + editar | editar (último) | só o estado final importa |
| editar + excluir | excluir | o intermediário é irrelevante |

A compactação acontece na LEITURA, não na gravação: a fila preserva o histórico bruto, e um bug
no compactador nunca destrói intenção já gravada.

[REGRA] A ordem global é preservada — cada alvo mantém a posição da sua PRIMEIRA operação.
Criar um treino e agendá-lo são duas operações em que a segunda referencia a primeira.

### B.3 — repositórios escrevem pela fila

`AgendadorDeSync` (interface no core, WorkManager no androidApp) mantém os repositórios KMP
livres de Android.

**O sync não pode apagar o que ainda não subiu.** `save()` recebe `outbox.alvosPendentes()` e
protege esses ids em DOIS lugares — na limpeza e no re-insert. Só o primeiro faria o treino
criado offline sumir da tela; só o segundo faria a versão antiga do servidor sobrescrever a
edição que o usuário acabou de fazer.

Três armadilhas de SQL que custaram caro:

- `limparTreinosExceto` precisa proteger por `id` **e** por `programId`: renomear um programa
  offline torna o programa pendente, o `save()` não o reescreve — e os treinos dele, que vêm
  aninhados na resposta, seriam apagados e nunca reinseridos.
- `programId` é NULLABLE e em SQL `NULL NOT IN ('a')` vale NULL, não TRUE. Sem
  `programId IS NULL OR …`, nenhum treino avulso seria limpo pelo sync — nunca.
- `NOT IN ()` com lista vazia é erro de sintaxe no SQLite; por isso existe a variante sem
  exceção, usada quando não há pendentes.

`get(id)` também mudou: **alvo pendente ⇒ o local é a verdade**. O servidor ou não conhece o
recurso (404) ou tem a versão anterior, e as duas respostas apagariam o que está na tela.

### B.4 — o processador

`ProcessadorDeOutbox` + `ExecutorDeOperacao` por feature (inversão: o core não pode depender de
feature). `FilaDeSaida` é a superfície estreita que torna o processador testável sem SQLDelight —
8 testes.

A distinção que sustenta tudo: **temporária vs. permanente**.

- 400/403/404/409 → o servidor RECUSOU. Retry dá o mesmo resultado para sempre. Marca, para de
  tentar, a UI mostra e oferece descartar.
- sem rede / 5xx / **401** → temporário. O 401 é renovado pelo `refreshTokens`; descartar a
  escrita de quem ficou uma hora sem abrir o app seria o pior desfecho possível.

[REGRA] Na primeira falha TEMPORÁRIA o processamento PARA. Não pula para a próxima. Se o POST do
treino falhar por rede e o PUT da agenda for tentado assim mesmo, o servidor devolve 400 (agenda
referenciando treino inexistente), o 400 é permanente, e a agenda do usuário é descartada por
causa de uma queda momentânea.

Na falha PERMANENTE segue para o próximo alvo: a recusa é daquele recurso, e os outros não têm
por que pagar por ela.

### Fronteira com o ARCH #25 — o outbox cobre o que o usuário POSSUI

Conteúdo `AI` é travado por entitlement, e o `403 ENTITLEMENT_REQUIRED` é o que abre o paywall.
Se essa mutação virasse otimista, o usuário free veria "salvo", o paywall nunca apareceria, e
minutos depois a edição sumiria sozinha quando a fila trouxesse o 403 — regressão numa rota de
monetização, disfarçada de melhoria offline.

Portanto: **`MANUAL` → outbox; `AI` → online-only e síncrono** (treino, programa, agenda).
`generate()` também segue online-only, por natureza (não há resultado otimista a mostrar — o
cliente não sabe que treinos a IA vai montar).

Preço aceito: premium não edita conteúdo de IA offline. Reverter exigiria confiar na cópia local
de `isPremium`, que é otimista por definição — colide com a autoridade do servidor.

### Selo na UI

Escrita otimista sem selo é desonesta: o usuário não teria como distinguir "está no servidor" de
"está só neste aparelho", e desinstalaria o app achando que sincronizou. `PendenciaDeSync` mora no
DOMÍNIO (a presentation não enxerga `core:database`); "Pendente" é discreto (some sozinho),
"Não sincronizou" vai em `error` (exige ação). [REGRA] nada de `lime` — é exclusiva do #16.

### Decisão: logout NÃO limpa a fila

Ela é chaveada por uid, então outra conta não a vê nem a envia. Apagar significaria descartar o
treino de quem saiu e voltou.

### O que a bateria manual revelou (e os 15 testes puros não)

Os cinco testes de emulador acharam **quatro defeitos**, nenhum deles no compactador nem no
processador — que eram justamente as duas peças com cobertura automatizada. Todos estavam nas
COSTURAS entre a fila e o resto do app. Vale como calibragem de onde o risco realmente mora.

**1. Treino criado offline não aparecia — sete "Descanso" na tela.** A criação gravava a tabela
`workout`, mas a visão de semana monta os 7 dias a partir de `program_schedule`. Online quem
escreve essa linha é o servidor; offline não escrevia ninguém. O dado estava salvo e invisível,
que é pior que erro na cara. Eu tinha visto essa lacuna durante a B.3 e a classifiquei como
"pré-existente" — com escrita offline ela deixou de ser latente.

**2. O selo "Pendente" grudava para sempre.** O KDoc do `Outbox` prometia que WorkManager e fila
eram redundantes — "se o worker atrasar, a próxima abertura esvazia". A promessa **não tinha
implementação**: só o worker chamava o processador. Hoje o `refresh()` envia antes de baixar, e
`list()` considera `contarPendentes()` na condição de cache-first (`sujo` é em memória e some ao
fechar o app, então o carimbo fresco segurava a fila por até 5 min).

**3. O worker falhava e era indepurável.** `Worker result RETRY` de 30s a 8min, sem uma linha de
diagnóstico — o `runCatching` engolia a exceção. A pista veio do TEMPO: duas execuções em 4ms,
que é exceção, não rede. Causa: `single<List<ExecutorDeOperacao>>`, Koin resolvendo genérico de
coleção. Duas lições viraram código: o worker loga cada etapa (`adb logcat -s FitJourneySync:*`)
e as etapas são independentes — uma falha no histórico não pode prender o dado do usuário.

**4. Alvo anulado pela compactação nunca saía da fila.** O teste "criar + excluir offline"
PASSOU no que era visível (nada chegou ao servidor) e escondeu isto: o processador só itera
sobre o resultado da compactação, então um alvo anulado nunca chega em `concluir()`. As linhas
ficavam na tabela para sempre — e com elas `contarPendentes()` acima de zero, o que desligava o
cache-first que a correção #2 acabara de introduzir. Um teste verde escondendo uma regressão de
performance silenciosa.

O padrão dos quatro: **lógica pura testada, integração não**. É onde a próxima cobertura deve ir.

