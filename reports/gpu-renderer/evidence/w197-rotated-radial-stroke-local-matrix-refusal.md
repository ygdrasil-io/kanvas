# W197 — Rotated radial-stroke local-matrix refusal

W197 records a public `Kanvas Surface` refusal for a non-AA square-miter path
stroke `(5.25,8.25) → (21.25,20.25)`, filled by a two-stop CLAMP radial
gradient whose local shader matrix rotates by `90°`, under a winding path clip.

The route emits the stable diagnostic
`unsupported.material.mapping.local_matrix` before native preparation. The
catalog therefore documents the exact support boundary without claiming
pixel output for an unimplemented local-matrix mapping.
