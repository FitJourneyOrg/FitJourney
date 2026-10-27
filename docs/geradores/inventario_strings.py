# -*- coding: utf-8 -*-
"""
Inventario dos literais do app Android (fatia G.3, ARCH #37).

REGRA DE PROJETO DESTE SCRIPT: ERRAR PARA MAIS.
----------------------------------------------
Os tres defeitos do `gen_erros.py` nesta sessao foram todos de SUBnotificacao: perdia frases,
cortava frases ao meio, mostrava contexto de outra funcao. Subnotificacao passa despercebida,
porque o relatorio parece limpo.

Aqui o vies e o oposto. Na duvida, o literal entra como CANDIDATO e eu descarto lendo. Um falso
positivo custa dez segundos de leitura; um falso negativo custa uma frase em portugues no meio de
um app em ingles, descoberta por um usuario.

Uso:
    python3 inventario_strings.py            # resumo por arquivo
    python3 inventario_strings.py --detalhe  # cada candidato com a linha
"""
import os
import re
import sys
from collections import defaultdict

RAIZ = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))), "app", "androidApp", "src", "main", "kotlin")

# ---------------------------------------------------------------------------
# NAO e texto de tela. Cada padrao aqui e uma decisao, e a lista e curta de proposito:
# quanto mais eu excluo automaticamente, mais chance de excluir algo que era texto.
# ---------------------------------------------------------------------------
DESCARTES = [
    (re.compile(r'\b(Log|log)\.[dviwe]\s*\('), "log"),
    (re.compile(r'\btag\s*=|"FitJourney[A-Za-z]*"'), "tag de log"),
    (re.compile(r'\bgetSystemService|Manifest\.permission|android\.'), "api do sistema"),
    (re.compile(r'\.route|AppRoute|navigate\(|popUpTo'), "rota de navegacao"),
    (re.compile(r'\bhttp[s]?://|\bwww\.'), "url"),
    (re.compile(r'"[a-z_]+"\s+to\b'), "chave de mapa"),
    (re.compile(r'\bJson|Serializable|encodeTo|decodeFrom'), "serializacao"),
]

# Onde texto de tela costuma aparecer. NAO e filtro: e classificacao, para eu priorizar.
FORTES = re.compile(
    r'\b(Text|OutlinedButton|Button|TextButton|Tab|Label|AlertDialog|Snackbar)\s*\(|'
    r'\b(text|title|titulo|label|placeholder|supportingText|contentDescription|rotulo)\s*='
)

LITERAL = re.compile(r'"((?:[^"\\\n]|\\.)*)"')


def parece_texto(s):
    """
    Heuristica DELIBERADAMENTE frouxa.

    Entra qualquer coisa com espaco, ou com acento, ou que comece com maiuscula e tenha 3+ letras.
    Sai o que e claramente identificador: sem espaco, todo minusculo ou todo maiusculo com _.
    """
    if len(s.strip()) < 2:
        return False
    if re.fullmatch(r"[A-Z0-9_]+", s):          # CONSTANTE
        return False
    if re.fullmatch(r"[a-z][a-zA-Z0-9]*", s):   # camelCase sem espaco
        return False
    if re.fullmatch(r"[\W\d\s]+", s):           # so simbolo/numero
        return False
    tem_espaco = " " in s.strip()
    tem_acento = bool(re.search(r"[áàâãéêíóôõúçÁÀÂÃÉÊÍÓÔÕÚÇ]", s))
    comeca_maiusculo = bool(re.match(r"[A-ZÁÀÂÃÉÊÍÓÔÕÚÇ]", s))
    return tem_espaco or tem_acento or (comeca_maiusculo and len(s) >= 3)


def sem_comentarios(texto):
    limpo = re.sub(r"/\*.*?\*/", lambda m: "\n" * m.group(0).count("\n"), texto, flags=re.S)
    return re.sub(r"//[^\n]*", "", limpo)


por_arquivo = defaultdict(list)
total, descartados = 0, defaultdict(int)

for raiz, _, arquivos in os.walk(RAIZ):
    for nome in sorted(arquivos):
        if not nome.endswith(".kt"):
            continue
        caminho = os.path.join(raiz, nome)
        texto = open(caminho, encoding="utf-8").read()
        limpo = sem_comentarios(texto)
        for i, linha in enumerate(limpo.split("\n"), 1):
            motivo = next((m for p, m in DESCARTES if p.search(linha)), None)
            for s in LITERAL.findall(linha):
                if not parece_texto(s):
                    continue
                if motivo:
                    descartados[motivo] += 1
                    continue
                total += 1
                por_arquivo[os.path.relpath(caminho, RAIZ)].append({
                    "linha": i,
                    "texto": s,
                    "forte": bool(FORTES.search(linha)),
                    "codigo": linha.strip()[:110],
                })

print("=" * 78)
print("CANDIDATOS A TEXTO DE TELA: %d  em %d arquivos" % (total, len(por_arquivo)))
fortes = sum(1 for v in por_arquivo.values() for c in v if c["forte"])
print("   dos quais em posicao FORTE (Text(, label=, titulo=...): %d" % fortes)
print("   os outros %d entram como candidatos mesmo assim, por decisao" % (total - fortes))
print()
print("DESCARTADOS automaticamente (e o que cada motivo tirou):")
for motivo, n in sorted(descartados.items(), key=lambda kv: -kv[1]):
    print("   %-22s %d" % (motivo, n))
print("=" * 78)
print()

for arq, itens in sorted(por_arquivo.items(), key=lambda kv: -len(kv[1])):
    f = sum(1 for c in itens if c["forte"])
    print("%-62s %3d  (forte: %d)" % (arq.replace("dev/rafael/app/", ""), len(itens), f))

if "--detalhe" in sys.argv:
    print("\n" + "=" * 78)
    for arq, itens in sorted(por_arquivo.items()):
        print("\n### %s" % arq)
        for c in itens:
            print("  %s:%-5d %s" % ("F" if c["forte"] else " ", c["linha"], c["texto"][:88]))
