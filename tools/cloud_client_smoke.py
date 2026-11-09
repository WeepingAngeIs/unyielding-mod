"""Real NeoForge/Cobblemon client resource loading under Xvfb, GitHub only."""
import os, json, shutil, subprocess, sys
from pathlib import Path
from cloud_runtime_smoke import VERSIONS, fetch_json, download

if os.environ.get('GITHUB_ACTIONS')!='true' or os.environ.get('RUNNER_ENVIRONMENT')!='github-hosted':
 raise SystemExit('Client builds and tests are restricted to GitHub-hosted runners')
root=Path.cwd(); project=root/'cloud-client'; run=project/'run'; out=root/'dist/client'
out.mkdir(parents=True,exist_ok=True);run.mkdir(parents=True,exist_ok=True)
shutil.copytree(root/'tools/client-smoke',project,dirs_exist_ok=True)
libs=project/'libs';libs.mkdir(exist_ok=True)
bundled=os.environ.get('UNYIELDING_BUNDLED_PACK')=='true'
versions=VERSIONS | ({'CobbleDollars':'Iqiqjfsi', 'LightmansCurrency':'n2ynBeOK'} if bundled else {})
for label,version in versions.items():
 info=fetch_json('https://api.modrinth.com/v2/version/'+version)
 item=next((x for x in info['files'] if x.get('primary')),info['files'][0])
 download(item['url'],libs/item['filename'],item['hashes']['sha512'])
companion=root/'unyielding-artifact/Unyielding-Mawonna.jar'
if not companion.is_file():raise FileNotFoundError(companion)
shutil.copy2(companion,libs/companion.name)
if not bundled:shutil.copytree(root/'client-pack',run/'resourcepacks/mawonna-test',dirs_exist_ok=True)
selected='["vanilla"]' if bundled else '["vanilla","file/mawonna-test"]'
(run/'options.txt').write_text('resourcePacks:'+selected+'\nmaxFps:30\nrenderDistance:2\nsimulationDistance:2\nguiScale:1\noverrideWidth:1280\noverrideHeight:720\n')
status='failed'
try:
 with (out/'client-console.log').open('w') as log:
  subprocess.run(['xvfb-run','-a','-s','-screen 0 1280x720x24','gradle','--no-daemon','runClient'],cwd=project,stdout=log,stderr=subprocess.STDOUT,check=True,timeout=1500,env=os.environ|{'LIBGL_ALWAYS_SOFTWARE':'true'})
 assert (run/'client-smoke-passed.json').exists(),'Client never confirmed loaded resource pack'
 result=json.loads((run/'client-smoke-passed.json').read_text())
 assert result.get('pose_transition_scale_samples',0)>0,'No pose transitions were checked'
 assert len(result.get('actual_gui_screenshots',[]))==6,'Missing actual GUI render screenshots'
 assert len(result.get('inkyeon_walk_samples',[]))==8,'Missing actual normal/shiny Inkyeon WALK rotation samples'
 assert len(result.get('fairy_sliggoo_face_bones',[]))==4,'Missing native Fairy Sliggoo face bones'
 for filename in result['actual_gui_screenshots']:
  assert (run/'visual-smoke'/filename).stat().st_size>10000,'Missing or empty GUI screenshot: '+filename
 log=(run/'logs/latest.log').read_text(errors='replace')
 failures=['Caught error loading resourcepacks']
 for namespace in ['lunavee','buzzeon','creepyeon','uwu','mawonna','mawile_custom','fairy_sliggoo','inkyeon']:
  failures.extend(prefix+namespace+':' for prefix in ['Failed to load animation group ', 'Failed to load model file with identifier ', 'Unable to load model '])
 for bad in failures:
  assert bad not in log,bad
 status='passed'
finally:
 for name in ['logs/latest.log','logs/debug.log','client-smoke-passed.json','client-smoke-failed.txt']:
  p=run/name
  if p.exists():shutil.copy2(p,out/p.name)
 if (run/'crash-reports').exists():shutil.copytree(run/'crash-reports',out/'crash-reports',dirs_exist_ok=True)
 if (run/'visual-smoke').exists():shutil.copytree(run/'visual-smoke',out/'visual-smoke',dirs_exist_ok=True)
 (out/'client-validation.json').write_text(json.dumps({'status':status,'minecraft':'1.21.1','cobblemon':'1.8.4','scope':'Actual Minecraft client resource reload; GUI profile and portrait screenshots of both sexes; Buzzeon flight cycle; Ghost Jirachi angles; Fairy Sliggoo normal/shiny standing face angles; Inkyeon normal/shiny WALK front/side/rear renders with nonzero stride and sampled leg rotations; runtime scale checks across pose transitions; malformed UV regression. Required dependencies only, without a world or complete user modpack.'},indent=2))
