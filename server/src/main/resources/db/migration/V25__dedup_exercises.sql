-- V25: dedup de exercícios de NOME IDÊNTICO (mesmo movimento, IDs distintos).
-- Repoint das referências (workout_exercises RESTRICT + session_set_logs soft) pro
-- sobrevivente e DELETE do duplicado (padrão da V15). Sobrevivente = is_base > visível > id.
-- Preserva mídia: se o sobrevivente está sem vídeo, herda do duplicado (muitas bases da
-- V16 entraram com video_ref/thumb_ref vazios; o original do seed V4 tem a mídia).

-- Bom dia: mantém df942cd6
UPDATE exercises SET video_ref=(SELECT video_ref FROM exercises WHERE id='77fcc02c-f1b8-554b-a0b7-bb0f803eb5e0'), thumb_ref=(SELECT thumb_ref FROM exercises WHERE id='77fcc02c-f1b8-554b-a0b7-bb0f803eb5e0') WHERE id='df942cd6-28b2-54a8-9b54-7a32db854beb' AND (video_ref IS NULL OR video_ref='');
UPDATE workout_exercises SET exercise_id='df942cd6-28b2-54a8-9b54-7a32db854beb' WHERE exercise_id='77fcc02c-f1b8-554b-a0b7-bb0f803eb5e0';
UPDATE session_set_logs  SET exercise_id='df942cd6-28b2-54a8-9b54-7a32db854beb' WHERE exercise_id='77fcc02c-f1b8-554b-a0b7-bb0f803eb5e0';
DELETE FROM exercises WHERE id='77fcc02c-f1b8-554b-a0b7-bb0f803eb5e0';

-- Cadeira flexora: mantém a0b7125a
UPDATE exercises SET video_ref=(SELECT video_ref FROM exercises WHERE id='5909d5ce-7ae5-5449-9364-420240424168'), thumb_ref=(SELECT thumb_ref FROM exercises WHERE id='5909d5ce-7ae5-5449-9364-420240424168') WHERE id='a0b7125a-5dc9-5413-95d2-254627588fd0' AND (video_ref IS NULL OR video_ref='');
UPDATE workout_exercises SET exercise_id='a0b7125a-5dc9-5413-95d2-254627588fd0' WHERE exercise_id='5909d5ce-7ae5-5449-9364-420240424168';
UPDATE session_set_logs  SET exercise_id='a0b7125a-5dc9-5413-95d2-254627588fd0' WHERE exercise_id='5909d5ce-7ae5-5449-9364-420240424168';
DELETE FROM exercises WHERE id='5909d5ce-7ae5-5449-9364-420240424168';

-- Desenvolvimento Arnold: mantém 9af4deba
UPDATE exercises SET video_ref=(SELECT video_ref FROM exercises WHERE id='f3d6dc9d-e05a-53c2-a1f2-754aae32cac1'), thumb_ref=(SELECT thumb_ref FROM exercises WHERE id='f3d6dc9d-e05a-53c2-a1f2-754aae32cac1') WHERE id='9af4deba-30c1-534c-91ae-cdf7bcc5c33c' AND (video_ref IS NULL OR video_ref='');
UPDATE workout_exercises SET exercise_id='9af4deba-30c1-534c-91ae-cdf7bcc5c33c' WHERE exercise_id='f3d6dc9d-e05a-53c2-a1f2-754aae32cac1';
UPDATE session_set_logs  SET exercise_id='9af4deba-30c1-534c-91ae-cdf7bcc5c33c' WHERE exercise_id='f3d6dc9d-e05a-53c2-a1f2-754aae32cac1';
DELETE FROM exercises WHERE id='f3d6dc9d-e05a-53c2-a1f2-754aae32cac1';

