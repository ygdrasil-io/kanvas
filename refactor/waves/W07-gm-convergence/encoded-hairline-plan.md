# W7 Encoded Rect Hairline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render the actual non-AA Rect hairline needed by alphagradients, with common math coverage and authenticated LINEAR/SRGB_ENCODED composition.

**Architecture:** One original draw occurrence owns one prepared W4d general geometry. Math computes disjoint hard Rect hairline coverage; existing W4d/W6 source-deferred/native paths carry the target composition domain. No four-command W3 expansion and no GM rewrite.

**Tech Stack:** Kotlin, math geometry/matrix modules, gpu-plan, wgpu4k native GPU, JUnit public Surface/Picture pixel witnesses.

**Spec:** [encoded-hairline-design.md](encoded-hairline-design.md)

## Global Constraints

- Base a170ea7be389240cc0b6efa581ec28ce33f1e910, branch codex/w7-encoded-hairline; workdir /Users/chaos/.codex/worktrees/w7-gm-diagnostic/kanvas. Never edit app cwd cbf6. Every command uses rtk and explicit workdir, every edit uses absolute apply_patch paths.
- Geometry lives in math with I/F32/64 nomenclature; no duplicate GPU geometry algorithm.
- Original Rect/STROKE/width0 provenance, source ownership, command order, source/proof seals, exact physical capability checks and all existing caps remain binding. No epsilon or primitive-envelope expansion.
- New scope: nonempty finite Rect, non-AA, width0, direct solid, SrcOver, BUTT, MITER/miter>=2; projected device bounds integral. LINEAR can use existing nonzero axis-aligned transforms; SRGB_ENCODED remains identity/integer translation.
- Coverage is the one-pixel inner border of [left,top,right+1,bottom+1), computed before clipping; corners once, no new edge at clip boundary.
- Encoded direct/root mixtures and one plain layer, existing solid/gradient/image/DrawColor siblings, Picture and snapshot remain required. Do not silently broaden AA, filters, finite strokes, paths or encoded transforms.
- Public Surface/Picture/native pixel/refusal/recovery tests and math calculation tests only. No infrastructure/source-text/mock tests.
- Expected geometry/pixels and discriminants before GPU. Existing CompositionEnvelope allowed as-is, explicitly weaker integration precision; no alteration of its formulas or historical gates.
- Preserve all43 fresh baseline identities; final selection also retains all50 previous composition-lot identities. Run one Gradle/GPU at a time. A crash/timeout is not GREEN.
- Fonts, codecs/external decoding, jpg-color-cube, GM/adapters, refs, thresholds, exclusions and historical scores unchanged. Draft stacked on2422, no merge or W7 closure.

## Review Focus

1. Integer-edge coverage differs from a centered width1 path: Task1 full pixel tables plus prior half-integer/AA/finite-stroke controls.
2. Corners and thin rects must not overblend: Task1 opaque masks and alpha-half corner-vs-side assertions, Task2 layer opacity.
3. Clipping must not create edges; overflow must not wrap: Task1 negative/large bounds math cases and public partial/full clipping, Task2 clear-only recovery.
4. Pending source construction and layer localization must carry domain and original command identity: Task2 gradients/DrawColor/image mixtures, both orders, translated bounded layer, alternating domains, Picture capture/replay.
5. A format change must not admit encoded AA/MSAA or stale clear-only metadata: Task2 refusal matrix and exact producer/consumer audit, plus existing empty snapshot and target-format tests.

---

### Task 1: Math-owned hard Rect hairline coverage with public LINEAR proof

**Status:** complete at `aa29de2ca`, after Sol task review and one scoped
test-only correction. Behavioral RED at pixel(1,1) preceded implementation;
native48/48 preserves all43 baseline identities, math478/478, process/wrapper0.
The additional height-one witness passes the focused math2/2 run. No Critical
or Important finding remains for this task. Inherited Java/Gradle warnings
remain recorded, and the complete earlier50 selection still belongs to Task2.

**Files:**
- Create: math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/RectHairlineCoverageI32.kt
- Create: math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/RectHairlineCoverageI32Test.kt
- Modify: gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4dGeneralPathPlanCompiler.kt
- Create: kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W7RectHairlineSurfacePixelTest.kt
- Existing controls: W7StrokeRoutingSurfacePixelTest.kt, W7SurfaceCompositionPixelTest.kt, W7GradientAlphaSurfacePixelTest.kt.

**Interfaces:**
- Consumes: original DrawScope Rect projection, Matrix3x3F64, existing math PathBuilder/PathFill preparation and immutable stroke work snapshots; preserve original DrawNode source.
- Produces: `public fun rectHairlineCoverageBandsI32(deviceRectI32: RectI32, clipI32: RectI32): List<RectI32>` in math. Requires ordered nonempty device/clip rects; returns new disjoint clipped bands, empty list if invisible. I64 arithmetic before clipping/conversion. No domain argument.
- Produces: one W4d prepared geometry occurrence for the exact hard/integer Rect hairline scope. Other draw classes use their existing preparation; material normalization keeps the original Rect/STROKE node and CTM.

