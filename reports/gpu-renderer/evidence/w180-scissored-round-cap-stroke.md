# W180 — Scissored round-cap stroke

W180 promotes the native horizontal round-cap path-stroke route under an
integral device scissor. The public `KanvasSurface` scene draws the
non-AA width-four segment `(6,16) → (26,16)` with round caps and constrains it
to `Rect(5,14,18,19)`.

The independent CPU oracle evaluates the union of the central rectangle and
the two radius-two endpoint disks at pixel centres, then intersects that shape
with the integral scissor. The catalog requires exact transparent RGBA8 output
and records the native `ScissorOnly` path-stroke route.

The native offscreen smoke validates the same round-cap geometry, integral
scissor bounds, exact readback, and submit/readback counters. Path-based Winding
clips remain a separate, explicitly bounded route.
