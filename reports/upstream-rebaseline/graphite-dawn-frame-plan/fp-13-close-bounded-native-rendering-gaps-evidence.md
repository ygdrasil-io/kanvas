# FP-13 Close Bounded Native-Rendering Gaps — Evidence

Status: **in progress** (Task 1 complete: `colr-v0-color-glyph` scene CPU-oracle
fix closes the byte-exact pin; further tasks append their own sections).

Branch: `codex/graphite-dawn-frame-fp13`. Machine: Linux, JDK Temurin 25, GPU =
Vulkan **llvmpipe** (software, CPU; Mesa 26.0.3, LLVM 21.1.8), Xvfb `:99`. All
GPU suite runs used `DISPLAY=:99`.

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
reference=independent-cpu-source-over
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

- SHA: (filled after commit)
- Message: conventional commit, Task 1 / FP-13 reference.

### 1.8 Notes and non-claims

- The sRGB-encode emulation embeds this host's (AMD EPYC Zen 3) `rsqrtps`
  approximation tables; byte-exactness is defined against the llvmpipe lane on
  this machine, matching the plan's llvmpipe baseline. On other CPUs the tables
  could differ by ±1 on boundary pixels; the harness oracle is not claimed to
  match non-llvmpipe adapters.
- The oracle's color handling treats the font palette as the lane does (palette
  values carried as linear premul, encoded at store); the fix does not change
  the lane's semantics, only the oracle's fidelity to them.
