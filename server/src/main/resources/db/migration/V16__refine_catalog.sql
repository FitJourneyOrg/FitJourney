-- V16: refino do catálogo (ARCH #28).
-- 1) coluna is_base (principal vs variação). 2) marca principais. 3) esconde exóticos do
--    motor (modality=NULL; seguem no catálogo p/ treino manual). 4) insere bases faltantes.
-- Ambiente (Academia/Casa) é derivado do equipment em código (EquipmentEnvironmentMap).

ALTER TABLE exercises ADD COLUMN is_base BOOLEAN NOT NULL DEFAULT false;

-- 144 principais
UPDATE exercises SET is_base = true WHERE id IN (
    'df942cd6-28b2-54a8-9b54-7a32db854beb',
    '9851a2f8-f8ac-54fb-8c27-4234abcc6bd9',
    '36032c9a-8fa1-5fa4-a2bd-75b2d458ea79',
    '99e32582-d818-5e97-8851-d2e0a04d3ca0',
    'abf1fc72-f07f-5c0b-820a-c7e010afa9d7',
    'dfd78903-efa3-528f-b747-bcc99e59ddc8',
    'f9354ac6-e9d0-52cf-b105-acb927c80074',
    'cf79231a-9077-5bfb-859e-582d83e30e5c',
    '4d50874b-4cec-502a-8051-0e0e471e8a99',
    '552b8970-aff0-58d9-9484-74be5eaf2bf2',
    '2aaf07b6-ae05-523b-ae5f-f6e7d515739c',
    '06a40e7c-7a54-5991-a75c-1001d93d0572',
    '03059786-28ba-54b6-a7eb-44015492dc57',
    '4a1133f0-f259-5081-aac3-73497f6037cb',
    '020e5caa-570f-5373-ae4e-63457e8c022a',
    '075e0444-df01-5dd3-8aa0-f7fbd965c048',
    '0d3be45f-2b0c-5118-8aff-e3e1249784a5',
    'c1df1689-c819-5573-9ea5-41c1ab08af9e',
    '39eb3723-cc94-5b18-9590-53a191964d8e',
    '79f8d96a-d315-53a9-b3e8-94fb9c57a9e1',
    'e5d40626-2cc3-543a-b57a-0804bfda605f',
    '8e8d03f8-1b9a-529c-a3e6-4438cf8c9f18',
    '94fc815a-8d7d-5189-865e-17708066df09',
    'be6b8864-8726-50a6-88c8-21ecad52ce36',
    '427d12d6-41cc-5bb4-bfa1-afe4da6aa002',
    'f6ca3c1e-5bda-5b50-bb52-37c95b0e7d15',
    'bb1464e2-3f05-5ce4-a600-658155df7ea6',
    '336e7052-eae3-5b09-b3cf-67bf911b16f3',
    'a5c53651-49c7-5365-822a-8cb8da06bd51',
    'ff79b49f-58fa-5d99-a782-2528080a9fba',
    'f45b280a-edf2-5366-9600-2bcd47d8fd42',
    'f9a2fd43-ccec-585e-8e1b-190f4d51f694',
    '7f0bb706-1ba1-56c5-8188-e6f5d69fb8c6',
    'cb1e8843-2491-5594-9f77-19c945490553',
    '570d206c-f743-5427-ba86-a9f05a256932',
    'aed32c40-608a-5d2c-9fae-f0a046a0e210',
    '41ad8f1f-f88e-500e-a2b0-28c53fb1c30f',
    '63e8a9d4-1d0c-5ec9-9000-286cbe67a7c0',
    '47ed255b-cca9-5dcf-943a-0f93df04c888',
    '6aba4098-93c1-5f1d-afa1-65db141b23a6',
    '8d1096a7-eadb-54e1-889c-4bcb7d13db6a',
    'ff4c217c-3d01-59ec-8646-d7a92ab388a9',
    '323db88b-1918-5e1a-8aa3-747c0fce6b0b',
    '4d304500-f1bf-5a6a-8a8b-884aeaad1004',
    'ceea8ca8-e5ed-58d5-9e0e-c57f8973354b',
    '5d30f49d-f617-57f5-889c-e8e42c639b9e',
    '43f0d050-afe4-5a2b-9749-88f343122641',
    'f765b56e-7f60-5837-bdce-c0be0a961562',
    'a28cf315-03c0-539f-b89e-07f8df3e00cd',
    '45b98976-fe0f-57ec-a1d7-e466d834aea8',
    'b276c9b3-120f-5eed-a131-c7cc2430aacc',
    'b2191802-8c70-5c4a-bde8-8aa707685462',
    'c5f4a906-d6a2-5177-a226-f47ad7efec04',
    'e4796c7d-1e34-51e1-9585-297fc984ebcb',
    '1f3f0b22-6cc6-5179-98c2-f3844c1a1be4',
    'bfb31ef8-a6b1-5663-8044-e892d88966f3',
    'c8479334-d145-512e-b4bd-fe8e77d335d2',
    '74dfa8b4-a086-500c-98f6-ee8fa3672d9f',
    'c42a18cb-0bd2-5158-a7e5-2682ad23b55f',
    '6a76b5cd-2a0c-5980-9324-035f624947eb',
    '8b73bd08-e5cb-51fd-9a8a-2ba1ab0b923b',
    'd26341a2-56a1-5130-bb58-1f6dee4f1f5a',
    'b6c5102a-c80c-50ec-bd8a-c245ca5c6a50',
    '7b823dc6-579a-5ffa-b56a-b2a8dc420902',
    '3cd35484-2799-5077-ac32-ae0b0ca00244',
    '38516dcb-4c83-593e-99d8-7cf01aa0eacd',
    '247f6dfc-2c7d-53b2-bfd7-2ccbef4bac9b',
    'eded16c1-1374-52fd-89eb-19a0d9166a51',
    '066cabdb-125b-59fc-a0e5-6adb648c646b',
    '16d85db7-9e37-55ed-acde-7896edd58201',
    '01fa2cb7-d8ce-593b-be0b-1072667c738c',
    '2794cf73-df14-50fa-8d74-466b74f87429',
    '33c9d06f-6d25-543c-a9e9-f22f7c2d8491',
    'fbd921a0-69ba-519a-95bf-d12dcaeca142',
    '739993d0-0aa7-50ba-9030-7b490ed0e21f',
    '9ed0cdb8-bbdd-5e1d-be5c-eca202503d31',
    'ffc73e33-e436-5cb4-b306-6005b10f55ea',
    '22abc486-e188-5f27-95d1-acd331daffd1',
    'dc2f45f4-2f6f-5e8b-93bf-473a778d83ed',
    'ef4e3c3e-1043-5f8c-b260-151295dbb478',
    '7d922bee-85e4-5de4-8e60-9274c4dcd72c',
    'fb3814e9-89fc-5f3e-876b-3077d13dbc72',
    '03a6a766-7e34-5dd2-8a8c-cb4457534a7c',
    '0a46cde1-4bf6-5758-8f94-b776ea587959',
    'a8eccd14-d32a-5a0e-9612-bba83a17aaa6',
    'd08d2546-09aa-58ec-8c83-9760ce45a75d',
    '2dec0f86-3318-5994-b75b-57c17ede6194',
    '67d1b649-59d0-5808-8f8f-e670e606c601',
    '1de1de77-70da-5930-89cd-4bcb63c7712d',
    '8c6b53d7-e3d3-5ddd-bcd1-07436fdb208e',
    '800ef82d-c644-5d1f-97bf-0a13aee0c46c',
    '7ac24423-8531-522c-a377-8dc9e2241943',
    '019873b5-f258-5799-bfe8-46d746cc7c1e',
    '51f5c419-b002-533f-9271-04a6a14aa011',
    '39af9068-d462-50e3-a9ff-086c4128e4f8',
    'b7c511ee-1e49-5eac-8b3f-b0c5792472fc',
    '82a5cc98-e1dc-5c54-a305-9b913cbbdb59',
    '7e6adc01-d07f-52f9-bf73-a4944bfcf62f',
    '77e5ed68-da7e-5e55-9ea6-3a51ab625535',
    '2d94d612-5d46-5e4b-b04b-4fdecbaa5ce9',
    'c197b4ab-8a2f-552e-94e0-4bba22cb2ae8',
    '2bd308a2-b65f-5da1-af82-5491a549e542',
    'ba9655da-86ef-5cd0-b3eb-68ab80198f21',
    '9af4deba-30c1-534c-91ae-cdf7bcc5c33c',
    '1f7e83d8-5170-540d-9fb9-e33e6b6253d6',
    '26038b4c-436d-5083-a3c1-65239107ac5a',
    '2a53cd33-7f44-5a00-aafe-92f857551c1a',
    '3a27c30a-6a50-59e1-9231-69eb2d9ac237',
    'd19c46ee-4b3b-5abd-9383-c4c94a244a0b',
    '70bf08c1-580c-597a-9f95-6843a1c2a4ca',
    '4afcf90e-91f8-588e-87e9-04e15c046250',
    'adad8fea-9428-5a58-82d9-e79935faaea4',
    '05a1eee6-06f9-50a2-8827-902b3b6fad44',
    'd5aa8f22-8373-558e-bfe7-1af980b1dc04',
    'b88ba2ea-6da8-5b45-a238-d069e54e9ef4',
    'b8c61d9c-7218-5737-b5f9-29aea0a04da9',
    'be89a141-ac87-573f-8cfc-c49090988f4c',
    '1ff60154-1068-5bbd-81f3-77ed6482dd1f',
    '9e7ad364-21e0-58ff-94bb-ebbe5014d321',
    '39db3ab8-e7d5-5294-8a44-2cacff2780e9',
    '777896ee-be7d-5189-a148-862a4123106a',
    '562041da-c75e-5354-983a-b49154b7682d',
    '2f0fdf11-7367-5ba9-914b-c11d3096595e',
    'a051ab20-e7c5-51db-8850-52dd33627c2a',
    'bf50b1f9-7467-502c-a7e6-4090ba56db8d',
    '94f2ce78-68b3-588e-b3e9-50dc1375db30',
    '7742b513-dc03-5294-a3a3-62cd5c473bcd',
    'c0229281-4eb3-55f1-bab5-b7f0c22c109f',
    'bd21e33b-b34c-508d-98a6-71407bbd44ea',
    '9bd84648-0d26-5f21-a324-9fe63c224c23',
    '553f12f8-c110-5a94-a70c-80fde577ba6d',
    '37492785-d612-52be-a187-969e5c36587b',
    '2261dcae-1a1d-50e1-820b-1f3b473abb79',
    'e0dfbfe7-db4d-52a8-a554-865358e1fe1e',
    'a78ce2ff-7796-5d04-9dac-5f673e7c5236',
    '6e66436b-c2ba-565f-908d-f858b8fa0dc4',
    '3cd26c0e-8c83-55a5-9032-d26db3ed37ca',
    'be8eb716-8316-5ea4-b383-f2850a903df9',
    'ff14f2bb-8293-5bf2-bf1e-b692d9e6a684',
    '2a0a3579-f68f-590e-a0b1-37d840b7df2b',
    'ea5b562a-c415-5041-8c7f-5a4233528235',
    '66b8014c-cdfa-55af-8d2c-f621cf030706',
    'c4cbc095-c390-5343-a460-c261e55e8d64',
    '5696f95f-61d9-5804-8748-098f2f907ac7'
);

