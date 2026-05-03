# kanvas-skia GM similarity report

Snapshot of the latest `:kanvas-skia:test` run. Best-ever scores tracked in `test-similarity-scores.properties`. Triptych debug images (rendered ｜ diff ｜ reference) land in `build/debug-images/<gm-name>-comparison.png` when a test trips its threshold.

| Test | Similarity | Δ vs prev | Tolerance | Match / Total | Max diff (A,R,G,B) | Mean miss (A,R,G,B) |
|------|-----------:|----------:|----------:|--------------:|--------------------|---------------------|
| BigRectGM | 99.83% | = | 160 | 40,555 / 40,625 | 0, 212, 242, 13 | 0, 212, 242, 13 |
| ClipStrokeRectGM | 100.00% | = | 160 | 80,000 / 80,000 | 0, 0, 0, 0 | 0, 0, 0, 0 |
| ConcavePathsGM | 100.00% | = | 160 | 300,000 / 300,000 | 0, 0, 0, 0 | 0, 0, 0, 0 |
| SimpleRectGM | 100.00% | = | 160 | 640,000 / 640,000 | 0, 0, 0, 0 | 0, 0, 0, 0 |
| ThinRectsGM | 100.00% | = | 160 | 76,800 / 76,800 | 0, 0, 0, 0 | 0, 0, 0, 0 |
