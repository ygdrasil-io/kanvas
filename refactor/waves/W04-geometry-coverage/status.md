# État W04 — geometry/coverage — W4d.2

Révision W4d.2 vérifiée : `cdf854b` (`style: remove W4d trailing whitespace`),
empilée sur `codex/w4d-strokes-hairlines` à
`0537e222c08db756498d5b73d07b13fe25b41aeb`. Les vérifications publiées ici ont
été exécutées le 2026-09-08 ; elles n'ont lancé ni GM, ni Skia.

## W4d.2 — transforms généraux et architecture AA4

W4d.2 étend la préparation path à `Matrix3x3F64`, sous les classes typées
`Identity`, `AxisAlignedAffine`, `GeneralAffine` et `Perspective`. La
classification est IEEE exacte (sans epsilon ni chaîne de caractères) ; le
mapping affine conserve les arcs par covariance et la perspective certifie les
intervalles homogènes avant division. Un horizon réel `w = 0` est terminal
`PerspectiveHorizonCrossing`, non une approximation affine. Fills, finite
strokes, dashes et hairlines partagent le ledger F64 : le finite stroke est
construit en source-space avant projection, tandis que la hairline devient un
pixel device après projection.

Deux capabilities sont scellées :

- `solid-path-geometry-hard-1x-general-transform-simple-scissor-src-over-srgb-v1`
  pour une frame hard-edge générale entièrement 1× ;
- `solid-path-geometry-mixed-aa4-general-transform-simple-scissor-src-over-srgb-v1`
  pour une frame contenant au moins un path AA, lorsque les faits physiques AA4
  sont réellement publiés par le runtime.

La lane hard générale est fonctionnelle de bout en bout : fills, finite
strokes, hairlines, `STROKE_AND_FILL`, rotations, skews, réflexions et
perspectives bornées passent par `:math` → `:gpu-plan` → `:gpu-renderer` →
`Surface`; les preuves publiques utilisent un oracle F64 indépendant et des
pixels RGBA/BGRA byte-exact. Les scenes invalides ou divergentes restent
terminales avant submit, sans fallback legacy après `Ready`.

### Graphe, ressources et autorité AA4

L'architecture AA est implémentée et scellée, sans recalcul dans le renderer :

| Frame | Attachments exacts | Resolve / hard-edge |
| --- | --- | --- |
| hard seulement | couleur sRGB 1× ; D24S8 1× seulement si stencil | aucun MSAA, aucun resolve |
| AA seulement | couleur sRGB 4×, D24S8 4×, target logique sRGB 1× | seuls le dernier pass couleur et son groupe atomique portent le resolve 4×→1× |
| AA + hard | mêmes ressources AA, mask `RGBA8Unorm` linéaire 1× réutilisable, D24S8 1× si le hard path utilise stencil | le hard path produit un mask binaire 1× puis un cover 4× par `textureLoad` entier non filtré, identique pour les quatre samples |

Le lowering dérive un witness canonique et une autorité W4d.2 versionnée. Elle
scelle graph, allocations, slots/usages/durées de vie, passes, resolve final,
payloads `Uniform32`/`Uniform64`, slabs uniformes et capacité du frame pool.
Le preflight et le materializer ne reclassifient ni la couverture ni les
ressources ; ils ne consomment que ces faits. Les leases sont détenus jusqu'à
completion, les échecs pré-submit rollbackent ou mettent le slot en quarantaine,
et l'autorité privée interdit de forger un consumer mask 4× par une simple
structural key.

### Gap AA4 de production — non résolu et volontairement non masqué

Le runtime natif courant expose `RGBA8UnormSrgb` seulement à l'échantillonnage
`{1}`. Il ne fournit ni support sRGB 4× ni probe/entrée de resolve sRGB 4×→1×.
`RGBA8Unorm` possède bien les faits 1×/4× et resolve, et l'adaptateur D24S8
consomme désormais sa table authentifiée `RenderAttachment` `{1,4}` sans
l'ajouter artificiellement aux formats couleur larges. Cette correction D24 ne
fabrique donc aucun support sRGB4.

Conséquence : une scène W4d.2 AA à transform `GeneralAffine` ou `Perspective`
est actuellement un terminal explicite
`w4d.general.texture-sample-support-unavailable`, avant fallback. Il n'existe
pas encore de preuve Surface positive AA ou mixte, ni de pixels exacts de
coverage 0 / 0,5 / 1, tant qu'une requête/probe natif réel ne certifie pas
sRGB4 **et** son resolve. Le test public prouve le terminal et l'absence de
pollution de la frame hard suivante ; ce n'est ni une rebaseline ni une
équivalence isopixel revendiquée.

La correction `42efea430` préserve la compatibilité historique : le compiler
W4d.2 ne capture plus un path AA identity/axis-aligned qui relève des lanes
W4c/W4d existantes. `KanvasSmokeTest::canvas drawPath records command()`
redevient vert et la baseline globale revient à ses 51 échecs historiques.

### Vérifications fraîches W4d.2

