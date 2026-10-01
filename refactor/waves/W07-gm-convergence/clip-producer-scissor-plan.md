# W7 Clip Producer Scissor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render out-of-attachment clip geometry safely and exactly through root and layer public routes.

**Architecture:** Carry prepared, attachment-local scissor authority independently of geometry. A closed ConstantZero producer preserves topology when ordinary coverage becomes empty, including late layer rebind. Backends only consume sealed decisions.

**Tech Stack:** Kotlin/JVM, math F64/I32, sealed GPU plans, wgpu4k/Metal, JUnit public native pixel tests.

**Spec:** `refactor/waves/W07-gm-convergence/clip-producer-scissor-design.md`.

## Global Constraints

- Font, codecs/external decoding and `jpg-color-cube` remain excluded.
- No weakening of proof, CompositionEnvelope, epsilon, cap, budget, historical oracle, reference, threshold, corpus631/443 or LINEAR domain.
- No CPU fallback, GM-name routing, fake Picture, infrastructure/mock/forwarding/source-text tests.
- Geometry and numerical queries stay in math, using I/F32/64 nomenclature.
- Controller owns all Gradle/native/corpus runs; workers never launch them or spawn helpers/reviewers. Exactly one native runtime at a time, max240 seconds per private process group, retain its terminal handle until exit.
- Product/test edits belong to the implementer, docs/evidence/publication to the controller. Follow RTK and apply_patch. Do not edit unrelated worktrees, existing pixel oracles, references or refusal boundaries.
- Use the existing isolated worktree `/Users/chaos/.codex/worktrees/w7-gm-diagnostic/kanvas`, branch `codex/w7-clip-producer-scissor`, parent `246da8583b8342c5828bb020b0e704e419082e7e` (#2427). Publish only a stacked draft, no merge.
- New public positives require native Render + Readback, positive dispatch, no refusal, literal expected pixels and byte-identical repetition. No capability skip added.

## Review Focus

- Ordinary empty and inverse offscreen coverage have opposite results: `initialOutsideCoverage` and `lateLayerOutsideCoverage` pin all four Intersect/Difference × normal/inverse outcomes.
- A producer that becomes empty only at W6 rebind must retain folds/resolve/reset: `lateLayerOutsideCoverage` and `orderedOutsideCoverage` pin this independently of initial emptiness.
- Root/layer coordinates must not be mixed or rebased twice: `translatedLayerScissor` and `affinePictureScissor` pin nonzero origin and outside-layer sentinels.
- Stencil and scissor cannot leak between siblings: `stencilAndScissorReset` pins an untouched hole, second polygon and later marker outside old scissors.
- AA and constant producers must respect actual attachment samples: `analyticRectScissor`, `analyticRRectScissor`, and `aa4BinaryPathScissor` pin analytic coverage and binary Path4x coverage, with offscreen4x constant companions.

---

### Task 1: Preserve producer scissor authority end to end

**Files:** All paths below are relative to the isolated worktree.

- Modify `math/matrix/src/commonMain/kotlin/org/graphiks/math/matrix/LayerClipMappingF32.kt`.
- Under `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/`, modify `PlanPasses.kt`, `W4eClipPlanCompiler.kt`, `PlanW4eGeometryBindingV1.kt`, `RenderGraph.kt`, `W6aLayerGraphValidation.kt`, `W4dGeneralRenderGraphCanonicalSeal.kt`, `W4eNativePayloadPlan.kt`, `W4eClipMaskProducerRecipeV1.kt`, `W4eClipMaskProducerDirectTriangleRecipeV1.kt`, `W4eClipMaskProducerStencilEdgeRecipeV1.kt`, `NativeSiteRecipeV1.kt`, `PlanPhysicalLayoutV1.kt`, `W6aLayerGraphConstruction.kt`; create `W4eClipMaskProducerConstantRecipeV1.kt`.
- Under `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/`, modify `passes/GPUPlanW4ePreparedAuthority.kt`, `recording/GPUW6aLayerFramePlan.kt`, `execution/GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt`, `execution/GPUWgpu4kW6aLayerFramePayloadMaterializer.kt`.
- Create `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W7ClipProducerScissorSurfacePixelTest.kt`.
- Only mandatory constructor-argument updates are permitted in `gpu-plan/src/test/kotlin/org/graphiks/kanvas/gpu/plan/RenderGraphContractTest.kt` (five existing calls). No new infrastructure assertions or infrastructure test execution.
- Leave `GPUPreparedNativeFramePayload.kt` global guards unchanged.

**Interfaces:**

- Consume existing `ClipPreparedEntryF32.copyConservativeScissorI32(): RectI32`, full `ClipGeometryF32`, existing layer target domain/origin and attachment rows. The prepared entry scissor, not Path bounds, is authoritative.
- Add mandatory `scissorI32: RectI32` and defensive `copyScissorI32(): RectI32` to `PlanPass.ClipMaskProducer`.
- Add `W4eClipMaskProducerRealizationV1 { Raster, ConstantZero }` to the producer contract. Planner selects ConstantZero iff ordinary coverage scissor is empty; inverse coverage remains Raster with its prepared domain.
- Add `LayerMappingF64.mapDeviceScissorToLayerI32OrNull(scissorI32: RectI32, targetDomainI32: RectI32): RectI32?` in math: intersection then one origin subtraction; valid empty returns `RectI32.Empty`, arithmetic failure returns null. Existing nonempty domain query stays unchanged.
- Add `W4eClipMaskProducerConstantRecipeV1` and native family `W4eClipMaskProducerConstant`. Follow existing freeze/canonical/catalog ownership conventions; no new open backend decision.
- Frozen/prepared authority snapshots both realization and scissor; all native signatures/catalog encodings authenticate them independently of geometry.

- [ ] **Step 1: Write the safe causal RED test only.**

`negativeTriangleScissorRenders`: Surface8×8, opaque red background, clip hard winding triangle(-4,-4),(12,-4),(-4,12), draw opaque blue full target. Assert literal RGBA blue(1,1), red(6,6), plus native evidence and exact second frame. Use public path draw/clip patterns from `GPUPlanSurfacePixelTest` and evidence pattern from `W7HardPictureSurfacePixelTest`; no product math oracle.

Freeze test source and report READY_FOR_RED to controller. Do not change product yet.

- [ ] **Step 2: Controller runs RED and archives the causal failure.**

Focused selector: `:kanvas:test --tests '*W7ClipProducerScissorSurfacePixelTest.negativeTriangleScissorRenders'` through the private bounded command below. Expected failure before GPU submission due to negative SetScissor. A compile failure is not RED; repair test compilation first. Do not run the positive overflow fixture on unfixed code.

- [ ] **Step 3: Carry and validate explicit scissor and realization.**

Both compiler constructor paths use the prepared entry copy; rebind uses the new math query. Retain full geometry/provenance and current `selectRealization`/Geometry.Empty algebra. Validate positive bounded Raster scissors against physical attachment extents; validate ConstantZero separately. Include fields in canonical digest and prepared authority. Preserve inverse full coverage domain; never infer inverse zero from interior bounds.

- [ ] **Step 4: Freeze closed ConstantZero and raster recipes.**

Realization is checked before geometry kind everywhere (payload sizing, freeze, catalog, recipe selection, verification). ConstantZero has exactly one native site, no V/I/uniform usage, sealed coverage0, full-target positive draw domain, Draw3, replace blend, no-bind-group ABI, clear/store, target/resolve/depth identity and extents, samples1/4, D24S8 reset. Existing conservative slab allocations may remain, but executable usages/accounting must match consumption. Match sample count and depth attachment compatibility in its dedicated constant pipeline; existing1x clear pipeline alone is insufficient. Raster analytic/triangle/stencil recipes copy bounded pass scissor, with edge/cover identical. Do not emit concurrent raster recipes for ConstantZero.

- [ ] **Step 5: Consume authority on both materializer routes.**

Root prepared producer uses its explicit scissor even without W6 recipe. W6 forwards/validates the new recipe map and compares pass/prepared/frozen scissor independently from Path geometry. Every analytic Raster sets scissor too. Constant never emits an empty SetScissor and preserves strict typed Draw validation. No backend clamp, no late pass deletion.

- [ ] **Step 6: Controller compiles and runs the causal GREEN.**

Freeze source and report READY_FOR_GREEN; same selector as Step2. Expected one success with clean XML and no refusal. Controller may run `:gpu-plan:compileTestKotlin` separately for mechanical callsite compatibility, never its infrastructure tests. Fix compiler/causal failures through this implementer, then proceed to positive overflow and matrix.

- [ ] **Step 7: Complete public regression witnesses.**

Use opaque blue over red and green for independent later draws unless transparent analytic fixture specified. Coordinates below are literal; assertions must not call product geometry helpers. Parameterize useful hard/root/layer and admitted AA companions without multiplying irrelevant combinations.

| Test | Fixture and literal assertions |
| --- | --- |
| `positiveOverflowTriangleScissorRenders` |8² triangle(5,6),(16,6),(5,15): blue(5,6),red(4,6). Run only after Step6. |
| `stencilScissorRenders` |8² evenodd outer[-2,-2,10,10],hole[2,2,6,6]: blue(1,1),red(3,3); concave winding L(-2,-2),(10,-2),(10,2),(2,2),(2,10),(-2,10): blue(1,6),red(6,6). Inverse versions complement these witnesses. |
| `initialOutsideCoverage` |8² ordinary/inverse triangle(10,10),(14,10),(10,14), plus rectangle Path[10,10,14,14]. INTERSECT ordinary allred/inverse allblue; DIFFERENCE ordinary allblue/inverse allred. Full grid assertions. |
| `lateLayerOutsideCoverage` |16² root, explicit bounded layer[4,3,12,11], clip path triangle(0,0),(2,0),(0,2) captured in root coordinates before layer: root-visible but layer-empty. Same four outcomes inside layer, outside allred. Source inspection must confirm this route reaches rebind; if public simplification removes it, report before substituting a different test. |
| `orderedOutsideCoverage` |Ordinary rectangle Path[1,1,7,7] plus outside triangle, outside before/after, normal/inverse×Intersect/Difference. Identity cases blue(3,3),red(0,0); zero cases allred. Layer version translates main mask(4,3), keeps outside root-visible as previous row. |
| `translatedLayerScissor` |16² root,layer[4,3,12,11], negative triangle translated(4,3): blue(5,4),red(10,9),red(3,4). |
| `affinePictureScissor` |Real PictureRecorder/Picture of the negative triangle clip and blue draw on local8²; translate picture(4,3) on16² red. Same three witnesses. Keep historical perspective clip refusal fixtures unchanged. |
| `stencilAndScissorReset` |16² root: first evenodd clip outer[-2,-2,6,6],hole[1,1,4,4],blue draw then restore; second hard concave clip L(4,0),(8,0),(8,2),(6,2),(6,6),(4,6),green draw then restore; independent green rect[12,12,14,14]. Assert red(2,2),blue(0,4),green(5,4),red(7,4),green(12,12). Layer[4,3,12,11] companion uses the two clips translated(4,3), marker remains root[12,12,14,14]; witnesses red(6,5),blue(4,7),green(9,7),red(11,7),green(12,12). |
| `analyticRectScissor` |Transparent8², Rect clip[-.25,0,.5,8], opaque black full draw. AA: alpha128(0,3),0(1,3); hard: alpha0 at both. Root and translated layer companions preserve values. |
| `analyticRRectScissor` |8² RRect[-2,-2,6,6],radius1 hard/analyticAA: blue(0,3),red(7,3),red(7,7). Translated layer companion keeps these local witnesses. Avoid fractional curved-edge oracle. |
| `aa4BinaryPathScissor` |8² evenodd path outer[-2,-2,10,10],hole[2,2,6,6],AA4: blue(1,1),red(3,3),inverse complement. Outside rectangle Path[10,10,14,14] AA4 normal/inverse×Intersect/Difference: full grids as initialOutsideCoverage. Exercise admitted layer forms; any existing capability refusal must be explicitly attributed, not hidden by skip or expanded silently. |
| `budgetRefusalPreservesSentinelAndRecovers` |First render independent valid red sentinel; same runtime reject the negative-clip scene under `frameLocalBudgetBytes=1L` with typed budget error, sentinel unchanged; then valid scene blue(1,1),red(6,6) renders/repeats. Do not dispose runtime between refusal and recovery. |

- [ ] **Step 8: Controller runs the complete new class and covering native tests.**

Expected all reached new fixtures succeed, no failure/skip/refusal; attribute any preexisting contract gap explicitly. Run class selector `*W7ClipProducerScissorSurfacePixelTest` then covering groups listed below. Worker appends exact controller command/output/archive in report, self-reviews and fixes via focused reruns, then commits product/test files only. Do not claim unexecuted tests.

- [ ] **Step 9: Sol task review then scoped fix/re-review if required.**

Controller creates package from recorded task BASE to HEAD; reviewer reads brief/report/diff and gives both spec and quality verdicts. No duplicate native runs. Resume the same implementer for findings; no controller product/test fixes.

## Controller qualification and publication gate (after Task1)

Native command template (fresh ARCHIVE each time; exact session retained):

```text
rtk proxy ruby /private/tmp/kanvas-w7-clip-scissor.L7Iwrt/bounded-run.rb ARCHIVE 240 ./gradlew TASK SELECTORS --offline --no-daemon --no-build-cache --no-parallel --console=plain -I /private/tmp/kanvas-w7-aa-blend.rFtTrn/review-evidence.init.gradle -Pw7.validationDir=ARCHIVE
```

- [ ] Cover existing W4e selectors `GPUPlanSurfacePixelTest.W4e public hard*`, `*.W4e public mixed hard*`, `*.W4e public Path AA4*`, `*.W4e public Surface*`, `*.W4e public inverse keeps*`, `*.W4e public budget error*`, plus entire `W6aLayerW4W5SurfacePixelTest`. Archive skips distinctly; never import the old AA skip into new witnesses.
- [ ] Re-run prior public94 (`SurfaceTest`, `PictureTest`, `W7HardPictureSurfacePixelTest`, the three empty W6b witnesses), GM9 (`Sk3dSimpleSurfacePixelTest`, `AlphaGradientsSurfacePixelTest`, `HardstopGradientSurfacePixelTest`), and covering160 (`W7AaDeferredBlendSurfacePixelTest`, `W7AaDeferredPictureSurfacePixelTest`, `W7AffineRectSurfacePixelTest`, `W7CoveredPlusSurfacePixelTest`, `W7RectHairlineSurfacePixelTest`); confirm exact class names from the previous run command before launch.
- [ ] At final product SHA, one bounded full `:kanvas:test`. Compare every reached failure by identity with inherited40; list deadline skips/unreached tests. Timeout is not a successful global suite.
- [ ] Final corpus631/443 sequential slices0–607,607–608,608–631 with30s GM watchdog and PNG enabled; strict aggregator vs `sk3d-port-813e61f09.json`. Preserve all18 invariants and inspect every changed rendered hash/metric; no count-gain promise.
- [ ] Astra whole-branch review of source, qualification and deferred minors, then at most one combined fix wave and scoped Sol re-review. If source changes, rerun covering qualification at actual final SHA; prior source evidence must not be relabeled.
- [ ] Update `refactor/README.md`, `status.md`, `pilotage.md`, plan and exact-SHA snapshot with gains, losses, unresolved limits. Publish/attach draft stacked on `codex/w7-sk3d-port` (#2427). Keep W7 active.

## Self-review

One end-to-end task is deliberate: producer contract, recipes and both consumers must change atomically to admit the same fixture. All design requirements map to Steps3–5 and explicit public witnesses; no orphan interface, unspecified geometry conversion or native guard relaxation. Controller gates are evidence/publication, not extra product tasks. The new matrix covers the five review-focus risks without new infrastructure tests.
