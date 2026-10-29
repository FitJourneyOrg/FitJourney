# ARCH #32 — Conquistas: regra derivada, desbloqueio persistido

> Fase 5 · 2026-08-17 · fecha a Fase 5 (Progresso & Gamificação)

## Contexto

XP, nível e streak (#16) são **100% derivados** das sessões: recalculados a cada consulta, sem
saldo mutável a corromper. A pergunta natural ao acrescentar conquistas era aplicar o mesmo
princípio — e a resposta é **não**, por um motivo que só aparece meses depois.

## Decisão

**A regra é derivada e pura; o desbloqueio é persistido.**

- `AchievementPolicy` (Kotlin puro, sem I/O) decide o que o progresso alcança. Testável sem
  banco, como o `XpPolicy`.
- `user_achievements (user_id, achievement_id, unlocked_at)` grava a concessão. PK composta.
- Uma vez concedida, **nunca é retirada**. O `AchievementRepository` sequer expõe um `delete` —
  é a forma mais barata de garantir a invariante.

### Por que não 100% derivado

Se o limiar de um streak subir de 7 para 10 dias, o cálculo puro faz a medalha **desaparecer**
do perfil de quem já a tinha visto. Tirar do usuário uma recompensa já entregue destrói a
confiança em todas as outras — e o pior é que ninguém percebe no code review, porque o código
"continua correto". Derivado puro também não sabe **quando** desbloqueou, então não há como
notificar nem marcar "novo".

O `unlocked_at` transforma o desbloqueio em fato histórico, e não em consequência da versão
atual do código.

### Consequência aceita

`current` pode ficar **abaixo** de `target` numa conquista desbloqueada — streak quebra e a
medalha continua. Não é inconsistência: é a diferença entre marco e estado atual. Por isso a UI
lê `unlockedAt` e **nunca** infere de `current >= target`, e a barra de progresso só aparece nas
bloqueadas (mostrá-la incompleta numa medalha ganha sugeriria que ela está em risco).

## Onde a avaliação dispara

Na **leitura** (`GET /me/achievements`), não no `POST /sessions`.

Avaliar na leitura dá o retroativo de graça — quem já tinha 60 treinos recebe na primeira
abertura da tela, sem migration de backfill — e mantém o registro de sessão com uma
responsabilidade só. O custo é uma escrita eventual dentro de um GET, idempotente pela PK.

**Débito:** quando existir notificação de desbloqueio, o `POST /sessions` precisa avaliar
também; senão o usuário só descobre a medalha ao abrir a tela.

## O catálogo

Nove conquistas em três métricas: `SESSOES` (1/10/50/100), `STREAK` (7/30/90) e `NIVEL` (5/10).

**Não existe "treinou N dias seguidos" literal**, e isso é deliberado. O app prescreve descanso
(#22/#26); premiar treino diário empurraria o usuário contra o próprio programa. A métrica de
streak reusa a definição do `XpPolicy`, em que **dia de descanso agendado conta como cumprido** —
a conquista recompensa seguir o plano, não ignorá-lo.

`achievement_id` é TEXT, não enum do Postgres: conquista nova não deve exigir migration. Em
troca, um id removido do código pode sobrar no banco; o service ignora em silêncio, porque
derrubar a tela por causa de uma linha órfã seria desproporcional.

**[REGRA] Os ids são contrato.** Renomear um id existente reescreveria a história de quem já o
tem. Conquista nova ganha id novo.

## Contrato

O servidor manda o **catálogo inteiro**, bloqueadas incluídas, com título e descrição. Duas
razões: a tela precisa mostrar o próximo marco (uma grade só com o que já se tem não sugere o
que fazer), e ajustar texto ou limiar deixa de exigir publicar versão nova do app.

## Alternativas descartadas

| Opção | Por que não |
|---|---|
| 100% derivado, como o XP | medalha some se a regra mudar; sem data, sem notificação |
| Enum no Postgres para o id | conquista nova passaria a exigir migration |
| Backfill na V34 | a data histórica real exige reprocessar sessão a sessão; só se paga se a data for exibida |
| Avaliar apenas no `POST /sessions` | sem retroativo — quem já treinava não receberia nada |

## Onde mora

Cliente: `app/androidApp/data/achievements` e `screens/achievements`, junto do `stats`.

O certo pela arquitetura seria `shared/features/achievements/{data,domain,presentation}`. Mas o
irmão direto (`Stats`, `SessionSync`) já está no módulo app como atalho conhecido, e criar três
módulos para o caçula deixaria a gamificação repartida em dois padrões. Preferi consistência
local e engrossar o débito que já existe: **extrair stats + session + achievements para features
próprias**.

Cor: `lime` (`colorScheme.tertiary`) — [REGRA] #16, exclusiva das recompensas do perfil
individual. A tela de conquistas é exatamente onde ela pertence.

---
_Escrito em 2026-08-17, ao fechar a Fase 5._
