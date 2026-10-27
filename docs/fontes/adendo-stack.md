# Adendo — Stack técnico (em uso)

> Tecnologias **efetivamente wired e usadas** até o momento atual do projeto (**Fases 0–6 concluídas; trabalho atual rastreado por fatia, não por fase — fatia J fechada em 2026-10-04**; migrations Flyway até **V61**, schema local SQLDelight até **8.sqm**). Versões conforme `gradle/libs.versions.toml`. Uso confirmado nos `build.gradle.kts` — não é só o que está declarado no catálogo.

## Stack em uso

| Tecnologia | Versão | Camada | Papel |
|---|---|---|---|
| Kotlin (KMP) | 2.4.0 | Build | Linguagem; código compartilhado Android (iOS é futuro) |
| Android Gradle Plugin | 9.0.1 | Build | Build Android (Gradle 9.x no wrapper) |
| Android SDK | compile/target 36 · min 24 | Build | Alvos da compilação Android |
| Konsist | 0.17.3 | Build/Arquitetura | Testa regras de dependência (feature não depende de feature) |
| Ktor (server) | 3.5.0 | Server | Rotas, auth plugin, content-negotiation, status-pages, call-logging |
| Exposed v1 (`org.jetbrains.exposed.v1.*`) | 1.0.0 | Server | SQL/ORM sobre PostgreSQL |
| HikariCP | 6.2.1 | Server | Pool de conexões |
| PostgreSQL (driver JDBC) | 42.7.5 | Server | Banco relacional. **`implementation`, não `runtimeOnly` (F.1)**: `PGobject` é necessário para gravar `JSONB` — o Postgres não faz cast implícito de `varchar`. A nota "código não referencia o driver" descrevia uma independência de banco que as migrations já não tinham |
| Flyway (+ postgresql) | 11.8.0 | Server | Migrations versionadas **V1–V61** (schema é a fonte da verdade) |
| Firebase Admin | 9.4.3 | Server | Verificação do idToken (Ktor autoriza) **e envio de push via FCM v1** (F.1) — a API legada está desativada no projeto |
| Logback | 1.5.18 | Server | Logging em runtime |
| kotlinx.serialization (JSON) | 1.9.0 | Shared | DTOs `@Serializable` |
| kotlinx.coroutines | 1.11.0 | Shared | Concorrência (async) |
| kotlinx.datetime + `kotlin.time.Clock` | 0.8.0 | Shared | Datas (server nunca usa relógio do cliente p/ lógica) |
| Compose Multiplatform | 1.11.1 | Cliente | UI declarativa (Android) |
| Material 3 | 1.11.0-alpha07 | Cliente | Componentes M3 Expressive |
| Navigation Compose | 2.8.4 | Cliente | Navegação type-safe (Android-only, ARCH #8/#13) |
| Koin (core/android/compose/viewmodel/ktor) | 4.2.1 | Cliente + Server | Injeção de dependência |
| SQLDelight (runtime + android-driver + coroutines-ext) | 2.3.2 | Cliente | Banco local (fonte da verdade da UI, ARCH #30): programa/treino/agenda em tabelas reais, sessões, `kv_cache`, **outbox de escritas**. Schema mora nas migrations (`deriveSchemaFromMigrations`) — 1.sqm a 8.sqm |
| WorkManager (`work-runtime-ktx`) | 2.10.0 | Cliente | Esvazia a outbox quando a rede volta, **com o app fechado**. Redundante de propósito com o `refresh()` em foreground: se o sistema não acorda o worker, a fila não pode ficar parada |
| Coil 3 (+ network-okhttp) | 3.1.0 | Cliente | Thumbs de exercício (`/media`) |
| Media3 / ExoPlayer | 1.5.1 | Cliente | mp4 em loop mudo no detalhe do exercício |
| CameraX (core + camera2 + lifecycle + view) | 1.4.1 | Cliente | Câmera IN-APP do check-in (4.4) — sem galeria, sem app externo. **Faltava neste adendo** (achado na revisão de 2026-10-07) |
| Play Services Location (fused) | 21.3.0 | Cliente | Uma leitura de GPS por check-in (5.2); localização é **opt-in** (PII separada, regra de privacidade do projeto). **Faltava neste adendo** (achado na revisão de 2026-10-07) |
| Firebase Auth (GitLive) | 2.4.0 | Cliente | Autenticação; sessão persiste offline |
| Firebase BOM + firebase-auth | 34.4.0 | Cliente | Firebase no Android |
| firebase-messaging | via BOM 34.4.0 | Cliente | **Android-only** (F.1): recebe o push, `onNewToken`, `onMessageReceived`. Fica fora do `commonMain` — o equivalente iOS é APNs, e um `expect/actual` aqui abstrairia duas coisas que só se parecem por fora |
| google-services | 4.5.0 | Cliente | Plugin Firebase (Android) |
| Ktor client OkHttp | 3.5.0 | Cliente | Engine HTTP (androidMain) |
| AndroidX (activity, lifecycle, core-ktx, appcompat) + Material | — | Cliente | Base Android/Compose |
| Compose Icons Extended (core + extended) | 1.7.3 | Cliente | Ícones Material além do core — P5 aberto (`material-icons/drag-and-drop`). **Faltava neste adendo** (achado na revisão de 2026-10-07) |
| kotlin.test | 2.4.0 | Teste | Testes unitários (fakes, sem mock) |
| JUnit | 4.13.2 | Teste | Runner |
| Testcontainers (postgresql + junit-jupiter) | 2.0.5 | Teste | Integração com Postgres real |
| H2 | 2.3.232 | Teste | Banco em memória (`testRuntimeOnly` no server) |
| Ktor client-mock | 3.5.0 | Teste | Testes da camada de rede |
| kotlinx-coroutines-test | 1.11.0 | Teste | Testes de código assíncrono |
| Ktor server-test-host | 3.5.0 | Teste | Testes de rota do servidor |

## Declarado no catálogo, mas ainda NÃO ativo (fora da seleção)

| Tecnologia | Versão | Motivo de não estar em uso |
|---|---|---|
| Ktor client Darwin + SQLDelight native-driver | 3.5.0 / 2.3.2 | Só pro alvo **iOS** (Fase 11) — sem módulo iosApp ainda |
| Ktor server-websockets | 3.5.0 | Grupos/realtime — **ainda sem uso**, e não é mais "Fase 6 a iniciar": a Fase 6 inteira (grupos, check-in, ranking, feed) fechou SEM precisar dele. A votação foi revogada (ARCH #33): o check-in nasce válido e o ranking muda devagar, então **polling basta**; socket só entraria se o feed social pedisse |
| Ktor server-auth-jwt | 3.5.0 | Alternativa ao Firebase; Firebase é o que está em uso |
| Exposed-migration | 1.0.0 | Geração de diff p/ Flyway — não wired |
| AndroidX Espresso / teste instrumentado | 3.7.0 | Testes hoje são unit + host (`testAndroidHostTest`); instrumentado não exercido |

## Módulos (settings.gradle.kts)

`:app:androidApp` · `:server` · `:shared-contract` · `:konsist` · core: `network`, `database`, `designsystem`, `domain`, `result`, `util`, `catalog` · features (módulos data/domain/presentation variam por área — ver `settings.gradle.kts`): `auth`, `profile`, `exercise`, `wiki`, `achievements`, `stats` (data/domain, sem presentation própria), `session`, `workout`, `program`. Sem `iosApp` — iOS não iniciado.

**O débito antigo foi pago.** `stats`, `session` e `achievements` viviam em `app/androidApp/data/*`; viraram features próprias (`shared/features/{achievements,stats,session}`), extraídas juntas como o débito original pedia.

⚠️ **Débito NOVO do mesmo padrão, achado nesta revisão (2026-10-07) — ainda não está em `debitos.md`:** check-in (câmera via CameraX, compressão de foto, localização via Play Services) continua em `app/androidApp/data/checkin` e `app/androidApp/screens/checkin`, não em feature própria.

## Arquitetura & disciplina (inalterado)

Feature = 3 módulos (`data` mappers/repos; `domain` Kotlin puro; `presentation` viewmodels/state). [REGRA] Konsist: feature nunca depende de feature. `AppResult<T>`/`AppError` (sealed) com `httpResult`/`mapHttpError` único em `core:network`. Convenção: models internos sem default. Disciplina C1 (explicar antes) e C2 (testar ao fechar fatia).

**Suíte verde são QUATRO tasks:** `:server:test` · `:server:integrationTest` (aplica V1–V34 em Postgres real) · `testAndroidHostTest` (módulos KMP) · `:app:androidApp:testDebugUnitTest` (ViewModels do módulo de aplicação). **Nome de task:** o plugin novo de `androidLibrary` não gera `compileKotlinAndroid` nem `testDebugUnitTest` nos módulos KMP — lá é `testAndroidHostTest`.

---
_Reconstruído do PDF canônico + verificado no código (`libs.versions.toml` e `build.gradle.kts`), 2026-08-06; atualizado 2026-10-07 (migrations V61, schema 8.sqm, CameraX/Play Services Location/Compose Icons Extended que faltavam entraram, módulos de gamificação corrigidos — débito antigo pago, um novo do mesmo padrão achado no check-in; websockets segue sem uso). Gerado por `docs/geradores/gen_adendo.py`._
