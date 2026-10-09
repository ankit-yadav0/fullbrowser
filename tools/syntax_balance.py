import sys,glob
def scan(src):
    i=0;n=len(src);stack=[];errs=[]
    pairs={')':'(',']':'[','}':'{'}
    line=1
    def tmpl(i):
        # inside "${ ... }" : skip balanced braces, nested strings handled recursively
        depth=1
        while i<n and depth:
            c=src[i]
            if c=='"': i=skip_str(i+1)
            elif c=='{': depth+=1;i+=1
            elif c=='}': depth-=1;i+=1
            else: i+=1
        return i
    def skip_str(i):
        while i<n:
            c=src[i]
            if c=='\\': i+=2;continue
            if c=='"': return i+1
            if c=='$' and i+1<n and src[i+1]=='{': i=tmpl(i+2);continue
            i+=1
        return i
    def skip_raw(i):
        while i<n:
            if src.startswith('"""',i):
                j=i+3
                while j<n and src[j]=='"': j+=1
                return j
            if src[i]=='$' and i+1<n and src[i+1]=='{': i=tmpl(i+2);continue
            i+=1
        return i
    while i<n:
        c=src[i]
        if c=='\n': line+=1
        if src.startswith('//',i):
            while i<n and src[i]!='\n': i+=1
            continue
        if src.startswith('/*',i):
            d=1;i+=2
            while i<n and d:
                if src.startswith('/*',i): d+=1;i+=2
                elif src.startswith('*/',i): d-=1;i+=2
                else:
                    if src[i]=='\n': line+=1
                    i+=1
            continue
        if src.startswith('"""',i): 
            s=i; i=skip_raw(i+3); line+=src[s:i].count('\n'); continue
        if c=='"': i=skip_str(i+1);continue
        if c=="'":
            if src[i+1]=='\\': i+=4 if src[i+2]!='u' else 8
            else: i+=3
            continue
        if c in '([{': stack.append((c,line))
        elif c in ')]}':
            if not stack or stack[-1][0]!=pairs[c]: errs.append(f"line {line}: unexpected {c}"); 
            else: stack.pop()
        i+=1
    for c,l in stack: errs.append(f"line {l}: unclosed {c}")
    return errs
bad=0
for f in sorted(glob.glob("app/src/**/*.kt",recursive=True)):
    e=scan(open(f).read())
    if e: bad+=1; print(f, e[:5])
print("files with problems:",bad)
