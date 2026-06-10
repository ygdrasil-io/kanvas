# KAN-043 Text Shaping And Fallback Scope

KAN-043 packages the explicit text shaping and fallback scope from existing
font/text evidence. It makes the font identity, shaping route, clusters, glyph
ids, CPU/GPU route or refusal, and fallback policy visible without adding
renderer behavior.

## Summary

| Metric | Count |
|---|---:|
| Total rows | 4 |
| Support rows | 2 |
| Refusal rows | 2 |
| Rows missing font hash | 0 |
| Rows missing shaping route | 0 |
| Rows missing clusters | 0 |
| Rows missing glyph ids | 0 |
| Implicit fallback rows | 0 |

## Scope Rows

| Row | Status | Category | Font | Shaping mode | Reason | CPU route | GPU route |
|---|---|---|---|---|---|---|---|
| `text.simple-latin.line.v1` | `pass` | `simple-latin-support` | `Liberation Sans` | `simple-codepoint-order` | `none` | `cpu.text.outline-path.simple-latin` | `webgpu.text.outline-path.simple-latin` |
| `font-kerning-style-fixture` | `pass` | `bounded-shaping-support` | `Liberation Sans Bold` | `simple-kerning-fixture` | `none` | `cpu.text.outline.kerning-style` | `webgpu.text.outline.kerning-style-fixture` |
| `font-complex-shaping-refusal` | `expected-unsupported` | `bounded-shaping-refusal` | `Liberation Serif Italic` | `unsupported` | `font.shaping-feature-unsupported` | `cpu.text.refusal-oracle.complex-shaping` | `webgpu.text.refuse` |
| `m62-missing-glyph-fallback-refusal` | `expected-unsupported` | `fallback-missing-glyph-refusal` | `Liberation Sans` | `simple-glyph-id-map-missing-glyph` | `font.shaping-fallback-missing` | `cpu.text.outline.missing-glyph-oracle` | `webgpu.text.refuse.missing-glyph-fallback` |

## Claim Guard

| Guard | Value |
|---|---|
| rowsMissingFontHash | `[]` |
| rowsMissingShapingRoute | `[]` |
| rowsMissingClusters | `[]` |
| rowsMissingGlyphIds | `[]` |
| supportRowsMissingProofs | `[]` |
| refusalRowsMissingReason | `[]` |
| implicitSystemFallbackRows | `[]` |
| hiddenBroadShapingClaims | `[]` |
| externalFontEngineClaims | `[]` |

No implicit system font fallback is allowed. Missing glyph fallback remains a
visible refusal with `font.shaping-fallback-missing` and legacy reason
`font.missing-glyph-fallback-unsupported`.

## Required Validation

- `validateKan043TextShapingFallbackScope`
- `:gpu-raster:pipelineConformanceTest -- includes SimpleLatinLineSceneEvidenceTest`
- `:kanvas-skia:pipelineConformanceTest -- includes font/shaper contract tests in the standard suite`
- `pipelinePmBundle`

## Validation

| Check | Status | Evidence |
|---|---|---|
| `font-identity-visible` | `pass` | Every row records font face, source path, and SHA-256 hash. |
| `glyphs-and-clusters-visible` | `pass` | Rows expose explicit clusters and glyph id arrays; missing glyph uses glyph id 0 (.notdef). |
| `support-refusal-separated` | `pass` | Simple Latin and bounded kerning support remain separate from complex shaping and fallback refusals. |
| `no-implicit-system-fallback` | `pass` | Every fallback policy records implicitSystemFallback=false. |

## Non-Claims

- KAN-043 does not add renderer, shader, selector, PipelineKey, threshold, or budget changes.
- KAN-043 does not claim broad text, broad shaping, broad font fallback, full GSUB/GPOS, Arabic/Indic shaping, emoji ZWJ, or color-font support.
- KAN-043 does not add or require HarfBuzz, FreeType, Fontations, CoreText, DirectWrite, fontconfig, AWT, JNI, Ganesh, Graphite, SkSL compiler, SkSL IR, or SkSL VM.
- KAN-043 does not convert missing glyph fallback or complex shaping refusal into support.
