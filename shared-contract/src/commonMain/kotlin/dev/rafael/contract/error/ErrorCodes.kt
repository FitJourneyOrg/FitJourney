package dev.rafael.contract.error

/**
 * Vocabulário de erro da API. O servidor escreve, o cliente lê.
 *
 * ## O código é o contrato; a frase é apresentação (G.2, ARCH #37)
 *
 * Até a fatia G.2 estes códigos existiam e quase não eram usados: o cliente exibia a
 * `ErrorResponse.message` literalmente, e por isso **103 frases em português viviam no servidor**.
 * Com dois idiomas isso deixa de funcionar, porque o servidor não sabe em que idioma a TELA está.
 *
 * O KDoc do `ErrorResponse` já mandava fazer assim desde sempre: *"String estável p/ o cliente
 * ramificar **sem parsear** `message`"*.
 *
 * ## Isto INVERTE uma decisão anterior, e de propósito
 *
 * O `ErrorUi` do cliente registra a lição de três defeitos seguidos: *"texto fixo no cliente para
 * um erro que o servidor sabe explicar melhor... quando o servidor tem contexto e o cliente não,
 * quem escreve a frase é o servidor"*.
 *
 * A lição continua válida; o que muda é o veículo. **O que o servidor tem é conhecimento da
 * situação, não direito sobre as palavras** — e o código carrega esse conhecimento igual. O perigo
 * de inverter é fazer o código genérico demais e perder a especificidade que aquela lição custou
 * três defeitos para conquistar.
 *
 * > **Código vago é a volta do texto fixo, com outro nome.**
 *
 * É por isso que a G.2 desmembrou situações que compartilhavam frase. `USUARIO_NAO_ENCONTRADO`
 * cobria "a pessoa que você procurou sumiu" **e** "a sua própria conta sumiu", que são coisas
 * opostas; agora são dois códigos.
 *
 * ## Convenções
 *
 * - **Prefixo por área**, para o cliente agrupar e para o nome dizer de onde veio.
 * - **`String`, nunca enum, no fio.** Código novo no servidor não pode quebrar cliente antigo: o
 *   desconhecido cai no genérico da mesma família, e é para isso que os genéricos continuam aqui.
 * - **Estável para sempre.** Renomear um código quebra o texto em todo app já instalado, e a
 *   pessoa passa a ver a frase de fallback sem que nada nos avise.
 */
object ErrorCodes {

    // ---------------------------------------------------------------------
    // Genéricos. Continuam existindo como FALLBACK, não como preguiça.
    //
    // O cliente cai aqui quando recebe um código que não conhece — servidor novo, app antigo.
    // Sem eles, um código novo viraria tela sem texto.
    // ---------------------------------------------------------------------
    const val VALIDATION = "VALIDATION"
    const val UNAUTHORIZED = "UNAUTHORIZED"
    const val FORBIDDEN = "FORBIDDEN"
    const val NOT_FOUND = "NOT_FOUND"
    const val CONFLICT = "CONFLICT"
    const val INTERNAL = "INTERNAL"

    // ---------------------------------------------------------------------
    // Portões de plano e de segurança. Anteriores à G.2, mantidos com o nome original:
    // renomear quebraria o app instalado, e o `ENTITLEMENT_REQUIRED` é o único que o cliente
    // já ramificava (ele abre o paywall).
    // ---------------------------------------------------------------------
    const val ENTITLEMENT_REQUIRED = "ENTITLEMENT_REQUIRED"
    const val HEALTH_GATE_REQUIRED = "HEALTH_GATE_REQUIRED"
    const val AGE_GATE_REQUIRED = "AGE_GATE_REQUIRED"

