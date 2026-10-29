# Offline — leitura e escrita (#30)

> Índice: `docs/regras/README.md` · Marcadores: [REGRA] imutável · [INV] invariante · [PROPOSTA] lacuna não ratificada.

## Offline (leitura + login)

- Leitura network-first-com-fallback: cai no cache local só em falha de **transporte**
  (`AppError.Connection` — sem rede, servidor fora do ar, timeout, DNS) e só quando já houve
  sincronização alguma vez com esta conta; nunca em 401/403/409/validação, que são resposta REAL
  do servidor. [Correção 2026-10-22: este arquivo dizia `AppError.Unexpected`, mas o código
  (`ProgramRepositoryImpl`, `NotificacoesRepositoryImpl`) sempre checou `AppError.Connection` —
  código vence doc.]
- Login offline: gate pela sessão persistida do Firebase (`isLoggedIn`), não por token fresco. 1º login exige internet.
- **[REGRA]** Escrita offline é UM CAMINHO SÓ (#30): grava local → enfileira → agenda o envio, com ou sem rede. Falha de rede mantém a operação na fila; recusa do servidor (400/403/404/409) para de tentar e avisa o usuário. Na primeira falha temporária o processamento PARA, porque a ordem entre operações é dependência real (criar treino antes de agendá-lo).
- **[REGRA]** Mutação GLOBAL sem id de recurso (ex. "marcar todas como lidas") usa um **alvo fixo
  por conta** no outbox, não um id por item — é o que faz a compactação colapsar repetições de
  graça, sem mudar `CompactadorDeOutbox` (fatia "notificações offline", 2026-10-22).
