# W168 — EvenOdd hole with a linear-gradient butt stroke

W168 promotes the EvenOdd-hole variant of the linear-gradient butt-cap stroke
route. The public `KanvasSurface` scene clips to an outer rectangle with an
inner rectangular hole, then draws the opaque width-four miter stroke with the
two-stop clamp linear gradient.

The independent CPU oracle combines pixel-centre EvenOdd XOR membership, finite
butt-stroke distance coverage, clamp projection on the gradient axis, and
linear-light interpolation before sRGB RGBA8 storage. The catalog requires
exact opaque bytes (zero channel tolerance).

The native offscreen smoke validates the same EvenOdd stencil-cover path-stroke
route and exact readback. Unsupported transform classes and shader variants
remain explicit refusals; no generic shader fallback is introduced.
