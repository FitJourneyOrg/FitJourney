# Fatia 11 — nível, promoção e ênfase — plano

_Aberta em 2026-10-29. Rascunho: não é fonte da verdade (ver README desta pasta)._

## O que é e por que agora

A Fatia 10 mediu que o bônus de foco é quase inerte (21/480 programas mudam, `ATENDIDO` cai) porque
a capacidade da sessão é o limite, não o alvo. A peça que falta é a **proteção contra sacrifício na
alocação** para o grupo em ênfase — sem ela, ligar o motor v3 em produção entrega um foco permanente
que piora a cobertura de quem o escolhe. Esta fatia entrega essa proteção, e com ela o resto do que
depende de nível: a promoção a intermediário (diagnóstico que `AncoraDoIniciante.situacao` já deixou
pendente), a promoção a avançado (que a correção S2 da spec fechou a entrada e não abriu saída) e a
ênfase como fase de dois mesociclos (que é o que consome a proteção).

Três peças, nesta ordem de dependência: (1) proteção na alocação — sem ela a ênfase não entrega nada,
igual à Fatia 10; (2) ênfase como fase (consome a proteção); (3) nível e promoção — independente das
outras duas, mas compartilha o padrão de diagnóstico por deload com `AncoraDoIniciante`.

## Perguntas que preciso responder antes de codar

- [x] Restrição de seleção de puxada horizontal no BACK — **decidido: `[REGRA]` nesta fatia, nos
  dois lados** (implementado e verificado, ver seção própria abaixo).

**Resolvidas por inferência, a confirmar se eu errar a leitura:**
- "No piso, durante a fase": implementado como ajuste do ALVO SEMANAL pré-alocação (mesma camada do
  bônus de foco da Fatia 10 — `VolumeTargets.alvos()`), não como mudança no piso prescritível da
  alocação (Fatia 8, outra camada). Motivo: o bônus já mexe no alvo antes da alocação; o piso dos
  demais grupos é a mesma operação com sinal trocado, e as duas precisam compor antes de `alocar()`
  rodar, não depois.
- Orçamento do bônus (+3/+6, total não por grupo, máx. 1 grande): **convive** com
  `VolumeTargets.bonusDeFoco` (Fatia 10) — é a MESMA regra, já implementada. O que esta fatia
  acrescenta é a proteção na alocação, o piso dos demais grupos e a duração de 2 mesociclos; não há
  bônus novo a desenhar.

## Decisões tomadas

- **Proteção da ênfase = extensão dinâmica de `PROTEGIDOS_ALOCACAO`, nunca lista paralela.** Motivo:
  a Fatia 8 já mostrou o custo de duas fontes respondendo à mesma pergunta. `alocar()` recebe o(s)
  grupo(s) em ênfase e os trata como protegidos só durante a fase. **A verificar ao implementar:**
  medir o volume entregue do músculo em ênfase, com e sem a proteção, por tamanho de músculo — se a
  proteção sozinha não mover o número, o próximo recurso é peso temporário elevado, medido antes de
  implementar.
- **Nível continua autodeclarado; o motor sugere promoção, o usuário confirma.** Nunca promove
  sozinho — nível libera exigência técnica, e progresso de carga comprova capacidade, não técnica.
- **A sugestão de promoção é DERIVADA, nunca armazenada** (recalculada do histórico que a V69 já
  persiste). A migration desta fatia é só o registro de RECUSA. Aceitar muda `profiles.level`
  (V2, já existe). Esquema definitivo abaixo.
- **Tolerâncias sem fonte ("estagna de novo", "poucas reduções") são heurísticas calibráveis**, mesmo
  tratamento de `REDUCAO_RETORNO`/`ESTAGNACAO_SESSOES` (Fatia 10). Os diagnósticos de promoção são
  eventos no tempo, inalcançáveis na matriz dos 720/960 por construção — teste unitário com histórico
  construído, `[INV]` de inalcançabilidade registrado ao lado.
