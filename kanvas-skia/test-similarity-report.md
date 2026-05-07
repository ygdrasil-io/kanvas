# kanvas-skia GM similarity report

Snapshot of the latest `:kanvas-skia:test` run. Best-ever scores tracked in `test-similarity-scores.properties`. Triptych debug images (rendered ｜ diff ｜ reference) land in `build/debug-images/<gm-name>-comparison.png` when a test trips its threshold.

| Test | Similarity | Δ vs prev | Tolerance | Match / Total | Max diff (A,R,G,B) | Mean miss (A,R,G,B) |
|------|-----------:|----------:|----------:|--------------:|--------------------|---------------------|
| TextBlobBlockReorderingGM | 80.87% | = | 1 | 44,478 / 55,000 | 0, 202, 124, 124 | 0, 94, 75, 102 |
| TextBlobColorTransGM | 84.43% | = | 1 | 911,872 / 1,080,000 | 0, 131, 131, 177 | 0, 84, 101, 89 |
| TextBlobGM | 95.13% | = | 1 | 292,235 / 307,200 | 0, 212, 242, 180 | 0, 95, 107, 21 |
| TextBlobShaderGM | 88.39% | = | 1 | 271,526 / 307,200 | 0, 96, 184, 152 | 0, 25, 85, 38 |
