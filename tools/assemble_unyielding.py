"""Cloud-only assembly: preserve the pinned Unyielding modules and append Mawonna."""
import argparse, hashlib, json, os, re, tomllib, zipfile
from pathlib import Path

if os.environ.get("GITHUB_ACTIONS") != "true" or os.environ.get("RUNNER_ENVIRONMENT") != "github-hosted":
    raise SystemExit("Builds are restricted to GitHub-hosted runners")
parser = argparse.ArgumentParser()
for arg in ("base", "module", "output"): parser.add_argument("--"+arg, required=True)
a = parser.parse_args()
base, module, output = map(Path, (a.base,a.module,a.output))
meta = json.loads(base.with_suffix(".json").read_text())
assert hashlib.sha256(base.read_bytes()).hexdigest() == meta["sha256"], "Unyielding base changed"
with zipfile.ZipFile(base) as z: original = {n:z.read(n) for n in z.namelist() if not n.endswith("/")}
with zipfile.ZipFile(module) as z: addition = {n:z.read(n) for n in z.namelist() if not n.endswith("/")}
assert not any(re.search(r"META-INF/[^/]+\.(SF|RSA|DSA)$", n) for n in original), "Signed base requires source rebuild"
path = "META-INF/neoforge.mods.toml"
base_toml = original[path].decode("utf-8")
module_toml = addition.pop(path).decode("utf-8")
assert "${" not in module_toml, "Module metadata has unresolved version variables"
start = module_toml.index("[[mods]]")
merged = base_toml.rstrip()+"\n\n# Mawonna evolution extension, built and checked on GitHub.\n"+module_toml[start:]
parsed = tomllib.loads(merged)
ids = [m["modId"] for m in parsed["mods"]]
assert len(ids) == len(set(ids)) and "mawonna" in ids
for n in ("META-INF/MANIFEST.MF", "pack.mcmeta", "LICENSE"):
    if n in addition:
        payload = addition.pop(n)
        if n == "LICENSE": addition["LICENSE-Mawonna"] = payload
entries = dict(original)
for name, payload in addition.items():
    if name in entries and entries[name] != payload: raise ValueError("Conflicting existing Unyielding entry: "+name)
    entries[name] = payload
entries[path] = merged.encode("utf-8")
output.parent.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(output,"w",zipfile.ZIP_DEFLATED) as z:
    for name in sorted(entries):
        info = zipfile.ZipInfo(name,date_time=(2026,11,7,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED
        z.writestr(info,entries[name])
with zipfile.ZipFile(output) as z:
    for name, payload in original.items():
        if name != path: assert z.read(name) == payload, "Original module changed: "+name
report = {"base":meta,"output_sha256":hashlib.sha256(output.read_bytes()).hexdigest(),"mod_ids":ids,"original_entries_preserved":len(original)-1,"new_entries":len(entries)-len(original)}
(output.parent/"unyielding-assembly.json").write_text(json.dumps(report,indent=2)+"\n")
print(json.dumps(report,indent=2))
