---
id: KGPU-M31-006
title: "Execute KanvasSurface recording to pixels — bridge renders real GPU output (prereq for M31-005 parity)"
status: review
milestone: M31
priority: P0
owner_area: execution-backend
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: true
adapter_required: true
depends_on: [KGPU-M29-001, KGPU-M31-001]
legacy_gate: null
---

# KGPU-M31-006 - Execute KanvasSurface recording to pixels — bridge renders real GPU output (prereq for M31-005 parity)

## PM Note

Le renderer Kanvas activé par défaut en production (M30-002 + M31-001) **ne rend
rien au niveau pixel** sur le chemin bridge SkCanvas : `Surface.flush()` retourne
un *recording* (graphe de tâches) qui n'est **jamais exécuté**, et le `SkSurface`
de sortie n'est jamais écrit. Ce ticket câble l'exécution GPU→pixels pour que le
chemin par défaut produise vraiment du rendu. C'est le pré-requis qui débloque la
parité (KGPU-M31-005) et qui rend l'activation production réelle plutôt que no-op.

## Problem

Surfaced by the 2026-06-25 review feasibility check:

- `:kanvas` `Surface`/`Canvas` (`kanvas/src/main/.../{Surface,Canvas,Frame}.kt`)
  record `NormalizedDrawCommand`s into a `GPURecording`. `Surface.flush()` =
  `recorder.close()` → `Frame(recording)` (`Surface.kt`). `Frame` exposes only
  `isEmpty` (`recording.taskList.tasks.isEmpty()`) — **no pixels, no readback**.
- Verified by grep: **nothing** in `:kanvas`, `:kanvas-skia-bridge`, or
  `:integration-tests` executes/submits the recording to pixels (no `readRgba`,
  `readPixels`, `toBitmap`, `GPUExecutionContext`, or `GPUBackendRuntime` use on
  the `:kanvas` `Surface` recording).
