# W150 — translated local-matrix radial stroke under a winding triangle clip

W150 promotes the bounded local-shader-matrix variant of the radial stroke route.
The public `KanvasSurface` scene keeps the geometry and hard Winding clip fixed and
applies only the translation `(1.25, -0.75)` through `Shader.WithLocalMatrix`.

The CPU oracle keeps coverage in device space and rebases only radial sampling by
that local translation before linear-light interpolation and sRGB RGBA8 storage.
The catalog requires 100% similarity with a one-channel LSB tolerance. Rotated and
non-uniform local matrices remain explicitly outside the route contract.

Native proof comes from the existing offscreen smoke for the same local-matrix
radial stroke and winding stencil cover route; it validates the native submission
and readback against an independent oracle. This wave promotes the proven bounded
shader transform without introducing a generic transform implementation.

