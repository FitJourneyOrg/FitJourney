# Motor v3 — desenho e quebra em fatias

_Aberto em 2026-10-13. Rascunho: não é fonte da verdade (ver README desta pasta)._

> ⚠️ `regras/01-motor-de-geracao.md` tem quatro `[INV]` marcados `[VENCIDO]` pela revisão. Cada
> fatia reescreve os seus ao entregar. **Não implemente contra um `[VENCIDO]`.**
>
> **O que este documento é:** o desenho do motor novo e a lista completa de fatias para construí-lo,
> em ordem de dependência. É a visão de engenharia.
>
> **O que ele não é:** a especificação de treinamento. Essa está em
> `propostas/motor-v3-ESPECIFICACAO.md`, revisada em cinco rodadas por um especialista, e é lá que
> mora o *porquê* de cada número. Aqui mora o *como*. Aquele arquivo é cópia de leitura do artifact
> `claude.ai/code/artifact/f664e0d0-68f3-4657-9036-71cc5fe5d6c2`, onde ficam as threads do revisor;
> o desenho anterior está em `historico/motor-redesenho-v2.md`.
>
> **Cada fatia abre o seu próprio arquivo** em `trabalho/` quando começar, copiando o `MODELO.md`.
> Este documento é o índice que diz qual vem depois de qual, e morre quando a última fechar.

---

## 1. Por que redesenhar, em um parágrafo

O motor atual prescreve primeiro e corta depois. Ele monta a semana somando volumes-alvo, descobre
no fim que não cabe, e apara o excesso por um laço que tira série de quem tem mais — sem olhar
prioridade, piso ou ordem de sacrifício. O resultado medido: músculos grandes abaixo do próprio
piso enquanto sobrava capacidade na sessão, e o recurso mais escasso — espaço no dia — gasto nos
músculos que menos precisavam dele, porque o motor não enxergava que toda remada já é trabalho de
bíceps. **A inversão é a mesma em todos os defeitos: o corte acontece depois da decisão, em vez de
ser a decisão.**

A revisão também nomeou os dois padrões de defeito que atravessam tudo, e eles são o critério de
projeto deste desenho:

1. **O mesmo número existe em várias versões** — alvo, provisório, alocado, entregue — e alguém lê
   da errada.
2. **Regras escritas contra valores que ninguém verificou se acontecem.** Duas regras de segurança
   existiam no papel e nunca disparavam.

O desenho abaixo responde ao primeiro com **um único ponto de medição**, e ao segundo com
**contagem de disparos por regra como teste**.

---

## 2. O desenho

### 2.1 O pipeline, com o laço

```
                              perfil do usuário
                                     │
          ┌──────────────────────────▼──────────────────────────┐
          │ [1] ALVOS           alvo(m) por músculo             │
          │     tabela de volume × nível × objetivo × idade     │
          └──────────────────────────┬──────────────────────────┘
                                     │
          ┌──────────────────────────▼──────────────────────────┐
     ┌───▶│ [2] CAPACIDADE      dias × teto da sessão           │
     │    │     teto = min(tempo, nível × objetivo × idade)     │
     │    └──────────────────────────┬──────────────────────────┘
     │                               │
     │    ┌──────────────────────────▼──────────────────────────┐
     │    │ [3] ALOCAÇÃO        alocado(m)                      │
     │    │     piso p/ todos → excedente por prioridade →      │
     │    │     rebaixamento → sacrifício na ordem declarada    │
     │    └──────────────────────────┬──────────────────────────┘
     │                               │
     │    ┌──────────────────────────▼──────────────────────────┐
     │    │ [4] ESTRUTURA       divisão, frequência efetiva,    │
     │    │     quais músculos em cada dia, ordem dos dias      │
     │    └──────────────────────────┬──────────────────────────┘
     │                               │
     │    ┌──────────────────────────▼──────────────────────────┐
     │    │ [5] COMPOSIÇÃO      prescrito(m): exercícios,       │
     │    │     papéis, 2 a 4 séries cada, ordem de execução    │
     │    └──────────────────────────┬──────────────────────────┘
     │                               │
     │    ┌──────────────────────────▼──────────────────────────┐
     │    │ [6] PREENCHIMENTO   entregue(m): exercício real do  │
     │    │     catálogo em cada vaga, âncoras fixas no ciclo   │
     │    └──────────────────────────┬──────────────────────────┘
     │                               │
     │    ┌──────────────────────────▼──────────────────────────┐
     │    │ [7] MEDIÇÃO         indireto, atendimento,          │
     │    │     cobertura, duração — TUDO sobre o entregue      │
     │    └──────────────────────────┬──────────────────────────┘
     │                               │
     │    ┌──────────────────────────▼──────────────────────────┐
     │    │ [8] VERIFICAÇÃO     invariantes, regras de          │
     │    │     segurança, aviso de honestidade                 │
     │    └──────────────────────────┬──────────────────────────┘
     │                               │
     └───────── estourou o tempo? ───┤  teto − 1, refaz de [2]
               (o teto só desce,     │  (termina sempre)
                então o laço termina)▼
                                 programa
```

### 2.2 As quatro decisões de arquitetura

**A. Orçamento antes da prescrição.** A capacidade da semana é calculada em [2], antes de qualquer
série ser prescrita. Nada em [3] a [6] pode gastar o que não existe. Isso elimina a classe inteira
de defeitos do motor atual.

**B. Um único ponto de medição.** Todo número que alguém lê — indireto, atendimento, cobertura,
duração, gatilho de aviso, invariante — é calculado em [7], **sobre `entregue`**. As etapas [1] a
[6] produzem estado intermediário que **ninguém mais lê depois**. É a resposta ao padrão 1.

