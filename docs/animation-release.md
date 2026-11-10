# Unyielding 1.1.9 build and verification

This release incorporates the approved ZIP trial into the combined JAR. Its base is the verified Unyielding 1.1.8 archive from Server-Jars commit `acb997103be64316cbc0897f158be1f5e8da8f1d`, SHA256 `d3d560525f810a340fe2c634a1bc3f4c7a14883dadba1926b1d7c29478bcbbcc`.

The assembler replaces the Mawonna component and requested pack assets while checking that unrelated modules remain byte-identical. It preserves all nine existing mod IDs and restores riding locators omitted by the standalone ZIP edits. Native Ghost Jirachi uses native-scale locator coordinates. The resource and data pack are embedded; an external Mawonna ZIP is unnecessary and can override the new models.

## Local build

Requirements: Python 3.11 or newer, JDK 21, Gradle 8.14.3, network access for missing pinned dependencies. From the repository root in PowerShell, set `JAVA_HOME` to the installed JDK and add its `bin` directory to `PATH`.

```powershell
$env:UNYIELDING_LOCAL_BUILD = 'true'
$env:UNYIELDING_GRADLE = 'gradle.bat'
# Optional: reuse matching installed dependencies rather than download them.
$env:UNYIELDING_LOCAL_MODS = 'C:/path/to/test-profile/mods'
python tools/build_animation_release.py
```

Gradle uses `build --parallel --max-workers=12 --build-cache`. The assembler checks dependency hashes, module metadata, archive uniqueness, JSON validity, admin moves, names and type order. Outputs are under `dist/animation-release/`.

## Local checks

Despite their historical filenames, these helpers run on the current PC when `UNYIELDING_LOCAL_BUILD=true`. They create isolated test installations under the repository, not inside a player's profile. They do not issue commands to an existing world.

```powershell
$env:UNYIELDING_BUNDLED_PACK = 'true'
$env:UNYIELDING_ANIMATED_RELEASE = 'true'
python tools/cloud_client_smoke.py
python tools/cloud_runtime_smoke.py --pack Mawonna.zip --eula-file source/runtime/eula.txt --unyielding dist/animation-release/Unyielding-1.1.9.jar --species mawonna mawonna_flower covislight fairy_sliggoo inkyeon lunavee buzzeon ghost_jirachi creepyeon
```

The server check reuses the supplied EULA acceptance file. It exercises normal/shiny spawning, progression, marriage and meal counters, persistence and exact Ghost Jirachi health boundaries. The client check loads the actual combined JAR, renders model/portrait snapshots and checks movement transitions. It uses small title-screen species fixtures for GUI scale lookup; full gameplay definitions load on the server.

The map check draws actual atlas frames using the production frame selection helper and loads the Xaero 26.5.0 mixin. It does not replay a live in-world radar session. Full-pack play, riding placement under player control and multiplayer gameplay still need user testing.

No hosted build was used for this release. `animated-release.yml` is available only by manual dispatch for a future request.

## Visual behavior

- Mawonna and Mawonna Flower: animated face/upper-chest portrait, animated full-body map icon; 30 source frames, 2.4-second loop.
- Inkyeon: still face portrait from the clean supplied PNG; animated right-facing full-body map icon; 20 source frames, 3.6-second loop. Transparent edges contain no screenshot text.
- Normal and shiny forms share these supplied icon images. Their existing 3D model textures remain separate.
- Ghost Jirachi: native Jirachi model and clips recolored purple; native-style purple map icon. The eye opens for `2 * HP < maxHP` and closes at half or above, including after recall/healing.
- Frilligoo: original Sliggoo eyes/mouth and restored UVs, no added painted smile, Dragon/Fairy order. Existing saved ID `fairy_sliggoo` is preserved.
- Inkyeon rear joints remain stationary, with approved front-leg/body animation and prior eye/tear/heart-bow fixes retained.
- Mawonna's approved hands, sleeves, animation loops and two cry variants are embedded from the trial ZIP.

Mawonna Flower remains command-only with separate admin stats (HP 155 / Attack 165 / Defense 200 / Sp. Atk 5 / Sp. Def 150 / Speed 30). It has 862 eligible level-one moves after removing Z/Max/G-Max, one-PP and malformed/internal entries. Public Mawonna remains HP 100 / Attack 120 / Defense 120 / Sp. Atk 10 / Sp. Def 100 / Speed 10, with 141 moves. Each Pokemon has one active ability at a time.
