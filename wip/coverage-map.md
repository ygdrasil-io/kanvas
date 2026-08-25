# Carte de couverture dérivée du code

Ce document est temporaire. Il est dérivé des API publiques actuelles et des types scellés dans le code Kotlin; il ne déclare pas une feature comme supportée. Il donne à chaque surface un lot propriétaire et un verdict/probe à produire avant la suppression de `wip/`.

## Légende

| Statut | Signification |
|---|---|
| `R` | Preuve catalogue actuelle rendue par `KanvasSurfaceProgram`. |
| `FS` | Refus actuel exercé par `KanvasSurfaceProgram`. |
| `FI` | Refus interne `RoutedSceneProgram`; diagnostic utile, pas une preuve Surface. |
| `N` | Aucune preuve catalogue actuelle; le lot doit ajouter un probe puis classifier rendu, refus stable ou dependency gate. |
| `DG` | Dependency gate (bloqué par une dépendance réelle), sans substitut temporaire. |

Les sources de vérité sont le code, les tests et les artefacts
générés/promus vérifiés, notamment [`Canvas.kt`](../kanvas/src/main/kotlin/org/graphiks/kanvas/canvas/Canvas.kt), les types scellés de `kanvas`, et le catalogue [`GpuEvidenceCatalog.kt`](../integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/catalog/GpuEvidenceCatalog.kt). Cette carte est une vue dérivée et ne fait pas autorité.

## Canvas public

| Surface source | Lot propriétaire unique | Statut actuel / probe requis |
|---|---:|---|
| `matrix`, `saveCount`, `localClipBounds`, `quickReject(RectF32)`, `quickReject(Path)`, `isClipEmpty`, `isClipRect` | 10 | `N` — probes de contrat d'état, clip et rejet. |
| `drawColor`, `clear`, `drawPoint`, `drawPoints`, `drawRect`, `drawRRect`, `drawDRRect` | 10 | `R` pour `drawRect` solide, le `drawRRect` solide uniforme non-AA sous scale `(2,1)`, le `drawRRect` solide asymétrique par coin, le `drawRRect` elliptique à rayons égaux aux demi-dimensions, le `drawDRRect` solide uniforme non-AA identité avec trou et le `drawDRRect` solide à trou asymétrique par coin; `N` pour les autres primitives et `clear`. |
| `save`, `saveLayer`, `restore`, `restoreToCount`, `flushAndSnapshot` | 10 | `N` — probes d'empilement, isolation de layer et snapshot. |
| `translate`, `scale`, `rotate`, `skew`, `concat`, `setMatrix`, `resetMatrix` | 10 | `R` pour la transform affine de rect et le seul scale `(2,1)` du `drawRRect` solide non-AA; `N` pour les autres combinaisons. |
| `clipRect` | 10 | `R` pour le scissor rectangulaire; `N` pour les autres opérations de clip. |
| `drawPath` | 20 | `R` uniquement pour six fills solides opaques non-AA prouvés par artefacts natifs : `solid-triangle-path`, `solid-concave-path`, `even-odd-path-hole`, `winding-path-hole`, `inverse-winding-triangle-path` et `inverse-even-odd-path-hole`. Les quatre fill types sont couverts seulement par ces formes littérales et leurs target bounds. Les courbes, strokes, implicit closure, oval/circle, AA, autres transforms et toute autre géométrie restent `N`/non revendiqués. |
| `clipRRect`, `clipPath` | 20 | `N` — aucune preuve catalogue publique `Surface` ; les clips et leurs variantes restent non revendiqués. |
| `drawImage`, `drawImageRect`, `drawImageNine`, `drawImageLattice`, `drawAtlas` | 40 | `N` — images, sampling, 9-patch, lattice et sprites. Le lot 40 est l'unique propriétaire de `drawAtlas`. |
| `drawString`, `drawText`, `measureText` | 60 | `DG` / `N` — probes texte et métriques; conserver un dependency gate si les fonts/codecs requis ne sont pas livrés. |
| `drawVertices`, `drawMesh`, `drawPicture` | 60 | `N` — probes vertices, mesh et picture. |
| `drawAnnotation` | 10 | `N` — probe de métadonnée sans effet pixel inattendu. |

## `Shader`

