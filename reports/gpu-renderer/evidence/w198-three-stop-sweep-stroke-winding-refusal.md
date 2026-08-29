# W198 — Three-stop sweep stroke under winding path-clip refusal

W198 records a public `Kanvas Surface` refusal for a non-AA butt/miter path
stroke `(5.25,8.25) → (21.25,20.25)`, under a winding triangular clip, using a
three-stop sweep gradient (red → green → blue).

Preparation emits the stable diagnostic
`unsupported.material.sweep_gradient_stop_count` before native submission.
The supported two-stop sweep routes remain separate rendered evidence; this
case keeps the three-stop boundary explicit until its native mapping is
implemented.
