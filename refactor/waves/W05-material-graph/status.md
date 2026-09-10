# État W05 — material graph, W5a Solid/Opacity

Révision de production initiale : `7dbaf8cdf672e836f6ec6d77b1734cb68b6669db` (« admit W5a frame materials after geometry validation »), continuation du correctif global `39ff21985bd1407958d3ba1e909a74bbedf50010` sur `e0b1f39ce23a8badbd10074eb308908a26725280`. La vérification de cette vague couvre aussi les commits de recovery `64e6429c`, `582606d7`, `cbd8ab5e`, `33c54c09`, `9891e117` et `9aa924e5c`. Les cinq findings Important, les minors et les résidus d'admission/noms publics des scoped re-reviews sont traités dans cette même vague. Les deux re-reviews globales Task 8 sont désormais `READY`; W5a est close pour son périmètre, sans élargir les gates aux suites hors périmètre.

## Gates publiques W5a

W5a implémente `Transparent`, `Solid` et `Opacity` sous `SRC_OVER`. Chaque draw promu porte une `MaterialV1` vers une table immuable. La frontière W3 différée a été vérifiée en production : la capability historique exige table `null` et uniquement `LegacyColorV1`; `W5A_CAPABILITY_ID` exige une table présente et uniquement `MaterialV1`. Les formes hybrides sont refusées.

| Cellule publique | Preuve retenue |
| --- | --- |
| Rect hard-edge / fractional | fixtures nested-opacity et fractional Rect (Tasks 1–2) |
| RRect analytique | fixture fractional RRect et observation d'une couverture exacte 0.75 dans les frames mixtes |
| Path fill direct / stencil cover | fixtures Path (Task 3), plus chacune des deux variantes de frame mixte |
| Path stroke / hairline | fixtures publiques stroke et hairline (Task 3) |
| Point / Points | trois commandes, points multiples et hairline (Task 4) |
| Text pré-résolu | fixtures A8 et mutation d'une liste de glyphs déjà résolus (Task 5), sans génération de font |
| Vertices, avec/sans couleurs vertex | fixtures Picture et mutation des tableaux publics (Task 6) |
| Prepared frames mixtes | Rect → Point → Rect, Rect → A8 Text → Rect, Rect → Vertices → Rect et public Mesh sans programme : trois sources/opacités distinctes, ordre observé par l'oracle et mutation des collections publiques après capture |
| RRect + stroke/hairline | RRect → Path stroke ou hairline → RRect, géométrie W4d native, mutation du Path après Picture et pixels de composition |
| Bornes publiques | 512 runs natifs rendus avant/après le refus précoce de 513 runs sur une autre Surface du même runtime/backend; 683 matériaux point identiques dédupliqués, 683 chaînes distinctes de trois entrées refusées à la vraie borne 2048 |
| Admission avant interning | 683 Rect hors cible à chaînes distinctes sont élidées avant la table commune et seul le Point valide rend; Rect non finie + Point conserve `unsupported.core_primitive.geometry.non_finite_transform`; 683 candidats Vertices non finis gardent `unsupported.vertices.transform` |
| Frame Rect + RRect + Path | `Picture playback composes planned Rect RRect and Path bindings in recorded order` : trois bindings distincts, composition indépendante, observation AA, puis comparaison de tous les bytes après mutation du Path |
| Ordre intercalé et stencil | `native mixed stencil frame preserves interleaved Rect bindings and captured mutation` : Rect → RRect → Path stencil-cover → Rect, réutilisation d'un binding et comparaison de tous les bytes après mutation |
| Opacité identique, children distincts | `native mixed equal opacity preserves distinct Solid children and nested chains` : Rect rouge et RRect bleu à opacité 0.5, puis Path rouge et Rect bleu avec chaînes shader/Paint imbriquées et children réutilisés non adjacents; quatre observations pixel indépendantes |
| Refus puis récupération du runtime/backend | `public W5b gradient refusal leaves the runtime able to render a later W5a frame` : W5a valide → gradient refusé `unsupported.material.w5a.kind` → W5a valide, sur trois instances Surface partageant le même runtime/backend sans dispose intermédiaire, et pixels avant/après identiques |
| Absence d'ownership W5a | `hard edge gradient RRect outside W5a retains legacy pixels after caller stop mutation` : RRect hard-edge hors admission W4b, gradient rouge/bleu capturé en Picture, mutation des stops vers vert, pixels legacy rouge/bleu conservés |

