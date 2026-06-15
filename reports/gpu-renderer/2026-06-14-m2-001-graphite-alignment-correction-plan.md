# M2-001 FillRRect Graphite Alignment - Correction Report

Date: 2026-06-14
Scope: `KGPU-M2-001 - Add native FillRRect first expansion route`
Branch context: `codex/gpu-renderer-m1-wave`
Status target: keep `review` until fresh GPU-lane evidence exists.

## Goal

Align the existing Kanvas `FillRRect` M2-001 implementation with the Graphite
algorithm references, without widening product support claims.

## Hard Constraints

- Do not port Ganesh or Graphite.
- Do not rebuild SkSL.
- WGSL remains the shader target.
- Do not activate `gpu-raster` product routing for `FillRRect`.
- Do not move `KGPU-M2-001` to `done` unless fresh adapter-backed or explicitly
  skipped GPU evidence is validated.
- Preserve current non-claims: no broad rrect, path, stroke, gradient, complex
  clip, text, image, filter, saveLayer, destination-read, or runtime-effect
  support.

## Source Files To Read First

- `/Users/chaos/.codex/worktrees/e281/kanvas/AGENTS.md`
- `/Users/chaos/.codex/RTK.md`
- `/Users/chaos/.codex/worktrees/e281/kanvas/.upstream/specs/gpu-renderer/tickets/M2-rect-rrect-gradient-scissor/KGPU-M2-001-add-native-fillrrect-first-expansion-route.md`
- `/Users/chaos/.codex/worktrees/e281/kanvas/.upstream/specs/gpu-renderer/14-first-slice-contract.md`
- `/Users/chaos/.codex/worktrees/e281/kanvas/.upstream/specs/gpu-renderer/09-draw-family-support-matrix.md`
- `/Users/chaos/.codex/worktrees/e281/kanvas/.upstream/specs/gpu-renderer/25-path-stroke-geometry-pipeline.md`
- `/Users/chaos/.codex/worktrees/e281/kanvas/.upstream/specs/gpu-renderer/tickets/GRAPHITE-ALGORITHM-REFERENCES.md`

## Graphite References

Use these only as algorithm references:

- `AnalyticRRectRenderStep.cpp`: per-corner rrect encoding and analytic coverage
  math.
- `Device.cpp`: simple-shape routing, inner-fill bounds, simple rrect
  eligibility.
- `DrawList.cpp`: sorted draw pass construction, pipeline/uniform/texture/scissor
  state changes.

Do not copy Graphite structures or behavior wholesale.

## Current Kanvas State

Current implementation adds isolated `:gpu-renderer` evidence for `FillRRect`:

- `GPURRect` currently stores only `radiusX` and `radiusY`.
- `GPUFillRRectCommandBuilder` captures rrect facts.
- `GPUFirstRoutePlanner.plan(FillRRect)` emits:
  - `native.fill_rrect.solid`
  - `rrect.fill.coverage`
  - `pending.pipeline.fill_rrect.solid.rgba8unorm.src_over`
- Accepted path is pre-materialization only.
- Refusals exist for unsupported radii, perspective, complex clip, blend, target
  format, and missing capability.
- No `gpu-raster` product route is enabled.

## Review Findings To Correct

### 1. RRect Radii Model Is Too Narrow

Graphite encodes x/y radii per corner: TL, TR, BR, BL. Kanvas currently has a
single `radiusX/radiusY`.

Correction options:

- Preferred: extend Kanvas rrect descriptor to per-corner radii.
- Minimal: keep uniform-only support and add explicit refusal for non-uniform
  rrects.

Recommended path: implement per-corner model, with a helper constructor for
uniform radii to preserve simple tests.

Expected diagnostics:

- `unsupported.geometry.rrect_radii`
- Add a more precise diagnostic if useful:
  - `unsupported.geometry.rrect_nonuniform_radii`
  - only if the current scope intentionally refuses non-uniform radii.

### 2. Native Route Label Must Not Imply Executed GPU Support

Current labels such as `native.fill_rrect.solid` are acceptable only as
route-candidate evidence.

Correction:

- Keep report/status wording explicit:
  - `native route candidate`
  - `pre-materialization evidence`
  - `not adapter-backed`
  - `not product activated`
- Do not claim executed GPU support from `rrect.fill.coverage` alone.

### 3. Missing Coverage/WGSL Evidence

Graphite's `AnalyticRRectRenderStep` includes real analytic coverage math.
Kanvas currently names `rrect.fill.coverage` but does not yet validate
WGSL/reflection/payload/readback for rrect.

