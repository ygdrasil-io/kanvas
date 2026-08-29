# W155 — inverse EvenOdd hole with a sweep square stroke

W155 promotes the inverse EvenOdd clip variant of the full-turn sweep square
stroke. The public `KanvasSurface` scene uses an outer rectangle plus an inner
rectangle with `INVERSE_EVEN_ODD`, then draws the opaque two-stop `0..360°` sweep
gradient on a width-four square-cap miter stroke.

The independent CPU oracle paints only the complement of the EvenOdd XOR region,
while retaining pixel-centre square-cap coverage and linear-light sweep interpolation
before sRGB RGBA8 storage. The catalog requires 100% similarity with one-channel
LSB tolerance.

The native offscreen smoke validates the same inverse EvenOdd stencil-cover route
and readback oracle. Ordinary EvenOdd, Winding, and Difference variants remain
separate explicit contracts.

