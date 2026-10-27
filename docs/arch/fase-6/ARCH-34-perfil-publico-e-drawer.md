# ARCH #34 — Perfil público × minha conta, e navegação por drawer

> Fase 6 · 2026-08-20 · antes da primeira linha de código da fatia A.0.
> Depende do `display_name` decidido no **ADR #33** (seção "O buraco na FUNDAÇÃO").

## Contexto

A fatia A.0 acrescenta `display_name` ao usuário e imediatamente esbarra num vazio: **não existe
tela de perfil**. `AppRoute.Perfil` está no bottom nav desde a Fase 4 e renderiza
`EmBreveScreen("Perfil", "Seus dados, plano e configurações.")`. A decisão 1-A.4 — nome editável
no perfil — não tinha onde morar.

Ao mesmo tempo o produto ganhou uma ambição nova: um perfil **estilo rede social**, com nível,
conquistas e nome, mais um **menu lateral** para os destinos que não são tarefa do dia a dia.

Este ADR decide as duas coisas juntas porque uma define a outra: é o drawer que dá lugar ao
perfil, e é o perfil que esvazia o drawer de configurações.

## 1. São DUAS telas, e a fronteira é o DTO

**Perfil** é público: o que qualquer pessoa vê. **Configurações da conta** é privado: plano,
preferências, sair. Nunca renderiza outra pessoa.

A rota do perfil é `Perfil(userId: String?)` — a **mesma** tela renderiza qualquer um, `null` = eu.
Isso força o desenho a nascer público: o que está ali é o que todo mundo vê. Perfil de outra
pessoa é o mesmo composable sem o cartão privado e sem o lápis de editar.

### [INVARIANTE] Dado privado não existe no `PublicProfileDto`

**"Desafios concluídos" é privado** — só o dono vê. A tentação é um campo anulável no DTO público,
preenchido quando `id == eu`. **Não.** O dado vem de uma rota separada (`/me/challenges`), e o
campo **não existe** no contrato público.

O motivo é o histórico deste projeto: toda vez que o isolamento dependeu de alguém lembrar de
escrever um `if`, ele falhou. Foi assim com o uid no cache local, e é a mesma razão pela qual
`achievement_id` é chaveado por usuário na PK e não filtrado no Kotlin. **Separação por
construção, não por condicional** — um campo que não existe no DTO não vaza por descuido.

Mesma regra já valendo no #33: o e-mail nunca atravessa a fronteira do grupo.

## 2. O que o perfil público mostra

| Aparece | Não aparece |
|---|---|
| `display_name` | e-mail |
| inicial sobre cor derivada do id | XP bruto |
| nível | streak |
| conquistas (desbloqueadas e bloqueadas) | programas e treinos |
| total de treinos, tempo de app | peso, altura, idade |
| — | limitações de saúde, PAR-Q |
| — | desafios concluídos (privado, ver §1) |

As três últimas da coluna direita são **PII de saúde**: não podem sequer chegar ao DTO público.

**Por que nível e conquistas SIM, mas XP bruto e streak NÃO.** É [REGRA] do #18 que grupo pontua
por contagem de check-ins, **nunca** por XP. Se do ranking do grupo eu toco num nome e vejo o XP e
o streak da pessoa, o perfil individual vira placar paralelo do grupo na cabeça do usuário. Nível
e conquistas são marcos discretos e envelhecem devagar; XP e streak são o número que muda todo
dia, e é ele que gera comparação. Mostrar o marco, esconder o contador.

