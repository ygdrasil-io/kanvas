# Non-GPU Font Fixtures Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Materialize non-GPU pure Kotlin text/font fixtures for `PKT-02D` through `PKT-11D` with vendored assets, provenance, licenses, expected dumps, validators, and matrix checkpoints.

**Architecture:** Add a dedicated fixture asset root under `reports/font/fixtures/` and validate it with a new asset validator before touching subsystem tests. Keep `reports/pure-kotlin-text/fixture-evidence-manifest.json`, `reports/pure-kotlin-text/dump-evidence-index.json`, and `reports/pure-kotlin-text/coverage-ticket-matrix.md` as the coordination source of truth. Each PKT slice adds assets and expected dumps without promoting broad support claims.

**Tech Stack:** Python 3 validators and unit tests, Kotlin/JVM module tests through Gradle, JSON manifests, vendored font/data files, RTK command wrapper.

---

## Scope Check

This spec covers several independent fixture families. Treat this plan as a master plan with independently committable tasks. Do not implement GPU handoff, GPU route registration, or `:kanvas-skia` facade migration in this plan.

## File Structure

Create:

- `reports/font/fixtures/README.md`: human policy for vendored fixtures, downloads, offline validation, license policy, and non-claims.
- `reports/font/fixtures/fonts/.gitkeep`: keeps the font asset directory before the first copied font.
- `reports/font/fixtures/licenses/.gitkeep`: keeps the license directory before the first copied license.
- `reports/font/fixtures/provenance/index.json`: machine-readable provenance index for every vendored fixture asset.
- `reports/font/fixtures/expected/.gitkeep`: keeps the expected dump directory before the first dump.
- `scripts/validate_font_fixture_assets.py`: validates fixture root, provenance, licenses, hashes, sizes, accepted license IDs, and offline policy.
- `scripts/test_validate_font_fixture_assets.py`: unit tests for the new validator.

Modify as tasks progress:

- `reports/pure-kotlin-text/fixture-evidence-manifest.json`: add current evidence paths and fixture gates for materialized fixture families.
- `reports/pure-kotlin-text/dump-evidence-index.json`: add expected dump rows and producer paths.
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`: add checkpoints for `PKT-02D` through `PKT-11D`.
- `scripts/validate_pure_kotlin_text_fixture_manifest.py`: only if the manifest needs new required family invariants.
- `scripts/test_validate_pure_kotlin_text_fixture_manifest.py`: tests for new manifest invariants.
- `scripts/validate_pure_kotlin_text_dump_index.py`: only if checked-in golden files become mandatory in dump rows.
- `scripts/test_validate_pure_kotlin_text_dump_index.py`: tests for new dump-index invariants.
- Focused Kotlin tests under `font/core`, `font/sfnt`, `font/scaler`, `font/text`, and `font/glyph`.

## Task 1: Fixture Asset Validator And Root

**Files:**
- Create: `reports/font/fixtures/README.md`
- Create: `reports/font/fixtures/fonts/.gitkeep`
- Create: `reports/font/fixtures/licenses/.gitkeep`
- Create: `reports/font/fixtures/provenance/index.json`
- Create: `reports/font/fixtures/expected/.gitkeep`
- Create: `scripts/validate_font_fixture_assets.py`
- Create: `scripts/test_validate_font_fixture_assets.py`

- [ ] **Step 1: Create a failing validator test for the empty approved root**

Write `scripts/test_validate_font_fixture_assets.py`:

```python
#!/usr/bin/env python3
import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
VALIDATOR_PATH = PROJECT_ROOT / "scripts" / "validate_font_fixture_assets.py"

sys.dont_write_bytecode = True


def load_validator():
    spec = importlib.util.spec_from_file_location("validate_font_fixture_assets", VALIDATOR_PATH)
    if spec is None or spec.loader is None:
        raise AssertionError("unable to load scripts/validate_font_fixture_assets.py")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class FontFixtureAssetsValidatorTest(unittest.TestCase):
    def test_repo_fixture_root_validates(self) -> None:
        validator = load_validator()
        index = validator.load_index(PROJECT_ROOT)
        validator.validate_index(PROJECT_ROOT, index)
        self.assertEqual("font-fixture-assets", index["indexId"])
        self.assertEqual("tests-must-not-download", index["offlinePolicy"])
        self.assertEqual(20 * 1024 * 1024, index["sizeBudgetBytes"])

    def test_rejects_missing_asset(self) -> None:
        validator = load_validator()
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "reports/font/fixtures/provenance").mkdir(parents=True)
            (root / "reports/font/fixtures/licenses").mkdir(parents=True)
            (root / "reports/font/fixtures/licenses/OFL-1.1.txt").write_text("license\n", encoding="utf-8")
            payload = {
                "schemaVersion": 1,
                "indexId": "font-fixture-assets",
                "fixtureRoot": "reports/font/fixtures",
                "licensePolicy": ["Apache-2.0", "SIL-OFL-1.1"],
                "sizeBudgetBytes": 20 * 1024 * 1024,
                "offlinePolicy": "tests-must-not-download",
                "fixtures": [
                    {
                        "fixtureId": "missing-font",
                        "familyId": "font-source-sfnt",
                        "ownerTickets": ["PKT-02D"],
                        "source": {
                            "kind": "vendored-external",
                            "project": "Missing",
                            "url": "https://example.invalid/font.ttf",
                            "version": "none",
                        },
                        "license": {
                            "id": "SIL-OFL-1.1",
                            "path": "reports/font/fixtures/licenses/OFL-1.1.txt",
                        },
                        "assets": [
                            {
                                "path": "reports/font/fixtures/fonts/missing.ttf",
                                "sha256": "0" * 64,
                                "sizeBytes": 1,
                                "role": "font",
                            }
                        ],
                        "expectedDumps": [],
                        "nonClaims": ["no-complete-target-support-claim"],
                    }
                ],
            }
            (root / "reports/font/fixtures/provenance/index.json").write_text(json.dumps(payload), encoding="utf-8")
            with self.assertRaises(validator.ValidationError) as failure:
                validator.validate_index(root, payload)
            self.assertIn("missing asset", str(failure.exception))


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run the failing test**

