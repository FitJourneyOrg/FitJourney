-- Nome de exibição do usuário (ARCH #33, fatia A.0).
--
-- Por que só agora: até a Fase 5 o app era INDIVIDUAL. Ninguém se via numa lista com outras
-- pessoas, então o usuário nunca precisou de nome. A Fase 6 (grupos, ranking, check-in,
-- comentário, denúncia) é inteiramente sobre dizer QUEM — e sem esta coluna a única
-- identificação disponível seria o e-mail, exposto a até 49 desconhecidos.
--
-- Por que NOT NULL e não nullable: coluna nullable espalharia `?: "Usuário"` por toda tela que
-- mostra gente, e uma delas seria esquecida — defeito que só aparece quando um usuário antigo
-- abre um grupo. Aqui a garantia é do banco, não da disciplina de quem escreve a tela.
--
-- Sem UNIQUE de propósito: nome de pessoa não é identificador. Duas pessoas podem se chamar
-- "Rafael"; forçar "Rafael2" seria hostil, e num grupo de até 50 membros que se conhecem a
-- ambiguidade é rara e se resolve socialmente. Quem identifica é o `id`.

-- 1. Entra nullable para que o backfill possa rodar.
ALTER TABLE users ADD COLUMN display_name VARCHAR(30);

-- 2. Backfill. A MESMA regra vive em DisplayNamePolicy.inicial() no Kotlin — as duas precisam
--    concordar, senão um usuário criado hoje ganha nome diferente de um migrado ontem.
--    Parte local do e-mail (antes do @); se não der (e-mail nulo, ou local com menos de 2
--    caracteres), cai em "Atleta-" + 6 primeiros hex do id.
UPDATE users
SET display_name = CASE
    WHEN email IS NOT NULL AND char_length(btrim(split_part(email, '@', 1))) >= 2
        THEN left(btrim(split_part(email, '@', 1)), 30)
    ELSE 'Atleta-' || left(replace(id::text, '-', ''), 6)
END
WHERE display_name IS NULL;

-- 3. Agora a garantia pode ser cravada.
ALTER TABLE users ALTER COLUMN display_name SET NOT NULL;

-- O CHECK existe para o caso que o Kotlin não cobre: escrita direta no banco (script, psql,
-- correção manual). O limite superior já vem do VARCHAR(30); o inferior é o que impede nome
-- vazio ou de um caractere só, que na tela vira uma linha sem dono.
ALTER TABLE users
    ADD CONSTRAINT users_display_name_len CHECK (char_length(btrim(display_name)) BETWEEN 2 AND 30);