- **"Puxar nunca cai abaixo de empurrar" é a razão de uma ISENÇÃO (ênfase em CHEST/SHOULDERS → BACK
  no volume normal, não no piso), não uma desigualdade geral a impor.** `SHOULDERS` como alvo de
  volume mistura exercícios de padrão de puxar na curadoria (Face Pull, Crucifixo Inverso) — somar o
  alvo inteiro como "empurrar" contaria o antídoto como veneno. Nota para
  `docs/regras/01-motor-de-geracao.md` ao promover, para ninguém tentar de novo.
- **Nenhuma restrição de volume puxar×empurrar nesta fatia.** A métrica refinada (abaixo) corta
  puxada VERTICAL dos dois lados de propósito — é o eixo postural certo, mas isso a torna "puxada
  horizontal + deltoide posterior contra TODO o empurrar", não "puxar contra empurrar" em geral.
  **Fixar um limiar numérico sobre um eixo recortado não se sustenta** — o número (81,4%, mediana +7)
  é real no recorte que ele mede, mas generalizar para "puxar vs. empurrar" seria o mesmo erro que
  rejeitamos na desigualdade por alvo: confundir uma medida parcial com a coisa inteira. Fica como
  métrica de auditoria permanente, sem imposição — ver desdobramento de seleção abaixo, que é a
  pergunta certa para este achado.

## Esquema — `promotion_decline` (aprovado, pendente só da restrição de BACK para fechar o plano)

```sql
-- Fatia 11: registra so a RECUSA de uma sugestao de promocao. A sugestao em si NAO e persistida --
-- e derivada do historico que a V69 ja guarda (exercise_progression, progression_event). Isto so
-- existe para o motor saber que ja perguntou e quando pode perguntar de novo.
--
-- D-1  PK (user_id, nivel_proposto), upsert a cada recusa -- guarda so a MAIS RECENTE por par.
--      Historico completo de recusas omitido DE PROPOSITO: o unico consumo desenhado aqui e "posso
--      perguntar de novo?", que depende so da ultima. Append-only (padrao progression_event) e
--      migration aditiva se um consumidor de historico aparecer depois.
-- D-2  `mesociclo_na_recusa` copiado de `user_mesocycle.numero` no momento da recusa -- NAO e
--      heuristica de calendario (a primeira proposta, 8 semanas, foi descartada: erra com pausa e
--      recalibracao). [REGRA] pode sugerir de novo quando `mesociclo_atual > mesociclo_na_recusa + 1`
--      (passou do mesociclo SEGUINTE ao da recusa). Sem numero calibravel aqui -- e contagem exata,
--      nao aproximacao.
-- `recusado_em` e TIMESTAMP (sem fuso), nao TIMESTAMPTZ: conferido contra as 51 colunas de timestamp
-- de TODAS as migrations do projeto (`grep -rho TIMESTAMPTZ\|TIMESTAMP`) -- nenhuma usa TIMESTAMPTZ,
-- V69 incluida. Manter TIMESTAMP e consistencia com o projeto inteiro, nao so com a V69.
CREATE TABLE promotion_decline (
    user_id             UUID      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    nivel_proposto      TEXT      NOT NULL CHECK (nivel_proposto IN ('INTERMEDIATE', 'ADVANCED')),
    mesociclo_na_recusa INT       NOT NULL CHECK (mesociclo_na_recusa >= 1),
    recusado_em         TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, nivel_proposto)
);
```

## Medição — puxar × empurrar

Duas versões, sobre o `entregue`. Script em `tools/cache/medir_push_pull.py`.

**1 — simples, por `movement_pattern`**: exclui toda isolação `NONE` dos dois lados (Crucifixo
Inverso, Elevação Lateral/Frontal). Mede só COMPOSTOS: 720 sem foco 59,6% push>pull (mediana 4); 960
foco CHEST/SHOULDERS 58,1% — a ênfase não move o número.

