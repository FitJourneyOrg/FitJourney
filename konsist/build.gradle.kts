// :konsist — teste de arquitetura (lint de fronteira). JVM puro, fora do grafo de produção.
// Escaneia o projeto via filesystem; não depende de nenhum outro módulo.
plugins {
    alias(libs.plugins.kotlinJvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.konsist)
    testImplementation(libs.kotlin.testJunit)
}

/*
 * ⭐ AS ENTRADAS DA TASK SÃO OS FONTES DO PROJETO INTEIRO.
 *
 * O `ArchitectureKonsistTest` usa `Konsist.scopeFromProject()`: lê os `.kt` de TODOS os módulos
 * pelo filesystem, em tempo de execução. Para o Gradle, porém, este módulo não depende de nenhum
 * outro — a única entrada declarada era o próprio `konsist/src`, que quase nunca muda.
 *
 * O resultado era o pior tipo de verde: `:konsist:test` ficava `UP-TO-DATE` e **não varria nada**.
 * Dava para quebrar uma regra de arquitetura em qualquer módulo e a task continuava passando sem
 * abrir o arquivo. Era preciso lembrar de `--rerun-tasks` — e o que depende de alguém lembrar não
 * é verificação, é hábito.
 *
 * > **Task sem entrada declarada não fica em cache: fica cega.**
 *
 * O `exclude` de `build/` não é detalhe: sem ele todo arquivo gerado entraria na conta e a task
 * ficaria PERMANENTEMENTE desatualizada, que é o defeito oposto e igualmente inútil — quem vê uma
 * task que nunca reaproveita nada aprende a desconfiar do cache inteiro.
 */
val fontesDoProjeto = rootProject.fileTree(rootProject.projectDir) {
    include("**/src/**/*.kt")
    exclude("**/build/**")
    exclude("**/.gradle/**")
}

tasks.named<Test>("test") {
    inputs.files(fontesDoProjeto)
        .withPropertyName("fontesDeTodoOProjeto")
        .withPathSensitivity(PathSensitivity.RELATIVE)

    /*
     * ⚠️ `fontes` É UM `val` LOCAL, E A CONTAGEM ACONTECE NA EXECUÇÃO. As duas coisas custaram
     * um build cada, e valem comentário.
     *
     * 1. **Local, e não a propriedade do script.** A primeira versão lia `fontesDoProjeto`
     *    diretamente dentro do `doFirst`. Isso faz a ação capturar uma referência ao objeto do
     *    script `.kts`, e o cache de configuração recusa:
     *
     *        cannot serialize Gradle script object references
     *
     *    A ação roda na EXECUÇÃO, quando o script já não existe. Um `val` local dá ao `doFirst`
     *    a própria `FileCollection` para capturar, que o cache sabe serializar.
     *
     *    > **O que a task faz na execução não pode se lembrar do arquivo que a configurou.**
     *
     * 2. **Na execução, e não na configuração.** A segunda versão resolvia a contagem aqui fora,
     *    com um `require` que lia a árvore durante a CONFIGURAÇÃO. Funcionava — e fazia todo `.kt`
     *    do projeto virar entrada do build script: qualquer edição em qualquer arquivo passava a
     *    invalidar o cache de configuração de **todo build**, não só deste. Eram 14s no lugar de
     *    1s em cada ciclo de edição, pagos para proteger contra um glob mal escrito.
     *
     *    > **Guarda que custa em todo build para proteger contra um erro de autoria custa caro no
     *    > lugar errado: o erro acontece uma vez, o preço todo dia.**
     *
     * O que sobrou no lugar da guarda: a linha `varrendo N`. Se alguém quebrar o padrão, a árvore
     * fica vazia, a task volta a ficar `UP-TO-DATE` para sempre e **a linha some do log**. É um
     * sinal mais fraco que um erro de build, e é honesto dizer isso — mas é de graça, e a ausência
     * dela é o que se procura quando o `:konsist:test` voltar a passar rápido demais.
     */
    val fontes = fontesDoProjeto

    doFirst {
        val quantos = fontes.files.size
        check(quantos >= 50) {
            "o fileTree de fontes casou com $quantos arquivo(s): o padrão está errado e o " +
                ":konsist:test voltaria a ficar cego"
        }
        logger.lifecycle("konsist: varrendo $quantos arquivos .kt do projeto")
    }
}
