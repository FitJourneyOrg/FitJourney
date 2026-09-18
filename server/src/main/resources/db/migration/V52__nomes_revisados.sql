-- Os nomes revisados dos exercícios (fatia H, sessão A, migration de DADO).
--
-- ============================================================================
-- DE ONDE VEM
-- ============================================================================
--
-- A revisão de nomenclatura de 2026-09-17/18 conferiu cada exercício contra 3 quadros do vídeo e
-- reescreveu o nome. **788 dos 900** exercícios com mídia mudam aqui. Os outros 65 do banco não
-- são tocados: 26 nunca tiveram mídia e 39 já saíram na V51.
--
-- Os 788 se dividem assim, e a primeira linha é a que justifica a migration sozinha:
--
--   *  44  ERRO          o nome descrevia OUTRO exercício. Conferidos quadro a quadro.
--   * 345  AJUSTE        exercício certo, nome impreciso (faltava equipamento, lado, ângulo)
--   * 422  PADRONIZAÇÃO  mesmo exercício, mudou redação, acento ou ordem dos termos
--
-- O caso que mostra o tamanho do problema: `Flexão de Cotovelos na Barra` não descrevia nada que
-- existisse — é `Extensão de Tríceps com Peso Corporal na Barra`. E `Elevação Lateral de Perna
-- com Faixa Elástica` era, no vídeo, um fire hydrant.
--
-- > Nome errado não é texto ruim: é o usuário procurando um exercício e encontrando outro.
--
--
-- ============================================================================
-- POR ID, NUNCA POR NOME
-- ============================================================================
--
-- O nome é exatamente o que esta migration muda. Casar por ele faria a instrução depender do
-- estado que ela própria corrige — e a V14, que renomeou 49 exercícios por id, já provou que o
-- nome do banco e o do catálogo andam separados.
--
--
-- ============================================================================
-- OS 9 TRAVESSÕES SAEM AQUI, E NÃO EM MIGRATION PRÓPRIA
-- ============================================================================
--
-- `Liberação Miofascial com Rolo – Isquiotibiais` e mais oito irmãos usavam travessão curto (–),
-- proibido pelo ARCH #37 em texto de usuário desde 2026-09-10. Viram `Rolo: Isquiotibiais`.
--
-- Eles já estavam entre os 788, então a troca acontece no valor gravado e não custa instrução
-- extra. Mas a lição é outra e vale registrar: **a regra do travessão tem teste varrendo o
-- `strings.xml` inteiro, e nome de exercício mora no banco** — fora do alcance dele. Os nove
-- entrariam pela porta dos fundos.
--
-- > Convenção travada num arquivo não protege o dado que chega por outro caminho.
--
-- A guarda no fim desta migration, mais o teste novo no `CaminhosDeMidiaIntegrationTest`, fecham
-- essa porta.
--
-- Idempotente: reescreve para o mesmo valor se rodar de novo.
UPDATE exercises AS e
   SET name = m.nome
  FROM (VALUES
    ('29fb0950-dc61-5dc1-a858-45d4be82723d'::uuid, 'Abdominal Grupado com Bola Suíça'),   -- ERRO | Abdominal de Rã com Bola de Exercícios
    ('4afcf90e-91f8-588e-87e9-04e15c046250'::uuid, 'Abdução de Quadril em Pé na Máquina (Alavanca)'),   -- PADR | Abdução Lateral do Quadril na Máquina
    ('4bc194b3-2774-5cb1-b2f7-7c61dfac9466'::uuid, 'Abdução de Quadril em Prancha Lateral'),   -- AJUS | Abdução de Quadril Lateral
    ('4a14d6e4-a137-5992-8c10-784dafc212ae'::uuid, 'Abdução de Quadril Deitado de Lado com Miniband'),   -- AJUS | Abdução de Quadril com Faixa
    ('61fb4647-fdd7-54e0-9122-617b5d49452f'::uuid, 'Ponte de Glúteos com Abdução de Quadril'),   -- PADR | Abdução de Quadril com Ponte
    ('0bc8d3cf-891f-5e72-b804-af2ce67322a2'::uuid, 'Concha em Decúbito Lateral (Clamshell)'),   -- AJUS | Abdução de Quadril em Decúbito Lateral
    ('be8eb716-8316-5ea4-b383-f2850a903df9'::uuid, 'Cadeira Abdutora'),   -- AJUS | Abdução de Quadril na Máquina
    ('5d30f49d-f617-57f5-889c-e8e42c639b9e'::uuid, 'Abdução de Quadril em Pé na Polia'),   -- PADR | Abdução de quadril com cabo
    ('772f6d66-ef9e-5d91-9f6e-d3a2f891fad9'::uuid, 'Adução de Quadril em Pé na Máquina (Alavanca)'),   -- PADR | Abdução do Quadril Lateral na Máquina
    ('e1fc7d32-85af-5611-bfbc-a96cceb673ea'::uuid, 'Abraço nos Joelhos em Pé'),   -- PADR | Abraços nos Joelhos em Pé
    ('3c166c66-c366-5734-a231-2259fbc98d4e'::uuid, 'Cadeira Adutora'),   -- AJUS | Adução de Quadril na Máquina
    ('d3ecad4f-0844-5aa0-a025-3b57ab8237b0'::uuid, 'Adução de Quadril Deitado de Lado'),   -- PADR | Adução de quadril deitado de lado
    ('1b6ebbcb-372d-57c7-b0ba-c218616a7f37'::uuid, 'Adução de Quadril na Polia'),   -- PADR | Adução do Quadril com Cabo
    ('bb1464e2-3f05-5ce4-a600-658155df7ea6'::uuid, 'Afundo com Peso Corporal'),   -- PADR | Afundo
    ('cac02b24-5064-53d0-a6f4-69bff4a082f4'::uuid, 'Afundo no Cabo com Braços Estendidos'),   -- AJUS | Afundo com Cabo
    ('3746462c-f5f6-504e-9fc5-b70a8e808081'::uuid, 'Afundo no Landmine'),   -- PADR | Afundo com landmine
    ('adad8fea-9428-5a58-82d9-e79935faaea4'::uuid, 'Afundo no Smith'),   -- PADR | Afundo na Máquina Smith
    ('c6b02d3e-eeb9-5c45-875d-bc39c790f380'::uuid, 'Subida no Banco (Step-Up)'),   -- ERRO | Afundo no banco
    ('8a78294e-875f-59c4-bee2-9ca74e63eeb6'::uuid, 'Subida no Banco com Halteres'),   -- ERRO | Afundo no banco com halteres
    ('a5c53651-49c7-5365-822a-8cb8da06bd51'::uuid, 'Agachamento Livre com Peso Corporal'),   -- PADR | Agachamento
    ('abf1fc72-f07f-5c0b-820a-c7e010afa9d7'::uuid, 'Agachamento Livre com Barra'),   -- AJUS | Agachamento Barra
    ('dfd78903-efa3-528f-b747-bcc99e59ddc8'::uuid, 'Agachamento Frontal com Barra'),   -- AJUS | Agachamento Frontal
    ('f2c97963-cdd2-5f1a-99ed-4fabbe844f83'::uuid, 'Agachamento Frontal com Barra no Banco (Box Squat)'),   -- PADR | Agachamento Frontal com Barra no Banco
    ('05a1eee6-06f9-50a2-8827-902b3b6fad44'::uuid, 'Agachamento Frontal com Barra no Rack'),   -- PADR | Agachamento Frontal com Barra no Smith
    ('43f0d050-afe4-5a2b-9749-88f343122641'::uuid, 'Agachamento Frontal na Polia'),   -- PADR | Agachamento Frontal com Cabo
    ('fc3bbcca-9b05-56e9-90cb-a3dff1a12fa2'::uuid, 'Agachamento Frontal com Kettlebells'),   -- PADR | Agachamento Frontal com Kettlebell
    ('dc2f45f4-2f6f-5e8b-93bf-473a778d83ed'::uuid, 'Agachamento Goblet com Halter'),   -- PADR | Agachamento Goblet com Haltere
    ('7b39d400-2a78-5250-a78c-7c4bff58f8c7'::uuid, 'Agachamento Goblet com Kettlebell e Miniband'),   -- PADR | Agachamento Goblet com Kettlebell e Faixa Elástica
    ('a2573e54-9ee5-50ae-86f2-d0417e71d337'::uuid, 'Agachamento Pistola com TRX'),   -- AJUS | Agachamento Pistol com TRX
    ('aabdf1f5-6fae-569b-89e7-5fff3bee7066'::uuid, 'Agachamento Pistola Assistido'),   -- PADR | Agachamento Pistola Apoiado
    ('d1762756-afce-510b-9e33-2bb271e27487'::uuid, 'Agachamento Pistola em Cima da Caixa'),   -- PADR | Agachamento Pistola com Apoio em Caixa
    ('e7279daa-2cd7-51e9-a6c9-34c3a9c107c2'::uuid, 'Agachamento Sissy Ajoelhado'),   -- PADR | Agachamento Sissy ajoelhado com Peso Corporal
    ('d7ed1e6b-6091-5cac-a25f-6fc2e4ee15d6'::uuid, 'Agachamento Patinador (Skater Squat)'),   -- AJUS | Agachamento Skater
    ('ef4e3c3e-1043-5f8c-b260-151295dbb478'::uuid, 'Agachamento Sumô com Halter'),   -- PADR | Agachamento Sumô com Halteres
    ('ff79b49f-58fa-5d99-a782-2528080a9fba'::uuid, 'Agachamento Sumô com Peso Corporal'),   -- PADR | Agachamento Sumô sem Pesos
    ('4f0ada02-fe1a-55a6-ae26-6bdb88d29820'::uuid, 'Agachamento Búlgaro com Salto'),   -- PADR | Agachamento búlgaro com salto
    ('1da9c7a0-79cd-54e4-bcc3-d3947a319572'::uuid, 'Agachamento com Barra acima da Cabeça'),   -- PADR | Agachamento com Barra Sobre a Cabeça
    ('b88ba2ea-6da8-5b45-a238-d069e54e9ef4'::uuid, 'Agachamento no Belt Squat'),   -- AJUS | Agachamento com Cinto
    ('1e1cecd9-1f9e-5557-9b14-ce10f532a2d2'::uuid, 'Afundo com Faixa Elástica'),   -- AJUS | Agachamento com Faixa Elástica em Afundo
    ('c5d1781d-a430-596c-9e42-6c71220ca79a'::uuid, 'Agachamento com Faixa Elástica acima da Cabeça'),   -- PADR | Agachamento com Faixa Elástica sobre a Cabeça
    ('58173809-a5b5-5de3-9759-45f4a700f767'::uuid, 'Agachamento no Banco com Halteres'),   -- PADR | Agachamento com Halteres no Banco
    ('8c672786-7ab7-5458-97df-88c2971e81b2'::uuid, 'Agachamento com Elevação de Joelho'),   -- PADR | Agachamento com Joelho Elevado
    ('ae9ae166-bdbc-565c-865a-6d4fdff52cfe'::uuid, 'Agachamento com Salto na Barra Hexagonal'),   -- PADR | Agachamento com Salto usando Barra Hexagonal
    ('2f98ad24-4113-57db-9424-c8d5cd3e74ba'::uuid, 'Agachamento Isométrico com Elevação de Panturrilhas'),   -- PADR | Agachamento com Sustentação e Elevação de Panturrilhas
    ('5f38b62f-03f7-58a5-853e-4ca1e7949686'::uuid, 'Agachamento no Rack com Pinos (Pin Squat)'),   -- AJUS | Agachamento com Trava
    ('e25623af-48aa-51b9-bd32-e0a0c3af2a79'::uuid, 'Agachamento com Salto e Barra'),   -- PADR | Agachamento com barra e salto
    ('5f7ac1de-2cff-5348-975e-c64b4fa11d56'::uuid, 'Agachamento com Desenvolvimento no Landmine'),   -- AJUS | Agachamento com barra no chão seguido de levantamento militar
    ('11b10341-8a48-5d90-936c-dc51f9db1b4b'::uuid, 'Agachamento Goblet com Kettlebell'),   -- AJUS | Agachamento com kettlebell
    ('a79b4463-d6c6-5bb7-b39e-3fe579353eac'::uuid, 'Salto do Ajoelhado para Agachamento com Barra'),   -- AJUS | Agachamento com salto ajoelhado
    ('f791ba7e-10e3-5705-83fc-205da96fed28'::uuid, 'Agachamento com Salto e Halteres'),   -- PADR | Agachamento com salto e halteres
    ('6d47c5e4-83bf-51a1-a067-bed2c91838f4'::uuid, 'Agachamento com Desenvolvimento com Kettlebells'),   -- AJUS | Agachamento e press com kettlebell
    ('164a53f5-e00a-5031-b9c7-b92d529276ad'::uuid, 'Agachamento Plié com Halter'),   -- AJUS | Agachamento em plié com halteres
    ('d012f791-94a7-5e73-adb8-f45b53765501'::uuid, 'Agachamento Hack com Barra'),   -- PADR | Agachamento hack com barra
    ('b392f6b6-e20e-5de5-b485-1845b8d9598b'::uuid, 'Agachamento Havaiano'),   -- PADR | Agachamento havaiano
    ('b8c61d9c-7218-5737-b5f9-29aea0a04da9'::uuid, 'Agachamento no Hack'),   -- PADR | Agachamento na Máquina Hack
    ('c64ec081-7009-5dd3-a1cb-8cf0bd368530'::uuid, 'Agachamento na Parede com Bola Suíça'),   -- AJUS | Agachamento na Parede com Bola de Exercício
    ('a09f6fd4-2c38-5e02-beb5-10c23edf4415'::uuid, 'Agachamento Pistola'),   -- PADR | Agachamento pistola
    ('103a0c02-4ab2-5c6b-abb3-cd89c93a5e5b'::uuid, 'Afundo Cruzado (Curtsy Lunge)'),   -- AJUS | Agachamento unilateral cruzado
    ('f9354ac6-e9d0-52cf-b105-acb927c80074'::uuid, 'Afundo Cruzado com Barra'),   -- ERRO | Agachamento unilateral cruzado com Barra
    ('7d922bee-85e4-5de4-8e60-9274c4dcd72c'::uuid, 'Afundo Cruzado com Halter'),   -- ERRO | Agachamento unilateral cruzado com haltere
    ('8666aa5c-dbbb-523a-be80-7207b1fa9061'::uuid, 'Air Bike'),   -- PADR | Airbike
    ('db482278-f837-5575-acda-f025fa46e5ad'::uuid, 'Alongamento Dinâmico de Peitoral'),   -- PADR | Alongamento Dinâmico do Peitoral
    ('6a364016-e822-5629-be5b-7896d8faa734'::uuid, 'Alongamento Lateral de Tronco em Pé'),   -- PADR | Alongamento Inclinado Lateral em Pé
    ('bc0faf03-dd2e-5930-9eba-84f20a860d48'::uuid, 'Alongamento de Adutores em Afundo Lateral'),   -- AJUS | Alongamento Lateral da Parte Interna da Coxa
    ('5bd08014-7398-5210-a333-bbcd22fcdb53'::uuid, 'Alongamento do Piriforme Sentado'),   -- PADR | Alongamento Piriforme
    ('3cfc036b-591c-50bf-8d3c-d0bc98cdfa5c'::uuid, 'Alongamento Reverso de Punho'),   -- AJUS | Alongamento Reverso de Pulso
    ('907db4aa-1bb1-58eb-9ec1-793c11736730'::uuid, 'Alongamento de Panturrilha Sentado com Perna Estendida'),   -- PADR | Alongamento Sentado para a Panturrilha com Perna Esticada
    ('2a486ad5-2cf6-58f3-86cf-295ddde4a9d3'::uuid, 'Alongamento Reverso Assistido (Peitoral e Ombro)'),   -- PADR | Alongamento assistido reverso (peitoral e ombro)
    ('9d85a0fe-8467-5a52-a898-0e828ebd63b5'::uuid, 'Alongamento com Bastão na Posição de Front Rack'),   -- PADR | Alongamento com PVC na Posição Frontal de Rack
    ('1b7e192b-d1a6-5051-966e-2eaaac9deeb7'::uuid, 'Alongamento de Panturrilha Agachado'),   -- PADR | Alongamento da panturrilha agachado
    ('69093cac-3760-5d38-83f3-1c48e45018c8'::uuid, 'Alongamento de Panturrilha no Degrau'),   -- PADR | Alongamento da panturrilha com descida do calcanhar
    ('c1c73e69-9005-5124-bb11-2cc08e27829f'::uuid, 'Alongamento da Parte Superior das Costas'),   -- PADR | Alongamento da parte superior das costas
    ('1aaeb6ae-03c0-5e86-ac1e-0b223b2d6bd0'::uuid, 'Alongamento de Costas no Rolo de Espuma'),   -- PADR | Alongamento das Costas com Rolo de Espuma
    ('17055c56-ef7d-5565-b9b9-efeb3656025c'::uuid, 'Alongamento de Adutores em Pé com Pernas Afastadas'),   -- PADR | Alongamento de Adutores com Pernas Afastadas em Pé
    ('eeb8ee17-3d14-5f2f-9197-e1d3969d116b'::uuid, 'Alongamento de Isquiotibiais Deitado'),   -- PADR | Alongamento de Isquiotibiais deitado
    ('30db47ff-55d0-558e-a9a5-62f6711dfe39'::uuid, 'Alongamento de Panturrilha Deitado com Faixa'),   -- AJUS | Alongamento de Panturrilha com Corda
    ('922bd1f3-801c-52c0-9ff7-b98997f5eb6a'::uuid, 'Alongamento Duplo de Pernas (Pilates)'),   -- AJUS | Alongamento de Pernas Duplo
    ('62ebad94-8204-5ce6-91d0-f3ec9c33eb5b'::uuid, 'Alongamento de Quadríceps Ajoelhado'),   -- PADR | Alongamento de Quadríceps ajoelhado
    ('e545610a-560a-5d74-970a-96151b84079b'::uuid, 'Alongamento de Ombro com Braço Cruzado'),   -- PADR | Alongamento de ombro com o braço cruzado
    ('63023350-385b-5b52-aec7-8830396a343a'::uuid, 'Alongamento Reverso de Ombro em Pé'),   -- PADR | Alongamento de ombro reverso em pé
    ('69c45db6-55a4-503e-9c8a-20f508e6da73'::uuid, 'Alongamento de Panturrilha Unilateral no Degrau'),   -- PADR | Alongamento de panturrilha com uma perna
    ('93f9d0b3-290b-56ee-aa70-201e4f355b64'::uuid, 'Alongamento de Panturrilha em Passada Larga'),   -- PADR | Alongamento de panturrilha em passo largo
    ('0fa9504c-598b-50cc-b3aa-238b06927625'::uuid, 'Alongamento de Panturrilha com Antebraços na Parede'),   -- AJUS | Alongamento de panturrilha em posição estática
    ('7f18599c-2111-5e93-bcf5-66ef8c532dd5'::uuid, 'Alongamento de Panturrilha com Ponta do Pé na Parede'),   -- PADR | Alongamento de panturrilha na parede
    ('04fa7325-3b16-5ca7-ad0f-9640e3ac306d'::uuid, 'Alongamento de Quadril 90-90'),   -- PADR | Alongamento de quadril 90-90
    ('2ab38081-8554-54f4-b7a4-13af1faa5f07'::uuid, 'Alongamento de Rotação da Coluna em Pé'),   -- PADR | Alongamento de rotação da coluna em pé
    ('cdd63b89-e3db-5355-a75f-4f90de7fb213'::uuid, 'Alongamento Dinâmico de Ombro com Braço Cruzado'),   -- ERRO | Alongamento de tríceps em pé
    ('3f366247-3635-5b2c-b1d8-ee54275a734d'::uuid, 'Alongamento de Peitoral acima da Cabeça'),   -- PADR | Alongamento do Peito Acima da Cabeça
    ('f23ffead-0edc-5cfd-9dfd-92863f8daa41'::uuid, 'Alongamento de Peitoral e Ombro Anterior com Bastão'),   -- PADR | Alongamento do Peito e Parte Frontal dos Ombros
    ('f2bd6ba3-be4d-5cf7-b97e-221fa131f9ab'::uuid, 'Alongamento de Dorsal e Peitoral Apoiado no Banco'),   -- AJUS | Alongamento do Peitoral até as Costas
    ('42b3cd32-2f79-5908-8fbf-e5d282cbf643'::uuid, 'Alongamento de Peitoral Unilateral na Parede'),   -- PADR | Alongamento do Peitoral com um Braço em Pé
    ('a1b7afbf-ad56-525f-986e-9fe54918d345'::uuid, 'Alongamento do Piriforme Sentado no Banco'),   -- PADR | Alongamento do Piriforme Sentado
    ('f932ea60-49ec-5b12-b98c-6643bdedb40e'::uuid, 'Alongamento de Flexores de Punho em Quatro Apoios'),   -- AJUS | Alongamento do desviador ulnar e extensor do punho
    ('1b0cd8e8-3480-52f3-95fb-4b6f979c8f4a'::uuid, 'Alongamento do Manguito Rotador'),   -- PADR | Alongamento do manguito rotador
    ('9539d104-a1b1-5a5c-b2c0-900f3a51df66'::uuid, 'Alongamento de Ombro com Toalha'),   -- PADR | Alongamento do ombro com toalha
    ('10513f6b-9984-5dff-bc9a-ef2a78171e98'::uuid, 'Alongamento de Peitoral no Rolo de Espuma'),   -- PADR | Alongamento do peito com rolo de espuma
    ('3eee9c89-9eb0-5c13-882c-0a98cf9deabf'::uuid, 'Alongamento de Peitoral e Ombro na Porta'),   -- PADR | Alongamento do peitoral e do ombro na porta
    ('a5adb4ac-6030-5470-b209-000a919568f8'::uuid, 'Alongamento Reverso de Peitoral no Banco'),   -- AJUS | Alongamento do peitoral reverso
    ('767d8609-68b7-538c-a50f-723a73da039d'::uuid, 'Alongamento do Tendão de Aquiles em Pé'),   -- PADR | Alongamento do tendão de Aquiles em pé
    ('6bfc9fd1-0dcd-53c7-98ec-071b7c3260be'::uuid, 'Alongamento do Tibial Posterior com Faixa'),   -- PADR | Alongamento do tibial posterior
    ('56827e54-01da-5b87-981b-e4c78da34221'::uuid, 'Liberação Miofascial do Trato Iliotibial'),   -- AJUS | Alongamento do trato iliotibial com rolo de espuma
    ('23f96c0a-f35e-501f-aaee-294fc297cbe4'::uuid, 'Alongamento de Adutores em Pé com Flexão de Tronco'),   -- PADR | Alongamento dos Adutores com Pernas Abertas em Pé
    ('14d6e399-b9ac-5e1b-95ad-4303298812ab'::uuid, 'Alongamento de Adutores Ajoelhado com Perna Estendida'),   -- PADR | Alongamento dos Adutores com a Perna Estendida ajoelhado
    ('c39a8912-864d-5780-96b8-4ea9cfeb1a75'::uuid, 'Alongamento de Adutores Sentado com Pernas Afastadas'),   -- PADR | Alongamento dos Adutores em Posição Sentada com Pernas Abertas
    ('426fd34b-eb05-5467-bc77-9b68e97eacbf'::uuid, 'Alongamento de Isquiotibiais Sentado com Pernas Afastadas'),   -- PADR | Alongamento dos Isquiotibiais Sentado
    ('b3d728c2-a2e9-5d8b-814f-854f690b84bc'::uuid, 'Liberação Miofascial de Adutores com Rolo'),   -- AJUS | Alongamento dos adutores da coxa com rolo de espuma
    ('31e6691e-8a44-5d07-938d-47e028134911'::uuid, 'Alongamento de Flexores do Quadril Ajoelhado'),   -- PADR | Alongamento dos flexores de quadril ajoelhado
    ('64209e97-eb18-5a60-b8f5-d6c52f1a72de'::uuid, 'Alongamento dos Flexores dos Dedos dos Pés em Pé'),   -- PADR | Alongamento dos flexores dos dedos dos pés em pé
    ('dcf60f1f-4620-5b7c-b722-e29641d2839e'::uuid, 'Alongamento de Isquiotibiais em Pé com Pernas Cruzadas'),   -- PADR | Alongamento dos isquiotibiais em pé com a perna cruzada
    ('0a9813be-1be6-56d1-a164-0f28effacf65'::uuid, 'Liberação Miofascial do Dorsal com Rolo'),   -- AJUS | Alongamento dos latíssimos dorsais com rolo de espuma
    ('2c4e41e6-0c9f-5760-8679-34b23483e450'::uuid, 'Alongamento de Ombros por trás das Costas'),   -- PADR | Alongamento dos ombros por trás das costas
    ('23e5c2d6-fe73-5928-9d7e-984d32a2c35f'::uuid, 'Rotação de Punhos'),   -- AJUS | Alongamento em Círculos nos Punhos
    ('f8e94e2d-a8a0-5a79-b2a5-f0031f437eb6'::uuid, 'Alongamento de Quadríceps em Pé'),   -- PADR | Alongamento em Pé dos Quadríceps
    ('7334356d-cfd3-5d09-a4d0-deff9f70b49f'::uuid, 'Alongamento de Peitoral na Quina da Parede'),   -- AJUS | Alongamento na parede do canto
    ('dff85371-747b-5432-b9c6-9694425afa3d'::uuid, 'Mobilidade de Pés e Tornozelos Sentado'),   -- AJUS | Alongamentos de pés e tornozelos
    ('1cc2b518-de1a-547a-86c5-fa0e4146e8f5'::uuid, 'Ciclismo ao Ar Livre'),   -- AJUS | Andar de Bicicleta ao Ar Livre
    ('9fedf673-c862-57b0-861b-87db1a635cff'::uuid, 'Arranco Unilateral com Kettlebell'),   -- PADR | Arranco com kettlebell de um braço
    ('077cb8f0-c61e-548f-9dce-9e5c197cba7c'::uuid, 'Arranco com Kettlebell em Afundo'),   -- PADR | Arranco com kettlebell em afundo
    ('bb56b2cd-dcda-5eb9-8bd6-8ee8cf7eea88'::uuid, 'Clean e Desenvolvimento com Kettlebell'),   -- ERRO | Arranco e Levantamento com Kettlebell
    ('01e8b2c9-ceea-55f5-ab6d-62f7d53e2ebd'::uuid, 'Clean and Jerk com Kettlebell'),   -- AJUS | Arranque e Arremesso com Kettlebell
    ('17529f79-5dea-583f-9741-778fadec64f1'::uuid, 'Arremesso com Barra (Jerk)'),   -- PADR | Arremesso com Barra
    ('66d5f29e-f6a4-5762-85e4-af9ec59bdcd9'::uuid, 'Arranco Unilateral com Halter'),   -- ERRO | Arremesso com haltere de um braço
    ('8992d197-2984-54d7-ab8c-8245f1672efc'::uuid, 'Abdominal com Arremesso de Bola Medicinal na Parede'),   -- AJUS | Arremesso de Medicina Bola com Levantamento de Tronco
    ('82deba9e-2357-52b3-8ff1-f9814e6c570c'::uuid, 'Clean and Press com Barra'),   -- AJUS | Arremesso e Pressão com Barra
    ('086f98c2-8a7e-531b-9e5c-883269d5ec1c'::uuid, 'Afundo Reverso'),   -- AJUS | Avanço Invertido
    ('28322bdc-85f9-5cfd-ba1f-1515a0ca2d0b'::uuid, 'Afundo Reverso com Halteres'),   -- AJUS | Avanço Invertido com Halteres
    ('859d61fb-7da8-56e2-aab3-0c4ce548f661'::uuid, 'Afundo no Cabo'),   -- AJUS | Avanço com Cabo
    ('e1388f76-351d-5535-906a-f31e89f466b5'::uuid, 'Afundo no Bosu com Elevação de Joelho'),   -- AJUS | Avanço com Joelho Alto em Cima da Bola Bosu
    ('60d7bf05-67bd-571d-9f5b-a5b3f9b86771'::uuid, 'Afundo Caminhando com Elevação de Joelho'),   -- PADR | Avanço com Joelho Elevado em Caminhada
    ('1d596773-d1c2-508d-9a4a-e38a29e0f05a'::uuid, 'Avanço com Peso Corporal'),   -- ERRO | Avanço sem Peso Corporal
    ('e6d50637-9b13-57f9-a71f-9665260d485f'::uuid, 'Swing com Gymstick'),   -- AJUS | Balanço com gymstick
    ('76fffc93-cacc-50e7-aa56-4c485600ab60'::uuid, 'Toque no Balão (Balloon Drill)'),   -- AJUS | Balloon Drill
    ('24acfa49-e4f0-543f-8ff2-7968175d546c'::uuid, 'Hang Clean com Barra'),   -- AJUS | Barbell Hang Clean
    ('74aa29dc-db79-511d-894a-538860ba9b4a'::uuid, 'Barra Fixa com Rotação'),   -- PADR | Barra Fixa com Giro
    ('6f2cf2f9-9664-500f-9660-a8ad842344a5'::uuid, 'Barra Fixa Pegada Fechada'),   -- PADR | Barra Fixa com Pegada Fechada
    ('b384be52-e007-5f8f-9e1e-9826ce799268'::uuid, 'Barra Fixa Pegada Supinada (Chin-Up)'),   -- PADR | Barra Fixa com Pegada Supinada
    ('5811a31d-bad3-5057-9195-ce298d523618'::uuid, 'Barra Fixa atrás da Nuca'),   -- AJUS | Barra Fixa com Pegada por Trás do Pescoço
    ('2e535f18-b2f5-5d47-8ce8-4297dd9918af'::uuid, 'Barra Fixa com Ênfase no Braquial'),   -- PADR | Barra Fixa para o Braquial
    ('f45b280a-edf2-5366-9600-2bcd47d8fd42'::uuid, 'Barra Fixa'),   -- PADR | Barra fixa
    ('8c7e8e57-5046-5942-8039-5343b70f21ab'::uuid, 'Barra Fixa Assistida com Faixa Elástica'),   -- PADR | Barra fixa Assistida com Faixa Elástica
    ('2eb251a2-00e0-51be-ad6c-6d7a078d26de'::uuid, 'Barra Fixa Pegada Aberta'),   -- AJUS | Barra fixa com Arco
    ('4482e897-2ac7-56ad-bf00-e2dedaf0fe38'::uuid, 'Barra Fixa com L-Sit'),   -- PADR | Barra fixa com L-sit
    ('2838a82b-8be4-52bf-b18f-f6d4893f1cc4'::uuid, 'Barra Fixa com Salto'),   -- PADR | Barra fixa com Salto
    ('4eddee45-aff8-579d-998c-31dbcaebc442'::uuid, 'Barra Fixa com Braços Alternados'),   -- PADR | Barra fixa com braços alternados
    ('fac3978a-a1af-5e21-84a7-23dc34c030c6'::uuid, 'Barra Fixa Supinada Assistida'),   -- PADR | Barra fixa com pegada invertida assistido
    ('460add81-0fe2-5241-9c6a-3dec2b43337e'::uuid, 'Barra Fixa Pegada Neutra'),   -- PADR | Barra fixa com pegada neutra
    ('139afe7c-b21d-53aa-a588-9e7b37628e61'::uuid, 'Barra Fixa com Peso'),   -- PADR | Barra fixa com peso
    ('4402be20-d5f3-548f-a3bc-00a8b4ebc959'::uuid, 'Bicicleta Ergométrica (Spinning)'),   -- AJUS | Bike
    ('0595c357-afaf-532c-9388-5f4a69844a9e'::uuid, 'Levantamento de Bola Medicinal do Chão acima da Cabeça'),   -- AJUS | Bola medicinal lançada para cima e para baixo
    ('14c35837-4aff-5094-a8c8-7f61ba6bb82b'::uuid, 'Wall Ball'),   -- AJUS | Bola na parede
    ('31b280a9-e70f-5c9e-93e9-2915ffaa3d95'::uuid, 'Bom Dia com Faixa Elástica'),   -- PADR | Bom Dia com Faixa Elástica de Resistência
    ('168a8c59-689d-5126-a731-e0f752a141f5'::uuid, 'Bom Dia no Smith'),   -- PADR | Bom Dia na Máquina Smith
    ('2e8ddcec-9ecb-596d-92bb-9e7d0d608487'::uuid, 'Jab (Boxe)'),   -- AJUS | Boxe jab
    ('f6f3fc1f-7c0c-5c62-b322-f87a637a07f1'::uuid, 'Burpee com Polichinelo'),   -- PADR | Burpee Jack
    ('8424de75-acf0-5106-9959-c5374bf42a58'::uuid, 'Burpee'),   -- AJUS | Burpees
    ('48194d9f-998f-5782-86e9-dc6b1e8afcc2'::uuid, 'Cadeira Extensora'),   -- PADR | Cadeira extensora
    ('a0b7125a-5dc9-5413-95d2-254627588fd0'::uuid, 'Cadeira Flexora'),   -- PADR | Cadeira flexora
    ('45d6d4f5-82ea-5aad-8622-42f8cc5409d8'::uuid, 'Caminhada Lateral com Miniband'),   -- PADR | Caminhada Lateral com Faixa de Resistência
    ('463aa08f-6b52-58cb-b6cb-289f3bf992c9'::uuid, 'Caminhada do Fazendeiro com Halteres'),   -- AJUS | Caminhada com Halteres
    ('db910d11-cbc5-514a-967c-004d3ec8d739'::uuid, 'Caminhada em Parada de Mão'),   -- PADR | Caminhada na Parada de Mão
    ('fe9b254f-fe79-5d5b-9698-0f7b7dc3fd51'::uuid, 'Caminhada'),   -- AJUS | Caminhar
    ('8a513913-6672-58bd-9e38-bb0fe019baa0'::uuid, 'Passada de Boxeador'),   -- AJUS | Cardio de Passos de Boxeador
    ('4240e19c-1d57-5a14-99bb-717ce1223fb9'::uuid, 'Moinho de Vento com Toque Cruzado no Pé'),   -- AJUS | Catavento corporal
    ('1db276c9-3f9f-57c5-8a95-864fe47ae6dc'::uuid, 'Chute em Gancho no Saco'),   -- PADR | Chute em Gancho
    ('6ab4ac53-dde4-5e72-a763-67051bd0e637'::uuid, 'Extensão de Quadril Alternada Deitado no Banco'),   -- AJUS | Chutes Alternados de Glúteos no Banco
    ('962a5a2e-c6b5-52ab-9cd3-c67325140834'::uuid, 'Chute no Glúteo (Butt Kicks)'),   -- AJUS | Chutes até o Glúteo
    ('cf739d88-80ef-52c2-98b0-d624b5d01efe'::uuid, 'Coice de Glúteo com Perna Flexionada'),   -- PADR | Coice com Perna Flexionada
    ('a328013f-f3ce-52a0-a1d6-259df1966651'::uuid, 'Coice de Burro (Quatro Apoios)'),   -- PADR | Coice de Burro
    ('ffe95770-4657-5ae5-b303-38ea9f4857b7'::uuid, 'Ativação Abdominal Deitado'),   -- AJUS | Contração abdominal
    ('f99922b3-a768-5657-a56f-b6f0347f2512'::uuid, 'Corda Naval'),   -- AJUS | Corda de batalha
    ('915f5a59-603f-59ac-818b-9f6cc4a67977'::uuid, 'Deslocamento Lateral (Shuffle)'),   -- ERRO | Corrida Lateral
    ('0cad3a20-b163-575d-abd3-ae046d8c3eda'::uuid, 'Skipping Alto em Deslocamento'),   -- AJUS | Corrida com Elevação dos Joelhos
    ('d59c90ce-030f-5811-bf2c-e41459d69aa5'::uuid, 'Corrida com Joelhos Altos (Skipping)'),   -- PADR | Corrida com Joelhos Altos
    ('9d3bbe9c-0e4f-5086-8fd0-d1eb05757686'::uuid, 'Corrida com Passadas Rápidas'),   -- PADR | Corrida com Passos Rápidos
    ('8bd3b51f-c6da-5286-a4ec-18a6f5f0d882'::uuid, 'Corrida com Passadas Saltadas (Bounding)'),   -- AJUS | Corrida com Salto
    ('1a230342-a4ce-50be-8567-9ac888cca92d'::uuid, 'Corrida com Passos Curtos'),   -- PADR | Corrida de Passos Curtos
    ('3ef83fe3-a154-5657-aef3-8af2e7f55dc9'::uuid, 'Sprint com Resistência de Faixa Elástica'),   -- ERRO | Corrida de Sprint com Assistência de Faixa Elástica
    ('2a74070a-2aa3-54e0-9f83-37dc7d4e84a6'::uuid, 'Pedalada em Pé na Bicicleta Ergométrica'),   -- AJUS | Corrida na Bicicleta Ergométrica
    ('286229ab-3fa0-5509-b3f2-491ddb390d5c'::uuid, 'Corrida de Costas'),   -- PADR | Corrida para Trás
    ('3cd35484-2799-5077-ac32-ae0b0ca00244'::uuid, 'Crossover na Polia Alta'),   -- AJUS | Cross over polia Alta
    ('38516dcb-4c83-593e-99d8-7cf01aa0eacd'::uuid, 'Crossover na Polia Baixa'),   -- AJUS | Cross over polia baixa
    ('8f4f2c9b-e668-5507-94f7-eecb8eeb557a'::uuid, 'Crossover na Polia Média'),   -- AJUS | Cross over polia media
    ('2253a9ac-da8e-5db6-8a65-319dd10a35e0'::uuid, 'Crossover Unilateral no Cabo'),   -- PADR | Crossover Unilateral com Cabo
    ('f42cac87-8df2-5e9c-8f89-b494e15522b8'::uuid, 'Crossover para Peitoral Superior'),   -- PADR | Crossover de peitoral superior com cabo
    ('e283cb8f-be51-5a35-abd4-3df3da134b0c'::uuid, 'Voador na Máquina (Alavanca)'),   -- AJUS | Crossover na Máquina
    ('6d83d55f-0ca7-5169-b025-8d909e5f06aa'::uuid, 'Crucifixo Reto no Cabo'),   -- PADR | Crucifixo Deitado com Cabo
    ('a0cf8055-e0f6-500c-876c-47a2c56f2773'::uuid, 'Crucifixo Inclinado no Cabo'),   -- PADR | Crucifixo Inclinado no Cross
    ('91444c1a-3221-5422-ae20-577f10e17194'::uuid, 'Crucifixo Declinado Unilateral no Cabo'),   -- PADR | Crucifixo Unilateral em Declinado com Cabo
    ('bd24a296-5234-58e9-b904-5fea7402d17e'::uuid, 'Crucifixo Declinado no Cabo'),   -- PADR | Crucifixo com Cabo Declinado
    ('539de6e6-211d-52b1-a7ab-630f11d378bb'::uuid, 'Crucifixo Declinado com Halteres'),   -- PADR | Crucifixo com Halteres Declinado
    ('800ef82d-c644-5d1f-97bf-0a13aee0c46c'::uuid, 'Crucifixo Inclinado com Halteres'),   -- PADR | Crucifixo com Halteres Inclinado
    ('a5de77d1-7692-5226-ac51-ec26070ab9c2'::uuid, 'Crucifixo no TRX'),   -- PADR | Crucifixo com TRX
    ('8c6b53d7-e3d3-5ddd-bcd1-07436fdb208e'::uuid, 'Crucifixo Declinado com Halteres (Visão 2)'),   -- PADR | Crucifixo com halteres
    ('e2c576b0-2d10-5db7-b9ec-8f262b48f9d9'::uuid, 'Crucifixo Inverso Unilateral no Cabo'),   -- PADR | Crucifixo inverso unilateral com cabo
    ('a0a9ddec-2273-5be7-8851-989bdf5fe33a'::uuid, 'Crucifixo Inverso com Gymstick'),   -- PADR | Crucifixo invertido com gymstick para deltoides posterior
    ('fd95c625-b179-5ba2-a3dd-ca2df2bb71b9'::uuid, 'Cruzado de Direita (Boxe)'),   -- PADR | Cruzado de Direita
    ('97df468e-3505-5a70-af63-400e47ddd0b8'::uuid, 'Círculos com os Braços'),   -- PADR | Círculos com os braços
    ('b41664bd-ecd4-5d5a-ad0e-433751116c8f'::uuid, 'Círculos com Um Braço'),   -- PADR | Círculos com um braço
    ('0b8ea2cc-b0ec-525b-8e4f-d7408791c609'::uuid, 'Círculos com Anilha'),   -- AJUS | Círculos de Braço com Pesos
    ('368f13fa-08a4-51fc-9dd5-f91c601062e6'::uuid, 'Descida Unilateral do Step'),   -- AJUS | Descida de um Pé Só
    ('cbc974d5-d735-5a9a-8cb0-7782967ec9d6'::uuid, 'Desenvolvimento Arnold Unilateral com Kettlebell'),   -- AJUS | Desenvolvimento Arnold com kettlebell
    ('1ebbf774-956d-5cde-9e38-38724f1ec25c'::uuid, 'Desenvolvimento Arnold Unilateral'),   -- PADR | Desenvolvimento arnold com um braço
    ('3a6d8b78-bddc-5512-bef5-fa7e5bd175f4'::uuid, 'Desenvolvimento Cubano Sentado com Halteres'),   -- PADR | Desenvolvimento cubano sentado com halteres
    ('969ef855-c8cc-5f17-8256-45374f805e8b'::uuid, 'Desenvolvimento Alternado em Pé com Halteres'),   -- AJUS | Desenvolvimento de Ombro Alternada em Pé com Halteres
    ('26038b4c-436d-5083-a3c1-65239107ac5a'::uuid, 'Desenvolvimento Sentado com Halteres'),   -- PADR | Desenvolvimento de Ombro no Banco com Halteres
    ('51e2cb9f-747a-5759-aecc-90154ca19877'::uuid, 'Desenvolvimento Alternado com Rotação com Halteres'),   -- PADR | Desenvolvimento de Ombros com Rotação Alternada com Halteres
    ('620b6822-ebf8-5e3f-963f-32ac3f84d8b9'::uuid, 'Desenvolvimento Sentado com Barra'),   -- PADR | Desenvolvimento de ombro com barra sentado
    ('33c9d06f-6d25-543c-a9e9-f22f7c2d8491'::uuid, 'Desenvolvimento em Pé no Cabo'),   -- PADR | Desenvolvimento de ombro com cabo
    ('00775e92-6658-5b0e-80e4-8a56d0781c5f'::uuid, 'Desenvolvimento Ajoelhado no Cabo'),   -- PADR | Desenvolvimento de ombro com cabo ajoelhado
    ('8e5fa2c9-897a-5651-ac83-40f8f85c88ce'::uuid, 'Desenvolvimento Z com Halteres'),   -- PADR | Desenvolvimento de ombro com halteres em Z
    ('a2657914-4c3a-50ad-a25f-cd63af52f697'::uuid, 'Desenvolvimento W com Halteres'),   -- PADR | Desenvolvimento de ombro com halteres em forma de W
    ('4342d924-d80b-5bad-b36a-3dd71507792f'::uuid, 'Desenvolvimento com Kettlebells'),   -- AJUS | Desenvolvimento de ombro com kettlebell
    ('96a10fa6-0de4-5639-9b93-1f1543b687d9'::uuid, 'Desenvolvimento Deitado de Bruços com Halteres'),   -- AJUS | Desenvolvimento de ombro deitado
    ('66b8014c-cdfa-55af-8d2c-f621cf030706'::uuid, 'Desenvolvimento na Máquina Articulada'),   -- AJUS | Desenvolvimento de ombro na máquina
    ('b6b5f2f0-af38-5071-a170-cfc6e6c1dd03'::uuid, 'Desenvolvimento na Máquina Pegada Neutra'),   -- PADR | Desenvolvimento de ombro na máquina (pegada martelo)
    ('ea5b562a-c415-5041-8c7f-5a4233528235'::uuid, 'Desenvolvimento na Máquina Inclinada (Invertido)'),   -- AJUS | Desenvolvimento de ombro reversa na máquina
    ('4916e991-8345-591c-95d6-5e3f8c8baf91'::uuid, 'Desenvolvimento Sentado com Faixa Elástica'),   -- PADR | Desenvolvimento de ombro sentado com faixa de resistência
    ('e57c25ed-a414-5b40-b6d0-c59203d0b43a'::uuid, 'Desenvolvimento Unilateral com Faixa Elástica'),   -- PADR | Desenvolvimento de ombro unilateral com banda
    ('d7b3caf6-c682-5c1f-be48-50a913b67a37'::uuid, 'Desenvolvimento Unilateral com Halter'),   -- PADR | Desenvolvimento de ombro unilateral com halter
    ('7db5a256-df8a-5959-9dd8-b8bc923c671b'::uuid, 'Desenvolvimento atrás da Nuca no Smith'),   -- AJUS | Desenvolvimento de ombros atrás da cabeça na máquina Smith
    ('e934af4b-fe4d-5438-a3fa-f40bff8556b4'::uuid, 'Desenvolvimento atrás da Nuca Sentado com Barra'),   -- AJUS | Desenvolvimento de ombros atrás do pescoço sentado
    ('c2a5c7ef-5ad9-5c0d-981f-6eac0416b716'::uuid, 'Desenvolvimento com Barra W Pegada Supinada'),   -- PADR | Desenvolvimento de ombros com barra W com pegada invertida
    ('2a53cd33-7f44-5a00-aafe-92f857551c1a'::uuid, 'Desenvolvimento em Pé com Halteres Pegada Neutra'),   -- PADR | Desenvolvimento de ombros com halteres em pé com pegada neutra
    ('c4cbc095-c390-5343-a460-c261e55e8d64'::uuid, 'Desenvolvimento no Smith'),   -- PADR | Desenvolvimento de ombros na máquina Smith
    ('6559e344-481b-5d19-bf77-2a8b3ddd3694'::uuid, 'Desenvolvimento Unilateral com Gymstick'),   -- AJUS | Desenvolvimento lateral com gymstick
    ('b0e380b9-66fc-5f86-8c55-ad67f75e0222'::uuid, 'Desenvolvimento atrás da Nuca com Gymstick'),   -- PADR | Desenvolvimento militar atrás da cabeça com gymstick
    ('f6ca3c1e-5bda-5b50-bb52-37c95b0e7d15'::uuid, 'Desenvolvimento Militar com Barra'),   -- PADR | Desenvolvimento militar com barra
    ('02ac3d8d-9269-5224-87e4-2256254ef07a'::uuid, 'Desenvolvimento Ajoelhado no Landmine'),   -- AJUS | Desenvolvimento militar com barra no chão ajoelhado
    ('ba3b0923-afdb-5fdb-923a-cb570d0e3ee5'::uuid, 'Desenvolvimento Militar Pegada Fechada'),   -- PADR | Desenvolvimento militar com pegada fechada
    ('2b899543-ebed-5adb-b80b-3a279fe475c5'::uuid, 'Desenvolvimento com Peso Corporal (sem Carga)'),   -- PADR | Desenvolvimento militar com peso do corpo
    ('c6e4a588-d987-59ce-bbac-bbe5761d99d7'::uuid, 'Desenvolvimento Unilateral com Kettlebell'),   -- PADR | Desenvolvimento militar de uma mão com kettlebell
    ('a6bc8644-82ee-51fd-8ba6-6e87229c24da'::uuid, 'Desenvolvimento Militar em Pé no Smith'),   -- PADR | Desenvolvimento militar em pé na máquina Smith
    ('8dbae9b3-dd1c-54ad-b82d-db583afe6d93'::uuid, 'Desenvolvimento em Pé no Landmine'),   -- AJUS | Desenvolvimento militar inclinado com barra presa no chão
    ('1eb8d16b-2105-5a8e-9eb2-1b4446a40f17'::uuid, 'Desenvolvimento Unilateral Ajoelhado com Kettlebell'),   -- AJUS | Desenvolvimentos com kettlebell unilateral de joelhos
    ('0ed7ea8a-23ed-5e4c-9ee5-2afa0072c70c'::uuid, 'Deslizamento na Parede com Rolo (Serrátil)'),   -- PADR | Deslize de parede do serrátil com rolo de espuma
    ('6f99f470-52b4-5c78-bf8f-c19c1f603550'::uuid, 'Depressão Escapular no Banco'),   -- AJUS | Dips de escápula
    ('41ad8f1f-f88e-500e-a2b0-28c53fb1c30f'::uuid, 'Mergulho na Cadeira'),   -- AJUS | Dips na cadeira
    ('cf7f3b1a-24ae-5057-9cd8-96b0061e3692'::uuid, 'Flexão Plantar em Pé'),   -- ERRO | Dorsiflexão plantar
    ('76e9d119-b670-5eb3-a12a-46c57fc1eb03'::uuid, 'Devil Press com Halteres'),   -- AJUS | Dumbbell Devil Press
    ('7c7f6937-8a6a-5cb1-a6e9-b27692d7b2c9'::uuid, 'Power Clean com Halteres'),   -- AJUS | Dumbbell Power Clean
    ('81e75d47-1c78-5cdb-9a07-d6992a485431'::uuid, 'Elevação Frontal Alternada com Halteres'),   -- PADR | Elevação Frontal Alternada Com Halteres
    ('b7de6dbd-dd3e-5cf5-b91d-69193d6e34a2'::uuid, 'Abdução em Quatro Apoios com Miniband (Fire Hydrant)'),   -- ERRO | Elevação Lateral de Perna com Faixa Elástica
    ('93ddb9f4-8fb8-540d-8fa9-c2242ddd35d9'::uuid, 'Elevação Lateral de Perna Deitado com Miniband'),   -- PADR | Elevação Lateral de Perna com Faixa Elástica Deitado de Lado
    ('ca911adc-0764-53b2-952b-c2bba3f6f37f'::uuid, 'Elevação Lateral Deitado de Lado no Banco (Deltoide Posterior)'),   -- PADR | Elevação Posterior com Halteres em Decúbito Ventral
    ('3a27c30a-6a50-59e1-9231-69eb2d9ac237'::uuid, 'Crucifixo Inverso Unilateral Deitado com Halter'),   -- AJUS | Elevação Posterior unilateral com halteres em Decúbito Prono
    ('be6b8864-8726-50a6-88c8-21ecad52ce36'::uuid, 'Elevação Pélvica com Barra'),   -- PADR | Elevação Pélvica Com Barra
    ('0feca28b-00c8-533b-9041-a593c8cf2435'::uuid, 'Elevação Pélvica com Barra e Pés no Banco'),   -- AJUS | Elevação Pélvica Com Barra Declinado
    ('e0b9dd49-7e09-55b2-b9af-48fdf9d57978'::uuid, 'Elevação Pélvica com Pés no Banco'),   -- AJUS | Elevação Pélvica Declinado
    ('6e66436b-c2ba-565f-908d-f858b8fa0dc4'::uuid, 'Elevação Pélvica na Máquina'),   -- PADR | Elevação Pélvica Na Máquina
    ('427d12d6-41cc-5bb4-bfa1-afe4da6aa002'::uuid, 'Elevação Pélvica Unilateral com Barra'),   -- PADR | Elevação Pélvica Unilateral Com Barra
    ('d21d72e0-25ab-5a57-a1ad-5803cf671c4b'::uuid, 'Elevação Pélvica com Faixa Elástica'),   -- PADR | Elevação Pélvica com Banda de Resistência
    ('3cd26c0e-8c83-55a5-9032-d26db3ed37ca'::uuid, 'Elevação Pélvica no Smith'),   -- PADR | Elevação Pélvica na Máquina Smith
    ('f151a13f-b061-56fe-8a5e-815a11dc4c2a'::uuid, 'Elevação Pélvica na Cadeira Extensora'),   -- PADR | Elevação Pélvica na Máquina de Extensão de Pernas
    ('553f12f8-c110-5a94-a70c-80fde577ba6d'::uuid, 'Elevação de Panturrilha Unilateral no Leg Press'),   -- PADR | Elevação Unilateral de Panturrilha no Leg Press
    ('eba1a864-5eec-527e-8a7d-9a04fc9c8f6a'::uuid, 'Subida no Banco com Barra'),   -- AJUS | Elevação com Barra em Degrau
    ('b76a8f68-e1dd-5b2e-b8f7-4bad12a22644'::uuid, 'Subida no Banco com Toque Cotovelo-Joelho'),   -- AJUS | Elevação com Giro do Cotovelo Oposto para o Joelho
    ('2e62e69e-2fb1-5e93-9825-9e4d79285415'::uuid, 'Flexão de Quadril em Pé com Faixa Elástica'),   -- AJUS | Elevação da Perna em Pé com Faixa Elástica de Resistência
    ('dc455c03-e442-51c7-ab03-96c1d7774653'::uuid, 'Crucifixo Inverso Apoiado no Banco Inclinado'),   -- PADR | Elevação de Deltoide Posterior com Halteres Inclinado
    ('db085c1a-7fb2-50a3-a97e-6b2ee8cd029d'::uuid, 'Elevação em Y Apoiado no Banco Inclinado'),   -- PADR | Elevação de Deltoide em Y com Halteres Inclinado
    ('9ed0cdb8-bbdd-5e1d-be5c-eca202503d31'::uuid, 'Elevação em 4 Direções com Halteres'),   -- AJUS | Elevação de Halteres (4 posições)
    ('a4d21e9b-11f8-59d2-9fdd-79cbb667a67d'::uuid, 'Subida no Step com Halteres'),   -- ERRO | Elevação de Joelho com Halteres
    ('db3082af-cd90-58d5-b4ee-7fcbcd378637'::uuid, 'Elevação de Panturrilha Sentado com Anilha'),   -- AJUS | Elevação de Panturrilha Sentado com Peso
    ('94fc815a-8d7d-5189-865e-17708066df09'::uuid, 'Elevação de Panturrilha em Pé com Barra'),   -- PADR | Elevação de Panturrilha com Barra em Pé
    ('dd3c4c22-8c70-5632-8851-c3682c98be6a'::uuid, 'Elevação de Panturrilha com Faixa Elástica'),   -- PADR | Elevação de Panturrilha com Faixa Elástica de Resistência
    ('4c6c3776-7dfe-55e4-b630-c328c7fc6dee'::uuid, 'Elevação de Panturrilha Unilateral no Hack'),   -- PADR | Elevação de Panturrilha com Uma Perna na Máquina Hack
    ('2261dcae-1a1d-50e1-820b-1f3b473abb79'::uuid, 'Panturrilha em Pé na Máquina'),   -- PADR | Elevação de Panturrilha em Máquina em pé
    ('5a6a5eff-5fe3-54c4-b845-52a24c31f1bf'::uuid, 'Elevação de Panturrilha Unilateral'),   -- PADR | Elevação de Panturrilha em Uma Perna
    ('65739122-6ec7-5dd8-9aa8-ce783719cffd'::uuid, 'Panturrilha em Pé na Máquina com Apoio nos Ombros'),   -- PADR | Elevação de Panturrilha na Máquina
    ('3535c70f-1b66-51db-a396-9bbc880a9748'::uuid, 'Elevação de Panturrilha no Leg Press Horizontal'),   -- PADR | Elevação de Panturrilha no Leg Press horizontal
    ('43ab3d6c-566b-516a-89a0-3e9ee4456d02'::uuid, 'Elevação de Panturrilha em Pé com Halteres'),   -- AJUS | Elevação de Panturrilhas
    ('9976fc24-9725-564d-a627-b66bcd6b5143'::uuid, 'Elevação de Panturrilha no Hack'),   -- PADR | Elevação de Panturrilhas no Hack
    ('0e064ee0-7eac-546d-8da6-b2136af5cbc7'::uuid, 'Elevação de Perna Estendida em Pé com Faixa Elástica'),   -- PADR | Elevação de Perna Reta em Pé com Faixa de Resistência
    ('c985f94c-cd96-57d3-8422-70d784bb8732'::uuid, 'Flexão de Quadril em Pé na Máquina (Alavanca)'),   -- AJUS | Elevação de Perna em Pé na Máquina
    ('b70a4789-b72e-567f-b0a4-df6c3725f8b3'::uuid, 'Subida no Banco com Elevação de Joelho e Rosca Bíceps'),   -- ERRO | Elevação de Perna Única com Equilíbrio e Rosca de Bíceps
    ('6cf9561b-4efe-5b70-a46b-a1bef038140d'::uuid, 'Elevação Lateral das Duas Pernas Deitado'),   -- AJUS | Elevação de Pernas deitado de Lado
    ('21d12780-1b5d-5232-9bfb-17ba038e59c3'::uuid, 'Ponte de Glúteos Sapo (Frog Pump)'),   -- ERRO | Elevação de Pernas estilo Sapo
    ('9467f7f2-f9ea-5d48-ab1c-0beef021345a'::uuid, 'Extensão de Quadril Ajoelhado com Faixa Elástica'),   -- AJUS | Elevação de Quadril com Banda de Resistência de Joelhos
    ('cf89a61c-01fc-5440-9103-c4a7c749f019'::uuid, 'Elevação em T Apoiado no Banco Inclinado'),   -- PADR | Elevação de T com Halteres Inclinada
    ('37492785-d612-52be-a187-969e5c36587b'::uuid, 'Panturrilha Sentado na Máquina (Alavanca)'),   -- AJUS | Elevação de panturrilha Sentado na Máquina
    ('e63cb82e-e44d-5d58-9312-e0ac9ba442ba'::uuid, 'Elevação de Panturrilha em Pé no Degrau'),   -- PADR | Elevação de panturrilha em pé
    ('6acd9615-6613-595f-9465-e561ec108102'::uuid, 'Elevação Frontal com Barra W e Rotação'),   -- PADR | Elevação frontal com barra girando
    ('65e24f07-39db-5331-83d1-33c267bf91fb'::uuid, 'Elevação Frontal com Barra W no Banco Inclinado'),   -- PADR | Elevação frontal com barra w inclinada
    ('2acc77d1-bd4e-5940-82f5-1eedfeb8fd70'::uuid, 'Elevação Frontal Bilateral com Halteres'),   -- PADR | Elevação frontal com dois braços com halteres
    ('d19c46ee-4b3b-5abd-9383-c4c94a244a0b'::uuid, 'Elevação Frontal com Halteres'),   -- PADR | Elevação frontal com halteres
    ('d44867c4-68e7-5f5f-bbd3-b9d91e00bd21'::uuid, 'Elevação Frontal Sentado com Halteres'),   -- PADR | Elevação frontal com halteres sentado
    ('f1b6b2f4-ce3b-5466-a4bb-63533c424f37'::uuid, 'Elevação Frontal com Faixa Elástica'),   -- AJUS | Elevação frontal lateral com elástico
    ('ea7abfd1-1f95-59c4-aabb-1e79beaa4cf0'::uuid, 'Elevação Lateral Alternada com Halteres'),   -- PADR | Elevação lateral alternada com halteres
    ('a77051be-f560-5cc6-a543-d0b15af32dfa'::uuid, 'Elevação Lateral no Landmine'),   -- AJUS | Elevação lateral com barra no chão
    ('daa734aa-6ed3-5ff0-b776-5a07912924fb'::uuid, 'Elevação Lateral com Cotovelos Flexionados'),   -- PADR | Elevação lateral com braço flexionado
    ('1cccf2ed-54da-5cb6-85e6-146f225c7c49'::uuid, 'Elevação Lateral Apoiado no Banco Inclinado'),   -- PADR | Elevação lateral com halteres com apoio no peito
    ('16de9f98-e03d-569e-8af0-4aaea68494f8'::uuid, 'Elevação Lateral Sentado com Halteres'),   -- PADR | Elevação lateral com halteres sentado
    ('7b1ba98f-fdac-5066-8270-9ef7a1aa9314'::uuid, 'Elevação Lateral Isométrica com Toalha na Parede'),   -- PADR | Elevação lateral com toalha na parede
    ('3f4442f7-b028-544a-b729-faff7658e789'::uuid, 'Crucifixo Inverso Curvado com Halteres'),   -- AJUS | Elevação lateral com tronco inclinado
    ('bed8ad38-d36f-5397-ae69-6804e57b1c51'::uuid, 'Elevação Lateral Cruzada no Crossover'),   -- PADR | Elevação lateral cruzada no crossover
    ('2bd308a2-b65f-5da1-af82-5491a549e542'::uuid, 'Elevação Lateral sem Carga'),   -- AJUS | Elevação lateral de braços
    ('bb2e5b1e-b501-583c-a650-962638cebf06'::uuid, 'Elevação Lateral Unilateral no Cabo'),   -- AJUS | Elevação lateral de braços com cabo
    ('108d9f2e-d23c-5bc2-a911-bd685da8d8d9'::uuid, 'Elevação Lateral com Halteres'),   -- PADR | Elevação lateral de braços com halteres
    ('f0b4d498-525a-5aad-87a6-9458995975b3'::uuid, 'Elevação Lateral Deitado de Lado com Halter (Deltoide Posterior)'),   -- AJUS | Elevação lateral de deltóide posterior com halteres
    ('a9562f06-34bb-52d2-9605-75ed21a0cd3e'::uuid, 'Elevação Lateral Deitado de Lado no Banco Inclinado'),   -- PADR | Elevação lateral de halteres inclinada
    ('1179ecc2-e8db-5c09-a982-4573a78c3f35'::uuid, 'Crucifixo Inverso Deitado no Banco'),   -- AJUS | Elevação lateral deitado
    ('e500d80e-9a6f-51c0-86c9-ba18ba039bf4'::uuid, 'Elevação Lateral e Frontal com Halteres'),   -- PADR | Elevação lateral e frontal com halteres
    ('5696f95f-61d9-5804-8748-098f2f907ac7'::uuid, 'Elevação Lateral na Máquina de Frente para o Aparelho'),   -- AJUS | Elevação lateral na máquina
    ('fbd921a0-69ba-519a-95bf-d12dcaeca142'::uuid, 'Elevação Lateral Unilateral Inclinado no Cabo'),   -- PADR | Elevação lateral unilateral com cabo
    ('4bf047bc-cb7c-5cbe-8181-b7f64308f6b5'::uuid, 'Elevação Lateral Unilateral Inclinado com Halter'),   -- PADR | Elevação lateral unilateral com haltere inclinado
    ('cd64f7c7-60ed-56b6-8add-99f00b8250b0'::uuid, 'Elevação Lateral Unilateral com Halter'),   -- PADR | Elevação lateral unilateral com halteres
    ('8cb731b8-1a9c-5f3b-8774-1daa61dd78e2'::uuid, 'Mergulho Escapular na Paralela'),   -- AJUS | Elevações de ombros na paralela
    ('872180a5-23f4-562c-ac1f-7de700376d45'::uuid, 'Elevação Frontal Apoiado no Banco Inclinado'),   -- AJUS | Elevações frontais com halteres apoiadas no peito
    ('afb5e1db-17d3-545e-a487-ff207762bfae'::uuid, 'Encolhimento acima da Cabeça'),   -- PADR | Encolhimento Acima da Cabeça
    ('c77d18b0-66fd-5dcb-b964-1c0357300e47'::uuid, 'Encolhimento Sentado no Banco Inclinado'),   -- AJUS | Encolhimento Inclinado Pronado
    ('1bd30711-a8df-5f37-a4cf-100c7c475747'::uuid, 'Encolhimento no Cabo'),   -- PADR | Encolhimento com Cabo
    ('dbf8cb70-28c9-5478-8f5d-0250e8871bf5'::uuid, 'Encolhimento Apoiado no Banco Inclinado com Halteres'),   -- AJUS | Encolhimento com Halteres em Declive
    ('7c19862e-402a-542e-86fc-024c634a7832'::uuid, 'Encolhimento com Barra'),   -- PADR | Encolhimento de Barra
    ('defbddab-38a9-5745-9722-b570f5c06660'::uuid, 'Encolhimento com Barra atrás do Corpo'),   -- PADR | Encolhimento de Barra Atrás das Costas
    ('1d8452b4-af41-5e43-8e0a-9bc2cf0fc0bb'::uuid, 'Encolhimento no Smith'),   -- PADR | Encolhimento de Ombros na Máquina Smith
    ('bf4a6a68-5945-530a-ac27-38932e0c118d'::uuid, 'Encolhimento com Barra atrás do Corpo no Rack'),   -- AJUS | Encolhimento de ombros por trás com barra
    ('a94bd8de-458e-5bd0-917d-203e26404a63'::uuid, 'Encolhimento na Máquina'),   -- PADR | Encolhimento na máquina
    ('0d86cd7a-91dd-56d2-a83a-47ccbb927a3f'::uuid, 'Escalador em Pé'),   -- ERRO | Escalador de Montanha
    ('612c04cb-a5f9-5c92-8ce3-ac00674fdecb'::uuid, 'Esquiador com Gymstick'),   -- PADR | Esquiador com gymstick
    ('55f98656-91f5-59f2-b8cd-f3e06812a269'::uuid, 'Corrida na Esteira'),   -- AJUS | Esteira Ergométrica
    ('1e688237-b9e7-59d9-904a-e7a10f9c2c3f'::uuid, 'Caminhada na Esteira Inclinada'),   -- AJUS | Esteira com Inclinação
    ('f837156b-144a-50d0-8b37-1aa3d1cfa3d9'::uuid, 'Exercício Pliométrico em X'),   -- PADR | Exercício Pliométrico X
    ('0b1ba8c3-74c1-573f-98bd-795d39b13358'::uuid, 'Exercício da Bailarina Sentado'),   -- PADR | Exercício de bailarina sentada
    ('5121553e-0a4a-5930-90b8-4fdbb36fae62'::uuid, 'Retração Escapular Sentado'),   -- PADR | Exercício de retração escapular sentada
    ('1263e640-b6d4-5454-8706-b4c2959f250b'::uuid, 'Drill dos 5 Pontos'),   -- AJUS | Exercícios das 5 Marcas
    ('de82b6ce-4c99-5b6d-8589-3f1fd755b802'::uuid, 'Escada de Agilidade'),   -- AJUS | Exercícios de Escada de Agilidade
    ('ecfb94f8-3688-587d-95cb-7bb91735e514'::uuid, 'Escada de Agilidade Lateral'),   -- AJUS | Exercícios de escada de agilidade lateral
    ('187bcdd0-b634-5794-9338-642920fad877'::uuid, 'Extensão de Tríceps Unilateral Ajoelhado no Cabo'),   -- AJUS | Extensão Concentrada com Cabo no Joelho
    ('070304fb-de21-5e21-8720-93ad943f2657'::uuid, 'Coice de Glúteo em Pé'),   -- AJUS | Extensão De Glúteo Em Pé
    ('1a7f25a9-6953-5257-85d9-c298be0bf5a1'::uuid, 'Extensão de Quadril com Perna Estendida em Quatro Apoios'),   -- AJUS | Extensão De Perna Reta
    ('4db6256f-f8be-5bfb-a172-e5ad75f430af'::uuid, 'Cadeira Extensora Unilateral'),   -- AJUS | Extensão de Perna Unilateral
    ('3321cdb7-8d97-58ce-bafa-3b3c94d02aa6'::uuid, 'Extensão de Quadril em Pé com Faixa Elástica'),   -- AJUS | Extensão de Perna em Pé com Faixa de Resistência
    ('120818cb-43cb-53b9-bdf0-10dbf63dd342'::uuid, 'Extensão de Quadril Deitado no Smith'),   -- AJUS | Extensão de Perna na Máquina Smith Reversa
    ('11e934f1-173e-5fed-9884-d605e693ae8a'::uuid, 'Cadeira Extensora com Miniband'),   -- PADR | Extensão de Pernas Sentado com Faixa de Resistência
    ('e0310bfd-88f9-5a44-82e5-5d1144b60e84'::uuid, 'Cadeira Extensora com Elástico Tubular'),   -- PADR | Extensão de Pernas com Faixa Elástica Sentado
    ('970f488d-03e9-5a6c-9624-4f2971134ef7'::uuid, 'Coice de Glúteo em Pé na Polia'),   -- PADR | Extensão de Quadril com Cabo
    ('105b3b17-2787-5525-9efd-3465abeb652b'::uuid, 'Extensão de Quadril em Pé na Máquina (Alavanca)'),   -- PADR | Extensão de Quadril em Pé na Máquina
    ('f0157a61-a9a4-54b2-861e-97431160382f'::uuid, 'Extensão de Quadril Apoiado no Banco'),   -- PADR | Extensão de Quadril no Banco
    ('a2c3400c-c468-5b0c-a82f-c0c972342f23'::uuid, 'Tríceps Francês com Gymstick'),   -- PADR | Extensão de Tríceps Acima da Cabeça com Gymstick
    ('5b66354a-9676-549d-a2cd-89ee16c02596'::uuid, 'Tríceps Testa Deitado no Cabo com Barra'),   -- ERRO | Extensão de Tríceps Deitado com Barra
    ('f950b1d6-4483-5cab-8fab-9604e1205af2'::uuid, 'Tríceps Pulley Unilateral Pegada Supinada'),   -- AJUS | Extensão de Tríceps Invertida Unilateral
    ('e9931efe-70f5-5c87-80a0-23c4ea4aedbf'::uuid, 'Tríceps Testa Declinado Pegada Fechada com Barra'),   -- AJUS | Extensão de Tríceps Testa Declinado Fechado
    ('ed1a43b1-0fb7-587c-ab76-a1855630dfab'::uuid, 'Tríceps Francês Ajoelhado no Cabo'),   -- AJUS | Extensão de Tríceps com Cabo em Posição Ajoelhada
    ('8b12a521-b3b0-574a-a4aa-daa1d6ca9855'::uuid, 'Tríceps Pulley com Faixa Elástica'),   -- AJUS | Extensão de Tríceps com Faixa Elástica
    ('96c6a3ec-64ef-5e6f-b6ab-d0c6510eb575'::uuid, 'Tríceps Pulley com Faixa Elástica na Estrutura'),   -- AJUS | Extensão de Tríceps com Faixas Elásticas
    ('6c4b391b-5694-59b5-bbb1-04c16d5215e6'::uuid, 'Tríceps Francês Unilateral na Polia Alta'),   -- AJUS | Extensão de Tríceps com Uma Mão no Pulley Alto Sobre a Cabeça
    ('09f69d8e-f699-502e-a997-19c7dc2b34f3'::uuid, 'Tríceps Testa com Barra W Pegada Fechada atrás da Cabeça'),   -- ERRO | Extensão de Tríceps deitado com Barra W Pegada Fechada atrás da Cabeça
    ('d6ec6001-1e33-5851-8aea-60129366fce2'::uuid, 'Tríceps Testa Deitado no Cabo com Corda'),   -- AJUS | Extensão de Tríceps deitado com Corda
    ('2a7d23a9-a458-5483-aec6-883fe9c1f41c'::uuid, 'Extensão de Ombro com Faixa Elástica'),   -- PADR | Extensão de ombro com faixa
    ('3035bda6-707e-59da-add1-ef76b84d85b2'::uuid, 'Extensão de Tríceps com Peso Corporal no Solo'),   -- AJUS | Extensão de tríceps
    ('37120db5-a15e-586f-a983-96e7473ea6e8'::uuid, 'Tríceps Testa Inclinado com Barra W'),   -- AJUS | Extensão de tríceps com barra W inclinada
    ('c1d69d79-c391-5051-b220-72cafeba0197'::uuid, 'Tríceps Testa com Barra atrás da Cabeça'),   -- AJUS | Extensão de tríceps com barra atrás da cabeça
    ('768a5661-18a6-5594-b331-e2ea60e1f667'::uuid, 'Tríceps Francês em Pé com Barra'),   -- AJUS | Extensão de tríceps com barra em pé
    ('e07177df-b160-5d23-a2a0-0ce305023025'::uuid, 'Extensão de Tríceps Ajoelhado no Cabo Apoiado no Banco'),   -- AJUS | Extensão de tríceps com cabo ajoelhado
    ('f51c8033-16ac-56da-a0f9-28e93c359932'::uuid, 'Tríceps Francês no Banco Inclinado no Cabo'),   -- AJUS | Extensão de tríceps com cabo inclinado
    ('ae4b315d-67f0-5da1-b3bc-90aa3a0f5916'::uuid, 'Extensão de Tríceps Horizontal no Cabo'),   -- PADR | Extensão de tríceps com cabo na posição horizontal
    ('58cc2a3e-0967-516d-96c5-3d0b25d421d1'::uuid, 'Extensão de Tríceps Horizontal com Faixa Elástica'),   -- PADR | Extensão de tríceps com elástico na posição horizontal
    ('978a0c86-b093-53bb-8569-5069b344b14c'::uuid, 'Tríceps Testa Unilateral Pegada Pronada com Halter'),   -- AJUS | Extensão de tríceps com haltere em pronação com um braço
    ('226ad443-4f53-519b-98df-575ad6750c32'::uuid, 'Tríceps Francês Unilateral Sentado com Halter'),   -- AJUS | Extensão de tríceps com haltere unilateral sentado
    ('d91a8299-c3d4-5b33-b288-7fcd2aa21ad5'::uuid, 'Tríceps Pulley Unilateral'),   -- AJUS | Extensão de tríceps com um braço
    ('1c9f2ff4-b2ef-5467-b21c-3d7a896bc3c8'::uuid, 'Extensão de Tríceps Lateral no Cabo'),   -- PADR | Extensão de tríceps lateral com cabo
    ('7f63f1a5-a7d1-5f0e-ae80-6bd74d793a3a'::uuid, 'Extensão de Tríceps na Máquina'),   -- PADR | Extensão de tríceps na máquina
    ('0189435d-5659-5071-8443-29e8995365c3'::uuid, 'Extensão de Tríceps na Máquina Pegada Neutra'),   -- PADR | Extensão de tríceps na máquina pegada neutra
    ('36ff6891-1948-50c8-9b05-1edf44a41d83'::uuid, 'Tríceps Francês em Pé na Polia Alta'),   -- AJUS | Extensão de tríceps no cabo alto
    ('08ccd287-ea6b-58df-bb66-270b3a244337'::uuid, 'Tríceps Testa Deitado no Cabo'),   -- AJUS | Extensão de tríceps no cabo deitado
    ('245d2c7d-d3c7-5868-adc9-18d38d8a261b'::uuid, 'Extensão Lombar Sentado na Máquina'),   -- AJUS | Extensão lombar sentada
    ('f22c5811-3905-5480-be0c-b9e26150410e'::uuid, 'Face Pull (Puxada para o Rosto)'),   -- AJUS | Face Pull
    ('7ee5dba4-8b7c-5197-9cf1-8a1c2ed78672'::uuid, 'Flexão Fechada na Bola Medicinal'),   -- PADR | Flexão Fechada com bola medicinal
    ('c8dc6761-5ac4-5df8-a160-3202dc830b59'::uuid, 'Flexão de Ombro Alternada na Parede'),   -- PADR | Flexão alternada de ombro
    ('cfe572d5-2e8a-5d02-bb7d-5f18afeb8702'::uuid, 'Flexão com Cobra'),   -- PADR | Flexão cobra
    ('69028342-cbba-559f-b4b8-4ab7149d2691'::uuid, 'Flexão com Mãos Cruzadas'),   -- PADR | Flexão com Cruzamento dos Braços
    ('ceea8ca8-e5ed-58d5-9e0e-c57f8973354b'::uuid, 'Flexão com Rotação (Flexão em T)'),   -- PADR | Flexão com Rotação
    ('a661fe92-7fb0-5c5d-b995-46b3027a3511'::uuid, 'Flexão com Toque nos Pés'),   -- PADR | Flexão com Toque nos Dedos dos Pés
    ('8ffbfb06-78ce-53fc-914e-8e4aed76cba5'::uuid, 'Flexão com Apoios (Push-Up Bars)'),   -- AJUS | Flexão com barras de apoio
    ('8a78bbe6-c653-5fd0-a231-69b67c36b12b'::uuid, 'Flexão Profunda com Kettlebells'),   -- PADR | Flexão com kettlebell profunda
    ('c76a32d7-a4f9-5c7c-bf99-afa0f6386d0e'::uuid, 'Flexão em Parada de Mão'),   -- PADR | Flexão com parada de mãos
    ('5b5a1b3c-ac3d-5c35-83ad-415c2aaa07d7'::uuid, 'Flexão com Peso'),   -- PADR | Flexão com peso
    ('8d1096a7-eadb-54e1-889c-4bcb7d13db6a'::uuid, 'Flexão com Um Braço'),   -- PADR | Flexão com um braço
    ('2b30b947-b1d1-514e-baf1-a0c426c39494'::uuid, 'Flexão Declinada na Bola Suíça'),   -- PADR | Flexão de Braço Declinada com Bola de Estabilidade
    ('e0865f54-9509-550a-ad08-b1259ac62c76'::uuid, 'Flexão Dive Bomber'),   -- AJUS | Flexão de Braço com Arqueamento
    ('e0946adb-91c3-5643-9dd7-5e9159c6001f'::uuid, 'Flexão Unilateral na Bola Medicinal'),   -- PADR | Flexão de Braço com Bola Medicinal com Apoio em Um Braço
    ('aac961ca-6744-5836-973e-0672cf80a4ae'::uuid, 'Flexão com Mãos na Bola Suíça'),   -- PADR | Flexão de Braço com Bola de Estabilidade
    ('4f0e9cc8-696f-5c44-8803-0d01d0044347'::uuid, 'Flexão com Uma Perna Elevada'),   -- PADR | Flexão de Braço com Uma Perna
    ('b032002d-2256-5713-aca8-af10c1dccc6a'::uuid, 'Flexão com Joelhos Apoiados'),   -- PADR | Flexão de Braço de Joelhos
    ('40f31393-5288-520d-a50d-24c39ef2d57c'::uuid, 'Flexão na Parede Pegada Fechada'),   -- PADR | Flexão de Braço na Parede com Pegada Fechada
    ('bfdd5742-dba3-5719-9610-4fa5dafb184f'::uuid, 'Flexão no Bosu'),   -- PADR | Flexão de Braço no Bosu
    ('7234144b-110e-56b6-9062-5f4dafa66da7'::uuid, 'Flexão Fechada com Joelhos Apoiados'),   -- PADR | Flexão de Braços com Apoio dos Joelhos Fechada
    ('dac08716-7146-5f62-91c3-19a1437b7cdc'::uuid, 'Flexão com Toque no Ombro'),   -- PADR | Flexão de Braços com Toque no Ombro
    ('3155d874-3a4f-50b1-b55b-4de5ba1366c0'::uuid, 'Extensão de Tríceps com Peso Corporal na Barra'),   -- ERRO | Flexão de Cotovelos na Barra
    ('882033da-4365-5de7-b84e-337a9eb1cd36'::uuid, 'Flexão na Ponta dos Dedos'),   -- PADR | Flexão de Dedos
    ('ca744fa1-fffb-565e-bcd0-585b5595e00e'::uuid, 'Flexão no TRX'),   -- PADR | Flexão de Peito com TRX
    ('5b4df4af-90eb-55df-b89d-4ea9067d978e'::uuid, 'Flexora em Pé Unilateral'),   -- AJUS | Flexão de Perna Unilateral na Máquina
    ('067dbe0b-8747-5065-8f09-4462367afdd7'::uuid, 'Flexão de Pernas Deitado com Halter'),   -- ERRO | Flexão de Perna com Halteres em Decúbito Dorsal
    ('c668a725-e04f-5057-af25-6313264f4bb4'::uuid, 'Flexão de Perna em Pé com Faixa Elástica'),   -- PADR | Flexão de Pernas com Faixa Elástica
    ('27715ece-d827-53f5-a74e-e471e2097dd2'::uuid, 'Flexão de Pernas Declinado com Halter'),   -- PADR | Flexão de Pernas com Halteres Declinado
    ('7c8860ca-98ce-5537-b8e6-46784fc116f9'::uuid, 'Flexão de Pernas Deitado com Faixa Elástica'),   -- PADR | Flexão de Pernas deitado com Faixa Elástica
    ('cffea506-65e7-5c4c-906f-f21e85a165b2'::uuid, 'Flexão de Pernas na Bola Suíça'),   -- PADR | Flexão de Pernas na Bola de Estabilidade
    ('f9b4cfcf-d75e-501f-9c44-899cf394f0b7'::uuid, 'Rosca de Punho Neutra Sentado com Halter'),   -- AJUS | Flexão de Pulso Neutra Sentado com Halteres
    ('8716e30f-596f-58c8-87ab-988b6992d9b7'::uuid, 'Flexão com Punhos Fechados'),   -- PADR | Flexão de Punho Fechado
    ('6849665e-f2f8-5b7a-8c25-b91d5788ee67'::uuid, 'Rosca de Punho Reversa com Anilha'),   -- AJUS | Flexão de Punho Reversa com Anilha
    ('49e50355-8d22-54dc-a977-7b144018228d'::uuid, 'Rosca de Punho Reversa com Barra sobre o Banco'),   -- AJUS | Flexão de Punho Reversa com Barra Sobre um Banco
    ('b6110705-4ad4-5fec-9d1f-856babc28df5'::uuid, 'Rosca de Punho Unilateral no Cabo Ajoelhado'),   -- ERRO | Flexão de Punho com Cabo em um Braço no Chão
    ('6492b12e-158a-5540-8a1e-311fa662e14e'::uuid, 'Rosca de Punho com Halteres'),   -- AJUS | Flexão de Punho com Halteres
    ('572d29dd-aa3a-5ba7-800b-d6889105f8e5'::uuid, 'Flexão com Queda entre Bancos'),   -- PADR | Flexão de Queda
    ('9bd84648-0d26-5f21-a324-9fe63c224c23'::uuid, 'Mergulho Sentado na Máquina'),   -- AJUS | Flexão de Tríceps na Máquina
    ('53ac7b9b-1fe7-5359-a7d5-a67dc82f3563'::uuid, 'Flexão com Elevação de Braço'),   -- PADR | Flexão de apoio com elevação de braço
    ('c5b606ef-244c-5d86-8cf8-c58ab71d4852'::uuid, 'Flexão Escapular'),   -- PADR | Flexão de braço com adução da escapula
    ('94029991-9dbb-5d17-8a6d-a8cdf9e70f1f'::uuid, 'Flexão em Parada de Mão com Déficit entre Bancos'),   -- ERRO | Flexão de braço com as mãos entre bancos
    ('1df89859-881c-51ae-94eb-f9986f7320dc'::uuid, 'Flexão com Palma'),   -- PADR | Flexão de braço com palmas
    ('8c3c4e4a-12b0-5451-894c-8b775fd5fa7f'::uuid, 'Flexão em Parada de Mão com Kipping'),   -- AJUS | Flexão de braço em posição de parada de mão com balanço
    ('c0105361-8bb4-5642-9f37-b779348ef69d'::uuid, 'Flexão Diamante com Joelhos Apoiados'),   -- PADR | Flexão de diamante de joelhos
    ('88d1911e-7f80-551e-bd2f-9f6b960d3d9a'::uuid, 'Flexão de Ombro com Faixa Elástica'),   -- PADR | Flexão de ombro com faixa
    ('e7fae1b6-6564-5f24-a070-bf9a264fbab6'::uuid, 'Flexão de Pernas Deslizante com Toalha'),   -- AJUS | Flexão de pernas com toalha
    ('4b17ccd9-4608-5d93-818d-46441178f2d8'::uuid, 'Flexão Pike com Mãos no Banco'),   -- ERRO | Flexão de pivô com banco
    ('5ca6afd5-de8b-5391-ba24-d2046670a10a'::uuid, 'Flexão Pike Profunda entre Cadeiras'),   -- ERRO | Flexão de pivô entre cadeiras
    ('f4efc717-6cc2-5188-8913-7f523768c557'::uuid, 'Flexão com Um Braço Assistida no Banco'),   -- PADR | Flexão de um braço com apoio
    ('24f94479-4238-541f-a9a1-0229e48d022a'::uuid, 'Flexão com Um Braço na Bola Medicinal'),   -- PADR | Flexão de um braço com bola medicinal
    ('ff4c217c-3d01-59ec-8646-d7a92ab388a9'::uuid, 'Flexão Diamante'),   -- PADR | Flexão diamante
    ('5b6ffeb8-e4d8-5e63-84cd-9c467ab343f5'::uuid, 'Flexão Pike'),   -- ERRO | Flexão em pivô
    ('110bdcba-ad42-5434-97c6-7f32f34dc0ca'::uuid, 'Flexão Hindu Modificada'),   -- PADR | Flexão hindu modificada
    ('323db88b-1918-5e1a-8aa3-747c0fce6b0b'::uuid, 'Flexão Inclinada'),   -- PADR | Flexão inclinada
    ('47ed255b-cca9-5dcf-943a-0f93df04c888'::uuid, 'Flexão na Parede'),   -- PADR | Flexão na parede
    ('db17b9b6-6425-5929-babe-ba61b3c40eca'::uuid, 'Flexão Nórdica'),   -- OK | Flexão nórdica
    ('4d304500-f1bf-5a6a-8a8b-884aeaad1004'::uuid, 'Flexão Plus'),   -- PADR | Flexão plus
    ('3de45d8f-69f2-5057-951c-441ee74fa554'::uuid, 'Flexão Reversa Apoiada nos Cotovelos'),   -- AJUS | Flexão reversa com cotovelos
    ('3bcdeb8b-d0b6-5067-8753-30c3ee972fc2'::uuid, 'Flexão em Parada de Mão na Parede'),   -- AJUS | Flexões de apoio de mão na parede
    ('025aae56-8944-5e58-adb0-7bbbb2c83572'::uuid, 'Flexão Hindu'),   -- AJUS | Flexões hindu
    ('961eddba-8210-5002-8258-dd485f16f8e1'::uuid, 'Gancho de Direita (Boxe)'),   -- PADR | Gancho de Direita
    ('2677ea1e-3957-5468-b00c-18246fb328f2'::uuid, 'Coice de Glúteo na Cadeira Extensora'),   -- PADR | Glúteo Coice Na Máquina De Extensão De Pernas
    ('e3c7aece-5327-5b35-a4ce-fb9eace3e5a0'::uuid, 'Coice de Glúteo no Smith'),   -- PADR | Glúteo Coice No Smith
    ('0e6cec84-50c6-5229-a2eb-74e5921a579e'::uuid, 'Coice de Glúteo com Gymstick'),   -- PADR | Glúteo Coice com Gymstick
    ('976a52bd-24ee-5746-a50d-6bff5dbeaeca'::uuid, 'Coice de Glúteo com Perna Flexionada e Miniband'),   -- AJUS | Glúteo Coice com Pernas Flexionada com Faixa
    ('46ec270e-1dc6-5172-aed2-bed7d5addf18'::uuid, 'Coice de Glúteo em Pé com Faixa Elástica'),   -- PADR | Glúteo Coice em Pé com Faixa Elástica
    ('50be13bb-03bb-56d3-a8f1-4a8cf068de4a'::uuid, 'Coice de Glúteo na Máquina Multi-Hip'),   -- AJUS | Glúteo Coice na Máquina
    ('c154addc-6ca3-5cf4-b630-f78bf9004969'::uuid, 'Coice de Glúteo Unilateral na Polia Baixa'),   -- ERRO | Glúteos Coice Unilateral na Polia Baixa
    ('9d2efb07-c81a-544c-a639-eb2fc050bbbb'::uuid, 'Coice de Glúteo em Quatro Apoios com Faixa Elástica'),   -- AJUS | Glúteos Coice com Faixa Elástica
    ('1ff60154-1068-5bbd-81f3-77ed6482dd1f'::uuid, 'Barra Fixa Assistida na Máquina'),   -- PADR | Graviton
    ('3a17152c-357f-59f2-9b9f-5820707584aa'::uuid, 'Hand Grip (Flexor de Mão)'),   -- AJUS | Hand Grip
    ('3f83b50b-811a-56b9-b663-ce8dc41f73e2'::uuid, 'Ergômetro de Braço'),   -- AJUS | Hands Bike
    ('2a0a3579-f68f-590e-a0b1-37d840b7df2b'::uuid, 'Hiperextensão Lombar'),   -- PADR | Hiperextensão
    ('ef0b4d49-b7d2-5c98-a43c-0960f6c033a3'::uuid, 'Hiperextensão Reversa Sapo no Banco'),   -- PADR | Hiperextensão Invertida de Sapo
    ('617b4438-d7e9-5999-925a-69247a77a9e5'::uuid, 'Hiperextensão Reversa com Faixa Elástica'),   -- PADR | Hiperextensão Reversa com Faixa de Resistência
    ('cc4a492f-dbbd-5250-ac38-056a183efdee'::uuid, 'Hiperextensão com Rotação'),   -- PADR | Hiperextensão com Torção
    ('8b0e4504-9447-5b1e-84d7-a132ebdfbd8a'::uuid, 'Hiperextensão no Solo'),   -- PADR | Hiperextensão no Chão
    ('1d56027a-5495-52a3-a45b-fb405b967370'::uuid, 'Mergulho Impossível (Impossible Dips)'),   -- AJUS | Impossible Dips
    ('db5d1de5-a818-5618-9294-ff4b500bf033'::uuid, 'Thruster com Barra'),   -- ERRO | Impulso com barra
    ('3d2ceac0-3429-5524-958c-d8d860359a20'::uuid, 'Inclinação Lateral de Tronco'),   -- PADR | Inclinação Lateral
    ('590b871a-31f1-5674-aba3-16e9d720c8cf'::uuid, 'Inclinação Lateral em Pé com Braço Estendido'),   -- PADR | Inclinação Lateral em Pé
    ('04a9298f-169a-575b-ae78-70bc7c836ae5'::uuid, 'Retroversão Pélvica'),   -- PADR | Inclinação Pélvica
    ('ebae7f5e-504d-5ce4-ba4e-89a457d89f96'::uuid, 'Joelho ao Peito Alternado Deitado'),   -- PADR | Joelho Alternado no Peito
    ('f2a5d535-79c5-5709-8803-1f3a9c62eedb'::uuid, 'Joelhos Altos Apoiado na Parede'),   -- AJUS | Joelhos altos contra a parede
    ('85267546-045c-59ac-bd1c-5d60b2df155e'::uuid, 'Hang Clean com Kettlebell'),   -- AJUS | Kettlebell Hang Clean
    ('b0eea968-2d00-5550-8c5f-bf9d08f8683c'::uuid, 'Oito com Kettlebell (Figure 8)'),   -- AJUS | Kettlebell em Forma de Oito
    ('b666d09c-a3f3-535b-b648-702df1110377'::uuid, 'Arremesso de Bola Medicinal Deitado'),   -- PADR | Lançamento de Bola Medicinal deitado
    ('c690086d-745a-5625-9316-5c2523075873'::uuid, 'Arremesso de Bola Medicinal acima da Cabeça'),   -- AJUS | Lançamento de bola medicinal
    ('2f9e2c9d-15cb-5fde-b4a5-59d961d0e55b'::uuid, 'Leg Press 45°'),   -- PADR | Leg Press
    ('8c59374a-0f9a-5a64-9dc7-635d6d9c2023'::uuid, 'Leg Press Unilateral'),   -- PADR | Leg Press unilateral
    ('fe804c1b-c9cd-5932-bb7d-d5c075285b7f'::uuid, 'Leg Press 90° no Smith'),   -- AJUS | Leg press 90 no smith
    ('abb5628a-094d-5544-a677-714b65e185a3'::uuid, 'Subida Unilateral Assistida na Máquina'),   -- AJUS | Leg press unilateral Assistido
    ('6d35203d-bad1-5a4c-b4c0-3a921ef63526'::uuid, 'Abdução em Quatro Apoios (Fire Hydrant)'),   -- AJUS | Levantamento Lateral de Perna em Quatro Apoios
    ('37737670-9d4a-5f51-82c0-c83c0a7120a9'::uuid, 'Levantamento Terra Sumô com Barra'),   -- AJUS | Levantamento Terra Sumô
    ('ae2877e2-50e0-5ced-97f7-7d5a106deaeb'::uuid, 'Levantamento Terra Unilateral com Peso Corporal'),   -- AJUS | Levantamento Terra Unilateral
    ('e980b4d8-5e81-5d24-a734-1c2dcc98c5e1'::uuid, 'Levantamento Terra no Landmine'),   -- PADR | Levantamento Terra com Barra no Landmine
    ('145bd851-5bba-5139-999e-a380a689b3fa'::uuid, 'Levantamento Turco (Turkish Get-Up)'),   -- PADR | Levantamento Turco
    ('61b54728-c357-5477-8266-b7bf941d737a'::uuid, 'Levantamento Terra Parcial no Rack (Rack Pull)'),   -- AJUS | Levantamento com Suporte
    ('7fdb01f1-76a6-584d-b18c-730c46f4f92e'::uuid, 'Deslizamento de Braços na Parede (Wall Slide)'),   -- AJUS | Levantamento de braço apoiado na parede
    ('7ad4e842-48be-5d1a-9b0c-21a333ae6f94'::uuid, 'Elevação em 3 Direções com Halteres'),   -- AJUS | Levantamento de halteres de 3 maneiras
    ('bf2c4d46-3fee-5d25-8b85-79c701b60af5'::uuid, 'Panturrilha Burrinho Apoiado no Banco'),   -- AJUS | Levantamento de panturrilha com apoio de banco
    ('bbeeed3f-3024-5c75-a1df-e517464d275d'::uuid, 'Panturrilha Burrinho Unilateral Apoiado no Banco'),   -- AJUS | Levantamento de panturrilha com apoio de uma perna
    ('b4261b30-964d-5d32-869c-a8be976b33dd'::uuid, 'Panturrilha Burrinho com Parceiro'),   -- ERRO | Levantamento de panturrilha com apoio e sobrecarga
    ('f7eeca9e-8914-5ffd-a848-e571a07ca6f5'::uuid, 'Panturrilha Burrinho na Máquina'),   -- AJUS | Levantamento de panturrilha na Máquina
    ('806f4b16-6de9-526b-9ab9-04112303795d'::uuid, 'Elevação Frontal Alternada Sentado com Halteres'),   -- AJUS | Levantamento frontal alternado com haltere sentado
    ('7d71e4e1-4b6f-59f7-9f7f-bc7249e7fa15'::uuid, 'Elevação Frontal com Anilha'),   -- AJUS | Levantamento frontal com anilha
    ('00e9137d-f835-523d-9e6e-80f7bc09e475'::uuid, 'Elevação Frontal no Landmine'),   -- AJUS | Levantamento frontal com barra
    ('e4671d83-da5b-5750-a0d7-c57f6ffb02b1'::uuid, 'Elevação Frontal Bilateral no Cabo'),   -- AJUS | Levantamento frontal de cabo com dois braços
    ('76f3ea6c-14af-5b5d-8a5b-63c8cf06ee38'::uuid, 'Elevação Frontal Unilateral no Cabo'),   -- AJUS | Levantamento frontal unilateral com cabo
    ('a509bd20-a5c3-59bb-9a81-69e91442eb34'::uuid, 'Elevação Lateral com Kettlebells'),   -- AJUS | Levantamento lateral com kettlebell
    ('de7ac1d0-a94e-54d3-8d08-5e7ac326ad6f'::uuid, 'Levantamento Terra Romeno com Halteres'),   -- PADR | Levantamento terra romeno com halteres
    ('836df378-c671-5ece-a7e0-60f3203d408e'::uuid, 'Arremesso Rotacional de Bola Medicinal'),   -- AJUS | Medicine Ball Rotational Throw
    ('88d05297-4c36-56b7-bd03-348aa5b5252f'::uuid, 'Face Pull Semiajoelhado no Cabo'),   -- AJUS | Meio Agachado com Puxada para o Rosto no Cabo
    ('7742b513-dc03-5294-a3a3-62cd5c473bcd'::uuid, 'Mergulho Assistido na Máquina'),   -- PADR | Mergulho de peito assistido
    ('942f067b-5e83-5374-8c4d-94f7931a6fb9'::uuid, 'Mergulho de Tríceps nas Paralelas'),   -- AJUS | Mergulho de tríceps
    ('eb30a555-f57d-5a1d-bcd0-527e03fab772'::uuid, 'Mergulho de Tríceps na Máquina (Alavanca)'),   -- PADR | Mergulho de tríceps na Máquina
    ('18835109-4f9f-539a-b6da-9474ea00a637'::uuid, 'Mergulho Assistido para Tríceps'),   -- AJUS | Mergulhos Assistidos para Tríceps
    ('1328d160-cb37-5844-9588-486eabb189ae'::uuid, 'Mergulho de Tríceps no Solo'),   -- AJUS | Mergulhos para tríceps no chão
    ('58b85b43-d4cc-5829-b144-493787ef4620'::uuid, 'Mesa Flexora'),   -- PADR | Mesa flexora
    ('5d702573-30c3-5fb7-84cf-a8d14ea07ef9'::uuid, 'Minhoca (Inchworm)'),   -- PADR | Minhoca
    ('a8c49aef-0011-53db-a6bc-1e431fa58645'::uuid, 'Moinho de Vento com Kettlebell'),   -- PADR | Moinho com Kettlebell
    ('d44848e1-4b5e-543c-b236-50539f90da52'::uuid, 'Moinho de Vento com Halter'),   -- PADR | Moinho de vento com haltere
    ('c7926e57-307d-5f7b-8b04-fa04268fb843'::uuid, 'Muscle Snatch com Barra'),   -- AJUS | Muscle Snatch
    ('a169c688-ceca-585b-8848-b9e8e2897b8c'::uuid, 'Muscle-Up'),   -- PADR | Muscle up
    ('b9e061c2-d293-55b8-899d-3f4a0424d55f'::uuid, 'Elíptico'),   -- AJUS | Máquina Elíptica
    ('819104b3-176a-5bb6-b948-045fe7d42ddb'::uuid, 'Simulador de Escada'),   -- AJUS | Máquina Simulador Escada
    ('90d12d3e-dfa7-52f8-85b1-fd3ca706d54e'::uuid, 'Simulador de Caminhada (Air Walker)'),   -- AJUS | Máquina de Caminhada Ondulatório
    ('0ab059ca-5a68-5d39-940e-3b18841a4f51'::uuid, 'Burpee Navy SEAL'),   -- ERRO | Navy Seal Burpee
    ('f453a6dc-1663-5295-aad0-3c8bdcacda1f'::uuid, 'Elevação de Panturrilha no Step com Halteres'),   -- AJUS | Panturrilhas em Pé
    ('a7b5972e-9336-57d4-99e6-a74e44e5eab7'::uuid, 'Mergulho nas Paralelas'),   -- AJUS | Paralela
    ('7f0bb706-1ba1-56c5-8188-e6f5d69fb8c6'::uuid, 'Mergulho nas Paralelas com Ênfase no Peitoral'),   -- AJUS | Paralelas
    ('570d206c-f743-5427-ba86-a9f05a256932'::uuid, 'Mergulho entre Cadeiras'),   -- AJUS | Paralelas entre Cadeiras
    ('d3ad0b18-7396-5134-8af0-b4bcef189052'::uuid, 'Mergulho nas Argolas'),   -- AJUS | Paralelas na Argola
    ('aff44941-afd1-5489-9902-f7c2adacb6e0'::uuid, 'Passe de Peito com Bola Medicinal na Parede'),   -- AJUS | Passagem de Bola Medicinal de Peito em Pé
    ('e05ca40b-5db6-5410-9072-48b61a846019'::uuid, 'Afundo Reverso com Elevação de Joelho'),   -- AJUS | Passo Invertido com Elevação do Joelho
    ('7e9d3e63-a662-50c3-8a25-822ea9e10581'::uuid, 'Passo Lateral Rápido'),   -- PADR | Passo Lateral em Alta Velocidade
    ('efe0626d-2c4c-52bf-863e-3f3a41570561'::uuid, 'Patinador (Skater Jump)'),   -- AJUS | Patinador
    ('f926f52d-f147-5fe3-bc18-9c4de3b8a723'::uuid, 'Flexão em Planche'),   -- PADR | Planche com Flexão de Braço
    ('09e651d7-6fde-5e68-9457-fff2ea584d33'::uuid, 'Polichinelo com Agachamento Sumô'),   -- AJUS | Polichinelo Frontal
    ('b7fb3049-fa08-5c5f-b181-2368404b405b'::uuid, 'Polichinelo'),   -- AJUS | Polichinelos
    ('fa859a0b-974b-57e8-915b-d73811434831'::uuid, 'Ponte de Glúteos Unilateral com Perna Estendida'),   -- AJUS | Ponte Unilateral Com Uma Perna Levantada
    ('f736e61e-7a77-5e0f-978a-cc6dca8ecd60'::uuid, 'Ponte de Glúteos Unilateral com Pé no Banco'),   -- PADR | Ponte Unilateral no Banco
    ('f178849d-0c29-53b5-ad56-b2d61005e711'::uuid, 'Ponte de Glúteos com Miniband'),   -- PADR | Ponte com Faixa Elástica
    ('ba9655da-86ef-5cd0-b3eb-68ab80198f21'::uuid, 'Ponte de Glúteos com Halter'),   -- PADR | Ponte com Halteres
    ('d0e6118d-03b8-520f-a038-62fbcfac1788'::uuid, 'Ponte de Glúteos Unilateral com Perna Elevada'),   -- AJUS | Ponte em Unilateral
    ('9e9e1a9f-61ed-558b-b6e9-741093f9f773'::uuid, 'Postura da Cobra'),   -- AJUS | Postura da Cobra - Alongamento Abdominal
    ('2b98fa01-bff6-5111-8b05-2cc03857e87f'::uuid, 'Postura de Virilha Sentado'),   -- PADR | Postura da Virilha Sentada
    ('2281b12d-d38e-5797-b3d6-273555098468'::uuid, 'Postura de Meio Sapo'),   -- PADR | Postura de meio sapo
    ('6d67a44b-839a-5040-8df2-c55b7a13be50'::uuid, 'Postura do Peixe'),   -- PADR | Postura de peixe
    ('0c05b07d-b212-53f4-8a82-f98a553f24b1'::uuid, 'Postura do Arco com Balanço'),   -- PADR | Postura do Arco Oscilante
    ('09d47bd7-5931-57f1-9a6a-581bc51dbb5a'::uuid, 'Postura do Sapo'),   -- PADR | Postura do sapo
    ('d6c9f705-2c0a-5f46-a206-f18da6242b6c'::uuid, 'Power Clean com Barra'),   -- AJUS | Power Clean
    ('c9b19ecb-cd79-5882-ae8f-cb41210935c8'::uuid, 'Protração e Retração Escapular'),   -- PADR | Protração e retração da escápula
    ('45b98976-fe0f-57ec-a1d7-e466d834aea8'::uuid, 'Pulldown com Braços Estendidos na Corda'),   -- AJUS | Pulldown com corda
    ('b276c9b3-120f-5eed-a131-c7cc2430aacc'::uuid, 'Pulldown Inclinado com Corda'),   -- PADR | Pulldown inclinado com corda
    ('a28cf315-03c0-539f-b89e-07f8df3e00cd'::uuid, 'Pulldown Unilateral com Braço Estendido no Cabo'),   -- AJUS | Pulldown unilateral com corda
    ('29970d91-9ced-51be-8adc-e198914566d0'::uuid, 'Pullover com Barra W Pegada Invertida'),   -- PADR | Pullover com Barra W Pegada invertida
    ('b7d108e3-7b60-5c18-8940-2c06da444a43'::uuid, 'Pullover Deitado no Cabo'),   -- PADR | Pullover com Cabo
    ('7a1970fd-689e-5e5a-a2f7-c63781194c60'::uuid, 'Pullover com Halter na Bola Suíça'),   -- PADR | Pullover com Halteres na Bola de Estabilidade
    ('ced48215-0ed8-5e92-97a3-007358102890'::uuid, 'Pullover com Barra no Banco Declinado'),   -- PADR | Pullover com barra no banco declinado
    ('421febc2-898b-530d-b9d6-76c04b8cd89f'::uuid, 'Pullover Sentado no Cabo'),   -- PADR | Pullover com cabo sentado
    ('7d1fcd6b-647c-55a4-81b1-d294879b4850'::uuid, 'Pullover com Halter'),   -- PADR | Pullover com haltere
    ('e43489f2-8f67-5420-8fb9-41b51fc9b841'::uuid, 'Pullover com Halteres e Pernas Elevadas'),   -- AJUS | Pullover de braço reto com halteres (joelhos a 90 graus)
    ('4f65f54b-82fc-54c5-bc9d-f258c3ab8304'::uuid, 'Pullover na Máquina (Alavanca)'),   -- AJUS | Pullover na Máquina
    ('135bc200-0b7a-57d7-832e-23be4c1cce58'::uuid, 'Elevação Pélvica Unilateral no Banco'),   -- ERRO | Pulo de impulso de quadril de uma perna
    ('f3bca6f8-ff34-5c3f-8024-5cd7137128d0'::uuid, 'Salto com Abertura em Agachamento Sumô'),   -- AJUS | Pulos com Abertura de Pernas
    ('6099d1d0-7bf9-53dc-ab17-00488cf63c8f'::uuid, 'Salto com Elevação de Joelho'),   -- PADR | Pulos de Joelho Elevado
    ('e4796c7d-1e34-51e1-9585-297fc984ebcb'::uuid, 'Puxada Alta Pegada Supinada'),   -- AJUS | Puxada Alta Invertida
    ('b2191802-8c70-5c4a-bde8-8aa707685462'::uuid, 'Puxada Alta Neutra Ajoelhado com Dois Cabos'),   -- ERRO | Puxada Alta Neutra com Cabos Duplos no Chão
    ('cab868a4-03df-5a5b-b1fb-2e9adb0fdd8b'::uuid, 'Puxada Unilateral Semiajoelhado no Cabo'),   -- AJUS | Puxada Alta com Um Joelho Apoiado
    ('9e7ad364-21e0-58ff-94bb-ebbe5014d321'::uuid, 'Puxada Alta na Máquina (Alavanca)'),   -- AJUS | Puxada Alta na Máquina
    ('f02c74cf-1c69-53fa-b9db-941e5617a2bb'::uuid, 'Pull-Through Ajoelhado no Cabo'),   -- AJUS | Puxada De Cabo Ajoelhada
    ('d9177dd4-370e-532f-98c4-7f7f95251262'::uuid, 'Remada em Front Lever'),   -- AJUS | Puxada Front Lever
    ('7f21efdf-b0ac-596e-9304-0f2f754414b4'::uuid, 'Puxada Ajoelhado com Faixa Elástica'),   -- PADR | Puxada ajoelhada com banda de resistência
    ('9e2c7e72-f528-5009-9040-dd931d18e1b2'::uuid, 'Puxada atrás da Nuca na Máquina'),   -- AJUS | Puxada alta na Máquina Nuca
    ('691c2ef7-292c-52e8-bd29-1b889a3f9ed7'::uuid, 'Puxada atrás da Nuca na Polia'),   -- AJUS | Puxada alta na polia nuca
    ('6d75c4c5-837e-545f-a5c9-1f580b83909d'::uuid, 'Puxada Alta Unilateral Ajoelhado'),   -- AJUS | Puxada alta unilateral alta ajoelhada
    ('f69770ef-a2a5-52c4-a944-88a7093df68d'::uuid, 'Levantamento Terra com Halter entre as Pernas'),   -- AJUS | Puxada com Halteres entre as Pernas
    ('8efe541c-8c4f-5239-9b07-ff448c18d606'::uuid, 'Puxada Unilateral Sentado no Cabo'),   -- PADR | Puxada com Um Braço com Cabo
    ('0a874551-77fd-523e-8114-8da19ad68e2d'::uuid, 'Barra Fixa com Halter entre os Pés'),   -- AJUS | Puxada com Um Braço com Peso Adicional
    ('7ea23869-df01-557f-8e0a-eb6156189db9'::uuid, 'Face Pull com Cabos Cruzados'),   -- PADR | Puxada de face com cabo cruzado
    ('9875f742-7c37-5b29-bb0a-051ad906e789'::uuid, 'Puxada Unilateral em Pé com Rotação no Cabo'),   -- PADR | Puxada em Pé com Torção no Cabo
    ('d24c8a77-ec24-505a-b784-67cb1cd6046b'::uuid, 'Puxada Escapular na Barra Fixa'),   -- PADR | Puxada escapular na barra fixa
    ('e49a019c-520a-5188-9ee7-394d5eed296f'::uuid, 'Barra Fixa Isométrica'),   -- AJUS | Puxada isométrica
    ('1f3f0b22-6cc6-5179-98c2-f3844c1a1be4'::uuid, 'Puxada Alta Pegada Fechada'),   -- PADR | Puxada na Polia Alta com Pegada Fechada
    ('4242a6fb-17c8-581f-8943-ee9edb99fddd'::uuid, 'Face Pull Ajoelhado'),   -- PADR | Puxada para o Rosto de Joelhos
    ('a00ca4b4-d35e-530d-9b66-5ef65b634a36'::uuid, 'Pull-Through com Faixa Elástica'),   -- ERRO | Puxar com Faixa Elástica
    ('663781a5-fd6d-53a0-9b98-2537e5fff799'::uuid, 'Pêndulo de Ombro (Codman)'),   -- PADR | Pêndulo de ombro
    ('c6fe56d2-1e2b-51bd-bcd7-9a983228d87f'::uuid, 'Perdigueiro (Bird Dog)'),   -- AJUS | Quadrúpede com elevação de braço e perna contralateral
    ('fb3814e9-89fc-5f3e-876b-3077d13dbc72'::uuid, 'Levantamento Terra Romeno'),   -- OK | RDL
    ('cab4b690-c0d8-5483-8981-f83bcced6bc9'::uuid, 'Engatinhar do Urso (Bear Crawl)'),   -- AJUS | Rastejo de Urso
    ('bbf3aa8e-1f53-56ed-b3e7-1b246557672a'::uuid, 'Remada Alta com Halteres'),   -- AJUS | Remada Alta
    ('d1a5383d-3caa-58f5-b4d2-f29a0a090134'::uuid, 'Remada Alta com Barra W'),   -- PADR | Remada Alta Com Barra W
    ('1c620f11-7e04-5f30-a33a-29a54cbf30ef'::uuid, 'Remada Alta no Cabo'),   -- PADR | Remada Alta com Cabo
    ('9e9cefed-76fd-5d16-8650-83ba5d44895f'::uuid, 'Remada Alta com Halter Único'),   -- AJUS | Remada Alta com Halter
    ('83a24f20-84d2-57ad-8ff3-e729b2d81e0e'::uuid, 'Remada com Barra Apoiada no Banco Inclinado'),   -- AJUS | Remada Curvada Inclinada com Barra
    ('552b8970-aff0-58d9-9484-74be5eaf2bf2'::uuid, 'Remada Curvada Pegada Supinada com Barra'),   -- PADR | Remada Curvada com Pegada Invertida na Barra
    ('73b16dce-dc06-581e-a529-344bd078fe20'::uuid, 'Remada Cavalinho (T-Bar) com Barra'),   -- AJUS | Remada Curvada em T
    ('f765b56e-7f60-5837-bdce-c0be0a961562'::uuid, 'Remada Curvada no Cabo'),   -- AJUS | Remada Inclinada com Cabo
    ('d7305076-7cbb-5c05-a042-406ee0e3bde0'::uuid, 'Remada Apoiada no Banco Inclinado Pegada Neutra com Halteres'),   -- PADR | Remada Inclinada com Pegada Neutra com Halteres
    ('d264ee5d-fcc5-5f8c-8b57-30ea2ae184c5'::uuid, 'Remada Apoiada no Banco Inclinado Pegada Supinada com Halteres'),   -- PADR | Remada Inclinada com Pegada Reversa com Halteres
    ('bfb31ef8-a6b1-5663-8044-e892d88966f3'::uuid, 'Remada Apoiada no Banco no Cabo'),   -- PADR | Remada Inclinada no banco com Cabo
    ('f9a2fd43-ccec-585e-8e1b-190f4d51f694'::uuid, 'Remada Invertida no Smith'),   -- PADR | Remada Invertida
    ('9b191e87-3d34-5767-813b-55a7a39b534a'::uuid, 'Remada Invertida nas Argolas'),   -- PADR | Remada Invertida Com Argolas
    ('149fecd4-58b1-5139-a704-5c2d37cbf8d0'::uuid, 'Crucifixo Inverso Curvado no Cabo'),   -- ERRO | Remada Invertida com Cabo Inclinado
    ('e456154d-c8a1-512e-bb87-c91a7e3b22f4'::uuid, 'Remada Sentada na Máquina com Anilhas'),   -- AJUS | Remada Sentada com Anilhas
    ('c8479334-d145-512e-b4bd-fe8e77d335d2'::uuid, 'Remada Sentada no Cabo'),   -- PADR | Remada Sentada com Cabo
    ('74dfa8b4-a086-500c-98f6-ee8fa3672d9f'::uuid, 'Remada Sentada na Polia com Pegador Reto'),   -- ERRO | Remada Sentada com Corda na Polia
    ('1a919a0d-45b5-5ed6-8cb6-65dbf32eec61'::uuid, 'Remada Cavalinho no Landmine'),   -- PADR | Remada T com Landmine
    ('562041da-c75e-5354-983a-b49154b7682d'::uuid, 'Remada Cavalinho na Máquina (Alavanca)'),   -- AJUS | Remada T na Máquina
    ('c42a18cb-0bd2-5158-a7e5-2682ad23b55f'::uuid, 'Remada Unilateral em Pé no Cabo'),   -- PADR | Remada Unilateral com Cabo
    ('26731a93-ef1d-5a46-9405-33061dac309b'::uuid, 'Abertura com Faixa Elástica (Pull-Apart)'),   -- ERRO | Remada afastada com banda de resistência
    ('87d7d118-b786-55b4-ad30-2ab62e8b1d50'::uuid, 'Remada Alta Unilateral com Halter'),   -- PADR | Remada alta com halteres unilateral
    ('6a76b5cd-2a0c-5980-9324-035f624947eb'::uuid, 'Remada Sentada Unilateral com Rotação no Cabo'),   -- PADR | Remada com Cabo Sentada Unilateral com Torção
    ('8ba5201a-83df-51e1-bbaa-e9a2f7af70f9'::uuid, 'Crucifixo Inverso Curvado com Faixa Elástica'),   -- AJUS | Remada com banda de resistência curvada para deltoides posterior
    ('c7cda0bd-60bf-5326-a102-c6e5b54ffeba'::uuid, 'Remada Alta com Barra'),   -- ERRO | Remada com barra
    ('9c14bb79-0fbb-5013-ac07-70d93cd38f6f'::uuid, 'Remada com Barra atrás do Corpo'),   -- AJUS | Remada com barra curvada para trás
    ('7a57245f-6345-5b8f-b690-7350813aa4fc'::uuid, 'Remada Unilateral Aberta com Halter (Deltoide Posterior)'),   -- PADR | Remada com halteres para a posterior de ombros
    ('5b31e2af-8985-57ea-9107-a3abcf4db62c'::uuid, 'Remada com Peso Corporal na Porta'),   -- PADR | Remada com o Peso do Corpo na Porta
    ('8b73bd08-e5cb-51fd-9a8a-2ba1ab0b923b'::uuid, 'Puxada Cruzada no Crossover'),   -- AJUS | Remada cruzada no cross
    ('3f57d45b-660e-532e-a4b0-26ccb2d753ab'::uuid, 'Remada Curvada com Barra Pegada Mista'),   -- AJUS | Remada curvada com barra de pegada alternada ampla com adução de escapula
    ('0a46cde1-4bf6-5758-8f94-b776ea587959'::uuid, 'Remada Curvada com Halteres'),   -- PADR | Remada curvada com halteres
    ('a8eccd14-d32a-5a0e-9612-bba83a17aaa6'::uuid, 'Remada Curvada Pegada Supinada com Halteres'),   -- PADR | Remada curvada com halteres com pegada invertida
    ('a8a8884c-5c4b-5bb6-89cc-66b67b55d9ff'::uuid, 'Remada Curvada com Kettlebells'),   -- PADR | Remada curvada com kettlebell
    ('00021572-ea2a-5d8a-b7cd-3b2808e287ef'::uuid, 'Remada para Deltoide Posterior Sentado com Halteres'),   -- PADR | Remada de deltoide posterior sentado com haltere
    ('91c6af2f-410c-5bb7-afde-bf76580c6022'::uuid, 'Remada Espingarda no Cabo'),   -- PADR | Remada de espingarda
    ('f63bada2-f2b7-516b-b893-368bfe35889e'::uuid, 'Elevação em Y no Cabo'),   -- AJUS | Remada em Y com cabo
    ('2f0fdf11-7367-5ba9-914b-c11d3096595e'::uuid, 'Puxada Frontal na Máquina (Alavanca)'),   -- ERRO | Remada frontal na máquina
    ('2018ec06-be90-5f56-bf85-1807ce60114e'::uuid, 'Crucifixo Inverso Deitado nos Cabos'),   -- AJUS | Remada inversa com cabos deitado
    ('380f2513-867f-5de0-b51d-723a0c5809d7'::uuid, 'Remada Sentada com Faixa Elástica'),   -- PADR | Remada sentada com faixa
    ('d26341a2-56a1-5130-bb58-1f6dee4f1f5a'::uuid, 'Remada Sentada no Cabo Pegada Fechada'),   -- PADR | Remada sentado com cabo pegada fechada
    ('110dbb19-c895-56cb-89fb-bbff2b7a8167'::uuid, 'Remada Unilateral no Landmine'),   -- PADR | Remada unilateral com barra landmine
    ('2f357a28-20b8-5886-8d18-1e4317fac5e2'::uuid, 'Remada Unilateral com Gymstick'),   -- PADR | Remada unilateral com gymstick
    ('2d1b4f95-3d14-5611-9ad8-730ac4cbdcb6'::uuid, 'Liberação Miofascial com Rolo: Isquiotibiais'),   -- AJUS | Rolagem de espuma para isquiotibiais
    ('5ec3e6df-243a-5471-a4f3-140ccf798cbd'::uuid, 'Liberação Miofascial com Rolo: Panturrilhas'),   -- AJUS | Rolamento de Espuma para Panturrilhas
    ('b7755bfd-9a3c-5dbc-8246-80cc2ca61633'::uuid, 'Liberação Miofascial com Rolo: Costas'),   -- AJUS | Rolamento de espuma nas costas
    ('632b1cd3-b753-5b2e-a82b-9598e26b4797'::uuid, 'Liberação Miofascial com Rolo: Quadríceps'),   -- AJUS | Rolamento de espuma nos quadríceps
    ('9c9d45ac-9577-5234-adff-1bdb477078ce'::uuid, 'Liberação Miofascial com Rolo: Romboides'),   -- AJUS | Rolamento de espuma nos romboides
    ('4486fca8-0c94-5ef5-b0a6-b52a5fd32302'::uuid, 'Rollout na Bola Suíça'),   -- AJUS | Rolamento na bola suíça
    ('265a5b04-7c13-59af-98a6-e54c751b6dfb'::uuid, 'Rolando como uma Bola (Pilates)'),   -- AJUS | Rolando como uma Bola
    ('0b9d0da0-2031-5a60-97f9-5342573e783c'::uuid, 'Rolo de Punho (Rolinho de Antebraço)'),   -- AJUS | Rolinho de antebraço
    ('4f275de0-d079-5ac9-8b05-545193d0d5d0'::uuid, 'Liberação Miofascial com Rolo: Glúteos'),   -- AJUS | Rolo de Espuma para os Glúteos
    ('85471bb9-c61c-5c2b-a13d-fe4522e1d1a7'::uuid, 'Liberação Miofascial com Rolo: Ombro Posterior'),   -- AJUS | Rolo de espuma ombro posterior
    ('3fcc1141-4f1d-58ca-82b0-d140b45fd6d8'::uuid, 'Liberação Miofascial com Rolo: Planta do Pé'),   -- AJUS | Rolo de espuma para fascite plantar
    ('e6fbbacc-adca-57c1-812a-f1b4042d3a61'::uuid, 'Liberação Miofascial com Rolo: Peitoral e Ombro Anterior'),   -- AJUS | Rolo de espuma para ombro e peito frontal
    ('7ac24423-8531-522c-a377-8dc9e2241943'::uuid, 'Rosca Alternada no Banco Inclinado com Halteres'),   -- AJUS | Rosca Banco Inclinado
    ('2d634659-e81c-575b-8bb7-d34e783f5734'::uuid, 'Rosca Bíceps Bilateral no Cabo em Banco Inclinado'),   -- PADR | Rosca Bilateral com Cabo em Banco Inclinado
    ('f2bbbc82-194b-5ddb-85e3-dd3e16538b5e'::uuid, 'Rosca Concentrada com Barra Sentado'),   -- ERRO | Rosca Concentrada com Pegada Fechada Sentado
    ('3d57785a-3983-54d6-b474-266be405c107'::uuid, 'Rosca Direta com Barra Deitado em Banco Alto'),   -- PADR | Rosca Direta com Barra deitado em Banco Alto
    ('5ce16878-4a4f-585d-b1fc-dc92c04ed533'::uuid, 'Rosca Direta com Barra Pegada Fechada'),   -- PADR | Rosca Direta com Barra em Pegada Fechada
    ('22c0f49a-5a02-558f-b3dc-ffd32a993b88'::uuid, 'Rosca Direta com Barra e Arm Blaster'),   -- AJUS | Rosca Direta com Barra no Banco Scott
    ('7b6f02de-6e6f-557e-838d-25a45e1b9e30'::uuid, 'Rosca Bíceps Deitado no Cabo'),   -- PADR | Rosca Direta com Cabo deitado
    ('bd21e33b-b34c-508d-98a6-71407bbd44ea'::uuid, 'Rosca Bíceps na Máquina'),   -- AJUS | Rosca Direta na Máquina
    ('62f869ef-d5dc-51d6-bcfd-0fa96b687567'::uuid, 'Rosca Martelo com Halteres e Arm Blaster'),   -- AJUS | Rosca Martelo com Halter no Banco Scott
    ('deb08e51-4bc0-5251-99ae-b3282ec12a60'::uuid, 'Rosca Scott Martelo com Halteres'),   -- AJUS | Rosca Scott com Halteres Martelo no Banco
    ('1e760fe9-132f-5a82-b493-25d386bb4e33'::uuid, 'Rosca Scott na Máquina (Alavanca)'),   -- AJUS | Rosca Scott na Máquina
    ('247f6dfc-2c7d-53b2-bfd7-2ccbef4bac9b'::uuid, 'Rosca Bíceps Unilateral no Cabo'),   -- PADR | Rosca Unilateral com Cabo
    ('3e9b2a66-b96c-51ee-9247-1272fed8a9bc'::uuid, 'Rosca Unilateral com Barra'),   -- ERRO | Rosca alternada com barra
    ('51f5c419-b002-533f-9271-04a6a14aa011'::uuid, 'Rosca Alternada Sentado com Halteres'),   -- PADR | Rosca alternada com halteres sentado
    ('355c89cd-a573-573c-b891-ea3361e10c7a'::uuid, 'Rosca Bíceps Alta com Halteres'),   -- PADR | Rosca bíceps alta com halteres
    ('2e6802ce-b946-5f92-a007-1c50835a48ac'::uuid, 'Rosca Bíceps Agachado no Cabo'),   -- ERRO | Rosca bíceps com cabo ajoelhado
    ('c1ad79b4-41c0-55b7-a497-835427fa848e'::uuid, 'Rosca Bíceps com Faixa Elástica'),   -- PADR | Rosca bíceps com faixa elástica
    ('8795dac0-b8b4-58c3-82e6-0766eeaf8f32'::uuid, 'Rosca Bíceps com Halteres'),   -- PADR | Rosca bíceps com halteres
    ('51f1dfc8-8294-5d48-aee2-43685b0b3460'::uuid, 'Rosca Bíceps Pegada Fechada com Barra W'),   -- PADR | Rosca bíceps com pegada fechada na barra W
    ('4941e9eb-0017-514e-8f7a-17c2eb853970'::uuid, 'Rosca Bíceps no Banco Inclinado com Cabo'),   -- PADR | Rosca bíceps inclinada com cabos
    ('8981164f-5848-57b6-b2fb-32b3c17215f9'::uuid, 'Rosca Bíceps Sentado com Halteres'),   -- AJUS | Rosca bíceps sentado
    ('97e2087c-6f87-57da-9224-ed3258942173'::uuid, 'Rosca Bíceps Unilateral com Halter'),   -- AJUS | Rosca bíceps unilateral
    ('66d82bf2-d190-5d26-9ee0-ce1486cccd15'::uuid, 'Rosca Bíceps Unilateral Pegada Invertida no Cabo'),   -- PADR | Rosca bíceps unilateral com pegada invertida em cabo
    ('925a7c67-3bdc-52c2-8687-d7a7716e39ba'::uuid, 'Rosca Bíceps Unilateral na Polia Alta'),   -- PADR | Rosca bíceps unilateral no cabo alto
    ('f13ff710-c39e-5d2b-91dc-3565ce3c37e3'::uuid, 'Rosca Bíceps com Halteres e Arm Blaster'),   -- AJUS | Rosca com Halteres no Banco Scott
    ('6d8e31aa-f88c-5c56-9482-b73e80f198cd'::uuid, 'Rosca Bíceps Bilateral na Polia Alta'),   -- AJUS | Rosca com Polia Alta
    ('39af9068-d462-50e3-a9ff-086c4128e4f8'::uuid, 'Rosca Alternada com Halteres'),   -- ERRO | Rosca com halteres
    ('b7c511ee-1e49-5eac-8b3f-b0c5792472fc'::uuid, 'Rosca Concentrada com Halter'),   -- AJUS | Rosca concentrada
    ('216e10d4-5a43-50b9-8504-bcd38df6f115'::uuid, 'Rosca Concentrada no Cabo'),   -- PADR | Rosca concentrada com cabo
    ('b4696d4e-6e8b-5a53-8215-5848a5691bb0'::uuid, 'Rosca Concentrada com Resistência da Perna'),   -- PADR | Rosca concentrada com perna
    ('7178fa81-0c88-5733-8fb9-a406c24dad8e'::uuid, 'Rosca Scott Unilateral no Cabo'),   -- ERRO | Rosca concentrada unilateral com cabo
    ('eb285cff-f74c-5a79-9fef-10206236ba4b'::uuid, 'Rosca Scott Unilateral em Pé com Halter'),   -- AJUS | Rosca de Bíceps com Halteres no Banco Scott
    ('e1d69b12-4d78-5ab5-a85b-c9517d42b4c3'::uuid, 'Rosca Bíceps na Polia Alta Sentado'),   -- AJUS | Rosca de Bíceps com Puxada de Cabo
    ('e9eb1fc8-395e-5514-a1d6-b7688f1dbe41'::uuid, 'Rosca de Punho Neutra com Anilhas'),   -- PADR | Rosca de Punho Pegada Neutra com Anilhas
    ('4b2e0279-1103-5be9-a0fd-9f8aaf5f2a76'::uuid, 'Rosca de Punho Reversa Sentado com Barra'),   -- AJUS | Rosca de Punho Reversa com Barra
    ('366c1aaa-e85b-5b0b-aa8d-6cf5e47aefd7'::uuid, 'Rosca de Punho com Barra atrás das Costas'),   -- PADR | Rosca de Punho com Barra Atrás das Costas
    ('c00739a0-dd76-533e-ae49-9bb6deea7dad'::uuid, 'Rosca Bíceps na Máquina (Alavanca)'),   -- AJUS | Rosca de bíceps na Máquina
    ('4c95ef07-5d9f-5135-ac1f-dae1c2c5adc4'::uuid, 'Rosca Bíceps Unilateral com Faixa Elástica'),   -- PADR | Rosca de bíceps unilateral com faixa de resistência
    ('557be3ac-8eae-532c-9eca-2b1e5e678eb3'::uuid, 'Rosca de Dedos com Barra atrás das Costas'),   -- AJUS | Rosca de dedo com barra
    ('f567e86b-b5ac-551f-963e-2592d3b30c52'::uuid, 'Rosca de Punho com Barra sobre o Banco'),   -- AJUS | Rosca de punho com barra
    ('c1df1689-c819-5573-9ea5-41c1ab08af9e'::uuid, 'Rosca Direta com Barra W'),   -- PADR | Rosca direta com barra w
    ('39eb3723-cc94-5b18-9590-53a191964d8e'::uuid, 'Rosca Inversa com Barra W'),   -- PADR | Rosca inversa com barra W
    ('82a5cc98-e1dc-5c54-a305-9b913cbbdb59'::uuid, 'Rosca Martelo com Halteres'),   -- AJUS | Rosca martelo
    ('eded16c1-1374-52fd-89eb-19a0d9166a51'::uuid, 'Rosca Martelo no Cabo com Corda'),   -- AJUS | Rosca martelo com corda
    ('fc056dd4-c8fe-5113-942e-958b5069fe49'::uuid, 'Rosca Martelo com Faixa Elástica'),   -- PADR | Rosca martelo com faixa de resistência
    ('db1de041-477c-54eb-a4ff-6e2e358f33b4'::uuid, 'Rosca Martelo com Garrafa de Água'),   -- PADR | Rosca martelo com garrafa de água
    ('f6015671-2a31-573d-a809-48e95bdca42a'::uuid, 'Rosca Scott Martelo Unilateral com Halter'),   -- AJUS | Rosca martelo com halteres no banco scott
    ('46896ead-cef1-5bcd-a016-dd784f35cf11'::uuid, 'Rosca Martelo Sentado com Halteres'),   -- AJUS | Rosca martelo sentada
    ('066cabdb-125b-59fc-a0e5-6adb648c646b'::uuid, 'Rosca Direta no Cabo com Barra'),   -- AJUS | Rosca no Cabo
    ('429c319c-6d4d-55f5-af2e-d6c5333c9237'::uuid, 'Rosca Direta com Barra Deitado no Banco Inclinado'),   -- AJUS | Rosca pronada no banco inclinado
    ('07e06aa1-6bc1-5909-a375-3010b132572a'::uuid, 'Rosca Scott Alternada com Halteres'),   -- AJUS | Rosca scott alternados com halteres
    ('7e6adc01-d07f-52f9-bf73-a4944bfcf62f'::uuid, 'Rosca Scott com Halteres'),   -- PADR | Rosca scott com halteres
    ('e25e055f-37e7-58e4-a739-b8287d44bb51'::uuid, 'Rosca Scott Unilateral em Pé com Halter (Banco Inclinado)'),   -- AJUS | Rosca scott unilateral com halteres
    ('71619419-a1ec-5cc1-8aa4-d7272e186c71'::uuid, 'Rosca Spider Unilateral em Pé com Halter'),   -- AJUS | Rosca spider com único haltere
    ('c8210b9c-8cf6-5e52-abba-3a5a05af14cc'::uuid, 'Rosca Spider Unilateral Deitado com Halter'),   -- AJUS | Rosca spider unilateral
    ('7faf0cd0-8344-588a-8e10-9f97d6452729'::uuid, 'Rotação Externa de Quadril Deitado com Faixa Elástica'),   -- AJUS | Rotação Externa De Quadril Com Faixa Elástica
    ('54874389-6e2d-54f1-b1cb-2a779a73db69'::uuid, 'Rotação Externa de Ombro no Cabo'),   -- PADR | Rotação Externa de Ombro com Cabo
    ('23ed3768-d22d-54dd-add2-eb504aad9d76'::uuid, 'Rotação Externa dos Pés com Faixa Elástica'),   -- PADR | Rotação Externa do Pé com Faixa Elástica
    ('f58641e7-cf2f-56bd-8e36-56d82037f610'::uuid, 'Rotação Interna de Quadril Sentado com Faixa Elástica'),   -- PADR | Rotação Interna do Quadril Sentado com Faixa Elástica
    ('bd6e7c43-ac23-5579-8bbf-bc623e9c1091'::uuid, 'Rotação Torácica em Quatro Apoios'),   -- PADR | Rotação da coluna torácica de joelhos
    ('ca7df7b6-d04c-592b-9af0-e65457bd02c5'::uuid, 'Rotação de Pés e Tornozelos Sentado'),   -- PADR | Rotação de Pé e Tornozelo
    ('dcab081d-1fff-56cf-92b3-7808f1f029bc'::uuid, 'Rotação Torácica Deitado (Open Book)'),   -- AJUS | Rotação do corpo superior deitado
    ('5205aca8-503b-54ef-9b94-2a98a6155367'::uuid, 'Rotação de Tronco em Pé'),   -- AJUS | Rotação em Pé
    ('c1c74f56-3fd4-5f32-94ab-68672cd12fba'::uuid, 'Rotação da Coluna Deitado'),   -- PADR | Rotação espinhal deitado
    ('c84465b1-c7be-5355-8702-79d7709b5d3d'::uuid, 'Rotação Externa de Ombro a 90° no Cabo'),   -- PADR | Rotação externa com cabo a 90 graus
    ('b29770af-0784-5525-af0d-0bbbce02a2a8'::uuid, 'Rotação Externa de Ombro Ajoelhado no Cabo'),   -- PADR | Rotação externa com cabo em posição de joelhos
    ('d8540fb3-270b-52ba-b158-0d726eb81150'::uuid, 'Rotação Externa de Ombro com Halter Apoiado no Banco'),   -- PADR | Rotação externa de halteres apoiada no banco
    ('f22d2050-982e-5673-8ca1-da6dbf29bc81'::uuid, 'Rotação Externa de Ombro com Faixa Elástica'),   -- PADR | Rotação externa de ombro com faixa elástica
    ('0679cf7c-0864-58f6-be6a-631e24d751ff'::uuid, 'Rotação Externa de Ombro sem Carga'),   -- PADR | Rotação externa do ombro
    ('6eddc2ec-81d0-5187-8ea5-01445cc3129e'::uuid, 'Rotação Externa de Ombro Deitado de Lado com Halter'),   -- PADR | Rotação externa do ombro deitado com haltere
    ('23c81ca4-e1ba-5069-b77b-58cdbf68a9c9'::uuid, 'Rotação Interna de Ombro a 90° no Cabo'),   -- PADR | Rotação interna de cabo a 90 graus
    ('633de9fd-0a7f-5525-948a-62f8329a4dfe'::uuid, 'Rotação Interna de Ombro no Cabo'),   -- PADR | Rotação interna de ombro com cabo
    ('8061f920-eb0a-5aa2-af46-e8f3dba4d8f6'::uuid, 'Rotação Interna de Ombro sem Carga'),   -- PADR | Rotação interna do ombro
    ('77558690-a9d2-5792-83d6-8a0874c7c4f4'::uuid, 'Rotação Interna de Ombro Sentado no Cabo'),   -- PADR | Rotação interna do ombro sentada com cabo
    ('b9c3f397-f3e9-5dbc-b6e4-0dd534e18829'::uuid, 'Rotação Torácica em Quatro Apoios com Braço Estendido'),   -- AJUS | Rotação para trás de joelhos
    ('2005c6cf-ef3a-595d-9615-74ae85156aac'::uuid, 'Salto Grupado (Tuck Jump)'),   -- AJUS | Salto com Joelhos Flexionados
    ('4f6eec95-c2dc-5834-bfcd-ec0e5237ff38'::uuid, 'Afundo com Salto e Halteres'),   -- AJUS | Salto com halteres dividido
    ('267c16db-f012-5bca-96cd-7a3af99f32c8'::uuid, 'Agachamento com Salto Grupado'),   -- AJUS | Salto em Agachamento com Joelhos Flexionados
    ('538e764e-59a5-584b-9cd3-d7dbbcef9cc1'::uuid, 'Salto na Caixa Unilateral'),   -- PADR | Salto em Caixa com uma Perna
    ('2e79386f-01d8-576c-82e1-be1edc66c476'::uuid, 'Salto Unilateral para a Frente'),   -- PADR | Salto em Uma Perna para a Frente
    ('fed751fe-36ea-5a84-814b-7741561b04de'::uuid, 'Salto na Caixa com Aterrissagem em Pistola'),   -- PADR | Salto na Caixa para Agachamento Pistola
    ('85ed0b16-03bf-5d64-8740-fd401ebdde4a'::uuid, 'Salto na Caixa 2 para 1'),   -- PADR | Salto para Caixa 2 para 1
    ('c556da0f-cd95-584d-8155-1565d10d3ba1'::uuid, 'Skipping com Impulsão (Power Skip)'),   -- AJUS | Saltos Potentes
    ('26126483-2fe7-57f8-a5ce-b19fb13568fa'::uuid, 'Polichinelo com Abertura Horizontal (Seal Jack)'),   -- AJUS | Saltos de afastamento
    ('6bd6141b-0de9-5ae2-98c8-27f6f6d84664'::uuid, 'Saltos em Tesoura'),   -- PADR | Saltos em tesoura
    ('d08d2546-09aa-58ec-8c83-9760ce45a75d'::uuid, 'Remada Serrote com Halter'),   -- PADR | Serrote
    ('1f683476-b9bb-5535-8a9e-03f979c46ae7'::uuid, 'Snap Jump (Prancha para Agachamento)'),   -- AJUS | Snap Jumps
    ('541ae273-16d5-56c0-b3b1-5bcdb88be2e8'::uuid, 'Direto de Direita no Saco'),   -- PADR | Soco direto de direita
    ('0ad063e5-9d6b-593d-8212-6913fddb678a'::uuid, 'Socos Alternados (Boxe)'),   -- AJUS | Socos
    ('817faae2-2f92-5c08-9f1d-f522f27776b0'::uuid, 'Subida no Step com Faixa Elástica'),   -- AJUS | Step com elástico
    ('50ae5b88-2328-5caf-8bef-600ce6eecd60'::uuid, 'Stiff Unilateral com Faixa Elástica'),   -- AJUS | Stiff com Elástico de Resistência
    ('f56eb755-edda-573c-bf04-7103f71d43f0'::uuid, 'Stiff com Halteres'),   -- AJUS | Stiff com Halter
    ('30a50289-16a6-58df-962f-e6197078f449'::uuid, 'Stiff com Barra'),   -- PADR | Stiff com barra
    ('1eda2d16-64d4-5953-8621-900472a3bbf0'::uuid, 'Subida no Banco com Elevação de Joelho'),   -- AJUS | Subida no Step com Elevação de Joelhos
    ('f441cf41-0b2c-599c-a284-269ee3271f9b'::uuid, 'Super-Homem (Superman)'),   -- PADR | Superman
    ('1fda0ab6-849f-5551-9a5f-6fc692e5bbb6'::uuid, 'Supino Declinado Unilateral Pegada Neutra com Halter'),   -- PADR | Supino Declinado Unilateral Pegada Martelo com Haltere
    ('43e06a26-a9a8-5566-b147-d519b63656af'::uuid, 'Supino Declinado na Máquina Articulada'),   -- AJUS | Supino Declinado na Máquina
    ('0c487a2a-1d46-5bd6-81be-0fb318af69fa'::uuid, 'Supino Inclinado Pegada Supinada com Halteres'),   -- PADR | Supino Inclinado com Halteres e Pegada Invertida
    ('6ba4dd2f-cf1e-500b-baae-ac5f2b2a81d1'::uuid, 'Supino Inclinado Pegada Neutra com Halteres'),   -- PADR | Supino Inclinado com Halteres em Martelo
    ('2d20a929-9c1c-5b42-958e-8050e94c78af'::uuid, 'Supino Inclinado na Máquina Pegada Neutra'),   -- PADR | Supino Inclinado na Máquina com Pegada Martelo
    ('eb9418fd-0ba9-5440-8ca7-21a26bfd5f56'::uuid, 'Supino Fechado Pegada Supinada'),   -- AJUS | Supino Invertido com Pegada Fechada
    ('2aaf07b6-ae05-523b-ae5f-f6e7d515739c'::uuid, 'Supino Reto com Barra'),   -- AJUS | Supino Reto
    ('a051ab20-e7c5-51db-8850-52dd33627c2a'::uuid, 'Supino Reto na Máquina Articulada'),   -- PADR | Supino Reto na Máquina
    ('1b574cb3-8324-5d9d-9626-e78d9993cffa'::uuid, 'Supino Unilateral Pegada Supinada com Halter'),   -- PADR | Supino Unilateral com Halteres com Pegada Reversa
    ('7b823dc6-579a-5ffa-b56a-b2a8dc420902'::uuid, 'Supino Unilateral em Pé no Cabo'),   -- PADR | Supino Unilateral no Cabo
    ('e330f1f9-1486-59f5-a696-39e7dbb1dc1f'::uuid, 'Svend Press com Anilha'),   -- AJUS | Supino com Anilha
    ('2dec0f86-3318-5994-b75b-57c17ede6194'::uuid, 'Supino Reto com Halteres'),   -- AJUS | Supino com Halteres
    ('a7354fa7-4bd1-5140-824d-7fe79870a311'::uuid, 'Supino Pegada Supinada com Halteres'),   -- PADR | Supino com Halteres Pegada Invertida
    ('47e85043-c9e4-5b13-b1b0-c98fb6ccf55c'::uuid, 'Supino Sentado Pegada Fechada no Cabo'),   -- PADR | Supino com Pegada Fechada Sentado com Cabo
    ('a77cd698-2d0c-58b3-a375-b34404188464'::uuid, 'Supino Inclinado no Smith'),   -- AJUS | Supino com banco inclinado no Smith
    ('03059786-28ba-54b6-a7eb-44015492dc57'::uuid, 'Supino Declinado com Barra'),   -- AJUS | Supino com barra declinado
    ('020e5caa-570f-5373-ae4e-63457e8c022a'::uuid, 'Supino no Chão com Barra (Floor Press)'),   -- PADR | Supino com barra no chão
    ('b6c5102a-c80c-50ec-bd8a-c245ca5c6a50'::uuid, 'Supino Sentado no Cabo'),   -- PADR | Supino com cabo sentado
    ('c1eaaff2-1187-5fc3-94f2-ded47c7c9207'::uuid, 'Supino Fechado com Halter Único'),   -- AJUS | Supino com haltere pegada fechada
    ('0c82c4f0-ed5a-59ff-b41c-94c15e04f9e7'::uuid, 'Supino Squeeze com Halteres'),   -- AJUS | Supino com halteres com pegada fechada
    ('e909ab84-4fe5-5470-b8cb-50ccdefe6d36'::uuid, 'Supino Unilateral com Kettlebell'),   -- PADR | Supino com kettlebell de um braço
    ('90689b82-eed4-5aff-b046-396780a6a8b1'::uuid, 'Supino no Chão com Kettlebells'),   -- PADR | Supino com kettlebell no chão
    ('0916acf3-957a-5e95-8c7a-60aea75851e9'::uuid, 'Supino Reto Pegada Aberta com Barra'),   -- AJUS | Supino com pegada aberta
    ('4a1133f0-f259-5081-aac3-73497f6037cb'::uuid, 'Supino Reto Pegada Fechada com Barra'),   -- AJUS | Supino com pegada fechada
    ('83e4f7e6-44ef-5216-9a51-5ebe9f2c4802'::uuid, 'Supino Declinado no Smith'),   -- PADR | Supino declinado na máquina Smith
    ('42badfbc-0cfd-57ee-8549-fd9d8954eddc'::uuid, 'Supino Declinado Pegada Neutra com Halteres'),   -- PADR | Supino declinado pegada martelo
    ('06a40e7c-7a54-5991-a75c-1001d93d0572'::uuid, 'Supino Inclinado com Barra'),   -- PADR | Supino inclinado com barra
    ('bfa09a60-72f6-5635-841b-9e16e0489ebb'::uuid, 'Supino Inclinado no Cabo'),   -- PADR | Supino inclinado com cabo
    ('980ea236-7c4c-5466-94e2-dd1713719195'::uuid, 'Supino Inclinado Pegada Fechada com Halteres'),   -- PADR | Supino inclinado com halteres e pegada fechada
    ('397ccf8b-f36d-50e2-872c-e14e2297b6f3'::uuid, 'Supino Inclinado Pegada Fechada com Barra'),   -- PADR | Supino inclinado com pegada fechada
    ('bf50b1f9-7467-502c-a7e6-4090ba56db8d'::uuid, 'Supino Inclinado na Máquina Articulada'),   -- PADR | Supino inclinado na máquina
    ('b946c52f-7a32-5a72-bb0b-d8799bc40b70'::uuid, 'Supino Declinado Pegada Aberta com Barra'),   -- AJUS | Supino invertido com pegada aberta
    ('131db371-64fd-5e01-843f-a5fb7560b329'::uuid, 'Supino Sentado na Máquina'),   -- PADR | Supino na máquina
    ('94f2ce78-68b3-588e-b3e9-50dc1375db30'::uuid, 'Supino Reto no Smith'),   -- PADR | Supino na máquina Smith
    ('4ab7d891-0bb1-59cd-9892-01538359a333'::uuid, 'Supino Inclinado 30° Pegada Supinada com Halteres'),   -- AJUS | Supino no banco inclinado 30 graus com pegada invertida
    ('0bcd8665-9c45-519e-99af-beb7126b66ad'::uuid, 'Supino Fechado no Smith'),   -- AJUS | Supino no smith com o triângulo
    ('cb9c710e-6392-5d87-a16f-f7e4054178bf'::uuid, 'Supino Reto Pegada Neutra com Halteres'),   -- PADR | Supino pegada martelo
    ('a3246f0c-0846-58a8-af1c-10caecbe5215'::uuid, 'Supino em Pé no Crossover'),   -- PADR | Supino reto em pé no cross over
    ('ca5e42ed-31ae-5f96-993e-59f2a9aed9e2'::uuid, 'Supino Unilateral na Máquina (Alavanca)'),   -- PADR | Supino unilateral na Máquina
    ('76e837d3-cac1-5929-8492-06729541152a'::uuid, 'Suspensão Passiva na Barra'),   -- PADR | Suspensão Passiva
    ('650ec061-851b-5e11-8189-dce068f096f3'::uuid, 'Swing 360 na Barra'),   -- PADR | Swing 360
    ('029a19c9-3c0f-5715-8d0c-940e89ee1345'::uuid, 'Swing com Kettlebell'),   -- AJUS | Swing de kettlebell
    ('5a55c7be-9a46-5e36-9f45-e5a437355362'::uuid, 'Swing Unilateral com Kettlebell'),   -- AJUS | Swing de kettlebell de um braço
    ('2c00279b-91e5-5ce4-84b9-28c93ecf0711'::uuid, 'Toque Lateral nos Pés em Pé'),   -- PADR | Toque Lateral dos Dedos dos Pés em Pé
    ('644989d0-8774-572f-aa4c-c57df35557f8'::uuid, 'Toque nos Pés Sentado'),   -- PADR | Toque nos Dedos dos Pés Sentado
    ('1490123f-9c8a-551b-8a3c-fd5df396dfd4'::uuid, 'Toque nos Pés em Pé'),   -- PADR | Toque nos Dedos dos Pés em Pé
    ('6c82c945-f68d-505e-b02c-a9e3f073cd46'::uuid, 'Rotação Oblíqua Sentado'),   -- PADR | Torção Oblíqua Sentada
    ('81e9634a-b2f4-55af-865d-df78074cbcd8'::uuid, 'Rotação Cotovelo ao Joelho em Pé'),   -- PADR | Torções do Cotovelo para o Joelho
    ('68dc2b56-ee43-5d5f-afb8-2e41bb752d0c'::uuid, 'Abdução Horizontal Unilateral com Elástico'),   -- AJUS | Tração lateral com elástico
    ('bcbf0fdb-5302-58e6-9d37-db7a83cbdb8f'::uuid, 'Tríceps Coice no Cabo'),   -- AJUS | Tríceps Coice com Cabo
    ('77e5ed68-da7e-5e55-9ea6-3a51ab625535'::uuid, 'Tríceps Coice com Halter'),   -- AJUS | Tríceps Coice com Halteres
    ('29ee0051-b2df-52f0-b200-06cc511da64e'::uuid, 'Tríceps Testa Alternado no Banco Inclinado com Halteres'),   -- AJUS | Tríceps Francês Alternada com Halteres no Banco Inclinado
    ('d9402e02-3fc5-5c71-9736-4b0c6e7513db'::uuid, 'Tríceps Francês com Faixa Elástica'),   -- PADR | Tríceps Francês com Faixa Elástica Acima da Cabeça
    ('496e37d5-bcad-5bb1-920c-bcd78ebcd0e1'::uuid, 'Tríceps Francês Sentado com Halter (Duas Mãos)'),   -- PADR | Tríceps Francês com Halter Bilateral
    ('2d94d612-5d46-5e4b-b04b-4fdecbaa5ce9'::uuid, 'Tríceps Francês em Pé com Halter'),   -- AJUS | Tríceps Francês com Halteres
    ('fa23d7d7-ddf0-51cd-88fd-b9768ac9ee25'::uuid, 'Tríceps Pulley com Barra V'),   -- AJUS | Tríceps Pulley barra V
    ('ee02839b-d65a-51be-bbbd-6bab3f7ff224'::uuid, 'Tríceps Testa Declinado com Halteres'),   -- PADR | Tríceps Testa com Banco Declinado com Halteres
    ('7f413c6b-a10a-50f6-9d90-a22a1334d0ad'::uuid, 'Tríceps Testa com Barra Pegada Supinada'),   -- PADR | Tríceps Testa com Barra Pegada Invertida
    ('79f8d96a-d315-53a9-b3e8-94fb9c57a9e1'::uuid, 'Tríceps Francês Sentado com Barra W'),   -- PADR | Tríceps francês com barra W acima da cabeça sentado
    ('16d85db7-9e37-55ed-acde-7896edd58201'::uuid, 'Tríceps Francês na Polia com Corda'),   -- PADR | Tríceps francês na polia com corda
    ('4df98d3c-f823-590d-958d-8c87c9955550'::uuid, 'Tríceps Francês no Banco Inclinado com Halter'),   -- PADR | Tríceps francês no banco inclinado com halter
    ('d5b218e5-a7b2-5505-9fa1-8414b010eb03'::uuid, 'Tríceps Francês Unilateral no Cabo'),   -- PADR | Tríceps francês unilateral no cabo
    ('cb1e8843-2491-5594-9f77-19c945490553'::uuid, 'Mergulho no Banco'),   -- AJUS | Tríceps no Banco
    ('01fa2cb7-d8ce-593b-be0b-1072667c738c'::uuid, 'Tríceps Pulley com Barra'),   -- AJUS | Tríceps pulley barra
    ('2794cf73-df14-50fa-8d74-466b74f87429'::uuid, 'Tríceps Pulley com Corda'),   -- AJUS | Tríceps pulley corda
    ('a03a9c76-78c6-59c1-8e81-e32f70e7e8b0'::uuid, 'Tríceps Pulley Pegada Supinada'),   -- AJUS | Tríceps pulley pegada invertida
    ('e5d40626-2cc3-543a-b57a-0804bfda605f'::uuid, 'Tríceps Testa com Barra'),   -- PADR | Tríceps testa com barra
    ('c197b4ab-8a2f-552e-94e0-4bba22cb2ae8'::uuid, 'Tríceps Testa Pegada Neutra com Halteres'),   -- PADR | Tríceps testa pegada neutra com halteres
    ('221fb5fd-39b5-513f-ae44-3c712ce3f989'::uuid, 'V-Up com Bola Suíça'),   -- AJUS | V-Up com Bola de Estabilidade
    ('14a87b59-c660-5076-8289-29ab4a75906b'::uuid, 'Virada de Pneu'),   -- PADR | Virar Pneu
    ('6b935146-e15f-5930-97b8-2133cc4d65a6'::uuid, 'Elevação Frontal com Halteres até acima da Cabeça'),   -- AJUS | Voador com Halteres para Cima
    ('70bf08c1-580c-597a-9f95-6843a1c2a4ca'::uuid, 'Crucifixo Inverso na Máquina (Voador Invertido)'),   -- AJUS | Voador invertido
    ('c0229281-4eb3-55f1-bab5-b7f0c22c109f'::uuid, 'Voador na Máquina (Pec Deck com Apoio de Antebraço)'),   -- PADR | Voador na Máquina
    ('7b101363-2502-5e47-86d6-79e988baaa44'::uuid, 'Voador no Pec Deck'),   -- PADR | Voador no pec deck
    ('739993d0-0aa7-50ba-9030-7b490ed0e21f'::uuid, 'Crucifixo Inverso em Pé no Cabo'),   -- AJUS | Voador para deltoides posterior com cabo
    ('670c21ad-2cd8-569e-984c-bb3c258aa6f6'::uuid, 'Agachamento Isométrico na Parede (Wall Sit)'),   -- AJUS | Wall Sit
    ('1e764c22-cc8d-5ea1-8379-291968a6b27b'::uuid, 'Agachamento Isométrico na Parede com Inclinação de Tronco'),   -- AJUS | Wall Sit com Inclinação de Tronco
    ('86d71dff-8553-55de-8d99-4764ae1dfaf0'::uuid, 'Abertura de Pernas Deitado (Alongamento de Adutores)'),   -- AJUS | adução de pernas (alongamento do adutor maior)
    ('8bfc1c04-c282-550e-9c26-58cd10415ed7'::uuid, 'Elevação Frontal com Dois Cabos no Crossover'),   -- AJUS | elevação frontal com cabo duplo no cross
    ('cf79231a-9077-5bfb-859e-582d83e30e5c'::uuid, 'Levantamento Terra'),   -- OK | levantamento terra
    ('3d3521df-770c-5b44-8ba3-7d3dad341775'::uuid, 'Levantamento Terra na Máquina (Alavanca)'),   -- AJUS | levantamento terra na máquina
    ('01e8367b-9816-5ba2-8fbf-f848cb7451d2'::uuid, 'Remada Cavalinho Apoiada na Máquina')   -- AJUS | remada T invertida na máquina
) AS m(id, nome)
 WHERE e.id = m.id
   AND e.name IS DISTINCT FROM m.nome;


