# FP-13 Close Bounded Native-Rendering Gaps — Evidence

Status: **in progress** (Task 1 complete: `colr-v0-color-glyph` scene CPU-oracle
fix closes the byte-exact pin; further tasks append their own sections).

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
  | Tasks 2-8 | PipelineTypesTest hygiene; dst-read formula; multi-key dst-read; complex-clip blur; 64/160 split; analytic clips non-direct; stencil-continuation | renderer fixes planned (Tasks 3-8) / test hygiene (Task 2); tracked in this evidence doc when each task lands |
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
