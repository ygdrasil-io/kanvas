# Route texte préparée pour `Surface` — conception FP-05

**Date :** 2026-07-28  
**Statut :** conception approuvée

## Objectif

FP-05 migre les opérations texte et glyphes de `Surface` vers la route de
frame préparée commune. À la fermeture de FP-05 :

- `DrawText` ne produit plus `legacy.surface.prepared.family.text` ;
- les glyphes A8 et les glyphes couleur pris en charge utilisent un ordre
  explicite upload-avant-échantillonnage ;
- les pixels, l'alpha du paint, l'atlas et l'ownership natif sont prouvés ;
- `Text` ne fait plus partie de l'allowlist du renderer immédiat.

La migration ne porte ni Ganesh ni Graphite. WebGPU reste l'unique backend
GPU, le WGSL reste statique et parser-validé, et l'animation reste inchangée.

## Sources d'autorité

Les sources s'appliquent dans cet ordre :

1. `AGENTS.md` et les cibles d'architecture actives ;
2. `reports/upstream-rebaseline/graphite-dawn-frame-plan/active-todo.md`,
   qui fixe l'acceptance FP-05 ;
3. `.upstream/specs/font/README.md` et ses specs liées, qui possèdent les
   frontières font, glyphes, shaping, couleur et emoji ;
4. le code et les tests courants, qui prouvent l'état réel sans réduire la
   cible ;
5. le source Skia `defc3a5a92966c32cb2a6a901e2fa3036a13bb8a`, utilisé comme
   référence technique Graphite+Dawn bornée.

Les invariants Graphite retenus proviennent notamment de :

- `src/text/gpu/SubRunContainer.cpp` : sélection de représentation et
  sous-runs ordonnés ;
- `src/gpu/graphite/text/GlyphData.cpp` : séparation entre identité du glyphe,
  résidence atlas et génération ;
- `src/gpu/graphite/text/TextAtlasManager.{h,cpp}` : atlas borné, upload,
  use tokens et génération monotone ;
- `src/gpu/graphite/DrawContext.cpp` et `Device.cpp` : upload task avant le
  render pass consommateur ;
- `src/gpu/graphite/render/BitmapTextRenderStep.cpp` : distinction entre
  couverture A8/LCD et couleur primitive ;
- `src/gpu/graphite/CommandBuffer.cpp`,
  `src/gpu/graphite/GpuWorkSubmission.cpp` et
  `src/gpu/graphite/dawn/DawnQueueManager.cpp` : ressources retenues jusqu'à
  la completion Dawn.

Ces références n'autorisent pas à reproduire les abstractions multi-backends
de Graphite.

## État initial et écarts

Le chemin produit texte n'appartient pas encore à la frame préparée commune :

- `GPUPreparedSurfaceFrameGate` renvoie `DrawText` vers le renderer legacy ;
- `GPULegacyImmediatePathAdapter` contient encore la famille `Text` ;
- `TextBridge` rasterise et construit un atlas indépendant par `GpuTextBlob` ;
- `GPUOpMapper` construit un `DrawTextRun` avec des bounds approximatives,
  un identifiant dérivé de `hashCode()` et aucune ressource préparée ;
- le builder Surface commun accepte uniquement `CorePrimitive` et
  `SampledImage` ;
- la route préparée ColorGlyph est isolée et ne sait pas partager une frame
  avec Core, Image et texte A8.

Les problèmes techniques à corriger sont :

- les deux rasterizers A8 actuels produisent seulement `0` ou `255`, sans
  couverture antialiasée en niveaux de gris ;
- le shader A8 legacy ne multiplie pas RGB par
  `paintAlpha × coverage`, ce qui viole le contrat prémultiplié ;
- l'identité publique de fonte ne distingue pas sûrement deux contenus de
  fonte ;
- deux formats incompatibles représentent actuellement la génération atlas ;
- `TextBridge` transforme certaines erreurs glyph-locales en rectangles vides
  au lieu de produire un refus stable ;
- les anciennes clés simples, les contrats riches `font/glyph` et
  `font/gpu-api`, `TextBridge` et les planners renderer créent plusieurs
  autorités concurrentes.

Le shader legacy et le rendu legacy ne sont donc pas des oracles de fidélité.
La section « Current WebGPU Text Evidence » de
`.upstream/specs/font/04-glyph-rendering-and-coverage.md` décrit également un
ancien état outline et cite des classes de test absentes du source courant.
Ses frontières font/glyph restent applicables, mais cette section ne constitue
pas une preuve runtime actuelle.

