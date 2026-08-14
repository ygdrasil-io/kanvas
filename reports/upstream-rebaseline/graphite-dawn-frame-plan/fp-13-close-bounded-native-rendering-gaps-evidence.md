# FP-13 Close Bounded Native-Rendering Gaps — Evidence

Status: **in progress** (Task 1 complete: `colr-v0-color-glyph` scene CPU-oracle
fix closes the byte-exact pin; Task 2 complete: `PipelineTypesTest` hygiene +
wgsl4k ticket; further tasks append their own sections).

Branch: `codex/graphite-dawn-frame-fp13`. Machine: Linux, JDK Temurin 25, GPU =
Vulkan **llvmpipe** (software, CPU; Mesa 26.0.3, LLVM 21.1.8), Xvfb `:99`. All
GPU suite runs used `DISPLAY=:99`.

## Task 0 — M86 burn-down wave (evidence only, no renderer code)

Task 0 is the M86 Fidelity Burn-Down Wave 2 input snapshot (plan §5, Phase 0):
the auditable JUnit XML snapshot of `:integration-tests:skia:test`, the
machine-readable residual-row inventory (341 rows) and the SkiaGmRunner refusal
inventory (498 rows), the ranked candidate list, and the required M86
statements. **Task 0 applies no renderer fix** — it is burn-down planning
evidence, not a completed visual correction.

### 1.1 Snapshot run

Command (headless, `DISPLAY=:99`):

```bash
./gradlew -F off :integration-tests:skia:test --no-parallel --console=plain
```

Result: **686 tests completed, 504 failed, 40 skipped** — matching FP-12 §1.2
exactly: the `SkiaGmRunner` contributes 615 GM cases (498 failures + 40 aborts
(39 `RenderCost.BLOCKING` + 1 untrustable reference `custommesh_uniforms`) + 77
passing); the remaining 71 module tests contribute 6 failures (498 + 6 = 504).
The 498 runner failures break down as: 489 terminal refusals
(`GPUPreparedSurfaceTerminalException` from a fresh render) + 7 missing
reference (`color`, `filter`, `orientation`, `rect`, `clippedbitmapshaders`,
`wacky_yuv_formats`, `lineargradientrt`) + 1 size mismatch (`scale-pixels`,
`Buffer sizes differ`) + 1 below threshold (`text_scale_skew`, similarity
77.75% < 80%).

Snapshot: 26 JUnit XML files committed under
`reports/upstream-rebaseline/graphite-dawn-frame-plan/fp13-m86-wave/
junit-xml-2026-08-13/` (commit `53c68881b`, `docs(evidence): fp13 m86 wave
junit xml snapshot`). The runner XML (`TEST-org.graphiks.kanvas.skia.
SkiaGmRunner.xml`, 615 parameterized cases) is the auditable source for the
refusal inventory; the build-dir copy is gitignored output, which is why the
committed copy exists.

### 1.2 Inventory generation (machine-readable)

Scripts (Python 3, stdlib only, no external deps; `--check` verifies counts and
writes nothing):

- `fp13-m86-wave/residual-inventory.py` — parses the blend matrix source
  (`GPUAllApiBlendSurfaceTest.kt`: `BlendMode.kt` enum order, `BlendContext`,
  the `ARTISTIC_MODES` / `MULTI_RENDER_DST_COPY_MODES` expressions) and the
  three clip pin files (`GPUClipCoverageSurfaceTest.kt`,
  `GPUClipAdvancedBlendSurfaceTest.kt`, `GPUPathClipRegressionTest.kt`), and
  emits:
  - `fp13-m86-wave/residual-inventory.csv` — **341 rows**, one per residual row
    (item, family, mode, context, refusalCode, referenceKind, expectedGpuRoute,
    pmValue, risk, ownerTask);
  - `fp13-m86-wave/ranked-candidates.md` — the 341 rows ranked by PM value ÷
    risk, grouped by item with per-item summary and the full row enumeration.
- `fp13-m86-wave/refusals-inventory.py` — parses the committed runner XML
  (stdlib `xml.etree`) and resolves each GM's logical name from the GM Kotlin
  sources (class-body `name` declaration; parent-constructor literal/named
  argument; curated defaults for the computed-name classes, each pinned by a
  source assertion); emits `fp13-m86-wave/refusals-inventory.csv` —
  **498 rows** (gm, refusalCode, rootCauseBucket, item).