**2 — refinada**: PUXAR = `HORIZONTAL_PULL` + deltoide posterior (família `CRUCIFIXO_INVERSO`).
EMPURRAR = `VERTICAL_PUSH`+`HORIZONTAL_PUSH` + deltoide anterior/lateral (`ELEVACAO_LATERAL`,
`ELEVACAO_FRONTAL`). Puxada VERTICAL fica fora dos dois lados de propósito (outro eixo, não o
postural). 720 sem foco: **81,4%** push>pull, mediana +7 séries/semana, máx 22. 960 foco: 77–78%,
mesma mediana. **Decisão: nenhuma restrição de volume agora** (ver acima) — o achado foi redirecionado
para a pergunta de seleção abaixo, que é a forma certa de agir sobre ele.

## Seleção de puxada horizontal no BACK — `[REGRA]` IMPLEMENTADA no oráculo

`aplicar_puxada_horizontal_semanal()` em `motor_v3.py`, chamada ao fim de `gerar()` (não muda
`entregue`/convergência do ponto fixo — mesmo alvo, papel e séries, só troca QUAL exercício ocupa a
vaga). `Exercicio` ganhou o campo `movement_pattern` (default `"NONE"` para não quebrar construtores
de teste antigos) — nota adicionada ao cabeçalho do arquivo, que antes dizia "sem padrão de
movimento" no carregamento: isso continua valendo para o CASAMENTO músculo↔exercício
(`volume_target` continua sendo a única fonte disso, `[REGRA]` de 2026-10-14 intacta); o novo campo
serve só para SELEÇÃO, mesmo papel que `familia` já tinha.

**Redação**: com 2+ vagas de BACK na semana, ao menos uma é `HORIZONTAL_PULL`. Com 1 vaga, a regra
NÃO se aplica (`puxada_horizontal_regra_nao_aplicavel`) — não há o que balancear.

**As três verificações pedidas, medidas no catálogo real (720 + 960 com foco):**

