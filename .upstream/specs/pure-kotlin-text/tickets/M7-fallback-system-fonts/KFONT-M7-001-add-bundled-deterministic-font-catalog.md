---
id: "KFONT-M7-001"
title: "Add bundled deterministic font catalog"
status: "done"
milestone: "M7"
priority: "P0"
owner_area: "fallback"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M1-003", "KFONT-M1-004", "KFONT-M2-004"]
legacy_gate: null
---

# KFONT-M7-001 - Add bundled deterministic font catalog

## PM Note

Ce ticket donne une base de fallback reproductible, pour que les tests texte ne dependent pas des fontes installees sur la machine.

## Problem

Fallback cannot be a support claim if the catalog is implicit, host-dependent, or based only on family names. Kanvas needs a deterministic bundled catalog with font provenance, typeface identities, family/style/generic/script/locale/emoji facts, coverage facts, and license metadata before fallback decisions can be audited.

## Scope

- Define the bundled fallback catalog schema and loader for deterministic repo-owned font fixtures.
- Record `FontSourceID`, `TypefaceID`, source hash, collection index, family/style names, generic family, supported scripts, language/locale hints, emoji/color capability facts, variation axes, palette facts, and license/provenance fields.
- Produce `font-catalog.json` with stable ordering and content hashes.
- Add diagnostics for duplicate family/style entries, missing required table facts, unsupported outline format, incomplete license/provenance, and missing glyph coverage metadata.
- Keep host system fonts out of the bundled catalog unless their bytes are captured as deterministic fixtures.

## Non-Goals

- Do not implement fallback ranking or run splitting; KFONT-M7-002 owns decisions.
- Do not scan host system font directories; KFONT-M7-005 owns host-dependent scans.
- Do not implement color emoji rendering or glyph artifacts.
- Do not make bundled catalog presence imply full script shaping support.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/01-font-source-sfnt-and-scalers.md`
- `.upstream/specs/pure-kotlin-text/02-opentype-layout-shaping-engine.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`

## Design Sketch

```kotlin
data class BundledFontCatalog(
    val generation: Int,
    val entries: List<FontCatalogEntry>,
    val diagnostics: List<RouteDiagnostic>,
)

data class FontCatalogEntry(
    val sourceId: FontSourceID,
    val typefaceId: TypefaceID,
    val provenance: FontProvenance,
    val family: FontFamilyName,
    val style: FontStyleFacts,
    val genericFamilies: Set<GenericFontFamily>,
    val coverage: FontCoverageFacts,
    val variationAxes: List<VariationAxisFacts>,
    val license: FontLicenseFacts,
)
```

## Acceptance Criteria

- [x] Two catalog loads from the same bundled font bytes produce byte-identical `font-catalog.json`.
- [x] Each current catalog entry records source/typeface IDs, hashes, family/style/generic facts, script coverage, locale hints, emoji/color capability facts, variation axes, and provenance/license metadata.
- [x] Duplicate or conflicting face facts emit deterministic diagnostics and do not silently override earlier entries.
- [x] Catalog ordering is deterministic and independent of filesystem enumeration order.
- [x] Host-installed fonts are absent from the bundled deterministic catalog unless committed as fixture bytes.

## Required Evidence

- `font-catalog.json` with catalog generation, entry ordering, font source/typeface IDs, coverage facts, variation facts, license/provenance, and diagnostics.
- Fixtures: bundled Latin/Greek/Cyrillic face, bundled Hebrew/Arabic face, bundled Devanagari/Thai face, bundled CJK face, optional bundled emoji-capable face metadata fixture, duplicate-family conflict fixture.
- Diagnostics asserted in tests: `font.required-table-missing`, `font.outline-format-unsupported`, `font.fallback-family-unavailable`, `font.catalog.duplicate-face`, `font.catalog.provenance-missing`.
- Review evidence that bundled font bytes and licenses are deterministic and repo-owned or license-compatible.

## Fallback / Refusal Behavior

- Unsupported or malformed paths must emit one of: `text.fallback.catalog-entry-malformed`, `font.source.host-dependent`.
- The diagnostic must name the affected range, glyph, cluster, lookup, font source, or route object when that subject exists.
- Silent fallback to platform/native/font engine behavior is not allowed; the ticket remains `tracked-gap` until the listed evidence and validation pass.

## Dashboard Impact

- Expected row: `Add bundled deterministic font catalog`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:core:test --tests '*FontCatalog*' --tests '*FontFixtureManifest*' --tests '*DiagnosticTaxonomy*'
rtk ./gradlew --no-daemon :font:core:test
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_font_fixture_assets.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_claim_dashboard.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_dump_index.py
```

## Status Notes

- `proposed`: Bundled catalog is the deterministic fallback foundation.
- Move to `ready` only after catalog fields, fixture families, and provenance requirements are reviewed.
- `done`: `font-catalog.json` now materializes deterministic bundled Latin, Hebrew, Arabic, Devanagari, Thai, CJK, and emoji-capable rows with checked-in fixture provenance under `reports/font/fixtures/`, while `font-catalog-duplicate-face.json` locks the duplicate-family conflict evidence requested by the ticket. This closes the catalog-breadth gate only; fallback support, shaping support, platform-font parity, color-glyph rendering, and GPU text support remain explicit non-claims.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M7`
- `area:fallback`
- `claim:tracked-gap`