-- 76 exóticos escondidos do motor
UPDATE exercises SET modality = NULL WHERE id IN (
    '5f7ac1de-2cff-5348-975e-c64b4fa11d56',
    '443e406a-24ab-5764-9bbd-bd621916e31f',
    'd7b8e4b6-0cc6-5075-9020-d5cd3de8d18d',
    '1da9c7a0-79cd-54e4-bcc3-d3947a319572',
    'db5d1de5-a818-5618-9294-ff4b500bf033',
    'c7926e57-307d-5f7b-8b04-fa04268fb843',
    'd6c9f705-2c0a-5f46-a206-f18da6242b6c',
    '676a6082-b3df-5a14-ad3b-a458f2ec9b90',
    '51de3c39-fe70-5d2a-8999-38fd6119083c',
    '17529f79-5dea-583f-9741-778fadec64f1',
    '24acfa49-e4f0-543f-8ff2-7968175d546c',
    '83cbac02-7949-5917-9179-bb30bda298e9',
    'cab4b690-c0d8-5483-8981-f83bcced6bc9',
    'd089bb9c-24f2-592f-ac3b-7d1fb42c0086',
    '791b3ec1-25d9-583d-aa54-0268f5a93cce',
    'e116d153-7def-534e-9fb1-45fbf7e9e0f4',
    'ff9baeab-e143-5793-aadd-1a627f92ba19',
    'e7279daa-2cd7-51e9-a6c9-34c3a9c107c2',
    'a09f6fd4-2c38-5e02-beb5-10c23edf4415',
    'a169c688-ceca-585b-8848-b9e8e2897b8c',
    'd3ad0b18-7396-5134-8af0-b4bcef189052',
    'c915d4e1-ebe5-5f30-a893-5cee465452db',
    'f926f52d-f147-5fe3-bc18-9c4de3b8a723',
    'd9177dd4-370e-532f-98c4-7f7f95251262',
    'e3c76218-1b13-5ce2-b4ff-c03d8dd1623f',
    '5bbd7eb6-ecf9-51b1-a7cc-3f019aa0434b',
    '8f296b9f-49d7-5671-9500-8b34bf06cd55',
    '4482e897-2ac7-56ad-bf00-e2dedaf0fe38',
    '2838a82b-8be4-52bf-b18f-f6d4893f1cc4',
    'db910d11-cbc5-514a-967c-004d3ec8d739',
    'c76a32d7-a4f9-5c7c-bf99-afa0f6386d0e',
    '025aae56-8944-5e58-adb0-7bbbb2c83572',
    '832fe86f-064b-5f46-b689-f2f01dc2188c',
    '8c3c4e4a-12b0-5451-894c-8b775fd5fa7f',
    'ca0491cd-ab3b-5934-9b38-40e4dfd491a3',
    '670c21ad-2cd8-569e-984c-bb3c258aa6f6',
    'e1388f76-351d-5535-906a-f31e89f466b5',
    'bfdd5742-dba3-5719-9610-4fa5dafb184f',
    '463aa08f-6b52-58cb-b6cb-289f3bf992c9',
    '74b342c8-beb7-5e25-8060-cbe8061dcda5',
    '7c7f6937-8a6a-5cb1-a6e9-b27692d7b2c9',
    '66d5f29e-f6a4-5762-85e4-af9ec59bdcd9',
    '76e9d119-b670-5eb3-a12a-46c57fc1eb03',
    '612c04cb-a5f9-5c92-8ce3-ac00674fdecb',
    '7fe495a6-67b1-567e-b48f-8bba29042acf',
    'b2bb7607-8402-5097-a55f-578d608d62f4',
    '3e7abe4c-38e0-5143-92e9-f3cb80685895',
    'db1de041-477c-54eb-a4ff-6e2e358f33b4',
    '112dbd59-f653-5eb0-b7ab-91abc7842a1f',
    'fc3bbcca-9b05-56e9-90cb-a3dff1a12fa2',
    '7b39d400-2a78-5250-a78c-7c4bff58f8c7',
    '6d47c5e4-83bf-51a1-a067-bed2c91838f4',
    'a8a8884c-5c4b-5bb6-89cc-66b67b55d9ff',
    '90689b82-eed4-5aff-b046-396780a6a8b1',
    '85267546-045c-59ac-bd1c-5d60b2df155e',
    '145bd851-5bba-5139-999e-a380a689b3fa',
    '029a19c9-3c0f-5715-8d0c-940e89ee1345',
    '5a55c7be-9a46-5e36-9f45-e5a437355362',
    '9fedf673-c862-57b0-861b-87db1a635cff',
    '077cb8f0-c61e-548f-9dce-9e5c197cba7c',
    'cbc974d5-d735-5a9a-8cb0-7782967ec9d6',
    '4342d924-d80b-5bad-b36a-3dd71507792f',
    'c6e4a588-d987-59ce-bbac-bbe5761d99d7',
    '14c35837-4aff-5094-a8c8-7f61ba6bb82b',
    'aff44941-afd1-5489-9902-f7c2adacb6e0',
    '8ffbfb06-78ce-53fc-914e-8e4aed76cba5',
    '959f3ed4-fb9e-59a3-9585-5f320e2aa347',
    'c64ec081-7009-5dd3-a1cb-8cf0bd368530',
    'aac961ca-6744-5836-973e-0672cf80a4ae',
    '1eda2d16-64d4-5953-8621-900472a3bbf0',
    'a2573e54-9ee5-50ae-86f2-d0417e71d337',
    'ca744fa1-fffb-565e-bcd0-585b5595e00e',
    '14a87b59-c660-5076-8289-29ab4a75906b',
    '139afe7c-b21d-53aa-a588-9e7b37628e61',
    '5b5a1b3c-ac3d-5c35-83ad-415c2aaa07d7',
    '7d71e4e1-4b6f-59f7-9f7f-bc7249e7fa15'
);

