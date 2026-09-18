package dev.rafael.app.ui

import androidx.annotation.StringRes
import dev.rafael.app.R
import dev.rafael.contract.error.ErrorCodes

/**
 * A frase que o usuário lê para cada código de erro (fatia G.2, extraída na G.3, ARCH #37).
 *
 * ## Por que o texto passou a morar no cliente
 *
 * Até a G.2 o cliente exibia a `ErrorResponse.message` literalmente, e por isso **103 frases em
 * português viviam no servidor**. Com dois idiomas isso deixa de funcionar: o servidor não sabe em
 * que idioma a TELA está.
 *
 * ## Isto NÃO abandona a lição do #31, muda o veículo
 *
 * O `ErrorUiTest` registra três defeitos seguidos do mesmo tipo, e a regra que ficou deles:
 * *"quando o servidor tem contexto e o cliente não, quem escreve a frase é o servidor"*.
 *
 * O que o servidor tem é **conhecimento da situação**, não direito sobre as palavras — e o código
 * carrega esse conhecimento igual. O risco de inverter é o código ficar genérico demais e perder a
 * especificidade que aquela lição custou três defeitos para conquistar; foi por isso que a G.2
 * desmembrou cinco situações que compartilhavam frase.
 *
 * > **Código vago é a volta do texto fixo, com outro nome.**
 *
 * E a proteção antiga continua de pé no fallback: **código que este arquivo não conhece cai na
 * mensagem do servidor**, que é o comportamento de antes. Um servidor novo falando com um app
 * antigo continua explicando melhor do que "algo deu errado".
 *
 * ## O título continua sendo da FAMÍLIA
 *
 * Só o TEXTO vem daqui. Título, ícone e ação seguem por tipo de erro, e é o desenho que o
 * `ErroInline` já documentava: *"o título é a CATEGORIA do erro; o texto é o que aconteceu"*.
 *
 * ## Desde a G.3: `@StringRes Int`, não `String`
 *
 * As frases mudaram de casa (`res/values/strings.xml`) mas não de dono. Devolver o id em vez do
 * texto mantém este objeto **Kotlin puro** — sem `@Composable`, sem `Context` — que é a mesma
 * escolha do `Rotulos.kt` e o que deixa `ErrorUi` testável sem Android.
 *
 * A chave é **derivada** do código: `PESSOA_NAO_EXISTE` → `R.string.erro_pessoa_nao_existe`. O
 * `TextosDeErroTest` confere a derivação lendo o `strings.xml`, então chave fora do padrão quebra
 * o build em vez de virar string órfã no catálogo do tradutor.
 */
object TextosDeErro {

