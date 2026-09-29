# Gradient Alpha Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rendre PREMULTIPLIED observable et vérifié pour LinearGradient sRGB clamp, sans changer la composition de Surface.

**Architecture:** Transport immutable paint→IR→définition V4, puis un seul
graphe d'interpolation partagé par preuve et WGSL. STRAIGHT reste le défaut.
Archive Picture versionnée ; chemins incapables de conserver le mode refusés.

**Tech Stack:** Kotlin, Scene IR, GPU-plan V4, WGSL, backend GPU natif, JUnit 5.

**Spec:** [gradient-alpha-design.md](gradient-alpha-design.md), autorité sur le
schedule F32, les limites et les preuves attendues. Base c80e5b56d.

## Global Constraints

- Mode par défaut, sources de composition, images et leurs conventions, filtres, snapshots, formats de sortie et domaine linéaire restent inchangés.
- Aucun GM/adaptateur, référence PNG, seuil, score historique, exclusion, plafond de budget, enveloppe numérique ou contrôle d'autorité modifié.
- Fonts, codecs/décodage externe et `jpg-color-cube` restent hors périmètre.
- Pas de tests d'infrastructure : Surface publique, pixels natifs, Picture publique, readPixels/sentinel/refus/récupération et deuxième rendu.
- Géométrie dans math ; nomenclature I/F32/64 pour ses valeurs et types.
- Nouveau mode admis pour LinearGradient, espace effectif SRGB, tile CLAMP ; les autres combinaisons refusent sans downgrade.
- Un seul runtime Gradle/GPU à la fois. Terra implémente ; Sol révise. Pas de merge ni clôture W7.

## Review Focus

- Perte du mode dans capture/reconstruction/archive : témoin Picture mutée après capture et replay décodé.
- Programme straight réutilisé pour premul sur les mêmes stops : témoin deux modes dans la même frame, dans les deux ordres.
- Division interdite aux alphas nuls/minimaux : témoins zéro gauche/droite/deux, alpha1 et deux alphas non nuls.
- Flag perdu par wrappers ou fallback : témoin SRGB/Opacity/matrice et refus non-SRGB/non-CLAMP avec sentinel/récupération.
- Coût V4 sous-compté ou publication avant refus : budget B/B−1 dérivé avant essai et réutilisation de Surface.

---

### Task 1: Politique alpha publique end-to-end, preuve et pixels

**Files:** chemins relatifs à la racine ; modifier seulement les consommateurs
nécessaires du nouveau champ, sans refonte générale des fichiers existants.

- Public : `kanvas/src/main/kotlin/org/graphiks/kanvas/paint/Shader.kt`,
  `kanvas/src/main/kotlin/org/graphiks/kanvas/dsl/ShaderScopes.kt`.
- Capture : `render-ir/src/main/kotlin/org/graphiks/kanvas/render/ir/MaterialNode.kt`,
  `kanvas/src/main/kotlin/org/graphiks/kanvas/render/ir/PaintSceneAdapter.kt`.
- Archive : `render-ir/src/main/kotlin/org/graphiks/kanvas/render/ir/SceneArchiveCodec.kt`,
  `kanvas/src/main/kotlin/org/graphiks/kanvas/picture/Picture.kt`.
- Plans dans `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/` :
  `MaterialSourceConstructionV4.kt`, `GradientInterpolationPlanV4.kt`,
  `FrameSourceLayoutV4.kt`, `EffectiveMaterialPlanner.kt`,
  `ColorOperationGraphV1.kt`, `ColorSourceProofCompilerV1.kt`,
  diagnostics W5f existants et consommateurs de ces définitions si nécessaires.
- Lowering/guards dans `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/` :
  `W5aMaterialSourceStage.kt` et `W5fColorOperationEmitterV1.kt` seulement
  si la propagation du programme ou le refus legacy l'exige ; pas de formule
  premul WGSL indépendante du graphe.
