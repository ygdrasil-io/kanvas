# W6d Task 4b — rapport Terra

## Portée livrée

Task 4b rend récursives les scènes possédées par `ImageFilter.Picture` dans le
graphe W6 gelé, sans changer la voie `DrawPicture` existante. La découverte
conserve l'ordre enregistré des draws, `Clear`, `DrawColor`, nested Pictures et
saveLayers ; elle descend aussi dans les scènes détenues par les nœuds filtre.
Le guard de cycle emploie l'identité objet seulement sur le chemin actif et se
dépile dans `finally`, donc deux scènes égales mais indépendantes ne sont pas
dédupliquées ni refusées par erreur.

Le domaine Picture est calculé une fois en F64 : `effectiveLocal = cull ∩
(src ?: cull)`, projeté par le contexte filtre, puis arrondi checked I32. Le
`src` reste un crop de contenu (pas une taille de target, translation ni
rescale) ; le domaine source reste celui du cull pour que les wrappers, dont un
blur, puissent produire un halo hors du contenu connu. L'échantillonnage hors
contenu est decal transparent.

Les scènes vides, culls vides et intersections vides choisissent le chemin
transparent existant, sans `FilterPassOperationV1.Picture` vide ni texture de
taille zéro. Le snippet du brief illustre un retour direct
`ContextualFilterResult(transparent, ..., null)`. L'invariant présent de
`FrozenOccurrence`/`FilterComposite` exige toutefois un terminal
`FilterPass` authentifié : l'implémentation conserve donc un `Offset(0)` gelé
vers une `FilterTarget` de taille normale. C'est une passe neutre explicite,
avec coût de passe/lifetime associé, mais elle ne crée aucune ressource zéro,
aucune passe après freeze, et laisse `ColorFilter`, `Compose`, `Blend` et `SRC`
observables. Le contrat terminal n'a pas été élargi.

`Clear`/`DrawColor` deviennent des `RenderPass` legacy-color gelés : pas de
W5 uniform fabriqué, shader statique de couleur, clip rectangulaire enregistré
respecté. Un clip totalement vide est un transparent `SRC_OVER` no-op, y
compris si le mode capturé était `SRC` ; il ne peut donc plus effacer le target.

Pour les IDs, les commandes Picture planifiées commencent après les commandes
root et le lane W4/W5 utilise directement cet ID planifié. Les ordinals/IDs
publics de la scène root ne bougent pas. `visualCommandCount` est exactement
`RenderGraph.visualDraws(passes).size`, donc les seuls draws physiques admis,
sans double comptage de la source filter.

La provenance re-entrante reste fermée : un `FilterTarget` ne peut être une
entrée Picture que si son producteur `FilterPass` est antérieur, que la source
scellée est l'aggregate/génération exacte, que le propriétaire authentifie la
clé, et que le `PictureAggregateBeginPass` correspondant a ce target pour
parent. La validation `DrawPicture` existante n'est pas relâchée.

## TDD — RED puis GREEN

Les fixtures publiques ont été écrites avec leurs oracles byte avant la
capture/render.

| Fixture / frontière | RED causal observé | GREEN final |
| --- | --- | --- |
| `pictureFilterNestedSceneKeepsSiblingAndDstOutOrder` | Refus W6b : source Picture non-graphique devait conserver son entrée layer gelée ; puis pixels transparents avant la traversée source-only. | XML classe W6d : présent et passant. |
| `pictureFilterNestedPictureLayerAndDrawColorKeepOrder` | Valeur d'oracle initiale incorrecte révélée par le pixel réel : attendu `[64,128,63,255]`, obtenu `[137,188,136,255]`; l'oracle a été corrigé indépendamment comme encodage sRGB du source-over linéaire prémultiplié bleu, rouge moitié, vert moitié. | XML classe W6d : passant. |
| `pictureFilterReentrantPassOrderKeepsEarlierAndInnerFilters` | `NoSuchElementException: Key 0 missing` dans la liaison source, puis `Failed requirement` localisé à `W6aLayerGraphValidation.kt:261` : parent `FilterTarget` refusé. | XML ciblé 1/0/0/0 ; puis classe W6d passante. |
| `pictureFilterSrcIsCropWithoutRescaleAndEmptyRemainsTransparent` | Refus lane lié à l'emploi du crop comme taille du target, puis absence de clé d'évaluation pour l'empty path. | XML ciblé 1/0/0/0 ; puis classe W6d passante. |
| `emptyPictureStillRunsComposeColorFilterAndSrcComposite` | Le chemin transparent était absent avant l'émetteur nullable. | XML ciblé 1/0/0/0. |
| `transformedPictureCropKeepsBlurHaloOutsideCull` | Halo absent lorsque la demande consommateur coupait le domaine source. | XML ciblé 1/0/0/0. |
| `pictureFilterDrawColorRespectsRecordedClip` | Refus initial de clip DrawColor non représenté par scissor gelé. | XML ciblé 1/0/0/0. |
| `pictureFilterFullyClippedDrawColorSrcKeepsPriorPixels` | XML 1/1/0/0 : bleu attendu, transparent obtenu avec `SRC` appliqué au target entier. | XML 1/0/0/0 après no-op transparent `SRC_OVER`. |