-- 23 novos exercícios-base
INSERT INTO exercises (id, name, category, description, video_ref, thumb_ref, modality, movement_pattern, is_compound, equipment, primary_muscles, secondary_muscles, unilateral, prescription_type, level, contraindications, is_base)
  VALUES ('58b85b43-d4cc-5829-b144-493787ef4620', 'Mesa flexora', 'LEGS', NULL, '', '', 'STRENGTH', 'KNEE_FLEXION', false, 'MACHINE', ARRAY['LEGS']::TEXT[], ARRAY[]::TEXT[], false, 'REPS', 'BEGINNER', ARRAY['KNEE']::TEXT[], true);
INSERT INTO exercises (id, name, category, description, video_ref, thumb_ref, modality, movement_pattern, is_compound, equipment, primary_muscles, secondary_muscles, unilateral, prescription_type, level, contraindications, is_base)
  VALUES ('a0b7125a-5dc9-5413-95d2-254627588fd0', 'Cadeira flexora', 'LEGS', NULL, '', '', 'STRENGTH', 'KNEE_FLEXION', false, 'MACHINE', ARRAY['LEGS']::TEXT[], ARRAY[]::TEXT[], false, 'REPS', 'BEGINNER', ARRAY['KNEE']::TEXT[], true);
INSERT INTO exercises (id, name, category, description, video_ref, thumb_ref, modality, movement_pattern, is_compound, equipment, primary_muscles, secondary_muscles, unilateral, prescription_type, level, contraindications, is_base)
  VALUES ('2ad91747-ffc2-5789-a36c-1213cefa5ed3', 'Flexora em pé unilateral', 'LEGS', NULL, '', '', 'STRENGTH', 'KNEE_FLEXION', false, 'MACHINE', ARRAY['LEGS']::TEXT[], ARRAY[]::TEXT[], true, 'REPS', 'BEGINNER', ARRAY['KNEE']::TEXT[], true);