Correction:

- Either add real WGSL/reflection/pipeline-key evidence for rrect, or keep the
  ticket in `review` with this as a remaining gate.
- Do not mark `done` from command/analysis/pass evidence alone.

### 4. Transform Policy Needs Explicit Scope

Spec allows accepted rounded-rect geometry when transform is accepted by the
rrect render step. Current Kanvas model accepts only `Identity` and `Translate`.

Correction:

- Add explicit `Scale`/`Affine` transform facts and refuse them deterministically
  for this wave, or implement them fully.
- Recommended minimal correction: add explicit refused cases for scale/affine
  until coverage math supports them.

## Implementation Plan

1. Add RED tests first.
   - Per-corner radii captured or refused.
   - Non-uniform radii behavior is deterministic.
   - Scale/affine transforms refuse explicitly if not implemented.
   - Route dumps do not imply product activation.
   - No resource/materialization/readback is claimed by the accepted fixture.
2. Update `NormalizedDrawCommand.kt`.
   - Add per-corner radii model or explicit non-uniform refusal input.
   - Keep command capture immutable.
   - Avoid material lowering or resource allocation in command builder.
3. Update `AnalysisContracts.kt`.
   - Validate per-corner radii.
   - Keep unsupported cases terminal.
   - Ensure accepted route remains pre-materialization unless WGSL/GPU evidence
     is added.
4. Update route/pass/recording tests.
   - Accepted uniform and/or per-corner rrect.
   - Refused invalid radii.
   - Refused transform cases.
   - Refused unsupported scope remains no-pass-work.
5. Update report/ticket notes.
   - Keep `KGPU-M2-001` in `review` unless GPU-lane evidence is completed.
   - Document remaining gate precisely.

## Files Likely Touched

- `/Users/chaos/.codex/worktrees/e281/kanvas/gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/commands/NormalizedDrawCommand.kt`
- `/Users/chaos/.codex/worktrees/e281/kanvas/gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/analysis/AnalysisContracts.kt`
- `/Users/chaos/.codex/worktrees/e281/kanvas/gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/PassContracts.kt`
- `/Users/chaos/.codex/worktrees/e281/kanvas/gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/routing/RoutingContracts.kt`
- `/Users/chaos/.codex/worktrees/e281/kanvas/gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/commands/NormalizedDrawCommandTest.kt`
- `/Users/chaos/.codex/worktrees/e281/kanvas/gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/analysis/FirstRoutePlannerTest.kt`
- `/Users/chaos/.codex/worktrees/e281/kanvas/gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPURecorderTest.kt`
- `/Users/chaos/.codex/worktrees/e281/kanvas/reports/gpu-renderer/2026-06-14-m1-m2-ticket-wave.md`
- `/Users/chaos/.codex/worktrees/e281/kanvas/.upstream/specs/gpu-renderer/tickets/M2-rect-rrect-gradient-scissor/KGPU-M2-001-add-native-fillrrect-first-expansion-route.md`

## Validation Commands

Run targeted tests first:

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommandTest
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.analysis.FirstRoutePlannerTest
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.recording.GPURecorderTest
```

Then broader validation:

```bash
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk ./gradlew --no-daemon :gpu-raster:test --tests '*GpuRenderer*'
rtk git diff --check
```

## Definition Of Done For This Correction

This correction is complete when:

- RRect geometry facts match the chosen scope: per-corner supported or
  non-uniform explicitly refused.
- Graphite comparison is documented as algorithm-reference alignment, not a
  port.
- No product support claim is widened.
- `KGPU-M2-001` remains `review` unless GPU-lane evidence is added.
- Validation commands pass.
- Remaining gate is explicitly documented:
  - adapter-backed WebGPU evidence, or
  - explicitly skipped GPU evidence accepted by the ticket/spec.

## Recommended Final Status

After this correction, the M2 simple closeout scene, and independent review:

- `KGPU-M2-001`: `done`
- `KGPU-M2-002`: `done`
- `KGPU-M2-003`: `done`
- `KGPU-M2-004`: `done`

Reason: `M2SimpleSceneEvidenceTest` now supplies contract-fixture evidence for
the M2 rrect, linear-gradient, scissor, batching, and skipped GPU-lane gates.
Independent review `019ec7aa-f95b-7f40-9f40-1bf80d87d2b9` accepted that
evidence for `done` and confirmed no product route activation or support claim
was widened.
