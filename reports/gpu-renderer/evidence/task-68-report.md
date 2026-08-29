# W92 — native stroke scissor and path-difference pixel evidence

## Objective

Extend the evidence for two already-supported routes without changing
production code: the native simple-stroke route must preserve an integral
device rectangle clip, and a path `Difference` clip must match an independent
CPU pixel oracle.

## Evidence

The inventory test draws the fractional segment `(5.25,8.25) → (21.25,20.25)`
with width `4`, butt cap, miter join, disabled anti-aliasing, and the identity
transform. An integral `DeviceRect` clip `(8,10)-(20,19)` is retained as the
exact `ScissorOnly(8,10,20,19)` execution plan while the draw remains on
`native.path_stroke.stencil_cover`.

The native offscreen smoke test renders the same command on a 32×32 target and
compares the complete RGBA readback with an independent pixel-center CPU oracle:
the finite diagonal segment is classified by distance to its centerline, then
restricted to the integer scissor rectangle. It also checks a successful native
frame, one queue submit, and one readback copy.

The clip-coverage test now applies the same full-buffer check to a 64×64
triangle path with `ClipOp.DIFFERENCE`. It verifies zero refused operations and
compares every RGBA byte against the independent path-containment oracle, in
addition to the existing fill count and representative-pixel assertions.

Validated command:

```text
:kanvas:test --tests '*GPUClipCoverageSurfaceTest.public hard difference path clip renders the complement through one stencil scope' --tests '*GPUFramePathApiInventoryTest.single diagonal butt miter stroke retains an integral device scissor' --tests '*GPUFramePathApiInventoryNativeSmokeTest.single diagonal butt miter stroke under integral device scissor matches CPU oracle natively'
```

The command completed successfully. Existing compiler/deprecation warnings are
unchanged and non-fatal.

## Scope and limits

This wave changes no production behavior and adds no fallback approximation.
The stroke proof covers only an integral, non-AA `DeviceRect` clip around one
diagonal butt/miter segment. The difference proof covers one non-AA winding
triangle on a 64×64 target. Fractional/AA clips, complex path clips, and
unsupported stroke transforms remain outside this proof. No GM baseline,
threshold, PNG, or retired `gpu-renderer-scenes` asset was changed.
