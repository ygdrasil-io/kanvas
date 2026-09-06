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

Le planner construit l'ordre total de paint et les groupes atomiques adjacents, puis choisit et scelle par draw :

- `DirectTriangle` : triangle strict, `WINDING`, non-inverse ;
- `StencilCover` : tout autre fill admis, non-inverse, `WINDING` ou `EVEN_ODD`.

Une frame W4c est atomique : elle est entièrement rendue par cette capability ou reste legacy avant promotion. `RenderGraph.of` valide cet ordre et les groupes. Après `Ready`, le renderer et le lowerer n'authentifient que mécaniquement le plan : ils ne reclassifient ni la géométrie, ni le fill rule, ni la stratégie, et ne tentent aucun fallback. Une divergence est terminale.

## 2. Admission et exclusions

Une frame est candidate si scène/cible ont le même extent sRGB non vide, si tous ses 1–512 draws visuels sont `GeometryNode.Path`/`DrawOrigin.PATH`, et si chaque draw respecte simultanément :

1. `PathF32` fini, non-inverse, de fill rule `WINDING` ou `EVEN_ODD`, dont la normalisation device-space retient au moins un contour ; « non dégénéré » ne signifie pas qu'une région de fill effective est non vide ;
2. material solid fini, `FILL` hard-edge et `SrcOver`, sans shader, blender, filtre, effect, resource ou operation blend ;
3. transform identité ou scale/translate axis-aligned fini, y compris une échelle négative ;
4. clip vide ou `ClipStackNode.DeviceRect` non-AA, non vide, aux bords `I32` ;
5. bounds, scissor, limites géométriques, capacités et budget calculables par arithmetic checked.

`SetTransform`, `SetClip` et `Annotation` finies peuvent être traversées comme métadonnées déjà capturées. `TEXT_EXPANDED_PATH` reste exclu : font est hors périmètre.

Sont hors W4c : strokes/hairlines, `ANTIALIASED`, inverse fills, rotation/skew/perspective, clips complexes ou AA, MSAA, target non-sRGB, images, gradients, shaders, filtres, blenders et blends non-`SrcOver`.

Les inverse fills restent W4e avec clips path/inverses/booléens. L'AA path est une capability distincte : `StencilAA` 4× ne doit jamais être obtenu en rendant une demande `ANTIALIASED` par la voie hard-edge 1×.

## 3. Géométrie backend-neutral dans `:math`

`:math:geometry` possède toute géométrie W4c ; `:math:matrix` possède les transforms. `:gpu-plan` et `:gpu-renderer` ne définissent aucun point, contour, triangle, fan, bounds ou tolérance géométrique.

Les valeurs ajoutées sont `PathFillLimitsI32`, `PathFillFlatteningPolicyF64`, `PathFillGeometryF32`, `PathFillDirectTriangleF32` et `PathStencilEdgeFanF32`. `PathFillLimitsI32` fixe 65 536 arêtes de flattening tentées au plus par path et 262 144 au plus par frame. Les coordonnées émises sont `F32`, les calculs de flattening/validation sont `F64`, les index, limites et compteurs portent `I32` ou `I64`. Ces types ne dépendent ni de WebGPU, WGSL, pipeline, bind group ni handle natif.

La normalisation et la préparation sont déterministes et précèdent toute allocation native :

1. copier le `PathF32` immutable de la Scene IR ; l'origine implicite avant le premier verbe dessinant est `(0,0)` ;
2. appliquer le transform admissible dans `:math:matrix` ;
3. un `MoveTo` clôt le contour de fill précédent, un contour ouvert est clôt à la fin, et un `Close` répété est un no-op ; un `ArcTo` SVG dont start et end sont identiques est toujours un no-op, quels que soient ses rayons ;
4. aplatir quad, cubic et arc en F64 à une erreur de flèche device-space `<= 0,25 px` ; une quad ou cubic dont les endpoints coïncident peut néanmoins porter une géométrie par ses contrôles. Chaque arête tentée, y compris fermeture, arête nulle ou arête dont des points F64 distincts s'effondreraient en F32, débite les limites par path et frame avant émission ;
5. retirer les sommets F32 émis consécutivement identiques et les fermetures de longueur nulle ; ce dédoublonnage peut donc retirer des points distincts en F64 après leur conversion F32 ;
6. retirer les contours ayant moins de trois sommets distincts ou entièrement collinéaires ; préserver les retraces et auto-intersections ;
7. vérifier finitude, fermeture, bounds et `PathFillLimitsI32` avec arithmetic checked ;
8. émettre un snapshot `F32` profondément immutable, son scissor conservateur, ses arêtes fermées non nulles et ses coûts vertex/index exacts.

