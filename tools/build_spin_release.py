"""Rebuild the two changed components; preserve the verified 1.1.9 release elsewhere."""
import hashlib, json, os, re, shutil, subprocess, tomllib, zipfile
from pathlib import Path
from cloud_runtime_smoke import fetch_json, download

assert os.environ.get('GITHUB_ACTIONS') == 'true', 'Heavy builds run on the hosted runner for this release'
root = Path.cwd()
server = Path(os.environ.get('UNYIELDING_SERVER_SOURCE', '../server-jars')).resolve()
out = root/'dist/settings-release'
out.mkdir(parents=True, exist_ok=True)
companion = root/'companion'
quests = server/'unyielding-quests'
versions = {'cobblemon':'xsVHrAsY', 'kotlin':'uhJhCT7X', 'emi':'5sIPA1To', 'xaero':'Q1tuMQBB'}
for project in [companion, quests]: (project/'libs').mkdir(exist_ok=True)
for label, version in versions.items():
    data = fetch_json('https://api.modrinth.com/v2/version/'+version)
    item = next((f for f in data['files'] if f.get('primary')), data['files'][0])
    target = companion/'libs'/item['filename']
    download(item['url'], target, item['hashes']['sha512'])
    if label in ('cobblemon','emi'): shutil.copy2(target, quests/'libs'/f'{label}.jar')
    if label in ('kotlin','xaero'):
        with zipfile.ZipFile(target) as z:
            for n in z.namelist():
                if not n.endswith('.jar'): continue
                if label == 'kotlin' and 'kotlin-stdlib-' in n and 'jdk' not in n:
                    (quests/'libs/kotlin-stdlib.jar').write_bytes(z.read(n))
                if label == 'xaero':
                    (companion/'compile-libs').mkdir(exist_ok=True)
                    (companion/'compile-libs'/Path(n).name).write_bytes(z.read(n))
for name, project in [('quests',quests),('companion',companion)]:
    with (out/f'{name}-build.log').open('w') as log:
        result = subprocess.run(['gradle','--no-daemon','build','--parallel','--max-workers=12','--build-cache'], cwd=project, stdout=log, stderr=subprocess.STDOUT)
    if result.returncode:
        print((out/f'{name}-build.log').read_text()[-18000:]); raise SystemExit(result.returncode)

def read(path):
    with zipfile.ZipFile(path) as z:
        assert z.testzip() is None
        assert len(z.namelist()) == len(set(z.namelist()))
        return {n:z.read(n) for n in z.namelist() if not n.endswith('/')}

base_path = server/'releases/Unyielding-1.1.8-test.jar'
sha = lambda data: hashlib.sha256(data).hexdigest()
assert sha(base_path.read_bytes()) == 'c76304bb8db8bd6367fe0c4a7e4354d83a8a0e90c64e25bcbd42dbbbd8458b43'
base = read(base_path)
quest = read(quests/'build/libs/UnyieldingQuests-1.6.9.jar')
module = read(companion/'build/libs/mawonna-companion-1.0.2.jar')
quest_prefixes = ('dev/unyielding/quests/','assets/unyieldingquests/','data/unyieldingquests/','defaultquests/')
meta = 'META-INF/neoforge.mods.toml'; manifest = 'META-INF/MANIFEST.MF'
entries = {n:v for n,v in base.items() if not n.startswith(quest_prefixes+('dev/mawonna/',))}
allowed = {n for n in base if n.startswith(quest_prefixes+('dev/mawonna/',))} | {meta,manifest}
for n,v in quest.items():
    if n in (meta,manifest): continue
    assert n.startswith(quest_prefixes), 'Unexpected quest entry '+n
    assert '/devtest/' not in n
    entries[n]=v; allowed.add(n)
for n,v in module.items():
    if n in (meta,manifest,'pack.mcmeta'): continue
    if not n.startswith(('assets/','data/','LICENSES/','dev/mawonna/','mawonna/','mawonna.','mawonna-')): continue
    if '/lang/' in n and n in base:
        merged = json.loads(v) | json.loads(base[n])
        if n == 'assets/mawonna/lang/en_us.json':
            merged['mawonna.ui.verified_dark_defeats'] = json.loads(v)['mawonna.ui.verified_dark_defeats']
        v = (json.dumps(merged, indent=2, ensure_ascii=False)+'\n').encode()
    entries[n]=v;allowed.add(n)
text=base[meta].decode()
for mod,version in [('mawonna','1.0.2'),('unyieldingquests','1.6.9')]:
    blocks=list(re.finditer(r'\[\[mods\]\].*?(?=\n\[\[|\Z)',text,re.S))
    block=next(b for b in blocks if re.search(r'modId\s*=\s*"'+mod+'"',b.group()))
    replaced,count=re.subn(r'(version\s*=\s*")[^"]+("\s*)',lambda m:m[1]+version+m[2],block.group(),count=1)
    assert count==1
    text=text[:block.start()]+replaced+text[block.end():]
entries[meta]=text.encode()
entries[manifest]=re.sub(rb'Implementation-Version: [^\r\n]+',b'Implementation-Version: 1.1.9',base[manifest])
entries[manifest]=re.sub(rb'Unyielding-Quests-Version: [^\r\n]+',b'Unyielding-Quests-Version: 1.6.9',entries[manifest])
oldmeta=tomllib.loads(base[meta].decode()); newmeta=tomllib.loads(text)
for m in oldmeta['mods']:
    if m['modId']=='mawonna':m['version']='1.0.2'
    if m['modId']=='unyieldingquests':m['version']='1.6.9'
assert oldmeta==newmeta
changed={n for n in base if entries.get(n)!=base[n]}
assert changed<=allowed
# Models, gameplay data, existing portraits and native Inkyeon walk cannot regress.
for n,v in base.items():
    if n.startswith('data/cobblemon/') or '/bedrock/' in n or '/animated_icons/mawonna/portrait_' in n or n.endswith('/inkyeon/portrait_static.png'):
        assert entries[n]==v,n
for n,v in entries.items():
    if n.endswith('.json'):json.loads(v)
dest=out/'Unyielding-1.1.9.jar'
with zipfile.ZipFile(dest,'w',zipfile.ZIP_DEFLATED) as z:
    for n,v in sorted(entries.items()):z.writestr(n,v)
assert read(dest)==entries
report={'status':'passed','version':'1.1.9','sha256':sha(dest.read_bytes()),'baseline_sha256':sha(base_path.read_bytes()),'mod_ids':[m['modId'] for m in newmeta['mods']], 'changed_entries':sorted(changed),'added_entries':sorted(set(entries)-set(base)),'preserved_entries':len(base)-len(changed),'portrait_model_species_walk_preserved':True}
(out/'assembly-report.json').write_text(json.dumps(report,indent=2)+'\n')
(out/'SHA256SUMS').write_text(report['sha256']+'  '+dest.name+'\n')
(root/'unyielding-artifact').mkdir(exist_ok=True)
shutil.copy2(dest,root/'unyielding-artifact/Unyielding-Mawonna.jar')
print(json.dumps({k:v for k,v in report.items() if not k.endswith('entries')},indent=2))
