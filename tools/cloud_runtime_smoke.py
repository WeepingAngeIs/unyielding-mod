#!/usr/bin/env python3
"""Boot a minimal real Cobblemon server on GitHub-hosted runners only.

Does not compile mods or run locally. Uses the user's already-accepted Minecraft
1.21.1 server EULA copied from CU-TestServer/eula.txt. Pass --eula-file explicitly.
"""
from __future__ import annotations
import argparse, hashlib, json, os, re, secrets, shutil, socket, struct, subprocess, time, urllib.request
from pathlib import Path

VERSIONS = {
    'Cobblemon 1.8.4 NeoForge': 'xsVHrAsY',
    'Kotlin for Forge 5.12.0': 'uhJhCT7X',
    'Mega Showdown 1.3.3': 'TrpwiTL5',
    'Accessories 1.1.0-beta.53': 'Fb55Fgjz',
    'owo-lib 0.12.15.5-beta.1': 'NMCHU6DZ',
    'Architectury 13.0.11': '1IiqEQGl',
}
NEOFORGE = '21.1.249'
MEAL_VERSIONS = {
    "Farmer's Delight 1.3.4": 'XTVZDOol',
    "My Nether's Delight 1.10.4.1": 'SGFJBL5q',
    'Twilight Delight 3.2.9': 'MFC7tDeW',
    "End's Delight 2.6.1": 'YTApg6Hl',
}
# Exact installed dependency, sourced from the user's CurseForge instance metadata.
TWILIGHT_FOREST = {
    'filename': 'twilightforest-1.21.1-4.8.3345-universal.jar',
    'url': 'https://edge.forgecdn.net/files/7797/302/twilightforest-1.21.1-4.8.3345-universal.jar',
    'sha1': '9cfd4154928433e2f1f6cfc3deb48c284debb580',
}
MEAL_AUDIT_MODS = [('eternal_starlight_delight-neoforge-1.4.0.jar', 'https://edge.forgecdn.net/files/8955/160/eternal_starlight_delight-neoforge-1.4.0.jar', 'eb01e0ac5c994492a3880b84cf56a1ff643b2c67c824b629b9aea6054dfdd69f6ff47ebbd5399919a760327249353d1b9414f463b1dc6e3058381e86a11bc9d4'),
    ('eternalstarlight-0.9.1+1.21.1+neoforge.jar', 'https://edge.forgecdn.net/files/8931/80/eternalstarlight-0.9.1%2b1.21.1%2bneoforge.jar', '2bd7d2fbc478b3cc2b6426ff9cffddf8800b46aa2ca5a4570f9d9ed2f50d4aeadc83bea0c5c410bf26c2e9e0c6cc3296cb8adff233787897265aa2d7a1ad9a71'),
    ('CreativeCore_NEOFORGE_v2.13.49_mc1.21.1.jar', 'https://edge.forgecdn.net/files/8975/261/CreativeCore_NEOFORGE_v2.13.49_mc1.21.1.jar', '344ebfc6ab4043620a66f127bacfe115f897e01ce130aa0b154d59672f5a7bde27e522201c1737c0062c03940ecd9ec44cfb2ebbe9da0abfbf0a09e84b31a765'),
    ('Spice of Life Onion_NEOFORGE_v1.5.6_mc1.21.1.jar', 'https://edge.forgecdn.net/files/7675/823/Spice%20of%20Life%20Onion_NEOFORGE_v1.5.6_mc1.21.1.jar', '15a08552a68e75e692a13e7ac82586f877991eb1fad2f40b06e1304c5f5d7aa67fa00f704448a80a4113ecba79b262bda131f3c2043851228befeaa0025f8c2f'),
    ('oceansdelight-neoforge-1.0.4-1.21.1.jar', 'https://edge.forgecdn.net/files/7939/973/oceansdelight-neoforge-1.0.4-1.21.1.jar', '8079160938175f9d0c1b8f10587c40e3c6b54210511109331f0bb37a845aa90522f60c4e668688e8a0b4023e0464b80d44f50a6af39572fe519d48c9581a9f15')]
