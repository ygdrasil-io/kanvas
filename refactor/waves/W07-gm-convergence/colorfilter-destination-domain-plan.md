# W7 destination-domain Matrix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` to implement this plan task-by-task, with a fresh reviewer gate after each task. The original implementer owns fixes to their changes.

**Goal:** Qualify native `saveLayer` Matrix evaluation and restore in `LINEAR` and `SRGB_ENCODED`, then make the separate, explicit GM111 domain declaration and measure its compatibility effect.

**Architecture:** Thread destination `CompositionDomain` through the Matrix execution graph, W6b filter resources/recipes, native materialization and cache identity, while preserving the `LINEAR` defaults and numeric order. Task 1 proves the capability with public pixels and scoped native receipts; Task 2 changes only GM111’s declared domain after Task 1 passes its fresh Sol review.

**Tech Stack:** Kotlin, public `Surface` API, GPU/WGPU native renderer, Gradle/JUnit, existing Skia GM parity checkpoint.

**Spec:** `refactor/waves/W07-gm-convergence/colorfilter-destination-domain-design.md`; diagnostic context: `refactor/waves/W07-gm-convergence/colorfilter-domain-diagnostic.md`.

## Global Constraints

- `CompositionDomain.LINEAR` remains the default; preserve all existing LINEAR results and identities where possible.
- Encoded filter admission is one non-nested public `saveLayer` with a leaf Matrix `ImageFilter.ColorFilter`, current/null input, captured alpha, and `SrcOver`; preserve every unrelated refusal.
- Encoded color uses `RGBA8_UNORM_ENCODED_SRGB_PREMUL` → `RGBA8Unorm`; linear color uses `RGBA8_UNORM_SRGB_LINEAR_PREMUL` → `RGBA8UnormSrgb`; one sample per target; no hidden sRGB conversion.
- Preserve 20 Matrix coefficients, matrix/clamp/premultiply order, budgets, caps, lifetime, epochs, quotas, reservations, accounting and cache reuse rules.
- Oracles are analytical and independent of product graphs/rendering, computed before `Surface`; account for every stage’s UNORM quantization; compare full 32×32 buffers using existing ±2 pixel policy and exact deterministic alpha.
- Do not add source-text, forwarding, or test-infrastructure tests; do not change the comparator, tolerance, default domain, decoder, references or global score thresholds.
- No CPU fallback, mock renderer, fake receipt, GPU skip, new exclusion, budget relaxation, shader change based on static suspicion, or encoded direct-filter admission.
- Preserve protected fixtures `96cd`, `b6206`, `490760`, `9a2058`; keep GM111 untouched throughout Task 1.
- Controller alone runs native work, one runtime invocation at a time, with the frozen wrapper, existing init, `--offline --no-daemon`, owned outer 240-second limit, FULL log/exit audit and PGID-empty check; separate seal precedes writes.
- `$W7_INIT_SCRIPT` in command examples is the controller’s already existing init-script path; the executor does not create or edit an init script.
- Task 2 keeps the existing `measureSkiaParity` configuration, denominator 443, GM vertices index607 and timeout; do not run fonts, codecs or jpg-color-cube rows.

## Review Focus

1. Transparent input with RGB/alpha matrix bias, alpha-zero input, and clamp boundaries must execute in the correct domain; pin with `alphaBiasClampAndTransparentBlackAreDomainCorrect` and `transparentBlackBiasLayerMatchesPerStageOracle` in Task 1.
2. Content outside a small draw can still be inside a full-sized source texture; the layer hint is not proof of out-of-extent sampling. Pin in-texture black in the layer test and separately inspect the direct LINEAR falsifier’s actual source/output extents and native sample receipt in Task 1.
3. Reusing one `Surface` cannot switch its configured domain, and caches may be per-Surface; prove same-domain replay and `LINEAR → SRGB_ENCODED → LINEAR` results on explicitly configured surfaces without promising cross-domain reuse in `destinationDomainsDoNotContaminateIndependentSurfaces`.
4. Restore alpha, source-over background and child ordering can be non-commutative; pin transparent and opaque backgrounds plus before/layer/after order in `restoreAlphaBackgroundsAndOrderMatchOracle`.
5. Unsupported encoded nesting, filters, shader/image, blend and backdrop/init cases must refuse and recover without poisoning a valid next frame; pin each refusal and same-Surface recovery in `foreignEncodedLayerFamiliesRefuseAndRecover`.

---

