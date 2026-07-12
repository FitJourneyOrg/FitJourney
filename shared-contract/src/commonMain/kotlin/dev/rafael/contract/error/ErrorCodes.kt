package dev.rafael.contract.error

/** Vocabulário de erro da API. Server escreve, cliente lê. */
object ErrorCodes {
    const val VALIDATION = "VALIDATION"
    const val UNAUTHORIZED = "UNAUTHORIZED"
    const val FORBIDDEN = "FORBIDDEN"
    const val NOT_FOUND = "NOT_FOUND"
    const val CONFLICT = "CONFLICT"
    const val INTERNAL = "INTERNAL"
    const val ENTITLEMENT_REQUIRED = "ENTITLEMENT_REQUIRED"   // <- novo (§8.2)
    const val HEALTH_GATE_REQUIRED = "HEALTH_GATE_REQUIRED"   // <- novo (§3.2)
}