## Composition native Task 7

La capability distincte `w5a-native-rect-rrect-path-composite-v1` est sélectionnée après les capabilities standalone existantes. Elle partitionne les commandes en runs natifs ordonnés, conserve leurs indices publics et confie Rect à W3, RRect analytique à W4b, Path fill à W4c et stroke/hairline à W4d. Le choix Path suit aussi les sémantiques de paint et l'admission native, pas la seule classe de géométrie. W4c n'accepte plus de conversion Rect/RRect en Path. La borne de 512 runs est vérifiée immédiatement après l'inventaire linéaire et avant toute `SceneSnapshot` par lane, y compris si une source material ultérieure serait refusée.

`W5aCompositePlanV1` possède les graphs de lanes, la table material internée et les réservations communes. Les refs locales sont remappées exactement dans la table de frame; la copie des draws conserve les faits de géométrie/raster sans reconstruction sémantique. Le graph composite utilise cette représentation hiérarchique typée, sans fabriquer une topologie standalone.

Correction d'interning : la clé d'une entrée inclut son child canonique et donc toute sa chaîne de bindings accessible, pas seulement sa structure et son alpha local. Puisque V1 désigne le child à `ref - 1`, toute nouvelle chaîne dont le child réutilisé n'est pas adjacent est copiée contiguë avant son parent. Les remaps canoniques restent déterministes, les bindings copiés défensivement et la borne de 2048 entrées contrôlée avant chaque ajout. La preuve RED Task 7 a produit du rouge dans le pixel bleu (`channel=0 observed=188 expected=[0]`); elle reste verte avec les chaînes imbriquées et le nouveau fragment.

`W5aPreparedFrameMaterialRegistry` remplace le bridge historiquement nommé CorePoint. Ses candidats Core locaux capturent uniquement le paint, sans acquisition de la table de frame ni admission géométrique implicite. Le mapper/lowerer existant, le recorder, la collecte des sémantiques et le preflight prepared vertices effectuent l'admission réelle : élision hors cible, transform, tessellation, limites et autres refus précèdent l'interning. Tous les `geometryRefusal` du lowerer sont propagés avant le recorder, sans filtre ad hoc; une Rect non finie conserve ainsi son diagnostic géométrique exact au lieu de devenir un refus de capture material ou de contrat de frame.

Seuls les payloads réellement admis Rect/RRect/Path/Points/A8 Text/Vertices et Mesh sans programme rejoignent ensuite la table commune. Text et Vertices sont abaissés une fois; leurs payloads sont remappés uniquement côté material, en authentifiant l'identité exacte du source-stage. Le witness Core vérifie également l'identité de la source locale et de sa ref globale. Aucune géométrie, tessellation, préparation d'atlas, inventaire ou artifact n'est rejoué. Les glyphs couleur/non-A8 et les géométries refusées n'acquièrent pas d'ownership W5a.

Correction de l'ownership des refus : les compilers natifs conservent séparément leurs refus de material et continuent les contrôles de géométrie, couverture, clip, état, provenance et limites sémantiques de toute la scène. Seule leur réussite complète permet d'émettre `GpuPlanSelection.MaterialOnlyRefusal`, lié à la scène, à la cible et à la capability. Les seams W4e et composite propagent ce résultat après leur propre admission; le backend vérifie l'identité scène/cible. Un `GapNotMigrated` ordinaire reste legacy : le routeur ne rescane plus les shaders et ne transforme plus des diagnostics accumulés en ownership. La nouvelle preuve RED échouait sur le faux terminal `unsupported.material.w5a.kind`; elle est GREEN sans modifier la géométrie ni substituer un material.

