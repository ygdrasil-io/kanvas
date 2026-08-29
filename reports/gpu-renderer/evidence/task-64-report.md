# W88 — transformed diagonal butt/miter stroke evidence

## Objective

Prove that the existing native simple-stroke route remains valid when a
diagonal segment is drawn through a uniform positive scale combined with a
translation.

## Evidence

The inventory test draws the local segment `(4.125,4.125) → (12.125,8.625)`,
width `2`, butt cap, miter join, disabled anti-aliasing, and the affine
transform `translation(2,3) * scaling(2,2)`. The device segment is
`(10.25,11.25) → (26.25,20.25)`. It verifies the
`native.path_stroke.stencil_cover` route, `StrokeStencilEdgeFan` geometry, and
cover bounds `(9,9)-(28,22)`.

The native offscreen smoke test compares the complete RGBA readback with an
independent device-space pixel-center distance oracle and checks `Succeeded`,
one native submit, and one readback copy. Fractional coordinates avoid
ambiguous rasterizer ties at the butt cap.

Validated command:

```text
:kanvas:test --tests '*GPUFramePathApiInventoryTest.single diagonal butt miter stroke with uniform scale and translation lowers to native stencil cover' --tests '*GPUFramePathApiInventoryNativeSmokeTest.single diagonal butt miter stroke with uniform scale and translation renders natively'
```

The command completed successfully. Existing compiler/deprecation warnings are
unchanged and non-fatal.

## Scope

This is evidence for the already-admitted simple-stroke transform contract; no
production implementation, GM baseline, threshold, or retired
`gpu-renderer-scenes` asset was changed. Non-uniform/negative scale, skew,
rotation, perspective, anti-aliasing, complex paths, and complex clips remain
outside this route.
