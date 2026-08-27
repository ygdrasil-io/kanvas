# Delivered font glyph-run evidence — 2026-08-27

`GPUDeliveredFontGlyphRunEvidenceTest` is the reproducible proof for the
shipped `LiberationSans-Regular.ttf` subset. It verifies nonzero glyph mapping,
the `FontTypeface.preparedTextOutline -> TextA8 -> WebGPU` route and a complete
CPU A8 oracle for every row, including the two-stop `CLAMP` gradient. The CPU
interpreter samples the immutable CPU-prepared A8 atlas with each sealed device
quad/UV, applies the admitted material uniforms, composites source-over, and
encodes the full 96 x 48 RGBA8 buffer. Every byte is compared to headless WebGPU
readback; `diff.json` records CPU/GPU hashes and exact deltas. `getGlyphPath`
is retained only as the independent CPU outline oracle.

The enclosing `gradtext`, `text_scale_skew`, and `fontscaler` GMs remain
unpromoted. Their exact refusal/non-promotion evidence is `refusals.json`.
No GM, threshold, or budget is changed.
