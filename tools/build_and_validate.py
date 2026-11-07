"""Build the complete pack only on GitHub-hosted runners."""
import os, json, zipfile, hashlib, re, io
from pathlib import Path
from PIL import Image
if os.environ.get('GITHUB_ACTIONS') != 'true' or os.environ.get('RUNNER_ENVIRONMENT') != 'github-hosted':
 raise SystemExit('All pack builds and tests must run on GitHub-hosted runners')
r=Path(__file__).resolve().parents[1]; out=r/'dist'; out.mkdir(exist_ok=True)
with zipfile.ZipFile(r/'Mawonna.zip') as z:
 assert z.testzip() is None
 files={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
for p in (r/'pack-additions').rglob('*'):
 if p.is_file():files[p.relative_to(r/'pack-additions').as_posix()]=p.read_bytes()
allowed={'assets/mawonna/lang/en_us.json','assets/cobblemon/sounds.json','data/mawonna/dexes/custom.json','pack.mcmeta'}
manifest=json.loads((r/'source/preserved-pack-manifest.json').read_text())
for name,sha in manifest.items():
 if name not in allowed:assert hashlib.sha256(files[name]).hexdigest()==sha, 'Existing pack changed: '+name
for name in allowed-{'pack.mcmeta'}:
 with zipfile.ZipFile(r/'Mawonna.zip') as z: before=json.loads(z.read(name))
 after=json.loads(files[name])
 for key,value in before.items():
  if isinstance(value,list): assert all(item in after[key] for item in value),(name,key)
  else: assert after[key]==value,(name,key)
for name,data in files.items():
 if name.endswith('.json'):json.loads(data)
base=r/'source/Mawonna-v17-base.zip'
assert hashlib.sha256(base.read_bytes()).hexdigest()=='ad24b99301a8741d2370bde79b35ac69c4a5f53b7e7768d064e4b80fad2141b9'
gp='assets/mawile_custom/bedrock/pokemon/models/mawile_third_evolution/mawile_third_evolution.geo.json'
with zipfile.ZipFile(base) as z: assert files[gp]==z.read(gp), 'Approved Mawonna v17 changed'
get=lambda n:json.loads(files[n])
s=get('data/cobblemon/species/custom/fairy_sliggoo.json')
assert s['baseStats']==dict(hp=200,attack=10,defence=120,special_attack=120,special_defence=110,speed=15)
assert sum(s['baseStats'].values())==575
assert (s['primaryType'],s['secondaryType'])==('fairy','dragon') and s['implemented']
assert s['behaviour']['moving']['fly']['canFly'] and not s['evolutions']
evo=get('data/mawonna/species_additions/sliggoo_evolution.json')
assert evo['target']=='cobblemon:sliggoo'
assert evo['evolutions'][0]['result']=='fairy_sliggoo'
assert evo['evolutions'][0]['requirements']==[dict(variant='defeat',target='type=dark',amount=1000)]
geo=get('assets/mawonna/bedrock/pokemon/models/fairy_sliggoo/fairy_sliggoo.geo.json')['minecraft:geometry'][0]
bones={b['name']:b for b in geo['bones']};assert len(bones)==len(geo['bones'])
wings={n for n in bones if n.startswith('cupid_wing_')};assert len(wings)==4
for b in bones.values():
 assert not b.get('parent') or b['parent'] in bones
 for c in b.get('cubes',[]):
  assert isinstance(c['uv'],list) and len(c['uv'])==2 and all(isinstance(v,int) for v in c['uv'])
  assert all(v>=0 for v in c['size'])
  assert all(0<=v<lim for v,lim in zip(c['uv'],[geo['description']['texture_width'],geo['description']['texture_height']]))
with Image.open(io.BytesIO(files['assets/mawonna/textures/pokemon/fairy_sliggoo/fairy_sliggoo.png'])) as im:
 assert im.size==(2048,1024);im.verify()
clips=get('assets/mawonna/bedrock/pokemon/animations/fairy_sliggoo/fairy_sliggoo.animation.json')['animations']
assert len(clips)==17
native=json.loads((r/'source/fairy-sliggoo/original-animation.json').read_text())['animations']
assert len(native)==15
for k,v in native.items():assert clips[k.replace('animation.sliggoo.','animation.fairy_sliggoo.')]==v,k
for k,v in clips.items():assert set(v.get('bones',{})).issubset(bones),(k,set(v.get('bones',{}))-bones.keys())
for motion in ['air_idle','air_fly']:assert wings.issubset(clips['animation.fairy_sliggoo.'+motion]['bones'])
poser=get('assets/mawonna/bedrock/pokemon/posers/fairy_sliggoo.json')
for name in re.findall(r"q.bedrock(?:_stateful|_quirk)?\('fairy_sliggoo', '([^']+)'",json.dumps(poser)):
 assert 'animation.fairy_sliggoo.'+name in clips,name
assert poser['rootBone'] in bones
for sid in ['mawonna','mawonna_flower','mawileunyielding','covislight','fairy_sliggoo']:
 assert get('data/cobblemon/species/custom/'+sid+'.json')['implemented']
bird=get('assets/mawonna/bedrock/pokemon/posers/pink_corvisquire.json')
assert all(p['transformTicks']==0 for p in bird['poses'].values())
meta=get('pack.mcmeta');meta['pack']['description']='Mawonna v17 + Corvislight + Mawile Unyielding + Fairy Sliggoo'
files['pack.mcmeta']=(json.dumps(meta,indent=2)+'\n').encode()
install=out/'installable'
with zipfile.ZipFile(out/'Mawonna.zip','w',zipfile.ZIP_DEFLATED) as z:
 for name,data in sorted(files.items()):
  info=zipfile.ZipInfo(name,(2026,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,data)
  p=install/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
report={'status':'passed','mawonna_version':17,'existing_entries_preserved':len(manifest)-len(allowed),'sliggoo_animations':len(clips),'sliggoo_wings':len(wings),'sliggoo_bst':575,'sha256':hashlib.sha256((out/'Mawonna.zip').read_bytes()).hexdigest()}
(out/'validation.json').write_text(json.dumps(report,indent=2));print(json.dumps(report))
