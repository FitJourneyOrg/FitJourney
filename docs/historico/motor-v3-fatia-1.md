# Motor v3 — Fatia 1: alvos e volume indireto (fechada em 2026-10-23)

Primeira fatia do motor v3 (`trabalho/motor-v3-desenho-e-fatias.md`), etapa [1] do pipeline:
tabela de volume semanal por músculo e tabela de volume indireto, como funções puras.

**Decisão de arquitetura que definiu o resto da fatia:** a tabela nova não entra em
`VolumeModel.kt` (motor antigo). Fica em pacote/arquivo separado (`engine.v3.VolumeTargets`,
`engine.v3.VolumeIndirectTable`) até a Fatia 3 trocar o `StructureEngine` pelo pipeline novo. O
motivo: o motor antigo não tem teto de sessão nem alocação por prioridade — herdar os números
novos hoje (braço a 12, abdômen a zero) faria as sessões crescerem sem nada que as limite, uma
mudança no programa que o usuário recebe sem passar pelo simulador que valida esses números.

Pelo mesmo raciocínio, o fator de idade (0,75 no teto, só 60+ iniciante ou 1º mesociclo) e o
fator de objetivo sobre o teto **não entraram nesta fatia** — quem os consome é o teto da sessão
(Fatia 2), que não existe ainda. Escrever agora seria código que nada chama, e qualquer proxy
para "mesociclo" (que só chega na Fatia 10) mudaria a regra da especificação em vez de
implementá-la. Decisão de como tratar essa ausência fica para a Fatia 2.

`demandaDireta`/`demandaDiretaPorMusculo` são funções puras que recebem o alvo e as séries de
composto por semana de cada doador já calculadas — essa conta (regra de composição de 2 a 4
séries por exercício) é da Fatia 5, não desta.

**Teste (`VolumeTargetsTest`, 11 casos, verde):** reproduz os totais 61·111·148 por nível, a
tabela de demanda direta inteira do intermediário e do avançado (86,0 e 113,0 de total), as duas
exceções do indireto (ombro nunca recipiente; glúteo→posterior em 0,2) e dois caminhos de falha
(indireto maior que o alvo não vira direto negativo; `GENERAL_HEALTH` usa o piso mesmo no
avançado).

Nenhum achado fora do previsto. Nenhuma migration.
