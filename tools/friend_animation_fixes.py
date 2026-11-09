"""Normalize exported scale keyframes for Cobblemon 1.8.4's actual parser.

BedrockAnimationAdapter.deserializeKeyframe accepts a vector array or a
pre/post object. It rejects scalar timestamp values, even though constant
scalar scale channels are supported. Lunavee's four orbiting stars use 484
scalar scale keyframes; one rejected frame prevents its entire animation
group from loading. Expand each uniform scale to [s, s, s] without changing
the authored time, interpolation, visibility or motion.

Run after original-import validation and before imported UV conversion.
"""

import json
import math


PREFIXES = tuple(
    f"assets/{namespace}/bedrock/pokemon/animations/"
    for namespace in ("lunavee", "buzzeon", "uwu", "creepyeon")
)


def _is_scalar(value):
    return isinstance(value, (int, float, str)) and not isinstance(value, bool)


def _vector(value, channel, location, report):
    if _is_scalar(value) and channel == "scale":
        report.append(location)
        return [value, value, value]
    if not (isinstance(value, list) and len(value) == 3 and all(_is_scalar(v) for v in value)):
        raise ValueError(f"Unsupported animation keyframe vector at {location}: {value!r}")
    return value


def apply(files):
    """Normalize scalar scale keyframes in place and report every changed frame."""
    report = {}
    for path in sorted(files):
        if not path.startswith(PREFIXES) or not path.endswith(".json"):
            continue
        document = json.loads(files[path])
        changes = []
        for animation_name, animation in document["animations"].items():
            for bone_name, channels in animation.get("bones", {}).items():
                for channel in ("position", "rotation", "scale"):
                    timeline = channels.get(channel)
                    if not isinstance(timeline, dict):
                        # A constant vector or scalar is parsed through a
                        # different branch and must not become a time map.
                        continue
                    for timestamp, frame in list(timeline.items()):
                        if not math.isfinite(float(timestamp)):
                            raise ValueError(f"Invalid keyframe timestamp in {path}: {timestamp}")
                        location = f"{animation_name}/{bone_name}/{channel}/{timestamp}"
                        if isinstance(frame, dict):
                            slots = [slot for slot in ("pre", "post") if slot in frame]
                            if not slots:
                                raise ValueError(f"Keyframe missing pre/post at {location}")
                            for slot in slots:
                                frame[slot] = _vector(frame[slot], channel, location + "/" + slot, changes)
                        else:
                            timeline[timestamp] = _vector(frame, channel, location, changes)
        if changes:
            files[path] = (json.dumps(document, indent=2) + "\n").encode()
        report[path] = {"expanded_uniform_scale_keyframes": len(changes), "keyframes": changes}
    return report
