# Decisões de Arquitetura (ADR) — índice

> Fonte: recorte do Painel Mestre v24 + Handoff Retomada 13, atualizado 2026-07-28. Numeração de ARCH ratificada: #26=volume; #27=multi-programa; #28=ambientes/is_base; #29=split guiado.

Um arquivo por decisão, agrupado por fase em `arch/fase-N/`. Ao fechar uma fatia que introduz decisão nova, crie `arch/fase-N/ARCH-NN-slug.md` e adicione a linha na seção da fase.

> ⚠️ **COLISÃO DE NUMERAÇÃO — ler antes de citar um número.** Existem DUAS sequências `#N` no projeto:
> **(a) ADR**, que é esta tabela, e **(b) requisito de produto**, usado na Documentação Completa e nas
> Regras. Elas se cruzam em **#16, #17 e #18**:
>
> | # | como ADR (aqui) | como requisito de produto |
> |---|---|---|
> | #16 | `core:catalog` — lookup de exercício | gamificação: XP, nível, conquistas, cor `lime` |
> | #17 | geração por IA stub-first | check-in |
> | #18 | gates de geração na rota | grupos |
>
> Ao citar um desses três, **escreva qual sequência**: "ADR #17" ou "requisito #17 (check-in)". Do #19
> em diante não há ambiguidade — só a sequência de ADR usa esses números.

### Fase 0 — Fundação & Tooling
| # | Decisão | Status |
|---|---------|--------|
| #3 | [server → core:result](fase-0/ARCH-03-server-core-result.md) | vigente |
| #6 | [Três convention plugins](fase-0/ARCH-06-convention-plugins.md) | vigente |

### Fase 1 — Backend base
| # | Decisão | Status |
|---|---------|--------|
| #4 | [Erro como fluxo funcional](fase-1/ARCH-04-erro-funcional.md) | vigente |
| #5 | [Flyway roda no boot](fase-1/ARCH-05-flyway-boot.md) | vigente |

### Fase 2 — Autenticação
| # | Decisão | Status |
|---|---------|--------|
| #7 | [Koin também no server](fase-2/ARCH-07-koin-server.md) | vigente |
| #8 | [UI nativa por plataforma](fase-2/ARCH-08-ui-nativa.md) | vigente |

### Fase 3 — Onboarding
| # | Decisão | Status |
|---|---------|--------|
| #9 | [domain importa enums do contract](fase-3/ARCH-09-domain-enums-contract.md) | vigente |
| #10 | [Navegação por estado simples](fase-3/ARCH-10-nav-estado.md) | QUITADO (C.2) |

### Fase 4 — Treino (núcleo)
| # | Decisão | Status |
|---|---------|--------|
| #11 | [Offline leve no cliente](fase-4/ARCH-11-offline-leve.md) | ⛔ substituído pelo #30 |
| #12 | [SQLDelight: um banco em core:database](fase-4/ARCH-12-sqldelight-core.md) | vigente |
| #13 | [Navigation Compose Android-only](fase-4/ARCH-13-navigation-android.md) | vigente |
| #14 | [Client expectSuccess=true](fase-4/ARCH-14-client-expectsuccess.md) | vigente |
| #15 | [Cache do flag de onboarding no cliente](fase-4/ARCH-15-cache-onboarding.md) | vigente |
| #16 | [core:catalog — lookup de exercício no core](fase-4/ARCH-16-core-catalog.md) | vigente |
| #17 | [Geração por IA stub-first](fase-4/ARCH-17-ia-stub-first.md) | vigente (evoluído p/ #20) |
| #18 | [Gates de geração na rota](fase-4/ARCH-18-gates-na-rota.md) | vigente |
| #19 | [Entitlement mínimo](fase-4/ARCH-19-entitlement-minimo.md) | vigente (emendado #22/#23/#27) |
| #20 | [IA offline-first (runtime determinístico)](fase-4/ARCH-20-ia-offline-first.md) | vigente |
| #21 | [Array nativo para dado filtrado em SQL](fase-4/ARCH-21-array-nativo.md) | vigente |
| #22 | [Geração produz PROGRAMA semanal](fase-4/ARCH-22-programa-semanal.md) | vigente (revisado por #27) |
| #23 | [Value-first: gera antes do pagamento; gate por posse](fase-4/ARCH-23-value-first.md) | implementado (2026-07) · blur + revelação |
| #24 | [Idade: gate de segurança + variável do motor](fase-4/ARCH-24-idade-gate.md) | vigente (implementado — V27 + motor) |
| #25 | [Monetização: grátis registra e conecta, premium pensa](fase-4/ARCH-25-monetizacao.md) | implementado (2026-07) · gate de edição via `requireEditable` |
| #26 | [Parametrização do motor: volume é a variável-mãe](fase-4/ARCH-26-volume-variavel-mae.md) | vigente ([PROPOSTA] até validar em uso) |
| #27 | [Multi-programa](fase-4/ARCH-27-multi-programa.md) | implementado (2026-07) · revisa #22 |
| #28 | [Dois ambientes + is_base + refino do catálogo](fase-4/ARCH-28-dois-ambientes-isbase.md) | implementado (2026-07) |
| #29 | [Split guiado na onboarding](fase-4/ARCH-29-split-guiado.md) | implementado (2026-07) · migration V18 |

### Fase 5 — Progresso & Gamificação
| # | Decisão | Status |
|---|---------|--------|
| #30 | [Offline-first](fase-5/ARCH-30-offline-first.md) | vigente · substitui o #11 · parcial (histórico/XP/programas prontos; escrita offline pendente) |
| #31 | [Política única de apresentação de erro](fase-5/ARCH-31-apresentacao-de-erro.md) | implementado (2026-08) · complementa #4 e #30 |
| #32 | [Conquistas: regra derivada, desbloqueio persistido](fase-5/ARCH-32-conquistas.md) | vigente |
| #33 | [Grupos e check-in: confiança, não verificação](fase-6/ARCH-33-grupos-checkin.md) | vigente (Fase 6, antes do código) |
| #34 | [Perfil público × minha conta, e navegação por drawer](fase-6/ARCH-34-perfil-publico-e-drawer.md) | vigente (Fase 6, antes do código) · o `[EU — confira]` da visibilidade foi fechado pela emenda **9.3-A** |
| #35 | [Amizades: o grafo do convite](fase-6/ARCH-35-amizades.md) | vigente (Fase 6, antes do código) · **revisado no mesmo dia** pela emenda 9.3-A |
| #36 | [Notificações: a gravada é a verdade, o push é o aviso](fase-6/ARCH-36-notificacoes.md) | implementado (F.1 + F) · emenda 36.3 |

### Fatia G — Idioma (fora do plano de fases)
| # | Decisão | Status |
|---|---------|--------|
| #37 | [Idioma: o servidor manda dados, o cliente escreve as frases](fase-7/ARCH-37-idioma.md) | vigente (antes do código) · V47 · **não está no RESUMO-FASES**: puxado a pedido |

### Motor v3 — fatia 0 (fora do plano de fases)
| # | Decisão | Status |
|---|---------|--------|
| #38 | [Perfil: JSON em TEXT; catálogo: colunas booleanas](fase-8/ARCH-38-json-vs-colunas-acessorios.md) | vigente · V65/V66, 2026-10-21 |
| #39 | [Casamento músculo↔exercício: coluna `volume_target`](fase-8/ARCH-39-casamento-volume-target.md) | decidido, não implementado · V64, 2026-10-14 |