Verified counts (both scripts `--check`, run at the commit above):

| inventory | expected | measured |
| --- | --- | --- |
| residual rows | 341 | 341 |
| …mixed-layout blend rows | 199 | 199 |
| …clip pins on mixed-layout | 10 (Coverage 1, Advanced 8, PathClip 1) | 10 |
| …path-destination-read | 60 | 60 |
| …direct-geometry re-points (2 DrawRRect DST + 30 DrawPoint) | 32 | 32 |
| …frame-global-pipeline re-points | 30 | 30 |
| …analytic-clip-non-direct | 4 | 4 |
| …dst-read-formula pins | 2 | 2 |
| …analytic-shape-multi-key pins | 2 | 2 |
| …complex-clip blur pins | 2 | 2 |
| SkiaGmRunner failures | 498 | 498 |
| …terminal refusals (51 distinct codes) | 489 | 489 |
| …missing reference / size mismatch / below threshold | 7 + 1 + 1 | 7 + 1 + 1 |

Item assignment cross-check (plan §1): item 1 = 32 (2 dst-read-formula pins +
30 frame-global fallout sharing the root), item 2 = 2, item 3 = 2, item 4 = 241
(199 blend + 10 clip pins + the 32 Task-6 split-resource fallout rows owned by
Task 6), item 5 = 4, item 6 = 60. No discrepancy was found between the
script-derived counts and the 341/489 arithmetic. Five GM rows map to plan §1
items: 3 × `mixed_uniform_layouts` → item 4, 1 × `path-destination-read` →
item 6, 1 × `clip_producer_authority` → item 3; the remaining 493 map to
`none`.

### 1.3 Required M86 statements

- **"CPU-oracle rows do not count as Skia-comparable fidelity."** All 341
  residual rows are `cpu-oracle` (verified against the pure-Kotlin pixel oracle
  of the blend/clip suites), as are the 284 prepared blend-matrix rows and the
  colr-v0 scene rows (Task 1). None is Skia-comparable: this wave can move
  rendering breadth, runtime, and PM operability scores only (M86, spec
  `03-skia-fidelity-and-gm-promotion.md`).
- **"No global similarity threshold was weakened."** This wave changes no
  thresholds and no assertion; `text_scale_skew` remains at its measured
  77.75% < 80% state (documented divergence, FP-12 §1.1).
- **Sprint report — "renderer fixes applied", tracked task-by-task:**

  | task | scope | renderer fix applied |
  | --- | --- | --- |
  | Task 0 (this task) | M86 wave — evidence only | **no renderer fix**; the sprint is a burn-down wave in progress |
  | Task 1 | colr-v0 scenes oracle fix | harness only — no renderer fix |
  | Task 2 | PipelineTypesTest hygiene + wgsl4k ticket | **no renderer fix** — test-hygiene only (unambiguously invalid WGSL sample + upstream ticket) |
  | Tasks 3-8 | dst-read formula; multi-key dst-read; complex-clip blur; 64/160 split; analytic clips non-direct; stencil-continuation | renderer fixes planned (Tasks 3-8); tracked in this evidence doc when each task lands |
  | Task 9 | evidence reconciliation + roadmap | — |

- **Root-cause classification requirement**: every residual row carries its
  refusal code and expected GPU route (`residual-inventory.csv`); every GM
  refusal carries a root-cause bucket in the fp-11 §1/§2 classification
  (`refusals-inventory.csv`), e.g. `unsupported.native-core-primitive.*` →
  "unsupported execution feature", `invalid.*` → "invalid frame plan / preflight
  seal", `failed.*` → "runtime failure class", plus the non-terminal kinds
  (missing reference → "missing reference artifact (chantier B)", size mismatch,
  below-threshold similarity).
- **341 rows full preservation**: every residual row of the plan §1 closure
  inventory (199 + 60 + 4 + 2 + 2 + 2 + 62 + 10) is enumerated per-row in
  `residual-inventory.csv`; the inventory is **not support status** (M86
  acceptance: "Inventory status is not support status") — it is the auditable
  burn-down baseline that Tasks 3-8 close row-by-row.

