# -*- coding: utf-8 -*-
"""
Gera o RELATÓRIO DO CATÁLOGO (estado real do banco) em markdown + PDF.

Consulta o Postgres via `docker exec` (mesmo container do docker-compose), monta as tabelas
e escreve:
    FitJourney_CATALOGO.md   (fonte, na raiz — local/gitignored)
    FitJourney_CATALOGO.pdf  (o relatório)

Uso (Windows, na raiz do projeto, com o container de pé):
    python docs/geradores/gen_catalogo.py
    # container com outro nome:  python docs/geradores/gen_catalogo.py meu-postgres
"""
import subprocess, sys, os, datetime
from genlib import build_pdf

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ROOT = os.path.join(HERE, "..")
CONTAINER = sys.argv[1] if len(sys.argv) > 1 else "fitjourney-postgres"
DB_USER, DB_NAME = "fitjourney", "fitjourney"

def q(sql):
    """Roda a query no container e devolve as linhas como lista de listas."""
    out = subprocess.run(
        ["docker", "exec", "-i", CONTAINER, "psql", "-U", DB_USER, "-d", DB_NAME,
         "-t", "-A", "-F", "|", "-c", sql],
        capture_output=True, text=True, encoding="utf-8",
    )
    if out.returncode != 0:
        raise SystemExit(f"Falha ao consultar o banco:\n{out.stderr}\n"
                         f"(container '{CONTAINER}' está de pé? docker ps)")
    return [ln.split("|") for ln in out.stdout.strip().splitlines() if ln.strip()]

def table(headers, rows, total_col=None):
    """Markdown table; total_col = índice da coluna numérica pra somar %."""
    md = "| " + " | ".join(headers) + " |\n|" + "|".join(["---"] * len(headers)) + "|\n"
    if total_col is not None:
        tot = sum(int(r[total_col]) for r in rows) or 1
    for r in rows:
        cells = list(r)
        if total_col is not None:
            cells.append(f"{100 * int(r[total_col]) / tot:.1f}%")
        md += "| " + " | ".join(cells) + " |\n"
    return md

def main():
    hoje = datetime.date.today().isoformat()

    completude = q("""SELECT count(*),
        count(*) FILTER (WHERE description IS NOT NULL AND description <> ''),
        count(*) FILTER (WHERE cardinality(primary_muscles) > 0),
        count(*) FILTER (WHERE video_ref <> '' AND thumb_ref <> ''),
        count(*) FILTER (WHERE is_base) FROM exercises;""")[0]
    total, com_desc, com_tax, com_midia, base = completude

    casa = q("""SELECT count(*) FROM exercises
        WHERE equipment IN ('BODYWEIGHT','DUMBBELL','BAND','KETTLEBELL','SUSPENSION');""")[0][0]
    estrutura = q("""SELECT count(*) FILTER (WHERE is_compound),
        count(*) FILTER (WHERE NOT is_compound), count(*) FILTER (WHERE unilateral) FROM exercises;""")[0]

    md = f"""# Catálogo de exercícios — relatório

> Retrato do banco em {hoje}. Gerado por `docs/gen_catalogo.py` (consulta o Postgres direto).
> Regenerar a qualquer momento: `python docs/geradores/gen_catalogo.py`.

## Completude

| Métrica | Qtd | Cobertura |
|---|---|---|
| **Total de exercícios** | {total} | — |
| Com descrição | {com_desc} | {100*int(com_desc)/int(total):.1f}% |
| Com taxonomia (músculo/equip./nível) | {com_tax} | {100*int(com_tax)/int(total):.1f}% |
| Com mídia (mp4 + png) | {com_midia} | {100*int(com_midia)/int(total):.1f}% |
| Marcados como base (`is_base`) | {base} | {100*int(base)/int(total):.1f}% |

## Estrutura do movimento

| Compostos | Isolamentos | Unilaterais |
|---|---|---|
| {estrutura[0]} | {estrutura[1]} | {estrutura[2]} |

**Disponíveis para treino em casa** (peso corporal, halter, elástico, kettlebell, suspensão): **{casa}** de {total}.

## Por categoria

{table(["Categoria", "Qtd", "%"], q("SELECT category, count(*) FROM exercises GROUP BY 1 ORDER BY 2 DESC;"), 1)}
## Por músculo primário

{table(["Músculo", "Qtd", "%"], q("SELECT unnest(primary_muscles), count(*) FROM exercises GROUP BY 1 ORDER BY 2 DESC;"), 1)}
## Por equipamento

{table(["Equipamento", "Qtd", "%"], q("SELECT coalesce(equipment,'(nulo)'), count(*) FROM exercises GROUP BY 1 ORDER BY 2 DESC;"), 1)}
## Por nível

{table(["Nível", "Qtd", "%"], q("SELECT coalesce(level,'(nulo)'), count(*) FROM exercises GROUP BY 1 ORDER BY 2 DESC;"), 1)}
## Por modalidade — o que o motor enxerga

O gerador determinístico (ARCH #20/#26) só usa `modality = 'STRENGTH'`. Os exercícios preenchidos
nas curadorias V29/V31/V33 ficaram com `modality` nulo de propósito: aparecem na biblioteca com as
seções completas, mas **não entram na geração de treino** sem uma decisão explícita.

{table(["Modality", "Qtd", "%"], q("SELECT coalesce(modality,'(nulo)'), count(*) FROM exercises GROUP BY 1 ORDER BY 2 DESC;"), 1)}
## Qualidade

| Checagem | Resultado |
|---|---|
| Sem taxonomia | {q("SELECT count(*) FROM exercises WHERE primary_muscles IS NULL OR cardinality(primary_muscles)=0;")[0][0]} |
| Sem descrição | {q("SELECT count(*) FROM exercises WHERE description IS NULL OR description='';")[0][0]} |
| Sem mídia | {q("SELECT count(*) FROM exercises WHERE video_ref='' OR thumb_ref='';")[0][0]} |
| Duplicatas de nome (normalizado) | {len(q("WITH n AS (SELECT regexp_replace(translate(lower(name),'áàâãäéèêëíìîïóòôõöúùûüçñ','aaaaaeeeeiiiiooooouuuucn'),'[^a-z0-9]','','g') k FROM exercises) SELECT k FROM n GROUP BY k HAVING count(*)>1;"))} |

## Histórico da curadoria

| Migration | O que fez |
|---|---|
| V28 | 962 descrições reimportadas (o V4 truncava em ~300 caracteres) |
| V29 | 146 exercícios não curados preenchidos |
| V30 | 176 correções em 126 curados (89 de severidade alta) |
| V31 | 393 pendentes auditados (190 correções) |
| V32 | 13 contradições, 8 dedups, 3 remoções, 15 renomes |
| V33 | 33 órfãos preenchidos + 2 dedups + Remo Ergômetro |

4 rodadas de auditoria técnica · **621 apontamentos** revisados por humano.

---
_Débitos abertos do catálogo em `FitJourney_DEBITOS.md` (convenção de nomenclatura, vocabulário
Afundo × Avanço, `MuscleGroup` sem CALVES/TRAPEZIUS/LOWER_BACK, distribuição de `level`)._
"""
    md_path = os.path.join(HERE, "gerados", "catalogo.md")
    open(md_path, "w", encoding="utf-8").write(md)
    build_pdf([md_path], os.path.join(HERE, "pdf", "FitJourney_CATALOGO.pdf"), "FitJourney — Catálogo")
    print("Fonte:", os.path.abspath(md_path))

if __name__ == "__main__":
    main()
