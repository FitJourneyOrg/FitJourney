package dev.rafael.server.features.exercise.models

/** Taxonomia do exercício (V8, Fatia F). Vive só no server — é insumo do
 *  motor de geração; o cliente não vê. Contraindicação usa BodyLimitation
 *  do shared-contract (mesmo conceito do lado do usuário — 1:1 no filtro). */

enum class Modality { STRENGTH, MOBILITY, CARDIO, PLYOMETRIC, SKILL }

enum class MovementPattern {
    NONE, SQUAT, HINGE, LUNGE, HORIZONTAL_PUSH, VERTICAL_PUSH,
    HORIZONTAL_PULL, VERTICAL_PULL, ROTATION, CARRY, GAIT, STRIKE,
    STRETCH, KNEE_FLEXION, KNEE_EXTENSION,
}

enum class PrescriptionType { REPS, TIME, DISTANCE }

/** V65. Onde o exercício é feito. Academia recebe ACADEMIA + AMBOS; casa recebe CASA + AMBOS. */
enum class Ambiente { ACADEMIA, CASA, AMBOS }

// O acessório exigido pelo exercício usa `Accessory`, do contrato — o MESMO enum que o perfil
// declara. Dois enums para o mesmo conceito é garantia de que um dia discordem.