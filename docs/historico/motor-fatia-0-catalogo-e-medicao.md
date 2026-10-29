# Motor v3 — fatia 0: catálogo e medição confiável

> **Histórico.** Fatia aberta em 2026-10-13, fechada em 2026-10-21. Estado atual em
> `docs/ESTADO.md`; regra de comportamento em `docs/regras/`; débitos em `docs/fontes/debitos.md`;
> migrations em `docs/migrations/INDICE.md`. Plano original (mais detalhe das cinco rodadas de
> revisão técnica e das decisões intermediárias) em `trabalho/motor-fatia-0-catalogo-e-medicao.md`,
> antes de ser apagado.

## O que a fatia resolveu

Pré-requisito do motor v3: enquanto o casamento músculo↔exercício fosse ambíguo e o simulador não
reproduzisse números confiáveis, nenhuma medição contra o catálogo valia.

- **Curadoria completa do catálogo**: 855 exercícios revisados nas 17 planilhas + 66 de
  referência. 29 saem (duplicados e um aparelho passivo), **893 ficam**. Confiança final:
  543 ALTA / 284 MÉDIA / 0 BAIXA, nenhum atributo PENDENTE.
- **Lista principal 79 → 147**, revisada grupo a grupo contra os buracos que a varredura de
  perfis mostrou. Nenhum exercício novo — todos já estavam curados no catálogo.
- **V64 regerada** (nunca tinha sido aplicada) com os 147 nomes; **V65** escrita: remove os 29 com
  guarda contra uso em `workout_exercises`, cria as colunas de seleção (`prioridade`,
  `relevancia`, `nivel_maximo`, `ambiente`) e de execução (`familia` + seis `requires_*`), aplica
  taxonomia em 840 exercícios, realinha `is_base` com `relevancia` e fecha num `CHECK`.
- **V66**: `profiles.accessories` — o outro lado do par dos seis `requires_*`. Continuado na
  fatia seguinte, `acessorios-no-onboarding.md` (fechada em 2026-10-22).
- **Contraindicação saiu do motor** (decisão do Rafael, 21/10): `ExercisePreFilter` perdeu o
  parâmetro `limitations` e o filtro duro — ele esvaziava grupo inteiro (dor no ombro deixava 1
  exercício de peito, dor no joelho deixava 0 de quadríceps). Virou aviso na tela, não exclusão.
- **Simulador (`tools/motor_sim/`) passou a escolher pela curadoria**: ambiente e acessório no
  pré-filtro, relevância 1 abrindo o músculo, família impedindo dois exercícios do mesmo vetor na
  sessão, prioridade desempatando.
- **Suíte verde ao fechar**: `:server:test` 575, `:server:integrationTest` 194.

## O defeito do Hand Grip

O **Hand Grip (Flexor de Mão)** estava na lista principal com alvo FOREARMS e **não era
alcançável por perfil nenhum**: curado como `ambiente = CASA`, mas a academia o recusava pelo
ambiente (correto) e a casa pelo equipamento — `HAND_GRIP` não estava listado em
`EquipmentEnvironmentMap.HOME_EQUIPMENT`. Resultado: um exercício "válido" no catálogo que
nenhum perfil de usuário jamais receberia.

Corrigido no servidor (`EquipmentEnvironmentMap`) e no `EQUIPAMENTO_CASA` do simulador, com um
teste que afirma a classe inteira, não só o caso — **todo exercício da lista principal precisa
ser alcançável por algum perfil** — para que o próximo "Hand Grip" (um exercício curado para um
ambiente mas sem equipamento correspondente mapeado) quebre o build em vez de ficar invisível de
novo.

> **Duas fontes de verdade sobre "onde isso roda" — a coluna `ambiente`, curada, e o mapa de
> equipamento, em código — só convivem enquanto um teste prova que não se contradizem.**

## O que ficou de fora, por decisão

- **Motor Kotlin em si** — não escrito nesta fatia; é `trabalho/motor-v3-desenho-e-fatias.md`,
  fatias 1-12.
- **"Referência do avançado" e "ombro_vira_isolamento"** — continuam abertos, 3 testes vermelhos
  do simulador (de 25, 22 verdes), não bloqueiam o fechamento. Não resolvidos nesta promoção;
  seguem como débito até alguém priorizar.
- **Tabela canônica do catálogo e o critério de casamento músculo↔exercício** (`[REGRA]` e ADR
  propostos no §7 do plano original) **não foram promovidos nesta passada** — o fechamento desta
  sessão tratou só da narrativa e das migrations (os dois itens que o `ESTADO.md` cobrava
  explicitamente). Se esse conteúdo ainda for relevante para o motor v3 em Kotlin, revisitar o
  plano original antes de apagá-lo de vez, ou perdê-lo.
