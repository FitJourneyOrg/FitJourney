# Fatia H — catálogo de exercícios (H.1, H.2, sessões A e B)

> **Histórico.** Fatia fechada — preservado literal do antigo `handoff-retomada.md`.
> Estado atual em `docs/ESTADO.md`; convenções em `docs/CONVENCOES.md`;
> regra de comportamento em `docs/regras/`; débitos em `docs/fontes/debitos.md`.

---

## 🏋️ FATIA H — CATÁLOGO DE EXERCÍCIOS. H.1 e H.2 FECHADAS (2026-09-15)

Migrations até **V49**. Falta a **H.3**, que é a carga dos ~960 nomes traduzidos e depende do
Rafael.

| entrega | |
|---|---|
| **V49** `exercise_translations` | PK `(exercise_id, locale)`, `CHECK` espelhando a V47, índice por `locale` |
| Leitura por idioma | `LEFT JOIN` com fallback para `exercises.name` |
| `GET /exercises?locale=` | validado pelo `IdiomaPolicy.de`, nunca recusa |
| Cache do cliente | passou a saber **em que idioma** está guardado |
| Testes | 5 de integração + 7 unitários da decisão de rebaixar |

**Verificado no aparelho, ponta a ponta**, com uma tradução inserida à mão: em inglês o nome mudou,
em português voltou. Isso prova o join, o `?locale=` chegando, e o cache rebaixando **nas duas
direções**.

### A escolha: tabela, e o que ela custou

Era tabela contra colunas `name_en`/`name_es`. A coluna tinha uma vantagem real: o `when (idioma)`
da leitura ficaria **exaustivo**, e idioma novo quebraria o build até alguém traduzir — o mesmo
mecanismo do `TextosDeAviso`.

Perdemos isso de propósito, por causa de um número: **o app vai ter mais de quatro idiomas**. Com
dez, aquela vantagem vira vinte colunas e um build vermelho que impede lançar qualquer idioma
enquanto o último não estiver pronto.

> **A escolha entre coluna e tabela não é de modelagem, é de quantos.**

A proteção mudou de dono: saiu do compilador e virou o `CatalogoTraduzidoIntegrationTest`.

**O pt-BR não entra na tabela.** `exercises.name` é o piso, no papel que o `Idioma.PADRAO` tem no
`IdiomaPolicy` — e por isso a leitura em português **não faz join nenhum**.

⚠️ **O `LEFT` é a linha mais importante da fatia.** Com `INNER`, trocar para inglês faria o
catálogo encolher de 963 para o número de traduzidos, sem erro e sem log. Tem teste só para isso.

### ⭐ O defeito que eu introduzi consertando o defeito

O cache guardava um idioma por vez e a decisão de reusá-lo nunca perguntava qual. A correção óbvia
é carimbar por idioma (`exercises:en`) — e ela **resolve a ida e não a volta**:

| | carimbo | banco | resultado |
|---|---|---|---|
| pt-BR → en | `exercises:en` nunca marcado | — | rebaixa ✅ |
| en → pt-BR | `exercises:pt-BR` fresco de ontem | cheio, **em inglês** | pularia a rede ❌ |

> **Carimbo diz quando foi baixado. Ele não diz o que está guardado.**

São duas condições, e a segunda é a que fecha: o `ExerciseLocalDataSource` grava o idioma **na
mesma transação das linhas** — fora dela, uma falha no meio deixaria o banco afirmando português
com inglês dentro, e o app confiaria nisso para sempre.

A regra virou a função pura `precisaRebaixar`, com sete testes. Testá-la pelo repositório exigiria
`SyncStamps`, SQLDelight e um HTTP falso: três peças de infraestrutura para quatro booleanos.

### Três decisões menores que vão parecer arbitrárias depois

**O `idioma` do `ExerciseRepository` não tem valor padrão.** Um `= Idioma.PADRAO` deixaria toda
chamada antiga compilando, e é por isso que não existe — o caminho errado seria o silencioso.

**O `alternatives` traduz na BORDA**, depois do filtro, sobre os poucos ids que sobram. A
alternativa era passar o idioma para o `poolFor`, e aí o motor — que decide por taxonomia —
passaria a saber que idiomas existem para servir um caso de UI.

**O cliente manda o `?locale=` em vez de o servidor ler `users.locale`.** Aquela coluna é cópia
reconciliada numa direção só: se o `PATCH /me` falhou sem rede, ela está atrasada, e o servidor
devolveria o idioma antigo enquanto o cliente carimbaria o novo.