Convergence impossible, limite d'arêtes tentées, limite frame, overflow ou taille host-addressable ne publient pas de contour partiel et retournent `ResourceLimitExceeded` avant allocation. Le non-fini est `InvalidScene` avant device. Si tous les contours sont retirés, le résultat est `Empty` et la frame est `NotCandidate` avant promotion. À l'inverse, dès qu'au moins un contour normalisé est retenu, le draw est non dégénéré pour l'admission, même si plusieurs contours s'annulent exactement et que le stencil produit finalement un no-op. Les deux frontières `PathFillLimitsI32` sont testées sans dépendre d'un GM ou d'une fixture.

`DirectTriangle` exige après préparation un seul contour fermé, exactement trois sommets distincts finis et non collinéaires, aucune courbe/arc/retrace/auto-intersection, `WINDING` non-inverse, bounds et scissor non vides. Sa preuve est calculée dans `:math` et transportée par le plan. Triangles `EVEN_ODD`, concaves, multi-contours, trous, courbes, arcs et paths de plus de trois côtés utilisent `StencilCover`.

Pour `StencilCover`, `EVEN_ODD` conserve la borne générale de 65 536 arêtes tentées par path. Le fill `WINDING` emploie un stencil 8-bit sans tenter de calculer un winding global exact : il est admis seulement si `emittedNonZeroClosedEdgeCountI32 <= 255` pour ce draw. Ainsi toute magnitude nette par sample reste conservativement loin de l'enroulement `±256` qui wrap. La frontière est exacte : 255 est admis, 256 retourne un résultat typé de géométrie trop complexe (`ResourceLimitExceeded`) avant `Ready`. `DirectTriangle` n'est pas soumis à cette borne Winding.

## 4. Plan, ordre et `RenderGraph`

`PathFillDraw` est une donnée immutable du plan : `commandIndex`, couleur linear-premultiplied, `PathFillGeometryF32`, stratégie, scissor `RectI32`, `HardEdge`, `SingleSample` et `SrcOver`.

Un triangle direct devient une passe colorée. Un draw stencil forme exactement le groupe atomique suivant, adjacent dans l'ordre de paint :

```text
StencilProducer(command i, clear stencil = 0, stencil write)
    -> StencilCover(command i, stencil read-write test+reset, SrcOver)
```

Le producer applique winding/even-odd sans écrire de fragments couleur ; le cover applique le quad borné, la couleur et le scissor. La destination est stockée/quantifiée sRGB entre draws.

Le planner construit les 1–512 draws seulement s'il peut sceller l'ordre total et l'atomicité de chaque paire; `RenderGraph.of` les valide. À défaut, la frame entière est explicitement refusée au stade pertinent. Le lowerer ne refait pas cette preuve : il authentifie mécaniquement le plan déjà scellé. Il est interdit de réduire silencieusement la capability à une path/frame, de rendre seulement le premier path ou de reclasser le reste.

W4c étend `RenderGraph` de façon complète :

- `PlanTextureFormat` devient scellé avec `Color(PlanLogicalColorFormat)` et `DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8)` ;
- `PlanResourceKind.Texture2D` accepte `PlanTextureFormat.Color` ou `PlanTextureFormat.DepthStencil`, `PlanResourceRole` gagne `DepthStencil`, et `PlanResourceUsage` gagne `DepthStencilAttachment` ;
- `PlanOperationCapability` gagne les faits depth/stencil et stencil/cover ;
- `AttachmentLoadPlan` gagne `Load` ;
- `PlanDepthStencilLoadStore` est un contrat typé séparé, avec `ClearZeroStore` pour le producer et `LoadStoreTestReset` pour le cover ;
- `PlanPass` gagne `StencilProducer` et `StencilCover`, incluant le groupe atomique et des accès depth/stencil typés : écriture pour le producer, lecture-écriture `test+reset` pour le cover.

Règles exactes de passes : la première passe de frame qui touche l'attachement couleur, directe ou producer, utilise `ClearTransparent`; les suivantes utilisent `Load`. Toute passe couleur store. Un producer utilise `ClearZeroStore` pour depth/stencil, attache la couleur avec `Store` mais sans écriture couleur; il clear donc aussi la couleur transparente s'il est la première passe. Un cover utilise couleur `Load+Store` et `LoadStoreTestReset` pour depth/stencil : il teste le stencil et le remet à zéro dans la même passe native (compare `NotEqual`, opération de passage `Zero`, write mask `0xff`), donc son accès stencil est obligatoirement read-write. Un direct utilise couleur clear/load puis store, sans depth/stencil.

La texture porte `Depth24PlusStencil8`, le même extent que la target, un seul sample et l'usage depth/stencil. Elle n'est ni une `PlanLogicalColorFormat` ni un buffer. Elle est absente si tous les draws sont des triangles directs ; sinon une unique texture est clearée avant chaque producer. Son intervalle d'usage est du premier producer au dernier cover, mais son lifetime physique déclaré va jusqu'au readback avec vertex/index/uniform, car les quatre ressources partagent le même lease de frame-pool.

