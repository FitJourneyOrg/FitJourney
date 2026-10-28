import motor, motor_v2 as v2, itertools, statistics, collections, json
GOALS=["GAIN_MUSCLE","LOSE_FAT","MAINTAIN","GENERAL_HEALTH"]; LEVELS=["BEGINNER","INTERMEDIATE","ADVANCED"]
AGES=[28,45,65,None]; ENVS=["ACADEMIA","CASA"]
FOCUS=[[],["BACK"],["CHEST"],["LEGS"],["SHOULDERS"],["BICEPS"],["GLUTES"]]
WORKC={"COMPOSTO_PESADO":30,"COMPOSTO_ACESSORIO":35,"ISOLAMENTO":40}
BIG={"CHEST","BACK","SHOULDERS","QUADS","POSTERIOR"}; SMALL={"BICEPS","TRICEPS","FOREARMS","CALVES","CORE","GLUTES"}

def indirect_of(slots_by_target_compound):
    ind=collections.Counter()
    for m,c in slots_by_target_compound.items():
        for syn,frac in v2.SYN.get(m,{}).items(): ind[syn]+=c*frac
    return ind

def metrics(target, direct, comp_sets, empty, nslots, day_sets, day_min, acc_days, ndays, focus_t):
    ind=indirect_of(comp_sets)
    eff={m: direct[m]+ind[m] for m in motor.TARGETS}
    att={m:(min(2.0,eff[m]/target[m]) if target[m] else 1.0) for m in motor.TARGETS}
    return dict(att=att, att_big=statistics.mean([att[m] for m in BIG]),
        att_small=statistics.mean([att[m] for m in SMALL]),
        att_all=statistics.mean(att.values()),
        att_focus=(statistics.mean([att[m] for m in focus_t]) if focus_t else None),
        empty=empty, nslots=nslots, sets_day=statistics.mean(day_sets),
        min_day=statistics.mean(day_min), acc_frac=acc_days/ndays,
        under70=sum(1 for m in motor.TARGETS if target[m] and att[m]<0.7))

rows=[]
for goal,level,days,age,env,focus in itertools.product(GOALS,LEVELS,[2,3,4,5,6],AGES,ENVS,FOCUS):
    eligible = level!="BEGINNER" and goal!="GENERAL_HEALTH"
    if focus and not eligible: continue
    ef = focus if eligible else []
    ft=set(itertools.chain.from_iterable(motor.FOCUS_TARGETS[g] for g in ef))
    for split in motor.SPLIT_OPTS[days]:
        # ---- ATUAL ----
        sk,prog=motor.generate(goal,level,days,ef,env,age,split_pref=split,seed=7)
        d=collections.Counter(); comp=collections.Counter(); empty=0; ns=0; ds=[]; dm=[]; acc=0
        for dd in prog:
            t=0; mm=0; roles=set()
            for f in dd['filled']:
                s=f['slot']; ns+=1; roles.add(s['role'])
                if f['ex'] is None: empty+=1; continue
                d[s['target']]+=s['sets']
                if s['role']!="ISOLAMENTO": comp[s['target']]+=s['sets']
                t+=s['sets']; mm+=s['sets']*(WORKC[s['role']]+s['rest'])
            ds.append(t); dm.append(mm/60+5)
            if "COMPOSTO_ACESSORIO" in roles: acc+=1
        ma=metrics(sk['weekly'],d,comp,empty,ns,ds,dm,acc,len(prog),ft)
        # ---- V2 (60 min) ----
        r=v2.generate(goal,level,days,ef,env,age,split_pref=split,session_min=60.0)
        d2=collections.Counter(); comp2=collections.Counter(); e2=0; ns2=0; ds2=[]; dm2=[]; acc2=0
        for dd in r['days']:
            t=0; roles=set()
            for f in dd['filled']:
                s=f['slot']; ns2+=1; roles.add(s['role'])
                if f['ex'] is None: e2+=1; continue
                d2[s['target']]+=s['sets']
                if s['role']!="ISOLAMENTO": comp2[s['target']]+=s['sets']
                t+=s['sets']
            ds2.append(t); dm2.append(dd['minutes'])
            if "COMPOSTO_ACESSORIO" in roles: acc2+=1
        tgt2={m:round(r['raw'][m]) for m in motor.TARGETS}
        mv=metrics(tgt2,d2,comp2,e2,ns2,ds2,dm2,acc2,len(r['days']),ft)
        rows.append(dict(goal=goal,level=level,days=days,age=age,env=env,split=split,
                         focus=",".join(ef),atual=ma,v2=mv))
json.dump(rows,open('cmp.json','w'))
print("pares simulados:",len(rows))
