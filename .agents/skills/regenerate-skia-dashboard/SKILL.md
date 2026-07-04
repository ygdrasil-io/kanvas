---
name: regenerate-skia-dashboard
description: Use when asked to regenerate the Skia GM visual dashboard, renders, or comparison scores in the Kanvas project
---

# Regenerate Skia GM Dashboard

## Command

```bash
./gradlew :integration-tests:skia:generateSkiaDashboard
```

The task `generateSkiaDashboard` depends on `generateSkiaRenders`, so renders are regenerated automatically before the dashboard.

## Output

`integration-tests/skia/build/reports/skia-gm-dashboard/index.html`

## Requirements

- macOS with WebGPU support (render generation requires it)
- Reference PNGs at `integration-tests/skia/src/test/resources/reference/`

## What It Does

1. Regenerates Kanvas render PNGs for all Skia GMs (via `SkiaRenderGenerator`)
2. Compares each render against its reference image (via `ComparisonUtils`)
3. Generates diff PNGs for non-identical renders
4. Writes `data/gms.json` with similarity scores, thresholds, per-pixel diagnostics
5. Copies reference/generated/diff images into the output
6. Produces a self-contained `index.html` with summary stats, filters, and comparison cards

## Note

Scores in `test-similarity-scores.properties` are managed by `SimilarityTracker` during test runs (`./gradlew :integration-tests:skia:test`), not by the dashboard task. The dashboard reads existing scores and displays them.
