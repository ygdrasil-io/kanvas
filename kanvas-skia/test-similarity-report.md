# kanvas-skia GM similarity report

Snapshot of the latest `:kanvas-skia:test` run. Best-ever scores tracked in `test-similarity-scores.properties`. Triptych debug images (rendered ｜ diff ｜ reference) land in `build/debug-images/<gm-name>-comparison.png` when a test trips its threshold.

| Test | Similarity | Δ vs prev | Tolerance | Match / Total | Max diff (A,R,G,B) | Mean miss (A,R,G,B) |
|------|-----------:|----------:|----------:|--------------:|--------------------|---------------------|
| DegenerateGradientGM | 61.30% | (new) | 1 | 392,344 / 640,000 | 0, 255, 255, 255 | 0, 123, 132, 159 |
| GradientDirtyLaundryGM | 93.14% | (new) | 1 | 366,600 / 393,600 | 0, 63, 43, 46 | 0, 19, 14, 11 |
| GradientMatrixGM | 66.04% | (new) | 1 | 422,664 / 640,000 | 0, 13, 30, 12 | 0, 9, 20, 8 |
| ImageBlur2GM | 85.69% | (new) | 8 | 214,221 / 250,000 | 0, 163, 121, 192 | 0, 36, 26, 42 |
| ImageBlurClampModeGM | 87.52% | (new) | 1 | 684,382 / 782,000 | 0, 30, 48, 44 | 0, 1, 2, 1 |
| ImageBlurRepeatModeGM | 35.83% | (new) | 1 | 280,188 / 782,000 | 0, 198, 198, 198 | 0, 82, 96, 113 |
| ImageFiltersCropExpandGM | 71.17% | (new) | 1 | 337,720 / 474,500 | 0, 255, 255, 255 | 0, 127, 119, 128 |
| ImageFiltersCroppedGM | 86.66% | (new) | 8 | 332,779 / 384,000 | 0, 167, 233, 196 | 0, 73, 45, 80 |
| PathHugeAaGM | 99.83% | (new) | 1 | 39,932 / 40,000 | 0, 45, 45, 45 | 0, 14, 14, 14 |
| PathHugeAaManualGM | 99.83% | (new) | 1 | 39,932 / 40,000 | 0, 45, 45, 45 | 0, 14, 14, 14 |