Run:

```bash
rtk python3 -m unittest scripts/test_validate_font_fixture_assets.py
```

Expected: fails because `scripts/validate_font_fixture_assets.py` does not exist.

- [ ] **Step 3: Create the fixture root and empty provenance index**

Create these directories and marker files:

```text
reports/font/fixtures/fonts/.gitkeep
reports/font/fixtures/licenses/.gitkeep
reports/font/fixtures/provenance/index.json
reports/font/fixtures/expected/.gitkeep
```

Write `reports/font/fixtures/provenance/index.json`:

```json
{
  "schemaVersion": 1,
  "indexId": "font-fixture-assets",
  "fixtureRoot": "reports/font/fixtures",
  "licensePolicy": [
    "Apache-2.0",
    "SIL-OFL-1.1"
  ],
  "sizeBudgetBytes": 20971520,
  "offlinePolicy": "tests-must-not-download",
  "fixtures": []
}
```

Write `reports/font/fixtures/README.md`:

```markdown
# Font Fixtures

This directory contains vendored or synthetic non-GPU font/text fixtures for
pure Kotlin text validation.

Rules:

- Fixture creation may download files from official project sources.
- Ordinary tests and validators must not download network resources.
- Accepted licenses for this fixture wave are SIL OFL 1.1 and Apache-2.0.
- Every vendored asset must appear in `provenance/index.json` with source URL,
  version or revision, license path, SHA-256, byte size, related PKT/KFONT rows,
  and explicit non-claims.
- The total fixture payload budget is 20 MiB.
- These fixtures do not by themselves promote complete text, font, shaping,
  paragraph, color glyph, SVG, emoji, or GPU support.
```

- [ ] **Step 4: Implement the validator**

Create `scripts/validate_font_fixture_assets.py` with:

```python
#!/usr/bin/env python3
import hashlib
import json
import re
import sys
from pathlib import Path
from typing import Any


INDEX_PATH = "reports/font/fixtures/provenance/index.json"
TOP_LEVEL_KEYS = [
    "schemaVersion",
    "indexId",
    "fixtureRoot",
    "licensePolicy",
    "sizeBudgetBytes",
    "offlinePolicy",
    "fixtures",
]
FIXTURE_KEYS = [
    "fixtureId",
    "familyId",
    "ownerTickets",
    "source",
    "license",
    "assets",
    "expectedDumps",
    "nonClaims",
]
SOURCE_KEYS = ["kind", "project", "url", "version"]
LICENSE_KEYS = ["id", "path"]
ASSET_KEYS = ["path", "sha256", "sizeBytes", "role"]
ACCEPTED_LICENSES = ["Apache-2.0", "SIL-OFL-1.1"]
SOURCE_KINDS = ["in-repo-existing", "vendored-external", "synthetic-kanvas"]
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")


class ValidationError(RuntimeError):
    pass


def fail(message: str) -> None:
    raise ValidationError(f"font fixture asset validation failed: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def require_keys(payload: dict[str, Any], expected: list[str], label: str) -> None:
    require(list(payload.keys()) == expected, f"{label} keys must be stable and ordered: {expected}")


def require_string(value: Any, label: str) -> str:
    require(isinstance(value, str) and value.strip() == value and value, f"{label} must be a non-empty trimmed string")
    return value


def require_string_list(value: Any, label: str, *, allow_empty: bool = False) -> list[str]:
    require(isinstance(value, list), f"{label} must be a list")
    if not allow_empty:
        require(value, f"{label} must be non-empty")
    for index, item in enumerate(value):
        require_string(item, f"{label}[{index}]")
    return value


def repo_path(root: Path, relative_path: str, label: str) -> Path:
    require_string(relative_path, label)
    require(not Path(relative_path).is_absolute(), f"{label} must be relative: {relative_path}")
    resolved_root = root.resolve()
    resolved_path = (resolved_root / relative_path).resolve()
    require(resolved_path == resolved_root or resolved_root in resolved_path.parents, f"{label} escapes project root: {relative_path}")
    return resolved_path


def require_file(root: Path, relative_path: str, label: str) -> Path:
    path = repo_path(root, relative_path, label)
    require(path.is_file(), f"missing asset file for {label}: {relative_path}")
    return path


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_json(root: Path, relative_path: str) -> Any:
    path = require_file(root, relative_path, relative_path)
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        fail(f"invalid JSON in {relative_path}: {exc}")


def load_index(root: Path) -> dict[str, Any]:
    index = load_json(root, INDEX_PATH)
    require(isinstance(index, dict), "index root must be an object")
    return index


def validate_fixture(root: Path, fixture: Any, index: int, accepted_licenses: list[str]) -> int:
    require(isinstance(fixture, dict), f"fixtures[{index}] must be an object")
    require_keys(fixture, FIXTURE_KEYS, f"fixtures[{index}]")
    fixture_id = require_string(fixture["fixtureId"], f"fixtures[{index}].fixtureId")
    require_string(fixture["familyId"], f"{fixture_id}.familyId")
    require_string_list(fixture["ownerTickets"], f"{fixture_id}.ownerTickets")

    source = fixture["source"]
    require(isinstance(source, dict), f"{fixture_id}.source must be an object")
    require_keys(source, SOURCE_KEYS, f"{fixture_id}.source")
    require(source["kind"] in SOURCE_KINDS, f"{fixture_id}.source.kind is not accepted: {source['kind']}")
    require_string(source["project"], f"{fixture_id}.source.project")
    require_string(source["url"], f"{fixture_id}.source.url")
    require_string(source["version"], f"{fixture_id}.source.version")

    license_payload = fixture["license"]
    require(isinstance(license_payload, dict), f"{fixture_id}.license must be an object")
    require_keys(license_payload, LICENSE_KEYS, f"{fixture_id}.license")
    license_id = require_string(license_payload["id"], f"{fixture_id}.license.id")
    require(license_id in accepted_licenses, f"{fixture_id}.license.id is not accepted: {license_id}")
    require_file(root, license_payload["path"], f"{fixture_id}.license.path")

    total_size = 0
    assets = fixture["assets"]
    require(isinstance(assets, list) and assets, f"{fixture_id}.assets must be a non-empty list")
    for asset_index, asset in enumerate(assets):
        require(isinstance(asset, dict), f"{fixture_id}.assets[{asset_index}] must be an object")
        require_keys(asset, ASSET_KEYS, f"{fixture_id}.assets[{asset_index}]")
        asset_path = require_file(root, asset["path"], f"{fixture_id}.assets[{asset_index}].path")
        expected_hash = require_string(asset["sha256"], f"{fixture_id}.assets[{asset_index}].sha256")
        require(SHA256_RE.match(expected_hash) is not None, f"{fixture_id}.assets[{asset_index}].sha256 must be lowercase SHA-256")
        actual_hash = sha256(asset_path)
        require(actual_hash == expected_hash, f"{fixture_id}.assets[{asset_index}] SHA-256 mismatch: {asset['path']}")
        expected_size = asset["sizeBytes"]
        require(isinstance(expected_size, int) and expected_size >= 0, f"{fixture_id}.assets[{asset_index}].sizeBytes must be non-negative integer")
        actual_size = asset_path.stat().st_size
        require(actual_size == expected_size, f"{fixture_id}.assets[{asset_index}] size mismatch: {asset['path']}")
        require_string(asset["role"], f"{fixture_id}.assets[{asset_index}].role")
        total_size += actual_size

    for dump_path in require_string_list(fixture["expectedDumps"], f"{fixture_id}.expectedDumps", allow_empty=True):
        require_file(root, dump_path, f"{fixture_id}.expectedDumps")

    non_claims = require_string_list(fixture["nonClaims"], f"{fixture_id}.nonClaims")
    require("no-complete-target-support-claim" in non_claims, f"{fixture_id}.nonClaims must include no-complete-target-support-claim")
    return total_size


def validate_index(root: Path, index: dict[str, Any]) -> None:
    require_keys(index, TOP_LEVEL_KEYS, "index")
    require(index["schemaVersion"] == 1, "schemaVersion must be 1")
    require(index["indexId"] == "font-fixture-assets", "indexId changed")
    require(index["fixtureRoot"] == "reports/font/fixtures", "fixtureRoot changed")
    require(index["licensePolicy"] == ACCEPTED_LICENSES, "licensePolicy changed")
    require(index["offlinePolicy"] == "tests-must-not-download", "offlinePolicy changed")
    size_budget = index["sizeBudgetBytes"]
    require(isinstance(size_budget, int) and size_budget == 20 * 1024 * 1024, "sizeBudgetBytes must be 20 MiB")
    fixtures = index["fixtures"]
    require(isinstance(fixtures, list), "fixtures must be a list")
    fixture_ids = [require_string(row.get("fixtureId"), f"fixtures[{i}].fixtureId") for i, row in enumerate(fixtures) if isinstance(row, dict)]
    require(fixture_ids == sorted(fixture_ids), "fixtures must be sorted by fixtureId")
    require(len(fixture_ids) == len(set(fixture_ids)), "fixtureId values must be unique")
    total_size = sum(validate_fixture(root, fixture, index, ACCEPTED_LICENSES) for index, fixture in enumerate(fixtures))
    require(total_size <= size_budget, f"fixture assets exceed size budget: {total_size} > {size_budget}")


def main() -> int:
    root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path.cwd()
    try:
        index = load_index(root)
        validate_index(root, index)
    except ValidationError as exc:
        print(str(exc), file=sys.stderr)
        return 1
    print(f"Font fixture asset validation passed: {len(index['fixtures'])} fixtures.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
```

