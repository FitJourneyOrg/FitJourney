-- V23: Fatia B.2 — MuscleGroup desmembrado no contrato (ARMS -> BICEPS/TRICEPS/FOREARMS).
-- O enum `ARMS` saiu do MuscleGroup (contrato/quiz). Esta migration re-tagueia o DADO que
-- ainda usava a string 'ARMS' em arrays TEXT[], senão o mapper (runCatching valueOf) dropava
-- silenciosamente e o bônus de foco por primary quebrava.
--
-- Regra de re-tag do primary: o único sinal confiável de QUAL braço é a `category`
-- (BICEPS/TRICEPS/FOREARMS já existem lá). Para secondary de composto (supino->tríceps,
-- puxada->bíceps) não dá p/ inferir pela category, e o motor NÃO usa secondary hoje -> DROP.

-- 1) primary_muscles: 'ARMS' -> sub-músculo, derivado da category do exercício.
UPDATE exercises SET primary_muscles = array_replace(primary_muscles, 'ARMS', 'BICEPS')
    WHERE 'ARMS' = ANY(primary_muscles) AND category = 'BICEPS';
UPDATE exercises SET primary_muscles = array_replace(primary_muscles, 'ARMS', 'TRICEPS')
    WHERE 'ARMS' = ANY(primary_muscles) AND category = 'TRICEPS';
UPDATE exercises SET primary_muscles = array_replace(primary_muscles, 'ARMS', 'FOREARMS')
    WHERE 'ARMS' = ANY(primary_muscles) AND category = 'FOREARMS';

-- 2) sobras: primary com 'ARMS' mas category não-braço (raro) -> remove (o enum não existe mais).
UPDATE exercises SET primary_muscles = array_remove(primary_muscles, 'ARMS')
    WHERE 'ARMS' = ANY(primary_muscles);

-- 3) secondary_muscles: dropa 'ARMS' (motor não usa; inferência bi/tri em composto é ambígua).
UPDATE exercises SET secondary_muscles = array_remove(secondary_muscles, 'ARMS')
    WHERE 'ARMS' = ANY(secondary_muscles);

-- 4) profiles: foco salvo com "ARMS" no JSON -> "BICEPS" (evita quebrar a desserialização do
--    perfil quando o enum não tem mais ARMS). Dev: mapeamento default; perde tríceps/antebraço.
UPDATE profiles SET focus_areas = replace(focus_areas, '"ARMS"', '"BICEPS"')
    WHERE focus_areas LIKE '%"ARMS"%';
