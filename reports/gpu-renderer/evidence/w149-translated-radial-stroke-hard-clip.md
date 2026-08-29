# W149 — translated radial stroke under a winding triangle clip

W149 adds the translated sibling of W148 to the GPU evidence catalog. The public
`KanvasSurface` scene applies a device translation of `(2, 0)` to the same opaque
two-stop `CLAMP` radial butt/miter stroke and winding triangle clip.

Its CPU oracle is independent and evaluates the translated device-space geometry at
pixel centres: winding membership, butt-segment distance, linear-light radial
interpolation, and sRGB RGBA8 storage. The case requires 100% similarity with a
one-channel LSB tolerance.

The existing native smoke proof for the translated radial stroke reaches
`native.path_stroke.stencil_cover`, validates winding stencil coverage, submits an
offscreen frame, and checks the readback against an independent oracle. W149 makes
that translated contract a first-class catalog case without widening support to
arbitrary transforms.