    /**
     * ⚠️ **Os códigos que DEVEM levar ao paywall.** Mora aqui, e não num `when` do cliente.
     *
     * Antes da G.2 os quatro portões de plano compartilhavam `ENTITLEMENT_REQUIRED`, e o `ErrorUi`
     * fazia `if (code == ENTITLEMENT_REQUIRED)` para abrir o paywall. Dar texto próprio a cada um
     * exigiu código próprio, e isso **quase apagou o paywall em silêncio**: os códigos novos
     * simplesmente não casariam com o `if`, nenhum teste falharia, e a tela mostraria "Sem
     * permissão" com um botão de voltar em vez da oferta.
     *
     * > **Ramificar por um código só e depois multiplicar os códigos quebra a ramificação sem
     * > quebrar o build.**
     *
     * Com o conjunto no contrato, acrescentar um portão é acrescentar aqui, e o cliente acompanha
     * sozinho. Um teste afirma que todo elemento daqui abre o paywall.
     *
     * **`LIMITE_DE_PROGRAMAS_PREMIUM` está de fora de propósito**: quem bate nele já é premium, e
     * oferecer o plano a quem acabou de pagar é pior que não oferecer nada.
     */
    val PORTOES_DE_PLANO: Set<String> = setOf(
        ENTITLEMENT_REQUIRED,
        LIMITE_DE_IA_GRATIS,
        LIMITE_DE_MANUAIS_GRATIS,
        EDICAO_DE_IA_E_PREMIUM,
        TREINO_BLOQUEADO_PREMIUM,
    )

    // ---------------------------------------------------------------------
    // Conta e sessão
    // ---------------------------------------------------------------------

    /**
     * A conta de QUEM ESTÁ PEDINDO não foi encontrada, logo depois de o servidor tê-la lido.
     *
     * **Não é o mesmo que [PESSOA_NAO_EXISTE]**, e essa confusão era o pior achado do catálogo: a
     * mesma frase *"Usuário não encontrado"* servia a "a pessoa que você procurou sumiu" e a "a
     * sua própria conta sumiu". Dizer que ela não existe para alguém que está logada é o app
     * negando a existência de quem está olhando.
     *
     * Na prática é estado impossível, e o texto do cliente trata como falha nossa.
     */
    const val MINHA_CONTA_SUMIU = "MINHA_CONTA_SUMIU"

    /** A pessoa que você tentou adicionar, bloquear ou visitar não existe mais. */
    const val PESSOA_NAO_EXISTE = "PESSOA_NAO_EXISTE"

    /** Nenhum usuário tem o código de 8 caracteres digitado (35.5). */
    const val CODIGO_DE_USUARIO_NAO_EXISTE = "CODIGO_DE_USUARIO_NAO_EXISTE"

    /** Muitas buscas por código erradas em pouco tempo (35.5). Já existia como literal. */
    const val RATE_LIMIT_CODIGO = "RATE_LIMIT_CODIGO"

    /** Nome de exibição curto demais (V35). */
    const val NOME_CURTO = "NOME_CURTO"

    /** Nome de exibição longo demais (V35). */
    const val NOME_LONGO = "NOME_LONGO"

    /** Tag de idioma que o app não tem (V47, #37). */
    const val IDIOMA_NAO_SUPORTADO = "IDIOMA_NAO_SUPORTADO"

    /** Credencial recusada pelo Firebase. */
    const val TOKEN_INVALIDO = "TOKEN_INVALIDO"

    // ---------------------------------------------------------------------
    // Perfil e onboarding
    // ---------------------------------------------------------------------

    /**
     * **O próprio usuário ainda não terminou o questionário.**
     *
     * Separado de [PERFIL_DE_TERCEIRO_INDISPONIVEL] na G.2. Os dois diziam *"Perfil não
     * encontrado"*, e este aqui **não é erro**: é um passo que falta. A tela pode oferecer o
     * onboarding em vez de uma cara de erro.
     */
    const val MEU_PERFIL_INCOMPLETO = "MEU_PERFIL_INCOMPLETO"

    /**
     * O perfil de outra pessoa não está disponível.
     *
     * ⚠️ **Deliberadamente vago, e não deve ser desmembrado.** Cobre "não existe", "id malformado"
     * e "essa pessoa te bloqueou". O contrato registra a regra: *"404 para id inexistente E
     * malformado, nunca 403 — sem permissão confirmaria a existência da conta"* (9.3-A).
     *
     * Um código mais preciso aqui vazaria exatamente o que o 404 protege.
     */
    const val PERFIL_DE_TERCEIRO_INDISPONIVEL = "PERFIL_DE_TERCEIRO_INDISPONIVEL"

