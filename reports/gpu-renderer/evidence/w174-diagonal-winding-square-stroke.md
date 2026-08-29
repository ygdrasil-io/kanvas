# W174 — Winding clip with a diagonal square stroke

W174 promotes the diagonal square-cap path-stroke route under a hard Winding
triangle clip. The public `KanvasSurface` scene draws one opaque width-four
miter stroke with square end caps and keeps anti-aliasing disabled for the
pixel-exact contract.

The independent CPU oracle evaluates the device-space triangle and the union of
the diagonal stroke body with its square end-cap extensions at pixel centres.
The catalog requires exact opaque RGBA8 output and records the native
stencil-cover path-stroke route.

The native offscreen smoke validates exact readback and submit/readback counts;
unsupported transform classes remain explicit refusals.
