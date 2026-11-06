# RIR chega do cliente e acorda a progressão — fatia fechada (2026-11-05)

> Movido de `docs/trabalho/rir-plano.md` ao fechar o ciclo. Decisões e desenho completos ficaram
> no plano original (ver git log); aqui só a narrativa e os achados. Backend → Contrato → Cliente,
> três sessões/etapas do mesmo dia.

## O que entregou

Fecha a face (a) de "o motor sabe, o app não mostra" (`fontes/debitos.md`, P0): RIR por série
chega do cliente até `session_set_logs.rir` (coluna existia desde a V69, nunca escrita), e
`SessionService.record()` passa a chamar `ProgressaoAplicadaService` → `calibrar()`/`decidir()`
→ `ProgressaoRepository.registrar()` **em produção** — não o motor v3 dormente, o
`SessionService` real que toda sessão salva já passa por hoje.

## O gap empilhado que a investigação de fechamento achou primeiro

O débito original só falava de (a). Investigando o fechamento de "declaração de custo do motor"
(2026-10-31), achou-se um segundo gap, mais sério: **mesmo com RIR chegando, nada no servidor
chamava `Progressao.decidir()`/`calibrar()`/`registrar()`** — zero chamadas em código de
produção, só em DI e teste. Resolver só o contrato do RIR não acordaria nada. As duas lacunas
precisavam fechar juntas.

## Os dois dados que não existiam em lugar nenhum do código

A primeira leitura (2026-10-31) presumia que `Papel`, `incremento` e `barraLivre` já existiam em
algum lugar — catálogo ou sessão — e só precisavam ser encontrados e ligados. **Não existiam.**
`incremento`/`barraLivre`/`cargaInvertida` nasceram numa fatia própria, puxada de dentro deste
plano (`catalogo-incremento-e-semantica-de-carga.md`, V74, 2026-11-05) — derivados de `equipment`
com autoconferência e três CHECKs. `Papel` resolveu mais barato que o previsto: a produção
(`StructureEngine`/`VolumeModel`) já calculava o equivalente como `SlotRole`, só não persistia.

## Unificação `Papel`/`SlotRole`, e por que não exigiu ligar o v3

Decisão com o Rafael: um enum só, `Papel` (nome que `Progressao.decidir()`/`calibrar()` já
consomem — não tocar a assinatura evita regerar o oráculo), movido pra `shared-contract`, com os
dois motores (v3 e produção) apontando pro mesmo tipo. Checado antes de propor: os três papéis da
produção (`VolumeModel.kt`) e as faixas do v3 são a MESMA classificação estrutural, calibração
numérica diferente — o papel é só o rótulo que escolhe qual faixa consultar, não a faixa em si.
Isso desacoplou esta fatia da decisão de trocar o gerador de produção pelo v3, que continua em
aberto e não-bloqueante.

## Decisões tomadas com o Rafael antes de codar

- **Bi-set/vaga duplicada**: só a 1ª ocorrência do exercício na sessão (por `orderIndex`) entra na
  progressão; as demais continuam contando pra tudo o resto (XP, histórico, etc.).
- **RIR por série, não só a 1ª** — mesmo padrão de `repsDone`/`weight`. `decidir()`/`calibrar()`
  usam RIR de todas as séries; a Regra 4 (`reducaoIntrasessao`) continua usando só a 1ª.
- **Calibração registra como `Evento.Carga(Decisao.MANTEM)`** — não existe `Decisao` própria pra
  "acabei de calibrar", e reaproveitar `MANTEM` não exige mudar o oráculo. Conta pro streak de
  estagnação como uma sessão que não subiu — aceito.
- **Sem `Profile` (onboarding incompleto): pula a sessão inteira** — sem `Goal` não dá pra
  escolher `PrescricaoV3.de()` pra nenhum exercício.
- **O contador da Regra 4 conta o EFEITO, não a intenção** — com arredondamento, se a regra
  computa e é apagada (`anulada = true`), o disparo não deve contar execução onde não houve freio
  de fato. Corrigido nesta fatia, que é quem instrumenta a Regra 4 pela primeira vez em produção.

## O item bloqueante: a troca de sinal da carga invertida

A V74 marcou 4 exercícios (Barra Fixa Assistida e família) com `cargaInvertida = true`, mas o
motor ainda não lia a flag. Sem a troca de sinal em `decidir()`/`calibrar()`/
`reducaoIntrasessao()`, o dia em que este fio ligasse faria a Barra Fixa Assistida **regredir
calada** — `SOBE` soma `incremento` normalmente, e na assistida mais peso é mais ajuda, não menos.
Direção errada é pior que magnitude errada. Resolvido nesta fatia, com caso de teste nomeado no
oráculo (exercício com `cargaInvertida = true`, topo da faixa, RIR no alvo, `SOBE` → carga
devolvida **menor** que a anterior) e seu caminho de falha (`cargaInvertida = false` no mesmo
cenário → carga maior, prova que o sinal não foi trocado para todo mundo).

