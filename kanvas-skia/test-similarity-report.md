# kanvas-skia GM similarity report

Snapshot of the latest `:kanvas-skia:test` run. Best-ever scores tracked in `test-similarity-scores.properties`. Triptych debug images (rendered ｜ diff ｜ reference) land in `build/debug-images/<gm-name>-comparison.png` when a test trips its threshold.

| Test | Similarity | Δ vs prev | Tolerance | Match / Total | Max diff (A,R,G,B) | Mean miss (A,R,G,B) |
|------|-----------:|----------:|----------:|--------------:|--------------------|---------------------|
| BlurDrawImageGM | 70.54% | = | 1 | 46,229 / 65,536 | 0, 176, 179, 181 | 0, 21, 24, 21 |
| BlurImageGM | 92.86% | = | 1 | 232,143 / 250,000 | 0, 147, 147, 154 | 0, 26, 25, 31 |
| BlurSmallSigmaGM | 87.11% | = | 1 | 114,176 / 131,072 | 0, 137, 137, 137 | 0, 43, 43, 43 |
| ClipRegionGM | 100.00% | = | 1 | 65,536 / 65,536 | 0, 0, 0, 0 | 0, 0, 0, 0 |
