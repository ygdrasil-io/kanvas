# W148 — radial stroke under a winding triangle clip

W148 promotes the bounded radial-stroke/path-clip combination to the GPU evidence
catalog. The public `KanvasSurface` scene uses one opaque two-stop `CLAMP` radial
gradient, a width-four butt/miter segment, and a non-AA winding triangle clip.

The independent CPU oracle evaluates pixel centres in device space, applies the
winding clip and butt-segment distance test, then performs linear-light radial
interpolation before sRGB RGBA8 storage. The catalog comparison policy allows one
channel LSB and requires 100% similarity.

Native route proof is supplied by the existing GPU frame-path smoke for the same
geometry and material: it reaches `native.path_stroke.stencil_cover`, configures
winding increment/decrement stencil coverage, submits one offscreen frame, and
validates the readback against its independent oracle. W148 adds the catalog pixel
oracle and promotion; unsupported variants remain outside this bounded contract.