- Gardes legacy éventuelles : `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUMaterialMapper.kt`.
- Create public test : `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W7GradientAlphaSurfacePixelTest.kt`.
- Create historical public fixture : `kanvas/src/test/resources/picture/format-15-straight-alpha-gradient-c80e5b56d.base64`.
- Reuse/extend independent oracle : `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5fColorCpuOracle.kt` ;
  utiliser `W5fSurfacePixelFixtures`, sans changer ses bornes ni l'enveloppe.

**Interfaces:**
- Produces public/IR `GradientAlphaMode { STRAIGHT, PREMULTIPLIED }` et
  dernier paramètre `alphaMode: GradientAlphaMode = GradientAlphaMode.STRAIGHT`
  de `Shader.LinearGradient`/`MaterialNode.LinearGradient.of` ; même propriété DSL.
- Consumes `PreparedSourceDefinitionV4`, `GradientStopSelection.interpolate`,
  preuve V4 et émission du graphe existants. Mode inclus dans identités,
  copies, bind/rebase et authentification, sans double préparation des stops.
  `GradientMetadata.recipeIdentity` et l'identité du slab restent indépendants
  du mode ; seules les identités d'exécution/programme/définition/graph changent.
  Après gardes alpha/domaine/tile, un stop unique peut devenir Solid.
- Produces Picture16/schema10 ; anciennes versions = STRAIGHT ; identifiants
  wire explicites 0=STRAIGHT, 1=PREMULTIPLIED ; inconnu = archive invalide.
- Refusal prefix for unsupported alpha combinations:
  `unsupported.material.gradient.alpha-mode`.

- [ ] **Step 1: Écrire les témoins publics avant les changements de sémantique.**
  Noms/contrats :
  `premultipliedTransparentEndpointPreservesWhite` : t=.5, blanc255→noir0,
  blanc destination, premul255 et straight205 avec enveloppe existante ;
  `premultipliedTwoNonzeroAlphasPreserveWeightedColor` : rouge128→bleu64,
  idéal (228,207,212,255), disjoint de straight (218,207,218,255).
  `zeroAndMinimalAlphaEndpointsStayFinite` : zéro aux deux côtés et chaque
  côté, alpha1/255, alpha128/64 ; pixels conformes sans NaN/refus inattendu.
  Construire les résultats par équations indépendantes dans l'oracle,
  `requireBounded` sur chaque attente ; Render/Readback et deux rendus.
  Introduire la déclaration API minimale si nécessaire pour compiler les
  tests : une erreur de compilation seule n'est pas le RED comportemental.
  Avant de modifier l'archiveur, capturer via Picture.toByteArray la fixture
  Picture15 décrite dans la spec ; conserver ses bytes en base64 dans la
  ressource indiquée. Elle est obtenue du writer historique réel, pas en
  réécrivant à la main le format ; ne pas garder de générateur de fixture
  dans les tests permanents.
  Ne pas activer la nouvelle formule avant d'avoir observé son refus ou
  les mauvais pixels via l'API réelle. Conserver séparément cette étape API.

- [ ] **Step 2: Exécuter le RED et préserver les preuves.**
  Commande de base ci-dessous, sélection
  `org.graphiks.kanvas.surface.W7GradientAlphaSurfacePixelTest` et archive
  `/private/tmp/kanvas-w7-alpha-mode.vpi7cG/red`.
  Attendu : refus typé ou différence de pixels pour le nouveau mode, pas
  une erreur de setup/GPU. Noter commande, code, message et cause avant patch.

- [ ] **Step 3: Transporter puis exécuter exactement le contrat de la spec.**
  Capture/reconstruction/DSL/canonical ids/archive ; propagation dans les
  métadonnées et la définition V4 ; admission effective sRGB/CLAMP et refus
  legacy ; recipe F32 par classes exactes d'alphas dans le graphe commun.
  La partition deux alphas positifs utilise la formule stable depuis min(a0,a1).
  Le mode STRAIGHT ne change pas. Ni seconde formule dans l'émetteur, ni
  pseudo-certificat, ni invalid→transparent. Si un cas ne se prouve pas,
  remonter le graphe et l'intervalle fautifs avant toute modification de preuve.

