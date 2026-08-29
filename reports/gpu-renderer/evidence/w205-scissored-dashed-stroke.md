# W205 — dashed stroke under an integral scissor

W205 extends the W204 bounded dash proof to the public `Surface` clip path.
The same non-AA horizontal width-four butt/miter stroke and `[8,4]` pattern is
recorded from `(4,16)` to `(28,16)`, then intersected with the device scissor
`[8,14]–[20,19]`.

The independent oracle expects only the two on-runs inside the scissor:
`x=8..11` and `x=16..19`, across four rows (`32` opaque pixels). The native
smoke proof reports zero refused operations and positive draw, pipeline,
submission, and readback counters.

This confirms that the existing bounded dash expansion composes with a simple
scissor. Empty patterns, non-integral/rotated paths, complex patterns, and
round-cap dash variants remain explicit refusals or outside the claim.
