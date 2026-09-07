# État W04 — geometry/coverage

Révision de code W4c vérifiée : `546a0500f048e7b28c3f3303b55990b17245505b`
(`fix(math): preserve anisotropic rotated arc geometry`), empilée sur W4b. Les
vérifications fraîches ci-dessous ont été exécutées le 2026-09-06 ; elles ne
lancent ni GM, ni Skia.

## Tranches W4a, W4b et W4c atteintes

W4a publie `solid-rect-scalar-aa-simple-scissor-src-over-srgb-v1` pour les frames de `Rect` solides, axis-aligned et fractionnaires. W4b ajoute sa branche sœur fermée, `solid-rect-rrect-scalar-aa-simple-scissor-src-over-srgb-v1`, pour une frame ordonnée de `Rect` et `RRect` remplis, `SolidColor` prémultipliée, `SrcOver`, AA scalaire, cible sRGB 1× et scissor entier simple.

W4b n'est sélectionnée que si la frame comporte au moins une primitive de provenance `DrawOrigin.RRECT`. Chaque `AnalyticRRectDraw` représente une primitive, conserve son ordinal et sa provenance `RECT`/`RRECT`, et transporte les faits device-space normalisés, le scissor et le slot Uniform80 scellés. Un `Rect` cohabitant est un `RRectF32` à huit `+0f`, mais reste d'origine `RECT`. Les RRect sont normalisés par l'unique API backend-neutral de `:math`, avant puis après `mapAxisAligned`, avec mêmes vecteurs et bits F32 sur JVM et JS.

La chaîne reste W3 → W4a → W4b : un Rect seul demeure W3/W4a suivant son enveloppe. Après `Ready` W4b, toute divergence du graphe, lowering, preflight ou matérialisation est un refus terminal : aucun reclassement W4b → W4a/W3, fallback direct, recalcul de scissor/bounds/rayons/transform, ni allocation « best effort » n'est permis.

W4c ajoute `solid-path-fill-tessellation-stencil-hard-1x-simple-scissor-src-over-srgb-v1`.
Elle admet une frame atomique de 1 à 512 `GeometryNode.Path` de provenance
`DrawOrigin.PATH`, `SolidColor`, `FILL`, `HARD_EDGE`, `SrcOver`, sRGB 1×,
transform identité/scale/translate et clip vide ou scissor `I32` non-AA. Le
chemin est intégralement préparé dans `:math` : `:math:matrix` construit le
flux device-space F64 et `:math:geometry` publie des snapshots F32 immuables,
leur scissor conservateur et la preuve de stratégie. `:gpu-plan` scelle ensuite
le `RenderGraph`, puis `:gpu-renderer` l'authentifie et le matérialise
mécaniquement jusqu'à `Surface`/readback, sans mapper ni `PathTessellator`
legacy après `Ready`.

La metadata d'un arc SVG tourné sous scale axis-aligned anisotrope est
redécomposée depuis sa covariance dès que les axes transformés ont un produit
scalaire non nul. La voie orthogonale rapide exige désormais `axisDot == 0.0` ;
la régression qui motivait ce verrou mesurait `0.32724927994422615 px` d'erreur
de support, au-delà de la borne W4c de `0.25 px`.

`DirectTriangle` est réservé à un unique contour `WINDING`, line-only, de trois
sommets distincts non collinéaires, sans retrace ni auto-intersection. Tout
autre fill admis devient une paire `StencilProducer → StencilCover` adjacente,
du même groupe atomique et du même ordre de paint. La première passe couleur
clear, les suivantes load/store ; le producer clear le stencil à zéro et le
cover le teste puis le remet à zéro (`NotEqual`/`Zero`, masque `0xff`) en accès
read-write. Une divergence de graph, scratch, lowering, preflight,
matérialisation ou ordre après `Ready` reste terminale et ne revient jamais à
W4b/W4a/W3 ou au legacy.

