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
    (SELECT id FROM wiki_articles WHERE slug NOT IN ('agua-no-dia-a-dia', 'antes-e-depois-do-treino', 'guia-do-iniciante', 'mitos-alimentacao', 'proteina-essencial'));

INSERT INTO wiki_article_translations (article_id, locale, title, body)
SELECT id, 'en', 'Beginner''s guide to the gym', 'To start well at the gym you only need three things: **train a few times a week, learn the basic movements calmly and keep showing up**. There is no perfect workout on day one. What works is turning up, making few mistakes and coming back next week.

## Before your first day

If you have been away from training for a long time or have any health condition, talk to a doctor before you start. It is quick and it avoids surprises.

Bring the basics:

- comfortable clothes and closed shoes
- a water bottle
- a small towel
- a phone with the app, to log your workout

If you can, visit the gym beforehand and ask for an assessment with a professional. Many gyms offer this, and it is the best way to leave with a plan that matches your level.

## How much to train, according to the people who set the guidance

Brazil''s Physical Activity Guidelines, published by the Ministry of Health, recommend **150 to 300 minutes of moderate physical activity per week** for adults, or **75 to 150 minutes of vigorous activity**. For muscle strengthening, which is what you do with weights, the World Health Organization recommends **at least two days a week, working the major muscle groups**.

Notice that the floor is lower than most people imagine. **Two or three sessions a week already put you inside the recommendation**, and that is exactly why starting slowly is not starting wrong.

## What your first weeks look like

Start with **2 to 3 sessions a week**, with a rest day in between. Training every day at the start only wears you out and raises the odds of quitting or getting hurt.

Each session can last 40 to 60 minutes and follow this order:

- **Warm-up:** 5 to 10 minutes of easy walking or cycling, plus a few light repetitions of the day''s exercises
- **Main part:** 5 to 7 exercises, covering the whole body
- **Finish:** light stretching if you enjoy it, with no rush

Early on, training the whole body each day is a good strategy. You practise the movements more often and learn faster.

## The movements worth learning first

Almost every workout is made of variations of a few movements. If you understand these, the rest gets easier:

- **Squat:** sit down and stand up under control, as if using a chair
- **Push:** bench press, push-up or shoulder press
- **Pull:** row or lat pulldown
- **Hinge at the hip:** deadlift with a light load, or the leg curl
- **Carry or brace the trunk:** plank and loaded carries

In the first weeks, the goal is **to learn the execution**, not to lift a lot of weight.

## How to choose the load

Pick a weight that leaves you finishing the set feeling you could do 2 or 3 more repetitions. If the last repetition comes out with your body shaking or the movement twisting, the weight is too heavy.

When every set feels easy for two weeks in a row, add a little load. Small increases, week after week, add up to a lot.

## Muscle soreness and pain that needs attention

It is normal for a muscle to feel sore for a day or two after your first sessions. That usually fades as your body adapts.

Sharp pain, the kind that shows up during the exercise, in a joint, or feels like a sting, is a sign to **stop the movement**. If it persists, see a health professional.

## Eating and sleeping are part of it too

You do not need a complicated diet to begin. Three simple adjustments already help:

- eat something light before training, if you feel hungry
- include a protein source in your main meals
- drink water through the day

Sleep matters as much as the workout, because that is when your body recovers.

## Common mistakes when starting out

- going too hard at first and quitting in week three
- comparing your workout with someone who has trained for years
- skipping the warm-up
- adding load too fast
- changing your workout every week, with no time to learn it

## What to expect

In the first weeks, the most visible gain is in energy, sleep and strength on each exercise. Changes in the body take longer and vary from person to person. Track your progress by the loads and the repetitions you manage, not only by the mirror.

## Where this comes from

The volume recommendations come from Brazil''s Physical Activity Guidelines (Ministry of Health, 2021) and from the World Health Organization''s physical activity recommendations, which the Brazilian guide follows.

This content is informational and does not replace guidance from a physical education or health professional.' FROM wiki_articles WHERE slug = 'guia-do-iniciante'
ON CONFLICT (article_id, locale) DO UPDATE SET
    title = EXCLUDED.title, body = EXCLUDED.body;

INSERT INTO wiki_article_translations (article_id, locale, title, body)
SELECT id, 'en', 'Protein: the essentials', 'Protein is the nutrient your body uses to **build and repair muscle**, and people who train need more of it than people who do not. The good news is that real food usually covers it, with no supplement and no complicated maths.

## How much you need per day

The International Society of Sports Nutrition recommends, for people who train, **1.4 to 2.0 grams of protein per kilo of body weight per day**. For a 70 kg person that is somewhere between 98 and 140 grams across the whole day.

If that number feels far from what you eat today, start simple: making sure there is a protein source in every main meal already gets you close.

## How to spread it across meals

Putting it all in dinner makes less of it count. The recommendation is to split it through the day, **every 3 to 4 hours**, with roughly **20 to 40 grams per meal**, which works out at 0.25 gram per kilo of body weight.