No Gradle build was run for this task; verification is script-level
(`--check` counts) against the committed snapshot and committed sources.

## Task 1 — colr-v0 scenes oracle fix (harness only)

**Task 1 result: `RenderGpuRendererSceneOffscreenMainTest > real COLRv0 scene
uses one prepared encoder submit and matches its CPU reference` now passes
byte-exact (`pixelExact=4096/4096`, `maxChannelDelta=0`).**

### 1.1 Root cause (before)

FP-12 §4.3 documented the latent divergence: the scene's CPU oracle
(`PreparedColorGlyphSceneFrame.composeCpuReference`) filled an opaque background
(`alpha=1`) while the product color-glyph lane clears transparent
(`GPULoadStorePlan("clear")`), so the byte-exact pin (`reference.png` vs
`render.png`, `RenderGpuRendererSceneOffscreenMainTest.kt:79-82`) failed with
`pixelExact=38/4096` on llvmpipe.

### 1.2 Before state (red run)

Command (headless):

```bash
DISPLAY=:99 ./gradlew -F off :gpu-renderer-scenes:test \
  --tests "org.graphiks.kanvas.gpu.renderer.scenes.offscreen.RenderGpuRendererSceneOffscreenMainTest" \
  --no-parallel --console=plain
```

Result: `28 tests completed, 1 failed`; the colr-v0 test failed at
`RenderGpuRendererSceneOffscreenMainTest.kt:79` with
`org.opentest4j.AssertionFailedError` (reference.png vs render.png byte list
mismatch; IDAT lengths 708 vs 650 bytes). Parity report (harness artifact):

```
COLRv0 color glyph parity report
fixture=/fonts/skia/colr.ttf
baseGlyph=2
layerGlyphs=7,8
reference=cpu-source-over + mirrored-llvmpipe-srgb-store
matchingPixels=38/4096
pixelExact=false
targetSize=64x64
uniformBytes=784
```

### 1.3 Fix (oracle made correct, no test weakened)

`gpu-renderer-scenes/.../offscreen/PreparedColorGlyphSceneFrame.kt`
(`composeCpuReference` + helpers; harness-only, no production renderer code):

1. **Transparent clear**: removed the opaque `rgba[pixel*4+3] = 1f` background
   fill; the zero-initialized `FloatArray` now matches the lane's transparent
   clear (all-zero RGBA). Glyph layers source-over onto it unchanged.
