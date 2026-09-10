package dev.rafael.app.ui

import dev.rafael.contract.error.ErrorCodes

/**
 * A frase que o usuário lê para cada código de erro (fatia G.2, ARCH #37).
 *
 * ## Por que o texto passou a morar aqui
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
 * ## Na G.3 tudo isto vira `strings.xml`
 *
 * Estas frases são literais Kotlin de propósito. A G.3 extrai os ~500 literais do app de uma vez,
 * e estes vão junto: fazer a mudança de resources aqui, agora, criaria uma segunda mecânica de
 * texto que a G.3 teria de desfazer.
 */
object TextosDeErro {

    /**
     * A frase, ou `null` quando este código não tem texto próprio.
     *
     * `null` **não é falha**: quem chama cai na mensagem do servidor. Acontece em dois casos, e os
     * dois são deliberados — código desconhecido (app antigo, servidor novo) e os de
     * [SEM_TEXTO_PROPRIO].
     */
    fun de(code: String?): String? = when (code) {

        // ---- amizades ----
        ErrorCodes.PEDIDO_NAO_EXISTE -> "Este pedido não existe mais."
        ErrorCodes.PEDIDO_JA_RESPONDIDO -> "Este pedido já foi respondido."
        ErrorCodes.PEDIDO_E_MEU -> "Você não pode aceitar um pedido que você mesmo enviou."
        ErrorCodes.SEM_RELACAO -> "Isso já foi desfeito."
        ErrorCodes.BLOQUEAR_A_SI_MESMO -> "Você não pode bloquear a si mesmo."
        ErrorCodes.PESSOA_NAO_EXISTE -> "Esta pessoa não está mais no FitJourney."

        // ---- conta e sessão ----
        ErrorCodes.TOKEN_INVALIDO -> "Sua sessão expirou. Entre de novo para continuar."
        ErrorCodes.MINHA_CONTA_SUMIU -> "Não consegui carregar sua conta agora. Tente de novo em instantes."
        ErrorCodes.CODIGO_DE_USUARIO_NAO_EXISTE -> "Nenhum usuário com esse código."
        ErrorCodes.RATE_LIMIT_CODIGO -> "Muitas tentativas. Tente de novo daqui a pouco."
        ErrorCodes.IDIOMA_NAO_SUPORTADO -> "Este idioma ainda não está disponível."

        // ---- perfil e onboarding ----
        ErrorCodes.MEU_PERFIL_INCOMPLETO -> "Você ainda não completou o questionário."
        ErrorCodes.PERFIL_DE_TERCEIRO_INDISPONIVEL -> "Este perfil não está disponível."
        ErrorCodes.DIAS_POR_SEMANA_INVALIDO -> "Escolha entre 2 e 6 dias por semana."
        ErrorCodes.FOCO_ALEM_DO_LIMITE -> "Escolha no máximo 2 grupos de foco."
        ErrorCodes.IDADE_INVALIDA -> "Digite uma idade entre 5 e 120 anos."
        ErrorCodes.DIAS_INDISPONIVEIS_INVALIDOS -> "Não consegui salvar sua agenda. Tente de novo."

        // ---- portões de geração ----
        ErrorCodes.PERFIL_AUSENTE_PARA_GERAR -> "Complete o questionário antes de gerar treinos."
        ErrorCodes.HEALTH_GATE_REQUIRED -> "Complete a avaliação de saúde antes de gerar treinos."
        ErrorCodes.AGE_GATE_REQUIRED ->
            "Menor de 18: é preciso o reconhecimento de supervisão de um responsável."
        ErrorCodes.PERFIL_INCOMPLETO_PARA_GERAR -> "Complete o questionário para gerar um treino."
        ErrorCodes.EDICAO_DE_IA_E_PREMIUM -> "Editar um programa gerado por IA é um recurso premium."
        ErrorCodes.TREINO_BLOQUEADO_PREMIUM -> "Este treino faz parte do plano Premium."

        // ---- programas e treinos ----
        ErrorCodes.ID_DE_PROGRAMA_INVALIDO -> "Não consegui abrir este programa."
        ErrorCodes.PROGRAMA_NAO_EXISTE -> "Este programa não está mais disponível."
        ErrorCodes.NOME_DE_PROGRAMA_VAZIO -> "Dê um nome ao programa."
        ErrorCodes.TREINO_NAO_EXISTE -> "Este treino não está mais disponível."
        ErrorCodes.TREINO_SEM_PROGRAMA -> "Não consegui salvar este treino."
        ErrorCodes.TREINO_DUPLICADO -> "Não consegui salvar este treino. Tente criar de novo."
        ErrorCodes.NOME_DE_TREINO_VAZIO -> "Dê um nome ao treino."
        ErrorCodes.TREINO_SEM_EXERCICIO -> "Adicione ao menos um exercício ao treino."
        ErrorCodes.EXERCICIO_SEM_SERIE -> "Cada exercício precisa de ao menos uma série."
        ErrorCodes.REPETICOES_INVALIDAS -> "As repetições precisam ser maiores que zero."
        ErrorCodes.ID_DE_EXERCICIO_INVALIDO -> "Não consegui abrir este exercício."
        ErrorCodes.EXERCICIO_FORA_DO_CATALOGO -> "Um dos exercícios deste treino não existe mais."
        ErrorCodes.EXERCICIO_NAO_EXISTE -> "Este exercício não está mais no catálogo."
        ErrorCodes.AMBIENTE_NAO_DEFINIDO -> "Complete o questionário para ver alternativas."

        // ---- cronograma ----
        ErrorCodes.AGENDA_VAZIA -> "Posicione ao menos um treino na semana."
        ErrorCodes.DIA_DA_SEMANA_INVALIDO -> "Não consegui salvar o cronograma. Tente de novo."
        ErrorCodes.DIA_JA_OCUPADO -> "Você já tem um treino neste dia. Escolha outro."
        ErrorCodes.AGENDA_COM_TREINO_REPETIDO -> "O mesmo treino não pode ocupar dois dias."
        ErrorCodes.AGENDA_INCOMPLETA -> "A agenda precisa cobrir exatamente os treinos do programa."
        ErrorCodes.ID_DE_TREINO_INVALIDO -> "Não consegui salvar o cronograma. Tente de novo."

        // ---- execução de treino ----
        ErrorCodes.SESSAO_SEM_SERIE -> "Registre ao menos uma série para concluir o treino."
        ErrorCodes.SESSAO_INVALIDA ->
            "Não consegui salvar este treino concluído. Ele continua na fila e vou tentar de novo."
        ErrorCodes.SESSAO_COM_FIM_ANTES_DO_INICIO -> "O fim do treino não pode ser antes do início."

        // ---- grupos e desafios ----
        ErrorCodes.GRUPO_NAO_EXISTE -> "Este desafio não está disponível para você."
        ErrorCodes.NAO_SOU_MEMBRO -> "Você não faz parte deste desafio."
        ErrorCodes.ADMIN_PRECISA_TRANSFERIR -> "Transfira o cargo de admin antes de sair do grupo."
        ErrorCodes.USE_SAIR_EM_VEZ_DE_EXPULSAR -> "Para sair do grupo, use a opção de sair."
        ErrorCodes.JA_SOU_O_ADMIN -> "Você já é o admin."
        ErrorCodes.DESAFIO_JA_COMECOU -> "O desafio já começou e a entrada está fechada."
        ErrorCodes.SO_O_ADMIN_DO_GRUPO -> "Só o admin do desafio pode fazer isso."
        ErrorCodes.CAMPOS_DO_GRUPO_INVALIDOS -> "Revise os campos do desafio."

        // ---- check-in ----
        ErrorCodes.CHECKIN_SEM_AS_REGRAS -> "Este desafio exige mais do que você enviou."
        ErrorCodes.PRAZO_DE_EXCLUSAO -> "Só dá para apagar um check-in no mesmo dia em que ele foi feito."
        ErrorCodes.CHECKIN_EM_ANALISE -> "Este check-in está sob moderação e não pode ser apagado."
        ErrorCodes.CURSOR_INVALIDO -> "Não consegui carregar mais publicações. Puxe para atualizar."
        ErrorCodes.LOCAL_SEM_NOME -> "Escreva onde você treinou."
        ErrorCodes.SEM_COORDENADAS -> "Não consegui localizar você. Tente de novo."
        ErrorCodes.FOTO_AUSENTE -> "Adicione uma foto para concluir o check-in."
        ErrorCodes.FOTO_GRANDE_DEMAIS -> "Esta foto é pesada demais. Escolha outra ou tire uma pela câmera."
        ErrorCodes.FOTO_INVALIDA -> "Não consegui ler esta imagem. Escolha outra ou tire uma pela câmera."

        // ---- social e moderação ----
        ErrorCodes.SEM_PERMISSAO_PARA_APAGAR_COMENTARIO ->
            "Só quem escreveu, quem fez o check-in, ou o admin do desafio pode apagar este comentário."
        ErrorCodes.REACAO_INVALIDA -> "Esta reação não existe."
        ErrorCodes.JA_DENUNCIEI -> "Você já denunciou isto."
        ErrorCodes.SO_O_ADMIN_JULGA -> "Só o admin do desafio julga denúncias."
        ErrorCodes.DENUNCIA_DO_PROPRIO_CONTEUDO -> "Não dá para denunciar o próprio conteúdo."
        ErrorCodes.CHECKIN_JA_INVALIDADO -> "Este check-in já foi invalidado."
        ErrorCodes.DENUNCIA_SEM_MOTIVO -> "Escreva o motivo da denúncia."
        ErrorCodes.ALVO_INDISPONIVEL -> "Este conteúdo não está mais disponível."

        // ---- notificações ----
        ErrorCodes.TOKEN_DE_PUSH_VAZIO -> "Não consegui preparar as notificações neste aparelho."

        else -> null
    }

