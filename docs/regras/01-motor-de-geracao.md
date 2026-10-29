# Motor de geração e gates (ARCH #20, #24, #26, #27, #28)

> Índice: `docs/regras/README.md` · Marcadores: [REGRA] imutável · [INV] invariante · [PROPOSTA] lacuna não ratificada.

> ⚠️ **Cinco `[INV]` desta página foram superados — quatro pela revisão do motor (2026-10-13), um
> pela decisão de 2026-10-21 sobre contraindicação — e estão marcados `[VENCIDO]` abaixo.** A
> especificação que os substitui é
> `propostas/motor-v3-ESPECIFICACAO.md`; o plano de implementação é
> `trabalho/motor-v3-desenho-e-fatias.md`. Cada fatia reescreve os seus ao entregar — até lá,
> **não implemente contra um `[VENCIDO]`**. Este aviso sai quando a última fatia fechar.

## Motor de geração (determinístico — ARCH #20)

Pipeline (sem LLM): ProfileDto → **StructureEngine** (esqueleto: volume por músculo → séries → papéis) → **ExercisePreFilter** (pool: ambiente + nível + acessórios) → **SlotFiller** (escolhe exercício por músculo+papel) → ProgramDto.

- **[INV]** [VENCIDO em parte: o foco virou BÔNUS DE ALVO na Fatia 10 (+3 pequeno / +6 grande, um por escolha — ver "Fatia 10" abaixo); a FASE de dois mesociclos é da Fatia 11] Volume é a variável-mãe (#26): séries efetivas/semana por músculo (RP/Israetel), calibradas por nível/objetivo; foco dá +3 (respeitando MRV). Idade calibra o volume por faixa + RIR conservador (#24).
- **[INV]** Só `modality='STRENGTH'` entra no pool. `is_base` primeiro (#28); variação só se a base acabar.
- **[INV]** [VENCIDO: o critério não cobre GLUTES, e o afundo casa em QUADS e GLUTES ao mesmo tempo — fatia 0] Perna fina e braço fino: QUADS/POSTERIOR casam por `movement_pattern`; BICEPS/TRICEPS/FOREARMS por `category` (Fatia B). Splits roteiam Push→tríceps, Pull→bíceps/antebraço.
- **[INV]** [VENCIDO duas vezes: o teto passa a ser min(tempo, nível × objetivo × idade), e a seleção deixa de variar por seed — vira determinística pelo perfil] Tetos por sessão: ≤6 exercícios, ≤20 séries totais; piso de 3 séries/exercício. Determinismo: estrutura fixa pelo perfil; seleção varia por seed.
- **[INV]** [VENCIDO em 2026-10-21: contraindicação deixou de filtrar o pool — ver `[REGRA]` abaixo] `ExercisePreFilter` também excluía exercícios cujo `contraindications` batia com `profiles.limitations` declaradas.

- **[REGRA]** Quem treina na ACADEMIA tem os seis `Accessory` por definição (`Accessory.paraAmbiente`, V65/V66) — o checklist de acessórios só aparece para CASA, e o conjunto usado é sempre o do ambiente da chamada, não o gravado no perfil.
- **[REGRA]** Contraindicação não filtra o pool (decisão de 2026-10-21). Como filtro duro ela esvaziava grupo inteiro — dor no ombro deixava 1 exercício de peito, dor no joelho deixava 0 de quadríceps. `profiles.limitations` continua gravado e `exercises.contraindications` segue curado para a biblioteca, mas a tela de limitações virou aviso ("procure um profissional de saúde antes de treinar"), não exclusão no motor.

- **[INV]** `exercises.volume_target` (V64) dá a cada exercício da lista principal **exatamente um**
  alvo de volume, curado contra `server/src/test/oracle/alvo_canonico.csv` — fato do DADO, já verdadeiro
  hoje, **independente** de o motor Kotlin já usar isso para casar músculo↔exercício (ainda não
  usa: o `[INV]` acima sobre `movement_pattern`/categoria continua sendo o que o `SlotFiller`
  roda de verdade).
- **[REGRA]** O critério de casamento ratificado para o motor v3 é `exercise.volumeTarget == alvo`
  — substitui o casamento por `movement_pattern`+categoria (opção C sobre A/B, decidida
  2026-10-14; ver `ARCH #39`). **Decidido, não implementado.** Não escreva código novo contra o
  critério antigo; a troca do `SlotFiller` é trabalho de uma fatia do motor v3 (1–12).
- **[REGRA]** A tabela canônica do catálogo (qual `volume_target` cada exercício tem) **não é
  documento, é dado** — vive em `exercises.volume_target` e em `server/src/test/oracle/alvo_canonico.csv`.
  Pendente de validação profissional (CREF) — ver débito em `fontes/debitos.md`.

### Motor v3 — etapa [1], ALVOS (Fatia 1, fechada em 2026-10-23)

- **[REGRA]** A tabela de volume semanal e a de volume indireto do motor v3 vivem em
  `engine.v3.VolumeTargets` / `engine.v3.VolumeIndirectTable`, **separadas** de `VolumeModel.kt`
  (motor antigo). O motor antigo não tem teto de sessão nem alocação por prioridade — herdar os
  números novos hoje faria as sessões crescerem sem nada que as limite. A tabela só passa a valer
  quando a Fatia 3 trocar o `StructureEngine` pelo pipeline novo.
- **[REGRA]** Tabela de volume semanal (séries efetivas/semana, `VolumeTargets.weeklySets`):
  braço (bíceps/tríceps) sobe a 12/12 no intermediário e 15/16 no avançado (antes ~9/10, ~3
  séries diretas); abdômen fica em **zero** em todo nível até o catálogo ter exercícios — reservar
  capacidade que não dá para preencher era o próprio defeito. `GENERAL_HEALTH` continua usando o
  piso (MEV), igual ao motor antigo. Totais por nível: 61 (iniciante) · 111 (intermediário) · 148
  (avançado).
- **[REGRA]** Volume indireto (`VolumeIndirectTable.indiretoRecebido`): o motor credita 0,5 série
  ao sinergista por série de composto do doador (peito/ombro→tríceps · costas→bíceps+antebraço ·
  posterior→glúteo+antebraço · quadríceps→glúteo). **Duas exceções:** ombro nunca é recipiente (o
  alvo "ombro" é o deltoide inteiro — descontar subserviria lateral/posterior); glúteo→posterior é
  **0,2**, não 0,5 (elevação pélvica/abdução treinam isquiotibial pouco; tratar como 0,5 derruba o
  atendimento do posterior de 86% para 76%, medido na especificação).
- **[INV]** `demandaDireta(alvo, indireto) = max(0, alvo − indireto)` nunca é negativa. Quem lê
  essa demanda daqui em diante é a alocação (Fatia 3) — nenhuma etapa anterior a ela.

### Motor v3 — etapa [2], CAPACIDADE (Fatia 2, fechada em 2026-10-23)

- **[REGRA]** `engine.v3.CapacityModel.overheadMinutos(dias)` deriva o overhead da rampa **do
  número de dias**, não do `SplitType`: 2–3 dias = 16min (full body, quatro compostos de barra);
  4–6 dias = 11min (upper/lower, upper/lower+PPL, PPL — dois compostos). `UPPER_LOWER_FULL` e
  `ARNOLD` são do motor antigo e não entram no motor v3 — `dias` fora de 2..6 lança
  `IllegalArgumentException`, nunca herda 11min em silêncio. Oferecer essas duas divisões no v3
  é decisão de produto pendente, não coberta pela especificação (ver `fontes/debitos.md`).
- **[REGRA]** `tetoDaSessao = min(tetoPorTempo, tetoPorNivel × fatorObjetivo × fatorIdade)`, com
  `tetoPorTempo = (minutos − overhead) ÷ 3`. **Retorna `Double`, não `Int`** — arredondar aqui
  quebra a tabela de cobertura da especificação célula a célula (confirmado batendo as 27
  células). O arredondamento para séries inteiras é da alocação (Fatia 3), na ordem de
  prioridade, nunca nesta etapa.
- **[REGRA]** Fator de objetivo sobre o teto: `MAINTAIN` → 0,6; os demais (inclusive
  `GENERAL_HEALTH`) → 1,0.
- **[REGRA]** Fator de idade sobre o teto: 0,75 para 60+ que seja iniciante OU esteja no 1º
  mesociclo. **`mesocicloAtual == null` conta como 1º mesociclo** — o app não acompanha
  mesociclos até a Fatia 10, então hoje todo aluno de 60+ recebe 0,75, independente do nível. É
  o lado seguro; a regra em si não muda quando a Fatia 10 passar o valor real.
- **[INV]** Teto fisiológico de 11 séries por músculo por sessão
  (`MAX_SERIES_POR_MUSCULO_POR_SESSAO`) existe como constante, mas **nada aplica isso ainda** —
  é a alocação (Fatia 3) quem usa.

### Motor v3 — etapa [3], ALOCAÇÃO POR PRIORIDADE (Fatia 3, fechada em 2026-10-23)

- **[REGRA]** `engine.v3.PriorityAllocation.alocar()` espelha `server/src/test/oracle/motor_v3.py::alocar()`
  termo a termo, validado rodando a função Python com os mesmos números em 5 capacidades: sem
  escassez devolve `direta` arredondada (meio pra cima); sob escassez, piso =
  `min(direta, max(0, piso do iniciante − indireto))` por músculo ativo → rebaixa ao piso do
  papel na `ORDEM_SACRIFICIO` (antebraço, panturrilha, abdômen, glúteo, bíceps, tríceps, ombro)
  → sacrifica na mesma ordem se nem isso couber → excedente proporcional ao `PESO_PRIORIDADE` →
  arredondamento final em ordem de prioridade. Recebe `direta`/`indireto`/`capacidade` já
  resolvidos — nenhum laço de convergência aqui.
- **[INV]** (até a Fatia 7) CHEST, BACK, QUADS e POSTERIOR nunca eram rebaixados nem
  sacrificados (fora de `ORDEM_SACRIFICIO`), e por isso `alocar()` não garantia
  `soma(resultado) ≤ capacidade` sob escassez extrema. **Superado pela Fatia 8**: ver o corte
  proporcional abaixo — esses 4 músculos agora também são reduzidos (e, se preciso, removidos)
  quando sacrificar todo o resto não basta. `soma(resultado) ≤ capacidade` passou a valer sempre
  em `alocar()`; o laço de corte por tempo (Fatia 8, ainda não construído) resolve outra coisa:
  o excedente que sobrevive à alocação e só aparece no `entregue` (preenchimento pelo catálogo).
- **[REGRA]** O ciclo do ponto fixo do indireto **não é da alocação** — precisa de séries de
  composto medidas sobre o entregue (Decisão B), que só existem com a composição. Atribuição
  movida para a Fatia 5 (`trabalho/motor-v3-desenho-e-fatias.md`, corrigido em 2026-10-23).
- **[REGRA]** A troca do `StructureEngine` em produção e a remoção do `VolumeModel.kt` antigo
  não são desta fatia — passam para **depois da Fatia 9**, quando o diferencial contra o
  simulador estiver verde em Kotlin. Ligar a alocação nova à produção antes disso misturaria
  peças novas com estrutura/composição/preenchimento ainda velhos.

### Motor v3 — etapa [4], ESTRUTURA DA SEMANA (Fatia 4, fechada em 2026-10-23)

- **[REGRA]** `engine.v3.WeekStructure.divisao(dias)` espelha `spec_v3.py::DIVISOES`: 2–3 dias =
  FULL_BODY (todos os músculos em todo dia); 4 = UPPER_LOWER; 5 = UL_PPL; 6 = PPL. `dias` fora de
  2..6 lança `IllegalArgumentException` — mesmo padrão de `CapacityModel.overheadMinutos` (Fatia
  2). `UPPER_LOWER_FULL`/`ARNOLD` continuam fora do motor v3 (débito de produto já registrado).
- **[REGRA]** `WeekStructure.distribuirPelosDias()` espelha `montar_semana()` termo a termo,
  validado rodando a função Python com as 3 saídas reais de `PriorityAllocation.alocar()` (sem
  escassez, capacidade 40, capacidade 65,33) — saída exata, sem tolerância. Recebe
  `alocado: Map<TargetMuscle, Int>`, não a capacidade nem o indireto — **depende das Fatias 2 e
  3**, não só da 2.
- **[REGRA]** Frequência efetiva do músculo = `max(1, min(dias que o treinam, total ÷ piso do
  papel))`, subindo 1 treino por vez enquanto `total/freq` passar do teto fisiológico de 11
  séries/sessão. Se o total alocado ficar abaixo do piso de isolamento (2), sobe a 2 antes de
  calcular a frequência (disparo `sobe_ao_piso_do_isolamento`). Músculo com pouca demanda treina
  em **menos dias**, nunca fatiado abaixo do piso — é o caso GLUTES (4 séries, piso 3 → 1
  treino/semana, não 2×2): teste de conformidade com o oráculo, não decisão de produto. **A
  pergunta de qual lado está certo (motor × `modelo_a_mao.py`, que assume 2× fixo) continua
  aberta na Fatia 9.**
- **[INV]** `distribuirPelosDias()` não corta nada: a soma distribuída é sempre igual à soma de
  `alocado`, mesmo quando `alocado` já excede a capacidade. Quem corta é o laço de corte por
  tempo (Fatia 8) — medido em 176/720 perfis (24,4%, recalculado na Fatia 7 após alinhar a
  convenção do mesociclo com a Fatia 2 -- era 154/720/21,4%; ver
  `trabalho/motor-v3-desenho-e-fatias.md`, Fatia 8).
- **[REGRA]** Calendário (rótulo de dia da semana para cada posição do ciclo) é concern de
  agenda/apresentação, fora do motor — `divisao()` já devolve quais músculos em cada posição, que
  é o que decide treino; rotular a posição é decisão de quem wireia a agenda, sem reabrir esta
  fatia.

### Motor v3 — etapa [5], COMPOSIÇÃO DA SESSÃO (Fatia 5, fechada em 2026-10-23)

- **[REGRA]** `engine.v3.SessionComposition.comporSessao()` espelha `compor_sessao()` do oráculo
  termo a termo, validado com as saídas reais de `WeekStructure.distribuirPelosDias()` (Fatia 4).
  Número de exercícios por músculo: até 4 séries → 1; 5 a 8 → 2; 9 ou mais → 3 — exceto músculo
  `SO_ISOLAMENTO` (bíceps, tríceps, antebraço, panturrilha, abdômen), que nunca recebe papel
  composto/acessório, só isolamento. `distribuir()` enche cada papel até 4 séries na ordem
  piso→piso→piso, depois primeiro papel até o teto, antes de passar ao próximo.
- **[REGRA]** Regra do ombro: o desenvolvimento (deltoide) cede espaço ao volume de composto de
  peito já alocado no mesmo dia — limite = 10 menos o composto de peito do dia. Se o limite ficar
  abaixo de 3 séries, o ombro inteiro vira isolamento (`ombro_vira_isolamento`); senão, a âncora
  composta do ombro é cortada para o limite e o resto vira isolamento
  (`ombro_teto_do_desenvolvimento`). Só se aplica a `SHOULDERS` — e só funciona porque `CHEST` é
  processado primeiro na ordem de `TargetMuscle` (mesma ordem de `ALVOS` no oráculo).
- **[INV]** `SessionComposition` **não inclui o ciclo do ponto fixo do indireto.** O oráculo mede
  `compostos_semana` sobre o exercício REAL escolhido no catálogo, não sobre o papel assinalado
  aqui — e os dois divergem em 293 a 299 de 720 perfis (até 41,5%) fora de academia, por falta de
  equipamento de isolamento em casa (`EQUIPAMENTO_CASA`): o fallback do `preencher()` enfia um
  exercício COMPOSTO numa vaga que pedia ISOLAMENTO (medido por músculo: BACK 272/360, POSTERIOR
  56/360, CHEST 27/360, cenário "casa com banco") — não é, na maioria dos casos, vaga vazia.
  Um ponto fixo construído sobre o papel estaria errado em 4 de 10 casos. O laço passou para a
  abertura da Fatia 6, que é onde "composto medido sobre o entregue" (Decisão B) passa a existir
  de verdade.
- **[REGRA]** Fallback do `preencher()` quando falta candidato do papel certo (decisão do
  Rafael, 2026-10-23): **inverte o tipo**, nos dois sentidos (isolamento ← composto e
  composto ← isolamento), medido sobre o entregue (Decisão B) — nunca sobre o papel assinalado.
  Condições:
  1. O substituto respeita a regra de família (no máximo um por família na sessão) — a inversão
     não pula essa regra, ela roda depois, sobre o conjunto já invertido.
  2. Vaga vazia só acontece se não houver candidato NENHUM depois da inversão nos dois sentidos.
  3. Cada troca conta o disparo `papel_trocado`, agregável por ambiente (é o que a Fatia 5 mediu:
     até 299/360 em "casa com banco", maioria por falta de equipamento de isolamento em casa, não
     por vaga vazia).
  4. A ordem de exibição na sessão segue o tipo do exercício ENTREGUE (composto antes de
     isolado), não o papel original — um composto que entrou por inversão abre a sessão como
     qualquer outro composto.

### Motor v3 — etapa [6], PREENCHIMENTO PELO CATÁLOGO + PONTO FIXO (Fatia 6, fechada em 2026-10-23)

- **[REGRA]** `engine.v3.CatalogPool.pool()` é o pré-filtro do motor v3 — **não é o
  `ExercisePreFilter` de produção** (decisão do Rafael: não alterá-lo; são dois pré-filtros que
  convivem até a Fatia 9 decidir se um substitui o outro). Puro, em memória, sem transação:
  `volumeTarget != null` primeiro (garante `prioridade`/`relevancia`/`nivelMaximo` presentes,
  CHECK do banco V65) → ambiente sem misturar casa/academia (`AMBOS` serve os dois) → nível
  cumulativo (BEGINNER vê só BEGINNER; ADVANCED vê os três) → `nivelMaximo` com válvula de
  escape **por músculo** (nunca global: se zerar um alvo, aquele alvo ignora o teto) →
  equipamento de casa (`DUMBBELL`/`BODYWEIGHT`/`BAND`/`HAND_GRIP`) quando `ambiente == CASA` →
  acessórios (`containsAll`).
- **[REGRA]** `engine.v3.CatalogFiller.preencher()` espelha `preencher()` do oráculo termo a
  termo: papel certo (com o fallback de inversão já registrado como `[REGRA]` acima) → não
  repetir exercício no dia → no máximo um por família na sessão (família é por DIA, não por
  músculo — dois músculos diferentes competem pela mesma família) → primeira vaga do músculo
  prefere relevância 1 → **desempate: usados na semana → prioridade → nome.** O nome como
  terceiro critério é só do Kotlin (o oráculo empata nos dois primeiros e decide pela ordem de
  iteração, não documentada) — testado embaralhando o catálogo de entrada e exigindo saída
  idêntica.
- **[INV]** A ordem final de cada sessão segue o tipo do exercício **entregue** (composto antes
  de isolado), nunca o papel original que gerou a vaga.
- **[REGRA]** `engine.v3.PontoFixoIndireto` é a mecânica do laço (converge / oscila / esgota 30
  iterações), separada de quem conhece o pipeline (`MotorV3Pipeline`) — é essa separação que
  torna o caminho "esgota as 30 iterações" testável, já que nenhum dos 720 perfis reais chega lá
  (máximo observado: 5 iterações). Oscilação: fica o estado de MENOR indireto total entre os da
  volta do ciclo, disparo `ponto_fixo_oscilou` no estado escolhido (que pode não ser o último
  calculado). Esgotamento: disparo `ponto_fixo_nao_convergiu` no último estado.
- **[REGRA]** `engine.v3.MotorV3Pipeline.gerar()` orquestra as Fatias 1–6 (alvo → capacidade →
  alocação → estrutura → composição → preenchimento → medição → indireto de novo, pelo ponto
  fixo). **Não está ligado em produção** — isso é decisão de depois da Fatia 9.
- **[REGRA]** Continuidade entre programas (seed/âncoras fixas/mesociclo) fica pra Fatia 10: o
  oráculo não sorteia nada (determinístico de graça — nada a fixar) e não rastreia mesociclo.
  **Dentro de um programa, a saída do `preencher()` é fixa** — reaproveitada em todas as
  semanas; a troca de exercício (progressão) só acontece ao gerar um NOVO programa, e isso é
  código da Fatia 10, não desta. **Cumprido na Fatia 10** (`CicloDoMesociclo.semanaDoPrograma`).
  **Única exceção:** a troca de âncora por estagnação do iniciante — `[REGRA]` nomeada na seção
  "Fatia 10" abaixo, que referencia esta de volta.

### Motor v3 — etapa [8] parte 1, REGRAS DE SEGURANÇA (Fatia 7, 2026-10-24)

- **[REGRA]** `engine.v3.SafetyRules.modoConservadorAutomatico()` espelha
  `motor_v3.py::modo_conservador_automatico()`: dispara para iniciante, objetivo saúde geral, ou
  60+ no 1º mesociclo (mesma convenção `[REGRA]` da Fatia 2 — `mesocicloAtual == null` conta como
  1º). Gatilho por preferência declarada ("quer começar no modo leve?") não existe ainda — campo
  fora de `ProfileDto`, depende de rastreio de mesociclo (Fatia 10).
- **[REGRA]** `engine.v3.SafetyRules.rirMinimo()`: piso de RIR de segurança, não a tabela de RIR
  por semana/objetivo. `3` se o modo conservador estiver ativo (vale em TODO exercício da
  sessão); senão `2` se `exercicio.rirMinimoSeguranca` (coluna V67, aprovada por nome contra
  `tools/curadoria_oficial/curadoria_canonica.csv` — 8 exercícios: Agachamento Livre com Barra,
  Afundo com Barra, Levantamento Terra, Stiff com Barra, Remada Curvada com Barra, Remada
  Cavalinho no Landmine, Desenvolvimento Militar com Barra, Flexão em Parada de Mão na Parede);
  senão nenhum piso.
- **[REGRA]** `rirMinimoSeguranca` **não é derivável de `movementPattern`/`equipment`** — a
  especificação trocou enumeração por propriedade justamente porque nenhum padrão de movimento
  capta "carrega a coluna ou passa sobre a cabeça" sem falso positivo/negativo (HINGE pega a
  Elevação Pélvica com Barra, que não tem esse risco; não pega a Remada Curvada com Barra, que
  tem; VERTICAL_PUSH inclui mergulhos, que não passam sobre a cabeça). `contraindications` também
  não serve: é dado de aviso ao usuário desde 21/10, não decisão do motor. A coluna foi aprovada
  por nome, offline, uma vez — o motor só lê a coluna em tempo de execução.
- **[INV]** Nenhum perfil `BEGINNER` recebe exercício com `rirMinimoSeguranca=true`. Não é
  invariante do laço de preenchimento — é propriedade de `CatalogPool.pool()` (filtro de nível
  cumulativo) cruzada com a curadoria: os 8 exercícios aprovados são todos `level`
  `INTERMEDIATE`/`ADVANCED`, então `BEGINNER` nunca os vê no pool. Medido nos 720 perfis do
  oráculo (2026-10-24): **0 violações**; testado no Kotlin via `CatalogPool` com fixture mista
  (`SafetyRulesTest`).
- **[REGRA]** Troca de barra por halter/máquina para o iniciante — **não construída.** A
  especificação (`motor-v3-ESPECIFICACAO.md`, linhas 521 e 765) manda o gatilho ser `BEGINNER`
  literal, não o modo conservador — e o `[INV]` acima mostra que a regra já está satisfeita pela
  curadoria, sem código: um `BEGINNER` nunca recebe nenhum dos 8 exercícios, então a troca nunca
  teria candidato para disparar. **Não estender para o modo conservador** — seria regra nova,
  fora da especificação (decisão do Rafael, 2026-10-24). As linhas 521/765 da especificação foram
  marcadas "satisfeita pela curadoria, sem regra no motor" com a mesma data.
- **Disparos medidos nos 720 perfis do oráculo** (mesma matriz de `test_motor_v3.py::PERFIS`,
  2026-10-24): modo conservador por iniciante 240, por saúde geral 180, por 60+ no 1º mesociclo
  360 (perfis podem disparar por mais de um gatilho; união 540 perfis com modo conservador
  ativo); RIR 2 por propriedade em 123 perfis (335 vagas); RIR 3 por modo conservador em 540
  perfis (7217 vagas). Nenhum gatilho com zero disparos.

### Motor v3 — etapa [7], AUDITORIA DO VOLUME EFETIVO (Fatia 9 antecipada, 2026-10-27)

- **[REGRA]** `engine.v3.ProgramAudit.auditar()` espelha `server/src/test/oracle/motor_v3.py::auditar()`
  termo a termo. Recebe alvo/entregue/indireto/treináveis e devolve, por músculo: `direto`,
  `indireto`, **`efetivo = direto + indireto`**, `minimo` (`VOLUME[m][0]`), `alvo`, `razao`,
  `status` e `atingiuMinimo`. **Não muda nenhuma série prescrita** — roda depois do pipeline, lê o
  resultado e o classifica; removê-la devolve o motor ao silêncio sem alterar programa nenhum.
- **Por que a peça existe:** a grandeza que responde *"este programa serve?"* — o estímulo total
  do músculo — **não existia no modelo**. O motor media `entregue` (direto) e `indireto` em
  separado e nunca somava os dois, então nenhuma verificação contra o piso era possível: não
  faltava um `if`, faltava o conceito. Medido antes de construir: **2.079 pares (perfil, músculo)**
  dos 720 perfis entregavam efetivo abaixo do piso, nenhum sinalizado.
- **[REGRA]** Cinco status, cada um com significado acionável distinto: `ATENDIDO` (efetivo ≥ alvo,
  ou alvo zero) · `MANUTENCAO` (passou do piso, não do alvo) · `ABAIXO_DO_MINIMO` · `NAO_TREINADO`
  (nada, nem direto nem indireto) · `SEM_CATALOGO` (não havia exercício no pool do perfil — não é
  escolha do motor).
- **[INV]** Todo músculo sai da auditoria com status; nenhum sai `ATENDIDO` com efetivo abaixo do
  alvo; nenhum sai `MANUTENCAO` com efetivo abaixo do piso. Medido nos 720, 0 violações.
- **[REGRA]** O gatilho do aviso de honestidade (`abaixoDoMinimo()`) usa **`atingiuMinimo`, não o
  status.** Motivo no item seguinte.
- **[REGRA] O limiar do mínimo depende do OBJETIVO** (decisão do Rafael, 2026-10-27).
  `VolumeTargets.minimoEfetivo(musculo, objetivo)` / `motor_v3.py::minimo_efetivo()`: quem pediu
  **manutenção** é medido contra o **MV** (volume de manutenção, o mínimo para *não perder*); todo
  o resto contra o **MEV** (`VOLUME[m][0]`, o mínimo para *crescer*).
  - **A lacuna que isto fecha:** `FATOR_OBJETIVO_ALVO` multiplica o alvo sem respeitar o piso, e em
    `MAINTAIN`/`BEGINNER` (0,6 sobre a coluna do iniciante, que É o piso) os 10 músculos nasciam
    com alvo abaixo dele — peito 4,8 contra 8. Eram **660 pares nos 720** reprovando por design.
    O defeito não estava na prescrição: estava em **comparar contra o limiar errado**. MEV e MV são
    grandezas distintas na literatura (a RP publica as duas: bíceps MV ≈ 4 contra MEV ≈ 8) e a
    nossa tabela só tinha uma.
  - **[PROPOSTA]** O fator do MV é **derivado**, não tabela ratificada: reaproveita o fator de
    "manter" (0,6), já revisado, aplicado ao piso. Fecha com a decisão existente — para o
    iniciante, o alvo de `MAINTAIN` É exatamente o MV, então "manter" passa a significar "treinar
    no volume de manutenção". Trocar por tabela revisada é trocar uma função.
  - Alternativas descartadas: clampar o alvo no piso (`alvo = max(piso, fator × coluna)`) tornaria
    `MAINTAIN` e `GAIN_MUSCLE` **idênticos para o iniciante**, matando o objetivo nesse nível; e
    deixar como estava faria o aviso de honestidade dar alarme falso em 9% dos pares, ensinando o
    usuário a ignorá-lo.
- **[REGRA] O fator de objetivo vale no alvo semanal E no teto da sessão.** `VolumeTargets.
  fatorObjetivoAlvo()` (alvo) e `CapacityModel.fatorObjetivo()` (teto) — os dois, como a
  especificação manda (*"esses fatores agora multiplicam **também** o teto da sessão, não só o
  alvo semanal"*). `weeklySets()` passou a devolver `Double` e a espelhar
  `motor_v3.py::alvos()` termo a termo, incluindo o limite pelo MRV: `min(coluna × fator, teto)`.
  - **Defeito corrigido em 2026-10-27 (E10):** o Kotlin aplicava o fator **só no teto**. Todo
    perfil `MAINTAIN` recebia alvo **67% maior** que o do oráculo — 36,6 contra 61,0 no iniciante,
    66,6 contra 111,0 no intermediário, 88,8 contra 148,0 no avançado — em **180 dos 720 perfis**.
  - **Por que passou:** nenhum teste exercitava `MAINTAIN` no alvo semanal (`VolumeTargetsTest` só
    usava `GAIN_MUSCLE` e `GENERAL_HEALTH`), e a varredura dos 720 em Kotlin que pegaria isso
    **não existe** — foi substituída pelo teste de 12 perfis ao fechar a Fatia 6. É a confirmação
    do risco registrado: **os dois lados podem concordar e estar errados juntos, ou nem ser
    comparados.** Teste de regressão adicionado (`manter aplica o fator 0,6 ao alvo semanal, nao
    so ao teto da sessao`).
- **Medido nos 720** (travado em teste): ATENDIDO 3.613 · MANUTENCAO 1.898 · ABAIXO_DO_MINIMO 1.242
  · SEM_CATALOGO 720 (CORE, sempre) · NAO_TREINADO 447, de 7.920 pares. **418/720 perfis (58%)**
  têm ao menos um músculo abaixo do piso — 376 pelo status, mais 42 que só aparecem por
  `atingiuMinimo`.
- **[PROPOSTA] Achado medido junto, não resolvido:** `papel_trocado` (312/720 perfis) faz o
  catálogo injetar indireto que a prescrição não planejou — 324 vagas de isolamento de costas
  preenchidas com composto creditam bíceps e antebraço. Somado: **BICEPS +398 séries de indireto
  não previsto, FOREARMS +526**, GLUTES +131, TRICEPS −87,5. Creditar pelo exercício real continua
  certo (Fatia 5); o que falta é o motor medir o que a troca custou. Ver
  `docs/historico/motor-v3-fatia-8.md`.

### Motor v3 — etapa [3] amendada pela etapa [8], CORTE PROPORCIONAL NA ALOCAÇÃO (Fatia 8, em andamento, 2026-10-24)

- **[REGRA]** `engine.v3.PriorityAllocation.alocar()` (Fatia 3) ganhou uma fase nova entre o
  sacrifício e a redistribuição de excedente: quando a capacidade não comporta os pisos, depois
  de sacrificar na `ORDEM_SACRIFICIO`, os grupos restantes são reduzidos proporcionalmente ao
  `PESO_PRIORIDADE`. Hoje só CHEST, BACK, QUADS e POSTERIOR sobrevivem até esse ponto (são os 4
  de fora da `ORDEM_SACRIFICIO`) — o corte é "água enchendo": cada iteração reduz todos os
  restantes proporcionalmente ao inverso do peso (quem pesa mais perde menos); se o corte
  calculado para algum músculo for maior que o piso que ele ainda tem, ele é removido por
  completo (`sacrificio_proporcional`) e o déficit é recalculado entre os que restam, até
  convergir (`corte_proporcional`) ou esvaziar tudo. Espelha
  `server/src/test/oracle/motor_v3.py::alocar()` termo a termo; mesmo algoritmo no Kotlin
  (`PriorityAllocation.kt`) e no oráculo.
- **Decisão de prescrição, não lacuna de implementação** (Rafael, 2026-10-24): a alternativa era
  estender a `ORDEM_SACRIFICIO` com os 4 grandes, um por vez (corte sequencial) — medido nos 288
  perfis de 2–3 dias antes de decidir: o sequencial resolvia `alocado > capacidade` (3/288, só
  ruído de arredondamento) mas fazia os 4 grandes perderem 25–36% de volume (ex.: BACK
  9,56 → 7,36 série/semana); o proporcional erra bem menos (BACK 9,56 → 9,22) e resolve mais do
  `entregue > capacidade` de tabela (214/288 → 47/288, contra 160/288 do sequencial). Ver
  `historico/motor-v3-fatia-8.md` para a tabela completa das quatro opções medidas.
- **[REGRA]** O corte proporcional troca o antigo `[INV]` da Fatia 3 (ver etapa [3] acima):
  `soma(resultado) ≤ capacidade` agora é garantido sempre em `alocar()`, inclusive sob escassez
  extrema.
- **[INV] fonte única** (2026-10-24): `ORDEM_SACRIFICIO` deixou de ser lista mantida à mão e
  passou a ser SEMPRE derivada de `PESO_PRIORIDADE` (peso crescente — quem pesa menos sacrifica
  primeiro — desempate por `ALVOS.index`/`ordinal` ascendente). As duas nunca podem discordar,
  por construção. Achado que motivou a mudança: a lista fixa antiga discordava do peso em
  GLUTES (peso 0,7, mas vinha ANTES de BICEPS/TRICEPS na ordem — protegido demais) e BICEPS
  (peso 0,5, vinha DEPOIS de GLUTES — sacrificado tarde demais). **Medido nos 720 perfis antes de
  mudar**: a correção não altera nenhum dos 156 eventos de zeramento da Fatia 8 (ver etapa [4]
  abaixo) — nesses perfis o laço de rebaixamento/sacrifício nunca é alcançado (a soma dos pisos
  de base já cabe na capacidade antes de qualquer sacrifício). A `[INV]` vale pela consistência
  interna — é a mesma razão pela qual a validação idêntica contra o oráculo não basta: os dois
  lados concordavam, e os dois estavam discordando do peso ao mesmo tempo. Ver
  `docs/historico/motor-v3-fatia-3.md` (nota retroativa) e `docs/historico/motor-v3-fatia-8.md`.
- **Medido nos 720 perfis** (não só nos 288 de 2–3 dias): `corte_proporcional` dispara só em
  2–3 dias (120/144 perfis de 2 dias, 52/144 de 3 dias; 0/144 em 4, 5 e 6 dias) — confirmando que
  4–6 dias não mudam com esta decisão. `alocado > capacidade` cai para 6/720 (3/144 em 3 dias,
  3/144 em 4 dias — ruído de arredondamento de ~0,2 série, não resolvido aqui: é o laço de corte
  por tempo da Fatia 8, ainda não construído, que precisa cobrir esse resíduo). `entregue >
  capacidade`: 130/720 no total, 47/288 nos perfis de 2–3 dias — a causa dos 47 (fallback do
  catálogo ou outra) **ainda não foi isolada**; isolar com medição, antes de desenhar o laço de
  corte por tempo, é trabalho da própria Fatia 8, não desta amendação.
- A rampa de carga (50%×8/70%×4/85%×2 no primeiro padrão pesado) **não muda** com esta decisão.
  Uma estimativa de 9 min/sessão contando a rampa só uma vez por sessão (em vez de uma vez por
  padrão pesado) foi medida por sensibilidade e ficou **pior**, não melhor (47/288 → 84/288 em
  `entregue > capacidade`) — fica registrada só como medição no histórico, nunca como regra.

### Motor v3 — ALVOS: CORE fora do motor (Fatia 8, 2026-10-27)

- **`[REGRA]` São 10 alvos de volume, não 11. CORE saiu do motor e do contrato.** `TargetMuscle`
  e `spec_v3.ALVOS` têm CHEST, BACK, SHOULDERS, BICEPS, TRICEPS, FOREARMS, QUADS, POSTERIOR,
  CALVES, GLUTES. `MuscleGroup.CORE` saiu junto, do contrato (era o vocabulário do usuário: quiz
  de foco, filtro da biblioteca, progresso). A `V68` aperta o `CHECK` de
  `exercises.volume_target` para recusar `'CORE'` — e **falha alto** se alguma linha ainda tiver
  o valor, em vez de reescrever catálogo por conta própria.
- **Por quê, medido antes de cortar.** CORE era o **único** alvo com pool vazio em *todas* as 15
  combinações de ambiente × nível × acessório, nos dois papéis (composto e isolamento), sem
  válvula de escape possível — a tabela completa de cobertura está em
  `historico/motor-v3-fatia-8.md`. O alvo semanal era 0 em todo nível, então o buraco era
  **dormente**: nunca abria vaga, não quebrava nada, e viraria vaga vazia de verdade no instante
  em que alguém desse alvo > 0. Na auditoria da etapa [7], os **720 status `SEM_CATALOGO` da
  matriz eram todos de CORE** — um por perfil, exatamente.
- **`[INV]` A remoção não muda prescrição nenhuma.** Medido nos 720 perfis depois de remover:
  `ATENDIDO` 3613, `MANUTENCAO` 2100, `ABAIXO_DO_MINIMO` 1040, `NAO_TREINADO` 447 e os 367
  perfis reprovados ficaram **idênticos**; só os 720 `SEM_CATALOGO` desapareceram. O snapshot em
  `test_motor_v3.py::test_a_matriz_de_hoje_tem_estes_numeros` trava isso.
- **Não precisou de migration de dado.** Conferido no banco **antes** de cortar o enum:
  `profiles.focus_areas` com `"CORE"` = **0**; `programs.focus_muscles` com `CORE` = **0**.
  Nenhum perfil escolheu abdômen como foco; nada foi reescrito.
- **Fora de escopo, de propósito:** `ExerciseCategory.CORE` (taxonomia de categoria do catálogo)
  e `CORE` em `primary_muscles`/`secondary_muscles` (tag grossa de estímulo, que a UI mostra).
  São papéis diferentes de `volume_target`, a única coluna que o motor lê para contabilizar
  volume — mesma distinção que o cabeçalho da V64 já fixava.
- **`[PROPOSTA]` Duas defesas ficaram sem caso vivo** e são decisão do Rafael, não conserto:
  (1) o status `SEM_CATALOGO` continua no código — é a defesa para um alvo que *perca* cobertura
  — mas **deixa de disparar** nos 720; (2) `VolumeTargets.V.mrv` é `Int?` e o ramo
  `if (v.mrv != null)` em `weeklySets()` existia **só** para CORE, que era o único alvo sem teto.
  Pela convenção do próprio projeto ("regra de segurança que nunca dispara é código morto com
  cara de proteção"), as duas pedem decisão: manter como defesa declarada ou simplificar.
  Registradas em `fontes/debitos.md`.

### Motor v3 — contabilidade do volume efetivo (Fatia 8, 2026-10-27)

- **`[REGRA]` A tabela de volume está em convenção TOTAL, para todos os músculos.** Alvo =
  trabalho direto + indireto fracionado. **Uma contabilidade só.** A história de como se chegou
  aqui (ARCH #26 em convenção direta → v3 redefiniu como total → item 20 corrigiu os braços →
  esta rodada corrigiu antebraço, glúteo e posterior) está na especificação,
  `propostas/motor-v3-ESPECIFICACAO.md`, seção "Volume indireto". Referências:
  **alvo total 65/113/148**, **demanda direta 49/94/122**.
- **`[INV]` Nenhum coeficiente em `INDIRETO`/`VolumeIndirectTable` sem fonte registrada.** Cada
  par sobrevivente carrega a citação no próprio código, com o grau de confiança:
  - `peito → tríceps`, `ombro → tríceps` — Pelland et al. 2025 (*Sports Medicine*, 67 estudos,
    2.058 participantes): entre contar a série indireta como 0, 0,5 ou 1, a quantificação
    **fracionada** foi a de evidência mais forte. Confiança **alta**.
  - `costas → bíceps` — Pelland et al. 2025, idem; Mannarino et al. 2021 (rosca +11,06% contra
    remada +5,16% na espessura dos flexores do cotovelo). Confiança **alta**.
  - `quadríceps → glúteo` — Kubo et al. 2019 (glúteo +6,7% só com agachamento). Confiança
    **alta**.
  - `posterior → glúteo` — **extensão por analogia** do par acima: a dobradiça também é extensão
    de quadril. Confiança **média**. Zerá-lo contradiria a biomecânica que sustenta o par irmão.
  - **Removidos por não ter fonte nenhuma:** `costas → antebraço` 0,5, `posterior → antebraço`
    0,5, `glúteo → posterior` 0,2. Tripwire nos dois lados
    (`test_nenhum_coeficiente_sem_fonte_voltou_para_a_tabela` no oráculo,
    `nenhum coeficiente sem fonte voltou para a tabela` no Kotlin) para que não voltem.
  - **Ressalvas que valem para todos:** 0,5 é o melhor de **três valores testados**, não
    estimativa contínua, e os estudos são majoritariamente de **não treinados jovens do sexo
    masculino**. É heurística calibrável, não verdade.
- **`[REGRA]` O piso de trabalho direto nunca passa por cima da capacidade.** Financiado **dentro
  do orçamento**, em ordem decrescente de peso; músculo que não couber sai como `MANUTENCAO` ou
  `NAO_TREINADO`. **O motivo é um defeito que já cometemos:** o `sobe_ao_piso_do_isolamento` da
  etapa [4] agia por fora do orçamento e foi exatamente o que produziu `entregue > capacidade` em
  130/720 perfis — o sintoma que abriu a Fatia 8. Piso é prioridade na fila do orçamento, não
  licença para estourá-lo.
- **Achado estrutural, medido:** com os três pares removidos, doadores
  (peito, ombro, costas, posterior, quadríceps) e recebedores (tríceps, bíceps, glúteo) viraram
  conjuntos **disjuntos** — o grafo de coeficientes **perdeu o ciclo**. Mesmo assim
  `ponto_fixo_oscilou` **subiu de 26 para 44/720**, porque o ciclo real do ponto fixo nunca foi o
  grafo: é o **orçamento** (a demanda direta do recebedor disputa capacidade com as séries de
  composto do doador), e o antebraço passou a disputar de verdade (pede 3/6/9 onde pedia ~0).
  **O laço do ponto fixo continua necessário** — isso responde a pergunta que o D-1 deixava
  aberta sobre ele.
- **`[REGRA]` A escolha do estado no ciclo do ponto fixo é determinística e declarada:** fica o
  estado de **menor indireto total**, com desempate pela lista ordenada de `(músculo, indireto)`
  — *não* "o último calculado". O motivo está no código: é o estado que **menos supõe trabalho
  não feito**. Medido em 2026-10-27 nos 44/720 que oscilam (24 com ciclo de 2 estados, 20 com 3):
  a diferença entre estados é de **1 a 3 séries** por músculo, mas **39 dos 44 têm ao menos um
  músculo trocando de rótulo** — `BICEPS` e `CALVES` e `CHEST` entre `ABAIXO_DO_MINIMO` e
  `MANUTENCAO` (16/8/4 perfis), `FOREARMS` entre `ABAIXO_DO_MINIMO` e `NAO_TREINADO` (16),
  `TRICEPS` entre `ABAIXO_DO_MINIMO` e `ATENDIDO` (10) e entre `ATENDIDO` e `MANUTENCAO` (3),
  `POSTERIOR` e `GLUTES` entre `ATENDIDO` e `MANUTENCAO` (2 cada).
- **`[INV]` Consequência medida: todo número por perfil tirado dos 720 tem de declarar se é
  estável ao desempate.** Verificado escolhendo cada extremo do ciclo:
  - **os 134 (`ATENDIDO` com direto 0, INTER/ADV) são INVARIANTES**, faixa +0/+0 — é a conjunção
    que os define (`direto == 0` **e** `ATENDIDO` **e** nível ≠ iniciante) que cai fora das
    trocas: as 10 trocas do tríceps são todas em perfis `BEGINNER`, e as trocas
    `ATENDIDO ↔ MANUTENCAO` acontecem em estados com direto > 0. **O piso direto pode ser
    dimensionado sobre eles.**
  - **o antebraço com direto 0 é 245 a 261** (+16), não 245 exato.
  - **`entregue > capacidade` é 149 a 170** (−18/+3), não 167 exato.

- **Dois defeitos antigos fecharam junto:** os 2 testes diferenciais que estavam vermelhos desde
  antes da Fatia 8 (`POSTERIOR: -0,8` no avançado, atribuídos a "pergunta aberta da Fatia 9") eram
  **o coeficiente `glúteo → posterior` sem fonte** — 0,2 × 4 séries de composto = 0,8. Oráculo
  hoje: **32 testes, 0 vermelha**.

### Motor v3 — MEDIÇÃO, REFERÊNCIA E AVISO (Fatia 9, 2026-10-27)

- **[REGRA]** O oráculo (`server/src/test/oracle/motor_v3.py`) é a fonte; o Kotlin se prova contra
  ele nos **720 perfis** via `MotorV3Varredura720Test` (`:server:test`, sem Docker), sobre
  `catalogo_banco.csv`. Divergência = o Kotlin está errado, salvo mudança DE PROPÓSITO no oráculo.
- **[INV]** `referencia_720.txt` é GERADA (`python3 gerar_referencia_720.py`), nunca editada à mão, e
  `ReferenciaDos720` falha se estiver velha. **Mudou o oráculo ou o catálogo: regenere, revise o diff, só então
  ajuste o Kotlin.**
- **[INV]** O banco bate com `catalogo_banco.csv` campo a campo (147 linhas da lista principal),
  afirmado por `MotorV3FidelidadeCatalogoRealIntegrationTest` (exige Docker). Migration de curadoria
  nova pede regenerar o CSV (`exportar_catalogo.sql`) e a referência.
- **[INV]** Teste que lê arquivo de fora de `src/test/kotlin` declara-o em `inputs.files` no
  `server/build.gradle.kts`, com guarda de contagem. Sem isso o Gradle dá verde sem rodar.
- **[INV]** Nos 720: `entregue ≤ capacidade`; nenhum `ATENDIDO` abaixo do alvo; nenhum `MANUTENCAO`
  abaixo do piso; `efetivo ≥ direto`; as regras de segurança `ombro_teto_do_desenvolvimento` e
  `um_empurrar_com_barra_por_sessao` disparam ao menos uma vez (regra que nunca dispara é código
  morto); `ombro_vira_isolamento` = 0 (tripwire da Fatia 10: quando o foco entrar e disparar, vira
  regra de segurança) e `ponto_fixo_nao_convergiu` = 0.
- **[REGRA]** `ProgramAudit.avisos()` dá a CAUSA, não o texto: `NAO_TREINADO` (nada, nem indireto),
  `ABAIXO_DO_PISO` (recebeu, mas menos que o piso) e `PISO_DIRETO_NEGADO` (piso batido, sem série
  direta). `SEM_CATALOGO` nunca avisa. O limiar respeita o objetivo (MV em manutenção). Não muda programa.

### Motor v3 — etapa [3], PISO DE TRABALHO DIRETO E PISO PRESCRITÍVEL (Fatia 8, 2026-10-27)

- **`[REGRA]` Piso de trabalho direto: 2 séries diretas por semana**, para `INTERMEDIATE` e
  `ADVANCED`, nos músculos com **crédito indireto sancionado** (`TRICEPS`, `BICEPS`, `GLUTES`).
  Iniciante **isento**, pela regra geral do híbrido (Iversen et al. 2021 / Gentil). **Não existe
  para completar volume** — esses casos já saíam `ATENDIDO` —, existe para garantir **estímulo
  direto onde o composto não alcança**: Brandão et al. 2020 (*J Strength Cond Res* 34(5)), a
  cabeça longa do tríceps cresce **+2,1% só com supino contra +17,5% com extensão**, porque ela
  cruza o ombro e no supino a ação é conflitante.
- **Por que 2 e não 4, com a regra de decisão fixada ANTES da medição** (decisão do Rafael,
  2026-10-27): 4 é o número da literatura (MV da Renaissance Periodization, Iversen 2021) e seria
  adotado **se** não aumentasse `entregue > capacidade` e não levasse nenhum músculo protegido
  abaixo do MEV. Medido: o piso de 4 é **honrado em 228/480** perfis INTER/ADV, contra
  **324/480** do piso de 2. **Piso honrado em menos da metade dos casos elegíveis não é garantia,
  é aspiração** — e declará-lo seria repor exatamente a desonestidade que esta rodada removeu.
- **`[REGRA]` `piso_direto_negado` / `pisoDiretoNegado` é EIXO SEPARADO, não status novo.** O
  músculo que tinha piso e não o recebeu continua com o status que o volume efetivo determina
  (`ATENDIDO`, `MANUTENCAO`, …) e carrega a marca em paralelo. Decisão do Rafael: criar um sexto
  status misturaria duas perguntas diferentes — *bateu o alvo?* e *teve trabalho direto?*
  **Asserção (b) do D-1, na forma final:** nenhum `ATENDIDO` com direto = 0 **sem**
  `pisoDiretoNegado`, no escopo INTER/ADV. Verde nos dois lados. Os `BEGINNER` ficam fora por
  isenção declarada, e isso tem preço medido: **57 pares `BEGINNER`/`TRICEPS`** saem `ATENDIDO`
  com zero série direta, cobertos só pelo crédito das pressões. Não é defeito; é a regra.
- **`[REGRA]` Piso prescritível: dentro do orçamento, todo músculo recebe pelo menos o piso do
  papel OU zero — nunca fração não prescritível.** Aplicado na **etapa [3]**
  (`piso_prescritivel()` / `PriorityAllocation`), em ordem decrescente de `PESO_PRIORIDADE`, nos
  **dois** caminhos de saída da alocação (com e sem escassez) — o caminho sem escassez foi o
  resíduo de 6/720 da primeira tentativa, que só retornava inteiros arredondados. Quem não cabe
  é **zerado**, não arredondado para baixo: meia série não se prescreve.
- **A lição que isto fecha:** o estouro de capacidade **nunca foi excesso de volume**, era
  **aumento de volume acontecendo fora do orçamento**. Os **167 → 27 → 0** são a mesma correção
  aplicada em três lugares: o piso condicional da etapa [3], o `sobe_ao_piso_do_isolamento` da
  etapa [4] (que agia por fora e hoje é `[INV]` que falha alto, 0 disparos nos 720) e, por fim,
  panturrilha e antebraço, que subiam por fora pelo mesmo defeito. **`entregue > capacidade` é
  0/720 — o critério de pronto da Fatia 8.**

- **`[INV]` `entregue ≤ capacidade`, sempre.** Era o critério de pronto da Fatia 8 e passa a ser
  invariante permanente: **0 violações nos 720**, afirmada nos dois lados. Não é "o número de
  hoje" — é a propriedade. Os outros dois invariantes do mesmo critério estão acima: nenhum
  músculo abaixo do mínimo efetivo **em silêncio** (resolvido pelo rótulo `MANUTENCAO`, etapa [7])
  e nenhum `direto = 0` em INTER/ADV sem marcação (asserção (b) + `pisoDiretoNegado`).

### Motor v3 — como LER os números dos 720 perfis (Fatia 8, 2026-10-27)

- **`[REGRA]` A matriz dos 720 declara CASA sem acessório nenhum: ela é o PIOR CASO, não o
  típico.** Decisão do Rafael: **a matriz fica como está** — é a fixture do teste diferencial,
  onde pior caso é vantagem e estabilidade histórica vale mais que representatividade. O número
  de produto existe **ao lado**, congelado em
  `test_motor_v3.py::test_o_caso_realista_de_casa_tem_estes_outros_numeros`.
- **`[INV]` Todo número medido sobre os 720 carrega esse viés e precisa declarar se sobrevive a
  ele.** Verificado por medição em 2026-10-27, não por argumento:
  - **Déficit de volume: robusto** (−6% com um banco). A conclusão "91% é capacidade física"
    sobrevive, e toda medição anterior sobre os 720 continua valendo.
  - **Vagas vazias: 100% artefato** — **239 → 0 com um banco sozinho**. Este número **nunca deve
    ser citado como problema de produto**; citá-lo foi o que produziu um débito de "buraco de
    catálogo em CASA" que a V66 já havia resolvido.
- **Achado não investigado:** com os **6 acessórios** o resultado é **pior** que só com banco —
  mais opções no pool mudam a escolha e pioram a cobertura. Registrado em `fontes/debitos.md`
  (não-monotonicidade nos acessórios).

### Motor v3 — etapa [6], A VAGA DIRETA DO TRÍCEPS (Fatia 8, 2026-10-27)

- **`[REGRA]` A vaga de `ISOLAMENTO` de `TRICEPS` tem de ser extensão de cotovelo.** `MERGULHO` é
  empurrar composto e só chega à vaga pela inversão de papel; fica fora. **Exigência, não
  preferência**: se excluí-lo esvaziar o pool ele volta e o disparo
  `triceps_sem_extensao_de_cotovelo` registra — melhor mergulho que vaga vazia. Mesma fonte
  (Brandão 2020). Registrada **separada** da regra de volume, de propósito: é restrição de
  seleção, não de quantidade.
- **`[REGRA]` A preferência por extensão ACIMA DA CABEÇA não mora no motor.** `[INV]` **o motor
  decide QUANTO, a curadoria decide QUAL.** Tentada como filtro no `preencher()`, a preferência
  por `TRICEPS_FRANCES` rodava **antes** da `[REGRA]` da relevância 1 e esvaziava o pool:
  **471/471 vagas em 2 exercícios**, com `TRICEPS_PULLEY` — relevância 1, prioridade 1, a escolha
  primária da curadoria — **inalcançável em todos os 720 perfis**. Decisão do Rafael (opção 4):
  se a ciência manda acima da cabeça primeiro, o que está errado é a **relevância no catálogo**, e
  o conserto é migration de curadoria validada por CREF — não filtro no motor. Tripwire nos dois
  lados fixando a distribuição que a curadoria produz hoje
  (`test_o_motor_nao_escolhe_a_familia_do_triceps_pela_ciencia`); mudar a curadoria **também**
  derruba o teste, de propósito, para que a mudança seja visível e deliberada. A `[PROPOSTA]` está
  em `fontes/debitos.md`.

### Motor v3 — mesociclo, progressão e foco (Fatia 10, 2026-10-28)

**Progressão dupla** (`engine.v3.Progressao`, espelha `progressao_v3.py`; provada contra
`referencia_progressao.txt`, 12.730 casos, e por casos escritos à mão):

- **`[REGRA]`** As quatro regras: (1) sobe só quando TODAS as séries batem o topo da faixa no RIR
  alvo; (2) o aumento é o menor incremento do equipamento, exceto nas 3 primeiras exposições, em que
  vale o salto da calibração pelo PIOR RIR da sessão; (3) logo após um salto, repetições abaixo da
  faixa são aceitas enquanto o RIR estiver no alvo; (4) se a 1ª série do dia sair mais difícil que o
  alvo, as seguintes reduzem **4% FIXO por ponto de RIR abaixo do alvo** (decisão de 2026-10-28, dentro
  da faixa de 3–5% da literatura; a contradição interna da spec foi fechada por decisão — **não
  reabrir**). Redução que arredonda para a mesma carga é ANULADA e sinalizada.
- **`[REGRA]`** Repetições abaixo da faixa fora da tolerância **devolvem um incremento**: simetria da
  progressão dupla (sem "devolver" o loop de subir e devolver da regra 3 nem existiria).
- **`[REGRA]` RIR NULO aplica SÓ as regras por repetição** (D-3, 2026-10-28). Sem exigência de RIR no
  topo, sem salto proporcional (se QUALQUER série vem sem RIR), sem tolerância pós-salto, sem o −4% e
  sem 1RM; a calibração, que é feita de RIR, não age. Sessão sem nenhum RIR não apaga o 1RM
  histórico. Nada é inferido. Coluna `rir` nullable em `session_set_logs`, sem backfill.
- **`[REGRA]`** Calibração: RIR informado ≥ 4 sobe na hora, até 3 séries; bandas +10/+25/+50% pelo
  excesso sobre o alvo (1–2 / 3–4 / ≥5), teto +25% em barra livre; nunca arredonda para a mesma
  carga (piso de um incremento). **O caso da spec fecha assim:** 20 kg → 30 → 45 → 55 kg nas 3 séries
  e 60 kg na 2ª exposição — três exposições, não ~16 sessões.
- **`[REGRA]`** Arredondamento: `para_incremento` vai ao múltiplo mais próximo e o **empate DESCE**
  (`ceil(x/inc − 0,5 − 1e-9)·inc`, fixado porque Kotlin e Python divergem em empate exato); séries
  arredondam meio pra cima.
- **`[INV]`** O 1RM estimado (Epley, melhor série, só composto/acessório) **não escolhe a carga e não
  entra em nenhuma regra desta fatia — nem na calibração**. É calculado e guardado como sinal para a
  promoção da Fatia 11. Limitação registrada: a precisão do Epley cai acima de ~10 repetições.
- **Heurísticas calibráveis, NÃO evidência** (mesmo padrão dos números da RP; trocar é trocar a
  constante): `REDUCAO_RETORNO = 10%`, `ESTAGNACAO_SESSOES = 3`, a escolha do Epley, e o "devolver"
  implícito acima. Detalhe em `fontes/debitos.md`.

**Mesociclo** (`CicloDoMesociclo`):

- **`[REGRA]`** 4 semanas, avança por CONCLUSÃO, não por calendário. Semanas 1–3: séries fixas;
  semana 4: deload (metade das séries, meio pra cima, mín. 1; **carga mantida**) para INTERMEDIATE e
  ADVANCED. **As séries nunca sobem dentro do ciclo.**
- **`[REGRA]`** O iniciante NÃO tem deload por calendário (25% do tempo dele seria desperdício): só por
  estagnação.
- **`[REGRA]` EXCEÇÃO NOMEADA à fixidez das âncoras** (a `[REGRA]` de exercícios fixos dentro do
  programa, acima, em "Motor v3 — etapa [8]/continuidade"): a **única** troca de exercício no meio do
  programa é a troca por estagnação do iniciante, e só dispara quando as TRÊS condições valem, nesta
  ordem — (a) um deload JÁ foi aplicado com a mesma âncora; (b) a estagnação PERSISTE: 3 sessões
  seguintes ao deload sem SOBE; (c) há diagnóstico registrado (vem de fora, Fatia 11). Subir depois do
  deload, em qualquer sessão, afasta a troca; estagnar de novo depois de retomar devolve DELOAD (sinal
  de promoção, Fatia 11). Implementação: `AncoraDoIniciante.situacao` (varrida em todos os históricos
  de até 7 eventos por um invariante independente da máquina). Referência cruzada com a `[REGRA]` de
  continuidade acima.
- **`[REGRA]`** Retorno após pausa, UMA regra em TRÊS ramos, relógio do SERVIDOR: ≤14 dias segue; 15–28
  semana 1 do MESMO mesociclo com carga 10% menor (heurística); >28 mesociclo NOVO e **recalibração
  obrigatória** (a carga informada seria a de antes da pausa). Pausa não é dimensão da matriz (é
  evento no tempo): **inalcançável na varredura dos 720 por construção**, coberta por teste unitário
  com histórico construído — o mesmo padrão do `ombro_vira_isolamento`.
- **`[REGRA]` 60+ que volta depois de >28 dias entra no mesociclo N+1 em MODO CONSERVADOR explícito**
  (`Mesociclo.conservador`), independente do número do mesociclo. **Extensão declarada ALÉM da lista
  literal da spec**, que só olha o 1º mesociclo. Motivo: quem volta depois de um mês fora é quem mais
  precisa do lado seguro. Vale até o fim DESTE mesociclo; abrir o seguinte o apaga. Efeito: RIR ≥ 3 e
  fator 0,75 no teto, via `Entrada.retornoConservador`.
- **`[INV]` Débito do 60+ FECHADO** (2026-10-28): `mesocicloAtual` real (`CicloDoMesociclo.paraPerfil`)
  tira o 60+ do modo conservador permanente que valia desde a Fatia 2/7. A regra em si não mudou;
  sem estado (nunca treinou) continua sendo o 1º mesociclo, o lado seguro. Iniciante 60+ segue
  conservador em qualquer mesociclo.

**Foco** (só o bônus de alvo; `VolumeTargets.bonusDeFoco`):

- **`[REGRA]`** Só INTERMEDIATE/ADVANCED, nunca GENERAL_HEALTH, no máximo 2 grupos. O bônus soma
  DEPOIS do fator de objetivo e respeita o MRV.
- **`[REGRA]` UM bônus por escolha do usuário:** +6 se o grupo cobre algum alvo grande, senão +3. Grupo
  de um alvo recebe o valor cheio; grupo que cobre vários alvos (`LEGS`) **reparte o valor único pelo
  peso** (3/2/1 em quadríceps/posterior/panturrilha, soma exata, aritmética inteira) — nada de somar
  o bônus cheio em cada alvo.
- **`[REGRA]` "grande" é DERIVADO do `PESO_PRIORIDADE`** com a fronteira declarada em 0,6
  (`FOCO_FRONTEIRA_GRANDE`): CHEST, BACK, SHOULDERS, QUADS, POSTERIOR, GLUTES grandes; BICEPS, TRICEPS,
  CALVES, FOREARMS pequenos. Fonte única, mesmo princípio da `ORDEM_SACRIFICIO`. **Não** é o
  conjunto `PROTEGIDOS_ALOCACAO` (proteção na alocação é outra pergunta).
- **`[INV]`** Eixo de foco na matriz limitado a nenhum / peito / ombro (`referencia_foco.txt`, 960
  perfis, arquivo à parte — `referencia_720.txt` não muda): é o que o tripwire de
  `ombro_vira_isolamento` exige; os nove grupos custariam tempo e tamanho sem ganho. Inclui
  GENERAL_HEALTH como controle (o foco é ignorado ali).
- **`[INV]` Reauditoria de `ombro_vira_isolamento`:** 0 disparos nos 720; **16 disparos em 8 perfis com
  foco em peito** (todos INTERMEDIATE, 6 dias, PPL). Entrou em `REGRAS_DE_SEGURANCA`, medida na UNIÃO
  das matrizes. Foco em ombro não a alcança.
- **`[INV]` O bônus de alvo é quase INERTE sem a Fatia 11:** medido, só 21 de 480 programas mudam por
  foco e o `ATENDIDO` cai, porque a capacidade da sessão é o limite — o bônus sobe o alvo, não o que
  cabe. A peça que faz o foco funcionar é a proteção contra sacrifício na alocação (Fatia 11).
  **Condição da troca de produção:** ligar entre a 10 e a 11 entrega um foco PERMANENTE e que PIORA a
  cobertura de quem o escolhe; ou a 11 vem antes, ou você aceita isso explicitamente
  (`fontes/debitos.md`).

**Persistência** (só interface + fakes nesta fatia; desenho em `trabalho/motor-v3-fatia-10-schema.md`;
V69 liberada em 2026-10-28):

- **`[REGRA]` D-1: a chave é `(usuário, exercício)` DE PROPÓSITO.** A progressão acompanha a pessoa;
  acrescentar `programa_id` faria o aluno voltar à calibração a cada programa novo com o mesmo
  exercício. O risco de estado velho ao retomar um exercício depois de meses é conhecido e já está
  coberto pela regra de pausa acima de 28 dias.
- **`[REGRA]` D-4: o mesociclo é por PESSOA.** `mesocicloAtual` entra no pipeline como atributo dela, e
  é isso que tira o 60+ do conservador permanente; por programa, o débito fechado voltaria, e quem
  regera o programa com frequência ficaria preso na semana 1, sem nunca deloadar. **Consequência
  conhecida (não é bug):** trocar de programa no meio do mesociclo pode fazer o deload da semana 4
  cair sobre exercícios com uma exposição só. É inofensivo (o deload mantém a carga e corta as
  séries pela metade) e é o preço de manter o contador na pessoa.
- **`[REGRA]` D-2:** `decidir()` não é idempotente; a idempotência vem de `progression_event` com PK
  `(user_id, exercise_id, session_id)` — a `workout_sessions.id` já nasce no cliente para o sync.
  Sem isso, um reenvio depois de timeout subiria a carga em dobro.
- **`[INV]`** A pausa se mede com `workout_sessions.created_at` (relógio do SERVIDOR), nunca com
  `started_at`/`finished_at` (relógio do cliente).

## Gates de geração (`/programs/generate`)

Máquina em ordem, cada um barra com Forbidden:

1. **Saúde (PAR-Q, §3.2):** `HealthScreening.gateSatisfied` = sem risco declarado OU risco reconhecido → senão `HEALTH_GATE_REQUIRED`.
2. **Idade (#24):** `ageGateSatisfied` = idade ≥18 OU minorSupervised (menor com supervisão declarada). ≥69 é só informativo; nulo não bloqueia → senão `AGE_GATE_REQUIRED`.
3. **Teto por plano (#27):** grátis 1 no total (IA ou manual); premium 3 no total (decisão de 2026-10-08, revista no mesmo dia de 2/4 para 1/3; quem já tinha mais mantém, só não cria) → senão `ENTITLEMENT_REQUIRED`.
4. **Blur/posse (#23):** o programa gerado sai com Dia 1 livre e dias >0 trancados (locked, exercícios removidos, lockedExerciseCount) para não-premium.
