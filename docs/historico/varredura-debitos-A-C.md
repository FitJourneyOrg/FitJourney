# Varredura de débitos — grupos A, B e C (2026-10-07 a 2026-10-09)

Plano: `trabalho/debitos-A-D.md` (segue vivo: o Grupo D está pendente). Rastreio por débito em
`fontes/debitos.md`. Aqui fica a narrativa e os defeitos achados.

## O que foi feito

- **A (obsoletos):** fechados sem código.
- **B1–B12:** inclui B10 (`origin` com fonte única em `programs.origin`; V62 derruba `workouts.origin`,
  15 linhas medidas, todas MANUAL), B11 (modelos internos do servidor sem valores default; exceção
  `User.idioma`) e B12 (paridade `ExerciseRef` × `Exercise` por teste; a duplicação é intencional,
  registrada no ARCH #16).
- **C1:** pull-to-refresh em Amigos e Notificações, só nelas (`atualizando` separado de `carregando`;
  estados vazios com `verticalScroll`). Achado no aparelho: o pull offline falhava calado; virou
  snackbar via `erroDoPull` (a tela de Amigos nunca exibiu o `erro` do estado).
- **C2:** sem listener de conectividade; expectativa corrigida (WorkManager cobre a escrita; a tela
  atualiza ao entrar, no pull e no "Tentar de novo"). Regra no ARCH #31.
- **C3:** fatores de idade do ARCH #24 ratificados ([PROPOSTA]→[REGRA], 2026-10-08).
- **C4:** Terra Romeno unido ao Stiff (V63). Corrigi um diagnóstico meu errado antes de escrever a
  migration; a decisão veio depois de uma folha de contato com imagens. Catálogo: 922.
- **C5, C6/C7, C8:** adiados com gatilho (Tier 3 de autorregulação; tela de perfil de treino na
  Fase 10 — texto do HealthGate já corrigido; logging de plano servido até o LLM ao vivo).
- **C9:** porta `Falhas` (`dev.rafael.server.error`) com `FalhasSlf4j` em produção e `FalhasFake` nos
  testes; ligada em `NotificadorFcm`, `NotificacaoService` e `AchievementService`. Convenção em
  `CONVENCOES.md`.
- **C10, C11, C12:** adiados (domínio e `assetlinks.json`; checklist de primeiro deploy; Fase 10).
- **Fora da lista:** tetos de programa grátis 1 / premium 3 (ARCH #27); Amigos: ações sem snackbar
  (decisão do Rafael).

## Defeitos e lições

- Meus erros de processo: diagnóstico por nome sem olhar vídeo/descrição (C4); string `can't` sem
  escape em `values-en` quebrou `mergeDebugResources`; teste que termina em `assertIs` devolve valor e
  o JUnit rejeita a classe inteira; o coroutines recria exceções ao atravessar `withContext`, então
  teste não compara instância de exceção.
- Flyway só roda no `:server:run` contra o banco de dev; `integrationTest` usa Testcontainers e não
  prova que a migration foi aplicada no banco de dev.
- O Windows mount via `device_bash` é lento: greps estreitos com `timeout`.

## Ficou aberto

- Grupo D: Play Billing real; credencial Firebase (adiada até escolher o provedor de deploy); login
  que exige o backend de pé; vocabulário de músculo; Home placeholder; 23 sem taxonomia.
- Fatias novas planejadas, cada uma em sessão própria: `trabalho/motor-lista-principal.md` e
  `trabalho/notificacoes-offline.md`.
- Recusa assíncrona do outbox sem aviso (opção 2 do B9); 3 `Stiff Unilateral` sem `movement_pattern`.