INSERT INTO exercises (id, name, category, description, video_ref, thumb_ref, modality, movement_pattern, is_compound, equipment, primary_muscles, secondary_muscles, unilateral, prescription_type, level, contraindications, is_base)
  VALUES ('30a50289-16a6-58df-962f-e6197078f449', 'Stiff com barra', 'LEGS', NULL, '', '', 'STRENGTH', 'HINGE', true, 'BARBELL', ARRAY['LEGS']::TEXT[], ARRAY[]::TEXT[], false, 'REPS', 'INTERMEDIATE', ARRAY['LOWER_BACK']::TEXT[], true);
INSERT INTO exercises (id, name, category, description, video_ref, thumb_ref, modality, movement_pattern, is_compound, equipment, primary_muscles, secondary_muscles, unilateral, prescription_type, level, contraindications, is_base)
  VALUES ('dde5714a-bae8-5110-8e88-8b0649d76b91', 'Levantamento terra romeno com barra', 'LEGS', NULL, '', '', 'STRENGTH', 'HINGE', true, 'BARBELL', ARRAY['LEGS']::TEXT[], ARRAY[]::TEXT[], false, 'REPS', 'INTERMEDIATE', ARRAY['LOWER_BACK']::TEXT[], true);
INSERT INTO exercises (id, name, category, description, video_ref, thumb_ref, modality, movement_pattern, is_compound, equipment, primary_muscles, secondary_muscles, unilateral, prescription_type, level, contraindications, is_base)
  VALUES ('4a073e03-1940-552c-872a-120e55e68b70', 'Stiff com halteres', 'LEGS', NULL, '', '', 'STRENGTH', 'HINGE', true, 'DUMBBELL', ARRAY['LEGS']::TEXT[], ARRAY[]::TEXT[], false, 'REPS', 'BEGINNER', ARRAY['LOWER_BACK']::TEXT[], true);
