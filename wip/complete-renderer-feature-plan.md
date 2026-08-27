# Skia GM renderer completion implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> `superpowers:subagent-driven-development` (recommended) or
> `superpowers:executing-plans` to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Classer puis fermer toute la surface de rendu non-font exposée par
Kanvas et exercée par les Skia GMs enregistrés, avec rendu GPU prouvé,
refus stable ou dependency gate explicite.

**Architecture:** La surface publique `Canvas`/`Surface` reste l'entrée de
preuve. Kanvas abaisse la géométrie en coverage et le paint en `PipelineIR`,
exécute le chemin de référence CPU et le chemin WebGPU headless, puis compare
les artefacts. Le plan est global, mais son implémentation reste divisée en
vagues possédant chacune une seule route de production.

**Tech stack:** Kotlin Multiplatform, Gradle, `wgpu4k`, WebGPU, WGSL,
PipelineIR, tests JUnit/Kotlin, Skia GM fixtures et catalogue GPU evidence v2.

**Specs:** `.upstream/target/skia-like-realtime-renderer-target.md`,
`.upstream/specs/skia-like-realtime/README.md`,
`.upstream/target/high-performance-wgsl-pipeline-target.md`,
`.upstream/specs/geometry-coverage/README.md` et
`.upstream/specs/wgsl-pipeline/README.md`.

## Global constraints

- Le code Kotlin, les tests exécutés et les artefacts vérifiés sont les seules
  sources de vérité. Ce plan ne prouve aucun support.
- Toutes les nouvelles preuves et tous les rapports sont écrits sous
  `reports/gpu-renderer/evidence/`.
- `gpu-renderer-scenes` est hors architecture et ne doit recevoir aucun ajout.
- Les preuves passent par la route publique Kanvas `Surface`; une route de test
  parallèle ne compte pas comme support produit.
- Le backend reste WebGPU. Ne pas porter Ganesh ou Graphite.
- Ne pas reconstruire le compilateur, l'IR ou la VM SkSL. Un runtime effect
  supporté utilise un descriptor Kanvas enregistré, une sémantique Kotlin/CPU
  et un module WGSL validé.
- Le runtime supporté reste headless/offscreen. Le windowing natif n'entre pas
  dans ce plan.
- Toute ambiguïté `wgsl4k` est minimisée et remontée au projet `wgsl4k`; elle ne
  reçoit pas de workaround caché dans Kanvas.
- Les fonts restent dependency-gated, sauf les routes de glyphes utilisant une
  font réellement livrée et déjà prouvée. Shaping, fallback, emoji et color
  fonts ne bloquent pas la fermeture non-font.
- Les codecs restent dependency-gated tant que leurs livraisons réelles ne sont
  pas disponibles. Les images raw ou déjà matérialisées restent dans le scope.
- Perspective générale, SkSL dynamique et backend natif interactif restent
  hors scope jusqu'à une décision d'architecture séparée.
- Une feature n'est supportée que si référence ou oracle, capture GPU native,
  diff, stats, route diagnostics et fallback policy sont tous vérifiés.
- Une variante non supportée doit échouer avant submission partielle avec un
  diagnostic stable. Aucun fallback CPU silencieux n'est accepté.
- Une vague correspond à une route de production et une PR. Les PR peuvent être
  stackées, mais chaque PR doit être testable et reviewable isolément.

---

## 1. Frontière de complétude

Le programme est terminé quand chaque élément de la surface publique actuelle
et chaque GM enregistré possède exactement un verdict calculable :

| Verdict | Définition vérifiable |
| --- | --- |
| `SUPPORTED` | Route publique rendue, oracle/référence valide, preuve CPU/GPU, diff/stats/routes et promotion vérifiés. |
| `STABLE_REFUSAL` | Route publique refusée avant travail GPU partiel avec diagnostic, limites et test de non-régression. |
| `DEPENDENCY_GATED` | Dépendance externe réelle identifiée et test du gate sans substitut temporaire. |
| `OUT_OF_SCOPE` | Décision d'architecture explicite, actuellement limitée à perspective générale, SkSL dynamique et windowing natif. |

