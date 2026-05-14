# kanvas-skia GM similarity report

Snapshot of the latest `:kanvas-skia:test` run. Best-ever scores tracked in `test-similarity-scores.properties`. Triptych debug images (rendered ｜ diff ｜ reference) land in `build/debug-images/<gm-name>-comparison.png` when a test trips its threshold.

| Test | Similarity | Δ vs prev | Tolerance | Match / Total | Max diff (A,R,G,B) | Mean miss (A,R,G,B) |
|------|-----------:|----------:|----------:|--------------:|--------------------|---------------------|
| GradientsNoTextureGM | 81.13% | = | 1 | 319,345 / 393,600 | 0, 63, 43, 46 | 0, 21, 14, 12 |
| ImageFiltersBaseGM | 85.74% | = | 8 | 300,101 / 350,000 | 0, 217, 242, 239 | 0, 50, 72, 45 |
| MandolineGM | 91.83% | = | 1 | 244,260 / 266,000 | 0, 255, 255, 255 | 0, 61, 61, 61 |
| OverStrokeGM | 83.48% | = | 1 | 208,710 / 250,000 | 0, 255, 255, 255 | 0, 98, 91, 102 |