INSERT INTO exercises (id, name, category, description, video_ref, thumb_ref, modality, movement_pattern, is_compound, equipment, primary_muscles, secondary_muscles, unilateral, prescription_type, level, contraindications, is_base)
  VALUES ('db17b9b6-6425-5929-babe-ba61b3c40eca', 'Flexão nórdica', 'LEGS', NULL, '', '', 'STRENGTH', 'KNEE_FLEXION', true, 'BODYWEIGHT', ARRAY['LEGS']::TEXT[], ARRAY[]::TEXT[], false, 'REPS', 'ADVANCED', ARRAY['KNEE']::TEXT[], true);
INSERT INTO exercises (id, name, category, description, video_ref, thumb_ref, modality, movement_pattern, is_compound, equipment, primary_muscles, secondary_muscles, unilateral, prescription_type, level, contraindications, is_base)
  VALUES ('f6cc99ac-9b39-51e6-a1e6-8c76d9da2a15', 'Deslizamento de calcanhar', 'LEGS', NULL, '', '', 'STRENGTH', 'KNEE_FLEXION', false, 'BODYWEIGHT', ARRAY['LEGS']::TEXT[], ARRAY[]::TEXT[], false, 'REPS', 'INTERMEDIATE', ARRAY['KNEE']::TEXT[], true);