| Commande | Résultat observé |
| --- | --- |
| `rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest :math:matrix:jvmTest :math:matrix:jsNodeTest :render-ir:test :gpu-plan:test --rerun-tasks --max-workers=1 --console=plain` | 115 suites XML, 1 685 tests, 0 failure, 0 error. |
| `rtk ./gradlew :gpu-renderer:test --tests '*GpuPlan*' --tests '*W4d*' --tests '*StencilAa*' --tests '*GPUFramePreflighterTest*' --tests '*GPUWgpu4kCorePrimitiveFramePoolTest*' --rerun-tasks --max-workers=1 --console=plain` | `BUILD SUCCESSFUL`; 53 tâches ; 13 suites, 309 tests, 0 failure, 0 error. |
| `rtk ./gradlew :gpu-plan:test --rerun-tasks --max-workers=1 --console=plain` après `42efea430` | `BUILD SUCCESSFUL`; 32 tâches ; 15 suites, 184 tests, 0 failure, 0 error. |
| Gate Surface filtrée W4d.2 | 2 080 tests, 45 failures historiques `DrawPoint`, 0 error ; aucune failure W4d.2. |
| `rtk ./gradlew --no-daemon -Pkotlin.compiler.execution.strategy=in-process :kanvas:test --rerun-tasks --max-workers=1 --console=plain` après `42efea430` | `BUILD FAILED` uniquement sur le ledger : 55 tâches, 120 suites, 3 664 tests, 51 failures, 0 error. |

Le dernier rerun isolé évite un classpath incrémental Kotlin intermittent vu
avec plusieurs démons (classes locales présentes mais temporairement non
chargées). Il conserve le même source HEAD et établit le ledger XML final ; ce
n'est pas un écart fonctionnel W4d.2.

Le ledger de 51 contient exactement les 45 `DrawPoint` (15 blends avancés ×
`UNCLIPPED`/`SCISSOR`/`ALPHA_MASK`) et les six échecs W4d.1 déjà recensés :
`ImageTest::ColorType enum values`, `GPUMaskBlurDispatchTest`, deux
`GPUPreparedSurfaceFrameBuilderTest`, `GPUPreparedTextStrokeTest` et
`GPURefusalGuardsTest`. Aucun `<error>` XML n'est présent.

La gate renderer complète rapportée pendant la revue Task 9 reste à 3 752
tests et 14 failures historiques, sans delta W4d.2 ; la gate ciblée ci-dessus
est la preuve fraîche de la surface modifiée par W4d.2.

## Tranches W4a, W4b, W4c et W4d.1 atteintes

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

Révision de code W4c vérifiée : `546a0500f048e7b28c3f3303b55990b17245505b`
(`fix(math): preserve anisotropic rotated arc geometry`), empilée sur W4b. Les
vérifications W4c ci-dessous ont été exécutées le 2026-09-06 ; elles n'ont
lancé ni GM, ni Skia.

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

## Correctif W4e Task 7 — 2026-09-09

Le scellement W4e distingue désormais le `SceneTarget` canonique 1× du
`LayerTarget` couleur MSAA 4× ; les masks hard et les scratchs clip conservent
`ClipMask`, et les familles D24S8 restent séparées. Le preflight,
matérialiseur et executor refusent toute continuation AA ou resolve dont les
rôles scellés sont contradictoires.

`InverseDomain.Zero` remplace le proxy W4d.2 par un snapshot exact du chemin
source non vide (segments et transform, inclus dans le digest canonique) ; seul
un chemin source réellement vide devient `Empty`, et les deux variantes sont
D24-free. Les tests couvrent ces faits au lowering public.

La matrice d'échecs W4e utilise désormais `GPUW4eFrameFailureBehavior` à
travers la session publique compiler → lowerer → preflight/materializer →
executor → completion/readback. Allocation, pipeline, bind group, encoder et
close refusent sans output/encoder scopes partiels, puis une exécution propre
retrouve le readback. Les anciens tests W4e à faux matérialiseur et rollback de
pool isolé ont été retirés.

Validation fraîche : `rtk ./gradlew :gpu-plan:test --rerun-tasks --console=plain`
a terminé avec `BUILD SUCCESSFUL` le 2026-09-09 (32 tâches exécutées). Les
gates renderer ciblées ont aussi passé ; les scénarios WGPU dépendants de
l'adaptateur natif restent explicitement `SKIPPED` lorsque cet adaptateur n'est
pas disponible.

La gate complète `rtk ./gradlew :gpu-renderer:test --rerun-tasks --console=plain`
a ensuite exécuté 3 775 tests. Elle reste rouge sur 15 échecs préexistants,
hors W4e (smoke runtime natif, inventaires de pipelines, matériaux, règles de
package et contrats image) ; les suites W4e ciblées demeurent vertes. Aucun de
ces échecs ne concerne les rôles W4e, la restauration `InverseDomain.Zero` ou
la matrice d'échecs publique.

## Limites ouvertes

W4 reste ouverte. W4d.2 laisse explicitement :

- un probe/requête de capability native sRGB 4× avec resolve 4×→1×, puis les
  preuves Surface AA/mixte exactes 0 / 0,5 / 1 ; aucune capacité ne doit être
  inventée pour contourner ce gap ;
- la limite conservative `TopologyLimit` de certaines unions PathOps F64→F32,
  notamment `STROKE_AND_FILL` projectif non vide et le fixture closed-skew ;
- W4e : clips path complexes, inverse paths et booléens.

W5 (materials), W6 (layers/effets) et W7 (convergence GM, incluant la
réévaluation de la dette SDF RRect W4b) ne font pas partie de W4d.2. Les gates
ci-dessus n'ont exécuté ni `:integration-tests:skia`, ni GM/dashboard/baseline,
ni `jpg-color-cube`, ni tests `font` ou `codec`. Des modules `font` peuvent
être compilés transitivement par Gradle, sans qu'aucune suite de test `font` ne
soit sélectionnée. Le diff W4d.2 ne modifie ni source `font`/`codec`, ni
seuil/tolérance, dashboard, rendu de référence ou baseline : ces exclusions
restent des frontières de portée, non une rebaseline du ledger DrawPoint.