    const val DIAS_POR_SEMANA_INVALIDO = "DIAS_POR_SEMANA_INVALIDO"
    const val FOCO_ALEM_DO_LIMITE = "FOCO_ALEM_DO_LIMITE"
    const val IDADE_INVALIDA = "IDADE_INVALIDA"
    const val DIAS_INDISPONIVEIS_INVALIDOS = "DIAS_INDISPONIVEIS_INVALIDOS"
    const val DIAS_LIVRES_INSUFICIENTES = "DIAS_LIVRES_INSUFICIENTES"

    // ---------------------------------------------------------------------
    // Geração de programa: os três portões (#23, #24, #25)
    // ---------------------------------------------------------------------

    /**
     * Pediu geração **sem ter feito o questionário**.
     *
     * Separado de [HEALTH_GATE_REQUIRED] na G.2: os dois diziam *"Complete a avaliação de saúde"*,
     * e a ação seguinte é outra. Aqui falta o onboarding inteiro; lá falta só a parte de saúde.
     */
    const val PERFIL_AUSENTE_PARA_GERAR = "PERFIL_AUSENTE_PARA_GERAR"

    const val PERFIL_INCOMPLETO_PARA_GERAR = "PERFIL_INCOMPLETO_PARA_GERAR"
    const val LIMITE_DE_PROGRAMAS_PREMIUM = "LIMITE_DE_PROGRAMAS_PREMIUM"
    const val LIMITE_DE_IA_GRATIS = "LIMITE_DE_IA_GRATIS"
    const val LIMITE_DE_MANUAIS_GRATIS = "LIMITE_DE_MANUAIS_GRATIS"
    const val EDICAO_DE_IA_E_PREMIUM = "EDICAO_DE_IA_E_PREMIUM"
    const val TREINO_BLOQUEADO_PREMIUM = "TREINO_BLOQUEADO_PREMIUM"

    // ---------------------------------------------------------------------
    // Programas e treinos
    // ---------------------------------------------------------------------

    /**
     * O programa não existe, ou não é desta conta.
     *
     * **UM código para as sete ocorrências**, e isso é decisão. Todas as sete são a mesma coisa do
     * ponto de vista de quem usa: agir sobre um programa que não está mais lá. Desmembrar por
     * caminho de código daria sete códigos e sete traduções para o mesmo julgamento.
     *
     * > **Desmembrar por caminho de código, e não por situação do usuário, multiplica trabalho sem
     * > mudar uma tela.**
     */
    const val PROGRAMA_NAO_EXISTE = "PROGRAMA_NAO_EXISTE"

    const val ID_DE_PROGRAMA_INVALIDO = "ID_DE_PROGRAMA_INVALIDO"
    const val NOME_DE_PROGRAMA_VAZIO = "NOME_DE_PROGRAMA_VAZIO"

    /** O treino não existe, ou não é desta conta. Mesma decisão do [PROGRAMA_NAO_EXISTE]. */
    const val TREINO_NAO_EXISTE = "TREINO_NAO_EXISTE"

    const val TREINO_SEM_PROGRAMA = "TREINO_SEM_PROGRAMA"
    const val TREINO_DUPLICADO = "TREINO_DUPLICADO"
    const val NOME_DE_TREINO_VAZIO = "NOME_DE_TREINO_VAZIO"
    const val TREINO_SEM_EXERCICIO = "TREINO_SEM_EXERCICIO"
    const val EXERCICIO_SEM_SERIE = "EXERCICIO_SEM_SERIE"
    const val REPETICOES_INVALIDAS = "REPETICOES_INVALIDAS"
    const val ID_DE_EXERCICIO_INVALIDO = "ID_DE_EXERCICIO_INVALIDO"
    const val EXERCICIO_FORA_DO_CATALOGO = "EXERCICIO_FORA_DO_CATALOGO"
    const val EXERCICIO_NAO_EXISTE = "EXERCICIO_NAO_EXISTE"
    const val AMBIENTE_NAO_DEFINIDO = "AMBIENTE_NAO_DEFINIDO"

