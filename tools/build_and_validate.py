import json, zipfile, struct, re, gzip, hashlib
from pathlib import Path
from PIL import Image
root=Path(__file__).resolve().parents[1]
for packed in (root/'source/pink-corvisquire').glob('*.gz'):
 packed.with_suffix('').write_bytes(gzip.decompress(packed.read_bytes()))
add=root/'pack-additions';out=root/'dist';out.mkdir(exist_ok=True)
files={p.relative_to(add).as_posix():p.read_bytes() for p in add.rglob('*') if p.is_file()}
for path,data in files.items():
 if path.endswith('.json'):json.loads(data)
n='pink_corvisquire';prefix='assets/mawonna/'
g=json.loads(files[prefix+f'bedrock/pokemon/models/{n}/{n}.geo.json'])['minecraft:geometry'][0]
bones={b['name']:b for b in g['bones']};assert len(bones)==len(g['bones'])
for b in bones.values():
 assert not b.get('parent') or b['parent'] in bones
 seen=set();cur=b
 while cur.get('parent'):
  assert cur['name'] not in seen;seen.add(cur['name']);cur=bones[cur['parent']]
 for cube in b.get('cubes',[]):
  assert all(v>=0 for v in cube['size'])
  uv=cube.get('uv')
  if isinstance(uv,list):assert all(0<=v<512 for v in uv)
  elif isinstance(uv,dict):
   for f in uv.values():assert all(0<=v<=512 for v in f['uv'])
a=json.loads(files[prefix+f'bedrock/pokemon/animations/{n}/{n}.animation.json'])['animations']
assert len(a)==12
for name,clip in a.items():
 assert clip.get('animation_length',1)>0
 for bone in clip.get('bones',{}):
  assert bone in bones,(name,bone)
  assert not bone.startswith('pupil'),(name,'pupil must remain still')
for name in ['air_fly','air_idle']:
 assert a['animation.'+n+'.'+name]['bones']['wings_closed']['scale']==[0,0,0]
 assert a['animation.'+n+'.'+name]['bones']['wings_open']['scale']==[1,1,1]
assert all(bones[b]['cubes'] for b in bones if b.startswith('wing_open_'))
tex=add/prefix/f'textures/pokemon/{n}/{n}.png'
with Image.open(tex) as im:assert im.size==(g['description']['texture_width'],g['description']['texture_height']);im.verify()
poser=json.loads(files[prefix+f'bedrock/pokemon/posers/{n}.json']);assert poser['rootBone'] in bones
for expr in re.findall(r"q\.bedrock(?:_stateful|_quirk)?\('pink_corvisquire', '([^']+)'",json.dumps(poser)):
 assert 'animation.'+n+'.'+expr in a,expr
resolver=json.loads(files[prefix+f'bedrock/pokemon/resolvers/{n}.json']);assert resolver['species']=='cobblemon:covislight'
species=json.loads(files['data/cobblemon/species/custom/covislight.json']);assert species['maleRatio']==0 and species['implemented']
expected_stats={'hp':110,'attack':10,'defence':80,'special_attack':120,'special_defence':150,'speed':125}
assert species['name']=='Corvislight'
assert species['baseStats']==expected_stats
assert sum(species['baseStats'].values())==595
catalog=json.loads((root/'source/pink-corvisquire/level-one-moves.json').read_text())
required={'1:'+m['id'] for m in catalog}
assert len(required)==68 and required.issubset(set(species['moves']))
assert len(species['moves'])==len(set(species['moves']))
assert {'1:steelwing','1:esperwing','1:dualwingbeat','1:oblivionwing','1:wingattack'}.issubset(required)
player=json.loads(files['data/cobblemon/species/custom/mawonna.json'])
admin=json.loads(files['data/cobblemon/species/custom/mawonna_flower.json'])
assert player['baseStats']==dict(hp=100,attack=120,defence=120,special_attack=10,special_defence=100,speed=10)
assert sum(player['baseStats'].values())==460
assert admin['baseStats']==dict(hp=155,attack=165,defence=200,special_attack=5,special_defence=150,speed=30)
assert player['abilities']==['futakuchionnaplayer'] and 'futakuchionna' in admin['abilities']
for base,result in [('corvisquire','covislight'),('mawile','mawonna')]:
 addition=json.loads(files[f'data/mawonna/species_additions/{base}_evolution.json'])
 evo=addition['evolutions'][0]
 assert addition['target']=='cobblemon:'+base and evo['result'].split()[0]==result
 req=evo['requirements'][0]
 assert req=={'variant':'defeat','target':'type=dark','amount':1000}
 for count in [0,999,1000,1001]:assert (count>=req['amount'])==(count>=1000)
 assert 'mawonna_flower' not in evo['result']
 if base=='corvisquire':assert {'variant':'properties','target':'gender=female'} in evo['requirements']
