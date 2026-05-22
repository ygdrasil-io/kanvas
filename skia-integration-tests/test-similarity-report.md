# kanvas-skia GM similarity report

Snapshot of the latest `:kanvas-skia:test` run. Best-ever scores tracked in `test-similarity-scores.properties`. Triptych debug images (rendered ｜ diff ｜ reference) land in `build/debug-images/<gm-name>-comparison.png` when a test trips its threshold.

| Test | Similarity | Δ vs prev | Tolerance | Match / Total | Max diff (A,R,G,B) | Mean miss (A,R,G,B) |
|------|-----------:|----------:|----------:|--------------:|--------------------|---------------------|
| HSLColorFilterGM | 39.67% | (new) | 8 | 366,518 / 924,000 | 0, 252, 254, 250 | 0, 49, 55, 54 |
| ImageCacheratorGM | 62.87% | (new) | 8 | 271,584 / 432,000 | 0, 215, 245, 237 | 0, 179, 222, 61 |
| ImageMakeWithFilterGM | 84.35% | (new) | 8 | 1,334,815 / 1,582,400 | 0, 255, 255, 255 | 0, 82, 97, 96 |
| ImagePictGM | 97.27% | (new) | 8 | 372,053 / 382,500 | 0, 212, 242, 237 | 0, 40, 134, 163 |