## Décision d'architecture

FP-05 utilise un atlas immuable, déterministe et borné par frame. Tous les
`DrawText` d'une même frame partagent l'inventaire et les pages nécessaires.
Les sous-runs restent dans l'ordre de dessin original.

FP-05 ne crée pas le gestionnaire d'atlas persistant de Graphite. FP-09
ajoutera la résidence GPU inter-frame, la réutilisation, l'éviction, la
compaction et les compteurs de cache. FP-05 fournit dès maintenant les clés,
générations, ownerships et frontières nécessaires à cette évolution.

Les alternatives sont rejetées :

- un atlas par `DrawText` recréerait textures et bindings, empêcherait la
  déduplication de frame et compliquerait les lifetimes ;
- un mini-Graphite persistant dans FP-05 dupliquerait FP-09 et introduirait des
  abstractions inutiles ;
- envelopper le renderer immédiat ne prouverait ni task graph, ni ownership,
  ni refus avant allocation.

## Frontières simplifiées pour le mono-backend

Kanvas ne crée pas d'équivalent à :

- `Recorder` ;
- `Device` et `DrawContext` comme couches séparées ;
- `TextureProxy` ;
- `ResourceProvider` polymorphe ;
- une hiérarchie multi-backends `Caps` ;
- un compilateur SkSL ;
- quatre atlases systématiquement liés pour chaque draw.

Les capacités WebGPU nécessaires restent décrites par les contrats
`GPUCapabilities` et les limites du device. Les handles wgpu4k sont créés
tardivement par le materializer existant et restent absents du frame plan.

Si un comportement de wgpu4k est ambigu ou incorrect, l'implémentation doit
produire une reproduction minimale et le signaler au projet wgpu4k. Elle ne
doit pas ajouter un workaround Kanvas caché.

## Composants

### Autorité font/glyph

`font/glyph` et `font/gpu-api` deviennent les autorités canoniques pour :

- l'identité de face dérivée du contenu et de la provenance ;
- le face index, les variations et la palette ;
- la clé de strike et la clé de masque ;
- l'extraction de l'outline, les métriques, bearings et bounds ;
- la rasterisation A8 réellement antialiasée ;
- les bytes immuables du masque ;
- les diagnostics et refus font/glyphe ;
- la génération atlas typée.

La génération est un entier non négatif porté par un type dédié et sérialisé
comme un champ numérique canonique. Elle n'est jamais un `String` libre avec
ou sans préfixe. L'autorité texte attribue une génération à chaque inventaire
FP-05 ; frame plan, artefacts et materializer doivent retenir exactement la
même valeur. Comme l'inventaire FP-05 est immuable, sa génération ne change
pas pendant la frame. FP-09 rendra ce compteur monotone entre frames et
l'incrémentera lors d'un remplacement ou d'une éviction.

Une clé de masque inclut au minimum :

- identité exacte de la fonte et face index ;
- coordonnées de variation ;
- glyph ID ;
- taille et quantification subpixel ;
- classe de représentation ;
- paramètres de rasterisation et d'antialiasing ;
- mask filter et padding lorsqu'ils modifient le masque ;
- palette lorsque la représentation l'exige.

Les positions atlas et les handles natifs ne font pas partie de cette identité.

### `GPUPreparedTextLowerer`

Le lowerer est pur et transactionnel. Il reçoit un `DisplayOp.DrawText` avec
son état exact et produit soit un draw texte préparé, soit un refus typé.

Le résultat accepté conserve :

- glyph IDs et positions exactes ;
- origine, transform, clip et bounds exactes ;
- paint, matière, alpha, blend et ordre ;
- identité de fonte et références d'artefacts ;
- choix explicite de représentation ;
- provenance et diagnostics.

Le lowerer n'alloue aucune ressource WebGPU, ne crée aucun shader et ne décide
pas d'un fallback legacy.

### `PreparedTextFrameInventory`

L'inventaire parcourt tous les draws texte dans l'ordre et :

1. résout les représentations ;
2. déduplique les masques par clé exacte ;
3. construit un petit ensemble borné de pages R8 immuables ;
4. calcule placements, UV, bounds et padding ;
5. forme des sous-runs compatibles ;
6. reçoit de l'autorité texte une génération typée unique ;
7. conserve l'ordre observable Core/Text/Image.