2. **Lane-exact output encoding**: the initial clear fix revealed a second,
   previously unexercised oracle divergence: the glyph chroma also differed
   (`maxChannelDelta=73` on 535 pixels after the clear fix) because the lane
   stores to `RGBA8UnormSrgb` (linear premul composite + hardware sRGB encode at
   store — pinned byte-exact by `GPUColorGlyphPreparedFrameSmokeTest.kt:126-138`),
   while the oracle wrote raw linear bytes with half-away rounding. The oracle
   now replicates the lane's exact store conversion: llvmpipe's
   `lp_build_linear_to_srgb` rational-polynomial approximation
   (`a*x^0.375 + b*x^0.5 + c`, `a=0.675*1.0622*255`, `b=0.325*1.0622*255`,
   `c=-0.0620*255`, threshold `0.0031308`) with the AMD Zen-3 `rsqrtps`
   approximation (4096-entry even/odd mantissa tables, extracted empirically
   from this host's hardware) and round-to-nearest-even quantization
   (`cvtps2dq`), matching Mesa 26.0.3's `lp_build_float_to_srgb_packed` /
   `lp_build_linear_to_srgb` path (verified against Mesa 26.0.3 source).

Verification of the encode model before implementation: a temporary probe
rendered 96 exact f32 linear inputs through the real lane (16 layers × 6 frames
via `GPUColorGlyphPreparedTestSupport`); a Python simulation of the above
algorithm matched all 96 outputs bit-for-bit. The probe test was removed after
use.

### 1.4 After state (green run)

Same command as §1.2: `BUILD SUCCESSFUL`, all 28 tests pass, including:

```
RenderGpuRendererSceneOffscreenMainTest > real COLRv0 scene uses one prepared encoder submit and matches its CPU reference() PASSED
```

Parity report (harness artifact):

```
matchingPixels=4096/4096
pixelExact=true
```

`colorTextRun:pixelExact=4096/4096` in run.json; independent decode of
`reference.png` vs `render.png` (`64x64`, RGBA8): `mismatched=0
maxChannelDelta=0`, `render(0,0)=(0,0,0,0)` (transparent background).

### 1.5 Full module regression

```bash
DISPLAY=:99 ./gradlew -F off :gpu-renderer-scenes:test --no-parallel --console=plain
```

Result: `BUILD SUCCESSFUL` — 274 tests, 0 failures (whole module, includes the
previous 28-test targeted class). No threshold or assertion was changed
anywhere; the pin closed by making the oracle correct.

### 1.6 Harness-only statement

- No production renderer code was touched (only
  `gpu-renderer-scenes/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/offscreen/PreparedColorGlyphSceneFrame.kt`,
  the offscreen test-harness scene frame).
- No test assertion or similarity threshold was weakened; the byte-exact pin is
  unchanged.
- The GPU-lane behavior is unchanged and remains pinned byte-exact by the
  existing `GPUColorGlyphPreparedFrameSmokeTest`.

### 1.7 Commit

- SHA: `cef92685c` — `fix(gpu-renderer-scenes): colr-v0 oracle clears transparent and mirrors lane srgb store (FP-13 task 1)`
- Files: `PreparedColorGlyphSceneFrame.kt` (oracle) + this evidence doc.

### 1.8 Notes and non-claims

- The sRGB-encode emulation embeds this host's (AMD EPYC Zen 3) `rsqrtps`
  approximation tables; byte-exactness is defined against the llvmpipe lane on
  this machine, matching the plan's llvmpipe baseline. On other CPUs the tables
  could differ by ±1 on boundary pixels; the harness oracle is not claimed to
  match non-llvmpipe adapters.
- The oracle's color handling treats the font palette as the lane does (palette
  values carried as linear premul, encoded at store); the fix does not change
  the lane's semantics, only the oracle's fidelity to them.

## Task 2 — PipelineTypesTest hygiene + wgsl4k ticket (test-hygiene only)

### 2.1 Defect (before)

`kanvas/src/test/kotlin/org/graphiks/kanvas/pipeline/PipelineTypesTest.kt:13`:

```kotlin
assertTrue(RuntimeEffect.compile("fn main() {}").isFailure)
```

`fn main() {}` is **valid** WGSL. When the runtime-effect wgsl4k wiring hook
(`RuntimeEffectWgsl4kWiring.install()`, `kanvas/.../RuntimeEffectWgsl4kWiring.kt`)
is installed in the same test JVM before this class runs — e.g. by
`RuntimeEffectCompileTest` or `GPUClipCoverageSurfaceTest` in the same fork —
`parseWgslResult`/`Lowerer` accept the empty-main module and `compile` returns
`success`, so `isFailure` is false and the test fails. The outcome therefore
depended on test-class execution order in the shared JVM fork (documented as a
"wgsl4k hook-order JVM flake" in fp-06/fp-07 evidence; passed in isolation,
failed in some full-suite runs).

Observed red-state evidence (this machine, hook installed via throwaway probe
test, removed before commit):

```
PROBE sample=<fn main() {}> isSuccess=true isFailure=false err=none
```

Full-module red run before the fix: `PipelineTypesTest` happened to run before
the hook-installing classes in the JUnit hash-based order, so the class passed
there; the order dependence is structural (probe above proves the failure mode
when the hook precedes it).

### 2.2 Fix (after)

Sample replaced with unambiguously parse-invalid WGSL — an unterminated brace:

```kotlin
assertTrue(RuntimeEffect.compile("fn main() {").isFailure)
```

`fn main() {` can never parse as a WGSL program (missing closing brace), so the
assertion holds whether or not the wgsl4k hook is installed. Test name
(`RuntimeEffect compile fails validation`) and all other assertions unchanged.

Probe evidence (hook installed, same throwaway probe):

```
PROBE sample=<fn main() {> isSuccess=false isFailure=true err=IllegalArgumentException: WGSL compilation failed: could not parse or reflect the source
```

### 2.3 Verification

- Targeted class, isolated: PASSED (run 1), PASSED (run 2), PASSED (run 3) —
  three consecutive `--rerun-tasks` runs of `--tests "…PipelineTypesTest"`, all
  green (fork-order independence check).
