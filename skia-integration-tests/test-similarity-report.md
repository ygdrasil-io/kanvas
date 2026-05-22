# kanvas-skia GM similarity report

Snapshot of the latest `:kanvas-skia:test` run. Best-ever scores tracked in `test-similarity-scores.properties`. Triptych debug images (rendered ｜ diff ｜ reference) land in `build/debug-images/<gm-name>-comparison.png` when a test trips its threshold.

| Test | Similarity | Δ vs prev | Tolerance | Match / Total | Max diff (A,R,G,B) | Mean miss (A,R,G,B) |
|------|-----------:|----------:|----------:|--------------:|--------------------|---------------------|
| GradientsLocalPerspectiveGM | 72.78% | (new) | 8 | 498,265 / 684,600 | 0, 255, 255, 255 | 0, 57, 70, 61 |
| GradientsViewPerspectiveGM | 73.69% | (new) | 8 | 309,490 / 420,000 | 0, 255, 255, 255 | 0, 49, 51, 36 |
