# Erro: push não chega, mas o token registra normalmente

> Índice: `docs/erros/README.md`

### 3. Push não chega, mas o token registra normalmente

Este é o pior da família: **o servidor loga sucesso, o FCM aceita, e nada acontece.**

| Causa | Como confirmar | Correção |
|---|---|---|
| **Conexão do FCM caída** no aparelho | o **Firebase Console** também não entrega | **reiniciar o celular** |
| **Não Perturbe ligado** | logcat mostra `Desenhando notificação: habilitadas=true, canal=3` e nada aparece | desligar o Não Perturbe |
| Token apagado como morto e não re-registrado | `device_tokens` vazia, sem `POST /me/devices` no log | **matar e abrir** o app — `registrar()` roda no `LaunchedEffect(Unit)` do `AppNavHost`, não ao voltar do background |
| Push foi para o outro aparelho | `Registrando aparelho: <prefixo>` no logcat não bate com o `LEFT(token,12)` do banco | conferir qual conta está em qual aparelho |

**O teste que separa nosso código de tudo o mais:** Firebase Console → Messaging → Enviar mensagem
de teste, colando o token do banco.

- console **entrega** e o nosso não → o defeito é nosso
- **nenhum dos dois** entrega → é o aparelho
