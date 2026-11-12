package dev.rafael.server.features.exercise.models

import dev.rafael.contract.exercise.ExerciseCategory
import dev.rafael.contract.profile.Accessory
import dev.rafael.contract.profile.BodyLimitation
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.MuscleGroup
import kotlin.uuid.Uuid

data class Exercise(
    val id: Uuid,
    val name: String,
    val category: ExerciseCategory,
    val description: String?,
    val videoRef: String,
    val thumbRef: String,

    // --- taxonomia (V8) — null nos 542 não-curados ---
    val modality: Modality?,
    val movementPattern: MovementPattern?,
    val secondaryPattern: MovementPattern?,
    val isCompound: Boolean?,
    val equipment: String?,
    val primaryMuscles: List<MuscleGroup>,
    val secondaryMuscles: List<MuscleGroup>,
    val unilateral: Boolean?,
    val prescriptionType: PrescriptionType?,
    val level: Level?,
    val contraindications: List<BodyLimitation>,

    // --- V16: principal (base) vs variação. Motor escolhe base primeiro. ---
    // Substituída por `relevancia` (três níveis); cai junto com a reescrita do SlotFiller.
    val isBase: Boolean,

    // --- V64/V65: seleção do motor. Null fora da lista principal. ---
    val volumeTarget: String?,
    val prioridade: Int?,
    val relevancia: Int?,
    val nivelMaximo: Level?,
    val ambiente: Ambiente?,

    // --- V65: catálogo inteiro ---
    val familia: String?,
    val acessorios: Set<Accessory>,

    // --- V67 (fatia 7): RIR mínimo por propriedade -- aprovado por nome, não derivável de
    // movementPattern/equipment (ver docs/trabalho/motor-v3-fatia-7.md). O motor não lê
    // contraindications em tempo de execução.
    val rirMinimoSeguranca: Boolean,

    // --- V74: semântica de carga, exigida por `Progressao.decidir()`/`calibrar()`.
    /**
     * Granularidade física de carga em kg: a menor carga alcançável naquele equipamento.
     *
     * `null` = não se aplica (sem carga externa em kg, como peso do corpo e faixa elástica) e o
     * chamador **não invoca o motor de progressão** para este exercício: `decidir()`/`calibrar()`
     * exigem `Double` não-nulo e não existe fallback por repetição (débito aberto).
     *
     * Hoje é derivado de [equipment] na V74 (paliativo declarado: põe leg press e pec deck no
     * mesmo número); o destino é calibração pelo histórico do usuário.
     */
    val incremento: Double?,
    /**
     * Peso livre sobre barra. Limita o salto proporcional da calibração ao teto de 25%
     * (`Progressao.CALIBRACAO_TETO_BARRA_LIVRE`). `SMITH` é false (trilho guiado) e `LANDMINE`
     * também (arco travado pela ponta fixa).
     *
     * `[INV]` enquanto for derivável, nunca discorda de `equipment IN (BARBELL, TRAP_BAR)` —
     * garantido pelo `chk_exercises_barra_livre` (V74), não por código.
     */
    val barraLivre: Boolean,
    /**
     * Mais peso = exercício mais **fácil** (máquina assistida: a carga é contrapeso). O motor tem
     * de **trocar o sinal** da decisão — `SOBE` significa DIMINUIR a carga.
     *
     * São 4 no catálogo, 1 na lista principal (Barra Fixa Assistida na Máquina). A troca de sinal
     * em `decidir()` ainda NÃO existe: é item que bloqueia o fechamento da fatia do RIR, porque
     * ligar o fio sem ela faz a assistida regredir calada.
     */
    val cargaInvertida: Boolean,
)