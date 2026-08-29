# W91 — zero-length simple-stroke refusal evidence

## Objective

Verify that a path containing two coincident points does not reach the native
stroke stencil-cover route as an empty geometry artifact.

## Evidence

The inventory test covers both `butt/miter` and `square/miter` with a path
`moveTo(10,10); lineTo(10,10)`, width `4`, anti-aliasing disabled, and the
identity transform. Path normalization removes the duplicate point, leaving a
one-point path rather than a valid two-point simple segment. The semantic
refusal is stable:

```text
unsupported.core_primitive.stroke.complex_exact_lowering
```

The test also checks the refusal facts (`pointCount=1` and the requested cap)
and asserts that no native stencil-cover route is selected. The stable
semantic refusal prevents the empty `GPUStroke` result from being treated as a
valid two-point native stroke.

Validated command:

```text
:kanvas:test --tests '*GPUFramePathApiInventoryTest.zero length butt and square strokes refuse before native preparation'
```

The command completed successfully. Existing compiler/deprecation warnings are
unchanged and non-fatal.

## Scope and limits

This wave changes no production behavior and adds no fallback approximation.
Zero-length round strokes and dashed zero-length paths remain governed by
their existing separate `GPUStroke` unit tests and refusal policies. No GM
baseline, threshold, or retired `gpu-renderer-scenes` asset was changed.
