# État W05 — material graph, W5a Solid/Opacity

Révision vérifiée : correction Task 7 « authenticate material-only refusal ownership », sur `328a3366c8e70705333fd139c7c2b40c6cf104d9`, le 10 septembre 2026. Cette entrée décrit le code du commit correctif qui la contient, et non l'ancienne tentative de conversion Rect/RRect en Path. Les reviews indépendantes de Task 8 restent à effectuer.

## Gates publiques W5a

W5a ferme `Transparent`, `Solid` et `Opacity` sous `SRC_OVER`. Chaque draw promu porte une `MaterialV1` vers une table immuable. La frontière W3 différée a été vérifiée dans le lowerer : la capability historique exige table `null` et uniquement `LegacyColorV1`; `W5A_CAPABILITY_ID` exige une table présente et uniquement `MaterialV1`. Les formes hybrides sont refusées.

| Cellule publique | Preuve retenue |
| --- | --- |
| Rect hard-edge / fractional | fixtures nested-opacity et fractional Rect (Tasks 1–2) |
| RRect analytique | fixture fractional RRect et observation d'une couverture exacte 0.75 dans les frames mixtes |
| Path fill direct / stencil cover | fixtures Path (Task 3), plus chacune des deux variantes de frame mixte |
| Path stroke / hairline | fixtures publiques stroke et hairline (Task 3) |
| Point / Points | trois commandes, points multiples et hairline (Task 4) |
| Text pré-résolu | fixtures A8 et mutation d'une liste de glyphs déjà résolus (Task 5), sans génération de font |
| Vertices, avec/sans couleurs vertex | fixtures Picture et mutation des tableaux publics (Task 6) |
| Frame Rect + RRect + Path | `Picture playback composes planned Rect RRect and Path bindings in recorded order` : trois bindings distincts, composition indépendante, observation AA, puis comparaison de tous les bytes après mutation du Path |
| Ordre intercalé et stencil | `native mixed stencil frame preserves interleaved Rect bindings and captured mutation` : Rect → RRect → Path stencil-cover → Rect, réutilisation d'un binding et comparaison de tous les bytes après mutation |
| Opacité identique, children distincts | `native mixed equal opacity preserves distinct Solid children and nested chains` : Rect rouge et RRect bleu à opacité 0.5, puis Path rouge et Rect bleu avec chaînes shader/Paint imbriquées et children réutilisés non adjacents; quatre observations pixel indépendantes |
| Refus puis récupération | `public W5b gradient refusal leaves the runtime able to render a later W5a frame` : W5a valide → gradient refusé `unsupported.material.w5a.kind` → W5a valide, sans dispose entre les trois frames, et pixels avant/après identiques |
| Absence d'ownership W5a | `hard edge gradient RRect outside W5a retains legacy pixels after caller stop mutation` : RRect hard-edge hors admission W4b, gradient rouge/bleu capturé en Picture, mutation des stops vers vert, pixels legacy rouge/bleu conservés |

## Composition native Task 7

La capability distincte `w5a-native-rect-rrect-path-composite-v1` est sélectionnée après les capabilities standalone existantes. Elle partitionne les commandes en runs natifs ordonnés, conserve leurs indices publics et confie Rect à W3, RRect analytique à W4b, Path fill à W4c. W4c n'accepte plus de conversion Rect/RRect en Path.

`W5aCompositePlanV1` possède les graphs de lanes, la table material internée et les réservations communes. Les refs locales sont remappées exactement dans la table de frame; la copie des draws conserve les faits de géométrie/raster sans reconstruction sémantique. Le graph composite utilise cette représentation hiérarchique typée, sans fabriquer une topologie standalone.

Correction d'interning : la clé d'une entrée inclut son child canonique et donc toute sa chaîne de bindings accessible, pas seulement sa structure et son alpha local. Puisque V1 évalue le child à `ref - 1`, toute nouvelle chaîne dont le child réutilisé n'est pas adjacent est copiée contiguë avant son parent. Les remaps canoniques restent déterministes, les bindings copiés défensivement et la borne de 2048 entrées contrôlée avant chaque ajout. Aucun changement de l'évaluateur ou des contrats standalone. La preuve RED a produit du rouge dans le pixel bleu (`channel=0 observed=188 expected=[0]`); elle passe après correction, y compris pour les chaînes imbriquées.

Correction de l'ownership des refus : les compilers natifs conservent séparément leurs refus de material et continuent les contrôles de géométrie, couverture, clip, état, provenance et limites sémantiques de toute la scène. Seule leur réussite complète permet d'émettre `GpuPlanSelection.MaterialOnlyRefusal`, lié à la scène, à la cible et à la capability. Les seams W4e et composite propagent ce résultat après leur propre admission; le backend vérifie l'identité scène/cible. Un `GapNotMigrated` ordinaire reste legacy : le routeur ne rescane plus les shaders et ne transforme plus des diagnostics accumulés en ownership. La nouvelle preuve RED échouait sur le faux terminal `unsupported.material.w5a.kind`; elle est GREEN sans modifier la géométrie ni substituer un material.