### Task 1: Native destination-domain Matrix layer capability

**Files:**

- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W7ColorFilterDestinationDomainSurfacePixelTest.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/CompositionAdmissionV1.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/ColorFilterPlanCompilerV1.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/ColorFilterExecutionPlanV1.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/ColorOperationGraphV1.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/ColorSourceProofV1.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/NumericOperationGraphV1.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/W5fColorOperationEmitterV1.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W6aLayerGraphConstruction.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W6bFilterGraphConstruction.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanResources.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/NativeSiteRecipeV1.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W6FilterCompositeLayerPlainRecipeV1.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanPhysicalLayoutV1.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUW6cSpatialFilterSessionCache.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kW6aLayerFramePayloadMaterializer.kt`
- Read-only neighbor suites: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W6cMultiInputSurfaceTest.kt`, `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W6cSpatialCacheRecoverySurfaceTest.kt`, `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W6aLayerRestoreSurfacePixelTest.kt`, `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W6bFilterAdmissionRecoverySurfaceTest.kt`, `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W7RootAaEncodedCapabilitySurfacePixelTest.kt`, `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/filters/SrgbMatrixColorFilterTest.kt`, `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kSrgbMatrixColorFilterSmokeTest.kt`, and `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SrgbColorFilterGmSurfaceRefusalEvidenceTest.kt`.

**Interfaces:**

- Consumes: `RenderConfig(compositionDomain: CompositionDomain = LINEAR)`; public `Surface.canvas`, `saveLayer(SaveLayerRec)`, `drawRect`, `restore`, `render`; existing W6a target logical formats and W6b/native recipe validators.
- Produces: `ColorFilterPlanCompilerV1.compile(filter: ColorFilterNode, compositionDomain: CompositionDomain = CompositionDomain.LINEAR): ColorFilterCompileResultV1`; `ColorFilterExecutionPlanV1.matrix(filter: ColorFilterNode.Matrix, compositionDomain: CompositionDomain = CompositionDomain.LINEAR)`; `ColorOperationGraphV1.Scalar.InputEncodedPremul(channelI32: Int)`; sealed filter and composite recipes whose source, filter, parent and restore domain/format/sample-count agree.
- The encoded compiler admits only a `ColorFilterNode.Matrix` leaf and refuses all other encoded filter trees before native work. Existing calls default to LINEAR. W6b passes the parent destination domain explicitly. Target-bound execution identity includes its domain. A Matrix graph selects `InputLinearPremul` or `InputEncodedPremul`; every visitor, emitter, evaluator and canonical substitution handles both explicitly.

