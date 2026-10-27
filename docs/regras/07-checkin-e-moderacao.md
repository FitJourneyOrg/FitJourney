# Check-in, denúncia e moderação (ARCH #33)

> Índice: `docs/regras/README.md` · Marcadores: [REGRA] imutável · [INV] invariante · [PROPOSTA] lacuna não ratificada.

### Check-in (requisito #17 — EMENDADO em 2026-08-19 pelo ADR #33)

> `#17` aqui é o **requisito de produto** (check-in), não o **ADR #17** (IA stub-first). As duas sequências colidem em #16, #17 e #18 — ver o aviso em `arch/README.md`.

**REVOGADO do #17** (não implementar; ver ARCH #33 para o porquê de cada queda):

| Revogado | Motivo |
|---|---|
| Votação de 24h entre membros | 50 membros = 50 votações/dia, cada um com 49 para votar. Ninguém faz. Com "silêncio aprova", vira aprovação automática com 50 notificações diárias — aritmética do desenho, não bug |
| Apuração ≥50%, empate aprova | consequência: sem votos, não há o que apurar |
| Contestação 24h via admin para check-in de terceiro | check-in de terceiro foi removido; a votação que o justificava não existe |

**CONTINUA VALENDO do #17:** só check-in **válido** conta no rank; localização é **opt-in**.

- **[REGRA]** O check-in nasce **VÁLIDO**. Se cumpre as regras obrigatórias do grupo, pontua na hora. **Ninguém aprova.** Não existe estado "pendente" — some a ambiguidade de "check-in pendente conta no ranking?".
- **[REGRA]** Foto, localização e emoji do dia são **AUTODECLARAÇÕES**. O servidor confere só o que é estrutural (existe foto? existe local?), nunca o conteúdo. Nenhuma máquina e nenhuma pessoa verifica no momento do check-in.
- **[REGRA]** A correção vem **depois, por DENÚNCIA**, julgada pelo admin: `VALIDO` → `INVALIDADO`. Denúncia acatada invalida o **check-in inteiro** e o ponto sai do ranking. Não há recurso — o admin é a autoridade única do grupo.
- **[INV]** Decisões do admin são **append-only** (nunca `UPDATE`): é o que permite responder "por que este check-in foi invalidado?" seis meses depois.

#### A máquina de estados, implementada na E.2 (V45, 2026-09-07)

```
                 1ª denúncia
  VALIDO ─────────────────────────► EM_ANALISE
     │                                 │    │
     │  admin invalida direto (6.10)   │    │ admin mantém ──► VALIDO
     └─────────────────────────────────┼────┘
                                       │ admin acata (6.3)
                                       ▼
                                  INVALIDADO ◄── terminal
```

- **[INV]** `EM_ANALISE` **continua contando ponto** (6.8). Se o ponto sumisse durante a análise, a denúncia viraria **arma**: bastaria denunciar quem lidera o ranking na véspera do fim. É presunção de boa-fé, e é o que impede a moderação de virar tática de jogo.
- **[INV]** `INVALIDADO` é **terminal** — "check-in invalidado nunca volta a contar". Denunciar o já invalidado é recusado: não há o que produzir.
- **[REGRA]** Recusar a denúncia devolve a `VALIDO` puro, **não a "válido com marca"**. Quem passou pela análise e foi mantido é indistinguível de quem nunca foi denunciado; guardar a suspeita seria punir a absolvição — e como o admin não vê quem denunciou, nem serviria de aviso.
- **[REGRA]** **Check-in sob moderação não se apaga** — nem `INVALIDADO`, nem `EM_ANALISE` *(ratificada em 2026-09-07)*. A 4.11 diz que apagar libera o slot do dia; juntas, as duas permitiriam apagar-e-refazer para desfazer a decisão do admin. Bloquear só o invalidado deixaria a fuga um passo mais cedo: apagar **antes** do julgamento, esvaziando a fila.
- **[REGRA]** Ninguém denuncia o **próprio** conteúdo. Quem quer desfazer o próprio check-in tem a 4.11; oferecer a denúncia como segundo caminho criaria uma forma de "apagar" fora do prazo, passando pelo admin. **Dois caminhos para o mesmo efeito é como uma regra de prazo deixa de valer.**
- **[INV]** Denúncia só até **7 dias** (6.9), contados do check-in — ou do **comentário**, quando o alvo é ele: um comentário ofensivo escrito hoje num check-in de duas semanas precisa poder ser denunciado hoje.
- **[INV]** Várias denúncias do mesmo alvo são **um caso, com contador** (6.11), e isso vale também para o ESTADO: a segunda não move nada. **Uma por pessoa por alvo**, garantida por índice único — não por checagem de código.
- **[REGRA]** O admin **não vê quem denunciou**. O banco guarda `reporter_id` (sustenta o "uma por pessoa" e a auditoria), mas ele não atravessa o DTO. Num grupo de conhecidos, denúncia identificada é denúncia que ninguém faz — e uma fila vazia com o problema intacto é pior que nenhuma fila.
- **[REGRA]** O motivo é **obrigatório**. É o atrito que separa a denúncia pensada do toque irritado, e como o admin julga sem saber quem reclamou, **a frase é tudo o que ele tem**.
- **[REGRA]** O check-in invalidado **fica no feed, marcado**. Sumir apagaria a única evidência de que a moderação agiu: quem denunciou não saberia se foi atendido, e a decisão — que não tem recurso — aconteceria sem ninguém ver. **A publicidade da decisão é o freio contra o abuso de quem decide.**
- **[REGRA]** Comentário também se denuncia (6.4), e acatar o **remove** — comentário não tem estado. É o único caminho da fase em que acatar apaga em vez de marcar, e por isso a auditoria não pode ter chave estrangeira no alvo: seria a única decisão sem rastro.

#### Duas emendas de 2026-09-08 — o que o admin NÃO decide

- **[REGRA] Denúncia não expira por tempo.** Proposto e recusado. Se o caso sumisse sozinho depois de N horas, **a inação do admin viraria absolvição** — bastaria não abrir o app, e num grupo de conhecidos o admin pode ser amigo de quem fraudou. A 6.12 já resolve pelo outro lado: ele não tem prazo **porque** o `EM_ANALISE` continua contando ponto (6.8), então o caso parado não prejudica ninguém. **O remédio para fila parada é fazer o admin ver, não apagar o que ele não viu.**
- **[REGRA] Denúncia em desafio ENCERRADO fecha sozinha**, com registro `ENCERRADO_SEM_JULGAMENTO` e `admin_id` **nulo**. Aqui o automático é honesto: o ranking congelou e as recompensas foram concedidas; invalidar mudaria um resultado que as pessoas já comemoraram, contra a 6.5. **Um caso que não pode produzir efeito não deveria continuar pedindo decisão.** Ninguém é notificado — não houve julgamento.
- **[INV]** O `admin_id` nulo é o que distingue **"o admin manteve"** de **"o desafio acabou antes"**. Preenchê-lo faria o histórico afirmar que alguém tomou uma decisão que ninguém tomou.
