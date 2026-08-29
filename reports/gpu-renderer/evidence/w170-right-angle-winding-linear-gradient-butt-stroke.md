# W170 — Right-angle Winding clip with a linear-gradient butt stroke

W170 promotes the right-angle rotation variant of the linear-gradient butt-cap
stroke route. The public `KanvasSurface` scene clips to a hard Winding triangle,
rotates the local path by 90 degrees around the 16×16 pivot, and draws the
opaque width-four miter stroke with the two-stop clamp linear gradient.

The independent CPU oracle evaluates the resulting device-space triangle and
stroke, samples the vertical gradient axis, and performs linear-light
interpolation before sRGB RGBA8 storage. The catalog requires exact opaque
bytes (zero channel tolerance).

The native offscreen smoke validates the same right-angle stencil-cover
path-stroke route and exact readback. Perspective and unsupported transform
classes remain explicit refusals; no generic shader fallback is introduced.
