# W96 — translated hard path clip

## Objective

Prove that a hard path clip captured under translation keeps its device-space
geometry and uses the exact winding stencil state in the native route.

## Evidence

The native offscreen smoke test uses a triangle whose local coordinates
`(4.25,4.25)-(27.25,4.25)-(4.25,27.25)` were translated by `(3,2)` to device
coordinates `(7.25,6.25)-(30.25,6.25)-(7.25,29.25)`. The clip is marked with
`transformClass="translate"`, uses non-AA `ClipOp.INTERSECT`, and clips an
opaque red 32×32 rectangle.

The inventory plan is asserted to retain `StencilCoverage`, the translated
path transform class, winding `IncrementWrap`/`DecrementWrap` producer
operations, and a `NotEqual` consumer comparison. Native preparation then
submits the frame and compares the complete RGBA readback against an
independent barycentric pixel-centre oracle. The test also checks `Succeeded`,
exactly one native submit, and one readback copy.

Validated command:

```text
:kanvas:test --tests '*GPUFramePathApiInventoryNativeSmokeTest.translated hard path clip retains device geometry and winding stencil state natively'
```

The command completed successfully. Existing compiler/deprecation warnings are
unchanged and non-fatal.

## Scope and limits

This wave changes tests and evidence only; production code is unchanged. It
covers one finite translated triangle, Winding fill, non-AA Intersect, and one
opaque consumer. Scaled/rotated/perspective clips, EvenOdd or inverse fill,
AA, and multiple clip elements remain outside this proof. No GM baseline,
threshold, PNG, or retired `gpu-renderer-scenes` asset was changed.
