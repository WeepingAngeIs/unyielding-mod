"""Apply requested gameplay and presentation revisions after validating source exports."""
import json

SIZES = {'lunavee': 0.5, 'buzzeon': 0.4, 'creepyeon': 0.4, 'ghost_jirachi': 0.4}
PORTRAITS = {
    'buzzeon': (1.6, [-0.75, 0.64, 0]),
    'creepyeon': (1.8, [-0.485, 0.89, 0]),
    'uwu': (1.55, [-0.23, 0.16, 0]),
}


def apply(files):
    def put(name, value):
        files[name] = (json.dumps(value, indent=2) + '\n').encode()

    for sid, size in SIZES.items():
        name = f'data/cobblemon/species/custom/{sid}.json'
        spec = json.loads(files[name])
        spec['baseScale'] = size
        if sid == 'lunavee':
            spec['height'] = 3
            spec['hitbox'] = {'fixed': False, 'height': 1, 'width': 0.9}
        put(name, spec)
    name = 'assets/cobblemon/lang/en_us.json'
    lang = json.loads(files[name])
    lang['cobblemon.species.ghostjirachi.name'] = 'Ghost Jirachi'
    lang['cobblemon.species.ghostjirachi.desc'] = lang['cobblemon.species.ghost_jirachi.desc']
    put(name, lang)
    for name in list(files):
        if name.startswith('assets/buzzeon/bedrock/pokemon/models/') and name.endswith('.geo.json'):
            geo = json.loads(files[name])
            for bone in geo['minecraft:geometry'][0]['bones']:
                # The new ears/wings extend up Y, unlike their Leafeon template.
                # Mirrored outward rest angles keep the entire flap outside the torso.
                angles = {'ear_left': 24, 'ear_right': -24,
                          'bug_wing_left': 35, 'bug_wing_left2': 35,
                          'bug_wing_right': -35, 'bug_wing_right2': -35}
                if bone['name'] in angles:
                    bone.setdefault('rotation', [0, 0, 0])[2] = angles[bone['name']]
            put(name, geo)
        if '/posers/' not in name or not name.startswith(tuple(f'assets/{ns}/' for ns in ['lunavee','buzzeon','creepyeon','uwu'])):
            continue
        poser = json.loads(files[name])
        namespace = name.split('/')[1]
        if namespace in PORTRAITS:
            poser['portraitScale'], poser['portraitTranslation'] = PORTRAITS[namespace]
        for pose in poser['poses'].values():
            # Match the proven Corvislight correction: never interpolate the 1/16 scale.
            pose['transformTicks'] = 0
        put(name, poser)


def validate(files):
    for sid, scale in SIZES.items():
        spec = json.loads(files[f'data/cobblemon/species/custom/{sid}.json'])
        assert spec['baseScale'] == scale, sid
        assert len([m for m in spec['moves'] if m.startswith('1:')]) <= 4, sid
    for name, data in files.items():
        if '/posers/' in name and name.startswith(tuple(f'assets/{ns}/' for ns in ['lunavee','buzzeon','creepyeon','uwu'])):
            poses = json.loads(data)['poses']
            assert all(p['transformTicks'] == 0 for p in poses.values()), name
            scales = [t['scale'] for p in poses.values() for t in p.get('transformedParts', []) if t.get('part') in ['import_uv_scale','export_scale']]
            assert scales and all(s == [0.0625]*3 for s in scales), name
    lang = json.loads(files['assets/cobblemon/lang/en_us.json'])
    assert lang['cobblemon.species.ghostjirachi.name'] == 'Ghost Jirachi'
