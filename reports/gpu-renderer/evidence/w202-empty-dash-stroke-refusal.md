# W202 — Empty dash stroke refusal

W202 records a public `Kanvas Surface` refusal for a width-four red path
stroke `(4,8) → (24,8)` carrying `PathEffect.Dash(floatArrayOf())`.

The product preserves the public operation but stops before native preparation
with `unsupported.core_primitive.stroke.dash_exact_lowering`. An empty dash is
therefore an explicit unsupported variant, not a silently rendered solid
stroke or an accidental no-op.