> O motor atual viola isso: ele mede o volume indireto sobre uma estrutura provisória e nunca
> recalcula. Na saída medida, o glúteo doa 1,6 série de crédito ao posterior tendo recebido zero.

**C. O corte é a decisão, não a sobra.** Não existe poda em lugar nenhum. Quando não cabe, o
caminho é sempre [3]: rebaixa ao piso, depois sacrifica na ordem declarada. O arredondamento de
`alocado` para séries inteiras também segue a ordem de prioridade.

> O motor atual viola isso duas vezes: no laço de amputação do `StructureEngine`, e de novo na
> passagem de alocado para exercício, onde costas perde 4,9 séries e panturrilha ganha 1,7.

**D. Regra de segurança é predicado sobre propriedade, nunca lista de nomes.** "RIR mínimo 2 em
barra livre que carrega a coluna ou passa sobre a cabeça" é uma função do exercício. A enumeração
equivalente deixava 12 dos 20 exercícios com barra de fora. É a resposta ao padrão 2.

### 2.3 As grandezas, e quem pode ler cada uma

A tabela abaixo é o contrato que impede o padrão 1. **A coluna da direita é regra, não convenção.**

| grandeza | produzida em | quem pode ler |
|---|---|---|
| `alvo(m)` | [1] | [3] como teto da alocação; [7] como denominador do atendimento |
| `capacidade` | [2] | [3] e o laço |
| `alocado(m)` | [3] | [4] e [5] |
| `prescrito(m)` | [5] | [6] |
| `entregue(m)` | [6] | **[7] e [8], e mais ninguém** |
| `indireto(m)` | [7], a partir de `entregue` | [7] e [8] |
| `direto(m)` = max(0, alvo − indireto) | [7] | [7] e [8] |
| `atendimento(m)` = (entregue + indireto) / alvo | [7] | [8] e a tela |

**A circularidade é real e o laço a resolve.** `direto` depende de `indireto`, que depende do
programa, que depende do teto. Não existe fórmula fechada: o ponto fixo é alcançado pelo laço de
[2] a [8], e é por isso que o teto só pode descer.

### 2.4 Onde isso cai no código

| etapa | arquivo | estado |
|---|---|---|
| [1] | `VolumeModel.kt` | reescrever: tabela nova, fatores no teto |
| [2] | **novo** `CapacityModel.kt` | não existe |
| [3] | `StructureEngine.kt` | reescrever: water-fill no lugar do laço de amputação |
| [4] | `WeekSpread.kt` + `StructureEngine.kt` | ajustar |
| [5] | **novo** `SessionComposer.kt` | hoje está diluído no `StructureEngine` |
| [6] | `SlotFiller.kt` | reescrever: âncoras fixas, seed do perfil |
| [7] | **novo** `ProgramAudit.kt` | não existe — é a peça que falta |
| [8] | **novo** `SafetyRules.kt` + invariantes no `ProgramAudit` | não existe |
| laço | `DeterministicWorkoutGenerator.kt` | passa a orquestrar o laço |
| catálogo | migration + `ExercisePreFilter.kt` | fatia 0 |

---

## 3. As fatias, em ordem de dependência

Cada uma cabe em uma sessão. **A ordem é de dependência, não de importância** — e nenhuma pode
começar antes da anterior fechar, com uma exceção marcada.

### Fatia 0 — catálogo e medição confiável
`trabalho/motor-fatia-0-catalogo-e-medicao.md` — **já planejada**

Casamento músculo↔exercício determinístico (hoje QUADS casa por padrão de movimento e GLUTES por
etiqueta, e os dois se sobrepõem no afundo e no agachamento); simulador corrigido e commitado; os
dois testes do revisor no simulador.

**Verifica-se por:** o simulador reproduz demanda direta de 44,0 · 86,0 · 113,0.
**Bloqueia:** todas as outras. Sem ela, nenhuma medição vale.

