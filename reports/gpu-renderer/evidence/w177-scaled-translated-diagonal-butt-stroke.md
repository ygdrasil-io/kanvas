# W177 — Scaled translated diagonal butt stroke under a Winding clip

W177 promotes the uniform-scale and translation variant of the opaque diagonal
path-stroke route. The public `KanvasSurface` scene applies a 1.5× scale and
translation `(2,1)` to a hard Winding triangle clip and a width-two local
butt-cap miter stroke, producing a width-three device-space stroke.

The independent CPU oracle evaluates the transformed device-space triangle and
butt stroke coverage at pixel centres. The catalog requires exact opaque RGBA8
output and records the native stencil-cover path-stroke route.

The native offscreen smoke validates the same uniform positive
scale/translation, Winding clip, exact readback, and submit/readback counters.
Perspective and unsupported transform classes remain explicit refusals.
