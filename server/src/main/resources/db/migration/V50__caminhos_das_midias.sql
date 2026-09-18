-- Caminhos das mídias voltam a existir (fatia H, migration de DADO).
--
-- ============================================================================
-- O QUE QUEBROU
-- ============================================================================
--
-- `video_ref` e `thumb_ref` guardam CAMINHO DE ARQUIVO, e sempre guardaram: a V39 mexeu neles com
-- `regexp_replace(thumb_ref, '\.png$', '.webp')`. Eles apontavam para o nome por extenso:
--
--     Bíceps/Rosca Direta com Barra no colete scott.webp
--
-- Em 2026-09-17 a revisão de nomenclatura renomeou os 1.846 arquivos de mídia para a `chave`
-- (`barbell_arm_blaster_curl.webp`). O disco mudou, o banco não — e **todo exercício do app passou
-- a servir 404**, em silêncio, porque imagem que falta não derruba tela nenhuma.
--
-- > Caminho de arquivo guardado em banco é um acoplamento que nada no build enxerga: o dia em que
-- > alguém organiza a pasta, o banco fica mentindo e ninguém é avisado.
--
--
-- ============================================================================
-- POR QUE O `video_ref` FOI A CHAVE PARA RECONSTRUIR, E NÃO O `name`
-- ============================================================================
--
-- Casar banco × catálogo pelo NOME resolvia só 93%, e falhava justamente onde a curadoria
-- trabalhou: a V14 renomeou 49 exercícios POR ID e não tocou nos arquivos.
--
-- O caso que prova: o banco chama `Rosca Direta com Barra no Banco Scott`; o arquivo sempre se
-- chamou `no colete scott` — que é o **arm blaster**, a cinta de cotovelo, não o banco Scott. A
-- V14 transformou um nome certo em errado, e o nome do arquivo guardou a verdade.
--
-- > O campo que ninguém achou importante o suficiente para curar é o que preservou a identidade.
--
-- A cadeia usada foi: `video_ref` → `arquivo_gif` do catálogo original → `nome_original` → `chave`.
-- Resolve **900 dos 965**. Os outros 65 estão contados e NÃO são tocados aqui:
--
--   * 39 são duplicados que a revisão removeu (mídia em `_removidos/`). Saem na V52; nenhum deles
--     está dentro do treino de ninguém, e isso foi MEDIDO, não suposto.
--   * 26 nasceram em migrations de curadoria com `video_ref` VAZIO e nunca tiveram mídia. Já
--     apareciam sem imagem antes disto. São o débito dos "exercícios de core sem mídia".
--
--
-- ============================================================================
-- ESCOPO
-- ============================================================================
--
-- Só `video_ref` e `thumb_ref`. **`name` não é tocado aqui**, mesmo sabendo que 850 nomes mudaram
-- na revisão: nome é texto que o usuário lê e merece migration própria, com o próprio voltar atrás.
-- Misturar as duas faria uma reversão custar as duas coisas.
--
-- Idempotente: reescreve para o mesmo valor se rodar de novo. O `WHERE` do final protege quem
-- rodar à mão num banco em estado diferente.
UPDATE exercises AS e
   SET video_ref = m.video_ref,
       thumb_ref = m.thumb_ref
  FROM (VALUES
    ('29fb0950-dc61-5dc1-a858-45d4be82723d'::uuid, 'Funcional e HIT/stability_ball_frog_crunch.mp4', 'Funcional e HIT/stability_ball_frog_crunch.webp'),   -- stability_ball_frog_crunch
    ('4afcf90e-91f8-588e-87e9-04e15c046250'::uuid, 'Glúteos/lever_standing_hip_abduction.mp4', 'Glúteos/lever_standing_hip_abduction.webp'),   -- lever_standing_hip_abduction
    ('4bc194b3-2774-5cb1-b2f7-7c61dfac9466'::uuid, 'Funcional e HIT/side_plank_hip_abduction.mp4', 'Funcional e HIT/side_plank_hip_abduction.webp'),   -- side_plank_hip_abduction
    ('ff78a9c3-b13d-5a6d-a090-84ea3960bcd4'::uuid, 'Funcional e HIT/seated_band_hip_abduction.mp4', 'Funcional e HIT/seated_band_hip_abduction.webp'),   -- seated_band_hip_abduction
    ('4a14d6e4-a137-5992-8c10-784dafc212ae'::uuid, 'Funcional e HIT/side_lying_band_hip_abduction.mp4', 'Funcional e HIT/side_lying_band_hip_abduction.webp'),   -- side_lying_band_hip_abduction
    ('61fb4647-fdd7-54e0-9122-617b5d49452f'::uuid, 'Glúteos/glute_bridge_with_hip_abduction.mp4', 'Glúteos/glute_bridge_with_hip_abduction.webp'),   -- glute_bridge_with_hip_abduction
    ('0bc8d3cf-891f-5e72-b804-af2ce67322a2'::uuid, 'Funcional e HIT/side_lying_clamshell.mp4', 'Funcional e HIT/side_lying_clamshell.webp'),   -- side_lying_clamshell
    ('9355a660-85c6-5ec3-be0b-689bc42b251a'::uuid, 'Funcional e HIT/standing_hip_abduction.mp4', 'Funcional e HIT/standing_hip_abduction.webp'),   -- standing_hip_abduction
    ('be8eb716-8316-5ea4-b383-f2850a903df9'::uuid, 'Glúteos/hip_abduction_machine.mp4', 'Glúteos/hip_abduction_machine.webp'),   -- hip_abduction_machine
    ('5d30f49d-f617-57f5-889c-e8e42c639b9e'::uuid, 'Glúteos/cable_standing_hip_abduction.mp4', 'Glúteos/cable_standing_hip_abduction.webp'),   -- cable_standing_hip_abduction
    ('772f6d66-ef9e-5d91-9f6e-d3a2f891fad9'::uuid, 'Pernas/lever_standing_hip_adduction.mp4', 'Pernas/lever_standing_hip_adduction.webp'),   -- lever_standing_hip_adduction
    ('e1fc7d32-85af-5611-bfbc-a96cceb673ea'::uuid, 'Mobilidade/standing_knee_hug.mp4', 'Mobilidade/standing_knee_hug.webp'),   -- standing_knee_hug
    ('19807bcf-cc13-51ec-8bfd-09e548320347'::uuid, 'Funcional e HIT/band_shoulder_adduction.mp4', 'Funcional e HIT/band_shoulder_adduction.webp'),   -- band_shoulder_adduction
    ('3c166c66-c366-5734-a231-2259fbc98d4e'::uuid, 'Pernas/hip_adduction_machine.mp4', 'Pernas/hip_adduction_machine.webp'),   -- hip_adduction_machine
    ('d3ecad4f-0844-5aa0-a025-3b57ab8237b0'::uuid, 'Mobilidade/side_lying_hip_adduction.mp4', 'Mobilidade/side_lying_hip_adduction.webp'),   -- side_lying_hip_adduction
    ('1b6ebbcb-372d-57c7-b0ba-c218616a7f37'::uuid, 'Pernas/cable_hip_adduction.mp4', 'Pernas/cable_hip_adduction.webp'),   -- cable_hip_adduction
    ('bb1464e2-3f05-5ce4-a600-658155df7ea6'::uuid, 'Calistenia/bodyweight_lunge.mp4', 'Calistenia/bodyweight_lunge.webp'),   -- bodyweight_lunge
    ('6ad42a65-f30f-5e57-bf84-b2d5af80883b'::uuid, 'Funcional e HIT/jumping_alternating_lunge.mp4', 'Funcional e HIT/jumping_alternating_lunge.webp'),   -- jumping_alternating_lunge
    ('336e7052-eae3-5b09-b3cf-67bf911b16f3'::uuid, 'Funcional e HIT/lateral_lunge.mp4', 'Funcional e HIT/lateral_lunge.webp'),   -- lateral_lunge
    ('36032c9a-8fa1-5fa4-a2bd-75b2d458ea79'::uuid, 'Pernas/barbell_lateral_lunge.mp4', 'Pernas/barbell_lateral_lunge.webp'),   -- barbell_lateral_lunge
    ('e7ed15c0-33bd-5938-8b1b-4286f89b61ff'::uuid, 'Calistenia/deep_lunge.mp4', 'Calistenia/deep_lunge.webp'),   -- deep_lunge
    ('99e32582-d818-5e97-8851-d2e0a04d3ca0'::uuid, 'Pernas/barbell_lunge.mp4', 'Pernas/barbell_lunge.webp'),   -- barbell_lunge
    ('cac02b24-5064-53d0-a6f4-69bff4a082f4'::uuid, 'Pernas/cable_lunge_with_straight_arm_hold.mp4', 'Pernas/cable_lunge_with_straight_arm_hold.webp'),   -- cable_lunge_with_straight_arm_hold
    ('b2bb7607-8402-5097-a55f-578d608d62f4'::uuid, 'Funcional e HIT/gymstick_lunge.mp4', 'Funcional e HIT/gymstick_lunge.webp'),   -- gymstick_lunge
    ('ffc73e33-e436-5cb4-b306-6005b10f55ea'::uuid, 'Pernas/dumbbell_lunge.mp4', 'Pernas/dumbbell_lunge.webp'),   -- dumbbell_lunge
    ('3746462c-f5f6-504e-9fc5-b70a8e808081'::uuid, 'Pernas/landmine_reverse_lunge.mp4', 'Pernas/landmine_reverse_lunge.webp'),   -- landmine_reverse_lunge
    ('adad8fea-9428-5a58-82d9-e79935faaea4'::uuid, 'Pernas/smith_machine_lunge.mp4', 'Pernas/smith_machine_lunge.webp'),   -- smith_machine_lunge
    ('c6b02d3e-eeb9-5c45-875d-bc39c790f380'::uuid, 'Calistenia/bench_step_up.mp4', 'Calistenia/bench_step_up.webp'),   -- bench_step_up
    ('8a78294e-875f-59c4-bee2-9ca74e63eeb6'::uuid, 'Pernas/dumbbell_bench_step_up.mp4', 'Pernas/dumbbell_bench_step_up.webp'),   -- dumbbell_bench_step_up
    ('a5c53651-49c7-5365-822a-8cb8da06bd51'::uuid, 'Calistenia/bodyweight_squat.mp4', 'Calistenia/bodyweight_squat.webp'),   -- bodyweight_squat
    ('abf1fc72-f07f-5c0b-820a-c7e010afa9d7'::uuid, 'Pernas/barbell_back_squat.mp4', 'Pernas/barbell_back_squat.webp'),   -- barbell_back_squat
    ('9851a2f8-f8ac-54fb-8c27-4234abcc6bd9'::uuid, 'Pernas/barbell_bulgarian_split_squat.mp4', 'Pernas/barbell_bulgarian_split_squat.webp'),   -- barbell_bulgarian_split_squat
    ('22abc486-e188-5f27-95d1-acd331daffd1'::uuid, 'Pernas/dumbbell_bulgarian_split_squat.mp4', 'Pernas/dumbbell_bulgarian_split_squat.webp'),   -- dumbbell_bulgarian_split_squat
    ('fbbbeb54-4a6a-5355-b159-f59d6226dc0a'::uuid, 'Funcional e HIT/bodyweight_bulgarian_split_squat.mp4', 'Funcional e HIT/bodyweight_bulgarian_split_squat.webp'),   -- bodyweight_bulgarian_split_squat
    ('d089bb9c-24f2-592f-ac3b-7d1fb42c0086'::uuid, 'Calistenia/shrimp_squat.mp4', 'Calistenia/shrimp_squat.webp'),   -- shrimp_squat
    ('791b3ec1-25d9-583d-aa54-0268f5a93cce'::uuid, 'Funcional e HIT/cossack_squat.mp4', 'Funcional e HIT/cossack_squat.webp'),   -- cossack_squat
    ('dfd78903-efa3-528f-b747-bcc99e59ddc8'::uuid, 'Pernas/barbell_front_squat.mp4', 'Pernas/barbell_front_squat.webp'),   -- barbell_front_squat
    ('f2c97963-cdd2-5f1a-99ed-4fabbe844f83'::uuid, 'Pernas/barbell_front_box_squat.mp4', 'Pernas/barbell_front_box_squat.webp'),   -- barbell_front_box_squat
    ('05a1eee6-06f9-50a2-8827-902b3b6fad44'::uuid, 'Pernas/barbell_rack_front_squat.mp4', 'Pernas/barbell_rack_front_squat.webp'),   -- barbell_rack_front_squat
    ('43f0d050-afe4-5a2b-9749-88f343122641'::uuid, 'Pernas/cable_front_squat.mp4', 'Pernas/cable_front_squat.webp'),   -- cable_front_squat
    ('fc3bbcca-9b05-56e9-90cb-a3dff1a12fa2'::uuid, 'Pernas/kettlebell_front_squat.mp4', 'Pernas/kettlebell_front_squat.webp'),   -- kettlebell_front_squat
    ('dc2f45f4-2f6f-5e8b-93bf-473a778d83ed'::uuid, 'Pernas/dumbbell_goblet_squat.mp4', 'Pernas/dumbbell_goblet_squat.webp'),   -- dumbbell_goblet_squat
    ('7b39d400-2a78-5250-a78c-7c4bff58f8c7'::uuid, 'Funcional e HIT/kettlebell_goblet_squat_with_mini_band.mp4', 'Funcional e HIT/kettlebell_goblet_squat_with_mini_band.webp'),   -- kettlebell_goblet_squat_with_mini_band
    ('d5aa8f22-8373-558e-bfe7-1af980b1dc04'::uuid, 'Pernas/reverse_hack_squat.mp4', 'Pernas/reverse_hack_squat.webp'),   -- reverse_hack_squat
    ('443e406a-24ab-5764-9bbd-bd621916e31f'::uuid, 'Pernas/jefferson_squat.mp4', 'Pernas/jefferson_squat.webp'),   -- jefferson_squat
    ('a2573e54-9ee5-50ae-86f2-d0417e71d337'::uuid, 'Calistenia/trx_pistol_squat.mp4', 'Calistenia/trx_pistol_squat.webp'),   -- trx_pistol_squat
    ('aabdf1f5-6fae-569b-89e7-5fff3bee7066'::uuid, 'Calistenia/assisted_pistol_squat.mp4', 'Calistenia/assisted_pistol_squat.webp'),   -- assisted_pistol_squat
    ('d1762756-afce-510b-9e33-2bb271e27487'::uuid, 'Crossfit/box_pistol_squat_elevated.mp4', 'Crossfit/box_pistol_squat_elevated.webp'),   -- box_pistol_squat_elevated
    ('74b342c8-beb7-5e25-8060-cbe8061dcda5'::uuid, 'Crossfit/dumbbell_pistol_squat.mp4', 'Crossfit/dumbbell_pistol_squat.webp'),   -- dumbbell_pistol_squat
    ('112dbd59-f653-5eb0-b7ab-91abc7842a1f'::uuid, 'Calistenia/kettlebell_pistol_squat.mp4', 'Calistenia/kettlebell_pistol_squat.webp'),   -- kettlebell_pistol_squat
    ('e116d153-7def-534e-9fb1-45fbf7e9e0f4'::uuid, 'Calistenia/box_pistol_squat.mp4', 'Calistenia/box_pistol_squat.webp'),   -- box_pistol_squat
    ('ff9baeab-e143-5793-aadd-1a627f92ba19'::uuid, 'Pernas/sissy_squat.mp4', 'Pernas/sissy_squat.webp'),   -- sissy_squat
    ('e7279daa-2cd7-51e9-a6c9-34c3a9c107c2'::uuid, 'Calistenia/kneeling_sissy_squat.mp4', 'Calistenia/kneeling_sissy_squat.webp'),   -- kneeling_sissy_squat
    ('d7ed1e6b-6091-5cac-a25f-6fc2e4ee15d6'::uuid, 'Calistenia/skater_squat.mp4', 'Calistenia/skater_squat.webp'),   -- skater_squat
    ('ef4e3c3e-1043-5f8c-b260-151295dbb478'::uuid, 'Pernas/dumbbell_sumo_squat.mp4', 'Pernas/dumbbell_sumo_squat.webp'),   -- dumbbell_sumo_squat
    ('ff79b49f-58fa-5d99-a782-2528080a9fba'::uuid, 'Calistenia/bodyweight_sumo_squat.mp4', 'Calistenia/bodyweight_sumo_squat.webp'),   -- bodyweight_sumo_squat
    ('d7b8e4b6-0cc6-5075-9020-d5cd3de8d18d'::uuid, 'Pernas/zercher_squat.mp4', 'Pernas/zercher_squat.webp'),   -- zercher_squat
    ('4f0ada02-fe1a-55a6-ae26-6bdb88d29820'::uuid, 'Calistenia/jumping_bulgarian_split_squat.mp4', 'Calistenia/jumping_bulgarian_split_squat.webp'),   -- jumping_bulgarian_split_squat
    ('1da9c7a0-79cd-54e4-bcc3-d3947a319572'::uuid, 'Crossfit/overhead_barbell_squat.mp4', 'Crossfit/overhead_barbell_squat.webp'),   -- overhead_barbell_squat
    ('51fa1e34-6e28-516e-a5ce-35a153178335'::uuid, 'Funcional e HIT/squat_with_side_kick_and_heel_touch.mp4', 'Funcional e HIT/squat_with_side_kick_and_heel_touch.webp'),   -- squat_with_side_kick_and_heel_touch
    ('b88ba2ea-6da8-5b45-a238-d069e54e9ef4'::uuid, 'Pernas/belt_squat.mp4', 'Pernas/belt_squat.webp'),   -- belt_squat
    ('1e1cecd9-1f9e-5557-9b14-ce10f532a2d2'::uuid, 'Funcional e HIT/band_split_squat.mp4', 'Funcional e HIT/band_split_squat.webp'),   -- band_split_squat
    ('c5d1781d-a430-596c-9e42-6c71220ca79a'::uuid, 'Funcional e HIT/band_overhead_squat.mp4', 'Funcional e HIT/band_overhead_squat.webp'),   -- band_overhead_squat
    ('3e7abe4c-38e0-5143-92e9-f3cb80685895'::uuid, 'Funcional e HIT/gymstick_squat.mp4', 'Funcional e HIT/gymstick_squat.webp'),   -- gymstick_squat
    ('58173809-a5b5-5de3-9759-45f4a700f767'::uuid, 'Pernas/dumbbell_box_squat.mp4', 'Pernas/dumbbell_box_squat.webp'),   -- dumbbell_box_squat
    ('8c672786-7ab7-5458-97df-88c2971e81b2'::uuid, 'Calistenia/squat_to_knee_raise.mp4', 'Calistenia/squat_to_knee_raise.webp'),   -- squat_to_knee_raise
    ('585d6e83-3b9d-5be4-969d-8bd5ba437ed5'::uuid, 'Calistenia/jump_squat.mp4', 'Calistenia/jump_squat.webp'),   -- jump_squat
    ('ae9ae166-bdbc-565c-865a-6d4fdff52cfe'::uuid, 'Crossfit/trap_bar_jump_squat.mp4', 'Crossfit/trap_bar_jump_squat.webp'),   -- trap_bar_jump_squat
    ('2f98ad24-4113-57db-9424-c8d5cd3e74ba'::uuid, 'Calistenia/squat_hold_calf_raise.mp4', 'Calistenia/squat_hold_calf_raise.webp'),   -- squat_hold_calf_raise
    ('5f38b62f-03f7-58a5-853e-4ca1e7949686'::uuid, 'Pernas/pin_squat.mp4', 'Pernas/pin_squat.webp'),   -- pin_squat
    ('e25623af-48aa-51b9-bd32-e0a0c3af2a79'::uuid, 'Crossfit/barbell_jump_squat.mp4', 'Crossfit/barbell_jump_squat.webp'),   -- barbell_jump_squat
    ('5f7ac1de-2cff-5348-975e-c64b4fa11d56'::uuid, 'Crossfit/landmine_squat_to_press.mp4', 'Crossfit/landmine_squat_to_press.webp'),   -- landmine_squat_to_press
    ('11b10341-8a48-5d90-936c-dc51f9db1b4b'::uuid, 'Pernas/kettlebell_goblet_squat.mp4', 'Pernas/kettlebell_goblet_squat.webp'),   -- kettlebell_goblet_squat
    ('a79b4463-d6c6-5bb7-b39e-3fe579353eac'::uuid, 'Crossfit/barbell_kneeling_jump_squat.mp4', 'Crossfit/barbell_kneeling_jump_squat.webp'),   -- barbell_kneeling_jump_squat
    ('f791ba7e-10e3-5705-83fc-205da96fed28'::uuid, 'Pernas/dumbbell_jump_squat.mp4', 'Pernas/dumbbell_jump_squat.webp'),   -- dumbbell_jump_squat
    ('6d47c5e4-83bf-51a1-a067-bed2c91838f4'::uuid, 'Crossfit/kettlebell_squat_to_press.mp4', 'Crossfit/kettlebell_squat_to_press.webp'),   -- kettlebell_squat_to_press
    ('164a53f5-e00a-5031-b9c7-b92d529276ad'::uuid, 'Pernas/dumbbell_plie_squat.mp4', 'Pernas/dumbbell_plie_squat.webp'),   -- dumbbell_plie_squat
    ('d012f791-94a7-5e73-adb8-f45b53765501'::uuid, 'Pernas/barbell_hack_squat.mp4', 'Pernas/barbell_hack_squat.webp'),   -- barbell_hack_squat
    ('b392f6b6-e20e-5de5-b485-1845b8d9598b'::uuid, 'Calistenia/hawaiian_squat.mp4', 'Calistenia/hawaiian_squat.webp'),   -- hawaiian_squat
    ('b8c61d9c-7218-5737-b5f9-29aea0a04da9'::uuid, 'Pernas/hack_squat_machine.mp4', 'Pernas/hack_squat_machine.webp'),   -- hack_squat_machine
    ('c64ec081-7009-5dd3-a1cb-8cf0bd368530'::uuid, 'Funcional e HIT/stability_ball_wall_squat.mp4', 'Funcional e HIT/stability_ball_wall_squat.webp'),   -- stability_ball_wall_squat
    ('4f4ed3d1-3253-5aa2-b62f-3f583460adc0'::uuid, 'Calistenia/bodyweight_box_squat.mp4', 'Calistenia/bodyweight_box_squat.webp'),   -- bodyweight_box_squat
    ('981d8d7d-6e47-5cb8-8980-baa6f7b5a31a'::uuid, 'Pernas/landmine_squat.mp4', 'Pernas/landmine_squat.webp'),   -- landmine_squat
    ('be89a141-ac87-573f-8cfc-c49090988f4c'::uuid, 'Pernas/smith_machine_squat.mp4', 'Pernas/smith_machine_squat.webp'),   -- smith_machine_squat
    ('a09f6fd4-2c38-5e02-beb5-10c23edf4415'::uuid, 'Calistenia/pistol_squat.mp4', 'Calistenia/pistol_squat.webp'),   -- pistol_squat
    ('103a0c02-4ab2-5c6b-abb3-cd89c93a5e5b'::uuid, 'Funcional e HIT/curtsy_lunge.mp4', 'Funcional e HIT/curtsy_lunge.webp'),   -- curtsy_lunge
    ('f9354ac6-e9d0-52cf-b105-acb927c80074'::uuid, 'Pernas/barbell_curtsy_lunge.mp4', 'Pernas/barbell_curtsy_lunge.webp'),   -- barbell_curtsy_lunge
    ('7d922bee-85e4-5de4-8e60-9274c4dcd72c'::uuid, 'Pernas/dumbbell_curtsy_lunge.mp4', 'Pernas/dumbbell_curtsy_lunge.webp'),   -- dumbbell_curtsy_lunge
    ('8666aa5c-dbbb-523a-be80-7207b1fa9061'::uuid, 'Cardio/air_bike.mp4', 'Cardio/air_bike.webp'),   -- air_bike
    ('206627b8-03d9-579d-9516-90d4da101ffa'::uuid, 'Mobilidade/hug_and_pat_stretch.mp4', 'Mobilidade/hug_and_pat_stretch.webp'),   -- hug_and_pat_stretch
    ('0caee4bd-c039-55de-a2e8-91104515e413'::uuid, 'Mobilidade/butterfly_stretch.mp4', 'Mobilidade/butterfly_stretch.webp'),   -- butterfly_stretch
    ('db482278-f837-5575-acda-f025fa46e5ad'::uuid, 'Mobilidade/dynamic_chest_stretch.mp4', 'Mobilidade/dynamic_chest_stretch.webp'),   -- dynamic_chest_stretch
    ('6a364016-e822-5629-be5b-7896d8faa734'::uuid, 'Mobilidade/standing_side_bend_stretch.mp4', 'Mobilidade/standing_side_bend_stretch.webp'),   -- standing_side_bend_stretch
    ('bc0faf03-dd2e-5930-9eba-84f20a860d48'::uuid, 'Mobilidade/side_lunge_adductor_stretch.mp4', 'Mobilidade/side_lunge_adductor_stretch.webp'),   -- side_lunge_adductor_stretch
    ('5bd08014-7398-5210-a333-bbcd22fcdb53'::uuid, 'Mobilidade/seated_piriformis_stretch.mp4', 'Mobilidade/seated_piriformis_stretch.webp'),   -- seated_piriformis_stretch
    ('3cfc036b-591c-50bf-8d3c-d0bc98cdfa5c'::uuid, 'Mobilidade/reverse_wrist_stretch.mp4', 'Mobilidade/reverse_wrist_stretch.webp'),   -- reverse_wrist_stretch
    ('907db4aa-1bb1-58eb-9ec1-793c11736730'::uuid, 'Mobilidade/seated_straight_leg_calf_stretch.mp4', 'Mobilidade/seated_straight_leg_calf_stretch.webp'),   -- seated_straight_leg_calf_stretch
    ('2a486ad5-2cf6-58f3-86cf-295ddde4a9d3'::uuid, 'Mobilidade/assisted_reverse_chest_and_shoulder_stretch.mp4', 'Mobilidade/assisted_reverse_chest_and_shoulder_stretch.webp'),   -- assisted_reverse_chest_and_shoulder_stretch
    ('9d85a0fe-8467-5a52-a898-0e828ebd63b5'::uuid, 'Mobilidade/pvc_front_rack_stretch.mp4', 'Mobilidade/pvc_front_rack_stretch.webp'),   -- pvc_front_rack_stretch
    ('45bcfcc6-f5e4-5a0b-84c9-30ad39910845'::uuid, 'Mobilidade/sphinx_stretch.mp4', 'Mobilidade/sphinx_stretch.webp'),   -- sphinx_stretch
    ('1b7e192b-d1a6-5051-966e-2eaaac9deeb7'::uuid, 'Mobilidade/squatting_calf_stretch.mp4', 'Mobilidade/squatting_calf_stretch.webp'),   -- squatting_calf_stretch
    ('69093cac-3760-5d38-83f3-1c48e45018c8'::uuid, 'Mobilidade/heel_drop_calf_stretch.mp4', 'Mobilidade/heel_drop_calf_stretch.webp'),   -- heel_drop_calf_stretch
    ('c1c73e69-9005-5124-bb11-2cc08e27829f'::uuid, 'Mobilidade/upper_back_stretch.mp4', 'Mobilidade/upper_back_stretch.webp'),   -- upper_back_stretch
    ('1aaeb6ae-03c0-5e86-ac1e-0b223b2d6bd0'::uuid, 'Mobilidade/foam_roller_back_stretch.mp4', 'Mobilidade/foam_roller_back_stretch.webp'),   -- foam_roller_back_stretch
    ('17055c56-ef7d-5565-b9b9-efeb3656025c'::uuid, 'Mobilidade/standing_wide_stance_adductor_stretch.mp4', 'Mobilidade/standing_wide_stance_adductor_stretch.webp'),   -- standing_wide_stance_adductor_stretch
    ('e9545427-e79b-51ce-b2b7-36b865e3ba56'::uuid, 'Mobilidade/lying_glute_stretch.mp4', 'Mobilidade/lying_glute_stretch.webp'),   -- lying_glute_stretch
    ('eeb8ee17-3d14-5f2f-9197-e1d3969d116b'::uuid, 'Mobilidade/lying_hamstring_stretch.mp4', 'Mobilidade/lying_hamstring_stretch.webp'),   -- lying_hamstring_stretch
    ('f95de728-50d0-505e-93fb-a4c85e06732c'::uuid, 'Mobilidade/standing_hamstring_stretch.mp4', 'Mobilidade/standing_hamstring_stretch.webp'),   -- standing_hamstring_stretch
    ('30db47ff-55d0-558e-a9a5-62f6711dfe39'::uuid, 'Mobilidade/lying_strap_calf_stretch.mp4', 'Mobilidade/lying_strap_calf_stretch.webp'),   -- lying_strap_calf_stretch
    ('922bd1f3-801c-52c0-9ff7-b98997f5eb6a'::uuid, 'Mobilidade/double_leg_stretch_pilates.mp4', 'Mobilidade/double_leg_stretch_pilates.webp'),   -- double_leg_stretch_pilates
    ('49649773-817e-5172-9c3e-41d0abc6980d'::uuid, 'Mobilidade/wrist_stretch.mp4', 'Mobilidade/wrist_stretch.webp'),   -- wrist_stretch
    ('62ebad94-8204-5ce6-91d0-f3ec9c33eb5b'::uuid, 'Mobilidade/kneeling_quad_stretch.mp4', 'Mobilidade/kneeling_quad_stretch.webp'),   -- kneeling_quad_stretch
    ('13294852-7cf8-5106-a890-bb896d8c59b6'::uuid, 'Mobilidade/quadruped_quad_stretch.mp4', 'Mobilidade/quadruped_quad_stretch.webp'),   -- quadruped_quad_stretch
    ('e545610a-560a-5d74-970a-96151b84079b'::uuid, 'Mobilidade/cross_body_shoulder_stretch.mp4', 'Mobilidade/cross_body_shoulder_stretch.webp'),   -- cross_body_shoulder_stretch
    ('63023350-385b-5b52-aec7-8830396a343a'::uuid, 'Mobilidade/standing_reverse_shoulder_stretch.mp4', 'Mobilidade/standing_reverse_shoulder_stretch.webp'),   -- standing_reverse_shoulder_stretch
    ('69c45db6-55a4-503e-9c8a-20f508e6da73'::uuid, 'Mobilidade/single_leg_step_calf_stretch.mp4', 'Mobilidade/single_leg_step_calf_stretch.webp'),   -- single_leg_step_calf_stretch
    ('93f9d0b3-290b-56ee-aa70-201e4f355b64'::uuid, 'Mobilidade/staggered_stance_calf_stretch.mp4', 'Mobilidade/staggered_stance_calf_stretch.webp'),   -- staggered_stance_calf_stretch
    ('0fa9504c-598b-50cc-b3aa-238b06927625'::uuid, 'Mobilidade/forearms_on_wall_calf_stretch.mp4', 'Mobilidade/forearms_on_wall_calf_stretch.webp'),   -- forearms_on_wall_calf_stretch
    ('7f18599c-2111-5e93-bcf5-66ef8c532dd5'::uuid, 'Mobilidade/toe_on_wall_calf_stretch.mp4', 'Mobilidade/toe_on_wall_calf_stretch.webp'),   -- toe_on_wall_calf_stretch
    ('04fa7325-3b16-5ca7-ad0f-9640e3ac306d'::uuid, 'Mobilidade/90_90_hip_stretch.mp4', 'Mobilidade/90_90_hip_stretch.webp'),   -- 90_90_hip_stretch
    ('2ab38081-8554-54f4-b7a4-13af1faa5f07'::uuid, 'Mobilidade/standing_spinal_rotation_stretch.mp4', 'Mobilidade/standing_spinal_rotation_stretch.webp'),   -- standing_spinal_rotation_stretch
    ('cdd63b89-e3db-5355-a75f-4f90de7fb213'::uuid, 'Mobilidade/dynamic_cross_body_shoulder_stretch.mp4', 'Mobilidade/dynamic_cross_body_shoulder_stretch.webp'),   -- dynamic_cross_body_shoulder_stretch
    ('3f366247-3635-5b2c-b1d8-ee54275a734d'::uuid, 'Mobilidade/overhead_chest_stretch.mp4', 'Mobilidade/overhead_chest_stretch.webp'),   -- overhead_chest_stretch
    ('f23ffead-0edc-5cfd-9dfd-92863f8daa41'::uuid, 'Mobilidade/stick_chest_and_front_shoulder_stretch.mp4', 'Mobilidade/stick_chest_and_front_shoulder_stretch.webp'),   -- stick_chest_and_front_shoulder_stretch
    ('f2bd6ba3-be4d-5cf7-b97e-221fa131f9ab'::uuid, 'Mobilidade/bench_lat_and_chest_stretch.mp4', 'Mobilidade/bench_lat_and_chest_stretch.webp'),   -- bench_lat_and_chest_stretch
    ('42b3cd32-2f79-5908-8fbf-e5d282cbf643'::uuid, 'Mobilidade/single_arm_wall_chest_stretch.mp4', 'Mobilidade/single_arm_wall_chest_stretch.webp'),   -- single_arm_wall_chest_stretch
    ('a1b7afbf-ad56-525f-986e-9fe54918d345'::uuid, 'Mobilidade/seated_bench_piriformis_stretch.mp4', 'Mobilidade/seated_bench_piriformis_stretch.webp'),   -- seated_bench_piriformis_stretch
    ('f932ea60-49ec-5b12-b98c-6643bdedb40e'::uuid, 'Mobilidade/quadruped_wrist_flexor_stretch.mp4', 'Mobilidade/quadruped_wrist_flexor_stretch.webp'),   -- quadruped_wrist_flexor_stretch
    ('1b0cd8e8-3480-52f3-95fb-4b6f979c8f4a'::uuid, 'Mobilidade/rotator_cuff_stretch.mp4', 'Mobilidade/rotator_cuff_stretch.webp'),   -- rotator_cuff_stretch
    ('9539d104-a1b1-5a5c-b2c0-900f3a51df66'::uuid, 'Mobilidade/towel_shoulder_stretch.mp4', 'Mobilidade/towel_shoulder_stretch.webp'),   -- towel_shoulder_stretch
    ('10513f6b-9984-5dff-bc9a-ef2a78171e98'::uuid, 'Mobilidade/foam_roller_chest_stretch.mp4', 'Mobilidade/foam_roller_chest_stretch.webp'),   -- foam_roller_chest_stretch
    ('3eee9c89-9eb0-5c13-882c-0a98cf9deabf'::uuid, 'Mobilidade/doorway_chest_and_shoulder_stretch.mp4', 'Mobilidade/doorway_chest_and_shoulder_stretch.webp'),   -- doorway_chest_and_shoulder_stretch
    ('a5adb4ac-6030-5470-b209-000a919568f8'::uuid, 'Mobilidade/seated_reverse_chest_stretch.mp4', 'Mobilidade/seated_reverse_chest_stretch.webp'),   -- seated_reverse_chest_stretch
    ('767d8609-68b7-538c-a50f-723a73da039d'::uuid, 'Mobilidade/standing_achilles_stretch.mp4', 'Mobilidade/standing_achilles_stretch.webp'),   -- standing_achilles_stretch
    ('6bfc9fd1-0dcd-53c7-98ec-071b7c3260be'::uuid, 'Mobilidade/band_tibialis_posterior_stretch.mp4', 'Mobilidade/band_tibialis_posterior_stretch.webp'),   -- band_tibialis_posterior_stretch
    ('56827e54-01da-5b87-981b-e4c78da34221'::uuid, 'Mobilidade/it_band_foam_rolling.mp4', 'Mobilidade/it_band_foam_rolling.webp'),   -- it_band_foam_rolling
    ('23f96c0a-f35e-501f-aaee-294fc297cbe4'::uuid, 'Mobilidade/standing_wide_leg_forward_fold.mp4', 'Mobilidade/standing_wide_leg_forward_fold.webp'),   -- standing_wide_leg_forward_fold
    ('14d6e399-b9ac-5e1b-95ad-4303298812ab'::uuid, 'Mobilidade/kneeling_adductor_stretch.mp4', 'Mobilidade/kneeling_adductor_stretch.webp'),   -- kneeling_adductor_stretch
    ('c39a8912-864d-5780-96b8-4ea9cfeb1a75'::uuid, 'Mobilidade/seated_straddle_adductor_stretch.mp4', 'Mobilidade/seated_straddle_adductor_stretch.webp'),   -- seated_straddle_adductor_stretch
    ('2a51d142-79e6-5f81-a636-5fa6378810cf'::uuid, 'Mobilidade/toe_extensor_stretch.mp4', 'Mobilidade/toe_extensor_stretch.webp'),   -- toe_extensor_stretch
    ('426fd34b-eb05-5467-bc77-9b68e97eacbf'::uuid, 'Mobilidade/seated_straddle_hamstring_stretch.mp4', 'Mobilidade/seated_straddle_hamstring_stretch.webp'),   -- seated_straddle_hamstring_stretch
    ('b3d728c2-a2e9-5d8b-814f-854f690b84bc'::uuid, 'Mobilidade/adductor_foam_rolling.mp4', 'Mobilidade/adductor_foam_rolling.webp'),   -- adductor_foam_rolling
    ('31e6691e-8a44-5d07-938d-47e028134911'::uuid, 'Mobilidade/kneeling_hip_flexor_stretch.mp4', 'Mobilidade/kneeling_hip_flexor_stretch.webp'),   -- kneeling_hip_flexor_stretch
    ('64209e97-eb18-5a60-b8f5-d6c52f1a72de'::uuid, 'Mobilidade/standing_toe_flexor_stretch.mp4', 'Mobilidade/standing_toe_flexor_stretch.webp'),   -- standing_toe_flexor_stretch
    ('dcf60f1f-4620-5b7c-b722-e29641d2839e'::uuid, 'Mobilidade/standing_crossed_leg_hamstring_stretch.mp4', 'Mobilidade/standing_crossed_leg_hamstring_stretch.webp'),   -- standing_crossed_leg_hamstring_stretch
    ('0a9813be-1be6-56d1-a164-0f28effacf65'::uuid, 'Mobilidade/lat_foam_rolling.mp4', 'Mobilidade/lat_foam_rolling.webp'),   -- lat_foam_rolling
    ('2c4e41e6-0c9f-5760-8679-34b23483e450'::uuid, 'Mobilidade/behind_the_back_shoulder_stretch.mp4', 'Mobilidade/behind_the_back_shoulder_stretch.webp'),   -- behind_the_back_shoulder_stretch
    ('23e5c2d6-fe73-5928-9d7e-984d32a2c35f'::uuid, 'Mobilidade/wrist_circles.mp4', 'Mobilidade/wrist_circles.webp'),   -- wrist_circles
    ('f8e94e2d-a8a0-5a79-b2a5-f0031f437eb6'::uuid, 'Mobilidade/standing_quad_stretch.mp4', 'Mobilidade/standing_quad_stretch.webp'),   -- standing_quad_stretch
    ('7334356d-cfd3-5d09-a4d0-deff9f70b49f'::uuid, 'Mobilidade/wall_corner_chest_stretch.mp4', 'Mobilidade/wall_corner_chest_stretch.webp'),   -- wall_corner_chest_stretch
    ('dff85371-747b-5432-b9c6-9694425afa3d'::uuid, 'Mobilidade/seated_foot_and_ankle_mobility.mp4', 'Mobilidade/seated_foot_and_ankle_mobility.webp'),   -- seated_foot_and_ankle_mobility
    ('1cc2b518-de1a-547a-86c5-fa0e4146e8f5'::uuid, 'Funcional e HIT/outdoor_cycling.mp4', 'Funcional e HIT/outdoor_cycling.webp'),   -- outdoor_cycling
    ('e3c76218-1b13-5ce2-b4ff-c03d8dd1623f'::uuid, 'Calistenia/duck_walk.mp4', 'Calistenia/duck_walk.webp'),   -- duck_walk
    ('676a6082-b3df-5a14-ad3b-a458f2ec9b90'::uuid, 'Crossfit/barbell_snatch.mp4', 'Crossfit/barbell_snatch.webp'),   -- barbell_snatch
    ('9fedf673-c862-57b0-861b-87db1a635cff'::uuid, 'Crossfit/kettlebell_one_arm_snatch.mp4', 'Crossfit/kettlebell_one_arm_snatch.webp'),   -- kettlebell_one_arm_snatch
    ('077cb8f0-c61e-548f-9dce-9e5c197cba7c'::uuid, 'Crossfit/kettlebell_split_snatch.mp4', 'Crossfit/kettlebell_split_snatch.webp'),   -- kettlebell_split_snatch
    ('51de3c39-fe70-5d2a-8999-38fd6119083c'::uuid, 'Crossfit/power_snatch.mp4', 'Crossfit/power_snatch.webp'),   -- power_snatch
    ('bb56b2cd-dcda-5eb9-8bd6-8ee8cf7eea88'::uuid, 'Crossfit/kettlebell_clean_and_press.mp4', 'Crossfit/kettlebell_clean_and_press.webp'),   -- kettlebell_clean_and_press
    ('01e8b2c9-ceea-55f5-ab6d-62f7d53e2ebd'::uuid, 'Crossfit/kettlebell_clean_and_jerk.mp4', 'Crossfit/kettlebell_clean_and_jerk.webp'),   -- kettlebell_clean_and_jerk
    ('17529f79-5dea-583f-9741-778fadec64f1'::uuid, 'Crossfit/barbell_jerk.mp4', 'Crossfit/barbell_jerk.webp'),   -- barbell_jerk
    ('66d5f29e-f6a4-5762-85e4-af9ec59bdcd9'::uuid, 'Crossfit/dumbbell_one_arm_snatch.mp4', 'Crossfit/dumbbell_one_arm_snatch.webp'),   -- dumbbell_one_arm_snatch
    ('0ad22277-28b9-5e2f-b855-c59b9c1c6cb8'::uuid, 'Funcional e HIT/reaction_ball_throw.mp4', 'Funcional e HIT/reaction_ball_throw.webp'),   -- reaction_ball_throw
    ('8992d197-2984-54d7-ab8c-8245f1672efc'::uuid, 'Crossfit/medicine_ball_sit_up_throw.mp4', 'Crossfit/medicine_ball_sit_up_throw.webp'),   -- medicine_ball_sit_up_throw
    ('82deba9e-2357-52b3-8ff1-f9814e6c570c'::uuid, 'Crossfit/barbell_clean_and_press.mp4', 'Crossfit/barbell_clean_and_press.webp'),   -- barbell_clean_and_press
    ('086f98c2-8a7e-531b-9e5c-883269d5ec1c'::uuid, 'Pernas/bodyweight_reverse_lunge.mp4', 'Pernas/bodyweight_reverse_lunge.webp'),   -- bodyweight_reverse_lunge
    ('28322bdc-85f9-5cfd-ba1f-1515a0ca2d0b'::uuid, 'Pernas/dumbbell_reverse_lunge.mp4', 'Pernas/dumbbell_reverse_lunge.webp'),   -- dumbbell_reverse_lunge
    ('859d61fb-7da8-56e2-aab3-0c4ce548f661'::uuid, 'Pernas/cable_lunge.mp4', 'Pernas/cable_lunge.webp'),   -- cable_lunge
    ('e1388f76-351d-5535-906a-f31e89f466b5'::uuid, 'Funcional e HIT/bosu_lunge_to_knee_drive.mp4', 'Funcional e HIT/bosu_lunge_to_knee_drive.webp'),   -- bosu_lunge_to_knee_drive
    ('60d7bf05-67bd-571d-9f5b-a5b3f9b86771'::uuid, 'Funcional e HIT/walking_lunge_with_knee_raise.mp4', 'Funcional e HIT/walking_lunge_with_knee_raise.webp'),   -- walking_lunge_with_knee_raise
    ('1d596773-d1c2-508d-9a4a-e38a29e0f05a'::uuid, 'Calistenia/bodyweight_forward_lunge.mp4', 'Calistenia/bodyweight_forward_lunge.webp'),   -- bodyweight_forward_lunge
    ('5bbd7eb6-ecf9-51b1-a7cc-3f019aa0434b'::uuid, 'Calistenia/back_lever.mp4', 'Calistenia/back_lever.webp'),   -- back_lever
    ('e6d50637-9b13-57f9-a71f-9665260d485f'::uuid, 'Funcional e HIT/gymstick_swing.mp4', 'Funcional e HIT/gymstick_swing.webp'),   -- gymstick_swing
    ('76fffc93-cacc-50e7-aa56-4c485600ab60'::uuid, 'Funcional e HIT/balloon_drill.mp4', 'Funcional e HIT/balloon_drill.webp'),   -- balloon_drill
    ('8f296b9f-49d7-5671-9500-8b34bf06cd55'::uuid, 'Calistenia/human_flag.mp4', 'Calistenia/human_flag.webp'),   -- human_flag
    ('24acfa49-e4f0-543f-8ff2-7968175d546c'::uuid, 'Crossfit/barbell_hang_clean.mp4', 'Crossfit/barbell_hang_clean.webp'),   -- barbell_hang_clean
    ('74aa29dc-db79-511d-894a-538860ba9b4a'::uuid, 'Calistenia/twisting_pull_up.mp4', 'Calistenia/twisting_pull_up.webp'),   -- twisting_pull_up
    ('6f2cf2f9-9664-500f-9660-a8ad842344a5'::uuid, 'Calistenia/close_grip_pull_up.mp4', 'Calistenia/close_grip_pull_up.webp'),   -- close_grip_pull_up
    ('b384be52-e007-5f8f-9e1e-9826ce799268'::uuid, 'Calistenia/chin_up.mp4', 'Calistenia/chin_up.webp'),   -- chin_up
    ('5811a31d-bad3-5057-9195-ce298d523618'::uuid, 'Calistenia/behind_the_neck_pull_up.mp4', 'Calistenia/behind_the_neck_pull_up.webp'),   -- behind_the_neck_pull_up
    ('b44564b6-55a2-5844-b8eb-10aa6d679d89'::uuid, 'Calistenia/upside_down_pull_up.mp4', 'Calistenia/upside_down_pull_up.webp'),   -- upside_down_pull_up
    ('2e535f18-b2f5-5d47-8ce8-4297dd9918af'::uuid, 'Calistenia/brachialis_pull_up.mp4', 'Calistenia/brachialis_pull_up.webp'),   -- brachialis_pull_up
    ('f45b280a-edf2-5366-9600-2bcd47d8fd42'::uuid, 'Costas/pull_up.mp4', 'Costas/pull_up.webp'),   -- pull_up
    ('8c7e8e57-5046-5942-8039-5343b70f21ab'::uuid, 'Calistenia/band_assisted_pull_up.mp4', 'Calistenia/band_assisted_pull_up.webp'),   -- band_assisted_pull_up
    ('2eb251a2-00e0-51be-ad6c-6d7a078d26de'::uuid, 'Calistenia/wide_grip_pull_up.mp4', 'Calistenia/wide_grip_pull_up.webp'),   -- wide_grip_pull_up
    ('4482e897-2ac7-56ad-bf00-e2dedaf0fe38'::uuid, 'Calistenia/l_sit_pull_up.mp4', 'Calistenia/l_sit_pull_up.webp'),   -- l_sit_pull_up
    ('2838a82b-8be4-52bf-b18f-f6d4893f1cc4'::uuid, 'Calistenia/jumping_pull_up.mp4', 'Calistenia/jumping_pull_up.webp'),   -- jumping_pull_up
    ('4eddee45-aff8-579d-998c-31dbcaebc442'::uuid, 'Calistenia/alternating_grip_pull_up.mp4', 'Calistenia/alternating_grip_pull_up.webp'),   -- alternating_grip_pull_up
    ('fac3978a-a1af-5e21-84a7-23dc34c030c6'::uuid, 'Calistenia/assisted_chin_up.mp4', 'Calistenia/assisted_chin_up.webp'),   -- assisted_chin_up
    ('460add81-0fe2-5241-9c6a-3dec2b43337e'::uuid, 'Calistenia/neutral_grip_pull_up.mp4', 'Calistenia/neutral_grip_pull_up.webp'),   -- neutral_grip_pull_up
    ('139afe7c-b21d-53aa-a588-9e7b37628e61'::uuid, 'Calistenia/weighted_pull_up.mp4', 'Calistenia/weighted_pull_up.webp'),   -- weighted_pull_up
    ('38a29474-3ddc-503d-90b7-c233b43d26a7'::uuid, 'Cardio/recumbent_bike.mp4', 'Cardio/recumbent_bike.webp'),   -- recumbent_bike
    ('4402be20-d5f3-548f-a3bc-00a8b4ebc959'::uuid, 'Cardio/stationary_bike.mp4', 'Cardio/stationary_bike.webp'),   -- stationary_bike
    ('0595c357-afaf-532c-9388-5f4a69844a9e'::uuid, 'Crossfit/medicine_ball_floor_to_overhead_lift.mp4', 'Crossfit/medicine_ball_floor_to_overhead_lift.webp'),   -- medicine_ball_floor_to_overhead_lift
    ('14c35837-4aff-5094-a8c8-7f61ba6bb82b'::uuid, 'Crossfit/wall_ball_shot.mp4', 'Crossfit/wall_ball_shot.webp'),   -- wall_ball_shot
    ('31b280a9-e70f-5c9e-93e9-2915ffaa3d95'::uuid, 'Funcional e HIT/band_good_morning.mp4', 'Funcional e HIT/band_good_morning.webp'),   -- band_good_morning
    ('168a8c59-689d-5126-a731-e0f752a141f5'::uuid, 'Pernas/smith_machine_good_morning.mp4', 'Pernas/smith_machine_good_morning.webp'),   -- smith_machine_good_morning
    ('028f6a66-6055-5818-a60e-70d3a4411fbe'::uuid, 'Funcional e HIT/shadow_boxing.mp4', 'Funcional e HIT/shadow_boxing.webp'),   -- shadow_boxing
    ('2e8ddcec-9ecb-596d-92bb-9e7d0d608487'::uuid, 'Funcional e HIT/boxing_jab.mp4', 'Funcional e HIT/boxing_jab.webp'),   -- boxing_jab
    ('f6f3fc1f-7c0c-5c62-b322-f87a637a07f1'::uuid, 'Crossfit/burpee_jack.mp4', 'Crossfit/burpee_jack.webp'),   -- burpee_jack
    ('8424de75-acf0-5106-9959-c5374bf42a58'::uuid, 'Crossfit/burpee.mp4', 'Crossfit/burpee.webp'),   -- burpee
    ('48194d9f-998f-5782-86e9-dc6b1e8afcc2'::uuid, 'Pernas/leg_extension.mp4', 'Pernas/leg_extension.webp'),   -- leg_extension
    ('a0b7125a-5dc9-5413-95d2-254627588fd0'::uuid, 'Pernas/seated_leg_curl.mp4', 'Pernas/seated_leg_curl.webp'),   -- seated_leg_curl
    ('45d6d4f5-82ea-5aad-8622-42f8cc5409d8'::uuid, 'Funcional e HIT/band_lateral_walk.mp4', 'Funcional e HIT/band_lateral_walk.webp'),   -- band_lateral_walk
    ('7db34f73-18ba-525a-b915-b367bad3d4d8'::uuid, 'Funcional e HIT/brisk_walk.mp4', 'Funcional e HIT/brisk_walk.webp'),   -- brisk_walk
    ('463aa08f-6b52-58cb-b6cb-289f3bf992c9'::uuid, 'Crossfit/dumbbell_farmer_s_walk.mp4', 'Crossfit/dumbbell_farmer_s_walk.webp'),   -- dumbbell_farmer_s_walk
    ('db910d11-cbc5-514a-967c-004d3ec8d739'::uuid, 'Calistenia/handstand_walk.mp4', 'Calistenia/handstand_walk.webp'),   -- handstand_walk
    ('ca0491cd-ab3b-5934-9b38-40e4dfd491a3'::uuid, 'Crossfit/wall_walk.mp4', 'Crossfit/wall_walk.webp'),   -- wall_walk
    ('fe9b254f-fe79-5d5b-9698-0f7b7dc3fd51'::uuid, 'Funcional e HIT/walking.mp4', 'Funcional e HIT/walking.webp'),   -- walking
    ('8a513913-6672-58bd-9e38-bb0fe019baa0'::uuid, 'Funcional e HIT/boxer_shuffle.mp4', 'Funcional e HIT/boxer_shuffle.webp'),   -- boxer_shuffle
    ('83cbac02-7949-5917-9179-bb30bda298e9'::uuid, 'Crossfit/zercher_carry.mp4', 'Crossfit/zercher_carry.webp'),   -- zercher_carry
    ('4240e19c-1d57-5a14-99bb-717ce1223fb9'::uuid, 'Mobilidade/standing_windmill_toe_touch.mp4', 'Mobilidade/standing_windmill_toe_touch.webp'),   -- standing_windmill_toe_touch
    ('1db276c9-3f9f-57c5-8a95-864fe47ae6dc'::uuid, 'Funcional e HIT/hook_kick.mp4', 'Funcional e HIT/hook_kick.webp'),   -- hook_kick
    ('6ab4ac53-dde4-5e72-a763-67051bd0e637'::uuid, 'Funcional e HIT/prone_bench_alternating_hip_extension.mp4', 'Funcional e HIT/prone_bench_alternating_hip_extension.webp'),   -- prone_bench_alternating_hip_extension
    ('962a5a2e-c6b5-52ab-9cd3-c67325140834'::uuid, 'Funcional e HIT/butt_kicks.mp4', 'Funcional e HIT/butt_kicks.webp'),   -- butt_kicks
    ('cf739d88-80ef-52c2-98b0-d624b5d01efe'::uuid, 'Funcional e HIT/bent_leg_glute_kickback.mp4', 'Funcional e HIT/bent_leg_glute_kickback.webp'),   -- bent_leg_glute_kickback
    ('a328013f-f3ce-52a0-a1d6-259df1966651'::uuid, 'Funcional e HIT/donkey_kick.mp4', 'Funcional e HIT/donkey_kick.webp'),   -- donkey_kick
    ('ffe95770-4657-5ae5-b303-38ea9f4857b7'::uuid, 'Mobilidade/supine_abdominal_bracing.mp4', 'Mobilidade/supine_abdominal_bracing.webp'),   -- supine_abdominal_bracing
    ('f99922b3-a768-5657-a56f-b6f0347f2512'::uuid, 'Crossfit/battle_rope.mp4', 'Crossfit/battle_rope.webp'),   -- battle_rope
    ('54676f7b-cada-5442-b86d-d0b1ab0d0cc3'::uuid, 'Funcional e HIT/running.mp4', 'Funcional e HIT/running.webp'),   -- running
    ('e9f60259-3b96-58eb-9578-6ebd325b83ac'::uuid, 'Funcional e HIT/jogging_in_place.mp4', 'Funcional e HIT/jogging_in_place.webp'),   -- jogging_in_place
    ('915f5a59-603f-59ac-818b-9f6cc4a67977'::uuid, 'Funcional e HIT/lateral_shuffle.mp4', 'Funcional e HIT/lateral_shuffle.webp'),   -- lateral_shuffle
    ('0cad3a20-b163-575d-abd3-ae046d8c3eda'::uuid, 'Funcional e HIT/high_knee_skip.mp4', 'Funcional e HIT/high_knee_skip.webp'),   -- high_knee_skip
    ('d59c90ce-030f-5811-bf2c-e41459d69aa5'::uuid, 'Funcional e HIT/high_knees.mp4', 'Funcional e HIT/high_knees.webp'),   -- high_knees
    ('9d3bbe9c-0e4f-5086-8fd0-d1eb05757686'::uuid, 'Funcional e HIT/fast_feet_run.mp4', 'Funcional e HIT/fast_feet_run.webp'),   -- fast_feet_run
    ('8bd3b51f-c6da-5286-a4ec-18a6f5f0d882'::uuid, 'Funcional e HIT/bounding.mp4', 'Funcional e HIT/bounding.webp'),   -- bounding
    ('1a230342-a4ce-50be-8567-9ac888cca92d'::uuid, 'Funcional e HIT/short_step_run.mp4', 'Funcional e HIT/short_step_run.webp'),   -- short_step_run
    ('3ef83fe3-a154-5657-aef3-8af2e7f55dc9'::uuid, 'Funcional e HIT/band_resisted_sprint.mp4', 'Funcional e HIT/band_resisted_sprint.webp'),   -- band_resisted_sprint
    ('2a74070a-2aa3-54e0-9f83-37dc7d4e84a6'::uuid, 'Cardio/standing_stationary_bike_ride.mp4', 'Cardio/standing_stationary_bike_ride.webp'),   -- standing_stationary_bike_ride
    ('286229ab-3fa0-5509-b3f2-491ddb390d5c'::uuid, 'Funcional e HIT/backward_running.mp4', 'Funcional e HIT/backward_running.webp'),   -- backward_running
    ('3cd35484-2799-5077-ac32-ae0b0ca00244'::uuid, 'Peitoral/high_cable_crossover.mp4', 'Peitoral/high_cable_crossover.webp'),   -- high_cable_crossover
    ('38516dcb-4c83-593e-99d8-7cf01aa0eacd'::uuid, 'Peitoral/low_cable_crossover.mp4', 'Peitoral/low_cable_crossover.webp'),   -- low_cable_crossover
    ('8f4f2c9b-e668-5507-94f7-eecb8eeb557a'::uuid, 'Peitoral/mid_cable_crossover.mp4', 'Peitoral/mid_cable_crossover.webp'),   -- mid_cable_crossover
    ('2253a9ac-da8e-5db6-8a65-319dd10a35e0'::uuid, 'Peitoral/one_arm_cable_crossover.mp4', 'Peitoral/one_arm_cable_crossover.webp'),   -- one_arm_cable_crossover
    ('f42cac87-8df2-5e9c-8f89-b494e15522b8'::uuid, 'Peitoral/low_to_high_cable_crossover.mp4', 'Peitoral/low_to_high_cable_crossover.webp'),   -- low_to_high_cable_crossover
    ('e283cb8f-be51-5a35-abd4-3df3da134b0c'::uuid, 'Peitoral/lever_chest_fly.mp4', 'Peitoral/lever_chest_fly.webp'),   -- lever_chest_fly
    ('6d83d55f-0ca7-5169-b025-8d909e5f06aa'::uuid, 'Peitoral/cable_flat_bench_fly.mp4', 'Peitoral/cable_flat_bench_fly.webp'),   -- cable_flat_bench_fly
    ('a0cf8055-e0f6-500c-876c-47a2c56f2773'::uuid, 'Peitoral/cable_incline_fly.mp4', 'Peitoral/cable_incline_fly.webp'),   -- cable_incline_fly
    ('91444c1a-3221-5422-ae20-577f10e17194'::uuid, 'Peitoral/cable_one_arm_decline_fly.mp4', 'Peitoral/cable_one_arm_decline_fly.webp'),   -- cable_one_arm_decline_fly
    ('bd24a296-5234-58e9-b904-5fea7402d17e'::uuid, 'Peitoral/cable_decline_fly.mp4', 'Peitoral/cable_decline_fly.webp'),   -- cable_decline_fly
    ('539de6e6-211d-52b1-a7ab-630f11d378bb'::uuid, 'Peitoral/dumbbell_decline_fly.mp4', 'Peitoral/dumbbell_decline_fly.webp'),   -- dumbbell_decline_fly
    ('800ef82d-c644-5d1f-97bf-0a13aee0c46c'::uuid, 'Peitoral/dumbbell_incline_fly.mp4', 'Peitoral/dumbbell_incline_fly.webp'),   -- dumbbell_incline_fly
    ('a5de77d1-7692-5226-ac51-ec26070ab9c2'::uuid, 'Calistenia/trx_chest_fly.mp4', 'Calistenia/trx_chest_fly.webp'),   -- trx_chest_fly
    ('8c6b53d7-e3d3-5ddd-bcd1-07436fdb208e'::uuid, 'Peitoral/dumbbell_flat_fly.mp4', 'Peitoral/dumbbell_flat_fly.webp'),   -- dumbbell_flat_fly
    ('e2c576b0-2d10-5db7-b9ec-8f262b48f9d9'::uuid, 'Ombros/cable_one_arm_reverse_fly.mp4', 'Ombros/cable_one_arm_reverse_fly.webp'),   -- cable_one_arm_reverse_fly
    ('a0a9ddec-2273-5be7-8851-989bdf5fe33a'::uuid, 'Funcional e HIT/gymstick_bent_over_reverse_fly.mp4', 'Funcional e HIT/gymstick_bent_over_reverse_fly.webp'),   -- gymstick_bent_over_reverse_fly
    ('fd95c625-b179-5ba2-a3dd-ca2df2bb71b9'::uuid, 'Funcional e HIT/right_cross.mp4', 'Funcional e HIT/right_cross.webp'),   -- right_cross
    ('97df468e-3505-5a70-af63-400e47ddd0b8'::uuid, 'Mobilidade/arm_circles.mp4', 'Mobilidade/arm_circles.webp'),   -- arm_circles
    ('b41664bd-ecd4-5d5a-ad0e-433751116c8f'::uuid, 'Mobilidade/single_arm_circles.mp4', 'Mobilidade/single_arm_circles.webp'),   -- single_arm_circles
    ('0b8ea2cc-b0ec-525b-8e4f-d7408791c609'::uuid, 'Ombros/weight_plate_arm_circles.mp4', 'Ombros/weight_plate_arm_circles.webp'),   -- weight_plate_arm_circles
    ('368f13fa-08a4-51fc-9dd5-f91c601062e6'::uuid, 'Pernas/single_leg_step_down.mp4', 'Pernas/single_leg_step_down.webp'),   -- single_leg_step_down
    ('9af4deba-30c1-534c-91ae-cdf7bcc5c33c'::uuid, 'Ombros/arnold_press.mp4', 'Ombros/arnold_press.webp'),   -- arnold_press
    ('cbc974d5-d735-5a9a-8cb0-7782967ec9d6'::uuid, 'Crossfit/kettlebell_one_arm_arnold_press.mp4', 'Crossfit/kettlebell_one_arm_arnold_press.webp'),   -- kettlebell_one_arm_arnold_press
    ('1f7e83d8-5170-540d-9fb9-e33e6b6253d6'::uuid, 'Ombros/dumbbell_cuban_press.mp4', 'Ombros/dumbbell_cuban_press.webp'),   -- dumbbell_cuban_press
    ('1ebbf774-956d-5cde-9e38-38724f1ec25c'::uuid, 'Ombros/one_arm_arnold_press.mp4', 'Ombros/one_arm_arnold_press.webp'),   -- one_arm_arnold_press
    ('3a6d8b78-bddc-5512-bef5-fa7e5bd175f4'::uuid, 'Ombros/seated_dumbbell_cuban_press.mp4', 'Ombros/seated_dumbbell_cuban_press.webp'),   -- seated_dumbbell_cuban_press
    ('969ef855-c8cc-5f17-8256-45374f805e8b'::uuid, 'Ombros/standing_alternating_dumbbell_press.mp4', 'Ombros/standing_alternating_dumbbell_press.webp'),   -- standing_alternating_dumbbell_press
    ('26038b4c-436d-5083-a3c1-65239107ac5a'::uuid, 'Ombros/seated_dumbbell_shoulder_press.mp4', 'Ombros/seated_dumbbell_shoulder_press.webp'),   -- seated_dumbbell_shoulder_press
    ('51e2cb9f-747a-5759-aecc-90154ca19877'::uuid, 'Ombros/alternating_rotational_dumbbell_press.mp4', 'Ombros/alternating_rotational_dumbbell_press.webp'),   -- alternating_rotational_dumbbell_press
    ('620b6822-ebf8-5e3f-963f-32ac3f84d8b9'::uuid, 'Ombros/seated_barbell_shoulder_press.mp4', 'Ombros/seated_barbell_shoulder_press.webp'),   -- seated_barbell_shoulder_press
    ('33c9d06f-6d25-543c-a9e9-f22f7c2d8491'::uuid, 'Ombros/standing_cable_shoulder_press.mp4', 'Ombros/standing_cable_shoulder_press.webp'),   -- standing_cable_shoulder_press
    ('00775e92-6658-5b0e-80e4-8a56d0781c5f'::uuid, 'Ombros/kneeling_cable_shoulder_press.mp4', 'Ombros/kneeling_cable_shoulder_press.webp'),   -- kneeling_cable_shoulder_press
    ('8e5fa2c9-897a-5651-ac83-40f8f85c88ce'::uuid, 'Ombros/dumbbell_z_press.mp4', 'Ombros/dumbbell_z_press.webp'),   -- dumbbell_z_press
    ('a2657914-4c3a-50ad-a25f-cd63af52f697'::uuid, 'Ombros/dumbbell_w_press.mp4', 'Ombros/dumbbell_w_press.webp'),   -- dumbbell_w_press
    ('4342d924-d80b-5bad-b36a-3dd71507792f'::uuid, 'Crossfit/kettlebell_shoulder_press.mp4', 'Crossfit/kettlebell_shoulder_press.webp'),   -- kettlebell_shoulder_press
    ('96a10fa6-0de4-5639-9b93-1f1543b687d9'::uuid, 'Ombros/prone_dumbbell_press.mp4', 'Ombros/prone_dumbbell_press.webp'),   -- prone_dumbbell_press
    ('66b8014c-cdfa-55af-8d2c-f621cf030706'::uuid, 'Ombros/plate_loaded_shoulder_press.mp4', 'Ombros/plate_loaded_shoulder_press.webp'),   -- plate_loaded_shoulder_press
    ('b6b5f2f0-af38-5071-a170-cfc6e6c1dd03'::uuid, 'Ombros/machine_neutral_grip_shoulder_press.mp4', 'Ombros/machine_neutral_grip_shoulder_press.webp'),   -- machine_neutral_grip_shoulder_press
    ('ea5b562a-c415-5041-8c7f-5a4233528235'::uuid, 'Ombros/lever_reverse_shoulder_press.mp4', 'Ombros/lever_reverse_shoulder_press.webp'),   -- lever_reverse_shoulder_press
    ('4916e991-8345-591c-95d6-5e3f8c8baf91'::uuid, 'Funcional e HIT/seated_band_shoulder_press.mp4', 'Funcional e HIT/seated_band_shoulder_press.webp'),   -- seated_band_shoulder_press
    ('e57c25ed-a414-5b40-b6d0-c59203d0b43a'::uuid, 'Funcional e HIT/band_one_arm_shoulder_press.mp4', 'Funcional e HIT/band_one_arm_shoulder_press.webp'),   -- band_one_arm_shoulder_press
    ('d7b3caf6-c682-5c1f-be48-50a913b67a37'::uuid, 'Ombros/one_arm_dumbbell_shoulder_press.mp4', 'Ombros/one_arm_dumbbell_shoulder_press.webp'),   -- one_arm_dumbbell_shoulder_press
    ('7db5a256-df8a-5959-9dd8-b8bc923c671b'::uuid, 'Ombros/smith_machine_behind_the_neck_press.mp4', 'Ombros/smith_machine_behind_the_neck_press.webp'),   -- smith_machine_behind_the_neck_press
    ('e934af4b-fe4d-5438-a3fa-f40bff8556b4'::uuid, 'Ombros/seated_barbell_behind_the_neck_press.mp4', 'Ombros/seated_barbell_behind_the_neck_press.webp'),   -- seated_barbell_behind_the_neck_press
    ('c2a5c7ef-5ad9-5c0d-981f-6eac0416b716'::uuid, 'Ombros/ez_bar_reverse_grip_shoulder_press.mp4', 'Ombros/ez_bar_reverse_grip_shoulder_press.webp'),   -- ez_bar_reverse_grip_shoulder_press
    ('2a53cd33-7f44-5a00-aafe-92f857551c1a'::uuid, 'Ombros/standing_neutral_grip_dumbbell_press.mp4', 'Ombros/standing_neutral_grip_dumbbell_press.webp'),   -- standing_neutral_grip_dumbbell_press
    ('c4cbc095-c390-5343-a460-c261e55e8d64'::uuid, 'Ombros/smith_machine_shoulder_press.mp4', 'Ombros/smith_machine_shoulder_press.webp'),   -- smith_machine_shoulder_press
    ('6559e344-481b-5d19-bf77-2a8b3ddd3694'::uuid, 'Funcional e HIT/gymstick_one_arm_press.mp4', 'Funcional e HIT/gymstick_one_arm_press.webp'),   -- gymstick_one_arm_press
    ('b0e380b9-66fc-5f86-8c55-ad67f75e0222'::uuid, 'Funcional e HIT/gymstick_behind_the_neck_press.mp4', 'Funcional e HIT/gymstick_behind_the_neck_press.webp'),   -- gymstick_behind_the_neck_press
    ('f6ca3c1e-5bda-5b50-bb52-37c95b0e7d15'::uuid, 'Ombros/barbell_military_press.mp4', 'Ombros/barbell_military_press.webp'),   -- barbell_military_press
    ('02ac3d8d-9269-5224-87e4-2256254ef07a'::uuid, 'Ombros/kneeling_landmine_press.mp4', 'Ombros/kneeling_landmine_press.webp'),   -- kneeling_landmine_press
    ('ba3b0923-afdb-5fdb-923a-cb570d0e3ee5'::uuid, 'Ombros/close_grip_military_press.mp4', 'Ombros/close_grip_military_press.webp'),   -- close_grip_military_press
    ('2b899543-ebed-5adb-b80b-3a279fe475c5'::uuid, 'Funcional e HIT/bodyweight_shoulder_press.mp4', 'Funcional e HIT/bodyweight_shoulder_press.webp'),   -- bodyweight_shoulder_press
    ('c6e4a588-d987-59ce-bbac-bbe5761d99d7'::uuid, 'Ombros/one_arm_kettlebell_press.mp4', 'Ombros/one_arm_kettlebell_press.webp'),   -- one_arm_kettlebell_press
    ('a6bc8644-82ee-51fd-8ba6-6e87229c24da'::uuid, 'Ombros/standing_smith_machine_military_press.mp4', 'Ombros/standing_smith_machine_military_press.webp'),   -- standing_smith_machine_military_press
    ('8dbae9b3-dd1c-54ad-b82d-db583afe6d93'::uuid, 'Ombros/standing_landmine_press.mp4', 'Ombros/standing_landmine_press.webp'),   -- standing_landmine_press
    ('1eb8d16b-2105-5a8e-9eb2-1b4446a40f17'::uuid, 'Crossfit/kettlebell_kneeling_one_arm_press.mp4', 'Crossfit/kettlebell_kneeling_one_arm_press.webp'),   -- kettlebell_kneeling_one_arm_press
    ('0ed7ea8a-23ed-5e4c-9ee5-2afa0072c70c'::uuid, 'Mobilidade/foam_roller_serratus_wall_slide.mp4', 'Mobilidade/foam_roller_serratus_wall_slide.webp'),   -- foam_roller_serratus_wall_slide
    ('6f99f470-52b4-5c78-bf8f-c19c1f603550'::uuid, 'Trapézio/bench_scapular_dip.mp4', 'Trapézio/bench_scapular_dip.webp'),   -- bench_scapular_dip
    ('41ad8f1f-f88e-500e-a2b0-28c53fb1c30f'::uuid, 'Calistenia/chair_dip.mp4', 'Calistenia/chair_dip.webp'),   -- chair_dip
    ('cf7f3b1a-24ae-5057-9cd8-96b0061e3692'::uuid, 'Mobilidade/standing_plantar_flexion.mp4', 'Mobilidade/standing_plantar_flexion.webp'),   -- standing_plantar_flexion
    ('76e9d119-b670-5eb3-a12a-46c57fc1eb03'::uuid, 'Crossfit/dumbbell_devil_press.mp4', 'Crossfit/dumbbell_devil_press.webp'),   -- dumbbell_devil_press
    ('7c7f6937-8a6a-5cb1-a6e9-b27692d7b2c9'::uuid, 'Crossfit/dumbbell_power_clean.mp4', 'Crossfit/dumbbell_power_clean.webp'),   -- dumbbell_power_clean
    ('81e75d47-1c78-5cdb-9a07-d6992a485431'::uuid, 'Ombros/dumbbell_alternating_front_raise.mp4', 'Ombros/dumbbell_alternating_front_raise.webp'),   -- dumbbell_alternating_front_raise
    ('b7de6dbd-dd3e-5cf5-b91d-69193d6e34a2'::uuid, 'Funcional e HIT/band_fire_hydrant.mp4', 'Funcional e HIT/band_fire_hydrant.webp'),   -- band_fire_hydrant
    ('93ddb9f4-8fb8-540d-8fa9-c2242ddd35d9'::uuid, 'Funcional e HIT/side_lying_band_leg_raise.mp4', 'Funcional e HIT/side_lying_band_leg_raise.webp'),   -- side_lying_band_leg_raise
    ('ca911adc-0764-53b2-952b-c2bba3f6f37f'::uuid, 'Ombros/side_lying_rear_delt_raise.mp4', 'Ombros/side_lying_rear_delt_raise.webp'),   -- side_lying_rear_delt_raise
    ('3a27c30a-6a50-59e1-9231-69eb2d9ac237'::uuid, 'Ombros/prone_one_arm_dumbbell_rear_delt_raise.mp4', 'Ombros/prone_one_arm_dumbbell_rear_delt_raise.webp'),   -- prone_one_arm_dumbbell_rear_delt_raise
    ('be6b8864-8726-50a6-88c8-21ecad52ce36'::uuid, 'Glúteos/barbell_hip_thrust.mp4', 'Glúteos/barbell_hip_thrust.webp'),   -- barbell_hip_thrust
    ('0feca28b-00c8-533b-9041-a593c8cf2435'::uuid, 'Glúteos/feet_elevated_barbell_hip_thrust.mp4', 'Glúteos/feet_elevated_barbell_hip_thrust.webp'),   -- feet_elevated_barbell_hip_thrust
    ('e0b9dd49-7e09-55b2-b9af-48fdf9d57978'::uuid, 'Calistenia/feet_elevated_glute_bridge.mp4', 'Calistenia/feet_elevated_glute_bridge.webp'),   -- feet_elevated_glute_bridge
    ('6e66436b-c2ba-565f-908d-f858b8fa0dc4'::uuid, 'Glúteos/machine_hip_thrust.mp4', 'Glúteos/machine_hip_thrust.webp'),   -- machine_hip_thrust
    ('427d12d6-41cc-5bb4-bfa1-afe4da6aa002'::uuid, 'Glúteos/barbell_single_leg_hip_thrust.mp4', 'Glúteos/barbell_single_leg_hip_thrust.webp'),   -- barbell_single_leg_hip_thrust
    ('d21d72e0-25ab-5a57-a1ad-5803cf671c4b'::uuid, 'Funcional e HIT/band_hip_thrust.mp4', 'Funcional e HIT/band_hip_thrust.webp'),   -- band_hip_thrust
    ('3cd26c0e-8c83-55a5-9032-d26db3ed37ca'::uuid, 'Glúteos/smith_machine_hip_thrust.mp4', 'Glúteos/smith_machine_hip_thrust.webp'),   -- smith_machine_hip_thrust
    ('f151a13f-b061-56fe-8a5e-815a11dc4c2a'::uuid, 'Glúteos/leg_extension_machine_hip_thrust.mp4', 'Glúteos/leg_extension_machine_hip_thrust.webp'),   -- leg_extension_machine_hip_thrust
    ('553f12f8-c110-5a94-a70c-80fde577ba6d'::uuid, 'Panturrilhas/single_leg_leg_press_calf_raise.mp4', 'Panturrilhas/single_leg_leg_press_calf_raise.webp'),   -- single_leg_leg_press_calf_raise
    ('eba1a864-5eec-527e-8a7d-9a04fc9c8f6a'::uuid, 'Pernas/barbell_step_up.mp4', 'Pernas/barbell_step_up.webp'),   -- barbell_step_up
    ('b76a8f68-e1dd-5b2e-b8f7-4bad12a22644'::uuid, 'Funcional e HIT/step_up_with_cross_body_elbow_to_knee.mp4', 'Funcional e HIT/step_up_with_cross_body_elbow_to_knee.webp'),   -- step_up_with_cross_body_elbow_to_knee
    ('2e62e69e-2fb1-5e93-9825-9e4d79285415'::uuid, 'Funcional e HIT/standing_band_hip_flexion.mp4', 'Funcional e HIT/standing_band_hip_flexion.webp'),   -- standing_band_hip_flexion
    ('dc455c03-e442-51c7-ab03-96c1d7774653'::uuid, 'Trapézio/incline_dumbbell_rear_delt_raise.mp4', 'Trapézio/incline_dumbbell_rear_delt_raise.webp'),   -- incline_dumbbell_rear_delt_raise
    ('db085c1a-7fb2-50a3-a97e-6b2ee8cd029d'::uuid, 'Trapézio/incline_dumbbell_y_raise.mp4', 'Trapézio/incline_dumbbell_y_raise.webp'),   -- incline_dumbbell_y_raise
    ('9ed0cdb8-bbdd-5e1d-be5c-eca202503d31'::uuid, 'Ombros/dumbbell_4_way_raise.mp4', 'Ombros/dumbbell_4_way_raise.webp'),   -- dumbbell_4_way_raise
    ('a4d21e9b-11f8-59d2-9fdd-79cbb667a67d'::uuid, 'Pernas/dumbbell_step_up_with_knee_drive.mp4', 'Pernas/dumbbell_step_up_with_knee_drive.webp'),   -- dumbbell_step_up_with_knee_drive
    ('8e8d03f8-1b9a-529c-a3e6-4438cf8c9f18'::uuid, 'Panturrilhas/seated_barbell_calf_raise.mp4', 'Panturrilhas/seated_barbell_calf_raise.webp'),   -- seated_barbell_calf_raise
    ('db3082af-cd90-58d5-b4ee-7fcbcd378637'::uuid, 'Panturrilhas/seated_plate_calf_raise.mp4', 'Panturrilhas/seated_plate_calf_raise.webp'),   -- seated_plate_calf_raise
    ('94fc815a-8d7d-5189-865e-17708066df09'::uuid, 'Panturrilhas/standing_barbell_calf_raise.mp4', 'Panturrilhas/standing_barbell_calf_raise.webp'),   -- standing_barbell_calf_raise
    ('dd3c4c22-8c70-5632-8851-c3682c98be6a'::uuid, 'Funcional e HIT/band_calf_raise.mp4', 'Funcional e HIT/band_calf_raise.webp'),   -- band_calf_raise
    ('4c6c3776-7dfe-55e4-b630-c328c7fc6dee'::uuid, 'Panturrilhas/single_leg_hack_machine_calf_raise.mp4', 'Panturrilhas/single_leg_hack_machine_calf_raise.webp'),   -- single_leg_hack_machine_calf_raise
    ('2261dcae-1a1d-50e1-820b-1f3b473abb79'::uuid, 'Panturrilhas/standing_machine_calf_raise.mp4', 'Panturrilhas/standing_machine_calf_raise.webp'),   -- standing_machine_calf_raise
    ('5a6a5eff-5fe3-54c4-b845-52a24c31f1bf'::uuid, 'Calistenia/single_leg_calf_raise.mp4', 'Calistenia/single_leg_calf_raise.webp'),   -- single_leg_calf_raise
    ('65739122-6ec7-5dd8-9aa8-ce783719cffd'::uuid, 'Panturrilhas/shoulder_pad_standing_calf_raise.mp4', 'Panturrilhas/shoulder_pad_standing_calf_raise.webp'),   -- shoulder_pad_standing_calf_raise
    ('e0dfbfe7-db4d-52a8-a554-865358e1fe1e'::uuid, 'Panturrilhas/leg_press_calf_raise.mp4', 'Panturrilhas/leg_press_calf_raise.webp'),   -- leg_press_calf_raise
    ('3535c70f-1b66-51db-a396-9bbc880a9748'::uuid, 'Panturrilhas/horizontal_leg_press_calf_raise.mp4', 'Panturrilhas/horizontal_leg_press_calf_raise.webp'),   -- horizontal_leg_press_calf_raise
    ('a78ce2ff-7796-5d04-9dac-5f673e7c5236'::uuid, 'Panturrilhas/smith_machine_calf_raise.mp4', 'Panturrilhas/smith_machine_calf_raise.webp'),   -- smith_machine_calf_raise
    ('43ab3d6c-566b-516a-89a0-3e9ee4456d02'::uuid, 'Panturrilhas/standing_dumbbell_calf_raise.mp4', 'Panturrilhas/standing_dumbbell_calf_raise.webp'),   -- standing_dumbbell_calf_raise
    ('9976fc24-9725-564d-a627-b66bcd6b5143'::uuid, 'Panturrilhas/hack_machine_calf_raise.mp4', 'Panturrilhas/hack_machine_calf_raise.webp'),   -- hack_machine_calf_raise
    ('0e064ee0-7eac-546d-8da6-b2136af5cbc7'::uuid, 'Funcional e HIT/standing_band_straight_leg_raise.mp4', 'Funcional e HIT/standing_band_straight_leg_raise.webp'),   -- standing_band_straight_leg_raise
    ('c985f94c-cd96-57d3-8422-70d784bb8732'::uuid, 'Pernas/lever_standing_hip_flexion.mp4', 'Pernas/lever_standing_hip_flexion.webp'),   -- lever_standing_hip_flexion
    ('b70a4789-b72e-567f-b0a4-df6c3725f8b3'::uuid, 'Crossfit/step_up_to_knee_raise_with_biceps_curl.mp4', 'Crossfit/step_up_to_knee_raise_with_biceps_curl.webp'),   -- step_up_to_knee_raise_with_biceps_curl
    ('6cf9561b-4efe-5b70-a46b-a1bef038140d'::uuid, 'Funcional e HIT/side_lying_double_leg_raise.mp4', 'Funcional e HIT/side_lying_double_leg_raise.webp'),   -- side_lying_double_leg_raise
    ('21d12780-1b5d-5232-9bfb-17ba038e59c3'::uuid, 'Funcional e HIT/frog_pump.mp4', 'Funcional e HIT/frog_pump.webp'),   -- frog_pump
    ('9467f7f2-f9ea-5d48-ab1c-0beef021345a'::uuid, 'Funcional e HIT/kneeling_band_hip_thrust.mp4', 'Funcional e HIT/kneeling_band_hip_thrust.webp'),   -- kneeling_band_hip_thrust
    ('cf89a61c-01fc-5440-9103-c4a7c749f019'::uuid, 'Trapézio/incline_dumbbell_t_raise.mp4', 'Trapézio/incline_dumbbell_t_raise.webp'),   -- incline_dumbbell_t_raise
    ('37492785-d612-52be-a187-969e5c36587b'::uuid, 'Panturrilhas/lever_seated_calf_raise.mp4', 'Panturrilhas/lever_seated_calf_raise.webp'),   -- lever_seated_calf_raise
    ('e63cb82e-e44d-5d58-9312-e0ac9ba442ba'::uuid, 'Calistenia/standing_step_calf_raise.mp4', 'Calistenia/standing_step_calf_raise.webp'),   -- standing_step_calf_raise
    ('6acd9615-6613-595f-9465-e561ec108102'::uuid, 'Ombros/ez_bar_front_raise_with_rotation.mp4', 'Ombros/ez_bar_front_raise_with_rotation.webp'),   -- ez_bar_front_raise_with_rotation
    ('65e24f07-39db-5331-83d1-33c267bf91fb'::uuid, 'Ombros/incline_ez_bar_front_raise.mp4', 'Ombros/incline_ez_bar_front_raise.webp'),   -- incline_ez_bar_front_raise
    ('2acc77d1-bd4e-5940-82f5-1eedfeb8fd70'::uuid, 'Ombros/dumbbell_two_arm_front_raise.mp4', 'Ombros/dumbbell_two_arm_front_raise.webp'),   -- dumbbell_two_arm_front_raise
    ('d19c46ee-4b3b-5abd-9383-c4c94a244a0b'::uuid, 'Ombros/dumbbell_front_raise.mp4', 'Ombros/dumbbell_front_raise.webp'),   -- dumbbell_front_raise
    ('d44867c4-68e7-5f5f-bbd3-b9d91e00bd21'::uuid, 'Ombros/seated_dumbbell_front_raise.mp4', 'Ombros/seated_dumbbell_front_raise.webp'),   -- seated_dumbbell_front_raise
    ('f1b6b2f4-ce3b-5466-a4bb-63533c424f37'::uuid, 'Funcional e HIT/band_front_raise.mp4', 'Funcional e HIT/band_front_raise.webp'),   -- band_front_raise
    ('ea7abfd1-1f95-59c4-aabb-1e79beaa4cf0'::uuid, 'Ombros/dumbbell_alternating_lateral_raise.mp4', 'Ombros/dumbbell_alternating_lateral_raise.webp'),   -- dumbbell_alternating_lateral_raise
    ('a77051be-f560-5cc6-a543-d0b15af32dfa'::uuid, 'Ombros/landmine_lateral_raise.mp4', 'Ombros/landmine_lateral_raise.webp'),   -- landmine_lateral_raise
    ('daa734aa-6ed3-5ff0-b776-5a07912924fb'::uuid, 'Ombros/bent_arm_lateral_raise.mp4', 'Ombros/bent_arm_lateral_raise.webp'),   -- bent_arm_lateral_raise
    ('1cccf2ed-54da-5cb6-85e6-146f225c7c49'::uuid, 'Ombros/chest_supported_lateral_raise.mp4', 'Ombros/chest_supported_lateral_raise.webp'),   -- chest_supported_lateral_raise
    ('16de9f98-e03d-569e-8af0-4aaea68494f8'::uuid, 'Ombros/seated_dumbbell_lateral_raise.mp4', 'Ombros/seated_dumbbell_lateral_raise.webp'),   -- seated_dumbbell_lateral_raise
    ('7b1ba98f-fdac-5066-8270-9ef7a1aa9314'::uuid, 'Calistenia/towel_wall_isometric_lateral_raise.mp4', 'Calistenia/towel_wall_isometric_lateral_raise.webp'),   -- towel_wall_isometric_lateral_raise
    ('3f4442f7-b028-544a-b729-faff7658e789'::uuid, 'Ombros/dumbbell_bent_over_reverse_fly.mp4', 'Ombros/dumbbell_bent_over_reverse_fly.webp'),   -- dumbbell_bent_over_reverse_fly
    ('bed8ad38-d36f-5397-ae69-6804e57b1c51'::uuid, 'Ombros/cable_crossover_lateral_raise.mp4', 'Ombros/cable_crossover_lateral_raise.webp'),   -- cable_crossover_lateral_raise
    ('2bd308a2-b65f-5da1-af82-5491a549e542'::uuid, 'Funcional e HIT/bodyweight_lateral_raise.mp4', 'Funcional e HIT/bodyweight_lateral_raise.webp'),   -- bodyweight_lateral_raise
    ('bb2e5b1e-b501-583c-a650-962638cebf06'::uuid, 'Ombros/cable_one_arm_lateral_raise.mp4', 'Ombros/cable_one_arm_lateral_raise.webp'),   -- cable_one_arm_lateral_raise
    ('108d9f2e-d23c-5bc2-a911-bd685da8d8d9'::uuid, 'Ombros/dumbbell_lateral_raise.mp4', 'Ombros/dumbbell_lateral_raise.webp'),   -- dumbbell_lateral_raise
    ('f0b4d498-525a-5aad-87a6-9458995975b3'::uuid, 'Ombros/side_lying_dumbbell_rear_delt_raise.mp4', 'Ombros/side_lying_dumbbell_rear_delt_raise.webp'),   -- side_lying_dumbbell_rear_delt_raise
    ('a9562f06-34bb-52d2-9605-75ed21a0cd3e'::uuid, 'Ombros/incline_side_lying_lateral_raise.mp4', 'Ombros/incline_side_lying_lateral_raise.webp'),   -- incline_side_lying_lateral_raise
    ('1179ecc2-e8db-5c09-a982-4573a78c3f35'::uuid, 'Ombros/prone_bench_reverse_fly.mp4', 'Ombros/prone_bench_reverse_fly.webp'),   -- prone_bench_reverse_fly
    ('e500d80e-9a6f-51c0-86c9-ba18ba039bf4'::uuid, 'Ombros/dumbbell_lateral_to_front_raise.mp4', 'Ombros/dumbbell_lateral_to_front_raise.webp'),   -- dumbbell_lateral_to_front_raise
    ('5696f95f-61d9-5804-8748-098f2f907ac7'::uuid, 'Ombros/chest_facing_machine_lateral_raise.mp4', 'Ombros/chest_facing_machine_lateral_raise.webp'),   -- chest_facing_machine_lateral_raise
    ('fbd921a0-69ba-519a-95bf-d12dcaeca142'::uuid, 'Ombros/cable_leaning_one_arm_lateral_raise.mp4', 'Ombros/cable_leaning_one_arm_lateral_raise.webp'),   -- cable_leaning_one_arm_lateral_raise
    ('4bf047bc-cb7c-5cbe-8181-b7f64308f6b5'::uuid, 'Ombros/leaning_one_arm_dumbbell_lateral_raise.mp4', 'Ombros/leaning_one_arm_dumbbell_lateral_raise.webp'),   -- leaning_one_arm_dumbbell_lateral_raise
    ('cd64f7c7-60ed-56b6-8add-99f00b8250b0'::uuid, 'Ombros/one_arm_dumbbell_lateral_raise.mp4', 'Ombros/one_arm_dumbbell_lateral_raise.webp'),   -- one_arm_dumbbell_lateral_raise
    ('8cb731b8-1a9c-5f3b-8774-1daa61dd78e2'::uuid, 'Calistenia/scapular_dip.mp4', 'Calistenia/scapular_dip.webp'),   -- scapular_dip
    ('872180a5-23f4-562c-ac1f-7de700376d45'::uuid, 'Ombros/chest_supported_dumbbell_front_raise.mp4', 'Ombros/chest_supported_dumbbell_front_raise.webp'),   -- chest_supported_dumbbell_front_raise
    ('afb5e1db-17d3-545e-a487-ff207762bfae'::uuid, 'Trapézio/overhead_shrug.mp4', 'Trapézio/overhead_shrug.webp'),   -- overhead_shrug
    ('c77d18b0-66fd-5dcb-b964-1c0357300e47'::uuid, 'Trapézio/incline_bench_seated_shrug.mp4', 'Trapézio/incline_bench_seated_shrug.webp'),   -- incline_bench_seated_shrug
    ('9869db9a-f3f1-5f4f-bea7-480a3e5e402b'::uuid, 'Trapézio/seated_gittleson_dumbbell_shrug.mp4', 'Trapézio/seated_gittleson_dumbbell_shrug.webp'),   -- seated_gittleson_dumbbell_shrug
    ('1bd30711-a8df-5f37-a4cf-100c7c475747'::uuid, 'Trapézio/cable_shrug.mp4', 'Trapézio/cable_shrug.webp'),   -- cable_shrug
    ('a0154f7e-603d-528f-b311-33c5bcd8647a'::uuid, 'Trapézio/dumbbell_shrug.mp4', 'Trapézio/dumbbell_shrug.webp'),   -- dumbbell_shrug
    ('dbf8cb70-28c9-5478-8f5d-0250e8871bf5'::uuid, 'Trapézio/incline_chest_supported_dumbbell_shrug.mp4', 'Trapézio/incline_chest_supported_dumbbell_shrug.webp'),   -- incline_chest_supported_dumbbell_shrug
    ('7c19862e-402a-542e-86fc-024c634a7832'::uuid, 'Trapézio/barbell_shrug.mp4', 'Trapézio/barbell_shrug.webp'),   -- barbell_shrug
    ('defbddab-38a9-5745-9722-b570f5c06660'::uuid, 'Trapézio/behind_the_back_barbell_shrug.mp4', 'Trapézio/behind_the_back_barbell_shrug.webp'),   -- behind_the_back_barbell_shrug
    ('1d8452b4-af41-5e43-8e0a-9bc2cf0fc0bb'::uuid, 'Trapézio/smith_machine_shrug.mp4', 'Trapézio/smith_machine_shrug.webp'),   -- smith_machine_shrug
    ('bf4a6a68-5945-530a-ac27-38932e0c118d'::uuid, 'Trapézio/rack_behind_the_back_barbell_shrug.mp4', 'Trapézio/rack_behind_the_back_barbell_shrug.webp'),   -- rack_behind_the_back_barbell_shrug
    ('a94bd8de-458e-5bd0-917d-203e26404a63'::uuid, 'Trapézio/machine_shrug.mp4', 'Trapézio/machine_shrug.webp'),   -- machine_shrug
    ('0d86cd7a-91dd-56d2-a83a-47ccbb927a3f'::uuid, 'Funcional e HIT/standing_mountain_climber.mp4', 'Funcional e HIT/standing_mountain_climber.webp'),   -- standing_mountain_climber
    ('612c04cb-a5f9-5c92-8ce3-ac00674fdecb'::uuid, 'Funcional e HIT/gymstick_skier.mp4', 'Funcional e HIT/gymstick_skier.webp'),   -- gymstick_skier
    ('55f98656-91f5-59f2-b8cd-f3e06812a269'::uuid, 'Cardio/treadmill_run.mp4', 'Cardio/treadmill_run.webp'),   -- treadmill_run
    ('1e688237-b9e7-59d9-904a-e7a10f9c2c3f'::uuid, 'Cardio/incline_treadmill_walk.mp4', 'Cardio/incline_treadmill_walk.webp'),   -- incline_treadmill_walk
    ('f837156b-144a-50d0-8b37-1aa3d1cfa3d9'::uuid, 'Funcional e HIT/x_plyometric_drill.mp4', 'Funcional e HIT/x_plyometric_drill.webp'),   -- x_plyometric_drill
    ('0b1ba8c3-74c1-573f-98bd-795d39b13358'::uuid, 'Mobilidade/seated_ballerina_arm_drill.mp4', 'Mobilidade/seated_ballerina_arm_drill.webp'),   -- seated_ballerina_arm_drill
    ('5121553e-0a4a-5930-90b8-4fdbb36fae62'::uuid, 'Mobilidade/seated_scapular_retraction.mp4', 'Mobilidade/seated_scapular_retraction.webp'),   -- seated_scapular_retraction
    ('1263e640-b6d4-5454-8706-b4c2959f250b'::uuid, 'Funcional e HIT/5_dot_drill.mp4', 'Funcional e HIT/5_dot_drill.webp'),   -- 5_dot_drill
    ('de82b6ce-4c99-5b6d-8589-3f1fd755b802'::uuid, 'Funcional e HIT/agility_ladder_drill.mp4', 'Funcional e HIT/agility_ladder_drill.webp'),   -- agility_ladder_drill
    ('ecfb94f8-3688-587d-95cb-7bb91735e514'::uuid, 'Funcional e HIT/lateral_agility_ladder_drill.mp4', 'Funcional e HIT/lateral_agility_ladder_drill.webp'),   -- lateral_agility_ladder_drill
    ('187bcdd0-b634-5794-9338-642920fad877'::uuid, 'Tríceps/kneeling_one_arm_cable_triceps_extension.mp4', 'Tríceps/kneeling_one_arm_cable_triceps_extension.webp'),   -- kneeling_one_arm_cable_triceps_extension
    ('070304fb-de21-5e21-8720-93ad943f2657'::uuid, 'Funcional e HIT/standing_glute_kickback.mp4', 'Funcional e HIT/standing_glute_kickback.webp'),   -- standing_glute_kickback
    ('1a7f25a9-6953-5257-85d9-c298be0bf5a1'::uuid, 'Funcional e HIT/quadruped_straight_leg_hip_extension.mp4', 'Funcional e HIT/quadruped_straight_leg_hip_extension.webp'),   -- quadruped_straight_leg_hip_extension
    ('ff14f2bb-8293-5bf2-bf1e-b692d9e6a684'::uuid, 'Eretor Lombar/weighted_back_extension.mp4', 'Eretor Lombar/weighted_back_extension.webp'),   -- weighted_back_extension
    ('4db6256f-f8be-5bfb-a172-e5ad75f430af'::uuid, 'Pernas/single_leg_extension.mp4', 'Pernas/single_leg_extension.webp'),   -- single_leg_extension
    ('3321cdb7-8d97-58ce-bafa-3b3c94d02aa6'::uuid, 'Funcional e HIT/standing_band_hip_extension.mp4', 'Funcional e HIT/standing_band_hip_extension.webp'),   -- standing_band_hip_extension
    ('120818cb-43cb-53b9-bdf0-10dbf63dd342'::uuid, 'Glúteos/smith_machine_prone_hip_extension.mp4', 'Glúteos/smith_machine_prone_hip_extension.webp'),   -- smith_machine_prone_hip_extension
    ('11e934f1-173e-5fed-9884-d605e693ae8a'::uuid, 'Funcional e HIT/seated_mini_band_leg_extension.mp4', 'Funcional e HIT/seated_mini_band_leg_extension.webp'),   -- seated_mini_band_leg_extension
    ('e0310bfd-88f9-5a44-82e5-5d1144b60e84'::uuid, 'Funcional e HIT/seated_tube_band_leg_extension.mp4', 'Funcional e HIT/seated_tube_band_leg_extension.webp'),   -- seated_tube_band_leg_extension
    ('970f488d-03e9-5a6c-9624-4f2971134ef7'::uuid, 'Glúteos/cable_standing_hip_extension.mp4', 'Glúteos/cable_standing_hip_extension.webp'),   -- cable_standing_hip_extension
    ('105b3b17-2787-5525-9efd-3465abeb652b'::uuid, 'Glúteos/lever_standing_hip_extension.mp4', 'Glúteos/lever_standing_hip_extension.webp'),   -- lever_standing_hip_extension
    ('f0157a61-a9a4-54b2-861e-97431160382f'::uuid, 'Funcional e HIT/bench_quadruped_hip_extension.mp4', 'Funcional e HIT/bench_quadruped_hip_extension.webp'),   -- bench_quadruped_hip_extension
    ('a2c3400c-c468-5b0c-a82f-c0c972342f23'::uuid, 'Funcional e HIT/gymstick_overhead_triceps_extension.mp4', 'Funcional e HIT/gymstick_overhead_triceps_extension.webp'),   -- gymstick_overhead_triceps_extension
    ('5b66354a-9676-549d-a2cd-89ee16c02596'::uuid, 'Tríceps/cable_lying_triceps_extension_bar.mp4', 'Tríceps/cable_lying_triceps_extension_bar.webp'),   -- cable_lying_triceps_extension_bar
    ('f950b1d6-4483-5cab-8fab-9604e1205af2'::uuid, 'Tríceps/cable_one_arm_reverse_grip_pushdown.mp4', 'Tríceps/cable_one_arm_reverse_grip_pushdown.webp'),   -- cable_one_arm_reverse_grip_pushdown
    ('e9931efe-70f5-5c87-80a0-23c4ea4aedbf'::uuid, 'Tríceps/decline_close_grip_barbell_skull_crusher.mp4', 'Tríceps/decline_close_grip_barbell_skull_crusher.webp'),   -- decline_close_grip_barbell_skull_crusher
    ('ed1a43b1-0fb7-587c-ab76-a1855630dfab'::uuid, 'Tríceps/kneeling_cable_overhead_triceps_extension.mp4', 'Tríceps/kneeling_cable_overhead_triceps_extension.webp'),   -- kneeling_cable_overhead_triceps_extension
    ('c5929a6c-04f9-53ff-af0c-e388f8e92b85'::uuid, 'Tríceps/cross_cable_triceps_extension.mp4', 'Tríceps/cross_cable_triceps_extension.webp'),   -- cross_cable_triceps_extension
    ('8b12a521-b3b0-574a-a4aa-daa1d6ca9855'::uuid, 'Funcional e HIT/band_triceps_pushdown.mp4', 'Funcional e HIT/band_triceps_pushdown.webp'),   -- band_triceps_pushdown
    ('96c6a3ec-64ef-5e6f-b6ab-d0c6510eb575'::uuid, 'Funcional e HIT/band_triceps_pushdown_rack.mp4', 'Funcional e HIT/band_triceps_pushdown_rack.webp'),   -- band_triceps_pushdown_rack
    ('6c4b391b-5694-59b5-bbb1-04c16d5215e6'::uuid, 'Tríceps/high_cable_one_arm_overhead_extension.mp4', 'Tríceps/high_cable_one_arm_overhead_extension.webp'),   -- high_cable_one_arm_overhead_extension
    ('09f69d8e-f699-502e-a997-19c7dc2b34f3'::uuid, 'Tríceps/ez_bar_close_grip_behind_the_head_extension.mp4', 'Tríceps/ez_bar_close_grip_behind_the_head_extension.webp'),   -- ez_bar_close_grip_behind_the_head_extension
    ('d6ec6001-1e33-5851-8aea-60129366fce2'::uuid, 'Tríceps/cable_rope_lying_triceps_extension.mp4', 'Tríceps/cable_rope_lying_triceps_extension.webp'),   -- cable_rope_lying_triceps_extension
    ('2a7d23a9-a458-5483-aec6-883fe9c1f41c'::uuid, 'Funcional e HIT/band_shoulder_extension.mp4', 'Funcional e HIT/band_shoulder_extension.webp'),   -- band_shoulder_extension
    ('3035bda6-707e-59da-add1-ef76b84d85b2'::uuid, 'Calistenia/bodyweight_floor_triceps_extension.mp4', 'Calistenia/bodyweight_floor_triceps_extension.webp'),   -- bodyweight_floor_triceps_extension
    ('37120db5-a15e-586f-a983-96e7473ea6e8'::uuid, 'Tríceps/incline_ez_bar_skull_crusher.mp4', 'Tríceps/incline_ez_bar_skull_crusher.webp'),   -- incline_ez_bar_skull_crusher
    ('c1d69d79-c391-5051-b220-72cafeba0197'::uuid, 'Tríceps/barbell_lying_behind_the_head_extension.mp4', 'Tríceps/barbell_lying_behind_the_head_extension.webp'),   -- barbell_lying_behind_the_head_extension
    ('768a5661-18a6-5594-b331-e2ea60e1f667'::uuid, 'Tríceps/standing_barbell_overhead_extension.mp4', 'Tríceps/standing_barbell_overhead_extension.webp'),   -- standing_barbell_overhead_extension
    ('e07177df-b160-5d23-a2a0-0ce305023025'::uuid, 'Tríceps/kneeling_bench_cable_triceps_extension.mp4', 'Tríceps/kneeling_bench_cable_triceps_extension.webp'),   -- kneeling_bench_cable_triceps_extension
    ('f51c8033-16ac-56da-a0f9-28e93c359932'::uuid, 'Tríceps/incline_cable_overhead_extension.mp4', 'Tríceps/incline_cable_overhead_extension.webp'),   -- incline_cable_overhead_extension
    ('ae4b315d-67f0-5da1-b3bc-90aa3a0f5916'::uuid, 'Tríceps/cable_horizontal_triceps_extension.mp4', 'Tríceps/cable_horizontal_triceps_extension.webp'),   -- cable_horizontal_triceps_extension
    ('58cc2a3e-0967-516d-96c5-3d0b25d421d1'::uuid, 'Funcional e HIT/band_horizontal_triceps_extension.mp4', 'Funcional e HIT/band_horizontal_triceps_extension.webp'),   -- band_horizontal_triceps_extension
    ('978a0c86-b093-53bb-8569-5069b344b14c'::uuid, 'Tríceps/one_arm_pronated_dumbbell_skull_crusher.mp4', 'Tríceps/one_arm_pronated_dumbbell_skull_crusher.webp'),   -- one_arm_pronated_dumbbell_skull_crusher
    ('226ad443-4f53-519b-98df-575ad6750c32'::uuid, 'Tríceps/seated_one_arm_dumbbell_overhead_extension.mp4', 'Tríceps/seated_one_arm_dumbbell_overhead_extension.webp'),   -- seated_one_arm_dumbbell_overhead_extension
    ('d91a8299-c3d4-5b33-b288-7fcd2aa21ad5'::uuid, 'Tríceps/cable_one_arm_pushdown.mp4', 'Tríceps/cable_one_arm_pushdown.webp'),   -- cable_one_arm_pushdown
    ('1c9f2ff4-b2ef-5467-b21c-3d7a896bc3c8'::uuid, 'Tríceps/cable_cross_body_triceps_extension.mp4', 'Tríceps/cable_cross_body_triceps_extension.webp'),   -- cable_cross_body_triceps_extension
    ('7f63f1a5-a7d1-5f0e-ae80-6bd74d793a3a'::uuid, 'Tríceps/machine_triceps_extension.mp4', 'Tríceps/machine_triceps_extension.webp'),   -- machine_triceps_extension
    ('0189435d-5659-5071-8443-29e8995365c3'::uuid, 'Tríceps/machine_neutral_grip_triceps_extension.mp4', 'Tríceps/machine_neutral_grip_triceps_extension.webp'),   -- machine_neutral_grip_triceps_extension
    ('36ff6891-1948-50c8-9b05-1edf44a41d83'::uuid, 'Tríceps/high_cable_standing_overhead_extension.mp4', 'Tríceps/high_cable_standing_overhead_extension.webp'),   -- high_cable_standing_overhead_extension
    ('08ccd287-ea6b-58df-bb66-270b3a244337'::uuid, 'Tríceps/cable_lying_skull_crusher.mp4', 'Tríceps/cable_lying_skull_crusher.webp'),   -- cable_lying_skull_crusher
    ('245d2c7d-d3c7-5868-adc9-18d38d8a261b'::uuid, 'Eretor Lombar/seated_machine_back_extension.mp4', 'Eretor Lombar/seated_machine_back_extension.webp'),   -- seated_machine_back_extension
    ('f22c5811-3905-5480-be0c-b9e26150410e'::uuid, 'Trapézio/face_pull.mp4', 'Trapézio/face_pull.webp'),   -- face_pull
    ('63e8a9d4-1d0c-5ec9-9000-286cbe67a7c0'::uuid, 'Calistenia/push_up.mp4', 'Calistenia/push_up.webp'),   -- push_up
    ('6aba4098-93c1-5f1d-afa1-65db141b23a6'::uuid, 'Calistenia/decline_push_up.mp4', 'Calistenia/decline_push_up.webp'),   -- decline_push_up
    ('7ee5dba4-8b7c-5197-9cf1-8a1c2ed78672'::uuid, 'Calistenia/medicine_ball_close_grip_push_up.mp4', 'Calistenia/medicine_ball_close_grip_push_up.webp'),   -- medicine_ball_close_grip_push_up
    ('c8dc6761-5ac4-5df8-a160-3202dc830b59'::uuid, 'Mobilidade/alternating_wall_shoulder_flexion.mp4', 'Mobilidade/alternating_wall_shoulder_flexion.webp'),   -- alternating_wall_shoulder_flexion
    ('cfe572d5-2e8a-5d02-bb7d-5f18afeb8702'::uuid, 'Funcional e HIT/cobra_push_up.mp4', 'Funcional e HIT/cobra_push_up.webp'),   -- cobra_push_up
    ('69028342-cbba-559f-b4b8-4ab7149d2691'::uuid, 'Calistenia/crossed_hands_push_up.mp4', 'Calistenia/crossed_hands_push_up.webp'),   -- crossed_hands_push_up
    ('ceea8ca8-e5ed-58d5-9e0e-c57f8973354b'::uuid, 'Funcional e HIT/t_push_up.mp4', 'Funcional e HIT/t_push_up.webp'),   -- t_push_up
    ('1dc76341-e5fd-57a9-814e-e1a49381afa0'::uuid, 'Calistenia/chest_tap_push_up.mp4', 'Calistenia/chest_tap_push_up.webp'),   -- chest_tap_push_up
    ('a661fe92-7fb0-5c5d-b995-46b3027a3511'::uuid, 'Calistenia/push_up_to_toe_touch.mp4', 'Calistenia/push_up_to_toe_touch.webp'),   -- push_up_to_toe_touch
    ('8ffbfb06-78ce-53fc-914e-8e4aed76cba5'::uuid, 'Calistenia/push_up_bars_push_up.mp4', 'Calistenia/push_up_bars_push_up.webp'),   -- push_up_bars_push_up
    ('8a78bbe6-c653-5fd0-a231-69b67c36b12b'::uuid, 'Calistenia/kettlebell_deep_push_up.mp4', 'Calistenia/kettlebell_deep_push_up.webp'),   -- kettlebell_deep_push_up
    ('c76a32d7-a4f9-5c7c-bf99-afa0f6386d0e'::uuid, 'Calistenia/handstand_push_up.mp4', 'Calistenia/handstand_push_up.webp'),   -- handstand_push_up
    ('5b5a1b3c-ac3d-5c35-83ad-415c2aaa07d7'::uuid, 'Calistenia/weighted_push_up.mp4', 'Calistenia/weighted_push_up.webp'),   -- weighted_push_up
    ('8d1096a7-eadb-54e1-889c-4bcb7d13db6a'::uuid, 'Calistenia/one_arm_push_up.mp4', 'Calistenia/one_arm_push_up.webp'),   -- one_arm_push_up
    ('2b30b947-b1d1-514e-baf1-a0c426c39494'::uuid, 'Calistenia/stability_ball_decline_push_up.mp4', 'Calistenia/stability_ball_decline_push_up.webp'),   -- stability_ball_decline_push_up
    ('e0865f54-9509-550a-ad08-b1259ac62c76'::uuid, 'Calistenia/dive_bomber_push_up.mp4', 'Calistenia/dive_bomber_push_up.webp'),   -- dive_bomber_push_up
    ('e0946adb-91c3-5643-9dd7-5e9159c6001f'::uuid, 'Calistenia/medicine_ball_one_hand_push_up.mp4', 'Calistenia/medicine_ball_one_hand_push_up.webp'),   -- medicine_ball_one_hand_push_up
    ('aac961ca-6744-5836-973e-0672cf80a4ae'::uuid, 'Calistenia/stability_ball_push_up.mp4', 'Calistenia/stability_ball_push_up.webp'),   -- stability_ball_push_up
    ('4f0e9cc8-696f-5c44-8803-0d01d0044347'::uuid, 'Calistenia/single_leg_push_up.mp4', 'Calistenia/single_leg_push_up.webp'),   -- single_leg_push_up
    ('b032002d-2256-5713-aca8-af10c1dccc6a'::uuid, 'Calistenia/knee_push_up.mp4', 'Calistenia/knee_push_up.webp'),   -- knee_push_up
    ('40f31393-5288-520d-a50d-24c39ef2d57c'::uuid, 'Calistenia/close_grip_wall_push_up.mp4', 'Calistenia/close_grip_wall_push_up.webp'),   -- close_grip_wall_push_up
    ('bfdd5742-dba3-5719-9610-4fa5dafb184f'::uuid, 'Funcional e HIT/bosu_push_up.mp4', 'Funcional e HIT/bosu_push_up.webp'),   -- bosu_push_up
    ('7234144b-110e-56b6-9062-5f4dafa66da7'::uuid, 'Calistenia/close_grip_knee_push_up.mp4', 'Calistenia/close_grip_knee_push_up.webp'),   -- close_grip_knee_push_up
    ('dac08716-7146-5f62-91c3-19a1437b7cdc'::uuid, 'Calistenia/shoulder_tap_push_up.mp4', 'Calistenia/shoulder_tap_push_up.webp'),   -- shoulder_tap_push_up
    ('3155d874-3a4f-50b1-b55b-4de5ba1366c0'::uuid, 'Calistenia/bodyweight_bar_triceps_extension.mp4', 'Calistenia/bodyweight_bar_triceps_extension.webp'),   -- bodyweight_bar_triceps_extension
    ('882033da-4365-5de7-b84e-337a9eb1cd36'::uuid, 'Calistenia/fingertip_push_up.mp4', 'Calistenia/fingertip_push_up.webp'),   -- fingertip_push_up
    ('ca744fa1-fffb-565e-bcd0-585b5595e00e'::uuid, 'Calistenia/trx_push_up.mp4', 'Calistenia/trx_push_up.webp'),   -- trx_push_up
    ('5b4df4af-90eb-55df-b89d-4ea9067d978e'::uuid, 'Pernas/standing_single_leg_curl_machine.mp4', 'Pernas/standing_single_leg_curl_machine.webp'),   -- standing_single_leg_curl_machine
    ('067dbe0b-8747-5065-8f09-4462367afdd7'::uuid, 'Pernas/dumbbell_lying_leg_curl.mp4', 'Pernas/dumbbell_lying_leg_curl.webp'),   -- dumbbell_lying_leg_curl
    ('c668a725-e04f-5057-af25-6313264f4bb4'::uuid, 'Funcional e HIT/standing_band_leg_curl.mp4', 'Funcional e HIT/standing_band_leg_curl.webp'),   -- standing_band_leg_curl
    ('27715ece-d827-53f5-a74e-e471e2097dd2'::uuid, 'Pernas/decline_dumbbell_leg_curl.mp4', 'Pernas/decline_dumbbell_leg_curl.webp'),   -- decline_dumbbell_leg_curl
    ('7c8860ca-98ce-5537-b8e6-46784fc116f9'::uuid, 'Funcional e HIT/lying_band_leg_curl.mp4', 'Funcional e HIT/lying_band_leg_curl.webp'),   -- lying_band_leg_curl
    ('cffea506-65e7-5c4c-906f-f21e85a165b2'::uuid, 'Funcional e HIT/stability_ball_leg_curl.mp4', 'Funcional e HIT/stability_ball_leg_curl.webp'),   -- stability_ball_leg_curl
    ('f9b4cfcf-d75e-501f-9c44-899cf394f0b7'::uuid, 'Antebraços/seated_dumbbell_neutral_wrist_curl.mp4', 'Antebraços/seated_dumbbell_neutral_wrist_curl.webp'),   -- seated_dumbbell_neutral_wrist_curl
    ('8716e30f-596f-58c8-87ab-988b6992d9b7'::uuid, 'Calistenia/knuckle_push_up.mp4', 'Calistenia/knuckle_push_up.webp'),   -- knuckle_push_up
    ('6849665e-f2f8-5b7a-8c25-b91d5788ee67'::uuid, 'Antebraços/weight_plate_reverse_wrist_curl.mp4', 'Antebraços/weight_plate_reverse_wrist_curl.webp'),   -- weight_plate_reverse_wrist_curl
    ('49e50355-8d22-54dc-a977-7b144018228d'::uuid, 'Antebraços/barbell_reverse_wrist_curl_over_bench.mp4', 'Antebraços/barbell_reverse_wrist_curl_over_bench.webp'),   -- barbell_reverse_wrist_curl_over_bench
    ('b6110705-4ad4-5fec-9d1f-856babc28df5'::uuid, 'Antebraços/cable_kneeling_one_arm_wrist_curl.mp4', 'Antebraços/cable_kneeling_one_arm_wrist_curl.webp'),   -- cable_kneeling_one_arm_wrist_curl
    ('6492b12e-158a-5540-8a1e-311fa662e14e'::uuid, 'Antebraços/dumbbell_wrist_curl.mp4', 'Antebraços/dumbbell_wrist_curl.webp'),   -- dumbbell_wrist_curl
    ('572d29dd-aa3a-5ba7-800b-d6889105f8e5'::uuid, 'Calistenia/drop_push_up.mp4', 'Calistenia/drop_push_up.webp'),   -- drop_push_up
    ('9bd84648-0d26-5f21-a324-9fe63c224c23'::uuid, 'Tríceps/seated_dip_machine.mp4', 'Tríceps/seated_dip_machine.webp'),   -- seated_dip_machine
    ('53ac7b9b-1fe7-5359-a7d5-a67dc82f3563'::uuid, 'Peitoral/push_up_with_arm_raise.mp4', 'Peitoral/push_up_with_arm_raise.webp'),   -- push_up_with_arm_raise
    ('c5b606ef-244c-5d86-8cf8-c58ab71d4852'::uuid, 'Calistenia/scapular_push_up.mp4', 'Calistenia/scapular_push_up.webp'),   -- scapular_push_up
    ('94029991-9dbb-5d17-8a6d-a8cdf9e70f1f'::uuid, 'Crossfit/deficit_handstand_push_up.mp4', 'Crossfit/deficit_handstand_push_up.webp'),   -- deficit_handstand_push_up
    ('1df89859-881c-51ae-94eb-f9986f7320dc'::uuid, 'Calistenia/clap_push_up.mp4', 'Calistenia/clap_push_up.webp'),   -- clap_push_up
    ('8c3c4e4a-12b0-5451-894c-8b775fd5fa7f'::uuid, 'Crossfit/kipping_handstand_push_up.mp4', 'Crossfit/kipping_handstand_push_up.webp'),   -- kipping_handstand_push_up
    ('c0105361-8bb4-5642-9f37-b779348ef69d'::uuid, 'Calistenia/knee_diamond_push_up.mp4', 'Calistenia/knee_diamond_push_up.webp'),   -- knee_diamond_push_up
    ('88d1911e-7f80-551e-bd2f-9f6b960d3d9a'::uuid, 'Funcional e HIT/band_shoulder_flexion.mp4', 'Funcional e HIT/band_shoulder_flexion.webp'),   -- band_shoulder_flexion
    ('e7fae1b6-6564-5f24-a070-bf9a264fbab6'::uuid, 'Pernas/towel_sliding_leg_curl.mp4', 'Pernas/towel_sliding_leg_curl.webp'),   -- towel_sliding_leg_curl
    ('4b17ccd9-4608-5d93-818d-46441178f2d8'::uuid, 'Calistenia/feet_elevated_pike_push_up.mp4', 'Calistenia/feet_elevated_pike_push_up.webp'),   -- feet_elevated_pike_push_up
    ('5ca6afd5-de8b-5391-ba24-d2046670a10a'::uuid, 'Calistenia/deficit_pike_push_up_between_chairs.mp4', 'Calistenia/deficit_pike_push_up_between_chairs.webp'),   -- deficit_pike_push_up_between_chairs
    ('f4efc717-6cc2-5188-8913-7f523768c557'::uuid, 'Calistenia/bench_assisted_one_arm_push_up.mp4', 'Calistenia/bench_assisted_one_arm_push_up.webp'),   -- bench_assisted_one_arm_push_up
    ('24f94479-4238-541f-a9a1-0229e48d022a'::uuid, 'Calistenia/medicine_ball_one_arm_push_up.mp4', 'Calistenia/medicine_ball_one_arm_push_up.webp'),   -- medicine_ball_one_arm_push_up
    ('ff4c217c-3d01-59ec-8646-d7a92ab388a9'::uuid, 'Calistenia/diamond_push_up.mp4', 'Calistenia/diamond_push_up.webp'),   -- diamond_push_up
    ('5b6ffeb8-e4d8-5e63-84cd-9c467ab343f5'::uuid, 'Calistenia/pike_push_up.mp4', 'Calistenia/pike_push_up.webp'),   -- pike_push_up
    ('110bdcba-ad42-5434-97c6-7f32f34dc0ca'::uuid, 'Calistenia/modified_hindu_push_up.mp4', 'Calistenia/modified_hindu_push_up.webp'),   -- modified_hindu_push_up
    ('323db88b-1918-5e1a-8aa3-747c0fce6b0b'::uuid, 'Calistenia/incline_push_up.mp4', 'Calistenia/incline_push_up.webp'),   -- incline_push_up
    ('47ed255b-cca9-5dcf-943a-0f93df04c888'::uuid, 'Calistenia/wall_push_up.mp4', 'Calistenia/wall_push_up.webp'),   -- wall_push_up
    ('db17b9b6-6425-5929-babe-ba61b3c40eca'::uuid, 'Pernas/nordic_hamstring_curl.mp4', 'Pernas/nordic_hamstring_curl.webp'),   -- nordic_hamstring_curl
    ('4d304500-f1bf-5a6a-8a8b-884aeaad1004'::uuid, 'Calistenia/push_up_plus.mp4', 'Calistenia/push_up_plus.webp'),   -- push_up_plus
    ('3de45d8f-69f2-5057-951c-441ee74fa554'::uuid, 'Calistenia/reverse_elbow_push_up.mp4', 'Calistenia/reverse_elbow_push_up.webp'),   -- reverse_elbow_push_up
    ('3bcdeb8b-d0b6-5067-8753-30c3ee972fc2'::uuid, 'Calistenia/wall_handstand_push_up.mp4', 'Calistenia/wall_handstand_push_up.webp'),   -- wall_handstand_push_up
    ('025aae56-8944-5e58-adb0-7bbbb2c83572'::uuid, 'Calistenia/hindu_push_up.mp4', 'Calistenia/hindu_push_up.webp'),   -- hindu_push_up
    ('961eddba-8210-5002-8258-dd485f16f8e1'::uuid, 'Funcional e HIT/right_hook.mp4', 'Funcional e HIT/right_hook.webp'),   -- right_hook
    ('2677ea1e-3957-5468-b00c-18246fb328f2'::uuid, 'Glúteos/leg_extension_machine_glute_kickback.mp4', 'Glúteos/leg_extension_machine_glute_kickback.webp'),   -- leg_extension_machine_glute_kickback
    ('e3c7aece-5327-5b35-a4ce-fb9eace3e5a0'::uuid, 'Glúteos/smith_machine_glute_kickback.mp4', 'Glúteos/smith_machine_glute_kickback.webp'),   -- smith_machine_glute_kickback
    ('0e6cec84-50c6-5229-a2eb-74e5921a579e'::uuid, 'Funcional e HIT/gymstick_glute_kickback.mp4', 'Funcional e HIT/gymstick_glute_kickback.webp'),   -- gymstick_glute_kickback
    ('976a52bd-24ee-5746-a50d-6bff5dbeaeca'::uuid, 'Funcional e HIT/band_bent_leg_glute_kickback.mp4', 'Funcional e HIT/band_bent_leg_glute_kickback.webp'),   -- band_bent_leg_glute_kickback
    ('46ec270e-1dc6-5172-aed2-bed7d5addf18'::uuid, 'Funcional e HIT/standing_band_glute_kickback.mp4', 'Funcional e HIT/standing_band_glute_kickback.webp'),   -- standing_band_glute_kickback
    ('50be13bb-03bb-56d3-a8f1-4a8cf068de4a'::uuid, 'Glúteos/multi_hip_machine_glute_kickback.mp4', 'Glúteos/multi_hip_machine_glute_kickback.webp'),   -- multi_hip_machine_glute_kickback
    ('c154addc-6ca3-5cf4-b630-f78bf9004969'::uuid, 'Glúteos/cable_bent_over_glute_kickback.mp4', 'Glúteos/cable_bent_over_glute_kickback.webp'),   -- cable_bent_over_glute_kickback
    ('9d2efb07-c81a-544c-a639-eb2fc050bbbb'::uuid, 'Funcional e HIT/quadruped_band_glute_kickback.mp4', 'Funcional e HIT/quadruped_band_glute_kickback.webp'),   -- quadruped_band_glute_kickback
    ('1ff60154-1068-5bbd-81f3-77ed6482dd1f'::uuid, 'Costas/machine_assisted_pull_up.mp4', 'Costas/machine_assisted_pull_up.webp'),   -- machine_assisted_pull_up
    ('3a17152c-357f-59f2-9b9f-5820707584aa'::uuid, 'Antebraços/hand_gripper.mp4', 'Antebraços/hand_gripper.webp'),   -- hand_gripper
    ('3f83b50b-811a-56b9-b663-ce8dc41f73e2'::uuid, 'Cardio/arm_ergometer.mp4', 'Cardio/arm_ergometer.webp'),   -- arm_ergometer
    ('651bc488-c5bc-576d-b555-13bf2597e4ce'::uuid, 'Crossfit/heaving_snatch_balance.mp4', 'Crossfit/heaving_snatch_balance.webp'),   -- heaving_snatch_balance
    ('2a0a3579-f68f-590e-a0b1-37d840b7df2b'::uuid, 'Eretor Lombar/back_extension_hyperextension.mp4', 'Eretor Lombar/back_extension_hyperextension.webp'),   -- back_extension_hyperextension
    ('ef0b4d49-b7d2-5c98-a43c-0960f6c033a3'::uuid, 'Eretor Lombar/frog_reverse_hyperextension.mp4', 'Eretor Lombar/frog_reverse_hyperextension.webp'),   -- frog_reverse_hyperextension
    ('617b4438-d7e9-5999-925a-69247a77a9e5'::uuid, 'Funcional e HIT/band_reverse_hyperextension.mp4', 'Funcional e HIT/band_reverse_hyperextension.webp'),   -- band_reverse_hyperextension
    ('cc4a492f-dbbd-5250-ac38-056a183efdee'::uuid, 'Eretor Lombar/twisting_back_extension.mp4', 'Eretor Lombar/twisting_back_extension.webp'),   -- twisting_back_extension
    ('8b0e4504-9447-5b1e-84d7-a132ebdfbd8a'::uuid, 'Eretor Lombar/floor_back_extension.mp4', 'Eretor Lombar/floor_back_extension.webp'),   -- floor_back_extension
    ('1d56027a-5495-52a3-a45b-fb405b967370'::uuid, 'Calistenia/impossible_dip.mp4', 'Calistenia/impossible_dip.webp'),   -- impossible_dip
    ('db5d1de5-a818-5618-9294-ff4b500bf033'::uuid, 'Crossfit/barbell_thruster.mp4', 'Crossfit/barbell_thruster.webp'),   -- barbell_thruster
    ('3d2ceac0-3429-5524-958c-d8d860359a20'::uuid, 'Mobilidade/side_bend.mp4', 'Mobilidade/side_bend.webp'),   -- side_bend
    ('590b871a-31f1-5674-aba3-16e9d720c8cf'::uuid, 'Mobilidade/standing_overhead_side_bend.mp4', 'Mobilidade/standing_overhead_side_bend.webp'),   -- standing_overhead_side_bend
    ('04a9298f-169a-575b-ae78-70bc7c836ae5'::uuid, 'Funcional e HIT/pelvic_tilt.mp4', 'Funcional e HIT/pelvic_tilt.webp'),   -- pelvic_tilt
    ('ebae7f5e-504d-5ce4-ba4e-89a457d89f96'::uuid, 'Mobilidade/lying_alternating_knee_to_chest.mp4', 'Mobilidade/lying_alternating_knee_to_chest.webp'),   -- lying_alternating_knee_to_chest
    ('f2a5d535-79c5-5709-8803-1f3a9c62eedb'::uuid, 'Funcional e HIT/wall_high_knees.mp4', 'Funcional e HIT/wall_high_knees.webp'),   -- wall_high_knees
    ('85267546-045c-59ac-bd1c-5d60b2df155e'::uuid, 'Crossfit/kettlebell_hang_clean.mp4', 'Crossfit/kettlebell_hang_clean.webp'),   -- kettlebell_hang_clean
    ('b0eea968-2d00-5550-8c5f-bf9d08f8683c'::uuid, 'Crossfit/kettlebell_figure_8.mp4', 'Crossfit/kettlebell_figure_8.webp'),   -- kettlebell_figure_8
    ('b666d09c-a3f3-535b-b648-702df1110377'::uuid, 'Funcional e HIT/lying_medicine_ball_chest_throw.mp4', 'Funcional e HIT/lying_medicine_ball_chest_throw.webp'),   -- lying_medicine_ball_chest_throw
    ('c690086d-745a-5625-9316-5c2523075873'::uuid, 'Funcional e HIT/overhead_medicine_ball_throw.mp4', 'Funcional e HIT/overhead_medicine_ball_throw.webp'),   -- overhead_medicine_ball_throw
    ('2f9e2c9d-15cb-5fde-b4a5-59d961d0e55b'::uuid, 'Pernas/45_leg_press.mp4', 'Pernas/45_leg_press.webp'),   -- 45_leg_press
    ('7fe495a6-67b1-567e-b48f-8bba29042acf'::uuid, 'Funcional e HIT/gymstick_lying_alternating_leg_press.mp4', 'Funcional e HIT/gymstick_lying_alternating_leg_press.webp'),   -- gymstick_lying_alternating_leg_press
    ('154cf991-c884-5730-a2d6-6a6bd7a8df7f'::uuid, 'Pernas/seated_leg_press.mp4', 'Pernas/seated_leg_press.webp'),   -- seated_leg_press
    ('8c59374a-0f9a-5a64-9dc7-635d6d9c2023'::uuid, 'Pernas/single_leg_leg_press.mp4', 'Pernas/single_leg_leg_press.webp'),   -- single_leg_leg_press
    ('fe804c1b-c9cd-5932-bb7d-d5c075285b7f'::uuid, 'Pernas/smith_machine_90_leg_press.mp4', 'Pernas/smith_machine_90_leg_press.webp'),   -- smith_machine_90_leg_press
    ('abb5628a-094d-5544-a677-714b65e185a3'::uuid, 'Pernas/machine_assisted_single_leg_step_up.mp4', 'Pernas/machine_assisted_single_leg_step_up.webp'),   -- machine_assisted_single_leg_step_up
    ('6d35203d-bad1-5a4c-b4c0-3a921ef63526'::uuid, 'Funcional e HIT/fire_hydrant.mp4', 'Funcional e HIT/fire_hydrant.webp'),   -- fire_hydrant
    ('37737670-9d4a-5f51-82c0-c83c0a7120a9'::uuid, 'Pernas/barbell_sumo_deadlift.mp4', 'Pernas/barbell_sumo_deadlift.webp'),   -- barbell_sumo_deadlift
    ('c3ac8e18-2119-503d-8fe8-0c740bb5e288'::uuid, 'Pernas/dumbbell_sumo_deadlift.mp4', 'Pernas/dumbbell_sumo_deadlift.webp'),   -- dumbbell_sumo_deadlift
    ('ae2877e2-50e0-5ced-97f7-7d5a106deaeb'::uuid, 'Calistenia/bodyweight_single_leg_deadlift.mp4', 'Calistenia/bodyweight_single_leg_deadlift.webp'),   -- bodyweight_single_leg_deadlift
    ('fa79d9e9-5c35-5806-b1d7-de146cc6d4ee'::uuid, 'Pernas/zercher_deadlift.mp4', 'Pernas/zercher_deadlift.webp'),   -- zercher_deadlift
    ('58636083-2d1f-5e97-a88b-f89db8896460'::uuid, 'Pernas/trap_bar_deadlift.mp4', 'Pernas/trap_bar_deadlift.webp'),   -- trap_bar_deadlift
    ('e980b4d8-5e81-5d24-a734-1c2dcc98c5e1'::uuid, 'Pernas/landmine_deadlift.mp4', 'Pernas/landmine_deadlift.webp'),   -- landmine_deadlift
    ('2a034994-0472-5043-b930-e49f6fbd6d39'::uuid, 'Pernas/dumbbell_deadlift.mp4', 'Pernas/dumbbell_deadlift.webp'),   -- dumbbell_deadlift
    ('8b3e088c-8795-5ef0-afe2-b8865aa421fa'::uuid, 'Pernas/kettlebell_deadlift.mp4', 'Pernas/kettlebell_deadlift.webp'),   -- kettlebell_deadlift
    ('145bd851-5bba-5139-999e-a380a689b3fa'::uuid, 'Crossfit/turkish_get_up.mp4', 'Crossfit/turkish_get_up.webp'),   -- turkish_get_up
    ('61b54728-c357-5477-8266-b7bf941d737a'::uuid, 'Pernas/rack_pull.mp4', 'Pernas/rack_pull.webp'),   -- rack_pull
    ('7fdb01f1-76a6-584d-b18c-730c46f4f92e'::uuid, 'Mobilidade/wall_slide.mp4', 'Mobilidade/wall_slide.webp'),   -- wall_slide
    ('7ad4e842-48be-5d1a-9b0c-21a333ae6f94'::uuid, 'Ombros/dumbbell_3_way_raise.mp4', 'Ombros/dumbbell_3_way_raise.webp'),   -- dumbbell_3_way_raise
    ('bf2c4d46-3fee-5d25-8b85-79c701b60af5'::uuid, 'Panturrilhas/bench_supported_donkey_calf_raise.mp4', 'Panturrilhas/bench_supported_donkey_calf_raise.webp'),   -- bench_supported_donkey_calf_raise
    ('bbeeed3f-3024-5c75-a1df-e517464d275d'::uuid, 'Panturrilhas/single_leg_donkey_calf_raise.mp4', 'Panturrilhas/single_leg_donkey_calf_raise.webp'),   -- single_leg_donkey_calf_raise
    ('b4261b30-964d-5d32-869c-a8be976b33dd'::uuid, 'Calistenia/partner_donkey_calf_raise.mp4', 'Calistenia/partner_donkey_calf_raise.webp'),   -- partner_donkey_calf_raise
    ('f7eeca9e-8914-5ffd-a848-e571a07ca6f5'::uuid, 'Panturrilhas/lever_donkey_calf_raise.mp4', 'Panturrilhas/lever_donkey_calf_raise.webp'),   -- lever_donkey_calf_raise
    ('806f4b16-6de9-526b-9ab9-04112303795d'::uuid, 'Ombros/seated_dumbbell_alternating_front_raise.mp4', 'Ombros/seated_dumbbell_alternating_front_raise.webp'),   -- seated_dumbbell_alternating_front_raise
    ('7d71e4e1-4b6f-59f7-9f7f-bc7249e7fa15'::uuid, 'Ombros/weight_plate_front_raise.mp4', 'Ombros/weight_plate_front_raise.webp'),   -- weight_plate_front_raise
    ('00e9137d-f835-523d-9e6e-80f7bc09e475'::uuid, 'Ombros/landmine_front_raise.mp4', 'Ombros/landmine_front_raise.webp'),   -- landmine_front_raise
    ('e4671d83-da5b-5750-a0d7-c57f6ffb02b1'::uuid, 'Ombros/cable_two_arm_front_raise.mp4', 'Ombros/cable_two_arm_front_raise.webp'),   -- cable_two_arm_front_raise
    ('76f3ea6c-14af-5b5d-8a5b-63c8cf06ee38'::uuid, 'Ombros/cable_one_arm_front_raise.mp4', 'Ombros/cable_one_arm_front_raise.webp'),   -- cable_one_arm_front_raise
    ('a509bd20-a5c3-59bb-9a81-69e91442eb34'::uuid, 'Crossfit/kettlebell_lateral_raise.mp4', 'Crossfit/kettlebell_lateral_raise.webp'),   -- kettlebell_lateral_raise
    ('de7ac1d0-a94e-54d3-8d08-5e7ac326ad6f'::uuid, 'Pernas/dumbbell_romanian_deadlift.mp4', 'Pernas/dumbbell_romanian_deadlift.webp'),   -- dumbbell_romanian_deadlift
    ('836df378-c671-5ece-a7e0-60f3203d408e'::uuid, 'Funcional e HIT/medicine_ball_rotational_throw.mp4', 'Funcional e HIT/medicine_ball_rotational_throw.webp'),   -- medicine_ball_rotational_throw
    ('88d05297-4c36-56b7-bd03-348aa5b5252f'::uuid, 'Trapézio/half_kneeling_cable_face_pull.mp4', 'Trapézio/half_kneeling_cable_face_pull.webp'),   -- half_kneeling_cable_face_pull
    ('832fe86f-064b-5f46-b689-f2f01dc2188c'::uuid, 'Calistenia/korean_dip.mp4', 'Calistenia/korean_dip.webp'),   -- korean_dip
    ('7742b513-dc03-5294-a3a3-62cd5c473bcd'::uuid, 'Peitoral/machine_assisted_chest_dip.mp4', 'Peitoral/machine_assisted_chest_dip.webp'),   -- machine_assisted_chest_dip
    ('942f067b-5e83-5374-8c4d-94f7931a6fb9'::uuid, 'Calistenia/parallel_bar_triceps_dip.mp4', 'Calistenia/parallel_bar_triceps_dip.webp'),   -- parallel_bar_triceps_dip
    ('eb30a555-f57d-5a1d-bcd0-527e03fab772'::uuid, 'Tríceps/lever_triceps_dip.mp4', 'Tríceps/lever_triceps_dip.webp'),   -- lever_triceps_dip
    ('18835109-4f9f-539a-b6da-9474ea00a637'::uuid, 'Tríceps/assisted_triceps_dip.mp4', 'Tríceps/assisted_triceps_dip.webp'),   -- assisted_triceps_dip
    ('1328d160-cb37-5844-9588-486eabb189ae'::uuid, 'Calistenia/floor_triceps_dip.mp4', 'Calistenia/floor_triceps_dip.webp'),   -- floor_triceps_dip
    ('6bd109c3-d403-5cff-ab75-bb96798e64e6'::uuid, 'Pernas/single_leg_lying_leg_curl.mp4', 'Pernas/single_leg_lying_leg_curl.webp'),   -- single_leg_lying_leg_curl
    ('58b85b43-d4cc-5829-b144-493787ef4620'::uuid, 'Pernas/lying_leg_curl.mp4', 'Pernas/lying_leg_curl.webp'),   -- lying_leg_curl
    ('5d702573-30c3-5fb7-84cf-a8d14ea07ef9'::uuid, 'Funcional e HIT/inchworm.mp4', 'Funcional e HIT/inchworm.webp'),   -- inchworm
    ('a8c49aef-0011-53db-a6bc-1e431fa58645'::uuid, 'Crossfit/kettlebell_windmill.mp4', 'Crossfit/kettlebell_windmill.webp'),   -- kettlebell_windmill
    ('d44848e1-4b5e-543c-b236-50539f90da52'::uuid, 'Crossfit/dumbbell_windmill.mp4', 'Crossfit/dumbbell_windmill.webp'),   -- dumbbell_windmill
    ('c7926e57-307d-5f7b-8b04-fa04268fb843'::uuid, 'Crossfit/barbell_muscle_snatch.mp4', 'Crossfit/barbell_muscle_snatch.webp'),   -- barbell_muscle_snatch
    ('a169c688-ceca-585b-8848-b9e8e2897b8c'::uuid, 'Calistenia/muscle_up.mp4', 'Calistenia/muscle_up.webp'),   -- muscle_up
    ('b9e061c2-d293-55b8-899d-3f4a0424d55f'::uuid, 'Cardio/elliptical_trainer.mp4', 'Cardio/elliptical_trainer.webp'),   -- elliptical_trainer
    ('819104b3-176a-5bb6-b948-045fe7d42ddb'::uuid, 'Cardio/stair_climber.mp4', 'Cardio/stair_climber.webp'),   -- stair_climber
    ('90d12d3e-dfa7-52f8-85b1-fd3ca706d54e'::uuid, 'Cardio/air_walker.mp4', 'Cardio/air_walker.webp'),   -- air_walker
    ('0ab059ca-5a68-5d39-940e-3b18841a4f51'::uuid, 'Funcional e HIT/navy_seal_burpee.mp4', 'Funcional e HIT/navy_seal_burpee.webp'),   -- navy_seal_burpee
    ('f453a6dc-1663-5295-aad0-3c8bdcacda1f'::uuid, 'Panturrilhas/dumbbell_step_calf_raise.mp4', 'Panturrilhas/dumbbell_step_calf_raise.webp'),   -- dumbbell_step_calf_raise
    ('a7b5972e-9336-57d4-99e6-a74e44e5eab7'::uuid, 'Calistenia/parallel_bar_dip.mp4', 'Calistenia/parallel_bar_dip.webp'),   -- parallel_bar_dip
    ('7f0bb706-1ba1-56c5-8188-e6f5d69fb8c6'::uuid, 'Peitoral/chest_dip.mp4', 'Peitoral/chest_dip.webp'),   -- chest_dip
    ('570d206c-f743-5427-ba86-a9f05a256932'::uuid, 'Calistenia/chair_dip_between_chairs.mp4', 'Calistenia/chair_dip_between_chairs.webp'),   -- chair_dip_between_chairs
    ('d3ad0b18-7396-5134-8af0-b4bcef189052'::uuid, 'Calistenia/ring_dip.mp4', 'Calistenia/ring_dip.webp'),   -- ring_dip
    ('aff44941-afd1-5489-9902-f7c2adacb6e0'::uuid, 'Funcional e HIT/medicine_ball_chest_pass.mp4', 'Funcional e HIT/medicine_ball_chest_pass.webp'),   -- medicine_ball_chest_pass
    ('e05ca40b-5db6-5410-9072-48b61a846019'::uuid, 'Funcional e HIT/reverse_lunge_to_knee_raise.mp4', 'Funcional e HIT/reverse_lunge_to_knee_raise.webp'),   -- reverse_lunge_to_knee_raise
    ('7e9d3e63-a662-50c3-8a25-822ea9e10581'::uuid, 'Funcional e HIT/fast_lateral_step.mp4', 'Funcional e HIT/fast_lateral_step.webp'),   -- fast_lateral_step
    ('66c64631-4e20-58ad-bf25-57ca2509a479'::uuid, 'Funcional e HIT/ski_step.mp4', 'Funcional e HIT/ski_step.webp'),   -- ski_step
    ('efe0626d-2c4c-52bf-863e-3f3a41570561'::uuid, 'Funcional e HIT/skater_jump.mp4', 'Funcional e HIT/skater_jump.webp'),   -- skater_jump
    ('c915d4e1-ebe5-5f30-a893-5cee465452db'::uuid, 'Calistenia/planche.mp4', 'Calistenia/planche.webp'),   -- planche
    ('f926f52d-f147-5fe3-bc18-9c4de3b8a723'::uuid, 'Calistenia/planche_push_up.mp4', 'Calistenia/planche_push_up.webp'),   -- planche_push_up
    ('8358acc1-24dc-5a23-b4a1-ef54484762db'::uuid, 'Cardio/vibration_plate.mp4', 'Cardio/vibration_plate.webp'),   -- vibration_plate
    ('09e651d7-6fde-5e68-9457-fff2ea584d33'::uuid, 'Funcional e HIT/plie_jumping_jack.mp4', 'Funcional e HIT/plie_jumping_jack.webp'),   -- plie_jumping_jack
    ('b7fb3049-fa08-5c5f-b181-2368404b405b'::uuid, 'Funcional e HIT/jumping_jack.mp4', 'Funcional e HIT/jumping_jack.webp'),   -- jumping_jack
    ('fa859a0b-974b-57e8-915b-d73811434831'::uuid, 'Funcional e HIT/single_leg_glute_bridge_extended_leg.mp4', 'Funcional e HIT/single_leg_glute_bridge_extended_leg.webp'),   -- single_leg_glute_bridge_extended_leg
    ('f736e61e-7a77-5e0f-978a-cc6dca8ecd60'::uuid, 'Funcional e HIT/single_leg_bench_glute_bridge.mp4', 'Funcional e HIT/single_leg_bench_glute_bridge.webp'),   -- single_leg_bench_glute_bridge
    ('f178849d-0c29-53b5-ad56-b2d61005e711'::uuid, 'Funcional e HIT/band_glute_bridge.mp4', 'Funcional e HIT/band_glute_bridge.webp'),   -- band_glute_bridge
    ('ba9655da-86ef-5cd0-b3eb-68ab80198f21'::uuid, 'Glúteos/dumbbell_glute_bridge.mp4', 'Glúteos/dumbbell_glute_bridge.webp'),   -- dumbbell_glute_bridge
    ('ebfce553-16d7-5cf9-aea0-e6b861da1cc4'::uuid, 'Funcional e HIT/glute_bridge.mp4', 'Funcional e HIT/glute_bridge.webp'),   -- glute_bridge
    ('18c799c5-7f66-5dca-8408-2935bb72c0d0'::uuid, 'Pernas/barbell_glute_bridge.mp4', 'Pernas/barbell_glute_bridge.webp'),   -- barbell_glute_bridge
    ('d0e6118d-03b8-520f-a038-62fbcfac1788'::uuid, 'Calistenia/single_leg_glute_bridge_leg_raised.mp4', 'Calistenia/single_leg_glute_bridge_leg_raised.webp'),   -- single_leg_glute_bridge_leg_raised
    ('9e9e1a9f-61ed-558b-b6e9-741093f9f773'::uuid, 'Mobilidade/cobra_pose.mp4', 'Mobilidade/cobra_pose.webp'),   -- cobra_pose
    ('2b98fa01-bff6-5111-8b05-2cc03857e87f'::uuid, 'Mobilidade/seated_straddle_pose.mp4', 'Mobilidade/seated_straddle_pose.webp'),   -- seated_straddle_pose
    ('2281b12d-d38e-5797-b3d6-273555098468'::uuid, 'Mobilidade/half_frog_pose.mp4', 'Mobilidade/half_frog_pose.webp'),   -- half_frog_pose
    ('6d67a44b-839a-5040-8df2-c55b7a13be50'::uuid, 'Mobilidade/fish_pose.mp4', 'Mobilidade/fish_pose.webp'),   -- fish_pose
    ('4fa95614-8a88-53c5-84a3-931d5d87a689'::uuid, 'Mobilidade/bow_pose.mp4', 'Mobilidade/bow_pose.webp'),   -- bow_pose
    ('0c05b07d-b212-53f4-8a82-f98a553f24b1'::uuid, 'Mobilidade/rocking_bow_pose.mp4', 'Mobilidade/rocking_bow_pose.webp'),   -- rocking_bow_pose
    ('36007841-c542-5115-9704-a1718a292832'::uuid, 'Mobilidade/happy_baby_pose.mp4', 'Mobilidade/happy_baby_pose.webp'),   -- happy_baby_pose
    ('09d47bd7-5931-57f1-9a6a-581bc51dbb5a'::uuid, 'Mobilidade/frog_pose.mp4', 'Mobilidade/frog_pose.webp'),   -- frog_pose
    ('d6c9f705-2c0a-5f46-a206-f18da6242b6c'::uuid, 'Crossfit/barbell_power_clean.mp4', 'Crossfit/barbell_power_clean.webp'),   -- barbell_power_clean
    ('c9b19ecb-cd79-5882-ae8f-cb41210935c8'::uuid, 'Mobilidade/scapular_protraction_and_retraction.mp4', 'Mobilidade/scapular_protraction_and_retraction.webp'),   -- scapular_protraction_and_retraction
    ('e5cb7b2d-ad78-598e-a204-f5ce158a2823'::uuid, 'Crossfit/jump_rope.mp4', 'Crossfit/jump_rope.webp'),   -- jump_rope
    ('45b98976-fe0f-57ec-a1d7-e466d834aea8'::uuid, 'Costas/rope_straight_arm_pulldown.mp4', 'Costas/rope_straight_arm_pulldown.webp'),   -- rope_straight_arm_pulldown
    ('b276c9b3-120f-5eed-a131-c7cc2430aacc'::uuid, 'Costas/rope_bent_over_pulldown.mp4', 'Costas/rope_bent_over_pulldown.webp'),   -- rope_bent_over_pulldown
    ('a28cf315-03c0-539f-b89e-07f8df3e00cd'::uuid, 'Costas/cable_one_arm_straight_arm_pulldown.mp4', 'Costas/cable_one_arm_straight_arm_pulldown.webp'),   -- cable_one_arm_straight_arm_pulldown
    ('dd5feb6c-d995-554a-bd2d-6b4c23503c15'::uuid, 'Costas/barbell_pullover.mp4', 'Costas/barbell_pullover.webp'),   -- barbell_pullover
    ('29970d91-9ced-51be-8adc-e198914566d0'::uuid, 'Costas/ez_bar_reverse_grip_pullover.mp4', 'Costas/ez_bar_reverse_grip_pullover.webp'),   -- ez_bar_reverse_grip_pullover
    ('b7d108e3-7b60-5c18-8940-2c06da444a43'::uuid, 'Costas/cable_lying_pullover.mp4', 'Costas/cable_lying_pullover.webp'),   -- cable_lying_pullover
    ('7a1970fd-689e-5e5a-a2f7-c63781194c60'::uuid, 'Peitoral/stability_ball_dumbbell_pullover.mp4', 'Peitoral/stability_ball_dumbbell_pullover.webp'),   -- stability_ball_dumbbell_pullover
    ('ced48215-0ed8-5e92-97a3-007358102890'::uuid, 'Costas/decline_barbell_pullover.mp4', 'Costas/decline_barbell_pullover.webp'),   -- decline_barbell_pullover
    ('421febc2-898b-530d-b9d6-76c04b8cd89f'::uuid, 'Costas/seated_cable_pullover.mp4', 'Costas/seated_cable_pullover.webp'),   -- seated_cable_pullover
    ('7d1fcd6b-647c-55a4-81b1-d294879b4850'::uuid, 'Peitoral/dumbbell_pullover.mp4', 'Peitoral/dumbbell_pullover.webp'),   -- dumbbell_pullover
    ('e43489f2-8f67-5420-8fb9-41b51fc9b841'::uuid, 'Peitoral/dumbbell_pullover_with_legs_raised.mp4', 'Peitoral/dumbbell_pullover_with_legs_raised.webp'),   -- dumbbell_pullover_with_legs_raised
    ('4f65f54b-82fc-54c5-bc9d-f258c3ab8304'::uuid, 'Costas/lever_pullover.mp4', 'Costas/lever_pullover.webp'),   -- lever_pullover
    ('135bc200-0b7a-57d7-832e-23be4c1cce58'::uuid, 'Calistenia/single_leg_hip_thrust.mp4', 'Calistenia/single_leg_hip_thrust.webp'),   -- single_leg_hip_thrust
    ('f3bca6f8-ff34-5c3f-8024-5cd7137128d0'::uuid, 'Funcional e HIT/sumo_squat_jump.mp4', 'Funcional e HIT/sumo_squat_jump.webp'),   -- sumo_squat_jump
    ('6099d1d0-7bf9-53dc-ab17-00488cf63c8f'::uuid, 'Funcional e HIT/high_knee_jump.mp4', 'Funcional e HIT/high_knee_jump.webp'),   -- high_knee_jump
    ('c5f4a906-d6a2-5177-a226-f47ad7efec04'::uuid, 'Costas/lat_pulldown.mp4', 'Costas/lat_pulldown.webp'),   -- lat_pulldown
    ('e4796c7d-1e34-51e1-9585-297fc984ebcb'::uuid, 'Costas/reverse_grip_lat_pulldown.mp4', 'Costas/reverse_grip_lat_pulldown.webp'),   -- reverse_grip_lat_pulldown
    ('b2191802-8c70-5c4a-bde8-8aa707685462'::uuid, 'Costas/kneeling_dual_cable_neutral_pulldown.mp4', 'Costas/kneeling_dual_cable_neutral_pulldown.webp'),   -- kneeling_dual_cable_neutral_pulldown
    ('a8a1932a-5a88-54d7-be1d-7d4461f3e182'::uuid, 'Costas/v_bar_lat_pulldown.mp4', 'Costas/v_bar_lat_pulldown.webp'),   -- v_bar_lat_pulldown
    ('cab868a4-03df-5a5b-b1fb-2e9adb0fdd8b'::uuid, 'Costas/half_kneeling_one_arm_cable_pulldown.mp4', 'Costas/half_kneeling_one_arm_cable_pulldown.webp'),   -- half_kneeling_one_arm_cable_pulldown
    ('9e7ad364-21e0-58ff-94bb-ebbe5014d321'::uuid, 'Costas/lever_lat_pulldown.mp4', 'Costas/lever_lat_pulldown.webp'),   -- lever_lat_pulldown
    ('f02c74cf-1c69-53fa-b9db-941e5617a2bb'::uuid, 'Glúteos/kneeling_cable_pull_through.mp4', 'Glúteos/kneeling_cable_pull_through.webp'),   -- kneeling_cable_pull_through
    ('d9177dd4-370e-532f-98c4-7f7f95251262'::uuid, 'Calistenia/front_lever_row.mp4', 'Calistenia/front_lever_row.webp'),   -- front_lever_row
    ('7f21efdf-b0ac-596e-9304-0f2f754414b4'::uuid, 'Funcional e HIT/kneeling_band_pulldown.mp4', 'Funcional e HIT/kneeling_band_pulldown.webp'),   -- kneeling_band_pulldown
    ('9e2c7e72-f528-5009-9040-dd931d18e1b2'::uuid, 'Costas/machine_behind_the_neck_pulldown.mp4', 'Costas/machine_behind_the_neck_pulldown.webp'),   -- machine_behind_the_neck_pulldown
    ('691c2ef7-292c-52e8-bd29-1b889a3f9ed7'::uuid, 'Costas/cable_behind_the_neck_pulldown.mp4', 'Costas/cable_behind_the_neck_pulldown.webp'),   -- cable_behind_the_neck_pulldown
    ('6d75c4c5-837e-545f-a5c9-1f580b83909d'::uuid, 'Costas/kneeling_one_arm_high_pulldown.mp4', 'Costas/kneeling_one_arm_high_pulldown.webp'),   -- kneeling_one_arm_high_pulldown
    ('f69770ef-a2a5-52c4-a944-88a7093df68d'::uuid, 'Crossfit/dumbbell_between_legs_deadlift.mp4', 'Crossfit/dumbbell_between_legs_deadlift.webp'),   -- dumbbell_between_legs_deadlift
    ('8efe541c-8c4f-5239-9b07-ff448c18d606'::uuid, 'Costas/seated_one_arm_cable_pulldown.mp4', 'Costas/seated_one_arm_cable_pulldown.webp'),   -- seated_one_arm_cable_pulldown
    ('0a874551-77fd-523e-8114-8da19ad68e2d'::uuid, 'Costas/dumbbell_weighted_chin_up.mp4', 'Costas/dumbbell_weighted_chin_up.webp'),   -- dumbbell_weighted_chin_up
    ('7ea23869-df01-557f-8e0a-eb6156189db9'::uuid, 'Trapézio/cross_cable_face_pull.mp4', 'Trapézio/cross_cable_face_pull.webp'),   -- cross_cable_face_pull
    ('9875f742-7c37-5b29-bb0a-051ad906e789'::uuid, 'Costas/standing_cable_twisting_pull.mp4', 'Costas/standing_cable_twisting_pull.webp'),   -- standing_cable_twisting_pull
    ('d24c8a77-ec24-505a-b784-67cb1cd6046b'::uuid, 'Calistenia/scapular_pull_up.mp4', 'Calistenia/scapular_pull_up.webp'),   -- scapular_pull_up
    ('e49a019c-520a-5188-9ee7-394d5eed296f'::uuid, 'Calistenia/isometric_pull_up_hold.mp4', 'Calistenia/isometric_pull_up_hold.webp'),   -- isometric_pull_up_hold
    ('1f3f0b22-6cc6-5179-98c2-f3844c1a1be4'::uuid, 'Costas/close_grip_lat_pulldown.mp4', 'Costas/close_grip_lat_pulldown.webp'),   -- close_grip_lat_pulldown
    ('4242a6fb-17c8-581f-8943-ee9edb99fddd'::uuid, 'Trapézio/kneeling_face_pull.mp4', 'Trapézio/kneeling_face_pull.webp'),   -- kneeling_face_pull
    ('a00ca4b4-d35e-530d-9b66-5ef65b634a36'::uuid, 'Funcional e HIT/band_pull_through.mp4', 'Funcional e HIT/band_pull_through.webp'),   -- band_pull_through
    ('663781a5-fd6d-53a0-9b98-2537e5fff799'::uuid, 'Mobilidade/shoulder_pendulum.mp4', 'Mobilidade/shoulder_pendulum.webp'),   -- shoulder_pendulum
    ('c6fe56d2-1e2b-51bd-bcd7-9a983228d87f'::uuid, 'Funcional e HIT/bird_dog.mp4', 'Funcional e HIT/bird_dog.webp'),   -- bird_dog
    ('fb3814e9-89fc-5f3e-876b-3077d13dbc72'::uuid, 'Pernas/romanian_deadlift.mp4', 'Pernas/romanian_deadlift.webp'),   -- romanian_deadlift
    ('cab4b690-c0d8-5483-8981-f83bcced6bc9'::uuid, 'Funcional e HIT/bear_crawl.mp4', 'Funcional e HIT/bear_crawl.webp'),   -- bear_crawl
    ('bbf3aa8e-1f53-56ed-b3e7-1b246557672a'::uuid, 'Ombros/dumbbell_upright_row.mp4', 'Ombros/dumbbell_upright_row.webp'),   -- dumbbell_upright_row
    ('d1a5383d-3caa-58f5-b4d2-f29a0a090134'::uuid, 'Trapézio/ez_bar_upright_row.mp4', 'Trapézio/ez_bar_upright_row.webp'),   -- ez_bar_upright_row
    ('1c620f11-7e04-5f30-a33a-29a54cbf30ef'::uuid, 'Trapézio/cable_upright_row.mp4', 'Trapézio/cable_upright_row.webp'),   -- cable_upright_row
    ('9e9cefed-76fd-5d16-8650-83ba5d44895f'::uuid, 'Trapézio/single_dumbbell_upright_row.mp4', 'Trapézio/single_dumbbell_upright_row.webp'),   -- single_dumbbell_upright_row
    ('83a24f20-84d2-57ad-8ff3-e729b2d81e0e'::uuid, 'Costas/barbell_incline_chest_supported_row.mp4', 'Costas/barbell_incline_chest_supported_row.webp'),   -- barbell_incline_chest_supported_row
    ('4d50874b-4cec-502a-8051-0e0e471e8a99'::uuid, 'Costas/barbell_bent_over_row.mp4', 'Costas/barbell_bent_over_row.webp'),   -- barbell_bent_over_row
    ('552b8970-aff0-58d9-9484-74be5eaf2bf2'::uuid, 'Costas/barbell_reverse_grip_bent_over_row.mp4', 'Costas/barbell_reverse_grip_bent_over_row.webp'),   -- barbell_reverse_grip_bent_over_row
    ('73b16dce-dc06-581e-a529-344bd078fe20'::uuid, 'Costas/t_bar_row.mp4', 'Costas/t_bar_row.webp'),   -- t_bar_row
    ('39db3ab8-e7d5-5294-8a44-2cacff2780e9'::uuid, 'Costas/smith_machine_bent_over_row.mp4', 'Costas/smith_machine_bent_over_row.webp'),   -- smith_machine_bent_over_row
    ('f765b56e-7f60-5837-bdce-c0be0a961562'::uuid, 'Costas/cable_bent_over_row.mp4', 'Costas/cable_bent_over_row.webp'),   -- cable_bent_over_row
    ('d7305076-7cbb-5c05-a042-406ee0e3bde0'::uuid, 'Costas/dumbbell_incline_neutral_grip_row.mp4', 'Costas/dumbbell_incline_neutral_grip_row.webp'),   -- dumbbell_incline_neutral_grip_row
    ('d264ee5d-fcc5-5f8c-8b57-30ea2ae184c5'::uuid, 'Costas/dumbbell_incline_reverse_grip_row.mp4', 'Costas/dumbbell_incline_reverse_grip_row.webp'),   -- dumbbell_incline_reverse_grip_row
    ('bfb31ef8-a6b1-5663-8044-e892d88966f3'::uuid, 'Costas/cable_chest_supported_row.mp4', 'Costas/cable_chest_supported_row.webp'),   -- cable_chest_supported_row
    ('f9a2fd43-ccec-585e-8e1b-190f4d51f694'::uuid, 'Costas/smith_machine_inverted_row.mp4', 'Costas/smith_machine_inverted_row.webp'),   -- smith_machine_inverted_row
    ('9b191e87-3d34-5767-813b-55a7a39b534a'::uuid, 'Calistenia/ring_inverted_row.mp4', 'Calistenia/ring_inverted_row.webp'),   -- ring_inverted_row
    ('149fecd4-58b1-5139-a704-5c2d37cbf8d0'::uuid, 'Trapézio/cable_bent_over_reverse_fly.mp4', 'Trapézio/cable_bent_over_reverse_fly.webp'),   -- cable_bent_over_reverse_fly
    ('aed32c40-608a-5d2c-9fae-f0a046a0e210'::uuid, 'Calistenia/table_inverted_row.mp4', 'Calistenia/table_inverted_row.webp'),   -- table_inverted_row
    ('03a6a766-7e34-5dd2-8a8c-cb4457534a7c'::uuid, 'Costas/dumbbell_renegade_row.mp4', 'Costas/dumbbell_renegade_row.webp'),   -- dumbbell_renegade_row
    ('e456154d-c8a1-512e-bb87-c91a7e3b22f4'::uuid, 'Costas/plate_loaded_seated_row.mp4', 'Costas/plate_loaded_seated_row.webp'),   -- plate_loaded_seated_row
    ('c8479334-d145-512e-b4bd-fe8e77d335d2'::uuid, 'Costas/seated_cable_row.mp4', 'Costas/seated_cable_row.webp'),   -- seated_cable_row
    ('74dfa8b4-a086-500c-98f6-ee8fa3672d9f'::uuid, 'Costas/seated_cable_row_straight_handle.mp4', 'Costas/seated_cable_row_straight_handle.webp'),   -- seated_cable_row_straight_handle
    ('777896ee-be7d-5189-a148-862a4123106a'::uuid, 'Costas/seated_machine_row.mp4', 'Costas/seated_machine_row.webp'),   -- seated_machine_row
    ('1a919a0d-45b5-5ed6-8cb6-65dbf32eec61'::uuid, 'Costas/landmine_t_bar_row.mp4', 'Costas/landmine_t_bar_row.webp'),   -- landmine_t_bar_row
    ('562041da-c75e-5354-983a-b49154b7682d'::uuid, 'Costas/lever_t_bar_row.mp4', 'Costas/lever_t_bar_row.webp'),   -- lever_t_bar_row
    ('c42a18cb-0bd2-5158-a7e5-2682ad23b55f'::uuid, 'Costas/standing_one_arm_cable_row.mp4', 'Costas/standing_one_arm_cable_row.webp'),   -- standing_one_arm_cable_row
    ('26731a93-ef1d-5a46-9405-33061dac309b'::uuid, 'Funcional e HIT/band_pull_apart.mp4', 'Funcional e HIT/band_pull_apart.webp'),   -- band_pull_apart
    ('87d7d118-b786-55b4-ad30-2ab62e8b1d50'::uuid, 'Trapézio/one_arm_dumbbell_upright_row.mp4', 'Trapézio/one_arm_dumbbell_upright_row.webp'),   -- one_arm_dumbbell_upright_row
    ('6a76b5cd-2a0c-5980-9324-035f624947eb'::uuid, 'Costas/seated_one_arm_cable_row_with_twist.mp4', 'Costas/seated_one_arm_cable_row_with_twist.webp'),   -- seated_one_arm_cable_row_with_twist
    ('8ba5201a-83df-51e1-bbaa-e9a2f7af70f9'::uuid, 'Funcional e HIT/band_bent_over_reverse_fly.mp4', 'Funcional e HIT/band_bent_over_reverse_fly.webp'),   -- band_bent_over_reverse_fly
    ('c7cda0bd-60bf-5326-a102-c6e5b54ffeba'::uuid, 'Trapézio/barbell_upright_row.mp4', 'Trapézio/barbell_upright_row.webp'),   -- barbell_upright_row
    ('9c14bb79-0fbb-5013-ac07-70d93cd38f6f'::uuid, 'Crossfit/barbell_behind_the_back_row.mp4', 'Crossfit/barbell_behind_the_back_row.webp'),   -- barbell_behind_the_back_row
    ('7a57245f-6345-5b8f-b690-7350813aa4fc'::uuid, 'Ombros/dumbbell_rear_delt_row.mp4', 'Ombros/dumbbell_rear_delt_row.webp'),   -- dumbbell_rear_delt_row
    ('5b31e2af-8985-57ea-9107-a3abcf4db62c'::uuid, 'Calistenia/doorway_bodyweight_row.mp4', 'Calistenia/doorway_bodyweight_row.webp'),   -- doorway_bodyweight_row
    ('8b73bd08-e5cb-51fd-9a8a-2ba1ab0b923b'::uuid, 'Costas/cable_crossover_pulldown.mp4', 'Costas/cable_crossover_pulldown.webp'),   -- cable_crossover_pulldown
    ('3f57d45b-660e-532e-a4b0-26ccb2d753ab'::uuid, 'Costas/barbell_mixed_grip_bent_over_row.mp4', 'Costas/barbell_mixed_grip_bent_over_row.webp'),   -- barbell_mixed_grip_bent_over_row
    ('0a46cde1-4bf6-5758-8f94-b776ea587959'::uuid, 'Costas/dumbbell_bent_over_row.mp4', 'Costas/dumbbell_bent_over_row.webp'),   -- dumbbell_bent_over_row
    ('a8eccd14-d32a-5a0e-9612-bba83a17aaa6'::uuid, 'Costas/dumbbell_reverse_grip_bent_over_row.mp4', 'Costas/dumbbell_reverse_grip_bent_over_row.webp'),   -- dumbbell_reverse_grip_bent_over_row
    ('a8a8884c-5c4b-5bb6-89cc-66b67b55d9ff'::uuid, 'Costas/kettlebell_bent_over_row.mp4', 'Costas/kettlebell_bent_over_row.webp'),   -- kettlebell_bent_over_row
    ('00021572-ea2a-5d8a-b7cd-3b2808e287ef'::uuid, 'Ombros/seated_dumbbell_rear_delt_row.mp4', 'Ombros/seated_dumbbell_rear_delt_row.webp'),   -- seated_dumbbell_rear_delt_row
    ('91c6af2f-410c-5bb7-afde-bf76580c6022'::uuid, 'Costas/cable_shotgun_row.mp4', 'Costas/cable_shotgun_row.webp'),   -- cable_shotgun_row
    ('f63bada2-f2b7-516b-b893-368bfe35889e'::uuid, 'Trapézio/cable_y_raise.mp4', 'Trapézio/cable_y_raise.webp'),   -- cable_y_raise
    ('2f0fdf11-7367-5ba9-914b-c11d3096595e'::uuid, 'Costas/lever_front_pulldown.mp4', 'Costas/lever_front_pulldown.webp'),   -- lever_front_pulldown
    ('2018ec06-be90-5f56-bf85-1807ce60114e'::uuid, 'Ombros/lying_cable_reverse_fly.mp4', 'Ombros/lying_cable_reverse_fly.webp'),   -- lying_cable_reverse_fly
    ('380f2513-867f-5de0-b51d-723a0c5809d7'::uuid, 'Funcional e HIT/seated_band_row.mp4', 'Funcional e HIT/seated_band_row.webp'),   -- seated_band_row
    ('d26341a2-56a1-5130-bb58-1f6dee4f1f5a'::uuid, 'Costas/seated_close_grip_cable_row.mp4', 'Costas/seated_close_grip_cable_row.webp'),   -- seated_close_grip_cable_row
    ('110dbb19-c895-56cb-89fb-bbff2b7a8167'::uuid, 'Costas/landmine_one_arm_row.mp4', 'Costas/landmine_one_arm_row.webp'),   -- landmine_one_arm_row
    ('2f357a28-20b8-5886-8d18-1e4317fac5e2'::uuid, 'Funcional e HIT/gymstick_one_arm_row.mp4', 'Funcional e HIT/gymstick_one_arm_row.webp'),   -- gymstick_one_arm_row
    ('72d6a804-e21e-5f50-a5e7-27839b5edcfe'::uuid, 'Cardio/rowing_machine.mp4', 'Cardio/rowing_machine.webp'),   -- rowing_machine
    ('2d1b4f95-3d14-5611-9ad8-730ac4cbdcb6'::uuid, 'Mobilidade/hamstring_foam_rolling.mp4', 'Mobilidade/hamstring_foam_rolling.webp'),   -- hamstring_foam_rolling
    ('5ec3e6df-243a-5471-a4f3-140ccf798cbd'::uuid, 'Mobilidade/calf_foam_rolling.mp4', 'Mobilidade/calf_foam_rolling.webp'),   -- calf_foam_rolling
    ('b7755bfd-9a3c-5dbc-8246-80cc2ca61633'::uuid, 'Mobilidade/back_foam_rolling.mp4', 'Mobilidade/back_foam_rolling.webp'),   -- back_foam_rolling
    ('632b1cd3-b753-5b2e-a82b-9598e26b4797'::uuid, 'Mobilidade/quad_foam_rolling.mp4', 'Mobilidade/quad_foam_rolling.webp'),   -- quad_foam_rolling
    ('9c9d45ac-9577-5234-adff-1bdb477078ce'::uuid, 'Mobilidade/rhomboid_foam_rolling.mp4', 'Mobilidade/rhomboid_foam_rolling.webp'),   -- rhomboid_foam_rolling
    ('4486fca8-0c94-5ef5-b0a6-b52a5fd32302'::uuid, 'Funcional e HIT/stability_ball_rollout.mp4', 'Funcional e HIT/stability_ball_rollout.webp'),   -- stability_ball_rollout
    ('265a5b04-7c13-59af-98a6-e54c751b6dfb'::uuid, 'Mobilidade/rolling_like_a_ball.mp4', 'Mobilidade/rolling_like_a_ball.webp'),   -- rolling_like_a_ball
    ('0b9d0da0-2031-5a60-97f9-5342573e783c'::uuid, 'Antebraços/wrist_roller.mp4', 'Antebraços/wrist_roller.webp'),   -- wrist_roller
    ('4f275de0-d079-5ac9-8b05-545193d0d5d0'::uuid, 'Mobilidade/glute_foam_rolling.mp4', 'Mobilidade/glute_foam_rolling.webp'),   -- glute_foam_rolling
    ('85471bb9-c61c-5c2b-a13d-fe4522e1d1a7'::uuid, 'Mobilidade/posterior_shoulder_foam_rolling.mp4', 'Mobilidade/posterior_shoulder_foam_rolling.webp'),   -- posterior_shoulder_foam_rolling
    ('3fcc1141-4f1d-58ca-82b0-d140b45fd6d8'::uuid, 'Mobilidade/plantar_foam_rolling.mp4', 'Mobilidade/plantar_foam_rolling.webp'),   -- plantar_foam_rolling
    ('e6fbbacc-adca-57c1-812a-f1b4042d3a61'::uuid, 'Mobilidade/chest_and_front_shoulder_foam_rolling.mp4', 'Mobilidade/chest_and_front_shoulder_foam_rolling.webp'),   -- chest_and_front_shoulder_foam_rolling
    ('7ac24423-8531-522c-a377-8dc9e2241943'::uuid, 'Bíceps/incline_dumbbell_alternating_curl.mp4', 'Bíceps/incline_dumbbell_alternating_curl.webp'),   -- incline_dumbbell_alternating_curl
    ('2d634659-e81c-575b-8bb7-d34e783f5734'::uuid, 'Bíceps/cable_incline_bilateral_biceps_curl.mp4', 'Bíceps/cable_incline_bilateral_biceps_curl.webp'),   -- cable_incline_bilateral_biceps_curl
    ('f2bbbc82-194b-5ddb-85e3-dd3e16538b5e'::uuid, 'Bíceps/seated_barbell_concentration_curl.mp4', 'Bíceps/seated_barbell_concentration_curl.webp'),   -- seated_barbell_concentration_curl
    ('075e0444-df01-5dd3-8aa0-f7fbd965c048'::uuid, 'Bíceps/barbell_curl.mp4', 'Bíceps/barbell_curl.webp'),   -- barbell_curl
    ('3d57785a-3983-54d6-b474-266be405c107'::uuid, 'Bíceps/barbell_prone_high_bench_curl.mp4', 'Bíceps/barbell_prone_high_bench_curl.webp'),   -- barbell_prone_high_bench_curl
    ('5ce16878-4a4f-585d-b1fc-dc92c04ed533'::uuid, 'Bíceps/close_grip_barbell_curl.mp4', 'Bíceps/close_grip_barbell_curl.webp'),   -- close_grip_barbell_curl
    ('22c0f49a-5a02-558f-b3dc-ffd32a993b88'::uuid, 'Bíceps/barbell_arm_blaster_curl.mp4', 'Bíceps/barbell_arm_blaster_curl.webp'),   -- barbell_arm_blaster_curl
    ('7b6f02de-6e6f-557e-838d-25a45e1b9e30'::uuid, 'Bíceps/cable_lying_biceps_curl.mp4', 'Bíceps/cable_lying_biceps_curl.webp'),   -- cable_lying_biceps_curl
    ('bd21e33b-b34c-508d-98a6-71407bbd44ea'::uuid, 'Bíceps/machine_biceps_curl.mp4', 'Bíceps/machine_biceps_curl.webp'),   -- machine_biceps_curl
    ('b66c7c5e-3af2-5b0c-bb3f-d8d617846b6f'::uuid, 'Antebraços/barbell_reverse_curl.mp4', 'Antebraços/barbell_reverse_curl.webp'),   -- barbell_reverse_curl
    ('d06a8654-14da-50cb-9991-a215592f8140'::uuid, 'Bíceps/dumbbell_reverse_curl.mp4', 'Bíceps/dumbbell_reverse_curl.webp'),   -- dumbbell_reverse_curl
    ('62f869ef-d5dc-51d6-bcfd-0fa96b687567'::uuid, 'Bíceps/dumbbell_arm_blaster_hammer_curl.mp4', 'Bíceps/dumbbell_arm_blaster_hammer_curl.webp'),   -- dumbbell_arm_blaster_hammer_curl
    ('0d3be45f-2b0c-5118-8aff-e3e1249784a5'::uuid, 'Bíceps/ez_bar_preacher_curl.mp4', 'Bíceps/ez_bar_preacher_curl.webp'),   -- ez_bar_preacher_curl
    ('deb08e51-4bc0-5251-99ae-b3282ec12a60'::uuid, 'Bíceps/dumbbell_hammer_preacher_curl.mp4', 'Bíceps/dumbbell_hammer_preacher_curl.webp'),   -- dumbbell_hammer_preacher_curl
    ('1e760fe9-132f-5a82-b493-25d386bb4e33'::uuid, 'Bíceps/lever_preacher_curl.mp4', 'Bíceps/lever_preacher_curl.webp'),   -- lever_preacher_curl
    ('247f6dfc-2c7d-53b2-bfd7-2ccbef4bac9b'::uuid, 'Bíceps/cable_one_arm_biceps_curl.mp4', 'Bíceps/cable_one_arm_biceps_curl.webp'),   -- cable_one_arm_biceps_curl
    ('019873b5-f258-5799-bfe8-46d746cc7c1e'::uuid, 'Bíceps/zottman_curl.mp4', 'Bíceps/zottman_curl.webp'),   -- zottman_curl
    ('3e9b2a66-b96c-51ee-9247-1272fed8a9bc'::uuid, 'Bíceps/barbell_one_arm_curl.mp4', 'Bíceps/barbell_one_arm_curl.webp'),   -- barbell_one_arm_curl
    ('51f5c419-b002-533f-9271-04a6a14aa011'::uuid, 'Bíceps/seated_dumbbell_alternating_curl.mp4', 'Bíceps/seated_dumbbell_alternating_curl.webp'),   -- seated_dumbbell_alternating_curl
    ('355c89cd-a573-573c-b891-ea3361e10c7a'::uuid, 'Bíceps/dumbbell_high_curl.mp4', 'Bíceps/dumbbell_high_curl.webp'),   -- dumbbell_high_curl
    ('2e6802ce-b946-5f92-a007-1c50835a48ac'::uuid, 'Bíceps/cable_squatting_biceps_curl.mp4', 'Bíceps/cable_squatting_biceps_curl.webp'),   -- cable_squatting_biceps_curl
    ('c1ad79b4-41c0-55b7-a497-835427fa848e'::uuid, 'Funcional e HIT/band_biceps_curl.mp4', 'Funcional e HIT/band_biceps_curl.webp'),   -- band_biceps_curl
    ('8795dac0-b8b4-58c3-82e6-0766eeaf8f32'::uuid, 'Bíceps/dumbbell_biceps_curl.mp4', 'Bíceps/dumbbell_biceps_curl.webp'),   -- dumbbell_biceps_curl
    ('51f1dfc8-8294-5d48-aee2-43685b0b3460'::uuid, 'Bíceps/ez_bar_close_grip_curl.mp4', 'Bíceps/ez_bar_close_grip_curl.webp'),   -- ez_bar_close_grip_curl
    ('4941e9eb-0017-514e-8f7a-17c2eb853970'::uuid, 'Bíceps/cable_incline_biceps_curl.mp4', 'Bíceps/cable_incline_biceps_curl.webp'),   -- cable_incline_biceps_curl
    ('8981164f-5848-57b6-b2fb-32b3c17215f9'::uuid, 'Bíceps/seated_dumbbell_biceps_curl.mp4', 'Bíceps/seated_dumbbell_biceps_curl.webp'),   -- seated_dumbbell_biceps_curl
    ('97e2087c-6f87-57da-9224-ed3258942173'::uuid, 'Bíceps/dumbbell_one_arm_biceps_curl.mp4', 'Bíceps/dumbbell_one_arm_biceps_curl.webp'),   -- dumbbell_one_arm_biceps_curl
    ('66d82bf2-d190-5d26-9ee0-ce1486cccd15'::uuid, 'Bíceps/cable_one_arm_reverse_grip_curl.mp4', 'Bíceps/cable_one_arm_reverse_grip_curl.webp'),   -- cable_one_arm_reverse_grip_curl
    ('925a7c67-3bdc-52c2-8687-d7a7716e39ba'::uuid, 'Bíceps/high_cable_one_arm_biceps_curl.mp4', 'Bíceps/high_cable_one_arm_biceps_curl.webp'),   -- high_cable_one_arm_biceps_curl
    ('f13ff710-c39e-5d2b-91dc-3565ce3c37e3'::uuid, 'Bíceps/dumbbell_arm_blaster_curl.mp4', 'Bíceps/dumbbell_arm_blaster_curl.webp'),   -- dumbbell_arm_blaster_curl
    ('6d8e31aa-f88c-5c56-9482-b73e80f198cd'::uuid, 'Bíceps/high_cable_double_biceps_curl.mp4', 'Bíceps/high_cable_double_biceps_curl.webp'),   -- high_cable_double_biceps_curl
    ('39af9068-d462-50e3-a9ff-086c4128e4f8'::uuid, 'Bíceps/dumbbell_alternating_curl.mp4', 'Bíceps/dumbbell_alternating_curl.webp'),   -- dumbbell_alternating_curl
    ('b7c511ee-1e49-5eac-8b3f-b0c5792472fc'::uuid, 'Bíceps/dumbbell_concentration_curl.mp4', 'Bíceps/dumbbell_concentration_curl.webp'),   -- dumbbell_concentration_curl
    ('216e10d4-5a43-50b9-8504-bcd38df6f115'::uuid, 'Bíceps/cable_concentration_curl.mp4', 'Bíceps/cable_concentration_curl.webp'),   -- cable_concentration_curl
    ('b4696d4e-6e8b-5a53-8215-5848a5691bb0'::uuid, 'Calistenia/leg_resisted_concentration_curl.mp4', 'Calistenia/leg_resisted_concentration_curl.webp'),   -- leg_resisted_concentration_curl
    ('7178fa81-0c88-5733-8fb9-a406c24dad8e'::uuid, 'Bíceps/cable_one_arm_preacher_curl.mp4', 'Bíceps/cable_one_arm_preacher_curl.webp'),   -- cable_one_arm_preacher_curl
    ('eb285cff-f74c-5a79-9fef-10206236ba4b'::uuid, 'Bíceps/standing_dumbbell_one_arm_preacher_curl.mp4', 'Bíceps/standing_dumbbell_one_arm_preacher_curl.webp'),   -- standing_dumbbell_one_arm_preacher_curl
    ('e1d69b12-4d78-5ab5-a85b-c9517d42b4c3'::uuid, 'Bíceps/seated_cable_overhead_biceps_curl.mp4', 'Bíceps/seated_cable_overhead_biceps_curl.webp'),   -- seated_cable_overhead_biceps_curl
    ('f2578383-69d6-52f0-a2f1-e331d02c4aa3'::uuid, 'Antebraços/dumbbell_finger_curl.mp4', 'Antebraços/dumbbell_finger_curl.webp'),   -- dumbbell_finger_curl
    ('e9eb1fc8-395e-5514-a1d6-b7688f1dbe41'::uuid, 'Antebraços/weight_plate_neutral_wrist_curl.mp4', 'Antebraços/weight_plate_neutral_wrist_curl.webp'),   -- weight_plate_neutral_wrist_curl
    ('4b2e0279-1103-5be9-a0fd-9f8aaf5f2a76'::uuid, 'Antebraços/seated_barbell_reverse_wrist_curl.mp4', 'Antebraços/seated_barbell_reverse_wrist_curl.webp'),   -- seated_barbell_reverse_wrist_curl
    ('366c1aaa-e85b-5b0b-aa8d-6cf5e47aefd7'::uuid, 'Antebraços/barbell_behind_the_back_wrist_curl.mp4', 'Antebraços/barbell_behind_the_back_wrist_curl.webp'),   -- barbell_behind_the_back_wrist_curl
    ('c00739a0-dd76-533e-ae49-9bb6deea7dad'::uuid, 'Bíceps/lever_biceps_curl.mp4', 'Bíceps/lever_biceps_curl.webp'),   -- lever_biceps_curl
    ('4c95ef07-5d9f-5135-ac1f-dae1c2c5adc4'::uuid, 'Funcional e HIT/band_one_arm_biceps_curl.mp4', 'Funcional e HIT/band_one_arm_biceps_curl.webp'),   -- band_one_arm_biceps_curl
    ('557be3ac-8eae-532c-9eca-2b1e5e678eb3'::uuid, 'Antebraços/barbell_behind_the_back_finger_curl.mp4', 'Antebraços/barbell_behind_the_back_finger_curl.webp'),   -- barbell_behind_the_back_finger_curl
    ('f567e86b-b5ac-551f-963e-2592d3b30c52'::uuid, 'Antebraços/barbell_wrist_curl_over_bench.mp4', 'Antebraços/barbell_wrist_curl_over_bench.webp'),   -- barbell_wrist_curl_over_bench
    ('c1df1689-c819-5573-9ea5-41c1ab08af9e'::uuid, 'Bíceps/ez_bar_curl.mp4', 'Bíceps/ez_bar_curl.webp'),   -- ez_bar_curl
    ('39eb3723-cc94-5b18-9590-53a191964d8e'::uuid, 'Bíceps/ez_bar_reverse_curl.mp4', 'Bíceps/ez_bar_reverse_curl.webp'),   -- ez_bar_reverse_curl
    ('82a5cc98-e1dc-5c54-a305-9b913cbbdb59'::uuid, 'Bíceps/dumbbell_hammer_curl.mp4', 'Bíceps/dumbbell_hammer_curl.webp'),   -- dumbbell_hammer_curl
    ('eded16c1-1374-52fd-89eb-19a0d9166a51'::uuid, 'Bíceps/cable_rope_hammer_curl.mp4', 'Bíceps/cable_rope_hammer_curl.webp'),   -- cable_rope_hammer_curl
    ('fc056dd4-c8fe-5113-942e-958b5069fe49'::uuid, 'Funcional e HIT/band_hammer_curl.mp4', 'Funcional e HIT/band_hammer_curl.webp'),   -- band_hammer_curl
    ('db1de041-477c-54eb-a4ff-6e2e358f33b4'::uuid, 'Funcional e HIT/water_bottle_hammer_curl.mp4', 'Funcional e HIT/water_bottle_hammer_curl.webp'),   -- water_bottle_hammer_curl
    ('f6015671-2a31-573d-a809-48e95bdca42a'::uuid, 'Bíceps/dumbbell_one_arm_hammer_preacher_curl.mp4', 'Bíceps/dumbbell_one_arm_hammer_preacher_curl.webp'),   -- dumbbell_one_arm_hammer_preacher_curl
    ('46896ead-cef1-5bcd-a016-dd784f35cf11'::uuid, 'Bíceps/seated_dumbbell_hammer_curl.mp4', 'Bíceps/seated_dumbbell_hammer_curl.webp'),   -- seated_dumbbell_hammer_curl
    ('066cabdb-125b-59fc-a0e5-6adb648c646b'::uuid, 'Bíceps/cable_straight_bar_curl.mp4', 'Bíceps/cable_straight_bar_curl.webp'),   -- cable_straight_bar_curl
    ('429c319c-6d4d-55f5-af2e-d6c5333c9237'::uuid, 'Bíceps/barbell_prone_incline_curl.mp4', 'Bíceps/barbell_prone_incline_curl.webp'),   -- barbell_prone_incline_curl
    ('07e06aa1-6bc1-5909-a375-3010b132572a'::uuid, 'Bíceps/dumbbell_alternating_preacher_curl.mp4', 'Bíceps/dumbbell_alternating_preacher_curl.webp'),   -- dumbbell_alternating_preacher_curl
    ('7e6adc01-d07f-52f9-bf73-a4944bfcf62f'::uuid, 'Bíceps/dumbbell_preacher_curl.mp4', 'Bíceps/dumbbell_preacher_curl.webp'),   -- dumbbell_preacher_curl
    ('e25e055f-37e7-58e4-a739-b8287d44bb51'::uuid, 'Bíceps/standing_dumbbell_one_arm_incline_preacher_curl.mp4', 'Bíceps/standing_dumbbell_one_arm_incline_preacher_curl.webp'),   -- standing_dumbbell_one_arm_incline_preacher_curl
    ('71619419-a1ec-5cc1-8aa4-d7272e186c71'::uuid, 'Bíceps/standing_dumbbell_one_arm_spider_curl.mp4', 'Bíceps/standing_dumbbell_one_arm_spider_curl.webp'),   -- standing_dumbbell_one_arm_spider_curl
    ('c8210b9c-8cf6-5e52-abba-3a5a05af14cc'::uuid, 'Bíceps/prone_dumbbell_one_arm_spider_curl.mp4', 'Bíceps/prone_dumbbell_one_arm_spider_curl.webp'),   -- prone_dumbbell_one_arm_spider_curl
    ('7faf0cd0-8344-588a-8e10-9f97d6452729'::uuid, 'Mobilidade/prone_band_hip_external_rotation.mp4', 'Mobilidade/prone_band_hip_external_rotation.webp'),   -- prone_band_hip_external_rotation
    ('54874389-6e2d-54f1-b1cb-2a779a73db69'::uuid, 'Mobilidade/cable_shoulder_external_rotation.mp4', 'Mobilidade/cable_shoulder_external_rotation.webp'),   -- cable_shoulder_external_rotation
    ('804f4ddc-7ba1-55bc-8007-aaa44913b9b0'::uuid, 'Mobilidade/seated_band_hip_external_rotation.mp4', 'Mobilidade/seated_band_hip_external_rotation.webp'),   -- seated_band_hip_external_rotation
    ('23ed3768-d22d-54dd-add2-eb504aad9d76'::uuid, 'Mobilidade/band_foot_external_rotation.mp4', 'Mobilidade/band_foot_external_rotation.webp'),   -- band_foot_external_rotation
    ('f58641e7-cf2f-56bd-8e36-56d82037f610'::uuid, 'Mobilidade/seated_band_hip_internal_rotation.mp4', 'Mobilidade/seated_band_hip_internal_rotation.webp'),   -- seated_band_hip_internal_rotation
    ('bd6e7c43-ac23-5579-8bbf-bc623e9c1091'::uuid, 'Mobilidade/quadruped_thoracic_rotation.mp4', 'Mobilidade/quadruped_thoracic_rotation.webp'),   -- quadruped_thoracic_rotation
    ('ca7df7b6-d04c-592b-9af0-e65457bd02c5'::uuid, 'Mobilidade/seated_ankle_circles.mp4', 'Mobilidade/seated_ankle_circles.webp'),   -- seated_ankle_circles
    ('dcab081d-1fff-56cf-92b3-7808f1f029bc'::uuid, 'Mobilidade/open_book_stretch.mp4', 'Mobilidade/open_book_stretch.webp'),   -- open_book_stretch
    ('5205aca8-503b-54ef-9b94-2a98a6155367'::uuid, 'Mobilidade/standing_trunk_rotation.mp4', 'Mobilidade/standing_trunk_rotation.webp'),   -- standing_trunk_rotation
    ('c1c74f56-3fd4-5f32-94ab-68672cd12fba'::uuid, 'Mobilidade/supine_spinal_twist.mp4', 'Mobilidade/supine_spinal_twist.webp'),   -- supine_spinal_twist
    ('c84465b1-c7be-5355-8702-79d7709b5d3d'::uuid, 'Mobilidade/cable_90_shoulder_external_rotation.mp4', 'Mobilidade/cable_90_shoulder_external_rotation.webp'),   -- cable_90_shoulder_external_rotation
    ('b29770af-0784-5525-af0d-0bbbce02a2a8'::uuid, 'Mobilidade/kneeling_cable_shoulder_external_rotation.mp4', 'Mobilidade/kneeling_cable_shoulder_external_rotation.webp'),   -- kneeling_cable_shoulder_external_rotation
    ('d8540fb3-270b-52ba-b158-0d726eb81150'::uuid, 'Mobilidade/bench_supported_dumbbell_external_rotation.mp4', 'Mobilidade/bench_supported_dumbbell_external_rotation.webp'),   -- bench_supported_dumbbell_external_rotation
    ('f22d2050-982e-5673-8ca1-da6dbf29bc81'::uuid, 'Mobilidade/band_shoulder_external_rotation.mp4', 'Mobilidade/band_shoulder_external_rotation.webp'),   -- band_shoulder_external_rotation
    ('0679cf7c-0864-58f6-be6a-631e24d751ff'::uuid, 'Mobilidade/bodyweight_shoulder_external_rotation.mp4', 'Mobilidade/bodyweight_shoulder_external_rotation.webp'),   -- bodyweight_shoulder_external_rotation
    ('6eddc2ec-81d0-5187-8ea5-01445cc3129e'::uuid, 'Mobilidade/side_lying_dumbbell_external_rotation.mp4', 'Mobilidade/side_lying_dumbbell_external_rotation.webp'),   -- side_lying_dumbbell_external_rotation
    ('23c81ca4-e1ba-5069-b77b-58cdbf68a9c9'::uuid, 'Mobilidade/cable_90_shoulder_internal_rotation.mp4', 'Mobilidade/cable_90_shoulder_internal_rotation.webp'),   -- cable_90_shoulder_internal_rotation
    ('633de9fd-0a7f-5525-948a-62f8329a4dfe'::uuid, 'Mobilidade/cable_shoulder_internal_rotation.mp4', 'Mobilidade/cable_shoulder_internal_rotation.webp'),   -- cable_shoulder_internal_rotation
    ('8061f920-eb0a-5aa2-af46-e8f3dba4d8f6'::uuid, 'Mobilidade/bodyweight_shoulder_internal_rotation.mp4', 'Mobilidade/bodyweight_shoulder_internal_rotation.webp'),   -- bodyweight_shoulder_internal_rotation
    ('77558690-a9d2-5792-83d6-8a0874c7c4f4'::uuid, 'Mobilidade/seated_cable_shoulder_internal_rotation.mp4', 'Mobilidade/seated_cable_shoulder_internal_rotation.webp'),   -- seated_cable_shoulder_internal_rotation
    ('b9c3f397-f3e9-5dbc-b6e4-0dd534e18829'::uuid, 'Mobilidade/quadruped_thoracic_reach_through_rotation.mp4', 'Mobilidade/quadruped_thoracic_reach_through_rotation.webp'),   -- quadruped_thoracic_reach_through_rotation
    ('2005c6cf-ef3a-595d-9615-74ae85156aac'::uuid, 'Funcional e HIT/tuck_jump.mp4', 'Funcional e HIT/tuck_jump.webp'),   -- tuck_jump
    ('4f6eec95-c2dc-5834-bfcd-ec0e5237ff38'::uuid, 'Funcional e HIT/dumbbell_jumping_split_squat.mp4', 'Funcional e HIT/dumbbell_jumping_split_squat.webp'),   -- dumbbell_jumping_split_squat
    ('267c16db-f012-5bca-96cd-7a3af99f32c8'::uuid, 'Funcional e HIT/squat_tuck_jump.mp4', 'Funcional e HIT/squat_tuck_jump.webp'),   -- squat_tuck_jump
    ('538e764e-59a5-584b-9cd3-d7dbbcef9cc1'::uuid, 'Calistenia/single_leg_box_jump.mp4', 'Calistenia/single_leg_box_jump.webp'),   -- single_leg_box_jump
    ('9c207c86-3b3c-5eb7-b38b-1f43d02ea5a8'::uuid, 'Calistenia/broad_jump.mp4', 'Calistenia/broad_jump.webp'),   -- broad_jump
    ('2e79386f-01d8-576c-82e1-be1edc66c476'::uuid, 'Funcional e HIT/single_leg_forward_hop.mp4', 'Funcional e HIT/single_leg_forward_hop.webp'),   -- single_leg_forward_hop
    ('f272c8be-e8fe-5c04-aabe-337822a1f8ac'::uuid, 'Crossfit/box_jump.mp4', 'Crossfit/box_jump.webp'),   -- box_jump
    ('fed751fe-36ea-5a84-814b-7741561b04de'::uuid, 'Calistenia/box_jump_to_pistol_squat.mp4', 'Calistenia/box_jump_to_pistol_squat.webp'),   -- box_jump_to_pistol_squat
    ('85ed0b16-03bf-5d64-8740-fd401ebdde4a'::uuid, 'Calistenia/two_to_one_box_jump.mp4', 'Calistenia/two_to_one_box_jump.webp'),   -- two_to_one_box_jump
    ('5cb973e0-ea61-56b7-95ae-563dd2210343'::uuid, 'Funcional e HIT/backward_jump.mp4', 'Funcional e HIT/backward_jump.webp'),   -- backward_jump
    ('a2dd9e69-5e2d-5118-b74d-24085c5f7855'::uuid, 'Funcional e HIT/zigzag_plyometric_jumps.mp4', 'Funcional e HIT/zigzag_plyometric_jumps.webp'),   -- zigzag_plyometric_jumps
    ('c556da0f-cd95-584d-8155-1565d10d3ba1'::uuid, 'Funcional e HIT/power_skip.mp4', 'Funcional e HIT/power_skip.webp'),   -- power_skip
    ('26126483-2fe7-57f8-a5ce-b19fb13568fa'::uuid, 'Funcional e HIT/seal_jack.mp4', 'Funcional e HIT/seal_jack.webp'),   -- seal_jack
    ('6bd6141b-0de9-5ae2-98c8-27f6f6d84664'::uuid, 'Funcional e HIT/scissor_jumps.mp4', 'Funcional e HIT/scissor_jumps.webp'),   -- scissor_jumps
    ('d08d2546-09aa-58ec-8c83-9760ce45a75d'::uuid, 'Costas/one_arm_dumbbell_row.mp4', 'Costas/one_arm_dumbbell_row.webp'),   -- one_arm_dumbbell_row
    ('1f683476-b9bb-5535-8a9e-03f979c46ae7'::uuid, 'Funcional e HIT/snap_jump.mp4', 'Funcional e HIT/snap_jump.webp'),   -- snap_jump
    ('541ae273-16d5-56c0-b3b1-5bcdb88be2e8'::uuid, 'Funcional e HIT/right_straight_punch_heavy_bag.mp4', 'Funcional e HIT/right_straight_punch_heavy_bag.webp'),   -- right_straight_punch_heavy_bag
    ('0ad063e5-9d6b-593d-8212-6913fddb678a'::uuid, 'Funcional e HIT/alternating_punches.mp4', 'Funcional e HIT/alternating_punches.webp'),   -- alternating_punches
    ('817faae2-2f92-5c08-9f1d-f522f27776b0'::uuid, 'Funcional e HIT/band_step_up.mp4', 'Funcional e HIT/band_step_up.webp'),   -- band_step_up
    ('ab73e776-764f-589c-9fe1-d795693cbb5f'::uuid, 'Pernas/barbell_single_leg_romanian_deadlift.mp4', 'Pernas/barbell_single_leg_romanian_deadlift.webp'),   -- barbell_single_leg_romanian_deadlift
    ('82691a8d-9cc8-5c4d-8075-860b5277a2a8'::uuid, 'Pernas/dumbbell_single_leg_romanian_deadlift.mp4', 'Pernas/dumbbell_single_leg_romanian_deadlift.webp'),   -- dumbbell_single_leg_romanian_deadlift
    ('50ae5b88-2328-5caf-8bef-600ce6eecd60'::uuid, 'Funcional e HIT/band_single_leg_romanian_deadlift.mp4', 'Funcional e HIT/band_single_leg_romanian_deadlift.webp'),   -- band_single_leg_romanian_deadlift
    ('f56eb755-edda-573c-bf04-7103f71d43f0'::uuid, 'Pernas/dumbbell_stiff_leg_deadlift.mp4', 'Pernas/dumbbell_stiff_leg_deadlift.webp'),   -- dumbbell_stiff_leg_deadlift
    ('30a50289-16a6-58df-962f-e6197078f449'::uuid, 'Pernas/barbell_stiff_leg_deadlift.mp4', 'Pernas/barbell_stiff_leg_deadlift.webp'),   -- barbell_stiff_leg_deadlift
    ('959f3ed4-fb9e-59a3-9585-5f320e2aa347'::uuid, 'Crossfit/legless_rope_climb.mp4', 'Crossfit/legless_rope_climb.webp'),   -- legless_rope_climb
    ('1eda2d16-64d4-5953-8621-900472a3bbf0'::uuid, 'Funcional e HIT/bench_step_up_with_knee_raise.mp4', 'Funcional e HIT/bench_step_up_with_knee_raise.webp'),   -- bench_step_up_with_knee_raise
    ('f441cf41-0b2c-599c-a284-269ee3271f9b'::uuid, 'Eretor Lombar/superman.mp4', 'Eretor Lombar/superman.webp'),   -- superman
    ('d1b3ed40-e89d-517a-9fd2-35a8bc90f8db'::uuid, 'Peitoral/dumbbell_alternating_bench_press.mp4', 'Peitoral/dumbbell_alternating_bench_press.webp'),   -- dumbbell_alternating_bench_press
    ('1fda0ab6-849f-5551-9a5f-6fc692e5bbb6'::uuid, 'Peitoral/one_arm_decline_neutral_grip_dumbbell_press.mp4', 'Peitoral/one_arm_decline_neutral_grip_dumbbell_press.webp'),   -- one_arm_decline_neutral_grip_dumbbell_press
    ('1de1de77-70da-5930-89cd-4bcb63c7712d'::uuid, 'Peitoral/dumbbell_decline_bench_press.mp4', 'Peitoral/dumbbell_decline_bench_press.webp'),   -- dumbbell_decline_bench_press
    ('43e06a26-a9a8-5566-b147-d519b63656af'::uuid, 'Peitoral/plate_loaded_decline_chest_press.mp4', 'Peitoral/plate_loaded_decline_chest_press.webp'),   -- plate_loaded_decline_chest_press
    ('246fba59-abf3-58f1-8e45-7c8003629e67'::uuid, 'Tríceps/close_grip_bench_press.mp4', 'Tríceps/close_grip_bench_press.webp'),   -- close_grip_bench_press
    ('67d1b649-59d0-5808-8f8f-e670e606c601'::uuid, 'Peitoral/dumbbell_incline_bench_press.mp4', 'Peitoral/dumbbell_incline_bench_press.webp'),   -- dumbbell_incline_bench_press
    ('0c487a2a-1d46-5bd6-81be-0fb318af69fa'::uuid, 'Peitoral/dumbbell_incline_reverse_grip_press.mp4', 'Peitoral/dumbbell_incline_reverse_grip_press.webp'),   -- dumbbell_incline_reverse_grip_press
    ('6ba4dd2f-cf1e-500b-baae-ac5f2b2a81d1'::uuid, 'Peitoral/dumbbell_incline_neutral_grip_press.mp4', 'Peitoral/dumbbell_incline_neutral_grip_press.webp'),   -- dumbbell_incline_neutral_grip_press
    ('2d20a929-9c1c-5b42-958e-8050e94c78af'::uuid, 'Peitoral/machine_incline_neutral_grip_press.mp4', 'Peitoral/machine_incline_neutral_grip_press.webp'),   -- machine_incline_neutral_grip_press
    ('eb9418fd-0ba9-5440-8ca7-21a26bfd5f56'::uuid, 'Tríceps/reverse_grip_close_bench_press.mp4', 'Tríceps/reverse_grip_close_bench_press.webp'),   -- reverse_grip_close_bench_press
    ('2aaf07b6-ae05-523b-ae5f-f6e7d515739c'::uuid, 'Peitoral/barbell_bench_press.mp4', 'Peitoral/barbell_bench_press.webp'),   -- barbell_bench_press
    ('a051ab20-e7c5-51db-8850-52dd33627c2a'::uuid, 'Peitoral/plate_loaded_flat_chest_press.mp4', 'Peitoral/plate_loaded_flat_chest_press.webp'),   -- plate_loaded_flat_chest_press
    ('1b574cb3-8324-5d9d-9626-e78d9993cffa'::uuid, 'Peitoral/one_arm_reverse_grip_dumbbell_press.mp4', 'Peitoral/one_arm_reverse_grip_dumbbell_press.webp'),   -- one_arm_reverse_grip_dumbbell_press
    ('7b823dc6-579a-5ffa-b56a-b2a8dc420902'::uuid, 'Peitoral/standing_one_arm_cable_press.mp4', 'Peitoral/standing_one_arm_cable_press.webp'),   -- standing_one_arm_cable_press
    ('e330f1f9-1486-59f5-a696-39e7dbb1dc1f'::uuid, 'Peitoral/plate_svend_press.mp4', 'Peitoral/plate_svend_press.webp'),   -- plate_svend_press
    ('2dec0f86-3318-5994-b75b-57c17ede6194'::uuid, 'Peitoral/dumbbell_bench_press.mp4', 'Peitoral/dumbbell_bench_press.webp'),   -- dumbbell_bench_press
    ('a7354fa7-4bd1-5140-824d-7fe79870a311'::uuid, 'Peitoral/dumbbell_reverse_grip_bench_press.mp4', 'Peitoral/dumbbell_reverse_grip_bench_press.webp'),   -- dumbbell_reverse_grip_bench_press
    ('47e85043-c9e4-5b13-b1b0-c98fb6ccf55c'::uuid, 'Peitoral/seated_close_grip_cable_press.mp4', 'Peitoral/seated_close_grip_cable_press.webp'),   -- seated_close_grip_cable_press
    ('a77cd698-2d0c-58b3-a375-b34404188464'::uuid, 'Peitoral/smith_machine_incline_bench_press.mp4', 'Peitoral/smith_machine_incline_bench_press.webp'),   -- smith_machine_incline_bench_press
    ('03059786-28ba-54b6-a7eb-44015492dc57'::uuid, 'Peitoral/barbell_decline_bench_press.mp4', 'Peitoral/barbell_decline_bench_press.webp'),   -- barbell_decline_bench_press
    ('020e5caa-570f-5373-ae4e-63457e8c022a'::uuid, 'Peitoral/barbell_floor_press.mp4', 'Peitoral/barbell_floor_press.webp'),   -- barbell_floor_press
    ('b6c5102a-c80c-50ec-bd8a-c245ca5c6a50'::uuid, 'Peitoral/seated_cable_chest_press.mp4', 'Peitoral/seated_cable_chest_press.webp'),   -- seated_cable_chest_press
    ('c1eaaff2-1187-5fc3-94f2-ded47c7c9207'::uuid, 'Peitoral/single_dumbbell_close_grip_press.mp4', 'Peitoral/single_dumbbell_close_grip_press.webp'),   -- single_dumbbell_close_grip_press
    ('0c82c4f0-ed5a-59ff-b41c-94c15e04f9e7'::uuid, 'Peitoral/dumbbell_squeeze_press.mp4', 'Peitoral/dumbbell_squeeze_press.webp'),   -- dumbbell_squeeze_press
    ('e909ab84-4fe5-5470-b8cb-50ccdefe6d36'::uuid, 'Peitoral/one_arm_kettlebell_bench_press.mp4', 'Peitoral/one_arm_kettlebell_bench_press.webp'),   -- one_arm_kettlebell_bench_press
    ('90689b82-eed4-5aff-b046-396780a6a8b1'::uuid, 'Peitoral/kettlebell_floor_press.mp4', 'Peitoral/kettlebell_floor_press.webp'),   -- kettlebell_floor_press
    ('0916acf3-957a-5e95-8c7a-60aea75851e9'::uuid, 'Peitoral/wide_grip_barbell_bench_press.mp4', 'Peitoral/wide_grip_barbell_bench_press.webp'),   -- wide_grip_barbell_bench_press
    ('4a1133f0-f259-5081-aac3-73497f6037cb'::uuid, 'Peitoral/close_grip_barbell_bench_press.mp4', 'Peitoral/close_grip_barbell_bench_press.webp'),   -- close_grip_barbell_bench_press
    ('83e4f7e6-44ef-5216-9a51-5ebe9f2c4802'::uuid, 'Peitoral/smith_machine_decline_bench_press.mp4', 'Peitoral/smith_machine_decline_bench_press.webp'),   -- smith_machine_decline_bench_press
    ('42badfbc-0cfd-57ee-8549-fd9d8954eddc'::uuid, 'Peitoral/dumbbell_decline_neutral_grip_press.mp4', 'Peitoral/dumbbell_decline_neutral_grip_press.webp'),   -- dumbbell_decline_neutral_grip_press
    ('485f5e73-8f3c-5306-bc1f-2bda0f93e5d4'::uuid, 'Funcional e HIT/standing_band_chest_press.mp4', 'Funcional e HIT/standing_band_chest_press.webp'),   -- standing_band_chest_press
    ('06a40e7c-7a54-5991-a75c-1001d93d0572'::uuid, 'Peitoral/barbell_incline_bench_press.mp4', 'Peitoral/barbell_incline_bench_press.webp'),   -- barbell_incline_bench_press
    ('bfa09a60-72f6-5635-841b-9e16e0489ebb'::uuid, 'Peitoral/cable_incline_chest_press.mp4', 'Peitoral/cable_incline_chest_press.webp'),   -- cable_incline_chest_press
    ('980ea236-7c4c-5466-94e2-dd1713719195'::uuid, 'Peitoral/dumbbell_incline_close_grip_press.mp4', 'Peitoral/dumbbell_incline_close_grip_press.webp'),   -- dumbbell_incline_close_grip_press
    ('397ccf8b-f36d-50e2-872c-e14e2297b6f3'::uuid, 'Peitoral/barbell_incline_close_grip_press.mp4', 'Peitoral/barbell_incline_close_grip_press.webp'),   -- barbell_incline_close_grip_press
    ('bf50b1f9-7467-502c-a7e6-4090ba56db8d'::uuid, 'Peitoral/plate_loaded_incline_chest_press.mp4', 'Peitoral/plate_loaded_incline_chest_press.webp'),   -- plate_loaded_incline_chest_press
    ('b946c52f-7a32-5a72-bb0b-d8799bc40b70'::uuid, 'Peitoral/wide_grip_barbell_decline_press.mp4', 'Peitoral/wide_grip_barbell_decline_press.webp'),   -- wide_grip_barbell_decline_press
    ('131db371-64fd-5e01-843f-a5fb7560b329'::uuid, 'Peitoral/machine_chest_press.mp4', 'Peitoral/machine_chest_press.webp'),   -- machine_chest_press
    ('94f2ce78-68b3-588e-b3e9-50dc1375db30'::uuid, 'Peitoral/smith_machine_bench_press.mp4', 'Peitoral/smith_machine_bench_press.webp'),   -- smith_machine_bench_press
    ('4ab7d891-0bb1-59cd-9892-01538359a333'::uuid, 'Peitoral/dumbbell_30_incline_reverse_grip_press.mp4', 'Peitoral/dumbbell_30_incline_reverse_grip_press.webp'),   -- dumbbell_30_incline_reverse_grip_press
    ('0bcd8665-9c45-519e-99af-beb7126b66ad'::uuid, 'Peitoral/smith_machine_close_grip_bench_press.mp4', 'Peitoral/smith_machine_close_grip_bench_press.webp'),   -- smith_machine_close_grip_bench_press
    ('cb9c710e-6392-5d87-a16f-f7e4054178bf'::uuid, 'Peitoral/dumbbell_neutral_grip_bench_press.mp4', 'Peitoral/dumbbell_neutral_grip_bench_press.webp'),   -- dumbbell_neutral_grip_bench_press
    ('a3246f0c-0846-58a8-af1c-10caecbe5215'::uuid, 'Peitoral/standing_cable_chest_press.mp4', 'Peitoral/standing_cable_chest_press.webp'),   -- standing_cable_chest_press
    ('ca5e42ed-31ae-5f96-993e-59f2a9aed9e2'::uuid, 'Peitoral/lever_one_arm_chest_press.mp4', 'Peitoral/lever_one_arm_chest_press.webp'),   -- lever_one_arm_chest_press
    ('76e837d3-cac1-5929-8492-06729541152a'::uuid, 'Calistenia/dead_hang.mp4', 'Calistenia/dead_hang.webp'),   -- dead_hang
    ('650ec061-851b-5e11-8189-dce068f096f3'::uuid, 'Calistenia/bar_swing_360.mp4', 'Calistenia/bar_swing_360.webp'),   -- bar_swing_360
    ('029a19c9-3c0f-5715-8d0c-940e89ee1345'::uuid, 'Crossfit/kettlebell_swing.mp4', 'Crossfit/kettlebell_swing.webp'),   -- kettlebell_swing
    ('5a55c7be-9a46-5e36-9f45-e5a437355362'::uuid, 'Crossfit/kettlebell_one_arm_swing.mp4', 'Crossfit/kettlebell_one_arm_swing.webp'),   -- kettlebell_one_arm_swing
    ('64a1a2e5-6f2e-59a4-a498-222cfebf4281'::uuid, 'Funcional e HIT/arm_scissors.mp4', 'Funcional e HIT/arm_scissors.webp'),   -- arm_scissors
    ('2c00279b-91e5-5ce4-84b9-28c93ecf0711'::uuid, 'Mobilidade/standing_side_toe_touch.mp4', 'Mobilidade/standing_side_toe_touch.webp'),   -- standing_side_toe_touch
    ('644989d0-8774-572f-aa4c-c57df35557f8'::uuid, 'Mobilidade/seated_toe_touch.mp4', 'Mobilidade/seated_toe_touch.webp'),   -- seated_toe_touch
    ('1490123f-9c8a-551b-8a3c-fd5df396dfd4'::uuid, 'Mobilidade/standing_toe_touch.mp4', 'Mobilidade/standing_toe_touch.webp'),   -- standing_toe_touch
    ('6c82c945-f68d-505e-b02c-a9e3f073cd46'::uuid, 'Mobilidade/seated_oblique_twist.mp4', 'Mobilidade/seated_oblique_twist.webp'),   -- seated_oblique_twist
    ('81e9634a-b2f4-55af-865d-df78074cbcd8'::uuid, 'Funcional e HIT/standing_elbow_to_knee_twist.mp4', 'Funcional e HIT/standing_elbow_to_knee_twist.webp'),   -- standing_elbow_to_knee_twist
    ('68dc2b56-ee43-5d5f-afb8-2e41bb752d0c'::uuid, 'Funcional e HIT/band_one_arm_horizontal_abduction.mp4', 'Funcional e HIT/band_one_arm_horizontal_abduction.webp'),   -- band_one_arm_horizontal_abduction
    ('bcbf0fdb-5302-58e6-9d37-db7a83cbdb8f'::uuid, 'Tríceps/cable_triceps_kickback.mp4', 'Tríceps/cable_triceps_kickback.webp'),   -- cable_triceps_kickback
    ('77e5ed68-da7e-5e55-9ea6-3a51ab625535'::uuid, 'Tríceps/dumbbell_triceps_kickback.mp4', 'Tríceps/dumbbell_triceps_kickback.webp'),   -- dumbbell_triceps_kickback
    ('29ee0051-b2df-52f0-b200-06cc511da64e'::uuid, 'Tríceps/incline_alternating_dumbbell_skull_crusher.mp4', 'Tríceps/incline_alternating_dumbbell_skull_crusher.webp'),   -- incline_alternating_dumbbell_skull_crusher
    ('d9402e02-3fc5-5c71-9736-4b0c6e7513db'::uuid, 'Funcional e HIT/band_overhead_triceps_extension.mp4', 'Funcional e HIT/band_overhead_triceps_extension.webp'),   -- band_overhead_triceps_extension
    ('496e37d5-bcad-5bb1-920c-bcd78ebcd0e1'::uuid, 'Tríceps/seated_two_hand_dumbbell_overhead_extension.mp4', 'Tríceps/seated_two_hand_dumbbell_overhead_extension.webp'),   -- seated_two_hand_dumbbell_overhead_extension
    ('2d94d612-5d46-5e4b-b04b-4fdecbaa5ce9'::uuid, 'Tríceps/standing_dumbbell_overhead_extension.mp4', 'Tríceps/standing_dumbbell_overhead_extension.webp'),   -- standing_dumbbell_overhead_extension
    ('51843476-c141-5d64-8e54-a2ac8fb0b421'::uuid, 'Funcional e HIT/standing_gymstick_overhead_triceps_extension.mp4', 'Funcional e HIT/standing_gymstick_overhead_triceps_extension.webp'),   -- standing_gymstick_overhead_triceps_extension
    ('fa23d7d7-ddf0-51cd-88fd-b9768ac9ee25'::uuid, 'Tríceps/v_bar_pushdown.mp4', 'Tríceps/v_bar_pushdown.webp'),   -- v_bar_pushdown
    ('ee02839b-d65a-51be-bbbd-6bab3f7ff224'::uuid, 'Tríceps/decline_dumbbell_skull_crusher.mp4', 'Tríceps/decline_dumbbell_skull_crusher.webp'),   -- decline_dumbbell_skull_crusher
    ('7f413c6b-a10a-50f6-9d90-a22a1334d0ad'::uuid, 'Tríceps/reverse_grip_barbell_skull_crusher.mp4', 'Tríceps/reverse_grip_barbell_skull_crusher.webp'),   -- reverse_grip_barbell_skull_crusher
    ('1c3d908a-b8a6-51f2-a8b1-ab83eba120a7'::uuid, 'Funcional e HIT/band_lying_triceps_extension.mp4', 'Funcional e HIT/band_lying_triceps_extension.webp'),   -- band_lying_triceps_extension
    ('79f8d96a-d315-53a9-b3e8-94fb9c57a9e1'::uuid, 'Tríceps/seated_ez_bar_overhead_extension.mp4', 'Tríceps/seated_ez_bar_overhead_extension.webp'),   -- seated_ez_bar_overhead_extension
    ('16d85db7-9e37-55ed-acde-7896edd58201'::uuid, 'Tríceps/cable_rope_overhead_extension.mp4', 'Tríceps/cable_rope_overhead_extension.webp'),   -- cable_rope_overhead_extension
    ('4df98d3c-f823-590d-958d-8c87c9955550'::uuid, 'Tríceps/incline_dumbbell_overhead_extension.mp4', 'Tríceps/incline_dumbbell_overhead_extension.webp'),   -- incline_dumbbell_overhead_extension
    ('d5b218e5-a7b2-5505-9fa1-8414b010eb03'::uuid, 'Tríceps/cable_one_arm_overhead_extension.mp4', 'Tríceps/cable_one_arm_overhead_extension.webp'),   -- cable_one_arm_overhead_extension
    ('cb1e8843-2491-5594-9f77-19c945490553'::uuid, 'Tríceps/bench_dip.mp4', 'Tríceps/bench_dip.webp'),   -- bench_dip
    ('01fa2cb7-d8ce-593b-be0b-1072667c738c'::uuid, 'Tríceps/straight_bar_pushdown.mp4', 'Tríceps/straight_bar_pushdown.webp'),   -- straight_bar_pushdown
    ('2794cf73-df14-50fa-8d74-466b74f87429'::uuid, 'Tríceps/rope_pushdown.mp4', 'Tríceps/rope_pushdown.webp'),   -- rope_pushdown
    ('a03a9c76-78c6-59c1-8e81-e32f70e7e8b0'::uuid, 'Tríceps/reverse_grip_pushdown.mp4', 'Tríceps/reverse_grip_pushdown.webp'),   -- reverse_grip_pushdown
    ('e5d40626-2cc3-543a-b57a-0804bfda605f'::uuid, 'Tríceps/barbell_skull_crusher.mp4', 'Tríceps/barbell_skull_crusher.webp'),   -- barbell_skull_crusher
    ('c197b4ab-8a2f-552e-94e0-4bba22cb2ae8'::uuid, 'Tríceps/neutral_grip_dumbbell_skull_crusher.mp4', 'Tríceps/neutral_grip_dumbbell_skull_crusher.webp'),   -- neutral_grip_dumbbell_skull_crusher
    ('221fb5fd-39b5-513f-ae44-3c712ce3f989'::uuid, 'Funcional e HIT/stability_ball_v_up_pass.mp4', 'Funcional e HIT/stability_ball_v_up_pass.webp'),   -- stability_ball_v_up_pass
    ('14a87b59-c660-5076-8289-29ab4a75906b'::uuid, 'Crossfit/tire_flip.mp4', 'Crossfit/tire_flip.webp'),   -- tire_flip
    ('e9a16a61-da4b-5bb0-b474-d48bb9b39c43'::uuid, 'Peitoral/incline_chest_fly_machine.mp4', 'Peitoral/incline_chest_fly_machine.webp'),   -- incline_chest_fly_machine
    ('6b935146-e15f-5930-97b8-2133cc4d65a6'::uuid, 'Peitoral/dumbbell_front_raise_to_overhead.mp4', 'Peitoral/dumbbell_front_raise_to_overhead.webp'),   -- dumbbell_front_raise_to_overhead
    ('70bf08c1-580c-597a-9f95-6843a1c2a4ca'::uuid, 'Ombros/reverse_pec_deck.mp4', 'Ombros/reverse_pec_deck.webp'),   -- reverse_pec_deck
    ('c0229281-4eb3-55f1-bab5-b7f0c22c109f'::uuid, 'Peitoral/machine_pec_deck_fly.mp4', 'Peitoral/machine_pec_deck_fly.webp'),   -- machine_pec_deck_fly
    ('7b101363-2502-5e47-86d6-79e988baaa44'::uuid, 'Peitoral/pec_deck_fly.mp4', 'Peitoral/pec_deck_fly.webp'),   -- pec_deck_fly
    ('739993d0-0aa7-50ba-9030-7b490ed0e21f'::uuid, 'Ombros/standing_cable_rear_delt_fly.mp4', 'Ombros/standing_cable_rear_delt_fly.webp'),   -- standing_cable_rear_delt_fly
    ('670c21ad-2cd8-569e-984c-bb3c258aa6f6'::uuid, 'Funcional e HIT/wall_sit.mp4', 'Funcional e HIT/wall_sit.webp'),   -- wall_sit
    ('1e764c22-cc8d-5ea1-8379-291968a6b27b'::uuid, 'Funcional e HIT/wall_sit_with_side_bend.mp4', 'Funcional e HIT/wall_sit_with_side_bend.webp'),   -- wall_sit_with_side_bend
    ('86d71dff-8553-55de-8d99-4764ae1dfaf0'::uuid, 'Mobilidade/supine_leg_straddle_stretch.mp4', 'Mobilidade/supine_leg_straddle_stretch.webp'),   -- supine_leg_straddle_stretch
    ('8bfc1c04-c282-550e-9c26-58cd10415ed7'::uuid, 'Ombros/dual_cable_front_raise.mp4', 'Ombros/dual_cable_front_raise.webp'),   -- dual_cable_front_raise
    ('cf79231a-9077-5bfb-859e-582d83e30e5c'::uuid, 'Costas/deadlift.mp4', 'Costas/deadlift.webp'),   -- deadlift
    ('3d3521df-770c-5b44-8ba3-7d3dad341775'::uuid, 'Pernas/lever_deadlift.mp4', 'Pernas/lever_deadlift.webp'),   -- lever_deadlift
    ('01e8367b-9816-5ba2-8fbf-f848cb7451d2'::uuid, 'Costas/lever_chest_supported_t_bar_row.mp4', 'Costas/lever_chest_supported_t_bar_row.webp')   -- lever_chest_supported_t_bar_row
) AS m(id, video_ref, thumb_ref)
 WHERE e.id = m.id
   AND (e.video_ref IS DISTINCT FROM m.video_ref OR e.thumb_ref IS DISTINCT FROM m.thumb_ref);


