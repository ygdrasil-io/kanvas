# M62 Glyph Route Dashboard Diagnostics

Linear: `FOR-18`

## Decision

Expose glyph route diagnostics directly in each PM dashboard row that carries `font` metadata.

The dashboard now shows a `Glyph route` line with:

- font source;
- text input;
- shaping mode;
- `font-diagnostics.json` path;
- atlas policy;
- fallback policy.

This makes the current boundary explicit: supported text rows are outline/path rendering rows, not glyph atlas rows.

## Current Answer For PM

Are we using a glyph atlas?

No. The current passing text rows use generated glyph outlines routed through path/coverage rendering. A future glyph atlas must introduce explicit atlas artifacts, cache/ownership diagnostics, and gate rules before any row can claim atlas support.

## Rows Covered

| Scene | Dashboard route meaning |
|---|---|
| `font-latin-outline-drawstring` | Outline/path rendering for simple Latin glyph ids. |
| `font-textblob-positioned-glyph-run` | Outline/path rendering for a prepositioned simple glyph run. |
| `font-kerning-style-fixture` | Outline/path rendering for a simple kerning/style fixture. |
| `font-emoji-color-glyph-refusal` | Color glyph / emoji remains refused. |
| `font-complex-shaping-refusal` | Complex shaping remains refused. |
| `m62-missing-glyph-fallback-refusal` | Missing-glyph/fallback-family selection remains refused. |

## Validation

```text
rtk ./gradlew --no-daemon pipelineSceneDashboardGate pipelinePmBundle
rtk git diff --check
```