- [ ] **Step 1: Add the public pixel tests and independent staged oracle.** Create the named class and these test methods: `identityAndLumaRespectBothDestinationDomains`; `partialAlphaMatrixUsesIndependentStageQuantization`; `alphaBiasClampAndTransparentBlackAreDomainCorrect`; `transparentBlackBiasLayerMatchesPerStageOracle`; `restoreAlphaBackgroundsAndOrderMatchOracle`; `destinationDomainsDoNotContaminateIndependentSurfaces`; `foreignEncodedLayerFamiliesRefuseAndRecover`; `directLinearMatrixBiasRecordsOutsideExtentFalsifier`. Use `Surface(32,32)` for full-buffer comparisons and set both `RenderConfig.compositionDomain` and its matching physical `gpuColorFormat` (`LINEAR` → `RGBA8_UNORM_SRGB`, `SRGB_ENCODED` → `RGBA8_UNORM`); the expected calculations run before constructing each `Surface`.

  Define independent scalar oracle functions in this test file, without invoking `ColorOperationGraphV1`, compiler/planner, renderer or readback to derive expected values. Convert ARGB source RGB with identity in encoded space and `EOTF_sRGB(c/255)` in LINEAR; premultiply by alpha. For each LINEAR attachment write quantize RGB as `round(255 * OETF_sRGB(clamp01(v)))`, alpha as `round(255 * clamp01(a))`, and decode quantized RGB with `EOTF_sRGB(byte/255)` when the next stage samples it. For each encoded attachment write `round(255 * clamp01(v))` with no transfer conversion. Matrix evaluation is `straight = (a == 0 ? 0 : premul/a)`, four row-major five-term dot products plus bias, clamp each result to `[0,1]`, then premultiply RGB by clamped output alpha. Apply quantization after source, filter and restore attachment writes. Restore captured group alpha by scaling premultiplied source RGBA, then composite source-over (`out = src + dst*(1-src.a)`) and quantize. Keep alpha exact when the deterministic equation yields an exact byte; otherwise compare against the oracle under the existing ±2 per-pixel policy.

  Pin exact source/matrix cases: identity on `[128,64,192,255]`; luma matrix rows `.2126,.7152,.0722,0,0` with alpha identity on opaque red, expecting `[127,127,127,255]` LINEAR and `[54,54,54,255]` encoded; partial source `[64,128,192,128]` with rows `[.5,.25,0,0,.0625]`, `[0,.5,.25,0,.125]`, `[.25,0,.5,0,.125]`, `[0,0,0,.5,.25]`; alpha/clamp matrix with RGB diagonal `2,1.5,1.5`, RGB red bias `-.25`, and alpha row `[0,0,0,2,-.25]`; alpha-zero source and matrix offsets `(.25,.5,.75,1)`. For the transparent layer bias, zero RGB coefficients and offsets `(.25,.5,.75,1)` must yield complete uniform buffers `[137,188,225,255]` LINEAR and `[64,128,191,255]` encoded.

  `restoreAlphaBackgroundsAndOrderMatchOracle` uses captured restore alpha `128/255`, once over transparent and once over opaque `[32,96,160,255]`, with `draw-before → layer-filter → restore → draw-after`; expected buffers differ from the counterfactual with after-draw moved before the layer. `destinationDomainsDoNotContaminateIndependentSurfaces` renders the same commands on LINEAR, encoded, then LINEAR `Surface`s, repeats render/replay on the same domain-specific Surface and compares all three full outputs to their corresponding oracle; do not assert shared-cache reuse across surfaces.

  In `foreignEncodedLayerFamiliesRefuseAndRecover`, use separate cases for nested saveLayer, `initWithPrevious`, backdrop, `ImageFilter.ColorFilter(Luma)` and composed Matrix, non-SrcOver blend, shader/image input, and direct paint colorFilter. Assert each is refused through the existing public failure route before readback mutates a sentinel, then discard the refused frame, submit a valid Matrix layer on the same Surface, and compare the recovery buffer to its oracle.

  The layer transparent-black fixture draws only hard `Rect(4,4,8,8)` on `Surface(32,32)` with matching advisory saveLayer bounds and no clip; its contract is black transparent **inside** the actual input texture. The distinct LINEAR falsifier draws that same rect directly with `Paint(imageFilter = ImageFilter.ColorFilter(ColorFilter.Matrix(biasMatrix), null), antiAlias = false)`. Inspect sealed construction facts and the native sample receipt to establish actual compact source extent, full requested output and at least one sample outside the source. If these facts cannot establish the extent relationship, record the exact unexercised boundary; do not count transparent texels inside a full-size input texture as outside-extent evidence.

- [ ] **Step 2: Prove the new surface suite is RED before changing production source.** From the controller-owned one-invocation runner, run `rtk ./gradlew --offline --no-daemon --init-script "$W7_INIT_SCRIPT" :kanvas:test --tests org.graphiks.kanvas.surface.W7ColorFilterDestinationDomainSurfacePixelTest`; launch it under the audited outer240 process-group harness and retain its FULL log and exit receipt. Expected: current LINEAR controls either pass or expose no regression, while the new encoded Matrix layer is refused or differs from the encoded independent oracle. Confirm owned PGID is empty before any source edit; if the suite does not provide this RED evidence, stop and record the actual refusal/route rather than assuming the diagnosis.

- [ ] **Step 3: Add the domain-aware compiler and numeric graph.** Implement the exact interfaces above in `ColorFilterPlanCompilerV1.kt` and `ColorFilterExecutionPlanV1.kt`; create the two domain-selected Matrix input variants in `ColorOperationGraphV1.kt`; update `ColorSourceProofV1.kt`, `NumericOperationGraphV1.kt`, and `W5fColorOperationEmitterV1.kt` for the new scalar. Keep matrix straight/premul, clamp and dynamic-uniform layout unchanged. Encoded `Compose`, `Lerp`, preset and non-Matrix nodes return existing refusal results before native materialization; LINEAR callers retain the default.