> **Débito herdado (decisão do Rafael, 2026-10-23): os 23 exercícios sem taxonomia completa
> entram AQUI, não em `debitos-A-D.md` grupo D.** A V57 preencheu `primary_muscles` (saem do
> filtro da Biblioteca e contam volume), mas `modality`/`movement_pattern`/`equipment`/`level`
> continuam `NULL` -- seguem fora do `WHERE modality IS NOT NULL` que SELECIONA exercício pra
> treino, hoje e no motor v3 (nenhuma fatia 1-12 redesenha esse filtro). **Bloqueia de verdade a
> Fatia 3** (alocação por prioridade): alocar por músculo sem esses 23 no pool mede cobertura
> contra um catálogo que não é o real. Curar antes de abrir a Fatia 3, não precisa ser antes da 1
> ou 2 (que não selecionam exercício, só calculam números). Ver `fontes/debitos.md` ("Os 23 novos
> entram sem taxonomia").

---

### Fatia 1 — alvos e volume indireto
Etapa [1] e a tabela de indireto que [7] vai usar.

- Tabela de volume nova por nível (braço a 12 no intermediário, abdômen a 0 até o catálogo ter).
- Fatores de objetivo e idade multiplicando **o teto da sessão**, não só o alvo semanal.
- Tabela de volume indireto: composto credita 0,5 ao sinergista, com as duas exceções
  (ombro não recebe desconto; glúteo→posterior é 0,2).
- Função `demandaDireta(alvo, indireto)`.

**Depende de:** 0. **Verifica-se por:** Tier 1 puro, com os totais 61 · 111 · 148 e a demanda
direta da fatia 0.

---

### Fatia 2 — capacidade e teto da sessão
Etapa [2]. Peça nova.

- `teto = min(teto por tempo, teto por nível × fator objetivo × fator idade)`.
- Teto por tempo = `(minutos − overhead) ÷ 3`, overhead **16 min** em full body (quatro compostos
  de barra) e **11 min** em upper/lower e PPL (dois). A rampa **substitui** o aquecimento fixo.
- Teto por nível 16/18; objetivo manter 0,6; idade 0,75 para 60+ iniciante ou 1º mesociclo.
- Teto fisiológico de 11 séries fracionadas por músculo por sessão.

**Depende de:** 1. **Verifica-se por:** a tabela de cobertura do documento, célula a célula.

> Hoje `capacity_sets()` devolve 92,2 séries/semana — 23,1 por sessão — e **não existe teto por
> sessão nenhum**. É o defeito de maior alcance ainda aberto.

---

### Fatia 3 — alocação por prioridade
Etapa [3]. O coração do redesenho.

- Piso para todo músculo treinado; excedente proporcional à prioridade, com peso extra à ênfase.
- Rebaixamento: um único exercício no piso do papel — 3 no composto, 2 no isolamento.
- Sacrifício na ordem declarada: antebraço, panturrilha, abdômen, glúteo, bíceps, tríceps, ombro.
  Músculo em ênfase nunca sai.
- **Arredondamento de alocado para séries inteiras na ordem de prioridade.**
- Recebe `direta`/`indireto`/`capacidade` já resolvidos — **não inclui o ciclo do ponto fixo do
  indireto** (ele precisa de séries de composto medidas sobre o entregue, que só existem com a
  composição — ver Fatia 5) nem a troca do `StructureEngine` em produção (ver nota no fechamento
  da fatia, abaixo da tabela de código, §2.4).

**Depende de:** 2. **Verifica-se por:** invariante — nunca sobra tempo suficiente para o menor
exercício de um músculo removido; e nenhum músculo ativo abaixo do piso sem ter sido rebaixado.

> **Correção de 2026-10-23 (ao abrir a fatia):** a tabela §2.4 abaixo atribuía a esta fatia a
> troca do `StructureEngine` em produção. Isso contradizia a condição do próprio Rafael — "só
> liga depois do diferencial contra o simulador verde" — porque o diferencial só fecha na Fatia
> 9. A troca de produção (e a remoção do `VolumeModel.kt` antigo) **passa para depois da Fatia
> 9**. Narrativa completa em `historico/motor-v3-fatia-3.md`.

> **Correção de 2026-10-23 (plano da fatia 3, confirmada pelo Rafael):** a troca do
> `StructureEngine` em produção **não é desta fatia** — move para depois da Fatia 9, quando o
> diferencial contra o simulador estiver verde em Kotlin. Ligar produção aqui rodaria alocação
> nova com estrutura/composição/preenchimento ainda velhos. `VolumeModel.kt` antigo sai na mesma
> hora que `StructureEngine` for trocado, não antes. A tabela da §2.4 (`[3] StructureEngine.kt
> reescrever`) descreve o DESTINO do código, não quando ele entra em produção.

> **Amendada pela Fatia 8 (`[REGRA]`, 2026-10-24):** `alocar()` ganhou uma fase de corte
> proporcional entre o sacrifício e a redistribuição de excedente — quando sacrificar tudo de
> `ORDEM_SACRIFICIO` não basta, os 4 músculos de fora dela (CHEST, BACK, QUADS, POSTERIOR) são
> reduzidos proporcionalmente ao inverso do `PESO_PRIORIDADE`, em vez de ficarem intocados. Isso
> inverte o `[INV]` original desta fatia ("esses 4 nunca são rebaixados nem sacrificados"):
> agora `soma(resultado) ≤ capacidade` é garantido sempre. Decisão de prescrição do Rafael,
> medida nos 720 perfis antes de decidir entre quatro saídas — narrativa completa em
> `historico/motor-v3-fatia-8.md`. Implementado no oráculo e no Kotlin (`PriorityAllocation`,
> `Allocation.kt`); `AllocationTest.kt` ganhou 2 testes novos para o corte proporcional.

---

### Fatia 4 — estrutura da semana (fechada em 2026-10-23)
Etapa [4].

- Divisão a partir do número de dias (2–3 = FULL_BODY, 4 = UPPER_LOWER, 5 = UL_PPL, 6 = PPL;
  fora de 2..6, erro explícito).
- Frequência efetiva: `max(1, min(dias que treinam, total ÷ piso do papel))`, sem fatiar
  composto abaixo do piso.
- Quais músculos em cada dia. **Calendário (rótulo de dia da semana) ficou fora de escopo** —
  concern de agenda/apresentação, não de motor.

**Depende de:** 2 **e** 3 — `distribuirPelosDias()` recebe a saída de `alocar()`, não só a
capacidade. **Verifica-se por:** frequência efetiva ≥ 1 para todo músculo com volume.
**Auditoria registrada aqui ao fechar:** 154/720 perfis (21,4%) excedem a capacidade — ver
contexto na Fatia 8, abaixo. **Recalculado na Fatia 7 (2026-10-23), após alinhar a convenção do
mesociclo com a `[REGRA]` da Fatia 2 (nulo = 1º): 176/720 (24,4%).**

---

### Fatia 5 — composição da sessão (fechada em 2026-10-23)
Etapa [5]. Peça nova, hoje diluída no `StructureEngine`.

- Exercícios por músculo por sessão: até 4 séries → 1; 5 a 8 → 2; 9 ou mais → 3.
- **De 2 a 4 séries por exercício**, enchendo o primeiro até 4 e passando ao próximo, sem deixar
  nenhum abaixo do piso do papel.
- Papéis: 1 = âncora; 2 = âncora + isolamento; 3 = âncora + acessório + isolamento.
- Regra do ombro: desenvolvimento cede espaço ao composto de peito do dia (inclusa aqui — não
  precisa de catálogo).

**Depende de:** 4. **Verifica-se por:** `SessionComposition.comporSessao()` contra
`compor_sessao()` do oráculo, com as saídas reais de `distribuirPelosDias()` da Fatia 4.

> Hoje o motor dá exatamente 3 séries a todo composto e joga o resto no isolamento. Nunca foi
> escrito em lugar nenhum — foi descoberto medindo.

> **Correção de escopo ao abrir (2026-10-23): o ponto fixo do indireto SAI desta fatia e entra
> na abertura da Fatia 6.** `gerar()` do oráculo não dá ponto fixo em [3]–[6] como este texto
> dizia — dá a volta completa, chamando `preencher()` (catálogo) a cada iteração, porque
> `compostos_semana` é medido do exercício REAL escolhido, não do papel assinalado em
> `compor()`. Medido: **293/720 perfis (40,7%) divergem** entre papel e composto real, soma de
> 1813 séries — não é simplificação aceitável, ver detalhe na Fatia 6, abaixo. **Recalculado na
> Fatia 7: 289/720 (40,1%), soma de 1767 séries**, após alinhar a convenção do mesociclo. **O calendário
> (rótulo de dia da semana) também não é desta fatia** — decisão já registrada na Fatia 4.

---

### Fatia 6 — preenchimento e continuidade (fechada em 2026-10-23)
Etapa [6]. **Abre com o ciclo do ponto fixo do indireto**, movido da Fatia 5 em 2026-10-23:
indireto → alocação → estrutura → composição → preenchimento → compostos de verdade → indireto
de novo, até convergir. Só pode existir aqui porque é a primeira etapa com séries de composto
medidas sobre o exercício REAL escolhido no catálogo (Decisão B) — antes do catálogo, só existe
papel, e papel não é composto entregue (divergem em 40,7% dos perfis — 40,1% após o recálculo da Fatia 7 — Fatia 5).

- Seleção determinística: seed derivado do perfil, não aleatório por geração.
- **Âncoras fixas dentro do mesociclo** — é o que torna a progressão comparável.
- Âncora de costas com apoio; remada curvada restrita ao avançado.
- Nenhuma vaga vazia: se o catálogo não tem candidato, o músculo não devia ter sido alocado.
- **Limite de iterações do ponto fixo: 30** (mesmo valor do oráculo). Sob escassez de iterações
  a regra que fecha é o estado de menor indireto total, com o disparo `ponto_fixo_oscilou` — nos
  720 perfis, `ponto_fixo_nao_convergiu` (bate as 30) nunca disparou; o máximo observado foi 5
  (6 após o recálculo da Fatia 7 — ver abaixo).
  **Para o teste provar os três caminhos (converge, oscila, esgota as 30 iterações) sem disparo
  zero, o caso de esgotamento vai precisar de um perfil construído, não um dos 720** — nenhum
  perfil real chega lá.

> **Medido ao fechar a Fatia 5 — por que a divergência papel×entregue muda com o ambiente**
> (4 cenários de **360 perfis cada** — mesma base de 2 a 6 dias/objetivo/nível/minutos/idade,
> variando só ambiente/acessórios; ACADEMIA + "CASA sem acessório" juntos reconciliam com o
> 293/720 da primeira medição, porque o 720-matriz original usa `acessorios=None`, que vira
> "tudo" em academia e "nada" em casa — são os mesmos dois cenários, divididos):
>
> | cenário | n | oscilou | vagas vazias | diverge | séries de diferença |
> |---|---|---|---|---|---|
> | ACADEMIA | 360 | 20 (19 após Fatia 7) | 0 | 0 | 0 |
> | CASA sem acessório | 360 | 4 (6) | 134 (131) | 293 (289) | 1813 (1767) |
> | CASA com banco | 360 | 8 | 0 | 299 (295) | 1466 (1410) |
> | CASA com tudo | 360 | 20 (19) | 0 | 27 | 54 |
>
> Valores entre parênteses: recalculados na Fatia 7 (2026-10-23), após alinhar a convenção do
> mesociclo (nulo = 1º) com a `[REGRA]` da Fatia 2 -- o oráculo usava `primeiro_mesociclo: bool
> = False` sem nenhum perfil da matriz setá-lo, diferente do Kotlin.
>
> 0 (ACADEMIA) + 293 (CASA sem acessório) = **293/720**, igual à primeira medição; 0 + 289 = 289
> após o recálculo. Academia
> nunca diverge. Em casa, até com todo acessório sobra divergência (27/360) — é o
> `EQUIPAMENTO_CASA` (o que falta não é só acessório, é equipamento de carga de academia).
>
> **Causa corrigida (medida, não suposta): "casa com banco" diverge MAIS que "casa sem
> acessório" (299 > 293) com ZERO vagas vazias — não é o fallback de vaga vazia, é troca de
> papel na direção contrária.** Checando por músculo: BACK em 272/360 (268/360 após Fatia 7),
> POSTERIOR em 56/360 (48/360), CHEST em 27/360 (sem mudança) — sempre papel pedindo ISOLAMENTO
> e saindo um exercício COMPOSTO (o real
> entregue fica MAIOR que o papel estimava, nunca menor). Casa com banco não tem máquina de
> isolamento pra costas/posterior/peito (remada curvada isolada, cadeira extensora, crucifixo) —
> só tem banco + barra/halter, que são equipamento de composto. Quando falta candidato
> ISOLAMENTO, o fallback do `preencher()` enfia um COMPOSTO na vaga, puxando o composto real
> pra cima do que o papel previa.
>
> **[PROPOSTA] virou [REGRA] (Rafael, 2026-10-23)** — não é ela que explica os ~40% (a causa é
> troca de papel, acima), mas a decisão ficou registrada: inverte o tipo nos dois sentidos,
> respeita família, vaga vazia só sem candidato nenhum depois da inversão, disparo
> `papel_trocado`, ordem final pelo tipo entregue (composto antes de isolado).

**Depende de:** 5. **Verifica-se por:** cinco gerações do mesmo perfil produzem o mesmo programa;
zero vagas vazias em academia; ponto fixo convergindo/oscilando/esgotando com disparo medido em
cada caminho.

**Fechamento (2026-10-23):** `CatalogPool` (pré-filtro puro, não é o `ExercisePreFilter` de
produção) + `CatalogFiller` (`preencher()` + fallback + reordenação) + `PontoFixoIndireto`
(mecânica do laço, testável com `passo` falso — é o que permite provar os 3 caminhos sem
depender de um dos 720 perfis reais) + `MotorV3Pipeline` (orquestra as Fatias 1–6, testado fim a
fim contra o oráculo com um catálogo pequeno). "Seed determinístico" e "âncoras fixas dentro do
mesociclo" (bullets originais acima): confirmado que o oráculo não sorteia nada (determinístico
de graça) e não rastreia mesociclo — registro para a Fatia 10 abaixo.