- Package run `--tests "org.graphiks.kanvas.pipeline.*"` (includes
  `RuntimeEffectCompileTest`, which installs the hook in the same fork):
  PASSED.
- Full module `./gradlew -F off :kanvas:test --no-parallel --console=plain
  --rerun-tasks`: 3,234 tests, **1 failure** —
  `GPUPreparedSurfaceImagePixelTest` "UNORM 1-LSB on llvmpipe (documented
  FP-03, unchanged)" — the documented pre-existing baseline from FP-12 §3, not
  touched. A second, unrelated `GPUMaskBlurSurfaceTest` session-close flake
  observed in the pre-fix full run (passes 19/19 in isolation with
  `--rerun-tasks`) did not reproduce in the post-fix run.

### 2.4 wgsl4k ticket

Opened per AGENTS.md (wgsl4k behavior surprises go to a wgsl4k ticket with
minimized evidence — no hidden workaround in this repo):

- URL: https://github.com/ygdrasil-io/wgsl4k/issues/15
  (`parseWgslResult`/`Lowerer` accept `fn main() {}` as a compilable module,
  making a consumer test order-dependent)
- wgsl4k version used by Kanvas: `1.0.0-20260629.231604-1`
  (`org.graphiks:wgsl-core-jvm`, `org.graphiks:wgsl-parser-jvm`,
  `gradle/libs.versions.toml:12`)
- Ticket body: full minimized evidence at
  `reports/upstream-rebaseline/graphite-dawn-frame-plan/fp13-m86-wave/wgsl4k-ticket-body.md`
  (committed copy); ticket filed via `gh issue create --repo ygdrasil-io/wgsl4k`.

### 2.5 Harness-only statement

- **No wgsl4k workaround** was added to Kanvas code; the wgsl4k acceptance gap
  is tracked upstream in ygdrasil-io/wgsl4k#15.
- **No production code** was touched — the only change is the WGSL sample
  string in `PipelineTypesTest.kt:13` (+ this evidence doc).
- **No similarity threshold or assertion semantics changed**; the assertion
  remains `assertTrue(RuntimeEffect.compile(...).isFailure)`.

## Task 3 — analytic-shape dst-read formula on the prepared lane

Task 3 wires the closed `GPUBlendFormulaLibrary` formula + shader-dst-read
pipeline onto the core-primitive run materializer for analytic shapes
(rect/rrect), closing the analytic-shape dst-read rows (plan §1 item 1). The
rows closed are the 2 `dst-read-formula` "mapped-route" clip rows and the 30
`frame-global-pipeline` DrawRRect dst-read fallout rows (fp-11 §5). This task
applies a **renderer fix** (a new closed analytic-shape dst-read program).

### 3.1 Before state (RED run)

Re-pointed the Task-3-owned rows in the blend matrix
(`GPUAllApiBlendSurfaceTest.kt:610` — `recordsDestinationRead()` → Prepared)
and ran the targeted class:

```bash
DISPLAY=:99 ./gradlew -F off :kanvas:test \
  --tests "org.graphiks.kanvas.surface.gpu.GPUAllApiBlendSurfaceTest" \
  --no-parallel --console=plain --rerun-tasks
```

Result: **1864 tests completed, 30 failed** — every re-pointed row refused with
the exact refusal code, captured per-row in the JUnit XML. All 30 failures are
`unsupported.native-core-primitive.frame-global-pipeline` (emitted at
`GPUWgpu4kCorePrimitiveRenderRunMaterializer.kt:151`). A debug probe (removed
before commit) confirmed the structural keys: all 15 modes (14 artistic +
`PLUS` = `plus_exact@v1`) are `shader=AnalyticShape,
blend=ShaderWithDestination(mode=X, formulaId=X@v1, sourceCoverage=None)`.

The 2 clip-suite rows were pinned Terminal before the fix and rendered their
refusal through the single-key materializer gate
(`GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt:917`,
`unsupported.native-core-primitive.dst-read-formula`).

### 3.2 Root cause