INSERT INTO exercises (id, name, category, description, video_ref, thumb_ref, modality, movement_pattern, is_compound, equipment, primary_muscles, secondary_muscles, unilateral, prescription_type, level, contraindications, is_base)
  VALUES ('e63cb82e-e44d-5d58-9312-e0ac9ba442ba', 'Elevação de panturrilha em pé', 'CALVES', NULL, '', '', 'STRENGTH', 'NONE', false, 'BODYWEIGHT', ARRAY['LEGS']::TEXT[], ARRAY[]::TEXT[], false, 'REPS', 'BEGINNER', ARRAY[]::TEXT[], true);
INSERT INTO exercises (id, name, category, description, video_ref, thumb_ref, modality, movement_pattern, is_compound, equipment, primary_muscles, secondary_muscles, unilateral, prescription_type, level, contraindications, is_base)
  VALUES ('94d38573-e89e-5294-8d91-1cb1a1442c75', 'Elevação de panturrilha unilateral em pé', 'CALVES', NULL, '', '', 'STRENGTH', 'NONE', false, 'BODYWEIGHT', ARRAY['LEGS']::TEXT[], ARRAY[]::TEXT[], true, 'REPS', 'BEGINNER', ARRAY[]::TEXT[], true);
INSERT INTO exercises (id, name, category, description, video_ref, thumb_ref, modality, movement_pattern, is_compound, equipment, primary_muscles, secondary_muscles, unilateral, prescription_type, level, contraindications, is_base)
  VALUES ('06be6a67-b525-5811-8ba4-4c10f3c03e9c', 'Elevação de panturrilha em pé com halteres', 'CALVES', NULL, '', '', 'STRENGTH', 'NONE', false, 'DUMBBELL', ARRAY['LEGS']::TEXT[], ARRAY[]::TEXT[], false, 'REPS', 'BEGINNER', ARRAY[]::TEXT[], true);
