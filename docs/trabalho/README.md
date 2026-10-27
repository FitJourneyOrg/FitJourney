# trabalho/ — a fatia que está sendo planejada

> **Pasta descartável, de propósito.** Um arquivo por fatia **em andamento**: ideias, informações,
> passos, dúvidas e rascunho de desenho — tudo que você quer registrar ANTES de codar e que não
> merece virar documentação permanente.
>
> Ela existe porque o `ESTADO.md` é curto por desenho (é lido em toda sessão) e um plano de fatia
> tem 100 ou 200 linhas de rascunho que não cabem lá — mas que também não podem ficar só no chat,
> porque sessão nova não tem o histórico da anterior.

## O ciclo de vida, em três estágios

```
docs/trabalho/<fatia>.md   →   ESTADO.md "Agora"   →   docs/historico/<fatia>.md
    planejando                    codando                     fechada
```

Cada informação mora em **exatamente um** dos três, e **muda de lugar** em vez de ser copiada. É a
mesma falha que matou o Painel Mestre: dois documentos descrevendo a mesma fatia em momentos
diferentes, e nenhum deles sabendo qual era o atual.

## Quando o arquivo morre

No **Fechamento de Ciclo**, junto com os testes verdes e o `gen_tudo.py`. Antes de apagar, o que
sobreviveu já saiu daqui para o seu lar definitivo:

| O que o plano tinha | Para onde foi |
|---|---|
| Decisão de comportamento ratificada | `docs/regras/<área>.md` como `[REGRA]` ou `[INV]` |
| Decisão de arquitetura | `docs/arch/` como ADR |
| O que ficou de fora, com motivo | `docs/fontes/debitos.md` |
| Narrativa do que aconteceu e o que quebrou | `docs/historico/<fatia>.md` |
| Migration nova | `docs/migrations/INDICE.md` |

**O que não foi para nenhum dos cinco era rascunho** — e rascunho some. Esse é o teste, e é por isso
que apagar aqui é seguro: se algo importante morreu com o arquivo, o erro foi não tê-lo promovido,
não tê-lo apagado.

## Regras

- **Um arquivo por fatia**, nomeado como a fatia (`fase-8-aprender.md`, `fatia-k-wiki.md`).
- **Mais de um arquivo aqui ao mesmo tempo é sinal de alerta:** a convenção é uma fatia por sessão.
  Dois arquivos significa fatia que ficou pela metade — decida fechar ou abandonar.
- **Nada aqui é fonte da verdade.** Em divergência com o código, com `regras/` ou com `arch/`, esta
  pasta perde. É rascunho, e rascunho pode estar errado por natureza.
- **Não gera PDF** e não entra na hierarquia de verdade.
- **Fora do git**, como todo o `docs/` — apagar é definitivo. Por isso a limpeza lista o que vai
  sair e espera o ok, em vez de apagar sozinha.

## Modelo

Copie `MODELO.md` ao abrir uma fatia nova.