`mapCorePrimitiveStructuralKeyToWgpu4kPipelineIdentity` (in
`GPUWgpu4kCorePrimitivePipelineDescriptor.kt`) explicitly refused
`Shader.AnalyticShape` + `Blend.ShaderWithDestination` keys — the destination-
read formula program existed only on the direct-geometry (uniform32) lane. The
analytic-shape (uniform80) lane therefore had no closed dst-read pipeline, so
the frame-global run materializer refused the 30 DrawRRect rows and the
single-key materializer refused the 2 AA clip rows (the latter are scalar-
coverage `ScalarCoverageInShader` keys whose coverage must be computed
in-shader).

### 3.3 Pipeline wiring (production code)

- `GPUCorePrimitiveNativeShader.kt` — new
  `buildCorePrimitiveAnalyticShapeDstReadNativeShader(modeLabel)` +
  `corePrimitiveAnalyticShapeDstReadNativeWgsl(formulaWgsl)`: the analytic-
  shape uniform80 block plus the destination snapshot texture/sampler
  (bindings 1/2), the analytic coverage functions, and the scalar-coverage
  result `return dst + coverage * (blended - dst);` (Graphite dst-read recipe
  with shader-applied coverage). Hard coverage (`anti_alias == 0`) reduces the
  factor to 0/1, so one exact program serves both the full-coverage (30
  DrawRRect) and scalar-coverage (2 clip) rows. New shader/binding-layout
  identity constants
  `core-primitive-analytic-shape-dst-read-device-geometry-wgsl-v1` /
  `dynamic-uniform80-analytic-shape-dst-read-v1`.
- `GPUWgpu4kCorePrimitivePipelineDescriptor.kt` — new
  `GPUWgpu4kCorePrimitivePipelineProgram.AnalyticShapeDstRead`; `nativeProgramOrNull`
  maps `Shader.AnalyticShape` + `ShaderWithDestination` (formula present,
  coverage in {None, ScalarCoverageInShader}) to the new program and still
  refuses LCD coverage; `nativeBlendProgramOrNull` routes the new program to
  `analyticShapeDstReadBlendProgramOrNull()` (the `DstRead*` blend program);
  `mapCorePrimitiveStructuralKeyToWgpu4kPipelineIdentity` and
  `corePrimitiveNativeComponentIdentityOrNull()` select the analytic-shape
  dst-read component; the render-pipeline descriptor uses the analytic-shape
  vertex/fragment entry points and the exact-Src fixed-function state.
- `GPUWgpu4kCorePrimitiveSessionCache.kt` — new
  `corePrimitiveAnalyticShapeDstReadComponentIdentity(modeLabel)`;
  `isCorePrimitiveDstRead()` recognizes both dst-read component families;
  `dstReadModeLabelOrNull()` resolves both prefixes;
  `uniformBindingSizeBytes()` returns 80 bytes for the analytic-shape dst-read
  layout; `createFirstPipeline()` selects the analytic-shape dst-read shader
  builder; `CORE_PRIMITIVE_SESSION_PIPELINE_CACHE_MAX_ENTRIES` raised 64 → 128
  (the closed 30-program + 32-dst-read-component universe now needs more than
  64 live pipelines in one retained session).
- `GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt` — the single-key
  `exactProgram` check for the uniform80 layout accepts
  `isAnalyticShapeProgram()` (SrcOver or DstRead).

No change was made to the multi-key authority (`GPUCorePrimitiveNativeRoute.kt:415`),
the `analytic-shape-multi-key` gate (`:1453`), or the
`preparedRouteResidualRefusalCodes` set; Task 4's multi-key rows still refuse
with their stable codes (verified by the green guard run).

### 3.4 After state (GREEN run)

```bash
DISPLAY=:99 ./gradlew -F off :kanvas:test \
  --tests "org.graphiks.kanvas.surface.gpu.GPUAllApiBlendSurfaceTest" \
  --no-parallel --console=plain --rerun-tasks
```

Result: **1864/1864 green** — the 30 re-pointed DrawRRect rows render Prepared
and are compared per-pixel against the pure-Kotlin CPU oracle
(`assertPixelsNear`, tolerance 2), exact.

```bash
DISPLAY=:99 ./gradlew -F off :kanvas:test \
  --tests "org.graphiks.kanvas.surface.gpu.GPUClipCoverageSurfaceTest" \
  --no-parallel --console=plain --rerun-tasks
```