- [ ] **Step 5: Run validator tests**

Run:

```bash
rtk python3 -m unittest scripts/test_validate_font_fixture_assets.py
```

Expected: passes with 2 tests.

- [ ] **Step 6: Commit infrastructure**

Run:

```bash
rtk git add reports/font/fixtures scripts/validate_font_fixture_assets.py scripts/test_validate_font_fixture_assets.py
rtk git commit -m "test: add font fixture asset validator"
```

Expected: commit succeeds.

## Task 2: Seed Existing Licensed Font Assets

**Files:**
- Modify: `reports/font/fixtures/provenance/index.json`
- Copy/Create: `reports/font/fixtures/fonts/liberation/LiberationSans-Regular.ttf`
- Copy/Create: `reports/font/fixtures/fonts/liberation/LiberationSerif-Regular.ttf`
- Copy/Create: `reports/font/fixtures/fonts/liberation/LiberationMono-Regular.ttf`
- Copy/Create: `reports/font/fixtures/licenses/liberation-OFL-1.1.txt`
- Copy/Create: `reports/font/fixtures/fonts/color/test_glyphs-glyf_colr_1.ttf`
- Copy/Create: `reports/font/fixtures/fonts/color/test_glyphs-glyf_colr_1_variable.ttf`
- Copy/Create: `reports/font/fixtures/licenses/test_glyphs_colrv1-Apache-2.0.txt`

- [ ] **Step 1: Copy existing licensed assets into the fixture root**

Run:

