# kanvas-skia GM similarity report

Snapshot of the latest `:kanvas-skia:test` run. Best-ever scores tracked in `test-similarity-scores.properties`. Triptych debug images (rendered ｜ diff ｜ reference) land in `build/debug-images/<gm-name>-comparison.png` when a test trips its threshold.

| Test | Similarity | Δ vs prev | Tolerance | Match / Total | Max diff (A,R,G,B) | Mean miss (A,R,G,B) |
|------|-----------:|----------:|----------:|--------------:|--------------------|---------------------|
| BigRectGM | 95.53% | = | 1 | 38,811 / 40,625 | 0, 212, 242, 13 | 0, 31, 25, 3 |
| ClipStrokeRectGM | 100.00% | = | 1 | 80,000 / 80,000 | 0, 0, 0, 0 | 0, 0, 0, 0 |
| ConcavePathsGM | 98.86% | = | 1 | 296,580 / 300,000 | 0, 67, 67, 67 | 0, 13, 13, 13 |
| DrawBitmapRect3 | 100.00% | = | 1 | 307,200 / 307,200 | 0, 0, 0, 0 | 0, 0, 0, 0 |
| SimpleRectGM | 100.00% | = | 1 | 640,000 / 640,000 | 0, 0, 0, 0 | 0, 0, 0, 0 |
| ThinRectsGM | 92.10% | = | 1 | 70,736 / 76,800 | 0, 18, 17, 17 | 0, 12, 13, 11 |
