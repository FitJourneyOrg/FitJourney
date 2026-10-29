# Notificações de grupo — os seis gatilhos (fatia F)

> Índice: `docs/regras/README.md` · Marcadores: [REGRA] imutável · [INV] invariante · [PROPOSTA] lacuna não ratificada.

### Notificação — os seis gatilhos (10.4 + 10.6, fatia F)

- **[REGRA]** Comentário avisa **só o dono do check-in**, e **nunca ele mesmo**. Sem "seguir a thread": dez pessoas comentando gerariam 55 avisos, e é a mesma aritmética que matou a votação.
- **[REGRA]** A denúncia avisa o denunciado **sem dizer quem denunciou nem o motivo**. O anonimato aqui é mais crítico que na fila: quem sabe por quem foi denunciado tem oportunidade de retaliar dentro do grupo. E o motivo foi escrito para o admin julgar — repassá-lo faria a denúncia virar canal de mensagem anônima.
- **[REGRA]** O admin é avisado **uma vez por CASO**, não por denúncia (6.11 no push). A quinta pessoa denunciando o mesmo alvo não gera aviso novo: ele já sabe que há algo ali, e a fila mostra um item só.
- **[REGRA]** **Manter não avisa ninguém.** O denunciado já soube quando a denúncia chegou; um segundo push dizendo "não deu em nada" ensina que metade dos avisos não pede ação.
- **[REGRA]** Invalidar pelo julgamento e invalidar direto (6.10) mandam o **mesmo** aviso. Quem perde o ponto não precisa saber por qual rota o admin passou — e o texto por isso **não menciona denúncia**, que só existe num dos caminhos.
- **[INV]** As entradas do dia são **agregadas**: uma por grupo por dia, garantida pela PK `(grupo, dia, tipo)` da V46. O teto **não depende do tamanho do grupo** — uma por entrada daria 1.225 avisos num desafio de 50.
- **[REGRA]** O **criador não conta** como entrada, e só ele lê *"no **seu** desafio"*. Os dois por `created_by`, nunca por papel: o cargo é transferível (2.12), o fundador não muda.
- **[INV]** O laço acorda **de hora em hora**, não uma vez por dia: o dia civil de cada grupo vira num instante diferente conforme o fuso. Quem garante o "uma vez por dia" é a chave do banco, não o intervalo.
- **[INV]** Estado do grupo (`AGENDADO`/`ATIVO`/`ENCERRADO`) é **derivado** de `(inicio, fim, agora, fuso_do_grupo)` — sem coluna `status`, sem job de virada. Mesma técnica do `currentWeek` (#22) e do XP (#16).
- **[INV]** `EMOJI_DO_DIA` implica `FOTO` — reproduzir um emoji exige onde mostrá-lo; sem a amarração dá para configurar um grupo impossível de cumprir.
- **[REGRA]** O emoji do dia é **derivado** de `(groupId, dia no fuso do grupo)`, nunca sorteado a cada leitura (fatia D). Precisa ser **o mesmo para todo o grupo naquele dia** e **imprevisível de véspera**; um sorteio de verdade daria as duas pessoas emojis diferentes e mudaria a cada recarga.
- **[INV]** O emoji é **GRAVADO** em `check_ins.emoji` na criação. A lista curada cresce, e recalcular na leitura faria o check-in de ontem exibir um emoji que ninguém imitou — o mesmo motivo de `title`/`body` da notificação virem renderizados (#36).
- **[REGRA]** Quem resolve o emoji é o **servidor**. Aceitá-lo do cliente seria deixá-lo escolher qual regra cumpriu, e o dia depende do fuso do GRUPO — um aparelho adiantado mandaria a pessoa fazer o gesto de amanhã.
- **[INV]** O sorteio sai **sempre da lista curada**, e a lista exclui: emoji de duas mãos (selfie se tira com uma), Unicode 14/15 (caixa vazia antes do Android 14), gestos que a câmera frontal espelha, e qualquer coisa ofensiva.
- **[INV]** Localização é **NOME escolhido pelo usuário** (sugerido pelo `Geocoder`), nunca coordenada na tela. A coordenada fica gravada arredondada a 2 casas (~1 km) e não é exibida.
- **[INV]** Todo usuário tem `display_name` (`users`, NOT NULL). O **e-mail nunca** atravessa a fronteira do grupo.

> O produto deixou de ser *verificação social* e passou a ser *confiança com correção*. O que se perde: foto e emoji não são prova, são conteúdo social. **Se a fraude incomodar, o caminho de volta não é a votação** (que não escala) — é tornar a denúncia mais fácil e visível.
