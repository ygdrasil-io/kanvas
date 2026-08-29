# W169 — Inverse Winding clip with a linear-gradient butt stroke

W169 promotes the explicit inverse-Winding variant of the linear-gradient
butt-cap stroke route. The public `KanvasSurface` scene uses an inverse-Winding
triangle clip and draws the opaque width-four miter stroke with the two-stop
clamp linear gradient in the resulting exterior region.

The independent CPU oracle evaluates inverse pixel-centre Winding membership,
finite butt-stroke distance coverage, clamp projection on the gradient axis,
and linear-light interpolation before sRGB RGBA8 storage. The catalog requires
exact opaque bytes (zero channel tolerance).

The native offscreen smoke validates the same inverse-fill stencil-cover
path-stroke route and exact readback. Unsupported transform classes and shader
variants remain explicit refusals; no generic shader fallback is introduced.