    /**
     * ⚠️ **Os códigos que continuam usando a frase do SERVIDOR, por decisão de 2026-09-09.**
     *
     * As mensagens destes nove carregam um número que só o servidor conhece: o prazo da denúncia, o
     * limite de caracteres, os tetos de plano. Com o texto no cliente, a frase precisaria do valor,
     * e havia três saídas: mandar o valor no `ErrorResponse`, mover as constantes para o contrato,
     * ou deixar estes nove com o texto do servidor. **Foi escolhida a terceira.**
     *
     * A consequência é real e é por isso que esta lista existe: **estas nove frases não serão
     * traduzidas** quando o inglês entrar. Sem a lista, elas apareceriam em português no meio de um
     * app em inglês e ninguém saberia dizer quantas eram nem quais.
     *
     * > **Débito que ninguém consegue enumerar não é débito, é surpresa.**
     *
     * O `TextosDeErroTest` afirma que todo código do `ErrorCodes` tem texto próprio **ou** está
     * aqui. Um código novo esquecido quebra o build; um código novo que legitimamente não tenha
     * texto entra nesta lista, e a decisão fica escrita.
     */
    val SEM_TEXTO_PROPRIO: Set<String> = setOf(
        ErrorCodes.PRAZO_DA_DENUNCIA,            // "...até {N} dias"
        ErrorCodes.COMENTARIO_INVALIDO,          // "...com até {N} caracteres"
        ErrorCodes.NOME_DO_LOCAL_LONGO,          // "Use até {N} caracteres"
        ErrorCodes.NOME_CURTO,                   // "Use pelo menos {N} caracteres"
        ErrorCodes.NOME_LONGO,                   // "Use no máximo {N} caracteres"
        ErrorCodes.DIAS_LIVRES_INSUFICIENTES,    // "...treinar {N}x na semana"
        ErrorCodes.LIMITE_DE_IA_GRATIS,          // "...limitado a {N} no plano grátis"
        ErrorCodes.LIMITE_DE_MANUAIS_GRATIS,     // "...limitado a {N} no plano grátis"
        ErrorCodes.LIMITE_DE_PROGRAMAS_PREMIUM,  // "...máximo de {N} programas"
    )

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
