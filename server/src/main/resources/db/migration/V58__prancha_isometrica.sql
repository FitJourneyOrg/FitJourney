-- V58 -- reinsere "Prancha" (prancha isometrica frontal), o exercicio de core mais prescrito.
--
-- CONTEXTO (debito P3 "Falta prancha isometrica no catalogo"). A V54 apagou 4 variantes de
-- prancha ('Prancha', 'Prancha Frontal', 'Prancha com toque no ombro', 'Prancha lateral'):
-- legado da V9/V16, video_ref vazio desde sempre, sem uso em workout_exercises e sem
-- correspondencia no catalogo externo (gifs_exercicios/catalogo.json). Conferido de novo agora
-- (2026-09-24): a fonte externa AINDA nao tem midia de prancha isometrica padrao -- so existem
-- 'Abducao de Quadril em Prancha Lateral' e 'Snap Jump (Prancha para Agachamento)', que sao
-- outros exercicios.
--
-- DECISAO DO RAFAEL (2026-09-24): inserir mesmo sem midia, no mesmo padrao dos exercicios de
-- core que ja existem sem description/midia (debito P5 conhecido). video_ref/thumb_ref vazios
-- sao convencao estabelecida desde a V25, e o cliente degrada graciosamente -- confirmado em
-- MediaUrls.url(): ref em branco vira null, nunca URL quebrada, e NetworkImage mostra placeholder
-- neutro em vez de tentar carregar. Nao ha risco de crash nem de 404 silencioso.
--
-- ISSO QUEBRA DE PROPOSITO 4 GUARDAS conhecidas de CaminhosDeMidiaIntegrationTest ("o catalogo
-- tem 923 exercicios", "fora do formato novo nao sobra ninguem", "nao existem mais exercicios
-- sem midia") -- elas existem pra pegar reincidencia silenciosa do padrao que a V54 limpou.
-- A correcao certa nao e zerar a guarda: e o teste passar a aceitar UMA excecao NOMEADA
-- ('Prancha'), continuando a reprovar qualquer outra reincidencia. Reescrito no mesmo commit.
--
-- TAXONOMIA copiada do veredito original da V16 (id 'c30b977e-...', apagado na V54): CORE
-- isolado, SHOULDERS como secundario estabilizador, BODYWEIGHT, prescription_type TIME
-- (isometrico -- a progressao e por tempo de sustentacao, nao repeticao), is_base = true (e o
-- exercicio-base da familia de pranchas, nao uma variacao).
--
-- exercise_translations (en) inserido na MESMA migration -- sem isso,
-- CatalogoTraduzidoIntegrationTest.`nenhum idioma fica traduzido pela metade` quebra: o teste
-- conta TODO `exercises` e falha se o total de traducoes en nao bater 1:1 com o catalogo inteiro.

INSERT INTO exercises (
    id, name, category, description, video_ref, thumb_ref,
    modality, movement_pattern, is_compound, equipment,
    primary_muscles, secondary_muscles, unilateral, prescription_type, level, contraindications,
    is_base
) VALUES (
    gen_random_uuid(), 'Prancha', 'CORE',
    'A prancha é um exercício isométrico que fortalece o core como um todo: reto abdominal, oblíquos e transverso do abdômen, além de exigir estabilização dos ombros e dos glúteos para manter o corpo alinhado.

Deite-se de bruços e apoie os antebraços e as pontas dos pés no chão, com os cotovelos alinhados sob os ombros. Suba o corpo mantendo uma linha reta da cabeça aos calcanhares, sem deixar o quadril cair nem subir demais. Contraia o abdômen e os glúteos e mantenha a posição pelo tempo prescrito, respirando normalmente.

É um exercício de baixo impacto e sem equipamento, adequado para qualquer nível -- a progressão se faz pelo TEMPO de sustentação, não por carga.

Aviso: interrompa se sentir dor na lombar -- geralmente indica quadril caído ou abdômen relaxado. Busque orientação de um profissional de educação física para corrigir a postura.',
    '', '',
    'STRENGTH', 'NONE', false, 'BODYWEIGHT',
    ARRAY['CORE']::TEXT[], ARRAY['SHOULDERS']::TEXT[], false, 'TIME', 'BEGINNER', ARRAY[]::TEXT[],
    true
);

INSERT INTO exercise_translations (exercise_id, locale, name, description)
    SELECT id, 'en', 'Plank', 'The plank is an isometric exercise that strengthens the whole core: rectus abdominis, obliques and transverse abdominis, while also requiring shoulder and glute stabilization to keep the body aligned.

Lie face down and support yourself on your forearms and the balls of your feet, elbows aligned under your shoulders. Raise your body into a straight line from head to heels, without letting your hips sag or pike up. Brace your abs and glutes and hold the position for the prescribed time, breathing normally.

It is a low-impact, no-equipment exercise suitable for any level -- progression comes from TIME under tension, not load.

Warning: stop if you feel lower-back pain -- it usually means the hips have sagged or the abs have relaxed. Seek guidance from a fitness professional to correct your posture.'
    FROM exercises WHERE name = 'Prancha'
    ON CONFLICT (exercise_id, locale) DO NOTHING;
