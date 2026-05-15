# kanvas-skia GM similarity report

Snapshot of the latest `:kanvas-skia:test` run. Best-ever scores tracked in `test-similarity-scores.properties`. Triptych debug images (rendered ｜ diff ｜ reference) land in `build/debug-images/<gm-name>-comparison.png` when a test trips its threshold.

| Test | Similarity | Δ vs prev | Tolerance | Match / Total | Max diff (A,R,G,B) | Mean miss (A,R,G,B) |
|------|-----------:|----------:|----------:|--------------:|--------------------|---------------------|
| CoordClampShaderGM | 50.61% | = | 1 | 432,130 / 853,830 | 0, 199, 195, 225 | 0, 45, 43, 57 |
| Crbug224618GM | 0.42% | = | 1 | 2,693 / 640,000 | 0, 163, 236, 224 | 0, 89, 128, 76 |
| HighContrastFilterGM | 52.05% | = | 1 | 174,897 / 336,000 | 0, 255, 255, 255 | 0, 61, 59, 64 |
| MixedTextBlobsGM | 52.73% | = | 1 | 461,375 / 875,000 | 0, 255, 255, 255 | 0, 32, 31, 31 |
| TableMaskFilterGM | 83.47% | = | 1 | 133,558 / 160,000 | 0, 114, 114, 114 | 0, 14, 14, 14 |
| TypefaceStylesKerningGM | 93.31% | = | 1 | 286,650 / 307,200 | 0, 255, 255, 255 | 0, 215, 215, 215 |
