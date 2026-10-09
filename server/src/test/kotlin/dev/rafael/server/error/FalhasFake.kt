package dev.rafael.server.error

/** Fake de [Falhas]: guarda o que foi registrado para o teste afirmar. */
class FalhasFake : Falhas {
    val registradas = mutableListOf<Pair<String, Any?>>()

    override fun registrar(contexto: String, erro: Any?) {
        registradas += contexto to erro
    }

    val contextos: List<String> get() = registradas.map { it.first }
}