```bash
rtk mkdir -p reports/font/fixtures/fonts/liberation reports/font/fixtures/fonts/color reports/font/fixtures/licenses
rtk cp kanvas-skia/src/main/resources/fonts/liberation/LiberationSans-Regular.ttf reports/font/fixtures/fonts/liberation/LiberationSans-Regular.ttf
rtk cp kanvas-skia/src/main/resources/fonts/liberation/LiberationSerif-Regular.ttf reports/font/fixtures/fonts/liberation/LiberationSerif-Regular.ttf
rtk cp kanvas-skia/src/main/resources/fonts/liberation/LiberationMono-Regular.ttf reports/font/fixtures/fonts/liberation/LiberationMono-Regular.ttf
rtk cp kanvas-skia/src/main/resources/fonts/liberation/LICENSE.txt reports/font/fixtures/licenses/liberation-OFL-1.1.txt
rtk cp skia-integration-tests/src/test/resources/fonts/test_glyphs-glyf_colr_1.ttf reports/font/fixtures/fonts/color/test_glyphs-glyf_colr_1.ttf
rtk cp skia-integration-tests/src/test/resources/fonts/test_glyphs-glyf_colr_1_variable.ttf reports/font/fixtures/fonts/color/test_glyphs-glyf_colr_1_variable.ttf
rtk cp skia-integration-tests/src/test/resources/fonts/test_glyphs_colrv1_LICENSE-Apache-2.0.txt reports/font/fixtures/licenses/test_glyphs_colrv1-Apache-2.0.txt
```

Expected: files exist under `reports/font/fixtures/`.

- [ ] **Step 2: Generate sorted provenance rows with computed hashes**

Run this script from the repository root:

```bash
rtk python3 - <<'PY'
import hashlib
import json
from pathlib import Path


INDEX = Path("reports/font/fixtures/provenance/index.json")


def asset(path: str, role: str) -> dict:
    payload = Path(path)
    digest = hashlib.sha256(payload.read_bytes()).hexdigest()
    return {
        "path": path,
        "sha256": digest,
        "sizeBytes": payload.stat().st_size,
        "role": role,
    }


index = json.loads(INDEX.read_text(encoding="utf-8"))
index["fixtures"] = [
    {
        "fixtureId": "color-colrv1-test-glyphs",
        "familyId": "color-glyphs",
        "ownerTickets": [
            "PKT-11D",
            "KFONT-M10-010"
        ],
        "source": {
            "kind": "in-repo-existing",
            "project": "googlefonts/color-fonts test glyphs",
            "url": "https://github.com/googlefonts/color-fonts",
            "version": "0046ea4c3b69e9fbbe464c2594816894e3aa5e4b"
        },
        "license": {
            "id": "Apache-2.0",
            "path": "reports/font/fixtures/licenses/test_glyphs_colrv1-Apache-2.0.txt"
        },
        "assets": [
            asset("reports/font/fixtures/fonts/color/test_glyphs-glyf_colr_1.ttf", "colrv1-font"),
            asset("reports/font/fixtures/fonts/color/test_glyphs-glyf_colr_1_variable.ttf", "colrv1-variable-font")
        ],
        "expectedDumps": [],
        "nonClaims": [
            "no-complete-target-support-claim",
            "no-complete-colrv1-rendering-claim",
            "no-gpu-color-glyph-support-claim"
        ]
    },
    {
        "fixtureId": "font-source-liberation-core",
        "familyId": "font-source-sfnt",
        "ownerTickets": [
            "PKT-02D",
            "PKT-03D",
            "KFONT-M1-004",
            "KFONT-M2-005"
        ],
        "source": {
            "kind": "in-repo-existing",
            "project": "Liberation Fonts",
            "url": "kanvas-skia/src/main/resources/fonts/liberation",
            "version": "repo-vendored-existing"
        },
        "license": {
            "id": "SIL-OFL-1.1",
            "path": "reports/font/fixtures/licenses/liberation-OFL-1.1.txt"
        },
        "assets": [
            asset("reports/font/fixtures/fonts/liberation/LiberationMono-Regular.ttf", "monospace-ttf"),
            asset("reports/font/fixtures/fonts/liberation/LiberationSans-Regular.ttf", "sans-ttf"),
            asset("reports/font/fixtures/fonts/liberation/LiberationSerif-Regular.ttf", "serif-ttf")
        ],
        "expectedDumps": [],
        "nonClaims": [
            "no-complete-target-support-claim",
            "no-complete-system-font-discovery-claim",
            "no-host-platform-fallback-claim"
        ]
    }
]
INDEX.write_text(json.dumps(index, indent=2) + "\n", encoding="utf-8")
PY
```

Expected: `reports/font/fixtures/provenance/index.json` contains two rows sorted by `fixtureId`, and every asset row has computed SHA-256 and byte size values.

- [ ] **Step 4: Validate fixture assets**

Run:

```bash
rtk python3 -m unittest scripts/test_validate_font_fixture_assets.py
rtk python3 scripts/validate_font_fixture_assets.py
rtk git diff --check
```

Expected: validator reports `2 fixtures`.

- [ ] **Step 5: Commit seeded licensed assets**

Run:

```bash
rtk git add reports/font/fixtures scripts/validate_font_fixture_assets.py scripts/test_validate_font_fixture_assets.py
rtk git commit -m "test: seed licensed font fixture assets"
```

Expected: commit succeeds.

## Task 3: PKT-02D And PKT-03D Font Container Fixtures

