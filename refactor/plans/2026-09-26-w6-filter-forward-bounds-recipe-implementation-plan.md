# W6 Contextual Filter Bounds Recipe Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Réserver chaque parent W6 à partir de la sortie réellement produite par ses filtres, sans perdre les halos ni allouer systématiquement le clip entier.

**Architecture:** `:gpu-plan` lie chaque DAG capturé une fois dans une recette contextuelle immuable. La même recette propage d'abord les demandes inverses, évalue ensuite les bounds forward sur un domaine source déjà scellé, puis est abaissée vers les `FilterPass` existants après réservation W6a. Les sorties des layers, draws filtrés et agrégats Picture alimentent le parent avant que celui-ci ne fige sa géométrie ; le renderer ne replanifie rien.

**Tech Stack:** Kotlin/JVM, `:math`, `:render-ir`, `:gpu-plan`, JUnit 5, APIs publiques `Surface`/`Canvas`/`Picture`, WebGPU/WGSL déjà présents.

**Spec:** `refactor/specs/2026-09-26-w6-filter-forward-bounds-recipe-design.md` (validée et relue par Astra) ; autorités parentes `refactor/specs/2026-09-16-w6-layers-effects-design.md` §6 et `refactor/specs/2026-09-22-w6b-w6e-stacked-delivery-design.md` §§3, 7–10. Base `codex/w6d-advanced-effects` à `47467e75712c0be0ba9794976c29cdcd51fa24bd` ; branche prérequis `codex/w6e-filter-bounds-recipe` ; W6e existe séparément à `b15247b190140b3887305367661b95cda3dfe07b` avant restack.

## Global Constraints

- Aucune API publique, famille, sérialisation Picture/wire, version, format, kernel, planner renderer, fallback legacy ou route submit supplémentaire. Le prérequis change seulement l'autorité de calcul interne `:gpu-plan` ; W6e reste une vague de convergence sans nouvel algorithme.
- `:math` possède tous les nouveaux objets géométriques ; noms de précision `I32`/`I64`/`F32`/`F64`. Calcul en F64, arrondi extérieur et conversions I32 checked ; aucune géométrie privée dans `:kanvas` ou `:gpu-renderer`.
- Garder séparés `knownContent`, `desiredOutput`, `requiredInput`, `producedOutput` et le domaine/ancrage **physiques de la source**. Le domaine source est scellé avant son filtre et ne se déduit pas de sa propre sortie ; chaque opération borne sa sortie par sa demande propre, pas par le clip terminal.
- La recette conserve les identités capturées, sources implicites contextuelles, provenance Picture, positions Merge/Blend, ordre Compose, générations et cache keys ; l'abaissement n'interprète pas une seconde fois le DAG. Cache chaud et froid ont le même budget pessimiste et la même durée de lease.
- Préserver `recordedInnerClip` versus `deferredCompositeClip`, les hints `saveLayer` non hard-clips, backdrop au save, `initWithPrevious` après les draws enfants, l'ordre Begin → enfants → Seal → lecteur et la terminalité atomique des refus.
- Tests exclusivement publics : `Surface`, `Canvas`, `Picture`, `render()`, `readPixels`, diagnostics, pixels, scopes `Render`/`Readback`, replay mémoire/wire, discard/re-record. Aucun test d'infrastructure, réflexion, mock, fake device, inspection de planner ou compteurs privés. Tous les expected/oracles sont calculés avant `Surface` ou `PictureRecorder` ; exact pour identity/Crop/Offset/composites, oracle indépendant à tolérance locale pour Blur/lighting.
- Exclure fonts/glyphs, codecs/formats externes, GMs, dashboard, renders, références, scores/rebaseline, suite Skia globale, `jpg-color-cube`, frontend SkSL arbitraire, F16 positif, W8 et claim ISO. Garder RGBA8 positif et F16/HDR refusal-only.
- Exécuter les Gradle selectors **sérialisés** via `rtk`. Noter séparément exit Gradle, XML JUnit, exit natif et commit. Un worker natif 133/134 avec XML vert est `UNKNOWN`, jamais PASS global ; un vrai RED est une assertion publique nommée, pas une erreur de compilation ou d'environnement.
- Pour chaque classe de test citée ci-dessous, lancer séparément `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.NomDeClasse'` ou `org.graphiks.kanvas.picture.NomDeClasse` selon son package ; pour les compilations, `rtk ./gradlew :gpu-plan:compileKotlin :kanvas:compileTestKotlin`.
- Le fichier utilisateur déjà modifié `.superpowers/sdd/2026-09-22-w6d-advanced-effects-implementation-plan/progress.md` reste intact et hors des commits. Terra implémente, Sol relit chaque tâche puis la branche entière ; Astra seulement si un blocage architectural démontré subsiste. Une seule vague de correction bornée par review, puis re-review ciblée ; aucun merge automatique.

