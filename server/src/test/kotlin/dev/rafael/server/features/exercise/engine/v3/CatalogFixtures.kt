package dev.rafael.server.features.exercise.engine.v3

import dev.rafael.contract.exercise.ExerciseCategory
import dev.rafael.contract.profile.Accessory
import dev.rafael.contract.profile.Level
import dev.rafael.server.features.exercise.engine.TargetMuscle
import dev.rafael.server.features.exercise.models.Ambiente
import dev.rafael.server.features.exercise.models.Exercise
import dev.rafael.server.features.exercise.models.MovementPattern
import dev.rafael.server.features.exercise.models.Modality
import kotlin.uuid.Uuid

/** `barra_livre` da V74 é função pura de `equipment`; a fixture deriva do mesmo jeito para não
 *  poder discordar do `chk_exercises_barra_livre`. A regra real vive na migration. */
private val EQUIPAMENTOS_DE_BARRA_LIVRE = setOf("BARBELL", "TRAP_BAR")

/** Fábrica de [Exercise] para os testes de `engine.v3` — mesmo padrão de
 *  `SlotFillerTest.ex()`, com os campos V64/V65 que o motor v3 usa. */
fun exercicioV3(
    name: String,
    alvo: TargetMuscle,
    composto: Boolean,
    equipamento: String = "MACHINE",
    familia: String = name,
    prioridade: Int = 1,
    relevancia: Int = 1,
    nivel: Level = Level.BEGINNER,
    nivelMaximo: Level = Level.ADVANCED,
    ambiente: Ambiente = Ambiente.ACADEMIA,
    acessorios: Set<Accessory> = emptySet(),
    rirMinimoSeguranca: Boolean = false,
    movementPattern: MovementPattern? = null,
    // V74. Nada no motor consome os três ainda — a fiação é da fatia do RIR. Os defaults NÃO são
    // silenciosos de propósito (ver o achado da Fatia 11, em que um default de fábrica escondeu
    // um campo até o motor precisar dele): `incremento` nasce com valor usável e `barraLivre` sai
    // do `equipamento` que o próprio teste já declara. Caso inconsistente, passe explícito.
    incremento: Double? = 2.5,
    barraLivre: Boolean = equipamento in EQUIPAMENTOS_DE_BARRA_LIVRE,
    cargaInvertida: Boolean = false,
) = Exercise(
    id = Uuid.random(),
    name = name,
    category = ExerciseCategory.CHEST,
    description = null,
    videoRef = "",
    thumbRef = "",
    modality = Modality.STRENGTH,
    movementPattern = movementPattern,
    secondaryPattern = null,
    isCompound = composto,
    equipment = equipamento,
    primaryMuscles = emptyList(),
    secondaryMuscles = emptyList(),
    unilateral = null,
    prescriptionType = null,
    level = nivel,
    contraindications = emptyList(),
    isBase = true,
    volumeTarget = alvo.name,
    prioridade = prioridade,
    relevancia = relevancia,
    nivelMaximo = nivelMaximo,
    ambiente = ambiente,
    familia = familia,
    acessorios = acessorios,
    rirMinimoSeguranca = rirMinimoSeguranca,
    incremento = incremento,
    barraLivre = barraLivre,
    cargaInvertida = cargaInvertida,
)
