# Rationale derivado — V56 (2026-09-22)

> **Histórico.** Fatia fechada — preservado literal do antigo `handoff-retomada.md`.
> Estado atual em `docs/ESTADO.md`; convenções em `docs/CONVENCOES.md`;
> regra de comportamento em `docs/regras/`; débitos em `docs/fontes/debitos.md`.

---

### ✅ Fatia "rationale derivado" fechada (2026-09-22)

O `rationale` era o maior débito de arquitetura remanescente da área de idioma (ver seção "Ainda
falta" da G.4, abaixo): frase pronta em PORTUGUÊS, montada pelo `StructureEngine` e **gravada no
banco** (`programs.rationale`), exibida sem tradução no `ProgramDetail` e no `ProgramReveal` —
contável desde a G.4 no `TextoDoServidorTest`, e violando a mesma regra já aplicada a XP/nível:
**estado que é função de outra coisa é DERIVADO, não persistido** (#22, #33) — aqui a função era
`split` + `daysPerWeek` + `focusAreas`. Carregava também o último travessão do produto fora do
alcance da varredura do `strings.xml` (o `rationale` nasce no servidor, não no cliente).

Fechado do jeito que o débito sempre previu: backend estruturado → contrato tipado → cliente monta
a frase, mesma técnica da G.2 com os erros.

| | |
|---|---|
| Servidor (**V56**) | parou de gravar a frase; passa a persistir `split` como CHAVE do enum (`SplitType.name`, não mais o rótulo renderizado) e `focusMuscles` (snapshot JSON do foco NO MOMENTO da geração). Coluna `rationale` **DROPADA**, não deixada vestigial |
| Guarda na migration | `DO $$ ... RAISE EXCEPTION` se sobrar valor de `split` fora do mapeamento label→enum conhecido — a V53/V54 (mesma fatia H) não tinham essa guarda, achado de um PR review anterior |
| Cliente (`4.sqm`) | mesma troca (chave + nullable) no schema local; `split`/`focusMuscles` recriados via `INSERT...SELECT` preservando o programa manual offline não sincronizado — nunca `DROP`+`CREATE` vazio |
| Frase montada em runtime | `rationaleDoPrograma()` (`RationaleDePrograma.kt`), no idioma da tela — reaproveita `SplitType.rotulo()/.descricao()` e `MuscleGroup.rotulo()`, que já existiam desde a G.3 e só não estavam ligados a este dado |
| `split` nos DTOs/domain | virou `SplitType?`/chave `String?` em toda a cadeia (contrato → server → cliente); `null` = programa MANUAL (shell sem motor) — antes era `""`/`"Manual"`, o mesmo truque stringly-typed que esta fatia está removendo |
| `TextoDoServidorTest` | caiu de 9 para 7 pontos contáveis — `ProgramDetail`/`ProgramReveal` saíram da lista |
| Achado no review (REV, não implementação) | o KDoc do `SplitType` no `shared-contract` ainda dizia que `label`/`description` eram "chave interna do motor" e que o `WeekSpread` comparava por `.label` — exatamente o que esta fatia eliminou. Corrigido para refletir que hoje só a CHAVE (`.name`) é comparada/persistida; `label`/`description` do enum viraram documentação, não dado lido em runtime |
| Verificação | compilado e testado verde pelo Rafael (`:server:test`, `testAndroidHostTest`) — não passou pela sandbox, só pelo aparelho dele |

Detalhe completo (SQL da V56, motivo da decisão de não retroalimentar `focusMuscles` em programas
legados) em `debitos.md`.

