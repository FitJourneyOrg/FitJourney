# Estado — onde paramos

> **O único documento de leitura obrigatória no início da sessão.** Só mora aqui o que muda a cada
> fatia. O resto se lê quando o assunto exigir: convenções em `CONVENCOES.md`, regra de
> comportamento em `regras/`, decisão em `arch/`, débito em `fontes/debitos.md`, migration em
> `migrations/INDICE.md`, fatia fechada em `historico/`.

_Atualizado em 2026-10-27 (**motor v3 — fatia 9 fechada**: o pipeline Kotlin bate com o oráculo nos **720 perfis** (vaga a vaga, músculo a músculo, disparo a disparo) e o banco bate com o `catalogo_banco.csv` (147 linhas, campo a campo). Oráculo movido para `server/src/test/oracle/`. Antes dela: fatia 8, fatia 7, fatia 6, fatia 5, fatia 4, fatia 3, fatia 2, fatia 1, notificações offline (cache-first), fatia 0, acessórios no onboarding fechadas. Narrativa em `historico/`._

## Agora

| | |
|---|---|
| Última fatia fechada | **Motor v3 — fatia 9 (medição e invariantes)**, 2026-10-27. Entregou: oráculo em `server/src/test/oracle/` com `referencia_720.txt` GERADA e teste de frescor; `MotorV3Varredura720Test` (720/720, sem Docker); invariantes independentes do oráculo; banco = CSV no integrationTest; `ProgramAudit.avisos()` (causa tipada do aviso de honestidade); `inputs.files` do Gradle. Pergunta aberta do `modelo_a_mao` fechada (era o coeficiente sem fonte, não a frequência). Verde: **`:server:test` 681, oráculo 38, integrationTest `MotorV3Fidelidade` 2**. Narrativa em `historico/motor-v3-fatia-9.md`. Antes dela: **fatia 8** (`historico/motor-v3-fatia-8.md`), **fatia 7** (2026-10-24), **fatias 1–6** (2026-10-23), notificações offline e acessórios (2026-10-22), **fatia 0** (2026-10-21) |
| Migration no código | **V68** (`V68__core_sai_do_motor.sql`) — aperta `chk_exercises_volume_target` para recusar `'CORE'`, com bloco `DO` que falha alto se alguma linha ainda usar o alvo. Antes dela, **V67** (`V67__rir_minimo_de_seguranca.sql`) — `exercises.rir_minimo_seguranca`, 8 exercícios `true`, autoconferência no fim. V64/V65/V66 **commitadas e mergeadas em `main`** (PR #58, 2026-10-23); V67 local, não commitada |
| Schema local | `9.sqm` (`8.sqm` = acervo do Aprender/Fase 8, `9.sqm` = tabela `notificacao`) |
| Catálogo | **893 exercícios** depois da V65 (922 antes: 29 saíram, duplicados e um aparelho passivo). **147 na lista principal**, com `volume_target`, `prioridade`, `relevancia`, `nivel_maximo` e `ambiente`; família e os seis `requires_*` nos 893 |
| Não commitado | **A Fatia 9 inteira** (verde), esperando seu commit e push: mudança de `tools/motor_sim` para `server/src/test/oracle/` (git renames), `.gitignore`, `server/build.gradle.kts`, `ProgramAudit.kt` + testes, `MotorV3Varredura720Test`, integrationTest reescrito, docs e PDFs regenerados. **V67 e V68 seguem locais, não commitadas** (a Fatia 8 foi commitada em `3a90d5e`/`9c98381`). Resta ver `git status` da fatia J.5 + Dúvidas Frequentes, de antes |
| Achado ao fechar | **2026-10-27, Fatia 9:** (1) referência embutida em teste envelhece em silêncio — os 12 perfis do integrationTest divergiram todos após a Fatia 8 e nada acusou; agora é arquivo gerado com teste de frescor. (2) Teste que lê arquivo fora de `src/test/kotlin` precisa declará-lo ao Gradle, senão `UP-TO-DATE` dá verde sem rodar. (3) Dois lados concordando não prova nada: os invariantes da varredura não olham para o oráculo. Antes: achados da Fatia 8 em `historico/motor-v3-fatia-8.md`; notificações offline em `historico/notificacoes-offline.md`; acessórios em `ARCH #38` |
| Em andamento | **Nada no motor — a fatia 8 fechou.** Paralelo, sem relação: **varredura de débitos A–D** (`trabalho/debitos-A-D.md`), A/B/C fechados em 2026-10-09, **D aguardando o Rafael** |
| Motor de geração | **Especificação pronta, fatias 0–9 fechadas, 10–12 por fazer.** A spec vive em `propostas/motor-v3-ESPECIFICACAO.md`; o desenho e as fatias em `trabalho/motor-v3-desenho-e-fatias.md`. O que roda hoje em produção ainda é a geração antiga (`StructureEngine` + `SlotFiller`, `Random(seed)`) — `engine.v3` existe em paralelo, sem fiação. `server/src/test/oracle/` é o oráculo (38 testes verdes) e escolhe pela curadoria. **Condição da troca de produção:** até a Fatia 10, todo 60+ fica em modo conservador permanente — ou a Fatia 10 vem antes, ou você aceita ao ligar (`fontes/debitos.md`). `regras/01-motor-de-geracao.md` tem cinco `[INV]` marcados `[VENCIDO]` |
| Próxima fatia | **Motor v3 — Fatia 10, mesociclo e progressão**: foco +3/+6 do quiz, rastreio de mesociclo (tira o 60+ do modo conservador permanente). **Abertura:** reauditar `ombro_vira_isolamento` quando o foco entrar na matriz (o tripwire em `test_motor_v3.py` e `MotorV3Varredura720Test` fica vermelho). **Três decisões esperando você, nenhuma bloqueante:** (1) a **relevância do tríceps** — nenhum dos 180 perfis de academia recebe extensão acima da cabeça, o conserto é migration de curadoria, **exige CREF**; (2) as **duas defesas sem caso vivo** depois do CORE (`SEM_CATALOGO`, `V.mrv` nulável); (3) o **antebraço** com alvo > 0 e zero direto em 245–261/720 (entrar só por foco, Fatia 10). Todas em `fontes/debitos.md` |

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
