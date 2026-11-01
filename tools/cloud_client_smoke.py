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
for label,version in VERSIONS.items():
 info=fetch_json('https://api.modrinth.com/v2/version/'+version)
 item=next((x for x in info['files'] if x.get('primary')),info['files'][0])
 download(item['url'],libs/item['filename'],item['hashes']['sha512'])
shutil.copytree(root/'client-pack',run/'resourcepacks/mawonna-test',dirs_exist_ok=True)
(run/'options.txt').write_text('resourcePacks:["vanilla","file/mawonna-test"]\nmaxFps:30\nrenderDistance:2\nsimulationDistance:2\n')
status='failed'
try:
 with (out/'client-console.log').open('w') as log:
  subprocess.run(['xvfb-run','-a','-s','-screen 0 1280x720x24','gradle','--no-daemon','runClient'],cwd=project,stdout=log,stderr=subprocess.STDOUT,check=True,timeout=1500,env=os.environ|{'LIBGL_ALWAYS_SOFTWARE':'true'})
 assert (run/'client-smoke-passed.json').exists(),'Client never confirmed loaded resource pack'
 log=(run/'logs/latest.log').read_text(errors='replace')
 for bad in ['Failed to load model file with identifier mawonna:', 'Unable to load model mawonna:', 'Caught error loading resourcepacks', 'Failed to load model file with identifier mawile_custom:']:
  assert bad not in log,bad
 status='passed'
finally:
 for name in ['logs/latest.log','logs/debug.log','client-smoke-passed.json','client-smoke-failed.txt']:
  p=run/name
  if p.exists():shutil.copy2(p,out/p.name)
 if (run/'crash-reports').exists():shutil.copytree(run/'crash-reports',out/'crash-reports',dirs_exist_ok=True)
 (out/'client-validation.json').write_text(json.dumps({'status':status,'minecraft':'1.21.1','cobblemon':'1.8.4','scope':'Actual Minecraft client resource reload, model/poser resolution, malformed UV regression, selected pack retained. Minimal mod set; not the complete user modpack.'},indent=2))
