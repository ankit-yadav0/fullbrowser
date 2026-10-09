import re, glob, sys
files=sorted(glob.glob('app/src/**/*.kt',recursive=True))
src={f:open(f,encoding='utf8').read() for f in files}
def strip(s):
    # remove comments and string contents (keep quotes) in one pass
    out=[];i=0;n=len(s)
    while i<n:
        if s.startswith('//',i):
            while i<n and s[i]!='\n': i+=1
        elif s.startswith('/*',i):
            j=s.find('*/',i+2); i=n if j<0 else j+2
        elif s.startswith('"""',i):
            j=s.find('"""',i+3); out.append('""'); i=n if j<0 else j+3
        elif s[i]=='"':
            i+=1
            while i<n and s[i]!='"':
                i+= 2 if s[i]=='\\' else 1
            i+=1; out.append('""')
        elif s[i]=="'":
            j=i+1
            if j<n and s[j]=='\\': j+=2
            else: j+=1
            while j<n and s[j]!="'": j+=1
            out.append("''"); i=j+1
        else: out.append(s[i]); i+=1
    return ''.join(out)
clean={f:strip(t) for f,t in src.items()}
def match_close(s,i,open_c,close_c):
    d=0
    for j in range(i,len(s)):
        if s[j]==open_c: d+=1
        elif s[j]==close_c:
            d-=1
            if d==0: return j
    return -1
def split_top(s):
    parts=[];d=0;cur=[]
    for ch in s:
        if ch in '([{<': d+= (ch!='<')
        if ch in ')]}': d-=1
        if ch==',' and d==0: parts.append(''.join(cur)); cur=[]
        else: cur.append(ch)
    parts.append(''.join(cur)); return [p for p in parts if p.strip()]
# ---- collect declarations from MAIN sources only
decl_members={}   # name -> set(members)
decl_params={}    # name -> list of param-name sets (overloads)
kinds={}
for f in files:
    if '/src/main/' not in f: continue
    s=clean[f]
    for m in re.finditer(r'\b(?:(?:data|enum|sealed|private|internal|abstract|open)\s+)*(object|class|interface)\s+(\w+)',s):
        kind,name=m.group(1),m.group(2)
        # span: first '{' after decl (before next declaration) to its match
        j=m.end(); 
        # primary ctor
        k=j
        while k<len(s) and s[k].isspace(): k+=1
        params=None
        if k<len(s) and s[k]=='(':
            e=match_close(s,k,'(',')')
            params={re.split(r'[:=]',p.strip().split()[-1] if False else p)[0].split()[-1] for p in split_top(s[k+1:e]) if p.strip()}
            decl_params.setdefault(name,[]).append(params); k=e+1
        b=s.find('{',k)
        nxt=re.search(r'[;\n]\s*(?:fun|val|var|class|object)\b',s[k:b if b>0 else k+1]) if b>0 else None
        members=set()
        if b>0 and (not re.match(r'[^{]*\n\s*\n',s[k:b]) or True):
            # ensure the brace belongs to this decl: only whitespace/supertypes between k and b
            between=s[k:b]
            if '\n\n' not in between and ('fun ' not in between) and ('val ' not in between) and (';' not in between):
                e=match_close(s,b,'{','}')
                body=s[b:e] if e>0 else ''
                for mm in re.finditer(r'\b(?:fun|val|var|object|class|interface)\s+(?:<[^>]*>\s*)?(?:[\w.]+\.)?(\w+)',body): members.add(mm.group(1))
                if kind=='class' and 'enum' in s[m.start():j]:
                    first=body.split(';')[0]
                    for mm in re.finditer(r'\b([A-Z][A-Z0-9_]+)\b',first): members.add(mm.group(1))
        decl_members.setdefault(name,set()).update(members); kinds[name]=kind
    for m in re.finditer(r'\bfun\s+(?:<[^>]*>\s*)?(?:[\w.]+\.)?(\w+)\s*\(',s):
        k=m.end()-1; e=match_close(s,k,'(',')')
        ps={re.split(r'[:=]',p)[0].replace('vararg','').replace('noinline','').replace('crossinline','').split()[-1] for p in split_top(s[k+1:e]) if p.strip()}
        decl_params.setdefault(m.group(1),[]).append(ps)
projnames={n for n in decl_members if kinds.get(n) in ('object',) or decl_members[n]}
errors=[]
objs={n for n,k in kinds.items() if k=='object'}
# ---- 1) imports of com.example.* resolve
declared_top=set(kinds)|{m.group(1) for f in files if '/src/main/' in f for m in re.finditer(r'^(?:private\s+|internal\s+)?(?:fun|val|const val)\s+(\w+)',clean[f],re.M)}
for f in files:
    for m in re.finditer(r'^import\s+com\.example\.([\w.]+)',clean[f],re.M):
        last=m.group(1).split('.')[-1]
        if last not in declared_top and last!='*' and last not in ('R','BuildConfig') and not m.group(1).startswith('ui.theme'):
            errors.append(f"{f}: import com.example.{m.group(1)} not found in main sources")
# ---- 2) Object.member usage
for f in files:
    s=clean[f]
    for m in re.finditer(r'\b([A-Z]\w+)\.(\w+)',s):
        o,mem=m.group(1),m.group(2)
        if o in objs or (o in decl_members and decl_members[o]):
            if o in objs or kinds.get(o)=='class':
                if mem not in decl_members[o] and mem not in ('Companion','copy','toString','hashCode','equals','values','valueOf','entries','name','ordinal','javaClass'):
                    # nested access chain like Obj.Nested.X handled by member set; skip if member is Capitalized nested or unknown kotlin prop
                    errors.append(f"{f}: {o}.{mem} -> no member '{mem}' declared on {o}")
# ---- 3) named args at call sites of project functions / constructors
calls=0
for f in files:
    s=clean[f]
    for name,plist in decl_params.items():
        for m in re.finditer(r'(?<![\w.])'+re.escape(name)+r'\s*\(',s):
            # skip declarations
            pre=s[max(0,m.start()-12):m.start()]
            if re.search(r'\b(fun|class|object|interface)\s+(<[^>]*>\s*)?$',pre) or re.search(r'fun\s+[\w.<>]*\.$',pre): continue
            k=m.end()-1; e=match_close(s,k,'(',')')
            if e<0: continue
            named=[re.match(r'\s*(\w+)\s*=(?!=)',p) for p in split_top(s[k+1:e])]
            named=[x.group(1) for x in named if x]
            if not named: continue
            calls+=1
            if not any(set(named)<=ps for ps in plist if ps is not None):
                errors.append(f"{f}: call {name}(...) uses named args {named} but declared params are {[sorted(p) for p in plist if p is not None]}")
    # Obj.func(named=...) calls
    for m in re.finditer(r'\b([A-Z]\w+)\.(\w+)\s*\(',s):
        o,fn=m.groups()
        if o in objs and fn in decl_params:
            k=m.end()-1; e=match_close(s,k,'(',')')
            if e<0: continue
            named=[re.match(r'\s*(\w+)\s*=(?!=)',p) for p in split_top(s[k+1:e])]
            named=[x.group(1) for x in named if x]
            if named:
                calls+=1
                if not any(set(named)<=ps for ps in decl_params[fn]):
                    errors.append(f"{f}: {o}.{fn} named args {named} vs declared {[sorted(p) for p in decl_params[fn]]}")
print(f"project types/objects indexed: {len(kinds)}; objects: {len(objs)}; named-arg call sites checked: {calls}")
for e in sorted(set(errors)): print("  ISSUE:",e)
print("ISSUES:",len(set(errors)))
