# kanvas-skia GM similarity report

Snapshot of the latest `:kanvas-skia:test` run. Best-ever scores tracked in `test-similarity-scores.properties`. Triptych debug images (rendered ｜ diff ｜ reference) land in `build/debug-images/<gm-name>-comparison.png` when a test trips its threshold.

| Test | Similarity | Δ vs prev | Tolerance | Match / Total | Max diff (A,R,G,B) | Mean miss (A,R,G,B) |
|------|-----------:|----------:|----------:|--------------:|--------------------|---------------------|
| ImageGM | 98.14% | (new) | 8 | 1,130,615 / 1,152,000 | 0, 255, 255, 255 | 0, 69, 68, 66 |
| ImageShaderGM | 95.19% | (new) | 8 | 364,096 / 382,500 | 0, 214, 245, 237 | 0, 165, 207, 44 |
| LocalMatrixImageShaderGM | 100.00% | = | 1 | 62,500 / 62,500 | 0, 0, 0, 0 | 0, 0, 0, 0 |