**Files:**
- Create: `reports/font/fixtures/expected/font-source/literation-scan-root.json`
- Create: `reports/font/fixtures/expected/sfnt/sfnt-cmap-format14-readiness.json`
- Modify: `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- Modify: `reports/pure-kotlin-text/dump-evidence-index.json`
- Modify: `reports/pure-kotlin-text/coverage-ticket-matrix.md`
- Test: `font/core/src/test/kotlin/org/graphiks/kanvas/font/FontCoreSurfaceTest.kt`
- Test: `font/sfnt/src/test/kotlin/org/graphiks/kanvas/font/sfnt/SFNTSurfaceTest.kt`

- [ ] **Step 1: Add expected scan-root dump**

Create `reports/font/fixtures/expected/font-source/liberation-scan-root.json`:

```json
{
  "schemaVersion": 1,
  "dumpId": "font-source-liberation-scan-root",
  "ownerTickets": [
    "PKT-02D"
  ],
  "scanRoot": "reports/font/fixtures/fonts/liberation",
  "hostDependent": false,
  "acceptedFamilies": [
    "Liberation Mono",
    "Liberation Sans",
    "Liberation Serif"
  ],
  "diagnostics": [],
  "nonClaims": [
    "no-complete-system-font-discovery-claim",
    "no-host-platform-fallback-claim"
  ]
}
```

- [ ] **Step 2: Add expected SFNT readiness dump**

Create `reports/font/fixtures/expected/sfnt/sfnt-cmap-format14-readiness.json`:

```json
{
  "schemaVersion": 1,
  "dumpId": "sfnt-cmap-format14-readiness",
  "ownerTickets": [
    "PKT-03D"
  ],
  "fixtures": [
    {
      "fixtureId": "font-source-liberation-core",
      "requiredTables": [
        "cmap",
        "head",
        "hhea",
        "hmtx",
        "maxp",
        "name",
        "OS/2",
        "post"
      ],
      "format14Status": "fixture-gated",
      "diagnostics": [
        "font.cmap.format14-fixture-missing"
      ]
    }
  ],
  "nonClaims": [
    "no-complete-sfnt-conformance-claim",
    "no-complete-cmap-format14-claim"
  ]
}
```

- [ ] **Step 3: Attach expected dumps to provenance**

Edit the `font-source-liberation-core` row in `reports/font/fixtures/provenance/index.json` so `expectedDumps` is:

```json
[
  "reports/font/fixtures/expected/font-source/liberation-scan-root.json",
  "reports/font/fixtures/expected/sfnt/sfnt-cmap-format14-readiness.json"
]
```

- [ ] **Step 4: Add or adjust focused Kotlin tests**

In `font/core/src/test/kotlin/org/graphiks/kanvas/font/FontCoreSurfaceTest.kt`, add a test that loads the three Liberation fixture paths and asserts deterministic accepted family ordering. Use existing `FallbackCatalog`/dump helpers already present in the file; do not add filesystem scanning behavior outside explicit paths.

In `font/sfnt/src/test/kotlin/org/graphiks/kanvas/font/sfnt/SFNTSurfaceTest.kt`, add a test that opens `LiberationSans-Regular.ttf` from `reports/font/fixtures/fonts/liberation/`, verifies required table tags are present through existing SFNT APIs, and records `font.cmap.format14-fixture-missing` as an explicit non-claim if no format 14 subtable is present.

- [ ] **Step 5: Update manifests and matrix**

Update `fixture-evidence-manifest.json` current evidence paths for `font-source-sfnt`, `font-source-system-scan`, and `sfnt-malformed-tables` to include the two expected dump files.

Update `dump-evidence-index.json` with rows for:

```json
"font-source-liberation-scan-root"
```

and

```json
"sfnt-cmap-format14-readiness"
```

Keep each row non-claiming and use classification `golden-gated` unless the dump index validator has been changed to accept checked golden artifacts.

Add `PKT-02D` and `PKT-03D` checkpoints to `coverage-ticket-matrix.md`.

- [ ] **Step 6: Validate PKT-02D/03D**

Run:

```bash
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk ./gradlew --no-daemon :font:core:test :font:sfnt:test
rtk git diff --check
```

Expected: all commands pass. If the dump index validator rejects new dump IDs, update its `REQUIRED_DUMPS` and tests in the same commit.

- [ ] **Step 7: Commit PKT-02D/03D**

Run:

```bash
rtk git add reports/font/fixtures reports/pure-kotlin-text font/core/src/test font/sfnt/src/test scripts
rtk git commit -m "test: add font source and sfnt fixture goldens"
```

Expected: commit succeeds.

## Task 4: PKT-04C And PKT-05B Scaler Fixtures

**Files:**
- Copy/Create: `reports/font/fixtures/fonts/scaler/Stroking.ttf`
- Copy/Create: `reports/font/fixtures/fonts/scaler/Stroking.otf`
- Copy/Create: `reports/font/fixtures/fonts/scaler/Variable.ttf`
- Create: `reports/font/fixtures/licenses/stroking-variable-Apache-2.0.txt` or accepted license copied from source if present
- Create: `reports/font/fixtures/expected/scaler/truetype-variation-readiness.json`
- Create: `reports/font/fixtures/expected/scaler/cff-cff2-readiness.json`
- Modify: `reports/font/fixtures/provenance/index.json`
- Modify: `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- Modify: `reports/pure-kotlin-text/dump-evidence-index.json`
- Modify: `reports/pure-kotlin-text/coverage-ticket-matrix.md`
- Test: `font/scaler/src/test/kotlin/org/graphiks/kanvas/font/scaler/FontScalerSurfaceTest.kt`

- [ ] **Step 1: Verify scaler fixture license before copying**

Run:

```bash
rtk sed -n '1,160p' skia-integration-tests/src/test/resources/fonts/Stroking_VARIABLE_PROVENANCE.md
```

Expected: the file names a source and a license. Continue only if the license is Apache-2.0 or SIL OFL 1.1. If the license is missing or different, do not copy these assets; instead vendor an official accepted-license scaler font in a separate commit and record its provenance.

- [ ] **Step 2: Copy accepted scaler assets and license**

If Step 1 confirms an accepted license, run:

```bash
rtk mkdir -p reports/font/fixtures/fonts/scaler reports/font/fixtures/licenses reports/font/fixtures/expected/scaler
rtk cp skia-integration-tests/src/test/resources/fonts/Stroking.ttf reports/font/fixtures/fonts/scaler/Stroking.ttf
rtk cp skia-integration-tests/src/test/resources/fonts/Stroking.otf reports/font/fixtures/fonts/scaler/Stroking.otf
rtk cp skia-integration-tests/src/test/resources/fonts/Variable.ttf reports/font/fixtures/fonts/scaler/Variable.ttf
rtk cp skia-integration-tests/src/test/resources/fonts/Stroking_VARIABLE_PROVENANCE.md reports/font/fixtures/licenses/stroking-variable-Apache-2.0.txt
```

Expected: three scaler font assets exist under `reports/font/fixtures/fonts/scaler/`.

