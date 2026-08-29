# W208 — scissored phase-shifted dashed stroke

W208 composes the two already bounded dash facts: the exact horizontal
`[8,4]` pattern at phase `4` and an integral device scissor. It does not widen
the route to arbitrary path effects; it proves that the admitted phase remains
correct when coverage is intersected by the existing scissor consumer.

The public `Surface` scene clips to `[8,14]–[20,19]`, then draws the same
non-AA width-four butt/miter line from `(4,16)` to `(28,16)`. The independent
CPU oracle applies the dash phase before the clip and expects `32` opaque
RGBA8 pixels. The native smoke test checks preserved dash gaps, zero refused
operations, and positive draw/pipeline/submission/readback counters.

The route remains bounded to the shared phase policy (`0` or `4`), integral
device scissor, horizontal two-vertex geometry, and `[8,4]` intervals.