Le lowerer réutilise chaque lowerer et assembler natif avec un witness composite explicite. Il conserve les enveloppes d'origine pour le preflight exact, puis transporte seulement les ranges scellés vers les indices de la frame. Un witness de frame vérifie cible, readback, préparations, ordre, packets, états raster, dépendances et budget. La frame prépare une seule cible et un seul staging, efface au premier rendu puis charge l'attachement, et conserve les paires stencil atomiques.

Toutes les capacités V/I/U arrondies, y compris le scratch Rect W3 de cette composition, et les leases D24S8 sont comptées avant l'allocation. Un pool de session dédié aux lanes composites utilise la factory native existante avec la borne de 512 lanes; le pool standalone à trois slots reste inchangé. Le materializer réutilise les implémentations natives W3/W4b/W4c/W4d, partage un seul buffer readback, rassemble leurs leases sous un lifecycle commun et retient les journals de rollback si le nettoyage doit être retenté.

Les premières preuves RED ont révélé les anciennes exigences « toute la frame appartient à une seule lane », puis la limite des trois slots et la priorité de sélection devant W4b à 512 draws. Les corrections ajoutent une autorité composite et une gestion propre des ressources; elles ne relâchent pas les enveloppes standalone.

## Audit des compilations alternatives Solid/Opacity

Le renderer génère maintenant le source-stage WGSL directement depuis chaque DAG numérique scellé, dans l'ordre des dépendances : sRGB→linear, prémultiplication et Opacity opèrent sur les bindings bruts en F32. `W5aMaterialPlanEvaluator` a été supprimé. Les anciens slots couleur de géométrie sont neutres, pas une seconde autorité. La queue du DAG est authentifiée à la couverture existante, au blend prémultiplié `SRC_OVER` et à l'attachement sRGB/clamp/UNORM8.

La partition native V2 conserve intégralement le group 0 de chaque lane et ajoute un group 1, binding 0, uniform non dynamique, dans les seuls fragments color-writing. Stencil/mask producers ne reçoivent ni source ni binding material; W4e inverse conserve son préfixe stencil atomique. Les pipelines composés conservent géométrie, constantes, entrypoints, raster, attachment et couverture. Text/Vertices utilisent ce même générateur dans leur composition material authentique, sans overlay core supplémentaire. Aucun compute prépass n'est introduit.

Le seal géométrique historique reste inchangé; une partition material V2 dérivée des packets immuables est ajoutée au budget agrégé avant toute allocation. Les raw buffers identiques sont dédupliqués et vivent jusqu'à completion; layouts/pipelines/bindings et buffers sont dans le journal de rollback commun. Coût exact des fixtures 512 : W4a `164100 + 16 = 164116` bytes (un Solid), W4b `164100 + 2×16 = 164132` bytes (deux Solids). Aucun slack arbitraire et aucun coût masqué; un caller ayant choisi exactement l'ancien budget doit compter ce nouveau matériel.

| Site de production | Décision W5a |
| --- | --- |
| compilers W3/W4a/W4b/W4c/W4d/W4e | `EffectiveMaterialPlanner` produit table/ref sur W5a; l'adaptateur historique conserve seulement `LegacyColorV1` |
| lowerers natifs + source V2 | transport de table/ref, génération du fragment depuis le DAG, bindings bruts; aucune évaluation CPU du résultat |
| composite Task 7 | interning structure + bindings + chaîne child canonique et remap exact, adjacency V1 conservée; aucune nouvelle évaluation du shader ni conversion de géométrie |
| bridges core/text/vertices | interning frame-wide après admissions, remaps d'émissions scellées et `compileW5a*`; les lanes non promues conservent leur entrée générique |
| `GPUMaterialMapper` | Opacity legacy reste un refus `OPACITY_CHILD`; aucun aplatissement Solid après sélection W5a |
| images, glyphs couleur, MeshProgram et dispatchs legacy restants | hors promotion W5a; `DrawMesh` sans programme utilise réellement la route publique vertices et dispose d'une preuve de mutation dédiée |

