# W200 — Three-stop radial stroke under winding path-clip refusal

W200 records a public `Kanvas Surface` refusal for a non-AA butt/miter path
stroke `(5.25,8.25) → (21.25,20.25)`, under a winding triangular clip, using a
three-stop CLAMP radial gradient (red → green → blue).

Preparation emits the stable diagnostic
`unsupported.material.radial_gradient_stop_count` before native submission.
The two-stop radial stroke routes remain rendered evidence; this case makes
the three-stop material boundary explicit.
