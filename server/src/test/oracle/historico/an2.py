import motor, itertools, statistics, collections, random
# instabilidade por seed: mesmo perfil, 6 geracoes
def exset(prog):
    return set(f['ex']['n'] for d in prog for f in d['filled'] if f['ex'])
jac=[]; anchor_change=[]
profs=[]
for goal in ["GAIN_MUSCLE","LOSE_FAT"]:
  for level in ["BEGINNER","INTERMEDIATE","ADVANCED"]:
    for days in [2,3,4,5,6]:
      for env in ["ACADEMIA","CASA"]:
        profs.append((goal,level,days,env))
for goal,level,days,env in profs:
    sets=[]; anchors=[]
    for seed in range(6):
        sk,prog=motor.generate(goal,level,days,[],env,28,seed=seed)
        sets.append(exset(prog))
        anchors.append(tuple((f['slot']['target'],f['ex']['name'] if f['ex'] else None)
                             for d in prog for f in d['filled'] if f['slot']['role']=="COMPOSTO_PESADO"))
    for a,b in itertools.combinations(sets,2):
        jac.append(len(a&b)/len(a|b) if (a|b) else 1.0)
    anchor_change.append(sum(1 for a,b in itertools.combinations(anchors,2) if a!=b)/15)
print("=== 8. INSTABILIDADE POR SEED (gerar de novo) ===")
print("  sobreposicao media dos exercicios entre duas geracoes do MESMO perfil:", f"{100*statistics.mean(jac):.0f}%")
print("  pares de geracoes em que TODAS as ancoras (composto pesado) nao sao identicas:", f"{100*statistics.mean(anchor_change):.0f}%")

print("\n=== 9. COMPOSICAO DE PAPEIS POR DIA ===")
cnt=collections.Counter(); byday=collections.Counter()
for goal,level,days,env in profs:
    sk,prog=motor.generate(goal,level,days,[],env,28,seed=3)
    for d in prog:
        rs=collections.Counter(f['slot']['role'] for f in d['filled'])
        byday[tuple(sorted(rs.items()))]+=0
        for k,v in rs.items(): cnt[k]+=v
tot=sum(cnt.values())
for k,v in cnt.items(): print(f"  {k:20s} {100*v/tot:.0f}% dos slots")
# dias 100% pesado+iso sem acessorio no upper
ul=0; ul_noacc=0
for goal,level,days,env in profs:
    sk,prog=motor.generate(goal,level,days,[],env,28,seed=3)
    for d in prog:
        if d['label'].startswith('Upper'):
            ul+=1
            if not any(f['slot']['role']=="COMPOSTO_ACESSORIO" for f in d['filled']): ul_noacc+=1
print(f"  dias Upper sem nenhum COMPOSTO_ACESSORIO: {100*ul_noacc/max(1,ul):.0f}% ({ul_noacc}/{ul})")

print("\n=== 10. REPETICAO DE EXERCICIO NA SEMANA (base p/ progressao) ===")
rep=[]
for goal,level,days,env in profs:
    sk,prog=motor.generate(goal,level,days,[],env,28,seed=3)
    names=[f['ex']['n'] for d in prog for f in d['filled'] if f['ex']]
    rep.append(len(names)-len(set(names)))
print("  exercicios repetidos entre sessoes da semana:", sum(rep), "(por construcao: alreadyUsed e por programa)")
