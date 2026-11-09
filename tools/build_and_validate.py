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
# Explicit removals and approved edits; all other baseline resources remain byte-identical.
removed=set(json.loads((r/'pack-removals.json').read_text()))
for name in removed: files.pop(name,None)
assert not any('mawileunyielding' in n.lower() for n in files)
allowed={
 'assets/cobblemon/lang/en_us.json','assets/mawonna/lang/en_us.json','assets/cobblemon/sounds.json','data/mawonna/dexes/custom.json','pack.mcmeta',
 'assets/mawonna/bedrock/pokemon/resolvers/mawonna.json',
 'assets/mawonna/bedrock/pokemon/resolvers/mawonna_flower.json',
 'assets/mawonna/bedrock/pokemon/resolvers/pink_corvisquire.json',
 'data/cobblemon/species/custom/mawonna.json','data/cobblemon/species/custom/mawonna_flower.json',
 'data/mawonna/species_additions/corvisquire_evolution.json',
 'data/mawonna/species_additions/mawile_evolution.json',
}
manifest=json.loads((r/'source/preserved-pack-manifest.json').read_text())
for name,sha in manifest.items():
 if name not in allowed and name not in removed:
  assert hashlib.sha256(files[name]).hexdigest()==sha, 'Existing pack changed: '+name
with zipfile.ZipFile(r/'Mawonna.zip') as z:
 for name in ['assets/cobblemon/sounds.json','data/mawonna/dexes/custom.json']:
  before=json.loads(z.read(name));after=json.loads(files[name])
  for key,value in before.items():
   if isinstance(value,list):assert all(item in after[key] for item in value if 'mawileunyielding' not in str(item)),(name,key)
   else:assert after[key]==value,(name,key)
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
catalog=json.loads((r/'source/fairy-sliggoo/level-one-moves.json').read_text())
assert all('1:'+m['id'] in s['moves'] for m in catalog)
assert all(not m.get('isZ') and not m.get('isMax') and m.get('isNonstandard') in (None,'Past') for m in catalog)
assert {'leechseed','withdraw','shelter','shellsmash','wingattack','dragontail','moonblast'}.issubset({m['id'] for m in catalog})
assert len(s['moves'])==len(set(s['moves']))
assert (s['primaryType'],s['secondaryType'])==('fairy','dragon') and s['implemented']
assert s['behaviour']['moving']['fly']['canFly'] and not s['evolutions']
evo=get('data/mawonna/species_additions/sliggoo_evolution.json')
assert evo['target']=='cobblemon:sliggoo'
assert evo['evolutions'][0]['result']=='fairy_sliggoo'
assert evo['evolutions'][0]['requirements']==[dict(variant='properties',target='mawonna_verified_dark_defeats_1000=1000')]
geo=get('assets/mawonna/bedrock/pokemon/models/fairy_sliggoo/fairy_sliggoo.geo.json')['minecraft:geometry'][0]
bones={b['name']:b for b in geo['bones']};assert len(bones)==len(geo['bones'])
wings={n for n in bones if n.startswith('cupid_wing_')};assert len(wings)==4
for b in bones.values():
 assert not b.get('parent') or b['parent'] in bones
 for c in b.get('cubes',[]):
  assert isinstance(c['uv'],list) and len(c['uv'])==2 and all(isinstance(v,int) for v in c['uv'])
  assert all(v>=0 for v in c['size'])
  # Native zero-width antenna planes have negative net origins; only their nonzero faces sample texture.
  u,v=c['uv'];x,y,z=c['size'];tw=geo['description']['texture_width'];th=geo['description']['texture_height']
  faces=[(u,v+z,z,y),(u+z,v+z,x,y),(u+z+x,v+z,z,y),(u+2*z+x,v+z,x,y),(u+z,v,x,z),(u+z+x,v,x,z)]
  for fu,fv,fw,fh in faces:
   if fw>0 and fh>0:assert 0<=fu and fu+fw<=tw and 0<=fv and fv+fh<=th,(b['name'],c['uv'],(fu,fv,fw,fh))
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
for sid in ['mawonna','mawonna_flower','inkyeon','covislight','fairy_sliggoo']:
 assert get('data/cobblemon/species/custom/'+sid+'.json')['implemented']
