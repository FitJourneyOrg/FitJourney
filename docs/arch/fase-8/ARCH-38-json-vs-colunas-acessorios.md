# ARCH #38 — Perfil: JSON em TEXT; catálogo: colunas booleanas

> Motor v3 · fatia 0 (fora do plano de fases) · decisão de 2026-10-21, escrita ao fechar
> `trabalho/acessorios-no-onboarding.md`. Migrations **V65** (`exercises.requires_*`) e **V66**
> (`profiles.accessories`).

## [REGRA] A mesma lista de seis acessórios é modelada de dois jeitos opostos, por um motivo: onde o filtro acontece

`exercises` ganhou seis colunas booleanas (`requires_bench`, `requires_elevated_surface`, ...) na
V65. `profiles` ganhou uma coluna `TEXT` com JSON (`accessories`) na V66. É a mesma informação —
quais dos seis acessórios essa linha tem ou exige — modelada de formas opostas dentro da mesma
feature.

A diferença não é estilo, é **onde cada lado é lido**:

- **Em `exercises`, o filtro vai para o `WHERE` do SQL.** O pool de exercícios candidatos por
  ambiente e nível é consultado direto no banco; colunas booleanas suportam índice e
  `WHERE requires_bench = false OR ...` sem desserializar nada.
- **Em `profiles`, a lista nunca é filtrada em SQL.** É lida inteira em código (app e motor), no
  mesmo padrão já estabelecido para `focus_areas` e `limitations` — ambos também `TEXT`/JSON.

> Seis colunas booleanas em `profiles` teriam o mesmo custo de manutenção dos seis booleanos de
> `exercises` (migration toda vez que a lista de acessórios mudar), sem o ganho que paga esse custo
> lá: não existe `WHERE` sobre o perfil.

## [INV] `NULL` × `[]` em `profiles.accessories` tem significado, e gravar errado apaga resposta de usuário

`NULL` = nunca perguntado; `[]` = perguntado, nada declarado. Colapsar os dois em `emptyList()` em
qualquer camada faz o app achar que já perguntou quando não perguntou, ou um `UPDATE` de cliente
antigo (que não manda a chave) apagar a resposta real de quem já tinha respondido.

`ProfileRepositoryImpl.upsert`: a gravação só escreve `accessories` quando `profile.accessories` não
é nulo — um `UPDATE` sem a chave preserva o valor já salvo. `ProfileDto.accessories` é
`List<Accessory>? = null`, não `= emptyList()`. Travado por
`AcessoriosPersistenciaIntegrationTest` (Postgres real), inclusive o caso de cliente antigo
reenviando o perfil sem o campo.

## Fora de escopo — achado ao fechar, não implementado

**"Banco satisfaz apoio firme"** era a intenção documentada no KDoc de `Accessory.kt`, mas **não
existe no filtro**: `ExercisePreFilter` faz `accessories.containsAll(ex.acessorios)`, puro, sem
nenhuma expansão de `BENCH` para também cobrir `ELEVATED_SURFACE`. Quem declara só `BENCH` hoje não
recebe exercício que exige só `ELEVATED_SURFACE`, embora um banco sirva fisicamente como apoio
elevado.

Resolvido corrigindo o KDoc (deixou de prometer uma implicação que o código não faz) em vez de
implementar a expansão — ver o `[REGRA]` inline em `Accessory.kt`. Se a expansão for implementada no
futuro, promover para `[REGRA]` aqui também.
