# KFONT-M11-007 GPU Text Resource Plan Evidence

Date: 2026-06-23
Status: implemented, locally revalidated.
Ticket: `.upstream/specs/pure-kotlin-text/tickets/M11-gpu-handoff/KFONT-M11-007-add-resource-upload-instance-binding-plan-contracts.md`

## Scope

This slice adds deterministic renderer-facing resource, upload, instance, and
binding plan evidence for the accepted A8 atlas subrun produced by
`KFONT-M11-006`. It remains contract-only: no WebGPU upload is executed, no
GPU handles are materialized, no broad GPU text support is claimed, and
`dftext` is not retired.

## Files

- `font/gpu-api/src/main/kotlin/org/graphiks/kanvas/glyph/gpu/GPUTextResourcePlan.kt`
- `font/gpu-api/src/test/kotlin/org/graphiks/kanvas/glyph/gpu/GPUTextResourcePlanTest.kt`
- `reports/pure-kotlin-text/gpu-text-resource-plan.json`
- `reports/pure-kotlin-text/gpu-text-upload-plan.json`
- `reports/pure-kotlin-text/gpu-text-instance-layout.json`
- `reports/pure-kotlin-text/gpu-text-binding-plan.json`
- `reports/pure-kotlin-text/gpu-text-resource-refusals.json`

## Evidence

- `GPUTextResourcePlan` records the A8 atlas resource ownership, atlas
  descriptor/page/entry refs, upload plan ID, instance buffer plan ID, binding
  plan ID, lifetime scope, diagnostics, and `resourceHandlesMaterialized=false`.
- `GPUTextRendererUploadPlan` records source artifact key, destination texture
  plan, page region, byte ranges, row stride, staging buffer requirements, and
  upload-before-sample dependency while keeping `uploadExecution=not-executed`.
- `GPUTextInstanceLayout` and `GPUTextInstanceBufferPlan` record stride,
  alignment, instance attributes, target/source/UV rects, page index, atlas
  generation, representation flags, and instance-upload-before-draw dependency.
- `GPUTextBinding` records resource slots, binding layout hash, atlas
  generation facts, material plan ref, and explicit fields excluded from
  `MaterialKey`: glyph IDs, atlas rects, atlas generations, upload tokens, and
  live texture handles.
- `gpu-text-resource-refusals.json` covers missing upload plan, upload budget
  overflow, unavailable atlas page, missing atlas entry, and unavailable
  binding layout with stable `text.gpu.*` and `unsupported.text.*` diagnostics.

## TDD Evidence

Red result:

```bash
rtk ./gradlew --no-daemon :font:gpu-api:test --tests '*GPUTextResourcePlan*'
```

Result: failed as expected at `:font:gpu-api:compileTestKotlin` because the
resource plan helpers, planner result type, and new route blockers did not
exist.

Green result:

```bash
rtk ./gradlew --no-daemon :font:gpu-api:test --tests '*GPUTextResourcePlan*'
```

Result: passed after adding the contract model, deterministic dumps, refusal
fixtures, and focused tests.

## Remaining Gate

KFONT-M11-007 closes only the resource/upload/instance/binding contract gate.
It does not claim executed upload-before-sample ordering, route-specific WGSL
parser/reflection validation, full `MaterialKey` leakage validation, SDF/
outline/color/bitmap/SVG support, broad GPU text support, or `dftext`
retirement. `KFONT-M11-008`, `KFONT-M11-009`, and `KFONT-M11-010` are now
ready to own those remaining gates.
