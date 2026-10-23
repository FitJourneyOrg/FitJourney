-- V66 -- acessorios do usuario: o que ele tem alem do equipamento de carga.
--
-- ============================================================================
-- O DEFEITO
-- ============================================================================
-- A V65 deu a cada exercicio seis colunas `requires_*` (banco, superficie elevada, barra fixa,
-- paralelas, step, ancoragem), mas o perfil nao tem onde guardar o que o usuario possui. Sem o
-- outro lado do par, as seis colunas nao filtram ninguem: o motor continua prescrevendo supino
-- com halteres para quem treina na sala, sem banco.
--
-- Medido no simulador (tools/motor_sim), varrendo os 180 perfis de casa:
--   sem acessorio declarado ..... 131 vagas sem exercicio
--   com cadeira ................. 45
--   com cadeira e banco ......... 0
-- O banco e o acessorio que mais pesa, e hoje o app nao pergunta: todo usuario de casa cai no
-- pior caso.
--
-- ============================================================================
-- A DECISAO
-- ============================================================================
-- JSON em TEXT, como focus_areas, limitations e unavailable_days (V2, V8, V19). Nao sao seis
-- colunas booleanas em `profiles` porque, deste lado, a lista e lida pela app e pelo motor em
-- codigo -- nao e filtrada em SQL. Em `exercises` sao colunas porque la o filtro e por exercicio
-- e pode ir para o WHERE.
--
-- NULL = o usuario nunca respondeu (cliente antigo, perfil anterior a esta versao).
-- '[]'  = respondeu e nao tem nada.
-- Os dois sao tratados igual pelo motor hoje; a diferenca existe para a app saber se precisa
-- perguntar.
--
-- ACADEMIA nao preenche: quem treina em academia tem os seis por definicao, e isso e regra de
-- codigo (Accessory.paraAmbiente), nao dado duplicado em 100 mil linhas de perfil.

ALTER TABLE profiles ADD COLUMN accessories TEXT;

COMMENT ON COLUMN profiles.accessories IS
    'JSON de Accessory (contrato): BENCH, ELEVATED_SURFACE, PULLUP_BAR, DIP_STATION, STEP, '
    'ANCHOR_POINT. Espelha as colunas requires_* de exercises. NULL = nunca perguntado; '
    '[] = perguntado e nao tem nada. Academia nao preenche: tem os seis por definicao.';
