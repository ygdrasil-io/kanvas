# kanvas-skia GM similarity report

Snapshot of the latest `:kanvas-skia:test` run. Best-ever scores tracked in `test-similarity-scores.properties`. Triptych debug images (rendered ｜ diff ｜ reference) land in `build/debug-images/<gm-name>-comparison.png` when a test trips its threshold.

| Test | Similarity | Δ vs prev | Tolerance | Match / Total | Max diff (A,R,G,B) | Mean miss (A,R,G,B) |
|------|-----------:|----------:|----------:|--------------:|--------------------|---------------------|
| GammaShaderTextGM | 82.95% | = | 8 | 74,652 / 90,000 | 0, 255, 255, 255 | 0, 92, 122, 87 |
| GradientsDegenrate2PointGM | 99.68% | = | 8 | 102,076 / 102,400 | 0, 63, 43, 20 | 0, 42, 28, 13 |
| GradientsLocalPerspectiveGM | 72.78% | = | 8 | 498,265 / 684,600 | 0, 255, 255, 255 | 0, 57, 70, 61 |
| GradientsManyColorsGM | 80.74% | = | 8 | 284,202 / 352,000 | 0, 255, 255, 255 | 0, 18, 17, 17 |
| GradientsViewPerspectiveGM | 73.69% | = | 8 | 309,490 / 420,000 | 0, 255, 255, 255 | 0, 49, 51, 36 |
| HSLColorFilterGM | 39.67% | = | 8 | 366,518 / 924,000 | 0, 252, 254, 250 | 0, 49, 55, 54 |
| ImageCacheratorGM | 62.87% | = | 8 | 271,584 / 432,000 | 0, 215, 245, 237 | 0, 179, 222, 61 |
| ImageGM | 98.14% | = | 8 | 1,130,615 / 1,152,000 | 0, 255, 255, 255 | 0, 69, 68, 66 |
| ImageMakeWithFilterGM | 84.35% | = | 8 | 1,334,815 / 1,582,400 | 0, 255, 255, 255 | 0, 82, 97, 96 |
| ImagePictGM | 97.27% | = | 8 | 372,053 / 382,500 | 0, 212, 242, 237 | 0, 40, 134, 163 |
| ImageShaderGM | 95.19% | = | 8 | 364,096 / 382,500 | 0, 214, 245, 237 | 0, 165, 207, 44 |
| LocalMatrixImageShaderGM | 100.00% | = | 1 | 62,500 / 62,500 | 0, 0, 0, 0 | 0, 0, 0, 0 |
