# kanvas-skia GM similarity report

Snapshot of the latest `:kanvas-skia:test` run. Best-ever scores tracked in `test-similarity-scores.properties`. Triptych debug images (rendered ｜ diff ｜ reference) land in `build/debug-images/<gm-name>-comparison.png` when a test trips its threshold.

| Test | Similarity | Δ vs prev | Tolerance | Match / Total | Max diff (A,R,G,B) | Mean miss (A,R,G,B) |
|------|-----------:|----------:|----------:|--------------:|--------------------|---------------------|
| FillTypesGM | 99.48% | = | 1 | 697,770 / 701,400 | 0, 255, 255, 255 | 0, 28, 28, 28 |
| FilterBugGM | 98.67% | = | 4 | 22,200 / 22,500 | 0, 15, 15, 15 | 0, 11, 11, 11 |
| ImageBlurTiledGM | 70.81% | = | 1 | 217,514 / 307,200 | 0, 162, 162, 162 | 0, 11, 11, 11 |
| ModeColorFiltersGM | 44.38% | = | 4 | 232,688 / 524,288 | 0, 244, 252, 251 | 0, 51, 58, 52 |
