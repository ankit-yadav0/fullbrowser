import re, glob, os, sys
import xml.etree.ElementTree as ET
root='.'
fails=[]
def fail(msg): fails.append(msg); print("  FAIL:",msg)
ANDROID='{http://schemas.android.com/apk/res/android}'

print("[1] Resource references in manifest + res XML resolve to defined resources")
defined=set()
for f in glob.glob('app/src/main/res/**/*',recursive=True):
    if os.path.isdir(f): continue
    parts=f.split(os.sep); folder=parts[-2]; name=os.path.splitext(parts[-1])[0]
    rtype=folder.split('-')[0]
    if rtype in ('drawable','mipmap','xml','layout','anim','raw','font','color'): defined.add((rtype,name))
    if rtype=='values':
        try:
            t=ET.parse(f).getroot()
            for e in t:
                n=e.get('name')
                tag=e.tag
                if n: defined.add(({'string':'string','color':'color','style':'style','dimen':'dimen','integer':'integer','bool':'bool','array':'array','string-array':'array','plurals':'plurals'}.get(tag,tag),n.replace('.','_')))
        except Exception as ex: fail(f"cannot parse {f}: {ex}")
refs=[]
for f in ['app/src/main/AndroidManifest.xml']+glob.glob('app/src/main/res/**/*.xml',recursive=True):
    txt=open(f,encoding='utf8').read()
    for m in re.finditer(r'@(?:(?:android):)?(\w+)/([\w.]+)',txt):
        refs.append((f,m.group(0),m.group(1),m.group(2)))
unres=0
for f,full,t,n in refs:
    if full.startswith('@android:') or full.startswith('@android/'): continue
    if (t,n.replace('.','_')) not in defined:
        # parent style refs like @style/Theme... may point to library styles
        fail(f"{f}: unresolved {full}"); unres+=1
print(f"  checked {len(refs)} references; unresolved={unres}")
print("  defined resource count:",len(defined))

print("[2] Kotlin R.* references")
rrefs=set()
for f in glob.glob('app/src/**/*.kt',recursive=True):
    for m in re.finditer(r'(?<!android\.)\bR\.(\w+)\.(\w+)',open(f,encoding='utf8').read()): rrefs.add((f,m.group(1),m.group(2)))
bad=[(f,t,n) for f,t,n in rrefs if (t,n) not in defined]
print(f"  {len(rrefs)} R.* refs in Kotlin; unresolved={len(bad)}",bad[:5])
for b in bad: fail(f"R ref unresolved {b}")

print("[3] Version-catalog aliases used by build files exist in libs.versions.toml")
toml=open('gradle/libs.versions.toml',encoding='utf8').read()
def alias_set(section):
    m=re.search(r'\['+section+r'\](.*?)(?=\n\[|\Z)',toml,re.S)
    body=m.group(1) if m else ''
    return {l.split('=')[0].strip().replace('-','.').replace('_','.') for l in body.splitlines() if '=' in l and not l.strip().startswith('#')}
libs=alias_set('libraries'); plugins=alias_set('plugins')
used=set(); usedp=set()
for f in ['build.gradle.kts','app/build.gradle.kts','settings.gradle.kts']:
    txt=re.sub(r'//.*','',open(f,encoding='utf8').read())
    for m in re.finditer(r'libs\.plugins\.([\w.]+)',txt): usedp.add(m.group(1))
    for m in re.finditer(r'libs\.(?!plugins\.|versions\.)([\w.]+)',txt): used.add(m.group(1))
miss=[u for u in used if u not in libs]
missp=[u for u in usedp if u not in plugins]
print(f"  libs used={len(used)} missing={miss}; plugins used={len(usedp)} missing={missp}")
for u in miss: fail("catalog lib alias missing: "+u)
for u in missp: fail("catalog plugin alias missing: "+u)

print("[4] Kotlin package declarations match directory layout")
badpk=0
for f in glob.glob('app/src/**/*.kt',recursive=True):
    m=re.search(r'^package\s+([\w.]+)',open(f,encoding='utf8').read(),re.M)
    d=os.path.dirname(f).split('java'+os.sep,1)[-1].replace(os.sep,'.')
    if not m or m.group(1)!=d: fail(f"package mismatch {f}: declared={m.group(1) if m else None} dir={d}"); badpk+=1
print("  mismatches:",badpk)

print("[5] namespace / applicationId / manifest class names")
b=open('app/build.gradle.kts').read()
ns=re.search(r'namespace\s*=\s*"([^"]+)"',b).group(1)
man=ET.parse('app/src/main/AndroidManifest.xml').getroot()
for tag in ('activity','service'):
    for e in man.iter(tag):
        n=e.get(ANDROID+'name'); fq=ns+n if n.startswith('.') else n
        path='app/src/main/java/'+fq.replace('.','/')+'.kt'
        print(f"  {tag} {n} -> {fq} file_exists={os.path.exists(path)}")
        if not os.path.exists(path): fail(f"manifest {tag} {n} has no source file")

print("[6] Manifest invariants (real file)")
app=man.find('application')
def a(e,k): return e.get(ANDROID+k)
checks={'allowBackup=false':a(app,'allowBackup')=='false','usesCleartextTraffic=false':a(app,'usesCleartextTraffic')=='false','not debuggable':a(app,'debuggable') is None}
acts=list(man.iter('activity')); svcs=list(man.iter('service'))
checks['exactly one activity, exported']=(len(acts)==1 and a(acts[0],'exported')=='true')
checks['services not exported']=all(a(s,'exported')=='false' for s in svcs)
checks['no receivers/providers']=not list(man.iter('receiver')) and not list(man.iter('provider'))
perms={a(p,'name') for p in man.iter('uses-permission')}
exp={'android.permission.INTERNET','android.permission.ACCESS_NETWORK_STATE','android.permission.FOREGROUND_SERVICE','android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK','android.permission.POST_NOTIFICATIONS','android.permission.CAMERA','android.permission.RECORD_AUDIO'}
checks['permission set exact']=(perms==exp)
for k,v in checks.items():
    print(f"  {'ok  ' if v else 'FAIL'} {k}")
    if not v: fail("manifest invariant: "+k)
print("[7] Backup rules cover all domains with no <include>")
need={'root','file','database','sharedpref','external','cache'}
for f,secs in (('app/src/main/res/xml/backup_rules.xml',[None]),('app/src/main/res/xml/data_extraction_rules.xml',['cloud-backup','device-transfer'])):
    r=ET.parse(f).getroot()
    for s in secs:
        node=r if s is None else r.find(s)
        ex={e.get('domain') for e in node if e.tag=='exclude' and e.get('path')=='.'}
        inc=[e for e in node if e.tag=='include']
        ok=need<=ex and not inc
        print(f"  {'ok  ' if ok else 'FAIL'} {os.path.basename(f)}[{s or 'root'}] excludes={sorted(ex)} includes={len(inc)}")
        if not ok: fail(f"backup rules {f} {s}")
print()
print("TOTAL FAILURES:",len(fails))
sys.exit(1 if fails else 0)
