# W153 — local-matrix sweep square stroke under a winding triangle clip

W153 promotes the bounded local-shader-matrix variant of the full-turn sweep
stroke route. The public `KanvasSurface` scene keeps the geometry and hard Winding
clip fixed and applies only the finite translation `(1.25, -0.75)` through
`Shader.WithLocalMatrix`.

The independent CPU oracle keeps device-space square-cap coverage unchanged and
rebases only the sweep centre before angular interpolation in linear light and sRGB
RGBA8 storage. The catalog requires 100% similarity with one-channel LSB tolerance.
Rotated and non-uniform local matrices remain outside the route contract.

The existing native offscreen smoke validates the same local-matrix sweep square
stroke route and its stencil-cover clip. This wave promotes that proven bounded
shader transform without introducing a generic transform implementation.