`UNCLASSIFIED`, une ligne de score orpheline ou un GM sans route observable
empêche la fermeture du programme.

Le périmètre est l'API Kanvas présente dans le code et les GMs présents dans
`SkiaGmRegistry`. Il ne promet pas la compatibilité automatique avec toute API
Skia future.

## 2. Fichiers structurants

Les chemins ci-dessous indiquent les frontières à inspecter. La présence d'un
type ou d'un lowerer ne vaut pas preuve de support.

| Responsabilité | Fichiers structurants |
| --- | --- |
| Surface publique | `kanvas/src/main/kotlin/org/graphiks/kanvas/canvas/Canvas.kt`, `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/Surface.kt` |
| Paint et effets | `kanvas/src/main/kotlin/org/graphiks/kanvas/paint/Paint.kt`, `kanvas/src/main/kotlin/org/graphiks/kanvas/paint/Shader.kt`, `kanvas/src/main/kotlin/org/graphiks/kanvas/paint/ColorFilter.kt`, `kanvas/src/main/kotlin/org/graphiks/kanvas/paint/ImageFilter.kt`, `kanvas/src/main/kotlin/org/graphiks/kanvas/paint/MaskFilter.kt`, `kanvas/src/main/kotlin/org/graphiks/kanvas/paint/PathEffect.kt`, `kanvas/src/main/kotlin/org/graphiks/kanvas/paint/SamplingOptions.kt` |
| Préparation GPU | `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceSemanticBuilder.kt`, `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceProductRouter.kt`, `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameBuilder.kt`, `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameGate.kt`, `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameExecution.kt` |
| Coverage et clips | `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUClipMapper.kt`, `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUClipCoveragePlanner.kt`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/clips/ClipContracts.kt`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/clips/GPUClipCoverageContracts.kt`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/clips/GPUClipExecutionPlan.kt` |
| Materials et WGSL | `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/` |
| Catalogue de preuve | `integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/catalog/GpuEvidenceCatalog.kt`, `integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/programs/KanvasSurfaceProgram.kt` |
| Exécution de preuve | `integration-tests/gpu-evidence/src/main/kotlin/org/graphiks/kanvas/gpu/evidence/runner/KanvasSurfaceEvidenceExecutor.kt` |
| GMs | `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmRegistry.kt`, `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmRunner.kt`, `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmRenderer.kt`, `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/GmCanvas.kt` |
| Scores et rendus | `integration-tests/skia/src/test/resources/test-similarity-scores.properties`, `integration-tests/skia/src/test/resources/reference/`, `integration-tests/skia/build/reports/` |

## 3. Contrat obligatoire de chaque vague

Chaque vague réutilise les étapes suivantes. Une PR qui saute une étape reste
une investigation et n'est pas une vague terminée.

- [ ] Repartir du dernier parent de stack validé et enregistrer son SHA.
- [ ] Sélectionner trois à cinq GMs représentatifs partageant la même première
      cause de refus ou le même type de mismatch.
- [ ] Écrire le test de contrat ou de pixel qui échoue pour la route publique.
- [ ] Exécuter le test ciblé et conserver la cause native initiale.
- [ ] Ajouter un test négatif pour la première variante volontairement hors
      limite, avec diagnostic stable attendu.
- [ ] Implémenter la plus petite route de production utilisée par Kanvas, sans
      couche réservée au harness.
- [ ] Exécuter les tests unitaires des modules modifiés.
- [ ] Ajouter ou étendre un oracle CPU indépendant de l'implémentation GPU.
- [ ] Ajouter la scène publique littérale et les compteurs de route.
- [ ] Exécuter `:integration-tests:gpu-evidence:test` et les GMs ciblés.
- [ ] Générer une capture native pour les seuls IDs de la vague.
- [ ] Vérifier bundle, manifest, référence, GPU, diff, stats, diagnostics et
      absence de fallback.
- [ ] Écrire le rapport de vague sous `reports/gpu-renderer/evidence/`.
- [ ] Promouvoir uniquement les IDs validés par l'oracle.
- [ ] Vérifier le catalogue promoted complet sans rebaseline implicite.
- [ ] Faire review puis commit/PR de la vague.

Commandes de contrôle communes :

```bash
./gradlew :gpu-renderer:test
./gradlew :kanvas:test
./gradlew :integration-tests:skia:test
./gradlew :integration-tests:gpu-evidence:test
./gradlew :integration-tests:gpu-evidence:verifyPromotedGpuEvidence
```

La génération, la vérification et la promotion quotidiennes utilisent une
sélection réelle, comme `-Pscene=solid-card-stack` ou
`-PscenesFile=scenes.txt`. `-Pall=true` est réservé à
l'initialisation du catalogue ou à un rebaseline complet explicitement
autorisé avec ses comparaisons avant/après.

## 4. Ordre des programmes

```text
W00-W01 vérité et evidence
   ├── W10-W12 état et primitives
   │      └── W20-W26 géométrie, clips, strokes et AA
   ├── W30-W35 paint, gradients, blend et color
   └── W40-W48 images, layers et filtres
             ├── W50-W53 runtime effects/WGSL
             └── W60-W65 vertices, mesh, picture et gates text

