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
HEADERS = {'User-Agent': 'unyielding-mod-cloud-runtime-smoke/1.0 (GitHub Actions)'}

def fetch_json(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=HEADERS), timeout=90) as response:
        return json.load(response)

def download(url, path, sha512=None):
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
    parser.add_argument('--species',nargs='+',default=['Covislight','mawonna','mawonna_flower'])
    args=parser.parse_args()
    if os.environ.get('GITHUB_ACTIONS')!='true' or os.environ.get('RUNNER_ENVIRONMENT')!='github-hosted':
        raise SystemExit('Runtime tests are allowed only on GitHub-hosted runners.')
    accepted=args.eula_file.read_text(encoding='utf-8-sig')
    if not re.search(r'^\s*eula\s*=\s*true\s*$',accepted,re.M|re.I):
        raise SystemExit('An existing accepted Minecraft EULA file is required; this test does not accept it.')
    if not args.pack.exists():raise FileNotFoundError(args.pack)
    root=Path.cwd();work=root/'cloud-runtime';work.mkdir(exist_ok=True)
    output=root/'dist/runtime';output.mkdir(parents=True,exist_ok=True)
    manifest=[]
    mods=work/'mods';mods.mkdir(exist_ok=True)
    for label,version in VERSIONS.items():
        info=fetch_json('https://api.modrinth.com/v2/version/'+version)
        chosen=next((x for x in info['files'] if x.get('primary')),info['files'][0])
        download(chosen['url'],mods/chosen['filename'],chosen['hashes']['sha512'])
        manifest.append({'label':label,'version_id':version,'file':chosen['filename'],'sha512':chosen['hashes']['sha512']})
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
    if args.pack.is_dir():
        if not (args.pack/'pack.mcmeta').is_file():raise AssertionError('Downloaded install artifact has no root pack.mcmeta')
        shutil.copytree(args.pack,packs/'Mawonna-runtime-test',dirs_exist_ok=True)
    else:
        shutil.copy2(args.pack,packs/'Mawonna-runtime-test.zip')
    report={'minecraft':'1.21.1','neoforge':NEOFORGE,'mods':manifest,'commands':[],
            'scope':'Real server boot and actual species spawning; no client rendering or evolution battle simulation.',
            'status':'failed'}
    proc=None;rcon=None
    try:
        with (output/'server-console.log').open('w') as log:
            proc=subprocess.Popen(['bash','run.sh','nogui'],cwd=work,stdout=log,stderr=subprocess.STDOUT)
            deadline=time.monotonic()+600
            while time.monotonic()<deadline:
                if proc.poll() is not None:raise RuntimeError('Cloud server exited before readiness')
                text=(output/'server-console.log').read_text(errors='replace')
                if re.search(r'Done \([\d.]+s\)!',text) and 'RCON running on' in text:break
                time.sleep(3)
            else:raise TimeoutError('Cloud server did not start within ten minutes')
            rcon=Rcon(password)
            enabled=rcon.command('datapack list enabled');report['enabled_datapacks']=enabled
            if 'Mawonna-runtime-test' not in enabled:raise AssertionError('Combined pack is not enabled')
            rcon.command('gamerule doMobSpawning false')
            rcon.command('forceload add 0 0')
            rcon.command('scoreboard objectives add smoke dummy')
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
        (output/'runtime-validation.json').write_text(json.dumps(report,indent=2))
    print(json.dumps({'status':report['status'],'species':[x['species_argument'] for x in report['commands']]}))

if __name__=='__main__':main()

