# W95 — inverse winding intersect path clip

## Objective

Prove the native stencil route for an inverse-winding path clip intersecting an
opaque consumer.

## Evidence

The native offscreen smoke test builds a 32×32 inventory frame with a full
opaque red rectangle and a non-AA triangle whose fill type is
`INVERSE_WINDING`. The clip operation is `ClipOp.INTERSECT`, so the expected
coverage is the area outside the triangle.

The frame is prepared through `GPUFramePathApiInventory.prepareNativeTaskList`
and submitted to the native WebGPU backend. The test checks `Succeeded`, exactly
one native submit and one readback, and compares every RGBA byte with an
independent barycentric pixel-centre oracle for the outside-of-triangle region.
Pure red keeps the byte oracle invariant under the sRGB transfer function.

Validated command:

```text
:kanvas:test --tests '*GPUFramePathApiInventoryNativeSmokeTest.inverse winding intersect path clip fills outside triangle natively'
```

The command completed successfully. Existing compiler/deprecation warnings are
unchanged and non-fatal.

## Scope and limits

This wave changes tests and evidence only; production code is unchanged. It
covers one finite triangle, inverse Winding fill, non-AA Intersect, and one
opaque full-surface consumer. EvenOdd inverse paths, AA, curves, and multiple
clip elements remain outside this proof. No GM baseline, threshold, PNG, or
retired `gpu-renderer-scenes` asset was changed.
