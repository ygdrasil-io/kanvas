# W94 — native EvenOdd difference path clip with a hole

## Objective

Prove the bounded native stencil route for a two-contour `EvenOdd` path used
with `ClipOp.DIFFERENCE`.

## Evidence

The native offscreen smoke test builds a 32×32 inventory frame containing a
full opaque red rectangle and an `EvenOdd` path with an outer rectangle
`(3.25,3.25)-(28.75,28.75)` plus an inner rectangle
`(10.25,10.25)-(21.75,21.75)`. The path is applied as a non-AA
`ClipOp.DIFFERENCE` clip.

`GPUFramePathApiInventory.prepareNativeTaskList` records the frame successfully.
Before preparation, the inventory is also asserted to carry a
`GPUClipExecutionPlan.StencilCoverage` with `Invert` front/back operations and
an `Equal` consumer comparison, so the pixel result cannot silently come from
an unrelated clip route.
The test submits it to the native WebGPU backend, checks `Succeeded`, compares
the complete RGBA readback against an independent pixel-centre CPU oracle, and
checks exactly one submit and one readback copy. Pure red is used so the exact
byte oracle is invariant under the sRGB transfer function.

Validated command:

```text
:kanvas:test --tests '*GPUFramePathApiInventoryNativeSmokeTest.even odd difference path clip with a hole matches CPU oracle natively'
```

The command completed successfully. Existing compiler/deprecation warnings are
unchanged and non-fatal.

## Scope and limits

This wave changes tests and evidence only; production code is unchanged. It
covers two rectangular contours, EvenOdd fill, non-AA Difference, and a full
opaque consumer. Curved contours, inverse fill, AA, multiple clip operations,
and destination-read consumers remain outside this proof. No GM baseline,
threshold, PNG, or retired `gpu-renderer-scenes` asset was changed.
