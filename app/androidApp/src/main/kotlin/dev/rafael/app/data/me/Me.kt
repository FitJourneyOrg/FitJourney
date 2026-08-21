package dev.rafael.app.data.me

import dev.rafael.contract.user.UserDto
import dev.rafael.core.result.AppResult
import kotlinx.coroutines.flow.Flow

/**
 * O USUÁRIO como as telas o enxergam — nome, plano, e-mail (V35, ARCH #33/#34).
 *
 * Mesma forma de [dev.rafael.app.data.stats.Stats]: interface porque a implementação recebe
 * `FitJourneyDatabase` no construtor, e sem ela todo teste de ViewModel exigiria um SQLite real.
 *
 * A diferença para `Stats` é que aqui EXISTE escrita — o nome é a primeira coisa do usuário que
 * o próprio usuário edita. Ver [renomear] para o porquê de ela ser online-only.
 */
interface Me {

    /** Último `/me` conhecido (cache local). Nunca falha; null antes do primeiro sync da vida. */
    fun observar(): Flow<UserDto?>

    /** Busca no servidor e grava no cache; o Flow re-emite. Offline: não faz nada, sem erro. */
    suspend fun sincronizar(forcar: Boolean = false)

    /**
     * Renomeia. Sucesso grava a resposta no cache e o Flow re-emite.
     *
     * ONLINE-ONLY, e isto é uma EXCEÇÃO consciente ao [REGRA] "escrita é um caminho só" (#30).
     * Três razões, na ordem em que pesam:
     *
     * 1. O outbox opera sobre tabelas locais com `alvoId`. O `/me` é um blob no `kv_cache` —
     *    não existe linha de usuário local para escrever de forma otimista. Encaixá-lo exigiria
     *    modelar o usuário no SQLDelight, que é trabalho desproporcional para um campo.
     * 2. A validação é do servidor e volta em `fieldErrors`. A recusa precisa chegar NO MOMENTO
     *    da ação, para a UI marcar o campo — é o mesmo argumento que deixou conteúdo de IA
     *    fora do outbox (#25/#30).
     * 3. O nome é editado raríssimas vezes, e a primeira delas é no onboarding, que já exige
     *    internet (o primeiro login não funciona offline).
     *
     * A consequência é honesta: offline, o nome não muda e o usuário vê o erro. Nada é perdido
     * em silêncio, porque nada chegou a ser gravado localmente. Registrado como débito.
     *
     * @return o nome NORMALIZADO pelo servidor. Devolver em vez de mandar quem chama reler o
     * [observar] evita uma corrida real: o Flow do SQLDelight re-emite de forma assíncrona, e
     * uma releitura logo após a gravação pode pegar o valor antigo.
     */
    suspend fun renomear(nome: String): AppResult<String>
}