-- ============================================================================
-- GUARDA
-- ============================================================================
--
-- Três afirmações, e as três valem para a tabela INTEIRA de propósito — diferente da V50, cuja
-- guarda precisou ser por contagem porque o formato só se aplicava à parte tocada. Aqui não há
-- nome legítimo com travessão nem nome legítimo repetido, em nenhum grupo.
DO $$
DECLARE
    com_travessao integer;
    repetidos     integer;
    total         integer;
BEGIN
    SELECT count(*) INTO com_travessao
      FROM exercises
     WHERE name LIKE '%–%' OR name LIKE '%—%';

    IF com_travessao > 0 THEN
        RAISE EXCEPTION 'V52: % nomes ainda usam travessão, proibido pelo ARCH #37', com_travessao;
    END IF;

    SELECT count(*) INTO repetidos FROM (
        SELECT name FROM exercises GROUP BY name HAVING count(*) > 1
    ) AS d;

    IF repetidos > 0 THEN
        RAISE EXCEPTION 'V52: % nomes de exercício ficaram duplicados', repetidos;
    END IF;

    -- A V51 deixou 926. Renomear não pode criar nem apagar linha.
    SELECT count(*) INTO total FROM exercises;
    IF total <> 926 THEN
        RAISE EXCEPTION 'V52: o catálogo tinha 926 e agora tem %', total;
    END IF;
END $$;
