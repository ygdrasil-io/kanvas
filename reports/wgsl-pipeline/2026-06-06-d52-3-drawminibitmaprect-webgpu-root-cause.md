# D52-3 - DrawMiniBitmapRect WebGPU root cause

Date: 2026-06-06
Ticket source: `global/kanvas/tickets/drafts/brouillon-ticket-d52-3-diagnostiquer-et-corriger-l-ecart-web-gpu-draw-mini-bitmap-rect`
Evidence JSON: `reports/wgsl-pipeline/scenes/generated/d52-drawminibitmaprect-webgpu-root-cause.json`
Input artifacts: `reports/wgsl-pipeline/scenes/artifacts/d52-drawminibitmaprect/`

## Result

D52-3 diagnoses the D52-2 WebGPU gap but does not apply a rendering fix.

Root cause: `DrawMiniBitmapRectGM` rotates some `drawImageRect` calls, so
`SkCanvas.drawImageRect` routes those calls through `drawPath(SkPath.Rect(dst))`
with an internal `SkBitmapShader(kClamp)` and a `src -> dst` local matrix. For
the 32 of 81 draws whose source rectangle extends beyond the 2048 x 2048 image
bounds, the original `drawImageRect` source-clip semantics are no longer
available to `SkWebGpuDevice`. WebGPU therefore treats the shader as a normal
user `kClamp` bitmap shader and extends edge texels across the whole destination
rect. The upstream Skia reference instead clips the sampled image footprint to
the intersection of `src` with the image bounds, producing the thin strips seen
in the D52-2 diff.

This is not a WebGPU-only route bug with a safe local correction. A WebGPU-only
change that converts this shape to decal/clip behavior would also affect real
user `SkBitmapShader(kClamp)` fills, where edge extension outside the image
footprint is the expected behavior and is covered by existing bitmap shader
tests.

## Measurements

| Run | Backend | Status | Similarity | Threshold | Fallback |
|---|---|---|---:|---:|---|
| D52-2 input | WebGPU | `expected-unsupported` | `94.9305%` | `99.95%` | `bitmap.drawminibitmaprect.rotated-fast-src-rect-webgpu-artifacts-required` |
| D52-3 after diagnosis | WebGPU | `expected-unsupported` | `94.9305%` | `99.95%` | `bitmap.drawminibitmaprect.rotated-fast-src-rect-webgpu-artifacts-required` |

D52-3 does not claim a score impact. `fallbackReason=none` remains forbidden
because WebGPU remains below `99.95%`.

## Evidence

The artifact diff localizes the visible failure to the oversized source-rect
cases:

- The GM uses source widths/heights `1, 3, 9, 27, 81, 243, 729, 2187, 6561`.
- The source image is `2048 x 2048`, so `2187` and `6561` are out of bounds.
- That produces 32 out-of-bounds source-rect draws out of 81 total draws.
- The strongest visible mismatches are the right and bottom grid cells where
  Skia shows a clipped strip, while Kanvas CPU/WebGPU show a full clamped tile.

The existing CPU artifact has the same class of semantic drift. Its status stays
`pass` only because the D52 harness uses the historical CPU floor (`40.0%`) as
diagnostic evidence, not as a WebGPU promotion gate.

## Correction decision

No WebGPU production patch is applied in D52-3.

A correct fix needs to preserve `drawImageRect` provenance or carry an explicit
source-image intersection/clipped-destination contract into the shader path. It
should be designed at the shared `SkCanvas.drawImageRect` rewrite boundary, then
validated across CPU and WebGPU. Patching only `SkWebGpuDevice` would require an
unsafe heuristic that cannot reliably distinguish:

- an internal rotated `drawImageRect` shader whose out-of-image source region
  should not extend edge texels; from
- a user-authored `SkBitmapShader(kClamp)` where edge extension is required.

## Non-claims

- No support promotion is claimed.
- No fallback reason is cleared.
- No threshold, global score, dashboard row, `results.json`, `scenes.json`,
  D50/D51/D52-1 manifest, `PipelineKey`, or fallback policy is changed.
- No M66 evidence is inherited.
- No Ganesh, Graphite, SkSL compiler, IR, or VM work is introduced.

## Validation

```bash
rtk python3 scripts/validate_d52_drawminibitmaprect_webgpu_root_cause.py
rtk python3 -m json.tool reports/wgsl-pipeline/scenes/generated/d52-drawminibitmaprect-webgpu-root-cause.json
rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-d52-3-pycache python3 -m py_compile scripts/validate_d52_drawminibitmaprect_webgpu_root_cause.py
rtk ./gradlew --no-daemon --rerun-tasks :gpu-raster:test --tests org.skia.gpu.webgpu.DrawMiniBitmapRectSceneCaptureTest
rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.DrawMiniBitmapRectSceneCaptureTest
rtk git diff --check
```

The D52-2 `--require-artifacts` validator remains valid for the D52-2 patch
scope, but it intentionally rejects the new D52-3 report, JSON, and validator
as out-of-scope files. D52-3 therefore revalidates the D52-2 artifacts through
`validate_d52_drawminibitmaprect_webgpu_root_cause.py` instead of listing the
D52-2 scope guard as a final command.
