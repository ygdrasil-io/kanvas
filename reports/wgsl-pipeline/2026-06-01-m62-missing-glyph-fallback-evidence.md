# M62 Missing Glyph Fallback Evidence

Linear: `FOR-17`

## Decision

Add `m62-missing-glyph-fallback-refusal` as the M62 missing-glyph/fallback evidence row.

This row is intentionally `expected-unsupported`: Kanvas currently has deterministic bundled-font outline rendering for simple glyph runs, but it does not yet own fallback-family selection or a glyph atlas/mask pipeline. The row documents that boundary with visible CPU/reference evidence and a stable GPU refusal reason.

## Scene

| Field | Value |
|---|---|
| Scene id | `m62-missing-glyph-fallback-refusal` |
| Base evidence | `font-latin-outline-drawstring` |
| Status | `expected-unsupported` |
| Font | `kanvas-skia/src/main/resources/fonts/liberation/LiberationSans-Regular.ttf` |
| Text input | `KANVAS U+10FFFF` |
| Shaping mode | `simple-glyph-id-map-missing-glyph` |
| CPU route | `cpu.text.outline.missing-glyph-oracle` |
| GPU route | `webgpu.text.refuse.missing-glyph-fallback` |
| Fallback | `font.missing-glyph-fallback-unsupported` |

## Artifacts

- Reference: `artifacts/m62-missing-glyph-fallback-refusal/skia.png`
- CPU: `artifacts/m62-missing-glyph-fallback-refusal/cpu.png`
- CPU diff: `artifacts/m62-missing-glyph-fallback-refusal/cpu-diff.png`
- CPU route: `artifacts/m62-missing-glyph-fallback-refusal/route-cpu.json`
- GPU route: `artifacts/m62-missing-glyph-fallback-refusal/route-gpu.json`
- Font diagnostics: `artifacts/m62-missing-glyph-fallback-refusal/font-diagnostics.json`
- Stats: `artifacts/m62-missing-glyph-fallback-refusal/stats.json`

## Non-Claims

- No fallback-family selection support is claimed.
- No glyph atlas, glyph mask, SDF, or LCD text support is claimed.
- No color glyph or emoji support is claimed.
- No complex shaping support is claimed.

## Validation

```text
rtk ./gradlew --no-daemon :kanvas-skia:test --tests 'org.skia.foundation.SkFont*'
rtk ./gradlew --no-daemon pipelineSceneDashboardGate pipelinePmBundle
rtk git diff --check
```
