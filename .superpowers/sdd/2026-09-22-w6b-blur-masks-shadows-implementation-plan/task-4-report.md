# Task 4 — W6b Mask Blur Auto-Layers

Base: `3fde179836cb9a3475a5d69e40ea4fd5de96581f`  
Commit prévu: `feat(gpu): execute w6b mask blur auto-layers`

## RED → GREEN

1. Ajout des tests publics `W6bMaskBlurAutoLayerSurfacePixelTest` et de son oracle CPU indépendant : rectangle translaté, les quatre styles (`NORMAL`, `SOLID`, `OUTER`, `INNER`), `DST_OUT` appliqué une fois, auto-layer dans un `saveLayer`, Picture parent/enfant avec recouvrement semi-transparent et trou `CLEAR`, puis chaîne combinée mask → image blur.
2. RED causal observé contre la production inchangée : les renders aboutissaient à la terminaison `w6b.filter.native_execution_unimplemented` (la frontière W6b n'était pas matérialisée).
3. Après le premier câblage, RED de pixels pour le blend/les origins, puis RED du witness sur la source matérialisée de la chaîne combinée. Le witness accepte désormais seulement `IMAGE_BLUR_X/Y` dont l'entrée est le `MaterializedSource` producteur immédiatement gelé.
4. GREEN : `W6bMaskBlurAutoLayerSurfacePixelTest` est à 5 tests, 0 failure dans le XML JUnit. La suite d'admission/récupération W6b est à 25/0 ; les deux anciennes attentes de terminaison native pour les masques exécutables sont devenues des assertions pixels publiques.

## Implémentation

- Ajout de la matérialisation WGSL gelée de la coverage source, du retain d'original coverage, des quatre formules de style et de la source W5 masquée. Les styles non `NORMAL` reçoivent explicitement leur source originale retenue.
- Les passes X/Y de blur coverage, style et `MaterializedSource` sont consommées directement depuis le graphe planifié ; aucune nouvelle opération, ressource, bounds ou passe n'est décidée côté renderer.
- Une occurrence image+mask suit maintenant coverage → style → source W5 masquée → image X/Y → composite. Le graphe/witness et le contrat Picture suivent l'arête `MaterializedSource` déjà publiée.
- Les sources Picture hors de leur texture RGBA scellée sont du noir transparent, mais leur mask parent conserve son alpha de halo ; cela respecte la formule de l'amendement Picture sans re-shader les enfants.
- Le draw de l'auto-layer transparente force `SRC_OVER` au stade source ; le blend capturé est appliqué une seule fois au `FilterComposite`. Le cache de localisation est indexé par `(commande, cible)` pour éviter de faire fuir l'origine d'un agrégat Picture dans une lane W5 brute.
- Les origins, leases et resource uses des coverage retains/sources restent publiés par le graphe. La coverage witness image-only de Task 3 est vidée, tandis que seules les racines atteignant une opération mask gelée sont matérialisées comme coverage executable.
- La validation `PictureStreamAggregateV1` conserve l'ordre scellé enfant → parent et autorise explicitement l'unique étape intermédiaire `MaterializedSource` avant le blur image du parent.

## Fichiers

Créés :

- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/W6bMaskCoverageSnippet.kt`
- `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W6bMaskBlurAutoLayerSurfacePixelTest.kt`
- `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W6bMaskBlurCpuOracle.kt`

Modifiés : graphes/W6a planification, witness W6b, contrat Picture stream, frame plan/encoder/materializer, snippet separable blur, et tests publics W6a/W6b concernés.

## Gates exécutés

| Commande | Résultat vérifié |
| --- | --- |
| `rtk ./gradlew --quiet :gpu-plan:compileKotlin` | GREEN, exit 0 |
| `rtk ./gradlew --quiet :gpu-renderer:compileKotlin` | GREEN, exit 0 |
| `rtk ./gradlew --quiet :kanvas:compileKotlin` | GREEN, exit 0 |
| `rtk ./gradlew --quiet :kanvas:test --tests org.graphiks.kanvas.surface.W6bMaskBlurAutoLayerSurfacePixelTest` | JUnit 5/0 |
| `rtk ./gradlew --quiet :kanvas:test --tests org.graphiks.kanvas.surface.W6bFilterAdmissionRecoverySurfaceTest` | JUnit 25/0 |
| `rtk ./gradlew --quiet :kanvas:test --tests 'org.graphiks.kanvas.surface.W6bImageBlurSurfacePixelTest.nested filtered Pictures run the child blur before the sealed parent blur'` | Task 3 JUnit 1/0 |
| `rtk ./gradlew --quiet :kanvas:test --tests org.graphiks.kanvas.surface.W6aLayerSurfacePixelTest` | JUnit 16/0 |
| `rtk ./gradlew --quiet :kanvas:test --tests org.graphiks.kanvas.surface.W6aLayerRestoreSurfacePixelTest` | JUnit 9/0 |
| `rtk ./gradlew --quiet :kanvas:test --tests org.graphiks.kanvas.surface.W6aNestedLayerSurfacePixelTest` | JUnit 9/10, dette préexistante ci-dessous |

`git diff --check` est vert. Les invocations `:kanvas:test` terminent ensuite avec le code natif 133 ; les verdicts JUnit listés ci-dessus sont écrits avant cet arrêt. Une tentative intermédiaire a produit 134 pour un parse error WGSL réel ; elle a été corrigée, puis les tests masque sont revenus à 5/0. Les exécutions natives 133/134 restent donc **UNKNOWN** sans preuve indépendante.

## Self-review / concerns

- Pas de route W6b ajoutée vers `GPUTopLevelMaskBlurFrameRecording` ni `GPUPreparedMaskFilterLowerer`; l'audit les trouve seulement dans les routes legacy/hors W6 et dans la documentation existante.
- Aucun test d'infrastructure, mock/fake device, reflection, compteur, source-shape, GM, render/baseline/dashboard, codec/font ou `jpg-color-cube` n'a été ajouté.
- `:gpu-plan:test` n'a pas été lancé : ses 28 dettes W3/W4 connues n'ont été ni corrigées ni reclassées.
- La seule failure `W6aNestedLayerSurfacePixelTest` est identique au worktree temporaire sur le base commit : `command limit refuses before readback and same surface recovers()` attend `w6a.layer.command_limit`, reçoit `w6b.filter.invalid_bounds: W6b picture traversal exceeded its sealed capture bound.` Elle est inchangée et non reclassée.
- La revue Sol demandée par le brief n'a pas été lancée : l'instruction de tâche interdit les sous-agents. Une revue humaine/Sol reste la seule étape non effectuée avant livraison.

## Fix round 1 — revue P1

Base de correction : `8d3899f9d` (`c75a421` est uniquement le ledger de revue).

### RED → GREEN

1. RED causal : un `RRect` clippé et un path stencil étaient admis mais la matérialisation cherchait ensuite une forme `SolidRect` à partir d'un scan renderer. Le path échouait également sur le contrat de deux packets stencil. Les nouveaux tests publics/oracles indépendants ont d'abord exercé ces pixels.
2. GREEN : le plan porte désormais le binding typé `W6bRasterCoverageBindingV1` depuis l'autorité W4 gelée vers `FilterCoverageSourcePass`. Les buffers vertex/index/uniform de coverage sont des allocations plan-owned distinctes, pour que les écritures différées du source W5 ne remplacent pas les bytes W4 avant la soumission. Le renderer consomme uniquement ce binding publié ; `frozenMaskCoverageInputs` et son prérequis `SolidRect` sont supprimés.
3. RED causal : le source stage W5 d'un auto-layer rectangulaire supposait qu'un module contenait toujours une queue `* coverage`; les sources W6a rect sont déjà W5-only. Un `DST_OUT` pouvait aussi conserver son destination-read/blend au source stage.
4. GREEN : une queue coverage authentifiée est retirée lorsqu'elle existe, sinon le module W5-only est conservé. Toute source W6b force `LegacySrcOverV1` dans les branches analytic et stencil, désactive le destination-read source et laisse le blend sélectionné exclusivement à `FilterComposite`.
5. Régression Task 3 trouvée et restaurée : une scène sans filtre dépassant `maxNodes` était revendiquée par W6b avant le refus `w6a.layer.command_limit`; le bornage ne rend plus W6b owner sans occurrence W6b. Un layer-mask avait aussi perdu son alpha après retrait du scan : le même binding `sealedAlphaSource` existant publie maintenant explicitement la génération RGBA du `LayerTarget` (mapping, bounds et génération exacts), sans nouvelle passe, source, cible ni opération.

### Fichiers complémentaires modifiés

- `gpu-plan/.../W6aLayerPlanCompiler.kt` : refuse de façon stable l'admission native des producteurs W4 sans packet raw-coverage exécutable (vertices/W4e), au lieu d'accepter puis échouer côté renderer.
- `gpu-plan/.../PictureStreamAggregateV1.kt`, `W6aLayerGraphValidation.kt`, `W6bFilterGraphWitnessV1.kt` : le binding alpha existant distingue le Picture aggregate scellé du `LayerTarget` courant gelé et valide les deux générations explicitement.
- `gpu-renderer/.../GPUW6aEncoderScopesV1.kt` et `PreparedGPUFrame.kt` : le coverage stencil conserve le seul pass W6b et ses deux groupes de commandes W4 gelés, avec depth/stencil emprunté validé.
- `kanvas/.../W6bMaskBlurAutoLayerSurfacePixelTest.kt`, `W6bMaskBlurCpuOracle.kt` : ajout des pixels publics RRect+clip et path stencil `DST_OUT` contre l'oracle CPU.

### Gates de correction

| Commande | Résultat vérifié |
| --- | --- |
| `rtk ./gradlew --quiet :gpu-plan:compileKotlin` | GREEN, exit 0 |
| `rtk ./gradlew --quiet :gpu-renderer:compileKotlin` | GREEN, exit 0 |
| `rtk ./gradlew --quiet :kanvas:compileKotlin` | GREEN, exit 0 |
| `rtk ./gradlew --quiet :gpu-plan:test --tests '*RenderGraphContractTest'` | GREEN, exit 0 |
| `rtk ./gradlew --quiet :kanvas:test --tests '*W6bMaskBlurAutoLayerSurfacePixelTest' --tests '*W6bFilterAdmissionRecoverySurfaceTest'` | JUnit 7/0 + 25/0 |
| `rtk ./gradlew --quiet :kanvas:test --tests '*W6aNestedLayerSurfacePixelTest' --tests '*W6aLayerRestoreSurfacePixelTest' --tests '*W6aLayerSurfacePixelTest' --tests '*W6aLayerW4W5SurfacePixelTest'` | JUnit 10/0 + 9/0 + 16/0 + 19/0 |

Les shards `:kanvas:test` écrivent les résultats JUnit verts ci-dessus puis le process natif termine avec 133. Sans preuve indépendante que ce code signifie succès ou échec, 133 (et 134 s'il réapparaît) demeure **UNKNOWN**. `:gpu-plan:test` complet contient toujours les 28 dettes W3/W4 déjà documentées ; il n'a pas été relancé, corrigé ni reclassé. Aucun travail GM/render/baseline/dashboard, font/codec, `jpg-color-cube`, route legacy `GPUTopLevelMaskBlurFrameRecording` ou `GPUPreparedMaskFilterLowerer` n'a été ajouté.

### Self-review

- Les ressources et l'ordre de passes restent gelés dans W6a/W4 : le renderer ne traverse ni capture IR ni graphe pour replanifier/retouver une coverage source.
- Les deux nouveaux tests restent des tests pixels publics avec oracle CPU ; aucun mock, fake device, reflection, compteur ou test de source statique n'a été introduit.
- Les contrôles Task 3 passaient avant commit : nested command-limit, restore mask blur, terminal-empty/layer et W4/W5 sont tous à 0 failure dans leurs XML JUnit.

## Fix round 2 — re-revue P1

Base de correction : `0e85abda1` (`66d3a44` est uniquement le ledger de revue).

### RED → GREEN

1. RED causal du direct path : le nouveau témoin public d'un triangle convexe était admis puis refusé avec `w6a.layer.invalid_plan: Path stencil structural authority requires stencil edge-fan geometry`. La cause était le packet W4c/W4d toujours publié comme `PathStencilCover` / `Stencil1x` sans ressource depth/stencil. Après la correction à `Shading` / `FullOrScissor`, le même témoin a atteint les pixels et a exposé le second RED : `expected RGBA=205,0,0,157`, `actual=0,0,0,86` (halo noir).
2. RED causal stencil : le témoin public concave `SRC_OVER` a échoué avec la même alpha mais RGB noir : `expected=177,0,0,115`, `actual=0,0,0,115`. Cela prouve que la couverture était produite, mais que W5 restait limité à la géométrie d'origine.
3. GREEN : `W5bPointDraw` et les paths directs réécrivent seulement le contenu de leur binding V/I gelé existant pour couvrir l'étendue source W6b publiée ; aucun buffer, source, target, bounds ni opération n'est créé. Les packets de la coverage W4 restent inchangés dans leur pass `FilterCoverageSourcePass` séparé. Un path stencil consomme le même binding `coverageSource` explicite dans son `StencilCover`, évalue W5 sur le rectangle de l'étendue source, et garde un état D24S8 neutre (compatible avec le render pass, sans test ni écriture stencil).
4. GREEN : W5 reconnaît `StencilCover.coverageSource` comme source W6b, retire uniquement la multiplication coverage déjà gelée, force `LegacySrcOverV1` au stade offscreen et conserve le blend sélectionné exclusivement dans `FilterComposite`. La règle Picture scellée transparent-black reste le chemin distinct de `MaterializedSource`.
5. La première version du triangle test utilisait une diagonale passant exactement par un centre de pixel. Son écart résiduel était une ambiguïté de règle de rasterisation au bord, non le halo. Le fixture est maintenant un triangle direct dont aucune arête ne traverse un centre de pixel, tout en restant calculé indépendamment par l'oracle CPU.

### Fichiers modifiés par ce correctif

- `gpu-renderer/.../planning/W4cPathFillGraphLowerer.kt`
- `gpu-renderer/.../planning/W4dPathStrokeGraphLowerer.kt`
- `gpu-renderer/.../execution/GPUW5aSourceStageNativeV2.kt`
- `gpu-renderer/.../execution/GPUWgpu4kW6aLayerFramePayloadMaterializer.kt`
- `kanvas/.../W6bMaskBlurAutoLayerSurfacePixelTest.kt`
- `kanvas/.../W6bMaskBlurCpuOracle.kt`

### Gates de correction

| Commande | Résultat vérifié |
| --- | --- |
| `rtk ./gradlew --quiet :gpu-plan:compileKotlin` | GREEN, exit 0 |
| `rtk ./gradlew --quiet :gpu-renderer:compileKotlin` | GREEN, exit 0 |
| `rtk ./gradlew --quiet :kanvas:compileKotlin` | GREEN, exit 0 |
| `rtk ./gradlew --quiet :gpu-plan:test --tests '*RenderGraphContractTest'` | GREEN, JUnit 94/0 |
| `rtk ./gradlew --quiet :kanvas:test --tests '*W6bMaskBlurAutoLayerSurfacePixelTest'` | JUnit 9/0 (les 7 témoins précédents plus direct et stencil RGB) |
| `rtk ./gradlew --quiet :kanvas:test --tests '*W6bFilterAdmissionRecoverySurfaceTest'` | JUnit 25/0 |
| `rtk ./gradlew --quiet :kanvas:test --tests '*W6bImageBlurSurfacePixelTest.nested filtered Pictures run the child blur before the sealed parent blur'` | Task 3 JUnit 1/0 |
| `rtk ./gradlew --quiet :kanvas:test --tests '*W6aNestedLayerSurfacePixelTest' --tests '*W6aLayerRestoreSurfacePixelTest' --tests '*W6aLayerSurfacePixelTest' --tests '*W6aLayerW4W5SurfacePixelTest'` | JUnit 10/0 + 9/0 + 16/0 + 19/0 |
| `rtk git diff --check` | GREEN |

Les invocations `:kanvas:test` ont toutes écrit les XML JUnit complets puis quitté avec 133. La seule exécution 134 rencontrée pendant ce round a été un diagnostic indépendant et causal WGPU : un pipeline source stencil sans state D24S8 était incompatible avec le render pass. Elle est corrigée par le pipeline `DirectSrcOverWithPathDepthStencil` neutre ; les deux témoins path sont ensuite JUnit GREEN. Les exits natifs 133 (et 134 hors de ce diagnostic corrigé) restent **UNKNOWN** sans preuve indépendante.

### Self-review / concerns

- Aucun nouveau `PlanPass`, arm d'opération, `SourceBinding`, bounds ou target : le correctif consomme les bindings W4/W5 et les passes déjà gelés. Il n'y a ni scan renderer, ni traversal de capture IR, ni replanification.
- La séparation buffer source/coverage de round 1 est conservée : les bytes W5 pleine étendue ne remplacent jamais les bytes de la raster coverage W4 avant la soumission.
- Le stencil garde ses deux packets W4 producer/cover pour `FilterCoverage`; seul son source material W5 réutilise le cover avec state stencil neutre, donc sans transformer cette source en `PathStencilCover` coverage ou changer l'ordre gelé.
- Aucun mock/fake device/reflection/counter/test de source statique, GM/render/baseline/dashboard, font/codec ou `jpg-color-cube` n'a été ajouté. Les routes legacy `GPUTopLevelMaskBlurFrameRecording` et `GPUPreparedMaskFilterLowerer` ne sont pas appelées.
- `:gpu-plan:test` complet n'a pas été lancé : ses 28 dettes W3/W4 documentées restent inchangées et non reclassées. Le statut 133 reste UNKNOWN.