- [x] **Step 1: Write behavioral RED tests.** Math `integerRingIsDisjointAndClippedWithoutInventingEdges`: rect(2,2,5,5), clip(0,0,8,8), exact covered lattice points: x2..5 at y2/5, x2/5 at y3/4, each once. `thinRingAndI32EdgesDoNotOverlapOrOverflow`: width1/height1, negative bounds and right=Int.MAX_VALUE clipped to a small visible region, exact expected small bands or empty, no wrap. Public `integerRectHairlineCoversEveryExpectedPixel` uses literal eight-row B/R table from spec with blue(255,17,61,211), red(255,239,51,73), before Surface creation. `translucentCornersAreCompositedOnce`, `clippingDoesNotMoveHairlineEdges`, `integerScaledHairlineStaysOneDevicePixel`, `thinAndFullyClippedHairlinesPreserveDestination` pin the named failures. Two native renders per accepted scenario. Product-missing-symbol compilation is not behavioral RED; first demonstrate current public integer-ring mismatch before adding the helper, then implement math tests.
- [x] **Step 2: Run and archive public RED.** Use bounded wrapper, select the first new public method. Expected is a wrong pixel/unsupported-scope failure in native public flow, not setup failure. If existing pixels already match, retain the characterization and reproduce a distinct required missing case; do not mislabel a passing fixture RED.
- [x] **Step 3: Implement the math helper and W4d integration.** Only the specified exact scope changes. Validate source/transform/clip first; compute complete unclipped ring, clip disjoint bands, build a single path geometry through current math budgeting. Original material/style authority and command index remain intact. No pseudo scene commands or new renderer. Preserve clip-empty handling as validated NoOp. If the adjacent half-integer/path/AA route changes, diagnose instead of weakening its tests.
- [x] **Step 4: Run math and public GREEN.** Math command `rtk proxy ./gradlew :math:geometry:jvmTest --offline --no-daemon --no-build-cache --console=plain` (confirm task name from module configuration before launch). Public run new W7RectHairlineSurfacePixelTest plus all43 baseline tests using wrapper below. Archive complete XML/events/exits, report warnings and any inherited failures by name; no success inferred from a still-running or crashed process.
- [x] **Step 5: Self-review, report and commit.** Exact commands, RED cause, final named identities, geometry/domain separation, budget/accounting and capture/provenance explanation. Commit only task files. Task review gates Task2.

### Task 2: Target-domain propagation through the single-occurrence path and Surface integration

**Status:** task review complete at `ebfd29bb1`, after one scoped correction
of deferred clear-only format and explicit AA closure. Native68/68 before
this correction, then56/56 including all43 initial controls, process/wrapper0.
Final whole-branch Astra review found the separate nonempty W5b successor
format gap and missing same-layer mixtures; unique grouped fix `f80d94fb4`
passes69/69, with all43/all50 controls. Scoped Sol review confirms both
Important addressed, no new Critical/Important. Residual Minor: an empty row
would skip the test helper's width assertion; all current grids are complete.
This status does not claim W7 completion or merge readiness.

**Files:**
- Modify: gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/CompositionAdmissionV1.kt
- Modify: gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4dGeneralPathPlanCompiler.kt
- Modify: gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W6aLayerPlanCompiler.kt
- As required by concrete joins: W6aLayerGraphConstruction.kt, W4dRenderGraphCanonicalSeal.kt and existing W4d source construction/sealing files in that package; never relax validators to admit unauthenticated state.
- Modify: gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W4dGeneralPathGraphLowerer.kt
- Modify where authenticated target facts require it: gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt and W4d native materialization authority.
- Modify: kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W7RectHairlineSurfacePixelTest.kt
- Existing controls: W7CompositionCpuOracle.kt (consume unchanged), W7SurfaceCompositionPixelTest.kt, W7GradientAlphaSurfacePixelTest.kt and prior final50 selection.

**Interfaces:**
- Consumes: Task1's single prepared hard Rect hairline geometry and untouched original DrawNode authority.
- Produces: same public Surface draw in both domains with consistent source/proof/program/logical/native/readback format. No new public API or Picture version; reuse RenderConfig.compositionDomain.
- W6 root ownership is whole-scene: after complete encoded admission, a root mixture containing this hairline may be segmented alongside already admitted sources, without requiring a synthetic layer. Existing plain-layer child routing remains ordered and localized.

**Capture clarification:** `RectF32` is mutable and must be mutated in place
after Picture recording, before serialization and replay. `Paint` and its
solid `ColorARGB` are immutable values: verify their captured style/width/color
through public pixels, without claiming that reassigning a local variable
tests object mutation. Replay the original hairline Picture in both domains;
Picture containing a snapshot image is a separate representation witness.

