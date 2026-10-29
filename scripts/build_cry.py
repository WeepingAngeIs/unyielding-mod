#!/usr/bin/env python3
"""Build the short Mawile Unyielding cry and patch only its audio wiring.

Requires Python 3, numpy, scipy, and FFmpeg with Rubber Band and libvorbis.
Run from any directory: python scripts/build_cry.py
The checked-in original is used locally; no network or account access is needed.
"""

from __future__ import annotations

import hashlib
import json
import subprocess
import zipfile
from pathlib import Path

import numpy as np
from scipy.signal import resample_poly


ROOT = Path(__file__).resolve().parents[1]
AUDIO = ROOT / "source" / "audio"
ARCHIVE = ROOT / "MawileUnyielding_Cobblemon_Addon.zip"
RATE = 48_000
DURATION = 1.5
SAMPLES = 72_000
SOURCE_SHA256 = "42dd909e73a7677cbd3158a14a1ce521ec6f295b4e425acb405f8a2c56ace3eb"
UPSTREAM_COMMIT = "f4140a097d9426753a5537c7d3263dd1e8e98434"
UPSTREAM_URL = (
    "https://gitlab.com/cable-mc/cobblemon/-/raw/" + UPSTREAM_COMMIT
    + "/common/src/main/resources/assets/cobblemon/sounds/pokemon/mawile/mawile_cry.ogg"
)
SOUND_PATH = "assets/cobblemon/sounds/pokemon/mawileunyielding/mawileunyielding_cry.ogg"
ANIMATION_PATH = (
    "assets/cobblemon/bedrock/pokemon/animations/0303_mawileunyielding/"
    "mawileunyielding.animation.json"
)
POSER_PATH = (
    "assets/cobblemon/bedrock/pokemon/posers/0303_mawileunyielding/mawileunyielding.json"
)
EVENT = "pokemon.mawileunyielding.cry"


def ffmpeg(args: list[str], audio: np.ndarray | None = None) -> subprocess.CompletedProcess:
    return subprocess.run(
        ["ffmpeg", "-nostdin", "-hide_banner", "-y", *args],
        input=None if audio is None else np.asarray(audio, dtype="<f4").tobytes(),
        capture_output=True,
        check=True,
    )


def raw_input() -> list[str]:
    return ["-f", "f32le", "-ar", str(RATE), "-ac", "1", "-i", "pipe:0"]


def decode(path: Path) -> np.ndarray:
    result = ffmpeg([
        "-v", "error", "-i", str(path), "-ac", "1",
        "-af", f"aresample={RATE}:resampler=soxr:precision=28",
        "-f", "f32le", "pipe:1",
    ])
    return np.frombuffer(result.stdout, dtype="<f4").astype(np.float64)


def shape_phrase(audio: np.ndarray, tempo: float, semitones: float,
                 fade_in: float, fade_out: float) -> np.ndarray:
    pitch = 2 ** (semitones / 12)
    filters = (
        f"rubberband=tempo={tempo}:pitch={pitch:.10f}:transients=smooth:"
        "detector=soft:formant=preserved:pitchq=quality,"
        "highpass=f=70:p=2,"
        "equalizer=f=2700:t=q:w=0.8:g=-2.5,"
        "highshelf=f=5000:g=-3,"
        "lowpass=f=9000:p=2"
    )
    output = ffmpeg(["-v", "error", *raw_input(), "-af", filters,
                     "-f", "f32le", "pipe:1"], audio)
    result = np.frombuffer(output.stdout, dtype="<f4").astype(np.float64)
    n_in, n_out = round(RATE * fade_in), round(RATE * fade_out)
    result[:n_in] *= 0.5 - 0.5 * np.cos(np.linspace(0, np.pi, n_in))
    result[-n_out:] *= 0.5 + 0.5 * np.cos(np.linspace(0, np.pi, n_out))
    return result


