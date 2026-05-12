# kanvas-skia GM similarity report

Snapshot of the latest `:kanvas-skia:test` run. Best-ever scores tracked in `test-similarity-scores.properties`. Triptych debug images (rendered ｜ diff ｜ reference) land in `build/debug-images/<gm-name>-comparison.png` when a test trips its threshold.

| Test | Similarity | Δ vs prev | Tolerance | Match / Total | Max diff (A,R,G,B) | Mean miss (A,R,G,B) |
|------|-----------:|----------:|----------:|--------------:|--------------------|---------------------|
| BackdropHintrectClippingGM | 64.03% | (new) | 1 | 335,714 / 524,288 | 0, 121, 159, 156 | 0, 21, 21, 21 |
| BackdropImagefilterCroprectGM | 92.00% | (new) | 1 | 276,000 / 300,000 | 0, 163, 246, 253 | 0, 120, 139, 165 |
| BackdropScalefactorGM | 61.72% | (new) | 1 | 485,394 / 786,432 | 0, 147, 160, 159 | 0, 19, 17, 17 |
| BitmapCopyGM | 99.83% | (new) | 8 | 177,896 / 178,200 | 0, 58, 58, 58 | 0, 25, 25, 25 |
| BitmapFiltersGM | 60.72% | (new) | 8 | 81,973 / 135,000 | 0, 210, 241, 234 | 0, 40, 41, 45 |
| ClippedBitmapShadersClampGM | 100.00% | (new) | 1 | 90,000 / 90,000 | 0, 0, 0, 0 | 0, 0, 0, 0 |
| ClippedBitmapShadersClampHqGM | 94.74% | (new) | 1 | 85,264 / 90,000 | 0, 62, 42, 45 | 0, 9, 7, 7 |
| ClippedBitmapShadersMirrorGM | 100.00% | (new) | 1 | 90,000 / 90,000 | 0, 0, 0, 0 | 0, 0, 0, 0 |
| ClippedBitmapShadersMirrorHqGM | 65.01% | (new) | 1 | 58,512 / 90,000 | 0, 63, 43, 45 | 0, 19, 14, 14 |
| ClippedBitmapShadersTileGM | 100.00% | (new) | 1 | 90,000 / 90,000 | 0, 0, 0, 0 | 0, 0, 0, 0 |
| ClippedBitmapShadersTileHqGM | 63.59% | (new) | 1 | 57,232 / 90,000 | 0, 60, 39, 43 | 0, 36, 24, 26 |
| FillTypesGM | 99.48% | = | 1 | 697,770 / 701,400 | 0, 255, 255, 255 | 0, 28, 28, 28 |
| FilterBugGM | 98.67% | = | 4 | 22,200 / 22,500 | 0, 15, 15, 15 | 0, 11, 11, 11 |
| ImageBlurTiledGM | 70.81% | = | 1 | 217,514 / 307,200 | 0, 162, 162, 162 | 0, 11, 11, 11 |
| ModeColorFiltersGM | 44.38% | = | 4 | 232,688 / 524,288 | 0, 244, 252, 251 | 0, 51, 58, 52 |
