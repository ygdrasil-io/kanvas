# W166 — Scaled translated Winding clip with a linear-gradient butt stroke

W166 promotes the bounded uniform-scale and translation variant of the
linear-gradient butt-cap stroke route under a hard Winding path clip. The
public `KanvasSurface` scene records a 1.5× scale followed by translation
(2,1), a width-two local stroke, and the same two-stop clamp linear gradient.

The independent CPU oracle evaluates the resulting device-space triangle and
stroke geometry, then samples the gradient on the inverse-transformed device
axis before linear-light interpolation and sRGB RGBA8 storage. The catalog
requires exact opaque bytes (zero channel tolerance).

The native offscreen smoke validates the same transformed stencil-cover
path-stroke route and exact readback. Perspective and unsupported transform
classes remain explicit refusals; no generic shader fallback is introduced.
