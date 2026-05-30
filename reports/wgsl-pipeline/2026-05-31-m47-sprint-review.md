# M47 Sprint Review

Date: 2026-05-31
Milestone: M47 -- Remaining Static Evidence & Runtime/Clip Hardening
Linear: GRA-272 through GRA-278

## Outcome

M47 is complete. The sprint locked the remaining static dashboard inventory,
converted all remaining static pass rows to generated evidence, and kept the two
Path AA rows as explicit static expected-unsupported policy evidence.

The dashboard remains clean: no tracked gaps, no failing support claims, and no
broader Path AA support claim. M47 reduced static dashboard evidence from five
rows to two rows, both intentionally scoped policy sentinels.

## Final Dashboard Counters

| Signal | Count |
|---|---:|
| Scene rows | 13 |
| `pass` | 11 |
| `tracked-gap` | 0 |
| `expected-unsupported` | 2 |
| `fail` | 0 |
| `maturity.generated-evidence` | 11 |
| `maturity.static-evidence` | 2 |
| `maturity.adapter-backed` | 2 |

## Ticket Summary

| Ticket | Result | PR | Evidence |
|---|---|---|---|
| GRA-273 | Locked the M47 remaining static evidence inventory and selected three pass rows for generated conversion. | #1252 | `reports/wgsl-pipeline/2026-05-31-m47-remaining-static-evidence-inventory.md` |
| GRA-274 | Converted `runtime-effect-simple` to generated evidence. | #1253 | `reports/wgsl-pipeline/2026-05-31-m47-runtime-effect-simple-generated-evidence.md` |
| GRA-275 | Converted `clip-rect-difference` to generated evidence. | #1254 | `reports/wgsl-pipeline/2026-05-31-m47-clip-rect-difference-generated-evidence.md` |
| GRA-276 | Converted `bitmap-shader-local-matrix` to generated evidence. | #1255 | `reports/wgsl-pipeline/2026-05-31-m47-bitmap-shader-local-matrix-generated-evidence.md` |
| GRA-277 | Validated that Path AA expected-unsupported rows remain static policy evidence. | #1256 | `reports/wgsl-pipeline/2026-05-31-m47-path-aa-expected-unsupported-policy-validation.md` |
| GRA-278 | Closed M47 with final counters, docs, and sprint review. | This PR | `reports/wgsl-pipeline/2026-05-31-m47-sprint-review.md` |

## Converted Rows

| Row | Source command / owner | Artifact root | Preserved support boundary |
|---|---|---|---|
| `runtime-effect-simple` | `rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.RuntimeEffectDescriptorWebGpuTest` | `artifacts/runtime-effect-simple` | Registered runtime-effect descriptor route, `fallbackReason=none`, 99.95 threshold, descriptor-missing refusal remains a separate diagnostic. |
| `clip-rect-difference` | `rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.ClipDifferenceCrossTest` | `artifacts/clip-rect-difference` | Clip difference route, analytic rrect mask coverage, `fallbackReason=none`, 80.0 threshold. |
| `bitmap-shader-local-matrix` | `rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.BitmapShaderRotatedTest` | `artifacts/bitmap-shader-local-matrix` | Bitmap shader local-matrix inverse/remap route, `fallbackReason=none`, 99.95 threshold, measured CPU/GPU performance payloads remain reporting-only. |

## Remaining Static Rows

| Row | Owner / rationale | Next recommended milestone |
|---|---|---|
| `path-aa-stroke-outline-fallback` | Path AA policy sentinel for the bounded stroke-outline overflow diagnostic `coverage.stroke-outline-edge-count-exceeded`; kept static because it summarizes inventory refusal policy rather than generated rendered support. | Future Path AA breadth milestone only after a concrete stroke-outline or atlas/mask implementation owner exists. |
| `path-aa-edge-budget-boundary` | Path AA policy sentinel for the broad 256-edge WebGPU AA budget refusal `coverage.edge-count-exceeded`; kept static because it protects the MVP boundary and avoids broad Path AA support claims. | Future Path AA budget/atlas milestone after benchmark-backed ADR update or implementation evidence. |

## PM Demo

Run:

```bash
rtk ./gradlew --no-daemon pipelineSceneDashboard
python3 -m http.server 8765 --bind 127.0.0.1 --directory build/reports/wgsl-pipeline-scenes
```

Open:

```text
http://127.0.0.1:8765/index.html
```

The dashboard should show 13 scenes: 11 pass, 2 expected unsupported, 11
generated evidence rows, and 2 static policy rows.

## Validation

M47 conversion tickets ran their owning commands:

```bash
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.RuntimeEffectDescriptorWebGpuTest
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.ClipDifferenceCrossTest
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.BitmapShaderRotatedTest
```

M47 closeout validation:

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
```

The generated dashboard export contains no duplicate scene ids, no tracked gaps,
no failing support claims, stable fallback reasons for expected unsupported rows,
valid tag namespaces, CPU/GPU artifacts for passing rows, and adapter metadata
for adapter-backed rows.

## Review Notes

- M47 completed the static-to-generated pass-row migration without changing the
  public scene count or support statuses.
- The remaining static rows are deliberate policy rows, not unowned conversion
  debt.
- The next useful milestone should focus on new capability evidence, not more
  dashboard hygiene, unless a Path AA implementation owner is created.