L'inventaire ne contient aucun handle WebGPU. Il refuse toute la frame avant
allocation native si les dimensions, identités, contenus ou budgets sont
incohérents.

### Payloads texte

La somme fermée `GPUDrawSemanticPayload` reçoit deux sémantiques :

- `TextA8` : la texture R8 fournit une couverture qui module le paint ;
- `ColorGlyph` : les couches couleur fournissent une couleur primitive.

Les deux routes partagent les primitives d'upload R8, les budgets et
l'ownership, mais gardent des ABI et shaders distincts.

### Builder, preflight et materializer communs

Le builder `Surface` existant est étendu ; aucun renderer texte parallèle
n'est ajouté. Il :

- ajoute les tâches d'upload des pages avant leurs consommateurs ;
- construit une seule frame hétérogène ordonnée ;
- partage une page entre tous les sous-runs compatibles ;
- conserve un seul submit et un seul readback lorsqu'il est demandé.

Le preflight pur valide toute la topologie, les générations, les budgets,
l'ABI WGSL, les bindings et l'ordre avant la première allocation native.

Le materializer crée textures, vues, samplers, buffers d'instances, uniforms
et bind groups, puis réordonne seulement les operands selon le plan déjà
scellé. Les ressources frame-locales sont
`PayloadOwnedCompletion`; les ressources de session sont `Borrowed`; le
staging de readback est `OutputOwnedReadback`.

## Flux de données

```text
DisplayOp.DrawText
  -> GPUPreparedTextLowerer
  -> résolution et rasterisation font/glyph
  -> PreparedTextFrameInventory
  -> payloads TextA8 / ColorGlyph / expansion path pour stroke
  -> task list Core + Image + Text
  -> preflight pur
  -> matérialisation WebGPU
  -> uploads
  -> render dans l'ordre exact
  -> un submit
  -> completion et libération
```

Les opérations logiques qui produisent plusieurs sous-runs ou paths sont
validées entièrement avant d'enregistrer le premier packet.

## Sémantique du paint

### Texte A8

Le masque A8 est une couverture linéaire, jamais une couleur sRGB. Le shader
évalue d'abord la matière commune, puis applique prémultiplication, alpha du
paint et couverture exactement une fois :

```text
paintStraightLinear = evaluatePreparedMaterial(localCoordinates)
paintPremulLinear = premultiply(paintStraightLinear, paintAlpha)
sourcePremulLinear = paintPremulLinear * glyphCoverage
output = blendPrepared(sourcePremulLinear, destination)
```

Le texte A8 accepte toute matière déjà prouvée par l'autorité paint commune :

- couleur solide ;
- gradients linéaire, radial, sweep et conical admis ;
- blend shaders admis ;
- runtime effects enregistrés avec Kotlin/CPU et WGSL parser-validé ;
- image shaders dont le sampling et l'ownership sont déjà acceptés.

Une matière non admise par le pipeline commun produit le même code canonique
que pour les autres géométries. Aucune liste texte concurrente n'est créée.

### Glyphes couleur

COLRv0 conserve l'ordre des layers et les couleurs CPAL. L'entrée foreground
utilise la couleur de foreground prévue par le contrat font. Comme Graphite
pour une primitive couleur, un shader de paint ne remplace pas la couleur du
glyphe. L'alpha du paint et les transformations couleur admises sont appliqués
exactement une fois.

COLRv0 et A8 ne partagent donc pas le même fragment shader, même si leurs
layers peuvent partager le format physique R8.

### Blend

Le texte réutilise exclusivement l'autorité blend commune. `SRC_OVER` fait
partie des preuves obligatoires ; tout autre mode déjà admis est accepté sans
liste spécifique au texte. Un mode qui nécessite une destination read non
prouvée reste un refus canonique, jamais un remplacement silencieux par
`SRC_OVER`.

## Stroke et filters

### Stroke

Un stroke n'est pas simulé par une dilation du masque A8. Le lowerer transforme
les glyph outlines en géométrie path préparée, conserve la provenance texte et
réutilise l'autorité stroke commune pour width, cap, join, miter et dash.

Les styles admis par cette autorité sont inclus dans FP-05. Un style non admis
produit son refus canonique. Cette route path peut être moins rapide que
l'atlas ; les métriques distinguent explicitement les deux routes.