Toutes les routes correctness fermées
   └── W70-W75 lifecycle, performance, burn-down GM et clôture
```

Les programmes indépendants peuvent être investigués en parallèle. Les
captures, promotions, modifications du catalogue et intégrations de stack
restent séquentielles.

## 5. Vagues ordonnées

### Programme A — vérité et preuve

| Vague | Route propriétaire | Livraison testable |
| --- | --- | --- |
| `W00` | Inventaire GM | Énumérer le registre courant, rejouer les GMs, retirer les scores orphelins et produire une table machine-readable `GM -> famille -> première route/refus -> score -> référence`. |
| `W01` | Evidence v2 | Réconcilier catalogue, bundles historiques de vagues et preuves promues; chaque scène possède un propriétaire, un verdict et une provenance. |

Sortie : aucun nombre copié dans le Markdown n'est requis par les gates; les
comptages sont recalculés depuis le registre, le catalogue et les bundles.

### Programme B — état Canvas et primitives

| Vague | Route propriétaire | Livraison testable |
| --- | --- | --- |
| `W10` | État Canvas | `save`, `restore`, `restoreToCount`, queries, clip bounds et sentinelles post-restore. |
| `W11` | Transform affine | Translation, scale uniforme/non uniforme, rotation, skew, concat/set/reset; matrice non finie, singulière et perspective refusées selon contrat. |
| `W12` | Primitives de base | `drawColor`, `clear`, points, rect, RRect, DRRect, annotation et snapshot, y compris bounds vides/hors surface. |

### Programme C — geometry et coverage

| Vague | Route propriétaire | Livraison testable |
| --- | --- | --- |
| `W20` | Path curves | Quadratiques, cubiques, coniques abaissables, ovales/circles et fermeture implicite sous budgets déterministes. |
| `W21` | Path topology | Contours multiples, winding/even-odd/inverse, auto-intersections bornées et orientation. |
| `W22` | Coverage AA | Arêtes fractionnaires, petites primitives, superpositions et coverage déterministe pour rect/RRect/path. |
| `W23` | Clip shapes | `clipRRect` et `clipPath` avec transforms affines et consommateurs rect/RRect/path. |
| `W24` | Clip composition | `INTERSECT`, `DIFFERENCE`, inverse fills, clips imbriqués et budgets stencil/intermédiaires. |
| `W25` | Stroke geometry | Rect/RRect/path, caps, joins, miter, épaisseurs entières/fractionnaires, hairline et transforms. |
| `W26` | Path effects | Dash, corner, discrete, trim, Path1D/Path2D; implémentation bornée ou refus stable par variante. |

### Programme D — paint, gradients et couleur

| Vague | Route propriétaire | Livraison testable |
| --- | --- | --- |
| `W30` | Gradient stops/tiles | Multi-stops, hard stops, `CLAMP`, `REPEAT`, `MIRROR`, `DECAL`, dégénérescences et limites. |
| `W31` | Gradient families | Linear, radial, sweep et conical avec oracle commun d'interpolation. |
| `W32` | Gradient transforms | Local matrices, CTM affine, interpolation/color spaces et gradients sous clip/stroke. |
| `W33` | Blend | Porter-Duff complet puis modes avancés, destination read, alpha prémultiplié et refus des formats incompatibles. |
| `W34` | Color filters | Matrix, blend, compose, table, lighting, conversions sRGB/linear, HSLA, lerp, high-contrast, luma et overdraw. |
| `W35` | Shader composition | Blend shader, local matrix, color-filter wrapper, working color space, coord clamp, Perlin/fractal déterministes. |

### Programme E — images, layers et filtres

| Vague | Route propriétaire | Livraison testable |
| --- | --- | --- |
| `W40` | Image sampling | NEAREST, LINEAR et cubic borné, crop, bords, alpha, transforms et mipmap policy. |
| `W41` | Image formats | Formats raw réellement matérialisables, upload layout, row stride et conversion color/premul; codecs absents restent gated. |
| `W42` | Image shaders | Tile modes, local matrix, sampling, color filter et clip/transform interactions. |
| `W43` | Image decomposition | `drawImageNine`, lattice et atlas avec bounds, sprites, colors, blend et budgets. |
| `W44` | saveLayer | Bounds, alpha, paint, SRC/SRC_OVER, nesting, init previous, restore et backdrop contract. |
| `W45` | Filtres fondamentaux | Crop, blur, drop shadow, offset, tile et color filter, sur primitive et layer. |
| `W46` | Graphe de filtres | Compose, blend et merge avec ordre, bounds, color space, ressources et refus cycliques/invalides. |
| `W47` | Filtres avancés | Dilate, erode, displacement, matrix convolution, magnifier, picture et six variantes lighting. |
| `W48` | Mask filters | Blur styles/qualités, shader et table avec coverage, layer interaction et budgets. |

### Programme F — runtime effects et WGSL

| Vague | Route propriétaire | Livraison testable |
| --- | --- | --- |
| `W50` | Descriptor registry | Au moins trois descriptors représentatifs avec uniform schemas, CPU semantics, WGSL parsé et refus d'un ID inconnu. |
| `W51` | Runtime children | Shader, color-filter et blender children, ordre de composition, nullability et limites de profondeur. |
| `W52` | ABI/reflection | Layouts, alignment, uniform slabs, binding reflection, cache keys et mismatch diagnostics. |
| `W53` | Runtime boundaries | Runtime shader/filter/blender/image-filter supportés par descriptors; SkSL arbitraire et kind mismatch refusés sans compilation cachée. |

### Programme G — vertices, mesh, picture et texte livré

| Vague | Route propriétaire | Livraison testable |
| --- | --- | --- |
| `W60` | Vertices | Positions, colors, texcoords, indices, blend, clip et transforms avec validation des buffers. |
| `W61` | Mesh | MeshProgram enregistré, uniforms et children, interpolation et budgets; programmes inconnus refusés. |
| `W62` | Picture | Record/replay, nested save/restore/layer/clip, ressources et refus des opérations non replayables. |
| `W63` | Glyphes livrés | Maintenir la route de glyph runs pour les fonts réellement livrées, avec cache, transforms et blend bornés. |
| `W64` | Font dependency gate | Shaping, fallback, variable/color fonts et emoji restent gated avec diagnostic et tests de gate. |
| `W65` | Text interactions | Pour la seule font livrée : clip, transform affine, alpha, gradient supporté et layer; aucune généralisation aux fonts gated. |

### Programme H — runtime, performance et fermeture GM

| Vague | Route propriétaire | Livraison testable |
| --- | --- | --- |
| `W70` | Resource lifecycle | Device/queue generation, upload/readback ownership, dispose/close, device loss et absence de use-after-free. |
| `W71` | Cache/determinism | Cache keys complets, eviction, budgets mémoire, replay déterministe et aucune réutilisation inter-device. |
| `W72` | Performance tiers | Benchmarks froid/chaud, frame time, allocations, pipeline builds, uploads et readbacks par famille correctness fermée. |
| `W73` | GM regeneration | Régénération complète du registre au SHA final, scores sans orphelins et dashboard cohérent. |
| `W74` | GM burn-down | Clustering des échecs résiduels par première cause; nouvelles micro-vagues jusqu'à disparition de `UNCLASSIFIED`. |
| `W75` | Support closure | Matrice générée finale, vérification de toutes les preuves, archive des rapports et suppression du dossier `wip/`. |

## 6. Stratégie de tests par niveau

| Niveau | Responsabilité | Gate |
| --- | --- | --- |
| Unit | Maths, bounds, lowering, diagnostics et budgets. | Tests ciblés `kanvas`/`gpu-renderer`. |
| Contract | API publique, route choisie et refus avant exécution partielle. | Tests `Surface` et `GmCanvas`. |
| CPU oracle | Sémantique indépendante RGBA8/premul/coverage. | Tests d'oracle avec fixtures littérales. |
| GPU evidence | Submission native, readback, route, diff et stats. | `gpu-evidence` generated puis verified. |
| GM | Effet sur les scènes Skia représentatives puis registre complet. | Tests ciblés pendant la vague, full run en `W73`. |
| Promotion | Bundle immutable et provenance exacte. | Promotion incrémentale puis `verifyPromotedGpuEvidence`. |

## 7. Politique de review et d'agents

- Utiliser un agent abordable distinct pour l'inventaire, le test/oracle et
  l'implémentation quand ces travaux sont indépendants.
- Utiliser Sol uniquement pour une review d'architecture, de correctness
  numérique, de sécurité GPU ou avant promotion d'une route à risque.
- Un agent de debug commence par reproduire et réduire la première cause; il ne
  modifie pas les seuils pour masquer un mismatch.
- Un agent d'implémentation reçoit une seule vague, les tests qui échouent et
  les diagnostics exacts.
- Le propriétaire de session intègre séquentiellement, vérifie la stack et
  régénère la preuve au SHA réellement proposé.

## 8. Stop conditions

Arrêter la vague, sans promouvoir, dans les cas suivants :

1. échec natif non reproductible ou adapter non admissible ;
2. correction de production qui change une sémantique publique non couverte par
   le plan de vague ;
3. ambiguïté de parser/IR/génération provenant de `wgsl4k` ;
4. oracle CPU et référence Skia en désaccord sans règle décidée ;
5. finding Sol sur l'architecture, la preuve ou la sécurité GPU ;
6. besoin d'une dépendance font/codec non livrée ;
7. dépassement de budget imposant une politique de fallback nouvelle.

Un échec localisé et compris déclenche une micro-vague de correction avant la
reprise. Il n'autorise pas à élargir silencieusement la PR courante.

## 9. Ordre de démarrage

Le premier lot exécutable est strictement :

1. `W00` — réconcilier registre, rendus et scores depuis `origin/master` ;
2. `W01` — réconcilier les preuves standalone avec le catalogue v2 ;
3. recalculer la priorité GM à partir des clusters réels ;
4. lancer `W10`, `W11`, `W20` dans cet ordre, sauf si `W00` montre qu'une autre
   cause bornée débloque nettement plus de GMs.

La décision de priorité peut changer; les dépendances et les critères de
preuve ne changent pas.

## 10. Self-review avant activation

- [ ] Chaque variante publique de `coverage-map.md` possède une vague owner.
- [ ] Chaque vague possède un résultat positif, une limite négative et un gate.
- [ ] Aucun support n'est déduit d'un texte WIP ou d'un score isolé.
- [ ] Les fonts/codecs absents restent dependency-gated.
- [ ] Aucun travail ne cible Ganesh, Graphite, SkSL dynamique, windowing natif
      ou `gpu-renderer-scenes`.
- [ ] Les captures et promotions sont incrémentales et liées au SHA exact.
- [ ] La fermeture finale exige zéro `UNCLASSIFIED`, pas 100 % de rendu.
