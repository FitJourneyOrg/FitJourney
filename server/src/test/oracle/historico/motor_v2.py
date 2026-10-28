"""Motor v2 — orcamento primeiro, prioridade na escassez, volume indireto contado,
papel por composicao, continuidade por mesociclo. Deterministico."""
import motor, random, itertools, collections
T=motor.TARGETS
# ---- prescricao por objetivo: (reps, descanso, RIR) pesado/acessorio/isolamento ----
GOAL_REPS={
 "GAIN_MUSCLE":   (("6-9",120,2),("8-12",90,1),("12-16",60,1)),
 "LOSE_FAT":      (("6-9",120,2),("8-12",75,1),("12-18",50,1)),
 "MAINTAIN":      (("5-8",150,3),("8-12",105,2),("12-15",75,2)),
 "GENERAL_HEALTH":(("8-12",120,3),("10-14",90,3),("12-18",75,3))}
GOAL_VOL={"GAIN_MUSCLE":1.0,"LOSE_FAT":0.9,"MAINTAIN":0.75,"GENERAL_HEALTH":None}
WORK={"COMPOSTO_PESADO":30,"COMPOSTO_ACESSORIO":35,"ISOLAMENTO":40}
def cost(role,goal):
    rest=GOAL_REPS[goal][{"COMPOSTO_PESADO":0,"COMPOSTO_ACESSORIO":1,"ISOLAMENTO":2}[role]][1]
    return (WORK[role]+rest)/60.0
EX_OVERHEAD=1.0; WARMUP=6.0; MIN_SETS_EX=3; MAX_SETS_EX=5; MAX_EX_MUSCLE=3
MEV={m:motor.V[m][0] for m in T}; MRV={m:motor.V[m][3] for m in T}
W={"CHEST":1.0,"BACK":1.1,"SHOULDERS":0.9,"QUADS":1.0,"POSTERIOR":0.9,"GLUTES":0.7,
   "BICEPS":0.5,"TRICEPS":0.55,"FOREARMS":0.2,"CALVES":0.4,"CORE":0.5}
FOCUS_MULT=1.8
SACRIFICE=["FOREARMS","CALVES","CORE","GLUTES","BICEPS","TRICEPS","SHOULDERS"]
SYN={"CHEST":{"TRICEPS":0.5},"SHOULDERS":{"TRICEPS":0.5},
     "BACK":{"BICEPS":0.5,"FOREARMS":0.5},"POSTERIOR":{"GLUTES":0.5,"FOREARMS":0.33},
     "QUADS":{"GLUTES":0.4},"GLUTES":{"POSTERIOR":0.2}}
INDIRECT_RECEIVERS={"BICEPS","TRICEPS","FOREARMS","GLUTES"}
DEMAND_BASE={"MACHINE":1,"CABLE":1,"DUMBBELL":2,"BODYWEIGHT":2,"BAND":2,"BARBELL":3}
DEMAND_OVERRIDE={"Barra Fixa":4,"Chin-Up":4,"Mergulho nas Paralelas":4,"Agachamento Livre":4,"Levantamento Terra":4}
def demand(e):
    for k,v in DEMAND_OVERRIDE.items():
        if k in e['name']: return v
    return DEMAND_BASE.get(e['eq'],2)
DEMAND_CAP={"BEGINNER":2,"INTERMEDIATE":3,"ADVANCED":4}
def pool_v2(env,level):
    return [e for e in motor.CAT if demand(e)<=DEMAND_CAP[level] and (env=="ACADEMIA" or e['eq'] in motor.HOME_EQ)]
def trainable(env,level):
    p=pool_v2(env,level); return {m for m in T if any(motor.matches(e,m) for e in p)}

def raw_targets(goal,level,focus_groups,age):
    af=motor.age_factor(age)
    ft=set(itertools.chain.from_iterable(motor.FOCUS_TARGETS[g] for g in focus_groups))
    gv=GOAL_VOL[goal]; out={}
    for m in T:
        base=MEV[m] if gv is None else motor.V[m][{"BEGINNER":0,"INTERMEDIATE":1,"ADVANCED":2}[level]]*gv
        out[m]=min(base*af+(3 if m in ft else 0), MRV[m])
    return out, ft