> **Registro para a Fatia 10 (continuidade entre programas):** dentro de UM programa, os
> exercícios escolhidos pelo `preencher()` ficam fixos — reaproveitar a mesma saída em todas as
> semanas/mesociclos do mesmo programa, nunca re-rodar o preenchimento a cada semana. A troca de
> exercício (progressão, variação) só acontece quando um NOVO programa é gerado — isso é decisão
> e código da Fatia 10, não desta.
>
> **→ Fechado na Fatia 10 (2026-10-28):** `CicloDoMesociclo.semanaDoPrograma` lê a saída do
> `preencher()`; a única exceção é a troca de âncora do iniciante (`[REGRA]` em
> `regras/01-motor-de-geracao.md`, "Fatia 10").

---

### Fatia 7 — regras de segurança
Etapa [8], parte 1. Peça nova.

- RIR mínimo 2 **por propriedade**: barra livre que carrega a coluna ou passa sobre a cabeça.
- Troca de barra por halter ou máquina para o iniciante, pela mesma propriedade.
- Regra do ombro: desenvolvimento com no máximo `(10 − composto de peito do dia)` séries; abaixo
  de 3 a vaga vira elevação lateral e a sobra vai para deltoide posterior; no máximo um empurrar
  com barra livre por sessão.
- Rampa automática antes do primeiro exercício de cada padrão pesado: 3 séries de calibração a ~50%×8, 70%×4, 85%×2 da carga de trabalho -- não entra no volume, entra no tempo da sessão.
- Substituições articulares por região, com janela de 12 meses.
- Modo conservador: RIR nunca abaixo de 3, por preferência declarada, valendo um mesociclo.

