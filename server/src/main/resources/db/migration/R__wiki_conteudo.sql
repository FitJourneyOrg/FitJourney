-- GERADO por conteudo/gen_wiki.py. NAO EDITE A MAO -- edite os .md e rode o gerador.
-- Migration REPETIVEL: o Flyway reaplica sozinho quando este arquivo muda.
--
-- ====================================================================
-- POR QUE O BLOCO `DO` COM GUARDA DE EXISTENCIA
-- ====================================================================
--
-- Migration repetivel roda em TODO migrate -- inclusive num migrate PARCIAL, com `target`
-- anterior a V61, que e quando a tabela deste seed ainda nem existe. O
-- `DisplayNameBackfillIntegrationTest` faz exatamente isso de proposito: migra ate a V34,
-- insere usuarios como eram antes da coluna, e so entao roda a V35 pra exercitar o backfill.
-- Sem a guarda, o seed estourava ali com `relation "wiki_articles" does not exist` e
-- derrubava a suite inteira (achado em 2026-09-30, no primeiro artigo real).
--
-- > Migration repetivel nao pode assumir que o schema dela ja nasceu.
--
-- O PL/pgSQL planeja cada comando na PRIMEIRA execucao, entao o RETURN antecipado impede
-- que os comandos abaixo sejam sequer planejados quando a tabela nao existe.

DO $wiki$
BEGIN
IF to_regclass('public.wiki_articles') IS NULL THEN
    RAISE NOTICE 'wiki_articles ainda nao existe (migrate parcial, anterior a V61): seed do acervo pulado';
    RETURN;
END IF;

-- Artigo apagado do conteudo tem de sumir do banco, senao vira fantasma no acervo.
DELETE FROM wiki_articles WHERE slug NOT IN ('agua-no-dia-a-dia', 'antes-e-depois-do-treino', 'guia-do-iniciante', 'mitos-alimentacao', 'proteina-essencial');

-- Limpa o destaque antes de reatribuir: o indice unico parcial da V61 recusaria dois
-- durante a troca de um artigo para outro.
UPDATE wiki_articles SET featured = FALSE;