**Risco reconhecido e aceito:** `lime` é [REGRA] exclusiva do perfil individual (#16), e um perfil
cheio de lime alcançado de dentro do grupo tensiona essa fronteira. A mitigação é o perfil ser uma
tela do perfil individual — nunca embutido em tela de grupo — e nenhum elemento de grupo usar lime.

### Visibilidade **[EU — confira]**

Vê o perfil de alguém quem **compartilha um grupo** com essa pessoa; eu vejo o meu sempre. Sem
essa regra o `userId` vira enumerável e o app entrega perfil de qualquer um a quem itere ids.

## 3. Navegação: drawer global + bottom nav com 4 abas

**Perfil sai do bottom nav.** As abas ficam Início · Treino · Grupos · Progresso — quatro tarefas
frequentes. O drawer fica com o secundário: perfil, exercícios, wiki, dúvidas, configurações, sair.

A objeção inicial era ter duas superfícies de navegação disputando os mesmos destinos, que é o que
o Material desaconselha. **Com o Perfil saindo de baixo, os conjuntos ficam disjuntos** e a
objeção cai: bottom nav = tarefa frequente, drawer = destino secundário. É o uso correto dos dois.

- **O drawer é GLOBAL**, presente em todas as telas-raiz (as mesmas que mostram a bottom bar). Se
  abrisse só na Home, chegar em Configurações exigiria voltar para a Home primeiro.
- **[REGRA] Item de drawer NAVEGA.** Toggle não é item de drawer — quebra a expectativa do
  componente e some do backstack.
- O cabeçalho do drawer (inicial + nome + nível) é **cabeçalho, não item**, e leva ao perfil.
- **`Sair` muda de casa**: hoje mora num ícone na saudação da Home, lugar onde ninguém procura.
  Vai para o rodapé do drawer.

## 4. Conquistas têm UM dono: o perfil

Hoje se chega por Progresso → Conquistas. Com o perfil exibindo a grade, seriam dois caminhos —
três, se o drawer também listasse. O cartão do Progresso passa a apontar para o perfil; a tela
`Conquistas` continua existindo como o "ver todas"; o drawer não lista.

## 5. Tema claro: DESCARTADO

Pedido e retirado na mesma conversa, e vale registrar por quê — a ideia vai voltar.

`FitJourneyTheme` só declara `darkColorScheme`. O app é **dark-only por construção**, não por
configuração: não existe tema claro para alternar. Um "modo claro" é paleta nova e revisão de
todas as telas — e, especificamente aqui, revalidação do `Volt` (`#CCFF00`), que é uma cor que só
tem contraste sobre fundo escuro **e carrega significado por [REGRA]** (#16).

Não é item de menu. Se um dia entrar, é fatia própria, provavelmente na Fase 9.

## 6. Escopo e sequência

- **A.0** — `display_name` (server → contrato → onboarding) · **drawer** · Perfil sai do bottom nav
  (4 abas) · **Configurações da conta** com nome editável e `Sair`. Entregável e testável sozinha.
- **C.1**, depois da fatia **C** (ranking) — **perfil público de terceiro**:
  `GET /users/{id}/profile`, `PublicProfileDto`, regra de visibilidade, desafios concluídos
  (privado). Antes de existir grupo, essa tela não tem porta de entrada real.

Meu próprio perfil é alcançável desde a A.0 pelo drawer; o que espera a A.1 é ver o **dos outros**.

**Wiki fitness** (Conhecimentos) é **Fase 8**. Entra no drawer já na A.0 com selo de fase — item
que promete e abre `EmBreve` irrita; item que diz quando chega, não.

## Alternativas descartadas

| Opção | Por que não |
|---|---|
| Uma tela só, com `if (souEu)` | cada campo novo vira pergunta de visibilidade; um esquecimento vaza PII |
| Campo anulável no DTO público para dado privado | o campo existe no contrato; basta um `if` esquecido |
| Manter Perfil no bottom nav E ter drawer | duas superfícies disputando o mesmo destino |
| Drawer só na Home | volta à Home para chegar em Configurações |
| Dark/light mode como item de menu | não existe tema claro; e toggle não é item de drawer |
| Conquistas também no drawer | terceiro caminho para a mesma tela; nenhum vira o óbvio |
| XP e streak no perfil público | vira placar paralelo do grupo, contra a [REGRA] do #18 |
| Foto de avatar na v1 | upload, moderação e storage por usuário (ver #33: inicial colorida) |

---
_Escrito em 2026-08-20, depois de o layout ser validado em mockup e antes do código._
