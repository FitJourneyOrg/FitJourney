import json, statistics, collections
rows=json.load(open('atual.json'))
def pct(x): return f"{100*x:.0f}%"
print("=== 1. ATENDIMENTO DO ALVO (media por musculo, todos os perfis) ===")
ms=["CHEST","BACK","SHOULDERS","QUADS","POSTERIOR","GLUTES","BICEPS","TRICEPS","FOREARMS","CALVES","CORE"]
for m in ms:
    v=[r['att'][m] for r in rows]
    print(f"  {m:10s} media {pct(statistics.mean(v)):>5s}  mediana {pct(statistics.median(v)):>5s}  >=90% em {pct(sum(1 for x in v if x>=0.9)/len(v))}")
print("\n=== 2. HIERARQUIA (grandes vs pequenos) ===")
print("  grandes:", pct(statistics.mean([r['att_big'] for r in rows])), " pequenos:", pct(statistics.mean([r['att_small'] for r in rows])))
inv=sum(1 for r in rows if r['att_small']>r['att_big'])
print("  perfis em que o pequeno e MAIS atendido que o grande:", pct(inv/len(rows)))
print("\n=== 3. FOCO ===")
f=[r for r in rows if r['focus']]
print("  perfis com foco:",len(f))
print("  atendimento do musculo de FOCO:", pct(statistics.mean([r['att_focus'] for r in f])))
print("  atendimento medio dos demais :", pct(statistics.mean([statistics.mean([v for k,v in r['att'].items() if k not in r['focus'].split(',') and not (r['focus']=='LEGS' and k in ('QUADS','POSTERIOR','CALVES'))]) for r in f])))
worse=sum(1 for r in f if r['att_focus'] < r['att_all'])
print("  foco atendido ABAIXO da media do proprio programa:", pct(worse/len(f)))
# foco vs sem foco, mesmo perfil
key=lambda r:(r['goal'],r['level'],r['days'],r['age'],r['env'],r['split'],r['seed'])
base={key(r):r for r in rows if not r['focus']}
gan=[]; 
for r in f:
    b=base.get(key(r))
    if not b: continue
    ft=[m for m in r['att'] if r['att'][m]!=b['att'][m]]
    for g in r['focus'].split(','):
        for m in ({'LEGS':['QUADS','POSTERIOR','CALVES']}.get(g,[g])):
            gan.append(r['delivered'].get(m,0)-b['delivered'].get(m,0))
print("  ganho medio de series/semana no musculo focado (foco ON vs OFF):", f"{statistics.mean(gan):+.2f}")
print("  casos em que focar REDUZIU as series do musculo focado:", pct(sum(1 for x in gan if x<0)/len(gan)))
print("\n=== 4. SLOTS VAZIOS ===")
print("  perfis com >=1 slot vazio:", pct(sum(1 for r in rows if r['empty'])/len(rows)))
print("  slots vazios / slots totais:", pct(sum(r['empty'] for r in rows)/sum(r['nslots'] for r in rows)))
for env in ["ACADEMIA","CASA"]:
    sub=[r for r in rows if r['env']==env]
    print(f"   {env}: perfis com vazio {pct(sum(1 for r in sub if r['empty'])/len(sub))}  vazios/slots {pct(sum(r['empty'] for r in sub)/sum(r['nslots'] for r in sub))}")
for lv in ["BEGINNER","INTERMEDIATE","ADVANCED"]:
    sub=[r for r in rows if r['level']==lv]
    print(f"   {lv}: perfis com vazio {pct(sum(1 for r in sub if r['empty'])/len(sub))}")
print("\n=== 5. PAPEL ACESSORIO / DENSIDADE ===")
print("  fracao de dias com COMPOSTO_ACESSORIO:", pct(statistics.mean([r['acc_frac'] for r in rows])))
ul=[r for r in rows if r['split']=='UPPER_LOWER']
print("  idem, so UPPER_LOWER:", pct(statistics.mean([r['acc_frac'] for r in ul])))
print("  series/sessao media:", f"{statistics.mean([r['sets_day'] for r in rows]):.1f}")
print("  minutos/sessao estimados: media", f"{statistics.mean([r['min_day'] for r in rows]):.0f}", "p10", f"{sorted(r['min_day'] for r in rows)[len(rows)//10]:.0f}", "p90", f"{sorted(r['min_day'] for r in rows)[9*len(rows)//10]:.0f}")
print("\n=== 6. OBJETIVO: quantos comportamentos distintos? ===")
g=collections.defaultdict(set)
for r in rows: g[(r['level'],r['days'],r['age'],r['env'],r['split'],r['seed'],r['focus'])].add((r['goal'],json.dumps(r['target'],sort_keys=True)))
same=collections.Counter()
for r in rows: same[r['goal']]=json.dumps(r['target'],sort_keys=True) if r['level']=='INTERMEDIATE' and r['days']==4 and r['age']==28 else same[r['goal']]
print("  alvos semanais por objetivo (INTER, 4d, 28a):")
for k,v in same.items(): print("   ",k, v[:120])
print("\n=== 7. ESCASSEZ ESTRUTURAL: alvo somado vs capacidade ===")
cap=collections.defaultdict(list)
for r in rows:
    tot=sum(r['target'].values()); capac=r['days']*20
    cap[(r['level'],r['days'])].append(tot/capac)
for k in sorted(cap): print(f"   {k[0]:12s} {k[1]}d  alvo/capacidade = {statistics.mean(cap[k]):.2f}")
