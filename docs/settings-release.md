# Unyielding 1.1.10 build and validation

Local edits and asset preparation are followed by heavy builds/game tests on GitHub-hosted runners. The tested artifact is published unchanged.

[Successful hosted run](https://github.com/WeepingAngeIs/unyielding-mod/actions/runs/42884115072). SHA256 `802006a6a784bea663e7aa487fd98933fe30d8e6a2fbfb313301437a069798c3`.

`.github/workflows/settings-release.yml` checks out this repository and the exact Server-Jars source commit, then runs `tools/build_settings_release.py`. It builds quest 1.6.9 and Mawonna 1.0.2 using Gradle 8.14.3 / Java 21 with `build --parallel --max-workers=12 --build-cache`. The assembler replaces only those owned components in verified Unyielding 1.1.9 and asserts that species, models, animations and existing portrait PNGs remain unchanged.

The isolated test-only helper from `Server-Jars/settings-update` is built against the final JAR and loaded only in the disposable server. It is never bundled. OPAC 0.33.0 is installed there to exercise actual claim protection. Server checks include 227 settings assertions, normal/shiny spawning and the existing progression/exact-HP self-test. Client checks render nine screenshots and verify settings receipt/reset, map geometry/selection, portrait clocks and existing model/animation behavior. The 28 unit tests also pass.

Run the **Build and verify Unyielding settings release** workflow on GitHub to reproduce. The build helper intentionally rejects non-GitHub execution for this release. The supplied accepted EULA file is reused by the disposable server. Dependency versions and downloaded hashes are checked. Outputs are `Unyielding-1.1.10-verified` and `Unyielding-1.1.10-evidence` workflow artifacts.

[Full validation and limits](https://github.com/WeepingAngeIs/Server-Jars/blob/main/releases/Unyielding-1.1.10-Validation.txt). These checks cover focused dependencies and fixtures, not the complete user modpack or live inventory/minimap play. The resulting file needs installation on both server and clients and a full restart.