1. **Espelho ("ao menos uma vertical"): NÃO escrito, por motivo, não por omissão.**
   `puxada_vertical_semanal_ausente` dispara em **360/720 (50%) e 480/960 (50%)** — mas **100% dos
   casos são CASA, 0% ACADEMIA**. **Por que não virou `[REGRA]` espelho:** não é defeito de seleção
   simétrico ao do BACK — é falta de EQUIPAMENTO, não ordem de prioridade. A matriz é "CASA sem
   acessório nenhum" (pior caso já documentado); sem `requires_pullup_bar` nem cabo, o pool de
   `VERTICAL_PULL` para BACK fica **vazio** em CASA — exigir a regra ali pediria o impossível (zero
   candidatos, nenhuma seleção resolveria isso). Registrando o motivo aqui, não só o número, para
   ninguém reabrir pedindo a regra espelho achando que foi esquecimento.
   **Mesmo achado, anexado ao débito de acessórios:** "casa sem acessório fica sem puxada vertical"
   era conhecido qualitativamente (mesma família do achado de 2026-10-27 em `fontes/debitos.md` —
   "Isolamento de CASA é raso para os 4 protegidos" e "buraco de catálogo: CASA sem isolamento de
   costas", ambos resolvidos por acessório, não por curadoria) — agora tem número exato:
   **360/720 perfis (100% dos CASA)**. Promover esta linha a `fontes/debitos.md` no fechamento, com
   o número, ao lado dos achados de acessório da Fatia 8.
2. **Conflito com família por sessão**: `puxada_horizontal_sem_candidato` = **0 em ambos** (720 e
   960) no catálogo real. A correção respeita a preferência de família inédita no dia (mesma lógica
   de `preencher()`), mas CEDE a preferência quando é a única saída — testado isoladamente em
   `test_familia_repetida_no_dia_nao_impede_a_correcao_quando_e_a_unica_saida`.
3. **Disparos e efeito**: `puxada_horizontal_semanal_corrigida` = **186/720 e 372/960** — exatamente
   os perfis que estavam sem horizontal antes da regra existir (25,8% e 38,8%, medidos na rodada
   anterior). Confirmado por teste (`test_academia_sem_foco_nunca_fica_sem_puxada_horizontal`) e por
   nova medição: **0 perfis com 2+ vagas de BACK ficam sem `HORIZONTAL_PULL` depois da correção**, nos
   dois conjuntos. `pool nunca vazio`: confirmado (item 2). Causa diagnosticada e registrada: `Barra
   Fixa` (prioridade 1) e `Puxada Alta` (prioridade 2) são as duas prioridades mais altas do alvo
   BACK em academia, ambas `VERTICAL_PULL` — por isso o problema é só de academia (em casa, sem essas
   duas opções por falta de equipamento, a seleção cai direto nas horizontais) e nunca de casa.

**Diff nas referências**: exatamente 186 linhas `V` mudaram em `referencia_720.txt` (546 linhas `D`,
= 186 corrigidos + 360 do espelho medido) e 372 em `referencia_foco.txt` (852 = 372 + 480) — nenhuma
outra linha tocada, confirmando que a mudança é isolada. Amostra: `Barra Fixa` → `Remada Curvada com
Barra`, mesmo alvo/papel/séries. 5 testes novos em `test_motor_v3.SelecaoDePuxadaHorizontalNoBack`
(feliz, não-aplicável, sem candidato, família cedendo). Suíte completa: **134/134 verde**
(`python3 -m unittest test_motor_v3 test_progressao_v3`).

## Proteção dinâmica + piso da ênfase — IMPLEMENTADO no oráculo

`alocar()` ganhou `protegidos_extra: frozenset` (extensão dinâmica de `PROTEGIDOS_ALOCACAO` só para
a chamada — nunca lista paralela, mesmo `[INV]` de fonte única da Fatia 8). Protege de rebaixamento e
sacrifício (os dois nomeados na spec, "nunca é sacrificado nem rebaixado"); **não** protege do corte
proporcional — tratamento igual ao que CHEST/BACK/QUADS/POSTERIOR já recebem hoje (a spec da ênfase
não fala de corte proporcional, que é invenção da Fatia 8, posterior ao conceito de
`PROTEGIDOS_ALOCACAO`).

`alvos()` ganhou o piso: com foco ativo, todo alvo FORA do foco vai a `VOLUME[m][0]` (a mesma coluna
que o iniciante usa) — exceto `BACK`, isento quando a ênfase é em `CHEST` ou `SHOULDERS` (volume
NORMAL, não piso — a isenção nomeada, não uma desigualdade geral). Sem foco, resultado idêntico ao de
antes (confirmado: `referencia_720.txt` não mudou um byte).

**Verificação pedida (decisão de implementação, "medir com e sem a proteção"), nos 960 (foco
peito/ombro, os únicos elegíveis):**

| configuração | `ATENDIDO` nos alvos em foco | séries entregues nos alvos em foco |
|---|---|---|
| baseline Fatia 10 (sem piso, sem proteção) | 182/960 — 19,0% | 6.407 |
| só piso (proteção dinâmica desligada) | **271/960 — 28,2%** | 7.680 |
| piso + proteção (implementação atual) | 271/960 — 28,2% | **8.272** |

**Leitura**: o PISO é o que converte `ATENDIDO` (182→271, +89 pares) — é ele que abre espaço de
sessão, confirmando a hipótese da Fatia 10 ("o bônus sozinho é quase inerte porque a capacidade é o
limite"). A proteção dinâmica, isolada, não converte NENHUM par adicional para `ATENDIDO` nesta
amostra — mas entrega **+592 séries** acima do que o piso sozinho já garantia, sob escassez real
(`disparos["escassez"]` ocorre em **680/960 — 70,8%** destes perfis: não é caso raro). Leitura mais
provável: o piso já abre espaço suficiente para o foco cruzar o mínimo na maioria dos casos; a
proteção garante que, quando a escassez aperta mais, o músculo em ênfase não perde o que já tinha
para o laço de rebaixamento/sacrifício, e fica com fatia maior da redistribuição final de sobra —
daí a série extra sem mudar o status. Não bati o "próximo recurso" (peso temporário elevado) porque
a proteção sozinha JÁ move volume real (+592 séries, +7,7%), mesmo sem mover `ATENDIDO` isoladamente
nesta amostra — decisão de ir além fica para você, com o número em mãos.

**Testes e referências**: `test_motor_v3.py` atualizado — 4 testes da Fatia 10 (`FocoDoQuiz`) que
assumiam "demais grupos inalterados" foram CORRIGIDOS para a nova realidade (comentário explica o
porquê em cada um, não só o número novo), e 1 teste novo isola a isenção do BACK
(`test_back_fica_no_normal_quando_a_enfase_e_em_empurrar`). A reauditoria do
`ombro_vira_isolamento` (Fatia 10) mudou de 8→72 perfis — **efeito esperado, não regressão**: o piso
libera espaço de sessão para o peito, que é exatamente o ponto desta fatia; medido e documentado no
teste, não ajustado de cabeça. `referencia_720.txt` sem diff (foco vazio não aciona nada).
`referencia_foco.txt`: diff extenso e esperado (piso reduz vagas dos músculos fora do foco — total de
linhas `V` cai de 4.582 para 3.846). Suíte completa: **135/135 verde**.

## Custo da fase para os demais grupos — MEDIDO, decisão fechada

Pergunta: a proteção dinâmica evita sacrifício/rebaixamento do grupo em ênfase, mas o PISO em
`alvos()` reduz os demais grupos de propósito — qual o custo disso para quem não está em ênfase?
Peso temporário elevado ficaria fora de escopo SE o custo for pequeno (regra de decisão fixada
antes da medição, não depois).

Medido nos 960 (`PERFIS_FOCO`), fase ligada (piso + proteção, atual) × fase desligada (só bônus,
Fatia 10), restrito aos alvos FORA do foco de cada perfil (o conjunto de "fora do foco" é o mesmo
nos dois lados — calculado uma vez, sem patch, para a comparação por músculo não comparar coisas
diferentes):

| métrica (fora do foco, 960 perfis) | fase desligada | fase ligada | delta |
|---|---|---|---|
| pares `ABAIXO_DO_MINIMO` | 1.612 | 1.622 | +10 |
| pares `NAO_TREINADO` | 952 | 1.027 | +75 |
| pares `MANUTENCAO` | 3.797 | 753 | −3.044 |
| soma `ABAIXO_DO_MINIMO` + `NAO_TREINADO` | 2.564 | 2.649 | +85 (+3,3%) |
| perfis com `entregue > capacidade` (`[INV]` Fatia 8) | 0/960 | 0/960 | sem violação |

Nota sobre a linha de base citada (2.143 nos 720): a remedição direta nos 720 sem foco, pelo mesmo
critério (`ABAIXO_DO_MINIMO` puro, todos os músculos), deu **1.429**; somando `NAO_TREINADO` dá
**2.188**. Não bate exatamente com 2.143 em nenhuma das duas leituras — registrado como está, sem
forçar o número a casar; o catálogo pode ter mudado desde a medição original. O que importa para a
decisão é a comparação fase-ligada × fase-desligada acima, que usa o mesmo oráculo e o mesmo
catálogo nos dois lados.

**Item 4 — o número decisivo**: perfis com algum grupo fora do foco caindo de `ATENDIDO` (fase
desligada) para `ABAIXO_DO_MINIMO`/`NAO_TREINADO` (fase ligada) por causa da fase: **10/960 — 1,0%**.

Causa, não só o número: os 10 casos são `INTERMEDIATE` + `MAINTAIN` + foco `SHOULDERS` + 3 ou 4
dias (ACADEMIA e CASA), e o músculo que cai é sempre `GLUTES`. `MAINTAIN` já reduz o alvo-base de
todos os músculos (`FATOR_OBJETIVO_ALVO`); `GLUTES` não entra na expansão de `SHOULDERS`
(`FOCO_PARA_ALVOS`) e tem `PESO_PRIORIDADE` baixo, então é o primeiro a sentir o piso; com 3-4 dias
a capacidade por sessão já é a mais apertada da matriz. É o encontro dos três fatores mais fracos
(objetivo que mais reduz alvo, músculo de menor prioridade, menor capacidade) — não uma falha geral
da fase, nem um padrão que se repete fora dessa combinação específica.

**Decisão (regra pré-fixada aplicada)**: item 4 é pequeno (1,0%, um músculo, uma combinação
estreita) → **fecha assim**. Peso temporário elevado fica fora de escopo desta fatia, registrado em
`docs/fontes/debitos.md` ao fechar como recurso disponível e não usado (reabrir só se a telemetria
em produção mostrar `GLUTES` reclamado nesta combinação específica, não antes).

## Diagnósticos de promoção — IMPLEMENTADOS no oráculo (progressao_v3.py), em paralelo

Três funções novas, todas puras sobre histórico já existente (sem estado novo persistido, confirma
a decisão #3 — "a sugestão é derivada, não armazenada"):

**`diagnostico_promocao_iniciante(decisoes)` — `[REGRA]`, segue a `ESPECIFICACAO` ao pé da letra.**
Opera sobre o histórico de UMA âncora (mesma entrada de `situacao_iniciante`, que já existe desde a
Fatia 10). Devolve `PROMOVER` (subiu depois do último deload e reestagnou — "ganhos fáceis
acabaram"), `RECUPERACAO` (nunca subiu depois do deload — fora do programa, sem promoção),
`RECUPERACAO_SEM_RETOMADA` (dois deloads consecutivos SEM retomada entre eles — gatilho do aviso do
app sobre sono/alimentação/estresse, que a spec pede; retomada seguida de nova estagnação não conta
para este aviso, é `PROMOVER`) ou `None` (nada a diagnosticar ainda). Bug pego pelo próprio teste
antes de fechar: minha primeira versão checava só "existe deload anterior", não "houve retomada
*entre* os dois últimos deloads" — `test_retomada_entre_dois_deloads_nao_conta_como_sem_retomada`
pegou, corrigido.

**`diagnostico_promocao_do_perfil(lista)` — `[REGRA]` (decisão do Rafael, 2026-10-29).** Agrega o
diagnóstico de todas as âncoras do perfil: promove com **2 âncoras em `PROMOVER`, ou 1 se o perfil
só tiver uma âncora**. Motivo registrado: o nível barra exigência técnica (acesso a barra livre); o
lift que estagna primeiro — tipicamente o mais isolado — não representa a maturidade da pessoa
inteira, e promover por ele abriria o portão cedo demais. Número (2) é **calibrável**, não
evidência — mesma família do 10%/3 sessões já aprovados.

**Medido antes de fixar**: nos 240 perfis `BEGINNER` da matriz de 720, o catálogo atual NUNCA gera
programa com 1 só vaga preenchida — mínimo 5, mediana 14, máximo 17. A exceção "1 âncora" do limiar
é defesa declarada **sem caso vivo hoje** (mesma situação do `SEM_CATALOGO`/`V.mrv` da Fatia 8) —
fica no código para quando o catálogo permitir perfil de baixa capacidade, não é remendo de bug.

**Disparo medido não é telemetria real** (o cliente não envia RIR ainda — débito já registrado na
Fatia 10, "ZERO disparos em produção"), então não há histórico de sessão de verdade para contar
quantos perfis promoveriam. Em vez de inventar histórico, medi a sensibilidade combinatória sobre a
distribuição REAL de nº de âncoras (5 a 17, acima) variando a probabilidade `p` de UMA âncora
chegar a `PROMOVER` (parâmetro, não medição):

| p por âncora | limiar 1 (qualquer) | limiar 2 (o fixado) | "maioria" (>50% das âncoras) |
|---|---|---|---|
| 0,05 | 46,3% dos perfis | 13,3% | 0,0% |
| 0,10 | 70,8% | 35,8% | 0,1% |
| 0,20 | 90,8% | 70,2% | 0,9% |

**Achado que motivou fixar em 2, não "maioria"**: com 5-17 âncoras por perfil, "maioria" (8 de 14
na mediana) é praticamente inatingível em qualquer `p` plausível — exigiria que quase toda âncora
estagnasse e reestagnasse ao mesmo tempo, o que o desenho de progressão dupla não produz (âncoras
de músculos diferentes progridem em ritmos diferentes). O limiar fixo de 2 é o que de fato
distingue "alguns ganhos fáceis acabando" (ruído, 1 âncora) de "o padrão geral mudou" (2+), sem
exigir unanimidade que nunca ocorre. Registrado em `docs/fontes/debitos.md` como heurística
calibrável — revisar primeiro se a telemetria (quando o RIR chegar) discordar.

**`diagnostico_promocao_avancado(um_rm_inicio, um_rm_fim, reducoes_no_periodo,
limite_poucas_reducoes=1)` — `[PROPOSTA]`, número calibrável (a spec dá a direção, não o valor,
igual aos 10%/3 sessões já aprovados).** Pré-condições (adesão, sem dor em escalonamento, âncora
congelada no período) são do CHAMADOR, não verificadas aqui — a função só compara dois números.
`um_rm_fim > um_rm_inicio` → `None` (ainda progride, nada a diagnosticar). Sem ganho e `reducoes_no_periodo
<= limite_poucas_reducoes` → `PROMOVER`. Sem ganho e muitas reduções → `RECUPERACAO` (aviso, sem
promoção). Comecei o limite em 1 (uma sessão com `DESCE` no período ainda é "sem ganho real"; duas
ou mais é recuperação) — primeiro a revisar se a telemetria discordar.

**`pode_sugerir_promocao_de_novo(mesociclo_atual, mesociclo_na_recusa)` — `[REGRA]` aprovado
(decisão #4 da sessão anterior).** `mesociclo_atual > mesociclo_na_recusa + 1`; `None` = nunca
recusou, sempre pode sugerir.

Testes: 4 classes novas em `test_progressao_v3.py` (`DiagnosticoDePromocaoDoIniciante`,
`DiagnosticoDePromocaoDoPerfil`, `DiagnosticoDePromocaoAoAvancado`, `PodeSugerirPromocaoDeNovo`),
cada uma com caminho de falha. Suíte completa (motor + progressão): **155/155 verde**.

**Ainda falta**: wiring com `AncoraDoIniciante.situacao` (quem chama o diagnóstico e quando — a
cada sessão? só quando `situacao_iniciante` retorna `ESTAGNACAO_DELOAD` pela 2ª vez+?); onde o
`mesociclo_na_recusa`/nível proposto são persistidos (a tabela `promotion_decline` já desenhada);
e o Kotlin (`engine.v3.Progressao`/`AncoraDoIniciante`/novo objeto de promoção) — tudo deferido até
a revisão deste diff.

## Desenho

### Servidor

**Oráculo primeiro** (`server/src/test/oracle/`):
- `motor_v3.py::alocar()` ganha o parâmetro de proteção dinâmica (grupo(s) em ênfase).
- `spec_v3.py`/`motor_v3.py` ganham o piso dos demais grupos durante a fase (pré-alocação, mesma
  camada do bônus de foco) e a duração de 2 mesociclos.
- `progressao_v3.py` ganha o diagnóstico de promoção (iniciante→intermediário por deload-sobe-estagna,
  intermediário→avançado por 1RM estimado congelado em 2 mesociclos) e `pode_sugerir_promocao_de_novo`
  (mesociclo_atual, mesociclo_na_recusa).
- Métrica de auditoria puxar/empurrar (refinada) entra como medição permanente no oráculo, sem
  asserção de limiar. Se a restrição de seleção de BACK for aprovada, vira `[REGRA]` com teste.
- Regenerar referências se a saída mudar; mostrar o diff antes de tocar no Kotlin.

**Kotlin**, só depois do oráculo verde: `PriorityAllocation`, ênfase (piso+duração em `VolumeTargets`
ou objeto novo), `AncoraDoIniciante` (diagnóstico a intermediário), objeto novo de promoção a
avançado, `ProgramAudit` (métrica puxar/empurrar + seleção de BACK se aprovada), repositório de
`PromotionDecline` + fake, `MotorV3Pipeline` fiando tudo.

### Contrato
- Campo de ênfase no `ProfileDto` (confirmar se já existe).
- Endpoint para aceitar (muda `profiles.level`) ou recusar (grava `promotion_decline`) a sugestão.

### Cliente
- Aviso acionável de ênfase que não cabe, confirmação de promoção, aviso de 2 deloads sem retomada —
  todos textos de Presentation, fora do escopo de servidor desta fatia.

> Ordem de implementação: Backend → Contrato → Cliente. Dependências apontam para o `Domain`.

## Migration

`promotion_decline` (esquema acima, aprovado). Não apaga nem reescreve dado existente.

## Testes (C2)

| Tier | O que | Onde |
|---|---|---|
| 1 — lógica pura | proteção dinâmica em `alocar()`; piso+duração da ênfase; diagnóstico de promoção (histórico construído); `pode_sugerir_promocao_de_novo`; métrica puxar/empurrar; seleção de BACK se aprovada | `PriorityAllocation`/ênfase/`AncoraDoIniciante`/promoção a avançado/`ProgramAudit` |
| 2 — serviço/ViewModel com fake | pipeline completo com e sem proteção; fluxo sugestão→recusa→reagendamento com fake de `PromotionDeclineRepository` | `MotorV3Pipeline` |
| 3 — rota/repositório, Testcontainers | `promotion_decline` real (upsert, leitura da última recusa) | `PromotionDeclineIntegrationTest` |

Cada um com ao menos um caminho de falha.

## Fora de escopo, por decisão

- Restrição de VOLUME puxar×empurrar — decidido: nunca (eixo recortado, não generaliza).
- Texto/UI dos avisos e confirmações (Presentation).
- Fatia 12 — depende desta.
- Validação profissional de número novo (CREF) — já fora de escopo geral do motor.

## Ao fechar — promover antes de apagar

- [ ] `[REGRA]`/`[INV]` → `docs/regras/01-motor-de-geracao.md` (nota de `SHOULDERS` misturar padrão
  de puxar; métrica de auditoria puxar/empurrar com os números; regra de seleção de BACK se aprovada)
- [ ] decisão de arquitetura → `docs/arch/` (esquema `promotion_decline`, D-1/D-2)
- [ ] fora de escopo → `docs/fontes/debitos.md`
- [ ] narrativa e defeitos achados → `docs/historico/motor-v3-fatia-11.md`
- [ ] migration → `docs/migrations/INDICE.md`
- [ ] `ESTADO.md` atualizado, `gen_tudo.py` rodado
- [ ] `tools/cache/medir_push_pull.py` — decidir se apaga ao fechar
- [ ] **este arquivo apagado**
