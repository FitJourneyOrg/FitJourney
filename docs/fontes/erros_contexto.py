# -*- coding: utf-8 -*-
"""
O QUE O USUÁRIO FEZ para chegar em cada erro (fatia G.2, ARCH #37).

POR QUE ISTO É ESCRITO À MÃO, NUM PROJETO QUE GERA TUDO
--------------------------------------------------------
O `gen_erros.py` lê do código a frase, o código, a operação, a rota, a guarda e o comentário.
Nada disso responde a pergunta que decide se o texto está bom: **o que a pessoa estava tentando
fazer?**

`if (!novidade)` é verdade sobre o código. *"Tentou denunciar de novo um check-in que já tinha
denunciado antes"* é verdade sobre a pessoa, e só a segunda permite julgar se a frase serve.

COMO ISTO NÃO FICA DESATUALIZADO
--------------------------------
A chave é a **frase exata**. O gerador cruza os dois lados e relata as duas metades: frase sem
descrição vira lacuna com aviso, descrição órfã vira lista no fim.

Isso não é teoria: a G.2 reescreveu 19 mensagens e desmembrou 6 situações, e o gerador acusou na
hora **30 frases sem descrição e 23 descrições órfãs**. Sem o cruzamento, as 23 antigas
continuariam parecendo válidas.

> **Descrição escrita à mão não some por disciplina. Some porque alguém a vê faltando.**
"""

