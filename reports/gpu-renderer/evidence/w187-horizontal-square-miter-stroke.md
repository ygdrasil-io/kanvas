# W187 — Horizontal square/miter stroke

W187 promotes the standalone horizontal width-four stroke with square caps
and miter joins on the native path-stroke route. The segment
`(8,16) → (24,16)` paints the device body at `y=14..17` and extends to
`x=6..25`, exactly one half-width beyond each endpoint.

The independent pixel-center oracle models that finite square-cap extension;
native offscreen readback matches it exactly and records one submit plus one
readback copy.
