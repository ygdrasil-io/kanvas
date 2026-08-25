# WIP 40 — Images, layers et image filters

> Document temporaire. Les codecs, fontes ou formats qui ne sont pas réellement
> livrés restent dependency-gated ; ce lot n'ajoute pas de substitut court terme.

## Objectif du groupe

Tester les routes qui consomment des textures ou créent des surfaces
intermédiaires : sampling image, grilles, sprites, `saveLayer` et image filters.
La qualité dépend autant des pixels que des bounds, allocations et durées de
vie des intermédiaires. Ce lot est l'unique propriétaire de `drawAtlas` : il
couvre le sampling, les instances et les artefacts image de cette API.

## Code et tests à lire

| Zone | Fichiers principaux |
| --- | --- |
| Images | `../gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/images/ImageContracts.kt`, `ImageUploadMaterializer.kt`, `GPUPreparedImageRefusalCodes.kt`, `KanvasImageCodecRegistry.kt` |
| Layers | `.../layers/LayerContracts.kt`, `FirstRouteDrawLayerPlanner.kt`, `GPUSaveLayerNativeExecutor.kt` |
| Filtres | `.../filters/GPUPreparedFilterDAGPlanner.kt`, `GPUFilterDAGExecutor.kt`, `GPUPreparedFilterRefusalCodes.kt`, `ColorMatrixFilter.kt`, `BlurFilter.kt` |
| Ressources | `.../resources/GPUScratchTexturePool.kt`, `GPUTextureFrameResourcePlan.kt`, `.../execution/GPUWgpu4kSurfaceBlitSessionCache.kt` |
| API | `../kanvas/src/main/kotlin/org/graphiks/kanvas/canvas/Canvas.kt` (images, atlas, `saveLayer`) |

## Matrice de scénarios

| Sous-famille | Scènes rendables à viser | Limites/refus à fixer |
| --- | --- | --- |
| `drawImage` | Image opaque/translucide, nearest/bilinear, source entière, alpha paint, translation/scale/rotation et bord hors surface. | Image nulle/vide, format ou codec absent, dimensions texture hors limite et sampling non pris en charge. |
| `drawImageRect` | Crop interne, crop à cheval, src/dst inversé, dst fractionnaire, couleur/filter paint et destination non opaque. | Src invalide, sampling/tile mode absent, transform/perspective rejeté avant upload coûteux. |
| `drawImageNine` / lattice | Coins fixes, centre étiré, cellules transparentes, lattice tronquée, bounds petites et grandes. | Découpage invalide, seams, grille trop grande et mode de sampling non routable. |
| `drawAtlas` | Plusieurs sprites, transforms indépendantes, tex rects, couleurs par sprite, blend et paint. | Longueurs de listes incohérentes, budget d'instances, texture absente et blend non admis. Ce lot possède les IDs de scène et les oracles atlas. |
| `saveLayer` | Bounds nulles/explicites, alpha, clip parent, layer vide, layers imbriquées et composite avec paint. | Bounds non finies, profondeur/allocation au-delà du budget et destination read indisponible. |
| Blur/filters | Blur sigma 0/borne, offset, crop, color matrix, affine, blend deux enfants, DAG 2–4 nœuds. | Tile mode, transform, DAG/cycle, taille d'intermédiaire, child absent et budget hors contrat. |

## Assertions spécifiques

Pour chaque scène, attester origine et sampling, clamping, alpha/premul,
bounds calculées, ordre des passes, nombre de textures intermédiaires, bytes,
ownership/release et absence de fuite après frame. Une couche ou un filtre
supporté doit exposer sa route native ; une route absente ne doit pas faire de
fallback CPU silencieux.

## Dépendances et sortie

Peut avancer après le lot 00 en parallèle avec 10, 30, 50 et les refus du lot
60. Les captures qui utilisent des images encodées ou des assets de texte sont
attendues jusqu'à ce que la dépendance réelle soit présente. La sortie associe
chaque render à un oracle/référence et chaque dépassement à un code de refus.

## Vérification

```bash
./gradlew :gpu-renderer:test
./gradlew :integration-tests:gpu-evidence:test --tests '*Image*' --tests '*Layer*' --tests '*Filter*'
./gradlew :integration-tests:gpu-evidence:test
```
