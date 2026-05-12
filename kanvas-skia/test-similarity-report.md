# kanvas-skia GM similarity report

Snapshot of the latest `:kanvas-skia:test` run. Best-ever scores tracked in `test-similarity-scores.properties`. Triptych debug images (rendered ｜ diff ｜ reference) land in `build/debug-images/<gm-name>-comparison.png` when a test trips its threshold.

| Test | Similarity | Δ vs prev | Tolerance | Match / Total | Max diff (A,R,G,B) | Mean miss (A,R,G,B) |
|------|-----------:|----------:|----------:|--------------:|--------------------|---------------------|
| AnisotropicImageScaleLinearGM | 57.02% | = | 6 | 414,158 / 726,280 | 0, 255, 255, 255 | 0, 95, 95, 95 |
| BlurPositioningGM | 98.00% | = | 2 | 235,208 / 240,000 | 0, 130, 130, 130 | 0, 36, 36, 36 |
| BlurTextSmallRadiiGM | 87.03% | = | 1 | 13,054 / 15,000 | 0, 76, 28, 84 | 0, 19, 6, 16 |
| Crbug1156804GM | 72.91% | = | 1 | 45,567 / 62,500 | 0, 25, 14, 28 | 0, 6, 3, 6 |
| Crbug905548GM | 49.61% | = | 1 | 9,922 / 20,000 | 0, 124, 124, 124 | 0, 12, 12, 12 |
| DrawableGM | 98.46% | = | 1 | 48,736 / 49,500 | 0, 53, 49, 5 | 0, 17, 14, 2 |
| DrawlinesWithLocalMatrixGM | 54.72% | = | 8 | 136,807 / 250,000 | 0, 56, 179, 231 | 0, 16, 10, 13 |
| GetPosTextPathGM | 99.13% | = | 16 | 371,153 / 374,400 | 0, 100, 80, 102 | 0, 18, 19, 18 |
| LargeClippedPathEvenoddGM | 99.70% | = | 2 | 996,970 / 1,000,000 | 0, 71, 92, 6 | 0, 32, 22, 2 |
| LargeClippedPathWindingGM | 99.78% | = | 2 | 997,831 / 1,000,000 | 0, 71, 92, 6 | 0, 32, 23, 2 |
| LargeGlyphBlurGM | 58.06% | = | 1 | 668,795 / 1,152,000 | 0, 152, 152, 152 | 0, 8, 8, 8 |
| RuntimeFunctionsGM | 10.72% | = | 4 | 7,028 / 65,536 | 0, 109, 22, 22 | 0, 34, 13, 11 |
| Skbug5321GM | 96.86% | = | 4 | 15,870 / 16,384 | 0, 255, 255, 255 | 0, 255, 255, 255 |
| Skbug8955GM | 99.04% | = | 1 | 9,904 / 10,000 | 0, 154, 154, 154 | 0, 73, 73, 73 |
