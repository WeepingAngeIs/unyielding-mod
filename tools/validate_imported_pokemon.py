"""Check imported exports against the approved originals during the cloud build."""
import hashlib
import json
import zipfile


def validate(files, root):
    expected = {
        'lunavee': [80, 75, 80, 45, 80, 131],
        'buzzeon': [100, 140, 80, 15, 80, 135],
        'ghost_jirachi': [100] * 6,
        'creepyeon': [150, 50, 120, 50, 120, 100],
    }
    keys = ['hp', 'attack', 'defence', 'special_attack', 'special_defence', 'speed']
    manifest = json.loads((root / 'source/imported-pokemon/manifest.json').read_text())
    for sid, stats in expected.items():
        species = json.loads(files[f'data/cobblemon/species/custom/{sid}.json'])
        assert [species['baseStats'][k] for k in keys] == stats, sid
        assert species['implemented'] and not species['evolutions'], sid
        source = root / 'source/imported-pokemon' / manifest[sid]['archive']
        assert hashlib.sha256(source.read_bytes()).hexdigest() == manifest[sid]['sha256']
        with zipfile.ZipFile(source) as archive:
            for name in archive.namelist():
                if not name.startswith(('assets/', 'data/')) or name.endswith('/'):
                    continue
                original = archive.read(name)
                if name.endswith(('/lang/en_us.json', '/sounds.json')):
                    merged = json.loads(files[name])
                    for key, value in json.loads(original).items():
                        assert merged[key] == value, (sid, name, key)
                else:
                    assert (json.loads(files[name]) == json.loads(original) if name.endswith('.json') else files[name] == original), ('Approved export changed', sid, name)
    # No baseline localization or sound event can be lost when combining add-ons.
    with zipfile.ZipFile(root / 'Mawonna.zip') as archive:
        for name in ['assets/cobblemon/lang/en_us.json', 'assets/cobblemon/sounds.json']:
            if name in archive.namelist():
                for key, value in json.loads(archive.read(name)).items():
                    assert json.loads(files[name])[key] == value, (name, key)
    print('Four approved Pokemon exports preserved; species stats and shared registries verified.')
