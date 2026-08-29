# W99 — inverse EvenOdd intersect path clip with a hole

## Objective

Prove that the native hard path-clip route supports a bounded inverse EvenOdd
`ClipOp.INTERSECT` with two contours: an outer rectangle and an inner hole.

## Evidence

The native offscreen smoke test clips an opaque red 32×32 rectangle with a
non-AA inverse EvenOdd path containing the outer rectangle
`(3.25,3.25)-(28.75,28.75)` and the inner rectangle
`(10.25,10.25)-(21.75,21.75)`. The expected result is red outside the outer
contour and inside the inner contour, with transparent pixels in the outer
shell.

The inventory asserts `StencilCoverage`, `Invert` front/back stencil
operations, `Equal` consumer comparison, and `geometry.inverseFill=true`.
Native preparation then submits the frame and compares the complete RGBA
readback against an independent pixel-centre CPU oracle. The test also checks
`Succeeded`, exactly one native submit, and one readback copy.

Validated command:

```text
:kanvas:test --tests '*GPUFramePathApiInventoryNativeSmokeTest.inverse even odd intersect path clip with a hole matches CPU oracle natively'
```

The command completed successfully. Existing compiler/deprecation warnings are
unchanged and non-fatal.

## Scope and limits

This wave changes tests and evidence only; production code is unchanged. It
covers one finite two-rectangle inverse EvenOdd path, non-AA Intersect, and one
opaque consumer. Curves, transformed clips, AA, nested clips, and other
blend/compositing cases remain outside this proof. No GM baseline, threshold,
PNG, or retired `gpu-renderer-scenes` asset was changed.
