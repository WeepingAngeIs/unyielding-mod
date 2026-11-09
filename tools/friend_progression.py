"""Replace the four friends' testing learnsets with explicit level progression.

Only numeric learnset entries are rescheduled. TM, tutor, egg, legacy and
special entries remain unchanged, as do every other species property.
"""
import json


LEVEL_GROUPS = {
    'lunavee': {
        1: 'quickattack babydolleyes helpinghand bite',
        5: 'bubble stringshot constrict',
        8: 'mudshot cottonspore charm',
        11: 'icywind pounce feint',
        14: 'swift aquajet iceshard',
        17: 'flamecharge trailblaze lowsweep',
        20: 'electroweb bubblebeam rocktomb',
        23: 'agility thunderwave yawn',
        26: 'rapidspin accelerock machpunch',
        29: 'silverwind ominouswind ancientpower',
        32: 'moonlight wish protect',
        35: 'bulldoze grasspledge secretpower',
        38: 'firefang icefang thunderfang',
        40: 'tailwind batonpass uturn',
        42: 'rockpolish autotomize tidyup',
        44: 'glare stunspore scaryface',
        46: 'crunch psychicfangs facade',
        48: 'electroball gyroball speedswap',
        50: 'moonblast playrough cosmicpower',
        52: 'psychic psyshock shadowball',
        54: 'bulletpunch shadowsneak suckerpunch',
        56: 'syrupbomb tarshot toxicthread',
        58: 'stickyweb silktrap venomdrench',
        60: 'calmmind dragondance quiverdance',
        62: 'afteryou quash trickroom',
        64: 'bodyslam doubleedge substitute',
        66: 'drumbeating esperwing aquastep',
        68: 'scaleshot hammerarm icehammer',
        70: 'shiftgear shellsmash filletaway',
        72: 'victorydance noretreat curse',
        74: 'bleakwindstorm glaciate spinout',
        76: 'orderup aurawheel healingwish',
        78: 'extremespeed lastresort',
        80: 'geomancy clangoroussoul vcreate',
    },
    'buzzeon': {
        1: 'tackle bugbite poisonsting stringshot',
        5: 'quickattack babydolleyes',
        8: 'strugglebug helpinghand',
        11: 'furycutter bite',
        14: 'pounce charm',
        17: 'twineedle infestation',
        20: 'aerialace pinmissile',
        23: 'silverwind signalbeam',
        26: 'roost protect',
        29: 'steamroller spiderweb',
        32: 'acrobatics dualwingbeat',
        35: 'poisonjab fellstinger',
        38: 'uturn skittersmack',
        41: 'tailwind agility',
        44: 'lunge xscissor',
        47: 'pollenpuff wish',
        50: 'crunch knockoff',
        53: 'crosspoison drillrun',
        56: 'stickyweb toxic',
        59: 'ragepowder powder',
        62: 'defendorder healorder',
        65: 'attackorder firstimpression',
        68: 'leechlife bugbuzz',
        71: 'swordsdance quiverdance',
        74: 'substitute silktrap',
        77: 'doubleedge tailglow',
        80: 'megahorn',
    },
}

# These exports already contain genuine level-up schedules. Move only their
# excess level-one entries; retain the established higher-level milestones.
LEVEL_ONE_REPLACEMENTS = {
    'ghost_jirachi': {
        'wish': 1, 'confusion': 1, 'astonish': 1, 'confuseray': 1,
        'refresh': 10, 'luckychant': 15,
    },
    'creepyeon': {
        'tackle': 1, 'tailwhip': 1, 'growl': 1, 'bite': 1,
        'helpinghand': 6, 'confusion': 8, 'covet': 12,
        'copycat': 18, 'refresh': 22, 'charm': 26,
        'batonpass': 32, 'takedown': 38, 'trumpcard': 48,
        'doubleedge': 60,
    },
}


def schedule(sid):
    result = {}
    for level, words in LEVEL_GROUPS[sid].items():
        for move in words.split():
            if move in result:
                raise AssertionError(('Duplicate curated move', sid, move))
            result[move] = level
    return result


def apply(files):
    """Update only four species' moves and return a concise validation report."""
    report = {}
    for sid in (*LEVEL_GROUPS, *LEVEL_ONE_REPLACEMENTS):
        path = f'data/cobblemon/species/custom/{sid}.json'
        species = json.loads(files[path])
        original = list(species['moves'])
        level_moves = [entry for entry in original if entry.split(':', 1)[0].isdigit()]
        other_moves = [entry for entry in original if not entry.split(':', 1)[0].isdigit()]
        if sid in LEVEL_GROUPS:
            levels = schedule(sid)
            assert set(levels) == {entry.split(':', 1)[1] for entry in level_moves}, ('Curated list differs from export', sid)
            updated = [(levels[entry.split(':', 1)[1]], entry.split(':', 1)[1]) for entry in level_moves]
        else:
            replacements = LEVEL_ONE_REPLACEMENTS[sid]
            updated = []
            for entry in level_moves:
                level, move = entry.split(':', 1)
                if int(level) == 1:
                    assert move in replacements, ('Unreviewed level-one entry', sid, move)
                    level = replacements[move]
                updated.append((int(level), move))
        updated.sort(key=lambda pair: (pair[0], pair[1]))
        species['moves'] = [f'{level}:{move}' for level, move in updated] + other_moves
        assert len([level for level, _ in updated if level == 1]) == 4, sid
        assert {entry.split(':', 1)[1] for entry in original} == {entry.split(':', 1)[1] for entry in species['moves']}, sid
        assert len(species['moves']) == len(original), sid
        files[path] = (json.dumps(species, indent=2, ensure_ascii=False) + '\n').encode()
        report[sid] = {
            'level_one_moves': [move for level, move in updated if level == 1],
            'level_up_entries': len(updated),
            'last_level_up': max(level for level, _ in updated),
            'non_level_entries_preserved': len(other_moves),
            'full_move_membership_preserved': True,
        }
    return report