| Variante source | Lot propriétaire unique | Statut actuel / probe requis |
|---|---:|---|
| `SolidColor` | 30 | `R` — `solid-card-stack` et `affine-solid-rect`. |
| `LinearGradient`, `RadialGradient`, `SweepGradient` | 30 | `R` pour les formes `CLAMP` du catalogue et le `LinearGradient` `REPEAT` borné/non mask-filtered sur `drawRect`. Le probe est non-AA et identité sans conclure au refus AA. Le renderer refuse `REPEAT` sur rrect/path ou `drawRect` mask-filtered, radial/sweep `REPEAT`, ainsi que `MIRROR` et `DECAL`; `N` pour les autres variantes. |
| `ConicalGradient` | 30 | `N` — probe radial à deux points ou refus stable. |
| `Image` | 40 | `N` — owner image/sampling. |
| `Blend` | 30 | `N` — probe composition de shaders. |
| `RuntimeEffect` | 50 | `N` — voir la frontière built-in/custom du lot 50. |
| `WithLocalMatrix`, `WithColorFilter`, `WithWorkingColorSpace`, `CoordClamp` | 30 | `N` — probes des wrappers et de leurs diagnostics. |
| `PerlinNoise`, `FractalNoise` | 30 | `N` — probes déterministes ou refus stable. |

## `ColorFilter` et `Blender`

| Variante source | Lot propriétaire unique | Statut actuel / probe requis |
|---|---:|---|
| `ColorFilter.Matrix`, `Blend`, `Compose`, `Table`, `Lighting`, `SRGBToLinear`, `LinearToSRGB`, `HSLAMatrix`, `Lerp`, `HighContrast`, `Luma`, `Overdraw` | 30 | `N` — probes de pixels et de composition. |
| `ColorFilter.RuntimeEffect` | 50 | `N` — descriptif registered avec sémantique Kotlin/CPU + WGSL validé, ou refus stable. |
| `Blender.Mode`, `Blender.Arithmetic` | 30 | `N` — probes de blend et d'arithmetic. |

## `ImageFilter` et `MaskFilter`

| Variante source | Lot propriétaire unique | Statut actuel / probe requis |
|---|---:|---|
| `ImageFilter.Crop`, `Blur`, `DropShadow`, `Offset`, `Tile` | 40 | `N` — le blur actuel est un `MaskFilter.Blur`, pas un `ImageFilter.Blur`. |
| `ImageFilter.ColorFilter`, `Compose`, `Blend`, `Merge` | 40 | `N` — probes de composition. |
| `ImageFilter.Dilate`, `Erode`, `DisplacementMap`, `MatrixConvolution` | 40 | `N` — probes morphologie, displacement et convolution. |
| `ImageFilter.DistantLitDiffuse`, `PointLitDiffuse`, `SpotLitDiffuse`, `DistantLitSpecular`, `PointLitSpecular`, `SpotLitSpecular` | 40 | `N` — probes lighting ou refus stable. |
| `ImageFilter.Picture`, `Magnifier` | 40 | `N` — probes picture-filter et magnifier. |
| `ImageFilter.RuntimeEffect` | 50 | `N` — même contrat registered Kotlin/CPU + WGSL que les runtime effects. |
| `MaskFilter.Blur`, `Shader`, `Table` | 40 | `R` pour le `MaskFilter.Blur` normal du catalogue; `N` pour ses variantes et les autres mask filters. |

## `PathEffect`, sampling et mesh

| Variante source | Lot propriétaire unique | Statut actuel / probe requis |
|---|---:|---|
| `PathEffect.Dash`, `Corner`, `Discrete`, `Path1D`, `Path2D`, `Trim` | 20 | `N` — probe par effet ou refus stable. |
| `SamplingOptions.NEAREST`, `LINEAR`, `Cubic` | 40 | `N` — oracle image/sampling, y compris le comportement aux bords. |
| `MeshProgram`, `MeshChildren`, `ShaderChild`, `ColorFilterChild`, `BlenderChild` | 60 | `N` — probes mesh et children; `MeshProgram.effect` doit utiliser un descriptor registered avec sémantique Kotlin/CPU + WGSL, selon le lot 50. |

## Conditions de sortie

Une ligne `N` doit devenir une preuve `R`, un refus `FS` avec code stable, ou `DG` documenté par une dépendance réelle. Les `FI` peuvent compléter un diagnostic, mais ne satisfont jamais seuls une couverture de route publique `Surface`. Le dossier `wip/` ne peut être supprimé qu'après résolution de toutes les lignes ci-dessus et archivage des preuves dans `reports/gpu-renderer/evidence/`.
