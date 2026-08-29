# W175 — Scaled translated radial square stroke under a Winding clip

W175 promotes the transformed radial-gradient stroke route. The public
`KanvasSurface` scene applies a uniform 1.25× scale and translation `(2,-1)`
to a hard Winding triangle clip and a width-four local square-cap miter stroke.
The resulting device-space stroke is compared against a two-stop clamp radial
gradient centred at `(22,19)` with radius 20.

The independent CPU oracle evaluates the transformed triangle, square-cap
coverage, and radial interpolation in device space before sRGB RGBA8 storage.
The catalog allows one channel of numeric tolerance and records the native
path-stroke stencil-cover route.

The native offscreen smoke validates the same uniform transform, Winding clip,
radial sampling, and submit/readback counters. Non-uniform or rotated shader
local matrices remain explicit refusals.
