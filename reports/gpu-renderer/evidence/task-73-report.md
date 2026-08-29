# W97 — uniform scaled and translated hard path clip

## Objective

Prove that a hard Winding path clip captured with a uniform scale and
translation keeps its device-space geometry and the native stencil route.

## Evidence

The native offscreen smoke test uses the local triangle
`(3.25,3.25)-(15.25,3.25)-(3.25,15.25)`, transformed by scale `1.5` and
translation `(2,1)` to device coordinates
`(6.875,5.875)-(24.875,5.875)-(6.875,23.875)`. The clip is marked
`transformClass="uniform-positive-scale-translate"`, uses non-AA
`ClipOp.INTERSECT`, and clips an opaque red 32×32 rectangle.

The inventory asserts `StencilCoverage`, the transform class, the transformed
vertices, and winding `IncrementWrap`/`DecrementWrap` producer operations with
a `NotEqual` consumer comparison. Native preparation then renders one frame;
the complete RGBA readback is compared with an independent barycentric
pixel-centre oracle, and exactly one submit plus one readback copy are required.

Validated command:

```text
:kanvas:test --tests '*GPUFramePathApiInventoryNativeSmokeTest.uniform scaled translated hard path clip retains device geometry and winding stencil state natively'
```

The command completed successfully. Existing compiler/deprecation warnings are
unchanged and non-fatal.

## Scope and limits

This wave changes tests and evidence only; production code is unchanged. It
covers one finite triangle, Winding fill, non-AA Intersect, uniform
scale-plus-translation, and one opaque consumer. Rotation, non-uniform scale,
perspective, inverse/EvenOdd fill, AA, and multiple clip elements remain
outside this proof. No GM baseline, threshold, PNG, or retired
`gpu-renderer-scenes` asset was changed.
