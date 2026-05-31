# M62 Font/Text Baseline Audit

Linear: `FOR-16`

## Decision

M62 starts from the existing generated M50 font/text rows. The current supported route is outline/path text rendering through generated glyph outlines. It is not a glyph mask atlas, SDF, LCD, full shaping, color glyph, emoji, or fallback-family pipeline.

## Baseline Rows

| Scene | Status | Reference | Font source | Text | Shaping mode | GPU route claim |
|---|---|---|---|---|---|---|
| `font-latin-outline-drawstring` | `pass` | `cpu-oracle` | `kanvas-skia/src/main/resources/fonts/liberation/LiberationSans-Regular.ttf` | `KANVAS` | `simple-glyph-id-map` | `webgpu.text.outline.simple-latin`, `glyphRepresentation=outline` |
| `font-textblob-positioned-glyph-run` | `pass` | `cpu-oracle` | `kanvas-skia/src/main/resources/fonts/liberation/LiberationSerif-Regular.ttf` | `GLYPH RUN` | `prepositioned-simple-run` | `webgpu.text.outline.positioned-glyph-run`, `glyphRepresentation=outline` |
| `font-kerning-style-fixture` | `pass` | `cpu-oracle` | `kanvas-skia/src/main/resources/fonts/liberation/LiberationSans-Bold.ttf` | `AVATAR` | `simple-kerning-fixture` | `webgpu.text.outline.kerning-style-fixture`, `glyphRepresentation=outline` |
| `font-emoji-color-glyph-refusal` | `expected-unsupported` | `cpu-oracle` | `skia-integration-tests/src/test/resources/fonts/test_glyphs-glyf_colr_1.ttf` | `EMOJI` | `emoji-color-glyph` | `font.color-glyph-emoji-unsupported` |
| `font-complex-shaping-refusal` | `expected-unsupported` | `cpu-oracle` | `kanvas-skia/src/main/resources/fonts/liberation/LiberationSerif-Italic.ttf` | `SHAPE` | `complex-shaping` | `font.complex-shaping-requires-explicit-shaper` |
| `m52-color-emoji-blendmodes-refusal` | `expected-unsupported` | `cpu-oracle` | derived from `font-emoji-color-glyph-refusal` | color emoji blend modes fixture | color glyph/emoji | `font.color-glyph-emoji-unsupported` |

## Artifact Expectations

Every existing pass row has:

- reference, CPU, GPU, CPU diff, GPU diff, and stats artifacts;
- CPU and GPU route diagnostics;
- `font.glyphDiagnostics` pointing to `font-diagnostics.json`;
- `fallbackReason=none`;
- adapter metadata for the WebGPU route.

Every existing refusal row has:

- reference/CPU/diff evidence;
- CPU and GPU route diagnostics;
- stable non-`none` GPU fallback reason;
- no GPU image claim.

## Gate Hardening

`pipelineSceneDashboardGate` now enforces the M62 baseline boundary for generated font/text rows:

- font/text pass rows require `font.glyphDiagnostics`;
- font/text pass rows must keep an outline/path rendering claim;
- font/text pass rows must not claim atlas routes unless future atlas artifacts are introduced;
- complex shaping rows cannot silently become `pass` without an explicit shaper milestone.

## Non-Claims

- No glyph atlas support is claimed.
- No glyph mask, SDF, LCD, or subpixel text pipeline is claimed.
- No HarfBuzz-like complex shaping is claimed.
- No emoji/color glyph rendering support is claimed.
- No fallback-family selection support is claimed.

## Validation

```text
rtk ./gradlew --no-daemon pipelineSceneDashboardGate pipelinePmBundle
rtk git diff --check
```