## Review Focus

- `Compose(Offset(-20), Offset(+20))` doit conserver l'intermédiaire hors du clip terminal qui revient dans le pixel final ; Task 1 le teste.
- Un `ImageFilter.Offset` porté par un **draw direct** sous parent non filtré doit agrandir le contenu parent, pas seulement un `BeginLayer` filtré ; Task 2 le teste.
- Lighting doit choisir ses bords CLAMP/DECAL depuis le domaine source scellé, tout en produisant depuis transparent black sur sa demande ; Task 1 le teste.
- Un petit Crop DECAL/ColorFilter identité sous grand clip ne doit pas grossir son target au clip complet et B/B−1 doit rester causal ; Task 3 le teste.
- Un Picture filtré partagé puis égal-par-valeur mais distinct, rejoué en mémoire et wire, doit préserver identité, demande inverse et ordre de ses enfants ; Task 2 le teste.

---

## Code Map et interfaces partagées

| Fichier | Responsabilité décidée |
| --- | --- |
| `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W6bContextualFilterRecipeV1.kt` (nouveau) | Recette immuable, faits de source sans `PlanResourceId`, demandes par nœud, bounds forward et provenance ; aucune allocation/pass. |
| `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W6bFilterGraphConstruction.kt` | Lier une recette par `PositiveOccurrence`, déplacer les décisions de bounds de `materializeNode` vers elle et abaisser cette recette, sans nouveau DAG walker à l'abaissement. |
| `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W6cSpatialBoundsPlanner.kt` | Garder l'autorité Crop/Offset/Tile existante ; exposer ses calculs au recipe owner sans les dupliquer dans W6a. |
| `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W6aLayerGraphConstruction.kt` | Lier recette avant réservation, ordonner demande inverse → source scellée → sortie forward → domaine restore/parent ; inclure draws directs et Picture entries. |
| `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W6FilterBoundsRecipeSurfacePixelTest.kt` (nouveau) | Témoins pixels publics layer, draw direct, composition, lighting, budget et recovery. |
| `kanvas/src/test/kotlin/org/graphiks/kanvas/picture/W6FilterBoundsRecipePictureTest.kt` (nouveau) | Témoins publics Picture mémoire/wire, partage/equal-distinct, clip différé et enfants. |
| `refactor/waves/W06-layers-effects/status.md` | Checkpoint prérequis, sorties distinctes XML/Gradle/native, PR stackée et report W6e. |

Interfaces internes à établir dans Task 1 et à conserver dans Tasks 2–3 :

```kotlin
internal class W6bFilterSourceFactsV1(
    sourceDomainDeviceI32: RectI32,
    knownContentDeviceI32: RectI32?,
    desiredOutputDeviceI32: RectI32,
    mapping: LayerMappingF64,
)
internal class W6bContextualFilterRecipeV1 {
    fun copyRequiredInputDeviceI32(): RectI32?
    fun evaluate(
        source: W6bFilterSourceFactsV1,
        runtimeCatalog: RuntimeEffectSemanticCatalogSnapshot,
    ): W6bEvaluatedFilterRecipeV1
}
internal class W6bEvaluatedFilterRecipeV1 {
    fun copyProducedOutputDeviceI32(): RectI32?
}
internal fun W6bFilterGraphConstruction.bindOccurrenceRecipe(
    occurrence: W6bFilterGraphConstruction.PositiveOccurrence,
    desiredOutputDeviceI32: RectI32,
    mapping: LayerMappingF64,
): W6bContextualFilterRecipeV1
```

