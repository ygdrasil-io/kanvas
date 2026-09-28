# W6 2A0c.IV — InverseDomain Native Sites Implementation Plan

**Status:** relu par Sol sans finding Critical/Important ; prêt pour exécution séquentielle.
**Base:** `codex/w6-final-gates` à `5d5cf5c88`, Draft PR #2409 empilée sur #2408.
**Spec:** `refactor/specs/2026-09-26-w6-final-gates-design.md` §4 et `refactor/plans/2026-09-26-w6-final-gates-implementation-plan.md` 2A0c.IV.

## But et frontière

Geler les bundles natifs réellement requis par `ClipPlanStrategy.InverseDomain` **sans mask** dans la route W6 finale. Ce lot suit `InverseMask` IIIb/c review-clean, mais n'en réutilise ni l'ABI mask+Uniform32, ni les deux owners Geometry, ni la recette scan-span. `InverseDomain` possède **un seul** `PathRenderPass` W4e natif de phase `SingleSampleDirectColor` : `Zero` exige un cover couleur et `Geometry` exige trois bundles sous ce même packet. Le résultat de ce plan ne crée aucune lease 2A1, aucun B/B−1 et aucune claim 2B.

Un subagent Terra implémente chaque tâche indépendante **séquentiellement** dans le worktree W6 existant ; un agent Sol fait la revue indépendante après chaque commit. Astra ne sert qu'en cas de blocage architectural persistant. Aucun test d'infrastructure du code, GM, dashboard ou suite Skia globale. Fonts et codecs hors périmètre. Les objets et opérations géométriques restent dans `:math:geometry`, avec la nomenclature I/F32/F64 ; le renderer ne reconstruit ni domaine ni fan.

## Contrat gelé après audit de reachability

| Prédicat W6 final | Owner natif | Bundles requis | Payload/ABI |
| --- | --- | --- | --- |
| `InverseDomain.Zero` + source `Empty` ou `InverseDomainSource` | `NativeSiteOwnerV1(path.id, path.ordinal, 0)` | `colorCover` fullscreen | U16 `INVERSE_DOMAIN_ZERO_UNIFORM`, sans V/I/D24S8 |
| `InverseDomain.Geometry` + intérieur `DirectTriangle` | même `path.id`/`ordinal`, slots `0`, `1`, `2` | `domainStencil`, `interiorZero` Replace, `colorCover` | quad `INVERSE_DOMAIN_QUAD` V/I, intérieur `INVERSE_DOMAIN_INTERIOR` V/I, U16 `INVERSE_DOMAIN_UNIFORM`, D24S8 scene-local |
| `InverseDomain.Geometry` + intérieur `StencilEdgeFan` | mêmes trois slots | `domainStencil`, `interiorZero` parity/winding, `colorCover` | mêmes rôles et slices, avec fan/fill rule/stencil distincts |

Le domaine vient de `InversePathGeometryF32.copyDomainI32()` déjà scellé ; `path.scissor` reste le scissor **source**. La forme Zero `Fill`/`Stroke` + `INVERSE_DOMAIN_ZERO_SOURCE` de l'encodeur générique est hors des bindings W6 corrects : `W4eClipPlanCompiler.sealW4eInverseDomainZero` ne conserve que `Empty` ou `InverseDomainSource`, et le payload final n'émet pas cette slice V/I. Le seal/préflight W6 doit donc la refuser, sans supprimer le fallback W4e autonome. `commonSource=true` dans l'appel W6 omet la commande de draw `domainStencil` Geometry, mais crée toujours ce pipeline : son slot `0` reste présent, authentifié et plus tard compté en 2A1. Aucun rabais d'identité ou de bundle ne dépend d'une commande omise.

## Points d'intégration

