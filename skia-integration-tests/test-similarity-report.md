# kanvas-skia GM similarity report

Snapshot of the latest `:kanvas-skia:test` run. Best-ever scores tracked in `test-similarity-scores.properties`. Triptych debug images (rendered ｜ diff ｜ reference) land in `build/debug-images/<gm-name>-comparison.png` when a test trips its threshold.

| Test | Similarity | Δ vs prev | Tolerance | Match / Total | Max diff (A,R,G,B) | Mean miss (A,R,G,B) |
|------|-----------:|----------:|----------:|--------------:|--------------------|---------------------|
| Strokes3GM | 90.41% | (new) | 1 | 2,034,300 / 2,250,000 | 0, 202, 214, 237 | 0, 62, 59, 31 |
| Strokes5GM | 99.48% | (new) | 1 | 318,344 / 320,000 | 0, 53, 196, 237 | 0, 8, 24, 26 |
| ZeroLenStrokesGM | 97.43% | (new) | 1 | 311,775 / 320,000 | 0, 255, 255, 255 | 0, 129, 129, 129 |
