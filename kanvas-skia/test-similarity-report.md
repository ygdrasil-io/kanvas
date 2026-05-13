# kanvas-skia GM similarity report

Snapshot of the latest `:kanvas-skia:test` run. Best-ever scores tracked in `test-similarity-scores.properties`. Triptych debug images (rendered ｜ diff ｜ reference) land in `build/debug-images/<gm-name>-comparison.png` when a test trips its threshold.

| Test | Similarity | Δ vs prev | Tolerance | Match / Total | Max diff (A,R,G,B) | Mean miss (A,R,G,B) |
|------|-----------:|----------:|----------:|--------------:|--------------------|---------------------|
| ImageFiltersGraphGM | 76.36% | (new) | 1 | 68,727 / 90,000 | 0, 255, 255, 255 | 0, 101, 79, 44 |
| ImageFiltersScaledGM | 61.01% | (new) | 1 | 435,612 / 714,000 | 0, 255, 255, 255 | 0, 60, 65, 129 |
| ImageMagnifierBoundsGM | 66.19% | (new) | 8 | 260,276 / 393,216 | 0, 251, 251, 247 | 0, 55, 57, 60 |
| ImageMagnifierCroppedGM | 90.56% | (new) | 1 | 59,352 / 65,536 | 0, 43, 13, 242 | 0, 43, 13, 242 |
| ImageMagnifierGM | 71.77% | (new) | 8 | 179,420 / 250,000 | 0, 179, 243, 249 | 0, 95, 85, 114 |
| ImageResizeTiledGM | 83.61% | (new) | 8 | 256,856 / 307,200 | 0, 255, 255, 255 | 0, 202, 202, 202 |
| MixerCFGM | 30.71% | (new) | 1 | 331,636 / 1,080,000 | 0, 216, 238, 249 | 0, 92, 79, 100 |
| OffsetImageFilterGM | 84.51% | (new) | 1 | 50,703 / 60,000 | 0, 165, 154, 193 | 0, 100, 98, 116 |
| OverdrawCanvasGM | 85.31% | (new) | 1 | 213,263 / 250,000 | 0, 187, 187, 187 | 0, 19, 52, 59 |
| ShallowAnglePathArcToGM | 99.88% | (new) | 1 | 89,889 / 90,000 | 0, 255, 255, 255 | 0, 255, 255, 255 |
