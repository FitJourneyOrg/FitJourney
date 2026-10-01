-- Fase 8 -- "Aprender": o acervo de conteudo do app.
--
-- ============================================================================
-- A MESMA MODELAGEM DE TRADUCAO DA V49, PELA MESMA RAZAO
-- ============================================================================
--
-- pt-BR NAO entra em `wiki_article_translations`. `wiki_articles.title`/`body` sao o PISO, como
-- `exercises.name` e, e como o `Idioma.PADRAO` e o piso do `IdiomaPolicy`. A leitura em pt-BR nao
-- faz join nenhum; linha ausente na tabela de traducao nao e erro, e o fallback funcionando.
--
-- O LEFT JOIN da leitura e obrigatorio pelo mesmo motivo escrito na V49: com INNER, artigo ainda
-- nao traduzido SUMIRIA do acervo quando a pessoa trocasse de idioma.
--
--
-- ============================================================================
-- TITULO E CORPO TRADUZIDOS ANDAM JUNTOS -- OS DOIS NOT NULL
-- ============================================================================
--
-- Diferente de `exercise_translations.description`, que e nullable de proposito (dava pra traduzir
-- os 963 NOMES numa leva e deixar as descricoes pra depois), aqui os dois sao obrigatorios na mesma
-- linha. Traducao pela metade num artigo produz titulo em ingles com corpo em portugues na MESMA
-- tela -- que e pior que o artigo inteiro no piso.
--
-- > Traducao parcial de termo e aceitavel; traducao parcial de prosa e defeito visivel.
--
--
-- ============================================================================
-- O `featured` E GARANTIDO POR INDICE, NAO POR CODIGO
-- ============================================================================
--
-- O "COMECE AQUI" da tela e um so. Um indice unico PARCIAL (`WHERE featured`) torna impossivel
-- existirem dois -- inclusive por escrita direta no banco, que e onde o Kotlin nao alcanca e por
-- onde o conteudo entra (o `R__wiki_conteudo.sql`).
--
--
-- ============================================================================
-- `updated_at` E DADO DE CONTEUDO, NAO CARIMBO DE ESCRITA
-- ============================================================================
--
-- Ele vem do frontmatter do arquivo .md e so muda quando o autor revisa o texto. Se fosse
-- `now()` na escrita, o seed repetivel -- que roda a cada deploy em que o conteudo mudou --
-- carimbaria a data de hoje em TODOS os artigos, e a tela ("atualizado jun/26") passaria a mentir
-- sobre todos eles por causa da revisao de um.

CREATE TABLE wiki_articles (
    id          UUID         PRIMARY KEY,
    slug        VARCHAR(80)  NOT NULL UNIQUE,
    category    VARCHAR(16)  NOT NULL,
    title       VARCHAR(200) NOT NULL,
    body        TEXT         NOT NULL,
    featured    BOOLEAN      NOT NULL DEFAULT FALSE,
    order_index INT          NOT NULL,
    updated_at  TIMESTAMP    NOT NULL
);

-- Espelha o enum `WikiCategory` do shared-contract, como o CHECK da V47 espelha o `Idioma`.
-- Categoria nova la sem migration aqui faz a carga do conteudo estourar, que e o que se quer.
ALTER TABLE wiki_articles
    ADD CONSTRAINT wiki_articles_categoria_suportada
    CHECK (category IN ('TRAINING', 'TECHNIQUE', 'NUTRITION', 'RECOVERY', 'MINDSET'));

-- No maximo UM destaque em todo o acervo. Todas as linhas indexadas tem `featured = true`, entao a
-- unicidade sobre a coluna permite exatamente uma.
CREATE UNIQUE INDEX wiki_articles_um_destaque ON wiki_articles (featured) WHERE featured;

CREATE TABLE wiki_article_translations (
    article_id UUID         NOT NULL REFERENCES wiki_articles(id) ON DELETE CASCADE,
    locale     VARCHAR(5)   NOT NULL,
    title      VARCHAR(200) NOT NULL,
    body       TEXT         NOT NULL,

    PRIMARY KEY (article_id, locale)
);

-- Mesmo vocabulario fechado da V47/V49. Idioma novo e DROP + ADD afrouxando o conjunto.
ALTER TABLE wiki_article_translations
    ADD CONSTRAINT wiki_article_translations_locale_suportado CHECK (locale IN ('pt-BR', 'en'));

-- A consulta que domina e "todas as traducoes de um locale": o cliente baixa o acervo inteiro de
-- uma vez, nao artigo a artigo. Mesma razao do indice da V49.
CREATE INDEX idx_wiki_article_translations_locale ON wiki_article_translations (locale);