def capacity_sets(goal,days,session_min):
    mix=0.45*cost("COMPOSTO_PESADO",goal)+0.20*cost("COMPOSTO_ACESSORIO",goal)+0.35*cost("ISOLAMENTO",goal)
    usable=days*(session_min-WARMUP-5*EX_OVERHEAD)
    return max(1.0, usable/mix)

def water_fill(raw, ft, muscles, cap):
    """piso (MEV ou alvo, o menor) + excedente proporcional a prioridade, teto no alvo."""
    muscles=list(muscles); dropped=[]
    floor=lambda m: min(MEV[m], raw[m])
    while muscles and sum(floor(m) for m in muscles)>cap:
        for s in SACRIFICE:
            if s in muscles and s not in ft: muscles.remove(s); dropped.append(s); break
        else: break
    plan={m:0.0 for m in T}
    for m in muscles: plan[m]=floor(m)
    left=cap-sum(plan.values())
    for _ in range(60):
        if left<=0.25: break
        hungry=[m for m in muscles if plan[m]<raw[m]-1e-9]
        if not hungry: break
        ws=sum(W[m]*(FOCUS_MULT if m in ft else 1.0) for m in hungry); moved=0.0
        for m in hungry:
            add=min(left*W[m]*(FOCUS_MULT if m in ft else 1.0)/ws, raw[m]-plan[m])
            plan[m]+=add; moved+=add
        left-=moved
        if moved<1e-6: break
    return plan, dropped

def ex_count(sets, iso_only):
    n=sets//MIN_SETS_EX
    return max(1, min(MAX_EX_MUSCLE if not iso_only else 2, n))
def roles_for(n, iso_only):
    if iso_only: return ["ISOLAMENTO"]*n
    if n==1: return ["COMPOSTO_PESADO"]
    if n==2: return ["COMPOSTO_PESADO","ISOLAMENTO"]
    return ["COMPOSTO_PESADO"]+["COMPOSTO_ACESSORIO"]*(n-2)+["ISOLAMENTO"]

