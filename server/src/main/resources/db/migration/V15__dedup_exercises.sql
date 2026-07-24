-- V15: dedup de exercícios (merge conservador de duplicatas funcionais).
-- Reaponta treinos que usem a duplicata pro sobrevivente, depois apaga a duplicata.
-- Só duplicatas de ALTA confiança; variações (reto/inclinado, pegadas, Smith/máquina) mantidas.

-- Barra fixa (colisão exata): mantém f45b280a / remove 56ce0d81 (ex "Pull Up" renomeado na V14)
UPDATE workout_exercises SET exercise_id = 'f45b280a-edf2-5366-9600-2bcd47d8fd42' WHERE exercise_id = '56ce0d81-1daa-5855-b0cb-cbc47728a515';
DELETE FROM exercises WHERE id = '56ce0d81-1daa-5855-b0cb-cbc47728a515';

-- Encolhimento na Máquina (colisão exata): mantém a94bd8de / remove 11d22f59 (ex "Alavanca" renomeado na V14)
UPDATE workout_exercises SET exercise_id = 'a94bd8de-458e-5bd0-917d-203e26404a63' WHERE exercise_id = '11d22f59-7aa8-56a1-8123-4011b49ca57b';
DELETE FROM exercises WHERE id = '11d22f59-7aa8-56a1-8123-4011b49ca57b';

-- Ponte de Glúteos = Elevação de Quadril com Peso Corporal
UPDATE workout_exercises SET exercise_id = 'ebfce553-16d7-5cf9-aea0-e6b861da1cc4' WHERE exercise_id = '17baba1a-141b-5190-a229-4db6d07706d6';
DELETE FROM exercises WHERE id = '17baba1a-141b-5190-a229-4db6d07706d6';

-- Remada Renegada = Remada com Halteres em Posição Prancha
UPDATE workout_exercises SET exercise_id = '03a6a766-7e34-5dd2-8a8c-cb4457534a7c' WHERE exercise_id = 'f3cf9abd-ad99-5579-af53-01a05b0a9f05';
DELETE FROM exercises WHERE id = 'f3cf9abd-ad99-5579-af53-01a05b0a9f05';

-- Dips na cadeira = Mergulho reverso
UPDATE workout_exercises SET exercise_id = '41ad8f1f-f88e-500e-a2b0-28c53fb1c30f' WHERE exercise_id = '9b2690a2-48bb-5198-ac8c-bc3a7f4d0a9f';
DELETE FROM exercises WHERE id = '9b2690a2-48bb-5198-ac8c-bc3a7f4d0a9f';

-- Hiperextensão = Hiperextensão de Lombar no Banco Plano
UPDATE workout_exercises SET exercise_id = '2a0a3579-f68f-590e-a0b1-37d840b7df2b' WHERE exercise_id = '09ef45ce-2a83-5bd0-b994-65995174dcaa';
DELETE FROM exercises WHERE id = '09ef45ce-2a83-5bd0-b994-65995174dcaa';
