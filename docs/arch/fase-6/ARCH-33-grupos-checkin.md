# ARCH #33 — Grupos e check-in: confiança, não verificação

> Fase 6 · 2026-08-17, revisto em 2026-08-19 · **substitui integralmente a versão de 17/08 manhã**,
> que descrevia validadores, votação e nível de confiança — três mecanismos abandonados durante o
> desenho. Detalhe operacional ratificado em `regras/07-checkin-e-moderacao.md`
> (`fontes/fase-6-decisoes.md`, o doc de conferência v10 — FINAL que gerou esta ARCH, foi
> apagado em 2026-10-07: zero itens `[ABERTO]`, conteúdo já promovido).

## Contexto

As regras de produto #17 e #18 previam verificação social do check-in: votação de 24h entre os
membros, apuração por maioria, contestação via admin. Ao detalhar para implementação, **cada
peça de verificação caiu por um motivo diferente** — e o resultado é um sistema mais simples,
mais barato e mais honesto sobre o que garante.

Este ADR registra por que caíram, porque a tentação de reintroduzi-las vai voltar.

## As quatro quedas

### 1. EXIF — teatro de segurança

A ideia era validar a data da foto pelos metadados. Dois fatos a mataram: **forjar leva dois
toques** num app gratuito, e **WhatsApp, Telegram, Instagram e screenshot removem o EXIF**. A
regra bloquearia o usuário honesto que salvou a foto de uma conversa e deixaria passar quem
baixou um editor. Pior: daria uma sensação de rigor que faria confiar menos na verificação que
de fato funciona.

**No lugar:** foto tirada **dentro do app**, com o **timestamp do servidor**. O dado é confiável
por construção, não por inspeção. Isso também eliminou a necessidade de check-in retroativo,
que só existia para ser validado por EXIF.

### 2. Geofence de academia — contradiz o #28

Validar presença por GPS na academia exclui quem treina em casa, que é um público que o produto
**decidiu servir**. Nenhum mecanismo de integridade pode assumir que treino acontece em
estabelecimento.

### 3. Validadores — complexidade sem retorno

Um corpo de 4 validadores por grupo resolveria o admin fantasma e distribuiria o julgamento.
Custava papéis novos, regras de nomeação, reposição por antiguidade e uma cadeia de sucessão
para contestação. **Removido em favor de só `ADMIN` e `MEMBRO`.** O admin fantasma passou a ser
tratado por modo somente-leitura mais reivindicação explícita.

### 4. Votação — não escala, e é a queda mais importante

Com 50 membros e um check-in por pessoa por dia, seriam **50 votações abertas por dia**, cada
membro com 49 para votar. Ninguém faz isso. Com "silêncio aprova", o resultado prático seria
aprovação automática de tudo — votação como decoração, e ainda gerando 50 notificações diárias.

Não é bug: é aritmética do desenho. Removida.

## A decisão que sobra

**O check-in nasce VÁLIDO.** Se cumpre as regras do grupo, conta ponto imediatamente. Ninguém
aprova.

**Foto, localização e emoji do dia são AUTODECLARAÇÕES.** O servidor confere apenas o que é
estrutural — existe foto? existe coordenada? — nunca o conteúdo. A correção vem depois, por
**denúncia** julgada pelo admin.

O produto deixou de ser *verificação social* e passou a ser *confiança com correção*. O que se
perde: foto e emoji não são prova, são conteúdo social; quem quiser fraudar, frauda até alguém
denunciar. O que se ganha: zero atrito, funciona em grupo de qualquer tamanho, e nenhuma peça
que finge garantir algo que não garante.

**Se a fraude incomodar no futuro, o caminho de volta não é a votação** (que não escala) — é
tornar a denúncia mais visível e fácil.

## O buraco na FUNDAÇÃO: o usuário não tem nome

Descoberto na décima rodada de revisão, depois de nove varreduras de lacunas não o encontrarem —
porque **não é uma lacuna da Fase 6**. O `UserDto` é `id`, `email`, `isPremium`. Não existe nome
nem avatar, e nunca fez falta: até a Fase 5 o app era individual, ninguém se via numa lista com
outras pessoas.