Les limites W4c sont `0.25 px` de flèche device-space, profondeur de subdivision
32, 65 536 arêtes tentées par path et 262 144 par frame. Chaque tentative est
débitée avant émission/dédoublonnage. Un stencil `WINDING` admet au plus 255
arêtes fermées non nulles émises ; 256 est
`ResourceLimitExceeded(WindingStencilEdgeLimit)`. `EVEN_ODD` conserve la borne
générale. Inverse fills, AA/MSAA, strokes/hairlines, rotation/skew/perspective,
clips complexes, images, gradients, shaders, filtres et blends non-`SrcOver`
restent hors promotion W4c et conservent leur route legacy avant `Ready`.

W4d.1 atteint la capability
`solid-path-stroke-tessellation-stencil-hard-1x-simple-scissor-src-over-srgb-v1`.
Elle accepte une frame atomique de 1 à 512 `GeometryNode.Path` de provenance
`DrawOrigin.PATH`, mélangeant `FILL`, `STROKE` et `STROKE_AND_FILL` et contenant
au moins un stroke. Son enveloppe demeure non inverse, `HARD_EDGE`,
`SolidColor`, `SrcOver`, sRGB 1×, layer racine, transform
identity/scale/translate et clip vide ou scissor `I32` non-AA. Caps Butt/Round/
Square, joins Miter/Round/Bevel, dash et hairline sont préparés dans `:math` :
le finite stroke garde dash et outline paramétriques source avant projection et
flattening F64→F32, tandis que la hairline reçoit une couverture nominale d'un
pixel device après projection. `STROKE_AND_FILL` non inverse publie une unique
union topologique certifiée ; une largeur nulle est un `FILL`, jamais une
hairline ajoutée.

La chaîne d'autorité W4d.1 est fermée : `:math:geometry` possède styles,
ledger transactionnel et snapshots immuables ; `:math:matrix` prépare le
device-space axis-aligned ; `:gpu-plan` sélectionne, budgète et scelle le
`RenderGraph` ; `:gpu-renderer` authentifie l'identité canonique, le graph, le
scratch et les payloads, puis matérialise mécaniquement jusqu'à `Surface` et
readback. Après `Ready`, aucun mapper, `PathTessellator`,
`AdvancedStrokePlan`, `GPUStroke`, `GPUPathHairlineContract` ou fallback
legacy ne peut être rappelé. Toute divergence de style, géométrie, capability,
graph, ressources, payload, scratch, pipeline ou ordre est un refus terminal
avant submit : elle ne reclassifie pas la frame et ne publie aucun rendu
partiel.

Les unités de travail, vertices, indices et bytes sont débités avant émission
ou dédoublonnage par un seul `PathStrokeWorkLedgerI64`, de 65 536 unités
tentées par path et 262 144 par frame, profondeur 32 et flèche device-space
0,25 px. Les limites explicites I32/I64 de vertices, indices, snapshots et
taille hôte restent terminales. Direct mesh est réservé au contour prouvé
simple ; le cas général est un groupe atomique adjacent
`StencilProducer → StencilCover`, et un `STROKE_AND_FILL` ne double jamais
deux windings ni deux composites `SrcOver` dans son recouvrement.

W4d.1 planifie V/I/Uniform32 et, seulement lorsqu'un stencil est requis, une
unique texture `Depth24PlusStencil8` 1×. Target, staging de readback, buffers
et D24S8 sont décidés et validés avant `Ready`, avec arithmetic checked,
capacités poolées et pic physique fermé. Le lease des ressources reste détenu
jusqu'à completion et readback ; acquisition, upload, preflight, submit et
finalisation rollbackent ou mettent le slot en quarantaine sur chaque échec,
sans fuite ni réutilisation prématurée.

## Ressources, durées de vie et ABI

Un graphe W4b matérialise exactement cinq `PlanResource`; pipeline et bind group sont des faits scellés, jamais des substituts au staging de readback.

