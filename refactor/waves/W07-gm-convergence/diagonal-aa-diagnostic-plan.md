# Diagonal AA Diagnostic Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Steps use checkbox syntax for tracking.

**Goal:** Isoler géométrie et couverture du gap AA diagonal réel avant toute correction produit.

**Architecture:** Préparation math comparée à quatre sommets indépendants ; Surface native compare source tiny CTM, coordonnées écran et contour littéral. La lecture alpha sur transparent sépare couverture et domaine couleur.

**Tech Stack:** Kotlin, math matrix/geometry, Surface, WebGPU natif, JUnit, Gradle.

**Spec:** refactor/waves/W07-gm-convergence/diagonal-aa-diagnostic-design.md

## Global Constraints

- Fonts, codecs externes et jpg-color-cube hors périmètre.
- Aucune modification renderer/plan/math produit, référence, seuil, score, registre631/scope443, GM, budget, sample count ou précision oracle.
- Pas de mock, skip GPU, test source-text/forwarding/infrastructure ou fallback CPU.
- Objets et calculs géométriques produit dans math, nomenclature I/F32/64.
- Un seul runtime contrôleur, watchdog240s inchangé, audit séparé avant suivant.
- Préserver le diagnostic inverse-filter untracked SHA96cd8349, les raw receipts et les workspaces existants. Aucun cleanup ni merge.
- Suites globales héritées RED/incomplètes : pas de GREEN global déduit.

## Review Focus

- Contour erroné par expansion après CTM : sommets littéraux math et rampes Surface source/écran.
- Contour explicite différent d'un stroke : rampe du FILL littéral avec les mêmes sommets indépendants.
- Alpha confondu avec RGB/domaine : transparent noir dans deux domaines, opaque blanc en encoded/LINEAR.
- Contenu absent mais score de fond haut : pixels noirs intérieurs et transparents/blancs extérieurs obligatoires.
- Attendus qui figent un sampling transitoire : ne pas affirmer des niveaux MSAA comme futur contrat d'aire ; archiver les observations.

---

### Task 1: Témoins géométrie et couverture native

**Files:**
- Create: `math/matrix/src/commonTest/kotlin/org/graphiks/math/matrix/DiagonalStrokeGeometryF64F32Test.kt`
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W7DiagonalStrokeSurfacePixelTest.kt`

**Interfaces:**
- Consumes: `Matrix3x3F32.preparePathStrokeGeometryF32(path, styleF64, PathStrokeDrawMode.Stroke)` ; `PathStrokePreparationResult.Ready.geometryF32.copyFillGeometryF32().copyStencilEdgeFanF32OrNull()` ; `Surface.canvas`, `Surface.render`, `RenderConfig.compositionDomain`.
- Produces: named math/JUnit witnesses and stdout lines prefixed `W7_DIAGONAL_AA_EVIDENCE` with domain, scene, RGBA rows, native dispatch/refusal and pass-scope counts. No product API.

- [ ] **Step 1: Write independent geometry witnesses.** Test `tinyReciprocalDiagonalPreservesButtOutline`: device line(70,20)->(150,100), width5; source line(20s,20s)->(100s,100s), width5s, CTM sx=sy=1/s, tx=50, ty=0. Use scales .00005f/.000045f/.0000035f/.000003f/.000002f. Verify each real edge endpoint, excluding fan anchor, matches one of four literal corners within3e-5, and all four corners occur. Check device path as control. Style BUTT/MITER, miterLimit4. No assertion of preparation ordering/private fields or bit-exact equality.

- [ ] **Step 2: Write native behavioral witnesses.** Test `tinyReciprocalAndLiteralOutlinePreserveDiagonalRamp`: dimensions192×128, black AA stroke, width5; compare source at s=.00005f, device line and FILL polygon using four corners above, at y60 x104..116, in LINEAR and SRGB_ENCODED. Before rendering pin literal black interior x110 and clear exterior x104/116. Assert native dispatch>0/refusal0, Render/Readback scopes, all repeats byte-equal. Ramp equality is an equivalence witness, not the sole expected behavior.

- [ ] **Step 3: Separate composition from coverage.** Test `transparentAlphaSeparatesDiagonalCoverageFromComposition`: same device stroke on transparent and on real hard white Rect in both domains. Require independent interior/exterior values, alpha ramps equal across transparent domains, encoded white RGB+transparent alpha in254..256 for each sampled cell and opaque alpha255. At partial-coverage cells require LINEAR white gray>encoded gray. Print all raw samples, do not assert current quantized levels or fit the Skia reference. Include independent analytic two-cell areas as literals in diagnostics/KDoc, not a full image CPU oracle.

- [ ] **Step 4: Self-review and report READY_VALIDATION.** Controller alone runs runtimes. Do not run tests/build/GPU, commit, stage, branch or spawn agents. This is diagnostic characterization of existing behavior, not a product fix: no fabricated RED/GREEN cycle. Explain which realistic geometry/coverage/composition faults assertions catch, and which information only the raw observation establishes.

- [ ] **Step 5: Controller qualification and task review.** Controller runs `:math:matrix:jvmTest --tests '*DiagonalStrokeGeometryF64F32Test*' --rerun` and `:kanvas:test --tests '*W7DiagonalStrokeSurfacePixelTest*' --rerun` separately under preserved240s wrapper. Full logs/exit/XML/stdout audited after each before source edits/next runtime. Record all warnings/failures and no global GREEN; parent full suites already qualified RED on identical unchanged product. After failed fixture, resume same implementer with exact error, never fit expected pixels. Sol independently reviews both spec and quality.

- [ ] **Step 6: Causal conclusion and strategic review.** Controller writes evidence and uncertain claims into `refactor/waves/W07-gm-convergence/diagonal-aa-diagnostic.md`, updates pilotage and commits exact explicit file list excluding protected untracked. One Astra whole-branch/strategy review, no GPU reruns by reviewers. Choose and write next product design/plan after applying load-bearing feedback. Do not call W7 complete or claim corpus gain; no corpus rerun needed for tests/docs-only delta.
