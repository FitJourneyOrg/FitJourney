# Motor v3 — semântica de carga no catálogo (ARCH · fatia do catálogo, 2026-11-05)

Estado: **implementado e verde** — `V74__incremento_e_semantica_de_carga.sql`, `ExercisesTable`,
`Exercise`, `ExerciseRepositoryImpl.toExercise()`, `SemanticaDeCargaIntegrationTest` (8 testes
contra Postgres real, 5 caminhos de falha). Nenhum dado de usuário é apagado ou reescrito:
`exercises` é catálogo, e as três colunas são novas.

## O problema

`Progressao.decidir()`/`calibrar()` exigem, por exercício, três coisas que o schema não tinha:
`incremento`, `barraLivre` e — achado nesta fatia — `cargaInvertida`. Verificado por grep em
`server/src/main/kotlin` e em `shared/features/{exercise,session}`: os nomes só apareciam como
**parâmetro** de `Progressao`, sem nenhum produtor. O plano original do RIR
(`docs/trabalho/rir-plano.md`) presumia que já existiam em algum canto do catálogo. Não existiam.

O único "incremento" no código era o passo fixo de 2,5 kg dos botões `-`/`+` da UI
(`WorkoutSessionViewModel.ajustarPeso`), igual para todo mundo e sem relação com o exercício.

## A decisão, e o que ela não é

| Fato | Consequência |
|---|---|
| `incremento` é granularidade FÍSICA, curada por **equipamento** | 1 valor por `equipment`, derivado na V74 |
| `decidir()` também o usa como **passo aditivo** | os dois usos ficam colados; débito do passo proporcional |
| `barra_livre` é função pura de `equipment` HOJE | coluna + CHECK que impede discordância ([INV]) |
| `carga_invertida` é **direção**, não magnitude | não é débito: ligar o fio sem ela causa dano ativo |
| `incremento` NULL não é "não curado" | é "não se aplica": o chamador não invoca o motor |

**Curar por equipamento é paliativo declarado.** Põe leg press e pec deck no mesmo número. Foi
escolhido porque é o eixo certo para granularidade (anilha é anilha), o aparato de curadoria já
existe, e `MACHINE`/`CABLE` nulos significariam **progressão automática inexistente para quem mais
precisa dela** — o programa do iniciante na academia é quase todo máquina e cabo.

Por isso `incremento` **não** ganhou CHECK amarrando-o a `equipment`, ao contrário de `barra_livre`:
ele nasce derivado e **o destino dele é deixar de ser**. Amarrá-lo hoje cimentaria o paliativo.

## O teto desta abordagem (o que a próxima iteração precisa saber)

Curar `incremento` **por exercício** (os 147, como `familia` e os `requires_*`) melhora leg press
contra pec deck, mas **não resolve a variância real, que é por ACADEMIA**: o mesmo pec deck tem pino
de 2,5 numa rede e 5 em outra. O teto é **calibração derivada do histórico do usuário**, não
curadoria por exercício.

E há duas famílias que número nenhum resolve, porque o problema é a semântica de `carga`:

- **Assistida** (4 exercícios): carga invertida. Resolvido nesta fatia pela flag; falta a troca de
  sinal no motor.
- **Aditiva sobre o corpo** (`Barra Fixa com Peso`, `Flexão com Peso`): `carga` é o peso ADICIONAL.
  O piso `max(carga − inc, inc)` impede prescrever "sem peso adicional", e o raciocínio percentual é
  cego ao peso corporal — 2,5→5 kg é +100% para o motor e ~+3% na vida real. Débito separado.

## Quem calibra a heurística

**Ninguém preenche.** Não é campo de perfil, não é tela, não é curadoria. Curto prazo é **tarefa de
medição** sobre os registros (hoje, o Rafael); destino é derivar do histórico. Registrado assim de
propósito, **para não nascer uma tela por falta de dono.**
