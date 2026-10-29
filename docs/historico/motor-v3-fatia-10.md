# Motor v3 — Fatia 10: mesociclo, progressão e foco (fechada em 2026-10-28)

Décima fatia do motor v3. Núcleo puro de progressão com **oráculo Python primeiro e Kotlin depois**,
foco como bônus de alvo, rastreio de mesociclo (fecha o débito do 60+) e persistência por exercício
(**só interface + fakes**; schema aprovado, migration na fatia seguinte). Regras em
`regras/01-motor-de-geracao.md`, "Motor v3 — mesociclo, progressão e foco"; desenho do schema em
`trabalho/motor-v3-fatia-10-schema.md`.

## O que entrou

- **`progressao_v3.py` / `engine.v3.Progressao`, `Mesociclo`, `ProgressaoRepository`**: progressão dupla
  (4 regras, −4% fixo), calibração por faixas, 1RM (Epley), ciclo de 4 semanas com deload, âncora do
  iniciante (máquina de estados), retorno após pausa em três ramos, continuidade do programa.
- **Três referências geradas** em `server/src/test/oracle/`, todas com teste de frescor no Python e
  `inputs.files` no Gradle (guarda `== 4`): `referencia_720.txt` (INALTERADA, diff vazio),
  `referencia_foco.txt` (960 perfis, arquivo à parte em vez de inflar a dos 720) e
  `referencia_progressao.txt` (12.730 casos das funções puras).
- **`ProgressaoContraOOraculoTest`** reproduz cada linha da grade no Kotlin; **`ProgressaoTest`**,
  **`MesocicloTest`**, **`AncoraEPausaTest`**, **`FocoDoQuizTest`**, **`ProgressaoFluxoTest`** são os
  casos escritos à mão, todos com caminho de falha. Fakes à mão (`FakeProgressaoRepository`,
  `FakeMesocicloRepository`) com a chave única de verdade.
- **`MotorV3Varredura720Test`** estendido para as duas matrizes; `ombro_vira_isolamento` virou
  regra de segurança medida na união (0 nos 720, 16 em 8 perfis com foco em peito).

## Medido

| | |
|---|---|
| `:server:test` | **759**, 0 falhas, 0 erros (era 681 na Fatia 9) |
| Oráculo Python | **129**, 0 falhas (a Fatia 9 registrou 38; o número do pedido estava desatualizado) |
| Foco em produção | só **21 de 480** programas mudam por foco; `ATENDIDO` cai |
| `ombro_vira_isolamento` | 0 / 720; **16 disparos em 8 perfis** com foco em peito |
| 20 kg → 60 kg | 3 séries de calibração → 55 kg; 2ª exposição → 60 kg (e não ~16 sessões) |

## Achados e lições

1. **O foco, sozinho, é quase inerte.** O bônus sobe o alvo, não o que cabe: a capacidade da sessão
   é o limite. A peça que faz o foco funcionar é a proteção contra sacrifício (Fatia 11). Virou
   condição endurecida da troca de produção.
2. **`decidir()` não é idempotente** — o repositório idempotente (upsert) não basta. Reenvio de
   sessão depois de timeout sobe a carga em dobro. Daí o `progression_event` com PK pela sessão.
3. **`session_set_logs` não guardava o RIR** que a progressão consome. Coluna nullable + `[REGRA]` do
   RIR nulo (só regras por repetição).
4. **Dois lados concordando não é prova — de novo.** Os 4 erros desta fatia que o Gradle pegou foram
   do TESTE, não do motor (matriz de foco sem o controle GENERAL_HEALTH, contagem 480 vs 240, teto por
   tempo 16,33 vs 18 duas vezes): asserções escritas com a conta de cabeça. Lição: derive o número
   do oráculo antes de fixá-lo no teste.
5. **"Maior vão da tabela" é afirmação a verificar.** A fronteira de "grande" foi escrita primeiro como
   0,7 "no maior vão" — falso (o maior é 0,7→0,9) e o glúteo ficava NA borda. Foi para 0,6 e o
   comentário passou a dizer o que é verdade.
6. **A reorganização do diff da referência é a revisão.** Regenerar com a fronteira nova mudou
   exatamente os 360 perfis `fSHOULDERS` e nenhum outro; esse diff era o efeito esperado.

## Decisões do Rafael desta fatia (2026-10-28)

4% fixo · três faixas de pausa · exceção nomeada à fixidez das âncoras · 60+ pausa longa → conservador
explícito (extensão além da spec) · D-1 (chave por exercício), D-2 (idempotência por sessão),
D-3 (`rir` nulo), D-4 (mesociclo por pessoa) aprovados, V69 liberada · grande/pequeno derivado do
peso · LEGS repartido · heurísticas (10%, 3 sessões, Epley, devolver) aceitas como calibráveis.

## Ficou para depois

Verificar V69 + repositórios no `:server:integrationTest` (Docker; escrita na 10b) · RIR no contrato/DTO · diagnóstico de estagnação, promoção por 1RM e a
fase de ênfase (Fatia 11) · texto/UI do aviso de honestidade (Presentation).
