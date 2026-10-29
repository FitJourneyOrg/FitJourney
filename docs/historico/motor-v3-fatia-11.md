# Motor v3 — Fatia 11: nível, promoção e ênfase (fechada em 2026-10-29)

Décima primeira fatia do motor v3. Fecha a condição de troca de produção aberta na Fatia 9/10
(foco permanente sem proteção): entrega a fase de ênfase de verdade (piso nos demais grupos +
proteção dinâmica contra sacrifício na alocação), a seleção de puxada horizontal semanal no BACK, e
os diagnósticos de promoção de nível (iniciante→intermediário por deload-como-diagnóstico,
intermediário→avançado por 1RM congelado). **Oráculo Python primeiro, Kotlin depois**, mesmo padrão
das fatias anteriores. Regras em `regras/01-motor-de-geracao.md`, "Foco — fase de ênfase (Fatia 11)";
schema em `arch/motor-v3-progressao-schema.md`, seção `promotion_decline`.

## O que entrou

- **`VolumeTargets.weeklySets`/`alvos()`**: com foco ativo, todo alvo FORA do foco efetivo vai ao
  piso (coluna do iniciante) — exceto `BACK`, isento quando a ênfase inclui `CHEST`/`SHOULDERS`
  (`EMPURRAR`), isenção NOMEADA ("puxar nunca cai abaixo de empurrar"), não uma desigualdade geral.
- **`PriorityAllocation.alocar(protegidosExtra=...)`**: os alvos do foco saem de `ORDEM_SACRIFICIO`
  (protegidos de rebaixamento/sacrifício, não de corte proporcional). `[INV]` fonte única intacto.
- **`WeeklyBackSelection`/`aplicar_puxada_horizontal_semanal`**: com 2+ vagas de BACK na semana, ao
  menos uma é `HORIZONTAL_PULL` — seleção, zero custo de capacidade, chamada ao FIM de `gerar()`.
- **`PromocaoDeNivel`/`progressao_v3.diagnostico_promocao_*`**: `diagnosticoIniciante` (por âncora),
  `diagnosticoDoPerfil` (agregação, 2 âncoras em PROMOVER ou 1 se só houver uma),
  `diagnosticoAvancado` (1RM congelado entre mesociclos), `podeSugerirDeNovo` (contador de
  mesociclo), e a ponte `diagnosticoFeitoParaTrocaDeAncora` para `AncoraDoIniciante.situacao`.
- **`promotion_decline` (V70)**: guarda só a recusa MAIS RECENTE por `(user_id, nivel_proposto)`.
- **Grade nova em `referencia_progressao.txt`**: tipos `PD`/`PP`/`PA`/`PN` (1.365+341+288+35 casos),
  reproduzidos no Kotlin por `ProgressaoContraOOraculoTest`; casos à mão em `PromocaoDeNivelTest`
  (23 testes, mirror de `test_progressao_v3.py`).
