# W7 Mixed Direct-Inverse Scene Diagnosis Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Steps use checkbox syntax.

**Goal:** Reproduce or falsify the suspected mixed ordinary/direct-inverse scene inventory gap before choosing an architectural repair.
**Architecture:** Public Surface pixel tests first; compare inverse draw alone, ordinary background plus inverse draw, and equivalent inverse clip. Hard and AA variants distinguish inventory from coverage-route effects. No renderer change is authorized by this diagnostic task.
**Tech Stack:** Kotlin/JVM, Surface, native wgpu/Metal.
**Spec:** Bounded diagnostic below, under the user's W7 carte blanche; the renderer repair design will follow runtime evidence, not a guessed guard change.

## Global Constraints

- Worktree /Users/chaos/.codex/worktrees/w7-gm-diagnostic/kanvas, branch codex/w7-inverse-scene-inventory, base0dce69805c0b441f71279ad520455b4163be9c82 (draft#2430).
- Only public real Surface pixels/native evidence. No mocks, infrastructure/source-text/forwarding tests, GPU-availability skips, CPU fallback, fake Picture or GM-name routing.
- Geometry uses org.graphiks.math.geometry.RectF32; no new numeric geometry outside math, I/F32/64 nomenclature retained.
- Preserve proofs, CompositionEnvelope, admission, epsilon, budgets/caps, references/thresholds,631registry/443eligible, scopes/domains/exclusions. Fonts, codecs externally decoded and jpg-color-cube remain excluded.
- Controller alone runs Gradle/native, serial private bounded240s process groups, actual handle retained to terminal. Workers have no runtime/build/subagent authority.
- Worker owns test/product edits; controller owns plan/evidence/publication. RTK shell and apply_patch edits. Do not commit failing test changes or alter product until controller has causal evidence and an approved repair design.
- Existing baseline: source unchanged from measured207rendered/184compared and41freshnative; global be813afd7 remains red/incomplete, not requalified. No merge/cleanup/W7 closure.

## Diagnostic question

The materializer's directInverseDomain requires no mask inventory and a direct-inverse consumer condition, then otherwise requires paired mask accumulators and resolved resources. Source suggests ordinary siblings can break that classification; no runtime value has yet identified the failing conjunct. Merely changing all to any would not prove authentic target/depth/resolve/scissor/sample/V/I/U authority.

Three 8x8 scenes use an integer-aligned inverse-winding rectangle (2,2)-(6,6). For each, compare antiAlias=false and true on the inverse geometry/clip while ordinary rectangles stay explicitly non-AA. Expected pixels are hand-derived, never from product helpers. A transparent interior in the inverse-only case follows the existing fresh Surface transparent clear contract; the other scenes explicitly draw their backgrounds.

## Review Focus

- Direct inverse is not silently replaced by ordinary fill: inside/outside literal pixels differ.
- Adding an ordinary sibling cannot erase/reverse the inverse or fail an otherwise valid frame.
- AA route effects are separated from a scene-inventory effect by independent hard/AA tests.
- No hidden baseline white: inverse-only interior is transparent, mixed scene interior blue, inverse clip interior white.
- Native submission and repeat remain observable; an exception is a real failed positive, not converted to a successful refusal assertion.

### Task 1: Add public causal pixel witnesses, tests only

**Create:** kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W7InverseSceneInventorySurfacePixelTest.kt.
**Read for interfaces:** W7ClipProducerScissorSurfacePixelTest.kt and GPUPlanSurfacePixelTest.kt in the same directory. Do not copy their capability-skip helpers.
**Consumes:** Surface(8,8), canvas, Path/addRect/FillType.INVERSE_WINDING, Paint, ClipOp.INTERSECT, RenderResult pixels/nativeEvidenceScopeKinds/stats/diagnostics; @AfterEach GPUBackendRuntimeFactory.dispose.
**Produces:** six independent @Test methods, each rendering its own fixture. No product file change.

- [ ] Before bodies, name the bug caught in concise comments: wrong inverse polarity, lost ordinary sibling, or missing native inventory; expected pixels independent of implementation.
- [ ] Add inverseOnlyHard and inverseOnlyAA: draw inverse rect in white, no other draw/clip. Assert every one of64pixels: for x,y in2..5, literal[0,0,0,0]; elsewhere[255,255,255,255]. No implicit white initialization.
- [ ] Add ordinaryThenInverseHard and ordinaryThenInverseAA: full8x8blue rectangle with antiAlias=false, then same inverse white path. Assert interior[0,0,255,255], exterior[255,255,255,255].
- [ ] Add inverseClipHard and inverseClipAA: full8x8white rectangle antiAlias=false, clipPath same inverse rect with explicit INTERSECT and variant antiAlias, then full8x8blue rectangle antiAlias=false. Assert interior[255,255,255,255], exterior[0,0,255,255].
- [ ] All six assert Render+Readback, dispatched>0, refused=0, emptydiagnostics; render same retained Surface a second time, assert clean evidence and byte-identical pixels. Do not catch expected positive render exceptions or change expectations on failure.
- [ ] Self-review imports/API/expected masks. Freeze tests only and report READY_FOR_DIAGNOSIS. No commit, build or product change. Controller executes :kanvas:test --tests '*W7InverseSceneInventorySurfacePixelTest' with private runner/init, examines all six terminal outcomes. Build/import failure is invalid evidence, not causal RED.
- [ ] After results, report exactly what is confirmed and unconfirmed; controller reviews the test evidence and defines a repair separately if needed. Do not infer the precise materializer conjunct from its shared error string.

## Controller continuation

- [ ] Audit fresh run and independent test review; capture exact boundary values if refusal reproduces.
- [ ] If all six pass, preserve the falsification and reproduce the curved original case before alleging an inventory bug. If some fail, isolate the differing condition and seek Astra architecture support before modifying admission.
- [ ] Write measured repair design/implementation tasks only once the cause is established. Keep RED tests local until a qualified solution or explicitly reviewed diagnostic artifact is ready; never publish a green claim for this preliminary phase.
