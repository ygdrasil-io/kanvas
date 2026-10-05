# W7 Sk3d Faithful Port Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Execute the original Skia sk3d_simple scene and measure its true residual parity gap.
**Architecture:** Preserve the faithful GM port and genuine Picture Rect+CTM. A distinct prerequisite extends hard Picture ownership and its closed W4d Rect source, reusing W6 occurrence assembly; no new compositor/backend.
**Tech Stack:** Kotlin/JVM, public Surface/Picture, native WebGPU, frozen Skia corpus.
**Spec:** `refactor/waves/W07-gm-convergence/sk3d-port-design.md`

## Global Constraints

- Références, seuils, domaine LINEAR, exclusions et timeout 30 s/GM restent inchangés.
- Fonts, codecs/décodage externe et jpg-color-cube restent exclus.
- Aucune relaxation de proof, epsilon, cap ou oracle existant. Pas de fallback CPU, de routage par nom de GM ni de merge.
- Pas de tests d'infrastructure, mocks, forwarding ou inspection de source ; tests publics natifs seulement.
- Géométrie dans math avec nomenclature I/F32/64 ; réutiliser les matrices existantes.
- Workdir `/Users/chaos/.codex/worktrees/w7-gm-diagnostic/kanvas`, branche `codex/w7-sk3d-port`, base `b31185372f65c1597edc5fc7d2f783f528f88e09`. RTK pour tout shell, apply_patch absolu, aucun helper chez les workers.
- Terra implémente, Sol relit ; Astra réservé à un blocage architectural réel. Draft empilée sur #2426, publication par contrôleur.

## Review Focus

1. Ordre CTM×translation, et non translation×CTM : témoins de silhouette.
2. Degrés/radians et rotation Y vs Z : coordonnées device indépendantes.
3. Alpha 136/255 vs 0,5 et Picture omise : couleur intérieure indépendante.
4. AA implicite et bord à x=40,504985 : pixels voisins hard, sans oracle ajusté.
5. Picture réelle et état restauré : audit du port, pixels extérieurs et repeat natif ; aucune substitution de chemin pour contourner une capacité manquante. Task2 contrôle aussi origine cible, stencil, refus AA-perspective et budgets analytiques.

## État qualifié — 1er octobre 2026