- [ ] **Step 3: Add scaler expected dumps**

Create `reports/font/fixtures/expected/scaler/truetype-variation-readiness.json`:

```json
{
  "schemaVersion": 1,
  "dumpId": "truetype-variation-readiness",
  "ownerTickets": [
    "PKT-04C"
  ],
  "fixtureIds": [
    "scaler-stroking-variable"
  ],
  "requiredEvidence": [
    "IUP interpolation fixture rows",
    "phantom point and advance delta rows",
    "avar coordinate mapping rows",
    "HVAR/VVAR/MVAR refusal rows"
  ],
  "nonClaims": [
    "no-full-variable-font-support-claim",
    "no-native-scaler-oracle-claim"
  ]
}
```

Create `reports/font/fixtures/expected/scaler/cff-cff2-readiness.json`:

```json
{
  "schemaVersion": 1,
  "dumpId": "cff-cff2-readiness",
  "ownerTickets": [
    "PKT-05B"
  ],
  "fixtureIds": [
    "scaler-stroking-cff"
  ],
  "requiredEvidence": [
    "CFF INDEX and dict rows",
    "Type 2 line curve flex endchar width rows",
    "local and global subroutine rows",
    "malformed stack and unsupported operator refusals",
    "CFF2 blend and vsindex refusal rows"
  ],
  "nonClaims": [
    "no-cff-rendering-support-claim",
    "no-cff2-variation-support-claim",
    "no-native-scaler-oracle-claim"
  ]
}
```

- [ ] **Step 4: Add provenance rows and focused tests**

Add provenance rows `scaler-stroking-cff` and `scaler-stroking-variable` with hashes and byte sizes from:

```bash
rtk shasum -a 256 reports/font/fixtures/fonts/scaler/Stroking.otf reports/font/fixtures/fonts/scaler/Variable.ttf
rtk wc -c reports/font/fixtures/fonts/scaler/Stroking.otf reports/font/fixtures/fonts/scaler/Variable.ttf
```

In `FontScalerSurfaceTest.kt`, add tests that assert these fixture files are readable as bytes and that current scaler diagnostics remain explicit when unsupported CFF/CFF2 or variation features are encountered. Do not assert complete outline support from these fixtures.

- [ ] **Step 5: Update manifests, matrix, and validate**

Update manifest rows `truetype-scaler` and `cff-cff2-scaler`; update dump index rows for the two scaler dumps; add `PKT-04C` and `PKT-05B` checkpoints.

Run:

```bash
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk ./gradlew --no-daemon :font:scaler:test
rtk git diff --check
```

Expected: all commands pass.

- [ ] **Step 6: Commit scaler fixtures**

Run:

```bash
rtk git add reports/font/fixtures reports/pure-kotlin-text font/scaler/src/test scripts
rtk git commit -m "test: add scaler fixture readiness goldens"
```

Expected: commit succeeds.

## Task 5: PKT-06D Unicode 16.0 Metadata

**Files:**
- Create: `reports/font/fixtures/expected/unicode/unicode-16-source-manifest.json`
- Modify: `reports/font/fixtures/provenance/index.json`
- Modify: `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- Modify: `reports/pure-kotlin-text/dump-evidence-index.json`
- Modify: `reports/pure-kotlin-text/coverage-ticket-matrix.md`
- Test: `font/text/src/test/kotlin/org/graphiks/kanvas/text/TextStackSurfaceTest.kt`

- [ ] **Step 1: Add Unicode source manifest**

Create `reports/font/fixtures/expected/unicode/unicode-16-source-manifest.json`:

```json
{
  "schemaVersion": 1,
  "dumpId": "unicode-16-source-manifest",
  "ownerTickets": [
    "PKT-06D",
    "KFONT-M5-001"
  ],
  "unicodeVersion": "16.0.0",
  "officialSource": "https://www.unicode.org/Public/16.0.0/ucd/",
  "sourceFiles": [
    "ArabicShaping.txt",
    "BidiBrackets.txt",
    "BidiMirroring.txt",
    "DerivedCoreProperties.txt",
    "GraphemeBreakProperty.txt",
    "LineBreak.txt",
    "ScriptExtensions.txt",
    "Scripts.txt",
    "UnicodeData.txt"
  ],
  "downloadPolicy": "allowed-during-fixture-creation-only",
  "ordinaryValidationPolicy": "offline",
  "nonClaims": [
    "no-complete-ucd-claim",
    "no-complete-uax9-claim",
    "no-complete-uax14-claim",
    "no-complete-uax29-claim"
  ]
}
```

- [ ] **Step 2: Add focused Unicode test**

In `font/text/src/test/kotlin/org/graphiks/kanvas/text/TextStackSurfaceTest.kt`, add a test that loads this JSON as a resource/file and asserts:

```kotlin
assertEquals("16.0.0", manifest.unicodeVersion)
assertEquals("offline", manifest.ordinaryValidationPolicy)
assertTrue(manifest.nonClaims.contains("no-complete-ucd-claim"))
```

Use the repository's existing JSON parsing pattern in this test module. If there is no parser helper, use a small local assertion over the file text and keep it scoped to this test.

- [ ] **Step 3: Update manifests, matrix, and validate**

Update the `unicode-data-generation` fixture family and `unicode-data-seed` dump row to point at the new manifest. Add a `PKT-06D` checkpoint.

Run:

```bash
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk ./gradlew --no-daemon :font:text:test --tests '*TextStackSurfaceTest*'
rtk git diff --check
```

Expected: all commands pass.

- [ ] **Step 4: Commit Unicode fixture metadata**

Run:

```bash
rtk git add reports/font/fixtures reports/pure-kotlin-text font/text/src/test scripts
rtk git commit -m "test: pin unicode fixture metadata"
```

Expected: commit succeeds.

## Task 6: PKT-07B, PKT-08B, And PKT-09C Text Fixtures

**Files:**
- Create: `reports/font/fixtures/expected/shaping/latin-gsub-gpos-goldens.json`
- Create: `reports/font/fixtures/expected/shaping/arabic-seed-readiness.json`
- Create: `reports/font/fixtures/expected/paragraph/paragraph-input-goldens.json`
- Modify: `reports/font/fixtures/provenance/index.json` if adding Arabic font assets
- Modify: `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- Modify: `reports/pure-kotlin-text/dump-evidence-index.json`
- Modify: `reports/pure-kotlin-text/coverage-ticket-matrix.md`
- Test: `font/text/src/test/kotlin/org/graphiks/kanvas/text/TextStackSurfaceTest.kt`