Les classes copient leurs rectangles/mapping à l'entrée et à la sortie ; `W6bFilterSourceFactsV1` est un conteneur de faits, pas une nouvelle primitive géométrique. La recette peut porter des opérations typées privées supplémentaires, mais pas de `PlanResourceId` final. Les feuilles Picture utilisent la découverte d'agrégat W6a existante pour leurs faits de contenu ; avancer cette découverte avant le scellement des géométries et ajouter une résolution sémantique sans émission de ressource dans ce même owner, pas dans un second parcours de scène. La signature de `freezeImageOccurrence` reçoit l'évaluation déjà scellée en plus de son `SourceBinding` physique ; elle refuse une discordance entre géométrie évaluée et binding, puis abaisse sans relire `occurrence.table`.

### Task 1: Recette unique et sorties des layers filtrés

**Agent:** Terra implémente ; Sol review.

**Files:** nouveau `W6bContextualFilterRecipeV1.kt` ; modifier `W6bFilterGraphConstruction.kt`, `W6cSpatialBoundsPlanner.kt`, `W6aLayerGraphConstruction.kt` ; créer `W6FilterBoundsRecipeSurfacePixelTest.kt`.

**Interfaces:** Produit les quatre types/fonctions ci-dessus. Consomme `PositiveOccurrence`, `LayerMappingF64`, `FilterBoundsPlanV1` et les opérations W6b/W6c/W6d existantes. Task 2 ajoute les consommateurs directs/Picture sans modifier leur contrat ; Task 3 audite les ressources résultantes.

- [ ] **Step 1 — RED public.** Ajouter `nestedOffsetAndBlurExpandOnlyTheirProducedOutput` : `Surface(4,1)` avec trois `saveLayer` non bornés dont l'interne porte `ImageFilter.Offset(1f,0f)` transforme un pixel bleu `[0,1)` en `[1,2)` ; un second témoin Blur 7×7 utilise l'oracle CPU W6b existant et conserve le halo hors contenu brut. Ajouter `composeKeepsIntermediateOutsideTerminalClip` avec `Compose(Offset(-20), Offset(+20))` et un pixel source bleu 1×1 : sortie finale bleue à x=0. Ajouter `nestedLightingRetainsSourceEdgeAndTransparentOutput` : source 3×3, oracle W6d indépendant, sortie sur un texel transparent demandé et bord CLAMP/DECAL identique à la source. Chaque rendu exige `Render` + `Readback`.
- [ ] **Step 2 — Vérifier le RED.** `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6FilterBoundsRecipeSurfacePixelTest'` ; garder les échecs JUnit de pixels nommés sur W6d, classer séparément l'exit natif 133/134.
- [ ] **Step 3 — Recette et demande inverse.** Implémenter les signatures du Code Map. Lier chaque occurrence image/mask et ses positions une seule fois ; propager la demande terminale à chaque input/opération de la recette, y compris Compose, Merge/Blend et Picture, avant de sceller toute production. `reverseInputDemand` devient un adaptateur sur cette même recette pour les anciens appelants, pas un second switch du DAG. Les familles W6b/W6c/W6d utilisent les helpers de bounds existants comme autorité unique ; sélectionner les opérations et valider le catalogue runtime pendant `evaluate`, avant toute ressource, pas au renderer.
- [ ] **Step 4 — Source puis forward.** Dans W6a, conserver le clip parent demandé distinct de l'étendue source ; réunir contenu direct, sorties enfants et snapshot/hint admissible pour sceller le domaine/ancrage de source **avant** d'appeler `evaluate`. Calculer `producedOutputByScope` depuis le résultat terminal filtré puis le restore (alpha/color filter/blend), et réserver le parent avec le domaine écrit, non avec la taille physique de l'enfant. Refactorer `freezeImageOccurrence` pour abaisser l'évaluation déjà produite dans le même ordre de passes/IDs, sans seconde lecture du DAG.
- [ ] **Step 5 — GREEN et préservation sérialisés.** Rejouer le nouveau selector, puis `W6aNestedLayerSurfacePixelTest`, `W6bImageBlurSurfacePixelTest`, `W6cComposeSurfaceTest`, `W6dLightingSurfacePixelTest`, `W6dAdvancedSamplingSurfacePixelTest` et `:gpu-plan:compileKotlin :kanvas:compileTestKotlin`. Vérifier les XML de méthodes et noter natif `UNKNOWN` si besoin.
- [ ] **Step 6 — Commit.** Ne stage que les fichiers Task 1, exécuter `rtk git diff --cached --check`, puis commit `feat(gpu-plan): evaluate contextual filter bounds before layer reservation`. Sol relit le diff et les quatre régions ; corriger au plus une vague bornée puis re-review ciblée avant Task 2.

