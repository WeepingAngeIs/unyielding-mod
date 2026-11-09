"""Cloud-only compatibility bake of approved per-face UV exports."""
import io
import json
import math
from PIL import Image
from convert_client_uv import layout

SCALE = 16


def scaled(value):
    if isinstance(value, list):
        return [scaled(v) for v in value]
    if isinstance(value, dict):
        return {k: v if k == 'lerp_mode' else scaled(v) for k, v in value.items()}
    if isinstance(value, str):
        return '(' + value + ')*16'
    return value * SCALE


def convert(files):
    report = {}
    for namespace, sid in [('lunavee', 'lunavee'), ('buzzeon', 'buzzeon'), ('uwu', 'ghost_jirachi')]:
        prefix = f'assets/{namespace}/'
        models = {n: json.loads(b) for n, b in files.items() if n.startswith(prefix + 'bedrock/pokemon/models/') and n.endswith('.geo.json')}
        tiles = []
        px = py = 2
        row = 0
        width = 4096
        for name, geo in models.items():
            model = geo['minecraft:geometry'][0]
            for bone in model['bones']:
                if 'pivot' in bone:
                    bone['pivot'] = scaled(bone['pivot'])
                for key, loc in bone.get('locators', {}).items():
                    if isinstance(loc, list): bone['locators'][key] = scaled(loc)
                    elif 'offset' in loc: loc['offset'] = scaled(loc['offset'])
                for cube in bone.get('cubes', []):
                    size, uv = cube['size'], cube['uv']
                    source = layout(size, *uv) if isinstance(uv, list) else {f: (*d['uv'], *d['uv_size']) for f, d in uv.items()}
                    target = scaled(size)
                    w = math.ceil(2 * (target[0] + target[2])) + 4
                    h = math.ceil(target[1] + target[2]) + 4
                    assert w < width
                    if px + w > width: px = 2; py += row; row = 0
                    tiles.append((source, layout(target, px, py)))
                    cube['uv'] = [px, py]
                    for key in ('origin', 'size', 'pivot', 'inflate'):
                        if key in cube: cube[key] = scaled(cube[key])
                    px += w
                    row = max(row, h)
            roots = [b for b in model['bones'] if not b.get('parent')]
            for bone in roots: bone['parent'] = 'import_uv_scale'
            model['bones'][:0] = [{'name': 'import_uv_root', 'pivot': [0, 0, 0]}, {'name': 'import_uv_scale', 'parent': 'import_uv_root', 'pivot': [0, 0, 0]}]
        height = 2 ** math.ceil(math.log2(py + row + 2))
        assert height <= 8192, (sid, height)
        for name, geo in models.items():
            desc = geo['minecraft:geometry'][0]['description']
            desc['texture_width'], desc['texture_height'] = width, height
            files[name] = json.dumps(geo).encode()
        for name in list(files):
            if not name.startswith(prefix): continue
            if '/textures/pokemon/' in name and name.endswith('.png'):
                original = Image.open(io.BytesIO(files[name])).convert('RGBA')
                atlas = Image.new('RGBA', (width, height))
                for source, target in tiles:
                    for face, (u, v, w, h) in target.items():
                        if not w or not h or face not in source: continue
                        su, sv, sw, sh = source[face]
                        left, top = math.floor(min(u, u+w)), math.floor(min(v, v+h))
                        right, bottom = math.ceil(max(u, u+w)), math.ceil(max(v, v+h))
                        # Affine sampling preserves orientation, negative UV extents and fractional source coordinates.
                        patch = original.transform((right-left, bottom-top), Image.Transform.AFFINE,
                            (sw/w, 0, su+(left-u)*sw/w, 0, sh/h, sv+(top-v)*sh/h), Image.Resampling.NEAREST)
                        atlas.paste(patch, (left, top))
                buf = io.BytesIO(); atlas.save(buf, format='PNG'); files[name] = buf.getvalue()
            elif '/animations/' in name and name.endswith('.json'):
                obj = json.loads(files[name])
                for clip in obj['animations'].values():
                    for channels in clip.get('bones', {}).values():
                        if 'position' in channels: channels['position'] = scaled(channels['position'])
                files[name] = json.dumps(obj).encode()
            elif '/posers/' in name and name.endswith('.json'):
                obj = json.loads(files[name]); obj['rootBone'] = 'import_uv_root'
                for pose in obj['poses'].values():
                    pose['transformTicks'] = 0
                    pose.setdefault('transformedParts', []).append({'part': 'import_uv_scale', 'scale': [1/SCALE]*3})
                files[name] = json.dumps(obj).encode()
        report[sid] = {'models': len(models), 'cubes': len(tiles), 'atlas': [width, height], 'scale_compensated': True}
    return report
