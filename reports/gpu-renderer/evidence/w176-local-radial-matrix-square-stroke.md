# W176 — Local-matrix radial square stroke under a Winding clip

W176 promotes the square-cap counterpart of the local-matrix radial stroke
route. The public `KanvasSurface` scene keeps a hard Winding triangle clip,
applies the bounded shader translation `(1.25,-0.75)`, and draws the opaque
width-four radial square-cap miter stroke.

The independent CPU oracle evaluates device-space clip and square-cap
coverage, then samples the translated radial gradient with linear-light
interpolation before sRGB RGBA8 storage. The catalog allows one channel of
numeric tolerance and records the native stencil-cover route.

The native offscreen smoke validates the same local shader matrix, square-cap
stroke, exact readback, and submit/readback counters. Rotated and non-uniform
shader matrices remain explicit refusals.
