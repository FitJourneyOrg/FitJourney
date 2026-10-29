# Ferramentas de bancada — comandos e receitas de reset

> Índice: `docs/erros/README.md`

## Comandos de referência

```powershell
# aparelhos
adb devices                          # o físico é o que NÃO começa com "emulator-"

# log do app (push, token, desenho da notificação)
adb -s 0077706457 logcat -c
adb -s 0077706457 logcat -s FitJourneyPush:*

# o processo morreu mesmo?
adb -s 0077706457 shell ps | findstr rafael

# instalar nos dois de uma vez
.\gradlew :app:androidApp:installDebug
```

```powershell
# aparelhos registrados para push
docker exec fitjourney-postgres psql -U fitjourney -d fitjourney -c "SELECT user_id, LEFT(token, 12) AS token, updated_at FROM device_tokens ORDER BY updated_at DESC;"

# notificações gravadas
docker exec fitjourney-postgres psql -U fitjourney -d fitjourney -c "SELECT type, title, read_at IS NULL AS nao_lida, created_at FROM notifications ORDER BY created_at DESC LIMIT 10;"

# migrations aplicadas
docker exec fitjourney-postgres psql -U fitjourney -d fitjourney -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 3;"
```

## Receitas de RESET para bateria manual

As baterias das fatias E e F precisam repetir gestos que o banco só aceita **uma vez** — é o preço
de os invariantes serem índices únicos em vez de checagem de código. Estes comandos desfazem o
estado sem desfazer a regra.

> ⚠️ **Mire o GRUPO, nunca só o status.** Na bateria E.2 um `update ... where status = 'VALIDO'`
> atingiu **42.784 linhas** do `seed_volume`. Todo comando abaixo filtra pelo título do desafio.

**Denunciar de novo o mesmo alvo** (o índice único `(alvo, denunciante)` recusa a segunda). Apaga
as denúncias **e** devolve o check-in a `VALIDO` — senão ele fica `EM_ANALISE` sem caso na fila:

```bash
docker exec fitjourney-postgres psql -U fitjourney -d fitjourney -c "delete from group_reports where group_id = (select id from groups where title = 'SEU DESAFIO'); update check_ins set status = 'VALIDO' where status = 'EM_ANALISE' and group_id = (select id from groups where title = 'SEU DESAFIO');"
```

**Zerar a moderação do grupo inteiro** — devolve check-in invalidado a `VALIDO`, apaga denúncias e
limpa a auditoria. É o reset completo entre blocos da bateria:

```bash
docker exec fitjourney-postgres psql -U fitjourney -d fitjourney -c "delete from group_reports where group_id = (select id from groups where title = 'SEU DESAFIO'); delete from moderation_actions where group_id = (select id from groups where title = 'SEU DESAFIO'); update check_ins set status = 'VALIDO' where group_id = (select id from groups where title = 'SEU DESAFIO');"
```

> Apagar `moderation_actions` **só existe na bancada**. Em produção a tabela é append-only por
> decisão (6.6) e nenhum caminho do código a apaga — o `DELETE` aqui é para o teste seguinte não
> herdar o histórico do anterior e a contagem de linhas mentir.

**Repetir o aviso diário no mesmo dia** (a PK `(grupo, dia, tipo)` da V46 recusa o segundo):

```bash
docker exec fitjourney-postgres psql -U fitjourney -d fitjourney -c "delete from group_daily_notices where group_id = (select id from groups where title = 'SEU DESAFIO');"
```

**Envelhecer a fila para testar o lembrete de 3 dias** — pelo `id` da denúncia, nunca em massa:

```bash
docker exec fitjourney-postgres psql -U fitjourney -d fitjourney -c "update group_reports set created_at = created_at - interval '4 days' where id = 'COLE_O_ID';"
```

**Conferir se o `created_at` divergiu do `local_date`** depois de qualquer envelhecimento:

```bash
docker exec fitjourney-postgres psql -U fitjourney -d fitjourney -c "select count(*) from check_ins where status = 'VALIDO' and created_at < local_date - interval '2 days';"
```

Zero é o esperado. Diferente de zero significa que algum `UPDATE` de teste vazou — **o sistema não
falha, passa a mentir em metades**: prazo de denúncia e purga leem `created_at`; `canDelete` e o
"um por dia" leem `local_date`.
