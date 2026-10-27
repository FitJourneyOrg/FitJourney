# Motor v3 — Fatia 5: composição da sessão

_Fechada em 2026-10-23._

## O que foi feito

`engine.v3.SessionComposition` — etapa [5] do motor v3: quantos exercícios por músculo numa
sessão, com que papel (âncora composto / acessório / isolamento) e quantas séries em cada.

- `comporSessao(series, disparos)` espelha `compor_sessao()` termo a termo; `compor()` trata a
  regra do ombro (desenvolvimento cede espaço ao composto de peito do dia); `papeisPara()`,
  `distribuir()`, `isolamentos()` completam o espelho.
- Validado rodando `compor_sessao()` do Python com as 3 saídas reais de `distribuirPelosDias()`
  da Fatia 4 (sem escassez, capacidade 40, capacidade 65,33) — saída exata, byte a byte.
  `SessionCompositionTest.kt`, 8 testes, todos verdes — incluindo os dois disparos da regra do
  ombro (`ombro_teto_do_desenvolvimento`, presente na fixture; `ombro_vira_isolamento`, caso
  dedicado CHEST=11/SHOULDERS=8).

## Correção de escopo na abertura

O desenho atribuía à Fatia 5 também o ciclo do ponto fixo do indireto. Medindo antes de codar:
`gerar()` do oráculo não dá ponto fixo em [3]–[6], como o texto dizia — dá a volta completa,
chamando `preencher()` (catálogo, Fatia 6) a cada iteração, porque `compostos_semana` é medido do
exercício REAL escolhido, não do papel assinalado em `compor()`.

- **293/720 perfis (40,7%) divergem** entre papel e composto real — soma de 1813 séries.
- Por ambiente (360 perfis por cenário): ACADEMIA nunca diverge (0/360); CASA sem acessório
  diverge em 293/360 (134 com vaga vazia); CASA com banco diverge em 299/360 com **zero** vagas
  vazias — checado por músculo: BACK 272/360, POSTERIOR 56/360, CHEST 27/360, sempre papel
  pedindo ISOLAMENTO e saindo COMPOSTO (falta máquina de isolamento em casa, banco não resolve);
  CASA com todos os acessórios ainda diverge 27/360, pelo equipamento de carga que falta em
  casa, não só acessório. **Corrigido (2026-10-23): a causa não é o fallback de vaga vazia — é
  troca de papel com vaga preenchida.** A pergunta da `[PROPOSTA]` continua válida para os casos
  de vaga vazia de verdade, mas não explica os ~40% medidos.
- `ponto_fixo_oscilou`: 24/720. `ponto_fixo_nao_convergiu` (30 iterações): zero — máximo
  observado, 5 iterações.

**Decisão do Rafael:** o ponto fixo sai da Fatia 5 e vira abertura da Fatia 6 — é lá que
"composto medido sobre o entregue" (Decisão B) passa a existir de verdade. Registrado em
`trabalho/motor-v3-desenho-e-fatias.md` (Fatia 5 e 6).

## Pergunta de especificação aberta (não decidir nesta fatia)

Quando falta candidato do papel certo, `preencher()` hoje inverte o tipo (fallback). A
alternativa é deixar a vaga vazia e contar como não entregue. Decide o resultado do ponto fixo —
fica registrada como `[PROPOSTA]` em `regras/01-motor-de-geracao.md`, a decidir antes da Fatia 6
implementar o laço.

## Reauditoria dos 720 perfis, pedida ao fechar

**A composição não muda o número: continua 154/720 (21,4%).** `distribuir()` sempre reconstrói o
total exato (`resto == 0`), então composição é soma-preservante por construção — medido
isolando Fatia 4 (`montar_semana`) de Fatia 5 (`compor_sessao`): a soma pós-composição é
idêntica à soma pré-composição em **720/720 perfis, sem exceção**. A variação que eu vi no
caminho (148→154 numa medição com indireto zerado) veio do piso de isolamento da **Fatia 4**
(`sobe_ao_piso_do_isolamento`), já registrado lá — não é efeito do piso de papel da Fatia 5.

**Causa estrutural confirmada:** a soma do piso de TODOS os músculos treináveis (coluna
iniciante da tabela de volume, 61 séries/semana) excede a capacidade em **100% dos perfis de 2
dias (144/144) e de 3 dias (144/144)** — 61 séries nunca cabe no teto físico de 18
séries/sessão × 2 ou 3 dias. É a causa que o Rafael já suspeitava; agora medida.

**Achado adicional, fora do escopo desta fatia mas registrado para a Fatia 6/8:** medindo sobre
as sessões REAIS do pipeline completo (`gerar()`, com catálogo e ponto fixo já ativos no
oráculo), o excesso sobe para **297/720 (41,2%)** — quase o dobro do 154/21,4% hoje registrado.
A diferença vem do catálogo (vagas vazias, fallback do `preencher()`), que esta fatia não tem —
não é uma correção ao 154/720 da Fatia 4 (que mede só alocação+estrutura), é um número a mais,
do pipeline completo, que a Fatia 6 precisa levar em conta.

## Recalculado após alinhar convenção do mesociclo (Fatia 7, 2026-10-23)

O oráculo tratava `primeiro_mesociclo` como `bool = False` por padrão (nenhum perfil da matriz
setava); o Kotlin trata `mesocicloAtual == null` como 1º mesociclo (`[REGRA]` da Fatia 2).
Corrigido para alinhar (`mesociclo_atual: Optional[int] = None`, nulo = 1º). Todos os números
abaixo, medidos de novo com a correção:

- **154/720 → 176/720 (24,4%)** acima da capacidade por `alocado` — ver `historico/motor-v3-
  fatia-4.md` para o detalhamento (por dias, por nível, excesso máximo).
- **293/720 → 289/720 (40,1%)** divergem entre papel e composto real, soma de **1767 séries**
  (era 1813).
- Por ambiente: ACADEMIA 0/360 (sem mudança); CASA sem acessório 289/360 (era 293), 131 com vaga
  vazia (era 134); CASA com banco 295/360 (era 299), zero vagas vazias, por músculo BACK 268/360
  (era 272), POSTERIOR 48/360 (era 56), CHEST 27/360 (sem mudança); CASA com tudo 27/360 (sem
  mudança). 0 (ACADEMIA) + 289 (CASA sem acessório) = 289/720, reconciliado.
- `ponto_fixo_oscilou`: **25/720** (era 24). `ponto_fixo_nao_convergiu`: continua zero — máximo
  observado agora **6 iterações** (era 5).
- **297/720 (41,2%)** acima da capacidade por `entregue` real (pipeline completo) -- **este
  número NÃO mudou** (41,25% arredondado é o mesmo 41,2% já registrado); a correção do
  mesociclo o confirma, não o altera.
- A causa estrutural (piso de todos os músculos, 61 séries/semana, excede capacidade em 100% dos
  perfis de 2–3 dias, 288/288) **não muda** -- verificado de novo, continua 288/288.

## Fora de escopo
- Ponto fixo do indireto → Fatia 6 (abertura).
- Fallback do `preencher()` → pergunta de especificação, Fatia 6.
- Calendário (dia da semana) → já fora de escopo desde a Fatia 4.

## Testes

`SessionCompositionTest.kt`, 8 testes, 0 falhas (`:server:test`, 2026-10-23).
