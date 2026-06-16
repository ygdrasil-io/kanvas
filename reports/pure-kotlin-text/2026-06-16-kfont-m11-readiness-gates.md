# KFONT M11 Readiness Gate Audit

Date: 2026-06-16
Status: readiness evidence, no support claim

## Scope

This audit reviewed the remaining M11 Typed GPU Handoff tickets after
`KFONT-M11-001`, `KFONT-M11-002`, `KFONT-M11-003`, and `KFONT-M11-005` reached
`done`. It decides whether `KFONT-M11-004` through `KFONT-M11-010` can move to
implementation without broadening GPU text support claims.

Authoritative baseline:

- `origin/master` at `10f8ffeb86783294760ea4854ccda2a2623c72ed`
- `.upstream/specs/pure-kotlin-text/README.md`
- `.upstream/specs/pure-kotlin-text/06-gpu-renderer-handoff.md`
- `.upstream/specs/gpu-renderer/21-text-glyph-pipeline.md`
- `.upstream/target/high-performance-wgsl-pipeline-target.md`
- `.upstream/specs/pure-kotlin-text/tickets/M11-gpu-handoff/README.md`
- `font/gpu-api/src/main/kotlin/org/graphiks/kanvas/glyph/gpu/`

## Verdict

No remaining M11 implementation ticket is ready now. The remaining tickets are
blocked by missing upstream text artifact and route-planning evidence:

| Ticket | Status | Blocking gate |
|---|---|---|
| `KFONT-M11-004` | `blocked` | Needs `KFONT-M9-003` A8 mask evidence and `KFONT-M9-005` atlas entry/page/generation/invalidation evidence before an A8 route plan, WGSL, and GPU proof can be claimed. |
| `KFONT-M11-006` | `blocked` | Depends on the accepted A8 route from `KFONT-M11-004`; there is no production `GPUTextSubRunPlan` contract yet. |
| `KFONT-M11-007` | `blocked` | Depends on `KFONT-M11-006`; existing `GPUTextUploadPlan` is CPU-side artifact metadata, not renderer resource/upload/instance/binding evidence. |
| `KFONT-M11-008` | `blocked` | Depends on `KFONT-M11-004` and `KFONT-M11-007`; there is no upload-before-sample task-edge or ordering-token evidence. |
| `KFONT-M11-009` | `blocked` | wgsl4k evolution fixtures exist, but real text WGSL modules/snippets, `GPUTextBinding` comparisons, and route evidence are absent. |
| `KFONT-M11-010` | `blocked` | Depends on `KFONT-M11-006` and `KFONT-M11-007`; there is no text `MaterialKey` leakage fixture tied to real subrun/resource/binding plans. |

## Current Code Evidence

`font:gpu-api` currently contains value-surface and diagnostic contracts for:

- `TextGPUArtifactRegistry`
- `TextGPUArtifactBundle`
- `DrawTextRunPayload`
- no-`Sk*` leakage validation
- route refusal diagnostics
- telemetry snapshots

It does not contain production contracts for:

- `GPUTextAtlasPlan`
- `GPUTextAtlasEntryRef`
- `GPUTextSubRunPlan`
- `GPUTextResourcePlan`
- `GPUTextBinding`
- `GPUTextOrderingToken`
- `TextWgslReflectionReport`
- `MaterialKeyLeakageCase`

The current `GlyphAtlasArtifact` type is a dumpable value object. It is not
evidence that the M9 glyph system has produced A8 masks, atlas entries, source
mask hashes, generation facts, eviction traces, or GPU upload/binding plans.

## Non-Claims

- This audit does not claim GPU text route support.
- This audit does not promote A8 atlas text, SDF text, outline text, color
  glyph, bitmap glyph, SVG glyph, emoji, or LCD support.
- This audit does not retire `dftext`, `scaledemoji_rendering`, or
  `coloremoji_blendmodes`.
- This audit does not turn wgsl4k dependency fixtures into text route support.
- CPU-rendered full text texture compatibility remains forbidden.

## Next Actionable Work

To unblock M11 implementation, the next actionable work is outside M11:

1. `KFONT-M9-003`: produce deterministic A8 mask artifact evidence.
2. `KFONT-M9-004`: produce SDF artifact boundary evidence needed by M9 atlas
   coverage.
3. `KFONT-M9-005`: produce atlas artifact, generation, invalidation, and
   eviction evidence.

After those gates are complete, re-evaluate `KFONT-M11-004` for an A8
`AtlasMaskSample` route plan with WGSL parser/reflection evidence and focused
GPU proof.

## Validation

```bash
rtk git diff --check
```
