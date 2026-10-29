package dev.rafael.server.features.exercise.db

import dev.rafael.core.result.AppResult
import dev.rafael.server.features.exercise.engine.v3.AncoraDoIniciante
import dev.rafael.server.features.exercise.engine.v3.Mesociclo
import dev.rafael.server.features.exercise.engine.v3.Progressao
import kotlin.uuid.Uuid

/**
 * Progressão e 1RM estimado guardados **por exercício, não por vaga** (checklist da spec, "Durante a
 * implementação"; Fatia 10 do motor v3). Schema: `V69__progressao_por_exercicio.sql`; desenho e
 * decisões D-1 a D-4 em `docs/trabalho/motor-v3-fatia-10-schema.md`.
 *
 * A chave é `(usuário, exercício)` **de propósito** (D-1): a progressão acompanha a pessoa, e
 * acrescentar `programa_id` faria o aluno voltar à calibração a cada programa novo com o mesmo
 * exercício. O risco de estado velho ao retomar um exercício depois de meses é coberto pela regra de
 * pausa acima de 28 dias.
 *
 * O repositório não decide nada: lê e grava o [Progressao.Estado] que o núcleo puro
 * ([Progressao.decidir], [Progressao.calibrar]) devolveu.
 */
interface ProgressaoRepository {

    /** O estado do exercício, ou `null` se a pessoa nunca o fez (a calibração é obrigatória). */
    suspend fun buscar(usuarioId: Uuid, exercicioId: Uuid): AppResult<Progressao.Estado?>

    /**
     * Grava (substitui) o estado do exercício, **sem** passar por uma sessão — o caso do retorno após
     * pausa ([dev.rafael.server.features.exercise.engine.v3.RetornoAposPausa.aplicar]). Para o
     * resultado de uma SESSÃO use [registrar]: é ele que é seguro contra reenvio.
     */
    suspend fun salvar(usuarioId: Uuid, exercicioId: Uuid, estado: Progressao.Estado): AppResult<Unit>

    /**
     * **D-2.** `decidir()` não é idempotente — aplicá-lo duas vezes à mesma sessão sobe a carga em
     * dobro. Aqui o evento da sessão e o novo estado entram NA MESMA transação, e a PK
     * `(usuário, exercício, sessão)` recusa a repetição.
     *
     * @return `true` se a sessão foi aplicada; `false` se já estava registrada — nesse caso o estado
     * **não é regravado** (o cliente reenviou depois de um timeout) e quem chama deve reler com [buscar].
     */
    suspend fun registrar(
        usuarioId: Uuid,
        exercicioId: Uuid,
        sessaoId: Uuid,
        estado: Progressao.Estado,
        evento: AncoraDoIniciante.Evento,
    ): AppResult<Boolean>

    /**
     * Os últimos [limite] eventos do exercício, do mais antigo ao mais novo — a entrada de
     * [AncoraDoIniciante.situacao]. A ordem é a de REGISTRO no servidor (a sessão pode ter sido
     * feita offline e sincronizada depois).
     */
    suspend fun historico(usuarioId: Uuid, exercicioId: Uuid, limite: Int = 50): AppResult<List<AncoraDoIniciante.Evento>>

    /** Todos os estados da pessoa, por exercício — o que o retorno após pausa precisa varrer. */
    suspend fun todos(usuarioId: Uuid): AppResult<Map<Uuid, Progressao.Estado>>
}

/**
 * Onde a pessoa está no mesociclo — **por pessoa, não por programa** (D-4): `mesocicloAtual` entra
 * no pipeline como atributo dela, e é isso que tira o 60+ do modo conservador permanente. Por
 * programa, o débito fechado voltaria. Consequência conhecida e aceita: trocar de programa no meio do
 * mesociclo pode fazer o deload da semana 4 cair sobre exercícios com uma exposição só — inofensivo
 * (o deload mantém a carga e corta as séries pela metade).
 */
interface MesocicloRepository {

    /** O mesociclo da pessoa, ou `null` se ela nunca treinou (= 1º mesociclo, o lado seguro). */
    suspend fun buscar(usuarioId: Uuid): AppResult<Mesociclo?>

    suspend fun salvar(usuarioId: Uuid, mesociclo: Mesociclo): AppResult<Unit>
}
