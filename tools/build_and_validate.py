import json, zipfile, struct, re, gzip
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
resolver=json.loads(files[prefix+f'bedrock/pokemon/resolvers/{n}.json']);assert resolver['species']=='mawonna:'+n
species=json.loads(files[f'data/mawonna/species/custom/{n}.json']);assert species['maleRatio']==0 and species['implemented']
expected_stats={'hp':110,'attack':10,'defence':80,'special_attack':120,'special_defence':150,'speed':125}
assert species['name']=='Corvislight'
assert species['baseStats']==expected_stats
assert sum(species['baseStats'].values())==595
catalog=json.loads((root/'source/pink-corvisquire/level-one-moves.json').read_text())
required={'1:'+m['id'] for m in catalog}
assert len(required)==68 and required.issubset(set(species['moves']))
assert len(species['moves'])==len(set(species['moves']))
assert {'1:steelwing','1:esperwing','1:dualwingbeat','1:oblivionwing','1:wingattack'}.issubset(required)
with zipfile.ZipFile(root/'Mawonna.zip') as original:
 assert original.testzip() is None
 old={i.filename:original.read(i) for i in original.infolist() if not i.is_dir()}
 assert 'pack.mcmeta' in old
 assert not set(old)&set(files),'Unexpected overwrite of existing Mawonna entry'
 with zipfile.ZipFile(out/'Mawonna-with-Pink-Corvisquire.zip','w',zipfile.ZIP_DEFLATED) as z:
  for path,data in sorted((old|files).items()):z.writestr(path,data)
with zipfile.ZipFile(out/'Mawonna-with-Pink-Corvisquire.zip') as z:
 assert z.testzip() is None
 assert all(z.read(p)==data for p,data in old.items())
report={'status':'passed','animations':len(a),'bones':len(bones),'original_entries_preserved':len(old),'added_entries':len(files),'minecraft_runtime_test':False}
(out/'validation.json').write_text(json.dumps(report,indent=2));print(json.dumps(report))
