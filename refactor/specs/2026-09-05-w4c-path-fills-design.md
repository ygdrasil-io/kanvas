# W4c — fills de paths par tessellation directe et stencil/cover

**Statut :** validé  
**Date :** 2026-09-05  
**Branche de base :** `codex/w4c-path-fills`, empilée sur W4b  
**Références :** `refactor/specs/2026-08-29-skia-renderer-remediation-design.md`, `refactor/specs/2026-09-03-w4-geometry-coverage-stack-design.md`, `refactor/specs/2026-09-04-w4b-analytic-rrect-design.md`

## 1. Décision

W4c ajoute la capability préparée :

```text
solid-path-fill-tessellation-stencil-hard-1x-simple-scissor-src-over-srgb-v1
```

Elle rend une frame de 1 à 512 `GeometryNode.Path` de provenance `DrawOrigin.PATH`, en `SolidColor`, `PaintStyleNode.FILL`, `CoverageRequest.HARD_EDGE`, `SrcOver`, sRGB 1×, avec clip vide ou scissor non-AA intégral `I32`.

Le planner choisit et scelle par draw :

- `DirectTriangle` : triangle strict, `WINDING`, non-inverse ;
- `StencilCover` : tout autre fill admis, non-inverse, `WINDING` ou `EVEN_ODD`.

Une frame W4c est atomique : elle est entièrement rendue par cette capability ou reste legacy avant promotion. Après `Ready`, le renderer ne reclassifie ni la géométrie, ni le fill rule, ni la stratégie, et ne tente aucun fallback.

## 2. Admission et exclusions

Une frame est candidate si scène/cible ont le même extent sRGB non vide, si tous ses 1–512 draws visuels sont `GeometryNode.Path`/`DrawOrigin.PATH`, et si chaque draw respecte simultanément :

1. `PathF32` fini, non-inverse, de fill rule `WINDING` ou `EVEN_ODD`, dont le fill device-space est non dégénéré ;
2. material solid fini, `FILL` hard-edge et `SrcOver`, sans shader, blender, filtre, effect, resource ou operation blend ;
3. transform identité ou scale/translate axis-aligned fini, y compris une échelle négative ;
4. clip vide ou `ClipStackNode.DeviceRect` non-AA, non vide, aux bords `I32` ;
5. bounds, scissor, limites géométriques, capacités et budget calculables par arithmetic checked.

`SetTransform`, `SetClip` et `Annotation` finies peuvent être traversées comme métadonnées déjà capturées. `TEXT_EXPANDED_PATH` reste exclu : font est hors périmètre.

Sont hors W4c : strokes/hairlines, `ANTIALIASED`, inverse fills, rotation/skew/perspective, clips complexes ou AA, MSAA, target non-sRGB, images, gradients, shaders, filtres, blenders et blends non-`SrcOver`.

Les inverse fills restent W4e avec clips path/inverses/booléens. L'AA path est une capability distincte : `StencilAA` 4× ne doit jamais être obtenu en rendant une demande `ANTIALIASED` par la voie hard-edge 1×.

## 3. Géométrie backend-neutral dans `:math`

`:math:geometry` possède toute géométrie W4c ; `:math:matrix` possède les transforms. `:gpu-plan` et `:gpu-renderer` ne définissent aucun point, contour, triangle, fan, bounds ou tolérance géométrique.

Les valeurs ajoutées sont `PathFillLimitsI32`, `PathFillFlatteningPolicyF64`, `PathFillGeometryF32`, `PathFillDirectTriangleF32` et `PathStencilEdgeFanF32`. `PathFillLimitsI32` fixe 65 536 arêtes aplaties au plus par path et 262 144 au plus par frame. Les coordonnées émises sont `F32`, les calculs de flattening/validation sont `F64`, les index, limites et compteurs portent `I32` ou `I64`. Ces types ne dépendent ni de WebGPU, WGSL, pipeline, bind group ni handle natif.