In practice that means a protein source at breakfast, at lunch and at dinner, plus one in a snack if it fits your routine.

## Good everyday sources

**Animal based:**

- eggs
- chicken, beef, fish and lean pork
- milk, yoghurt and cheese

**Plant based:**

- beans, lentils, chickpeas and peas
- tofu and tempeh
- nuts and seeds, in smaller amounts

People on a vegetarian or vegan diet get there by combining varied sources through the day, such as rice with beans, tofu and legumes. It takes a bit more planning, and it is where a dietitian helps most.

## What about supplements?

Whey and similar products are a convenient way to top up the total, not a requirement. If your food already reaches the range above, the supplement adds nothing the food did not. If you struggle to get there, it solves a real practical problem.

## Common mistakes

- believing only the supplement "gets results"
- eating plenty of protein and almost no other food group
- leaving it all for dinner
- forgetting that training and rest also make muscle grow

## One important caution

Anyone with kidney disease or another health condition should talk to a doctor or dietitian before raising the protein in their diet.

## Where this comes from

The numbers come from the protein and exercise position stand of the International Society of Sports Nutrition (ISSN, 2017), which reviews the available literature on the subject.

This content is informational and does not replace guidance from a dietitian or another health professional.' FROM wiki_articles WHERE slug = 'proteina-essencial'
ON CONFLICT (article_id, locale) DO UPDATE SET
    title = EXCLUDED.title, body = EXCLUDED.body;

INSERT INTO wiki_article_translations (article_id, locale, title, body)
SELECT id, 'en', 'What to eat before and after training', 'Before training, it is worth eating something **light and easy to digest**, with carbohydrate. Afterwards, have a normal meal with **protein and carbohydrate**. No magic formula and no rush are needed: what matters most is what you eat across the whole day.

## Before the session

The goal is to have energy without a heavy stomach. In general, eating between one and three hours beforehand works well. The closer to the session, the lighter the meal should be.

Good options:

- banana with oats
- bread with egg or cheese
- yoghurt with fruit
- rice with chicken, if there is time to digest it

If the session is very close, a simple piece of fruit already helps. If you train early in the morning and feel fine without eating, that is fine too. Every body reacts differently, so it is worth testing and paying attention.

## What to avoid beforehand

- very fatty or very high fibre meals right before training
- fried food and heavy dishes
- training very hungry on an empty stomach, if that makes you dizzy or weak

## After the session

After training, your body makes good use of a meal with protein, for recovery, and carbohydrate, to restore energy. It can be your normal lunch or dinner; it does not have to be anything special.

The International Society of Sports Nutrition suggests **20 to 40 grams of protein per meal**, which works out at 0.25 gram per kilo of body weight. An ordinary plate with meat, egg or chicken usually lands in that range without you measuring anything.

Simple examples:

- rice, beans, meat or egg and salad
- a sandwich with chicken or tuna
- yoghurt with fruit and oats
- an omelette with bread and a piece of fruit

## The 30 minute "window" is not what they said it was

This is the part where the science changed its mind and plenty of people never heard about it. The same ISSN review points out that **the effect of training on muscle building lasts at least 24 hours**, tapering off across that period.

In other words, there is no short countdown running after your last set. If you eat well over the next few hours, you are fine. That matters especially for people who train and cannot eat straight afterwards because of work or the commute.

## Do not forget the water

Drink water before, during and after training. If you feel very thirsty, get a headache or feel dizzy, ease off and rest.

## Where this comes from

The numbers and the 24 hour window come from the protein and exercise position stand of the International Society of Sports Nutrition (ISSN, 2017).

This content is informational and does not replace guidance from a dietitian or another health professional.' FROM wiki_articles WHERE slug = 'antes-e-depois-do-treino'
ON CONFLICT (article_id, locale) DO UPDATE SET
    title = EXCLUDED.title, body = EXCLUDED.body;

INSERT INTO wiki_article_translations (article_id, locale, title, body)
SELECT id, 'en', 'How much water to drink', 'There is no single number that works for everyone. The right amount depends on your weight, the climate, how much you sweat and the kind of training you do, but the practical rule is: **drink water through the day, before you get very thirsty, and adjust by the colour of your urine**.

## Why water matters in training

Water helps regulate body temperature, carry nutrients and keep muscles working. When you lose fluid through sweat and do not replace it, performance drops and the session feels heavier than it should.

## How to tell whether you are drinking enough

Use simple signals from your own body:

- **Pale urine**, the colour of light straw, is a good sign
- **Dark urine** with a strong smell means fluid is missing
- **Frequent thirst**, a dry mouth and headaches are warnings too
- **Unusual tiredness** during training can be related to low hydration

## Before and during the session

The American College of Sports Medicine recommends **arriving at exercise already hydrated**, drinking in the hours leading up to the session rather than trying to make it all up on the spot. During the effort, the guidance is to **start drinking early and at regular intervals**, without waiting for thirst to bite, since thirst shows up after fluid loss has already begun.

