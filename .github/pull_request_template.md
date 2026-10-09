## O que muda

<!-- Descreva objetivamente o que este PR altera. Se toca várias camadas
     (server/contrato/cliente), agrupe por camada. -->



## Por quê

<!-- O problema que resolve ou a decisão que implementa. Referencie o ARCH
     ou a fatia quando aplicável. -->



## Como testar

<!-- Comandos e passos para validar. Inclua o resultado esperado. -->

```
./gradlew :server:test
./gradlew :server:integrationTest --rerun-tasks
./gradlew :app:androidApp:testDebugUnitTest testAndroidHostTest
./gradlew :konsist:test --rerun-tasks
```

> ⚠️ **`--rerun-tasks` não é opcional** em `integrationTest` e `konsist`: as duas ficam
> `UP-TO-DATE` e devolvem `BUILD SUCCESSFUL` sem executar nada. Testcontainers em menos
> de 30s é sinal de que não rodou.

**No aparelho** — o que teste nenhum responde:

<!-- Liste os passos. Vários defeitos desta base foram achados olhando a tela, não
     rodando a suíte: barra de abas em português, catálogo em 404, nome errado no chip. -->



## Migration de DADO

<!-- Preencha SÓ se a migration reescreve ou apaga linha existente. Criar tabela ou
     coluna não conta. Se não se aplica, apague a seção inteira. -->

- **O que é reescrito/apagado:**
- **Quantas linhas, medido:** <!-- número real de consulta, não estimativa -->
- **Como voltar atrás:**
- [ ] A guarda afirma sobre **o que a migration tocou**, e não sobre a tabela inteira
- [ ] Casa por **id**, e não por nome ou outro campo que a própria migration altera
- [ ] Se apaga linha, reconfere no banco que ninguém depende dela antes de apagar

## Tipo

- [ ] feat (nova funcionalidade)
- [ ] fix (correção de bug)
- [ ] refac (refatoração sem mudar comportamento)
- [ ] Mudança de schema (migration)
- [ ] **Mudança de dado** (migration que reescreve linha existente)
- [ ] Mudança de contrato (DTO/rota)
- [ ] docs / chore

## Checklist

- [ ] Build verde (`:server:build` e/ou `:app:build`)
- [ ] Testes verdes, e **executados** (ver o aviso acima)
- [ ] Migration criada e aplicada no boot (se mexeu no schema)
- [ ] Contrato atualizado (se mudou DTO/rota) — shared-contract é a fonte única
- [ ] Koin wiring registrado (se adicionou classe injetável) — não esquecer, crasha o boot
- [ ] Mappers passam TODOS os campos novos (o bug recorrente: campo some silenciosamente)
- [ ] Round-trip testado se adicionou campo persistido (criar → reler → confirmar valor)
- [ ] Ordem respeitada: backend → contrato → cliente
- [ ] Texto novo que o usuário lê está no `strings.xml`, sem travessão e sem caixa alta (#37)
- [ ] Se acrescentou evento/estado, alguém o **emite** — caminho completo, não metade
- [ ] Painel Mestre + Handoff atualizados (e regenerados os PDFs) se fechou fatia/ARCH/débito

## Débitos registrados

<!-- O que ficou pendente e foi anotado no Painel. Deixe explícito o que NÃO
     está neste PR de propósito.

     Repita aqui o essencial: o debitos.md não vai para o git, então esta seção é o
     único registro do pendente que sobrevive no repositório. -->



## Refs

<!-- ARCH #, issue #, ou fatia -->
