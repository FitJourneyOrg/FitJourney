# ARCH #23 — Value-first: gera antes do pagamento; gate por posse

**Fase:** 4 · **Status:** vigente

## Decisão
Gera para todos antes do paywall (viável pelo #20 — custo zero). Blur com placeholders do server. Gate: !isPremium && jaTemPrograma → 403.

## Emenda 2026-08 — o gate de LEITURA (achado em teste manual)

O blur estava aplicado **só** em `GET /programs`. O `GET /workouts/{id}` devolvia o treino
completo de um dia trancado a quem pedisse pelo id — bastava tocar no cadeado para ver os
exercícios e o botão "Iniciar treino".

**Bloqueio que só existe na vitrine não é bloqueio.** Viola a autoridade do servidor: o
entitlement precisa valer no recurso, não na listagem.

Correções:

- **[REGRA] `ProgramAccess` é a definição ÚNICA de quem vê o quê.** O corte vivia inline no
  `ProgramBlur` (`index == 0`), e foi por isso que o outro endpoint nunca soube dele. Agora o
  blur (esconde na lista) e o gate (recusa o recurso) leem a **mesma** função — se cada lado
  reimplementasse a regra, uma reordenação de agenda faria os dois divergirem: um lado vazaria
  conteúdo pago, o outro trancaria o dia grátis.
- `ProgramService.requireReadable` é o gêmeo do `requireEditable` (#25): mesma forma, mesmo
  retorno `Forbidden(ENTITLEMENT_REQUIRED)`. A diferença é que a leitura precisa do **índice**
  do treino, porque o Dia 1 é liberado.
- Cliente: `WorkoutLocalDataSource.save()` só deixa `locked` **subir**. A tabela `workout` tem
  duas fontes de escrita e só `/programs` é autoridade sobre entitlement — sem essa trava, a
  Home (que buscava o treino do dia só para estimar a duração) gravava `locked = false` por
  cima e o dia trancado aparecia liberado.
- Testes: `ProgramAccessTest`, incluindo um que compara blur e gate índice a índice. Ele quebra
  no instante em que alguém reimplementar a regra de um dos lados — que é como o bug nasceu.

**Nota de UX (2026-08):** recusar o premium **não** apaga o programa. Quem recusa fica com o
Dia 1 liberado — é o gancho do free tier. Ver o `voltarParaHome` do `AppRoute.Paywall`: no
onboarding, "Agora não" leva à Home em vez de devolver ao Reveal, que é a própria tela de oferta.

---
_Detalhe histórico: Painel Mestre v24 (PDF). Este ADR é a fonte a partir de 2026-07-26._