A Fase 6 é inteiramente sobre isso. Ranking, card de check-in, comentário, denúncia — tudo
precisa dizer **quem**. Sem nome, a única identificação disponível seria o **e-mail**, e exibir
`fulano@gmail.com` para 49 desconhecidos é vazamento de PII.

**Decisão:** `display_name` em `users`, **NOT NULL**, 2 a 30 caracteres, preenchido no onboarding,
editável no perfil, **sem exigência de unicidade**. A migration faz **backfill** de quem já existe
com a parte local do e-mail (antes do `@`).

**Por que NOT NULL e não nullable:** coluna nullable espalharia `?: "Usuário"` por toda tela que
mostra gente, e uma delas ficaria esquecida — defeito que só aparece quando um usuário antigo
abre um grupo. O backfill dá um nome razoável de imediato; quem não gostar edita.

**Por que não exigir unicidade:** nome de pessoa não é identificador. Forçar "Rafael2" é hostil, e
num grupo de até 50 membros que se conhecem a ambiguidade é rara e se resolve socialmente.

**Avatar fica para a v2** — exigiria upload, moderação e storage por usuário. Inicial sobre cor
derivada do id entrega quase todo o reconhecimento visual sem nenhuma dessas peças.

**[INVARIANTE] Todo usuário tem `display_name`; o e-mail NUNCA atravessa a fronteira do grupo.**

Consequência de sequenciamento: isto é a **fatia A.0**, migration em `users` — tabela com dados
reais — e portanto **anterior** a qualquer tabela de grupo. O ranking não existe sem nome.

E o nome precisa de uma **tela**: `AppRoute.Perfil` renderiza `EmBreveScreen` desde a Fase 4, então
não há onde editá-lo. Isso virou o **ADR #34** (perfil público × configurações da conta, e
navegação por drawer), que também cresce a A.0.

## Decisões estruturais que sustentam a v2

### Estado do grupo é DERIVADO, não persistido

`AGENDADO`/`ATIVO`/`ENCERRADO` é função pura de `(data_inicio, data_fim, agora, fuso_do_grupo)`.
**Sem coluna `status`, sem job que vira a chave.**