Le graphe contient target, staging readback, vertex/index/uniform et éventuellement depth/stencil. Les dépendances imposent l'ordre linéaire des draws et chaque `producer -> cover`; `RenderGraph.of` valide références, lifetimes, formats, usages, les accès stencil producer-write et cover-read-write-test-reset, adjacence atomique et pic calculé. Un graph contrefait ne peut atteindre aucune autre lane.

## 5. Budget, lowering et erreurs

Les buffers V/I/U sont calculés depuis les coûts exacts de `PathFillGeometryF32`, puis réservés par la politique de pool. Un triangle utilise trois sommets F32 et trois indices I32. Un stencil utilise trois sommets et indices par arête de l'edge fan, plus le quad de cover (quatre sommets, six indices). Chaque draw réserve un slot `Uniform32` dynamique, aligné sur `minUniformBufferOffsetAlignment`.

Le pic frame-local est :

```text
target + readback + vertexCapacity + indexCapacity + uniformCapacity
    + depthStencilBytes (si StencilCover)
```

À 1×, `depthStencilBytes = 4 × width × height`. Offsets uniformes, tailles host-addressable, capacités de pool, `maxBufferSizeBytes` et limites device sont contrôlés avant `Ready`. Vertex, index, uniform et depth/stencil partagent un unique lease de frame-pool : leurs lifetimes physiques vont donc jusqu'à completion/readback, sans libération anticipée après le dernier cover. Le pic au readback inclut explicitement depth/stencil. Les capacités pool arrondies sont déclarées par le graphe et comptées dans ce pic.

Le lowerer W4c est un sibling scellé de W4a/W4b. Il réutilise mécaniquement les pipelines natifs winding/even-odd/cover, les snapshots, l'ABI `Uniform32`, les buffers V/I, le pool, `Depth24PlusStencil8`, le preflight, completion et readback. La géométrie legacy de `PathTessellator` vit dans `:gpu-renderer`; W4c ne la consomme pas comme autorité et ne consomme que les snapshots/proofs profondément immutables produits dans `:math`. Les mappers et prepared builders ne peuvent modifier tolérance, fan ou fill rule après `Ready`.

Précédence : non-fini/contradiction reconnue → `InvalidScene` ; famille ou état hors scope → `GapNotMigrated` et legacy ; complexité/overflow/budget → `ResourceLimitExceeded` sans allocation ; capability physique absente après sélection → `GapOnPromotedScope`/`UnsupportedCapability` ; graph, scratch, lowering, ordre atomique ou exécution contradictoires après `Ready` → erreur terminale sans fallback.

## 6. Preuves, exclusions et alternatives

Les tests sont comportementaux, publics ou portent sur des invariants de données. Sont interdits : inspection de source, reflection, accès privé, call-count d'infrastructure et duplication de la classification dans le renderer.

Les preuves couvrent dans `:math` triangle strict, initialisation à l'origine implicite, `MoveTo`/`Close`, vertices répétés, contours dégénérés, retraces, self-intersections, quad/cubic/arc à `0,25 px`, transform négatif, limites tentées/émises 255/256/65 536/262 144, overflow, immutabilité et parité JVM/JS ; dans planner/lowerer, sélection direct/stencil, `WINDING`/`EVEN_ODD`, ressources/budgets/lifetimes jusqu'au readback, formats/passes exacts et refus explicite quand l'atomicité multi-path n'est pas prouvée.

Les bytes `Surface` sont comparés exactement à un oracle CPU indépendant pour triangle, concave, trou winding, trou even-odd, courbes, scissor, transform négatif, deux paths translucides et RGBA/BGRA. L'oracle ne réutilise ni planner, payload GPU ni flattening de production. Pour les fixtures line-only, il calcule exactement les crossings par ray casting. Pour une fixture de courbes, la comparaison whole-image byte-exact n'est autorisée que si un oracle d'intervalles F64 de test, alimenté par le `PathF32` original et le transform — et non les vertices aplatis de production — construit une enclosure device-space à arrondi sortant de chaque primitive. Il calcule indépendamment une borne conservative de l'erreur euclidienne introduite par la conversion finale en F32, puis prouve pour chaque centre de pixel une distance strictement supérieure à `0,25 px +` cette borne `+` l'enclosure numérique de l'oracle, avec chaque crossing/tie résolu de façon unique. Sans ce certificat, la fixture de courbes est invalide : elle n'obtient ni tolérance ni égalité approximative. Les tests math de tolérance restent indépendants. L'oracle certifié applique ensuite `SrcOver` linear-premultiplied et le store sRGB après chaque draw.

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
