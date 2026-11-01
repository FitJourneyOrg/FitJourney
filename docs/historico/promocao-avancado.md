# Promoção intermediário→avançado — fatia fechada (2026-10-31)

> Movido de `docs/trabalho/promocao-avancado.md` ao fechar o ciclo. Decisões e desenho completos
> ficaram no plano original (ver git log); aqui só a narrativa e os achados.

## O que entregou

Fecha o débito (b) de "o motor sabe, o app não mostra" (`fontes/debitos.md`, P0) — o avançado
(`PromocaoDeNivel.diagnosticoAvancado`, núcleo puro desde a Fatia 11) ganhou chamador:

- **V72** (`um_rm_do_evento_de_progressao.sql`) — `progression_event.um_rm_kg`, nullable, sem
  backfill. Fecha o furo que a V71 deixou: a âncora (mesociclo) existia, o valor não.
- **`PromocaoDeNivel.diagnosticoAvancadoDoPerfil`** — agregação por **maioria estrita** (mais da
  metade dos compostos `relevancia = 1` em PROMOVER). Registrado como `[REGRA]` em `regras/01`;
  heurística calibrável registrada em `debitos.md`, 2º parâmetro a recalibrar quando houver
  telemetria (depois do "2" fixo do iniciante).
- **`ExerciseRepository.ancorasAvancadas`** + **`ProgressaoRepository.primeiroUmRmNoMesociclo`/
  `reducoesDesde`** — leitura do histórico real.
- **`PromotionService.diagnosticarAvancado`** — compara 1RM entre mesociclo anterior e atual por
  âncora, agrega, distingue DADO INSUFICIENTE (sem mesociclo anterior) de DIAGNOSTICOU-E-NÃO-É-
  PROMOVER (nunca colapsados no mesmo `null` sem log — exigência do Rafael, por causa do período
  cego da V72 sem backfill), e respeita recusa anterior via `podeSugerirDeNovo`.

## Verificação feita (não só assumida)

Sensibilidade combinatória medida contra os 720 perfis da matriz (oráculo + teste `[INV]` em
`PromocaoDeNivelTest`): nenhum perfil INTERMEDIATE tem menos de 2 âncoras avançadas (compostos
`relevancia = 1`); distribuição 2–12, mediana 5. Maioria estrita é estruturalmente alcançável —
ao contrário do caso do iniciante, onde "maioria" teria sido inatingível com poucas âncoras. A
taxa de disparo real (quantas vezes PROMOVER de fato dispara em produção) **não foi e não pode
ser medida agora** — depende de RIR chegando do cliente, que ainda não chega (débito separado,
P0). É contagem estrutural de âncoras, não taxa de disparo medida.

## Achado durante o fechamento: fixture de teste com data absoluta

`CheckInIntegrationTest.grupoDe()` usava `startDate`/`endDate` fixos (`2026-09-01`/`2026-09-30`)
para representar um desafio "ainda rolando". Não é bug desta fatia, mas apareceu ao rodar a
suíte: em 2026-10-31 a data fixa já tinha passado da carência de 30 dias para purga de foto
(`comFotoExpirada`), e o grupo "rolando" (e qualquer outro teste da mesma classe que usasse o
default) passou a contar como encerrado-além-da-carência por acidente — poluindo uma asserção
global (`comFotoExpirada().size`) sem isolamento por teste, exatamente o risco que o próprio
`BancoDeTeste` já documenta (truncate por classe, não por teste). Corrigido: datas relativas a
`hoje()`. Lição: fixture de data absoluta em teste de integração é bomba-relógio — vence sozinha
conforme o tempo real passa, sem ninguém tocar no código.

## Também achado: `:server:integrationTest` não roda sem Docker de pé

A mesma rodada começou com as 35 classes falhando em bloco (`IllegalStateException` em
`initializationError`, ~9s de build) — sintoma de Testcontainers não achando Docker, não de bug
de migration. Rodar de novo com Docker ativo bastou para isolar a falha real (1/213) acima. Não
é achado novo — "suíte que não roda não cobre nada" já está no `ESTADO.md` — mas vale o registro
porque o padrão de falha (todas as classes, instantâneo) é fácil de confundir com "a migration
nova quebrou tudo".

## Fora de escopo (ficou aberto, outra camada)

- Declaração de custo da fase de ênfase ao usuário via `ProgramAudit.avisos()` — débito (c)/(d)
  de "o motor sabe, o app não mostra", ainda P0, não iniciado nesta fatia.
- Expiração do foco em 2 mesociclos — rebaixada a feature de produto (2026-10-30), não é desta
  fatia.
