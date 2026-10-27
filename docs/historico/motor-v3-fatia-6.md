# Motor v3 — Fatia 6: preenchimento pelo catálogo + ponto fixo do indireto

_Fechada em 2026-10-23._

## O que foi feito

Quatro peças novas em `engine.v3`:

- **`CatalogPool.pool()`** — pré-filtro próprio do motor v3 (não é o `ExercisePreFilter` de
  produção, por decisão do Rafael). Puro, em memória: `volumeTarget != null` → ambiente sem
  misturar casa/academia → nível cumulativo → `nivelMaximo` com válvula de escape por músculo →
  equipamento de casa → acessórios.
- **`CatalogFiller.preencher()`** — escolha do exercício real, espelhando `preencher()` do
  oráculo: papel certo + fallback de inversão (decisão do Rafael, duas condições extras:
  respeita família, vaga vazia só sem candidato nenhum) + não repetir no dia + família (por dia,
  não por músculo) + relevância 1 na primeira vaga do músculo + desempate usados-na-
  semana→prioridade→**nome** (terceiro critério só do Kotlin, testado com catálogo embaralhado)
  + ordem final por tipo entregue (composto antes de isolado).
- **`PontoFixoIndireto`** — mecânica genérica do laço (converge/oscila/esgota 30 iterações),
  separada do pipeline concreto. É essa separação que torna o caminho "esgota as 30 iterações"
  testável — nenhum dos 720 perfis reais chega lá (máximo observado: 5).
- **`MotorV3Pipeline.gerar()`** — orquestra Fatias 1–6. Não liga em produção (decisão de depois
  da Fatia 9).

Testes: `CatalogPoolTest` (5), `CatalogFillerTest` (6, incluindo os dois sentidos do
`papel_trocado` e o desempate embaralhado), `PontoFixoIndiretoTest` (5, os 3 caminhos mecânicos),
`MotorV3PipelineTest` (1, fim a fim contra o oráculo com catálogo pequeno) — 17 testes, todos
verdes.

## Decisão do fallback (Rafael, antes de abrir esta fatia)

A `[PROPOSTA]` da Fatia 5 virou `[REGRA]`: inverte o tipo nos dois sentidos, medido sobre o
entregue. Condições: respeita família; vaga vazia só sem candidato nenhum depois da inversão;
disparo `papel_trocado`; ordem final por tipo entregue.

## Decisões desta abertura

- Pré-filtro próprio (`CatalogPool`), não altera `ExercisePreFilter`.
- `volumeTarget != null` filtra os candidatos — confirmado: `familia` está nos 893 exercícios,
  não só nos 147 da lista principal (correção ao registro anterior).
- Desempate por nome como terceiro critério explícito — mais forte que o oráculo garante hoje.
- "Seed determinístico" e "âncoras fixas dentro do mesociclo": confirmado que o oráculo não tem
  sorteio nem rastreia mesociclo. Registrado para a Fatia 10: dentro do programa, a saída do
  `preencher()` é fixa em todas as semanas; a troca entre programas é da Fatia 10.

## Fora de escopo
- Wiring em produção (`SlotFiller`/`StructureEngine`) — depois da Fatia 9.
- Mesociclo/continuidade entre programas — Fatia 10.

## Testes

17 testes (`CatalogPoolTest` 5, `CatalogFillerTest` 6, `PontoFixoIndiretoTest` 5,
`MotorV3PipelineTest` 1), 0 falhas (`:server:test`, 2026-10-23).

## Fidelidade contra o catálogo real (adendo ao fechamento, 2026-10-23)

Pedido do Rafael antes de apagar o plano: os 17 testes acima rodam contra catálogos sintéticos
(fixtures à mão ou um catálogo de 22 exercícios). Faltava confirmar que `CatalogFiller.preencher()`
bate com o oráculo usando o dado de PRODUÇÃO.

