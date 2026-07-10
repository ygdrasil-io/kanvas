# Glyph Coordinate Mapping For Text GM Orientation

Date: 2026-07-10
Status: Design approved

## Goal

Fix text GM orientation and placement by introducing one shared conversion from
font glyph coordinates to Kanvas canvas coordinates.

The immediate visible failure is `text_scale_skew`: generated text is vertically
flipped relative to the Skia reference. The same root cause affects more than
the A8 atlas path because shader/stroke text and color glyph outline routes also
consume raw glyph contours.

## Context

Kanvas currently keeps `GlyphScaler` output in font coordinates. TrueType and
OpenType outlines are y-up around the text baseline, while the Kanvas render
surface and GM PNGs are y-down. The current text atlas path stores glyph mask
rectangles as `Rect(0, 0, width, height)` and places quads at the glyph baseline
position. That loses the glyph bearing and baseline-relative bounds, then samples
the A8 atlas as though its first row were the top of a screen-space glyph.

The previous text precision design fixed hardcoded quad dimensions and per-run
font size. This design extends that work by fixing orientation and
baseline-relative placement.

Relevant constraints:

- Keep `GlyphScaler` as a font-domain component that returns font coordinates.
- Keep WebGPU as the GPU backend.
- Do not add a substitute font stack.
- Do not change global GM thresholds as part of this correction.
- Validate through generated GM renders before claiming visual improvement.

## Recommended Approach

Add a Kanvas-side helper for glyph coordinate conversion and use it across all
text routes.

Rejected alternatives:

- Patch only `drawTextAtlasPass` by flipping UVs or quads. This fixes the
  narrow A8 atlas symptom but leaves shader/stroke text and color glyph outline
  routes inconsistent.
- Change `GlyphScaler` to emit y-down outlines. This would make the font module
  less faithful to OpenType coordinates and could break font/scaler contracts.

## Architecture

Introduce a helper in `kanvas/text`, for example `GlyphCoordinateMapper`, with a
small data model such as `GlyphPlacement`.

The helper receives:

- a `ScaledGlyph`;
- a glyph baseline position from a `KanvasGlyphRun`;
- optional CTM-independent glyph metadata.

It returns:

- baseline-relative canvas bounds for the glyph;
- device/canvas placement rect for atlas quads;
- y-down outline commands for outline routes;
- mask dimensions for A8 atlas packing;
- empty-glyph status when the glyph has no drawable outline or mask.

`GlyphScaler` remains unchanged. The conversion happens after scaling and before
the glyph is handed to rasterization or WebGPU draw submission.

## Data Flow

### Atlas Text

```text
TextBlob / KanvasGlyphRun
  -> GlyphScaler.scaleGlyph(glyphId, run.fontSize)
  -> GlyphCoordinateMapper.map(scaledGlyph, baselinePosition)
  -> A8Rasterizer receives a stable drawable mask orientation
  -> TextBridge stores placement rect relative to baseline
  -> GpuTextBlob carries glyphRects with real bearing/baseline offsets
  -> drawTextAtlasPass places quad at pos + glyphRect
  -> WebGPU samples UVs without route-local Y hacks
```

The placement rect must no longer be `Rect(0, 0, width, height)` unless the
glyph actually starts at the baseline origin. The rect should preserve the
font-derived left bearing and vertical offset.

### Outline Text

```text
DrawText route
  -> GlyphScaler.scaleGlyph(...)
  -> GlyphCoordinateMapper.map(...)
  -> renderShaderText / renderColorText / drawGlyphPath consume y-down commands
  -> GPURenderer applies CTM once
```

The renderer must stop adding raw `cmd.y + pos.y` directly. All routes should
consume the same mapped glyph commands and then apply the current transform.

## Offset Ownership

The correction should keep a single owner for text origin offsets.

Current behavior stores `(x, y)` inside `Font.toTextBlob()` positions and also
stores `(x, y)` on `DisplayOp.DrawText`. The implementation must move to this
stable convention and test it:

- `TextBlob` positions are local to the text blob;
- `DisplayOp.DrawText.x/y` is the draw origin;
- renderer placement uses draw origin plus per-glyph local position plus mapped
  glyph bounds.

This may require adapting `Font.toTextBlob()` and any GM-created text blobs that
currently assume absolute positions.

## Error Handling

- Empty glyph outlines remain skipped or refused through existing diagnostics.
- Missing rasterized masks keep the current graceful degradation behavior.
- Unsupported color glyph representations keep their current stable diagnostics.
- No route should silently approximate with a different coordinate convention.

## Testing

Add targeted tests before or with the implementation:

- Unit test for `GlyphCoordinateMapper`: a glyph with positive font-space y
  above the baseline maps to a canvas rect above the baseline.
- Unit test for baseline-relative rect preservation: left bearing and top/bottom
  offsets survive into `GpuTextBlob.glyphRects`.
- Regression test for text origin ownership: `drawString("A", x, y)` should not
  double-apply `(x, y)`.
- Existing text bridge and renderer tests should be updated to assert the new
  placement convention instead of `Rect(0, 0, width, height)`.

## GM Validation

Run generated renders and inspect the output:

```bash
./gradlew :integration-tests:skia:generateSkiaRendersFor -Pgm.name=text_scale_skew -Pgm.includeBlocking=true
./gradlew :integration-tests:skia:generateSkiaRendersFor -Pgm.name=text_scale_skew_rotate -Pgm.includeBlocking=true
```

Run the GM runner for the same rows to capture similarity against the checked-in
references when WebGPU is available in the local environment. If WebGPU is not
available, record that blocker and rely on generated-render inspection only:

```bash
./gradlew :integration-tests:skia:test --tests "*SkiaGmRunner*" \
  -Dkanvas.gm.includeBlocking=true \
  -Dkanvas.render.debugLevel=PIXEL
```

The validation evidence should include:

- regenerated Kanvas PNGs for `text_scale_skew` and `text_scale_skew_rotate`;
- comparison against `integration-tests/skia/src/test/resources/reference`;
- similarity scores when available;
- no global threshold lowering;
- no unrelated generated-render churn.

## Non-Goals

- Complex shaping.
- Full font fallback.
- LCD/subpixel text.
- Emoji ZWJ sequence support.
- Broad color font parity beyond routes already present.
- New Skia/SkSL compiler behavior.

## Completion Criteria

- Text atlas output is no longer vertically flipped for simple Latin GMs.
- Shader/stroke and color glyph outline routes use the same glyph coordinate
  mapping helper.
- Text origin ownership is explicit and covered by tests.
- Targeted unit tests pass.
- `text_scale_skew` and `text_scale_skew_rotate` generated renders are visibly
  oriented like their references.
- The workspace does not retain unrelated generated-render changes.
