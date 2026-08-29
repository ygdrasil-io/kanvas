# W164 — Winding clip with a linear-gradient square stroke

W164 promotes the linear-gradient stroke route under a hard Winding path clip.
The public `KanvasSurface` scene clips to the standard triangle and draws an
opaque width-four square-cap miter stroke using a two-stop clamp linear
gradient along the device X axis.

The independent CPU oracle combines pixel-centre triangle membership,
square-stroke distance coverage, clamp projection on the gradient axis, and
linear-light interpolation before sRGB RGBA8 storage. The catalog requires
exact opaque bytes (zero channel tolerance).

The native offscreen smoke validates the same stencil-cover path route and
exact readback. Unsupported gradient stop counts and transform classes remain
explicit refusals; no generic shader fallback is introduced.
