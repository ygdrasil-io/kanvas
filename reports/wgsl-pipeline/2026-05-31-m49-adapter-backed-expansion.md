# M49 Adapter-Backed Evidence Expansion

Date: 2026-05-31
Linear: GRA-291
Parent epic: GRA-287
Depends on: GRA-290

## Scope

GRA-291 expands dashboard adapter-backed proof from 2 rows to 7 rows by adding
adapter metadata to already-supported, already-passing rows whose owning GPU
commands passed on the available adapter.

No row status, fallback reason, similarity threshold, route, or support boundary
is broadened by this ticket.

## Result

| Signal | Before | After |
|---|---:|---:|
| Adapter-backed rows | 2 | 7 |
| `pass` rows | 18 | 18 |
| `expected-unsupported` rows | 5 | 5 |
| `tracked-gap` rows | 0 | 0 |
| `fail` rows | 0 | 0 |

Adapter used for the promoted rows: `Apple M2 Max`.

## Newly Adapter-Backed Rows

| Scene id | Family | Command | Why promoted |
|---|---|---|---|
| `bitmap-rect-nearest` | bitmap/image-rect | `rtk ./gradlew --no-daemon :gpu-raster:gpuSmokeTest --tests org.skia.gpu.webgpu.DrawBitmapRectSkbug4734WebGpuTest` | Existing required GPU smoke row passed on adapter; fallback remains `none`. |
| `linear-gradient-rect` | gradient | `rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.LinearGradientRectTest --tests '*GeneratedLinearGradientWgslTest'` | Generated WGSL/parser and GPU gradient tests passed; fallback remains `none`. |
| `src-over-stack` | blend | `rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.BlendModeTest --tests org.skia.gpu.webgpu.TranslucentSrcOverTest` | Stable blend route tests passed; measured perf remains reporting-only. |
| `bitmap-shader-local-matrix` | bitmap / local matrix | `rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.BitmapShaderRotatedTest` | Bitmap local-matrix affine route tests passed; fallback remains `none`. |
| `clip-rect-difference` | clip | `rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.ClipDifferenceCrossTest` | Difference clip GPU tests passed; threshold remains intentionally broad at 80.0. |

The two pre-existing adapter-backed rows remain:

- `solid-rect`
- `analytic-aa-convex`

## Artifact Updates

For each newly promoted row, the dashboard row now includes:

- `maturity.adapter-backed` tag;
- `gpu.stats.adapter=Apple M2 Max`;
- `gpu.stats.adapterEvidenceCommand`;
- `gpu.stats.adapterEvidenceCommit`;
- linked evidence report path.

The row artifact directories also carry adapter evidence metadata in:

- `reports/wgsl-pipeline/scenes/artifacts/<scene-id>/stats.json`
- `reports/wgsl-pipeline/scenes/artifacts/<scene-id>/route-gpu.json`

## Skipped Candidates

| Candidate | Reason skipped |
|---|---|
| M48 paint/transform/gradient rows | Not needed to reach the count target; avoid expanding M48 scene-pack claims until a dedicated capture writer is attached to those rows. |
| Broad Path AA rows | Out of scope; expected-unsupported edge-budget rows must remain refusals. |
| Image-filter DAG breadth | Out of scope; arbitrary image-filter DAG support remains non-claim. |
| Runtime-effect arbitrary SkSL | Out of scope; target forbids rebuilding SkSL compiler/VM. |

## Non-Claims

- This does not claim text, glyph masks, fonts, emoji, codecs, broad Path AA,
  arbitrary image-filter DAGs, perspective/3D transforms, or arbitrary SkSL.
- This does not change performance readiness; measured payloads remain
  reporting-only until M49-E.
- This does not add a new GPU backend; WebGPU remains the GPU backend.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-raster:gpuSmokeTest --tests org.skia.gpu.webgpu.DrawBitmapRectSkbug4734WebGpuTest :gpu-raster:test --tests org.skia.gpu.webgpu.LinearGradientRectTest --tests '*GeneratedLinearGradientWgslTest' --tests org.skia.gpu.webgpu.BlendModeTest --tests org.skia.gpu.webgpu.TranslucentSrcOverTest --tests org.skia.gpu.webgpu.BitmapShaderRotatedTest --tests org.skia.gpu.webgpu.ClipDifferenceCrossTest
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
```
