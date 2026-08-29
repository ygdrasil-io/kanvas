# W179 — Right-angle diagonal square stroke under a Winding clip

W179 promotes the square-cap counterpart of the 90° rotated diagonal
path-stroke route. The public `KanvasSurface` scene keeps a hard Winding
triangle clip and rotates a width-four local diagonal square-cap miter stroke
around `(16,16)`. The resulting device-space stroke runs from `(23.75,8.25)`
to `(17.75,20.25)` and includes the two-pixel cap extensions.

The independent CPU oracle evaluates the rotated device-space triangle and
square-cap stroke coverage at pixel centres. The catalog requires exact opaque
RGBA8 output and records the native stencil-cover path-stroke route.

The native offscreen smoke validates the same right-angle transform, cap
extension, Winding clip, exact readback, and submit/readback counters.
