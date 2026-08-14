# FP-13 Close Bounded Native-Rendering Gaps — Evidence

Status: **in progress** (Task 1 complete: `colr-v0-color-glyph` scene CPU-oracle
fix closes the byte-exact pin; Task 2 complete: `PipelineTypesTest` hygiene +
wgsl4k ticket; Task 3 complete: analytic-shape dst-read formula; Task 4 complete:
analytic-shape multi-key dst-read; Task 5 complete: complex-clip blur; Task 6
complete: analytic-clip uniform64/160 split; further tasks append their own
sections).

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

## Task 4 — analytic-shape multi-key dst-read

Task 4 closes the 2 `analytic-shape-multi-key` rows (plan §1 item 2) by
routing the geometric-interpolation fixed-function blends (CLEAR/SRC/DST_IN)
on the AA analytic-shape lane through the Task 3 shader dst-read formula. This
task applies a **renderer fix** (blend planning + analysis re-point onto the
closed AnalyticShapeDstRead program).

### 4.1 Row identification

The two rows are the clip-suite CLEAR/SRC/DST_IN AA rects over a WHITE
background (fp-13 residual CSV item 2, ownerTask 4):

- `GPUClipCoverageSurfaceTest.kt` `AA geometry coverage blends after clear src
  and dst in` (UNCLIPPED).
- `GPUClipCoverageSurfaceTest.kt` `AA scissor preserves destination outside
  clear src and dst in` (SCISSOR).

Both previously pinned `Terminal(unsupported.native-core-primitive.analytic-
shape-multi-key)`. The plan's "blend-matrix :640 multi-key subset" label is a
mismatch: the actual multi-key rows live in the clip suite (the blend matrix's
`shapePaint` uses `antiAlias = false`, so no analytic-shape multi-key seal is
ever formed there). The plan's "dst-read" label is also loose: the rows are the
fixed-function CLEAR/SRC/DST_IN blends whose AA semantics the coverage-
modulating shader cannot express.

### 4.2 Before state (RED run)

Re-pointed the 2 rows to the pre-FP-09 pixel pins (rendered) and ran:

```bash
DISPLAY=:99 ./gradlew -F off :kanvas:test \
  --tests "org.graphiks.kanvas.surface.gpu.GPUClipCoverageSurfaceTest" \
  --no-parallel --console=plain --rerun-tasks
```

Result: **2 tests failed (6 sub-cases, 3 modes × 2 contexts)**, every one
`GPUPreparedSurfaceTerminalException:
unsupported.native-core-primitive.analytic-shape-multi-key: Multi-key
analytic-shape CorePrimitive passes remain on the legacy route until their AA
coverage semantics are verified on the prepared lane.` (emitted at
`GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt:1453`).

### 4.3 Root cause

The analysis (`GPUFirstRoutePlanner.plan(FillRect/FillRRect)`) computed the
packet's blend plan with full-or-scissor coverage (`canonicalPlan` default),
so an AA CLEAR/SRC/DST_IN rect carried the full-coverage fixed-function states
`zero_zero`/`one_zero`/`zero_sa`. The analytic-shape shader emits
`premul_rgba * coverage`, which reproduces SRC_OVER/DST_OVER/DST_OUT/SRC_ATOP/
XOR/SCREEN (modulate-compatible) but cannot express the geometric AA
interpolation `dst + coverage * (blended - dst)` for CLEAR/SRC/SRC_IN/DST_IN/
SRC_OUT/DST_ATOP/MODULATE. A two-draw AA frame (WHITE SRC_OVER + RED
CLEAR/SRC/DST_IN) therefore sealed a fixed-function multi-key analytic-shape
pass, which the `:1453` gate refused because its AA semantics were unverified.

### 4.4 Fix (production code)

- `GPUBlendPlanning.kt` — new `GPUBlendPlan.forCorePrimitiveAnalyticShapeCoverage()`
  (+ `If(scalarCoverage)`) projects a full-coverage plan onto the
  analytic-shape lane: the geometric modes (CLEAR/SRC/SRC_IN/DST_IN/SRC_OUT/
  DST_ATOP/MODULATE) route through `ShaderBlendWithDstRead(mode,
  "$modeLabel@v1", ScalarCoverageInShader)`; every other fixed state is left
  untouched (the analytic shader supplies the modulation).
- `GPUOpMapper.kt` (`mapCoreOperation`) and `AnalysisContracts.kt`
  (`plan(FillRect)`/`plan(FillRRect)`) apply the projection only when the shape
  consumes scalar coverage (AA), so the packet's blend plan and the mapper's
  `dependsOnDestination` agree; non-AA shapes and the 30 non-AA DrawRRect
  dst-read rows are unchanged.
- `GPUWgpu4kCorePrimitivePipelineDescriptor.kt` — new
  `DstReadClear/Src/SrcIn/DstIn/SrcOut/DstAtop` blend programs (exact-Src
  fixed-function state, formula per mode) so those modes map onto the closed
  `AnalyticShapeDstRead` program; `fixedNativeBlendProgramOrNull` now excludes
  `isDstRead()` candidates so the new exact-Src states cannot collide with the
  `PremulSrc` fixed-function program.

The routing now makes the dst-read draw split into an ordered destination
pass + snapshot + consumer pass (the existing dst-copy shape), so the frame no
longer seals a multi-key pass. The `:415` (`dst-read-formula`) and `:1453`
(`analytic-shape-multi-key`) gates are **unchanged**: the dst-read rows no
longer reach them, and the fixed-function multi-key seal (e.g. SRC_OVER +
DST_OVER AA) is not closed by this task and stays refused.

### 4.5 After state (GREEN run)

```bash
DISPLAY=:99 ./gradlew -F off :kanvas:test \
  --tests "org.graphiks.kanvas.surface.gpu.GPUClipCoverageSurfaceTest" \
  --no-parallel --console=plain --rerun-tasks
```

Result: **41/41 green** — the 2 rows render Prepared and assert the pre-FP-09
reference pixels at the half-coverage edge (3,8): CLEAR `(128,188,188,188)`,
SRC `(255,255,188,188)`, DST_IN `WHITE`; the scissor row asserts `WHITE`
outside the scissor. The single-key `clear and color dodge` clip row stays
green (CLEAR now renders through the same dst-read formula).

### 4.6 Re-pointed matrix pins

