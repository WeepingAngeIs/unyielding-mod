import json, os, zipfile
from pathlib import Path
assert os.environ.get('GITHUB_ACTIONS') == 'true', 'Build on GitHub Actions'
r=Path(__file__).resolve().parents[1];p=r/'pack-additions'
files=[f for f in p.rglob('*') if f.is_file() and ('lunavee' in f.as_posix() or f.as_posix().endswith('uwu/lang/en_us.json'))]
for f in files:
 if f.suffix=='.json':json.loads(f.read_text())
s=json.loads((p/'data/cobblemon/species/custom/lunavee.json').read_text())
assert sum(s['baseStats'].values())==525
geo=json.loads((p/'assets/uwu/bedrock/pokemon/models/lunavee/lunavee.geo.json').read_text())['minecraft:geometry'][0]
names={b['name'] for b in geo['bones']}
assert len(names)==len(geo['bones'])
for b in geo['bones']:
 assert 'parent' not in b or b['parent'] in names
for anim in json.loads((p/'assets/uwu/bedrock/pokemon/animations/lunavee/lunavee.animation.json').read_text())['animations'].values():assert set(anim['bones'])<=names
with zipfile.ZipFile(r/'Mawonna.zip') as base:meta=base.read('pack.mcmeta')
(r/'dist').mkdir(exist_ok=True)
with zipfile.ZipFile(r/'dist/Lunavee.zip','w',zipfile.ZIP_DEFLATED) as z:
 z.writestr('pack.mcmeta',meta)
 for f in files:z.write(f,f.relative_to(p))
 z.write(r/'docs/LUNAVEE.md','README.md')
print('Validated JSON, stats, hierarchy and animation bone references; packaged',len(files),'assets. Runtime untested.')
