# W93 — full RGBA oracle for an intersecting path clip

## Objective

Prove the public hard path `INTERSECT` route against a complete CPU-produced
RGBA buffer, rather than relying only on a changed-pixel count.

## Evidence

The existing 64×64 triangle clip test now keeps explicit background, fill, and
clip-path values. It renders a full-surface opaque fill through one non-AA
Winding `ClipOp.INTERSECT` path clip and compares every RGBA byte with the
independent `cubicClipCpuOracle` pixel-centre classification. It also checks
zero fatal diagnostics and zero refused operations.

Validated command:

```text
:kanvas:test --tests '*GPUClipCoverageSurfaceTest.public drawColor hard path clip renders through one stencil scope'
```

The command completed successfully. Existing compiler/deprecation warnings are
unchanged and non-fatal.

## Scope and limits

This wave changes tests and evidence only; production rendering code is
unchanged. It covers one integral, non-AA triangle with Winding fill and
`INTERSECT` over an opaque background. EvenOdd, inverse paths, AA, curves, and
multi-element clip composition remain covered by separate tests or explicit
refusal policies. No GM baseline, threshold, PNG, or retired
`gpu-renderer-scenes` asset was changed.
