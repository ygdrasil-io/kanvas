# W89 — transformed diagonal square/miter stroke evidence

## Objective

Prove that the existing native simple-stroke route handles a diagonal
square-cap/miter stroke under the supported uniform positive scale plus
translation transform.

## Evidence

The inventory test draws the local segment `(4.125,4.125) → (12.125,8.625)`,
width `2`, square cap, miter join, disabled anti-aliasing, and
`translation(2,3) * scaling(2,2)`. It verifies the
`native.path_stroke.stencil_cover` route, `StrokeStencilEdgeFan` geometry, and
cover bounds `(7,8)-(29,23)`.

The native offscreen smoke test renders the same operation on a 32×32 target
and compares the complete RGBA readback with an independent device-space
pixel-center oracle that extends the segment by half the device stroke width
along its tangent. It also checks `Succeeded`, one native submit, and one
readback copy. Fractional coordinates avoid ambiguous rasterizer ties at the
cap boundary.

Validated command:

```text
:kanvas:test --tests '*GPUFramePathApiInventoryTest.single diagonal square miter stroke with uniform scale and translation lowers to native stencil cover' --tests '*GPUFramePathApiInventoryNativeSmokeTest.single diagonal square miter stroke with uniform scale and translation renders natively'
```

The command completed successfully. Existing compiler/deprecation warnings are
unchanged and non-fatal.

## Scope

This is evidence for the existing simple-stroke transform contract; no
production implementation, GM baseline, threshold, or retired
`gpu-renderer-scenes` asset was changed. Non-uniform/negative scale, skew,
rotation, perspective, anti-aliasing, complex paths, and complex clips remain
outside this route.
