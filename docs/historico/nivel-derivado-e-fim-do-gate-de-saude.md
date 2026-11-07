# Nível derivado e fim do gate de saúde — 2026-11-07

## O problema

Primeiro recorte da "Fatia 12 — questionário e triagem" (`trabalho/motor-v3-desenho-e-fatias.md`),
dividida porque a fatia inteira (4 perguntas novas + remapear `BodyLimitation` + minutos→teto +
modo leve + cliente) não cabia numa sessão. Este recorte isola as duas mudanças que afetam a
geração **antiga, em produção, hoje** — as outras alimentam o motor v3, ainda sem fiação nenhuma.

Lendo `contract/profile/` e o server antes de codar, dois mecanismos já em produção contradiziam
a especificação (`propostas/motor-v3-ESPECIFICACAO.md`, 3ª rodada de revisão):

1. `Level` é autodeclarado (tela de escolha livre no quiz) — a spec manda derivar de duas
   perguntas novas, entrada máxima intermediário.
2. `HealthScreening` armazena e BLOQUEIA `/programs/generate` (`ProgramRoutes.kt:60`) — a spec
   já tinha decidido abandonar isso: autoavaliação sem armazenar, nunca bloqueia.

## Medição antes de decidir

Banco real, 2026-11-07: `environment` tem 16 `ACADEMIA` / 1 `CASA` (confirma: "casa em breve" na
tela não pode desligar quem já usa). `limitations` tem **0** linhas em qualquer das 5 colunas de
`BodyLimitation` — remapear as regiões (fora desta fatia) não tem risco de migration destrutiva.

## Decisões (Rafael, via 4 perguntas apresentadas)

- **Remove o gate de saúde** — segue a spec. Extraído para `ProgramGerarGate.avaliar()` (pura,
  mesmo padrão de `ProgramLimits`), só resta o gate de idade (#24).
- **Nível vira derivado no server, com fallback aditivo** — quando as duas perguntas novas
  (`trainingConsistency`/`timeOffTraining`) vêm preenchidas, `nivelDerivado()` manda; se vierem
  `null` (cliente de produção hoje, que ainda não pergunta as duas), o `level` que o cliente
  mandou continua valendo. **Sem isso o onboarding em produção quebraria** no dia em que o
  backend subisse sem o cliente novo simultâneo.
- **"Não tenho este equipamento" (academia)**: decisão de contrato adiada pra quando a fatia de
  cliente chegar, mas já resolvida — `accessories != null` basta como gatilho, sem campo novo.
- **BodyLimitation/minutos/modo leve/cliente**: próximas fatias (débito registrado, não perdido).

## Desenho

`TrainingConsistency`/`TimeOffTraining` (enums, shared-contract) + `nivelDerivado()` (pura,
shared-contract — cliente pode antecipar na UI futuramente, servidor é quem decide de verdade).
`ProfileDto`/`Profile`/`ProfilesTable`/`ProfileRepositoryImpl` ganham os dois campos, nullable,
aditivos. `ProfileService.saveProfile` deriva `level` só quando os dois vêm preenchidos.
`ProgramGerarGate` substitui o bloco inline de `ProgramRoutes.kt` que checava `health`.

`ProfileDto.health` e a coluna `profiles.health_screening` **não foram apagados** — ficam mortos
(sem leitura), débito próprio pra quando o cliente também parar de mandar o campo.

V78, aditiva — 2 colunas nullable em `profiles`.

## Dois achados de compilação, ambos durante o desenvolvimento

- **Smart-cast cross-módulo**: `if (dto.trainingConsistency != null && dto.timeOffTraining !=
  null) { nivelDerivado(dto.trainingConsistency, dto.timeOffTraining) }` não compila — Kotlin
  não faz smart-cast de propriedade pública declarada em OUTRO módulo (`shared-contract` é
  módulo separado de `server`), mesmo com o null-check explícito na mesma expressão. Corrigido
  bindando em `val` local antes do `if`.
- **Fixtures de teste que constroem `Profile(...)` direto** (`AcessoriosPersistenciaIntegrationTest`,
  `ProgressaoAplicadaServiceTest`, `PromotionServiceTest`, `SugestaoDeProgressaoServiceTest`)
  quebrariam se os dois campos novos fossem obrigatórios — todas usam argumento nomeado, então
  dar `= null` de default aos dois em `Profile` (não só em `ProfileDto`) evitou tocar nos 4
  arquivos.

## Testes

- **Tier 1**: `NivelDerivadoTest` (5 casos, incluindo o teto intermediário pra "mais de 2 anos").
  `ProgramGerarGateTest` — idade bloqueia, **saúde com risco declarado e não reconhecido NÃO
  bloqueia mais** (guarda de regressão do que esta fatia muda).
- **Tier 2**: `ProfileServiceTest` — com as duas perguntas novas, deriva e ignora o `level`
  enviado; sem as duas (cliente antigo), `level` enviado continua valendo (fallback). Precisou
  de um stub de `UserRepository` funcional nesses dois testes (os existentes nunca tocam o
  repositório, então o stub que sempre `error()`a não servia aqui).
- **Tier 3** (`NivelDerivadoPersistenciaIntegrationTest`, Postgres real): round-trip dos dois
  campos; perfil legado grava/lê `null` sem quebrar; JSON sem as duas chaves novas desserializa
  (cliente antigo de verdade, que nem manda a chave).

`:konsist:test`, `:server:test` e `:server:integrationTest` verdes (confirmado pelo Rafael).

## Fora de escopo

Todo o resto da Fatia 12 — ver `fontes/debitos.md`, seção "Motor v3 — fatia 12".