bird=get('assets/mawonna/bedrock/pokemon/posers/pink_corvisquire.json')
assert all(p['transformTicks']==0 for p in bird['poses'].values())
meta=get('pack.mcmeta');meta['pack']['description']='Mawonna v17 + Lunavee + Buzzeon + Ghost Jirachi + Creepyeon | companion mod required'
files['pack.mcmeta']=(json.dumps(meta,indent=2)+'\n').encode()
# Every custom defeat gate uses a fresh verified feature, never native unfiltered defeats.
for base_name,cap in [('mawile',666),('corvisquire',1000),('sliggoo',1000),('sylveon',1000)]:
 ev=get('data/mawonna/species_additions/'+base_name+'_evolution.json')['evolutions'][0]
 assert all(req['variant']!='defeat' for req in ev['requirements'])
 key='mawonna_verified_dark_defeats'+('_1000' if cap==1000 else '')
 assert any(key+'='+str(cap) in req.get('target','') for req in ev['requirements'])
 feature=get('data/cobblemon/species_features/'+key+'.json')
 assert feature['default']==0 and feature['visible'] and feature['max']==cap and 'itemPoints' not in feature
maw_req=get('data/mawonna/species_additions/mawile_evolution.json')['evolutions'][0]['requirements']
assert any('mawonna_distinct_meals=49' in req.get('target','') and 'mawonna_married=1' in req.get('target','') for req in maw_req)
for key,cap in [('mawonna_distinct_meals',49),('mawonna_married',1)]:
 feature=get('data/cobblemon/species_features/'+key+'.json');assert feature['default']==0 and feature['max']==cap and 'itemPoints' not in feature
ink=get('data/cobblemon/species/custom/inkyeon.json')
assert ink['baseStats']==dict(hp=101,attack=145,defence=90,special_attack=10,special_defence=105,speed=146)
assert ink['abilities']==['synchronize','inkylove','h:taintedcharm']
assert (ink['primaryType'],ink['secondaryType'])==('fairy','dark')
assert all('1:'+m['id'] in ink['moves'] for m in json.loads((r/'source/inkyeon-level-one-moves.json').read_text()))
ibones={b['name'] for b in get('assets/mawonna/bedrock/pokemon/models/inkyeon/inkyeon.geo.json')['minecraft:geometry'][0]['bones']}
iclips=get('assets/mawonna/bedrock/pokemon/animations/inkyeon/inkyeon.animation.json')['animations']
for name,clip in iclips.items():assert set(clip.get('bones',{})).issubset(ibones),(name,set(clip.get('bones',{}))-ibones)
assert abs(iclips['animation.inkyeon.playful_idle']['animation_length']/3-iclips['animation.inkyeon.battle_idle']['animation_length'])<1e-8
iposer=get('assets/mawonna/bedrock/pokemon/posers/inkyeon.json')
assert iposer['rootBone'] in ibones
for clip in re.findall(r"q.bedrock(?:_stateful|_quirk)?\('inkyeon', '([^']+)'",json.dumps(iposer)):assert 'animation.inkyeon.'+clip in iclips
for sid,catalog_file in [('mawonna','mawonna-public-moves.json'),('mawonna_flower','mawonna-flower-moves.json')]:
 spec=get('data/cobblemon/species/custom/'+sid+'.json');selected=json.loads((r/'source'/catalog_file).read_text())
 assert len(spec['moves'])==len(selected) and set(spec['moves'])=={'1:'+m['id'] for m in selected}
 assert 'h:futakuchisgrasp' in spec['abilities'] and not any('sheerforce' in a for a in spec['abilities'])
 if sid=='mawonna':assert all(m['pp']>1 and not m['zMove'] and not m['maxMove'] for m in selected)
 else:assert any(m['zMove'] and m['pp']==1 for m in selected)
for resolver in ['mawonna','mawonna_flower','pink_corvisquire','fairy_sliggoo','inkyeon']:
 variants=get('assets/mawonna/bedrock/pokemon/resolvers/'+resolver+'.json')['variations']
 assert any(v.get('aspects')==['shiny'] for v in variants),resolver
 for variant in variants:
  if 'texture' in variant:
   ns,path=variant['texture'].split(':',1)
   with Image.open(io.BytesIO(files['assets/'+ns+'/'+path])) as im:im.verify()
from validate_imported_pokemon import validate
validate(files, r)
from convert_imported_uv import convert
(out/'imported-uv-conversion.json').write_text(json.dumps(convert(files), indent=2))
install=out/'installable' 
with zipfile.ZipFile(out/'Mawonna.zip','w',zipfile.ZIP_DEFLATED) as z:
 for name,data in sorted(files.items()):
  info=zipfile.ZipInfo(name,(2026,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,data)
  p=install/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
report={'status':'passed','mawonna_version':17,'existing_entries_preserved':sum(n not in allowed and n not in removed for n in manifest),'sliggoo_animations':len(clips),'sliggoo_wings':len(wings),'sliggoo_bst':575,'inkyeon_animations':len(iclips),'inkyeon_bst':597,'fresh_verified_defeat_counters':True,'removed_mawile_unyielding':True,'sha256':hashlib.sha256((out/'Mawonna.zip').read_bytes()).hexdigest()}
(out/'validation.json').write_text(json.dumps(report,indent=2));print(json.dumps(report))


