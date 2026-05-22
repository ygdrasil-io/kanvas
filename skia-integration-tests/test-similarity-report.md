# kanvas-skia GM similarity report

Snapshot of the latest `:kanvas-skia:test` run. Best-ever scores tracked in `test-similarity-scores.properties`. Triptych debug images (rendered ｜ diff ｜ reference) land in `build/debug-images/<gm-name>-comparison.png` when a test trips its threshold.

| Test | Similarity | Δ vs prev | Tolerance | Match / Total | Max diff (A,R,G,B) | Mean miss (A,R,G,B) |
|------|-----------:|----------:|----------:|--------------:|--------------------|---------------------|
| LatticeGM | 92.95% | (new) | 1 | 594,878 / 640,000 | 0, 183, 202, 186 | 0, 64, 71, 61 |
| MatrixConvolutionGM_basic | 43.94% | (new) | 1 | 65,905 / 150,000 | 0, 255, 255, 255 | 0, 45, 45, 45 |
| MatrixConvolutionGM_basic_color | 47.47% | (new) | 1 | 71,212 / 150,000 | 0, 247, 250, 115 | 0, 46, 46, 38 |
| MatrixConvolutionGM_big | 57.97% | (new) | 1 | 86,961 / 150,000 | 0, 255, 255, 255 | 0, 58, 58, 58 |
| PerlinNoiseLayeredGM | 0.00% | (new) | 1 | 0 / 250,000 | 0, 73, 76, 75 | 0, 36, 36, 36 |
| PerlinNoiseLocalMatrixGM | 62.50% | (new) | 1 | 192,008 / 307,200 | 0, 79, 95, 86 | 0, 11, 10, 9 |