La préparation est déterministe et précède toute allocation native :

1. copier le `PathF32` immutable de la Scene IR ;
2. appliquer le transform admissible dans `:math:matrix` ;
3. fermer implicitement les contours ouverts selon la sémantique publique de fill ;
4. aplatir quad, cubic et arc en F64 à une erreur de flèche device-space `<= 0,25 px` ;
5. vérifier finitude, fermeture, bounds et `PathFillLimitsI32` avec arithmetic checked ;
6. émettre un snapshot `F32` profondément immutable, son scissor conservateur et ses coûts vertex/index exacts.

Convergence impossible, limite ou overflow ne publient pas de contour partiel. Après reconnaissance de la famille, ils donnent un résultat typé de limite sans fallback ; le non-fini est `InvalidScene` avant device. Les deux frontières `PathFillLimitsI32` sont testées sans dépendre d'un GM ou d'une fixture.

`DirectTriangle` exige après préparation un seul contour fermé, exactement trois sommets distincts finis et non collinéaires, aucune courbe/arc/retrace/auto-intersection, `WINDING` non-inverse, bounds et scissor non vides. Sa preuve est calculée dans `:math` et transportée par le plan. Triangles `EVEN_ODD`, concaves, multi-contours, trous, courbes, arcs et paths de plus de trois côtés utilisent `StencilCover`.

## 4. Plan, ordre et `RenderGraph`

`PathFillDraw` est une donnée immutable du plan : `commandIndex`, couleur linear-premultiplied, `PathFillGeometryF32`, stratégie, scissor `RectI32`, `HardEdge`, `SingleSample` et `SrcOver`.

Un triangle direct devient une passe colorée. Un draw stencil forme exactement le groupe atomique suivant, adjacent dans l'ordre de paint :

```text
StencilProducer(command i, clear stencil = 0)
    -> StencilCover(command i, stencil read-only, SrcOver)
```

Le producer applique winding/even-odd sans écrire la couleur ; le cover applique le quad borné, la couleur et le scissor. La première passe colorée clear la target transparente et les suivantes la load/store : la destination est donc stockée/quantifiée sRGB entre draws.

Les 1–512 draws ne sont admis que si le lowering prouve l'ordre total et l'atomicité de chaque paire. À défaut, la frame entière est explicitement refusée au stade pertinent. Il est interdit de réduire silencieusement la capability à une path/frame, de rendre seulement le premier path ou de reclasser le reste.

W4c étend `RenderGraph` par une texture depth/stencil, le rôle `DepthStencil`, l'usage `DepthStencilAttachment`, les capabilities depth/stencil et stencil/cover, et les passes typées `StencilProducer`/`StencilCover` avec accès writable/read-only et groupe atomique scellé.

La texture porte `Depth24PlusStencil8`, le même extent que la target, un seul sample et l'usage depth/stencil. Elle n'est ni une `PlanLogicalColorFormat` ni un buffer. Elle est absente si tous les draws sont des triangles directs ; sinon une unique texture est réservée du premier producer au dernier cover et clearée avant chaque producer. Son coût entre exactement une fois dans le pic.

Le graphe contient target, staging readback, vertex/index/uniform et éventuellement depth/stencil. Les dépendances imposent l'ordre linéaire des draws et chaque `producer -> cover`; `RenderGraph.of` valide références, lifetimes, formats, usages, adjacence atomique et pic calculé. Un graph contrefait ne peut atteindre aucune autre lane.

## 5. Budget, lowering et erreurs

Les buffers V/I/U sont calculés depuis les coûts exacts de `PathFillGeometryF32`, puis réservés par la politique de pool. Un triangle utilise trois sommets F32 et trois indices I32. Un stencil utilise trois sommets et indices par arête de l'edge fan, plus le quad de cover (quatre sommets, six indices). Chaque draw réserve un slot `Uniform32` dynamique, aligné sur `minUniformBufferOffsetAlignment`.

