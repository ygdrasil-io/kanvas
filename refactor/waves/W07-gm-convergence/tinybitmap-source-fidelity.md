# W7 — source `tinybitmap` et image shader encoded

3 octobre 2026. Source `78ee0ee0870fb2387eddbfedb819bb358f1f0690`, branche
`codex/w7-tinybitmap-source-fidelity`, base
`e5069ccb54bd48706e7a0fd65a2e2401eec38c2e`, parent draft #2442.
W7 ACTIVE : cette qualification ciblée ne clôture ni W7 ni la parité Skia.

## Correction et périmètre architectural

La source Skia est épinglée à
`4f26f22daa4bf124e2999145f5caad4b10625580/gm/tinybitmap.cpp`.
Le GM local déclare le fond opaque 221, retire son overpaint redondant et
utilise les octets RGBA `[128,0,0,128]` explicitement PREMUL. Son nom,
100×100, domaine LINEAR, AA par défaut, sampling nearest, REPEAT/MIRROR,
tolérance 2 et `minSimilarity=0` restent inchangés. Aucun ajustement à la PNG.

Les contrôles frozen dans les deux domaines ont révélé un prérequis produit :
une feuille `Shader.Image` était refusée dans SRGB_ENCODED. L'admission ajoutée
reste limitée à une RECT FILL entière non-AA, SrcOver, transform identité ou
translation I32 entière, clip hard entier, nearest et pixels owned RGBA/BGRA,
SRGB PREMUL SOURCE_SPACE. CLAMP, REPEAT, MIRROR et DECAL sont couverts.
La provenance RECT est conservée ; les shaders composés ne sont pas admis.

`W5eImagePlanCompiler` transmet déjà le domaine de la target à la metadata,
à `ColorV3` et à l'autorité numérique scellée. Le stage image utilise désormais
`execution.colorAlpha.compositionDomain`, cette autorité existante, au lieu du
défaut LINEAR. Pas de nouveau programme, format d'upload, CPU fallback,
validator affaibli, budget ou cap relevé. L'ancienne matrice de refus ne change
que son image nearest devenue admise, remplacée par une image nonnearest.

## Témoins natifs indépendants

Un RED à quatre échecs a précédé le prérequis encoded ; les contrôles LINEAR
passaient déjà leurs buffers littéraux. Après correction, 14/14 tests ciblés
PASS, sans skip, erreur, timeout ou signal : EncodedImage4, TinyBitmap3,
BitmapRect3, ChildPort3 et ChildCausal1. Les quatre nouveaux tests produisent
96 rendus natifs réussis et couvrent pixels nonuniformes, alternance des deux
domaines avec le même Image, layouts RGBA/BGRA, quatre tile modes, translation,
clip, RGB non nul avec alpha zéro, replay sur la même Surface et huit refus
suivis d'une récupération transactionnelle native.

Les oracles sont des pixels littéraux indépendants, non une PNG historique ou
une sortie du renderer. Le buffer TinyBitmap 100×100 est uniforme :
LINEAR `[230,194,194,255]`, SHA
`a1d14b67097c5a1235c25cd91acdfd57c2890f7896637d3472fd3fa6d3e9939b` ;
SRGB_ENCODED `[230,165,165,255]`, SHA
`d8d95c3bf9d98a4479ebf85604be243b92c4f306799b966ed371634b23a4fa0c`.
Les anciens témoins BitmapRect/ChildSampling gardent leurs buffers attendus.

Tests frozen : TinyBitmap
`954206d730622e743d81eb3a8f299c9a7fd7b3cffb62e7395382d3e0532a4357` ;
EncodedImage `f83274e421d332e52a166b2e935baf1f8a0c63f01d04a4af92425b91519a3320`.
Ils n'ont pas été adaptés après RED. Revue source Sol Approved, C0/I0/M0,
limitée à cette tâche ; les preuves natives ont été auditées par le contrôleur,
non réexécutées par le reviewer.

Revue finale Sol du lot `e5069ccb…f7b445f8` : Approved pour publication draft
stackée, aucun nouveau défaut Critical/Important/Minor. Le reviewer a aussi
décodé le PNG livré : 100×100 uniformément `[230,194,194,255]`, SHA RGBA
`a1d14b…` conforme. Les deux points Important ouverts sont la suite W5e héritée
et le budget en contexte de suite, détaillés ci-dessous ; merge et validation
globale restent non qualifiés. Cette mention de verdict est administrative,
postérieure au commit revu, sans changement source/test/artifact.

