---
id: "KFONT-M11-008"
title: "Add upload-before-sample ordering validation"
status: "ready"
milestone: "M11"
priority: "P0"
owner_area: "gpu-api"
claim_impact: "GPU-gated"
depends_on: ["KFONT-M11-004", "KFONT-M11-007"]
legacy_gate: ["dftext"]
---

# KFONT-M11-008 - Add upload-before-sample ordering validation

## PM Note

Ce ticket garantit qu'un draw texte ne peut pas échantillonner un atlas avant son upload ou après son éviction.

## Problem

Text atlas routes are only correct if resource uploads, generation validation, instance buffer uploads, draw sampling, eviction, compaction, clip/layer barriers, and destination-read barriers are ordered. The current catalog does not require proof that an atlas upload precedes sampling or that stale generations refuse. Without this, GPU text can pass once and then fail under atlas mutation.

## Scope

- Define `GPUTextOrderingToken` linking text artifact generation, atlas upload task, atlas page/resource generation, instance buffer upload, draw sampling, and eviction/compaction barriers.
- Validate upload-before-sample ordering for A8 atlas route and leave SDF/color/bitmap/SVG order cases as dependency-gated until routes exist.
- Emit `gpu-text-ordering-trace.json` with task IDs, dependency edges, generation checks, resource state, draw refs, and diagnostics.
- Add stale generation, missing upload edge, unsafe eviction-before-draw, and instance-upload-after-draw negative tests.
- Preserve visual order and route diagnostics through splits and barriers.

## Non-Goals

- Do not implement a general GPU task graph scheduler.
- Do not execute all text routes; unsupported route order cases may remain diagnostic fixtures.
- Do not hide ordering problems by rebuilding atlases without budget and diagnostics.
- Do not retire `dftext` without SDF upload and sampling evidence.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/06-gpu-renderer-handoff.md`
- `.upstream/specs/gpu-renderer/21-text-glyph-pipeline.md`
- `.upstream/target/high-performance-wgsl-pipeline-target.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`

## Design Sketch

```kotlin
data class GPUTextOrderingToken(
    val tokenId: GPUTextOrderingTokenId,
    val artifactGeneration: ArtifactGeneration,
    val atlasUploadTask: GPUTaskId?,
    val atlasPageGeneration: AtlasPageGeneration?,
    val instanceUploadTask: GPUTaskId?,
    val drawTask: GPUTaskId,
    val barriers: List<GPUTextOrderingBarrier>,
)

data class GPUTextOrderingValidation(
    val token: GPUTextOrderingToken,
    val dependencyEdges: List<GPUTaskEdge>,
    val diagnostics: List<GPUTextDiagnostic>,
)
```

## Acceptance Criteria

- [ ] Accepted A8 atlas draws have explicit upload-before-sample and instance-upload-before-draw edges.
- [ ] Stale atlas generation refuses with `unsupported.text.atlas_generation_stale`.
- [ ] Missing upload edge refuses with `unsupported.text.upload_plan_missing` or `unsupported.text.upload_failed`.
- [ ] Eviction or compaction cannot move before dependent draws without a recorded barrier.
- [ ] `gpu-text-ordering-trace.json` is deterministic and references subrun, resource, upload, and draw IDs.

## Required Evidence

- `gpu-text-ordering-trace.json` accepted A8 route fixture with upload, instance upload, draw, and generation validation edges.
- Negative fixtures for missing upload, stale generation, eviction-before-draw, and instance-upload-after-draw.
- Dashboard note keeping `dftext` open until SDF upload-before-sample evidence exists.

## Fallback / Refusal Behavior

- Missing ordering proof refuses route execution rather than sampling existing resources opportunistically.
- Stale generations rebuild only through an explicit upload plan within budget; otherwise they refuse.
- Legacy gate `dftext` remains open until SDF ordering and sampling evidence are linked.

## Dashboard Impact

- Expected row: `Text upload-before-sample ordering`.
- Expected classification: `GPU-gated`.
- Claim promotion allowed: no, unless ordering traces and stale-generation refusals are attached.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:gpu-api:test --tests '*TextOrdering*'
```

## Status Notes

- `proposed`: Depends on an A8 route plan and resource/upload/binding contracts.
- Move to `ready` only after ordering token fields and negative ordering cases are reviewed.
- `blocked` (2026-06-16): Readiness audit confirmed this ticket depends on
  `KFONT-M11-004` and `KFONT-M11-007`. There is no accepted A8 route, no
  renderer resource/upload/binding plan, and no `GPUTextOrderingToken`
  evidence tying upload task, instance upload, draw sampling, generation
  validation, eviction, and barriers. Remaining gate: finish A8 route and
  resource/upload/binding contracts, then re-review ordering token fields and
  negative ordering cases.
- `blocked` (2026-06-23): `KFONT-M11-004` and `KFONT-M11-006` are now done,
  and `KFONT-M11-007` is the ready prerequisite for resource/upload/instance/
  binding contracts. This ticket stays blocked until those contracts land with
  upload task, instance upload, draw sampling, generation validation, eviction,
  and barrier evidence.
- `ready` (2026-06-23): `KFONT-M11-007` now lands resource/upload/instance/
  binding plan dumps with upload-before-sample and instance-upload-before-draw
  dependency labels. This ticket can now validate real ordering traces,
  generation checks, eviction barriers, and negative ordering cases without a
  new PM decision.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M11`
- `area:gpu-api`
- `claim:GPU-gated`
- `legacy:dftext`