| Rôle | Kind / usages | Taille planifiée | Durée de vie |
| --- | --- | --- | --- |
| `LogicalTarget` | texture 2D, render attachment, copy source | `4 × width × height` | `[0, 2)` |
| `ReadbackStaging` | buffer, copy destination, map read | `alignUp(4 × width, 256) × height` | `[1, 2)` |
| `VertexData` | buffer, vertex, copy destination | capacité pool réservée | `[0, 2)` |
| `IndexData` | buffer, index, copy destination | capacité pool réservée | `[0, 2)` |
| `UniformData` | buffer, uniform, copy destination | capacité pool réservée | `[0, 2)` |

Pour `N` primitives : `vertexBytes = 32 × N`, `indexBytes = 24 × N`, `uniformStride = alignUp(80, minUniformBufferOffsetAlignment)` et `uniformBytes = uniformStride × N`. Avant `Ready`, les multiplications checked imposent aussi `uniformBytes <= Int.MAX_VALUE` (taille hôte représentable) et `(N - 1) × uniformStride <= UInt.MAX_VALUE` (dernier dynamic offset). Le pic checked est la somme des cinq tailles physiques, avec les capacités poolées V/I/U, et les buffers ne retournent au pool qu'après completion/readback. La frontière publique reste 512 draws mixtes avec au moins un RRECT en W4b ; 513 est `NotCandidate` et ne promeut aucune allocation.

W4c conserve target, staging readback, vertex, index et Uniform32 ; une frame
avec au moins un `StencilCover` ajoute une unique texture
`Depth24PlusStencil8`, de l'extent cible, sample 1 et usage
`DepthStencilAttachment`. Les capacités V/I/U et D24S8 sont décidées par le
graph avant `Ready`; le pic exact est `target + readback + vertexCapacity +
indexCapacity + uniformCapacity + depthStencilBytes`, avec
`depthStencilBytes = 4 × width × height` une seule fois. Les quatre ressources
V/I/U/D24S8 partagent le lease du frame-pool et restent physiquement détenues
jusqu'au readback/completion, y compris si l'intervalle logique D24S8 finit au
dernier cover. Les frames direct-only n'allouent pas D24S8.

W4b réutilise sans modification `Uniform80` d'`AnalyticShape` : target/padding aux octets 0..15, couleur prémultipliée 16..31, bounds device 32..47, `TL, TR` 48..63, puis `BR, BL` 64..79. Les Rect ont huit rayons positifs nuls et conservent la couverture rectangulaire exacte ; les RRect non nuls suivent la branche SDF native existante.

## Preuves pixels non-GM

L'oracle CPU W4b est test-only et indépendant : aire de chevauchement exacte avec huit rayons nuls, et équation SDF/ramp d'AA native reproduite sans importer shader, renderer, materializer ou helper privé. La cible RGBA8 sRGB stockée puis quantifiée est relue comme destination par chaque draw suivant ; une cible de précision différente demanderait une nouvelle capability et un nouvel oracle, pas une réutilisation implicite de W4b. Les comparaisons sont byte-exact (`assertContentEquals`), sans seuil ni tolérance. Elles couvrent les RRect asymétriques, normalisation à la limite, échelles positive/X/Y/XY, scissor, ordre `SrcOver` avec quantification sRGB entre draws, ordres de pixels RGBA/BGRA et la frontière 512/513.

`RRectNormalizationF32Result.Accepted` conserve un snapshot `RRectF32` profond et privé ; `copyShape()` rend un nouveau snapshot défensif. Le contrat public expose `Rejected.reason` et les raisons singulières `NonFiniteRadius` / `NegativeRadius`, tandis que la factory `AnalyticRRectDraw.of` conserve l'ordre `commandIndex`, `color`, `origin`, `deviceShape`, `rasterBounds`, `scissor`.

