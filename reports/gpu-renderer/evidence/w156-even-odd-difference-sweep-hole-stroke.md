# W156 — EvenOdd Difference hole with a sweep square stroke

W156 promotes the `ClipOp.DIFFERENCE` variant of the EvenOdd full-turn sweep
square-stroke route. The public `KanvasSurface` scene subtracts an outer
rectangle plus an inner rectangle hole from the current clip, then draws the
opaque two-stop `0..360°` sweep gradient on a width-four square-cap miter
stroke.

The independent CPU oracle models Difference as the complement of the
EvenOdd rectangle XOR, applies pixel-centre square-cap distance coverage, and
computes the sweep in linear light before sRGB RGBA8 storage. The catalog
requires 100% similarity with one-channel LSB tolerance.

The native offscreen smoke validates the distinct EvenOdd Difference
stencil-cover route (`Invert`) and readback oracle. Intersection and inverse
EvenOdd variants remain separate explicit contracts; no generic clip fallback
is introduced.
