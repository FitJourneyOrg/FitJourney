# Âncoras do avançado bodyweight — 2026-11-06

## O problema

4 de 26 âncoras do avançado (Barra Fixa, Flexão, Mergulho e afins — compostos `BODYWEIGHT` sem
`incremento`, `relevancia = 1`) caíam **permanentemente** em DADO INSUFICIENTE no
`diagnosticoAvancadoPorAncora`, mesmo para quem treina em academia. Achado em 2026-11-05, medido,
registrado como P0 em `fontes/debitos.md`.

Causa raiz: `PromotionService.diagnosticoAvancadoPorAncora` compara `umRmInicio`×`umRmFim` do
mesociclo (`PromocaoDeNivel.diagnosticoAvancado`). `estado.umRm` só é escrito por
`Progressao.decidir()`, que exige `estado.carga` não-nulo (`estimarUmRm(carga, ...)`, fórmula de
Epley). Essas 4 âncoras passam pelo eixo de REPETIÇÃO (`ProgressaoPorRepeticao`, fatia
"progressao-por-repeticao", fechada no dia anterior) — nunca pelo de carga — então `estado.umRm`
nunca existia pra elas. Não era bug introduzido por aquela fatia: era pré-requisito satisfeito
(o eixo de reps existir) sem a peça que faltava (um sinal de força equivalente ao 1RM).

## Decisão (Rafael, via 3 opções apresentadas)

Sinal análogo ao 1RM via reps+RIR, sem precisar de carga — em vez de (a) duplicar
`diagnosticoAvancado` inteiro no eixo de reps, ou (b) modelar carga externa opcional pra
bodyweight (maior escopo, resolve só parcialmente quem nunca adiciona peso).

## Desenho

`ProgressaoPorRepeticao.estimarSinalDeForca(series, papel)` — mesma restrição do `estimarUmRm`
(só COMPOSTO/ACESSORIO, só séries com RIR), mas o valor é `reps + RIR` da melhor série, não Epley
(sem carga pra multiplicar). `ProgressaoPorRepeticao.Estado` ganha `sinalForca: Double?`, carregado
entre sessões com o mesmo fallback do eixo de carga (sem RIR na sessão → mantém o valor anterior).

**Decisão de implementação que vale registrar**: o sinal é gravado na MESMA coluna `um_rm_kg` de
`exercise_progression`/`progression_event` que o eixo de carga usa pro 1RM real — não uma coluna
nova. `ProgressaoRepositoryImpl.buscar()`/`primeiroUmRmNoMesociclo()` já liam essa coluna de
QUALQUER linha, independente do eixo, e `PromotionService` já consumia `.umRm` sem saber que
existem dois motores — então popular essa coluna fechou o circuito inteiro com **zero mudança**
em `PromotionService`, `PromocaoDeNivel`, contrato ou cliente. O preço: o comentário da V76
("um conjunto de colunas OU outro, nunca os dois ao mesmo tempo") deixa de valer pra `um_rm_kg`
especificamente — documentado como exceção em `regras/01-motor-de-geracao.md`, não escondido.

Zero migration: as duas colunas (`exercise_progression.um_rm_kg`, `progression_event.um_rm_kg`)
já existiam, nullable, desde V69/V72.

## Testes

- **Tier 1** (`ProgressaoPorRepeticaoTest`): `estimarSinalDeForca` com RIR/composto, null pra
  isolamento, null sem RIR nenhum; `decidir()` propaga `sinalForca` nos 3 ramos (SOBE/MANTEM/DESCE)
  e cai no fallback quando a sessão não tem RIR.
- **Tier 2** (`ProgressaoAplicadaServiceTest`): exercício BODYWEIGHT+COMPOSTO com RIR grava
  `sinalForca` via o fake de repositório.
- **Tier 3** (`ProgressaoIntegrationTest`, Postgres real): `registrarPorRepeticao` com
  `sinalForca` → `buscar()` devolve `Progressao.Estado.umRm` não-nulo (o MESMO caminho que
  `PromotionService` usa) → `primeiroUmRmNoMesociclo` acha o valor do 1º evento do mesociclo;
  caminho de falha: sem RIR nenhuma série, fica `null`, não finge dado que não existe.

`:server:test` e `:server:integrationTest` verdes (confirmado pelo Rafael).

## Fora de escopo

- BAND/HAND_GRIP continuam sem progressão — débito separado, precisa modelar o kit do usuário.
- Não recalibra `limitePoucasReducoes` nem o limiar de maioria de `diagnosticoAvancadoDoPerfil` —
  o sinal novo entra na MESMA fórmula já calibrável, sem tocar em número nenhum.