> **Quem cacheia a resposta tem de ser quem escolhe a pergunta.**

### ⚠️ O que a H.3 tem de desfazer

`exercise_translations` entrou no `TRUNCATE` do `BancoDeTeste`, porque hoje a tabela nasce vazia e
tudo nela é dado de teste. **Quando a carga entrar, essa linha sai**: as traduções viram dado de
referência, como os seeds do catálogo, e truncá-las faria todo teste de idioma ler o piso e passar
verde por engano. O aviso está escrito no próprio `BancoDeTeste`.

### O que ficou para depois da H.3

- **`description` e os dois regex** do `ExerciseDetailScreen` (`safetyRegex`, `warnRegex`). São
  ~960 parágrafos, e traduzi-los faz o disclaimer aparecer **duplicado** e os avisos perderem o
  destaque. Fatia própria.
- **`SAFETY_NOTE`**, literal em português no cliente, mostrado em todo exercício. Já era débito
  declarado da G.3.

---

---

## 🏋️ FATIA H — SESSÃO A FECHADA (2026-09-18)

A revisão de nomenclatura chegou pronta do Rafael (923 exercícios conferidos contra 3 quadros de
vídeo cada) e **quebrou três suposições da H.1**. A sessão A absorveu o estrago e aplicou a parte
decidida.

| migration | o que fez |
|---|---|
| **V50** | corrigiu `video_ref`/`thumb_ref` dos 900 — **as imagens estavam em 404** |
| **V51** | removeu os 39 duplicados; mídia já estava em `_removidos/` |
| **V52** | aplicou 788 nomes revisados e tirou os 9 travessões |

Verificado no banco (926 linhas), na suíte (`13 executed`) e no aparelho.

### ⭐ O defeito que ninguém tinha como ver

A revisão renomeou 1.846 arquivos de mídia para a `chave`. O banco guarda `video_ref` e
`thumb_ref` como **caminho de arquivo**, e continuou apontando para o nome por extenso. **Todos os
900 exercícios passaram a servir 404**, em silêncio, porque imagem que falta não derruba tela.

> **Caminho de arquivo guardado em banco é um acoplamento que nada no build enxerga: o dia em que
> alguém organiza a pasta, o banco fica mentindo e ninguém é avisado.**

### A ponte foi o `video_ref`, e não o nome

Casar banco × catálogo pelo NOME resolvia 93% e falhava justo onde a curadoria trabalhou. O caso
que decidiu: o banco chama `Rosca Direta com Barra no Banco Scott`; o arquivo sempre se chamou
`no colete scott` — que é o **arm blaster**, não o banco Scott. A V14 renomeou e transformou um
nome certo em errado.

> **O campo que ninguém achou importante o suficiente para curar é o que preservou a identidade.**

Cadeia usada: `video_ref` → `arquivo_gif` do backup original → `nome_original` → `chave`. Resolve
900 de 965.

### As duas lacunas opostas, e uma delas é pior do que eu disse

| | qtd | situação |
|---|---:|---|
| no banco **sem mídia** (`video_ref` vazio) | **26** | nasceram nas migrations de curadoria |
| no catálogo e **fora do banco** | **23** | têm mídia e chave; vários com sufixo `(1)`/`(2)` |

⚠️ **Eu classifiquei os 26 como cosméticos e estava errado.** A saída do
`WorkoutGenerationIntegrationTest` mostrou `Stiff com halteres` — um dos 26 — **dentro de um
programa gerado**. O motor os prescreve: 1 em cada 16 exercícios do treino de alguém chega com
miniatura em branco e nome fora do padrão.

> **Débito classificado pela lista revelou outro tamanho quando o motor o escolheu.**

### Achados de método desta sessão

**Duas guardas minhas erraram a mira.** A da V50 afirmava o formato para a tabela inteira quando a
migration tocava 900 de 965, e abortou a suíte. A mesma falha estava no teste. A correção foi
afirmar **contagem exata** em vez de propriedade universal.

> **Guarda que afirma sobre a tabela inteira quando a migration tocou uma parte dela acusa o que
> não mudou.**

**O `:server:integrationTest` deu verde sem rodar** — `BUILD SUCCESSFUL in 948ms` com Testcontainers
é impossível. Mesmo defeito que o `:konsist:test` já tinha. **Use `--rerun-tasks`.**

**Pull-to-refresh não existia.** O `ExerciseListEvent.Refresh` estava lá, com o comentário *"o
usuário pediu: fura o TTL"*, e nada o emitia. Sem ele, um deploy que corrige o catálogo leva 24h
para chegar e ninguém pode apressar — foi exatamente o que aconteceu com as mídias quebradas.

