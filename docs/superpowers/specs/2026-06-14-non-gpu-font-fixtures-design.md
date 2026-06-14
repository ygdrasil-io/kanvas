# Non-GPU Font Fixture Wave Design

Date: 2026-06-14
Status: approved design

## Purpose

Create the first broad non-GPU fixture wave for pure Kotlin text/font work.
The wave covers `PKT-02D` through `PKT-11D`, using the active coordination
matrix as the backlog source of truth. It materializes fixture files,
provenance, licenses, manifests, expected dumps, validators, and checkpoints
needed before later support slices can make implementation claims.

This design is not an implementation plan and not a support claim.

## Scope

The wave covers these non-GPU fixture families:

- font-source scans, fallback catalog inputs, and skipped-file diagnostics;
- SFNT/TTC fixtures, malformed table fixtures, and `cmap` format 14 fixtures;
- TrueType variation fixtures for IUP, phantom points, `avar`, and metrics
  refusals;
- CFF/CFF2 fixtures for INDEX/DICT, Type 2 charstrings, subroutines,
  malformed data, and variation refusals;
- Unicode 16.0 source metadata and generation contract evidence;
- Latin GSUB/GPOS fixture goldens;
- Arabic complex-shaping seed fixtures and refusals;
- paragraph input/style schema goldens;
- A8/SDF atlas lifecycle and refusal fixtures;
- COLR, PNG bitmap glyph, SVG subset, and emoji ZWJ fixtures.

The wave excludes:

- GPU route promotion;
- `DrawTextRun` GPU handoff support;
- `:kanvas-skia` facade migration;
- broad `target-supported` claims;
- native or platform font APIs as normative oracles.

## Fixture Root

All new fixture assets live under:

```text
reports/font/fixtures/
```

The intended structure is:

```text
reports/font/fixtures/
  README.md
  fonts/
  licenses/
  provenance/
  expected/
```

`fonts/` stores vendored or synthetic font files. `licenses/` stores exact
license texts copied from accepted sources. `provenance/` stores structured
metadata per fixture or fixture family. `expected/` stores deterministic dumps
and goldens.

Existing manifests remain under `reports/pure-kotlin-text/` and reference this
fixture root:

- `fixture-evidence-manifest.json`;
- `dump-evidence-index.json`;
- `coverage-ticket-matrix.md`.

## Download And Licensing Policy

Downloads are allowed only during fixture creation, not during tests or normal
validation. Downloaded files must come from official project sources.

Accepted licenses for this wave:

- SIL OFL;
- Apache-2.0.

Every downloaded or vendored fixture must record:

- source URL;
- source project and version, tag, commit, or release;
- license ID and license file path;
- SHA-256;
- byte size;
- reason the fixture is included;
- related PKT and KFONT rows.

The total fixture budget for this wave is 20 MiB. Validators should fail if the
budget is exceeded unless a later design updates the limit.

## Offline Rule

After fixture creation, all tests and validators must run offline. No Gradle
test, Python validator, fixture generator, or dump comparison may fetch network
resources in ordinary validation.

Network-sourced files become vendored inputs with provenance. If a fixture
cannot be sourced from an official project with an accepted license, that
fixture family must remain blocked with a precise source/licensing gate instead
of using an implicit or unverifiable source.

## Creation Order

The implementation should use this order:

1. Fixture and golden infrastructure.
   Extend validators for `reports/font/fixtures/`, provenance, licenses,
   SHA-256, total size, and offline policy.

2. Font containers.
   Implement `PKT-02D` and `PKT-03D`: font-source scan fixtures, SFNT/TTC,
   malformed tables, and `cmap` format 14.

3. Scalers.
   Implement `PKT-04C` and `PKT-05B`: TrueType variation fixtures, then
   CFF/CFF2 fixtures and refusal goldens.

4. Text shaping and paragraph.
   Implement `PKT-06D`, `PKT-07B`, `PKT-08B`, and `PKT-09C`: Unicode 16.0,
   Latin GSUB/GPOS, Arabic seed, and paragraph input goldens.

5. Glyph and color.
   Implement `PKT-10D` and `PKT-11D`: A8/SDF lifecycle plus COLR, PNG, SVG,
   and emoji fixtures.

Each slice must add a checkpoint to `coverage-ticket-matrix.md`.

## Validation Rules

Validators should reject:

- fixture file referenced by a manifest but missing from disk;
- missing provenance JSON;
- missing or unaccepted license;
- SHA-256 mismatch;
- missing expected dump when the fixture row requires one;
- source URL or version omitted for vendored external files;
- total fixture size above 20 MiB;
- network download command in tests or ordinary validators;
- `target-supported` claim without complete evidence;
- external engine output used as a normative oracle.

Expected validation commands for the wave:

```bash
rtk python3 -m unittest scripts/test_validate_pure_kotlin_text_fixture_manifest.py scripts/test_validate_pure_kotlin_text_dump_index.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk ./gradlew --no-daemon :font:core:test :font:sfnt:test :font:scaler:test :font:text:test :font:glyph:test
rtk git diff --check
```

The implementation may add narrower validation commands per slice, but it must
preserve these broader gates when shared fixture or manifest contracts change.

## Non-Claims

This wave does not claim:

- complete pure Kotlin text support;
- complete SFNT, TrueType, CFF, CFF2, shaping, paragraph, color glyph, SVG, or
  emoji support;
- GPU text rendering support;
- `:kanvas-skia` facade migration;
- parity with HarfBuzz, FreeType, Fontations, AWT, JNI, platform shapers,
  native font APIs, browser layout, or Skia native output.

External engines may be mentioned only as optional drift inputs when allowed by
the active specs. They are not normative pass/fail oracles.

## Success Criteria

The wave is successful when:

- `reports/font/fixtures/` exists with documented structure;
- every fixture added has accepted license text, provenance, SHA-256, and
  stable size metadata;
- manifests point to concrete files or precise source/licensing gates;
- expected dumps are versioned where required;
- validators catch missing files, bad hashes, missing licenses, and oversized
  fixture sets;
- `coverage-ticket-matrix.md` records checkpoints for completed PKT slices;
- all required validations pass;
- remaining gates and non-claims are explicit.

## Open Decisions Resolved By This Design

- Unicode version: Unicode 16.0.
- Fixture policy: downloaded now from official sources when needed, then
  vendored for offline tests.
- License policy: SIL OFL and Apache-2.0 only.
- Fixture size budget: 20 MiB.
- Fixture root: `reports/font/fixtures/`.