Le lowerer réutilise chaque lowerer et assembler natif avec un witness composite explicite. Il conserve les enveloppes d'origine pour le preflight exact, puis transporte seulement les ranges scellés vers les indices de la frame. Un witness de frame vérifie cible, readback, préparations, ordre, packets, états raster, dépendances et budget. La frame prépare une seule cible et un seul staging, efface au premier rendu puis charge l'attachement, et conserve les paires stencil atomiques.

Toutes les capacités V/I/U arrondies, y compris le scratch Rect W3 de cette nouvelle composition, et les leases D24S8 sont comptées avant l'allocation. Un pool de session dédié aux lanes composites utilise la factory native existante avec la borne de 512 lanes; le pool standalone à trois slots reste inchangé. Le materializer réutilise les implémentations natives W3/W4b/W4c, partage un seul buffer readback, rassemble leurs leases sous un lifecycle commun et retient les journals de rollback si le nettoyage doit être retenté.

Les premières preuves RED ont révélé les anciennes exigences « toute la frame appartient à une seule lane », puis la limite des trois slots et la priorité de sélection devant W4b à 512 draws. Les corrections ajoutent une autorité composite et une gestion propre des ressources; elles ne relâchent pas les enveloppes standalone.

## Audit des compilations alternatives Solid/Opacity

| Site de production | Décision W5a |
| --- | --- |
| compilers W3/W4a/W4b/W4c/W4d/W4e | `EffectiveMaterialPlanner` produit table/ref sur W5a; l'adaptateur historique conserve seulement `LegacyColorV1` |
| lowerers natifs | évaluation de la table scellée; aucune couleur legacy concurrente |
| composite Task 7 | interning structure + bindings + chaîne child canonique et remap exact, adjacency V1 conservée; aucune nouvelle évaluation du shader ni conversion de géométrie |
| bridges points/text/vertices | capture commune puis `compileW5a*`; les lanes non promues conservent leur entrée générique |
| `GPUMaterialMapper` | Opacity legacy reste un refus `OPACITY_CHILD`; aucun aplatissement Solid après sélection W5a |
| images, glyphs couleur, mesh et dispatchs legacy restants | hors promotion W5a de ces routes; retrait global reporté aux tranches concernées |

Cet audit porte sur le code de production. Aucun test de source shape, réflexion, détails privés, compteurs ou nombre d'appels n'a été ajouté.

## Vérification

Commande finale, sérielle :

```bash
rtk ./gradlew :gpu-plan:compileKotlin :gpu-renderer:compileKotlin :kanvas:compileTestKotlin :kanvas:test --tests 'org.graphiks.kanvas.surface.W5aMaterialSurfacePixelTest' --tests 'org.graphiks.kanvas.surface.GPUPlanSurfacePixelTest' --no-parallel
```

Résultat : `BUILD SUCCESSFUL`, 104 tests, 101 passés, 0 failure/error, 3 skips AA4 authentiques. Répartition : W5a 33 tests (32 passés, 1 skip); GPUPlan 71 tests (69 passés, 2 skips), incluant les régressions publiques standalone W3/W4a/W4b/W4c et le cas W4b à 512 draws. La nouvelle preuve legacy et la récupération W5a ont aussi été exécutées séparément : 2/2 passées. Les trois frames mixtes restent vertes dans la suite complète. `rtk git diff --check` est propre.

## Limites et suite

- Les trois skips AA4 restent attachés à l'indisponibilité native documentée; aucune capability ni réussite AA4 n'est simulée.
- SolidColor, Opacity et Paint sont immuables. La mutation publique observable porte sur Path, tableaux vertices et listes glyphs après capture.
- Les dépendances font se compilent transitivement, mais aucune suite font/codec/GM/dashboard/baseline/Skia/`jpg-color-cube` n'a été exécutée. Les fixtures text utilisent seulement des glyphs déjà résolus.
- Aucun test d'infrastructure n'a servi de preuve. La vérification Task 8 et ses reviews Sol indépendantes restent distinctes de cette implémentation.
- Une variante exploratoire non retenue, gradient Rect suivi de gradient RRect hard-edge, atteint legacy mais y rencontre `invalid.preflight.core_primitive_direct_geometry_resources` (uniform slab). Ce refus de ressources legacy distinct reste hors de ce correctif; la preuve retenue concerne la RRect seule demandée.
- W5b porte les blends communs; gradients, images, local matrices, filters, noise et runtime effects restent les tranches suivantes avec refus typés.
