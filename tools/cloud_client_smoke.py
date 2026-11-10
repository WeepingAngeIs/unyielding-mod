"""Real client resource checks on an authorized local PC or hosted Linux runner."""
import os, json, shutil, subprocess, sys, zipfile, hashlib
from pathlib import Path
from cloud_runtime_smoke import VERSIONS, fetch_json, download

if os.environ.get('UNYIELDING_LOCAL_BUILD')!='true' and (os.environ.get('GITHUB_ACTIONS')!='true' or os.environ.get('RUNNER_ENVIRONMENT')!='github-hosted'):
 raise SystemExit('Set UNYIELDING_LOCAL_BUILD=true for an authorized local run, or use a hosted runner')
root=Path.cwd(); project=root/'cloud-client'; run=project/'run'; out=root/'dist/client'
out.mkdir(parents=True,exist_ok=True);run.mkdir(parents=True,exist_ok=True)
shutil.copytree(root/'tools/client-smoke',project,dirs_exist_ok=True)
libs=project/'libs';libs.mkdir(exist_ok=True)
bundled=os.environ.get('UNYIELDING_BUNDLED_PACK')=='true'
versions=VERSIONS | ({'CobbleDollars':'Iqiqjfsi', 'LightmansCurrency':'n2ynBeOK'} if bundled else {})
if os.environ.get('UNYIELDING_ANIMATED_RELEASE')=='true': versions |= {'Xaero Minimap 26.5.0':'Q1tuMQBB'}
for label,version in versions.items():
 info=fetch_json('https://api.modrinth.com/v2/version/'+version)
 item=next((x for x in info['files'] if x.get('primary')),info['files'][0])
 download(item['url'],libs/item['filename'],item['hashes']['sha512'])
 if label.startswith('Kotlin for Forge'):
  with zipfile.ZipFile(libs/item['filename']) as kotlin:
   metadata=json.loads(kotlin.read('META-INF/jarjar/metadata.json'))
   stdlib=next(entry for entry in metadata['jars'] if entry['identifier']=={'group':'org.jetbrains.kotlin','artifact':'kotlin-stdlib'})
   compile_libs=project/'compile-libs';compile_libs.mkdir(exist_ok=True)
   (compile_libs/Path(stdlib['path']).name).write_bytes(kotlin.read(stdlib['path']))
companion=root/'unyielding-artifact/Unyielding-Mawonna.jar'
if not companion.is_file():raise FileNotFoundError(companion)
shutil.copy2(companion,libs/companion.name)
if not bundled:shutil.copytree(root/'client-pack',run/'resourcepacks/mawonna-test',dirs_exist_ok=True)
selected='["vanilla"]' if bundled else '["vanilla","file/mawonna-test"]'
(run/'options.txt').write_text('resourcePacks:'+selected+'\nmaxFps:30\nrenderDistance:2\nsimulationDistance:2\nguiScale:1\noverrideWidth:1280\noverrideHeight:720\n')
status='failed'
for name in ['client-smoke-passed.json','client-smoke-failed.txt']:
 (run/name).unlink(missing_ok=True)
 (out/name).unlink(missing_ok=True)
try:
 with (out/'client-console.log').open('w') as log:
  subprocess.run(([os.environ['UNYIELDING_GRADLE'],'--no-daemon','runClient'] if os.name=='nt' else ['xvfb-run','-a','-s','-screen 0 1280x720x24','gradle','--no-daemon','runClient']),cwd=project,stdout=log,stderr=subprocess.STDOUT,check=True,timeout=1500,env=os.environ|{'LIBGL_ALWAYS_SOFTWARE':'true'})
 assert (run/'client-smoke-passed.json').exists(),'Client never confirmed loaded resource pack'
 result=json.loads((run/'client-smoke-passed.json').read_text())
 if os.environ.get('UNYIELDING_SETTINGS_RELEASE')=='true':
  assert result.get('settings_client',{}).get('packet_handler_cases')==7,'Settings receipt/renderer checks missing'
  assert result.get('animated_icons',{}).get('mapScaleCases')==8,'Map scaling geometry checks missing'
 assert result.get('pose_transition_scale_samples',0)>0,'No pose transitions were checked'
 assert len(result.get('animated_icons',{}).get('native_repository_portrait_selection',{}))==3,'Mawonna animated portrait interception failed'
 assert len(result.get('actual_gui_screenshots',[]))==9,'Missing actual GUI render screenshots'
 assert len(result.get('inkyeon_walk_samples',[]))==8,'Missing actual normal/shiny Inkyeon WALK rotation samples'
 assert len(result.get('fairy_sliggoo_face_bones',[]))==4,'Missing native Frilligoo face bones'
 assert result.get('ghost_jirachi_native',{}).get('native_bones')==49,'Ghost Jirachi native skeleton check missing'
 assert len(result.get('inkyeon_transition_checks',{}).get('samples',[]))>=280,'Missing Inkyeon idle/walk transition checks'
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
 (out/'client-validation.json').write_text(json.dumps({'status':status,'minecraft':'1.21.1','cobblemon':'1.8.4','jar_sha256':hashlib.sha256(companion.read_bytes()).hexdigest(),'scope':'Actual Minecraft client resource reload; GUI profile and portrait screenshots; native portrait interception and map atlas frames; Buzzeon flight cycle; Ghost Jirachi angles; Frilligoo normal/shiny native-face angles; Inkyeon normal/shiny WALK renders with native Sylveon four-leg stride and static knee/ankle posture; runtime scale checks across pose transitions; malformed UV regression. Required dependencies only, without a world or complete user modpack. Live minimap radar still requires in-world verification.'},indent=2))
