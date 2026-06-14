---
id: "KFONT-M9-006"
title: "Add glyph cache telemetry"
status: "proposed"
milestone: "M9"
priority: "P2"
owner_area: "glyph"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M9-005"]
legacy_gate: ["dftext"]
---

# KFONT-M9-006 - Add glyph cache telemetry

## PM Note

Ce ticket rend les coûts cache glyph visibles avant de transformer les budgets en gates produit.

## Problem

Glyph artifact work introduces mask generation, SDF generation, atlas packing, eviction, invalidation, and upload-preparation costs. Without telemetry, performance regressions and cache churn remain hidden, and `dftext` evidence cannot separate CPU generation cost from future GPU upload cost. The gap is a deterministic cache inventory and counter schema tied to artifact keys.

## Scope

- Define glyph cache telemetry counters for route counts, A8 generation time, SDF generation time, atlas pack time, cache hit/miss, eviction count, resident bytes, invalidation count, upload byte count, and artifact budget refusals.
- Record environment, font source set, Unicode data version, cold/warm cache state, sample count, median, p90, and max where repeated.
- Emit `glyph-cache-inventory.json` and `glyph-cache-telemetry.json` with key preimages and memory accounting.
- Add deterministic dump mode that replaces timings with stable fixture counters for unit tests.
- Surface telemetry in PM/dashboard evidence without turning advisory budgets into blocking gates.

## Non-Goals

- Do not establish release-blocking performance thresholds in this ticket.
- Do not measure GPU time or WebGPU upload execution; M11 and M12 own those promoted gates.
- Do not mask unsupported routes by prewarming them.
- Do not retire `dftext` based on telemetry alone.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/04-glyph-representation-and-artifacts.md`
- `.upstream/specs/pure-kotlin-text/08-performance-budgets-and-telemetry.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`

## Design Sketch

```kotlin
data class GlyphCacheTelemetry(
    val environment: MeasurementEnvironment,
    val fontSourceSet: FontSourceSetID,
    val unicodeVersion: UnicodeVersion,
    val cacheState: CacheState,
    val routeCounts: Map<GlyphRepresentationRoute, Int>,
    val artifactCounters: GlyphArtifactCounters,
    val atlasCounters: GlyphAtlasCounters,
    val budgetRefusals: List<TextDiagnostic>,
)

data class GlyphCacheInventoryEntry(
    val keyPreimageHash: StableHash,
    val artifactType: GlyphArtifactType,
    val residentBytes: Long,
    val generation: AtlasGeneration?,
    val invalidationToken: AtlasInvalidationToken?,
)
```

## Acceptance Criteria

- [ ] Telemetry separates A8 generation, SDF generation, atlas packing, cache lookup, eviction, invalidation, and upload-preparation bytes.
- [ ] Cache inventories include key preimage hashes, resident byte counts, generation tokens, and artifact type.
- [ ] Warm-cache and cold-cache fixtures are labeled and deterministic in test mode.
- [ ] Budget refusals emit `text.glyph.artifact-budget-exceeded` or a narrower reason with cache domain and key hash.
- [ ] Dashboard evidence states that budgets are advisory until a future acceptance update promotes them.

## Required Evidence

- `glyph-cache-inventory.json` fixture with A8, SDF, and evicted entries.
- `glyph-cache-telemetry.json` fixture showing route counts, hit/miss/eviction/invalidation counters, resident bytes, and upload-preparation bytes.
- Budget-refusal diagnostic snapshot for cache memory and atlas upload-preparation limits.

## Fallback / Refusal Behavior

- Telemetry collection failure must not fabricate support; it emits `text.glyph.telemetry-unavailable` and keeps the claim blocked.
- Warmup may prepare metadata but must not hide unsupported glyph routes.
- Legacy gate `dftext` remains open until SDF artifacts have correctness and GPU evidence, not just cache metrics.

## Dashboard Impact

- Expected row: `Glyph cache telemetry`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no, unless cache inventory and telemetry dumps are attached and labeled advisory.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:glyph:test --tests '*GlyphCache*'
```

## Status Notes

- `proposed`: Adds observability for artifact planning and atlas residency before M12 performance gates.
- Move to `ready` only after telemetry schema, deterministic dump mode, and advisory-budget wording are reviewed.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M9`
- `area:glyph`
- `claim:tracked-gap`
- `legacy:dftext`
