# W7 Root AA Source Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rendre un Path AA SrcOver racine dans une frame W6 ordinaire en réemployant sa source AA isolée.

**Architecture:** Source MSAA 4×/resolve 1× par occurrence, composite immédiat vers la racine 1×. Même autorité, mêmes ressources et budgets ; root n'a pas de scope de layer et son origine native est zéro.

**Tech Stack:** Kotlin, Scene IR/gpu-plan, WebGPU natif, tests publics JUnit Surface.

**Spec:** [root-aa-design.md](root-aa-design.md)

## Global Constraints

- Fonts, codecs, external decoding et `jpg-color-cube` restent hors périmètre.
- Ne modifier ni fixture GM, adaptateur GM, référence, seuil, exclusion, budget cap, enveloppe numérique ou contrôle d'autorité pour faire passer un cas.
- Aucun nouveau test d'infrastructure : pixels publics Surface, Render/Readback, second rendu, refus/sentinel et récupération uniquement.
- AA filtré, PLUS/destination-read, root dans une frame W6b, clips complexes et retrait du legacy restent hors de cette admission.
- Les types géométriques appartiennent à math avec nomenclature I/F32/64 ; aucun nouveau type géométrique n'est nécessaire ici.
- Un seul processus Gradle/GPU à la fois, terminal réel avant handoff ; tous les échecs et warnings restent rapportés.
- Mesurer les mêmes 631 identités avec timeout 30 s ; ne pas présenter un diagnostic déplacé comme un gain de rendu.
- Suite globale connue rouge/incomplète : publication draft empilée sur #2417 seulement, sans merge ni clôture W7.

## Review Focus

Les cinq risques sont couverts par Task 1 : ordre des siblings (témoin 1),
alpha appliqué deux fois (2), confusion origine root/layer et clip (3),
partage stencil/resolve (4), sous-comptage ou publication avant refus (5–6).
Les autres inputs restent explicitement non admis, pas implicitement certifiés.

### Task 1: Root AA occurrences avec l'autorité existante

