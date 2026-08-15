# Migrations do banco local (SQLDelight)

O banco local **deixou de ser descartável**: com offline-first ele guarda dado que só existe
no aparelho (sessões com `pending = 1`, ainda não enviadas). Apagar significaria perder treino
do usuário.

## Onde cada coisa mora

```
src/commonMain/sqldelight/dev/rafael/core/database/
├── 1.sqm              ← O SCHEMA (todas as tabelas + índices)
├── cache.sq           ← só QUERIES
├── exercise.sq        ← só QUERIES
├── profile.sq         ← só QUERIES
└── workoutSession.sq  ← só QUERIES
```

**[REGRA] `.sq` e `.sqm` ficam no MESMO diretório** — o do pacote (`dev/rafael/core/database/`).
O SQLDelight deriva o pacote Kotlin do caminho da pasta; como o schema vem do `.sqm`, pôr a
migration numa subpasta (`migrations/`) gera as data classes num pacote diferente e o projeto
não compila (`Unresolved reference 'Exercise'`). Se quiser separar visualmente, use o NOME —
nunca a pasta. *(Validado em build: 2026-08-13.)*

O nome do arquivo `.sq` também importa: ele define o objeto de queries gerado
(`workoutSession.sq` → `db.workoutSessionQueries`).

## Como mudar o schema a partir de agora

1. **O schema NÃO fica nos `.sq`** — eles só têm queries. O schema vive nas migrations
   (`deriveSchemaFromMigrations = true`), que é o que permite migrar um banco antigo.
2. Crie a migration em `N.sqm` (na mesma pasta dos `.sq`), onde `N` é a **versão de origem**:
   - próxima mudança → `2.sqm` (migra da v2 para a v3)
   - depois → `3.sqm`, e assim por diante.
3. Escreva só o `ALTER`/`CREATE` necessário:

   ```sql
   -- 2.sqm — exemplo
   ALTER TABLE workout_session ADD COLUMN durationSeconds INTEGER;
   ```

4. Se a mudança criar/alterar tabela, ajuste também as queries nos `.sq` que a usam.
5. Buildar. Erro de geração aparece em build, não em runtime.

## O que NÃO fazer

- **Não** bumpe o nome do arquivo do banco (`fitjourney.db`). Era o truque antigo — recriava
  o banco e apagava o que não tinha sido sincronizado.
- **Não** ponha `.sqm` em subpasta (ver a [REGRA] acima).
- **Não** edite uma `.sqm` já publicada: quem já migrou não a roda de novo. Crie a próxima.
- **Não** use `ON CONFLICT ... DO UPDATE` (upsert): exige SQLite 3.24 e o `minSdk 24` alcança
  aparelhos com versão anterior — quebraria em runtime. Use `INSERT OR REPLACE`.

## Histórico

Antes disto o banco era versionado pelo nome (`fitjourney_v1..v4.db`) e cada mudança de schema
recriava tudo — aceitável quando o local era só cache. A transição para `fitjourney.db` com
migrations reais, junto com a tabela `workout_session` (histórico + fila de envio), é a
**última** perda de dado local do projeto.
