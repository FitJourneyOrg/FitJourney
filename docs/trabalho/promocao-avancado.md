# Promoção intermediário→avançado — plano

_Aberta em 2026-10-30. Rascunho: não é fonte da verdade (ver README desta pasta)._

## O que é e por que agora

Fecha o débito (b) de "o motor sabe, o app não mostra" (`fontes/debitos.md`, P0): o iniciante já
tem service/rota (fatia anterior, 2026-10-29); o avançado (`PromocaoDeNivel.diagnosticoAvancado`)
continua sem chamador. Núcleo puro já fechado na Fatia 11.

## Perguntas que preciso responder antes de codar

- [x] **Agregação entre âncoras — RESPONDIDO (2026-10-31).** Maioria estrita (mais da metade
  em PROMOVER, sem exceção de tamanho). `PromocaoDeNivel.diagnosticoAvancadoDoPerfil()` +
  `progressao_v3.py::diagnostico_promocao_avancado_do_perfil()`. Registrado como `[REGRA]` em
  `docs/regras/01-motor-de-geracao.md` e como heurística calibrável em `fontes/debitos.md`.

## Decisões tomadas

- **Âncora = compostos com `relevancia = 1` do histórico de progressão** (mesma fonte do
  iniciante, `exerciciosComEventoNoMesociclo`, filtrada por `exercises.relevancia = 1`) — decidido
  em 2026-10-29, antes desta fatia. Motivo: derivada da curadoria, sem coluna nova; isolado
  estagna por razão que não indica maturidade.
- **Snapshot do 1RM = V72, não tabela separada** — decidido em 2026-10-30. `progression_event`
  ganha `um_rm_kg` nullable, sem backfill, gravado em todo `registrar()` novo (o valor já existe
  em `estado.umRm`, só não era persistido por evento). "1RM no início do mesociclo N" = primeiro
  evento do exercício com `mesociclo = N`. Alternativa descartada: tabela de snapshot — duplicaria
  em outro lugar o que o log deveria ter registrado na hora (critério do Rafael).
- **`umRmInicio`/`umRmFim` são mesociclo anterior × atual** — `diagnosticoAvancado` compara "dois
  mesociclos consecutivos" (KDoc). `umRmInicio = primeiroUmRmNoMesociclo(ancora, mesocicloAtual - 1)`,
  `umRmFim = buscar(ancora).umRm` (estado atual). Se `mesocicloAtual <= 1`, não há mesociclo
  anterior — é DADO INSUFICIENTE, não "sem promoção".
- **`reducoesNoPeriodo` = `reducoesDesde(ancora, mesocicloAtual - 1)`** — conta eventos `DESCE`
  a partir do mesociclo anterior (inclusive).
- **Dado insuficiente ≠ nenhuma promoção a sugerir** — exigência do Rafael (2026-10-30): sem
  backfill, ~2 mesociclos após o deploy da V72 não têm dado recuperável pra NINGUÉM. O service não
  pode colapsar "não dá pra diagnosticar ainda" e "diagnosticou e não é promover" no mesmo `null`
  sem registro — senão, daqui a um mês, ninguém sabe se a regra funciona ou quebrou. Tratamento:
  distinguir os dois casos explicitamente no service (logging/observabilidade no mínimo; decisão
  de expor isso no contrato ou não ainda em aberto — ver Desenho).

## Status (2026-10-31)

Código escrito: migration V72, repositório (`primeiroUmRmNoMesociclo`/`reducoesDesde`), `ExerciseRepository.ancorasAvancadas`, `PromocaoDeNivel.diagnosticoAvancadoDoPerfil`, `PromotionService.diagnosticarAvancado`, testes (`PromocaoDeNivelTest`, `PromotionServiceTest`, invariante de matriz). **Nada commitado ainda** — falta rodar a suíte (cinco comandos) e o Rafael confirmar build verde antes do commit.

## Desenho

### Servidor

Repositório (`ProgressaoRepository`, já implementado nesta sessão):
- `primeiroUmRmNoMesociclo(usuarioId, exercicioId, mesociclo): Double?`
- `reducoesDesde(usuarioId, exercicioId, desde): Int`

`PromotionService` — **não escrito ainda**, bloqueado pela pergunta de agregação acima. Esqueleto
pendente: achar âncoras avançadas (interseção `exerciciosComEventoNoMesociclo` × `relevancia = 1`,
via `ExerciseRepository`), por âncora rodar `diagnosticoAvancado`, agregar (como?), distinguir dado
insuficiente de sem-promoção, consultar `podeSugerirDeNovo`/`PromotionDeclineRepository` (mesmo
padrão do iniciante).

### Contrato

Provavelmente sem mudança — `PromotionStatusDto`/`PromotionSuggestionDto` já existem; a distinção
"dado insuficiente" pode não precisar chegar ao cliente (é problema de observabilidade nossa, não
de UX da pessoa) — decidir ao escrever o service.

### Cliente

Nenhuma mudança prevista — o cliente já lê `GET /me/promotion` e aceita via `PUT /me/profile`,
igual ao iniciante.

## Migration

**V72** (`V72__um_rm_do_evento_de_progressao.sql`) — `progression_event.um_rm_kg` (nullable, sem
backfill). Não apaga nem reescreve dado de usuário. **Já implementada e commitável.**

## Testes (C2)

Pendente — aguardando a resposta da agregação pra escrever `PromotionServiceTest` (mesmo padrão
dos 8 casos do iniciante).

## Fora de escopo, por decisão

- Declaração de custo da fase de ênfase ao usuário — débito (c)/(d) separado, mesmo P0.
- Expiração do foco em 2 mesociclos — rebaixada a feature de produto (2026-10-30), não é desta
  fatia.

## Ao fechar — promover antes de apagar

- [ ] `[REGRA]`/`[INV]` → `docs/regras/01-motor-de-geracao.md` (agregação de âncoras do avançado,
  quando decidida)
- [ ] migration → `docs/migrations/INDICE.md` (V72)
- [ ] narrativa e defeitos achados → `docs/historico/`
- [ ] `ESTADO.md` atualizado, `gen_tudo.py` rodado
- [ ] este arquivo apagado
