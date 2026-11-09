"""Ensure custom riding definitions can resolve a real ``middle`` locator.

Run after the original-resource preservation checks, before convert_imported_uv.
This pass only adds locators to existing bones. It does not change model shapes,
pivots, hierarchy or existing locators. Imported UV conversion scales newly
added Ghost Jirachi locators together with the rest of its geometry. Creepyeon
and Corvislight are already authored at 16x, so their stored coordinates below
are already multiplied by 16.
"""

import copy
import json
import math


MODELS = {
    "mawonna": (
        "assets/mawile_custom/bedrock/pokemon/models/mawile_third_evolution/mawile_third_evolution.geo.json",
    ),
    "mawonna_flower": (
        "assets/mawile_custom/bedrock/pokemon/models/mawile_third_evolution/mawile_third_evolution.geo.json",
    ),
    "covislight": (
        "assets/mawonna/bedrock/pokemon/models/pink_corvisquire/pink_corvisquire.geo.json",
    ),
    "fairy_sliggoo": (
        "assets/mawonna/bedrock/pokemon/models/fairy_sliggoo/fairy_sliggoo.geo.json",
    ),
    "inkyeon": (
        "assets/mawonna/bedrock/pokemon/models/inkyeon/inkyeon.geo.json",
    ),
    "lunavee": (
        "assets/lunavee/bedrock/pokemon/models/lunavee/lunavee.geo.json",
    ),
    "buzzeon": tuple(
        f"assets/buzzeon/bedrock/pokemon/models/buzzeon_{sex}.geo.json"
        for sex in ("male", "female")
    ),
    "creepyeon": tuple(
        f"assets/creepyeon/bedrock/pokemon/models/creepyeon_{sex}.geo.json"
        for sex in ("male", "female")
    ),
    "ghost_jirachi": tuple(
        f"assets/uwu/bedrock/pokemon/models/ghost_jirachi/ghost_jirachi_{sex}.geo.json"
        for sex in ("male", "female")
    ),
}

# Model-space coordinates: at the shell's upper surface, the quadruped's
# upper back (15, 1.5 before its 16x export), and behind Jirachi's upper torso.
MISSING_SEATS = {
    "fairy_sliggoo": ("fairy_pearl_shell", [0, 16.5, 2.4]),
    "creepyeon": ("locator_middle", [0, 240, 24]),
    "ghost_jirachi": ("torso", [0, 13, 2.5]),
}


def _offset(locator):
    value = locator.get("offset") if isinstance(locator, dict) else locator
    if not (
        isinstance(value, list)
        and len(value) == 3
        and all(isinstance(v, (int, float)) and math.isfinite(v) for v in value)
    ):
        raise ValueError(f"Invalid riding locator: {locator!r}")
    return value


def apply(files):
    """Add missing middle locators in place and return a per-species report."""
    report = {}
    processed = {}
    for sid, paths in MODELS.items():
        report[sid] = []
        for path in paths:
            if path in processed:
                report[sid].append(copy.deepcopy(processed[path]))
                continue
            model = json.loads(files[path])
            geometries = model["minecraft:geometry"]
            if len(geometries) != 1:
                raise ValueError(f"Expected one geometry in {path}")
            bones = geometries[0]["bones"]
            by_name = {bone["name"]: bone for bone in bones}
            found = [bone for bone in bones if "middle" in bone.get("locators", {})]
            if len(found) > 1:
                raise ValueError(f"Ambiguous middle locators in {path}")
            if found:
                bone = found[0]
                action = "retained"
            elif sid == "covislight":
                # Reuse the previously authored riding seat, with the name used
                # by the shared custom-species riding definitions.
                seats = [bone for bone in bones if "seat_1" in bone.get("locators", {})]
                if len(seats) != 1:
                    raise ValueError(f"Expected Corvislight's existing seat_1 in {path}")
                bone = seats[0]
                bone["locators"]["middle"] = copy.deepcopy(bone["locators"]["seat_1"])
                action = "aliased_seat_1"
            else:
                if sid not in MISSING_SEATS:
                    raise ValueError(f"Expected an existing middle locator in {path}")
                bone_name, point = MISSING_SEATS[sid]
                bone = by_name[bone_name]
                bone.setdefault("locators", {})["middle"] = list(point)
                action = "added"
            point = _offset(bone["locators"]["middle"])
            entry = {"model": path, "bone": bone["name"], "offset": point, "action": action}
            if action != "retained":
                files[path] = (json.dumps(model, indent=2) + "\n").encode()
            processed[path] = entry
            report[sid].append(copy.deepcopy(entry))
    return report
