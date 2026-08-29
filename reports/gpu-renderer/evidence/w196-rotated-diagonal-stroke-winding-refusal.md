# W196 — Rotated diagonal stroke under winding path-clip refusal

W196 records a public `Kanvas Surface` refusal for a non-AA butt/miter path
stroke `(8.25,8.25) → (20.25,14.25)`, rotated `45°` around `(16,16)`, under a
winding triangular path clip.

The preparation route reports the stable diagnostic
`unsupported.geometry.perspective_path` and stops before native submission.
This is intentionally a refusal proof: the bounded transform classes already
promoted by the renderer remain supported, while this non-right-angle rotated
composition is not advertised as rendered.
