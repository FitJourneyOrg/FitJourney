## O que muda

<!-- Descreva objetivamente o que este PR altera. Se toca várias camadas
     (server/contrato/cliente), agrupe por camada. -->



## Por quê

<!-- O problema que resolve ou a decisão que implementa. Referencie o ARCH
     ou a fatia quando aplicável. -->



## Como testar

<!-- Comandos e passos para validar. Inclua o resultado esperado. -->

```
./gradlew :server:build
./gradlew :server:test
```



## Tipo

- [ ] feat (nova funcionalidade)
- [ ] fix (correção de bug)
- [ ] refac (refatoração sem mudar comportamento)
- [ ] Mudança de schema (migration)
- [ ] Mudança de contrato (DTO/rota)
- [ ] docs / chore

## Checklist

- [ ] Build verde (`:server:build` e/ou `:app:build`)
- [ ] Testes verdes (se aplicável)
- [ ] Migration criada e aplicada no boot (se mexeu no schema)
- [ ] Contrato atualizado (se mudou DTO/rota) — shared-contract é a fonte única
- [ ] Koin wiring registrado (se adicionou classe injetável) — não esquecer, crasha o boot
- [ ] Mappers passam TODOS os campos novos (o bug recorrente: campo some silenciosamente)
- [ ] Round-trip testado se adicionou campo persistido (criar → reler → confirmar valor)
- [ ] Ordem respeitada: backend → contrato → cliente
- [ ] Painel Mestre + Handoff atualizados (e regenerados os PDFs) se fechou fatia/ARCH/débito

## Débitos registrados

<!-- O que ficou pendente e foi anotado no Painel. Deixe explícito o que NÃO
     está neste PR de propósito. -->



## Refs

<!-- ARCH #, issue #, ou fatia -->****