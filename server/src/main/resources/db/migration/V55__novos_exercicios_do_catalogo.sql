-- Fatia H, sessao B: os 23 exercicios do catalogo.json que nunca tiveram linha em `exercises`
-- (populacao exclusiva do catalogo, descoberta pela conferencia reversa da V53 -- nao sao
-- renomeacoes, sao exercicios que nunca foram semeados). Rafael revisou a lista com categoria
-- e confirmou: todo exercicio do catalogo com video_ref/thumb_ref fisico presente entra. Os 23
-- passaram na checagem (video e thumb confirmados em disco).
--
-- category usa o enum ExerciseCategory (shared-contract), nao a string em portugues do
-- catalogo.json -- mesmo mapeamento da V4/V28.
--
-- id gerado via gen_random_uuid() (a tabela `exercises` nao tem DEFAULT na coluna id).

INSERT INTO exercises (id, name, category, description, video_ref, thumb_ref) VALUES (gen_random_uuid(), 'Agachamento Apoiado na Cadeira Abdutora', 'GLUTES', 'O Agachamento Apoiado na Cadeira Abdutora é um exercício focado principalmente no fortalecimento dos músculos das coxas, glúteos e quadris.

Durante a execução do movimento, você se posiciona em uma máquina específica, com as costas apoiadas e os pés em contato com a base. Ao realizar o movimento de abertura das pernas, você ativa os músculos abdutores, promovendo um trabalho isolado que ajuda a melhorar a força e a definição dessa região.

Além disso, esse exercício contribui para o aumento da estabilidade na região do quadril, o que pode resultar em melhor desempenho em atividades físicas do dia a dia e em outros esportes. O Agachamento na Máquina Abdutora é ideal para quem deseja esculpir as pernas e os glúteos, proporcionando um shape mais tonificado e equilibrado.

Entretanto, é essencial realizar o exercício com a técnica correta e sob orientação de um profissional de educação física para evitar lesões e garantir um treino eficaz.', 'Glúteos/hip_abductor_machine_squat.mp4', 'Glúteos/hip_abductor_machine_squat.webp');
INSERT INTO exercise_translations (exercise_id, locale, name, description)
    SELECT id, 'en', 'Hip Abductor Machine Squat', 'The Hip Abductor Machine Squat is an exercise mainly focused on strengthening the thighs, glutes and hips.

Stand on the platform of the hip abductor machine, holding the frame for support, with the pads against the outside of your knees. Squat down and push your knees out against the pads, activating the hip abductors. This provides targeted work that helps improve strength and definition in this area.

This exercise also increases hip stability, which can lead to better performance in daily activities and sports. It is ideal for anyone who wants to sculpt the legs and glutes for a more toned, balanced physique.

However, it is essential to use correct technique and follow the guidance of a fitness professional to avoid injuries and ensure an effective workout.' FROM exercises WHERE name = 'Agachamento Apoiado na Cadeira Abdutora'
    ON CONFLICT (exercise_id, locale) DO NOTHING;

INSERT INTO exercises (id, name, category, description, video_ref, thumb_ref) VALUES (gen_random_uuid(), 'Bom Dia com Barra', 'LEGS', 'O exercício "Bom Dia" é uma excelente atividade destinada a fortalecer a cadeia posterior, incluindo músculos como os isquiotibiais, glúteos e a região lombar.

Ele geralmente é realizado em pé, com os pés na largura dos ombros e os joelhos levemente flexionados. Ao executar o movimento, você deve inclinar o tronco para frente, mantendo a coluna reta, até que esteja quase paralelo ao chão. É importante contrair os glúteos e os músculos do core para estabilizar o corpo, e então retornar à posição inicial.

Esse exercício não só aumenta a força muscular e a mobilidade, mas também pode melhorar a postura e prevenir lesões na região lombar quando realizado corretamente.

Recomenda-se sempre realizar o exercício sob a supervisão de um profissional de educação física, especialmente se você for iniciante ou tiver alguma condição física pré-existente.', 'Pernas/barbell_good_morning.mp4', 'Pernas/barbell_good_morning.webp');
INSERT INTO exercise_translations (exercise_id, locale, name, description)
    SELECT id, 'en', 'Barbell Good Morning', 'The Barbell Good Morning is an excellent exercise for strengthening the posterior chain, including the hamstrings, glutes and lower back.

It is performed standing, with the barbell across your upper back, your feet shoulder-width apart and your knees slightly bent. Hinge your torso forward, keeping your spine straight, until it is almost parallel to the floor. Engage your glutes and core to stabilize your body, then return to the starting position.

This exercise not only increases muscle strength and mobility but can also improve posture and help prevent lower-back injuries when performed correctly.

It is always recommended to perform it under the supervision of a fitness professional, especially if you are a beginner or have a pre-existing physical condition.' FROM exercises WHERE name = 'Bom Dia com Barra'
    ON CONFLICT (exercise_id, locale) DO NOTHING;

INSERT INTO exercises (id, name, category, description, video_ref, thumb_ref) VALUES (gen_random_uuid(), 'Coice de Glúteo na Máquina', 'GLUTES', 'Exercício: Glúteo Coice na Máquina

O Glúteo Coice na Máquina é um exercício isolado focado no fortalecimento e desenvolvimento dos músculos glúteos, em especial o glúteo máximo. Para realizar o exercício, o praticante se posiciona em uma máquina específica com suporte para o tronco e tornozelos. A execução consiste em estender a perna para trás, ativando a musculatura dos glúteos e, em menor escala, os músculos isquiotibiais.

O objetivo principal deste exercício é aumentar a força e a resistência dos glúteos, contribuindo também para a melhora da estabilidade do quadril, prevenção de lesões e um melhor desempenho em atividades físicas diárias e esportivas. Além disso, ele pode ajudar na definição muscular dessa região, promovendo um contorno mais estético.

É importante realizar o exercício com a técnica correta e ajustar a carga de acordo com seu nível de condicionamento físico. A supervisão de um profissional de Educação Física é essencial para garantir a execução segura e eficaz do movimento.

Aviso: Sempre busque orientação de um profissional qualificado para a prática de exercícios físicos.', 'Glúteos/machine_glute_kickback.mp4', 'Glúteos/machine_glute_kickback.webp');
INSERT INTO exercise_translations (exercise_id, locale, name, description)
    SELECT id, 'en', 'Machine Glute Kickback', 'Exercise: Machine Glute Kickback

The Machine Glute Kickback is an isolation exercise focused on strengthening and developing the glutes, especially the gluteus maximus. To perform the exercise, position yourself on a dedicated glute kickback machine with support for your torso and a pad or platform for your working leg. The movement consists of extending your leg backward, activating the glutes and, to a lesser extent, the hamstrings.

The main goal of this exercise is to increase glute strength and endurance, which also helps improve hip stability, prevent injuries, and support better performance in daily activities and sports. It can also help define this area, creating a more sculpted shape.

Use proper technique and adjust the load to your fitness level. Supervision from a fitness professional is essential to ensure the movement is performed safely and effectively.

Warning: Always seek guidance from a qualified professional when exercising.' FROM exercises WHERE name = 'Coice de Glúteo na Máquina'
    ON CONFLICT (exercise_id, locale) DO NOTHING;

INSERT INTO exercises (id, name, category, description, video_ref, thumb_ref) VALUES (gen_random_uuid(), 'Crucifixo Unilateral no Solo com Landmine', 'CHEST', 'O Crucifixo Unilateral no Solo com Landmine é um exercício para o peitoral que trabalha o peitoral maior, com ajuda dos deltoides anteriores e do core, que atua para impedir a rotação do tronco. O chão limita a amplitude do movimento, tornando-o uma forma segura para os ombros de treinar o peitoral um lado de cada vez.

Para executá-lo, encaixe uma ponta da barra em um suporte landmine e deite-se de barriga para cima no chão, ao lado da ponta livre. Segure a ponta da barra com uma mão, com o braço estendido acima do peito e o cotovelo levemente flexionado. Mantendo esse ângulo do cotovelo, desça a barra para o lado em um arco amplo até a parte de trás do braço tocar levemente o chão. Contraia o peitoral para levar a barra de volta acima do peito. Complete todas as repetições e depois troque de lado.

Faça 3 séries de 8 a 12 repetições por braço, com carga leve a moderada.

Atenção: mantenha o movimento lento e controlado e não deixe o braço cair no chão. Busque sempre a orientação de um profissional de educação física antes de iniciar qualquer programa de exercícios, para garantir uma execução correta e segura.', 'Peitoral/landmine_one_arm_floor_fly.mp4', 'Peitoral/landmine_one_arm_floor_fly.webp');
INSERT INTO exercise_translations (exercise_id, locale, name, description)
    SELECT id, 'en', 'Landmine One-Arm Floor Fly', 'The Landmine One-Arm Floor Fly is a chest exercise that targets the pectoralis major, with help from the front deltoids and the core, which works to keep your torso from rotating. The floor limits your range of motion, making it a shoulder-friendly way to train the chest one side at a time.

To perform it, place one end of a barbell in a landmine attachment and lie on your back on the floor beside the free end. Hold the end of the bar in one hand with your arm extended above your chest and a slight bend in your elbow. Keeping that elbow angle fixed, lower the bar out to the side in a wide arc until your upper arm lightly touches the floor. Squeeze your chest to bring the bar back up over your chest. Complete all reps, then switch sides.

Do 3 sets of 8 to 12 reps per arm, using a light to moderate load.

Warning: Keep the movement slow and controlled, and don''t let your arm drop onto the floor. Always seek guidance from a fitness professional before starting any exercise program to ensure proper and safe form.' FROM exercises WHERE name = 'Crucifixo Unilateral no Solo com Landmine'
    ON CONFLICT (exercise_id, locale) DO NOTHING;

INSERT INTO exercises (id, name, category, description, video_ref, thumb_ref) VALUES (gen_random_uuid(), 'Cruz de Ferro com Halteres', 'CROSSFIT', 'O exercício conhecido como Cruz de Ferro com Halteres é uma excelente opção para trabalhar a musculatura do peitoral, ombros e tríceps. Ao executar o movimento, você utiliza halteres, o que permite um maior conforto nas articulações e uma amplitude de movimento mais completa, ajudando a aumentar a força e a definição muscular.

Para realizá-lo, comece deitado em um banco plano com um haltere em cada mão. Com os braços estendidos acima do peito e as palmas das mãos voltadas uma para a outra, desça lentamente os halteres para os lados, mantendo os cotovelos ligeiramente flexionados. O movimento deve ser controlado, voltando à posição inicial ao tensionar os músculos do peito. É importante manter a postura correta durante toda a execução para evitar lesões.

Esse exercício não só fortalece o peitoral, mas também ativa os músculos estabilizadores, contribuindo para um melhor equilíbrio e postura. Adicionalmente, ajuda a desenvolver força funcional, que é benéfica para diversas atividades diárias e outros exercícios.

Entretanto, é fundamental executar o exercício com a técnica correta e iniciar com pesos adequados. Sempre procure a orientação de um profissional qualificado para garantir a segurança e eficácia do treino.', 'Crossfit/dumbbell_iron_cross.mp4', 'Crossfit/dumbbell_iron_cross.webp');
INSERT INTO exercise_translations (exercise_id, locale, name, description)
    SELECT id, 'en', 'Dumbbell Iron Cross', 'The Dumbbell Iron Cross is an excellent option for working the shoulders, especially the front and side deltoids. Holding a dumbbell in each hand, you move your arms through a wide arc, alternating between front and lateral raises, which helps build shoulder strength, endurance and control.

To perform it, stand with your feet shoulder-width apart and a dumbbell in each hand in front of your thighs. Raise both dumbbells to the front up to shoulder height, then open your arms out to the sides into a "T" (the cross position), bring them back to the front and lower them with control. Keep a slight bend in your elbows and correct posture throughout to avoid injuries.

This exercise not only strengthens the shoulders but also activates stabilizing muscles, contributing to better balance and posture. It also helps develop functional strength, which benefits many daily activities and other exercises.

However, it is essential to use correct technique and start with appropriate weights. Always seek guidance from a qualified professional to ensure safe, effective training.' FROM exercises WHERE name = 'Cruz de Ferro com Halteres'
    ON CONFLICT (exercise_id, locale) DO NOTHING;

INSERT INTO exercises (id, name, category, description, video_ref, thumb_ref) VALUES (gen_random_uuid(), 'Elevação Lateral Sentado com Tronco Inclinado', 'SHOULDERS', 'A Elevação Lateral Sentado com Tronco Inclinado é um exercício eficaz para fortalecer os deltoides posteriores e a parte superior das costas, incluindo os romboides e o trapézio. Ela é importante para melhorar a postura e a estabilidade dos ombros, o que é útil nas atividades diárias e nos esportes.

Para executar o exercício, sente-se na ponta de um banco com os pés juntos no chão. Segure um halter em cada mão e incline o tronco à frente até o peito ficar próximo das coxas, deixando os halteres pendurados ao lado das panturrilhas, com as palmas das mãos voltadas uma para a outra. Com os cotovelos levemente flexionados, eleve os halteres para os lados até os braços ficarem mais ou menos alinhados com os ombros, aproximando as escápulas. Desça lentamente até a posição inicial, controlando o movimento.

Faça 3 séries de 12 a 15 repetições com carga leve, concentrando-se nos músculos da parte superior das costas e evitando movimentos bruscos.

Atenção: realize este exercício com a supervisão de um profissional de educação física, que pode ajudar com a técnica correta e adaptá-lo às suas necessidades e ao seu nível de condicionamento.', 'Ombros/seated_bent_over_lateral_raise.mp4', 'Ombros/seated_bent_over_lateral_raise.webp');
INSERT INTO exercise_translations (exercise_id, locale, name, description)
    SELECT id, 'en', 'Seated Bent-Over Lateral Raise', 'The Seated Bent-Over Lateral Raise is an effective exercise for strengthening the rear deltoids and upper back, including the rhomboids and trapezius. It is important for improving posture and shoulder stability, which is useful in daily activities and sports.

To perform the exercise, sit on the end of a bench with your feet together on the floor. Hold a dumbbell in each hand and lean forward until your chest is close to your thighs, letting the dumbbells hang beside your calves with your palms facing each other. With a slight bend in your elbows, raise the dumbbells out to the sides until your arms are roughly in line with your shoulders, squeezing your shoulder blades together. Slowly lower them back to the starting position, controlling the movement.

Perform 3 sets of 12 to 15 reps with a light weight, focusing on the muscles of the upper back and avoiding jerky movements.

Warning: Follow the supervision of a fitness professional when performing this exercise, as they can help with proper form and adapt it to your needs and fitness level.' FROM exercises WHERE name = 'Elevação Lateral Sentado com Tronco Inclinado'
    ON CONFLICT (exercise_id, locale) DO NOTHING;

INSERT INTO exercises (id, name, category, description, video_ref, thumb_ref) VALUES (gen_random_uuid(), 'Elevação Lateral na Máquina Sentado', 'SHOULDERS', 'Exercício: Máquina de Elevação Lateral

A máquina de elevação lateral é um equipamento de musculação que foca no fortalecimento dos músculos deltóides, mais especificamente a porção lateral. Este exercício é ideal para aumentar a definição e a largura dos ombros, contribuindo para uma estética corporal equilibrada e ajudando na melhoria da postura.

Ao realizar a elevação lateral na máquina, o praticante se senta em uma posição confortável, com as costas apoiadas, e ajusta os apoios de acordo com sua altura. O movimento consiste em levantar os braços lateralmente até atingir a altura dos ombros, mantendo as costas retas e os cotovelos levemente flexionados. Esse exercício não apenas fortalece os ombros, mas também pode auxiliar na prevenção de lesões, uma vez que promove um fortalecimento equilibrado da musculatura acessória da região.

É importante ressaltar que a técnica correta e a utilização de cargas apropriadas são fundamentais para evitar lesões. Por isso, recomenda-se sempre buscar a orientação de um profissional de educação física antes de iniciar qualquer treino.', 'Ombros/seated_machine_lateral_raise.mp4', 'Ombros/seated_machine_lateral_raise.webp');
INSERT INTO exercise_translations (exercise_id, locale, name, description)
    SELECT id, 'en', 'Seated Machine Lateral Raise', 'Exercise: Seated Machine Lateral Raise

The Seated Machine Lateral Raise is a strength training exercise that strengthens the deltoids, specifically the side delts. It is ideal for increasing shoulder definition and width, contributing to a balanced physique and helping improve posture.

To perform the Seated Machine Lateral Raise, sit comfortably with your back supported and adjust the pads to your height. Raise your arms out to the sides until they reach shoulder height, keeping your back straight and your elbows slightly bent. This exercise not only strengthens the shoulders but can also help prevent injuries by promoting balanced strength in the supporting muscles of the area.

Warning: Correct technique and appropriate loads are essential to avoid injury. Always seek guidance from a certified fitness professional before starting any workout.' FROM exercises WHERE name = 'Elevação Lateral na Máquina Sentado'
    ON CONFLICT (exercise_id, locale) DO NOTHING;

INSERT INTO exercises (id, name, category, description, video_ref, thumb_ref) VALUES (gen_random_uuid(), 'Elevação Pélvica com Peso Corporal', 'CALISTHENICS', 'A Elevação Pélvica com Peso Corporal é um exercício eficaz para fortalecer a musculatura da região glútea e dos músculos isquiotibiais. Este movimento também engaja a região do core, proporcionando maior estabilidade e suporte ao tronco.

Para realizar o exercício, deite-se de costas em uma superfície plana, com os joelhos dobrados e os pés apoiados no chão, alinhados com os quadris. Mantenha os braços ao longo do corpo. Ao inspirar, levante o quadril em direção ao teto, pressionando os calcanhares contra o solo. No topo do movimento, contraia os glúteos e mantenha a posição por um segundo. Em seguida, ao expirar, abaixe lentamente o quadril de volta à posição inicial. Este movimento deve ser executado de forma controlada para maximizar o recrutamento muscular e evitar lesões.

A Elevação de Quadril pode ser facilmente adaptada, aumentando a dificuldade com a inclusão de pesos ou variações como a elevação de uma perna. É uma ótima opção tanto para iniciantes quanto para praticantes mais avançados.

É fundamental realizar o exercício com a forma correta para evitar lesões. Recomenda-se sempre consultar um profissional de educação física antes de iniciar qualquer programa de exercícios.', 'Calistenia/bodyweight_hip_thrust.mp4', 'Calistenia/bodyweight_hip_thrust.webp');
INSERT INTO exercise_translations (exercise_id, locale, name, description)
    SELECT id, 'en', 'Bodyweight Hip Thrust', 'The Bodyweight Hip Thrust is an effective exercise for strengthening the glutes and hamstrings. It also engages the core, providing greater stability and support for the trunk.

To perform it, rest your upper back on a bench or lie on your back on the floor, with your knees bent and your feet flat on the floor, in line with your hips. Push your heels into the floor and raise your hips toward the ceiling. At the top, squeeze your glutes and hold for a second. Then slowly lower your hips back to the starting position. Perform the movement with control to maximize muscle recruitment and avoid injuries.

This exercise is easy to adapt, increasing difficulty with added weight or variations such as the single-leg version. It is a great option for both beginners and more advanced trainees.

Use correct form to avoid injuries. It is always recommended to consult a fitness professional before starting any exercise program.' FROM exercises WHERE name = 'Elevação Pélvica com Peso Corporal'
    ON CONFLICT (exercise_id, locale) DO NOTHING;

INSERT INTO exercises (id, name, category, description, video_ref, thumb_ref) VALUES (gen_random_uuid(), 'Encolhimento na Máquina (Alavanca)', 'TRAPEZIUS', 'O Encolhimento na Máquina (Alavanca) é um exercício voltado para o fortalecimento e definição dos músculos da região anterior e lateral do pescoço, especificamente os músculos esternocleidomastoideo e trapézio superior.

Este exercício é realizado em uma máquina específica que permite a movimentação controlada, proporcionando um estímulo eficiente para os músculos cervicais. Ao executar o movimento, o praticante deve se posicionar adequadamente, ajustando a carga conforme seu nível de força e resistência. O movimento consiste em elevar a cabeça em direção ao peito, mantendo os ombros relaxados e evitando tensão desnecessária na região cervical.

O Encolhimento com Alavanca não só ajuda no fortalecimento muscular, mas também pode contribuir para melhorar a postura, promover estabilidade na região cervical e aumentar a resistência durante outras atividades físicas que envolvem os músculos do pescoço.

Contudo, é importante ressaltar que a execução desse exercício deve ser acompanhada por um profissional de educação física para garantir a correta execução e prevenir lesões. Cada praticante deve respeitar suas limitações e adaptar a carga às suas capacidades individuais.', 'Trapézio/lever_shrug.mp4', 'Trapézio/lever_shrug.webp');
INSERT INTO exercise_translations (exercise_id, locale, name, description)
    SELECT id, 'en', 'Lever Shrug', 'The Lever Shrug is an exercise aimed at strengthening and defining the upper trapezius.

It is performed on a lever (plate-loaded) shrug machine, which allows controlled movement and an efficient stimulus. Stand in the machine holding the handles with your arms straight and adjust the load to your strength level. Shrug your shoulders up toward your ears, hold briefly and lower them with control, avoiding unnecessary tension in the neck.

The Lever Shrug not only builds strength but can also help improve posture, stabilize the neck and shoulders and increase endurance in other activities that involve the upper back.

This exercise should be supervised by a fitness professional to ensure correct execution and prevent injuries. Each person should respect their limits and adapt the load to their individual capacity.' FROM exercises WHERE name = 'Encolhimento na Máquina (Alavanca)'
    ON CONFLICT (exercise_id, locale) DO NOTHING;

INSERT INTO exercises (id, name, category, description, video_ref, thumb_ref) VALUES (gen_random_uuid(), 'Flexão Nórdica no Banco', 'LEGS', 'A Flexão Nórdica, também conhecida como Nordic Hamstring Curl, é um exercício que visa fortalecer os músculos isquiotibiais, que estão localizados na parte de trás das coxas. Este movimento é essencial para melhorar a estabilidade do joelho, aumentar a força muscular e prevenir lesões, especialmente em atletas de esportes que exigem corrida e mudanças rápidas de direção. Além disso, a flexão nórdica também beneficia a força do core e do quadril, o que contribui para uma melhor performance em diversas atividades físicas.

Para realizar a Flexão Nórdica, posicione-se de joelhos com os pés fixos atrás de você (pode usar um parceiro ou equipamento para segurar os pés). Com o corpo alinhado em uma linha reta, comece a descer lentamente o tronco em direção ao chão mantendo a contração dos isquiotibiais. A ideia é descer controladamente o máximo que conseguir sem cair, e depois retornar à posição inicial, utilizando a força dos isquiotibiais. Se necessário, a ajuda das mãos pode ser utilizada para equilibrar na subida.

Devido à sua intensidade, é importante realizar este exercício com cautela e sempre sob a orientação de um profissional de educação física para evitar lesões e garantir a execução correta do movimento.', 'Pernas/bench_nordic_hamstring_curl.mp4', 'Pernas/bench_nordic_hamstring_curl.webp');
INSERT INTO exercise_translations (exercise_id, locale, name, description)
    SELECT id, 'en', 'Bench Nordic Hamstring Curl', 'The Bench Nordic Hamstring Curl is an exercise designed to strengthen the hamstrings, the muscles along the back of your thighs. This movement is key for improving knee stability, building muscle strength, and preventing injuries, especially for athletes in sports that involve sprinting and quick changes of direction. It also builds core and hip strength, which supports better performance across many physical activities.

To perform the Bench Nordic Hamstring Curl, kneel on a padded bench with your ankles secured behind you (under the bench pad, or held by a partner). Keeping your body in a straight line from knees to head, slowly lower your torso toward the floor while keeping your hamstrings engaged. The goal is to lower yourself under control as far as you can without collapsing, then pull yourself back to the starting position using your hamstrings. If needed, use your hands to catch yourself at the bottom and give a light push to help on the way up.

Because of its intensity, perform this exercise with caution and ideally under the guidance of a fitness professional to avoid injury and make sure you use proper form.' FROM exercises WHERE name = 'Flexão Nórdica no Banco'
    ON CONFLICT (exercise_id, locale) DO NOTHING;

INSERT INTO exercises (id, name, category, description, video_ref, thumb_ref) VALUES (gen_random_uuid(), 'Flexão com Mãos Invertidas', 'CALISTHENICS', 'A Flexão com Mãos Invertidas é uma variação da flexão em que as mãos ficam giradas, com os dedos apontando para trás, em direção aos pés. É um excelente exercício de fortalecimento da parte superior do corpo, com ênfase extra nos bíceps, antebraços e parte frontal dos ombros, além do peitoral e dos tríceps.

Para executá-la, fique em posição de prancha com as mãos abaixo do peito, os dedos apontando para os pés e os cotovelos próximos ao corpo. Desça o corpo em direção ao chão flexionando os cotovelos, mantendo-os junto ao tronco, e depois empurre de volta à posição inicial. Mantenha o core contraído e o corpo alinhado durante todo o movimento.

Esta variação é útil para desenvolver força e mobilidade nos punhos e para variar o treino de flexões, contribuindo para um melhor desempenho em diversas atividades físicas.

Ela altera a carga sobre os punhos e cotovelos, por isso é fundamental executá-la com cuidado e com a técnica correta para evitar lesões. Sempre que possível, consulte um profissional de educação física para receber orientação personalizada.', 'Calistenia/reverse_hand_push_up.mp4', 'Calistenia/reverse_hand_push_up.webp');
INSERT INTO exercise_translations (exercise_id, locale, name, description)
    SELECT id, 'en', 'Reverse-Hand Push-Up', 'The Reverse-Hand Push-Up is a push-up variation in which your hands are turned so that your fingers point back toward your feet. It is an excellent strengthening exercise for the upper body, with extra emphasis on the biceps, forearms and front of the shoulders, as well as the chest and triceps.

To perform it, get into a plank with your hands under your chest, fingers pointing toward your feet and elbows close to your body. Lower your body toward the floor by bending your elbows, keeping them tucked, then push back up to the starting position. Keep your core engaged and your body in a straight line throughout.

This variation is useful for building wrist strength and mobility and for adding variety to push-up training, contributing to better performance in many physical activities.

It changes the load on the wrists and elbows, so it is crucial to perform it carefully and with correct technique to avoid injuries. Whenever possible, consult a fitness professional for personalized guidance.' FROM exercises WHERE name = 'Flexão com Mãos Invertidas'
    ON CONFLICT (exercise_id, locale) DO NOTHING;

INSERT INTO exercises (id, name, category, description, video_ref, thumb_ref) VALUES (gen_random_uuid(), 'Flexão com Transição para Cobra', 'FUNCTIONAL_HIT', 'A Flexão com Transição para Cobra é um exercício com peso corporal que combina a flexão com o alongamento da cobra, do yoga. Ela fortalece peitoral, tríceps, ombros e core, enquanto alonga o abdômen e os flexores do quadril e melhora a mobilidade da coluna.

Para executar a Flexão com Transição para Cobra, siga estes passos:

1. Comece em prancha alta, com as mãos um pouco mais afastadas que a largura dos ombros e o corpo alinhado da cabeça aos calcanhares.

2. Desça o peito em direção ao chão flexionando os cotovelos, mantendo-os a cerca de 45 graus do corpo.

3. Quando o corpo estiver perto do chão, apoie o quadril e as coxas no chão, estenda a ponta dos pés e empurre com as mãos para elevar o peito na posição de cobra, com o quadril no chão e o olhar à frente.

4. Eleve o quadril de volta à posição de prancha e faça uma flexão para retornar à posição inicial.

5. Repita por 2 a 3 séries de 8 a 12 repetições, sempre mantendo uma boa postura para evitar lesões.

Atenção: faça um bom aquecimento antes de começar este exercício. Se você é iniciante ou tem problemas nas costas ou outras condições de saúde preexistentes, consulte um profissional de educação física ou personal trainer certificado para garantir uma execução segura e eficaz.', 'Funcional e HIT/push_up_to_cobra.mp4', 'Funcional e HIT/push_up_to_cobra.webp');
INSERT INTO exercise_translations (exercise_id, locale, name, description)
    SELECT id, 'en', 'Push-Up to Cobra', 'The Push-Up to Cobra is a bodyweight exercise that combines a push-up with the cobra stretch from yoga. It strengthens the chest, triceps, shoulders, and core while stretching the abs and hip flexors and improving spinal mobility.

To perform the Push-Up to Cobra, follow these steps:

1. Start in a high plank position with your hands slightly wider than shoulder-width apart and your body in a straight line from head to heels.

2. Lower your chest toward the floor by bending your elbows, keeping them at about a 45-degree angle to your body.

3. As your body nears the floor, lower your hips and thighs to the floor, point your toes, and press through your hands to lift your chest into a cobra position, with your hips on the floor and your gaze forward.

4. Lift your hips back up into a plank position and perform a push-up to return to the starting position.

5. Repeat for 2 to 3 sets of 8 to 12 reps, always maintaining good posture to avoid injury.

Warning: Warm up properly before starting this exercise. If you are a beginner or have back problems or other preexisting health conditions, consult a certified fitness professional or personal trainer to make sure you perform it safely and effectively.' FROM exercises WHERE name = 'Flexão com Transição para Cobra'
    ON CONFLICT (exercise_id, locale) DO NOTHING;

INSERT INTO exercises (id, name, category, description, video_ref, thumb_ref) VALUES (gen_random_uuid(), 'Hiperextensão Lombar no Banco Reto', 'LOWER_BACK', 'A hiperextensão de lombar no banco plano é um exercício eficaz para fortalecer a musculatura da região lombar e dos glúteos. Ao realizar este movimento, você foca no fortalecimento do eretor da espinha, um grupo de músculos que ajuda a manter a postura ereta e a estabilizar a coluna vertebral.

A execução se dá em um banco plano, com a parte superior das pernas apoiada e os pés firmemente fixos. O movimento consiste em flexionar o tronco à frente e, em seguida, retornar à posição inicial, erguendo o tronco para trás. Esse movimento deve ser controlado, evitando movimentos bruscos para prevenir lesões.

Além de proporcionar aumento da força e resistência da região lombar, este exercício pode auxiliar na prevenção de dores nas costas e melhorar a performance em outras atividades físicas, especialmente em esportes que exigem a utilização de força do tronco.

É importante destacar que a execução correta do exercício é crucial para evitar lesões. Portanto, sempre busque a orientação de um profissional de educação física antes de iniciar a prática deste ou de qualquer outro exercício.', 'Eretor Lombar/flat_bench_back_extension.mp4', 'Eretor Lombar/flat_bench_back_extension.webp');
INSERT INTO exercise_translations (exercise_id, locale, name, description)
    SELECT id, 'en', 'Flat Bench Back Extension', 'The Flat Bench Back Extension is an effective exercise for strengthening the lower back and glutes. It focuses on the erector spinae, a group of muscles that helps you stand upright and stabilizes the spine.

The exercise is performed on a flat bench, with your upper thighs supported and your feet firmly secured. Lower your torso forward, then return to the starting position by raising your torso back up. Keep the movement controlled and avoid jerky motions to prevent injury.

Besides increasing lower back strength and endurance, this exercise can help prevent back pain and improve performance in other activities, especially sports that demand trunk strength.

Warning: Correct form is crucial to avoid injury. Always seek guidance from a certified fitness professional before starting this or any other exercise.' FROM exercises WHERE name = 'Hiperextensão Lombar no Banco Reto'
    ON CONFLICT (exercise_id, locale) DO NOTHING;

INSERT INTO exercises (id, name, category, description, video_ref, thumb_ref) VALUES (gen_random_uuid(), 'Meio Desenvolvimento Arnold Sentado', 'SHOULDERS', 'Exercício: Desenvolvimento Arnold (Metade)

O Desenvolvimento Arnold, também conhecido como Arnold Press, é um exercício para os ombros que visa melhorar a força e a definição muscular na região. Ao realizar a versão “metade”, o movimento é iniciado a partir de uma posição em que os halteres estão alinhados com o queixo, com as palmas das mãos voltadas para o corpo. Em seguida, você irá empurrar os halteres para cima e, ao mesmo tempo, girar os pulsos, de modo que, ao final do movimento, as palmas das mãos fiquem voltadas para a frente.

Esse exercício não só trabalha o deltóide, mas também ativa músculos secundários, como o tríceps e a parte superior do peitoral. O movimento de rotação ajuda a ativar diferentes fibras musculares e pode contribuir para um desenvolvimento mais equilibrado da musculatura dos ombros.

É importante realizar o exercício com atenção à forma correta, evitando sobrecargas e lesões. Sempre busque a orientação de um profissional capacitado para garantir a execução adequada e a segurança durante o treinamento.', 'Ombros/seated_half_arnold_press.mp4', 'Ombros/seated_half_arnold_press.webp');
INSERT INTO exercise_translations (exercise_id, locale, name, description)
    SELECT id, 'en', 'Seated Half Arnold Press', 'Exercise: Seated Half Arnold Press

The Arnold Press is a shoulder exercise that aims to improve strength and muscle definition. In this "half" version, performed seated, the rotation happens only in the first part of the movement: you start with the dumbbells at chin height, palms facing your body, and rotate them until your palms face forward as you begin to press, then finish the press overhead.

This exercise works not only the deltoids but also secondary muscles, such as the triceps and upper chest. The rotation helps activate different muscle fibers and can contribute to more balanced shoulder development.

Pay attention to correct form, avoiding overload and injuries. Always seek guidance from a qualified professional to ensure proper execution and safety during training.' FROM exercises WHERE name = 'Meio Desenvolvimento Arnold Sentado'
    ON CONFLICT (exercise_id, locale) DO NOTHING;

INSERT INTO exercises (id, name, category, description, video_ref, thumb_ref) VALUES (gen_random_uuid(), 'Mergulho entre Bancos', 'TRICEPS', 'O exercício Tríceps no Banco é uma excelente opção para fortalecer e tonificar os músculos do tríceps braquial, que são os responsáveis pela extensão do cotovelo. Para realizá-lo, você precisa de um banco e, opcionalmente, de um par de halteres ou apenas do peso do corpo.

Para executar o exercício, siga os passos abaixo:

• Sentado ou apoiado no banco, mantenha as mãos na borda do banco, com os dedos voltados para frente.

• Estenda as pernas à sua frente, mantendo os calcanhares apoiados no chão.

• Flexione os cotovelos lentamente, descendo o corpo em direção ao chão, até que os cotovelos estejam em um ângulo de aproximadamente 90 graus.

• Em seguida, empurre-se para cima, estendendo os braços novamente.

Esse exercício é eficaz para melhorar a força dos membros superiores, especialmente para quem pratica esportes que exigem movimentos de empurrar.

É importante sempre realizar o exercício com a postura correta e em um ritmo controlado para evitar lesões. Além disso, recomenda-se consultar um profissional de educação física antes de iniciar qualquer programa de treinamento físico.', 'Tríceps/between_benches_dip.mp4', 'Tríceps/between_benches_dip.webp');
INSERT INTO exercise_translations (exercise_id, locale, name, description)
    SELECT id, 'en', 'Between-Benches Dip', 'The Between-Benches Dip is an excellent option for strengthening and toning the triceps brachii, the muscles responsible for extending the elbow. You''ll need two benches placed parallel to each other and, optionally, a weight plate on your lap for extra resistance.

To perform the exercise, follow these steps:

Place your hands on the edge of one bench beside your hips, fingers pointing forward.

Extend your legs and rest your heels on the second bench in front of you, then slide your hips off the first bench.

Slowly bend your elbows to lower your body toward the floor until your elbows reach about a 90-degree angle.

Then push yourself back up by straightening your arms.

This exercise is effective for building upper-body strength, especially for athletes whose sports involve pushing movements.

Warning: Always use proper posture and a controlled pace to avoid injury. It''s also a good idea to consult a fitness professional before starting any training program.' FROM exercises WHERE name = 'Mergulho entre Bancos'
    ON CONFLICT (exercise_id, locale) DO NOTHING;

INSERT INTO exercises (id, name, category, description, video_ref, thumb_ref) VALUES (gen_random_uuid(), 'Mesa Flexora Ajoelhado (Alavanca)', 'LEGS', 'A Mesa Flexora Ajoelhado (Alavanca) é um exercício de força feito em máquina que isola os isquiotibiais, os músculos da parte de trás das coxas, trabalhando uma perna de cada vez.

O exercício consiste em flexionar o joelho contra uma resistência em uma máquina de flexão de pernas ajoelhado. É especialmente eficaz para trabalhar os isquiotibiais, com alguma participação das panturrilhas e dos glúteos, promovendo hipertrofia e ganho de força. Como cada perna trabalha de forma independente, ele também ajuda a corrigir desequilíbrios de força entre os lados e contribui para a estabilidade dos joelhos.

Para realizar o exercício, apoie um dos joelhos no apoio da máquina, apoie os antebraços ou o peito no suporte e posicione a parte de trás do tornozelo da perna que vai trabalhar sob o rolo. Mantendo o quadril parado e o tronco apoiado, flexione o joelho para levar o rolo em direção aos glúteos, contraia o isquiotibial no alto e depois desça lentamente até a perna ficar quase estendida. Faça 3 séries de 10 a 12 repetições por perna e depois troque de lado.

Atenção: use a técnica correta e uma carga adequada à sua capacidade para evitar lesões e aproveitar ao máximo o treino. É altamente recomendável buscar a orientação de um profissional de educação física ao incluir este exercício na sua rotina.', 'Pernas/lever_kneeling_leg_curl.mp4', 'Pernas/lever_kneeling_leg_curl.webp');
INSERT INTO exercise_translations (exercise_id, locale, name, description)
    SELECT id, 'en', 'Lever Kneeling Leg Curl', 'The Lever Kneeling Leg Curl is a machine-based strength exercise that isolates the hamstrings, the muscles on the back of the thighs, working one leg at a time.

This exercise involves bending the knee against resistance on a kneeling leg curl machine. It is especially effective for targeting the hamstrings, with some help from the calves and glutes, promoting muscle growth and strength gains. Because each leg works independently, it also helps correct strength imbalances between sides and supports knee stability.

To perform the exercise, kneel on the machine''s knee pad with one leg, rest your forearms or chest on the support pad, and place the back of the working ankle under the roller pad. Keeping your hips still and your torso supported, bend your knee to curl the pad up toward your glutes, squeeze your hamstring at the top, then slowly lower it until your leg is almost straight. Complete 3 sets of 10 to 12 repetitions per leg, then switch sides.

Warning: Use proper technique and a load appropriate for your ability to prevent injury and get the most out of your workout. It is highly recommended that you seek guidance from a fitness professional when adding this exercise to your routine.' FROM exercises WHERE name = 'Mesa Flexora Ajoelhado (Alavanca)'
    ON CONFLICT (exercise_id, locale) DO NOTHING;

INSERT INTO exercises (id, name, category, description, video_ref, thumb_ref) VALUES (gen_random_uuid(), 'Nadador (Swimming)', 'FUNCTIONAL_HIT', 'Exercício: Nadador (Swimming)

O Nadador (Swimming) é um exercício no solo com peso corporal que fortalece os músculos da parte de trás do corpo, incluindo a lombar (eretores da espinha), os glúteos, a parte posterior dos ombros e a parte superior das costas. Ele também melhora a mobilidade dos ombros e favorece uma postura melhor.

Para executá-lo, deite-se de bruços em um colchonete, com as pernas estendidas e os braços esticados acima da cabeça. Mantenha a testa um pouco acima do chão e o pescoço em posição neutra. Contraia os glúteos e eleve levemente os braços, o peito e as pernas do chão. Mantendo essa posição, eleve um pouco mais o braço direito e a perna esquerda; depois, troque para o braço esquerdo e a perna direita, em um movimento alternado e contínuo, como se estivesse nadando. Mantenha o movimento controlado e a respiração constante.

Faça 3 séries de 20 a 30 segundos, ou de 10 a 15 repetições por lado, descansando entre as séries.

Atenção: evite arquear demais a lombar ou forçar o pescoço com movimentos bruscos. Pare se sentir dor nas costas e consulte um profissional qualificado antes de começar se tiver histórico de problemas na coluna.', 'Funcional e HIT/prone_swimmers.mp4', 'Funcional e HIT/prone_swimmers.webp');
INSERT INTO exercise_translations (exercise_id, locale, name, description)
    SELECT id, 'en', 'Prone Swimmers', 'Exercise: Prone Swimmers

Prone Swimmers is a bodyweight floor exercise that strengthens the muscles along the back of your body, including the lower back (erector spinae), glutes, rear shoulders, and upper back. It also improves shoulder mobility and supports better posture.

To perform it, lie face down on a mat with your legs extended and your arms stretched overhead. Keep your forehead just off the floor and your neck neutral. Squeeze your glutes and lift your arms, chest, and legs slightly off the floor. Holding that position, lift your right arm and left leg a little higher, then switch to your left arm and right leg in a steady, alternating flutter, as if swimming. Keep the movement controlled and breathe steadily.

Do 3 sets of 20 to 30 seconds, or 10 to 15 reps per side, resting between sets.

Warning: Avoid overarching your lower back or jerking your neck. Stop if you feel back pain, and consult a qualified professional before starting if you have a history of back problems.' FROM exercises WHERE name = 'Nadador (Swimming)'
    ON CONFLICT (exercise_id, locale) DO NOTHING;

INSERT INTO exercises (id, name, category, description, video_ref, thumb_ref) VALUES (gen_random_uuid(), 'Remada Apoiada no Banco a 45° com Halteres', 'SHOULDERS', 'A Remada Apoiada no Banco a 45° com Halteres é um exercício de musculação que tem como objetivo principal desenvolver a força e a hipertrofia dos músculos das costas, especialmente os músculos romboides, latíssimos do dorso e trapézio.

Para realizar o exercício, o praticante deve ficar em uma posição inclinada, normalmente utilizando um banco ajustável a 45 graus. É importante manter os pés firmes no chão e pegar os halteres ou a barra com uma pegada que fique um pouco mais larga que a largura dos ombros. A execução inicia com os braços estendidos em direção ao chão e, em seguida, ao puxar o peso em direção ao corpo, deve-se manter os cotovelos próximos ao tronco, focando na contração dos músculos das costas.

Além de trabalhar principalmente a musculatura das costas, a remada inclinada também envolve os músculos dos braços, como os bíceps, e músculos estabilizadores da região do núcleo, promovendo um trabalho completo para a parte superior do corpo.

É fundamental garantir uma execução correta para evitar lesões, respeitando a amplitude de movimento e a postura. Recomendamos sempre a orientação de um profissional de Educação Física para adequar o exercício ao seu nível de experiência e condição física.', 'Ombros/45_incline_dumbbell_row.mp4', 'Ombros/45_incline_dumbbell_row.webp');
INSERT INTO exercise_translations (exercise_id, locale, name, description)
    SELECT id, 'en', '45° Incline Dumbbell Row', 'The 45° Incline Dumbbell Row is a strength exercise whose main goal is building strength and size in the back, especially the rhomboids, lats, and trapezius.

To perform the exercise, set an adjustable bench to 45 degrees and lie face down on it with your chest supported. Keep your feet firmly on the floor and hold a dumbbell in each hand. Start with your arms hanging straight toward the floor, then pull the weights toward your body, keeping your elbows close to your torso and focusing on squeezing your back muscles.

Besides working the back, the 45° Incline Dumbbell Row also involves the arms, such as the biceps, and the core stabilizers, providing complete upper-body work.

Warning: Proper form is essential to avoid injury, so respect your range of motion and posture. Always seek guidance from a fitness professional to adapt the exercise to your experience level and physical condition.' FROM exercises WHERE name = 'Remada Apoiada no Banco a 45° com Halteres'
    ON CONFLICT (exercise_id, locale) DO NOTHING;

INSERT INTO exercises (id, name, category, description, video_ref, thumb_ref) VALUES (gen_random_uuid(), 'Remada Deitado no Banco com Halteres', 'BACK', 'Exercício: Remada Deitado no Banco com Halteres

A Remada Deitado no Banco com Halteres é uma remada com apoio no peito, feita deitado de bruços em um banco reto. Ela trabalha principalmente o latíssimo do dorso, os romboides e o trapézio, com ajuda dos deltoides posteriores e do bíceps. Como o banco apoia o tronco, o exercício elimina o impulso e tira a sobrecarga da lombar.

Para executar a Remada Deitado no Banco com Halteres, siga estes passos:

1. Posicione um banco reto em uma altura suficiente (por exemplo, sobre blocos) para que os braços fiquem estendidos sem que os halteres toquem o chão.

2. Deite-se de bruços no banco, com o peito perto da borda superior, segurando um halter em cada mão com os braços estendidos para baixo.

3. Puxe os dois halteres em direção ao tronco, levando os cotovelos para trás e aproximando as escápulas.

4. Faça uma breve pausa no topo e depois desça os halteres com controle até os braços ficarem totalmente estendidos.

5. Faça de 3 a 4 séries de 8 a 12 repetições, mantendo o peito em contato com o banco o tempo todo.

Este exercício é eficaz para desenvolver a força das costas e melhorar a postura, sendo uma ótima opção para quem quer treinar as costas sem sobrecarregar a lombar.

Atenção: consulte sempre um profissional de educação física antes de iniciar um novo programa de exercícios, principalmente se você for iniciante ou tiver alguma condição de saúde específica.', 'Costas/dumbbell_prone_bench_row.mp4', 'Costas/dumbbell_prone_bench_row.webp');
INSERT INTO exercise_translations (exercise_id, locale, name, description)
    SELECT id, 'en', 'Dumbbell Prone Bench Row', 'Exercise: Dumbbell Prone Bench Row

The Dumbbell Prone Bench Row is a chest-supported rowing exercise performed lying face down on a flat bench. It mainly targets the lats, rhomboids, and trapezius, with help from the rear deltoids and biceps. Because the bench supports your torso, it removes momentum and takes stress off the lower back.

To perform the Dumbbell Prone Bench Row, follow these steps:

1. Set a flat bench high enough (for example, on blocks) that your arms can hang straight without the dumbbells touching the floor.

2. Lie face down on the bench with your chest near the top edge, holding a dumbbell in each hand with your arms hanging straight down.

3. Pull both dumbbells toward your torso, driving your elbows back and squeezing your shoulder blades together.

4. Pause briefly at the top, then lower the dumbbells under control until your arms are fully extended.

5. Perform 3 to 4 sets of 8 to 12 reps, keeping your chest in contact with the bench throughout.

This exercise is effective for building back strength and improving posture, and it is a great option for people who want to train the back without loading the lower back.

Warning: Always consult a fitness professional before starting any new exercise program, especially if you are a beginner or have a specific health condition.' FROM exercises WHERE name = 'Remada Deitado no Banco com Halteres'
    ON CONFLICT (exercise_id, locale) DO NOTHING;

INSERT INTO exercises (id, name, category, description, video_ref, thumb_ref) VALUES (gen_random_uuid(), 'Supino Declinado na Máquina (Alavanca)', 'CHEST', 'O supino declinado com alavanca é um exercício de musculação que foca no desenvolvimento da musculatura peitoral inferior, além de envolver os músculos deltoides e tríceps. Este movimento é realizado em uma máquina específica, que proporciona maior estabilidade e segurança ao executá-lo, reduzindo o risco de lesões em comparação ao supino livre.

A execução do supino declinado é feita com o atleta deitado em uma superfície inclinada para baixo. A posição correta dos pés e das mãos na alavanca é fundamental para garantir a eficácia do exercício e evitar compensações. O movimento consiste em abaixar o peso de forma controlada até quase tocar o peito, e em seguida, empurrá-lo para cima, voltando à posição inicial. Esse exercício não só ajuda a esculpir o peitoral, mas também melhora a força geral da parte superior do corpo.

No entanto, é essencial realizar esse exercício sob a supervisão de um profissional de educação física. A orientação adequada é fundamental para garantir que você esteja utilizando a forma correta e ajustando a carga de acordo com sua capacidade, minimizando o risco de lesões.', 'Peitoral/lever_decline_chest_press.mp4', 'Peitoral/lever_decline_chest_press.webp');
INSERT INTO exercise_translations (exercise_id, locale, name, description)
    SELECT id, 'en', 'Lever Decline Chest Press', 'The Lever Decline Chest Press is a strength training exercise focused on developing the lower chest, while also engaging the deltoids and triceps. It is performed on a dedicated machine that provides greater stability and safety, reducing the risk of injury compared to the free-weight bench press.

The Lever Decline Chest Press is performed seated in the machine, with the handles set so that you press on a downward angle. Correct positioning of your back, feet, and hands on the lever is essential for the exercise to be effective and to avoid compensations. Press the handles forward and down until your arms are extended, then return them with control until they are close to your chest. This exercise not only helps sculpt the chest but also improves overall upper-body strength.

Warning: It is essential to perform this exercise under the supervision of a fitness professional. Proper guidance ensures that you use correct form and adjust the load to your ability, minimizing the risk of injury.' FROM exercises WHERE name = 'Supino Declinado na Máquina (Alavanca)'
    ON CONFLICT (exercise_id, locale) DO NOTHING;

INSERT INTO exercises (id, name, category, description, video_ref, thumb_ref) VALUES (gen_random_uuid(), 'Supino Inclinado na Máquina (Alavanca)', 'CHEST', 'O exercício Supino Inclinado na Alavanca é uma variação do supino tradicional que foca principalmente na parte superior do peitoral, especialmente nas fibras superiores, além de trabalhar também os deltoides anteriores e os tríceps. Ao realizar o supino inclinado, a inclinação do banco permite um ângulo que ativa estes músculos de forma diferenciada, promovendo um desenvolvimento muscular equilibrado e evitando lesões.

A execução do exercício geralmente envolve a devida acomodação na posição deitado, com os pés firmemente apoiados no chão e as mãos posicionadas na barra ou alavanca, em uma largura que favoreça a estabilidade. É importante controlar a descida do peso até que a barra chegue próximo ao peito, garantindo um movimento fluido e controlado. A pressão deve ser feita para cima, estendendo os braços completamente sem trancar os cotovelos.

É fundamental manter uma postura adequada durante a realização do exercício, evitando a curvatura excessiva da coluna ou a elevação dos pés, para garantir a segurança e eficácia do treinamento.

Antes de iniciar qualquer programa de exercícios, é sempre recomendável consultar um profissional qualificado que possa orientar e supervisionar a execução adequada dos movimentos.', 'Peitoral/lever_incline_chest_press.mp4', 'Peitoral/lever_incline_chest_press.webp');
INSERT INTO exercise_translations (exercise_id, locale, name, description)
    SELECT id, 'en', 'Lever Incline Chest Press', 'The Lever Incline Chest Press is a variation of the traditional bench press that mainly targets the upper chest, especially the upper fibers, while also working the front deltoids and triceps. The incline angle activates these muscles in a distinct way, promoting balanced muscle development.

To perform the exercise, settle into the machine with your back against the inclined pad, your feet planted firmly on the floor, and your hands on the lever handles at a width that feels stable. Control the lowering phase until the handles come close to your chest, keeping the movement smooth. Then press the handles up and away, extending your arms fully without locking your elbows.

Maintain proper posture throughout the exercise, avoiding excessive arching of your back or lifting your feet, to keep your training safe and effective.

Warning: Before starting any exercise program, it is always recommended to consult a qualified professional who can guide and supervise your form.' FROM exercises WHERE name = 'Supino Inclinado na Máquina (Alavanca)'
    ON CONFLICT (exercise_id, locale) DO NOTHING;

INSERT INTO exercises (id, name, category, description, video_ref, thumb_ref) VALUES (gen_random_uuid(), 'Supino Sentado na Máquina (Alavanca)', 'CHEST', 'O Supino Sentado na Máquina (Alavanca) é um exercício que visa o fortalecimento dos músculos peitorais, deltoides e tríceps. Ele é realizado em uma máquina específica que utiliza uma alavanca, permitindo um movimento controlado e seguro. Esse tipo de supino é especialmente benéfico para quem busca aumentar a força e a massa muscular dos músculos do tronco, além de melhorar a estabilidade nas articulações dos ombros e cotovelos.

Durante o exercício, o praticante se deita em um banco e empurra a alavanca para cima, estendendo totalmente os braços, e depois a retorna à posição inicial. A carga e a trajetória do movimento são ajustáveis, o que torna o exercício acessível para diferentes níveis de condicionamento físico.

É crucial realizar o Supino com Alavanca com a técnica correta, pois uma execução inadequada pode levar a lesões. Portanto, é altamente recomendável que os praticantes busquem orientação de um profissional qualificado para garantir que realizem o exercício de forma segura e eficaz.', 'Peitoral/lever_seated_chest_press.mp4', 'Peitoral/lever_seated_chest_press.webp');
INSERT INTO exercise_translations (exercise_id, locale, name, description)
    SELECT id, 'en', 'Lever Seated Chest Press', 'The Lever Seated Chest Press is an exercise that strengthens the chest, deltoids, and triceps. It is performed on a lever machine, which allows a controlled and safe movement. This type of press is especially beneficial for those looking to increase upper-body strength and muscle mass, while also improving stability in the shoulder and elbow joints.

During the exercise, you sit on the machine with your back against the pad and press the lever handles forward, fully extending your arms, then return them to the starting position. The load and seat position are adjustable, making the exercise accessible to different fitness levels.

Warning: It is crucial to perform the Lever Seated Chest Press with correct technique, as improper form can lead to injury. It is highly recommended to seek guidance from a qualified professional to make sure you perform the exercise safely and effectively.' FROM exercises WHERE name = 'Supino Sentado na Máquina (Alavanca)'
    ON CONFLICT (exercise_id, locale) DO NOTHING;

INSERT INTO exercises (id, name, category, description, video_ref, thumb_ref) VALUES (gen_random_uuid(), 'Supino Sentado na Máquina Convergente', 'CHEST', 'O exercício de Supino na Máquina é uma variação da tradicional movimentação de supino, mas realizada em uma máquina que proporciona maior estabilidade e controle durante o movimento.

Esse exercício é especialmente voltado para o trabalho do miolo do peitoral, ou seja, a parte média da parede torácica. Ao empurrar o peso com os braços em direção ao teto, o Supino na Máquina ativa não apenas os músculos peitorais, mas também os tríceps e os ombros. A posição fixa das mãos e a trajetória guiada da máquina ajudam a manter a forma correta, minimizando o risco de lesões e permitindo um foco específico na musculatura desejada.

Além disso, a máquina proporciona uma resistência constante durante todo o movimento, o que pode ser benéfico para a hipertrofia muscular e o fortalecimento da região do peito.

É importante lembrar que, ao iniciar qualquer programa de exercícios, é fundamental seguir a orientação de um profissional de educação física para garantir uma prática segura e eficaz.', 'Peitoral/converging_chest_press_machine.mp4', 'Peitoral/converging_chest_press_machine.webp');
INSERT INTO exercise_translations (exercise_id, locale, name, description)
    SELECT id, 'en', 'Converging Chest Press Machine', 'The Converging Chest Press Machine is a variation of the traditional bench press performed on a machine that provides greater stability and control throughout the movement.

This exercise especially targets the inner and middle chest. As you press the handles forward, their converging path brings your hands together, working not only your chest but also your triceps and shoulders. The fixed hand position and guided path help you maintain proper form, minimizing the risk of injury and letting you focus on the target muscles.

The machine also provides constant resistance throughout the entire movement, which can be beneficial for muscle growth and chest strength.

Warning: When starting any exercise program, follow the guidance of a fitness professional to make sure you train safely and effectively.' FROM exercises WHERE name = 'Supino Sentado na Máquina Convergente'
    ON CONFLICT (exercise_id, locale) DO NOTHING;