lang=json.loads(files['assets/mawonna/lang/en_us.json'])
for sid in ['covislight','mawonna','mawonna_flower']:
 entry=json.loads(files[f'data/cobblemon/dex_entries/pokemon/custom/{sid}.json'])
 assert entry['speciesId']=='cobblemon:'+sid
 assert 'cobblemon.species.'+sid+'.desc' in lang
assert '1,000' in lang['cobblemon.species.covislight.desc']
assert '1,000' in lang['cobblemon.species.mawonna.desc']
base_zip=root/'Mawonna.zip'
assert hashlib.sha256(base_zip.read_bytes()).hexdigest()=='ad24b99301a8741d2370bde79b35ac69c4a5f53b7e7768d064e4b80fad2141b9', 'Wrong Mawonna base: must be the user-supplied v17 Floral Kimono'
with zipfile.ZipFile(base_zip) as original:
 assert original.testzip() is None
 original_entries={i.filename:original.read(i) for i in original.infolist() if not i.is_dir()}
 removals=set(json.loads((root/'pack-removals.json').read_text()))
 assert removals=={'data/mawile_custom/species/custom/mawile_third_evolution.json','assets/mawile_custom/bedrock/pokemon/resolvers/mawile_third_evolution.json'}
 assert removals.issubset(original_entries)
 old={p:data for p,data in original_entries.items() if p not in removals}
 assert 'pack.mcmeta' in old
 assert not set(old)&set(files),'Unexpected overwrite of existing Mawonna entry'
 # V17 did not include the later cry; preserve that separate repository audio.
 cry='assets/mawile_custom/sounds/pokemon/mawile_third_evolution/mawonna_cry.ogg'
 files[cry]=(root/'source/audio/mawonna_cry.ogg').read_bytes()
 geo_path='assets/mawile_custom/bedrock/pokemon/models/mawile_third_evolution/mawile_third_evolution.geo.json'
 maw_geo=json.loads(old[geo_path])['minecraft:geometry'][0]
 assert len(maw_geo['bones'])==131
 assert sum(b['name'].startswith('kimono') for b in maw_geo['bones'])==26
 maw_bones={b['name'] for b in maw_geo['bones']}
 maw_anim=json.loads(old['assets/mawile_custom/bedrock/pokemon/animations/mawile_third_evolution/mawile_third_evolution.animation.json'])['animations']
 for clip in maw_anim.values():
  assert set(clip.get('bones',{})).issubset(maw_bones),set(clip.get('bones',{}))-maw_bones
 for sid in ['mawonna','mawonna_flower']:
  res=json.loads(files[f'assets/mawonna/bedrock/pokemon/resolvers/{sid}.json'])
  assert res['species']=='cobblemon:'+sid
  assert res['variations'][0]['model']=='mawile_custom:mawile_third_evolution.geo'
 metadata=json.loads(old['pack.mcmeta'])
 metadata['pack']['description']='Mawonna v17 Floral Kimono + Corvislight | Player and Flower versions'
 old['pack.mcmeta']=json.dumps(metadata,indent=2).encode()

 with zipfile.ZipFile(out/'Mawonna-v17-Corvislight.zip','w',zipfile.ZIP_DEFLATED) as z:
  for path,data in sorted((old|files).items()):z.writestr(path,data)
with zipfile.ZipFile(out/'Mawonna-v17-Corvislight.zip') as z:
 assert z.testzip() is None
 assert all(z.read(p)==data for p,data in old.items())
# The downloadable artifact is the pack itself, never a ZIP containing another ZIP.
installable=out/'installable'
for path,data in sorted((old|files).items()):
 target=installable/path
 target.parent.mkdir(parents=True,exist_ok=True)
 target.write_bytes(data)
assert (installable/'pack.mcmeta').is_file()
assert not list(installable.glob('*.zip'))
for sid in ['covislight','mawonna','mawonna_flower']:
 assert (installable/f'data/cobblemon/species/custom/{sid}.json').is_file()
report={'mawonna_model_version':17,'mawonna_base_sha256':hashlib.sha256(base_zip.read_bytes()).hexdigest(),'directly_installable_artifact':True,'status':'passed','animations':len(a),'bones':len(bones),'original_entries_preserved':len(old),'added_entries':len(files),'minecraft_runtime_test':False}
(out/'validation.json').write_text(json.dumps(report,indent=2));print(json.dumps(report))