Le pic frame-local est :

```text
target + readback + vertexCapacity + indexCapacity + uniformCapacity
    + depthStencilBytes (si StencilCover)
```

À 1×, `depthStencilBytes = 4 × width × height`. Offsets uniformes, tailles host-addressable, capacités de pool, `maxBufferSizeBytes` et limites device sont contrôlés avant `Ready`; les buffers restent réservés jusqu'à completion/readback. Les capacités pool arrondies sont déclarées par le graphe et comptées dans le pic.

Le lowerer W4c est un sibling scellé de W4a/W4b. Il réutilise mécaniquement les pipelines natifs winding/even-odd/cover, les snapshots, l'ABI `Uniform32`, les buffers V/I, le pool, `Depth24PlusStencil8`, le preflight, completion et readback. Les mappers, prepared builders et `PathTessellator` legacy ne sont pas une autorité W4c : cette géométrie est dans `:gpu-renderer` et ne peut modifier tolérance, fan ou fill rule après `Ready`.

Précédence : non-fini/contradiction reconnue → `InvalidScene` ; famille ou état hors scope → `GapNotMigrated` et legacy ; complexité/overflow/budget → `ResourceLimitExceeded` sans allocation ; capability physique absente après sélection → `GapOnPromotedScope`/`UnsupportedCapability` ; graph, scratch, lowering, ordre atomique ou exécution contradictoires après `Ready` → erreur terminale sans fallback.

## 6. Preuves, exclusions et alternatives

Les tests sont comportementaux, publics ou portent sur des invariants de données. Sont interdits : inspection de source, reflection, accès privé, call-count d'infrastructure et duplication de la classification dans le renderer.

Les preuves couvrent dans `:math` triangle strict, quad/cubic/arc à `0,25 px`, fermeture implicite, transform négatif, limites/overflow/immutabilité et parité JVM/JS ; dans planner/lowerer, sélection direct/stencil, `WINDING`/`EVEN_ODD`, ressources/budgets/lifetimes/frontières ±1 et refus explicite quand l'atomicité multi-path n'est pas prouvée.

Les bytes `Surface` sont comparés exactement à un oracle CPU indépendant pour triangle, concave, trou winding, trou even-odd, courbes, scissor, transform négatif, deux paths translucides et RGBA/BGRA. L'oracle ne réutilise ni planner, payload GPU ni flattening de production ; il implémente approximation bornée, test de fill au centre de pixel, `SrcOver` linear-premultiplied et store sRGB après chaque draw.

Font, codec, GMs Skia, `jpg-color-cube`, régénération render/dashboard, baseline et thresholds sont hors scope et inchangés. Les GMs ne participent pas à l'admission. La dette SDF W4b reste strictement inchangée : W4c ne modifie ni sa capability, ni son oracle, ni son suivi de gap.

| Approche | Décision | Motif |
|---|---|---|
| Fan direct général | rejetée | ne prouve pas concavité, trous ou auto-intersections |
| `PathTessellator` legacy comme autorité | rejetée | viole l'ownership `:math` et autorise une reclassification tardive |
| `ANTIALIASED` par hard-edge 1× | rejetée | change silencieusement la coverage ; l'AA 4× est une capability distincte |
| Inverse fills dès W4c | rejetée | inverse et clips booléens relèvent du sous-graphe W4e |
| Premier path seulement | rejetée | viole atomicité de frame et ordre `SrcOver` |

## 7. Critères de sortie

W4c est prête à être empilée lorsque la capability est reliée de `Surface` au readback, que la géométrie est entièrement dans `:math`/`:math:matrix`, que direct et stencil sont scellés, que buffers/depth-stencil/budgets/lifetimes reflètent les réservations réelles, que l'ordre multi-path est prouvé ou explicitement refusé, que l'oracle public passe, et que W3/W4a/W4b ainsi que la baseline globale restent inchangés.
