# M50-D First Font/Text Evidence Pack

Date: 2026-05-31
Milestone: M50 -- MEP Readiness Acceleration Toward 80%

## Scope

This pack adds selected generated font/text evidence from the existing pure
Kotlin OpenType/simple text surface. It does not claim broad font, emoji,
complex shaping, SDF, LCD, glyph-mask, fallback-family, or text-layout support.

## Generated Pass Rows

| Scene | Font source | Text input | Shaping mode | Route |
|---|---|---|---|---|
| `font-latin-outline-drawstring` | `kanvas-skia/src/main/resources/fonts/liberation/LiberationSans-Regular.ttf` | `KANVAS` | `simple-glyph-id-map` | `webgpu.text.outline.simple-latin` |
| `font-textblob-positioned-glyph-run` | `kanvas-skia/src/main/resources/fonts/liberation/LiberationSerif-Regular.ttf` | `GLYPH RUN` | `prepositioned-simple-run` | `webgpu.text.outline.positioned-glyph-run` |
| `font-kerning-style-fixture` | `kanvas-skia/src/main/resources/fonts/liberation/LiberationSans-Bold.ttf` | `AVATAR` | `simple-kerning-fixture` | `webgpu.text.outline.kerning-style-fixture` |

Each pass row includes:

- reference/oracle image;
- CPU image and diff;
- GPU image and diff;
- route diagnostics;
- `font-diagnostics.json`;
- stats;
- adapter metadata;
- `fallbackReason=none`.

## Generated Expected-Unsupported Rows

| Scene | Font source | Text input | Stable fallback reason |
|---|---|---|---|
| `font-emoji-color-glyph-refusal` | `skia-integration-tests/src/test/resources/fonts/test_glyphs-glyf_colr_1.ttf` | `EMOJI` | `font.color-glyph-emoji-unsupported` |
| `font-complex-shaping-refusal` | `kanvas-skia/src/main/resources/fonts/liberation/LiberationSerif-Italic.ttf` | `SHAPE` | `font.complex-shaping-requires-explicit-shaper` |

The refusal rows keep CPU/reference diagnostics and stable GPU refusal routes
visible in the dashboard. They are not support claims.

## Validation

```bash
rtk ./gradlew --no-daemon :kanvas-skia:test --tests 'org.skia.foundation.opentype.*'
rtk ./gradlew --no-daemon pipelineSceneDashboard
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
```