INSERT INTO exercises (id, name, category, description, video_ref, thumb_ref, modality, movement_pattern, is_compound, equipment, primary_muscles, secondary_muscles, unilateral, prescription_type, level, contraindications, is_base)
  VALUES ('b5560c6b-c58e-50e4-97a8-2b6fac02cc7d', 'Elevação de panturrilha sentado com halteres', 'CALVES', NULL, '', '', 'STRENGTH', 'NONE', false, 'DUMBBELL', ARRAY['LEGS']::TEXT[], ARRAY[]::TEXT[], false, 'REPS', 'BEGINNER', ARRAY[]::TEXT[], true);
INSERT INTO exercises (id, name, category, description, video_ref, thumb_ref, modality, movement_pattern, is_compound, equipment, primary_muscles, secondary_muscles, unilateral, prescription_type, level, contraindications, is_base)
  VALUES ('8b2aa0e7-e7a6-54f7-a2b7-ff0424dac62d', 'Elevação de panturrilha no degrau com haltere', 'CALVES', NULL, '', '', 'STRENGTH', 'NONE', false, 'DUMBBELL', ARRAY['LEGS']::TEXT[], ARRAY[]::TEXT[], true, 'REPS', 'BEGINNER', ARRAY[]::TEXT[], true);
INSERT INTO exercises (id, name, category, description, video_ref, thumb_ref, modality, movement_pattern, is_compound, equipment, primary_muscles, secondary_muscles, unilateral, prescription_type, level, contraindications, is_base)
  VALUES ('c30b977e-6de8-5a19-84e9-556c944536bf', 'Prancha', 'CORE', NULL, '', '', 'STRENGTH', 'NONE', false, 'BODYWEIGHT', ARRAY['CORE']::TEXT[], ARRAY[]::TEXT[], false, 'TIME', 'BEGINNER', ARRAY[]::TEXT[], true);
INSERT INTO exercises (id, name, category, description, video_ref, thumb_ref, modality, movement_pattern, is_compound, equipment, primary_muscles, secondary_muscles, unilateral, prescription_type, level, contraindications, is_base)
  VALUES ('250ddb2b-6e59-5222-a4fc-16ba85d57bcc', 'Prancha lateral', 'CORE', NULL, '', '', 'STRENGTH', 'NONE', false, 'BODYWEIGHT', ARRAY['CORE']::TEXT[], ARRAY[]::TEXT[], true, 'TIME', 'BEGINNER', ARRAY[]::TEXT[], true);
INSERT INTO exercises (id, name, category, description, video_ref, thumb_ref, modality, movement_pattern, is_compound, equipment, primary_muscles, secondary_muscles, unilateral, prescription_type, level, contraindications, is_base)
  VALUES ('eb77a949-3ef0-5fe4-9c54-a206059bf8d6', 'Abdominal', 'CORE', NULL, '', '', 'STRENGTH', 'NONE', false, 'BODYWEIGHT', ARRAY['CORE']::TEXT[], ARRAY[]::TEXT[], false, 'REPS', 'BEGINNER', ARRAY[]::TEXT[], true);
INSERT INTO exercises (id, name, category, description, video_ref, thumb_ref, modality, movement_pattern, is_compound, equipment, primary_muscles, secondary_muscles, unilateral, prescription_type, level, contraindications, is_base)
  VALUES ('d886743e-0ada-506b-ab45-45792ac40689', 'Abdominal infra', 'CORE', NULL, '', '', 'STRENGTH', 'NONE', false, 'BODYWEIGHT', ARRAY['CORE']::TEXT[], ARRAY[]::TEXT[], false, 'REPS', 'BEGINNER', ARRAY['LOWER_BACK']::TEXT[], true);
