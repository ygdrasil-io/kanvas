# M47 Clip Rect Difference Generated Evidence

Date: 2026-05-31
Issue: GRA-275

## Outcome

`clip-rect-difference` was converted from static dashboard evidence to generated
evidence through `pipelineGeneratedSceneExport` while preserving the targeted
clip-lowering diagnostics for `Skbug9319GM`.

The static row was removed from:

```text
reports/wgsl-pipeline/scenes/data/scenes.json
```

The generated row was added to:

```text
reports/wgsl-pipeline/scenes/generated/results.json
```

The scene id remains `clip-rect-difference`, so the merged dashboard keeps the
same public row identity without duplicate scene ids.

## Preserved Support Semantics

| Field | Value |
|---|---|
| Status | `pass` |
| Priority | `P1` |
| Reference kind | `skia-upstream` |
| Draw kind | `Skbug9319GM` |
| CPU route | `cpu.coverage.clip-rect-difference` |
| CPU coverage plan | `clipRect(kDifference) + clipRRect(kDifference) CPU coverage oracle` |
| GPU route | `webgpu.coverage.clip-difference.analytic-rrect-mask` |
| GPU coverage strategy | `webgpu.coverage.clip-rect-difference` |
| GPU pipeline key | `clipOp=kDifference shape=rect+rrect maskFilter=blur` |
| CPU fallback reason | `none` |
| GPU fallback reason | `none` |
| Threshold | `80.0` |
| CPU similarity | `100.0%` |
| GPU similarity | `84.44%` |
| GPU matching pixels | `110672 / 131072` |
| GPU max channel delta | `255` |

Tags changed from `source.static` / `maturity.static-evidence` to
`source.generated` / `maturity.generated-evidence`. Existing clip, route,
reference, and risk tags were preserved.

## Clip-Lowering Diagnostics

The generated row keeps explicit clip-lowering evidence:

- Clip operation: `kDifference`.
- Clip interaction: targeted `clipRect(kDifference)` plus
  `clipRRect(kDifference)` shape handling.
- CPU oracle route: `cpu.coverage.clip-rect-difference`.
- GPU route: `webgpu.coverage.clip-difference.analytic-rrect-mask`.
- Fallback route: none for the selected row; `fallbackReason=none`.
- Non-selected policy: AA clip variants outside this row may remain
  expected-unsupported with stable fallback reasons.

## Threshold Policy

The broad `80.0` threshold remains intentionally unchanged. This row is backed
by Skia upstream visual evidence and selected clip-difference route diagnostics;
it is not broad clip-stack conformance. Tightening the threshold would require a
separate artifact update proving a narrower pixel contract for the selected
Skbug9319GM scene.

## Not Broad Clip/Mask Stack Support

This conversion does not claim full clip-stack, mask-stack, or arbitrary AA clip
coverage. It only promotes the existing selected `clipRect(kDifference)` /
`clipRRect(kDifference)` dashboard row to generated evidence.

## Artifacts

Canonical artifacts remain under:

```text
reports/wgsl-pipeline/scenes/artifacts/clip-rect-difference/
```

Key files:

- `reports/wgsl-pipeline/scenes/artifacts/clip-rect-difference/skia.png`
- `reports/wgsl-pipeline/scenes/artifacts/clip-rect-difference/cpu.png`
- `reports/wgsl-pipeline/scenes/artifacts/clip-rect-difference/gpu.png`
- `reports/wgsl-pipeline/scenes/artifacts/clip-rect-difference/cpu-diff.png`
- `reports/wgsl-pipeline/scenes/artifacts/clip-rect-difference/gpu-diff.png`
- `reports/wgsl-pipeline/scenes/artifacts/clip-rect-difference/route-cpu.json`
- `reports/wgsl-pipeline/scenes/artifacts/clip-rect-difference/route-gpu.json`
- `reports/wgsl-pipeline/scenes/artifacts/clip-rect-difference/stats.json`
- `reports/wgsl-pipeline/scenes/artifacts/clip-rect-difference/cpu-performance.json`
- `reports/wgsl-pipeline/scenes/artifacts/clip-rect-difference/gpu-performance.json`

## Generation Command

```bash
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.ClipDifferenceCrossTest
```

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.ClipDifferenceCrossTest
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
```

All commands passed.