- **`Exercise.movementPattern`** passou a ser lido de fato pelo catálogo de teste (`Oraculo.catalogo()`
  não lia a coluna `movement_pattern` do CSV — ver Achados #1, abaixo).

## Medido

| | |
|---|---|
| `:server:test` | **782** (contagem direta em `docs/gerados/testes.md`, categoria "Unidade — servidor"), 0 falhas |
| `:server:integrationTest` | verde (Docker) |
| Oráculo Python | **94**, 0 falhas |
| Custo da fase (não-foco, 960 perfis) | +592 séries sob escassez real (680/960); 10/960 (1,0%) com algum grupo caindo de `ATENDIDO` para abaixo do mínimo (sempre GLUTES/INTERMEDIATE/MAINTAIN/foco SHOULDERS — combinação mais fraca da matriz); +75 pares `NAO_TREINADO` a mais que com a fase desligada |
| `ombro_vira_isolamento` com foco em peito | **136** (era 16 sem a fase — Fatia 10) — ver Achados #3 |
| Seleção de puxada horizontal | corrige 186/720 e 372/960 perfis sem horizontal; 0 sem candidato em ambos |
| `puxada_vertical_semanal_ausente` | 360/720 e 480/960 (50%) — **100% CASA, 0% ACADEMIA** (falta de equipamento, não defeito de seleção) |
| Âncoras por perfil BEGINNER (720) | mínimo 5, mediana 14, máximo 17 — `[INV]` trancado |

## Achados e lições

1. **O catálogo de teste não lia `movement_pattern`.** `Oraculo.catalogo()` (`MotorV3Varredura720Test`)
   montava `Exercise` via `exercicioV3()`, que fixava `movementPattern = null` sempre — nenhum
   construtor de teste antigo passava o campo porque nada no Kotlin o lia antes desta fatia. Com
   `WeeklyBackSelection` passando a ler o campo, os 1680/1680 perfis da varredura divergiram do
   oráculo de uma vez: o Kotlin nunca achava puxada vertical/horizontal, mesmo com o catálogo real
   tendo os dois. Causa raiz era FIXTURE, não lógica de produção — `exercicioV3` ganhou o parâmetro
   e `Oraculo.catalogo()` passou a ler a coluna do CSV. Lição repetida (3ª vez no projeto): um campo
   novo com default silencioso em fábrica de teste é invisível até algo começar a ler esse campo.
2. **Dois lados concordendo não é prova — de novo.** As outras 6 falhas do Kotlin (de 7 no total)
   eram expectativas congeladas ANTES do piso de ênfase existir: `FocoDoQuizTest` assumia "o resto
   não muda" quando agora o resto vai ao piso; `MotorV3PipelineTest` não esperava os disparos novos
   de puxada no catálogo sintético; e `ombro_vira_isolamento` com foco ainda cravava 16 (Fatia 10),
   não 136 (Fatia 11, com a fase ligada). Nenhuma era bug do motor — todas eram teste medindo o
   motor antigo.
3. **A fase move o `ombro_vira_isolamento` de 16 para 136.** O piso elevando o peso relativo de
   CHEST quando ele é o foco deixa o composto de peito dominar a sessão com mais frequência — a
   regra de segurança do ombro dispara proporcionalmente mais. Não é regressão: é o efeito esperado
   da fase funcionando (ver `[INV]` em `regras/01`, "qualquer código citando 16 para a matriz COM
   foco está medindo a Fatia 10 sem a fase").
4. **O espelho da puxada vertical foi avaliado e descartado — por motivo, não por omissão.**
   `puxada_vertical_semanal_ausente` dispara 100% em CASA (0% ACADEMIA): sem `requires_pullup_bar`
   nem cabo, o pool de `VERTICAL_PULL` para BACK fica vazio ali — exigir a regra pediria o
   impossível. Mesmo achado qualitativo já conhecido ("CASA sem acessório fica sem puxada vertical"),
   agora com número exato, anexado ao débito de acessórios da Fatia 8.
5. **A restrição de VOLUME puxar×empurrar foi medida e rejeitada — o eixo certo era outro.** A
   métrica simples (só compostos) e a refinada (puxar=HORIZONTAL_PULL+deltoide posterior,
   empurrar=push+deltoide anterior/lateral) deram 59,6%–81,4% push>pull, mas restringir volume
   teria recortado "puxada horizontal + deltoide posterior contra TODO o empurrar", generalizando
   um achado real num eixo estreito. A pergunta certa era de SELEÇÃO (qual exercício), não de
   volume — redirecionada para a regra de puxada horizontal semanal (item acima).
6. **Script de medição ad hoc não é cache.** `tools/cache/medir_push_pull.py` e `medir_custo_fase.py`
   violavam a própria regra do `tools/README.md` (cache/ é dado derivado, não script) — movidos para
   `tools/motor_sim/` (nova bancada, documentada no README), paths corrigidos para relativos.

## Decisões do Rafael desta fatia (2026-10-29)

Peso temporário na ênfase: aceito como recurso não usado (regressão pequena, 1,0%) · nenhuma
restrição de volume puxar×empurrar (eixo recortado, não generaliza) · seleção de puxada horizontal
semanal aprovada, sem espelho vertical (falta de equipamento, não defeito) · agregação da promoção:
2 âncoras em PROMOVER, ou 1 se só houver uma (nível barra exigência técnica) · `limite_poucas_reducoes=1`
e o limiar de 2 âncoras registrados como heurísticas calibráveis, primeiras a recalibrar quando o
RIR chegar do cliente · V70 (`promotion_decline`) aprovada · service/rota da promoção aprovado como
sessão NOVA (assunto novo, não resto de fatia) · dívida de produto consolidada num item só ("O motor
sabe, o app não mostra") em vez de quatro espalhadas · `medir_push_pull.py`/`medir_custo_fase.py`
movidos para `tools/motor_sim/` (não eram snapshot testado, não cabiam em `cache/`).

## Ficou para depois

Service/rota que chama `PromocaoDeNivel` com histórico real e decide quando perguntar ao usuário
(sessão nova) · fase de ênfase declarar ao usuário o que ela custa (Presentation) · texto/UI do
aviso de honestidade (Presentation, mesmo copy da fase) · RIR no contrato/DTO do cliente (zera o
−4% e os disparos de promoção em produção enquanto não chegar) · "fase de 2 mesociclos" da ênfase — **estava no plano original desta fatia**
(`motor-v3-desenho-e-fatias.md`/`motor-v3-fatia-11.md`) e não foi implementada: hoje o foco continua
permanente, nenhum código lê `mesociclo_atual` para expirá-lo. Não bloqueava a troca de produção
(essa condição era só a proteção), mas fica como dívida do próprio plano, não como corte decidido.
