# W151 — right-angle radial square stroke under a winding triangle clip

W151 promotes the bounded right-angle transform variant of the radial stroke route.
The public `KanvasSurface` scene keeps a hard non-AA Winding triangle clip, rotates
the two-point path by +90° around `(16, 16)`, and renders an opaque two-stop `CLAMP`
radial gradient with width-four square caps and a miter join.

The independent CPU oracle uses the resulting device-space segment and explicitly
extends it by the half-width to model square caps. It then applies winding clip
membership, radial linear-light interpolation, and sRGB RGBA8 storage. The catalog
requires 100% similarity with one-channel LSB tolerance.

The existing native offscreen smoke validates the same right-angle radial square
stroke route, including the native stencil-cover path, rotation classification,
GPU submission, and readback oracle. Arbitrary affine transforms remain outside
this bounded promotion.

