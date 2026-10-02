# Root Path AA Encoded Composition Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. The controller owns every runtime, evidence audit, ordinary Git commit and PR publication.

**Goal:** Admit the closed root plain-solid AA Path/finite-stroke encoded domain without changing LINEAR or GM scenes.
**Architecture:** Retain existing math geometry and per-sample MSAA4 until the final resolve; authenticate the selected domain through source, graph, resources, keys and native validation.
**Tech Stack:** Kotlin/JUnit5, existing W4d GPU plan/renderer, native WebGPU/Metal, existing interval oracle.
**Spec:** refactor/waves/W07-gm-convergence/root-aa-encoded-design.md

## Global Constraints

- Actual checkout /Users/chaos/.codex/worktrees/w7-gm-diagnostic/kanvas; branch codex/w7-color-authority-diagnostic. cbf6 is unrelated dirty state.
- Fonts, external codecs and jpg-color-cube excluded. GM ports/domains, references, comparator, tolerance2, thresholds,631 registry/443 eligible, budgets/caps/eps remain unchanged.
- No new infrastructure/mock/source-text/forwarding tests, GPU skips, CPU renderer/fallback, fake Picture or GM routing.
- Geometry/formulas product in math, I/F32/64 nomenclature; reuse existing policies. No expanded CompositionEnvelope or numeric primitive bounds.
- Controller alone runs Gradle/native, audits original terminal/counts/messages/XML/exit in a separate next tool call, snapshots and publishes. Worker writes only its specified files and report, uses apply_patch/rtk, no Git/runtime/subagents.
- Do not read/write any completed .superpowers workspace or inherited W7InverseFilterDiagnosticSurfaceTest.kt.
- No change to LINEAR defaults or W4e/W6 AA sources; new encoded family root-only, noninverse linear-segment Path FILL/finite STROKE, solid/no effects/SrcOver/hard integer Rect clip/finite axis-aligned nonsingular CTM.
- Qualification stays RED/incomplete when controls, numeric resolve or native fail; do not patch product until the native causal gate is audited.

## Review Focus

- Correlated coincident/complementary MSAA masks must survive to the final resolve, not become independent scalar coverage.
- Same alpha with different encoded/LINEAR RGB, premultiplication and byte layout must not collide in caches.
- Extreme reciprocal F32 CTM and anisotropic normals must keep source stroke width and geometry.
- New Path cannot open layers/images/gradients/AA clips/inverse/perspective by mixture; whole-frame refusal, intact readback sentinel and recovery.
- Preflight/resource/native domain identities and exact budget boundary must agree; no relaxed seals or inferred expected pixels.

### Task 1: Pin the independent native root-MSAA domain witnesses before product edits

**Files:**
- Create: kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W7RootAaMsaaDomainSurfacePixelTest.kt
- Create: kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W7MsaaCompositionCpuOracle.kt

**Interfaces:**
- Consumes: existing W7CompositionCpuOracle.solid/store/storedSample/srcOver/swizzle and WgslFloatEnvelopeV1Oracle.Interval/gradientAdd/gradientMultiply/gradientDivide; no modifications to either.
- Produces: test-only sample-mask composition orchestrator and unchanged real Surface tests. Full expected envelopes/alternative disjointness computed before any Surface.render; source stays unchanged. Numeric/model uncertainty is NEEDS_CONTEXT, not a fitted tolerance.

- [ ] Read only the existing oracle contracts and real Surface assertion patterns; state the production mutation each new case catches.
- [ ] Add independent orchestration retaining four immutable sample states. Use explicit masks derived from the pinned standard positions, not production geometry, shaders or native helpers. For each draw: update only covered samples with independent SrcOver, store each sample in its domain, then decode/average/store once at the final resolve. Keep full code sets and store trace without changing primitive bounds. If resolve precision lacks a justified bound, report the exact gap before implementing product.
- [ ] Add LINEAR control test `linearRootStrokeSeparatesCoverageFromStoredColor`: Surface12×12; Path M4,2 L4,10 width5, black/opaque red, AA=true STROKE/BUTT/MITER; transpose for horizontal M2,4 L10,4. On transparent and on a white full Path FILL background (AA=false), verify half cells(1,5)/(6,5), interiors(3,5), exterior(0,5)/(7,5), transposed for horizontal. Native Render+Readback, clean diagnostics/zero refusal before pixels, render twice.
- [ ] Add LINEAR control `linearRootMasksStayCorrelatedUntilResolve`: same black stroke twice over white must preserve half-mask result. Complementary filled Paths on Surface8×8: left rectangle(0,0)..(2.5,8), right rectangle(2.5,0)..(8,8), both black opaque AA=true over full white Path hard; pixel(2,4) is full black. Before GPU assert disjoint from resolve-per-draw scalar SrcOver alternatives (about gray137 versus half188, and gray137 versus black0).
- [ ] Add encoded desired-behavior tests `encodedRootStrokeUsesSelectedDomain` and `encodedRootMasksStayCorrelatedUntilResolve`, same scenes/oracles, RenderConfig(compositionDomain=SRGB_ENCODED). Expected half opaque-black white result about128 instead of188, opaque-red transparent RGBabout128 with same alphaabout128. Preserve both LINEAR and encoded expectations independent/disjoint. Do NOT assert the current refusal as expected success, skip it, or relax pixels.
- [ ] Stop after writing tests, self-review and report READY_FOR_CONTROLLER_RED with changed files, exact masks/expected sets, numeric authorities/uncertainties and commands; no runtime/source/Git. Controller runs LINEAR selections first, audits, then encoded selections; controls must qualify before product release.
- [ ] Controller command pattern: rtk proxy ruby /private/tmp/kanvas-w7-inverse-inventory.hbWqUb/bounded-run.rb PRIVATE_ARCHIVE 240 ./gradlew :kanvas:test --offline --no-daemon --rerun-tasks -I /private/tmp/kanvas-w7-aa-blend.rFtTrn/review-evidence.init.gradle -Pw7.validationDir=PRIVATE_ARCHIVE --tests CLASS.METHOD. Gradle/native original terminal, NEXT separate full audit. Compilation/resource faults are not causal RED; encoded must fail specifically at closed composition capability.
- [ ] Snapshot explicit files after audit, task-scoped Sol spec/quality review; report all cannot-verifies and findings. Fix same writer, same oracles; no source until this gate closes. Task1 complete means witnesses qualified/retained causal RED, not feature GREEN.