- The offscreen GPU backend (`GPUBackendRuntimeWgpu` + `GPUBackendOffscreenTarget`,
  used by M28's `RectOnlyOffscreenRenderer` for the **`GPURendererScene`** model) is
  a **separate** path; it is NOT wired to consume the `:kanvas` `Surface` recording.
- Consequence: the production-default renderer (M30-002/M31-001) is a **pixel-level
  no-op** for the SkCanvas bridge — drawing via `SkiaKanvasSurface` leaves the
  wrapped `SkSurface` blank unless the `useLegacyGpuRaster` rollback is set.

## Scope

- Add a GPU execution path that consumes a `:kanvas` `Surface`/`Frame` `GPURecording`
  (its `NormalizedDrawCommand`s / task list), executes it on a
  `GPUBackendOffscreenTarget` via the offscreen GPU backend, and reads back RGBA.
- Expose this as e.g. `Surface.renderToRgba(): ByteArray` (and/or write the result
  into the wrapped output) so the bridge produces real pixels.
- Reuse the M28 offscreen execution (`GPUBackendRuntimeWgpu`, `readRgba`) rather
  than a new backend.
- Respect the environment constraint: WebGPU is only available via the application
  render task (not the JUnit test JVM); provide a render-task / main entry point
  to produce a `render.png` like `renderGpuRendererSceneOffscreen` does.

## Non-Goals

- No new draw families; no windowed/native (Kadre) runtime.
- Not the bridge↔legacy pixel-parity comparison itself (that is KGPU-M31-005).
- Not fixing `Canvas.drawImage` (currently lowered to a solid-color FillRect) or
  non-`SrcOver` blend support — track separately.

## Spec Sources

- `.upstream/specs/gpu-renderer/10-gpu-execution-context-submission.md`
- `.upstream/specs/gpu-renderer/37-draw-packet-command-stream.md`
- `.upstream/specs/gpu-renderer/tickets/M31-production-activation/KGPU-M31-001-production-activation.md`
- `kanvas/src/main/kotlin/org/graphiks/kanvas/Surface.kt`
- `gpu-renderer-scenes/src/main/.../offscreen/RectOnlyOffscreenRenderer.kt` (offscreen execution precedent)

## Design Sketch

```kotlin
// Execute the kanvas Surface recording on the offscreen GPU backend and read back.
fun Surface.renderToRgba(): ByteArray {
    val recording = recorder.snapshot() // or flush()
    GPUBackendRuntimeFactory.createOrNull()?.use { session ->
        session.createOffscreenTarget(GPUOffscreenTargetRequest(width, height, format.label)).use { target ->
            target.encode(clearColor = ...) { execute(recording.taskList) /* dispatch each command */ }
            return target.readRgba()
        }
    } ?: error("webgpu-context-unavailable")
}
```

## Acceptance Criteria

- [ ] Drawing a representative scene via `SkiaKanvasSurface` / `Surface` produces
      **non-empty** pixel output (`nonTransparentPixels > 0`) read back as RGBA.
- [ ] A simple solid-fill scene rendered through the bridge matches a CPU reference
      within tolerance (proves real rendering, not blank/garbage).
- [ ] Output is reproducible via a gradle render task (GPU available there).
- [ ] Any command that cannot be executed emits a stable `RefuseDiagnostic`; no
      silent blank output.

## Required Evidence

- A `render.png` produced from the bridge/`Surface` path + `nonTransparentPixels`.
- Execution-wiring transcript (recording → offscreen target → readback).
- CPU-reference check for the simple solid scene.

## Fallback / Refusal Behavior

If the recording (or a command) cannot be executed on the GPU backend, emit a
stable `RefuseDiagnostic` and either roll back to the legacy path or refuse — never
return a silently-blank surface while claiming the Kanvas route rendered.

## Dashboard Impact

- Expected row: `gpu-renderer.m31.kanvas-surface-execution`
- Expected classification: `PromotedSupported` (after acceptance)
- Claim promotion allowed: only after real rendered-pixel evidence is attached.

## Validation

```bash
rtk ./gradlew --no-daemon :kanvas-skia-bridge:test
# Once implemented: a render task / main that renders a bridge scene to render.png
rtk git diff --check
```

## Status Notes

- `proposed`: Initial ticket — surfaced by the 2026-06-25 review feasibility check.
  The `:kanvas` `Surface` path is record-only (`flush()` returns a `GPURecording`,
  never executed); the production-default renderer therefore produces no pixels for
  the SkCanvas bridge. This blocks KGPU-M31-005 (pixel parity) and means the
  production activation (M31-001) is not yet a real render path.
- `review`: Implementation complete per PR #1887.
  - `Surface.renderToRgba()` executes the recording on `GPUBackendOffscreenTarget`
    and returns RGBA pixels. Solid-fill rects (Identity transform, Root layer,
    WideOpen/DeviceRect clip) are dispatched via `drawFullscreenPass`.
  - `SkiaKanvasSurface.flush()` now auto-renders to the wrapped `SkSurface`
    when Kanvas renderer is enabled (GPU-backed; emits `kanvas-render-failed`
    diagnostic on failure — never silent no-op, per refusal-first policy).
  - Evidence: `solid-red-rect` (320×240) → `nonTransparentPixels=30800`
    (rect 220×140 = 30800px exact). GPU vs CPU reference: `similarity=100%`,
    `matching=76800/76800`, `maxDiff=0`.
  - Bridge SkSurface verification: rect via bridge → wrapped `SkSurface`
    contains `nonTransparentPixels=22500` (150×150 rect).
  - All 3 test suites green (`:kanvas:test`, `:gpu-renderer:test`,
    `:kanvas-skia-bridge:test`).
  - Scope limit: only solid-fill rects with supported state; everything else
    emits stable `refuse:` diagnostics (no silent blank output).
  - GPU available only via gradle JavaExec tasks (`renderKanvasSurfaceOffscreen`,
    `compareKanvasSurfaceOffscreen`, `verifyBridgeSkSurfaceRender`), not JUnit
    test JVM. This is by design (WebGPU runtime constraint).

## Linear Labels

- `gpu-renderer`
- `milestone:M31`
- `area:execution-backend`
