# W182 — Scissored diagonal butt stroke

W182 promotes the native diagonal butt/miter path-stroke route under an
integral device scissor. The public `KanvasSurface` scene constrains the
non-AA width-four segment `(5.25,8.25) → (21.25,20.25)` to
`Rect(8,10,20,19)`.

The independent CPU oracle evaluates the diagonal butt-stroke distance at
pixel centres and intersects it with the integral scissor. The catalog requires
exact transparent RGBA8 output and records the native `ScissorOnly`
path-stroke route.

The native offscreen smoke validates the same diagonal geometry, scissor
bounds, exact readback, and submit/readback counters.
