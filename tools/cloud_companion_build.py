"""Build the gameplay companion exclusively on GitHub-hosted runners."""
import json
import os
import shutil
import subprocess
from pathlib import Path
from cloud_runtime_smoke import VERSIONS, download, fetch_json

if os.environ.get('GITHUB_ACTIONS') != 'true' or os.environ.get('RUNNER_ENVIRONMENT') != 'github-hosted':
    raise SystemExit('Companion builds and tests are restricted to GitHub-hosted runners')
root = Path.cwd()
project = root / 'companion'
libs = project / 'libs'
libs.mkdir(parents=True, exist_ok=True)
out = root / 'dist/companion'
out.mkdir(parents=True, exist_ok=True)
manifest = []
for label, version in VERSIONS.items():
    info = fetch_json('https://api.modrinth.com/v2/version/' + version)
    item = next((x for x in info['files'] if x.get('primary')), info['files'][0])
    download(item['url'], libs / item['filename'], item['hashes']['sha512'])
    manifest.append({'label': label, 'version_id': version, 'sha512': item['hashes']['sha512']})
try:
    with (out / 'build.log').open('w') as log:
        subprocess.run(['gradle', '--no-daemon', 'build'], cwd=project, stdout=log,
                       stderr=subprocess.STDOUT, check=True, timeout=1500)
    jar = project / 'build/libs/mawonna-companion-1.0.0.jar'
    if not jar.is_file():
        raise AssertionError('Companion build did not produce the expected installable JAR')
    subprocess.run(['python', 'tools/assemble_unyielding.py', '--base', 'source/unyielding-base.jar',
                    '--module', str(jar.relative_to(root)), '--output', 'dist/Unyielding-Mawonna.jar'],
                   cwd=root, check=True, timeout=180)
    (out / 'dependencies.json').write_text(json.dumps(manifest, indent=2))
except Exception:
    log_file = out / 'build.log'
    if log_file.exists():
        print('\n'.join(log_file.read_text(errors='replace').splitlines()[-150:]))
    raise
finally:
    reports = project / 'build/reports'
    if reports.is_dir():
        shutil.copytree(reports, out / 'reports', dirs_exist_ok=True)