- `gpu-plan/.../W4eClipPlanCompiler.kt`, `PlanW4eGeometryBindingV1.kt`, `W4eNativePayloadPlan.kt` : faits scellés existants ; les consulter, ne pas y ajouter de géométrie concurrente.
- Nouveau `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W6InverseDomainPathRecipeV1.kt` : variante Zero, Geometry Direct/Fan et sous-recettes de bundles, contenus défensifs et encodage canonique exact.
- `gpu-plan/.../NativeSiteRecipeV1.kt`, `PlanPhysicalLayoutV1.kt`, `W6aLayerGraphConstruction.kt` : catalogue, seal et exhaustivité depuis `finalW4eBindings`, non depuis le proxy W6 ni l'index des steps.
- `gpu-renderer/.../recording/GPUW6aLayerFramePlan.kt`, `planning/W4eClipGraphLowerer.kt`, `execution/GPUWgpu4kW6aLayerFramePayloadMaterializer.kt`, `execution/GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt`, `execution/GPUW6aNativePathValidation.kt` : projection, usages réellement consommés, préflight complet avant `device.create*`, sélection catalog-first et evidence.
- `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W6aLayerW4W5SurfacePixelTest.kt` : uniquement des témoins publics `Surface`; expected littéral ou oracle math indépendant fixé **avant** la construction de `Surface`.
- `refactor/waves/W06-layers-effects/status.md` : preuves, limites et handoff, sans budget revendiqué.

Les chemins abrégés `gpu-plan/...` et `gpu-renderer/...` désignent les propriétaires existants, non de nouveaux répertoires. Avant chaque modification, relever la signature et le propriétaire exacts dans le code courant ; ne pas créer une seconde autorité par commodité.

## Task 1 — Témoins publics et inventaire final

1. Inspecter les `finalW4eBindings` W6 et la route `encodeW4eNativePasses(commonSource=true)` ; inscrire dans le statut les IDs/ordinals des sites *par type*, sans copier des IDs de fixture comme contrat. Confirmer `SingleSampleDirectColor`, U16, D24S8 seulement Geometry, et les trois créations Geometry malgré le draw domain omis.
2. Ajouter au test Surface W6 trois oracles publics : Zero `Empty` sur une layer sans clip, Geometry DirectTriangle inverse sur une layer sans clip, Geometry fan non convexe ou à deux contours avec `INVERSE_EVEN_ODD`. Garder frontières non ambiguës aux centres de pixels. Distinguer `InverseDomainSource` Zero non vide de `Empty` si un Path public dégénéré atteint réellement cette forme ; sinon consigner la reachability auditée sans prétendre à un pixel positif. Chaque expected est établi avant `Surface`, et chaque résultat vérifie pixels et scopes `Render` + `Readback`.
3. Exécuter chaque nouveau sélecteur séparément avant production : `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6aLayerW4W5SurfacePixelTest.<nom exact>' --no-daemon --no-parallel --no-build-cache`. Un témoin peut déjà être GREEN : il caractérise la route, ce n'est pas un RED fabriqué. Si RED, localiser le défaut de rendu avant toute modification de recette ; ne jamais modifier l'expected pour masquer l'écart. Séparer XML JUnit et exit natif GLFW 133.
4. Commit ciblé des témoins et de l'inventaire, puis revue Sol sur la causalité publique et l'absence de confusion avec `InverseMask`.

## Task 2 — Zero, un bundle vertical complet

1. Geler une recette `ZeroCover` dédiée depuis le `PathRenderPass` final et son payload : owner `(id, ordinal, 0)`, prédicat `Zero`, `preserved-zero source form` `Empty`/`InverseDomainSource`, domaine, scissor source, target, load/store/blend, U16, ABI bind group 0, fullscreen draw 3. Refuser `Fill`/`Stroke`, V/I, D24S8 et `INVERSE_DOMAIN_ZERO_SOURCE` dans la route W6. Encoder les bytes canoniques du site, pas seulement ses tailles.
2. Ajouter famille/catalogue/seal et projection dans `GPUW6aLayerFramePlan`. Le catalogue exige exactement un owner Zero pour chaque binding Zero final, y compris cache hit ou draw omis ; les variantes non-Zero restent sur leur route historique jusqu'à Task 3, sans claim d'exhaustivité IV prématurée.
3. `W4eClipGraphLowerer.resourceUses` n'enregistre pour ce Zero que target + U réellement consommés, sans V/I/depth ; préserver les usages des autres phases W4d/W4e. Dans le préflight W6, comparer owner/canonique, ressources physiques, U-slice et bytes, packet/consumer par identité, source form, domaine/scissor, phase/sample/load/store/blend, usages enregistrés et ordre avant le premier `device.create*`.
4. Passer la map typée à l'encodeur W6. Pour le site Zero, dispatcher sur la recette **avant** le fallback `InverseDomain`, utiliser l'ABI, le pipeline, l'uniform et le domaine de la recette, puis confronter les champs préparés. Préserver l'appel W4e autonome sans map.
5. Compiler séparément `:gpu-plan:compileKotlin`, `:gpu-renderer:compileKotlin`, `:kanvas:compileTestKotlin` avec `--no-daemon --no-parallel --no-build-cache`. Lancer le nouveau témoin Zero, la classe W6a entière, la classe scan-span et les deux sélecteurs W4e de Task 3. Consigner XML et native séparément, `git diff --check`, commit ciblé, revue Sol ; corriger tout Critical/Important avant Task 3.

