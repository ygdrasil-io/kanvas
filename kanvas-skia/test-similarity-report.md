# kanvas-skia GM similarity report

Snapshot of the latest `:kanvas-skia:test` run. Best-ever scores tracked in `test-similarity-scores.properties`. Triptych debug images (rendered ｜ diff ｜ reference) land in `build/debug-images/<gm-name>-comparison.png` when a test trips its threshold.

| Test | Similarity | Δ vs prev | Tolerance | Match / Total | Max diff (A,R,G,B) | Mean miss (A,R,G,B) |
|------|-----------:|----------:|----------:|--------------:|--------------------|---------------------|
| pictureshader | 32.61% | (new) | 8 | 661,942 / 2,030,000 | 0, 148, 190, 241 | 0, 80, 38, 116 |
| pictureshader_alpha | 31.56% | (new) | 8 | 640,574 / 2,030,000 | 0, 86, 94, 110 | 0, 67, 51, 76 |
| pictureshader_localwrapper | 32.61% | (new) | 8 | 661,942 / 2,030,000 | 0, 148, 190, 241 | 0, 80, 38, 116 |