-- Elevação de panturrilha em pé: mantém e63cb82e
UPDATE exercises SET video_ref=(SELECT video_ref FROM exercises WHERE id='3dce0442-b15a-5c38-b354-b18b00244573'), thumb_ref=(SELECT thumb_ref FROM exercises WHERE id='3dce0442-b15a-5c38-b354-b18b00244573') WHERE id='e63cb82e-e44d-5d58-9312-e0ac9ba442ba' AND (video_ref IS NULL OR video_ref='');
UPDATE workout_exercises SET exercise_id='e63cb82e-e44d-5d58-9312-e0ac9ba442ba' WHERE exercise_id='3dce0442-b15a-5c38-b354-b18b00244573';
UPDATE session_set_logs  SET exercise_id='e63cb82e-e44d-5d58-9312-e0ac9ba442ba' WHERE exercise_id='3dce0442-b15a-5c38-b354-b18b00244573';
DELETE FROM exercises WHERE id='3dce0442-b15a-5c38-b354-b18b00244573';

-- Elevação lateral na máquina: mantém 5696f95f
UPDATE exercises SET video_ref=(SELECT video_ref FROM exercises WHERE id='9480d492-a678-5297-9675-088fc7614aed'), thumb_ref=(SELECT thumb_ref FROM exercises WHERE id='9480d492-a678-5297-9675-088fc7614aed') WHERE id='5696f95f-61d9-5804-8748-098f2f907ac7' AND (video_ref IS NULL OR video_ref='');
UPDATE workout_exercises SET exercise_id='5696f95f-61d9-5804-8748-098f2f907ac7' WHERE exercise_id='9480d492-a678-5297-9675-088fc7614aed';
UPDATE session_set_logs  SET exercise_id='5696f95f-61d9-5804-8748-098f2f907ac7' WHERE exercise_id='9480d492-a678-5297-9675-088fc7614aed';
DELETE FROM exercises WHERE id='9480d492-a678-5297-9675-088fc7614aed';

-- Flexão Nórdica: mantém db17b9b6
UPDATE exercises SET video_ref=(SELECT video_ref FROM exercises WHERE id='32b5c51a-31d0-5809-a50a-316cf93a1323'), thumb_ref=(SELECT thumb_ref FROM exercises WHERE id='32b5c51a-31d0-5809-a50a-316cf93a1323') WHERE id='db17b9b6-6425-5929-babe-ba61b3c40eca' AND (video_ref IS NULL OR video_ref='');
UPDATE workout_exercises SET exercise_id='db17b9b6-6425-5929-babe-ba61b3c40eca' WHERE exercise_id='32b5c51a-31d0-5809-a50a-316cf93a1323';
UPDATE session_set_logs  SET exercise_id='db17b9b6-6425-5929-babe-ba61b3c40eca' WHERE exercise_id='32b5c51a-31d0-5809-a50a-316cf93a1323';
DELETE FROM exercises WHERE id='32b5c51a-31d0-5809-a50a-316cf93a1323';
UPDATE exercises SET video_ref=(SELECT video_ref FROM exercises WHERE id='29b1fd1f-c684-5f96-ad92-5871fb501f63'), thumb_ref=(SELECT thumb_ref FROM exercises WHERE id='29b1fd1f-c684-5f96-ad92-5871fb501f63') WHERE id='db17b9b6-6425-5929-babe-ba61b3c40eca' AND (video_ref IS NULL OR video_ref='');
UPDATE workout_exercises SET exercise_id='db17b9b6-6425-5929-babe-ba61b3c40eca' WHERE exercise_id='29b1fd1f-c684-5f96-ad92-5871fb501f63';
UPDATE session_set_logs  SET exercise_id='db17b9b6-6425-5929-babe-ba61b3c40eca' WHERE exercise_id='29b1fd1f-c684-5f96-ad92-5871fb501f63';
DELETE FROM exercises WHERE id='29b1fd1f-c684-5f96-ad92-5871fb501f63';

-- Glúteo Coice na Máquina: mantém 50be13bb
UPDATE exercises SET video_ref=(SELECT video_ref FROM exercises WHERE id='55927e2e-d696-5943-8ab3-4fefd015abbb'), thumb_ref=(SELECT thumb_ref FROM exercises WHERE id='55927e2e-d696-5943-8ab3-4fefd015abbb') WHERE id='50be13bb-03bb-56d3-a8f1-4a8cf068de4a' AND (video_ref IS NULL OR video_ref='');
UPDATE workout_exercises SET exercise_id='50be13bb-03bb-56d3-a8f1-4a8cf068de4a' WHERE exercise_id='55927e2e-d696-5943-8ab3-4fefd015abbb';
UPDATE session_set_logs  SET exercise_id='50be13bb-03bb-56d3-a8f1-4a8cf068de4a' WHERE exercise_id='55927e2e-d696-5943-8ab3-4fefd015abbb';
DELETE FROM exercises WHERE id='55927e2e-d696-5943-8ab3-4fefd015abbb';