CAUSAS = {
    # =====================================================================================

    "Identificador de programa inválido.": {
        "StatsRoutes.?": [
            "Abriu a análise de progressão (`/me/progress`) com `?programId=` que não é `avulsos` nem um UUID válido — link antigo, id editado à mão, ou programa que já não existe mais nesse formato.",
            "Abriu o detalhe de um exercício (`/me/progress/exercicio`, J.5) com `?programId=` ausente ou malformado. Aqui não há valor padrão como no `/me/progress`: sem um programa válido não existe o que desenhar.",
        ],
    },

    # A CAUSA DE CADA OCORRÊNCIA, quando a mesma frase serve a mais de um lugar.
    #
    # O `CONTEXTO` descreve a FRASE. Aqui se descreve cada LUGAR que a usa, e a diferença entre
    # os dois níveis é o que decide se a frase precisa ser desmembrada.
    #
    # > **Duas causas diferentes debaixo da mesma frase é a definição de frase vaga.**
    #
    # Chave: a frase, depois `Classe.funcao`. Lista quando a mesma função a produz mais de uma
    # vez, na ordem do arquivo.
    # =====================================================================================

    "Este pedido já foi respondido.": {
        "FriendshipService.responder": [
            "O pedido existe e não está mais PENDENTE: a pessoa já aceitou ou recusou antes, "
            "provavelmente em outro aparelho ou noutra sessão.",
            "Corrida real: o pedido estava pendente quando a tela carregou, e a outra pessoa "
            "respondeu ou cancelou entre a checagem e a gravação.",
        ],
    },

    "Este desafio não está disponível para você.": {
        "CheckInService.naoEncontrado":
            "Tentou fazer check-in, ver o feed ou o ranking de um desafio que não existe, ou do "
            "qual ela não participa. Os dois casos ficam juntos aqui de propósito: são gestos que "
            "a tela só oferece a quem já está dentro.",
        "GroupMembershipService.naoEncontrado":
            "Tentou entrar, sair, convidar ou administrar um desafio que não existe, ou do qual "
            "ela não participa. Mesma decisão do anterior.",
    },

    "Este conteúdo não está mais disponível.": {
        "ModeracaoService.naoEncontrado":
            "Tentou denunciar ou julgar um check-in ou comentário que não existe mais, ou que "
            "pertence a outro desafio. **Vago de propósito**: precisar aqui confirmaria que o item "
            "existe em algum lugar.",
        "SocialService.naoEncontrado":
            "Tentou comentar, reagir ou apagar num check-in que não existe mais, ou que pertence a "
            "outro desafio. Mesma decisão de vagueza.",
    },

    "Não consegui abrir este programa.": {
        "ProgramRoutes.?": [
            "Renomeou um programa por um link ou atalho com id malformado.",
            "Salvou o cronograma de um programa com id malformado.",
            "Apagou um programa com id malformado.",
        ],
    },

    "Programa não encontrado": {
        "ProgramRoutes.?":
            "Apagou um programa que não existe mais, ou que é de outra conta.",
        "ProgramService.rename":
            "Renomeou um programa que sumiu entre a tela carregar e o salvar.",
        "ProgramService.setSchedule": [
            "Salvou o cronograma de um programa que não existe, ou que é de outra conta.",
            "O programa existia na checagem e sumiu na gravação do cronograma. Corrida.",
        ],
        "ProgramService.resolveNewWorkoutDay":
            "Criou um treino dentro de um programa que não existe mais.",
        "ProgramService.requireEditable":
            "Tentou editar um programa que não existe, ou que é de outra conta.",
        "ProgramService.requireReadable":
            "Tentou abrir um treino cujo programa não existe mais.",
    },

    "Treino não encontrado": {
        "ProgramService.requireReadable":
            "Abriu um treino que não está no programa: foi apagado, ou o link aponta para outro.",
        "WorkoutRoutes.?":
            "Abriu, editou ou apagou um treino que não existe, ou que é de outra conta.",
        "WorkoutRoutes.notFound":
            "Mesma situação, pela função auxiliar que monta a recusa das rotas de treino.",
        "WorkoutRoutes.AppResult":
            "Apagou um treino que não existe mais, ou que é de outra conta.",
    },

    "Nome do programa é obrigatório": {
        "ProgramService.createManual": "Criou um programa novo deixando o nome em branco.",
        "ProgramService.rename": "Renomeou um programa apagando o nome inteiro.",
    },

    "Dia da semana deve estar entre 1 (Seg) e 7 (Dom)": {
        "ProgramService.setSchedule":
            "O cronograma chegou com um dia fora de segunda a domingo. Pela tela é impossível: é "
            "cliente antigo ou item corrompido saindo da fila offline.",
        "ProgramService.resolveNewWorkoutDay":
            "Criou um treino escolhendo um dia fora de segunda a domingo. Mesma origem.",
    },

    "Erro interno": {
        "ErrorMapper.?": [
            "Falhou alguma coisa dentro do servidor: banco fora, exceção não prevista, bug.",
            "O servidor tentou falar com algo externo e não conseguiu. Chega aqui por engano de "
            "tipo interno: `Connection` é erro de CLIENTE.",
        ],
    },
}