> **Evento que ninguém emite não é recurso: é a metade de um recurso que passa por pronta na
> leitura do ViewModel.**

---

### Próximo passo

**Sessão B — descrições em pt-BR.** 1,1 milhão de caracteres para 923 exercícios, mais a decisão
que arrasta cliente: o catálogo tem `descricao` (HTML) **e** `descricao_texto` (texto puro), e o
`ExerciseDetailScreen` faz `split("\n\n")` com dois regex de palavras portuguesas.

Depois: **C** (tradução: 900 nomes + 900 descrições em `exercise_translations`) e **D** (os 26 e os
23). B vem antes de C para não carregar o inglês num formato que vai mudar.

⚠️ **A G.5 nasceu de uma pergunta que nenhuma varredura respondia.** Perguntado "quais telas
faltam", o inventário respondia *nenhuma* — e respondia certo, porque ele mede LITERAIS do cliente.
As três telas de conquistas estavam em português vindas de um enum do servidor. A ordem de conferir
ficou assim, e vale repetir na fatia H:

1. varredura de literais (`inventario_strings.py`) — pega o que o cliente escreve;
2. `.name` de enum nas telas — pega identificador virando rótulo;
3. **os DTOs do contrato, campo a campo, procurando `String` que é prosa** — pega o resto, e é o
   passo que faltava.

> **Perguntar "que tela falta" devolve a resposta do medidor, não a da tela.**

---


### ✅ Fatia H fechada — sessão B (2026-09-21)

Este documento (v34) previa sessão **B** (descrições), **C** (tradução) e **D** (os 26 e os 23)
como três fatias separadas. Rodaram numa sentada só, porque parar no meio teria deixado
`exercise_translations` (V49, criada na H.1/H.2 e ociosa desde então) e a `description` de 23
exercícios inconsistentes por uma sessão a mais de espera.

| | |
|---|---|
| Descrições reescritas (288 AJUSTE/PADRONIZAÇÃO + 13 ERRO) | pt-BR e en, revisadas |
| Travessão no catálogo | **0** — achou e corrigiu 2 introduzidos pela própria reescrita desta sessão |
| Débito do cliente | fechado — `ExerciseDetailScreen` não depende mais de regex em português |
| `description` carregada (V53) | **923/923** |
| `exercise_translations` en carregada (V53/V55) | **923/923** |
| Exercícios só no catálogo, sem linha no banco | eram **23** → **0** (V55, inseridos) |
| Exercícios só no banco, sem correspondência no catálogo | eram **26** → **0** (V54: 4 consolidados, 22 removidos) |
| Migrations | V53, V54, V55 |

**Achado que muda a leitura do risco aceito no matching por `name` (V53):** 4 dos 26 "órfãos" do
banco não eram órfãos — eram o MESMO exercício do catálogo sob outra capitalização/sufixo
(`Stiff com halteres` × `Stiff com Halteres`, `Levantamento terra romeno com barra` ×
`Levantamento Terra Romeno`, e mais dois pares de panturrilha/flexora). O match por `name` é
case-sensitive, então essas duplicatas nunca teriam sido pegas pela V53 sozinha — só apareceram
porque a conferência comparou os dois lados por completo, não só validou o que já batia.
`workout_exercises` migrado pro exercício moderno antes de apagar o legado; zero perda de dado
real (os 3 que tinham uso sem equivalente foram apagados com o histórico, decisão explícita do
Rafael — ambiente de desenvolvimento, usuários de teste).

`CaminhosDeMidiaIntegrationTest` (da V50) travava 900 caminhos no formato novo / 26 sem mídia /
926 exercícios como invariante numérico fixo. Os 4 asserts quebraram — esperado, o próprio
comentário do arquivo previa o dia em que a dívida seria paga. Atualizado para 923 / zero / 923.
`:server:integrationTest` verde, 144 testes.

> **Comparação de nome que ignora capitalização é o tipo de guarda que só aparece quando alguém
> lista os dois lados inteiros, nunca quando alguém só confere o que já bateu.**

Detalhe completo (23 exercícios por categoria, decisões do Rafael passo a passo) estava em
`docs/migrations/v53-traducao/V53_conferencia*.sql`; apagado em 2026-10-07 por decisão do Rafael (migration V49-V55 já aplicada, dado de descrição já carregado no banco — não há mais segunda cópia em lugar nenhum, só o histórico git se precisar recuperar o detalhe passo a passo).