-- Mesa flexora: mantém 58b85b43
UPDATE exercises SET video_ref=(SELECT video_ref FROM exercises WHERE id='3415237c-4c98-5cb6-8316-0936da363d02'), thumb_ref=(SELECT thumb_ref FROM exercises WHERE id='3415237c-4c98-5cb6-8316-0936da363d02') WHERE id='58b85b43-d4cc-5829-b144-493787ef4620' AND (video_ref IS NULL OR video_ref='');
UPDATE workout_exercises SET exercise_id='58b85b43-d4cc-5829-b144-493787ef4620' WHERE exercise_id='3415237c-4c98-5cb6-8316-0936da363d02';
UPDATE session_set_logs  SET exercise_id='58b85b43-d4cc-5829-b144-493787ef4620' WHERE exercise_id='3415237c-4c98-5cb6-8316-0936da363d02';
DELETE FROM exercises WHERE id='3415237c-4c98-5cb6-8316-0936da363d02';

-- Prancha Lateral: mantém 250ddb2b
UPDATE exercises SET video_ref=(SELECT video_ref FROM exercises WHERE id='524e4b5c-0ae9-45c2-b213-3382a4cf91a8'), thumb_ref=(SELECT thumb_ref FROM exercises WHERE id='524e4b5c-0ae9-45c2-b213-3382a4cf91a8') WHERE id='250ddb2b-6e59-5222-a4fc-16ba85d57bcc' AND (video_ref IS NULL OR video_ref='');
UPDATE workout_exercises SET exercise_id='250ddb2b-6e59-5222-a4fc-16ba85d57bcc' WHERE exercise_id='524e4b5c-0ae9-45c2-b213-3382a4cf91a8';
UPDATE session_set_logs  SET exercise_id='250ddb2b-6e59-5222-a4fc-16ba85d57bcc' WHERE exercise_id='524e4b5c-0ae9-45c2-b213-3382a4cf91a8';
DELETE FROM exercises WHERE id='524e4b5c-0ae9-45c2-b213-3382a4cf91a8';

-- Remada Inclinada a 45 Graus: mantém 9f833ee9
UPDATE exercises SET video_ref=(SELECT video_ref FROM exercises WHERE id='f200d600-01eb-55ed-ae4d-225bf43ef2c0'), thumb_ref=(SELECT thumb_ref FROM exercises WHERE id='f200d600-01eb-55ed-ae4d-225bf43ef2c0') WHERE id='9f833ee9-1a11-5b3f-a07d-34c840a0288f' AND (video_ref IS NULL OR video_ref='');
UPDATE workout_exercises SET exercise_id='9f833ee9-1a11-5b3f-a07d-34c840a0288f' WHERE exercise_id='f200d600-01eb-55ed-ae4d-225bf43ef2c0';
UPDATE session_set_logs  SET exercise_id='9f833ee9-1a11-5b3f-a07d-34c840a0288f' WHERE exercise_id='f200d600-01eb-55ed-ae4d-225bf43ef2c0';
DELETE FROM exercises WHERE id='f200d600-01eb-55ed-ae4d-225bf43ef2c0';

-- Stiff com barra: mantém 30a50289
UPDATE exercises SET video_ref=(SELECT video_ref FROM exercises WHERE id='43fbfbca-ec68-5549-ad3f-d9ebba64d2f0'), thumb_ref=(SELECT thumb_ref FROM exercises WHERE id='43fbfbca-ec68-5549-ad3f-d9ebba64d2f0') WHERE id='30a50289-16a6-58df-962f-e6197078f449' AND (video_ref IS NULL OR video_ref='');
UPDATE workout_exercises SET exercise_id='30a50289-16a6-58df-962f-e6197078f449' WHERE exercise_id='43fbfbca-ec68-5549-ad3f-d9ebba64d2f0';
UPDATE session_set_logs  SET exercise_id='30a50289-16a6-58df-962f-e6197078f449' WHERE exercise_id='43fbfbca-ec68-5549-ad3f-d9ebba64d2f0';
DELETE FROM exercises WHERE id='43fbfbca-ec68-5549-ad3f-d9ebba64d2f0';