### Mask filter

Le blur mask filter admis devient un paramètre de la clé de masque. Le
rasterizer génère une couverture filtrée avec padding et bounds exactes avant
la coloration. Sigma, style, transform de rasterisation et padding participent
à l'identité.

Les autres mask filters sont admis seulement lorsqu'une autorité commune les
décrit et les prouve ; le texte ne crée pas un moteur de filtres concurrent.

### Image filter

Un image filter agit sur le résultat complet du draw et exige une cible
intermédiaire puis une composition. FP-05 prépare la source texte, mais FP-07
possède l'offscreen, le filter plan et la composition. Jusqu'à cette
intégration, une opération texte portant un image filter produit un refus
terminal ; elle ne revient pas au renderer legacy.

## Emoji et glyphes couleur

Le mot « emoji » n'est pas un motif de refus. FP-05 accepte :

- les glyphes emoji monochromes représentables en A8 ;
- les glyphes COLRv0/CPAL prouvés ;
- tout emoji déjà shapé et positionné dont le glyph ID possède une
  représentation A8 ou COLRv0.

Le renderer ne re-shape pas une séquence. Une séquence ZWJ déjà convertie en
glyph ID est traitée comme tout autre glyphe.

Restent séparés tant que leurs propriétaires n'ont pas livré contrats,
implémentations et preuves :

- shaping implicite de séquences ZWJ, modifiers et variation selectors ;
- fallback automatique vers une fonte système ;
- COLRv1 hors des slices explicitement prouvées ;
- CBDT/CBLC et sbix sans decode, sélection de strike et placement internes ;
- SVG-in-OpenType sans renderer interne accepté.

Aucune API emoji native de plateforme n'est utilisée comme substitut.

## Transforms, clips et missing glyph

La route atlas directe accepte identity, translation, scale et affine
non-singulier lorsque le placement et le sampling restent prouvés. Perspective
et matrice singulière produisent un refus ou une route path distincte seulement
si cette dernière possède ses propres preuves.

Les clips wide-open et scissor font partie du minimum obligatoire. Une clip
complexe est acceptée seulement via l'autorité clip commune, sans règle texte
parallèle.

Le missing glyph utilise le glyph ID `.notdef` de la fonte sélectionnée
lorsqu'il est disponible. En son absence, le lowerer refuse avec un code
stable ; il ne consulte pas implicitement les fontes système.

## Budgets et batching

Les limites sont configurables et validées avant allocation :

- dimensions et nombre maximal de pages ;
- bytes cumulés des pages ;
- nombre de glyphes, sous-runs et instances ;
- bytes des vertex/index/instance/uniform buffers ;
- alignements et limites WebGPU.

Un glyphe identique n'est stocké qu'une fois par frame. Les instances sont
regroupées par sous-run compatible. Il est interdit de créer un uniform, un
bind group ou un draw par glyphe.

Le nombre de draws suit le nombre de sous-runs ordonnés, pas le nombre de
glyphes. Plusieurs pages peuvent exister dans une frame ; elles restent toutes
vivantes jusqu'à la completion.

## Refus et erreurs

Les codes sont centralisés et testés par valeur exacte. Ils couvrent au
minimum :

- fonte ou face sans identité stable ;
- glyphe, outline, métriques ou représentation absents ;
- artefact mutable ou hash incohérent ;
- génération absente, périmée ou incompatible ;
- couverture, dimensions, UV, bounds ou padding invalides ;
- budget ou limite WebGPU dépassé ;
- transform, clip, paint, blend ou filtre non admis ;
- glyph format sans renderer interne ;
- upload dependency, binding, ABI ou ownership incomplet ;
- erreur de rasterisation ou de packing.

Les invariants d'échec sont :

- refus lowerer/rasterizer/packing avant allocation native ;
- frame refusée jamais partiellement soumise ;
- génération incohérente refusée avant création de l'encoder ;
- création native échouée suivie d'un rollback `close-once` ;
- après submit, ressources retenues jusqu'à la completion, même si le readback
  échoue ;
- aucun fallback immediate, CPU ou legacy après admission.

## Cutover produit

Le développement se fait gate fermée. La bascule est atomique après les preuves
natives :

- `DrawText` devient prepared-or-refused ;
- `legacy.surface.prepared.family.text` disparaît ;
- `Text` quitte l'allowlist legacy ;
- les refus post-admission sont terminaux ;
- les tests prouvent qu'aucun consommateur du renderer immédiat n'est invoqué.

