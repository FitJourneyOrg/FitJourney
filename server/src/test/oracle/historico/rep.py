import json, statistics, collections, motor
rows=json.load(open('cmp.json'))
P=lambda x:f"{100*x:.0f}%"
def M(k,eng,sub=None):
    s=sub or rows
    return statistics.mean([r[eng][k] for r in s])
print("### ATENDIMENTO EFETIVO (direto + indireto fracionado) / alvo\n")
print(f"{'':12s} {'ATUAL':>8s} {'V2':>8s}")
for k,lab in [('att_all','media geral'),('att_big','grandes'),('att_small','pequenos')]:
    print(f"{lab:12s} {P(M(k,'atual')):>8s} {P(M(k,'v2')):>8s}")
print(f"{'musc <70%':12s} {M('under70','atual'):>8.1f} {M('under70','v2'):>8.1f}  (quantos musculos por programa ficam abaixo de 70% do alvo)")
print("\n### POR MUSCULO")
print(f"{'':11s} {'ATUAL':>7s} {'V2':>7s}")
for m in ["CHEST","BACK","SHOULDERS","QUADS","POSTERIOR","GLUTES","BICEPS","TRICEPS","FOREARMS","CALVES","CORE"]:
    a=statistics.mean([r['atual']['att'][m] for r in rows]); b=statistics.mean([r['v2']['att'][m] for r in rows])
    print(f"{m:11s} {P(a):>7s} {P(b):>7s}")
print("\n### FOCO")
f=[r for r in rows if r['focus']]
print(f"  atendimento do musculo focado:   ATUAL {P(M('att_focus','atual',f))}   V2 {P(M('att_focus','v2',f))}")
print(f"  foco abaixo da media do programa: ATUAL {P(sum(1 for r in f if r['atual']['att_focus']<r['atual']['att_all'])/len(f))}   V2 {P(sum(1 for r in f if r['v2']['att_focus']<r['v2']['att_all'])/len(f))}")
print("\n### SLOTS VAZIOS")
for env in ["ACADEMIA","CASA"]:
    s=[r for r in rows if r['env']==env]
    print(f"  {env:9s} perfis com vazio: ATUAL {P(sum(1 for r in s if r['atual']['empty'])/len(s))}   V2 {P(sum(1 for r in s if r['v2']['empty'])/len(s))}")
for lv in ["BEGINNER","INTERMEDIATE","ADVANCED"]:
    s=[r for r in rows if r['level']==lv]
    print(f"  {lv:12s} ATUAL {P(sum(1 for r in s if r['atual']['empty'])/len(s))}   V2 {P(sum(1 for r in s if r['v2']['empty'])/len(s))}")
print("\n### SESSAO")
print(f"  series/sessao:  ATUAL {M('sets_day','atual'):.1f}   V2 {M('sets_day','v2'):.1f}")
print(f"  minutos/sessao: ATUAL {M('min_day','atual'):.0f}   V2 {M('min_day','v2'):.0f}")
print(f"  dias com composto acessorio: ATUAL {P(M('acc_frac','atual'))}   V2 {P(M('acc_frac','v2'))}")