    // ---------------------------------------------------------------------
    // Cronograma semanal
    // ---------------------------------------------------------------------
    const val AGENDA_VAZIA = "AGENDA_VAZIA"
    const val DIA_DA_SEMANA_INVALIDO = "DIA_DA_SEMANA_INVALIDO"
    const val DIA_JA_OCUPADO = "DIA_JA_OCUPADO"
    const val AGENDA_COM_TREINO_REPETIDO = "AGENDA_COM_TREINO_REPETIDO"
    const val AGENDA_INCOMPLETA = "AGENDA_INCOMPLETA"
    const val ID_DE_TREINO_INVALIDO = "ID_DE_TREINO_INVALIDO"

    // ---------------------------------------------------------------------
    // Execução de treino (Fase 5)
    // ---------------------------------------------------------------------
    const val SESSAO_SEM_SERIE = "SESSAO_SEM_SERIE"
    const val SESSAO_INVALIDA = "SESSAO_INVALIDA"
    const val SESSAO_COM_FIM_ANTES_DO_INICIO = "SESSAO_COM_FIM_ANTES_DO_INICIO"

    // ---------------------------------------------------------------------
    // Grupos e desafios (#33)
    // ---------------------------------------------------------------------

    /** O desafio não existe. Separado de [NAO_SOU_MEMBRO] na G.2. */
    const val GRUPO_NAO_EXISTE = "GRUPO_NAO_EXISTE"

    /**
     * O desafio existe, mas quem pediu **não participa dele**.
     *
     * Desmembrado de `GRUPO_NAO_EXISTE` na G.2, com uma troca consciente: **confirma que o desafio
     * existe**. Aceito porque o id é UUID e ninguém o adivinha, e porque o caso real é sair de um
     * desafio e abrir uma notificação antiga — dizer "não existe" ali faz a pessoa achar que o
     * desafio foi apagado.
     *
     * Diferente do [PERFIL_DE_TERCEIRO_INDISPONIVEL], onde o id circula em notificação e link e a
     * confirmação teria valor para quem estivesse colecionando contas.
     */
    const val NAO_SOU_MEMBRO = "NAO_SOU_MEMBRO"

    const val ADMIN_PRECISA_TRANSFERIR = "ADMIN_PRECISA_TRANSFERIR"
    const val USE_SAIR_EM_VEZ_DE_EXPULSAR = "USE_SAIR_EM_VEZ_DE_EXPULSAR"
    const val JA_SOU_O_ADMIN = "JA_SOU_O_ADMIN"
    const val DESAFIO_JA_COMECOU = "DESAFIO_JA_COMECOU"
    const val SO_O_ADMIN_DO_GRUPO = "SO_O_ADMIN_DO_GRUPO"
    const val CAMPOS_DO_GRUPO_INVALIDOS = "CAMPOS_DO_GRUPO_INVALIDOS"

    // ---------------------------------------------------------------------
    // Check-in (#33, fatia B)
    // ---------------------------------------------------------------------
    const val CHECKIN_SEM_AS_REGRAS = "CHECKIN_SEM_AS_REGRAS"
    const val PRAZO_DE_EXCLUSAO = "PRAZO_DE_EXCLUSAO"
    const val CHECKIN_EM_ANALISE = "CHECKIN_EM_ANALISE"
    const val CURSOR_INVALIDO = "CURSOR_INVALIDO"
    const val LOCAL_SEM_NOME = "LOCAL_SEM_NOME"
    const val NOME_DO_LOCAL_LONGO = "NOME_DO_LOCAL_LONGO"
    const val SEM_COORDENADAS = "SEM_COORDENADAS"
    const val FOTO_AUSENTE = "FOTO_AUSENTE"
    const val FOTO_GRANDE_DEMAIS = "FOTO_GRANDE_DEMAIS"
    const val FOTO_INVALIDA = "FOTO_INVALIDA"

