# kanvas-skia GM similarity report

Snapshot of the latest `:kanvas-skia:test` run. Best-ever scores tracked in `test-similarity-scores.properties`. Triptych debug images (rendered ｜ diff ｜ reference) land in `build/debug-images/<gm-name>-comparison.png` when a test trips its threshold.

| Test | Similarity | Δ vs prev | Tolerance | Match / Total | Max diff (A,R,G,B) | Mean miss (A,R,G,B) |
|------|-----------:|----------:|----------:|--------------:|--------------------|---------------------|
| DashCircle2GM | 87.99% | = | 1 | 502,885 / 571,500 | 0, 255, 255, 255 | 0, 107, 107, 107 |
| DiscardGM | 100.00% | = | 4 | 10,000 / 10,000 | 0, 0, 0, 0 | 0, 0, 0, 0 |
