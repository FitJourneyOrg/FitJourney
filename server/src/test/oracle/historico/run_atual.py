import motor, itertools, collections, statistics, json
GOALS=["GAIN_MUSCLE","LOSE_FAT","MAINTAIN","GENERAL_HEALTH"]
LEVELS=["BEGINNER","INTERMEDIATE","ADVANCED"]
AGES=[28,45,65,None]; ENVS=["ACADEMIA","CASA"]
FOCUS=[[],["BACK"],["CHEST"],["LEGS"],["SHOULDERS"],["BICEPS"],["GLUTES"],["TRICEPS"],["CORE"],["FOREARMS"]]
SEEDS=[1,2,3]
WORK={"COMPOSTO_PESADO":30,"COMPOSTO_ACESSORIO":35,"ISOLAMENTO":40}
BIG={"CHEST","BACK","SHOULDERS","QUADS","POSTERIOR"}
SMALL={"BICEPS","TRICEPS","FOREARMS","CALVES","CORE","GLUTES"}

rows=[]
for goal,level,days,age,env,focus in itertools.product(GOALS,LEVELS,[2,3,4,5,6],AGES,ENVS,FOCUS):
    eligible = level!="BEGINNER" and goal!="GENERAL_HEALTH"
    eff_focus = focus if eligible else []
    if focus and not eligible: continue          # evita duplicar o caso "sem foco"
    for split in motor.SPLIT_OPTS[days]:
        for seed in SEEDS:
            sk,prog=motor.generate(goal,level,days,eff_focus,env,age,split_pref=split,seed=seed)
            delivered=collections.Counter(); empty=0; nslots=0
            day_sets=[]; day_min=[]; acc_days=0
            for d in prog:
                ds=0; dm=0; roles=set()
                for f in d['filled']:
                    s=f['slot']; nslots+=1; roles.add(s['role'])
                    if f['ex'] is None: empty+=1; continue
                    delivered[s['target']]+=s['sets']
                    ds+=s['sets']; dm+=s['sets']*(WORK[s['role']]+s['rest'])
                day_sets.append(ds); day_min.append(dm/60+5)
                if "COMPOSTO_ACESSORIO" in roles: acc_days+=1
            tgt=sk['weekly']
            att={m: (delivered[m]/tgt[m] if tgt[m] else 1.0) for m in motor.TARGETS}
            ft=set(itertools.chain.from_iterable(motor.FOCUS_TARGETS[g] for g in eff_focus))
            rows.append(dict(goal=goal,level=level,days=days,age=age,env=env,
                focus=",".join(eff_focus),split=split,seed=seed,
                empty=empty,nslots=nslots,
                att=att, delivered=dict(delivered), target=dict(tgt),
                att_focus=(statistics.mean([att[m] for m in ft]) if ft else None),
                att_big=statistics.mean([att[m] for m in BIG]),
                att_small=statistics.mean([att[m] for m in SMALL]),
                att_all=statistics.mean(att.values()),
                sets_day=statistics.mean(day_sets), min_day=statistics.mean(day_min),
                acc_frac=acc_days/len(prog)))
json.dump(rows, open('atual.json','w'))
print("perfis simulados:", len(rows))
