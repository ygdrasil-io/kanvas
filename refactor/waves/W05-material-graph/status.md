# État W05 — material graph, W5a Solid/Opacity

Révision de production vérifiée : `39ff21985bd1407958d3ba1e909a74bbedf50010` (« execute sealed W5a source DAG across authentic lanes »), le 10 septembre 2026, correctif global sur `e0b1f39ce23a8badbd10074eb308908a26725280`. Les cinq findings Important et les minors des deux reviews globales sont traités dans cette vague. Les reviews spec et qualité de Task 7 étaient `READY`; les deux re-reviews globales Task 8 restent en attente. Les tests verts ne constituent pas leur approbation et W5a n'est pas déclarée globalement close.

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
| Bornes publiques | 512 runs natifs rendus, 513 refusés précocement puis récupération sur la même Surface; 683 matériaux point identiques dédupliqués, 683 chaînes distinctes de trois entrées refusées à la vraie borne 2048 |
| Admission avant interning | 683 candidats Vertices avec transform non finie gardent `unsupported.vertices.transform`, sans épuiser une table qu'ils ne sont pas admis à rejoindre |
| Frame Rect + RRect + Path | `Picture playback composes planned Rect RRect and Path bindings in recorded order` : trois bindings distincts, composition indépendante, observation AA, puis comparaison de tous les bytes après mutation du Path |
| Ordre intercalé et stencil | `native mixed stencil frame preserves interleaved Rect bindings and captured mutation` : Rect → RRect → Path stencil-cover → Rect, réutilisation d'un binding et comparaison de tous les bytes après mutation |
| Opacité identique, children distincts | `native mixed equal opacity preserves distinct Solid children and nested chains` : Rect rouge et RRect bleu à opacité 0.5, puis Path rouge et Rect bleu avec chaînes shader/Paint imbriquées et children réutilisés non adjacents; quatre observations pixel indépendantes |
| Refus puis récupération | `public W5b gradient refusal leaves the runtime able to render a later W5a frame` : W5a valide → gradient refusé `unsupported.material.w5a.kind` → W5a valide, sans dispose entre les trois frames, et pixels avant/après identiques |
| Absence d'ownership W5a | `hard edge gradient RRect outside W5a retains legacy pixels after caller stop mutation` : RRect hard-edge hors admission W4b, gradient rouge/bleu capturé en Picture, mutation des stops vers vert, pixels legacy rouge/bleu conservés |

## Composition native Task 7

La capability distincte `w5a-native-rect-rrect-path-composite-v1` est sélectionnée après les capabilities standalone existantes. Elle partitionne les commandes en runs natifs ordonnés, conserve leurs indices publics et confie Rect à W3, RRect analytique à W4b, Path fill à W4c et stroke/hairline à W4d. Le choix Path suit aussi les sémantiques de paint et l'admission native, pas la seule classe de géométrie. W4c n'accepte plus de conversion Rect/RRect en Path. La borne de 512 runs est vérifiée immédiatement après l'inventaire linéaire et avant toute `SceneSnapshot` par lane, y compris si une source material ultérieure serait refusée.

`W5aCompositePlanV1` possède les graphs de lanes, la table material internée et les réservations communes. Les refs locales sont remappées exactement dans la table de frame; la copie des draws conserve les faits de géométrie/raster sans reconstruction sémantique. Le graph composite utilise cette représentation hiérarchique typée, sans fabriquer une topologie standalone.

Correction d'interning : la clé d'une entrée inclut son child canonique et donc toute sa chaîne de bindings accessible, pas seulement sa structure et son alpha local. Puisque V1 désigne le child à `ref - 1`, toute nouvelle chaîne dont le child réutilisé n'est pas adjacent est copiée contiguë avant son parent. Les remaps canoniques restent déterministes, les bindings copiés défensivement et la borne de 2048 entrées contrôlée avant chaque ajout. La preuve RED Task 7 a produit du rouge dans le pixel bleu (`channel=0 observed=188 expected=[0]`); elle reste verte avec les chaînes imbriquées et le nouveau fragment.

Le registre prepared de frame interne également les sources Rect/RRect/Path/Points et les seules admissions authentiques A8 Text et Vertices/Mesh sans programme. Text et Vertices sont abaissés une fois par leurs lowerers existants; les émissions sont ensuite remappées sur la table commune en authentifiant l'identité exacte du source-stage. La préparation des atlas, inventaires et artifacts n'est pas rejouée. Les glyphs couleur/non-A8 et les géométries refusées n'acquièrent pas d'ownership W5a.

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

## Vérification

Vérification JVM du correctif global Task 8, fraîche et sérielle, sur le contenu de `39ff219` :

```bash
rtk proxy ./gradlew :gpu-plan:compileKotlin :gpu-renderer:compileKotlin :kanvas:compileKotlin --no-parallel --max-workers=1
rtk proxy ./gradlew :kanvas:test --tests '*W5aMaterialSurfacePixelTest' --tests '*GPUPlanSurfacePixelTest' --no-parallel --max-workers=1
rtk git diff --check
```

Résultat : les deux commandes Gradle sont `BUILD SUCCESSFUL`; 117 tests publics, 114 passés, 0 failure/error, 3 skips AA4 authentiques. Répartition : W5a 46 tests (45 passés, 1 skip); GPUPlan 71 tests (69 passés, 2 skips), avec les deux fixtures 512, W4e inverse et toutes les nouvelles frames mixtes. Les XML finaux sont datés du 10 septembre 2026, 16:39:44 UTC (GPUPlan) et 16:39:48 UTC (W5a). `rtk git diff --check` est propre.

Skips exacts : `public mixed AA4 frame keeps a hard Path binary cover materialized only at color output` (`w4d.general.texture-sample-support-unavailable`), `W4e public Path AA4 uses only binary fixtures after its exact native capability boundary` et `W4e public mixed hard and Path AA4 inverse consumers keep distinct D24S8 domains` (tous deux `w4e.clip.sample-count-unavailable`).

La commande planifiée `:kanvas:jsNodeTest` est absente : `:kanvas` applique `buildsrc.convention.kotlin-jvm` et l'inventaire Gradle ne publie aucune tâche JS/Node. Aucun substitut de test d'infrastructure n'a été exécuté.

## Limites et suite

- Les trois skips AA4 restent attachés à l'indisponibilité native documentée; aucune capability ni réussite AA4 n'est simulée.
- SolidColor, Opacity et Paint sont immuables. La mutation publique observable porte sur Path, tableaux vertices et listes glyphs après capture.
- Les dépendances font se compilent transitivement, mais aucune suite font/codec/GM/dashboard/baseline/Skia/`jpg-color-cube` n'a été exécutée. Les fixtures text utilisent seulement des glyphs déjà résolus.
- Aucun test d'infrastructure n'a servi de preuve. Les deux re-reviews globales Sol indépendantes de Task 8 restent en attente après cette correction.
- Le run public final émet aussi `Context leak detected, CoreAnalytics returned false`, sans failure/error ni correspondance dans les sources du repository; warning natif non attribué à un défaut du correctif, conservé explicitement dans le rapport plutôt que présenté comme absent.
- Une variante exploratoire non retenue, gradient Rect suivi de gradient RRect hard-edge, atteint legacy mais y rencontre `invalid.preflight.core_primitive_direct_geometry_resources` (uniform slab). Ce refus de ressources legacy distinct, suivi comme gap non bloquant, reste hors de ce correctif; la preuve retenue concerne la RRect seule demandée.
- W5b porte les blends communs; gradients, images, local matrices, filters, noise et runtime effects restent les tranches suivantes avec refus typés.
