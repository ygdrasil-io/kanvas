# W199 — Linear-gradient round stroke under winding path-clip refusal

W199 records a public `Kanvas Surface` refusal for a two-stop CLAMP
linear-gradient round-cap path stroke `(6,16) → (26,16)`, width `4`, under a
winding triangular clip.

The geometry route is selected, then preparation stops with the stable
diagnostic `unsupported.core_primitive.material.path_stencil`. This keeps the
material/stencil limitation explicit and avoids presenting a partial render as
supported evidence.
