# W84 — square-cap simple path stroke evidence

## Objective

Prove the existing native single-segment square-cap stroke lowering. The
production route already admits this bounded case; this wave supplies the
missing device geometry and native pixel evidence.

## Evidence

The test draws `(8,16) → (24,16)` with a 4 px square/miter stroke, identity
transform, solid red paint, and anti-aliasing disabled. The inventory test
confirms `native.path_stroke.stencil_cover`, `StrokeStencilEdgeFan`, and device
bounds `(6,14)-(26,18)`, including the two-pixel cap extension at each end.
The native offscreen smoke test compares the full RGBA readback with an
independent CPU oracle (`x=6..25`, `y=14..17`) and checks `Succeeded`, one
submit, and one readback copy.

Validated command:

```text
:kanvas:test --tests '*GPUFramePathApiInventoryTest.single horizontal square miter stroke lowers to native stencil cover' --tests '*GPUFramePathApiInventoryNativeSmokeTest.single horizontal square miter stroke renders natively'
```

The command completed successfully. Existing compiler/deprecation warnings are
unchanged and non-fatal.

## Scope

This wave changes no production route. Diagonal square caps, round caps under
scale, joins other than miter, AA, dash/path effects, and multi-segment paths
remain outside the proven contract. No GM score, PNG, threshold, baseline, or
retired `gpu-renderer-scenes` asset was changed.
