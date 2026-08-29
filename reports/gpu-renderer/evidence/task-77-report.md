# W101 — public canvas snapshot with translated path clip

## Objective

Prove the complete public recording path: a clip captured while the canvas is
translated remains a device-space native path clip after the canvas transform
is reset for the content draw.

## Evidence

The native offscreen smoke test builds a real `Surface(32, 32)` snapshot using
`Surface.canvas`. It translates by `(3,2)`, clips a local triangle
`(4.25,4.25)-(27.25,4.25)-(4.25,27.25)` with non-AA
`ClipOp.INTERSECT`, calls the public `resetMatrix()` API (the available
equivalent of `resetTransform()`), and draws an opaque red 32×32 rectangle.
`GPUFramePathApiInventory.plan` consumes the resulting `snapshotOps()` rather
than a hand-built operation list.

The inventory asserts `transformClass="translate"`, device-space vertices
`(7.25,6.25)-(30.25,6.25)-(7.25,29.25)`, `StencilCoverage`, Winding
`IncrementWrap`/`DecrementWrap` producer operations, and a `NotEqual` consumer
comparison. Native preparation then submits the frame and compares the full
RGBA readback against an independent barycentric pixel-centre CPU oracle. The
test also checks `Succeeded`, exactly one native submit, and one readback copy.

Validated command:

```text
:kanvas:test --tests '*GPUFramePathApiInventoryNativeSmokeTest.public canvas translated path clip snapshot renders natively after transform reset'
```

The command completed successfully. Existing compiler/deprecation warnings are
unchanged and non-fatal.

## Scope and limits

This wave changes tests and evidence only; production code is unchanged. It
covers one public canvas snapshot, one finite translated Winding triangle,
non-AA Intersect, transform reset before drawing, and one opaque consumer.
Other transforms, EvenOdd/inverse fill, AA, nested clips, and other
blend/compositing cases remain outside this proof. No GM baseline, threshold,
PNG, or retired `gpu-renderer-scenes` asset was changed.
