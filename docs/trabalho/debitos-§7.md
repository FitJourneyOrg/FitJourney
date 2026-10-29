# Achados da fatia 0 do motor (2026-10-14) — ainda não triados

> Não são "fora de escopo por decisão" (esses foram para `fontes/debitos.md` no fechamento de
> `motor-fatia-0-catalogo-e-medicao.md`, 2026-10-22). São bugs/lacunas encontrados no caminho,
> registrados no "Andamento — 2026-10-14" daquele plano antes de ele ser apagado. Ninguém decidiu
> ainda se cada um é débito formal, em qual fatia entra, ou se já foi resolvido por outra via.

- **11 dos 78 exercícios do pool (lista principal) com `modality` NULL**, invisíveis ao motor
  Kotlin — Leg Press 45°, Pec Deck, as duas Elevações Laterais com halter, Remada Cavalinho T-Bar,
  entre outros. Não muda a referência medida (banco e catálogo pretendido dão o mesmo número), mas
  é migration de dado pendente.
- **Ponto fixo indireto↔alocação oscila em 26 de 720 perfis** sob escassez (séries inteiras).
  Candidato à fatia 3 do motor v3. O oráculo (`server/src/test/oracle/`) fica com o estado de menor
  indireto e conta `ponto_fixo_oscilou`.
- **Nenhum exercício ADVANCED no pool.** GLUTES com 3 candidatos e nenhum em casa. `papel_trocado`
  disparou 1164 vezes na matriz de 720 perfis — a cobertura alvo×papel do catálogo é fraca.
- **`Crucifixo Inverso na Máquina (Voador Invertido)` está com `equipment = DUMBBELL`** — provável
  erro de dado (o nome diz máquina).
- **Porção do deltoide não existe no catálogo.** "O isolamento de ombro é sempre elevação
  lateral" (regra do motor v3) exige saber qual isolamento é lateral vs posterior, e essa
  distinção não está modelada (`[PENDENTE 2]` da especificação).

## Para decidir

- Qual destes é débito real (→ `fontes/debitos.md`) e qual já foi resolvido ou ficou obsoleto
  desde 14/10 (curadoria avançou bastante entre 14/10 e 21/10 — alguns destes talvez já estejam
  cobertos pela curadoria completa dos 893).
- Em qual fatia do motor v3 (1–12) cada item cai, se ainda for válido.
