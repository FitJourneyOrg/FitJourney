"""Sensibilidade do atendimento aos coeficientes de volume indireto.
V1 = primeira versao que eu escrevi · V2 = depois da minha propria critica · V3 = 0,5 do Rafael."""
import importlib, statistics, collections, itertools, json
import motor, motor_v2 as v2

VARIANTS = {
 "V1 (1a versao)": (
   {"CHEST":{"TRICEPS":0.5,"SHOULDERS":0.33},"SHOULDERS":{"TRICEPS":0.5},
    "BACK":{"BICEPS":0.5,"FOREARMS":0.5},"POSTERIOR":{"GLUTES":0.5,"FOREARMS":0.33},
    "QUADS":{"GLUTES":0.5,"POSTERIOR":0.25},"GLUTES":{"POSTERIOR":0.5}},
   {"BICEPS","TRICEPS","FOREARMS","GLUTES","POSTERIOR","SHOULDERS"}),
 "V2 (pos-critica)": (
   {"CHEST":{"TRICEPS":0.5},"SHOULDERS":{"TRICEPS":0.5},
    "BACK":{"BICEPS":0.5,"FOREARMS":0.5},"POSTERIOR":{"GLUTES":0.5,"FOREARMS":0.33},
    "QUADS":{"GLUTES":0.4},"GLUTES":{"POSTERIOR":0.2}},
   {"BICEPS","TRICEPS","FOREARMS","GLUTES"}),
 "V3 (0,5 Rafael)": (
   {"CHEST":{"TRICEPS":0.5},"SHOULDERS":{"TRICEPS":0.5},
    "BACK":{"BICEPS":0.5,"FOREARMS":0.5},"POSTERIOR":{"GLUTES":0.5,"FOREARMS":0.5},
    "QUADS":{"GLUTES":0.5},"GLUTES":{"POSTERIOR":0.2}},
   {"BICEPS","TRICEPS","FOREARMS","GLUTES"}),
 "V4 (0,5 puro)": (
   {"CHEST":{"TRICEPS":0.5},"SHOULDERS":{"TRICEPS":0.5},
    "BACK":{"BICEPS":0.5,"FOREARMS":0.5},"POSTERIOR":{"GLUTES":0.5,"FOREARMS":0.5},
    "QUADS":{"GLUTES":0.5},"GLUTES":{"POSTERIOR":0.5}},
   {"BICEPS","TRICEPS","FOREARMS","GLUTES","POSTERIOR"}),
}
MS=["CHEST","BACK","SHOULDERS","QUADS","POSTERIOR","GLUTES","BICEPS","TRICEPS","FOREARMS","CALVES"]
BIG={"CHEST","BACK","SHOULDERS","QUADS","POSTERIOR"}; SMALL={"BICEPS","TRICEPS","FOREARMS","CALVES","CORE","GLUTES"}
GOALS=["GAIN_MUSCLE","LOSE_FAT","MAINTAIN","GENERAL_HEALTH"]; LEVELS=["BEGINNER","INTERMEDIATE","ADVANCED"]
FOCUS=[[],["BACK"],["CHEST"],["LEGS"]]
out={}
for name,(syn,recv) in VARIANTS.items():
    v2.SYN=syn; v2.INDIRECT_RECEIVERS=recv
    accA=collections.defaultdict(list); accV=collections.defaultdict(list)
    focA=[]; focV=[]; invA=0; invV=0; nfoc=0
    for goal,level,days,age,env,focus in itertools.product(GOALS,LEVELS,[2,3,4,5,6],[28,45,65,None],["ACADEMIA","CASA"],FOCUS):
        elig=level!="BEGINNER" and goal!="GENERAL_HEALTH"
        if focus and not elig: continue
        ef=focus if elig else []
        ft=set(itertools.chain.from_iterable(motor.FOCUS_TARGETS[g] for g in ef))
        for split in motor.SPLIT_OPTS[days]:
            def att_of(direct,comp,target):
                ind=collections.Counter()
                for m,c in comp.items():
                    for s,f in syn.get(m,{}).items(): ind[s]+=c*f
                return {m:(min(2.0,(direct[m]+ind[m])/target[m]) if target[m] else 1.0) for m in motor.TARGETS}
            sk,prog=motor.generate(goal,level,days,ef,env,age,split_pref=split,seed=7)
            dA=collections.Counter(); cA=collections.Counter()
            for d in prog:
                for f in d['filled']:
                    if f['ex'] is None: continue
                    s=f['slot']; dA[s['target']]+=s['sets']
                    if s['role']!="ISOLAMENTO": cA[s['target']]+=s['sets']
            aA=att_of(dA,cA,sk['weekly'])
            r=v2.generate(goal,level,days,ef,env,age,split_pref=split,session_min=60.0)
            dV=collections.Counter(); cV=collections.Counter()
            for d in r['days']:
                for f in d['filled']:
                    if f['ex'] is None: continue
                    s=f['slot']; dV[s['target']]+=s['sets']
                    if s['role']!="ISOLAMENTO": cV[s['target']]+=s['sets']
            tV={m:round(r['raw'][m]) for m in motor.TARGETS}
            aV=att_of(dV,cV,tV)
            for m in motor.TARGETS: accA[m].append(aA[m]); accV[m].append(aV[m])
            accA['_big'].append(statistics.mean([aA[m] for m in BIG])); accV['_big'].append(statistics.mean([aV[m] for m in BIG]))
            accA['_small'].append(statistics.mean([aA[m] for m in SMALL])); accV['_small'].append(statistics.mean([aV[m] for m in SMALL]))
            accA['_all'].append(statistics.mean(aA.values())); accV['_all'].append(statistics.mean(aV.values()))
            if ft:
                nfoc+=1
                fa=statistics.mean([aA[m] for m in ft]); fv=statistics.mean([aV[m] for m in ft])
                focA.append(fa); focV.append(fv)
                if fa<statistics.mean(aA.values()): invA+=1
                if fv<statistics.mean(aV.values()): invV+=1
    out[name]=dict(A={k:statistics.mean(v) for k,v in accA.items()},
                   V={k:statistics.mean(v) for k,v in accV.items()},
                   focA=statistics.mean(focA),focV=statistics.mean(focV),
                   invA=invA/nfoc,invV=invV/nfoc)
