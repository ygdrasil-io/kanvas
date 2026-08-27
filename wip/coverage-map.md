# Carte de couverture dérivée du code

Ce document affecte un owner de vague à chaque surface publique. Il ne porte
pas le statut réel : le statut est calculé depuis le code, les tests exécutés,
les diagnostics et les artefacts vérifiés produits par `W00`/`W01`.

## Verdicts autorisés

| Verdict | Condition |
| --- | --- |
| `SUPPORTED` | Preuve publique CPU/GPU promue et fallback policy vérifiée. |
| `STABLE_REFUSAL` | Diagnostic public stable avant exécution partielle. |
| `DEPENDENCY_GATED` | Dépendance réelle absente, sans substitut. |
| `OUT_OF_SCOPE` | Décision architecturale explicite. |

## Canvas et état

| Surface publique | Owner |
| --- | --- |
| `matrix`, `saveCount`, `localClipBounds`, `quickReject`, `isClipEmpty`, `isClipRect` | `W10` |
| `save`, `restore`, `restoreToCount` | `W10` |
| `translate`, `scale`, `rotate`, `skew`, `concat`, `setMatrix`, `resetMatrix` | `W11` |
| `drawColor`, `clear`, `drawPoint`, `drawPoints`, `drawRect`, `drawRRect`, `drawDRRect` | `W12` |
| `PointMode.POINTS`, `LINES`, `POLYGON` | `W12` |
| `drawAnnotation`, `flushAndSnapshot` | `W12` |
| `drawPath` fills/curves/topology | `W20`, `W21`, `W22` |
| `clipRect`, `clipRRect`, `clipPath` | `W10`, `W23`, `W24` |
| Stroke de rect/RRect/path | `W25` |
| `saveLayer` | `W44` |
| `drawImage`, `drawImageRect` | `W40`, `W41` |
| `drawImageNine`, `drawImageLattice`, `drawAtlas` | `W43` |
| `LatticeFlags.DEFAULT`, `TRANSPARENT`, `FIXED_COLOR` | `W43` |
| `drawVertices`, `drawMesh`, `drawPicture` | `W60`, `W61`, `W62` |
| `VertexMode.TRIANGLES`, `TRIANGLE_STRIP`, `TRIANGLE_FAN` | `W60` |
| `drawString`, `drawText`, `measureText` | `W63`, `W64`, `W65` |

## Paint et shaders

| Variante publique | Owner |
| --- | --- |
| `Shader.SolidColor` | `W12`, `W33` |
| `LinearGradient`, `RadialGradient`, `SweepGradient`, `ConicalGradient` | `W30`, `W31`, `W32` |
| `ColorSpaceInterpolation.SRGB`, `LINEAR`, `OKLAB`, `HSL`, `OKLCH` | `W31`, `W32` |
| `TileMode.CLAMP`, `REPEAT`, `MIRROR`, `DECAL` | `W30`, `W42`, `W45` |
| `Shader.Image` | `W40`, `W42` |
| `Shader.Blend` | `W33`, `W35` |
| `Shader.RuntimeEffect` | `W50`, `W53` |
| `WithLocalMatrix`, `WithColorFilter`, `WithWorkingColorSpace`, `CoordClamp` | `W35` |
| `PerlinNoise`, `FractalNoise` | `W35` |
| `Paint.style`, strokeWidth/cap/join/miter, `antiAlias` | `W22`, `W25` |
| `Paint.blendMode`, `Paint.blender` | `W33` |
| `Paint.colorFilter` | `W34` |
| `Paint.maskFilter`, `Paint.imageFilter` | `W45` à `W48` |
| `Paint.pathEffect` | `W26` |

## ColorFilter, Blender et PathEffect

| Variantes publiques | Owner |
| --- | --- |
| Matrix, Blend, Compose, Table, Lighting | `W34` |
| SRGBToLinear, LinearToSRGB, HSLAMatrix, Lerp | `W34` |
| HighContrast, Luma, Overdraw | `W34` |
| `ColorFilter.RuntimeEffect` | `W50`, `W53` |
| `Blender.Mode`, `Blender.Arithmetic` | `W33` |
| Dash, Corner, Discrete, Path1D, Path2D, Trim | `W26` |
| `Path1DStyle.TRANSLATE`, `ROTATE`, `MORPH` | `W26` |

## Images, sampling et filtres

| Variantes publiques | Owner |
| --- | --- |
| NEAREST, LINEAR, Cubic | `W40` |
| `Cubic.Mitchell`, `Cubic.CatmullRom` et coefficients B/C explicites | `W40` |
| Formats/upload/color conversion | `W41` |
| Image shader/tile/local matrix | `W42` |
| Nine, Lattice, Atlas | `W43` |
| Crop, Blur, DropShadow, Offset, Tile, ColorFilter | `W45` |
| Compose, Blend, Merge | `W46` |
| Dilate, Erode, DisplacementMap, MatrixConvolution | `W47` |
| `ColorChannel.R`, `G`, `B`, `A` | `W47` |
| Picture, Magnifier | `W47` |
| `DistantLitDiffuse`, `PointLitDiffuse`, `SpotLitDiffuse` | `W47` |
| `DistantLitSpecular`, `PointLitSpecular`, `SpotLitSpecular` | `W47` |
| `ImageFilter.RuntimeEffect` | `W50`, `W53` |
| `MaskFilter.Blur`, Shader, Table | `W48` |
| `BlurStyle.NORMAL`, `SOLID`, `OUTER`, `INNER` | `W48` |

## Runtime, mesh et ressources

| Surface publique | Owner |
| --- | --- |
| Runtime descriptors et uniforms | `W50` |
| Shader/ColorFilter/Blender children | `W51` |
| WGSL ABI/reflection/layout/cache keys | `W52` |
| Runtime shader/filter/blender/image-filter boundaries | `W53` |
| Vertices et indices | `W60` |
| MeshProgram, MeshChildren et interpolation | `W61` |
| Picture record/replay | `W62` |
| Device, queue, textures, buffers, readback et dispose | `W70` |
| Caches, budgets et déterminisme | `W71` |

## Dependency gates et hors scope

| Surface | Verdict attendu tant que la décision ne change pas | Owner |
| --- | --- | --- |
| Shaping, fallback, variable/color fonts, emoji | `DEPENDENCY_GATED` | `W64` |
| Codecs non livrés | `DEPENDENCY_GATED` | `W41` |
| Perspective générale | `OUT_OF_SCOPE` | `W11` |
| SkSL dynamique | `OUT_OF_SCOPE` | `W53` |
| Windowing natif | `OUT_OF_SCOPE` | `W70` |

## Condition de sortie

`W75` compare cette liste aux types publics réellement présents. Toute variante
ajoutée au code sans owner ou tout verdict `UNCLASSIFIED` fait échouer la
fermeture. Le dossier `wip/` est supprimé lorsque ces contrôles sont absorbés
par des tests et rapports générés.
