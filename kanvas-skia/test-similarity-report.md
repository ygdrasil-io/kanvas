# kanvas-skia GM similarity report

Snapshot of the latest `:kanvas-skia:test` run. Best-ever scores tracked in `test-similarity-scores.properties`. Triptych debug images (rendered ｜ diff ｜ reference) land in `build/debug-images/<gm-name>-comparison.png` when a test trips its threshold.

| Test | Similarity | Δ vs prev | Tolerance | Match / Total | Max diff (A,R,G,B) | Mean miss (A,R,G,B) |
|------|-----------:|----------:|----------:|--------------:|--------------------|---------------------|
| BicubicGM | 88.51% | = | 8 | 84,968 / 96,000 | 0, 30, 30, 30 | 0, 14, 14, 14 |
| BitmapPremulGM | 100.00% | = | 2 | 262,144 / 262,144 | 0, 0, 0, 0 | 0, 0, 0, 0 |
| ColorFilterAlpha8GM | 100.00% | = | 2 | 160,000 / 160,000 | 0, 0, 0, 0 | 0, 0, 0, 0 |
