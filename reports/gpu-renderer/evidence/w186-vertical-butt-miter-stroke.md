# W186 — Vertical butt/miter stroke

W186 promotes a standalone vertical width-four stroke from the public
`KanvasSurface` API to the native path-stroke route. The non-AA segment
`(16,4) → (16,28)` produces an opaque red four-pixel column at `x=14..17`
for the half-open device rows `y=4..27`.

The independent CPU oracle evaluates pixel-center distance to the finite
segment, with no cap extension, so the evidence also proves that the butt cap
does not paint beyond either endpoint. Native offscreen readback confirms the
same RGBA8 pixels and one submit/readback pair.
