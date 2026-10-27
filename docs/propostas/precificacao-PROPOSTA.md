# Precificação do FitJourney — avaliação de features + benchmark de mercado (PROPOSTA)

> **Status:** faixa de preço **RATIFICADA em 2026-09-26** — registrada em `painel-mestre.md` →
> Decisões recentes. Este arquivo continua existindo só pelo que falta: o número exato dentro da
> faixa e as decisões 2–5 da seção 5 (moeda fixa, trial por tempo, tetos do freemium, mensal+anual)
> — nenhuma delas resolvida ainda. As seções 1–3 abaixo (inventário + benchmark) são o
> embasamento para essas escolhas, não decisões em si; ficam como referência até a seção 5
> fechar.
> **Fonte:** inventário de features pelo código + docs (`documentacao-completa.md`,
> `painel-mestre.md`), benchmark de mercado por busca em 2026-09-25.

---

## 1. O que o FitJourney entrega hoje (inventário completo)

### 1.1 Núcleo — geração e execução de treino
- **Motor determinístico de treino por IA** (#20/#26) — sem LLM, volume por músculo como
  variável-mãe, calibrado por nível/objetivo/idade/ambiente (casa ou academia). Reprodutível e
  explicável, não uma caixa-preta.
- **Split guiado** (#29) — livre ou recomendado por dias de treino.
- **Cronograma de semanas** (#22) — duração mínima 2 meses, semana atual derivada, calendário-âncora.
- **Multi-programa** (#27) — vários programas simultâneos (IA e manuais), sem substituição.
- **"Treino ativo"** (V59) — destaque de qual treino é o foco do momento.
- **Execução de treino** — log de sessão, séries/reps/RIR, cronômetro de descanso, **offline-first**
  completo (ler e criar/editar sem rede, fila que sobrevive a fechar o app).
- **Catálogo de exercícios** — 923 exercícios com descrição, vídeo/imagem, filtro por categoria e
  grupo muscular, em pt-BR e inglês.
- **Segurança** — gate de saúde (PAR-Q) e de idade antes de gerar; exclusão automática de
  exercícios por limitação física.

### 1.2 Progresso & gamificação (#16/#32)
- XP, nível e streak.
- 9 conquistas com 3 métricas, avaliação retroativa.
- Histórico de sessões e progresso.

### 1.3 Social (#33/#34/#35/#36)
- **Grupos/desafios** — criar, entrar por código ou link, prazo definido, ranking por contagem de
  check-in (não XP — o ranking é sobre consistência, não sobre quem treina mais pesado).
- **Check-in com foto** — local opcional (opt-in, nome escolhido pelo usuário, não coordenada
  exposta), **emoji do dia** (gesto de "uma mão só", pensado pra quem tira foto sozinho).
- **Feed, comentários e reações** no check-in de qualquer membro do grupo.
- **Denúncia e moderação** — qualquer membro denuncia, o admin do grupo julga; decisão pública no
  feed, denunciante anônimo.
- **Perfil público** — nome, nível, XP, conquistas visíveis a qualquer usuário autenticado.
- **Amizades** — convite por código de 8 caracteres, sem precisar digitar nada manualmente; bloqueio.
- **Notificações** — central própria (6 meses de retenção) + push, 6+ gatilhos diferentes
  (comentário, denúncia, entradas do dia, etc.) — funciona mesmo com o app fechado.

### 1.4 Base
- Português (pt-BR) e inglês, com troca de idioma no app.
- Autenticação Firebase, app 100% funcional offline pra tudo que não seja gerar/editar por IA.

**Leitura pra precificação:** isto não é um app de UMA coisa. É motor de IA (categoria Fitbod) +
tracker/log (categoria Strong/Hevy) + comunidade com gamificação social (categoria mais rara —
nenhum concorrente pesquisado tem essa combinação inteira).

---

## 2. O que já é grátis vs premium HOJE (código, não proposta)

| | Grátis | Premium |
|---|---|---|
| Programas de IA | 1, com Dia 1 liberado e o resto trancado a partir do Dia 2 | edição liberada em todos os dias |
| Programas manuais | até 2 | — |
| Total de programas | 3 (1 IA + 2 manuais) | até 10 |
| Execução, histórico, gamificação, social, catálogo | tudo liberado | tudo liberado |

Ou seja: o corte de valor é só **quantidade de programas + edição de conteúdo de IA**. Todo o
resto (o que mais diferencia o app — social, gamificação, execução offline) é **igual pros dois
planos**. Não existe hoje nenhum trial por TEMPO (X dias grátis) — é freemium por feature/limite,
sempre disponível.

---

## 3. Benchmark de mercado (2026)

### 3.1 Internacional

| App | Categoria | Mensal | Anual | Vitalício | Tem grátis pra sempre? |
|---|---|---|---|---|---|
| Hevy Pro | tracker/log | US$ 2,99 | US$ 23,99 | US$ 74,99 | sim (com limite) |
| Strong Pro | tracker/log | US$ 4,99 | US$ 29,99 | US$ 99,99 | sim (com limite) |
| Jefit Elite | tracker + comunidade | US$ 12,99 | US$ 69,99 | — | sim (com anúncio) |
| Fitbod | gerador de treino por IA | US$ 15,99 | US$ 95,99 | promo só | não (trial vira somente-leitura) |
| SensAI | gerador de treino por IA | US$ 6,99 | US$ 69,99 | — | não |
| Freeletics Coach | coach IA (corpo livre) | — (só anual) | US$ 94,99 | US$ 549,99 | não (34 treinos fixos, "demo") |
| Dr. Muscle | coach IA premium | ~US$ 49 | — | — | tem plano free reduzido |

**Padrão claro:** tracker puro (Hevy/Strong) é barato (US$ 3–5/mês) porque só registra o que você
já sabe fazer. Gerador por IA de verdade (Fitbod/SensAI/Freeletics) cobra 3 a 5× mais, porque
substitui a decisão de "o que treinar hoje" — é o valor que se cobra, não a tela.

### 3.2 Brasil (referência local)

**Gymnamic** (treino personalizado + vídeo, sem motor de IA declarado): R$ 59,90/mês avulso,
R$ 49,90/mês no trimestral, **R$ 39,90/mês no anual** (R$ 478,80/ano) — com trial de 7 dias.

Isso dá uma âncora de PPP: o mercado brasileiro de treino por assinatura já aceita a faixa de
R$ 40–60/mês quando há personalização real, bem acima de "quanto custa um app de treino
qualquer" e bem abaixo do preço em dólar convertido direto (R$ 59,90 ≈ US$ 11, não US$ 40+).

---

## 4. Onde o FitJourney se encaixa — RATIFICADO (ver `painel-mestre.md`)

Motor de IA determinístico (categoria Fitbod/SensAI, tier de US$ 7–16/mês lá fora) **mais**
comunidade com gamificação de verdade (categoria Jefit, que cobra mais que tracker puro só pela
comunidade) **mais** offline-first completo (nenhum concorrente pesquisado promete isso). Ao mesmo
tempo, sem coach humano, sem nutrição, sem vídeo-aula guiada — não é Dr. Muscle nem Gymnamic.

A faixa resultante (US$ 9,99–14,99/mês / R$ 24,90–34,90/mês, detalhe no Painel Mestre) foi
ratificada em 2026-09-26. O que falta é só o número exato dentro da faixa — ver seção 5.

---

## 5. Decisões que só você toma (antes de eu tocar em código de billing)

1. **Preço final** — dentro da faixa acima, ou outra referência que você tenha (concorrente
   direto que conhece, teto de disposição a pagar da sua base).
2. **Moeda** — BRL fixo (Play Console permite fixar por país) ou deixar a conversão automática do
   Google a partir de um preço-base em USD? Faz diferença pro brasileiro pagar R$ 24,90 fixo vs.
   pagar a cotação do dia de um preço em dólar.
3. **Trial por tempo** — hoje não existe. Vale adicionar (ex: 7 dias premium completo, decai pro
   freemium atual) além do freemium por feature já implementado, ou o freemium atual já é
   suficiente como "trial"? Isso muda o desenho técnico (precisa de data de expiração de trial no
   banco, hoje inexistente).
4. **Revisar os tetos do freemium** — 1 IA + 2 manuais / 10 premium continuam fazendo sentido com
   o preço escolhido, ou a régua deveria mudar junto (ex: apertar o grátis se o preço for mais
   baixo, ou o contrário)?
5. **Mensal only, anual only, ou os dois?** Freeletics só vende anual (força compromisso, preço
   auto mais alto); a maioria vende os dois com desconto no anual — parece o caminho mais seguro
   aqui dado que o app ainda não tem base validada.

Assim que essas cinco estiverem decididas, o desenho técnico de assinatura de verdade
(`subscriptions` com estado/expiração/renovação, não só o `is_premium` booleano de hoje) fica
trivial de especificar.
