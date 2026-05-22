# kanvas-skia GM similarity report

Snapshot of the latest `:kanvas-skia:test` run. Best-ever scores tracked in `test-similarity-scores.properties`. Triptych debug images (rendered ｜ diff ｜ reference) land in `build/debug-images/<gm-name>-comparison.png` when a test trips its threshold.

| Test | Similarity | Δ vs prev | Tolerance | Match / Total | Max diff (A,R,G,B) | Mean miss (A,R,G,B) |
|------|-----------:|----------:|----------:|--------------:|--------------------|---------------------|
| RadialGradient2GM | 57.39% | = | 8 | 183,638 / 320,000 | 0, 212, 241, 233 | 0, 38, 36, 36 |
| RadialGradient2NoditherGM | 57.41% | = | 8 | 183,702 / 320,000 | 0, 212, 241, 233 | 0, 38, 36, 36 |
| RadialGradient3GM | 100.00% | = | 8 | 250,000 / 250,000 | 0, 0, 0, 0 | 0, 0, 0, 0 |
| RadialGradient3NoditherGM | 100.00% | = | 8 | 250,000 / 250,000 | 0, 0, 0, 0 | 0, 0, 0, 0 |
| RadialGradient4GM | 100.00% | = | 8 | 250,000 / 250,000 | 0, 0, 0, 0 | 0, 0, 0, 0 |
| RadialGradient4NoditherGM | 100.00% | = | 8 | 250,000 / 250,000 | 0, 0, 0, 0 | 0, 0, 0, 0 |
| RadialGradientGM | 88.54% | = | 8 | 1,450,594 / 1,638,400 | 0, 10, 10, 10 | 0, 9, 9, 9 |
| RadialGradientPrecisionGM | 4.92% | = | 1 | 1,966 / 40,000 | 0, 18, 17, 15 | 0, 10, 10, 8 |
| TestExtractAlphaGM | 96.86% | = | 8 | 172,607 / 178,200 | 0, 203, 91, 84 | 0, 178, 53, 17 |
| TilemodesNpotGM | 99.21% | = | 8 | 488,911 / 492,800 | 0, 185, 185, 185 | 0, 42, 41, 45 |
| Tiling2BitmapGM | 95.40% | = | 8 | 378,245 / 396,500 | 0, 255, 255, 255 | 0, 14, 19, 14 |
| Tiling2GradientGM | 67.20% | = | 8 | 266,441 / 396,500 | 0, 255, 255, 255 | 0, 27, 16, 18 |
