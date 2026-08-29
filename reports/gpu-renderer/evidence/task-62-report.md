# W86 — diagonal butt/miter stroke evidence

## Objective

Prove that Kanvas can lower and render a non-axis-aligned simple path stroke
through the existing native stencil-cover route, without widening the route
contract or changing production rendering code.

## Evidence

The inventory test draws a single diagonal segment from `(5.25,8.25)` to
`(21.25,20.25)`, width `4`, butt cap, miter join, disabled anti-aliasing, and
identity transform. It verifies the `native.path_stroke.stencil_cover` route,
`StrokeStencilEdgeFan` geometry, and cover bounds `(4,6)-(23,22)`.

The native offscreen smoke test renders the same operation on a 32×32 target,
checks a complete RGBA readback against an independent pixel-center distance
oracle, and verifies `Succeeded`, one native submit, and one readback copy.
Fractional endpoints intentionally avoid ambiguous pixel ties at the butt cap.

Validated command:

```text
:kanvas:test --tests '*GPUFramePathApiInventoryTest.single diagonal butt miter stroke lowers to native stencil cover' --tests '*GPUFramePathApiInventoryNativeSmokeTest.single diagonal butt miter stroke renders natively'
```

The command completed successfully. Existing compiler/deprecation warnings are
unchanged and non-fatal.

## Scope

This is evidence for the already-admitted simple-stroke route; no production
implementation, GM baseline, threshold, or retired `gpu-renderer-scenes` asset
was changed. Round caps/joins, anti-aliasing, complex paths, clips, and other
transforms remain explicitly outside this route.