L'oracle W4c est test-only et indépendant : aucun import de `gpu.plan` ou
`gpu.renderer`, aucun graph, payload, préparation ou flattening de production.
Il part des verbes `PathF32` source, applique son propre mapping F64, calcule
les crossings Winding/EvenOdd au centre du pixel, applique le scissor, `SrcOver`
linear-premultiplied, encode sRGB et quantifie après chaque draw. Les pixels
RGBA8/BGRA8 sont comparés byte-exactement. Quad, cubic et SVG arc exigent en
plus un certificat d'intervalles F64 à arrondi sortant : toute fixture dont les
crossings/ties ne sont pas séparés au-delà de `0.25 + f32Bound + intervalBound`
est `Uncertified` et ne peut produire aucun attendu ni tolérance.

## Commandes fraîches W4c

| Commande | Résultat frais du 2026-09-06 |
| --- | --- |
| `rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest :math:matrix:jvmTest :math:matrix:jsNodeTest :render-ir:test :gpu-plan:test --rerun-tasks` | `BUILD SUCCESSFUL` ; 85 tâches exécutées ; scan XML des modules concernés : 0 failure, 0 error. |
| `rtk ./gradlew :gpu-renderer:test --tests '*GpuPlan*' --tests '*W4c*' --tests '*GPUCorePrimitivePathStencil*' --tests '*GPUFramePreflighterTest*' --tests '*GPUWgpu4kCorePrimitiveFramePoolTest*' --rerun-tasks` | `BUILD SUCCESSFUL` ; 53 tâches exécutées ; XML renderer : 0 failure, 0 error. |
| `rtk ./gradlew :kanvas:test --tests '*GPUPlanSurface*' --tests '*SurfaceTest*' --tests '*DisplayOpSceneAdapterTest*' --rerun-tasks` | `BUILD FAILED` uniquement sur la baseline : 2 062 tests, 45 failures, 0 error ; les 45 sont `GPUAllApiBlendSurfaceTest :: DrawPoint` du ledger, aucune failure W4c. |
| `rtk ./gradlew :kanvas:test --rerun-tasks` | `BUILD FAILED` uniquement sur la baseline : 120 suites, 3 646 tests, 51 failures, 0 error ; aucun nom nouveau vis-à-vis du ledger W4b. |

Les compilations transitives n'ont exécuté aucun test `font`; aucun test `codec` n'a été lancé.

## Commandes fraîches W4d.1

| Commande | Résultat frais du 2026-09-07 |
| --- | --- |
| `rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest :math:matrix:jvmTest :math:matrix:jsNodeTest :render-ir:test :gpu-plan:test --rerun-tasks` | `BUILD SUCCESSFUL` ; 85 tâches exécutées. |
| `rtk ./gradlew :gpu-renderer:test --tests '*GpuPlan*' --tests '*W4d*' --tests '*GPUCorePrimitivePathStencil*' --tests '*GPUFramePreflighterTest*' --tests '*GPUWgpu4kCorePrimitiveFramePoolTest*' --rerun-tasks` | `BUILD SUCCESSFUL` ; 53 tâches exécutées. |
| `rtk ./gradlew :kanvas:test --tests '*GPUPlanSurface*' --tests '*SurfaceTest*' --tests '*DisplayOpSceneAdapterTest*' --rerun-tasks` | `BUILD FAILED` uniquement sur le ledger historique : 2 073 tests, 45 failures, 0 error. |

Le scan XML frais `rtk rg -n '<failure|<error'
kanvas/build/test-results/test/TEST-*.xml` retourne 45 matches dans un seul
fichier, `TEST-org.graphiks.kanvas.surface.gpu.GPUAllApiBlendSurfaceTest.xml`.
Il contient exactement `GPUAllApiBlendSurfaceTest :: DrawPoint` pour les 15
blends avancés `PLUS`, `MULTIPLY`, `OVERLAY`, `DARKEN`, `LIGHTEN`,
`COLOR_DODGE`, `COLOR_BURN`, `HARD_LIGHT`, `SOFT_LIGHT`, `DIFFERENCE`,
`EXCLUSION`, `HUE`, `SATURATION`, `COLOR`, `LUMINOSITY`, chacun sous
`UNCLIPPED`, `SCISSOR` et `ALPHA_MASK` ; `rg -o '<error'` retourne 0. Il n'y a
donc aucun nom nouveau ni échec W4d.1 dans la gate filtrée. La compilation
transitive de `font` a eu lieu, sans sélection ni exécution de test `font` ;
aucun test `codec` n'a été lancé.

