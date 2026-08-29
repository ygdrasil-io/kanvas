# W154 — EvenOdd hole with a sweep square stroke

W154 promotes the EvenOdd clip variant of the full-turn sweep square-stroke route.
The public `KanvasSurface` scene clips with an outer rectangle and an inner
rectangle hole, then draws the opaque two-stop `0..360°` sweep gradient on a
width-four square-cap miter stroke.

The independent CPU oracle evaluates pixel-centre EvenOdd membership as an XOR of
the two rectangle interiors, applies square-cap distance coverage, and computes
the sweep in linear light before sRGB RGBA8 storage. The catalog requires 100%
similarity with one-channel LSB tolerance.

The native offscreen smoke validates the same EvenOdd stencil-cover route and
readback oracle. Winding and inverse variants remain separate contracts; no
generic clip fallback is introduced.

