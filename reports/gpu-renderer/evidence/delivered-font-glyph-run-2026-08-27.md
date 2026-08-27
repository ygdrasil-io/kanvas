# Delivered font glyph-run evidence — 2026-08-27

`GPUDeliveredFontGlyphRunEvidenceTest` is the reproducible proof for the
shipped `LiberationSans-Regular.ttf` subset. It verifies nonzero glyph mapping,
the `FontTypeface.preparedTextOutline -> TextA8 -> WebGPU` route, headless
WebGPU readback, and the opaque A8 CPU oracle for the white affine and scaler
rows. `FontTypeface.getGlyphPath` is retained only as that CPU outline oracle.
The gradient row is explicitly limited to two stops with `CLAMP`.

The enclosing `gradtext`, `text_scale_skew`, and `fontscaler` GMs remain
unpromoted. Their exact refusal/non-promotion evidence is `refusals.json`.
No GM, threshold, or budget is changed.
