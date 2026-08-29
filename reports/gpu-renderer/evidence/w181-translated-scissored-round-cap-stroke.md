# W181 — Translated scissored round-cap stroke

W181 promotes the translated counterpart of the integral scissored round-cap
route. The public `KanvasSurface` scene keeps the device scissor
`Rect(8,16,21,21)`, translates the canvas by `(3,2)`, and draws the local
width-four round-cap segment `(6,16) → (26,16)`, which lands at
`(9,18) → (29,18)`.

The independent CPU oracle evaluates the translated device-space union of the
central rectangle and the two radius-two endpoint disks, then intersects it
with the integral scissor. The catalog requires exact transparent RGBA8 output
and records the native `ScissorOnly` path-stroke route.

The native offscreen smoke validates the same translation, scissor bounds,
exact readback, and submit/readback counters.
