# W163 — Winding butt sweep stroke

W163 adds the Winding butt-cap variant of the bounded sweep route. The public
`KanvasSurface` scene clips to a hard triangle and draws a width-four butt-cap
miter stroke carrying a full-turn two-stop sweep gradient.

The independent CPU oracle evaluates pixel-centre Winding membership and
butt-stroke coverage without endpoint extension, then stores linear-light sweep
interpolation as sRGB RGBA8. The catalog requires 100% similarity with
one-channel LSB tolerance.

The native offscreen smoke validates the Winding stencil-cover route and
endpoint behavior. Square-cap, inverse, and Difference variants remain
separate explicit contracts; no generic fallback is introduced.
