# W87 — diagonal square/miter stroke evidence

## Objective

Prove that the existing native stencil-cover route handles a non-axis-aligned
single-segment square-cap/miter stroke, including the cap extension at both
ends.

## Evidence

The inventory test draws the diagonal segment `(5.25,8.25) → (21.25,20.25)`,
width `4`, square cap, miter join, disabled anti-aliasing, and identity
transform. It verifies the `native.path_stroke.stencil_cover` route,
`StrokeStencilEdgeFan` geometry, and cover bounds `(2,5)-(25,24)`.

The native offscreen smoke test renders the same operation on a 32×32 target
and compares the complete RGBA readback with an independent pixel-center oracle
that extends the segment by half the stroke width along its tangent. It also
checks `Succeeded`, one native submit, and one readback copy. Fractional
endpoints avoid ambiguous rasterizer ties at the cap boundary.

Validated command:

```text
:kanvas:test --tests '*GPUFramePathApiInventoryTest.single diagonal square miter stroke lowers to native stencil cover' --tests '*GPUFramePathApiInventoryNativeSmokeTest.single diagonal square miter stroke renders natively'
```

The command completed successfully. Existing compiler/deprecation warnings are
unchanged and non-fatal.

## Scope

This is evidence for the already-admitted simple-stroke route; no production
implementation, GM baseline, threshold, or retired `gpu-renderer-scenes` asset
was changed. Round caps/joins, anti-aliasing, complex paths, clips, and other
transforms remain explicitly outside this route.