**Depende de:** 5 (a regra do ombro precisa da composição). **Verifica-se por:** contagem de
disparos — **regra com zero disparos falha o teste**.

> **Rampa corrigida ao abrir esta fatia (2026-10-23):** a redação antiga ("3 séries no primeiro
> composto de barra, 2 em barra livre de outra região, 1 em máquina") e a da especificação
> (`motor-v3-ESPECIFICACAO.md`, "Aquecimento") **não descrevem a mesma rampa** -- são
> incompatíveis, não duas formas de dizer a mesma coisa. A antiga varia a *quantidade de séries*
> por posição/equipamento do exercício na sessão (3→2→1, implicando uma rampa por exercício
> pesado). A especificação é uma rampa de **carga**, sempre 3 séries (50%×8, 70%×4, 85%×2),
> **uma vez por padrão pesado** (não por exercício, não por posição), e explicitamente fora do
> volume mas dentro do tempo da sessão. Pela regra do projeto (especificação vence, doc é
> corrigido), a linha acima foi substituída pela versão da especificação.

> **`ombro_vira_isolamento`, registrado ao abrir esta fatia (2026-10-23):** zero disparos nos 720
> perfis reais da matriz (confirmado), mas **não é regra morta por construção** -- já está
> protegida por teste unitário com entrada construída
> (`SessionCompositionTest."regra do ombro vira isolamento quando o peito do dia nao deixa
> espaco minimo"`, CHEST=11 série/dia -> composto 4 + acessório 4 = peito 8 -> dispara).
> Inalcançável nos 720 perfis **sem foco** -- máximo observado de `peito_composto_do_dia` é 7,
> precisa de 8. Decisão do Rafael: não mudar 10/3 (`OMBRO_LIMITE`/`OMBRO_MINIMO_COMPOSTO`); medir
> de novo quando o foco (+3/+6, ver Fatia 10) entrar -- é o candidato mais provável a cruzar a
> linha.


---

### Fatia 8 — corte por tempo
O laço.

- Estimar a duração do programa gerado: execução, descanso prescrito, rampas, trocas.
- Se estourar, baixa o teto em 1 série e refaz de [2]. O teto só desce, então termina.
- O corte percorre a cascata de [3]. **Nunca uma poda no fim.**

**Depende de:** 7. **Verifica-se por:** nenhuma sessão acima do tempo informado; e a ordem de
prioridade preservada entre a primeira e a última iteração. **Critério de pronto: 0/720 perfis
da matriz acima da capacidade** — é o número que substitui o 176/720 (era 154/720 antes do
recálculo da Fatia 7; e o 297/720 do pipeline completo, Fatia 6, que não mudou) medidos antes
desta fatia existir.

> **Contexto medido ao abrir a Fatia 4 (2026-10-23):** auditoria dos 720 perfis
> (`test_motor_v3.py::PERFIS`) contando `soma(alocado) > capacidade` deu **154/720 (21,4%) —
> perfil comum, não extremo.** Concentrado em poucos dias (108 em 2 dias, 43 em 3, 3 em 4, zero
> em 5 e 6); quase igual entre níveis (BEGINNER 54, INTERMEDIATE 48, ADVANCED 52). Excesso
> máximo: 10,67 séries. É exatamente o caso que este laço existe para resolver — as Fatias 4 e 5
> processam `alocado` como ele vem, inclusive excedendo a capacidade, porque hoje não há outro
> mecanismo de corte.

> **Reauditoria ao fechar a Fatia 5 (2026-10-23):** a composição não muda o 154/720 — é
> soma-preservante por construção, confirmado em 720/720 perfis sem exceção. **Causa estrutural
> medida:** a soma do piso de TODOS os músculos treináveis (61 séries/semana, coluna iniciante)
> excede a capacidade em **100% dos perfis de 2 dias (144/144) e de 3 dias (144/144)** — 61
> séries não cabe no teto físico (18/sessão) × 2 ou 3 dias, de jeito nenhum. É `alocar()`
> sacrificando os músculos de `ORDEM_SACRIFICIO` (tudo exceto CHEST/BACK/QUADS/POSTERIOR) que
> evita que os 288 (144+144) virem excesso de verdade — e ainda assim 154 continuam acima.
> **O piso de todos os músculos (61 séries/semana) passa da capacidade em 100% dos perfis de
> 2–3 dias — 288/288** (144 de 2 dias + 144 de 3 dias, sobre os 720 da matriz). É decisão
> estrutural da especificação, pendente com o Rafael, não um bug de implementação.
> **Decisão pendente do Rafael, na especificação, antes de implementar o corte desta fatia:**
> 2–3 dias cabe o piso de todo mundo, ou a especificação aceita que nem todo músculo treina
> sempre (reduzir a divisão FULL_BODY, ou abrir excepão de frequência abaixo da semanal)? Não
> implementar o corte sem essa resposta — o laço de tempo (baixar o teto 1 série por vez) não
> resolve um déficit estrutural de 61 vs. no máximo ~54 séries de capacidade.
> **Achado adicional, fora do escopo da Fatia 5, registrado para esta fatia:** medindo sobre as
> sessões reais do pipeline completo (com catálogo e ponto fixo, Fatia 6), o excesso sobe para
> **297/720 (41,2%)** — quase o dobro do 154/21,4%. A diferença é o catálogo (vagas vazias,
> fallback do `preencher()`), não a alocação pura.

> **Recalculado na Fatia 7 (2026-10-23), após alinhar a convenção do mesociclo (nulo = 1º) com
> a `[REGRA]` da Fatia 2** -- o oráculo tratava `primeiro_mesociclo` como `bool = False` sem
> nenhum perfil da matriz setá-lo, diferente do Kotlin (`CapacityModel.fatorIdade`). Corrigido
> (`mesociclo_atual: Optional[int] = None`). Resultado: **154/720 → 176/720 (24,4%)** por
> `alocado`; 108+43=151 de 2-3 dias → 120+53=173 (3 em 4 dias, sem mudança); por nível BEGINNER
> 54 (sem mudança), INTERMEDIATE 48→60, ADVANCED 52→62; excesso máximo 10,67→13,8 séries.
> **297/720 (41,2%) por `entregue` NÃO mudou** -- 41,25% arredondado continua 41,2%, a correção
> confirma o número em vez de alterá-lo. **288/288 (piso de todos vs. capacidade) também não
> mudou** -- capacidade menor só reforça a conclusão. O critério de pronto desta fatia (0/720)
> não muda de forma, só a régua: agora é 176/720, não 154/720.

> **Decisão pendente resolvida em 2026-10-24:** entre as quatro saídas propostas (piso só pros
> grandes, alternar entre semanas, baixar o piso de composto, estender `ORDEM_SACRIFICIO`), o
> Rafael decidiu **(d) com corte proporcional por `PESO_PRIORIDADE`** — detalhe completo,
> números das quatro opções e da sensibilidade da rampa em `historico/motor-v3-fatia-8.md`.
> Implementado como amendamento à Fatia 3 (`alocar()`, oráculo e Kotlin). Medido nos 720: `6/720`
> `alocado > capacidade` (era 176/720) — resíduo de arredondamento de ~0,2 série, não resolvido
> por esta decisão; `130/720` `entregue > capacidade` (era 297/720). **4–6 dias confirmados sem
> mudança** (mesmos números de antes, por dias). Isso resolve o bloqueio de entrada da fatia —
> o laço de corte por tempo propriamente **ainda não foi desenhado nem implementado**, e a causa
> dos 130/720 (ou 47/288 em 2–3 dias) restantes **ainda não foi isolada** (fallback do catálogo
> ou outra causa — isolar com medição é o próximo passo desta fatia, antes do laço). Débito de
> produto registrado: aviso no onboarding para 2 dias/≤60 min (`fontes/debitos.md`).

---

### Fatia 9 — medição e invariantes
Etapa [7] e [8], parte 2. Peça nova, e é a que fecha o padrão 1.

> **FECHADA em 2026-10-27** — narrativa em `historico/motor-v3-fatia-9.md`. O oráculo mora em `server/src/test/oracle/` (saiu de `tools/motor_sim`, git renames); varredura dos 720, invariantes, banco = CSV e `ProgramAudit.avisos()` entregues. Texto/UI do aviso ficaram em `fontes/debitos.md`. A **condição da troca de produção** (60+ conservador até a Fatia 10) segue valendo.

- `ProgramAudit`: indireto, atendimento, cobertura e duração, **todos sobre `entregue`**.
- Invariantes como teste automatizado.
- Aviso de honestidade: gatilho é músculo grande abaixo do piso, com texto acionável.
- **Os dois testes do revisor, agora em Kotlin:** contagem de disparos por regra, e o diferencial
  entre o simulador e o motor falhando acima de 0,5 série por músculo.

> **Critério de pronto desta fatia, registrado ao fechar a Fatia 6 (2026-10-23):** auditoria
> completa dos 720 perfis rodando `CatalogFiller.preencher()` (via `MotorV3Pipeline`) contra o
> catálogo REAL do Postgres (via `ExerciseRepositoryImpl`), comparado linha a linha com o oráculo
> `motor_v3.py::gerar()` sobre o mesmo `catalogo_banco.csv`. Precisa de um parser de perfis em
> massa (os 720 do `itertools.product` já usados pelo simulador) e de uma forma de gerar/guardar
> a referência dos 720 sem acoplar o teste de catálogo ao de pipeline. Até isso existir, o que
> fecha a Fatia 6 é o substituto pequeno: `MotorV3FidelidadeCatalogoRealIntegrationTest` (12
> perfis -- 4 cenários de ambiente × 3 níveis -- contra o mesmo catálogo real).

> **Pergunta aberta, registrada ao abrir a Fatia 3 (2026-10-23):** `modelo_a_mao.py` assume
> frequência fixa 2× para todo músculo (simplificação do método de referência da especificação);
> `motor_v3.py` aplica a frequência efetiva de verdade (Fatia 4) e, para GLUTES no avançado
> (demanda direta 4,0/semana), isso muda o resultado — 1 sessão com 4 séries vira composto
> (credita 0,2×4=0,8 ao posterior) em vez de 2 sessões de 2 (abaixo do piso, 0 composto). É a
> causa exata dos 2 vermelhos de `test_diferencial_contra_o_modelo_a_mao` em `ADVANCED`. Esta
> fatia precisa decidir qual lado está certo — o teste não fica verde só de ajustar tolerância, e
> o modelo à mão não pode copiar a lógica do motor (viraria tautologia, contradizendo o propósito
> dele, declarado no topo do próprio arquivo).

> **Condição da troca de produção, registrada ao fechar a Fatia 7 (2026-10-24):** até a Fatia 10
> existir, TODO perfil 60+ fica no modo conservador (RIR nunca abaixo de 3) e com o fator de
> idade 0,75 PERMANENTEMENTE (`mesocicloAtual == null` conta como 1º mesociclo, `[REGRA]` da
> Fatia 2 -- sem rastreio de mesociclo, nenhum 60+ sai do 1º). Isto não é bug: é o lado seguro,
> documentado desde a Fatia 2. Mas é uma condição explícita da troca: **ou a Fatia 10 (mesociclo)
> vem antes da troca do `StructureEngine` em produção, ou o Rafael aceita isto explicitamente ao
> ligar** -- não é para passar em silêncio junto com o resto da troca de produção.
>
> **A varredura dos 720 perfis contra o catálogo real, em Kotlin, fica para esta fatia** (não
> antecipada na Fatia 7) -- mesma leitura já registrada no fechamento da Fatia 6 (critério de
> pronto desta fatia), agora também cobrindo os disparos de `modo_conservador_ativo`,
> `rir_2_por_propriedade` e `rir_3_modo_conservador` (medidos só no oráculo na Fatia 7 --
> 240/180/360 de 720 por gatilho, união 540; 123 perfis em RIR 2, 540 em RIR 3 -- ver
> `docs/regras/01-motor-de-geracao.md`).

- Recalcular tudo depois de cada troca pelos botões "senti dor" e "não tenho este equipamento".

**Depende de:** 8. **Pode começar antes:** a estrutura do `ProgramAudit` pode ser escrita junto
com a fatia 3, porque as invariantes dela é que dão o critério de pronto das fatias 3 a 8. Se a
sessão der, vale antecipar.

---

### Fatia 10 — mesociclo e progressão  ✅ FECHADA em 2026-10-28 (Kotlin verde; narrativa em `historico/motor-v3-fatia-10.md`)
- Mesociclo fixo em 4 semanas; séries fixas no ciclo, progressão por carga; deload na semana 4.
- Progressão dupla com as quatro regras, incluindo a tolerância pós-salto e a redução de 4% por
  ponto de RIR abaixo do alvo.
- Série de calibração com saltos por faixa (+10 / +25 / +50%, teto de +25% em barra livre, três
  primeiras exposições).
- Retorno após pausa, com a calibração obrigatória acima de 1 mês parado.
- Progressão e 1RM estimado guardados **por exercício**, não por vaga.
- **Foco (registrado ao abrir a Fatia 7, 2026-10-23):** `alvos()`/`VolumeTargets` não aplicam
  nenhum bônus de foco hoje -- o único rastro é o `[INV]` `[VENCIDO]` em
  `regras/01-motor-de-geracao.md:16` ("foco agora é +3 pequeno / +6 grande, como fase de dois
  mesociclos"), nunca implementado. `ProfileDto.focusAreas` já existe como dado; falta o
  mecanismo. **Reauditar `ombro_vira_isolamento` quando o foco entrar** -- nos 720 perfis sem
  foco, o máximo observado de `peito_composto_do_dia` é 7 (precisa de 8 para disparar); foco em
  peito pode ser o que falta para cruzar essa linha num perfil real.

**Depende de:** 6. **Verifica-se por:** Tier 2 com histórico falso, incluindo o caso de 20 kg →
60 kg fechando em três exposições.

---

### Fatia 11 — nível, promoção e ênfase
- Nível derivado, não autodeclarado; entrada máxima em intermediário.
- Promoção por deload como diagnóstico: estagna → deload com a **mesma âncora** → se volta a subir
  e estagna de novo, promove; se não volta, é recuperação e o app avisa.
- Promoção a avançado por 1RM estimado sem ganho em dois mesociclos, com âncoras congeladas.
- Ênfase como fase de dois mesociclos, demais grupos no piso, no máximo um grupo grande,
  +3 pequeno / +6 grande, puxar nunca abaixo de empurrar.
- **Condição herdada da Fatia 10 (2026-10-28) — o foco é permanente até esta fatia.** A Fatia 10
  entregou SÓ o bônus de alvo. A medição mostrou o bônus quase inerte: **21 de 480 programas mudam por
  foco e o `ATENDIDO` cai**, porque a capacidade da sessão é o limite. Isso confirma a **proteção
  contra sacrifício na alocação** como a peça que faz o foco funcionar — e ela é desta fatia. Ligar
  o motor v3 em produção entre a 10 e a 11 entrega um foco permanente e que **piora a cobertura de
  quem o escolhe**; ou esta fatia vem antes de ligar, ou o Rafael aceita isso explicitamente
  (mesmo tratamento do 60+ na Fatia 7).
- **Orçamento do bônus:** `LEGS` ficou em +6 repartido (3/2/1) por escolha; o "limite total" da spec
  e o piso nos demais grupos são desta fatia.
- **Por que o eixo de foco na matriz é nenhum / peito / ombro:** é o que o tripwire de
  `ombro_vira_isolamento` exige (peito dispara a regra: 16 disparos em 8 perfis; ombro não); os nove
  grupos custariam tempo e tamanho sem ganho. Arquivo à parte (`referencia_foco.txt`, 960 perfis).
  Ao implementar a fase, a matriz de foco precisa de novo eixo (fase 1/2) — reavaliar o conjunto.

**Depende de:** 10. **Verifica-se por:** Tier 2 com histórico falso dos três caminhos de promoção.

---

### Fatia 12 — questionário e triagem
- As perguntas novas: tempo de treino consistente, há quanto tempo parou (não parei · até 1 mês ·
  1 a 6 meses · mais de 6 meses), minutos disponíveis, dor ou lesão nos últimos 12 meses.
- PAR-Q como **autoavaliação sem armazenamento**, orientando procurar médico.
- Preferência de modo leve, valendo um mesociclo.
- "Casa" marcada "em breve" na primeira tela; botão "não tenho este equipamento" na academia.

**Depende de:** 11. **Verifica-se por:** Tier 3 de rota.

---

## 4. O que atravessa todas as fatias

- **Determinismo.** Mesmo perfil, mesmo programa. Vale como teste em toda fatia de [4] em diante.
- **Nada lê estado intermediário.** Revisar por isto em todo PR: se uma medição não veio do
  `ProgramAudit`, está errada.
- **Toda regra de segurança nova nasce com contagem de disparos.** Sem exceção.
- **Tier 1 em tudo que é função pura** — alvos, teto, alocação, composição são todos testáveis sem
  banco.

---

## 5. Fora de escopo de todas as fatias

Vai para `fontes/debitos.md` no fechamento.

- **Validação profissional:** tabela canônica do catálogo, o "10" da regra do ombro, e os pontos de
  partida da 2ª rodada (0,75 para 60+, piso de rebaixamento, limite de supino). Item de CREF.
- **LGPD** (consentimento para regiões de dor e botão de dor) e **Lei 9.696/98** (responsável
  técnico). Item de advogado.
- Versão limpa do documento para o profissional, com o histórico das rodadas em apêndice.
- Treino em casa com lista de equipamentos; deltoide em três porções; abdômen no catálogo;
  mais panturrilha; coeficiente de indireto por exercício; autorregulação no dia; fadiga entre
  sessões; objetivo de força; individualização pela resposta ao treino.

---

## 6. Ao fechar a última fatia

- [ ] `arch/motor-de-geracao-como-funciona.md` reescrito a partir da §2 deste documento
- [ ] `regras/01-motor-de-geracao.md` com as `[REGRA]` e `[INV]` de todas as fatias
- [ ] ADR das quatro decisões de arquitetura da §2.2 em `arch/`
- [ ] `historico/` com a narrativa das cinco rodadas de revisão e dos defeitos achados
- [ ] `fontes/debitos.md` com a §5
- [ ] **este arquivo apagado**