### Task 2: Auto-layers des draws et agrégats Picture

**Agent:** Terra implémente ; Sol review.

**Files:** modifier `W6aLayerGraphConstruction.kt`, `W6bFilterGraphConstruction.kt`, la recette Task 1 et les deux nouveaux tests publics. Réutiliser `PictureStreamAggregateDiscoveryV1` ; ne pas modifier `:render-ir`, wire ou `:gpu-renderer`.

**Interfaces:** Consomme `bindOccurrenceRecipe`/`evaluate` de Task 1. Produit la contribution `producedOutput` d'une entrée draw/Picture au `knownContent` du scope courant avant réservation et les faits Picture sémantiques sans `PlanResourceId`.

- [ ] **Step 1 — RED public direct.** Ajouter `directOffsetAndBlurSurviveUnfilteredParent` : `saveLayer()` sans filtre, `drawRect([0,1), Paint(imageFilter=Offset(+1)))`, restore ; attendu exact `[transparent, blue, transparent, transparent]` sur 4×1. Variante Blur sur 7×7 avec oracle indépendant et tolérance locale. Ajouter `directOffsetUnderPictureAggregateSurvivesParentRestore` dans le test Picture, avec un sibling non filtré et `drawPicture` porteur du filtre ; préserver l'ordre des pixels.
- [ ] **Step 2 — Vérifier le RED.** Lancer les deux classes ciblées séparément ; exiger un échec d'assertion public causal pour la variante directe avant correction. Ne pas attribuer un exit 133 au code.
- [ ] **Step 3 — Propager les sorties directes.** Avant `sealGeometry`, évaluer les recettes des auto-layers draws/Picture entries avec leur raster source, mapping capturé et demande inverse propres. Ajouter leur domaine terminal après clip/blend au contenu du scope. Remplacer le seul cas spécial `MaskFilter.Blur` de `directKnownByScope` par la même autorité, sans perdre ses styles ni les draws non filtrés. La création physique tardive `appendFrozenOccurrence` consomme ces évaluations, avec la même identité et la même provenance.
- [ ] **Step 4 — Picture replay.** Ajouter `sharedAndEqualDistinctPictureFiltersKeepDemandAndIdentityAcrossWire` : attendu et Picture créés avant `Surface`, une source partagée deux fois et un nœud égal mais distinct ; comparer pixels sur replay mémoire et `Picture.fromByteArray(picture.toByteArray())`, y compris un parent à `deferredCompositeClip` et un `recordedInnerClip` enfant qui ne coupe pas le halo du filtre parent. Vérifier `Render` + `Readback`.
- [ ] **Step 5 — GREEN et préservation sérialisés.** Relancer les deux classes nouvelles, `W6bMaskBlurAutoLayerSurfacePixelTest`, `W6cSpatialDagPictureTest`, `W6dPictureFilterSurfacePixelTest`, `W6dBackdropPreviousSurfacePixelTest` et les deux compilations Task 1. Vérifier la séquence Begin → enfants → Seal → lecteur dans la review du plan gelé, sans test d'infrastructure.
- [ ] **Step 6 — Commit.** Stage uniquement les fichiers Task 2, `rtk git diff --cached --check`, commit `fix(gpu-plan): include direct filter and picture output in parent bounds`, puis review Sol et au plus une correction/re-review ciblée.

### Task 3: Budget exact, temporalité et récupération

**Agent:** Terra implémente ; Sol review.

**Files:** modifier seulement les owners Task 1–2 si un RED les désigne ; étendre `W6FilterBoundsRecipeSurfacePixelTest.kt` et `W6FilterBoundsRecipePictureTest.kt` ; mettre à jour `refactor/waves/W06-layers-effects/status.md` après vérification. Aucun nouveau type de budget/cache.

**Interfaces:** Consomme la recette et les domaines terminaux Tasks 1–2 ; ne change ni leur API interne ni les clés `FilterEvaluationKeyV1`. Produit les preuves publiques B/B−1, cache, ordre temporel, refus terminal et reprise.

