# W83 — vertical simple path stroke evidence

## Objective

Prove the existing native single-segment butt/miter stroke route for a vertical
segment. This closes an orientation gap without changing the route admission
policy or adding a special-case implementation.

## Evidence

The test draws `(16,4) → (16,28)` with a 4 px butt/miter stroke, identity
transform, solid red paint, and anti-aliasing disabled. The inventory test
confirms `native.path_stroke.stencil_cover`, `StrokeStencilEdgeFan`, and device
bounds `(14,4)-(18,28)`. The native offscreen smoke test compares the complete
RGBA readback with an independent CPU oracle (`x=14..17`, `y=4..27`) and checks
`Succeeded`, one submit, and one readback copy.

Validated command:

```text
:kanvas:test --tests '*GPUFramePathApiInventoryTest.single vertical butt miter stroke lowers to native stencil cover' --tests '*GPUFramePathApiInventoryNativeSmokeTest.single vertical butt miter stroke renders natively'
```

The command completed successfully. Existing compiler/deprecation warnings are
unchanged and non-fatal.

## Scope

This wave changes no production route. It supplies native proof that the
existing geometry/lowering path is orientation-independent for this bounded
case. Diagonal segments, round caps, joins other than miter, AA, dash/path
effects, and multi-segment paths remain outside the proven contract. No GM
score, PNG, threshold, baseline, or retired `gpu-renderer-scenes` asset was
changed.
