---
id: "KFONT-M11-006"
title: "Add `GPUTextSubRunPlan` splitting tests"
status: "blocked"
milestone: "M11"
priority: "P1"
owner_area: "gpu-api"
claim_impact: "GPU-gated"
depends_on: ["KFONT-M11-003", "KFONT-M11-004", "KFONT-M11-005"]
legacy_gate: null
---

# KFONT-M11-006 - Add `GPUTextSubRunPlan` splitting tests

## PM Note

Ce ticket prouve que le texte est découpé en subruns GPU sans perdre l'ordre visuel ni les glyphes.

## Problem

The renderer cannot draw a full text command as one opaque blob. It must split text by representation, route, atlas page, atlas generation, transform class, material, clip, layer, destination-read requirement, instance-buffer budget, and pipeline compatibility. Without tested `GPUTextSubRunPlan` splitting, atlas residency or batching optimizations can change output order or drop glyphs under resource pressure.

## Scope

- Define dumpable `GPUTextSubRunPlan` values derived from one `DrawTextRunPayload`.
- Split by representation, route, atlas descriptor/page, generation token, transform class, material/color plan, clip/stencil state, layer/destination-read requirements, instance buffer budget, and binding compatibility.
- Preserve visual order where overlapping glyph coverage or blending makes order observable.
- Emit `gpu-text-subrun-plan.json` with split reasons, source glyph ranges, bounds, route outcome, ordering token refs, and diagnostics.
- Add tests for multi-page atlas split, mixed A8/SDF refusal split, color/bitmap/SVG dependency-gated split, clip/layer split, and instance-budget split.

## Non-Goals

- Do not implement every render step; unsupported routes can produce refused subruns.
- Do not sort subruns across unsafe blend, clip, layer, upload, or generation barriers.
- Do not put glyph IDs or atlas coordinates into `MaterialKey`.
- Do not allocate GPU buffers in the planning contract test.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/06-gpu-renderer-handoff.md`
- `.upstream/specs/gpu-renderer/21-text-glyph-pipeline.md`
- `.upstream/target/high-performance-wgsl-pipeline-target.md`

## Design Sketch

```kotlin
data class GPUTextSubRunPlan(
    val parentCommandId: DrawCommandId,
    val subRunId: GPUTextSubRunId,
    val sourceGlyphRange: GlyphRange,
    val representation: GPUTextRepresentation,
    val route: GPUTextRoute,
    val bounds: TextSubRunBounds,
    val atlasEntryRefs: List<GPUTextAtlasEntryRef>,
    val instanceLayout: GPUTextInstanceLayout,
    val materialPlanRef: MaterialPlanRef,
    val orderingToken: GPUTextOrderingToken,
    val splitReasons: List<GPUTextSplitReason>,
    val diagnostics: List<GPUTextDiagnostic>,
)
```

## Acceptance Criteria

- [ ] Splits are deterministic for the same payload, artifact registry, and budget policy.
- [ ] Multi-page or multi-generation atlas entries split into compatible subruns.
- [ ] Unsupported SDF/color/bitmap/SVG artifacts produce refused subruns with route-specific diagnostics.
- [ ] Instance-budget and binding-budget splits preserve glyph coverage order.
- [ ] `gpu-text-subrun-plan.json` records source glyph ranges, split reasons, bounds, route, ordering token, and diagnostic outcome.

## Required Evidence

- `gpu-text-subrun-plan.json` fixtures for atlas page split, atlas generation split, representation split, clip/layer split, and instance-budget split.
- Refusal fixture mixing accepted A8 subrun with dependency-gated SDF/color subruns.
- Ordering evidence showing unsafe sort barriers are preserved.

## Fallback / Refusal Behavior

- Split failures refuse the affected command with `unsupported.text.instance_buffer_budget_exceeded`, `unsupported.text.binding_layout_unavailable`, or a narrower reason.
- Planner must not drop glyphs to satisfy resource budgets.
- Refused subruns remain associated with source text/glyph ranges for PM evidence.

## Dashboard Impact

- Expected row: `GPUTextSubRunPlan splitting`.
- Expected classification: `GPU-gated`.
- Claim promotion allowed: no, unless split fixtures and refusal diagnostics are attached.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:gpu-api:test --tests '*GPUTextSubRunPlan*'
```

## Status Notes

- `proposed`: Builds on normalized `DrawTextRun`, accepted A8 route, and dependency-gated refusal mapping.
- Move to `ready` only after split reason taxonomy and visual-order policy are reviewed.
- `blocked` (2026-06-16): Readiness audit confirmed the split tests depend
  on `KFONT-M11-004`. `font:gpu-api` currently has only registry,
  `DrawTextRunPayload`, leakage validation, route refusals, and telemetry;
  it does not expose a production `GPUTextSubRunPlan`, atlas-entry
  compatibility model, or accepted A8 route to split. Remaining gate: finish
  `KFONT-M11-004`, then re-review split reason taxonomy and visual-order
  policy.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M11`
- `area:gpu-api`
- `claim:GPU-gated`