def meter(audio: np.ndarray) -> dict:
    result = ffmpeg([
        *raw_input(), "-af", "loudnorm=I=-20:TP=-6:LRA=7:print_format=json",
        "-f", "null", "-",
    ], audio)
    log = result.stderr.decode("utf-8", errors="replace")
    data = json.loads(log[log.rfind("{"):])
    return {
        "integrated_lufs": float(data["input_i"]),
        "true_peak_dbtp": float(data["input_tp"]),
    }


def json_bytes(value: dict) -> bytes:
    return (json.dumps(value, indent=2, ensure_ascii=False) + "\n").encode("utf-8")


def patch_archive(sound: bytes) -> dict:
    with zipfile.ZipFile(ARCHIVE) as current:
        infos = current.infolist()
        original = {info.filename: current.read(info.filename) for info in infos}
    assert len(infos) == len(original), "Archive contains duplicate paths"
    assets = dict(original)
    sounds = json.loads(assets.get("assets/cobblemon/sounds.json", b"{}"))
    sounds[EVENT] = {"sounds": [{
        "name": "cobblemon:pokemon/mawileunyielding/mawileunyielding_cry",
        "volume": 1.0,
    }]}
    assets["assets/cobblemon/sounds.json"] = json_bytes(sounds)
    assets[SOUND_PATH] = sound

    animations = json.loads(assets[ANIMATION_PATH])
    cry = animations["animations"]["animation.mawileunyielding.cry"]
    cry["loop"] = False
    cry["animation_length"] = max(DURATION, float(cry.get("animation_length", 0)))
    effects = cry.setdefault("sound_effects", {})
    # Idempotent: retain unrelated effects and install this cry once at its onset.
    for key in list(effects):
        if effects[key].get("effect") == EVENT:
            del effects[key]
    effects["0.0"] = {"effect": EVENT}
    assets[ANIMATION_PATH] = json_bytes(animations)

    poser = json.loads(assets[POSER_PATH])
    poser["animations"]["cry"] = "q.bedrock_stateful('mawileunyielding', 'cry')"
    assets[POSER_PATH] = json_bytes(poser)

    allowed = {"assets/cobblemon/sounds.json", SOUND_PATH, ANIMATION_PATH, POSER_PATH}
    assert all(assets[path] == value for path, value in original.items() if path not in allowed)
    for name, content in assets.items():
        if name.endswith((".json", ".mcmeta")):
            json.loads(content)
    species = json.loads(assets["data/cobblemon/species/custom/mawileunyielding.json"])
    assert species["maleRatio"] == 0.0

    temporary = ARCHIVE.with_suffix(".tmp.zip")
    with zipfile.ZipFile(temporary, "w") as archive:
        for info in infos:
            archive.writestr(info, assets[info.filename])
        for path in sorted(assets.keys() - original.keys()):
            info = zipfile.ZipInfo(path, date_time=(2026, 10, 29, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, assets[path])
    with zipfile.ZipFile(temporary) as archive:
        assert archive.testzip() is None
        assert archive.read(SOUND_PATH) == sound
    temporary.replace(ARCHIVE)
    return {
        "sound_event": "cobblemon:" + EVENT,
        "sound_path": SOUND_PATH,
        "changed_entries": sorted(p for p in original if original[p] != assets[p]),
        "added_entries": sorted(assets.keys() - original.keys()),
        "archive_sha256": hashlib.sha256(ARCHIVE.read_bytes()).hexdigest(),
    }


def main() -> None:
    source = AUDIO / "mawile_original.ogg"
    assert hashlib.sha256(source.read_bytes()).hexdigest() == SOURCE_SHA256
    original = decode(source)

    # A short opening excerpt followed by a longer, softer answer. This avoids
    # stretching the complete 0.69-second original more than twofold, and avoids
    # turning the cry into a repeated full-sample loop.
    opening = shape_phrase(original[round(0.006 * RATE):round(0.340 * RATE)],
                           tempo=0.86, semitones=0.70, fade_in=0.012, fade_out=0.045)
    answer = shape_phrase(original, tempo=0.69, semitones=0.10,
                          fade_in=0.020, fade_out=0.130)
    mixed = np.zeros(SAMPLES, dtype=np.float64)
    mixed[:len(opening)] += 0.78 * opening
    answer_start = round(0.430 * RATE)
    assert answer_start + len(answer) <= SAMPLES
    mixed[answer_start:answer_start + len(answer)] += answer
    # Preserve a clean, quiet ending and exactly 1.5 seconds of decoded PCM.
    mixed[-round(0.030 * RATE):] = 0
    measured = meter(mixed)
    gain_db = min(-20.0 - measured["integrated_lufs"], -6.0 - measured["true_peak_dbtp"])
    mixed *= 10 ** (gain_db / 20)

    master = AUDIO / "mawileunyielding_cry.wav"
    delivery = AUDIO / "mawileunyielding_cry.ogg"
    ffmpeg(["-v", "error", *raw_input(), "-c:a", "pcm_s24le", "-map_metadata", "-1",
            "-fflags", "+bitexact", "-flags:a", "+bitexact", str(master)], mixed)
    ffmpeg(["-v", "error", "-i", str(master), "-c:a", "libvorbis", "-q:a", "7",
            "-map_metadata", "-1", "-fflags", "+bitexact", "-flags:a", "+bitexact",
            str(delivery)])
    decoded = decode(delivery)
    encoded_meter = meter(decoded)
    oversampled_peak = np.max(np.abs(resample_poly(decoded, 4, 1)))
    assert len(decoded) == SAMPLES, f"Expected 72000 samples, found {len(decoded)}"
    assert np.all(np.isfinite(decoded))
    assert np.max(np.abs(decoded)) < 1.0
    assert encoded_meter["true_peak_dbtp"] <= -5.5
    # The conservative peak ceiling may leave this short transient sound a
    # little quieter than the loudness target; preserve dynamics rather than
    # compressing it merely to hit a meter value.
    assert -22.0 <= encoded_meter["integrated_lufs"] <= -19.5
    assert np.max(np.abs(decoded[-round(0.01 * RATE):])) < 1e-4
    pack = patch_archive(delivery.read_bytes())
    report = {
        "source_url": UPSTREAM_URL,
        "source_sha256": SOURCE_SHA256,
        "source_metrics": meter(original),
        "duration_seconds": len(decoded) / RATE,
        "decoded_samples": len(decoded),
        "sample_rate_hz": RATE,
        "channels": 1,
        "master": "24-bit PCM WAV",
        "delivery": "Ogg Vorbis quality 7",
        "final_metrics": encoded_meter,
        "sample_peak_dbfs": float(20 * np.log10(np.max(np.abs(decoded)))),
        "fourfold_oversampled_peak_dbfs": float(20 * np.log10(oversampled_peak)),
        "clipped_samples": int(np.count_nonzero(np.abs(decoded) >= 1.0)),
        "final_10ms_peak_dbfs": float(20 * np.log10(max(np.max(np.abs(decoded[-480:])), 1e-15))),
        "ogg_sha256": hashlib.sha256(delivery.read_bytes()).hexdigest(),
        "processing": {
            "opening_source_seconds": [0.006, 0.340],
            "opening_tempo": 0.86,
            "opening_pitch_semitones": 0.70,
            "answer_start_seconds": 0.430,
            "answer_tempo": 0.69,
            "answer_pitch_semitones": 0.10,
            "formants": "preserved",
            "upper_mid_eq": "-2.5 dB at 2.7 kHz, Q 0.8",
            "high_shelf": "-3 dB above 5 kHz",
            "low_pass_hz": 9000,
            "reverb": False,
            "hard_limiting": False,
            "sounds_json_volume": 1.0,
        },
        "pack": pack,
        "minecraft_runtime_tested": False,
    }
    (ROOT / "docs" / "MAWILE_CRY_QA.json").write_bytes(json_bytes(report))
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
