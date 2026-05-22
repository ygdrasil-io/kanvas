# kanvas-skia GM similarity report

Snapshot of the latest `:kanvas-skia:test` run. Best-ever scores tracked in `test-similarity-scores.properties`. Triptych debug images (rendered ｜ diff ｜ reference) land in `build/debug-images/<gm-name>-comparison.png` when a test trips its threshold.

| Test | Similarity | Δ vs prev | Tolerance | Match / Total | Max diff (A,R,G,B) | Mean miss (A,R,G,B) |
|------|-----------:|----------:|----------:|--------------:|--------------------|---------------------|
| GammaShaderTextGM | 82.95% | (new) | 8 | 74,652 / 90,000 | 0, 255, 255, 255 | 0, 92, 122, 87 |
| GradientsManyColorsGM | 80.74% | (new) | 8 | 284,202 / 352,000 | 0, 255, 255, 255 | 0, 18, 17, 17 |
