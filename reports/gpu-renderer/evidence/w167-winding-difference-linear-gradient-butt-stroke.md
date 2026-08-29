# W167 — Winding Difference clip with a linear-gradient butt stroke

W167 promotes the `ClipOp.DIFFERENCE` variant of the linear-gradient butt-cap
stroke route. The public `KanvasSurface` scene subtracts the standard hard
Winding triangle from the target before drawing the opaque width-four miter
stroke with the two-stop clamp linear gradient.

The independent CPU oracle evaluates the inverse pixel-centre Winding mask,
finite butt-stroke distance coverage, clamp projection on the gradient axis,
and linear-light interpolation before sRGB RGBA8 storage. The catalog requires
exact opaque bytes (zero channel tolerance).

The native offscreen smoke validates the same Difference stencil-cover
path-stroke route and exact readback. Unsupported transform classes and shader
variants remain explicit refusals; no generic shader fallback is introduced.