Cet audit porte sur le code de production. Les assertions W5a ajoutées dans les tests compiler/lowerer, SceneArchiveCodec et DisplayOpSceneAdapter ont été retirées du diff complet de branche; seules des adaptations mécaniques de tests historiques restent, sans servir de preuve W5a et sans exécuter de suite codec/infrastructure. Les preuves conservées sont exclusivement publiques. Si CPU et GPU sont tous deux dans l'enveloppe, aucun RED black-box ne distingue honnêtement l'architecture : la review de production prouve alors DAG→fragment, les tests prouvent pixels/enveloppe/mutation.

L'audit exhaustif des déclarations publiques ajoutées depuis la base empilée inclut les nombres dans les collections, maps, tableaux et types nullables. Les résidus `MaterializedSolidV2.premultipliedRgbaF32` et `issue(refsByCommandIdI32, sourcePlansByCommandIdI32)` sont corrigés; les KDoc décrivent le slot géométrique neutre et le source-stage fragment. Les signatures historiques inchangées et les overrides imposés par Kotlin ne sont pas présentés comme de nouvelles APIs W5a.

## Enveloppe numérique des preuves publiques

Le cas public Rect puis Point à source ARGB `(197, 211, 79, 41)`, opacité shader `0.5` et paint `173/255` a isolé le RED `channel=2 observed=18 expected=[17]`. L'audit de la disposition brute, du binding et de l'expression DAG n'a pas révélé de divergence : l'oracle appliquait à tort la précision du `pow` WGSL à l'attachement fixed-function. La fixture 17/18 a été retirée de la suite car sa borne portable élargie est `Unbounded`; elle reste un exemple documenté, non un gate assoupli.

L'oracle indépendant distingue désormais les deux étapes. La source reste bornée par les opérations F32/WGSL du DAG. La conversion d'attachement utilise la référence sRGB réelle, calculée par arithmétique décimale dirigée et racines rationnelles, puis les bornes officielles : erreur totale d'encodage RGB strictement inférieure à un code et erreur de décodage, mesurée après ré-encodage exact, au plus un demi-code ([Metal, §8.7.7, 4 juin 2026](https://developer.apple.com/metal/Metal-Shading-Language-Specification.pdf)). Cette enveloppe contient l'encodage D3D limité à `0.6` code; l'alpha linéaire conserve aussi la borne FLOAT→UNORM de `0.6` code ([D3D 11.3, §3.2.3.6–8](https://microsoft.github.io/DirectX-Specs/d3d/archive/D3D11_3_FunctionalSpec.htm)). Il ne s'agit pas d'une tolérance ajoutée à un arrondi préalable. Le bleu de la régression admet analytiquement les seuls codes adjacents `{17, 18}`.

La couverture multiplie la source dans le fragment avant les facteurs fixed-function `One`/`InvSrcAlpha`. D3D11.3 §17.5 autorise une précision target-format mais ne fixe ni lattice ni schedule : l'oracle ferme donc le blend sur la grille RGBA8 minimale autorisée et F32, avec facteurs, produits et destination corrélée, plutôt qu'une enveloppe F32 seule. Les formes ordinaires/FMA, les erreurs F32 dirigées sur tout l'intervalle et FTZ restent incluses. Aucune mesure empirique ni seuil de similarité n'entre dans cette dérivation.

La première propagation des bornes officielles a rendu 13 anciennes fixtures multi-draw `Unbounded`. Une recherche déterministe par endpoints et grille fixed-point a retenu seulement des témoins à fond primaire opaque, couche blanche avec opacité/alpha Paint non triviaux, puis primaire opaque : ils préservent l'ordre, la capture et les mutations, tout en satisfaisant la règle stricte du singleton ou de deux codes adjacents. Un contre-exemple public en ordre inversé reste rejeté dans la preuve à trois Points. La combinaison fractionnaire 9/16 avec deux opacités et alpha Paint `253/255` reste explicitement `Unbounded`; le témoin 9/16 retenu garde les deux opacités mais un alpha Paint exact. Les conversions fixed-function peuvent élargir au-delà de deux codes l'enveloppe d'une scène arbitraire; ces fixtures sélectionnées ne prouvent pas une borne universelle. Un tel résultat reste `Unbounded`, jamais un succès assoupli. Aucun pipeline destination-read n'a été ajouté.