- [ ] **Step 1 — RED budget/non-expansion.** Ajouter `tinyCropAndIdentityKeepContentSizedBudget` : sous grand clip, 1×1 Crop DECAL et ColorFilter identité rendent leurs pixels exacts ; dériver B **avant** `Surface` depuis root, source/layer/filter targets réels et readback aligné du fixture, documenter l'addition I64 dans le test. `RenderConfig(frameLocalBudgetBytes=B)` rend ; B−1 refuse avec diagnostic owner, sentinelle `readPixels` intacte et même `Surface` réenregistrable. Le test doit discriminer le correctif erroné `producedOutput = desiredOutput`.
- [ ] **Step 2 — RED cache/temporalité.** Ajouter `warmPictureReplayRetainsColdBudgetAndIdentity` : le même Picture à filtre spatial est rejoué à froid/chaud avec même B/B−1, sans cache-hit gratuit. Ajouter `backdropAndPreviousKeepSaveThenPostChildOrder` : pixel parent/sibling/child distinct, backdrop lit le parent au save et `initWithPrevious` filtre parent+enfant après enfants ; expected exact avant `Surface`.
- [ ] **Step 3 — Vérifier les RED.** Exécuter les deux classes nouvelles séparément contre le code Tasks 1–2 ; si une assertion passe déjà, conserver le test de préservation ; si RED, attribuer le propriétaire précis avant correction. Ne jamais créer de test privé pour forcer un RED.
- [ ] **Step 4 — Correction minimale.** Ajuster seulement union des domaines, l'enveloppe checked, la conservation des identités/leases ou l'ordre de snapshot dans `:gpu-plan` selon le RED. Les refus budget restent avant publication ; aucun resize, pass, resource, key ou fallback n'est inventé après freeze. Conserver les diagnostics W6 et la récupération de la même `Surface`.
- [ ] **Step 5 — GREEN final ciblé.** Lancer les deux classes nouvelles, `W6aLayerBudgetRecoverySurfacePixelTest`, `W6bBudgetRecoverySurfacePixelTest`, `W6bFilterAdmissionRecoverySurfaceTest`, `W6cSpatialDagAdmissionSurfaceTest`, `W6cSpatialCacheRecoverySurfaceTest`, `W6dAdvancedRecoverySurfacePixelTest`, `W6dBackdropPreviousSurfacePixelTest`, `W6dMagnifierReverseDemandSurfacePixelTest`, `W6dPictureRuntimeEffectPictureTest`, puis `:gpu-plan:compileKotlin :kanvas:compileTestKotlin`. Vérifier sortie Gradle/XML/native séparément ; ne pas exécuter GMs, suite Skia globale ou `jpg-color-cube`.
- [ ] **Step 6 — Checkpoint et commit.** Décrire les résultats exacts, exclusions, tests RED/GREEN et limites natives dans `refactor/waves/W06-layers-effects/status.md` ; stage uniquement Task 3, `rtk git diff --cached --check`, commit `test(gpu-plan): prove contextual bounds budget and recovery`. Sol review puis au plus une correction/re-review ciblée.

## Gate de branche et reprise W6e (contrôleur, après Tasks 1–3)

1. Une review Sol whole-branch compare `codex/w6d-advanced-effects..HEAD` à la spec, confirme absence de second DAG interpreter, de `PlanResourceId` dans la recette, de replanning renderer et de finding Critical/Important. Une seule correction bornée et re-review ciblée si nécessaire.
2. Vérifier `rtk git diff --check codex/w6d-advanced-effects..HEAD`, compilations et XML ciblés frais ; publier une Draft PR vers `codex/w6d-advanced-effects`, l'attacher à la tâche, sans merge. Le fichier de suivi utilisateur non commité reste hors de la PR.
3. Rebaser `codex/w6e-effects-convergence` sur cette branche **sans perdre ses commits/tests Task 1–5**, puis reprendre son plan existant à Task 5. Exécuter sa matrice publique 22 familles + Offset trois parents ; si RED persiste, corriger dans l'owner prérequis avec un nouveau gate/review avant de revendiquer Task 5 GREEN. Ensuite seulement terminer W6e Task 6 et sa Draft PR ciblant la branche prérequis.
