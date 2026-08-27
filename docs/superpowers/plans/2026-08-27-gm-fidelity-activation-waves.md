# Plan — activation progressive de routes GM Skia non-font

## But et règles de passage

Cette série transforme les refus actuellement observés dans les GMs Skia en
routes Kanvas réellement supportées, une capacité limitée à la fois. Chaque
vague est isolée sur une branche dérivée de la précédente et donne lieu à une
PR séparée. Une vague n'est considérée comme réussie que si elle produit un
rendu natif WebGPU/offscreen, une référence CPU indépendante, une image de
référence et un diff/statistiques, les diagnostics de route et une politique
de refus stable pour les variantes hors périmètre. Une hausse de score sans
rendu natif ou obtenue en relâchant le seuil est invalide.

Contraintes communes à toutes les vagues :

- Le code et les tests sont la source de vérité ; les rapports vont sous
  `reports/gpu-renderer/evidence/`.
- Le backend reste WebGPU et headless/offscreen. Aucun portage Ganesh ou
  Graphite, aucun compilateur/VM SkSL dynamique et aucun retour de
  `gpu-renderer-scenes`.
- `SkRuntimeEffect` reste une façade de compatibilité : un effet n'est
  supporté que s'il possède un descripteur Kanvas enregistré, un comportement
  Kotlin/CPU et une implémentation WGSL validée par le parser.
- Une route doit conserver les budgets, validations d'entrée, diagnostics et
  statistiques existants. Les cas non couverts doivent être refusés avec un
  code stable et explicite.
- Ne pas modifier un GM pour contourner un refus. Toute modification d'un
  portage doit corriger une divergence sémantique démontrée.
- Les polices et codecs non livrés restent dependency-gated ; aucune solution
  provisoire n'est ajoutée pour faire passer une ligne historique.

## Préparation commune

Avant chaque vague : partir du commit de la branche précédente, lancer
`git fetch origin --prune`, vérifier que l'arbre est propre, puis consulter le
GM et le code de route concernés. Après chaque vague : lancer les tests ciblés,
`generateSkiaRenders` pour les GMs touchés, collecter les artefacts dans
`reports/gpu-renderer/evidence/`, comparer les scores sans réécrire les
seuils, committer, pousser et ouvrir une PR empilée. Une revue indépendante
doit statuer sur les preuves et les refus avant la PR.

## Task 1 / Vague 1 — formats bitmap additionnels

**Objectif.** Rendre utilisables les formats déjà exercés par
`AllBitmapConfigsGm` : `RGB_565`, `ARGB_4444`, `RGBA_F16` et `GRAY_8`, en les
convertissant explicitement vers le format préparé RGBA8 tout en conservant
alpha, color-space, dimensions, budget et hash d'artefact.

**Fichiers/interfaces.** Étendre `GPUPreparedImageSourceFormat` et
`GPUPreparedImageSource` dans `gpu-renderer/.../PreparedImageContracts.kt` et
`kanvas/.../GPUPreparedImageSource.kt`; remplacer les refus inconditionnels de
`GPUMaterialMapper.toPreparedImageMaterial` et `expandToPreparedRgba` par la
conversion bornée. Réutiliser `Bitmap.getPixel()` ou une conversion
équivalente testable, sans chemin codec implicite.

**Tests et preuves.** Mettre à jour `GPUPreparedImageSourceTest` et
`GPUMaterialMapperTest` pour les quatre formats et garder un cas de format
réellement inconnu refusé. Tester les six configurations dans
`AllBitmapConfigsGmTest`; régénérer `all_bitmap_configs`, `copyTo4444`,
`format4444` et `mipmap_gray8_srgb`; joindre référence CPU, PNG GPU, diff,
stats et diagnostics `unsupported.image.pixel.format` pour les formats hors
contrat.

**Passage.** Aucun changement de seuil ; les pixels des formats convertis
doivent être attestés par le rendu natif et l'oracle CPU.

