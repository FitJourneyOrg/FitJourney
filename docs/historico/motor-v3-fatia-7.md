# Motor v3 — Fatia 7: regras de segurança (fechada em 2026-10-24)

Sétima fatia do motor v3, etapa [8] parte 1: RIR mínimo por propriedade, modo conservador
automático, rampa (correção de especificação), e a decisão de não construir troca de barra nem
substituições articulares.

## Achado 1 — convenção de mesociclo divergia entre oráculo e Kotlin

`tools/motor_sim/motor_v3.py` usava `primeiro_mesociclo: bool = False` por padrão; o Kotlin
(`CapacityModel.fatorIdade`, `[REGRA]` da Fatia 2) trata `mesocicloAtual == null` como 1º
mesociclo — convenção OPOSTA. Nenhum perfil da matriz de 720 setava o campo, então todo 65 anos
não-iniciante do oráculo vinha recebendo fator de idade 1,0 (sem redução) em vez de 0,75.

Medido antes de decidir (pedido do Rafael): alocado > capacidade foi de 154/720 (21,4%) para
176/720 (24,4%); entregue > capacidade, de 268/720 para **297/720 (41,2%) — confirmando que esse
número, já registrado em `historico/motor-v3-fatia-5.md` desde a Fatia 5, estava certo o tempo
todo** (a divergência de 268 vinha do bug de mesociclo, não de uma fórmula diferente). Tabela
completa de antes/depois em `historico/motor-v3-fatia-4.md` e `historico/motor-v3-fatia-5.md`
("Recalculado após alinhar convenção do mesociclo").

Corrigido: `mesociclo_atual: Optional[int] = None` + função compartilhada
`primeiro_mesociclo(mesociclo_atual)`, igual à convenção do Kotlin, usada pelo fator de idade E
pelo modo conservador desta fatia. `test_motor_v3.py` continua com os mesmos 3 vermelhos
pré-existentes depois da correção (confirmado rodando a suíte inteira) — a divergência não
escondia nem destampava nenhum outro defeito.

**Por que as validações "idênticas" das Fatias 2 e 3 não pegaram isso** (registrado em
`historico/motor-v3-fatia-4.md`, resumo aqui): nenhuma cruzava Kotlin×Python num perfil 60+ com
mesociclo não setado, pelo desenho do próprio teste. `CapacityModelTest` (Fatia 2) testava
`fatorIdade` contra a tabela da especificação (documento), não contra `teto_sessao()` do oráculo.
`AllocationTest` (Fatia 3) comparava `alocar()`, que recebe a capacidade já calculada como
parâmetro — nunca passa pelo trecho com o bug. A auditoria dos 720 perfis rodava só o lado
Python. O teste de 12 perfis da Fatia 6 usa idade=30 fixo, então também não cruzaria. O bug só
ficou visível quando esta fatia precisou da mesma convenção nos dois lados para o modo
conservador.

## Achado 2 — RIR mínimo por propriedade não é derivável de colunas existentes

`movement_pattern` não serve: HINGE pega a Elevação Pélvica com Barra (mesmo padrão do Stiff, mas
sem risco à coluna) e não pega a Remada Curvada com Barra (HORIZONTAL_PULL) — o caso que levou a
especificação a trocar enumeração por propriedade. VERTICAL_PUSH não é "sobre a cabeça" (mergulhos
são VERTICAL_PUSH sem passar a carga sobre a cabeça). `contraindications` pega LUMBAR em
máquina/Smith (falha segura) e é dado de aviso ao usuário desde 21/10 — o motor não lê essa coluna
em tempo de execução.

A lista candidata foi gerada OFFLINE, uma vez, contra `tools/curadoria_oficial/curadoria_canonica.csv`
(a fonte certa — `tools/cache/principal_midia.csv`, de 10/10, é anterior à curadoria dos 147).
Aprovados pelo Rafael (V67, `rir_minimo_seguranca = true`): Agachamento Livre com Barra, Afundo
com Barra, Levantamento Terra, Stiff com Barra, Remada Curvada com Barra, Remada Cavalinho no
Landmine, Desenvolvimento Militar com Barra, e Flexão em Parada de Mão na Parede (decisão do
agente, confirmada pelo Rafael: sem carga externa, mas falha é cair de cabeça — mesmo risco dos
outros 7). Halteres (falha solta o peso) e máquina/Smith (trajetória guiada) ficaram de fora por
desenho, não por esquecimento.

## Troca de barra para o iniciante — não construída

A especificação (`motor-v3-ESPECIFICACAO.md`, linhas 521 e 765) manda o gatilho ser `BEGINNER`
literal, não o modo conservador. Os 8 exercícios aprovados são todos `level`
INTERMEDIATE/ADVANCED — o filtro de nível cumulativo do `CatalogPool`/`pool()` já garante que
nenhum BEGINNER os recebe, então a regra já está satisfeita pela curadoria sem código. Uma troca
"para o iniciante" daria zero disparos garantido (regra morta por desenho do catálogo, não por
bug). Decisão do Rafael: não estender para o modo conservador (seria regra nova, fora da
especificação). Marcado nas duas fontes (especificação e `docs/regras/01-motor-de-geracao.md`),
com a mesma data.

