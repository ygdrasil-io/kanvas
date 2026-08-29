# W183 — Scaled translated horizontal hairline

W183 promotes the native hairline route `direct_device_quad`. The public
`KanvasSurface` scene records a zero-width non-AA horizontal path
`(4,8) → (14,8)`, then applies a uniform scale of `2×` and translation
`(2,3)`. The device-space result is the opaque red row `y=18`, columns
`x=10..29`.

The independent CPU oracle models the one-pixel device-row contract directly,
with transparent RGBA8 outside the finite segment. The catalog requires exact
output and keeps this route distinct from `stencil_cover` path strokes.

The native offscreen smoke validates the same transform, direct hairline quad,
exact readback, and submit/readback counters.
