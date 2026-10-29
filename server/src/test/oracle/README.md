# Simulador do motor

Bancada fora do git (decisão de 2026-08-23). Dois simuladores, de duas épocas — **não misturar**.

## v3 — o oráculo vigente (fatia 0, 2026-10-14)

Réplica em Python do pipeline da especificação (`docs/propostas/motor-v3-ESPECIFICACAO.md`). É
contra ele que o motor Kotlin vai ser testado na fatia 9.

| arquivo | o que é |
|---|---|
| `spec_v3.py` | os números da spec, num lugar só (tabela de volume, indireto, tetos, referências) |
| `modelo_a_mao.py` | a demanda direta pelo método de 3 passos da spec — **não importa o simulador** |
| `motor_v3.py` | o pipeline: alvos → teto → alocação → semana → composição → preenchimento → auditoria sobre o ENTREGUE |
| `test_motor_v3.py` | os testes da fatia 0 (diferencial, cobertura, disparos) |
| `catalogo_banco.csv` | o pool do motor, **exportado do banco** (`exportar_catalogo.sql`) — o simulador não lê planilha |
| `progressao_v3.py` | Fatia 10: progressão dupla, calibração, 1RM, mesociclo, âncora do iniciante, retorno após pausa — funções puras |
| `test_progressao_v3.py` | os testes da Fatia 10 (inclui 20 kg → 55 kg → 60 kg e as três faixas de pausa) |
| `gerar_referencia_720.py` | gera `referencia_720.txt` (720 perfis) **e** `referencia_foco.txt` (960 perfis com foco peito/ombro); `--verifica` confere os dois |
| `gerar_referencia_progressao.py` | gera `referencia_progressao.txt` (~10,6 mil casos das funções de `progressao_v3.py`) |
| `referencia_720.txt`, `referencia_foco.txt`, `referencia_progressao.txt` | GERADOS, não editar. O Kotlin lê os três (e o CSV): estão em `inputs.files` do Gradle, com guarda `== 4` |
| `historico/` | o v2, congelado (ver o README de lá) |
| `alvo_canonico.csv` + `gerar_v64.py` + `cabecalho_v64.sql` | fonte e gerador da V64 (`volume_target`) |

```
cd server/src/test/oracle
python3 motor_v3.py                       # demanda direta da referência, banco × pretendido
python3 -m unittest -v test_motor_v3      # sem dependência externa
```

Regenerar o catálogo depois de qualquer migration de dado do catálogo (comando no topo de
`exportar_catalogo.sql`). Casamento músculo↔exercício = `volume_target`, um valor por exercício.

O que é PROVISÓRIO no `motor_v3.py` (alocação sob escassez, corte por sessão, ciclo do ponto fixo)
está listado no topo do arquivo, com a fatia que decide cada um.

## A escolha do exercício saiu do provisório (21/10, com a V65)

Era `is_base` + ordem alfabética. Agora é a curadoria: **ambiente** e **acessórios** no pré-filtro,
**relevância 1** abrindo o músculo, **família** impedindo dois do mesmo vetor na sessão e
**prioridade** desempatando. O `catalogo_banco.csv` passou de 79 para 144 linhas e ganhou as
colunas novas — reexporte depois de aplicar a V65 no banco de dev.

Estado dos testes (23): passam 19. Os 4 vermelhos são **anteriores a este trabalho** e são de
outras fatias — três do diferencial contra o modelo à mão (ponto fixo do indireto, fatia 3) e um
do `ombro_vira_isolamento`, regra da spec que a matriz nunca dispara. A curadoria apagou quatro
vermelhos que existiam: o diferencial de BICEPS nos três níveis e o papel trocado em BACK
ISOLAMENTO, que vinha do Pulldown com Braços Estendidos marcado como composto.

**Um buraco ficou enumerado, não escondido:** POSTERIOR não tem composto de nível BEGINNER na
academia, e o iniciante recebe mesa flexora na vaga de composto. Ver
`test_papel_trocado_na_academia_so_no_buraco_conhecido`.

## v2 — histórico

Mudou de pasta em 21/10: está em `historico/`, com um README próprio explicando por que não se
apaga (é a procedência dos números de `docs/historico/motor-redesenho-v2.md`). O
`alvo_canonico_79.csv`, a tabela da V64 antes da revisão da lista principal, também está lá.
