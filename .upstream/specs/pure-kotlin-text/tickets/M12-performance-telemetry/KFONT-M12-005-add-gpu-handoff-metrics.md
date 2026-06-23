---
id: "KFONT-M12-005"
title: "Add GPU handoff metrics"
status: "done"
milestone: "M12"
priority: "P1"
owner_area: "telemetry"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M12-001", "KFONT-M11-004", "KFONT-M11-005"]
legacy_gate: ["dftext"]
---

# KFONT-M12-005 - Add GPU handoff metrics

## PM Note

Ce ticket montre ce que le renderer GPU reçoit vraiment du texte: artifacts typés, uploads, réutilisation et refus.

## Problem

The GPU handoff spec forbids the renderer from parsing fonts, reshaping text, or replacing unsupported text with a CPU-rendered full texture. M12 has no metrics proving how many `DrawTextRun` commands used typed artifacts, how many upload dependencies were created, which routes were reused, or which routes refused. Without this, GPU text regressions and `dftext` gate decisions cannot be audited.

## Scope

- Emit GPU handoff metrics for artifact registry lookup time, artifact type count, `DrawTextRun` route count, selected GPU route class, upload dependency count, upload byte count, upload plan reuse, artifact reuse, stale generation refusals, and artifact budget refusals.
- Cover typed text artifacts: `GlyphAtlasArtifact`, `SDFGlyphAtlasArtifact`, `GlyphUploadPlan`, `ColorGlyphPlan`, `BitmapGlyphPlan`, `SVGGlyphPlan`, and `OutlineGlyphPlan`.
- Record refusal diagnostics for unregistered artifacts, nondeterministic artifact keys, missing upload plans, missing GPU capability, transform refusal, color/SVG refusal, and forbidden CPU-rendered text textures.
- Keep `Sk*` objects, font bytes, live GPU handles, and material-key leakage out of handoff telemetry dumps.
- Use KFONT-M12-001 aggregation while preserving the handoff boundary from `06-gpu-renderer-handoff.md`.

## Non-Goals

- Do not promote support without the Required Evidence section attached.
- Do not claim GPU renderer support unless a dedicated GPU route ticket provides evidence.
- Do not migrate or rewrite Skia-like facade APIs in this ticket.
- Do not use HarfBuzz, FreeType, Fontations, AWT, JNI, CoreText, DirectWrite, or fontconfig as normative behavior.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/08-performance-budgets-and-telemetry.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`
- `.upstream/specs/pure-kotlin-text/01-font-source-sfnt-and-scalers.md`
- `.upstream/specs/pure-kotlin-text/02-opentype-layout-shaping-engine.md`
- `.upstream/specs/pure-kotlin-text/03-paragraph-engine.md`
- `.upstream/specs/pure-kotlin-text/04-glyph-representation-and-artifacts.md`
- `.upstream/specs/pure-kotlin-text/06-gpu-renderer-handoff.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`

## Design Sketch

```kotlin
data class GPUTextHandoffMetricSample(
    val drawTextRunId: DrawCommandID,
    val artifactTypes: Map<TextArtifactType, Int>,
    val gpuRouteCounts: Map<GPURouteClass, Int>,
    val registryLookupMicros: MetricDistribution,
    val uploadDependencyCount: Int,
    val uploadBytes: Long,
    val artifactReuseCount: Long,
    val uploadPlanReuseCount: Long,
    val refusedRouteCount: Int,
    val diagnostics: List<RouteDiagnostic>,
)

data class GPUTextRefusalMetric(
    val artifactKeyHash: String?,
    val route: TextRouteKind,
    val code: String,
    val dependencyGate: String?,
    val dashboardClaimImpact: ClaimImpact,
)
```

## Acceptance Criteria

- [x] Handoff samples count every typed artifact and every `DrawTextRun` route selected or refused.
- [x] Upload byte counts, upload dependency counts, artifact reuse, and upload-plan reuse are serialized separately.
- [x] Stale generation, artifact budget, capability, transform, color, SVG, and CPU-rendered-texture refusals keep stable `text.gpu.*` diagnostics.
- [x] Telemetry dumps prove no `Sk*` object, font bytes, live GPU handle, atlas coordinate, glyph ID list, or upload token leaks into `MaterialKey` identity.
- [x] The `dftext` legacy gate remains open unless SDF artifact, atlas/cache, transform policy, GPU route evidence, diagnostics, and dashboard updates are linked.

## Required Evidence

- `gpu-text-handoff-metrics.json` covering A8 atlas, SDF atlas or SDF-gated route, outline, color/bitmap/SVG planned or refused routes.
- `draw-text-run-upload-plan.json` with stable artifact key hashes, upload byte counts, dependency order, and reuse counters.
- Diagnostic snapshots for `text.gpu.artifact-unregistered`, `text.gpu.upload-plan-missing`, `text.gpu.atlas-generation-stale`, and `text.gpu.CPU-rendered-texture-forbidden`.
- No-`Sk*` and `MaterialKey` leakage report excerpt tied to the same fixtures.
- Dashboard trend excerpt with GPU handoff rows and `dftext` still visible when not retired.

## Fallback / Refusal Behavior

- Unsupported GPU routes emit `text.gpu.*` diagnostics and remain visible in telemetry; they are not converted into CPU-rendered texture compatibility.
- Missing typed artifacts refuse with `text.gpu.artifact-unregistered` or a narrower dependency diagnostic.
- Legacy gate(s) `dftext` remain open until implementation evidence, diagnostics, and dashboard updates are linked.

## Dashboard Impact

- Expected rows: `GPU text handoff metrics`, `GPU text upload metrics`, `GPU text route refusals`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no; this ticket exposes route costs and refusals but does not promote GPU text support alone.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelinePerformanceTrendWarnings
```

## Status Notes

- `proposed`: Initial markdown ticket written from the pure Kotlin font roadmap.
- Move to `ready` only after scope, dependencies, evidence, and validation commands are reviewed.
- `done`: `gpu-text-handoff-metrics.json` and `draw-text-run-upload-plan.json` now attach deterministic advisory GPU handoff/upload-plan evidence, stable refusal diagnostics, and bounded MaterialKey/no-`Sk*` leakage audit facts without promoting GPU support or release-gate claims.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M12`
- `area:telemetry`
- `claim:tracked-gap`
- `legacy:dftext`
