# Estado — onde paramos

> **O único documento de leitura obrigatória no início da sessão.** Só mora aqui o que muda a cada
> fatia. O resto se lê quando o assunto exigir: convenções em `CONVENCOES.md`, regra de
> comportamento em `regras/`, decisão em `arch/`, débito em `fontes/debitos.md`, migration em
> `migrations/INDICE.md`, fatia fechada em `historico/`.

_Atualizado em 2026-10-29 (**motor v3 — fatia 11 fechada**: fase de ênfase de verdade (piso nos demais grupos + proteção dinâmica contra sacrifício na alocação), seleção de puxada horizontal semanal no BACK, e os diagnósticos de promoção de nível (iniciante→intermediário, intermediário→avançado) no oráculo e no Kotlin. **Fecha a condição de troca de produção** aberta na Fatia 9/10. `:server:test` 782 verdes, `:server:integrationTest` verde, oráculo 94. Migration V70 (`promotion_decline`) commitada. Antes dela: fatias 0–10 fechadas. Narrativa em `historico/`._

## Agora

| | |
|---|---|
| Última fatia fechada | **Motor v3 — fatia 11 (nível, promoção e ênfase)**, 2026-10-29. Entregou: piso nos demais grupos durante a ênfase (isenção nomeada do BACK) + proteção dinâmica contra sacrifício na alocação (`protegidosExtra`), seleção de puxada horizontal semanal no BACK (`WeeklyBackSelection`), diagnósticos de promoção (`PromocaoDeNivel`: iniciante→intermediário por deload-como-diagnóstico, intermediário→avançado por 1RM congelado, `podeSugerirDeNovo`), V70 (`promotion_decline`) + repositório. Grade nova `PD`/`PP`/`PA`/`PN` em `referencia_progressao.txt` (14.757 casos) + 23 testes à mão (`PromocaoDeNivelTest`). `:server:test` **782**, `:server:integrationTest` verde, oráculo **94**. Detalhe em `historico/motor-v3-fatia-11.md`; regras em `regras/01`, "Foco — fase de ênfase (Fatia 11)" |
| Migration no código | **V70** (`V70__recusa_de_promocao.sql`) — tabela `promotion_decline`, PK `(user_id, nivel_proposto)`, guarda só a recusa mais recente. Antes dela, **V69** (`progressao_por_exercicio`) e **V68** (`core_sai_do_motor`). V64–V69 **commitadas e mergeadas em `main`**; V70 local, não commitada |
| Schema local | `9.sqm` (`8.sqm` = acervo do Aprender/Fase 8, `9.sqm` = tabela `notificacao`) |
| Catálogo | **893 exercícios** depois da V65 (922 antes: 29 saíram, duplicados e um aparelho passivo). **147 na lista principal**, com `volume_target`, `prioridade`, `relevancia`, `nivel_maximo` e `ambiente`; família e os seis `requires_*` nos 893 |
| Não commitado | **Nada da Fatia 11** — três commits no `develop` (oráculo, espelho Kotlin, testes de promoção), `:server:test`/`:server:integrationTest` verdes, `git status` limpo. `docs/` e `tools/` continuam fora do git (promoção desta fatia feita manualmente nesta sessão) |
| Achado ao fechar | **2026-10-29, Fatia 11:** (1) `Oraculo.catalogo()` (teste Kotlin) nunca lia `movement_pattern` do CSV — fábrica de teste com default silencioso, invisível até `WeeklyBackSelection` precisar do campo; causa dos 1680/1680 perfis divergindo na varredura. (2) dois lados concordando não é prova, de novo: as outras 6 falhas eram expectativa congelada antes do piso de ênfase existir, não bug do motor. (3) a fase move `ombro_vira_isolamento` de 16 para 136 — efeito esperado, não regressão. (4) restrição de volume puxar×empurrar foi medida e rejeitada — o eixo certo era seleção, não volume. (5) scripts de medição ad hoc em `tools/cache/` violavam a própria regra do `tools/README.md` — movidos para `tools/motor_sim/` |
| Em andamento | **Nada no motor — a fatia 11 fechou.** Paralelo, sem relação: **varredura de débitos A–D** (`trabalho/debitos-A-D.md`), A/B/C fechados em 2026-10-09, **D aguardando o Rafael** |
| Motor de geração | **Especificação pronta, fatias 0–11 fechadas, 12 por fazer.** A spec vive em `propostas/motor-v3-ESPECIFICACAO.md`; o desenho e as fatias em `trabalho/motor-v3-desenho-e-fatias.md`. O que roda hoje em produção ainda é a geração antiga (`StructureEngine` + `SlotFiller`, `Random(seed)`) — `engine.v3` existe em paralelo, sem fiação. `server/src/test/oracle/` é o oráculo (94 testes verdes) e escolhe pela curadoria. **Condição da troca de produção: FECHADA nesta fatia** — a proteção contra sacrifício existe agora, o risco de foco permanente sem proteção não se aplica mais; LIGAR continua decisão sua. **Aberto, mas não bloqueante:** a "fase de 2 mesociclos" da ênfase estava no plano original da Fatia 11 e não foi implementada — o foco continua permanente (`fontes/debitos.md`) |
| Próxima fatia | **Fatia 11 fechada e verde.** O que ficou: **service/rota da promoção de nível, aprovado como sessão NOVA** (lê `PromocaoDeNivel` com histórico real, decide quando perguntar, grava `promotion_decline`) — é assunto novo, não resto de fatia. Dívida de produto consolidada num item só, **"O motor sabe, o app não mostra"** (`fontes/debitos.md`): RIR não chega do cliente, promoção sem service/rota, ênfase sem declarar custo, rótulos de manutenção/não-treinado sem UI — priorizável de uma vez. Depois: **Fatia 12 — questionário e triagem** (`trabalho/motor-v3-desenho-e-fatias.md`) |

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
