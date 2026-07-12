# Task 8 — Destination-read blends GPU report

## Delivered

- Added a shared GPU-only destination-read composer: source render → GPU texture copy (or transparent snapshot) → replacement formula pass.
- Added `BLEND_FORMULA_WGSL` (two textures) and `CLIP_BLEND_FORMULA_WGSL` (three textures). Both interpolate the same premultiplied-alpha standard blend implementation for Multiply, Screen, Overlay, Darken, Lighten, Difference, and Exclusion.
- The formula pass clears the scene then uses fixed-function `SRC` only to replace the target; it never blends the formula result a second time.
- Complex clips use the three-texture formula and multiply premultiplied source color and alpha by the clip-mask alpha before blending.
- Scissor clips retain the original source clip; alpha-mask sources remain full-target because the final formula consumes the mask.
- Empty scenes initialize the snapshot to transparent. Existing scenes use `copyOffscreenTexture`; this keeps destination snapshots GPU-to-GPU with no CPU readback/upload.
- Added route diagnostics with source, snapshot, mode, clip strategy, and `copy-then-formula` action facts. An absent formula is refused before source dispatch.
- Replaced Task 7 destination-read refusal expectations and exercised `SRC` replacement topology in the native runtime smoke test.

## TDD evidence

- RED: the new alpha-mask test produced seven `unsupported.clip.mask.blend_mode:*` refusals; the three-texture shader test did not compile because `CLIP_BLEND_FORMULA_WGSL` was absent.
- GREEN: all seven modes are checked against a CPU calculation of the standard formula in linear color space, then compared after sRGB conversion. The test also asserts clipped exterior preservation, partial-alpha visibility, at least seven GPU destination copies, and no destination readbacks.
- A follow-up RED exposed non-AA scissor sources escaping their clip. The source route now retains that scissor and its regression test is green.

### P1 review follow-up — text and textured geometry

- RED: `DrawText`, `DrawVertices`, and `DrawMesh` under a non-AA scissor with `DARKEN` each changed white pixels outside the clip. Their source passes emitted a full-target scissor before the destination-read formula.
- GREEN: the preserved scissor now reaches the text-atlas and textured-vertex dispatchers. `DrawMesh` shares the latter route. Each regression asserts the exterior remains opaque white after the GPU snapshot/formula pass.

### P1 re-review follow-up — empty scissor

- RED: an integer `DeviceRect` wholly outside the target becomes `null` when truncated to target bounds. Text, textured vertices, and meshes then interpreted that `null` as no scissor and emitted a full-target source before `DARKEN`.
- GREEN: an empty preserved `DeviceRect` now short-circuits source encoding before any text-atlas or textured-vertex dispatch. The destination-read composer therefore leaves the existing destination untouched. Regressions require a white 16×16 destination, zero fatals, and only the background operation dispatched for each of `DrawText`, `DrawVertices`, and `DrawMesh`.

## Verification

Passed:

- `rtk ./gradlew :kanvas:test --tests org.graphiks.kanvas.surface.gpu.GPUClipAdvancedBlendSurfaceTest --tests org.graphiks.kanvas.surface.gpu.GPUPathClipRegressionTest --tests org.graphiks.kanvas.surface.gpu.GPUClipCoverageWgslTest --tests org.graphiks.kanvas.surface.gpu.GPUClipCoverageSurfaceTest`
- `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.gpu.GPUClipCoverageSurfaceTest.empty scissor destination read DrawText keeps destination intact' --tests 'org.graphiks.kanvas.surface.gpu.GPUClipCoverageSurfaceTest.empty scissor destination read DrawVertices keeps destination intact' --tests 'org.graphiks.kanvas.surface.gpu.GPUClipCoverageSurfaceTest.empty scissor destination read DrawMesh keeps destination intact'`
- `rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeSmokeTest --rerun-tasks`
- `rtk git diff --check`

For the empty-scissor re-review, the targeted XML completed with three tests and zero failures. A concurrent
`GPUClipCoverageSurfaceTest --rerun-tasks` Gradle client remained blocked after its worker finished
(PID 52155); it was left untouched, so no broader rerun was started from this follow-up.

Full `rtk ./gradlew :kanvas:test` ran 495 tests and has one residual failure outside Task 8's destination-read route:

- `GPUSaveLayerCompositeRegressionTest > DrawPicture containing saveLayer is refused before any child reaches its parent()` expects `unsupported.picture.save_layer` but receives `unsupported.picture.nested_clip` at `GPUSaveLayerCompositeRegressionTest.kt:217`.

The Task 8-focused suites pass; no Task 9+ implementation was added.