**Files:**
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W6aLayerPlanCompiler.kt` — sélection vers ligne 212.
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W6aLayerGraphConstruction.kt` — AA vers 2661 et binding natif vers 3053.
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W7RootAaSurfacePixelTest.kt`.

**Interfaces:** Consomme `acceptsW6AaColorSourceScope`, `AaResolvedColor`,
`rebindW4eV6` et `PlanW4dAaSourceBindingV1`. Produit leurs mêmes types, avec
une occurrence root correctement scellée ; aucune nouvelle API/native recipe.

- [ ] **Step 1: Écrire les témoins publics avant code de production.**

1. `root aa preserves sibling chronology` : Surface7×7, fond rouge opaque,
   triangle bleu `(1,1)-(5,1)-(1,5)`, layer verte `[2,2,3,3]`. Deux ordres :
   root→layer donne vert à(2,2), layer→root donne bleu. Toujours bleu(1,1),
   rouge(0,0)/(5,5), alpha255. Second rendu Surface égal byte à byte.
2. `root aa alpha is composed once` : fond noir, Path rectangle `[1,1,6,6]`
   blanc alpha128, layer vide. Pixel(3,3)=(188,188,188,255), extérieur noir.
   Picture sans clip enregistré via API publique puis playback sur Surface,
   mêmes literals et pixels ; pas d'inspection de DisplayOps.
3. `root aa transform clip and layer origin remain distinct` : Surface9×8,
   clip hard device `[3,2,5,4]` posé avant translate(2,1), Path rectangle local
   `[0,0,4,4]` bleu. Restore ; layerAA `[5,4,9,8]`, triangle vert aux points
   `(5,4)-(9,4)-(5,8)`. Bleu(3,2), transparent(2,2)/(5,2), vert(5,4).
   Le clip doit être explicitement non-AA ; aucun shader/material étendu.
4. `root aa stencil sources stay isolated` : Surface7×7, deux L concaves
   disjoints. Premier vert points `(1,1),(3,1),(3,3),(2,3),(2,5),(1,5)` ;
   second bleu est le premier translaté de(3,0). Layer ordinaire jaune sur
   `[0,6,1,7]` entre les deux draws root. Vert(1,1), bleu(4,1), jaune(0,6),
   transparent(2,4)/(5,4)/(6,6). Second rendu égal.
5. `root aa exact budget refuses before publication and recovers` : fixture
   de la spec B=27772 (somme écrite avant Surface). À B pixel triangle bleu
   (1,1), extérieur transparent. À27771 `readPixels` refuse exactement le
   préfixe `w6a.layer.frame_budget_exceeded`, sentinel0x5a inchangé. Discard
   puis rectangle hard bleu : pixel exact sur la même Surface, Render/Readback.
6. `unsupported root aa siblings refuse transactionally` : après un root AA
   admissible, ajouter successivement (fixtures séparées) rootAA+maskBlur,
   rootAA+imageBlur, rootAA+PLUS, puis une occurrence W6b filtrée distincte.
   Conserver les diagnostics observés sur la base, sans changer leurs guards.
   Sentinel intact puis discard+rectangle hard réussi sur la même Surface.
   Ajouter un contrôle hard-path root + layer ordinaire positif pour préserver
   la route historique ; ces contrôles peuvent être déjà verts sur la base.

Tous les positifs exigent Render/Readback et pixels indépendants. GPU natif
connu disponible : aucun catch de capability refusé ne vaut un PASS positif.
Nettoyage GPU au `@AfterAll`, pattern W7 existant. Pas de nouveau helper product.

- [ ] **Step 2: Exécuter le RED public et l'archiver.**

Run: `rtk proxy ./gradlew :kanvas:test --offline --no-build-cache --tests org.graphiks.kanvas.surface.W7RootAaSurfacePixelTest -I /private/tmp/kanvas-w7-image-opacity.YmtXjI/isolated.init.gradle -Pw7.validationDir=/private/tmp/kanvas-w7-root-aa.8WK1ZR/red --console=plain`.
Les nouveaux positifs doivent refuser `w6a.layer.unsupported_child` avant patch.
Un défaut de compilation/oracle n'est pas un RED. Corriger le test d'abord.
Préserver les diagnostics des contrôles négatifs, ne pas les deviner.

- [ ] **Step 3: Étendre seulement les trois sites autorisés.**

Sélection : condition actuelle des layers inchangée ; nouvelle alternative
scope nul, `!ownsW6b`, couverture AA explicite et contrat source existant.
Construction : conserver le garde anti-directFilter/coverage ; contexte layer
inchangé, root sans mapping, bounds=`RectI32(0,0,width,height)`, destination root.
Remapper les mêmes passes/loads ; émettre RenderChildren seulement si scope non
nul. Composite adjacent et version destination exacts dans les deux branches.
Binding final : pour topology AA et scope nul, origine explicite Point2I32.Origin,
pas `targetOriginDevice` sur la texture multisample. Aucun nouveau type contexte
n'est obligatoire si quelques variables locales rendent ces deux branches claires.

Tout besoin de changer un seal/native recipe/admission numérique exige un
diagnostic causal envoyé au contrôleur avant modification, pas un assouplissement.

- [ ] **Step 4: GREEN ciblé et contrôles proches, séquentiels.**

Même commande, archive unique `green`. Puis sélection conjointe de
`W7AaPathLayerSurfacePixelTest`, `W6aLayerSurfacePixelTest`,
`W6aNestedLayerSurfacePixelTest`, `W6aInitWithPreviousSurfacePixelTest`,
`W6aLayerBudgetRecoverySurfacePixelTest`, archive `related`.
Ne pas relancer W5f large, fonts/codecs ou tests infrastructure.

- [ ] **Step 5: Une tentative globale bornée, puis validation finale.**

Même init, `:kanvas:test` sans filtre et archive `full-suite-240` ; timeout240s.
Conserver toutes les identités d'échec/interruption/warnings et l'exit réel.
Comparer à `/private/tmp/kanvas-w7-layer-source.mCvMdN/stage-b-full-kanvas`.
Pas de répétition globale. Final nouveau shard + ancien W7AA, archive `final`.

- [ ] **Step 6: Self-review, commit et handoff réels.**

`git diff --check`, commit seulement les deux fichiers production et le nouveau
test. Aucun push par worker. Rapport scratch avec commandes/exits/XML, tous les
échecs individuels, limites et absence de worker ; rendre ownership runtime au root.

### Clôture contrôleur

- [ ] Relecture de tâche Sol, correction puis re-review ciblée si nécessaire.
- [ ] Corpus631 sur le commit exact, slices0–607/607–608/608–631, timeout30.
- [ ] Comparaison individuelle à `rect-adapter-d45904e0b.json`, gains/pertes,
  empreintes et diagnostics ; aucun gain anticipé, aucun seuil modifié.
- [ ] Mise à jour du suivi existant, review globale Sol, PR draft sur #2417.

## Self-review du plan

Trois sites producteurs/consommateurs couverts dans une seule tâche dépendante.
Les cinq risques ont un témoin ; diagnostic Terra corrigé par Astra sur
HARD_EDGE, layer vide et origine native. Aucun nouveau type/API ni contrat
filtré/PLUS. L'avis Astra porte aussi sur ces fichiers avant handoff.
