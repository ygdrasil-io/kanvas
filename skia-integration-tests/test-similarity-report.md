# kanvas-skia GM similarity report

Snapshot of the latest `:kanvas-skia:test` run. Best-ever scores tracked in `test-similarity-scores.properties`. Triptych debug images (rendered ｜ diff ｜ reference) land in `build/debug-images/<gm-name>-comparison.png` when a test trips its threshold.

| Test | Similarity | Δ vs prev | Tolerance | Match / Total | Max diff (A,R,G,B) | Mean miss (A,R,G,B) |
|------|-----------:|----------:|----------:|--------------:|--------------------|---------------------|
| LcdTextSizeGM | 90.59% | (new) | 1 | 34,787 / 38,400 | 0, 255, 255, 255 | 0, 62, 60, 62 |
| LinearGradientGM | 93.83% | (new) | 1 | 234,584 / 250,000 | 0, 18, 4, 19 | 0, 11, 1, 12 |
| LinearGradientNoDitherGM | 93.84% | (new) | 1 | 234,605 / 250,000 | 0, 18, 4, 19 | 0, 11, 1, 12 |
| LinearGradientTinyGM | 97.33% | (new) | 1 | 292,000 / 300,000 | 0, 62, 42, 19 | 0, 39, 26, 12 |
| ManyPathAtlasesGM_128 | 95.06% | (new) | 1 | 15,574 / 16,384 | 0, 54, 12, 73 | 0, 15, 2, 21 |
| ManyPathAtlasesGM_2048 | 95.06% | (new) | 1 | 15,574 / 16,384 | 0, 54, 12, 73 | 0, 15, 2, 21 |
| ModeColorFilterGM | 18.94% | (new) | 1 | 99,288 / 524,288 | 0, 245, 253, 251 | 0, 35, 40, 36 |
| PathFillGM | 97.89% | (new) | 1 | 300,732 / 307,200 | 0, 67, 66, 66 | 0, 13, 13, 13 |
| PathInverseFillGM | 99.41% | (new) | 1 | 98,415 / 99,000 | 0, 255, 255, 255 | 0, 62, 61, 61 |
