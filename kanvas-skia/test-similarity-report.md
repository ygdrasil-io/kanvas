# kanvas-skia GM similarity report

Snapshot of the latest `:kanvas-skia:test` run. Best-ever scores tracked in `test-similarity-scores.properties`. Triptych debug images (rendered ｜ diff ｜ reference) land in `build/debug-images/<gm-name>-comparison.png` when a test trips its threshold.

| Test | Similarity | Δ vs prev | Tolerance | Match / Total | Max diff (A,R,G,B) | Mean miss (A,R,G,B) |
|------|-----------:|----------:|----------:|--------------:|--------------------|---------------------|
| BlurTextSmallRadiiGM | 87.03% | = | 1 | 13,054 / 15,000 | 0, 76, 28, 84 | 0, 19, 6, 16 |
| Crbug1156804GM | 72.91% | = | 1 | 45,567 / 62,500 | 0, 25, 14, 28 | 0, 6, 3, 6 |
| Crbug905548GM | 49.61% | = | 1 | 9,922 / 20,000 | 0, 124, 124, 124 | 0, 12, 12, 12 |
