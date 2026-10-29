# Motor v3 — Fatia 3: alocação por prioridade (fechada em 2026-10-23)

Terceira fatia do motor v3, etapa [3] — "o coração do redesenho". `PriorityAllocation.alocar()`,
espelhando `tools/motor_sim/motor_v3.py::alocar()` termo a termo: piso para todos, rebaixamento
na ordem declarada, sacrifício na mesma ordem se nem isso couber, excedente proporcional ao peso
de prioridade, arredondamento final em ordem de prioridade.

**A abertura da fatia corrigiu o plano em três pontos**, todos confirmados rodando
`tools/motor_sim/` de verdade, não supostos:

1. **A troca do `StructureEngine` em produção não é desta fatia.** O `desenho-e-fatias.md`
   sugeria isso na tabela §2.4, mas contradiz a condição do Rafael — "só liga depois do
   diferencial contra o simulador verde", e o diferencial só fecha na Fatia 9. Ligar a alocação
   nova à produção agora misturaria peças novas com estrutura/composição/preenchimento ainda
   velhos. Movida para depois da Fatia 9, junto com a remoção do `VolumeModel.kt` antigo.

2. **O ciclo do ponto fixo do indireto não é desta fatia.** `alocar()` recebe `direta`/`indireto`
   já resolvidos — o laço que faz indireto → alocação → estrutura → composição → compostos de
   verdade → indireto de novo só existe a partir de quando há séries de composto medidas sobre o
   entregue (Decisão B), e isso só acontece com a composição (Fatia 5). Atribuição movida da
   Fatia 3 para a Fatia 5 no `desenho-e-fatias.md`.

3. **Os 2 vermelhos do diferencial (`test_diferencial_contra_o_modelo_a_mao`, ADVANCED,
   POSTERIOR −0,8) não são bug de alocação.** Isolei `mv.alocar()` com os mesmos números desta
   fatia: converge limpo, sem escassez nem oscilação, nos perfis que falham — a causa é outra.
   GLUTES tem demanda direta 4,0/semana no avançado. `modelo_a_mao.py` assume frequência fixa 2×
   para todo músculo (simplificação documentada no próprio arquivo, o método da especificação
   para calcular os números de referência) → 2 séries/sessão → abaixo do piso de composto (3) →
   0 composto → credita 0 ao posterior. `motor_v3.py` aplica a frequência efetiva de verdade
   (Fatia 4): `4 // piso_do_papel(GLUTES)=3 → frequência 1`, então GLUTES treina 1×/semana com as
   4 séries juntas, que passam o piso → vira composto → credita 0,2×4=0,8 ao posterior. Fica
   registrada como pergunta aberta da Fatia 9: lá é preciso decidir qual lado está certo, e o
   modelo à mão não pode copiar a lógica do motor (viraria tautologia). O 3º vermelho
   (`ombro_vira_isolamento` nunca dispara) já era da Fatia 7, confirmado, não tocado aqui.

**Achado ao implementar:** `alocar()` não garante que a soma do resultado caiba na capacidade
quando a escassez é extrema — CHEST, BACK, QUADS e POSTERIOR nunca são rebaixados nem
sacrificados (ficam fora de `ORDEM_SACRIFICIO`), e se o piso deles por si só já excede a
capacidade, o algoritmo não tem mais o que cortar. Testado explicitamente (capacidade 29,33,
resultado soma 30) — é comportamento do próprio oráculo, não lacuna desta implementação. Quem
resolve isso de verdade é o laço de corte por tempo (Fatia 8).

**Teste (`AllocationTest`, 8 casos, verde):** 5 capacidades com saída EXATA contra a função
Python (sem escassez; só escassez; +1 rebaixamento; +rebaixamento+sacrifício; escassez extrema),
ordem de sacrifício, piso do papel, e a bateria estilo Fatia 7 provando que escassez/
rebaixamento/sacrifício disparam pelo menos uma vez cada.

Nenhuma migration.

**Nota retroativa (Fatia 8, 2026-10-24):** `ORDEM_SACRIFICIO`, declarada aqui como lista fixa
(antebraço, panturrilha, abdômen, glúteo, bíceps, tríceps, ombro), discordava do
`PESO_PRIORIDADE` desta mesma fatia em dois pontos: GLUTES (peso 0,7, mais pesado que
BICEPS/TRICEPS) vinha ANTES deles na ordem de sacrifício, e BICEPS (peso 0,5, o mais leve dos
três) vinha DEPOIS. A bateria de coerência da Fatia 8 (comparar a ordem contra o peso, perfil a
perfil) foi o que expôs a discordância — a validação diferencial contra o oráculo desta fatia
não bastou, porque o oráculo tinha a MESMA lista fixa, com a MESMA discordância: os dois lados
concordavam entre si e discordavam do peso ao mesmo tempo. `ORDEM_SACRIFICIO` passou a ser
derivada de `PESO_PRIORIDADE` (ver `docs/regras/01-motor-de-geracao.md`, `[INV]` fonte única) —
medido nos 720 perfis, a correção não muda nenhum resultado desta fatia nem dos 156 eventos de
zeramento da Fatia 8 (o laço de sacrifício nunca é alcançado nesses casos), mas a `[INV]` vale
pela consistência interna. Ver `docs/historico/motor-v3-fatia-8.md`.
