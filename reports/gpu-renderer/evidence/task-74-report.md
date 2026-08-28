# W98 — native EvenOdd intersect path clip with a hole

## Objective

Prove the native stencil behavior for a two-contour `EvenOdd` path used with
`ClipOp.INTERSECT`, complementing the `Difference` proof from W94.

## Evidence

The native offscreen smoke test clips an opaque red 32×32 rectangle with an
outer rectangle `(3.25,3.25)-(28.75,28.75)` and an inner rectangle
`(10.25,10.25)-(21.75,21.75)`, both in one non-AA `EvenOdd` path. The complete
RGBA readback is compared with an independent CPU oracle: red appears in the
outer shell and transparent pixels remain outside the contour and in the hole.

The inventory is asserted to use `GPUClipExecutionPlan.StencilCoverage`, with
`Invert` producer operations and a `NotEqual` consumer comparison. The native
frame must complete successfully with exactly one submit and one readback copy.

Validated command:

```text
:kanvas:test --tests '*GPUFramePathApiInventoryNativeSmokeTest.even odd intersect path clip with a hole matches CPU oracle natively'
```

The command completed successfully. Existing compiler/deprecation warnings are
unchanged and non-fatal.

## Scope and limits

This wave changes tests and evidence only; production code is unchanged. It
covers two rectangular contours, EvenOdd fill, non-AA Intersect, and one
opaque consumer. Curves, inverse fill, AA, multiple clip operations, and
destination-read consumers remain outside this proof. No GM baseline,
threshold, PNG, or retired `gpu-renderer-scenes` asset was changed.
