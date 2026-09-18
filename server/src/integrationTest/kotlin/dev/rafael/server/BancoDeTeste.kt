package dev.rafael.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import dev.rafael.server.db.Migrations
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * UM Postgres para toda a suíte de integração.
 *
 * ## Por que deixou de ser um container por classe
 *
 * Cada classe subia o próprio container e rodava as **47 migrations** do zero: ~5s de boot mais
 * ~2,5s de schema, oito vezes. Com os arquivos de V40–V46 seriam treze — e **suíte lenta é suíte
 * que ninguém roda**.
 *
 * **Duas classes continuam com container próprio, de propósito**: `MigrationIntegrationTest` e
 * `DisplayNameBackfillIntegrationTest`. Elas não testam dado — testam o **schema nascendo**. A
 * segunda migra só até a V34, insere usuários como existiam antes da coluna, e só então roda a V35
 * para exercitar o backfill; um banco já migrado não teria o que preencher. Um container limpo é o
 * objeto do teste, não o custo dele.
 *
 * Isso não é hipótese neste projeto: a suíte de integração atravessou **duas fatias sem ser
 * executada** porque exige Docker e não roda junto com `:server:test`. Quando finalmente rodou, a
 * V40 tinha quebrado 51 de 62 testes. O custo de rodar é o que decide se alguém roda.
 *
 * ## O que se perde, e como isso é compensado
 *
 * O isolamento deixa de ser **por construção** e passa a depender de [limpar], que roda uma vez por
 * **classe** — não por teste. Isso impõe uma regra a quem escreve:
 *
 * > **Asserção sobre a tabela inteira assume um isolamento que esta suíte não dá.** Afirme sobre o
 * > cenário do teste — o par, o grupo, o usuário —, nunca sobre um `count()` global.
 *
 * Já cobrou uma vez: um teste de cascata afirmava `blocks.count() == 0` e falhou por causa dos
 * bloqueios dos testes anteriores da mesma classe, sem nenhuma relação com a cascata.
 *
 * Em troca:
 *
 * - a limpeza é explícita e vive num lugar só — se faltar tabela, falta para todo mundo ao mesmo
 *   tempo, e o teste que quebra aponta para cá;
 * - o `TRUNCATE ... CASCADE` é mais rápido que subir container, então cada classe continua
 *   começando de um banco vazio;
 * - o schema é aplicado **uma vez**, o que também testa que as 47 migrations convivem — que é o
 *   que o `MigrationIntegrationTest` afirma de propósito, e continua fazendo com container próprio;
 * - **doze das treze classes** compartilham o mesmo Postgres, o que tirou da suíte os oito boots
 *   que ela pagava só para chegar num banco vazio.
 *
 * ## Container singleton, sem `@Testcontainers`
 *
 * O container sobe no primeiro acesso e **nunca é parado**: o Ryuk do Testcontainers o derruba
 * quando a JVM morre. Parar em `@AfterAll` de alguma classe mataria o banco das outras, e a ordem
 * entre classes não é garantida.
 */
object BancoDeTeste {

    // Sem `<*>`: nesta versão do Testcontainers a classe **não é genérica**, e o curinga faz o
    // Kotlin perder `jdbcUrl`/`username`/`password`. Os outros arquivos da suíte já usavam a forma
    // sem parâmetro — copiar a assinatura errada de memória custou uma compilação.
    private val postgres by lazy {
        PostgreSQLContainer("postgres:16-alpine").apply { start() }
    }

    private lateinit var db: Database

    val dataSource: HikariDataSource by lazy {
        val ds = HikariConfig().apply {
            jdbcUrl = postgres.jdbcUrl
            username = postgres.username
            password = postgres.password
            driverClassName = "org.postgresql.Driver"
            isAutoCommit = false
        }.let(::HikariDataSource)

        Migrations.run(ds)
        db = Database.connect(ds)
        ds
    }

    /**
     * Zera os dados **sem** derrubar o schema. Chamar no `@BeforeAll` de cada classe.
     *
     * `TRUNCATE ... RESTART IDENTITY CASCADE` numa lista só: o `CASCADE` resolve a ordem das
     * chaves estrangeiras sozinho, e listar as tabelas na ordem certa seria mais uma coisa para
     * manter em sincronia com o schema.
     *
     * **O catálogo de exercícios NÃO é limpo.** Ele vem das migrations de seed (V4, V9, V28…) e é
     * dado de referência, não dado de teste — o `WorkoutGenerationIntegrationTest` depende dele
     * para gerar um programa. Truncá-lo faria a suíte inteira depender de recriá-lo.
     *
     * ⚠️ **Mas `exercise_translations` É limpa** (fatia H), e a distinção importa: a V49 cria a
     * tabela VAZIA, e a carga dos nomes traduzidos é migration de dado separada (H.3). Enquanto ela
     * não existir, tudo que estiver ali foi um teste que inseriu — logo, é dado de teste.
     *
     * > **A tabela de referência é a que as migrations preenchem; a que nasce vazia pertence a
     * > quem escreveu nela.**
     *
     * Quando a H.3 entrar, esta linha precisa sair da lista: aí as traduções passam a ser
     * referência, e truncá-las faria todo teste de idioma ler o piso e passar verde por engano.
     */
    fun limpar() {
        // ⚠️ REIVINDICA O DEFAULT ANTES DE LIMPAR.
        //
        // `Database.connect` do Exposed é **estado global**: quem conecta por último vira o default
        // de todo `transaction { }` do processo. Hoje só este objeto conecta, então a linha é uma
        // guarda — mas ela nasceu de um estrago concreto, e é por isso que fica.
        //
        // Enquanto seis classes ainda subiam o próprio container, elas conectavam o datasource
        // DELAS no `@BeforeAll` e o fechavam no `@AfterAll`; a classe seguinte, que usava este
        // objeto, herdava um datasource morto. Sintoma: **39 testes falhando com `SQLException`**,
        // todos nas classes que rodaram DEPOIS de uma com container próprio — e a que rodou
        // primeiro passou, o que fazia o erro parecer aleatório.
        //
        // > **Estado global de conexão não convive com duas estratégias de container.**
        //
        // As duas classes que ainda sobem container próprio (`MigrationIntegrationTest` e
        // `DisplayNameBackfillIntegrationTest`) NÃO chamam `Database.connect` — falam JDBC cru,
        // porque testam o schema nascendo, antes de haver `Table` que o descreva. É o que as torna
        // inofensivas aqui, e é a condição que esta linha protege caso alguém a esqueça.
        TransactionManager.defaultDatabase = db

        dataSource.connection.use { conexao ->
            conexao.createStatement().use { st ->
                st.execute(
                    """
                    TRUNCATE TABLE
                        exercise_translations,
                        group_daily_notices,
                        moderation_actions,
                        group_reports,
                        check_in_reactions,
                        check_in_comments,
                        check_ins,
                        group_invites,
                        group_rules,
                        group_members,
                        groups,
                        notifications,
                        device_tokens,
                        friendships,
                        blocks,
                        user_achievements,
                        session_set_logs,
                        workout_sessions,
                        workout_sets,
                        workout_exercises,
                        workouts,
                        programs,
                        profiles,
                        users
                    RESTART IDENTITY CASCADE
                    """.trimIndent(),
                )
            }
            conexao.commit()
        }
    }
}
