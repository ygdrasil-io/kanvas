# Task 5 report — reusable GPU clip coverage masks

## Outcome

Implemented the reusable GPU `AlphaMask` clip route.  A strict per-frame
prepass counts logical clip uses before rendering; cache entries are acquired
and released through idempotent leases, and are evicted after their final
planned use.  The cache enforces the configured intermediate-byte budget
before allocation and provides a typed refusal for that limit.

`GPURenderer` now renders a mask-bearing logical draw into the existing source
target, then composites `source × clipMask` fully on the GPU.  There is no CPU
readback or CPU raster fallback in this route.  Rect, RRect and Path elements
are materialized in clip order using GPU coverage/stencil passes:

- `Intersect` uses `DST_IN` (destination alpha multiplied by coverage).
- `Difference` uses `DST_OUT` (destination alpha reduced by coverage).
- Path inverse/empty cases use constant GPU passes; path differences clear the
  complement when required, so an intersection cannot leave stale mask alpha.

The mask route currently supports `SRC_OVER`.  Other blend modes are
explicitly refused as `unsupported.clip.mask.blend_mode:<label>` after
consuming the planned lease, rather than being rendered with an incorrect
source-composite semantic.

## Files changed

- `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUClipCoverage.kt`
  — frame cache, exact-use prepass, mask allocation/materialization and GPU
  composite helpers.
- `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUClipCoveragePlanner.kt`
  — documents the transition from immutable plan cache to frame materialization.
- `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt`
  — integrates prepass, leases, source rendering and two-texture composition.
- `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUWgsl.kt`
  — non-AA analytic coverage corrections, stencil cover WGSL and
  source-times-mask compositor WGSL.
- `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUClipCoverageDispatchTest.kt`
  — ordering, reuse, budget/refusal, layout, prepass and real-GPU tests.
- `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUClipCoverageWgslTest.kt`
  — parser/lowerer coverage for the added shaders and non-AA behavior.

## TDD evidence

The initial RED tests did not compile because the requested frame-cache and
mask APIs (`GPUClipCoverageFrameCache`, `acquireClipMask`,
`CLIP_MASK_COMPOSITE_WGSL`) did not yet exist.  After the initial GREEN pass,
the real GPU test exposed a semantic failure: analytic rect/RRect mask passes
were scissored to their bounds, so an `Intersect` left alpha outside the shape.
The final implementation renders those analytic masks across the full target
and uses a transparent complement stencil pass for path intersections.  The
real GPU test then passed for the ordered route `Rect ∩ / RRect − / Path −`.

## Final verification

Fresh focused verification:

```bash
rtk ./gradlew :kanvas:test --tests org.graphiks.kanvas.surface.gpu.GPUClipCoverageDispatchTest --tests org.graphiks.kanvas.surface.gpu.GPUClipCoverageWgslTest --tests org.graphiks.kanvas.surface.gpu.GPUClipCoverageContractsTest
rtk git diff --check
```

Result: `BUILD SUCCESSFUL`; 27 tests passed with zero failures:

- `GPUClipCoverageContractsTest`: 16
- `GPUClipCoverageDispatchTest`: 6, including the real WebGPU surface route
- `GPUClipCoverageWgslTest`: 5

`git diff --check` was clean.  Gradle emitted only pre-existing environment
warnings about restricted `System::load`, LWJGL's `sun.misc.Unsafe`, and
deprecated Gradle features.

## Review

An independent review initially identified three issues: the new cache was
not integrated into `GPURenderer`, non-AA rect/RRect masks could overfill, and
prepass accounting could leak.  The implementation now integrates the actual
GPU route, adds shader hard-coverage behavior for non-AA geometry, and wraps
the full compositor path in `try/finally` so every acquired lease is released.
The follow-up review approved the result.

## Remaining concern

Clip-mask materialization is intentionally restricted to `SRC_OVER` until the
renderer has a correct general blend-mode composition strategy.  Unsupported
mask-bearing operations are refused with a stable diagnostic instead of a
silent semantic downgrade.  The route otherwise preserves the configured
frame memory bound and does not introduce CPU fallback or readback.