**[INV]** Nenhum perfil BEGINNER recebe exercício com `rirMinimoSeguranca=true`. Medido nos 720
perfis do oráculo: **0 violações**. Testado no Kotlin via `CatalogPool` com fixture mista
(`SafetyRulesTest`).

## Rampa — correção de especificação, não de código

`desenho-e-fatias.md` descrevia "3 séries no primeiro composto de barra, 2 em barra livre de
outra região, 1 em máquina" — uma rampa que varia por posição/equipamento do exercício. A
especificação ("Aquecimento") descreve outra coisa: rampa de CARGA fixa (50%×8, 70%×4, 85%×2 da
carga de trabalho), 3 séries, uma vez por padrão pesado — fora do volume, dentro do tempo. As
duas não são a mesma rampa; pela regra do projeto, a especificação venceu e o doc foi corrigido.
Nenhum código novo: o overhead que contabiliza essa rampa já existe desde a Fatia 2
(`CapacityModel.overheadMinutos()`, 16min full body / 11min upper-lower/PPL) — era descrição
errada no plano, não lacuna de implementação.

## Substituições articulares — descontinuadas

Dependiam de limitação declarada (`profiles.limitations`) para decidir a troca; a decisão de
21/10 (V65) tirou limitação declarada das decisões do motor (vira aviso ao usuário). Marcado
`[VENCIDO]` na especificação; não construído.

## `ombro_vira_isolamento` — convertido para padrão enumerado

Zero disparos nos 720 perfis reais (sem foco do quiz): `peito_composto_do_dia` máximo observado
é 7, a regra precisa de 8. Não é regra morta — `SessionCompositionTest` (existente) já cobre o
mecanismo com entrada construída (CHEST=11→peito=8, dispara). Removido de `REGRAS_DE_SEGURANCA`
em `test_motor_v3.py` (que exige disparo não-zero na matriz) e substituído por
`test_ombro_vira_isolamento_inalcancavel_sem_foco`, que afirma **zero** disparos hoje — vira
tripwire: quando o foco (+3/+6, Fatia 10) entrar na matriz e algum perfil passar de 7 compostos
de peito no dia, esse teste fica vermelho, sinalizando a reauditoria (mover a regra de volta pra
`REGRAS_DE_SEGURANCA`). 10/3 não mudou.

Depois dessa conversão, `test_motor_v3.py` tem só os 2 vermelhos pré-existentes do diferencial
GLUTES→POSTERIOR em `ADVANCED` (pergunta aberta da Fatia 9) — confirmado: 24 testes, 2 falhas, 0
erros.

## Disparos medidos nos 720 perfis (oráculo, `test_motor_v3.py::PERFIS`)

Modo conservador por iniciante: 240/720. Por saúde geral: 180/720. Por 60+ no 1º mesociclo:
360/720. União (modo conservador ativo, ao menos um gatilho): 540/720. RIR 2 por propriedade:
123 perfis (335 vagas). RIR 3 por modo conservador: 540 perfis (7217 vagas). Nenhum gatilho com
zero disparos.

## Varredura dos 720 perfis contra o catálogo real, em Kotlin — fica para a Fatia 9

Mesma leitura já registrada no fechamento da Fatia 6 (critério de pronto da Fatia 9): precisa de
um parser de perfis em massa cross-language, e acopla o teste de catálogo ao de pipeline. Agora
também cobre os disparos de `modo_conservador_ativo`/`rir_2_por_propriedade`/
`rir_3_modo_conservador`, medidos só no oráculo nesta fatia.

## Condição registrada para a troca de produção (pós-Fatia 9)

Até a Fatia 10 (mesociclo) existir, TODO perfil 60+ fica no modo conservador e com o fator de
idade 0,75 PERMANENTEMENTE (`mesocicloAtual == null` conta como 1º mesociclo, sem rastreio de
mesociclo nenhum 60+ sai do 1º). Não é bug — é o lado seguro, documentado desde a Fatia 2 — mas é
condição explícita da troca: ou a Fatia 10 vem antes da troca do `StructureEngine` em produção,
ou o Rafael aceita isto explicitamente ao ligar. Registrado em `trabalho/motor-v3-desenho-e-fatias.md`,
Fatia 9.

## Testes

`SafetyRulesTest` (Kotlin, novo): 8/8 verde — 3 gatilhos do modo conservador + falha, 2 pisos de
RIR + falha, `[INV]` via `CatalogPool` com fixture mista. `test_motor_v3.py` (oráculo): 24 testes,
2 falhas (pré-existentes, Fatia 9), 0 erros. `:server:integrationTest`: 24 suítes, 0 falhas, 0
erros, incluindo `MigrationIntegrationTest` (V1..V67 em Postgres limpo via Testcontainers — a
autoconferência da V67 teria derrubado essa classe se a contagem não fosse 8) e
`MotorV3FidelidadeCatalogoRealIntegrationTest` (sem regressão). Confirmado pelos XML dos
relatórios, não só pela ausência de erro no console.

Nenhuma migration apaga ou reescreve dado de usuário — V67 é coluna nova em `exercises`
(catálogo), não em `profiles`.