## Ledger XML global exact

Le scan `rtk rg -n '<failure|<error' kanvas/build/test-results/test/TEST-*.xml`,
exécuté après la gate globale W4c le 2026-09-06, retourne 51 matches dans 6
fichiers. L'inventaire XML totalise 120 suites, 3 646 tests, 51 failures et 0
error. Les 51 seuls noms sont :

- `ImageTest :: ColorType enum values()` ;
- `GPUAllApiBlendSurfaceTest :: DrawPoint/{PLUS, MULTIPLY, OVERLAY, DARKEN, LIGHTEN, COLOR_DODGE, COLOR_BURN, HARD_LIGHT, SOFT_LIGHT, DIFFERENCE, EXCLUSION, HUE, SATURATION, COLOR, LUMINOSITY}/{UNCLIPPED, SCISSOR, ALPHA_MASK}` — les 45 combinaisons exactes du produit cartésien ;
- `GPUMaskBlurDispatchTest :: local path mask scales dash intervals and phase()` ;
- `GPUPreparedSurfaceFrameBuilderTest :: public non finite singular and perspective transforms refuse before frame task assembly()` ;
- `GPUPreparedSurfaceFrameBuilderTest :: prepared atlas expands to ordered sampled packets sharing one artifact with distinct uniforms()` ;
- `GPUPreparedTextStrokeTest :: prepared stroke path key seals exact geometry and verb count seals every contour()` ;
- `GPURefusalGuardsTest :: direct fill guard refuses radial and sweep non identity matrix facts before dispatch()`.

`rtk rg -n '<error' kanvas/build/test-results/test/TEST-*.xml` ne retourne
aucune occurrence. Aucun nom nouveau et aucune failure W4c ne bloquent donc ce
suivi documentaire ; les failures listées sont hors périmètre et ne sont pas
modifiées par W4c.

## Exclusions et dette SDF

W4c n'a exécuté ni `:integration-tests:skia`, ni GM/dashboard/baseline, ni
`jpg-color-cube`, ni test `font` ou `codec`; `jpg-color-cube` demeure en
quarantaine. Les commandes `:kanvas:test` ont pu compiler transitivement des
modules `font`, mais aucune suite de test `font` n'a été sélectionnée. Aucun
shader, seuil de similarité, tolérance ou baseline n'a été modifié ; le diff
W4c ne contient aucun chemin `font`, `codec`, GM, dashboard, render/baseline ou
`jpg-color-cube`.

Pour les RRect non nuls, la SDF native n'est pas l'aire analytique Skia exacte. Cette dette est explicitement réservée à W7 : un nouveau shader ne pourra être envisagé qu'après une divergence matérielle constatée par l'intégration Skia. Il est interdit de la masquer par une tolérance, un seuil plus bas ou une rebaseline.

## Limites ouvertes

W4 reste ouverte. W4d.1 laisse explicitement : la limite conservative
`TopologyLimit` de l'union F64→F32 pour les auto-intersections, W4d.2
(transforms généraux et AA commune fill/stroke), W4e (clips path complexes,
inverse et booléens) et la baseline historique DrawPoint ci-dessus. W5
(materials), W6 (layers/effets) et W7 (convergence GM, y compris la
réévaluation de la dette SDF RRect W4b) ne font pas partie de W4d.1.

W4d.1 n'a exécuté ni GM Skia, dashboard ou baseline, ni `jpg-color-cube`, ni
test `font` ou `codec`; aucun de ces chemins, ni seuil/tolérance de similarité,
n'est modifié par le diff `codex/w4c-path-fills...HEAD`. Ces exclusions restent
des frontières de portée, non une rebaseline de la dette DrawPoint.
