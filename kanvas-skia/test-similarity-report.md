# kanvas-skia GM similarity report

Snapshot of the latest `:kanvas-skia:test` run. Best-ever scores tracked in `test-similarity-scores.properties`. Triptych debug images (rendered ｜ diff ｜ reference) land in `build/debug-images/<gm-name>-comparison.png` when a test trips its threshold.

| Test | Similarity | Δ vs prev | Tolerance | Match / Total | Max diff (A,R,G,B) | Mean miss (A,R,G,B) |
|------|-----------:|----------:|----------:|--------------:|--------------------|---------------------|
| composeshader_bitmap | 53.83% | (new) | 16 | 4,710 / 8,750 | 0, 211, 240, 179 | 0, 59, 56, 24 |
| composeshader_bitmap_lm | 53.83% | (new) | 16 | 4,710 / 8,750 | 0, 211, 240, 179 | 0, 59, 56, 24 |
