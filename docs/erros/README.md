# Erros que já aconteceram — índice

> Nasceu da bateria da F.1 (2026-08-29/30), onde **cinco horas foram gastas em problemas de
> ambiente que imitavam defeitos de código**. Movido de `docs/referencia/diagnostico-ambiente.md`
> em 2026-10-07 (pasta `referencia/` apagada — só tinha este conteúdo, sem motivo pra existir
> separada). Mesma convenção do `docs/regras/`: um erro por arquivo, índice aqui, cresce conforme
> acontece um novo.

## A regra que resume a pasta

> **Antes de suspeitar do código, elimine o ambiente.** Aparelho, rede e IP produzem sintomas
> idênticos aos de defeito real, e custam segundos para descartar.

---

## Verificação de 60 segundos

Rode esta sequência sempre que algo "parou de funcionar" sem mudança de código:

```powershell
# 1. o IP da LAN mudou? (ignore o 172.x — é adaptador virtual do WSL)
ipconfig | findstr /C:"IPv4"

# 2. o app aponta para esse IP? (raiz do projeto, ignorado pelo git)
type local.properties

# 3. os dois aparelhos estão conectados?
adb devices
```

E no **navegador do celular**: `http://<IP>:8080/health` → tem que devolver
`{"status":"UP","service":"fitjourney-server","db":"UP"}`.

Se o `/health` falha, **o problema não é o app**. Pare aqui e resolva a rede.

---

## Índice

| # | Erro | Sintoma |
|---|---|---|
| 1 | [`01-servidor-nao-responde.md`](01-servidor-nao-responde.md) | "O aplicativo está no ar, mas o servidor não respondeu" |
| 2 | [`02-403-login-mas-reabre-logado.md`](02-403-login-mas-reabre-logado.md) | 403 no login, mas reabrir o app entra logado |
| 3 | [`03-push-nao-chega.md`](03-push-nao-chega.md) | Push não chega, mas o token registra normalmente |
| 4 | [`04-notificacao-invisivel.md`](04-notificacao-invisivel.md) | Notificação criada com sucesso e invisível |
| 5 | [`05-permissao-notificacao-nao-aparece.md`](05-permissao-notificacao-nao-aparece.md) | Diálogo de permissão de notificação não aparece |
| — | [`ferramentas-de-bancada.md`](ferramentas-de-bancada.md) | Comandos adb/docker + receitas de RESET pra repetir teste manual |

## Dívidas que resolveriam categorias inteiras desta pasta

- **⚪ IP fixo por DHCP.** Reservar o IP desta máquina no roteador elimina o erro 1 e o erro 2 de
  uma vez. É configuração, não código. (também em `debitos.md`, "Bancada de desenvolvimento")
- **⚪ Checagem de ambiente no boot do debug.** Um `GET /health` na abertura, com o endereço na
  tela quando falha, transformaria "investigar" em "ler". (também em `debitos.md`)

*(A terceira dívida que estava aqui — "o app não diz para onde está falando" — foi paga em
2026-09-22: `ErroDeTela` ganhou o rodapé `DEBUG: ${BuildConfig.API_BASE_URL}`. Removida desta lista
porque já não é dívida.)*

## Convenção pra manter isto útil

Erro novo que já aconteceu de verdade (não hipótese) e já tem solução confirmada → arquivo novo
aqui, numerado na sequência, mais uma linha na tabela. Erro sem solução ainda é débito
(`debitos.md`), não entra aqui até fechar.
