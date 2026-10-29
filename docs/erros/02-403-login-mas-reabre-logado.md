# Erro: 403 no login, mas reabrir o app entra logado

> Índice: `docs/erros/README.md`

### 2. "Sem permissão" (403) no login, mas reabrir o app entra logado

**Não é o nosso servidor.** Confirme: se nenhuma linha aparece no log do Ktor, a requisição nunca
chegou lá.

**Causa real observada:** o IP antigo (`192.168.15.6`) foi reatribuído pelo DHCP a **outro
aparelho da rede**, que respondeu 403 a um POST que não entende.

O app entra ao reabrir porque a sessão do Firebase persiste no aparelho e a Home é cache-first
([ARCH #30]) — o login local funciona sem servidor nenhum.

**Correção:** IP novo + `installDebug`.

**Sintoma irmão:** `/health` no navegador do celular devolvendo **403** em vez do JSON — prova
definitiva de que quem atende naquele IP não é o Ktor.