Result: **41/41 green** — the 2 re-pointed clip rows (DARKEN / COLOR_DODGE
rects) render Prepared and assert RED (`DARKEN(src, transparent) = src`,
`COLOR_DODGE(src, transparent) = src`).

### 3.5 Re-pointed matrix pins

- `GPUAllApiBlendSurfaceTest.kt:610-613` — `mode.recordsDestinationRead()` on
  `DrawRRect` re-pointed from
  `Terminal(unsupported.native-core-primitive.frame-global-pipeline)` to `null`
  (Prepared, pixel-oracle proven). The now-unused
  `PREPARED_FRAME_GLOBAL_PIPELINE_REFUSAL` constant was removed.
- `GPUClipCoverageSurfaceTest.kt:399-410` (`no clip destination read composes
  against a transparent snapshot`) and `:413-440` (`clear and color dodge use
  their mapped clip composition routes`) re-pointed from
  `Terminal(unsupported.native-core-primitive.dst-read-formula)` to Prepared
  with `assertRgbaNear(..., Color.RED)`; the now-unused
  `PREPARED_DST_READ_FORMULA_REFUSAL` constant was removed.
- `GPUWgpu4kCorePrimitivePipelineDescriptorTest.kt` — 3 ×
  `entries.size` assertion updated 29 → 30; new test
  `analytic shape dst read shading keys map to the analytic dst read formula
  program` covers the program, component identity, uniform80 bind-group layout,
  exact-Src fixed-function state, scalar-coverage mapping, and LCD refusal.

### 3.6 Rows that stayed refused (unchanged, re-classified with evidence)

No row stayed refused as a forced re-classification. The rows that remain
refused are owned by other tasks and were not re-pointed:

- 2 DrawRRect DST rows and 30 DrawPoint rows on
  `invalid.preflight.core_primitive_direct_geometry_resources` — Task 6
  (split-lane geometry-slab authority); verified unaffected (blend matrix
  pins `:607-609` and `:585-599` unchanged).
- 2 `analytic-shape-multi-key` rows (`GPUClipCoverageSurfaceTest.kt:329,352`)
  — Task 4; still refuse with their stable code (green guard run).
- The multi-key `dst-read-formula` and `multi-key-component` defenses
  (`GPUCorePrimitiveNativeRoute.kt:415`, `GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt:1453`)
  are untouched; `dst-read-formula` stays in the residual set because the
  multi-key scalar-coverage path still emits it.

### 3.7 Full-run summaries

```bash
DISPLAY=:99 ./gradlew -F off :gpu-renderer:test --no-parallel --console=plain --rerun-tasks
```

Result: **3301 tests, 1 failure** — the documented pre-existing
`GPURendererPackageBoundaryTest` baseline (exactly 20 package cycle violations,
0 rule violations, unchanged; fp-11 §0.1). No cycle was added or removed by
this task.

```bash
DISPLAY=:99 ./gradlew -F off :kanvas:test --no-parallel --console=plain --rerun-tasks
```

Result: **3234 tests, 1 failure** — the documented pre-existing
`GPUPreparedSurfaceImagePixelTest` UNORM 1-LSB llvmpipe baseline (FP-03,
unchanged).

Guards (all green, verified in the full runs):
`GPUPreparedSurfaceLegacyAbsenceTest` 1/1,
`GPUPreparedSurfaceProductRouterTest` 15/15,
`GPUPreparedCompositeCaptureSemanticTest` 19/19,
`GPUPreparedCompositeFrameRouteIntegrationTest` 8/8,
`GPUPreparedSurfaceLifetimeStressTest` 6/6,
`GPUWgpu4kCorePrimitiveClipStencilAaFrameSmokeTest` 1/1.

### 3.8 Notes and non-claims

- The analytic-shape dst-read shader applies coverage with the scalar-coverage
  lerp (`dst + coverage * (blended - dst)`); for the non-AA DrawRRect rows the
  analytic coverage factor is 0/1, so the same program reproduces the CPU
  oracle's full-coverage blend exactly. This is not a new AA oracle — it reuses
  the existing analytic coverage functions unchanged.
- CPU-oracle evidence is not Skia-comparable fidelity (M86 statement, Task 0).
- No global similarity threshold or assertion was weakened; the only matrix
  changes are re-points from Terminal to Prepared.
