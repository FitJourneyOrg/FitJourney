# historico/ — simulador v2, congelado

O redesenho v2 do motor (outubro de 2026), aposentado pela fatia 0 em 14/10.

**Não mexer e não apagar.** Os números de `docs/historico/motor-redesenho-v2.md` saíram daqui, e
arquivo de `historico/` não se edita: estes scripts são a procedência daquele documento.

| arquivo | o que é |
|---|---|
| `motor.py` | réplica do motor Kotlin de 09/10 (StructureEngine + ExercisePreFilter + SlotFiller) |
| `motor_v2.py` | o motor proposto no redesenho v2 |
| `run_atual.py`, `an_atual.py`, `an2.py`, `compare.py`, `rep.py`, `sens.py` | as análises da época |

Lia o pool de `tools/cache/lista-principal-detalhada.xlsx`, com remendos no `motor.load()` e
casamento músculo↔exercício por padrão de movimento e etiqueta — exatamente o que a fatia 0
aposentou ao adotar `volume_target`. O caminho do xlsx em `motor.load()` aponta para uma sessão
antiga, então nada aqui roda sem ajuste.

O simulador vivo é o v3, na pasta acima.