CFF et variations déjà abaissés en `DrawPath` continuent par la route
géométrique préparée. Ils conservent un diagnostic de provenance et ne sont pas
comptés comme réussite de l'atlas texte.

## Validation

### Contrats purs

Les tests couvrent :

- deux fontes différentes, deux face indices, tailles, positions subpixel,
  variations et palettes ;
- mêmes bytes/provenance donnant la même identité, mutation source sans fuite ;
- couvertures A8 `0`, `1`, valeurs intermédiaires et `255` ;
- antialiasing réel, déterminisme et immutabilité ;
- bounds, bearings, espaces, glyphes vides et `.notdef` ;
- déduplication de glyphes répétés ;
- packing sans chevauchement et multi-page ;
- UV, padding, budgets et génération ;
- transforms, clips, matières, strokes, blur et représentations emoji ;
- matrice paramétrée des refus.

### Oracles pixels

Les oracles CPU sont indépendants du shader legacy et réutilisent le contrat
couleur préparé :

- évaluation de matière dans le bon espace ;
- prémultiplication ;
- couverture A8 ;
- alpha du paint appliqué une fois ;
- encodage vers la cible sRGB préparée ;
- comparaison native `maxChannelDelta <= 1`.

Les preuves obligatoires incluent :

- couverture `0`, `1`, `128`, `255` ;
- paint alpha `0`, `0.5`, `1` ;
- couleur solide et gradients ;
- stroke avec caps/joins admis ;
- blur avec padding et bounds ;
- COLRv0 palette/foreground/alpha ;
- A8 et glyphes couleur dans la même frame.

### Task graph et preflight

Les tests prouvent :

- chaque upload avant son premier consommateur ;
- partage d'un masque et d'une page ;
- ordre `Core -> Text -> Image -> Text -> ColorGlyph` ;
- multi-page sans use-after-close ;
- un encoder, un submit et un readback au plus ;
- aucun uniform/bind group par glyphe ;
- stale generation et budget refusés avec zéro allocation native ;
- rollback `close-once` ;
- aucun fallback legacy.

### Tests natifs WebGPU

Les smokes couvrent :

- A8 simple et transformé ;
- gradient et matière enregistrée ;
- stroke ;
- blur mask filter ;
- clip scissor ;
- COLRv0 ;
- emoji monochrome et COLRv0 ;
- missing glyph ;
- frame mixte Core/Image/Text ;
- completion-only et readback ;
- fermeture/recréation du runtime.

Les erreurs natives déjà attribuées à FP-09 restent une classe distincte, mais
ne peuvent pas masquer un échec reproductible propre à FP-05.

### Performance

FP-05 enregistre :

- temps de lowering, rasterisation et packing ;
- bytes uploadés ;
- nombre de pages, sous-runs, draws et bind groups ;
- nombre de submits ;
- p50/p95 de frame froide ;
- distinction atlas A8, COLRv0 et path stroke.

FP-09 ajoute :

- cache hits/misses ;
- zéro réupload pour un texte inchangé ;
- éviction et changement de génération ;
- p50/p95 de frames chaudes répétées.

FP-11 régénère les rendus GM et scores, puis publie inputs, hashes, échantillons
bruts, p50/p95 et verdicts. Aucune proximité de performance avec Graphite
n'est revendiquée avant ces mesures.

## Critères de fermeture FP-05

FP-05 est fermé seulement lorsque :

- la route produit commune accepte ou refuse terminalement chaque `DrawText` ;
- A8, COLRv0, paint alpha, matières communes, stroke admis et blur possèdent
  des preuves CPU/GPU ;
- l'ordre upload-avant-sampling et l'ownership de completion sont prouvés ;
- les frames mixtes conservent exactement leur ordre ;
- les refus provoquent zéro allocation et aucun fallback ;
- `legacy.surface.prepared.family.text` et la famille legacy `Text`
  disparaissent ;
- les suites ciblées et agrégées pertinentes passent ;
- une review indépendante ne laisse aucun problème Critical ou Important
  légitime non corrigé ou explicitement attribué.

La résidence atlas persistante, les image filters composés et les formats
emoji non encore rendus ne sont pas faussement revendiqués comme fermeture
FP-05 ; leurs propriétaires restent FP-09, FP-07 et les specs font/FP-10.
