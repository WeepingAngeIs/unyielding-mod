"""Patch only the requested model assets into the verified combined release."""
import argparse
import hashlib
import json
import re
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE_SHA = '876595a2d8bc79589bfde81c7253354e76e042eb9eefdb7db847d3968f85c09b'
BASE_URL = 'https://raw.githubusercontent.com/WeepingAngeIs/Server-Jars/c663a61246e255d0c8eda35e04a0e707e182bf46/releases/Unyielding-1.1.7.jar'
PACK_SHA = '7bd79d1a20a776a500dffc0e70b315778885608a16c0e3974eb62feaf0477e12'
PACK_URL = 'https://raw.githubusercontent.com/WeepingAngeIs/unyielding-mod/eabce5dd5eee89490d548a4aae5d9253374c5969/Mawonna.zip'
ASSETS = [
    f'assets/mawonna/bedrock/pokemon/{kind}/{sid}/{sid}.{suffix}.json'
    for sid in ('fairy_sliggoo', 'inkyeon')
    for kind, suffix in [('models', 'geo'), ('animations', 'animation')]
] + [f'assets/mawonna/bedrock/pokemon/posers/{sid}.json' for sid in ('fairy_sliggoo', 'inkyeon')]
ASSETS += [f'assets/mawonna/textures/pokemon/fairy_sliggoo/fairy_sliggoo{suffix}.png' for suffix in ('', '_shiny')]


def sha(data):
    return hashlib.sha256(data).hexdigest()


def read_zip(path):
    with zipfile.ZipFile(path) as archive:
        assert archive.testzip() is None
        assert len(archive.namelist()) == len(set(archive.namelist()))
        return {n: archive.read(n) for n in archive.namelist() if not n.endswith('/')}


def write_zip(path, entries):
    with zipfile.ZipFile(path, 'w', zipfile.ZIP_DEFLATED) as archive:
        for name, data in sorted(entries.items()):
            info = zipfile.ZipInfo(name, (2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.create_system = 3
            info.external_attr = 0o600 << 16
            archive.writestr(info, data)
    assert read_zip(path) == entries


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--base', type=Path)
    args = parser.parse_args()
    out = ROOT / 'dist/model-hotfix'
    out.mkdir(parents=True, exist_ok=True)
    base = args.base or out / 'Unyielding-1.1.7.jar'
    if not base.is_file():
        with urllib.request.urlopen(BASE_URL, timeout=180) as response:
            base.write_bytes(response.read())
    assert sha(base.read_bytes()) == BASE_SHA, 'Unexpected baseline release'
    original = read_zip(base)
    revised = dict(original)
    pack_path = ROOT / 'Mawonna.zip'
    if sha(pack_path.read_bytes()) != PACK_SHA:
        pack_path = out / 'Mawonna-baseline.zip'
        if not pack_path.is_file() or sha(pack_path.read_bytes()) != PACK_SHA:
            with urllib.request.urlopen(PACK_URL, timeout=180) as response:
                pack_path.write_bytes(response.read())
    assert sha(pack_path.read_bytes()) == PACK_SHA, 'Unexpected baseline resource pack'
    pack = read_zip(pack_path)
    changes = []
    for name in ASSETS:
        content = (ROOT / 'pack-additions' / name).read_bytes()
        assert pack[name] == original[name], 'Pack and installed baseline disagree: ' + name
        if name.endswith('.geo.json'):
            document = json.loads(content)
            old_bones = {b['name']: b for b in json.loads(original[name])['minecraft:geometry'][0]['bones']}
            # Source models predate the assembly-added seat locator. Preserve it.
            for bone in document['minecraft:geometry'][0]['bones']:
                for key, value in old_bones.get(bone['name'], {}).get('locators', {}).items():
                    bone.setdefault('locators', {}).setdefault(key, value)
            if document != json.loads(original[name]):
                content = (json.dumps(document, indent=2) + '\n').encode()
            else:
                content = original[name]
        elif name.endswith('.json'):
            document = json.loads(content)
            content = original[name] if document == json.loads(original[name]) else (json.dumps(document, indent=2) + '\n').encode()
        if content != original[name]:
            changes.append(name)
            revised[name] = pack[name] = content
    assert changes and any('fairy_sliggoo' in n for n in changes) and any('inkyeon' in n for n in changes)
    manifest = 'META-INF/MANIFEST.MF'
    revised[manifest] = re.sub(rb'(?m)^Implementation-Version: [^\r\n]+', b'Implementation-Version: 1.1.8', original[manifest])
    assert revised[manifest] != original[manifest]
    changed = {n for n in original if revised[n] != original[n]}
    assert changed == set(changes) | {manifest}
    assert set(revised) == set(original)
    jar = out / 'Unyielding-1.1.8.jar'
    write_zip(jar, revised)
    write_zip(out / 'Mawonna.zip', pack)
    report = dict(status='passed', version='1.1.8', base_sha256=BASE_SHA,
                  jar_sha256=sha(jar.read_bytes()), pack_sha256=sha((out / 'Mawonna.zip').read_bytes()),
                  changed_assets=changes, preserved_entries=len(original)-len(changed),
                  unchanged_gameplay_data=True, unchanged_java_classes=True)
    (out / 'assembly-report.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
