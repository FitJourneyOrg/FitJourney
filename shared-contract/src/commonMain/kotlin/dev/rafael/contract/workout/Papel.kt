package dev.rafael.contract.workout

import kotlinx.serialization.Serializable

/**
 * Papel do exercício dentro do treino — define faixa de reps, descanso e RIR alvo.
 *
 * Nasce unificado (2026-11-05, fatia "rir-chega-e-registra") a partir de dois enums gêmeos que
 * tinham a mesma classificação estrutural mas viviam em motores diferentes:
 * - `SessionComposition.Papel` (motor v3) — atribuído por VAGA, depende de quantas séries o
 *   músculo recebe na semana e da posição na distribuição (`SessionComposition.papeisPara()`).
 * - `VolumeModel.SlotRole` (motor de produção, `StructureEngine.roleFor()`), com os nomes
 *   `COMPOSTO_PESADO`/`COMPOSTO_ACESSORIO`/`ISOLAMENTO` — mapeados aqui para
 *   `COMPOSTO`/`ACESSORIO`/`ISOLAMENTO` (mesma classificação estrutural; a calibração NUMÉRICA
 *   de reps/descanso/RIR por papel continua própria de cada motor, em `RoleParams` e
 *   `PrescricaoV3` — unificar o rótulo não obriga a igualar os números).
 *
 * O nome escolhido é `Papel` (não `SlotRole`): é o que `Progressao.decidir()`/`calibrar()` já
 * consomem, e manter a assinatura deles evita regerar o oráculo
 * (`server/src/test/oracle/progressao_v3.py` / `referencia_progressao.txt`, 847 KB).
 *
 * **Os valores não mudam depois desta unificação**: `Papel` é só o rótulo que escolhe qual faixa
 * de reps/RIR consultar — ambos os motores continuam livres para calibrar os próprios números por
 * papel.
 */
@Serializable
enum class Papel { COMPOSTO, ACESSORIO, ISOLAMENTO }
