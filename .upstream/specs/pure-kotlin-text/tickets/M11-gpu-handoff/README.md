# M11 - Typed GPU Handoff

## Goal

Expose pure Kotlin text artifacts to the GPU renderer through typed `DrawTextRun` payloads, artifact registry descriptors, route/refusal diagnostics, subrun/resource/upload/binding plans, upload-before-sample ordering, WGSL parser/reflection checks, and material-key leakage tests.

## Dependencies

M8 supplies paragraph and glyph run outputs. M9 supplies strike keys, A8/SDF artifacts, atlas generation, and cache facts. M10 supplies color, bitmap, SVG, and emoji plans. GPU renderer specs supply normalized draw commands, routing, task graph, binding ABI, material keys, texture ownership, and WGSL validation rules.

## Exit Criteria

- [ ] `DrawTextRunPayload` and text artifact registry dumps contain only immutable value objects and no `Sk*`, font bytes, native handles, raw GPU handles, or CPU-rendered full text texture routes.
- [ ] A8 atlas route evidence includes route plan, resource plan, instance layout, bindings, WGSL reflection, upload-before-sample ordering, and focused GPU proof.
- [ ] Unsupported SDF, outline, color, bitmap, SVG, stale atlas, missing upload, and unregistered artifact cases refuse with precise `text.gpu.*` and `unsupported.text.*` diagnostics.
- [x] `GPUTextSubRunPlan` splitting preserves visual order and records split reasons by representation, atlas page/generation, transform, material, clip, layer, destination-read, and budget.
- [ ] `MaterialKey` tests prove glyph IDs, atlas coordinates, generations, live handles, and upload tokens stay out of material identity.

## Tickets

| Ticket | Status | Priority | Claim Impact | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|
| [KFONT-M11-001 - Align `font:gpu-api` with target artifact registry](KFONT-M11-001-align-font-gpu-api-with-target-artifact-registry.md) | `done` | `P0` | `GPU-gated` | `gpu-api` | `KFONT-M9-002`, `KFONT-M10-010` | `dftext`, `scaledemoji_rendering`, `coloremoji_blendmodes` |
| [KFONT-M11-002 - Add no-`Sk*` leakage validation](KFONT-M11-002-add-no-sk-leakage-validation.md) | `done` | `P0` | `GPU-gated` | `gpu-api` | `KFONT-M11-001` | - |
| [KFONT-M11-003 - Add normalized `DrawTextRun` contract](KFONT-M11-003-add-normalized-drawtextrun-contract.md) | `done` | `P0` | `GPU-gated` | `gpu-api` | `KFONT-M8-002`, `KFONT-M9-002`, `KFONT-M11-002` | - |
| [KFONT-M11-004 - Wire atlas A8 artifact route](KFONT-M11-004-wire-atlas-a8-artifact-route.md) | `done` | `P0` | `GPU-gated` | `gpu-api` | `KFONT-M9-003`, `KFONT-M9-005`, `KFONT-M11-003` | - |
| [KFONT-M11-005 - Wire dependency-gated diagnostics for unsupported routes](KFONT-M11-005-wire-dependency-gated-diagnostics-for-unsupported-routes.md) | `done` | `P0` | `DependencyGated` | `gpu-api` | `KFONT-M11-001`, `KFONT-M11-003` | `dftext`, `scaledemoji_rendering`, `coloremoji_blendmodes` |
| [KFONT-M11-006 - Add `GPUTextSubRunPlan` splitting tests](KFONT-M11-006-add-gputextsubrunplan-splitting-tests.md) | `done` | `P1` | `GPU-gated` | `gpu-api` | `KFONT-M11-003`, `KFONT-M11-004`, `KFONT-M11-005` | - |
| [KFONT-M11-007 - Add resource/upload/instance/binding plan contracts](KFONT-M11-007-add-resource-upload-instance-binding-plan-contracts.md) | `done` | `P0` | `GPU-gated` | `gpu-api` | `KFONT-M11-006` | `dftext` |
| [KFONT-M11-008 - Add upload-before-sample ordering validation](KFONT-M11-008-add-upload-before-sample-ordering-validation.md) | `done` | `P0` | `GPU-gated` | `gpu-api` | `KFONT-M11-004`, `KFONT-M11-007` | `dftext` |
| [KFONT-M11-009 - Add WGSL parser/reflection validation for text routes](KFONT-M11-009-add-wgsl-parser-reflection-validation-for-text-routes.md) | `done` | `P0` | `GPU-gated` | `gpu-api` | `KFONT-M11-004`, `KFONT-M11-007` | `dftext`, `coloremoji_blendmodes` |
| [KFONT-M11-010 - Add `MaterialKey` leakage tests](KFONT-M11-010-add-materialkey-leakage-tests.md) | `ready` | `P0` | `GPU-gated` | `gpu-api` | `KFONT-M11-006`, `KFONT-M11-007` | - |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:gpu-api:test --tests '*Text*'
rtk ./gradlew --no-daemon :gpu-raster:pipelineConformanceTest --tests '*Text*'
```

Required evidence for this milestone includes `text-gpu-artifact-registry.json`, `text-gpu-no-sk-leakage-report.json`, `draw-text-run-payload.json`, `gpu-text-a8-route-plan.json`, `gpu-text-route-refusals.json`, `gpu-text-subrun-plan.json`, `gpu-text-resource-plan.json`, `gpu-text-upload-plan.json`, `gpu-text-instance-layout.json`, `gpu-text-binding-plan.json`, `gpu-text-ordering-trace.json`, `text-wgsl-reflection.json`, `text-wgsl-validation-report.json`, and `text-material-key-leakage-report.json`.

## Non-Claims

- Artifact registration is not GPU support.
- A8 atlas proof does not imply broad shaping, SDF, outline, color glyph, bitmap glyph, SVG glyph, emoji, or LCD support.
- CPU-rendered full text texture compatibility is forbidden.
- `dftext`, `scaledemoji_rendering`, and `coloremoji_blendmodes` remain open until route-specific GPU evidence, diagnostics, and dashboard updates are linked.

## Current Readiness Gate

2026-06-23 update: `KFONT-M11-009` is now `done` on deterministic text WGSL
reflection and validation evidence for the accepted A8 atlas subrun, including
reflected texture/sampler/uniform bindings, `TextParams` uniform layout,
instance input expectations, Kotlin plan comparisons, and parser/binding/SDF/
registration refusal diagnostics. `KFONT-M11-010` remains `ready` for full
`MaterialKey` leakage fixtures. The milestone still does not claim executed
uploads, broad GPU text support, SDF support, a general GPU task graph
scheduler, route promotion, or `dftext` retirement.

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and `../STATUS.md` in the same change.
