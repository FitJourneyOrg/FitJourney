package dev.rafael.server.features.exercise.engine.v3

import dev.rafael.contract.exercise.ExerciseCategory
import dev.rafael.contract.profile.Accessory
import dev.rafael.contract.profile.Level
import dev.rafael.server.features.exercise.engine.TargetMuscle
import dev.rafael.server.features.exercise.models.Ambiente
import dev.rafael.server.features.exercise.models.Exercise
import dev.rafael.server.features.exercise.models.Modality
import kotlin.uuid.Uuid

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
) = Exercise(
    id = Uuid.random(),
    name = name,
    category = ExerciseCategory.CHEST,
    description = null,
    videoRef = "",
    thumbRef = "",
    modality = Modality.STRENGTH,
    movementPattern = null,
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
)
