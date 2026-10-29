# Erro: notificação criada com sucesso e invisível

> Índice: `docs/erros/README.md`

### 4. Notificação criada com sucesso e invisível

`IMPORTANCE_DEFAULT` **não faz heads-up**: toca som e põe o ícone na barra, mas não aparece
flutuando sobre a tela. Com o app aberto na frente, o resultado é nada na tela e tudo na bandeja —
**puxe a barra de status antes de concluir que falhou**.

É decisão do `CanalDeNotificacao`, não defeito. Mudar para `IMPORTANCE_HIGH` exige **desinstalar o
app**: o Android congela a importância na criação do canal.