## Vérification

Vérification JVM du correctif global Task 8 et de sa continuation, fraîche et sérielle, sur la série `7dbaf8c` → `9aa924e5c` :

```bash
rtk proxy ./gradlew :gpu-plan:compileKotlin :gpu-renderer:compileKotlin :kanvas:compileKotlin --no-parallel --max-workers=1
rtk proxy ./gradlew :kanvas:test --tests '*W5aMaterialSurfacePixelTest' --tests '*GPUPlanSurfacePixelTest' --no-parallel --max-workers=1
rtk git diff --check
```

Résultat : les deux commandes Gradle sont `BUILD SUCCESSFUL`; 119 tests publics, 116 passés, 0 failure/error, 3 skips AA4 authentiques. Répartition : W5a 48 tests (47 passés, 1 skip); GPUPlan 71 tests (69 passés, 2 skips), avec les deux fixtures 512, W4e inverse, toutes les frames mixtes et les régressions admission/numérique. Les compteurs ci-dessus sont la source de vérification; les timestamps XML ne sont pas une source documentaire. `rtk git diff --check` est propre.

Skips exacts : `public mixed AA4 frame keeps a hard Path binary cover materialized only at color output` (`w4d.general.texture-sample-support-unavailable`), `W4e public Path AA4 uses only binary fixtures after its exact native capability boundary` et `W4e public mixed hard and Path AA4 inverse consumers keep distinct D24S8 domains` (tous deux `w4e.clip.sample-count-unavailable`).

La commande planifiée `:kanvas:jsNodeTest` est absente : `:kanvas` applique `buildsrc.convention.kotlin-jvm` et l'inventaire Gradle ne publie aucune tâche JS/Node. Aucun substitut de test d'infrastructure n'a été exécuté.

## Limites et suite

- Les trois skips AA4 restent attachés à l'indisponibilité native documentée; aucune capability ni réussite AA4 n'est simulée.
- SolidColor, Opacity et Paint sont immuables. La mutation publique observable porte sur Path, tableaux vertices et listes glyphs après capture.
- Les preuves numériques multi-draw concernent les entrées dont l'enveloppe indépendante officielle reste bornée à deux codes adjacents; les autres entrées restent explicitement `Unbounded`, sans affaiblissement de l'assertion ni prétention de conformance exhaustive de tous les backends.
- Les dépendances font se compilent transitivement, mais aucune suite font/codec/GM/dashboard/baseline/Skia/`jpg-color-cube` n'a été exécutée. Les fixtures text utilisent seulement des glyphs déjà résolus.
- Aucun test d'infrastructure n'a servi de preuve. Les deux re-reviews globales Sol indépendantes de Task 8 sont `READY`; la validation reste fondée sur la revue de production et les pixels publics autorisés.
- Le run public final émet aussi `Context leak detected, CoreAnalytics returned false`, sans failure/error ni correspondance dans les sources du repository; warning natif non attribué à un défaut du correctif, conservé explicitement dans le rapport plutôt que présenté comme absent.
- Une variante exploratoire non retenue, gradient Rect suivi de gradient RRect hard-edge, atteint legacy mais y rencontre `invalid.preflight.core_primitive_direct_geometry_resources` (uniform slab). Ce refus de ressources legacy distinct, suivi comme gap non bloquant, reste hors de ce correctif; la preuve retenue concerne la RRect seule demandée.
- W5b porte les blends communs; gradients, images, local matrices, filters, noise et runtime effects restent les tranches suivantes avec refus typés.