### Task 2: Extend one closed root encoded AA capability end-to-end

**Files (existing owners; any necessary additional exact key-producer file must be named in the task brief before edits):**
- Modify: gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/CompositionAdmissionV1.kt
- Modify: gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4dGeneralPathPlanCompiler.kt
- Modify: gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W4dGeneralPathGraphLowerer.kt
- Modify: gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUW4dGeneralMsaaValidation.kt
- Verify/modify only if required: existing GPUPlanW4dGeneralPreparedAuthority and GPUW4dPathSampleContinuationAuthority key producers/consumers.
- Extend Task1 pixel test only for the named guards; no new production numeric/geometry helper unless a separately qualified math defect is found.

**Interfaces:**
- Consumes: Task1 frozen test/oracle blobs, public CompositionDomain, existing logical format/domain/source proof and math geometry.
- Produces: root-only closed encoded AA admission, native MSAA/resolve/target keys and same public Surface pixels. No new public default or GM behavior.

- [ ] Before source dispatch, controller records Task1 qualified blobs/RED/LINEAR counts, exact additional key producer paths, and statically derived budget B expression for a small fixture. The expanded test values and covering classes become Task2 brief authority; unresolved numeric/root ownership facts stop source dispatch.
- [ ] Add the remaining independent native guards first: solid(R128,G64,B32,A128), source-alpha once, RGBA/BGRA/AUTO/explicit compatible format, repeated/alternated domains, Picture memory/archive capture/snapshot SOURCE_SPACE/replay of an independently validated producer. Assert disjoint wrong-domain/wrong-alpha/wrong-channel alternatives before GPU; disclose any nondiscriminated store.
- [ ] Add identity, integer T and hard clip, then tiny local teeny vertical source(20*s,20*s)..(20*s,100*s), width5*s, s=.00005f, T50 then reciprocalscale; compare literal qualified device body/half masks away from caps. Add anisotropic source H/V under T(8,8)S(4,2), finite width2 with source centers yielding device half boundaries; exact fixtures recorded in brief before first run.
- [ ] Add whole-frame refusal/sentinel/discard/recovery cases after a valid Path: layer, AA clip, inverse, shader, filter, perspective/skew, AA hairline, unsupported join/cap and incompatible target. Preserve existing encoded hard Rect/image/gradient/plain-layer fixtures as controls, not newly admitted mixtures.
- [ ] Implement closed frame admission and contextual transforms; normalize direct solid sources with target domain, retain original Path/paint/command/source identities.
- [ ] Propagate selected logical format through root AA preflight/construction/graph/lowering/keys/native interpretation. Keep W4e/W6 sources LINEAR; authenticate generation/views/roles/samples4/1 and final-only resolve. No global AA_FORMAT replacement or gate removal alone.
- [ ] Controller runs unchanged Task1 + new guards, audits; budget B admits and B−1 refuses before partial readback, recovery succeeds. Sol review with actual receipts, same-worker fix loop.
- [ ] Controller runs proportional root/encoded/LINEAR control classes and one bounded full project suite; fresh corpus631/443 with domains/scopes unchanged, identify every failure/lost outcome/hash/diagnostic/metric rather than masking. No global green on a partial or terminated run.
- [ ] Final scoped whole-lot review/fixwave, ordinary explicit commit and draft PR stacked on #2435, attach, verify exact remote base/head/body. Keep global failures, AA sampling mismatch, reference provenance, crbug/RRect/inverse/filter/pathops debts open. GM opt-in is a separate future measured scene change.
