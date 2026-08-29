# W184 — Scaled horizontal hairline

W184 promotes the scale-only hairline variant on the native
`direct_device_quad` route. The public `KanvasSurface` scene records the
zero-width non-AA segment `(4,8) → (14,8)` under a uniform `2×` scale. The
device-space result is the opaque red row `y=16`, columns `x=8..27`.

The independent CPU oracle models the exact one-pixel device-row contract,
with transparent RGBA8 outside the half-open segment. The catalog keeps this
hairline route separate from `stencil_cover` strokes.

The native offscreen smoke validates the same scale transform, direct device
quad, exact readback, and submit/readback counters.
