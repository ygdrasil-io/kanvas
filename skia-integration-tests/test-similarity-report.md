# kanvas-skia GM similarity report

Snapshot of the latest `:kanvas-skia:test` run. Best-ever scores tracked in `test-similarity-scores.properties`. Triptych debug images (rendered ｜ diff ｜ reference) land in `build/debug-images/<gm-name>-comparison.png` when a test trips its threshold.

| Test | Similarity | Δ vs prev | Tolerance | Match / Total | Max diff (A,R,G,B) | Mean miss (A,R,G,B) |
|------|-----------:|----------:|----------:|--------------:|--------------------|---------------------|
| Dashing3GM | 91.62% | (new) | 1 | 281,458 / 307,200 | 0, 255, 255, 255 | 0, 130, 130, 130 |
| Dashing4GM | 90.10% | (new) | 1 | 634,280 / 704,000 | 0, 255, 255, 255 | 0, 96, 96, 96 |
| Dashing5GMAA | 61.40% | (new) | 1 | 49,122 / 80,000 | 0, 212, 242, 212 | 0, 66, 77, 103 |
| Dashing5GMBW | 65.12% | (new) | 1 | 52,095 / 80,000 | 0, 212, 242, 237 | 0, 83, 103, 122 |