Mesma técnica do `currentWeek` (#22) e do XP (#16). Um job de ativação seria mais uma peça
falhando em silêncio — e o `SyncWorker` da fatia B já nos custou uma noite exatamente assim.

### Enum com um valor só

`tipo_grupo` (`DESAFIO`), `modelo_pontuacao` (`CONTAGEM_CHECKINS`) e `role` (`ADMIN`/`MEMBRO`)
nascem como enum mesmo tendo um ou dois valores. É a técnica do `achievement_id` (#32): o campo
existe no schema desde já, e o segundo valor não custa migration de estrutura. Um booleano
`is_admin` exigiria backfill assim que surgisse `MODERADOR` — e nesta conversa o papel
intermediário apareceu e desapareceu antes da primeira linha de código.

### Regras configuráveis com fronteira clara

A regra obrigatória do grupo define o **mínimo para o check-in ser aceito**. `FOTO`,
`LOCALIZACAO` e `EMOJI_DO_DIA` entram na Fase 6; `GYM_PASS` nasce **declarada e indisponível**
porque depende de contrato comercial com terceiro — o tipo existe no motor, a implementação
espera.

**[INVARIANTE] `EMOJI_DO_DIA` implica `FOTO`** — reproduzir um emoji exige onde mostrá-lo, e sem
a amarração dá para configurar um grupo impossível de cumprir.

**Risco reconhecido:** cada regra booleana **dobra** os caminhos de teste. Três regras são 8
configurações de grupo. A fatia D começa com duas.

### Localização é NOME, não coordenada

O usuário **nomeia o próprio local** — o `Geocoder` nativo do Android sugere ("Ipiranga"), ele
confirma ou reescreve ("Smart Fit", "Casa"). O grupo vê só esse texto.

Três problemas resolvidos de uma vez: nome de estabelecimento exigiria a **Places API** (paga);
mostrar endereço num grupo de 50 pessoas é PII pesada, ainda mais para quem treina em casa
(#28); e mapa embutido cobra por **visualização** — cada check-in é visto por até 49 pessoas,
várias vezes ao dia.

A coordenada fica gravada **arredondada a 2 casas** (~1 km) e nunca aparece na tela. É o que
permite acrescentar mapa depois mexendo em um componente só.

### Append-only para decisões

Decisões do admin nunca são `UPDATE` — registro novo. Auditoria de graça, e é o que permite
responder "por que este check-in foi invalidado?" seis meses depois, no primeiro conflito real
entre usuários.

### Mídia atrás de interface

`ArmazenamentoDeMidia` com filesystem hoje (como o `/media` dos exercícios), S3 amanhã, sem
tocar em service nem rota.

### Banner do grupo sai da v2 e entra na Fase 6

Estava na "customização visual" adiada, e voltou por causa da **tela de preview do convite**. O
convite é o gargalo do produto: é ele que faz alguém entrar, e um convite sem identidade visual
converte menos.

Custa pouco porque a infraestrutura já está no desenho — usa o mesmo `ArmazenamentoDeMidia` da
foto de check-in. Duas diferenças: **não expira** (não tem os 90 dias, porque é identidade do
grupo e não evidência) e é editável junto com título e descrição. "Customização visual" na v2
passa a significar cores e tema, só.

## Emenda ao requisito #17 (check-in) — APLICADA em 2026-08-19

> **Cuidado com o número:** aqui `#17` é o **requisito de produto** (check-in), não o **ADR #17**
> (geração por IA stub-first). As duas sequências colidem em #16, #17 e #18 — ver o aviso em
> `arch/README.md`.

O requisito #17 definia votação de 24h, apuração ≥50% com empate aprovando, e contestação para
check-in de terceiro. **Nada disso existe.** As Regras foram emendadas na mesma data:
a [REGRA] do check-in passou a descrever a máquina real (`VALIDO` → `INVALIDADO` por denúncia
acatada), com as três regras revogadas registradas nominalmente.

Ficam **de pé** do #17: só check-in válido conta no rank, e localização é opt-in.

O registro nominal da revogação importa mais que a substituição: sem ele, alguém lê o documento
canônico daqui a seis meses e implementa a votação.

## Escopo

**Fase 6:** `display_name` do usuário (fatia A.0, pré-requisito), grupos (criar, entrar por
link/código, papéis, expulsar, sair, banner), check-in próprio com foto in-app, regras
configuráveis, pontuação e ranking interno, comentários, reações, denúncia e moderação,
notificação.

**Ordem das fatias:** **A.0** `display_name` → **A** grupo/admin/entrada/estados/preview/banner →
**B** check-in + foto → **C** pontuação + ranking (*primeiro jogável*) → **D** motor de regras →
**E** comentários/reações/denúncia → **F** notificação.

**v2:** grupo × grupo · `GYM_PASS` · tipos além de `DESAFIO` · modelos de pontuação além de
contagem · avatar do usuário · customização visual (cores e tema) · mapa embutido.

## Alternativas descartadas

| Opção | Por que não |
|---|---|
| EXIF para validar data | forjável em dois toques; removido por apps de mensagem |
| Geofence de academia | exclui o ambiente CASA (#28) |
| Votação entre membros | 50 membros = 50 votações/dia; ninguém vota |
| 4 validadores por grupo | complexidade de papéis sem retorno proporcional |
| Places API para nome do local | custo recorrente por consulta |
| Mapa embutido ou estático | cobra por visualização, e cada check-in tem até 49 |
| Coluna `status` no grupo | estado derivado nunca diverge; job pode falhar em silêncio |
| Foto obrigatória em todo check-in | storage, banda e moderação obrigatória já na v1 |
| Check-in por terceiro | removido; a votação que o justificava não existe mais |
| `display_name` nullable com fallback na UI | `?: "Usuário"` em toda tela; uma seria esquecida |
| `display_name` único | nome de pessoa não é identificador; "Rafael2" é hostil |
| Avatar do usuário na v1 | upload, moderação e storage por usuário; inicial colorida basta |

---
_Escrito em 2026-08-17, antes da primeira linha de código da Fase 6, após oito rodadas de
refinamento com o Rafael. Revisto em 2026-08-19 (v10): `display_name`, banner do grupo, e a
emenda ao #17 aplicada nas Regras._
