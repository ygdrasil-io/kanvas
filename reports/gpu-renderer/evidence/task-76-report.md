# W100 — translated winding difference path clip

## Objective

Prove that a translated hard Winding path clip with `ClipOp.DIFFERENCE`
renders the complement of the path through the native stencil route.

## Evidence

The native offscreen smoke test clips an opaque red 32×32 rectangle with a
non-AA triangle whose local coordinates
`(4.25,4.25)-(27.25,4.25)-(4.25,27.25)` are translated by `(3,2)` and retained
as device-space vertices
`(7.25,6.25)-(30.25,6.25)-(7.25,29.25)`. The clip carries
`transformClass="translate"`.

The inventory asserts `StencilCoverage`, the translated path transform class,
Winding `IncrementWrap`/`DecrementWrap` producer operations, and an `Equal`
consumer comparison for the difference operation. Native preparation then
submits the frame and compares the complete RGBA readback against an
independent barycentric pixel-centre CPU oracle: red outside the triangle and
transparent inside it. The test also checks `Succeeded`, exactly one native
submit, and one readback copy.

Validated command:

```text
:kanvas:test --tests '*GPUFramePathApiInventoryNativeSmokeTest.translated winding difference path clip fills outside triangle natively'
```

The command completed successfully. Existing compiler/deprecation warnings are
unchanged and non-fatal.

## Scope and limits

This wave changes tests and evidence only; production code is unchanged. It
covers one finite translated Winding triangle, non-AA Difference, and one
opaque consumer. Intersect, EvenOdd/inverse fill, other transforms, AA, nested
clips, and other blend/compositing cases remain outside this proof. No GM
baseline, threshold, PNG, or retired `gpu-renderer-scenes` asset was changed.
