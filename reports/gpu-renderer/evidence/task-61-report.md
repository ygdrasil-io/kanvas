# W85 — translated uniformly scaled hairline evidence

## Objective

Prove the direct native hairline route for the affine transform form produced by
combining a uniform scale and a translation.

## Evidence

The test draws the local segment `(4,8) → (14,8)` with zero-width stroke and
anti-aliasing disabled, under `translation(2,3) * scaling(2,2)`. The device
segment is `(10,19) → (30,19)`, and the direct quad covers `(10,18)-(30,20)`.
The inventory test verifies `DirectTriangles`, the exact indices, and those
device bounds. The native offscreen smoke test compares the full RGBA readback
with an independent CPU oracle (`x=10..29`, row `y=18`) and checks `Succeeded`,
one submit, and one readback copy.

Validated command:

```text
:kanvas:test --tests '*GPUFramePathApiInventoryTest.single segment hairline with uniform scale and translation lowers to direct device quad' --tests '*GPUFramePathApiInventoryNativeSmokeTest.single horizontal hairline with uniform scale and translation renders one pixel row natively'
```

The command completed successfully. Existing compiler/deprecation warnings are
unchanged and non-fatal.

## Scope

Only the already-admitted axis-aligned hairline contract is proven here. The
route still refuses non-uniform/negative scale, skew, rotation, perspective,
AA, dash/path effects, non-root layers, and complex clips. No GM score, PNG,
threshold, baseline, or retired `gpu-renderer-scenes` asset was changed.
