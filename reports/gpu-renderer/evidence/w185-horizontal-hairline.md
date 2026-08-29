# W185 — Horizontal hairline

W185 promotes the identity-transform hairline on the native
`direct_device_quad` route. The public `KanvasSurface` scene records the
zero-width non-AA segment `(4,16) → (28,16)`, producing an opaque red device
row at `y=16` for columns `x=4..27`.

The independent CPU oracle models the exact one-pixel device-row contract,
with transparent RGBA8 outside the half-open segment. The catalog keeps this
hairline route distinct from `stencil_cover` path strokes.

The native offscreen smoke validates the identity hairline geometry, direct
device quad, exact readback, and submit/readback counters.
