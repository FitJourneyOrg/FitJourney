# Progressão por repetição

> Movido de `docs/trabalho/progressao-por-repeticao-plano.md` ao fechar o ciclo (2026-11-06).
> Rascunho não é fonte da verdade — isto é narrativa, a verdade está no código e em `regras/01`.

## O problema

42/147 (28,6%) do pool do motor (`BODYWEIGHT` 26, `BAND` 15, `HAND_GRIP` 1) tinha `incremento`
NULL, e `decidir()`/`calibrar()` exigem `Double` não-nulo — pra esses exercícios o motor
simplesmente não era chamado. Achado e medido na fatia "catálogo: incremento e semântica de
carga" (2026-11-05), virou P0 em `debitos.md`: quem treina em casa, sem carga variável, não tinha
progressão automática nenhuma. Também é a causa raiz de 4/26 âncoras do avançado estruturalmente
mortas (`estimarUmRm()` exige `carga: Double`) — essa fatia é pré-requisito, não resolve aquela.

## Decisões, nesta ordem

1. **Escopo: só BODYWEIGHT (26) agora.** BAND não tem "incremento" físico claro (resistência por
   nível de elástico, e o perfil não registra o kit do usuário) e HAND_GRIP é 1 exercício só.
   Ficam de fora, débito separado — resolver exige modelar o kit do usuário primeiro, não é só
   trocar o eixo de progressão.
2. **Mecânica: reps puro, objeto paralelo.** `ProgressaoPorRepeticao`, não uma emenda a
   `Progressao.decidir()` — colar as duas semânticas (carga vs. reps) na mesma assinatura
   repetiria o erro que o ADR da semântica de carga já apontou pro passo proporcional.
3. **Teto: 20 repetições, absoluto.** Isolamento (12–16) e composto/acessório (6–9/8–12) cabem
   embaixo sem tabela nova. Ao bater: `MANTEM`, sem alarme — variação de exercício é débito.
4. **Sem calibração.** A faixa original já é estado válido da 1ª exposição — diferente de carga,
   que precisa descobrir um peso que não existe.

## O achado no meio da implementação

Ao ligar o fio no `ProgressaoAplicadaService`, a leitura de `AncoraDoIniciante.semSubirNoFim` e
`PromocaoDeNivel.diagnosticoIniciante` mostrou que os dois só reconheciam `Evento.Carga` pro
streak de SOBE que decide DELOAD (estagnação) e promoção de nível. Registrar um evento separado
sem ajustar isso faria quem treina bodyweight cair em DELOAD **falso** a cada 3 sessões, mesmo
progredindo normalmente — a estagnação nunca seria detectada como "subiu".

Perguntado, o Rafael decidiu **unificar** (Opção A): os dois eixos contam igual pro contador,
porque "estagnação é ausência de progressão de estímulo, independente da variável que gerou esse
estímulo". `AncoraDoIniciante.Evento` ganhou `Repeticao(decisao)`, e a propriedade `Evento.subiu`
(true pra SOBE de qualquer um dos dois enums, que são tipos diferentes apesar dos nomes iguais)
substituiu as duas checagens manuais de `it is Evento.Carga && it.decisao == Decisao.SOBE`.

## O segundo achado: Contrato não é paridade

Ao planejar a etapa de Contrato ("expor a faixa de reps pro cliente"), a checagem de como o eixo
de CARGA expõe isso hoje mostrou que **não expõe nada**: não há rota nem campo no
`WorkoutExerciseDto`/`SetLogDto` com carga ou faixa sugerida — o motor roda só como registro
invisível (alimenta deload/promoção), e quem treina com carga também não vê alvo na tela. Fazer
isso só pra bodyweight seria inconsistente e maior que esta fatia. Virou débito próprio ("mostrar
sugestão de progressão"), que afeta os dois eixos igual — a fatia fechou no servidor, em paridade
real com o que já existe.

## O que entregou

- `ProgressaoPorRepeticao` (Tier 1 puro, `engine/v3/`).
- `ExercicioParaProgressao.equipment` (campo novo, decide o branch).
- `ProgressaoAplicadaService.aplicarExercicioPorRepeticao()`.
- `AncoraDoIniciante.Evento.Repeticao` + `.subiu`, usado em `AncoraDoIniciante` e
  `PromocaoDeNivel`.
- `ProgressaoRepository.buscarPorRepeticao`/`registrarPorRepeticao`, mesma linha/PK de
  `exercise_progression`.
- `V76__progressao_por_repeticao.sql` — aditiva, 2 colunas nullable + CHECK; recria o CHECK de
  `progression_event.tipo` com `SOBE_REP`/`MANTEM_REP`/`DESCE_REP`.
- Testes: Tier 1 (`ProgressaoPorRepeticaoTest`), Tier 2 (`ProgressaoAplicadaServiceTest` — BODYWEIGHT
  sem calibração, BAND continua pulando) e Tier 3 (`ProgressaoIntegrationTest` — D-1, D-2,
  histórico misto, os dois CHECKs novos) — todos verdes.

## Fora de escopo (virou débito)

- Progressão pra BAND/HAND_GRIP — precisa modelar o kit do usuário primeiro.
- Sugerir variação mais difícil do exercício ao bater o teto de 20 reps.
- Mostrar a sugestão de progressão (carga OU reps) na tela de execução — feature inteira ausente
  pros dois eixos, não nasceu aqui.
- `ProgressaoRepository.todos()` devolve carga `null` indistinguível de "calibração pendente"
  pra exercícios de repetição — sem chamador em produção hoje, não corrigido por falta de uso real.