INSERT INTO wiki_articles (id, slug, category, title, body, featured, order_index, updated_at)
VALUES (gen_random_uuid(), 'guia-do-iniciante', 'TRAINING', 'Guia do iniciante na academia', 'Para começar bem na academia, você só precisa de três coisas: **treinar poucas vezes por semana, aprender os movimentos básicos com calma e manter a constância**. Não existe treino perfeito no primeiro dia. O que funciona é aparecer, errar pouco e voltar na próxima semana.

## Antes do primeiro dia

Se você está sem treinar há muito tempo ou tem alguma condição de saúde, converse com um médico antes de começar. É rápido e evita surpresas.

Leve o básico:

- roupa confortável e tênis fechado
- garrafa de água
- uma toalha pequena
- um celular com o app para anotar o treino

Se puder, vá conhecer a academia antes e peça uma avaliação com um profissional. Muitas academias oferecem isso, e é a melhor forma de sair com uma ficha adequada ao seu nível.

## Quanto treinar, segundo quem define a recomendação

O Guia de Atividade Física para a População Brasileira, do Ministério da Saúde, recomenda para adultos **entre 150 e 300 minutos por semana de atividade física moderada**, ou **entre 75 e 150 minutos de atividade vigorosa**. Para o fortalecimento muscular, que é o que você faz na musculação, a recomendação da Organização Mundial da Saúde é de **pelo menos dois dias por semana, envolvendo os grandes grupos musculares**.

Repare que o piso é mais baixo do que a maioria das pessoas imagina. **Dois ou três treinos por semana já colocam você dentro da recomendação**, e é exatamente por isso que começar devagar não é começar errado.

## Como ficam suas primeiras semanas

Comece com **2 a 3 treinos por semana**, com um dia de descanso entre eles. Treinar todo dia no começo só cansa e aumenta o risco de desistir ou de se machucar.

Cada treino pode durar entre 40 e 60 minutos e seguir esta ordem:

- **Aquecimento:** 5 a 10 minutos de caminhada leve ou bicicleta, mais algumas repetições leves dos exercícios do dia
- **Parte principal:** de 5 a 7 exercícios, cobrindo o corpo todo
- **Final:** alongamento leve se você gostar, sem pressa

No começo, treinar o corpo inteiro em cada dia é uma boa estratégia. Você pratica os movimentos com mais frequência e aprende mais rápido.

## Os movimentos que valem aprender primeiro

Quase todo treino é feito de variações de poucos movimentos. Se você entende estes, o resto fica mais fácil:

- **Agachar:** sentar e levantar com controle, como numa cadeira
- **Empurrar:** supino, flexão ou desenvolvimento de ombros
- **Puxar:** remada ou puxada na frente
- **Dobrar o quadril:** levantamento terra com carga leve ou a mesa flexora
- **Carregar ou firmar o tronco:** prancha e caminhada com peso

Nas primeiras semanas, o objetivo é **aprender a executar**, não levantar muito peso.

## Como escolher a carga

Escolha um peso com o qual você termine a série sentindo que conseguiria fazer mais 2 ou 3 repetições. Se a última repetição sai com o corpo tremendo ou com o movimento torto, o peso está alto demais.

Quando todas as séries ficarem fáceis por duas semanas seguidas, aumente um pouco a carga. Pequenos aumentos, semana após semana, somam muito.

## Dor muscular e dor que exige atenção

É normal sentir o músculo dolorido por um ou dois dias depois dos primeiros treinos. Isso costuma diminuir conforme o corpo se adapta.

Já a dor aguda, que aparece durante o exercício, em uma articulação ou com sensação de fisgada, é sinal para **parar o movimento**. Se ela persistir, procure um profissional de saúde.

## Comer e dormir também fazem parte

Você não precisa de dieta complicada para começar. Três ajustes simples já ajudam:

- comer algo leve antes de treinar, se sentir fome
- incluir uma fonte de proteína nas refeições principais
- beber água ao longo do dia

O sono importa tanto quanto o treino, porque é quando o corpo se recupera.

## Erros comuns de quem está começando

- começar forte demais e abandonar na terceira semana
- comparar seu treino com o de quem treina há anos
- pular o aquecimento
- aumentar a carga rápido demais
- trocar de treino toda semana, sem dar tempo de aprender

## O que esperar

Nas primeiras semanas, o ganho mais visível é na disposição, no sono e na força de cada exercício. Mudanças no corpo levam mais tempo e variam de pessoa para pessoa. Acompanhe seu progresso pelas cargas e pelas repetições que você consegue fazer, e não só pelo espelho.

## De onde vem

As recomendações de quantidade vêm do Guia de Atividade Física para a População Brasileira (Ministério da Saúde, 2021) e das recomendações de atividade física da Organização Mundial da Saúde, que o guia brasileiro segue.

Este conteúdo é informativo e não substitui a orientação de um profissional de educação física ou de saúde.', TRUE, 1, TIMESTAMP '2026-09-30 00:00:00')
ON CONFLICT (slug) DO UPDATE SET
    category = EXCLUDED.category, title = EXCLUDED.title, body = EXCLUDED.body,
    featured = EXCLUDED.featured, order_index = EXCLUDED.order_index,
    updated_at = EXCLUDED.updated_at;

INSERT INTO wiki_articles (id, slug, category, title, body, featured, order_index, updated_at)
VALUES (gen_random_uuid(), 'proteina-essencial', 'NUTRITION', 'Proteína: o essencial', 'Proteína é o nutriente que o corpo usa para **construir e reparar músculos**, e quem treina precisa de mais dela do que quem não treina. A boa notícia é que comida de verdade costuma dar conta, sem suplemento e sem conta complicada.

## Quanto você precisa por dia

A Sociedade Internacional de Nutrição Esportiva recomenda, para quem treina, **entre 1,4 e 2,0 gramas de proteína por quilo de peso por dia**. Para uma pessoa de 70 kg, isso dá algo entre 98 e 140 gramas ao longo do dia inteiro.

Se esse número parecer distante do que você come hoje, comece pelo simples: garantir uma fonte de proteína em cada refeição principal já aproxima muito.

## Como distribuir nas refeições

Concentrar tudo no jantar aproveita menos. A recomendação é dividir ao longo do dia, **a cada 3 ou 4 horas**, com cerca de **20 a 40 gramas por refeição**, o equivalente a 0,25 grama por quilo de peso.

Na prática, isso é uma fonte de proteína no café, no almoço e no jantar, e mais uma num lanche se couber na sua rotina.

## Boas fontes no dia a dia

**De origem animal:**

- ovos
- frango, carne bovina, peixe e carne suína magra
- leite, iogurte e queijos

**De origem vegetal:**

- feijão, lentilha, grão de bico e ervilha
- tofu e tempeh
- castanhas e sementes, em menor quantidade

Quem segue alimentação vegetariana ou vegana chega lá combinando fontes variadas ao longo do dia, como arroz com feijão, tofu e leguminosas. Exige um pouco mais de planejamento, e é onde um nutricionista ajuda mais.

## E o suplemento?

Whey e similares são uma forma prática de completar a conta, não uma exigência. Se a sua alimentação já alcança a faixa acima, o suplemento não adiciona nada que a comida não tenha dado. Se você tem dificuldade de chegar lá, ele resolve um problema real de praticidade.

## Erros comuns

- achar que só o suplemento "dá resultado"
- comer muita proteína e quase nenhum outro grupo de alimento
- deixar tudo para o jantar
- esquecer que treino e descanso também fazem o músculo crescer

## Um cuidado importante

Quem tem doença nos rins ou outra condição de saúde deve conversar com médico ou nutricionista antes de aumentar a proteína da alimentação.

## De onde vem

Os números vêm do position stand de proteína e exercício da Sociedade Internacional de Nutrição Esportiva (ISSN, 2017), que revisa a literatura disponível sobre o tema.

Este conteúdo é informativo e não substitui a orientação de um nutricionista ou de outro profissional de saúde.', FALSE, 2, TIMESTAMP '2026-09-30 00:00:00')
ON CONFLICT (slug) DO UPDATE SET
    category = EXCLUDED.category, title = EXCLUDED.title, body = EXCLUDED.body,
    featured = EXCLUDED.featured, order_index = EXCLUDED.order_index,
    updated_at = EXCLUDED.updated_at;

INSERT INTO wiki_articles (id, slug, category, title, body, featured, order_index, updated_at)
VALUES (gen_random_uuid(), 'antes-e-depois-do-treino', 'NUTRITION', 'O que comer antes e depois do treino', 'Antes de treinar, vale comer algo **leve e fácil de digerir**, com carboidrato. Depois, faça uma refeição normal com **proteína e carboidrato**. Não precisa de fórmula mágica nem de pressa: o que mais importa é o que você come ao longo do dia todo.

## Antes do treino

O objetivo é ter energia sem sentir o estômago pesado. Em geral, comer entre uma e três horas antes funciona bem. Quanto mais perto do treino, mais leve deve ser a refeição.

Boas opções:

- banana com aveia
- pão com ovo ou queijo
- iogurte com fruta
- arroz com frango, se houver tempo para digerir

Se o treino está muito perto, uma fruta simples já ajuda. Se você treina de manhã bem cedo e se sente bem sem comer, tudo bem também. Cada corpo reage de um jeito, então vale testar e observar.

## O que evitar antes

- refeições muito gordurosas ou com muita fibra logo antes de treinar
- frituras e comidas muito pesadas
- treinar com muita fome e sem nada no estômago, se isso causar tontura ou fraqueza

## Depois do treino

Depois de treinar, o corpo aproveita bem uma refeição com proteína, para recuperação, e carboidrato, para repor a energia. Pode ser o seu almoço ou jantar normal, não precisa ser algo especial.

A Sociedade Internacional de Nutrição Esportiva sugere **entre 20 e 40 gramas de proteína por refeição**, o equivalente a 0,25 grama por quilo de peso. Um prato comum com carne, ovo ou frango costuma ficar nessa faixa sem que você precise medir nada.

Exemplos simples:

- arroz, feijão, carne ou ovo e salada
- sanduíche com frango ou atum
- iogurte com fruta e aveia
- omelete com pão e uma fruta

## A "janela" de 30 minutos não existe como diziam

Essa é a parte em que a ciência mudou de ideia e muita gente não ficou sabendo. A mesma revisão da ISSN aponta que **o efeito do treino sobre a construção muscular dura pelo menos 24 horas**, e vai diminuindo ao longo desse período.

Ou seja: não existe um cronômetro de poucos minutos correndo depois da última série. Se você comer bem nas próximas horas, está ótimo. Isso vale especialmente para quem treina e não consegue comer logo em seguida por causa do trabalho ou do trânsito.

## Não esqueça da água

Beba água antes, durante e depois do treino. Se você sentir muita sede, dor de cabeça ou tontura, reduza o ritmo e descanse.

## De onde vem

Os números e o prazo de 24 horas vêm do position stand de proteína e exercício da Sociedade Internacional de Nutrição Esportiva (ISSN, 2017).

Este conteúdo é informativo e não substitui a orientação de um nutricionista ou de outro profissional de saúde.', FALSE, 3, TIMESTAMP '2026-09-30 00:00:00')
ON CONFLICT (slug) DO UPDATE SET
    category = EXCLUDED.category, title = EXCLUDED.title, body = EXCLUDED.body,
    featured = EXCLUDED.featured, order_index = EXCLUDED.order_index,
    updated_at = EXCLUDED.updated_at;

INSERT INTO wiki_articles (id, slug, category, title, body, featured, order_index, updated_at)
VALUES (gen_random_uuid(), 'agua-no-dia-a-dia', 'NUTRITION', 'Quanta água beber', 'Não existe um número único que valha para todo mundo. A quantidade certa depende do seu peso, do clima, do quanto você sua e do tipo de treino, mas a regra prática é: **beba água ao longo do dia, antes de sentir muita sede, e ajuste conforme a cor da sua urina**.

## Por que a água importa no treino

A água ajuda na regulação da temperatura do corpo, no transporte de nutrientes e no funcionamento dos músculos. Quando você perde líquido pelo suor e não repõe, o rendimento cai e o treino parece mais pesado do que deveria.

## Como saber se está bebendo o suficiente

Use sinais simples do próprio corpo:

- **Urina clara**, cor de palha clara, é um bom sinal
- **Urina escura** e com cheiro forte indica que falta líquido
- **Sede frequente**, boca seca e dor de cabeça também são avisos
- **Cansaço fora do normal** durante o treino pode ter a ver com pouca hidratação

## Antes e durante o treino

O Colégio Americano de Medicina do Esporte recomenda **chegar ao exercício já hidratado**, bebendo líquido nas horas que antecedem o treino em vez de tentar compensar tudo na hora. Durante o esforço, a orientação é **começar a beber cedo e em intervalos regulares**, sem esperar a sede apertar, já que a sede aparece depois que a perda de líquido começou.

Na prática, para um treino comum de academia isso é uma garrafa por perto e goles entre as séries.

## Precisa de bebida esportiva?

Para a maioria dos treinos de quem está começando, **água é suficiente**. As bebidas com carboidrato e sódio existem para um cenário específico: o mesmo documento do ACSM trata delas para esforços que **passam de uma hora**, quando repor energia e eletrólitos junto com o líquido passa a fazer diferença. Um treino de musculação de 45 a 60 minutos não é esse cenário.

## Cuidado com o exagero, e ele tem nome

Beber muito além do necessário também é problema. Quando entra um grande volume de líquido sem eletrólitos e o corpo já está perdendo sódio pelo suor, o sódio do sangue pode cair a ponto de causar **hiponatremia**, quadro que o ACSM associa a desorientação, confusão mental e, nos casos graves, convulsões.

Isso é raro em treino de academia e aparece mais em provas longas, mas serve para desfazer a ideia de que "quanto mais água, melhor". O alvo é repor o que se perde, não encher.

Quem tem doença no coração, nos rins ou qualquer condição de saúde deve seguir a orientação do próprio médico sobre quantidade de líquido, que pode ser bem diferente da recomendação geral.

## Dicas para criar o hábito

- mantenha uma garrafa sempre por perto, no trabalho e na academia
- beba um copo ao acordar
- associe o hábito a rotinas, como beber um pouco a cada refeição
- use o celular para lembrar, se ajudar
- aromatize com limão ou hortelã se você acha água pura sem graça

## De onde vem

As orientações sobre beber antes e durante o esforço, o limiar de uma hora para bebidas esportivas e o alerta de hiponatremia vêm do position stand sobre exercício e reposição hídrica do Colégio Americano de Medicina do Esporte (ACSM). Os sinais de urina e sede são de uso prático corrente e servem como referência do dia a dia, não como exame.

Este conteúdo é informativo e não substitui a orientação de um nutricionista ou de outro profissional de saúde.', FALSE, 4, TIMESTAMP '2026-09-30 00:00:00')
ON CONFLICT (slug) DO UPDATE SET
    category = EXCLUDED.category, title = EXCLUDED.title, body = EXCLUDED.body,
    featured = EXCLUDED.featured, order_index = EXCLUDED.order_index,
    updated_at = EXCLUDED.updated_at;

INSERT INTO wiki_articles (id, slug, category, title, body, featured, order_index, updated_at)
VALUES (gen_random_uuid(), 'mitos-alimentacao', 'NUTRITION', 'Mitos de alimentação que atrapalham o iniciante', 'Muita gente desiste ou se frustra no começo por seguir regras que **não têm base sólida**. A boa notícia é que uma alimentação simples, variada e constante costuma funcionar melhor do que qualquer regra da moda.

## Mito 1: carboidrato engorda

O carboidrato é a principal fonte de energia para o treino e não é vilão. O que leva ao ganho de peso é comer mais energia do que o corpo gasta de forma contínua, e isso pode acontecer com qualquer alimento. Arroz, feijão, frutas, pão e aveia têm espaço numa alimentação equilibrada.

## Mito 2: é preciso comer de 3 em 3 horas para acelerar o metabolismo

Aqui vale separar duas coisas que costumam ser confundidas.

**O mito** é achar que a frequência das refeições, por si só, acelera o metabolismo ou faz emagrecer. Não é assim: para o peso, o que pesa é o total do dia, e o melhor esquema de refeições é o que você consegue manter.

**O que tem base** é distribuir a **proteína** ao longo do dia. A Sociedade Internacional de Nutrição Esportiva recomenda porções a cada 3 ou 4 horas para quem treina, e isso é sobre construção muscular, não sobre metabolismo ou emagrecimento. São recomendações diferentes, com objetivos diferentes.

## Mito 3: suar muito é queimar gordura

O suor é a forma de o corpo se resfriar e sai principalmente água. Suar mais não quer dizer que você perdeu mais gordura, e o peso que some numa sauna ou com muita roupa volta quando você se hidrata.

## Mito 4: tem que tomar whey para crescer

O suplemento é um complemento prático, não uma exigência. A própria ISSN trata o suplemento como uma forma conveniente de atingir a quantidade diária de proteína, e não como algo que a comida não possa dar. Quem já come proteína suficiente em comida comum não ganha nada extra com o pote.

## Mito 5: o músculo vira gordura quando você para de treinar

Músculo e gordura são tecidos diferentes e um não se transforma no outro. Quando alguém para de treinar e mantém a mesma alimentação, pode perder massa muscular e ganhar gordura, e isso dá a impressão de que um virou o outro.

## Mito 6: comer à noite engorda

O horário, sozinho, não é o que faz engordar. O que conta é o conjunto do dia. Mesmo assim, refeições muito pesadas perto de dormir podem atrapalhar o sono para algumas pessoas, e o sono é parte importante do progresso.

## Mito 7: existe comida proibida

Nenhum alimento sozinho estraga ou salva sua alimentação. Doces, pizza e churrasco podem aparecer de vez em quando sem culpa. Proibir tudo costuma levar a exageros depois, e o equilíbrio do dia a dia é o que pesa.

## Mito 8: tem que comer logo depois do treino ou o treino foi perdido

A ideia de uma janela de poucos minutos depois da última série não se sustenta. Segundo a revisão da ISSN, o efeito do treino sobre a construção muscular **dura pelo menos 24 horas**. Comer bem nas horas seguintes resolve.

## Como evitar cair em mitos

- desconfie de promessas rápidas e de fórmulas milagrosas
- prefira fontes com profissionais de saúde e com base científica
- lembre que o mesmo método não serve para todo mundo
- converse com um nutricionista quando precisar de orientação individual

## De onde vem

As afirmações sobre proteína, suplemento e o prazo de 24 horas vêm do position stand de proteína e exercício da Sociedade Internacional de Nutrição Esportiva (ISSN, 2017).

Este conteúdo é informativo e não substitui a orientação de um nutricionista ou de outro profissional de saúde.', FALSE, 5, TIMESTAMP '2026-09-30 00:00:00')
ON CONFLICT (slug) DO UPDATE SET
    category = EXCLUDED.category, title = EXCLUDED.title, body = EXCLUDED.body,
    featured = EXCLUDED.featured, order_index = EXCLUDED.order_index,
    updated_at = EXCLUDED.updated_at;

-- Traducao que sumiu do conteudo tambem tem de sumir do banco.
DELETE FROM wiki_article_translations WHERE locale = 'en' AND article_id IN
    (SELECT id FROM wiki_articles WHERE slug NOT IN (''));

END
$wiki$;