    /**
     * A frase, ou `null` quando este código não tem texto próprio.
     *
     * `null` **não é falha**: quem chama cai na mensagem do servidor. Acontece em dois casos, e os
     * dois são deliberados — código desconhecido (app antigo, servidor novo) e os de
     * [SEM_TEXTO_PROPRIO].
     */
    @StringRes
    fun de(code: String?): Int? = when (code) {

        // ---- amizades ----
        ErrorCodes.PEDIDO_NAO_EXISTE -> R.string.erro_pedido_nao_existe
        ErrorCodes.PEDIDO_JA_RESPONDIDO -> R.string.erro_pedido_ja_respondido
        ErrorCodes.PEDIDO_E_MEU -> R.string.erro_pedido_e_meu
        ErrorCodes.SEM_RELACAO -> R.string.erro_sem_relacao
        ErrorCodes.BLOQUEAR_A_SI_MESMO -> R.string.erro_bloquear_a_si_mesmo
        ErrorCodes.PESSOA_NAO_EXISTE -> R.string.erro_pessoa_nao_existe

        // ---- conta e sessão ----
        ErrorCodes.TOKEN_INVALIDO -> R.string.erro_token_invalido
        ErrorCodes.MINHA_CONTA_SUMIU -> R.string.erro_minha_conta_sumiu
        ErrorCodes.CODIGO_DE_USUARIO_NAO_EXISTE -> R.string.erro_codigo_de_usuario_nao_existe
        ErrorCodes.RATE_LIMIT_CODIGO -> R.string.erro_rate_limit_codigo
        ErrorCodes.IDIOMA_NAO_SUPORTADO -> R.string.erro_idioma_nao_suportado

        // ---- perfil e onboarding ----
        ErrorCodes.MEU_PERFIL_INCOMPLETO -> R.string.erro_meu_perfil_incompleto
        ErrorCodes.PERFIL_DE_TERCEIRO_INDISPONIVEL -> R.string.erro_perfil_de_terceiro_indisponivel
        ErrorCodes.DIAS_POR_SEMANA_INVALIDO -> R.string.erro_dias_por_semana_invalido
        ErrorCodes.FOCO_ALEM_DO_LIMITE -> R.string.erro_foco_alem_do_limite
        ErrorCodes.IDADE_INVALIDA -> R.string.erro_idade_invalida
        ErrorCodes.DIAS_INDISPONIVEIS_INVALIDOS -> R.string.erro_dias_indisponiveis_invalidos

        // ---- portões de geração ----
        ErrorCodes.PERFIL_AUSENTE_PARA_GERAR -> R.string.erro_perfil_ausente_para_gerar
        ErrorCodes.HEALTH_GATE_REQUIRED -> R.string.erro_health_gate_required
        ErrorCodes.AGE_GATE_REQUIRED -> R.string.erro_age_gate_required
        ErrorCodes.PERFIL_INCOMPLETO_PARA_GERAR -> R.string.erro_perfil_incompleto_para_gerar
        ErrorCodes.EDICAO_DE_IA_E_PREMIUM -> R.string.erro_edicao_de_ia_e_premium
        ErrorCodes.TREINO_BLOQUEADO_PREMIUM -> R.string.erro_treino_bloqueado_premium

        // ---- programas e treinos ----
        ErrorCodes.ID_DE_PROGRAMA_INVALIDO -> R.string.erro_id_de_programa_invalido
        ErrorCodes.PROGRAMA_NAO_EXISTE -> R.string.erro_programa_nao_existe
        ErrorCodes.NOME_DE_PROGRAMA_VAZIO -> R.string.erro_nome_de_programa_vazio
        ErrorCodes.TREINO_NAO_EXISTE -> R.string.erro_treino_nao_existe
        ErrorCodes.TREINO_SEM_PROGRAMA -> R.string.erro_treino_sem_programa
        ErrorCodes.TREINO_DUPLICADO -> R.string.erro_treino_duplicado
        ErrorCodes.NOME_DE_TREINO_VAZIO -> R.string.erro_nome_de_treino_vazio
        ErrorCodes.TREINO_SEM_EXERCICIO -> R.string.erro_treino_sem_exercicio
        ErrorCodes.EXERCICIO_SEM_SERIE -> R.string.erro_exercicio_sem_serie
        ErrorCodes.REPETICOES_INVALIDAS -> R.string.erro_repeticoes_invalidas
        ErrorCodes.ID_DE_EXERCICIO_INVALIDO -> R.string.erro_id_de_exercicio_invalido
        ErrorCodes.EXERCICIO_FORA_DO_CATALOGO -> R.string.erro_exercicio_fora_do_catalogo
        ErrorCodes.EXERCICIO_NAO_EXISTE -> R.string.erro_exercicio_nao_existe
        ErrorCodes.AMBIENTE_NAO_DEFINIDO -> R.string.erro_ambiente_nao_definido

        // ---- cronograma ----
        ErrorCodes.AGENDA_VAZIA -> R.string.erro_agenda_vazia
        ErrorCodes.DIA_DA_SEMANA_INVALIDO -> R.string.erro_dia_da_semana_invalido
        ErrorCodes.DIA_JA_OCUPADO -> R.string.erro_dia_ja_ocupado
        ErrorCodes.AGENDA_COM_TREINO_REPETIDO -> R.string.erro_agenda_com_treino_repetido
        ErrorCodes.AGENDA_INCOMPLETA -> R.string.erro_agenda_incompleta
        ErrorCodes.ID_DE_TREINO_INVALIDO -> R.string.erro_id_de_treino_invalido

        // ---- execução de treino ----
        ErrorCodes.SESSAO_SEM_SERIE -> R.string.erro_sessao_sem_serie
        ErrorCodes.SESSAO_INVALIDA -> R.string.erro_sessao_invalida
        ErrorCodes.SESSAO_COM_FIM_ANTES_DO_INICIO -> R.string.erro_sessao_com_fim_antes_do_inicio

        // ---- grupos e desafios ----
        ErrorCodes.GRUPO_NAO_EXISTE -> R.string.erro_grupo_nao_existe
        ErrorCodes.NAO_SOU_MEMBRO -> R.string.erro_nao_sou_membro
        ErrorCodes.ADMIN_PRECISA_TRANSFERIR -> R.string.erro_admin_precisa_transferir
        ErrorCodes.USE_SAIR_EM_VEZ_DE_EXPULSAR -> R.string.erro_use_sair_em_vez_de_expulsar
        ErrorCodes.JA_SOU_O_ADMIN -> R.string.erro_ja_sou_o_admin
        ErrorCodes.DESAFIO_JA_COMECOU -> R.string.erro_desafio_ja_comecou
        ErrorCodes.SO_O_ADMIN_DO_GRUPO -> R.string.erro_so_o_admin_do_grupo
        ErrorCodes.CAMPOS_DO_GRUPO_INVALIDOS -> R.string.erro_campos_do_grupo_invalidos

        // ---- check-in ----
        ErrorCodes.CHECKIN_SEM_AS_REGRAS -> R.string.erro_checkin_sem_as_regras
        ErrorCodes.PRAZO_DE_EXCLUSAO -> R.string.erro_prazo_de_exclusao
        ErrorCodes.CHECKIN_EM_ANALISE -> R.string.erro_checkin_em_analise
        ErrorCodes.CURSOR_INVALIDO -> R.string.erro_cursor_invalido
        ErrorCodes.LOCAL_SEM_NOME -> R.string.erro_local_sem_nome
        ErrorCodes.SEM_COORDENADAS -> R.string.erro_sem_coordenadas
        ErrorCodes.FOTO_AUSENTE -> R.string.erro_foto_ausente
        ErrorCodes.FOTO_GRANDE_DEMAIS -> R.string.erro_foto_grande_demais
        ErrorCodes.FOTO_INVALIDA -> R.string.erro_foto_invalida

        // ---- social e moderação ----
        ErrorCodes.SEM_PERMISSAO_PARA_APAGAR_COMENTARIO ->
            R.string.erro_sem_permissao_para_apagar_comentario
        ErrorCodes.REACAO_INVALIDA -> R.string.erro_reacao_invalida
        ErrorCodes.JA_DENUNCIEI -> R.string.erro_ja_denunciei
        ErrorCodes.SO_O_ADMIN_JULGA -> R.string.erro_so_o_admin_julga
        ErrorCodes.DENUNCIA_DO_PROPRIO_CONTEUDO -> R.string.erro_denuncia_do_proprio_conteudo
        ErrorCodes.CHECKIN_JA_INVALIDADO -> R.string.erro_checkin_ja_invalidado
        ErrorCodes.DENUNCIA_SEM_MOTIVO -> R.string.erro_denuncia_sem_motivo
        ErrorCodes.ALVO_INDISPONIVEL -> R.string.erro_alvo_indisponivel

        // ---- notificações ----
        ErrorCodes.TOKEN_DE_PUSH_VAZIO -> R.string.erro_token_de_push_vazio

        // ---- os nove que carregavam número do servidor (G.4, 2026-09-11) ----
        // O número virou parte da frase no catálogo, com o nome da constante do servidor no
        // comentário de cada uma. Ver a nota do `SEM_TEXTO_PROPRIO`, logo abaixo.
        ErrorCodes.NOME_CURTO -> R.string.erro_nome_curto
        ErrorCodes.NOME_LONGO -> R.string.erro_nome_longo
        ErrorCodes.COMENTARIO_INVALIDO -> R.string.erro_comentario_invalido
        ErrorCodes.NOME_DO_LOCAL_LONGO -> R.string.erro_nome_do_local_longo
        ErrorCodes.PRAZO_DA_DENUNCIA -> R.string.erro_prazo_da_denuncia
        ErrorCodes.LIMITE_DE_IA_GRATIS -> R.string.erro_limite_de_ia_gratis
        ErrorCodes.LIMITE_DE_MANUAIS_GRATIS -> R.string.erro_limite_de_manuais_gratis
        ErrorCodes.LIMITE_DE_PROGRAMAS_PREMIUM -> R.string.erro_limite_de_programas_premium
        ErrorCodes.DIAS_LIVRES_INSUFICIENTES -> R.string.erro_dias_livres_insuficientes

        else -> null
    }

