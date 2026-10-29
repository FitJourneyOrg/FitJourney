# Amizades (ARCH #35)

> Índice: `docs/regras/README.md` · Marcadores: [REGRA] imutável · [INV] invariante · [PROPOSTA] lacuna não ratificada.

### Amizades (ARCH #35 — implementado em 2026-08-27, V40)

- **[REGRA]** Amizade **não é permissão, é atalho**: serve para convidar sem digitar código. O perfil já é público pela 9.3-A; desfazer amizade não tira acesso a nada.
- **[REGRA]** Relação **simétrica com aceite** (modelo Facebook, não "seguir"). Estados: `NENHUMA` → `PEDIDO_ENVIADO`/`PEDIDO_RECEBIDO` → `AMIGOS`, e `BLOQUEADO_POR_MIM` fora da linha.
- **[INV]** A chave é o **par canônico** (`user_a` = menor uuid) e é a **PK**. Pedido cruzado duplicado é impossível **no banco**, não por checagem de código.
- **[REGRA]** **Pedido cruzado vira amizade na hora.** Os dois manifestaram a mesma intenção; exigir que um refaça o gesto com outro nome é burocracia. Também é o que resolve a corrida de dois toques simultâneos sem erro.
- **[REGRA]** Buscar por código **abre o perfil**, nunca manda pedido — erro de digitação viraria pedido a um desconhecido. Código de **8 caracteres**, regenerável, com limite de **10 tentativas/hora** por usuário (não por IP: puniria o dedo grosso, não o atacante).
- **[REGRA]** Bloquear **apaga a amizade e o pedido pendente na mesma transação**. "Remover" que não impede novo pedido é gesto vazio.
- **[INV]** Quem foi bloqueado vê **perfil indisponível** — o mesmo tratamento de conta apagada. A ausência não explica a causa. Desbloquear devolve o perfil.
- **[INV]** `DELETE /friends/{id}` decide pelo **estado**, não pelo erro da tentativa anterior. **Erro não é canal de decisão.**
