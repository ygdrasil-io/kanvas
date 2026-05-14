# kanvas-skia GM similarity report

Snapshot of the latest `:kanvas-skia:test` run. Best-ever scores tracked in `test-similarity-scores.properties`. Triptych debug images (rendered ｜ diff ｜ reference) land in `build/debug-images/<gm-name>-comparison.png` when a test trips its threshold.

| Test | Similarity | Δ vs prev | Tolerance | Match / Total | Max diff (A,R,G,B) | Mean miss (A,R,G,B) |
|------|-----------:|----------:|----------:|--------------:|--------------------|---------------------|
| GiantBitmapClampBilerpRotate | 87.34% | (new) | 4 | 268,294 / 307,200 | 0, 255, 255, 255 | 0, 153, 174, 165 |
| GiantBitmapClampBilerpScale | 88.13% | (new) | 4 | 270,720 / 307,200 | 0, 255, 255, 255 | 0, 154, 171, 169 |
| GiantBitmapClampPointRotate | 87.94% | (new) | 4 | 270,161 / 307,200 | 0, 255, 255, 255 | 0, 158, 181, 172 |
| GiantBitmapClampPointScale | 88.13% | (new) | 4 | 270,720 / 307,200 | 0, 255, 255, 255 | 0, 157, 175, 173 |
| GiantBitmapMirrorBilerpRotate | 67.33% | (new) | 4 | 206,847 / 307,200 | 0, 255, 255, 255 | 0, 152, 168, 166 |
| GiantBitmapMirrorBilerpScale | 67.34% | (new) | 4 | 206,880 / 307,200 | 0, 255, 255, 255 | 0, 154, 184, 145 |
| GiantBitmapMirrorPointRotate | 68.89% | (new) | 4 | 211,621 / 307,200 | 0, 255, 255, 255 | 0, 157, 175, 173 |
| GiantBitmapMirrorPointScale | 67.97% | (new) | 4 | 208,800 / 307,200 | 0, 255, 255, 255 | 0, 157, 189, 149 |
| GiantBitmapRepeatBilerpRotate | 67.33% | (new) | 4 | 206,847 / 307,200 | 0, 255, 255, 255 | 0, 152, 168, 166 |
| GiantBitmapRepeatBilerpScale | 66.72% | (new) | 4 | 204,960 / 307,200 | 0, 255, 255, 255 | 0, 142, 160, 158 |
| GiantBitmapRepeatPointRotate | 68.89% | (new) | 4 | 211,621 / 307,200 | 0, 255, 255, 255 | 0, 157, 175, 173 |
| GiantBitmapRepeatPointScale | 68.13% | (new) | 4 | 209,280 / 307,200 | 0, 255, 255, 255 | 0, 148, 169, 165 |
| GradientsNoTextureGM | 81.13% | = | 1 | 319,345 / 393,600 | 0, 63, 43, 46 | 0, 21, 14, 12 |
| ImageFiltersBaseGM | 85.74% | = | 8 | 300,101 / 350,000 | 0, 217, 242, 239 | 0, 50, 72, 45 |
| ImageFiltersStrokedGM | 91.44% | +91.03% | 4 | 393,178 / 430,000 | 0, 255, 255, 255 | 0, 133, 145, 125 |
| MandolineGM | 91.83% | = | 1 | 244,260 / 266,000 | 0, 255, 255, 255 | 0, 61, 61, 61 |
| MipmapGM | 92.09% | (new) | 4 | 73,673 / 80,000 | 0, 179, 179, 179 | 0, 25, 25, 25 |
| MipmapGray8SrgbGM | 99.99% | (new) | 4 | 59,794 / 59,800 | 0, 6, 6, 6 | 0, 5, 5, 5 |
| MipmapSrgbGM | 89.05% | (new) | 4 | 53,254 / 59,800 | 0, 141, 141, 141 | 0, 15, 15, 15 |
| NearestHalfPixelImageGM | 78.54% | (new) | 4 | 48,728 / 62,040 | 0, 255, 255, 255 | 0, 101, 101, 106 |
| OverStrokeGM | 83.48% | = | 1 | 208,710 / 250,000 | 0, 255, 255, 255 | 0, 98, 91, 102 |
