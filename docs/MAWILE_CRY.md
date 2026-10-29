# Mawile Unyielding: short custom cry

Created October 29, 2026 for the user's female-only third-evolution design.

## Sound

This is an edit of the original Mawile cry distributed with Cobblemon. A short
opening excerpt leads into a longer, slightly lower-pitched answer. The edit is
exactly **1.5 seconds**, including its short, quiet ending. The pitch changes are
small (+0.70 and +0.10 semitones), with formants preserved. A gentle upper-mid
cut and high shelf reduce sharpness. There are no added chimes, voice layers,
reverb, or hard limiting.

The first phrase uses 0.006–0.340 seconds of the source at tempo 0.86. The answer
uses the source at tempo 0.69 and begins at 0.430 seconds. Raised-cosine fades
smooth both phrases. The master is produced in floating point, then saved as
24-bit PCM WAV; the game's copy is encoded once from that master to mono Ogg
Vorbis at quality 7. Both outputs are 48 kHz. The original source is already
Vorbis; the WAV preserves this edit without another lossy encoding step, but
does not restore detail absent from the source.

Files:

- `source/audio/mawile_original.ogg`: untouched source for reproducible editing.
- `source/audio/mawileunyielding_cry.wav`: lossless master of the finished edit.
- `source/audio/mawileunyielding_cry.ogg`: exact audio included in the addon ZIP.
- `scripts/build_cry.py`: audio recipe and scoped ZIP patcher.
- `docs/MAWILE_CRY_QA.json`: machine-readable measurements and checksums.

## Validation

Measured on the decoded final Ogg, including the encoder's effects:

| Property | Result |
| --- | --- |
| Duration | 1.500000 seconds |
| Samples | 72,000 |
| Channels / sample rate | Mono / 48,000 Hz |
| Integrated loudness | -21.16 LUFS |
| True peak | -6.21 dBTP |
| Clipped samples | 0 |
| Final 10 milliseconds | Digital silence |

The conservative peak ceiling leaves the sound slightly quieter than the
-20 LUFS target. No compressor was added simply to raise that measurement.
The `sounds.json` volume is 1.0; loudness control is baked into the audio.

The ZIP passes CRC validation and every JSON file parses. The sound event,
animation keyframe, audio path, and stateful cry hook resolve consistently.
All seven unrelated original ZIP entries are byte-for-byte unchanged. The
species remains female-only (`maleRatio: 0.0`); its statistics are unchanged.
The original Mawile event and assets are not overridden.

These are audio-signal and structural checks. Subjective listening approval
and actual Minecraft playback have not been established in this environment.

## Installed wiring

- Audio: `assets/cobblemon/sounds/pokemon/mawileunyielding/mawileunyielding_cry.ogg`
- Sound event: `cobblemon:pokemon.mawileunyielding.cry`
- Cry animation: non-looping, length 1.5 seconds, one sound keyframe at 0.0.
- Poser cry hook: `q.bedrock_stateful('mawileunyielding', 'cry')`.

The original cry bone keyframes remain unchanged. Only its duration and audio
event were updated; all other animations and poser hooks remain unchanged.

## Install and test

1. Replace the previous `MawileUnyielding_Cobblemon_Addon.zip` with the updated
   ZIP from the repository root. Do not enable both old and new copies.
2. Update the world/server datapack copy and each client's enabled resource
   pack copy, or update the server resource pack served to clients.
3. Reload client resources with F3+T or restart the client. A server-only
   datapack replacement cannot supply the client audio by itself.
4. With cheats or operator permission, test the sound directly:

   ```mcfunction
   /playsound cobblemon:pokemon.mawileunyielding.cry neutral @s ~ ~ ~ 1 1
   ```

5. Give yourself the custom species with `/pokegive mawileunyielding`, then
   send it out to check the actual animated cry.

## Rebuild

Install Python 3 with `numpy` and `scipy`, plus FFmpeg built with Rubber Band,
libsoxr, and libvorbis. From the repository:

```sh
python scripts/build_cry.py
```

The script verifies the source audio checksum, rebuilds the WAV and Ogg,
checks the encoded sound, updates the root addon ZIP, and writes the QA JSON.
It preserves unrelated ZIP entries and can be rerun after exporting a new
model/animation pack. Run it after a fresh Blockbench export to reapply the
cry event and stateful hook; this sound-only update does not edit the
Blockbench model source.

## Provenance and integration references

Original audio is from the official Cobblemon repository, pinned to revision
`f4140a097d9426753a5537c7d3263dd1e8e98434`:

[Original Mawile cry](https://gitlab.com/cable-mc/cobblemon/-/blob/f4140a097d9426753a5537c7d3263dd1e8e98434/common/src/main/resources/assets/cobblemon/sounds/pokemon/mawile/mawile_cry.ogg)

Original SHA-256:
`42dd909e73a7677cbd3158a14a1ce521ec6f295b4e425acb405f8a2c56ace3eb`.
Original Git blob SHA:
`9f28d93ff6889ad43ce4ddef62167fc33a3bfe3f`.

The sound routing follows the official
[adding sounds tutorial](https://wiki.cobblemon.com/index.php/Tutorials/Adding_Sounds_To_Animations),
[Mawile animation](https://gitlab.com/cable-mc/cobblemon/-/blob/f4140a097d9426753a5537c7d3263dd1e8e98434/common/src/main/resources/assets/cobblemon/bedrock/pokemon/animations/0303_mawile/mawile.animation.json),
and [Mawile poser](https://gitlab.com/cable-mc/cobblemon/-/blob/f4140a097d9426753a5537c7d3263dd1e8e98434/common/src/main/resources/assets/cobblemon/bedrock/pokemon/posers/0303_mawile/mawile.json).
The original cry remains attributable to its original rights holders; this
update documents the derivative edit and adds no ownership claim over the
source audio.
