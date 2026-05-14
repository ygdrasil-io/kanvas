# kanvas-skia GM similarity report

Snapshot of the latest `:kanvas-skia:test` run. Best-ever scores tracked in `test-similarity-scores.properties`. Triptych debug images (rendered ｜ diff ｜ reference) land in `build/debug-images/<gm-name>-comparison.png` when a test trips its threshold.

| Test | Similarity | Δ vs prev | Tolerance | Match / Total | Max diff (A,R,G,B) | Mean miss (A,R,G,B) |
|------|-----------:|----------:|----------:|--------------:|--------------------|---------------------|
| __TestToolingFixtureAlpha | 87.50% | +1.30% | - | - | - | - |
| __TestToolingFixtureBeta | 99.42% | +0.12% | 32 | 9,942 / 10,000 | 0, 12, 5, 7 | 0, 4, 2, 3 |