Task1 et Task2 terminées sur leur périmètre amendé, reviews Sol approuvées.
Revue globale Astra suivie d'une unique correction test/KDoc et d'une
contre-relecture Sol approuvée : trou even-odd maintenant observable,
allocations temporaires documentées. Draft [#2427](https://github.com/ygdrasil-io/kanvas/pull/2427)
empilée sur #2426 ; aucun merge ni clôture W7.

Produit mesuré813e61f09 : ciblés263/263, corpus631/443 complet. Correction
finale5a931c87a : public94+GM9 frais,103/103, code exécutable inchangé. La
globale240s reste incomplète (688SUCCESS/40FAILURE/1SKIPPED,22anciennes
identités non atteintes) ; sa réussite n'est pas un livrable atteint.
Les détails et limites sont dans [status](status.md) et [pilotage](https://github.com/ygdrasil-io/kanvas/blob/36350563f48485598009d61a1707f7cff0ff7e94/refactor/waves/W07-gm-convergence/pilotage.md).

Les checkboxes ci-dessous indiquent les obligations traitées par la solution
finale amendée, pas la conservation des implémentations intermédiaires.
Fix3 a remplacé l'admission tardive fix2 ; fix4 a rendu le préfixe F64 lazy
après provenance par entrée. Le contrôleur a repris toute qualification
native, globale et corpus après l'essai chevauché invalide. L'historique
ci-dessous est conservé pour expliquer ces amendements.

## Historique de l'ordre d'exécution amendé après preuve native

Task1 a produit le RED causal et le port fidèle WIP, puis a rencontré un
refus renderer. Elle reste suspendue, non terminée. Exécuter Task2, le
prérequis distinct ci-dessous, avec un nouvel implementer et sa review Sol ;
puis reprendre Task1 avec son implementer original pour qualification et
corpus. Ne pas rejouer son RED ni remplacer sa scène. Les deux WIP Task1
restent hors des commits renderer Task2. Une seule runtime native active.

### Task 1: Correct the port and qualify its pixels

**Files:**
- Modify `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/composite/Sk3dSimpleGm.kt`.
- Create `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/Sk3dSimpleSurfacePixelTest.kt`.
- Update `refactor/waves/W07-gm-convergence/status.md`, `pilotage.md`, `refactor/README.md`; add measured `sk3d-port-<sha>.json` alongside the prior lot's snapshot.

**Interfaces:** Existing SkiaGm.draw/GmCanvas, PictureRecorder and math matrices; consume the reviewed Task2 hard Picture source. Task1 itself changes no renderer/API contract. Read the spec for exact Skia parameters and order; update the port's source link to the pinned revision. Direct GmCanvas and recorder Canvas retain their existing distinct adapters.

- [x] Write three public native tests named `perspectiveFootprintMatchesIndependentCamera`, `pictureOverlayUsesSkiaAlpha`, `hardEdgeHasNoPartialCoverage`. Record the actual GM onto a white public Surface, using its existing composition config; no snapshot-op/source-text assertions. The expected coordinates/colors are set before creating Surface.
  - Footprint: inside `(60,100)` and `(150,100)` are `(182±1,0,193±1,255)`; outside `(20,150)`, `(220,150)`, `(0,0)`, `(299,299)` are exact white. Use the spec's independent camera derivation, not a production matrix helper for expectations.
  - Alpha: interior `(150,100)` has the same fixed color band; show before GPU that these bands exclude half-alpha `(188,0,188)` and omitted Picture `(255,0,0)`.
  - Hard edge: `(40,100)` exact white, `(41,100)` interior color. The left edge x=40.5049845 is just beyond the first pixel centre; document the independent margin. Do not change expected bands after observing GPU.
  - Every test requires clean Render/Readback, dispatch >0, no refusal/diagnostic/skip and second clean byte-identical render. Follow existing native Surface cleanup conventions.
- [x] Run only this class to causal RED before editing the port. Archive test identities, exact failure and wrapper/child exit; setup/compiler failure is not RED.
- [x] Implement the spec's faithful camera, colors and AA flags, preserving real recorder Rect+CTM and drawing order. After the archived green-1 refusal, consume Task2 and run focused GREEN without altering the three expectations.
- [x] Re-run this class plus `AlphaGradientsSurfacePixelTest` and `HardstopGradientSurfacePixelTest`. Account all identities, XML failures/errors/skips/stderr and exits. Run one bounded240 `:kanvas:test` attempt after final edits; list reached failures by identity and preserve timeout/unreached as incomplete, not green. No per-edit global reruns.
- [x] Commit only port/tests. Measure frozen631/443 at that exact full SHA with existing `measureSkiaParity` and strict `summarize-parity.mjs`, 30s/GM, partitions0–607/607–608/608–631. Use fresh run dirs; keep live handles until terminal, never restart on observation timeout. Compare18invariants and every prior outcome/hash/metric against `picture-3398dc3.json`. Only sk3d_simple has an edited scene; the generic Task2 capability may change other outcomes, which must each be listed and explained, never omitted from the denominator. Inspect sk3d actual/reference/diff even if score rises. Investigate any previously rendered loss or changed old image outside sk3d before claiming preservation. Report gains/losses and fidelity separately.
- [x] Update the existing durable docs and add the current snapshot without falsifying baseline history. Mark review pending. Self-review, commit docs, and write full report with literal commands, RED/GREEN evidence, all commits, corpus comparison and remaining limitations. Controller owns independent Sol task review and publication.

### Task 2: Supply the closed hard Picture renderer prerequisite

**Files:**
- Modify `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPlanSurfaceCandidateGate.kt`.
- Modify `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/{W6aLayerPlanCompiler,CapabilityCompilerChain,W4dGeneralPathPlanCompiler,W6aLayerGraphConstruction}.kt`.
- Create `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W7HardPictureSurfacePixelTest.kt`.
- Preserve the two uncommitted Task1 GM/test files; do not edit or stage them. Controller owns plan/design/status and publication during this prerequisite.

**Interfaces:** Add internal `W4dGeneralPathPlanCompiler.w6HardRectFillSource(catalog: RuntimeEffectSemanticCatalogSnapshot): W4dGeneralPathPlanCompiler` and a distinct closed projection mode `PictureHardFill`. Nominate the same hard Picture family in DisplayOp and captured Scene ownership; prefer W6 only for this Picture family, with bounded identity traversal. Source selection consumes `OccurrenceSourceInputV1.materialCoordinateDraw()` in `preparePictureDrawLane`; outputs the existing ordinary hard `SourceDeferredRenderConstructionV4`. No changes to prepared flat compositor, math API, AA source contract or direct W6 source selector. Read the spec's prerequisite section for binding admission/provenance/mapping rules.

- [x] Write the public native class before renderer edits, using the fixed H/A geometry and color witnesses in the spec. Tests: `projectiveRectHasHardCoverage`, `projectiveTriangleKeepsItsShape`, `projectiveEvenOddHoleDoesNotLeakStencil`, `affineRectPictureUsesItsRecordedTransform`, `twoPictureOccurrencesSeeTheirOwnDestination`, `nestedPictureComposesOuterTranslationOnce`, `pictureLayerRebasesHardScissorAndSiblings`, `identityAndScaleTranslatePicturesKeepAnalyticBudget`, `hardPictureRefusalsPreserveSentinelAndRecover`, `hardSiblingDoesNotAdmitAaPerspective`, `directFrameWithoutPictureStaysClean`. Use parameterization for the five refusal inputs (horizon, NaN, RGBA16_FLOAT, budget1, mixed AA) if clearer, without duplicating mixed-AA coverage. Derive identity/scale-translate Picture budget and layer/scissor witness margins statically before GPU and record the calculation in test/report. Budget1 is an immutable insufficiency check, not an empirical measured threshold. Positive tests demand native Render/Readback, clean stats, dispatch and repeat; negatives demand precise predicted diagnostic family, unchanged readPixels sentinel and clean recovery with the admitted H control.
- [x] Run only this new class to causal RED, archive every identity/outcome and exit. The existing Sk3d green-1 is additional causal evidence, not permission to skip the new public RED. Keep oracles unchanged after observation. If a fixture cannot be justified from existing contracts, ask with its proposed independent derivation before GPU.
- [x] Implement owner nomination, captured priority, closed hard Rect factory/preflight/copy propagation and Picture occurrence source selection together. Preserve identity/scale analytic selection and Path source semantics. Reuse existing math/assembly/physical resource checks. If native execution uncovers an additional boundary, report the exact cause before widening assembly scope; do not add a replacement scene or weaken a proof.
- [x] Run focused GREEN for this class and the unchanged three Sk3d tests. Run covering classes `W7AaDeferredPictureSurfacePixelTest`, `W7AaDeferredBlendSurfacePixelTest`, `W7AffineRectSurfacePixelTest`, `W7CoveredPlusSurfacePixelTest` and the existing encoded Rect hairline public test class (resolve its exact filename before running). Account all END identities, XML failures/errors/skips/stderr and wrapper/child exits, fresh archives. No global/corpus run in Task2: the one final global attempt and corpus belong to resumed Task1 after this review.
- [x] Self-review, `git diff --check`, commit only the five renderer files and the new public test. Write `.superpowers/sdd/sk3d-port-plan/task-2-report.md` with commands, complete RED/GREEN/covering accounting, static budget derivation, commits and remaining limitations; return concise DONE/NEEDS_CONTEXT. No helpers, no push/PR/merge. Controller creates independent Sol task review before resuming Task1.

## Validation execution

### Task2 fix round2 — typed Picture clip regression

Le global `task1-resume-global-1` au SHA `ff3e3bb4a` est le RED causal :
abort natif134 dans le PictureTest existant de clip perspective. Ne pas
reprovoquer cet abort ni changer l'oracle historique. Réouvrir Task2 avec
l'implementer original pour l'admission fermée décrite dans le design,
avant de reprendre la qualification Task1.

- [x] Modifier uniquement `W6aLayerGraphConstruction.kt`, le partage étroit
  des prédicats hard dans `W6aLayerPlanCompiler.kt` et, si nécessaire, une
  query des clips de source dans `OccurrenceSourceInputV1.kt`. Garder les
  seeds raw/captured structurels et les modes W4d/W4e inchangés.
- [x] Ajouter des témoins publics de refus précis, sentinelle et recovery
  dans `W7HardPictureSurfacePixelTest`, incluant Rect/Path, sibling valide,
  Picture imbriquée/layer, et annulation de perspective enregistrée par
  transform externe. Pas de tests d'infrastructure ni de nouvel oracle
  fitted. Le PictureTest existant et les trois témoins Sk3d restent intacts.
- [x] Le contrôleur exécute désormais seul les validations natives, conserve
  le handle vivant jusqu'au terminal et archive chaque commande. Le worker
  écrit tests/code/rapport, sans Gradle/GPU. Qualification : identité Picture
  régressive puis PictureTest complet + hard Picture ; Sk3d/Alpha/Hardstop ;
  covering160 existant ; un global240 final avant corpus. Sol relit le diff
  fix2 avec le rapport et les preuves du contrôleur.

Les deux slices `task1-resume-corpus-ff3e3bb` se chevauchent réellement ;
leurs journaux sont préservés, mais aucune qualification/agrégation ne les
utilise. La future mesure est neuve, séquentielle, au SHA produit corrigé.

### Task2 fix round3 — early typed clip priority

Le global fix2 confirme deux SurfaceTest anciennement verts : priorité des
clips singular/overflow et provenance perspective schema1. La review Sol
les garde ouverts. Appliquer la correction de design ci-dessus avec le même
implementer, en remplaçant (pas en doublant) l'admission tardive fix2.

- [x] Extraire l'énumération des clips consommés sans nécessiter un carrier.
  Parcourir une fois les drafts actifs avant leur premier cull. Préserver
  ordre, scopes source/composition, empty/no-op et diagnostics typés.
- [x] Ajouter seulement l'extraction numérique math/matrix et l'adaptateur
  render-ir nécessaires ; réutiliser les primitives existantes et partager
  le classifier utilisé par GPUClipMapper si extrait. Aucun backend/scissor,
  proof, cap, seed, mode W4d, oracle historique ou nouveau test d'infrastructure.
- [x] Companions publics : singular/overflow et legacy sentinelle+recovery,
  grande matrice dont la projection est encore F32 finie, ordre des refus,
  préservation des sous-arbres au clip terminal vide existant. Ne jamais
  augmenter les tolérances après observation. Conserver les tests fix2.
- [x] Contrôleur seul : SurfaceTest et PictureTest inchangés avec hardPicture,
  puis GM9 et covering160 ; relecture Sol fix3 ; ensuite un global240 final.
  Réutiliser le globalfix2 comme RED causal des deux identités, sans répétition
  avant modification. Aucun corpus tant que ces régressions restent ouvertes.

Archive root `/private/tmp/kanvas-w7-sk3d.WUms9p` (fresh per invocation).
Use `/private/tmp/kanvas-w7-sk3d.WUms9p/bounded-run.rb` (same runner with its
archive guard scoped to this lot and maximum240s) and read-only
`/private/tmp/kanvas-w7-aa-blend.rFtTrn/review-evidence.init.gradle`, reading
both before use. Gradle flags:
`--offline --no-daemon --no-build-cache --no-parallel --console=plain`.
Public test task `:integration-tests:skia:test --tests org.graphiks.kanvas.skia.Sk3dSimpleSurfacePixelTest`.
Global baseline `/private/tmp/kanvas-w7-aa-blend.rFtTrn/layer-preservation-full-final-1`
has751END,710SUCCESS/40FAILURE/1SKIPPED,timeout124/143; inherited red is not green.
The user delegates W7 design/execution choices; no new approval loop.
Plan self-review: Task2 supplies the newly proven renderer prerequisite,
Task1 consumes it without changing its oracles. They share runtime but not
edit/commit ownership. Captured nomination and source projection ship together;
all five review focuses have public witnesses or the explicit Picture audit.