    /**
     * ⚠️ **VAZIA desde 2026-09-11, e a lista fica de pé pelo que ela ensina.**
     *
     * Aqui moravam nove códigos cuja frase carregava um número que só o servidor conhece — prazo
     * da denúncia, limite de caracteres, tetos de plano. Em 2026-09-09 a decisão foi deixá-los com
     * o texto do servidor, o que significava **nove frases em português no meio de um app em
     * inglês**. A lista existia para que se soubesse quantas eram e quais.
     *
     * > **Débito que ninguém consegue enumerar não é débito, é surpresa.**
     *
     * ## Por que a terceira saída acabou sendo a mais simples
     *
     * As opções eram: mandar o valor no `ErrorResponse`, mover as constantes para o contrato, ou
     * cravar o número na frase do catálogo. A terceira parecia a suja, e é a que este catálogo
     * **já praticava** em `erro_dias_por_semana_invalido` ("entre 2 e 6"), `erro_idade_invalida`
     * ("5 e 120"), `erro_foco_alem_do_limite` ("no máximo 2") e `enum_bloqueio_lotado` ("50
     * participantes") — todos anteriores, todos comentados como regra de produto.
     *
     * Mandar parâmetros no envelope teria criado **duas maneiras de fazer a mesma coisa** no mesmo
     * app, que é pior que qualquer uma das duas.
     *
     * > **Antes de inventar o mecanismo, olhe se a base já resolveu isso quatro vezes.**
     *
     * O preço está registrado como débito: doze frases do catálogo dependem de constantes do
     * servidor que elas não enxergam (`DisplayNamePolicy.MIN`, `ProgramLimits.FREE_AI_LIMIT`, …).
     * Mudar a constante sem mudar a frase faz o servidor recusar num limite e o app anunciar
     * outro. Cada string carrega no comentário o nome da constante que espelha.
     *
     * ## A lista continua existindo, e vazia é o estado dela
     *
     * O `TextosDeErroTest` afirma que todo código do `ErrorCodes` tem texto próprio **ou** está
     * aqui. Um código novo esquecido quebra o build; um que legitimamente não tenha texto entra
     * aqui, e a decisão fica escrita. Apagar o conjunto tiraria o lugar onde essa decisão mora.
     */
    val SEM_TEXTO_PROPRIO: Set<String> = emptySet()

    /**
     * Os genéricos de família. Não têm texto próprio porque o texto deles JÁ é o da família, e é o
     * `ErrorUi` que o escreve.
     */
    val GENERICOS: Set<String> = setOf(
        ErrorCodes.VALIDATION,
        ErrorCodes.UNAUTHORIZED,
        ErrorCodes.FORBIDDEN,
        ErrorCodes.NOT_FOUND,
        ErrorCodes.CONFLICT,
        ErrorCodes.INTERNAL,
        ErrorCodes.ENTITLEMENT_REQUIRED,
    )
}