- [x] **Step 1: Add encoded behavioral RED.** `encodedHairlineUsesTheSameGeometryAndDifferentBlendDomain` uses the Task1 ring and alpha128 red on opaque blue; wrong-domain and double-corner expectations must be disjoint before GPU. `encodedHairlineMixesWithAdmittedSourcesInBothOrders` covers solid, two gradient alpha modes, image and DrawColor, both orders, at least one visible unshared pixel per draw. Run minimal new method first; expect `unsupported.surface.composition.geometry` before feature support.
- [x] **Step 2: Propagate the authenticated target domain.** Change W4d normalization, deferred captures, formats/resources, capability checks, construction and native readback coherently using selected.target/validated graph, not ambient config during publication. Add domain to new encoded identities only; preserve unaffected LINEAR identity bytes and the complete physical snapshot. Keep AA formats/policies LINEAR and closed. Add the exact hairline geometry/source exception to CompositionAdmissionV1, with geometry-before-blend/source precedence. Reuse W6 source and graph paths; no duplicate four-draw fan-out.
- [x] **Step 3: Complete public integration.** `plainLayerHairlinePreservesLocalizationAndRestoreOpacity` uses nonzero bounds, translation, hard clip and restore255/128; independently compute source/layer stores before rendering, cover RGBA/BGRA and repeat. `hairlinePictureAndSnapshotsPreserveCapturedRepresentation` covers mutable Rect/paint capture, memory/archive replay, full/subset SOURCE_SPACE snapshots, domain alternation and second renders. `encodedHairlineExclusionsAreTransactional` covers AA, finite width, PATH, noninteger translation, shader stroke, bad cap/join/miter, filters, non-SrcOver, nested layer and contradictory native format; diagnostic, sentinel, discard and same-runtime recovery. Preserve already-supported combinations, don't insert arbitrary global refusals.
- [x] **Step 4: Close invisible/budget behavior.** `clippedEncodedHairlineRendersAndSnapshotsTransparent` proves exact zero for standalone fully clipped outline then recovery; repeated render and snapshot tag/layout. Fix inherited clear-only readback layout format from authenticated target, not by accepting more formats. `encodedHairlineBudgetBoundaryIsTransactional` uses a frozen static resource derivation archived before the first GPU attempt for a nontrivial ring/plain-layer scene: B succeeds with expected pixels, B−1 refuses without mutation, B recovery succeeds twice. If derivation is wrong, retain that failed prediction and produce a new independently preflighted witness; no cap increase.
- [x] **Step 5: GREEN, report and commit.** Run new full class, all43 baseline tests, and the complete prior final50 selection (deduplicate selections, compare actual names). Include static audit of LINEAR identity stability, original source occurrence cardinality, budgets, AA exclusion and readback format; pixels alone cannot prove identity/seal properties. Report exact commands/exits and all missing/renamed identities; no new global/corpus by worker. Commit task files and task-owned report.

## Commands and controller delivery

One runtime at a time; write-once evidence paths:

```sh
rtk proxy ruby /private/tmp/kanvas-w7-encoded-hairline.AwWRwQ/bounded-run.rb /private/tmp/kanvas-w7-encoded-hairline.AwWRwQ/RUN 240 ./gradlew :kanvas:test --offline --no-daemon --no-build-cache --tests org.graphiks.kanvas.surface.W7RectHairlineSurfacePixelTest -I /private/tmp/kanvas-w7-image-opacity.YmtXjI/isolated.init.gradle -Pw7.validationDir=/private/tmp/kanvas-w7-encoded-hairline.AwWRwQ/RUN --console=plain
```

Replace RUN with a unique stage; append required class/method selections.
Previous final50 exact command: `.superpowers/sdd/surface-composition-plan/final-fix-report.md` is controller-owned previous-plan evidence; controller copies the command into this plan's Task2 brief rather than granting sibling scratch access.

- [x] Fresh native baseline43/43 on a170ea7be, child/wrapper0, three classes, zero failures/skips/duplicates/runners.
- [x] Task1 and Task2 independently reviewed for spec and quality; final fix and scoped review close both Important findings. Residual empty-row helper Minor is explicitly deferred; current grids are complete.
- [x] One global :kanvas:test bounded240s on ebfd29bb1:678PASS/40inheritedFAIL/1interrupted,719common identities,25previous unreached,no new observed failures; wrapper124/child143. Red/incomplete; no global rerun after final correction.
- [x] Corpus631/443 on f80d94fb4, timeout30s retained:198/443 rendered,176compared,all198oldRGBA identical to surface-composition-1d629b0be.json; no metadata/invariant/result/diagnostic changes, no lost/new render. No changed image to attribute or inspect and no GM gain claimed.
- [x] One final whole-branch Astra review, unique grouped fix f80d94fb4 and scoped Sol re-review; I1/I2 addressed, residual M1 empty-row helper and inherited M2 warnings recorded without a merge-readiness claim.
- [ ] Durable pilotage/status/corpus updated; stacked draft on2422 attached, no merge. Continue objective W7 with faithful alphagradients port as a separately measured scene change.
