# kanvas-skia GM similarity report

Snapshot of the latest `:kanvas-skia:test` run. Best-ever scores tracked in `test-similarity-scores.properties`. Triptych debug images (rendered ｜ diff ｜ reference) land in `build/debug-images/<gm-name>-comparison.png` when a test trips its threshold.

| Test | Similarity | Δ vs prev | Tolerance | Match / Total | Max diff (A,R,G,B) | Mean miss (A,R,G,B) |
|------|-----------:|----------:|----------:|--------------:|--------------------|---------------------|
| ImageBlurRepeatModeGM | 35.83% | (new) | 1 | 280,188 / 782,000 | 0, 198, 198, 198 | 0, 82, 96, 113 |
| ImageFiltersCropExpandGM | 71.17% | (new) | 1 | 337,720 / 474,500 | 0, 255, 255, 255 | 0, 127, 119, 128 |
| ImageFiltersCroppedGM | 86.66% | (new) | 8 | 332,779 / 384,000 | 0, 167, 233, 196 | 0, 73, 45, 80 |
| PathHugeAaGM | 99.83% | (new) | 1 | 39,932 / 40,000 | 0, 45, 45, 45 | 0, 14, 14, 14 |
| PathHugeAaManualGM | 99.83% | (new) | 1 | 39,932 / 40,000 | 0, 45, 45, 45 | 0, 14, 14, 14 |