- [ ] **Step 1: Add Latin GSUB/GPOS expected dump**

Create `reports/font/fixtures/expected/shaping/latin-gsub-gpos-goldens.json`:

```json
{
  "schemaVersion": 1,
  "dumpId": "latin-gsub-gpos-goldens",
  "ownerTickets": [
    "PKT-07B"
  ],
  "fixtureId": "font-source-liberation-core",
  "cases": [
    {
      "caseId": "latin-fi-liga-requested",
      "text": "office",
      "features": {
        "liga": true,
        "kern": true
      },
      "requiredDumpFields": [
        "feature-order",
        "glyph-sequence",
        "cluster-map",
        "advance-adjustments"
      ]
    },
    {
      "caseId": "latin-kern-requested-off",
      "text": "AV",
      "features": {
        "liga": false,
        "kern": false
      },
      "requiredDumpFields": [
        "feature-order",
        "glyph-sequence",
        "cluster-map"
      ]
    }
  ],
  "nonClaims": [
    "no-complete-gsub-gpos-support-claim",
    "no-greek-cyrillic-hebrew-promotion-claim",
    "no-native-shaper-oracle-claim"
  ]
}
```

- [ ] **Step 2: Add Arabic seed and paragraph expected dumps**

Create `reports/font/fixtures/expected/shaping/arabic-seed-readiness.json`:

```json
{
  "schemaVersion": 1,
  "dumpId": "arabic-seed-readiness",
  "ownerTickets": [
    "PKT-08B"
  ],
  "script": "Arabic",
  "cases": [
    "joining-forms",
    "lam-alef",
    "marks",
    "cursive-attachment",
    "mixed-bidi"
  ],
  "requiredDiagnostics": [
    "text.shaping.cursive-attachment-unavailable",
    "text.shaping.mark-positioning-unavailable",
    "text.shaping.gdef-required",
    "text.shaping.paragraph-bidi-required"
  ],
  "nonClaims": [
    "no-arabic-shaping-support-claim",
    "no-complex-shaping-support-claim",
    "no-native-shaper-oracle-claim"
  ]
}
```

Create `reports/font/fixtures/expected/paragraph/paragraph-input-goldens.json`:

```json
{
  "schemaVersion": 1,
  "dumpId": "paragraph-input-goldens",
  "ownerTickets": [
    "PKT-09C"
  ],
  "cases": [
    {
      "caseId": "multi-style-with-placeholder",
      "text": "hello [box] world",
      "styleRuns": [
        {
          "range": [0, 5],
          "family": "Liberation Sans"
        },
        {
          "range": [12, 17],
          "family": "Liberation Serif"
        }
      ],
      "placeholderRanges": [
        [6, 11]
      ]
    }
  ],
  "negativeCases": [
    "invalid-range",
    "non-finite-placeholder-metric",
    "unsupported-baseline"
  ],
  "nonClaims": [
    "no-complete-paragraph-layout-claim",
    "no-skia-paragraph-parity-claim"
  ]
}
```

- [ ] **Step 3: Add focused tests**

Add tests in `TextStackSurfaceTest.kt` that load these three expected dumps and assert the non-claims and required diagnostics are present. Do not assert actual Arabic shaping support from the seed fixture.

- [ ] **Step 4: Update manifests, matrix, and validate**

Update `latin-gsub-gpos-fixtures`, `complex-script-fixture-matrix`, `paragraph`, and `paragraph-fixture-goldens` rows. Add dump rows for the three expected files. Add checkpoints for `PKT-07B`, `PKT-08B`, and `PKT-09C`.

Run:

```bash
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk ./gradlew --no-daemon :font:text:test
rtk git diff --check
```

Expected: all commands pass.

- [ ] **Step 5: Commit text fixture goldens**

Run:

```bash
rtk git add reports/font/fixtures reports/pure-kotlin-text font/text/src/test scripts
rtk git commit -m "test: add shaping and paragraph fixture goldens"
```

Expected: commit succeeds.

## Task 7: PKT-10D And PKT-11D Glyph, Color, SVG, And Emoji Fixtures