INSERT INTO exercises (id, name, category, description, video_ref, thumb_ref, modality, movement_pattern, is_compound, equipment, primary_muscles, secondary_muscles, unilateral, prescription_type, level, contraindications, is_base)
  VALUES ('2d7453d5-bb32-55b4-9534-b7a954a7316b', 'Prancha com toque no ombro', 'CORE', NULL, '', '', 'STRENGTH', 'NONE', false, 'BODYWEIGHT', ARRAY['CORE']::TEXT[], ARRAY[]::TEXT[], false, 'TIME', 'BEGINNER', ARRAY[]::TEXT[], true);
INSERT INTO exercises (id, name, category, description, video_ref, thumb_ref, modality, movement_pattern, is_compound, equipment, primary_muscles, secondary_muscles, unilateral, prescription_type, level, contraindications, is_base)
  VALUES ('331d44ad-0bf1-583d-9e77-a9d6fab9bb0a', 'Abdominal bicicleta', 'CORE', NULL, '', '', 'STRENGTH', 'ROTATION', false, 'BODYWEIGHT', ARRAY['CORE']::TEXT[], ARRAY[]::TEXT[], false, 'REPS', 'BEGINNER', ARRAY[]::TEXT[], true);
INSERT INTO exercises (id, name, category, description, video_ref, thumb_ref, modality, movement_pattern, is_compound, equipment, primary_muscles, secondary_muscles, unilateral, prescription_type, level, contraindications, is_base)
  VALUES ('e34184be-94db-5144-8d0f-8c70ec00bdc2', 'Abdominal na polia', 'CORE', NULL, '', '', 'STRENGTH', 'NONE', false, 'CABLE', ARRAY['CORE']::TEXT[], ARRAY[]::TEXT[], false, 'REPS', 'BEGINNER', ARRAY[]::TEXT[], true);
INSERT INTO exercises (id, name, category, description, video_ref, thumb_ref, modality, movement_pattern, is_compound, equipment, primary_muscles, secondary_muscles, unilateral, prescription_type, level, contraindications, is_base)
  VALUES ('2c0abe5e-815e-5c1a-9f94-5152eb50c3f3', 'Rotação no cabo (Pallof)', 'CORE', NULL, '', '', 'STRENGTH', 'ROTATION', false, 'CABLE', ARRAY['CORE']::TEXT[], ARRAY[]::TEXT[], true, 'REPS', 'INTERMEDIATE', ARRAY['LOWER_BACK']::TEXT[], true);
INSERT INTO exercises (id, name, category, description, video_ref, thumb_ref, modality, movement_pattern, is_compound, equipment, primary_muscles, secondary_muscles, unilateral, prescription_type, level, contraindications, is_base)
  VALUES ('c7a31dd9-e915-5217-94d8-b46af42991a3', 'Abdominal na máquina', 'CORE', NULL, '', '', 'STRENGTH', 'NONE', false, 'MACHINE', ARRAY['CORE']::TEXT[], ARRAY[]::TEXT[], false, 'REPS', 'BEGINNER', ARRAY[]::TEXT[], true);
INSERT INTO exercises (id, name, category, description, video_ref, thumb_ref, modality, movement_pattern, is_compound, equipment, primary_muscles, secondary_muscles, unilateral, prescription_type, level, contraindications, is_base)
  VALUES ('25a0b344-f41e-5d92-99a8-2714ba277276', 'Elevação de pernas na cadeira romana', 'CORE', NULL, '', '', 'STRENGTH', 'NONE', false, 'MACHINE', ARRAY['CORE']::TEXT[], ARRAY[]::TEXT[], false, 'REPS', 'INTERMEDIATE', ARRAY['LOWER_BACK']::TEXT[], true);
