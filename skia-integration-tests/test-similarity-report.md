# kanvas-skia GM similarity report

Snapshot of the latest `:kanvas-skia:test` run. Best-ever scores tracked in `test-similarity-scores.properties`. Triptych debug images (rendered ｜ diff ｜ reference) land in `build/debug-images/<gm-name>-comparison.png` when a test trips its threshold.

| Test | Similarity | Δ vs prev | Tolerance | Match / Total | Max diff (A,R,G,B) | Mean miss (A,R,G,B) |
|------|-----------:|----------:|----------:|--------------:|--------------------|---------------------|
| AaRectModesGM | 80.28% | = | 1 | 246,629 / 307,200 | 0, 209, 230, 30 | 0, 25, 21, 8 |
| AllBitmapConfigsGM | 57.02% | -0.19% | 8 | 56,057 / 98,304 | 0, 255, 255, 255 | 0, 80, 84, 82 |
| AllVariants8888GM | 64.35% | = | 8 | 92,776 / 144,172 | 0, 212, 242, 237 | 0, 106, 190, 118 |
| AlphaImageAlphaTintGM | 43.68% | = | 8 | 5,312 / 12,160 | 0, 54, 119, 22 | 0, 20, 36, 9 |
| AlphaImageGM | 34.09% | = | 8 | 22,338 / 65,536 | 0, 155, 247, 253 | 0, 62, 71, 84 |
| AlternateLumaGM | 4.44% | = | 8 | 2,182 / 49,152 | 0, 210, 219, 215 | 0, 60, 62, 62 |
| AnimatedBackdropBlurGM | 97.21% | -0.08% | 8 | 509,673 / 524,288 | 0, 255, 255, 255 | 0, 31, 35, 35 |
| AnimatedGifGM | 100.00% | = | 4 | 2,452,480 / 2,452,480 | 0, 0, 0, 0 | 0, 0, 0, 0 |
| AnimatedImageBlursGM | 92.41% | = | 8 | 242,244 / 262,144 | 0, 56, 56, 56 | 0, 12, 12, 12 |
| AnimatedTiledImageBlurGM | 17.26% | = | 8 | 48,487 / 280,900 | 0, 71, 52, 64 | 0, 15, 10, 16 |
| AnisoMipsGM | 98.04% | = | 8 | 132,556 / 135,200 | 0, 107, 119, 123 | 0, 28, 24, 26 |
| AnisotropicImageScaleAnisoGM | 54.64% | = | 8 | 396,860 / 726,280 | 0, 255, 255, 255 | 0, 86, 86, 86 |
| AnisotropicImageScaleLinearGM | 57.02% | = | 6 | 414,158 / 726,280 | 0, 255, 255, 255 | 0, 95, 95, 95 |
| AnisotropicImageScaleMipGM | 55.29% | = | 8 | 401,593 / 726,280 | 0, 255, 255, 255 | 0, 78, 78, 78 |
| AnnotatedTextGM | 99.90% | -0.05% | 8 | 261,877 / 262,144 | 0, 148, 158, 174 | 0, 121, 122, 124 |
| B119394958GM | 90.55% | = | 1 | 9,055 / 10,000 | 0, 159, 80, 224 | 0, 16, 15, 16 |
| BackdropHintrectClippingGM | 64.03% | = | 1 | 335,714 / 524,288 | 0, 121, 159, 156 | 0, 21, 21, 21 |
| BackdropImagefilterCroprectGM | 96.00% | = | 1 | 288,000 / 300,000 | 0, 64, 180, 175 | 0, 32, 91, 88 |
| BackdropImagefilterCroprectNestedGM | 78.00% | = | 1 | 234,000 / 300,000 | 0, 24, 119, 84 | 0, 13, 34, 10 |
| BackdropImagefilterCroprectPerspGM | 91.71% | = | 1 | 275,137 / 300,000 | 0, 113, 190, 226 | 0, 50, 55, 50 |
| BackdropImagefilterCroprectRotatedGM | 62.37% | = | 1 | 187,101 / 300,000 | 0, 101, 190, 226 | 0, 50, 126, 21 |
| BackdropLayerTilemodeGM | 0.00% | = | 1 | 0 / 65,536 | 0, 41, 137, 152 | 0, 13, 35, 36 |
| BackdropScalefactorGM | 61.72% | = | 1 | 485,394 / 786,432 | 0, 147, 160, 159 | 0, 19, 17, 17 |
| BadAppleGM | 96.71% | = | 8 | 253,509 / 262,144 | 0, 214, 214, 214 | 0, 59, 59, 59 |
| BatchedConvexPathsGM | 34.94% | = | 1 | 91,605 / 262,144 | 0, 21, 24, 25 | 0, 7, 6, 7 |
| BeziersGM | 94.91% | = | 1 | 303,717 / 320,000 | 0, 97, 151, 223 | 0, 9, 10, 8 |
| BicubicGM | 88.51% | = | 8 | 84,968 / 96,000 | 0, 30, 30, 30 | 0, 14, 14, 14 |
| BigRectGM | 95.53% | = | 1 | 38,811 / 40,625 | 0, 212, 242, 13 | 0, 31, 25, 4 |
| BigTextCrbug1370488GM | 33.36% | = | 8 | 87,440 / 262,144 | 0, 255, 255, 255 | 0, 254, 254, 254 |
| BigTextGM | 98.20% | = | 8 | 301,684 / 307,200 | 0, 215, 244, 237 | 0, 118, 122, 102 |
| BigTileImageFilterGM | 86.30% | = | 8 | 226,240 / 262,144 | 0, 202, 245, 68 | 0, 60, 61, 18 |
| BitmapCopyGM | 99.78% | -0.05% | 8 | 177,808 / 178,200 | 0, 114, 114, 114 | 0, 40, 40, 40 |
| BitmapFiltersGM | 60.66% | -0.06% | 8 | 81,890 / 135,000 | 0, 210, 241, 234 | 0, 40, 41, 45 |
| BitmapImageGM | 99.52% | -0.48% | 8 | 1,043,568 / 1,048,576 | 0, 25, 10, 32 | 0, 5, 2, 10 |
| BitmapPremulGM | 100.00% | = | 2 | 262,144 / 262,144 | 0, 0, 0, 0 | 0, 0, 0, 0 |
| BitmapRectRoundingGM | 100.00% | = | 1 | 307,200 / 307,200 | 0, 0, 0, 0 | 0, 0, 0, 0 |
| BitmapSubsetShaderGM | 97.44% | = | 1 | 63,860 / 65,536 | 0, 60, 98, 180 | 0, 2, 2, 3 |
| BleedDownscaleGM | 88.43% | = | 4 | 76,400 / 86,400 | 0, 4, 3, 59 | 0, 4, 3, 59 |
| BlobRSXformDistortableGM | 63.50% | = | 1 | 31,750 / 50,000 | 0, 198, 198, 198 | 0, 62, 62, 62 |
| BlobRSXformGM | 76.51% | = | 1 | 38,256 / 50,000 | 0, 198, 198, 198 | 0, 57, 57, 57 |
| FlightAnimatedImageGM | 82.92% | = | 4 | 8,576,565 / 10,342,656 | 0, 242, 242, 242 | 0, 70, 70, 70 |
| StoplightAnimatedImageGM | 87.07% | = | 4 | 80,464 / 92,416 | 0, 255, 255, 255 | 0, 198, 198, 212 |
