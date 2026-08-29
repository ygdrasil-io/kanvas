# W63 — Glyph/font route evidence

The font and glyph modules contain a real CPU scaler/rasterizer and A8 atlas
planner, and the GPU renderer contains the R8/A8 atlas payload contracts. The
checked-out production sources do not ship a font resource under a production
resource directory; the `.ttf` files found in the checkout are test fixtures.
`TextBridge` therefore accepts a `FontTypeface` or an explicitly resolvable
classpath `KanvasTypeface`, and returns `null` when that dependency is absent.
No system-font lookup, fake glyph, or procedural substitute is introduced.

The dependency gate is consequently still active for a production glyph GM.
The existing tests cover glyph geometry, A8 rasterization, atlas placement,
cache/strike identity, ownership and deterministic refusal paths. This wave
does not claim a new native WebGPU text pixel promotion.

Verification:

```text
./gradlew --offline --no-daemon :font:glyph:test \
  --tests '*GlyphSurfaceTest' \
  --tests '*GlyphAtlas*' \
  --tests '*GlyphStrikeKey*'
```

Result: 131 glyph-module tests passed (67 `GlyphSurfaceTest`, 59
`ColorGlyphSurfaceTest`, and 5 `GlyphStrikeKeyContractTest`). No
`gpu-renderer-scenes` files were modified.