- [ ] **Step 4: Propagate the frozen domain and logical format through W6.** Extend only Matrix layer admission in `CompositionAdmissionV1.kt`; pass the selected logical format/domain from `W6aLayerGraphConstruction.kt` through `W6bFilterGraphConstruction.kt` resource sealing and filter compilation; add the encoded format variant to the relevant `PlanResources.kt`, `W6FilterColorFilterRecipeV1`/`NativeSiteRecipeV1.kt`, and `W6FilterCompositeLayerPlainRecipeV1.kt` checks. Every positive Matrix receipt must show matching source/filter/parent/restore domain and format, with sample count 1. Keep all unrelated W6b sealed formats and refusal checks intact.

- [ ] **Step 5: Materialize recipe formats and domain-bound cache identities.** In `GPUWgpu4kW6aLayerFramePayloadMaterializer.kt`, use the recipe’s target GPU format explicitly for both the ColorFilter pass and plain layer restore; do not select either from `w6aColorTarget(recipe.blend)`’s implicit default. Update target-bound `colorSpaceIdentity`/layout in `PlanPhysicalLayoutV1.kt` and exact-format cache creation in `GPUW6cSpatialFilterSessionCache.kt`. Preserve current per-Surface cache lifetime; prove plan/recipe identities distinguish LINEAR and encoded and that LINEAR → encoded → LINEAR surfaces never materialize an encoded format for either LINEAR render. Do not claim a cache hit across separate Surface-owned sessions.

- [ ] **Step 6: Run the new Surface tests GREEN and inspect native receipts.** Repeat the exact Step 2 command through the same controller harness after implementation. Expected: all eight named methods pass; every positive result is clean, dispatches with zero refusals, returns all 1024 RGBA pixels (4096 bytes), reports `Render` and `Readback` native scopes and successful native submission/readback completion; captured plan/recipe evidence shows the filter and restore passes attached with the domain/formats above. The direct LINEAR falsifier either proves a sample outside the actual compact source extent or leaves a written, precise boundary limitation. A mismatch does not authorize a shader edit until its first divergent boundary and causal outside-extent return-zero mechanism are demonstrated.

- [ ] **Step 7: Run targeted neighbor suites and keep GM111 unchanged.** Run sequentially, through the same offline/no-daemon controller harness: `rtk ./gradlew --offline --no-daemon --init-script "$W7_INIT_SCRIPT" :kanvas:test --tests org.graphiks.kanvas.surface.W6aLayerRestoreSurfacePixelTest --tests org.graphiks.kanvas.surface.W6bFilterAdmissionRecoverySurfaceTest --tests org.graphiks.kanvas.surface.W6cMultiInputSurfaceTest --tests org.graphiks.kanvas.surface.W6cSpatialCacheRecoverySurfaceTest --tests org.graphiks.kanvas.surface.W7RootAaEncodedCapabilitySurfacePixelTest`; then `rtk ./gradlew --offline --no-daemon --init-script "$W7_INIT_SCRIPT" :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.filters.SrgbMatrixColorFilterTest --tests org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kSrgbMatrixColorFilterSmokeTest`; then `rtk ./gradlew --offline --no-daemon --init-script "$W7_INIT_SCRIPT" :integration-tests:skia:test --tests org.graphiks.kanvas.skia.SrgbColorFilterGmSurfaceRefusalEvidenceTest`. Expected: every existing assertion passes, including protected AA encoded replay and the existing refusal of direct encoded paint filters; GM111’s source/default domain remains unchanged. Keep one runtime invocation active at a time and preserve each FULL log/exit/PGID receipt.

- [ ] **Step 8: Commit and request the fresh Task 1 Sol review.** After all receipts are complete and the design’s outside-extent limit is recorded accurately, commit only Task 1 implementation/test files with a descriptive local commit. A fresh Sol reviewer must confirm format/domain identity, refusal preservation, native attachments and alpha/transparent-black behavior before Task 2 starts. The original implementer fixes any review findings; Task 2 remains gated until that review passes.

### Task 2: Explicit GM111 encoded-domain compatibility declaration

**Files:**

