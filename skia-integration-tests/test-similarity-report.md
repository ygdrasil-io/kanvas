# kanvas-skia GM similarity report

Snapshot of the latest `:kanvas-skia:test` run. Best-ever scores tracked in `test-similarity-scores.properties`. Triptych debug images (rendered ｜ diff ｜ reference) land in `build/debug-images/<gm-name>-comparison.png` when a test trips its threshold.

| Test | Similarity | Δ vs prev | Tolerance | Match / Total | Max diff (A,R,G,B) | Mean miss (A,R,G,B) |
|------|-----------:|----------:|----------:|--------------:|--------------------|---------------------|
| TilemodesNpotGM | 99.21% | (new) | 8 | 488,911 / 492,800 | 0, 185, 185, 185 | 0, 42, 41, 45 |
| Tiling2BitmapGM | 95.40% | (new) | 8 | 378,245 / 396,500 | 0, 255, 255, 255 | 0, 14, 19, 14 |
| Tiling2GradientGM | 67.20% | (new) | 8 | 266,441 / 396,500 | 0, 255, 255, 255 | 0, 27, 16, 18 |
