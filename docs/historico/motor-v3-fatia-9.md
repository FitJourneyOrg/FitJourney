# Motor v3 — Fatia 9: medição e invariantes (fechada em 2026-10-27)

Nona fatia do motor v3. Critério de pronto registrado na Fatia 6: o pipeline Kotlin inteiro contra
o catálogo real, nos 720 perfis, comparado linha a linha com o oráculo. **Cumprido.**

## O que entrou

- **`server/src/test/oracle/` saiu de `tools/motor_sim`** (git renames). Era o primeiro item da
  abertura: o oráculo é código de teste do módulo que espelha, não bancada de script. Referências
  vivas reescritas (KDoc de `engine/v3`, docs). **V64/V66 mantêm o caminho antigo nos comentários
  de propósito:** migration aplicada é imutável, e `cabecalho_v64.sql` é entrada congelada do gerador.
- **`referencia_720.txt`** (19.540 linhas): a saída de `motor_v3.gerar()` para os 720 perfis,
  GERADA por `gerar_referencia_720.py`. `ReferenciaDos720` (Python) falha se ela não bater com o
  oráculo de hoje, e se não cobrir exatamente a matriz.
- **`MotorV3Varredura720Test`** (`:server:test`, sem Docker): `MotorV3Pipeline.gerar` sobre o
  `catalogo_banco.csv` contra a referência — divisão, capacidade, iterações, cada vaga, cada um dos
  10 músculos (inclusive a auditoria e `pisoDiretoNegado`) e os disparos. Tolerância 1e-5.
  Os "dois testes do revisor" caem aqui (diferencial = 0 por construção quando a comparação passa).
- **Invariantes independentes do oráculo** (mesmo arquivo): `entregue ≤ capacidade`, `ATENDIDO` nunca
  abaixo do alvo, `MANUTENCAO` nunca abaixo do piso, `efetivo ≥ direto`, as duas regras de segurança
  disparam na matriz, `ombro_vira_isolamento` e `ponto_fixo_nao_convergiu` em zero. Existe para o
  caso em que oráculo e Kotlin errem JUNTOS.
- **`MotorV3FidelidadeCatalogoRealIntegrationTest`** reescrito: afirma uma coisa só — as 147 linhas
  da lista principal do Postgres (via `ExerciseRepositoryImpl.findAll`, o `toExercise()` real) são as
  de `catalogo_banco.csv`, campo a campo. Banco = CSV (Docker) e Kotlin = oráculo sobre o CSV (sem
  Docker) dão oráculo = Kotlin sobre o catálogo real por transitividade, e cada um aponta a causa.
- **`ProgramAudit.avisos()`**: o aviso de honestidade. Um `Aviso(musculo, causa, efetivo, minimo)` por
  músculo que merece fala, com causa `NAO_TREINADO` / `ABAIXO_DO_PISO` / `PISO_DIRETO_NEGADO`, do mais
  grave ao menos. O domínio diz a causa; a frase é do i18n (Presentation).
- **`server/build.gradle.kts`**: os dois arquivos do oráculo são `inputs.files` de `test` e
  `integrationTest`, com guarda (`check` de que o `fileTree` achou os 2).

## Medido

| | |
|---|---|
| Perfis conferidos Kotlin × oráculo | **720 / 720**, sem divergência, 0,28 s |
| Banco × `catalogo_banco.csv` | **147 / 147** campo a campo (catálogo não mudou desde 24/10) |
| `:server:test` | **681**, 0 falhas · oráculo Python **38**, 0 falhas |

## Pergunta aberta fechada

`modelo_a_mao` assume frequência 2× e o motor aplica a efetiva; o diferencial vermelho em
`ADVANCED` (`POSTERIOR: -0,8`) parecia vir daí. **Não vinha:** era o coeficiente `glúteo → posterior`
sem fonte, removido na Fatia 8. Nenhum dos dois lados estava errado na frequência; o oráculo ficou
verde sem tocar nela.

## Lições

1. **Referência embutida em teste envelhece em silêncio.** Os 12 perfis do teste de integração
   divergiram todos depois da Fatia 8 e nada acusou, porque o `integrationTest` não rodou. Referência
   agora é arquivo GERADO com teste de frescor — e o teste do lado Kotlin lê o mesmo arquivo.
2. **Teste que lê arquivo fora de `src/test/kotlin` precisa declará-lo ao Gradle**, senão `UP-TO-DATE`
   dá verde sem rodar (mesmo defeito de 2026-09-22). Vale para qualquer fixture externa futura.
3. **Dois lados concordando não é prova.** A varredura compara com o oráculo; os invariantes não
   olham para ele. Um sem o outro deixa passar o erro comum (foi o caso do fator `MAINTAIN`, E10).

## Fora desta fatia

- **Texto e UI do aviso** (a frase, o i18n, onde aparece): Presentation, ver `fontes/debitos.md`.
- **Troca de produção do `StructureEngine`:** continua depois da Fatia 9 e com a condição de 60+
  (modo conservador permanente até a Fatia 10 — mesociclo) em aberto; é decisão explícita, não passa
  em silêncio. Registro em `trabalho/motor-v3-desenho-e-fatias.md`.
