# Sistema de notificações — FCM e central (ARCH #36)

> Índice: `docs/regras/README.md` · Marcadores: [REGRA] imutável · [INV] invariante · [PROPOSTA] lacuna não ratificada.

### Notificações (ARCH #36 — implementado em 2026-08-30, V41/V42)

- **[REGRA]** **Grava primeiro, despacha depois.** A notificação na tabela é a VERDADE; o push é a tentativa de avisar agora. O FCM não guarda nada — dispensou, sumiu. A ordem inversa perderia o aviso de quem mais precisa dele: justamente quem não recebe push.
- **[INV]** `title` e `body` são gravados **já renderizados**. Guardar só o id e montar a frase na leitura faria a notificação de ontem dizer o nome novo de quem trocou de nome hoje. **Notificação é registro do que houve, não consulta ao presente.**
- **[REGRA]** Falha ao notificar **nunca derruba a ação que a originou**. O pedido de amizade já aconteceu.
- **[INV]** O token FCM é identidade de **INSTALAÇÃO**, não de usuário: tabela própria (várias por conta), token como PK, e `ON CONFLICT (token) DO UPDATE SET user_id` porque o mesmo aparelho serve a várias contas.
- **[INV]** O **logout apaga o token deste aparelho**, e a baixa vem **antes** do `signOut` — depois dele o token do Firebase já não vale. Sem isso, quem sai continua recebendo notificação até a próxima pessoa entrar.
- **[REGRA]** Abrir a central marca **tudo** como lido, e a lista exibida **preserva o destaque** do snapshot já carregado — se sumisse na hora, a pessoa perderia a informação que foi buscar.
- **[REGRA]** Aceitar um pedido **não** marca a notificação como lida: são dois fatos diferentes.
- **[REGRA]** Retenção de **6 meses**, com purga agendada no boot.
- **[INV]** O contador do badge é `count { readAt == null }` **da mesma lista** que a tela mostra, nunca uma rota `/count`. Duas fontes da mesma verdade divergem no dia em que uma ganhar cache. **Falhar não zera o badge** — zerar diria "você não tem notificações" quando a verdade é "não consegui perguntar".