In practice, for an ordinary gym session that means a bottle within reach and sips between sets.

## Do you need a sports drink?

For most sessions of someone starting out, **water is enough**. Drinks with carbohydrate and sodium exist for a specific scenario: the same ACSM document covers them for efforts that **run beyond one hour**, when replacing energy and electrolytes alongside fluid starts to make a difference. A 45 to 60 minute weights session is not that scenario.

## Careful with overdoing it, and it has a name

Drinking far beyond what you need is also a problem. When a large volume of fluid without electrolytes goes in while the body is already losing sodium through sweat, blood sodium can fall far enough to cause **hyponatraemia**, which the ACSM associates with disorientation, mental confusion and, in severe cases, seizures.

This is rare in gym training and shows up more in long events, but it undoes the idea that "the more water the better". The target is to replace what you lose, not to fill up.

Anyone with heart disease, kidney disease or any health condition should follow their own doctor''s guidance on fluid, which may be quite different from the general recommendation.

## Tips for building the habit

- keep a bottle nearby, at work and at the gym
- drink a glass when you wake up
- attach the habit to routines, such as drinking a little at every meal
- use your phone as a reminder, if that helps
- flavour it with lemon or mint if plain water feels dull

## Where this comes from

The guidance on drinking before and during effort, the one hour threshold for sports drinks and the hyponatraemia warning come from the position stand on exercise and fluid replacement of the American College of Sports Medicine (ACSM). The urine and thirst signals are in common practical use and serve as an everyday reference, not as a test.

This content is informational and does not replace guidance from a dietitian or another health professional.' FROM wiki_articles WHERE slug = 'agua-no-dia-a-dia'
ON CONFLICT (article_id, locale) DO UPDATE SET
    title = EXCLUDED.title, body = EXCLUDED.body;

INSERT INTO wiki_article_translations (article_id, locale, title, body)
SELECT id, 'en', 'Nutrition myths that hold beginners back', 'Plenty of people give up or get frustrated early because they follow rules that **have no solid basis**. The good news is that simple, varied and consistent eating usually works better than any trendy rule.

## Myth 1: carbohydrate makes you fat

Carbohydrate is the main source of energy for training and it is not the villain. What leads to weight gain is consistently taking in more energy than the body spends, and that can happen with any food. Rice, beans, fruit, bread and oats all have a place in a balanced diet.

## Myth 2: you have to eat every 3 hours to speed up your metabolism

Two things get mixed up here, and it is worth separating them.

**The myth** is believing that meal frequency, on its own, speeds up metabolism or causes weight loss. It does not work that way: for body weight, what counts is the day''s total, and the best meal pattern is the one you can keep up.

**What does have a basis** is spreading **protein** across the day. The International Society of Sports Nutrition recommends portions every 3 to 4 hours for people who train, and that is about building muscle, not about metabolism or weight loss. Different recommendations, different goals.

## Myth 3: sweating a lot means burning fat

Sweat is how the body cools itself and it is mostly water. Sweating more does not mean you lost more fat, and the weight that disappears in a sauna or under extra clothing comes back once you rehydrate.

## Myth 4: you have to take whey to grow

A supplement is a practical top-up, not a requirement. The ISSN itself treats supplements as a convenient way to reach the daily protein total, not as something food cannot provide. Anyone already eating enough protein from ordinary food gains nothing extra from the tub.

## Myth 5: muscle turns into fat when you stop training

Muscle and fat are different tissues and one does not turn into the other. When someone stops training and keeps eating the same way, they can lose muscle mass and gain fat, which gives the impression that one became the other.

## Myth 6: eating at night makes you fat

The clock, on its own, is not what causes weight gain. What counts is the whole day. Even so, very heavy meals close to bedtime can disturb sleep for some people, and sleep is an important part of progress.

## Myth 7: some foods are forbidden

No single food ruins or rescues your diet. Sweets, pizza and barbecue can show up now and then without guilt. Banning everything tends to lead to blowouts later, and it is the day-to-day balance that counts.

## Myth 8: you have to eat right after training or the session was wasted

The idea of a short window after your last set does not hold up. According to the ISSN review, the effect of training on muscle building **lasts at least 24 hours**. Eating well over the following hours sorts it out.

## How to avoid falling for myths

- be suspicious of fast promises and miracle formulas
- prefer sources with health professionals and a scientific basis
- remember that the same method does not suit everyone
- talk to a dietitian when you need individual guidance

## Where this comes from

The claims about protein, supplements and the 24 hour window come from the protein and exercise position stand of the International Society of Sports Nutrition (ISSN, 2017).

This content is informational and does not replace guidance from a dietitian or another health professional.' FROM wiki_articles WHERE slug = 'mitos-alimentacao'
ON CONFLICT (article_id, locale) DO UPDATE SET
    title = EXCLUDED.title, body = EXCLUDED.body;

END
$wiki$;
