# Erro: app diz que o servidor não respondeu

> Índice: `docs/erros/README.md`

### 1. "O aplicativo está no ar, mas o servidor não respondeu"

`AppError.Connection` — o app não alcançou nada.

| Causa | Como confirmar | Correção |
|---|---|---|
| **IP da LAN mudou** (o mais comum — aconteceu 3× numa sessão) | `ipconfig` mostra IP diferente do `local.properties` | atualizar `apiBaseUrl` + `installDebug` |
| **Arquivo gravado em UTF-16** | `type` mostra espaços entre letras, ou lixo | `Set-Content … -Encoding ASCII` |
| Ktor parado | terminal do `:server:run` fechou | subir de novo |
| APK velho | mudou o `gradle.properties` e não reinstalou | `installDebug` — o valor é **embutido no build** |
| Aparelho em outra rede | Wi-Fi vs. dados móveis | mesma rede do PC |

> ⚠️ **`echo x > arquivo` no PowerShell grava UTF-16LE com BOM.** O Gradle lê `.properties` como
> texto simples e não entende. Use sempre `Set-Content -Encoding ASCII`, ou edite pelo editor.

> ✅ **O endereço agora aparece em todo build:**
> `FitJourney: API_BASE_URL = http://192.168.15.8:8080`.
> Confira essa linha ANTES de abrir o app — se ela mostrar `10.0.2.2`, o `local.properties` não
> foi lido, e você descobre em segundos em vez de meia hora.

**Onde configurar** (precedência): `local.properties` na raiz → `gradle.properties` global →
default `10.0.2.2`. O default é comitado; o IP da sua rede fica só no `local.properties`, que o
git ignora.

> ⚠️ **`Responding at http://127.0.0.1:8080`** no log do Ktor é normal e não diz o IP que o
> celular deve usar. Use o IPv4 do **adaptador Wi-Fi**, nunca o `172.x` do WSL/Hyper-V nem o
> `127.0.0.1`.
