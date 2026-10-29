# Documentação Completa — visão, produto e roadmap

> O "o quê" e o "porquê". Atualizado **2026-09-09** — **Fase 6 CONCLUÍDA**: grupos, check-in, ranking, perfil público, amizades, emoji do dia, comentários/reações, denúncia/moderação e notificação completa. Detalhe de comportamento nas Regras (`docs/regras/`); contrato na fonte única `shared-contract`.

## Visão

FitJourney é um app de fitness gamificado, Kotlin Multiplatform (Android-first, iOS depois). Gera programas de treino inteligentes por um motor determinístico (ciência de hipertrofia, sem LLM), acompanha a execução e recompensa a consistência com XP, níveis e conquistas.

## Loop do produto

Onboarding (objetivo, nível, idade, dias, foco, ambiente, split, saúde, limitações) → Geração de um programa semanal por IA → Agenda em semanas (cronograma + calendário-âncora) → Execução de treino (log de sessão, offline-first) → Progresso & gamificação (XP/conquistas) → Social (grupos + check-in).

## Pilares

- **Motor determinístico (#20/#26):** volume por músculo é a variável-mãe; reprodutível, explicável, calibrado por nível/objetivo. Perna e braço "finos" (quadríceps/posterior; bíceps/tríceps/antebraço).
- **Multi-programa (#27):** o usuário mantém vários programas (IA e manuais), sem substituição; tetos por plano.
- **Value-first (#23):** o 1º treino do programa de IA é grátis; o resto desbloqueia com assinatura (blur no servidor).
- **Segurança primeiro:** gates de saúde (PAR-Q) e idade (#24) antes de gerar; exclusão de exercícios por limitação física.
- **Offline (#30, completo):** ler programas/treinos e registrar sessões sem rede; **criar e editar** programa/treino/agenda offline, com fila (outbox) que sobrevive a fechar o app. Login persiste offline. Gerar programa por IA e editar conteúdo de IA exigem rede — o motor e o entitlement são do servidor.
- **Confiança, não verificação (#33):** o check-in nasce **válido**; foto, local e emoji são autodeclarações, e a correção vem por denúncia julgada pelo admin. A votação entre membros foi revogada por aritmética: 50 membros seriam 50 votações por dia que ninguém faz.
- **Social como atalho, não como muro (#34/#35):** o perfil é **público** a qualquer usuário autenticado (nome, nível, XP, conquistas — e nada de histórico de treino ou saúde). Amizade não dá acesso a nada: serve para **convidar sem digitar código**. Bloqueio existe porque "remover" que não impede novo pedido é gesto vazio.
- **Correção é do grupo, não da plataforma (#33 seção 6):** quem julga uma denúncia é o **admin do desafio**, e a decisão dele encerra o assunto — não há recurso nem moderação central. Em compensação, o **denunciante é anônimo** e a decisão é **pública no feed**: sem o anonimato ninguém denuncia num grupo de conhecidos, e sem a publicidade nada segura quem decide.
- **A notificação é registro, não aviso efêmero (#36):** tudo que chega é **gravado** no servidor e vive numa central própria por 6 meses. O push é só a tentativa de avisar na hora — quem nega a permissão, ou tem o celular desligado, continua sabendo do que aconteceu.

## Monetização

Freemium. Grátis: 1 programa de IA + 2 manuais, com o programa de IA "trancado" a partir do Dia 2. Premium: até 10 programas, edição de programas de IA, e todos os dias desbloqueados. Compra atrás da interface `Billing` (hoje DevBilling; Play Billing pluga sem tocar na UI — Fase 10).

## Roadmap (fases)

| Fase | Título | Status |
|---|---|---|
| 0–3 | Fundação, backend base, auth, onboarding | concluídas |
| 4 | Treino (núcleo) — catálogo, CRUD, motor IA, multi-programa, ambientes, value-first, edição premium, agendamento, cronograma de semanas, gate de idade | concluída (2026-08-04) |
| 5 | Progresso & Gamificação — execução ao vivo, XP/níveis/conquistas (#16) | **concluída (2026-08-17)** |
| 6 | Grupos + check-in (#17/#18) | ✅ **concluída (2026-09-09)** — marco **M4 atingido**. Nove fatias: grupos e convite (A), check-in com foto e feed (B), ranking (C), perfil público (C.1), amizades (#35), notificações + push (F.1), emoji do dia (D), comentários e reações (E.1), denúncia/moderação (E.2) e os seis gatilhos de notificação (F) |
| **G** | **Idioma (ARCH #37)** | **em andamento — G.1 fechada (2026-09-09)**. Fora do plano original de fases, puxado a pedido. Meta: 100% da UI traduzida, com ressalva de conteúdo humano. Primeira leva pt-BR e en; estrutura aberta para N idiomas |
| 7 | Trial & Assinatura | feito (falta Play Billing real) |
| 8 | Conhecimento & Notificações | **a infraestrutura de notificação foi antecipada para a F.1** (ARCH #36): tipo novo entra só plugando. Resta o conteúdo (conhecimento) |
| 9 | Qualidade & Operação | a fazer |
| 10 | Beta & Deploy Android | a fazer |
| 11 | iOS | a fazer |

Marcos: M3 loop de treino jogável (atingido) · M4 social+gamificação · M5 monetização · M6 beta Android · M7 launch iOS.

## Estado atual (2026-09-09) — Fase 6 concluída

Fases 0–6 concluídas, **marco M4 atingido**. O app deixou de ser individual e o ciclo social fecha
inteiro:

- **Jogar** — criar desafio, entrar por código ou link, fazer check-in com foto (podendo exigir
  local e o **emoji do dia**), ver feed e ranking.
- **Conviver** — reagir e comentar, abrir o perfil de qualquer pessoa, adicionar como amiga,
  bloquear.
- **Corrigir** — qualquer membro denuncia um check-in ou comentário, o admin julga numa fila
  própria, e a decisão fica registrada para sempre. Como nada é verificado no momento do check-in,
  **a denúncia é a única resposta à fraude** — sem ela o produto não fechava.
- **Ser avisado** — seis gatilhos de notificação, com push e central, inclusive com o app fechado.
  Quem nega a permissão ou está deslogado não perde nada: a gravada é a verdade.

**Em andamento: a fatia G, idioma (ARCH #37).** A G.1 fechou o lado do servidor: `users.locale`
(V47), a política de fallback compartilhada entre servidor e cliente, e o `Aviso` deixando de
carregar texto para carregar chave e parâmetros. A decisão que estrutura tudo é **texto que o
usuário lê vive num lugar só, o `strings.xml` do cliente**, com uma exceção principiada: a
notificação, porque push só-dados não aparece com o app fechado. Falta a extração dos ~500 literais,
os ~104 códigos de erro, o inglês e o catálogo de exercícios.

Migrations até **V47**. Suíte com **710 testes** em 78 arquivos, verde nas cinco tasks (incluindo
integração). **Cinco baterias manuais completas** — F.1, D, E.1, E.2 e F.

Próximo: **Fase 7 em diante**, ou os débitos de prioridade alta. O maior é a ausência de testes de
integração para V40–V46: várias garantias dessas migrations (índices únicos parciais, arcos
exclusivos, cascatas) só o Postgres prova, e hoje passam por disciplina.

A F.2 proposta continua sendo o `PullToRefreshBox` nas telas cujo conteúdo muda por ação de
terceiros.

---
_Reconstruído do PDF canônico, 2026-08-06; atualizado 2026-09-07. Gerado por `docs/geradores/gen_documentacao.py`._
