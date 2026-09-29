# W7 Mixed Root AA Rect Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rendre des Rect STROKE AA plain solid au milieu de Rect FILL en dégradé linéaire, sans layer artificielle.

**Architecture:** Admission atomique racine bornée dans W6, opt-in source Rect W4d, outline math Stroke et autorité native AaResolvedColor existante.

**Tech Stack:** Kotlin, Scene IR/gpu-plan, WebGPU natif, JUnit Surface public.

**Spec:** [mixed-root-aa-rect-design.md](mixed-root-aa-rect-design.md)

## Global Constraints

- Fonts, codecs, external decoding et `jpg-color-cube` restent hors périmètre.
- Aucun changement de GM/adaptateur, référence, seuil, exclusion, budget cap, enveloppe numérique ou contrôle d'autorité pour faire passer un cas.
- Aucun nouveau test d'infrastructure : Surface public, pixels, Render/Readback, second rendu, refus/sentinel/récupération.
- Les objets géométriques restent dans math, nomenclature I/F32/64; aucun nouveau type géométrique n'est prévu.
- Un seul processus Gradle/GPU à la fois, terminal réel avant handoff.
- Suite globale connue rouge/incomplète : une tentative bornée, échecs et warnings rapportés; draft empilée seulement, ni merge ni clôture W7.

## Review Focus

Task1 couvre les cinq risques : ordre des occurrences, alpha appliqué deux fois,
trou/coin du stroke, transform/clip/hairline, publication partielle avant refus.
L'admission atomique et les anciens standalone sont contrôlés par les négatifs
et suites voisines. Images/Picture/Path nouveaux restent hors domaine.

### Task 1: Source Rect stroke AA dans un mélange racine borné

**Files:**
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W6aLayerPlanCompiler.kt` — `select` et helpers privés d'admission racine.
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4dGeneralPathPlanCompiler.kt` — opt-in Rect source indépendant, preflight/classification, factory interne `w6RootAaRectStrokeSource`.
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanW4dAaSourceBindingV1.kt` — recette Fill/Stroke explicite.
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W7MixedRootAaRectSurfacePixelTest.kt`.

**Interfaces:** Consomme `PathDrawGeometry.Stroke.valueF32.copyFillGeometryF32`,
`AaResolvedColor`, `acceptsW6AaColorSourceScope` et le composite root existant.
Produit `internal fun w6RootAaRectStrokeSource(catalog: RuntimeEffectSemanticCatalogSnapshot): W4dGeneralPathPlanCompiler`
dans le companion, avec opt-in privé conservé par les copies du compiler.
Aucun nouveau contrat natif; si un garde supplémentaire bloque, diagnostic au
contrôleur avant élargissement du lot.

- [ ] **Step 1: Ajouter les témoins publics avant la production.** Chaque
  positif impose Render/Readback et un second `surface.render()` byte-identique.
  Nettoyage GPU `@AfterAll` comme les W7 existants, pas de capability catch en PASS.
  - `mixed root gradient and aa ring preserve pixels and order` : Surface8×8,
    gradient linéaire bleu→bleu plein viewport (shader réellement enregistré),
    Rect centreline[2.5,2.5,5.5,5.5] stroke rouge width1 AA. Oracle8×8 : anneau
    [2,6) moins[3,5) rouge, le reste bleu. Inverser les draws : tout bleu.
  - `mixed root ring alpha is composed once` : même fond bleu, Rect rouge
    alpha128 centreline[2.5,2.5,5.5,5.5], width1 : full ring=(188,0,187,255),
    trou/extérieur bleu. Variante centreline[2,2,6,6] width1 : au bord gauche
    (1,3), coverage1/2 donne (137,0,224,255), tolerance1 par RGB pour stockage
    sRGB/UNORM; alpha255 exact. Le coin full(2,2) de la première fixture garde
    le même oracle full, sans accumulation de bandes.
  - `mixed root two rings retain sibling chronology` : fond gradient bleu,
    ring rouge précédent, fill vert hard[2,2,3,3], deuxième ring jaune centreline
    [4.5,2.5,7.5,5.5], width1 AA. (2,2)=vert, (2,4)=rouge, (4,2)=jaune,
    (5,3)=rouge, (6,3)=bleu, (0,0)=bleu. Second rendu identique.
  - `mixed root scaled hairline respects device clip` : Surface8×8 fond
    gradient bleu, clip hard device[2,2,5,6], scale2, Rect local centreline
    [1.25,1.25,2.75,2.75], width0 : ring[2,6) moins[3,5), coupé àx<5.
    Rouge(2,2)/(4,2)/(2,4), bleu(5,2)/(3,3)/(0,0). Variante translate(1,1)
    et Rect local[1.5,1.5,4.5,4.5] width1 atteint le même anneau non coupé.
  - `mixed root invalid siblings refuse without publication` : partir du
    mélange de base, variantes stroke Shader.SolidColor, stroke non-SrcOver,
    clip complexe, sibling filtre/Picture, stroke transparent/offscreen.
    Mesurer et figer les diagnostics historiques réellement observés avant
    patch; ne pas promettre une réussite nouvelle de ces variantes. Sentinel
    0x5a inchangé pour chaque refus, discard puis hard Rect bleu sur la même
    Surface, deux rendus réussis. Si une variante est déjà rendable, en faire
    un contrôle positif indépendant et le signaler, ne pas inventer un refus.
  - `mixed root exact budget refuses before publication and recovers` :
    fixture8×8 gradient bleu→bleu + ring opaque centreline[2.5,2.5,5.5,5.5].
    Avant son premier rendu, écrire dans le rapport la somme B de toutes les
    ressources physiques (root/readback, AA4/resolve/depth4, V/I/U alloués,
    gradient stops, uniforms composites et matériaux). Fixer B littéral dans
    le test; ne lire ni peak ni échec pour le choisir. B rend l'oracle, B−1
    refuse le budget W6, sentinel intact puis récupération sur la même Surface.