-- ============================================================================
-- GUARDA: o UPDATE fez exatamente o que diz
-- ============================================================================
--
-- ⚠️ A primeira versão desta guarda afirmava o formato para a tabela INTEIRA e abortou a
-- migration. Estava certa em falhar e errada no que media: a V50 atualiza 900 linhas, e as outras
-- 65 continuam legitimamente fora do formato novo —
--
--   * 39 são os duplicados que a V52 ainda vai remover, com o caminho antigo por extenso;
--   * 26 nunca tiveram mídia e têm `video_ref` vazio.
--
-- > Guarda que afirma sobre a tabela inteira quando a migration tocou uma parte dela acusa o que
-- > não mudou.
--
-- A afirmação correta é sobre a CONTAGEM: exatamente 900 caminhos no formato `<Categoria>/<chave>`.
-- Menos que isso significa que o mapeamento não alcançou alguém; mais, que alguém já estava no
-- formato novo por outro caminho e o mapa está desatualizado. Os dois são defeito.
--
-- Falhar aqui aborta e deixa o banco no estado anterior, que é o que se quer: melhor não subir do
-- que subir com metade dos caminhos apontando para o vazio.
DO $$
DECLARE
    no_formato_novo integer;
    vazios          integer;
BEGIN
    SELECT count(*) INTO no_formato_novo
      FROM exercises
     WHERE video_ref ~ '^[^/]+/[a-z0-9_]+\.mp4$'
       AND thumb_ref ~ '^[^/]+/[a-z0-9_]+\.webp$';

    IF no_formato_novo <> 900 THEN
        RAISE EXCEPTION 'V50: esperava 900 caminhos no formato da chave, encontrei %', no_formato_novo;
    END IF;

    -- Os 26 sem mídia são débito conhecido e enumerado. Se o número muda, alguém acrescentou
    -- exercício sem imagem — e isso precisa ser uma decisão, não um efeito colateral.
    SELECT count(*) INTO vazios FROM exercises WHERE video_ref = '';

    IF vazios <> 26 THEN
        RAISE EXCEPTION 'V50: esperava 26 exercícios sem mídia, encontrei %', vazios;
    END IF;
END $$;
