# W82 — uniform scale plus translation for simple path strokes

## Objective

Admit the transform form that Kanvas actually produces when a uniform scale
is combined with a translation: an affine matrix with equal positive diagonal
coefficients, zero skew, and finite translation.

## Implementation

- Added a shared `isUniformPositiveScaleTranslate()` admission predicate.
- Reused it for the bounded hairline and simple-stroke contracts.
- Kept the generic stroke refusal bypass conditional on the complete simple
  stroke contract, so non-uniform scale, skew, rotation, perspective, and
  unsupported stroke styles remain refused.
- Added planner coverage for the native stencil-cover route.
- Added a native offscreen smoke test for a 2 px local butt/miter segment under
  `translation(2,3) * scaling(2,2)`.

## Evidence

The local segment `(4,8) → (14,8)` becomes `(10,19) → (30,19)` in device
space, with a four-pixel stroke row over `x=10..29`, `y=17..20`. The native
test compares the full RGBA readback with an independent CPU oracle and checks
`Succeeded`, one submit, and one readback copy.

Validated commands:

```text
:gpu-renderer:test --tests '*FirstRoutePlannerTest.fill path simple stroke with uniform scale and translation builds native stencil cover route'
:kanvas:test --tests '*GPUFramePathApiInventoryNativeSmokeTest.single butt miter stroke with uniform scale and translation renders natively'
```

Both completed successfully. Existing compiler/deprecation warnings are
unchanged and non-fatal.

## Limits

This does not admit skew, rotation, non-uniform or negative scale, perspective,
AA, dash/path effects, multi-segment paths, round caps under scale, or broader
joins/caps. No GM score, PNG, threshold, baseline, or retired
`gpu-renderer-scenes` asset was changed.
