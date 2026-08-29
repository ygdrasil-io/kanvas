# W178 — Right-angle diagonal butt stroke under a Winding clip

W178 promotes the 90° rotation variant of the opaque diagonal path-stroke
route. The public `KanvasSurface` scene keeps a hard Winding triangle clip and
rotates a width-four local diagonal butt-cap miter stroke around `(16,16)`.
The resulting device-space stroke runs from `(23.75,8.25)` to `(17.75,20.25)`.

The independent CPU oracle evaluates the rotated device-space triangle and
butt-stroke coverage at pixel centres. The catalog requires exact opaque RGBA8
output and records the native stencil-cover path-stroke route.

The native offscreen smoke validates the same right-angle transform, Winding
clip, exact readback, and submit/readback counters. Non-right-angle rotation
remains an explicit refusal.