## Task 2 / Vague 2 — image shader et sampling local borné

**Objectif.** Supporter le cas de matrice locale d'image utilisé par
`DrawimagerectFilterGm` : translation/scale affine finie, sampling nearest et
linear sur image préparée, sans repeat, perspective, mipmap ou codec nouveau.

**Fichiers/interfaces.** Compléter `GPUMaterialMapper` et la préparation du
matériel image pour propager la matrice locale et le filtre vers l'émission
WGSL existante. Ajouter des validations de bornes et un refus distinct pour
affine non supportée ou mode de tuile/sampling hors contrat.

**Tests et preuves.** Tests unitaires de mapping pour translation demi-pixel,
scale uniforme et modes nearest/linear, plus refus repeat/perspective. Test
`DrawimagerectFilterGmTest`; régénérer `drawimagerect_filter` et un cas
nearest/alpha représentatif. Capturer CPU/GPU/diff/stats/route.

## Task 3 / Vague 3 — gradient linéaire à deux arrêts

**Objectif.** Activer une route WGSL pour un gradient linéaire clampé à deux
  arrêts, couleur premultiplied, avec matrice locale affine bornée.

**Fichiers/interfaces.** Implémenter le descripteur de matériau gradient dans
  `GPUMaterialMapper` et son émission WGSL/packing dans le pipeline existant.
  Refuser explicitement tile modes complexes, nombre d'arrêts non supporté,
  perspective et paramètres non finis.

**Tests et preuves.** Tests de packing et de calcul CPU de référence ; cas
  rendu et cas refusé. Régénérer `linear_gradient`, `fillrect_gradient` et
  `gradient_matrix` uniquement si la route les couvre réellement ; comparer
  les pixels et conserver les diagnostics.

## Task 4 / Vague 4 — strokes simples

**Objectif.** Lever un seul sous-ensemble déterministe de strokes : largeur
  positive finie, ligne/path sans dash ni path-effect, cap butt ou square,
  join miter, couverture sans AA scalaire non promue.

**Fichiers/interfaces.** Étendre le lowering stroke et le plan de couverture
  dans `gpu-renderer` sans augmenter globalement les budgets. Garder les
  refus pour round cap/join, dash, hairline et géométrie hors budget.

**Tests et preuves.** Commencer par un GM représentatif simple et ses variantes
  refusées ; oracle CPU, rendu WebGPU, diff/statistiques et route diagnostics.
  N'activer `strokedline_caps`, `strokes_round` ou `dashcircle` que si leur
  périmètre exact est effectivement couvert.

## Task 5 / Vague 5 — anti-aliasing déterministe de primitives minces

**Objectif.** Supporter la couverture analytique bornée des rect/oval/rrect
  simples avec AA, sans fan stencil ni promotion scalaire implicite.

**Fichiers/interfaces.** Ajouter le cas vectoriel/coverage dans les plans
  Geometry/Coverage et l'émission WGSL, avec budget explicite et refus pour
  les formes trop fines ou trop complexes.

**Tests et preuves.** Tests de couverture CPU/GPU sur `thinrects`,
  `thinroundrects` et un `rrect_clip_*` représentatif ; régénérer seulement les
  GMs attestés et conserver les refus.

## Task 6 / Vague 6 — clip path courbe compositionnel

**Objectif.** Supporter un clip path cubique borné avec opérations
  `Intersect` et `Difference`, winding/even-odd explicitement définis, dans le
  budget d'arêtes établi.

**Fichiers/interfaces.** Compléter le lowering clip et le plan de coverage
  (CPU spans + stencil-cover WebGPU si déjà présent). Aucun chemin générique
  illimité ; inverse clips, perspective et budgets dépassés restent refusés.

**Tests et preuves.** Oracle indépendant et tests sur `clippedcubic`/`clipcubic`
  quand le cas respecte le budget ; cas `Difference`, winding et budget refusé
  séparés ; artefacts complets dans le répertoire d'évidence.