- `GPUClipCoverageSurfaceTest.kt:313-339` and `:341-364` — re-pointed from
  `assertTerminal(PREPARED_ANALYTIC_SHAPE_MULTI_KEY_REFUSAL, surface::render)`
  to `render()` + `assertRgbaNear`.
- `GPUPreparedSurfaceFrameBuilderTest.kt:726-752` (`two analytic rects with
  mixed blend modes route the clear consumer through the dst read formula`)
  and `:782-801` (`scalar src rect routes through the dst read formula with a
  destination snapshot`) — re-pointed from the fixed-function multi-key /
  no-snapshot assertions to the dst-read + `GPUTask.DestinationSnapshots`
  assertions.

No router-matrix change was required: the `analytic-shape-multi-key` code can
still fire for the fixed-function multi-key analytic-shape family (two distinct
modulate-compatible AA keys), which this task does not close; the code stays in
`GPUPreparedSurfaceProductRouterTest.kt:473` and
`preparedRouteResidualRefusalCodes` (`GPUPreparedSurfaceFrameExecution.kt:1090`).
The fix round re-added a regression pin for that family (see §4.9).

### 4.7 Full-run summaries

```bash
DISPLAY=:99 ./gradlew -F off :gpu-renderer:test --no-parallel --console=plain --rerun-tasks
```

Result: **3301 tests, 1 failure** — the documented pre-existing
`GPURendererPackageBoundaryTest` baseline (20 package cycle violations / 0 rule
violations, unchanged).

```bash
DISPLAY=:99 ./gradlew -F off :kanvas:test --no-parallel --console=plain --rerun-tasks
```

Result: **3234 tests, 1 failure** — the documented pre-existing
`GPUPreparedSurfaceImagePixelTest` UNORM 1-LSB llvmpipe baseline (unchanged).

Guards green: `GPUPreparedSurfaceProductRouterTest` 15/15,
`GPUAllApiBlendSurfaceTest` 1864/1864, `GPUClipCoverageSurfaceTest` 41/41,
`GPUPreparedSurfaceLegacyAbsenceTest` 1/1,
`GPUPreparedCompositeCaptureSemanticTest` 19/19,
`GPUPreparedCompositeFrameRouteIntegrationTest` 8/8,
`GPUPreparedSurfaceLifetimeStressTest` 6/6,
`GPUWgpu4kCorePrimitiveClipStencilAaFrameSmokeTest` 1/1.

### 4.8 Notes and non-claims

- The half-coverage edge pin reuses the pre-FP-09 reference values (sRGB-encoded
  `linearToSrgb(0.5)=188` at alpha 128), so the dst-read formula is verified
  against the geometric-interpolation oracle, not a weakened threshold.
- CPU-oracle evidence is not Skia-comparable fidelity (M86 statement, Task 0).
- One `GPUWgpu4kSolidRectFrameSmokeTest` (10 s native-completion timeout) flaked
  in one full `:gpu-renderer:test` run and passed in isolation and in the
  re-run; it is the documented environmental timeout family, not a task change.

### 4.9 Fix round 1 (review: coverage of the refusal path + the four extra modes)

Review finding 1 — the `analytic-shape-multi-key` path lost all test coverage.
Re-added `PREPARED_ANALYTIC_SHAPE_MULTI_KEY_REFUSAL` and a regression pin:
`GPUClipCoverageSurfaceTest.kt:382-403` `two aa rects with fixed function
blends stay terminal on the multi key analytic shape refusal` — WHITE SRC_OVER +
RED DST_OVER (both modulate-compatible fixed AA) seal a multi-key analytic-shape
pass and `assertTerminal(unsupported.native-core-primitive.analytic-shape-multi-key)`
at the `:1453` gate.

Review finding 2 — the projection covered seven modes but only CLEAR/SRC/DST_IN
were pixel-pinned. Extended the `AA geometry coverage blends after clear src and
dst in` pin to the remaining AA modes with the same half-coverage edge oracle:

| mode | oracle at (3,8) | latent pre-fix (`src*coverage`) value |
| --- | --- | --- |
| SRC_IN | `(255,255,188,188)` (== SRC over opaque dst) | `(188,0,0,128)` |
| SRC_OUT | `(128,188,188,188)` (== CLEAR over opaque dst) | `(0,0,0,0)` |
| DST_ATOP | `WHITE` (opaque src preserves dst) | `(188,188,188,128)` |
| MODULATE | `(255,255,188,188)` (src*dst = RED over WHITE) | `(188,0,0,128)` |

All four verified cleanly on llvmpipe at the established `assertRgbaNear`
tolerance 8; the pre-fix fixed-function `src*coverage` AA values differ from the
oracle, so the pins prove the dst-read formula (not the old modulate shader).
Stale comment fixed: `GPUClipCoverageSurfaceTest.kt:467-469` (`clear and color
dodge use their mapped clip composition routes`) — CLEAR now rides the
analytic-shape dst-read formula (FP-13 Task 4), matching COLOR_DODGE.

Fix-round runs: `GPUClipCoverageSurfaceTest` 42/42, `GPUAllApiBlendSurfaceTest`
1864/1864, `GPUPreparedSurfaceProductRouterTest` 15/15; `:gpu-renderer:test`
3301 (1 documented package-boundary baseline); `:kanvas:test` 3235 (1 documented
image-pixel UNORM baseline).

## Task 5 — complex-clip blur (mask-blur composite under multi-rect analytic clip)

Task 5 closes the 2 clip-suite pins on
`invalid.preflight.core_primitive_clip_producer_authority` (plan §1 item 3) by
extending the FP-11 Task 7 analytic-clip ABI to the clip producer authority: a
rect-decomposable complex clip (AA rect INTERSECT + axis-aligned orthogonal
polygon DIFFERENCE) lowers to bounded analytic multi-rect coverage and the mask
blur composite renders prepared. Coverage-mask and stacked clips stay terminal.
This task applies a **renderer fix** (clip lowering + composite shader + uniform
packing).

### 5.1 Row identification

The two rows (fp-13 residual CSV item 3, ownerTask 5):

- `GPUClipCoverageSurfaceTest.kt` `complex clip blur is terminal at the clip
  producer preflight` (sigma = 2).
- `GPUClipCoverageSurfaceTest.kt` `complex mask blur frames are terminal at the
  clip producer preflight` (sigma = 1.5; also asserted no destination readback
  snapshot was allocated before refusal).

