# W161 — inverse Winding butt sweep stroke

W161 adds the inverse-Winding butt-cap variant of the bounded sweep stroke
route. The public `KanvasSurface` scene clips to the exterior of a triangle and
draws a width-four butt-cap miter stroke carrying a full-turn two-stop sweep
gradient.

The independent CPU oracle evaluates inverse pixel-centre triangle membership,
finite butt-stroke coverage without endpoint extension, and linear-light sweep
interpolation before sRGB RGBA8 storage. The catalog requires 100% similarity
with one-channel LSB tolerance.

The native offscreen smoke validates the inverse-Winding stencil-cover route
and butt endpoint behavior. Square-cap and Difference variants remain separate
explicit contracts; no generic fallback is introduced.