A auditoria completa dos 720 perfis com catálogo real fica para a Fatia 9 (registrado em
`desenho-e-fatias.md` como critério de pronto dela — precisa de parser de perfis em massa e de
onde guardar a referência dos 720 sem acoplar os dois testes). Substituto pequeno, decidido pelo
Rafael: `MotorV3FidelidadeCatalogoRealIntegrationTest` (`server/src/integrationTest`), 12 perfis
(4 cenários de ambiente × 3 níveis -- academia, casa sem acessório, casa com banco, casa com
tudo -- × BEGINNER/INTERMEDIATE/ADVANCED), mesmos parâmetros do `MotorV3PipelineTest`
(GAIN_MUSCLE, 4 dias, 60 min, 30 anos). Carrega o catálogo completo via
`ExerciseRepositoryImpl.findAll()` (dado real, pós V1..V66) e roda `MotorV3Pipeline.gerar()`
sobre ele, comparando vaga a vaga (dia, alvo, papel, séries, nome do exercício) contra a
referência gerada por `tools/motor_sim/motor_v3.py::gerar()` sobre o mesmo `catalogo_banco.csv`
(embutida no teste, 245 linhas).

Escrito e rodado pelo Rafael: **1ª rodada falhou** -- 10/12 perfis divergiram do oráculo em
*ordem*, não em conteúdo (confirmado por conjunto: mesmo exercício, papel e séries em todas as
vagas de todos os 12 perfis). Causa: `CatalogFiller.preencher()` (Kotlin) termina cada dia com
`sortedByDescending { isCompound }` (regra do fallback, condição 4 -- "composto antes de
isolado"), mas `motor_v3.py::preencher()` nunca tinha esse sort -- a regra entrou só no Kotlin.

**Correção no oráculo:** adicionado `vagas.sort(key=lambda v: not (v.exercicio.composto if
v.exercicio else False))` ao final do laço de cada dia em `preencher()`, sort estável (preserva
ordem de preenchimento dentro do mesmo grupo), espelhando o Kotlin. Referência dos 12 perfis
regenerada com o oráculo corrigido.

**Confirmado que o patch não muda nenhuma métrica** -- medido com e sem o sort, nos mesmos 720
perfis da matriz (`itertools.product` de objetivo/nível/dias/minutos/idade/ambiente):
acima da capacidade por `alocado`: **154/720**, igual nos dois; `papel_trocado` por ambiente:
**ACADEMIA 0, CASA 717**, igual nos dois; excesso sobre `entregue` (duas definições testadas --
qualquer sessão acima do teto: 565/720; soma semanal acima da capacidade: 268/720) também
idêntico nos dois casos. **Nota:** não consegui reproduzir aqui o "297/720 (41,2%)" registrado
em `desenho-e-fatias.md`/`historico/motor-v3-fatia-5.md` com nenhuma das duas definições que
tentei -- a fórmula exata usada naquela medição não está documentada o bastante para eu
reconstruir sem ambiguidade. O que importa para esta fatia, e que CONFIRMEI, é que o sort não
move nenhum perfil de um lado pro outro de nenhuma dessas métricas (mesmos números antes/depois
do patch); se o "297/720" precisar ser reconferido com exatidão, é tarefa separada.

**Resolvido na Fatia 7 (2026-10-23):** o "297/720" era `soma(entregue) > capacidade` -- a mesma
fórmula testada acima como dando 268/720 -- mas medida depois de corrigir uma divergência de
convenção de mesociclo entre o oráculo e o Kotlin (ver `historico/motor-v3-fatia-4.md` e
`motor-v3-fatia-5.md`, seção "Recalculado..."). Com a correção, essa fórmula dá exatamente
**297/720**, confirmando a fórmula E o número ao mesmo tempo. Os outros números desta nota
("154/720", "papel_trocado CASA 717") também mudaram com a correção -- 176/720 e CASA 706 -- mas
a conclusão central permanece: nem a correção do mesociclo nem o sort de ordem mudam um em
relação ao outro de forma independente; cada correção afeta a métrica por razões próprias e
documentadas em seus respectivos lugares.

**Rodando `test_motor_v3.py` inteiro, achei 3 falhas -- confirmei as 3 como PRÉ-EXISTENTES**
(reproduzidas também com o sort temporariamente desativado, idênticas): o disparo
`ombro_vira_isolamento` não dispara em nenhum dos 720 perfis (regra morta, já presente antes
desta fatia) e os 2 vermelhos de `test_diferencial_contra_o_modelo_a_mao` em `ADVANCED`
(diferença de -0,8 no POSTERIOR -- a pergunta aberta registrada ao abrir a Fatia 3 sobre
frequência efetiva vs. fixa do `modelo_a_mao.py`). Nenhuma das 3 foi causada por este patch.

**2ª rodada, com o oráculo corrigido: verde** -- 1 teste, 0 falhas, 0 erros (`:server:integrationTest`, 2026-10-24, XML confirmado).