Both rendered the fixture `renderBlurredDifferenceClipScene`: 16×16, a
translucent `Color.fromArgb(128, 32, 64, 192)` background, an AA rect
`clipRect(1,1,15,15) INTERSECT`, an AA `clipPath` of the axis-aligned orthogonal
L-shape polygon `(5,4)(12,4)(12,8)(9,8)(9,12)(5,12)` DIFFERENCE, and a DARKEN
`drawRect(4,4,12,12)` with `MaskFilter.Blur(NORMAL, sigma)`.

### 5.2 Before state (RED run)

Both pins asserted
`Terminal(invalid.preflight.core_primitive_clip_producer_authority)`. Confirmed
green at HEAD before the change (42/42 clip suite, both pins terminal).

### 5.3 Root cause

`GPUClipCoveragePlanner.planForFrameRoute` classifies a clip with a DIFFERENCE
element as a `Mask` coverage plan (only INTERSECT rects/rrects are "simple
analytic intersection"), and `toMaskExecutionPlan` then lowers it to
`GPUClipExecutionPlan.CoverageMask`. The blur lane's composite packet inherits
that `CoverageMask` `clipExecutionPlan`, so
`validateCorePrimitiveClipProducerAuthority`
(`GPUCorePrimitivePreparedFrameTaskListBuilder.kt`) rejects the composite
Shading consumer — a coverage-mask clip whose producer topology the blur lane
never builds — with `invalid.preflight.core_primitive_clip_producer_authority`.

### 5.4 Fix (production code)

- `clips/GPUClipExecutionPlan.kt` — new `GPUClipAnalyticRectElement`
  (rect/rrect + `GPUClipMaskCombine` operation) and new
  `GPUClipExecutionPlan.AnalyticMultiRect` (1..4 ordered elements), plus
  `GPU_ANALYTIC_MULTI_RECT_MAX_ELEMENTS = 4` and the identity-builder
  serialization.
- `kanvas/.../GPUOpMapper.kt` — in `toMaskExecutionPlan`, before the
  CoverageMask fallback, `toAnalyticMultiRectOrNull()` lowers a complex clip
  whose elements are all rect/rrect or a **non-inverse single-contour
  axis-aligned orthogonal polygon** (at least one such path present) into an
  ordered rect list (scanline band decomposition), admitting `AnalyticMultiRect`
  only when the decomposed count fits the fixed block. Plain rect-vs-rect
  differences, inverse fills, curved/multi-contour paths, and over-capacity
  decompositions all return null → CoverageMask (terminal).
- `recording/GPUTopLevelMaskBlurFrameRecording.kt` — `GPUTopLevelMaskBlurCompositeClip`
  (ordered element list) replaces the single-rect clip;
  `topLevelMaskBlurCompositeClipOrNull` now admits `AnalyticCoverage` (single
  rect) and `AnalyticMultiRect`; `topLevelMaskBlurCompositeClipRefusal` admits
  both; the composite-clip WGSL `CorePrimitiveAnalyticClipBlock` extends to a
  160-byte block (`clip_count`, global `anti_alias`, four `clipN_bounds` +
  `clipN_operation` entries) with `clip_coverage` folding `factor0*1*2*3` where
  each factor is `1` (inactive), rect coverage (INTERSECT) or one-minus-coverage
  (DIFFERENCE). A single INTERSECT rect is `clip_count = 1`, byte-identical to
  the Task 7 single-rect contract.
- `execution/GPUWgpu4kMaskBlurSessionCache.kt` — the non-dst composite-clip WGSL
  mirrors the same block/fold.
- `execution/GPUWgpu4kMaskBlurFramePayloadMaterializer.kt` —
  `compositeClipUniformBytes` packs the 160-byte block; the clip uniform buffer
  size is 64 → 160 bytes.
- `passes/GPUCorePrimitivePreparedAuthority.kt` — `AnalyticMultiRect` maps to
  `Clip.Refused` (the core lane never renders it; analytic shapes still refuse
  any non-NoClip/non-ScissorOnly clip).
- `kanvas/.../GPUPreparedSurfaceFrameBuilder.kt`,
  `recording/GPUCorePrimitivePreparedFrameTaskListBuilder.kt` — exhaustive
  `when` branches for the new variant.

### 5.5 After state (GREEN run)

```bash
DISPLAY=:99 ./gradlew -F off :kanvas:test \
  --tests "org.graphiks.kanvas.surface.gpu.GPUClipCoverageSurfaceTest" \
  --tests "org.graphiks.kanvas.surface.gpu.GPUMaskBlurSurfaceTest" \
  --no-parallel --console=plain --rerun-tasks
```

Result: **63/63 green** — the 2 clip-suite pins render prepared and assert the
CPU-oracle reference (`TopLevelMaskBlurPixelOracle.ComplexClip`) exactly within
the lane's tolerance; the new `mask blur composite under a multi rect analytic
clip renders prepared` case in `GPUMaskBlurSurfaceTest` is oracle-exact; the
coverage-clip terminal pin (`unsupported.native-mask-blur.clip`) and the single
analytic rect cases stay green unchanged.

### 5.6 Re-pointed pins + new oracle mode

- `GPUClipCoverageSurfaceTest.kt:165` → `complex clip blur renders prepared
  under a multi rect analytic clip` (sigma = 2, oracle-exact); the
  `PREPARED_CLIP_PRODUCER_AUTHORITY_REFUSAL` constant is removed (no longer
  reachable in the clip suite).
- `GPUClipCoverageSurfaceTest.kt:175` → `complex mask blur frames render
  prepared with the multi rect analytic clip` (sigma = 1.5). The readback
  assertion is re-expressed for the rendered path: `destinationReadbackSnapshots`
  stays unchanged (the blur lane never allocates a legacy CPU readback) while
  `destinationCopies` is asserted > before (the lane legitimately allocates its
  native destination copy for the DARKEN composite), and the pixels are
  oracle-exact. Never weakened.
- `GPUMaskBlurSurfaceTest.kt` new case `mask blur composite under a multi rect
  analytic clip renders prepared` (SRC_OVER over transparent, L-shape DIFFERENCE
  decomposed to `[10,8,24,16]` + `[10,16,18,24]`).
- `TopLevelMaskBlurPixelOracle.kt` — new `Clip` sealed interface with the
  existing `RectClip` and a new `ComplexClip` (`ComplexClipElement` +
  `ComplexClipOperation`); `compositePass` folds ordered INTERSECT/DIFFERENCE
  rect coverage with the same two-sided SDF ramp.

### 5.7 What STAYS terminal

- Coverage-mask/stacked clips: `GPUMaskBlurSurfaceTest.kt` `mask blur composites
  under coverage clips are terminal` (`unsupported.native-mask-blur.clip`) and
  the single analytic rect cases stay exactly as they are.
- `core_primitive_clip_producer_authority` remains pinned for the stencil and
  coverage-mask producer/consumer corruption scenarios in
  `GPUFramePreflighterTest.kt` (107/107 green).
- Rect-vs-rect DIFFERENCE clips (no path) still lower to `CoverageMask`; the
  core-lane two-rect DIFFERENCE pins (`complex clip accepts every standard blend
  mode`, `fixed alpha mask composition…`, `coverage alpha mask preserves
  difference holes…`, `alpha mask retains geometric coverage…`) stay
  `unsupported.recording.core_primitive_analytic_shape_clip`.

### 5.8 Full-run summaries

```bash
DISPLAY=:99 ./gradlew -F off :gpu-renderer:test --no-parallel --console=plain --rerun-tasks
```

Result: **3301 tests, 1 failure** — the documented pre-existing
`GPURendererPackageBoundaryTest` baseline (20 package cycle violations / 0 rule
violations, unchanged; no new cycle or semantic-reference violation introduced).

```bash
DISPLAY=:99 ./gradlew -F off :kanvas:test --no-parallel --console=plain --rerun-tasks
```

Result: **3236 tests, 1 failure** — the documented pre-existing
`GPUPreparedSurfaceImagePixelTest` UNORM 1-LSB llvmpipe baseline (unchanged).

Guards green: `GPUPreparedSurfaceProductRouterTest` 15/15,
`GPUAllApiBlendSurfaceTest` 1864/1864, `GPUPathClipRegressionTest` 4/4,
`GPUPreparedSurfaceLegacyAbsenceTest` 1/1,
`GPUPreparedCompositeCaptureSemanticTest` 19/19,
`GPUPreparedCompositeFrameRouteIntegrationTest` 8/8,
`GPUPreparedSurfaceLifetimeStressTest` 6/6,
`GPUWgpu4kCorePrimitiveClipStencilAaFrameSmokeTest` 1/1,
`GPUFramePreflighterTest` 107/107,
`GPUCorePrimitiveSemanticHotPathSourceGuardTest` 2/2,
`GPUCorePrimitiveNativeShaderTest` 13/13.

### 5.9 Notes and non-claims

- CPU-oracle evidence is not Skia-comparable fidelity (M86 statement, Task 0).
- The oracle destination for the translucent background is modeled as literal
  unpremultiplied sRGB bytes (the solid-fill lane stores the color as-is; the
  composite decodes sRGB on read), which diverges from the opaque-only
  `fillRect` helper's premultiplied round-trip — the fixture's destination is
  built literally to match the observed GPU store.
- The L-shape DIFFERENCE polygon decomposes to exactly two band rects; a
  rect-decomposable orthogonal polygon exceeding the four-rect block stays on
  the coverage-mask route (terminal).

### 5.10 Fix round 1 (review: INTERSECT over-admission + fill-rule + cross-lane note)

Review finding 1 (Critical) — `toAnalyticMultiRectOrNull` over-admitted INTERSECT
orthogonal polygons: the shader fold `factor0 * factor1` multiplies per-rect
coverage, which is exact for a DIFFERENCE union (`(1-r0)*(1-r1)`) but collapses a
multi-band INTERSECT polygon to zero (disjoint rect coverages multiply to an
empty clip). `decomposeOrthogonalPolygon` now rejects non-`Difference` path
elements (`if (element.operation != GPUClipCoverageOperation.Difference) return
null`) alongside the existing inverse/contour/bounds guards
(`GPUOpMapper.kt:1461`). Regression pin added:
`GPUClipCoverageSurfaceTest.kt` `intersect orthogonal polygon clip stays terminal
at the clip producer preflight` — an INTERSECT L-shape path + blur stays
`invalid.preflight.core_primitive_clip_producer_authority`.

Review finding 2 (Important) — the classification lives in the shared
`GPUOpMapper.toMaskExecutionPlan`, so the rect + orthogonal-polygon DIFFERENCE
clip now lowers to `AnalyticMultiRect` for **all** consumers, not just blur. The
non-blur cross-lane change (no test pins this shape): a non-blur draw under this
clip previously lowered to `CoverageMask` (coverage-mask producer route); it now
lowers to `AnalyticMultiRect`, which the core lane refuses — analytic-shape draws
still hit `unsupported.recording.core_primitive_analytic_shape_clip` (the
`!= NoClip && !ScissorOnly` gate is unchanged), and direct non-analytic-shape
draws are refused via `corePrimitiveDirectClipAuthority` `else -> Refused`
(`unsupported.native-core-primitive.clip`) with the defensive
`corePrimitiveStructuralClip → Clip.Refused` mapping. Both are refusals; the clip
suite + core primitive suites stay green.

Bundled hardening — `decomposeOrthogonalPolygon` now guards the fill rule: the
even-odd scanline is exact for `EvenOdd` fill always, but for `Winding` fill only
when the polygon is simple (a self-intersecting Winding polygon would be
mis-decomposed). The sweep now accumulates the non-zero winding number and
rejects any band where it leaves `{-1, 0, 1}` for Winding-filled polygons
(`GPUOpMapper.kt:1479-1500`). The fixture's simple Winding L-shape still admits
(|winding| max 1).

Covering runs: `GPUClipCoverageSurfaceTest` 43/43, `GPUMaskBlurSurfaceTest`
20/20, `GPUFramePreflighterTest` 107/107; `:gpu-renderer:test` 3301 (1 documented
package-boundary baseline); `:kanvas:test` 3237 (1 documented image-pixel UNORM
baseline).

## Task 6 — analytic-clip uniform64/160 split

Task 6 wires the analytic-clip uniform64/160 split admission (the former
`mixed_uniform_layouts` gate) with the per-step continuation/ownership design
(fp-11 §4), preserving the split-lane mid-loop lease cleanup (`3bd78e180`). The
result: the 199 blend rows leave `unsupported.recording.core_primitive_mixed_
uniform_layouts`; SRC_OVER rows render prepared; the non-SRC_OVER analytic-clip
rows, the analytic-shape-under-clip rows, and the path-stencil cover rows
re-point to their accurate stable codes (the analytic-clip blend programs, the
combined shape+clip shader, and the path-stencil continuation are separate
features, not the split).

### 6.1 Re-measured distribution (before → after)

Before (closure-HEAD re-measure, green 1864-row matrix): 199 blend rows on
`mixed_uniform_layouts` — DrawRRect 29 (ALPHA_MASK), DrawRect/DrawColor 56
(ALPHA_MASK non-DST), DrawPath/DrawDRRect 58 (ALPHA_MASK), DrawPoint/DrawPoints
56 (ALPHA_MASK non-DST). This matches fp-11 §0.3 exactly (no drift).

After (the RED re-point → 199 failures captured per-row, then the split + re-point
→ green 1864-row matrix):

| code | rows | detail |
| --- | --- | --- |
| Prepared (renders, pixel-oracle exact) | **4** | DrawRect/DrawColor/DrawPoint/DrawPoints × SRC_OVER × ALPHA_MASK |
| `unsupported.recording.core_primitive_analytic_shape_clip` | **29** | DrawRRect × ALPHA_MASK (analytic-shape uniform80 under analytic clip) |
| `unsupported.native-core-primitive.session-cache-pipeline` | **93** | DrawRect 27 + DrawColor 27 + DrawPoint 12 + DrawPoints 27 (non-SRC_OVER fixed-function and artistic modes on the analytic-clip uniform64 lane) |
| `invalid.preflight.core_primitive_path_stencil` | **28** | DrawPath 14 + DrawDRRect 14 (non-dst-copy path-stencil cover under analytic clip) |
| `unsupported.native-core-primitive.path-destination-read` | **30** | DrawPath 15 + DrawDRRect 15 (dst-copy path cover under analytic clip) |
| `invalid.preflight.core_primitive_direct_geometry_resources` | **15** | DrawPoint × dst-copy modes × ALPHA_MASK (the four-render shape seal) |
| total | **199** | ✓ |

The two distinct emission sites are captured per-row in the RED run:

- `DrawRect/SRC_OVER/ALPHA_MASK` → "One direct CorePrimitive pass cannot mix
  analytic-clip uniform64 or uniform160 with another uniform layout." (the former
  frame-level gate `:2132`).
- `DrawRRect/SRC_OVER/ALPHA_MASK` → "One direct CorePrimitive draw cannot combine
  analytic-shape uniform80 with analytic-clip uniform64 or uniform160." (the
  former single-draw gate `:1617`).

Both gates are retired; `core_primitive_mixed_uniform_layouts` is no longer
emitted by any builder gate (`rg` over `gpu-renderer/src` + `kanvas/src` → no
production matches).

### 6.2 The split wiring (production code)

- `GPUCorePrimitivePreparedFrameTaskListBuilder.kt:2103-2117` — the frame-level
  `activeDirectUniformLayouts > 1` analytic-clip 64/160 gate is removed. The
  direct pass now splits by uniform layout (uniform32/80/64/160), each group owns
  its slab, and the split-lane materializer materializes every pass in step
  order. The comment pins the retired deterministic-residual evidence (the
  FP-11 leak `3bd78e180` at `GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt:
  5639-5676`).
- `GPUCorePrimitivePreparedFrameTaskListBuilder.kt:1612-1617` — the single-draw
  "analytic-shape uniform80 + analytic-clip uniform64/160" gate is retired; the
  analytic-shape-under-clip draw now falls through to the
  `core_primitive_analytic_shape_clip` refusal (NoClip or ScissorOnly), which is
  the accurate stable code.
- `GPUFramePreflighter.kt:4224-4241` and `:4285-4314` — the per-step
  uniform64/160 seal construction now rebases each step's seals to a zero-based
  sliced slab when a frame owns multiple uniform64/uniform160 steps (fp-11 §4
  per-step continuation/ownership), and rebuilds the per-step packed bytes.
- `GPUFramePreflighter.kt:6439-6546` — restored
  `sliceAnalyticClipUniformSealsToCommands` and
  `sliceAnalyticIntersectionUniformSealsToCommands` (mirrors of
  `sliceAnalyticShapeUniformSealsToCommands`), removed as FP-11 Task 6 probe
  debris (`178c681fa`) when the gate still pinned the split.

### 6.3 Lease-cleanup re-verification (deterministic)

The split-lane mid-loop lease cleanup (`GPUWgpu4kCorePrimitiveFramePayload
Materializer.kt:5639-5676`, mirroring the dst-copy lane at `:5251-5261`) is
preserved and re-verified: the full `:kanvas:test` run completed with **zero**
`failed.surface.prepared.session-close` / `GPUOwnedNativeCloseIncompleteException`
occurrences (scanned all JUnit XML). The `session-cache-pipeline` refusals that
surfaced for the non-SRC_OVER analytic-clip rows are clean (the FP-11 cleanup
releases/quarantines the already-materialized run lifecycles), confirming the
leak is fixed and not reintroduced.

### 6.4 Task-6 split-resource fallout (32 rows)

The 2 DrawRRect DST rows (UNCLIPPED/SCISSOR) and the 30 DrawPoint dst-copy rows
(15 modes × 2 contexts) remain on `invalid.preflight.core_primitive_direct_
geometry_resources`, unchanged (the green blend matrix still asserts that exact
code per row). They are re-pointed with evidence, not closed: the DST rrect pass
still cannot exact its shared geometry slab authority after the split, and the
DrawPoint fixture's three separate point commands still make a four-render shape
that fails the same direct-resource seal (fp-11 §5). The stable typed refusal is
retained. In addition, the 15 DrawPoint dst-copy ALPHA_MASK rows that were part of
the 199 re-point to the same code (the split now lets them reach the four-render
seal).

### 6.5 Clip pins + router re-points

- `GPUClipCoverageSurfaceTest.kt:112` (Coverage 1) → `assertTerminal(
  PREPARED_ANALYTIC_SHAPE_CLIP_REFUSAL)` (the default-AA rect lowers to the
  analytic-shape uniform80 lane under an analytic clip).
- `GPUClipAdvancedBlendSurfaceTest.kt:53-57` (Advanced 8) → the clipped
  destination-read blends re-point to `unsupported.recording.core_primitive_
  analytic_shape_clip` (the default-AA source rect is analytic-shape-under-clip).
- `GPUPathClipRegressionTest.kt:25` (PathClip 1) → re-pointed to
  `invalid.preflight.core_primitive_path_stencil` (the AA background splits to
  its own uniform80 run; the analytic-clipped path pair fails the
  exactly-one-path-pass authority).
- `GPUPreparedSurfaceProductRouterTest.kt:470-478` → `core_primitive_mixed_
  uniform_layouts` removed from the terminal-family matrix (verified unreachable
  by `rg` over the production emitters).

### 6.6 Test updates

- `GPUAllApiBlendSurfaceTest.kt` — the 199 rows re-point per §6.1; the unused
  `PREPARED_MIXED_UNIFORM_LAYOUTS_REFUSAL` constant removed; three new constants
  (`PREPARED_ANALYTIC_SHAPE_CLIP_REFUSAL`,
  `PREPARED_SESSION_CACHE_PIPELINE_REFUSAL`, `PREPARED_PATH_STENCIL_REFUSAL`).
- `GPUCorePrimitivePreparedFrameTaskListBuilderTest.kt` — the
  "analytic shape and analytic clip uniform layouts refuse atomically" pin
  re-pointed to `core_primitive_analytic_shape_clip`; the three mixed-frame
  "refuse before slab budget planning" tests converted to split tests
  (`uniform160 and uniform32 split…`, `uniform32 and uniform64 split…`,
  `uniform64 and uniform160 split…`), each asserting two direct passes with per-
  layout slabs and `GPUFramePlanner.plan(taskList).atomicallyRefused == false`.
- `GPUFramePreflighterTest.kt` — two new slice-helper tests
  (`analytic clip uniform slab slice rebases one step to zero based offsets`,
  `analytic intersection uniform slab slice rebases one step to zero based
  offsets`) cover the restored uniform64/160 per-step slicing.

### 6.7 Full-run summaries

```bash
DISPLAY=:99 ./gradlew -F off :gpu-renderer:test --no-parallel --console=plain --rerun-tasks
```

Result: **3303 tests, 1 failure** — the documented pre-existing
`GPURendererPackageBoundaryTest` baseline (exactly 20 package cycle violations /
0 rule violations, unchanged).

```bash
DISPLAY=:99 ./gradlew -F off :kanvas:test --no-parallel --console=plain --rerun-tasks
```

Result: **3237 tests, 1 failure** — the documented pre-existing
`GPUPreparedSurfaceImagePixelTest` UNORM 1-LSB llvmpipe baseline (unchanged).
No `session-close` / `GPUOwnedNativeCloseIncompleteException` in either run.

Guards green: `GPUAllApiBlendSurfaceTest` 1864/1864,
`GPUPreparedSurfaceProductRouterTest` 15/15,
`GPUClipCoverageSurfaceTest` (green), `GPUClipAdvancedBlendSurfaceTest` (green),
`GPUPathClipRegressionTest` (green), `GPUPreparedSurfaceLegacyAbsenceTest` 1/1,
`GPUPreparedSurfaceLifetimeStressTest` 6/6, `GPUFramePreflighterTest` (green),
`GPUCorePrimitivePreparedFrameTaskListBuilderTest` (green).

### 6.8 Notes and non-claims

- The split closes the SRC_OVER analytic-clip rows (per-pixel oracle exact). The
  non-SRC_OVER fixed-function and artistic modes on the analytic-clip uniform64
  lane need the analytic-clip blend programs (an `AnalyticClipDstRead` program +
  the geometric projection, paralleling Tasks 3/4 for the analytic-shape lane),
  which is a separate feature from the split; those rows re-point to the lane's
  exact `session-cache-pipeline` identity refusal. The path-stencil cover rows
  need the Task 8 stencil-continuation feature; the analytic-shape-under-clip
  rows need a combined shape+clip shader. CPU-oracle evidence is not
  Skia-comparable fidelity (M86 statement, Task 0).
- No global similarity threshold or assertion was weakened; the only matrix
  changes are re-points from `mixed_uniform_layouts` to the row's accurate code
  or to Prepared.

## Task 7 — analytic clips over non-direct shading geometry

Task 7 admits the analytic-clip authority for non-direct shading geometry on the
Task 6 uniform64/160 frame, for the 4 rows (DrawRect/DrawColor/DrawPoint/DrawPoints
× ALPHA_MASK × DST, fp-11 §2, residual-inventory item 5). Rows whose shading
geometry is stencil-shaded (the path case) defer to Task 8 unchanged.

### 7.1 Root cause — why the 4 rows' shading geometry is "non-direct"

`BlendMode.DST` always specializes to `GPUBlendPlan.NoOp("destination is
unchanged")` (`GPUBlendPlanning.kt:168`, `:205`, `:239`) regardless of coverage —
a DST draw writes nothing, so there is no destination-read formula lane for it.

`directCorePrimitiveGeometryBytes` (`GPUCorePrimitivePreparedFrameTaskListBuilder.kt:700-739`)
excludes NoOp blends at `:709` (`packet.blendPlan is GPUBlendPlan.NoOp`), so a DST
draw under an analytic clip never enters `directGeometryBytesByCommandId`. Its
command id therefore fails the gate at `:2000` (`it !in directGeometryBytesByCommandId
&& it !in pathStencilPlansByCommandId`), refusing with
`unsupported.recording.core_primitive_analytic_clip_non_direct_geometry`.

The clip is `AnalyticCoverage` (contentKey-less, `contentKeyOrNull() = null` at
`:843`), so it is not a clip artifact — it is carried entirely by the analytic-clip
authority + uniform64. Because a NoOp draw shades nothing, that authority is
vacuous: the correct frame simply elides the NoOp packet (exactly how the already-
green DST UNCLIPPED/SCISSOR rows behave), leaving the background fill as the whole
result (oracle: destination unchanged).

The other three exclusion classes of `directCorePrimitiveGeometryBytes` are NOT
in scope here: path-stencil covers (StencilEdgeFan) are excluded from the gate via
`pathStencilPlansByCommandId` and defer to Task 8; analytic-shape (uniform80)
rrects under a clip refuse earlier at `core_primitive_analytic_shape_clip`; the
30 DrawPoint dst-copy rows are ShaderBlendWithDstRead (not NoOp), so they stay on
`direct_geometry_resources` (§7.5).

### 7.2 Before state (RED run)

The 4 rows re-pointed from Terminal to Prepared (`null`) in
`GPUAllApiBlendSurfaceTest.kt`:

```
:kanvas:test --tests "*GPUAllApiBlendSurfaceTest" --rerun-tasks
```

Result: **1864 tests, 4 failed**, each exactly:

```
org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceTerminalException:
unsupported.recording.core_primitive_analytic_clip_non_direct_geometry:
Prepared analytic clips require one direct CorePrimitive shading geometry.
```

per row: `DrawRect/DST/ALPHA_MASK`, `DrawColor/DST/ALPHA_MASK`,
`DrawPoint/DST/ALPHA_MASK`, `DrawPoints/DST/ALPHA_MASK`. (The path and rrect DST
rows stayed green on their own pins throughout — routing unchanged.)

### 7.3 Admission (production code)

- `GPUCorePrimitivePreparedFrameTaskListBuilder.kt:644-646` — new
  `GPUDrawSemanticPayload.CorePrimitive.hasPathStencilCoverGeometry()` helper
  (true for a `TriangulatedPath` in `StencilEdgeFan` mode).
- `GPUCorePrimitivePreparedFrameTaskListBuilder.kt:1587-1599` — the analytic-clip
  authority loop skips a `GPUBlendPlan.NoOp` packet unless it is a path-stencil
  cover: a NoOp draw shades nothing, so its analytic-clip authority is vacuous and
  the packet elides downstream like any other NoOp. The path-stencil cover keeps
  the authority (it still runs the stencil test/reset even when its color blend is
  destination-only), so path rows defer to Task 8 unchanged.
- `GPUCorePrimitivePreparedFrameTaskListBuilder.kt:1611-1618` — the
  analytic-intersection twin applies the same rule (consistent with the clip gate).

The gate at `:2000` (twin `:2009` → now `:2024`/`:2031`) is unchanged; it still
refuses non-direct, non-NoOp shading geometry (an inverse-fill or stroked
direct-triangle path under an analytic clip), so the code remains reachable.

### 7.4 After state (GREEN run)

`:kanvas:test --tests "*GPUAllApiBlendSurfaceTest" --rerun-tasks` →
**1864/1864**. The 4 rows render Prepared, per-pixel CPU-oracle exact via
`assertPixelsNear(..., tolerance = 2)` (oracle = destination unchanged; the DST
NoOp packet elides and only the background fill contributes).

### 7.5 DrawPoint direct_geometry_resources re-verification (unchanged)

The 30 DrawPoint four-render rows (15 dst-copy modes × UNCLIPPED/SCISSOR, fp-11
§5 "three separate point commands make a four-render shape") plus the 15 DrawPoint
dst-copy ALPHA_MASK rows re-pointed by Task 6, and the 2 DrawRRect DST rows, all
remain on `invalid.preflight.core_primitive_direct_geometry_resources`. They are
ShaderBlendWithDstRead, not NoOp, so the admission does not touch them; the green
1864/1864 matrix asserts their exact code per row (no outcome change). No split of
ownership with Task 6.

### 7.6 Router re-point

- `GPUPreparedSurfaceProductRouterTest.kt:470-481` — `core_primitive_analytic_
  clip_non_direct_geometry` stays in the terminal-family matrix with an updated
  comment: the NoOp (DST) rows are now admitted, but the gate still refuses other
  non-direct shading geometry (inverse-fill / stroked direct-triangle path under
  an analytic clip), so the code remains reachable and is kept (not removed).

### 7.7 Full-run summaries

```bash
DISPLAY=:99 ./gradlew -F off :gpu-renderer:test --no-parallel --console=plain
```

Result: **3303 tests, 1 failure** — the documented pre-existing
`GPURendererPackageBoundaryTest` baseline (exactly 20 package cycle violations /
0 rule violations, unchanged).

```bash
DISPLAY=:99 ./gradlew -F off :kanvas:test --no-parallel --console=plain
```

Result: **3237 tests, 1 failure** — the documented pre-existing
`GPUPreparedSurfaceImagePixelTest` UNORM 1-LSB llvmpipe baseline (unchanged).
No `session-close` / `GPUOwnedNativeCloseIncompleteException` in either run
(scanned all JUnit XML).

Guards green: `GPUAllApiBlendSurfaceTest` 1864/1864,
`GPUPreparedSurfaceProductRouterTest` 15/15, `GPUClipCoverageSurfaceTest` (green),
`GPUClipAdvancedBlendSurfaceTest` (green), `GPUPathClipRegressionTest` (green),
`GPUCorePrimitivePreparedFrameTaskListBuilderTest` (green).

### 7.8 Notes and non-claims

- The admission is scoped to NoOp (DST) draws: the analytic clip is vacuous because
  nothing is shaded. It does not admit analytic clips over any shading geometry
  that actually samples coverage (path-stencil cover, inverse-fill/stroked
  direct-triangle paths, stencil-covered rrects) — those stay refused and defer to
  their own tasks (Task 8, and later geometry-lowering work).
- CPU-oracle evidence is not Skia-comparable fidelity (M86 statement, Task 0). No
  global similarity threshold or assertion was weakened.

## Task 8 — path-stencil stencil-continuation + dst-read path cover

Task 8 wires the fp-11 §3 stencil-continuation feature: a destination-reading
path (StencilEdgeFan) lowers to a background render, a producer render that
stores the fan (Clear+Store), an ordered destination snapshot copy, and a
continued cover render that loads the fan read-only and shades with the
dst-read formula. The 60 UNCLIPPED/SCISSOR path dst-copy rows render Prepared
(CPU-oracle exact); the analytic-clip path families re-document to the
path-stencil preflight code (a separate analytic-clip × stencil-cover feature).

### 8.1 Row scope (before → after)

Before (green 1864-row matrix, closure-HEAD codes):

| code | rows |
| --- | --- |
| `unsupported.native-core-primitive.path-destination-read` | 90 (60 UNCLIPPED/SCISSOR + 30 ALPHA_MASK dst-copy) |
| `invalid.preflight.core_primitive_path_stencil` | 28 (non-dst-copy ALPHA_MASK) |

After (green 1864-row matrix, re-pointed):

| outcome | rows |
| --- | --- |
| Prepared (renders, CPU-oracle exact) | **60** (DrawPath/DrawDRRect × 15 dst-copy modes × UNCLIPPED/SCISSOR) |
| `invalid.preflight.core_primitive_path_stencil` | **58** (30 ALPHA_MASK dst-copy + 28 non-dst-copy ALPHA_MASK) |

`unsupported.native-core-primitive.path-destination-read` left production (`rg`
over `gpu-renderer/src` + `kanvas/src` → no matches). The 30 ALPHA_MASK dst-copy
rows and the 28 non-dst-copy ALPHA_MASK rows share the analytic-clip ×
stencil-cover root (the preflighter's "Analytic path pair cannot continue its fan
across the destination snapshot yet." / "Analytic path pair contradicts …")
and re-point to the stable `invalid.preflight.core_primitive_path_stencil` code.

### 8.2 The five feature pieces (file:line)

1. **Producer stores the fan** — `GPUCorePrimitivePreparedFrameTaskListBuilder.kt`
   splits a dst-read path into producer/cover render tasks (`flatMapIndexed`), the
   producer with `pathDepthStencilProducerLoadStore = WritableStencil(Clear, Store, 0)`
   and the cover with `pathDepthStencilCoverLoadStore = ReadOnlyKeep`;
   `consumerResourceUses`/`consumerDepthStencilLoadStore` gain a `pathPacketRole`
   parameter (producer `write=true`, cover `write=false` + snapshot).
2. **TextureCopy snapshot between stencil and cover** — the destination snapshot
   consumer ref keys to the assembled cover packet id
   `${basePacket.packetId}.path-stencil-cover`; the linearizer places the ordered
   copy between the producer and cover (`GPUFramePlanner.kt:766-784`).
3. **Cover loads read-only + binds snapshot** — `GPUCorePrimitiveNativeScopeRouteUnit
   .PathProducer`/`PathCover` halves; `GPUCorePrimitivePathStencilNativeRouteSeal
   .Continued`; `GPUWgpu4kCorePrimitivePipelineDescriptor.PathStencilCoverDstRead`
   (stencil `NotEqual`/`Keep`, `writeMask = 0`, dst-read shader
   `buildCorePrimitiveDstReadNativeShader`).
4. **Cross-step pair admission + second path render** — `GPUFramePreflighter.kt`
   `continuedDstReadPathAdmission` (two path renders + a `CopyDestinationStep`);
   the pair-building loop iterates a flattened `(stepIndex, packet)` stream so the
   producer in one render pairs with the cover in the next.
5. **Run materializer + frame pool** — `supportedPathComponents` accepts
   `isCorePrimitiveDstRead()`; the frame slot allows `dstRead` + `pathDepthStencil`;
   the native stencil config derives from `plan.renderStep.depthStencilLoadStore`
   (`GPUWgpu4kCorePrimitiveRenderRunMaterializer.kt:399-426`).

The materializer lane `materializeContinuedPathDstReadCore`
(`GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt:5408`) creates one shared
frame-local path D24S8 and materializes background / producer / cover, passing the
shared view to the producer and cover runs so the fan persists across the ordered
snapshot copy.

### 8.3 RED evidence

Flipping all 118 path rows to Prepared:

```
./gradlew -F off :kanvas:test --tests "*GPUAllApiBlendSurfaceTest" --no-parallel --console=plain
```

→ **118 failures**: 90 × `unsupported.native-core-primitive.path-destination-read:
Prepared path-stencil packets cannot consume destination-read snapshots yet.` and
28 × `invalid.preflight.core_primitive_path_stencil: Analytic path pair contradicts
command, geometry prefix, clip seal, slot, offset, layout, or generation authority.`

### 8.4 GREEN evidence

```
./gradlew -F off :kanvas:test --tests "*GPUAllApiBlendSurfaceTest" --no-parallel --console=plain
```

→ **1864/1864** (0 failed, 0 skipped). The 60 UNCLIPPED/SCISSOR dst-copy rows render
Prepared, per-pixel CPU-oracle exact (`assertPixelsNear(..., tolerance = 2)`).

### 8.5 Full-run summaries

```bash
DISPLAY=:99 ./gradlew -F off :gpu-renderer:test --no-parallel --console=plain
```
→ **3303 tests, 1 failure** — the documented `GPURendererPackageBoundaryTest`
baseline (20 cycles / 0 rules, unchanged).

```bash
DISPLAY=:99 ./gradlew -F off :kanvas:test --no-parallel --console=plain
```
→ **3237 tests, 1 failure** — the documented `GPUPreparedSurfaceImagePixelTest`
UNORM 1-LSB llvmpipe baseline (unchanged).

Guards green: `GPUAllApiBlendSurfaceTest` 1864/1864,
`GPUClipCoverageSurfaceTest`, `GPUClipAdvancedBlendSurfaceTest`,
`GPUPathClipRegressionTest`, `GPUPreparedSurfaceProductRouterTest` (the
`path-destination-read` code left the terminal-family matrix),
`GPUWgpu4kCorePrimitiveClipStencilAaFrameSmokeTest`.

### 8.6 Re-points

- `GPUAllApiBlendSurfaceTest.kt:656-669` — the 60 UNCLIPPED/SCISSOR dst-copy rows
  → Prepared; the 30 ALPHA_MASK dst-copy rows → `PREPARED_PATH_STENCIL_REFUSAL`.
- `GPUPreparedSurfaceProductRouterTest.kt:474-479` — `path-destination-read`
  removed from the terminal-family matrix (left production).
- `GPUPathClipRegressionTest.kt:133-166` — the destination-reading path frame now
  renders Prepared (cyan DIFFERENCE-over-white, `route:destination-read:DrawPath:`
  evidence) instead of refusing.

### 8.7 Notes and non-claims

- CPU-oracle evidence is not Skia-comparable fidelity (M86 statement, Task 0).
  No global similarity threshold or assertion was weakened.
- The 58 analytic-clip path rows remain refused with their stable
  `invalid.preflight.core_primitive_path_stencil` code (analytic-clip ×
  stencil-cover is a separate feature; not forced).
