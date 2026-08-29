# W165 — Winding clip with a linear-gradient butt stroke

W165 promotes the butt-cap variant of the linear-gradient stroke route under a
hard Winding path clip. The public `KanvasSurface` scene uses the same device
space triangle, opaque width-four miter stroke, and two-stop clamp linear
gradient as W164, with the stroke ends left unextended (`BUTT`).

The independent CPU oracle combines pixel-centre triangle membership, finite
butt-stroke distance coverage, clamp projection on the gradient axis, and
linear-light interpolation before sRGB RGBA8 storage. The catalog requires
exact opaque bytes (zero channel tolerance).

The native offscreen smoke validates the same stencil-cover path-stroke route
and exact readback. Unsupported gradient stop counts and transform classes
remain explicit refusals; no generic shader fallback is introduced.
