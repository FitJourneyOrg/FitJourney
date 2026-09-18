-- Tradução do catálogo de exercícios (fatia H, ARCH #37).
--
-- ============================================================================
-- POR QUE TABELA, E NÃO COLUNAS `name_en` / `name_es` / ...
-- ============================================================================
--
-- A alternativa era acrescentar duas colunas por idioma em `exercises`, e ela tem uma vantagem
-- REAL que esta não tem: o `when (idioma)` da leitura ficaria exaustivo, e idioma novo no enum
-- `Idioma` quebraria o build até alguém traduzir. É o mesmo mecanismo que o `TextosDeAviso` usa e
-- que o #37 celebra.
--
-- Perdemos isso de propósito, por causa de um número: **o app vai ter mais de quatro idiomas**.
-- Com dez, aquela vantagem vira duas coisas ruins: vinte colunas em `exercises`, e um build
-- vermelho que impede lançar QUALQUER idioma enquanto o último não estiver traduzido.
--
-- > A escolha entre coluna e tabela não é de modelagem, é de quantos.
--
-- O que a tabela custa está escrito e coberto: sem o compilador, a guarda passa a ser o
-- `CatalogoTraduzidoIntegrationTest`, que percorre `Idioma.TODOS` e cobra uma linha por exercício
-- em cada idioma. Invariante de teste em vez de invariante de tipo — mais fraca, e é o preço.
--
--
-- ============================================================================
-- O pt-BR NÃO ENTRA AQUI. `exercises.name` CONTINUA SENDO O PISO.
-- ============================================================================
--
-- A modelagem "pura" moveria os 963 nomes para cá e deixaria `exercises` sem texto. Recusado:
-- custaria uma migration de dado sobre a coluna que o app inteiro já lê, e o ganho seria estético.
--
-- Deixando o pt-BR onde está, a coluna antiga assume o papel que o `Idioma.PADRAO` já tem no
-- `IdiomaPolicy`: o valor que a cadeia de fallback alcança quando nada mais bate. A leitura é um
-- LEFT JOIN com `COALESCE` — uma linha, e o mesmo desenho que o cliente já usa para o
-- `values-en` cair no `values/`.
--
-- > O piso não precisa ser uma linha na tabela de traduções: precisa existir sempre.
CREATE TABLE exercise_translations (
    exercise_id UUID         NOT NULL REFERENCES exercises(id) ON DELETE CASCADE,
    locale      VARCHAR(5)   NOT NULL,
    name        VARCHAR(200) NOT NULL,

    -- Nullable de propósito: dá para traduzir o NOME de 963 exercícios numa leva e deixar as
    -- descrições para depois (elas são parágrafos, não termos). Sem isto, a fatia do nome ficaria
    -- refém da fatia da descrição.
    description TEXT,

    PRIMARY KEY (exercise_id, locale)
);


-- ============================================================================
-- CHECK DE VOCABULÁRIO FECHADO, ESPELHANDO A V47
-- ============================================================================
--
-- Mesma razão escrita lá, e ela não é o Kotlin: o CHECK vale para escrita DIRETA — script, seed,
-- correção manual em produção — que é exatamente onde o código não alcança. E é por script que as
-- traduções vão entrar.
--
-- ⚠️ Este CHECK espelha o enum `Idioma` do `shared-contract`, como o de `users.locale`. Idioma
-- novo lá sem migration aqui faz o INSERT da carga estourar. O `CatalogoTraduzidoIntegrationTest`
-- afirma que os dois lados listam o mesmo conjunto.
--
-- Idioma novo é DROP + ADD afrouxando o vocabulário — seguro sobre dado existente, porque nenhuma
-- linha atual pode violar um conjunto maior. Mesmo movimento da V46 em `moderation_actions`.
ALTER TABLE exercise_translations
    ADD CONSTRAINT exercise_translations_locale_suportado CHECK (locale IN ('pt-BR', 'en'));


-- ============================================================================
-- ÍNDICE POR `locale`
-- ============================================================================
--
-- A PK é (exercise_id, locale), ótima para "a tradução DESTE exercício". Mas a consulta que domina
-- é a outra: **todas as traduções de um locale**, porque o cliente baixa o catálogo inteiro de uma
-- vez (`replaceAll`, ~963 linhas) e não pede exercício a exercício.
--
-- Sem este índice, o LEFT JOIN filtrado por locale varre a tabela toda a cada sincronização.
CREATE INDEX idx_exercise_translations_locale ON exercise_translations (locale);


-- Nenhuma linha é inserida aqui. A carga dos ~960 nomes em inglês é migration própria (H.3), e ela
-- só existe depois que a tradução chegar revisada — o mesmo portão que a G.3 impôs à G.4.