- [ ] **Step 4: Étendre les mêmes tests aux frontières et intégrations publiques.**
  `mixedModesKeepCaptureRangesAndOrder` : mêmes stops 2/16/17, deux ordres,
  mutations après enregistrement, Rect et Path fill admis, deux rendus.
  `wrappersAndMixedAaRootPreserveAlphaMode` : Opacity, working SRGB, local
  matrix/CoordClamp et gradient Rect sibling d'un stroke AA déjà admis.
  `pictureRoundTripPreservesAlphaMode` : Picture, archive non-null, playback
  direct/décodé sur Surface, pixels attendus ; fixture ancienne straight
  produit son résultat historique. Pas de test des détails d'infrastructure.
  `clampHardStopsAndDegenerateSourcesKeepAlphaMode` : un stop, égalités/hard
  stops, endpoints extérieurs clamp, axe dégénéré (CLAMP t=1, dernier stop,
  pas de moyenne), zéros et opaque.
  `unsupportedAlphaCombinationsRefuseBeforePublicationAndRecover` : non-SRGB,
  non-CLAMP, wrapper working non-SRGB ; sentinel intact, discard et deux
  rendus sains de la même Surface. Une route hors contrat ne réussit pas
  en réinterprétant le mode.
  `premultipliedBudgetRefusesPreciselyAndRecovers` : dériver B statiquement
  avant essai (targets/readback/V4 uniforms/stops/V-I le cas échéant),
  consigner la somme ; B passe, B−1 refuse, sentinel intact, récupération.
  Inclure une frame mixte straight/premul aux mêmes stops, avec budget
  comptant un seul slab partagé : ne pas seulement tester chaque mode isolé.
  Ne pas rechercher B en exécutant les budgets jusqu'à trouver le seuil.

- [ ] **Step 5: GREEN ciblé et contrôles voisins.**
  Rejouer la nouvelle classe, puis W5cGradientSurfacePixelTest,
  les deux `mixedHistoricalAndWorkingSrgb*` W5f et
  W7MixedRootAaRectSurfacePixelTest. Réutiliser les fixtures existantes.
  Un succès requiert assertions, XML et exit Gradle0 ; crash natif après
  assertions n'est pas un run vert. Rapporter les warnings existants.

- [ ] **Step 6: Auto-revue et commit de la tâche.**
  Vérifier les chemins de reconstruction/canonicalisation contre la carte
  de la spec, l'immutabilité et les refus. Rapport complet avec RED/GREEN,
  calcul B avant essai et limites ; commit des fichiers explicitement nommés.
  Le contrôleur exécute une seule globale bornée et le corpus après revue
  de tâche pour éviter des runs identiques par chaque agent.

**Commande ciblée :** `rtk proxy ./gradlew :kanvas:test --offline --no-daemon --no-build-cache --tests '<selection>' -I /private/tmp/kanvas-w7-image-opacity.YmtXjI/isolated.init.gradle -Pw7.validationDir=<archive neuve> --console=plain`.

## Vérification et livraison du lot par le contrôleur

- [ ] Revue Sol de tâche, corrections/re-review si nécessaire.
- [ ] Tests ciblés finaux, une globale `:kanvas:test` bornée à 240 s,
  résultats nommés, comparaison avec la dernière globale rouge/incomplète.
- [ ] Corpus631/443, mêmes références/seuils/scopes, comparaison des198 anciennes
  empreintes RGBA et issues contre `mixed-root-ff628a94d.json`. Pas de gain
  revendiqué pour un GM qui n'active pas le nouveau mode.
- [ ] Revue finale Sol de toute la branche, preuves et réserves dans
  `pilotage.md`/`status.md`, PR draft stackée sur #2420 ; W7 reste actif.