**Files:**
- Create: `reports/font/fixtures/expected/glyph/a8-sdf-atlas-lifecycle.json`
- Create: `reports/font/fixtures/expected/color/color-svg-emoji-goldens.json`
- Modify: `reports/font/fixtures/provenance/index.json`
- Modify: `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- Modify: `reports/pure-kotlin-text/dump-evidence-index.json`
- Modify: `reports/pure-kotlin-text/coverage-ticket-matrix.md`
- Test: `font/glyph/src/test/kotlin/org/graphiks/kanvas/glyph/GlyphSurfaceTest.kt`
- Test: `font/glyph/src/test/kotlin/org/graphiks/kanvas/glyph/color/ColorGlyphSurfaceTest.kt`

- [ ] **Step 1: Add A8/SDF lifecycle expected dump**

Create `reports/font/fixtures/expected/glyph/a8-sdf-atlas-lifecycle.json`:

```json
{
  "schemaVersion": 1,
  "dumpId": "a8-sdf-atlas-lifecycle",
  "ownerTickets": [
    "PKT-10D"
  ],
  "fixtureIds": [
    "font-source-liberation-core"
  ],
  "requiredEvidence": [
    "A8 atlas pack fixture",
    "SDF normalization fixture",
    "SDF transform refusal",
    "atlas capacity refusal",
    "stale generation refusal"
  ],
  "requiredDiagnostics": [
    "text.glyph.atlas-capacity-refused",
    "text.glyph.stale-generation-refused",
    "text.glyph.sdf-transform-refused"
  ],
  "nonClaims": [
    "no-complete-a8-atlas-claim",
    "no-complete-sdf-generation-claim",
    "no-gpu-text-route-claim"
  ]
}
```

- [ ] **Step 2: Add color/SVG/emoji expected dump**

Create `reports/font/fixtures/expected/color/color-svg-emoji-goldens.json`:

```json
{
  "schemaVersion": 1,
  "dumpId": "color-svg-emoji-goldens",
  "ownerTickets": [
    "PKT-11D"
  ],
  "fixtureIds": [
    "color-colrv1-test-glyphs"
  ],
  "colorFamilies": [
    "COLRv0",
    "COLRv1",
    "PNG",
    "SVG",
    "Emoji ZWJ"
  ],
  "requiredRefusals": [
    "text.color.non-png-payload-refused",
    "text.svg.external-resource-refused",
    "text.svg.script-refused",
    "text.svg.filter-refused",
    "text.emoji.fallback-unavailable"
  ],
  "nonClaims": [
    "no-complete-colrv1-rendering-claim",
    "no-png-bitmap-route-claim",
    "no-svg-rendering-support-claim",
    "no-emoji-zwj-shaping-claim",
    "no-gpu-color-glyph-support-claim"
  ]
}
```

- [ ] **Step 3: Add focused glyph/color tests**

In `GlyphSurfaceTest.kt`, add a test that loads `a8-sdf-atlas-lifecycle.json` and asserts all required diagnostics are stable strings.

In `ColorGlyphSurfaceTest.kt`, add a test that loads `color-svg-emoji-goldens.json` and asserts all required refusals and non-claims are present. Do not add a support assertion for SVG rendering or emoji shaping.

- [ ] **Step 4: Update manifests, matrix, and validate**

Update `a8-sdf-artifacts`, `color-glyphs`, `png-bitmap-glyphs`, `svg-glyphs`, and `emoji` rows. Add dump rows for both expected files. Add checkpoints for `PKT-10D` and `PKT-11D`.

Run:

```bash
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk ./gradlew --no-daemon :font:glyph:test
rtk git diff --check
```

Expected: all commands pass.

- [ ] **Step 5: Commit glyph/color fixtures**

Run:

```bash
rtk git add reports/font/fixtures reports/pure-kotlin-text font/glyph/src/test scripts
rtk git commit -m "test: add glyph color svg emoji fixture goldens"
```

Expected: commit succeeds.

## Task 8: Final Wave Validation

**Files:**
- Inspect: `reports/pure-kotlin-text/coverage-ticket-matrix.md`
- Inspect: validators under `scripts/`

- [ ] **Step 1: Run complete coordination validation**

Run:

```bash
rtk python3 -m unittest scripts/test_validate_font_fixture_assets.py scripts/test_validate_pure_kotlin_text_fixture_manifest.py scripts/test_validate_pure_kotlin_text_dump_index.py
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
```

Expected: all tests and validators pass.

- [ ] **Step 2: Run non-GPU font module tests**

Run:

```bash
rtk ./gradlew --no-daemon :font:core:test :font:sfnt:test :font:scaler:test :font:text:test :font:glyph:test
```

Expected: all listed Gradle tasks pass.

- [ ] **Step 3: Run diff hygiene**

Run:

```bash
rtk git diff --check
```

Expected: no output and exit code 0.

- [ ] **Step 4: Review support claims**

Run:

```bash
rtk rg -n "target-supported|complete .*support|HarfBuzz|FreeType|Fontations|AWT|JNI|CoreText|DirectWrite|fontconfig|native font|platform shaper" reports/font/fixtures reports/pure-kotlin-text font/core font/sfnt font/scaler font/text font/glyph scripts
```

Expected: any match is either an explicit non-claim, a hard-rule list, or a drift-only policy. If a fixture row implies a new support claim, change it back to `fixture-gated` or `tracked-gap`.

- [ ] **Step 5: Commit final checkpoint cleanup**

Run:

```bash
rtk git add reports/font/fixtures reports/pure-kotlin-text font/core font/sfnt font/scaler font/text font/glyph scripts
rtk git commit -m "test: validate non-gpu font fixture wave"
```

Expected: commit succeeds if Task 8 changed files. If Task 8 only validated previous commits, do not create an empty commit.

## Implementation Notes

- Preserve unrelated local changes in the worktree.
- Do not use Linear.
- Do not use archives as active backlog.
- Do not add HarfBuzz, FreeType, Fontations, AWT, JNI, platform shapers, native font APIs, or Skia native output as normative oracles.
- Downloading is allowed only while vendoring fixtures during implementation. Tests and validators must stay offline.
- If an external source has uncertain licensing, stop that fixture family and record the source/licensing gate in the manifest and matrix.
- If a source asset would push the fixture set above 20 MiB, stop and ask for a scope decision before committing it.

## Self-Review

Spec coverage:

- Fixture root, policy, accepted licenses, offline rule, and size budget are covered by Task 1.
- Existing licensed font and color assets are covered by Task 2.
- PKT-02D/03D are covered by Task 3.
- PKT-04C/05B are covered by Task 4.
- PKT-06D is covered by Task 5.
- PKT-07B/08B/09C are covered by Task 6.
- PKT-10D/11D are covered by Task 7.
- Final validation and support-claim review are covered by Task 8.

Placeholder scan:

- The plan contains no `TBD`, `TODO`, or open-ended implementation placeholders.
- Hash and size values are generated by explicit commands immediately before editing provenance rows.

Type consistency:

- Provenance rows use the key order required by `scripts/validate_font_fixture_assets.py`.
- Expected dump files use `schemaVersion`, `dumpId`, `ownerTickets`, evidence fields, and `nonClaims` consistently across tasks.