HEADERS = {'User-Agent': 'unyielding-mod-cloud-runtime-smoke/1.0 (GitHub Actions)'}

def fetch_json(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=HEADERS), timeout=90) as response:
        return json.load(response)

def download(url, path, sha512=None):
    if os.environ.get('UNYIELDING_LOCAL_BUILD')=='true':
        for folder in [Path(os.environ.get('UNYIELDING_LOCAL_MODS','__missing__')),Path.cwd()/'companion/libs']:
            cached=folder/path.name
            if cached.is_file() and cached.resolve()!=path.resolve() and (sha512 is None or hashlib.sha512(cached.read_bytes()).hexdigest()==sha512):
                shutil.copy2(cached,path)
                return
    with urllib.request.urlopen(urllib.request.Request(url, headers=HEADERS), timeout=180) as response, path.open('wb') as handle:
        shutil.copyfileobj(response, handle)
    if sha512 and hashlib.sha512(path.read_bytes()).hexdigest() != sha512:
        raise RuntimeError('Download checksum mismatch: '+path.name)

class Rcon:
    def __init__(self, password):
        self.sock = socket.create_connection(('127.0.0.1', 25575), timeout=30)
        self.sock.settimeout(30)
        self.sequence = 1
        self.send(3, password)
        packet_id, _, _ = self.receive()
        if packet_id == -1:
            raise RuntimeError('Cloud RCON authentication failed')
    def recv_exact(self, size):
        data = b''
        while len(data) < size:
            block = self.sock.recv(size-len(data))
            if not block:
                raise ConnectionError('RCON connection closed')
            data += block
        return data
    def send(self, kind, text):
        self.sequence += 1
        payload = struct.pack('<ii', self.sequence, kind) + text.encode('utf-8') + b'\x00\x00'
        self.sock.sendall(struct.pack('<i',len(payload)) + payload)
        return self.sequence
    def receive(self):
        size = struct.unpack('<i',self.recv_exact(4))[0]
        if not 10 <= size <= 1048576:
            raise RuntimeError('Invalid RCON packet size')
        packet = self.recv_exact(size)
        packet_id,kind = struct.unpack('<ii',packet[:8])
        return packet_id,kind,packet[8:-2].decode('utf-8', errors='replace')
    def command(self, command):
        wanted=self.send(2,command)
        for _ in range(10):
            packet_id,_,text=self.receive()
            if packet_id==wanted:
                return text
        raise RuntimeError('No RCON response for command')


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--pack',required=True,type=Path)
    parser.add_argument('--eula-file',required=True,type=Path)
    parser.add_argument('--unyielding',required=True,type=Path)
    parser.add_argument('--settings-test',type=Path,help='Test-only settings harness for the isolated server')
    parser.add_argument('--species',nargs='+',default=['Covislight','mawonna','mawonna_flower'])
    args=parser.parse_args()
    if os.environ.get('UNYIELDING_LOCAL_BUILD')!='true' and (os.environ.get('GITHUB_ACTIONS')!='true' or os.environ.get('RUNNER_ENVIRONMENT')!='github-hosted'):
        raise SystemExit('Runtime tests are allowed only on GitHub-hosted runners.')
    accepted=args.eula_file.read_text(encoding='utf-8-sig')
    if not re.search(r'^\s*eula\s*=\s*true\s*$',accepted,re.M|re.I):
        raise SystemExit('An existing accepted Minecraft EULA file is required; this test does not accept it.')
    if not args.pack.exists():raise FileNotFoundError(args.pack)
    root=Path.cwd();work=root/'cloud-runtime';work.mkdir(exist_ok=True)
    output=root/'dist/runtime';output.mkdir(parents=True,exist_ok=True)
    manifest=[]
    mods=work/'mods';mods.mkdir(exist_ok=True)
    test_dependencies = {'Open Parties and Claims 0.33.0':'yWmXD7GW'} if args.settings_test else {}
    for label,version in (VERSIONS | MEAL_VERSIONS | {'CobbleDollars':'Iqiqjfsi','LightmansCurrency':'n2ynBeOK'} | test_dependencies).items():
        info=fetch_json('https://api.modrinth.com/v2/version/'+version)
        chosen=next((x for x in info['files'] if x.get('primary')),info['files'][0])
        download(chosen['url'],mods/chosen['filename'],chosen['hashes']['sha512'])
        manifest.append({'label':label,'version_id':version,'file':chosen['filename'],'sha512':chosen['hashes']['sha512']})
    for filename, url, digest in MEAL_AUDIT_MODS:
        download(url, mods/filename, digest)
        manifest.append({'label':filename, 'sha512':digest})
    forest=mods/TWILIGHT_FOREST['filename']
    download(TWILIGHT_FOREST['url'],forest)
    if hashlib.sha1(forest.read_bytes()).hexdigest()!=TWILIGHT_FOREST['sha1']:
        raise AssertionError('Twilight Forest dependency checksum mismatch')
    manifest.append({'label':'Twilight Forest 4.8.3345',**TWILIGHT_FOREST})
    if not args.unyielding.is_file():raise FileNotFoundError(args.unyielding)
    shutil.copy2(args.unyielding,mods/'Unyielding-Mawonna.jar')
    manifest.append({'label':'Integrated Unyielding mod','file':'Unyielding-Mawonna.jar','sha256':hashlib.sha256(args.unyielding.read_bytes()).hexdigest()})
    if args.settings_test:
        if not args.settings_test.is_file(): raise FileNotFoundError(args.settings_test)
        shutil.copy2(args.settings_test,mods/'settings-smoke-test-only.jar')
        manifest.append({'label':'Test-only settings harness','sha256':hashlib.sha256(args.settings_test.read_bytes()).hexdigest()})
    installer=work/'neoforge-installer.jar'
    download(f'https://maven.neoforged.net/releases/net/neoforged/neoforge/{NEOFORGE}/neoforge-{NEOFORGE}-installer.jar',installer)
    with (output/'installer.log').open('w') as log:
        subprocess.run(['java','-jar',installer.name,'--installServer'],cwd=work,stdout=log,stderr=subprocess.STDOUT,check=True,timeout=600)
    (work/'eula.txt').write_text(accepted)
    (work/'user_jvm_args.txt').write_text('-Xms1G\n-Xmx4G\n-Djava.awt.headless=true\n')
    password=secrets.token_urlsafe(30)
    (work/'server.properties').write_text('\n'.join([
        'server-ip=127.0.0.1','server-port=25565','enable-rcon=true','rcon.port=25575',
        'rcon.password='+password,'online-mode=false','enforce-secure-profile=false',
        'level-name=smoke-world','level-type=minecraft:flat','generate-structures=false',
        'view-distance=2','simulation-distance=2','max-players=1','spawn-protection=0',
        'difficulty=peaceful','sync-chunk-writes=false','motd=Cloud smoke test only',
    ])+'\n')
    packs=work/'smoke-world/datapacks';packs.mkdir(parents=True,exist_ok=True)
    if os.environ.get('UNYIELDING_BUNDLED_PACK')=='true':
        pass  # Test embedded data without an external duplicate pack.
    elif args.pack.is_dir():
        if not (args.pack/'pack.mcmeta').is_file():raise AssertionError('Downloaded install artifact has no root pack.mcmeta')
        shutil.copytree(args.pack,packs/'Mawonna-runtime-test',dirs_exist_ok=True)
    else:
        shutil.copy2(args.pack,packs/'Mawonna-runtime-test.zip')
    report={'minecraft':'1.21.1','neoforge':NEOFORGE,'mods':manifest,'commands':[],
            'scope':'Real server boot and normal/shiny spawning; real BattleFaintedEvent fixtures with synthetic actors exercise production defeat tracking, native counters, evolution gates, feeding, marriage and persistence. No full human battle replay.',
            'status':'failed'}
    proc=None;rcon=None
    try:
        with (output/'server-console.log').open('w') as log:
            proc=subprocess.Popen((['cmd','/c','run.bat','nogui'] if os.name=='nt' else ['bash','run.sh','nogui']),cwd=work,stdout=log,stderr=subprocess.STDOUT)
            deadline=time.monotonic()+600
            while time.monotonic()<deadline:
                if proc.poll() is not None:raise RuntimeError('Cloud server exited before readiness')
                text=(output/'server-console.log').read_text(errors='replace')
                if re.search(r'Done \([\d.]+s\)!',text) and 'RCON running on' in text:break
                time.sleep(3)
            else:raise TimeoutError('Cloud server did not start within ten minutes')
            rcon=Rcon(password)
            enabled=rcon.command('datapack list enabled');report['enabled_datapacks']=enabled
            if os.environ.get('UNYIELDING_BUNDLED_PACK')!='true' and 'Mawonna-runtime-test' not in enabled:raise AssertionError('Combined pack is not enabled')
            rcon.command('gamerule doMobSpawning false')
            rcon.command('forceload add 0 0')
            rcon.command('scoreboard objectives add smoke dummy')
            self_test=rcon.command('mawonnatest')
            report['companion_self_test']=self_test
            if 'MAWONNA_SELF_TEST_OK' not in self_test:
                raise AssertionError('Companion runtime assertions failed: '+self_test)
            if args.settings_test:
                settings_test=rcon.command('settingstest')
                report['settings_self_test']=settings_test
                if 'SETTINGSTEST PASS' not in settings_test:
                    raise AssertionError('Settings runtime assertions failed: '+settings_test)
                result=json.loads((work/'settings-smoke-result.json').read_text())
                report['settings_results']=result
                if result.get('status') != 'PASS':
                    raise AssertionError('Settings report/cleanup did not pass: '+json.dumps(result))
            for index,species in enumerate(args.species):
                # Score records the actual command's return success; parser errors cannot pass.
                marker='spawn_'+str(index)
                rcon.command('kill @e[type=cobblemon:pokemon]')
                response=rcon.command(f'execute positioned 0 80 0 store success score {marker} smoke run pokespawn {species} level=5')
                score=rcon.command(f'scoreboard players get {marker} smoke')
                rcon.command('scoreboard players set entity_exists smoke 0')
                rcon.command('execute if entity @e[type=cobblemon:pokemon] run scoreboard players set entity_exists smoke 1')
                exists=rcon.command('scoreboard players get entity_exists smoke')
                record={'species_argument':species,'command_response':response,'score':score,'entity_check':exists}
                report['commands'].append(record)
                if not re.search(r'\bhas 1\b',score) or not re.search(r'\bhas 1\b',exists):
                    raise AssertionError('Actual spawn command failed: '+json.dumps(record))
                rcon.command('kill @e[type=cobblemon:pokemon]')
                response=rcon.command(f'execute positioned 0 80 0 store success score {marker}_shiny smoke run pokespawn {species} shiny level=5')
                score=rcon.command(f'scoreboard players get {marker}_shiny smoke')
                report['commands'].append({'species_argument':species+' shiny','command_response':response,'score':score})
                if not re.search(r'\bhas 1\b',score):raise AssertionError('Shiny spawn command failed: '+species)
            report['status']='passed'
    finally:
        if rcon:
            try:rcon.command('stop')
            except (ConnectionError,OSError):pass
            rcon.sock.close()
        if proc:
            try:proc.wait(timeout=60)
            except subprocess.TimeoutExpired:proc.kill();proc.wait()
        for name in ['latest.log','debug.log']:
            p=work/'logs'/name
            if p.exists():shutil.copy2(p,output/name)
        settings_report=work/'settings-smoke-result.json'
        if args.settings_test and settings_report.exists():shutil.copy2(settings_report,output/settings_report.name)
        (output/'runtime-validation.json').write_text(json.dumps(report,indent=2))
        if report['status']!='passed':
            latest=output/'latest.log'
            console=output/'server-console.log'
            failure_log=latest if latest.exists() else console
            if failure_log.exists():
                print('Cloud server failure: last 100 log lines',flush=True)
                print('\n'.join(failure_log.read_text(errors='replace').splitlines()[-100:]),flush=True)
    print(json.dumps({'status':report['status'],'species':[x['species_argument'] for x in report['commands']]}))

if __name__=='__main__':main()

