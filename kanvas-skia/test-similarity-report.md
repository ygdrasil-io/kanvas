# kanvas-skia GM similarity report

Snapshot of the latest `:kanvas-skia:test` run. Best-ever scores tracked in `test-similarity-scores.properties`. Triptych debug images (rendered ｜ diff ｜ reference) land in `build/debug-images/<gm-name>-comparison.png` when a test trips its threshold.

| Test | Similarity | Δ vs prev | Tolerance | Match / Total | Max diff (A,R,G,B) | Mean miss (A,R,G,B) |
|------|-----------:|----------:|----------:|--------------:|--------------------|---------------------|
| BigRectGM | 95.53% | = | 1 | 38,811 / 40,625 | 0, 212, 242, 13 | 0, 31, 25, 3 |
| BitmapRectRoundingGM | 100.00% | = | 1 | 307,200 / 307,200 | 0, 0, 0, 0 | 0, 0, 0, 0 |
| ClipStrokeRectGM | 100.00% | = | 1 | 80,000 / 80,000 | 0, 0, 0, 0 | 0, 0, 0, 0 |
| ConcavePathsGM | 98.86% | -0.00% | 1 | 296,576 / 300,000 | 0, 65, 65, 65 | 0, 13, 13, 13 |
| ConvexPathsGM | 99.68% | = | 1 | 1,315,758 / 1,320,000 | 0, 121, 85, 227 | 0, 26, 20, 40 |
| Crbug788500GM | 99.93% | = | 1 | 89,941 / 90,000 | 0, 59, 59, 59 | 0, 17, 17, 17 |
| Crbug884166GM | 98.98% | = | 1 | 89,086 / 90,000 | 0, 44, 44, 44 | 0, 11, 11, 11 |
| Crbug887103GM | 99.82% | = | 1 | 269,921 / 270,400 | 0, 46, 46, 46 | 0, 16, 16, 16 |
| Crbug908646GM | 99.56% | = | 1 | 89,604 / 90,000 | 0, 30, 30, 30 | 0, 12, 12, 12 |
| Crbug913349GM | 99.76% | = | 1 | 299,277 / 300,000 | 0, 41, 41, 41 | 0, 14, 14, 14 |
| DrawBitmapRect3 | 100.00% | = | 1 | 307,200 / 307,200 | 0, 0, 0, 0 | 0, 0, 0, 0 |
| SimpleRectGM | 100.00% | = | 1 | 640,000 / 640,000 | 0, 0, 0, 0 | 0, 0, 0, 0 |
| ThinRectsGM | 92.10% | = | 1 | 70,736 / 76,800 | 0, 18, 17, 17 | 0, 12, 13, 11 |