Le diagnostic `Failed requirement` re-entrant a été traité selon le flux de
debug : stack et première condition fausse capturées, puis validation resserrée
sur la provenance/génération exacte plutôt qu'assouplie globalement.

## Vérification finale

| Commande | JUnit XML (tests/failures/errors/skipped) | Processus |
| --- | --- | --- |
| `:gpu-plan:compileKotlin` | n/a | SUCCESS |
| `:gpu-renderer:compileKotlin` | n/a | SUCCESS |
| `:kanvas:test --tests W6dPictureFilterSurfacePixelTest` | 10/0/0/0 | UNKNOWN — worker natif exit 133 après tests passants |
| `:kanvas:test --tests W6bImageBlurSurfacePixelTest` | 10/0/0/0 | UNKNOWN — worker natif exit 133 après tests passants |
| `:kanvas:test --tests W6aLayerPictureTest.translatedPictureLayerKeepsNonzeroOrigin` | 1/0/0/0 | UNKNOWN — worker natif exit 133 après test passant |
| `:kanvas:test --tests W6aLayerPictureTest.drawPictureInsidePreviousLayerPreservesHostClip` | 1/0/0/0 | UNKNOWN — worker natif exit 133 après test passant |

Les XML sont sous `kanvas/build/test-results/test/`. Le known native worker
exit 133 rend le statut Gradle global inconclusif ; ce rapport ne le déclare pas
GREEN. `git diff --check` est propre.

## Fichiers modifiés

- `gpu-plan/.../PictureStreamAggregateV1.kt` — découverte récursive et
  propriétaires/domaine F64 filter-owned.
- `gpu-plan/.../W6aLayerGraphConstruction.kt` — agrégats re-entrants, crop
  source, direct legacy color, IDs de lane et accounting visuel.
- `gpu-plan/.../W6aLayerGraphValidation.kt` — parent `FilterTarget` admis
  uniquement comme target déjà initialisé pour l'aggregate re-entrant.
- `gpu-plan/.../W6bFilterGraphConstruction.kt` — traversée à identité active,
  émission nullable et leaf transparent/Offset gelé.
- `gpu-plan/.../W6bFilterGraphWitnessV1.kt` — preuve fermée de l'entrée
  re-entrante et consommation du parent Begin.
- `gpu-plan/.../PlanPhysicalLayoutV1.kt` — absence de uniform W5 pour les
  draws legacy-color gelés.
- `gpu-renderer/.../GPUW6aGeometryHostV1.kt`,
  `GPUW6aLayerFramePlan.kt`,
  `GPUWgpu4kW6aLayerFramePayloadMaterializer.kt` — materialisation directe
  d'une couleur gelée, sans replay, replan, cache, role ou allocator nouveau.
- `kanvas/.../W6dPictureFilterSurfacePixelTest.kt` — fixtures pixels publiques
  nested/order/crop/empty/wrappers/halo/clip.

## Revue propre

Relecture effectuée avant commit : pas de geometry ajoutée hors `:math`, pas de
nouveau `PlanPass`, rôle ressource, allocator, cache ou renderer replay. Les
gates `W6bImageBlur` et les deux sélecteurs W6a prescrits restent verts dans
leurs XML ; le writer14 stale du test W6a complet n'a pas été utilisé.