## Comparaison à la référence historique

Mesure du seul cas `[592,593)`, registry 631 inchangée. Les deux mesures
terminent exit 0, sans timeout/signal, avec zéro refus/diagnostic. Les opérations
et dispatches passent de 3 à 2 grâce au retrait de l'overpaint.

| Mesure | Avant | Après |
| --- | ---: | ---: |
| Pixels exacts | 0 % | 0 % |
| Pixels à ±2 | 0 % | 0 % |
| SSIM | 0.9845261966447928 | 0.9949447881896182 |
| Erreur moyenne de canal | 0.11666666666666667 | 0.054901960784313725 |
| Delta maximal RGBA | [9,55,55,0] | [0,28,28,0] |

La comparaison standard du seul `tinybitmap` exécute 1 test PASS, sans skip ni
erreur, mais rapporte également **0 %** : son seuil historique est 0 %. Ce
PASS n'est pas une parité acquise. Aucun nouveau relevé agrégé du corpus.

La révision/configuration productrice de la référence locale reste inconnue.
Trois limites de fidélité demeurent : proxy full Rect pour le `drawPaint`
Skia ; alpha public byte 128/255 contre `setAlphaf(0.5)` exact ; choix du domaine
du harness Skia historique non établi pour cette PNG. La preuve du harness
épinglé bare-8888 concerne cette configuration précise, pas la provenance de
la référence. Une migration du GM à encoded n'est pas livrée dans ce lot.

## Régressions et gap à résoudre

La suite publique ciblée W5eImageShader27 + W7SurfaceComposition25 reste RED :
52 exécutés, 47 PASS, 5 FAIL, aucun skip/erreur/timeout. Quatre anciens tests
de refus W5e (rrect, pathStroke, pathHairline, vertices) échouent de la même
manière avec les deux corefiles restaurés au parent : ces quatre échecs sont
hérités, démontrés par une exécution native de baseline.

Le cinquième échec, `encodedPlainLayerBudgetIsExactAndOneByteLessRecovers`,
refuse le rendu accepté à 888 octets avec
`unsupported.readback_staging.aggregate_budget_exceeded` dans la suite.
Le même test inchangé passe isolément avec ce patch et dans la séquence
baseline étroite. **Cause non attribuée** : ni regression du patch ni défaut
de cache/pool/isolation ne sont prouvés. Le contrôle isolé conserve l'acceptation
exacte B=888, le refus B−1, les pixels attendus et la récupération. Ce gap reste
ouvert avant validation globale/merge ; pas de budget augmenté ou test ignoré.

## Artifacts et preuves conservées

Le générateur ciblé termine exit 0 : 1 rendu, 0 échec, 2 dispatches/0 refus.
Seul `generated-renders/image/tinybitmap.png` change, SHA
`b3f302353c21ca67bbf7b36d3c9db028d49d487806a367e98b119f14a326a4dd`.
Les 813 PNG non-cibles restent scellées à
`ca9ccd2e0596c72d277ca143b3b8dc92cf3dd3bdcb26751818b2fbd7d8bc8ba0` ;
les 558 autres valeurs de scores à
`d9f2f6e711f0f81fc416d9116876ffaff7ae9e09a0fd24d76fd0004bc696563f`.
Le score cible reste `0.0` ; seul le timestamp du fichier de scores change.
La référence reste
`c52d28dd7fe1d45b1650ad3332ed36df1acb60987d166c588653180cd7f53c01`.

Le contrôleur a lu les inventaires complets, process/exit, tous les events,
stacks et XML stdout/stderr, et le JSONL brut de parité, puis effectué des
post-seals séparés. Chaque run était séquentiel, borné à 240 s, sans writes
source/docs/Git pendant le runtime. Archives privées conservées sous
`/private/tmp/kanvas-w7-inverse-inventory.hbWqUb` :
`encoded-image-shader-red-1`, `encoded-image-shader-green-1`,
`encoded-image-shader-regressions-1`, `encoded-image-shader-baseline-five-1`,
`encoded-image-shader-budget-isolated-1`, `tinybitmap-after-1`,
`tinybitmap-generator-1`, `tinybitmap-runner-1`. Reçu détaillé dans
`.superpowers/sdd/encoded-image-shader-e506-plan/controller-evidence.md`.

Warnings JVM native-access, LWJGL Unsafe, Gradle et safe calls W6 existants
restent visibles. Fonts, codecs externes et `jpg-color-cube` restent exclus.
Aucun merge, GREEN global ou clôture W7 revendiqués.