-- Supino inclinado na Máquina: mantém bf50b1f9
UPDATE exercises SET video_ref=(SELECT video_ref FROM exercises WHERE id='ef49c17e-c771-55f8-b87f-332090431261'), thumb_ref=(SELECT thumb_ref FROM exercises WHERE id='ef49c17e-c771-55f8-b87f-332090431261') WHERE id='bf50b1f9-7467-502c-a7e6-4090ba56db8d' AND (video_ref IS NULL OR video_ref='');
UPDATE workout_exercises SET exercise_id='bf50b1f9-7467-502c-a7e6-4090ba56db8d' WHERE exercise_id='ef49c17e-c771-55f8-b87f-332090431261';
UPDATE session_set_logs  SET exercise_id='bf50b1f9-7467-502c-a7e6-4090ba56db8d' WHERE exercise_id='ef49c17e-c771-55f8-b87f-332090431261';
DELETE FROM exercises WHERE id='ef49c17e-c771-55f8-b87f-332090431261';

-- Supino na Máquina: mantém 131db371
UPDATE exercises SET video_ref=(SELECT video_ref FROM exercises WHERE id='be967d64-1d64-54c3-8668-8a8cc35f4e71'), thumb_ref=(SELECT thumb_ref FROM exercises WHERE id='be967d64-1d64-54c3-8668-8a8cc35f4e71') WHERE id='131db371-64fd-5e01-843f-a5fb7560b329' AND (video_ref IS NULL OR video_ref='');
UPDATE workout_exercises SET exercise_id='131db371-64fd-5e01-843f-a5fb7560b329' WHERE exercise_id='be967d64-1d64-54c3-8668-8a8cc35f4e71';
UPDATE session_set_logs  SET exercise_id='131db371-64fd-5e01-843f-a5fb7560b329' WHERE exercise_id='be967d64-1d64-54c3-8668-8a8cc35f4e71';
DELETE FROM exercises WHERE id='be967d64-1d64-54c3-8668-8a8cc35f4e71';

-- Tríceps no Banco: mantém cb1e8843
UPDATE exercises SET video_ref=(SELECT video_ref FROM exercises WHERE id='94c2c728-09da-51b9-838b-724d2020b90f'), thumb_ref=(SELECT thumb_ref FROM exercises WHERE id='94c2c728-09da-51b9-838b-724d2020b90f') WHERE id='cb1e8843-2491-5594-9f77-19c945490553' AND (video_ref IS NULL OR video_ref='');
UPDATE workout_exercises SET exercise_id='cb1e8843-2491-5594-9f77-19c945490553' WHERE exercise_id='94c2c728-09da-51b9-838b-724d2020b90f';
UPDATE session_set_logs  SET exercise_id='cb1e8843-2491-5594-9f77-19c945490553' WHERE exercise_id='94c2c728-09da-51b9-838b-724d2020b90f';
DELETE FROM exercises WHERE id='94c2c728-09da-51b9-838b-724d2020b90f';

-- Voador na Máquina: mantém c0229281
UPDATE exercises SET video_ref=(SELECT video_ref FROM exercises WHERE id='824e23e7-51b9-5396-b4d0-5218f48818d9'), thumb_ref=(SELECT thumb_ref FROM exercises WHERE id='824e23e7-51b9-5396-b4d0-5218f48818d9') WHERE id='c0229281-4eb3-55f1-bab5-b7f0c22c109f' AND (video_ref IS NULL OR video_ref='');
UPDATE workout_exercises SET exercise_id='c0229281-4eb3-55f1-bab5-b7f0c22c109f' WHERE exercise_id='824e23e7-51b9-5396-b4d0-5218f48818d9';
UPDATE session_set_logs  SET exercise_id='c0229281-4eb3-55f1-bab5-b7f0c22c109f' WHERE exercise_id='824e23e7-51b9-5396-b4d0-5218f48818d9';
DELETE FROM exercises WHERE id='824e23e7-51b9-5396-b4d0-5218f48818d9';