- Modify: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/composite/ColorFilterImageFilterLayerGm.kt`
- Create: `refactor/waves/W07-gm-convergence/colorfilter-domain-migration.md`
- Read-only evidence: `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/SkiaGmParityCheckpoint.kt`, the existing Task 1 baseline and checkpoint outputs, and the current GM reference PNG.

**Precondition:** Task 1 is implemented, targeted native receipts are qualified, its out-of-extent finding/limitation is documented, and the fresh Sol review has passed. Task 1 did not edit GM111.

**Interfaces:**

- Consumes: `SkiaGm.compositionDomain` (default `CompositionDomain.LINEAR`), `compositionConfig()` copying that value into `RenderConfig`, and existing `:integration-tests:skia:measureSkiaParity` properties.
- Produces: one override `ColorFilterImageFilterLayerGm.compositionDomain = CompositionDomain.SRGB_ENCODED`; a migration record that distinguishes this explicit GM policy from renderer behavior and carries the before/after checkpoint evidence.

- [ ] **Step 1: Capture the Task 1 baseline for the exact GM registry slice.** Before editing GM111, invoke the existing parity checkpoint under the controller-owned outer240 harness with the frozen wrapper, existing init, offline/no-daemon settings and FULL receipt:

  ```bash
  rtk ./gradlew --offline --no-daemon --init-script "$W7_INIT_SCRIPT" :integration-tests:skia:measureSkiaParity \
    -Pgm.parityFrom=111 -Pgm.parityTo=112 -Pgm.parityTimeout=30 \
    -Pgm.rendererCommit="$W7_TASK1_COMMIT" -Pgm.parityImages=false \
    -Pgm.parityOutput="$W7_PARITY_ROOT/before-111-112"
  ```

  Set `$W7_TASK1_COMMIT` to the exact 40-hex reviewed Task 1 commit and `$W7_PARITY_ROOT` to a new, empty, retained output directory. The existing checkpoint refuses to overwrite `slice-111-112.jsonl`; retain its run row and GM row as the before record. Expected baseline row identifies registry index 111, `colorfilterimagefilter_layer`, `compositionDomain=LINEAR`, and the prior canonical/native 127-vs-54 gap; record actual outcome without rewriting references or scores.

- [ ] **Step 2: Change only GM111’s declared composition domain.** In `ColorFilterImageFilterLayerGm.kt`, add the import for `CompositionDomain` and override `compositionDomain` with `CompositionDomain.SRGB_ENCODED`. Keep its name, 32×32 dimensions, reference, coefficients `.2126/.7152/.0722`, draw operations, background, family, tolerance, threshold and refusal policy byte-for-byte unchanged.

- [ ] **Step 3: Commit the one-field GM declaration locally.** Commit only `ColorFilterImageFilterLayerGm.kt`, then record its exact 40-hex SHA as `$W7_TASK2_COMMIT`; the checkpoint’s `rendererCommit` must identify the source actually rendered. Keep the migration document out of this commit so its values can be written from receipts.

- [ ] **Step 4: Check the targeted post-change checkpoint.** Run the same command and timeout for index 111 to 112 into the unique `$W7_PARITY_ROOT/after-111-112` directory, setting `$W7_TASK2_COMMIT` to the source commit from Step 3. Expected GM row: index 111, same name/reference/dimensions/operation count as baseline, only `compositionDomain` changes to `SRGB_ENCODED`, rendered and compared, 0 refusals, full 32×32 pixels, and channel output matching the existing canonical `[54,54,54,255]` oracle (100% pixels within ±2 and exact max delta when measured exact). `declaredContractPass=true` alone is insufficient because `minSimilarity=0`; retain the row’s pixel2/max-delta, native-scope and completion evidence. If the targeted result fails, stop before the census and fix/review Task 2 without altering any excluded field.

- [ ] **Step 5: Run the unchanged full 631-entry parity census only after the target passes.** Run these five non-overlapping slices sequentially through the controller-owned one-invocation outer240 harness, using the existing checkpoint configuration, timeout 30 seconds, image output disabled, unique empty output directories and the same `$W7_TASK2_COMMIT`. Preserve each slice’s FULL log/exit audit and separate seal before starting the next slice. The split keeps GM vertices index607 isolated in its own invocation while retaining complete `[0,631)` coverage:

  ```bash
  rtk ./gradlew --offline --no-daemon --init-script "$W7_INIT_SCRIPT" :integration-tests:skia:measureSkiaParity \
    -Pgm.parityFrom=0 -Pgm.parityTo=200 -Pgm.parityTimeout=30 \
    -Pgm.rendererCommit="$W7_TASK2_COMMIT" -Pgm.parityImages=false \
    -Pgm.parityOutput="$W7_PARITY_ROOT/full-0-200"
  rtk ./gradlew --offline --no-daemon --init-script "$W7_INIT_SCRIPT" :integration-tests:skia:measureSkiaParity \
    -Pgm.parityFrom=200 -Pgm.parityTo=400 -Pgm.parityTimeout=30 \
    -Pgm.rendererCommit="$W7_TASK2_COMMIT" -Pgm.parityImages=false \
    -Pgm.parityOutput="$W7_PARITY_ROOT/full-200-400"
  rtk ./gradlew --offline --no-daemon --init-script "$W7_INIT_SCRIPT" :integration-tests:skia:measureSkiaParity \
    -Pgm.parityFrom=400 -Pgm.parityTo=607 -Pgm.parityTimeout=30 \
    -Pgm.rendererCommit="$W7_TASK2_COMMIT" -Pgm.parityImages=false \
    -Pgm.parityOutput="$W7_PARITY_ROOT/full-400-607"
  rtk ./gradlew --offline --no-daemon --init-script "$W7_INIT_SCRIPT" :integration-tests:skia:measureSkiaParity \
    -Pgm.parityFrom=607 -Pgm.parityTo=608 -Pgm.parityTimeout=30 \
    -Pgm.rendererCommit="$W7_TASK2_COMMIT" -Pgm.parityImages=false \
    -Pgm.parityOutput="$W7_PARITY_ROOT/full-607-608"
  rtk ./gradlew --offline --no-daemon --init-script "$W7_INIT_SCRIPT" :integration-tests:skia:measureSkiaParity \
    -Pgm.parityFrom=608 -Pgm.parityTo=631 -Pgm.parityTimeout=30 \
    -Pgm.rendererCommit="$W7_TASK2_COMMIT" -Pgm.parityImages=false \
    -Pgm.parityOutput="$W7_PARITY_ROOT/full-608-631"
  ```

  The five slices together cover exactly `[0,631)` without gaps or overlap. Each slice receipt should retain the registry census metadata (including `registryCount=631`) and its rows; aggregate only the retained rows for full-census outcomes, eligible denominator 443, GM vertices index607 and timeout behavior. Retain existing exclusions; do not run fonts, codecs or jpg-color-cube renders. Report failures, timeouts, refusals and measured scores as observed; do not call this run globalGREEN or claim renderer convergence from the domain migration.

- [ ] **Step 6: Write the migration record from actual checkpoint output.** In `colorfilter-domain-migration.md`, record the reviewed Task 1 commit and Task 2 source commit; before/after slice paths and SHA-256; exact index/name/domain change; row operation counts, refusal count, pixel2/max-delta and completion evidence; and each of the five full-census slice paths/hashes plus aggregated registry count, denominator, GM vertices index607, timeout and outcomes. State that this is a per-GM explicit domain policy, with upstream producer/backend/revision/configuration still unknown. State that coefficients, operations, target backgrounds, reference bytes/hash, tolerance2, `minSimilarity=0`, score file and global default did not change. Do not claim same-input bug fix or zero-delta corpus. Do not put the migration document’s own commit SHA inside that document; record its exact SHA only after commit in the SDD ledger/report alongside the known source/review commits and archive hashes.

- [ ] **Step 7: Commit the migration record and request the fresh Task 2 Sol review.** Commit only `colorfilter-domain-migration.md`; leave `test-similarity-scores.properties`, reference PNGs and dashboard thresholds unchanged. Fresh Sol review checks the single-field GM diff and that the report matches retained before/after/full-census receipts. The original implementer resolves review findings and preserves all run archives.

## Plan Self-Review

- **Spec coverage:** Task 1 owns admission, API/defaults, typed graph visitors, sealed formats/recipes, both native materialization sites, cache identity, independent per-stage oracle, alpha/background/order, transparent-black in-texture, direct LINEAR extent falsifier, foreign refusal/recovery and protected neighbor suites. Task 2 owns only the post-qualification GM111 policy change, targeted checkpoint, full existing census and truthful migration record.
- **Step scan:** Every checkbox creates a named test, runs an identified command, modifies a named component/signature, retains a specific receipt, or performs an explicitly gated commit/review. No test asks for an unspecified “appropriate edge case.”
- **Type consistency:** The compiler and Matrix signatures are defined in Task 1’s Interfaces and use the existing `CompositionDomain`; existing calls retain LINEAR by default; only the GM’s existing `compositionDomain` interface property is overridden in Task 2.
- **Review Focus:** All five risks map to the exact Task 1 tests named above; outside-extent evidence is not inferred from the layer hint or its pixel output.
- **Proportion:** Two reviewer-sized tasks, as requested; the detail specifies interfaces, numeric oracle stages, fixture values, test names, commands and gates without transcribing implementation bodies.