## Task 3 — Geometry DirectTriangle, trois bundles

1. Geler depuis le binding final trois sous-recettes ordonnées sous **le même** `PathRenderPass.id` et `ordinal` : `0=domainStencil`, `1=interiorZero`, `2=colorCover`. Sceller la topologie `DirectTriangle`, fill rule, domaine et scissor source distincts, quad/interior V/I exacts, U16, target/D24S8/sample/load/store/blend, stencil clear/replace/test et l'état `domainStencil pipeline requis, draw omis par commonSource`. Le contenu V/I et les bytes U entrent dans l'identité canonique ou dans un snapshot/digest exact dérivé du payload scellé ; aucune seconde géométrie hors `:math`.
2. Étendre catalogue/seal pour exiger exactement `0,1,2` dans cet ordre pour chaque Geometry Direct final, sans les confondre avec les deux **passes** `InverseMask.Geometry`. Projeter les trois sites W6, comparer les ressources/uses/slices/packet, les faits préparés et l'ordre avant allocation, même si le draw `domainStencil` est omis. Garder le D24S8 scene-local et son lifetime ; ne pas émettre de lease 2A1 dans cette tâche.
3. Encoder Geometry Direct depuis les trois sous-recettes en créant les trois pipelines catalogués ; la commande `domainStencil` absente sous `commonSource=true` ne supprime ni sa création ni sa validation. Confronter la géométrie préparée seulement comme assertion, sans nouvelle triangulation.
4. Rejouer les trois compiles séparées et les témoins Direct/Zero, classes W6a/scan-span et deux W4e ; XML F0/E0/S0, native séparée. Commit ciblé puis revue Sol ; le lot IV reste ouvert jusqu'au fan.

## Task 4 — Geometry Fan, fermeture IV et handoff

1. Étendre Geometry avec la variante `StencilEdgeFan` et les mêmes slots `0,1,2`. Geler fill rule, vertices/indices/débuts de contours exacts de `:math:geometry`, slices du payload final et stencil parity/winding de `interiorZero`. Deux fans de même taille mais de contenu distinct doivent avoir des identités canoniques distinctes. Comparer préparé et payload avant allocation, comme pour le Fan `InverseMask`, sans réutiliser son ABI mask.
2. Faire choisir le pipeline/stencil Geometry Fan depuis la recette, préserver Direct, Zero et fallback W4e autonome. Vérifier les trois bundles sur le témoin public fan et le cas `commonSource=true` : un pipeline non dessiné reste un site obligatoire.
3. Lancer séparément les trois compiles, les nouveaux sélecteurs, `W6aLayerW4W5SurfacePixelTest`, `W6InverseScanSpanSurfacePixelTest` et les deux sélecteurs W4e préservés. Exiger XML F0/E0/S0 ; reporter GLFW 133/134 comme **UNKNOWN** et non PASS. `git diff --check`, commit local et revue Sol transversale de Zero/Direct/Fan, owner/ordinal, catalogue, seal, packet, préflight et encodeur.
4. Mettre à jour `status.md` et la Draft PR #2409 empilée. `2A0c.IV` peut être déclaré review-clean seulement après cette revue ; `2A0d`, `2A1`, `2B` et la qualification native globale restent ouverts. Aucun B ni lease fictif n'est fixé avant l'inventaire final de tous les sites.