CONTEXTO = {

    # ---- Amizades (#35) ----
    "Este pedido não existe mais.":
        "Tentou aceitar ou recusar um pedido de amizade que não existe. O caso real é tela "
        "desatualizada: a outra pessoa cancelou o pedido no meio do caminho.",
    "Este pedido já foi respondido.":
        "Tentou responder um pedido que já foi aceito ou recusado. Quase sempre é corrida entre "
        "dois aparelhos, ou notificação antiga.",
    "Você não pode aceitar um pedido que você mesmo enviou.":
        "⚠️ **Pela tela isto é impossível**: quem enviou vê 'Pedido enviado', não botões de "
        "aceitar. É a guarda de segurança contra a falha mais óbvia de um fluxo com aceite, e "
        "chegar aqui significa requisição feita por fora do app.",
    "Isso já foi desfeito.":
        "Tentou desfazer uma amizade, cancelar um pedido ou desbloquear alguém com quem não tem "
        "mais relação nenhuma. Quase sempre é tela desatualizada: a outra pessoa desfez primeiro.",
    "Você não pode bloquear a si mesmo":
        "Abriu o próprio perfil e tocou em bloquear. A tela não deveria oferecer o botão ali, "
        "então isto é a segunda linha de defesa, não o caminho normal.",
    "Esta pessoa não está mais no FitJourney.":
        "Tentou adicionar, bloquear ou visitar alguém que apagou a conta. O caso real é abrir um "
        "perfil guardado ou uma notificação antiga.",

    # ---- Conta e sessão ----
    "Sua sessão expirou. Entre de novo para continuar.":
        "O app mandou uma credencial que o Firebase recusou: expirada, adulterada, ou de outro "
        "projeto. **A pessoa não vê esta frase hoje**: o cliente ignora a mensagem em 401 e usa "
        "texto próprio. Fica como rede, caso um dia apareça.",
    "Não consegui carregar sua conta agora. Tente de novo em instantes.":
        "⚠️ **É a conta de quem está pedindo, e ela sumiu no meio da requisição**, logo depois de o "
        "servidor tê-la lido. Não é algo que a pessoa fez: é estado impossível. A frase antiga "
        "dizia 'Usuário não encontrado', que é o app negando a existência de quem está olhando.",
    "Nenhum usuário com esse código.":
        "Digitou o código de 8 caracteres de outra pessoa e não existe ninguém com ele. Quase "
        "sempre é erro de digitação, ou a pessoa gerou um código novo.",
    "Muitas tentativas. Tente de novo daqui a pouco.":
        "Errou o código de usuário de outra pessoa muitas vezes seguidas. O limite impede "
        "descobrir códigos por tentativa e erro, e só conta código bem formado que não achou "
        "ninguém: erro de digitação não gasta cota.",
    "Idioma não suportado.":
        "Pediu um idioma que o app ainda não tem. Pela tela é impossível, porque a lista só oferece "
        "os que existem: pega cliente antigo ou requisição feita por fora.",

    # ---- Perfil e onboarding ----
    "Você ainda não completou o questionário.":
        "⚠️ **É o próprio perfil, e não é erro**: é um passo que falta. A frase antiga dizia 'Perfil "
        "não encontrado', que soa como coisa perdida. A tela pode oferecer o onboarding.",
    "Este perfil não está disponível.":
        "Abriu o perfil de outra pessoa e ele não está disponível. **Cobre três coisas de "
        "propósito**: a conta não existe, o id é malformado, ou essa pessoa a bloqueou (9.3-A). "
        "Distinguir vazaria a existência da conta.",
    "Escolha entre 2 e 6 dias por semana":
        "Salvou o questionário com frequência fora do intervalo. Menos de dois não sustenta "
        "frequência por músculo, mais de seis não deixa o corpo se recuperar.",
    "Escolha no máximo 2 grupos de foco":
        "Marcou mais de dois grupos musculares como prioridade. Priorizar tudo é não priorizar "
        "nada: o volume extra deixaria de existir se fosse distribuído entre todos.",
    "Digite uma idade entre 5 e 120 anos.":
        "Digitou uma idade impossível no questionário. Quase sempre é dedo errado no teclado ou "
        "ano de nascimento no lugar da idade. A frase antiga dizia 'fora do intervalo válido' sem "
        "dizer qual era o intervalo.",
    "Dias indisponíveis inválidos":
        "Marcou dias repetidos ou fora de 1 a 7 na agenda. Pela tela é impossível: pega cliente "
        "antigo ou dado corrompido saindo da fila offline.",
    "Dias livres insuficientes para treinar ${dto.daysPerWeek}x na semana":
        "Bloqueou tantos dias na agenda que não sobram dias para a frequência que ela mesma "
        "escolheu. Exemplo: quer treinar cinco vezes e marcou quatro dias como indisponíveis.",

    # ---- Portões de geração (#23, #24, #25) ----
    "Complete o questionário antes de gerar treinos.":
        "⚠️ **Não falta a avaliação de saúde, falta o questionário INTEIRO.** A frase antiga mandava "
        "completar a saúde, e a pessoa ia procurar uma seção que nunca chegou a ver.",
    "Complete a avaliação de saúde antes de gerar treinos.":
        "Tem perfil, e a parte de saúde é que não foi respondida, ou indicou risco sem reconhecê-lo. "
        "É o primeiro dos três portões, e existe porque gerar carga para quem tem condição cardíaca "
        "não reconhecida é risco físico real.",
    "Menor de 18: é preciso o reconhecimento de supervisão de um responsável.":
        "Tem menos de 18 anos e pediu para gerar treino sem marcar que treina com supervisão.",
    "Perfil incompleto para gerar programa.":
        "Pediu para gerar com o questionário pela metade: faltou objetivo, nível ou ambiente, e sem "
        "eles o motor não tem por onde começar.",
    "Você atingiu o limite máximo de $PREMIUM_TOTAL_LIMIT programas.":
        "Sendo premium, tentou criar acima do teto. **Não abre paywall**: oferecer o plano a quem "
        "acabou de pagar é pior que não oferecer nada. É teto de uso, não portão de plano.",
    "Limite de programas do plano grátis: $FREE_TOTAL_LIMIT. Assine o premium pra criar mais.":
        "No plano grátis, tentou criar (por IA ou manual) um programa além do teto de 1 no total. "
        "O primeiro é de graça de propósito: a pessoa vê o valor antes de decidir pagar.",
    "Editar um programa gerado por IA é um recurso premium.":
        "No plano grátis, tentou editar um treino que a IA gerou. Ela pode usar o treino inteiro e "
        "registrar as sessões: o que é pago é modificar o que a IA montou.",
    "Este treino faz parte do plano Premium.":
        "No plano grátis, tentou abrir um treino além do primeiro de um programa gerado por IA. O "
        "programa inteiro fica visível com desfoque, e é isso que ela está tentando destravar.",

    # ---- Programas e treinos ----
    "Não consegui abrir este programa.":
        "Abriu um programa por um link com id malformado. Não acontece navegando pelo app.",
    "Programa não encontrado":
        "Tentou abrir, editar ou apagar um programa que não existe, ou que é de outra conta. O caso "
        "comum é atalho antigo, ou trocar de conta no mesmo aparelho.",
    "Nome do programa é obrigatório":
        "Salvou um programa com o campo de nome vazio.",
    "Treino não encontrado":
        "Tentou abrir, editar ou apagar um treino que não existe, ou que é de outra conta.",
    "Não consegui salvar este treino.":
        "O app tentou criar um treino solto, sem dizer a qual programa ele pertence. É falha de "
        "cliente: pela tela sempre se cria dentro de um programa.",
    "Não consegui salvar este treino. Tente criar de novo.":
        "A fila offline reenviou um treino com um identificador que já pertence a OUTRA conta. O "
        "reenvio normal, da mesma conta, devolve o treino existente e não dá erro.",
    "Nome do treino é obrigatório":
        "Salvou um treino com o campo de nome vazio.",
    "Treino precisa de ao menos 1 exercício":
        "Salvou um treino sem nenhum exercício dentro.",
    "Cada exercício precisa de ao menos 1 série":
        "Salvou um treino em que algum exercício ficou sem nenhuma série.",
    "Repetições devem ser maiores que zero":
        "Digitou zero ou um número negativo no campo de repetições.",
    "Um dos exercícios deste treino não pôde ser salvo.":
        "O treino chegou apontando para um exercício com id malformado. Defeito de cliente.",
    "Um ou mais exercícios não existem no catálogo":
        "Salvou um treino que aponta para exercício removido do catálogo. Acontece com treino "
        "montado numa versão antiga e sincronizado depois de uma curadoria.",

    # ---- Cronograma ----
    "A agenda não pode ser vazia":
        "Salvou o cronograma semanal sem nenhum treino posicionado.",
    "Dia da semana deve estar entre 1 (Seg) e 7 (Dom)":
        "O cronograma chegou com um dia fora de segunda a domingo. Pela tela é impossível.",
    "Dois treinos não podem cair no mesmo dia":
        "Arrastou dois treinos para o mesmo dia da semana no cronograma.",
    "Você já tem um treino neste dia. Escolha outro.":
        "Criou um treino novo escolhendo um dia que já está ocupado naquele programa.",
    "Não consegui salvar o cronograma. Tente de novo.":
        "O cronograma chegou apontando para um treino com id malformado. É defeito de cliente, não "
        "algo que a pessoa consiga fazer pela tela.",
    "A agenda não pode ter treino repetido":
        "Colocou o mesmo treino em dois dias diferentes do cronograma.",
    "A agenda precisa cobrir exatamente os treinos do programa":
        "Salvou um cronograma que deixou treino de fora, ou que inclui treino de outro programa. "
        "Acontece com duas telas abertas.",

    # ---- Catálogo de exercícios ----
    "Não consegui abrir este exercício.":
        "Abriu a tela de um exercício por um link ou atalho com id malformado. Não acontece "
        "navegando pelo app; acontece com deep link antigo ou cache corrompido.",
    "Ambiente de treino não definido":
        "Pediu alternativas de um exercício antes de terminar o questionário, onde se escolhe entre "
        "academia e casa. Sem isso o servidor não sabe quais equipamentos filtrar.",
    "Exercício não encontrado":
        "Pediu alternativas de um exercício que não existe mais no catálogo. Acontece com treino "
        "antigo que aponta para um exercício removido numa curadoria.",

    # ---- Execução de treino ----
    "A sessão precisa de ao menos 1 série":
        "Terminou um treino sem registrar nenhuma série. Costuma ser abrir, não fazer nada e "
        "tocar em concluir.",
    "Não consegui salvar este treino concluído. Ele continua na fila e vou tentar de novo.":
        "O app enviou um registro malformado. **O treino não se perde**: fica no outbox e reenvia. "
        "A frase antiga ('Dados da sessão inválidos') deixava a pessoa achando que perdeu, e ela "
        "podia refazer o registro por medo.",
    "O fim do treino não pode ser antes do início":
        "O registro chegou com hora de término anterior à de início. Acontece quando o relógio do "
        "aparelho é ajustado no meio do treino, incluindo a virada de horário de verão.",

    # ---- Check-in ----
    "Este desafio exige mais do que você enviou.":
        "Tentou fazer check-in sem cumprir todas as regras do desafio: faltou a foto, o nome do "
        "lugar ou o emoji do dia. A tela mostra as exigências antes, então isto normalmente é uma "
        "permissão negada no meio do caminho.",
    "Só dá para apagar um check-in no mesmo dia em que ele foi feito.":
        "Tentou apagar um check-in de um dia anterior. O prazo é o próprio dia porque ele já contou "
        "ponto no ranking, e apagar depois mudaria um placar que as outras pessoas já viram.",
    "Este check-in está sob moderação e não pode ser apagado.":
        "Tentou apagar o próprio check-in depois que alguém o denunciou. Poder apagar aqui seria "
        "uma forma de escapar do julgamento antes de o admin olhar.",
    "Não consegui carregar mais publicações. Puxe para atualizar.":
        "Rolou o feed do grupo e o app mandou um marcador de paginação corrompido. A pessoa não fez "
        "nada de anormal: é defeito de cliente, ou cursor guardado de uma versão antiga.",
    "Escreva onde você treinou.":
        "Fez check-in num desafio que exige localização, o aparelho deu as coordenadas, mas o campo "
        "com o nome do lugar ficou vazio. As coordenadas sozinhas não vão para o feed.",
    "Use até ${CheckInPolicy.MAX_NOME_DO_LOCAL} caracteres.":
        "Escreveu um nome de lugar longo demais. Costuma ser um endereço inteiro colado.",
    "Não consegui localizar você. Tente de novo.":
        "Fez check-in num desafio que exige localização e o aparelho não devolveu as coordenadas: "
        "GPS desligado, sem sinal, ou permissão de local perdida no meio do caminho.",
    "Adicione uma foto para concluir o check-in.":
        "Confirmou o check-in de um desafio que exige foto sem nenhuma imagem anexada.",
    "Esta foto é pesada demais. Escolha outra ou tire uma pela câmera.":
        "Anexou uma imagem acima do limite. Costuma ser foto de câmera profissional ou captura de "
        "tela em resolução muito alta.",
    "Não consegui ler esta imagem. Escolha outra ou tire uma pela câmera.":
        "Anexou um arquivo que não é imagem, ou uma imagem corrompida. Pela câmera do app não "
        "acontece; acontece escolhendo da galeria um arquivo quebrado ou renomeado.",

    # ---- Social e moderação ----
    "Escreva algo, com até ${SocialPolicy.MAX_COMENTARIO} caracteres.":
        "Enviou um comentário vazio, só com espaços, ou longo demais.",
    "Só quem escreveu, quem fez o check-in, ou o admin do desafio pode apagar este comentário.":
        "Tentou apagar um comentário que não é seu, num check-in que não é seu, sem ser admin. "
        "Normalmente é tela desatualizada depois de perder o cargo.",
    "Esta reação não existe.":
        "Mandou um emoji fora das seis opções da barra. Pela tela é impossível: acontece com "
        "cliente adulterado ou versão antiga com lista diferente.",
    "Você já denunciou este check-in.":
        "Denunciou de novo um check-in que já tinha denunciado. A segunda denúncia da mesma pessoa "
        "não conta: várias pessoas viram um caso só na fila do admin.",
    "Você já denunciou este comentário.":
        "Mesma coisa, para comentário.",
    "Só o admin do desafio julga denúncias.":
        "Tentou abrir a fila de moderação ou julgar uma denúncia num grupo onde não é admin. "
        "Acontece com link antigo, ou depois de o cargo ser transferido.",
    "Denúncias só valem até ${ModeracaoPolicy.PRAZO_EM_DIAS} dias.":
        "Tentou denunciar um conteúdo velho demais. Passado o prazo ele já saiu do feed de todo "
        "mundo, e invalidá-lo mexeria num ranking já comemorado.",
    "Não dá para denunciar o próprio conteúdo.":
        "Tocou em denunciar no próprio check-in ou comentário. Para o próprio conteúdo existe "
        "apagar, que resolve na hora sem passar pelo admin.",
    "Este check-in já foi invalidado.":
        "Tentou denunciar um check-in que o admin já invalidou. Não há o que julgar: ele já não "
        "conta ponto, e a decisão não tem recurso.",
    "Escreva o motivo da denúncia.":
        "Confirmou a denúncia com o motivo vazio. É obrigatório porque é a única coisa que o admin "
        "vê para decidir, e denúncia sem motivo vira trabalho sem informação.",
    "Este conteúdo não está mais disponível.":
        "Tentou agir sobre um check-in ou comentário que não existe mais, ou que é de outro grupo. "
        "**Vago de propósito**: precisar aqui confirmaria que o item existe em algum lugar.",

    # ---- Grupos e desafios ----
    "Este desafio não existe mais.":
        "Abriu um desafio cujo id não existe: foi apagado, ou o último membro saiu e ele se desfez "
        "sozinho.",
    "Você não faz parte deste desafio.":
        "⚠️ **O desafio EXISTE e ela não é membro.** O caso real é sair de um desafio e abrir depois "
        "uma notificação antiga. A frase antiga dizia 'não encontrado' e fazia a pessoa achar que "
        "o desafio foi apagado, quando ele está lá e ela é que saiu.",
    "Este desafio não está disponível para você.":
        "Tentou check-in, feed, ranking ou administração num desafio que não existe ou do qual não "
        "participa. **Os dois casos ficam juntos aqui**, ao contrário de abrir o desafio: são "
        "gestos que a tela só oferece a quem já está dentro.",
    "Transfira o cargo de admin antes de sair do grupo.":
        "Sendo admin, tentou sair de um desafio que ainda tem outras pessoas. Sair deixaria o grupo "
        "sem ninguém para julgar denúncias. Se fosse o último membro, o grupo seria apagado.",
    "Para sair do grupo, use a opção de sair.":
        "Sendo admin, tentou expulsar a si mesmo pela lista de membros. O caminho existe, mas é "
        "outro botão, e ele avisa que o desafio pode ser apagado.",
    "Você já é o admin.":
        "Tentou transferir o cargo de admin para si mesmo.",
    "O desafio já começou e a entrada está fechada.":
        "Sendo admin, tentou gerar um link de convite para um desafio fora do período de inscrição. "
        "Depois que começa ninguém mais entra, porque teria menos dias para pontuar.",
    "Só o admin do grupo pode fazer isso.":
        "Tentou uma ação de administração sem ser admin. Costuma ser tela aberta antes de o cargo "
        "ser transferido.",
    "Revise os campos do grupo.":
        "Tentou criar ou editar um desafio com o formulário inválido: título vazio ou longo demais, "
        "período invertido, sem nenhuma regra marcada.",

    # ---- Notificações ----
    "Não consegui preparar as notificações neste aparelho.":
        "O app tentou registrar este aparelho para push mandando um identificador vazio. É falha do "
        "cliente, e a pessoa não vê nada: no máximo o push não chega.",

    # ---- Nome de exibição ----
    "Use pelo menos $MIN caracteres.":
        "Salvou o nome de exibição curto demais, no onboarding ou na tela de conta.",
    "Use no máximo $MAX caracteres.":
        "Salvou um nome de exibição longo demais. Costuma ser nome completo com sobrenomes.",

    # ---- Progresso e análise (fatia J) ----
    "Identificador de programa inválido.":
        "Pediu a análise de progressão ou o detalhe de um exercício com um `programId` que não é nem `avulsos` nem um UUID válido. Ver `CAUSAS` abaixo: os dois lugares que usam esta frase têm motivo um pouco diferente.",
    "Identificador de exercício inválido.":
        "Abriu o detalhe de um exercício dentro de um programa (`/me/progress/exercicio`, J.5) com `?exercicioId=` ausente ou que não é um UUID. Ao contrário da seleção de exercícios do `/me/progress` (que descarta id ruim em silêncio), aqui o id muda QUAL dado responde — sem ele não há tela.",

    # ---- Análise de progressão (J.2 e J.5): as mesmas situações de cima, com ponto final ----
    "Programa não encontrado.":
        "Abriu a análise de progressão ou o detalhe de um exercício filtrando por um programa que "
        "não existe mais ou que é de outra conta. O servidor não distingue os dois de propósito. "
        "Caso comum: apagou o programa em outro aparelho e este ainda tinha o filtro guardado.",
    "Exercício não encontrado.":
        "Abriu o detalhe de um exercício (`/me/progress/exercicio`, J.5) cujo id não está mais no "
        "catálogo. Acontece com histórico antigo que aponta para um exercício removido numa curadoria.",
    "Veja a evolução detalhada de cada exercício assinando o premium.":
        "No plano grátis, chegou ao detalhe de um exercício dentro de um programa. A tela inteira é "
        "paga, sem bloco grátis para misturar, por isso o servidor recusa com 403 em vez de "
        "devolver campo nulo. Quem chega aqui normalmente já viu o paywall na lista.",

    # ---- Genéricos do servidor ----
    "Recurso não encontrado":
        "Chamou um endereço que não existe na API. A pessoa não vê isto navegando pelo app: é "
        "versão de cliente incompatível ou requisição feita por fora.",
    "Erro interno":
        "Alguma coisa falhou no servidor. É deliberadamente vago: detalhe de falha interna fica no "
        "log, nunca na tela. **A pessoa não vê esta frase**: o cliente ignora a mensagem em 500.",
}