    // ---------------------------------------------------------------------
    // Social e moderação (fatias E.1 e E.2)
    // ---------------------------------------------------------------------
    const val COMENTARIO_INVALIDO = "COMENTARIO_INVALIDO"
    const val SEM_PERMISSAO_PARA_APAGAR_COMENTARIO = "SEM_PERMISSAO_PARA_APAGAR_COMENTARIO"
    const val REACAO_INVALIDA = "REACAO_INVALIDA"
    const val JA_DENUNCIEI = "JA_DENUNCIEI"
    const val SO_O_ADMIN_JULGA = "SO_O_ADMIN_JULGA"
    const val PRAZO_DA_DENUNCIA = "PRAZO_DA_DENUNCIA"
    const val DENUNCIA_DO_PROPRIO_CONTEUDO = "DENUNCIA_DO_PROPRIO_CONTEUDO"
    const val CHECKIN_JA_INVALIDADO = "CHECKIN_JA_INVALIDADO"
    const val DENUNCIA_SEM_MOTIVO = "DENUNCIA_SEM_MOTIVO"

    /**
     * O check-in ou comentário alvo não existe **ou é de outro grupo**.
     *
     * ⚠️ **Vago de propósito, como o [PERFIL_DE_TERCEIRO_INDISPONIVEL].** A segunda metade é
     * alguém tentando alcançar conteúdo de um grupo do qual não participa; uma frase precisa
     * confirmaria que o item existe em algum lugar.
     */
    const val ALVO_INDISPONIVEL = "ALVO_INDISPONIVEL"

    // ---------------------------------------------------------------------
    // Amizades (#35)
    // ---------------------------------------------------------------------

    /** Não há pedido de amizade entre as duas pessoas. */
    const val PEDIDO_NAO_EXISTE = "PEDIDO_NAO_EXISTE"

    /**
     * O pedido existe, mas **não está mais esperando resposta**, ou quem tentou responder foi quem
     * o enviou.
     *
     * Desmembrado de `PEDIDO_NAO_EXISTE` na G.2. O caso comum é corrida: duas telas abertas, e a
     * outra pessoa já respondeu. Dizer "não existe" ali é impreciso, porque ele existe e está
     * resolvido.
     */
    const val PEDIDO_JA_RESPONDIDO = "PEDIDO_JA_RESPONDIDO"

    /**
     * Tentou responder o pedido que ELA MESMA enviou.
     *
     * ## Desmembrado de `PEDIDO_JA_RESPONDIDO` no meio da própria G.2
     *
     * A primeira versão da fatia juntou os dois casos do `podeResponder`, e a frase dizia *"Este
     * pedido já foi respondido"* também para quem tentava aceitar o próprio pedido — onde ela é
     * **simplesmente falsa**: ninguém respondeu nada.
     *
     * Foi o mesmo erro que a fatia inteira existe para corrigir, cometido enquanto eu o corrigia. O
     * teste `quem mandou nao consegue aceitar o proprio pedido` foi quem apontou.
     *
     * > **Guarda que barra por dois motivos precisa de duas frases, mesmo quando a condição é uma
     * > só no código.**
     *
     * É **403 e não 409**: a tela nunca oferece este caminho (quem enviou vê "Pedido enviado", não
     * botões de aceitar), então isto é a guarda de segurança que o `FriendshipPolicy` descreve como
     * *"a falha mais óbvia de um fluxo com aceite"*, e não um estado que a pessoa alcançou usando o
     * app.
     */
    const val PEDIDO_E_MEU = "PEDIDO_E_MEU"

    const val SEM_RELACAO = "SEM_RELACAO"
    const val BLOQUEAR_A_SI_MESMO = "BLOQUEAR_A_SI_MESMO"

    // ---------------------------------------------------------------------
    // Notificações (F.1)
    // ---------------------------------------------------------------------
    const val TOKEN_DE_PUSH_VAZIO = "TOKEN_DE_PUSH_VAZIO"
}
