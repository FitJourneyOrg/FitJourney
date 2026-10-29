-- V67 -- RIR minimo de seguranca: piso de RIR 2 por propriedade, nao por lista (motor v3,
-- fatia 7, 2026-10-24).
--
-- ============================================================================
-- O DEFEITO
-- ============================================================================
-- A regra da especificacao e "RIR minimo 2 em todo exercicio com barra livre que carregue a
-- coluna ou passe sobre a cabeca", "por propriedade, nao por lista" -- correcao da quinta rodada,
-- porque a enumeracao anterior (supino, agachamento, desenvolvimento, terra, stiff) deixava de
-- fora 12 dos 20 exercicios com barra do catalogo, incluindo tres de risco: Afundo com Barra,
-- Remada Curvada com Barra e Remada Cavalinho.
--
-- Nenhuma coluna existente captura essa propriedade sozinha:
--   * movement_pattern nao serve: HINGE pega a Elevacao Pelvica com Barra (mesmo padrao do
--     Stiff, mas a barra fica no quadril, aluno deitado, falhar nao arrisca a coluna) e NAO pega
--     a Remada Curvada com Barra (HORIZONTAL_PULL) -- o caso que motivou a troca da enumeracao
--     pela propriedade. VERTICAL_PUSH tambem nao significa "sobre a cabeca": mergulhos sao
--     VERTICAL_PUSH e a carga nao passa sobre a cabeca.
--   * contraindications pega LUMBAR em exercicios de maquina/Smith (trajetoria guiada, falha
--     segura) e em casos sem relacao com este risco -- e e dado de aviso ao usuario desde a
--     decisao de 21/10, nao decisao do motor; o motor nao le essa coluna em tempo de execucao.
--
-- ============================================================================
-- A DECISAO
-- ============================================================================
-- Coluna booleana propria, so do servidor, igual em espirito a volume_target (V64) e as
-- requires_* (V65): "e risco de RIR minimo" passa a ser propriedade do schema, conferida uma
-- vez por nome contra a lista principal (147), nao recalculada pelo motor a cada execucao.
-- contraindications foi usado apenas aqui, OFFLINE, para montar a lista candidata que o Rafael
-- aprovou por nome -- o motor em tempo de execucao le so esta coluna nova.
--
-- Aprovados (peso livre com barra ou landmine, carga na coluna ou sobre a cabeca; halteres e
-- maquina/Smith ficam de fora -- na falha o halter solta e a maquina guia a trajetoria):
--   Agachamento Livre com Barra, Afundo com Barra, Levantamento Terra, Stiff com Barra,
--   Remada Curvada com Barra, Remada Cavalinho no Landmine, Desenvolvimento Militar com Barra.
-- Mais Flexao em Parada de Mao na Parede: sem carga externa, mas falhar e cair de cabeca -- o
-- unico caso em que o peso do corpo carrega o mesmo risco dos sete de cima.
-- Todo o resto da lista principal (147) fica false.
--
-- false (default) = nao se aplica ou nunca avaliado (exercicio fora da lista principal).
-- O motor so olha para esta coluna; nao olha para contraindications, movement_pattern nem
-- equipment para decidir o piso de RIR.

ALTER TABLE exercises ADD COLUMN rir_minimo_seguranca BOOLEAN NOT NULL DEFAULT false;

COMMENT ON COLUMN exercises.rir_minimo_seguranca IS
    'RIR minimo 2 em qualquer semana do mesociclo (motor v3, fatia 7): peso livre com barra ou '
    'landmine que carrega a coluna ou passa sobre a cabeca, aprovado por nome pelo Rafael a '
    'partir de contraindications + julgamento (nao e derivavel de movement_pattern/equipment). '
    'false = nao se aplica. Decide tambem a troca por halter/maquina para o iniciante.';

UPDATE exercises SET rir_minimo_seguranca = true WHERE name IN (
    'Agachamento Livre com Barra',
    'Afundo com Barra',
    'Levantamento Terra',
    'Stiff com Barra',
    'Remada Curvada com Barra',
    'Remada Cavalinho no Landmine',
    'Desenvolvimento Militar com Barra',
    'Flexão em Parada de Mão na Parede');

-- Autoconferencia: nome que nao casar derruba a migration aqui, em vez de deixar a lista
-- aprovada incompleta em silencio (mesmo padrao da V64).
DO $$
DECLARE
    achado INTEGER;
BEGIN
    SELECT count(*) INTO achado FROM exercises WHERE rir_minimo_seguranca = true;
    IF achado IS DISTINCT FROM 8 THEN
        RAISE EXCEPTION 'V67: esperado 8 exercicios com rir_minimo_seguranca=true, achado %', achado;
    END IF;
END $$;
-- total marcado true: 8