## Achados do servidor, não previstos no desenho original

- **`Goal` vem de `Profile`, não de `User`** (`ProfileRepository.findByUserId`) — só descoberto ao
  implementar, não estava em nenhuma leitura anterior do código.
- **`ExerciseRepository.paraProgressao(ids)`** — lê `incremento`/`barraLivre`/`cargaInvertida` sem
  idioma, mesmo motivo do `paraAnalise` existente ("quem decide não traduz"). Débito registrado:
  é código novo contra o Exposed real sem caso em `ProgressaoIntegrationTest` — só os fakes cobrem
  a orquestração.
- **`papel` de origem é resolvido pela VAGA exata** (`exerciseId` E `orderIndex`), via
  `WorkoutRepository.findById`. Sessão sem `workoutId` (treino manual) ou `WorkoutExercise.papel
  == null` pula SÓ aquele exercício, mesmo piso do `incremento == null`.
- **`cargaInicial` da calibração** vem do primeiro `SetLog.weightKg` não-nulo da vaga — não existe
  outro lugar pra "qual foi o peso que a pessoa usou pela 1ª vez".

## Cliente — o que faltava, e o achado antes de tocar `SetEntry`

Primeiro passo, por desenho do plano: checar se existia tabela/cache local de log de sessão antes
de assumir que o DTO bastava. **Achado: não precisava de migration nenhuma.** `workoutSession.sq`
guarda a sessão inteira serializada como `payload` (JSON de `WorkoutSessionDto`, via
`SessionSync.kt`) — não é um schema coluna-a-coluna. `rir: Int? = null` já chegava opcional no
contrato (etapa anterior desta mesma fatia); bastou o cliente aprender a preencher o campo.

Entregou: `SetEntry.rir: String` (mesmo padrão texto-livre de `repsDone`/`weight`),
`SessionEvent.RirChanged` tratado em `onEvent` com o mesmo filtro de dígitos, `finish()`
carregando `rir = e.rir.toIntOrNull()` no DTO (em branco → `null`, não `0` — D-3 exige que "não
informado" e "zero" sejam coisas diferentes), e uma terceira coluna no `CartaoDeNumeros` da tela
de execução (stepper `-`/`+`, mostra `?` quando em branco) com uma frase explicando o que é RIR
pra quem nunca usou.

## O defeito que o teste achou, e a correção que ele exigiu no próprio `ViewModel`

`WorkoutSessionViewModelTest` não existia — zero testes pra este ViewModel antes desta fatia.
Escrever o primeiro caso travou o processo de teste: `init` dispara `contarOTempoDaSessao()`, um
`while (true) { ...; delay(1_000) }` que nunca termina por conta própria — em produção, o Android
cancela o `viewModelScope` inteiro quando a tela fecha (`ViewModelStore.clear()`), mas o teste
constrói o ViewModel direto, sem esse ciclo de vida. `onCleared()` já existia, mas era
`protected`: o teste não tinha como chamá-lo pra parar o laço, que ficava pendurado no
dispatcher depois do teste "passar" — o processo trava esperando uma corrotina que nunca acaba.

Corrigido no próprio ViewModel, não só no teste: `onCleared()` passou a `public` (única forma de
o teste cancelar sem depender do `ViewModelStore` do Android) e agora cancela também o job do
cronômetro — antes só cancelava o do descanso (`restJob`). Três casos no teste novo: RIR chega no
DTO, RIR em branco chega como `null` (caminho de falha nomeado, D-3), filtro rejeita não-dígito.
Todos chamam `vm.onCleared()` no fim.

Verificado pelo Rafael: `:shared:features:session:presentation:testAndroidHostTest` verde.

## O que ficou fora, nomeado pra não ser suposto na próxima sessão

- **UI/texto mostrando o resultado da progressão ao usuário** (subiu carga, manteve, etc.) — é a
  MESMA família de "o motor sabe, o app não mostra" que esta fatia parcialmente fecha, não o
  completa.
- **Medir o disparo real em produção** — o fio está ligado, mas o volume de dados pra confirmar
  que a Regra do −4%/calibração disparam como esperado depende de uso acumulado, não é
  instantâneo. `limite_poucas_reducoes` (outra heurística dependente de RIR real) segue na mesma
  situação.
- **(b) avançado sem service/rota** continua aberto — não tocado nesta fatia, fora de escopo desde
  o plano original.
- Ligar o motor v3 como gerador de produção — débito separado, não bloqueante aqui.