def build_days(goal,level,days,ft,age,session_min,plan,split_pref,tr):
    split=motor.resolve_split(days,split_pref); tmpl=motor.split_template(days,split)
    freq={m:max(1,sum(1 for _,mus in tmpl if m in mus)) for m in T}
    # frequencia efetiva: nao fatiar abaixo do piso de 3 series/exercicio
    eff={m: max(1, min(freq[m], int(plan[m]//MIN_SETS_EX))) if plan[m]>=MIN_SETS_EX else 0 for m in T}
    reps=GOAL_REPS[goal]; senior=motor.age_band(age)=="SENIOR"
    # em qual rodada cada musculo entra, quando eff < freq (alterna por dia)
    day_of={m:set() for m in T}
    rounds=collections.defaultdict(list)
    for di,(lab,mus) in enumerate(tmpl):
        for m in mus: rounds[m].append(di)
    for m in T:
        if eff[m]==0: continue
        ds=rounds[m]
        if eff[m]>=len(ds): day_of[m]=set(ds); continue
        off=motor.CANON.index(m) % len(ds)          # desencontra os musculos entre as rodadas
        step=len(ds)/eff[m]
        day_of[m]=set(ds[int((i*step+off)) % len(ds)] for i in range(eff[m]))
    out=[]
    for di,(label,mus) in enumerate(tmpl):
        cand=[m for m in motor.CANON if m in mus and m in tr and di in day_of[m]]
        ss={m: max(MIN_SETS_EX, min(12, int(round(plan[m]/max(1,len(day_of[m]))))) ) for m in cand}
        pri=sorted(cand,key=lambda m:(-(W[m]*(FOCUS_MULT if m in ft else 1.0)), motor.CANON.index(m)))
        budget=session_min-WARMUP; slots=[]
        # ETAPA 1 — piso: 1 exercicio de 3 series por musculo do dia (garante presenca)
        placed={}
        for m in pri:
            role0="ISOLAMENTO" if m in motor.ISO_ONLY else "COMPOSTO_PESADO"
            c=MIN_SETS_EX*cost(role0,goal)+EX_OVERHEAD
            if c>budget: continue
            budget-=c; placed[m]=[dict(target=m,role=role0,sets=MIN_SETS_EX,focus=(m in ft))]
        # ETAPA 2 — excedente: sobe series e abre exercicios, na ordem de prioridade
        progress=True
        while budget>0.5 and progress:
            progress=False
            for m in pri:
                if m not in placed: continue
                cur=sum(x['sets'] for x in placed[m])
                if cur>=ss[m]: continue
                want_n=min(ex_count(ss[m], m in motor.ISO_ONLY), MAX_EX_MUSCLE)
                # abre novo exercicio se o volume justifica e o ultimo ja esta no teto
                if len(placed[m])<want_n and all(x['sets']>=MIN_SETS_EX for x in placed[m]):
                    rr=roles_for(len(placed[m])+1, m in motor.ISO_ONLY)
                    newrole=rr[-1]
                    c=MIN_SETS_EX*cost(newrole,goal)+EX_OVERHEAD
                    if c<=budget:
                        # reescreve os papeis do musculo para a composicao do novo n
                        for i,x in enumerate(placed[m]): x['role']=rr[i]
                        placed[m].append(dict(target=m,role=newrole,sets=MIN_SETS_EX,focus=(m in ft)))
                        budget-=c; progress=True; continue
                cands=[x for x in placed[m] if x['sets']<MAX_SETS_EX]
                if not cands: continue
                x=max(cands,key=lambda y:motor.ROLE_ORD[y['role']])   # engorda o mais barato
                c=cost(x['role'],goal)
                if c<=budget: x['sets']+=1; budget-=c; progress=True
        for m in pri:
            for x in placed.get(m,[]):
                rp,rest,rir=reps[{"COMPOSTO_PESADO":0,"COMPOSTO_ACESSORIO":1,"ISOLAMENTO":2}[x['role']]]
                x.update(reps=rp,rest=rest,rir=min(4,rir+1) if senior else rir)
                slots.append(x)
        slots.sort(key=lambda s:(0 if s['focus'] else 1, motor.ROLE_ORD[s['role']], motor.CANON.index(s['target'])))
        out.append(dict(label=label,slots=slots,minutes=session_min-budget))
    return out,split,freq,eff

def indirect_from(daylist):
    comp=collections.Counter()
    for d in daylist:
        for s in d['slots']:
            if s['role']!="ISOLAMENTO": comp[s['target']]+=s['sets']
    ind=collections.Counter()
    for m,c in comp.items():
        for syn,frac in SYN.get(m,{}).items(): ind[syn]+=c*frac
    return ind

def fill(daylist,pool,focus_names,rng):
    anchors={}; used_global=set(); out=[]; fails=[]
    for d in daylist:
        used_day=set(); comp_pat=set(); res=[]
        for s in d['slots']:
            t,role=s['target'],s['role']
            a=anchors.get(t)
            if role=="COMPOSTO_PESADO" and a and a['n'] not in used_day:
                used_day.add(a['n']); comp_pat.add(a['pat']); res.append(dict(slot=s,ex=a)); continue
            cand=[e for e in pool if motor.matches(e,t) and e['n'] not in used_day]
            byrole=[e for e in cand if (e['comp'] if role!="ISOLAMENTO" else not e['comp'])]
            cand=byrole if byrole else cand          # substituicao guiada: papel vizinho
            if not cand:
                res.append(dict(slot=s,ex=None)); fails.append((d['label'],t,role)); continue
            def sc(e):
                v=0.0; dm=demand(e)
                if any(p in focus_names for p in e['prim']): v+=4.0
                if role=="COMPOSTO_PESADO": v+=0.6*(dm-1)
                elif role=="ISOLAMENTO": v+=0.6*(4-dm)
                if e['base']: v+=0.5
                if e['n'] in used_global: v-=1.0
                if e['comp'] and e['pat'] in comp_pat: v-=2.5
                return v+rng.random()*0.35
            best=max(cand,key=sc)
            used_day.add(best['n']); used_global.add(best['n'])
            if best['comp']: comp_pat.add(best['pat'])
            if role=="COMPOSTO_PESADO": anchors.setdefault(t,best)
            res.append(dict(slot=s,ex=best))
        out.append(dict(label=d['label'],filled=res,minutes=d['minutes']))
    return out,fails

def generate(goal,level,days,focus_groups,env,age,split_pref=None,session_min=60.0,mesocycle=0):
    tr=trainable(env,level)
    raw,ft=raw_targets(goal,level,focus_groups,age)
    cap=capacity_sets(goal,days,session_min)
    plan,dropped=water_fill(raw,ft,[m for m in T if m in tr],cap)
    # passo 1: estrutura provisoria -> volume indireto medido -> alvo DIRETO dos sinergistas
    d1,split,freq,eff=build_days(goal,level,days,ft,age,session_min,plan,split_pref,tr)
    ind=indirect_from(d1)
    plan2=dict(plan)
    for m in INDIRECT_RECEIVERS:
        if plan[m]<=0: continue
        need=plan[m]-ind[m]
        if need>=MIN_SETS_EX*0.8: plan2[m]=max(MIN_SETS_EX,need)
        elif m in ("BICEPS","TRICEPS") and plan[m]>=5: plan2[m]=float(MIN_SETS_EX)  # piso direto
        else: plan2[m]=0.0
    freed=sum(plan[m]-plan2[m] for m in INDIRECT_RECEIVERS)
    if freed>0:   # reinveste o orcamento liberado nos prioritarios, respeitando o alvo
        hungry=[m for m in T if plan2[m]>0 and plan2[m]<raw[m]-1e-9 and m not in INDIRECT_RECEIVERS]
        for _ in range(20):
            if freed<=0.25 or not hungry: break
            ws=sum(W[m]*(FOCUS_MULT if m in ft else 1.0) for m in hungry); moved=0.0
            for m in hungry:
                add=min(freed*W[m]*(FOCUS_MULT if m in ft else 1.0)/ws, raw[m]-plan2[m])
                plan2[m]+=add; moved+=add
            freed-=moved
            if moved<1e-6: break
            hungry=[m for m in hungry if plan2[m]<raw[m]-1e-9]
    d2,split,freq,eff=build_days(goal,level,days,ft,age,session_min,plan2,split_pref,tr)
    # passo 3: gastar o tempo que sobrou no dia, em ordem de prioridade, sem passar do alvo
    delivered=collections.Counter()
    for d in d2:
        for s_ in d['slots']: delivered[s_['target']]+=s_['sets']
    for d in d2:
        left=session_min-WARMUP-d['minutes']
        pri=sorted({s_['target'] for s_ in d['slots']},
                   key=lambda m:(-(W[m]*(FOCUS_MULT if m in ft else 1.0)), motor.CANON.index(m)))
        progress=True
        while left>0.5 and progress:
            progress=False
            for m in pri:
                if delivered[m]>=raw[m]-0.5: continue
                cands=[s_ for s_ in d['slots'] if s_['target']==m and s_['sets']<MAX_SETS_EX]
                if not cands: continue
                s_=min(cands,key=lambda x:motor.ROLE_ORD[x['role']]*-1)   # engorda o mais barato (isolamento)
                c=cost(s_['role'],goal)
                if c>left: continue
                s_['sets']+=1; delivered[m]+=1; left-=c; d['minutes']+=c; progress=True
                break
    seed=abs(hash((goal,level,days,tuple(sorted(focus_groups)),env,age,split,round(session_min),mesocycle)))%(2**31)
    filled,fails=fill(d2,pool_v2(env,level),set(focus_groups),random.Random(seed))
    return dict(plan=plan2,raw=raw,indirect=dict(ind),dropped=dropped,cap=cap,split=split,
                freq=freq,eff=eff,days=filled,failures=fails,trainable=tr)