## Task 7 / Vague 7 — filtres simples et DAG borné

**Objectif.** Activer uniquement les filtres dont le DAG est déjà représentable
  (blur/offset rect borné), avec ordre des opérations et quick-reject définis.

**Fichiers/interfaces.** Ajouter les descripteurs de filtre dans le plan de
  rendu existant, vérifier les dépendances d'image et la taille de kernel,
  préserver les refus pour hairline, kernel excessif et source absente.

**Tests et preuves.** Cas `blurrects`, `offsetimagefilter` et refus
  `blurquickreject` si le stroke préalable n'est pas encore couvert ; CPU/GPU
  indépendants, diff/stats et diagnostics.

## Task 8 / Vague 8 — compositing et blend modes communs

**Objectif.** Stabiliser `saveLayer` borné et les blend modes courants (SRC,
  SRC_OVER) avec isolation et opacity correctes.

**Fichiers/interfaces.** Compléter le plan de compositing WebGPU et les
  allocations de cible ; ne pas contourner les limites d'aliasing ou de
  budget. Les modes non implémentés restent refusés.

**Tests et preuves.** Tests `srcmode`, saveLayer opacity/isolation, et
  `rasterallocator` seulement après disponibilité de la couverture AA ; oracle
  CPU, rendu GPU et preuves de mémoire/route.

## Task 9 / Vague 9 — color filters et color-space explicite

**Objectif.** Ajouter un color filter matriciel ou un sous-ensemble équivalent
  et les conversions sRGB documentées, sans supposer un codec absent.

**Fichiers/interfaces.** Descripteurs Kotlin/CPU + WGSL validés, propagation du
  color-space dans le matériau et refus stable des filtres non représentables.

**Tests et preuves.** `colorfilterimagefilter`, `srgb_colorfilter` et
  `colorspace` selon leur périmètre ; comparer valeurs CPU/GPU et conserver
  les cas refusés.

## Task 10 / Vague 10 — texte/glyphes limité à la police livrée

**Objectif.** Ne traiter que les GMs utilisant une police disponible dans le
  dépôt et un chemin glyphique déjà livré ; aucune promesse générale de font.

**Fichiers/interfaces.** Vérifier le chargement de la police, le mapping
  glyph-run et les transformations autorisées ; sinon laisser le refus
  dependency-gated explicite.

**Tests et preuves.** `gradtext`, `text_scale_skew`, `fontscaler` uniquement si
  la police et l'oracle sont disponibles, avec preuves complètes ; sinon
  rapporter le blocage de dépendance sans changement artificiel.

## Task 11 / Vague 11 — runtime effects enregistrés

**Objectif.** Ajouter un ou deux effets enregistrés (par exemple
  `lineargradientrt` ou `runtimecolorfilter`) avec descripteur Kanvas,
  comportement Kotlin/CPU et WGSL parser-validé.

**Fichiers/interfaces.** Registre des runtime effects, validation wgsl4k,
  packing des uniforms et diagnostics. Aucun parsing/compilation dynamique de
  SkSL ; ouvrir un ticket wgsl4k si le parser est ambigu plutôt qu'ajouter un
  workaround caché.

**Tests et preuves.** Tests CPU/GPU du descripteur, cas rendu, cas refusé pour
  effet non enregistré et artefacts des GMs correspondants. Promotion seulement
  après revue Sol et vérification native.

## Gestion des PR empilées

Chaque vague est développée par un agent distinct, revue par un autre agent,
 puis poussée sur une branche `codex/gm-activation-wave-N` basée sur la branche
 précédente. La PR suivante est créée depuis la PR précédente afin de garder
 une pile lisible. En cas de bug, lancer une investigation/correction ciblée
 dans la vague courante ; en cas de feature manquante, faire implémenter le
 sous-ensemble borné par un agent adapté. Arrêter seulement pour un échec
 natif reproductible, un correctif de production nécessaire non résolu ou un
 finding Sol confirmé.
