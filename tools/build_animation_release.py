"""Build locally or on explicitly requested hosted CI; preserve other 1.1.8 modules."""
import os, json, hashlib, re, subprocess, zipfile, tomllib, shutil
from pathlib import Path
from cloud_runtime_smoke import VERSIONS, fetch_json, download

assert os.environ.get('UNYIELDING_LOCAL_BUILD')=='true' or (os.environ.get('GITHUB_ACTIONS')=='true' and os.environ.get('RUNNER_ENVIRONMENT')=='github-hosted')
root=Path.cwd(); out=root/'dist/animation-release';out.mkdir(parents=True,exist_ok=True)
project=root/'companion';libs=project/'libs';libs.mkdir(exist_ok=True)
versions=VERSIONS | {'Xaero Minimap 26.5.0':'Q1tuMQBB'}
for label,version in versions.items():
    info=fetch_json('https://api.modrinth.com/v2/version/'+version)
    item=next((x for x in info['files'] if x.get('primary')),info['files'][0])
    target=libs/item['filename']
    cached=Path(os.environ.get('UNYIELDING_LOCAL_MODS','__missing__'))/item['filename']
    if cached.is_file() and hashlib.sha512(cached.read_bytes()).hexdigest()==item['hashes']['sha512']:shutil.copy2(cached,target)
    elif target.is_file() and hashlib.sha512(target.read_bytes()).hexdigest()==item['hashes']['sha512']:pass
    else:download(item['url'],target,item['hashes']['sha512'])
    if label.startswith('Xaero'):
        with zipfile.ZipFile(target) as z:
            for n in z.namelist():
                if n.endswith('.jar'):
                    nested=project/'compile-libs';nested.mkdir(exist_ok=True)
                    (nested/Path(n).name).write_bytes(z.read(n))
with (out/'companion-build.log').open('w') as log:
    result=subprocess.run([os.environ.get('UNYIELDING_GRADLE','gradle'),'--no-daemon','build','--parallel','--max-workers=12','--build-cache'],cwd=project,stdout=log,stderr=subprocess.STDOUT)
if result.returncode:
    print((out/'companion-build.log').read_text()[-18000:]);raise SystemExit(result.returncode)
base=out/'Unyielding-1.1.8.jar'
download('https://raw.githubusercontent.com/WeepingAngeIs/Server-Jars/acb997103be64316cbc0897f158be1f5e8da8f1d/releases/Unyielding-1.1.8.jar',base)
digest=lambda b:hashlib.sha256(b).hexdigest()
assert digest(base.read_bytes())=='d3d560525f810a340fe2c634a1bc3f4c7a14883dadba1926b1d7c29478bcbbcc'
def read(p):
    with zipfile.ZipFile(p) as z:
        assert z.testzip() is None
        assert len(z.namelist())==len(set(z.namelist()))
        return {n:z.read(n) for n in z.namelist() if not n.endswith('/')}
before=read(base);pack=read(root/'Mawonna.zip');module=read(project/'build/libs/mawonna-companion-1.0.1.jar')
entries={n:v for n,v in before.items() if not n.startswith('dev/mawonna/')}
allowed=set(n for n in before if n.startswith('dev/mawonna/'))
for source in [pack,module]:
    for n,value in source.items():
        if n in ['META-INF/MANIFEST.MF','META-INF/neoforge.mods.toml','pack.mcmeta'] or not n.startswith(('assets/','data/','LICENSES/','dev/mawonna/','mawonna/','mawonna.','mawonna-')):continue
        # The current pack owns requested assets. Companion language also carries
        # ring/evolution strings that must not replace the freshly renamed species.
        if '/lang/' in n and n in entries:
            incoming=json.loads(value);existing=json.loads(entries[n])
            value=(json.dumps((existing|incoming) if source is pack else (incoming|existing),indent=2,ensure_ascii=False)+'\n').encode()
        if n.endswith('.geo.json') and n in before:
            incoming=json.loads(value);old_geometry=json.loads(before[n])
            old_bones={b['name']:b for b in old_geometry['minecraft:geometry'][0]['bones']}
            for bone in incoming['minecraft:geometry'][0]['bones']:
                seat=old_bones.get(bone['name'],{}).get('locators',{}).get('middle')
                if seat is not None and 'middle' not in bone.get('locators',{}):
                    # Jirachi now uses native coordinates instead of the old 16x import.
                    if 'ghost_jirachi' in n:seat=[v/16 for v in seat]
                    bone.setdefault('locators',{})['middle']=seat
            value=(json.dumps(incoming,indent=2)+'\n').encode()
        entries[n]=value;allowed.add(n)
meta='META-INF/neoforge.mods.toml';current=before[meta].decode();new=module[meta].decode()
current=re.sub(r'(modId\s*=\s*"mawonna"\s*\nversion\s*=\s*")[^"]+',r'\g<1>1.0.1',current)
new_toml=tomllib.loads(new)
for mixin in new_toml.get('mixins',[]):
    config=mixin['config']
    if config not in current:current+='\n[[mixins]]\nconfig="'+config+'"\n'
entries[meta]=current.encode();allowed.add(meta)
manifest='META-INF/MANIFEST.MF';entries[manifest]=re.sub(rb'(?m)^Implementation-Version: [^\r\n]+',b'Implementation-Version: 1.1.9',before[manifest]);allowed.add(manifest)
changed={n for n in before if entries.get(n)!=before[n]};assert changed<=allowed,changed-allowed
oldmods=tomllib.loads(before[meta].decode())['mods'];newmods=tomllib.loads(current)['mods'];assert len(oldmods)==len(newmods)==9
for a,b in zip(oldmods,newmods):
    assert a['modId']==b['modId']
    if a['modId']!='mawonna':assert a==b
for n,v in before.items():
    if n not in allowed:assert entries[n]==v
for n in entries:
    if n.endswith('.json'):json.loads(entries[n])
flower=json.loads(entries['data/cobblemon/species/custom/mawonna_flower.json']);assert len(flower['moves'])==862 and all(m.startswith('1:') for m in flower['moves'])
sliggoo=json.loads(entries['data/cobblemon/species/custom/fairy_sliggoo.json']);assert (sliggoo['primaryType'],sliggoo['secondaryType'])==('dragon','fairy')
lang=json.loads(entries['assets/mawonna/lang/en_us.json']);assert lang['cobblemon.species.fairysliggoo.name']=='Frilligoo'
dest=out/'Unyielding-1.1.9.jar'
with zipfile.ZipFile(dest,'w',zipfile.ZIP_DEFLATED) as z:
    for n,v in sorted(entries.items()):z.writestr(n,v)
assert read(dest)==entries
report={'status':'passed','version':'1.1.9','sha256':digest(dest.read_bytes()),'baseline_sha256':digest(base.read_bytes()),'pack_sha256':digest((root/'Mawonna.zip').read_bytes()),'changed_entries':sorted(changed),'added_entries':sorted(set(entries)-set(before)),'unrelated_entries_preserved':len(before)-len(changed),'mod_ids':[m['modId'] for m in newmods],'admin_level_one_moves':862}
(out/'assembly-report.json').write_text(json.dumps(report,indent=2)+'\n')
(out/'SHA256SUMS').write_text(report['sha256']+'  '+dest.name+'\n')
artifact=root/'unyielding-artifact';artifact.mkdir(exist_ok=True);shutil.copy2(dest,artifact/'Unyielding-Mawonna.jar')
print(json.dumps({k:v for k,v in report.items() if k not in ['changed_entries','added_entries']},indent=2))