P=lambda x:f"{100*x:.0f}%"
print("### ATENDIMENTO EFETIVO DO MOTOR V2, por conjunto de coeficientes\n")
hdr=f"{'':18s}"+"".join(f"{k:>18s}" for k in VARIANTS); print(hdr)
for k,lab in [('_all','media geral'),('_big','GRANDES'),('_small','pequenos')]:
    print(f"{lab:18s}"+"".join(f"{P(out[v]['V'][k]):>18s}" for v in VARIANTS))
print(f"{'foco':18s}"+"".join(f"{P(out[v]['focV']):>18s}" for v in VARIANTS))
print(f"{'foco invertido':18s}"+"".join(f"{P(out[v]['invV']):>18s}" for v in VARIANTS))
print()
for m in MS:
    print(f"{m:18s}"+"".join(f"{P(out[v]['V'][m]):>18s}" for v in VARIANTS))
print("\n### o MESMO, medindo o motor ATUAL (a contabilidade muda os dois lados)\n")
print(hdr)
for k,lab in [('_all','media geral'),('_big','GRANDES'),('_small','pequenos')]:
    print(f"{lab:18s}"+"".join(f"{P(out[v]['A'][k]):>18s}" for v in VARIANTS))
for m in ["POSTERIOR","SHOULDERS","GLUTES","FOREARMS"]:
    print(f"{m:18s}"+"".join(f"{P(out[v]['A'][m]):>18s}" for v in VARIANTS))
json.dump({k:{'V':v['V'],'A':v['A']} for k,v in out.items()}, open('sens.json','w'))
