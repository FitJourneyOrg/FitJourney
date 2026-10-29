# Estado — onde paramos

> **O único documento de leitura obrigatória no início da sessão.** Só mora aqui o que muda a cada
> fatia. O resto se lê quando o assunto exigir: convenções em `CONVENCOES.md`, regra de
> comportamento em `regras/`, decisão em `arch/`, débito em `fontes/debitos.md`, migration em
> `migrations/INDICE.md`, fatia fechada em `historico/`.

_Atualizado em 2026-10-28 (**motor v3 — fatia 10 fechada**: progressão dupla, calibração, mesociclo/deload, retorno após pausa e foco (só bônus de alvo) no oráculo e no Kotlin, provados contra 3 referências geradas; débito do 60+ **fechado**. `:server:test` 759 verdes, oráculo 129. Schema de progressão por exercício **aprovado**, migration V69 ainda não escrita. Antes dela: fatias 0–9 fechadas. Narrativa em `historico/`._

## Agora

| | |
|---|---|
| Última fatia fechada | **Motor v3 — fatia 10 (mesociclo, progressão e foco)**, 2026-10-28. Entregou: `Progressao`, `Mesociclo`/`CicloDoMesociclo`/`AncoraDoIniciante`/`RetornoAposPausa`, `ProgressaoRepository` + fakes, foco como bônus (grande derivado do peso, `LEGS` repartido), três referências do oráculo (`referencia_720` intacta, `referencia_foco` 960, `referencia_progressao` 12.730). `:server:test` **759**, oráculo **129**. Detalhe em `historico/motor-v3-fatia-10.md`; regras em `regras/01`, "Motor v3 — mesociclo, progressão e foco" |
| Migration no código | **V68** (`V68__core_sai_do_motor.sql`) — aperta `chk_exercises_volume_target` para recusar `'CORE'`, com bloco `DO` que falha alto se alguma linha ainda usar o alvo. Antes dela, **V67** (`V67__rir_minimo_de_seguranca.sql`) — `exercises.rir_minimo_seguranca`, 8 exercícios `true`, autoconferência no fim. V64/V65/V66 **commitadas e mergeadas em `main`** (PR #58, 2026-10-23); V67 local, não commitada |
| Schema local | `9.sqm` (`8.sqm` = acervo do Aprender/Fase 8, `9.sqm` = tabela `notificacao`) |
| Catálogo | **893 exercícios** depois da V65 (922 antes: 29 saíram, duplicados e um aparelho passivo). **147 na lista principal**, com `volume_target`, `prioridade`, `relevancia`, `nivel_maximo` e `ambiente`; família e os seis `requires_*` nos 893 |
| Não commitado | **A Fatia 10 inteira** (verde), esperando seu commit e push: oráculo (`progressao_v3.py`, `gerar_referencia_progressao.py`, `test_progressao_v3.py`, 3 referências, `spec_v3.py`/`motor_v3.py`/`test_motor_v3.py`/README), Kotlin (`Progressao`, `Mesociclo`, `ProgressaoRepository`, mudanças em `VolumeTargets`, `CapacityModel`, `SafetyRules`, `MotorV3Pipeline`), testes novos, `server/build.gradle.kts` (guarda `== 4`). `docs/` e `tools/` estão fora do git |
| Achado ao fechar | **2026-10-28, Fatia 10:** (1) o foco como bônus de alvo é quase inerte — 21/480 programas mudam — porque a capacidade é o limite; a proteção na alocação (Fatia 11) é o que o faz funcionar. (2) `decidir()` não é idempotente: o upsert do repositório não basta, a idempotência vem de `progression_event` pela sessão. (3) `session_set_logs` não guarda RIR. (4) asserção escrita com conta de cabeça erra: 4 falhas do Gradle foram do teste, não do motor |
| Em andamento | **Nada no motor — a fatia 10 fechou.** Paralelo, sem relação: **varredura de débitos A–D** (`trabalho/debitos-A-D.md`), A/B/C fechados em 2026-10-09, **D aguardando o Rafael** |
| Motor de geração | **Especificação pronta, fatias 0–10 fechadas, 11–12 por fazer.** A spec vive em `propostas/motor-v3-ESPECIFICACAO.md`; o desenho e as fatias em `trabalho/motor-v3-desenho-e-fatias.md`. O que roda hoje em produção ainda é a geração antiga (`StructureEngine` + `SlotFiller`, `Random(seed)`) — `engine.v3` existe em paralelo, sem fiação. `server/src/test/oracle/` é o oráculo (129 testes verdes) e escolhe pela curadoria. **Condição da troca de produção:** o 60+ já saiu do conservador permanente (Fatia 10), mas o **foco é permanente e quase inerte até a Fatia 11** — ou a 11 vem antes de ligar, ou você aceita explicitamente (`fontes/debitos.md`). `regras/01-motor-de-geracao.md` tem cinco `[INV]` marcados `[VENCIDO]` |
| Próxima fatia | **Fatia 10b escrita (V69 + `*RepositoryImpl` + `ProgressaoIntegrationTest`), aguardando você rodar `:server:test` e `:server:integrationTest` (Docker); nada grava `session_set_logs.rir` ainda (contrato/DTO).** Depois, em sessão NOVA: **Fatia 11 — nível, promoção e ênfase** (proteção contra sacrifício, que faz o foco funcionar). **Três decisões esperando você, nenhuma bloqueante:** (1) **relevância do tríceps** (CREF); (2) as **defesas `SEM_CATALOGO` e `V.mrv`** sem caso vivo; (3) o **antebraço** com alvo > 0 e zero direto em 245–261/720. Todas em `fontes/debitos.md` |

> ⚠️ **Dois achados da reestruturação de 2026-10-07, ambos esperando sua confirmação:**
>
> 1. **A V61 não estava em documento de estado nenhum.** Painel, Handoff e README paravam na V60;
>    `V61__wiki.sql` está em `server/src/main/resources/db/migration/` desde antes desta sessão, e
>    só aparecia no `adendo-stack.md` e no `contrato-api.md`. O código vence a doc — por isso a
>    tabela acima diz V61.
> 2. **A V58 pagou um débito que continuou listado como aberto.** `V58__prancha_isometrica.sql`
>    existe, e "prancha isométrica ausente" seguia nas pendências do catálogo.

## Fase corrente

A tabela **"Por fase"** do `README.md` é a fonte única — não duplicar aqui.

> ⚠️ **Divergência corrigida em 2026-10-07, confirme:** três documentos discordavam sobre a Fase 6.
> O `README.md` dizia "em curso, faltam D, E e o resto da F"; o Handoff tinha ao mesmo tempo
> "FASE 6 CONCLUÍDA (2026-09-09), todas as fatias entregues (A.0·A·B·C·C.1·D·E.1·E.2·F)" **e** um
> "Próximo passo" listando `#35 → D → E → F` como restantes. Adotei a versão concluída: é a mais
> recente, a mais específica, e as fatias G/H/J fecharam depois dela. O "Próximo passo" antigo foi
> descartado por ser resíduo — está no `historico/` se precisar conferir.

## Suíte — são CINCO tasks

`:konsist:test` · `:server:test` · `:server:integrationTest` (Postgres real, **exige Docker**) ·
`testAndroidHostTest` (módulos KMP) · `:app:androidApp:testDebugUnitTest` (ViewModels do app).

`gradlew` não roda no ambiente do Claude — ele pede, você roda e cola. Contagem atual de testes:
`gerados/testes.md` (gerado), nunca somada à mão.

Em 2026-10-21 rodaram duas, verdes: `:server:test` (575) e `:server:integrationTest` (194). **As outras três
não foram rodadas desde a curadoria** — `:konsist:test`, `testAndroidHostTest` e
`:app:androidApp:testDebugUnitTest`.

> **Suíte que não roda não cobre nada.** Vale para a que exige Docker e para a que o Gradle acha
> que já rodou (`:konsist:test` fica `up-to-date` sem varrer — use `--rerun-tasks` para provar).

## Fechamento de ciclo

Testes verdes → atualizar **este arquivo**, `fontes/debitos.md` e `migrations/INDICE.md` →
`python3 docs/geradores/gen_tudo.py`. O que só você faz: confirmar build verde e o `git push`/merge.

---

## Pendências ativas (demais em `docs/fontes/debitos.md`)

- **`motor-fatia-0-catalogo-e-medicao.md` fechado e apagado em 22/10.** §7 inteiro promovido:
  narrativa e Hand Grip → `historico/`; V64/V65/V66 → `migrations/INDICE.md`; critério de
  casamento (`volume_target`, decidido mas **não implementado** no `SlotFiller`) →
  `[REGRA]`/`[INV]` em `regras/01-motor-de-geracao.md` + `ARCH #39`; fora de escopo (CREF, LGPD,
  Lei 9.696/98, referência do avançado, `ombro_vira_isolamento`, refinamentos futuros) →
  `fontes/debitos.md`. Os 5 achados do andamento de 2026-10-14 (não eram "fora de escopo", eram
  bug/lacuna no caminho) **ainda não triados** — ficaram em `trabalho/debitos-§7.md`, a decidir
  qual é débito real e em qual fatia do motor v3 cada um cai.
  `acessorios-no-onboarding.md` já tinha fechado e sido apagado em 22/10.
  `motor-v3-desenho-e-fatias.md` é o roteiro das fatias 1–12 e fica, é o próximo vivo;
  `motor-lista-principal.md` e `notificacoes-offline.md` são planos antigos, a decidir

- **Dúvidas sem preço/plano, cancelamento, contato de suporte e política de privacidade** — as
  8 perguntas (2026-10-04) cobrem só regra já implementada; os quatro de cima dependem de
  decisão ou dado que só o Rafael tem, não foram esquecidos

- **Notificação de desbloqueio de conquista** — hoje a avaliação só roda na leitura, então o
  usuário descobre a medalha ao abrir a tela. Quando houver notificação, o `POST /sessions`
  precisa avaliar também
- **`stats` + `session` + `achievements` no módulo app** — os três deviam ser features próprias;
  conquistas seguiu o padrão dos irmãos de propósito, para não repartir a gamificação em dois

- **Outbox sem teste de integração** — compactador e processador têm cobertura pura, mas a
  interação outbox × `save()` (preservar pendentes) é exatamente onde estavam os bugs, e só
  tem verificação manual
- **Premium não edita conteúdo de IA offline** — consequência aceita da fronteira #30 × #25
- **Sem listener de conectividade** (⚪ aceito em 2026-10-08) — a fila sobe pelo WorkManager; a tela aberta não reage à rede voltando, só ao entrar, ao puxar ou ao "Tentar de novo"

- ~~`tools/` sem decisão de versionamento~~ — **DECIDIDO (2026-08-23): fora do git**, junto com `exercises.csv` e `gifs_exercicios/`
- `SessionApi`/`SessionSync` no módulo app (atalho) — extrair p/ `session:data`
- `fieldErrors` do nome de programa inalcançável pela UI (`enabled = name.isNotBlank()` barra
  antes) — rede de segurança, não caminho normal
- `/me/profile` sem TTL — decisão consciente (é o gate da Splash), roda de fundo
- Catálogo: nomenclatura de placas, Afundo × Avanço, `MuscleGroup` sem CALVES/TRAPEZIUS/
  LOWER_BACK, prancha isométrica ausente, 74% da base em INTERMEDIATE
- Dedup FUNCIONAL (stiff ↔ terra romeno) — precisa de decisão de produto


## Débitos ⚪ abertos (lista completa em `fontes/debitos.md`)

Re-encodar os 963 MP4 (197 MB, hoje 95% da mídia) · deep link do convite · vocabulário do 409
duplicado entre servidor e `EntrarScreen` · `LocalClipboardManager` deprecado · `integrationTest`
em 3m51s (um container por classe) · ~24 imagens órfãs sem exercício · base da API como `var`
global · copiar `gifs_exercicios/banners/default.jpg` para produção quando houver (dev já resolvido
e verificado em 2026-09-06).

> **Débito só fecha quando alguém verifica, não quando alguém entrega.** O banner ficou duas
> semanas aberto depois de resolvido. A V58 é o mesmo caso pelo outro lado: pagou e ninguém riscou.
