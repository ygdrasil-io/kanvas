# W206 — translated dashed stroke

W206 proves the bounded dash route after an integral CTM translation. The
public `Surface` records the same non-AA horizontal width-four butt/miter
stroke and `[8,4]` pattern as W204, then applies `translate(3,2)` before
drawing it.

The device-space CPU oracle checks the translated line `(7,18)–(31,18)` and
expects two opaque 8-pixel runs across four rows (`64` pixels). The native
smoke proof reports zero refused operations plus positive draw, pipeline,
submission, and readback counters.

The claim remains limited to integral translation. Rotation, non-uniform
scaling, non-zero phase, and complex dash arrays are not implied.
