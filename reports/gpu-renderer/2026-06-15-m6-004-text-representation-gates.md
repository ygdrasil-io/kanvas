# GPU Renderer M6-004 Text Representation Gates

Date: 2026-06-15

## Scope

| Ticket | Final status | Scope |
|---|---|---|
| KGPU-M6-004 | `done` | Add deterministic dependency/refusal gates for SDF, color, bitmap, SVG, emoji, LCD, and CPU-rendered text texture representations without promoting a text GPU route. |

## Evidence

- `GPUTextRepresentationGateMatrix` records the current text representation
  refusal matrix for `A8MaskAtlas`, `SDFMaskAtlas`, `COLRColorGlyph`,
  `BitmapGlyph`, `SVGGlyph`, `EmojiColorGlyph`, `LCDMask`, and
  `CPURenderedTextTexture`.
- The dump lines keep the legacy gates visible: `dftext`,
  `scaledemoji_rendering`, and `coloremoji_blendmodes`.
- `dependency.text.emoji_color_glyph_unavailable` is recorded as the current
  emoji color glyph dependency gate; SDF/color/bitmap/SVG/LCD reuse the stable
  `unsupported.text.*` route refusal codes from the text target.
- `GPUTextCommandHandoffTest` proves the matrix contents, deterministic dump,
  and non-promotion status.
- Independent review `019ec8df-9efc-72e0-bdef-b6a9b4e75600` verdict:
  `ACCEPT`; no support claim, CPU-rendered fallback, or route promotion was
  found.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.text.GPUTextCommandHandoffTest
rtk ./gradlew --no-daemon :font:gpu-api:test :gpu-renderer:test
```

Independent review also ran:

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.text.GPUTextCommandHandoffTest
rtk git diff --check
```

## Non-Claims And Remaining Gates

- No GPU text route is promoted.
- No A8 atlas sampling route, SDF route, color glyph route, bitmap glyph route,
  SVG glyph route, LCD text route, or emoji rendering support is claimed.
- No WebGPU adapter execution, upload/binding/readback evidence, or `dftext`
  retirement is claimed.
- No CPU-rendered full text texture fallback is allowed.
- KGPU-M6-002 and KGPU-M6-003 remain blocked on upload, binding, WGSL,
  adapter-backed sampling/readback, and resource-generation evidence.
