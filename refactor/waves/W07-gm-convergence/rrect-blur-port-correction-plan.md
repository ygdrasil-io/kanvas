# RRectBlur port correction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Tasks use checkbox steps; controller owns runtime and Git.

**Goal:** Correct verified RRectBlur caller geometry and nearby GM operations without claiming full fidelity or changing math/AA.

**Architecture:** Keep qualified ordinary W6 source and math intact. Fix only actual GM inputs/calls; native independent pixels and full-frame comparison distinguish port repair from remaining central-panel/blur debts.

**Tech Stack:** Kotlin, existing math RRectF32/CornerRadiiF32, Surface/GmCanvas, native Apple GPU, JUnit, existing ComparisonUtils/ReferenceManager.

**Spec:** `refactor/waves/W07-gm-convergence/rrect-blur-port-correction-design.md`

## Global Constraints

- User delegates design/execution; no repeated approval loops, fresh Subagent writers, Sol only reviews.
- Parent2d84851d62ade6757628053189701f9d408927f3; Task1 W6 sourcec148 byte-identical, no math/API/shader/consumer/budget/sample/domain changes.
- Fonts/external codecs/jpg-color-cube excluded; no CPU renderer/diff/fallback, mock/skip or infrastructure/source-text/forwarding tests.
- Geometry remains math I/F32/64, no replacement geometry type; dimensions300×400 and730×1350 LINEAR unchanged.
- Fixed reference PNG hashes: rrect_blurs: 3327fa6254d219f5a23c5bdcdab30f0f363da1834353ff246f7e0b5ce66beaaa; blurcircles2: 57680c49964fa6989acebf8526498cf799f9eaf07ad3d5c3cd8dfd7f87146964.
- Registry631/eligible443, references/scores/seuils fixed; no black fake middle panel or masked comparison.
- Runtime/Git controller only, one runtime max240s, complete separate audit before next runtime/sourceedit. Protected96cd/raw/workspaces preserved.
- Target8019 not verified, source8d5 checked by Astra; exact upstream/PNG equivalence not claimed. Original Task2 fidelity remains falsified.

## Review Focus

- Actual GM, not copied constructor in test: full-GM corner witnesses must go RED before correction.
- One-corner overload remains valid: independently visible native API control, no math semantic change.
- Asymmetric blue corners and blur support: literal outside cells prove BL correction, not center-only presence.
- Real drawRRect under W6 may expose new refusal: stop before expanding planner/authority.
- Central panel/fonts and large-sigma blur remain unresolved: whole-frame metrics include all pixels, source qualification isn't fidelity.

---

### Task 1: Actual GM geometry and separators

**Files:**
- Modify `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/blur/RRectBlurGm.kt` only shapes/calls/linePaint.
- Create `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/W7RRectBlurPortSurfacePixelTest.kt`.
- Read only math, GmCanvas, two qualified original fixtures; no shared product edit.

**Interfaces:**
- Consumes existing RRectBlurGm.draw, GmCanvas.drawRRect/drawPath/drawLine, Surface native route and immutable math RRectF32.
- Produces unchanged GM name/dimensions/reference/domain and native tests `explicitCornerControlKeepsPerCornerApiSemantics`, `actualRrectBlurKeepsAllFourFirstRowCorners`, `actualRrectBlurIncludesSecondVerticalHairline`.

- [ ] **Step 1: Write native tests only.** Pin spec transparent API control RGBA0/white and asymmetry; actual GM eight bg68 corner samples, blue10,394bg68, full centers; separate real GM x199/200 half-separator192±1 and198/201bg68. Keep literal oracles/native>0/refused0/fullalpha unconditional, unique @AfterAll dispose. API controls require actual drawCallCount>0/pipelineCount>0; real GM additionally requires RenderReadback. No artificial sibling/filter to force control metadata. Writer report full geometry/kernel proof and fixturehash READY_RED; no product/runtime/Git.

  Literal actual-GM corner assertion (all RGB channels similarly), not an equality to another route:
  ```kotlin
  assertEquals(68, result.pixels[(75 * 300 + 75) * 4].toInt())
  assertEquals(255, result.pixels[(75 * 300 + 75) * 4 + 3].toInt())
  assertTrue(result.stats.opsDispatched > 0)
  assertEquals(0, result.stats.opsRefused)
  ```
- [ ] **Step 2: Controller authentic RED.** Bounded `:integration-tests:skia:test --tests '*W7RRectBlurPortSurfacePixelTest*'`; API control PASS, real corners and missing second separator FAIL at intended pixels, not build/setup/refusal. Audit every case/full stdout/stacks; do not fix implementation before audited RED. If expectation invalid, same writer fixture-only independent reproof before next run.
- [ ] **Step 3: Same writer minimal GM fix.** Explicit named fourcorner parameters first two rows, correct blue BR10,30/BL30,30, true left drawRRect/right drawPath, five AA white hairlines including200. All other paint/filter/bounds/padding/domain/setup remain unchanged; no new shared geometry factory/overload. READY_VALIDATION, no runtime/Git.
- [ ] **Step 4: Controller GREEN.** Same command/all oracles unchanged, all3cases PASS/native. New refusal or math-control failure is a falsifier, no unauthorized family widening. Pin source/test/protected hashes.
- [ ] **Step 5: Controller commit and independent Sol gate.** Exact file range with full packet; spec+quality C/I cleared before Task2. Minors deferred only with recorded cost. No push/PR yet.

### Task 2: Whole-frame requalification and retained-product disposition

**Files:**
- Existing `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/W7W6OrdinaryAaPathSourceIntegrationTest.kt` read-only initially; original capture indices/anchors preserved unless a new semantic scene position demonstrably needs independent adjustment.
- Create controller `refactor/waves/W07-gm-convergence/rrect-blur-port-qualification.md`.
- Modify controller `refactor/waves/W07-gm-convergence/pilotage.md` and original qualification append-only disposition.

**Interfaces:**
- Consumes Task1 fixed GM and frozen Task2 native/full reference fixtures; original DrawNode capture receipts remain historical.
- Produces measured same-scene full images/ref/diff/crops/replay and actual gains/debts, not a new masked score or Task2-fidelity completion.

- [ ] **Step 1: Controller full GM.** Run original rrect full fixture separately via ordinary-gm-evidence.init.gradle,240s; audit complete terminal/log/events/ALLXML/stdoutstderr. Inspect whole images and new correctedcorner/blue/secondline crops; retain all metrics and explicit missing central panel/labels. Original anchors must still pass; no reference or expected-position edits based on output.
- [ ] **Step 2: Controller covering control.** Run unchanged blurcircles full fixture separately, same hashes/dimensions/native/replay/metrics as qualified2d; source reduced5 already qualified exactc148, rerun only if source changes (not authorized by this plan). Full audits before next action.
- [ ] **Step 3: Write qualified disposition.** Compare whole-frame metrics/content before-after; if any substantial new refusal or geometry regression, record and re-prioritize. Decide explicitly whether a separate admission-retained corpus qualification is justified; no automatic9/corpus/publication and no original Task2 completion claim.
- [ ] **Step 4: Sol review then final strategy disposition.** Gate exact plan range/source/ledger. Use Astra only if new diagnosis/strategy needs it, not repeated default review. Subsequent corpus/draft stack requires explicit new or amended qualification criteria, controller-owned; no merge/globalGREEN/W7 close.
