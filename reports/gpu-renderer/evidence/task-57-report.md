# W81 — uniform positive scale for simple path strokes

## Objective

Extend the existing native single-segment path-stroke route to admit only a
strictly positive uniform scale. The route remains intentionally narrow: one
immutable two-point contour, finite width, non-AA, butt/square cap (or an
already-proven round-cap case), miter join, no dash/path effect, root layer,
and the existing native stencil-cover capability.

## Implementation

- Centralized the `GPUTransformFacts.isUniformPositiveScale()` predicate and
  reused it by the bounded hairline and simple-stroke contracts.
- Allowed the simple-stroke refusal path to reach the native planner when that
  exact contract is satisfied; unrelated stroke transforms still use the
  existing refusal policy.
- Added planner coverage for a 2 px local horizontal stroke under a 2× scale.
- Added a native offscreen smoke test with an independent CPU pixel oracle.
- Kept the pixel-exact round-cap exception restricted to identity/translation;
  uniformly scaled round caps remain explicitly refused until their device-space
  coverage is independently proven.

## Evidence

The native test draws `(4,8) → (14,8)` with a 2 px stroke and a 2× uniform
scale. The expected device result is a solid 4 px row over `x=8..27`,
`y=14..17`. The test verifies `Succeeded`, exact RGBA readback, one submit,
and one readback copy.

Validated commands:

```text
:gpu-renderer:test --tests '*FirstRoutePlannerTest.fill path simple stroke with uniform scale builds native stencil cover route' --tests '*NativePathHairlineContractTest*'
:kanvas:test --tests '*GPUFramePathApiInventoryTest.single segment hairline with uniform scale lowers to direct device quad' --tests '*GPUFramePathApiInventoryNativeSmokeTest.single butt miter stroke with uniform scale renders natively'
```

Both commands completed successfully. Existing compiler/deprecation warnings
are unchanged and non-fatal.

## Scope and limits

This does not enable non-uniform, negative, affine, perspective, AA, dash,
path-effect, multi-segment, uniformly scaled round-cap, or broader cap/join combinations. No GM score,
PNG, threshold, baseline, or retired `gpu-renderer-scenes` asset was changed.