- [ ] **Step 2: RED isolé, causal et archivé.**
  `rtk proxy ./gradlew :kanvas:test --offline --no-build-cache --tests org.graphiks.kanvas.surface.W7MixedRootAaRectSurfacePixelTest -I /private/tmp/kanvas-w7-image-opacity.YmtXjI/isolated.init.gradle -Pw7.validationDir=/private/tmp/kanvas-w7-next.0hEQlf/mixed-red --console=plain`.
  Le mélange positif doit échouer sur le refus AA stroke existant. Pas de
  compile error comme RED. Corriger d'abord tout oracle/test invalide.

- [ ] **Step 3: Implémenter le domaine de la spec, sans route alternative.**
  Tout draw doit satisfaire le domaine; stroke+LinearGradient obligatoires.
  W6 reste prioritaire pour ses layers/filtres historiques. Pour la nouvelle
  branche, sélectionner tous les segments avant candidat; garder la décision
  historique si aucun candidat complet, sans retry après admission. Source
  Rect dédiée à cette branche uniquement; provenance/styles inchangés. Encoder
  réellement Stroke dans sa recipe avec son fill mesh, sans changer Fill.
  Réutiliser la construction root, origine0, resolve/composite adjacent existants.

- [ ] **Step 4: GREEN ciblé et contrôles proches sérialisés.** Même commande,
  archive `mixed-green`; puis `W7StrokeRoutingSurfacePixelTest`,
  `W7RootAaSurfacePixelTest`, `W7AaPathLayerSurfacePixelTest`,
  `W7AaMaskBlurSurfacePixelTest`, `W6aLayerBudgetRecoverySurfacePixelTest`,
  archive `mixed-related`. Chaque run doit réellement terminer avant suivant.
  Ne pas lancer la globale : le contrôleur en prend une unique tentative finale.

- [ ] **Step 5: Self-review et commit limité.** `git diff --check`, ne committer
  que les quatre fichiers possédés. Rapport détaillé scratch : RED/GREEN,
  commandes/exits/XML, budget pré-calculé, échecs/warnings et limites. Pas de
  push, pas de sous-agents; rendre l'ownership runtime au contrôleur.

### Clôture contrôleur

- [ ] Revue tâche Sol, corrections/re-review si nécessaire.
- [ ] Tentative `:kanvas:test` globale unique bornée240s, puis final ciblé.
- [ ] Mesure corpus631 au SHA exact, timeout30s et comparaison au snapshot
  `aa-mask-82893045c.json`, pertes/gains/pixels/diagnostics séparés.
- [ ] Suivi à jour, revue globale Sol et draft empilée sur #2419; aucun merge.

## Self-review du plan

Une tâche dépendante produit un seul contrat traversant trois fichiers;
pas de tâche de scaffolding. Les cinq risques ont des pixels/refus publics.
Avis Astra réduit par pilotage aux siblings Rect/LinearGradient pour limiter
le premier domaine; images en mémoire reportées explicitement, pas promises.
Le budget est une dérivation physique préalable confiée au worker, pas un
oracle appris du planner. Toute divergence doit être rapportée avant patch.
