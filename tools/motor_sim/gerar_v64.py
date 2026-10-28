"""Gera V64__alvo_de_volume_do_motor.sql a partir de alvo_canonico.csv (fonte unica da tabela).

Uso (de tools/motor_sim/):
  python3 gerar_v64.py alvo_canonico.csv cabecalho_v64.sql \
    > ../../server/src/main/resources/db/migration/V64__alvo_de_volume_do_motor.sql
Rodar de novo so enquanto a V64 nao foi aplicada em lugar nenhum; depois dela, mudanca de alvo
e migration nova.
"""
import csv, sys, collections

ALVOS = ["CHEST", "BACK", "SHOULDERS", "BICEPS", "TRICEPS", "FOREARMS",
         "QUADS", "POSTERIOR", "CALVES", "GLUTES", "CORE"]  # = TargetMuscle.entries de 2026-10-14
# CORE fica NESTA lista de proposito: a V64 ja foi aplicada, e este gerador tem de continuar
# reproduzindo o arquivo que gerou. CORE saiu do motor em 2026-10-27 (V68 aperta o CHECK); quem
# olhar aqui procurando a lista viva deve ler spec_v3.ALVOS, nao este congelado.

linhas = list(csv.DictReader(open(sys.argv[1], encoding="utf-8")))
nomes = [l["nome"] for l in linhas]
assert len(nomes) == len(set(nomes)), "nome repetido na tabela canonica"
por_alvo = collections.defaultdict(list)
for l in linhas:
    a = l["volume_target"].strip()
    if a:
        assert a in ALVOS, f"alvo fora de TargetMuscle: {l['nome']} -> {a}"
        por_alvo[a].append(l["nome"])
sem_alvo = [l for l in linhas if not l["volume_target"].strip()]
total = sum(len(v) for v in por_alvo.values())
q = lambda s: "'" + s.replace("'", "''") + "'"

out = []
p = out.append
p(open(sys.argv[2], encoding="utf-8").read().rstrip("\n") if len(sys.argv) > 2 else "")
p("")
p("ALTER TABLE exercises ADD COLUMN volume_target VARCHAR(16);")
p("ALTER TABLE exercises ADD CONSTRAINT chk_exercises_volume_target CHECK (")
p("    volume_target IS NULL OR volume_target IN (" + ", ".join(q(a) for a in ALVOS) + "));")
p("")
for a in ALVOS:
    if not por_alvo[a]:
        continue
    p(f"-- {a} ({len(por_alvo[a])})")
    p(f"UPDATE exercises SET volume_target = {q(a)} WHERE name IN (")
    p(",\n".join("    " + q(n) for n in por_alvo[a]) + ");")
    p("")
p("-- Fora da contabilidade do motor, de proposito (continuam no catalogo para troca e treino manual):")
for l in sem_alvo:
    p(f"--   {l['nome']}: {l['motivo']}")
p("")
p("-- Autoconferencia: nome que nao casar (renomeado, digitado errado, duplicado) derruba a migration")
p("-- aqui, em vez de deixar um exercicio da lista principal invisivel ao motor em silencio.")
p("DO $$")
p("DECLARE")
p("    esperado CONSTANT jsonb := '" + "{" + ", ".join(f'"{a}": {len(por_alvo[a])}' for a in ALVOS if por_alvo[a]) + "}" + "';")
p("    achado   jsonb;")
p("BEGIN")
p("    SELECT jsonb_object_agg(volume_target, n) INTO achado")
p("      FROM (SELECT volume_target, count(*) AS n FROM exercises")
p("             WHERE volume_target IS NOT NULL GROUP BY volume_target) t;")
p("    IF achado IS DISTINCT FROM esperado THEN")
p("        RAISE EXCEPTION 'V64: contagem por alvo diverge. esperado %, achado %', esperado, achado;")
p("    END IF;")
p("END $$;")
print("\n".join(out))
print(f"-- total com alvo: {total}", file=sys.stderr)
