---
id: "KFONT-M9-005"
title: "Add atlas eviction and invalidation tests"
status: "done"
milestone: "M9"
priority: "P1"
owner_area: "glyph"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M9-001", "KFONT-M9-003", "KFONT-M9-004"]
legacy_gate: ["dftext"]
---

# KFONT-M9-005 - Add atlas eviction and invalidation tests

## PM Note

Ce ticket prouve que les atlas glyph ne réutilisent pas des entrées stale après variation, palette ou éviction.

## Problem

A glyph atlas artifact is useful only if its generation, entries, budget, and invalidation rules are visible. The current ticket does not require tests for stale generations, eviction, palette or variation invalidation, atlas capacity overflow, or source mask hash changes. Without those checks, GPU handoff could sample old atlas rectangles or silently drop glyphs under cache pressure.

## Scope

- Define deterministic `GlyphAtlasArtifact` and `SDFGlyphAtlasArtifact` dumps for A8 and SDF inputs.
- Record artifact key preimage, compact hash, atlas generation, page/entry list, dimensions, row stride, source mask hashes, upload byte hash, memory budget, lifetime class, and invalidation token.
- Add tests for eviction, atlas-capacity refusal, stale generation detection, font source invalidation, variation change, palette change, SDF spread change, and renderer descriptor version change.
- Emit `glyph-atlas.json`, `glyph-atlas-eviction-trace.json`, and stale-generation diagnostics.
- Keep GPU texture creation and upload execution for M11; this ticket owns CPU-prepared atlas artifacts only.

## Non-Goals

- Do not implement WebGPU texture allocation or bind groups.
- Do not merge glyph atlas lifetime with image, path, or coverage atlas lifetime.
- Do not hide atlas overflow by dropping glyphs.
- Do not retire `dftext` without M11 sampling and ordering validation.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/04-glyph-representation-and-artifacts.md`
- `.upstream/specs/pure-kotlin-text/06-gpu-renderer-handoff.md`
- `.upstream/specs/pure-kotlin-text/08-performance-budgets-and-telemetry.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`

## Design Sketch

```kotlin
data class GlyphAtlasArtifact(
    val artifactKey: GlyphAtlasArtifactKey,
    val generation: AtlasGeneration,
    val format: GlyphMaskFormat,
    val dimensions: IntSize,
    val entries: List<GlyphAtlasEntry>,
    val sourceMaskHashes: List<StableHash>,
    val uploadByteHash: StableHash,
    val invalidationToken: AtlasInvalidationToken,
    val diagnostics: List<TextDiagnostic>,
)

data class GlyphAtlasEntry(
    val strikeKeyHash: StableHash,
    val rect: RectI,
    val sourceBounds: RectI,
    val paddingPx: Int,
    val sourceMaskHash: StableHash,
)
```

## Acceptance Criteria

- [x] Eviction changes atlas generation and records which strike keys were evicted.
- [x] Variation, palette, SDF spread, renderer descriptor version, and source font invalidation produce new keys or stale-generation diagnostics.
- [x] Atlas capacity overflow emits `text.glyph.atlas-capacity-exceeded` and never drops glyphs silently.
- [x] `glyph-atlas.json` includes every entry rect, source mask hash, upload byte hash, invalidation token, and budget fact.
- [x] GPU consumers can detect stale atlas generations from the artifact dump without parsing fonts or masks again.

## Required Evidence

- `glyph-atlas.json` fixture for A8 atlas and SDF atlas inputs.
- `glyph-atlas-eviction-trace.json` showing eviction order, generation increments, invalidation token changes, and resident byte counts.
- Refusal fixture for atlas capacity overflow and stale generation reuse.
- Diagnostic snapshot with `text.glyph.atlas-generation-stale` and `text.glyph.atlas-capacity-exceeded`.

## Fallback / Refusal Behavior

- Stale atlas entries refuse until regenerated or explicitly rebuilt within budget.
- Capacity overflow may split the plan only if the split preserves all glyphs and records the generation boundaries.
- Legacy gate `dftext` remains open until SDF atlas artifacts have M11 upload-before-sample evidence.

## Dashboard Impact

- Expected row: `Glyph atlas eviction and invalidation`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no, unless atlas generation and stale-refusal evidence are attached.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:glyph:test --tests '*Atlas*'
rtk ./gradlew --no-daemon :font:glyph:test
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_font_fixture_assets.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_claim_dashboard.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_font_fixtures.py
```

## Status Notes

- `done`: CPU atlas artifact evidence now covers deterministic A8/SDF atlas dumps, eviction order, generation increments, invalidation-token changes, source-mask hashes, and stale-generation/capacity diagnostics while keeping GPU upload, sampling, and `dftext` retirement explicitly gated on M11.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M9`
- `area:glyph`
- `claim:tracked-gap`
- `legacy:dftext`
